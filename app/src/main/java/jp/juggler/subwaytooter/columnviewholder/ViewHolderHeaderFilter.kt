package jp.juggler.subwaytooter.columnviewholder

import android.view.View
import jp.juggler.subwaytooter.ActKeywordFilter
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R

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
