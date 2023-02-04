package jp.juggler

import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.juggler.crypt.*
import jp.juggler.util.data.decodeBase64
import jp.juggler.util.data.decodeJsonObject
import jp.juggler.util.log.LogCategory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.interfaces.ECPrivateKey

@Suppress("SpellCheckingInspection")
@RunWith(AndroidJUnit4::class)
class WebPushCryptTest {
    companion object {
        private val log = LogCategory("WebPushCryptTest")
    }

    // https://developers.google.com/web/updates/2016/03/web-push-encryption

    @Test
    fun testGenerateKeyPair() {
        val provider = defaultSecurityProvider
        val pair = provider.generateKeyPair()
        val privateKey = (pair.private as ECPrivateKey)
        val bytes = encodePrivateKeyRaw(privateKey)
        val newKey = provider.decodePrivateKeyRaw(bytes)
        assertEquals(
            "s is same?",
            privateKey.s,
            newKey.s,
        )
    }

    @Test
    fun decryptMessage() {
        // Authentication secret (auth_secret)
        // 購読時に指定する
        val authSecret = "BTBZMqHH6r4Tts7J_aSIgg".decodeBase64()

        // User agent public key (ua_public)
        // 購読時に指定する
        val receiverPublicBytes =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4".decodeBase64()

        // User agent private key (ua_private)
        val receiverPrivateBytesShort = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94".decodeBase64()

        // encryption ヘッダから
        val saltBytes = "pr1_1DFjrzX3RNvJPRngDA".decodeBase64()

        // Application server public key (as_public)
        // crypto-key ヘッダの前半、dh=xxx; の中味
        // crypto-key: dh=BLJQjupjQyujhTC--_5xRUgcBfAP_zINsAGTlaDuEME7s9TVgQYsyrzrgbt1vqScmZkoj4BWfPit6EzzaXDW02I;p256ecdsa=BDmWlrZ3gvcv0R7sBhaSp_99FRSC3bBNn9CElRvbcviwYwVPL1Z-G9srAJS6lv_pMe5IkTmKgBWUCNefnN3QoeQ
        val senderPublicBytes =
            "BLJQjupjQyujhTC--_5xRUgcBfAP_zINsAGTlaDuEME7s9TVgQYsyrzrgbt1vqScmZkoj4BWfPit6EzzaXDW02I"
                .decodeBase64()

        val body =
            "pTTuh1jT8KJ4zaGwIWjg417KTDzh+eIVe472nMgett3XyhoM5pAz8Yu2RPBXJHE/AojoMA1g+/uzbByu3d1/AygBh99qJ6Xtjya+XBSYoVrNJqT7vq0cKU9bZ8NrEepnaZUc2HjFUDDXNyHi2xBtJnMk/hSZTzyaiCQS2KssGAwixgdK/dTP8Yg+Pul3tgOQvq5CbYFd7iwBQntVv80vO8X+5hyIglA21+6/2fq5lCZSMri5K9/WbSb6erLkxO//A92KjZTnuufE4pUwtIdYW1bFnw5xu6ozjsCsDLbQTSo+JmghOzc/iYx5hG+y5YViC1UXue4eKKlmjbVDRLH6WkEEIKH2cwd4Gf9ewhYwhH7oKKIc4tjvRunq2gtBirQgRYJahgfwykdYA44iyogBc1rFZPGbxr1ph4RxVhdBmIZ+yMN6GQSiDCS+8jKGsc5xnjxrSXXdFva1a2xc1lpiReypZlTTXFmF16Cf+Z6B0UvFTa2AcqEDD0BBlhhbMBoG7n4CRjr5ObE2lG5PBg+gqitx/O1S+X8a4N78L+eK1upEVM+HRQAdCmiqDNJF0/N/VWSMrNCl7HNgnhmYU9Z1aYepiEioz1Tu14UzY/2NOx5z4h4szyJW8s/diAyOhnh+RBRM3QLHtygpLZ3i7o6vVUc="
                .decodeBase64()

        AesGcmDecoder(
            // receiver private key in X509 format
            receiverPrivateBytes =
            defaultSecurityProvider.decodePrivateKeyRaw(receiverPrivateBytesShort).encoded,
            // receiver public key in 65bytes X9.62 uncompressed format
            receiverPublicBytes = receiverPublicBytes,
            // sender public key in 65bytes X9.62 uncompressed format
            senderPublicBytes = senderPublicBytes,
            // auth secrets created at subscription
            authSecret = authSecret,
            // salt in HTTP header
            saltBytes = saltBytes,
        ).run {
            assertEquals(
                "sharedKeyBytes",
                "irnQ9JOfMP/kl/SB8LUHpvjmUjwlkYzypisDnlHVKSA=",
                sharedKeyBytes.encodeBase64()
            )

            deriveKey()

            assertEquals(
                "ikm",
                "Tq5bGvBQUWTQdqFfAK1WAE/etlIpcc07QLh+RNCAD9Y=",
                ikm.encodeBase64()
            )

            assertEquals(
                "contentEncryptionKey",
                "patR3W6rY0PG/5YnwY3/kA==",
                key.encodeBase64()
            )

            assertEquals(
                "nonce",
                "E/GxDDwW9lfa7/by",
                nonce.encodeBase64()
            )
            decode(body.toByteRange())
        }.decodeUTF8().let {
            assertEquals(
                "text",
                """{"title":"あなたのトゥートが tateisu 🤹 さんにお気に入り登録されました","image":null,"badge":"https://mastodon2.juggler.jp/badge.png","tag":84,"timestamp":"2018-05-11T17:06:42.887Z","icon":"/system/accounts/avatars/000/000/003/original/72f1da33539be11e.jpg","data":{"content":":enemy_bullet:","nsfw":null,"url":"https://mastodon2.juggler.jp/web/statuses/98793123081777841","actions":[],"access_token":null,"message":"%{count} 件の通知","dir":"ltr"}}""",
                it
            )
        }
    }

