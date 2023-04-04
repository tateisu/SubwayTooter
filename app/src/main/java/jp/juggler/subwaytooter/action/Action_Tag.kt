package jp.juggler.subwaytooter.action

import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.actmain.addColumn
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.api.entity.TootTag
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.column.onTagFollowChanged
import jp.juggler.subwaytooter.dialog.DlgConfirm.confirm
import jp.juggler.subwaytooter.dialog.actionsDialog
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.daoAcctColor
import jp.juggler.subwaytooter.table.daoSavedAccount
import jp.juggler.subwaytooter.table.sortedByNickname
import jp.juggler.subwaytooter.util.matchHost
import jp.juggler.subwaytooter.util.openCustomTab
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.encodePercent
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.network.toFormRequestBody
import jp.juggler.util.network.toPost
import kotlinx.coroutines.withContext

private val log = LogCategory("Action_Tag")

fun ActMain.longClickTootTag(pos: Int, accessInfo: SavedAccount, item: TootTag) {
    tagTimelineFromAccount(
        pos = pos,
        url = "https://${accessInfo.apiHost.ascii}/tags/${item.name.encodePercent()}",
        host = accessInfo.apiHost,
        tagWithoutSharp = item.name
    )
}

// ハッシュタグへの操作を選択する
fun ActMain.tagDialog(
    accessInfo: SavedAccount?,
    pos: Int,
    // タグのURL
    // URLのホスト部分は普通はaccessInfoと同じだが、検索などでバラバラになる場合がある
    url: String?,
    // タグのHost。
    // 普通はaccessInfoと同じだが、検索などでバラバラになる場合がある
    host: Host,
    // タグの名前
    tagWithoutSharp: String,
    // 複数のタグをまとめて引用したい場合がある
    tagList: ArrayList<String>? = null,
    // nullでなければ投稿者別タグTLを開く選択肢を表示する
    whoAcct: Acct? = null,
    // TootTagの情報があれば
    tagInfo: TootTag? = null,
) {
    val activity = this
    val tagWithSharp = "#$tagWithoutSharp"
    launchAndShowError {
        actionsDialog(tagWithSharp) {
            action(getString(R.string.open_hashtag_column)) {
                tagTimelineFromAccount(
                    pos,
                    url,
                    host,
                    tagWithoutSharp
                )
            }

            // 投稿者別タグTL
            if (whoAcct != null) {
                action(
                    daoAcctColor.getStringWithNickname(
                        activity,
                        R.string.open_hashtag_from_account,
                        whoAcct
                    )
                ) {
                    tagTimelineFromAccount(
                        pos,
                        "https://${whoAcct.host?.ascii}/@${whoAcct.username}/tagged/${tagWithoutSharp.encodePercent()}",
                        host,
                        tagWithoutSharp,
                        whoAcct
                    )
                }
            }

            action(getString(R.string.open_in_browser)) {
                openCustomTab(url)
            }

            action(getString(R.string.quote_hashtag_of, tagWithSharp)) {
                openPost("$tagWithSharp ")
            }

            if (tagList != null && tagList.size > 1) {
                val sb = StringBuilder()
                for (s in tagList) {
                    if (sb.isNotEmpty()) sb.append(' ')
                    sb.append(s)
                }
                val tagAll = sb.toString()
                action(
                    getString(R.string.quote_all_hashtag_of, tagAll)
                ) { openPost("$tagAll ") }
            }

            val ti = TootInstance.getCached(accessInfo)
            if (ti != null && accessInfo?.isMisskey == false) {
                var tag = tagInfo
                if (tag == null) {
                    val result = runApiTask(accessInfo) { client ->
                        client.request("/api/v1/tags/${tagWithoutSharp.encodePercent()}")
                    }
                    if (result != null) {
                        TootParser(activity, accessInfo)
                            .tag(result.jsonObject)
                            ?.let { tag = it }
                    }
                }

                val toggle = !(tag?.following ?: false)
                val toggleCaption = when (toggle) {
                    true -> R.string.follow_hashtag_of
                    else -> R.string.unfollow_hashtag_of
                }
                action(getString(toggleCaption, tagWithSharp)) {
                    followHashTag(accessInfo, tagWithoutSharp, toggle)
                }
            }
        }
    }
}

// 検索カラムからハッシュタグを選んだ場合、カラムのアカウントでハッシュタグを開く
fun ActMain.tagTimeline(
    pos: Int,
    accessInfo: SavedAccount,
    tagWithoutSharp: String,
    acctAscii: String? = null,
) {
    if (acctAscii == null) {
        addColumn(pos, accessInfo, ColumnType.HASHTAG, params = arrayOf(tagWithoutSharp))
    } else {
        addColumn(
            pos,
            accessInfo,
            ColumnType.HASHTAG_FROM_ACCT,
            params = arrayOf(tagWithoutSharp, acctAscii)
        )
    }
}

