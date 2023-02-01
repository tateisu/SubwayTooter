package jp.juggler.crypt

import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.ECPointUtil
import org.bouncycastle.jce.spec.ECNamedCurveSpec
import org.conscrypt.Conscrypt
import java.math.BigInteger
import java.security.*
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

val defaultSecurityProvider by lazy {
    Conscrypt.newProvider()!!.also { Security.addProvider(it) }
}

const val DEFAULT_CURVE_NAME = "secp256r1"

const val GCM_TAG_BITS = 16 * 8

// HKDFのExpandの末尾に付与する
private val hkdfTrailBytes = ByteArray(1) { 1 }.toByteRange()

// k=v;k=v;... を解釈する
fun String.parseSemicolon() = split(";").map { pair ->
    pair.split("=", limit = 2).map { it.trim() }
}.mapNotNull {
    when {
        it.isEmpty() -> null
        else -> it[0] to it.elementAtOrNull(1)
    }
}.toMap()

// ECPrivateKey のS値を32バイトの配列にコピーする
// BigInteger.toByteArrayは可変長なので、長さの調節を行う
fun encodePrivateKeyRaw(key: ECPrivateKey): ByteArray {
    val srcBytes = key.s.toByteArray()
    return when {
        srcBytes.size == 32 -> srcBytes
        // 32バイト以内なら先頭にゼロを詰めて返す
        srcBytes.size < 32 -> ByteArray(32).also {
            System.arraycopy(
                srcBytes, 0,
                it, it.size - srcBytes.size,
                srcBytes.size
            )
        }
        // ビッグエンディアンの先頭に符号ビットが付与されるので、32バイトに収まらない場合がある
        // 末尾32バイト分を返す
        else -> ByteArray(32).also {
            System.arraycopy(
                srcBytes, srcBytes.size - it.size,
                it, 0,
                it.size
            )
        }
    }
}

/**
 * WebPushのp256dh、つまり公開鍵を X9.62 uncompressed format で符号化したバイト列を作る。
 * - 出力の長さは65バイト
 */
fun encodeP256Dh(src: ECPublicKey): ByteArray = src.run {
    val bitsInByte = 8
    val keySizeBytes = (params.order.bitLength() + bitsInByte - 1) / bitsInByte
    return ByteArray(1 + 2 * keySizeBytes).also { dst ->
        var offset = 0
        dst[offset++] = 0x04
        w.affineX.toByteArray().let { x ->
            when {
                x.size <= keySizeBytes ->
                    System.arraycopy(
                        x, 0,
                        dst, offset + keySizeBytes - x.size,
                        x.size
                    )

                x.size == keySizeBytes + 1 && x[0].toInt() == 0 ->
                    System.arraycopy(
                        x, 1,
                        dst, offset,
                        keySizeBytes
                    )

                else -> error("x value is too large")
            }
        }
        offset += keySizeBytes
        w.affineY.toByteArray().let { y ->
            when {
                y.size <= keySizeBytes -> System.arraycopy(
                    y, 0,
                    dst, offset + keySizeBytes - y.size,
                    y.size
                )

                y.size == keySizeBytes + 1 && y[0].toInt() == 0 -> System.arraycopy(
                    y, 1,
                    dst, offset,
                    keySizeBytes
                )
                else -> error("y value is too large")
            }
        }
    }
}

// JavaScriptのcreateECDHがエンコードした秘密鍵をデコードする
// https://github.com/nodejs/node/blob/main/lib/internal/crypto/diffiehellman.js#L232
// https://github.com/nodejs/node/blob/main/src/crypto/crypto_ec.cc#L265
fun Provider.decodePrivateKeyRaw(srcBytes: ByteArray): ECPrivateKey {
    // 符号拡張が起きないように先頭に０を追加する
    val newBytes = ByteArray(srcBytes.size + 1).also {
        System.arraycopy(
            srcBytes, 0,
            it, it.size - srcBytes.size,
            srcBytes.size
        )
    }

    val s = BigInteger(newBytes)

    // テキトーに鍵を作る
    val keyPair = generateKeyPair()
    // params部分を取り出す
    val ecParameterSpec = (keyPair.private as ECPrivateKey).params
    // s値を指定して鍵を作る
    val privateKeySpec = ECPrivateKeySpec(s, ecParameterSpec)
    return KeyFactory.getInstance("EC", this)
        .generatePrivate(privateKeySpec) as ECPrivateKey
}

/**
 * ECの鍵ペアを作成する
 */
fun Provider.generateKeyPair(curveName: String = DEFAULT_CURVE_NAME): KeyPair =
    KeyPairGenerator.getInstance("EC", this).apply {
        @Suppress("SpellCheckingInspection")
        initialize(ECGenParameterSpec(curveName))
    }.genKeyPair() ?: error("genKeyPair returns null")

fun Mac.update(br: ByteRange) = update(br.ba, br.start, br.size)

fun hmacSha256(salt: ByteRange, keyMaterial: ByteRange, plus1: Boolean = false) =
    Mac.getInstance("HMacSHA256").run {
        init(SecretKeySpec(salt.ba, salt.start, salt.size, "HMacSHA256"))
        update(keyMaterial)
        if (plus1) update(hkdfTrailBytes)
        doFinal()
    } ?: error("Mac.doFinal returns null")

/**
 * HKDF の2段階目は末尾に \01 を追加する
 */
fun hmacSha256Plus1(salt: ByteRange, keyMaterial: ByteRange) =
    hmacSha256(salt, keyMaterial, plus1 = true)

// 鍵全体をX509でエンコードしたものをデコードする
fun Provider.decodePrivateKeyX509(src: ByteArray) =
    KeyFactory.getInstance("EC", this)
        .generatePrivate(PKCS8EncodedKeySpec(src))
        ?: error("generatePrivate returns null")

/**
 * p256dh(65バイト)から公開鍵を復元する
 * - ECPointUtil.decodePoint はバイト配列全体を指定するしかないので、src引数はByteArrayである
 */
fun Provider.decodeP256dh(src: ByteArray, curveName: String = DEFAULT_CURVE_NAME): ECPublicKey {
    val spec = ECNamedCurveTable.getParameterSpec(curveName)
    val params = ECNamedCurveSpec(curveName, spec.curve, spec.g, spec.n)
    val pubKeySpec = ECPublicKeySpec(
        ECPointUtil.decodePoint(params.curve, src),
        params
    )
    return KeyFactory.getInstance("EC", this)
        .generatePublic(pubKeySpec) as ECPublicKey
}

fun Provider.sharedKeyBytes(
    receiverPrivateBytes: ByteArray,
    senderPublicBytes: ByteArray,
) = KeyAgreement.getInstance("ECDH", this).run {
    val receiverPrivate = decodePrivateKeyX509(receiverPrivateBytes)
    val senderPublic = decodeP256dh(senderPublicBytes)
    init(receiverPrivate)
    doPhase(senderPublic, true)
    generateSecret()
}.toByteRange()
