package jp.juggler.crypt

import jp.juggler.util.data.encodeUTF8
import java.io.ByteArrayOutputStream
import java.security.Provider
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Content-Encoding: aesgcm のデコード。
 * 中間状態のテストがしたいので状態をもつクラスとして実装した
 */
class AesGcmDecoder(
    // receiver private key in X509 format
    receiverPrivateBytes: ByteArray,
    // receiver public key in 65bytes X9.62 uncompressed format, created at subscription
    private val receiverPublicBytes: ByteArray,
    // auth secret, created at subscription
    authSecret: ByteArray,
    // sender public key in 65bytes X9.62 uncompressed format
    private val senderPublicBytes: ByteArray,
    // salt in HTTP header
    saltBytes: ByteArray,
    // provider for security functions
    private val provider: Provider = defaultSecurityProvider,
) {
    companion object {
        private const val b0 = 0.toByte()

        private val infoCeAuthZ = "Content-Encoding: auth\u0000".encodeUTF8().toByteRange()
        private val prefixCeAesGcmZP256Z = "Content-Encoding: aesgcm\u0000P-256\u0000".encodeUTF8()
        private val prefixCeNonceZP256Z = "Content-Encoding: nonce\u0000P-256\u0000".encodeUTF8()

        fun ByteArrayOutputStream.writeLengthAndBytes(b: ByteArray) {
            val len = b.size
            write(len.shr(8).and(255))
            write(len.and(255))
            write(b)
        }
    }

    private val salt = saltBytes.toByteRange()

    val auth = authSecret.toByteRange()

    val sharedKeyBytes = provider.sharedKeyBytes(
        receiverPrivateBytes = receiverPrivateBytes,
        senderPublicBytes = senderPublicBytes,
    )

    //
    var ikm = ByteRange.empty

    @Suppress("MemberVisibilityCanBePrivate")
    var prk = ByteRange.empty
    var key = ByteRange.empty
    var nonce = ByteRange.empty

    // The start index for each element within the buffer is:
    // value               | length | start    |
    // -----------------------------------------
    // 'Content-Encoding: '| 18     | 0        |
    // type                | len    | 18       |
    // nul byte            | 1      | 18 + len |
    // 'P-256'             | 5      | 19 + len |
    // nul byte            | 1      | 24 + len |
    // client key length   | 2      | 25 + len |
    // client key          | 65     | 27 + len |
    // server key length   | 2      | 92 + len |
    // server key          | 65     | 94 + len |
    private fun createInfo(
        prefix: ByteArray,
        clientPublicKey: ByteArray, // 65 byte
        serverPublicKey: ByteArray, // 65 byte
    ) = ByteArrayOutputStream(120).apply {
        write(prefix)
        // For the purposes of push encryption the length of the keys will always be 65 bytes.
        writeLengthAndBytes(clientPublicKey)
        writeLengthAndBytes(serverPublicKey)
    }.toByteRange()

    fun deriveKey() {
        // input key material の導出はWebPushの仕様に依存する
        ikm = hmacSha256Plus1(
            hmacSha256(auth, sharedKeyBytes).toByteRange(),
            infoCeAuthZ
        ).toByteRange(end = 32)

        // ikm, salt から prk, key, nonce を導出する

        prk = hmacSha256(salt, ikm).toByteRange()

        key = hmacSha256Plus1(
            prk,
            createInfo(
                prefix = prefixCeAesGcmZP256Z,
                clientPublicKey = receiverPublicBytes,
                serverPublicKey = senderPublicBytes,
            )
        ).toByteRange(end = 16)

        nonce = hmacSha256Plus1(
            prk,
            createInfo(
                prefix = prefixCeNonceZP256Z,
                clientPublicKey = receiverPublicBytes,
                serverPublicKey = senderPublicBytes,
            )
        ).toByteRange(end = 12)
    }

    fun decode(src: ByteRange): ByteRange {

        val cip = Cipher.getInstance("AES/GCM/NoPadding", provider)
        cip.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key.ba, key.start, key.size, "AES"),
            GCMParameterSpec(
                GCM_TAG_BITS,
                nonce.ba, nonce.start, nonce.size
            ),
        )
        var dst = cip.doFinal(src.ba, src.start, src.size).toByteRange()

        // 多分不要だと思うが、
        // https://greenbytes.de/tech/webdav/draft-ietf-httpbis-encryption-encoding-02.html
        // にあるレコード先頭のパディングを除去する
        // テキストメッセージなら nul文字が含まれないのでヒットしないはず
        if (dst.size >= 3 && dst[2] == b0) {
            val reader = dst.byteRangeReader()
            val padLen = 2 + reader.readUInt16()
            reader.skip(padLen)
            dst = reader.remainBytes()
        }

        // 先頭の空白を除去する
        var i = 0
        while (i < dst.size && dst[i] in 0..32) ++i
        if (i > 0) dst = dst.subRange(start = i)

        return dst
    }
}