// アカウントを選んでハッシュタグカラムを開く
fun ActMain.tagTimelineFromAccount(
    pos: Int,
    // タグのURL
    // URLのホスト部分は普通はaccessInfoと同じだが、検索などでバラバラになる場合がある
    url: String?,
    // タグのHost。
    // 普通はaccessInfoと同じだが、検索などでバラバラになる場合がある
    host: Host,
    // タグの名前。#を含まない
    tagWithoutSharp: String,
    // 「投稿者別タグTL」を開くなら、投稿者のacctを指定する
    acct: Acct? = null,
) {
    val activity = this
    launchAndShowError {
        actionsDialog("#$tagWithoutSharp") {

            val accountList = daoSavedAccount.loadAccountList().sortedByNickname()

            // 分類する
            val listOriginal = ArrayList<SavedAccount>()
            val listOriginalPseudo = ArrayList<SavedAccount>()
            val listOther = ArrayList<SavedAccount>()
            for (a in accountList) {
                if (acct == null) {
                    when {
                        !a.matchHost(host) -> listOther.add(a)
                        a.isPseudo -> listOriginalPseudo.add(a)
                        else -> listOriginal.add(a)
                    }
                } else {
                    when {
                        // 疑似アカウントはacctからaccount idを取得できないので
                        // アカウント別タグTLを開けない
                        a.isPseudo -> Unit

                        // ミスキーはアカウント別タグTLがないので
                        // アカウント別タグTLを開けない
                        a.isMisskey -> Unit

                        !a.matchHost(host) -> listOther.add(a)
                        else -> listOriginal.add(a)
                    }
                }
            }

            // ブラウザで表示する
            if (!url.isNullOrBlank()) {
                action(getString(R.string.open_web_on_host, host)) {
                    openCustomTab(url)
                }
            }

            // 同タンスのアカウントがない場合は疑似アカウントを作成して開く
            // ただし疑似アカウントではアカウントの同期ができないため、特定ユーザのタグTLは読めない)
            if (acct == null && listOriginal.isEmpty() && listOriginalPseudo.isEmpty()) {
                action(getString(R.string.open_in_pseudo_account, "?@$host")) {
                    launchMain {
                        addPseudoAccount(host)?.let { tagTimeline(pos, it, tagWithoutSharp) }
                    }
                }
            }

            // 分類した順に選択肢を追加する
            for (a in listOriginal) {
                action(
                    daoAcctColor.getStringWithNickname(activity, R.string.open_in_account, a.acct)
                ) {
                    tagTimeline(pos, a, tagWithoutSharp, acct?.ascii)
                }
            }
            for (a in listOriginalPseudo) {
                action(
                    daoAcctColor.getStringWithNickname(activity, R.string.open_in_account, a.acct)
                ) {
                    tagTimeline(pos, a, tagWithoutSharp, acct?.ascii)
                }
            }
            for (a in listOther) {
                action(
                    daoAcctColor.getStringWithNickname(activity, R.string.open_in_account, a.acct)
                ) {
                    tagTimeline(pos, a, tagWithoutSharp, acct?.ascii)
                }
            }
        }
    }
}

fun ActMain.followHashTag(
    accessInfo: SavedAccount,
    tagWithoutSharp: String,
    isSet: Boolean,
) {
    val activity = this
    launchMain {
        if (!isSet) confirm(R.string.unfollow_hashtag_confirm, tagWithoutSharp)
        runApiTask(accessInfo) { client ->
            client.request(
                "/api/v1/tags/${tagWithoutSharp.encodePercent()}/${if (isSet) "follow" else "unfollow"}",
                "".toFormRequestBody().toPost()
            )
        }?.let { result ->
            when (val error = result.error) {
                null -> {
                    showToast(
                        false,
                        when {
                            isSet -> R.string.follow_succeeded
                            else -> R.string.unfollow_succeeded
                        }
                    )
                    // 成功時はTagオブジェクトが返る
                    // フォロー中のタグ一覧を更新する
                    TootParser(activity, accessInfo).tag(result.jsonObject)?.let { tag ->
                        withContext(AppDispatchers.MainImmediate) {
                            for (column in appState.columnList) {
                                column.onTagFollowChanged(accessInfo, tag)
                            }
                        }
                    }
                }
                else -> showToast(true, error)
            }
        }
    }
}
