package jp.juggler.subwaytooter.action

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.api.runApiTask
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
internal suspend fun AppCompatActivity.addPseudoAccount(
	host : Host,
	instanceInfoArg : TootInstance? = null
):SavedAccount? {

	suspend fun AppCompatActivity.getInstanceInfo():TootInstance? {
		var resultTi : TootInstance? = null
		val result = runApiTask(host) { client->
			val (instance, instanceResult) = TootInstance.get(client)
			resultTi = instance
			instanceResult
		}
		result?.error?.let{ showToast(true, it )}
		return resultTi
	}

	try {
		val acct = Acct.parse("?", host)
		
		var account = SavedAccount.loadAccountByAcct(this, acct.ascii)
		if(account != null) return account

		val instanceInfo = instanceInfoArg
			?: getInstanceInfo()
			?: return null

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
		
		account = SavedAccount.loadAccount(applicationContext, row_id)
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
		return account
	} catch(ex : Throwable) {
		val log = LogCategory("addPseudoAccount")
		log.trace(ex)
		log.e(ex, "failed.")
		showToast(ex, "addPseudoAccount failed.")
	}
	return null
}



internal fun SavedAccount.saveUserRelation( src : TootRelationShip?) : UserRelation? {
	src ?: return null
	val now = System.currentTimeMillis()
	return UserRelation.save1Mastodon(now, db_id, src)
}

internal fun SavedAccount.saveUserRelationMisskey(
	whoId : EntityId,
	parser : TootParser
) : UserRelation? {
	val now = System.currentTimeMillis()
	val relation = parser.getMisskeyUserRelation(whoId)
	UserRelation.save1Misskey(now, db_id, whoId.toString(), relation)
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
enum class CrossAccountMode{
	SameAccount,    // same account, id and relation can be reused. NOT_CROSS_ACCOUNT
	SameInstance,   // same instance. id can be reused, but relation is not. CROSS_ACCOUNT_SAME_INSTANCE = 2
	RemoteInstance, // remote instance. it and relation can't be reused. CROSS_ACCOUNT_REMOTE_INSTANCE = 3
	;

	val isRemote :Boolean
		get()= this == RemoteInstance

	val isNotRemote: Boolean
		get() = this != RemoteInstance
}



internal fun calcCrossAccountMode(
	timeline_account : SavedAccount,
	action_account : SavedAccount
) : CrossAccountMode = when {
	timeline_account == action_account -> CrossAccountMode.SameAccount
	timeline_account.matchHost(action_account) -> CrossAccountMode.SameInstance
	else -> CrossAccountMode.RemoteInstance
}
