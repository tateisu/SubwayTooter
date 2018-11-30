package jp.juggler.subwaytooter

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.toUri

class ActAbout : AppCompatActivity() {
	
	companion object {
		val log = LogCategory("ActAbout")
		
		const val EXTRA_SEARCH = "search"
		
		const val url_store = "https://play.google.com/store/apps/details?id=jp.juggler.subwaytooter"

		const val developer_acct = "tateisu@mastodon.juggler.jp"
		
		const val url_futaba = "https://www.instagram.com/hinomoto_hutaba/"

		const val url_weblate = "https://hosted.weblate.org/projects/subway-tooter/"
		
		val contributors = arrayOf(
			"@Balor@freeradical.zone", "update english language",
			"@Luattic@oc.todon.fr", "update french language",
			"@BoF@mstdn.fr", "update arabic language"
		)
	}
	
	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		App1.setActivityTheme(this, false)
		setContentView(R.layout.act_about)
		
		Styler.fixHorizontalPadding(findViewById(R.id.svContent))
		
		
		try {
			val pInfo = packageManager.getPackageInfo(packageName, 0)
			val tv = findViewById<TextView>(R.id.tvVersion)
				tv.text = getString(R.string.version_is, pInfo.versionName)
		} catch(ex : PackageManager.NameNotFoundException) {
			log.trace(ex,"getPackageInfo failed.")
		}
		
		var b : Button
		
		b = findViewById(R.id.btnDeveloper)
		b.text = getString(R.string.search_for, developer_acct)
		b.setOnClickListener {
			val data = Intent()
			data.putExtra(EXTRA_SEARCH, developer_acct)
			setResult(Activity.RESULT_OK, data)
			finish()
		}
		
		b = findViewById(R.id.btnRate)
		b.text = url_store
		b.setOnClickListener { open_browser(url_store) }
		
		b = findViewById(R.id.btnIconDesign)
		b.text = url_futaba
		b.setOnClickListener { open_browser(url_futaba) }
		
		b = findViewById(R.id.btnWeblate)
		b.text = "Please help translation!"
		b.setOnClickListener { open_browser(url_weblate) }
		
		val ll = findViewById<LinearLayout>(R.id.llContributors)
		val density = resources.displayMetrics.density
		val margin_top = (0.5f + density * 8).toInt()
		val padding = (0.5f + density * 8).toInt()
		
		var i = 0
		val ie = contributors.size
		while(i < ie) {
			val acct = contributors[i]
			val works = contributors[i + 1]
			
			b = Button(this)
			//
			val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
			if(i < 0) lp.topMargin = margin_top
			b.layoutParams = lp
			//
			b.setBackgroundResource(R.drawable.btn_bg_transparent)
			b.setPadding(padding, padding, padding, padding)
			b.setAllCaps(false)
			//
			b.text = getString(R.string.search_for, acct) + "\n" + getString(R.string.thanks_for, works)
			b.setOnClickListener {
				val data = Intent()
				data.putExtra(EXTRA_SEARCH, acct)
				setResult(Activity.RESULT_OK, data)
				finish()
			}
			//
			ll.addView(b)
			i += 2
		}
	}
	
	private fun open_browser(url : String) {
		try {
			val intent = Intent(Intent.ACTION_VIEW, url.toUri())
			startActivity(intent)
		} catch(ex : Throwable) {
			log.trace(ex,"open_browser failed.")
		}
		
	}
	
	
}
