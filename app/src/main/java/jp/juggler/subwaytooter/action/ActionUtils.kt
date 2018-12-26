package jp.juggler.subwaytooter.action

import android.content.Context
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootRelationShip
import jp.juggler.subwaytooter.api.entity.parseList
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.table.UserRelationMisskey
import jp.juggler.util.LogCategory
import jp.juggler.util.showToast
import org.json.JSONObject
import java.util.*

// 疑似アカウントを作成する
// 既に存在する場合は再利用する
// 実アカウントを返すことはない
internal fun addPseudoAccount(
	context : Context,
	host : String,
	isMisskey : Boolean? = null,
	callback : (SavedAccount) -> Unit
) {
	try {
		val username = "?"
		val full_acct = "$username@$host"
		
		var account = SavedAccount.loadAccountByAcct(context, full_acct)
		if(account != null) {
			callback(account)
			return
		}
		
		if(isMisskey == null) {
			TootTaskRunner(context).run(object : TootTask {
				
				var isMisskey2 : Boolean = false
				
				override fun background(client : TootApiClient) : TootApiResult? {
					client.instance = host
					val r = client.getInstanceInformation()
					isMisskey2 = r?.jsonObject?.optBoolean(TootApiClient.KEY_IS_MISSKEY) ?: false
					return r
				}
				
				override fun handleResult(result : TootApiResult?) {
					if(result != null) addPseudoAccount(context, host, isMisskey2, callback)
				}
			})
			return
		}
		
		val account_info = JSONObject()
		account_info.put("username", username)
		account_info.put("acct", username)
		
		val row_id =
			SavedAccount.insert(host, full_acct, account_info, JSONObject(), isMisskey = isMisskey)
		account = SavedAccount.loadAccount(context, row_id)
		if(account == null) {
			throw RuntimeException("loadAccount returns null.")
		}
		account.notification_follow = false
		account.notification_favourite = false
		account.notification_boost = false
		account.notification_mention = false
		account.notification_reaction = false
		account.notification_vote = false
		account.saveSetting()
		callback(account)
		return
	} catch(ex : Throwable) {
		val log = LogCategory("addPseudoAccount")
		log.trace(ex)
		log.e(ex, "failed.")
		showToast(context, ex, "addPseudoAccount failed.")
	}
	return
}

// 疑似アカ以外のアカウントのリスト
fun makeAccountListNonPseudo(
	context : Context, pickup_host : String?
) : ArrayList<SavedAccount> {
	
	val list_same_host = ArrayList<SavedAccount>()
	val list_other_host = ArrayList<SavedAccount>()
	for(a in SavedAccount.loadAccountList(context)) {
		if(a.isPseudo) continue
		(if(pickup_host == null || pickup_host.equals(
				a.host,
				ignoreCase = true
			)) list_same_host else list_other_host).add(a)
	}
	SavedAccount.sort(list_same_host)
	SavedAccount.sort(list_other_host)
	list_same_host.addAll(list_other_host)
	return list_same_host
}

internal fun saveUserRelation(access_info : SavedAccount, src : TootRelationShip?) : UserRelation? {
	src ?: return null
	val now = System.currentTimeMillis()
	return UserRelation.save1(now, access_info.db_id, src)
}

internal fun saveUserRelationMisskey(
	access_info : SavedAccount,
	whoId : EntityId,
	parser : TootParser
) : UserRelation? {
	val now = System.currentTimeMillis()
	val relation = parser.getMisskeyUserRelation(whoId)
	UserRelationMisskey.save1(now, access_info.db_id, whoId.toString(), relation)
	return relation
}

// relationshipを取得
internal fun loadRelation1Mastodon(
	client : TootApiClient,
	access_info : SavedAccount,
	who : TootAccount
) : RelationResult {
	val rr = RelationResult()
	rr.result = client.request("/api/v1/accounts/relationships?id=${who.id}")
	val r2 = rr.result
	val jsonArray = r2?.jsonArray
	if(jsonArray != null) {
		val list = parseList(::TootRelationShip, TootParser(client.context, access_info), jsonArray)
		if(list.isNotEmpty()) {
			rr.relation = saveUserRelation(access_info, list[0])
		}
	}
	return rr
}

// 別アカ操作と別タンスの関係
const val NOT_CROSS_ACCOUNT = 1
const val CROSS_ACCOUNT_SAME_INSTANCE = 2
const val CROSS_ACCOUNT_REMOTE_INSTANCE = 3

internal fun calcCrossAccountMode(
	timeline_account : SavedAccount,
	action_account : SavedAccount
) : Int {
	return if(! timeline_account.host.equals(action_account.host, ignoreCase = true)) {
		CROSS_ACCOUNT_REMOTE_INSTANCE
	} else if(! timeline_account.acct.equals(action_account.acct, ignoreCase = true)) {
		CROSS_ACCOUNT_SAME_INSTANCE
	} else {
		NOT_CROSS_ACCOUNT
	}
}
