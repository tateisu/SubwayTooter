package jp.juggler.subwaytooter.action

import android.content.Context
import androidx.core.net.toUri
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.actmain.addColumn
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.column.findStatus
import jp.juggler.subwaytooter.columnviewholder.ItemListAdapter
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.matchHost
import jp.juggler.subwaytooter.util.openCustomTab
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.network.toFormRequestBody
import jp.juggler.util.network.toPost

private val log = LogCategory("Action_Conversation")

fun ActMain.clickConversation(
    pos: Int,
    accessInfo: SavedAccount,

    // optional. 未読表示のクリアに使う
    listAdapter: ItemListAdapter? = null,

    // どちらか非nullであること
    status: TootStatus? = null,
    summary: TootConversationSummary? = null,
) {
    // 未読クリアと表示の更新
    (summary ?: status?.conversationSummary)?.let {
        if (conversationUnreadClear(accessInfo, it)) {
            listAdapter?.notifyChange(
                reason = "ConversationSummary reset unread",
                reset = true
            )
        }
    }
    // 会話カラムを開く
    (status ?: summary?.last_status)?.let {
        conversation(pos, accessInfo, it)
    }
}

// プレビューカードのイメージは返信かもしれない
fun ActMain.clickCardImage(
    pos: Int,
    accessInfo: SavedAccount,
    card: TootCard?,
    longClick: Boolean = false,
) {
    card ?: return
    card.originalStatus?.let {
        if (longClick) {
            conversationOtherInstance(pos, it)
        } else {
            conversation(pos, accessInfo, it)
        }
        return
    }
    card.url?.notEmpty()?.let {
        openCustomTab(this, pos, it, accessInfo = accessInfo)
    }
}

// 「～からの返信」の表記を押した
fun ActMain.clickReplyInfo(
    pos: Int,
    accessInfo: SavedAccount,
    columnType: ColumnType,
    statusReply: TootStatus?,
    statusShowing: TootStatus?,
    longClick: Boolean = false,
    contextMenuOpener: ActMain.(status: TootStatus) -> Unit = {},
) {
    when {
        statusReply != null ->
            if (longClick) {
                contextMenuOpener(this, statusReply)
            } else {
                conversation(pos, accessInfo, statusReply)
            }

        // tootsearchは返信元のIDを取得するのにひと手間必要
        columnType == ColumnType.SEARCH_TS ||
                columnType == ColumnType.SEARCH_NOTESTOCK ->
            conversationFromTootsearch(pos, statusShowing)

        else ->
            statusShowing?.in_reply_to_id
                ?.let { conversationLocal(pos, accessInfo, it) }
    }
}

/////////////////////////////////////////////////////////////////////////////////////
// open conversation

// returns true if unread flag will be cleared.
internal fun ActMain.conversationUnreadClear(
    accessInfo: SavedAccount,
    conversationSummary: TootConversationSummary?,
): Boolean {

    // サマリがない
    conversationSummary ?: return false

    // 更新の必要がない
    if (!conversationSummary.unread) return false

    // 変数を更新
    conversationSummary.unread = false

    // 未読フラグのクリアをサーバに送る
    launchMain {
        runApiTask(accessInfo, progressStyle = ApiTask.PROGRESS_NONE) { client ->
            client.request(
                "/api/v1/conversations/${conversationSummary.id}/read",
                "".toFormRequestBody().toPost()
            )
        }
        // 応答の内容は見ない
    }

    return true // 表示も更新されるべき
}

// ローカルかリモートか判断する
fun ActMain.conversation(
    pos: Int,
    accessInfo: SavedAccount,
    status: TootStatus,
) {
    if (accessInfo.isNA || !accessInfo.matchHost(status.readerApDomain)) {
        conversationOtherInstance(pos, status)
    } else {

        conversationLocal(pos, accessInfo, status.id)
    }
}

// ローカルから見える会話の流れを表示する
fun ActMain.conversationLocal(
    pos: Int,
    accessInfo: SavedAccount,
    statusId: EntityId,
    isReference: Boolean = false,
) = addColumn(
    pos,
    accessInfo,
    when {
        isReference && TootInstance.getCached(accessInfo)?.canUseReference == true ->
            ColumnType.CONVERSATION_WITH_REFERENCE
        else ->
            ColumnType.CONVERSATION
    },
    statusId,
)

private val reDetailedStatusTime =
    """<a\b[^>]*?\bdetailed-status__datetime\b[^>]*href="https://[^/]+/@[^/]+/([^\s?#/"]+)"""
        .toRegex()

private val reHeaderOgUrl =
    """<meta\s+content="https://[^/"]+/notice/([^/"]+)"\s+property="og:url"/?>"""
        .toRegex()

// 疑似アカウントではURLからIDを取得するのにHTMLと正規表現を使う
suspend fun guessStatusIdFromPseudoAccount(
    context: Context, // for string resource
    client: TootApiClient, // for get HTML
    remoteStatusUrl: String,
): Pair<TootApiResult?, EntityId?> {

    val result = client.getHttp(remoteStatusUrl)

    result?.string?.let { html ->

        reDetailedStatusTime.find(html)
            ?.groupValues?.elementAtOrNull(1)
            ?.let { return Pair(result, EntityId(it)) }

        reHeaderOgUrl.find(html)
            ?.groupValues?.elementAtOrNull(1)
            ?.let { return Pair(result, EntityId(it)) }
    }

    return Pair(
        result?.setError(context.getString(R.string.status_id_conversion_failed)),
        null
    )
}

