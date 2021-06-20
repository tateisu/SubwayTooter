package jp.juggler.subwaytooter

import android.view.View
import android.widget.TextView

import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.view.MyLinkMovementMethod
import org.jetbrains.anko.textColor

internal class ViewHolderHeaderSearch(
    activityArg: ActMain,
    viewRoot: View,
) : ViewHolderHeaderBase(activityArg, viewRoot) {

    private val tvSearchDesc: TextView

    init {
        this.tvSearchDesc = viewRoot.findViewById(R.id.tvSearchDesc)
        tvSearchDesc.visibility = View.VISIBLE
        tvSearchDesc.movementMethod = MyLinkMovementMethod
    }

    override fun showColor() {
    }

    override fun bindData(column: Column) {
        super.bindData(column)

        tvSearchDesc.textColor = column.getContentColor()
        tvSearchDesc.text = DecodeOptions(
            activity, accessInfo, decodeEmoji = true,
            mentionDefaultHostDomain = accessInfo
        )
            .decodeHTML(column.getHeaderDesc())
    }

    override fun onViewRecycled() {
    }
}
