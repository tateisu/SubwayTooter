package jp.juggler.subwaytooter

import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import jp.juggler.subwaytooter.api.entity.TootAccountRef
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.LogCategory
import jp.juggler.util.scan

abstract class ViewHolderHeaderBase(
    val activity: ActMain,
    val viewRoot: View
) : RecyclerView.ViewHolder(viewRoot) {

    companion object {
        private val log = LogCategory("HeaderViewHolderBase")
    }

    internal lateinit var column: Column
    internal lateinit var accessInfo: SavedAccount

    init {
        viewRoot.scan { v ->
            try {
                if (v is Button) {
                    // ボタンは太字なので触らない
                } else if (v is TextView) {
                    v.typeface = ActMain.timelineFont
                    if (!activity.timelineFontSizeSp.isNaN()) {
                        v.textSize = activity.timelineFontSizeSp
                    }

                    val fv = activity.timelineSpacing
                    if (fv != null) v.setLineSpacing(0f, fv)
                }
            } catch (ex: Throwable) {
                log.trace(ex)
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
}
