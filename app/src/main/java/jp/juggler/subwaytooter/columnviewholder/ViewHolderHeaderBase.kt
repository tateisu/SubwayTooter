package jp.juggler.subwaytooter.columnviewholder

import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.api.entity.TootAccountRef
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.column.ColumnLoadReason
import jp.juggler.subwaytooter.column.startLoading
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.withCaption
import jp.juggler.util.ui.scan

abstract class ViewHolderHeaderBase(viewRoot: View) : RecyclerView.ViewHolder(viewRoot) {

    companion object {
        private val log = LogCategory("HeaderViewHolderBase")
    }

    abstract val activity: ActMain

    internal lateinit var column: Column
    internal lateinit var accessInfo: SavedAccount

    init {
        viewRoot.scan { v ->
            try {
                if (v is Button) {
                    // ボタンは太字なので触らない
                } else if (v is TextView) {
                    v.typeface = ActMain.timelineFont
                    try {
                        activity.timelineFontSizeSp
                            .takeIf { it.isFinite() }
                            ?.let { v.textSize = it }

                        activity.timelineSpacing
                            ?.let { v.setLineSpacing(0f, it) }
                    } catch (ex: NullPointerException) {
                        // 非null型なのになぜかnull例外が出る
                        log.w(ex.withCaption("can't read timelineFontSizeSp, timelineSpacing"))
                    }
                }
            } catch (ex: Throwable) {
                log.e(ex, "can't initialize text styles.")
            }
        }
    }

    internal open fun bindData(column: Column) {
        this.column = column
        this.accessInfo = column.accessInfo
    }

    internal abstract fun showColor()

    internal abstract fun onViewRecycled()

    internal open fun getAccount(): TootAccountRef? = null

    fun reloadBySettingChange(){
        activity.appState.saveColumnList()
        column.startLoading(ColumnLoadReason.SettingChange)
    }
}
