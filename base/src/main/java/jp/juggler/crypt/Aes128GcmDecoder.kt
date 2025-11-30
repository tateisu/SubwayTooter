package jp.juggler.crypt

import jp.juggler.util.data.encodeUTF8
import java.io.ByteArrayOutputStream
import java.security.Provider
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor
import kotlin.math.min

/**
 * Content-Encoding: aes128gcm のデコード
 * - 単体テストで中間状態を見たいので、状態を持つクラスとして実装した
 * - 鍵の導出が RFC8188生とWebPushの2通りある
 */
class Aes128GcmDecoder(
    // ペイロードのリーダー
    reader: ByteRangeReader,
    // 暗号プロバイダ
    private val provider: Provider = defaultSecurityProvider,
) {
    companion object {
        private const val b0 = 0.toByte()
        private const val b1 = 1.toByte()
        private const val b2 = 2.toByte()

        private val prefixWpInfoZ = "WebPush: info\u0000".encodeUTF8().toByteRange()
        private val ceAes128gcmZ = "Content-Encoding: aes128gcm\u0000".encodeUTF8().toByteRange()
        private val ceNonceZ = "Content-Encoding: nonce\u0000".encodeUTF8().toByteRange()
    }

    // ペイロードのヘッダから導出される
    val salt: ByteRange
    val recordSize: Int
    val keyId: ByteRange
    val encryptedContent: ByteRange

    // RFC8188のキー導出手順で計算される
    var prk = ByteRange.empty
    var key = ByteRange.empty
    var nonce = ByteRange.empty

    init {
        // ペイロード内部のヘッダを読む
        salt = reader.readBytes(16)
        recordSize = reader.readUInt32()
        val keyIdLen = reader.readUInt8()
        keyId = reader.readBytes(keyIdLen)
        encryptedContent = reader.remainBytes()
    }

    /**
     * RFC8188のキー導出手順
     */
    fun deriveKeyRfc8188(inputKey: ByteRange) {
        prk = hmacSha256(salt, inputKey).toByteRange()
        key = hmacSha256Plus1(prk, ceAes128gcmZ).toByteRange(end = 16)
        nonce = hmacSha256Plus1(prk, ceNonceZ).toByteRange(end = 12)
    }

    /**
     * WebPush は inputKey の計算が少し増える
     */
    fun deriveKeyWebPush(
        // receiver private key in X509 format
        receiverPrivateBytes: ByteArray,
        // receiver public key in 65bytes X9.62 uncompressed format
        receiverPublicBytes: ByteArray,
        // auth secrets created at subscription
        authSecret: ByteArray,
    ) {
        // sender public key in 65bytes X9.62 uncompressed format
        val senderPublicBytes = keyId
        val sharedKeyBytes = provider.sharedKeyBytes(
            receiverPrivateBytes = receiverPrivateBytes,
            senderPublicBytes = senderPublicBytes.toByteArray(),
        )

        // create input key material
        val ikm = hmacSha256Plus1(

            hmacSha256(
                authSecret.toByteRange(),
                sharedKeyBytes,
            ).toByteRange(),

            ByteArrayOutputStream(130 + prefixWpInfoZ.size).apply {
                write(prefixWpInfoZ)
                write(receiverPublicBytes)
                write(senderPublicBytes)
            }.toByteRange()

        ).toByteRange(end = 32)

        deriveKeyRfc8188(ikm)
    }

    /**
     * レコードを順にデコードする
     */
    fun decode(): ByteRange {
        if (recordSize < 1) error("invalid record size")

        val iv = ByteArray(12)

        nonce.copyElements(iv, length = 12)

        var counter = 0L
        fun updateIv() {
            // 短いプッシュメッセージならカウンタは64bitを超えない。
            // カウンターをBEで12バイト表現すると上位8バイトは常に0なのでXOR演算する必要がない
            // 下位バイトだけXOR演算する
            var v = counter++
            if (v != 0L) {
                for (i in 0 until 8) {
                    val pos = 11 - i
                    iv[pos] = nonce[pos].xor(v.toByte())
                    v = v.ushr(8)
                }
            }
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding", provider)

        val src = encryptedContent
        val bao = ByteArrayOutputStream(src.size)
        val deltaBuffer = ByteArray(recordSize)
        val end = src.size
        for (i in src.indices step recordSize) {
            val step = min(recordSize, end - i)
            // カウンター番号でivを更新
            updateIv()
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key.ba, 0, 16, "AES-GCM"),
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )
            val delta = cipher.doFinal(src.ba, src.start + i, step, deltaBuffer)
            var deltaLast = delta - 1
            // 末尾の0を削ると、その手前にデリミタが見えるはず
            while (deltaLast >= 0 && deltaBuffer[deltaLast] == b0) --deltaLast
            // 最後のレコードかどうかでデリミタが異なる
            val delimiter = if (i + step >= end) b2 else b1
            // デリミタがないのはエラー
            if (deltaLast < 0 || deltaBuffer[deltaLast] != delimiter) {
                error("missing delimiter")
            }
            // デリミタの手前までが有効なデータ
            bao.write(deltaBuffer, 0, deltaLast)
        }
        return bao.toByteRange()
    }
}
