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
	
	fun setAccessInfo(access_info : SavedAccount?) : ChromeTabOpener {
		this.accessInfo = access_info
		return this
	}
	
	fun allowIntercept( v:Boolean ): ChromeTabOpener{
		this.allowIntercept = v;
		return this
	}
	
	fun setTagList(tag_list : ArrayList<String>) : ChromeTabOpener {
		this.tagList = tag_list
		return this
	}
	
	fun open() {
		activity.openChromeTab(this)
	}
}
