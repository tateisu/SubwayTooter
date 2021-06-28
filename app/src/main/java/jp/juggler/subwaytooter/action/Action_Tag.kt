package jp.juggler.subwaytooter.action

import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.actmain.addColumn
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.entity.TootTag
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.matchHost
import jp.juggler.subwaytooter.util.openCustomTab
import jp.juggler.util.encodePercent
import jp.juggler.util.launchMain
import java.util.*

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
    pos: Int,
    url: String,
    host: Host,
    tagWithoutSharp: String,
    tagList: ArrayList<String>?,
    whoAcct: Acct?,
) {
    val tagWithSharp = "#$tagWithoutSharp"

    val d = ActionsDialog()
        .addAction(getString(R.string.open_hashtag_column)) {
            tagTimelineFromAccount(
                pos,
                url,
                host,
                tagWithoutSharp
            )
        }

    // https://mastodon.juggler.jp/@tateisu/101865456016473337
    // 一時的に使えなくする
    if (whoAcct != null) {
        d.addAction(
            AcctColor.getStringWithNickname(
                this,
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

    d.addAction(getString(R.string.open_in_browser)) { openCustomTab(url) }
        .addAction(getString(R.string.quote_hashtag_of, tagWithSharp)) { openPost("$tagWithSharp ") }

    if (tagList != null && tagList.size > 1) {
        val sb = StringBuilder()
        for (s in tagList) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(s)
        }
        val tagAll = sb.toString()
        d.addAction(
            getString(
                R.string.quote_all_hashtag_of,
                tagAll
            )
        ) { openPost("$tagAll ") }
    }

    d.show(this, tagWithSharp)
}

// 検索カラムからハッシュタグを選んだ場合、カラムのアカウントでハッシュタグを開く
fun ActMain.tagTimeline(
    pos: Int,
    accessInfo: SavedAccount,
    tagWithoutSharp: String,
    acctAscii: String? = null,
) {
    if (acctAscii == null) {
        addColumn(pos, accessInfo, ColumnType.HASHTAG, tagWithoutSharp)
    } else {
        addColumn(
            pos,
            accessInfo,
            ColumnType.HASHTAG_FROM_ACCT,
            tagWithoutSharp,
            acctAscii
        )
    }
}

// アカウントを選んでハッシュタグカラムを開く
fun ActMain.tagTimelineFromAccount(
    pos: Int,
    url: String,
    host: Host,
    tagWithoutSharp: String,
    acct: Acct? = null,
) {

    val dialog = ActionsDialog()

    val accountList = SavedAccount.loadAccountList(this)
    SavedAccount.sort(accountList)

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

                // acctからidを取得できない
                a.isPseudo -> {
                }

                // ミスキーのアカウント別タグTLは未対応
                a.isMisskey -> {
                }

                !a.matchHost(host) -> listOther.add(a)
                else -> listOriginal.add(a)
            }
        }
    }

    // ブラウザで表示する
    dialog.addAction(getString(R.string.open_web_on_host, host)) {
        openCustomTab(url)
    }

    // 同タンスのアカウントがない場合は疑似アカウントを作成して開く
    // ただし疑似アカウントではアカウントの同期ができないため、特定ユーザのタグTLは読めない)
    if (acct == null && listOriginal.isEmpty() && listOriginalPseudo.isEmpty()) {
        dialog.addAction(getString(R.string.open_in_pseudo_account, "?@$host")) {
            launchMain {
                addPseudoAccount(host)?.let { tagTimeline(pos, it, tagWithoutSharp) }
            }
        }
    }

    // 分類した順に選択肢を追加する
    for (a in listOriginal) {
        dialog.addAction(
            AcctColor.getStringWithNickname(
                this,
                R.string.open_in_account,
                a.acct
            )
        ) {
            tagTimeline(pos, a, tagWithoutSharp, acct?.ascii)
        }
    }
    for (a in listOriginalPseudo) {
        dialog.addAction(AcctColor.getStringWithNickname(this, R.string.open_in_account, a.acct)) {
            tagTimeline(pos, a, tagWithoutSharp, acct?.ascii)
        }
    }
    for (a in listOther) {
        dialog.addAction(AcctColor.getStringWithNickname(this, R.string.open_in_account, a.acct)) {
            tagTimeline(pos, a, tagWithoutSharp, acct?.ascii)
        }
    }

    dialog.show(this, "#$tagWithoutSharp")
}
