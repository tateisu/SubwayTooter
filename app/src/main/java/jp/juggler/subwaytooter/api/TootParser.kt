package jp.juggler.subwaytooter.api

import android.content.Context
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.table.UserRelation


import jp.juggler.util.WordTrieTree
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.util.JsonArray
import jp.juggler.util.JsonObject

class TootParser(
	val context : Context,
	val linkHelper : LinkHelper,
	var pinned : Boolean = false, // プロフィールカラムからpinned TL を読んだ時だけ真
	var highlightTrie : WordTrieTree? = null,
	var serviceType : ServiceType = ServiceType.MASTODON,
	var misskeyDecodeProfilePin : Boolean = false,
	var fromStream : Boolean = false,
	var decodeQuote : Boolean = true
) {
	
	val misskeyUserRelationMap = HashMap<EntityId, UserRelation>()
	val misskeyAccountDetailMap = HashMap<EntityId, TootAccount>()
	
	val accessHost : Host?
		get() = linkHelper.host
	
	init {
		if(linkHelper.isMisskey) serviceType = ServiceType.MISSKEY
	}
	
	fun getFullAcct(acct : Acct?) = linkHelper.getFullAcct(acct)
	
	fun account(src : JsonObject?) = parseItem(::TootAccount, this, src)
	fun accountList(array : JsonArray?) =
		TootAccountRef.wrapList(this, parseList(::TootAccount, this, array))
	
	fun status(src : JsonObject?) = parseItem(::TootStatus, this, src)
	fun statusList(array : JsonArray?) = parseList(::TootStatus, this, array)
	
	fun notification(src : JsonObject?) = parseItem(::TootNotification, this, src)
	fun notificationList(src : JsonArray?) = parseList(::TootNotification, this, src)
	
	fun tagList(array : JsonArray?) = parseList(::TootTag, array)
	fun results(src : JsonObject?) = parseItem(::TootResults, this, src)
	fun instance(src : JsonObject?) = parseItem(::TootInstance, this, src)
	
	fun getMisskeyUserRelation(whoId : EntityId) = misskeyUserRelationMap[whoId]
	
	fun parseMisskeyApShow(jsonObject : JsonObject?) : Any? {
		// ap/show の戻り値はActivityPubオブジェクトではなく、Misskeyのエンティティです。
		return when(jsonObject?.string("type")) {
			"Note" -> status(jsonObject.jsonObject("object"))
			"User" -> account(jsonObject.jsonObject("object"))
			else -> null
		}
	}
	
}