    /**
     * 今どきのMastodonはCrypto-Keyが異なる
     * dh=XXX;p256ecdsa=XXX
     */
    @Test
    fun test2() {
        // 購読時に分かった情報
        val receiverPrivateBytes =
            "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgN1UVSn5P4XC9zuuT3sW8TTikA8AhsjZKp9W6BwzSlW2hRANCAAQSFVIOe_wsdFuDCKjrNRM1yWIkwlfx8ZoQ3OyYJ2K5oeXpmjo5EAimVq0Fs0NuxA8Y6F1hTB_Orc4gR1WOK-W4"
                .decodeBase64()
        val receiverPublicBytes =
            "BBIVUg57_Cx0W4MIqOs1EzXJYiTCV_HxmhDc7JgnYrmh5emaOjkQCKZWrQWzQ27EDxjoXWFMH86tziBHVY4r5bg"
                .decodeBase64()
        val senderPublicBytesOld =
            "BLzIgsz-VRhTxuVgNoQljTwAFzxbanfGxNk8tldaruBztvsK9elES_2lE_8c91-RNOInEBEFUrCzDw-60bzUCr8"
                .decodeBase64()
        val authSecret = "WrgqiWu8r3D9Ql3qYGkXTw"
            .decodeBase64()

        // プッシュメッセージに含まれる情報
        val headerJson = """{
            |"Digest":"SHA-256=kN1u9yotvg3utprFOaJ4qUGJ6cRhxGNfICXAGLjx7uM=",
            |"Content-Encoding":"aesgcm",
            |"Encryption":"salt=xtC3F3tO3UvQBtq-4Q38AQ",
            |"Crypto-Key":"dh=BLivX-rukpGqww9YMqavS7o112MNTobqBqjzPX1ioUojdrDHKM1DwZKn6U2au0ddohfC4BGTDatjF96S7dOP2C8;p256ecdsa=BLzIgsz-VRhTxuVgNoQljTwAFzxbanfGxNk8tldaruBztvsK9elES_2lE_8c91-RNOInEBEFUrCzDw-60bzUCr8",
            |"Authorization":"WebPush eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJhdWQiOiJodHRwczovL21hc3RvZG9uLW1zZy5qdWdnbGVyLmpwIiwiZXhwIjoxNjc0ODQ3MjQ4LCJzdWIiOiJtYWlsdG86dGF0ZWlzdStqdWdnbGVyQGdtYWlsLmNvbSJ9.ksn_40KkHNcS0G3C8jMG-dsA6AcUydYYc1sA8JyXCCuDrNr-CLnl_-ezk1s-pC75yukNN0pIfqPJlJYtY_MQoQ",
            |"<>cameFrom":"unifiedPush"
            |}""".trimMargin().decodeJsonObject()

        val body =
            "4BVejMFrVADAEzkonO1QqUnDLiWCGAlzSbWg1KkG23BMZNLxUcQPJtULTTEMdoIzg4zZwDmsanDp5fepH8BDKpb5IoQlyFJjN0pq3tebWj79QL9h7QafxPByGx9HF9-zKlOUbmPMc3yS1tfccz30HvVSsMNUtcpuy1hsPkknwRBPI_SoLN20LEtAQ6IKLQGpiNMDx2rND9bd9wLz0Cr4pxUgZVxv0VjpiKHyTup0_BRotGd5e719uMmyDE-hzrqQYRr2QFrvHa1tNk41tpbPpeSAqhihsxU6bXxc4Lbws1Q6DyZBYHSUr6Av4SldA2Kmi3EjLijRvz3YEDWnSGv65V0SJv-O7tmFjexqFmFcF0qfv4Udzl9prjmutztcaWRAH-v9JdP3o2kthpK8RM4NC6Y0yU3GhEQ0z_1cfsuWsAvRe3H8pVcx3GXHVdWJ7VfqAwI-anSZz355-cuBbxAyrsBSp1Ysfru2D_5g53SZ7iNduHsXfKno9e6BkQr6u5pleXVHpRHYimzsjj46wbA0IfvEkhBpp9CoJ3JzPFkBowxpzuzmN1_IDiSDoYWVMYl1GXtwWOmKMKaKOCh3ULk2FH1WdUzNlvMd1N4Kf-MXjHFhkE33jSlLnKRLHqgzDEJgofWEzfoV-zruHFQfKwiN_7_NJdLYuGVV3z0IFiw6g9RdIvrNy7OEsTCaXbWC-xOEelJAvALvu308t_0PxxRdhSLVivrubyLZEKz1R8d6MzJ9b3f_mg6oYiGJ1u5oMrUvUUeNCERq4t9f0WMLo-zLFkNXvGe2TxnKMO9ZsWlXS1OnKnN6olNPKfVAFW50RxD6nBdP3S2a9uI58D9ycanCU_-i8osFrKg-GbYZ2tvRZw"
                .decodeBase64()

        // Encryption からsaltを読む
        val saltBytes = headerJson.string("Encryption")?.parseSemicolon()
            ?.get("salt")?.decodeBase64()
            ?: error("missing Encryption.salt")

        // Crypt-Key から dh と p256ecdsa を見る
        val cryptKeys = headerJson.string("Crypto-Key")?.parseSemicolon()
            ?: error("missing Crypto-Key")

        val senderPublicBytes = cryptKeys["dh"]?.decodeBase64()
            ?: senderPublicBytesOld

        // JWT検証で使われるらしい
        // val p256ecdsa = cryptKeys["p256ecdsa"]?.decodeBase64()
        //   ?: error("missing p256ecdsa")

        AesGcmDecoder(
            // receiver private key in X509 format
            receiverPrivateBytes = receiverPrivateBytes,
            // receiver public key in 65bytes X9.62 uncompressed format
            receiverPublicBytes = receiverPublicBytes,
            // sender public key in 65bytes X9.62 uncompressed format
            senderPublicBytes = senderPublicBytes,
            // auth secrets created at subscription
            authSecret = authSecret,
            // salt in HTTP header
            saltBytes = saltBytes,
        ).run {
            deriveKey()
            decode(body.toByteRange())
        }.decodeUTF8().let {
            assertEquals(
                "text",
                """{"access_token":"ON0yBbjmRS-QuSk5Uv7fjeKFVQESnYCZP39z71On68E","preferred_locale":"ja","notification_id":341159,"notification_type":"favourite","icon":"https://m1j.zzz.ac/accounts/avatars/000/008/939/original/112ed1e5343f2e7b.png","title":"tateisu⛏️@テスト鯖 :ct080:さんにお気に入りに登録されました","body":"クライアントアプリにAPIキーとシークレットを持たせるという考え方は、クライアント側でシークレットが如何に漏洩しやすいかを考えると悪手としかいいようがない。簡単に漏洩して、アプリと無関係なbotに流用/悪用される"}""",
                it
            )
        }
    }

