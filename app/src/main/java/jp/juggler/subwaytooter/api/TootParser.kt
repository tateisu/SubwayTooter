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
	
	////////////////////////////////////////////////////////
	// parser options
	
	fun setPinned(pinned : Boolean) : TootParser {
		this.pinned = pinned
		return this
	}
	
	fun setHighlightTrie(highlightTrie : WordTrieTree?) : TootParser {
		this.highlightTrie = highlightTrie
		return this
	}
	
	/////////////////////////////////////////////////////////
	// parser methods
	
	fun account(src : JSONObject?) : TootAccount? {
		return TootAccount.parse(context, accessInfo, src)
	}
	
	fun status(src : JSONObject?,serviceType :ServiceType = ServiceType.MASTODON ) : TootStatus? {
		return TootStatus.parse(this, src,serviceType)
	}
	
	fun statusList(array : JSONArray?,serviceType :ServiceType = ServiceType.MASTODON) : TootStatus.List {
		return TootStatus.parseList(this, array,serviceType)
	}
	
	fun notification(src : JSONObject?) : TootNotification? {
		return parseItem(::TootNotification,this, src)
	}
	
	fun notificationList(src : JSONArray?) : ArrayList<TootNotification> {
		return parseList(::TootNotification,this, src)
	}
	
	fun results(src : JSONObject?) : TootResults? {
		return parseItem(::TootResults,this, src)
	}
	
	fun context(src : JSONObject?) : TootContext? {
		return parseItem(::TootContext,this, src)
	}
	
}
