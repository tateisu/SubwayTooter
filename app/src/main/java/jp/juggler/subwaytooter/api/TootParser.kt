package jp.juggler.subwaytooter.api

import android.content.Context
import jp.juggler.subwaytooter.api.entity.*

import org.json.JSONArray
import org.json.JSONObject

import jp.juggler.subwaytooter.util.WordTrieTree
import jp.juggler.subwaytooter.util.LinkHelper

class TootParser(
	val context : Context,
	val linkHelper : LinkHelper,
	var pinned : Boolean = false, // プロフィールカラムからpinned TL を読んだ時だけ真
	var highlightTrie : WordTrieTree? = null,
	var serviceType : ServiceType = ServiceType.MASTODON,
	var misskeyDecodeProfilePin :Boolean = false
) {
	
	fun account(src : JSONObject?) = parseItem(::TootAccount, this, src)
	fun accountList(array : JSONArray?) = TootAccountRef.wrapList(this,parseList(::TootAccount, this, array))
	
	fun status(src : JSONObject?) = parseItem(::TootStatus, this, src)
	fun statusList(array : JSONArray?) = parseList(::TootStatus, this, array)
	
	fun notification(src : JSONObject?) = parseItem(::TootNotification, this, src)
	fun notificationList(src : JSONArray?) = parseList(::TootNotification, this, src)
	
	fun results(src : JSONObject?) = parseItem(::TootResults, this, src)
	fun instance(src : JSONObject?) = parseItem(::TootInstance, this, src)
	fun trendTagList(array : JSONArray?)= parseList(::TootTrendTag, array)
	
	fun resultsV2(src : JSONObject) = parseItem(::TootResultsV2, this, src)

}
