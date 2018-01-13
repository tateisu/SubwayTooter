package jp.juggler.subwaytooter

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup

internal class TabletColumnPagerAdapter(
	private val activity : ActMain
) : RecyclerView.Adapter<TabletColumnViewHolder>() {
	
	private val inflater : LayoutInflater
	private val columnList : List<Column>
	
	var columnWidth :Int = 0
	
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
