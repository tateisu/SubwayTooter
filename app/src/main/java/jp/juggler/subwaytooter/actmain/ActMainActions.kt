package jp.juggler.subwaytooter.actmain

import android.text.Spannable
import android.view.View
import android.widget.TextView
import androidx.core.view.GravityCompat
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.pref.PrefI
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.action.openColumnList
import jp.juggler.subwaytooter.action.openPost
import jp.juggler.subwaytooter.api.entity.TootAccountRef
import jp.juggler.subwaytooter.api.entity.TootTag.Companion.findHashtagFromUrl
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.columnviewholder.ColumnViewHolder
import jp.juggler.subwaytooter.columnviewholder.TabletColumnViewHolder
import jp.juggler.subwaytooter.columnviewholder.ViewHolderHeaderBase
import jp.juggler.subwaytooter.columnviewholder.ViewHolderItem
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.itemviewholder.ItemViewHolder
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.util.openCustomTab
import jp.juggler.util.*
import java.util.*

private val log = LogCategory("ActMainActions")

fun ActMain.onBackPressedImpl() {

    // メニューが開いていたら閉じる
    if (drawer.isDrawerOpen(GravityCompat.START)) {
        drawer.closeDrawer(GravityCompat.START)
        return
    }

    // カラムが0個ならアプリを終了する
    if (appState.columnCount == 0) {
        finish()
        return
    }

    // カラム設定が開いているならカラム設定を閉じる
    if (closeColumnSetting()) {
        return
    }

    fun getClosableColumnList(): List<Column> {
        val visibleColumnList = ArrayList<Column>()
        phoneTab({ env ->
            try {
                appState.column(env.pager.currentItem)?.addTo(visibleColumnList)
            } catch (ex: Throwable) {
                log.w(ex)
            }
        }, { env ->
            visibleColumnList.addAll(env.visibleColumns)
        })

        return visibleColumnList.filter { !it.dontClose }
    }

    // カラムが1個以上ある場合は設定に合わせて挙動を変える
    when (PrefI.ipBackButtonAction(pref)) {
        PrefI.BACK_EXIT_APP -> finish()
        PrefI.BACK_OPEN_COLUMN_LIST -> openColumnList()
        PrefI.BACK_CLOSE_COLUMN -> {
            val closeableColumnList = getClosableColumnList()
            when (closeableColumnList.size) {
                0 -> when {
                    PrefB.bpExitAppWhenCloseProtectedColumn(pref) && PrefB.bpDontConfirmBeforeCloseColumn(pref) -> finish()
                    else -> showToast(false, R.string.missing_closeable_column)
                }
                1 -> closeColumn(closeableColumnList.first())
                else -> showToast(false, R.string.cant_close_column_by_back_button_when_multiple_column_shown)
            }
        }
        // ActAppSetting.BACK_ASK_ALWAYS
        else -> {
            val closeableColumnList = getClosableColumnList()
            val dialog = ActionsDialog()
            if (closeableColumnList.size == 1) {
                val column = closeableColumnList.first()
                dialog.addAction(getString(R.string.close_column)) {
                    closeColumn(column, bConfirmed = true)
                }
            }
            dialog.addAction(getString(R.string.open_column_list)) { openColumnList() }
            dialog.addAction(getString(R.string.app_exit)) { finish() }
            dialog.show(this, null)
        }
    }
}

fun ActMain.onClickImpl(v: View) {
    when (v.id) {
        R.id.btnToot -> openPost()
        R.id.btnQuickToot -> performQuickPost(null)
        R.id.btnQuickTootMenu -> toggleQuickPostMenu()
        R.id.btnMenu -> if (!drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.openDrawer(GravityCompat.START)
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
            log.trace(ex)
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
