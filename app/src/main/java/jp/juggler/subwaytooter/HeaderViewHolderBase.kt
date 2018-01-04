package jp.juggler.subwaytooter

import android.view.View
import android.widget.Button
import android.widget.TextView

import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils

internal abstract class HeaderViewHolderBase(val activity : ActMain, val column : Column, val viewRoot : View) {
	
	companion object {
		
		private val log = LogCategory("HeaderViewHolderBase")
	}
	
	val access_info : SavedAccount
	
	internal abstract fun showColor()
	
	internal abstract fun bindData(column : Column)
	
	init {
		this.access_info = column.access_info
		
		//FIXME これ必要？ viewRoot.tag = this
		
		if(activity.timeline_font != null) {
			Utils.scanView(viewRoot) { v ->
				try {
					if(v is Button) {
						// ボタンは太字なので触らない
					} else if(v is TextView) {
						v.typeface = activity.timeline_font
					}
				} catch(ex : Throwable) {
					log.trace(ex)
				}
			}
		}
		
	}
	

}
