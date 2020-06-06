package jp.juggler.subwaytooter

import androidx.test.runner.AndroidJUnit4
import jp.juggler.util.decodeUTF8
import jp.juggler.util.encodeHex
import jp.juggler.util.encodeUTF8
import org.junit.Test
import org.junit.runner.RunWith
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@RunWith(AndroidJUnit4::class)
class TestCipherAES {
	
	private val AES_MODE = "AES/CBC/PKCS5PADDING"
	private val AES = "AES"
	
	fun genKey():ByteArray{
		val keygen = KeyGenerator.getInstance(AES)
		keygen.init(256)
		val key  = keygen.generateKey()
		return key.encoded
	}

	fun e(keyBytes:ByteArray,src:ByteArray):Pair<ByteArray,ByteArray>{
		val key = SecretKeySpec(keyBytes, AES);
		val cipher = Cipher.getInstance(AES_MODE)
		cipher.init(Cipher.ENCRYPT_MODE, key)
		val dst = cipher.doFinal( src )
		val iv = cipher.iv
		return Pair(iv,dst)
	}

	fun d(keyBytes:ByteArray,iv:ByteArray,src:ByteArray):ByteArray{
		val key = SecretKeySpec(keyBytes, AES);
		val cipher = Cipher.getInstance(AES_MODE)
		cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
		return cipher.doFinal(src)
	}
	
	@Test
	@Throws(Exception::class)
	fun test(){
		val text = "使用するアルゴリズムを自由に選択できる場合（サードパーティのシステムとの互換性を必要としない場合など）は、次のアルゴリズムを使用することをおすすめします。"
		
		val keyBytes = genKey()
		println( "keyBytes ${keyBytes.size} ${keyBytes.encodeHex()}")
		
		val(iv,encrypted ) = e( keyBytes,text.encodeUTF8())
		println( "iv ${iv.size}  ${iv.encodeHex()}")
		println( "encrypted ${encrypted.size} ${encrypted.encodeHex()}")
		
		val decrypted = d(keyBytes,iv,encrypted)
		println( "decrypted ${decrypted.size} ${decrypted.decodeUTF8()}")
		
		assert( text == decrypted.decodeUTF8() )
	}
}
