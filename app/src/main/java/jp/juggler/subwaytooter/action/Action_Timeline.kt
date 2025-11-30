package jp.juggler.subwaytooter.action

import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.actmain.addColumn
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.api.syncStatus
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.dialog.pickAccount
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.daoSavedAccount
import jp.juggler.subwaytooter.table.sortInplaceByNickname
import jp.juggler.subwaytooter.table.sortedByNickname
import jp.juggler.subwaytooter.util.matchHost
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.log.showToast

// アカウントを選んでタイムラインカラムを追加
fun ActMain.timeline(
    pos: Int,
    type: ColumnType,
    args: Array<out Any> = emptyArray(),
) {
    launchMain {
        pickAccount(
            bAllowPseudo = type.bAllowPseudo,
            bAllowMisskey = type.bAllowMisskey,
            bAllowMastodon = type.bAllowMastodon,
            bAuto = true,
            message = getString(
                R.string.account_picker_add_timeline_of,
                type.name1(applicationContext)
            )
        )?.let { account ->
            when (type) {
                ColumnType.PROFILE ->
                    account.loginAccount?.id?.let {
                        addColumn(pos, account, type, params = arrayOf(it))
                    }

                ColumnType.PROFILE_DIRECTORY ->
                    addColumn(pos, account, type, params = arrayOf(account.apiHost))

                else -> addColumn(pos, account, type, params = args)
            }
        }
    }
}

// アカウント選択付きでタイムラインを追加
// その際にアカウントをラムダ式でフィルタする
//fun ActMain.timelineWithFilter(
//    pos: Int,
//    type: ColumnType,
//    args: Array<out Any> = emptyArray(),
//    filter: suspend (SavedAccount) -> Boolean
//) {
//    launchMain {
//        val accountList = accountListWithFilter { _, a ->
//            when {
//                a.isPseudo && !type.bAllowPseudo -> false
//                a.isMisskey && !type.bAllowMisskey -> false
//                a.isMastodon && !type.bAllowMastodon -> false
//                else -> filter(a)
//            }
//        }?.toMutableList() ?: return@launchMain
//
//        pickAccount(
//            accountListArg = accountList,
//            bAuto = true,
//            message = getString(
//                R.string.account_picker_add_timeline_of,
//                type.name1(applicationContext)
//            )
//        )?.let { ai ->
//            when (type) {
//                ColumnType.PROFILE ->
//                    ai.loginAccount?.id?.let { addColumn(pos, ai, type, it) }
//
//                ColumnType.PROFILE_DIRECTORY ->
//                    addColumn(pos, ai, type, ai.apiHost)
//
//                else ->
//                    addColumn(pos, ai, type, *args)
//            }
//        }
//    }
//}

// 指定アカウントで指定タンスのドメインタイムラインを開く
// https://fedibird.com/@noellabo/103266814160117397
fun ActMain.timelineDomain(
    pos: Int,
    accessInfo: SavedAccount,
    host: Host,
) = addColumn(pos, accessInfo, ColumnType.DOMAIN_TIMELINE, params = arrayOf(host))

// 指定タンスのローカルタイムラインを開く
fun ActMain.timelineLocal(
    pos: Int,
    host: Host,
) {
    launchMain {
        // 指定タンスのアカウントを持ってるか？
        val accountList = ArrayList<SavedAccount>()
        for (a in daoSavedAccount.loadAccountList()) {
            if (a.matchHost(host)) accountList.add(a)
        }

        if (accountList.isEmpty()) {
            // 持ってないなら疑似アカウントを追加する
            addPseudoAccount(host)?.let { ai ->
                addColumn(pos, ai, ColumnType.LOCAL)
            }
        } else {
            // 持ってるならアカウントを選んで開く
            pickAccount(
                bAllowPseudo = true,
                bAuto = false,
                message = getString(R.string.account_picker_add_timeline_of, host),
                accountListArg = accountList.sortedByNickname()
            )?.let { addColumn(pos, it, ColumnType.LOCAL) }
        }
    }
}

private fun ActMain.timelineAround(
    accessInfo: SavedAccount,
    pos: Int,
    id: EntityId,
    type: ColumnType,
) = addColumn(pos, accessInfo, type, params = arrayOf(id))

// 投稿を同期してstatusIdを調べてから指定アカウントでタイムラインを開く
private fun ActMain.timelineAroundByStatus(
    accessInfo: SavedAccount,
    pos: Int,
    status: TootStatus,
    type: ColumnType,
) {
    launchMain {
        var resultStatus: TootStatus? = null
        runApiTask(accessInfo) { client ->
            val pair = client.syncStatus(accessInfo, status)
            resultStatus = pair.second
            pair.first
        }?.let { result ->
            when (val localStatus = resultStatus) {
                null -> showToast(true, result.error)
                else -> timelineAround(accessInfo, pos, localStatus.id, type)
            }
        }
    }
}

// 指定タンスの指定投稿付近のタイムラインを開く。アカウント選択あり。
fun ActMain.timelineAroundByStatusAnotherAccount(
    accessInfo: SavedAccount,
    pos: Int,
    host: Host?,
    status: TootStatus?,
    type: ColumnType,
    allowPseudo: Boolean = true,
) {
    host?.valid() ?: return
    status ?: return

    // 利用可能なアカウントを列挙する
    val accountList1 = ArrayList<SavedAccount>() // 閲覧アカウントとホストが同じ
    val accountList2 = ArrayList<SavedAccount>() // その他実アカウント
    label@ for (a in daoSavedAccount.loadAccountList()) {
        // Misskeyアカウントはステータスの同期が出来ないので選択させない
        if (a.isNA || a.isMisskey) continue
        when {
            // 閲覧アカウントとホスト名が同じならステータスIDの変換が必要ない
            a.matchHost(accessInfo) -> if (allowPseudo || !a.isPseudo) accountList1.add(a)

            // 実アカウントならステータスを同期して同時間帯のTLを見れる
            !a.isPseudo -> accountList2.add(a)
        }
    }
    accountList1.sortInplaceByNickname()
    accountList2.sortInplaceByNickname()
    accountList1.addAll(accountList2)

    if (accountList1.isEmpty()) {
        showToast(false, R.string.missing_available_account)
        return
    }

    launchMain {
        pickAccount(
            bAuto = true,
            message = "select account to read timeline",
            accountListArg = accountList1
        )?.let { ai ->
            if (!ai.isNA && ai.matchHost(accessInfo)) {
                timelineAround(ai, pos, status.id, type)
            } else {
                timelineAroundByStatus(ai, pos, status, type)
            }
        }
    }
}

fun ActMain.clickAroundAccountTL(
    accessInfo: SavedAccount,
    pos: Int,
    who: TootAccount,
    status: TootStatus?,
) =
    timelineAroundByStatusAnotherAccount(
        accessInfo,
        pos,
        who.apiHost,
        status,
        ColumnType.ACCOUNT_AROUND, allowPseudo = false
    )

fun ActMain.clickAroundLTL(
    accessInfo: SavedAccount,
    pos: Int,
    who: TootAccount,
    status: TootStatus?,
) =
    timelineAroundByStatusAnotherAccount(
        accessInfo,
        pos,
        who.apiHost,
        status,
        ColumnType.LOCAL_AROUND
    )

fun ActMain.clickAroundFTL(
    accessInfo: SavedAccount,
    pos: Int,
    who: TootAccount,
    status: TootStatus?,
) =
    timelineAroundByStatusAnotherAccount(
        accessInfo,
        pos,
        who.apiHost,
        status,
        ColumnType.FEDERATED_AROUND
    )
