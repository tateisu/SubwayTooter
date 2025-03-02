package jp.juggler.subwaytooter.actmain

import android.app.AlertDialog
import android.text.Spannable
import android.view.View
import android.widget.TextView
import androidx.core.view.GravityCompat
import androidx.work.WorkManager
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.action.openColumnList
import jp.juggler.subwaytooter.action.openPost
import jp.juggler.subwaytooter.api.entity.TootAccountRef
import jp.juggler.subwaytooter.api.entity.TootTag.Companion.findHashtagFromUrl
import jp.juggler.subwaytooter.appsetting.appSettingRoot
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.columnviewholder.ColumnViewHolder
import jp.juggler.subwaytooter.columnviewholder.TabletColumnViewHolder
import jp.juggler.subwaytooter.columnviewholder.ViewHolderHeaderBase
import jp.juggler.subwaytooter.columnviewholder.ViewHolderItem
import jp.juggler.subwaytooter.dialog.actionsDialog
import jp.juggler.subwaytooter.itemviewholder.ItemViewHolder
import jp.juggler.subwaytooter.pref.*
import jp.juggler.subwaytooter.push.PushWorker
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.util.checkPrivacyPolicy
import jp.juggler.subwaytooter.util.openCustomTab
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.data.addTo
import jp.juggler.util.data.cast
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.ui.dismissSafe
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit

private val log = LogCategory("ActMainActions")

fun ActMain.onBackPressedImpl() {
    launchAndShowError {

        // メニューが開いていたら閉じる
        if (views.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            views.drawerLayout.closeDrawer(GravityCompat.START)
            return@launchAndShowError
        }

        // カラムが0個ならアプリを終了する
        if (appState.columnCount == 0) {
            finish()
            return@launchAndShowError
        }

        // カラム設定が開いているならカラム設定を閉じる
        if (closeColumnSetting()) {
            return@launchAndShowError
        }

        fun getClosableColumnList(): List<Column> {
            val visibleColumnList = ArrayList<Column>()
            phoneTab({ env ->
                try {
                    appState.column(env.pager.currentItem)?.addTo(visibleColumnList)
                } catch (ex: Throwable) {
                    log.e(ex, "getClosableColumnList failed.")
                }
            }, { env ->
                visibleColumnList.addAll(env.visibleColumns)
            })

            return visibleColumnList.filter { !it.dontClose }
        }

        // カラムが1個以上ある場合は設定に合わせて挙動を変える
        when (PrefI.ipBackButtonAction.value) {
            PrefI.BACK_EXIT_APP -> finish()
            PrefI.BACK_OPEN_COLUMN_LIST -> openColumnList()
            PrefI.BACK_CLOSE_COLUMN -> {
                val closeableColumnList = getClosableColumnList()
                when (closeableColumnList.size) {
                    0 -> when {
                        PrefB.bpExitAppWhenCloseProtectedColumn.value &&
                                PrefB.bpDontConfirmBeforeCloseColumn.value ->
                            finish()

                        else -> showToast(false, R.string.missing_closeable_column)
                    }

                    1 -> closeColumn(closeableColumnList.first())
                    else -> showToast(
                        false,
                        R.string.cant_close_column_by_back_button_when_multiple_column_shown
                    )
                }
            }
            /* PrefI.BACK_ASK_ALWAYS */
            else -> actionsDialog {
                val closeableColumnList = getClosableColumnList()
                if (closeableColumnList.size == 1) {
                    val column = closeableColumnList.first()
                    action(getString(R.string.close_column)) {
                        closeColumn(column, bConfirmed = true)
                    }
                }
                action(getString(R.string.open_column_list)) { openColumnList() }
                action(getString(R.string.app_exit)) { finish() }
            }
        }
    }
}

fun ActMain.onClickImpl(v: View) {
    when (v.id) {
        R.id.btnToot -> openPost()
        R.id.ivQuickTootAccount -> quickPostAccountDialog(
            getString(
                R.string.account_picker_add_timeline_of,
                ColumnType.PROFILE.name1(this)
            )
        ) { openProfileQuickPostAccount(it) }

        R.id.btnQuickToot -> quickPostAccountDialog { performQuickPost(it) }
        R.id.btnQuickTootMenu -> toggleQuickPostMenu()
        R.id.btnMenu -> if (!views.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            views.drawerLayout.openDrawer(GravityCompat.START)
        }
    }
}