    // https://github.com/web-push-libs/ecec/blob/master/src/decrypt.c

    @Test
    fun testRfc8188() {
        val body = "I1BsxtFttlv3u_Oo94xnmwAAEAAA-NAVub2qFgBEuQKRapoZu-IxkIva3MEB1PD-ly8Thjg"
            .decodeBase64()
        val inputKey = "yqdlZ-tYemfogSmv7Ws5PQ"
            .decodeBase64()

        Aes128GcmDecoder(body.byteRangeReader()).run {
            log.i("recordSize=$recordSize, keyId.size=${keyId.size}")

            assertEquals(
                "salt",
                "I1BsxtFttlv3u_Oo94xnmw",
                salt.encodeBase64Url()
            )
            assertEquals(
                "keyId",
                "",
                keyId.encodeBase64Url()
            )

            deriveKeyRfc8188(inputKey.toByteRange())

            assertEquals(
                "prk",
                "zyeH5phsIsgUyd4oiSEIy35x-gIi4aM7y0hCF8mwn9g",
                prk.encodeBase64Url()
            )
            assertEquals(
                "key",
                "_wniytB-ofscZDh4tbSjHw",
                key.encodeBase64Url()
            )
            assertEquals(
                "NONCE",
                "Bcs8gkIRKLI8GeI8",
                nonce.encodeBase64Url()
            )
            decode()
        }.decodeUTF8().let {
            assertEquals(
                "text",
                "I am the walrus",
                it
            )
        }
    }

