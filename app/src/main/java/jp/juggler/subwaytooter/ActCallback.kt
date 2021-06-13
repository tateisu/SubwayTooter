package jp.juggler.subwaytooter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import jp.juggler.util.LogCategory
import jp.juggler.util.digestSHA256Hex
import okhttp3.internal.toHexString
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.atomic.AtomicReference

class ActCallback : AppCompatActivity() {
	
	companion object {
		private val log = LogCategory("ActCallback")
		
		internal val last_uri = AtomicReference<Uri>(null)
		internal val sent_intent = AtomicReference<Intent>(null)
		
		private fun String?.isMediaMimeType() = when {
			this == null -> false
			this.startsWith("image/") -> true
			this.startsWith("video/") -> true
			this.startsWith("audio/") -> true
			else -> false
			
		}
	}
	
	override fun onCreate(savedInstanceState : Bundle?) {
		log.d("onCreate flags=0x${intent.flags.toHexString()}")
		super.onCreate(savedInstanceState)

		var intent : Intent? = intent
		when {
			intent == null -> {
				// 多分起きないと思う
			}
			
			(intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0 -> {
				// 履歴から開いた場合はIntentの中味を読まない
			}
			
			else -> {
				val action = intent.action
				val type = intent.type
				if(
				// ACTION_SEND か ACTION_SEND_MULTIPLE
					Intent.ACTION_SEND == action
					|| Intent.ACTION_SEND_MULTIPLE == action
					// ACTION_VIEW かつ  type が 画像かビデオか音声
					|| Intent.ACTION_VIEW == action && type.isMediaMimeType()) {
					
					// Google Photo などから送られるIntentに含まれるuriの有効期間はActivityが閉じられるまで
					// http://qiita.com/pside/items/a821e2fe9ae6b7c1a98c
					
					// 有効期間を延長する
					intent = remake(intent)
					if(intent != null) {
						sent_intent.set(intent)
					}
				} else {
					val uri = intent.data
					if(uri != null) {
						last_uri.set(uri)
					}
				}
			}
		}
		
		// どうであれメイン画面に戻る
		intent = Intent(this, ActMain::class.java)
		intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY or Intent.FLAG_ACTIVITY_NEW_TASK)
		startActivity(intent)
		finish()
	}
	
	private fun copyExtraTexts(dst : Intent, src : Intent) {
		var sv : String?
		//
		sv = src.getStringExtra(Intent.EXTRA_TEXT)
		if(sv != null) dst.putExtra(Intent.EXTRA_TEXT, sv)
		//
		sv = src.getStringExtra(Intent.EXTRA_SUBJECT)
		if(sv != null) dst.putExtra(Intent.EXTRA_SUBJECT, sv)
	}
	
	private fun remake(src : Intent) : Intent? {
		
		sweepOldCache()
		
		try {
			val action = src.action
			val type = src.type
			
			if(type.isMediaMimeType()) {
				if(Intent.ACTION_VIEW == action) {
					src.data?.let { uriOriginal ->
						try {
							val uri = saveToCache(uriOriginal)
							val dst = Intent(action)
							dst.setDataAndType(uri, type)
							copyExtraTexts(dst, src)
							return dst
						} catch(ex : Throwable) {
							log.trace(ex)
						}
						
					}
					
				} else if(Intent.ACTION_SEND == action) {
					var uri : Uri? = src.getParcelableExtra(Intent.EXTRA_STREAM)
					if(uri == null) {
						// text/plain
						return src
					} else {
						try {
							uri = saveToCache(uri)
							
							val dst = Intent(action)
							dst.type = type
							dst.putExtra(Intent.EXTRA_STREAM, uri)
							copyExtraTexts(dst, src)
							return dst
						} catch(ex : Throwable) {
							log.trace(ex)
						}
						
					}
				} else if(Intent.ACTION_SEND_MULTIPLE == action) {
					val list_uri =
						src.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: return null
					val list_dst = ArrayList<Uri>()
					for(uriOriginal in list_uri) {
						if(uriOriginal != null) {
							try {
								val uri = saveToCache(uriOriginal)
								list_dst.add(uri)
							} catch(ex : Throwable) {
								log.trace(ex)
							}
							
						}
					}
					if(list_dst.isEmpty()) return null
					val dst = Intent(action)
					dst.type = type
					dst.putParcelableArrayListExtra(Intent.EXTRA_STREAM, list_dst)
					copyExtraTexts(dst, src)
					return dst
				}
			} else if(Intent.ACTION_SEND == action) {
				
				// Swarmアプリから送られたインテントは getType()==null だが EXTRA_TEXT は含まれている
				// EXTRA_TEXT の存在を確認してからtypeがnullもしくは text/plain なら受け取る
				
				val sv = src.getStringExtra(Intent.EXTRA_TEXT)
				if(sv?.isNotEmpty() == true && (type == null || type.startsWith("text/"))) {
					val dst = Intent(action)
					dst.type = "text/plain"
					copyExtraTexts(dst, src)
					
					return dst
				}
			}
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
		return null
	}
	
	@Throws(Throwable::class)
	private fun saveToCache(uri : Uri) : Uri {
		
		// prepare cache directory
		val cache_dir = cacheDir
		
		cache_dir.mkdirs()
		
		val name =
			"img." + System.currentTimeMillis().toString() + "." + uri.toString().digestSHA256Hex()
		
		val dst = File(cache_dir, name)
		
		FileOutputStream(dst).use { outStream ->
			val source = contentResolver.openInputStream(uri)
				?: throw RuntimeException("getContentResolver.openInputStream returns null.")
			source.use { inStream ->
				IOUtils.copy(inStream, outStream)
			}
		}
		return FileProvider.getUriForFile(this, App1.FILE_PROVIDER_AUTHORITY, dst)
	}
	
	private fun sweepOldCache() {
		// sweep old cache
		try {
			// prepare cache directory
			val cache_dir = cacheDir
			
			cache_dir.mkdirs()
			
			val now = System.currentTimeMillis()
			val files = cache_dir.listFiles()
			if(files != null) for(f in files) {
				try {
					if(f.isFile
						&& f.name.startsWith("img.")
						&& now - f.lastModified() >= 86400000L) {
						
						f.delete()
					}
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
			}
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
	}
	
}