fun ActMain.onMyClickableSpanClickedImpl(viewClicked: View, span: MyClickableSpan) {
    // ビュー階層を下から辿って文脈を取得する
    var column: Column? = null
    var whoRef: TootAccountRef? = null
    var view = viewClicked
    loop@ while (true) {
        when (val tag = view.tag) {
            is ItemViewHolder -> {
                column = tag.column
                whoRef = tag.getAccount()
                break@loop
            }

            is ViewHolderItem -> {
                column = tag.ivh.column
                whoRef = tag.ivh.getAccount()
                break@loop
            }

            is ColumnViewHolder -> {
                column = tag.column
                whoRef = null
                break@loop
            }

            is ViewHolderHeaderBase -> {
                column = tag.column
                whoRef = tag.getAccount()
                break@loop
            }

            is TabletColumnViewHolder -> {
                column = tag.columnViewHolder.column
                break@loop
            }

            else -> when (val parent = view.parent) {
                is View -> view = parent
                else -> break@loop
            }
        }
    }

    val hashtagList = ArrayList<String>().apply {
        try {
            val cs = viewClicked.cast<TextView>()?.text
            if (cs is Spannable) {
                for (s in cs.getSpans(0, cs.length, MyClickableSpan::class.java)) {
                    val li = s.linkInfo
                    val pair = li.url.findHashtagFromUrl()
                    if (pair != null) add(if (li.text.startsWith('#')) li.text else "#${pair.first}")
                }
            }
        } catch (ex: Throwable) {
            log.e(ex, "can't create hashtagList")
        }
    }

    val linkInfo = span.linkInfo

    openCustomTab(
        this,
        nextPosition(column),
        linkInfo.url,
        accessInfo = column?.accessInfo,
        tagList = hashtagList.notEmpty(),
        whoRef = whoRef,
        linkInfo = linkInfo
    )
}

suspend fun ActMain.themeDefaultChangedDialog() {
    val lpThemeDefaultChangedWarnTime = PrefL.lpThemeDefaultChangedWarnTime
    val ipUiTheme = PrefI.ipUiTheme
    val now = System.currentTimeMillis()

    // テーマが未定義でなければ警告しない
    if (lazyPref.getInt(ipUiTheme.key, -1) != -1) {
        log.i("themeDefaultChangedDialog: theme was set.")
        return
    }

    // 頻繁には警告しない
    if (now - lpThemeDefaultChangedWarnTime.value < TimeUnit.DAYS.toMillis(60L)) {
        log.i("themeDefaultChangedDialog: avoid frequently check.")
        return
    }
    lpThemeDefaultChangedWarnTime.value = now

    // 色がすべてデフォルトなら警告不要
    val customizedKeys = ArrayList<String>()
    appSettingRoot.items.find { it.caption == R.string.color }?.scan { item ->
        item.pref?.let { p ->
            when {
                p == PrefS.spBoostAlpha -> Unit
                p.hasNonDefaultValue() -> customizedKeys.add(p.key)
            }
        }
    }
    if (customizedKeys.isEmpty()) {
        ipUiTheme.value = ipUiTheme.defVal
        return
    }
    log.w("themeDefaultChangedDialog: customizedKeys=${customizedKeys.joinToString(",")}")
    suspendCancellableCoroutine { cont ->
        val dialog = AlertDialog.Builder(this)
            .setMessage(R.string.color_theme_changed)
            .setPositiveButton(android.R.string.ok, null)
            .setOnDismissListener {
                if (cont.isActive) cont.resume(Unit) { _, _, _ -> }
            }
            .create()
        cont.invokeOnCancellation { dialog.dismissSafe() }
        dialog.show()
    }
}

fun ActMain.launchDialogs() {
    launchAndShowError {
        // プライバシーポリシー
        val agreed = try {
            checkPrivacyPolicy()
        } catch (ex: Throwable) {
            log.e(ex, "checkPrivacyPolicy failed.")
            return@launchAndShowError
        }
        // 同意がないなら残りの何かは表示しない
        if (!agreed) return@launchAndShowError

        // テーマ告知
        themeDefaultChangedDialog()

        // 通知権限の確認を一度だけ行う
        if (!prefDevice.supressRequestNotificationPermission) {
            prefDevice.supressRequestNotificationPermission = true
            if (!prNotification.checkOrLaunch()) return@launchAndShowError
        }

        // 画面を作成したあと一度だけ行う
        // 画像ビューアから戻ってきたときなどは行わない
        if (!subscriptionUpdaterCalled) {
            subscriptionUpdaterCalled = true
            afterNotificationGranted()
        }
    }
}

suspend fun ActMain.afterNotificationGranted() {
    sideMenuAdapter.filterListItems()

    // Workの掃除
    WorkManager.getInstance(applicationContext).pruneWork()

    // 認証やアクセストークン更新から戻ってきた時に処理を重ねたくない
    delay(2000L)
    if (!accountVerifyMutex.isLocked) {
        // 定期的にendpointを再登録したい
        PushWorker.enqueueRegisterEndpoint(applicationContext, keepAliveMode = true)
    }
}
