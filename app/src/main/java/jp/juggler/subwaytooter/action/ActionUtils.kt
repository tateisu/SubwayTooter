package jp.juggler.subwaytooter.action

import android.content.Context
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.util.matchHost
import jp.juggler.util.JsonObject
import jp.juggler.util.LogCategory
import jp.juggler.util.jsonObject
import jp.juggler.util.showToast
import java.util.*

// 疑似アカウントを作成する
// 既に存在する場合は再利用する
// 実アカウントを返すことはない
internal fun addPseudoAccount(
	context : Context,
	host : Host,
	instanceInfo : TootInstance? = null,
	callback : (SavedAccount) -> Unit
) {
	try {
		val acct = Acct.parse("?", host)
		
		var account = SavedAccount.loadAccountByAcct(context, acct.ascii)
		if(account != null) {
			callback(account)
			return
		}
		
		if(instanceInfo == null) {
			TootTaskRunner(context).run(host, object : TootTask {
				
				var targetInstance : TootInstance? = null
				
				override suspend fun background(client : TootApiClient) : TootApiResult? {
					val (instance, instanceResult) = TootInstance.get(client)
					targetInstance = instance
					return instanceResult
				}
				
				override suspend fun handleResult(result : TootApiResult?) = when {
					result == null -> {
					}
					
					targetInstance == null -> context.showToast(false, result.error)
					else -> addPseudoAccount(context, host, targetInstance, callback)
				}
			})
			return
		}
		
		val account_info = jsonObject {
			put("username", acct.username)
			put("acct", acct.username) // ローカルから参照した場合なのでshort acct
		}
		
		val row_id = SavedAccount.insert(
			acct = acct.ascii,
			host = host.ascii,
			domain = instanceInfo.uri,
			account = account_info,
			token = JsonObject(),
			misskeyVersion = instanceInfo.misskeyVersion
		)
		
		account = SavedAccount.loadAccount(context, row_id)
		if(account == null) {
			throw RuntimeException("loadAccount returns null.")
		}
		account.notification_follow = false
		account.notification_follow_request = false
		account.notification_favourite = false
		account.notification_boost = false
		account.notification_mention = false
		account.notification_reaction = false
		account.notification_vote = false
		account.notification_post = false
		account.saveSetting()
		callback(account)
		return
	} catch(ex : Throwable) {
		val log = LogCategory("addPseudoAccount")
		log.trace(ex)
		log.e(ex, "failed.")
		context.showToast(ex, "addPseudoAccount failed.")
	}
	return
}

// 疑似アカ以外のアカウントのリスト
fun makeAccountListNonPseudo(
	context : Context, pickup_host : Host?
) : ArrayList<SavedAccount> {
	
	val list_same_host = ArrayList<SavedAccount>()
	val list_other_host = ArrayList<SavedAccount>()
	for(a in SavedAccount.loadAccountList(context)) {
		if(a.isPseudo) continue
		when(pickup_host) {
			null, a.apDomain, a.apiHost -> list_same_host
			else -> list_other_host
		}.add(a)
	}
	SavedAccount.sort(list_same_host)
	SavedAccount.sort(list_other_host)
	list_same_host.addAll(list_other_host)
	return list_same_host
}

internal fun saveUserRelation(access_info : SavedAccount, src : TootRelationShip?) : UserRelation? {
	src ?: return null
	val now = System.currentTimeMillis()
	return UserRelation.save1Mastodon(now, access_info.db_id, src)
}

internal fun saveUserRelationMisskey(
	access_info : SavedAccount,
	whoId : EntityId,
	parser : TootParser
) : UserRelation? {
	val now = System.currentTimeMillis()
	val relation = parser.getMisskeyUserRelation(whoId)
	UserRelation.save1Misskey(now, access_info.db_id, whoId.toString(), relation)
	return relation
}

//// relationshipを取得
//internal fun loadRelation1Mastodon(
//	client : TootApiClient,
//	access_info : SavedAccount,
//	who : TootAccount
//) : RelationResult {
//	val rr = RelationResult()
//	rr.result = client.request("/api/v1/accounts/relationships?id=${who.id}")
//	val r2 = rr.result
//	val jsonArray = r2?.jsonArray
//	if(jsonArray != null) {
//		val list = parseList(::TootRelationShip, TootParser(client.context, access_info), jsonArray)
//		if(list.isNotEmpty()) {
//			rr.relation = saveUserRelation(access_info, list[0])
//		}
//	}
//	return rr
//}

// 別アカ操作と別タンスの関係
const val NOT_CROSS_ACCOUNT = 1
const val CROSS_ACCOUNT_SAME_INSTANCE = 2
const val CROSS_ACCOUNT_REMOTE_INSTANCE = 3

internal fun calcCrossAccountMode(
	timeline_account : SavedAccount,
	action_account : SavedAccount
) : Int = when {
	timeline_account == action_account -> NOT_CROSS_ACCOUNT
	timeline_account.matchHost(action_account) -> CROSS_ACCOUNT_SAME_INSTANCE
	else -> CROSS_ACCOUNT_REMOTE_INSTANCE
}
