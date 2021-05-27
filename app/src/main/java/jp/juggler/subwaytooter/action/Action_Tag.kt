package jp.juggler.subwaytooter.action

import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.ColumnType
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.matchHost
import jp.juggler.subwaytooter.util.openCustomTab
import jp.juggler.util.encodePercent
import jp.juggler.util.launchMain
import java.util.*

// ハッシュタグへの操作を選択する
fun ActMain.tagDialog(
    pos: Int,
    url: String,
    host: Host,
    tag_without_sharp: String,
    tag_list: ArrayList<String>?,
    whoAcct: Acct?
) {
    val tag_with_sharp = "#$tag_without_sharp"

    val d = ActionsDialog()
        .addAction(getString(R.string.open_hashtag_column)) {
            tagTimelineFromAccount(
                pos,
                url,
                host,
                tag_without_sharp
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
                "https://${whoAcct.host?.ascii}/@${whoAcct.username}/tagged/${tag_without_sharp.encodePercent()}",
                host,
                tag_without_sharp,
                whoAcct
            )
        }
    }


    d.addAction(getString(R.string.open_in_browser)){ openCustomTab(url) }
        .addAction(getString(R.string.quote_hashtag_of, tag_with_sharp)){ openPost("$tag_with_sharp ") }


    if (tag_list != null && tag_list.size > 1) {
        val sb = StringBuilder()
        for (s in tag_list) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(s)
        }
        val tag_all = sb.toString()
        d.addAction(
            getString(
                R.string.quote_all_hashtag_of,
                tag_all
            )
        ) { openPost("$tag_all ") }
    }

    d.show(this, tag_with_sharp)
}

// 検索カラムからハッシュタグを選んだ場合、カラムのアカウントでハッシュタグを開く
fun ActMain.tagTimeline(
    pos: Int,
    access_info: SavedAccount,
    tag_without_sharp: String,
    acctAscii: String? = null
) {
    if (acctAscii == null) {
        addColumn(pos, access_info, ColumnType.HASHTAG, tag_without_sharp)
    } else {
        addColumn(
            pos,
            access_info,
            ColumnType.HASHTAG_FROM_ACCT,
            tag_without_sharp,
            acctAscii
        )
    }
}

// アカウントを選んでハッシュタグカラムを開く
fun ActMain.tagTimelineFromAccount(
    pos: Int,
    url: String,
    host: Host,
    tag_without_sharp: String,
    acct: Acct? = null
) {

    val dialog = ActionsDialog()

    val account_list = SavedAccount.loadAccountList(this)
    SavedAccount.sort(account_list)

    // 分類する
    val list_original = ArrayList<SavedAccount>()
    val list_original_pseudo = ArrayList<SavedAccount>()
    val list_other = ArrayList<SavedAccount>()
    for (a in account_list) {
        if (acct == null) {
            when {
                !a.matchHost(host) -> list_other.add(a)
                a.isPseudo -> list_original_pseudo.add(a)
                else -> list_original.add(a)
            }
        } else {
            when {

                // acctからidを取得できない
                a.isPseudo -> {
                }

                // ミスキーのアカウント別タグTLは未対応
                a.isMisskey -> {
                }

                !a.matchHost(host) -> list_other.add(a)
                else -> list_original.add(a)
            }
        }
    }

    // ブラウザで表示する
    dialog.addAction(getString(R.string.open_web_on_host, host))
    { openCustomTab(url) }

    // 同タンスのアカウントがない場合は疑似アカウントを作成して開く
    // ただし疑似アカウントではアカウントの同期ができないため、特定ユーザのタグTLは読めない)
    if (acct == null && list_original.isEmpty() && list_original_pseudo.isEmpty()) {
        dialog.addAction(getString(R.string.open_in_pseudo_account, "?@$host")) {
            launchMain{
                addPseudoAccount( host) ?.let{ tagTimeline( pos, it, tag_without_sharp) }
            }
        }
    }

    // 分類した順に選択肢を追加する
    for (a in list_original) {
        dialog.addAction(
            AcctColor.getStringWithNickname(
                this,
                R.string.open_in_account,
                a.acct
            )
        )
        { tagTimeline( pos, a, tag_without_sharp, acct?.ascii) }
    }
    for (a in list_original_pseudo) {
        dialog.addAction( AcctColor.getStringWithNickname( this, R.string.open_in_account, a.acct )){
			tagTimeline( pos, a, tag_without_sharp, acct?.ascii)
        }
    }
    for (a in list_other) {
        dialog.addAction(AcctColor.getStringWithNickname(this,R.string.open_in_account,a.acct) ){
			tagTimeline( pos, a, tag_without_sharp, acct?.ascii)
        }
    }

    dialog.show(this, "#$tag_without_sharp")
}
