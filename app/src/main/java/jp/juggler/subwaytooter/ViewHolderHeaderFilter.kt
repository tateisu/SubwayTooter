package jp.juggler.subwaytooter

import android.view.View

internal class ViewHolderHeaderFilter(
	arg_activity : ActMain,
	viewRoot : View
) : ViewHolderHeaderBase(arg_activity, viewRoot), View.OnClickListener {
	
	
	init {
		viewRoot.findViewById<View>(R.id.btnCreate).setOnClickListener(this)
	}
	
	override fun showColor() {
	}
	
//	override fun bindData(column : Column) {
//		super.bindData(column)
//	}
	
	override fun onViewRecycled() {
	}
	
	override fun onClick(v : View?) {
		ActKeywordFilter.open(activity,column.access_info)
	}
	
}
