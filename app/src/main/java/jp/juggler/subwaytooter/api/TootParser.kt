package jp.juggler.subwaytooter.api

import android.content.Context
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.table.UserRelation

import org.json.JSONArray
import org.json.JSONObject

import jp.juggler.util.WordTrieTree
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.util.parseString

class TootParser(
	val context : Context,
	val linkHelper : LinkHelper,
	var pinned : Boolean = false, // プロフィールカラムからpinned TL を読んだ時だけ真
	var highlightTrie : WordTrieTree? = null,
	var serviceType : ServiceType = ServiceType.MASTODON,
	var misskeyDecodeProfilePin : Boolean = false
) {
	
	val misskeyUserRelationMap = HashMap<EntityId, UserRelation>()
	val misskeyAccountDetailMap = HashMap<EntityId, TootAccount>()
	
	val accessHost : String?
		get() = linkHelper.host
	
	init {
		if(linkHelper.isMisskey) serviceType = ServiceType.MISSKEY
	}
	
	fun getFullAcct(acct : String?) = linkHelper.getFullAcct(acct)
	
	fun account(src : JSONObject?) = parseItem(::TootAccount, this, src)
	fun accountList(array : JSONArray?) =
		TootAccountRef.wrapList(this, parseList(::TootAccount, this, array))
	
	fun status(src : JSONObject?) = parseItem(::TootStatus, this, src)
	fun statusList(array : JSONArray?) = parseList(::TootStatus, this, array)
	
	fun notification(src : JSONObject?) = parseItem(::TootNotification, this, src)
	fun notificationList(src : JSONArray?) = parseList(::TootNotification, this, src)
	
	fun results(src : JSONObject?) = parseItem(::TootResults, this, src)
	fun instance(src : JSONObject?) = parseItem(::TootInstance, this, src)
	fun trendTagList(array : JSONArray?) = parseList(::TootTrendTag, array)
	
	fun resultsV2(src : JSONObject) = parseItem(::TootResultsV2, this, src)
	
	fun getMisskeyUserRelation(whoId : EntityId) = misskeyUserRelationMap[whoId]
	
	fun parseMisskeyApShow(jsonObject : JSONObject?) : Any? {
		// ap/show の戻り値はActivityPubオブジェクトではなく、Misskeyのエンティティです。
		return when(jsonObject?.parseString("type")) {
			"Note" -> status(jsonObject.optJSONObject("object"))
			"User" -> account(jsonObject.optJSONObject("object"))
			else -> null
		}
	}
	
}
