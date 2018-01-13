package jp.juggler.subwaytooter.api

import android.content.Context
import jp.juggler.subwaytooter.api.entity.*

import org.json.JSONArray
import org.json.JSONObject

import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.WordTrieTree

class TootParser(
	val context : Context,
	val accessInfo : SavedAccount,
	var pinned : Boolean = false, // プロフィールカラムからpinned TL を読んだ時だけ真
	var highlightTrie : WordTrieTree? = null
) {
	
	
	fun account(src : JSONObject?)
		=TootAccount.parse(context, accessInfo, src)
	
	fun status(src : JSONObject?,serviceType :ServiceType = ServiceType.MASTODON )
		=TootStatus.parse(this, src,serviceType)
	
	fun statusList(array : JSONArray?,serviceType :ServiceType = ServiceType.MASTODON)
		=TootStatus.parseList(this, array,serviceType)
	
	fun notification(src : JSONObject?)
		=parseItem(::TootNotification,this, src)
	
	fun notificationList(src : JSONArray?)
		=parseList(::TootNotification,this, src)
	
	fun results(src : JSONObject?)
		=parseItem(::TootResults,this, src)
	
}
