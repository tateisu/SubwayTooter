package jp.juggler.subwaytooter.actmain

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.columnviewholder.TabletColumnViewHolder

internal class TabletColumnPagerAdapter(
    private val activity: ActMain,
) : RecyclerView.Adapter<TabletColumnViewHolder>() {

    var columnWidth: Int = 0 // dividerの幅を含まない

    private val appState = activity.appState

    override fun getItemCount(): Int = appState.columnCount

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabletColumnViewHolder =
        TabletColumnViewHolder(activity, parent)

    override fun onBindViewHolder(holder: TabletColumnViewHolder, position: Int) {
        val columnWidth = this.columnWidth
        if (columnWidth > 0) {
            val lp = holder.itemView.layoutParams
            lp.width = columnWidth
            holder.itemView.layoutParams = lp
        }

        holder.bind(appState.column(position)!!, position, appState.columnCount)
    }

    override fun onViewRecycled(holder: TabletColumnViewHolder) {
        super.onViewRecycled(holder)
        holder.onViewRecycled()
    }
}
