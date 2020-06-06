package jp.juggler.subwaytooter.util

import jp.juggler.util.LogCategory
import jp.juggler.util.decodeUTF8
import jp.juggler.util.encodeHex
import jp.juggler.util.encodeUTF8
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// OlmAccount のシリアライズに使うAES暗号化
object CipherAES {
	
	private val log = LogCategory("CipherAES")
	
	private const val AES_MODE = "AES/CBC/PKCS5PADDING"
	private const val AES = "AES"
	
	fun genKey() : ByteArray {
		val keygen = KeyGenerator.getInstance(AES)
		keygen.init(256)
		val key = keygen.generateKey()
		return key.encoded
	}
	
	private fun encryptCipher(keyBytes : ByteArray) : Cipher =
		Cipher.getInstance(AES_MODE).apply {
			init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, AES))
		}
	
	private fun decryptCipher(keyBytes : ByteArray, iv : ByteArray) : Cipher =
		Cipher.getInstance(AES_MODE).apply {
			init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, AES), IvParameterSpec(iv))
		}
	
	fun e(keyBytes : ByteArray, src : ByteArray) : Pair<ByteArray, ByteArray> {
		val cipher = encryptCipher(keyBytes)
		val dst = cipher.doFinal(src)
		val iv = cipher.iv
		return Pair(iv, dst)
	}
	
	fun d(keyBytes : ByteArray, iv : ByteArray, src : ByteArray) =
		decryptCipher(keyBytes, iv).doFinal(src) !!
	
	fun test() {
		val text = "使用するアルゴリズムを自由に選択できる場合（サードパーティのシステムとの互換性を必要としない場合など）は、次のアルゴリズムを使用することをおすすめします。"
		
		val keyBytes = genKey()
		log.d("keyBytes ${keyBytes.size} ${keyBytes.encodeHex()}")
		// keyBytes 32 26a6af8dd4f772bbcf8423528e37c56f8d0e72c4ab8ecf5a6e356e9d9831e33f
		
		val (iv, encrypted) = e(keyBytes, text.encodeUTF8())
		log.d("iv ${iv.size}  ${iv.encodeHex()}")
		log.d("encrypted ${encrypted.size} ${encrypted.encodeHex()}")
		// iv 16  c3068c95d2be30e4fa4211628be97a7f
		// encrypted 240 d82139abd8d238f8ef1acea71118f563fb5e707dc89518c29e7a…
		
		val decrypted = d(keyBytes, iv, encrypted)
		log.d("decrypted ${decrypted.size} ${decrypted.decodeUTF8()}")
		// decrypted 231 使用するアルゴリズムを自由に選択でき…
	}
}