private fun ActMain.conversationRemote(
    pos: Int,
    accessInfo: SavedAccount,
    remoteStatusUrl: String,
) {

    launchMain {
        var localStatusId: EntityId? = null

        runApiTask(
            accessInfo,
            progressPrefix = getString(R.string.progress_synchronize_toot)
        ) { client ->
            if (accessInfo.isPseudo) {
                // 疑似アカウントではURLからIDを取得するのにHTMLと正規表現を使う
                val pair =
                    guessStatusIdFromPseudoAccount(applicationContext, client, remoteStatusUrl)
                localStatusId = pair.second
                pair.first
            } else {
                // 実アカウントでは検索APIを使える
                val (result, status) = client.syncStatus(accessInfo, remoteStatusUrl)
                if (status != null) {
                    localStatusId = status.id
                    log.d("status id conversion $remoteStatusUrl=>${status.id}")
                }
                result
            }
        }?.let { result ->
            when (val statusId = localStatusId) {
                null -> showToast(true, result.error)
                else -> conversationLocal(pos, accessInfo, statusId)
            }
        }
    }
}

// アプリ外部からURLを渡された場合に呼ばれる
fun ActMain.conversationOtherInstance(
    pos: Int,
    urlArg: String,
    statusIdOriginal: EntityId? = null,
    hostAccess: Host? = null,
    statusIdAccess: EntityId? = null,
    isReference: Boolean = false,
) {

    val activity = this

    val dialog = ActionsDialog()

    val hostOriginal = Host.parse(urlArg.toUri().authority ?: "")

    // 選択肢：ブラウザで表示する
    dialog.addAction(
        getString(
            R.string.open_web_on_host,
            hostOriginal.pretty
        )
    ) { openCustomTab(urlArg) }

    // トゥートの投稿元タンスにあるアカウント
    val localAccountList = ArrayList<SavedAccount>()

    // TLを読んだタンスにあるアカウント
    val accessAccountList = ArrayList<SavedAccount>()

    // その他のタンスにあるアカウント
    val otherAccountList = ArrayList<SavedAccount>()

    for (a in SavedAccount.loadAccountList(applicationContext)) {

        // 疑似アカウントは後でまとめて処理する
        if (a.isPseudo) continue

        if (isReference && TootInstance.getCached(a)?.canUseReference != true) continue

        if (statusIdOriginal != null && a.matchHost(hostOriginal)) {
            // アクセス情報＋ステータスID でアクセスできるなら
            // 同タンスのアカウントならステータスIDの変換なしに表示できる
            localAccountList.add(a)
        } else if (statusIdAccess != null && a.matchHost(hostAccess)) {
            // 既に変換済みのステータスIDがあるなら、そのアカウントでもステータスIDの変換は必要ない
            accessAccountList.add(a)
        } else {
            // 別タンスでも実アカウントなら検索APIでステータスIDを変換できる
            otherAccountList.add(a)
        }
    }

    // 参照の場合、status URLから/references を除去しないとURLでの検索ができない
    val url = when {
        isReference -> """/references\z""".toRegex().replace(urlArg, "")
        else -> urlArg
    }

    // 同タンスのアカウントがないなら、疑似アカウントで開く選択肢
    if (localAccountList.isEmpty()) {
        if (statusIdOriginal != null) {
            dialog.addAction(
                getString(R.string.open_in_pseudo_account, "?@${hostOriginal.pretty}")
            ) {
                launchMain {
                    addPseudoAccount(hostOriginal)?.let { sa ->
                        conversationLocal(pos, sa, statusIdOriginal, isReference = isReference)
                    }
                }
            }
        } else {
            dialog.addAction(
                getString(R.string.open_in_pseudo_account, "?@${hostOriginal.pretty}")
            ) {
                launchMain {
                    addPseudoAccount(hostOriginal)?.let { sa ->
                        conversationRemote(pos, sa, url)
                    }
                }
            }
        }
    }

    // ローカルアカウント
    if (statusIdOriginal != null) {
        SavedAccount.sort(localAccountList)
        for (a in localAccountList) {
            dialog.addAction(
                AcctColor.getStringWithNickname(
                    activity,
                    R.string.open_in_account,
                    a.acct
                )
            ) { conversationLocal(pos, a, statusIdOriginal, isReference = isReference) }
        }
    }

    // アクセスしたアカウント
    if (statusIdAccess != null) {
        SavedAccount.sort(accessAccountList)
        for (a in accessAccountList) {
            dialog.addAction(
                AcctColor.getStringWithNickname(
                    activity,
                    R.string.open_in_account,
                    a.acct
                )
            ) { conversationLocal(pos, a, statusIdAccess, isReference = isReference) }
        }
    }

    // その他の実アカウント
    SavedAccount.sort(otherAccountList)
    for (a in otherAccountList) {
        dialog.addAction(
            AcctColor.getStringWithNickname(
                activity,
                R.string.open_in_account,
                a.acct
            )
        ) { conversationRemote(pos, a, url) }
    }

    dialog.show(activity, activity.getString(R.string.open_status_from))
}

