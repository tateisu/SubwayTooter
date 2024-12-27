package jp.juggler.subwaytooter.action

import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.api.runApiTask2
import jp.juggler.subwaytooter.api.showApiError
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.daoSavedAccount
import jp.juggler.subwaytooter.util.matchHost
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.buildJsonObject
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast

private val log = LogCategory("ActionUtils")

// 疑似アカウントを作成する
// 既に存在する場合は再利用する
// 実アカウントを返すことはない
internal suspend fun AppCompatActivity.addPseudoAccount(
    host: Host,
    instanceInfoArg: TootInstance? = null,
): SavedAccount? {
    try {
        suspend fun AppCompatActivity.getInstanceInfo(): TootInstance? {
            return try {
                runApiTask2(host) { TootInstance.getOrThrow(it) }
            } catch (ex: Throwable) {
                showApiError(ex)
                null
            }
        }

        val acct = Acct.parse("?", host)

        var account = daoSavedAccount.loadAccountByAcct(acct)
        if (account != null) return account

        val instanceInfo = instanceInfoArg
            ?: getInstanceInfo()
            ?: return null

        val accountInfo = buildJsonObject {
            put("username", acct.username)
            put("acct", acct.username) // ローカルから参照した場合なのでshort acct
        }

        val rowId = daoSavedAccount.saveNew(
            acct = acct.ascii,
            host = host.ascii,
            domain = instanceInfo.apDomain.ascii,
            account = accountInfo,
            token = JsonObject(),
            misskeyVersion = instanceInfo.misskeyVersionMajor
        )

        account = daoSavedAccount.loadAccount(rowId)
            ?: error("loadAccount returns null.")

        account.notificationFollow = false
        account.notificationFollowRequest = false
        account.notificationFavourite = false
        account.notificationBoost = false
        account.notificationMention = false
        account.notificationReaction = false
        account.notificationVote = false
        account.notificationPost = false
        account.notificationUpdate = false
        account.notificationSeveredRelationships = false
        account.notificationStatusReference = false
        account.notificationPushEnable = false
        account.notificationPullEnable = false
        daoSavedAccount.save(account)
        return account
    } catch (ex: Throwable) {
        log.e(ex, "addPseudoAccount failed.")
        showToast(ex, "addPseudoAccount failed.")
        return null
    }
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
enum class CrossAccountMode {
    SameAccount,    // same account, id and relation can be reused. NOT_CROSS_ACCOUNT
    SameInstance,   // same instance. id can be reused, but relation is not. CROSS_ACCOUNT_SAME_INSTANCE = 2
    RemoteInstance, // remote instance. it and relation can't be reused. CROSS_ACCOUNT_REMOTE_INSTANCE = 3
    ;

    val isRemote: Boolean
        get() = this == RemoteInstance

    val isNotRemote: Boolean
        get() = this != RemoteInstance
}

internal fun calcCrossAccountMode(
    timelineAccount: SavedAccount,
    actionAccount: SavedAccount,
): CrossAccountMode = when {
    timelineAccount == actionAccount -> CrossAccountMode.SameAccount
    timelineAccount.matchHost(actionAccount) -> CrossAccountMode.SameInstance
    else -> CrossAccountMode.RemoteInstance
}