    @Test
    fun testRfc8188b() {
        val inputKey = "BO3ZVPxUlnLORbVGMpbT1Q"
            .decodeBase64()

        val rawBody =
            "uNCkWiNYzKTnBN9ji3-qWAAAABkCYTHOG8chz_gnvgOqdGYovxyjuqRyJFjEDyoF1Fvkj6hQPdPHI51OEUKEpgz3SsLWIqS_uA"
                .decodeBase64()

        Aes128GcmDecoder(rawBody.byteRangeReader()).run {
            log.i("recordSize=$recordSize, keyId.size=${keyId.size}, encryptedContent.size=${encryptedContent.size}")
            deriveKeyRfc8188(inputKey.toByteRange())
            decode()
        }.decodeUTF8().let {
            assertEquals(
                "text",
                "I am the walrus",
                it
            )
        }
    }

    @Test
    fun testMisskey13() {
        val receiverPrivateBytes =
            "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgr18ZyNLsRg71vHNxBBGFxeIvV497YUaONPJL1EOKLGChRANCAAQUrMEJuCxzjD-S3xDlcgAvmnbKQZVQzkTCkOcKLSHgGopshtc3ETyrD7m7fhuZAlV1KXJVqHVBtMwrwvZ5xIyw"
                .decodeBase64()
        val receiverPublicBytes =
            "BBSswQm4LHOMP5LfEOVyAC-adspBlVDORMKQ5wotIeAaimyG1zcRPKsPubt-G5kCVXUpclWodUG0zCvC9nnEjLA"
                .decodeBase64()
//        val senderPublicBytes =
//            "BBtZLosjNMsuFAQg0QHYIgDNKG6IC_yxMYW1Tx_Cx20FbqEbAt_KN56zLc48yJxmOJhMkjR5Kf68bEIugNQ-wWU"
//                .decodeBase64()
        val authSecret = "x6yts9DKC4ZnnHdPHgiwkQ"
            .decodeBase64()

//        val headerJson = """{
//            |"TTL":"2419200",
//            |"Content-Encoding":"aes128gcm",
//            |"Authorization":"vapid t=eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJhdWQiOiJodHRwczovL21hc3RvZG9uLW1zZy5qdWdnbGVyLmpwIiwiZXhwIjoxNjc0OTU4MTMyLCJzdWIiOiJodHRwczovL2RyZHIuY2x1YiJ9.I9xIycRJXiWYTZQlsTiS5RyceJNXGbrT8N-rac7Q5UouTWnZUmNbxEknOQigZV71qY3DJ_S7RomXJW3p1KwNmQ, k=BBtZLosjNMsuFAQg0QHYIgDNKG6IC_yxMYW1Tx_Cx20FbqEbAt_KN56zLc48yJxmOJhMkjR5Kf68bEIugNQ-wWU",
//            |"<>cameFrom":"fcm"
//            |}""".trimMargin()
        val rawBody =
            "0VUgniuATYrYU481rjSNBAAAEABBBK66EO_qZXsRw0Clzv0LF-SxMe5qRmCgKrZHApIc5ZX_XsXmzBg1rU827Hp9BSdXEWaIaBYAYROjTQfSuHtylP1H0_EOEMNdqswJpEVdVVQrfC_7JAxNAOlXUz4QU3oKl3yhSTlp6M4kFFaBfPrn2SyFI4f5w0wIRATH-Ck0shakAp5hKD7nVwvzEmh3wg8Vug8gPRmGhOPet6DzG5rhLtPng5OJ2eFQUB00duVZyW8TgZ8iPd0GYZkaHw2b16OBN3doLPKiYN_OL-JKVmWutMzivG52DDQ_XqbaFfu5dRLWHkyipq078-nDL6yw9_tNQCdDcVGKx1llxHI6FuPbYgF_RJ8PNZ1Sj-sMRdGtQqcsFH0WDWJu_JBqHSYvBxjEhhDv4qepQTYDl3MYVI-EIbQLIWuCZRo4FRkd594endP6IQ2LH950EE4AMeVHmdxENGELJidrF4eE8reHWf5We0_Jj1m92L8ZJ--z_FMHdOEGlCHwFp1IQPNS-SF6CPZVdrzxjLMo12CELNjh1g9bKxA2ld7OBYcVb3nt6uWBBgiJEqSXEVjzv2JhoHMh3lHLyFngoddfL55aDEVe4gTOwhflPSiKO-8nQd87KLQ_vi4r-SItRzQXhXcI0v7ryFubBwWQrRmRaIaDrMgheEJ50caWgeaDoTXtgkorG63q6KIfkpBlyDyq3YaKpXHJ7rmr-1BYP0Z8PS2O5bqRFyLgtLXIn5a3da_XlTnjdHJH7_N0c8WxVRow5Z5Qzlj790FuGrMQQ8c6SrAC7lTmt6_wq-Ej3SH53jN0HumZ7v4pmkqCR5o7SJYHAgMx9KUeW-Rwr9k3XKVS9MMP9wPhke0Iy51LOfDZ_U6tHVQ_QEkEb1lk1eNSNo0ZSna1vhXh5WB8Pnm3Skxa1k4qvQFKjdNFOZZ57lXqG42oEmplZirhiBgvf40sWmFs_s6_u4tTkD1jagHlrus8l7U5T-34RpzOeqM3z5wqjylBJyubwfEA_7C3WWYOw5U0bduP7QLgFGGzVmu3qAAcQgfHOOehgWXc95j50jXjfV9XD3znXX3WLY0R4qdkWcivQMSxL3PATh1a9fgZJs-SRvH7fW5SrEK3Cj9wAXLfOOcL9gP519eW4sPN-P3xupAfgxXblolBvx1wmdtL51ey1X02cOK_EyFS0pl5D_1vaL6ncq6RDfP2emD-U4jSBEOJbWpzINr9xUnRBLxGYbRZfSSqjdFZGO49JKOW_h9vtZT-NAuPaar0v0Qfz_qF5_-pyo8dQpfNGxZ1ZTzlMwy4v6l18RRAZx2smbgNVmSe2uUCDhcFhO_Y9a9wlbbhYUesenR0g-fbUvVGSdz3V2hoTanQJLwOSbigIFmgMTUnOGPs4uv4ynXleDR6h6zoZ5WwuR8i06AjYq9M5E3nlmxoGKebX_6r1p0Fg0R8QRiz9UmRtxn-G72eLEy5351ZA4xEZrvm4eYoyS7t3BRta0R_4qKFdNONv46ISIj3TU3sUNo2ktIbZE8VjDomxqfgLugbx1Oyvipa9xTFyaJBzhSxxwfdzLlfxdLdrGoh2OseBY56_WR99yh5x6q2UQDo6lyqjzIa8nY_EptfqQmzgCvUJYGmRqXi6Cp5bEyuUvxZ_W-paJoMT5ZW_IjVrSkqYm-oxOkGzKsaAjLmA0e9Qek7uZlLZ9KPl56ON7AHqli0qHi5ZaoosIMVMd6GN0DUfiFHpimGmHw63W0xwArEr0sWf3Wqclk0b3BS7o4oRPMjtjersoFtqxaJ8xJpV4c3UwNjXz9eBQFtJZ_NR1jv_4Kuet3d78RojWEo4OGP9Y7VbPc77yekqm1f1Fxi68w-aZFgL6ClDsy1eAH5HQmYa5KzylTYOt3ZQDXGARiFXR3N0isKPsBBcXZkIzcrKEccfBhYxm2ru3ruNnwMZ4XKzmq5SLRfhxvsCspPaiW3N7oKEbX86ONoAL_i6kcpxAn8iY_XZ36ZyOD_cDkG0OesIdbc4hRcXaLSWmvknXy1KUCbwbe9pMx7HiYn5u_8vgtSELlWQdagc2T3SiD8-gqXTp3RmwwAJSizme2XL9RcjkZOtQnItpNbCwON5afYM85OEKFQkatnt6d8rm2WhoZZ8lU03zD9Kt2Tn9vbkhDYcy_ZE0RHERQLtl5Q4GT81J8RpOEjeJTt2egGFI0PC_tx5MNl_Piy6ows7ly24TsEjNoDJQpnowk_S4OLbN2fhpr1-yhfhbQ-j447FcYkEACjW5t74X4ey8iT88rVMmgefDx7o7NYWW7aKd6vfPpe7EmYiERxCDPYhzQhuKk8vm3FoWM2C6cuuzq1ElUraA70SM9smuwOBJFqgs7ZosIjhb7BdrMiy8lILejHKkTyl-lbhKNzGvO4EIgeHZRn8vzdDmwIINQskT3KgPMpXw"
                .decodeBase64()

        Aes128GcmDecoder(rawBody.byteRangeReader()).run {
            deriveKeyWebPush(
                // receiver private key in X509 format
                receiverPrivateBytes,
                // receiver public key in 65bytes X9.62 uncompressed format
                receiverPublicBytes,
                // auth secrets created at subscription
                authSecret,
            )
            decode()
        }.decodeUTF8().let {
            assertEquals(
                "text",
                """{"type":"notification","body":{"id":"9ajlf15org","createdAt":"2023-01-28T14:08:50.844Z","type":"reaction","isRead":false,"userId":"8fhziu1e3v","user":{"id":"8fhziu1e3v","name":"tateisu⛏️@テスト鯖 :ct080:","username":"tateisu","host":"mastodon2.juggler.jp","avatarUrl":"https://m2j.zzz.ac/accounts/avatars/000/000/001/original/e75c1f2674f44551.png","avatarBlurhash":"yDHUW|V]02t30otQ_H?XoeD,WBt7jbt2tMocN2Rn%Fk9nTE1kB%2e:X3bFRVR:ayWBj@%0fkRR?Ej[E3axt7j]ob9Oay%Fj[xajGIW","isBot":true,"isCat":false,"instance":{"name":"ジャグ鯖(テスト用)","softwareName":"mastodon","softwareVersion":"3.5.3","iconUrl":"https://mastodon2.juggler.jp/packs/media/icons/android-chrome-36x36-d64a2e909b4574fb02214762c513032d.png","faviconUrl":"https://mastodon2.juggler.jp/packs/media/icons/favicon-48x48-28b6709639acd18287a854f974311336.png","themeColor":"#6364ff"},"emojis":{"ctf00":"https://m2j.zzz.ac/custom_emojis/images/000/007/937/original/ctf00.png","ct080":"https://m2j.zzz.ac/custom_emojis/images/000/004/225/original/ct080.png","ctff0":"https://m2j.zzz.ac/custom_emojis/images/000/008/177/original/ctff0.png"},"onlineStatus":"unknown"},"note":{"id":"99tzth0zfn","createdAt":"2023-01-10T16:09:58.643Z","userId":"99tcttijnu","text":":thinking_woozy_portal_orange:​:thinking_garbled:​","visibility":"public","localOnly":false,"renoteCount":0,"repliesCount":2,"reactions":{"👍":1,":revbunhdthinking@.:":1,":ainers_misskeyio@misskey.io:":1},"reactionEmojis":{"ainers_misskeyio@misskey.io":"https://s3.arkjp.net/misskey/ea176d9b-a92a-4ec2-b11c-defdac551156.apng"},"fileIds":[],"files":[],"replyId":null,"renoteId":null,"myReaction":":revbunhdthinking@.:"},"reaction":"👍"},"userId":"99tcttijnu","dateTime":1674914932947}""",
                it,
            )
        }
    }
}