// リモートかもしれない会話の流れを表示する
fun ActMain.conversationOtherInstance(
    pos: Int,
    status: TootStatus?,
) {
    // URLが不明なトゥートというのはreblogの外側のアレ
    val url = status?.url?.notEmpty() ?: return

    when {

        // 検索サービスではステータスTLをどのタンスから読んだのか分からない
        status.readerApDomain == null ->
            conversationOtherInstance(
                pos,
                url,
                TootStatus.validStatusId(status.id)
                    ?: TootStatus.findStatusIdFromUri(
                        status.uri,
                        status.url
                    )
            )

        // TLアカウントのホストとトゥートのアカウントのホストが同じ
        status.originalApDomain == status.readerApDomain ->
            conversationOtherInstance(
                pos,
                url,
                TootStatus.validStatusId(status.id)
                    ?: TootStatus.findStatusIdFromUri(
                        status.uri,
                        status.url
                    )
            )

        else -> {
            // トゥートを取得したタンスと投稿元タンスが異なる場合
            // status.id はトゥートを取得したタンスでのIDである
            // 投稿元タンスでのIDはuriやURLから調べる
            // pleromaではIDがuuidなので失敗する(その時はURLを検索してIDを見つける)
            conversationOtherInstance(
                pos, url, TootStatus.findStatusIdFromUri(
                    status.uri,
                    status.url
                ), status.readerApDomain, TootStatus.validStatusId(status.id)
            )
        }
    }
}

////////////////////////////////////////

fun ActMain.conversationMute(
    accessInfo: SavedAccount,
    status: TootStatus?,
) {
    status ?: return

    // toggle change
    val bMute = !status.muted

    launchMain {
        var localStatus: TootStatus? = null
        runApiTask(accessInfo) { client ->
            client.request(
                "/api/v1/statuses/${status.id}/${if (bMute) "mute" else "unmute"}",
                "".toFormRequestBody().toPost()
            )?.also { result ->
                localStatus = TootParser(this, accessInfo).status(result.jsonObject)
            }
        }?.let { result ->
            when (val ls = localStatus) {
                null -> showToast(true, result.error)

                else -> {
                    for (column in appState.columnList) {
                        if (accessInfo == column.accessInfo) {
                            column.findStatus(accessInfo.apDomain, ls.id) { _, status ->
                                status.muted = bMute
                                true
                            }
                        }
                    }
                    showToast(
                        true,
                        if (bMute) R.string.mute_succeeded else R.string.unmute_succeeded
                    )
                }
            }
        }
    }
}

//////////////////////////////////////////////////

// tootsearch APIは投稿の返信元を示すreplyの情報がない。
// in_reply_to_idを参照するしかない
// ところがtootsearchでは投稿をどのタンスから読んだか分からないので、IDは全面的に信用できない。
// 疑似ではないアカウントを選んだ後に表示中の投稿を検索APIで調べて、そのリプライのIDを取得しなおす
fun ActMain.conversationFromTootsearch(
    pos: Int,
    statusArg: TootStatus?,
) {
    statusArg ?: return

    // step2: 選択したアカウントで投稿を検索して返信元の投稿のIDを調べる
    fun step2(a: SavedAccount) = launchMain {
        var tmp: TootStatus? = null
        runApiTask(a) { client ->
            val (result, status) = client.syncStatus(a, statusArg)
            tmp = status
            result
        }?.let { result ->
            val status = tmp
            val replyId = status?.in_reply_to_id
            when {
                status == null -> showToast(true, result.error ?: "?")
                replyId == null -> showToast(true, "showReplyTootsearch: in_reply_to_id is null")
                else -> conversationLocal(pos, a, replyId)
            }
        }
    }

    // step 1: choose account

    val host = statusArg.account.apDomain
    val localAccountList = ArrayList<SavedAccount>()
    val otherAccountList = ArrayList<SavedAccount>()

    for (a in SavedAccount.loadAccountList(this)) {

        // 検索APIはログイン必須なので疑似アカウントは使えない
        if (a.isPseudo) continue

        if (a.matchHost(host)) {
            localAccountList.add(a)
        } else {
            otherAccountList.add(a)
        }
    }

    val dialog = ActionsDialog()

    SavedAccount.sort(localAccountList)
    for (a in localAccountList) {
        dialog.addAction(
            AcctColor.getStringWithNickname(
                this,
                R.string.open_in_account,
                a.acct
            )
        ) { step2(a) }
    }

    SavedAccount.sort(otherAccountList)
    for (a in otherAccountList) {
        dialog.addAction(
            AcctColor.getStringWithNickname(
                this,
                R.string.open_in_account,
                a.acct
            )
        ) { step2(a) }
    }

    dialog.show(this, getString(R.string.open_status_from))
}
