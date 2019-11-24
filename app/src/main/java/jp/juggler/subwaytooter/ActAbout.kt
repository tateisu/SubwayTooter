package jp.juggler.subwaytooter

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.util.LogCategory

class ActAbout : AppCompatActivity() {
	
	class Translators(
		val name:String,
		val acct:String?,
		val lang:String
	)
	
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
		
		// git log --pretty=format:"%an %s" |grep "Translated using Weblate"|sort|uniq
		val translators = arrayOf(
			Translators("Allan Nordhøy",null,"English & Norwegian Bokmål"),
			Translators("ButterflyOfFire","@ButterflyOfFire@mstdn.fr", "Arabic & French"),
			Translators("Ch",null,"Korean"),
			Translators("Elizabeth Sherrock",null,"Chinese (Simplified)"),
			Translators("Gennady Archangorodsky",null,"Hebrew"),
			Translators("inqbs Siina",null,"Korean"),
			Translators("Jeong Arm","@jarm@qdon.space","Korean"),
			Translators("Joan Pujolar","@jpujolar@mastodont.cat","Catalan"),
			Translators("Kai Zhang","@bearzk@mastodon.social","Chinese (Simplified)"),
			Translators("lptprjh",null,"Korean"),
			Translators("mynameismonkey",null,"Welsh"),
			Translators("Nathan",null,"French"),
			Translators("Owain Rhys Lewis",null,"Welsh"),
			Translators("Swann Martinet",null,"French"),
			Translators("takubunn",null,"Chinese (Simplified)"),
			Translators("배태길",null,"Korea")
		)
	}
	
	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		App1.setActivityTheme(this)
		setContentView(R.layout.act_about)
		App1.initEdgeToEdge(this)
		
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
		setButton(R.id.btnWeblate, getString(R.string.please_help_translation) ) { openUrl(url_weblate) }
		
		val ll = findViewById<LinearLayout>(R.id.llContributors)
		val density = resources.displayMetrics.density
		val margin_top = (0.5f + density * 8).toInt()
		val padding = (0.5f + density * 8).toInt()
		
		for(who in translators) {
			ll.addView(Button(this).apply {
				//
				layoutParams = LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT
				).apply {
					if(ll.childCount != 0) topMargin = margin_top
				}
				//
				setBackgroundResource(R.drawable.btn_bg_transparent)
				setPadding(padding, padding, padding, padding)
				isAllCaps = false

				//
				val acct = who.acct ?: "@?@?"
				text = "${who.name}\n$acct\n${getString(R.string.thanks_for, who.lang)}"
				gravity = Gravity.START or Gravity.CENTER_VERTICAL
				
				setOnClickListener {
					val data = Intent()
					data.putExtra(EXTRA_SEARCH, who.acct ?: who.name)
					setResult(Activity.RESULT_OK, data)
					finish()
				}
			})
		}
	}
}
