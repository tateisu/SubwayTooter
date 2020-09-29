package jp.juggler.subwaytooter.util

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.app.Activity
import androidx.browser.customtabs.CustomTabsIntent
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootAttachment
import jp.juggler.subwaytooter.dialog.DlgAppPicker
import jp.juggler.subwaytooter.pref
import jp.juggler.util.*

private val log = LogCategory("AppOpener")

// returns true if activity is opened.
// returns false if fallback required
private fun Activity.startActivityExcludeMyApp(
	intent : Intent,
	startAnimationBundle : Bundle? = null
) : Boolean {
	try {
		val pm = packageManager !!
		val myName = packageName
		
		val filter : (ResolveInfo) -> Boolean = {
			it.activityInfo.packageName != myName &&
				it.activityInfo.exported &&
				- 1 == it.activityInfo.packageName.indexOf("com.huawei.android.internal")
		}
		
		// resolveActivity がこのアプリ以外のActivityを返すなら、それがベストなんだろう
		// ただしAndroid M以降はMATCH_DEFAULT_ONLYだと「常時」が設定されてないとnullを返す
		val ri = pm.resolveActivity(
			intent,
			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				PackageManager.MATCH_ALL
			} else {
				PackageManager.MATCH_DEFAULT_ONLY
			}
		)?.takeIf(filter)
		
		if(ri != null) {
			intent.setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
			startActivity(intent, startAnimationBundle)
			return true
		}
		
		return DlgAppPicker(
			this,
			intent,
			autoSelect = true,
			filter = filter
		) {
			try {
				intent.component = it.cn()
				startActivity(intent, startAnimationBundle)
			} catch(ex : Throwable) {
				log.trace(ex)
				showToast(ex, "can't open. ${intent.data}")
			}
		}.show()
		
	} catch(ex : Throwable) {
		log.trace(ex)
		showToast(ex, "can't open. ${intent.data}")
		return true // fallback not required in this case
	}
}

fun Activity.openBrowser(uri : Uri?) {
	if(uri != null) {
		val rv = startActivityExcludeMyApp(Intent(Intent.ACTION_VIEW, uri))
		if(! rv) showToast(true, "there is no app that can open $uri")
	}
}

fun Activity.openBrowser(url : String?) = openBrowser(url.mayUri())

// ubway Tooterの「アプリ設定/挙動/リンクを開く際にCustom Tabsを使わない」をONにして
// 投稿のコンテキストメニューの「トゥートへのアクション/Webページを開く」「ユーザへのアクション/Webページを開く」を使うと
// 投げたインテントをST自身が受け取って「次のアカウントから開く」ダイアログが出て
// 「Webページを開く」をまた押すと無限ループしてダイアログの影が徐々に濃くなりそのうち壊れる
// これを避けるには、投稿やトゥートを開く際に bpDontUseCustomTabs がオンならST以外のアプリを列挙したアプリ選択ダイアログを出すしかない
fun Activity.openCustomTabOrBrowser(url : String?) {
	url ?: return
	if(! Pref.bpDontUseCustomTabs(pref())) {
		openCustomTab(url)
	} else {
		openBrowser(url)
	}
}

// Chrome Custom Tab を開く
fun Activity.openCustomTab(url : String?) {
	url ?: return
	
	if(url.isEmpty()) {
		showToast(false, "URL is empty string.")
		return
	}
	
	val pref = pref()
	if(Pref.bpDontUseCustomTabs(pref)) {
		openCustomTabOrBrowser(url)
		return
	}
	
	try {
		if(url.startsWith("http") && Pref.bpPriorChrome(pref)) {
			try {
				// 初回はChrome指定で試す
				val customTabsIntent = CustomTabsIntent.Builder()
					.setToolbarColor(getAttributeColor(R.attr.colorPrimary))
					.setShowTitle(true)
					.build()
				
				val rv = startActivityExcludeMyApp(
					customTabsIntent.intent.also {
						it.component = ComponentName(
							"com.android.chrome",
							"com.google.android.apps.chrome.Main"
						)
						it.data = url.toUri()
					},
					customTabsIntent.startAnimationBundle
				)
				if(rv) return
			} catch(ex2 : Throwable) {
				log.e(ex2, "openChromeTab: missing chrome. retry to other application.")
			}
		}
		
		// Chromeがないようなのでcomponent指定なしでリトライ
		val customTabsIntent = CustomTabsIntent.Builder()
			.setToolbarColor(getAttributeColor(R.attr.colorPrimary))
			.setShowTitle(true)
			.build()
		
		val rv = startActivityExcludeMyApp(
			customTabsIntent.intent.also {
				it.data = url.toUri()
			},
			customTabsIntent.startAnimationBundle
		)
		if(! rv) {
			showToast(true, "the browser app is not installed.")
		}
		
	} catch(ex : Throwable) {
		log.trace(ex)
		val scheme = url.mayUri()?.scheme ?: url
		showToast(true, "can't open browser app for %s", scheme)
	}
}

fun Activity.openCustomTab(ta : TootAttachment) =
	openCustomTab(ta.getLargeUrl(pref()))
