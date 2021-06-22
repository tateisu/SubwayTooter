package jp.juggler.subwaytooter

import android.content.res.ColorStateList
import android.text.TextUtils
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import jp.juggler.util.isEnabledAlpha
import jp.juggler.util.notZero
import jp.juggler.util.vg
import org.jetbrains.anko.textColor

// カラムヘッダなど、負荷が低い部分の表示更新
fun ColumnViewHolder.showColumnHeader() {
    activity.handler.removeCallbacks(procShowColumnHeader)
    activity.handler.postDelayed(procShowColumnHeader, 50L)
}

fun ColumnViewHolder.showColumnStatus() {
    activity.handler.removeCallbacks(procShowColumnStatus)
    activity.handler.postDelayed(procShowColumnStatus, 50L)
}

fun ColumnViewHolder.showColumnColor() {
    val column = this.column
    if (column == null || column.isDispose.get()) return

    // カラムヘッダ背景
    column.setHeaderBackground(llColumnHeader)

    // カラムヘッダ文字色(A)
    var c = column.getHeaderNameColor()
    val csl = ColorStateList.valueOf(c)
    tvColumnName.textColor = c
    ivColumnIcon.imageTintList = csl
    btnAnnouncements.imageTintList = csl
    btnColumnSetting.imageTintList = csl
    btnColumnReload.imageTintList = csl
    btnColumnClose.imageTintList = csl

    // カラムヘッダ文字色(B)
    c = column.getHeaderPageNumberColor()
    tvColumnIndex.textColor = c
    tvColumnStatus.textColor = c

    // カラム内部の背景色
    flColumnBackground.setBackgroundColor(
        column.columnBgColor.notZero()
            ?: Column.defaultColorContentBg
    )

    // カラム内部の背景画像
    ivColumnBackgroundImage.alpha = column.columnBgImageAlpha
    loadBackgroundImage(ivColumnBackgroundImage, column.columnBgImage)

    // エラー表示
    tvLoading.textColor = column.getContentColor()

    statusAdapter?.findHeaderViewHolder(listView)?.showColor()

    // カラム色を変更したらクイックフィルタの色も変わる場合がある
    showQuickFilter()

    showAnnouncements(force = false)
}

fun ColumnViewHolder.showError(message: String) {
    hideRefreshError()

    refreshLayout.isRefreshing = false
    refreshLayout.visibility = View.GONE

    llLoading.visibility = View.VISIBLE
    tvLoading.text = message
    btnConfirmMail.vg(column?.accessInfo?.isConfirmed == false)
}

fun ColumnViewHolder.showColumnCloseButton() {
    column?.dontClose?.let { btnColumnClose.isEnabledAlpha = !it }
}

internal fun ColumnViewHolder.showContent(
    reason: String,
    changeList: List<AdapterChange>? = null,
    reset: Boolean = false
) {
    // クラッシュレポートにadapterとリストデータの状態不整合が多かったので、
    // とりあえずリストデータ変更の通知だけは最優先で行っておく
    try {
        statusAdapter?.notifyChange(reason, changeList, reset)
    } catch (ex: Throwable) {
        ColumnViewHolder.log.trace(ex)
    }

    showColumnHeader()
    showColumnStatus()

    val column = this.column
    if (column == null || column.isDispose.get()) {
        showError("column was disposed.")
        return
    }

    if (!column.bFirstInitialized) {
        showError("initializing")
        return
    }

    if (column.bInitialLoading) {
        var message: String? = column.taskProgress
        if (message == null) message = "loading?"
        showError(message)
        return
    }

    val initialLoadingError = column.mInitialLoadingError
    if (initialLoadingError.isNotEmpty()) {
        showError(initialLoadingError)
        return
    }

    val statusAdapter = this.statusAdapter

    if (statusAdapter == null || statusAdapter.itemCount == 0) {
        showError(activity.getString(R.string.list_empty))
        return
    }

    llLoading.visibility = View.GONE

    refreshLayout.visibility = View.VISIBLE

    statusAdapter.findHeaderViewHolder(listView)?.bindData(column)

    if (column.bRefreshLoading) {
        hideRefreshError()
    } else {
        refreshLayout.isRefreshing = false
        showRefreshError()
    }
    procRestoreScrollPosition.run()
}

fun ColumnViewHolder.showColumnSetting(show: Boolean): Boolean {
    llColumnSetting.vg(show)
    llColumnHeader.invalidate()
    return show
}

fun ColumnViewHolder.showRefreshError() {
    val column = column
    if (column == null) {
        hideRefreshError()
        return
    }

    val refreshError = column.mRefreshLoadingError
    //		val refreshErrorTime = column.mRefreshLoadingErrorTime
    if (refreshError.isEmpty()) {
        hideRefreshError()
        return
    }

    tvRefreshError.text = refreshError
    when (column.mRefreshLoadingErrorPopupState) {
        // initially expanded
        0 -> {
            tvRefreshError.isSingleLine = false
            tvRefreshError.ellipsize = null
        }

        // tap to minimize
        1 -> {
            tvRefreshError.isSingleLine = true
            tvRefreshError.ellipsize = TextUtils.TruncateAt.END
        }
    }

    if (!bRefreshErrorWillShown) {
        bRefreshErrorWillShown = true
        if (llRefreshError.visibility == View.GONE) {
            llRefreshError.visibility = View.VISIBLE
            val aa = AlphaAnimation(0f, 1f)
            aa.duration = 666L
            llRefreshError.clearAnimation()
            llRefreshError.startAnimation(aa)
        }
    }
}

fun ColumnViewHolder.hideRefreshError() {
    if (!bRefreshErrorWillShown) return
    bRefreshErrorWillShown = false
    if (llRefreshError.visibility == View.GONE) return
    val aa = AlphaAnimation(1f, 0f)
    aa.duration = 666L
    aa.setAnimationListener(object : Animation.AnimationListener {
        override fun onAnimationRepeat(animation: Animation?) {
        }

        override fun onAnimationStart(animation: Animation?) {
        }

        override fun onAnimationEnd(animation: Animation?) {
            if (!bRefreshErrorWillShown) llRefreshError.visibility = View.GONE
        }
    })
    llRefreshError.clearAnimation()
    llRefreshError.startAnimation(aa)
}
