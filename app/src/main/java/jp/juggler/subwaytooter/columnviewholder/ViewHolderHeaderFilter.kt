package jp.juggler.subwaytooter.columnviewholder

import android.view.View
import android.view.ViewGroup
import jp.juggler.subwaytooter.ActKeywordFilter
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.databinding.LvHeaderFilterBinding

internal class ViewHolderHeaderFilter(
    override val activity: ActMain,
    parent: ViewGroup,
    val views: LvHeaderFilterBinding =
        LvHeaderFilterBinding.inflate(activity.layoutInflater, parent, false),
) : ViewHolderHeaderBase(views.root), View.OnClickListener {

    init {
        views.root.tag = this
        views.btnCreate.setOnClickListener(this)
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
