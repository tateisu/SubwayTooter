package jp.juggler.subwaytooter

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.TextView

import org.apache.commons.io.IOUtils

import java.io.ByteArrayOutputStream

import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils

class ActOSSLicense : AppCompatActivity() {
	
	companion object {
		private val log = LogCategory("ActOSSLicense")
	}

	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		App1.setActivityTheme(this, true)
		setContentView(R.layout.act_oss_license)
		
		try {
			resources.openRawResource(R.raw.oss_license)?.use{ inData ->
				ByteArrayOutputStream().use { bao ->
					IOUtils.copy(inData, bao)
					val text = Utils.decodeUTF8(bao.toByteArray())
					val tv = findViewById<TextView>(R.id.tvText)
					tv.text = text
				}
			}
		} catch(ex : Throwable) {
			log.trace(ex)
		}
	}
	
}
