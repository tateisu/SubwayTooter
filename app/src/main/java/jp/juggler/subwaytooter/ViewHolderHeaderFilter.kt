package jp.juggler.subwaytooter

import android.view.View

internal class ViewHolderHeaderFilter(
    activityArg: ActMain,
    viewRoot: View,
) : ViewHolderHeaderBase(activityArg, viewRoot), View.OnClickListener {

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

    override fun onClick(v: View?) {
        ActKeywordFilter.open(activity, column.accessInfo)
    }
}
