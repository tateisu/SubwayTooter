package jp.juggler.subwaytooter

import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.Button
import android.widget.TextView
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.scan

internal abstract class ViewHolderHeaderBase(val activity : ActMain, val viewRoot : View) :
	RecyclerView.ViewHolder(viewRoot) {
	
	companion object {
		private val log = LogCategory("HeaderViewHolderBase")
	}
	
	internal lateinit var column : Column
	internal lateinit var access_info : SavedAccount
	
	init {
		viewRoot.scan { v ->
			try {
				if(v is Button) {
					// ボタンは太字なので触らない
				} else if(v is TextView) {
					if(ActMain.timeline_font != null) {
						v.typeface = ActMain.timeline_font
					}
					if(! activity.timeline_font_size_sp.isNaN()) {
						v.textSize = activity.timeline_font_size_sp
					}
				}
			} catch(ex : Throwable) {
				log.trace(ex)
			}
		}
	}
	
	internal open fun bindData(column : Column) {
		this.column = column
		this.access_info = column.access_info
	}
	
	internal abstract fun showColor()
	
	internal abstract fun onViewRecycled()
}
