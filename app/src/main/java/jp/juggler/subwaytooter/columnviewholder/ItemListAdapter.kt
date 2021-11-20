package jp.juggler.subwaytooter.columnviewholder

import android.annotation.SuppressLint
import android.os.Handler
import android.os.SystemClock
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TimelineItem
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.column.HeaderType
import jp.juggler.subwaytooter.column.toAdapterIndex
import jp.juggler.subwaytooter.column.toListIndex
import jp.juggler.subwaytooter.itemviewholder.ItemViewHolder
import jp.juggler.subwaytooter.itemviewholder.bind
import jp.juggler.util.AdapterChange
import jp.juggler.util.AdapterChangeType
import jp.juggler.util.LogCategory

class ItemListAdapter(
    private val activity: ActMain,
    private val column: Column,
    internal val columnVh: ColumnViewHolder,
    private val bSimpleList: Boolean,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private val log = LogCategory("ItemListAdapter")
    }

    private class DiffCallback(
        val oldList: List<TimelineItem>,
        val newList: List<TimelineItem>,
        val biasListIndex: Int,
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int {
            return oldList.size - biasListIndex
        }

        override fun getNewListSize(): Int {
            return newList.size - biasListIndex
        }

        override fun areItemsTheSame(oldAdapterIndex: Int, newAdapterIndex: Int): Boolean {
            val oldListIndex = oldAdapterIndex + biasListIndex
            val newListIndex = newAdapterIndex + biasListIndex

            // header?
            if (oldListIndex < 0 || newListIndex < 0) return oldListIndex == newListIndex

            // compare object address
            return oldList[oldListIndex] === newList[newListIndex]
        }

        override fun areContentsTheSame(oldAdapterIndex: Int, newAdapterIndex: Int): Boolean {
            val oldListIndex = oldAdapterIndex + biasListIndex
            val newListIndex = newAdapterIndex + biasListIndex

            // headerは毎回更新する
            return !(oldListIndex < 0 || newListIndex < 0)
        }
    }

    private var list: ArrayList<TimelineItem>
    private val handler: Handler

    init {
        this.list = ArrayList()
        this.handler = activity.handler
        setHasStableIds(true)
    }

    override fun getItemCount(): Int {
        return column.toAdapterIndex(column.listData.size)
    }

    override fun getItemId(position: Int): Long {
        return try {
            list[column.toListIndex(position)].listViewItemId
        } catch (ignored: Throwable) {
            0L
        }
    }

    override fun getItemViewType(position: Int): Int {
        val headerType = column.type.headerType
        if (headerType == null || position > 0) return 0
        return headerType.viewType
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        when (viewType) {
            0 -> {
                val holder = ItemViewHolder(activity)
                holder.viewRoot.tag = holder
                return ViewHolderItem(holder)
            }

            HeaderType.Profile.viewType -> {
                val viewRoot =
                    activity.layoutInflater.inflate(R.layout.lv_header_profile, parent, false)
                val holder = ViewHolderHeaderProfile(activity, viewRoot)
                viewRoot.tag = holder
                return holder
            }

            HeaderType.Search.viewType -> {
                val viewRoot =
                    activity.layoutInflater.inflate(R.layout.lv_header_search_desc, parent, false)
                val holder = ViewHolderHeaderSearch(activity, viewRoot)
                viewRoot.tag = holder
                return holder
            }

            HeaderType.Instance.viewType -> {
                val viewRoot =
                    activity.layoutInflater.inflate(R.layout.lv_header_instance, parent, false)
                val holder = ViewHolderHeaderInstance(activity, viewRoot)
                viewRoot.tag = holder
                return holder
            }

            HeaderType.Filter.viewType -> {
                val viewRoot =
                    activity.layoutInflater.inflate(R.layout.lv_header_filter, parent, false)
                val holder = ViewHolderHeaderFilter(activity, viewRoot)
                viewRoot.tag = holder
                return holder
            }
            HeaderType.ProfileDirectory.viewType -> {
                val viewRoot =
                    activity.layoutInflater.inflate(R.layout.lv_header_profile_directory, parent, false)
                val holder = ViewHolderHeaderProfileDirectory(activity, viewRoot)
                viewRoot.tag = holder
                return holder
            }
            else -> error("unknown viewType: $viewType")
        }
    }

    fun findHeaderViewHolder(listView: RecyclerView): ViewHolderHeaderBase? {
        return when (column.type.headerType) {
            null -> null
            else -> listView.findViewHolderForAdapterPosition(0) as? ViewHolderHeaderBase
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, adapterIndex: Int) {
        if (holder is ViewHolderItem) {
            val listIndex = column.toListIndex(adapterIndex)
            holder.ivh.bind(this, column, bSimpleList, list[listIndex])
        } else if (holder is ViewHolderHeaderBase) {
            holder.bindData(column)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is ViewHolderItem) {
            holder.ivh.onViewRecycled()
        } else if (holder is ViewHolderHeaderBase) {
            holder.onViewRecycled()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun notifyChange(
        reason: String,
        changeList: List<AdapterChange>? = null,
        reset: Boolean = false,
    ) {

        val timeStart = SystemClock.elapsedRealtime()

        // カラムから最新データをコピーする
        val newList = ArrayList<TimelineItem>()
        newList.ensureCapacity(column.listData.size)
        newList.addAll(column.listData)

        when {
            // 変更リストが指定された場合はヘッダ部分と変更部分を通知する
            changeList != null -> {

                log.d("notifyChange: changeList=${changeList.size},reason=$reason")

                this.list = newList

                // ヘッダは毎回更新する
                // (ヘッダだけ更新するためにカラのchangeListが渡される)
                if (column.type.headerType != null) {
                    notifyItemRangeChanged(0, 1)
                }

                // 変更リストを順番に通知する
                for (c in changeList) {
                    val adapterIndex = column.toAdapterIndex(c.listIndex)
                    log.d("notifyChange: ChangeType=${c.type} offset=$adapterIndex,count=${c.count}")
                    when (c.type) {
                        AdapterChangeType.RangeInsert -> notifyItemRangeInserted(adapterIndex, c.count)
                        AdapterChangeType.RangeRemove -> notifyItemRangeRemoved(adapterIndex, c.count)
                        AdapterChangeType.RangeChange -> notifyItemRangeChanged(adapterIndex, c.count)
                    }
                }
            }

            reset -> {
                log.d("notifyChange: DataSetChanged! reason=$reason")
                this.list = newList
                notifyDataSetChanged()
            }

            else -> {

                val diffResult = DiffUtil.calculateDiff(
                    DiffCallback(
                        oldList = this.list, // 比較対象の古いデータ
                        newList = newList,
                        biasListIndex = column.toListIndex(0)
                    ),
                    false // ログを見た感じ、移動なんてなかった
                )
                val time = SystemClock.elapsedRealtime() - timeStart
                log.d("notifyChange: size=${newList.size},time=${time}ms,reason=$reason")

                this.list = newList
                diffResult.dispatchUpdatesTo(object : ListUpdateCallback {

                    override fun onInserted(position: Int, count: Int) {
                        log.d("notifyChange: notifyItemRangeInserted offset=$position,count=$count")
                        notifyItemRangeInserted(position, count)
                    }

                    override fun onRemoved(position: Int, count: Int) {
                        log.d("notifyChange: notifyItemRangeRemoved offset=$position,count=$count")
                        notifyItemRangeRemoved(position, count)
                    }

                    override fun onChanged(position: Int, count: Int, payload: Any?) {
                        log.d("notifyChange: notifyItemRangeChanged offset=$position,count=$count")
                        notifyItemRangeChanged(position, count, payload)
                    }

                    override fun onMoved(fromPosition: Int, toPosition: Int) {
                        log.d("notifyChange: notifyItemMoved from=$fromPosition,to=$toPosition")
                        notifyItemMoved(fromPosition, toPosition)
                    }
                })
            }
        }

        // diffを取る部分をワーカースレッドで実行したいが、直後にスクロール位置の処理があるので差支えがある…
    }
}
