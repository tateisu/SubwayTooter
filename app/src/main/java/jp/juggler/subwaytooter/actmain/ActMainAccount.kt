package jp.juggler.subwaytooter.actmain

import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.column.fireShowColumnHeader
import jp.juggler.subwaytooter.pref.PrefL
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.daoSavedAccount

// デフォルトの投稿先アカウントを探す。アカウント選択が必要な状況ならnull
val ActMain.currentPostTarget: SavedAccount?
    get() {
        val dbId = PrefL.lpDefaultPostAccount.value
        if (dbId != -1L) {
            val a = daoSavedAccount.loadAccount(dbId)
            if (a != null && !a.isPseudo) return a
        }
        phoneTab(
            { env ->
                val c = env.pagerAdapter.getColumn(env.pager.currentItem)
                return when {
                    c == null || c.accessInfo.isPseudo -> null
                    else -> c.accessInfo
                }
            },
            { env ->
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
                        if (accounts.none { it == a }) accounts.add(a)
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
    }

fun ActMain.reloadAccountSetting(
    newAccounts: List<SavedAccount>,
) {
    for (column in appState.columnList) {
        val a = column.accessInfo
        val b = newAccounts.find { it.acct == a.acct }
        if (!a.isNA && b != null) daoSavedAccount.reloadSetting(a, b)
        column.fireShowColumnHeader()
    }
}

fun ActMain.reloadAccountSetting(account: SavedAccount) {
    val newData = daoSavedAccount.loadAccount(account.db_id)
        ?: return
    for (column in appState.columnList) {
        val a = column.accessInfo
        if (a.acct != newData.acct) continue
        if (!a.isNA) daoSavedAccount.reloadSetting(a, newData)
        column.fireShowColumnHeader()
    }
}
