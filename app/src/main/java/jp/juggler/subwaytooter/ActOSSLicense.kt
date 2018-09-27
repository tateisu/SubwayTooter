package jp.juggler.subwaytooter

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.TextView
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.decodeUTF8
import jp.juggler.subwaytooter.util.loadRawResource

class ActOSSLicense : AppCompatActivity() {
	
	companion object {
		private val log = LogCategory("ActOSSLicense")
	}
	
	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		App1.setActivityTheme(this, true)
		setContentView(R.layout.act_oss_license)
		
		try {
			val tv = findViewById<TextView>(R.id.tvText)
			tv.text = loadRawResource(R.raw.oss_license).decodeUTF8()
		} catch(ex : Throwable) {
			log.trace(ex)
		}
	}
	
}
