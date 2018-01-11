package jp.juggler.subwaytooter

import android.view.View
import android.widget.Button
import android.widget.TextView

import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils
import jp.juggler.subwaytooter.view.MyLinkMovementMethod
import jp.juggler.subwaytooter.view.MyListView

internal class HeaderViewHolderSearchDesc(
	arg_activity : ActMain,
	arg_column : Column,
	parent : MyListView,
	html : String
) : HeaderViewHolderBase(
	arg_activity,
	arg_column,
	arg_activity.layoutInflater.inflate(R.layout.lv_header_search_desc, parent, false)
) {
	
	init {
		
		Utils.scanView(viewRoot) { v ->
			try {
				if(v is Button) {
					// ボタンは太字なので触らない
				} else if(v is TextView) {
					if(activity.timeline_font != null) {
						v.typeface = activity.timeline_font
					}
					if(! activity.timeline_font_size_sp.isNaN()) {
						v.textSize = activity.timeline_font_size_sp
					}
				}
			} catch(ex : Throwable) {
				log.trace(ex)
			}
		}
		
		val sv = DecodeOptions(decodeEmoji = true).decodeHTML(activity, access_info, html)
		
		val tvSearchDesc = viewRoot.findViewById<TextView>(R.id.tvSearchDesc)
		tvSearchDesc.visibility = View.VISIBLE
		tvSearchDesc.movementMethod = MyLinkMovementMethod
		tvSearchDesc.text = sv
	}
	
	override fun showColor() {
		//
	}
	
	override fun bindData(column : Column) {
		//
	}
	
	companion object {
		private val log = LogCategory("HeaderViewHolderSearchDesc")
	}
}
