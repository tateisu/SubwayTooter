package jp.juggler.subwaytooter.actmain

import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.PrefL
import jp.juggler.subwaytooter.column.fireShowColumnHeader
import jp.juggler.subwaytooter.table.SavedAccount
import java.util.ArrayList

// デフォルトの投稿先アカウントを探す。アカウント選択が必要な状況ならnull
val ActMain.currentPostTarget: SavedAccount?
    get() = phoneTab(
        { env ->
            val c = env.pagerAdapter.getColumn(env.pager.currentItem)
            return when {
                c == null || c.accessInfo.isPseudo -> null
                else -> c.accessInfo
            }
        },
        { env ->

            val dbId = PrefL.lpTabletTootDefaultAccount(App1.pref)
            if (dbId != -1L) {
                val a = SavedAccount.loadAccount(this@currentPostTarget, dbId)
                if (a != null && !a.isPseudo) return a
            }

            val accounts = ArrayList<SavedAccount>()
            for (c in env.visibleColumns) {
                try {
                    val a = c.accessInfo
                    // 画面内に疑似アカウントがあれば常にアカウント選択が必要
                    if (a.isPseudo) {
                        accounts.clear()
                        break
                    }
                    // 既出でなければ追加する
                    if (null == accounts.find { it == a }) accounts.add(a)
                } catch (ignored: Throwable) {
                }
            }

            return when (accounts.size) {
                // 候補が1つだけならアカウント選択は不要
                1 -> accounts.first()
                // 候補が2つ以上ならアカウント選択は必要
                else -> null
            }
        })

fun ActMain.reloadAccountSetting(newAccounts: ArrayList<SavedAccount> = SavedAccount.loadAccountList(this)) {
    for (column in appState.columnList) {
        val a = column.accessInfo
        if (!a.isNA) a.reloadSetting(this, newAccounts.find { it.acct == a.acct })
        column.fireShowColumnHeader()
    }
}

fun ActMain.reloadAccountSetting(account: SavedAccount) {
    val newData = SavedAccount.loadAccount(this, account.db_id)
        ?: return
    for (column in appState.columnList) {
        val a = column.accessInfo
        if (a.acct != newData.acct) continue
        if (!a.isNA) a.reloadSetting(this, newData)
        column.fireShowColumnHeader()
    }
}
