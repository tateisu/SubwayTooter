package jp.juggler.subwaytooter.action

import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.actmain.addColumn
import jp.juggler.subwaytooter.actmain.defaultInsertPosition
import jp.juggler.subwaytooter.actmain.nextPosition
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.column.onDomainBlockChanged
import jp.juggler.subwaytooter.dialog.pickAccount
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.matchHost
import jp.juggler.subwaytooter.util.openBrowser
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.encodePercent
import jp.juggler.util.log.showToast
import jp.juggler.util.network.toFormRequestBody
import jp.juggler.util.network.toRequest

// profile directory を開く
private fun ActMain.serverProfileDirectory(
    accessInfo: SavedAccount,
    host: Host,
    instance: TootInstance? = null,
    pos: Int = defaultInsertPosition,
) {
    when {
        // インスタンスのバージョン情報がなければ取得してやり直し
        instance == null -> launchMain {
            var targetInstance: TootInstance? = null
            runApiTask(host) { client ->
                val (ti, ri) = TootInstance.getEx(client, host, allowPixelfed = true)
                targetInstance = ti
                ri
            }?.let { result ->
                when (val ti = targetInstance) {
                    null -> showToast(true, result.error)
                    else -> serverProfileDirectory(accessInfo, host, ti, pos)
                }
            }
        }

        // Misskey非対応
        instance.instanceType == InstanceType.Misskey ->
            showToast(false, R.string.profile_directory_not_supported_on_misskey)

        // バージョンが足りないならWebページを開く
        !instance.versionGE(TootInstance.VERSION_3_0_0_rc1) ->
            openBrowser("https://${host.ascii}/explore")

        // ホスト名部分が一致するならそのアカウントで開く
        accessInfo.matchHost(host) ->
            addColumn(
                false,
                pos,
                accessInfo,
                ColumnType.PROFILE_DIRECTORY,
                params = arrayOf(host)
            )

        // 疑似アカウントで開く
        else -> launchMain {
            addPseudoAccount(host, instance)?.let { ai ->
                addColumn(
                    false,
                    pos,
                    ai,
                    ColumnType.PROFILE_DIRECTORY,
                    params = arrayOf(host)
                )
            }
        }
    }
}

// サイドメニューからprofile directory を開く
fun ActMain.serverProfileDirectoryFromSideMenu() {
    launchMain {
        pickAccount(
            bAllowPseudo = true,
            bAllowMisskey = false,
            bAllowMastodon = true,
            bAuto = true,
            message = getString(
                R.string.account_picker_add_timeline_of,
                ColumnType.PROFILE_DIRECTORY.name1(applicationContext)
            )
        )?.let { ai ->
            serverProfileDirectory(ai, ai.apiHost)
        }
    }
}

// インスタンス情報カラムやコンテキストメニューからprofile directoryを開く
fun ActMain.serverProfileDirectoryFromInstanceInformation(
    currentColumn: Column,
    host: Host,
    instance: TootInstance? = null,
) = serverProfileDirectory(
    currentColumn.accessInfo,
    host,
    instance = instance,
    pos = nextPosition(currentColumn)
)

// インスタンス情報カラムを開く
fun ActMain.serverInformation(
    pos: Int,
    host: Host,
) = addColumn(
    false,
    pos,
    SavedAccount.na,
    ColumnType.INSTANCE_INFORMATION,
    params = arrayOf(host),
)

// ドメインブロック一覧から解除
fun ActMain.clickDomainBlock(accessInfo: SavedAccount, item: TootDomainBlock) {
    AlertDialog.Builder(this)
        .setMessage(getString(R.string.confirm_unblock_domain, item.domain.pretty))
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(R.string.ok) { _, _ ->
            domainBlock(accessInfo, item.domain, bBlock = false)
        }
        .show()
}

// ContextMenuからドメインブロックを追加
fun ActMain.clickDomainBlock(
    accessInfo: SavedAccount,
    who: TootAccount,
) {
    // 疑似アカウントではドメインブロックできない
    if (accessInfo.isPseudo) {
        showToast(false, R.string.domain_block_from_pseudo)
        return
    }

    val whoApDomain = who.apDomain

    // 自分のドメインではブロックできない
    if (accessInfo.matchHost(whoApDomain)) {
        showToast(false, R.string.domain_block_from_local)
        return
    }

    AlertDialog.Builder(this)
        .setMessage(getString(R.string.confirm_block_domain, whoApDomain))
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(R.string.ok) { _, _ ->
            domainBlock(accessInfo, whoApDomain, true)
        }
        .show()
}

// ドメインブロック
fun ActMain.domainBlock(
    accessInfo: SavedAccount,
    domain: Host,
    bBlock: Boolean,
) {

    if (accessInfo.matchHost(domain)) {
        showToast(false, R.string.it_is_you)
        return
    }

    launchMain {
        runApiTask(accessInfo) { client ->
            client.request(
                "/api/v1/domain_blocks",
                "domain=${domain.ascii.encodePercent()}"
                    .toFormRequestBody()
                    .toRequest(if (bBlock) "POST" else "DELETE")
            )
        }?.let { result ->
            when (result.jsonObject) {
                null -> showToast(false, result.error)
                else -> {
                    for (column in appState.columnList) {
                        column.onDomainBlockChanged(accessInfo, domain, bBlock)
                    }
                    showToast(
                        false,
                        if (bBlock) R.string.block_succeeded else R.string.unblock_succeeded
                    )
                }
            }
        }
    }
}
