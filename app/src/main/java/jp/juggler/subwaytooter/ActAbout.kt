package jp.juggler.subwaytooter

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import jp.juggler.util.LogCategory

class ActAbout : AppCompatActivity() {
	
	companion object {
		val log = LogCategory("ActAbout")
		
		const val EXTRA_SEARCH = "search"
		
		const val url_store =
			"https://play.google.com/store/apps/details?id=jp.juggler.subwaytooter"
		
		const val developer_acct = "tateisu@mastodon.juggler.jp"
		const val official_acct = "SubwayTooter@mastodon.juggler.jp"
		
		const val url_release = "https://github.com/tateisu/SubwayTooter/releases"
		
		const val url_futaba = "https://www.instagram.com/hinomoto_hutaba/"
		
		const val url_weblate = "https://hosted.weblate.org/projects/subway-tooter/"
		
		val contributors = arrayOf(
			Pair("@ButterflyOfFire@mstdn.fr", "update arabic & french language")
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
			log.trace(ex, "getPackageInfo failed.")
		}
		
		fun setButton(btnId : Int, caption : String, onClick : () -> Unit) {
			val b : Button = findViewById(btnId)
			b.text = caption
			b.setOnClickListener { onClick() }
		}
		
		fun searchAcct(acct : String) {
			setResult(Activity.RESULT_OK, Intent().apply { putExtra(EXTRA_SEARCH, acct) })
			finish()
		}
		
		fun openUrl(url : String) {
			App1.openBrowser(this@ActAbout, url)
		}
		
		setButton(
			R.id.btnDeveloper,
			getString(R.string.search_for, developer_acct)
		) { searchAcct(developer_acct) }
		
		setButton(
			R.id.btnOfficialAccount,
			getString(R.string.search_for, official_acct)
		) { searchAcct(official_acct) }
		
		
		setButton(R.id.btnRate, url_store) { openUrl(url_store) }
		setButton(R.id.btnReleaseNote, url_release) { openUrl(url_release) }
		setButton(R.id.btnIconDesign, url_futaba) { openUrl(url_futaba) }
		setButton(R.id.btnWeblate, "Please help translation!") { openUrl(url_weblate) }
		
		val ll = findViewById<LinearLayout>(R.id.llContributors)
		val density = resources.displayMetrics.density
		val margin_top = (0.5f + density * 8).toInt()
		val padding = (0.5f + density * 8).toInt()
		
		for( pair in contributors){
			ll.addView(Button(this).apply{
				val acct = pair.first
				val works = pair.second

				//
				layoutParams = LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT
				).apply{
					if( ll.childCount != 0 ) topMargin = margin_top
				}
				//
				setBackgroundResource(R.drawable.btn_bg_transparent)
				setPadding(padding, padding, padding, padding)
				isAllCaps = false
				//
				text = getString(R.string.search_for, acct) + "\n" +
					getString(R.string.thanks_for, works)
				
				gravity = Gravity.START or Gravity.CENTER_VERTICAL
				
				setOnClickListener {
					val data = Intent()
					data.putExtra(EXTRA_SEARCH, acct)
					setResult(Activity.RESULT_OK, data)
					finish()
				}
			})
		}
	}
}
