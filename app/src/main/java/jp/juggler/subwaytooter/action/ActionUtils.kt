package jp.juggler.subwaytooter.action

import android.content.Context

import org.json.JSONObject

import java.util.ArrayList

import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootRelationShip
import jp.juggler.subwaytooter.api.entity.parseList
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.util.*

// ユーザ名からアカウントIDを取得する
internal fun findAccountByName(
	activity : ActMain,
	access_info : SavedAccount,
	host : String,
	user : String,
	callback : TootAccountOrNullCallback // 失敗するとnullがコールバックされる
) {
	TootTaskRunner(activity).run(access_info, object : TootTask {
		
		var who : TootAccount? = null
		
		override fun background(client : TootApiClient) : TootApiResult? {
			
			val path = "/api/v1/accounts/search" + "?q=" + user.encodePercent()
			
			val result = client.request(path)
			val array = result?.jsonArray
			if(array != null) {
				val parser = TootParser(activity, access_info)
				for(i in 0 until array.length()) {
					val a = parser.account(array.optJSONObject(i))
					if(a != null) {
						if(a.username == user
							&& access_info.getFullAcct(a).equals("$user@$host", ignoreCase = true)
						) {
							who = a
							break
						}
					}
				}
			}
			return result
		}
		
		override fun handleResult(result : TootApiResult?) {
			callback(who)
		}
	})
	
}

// 疑似アカウントを作成する
// 既に存在する場合は再利用する
// 実アカウントを返すことはない
internal fun addPseudoAccount(
	context : Context,
	host : String,
	isMisskey : Boolean = false
) : SavedAccount? {
	
	try {
		val username = "?"
		val full_acct = "$username@$host"
		
		var account = SavedAccount.loadAccountByAcct(context, full_acct)
		if(account != null) {
			return account
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
		account.saveSetting()
		return account
	} catch(ex : Throwable) {
		val log = LogCategory("addPseudoAccount")
		log.trace(ex)
		log.e(ex, "failed.")
		showToast(context, ex, "addPseudoAccount failed.")
	}
	
	return null
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

internal fun saveUserRelation(
	access_info : SavedAccount,
	src : TootRelationShip?
) : UserRelation? {
	if(src == null) return null
	val now = System.currentTimeMillis()
	return UserRelation.save1(now, access_info.db_id, src)
}

// relationshipを取得
internal fun loadRelation1(
	client : TootApiClient, access_info : SavedAccount, who_id : Long
) : RelationResult {
	val rr = RelationResult()
	rr.result = client.request("/api/v1/accounts/relationships?id=$who_id")
	val r2 = rr.result
	val jsonArray = r2?.jsonArray
	if(jsonArray != null) {
		val list = parseList(::TootRelationShip, jsonArray)
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
