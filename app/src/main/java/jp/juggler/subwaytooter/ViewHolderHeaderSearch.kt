package jp.juggler.subwaytooter

import android.view.View
import android.widget.TextView

import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.view.MyLinkMovementMethod

internal class ViewHolderHeaderSearch(
	arg_activity : ActMain,
	viewRoot : View
) : ViewHolderHeaderBase(arg_activity, viewRoot) {
	
	private val tvSearchDesc : TextView
	
	init {
		this.tvSearchDesc = viewRoot.findViewById(R.id.tvSearchDesc)
		tvSearchDesc.visibility = View.VISIBLE
		tvSearchDesc.movementMethod = MyLinkMovementMethod
	}
	
	override fun showColor() {
	}
	
	override fun bindData(column : Column) {
		super.bindData(column)
		
		val html = column.getHeaderDesc() ?: ""
		val sv = DecodeOptions(activity, access_info,decodeEmoji = true).decodeHTML( html)
		
		tvSearchDesc.text = sv
	}
	
	override fun onViewRecycled() {
	}
	
}
