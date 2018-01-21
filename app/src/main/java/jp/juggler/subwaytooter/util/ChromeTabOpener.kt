package jp.juggler.subwaytooter.util

import java.util.ArrayList

import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.table.SavedAccount

@Suppress("MemberVisibilityCanPrivate")
class ChromeTabOpener(
	val activity : ActMain,
	val pos : Int,
	val url : String,
	var accessInfo : SavedAccount? = null,
	var tagList : ArrayList<String>? = null,
	var allowIntercept :Boolean = true
) {
	
	fun open() {
		activity.openChromeTab(this)
	}
}
