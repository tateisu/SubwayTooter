package jp.juggler.subwaytooter

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup

internal class TabletColumnPagerAdapter(
	private val activity : ActMain
) : androidx.recyclerview.widget.RecyclerView.Adapter<TabletColumnViewHolder>() {
	
	private val inflater : LayoutInflater
	private val columnList : List<Column>
	
	var columnWidth :Int = 0 // dividerの幅を含まない
	
	init {
		this.inflater = activity.layoutInflater
		this.columnList = App1.getAppState(activity).column_list
	}
	
	override fun getItemCount() : Int {
		return columnList.size
	}
	
	override fun onCreateViewHolder(parent : ViewGroup, viewType : Int) : TabletColumnViewHolder {
		val viewRoot = inflater.inflate(R.layout.page_column, parent, false)
		
		return TabletColumnViewHolder(activity, viewRoot)
	}
	
	override fun onBindViewHolder(holder : TabletColumnViewHolder, position : Int) {
		val columnWidth = this.columnWidth
		if(columnWidth > 0) {
			val lp = holder.itemView.layoutParams
			lp.width = columnWidth
			holder.itemView.layoutParams = lp
		}
		
		val columnList = this.columnList
		holder.bind(columnList[position], position, columnList.size)
	}
	
	override fun onViewRecycled(holder : TabletColumnViewHolder) {
		super.onViewRecycled(holder)
		holder.onViewRecycled()
	}
}
