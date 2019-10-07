package jp.juggler.subwaytooter

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import jp.juggler.util.*

class ActOSSLicense : AppCompatActivity() {
	
	companion object {
		private val log = LogCategory("ActOSSLicense")
	}
	
	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		App1.setActivityTheme(this, noActionBar = true)
		setContentView(R.layout.act_oss_license)
		App1.initEdgeToEdge(this)
		
		try {
			val tv = findViewById<TextView>(R.id.tvText)
			tv.text = loadRawResource(R.raw.oss_license).decodeUTF8()
		} catch(ex : Throwable) {
			log.trace(ex)
		}
	}
	
}
