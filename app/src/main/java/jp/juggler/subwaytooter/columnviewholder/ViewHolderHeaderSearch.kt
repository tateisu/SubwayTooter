package jp.juggler.subwaytooter.columnviewholder

import android.view.View
import android.view.ViewGroup
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.column.getContentColor
import jp.juggler.subwaytooter.column.getHeaderDesc
import jp.juggler.subwaytooter.databinding.LvHeaderSearchDescBinding
import jp.juggler.subwaytooter.util.emojiSizeMode
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.view.MyLinkMovementMethod
import org.jetbrains.anko.textColor

internal class ViewHolderHeaderSearch(
    override val activity: ActMain,
    parent: ViewGroup,
    val views: LvHeaderSearchDescBinding =
        LvHeaderSearchDescBinding.inflate(activity.layoutInflater, parent, false),
) : ViewHolderHeaderBase(views.root) {

    init {
        views.root.tag = this
        views.run {
            tvSearchDesc.visibility = View.VISIBLE
            tvSearchDesc.movementMethod = MyLinkMovementMethod
        }
    }

    override fun showColor() {
    }

    override fun bindData(column: Column) {
        super.bindData(column)
        views.run {
            tvSearchDesc.textColor = column.getContentColor()
            tvSearchDesc.text = DecodeOptions(
                activity, accessInfo, decodeEmoji = true,
                authorDomain = accessInfo,
                emojiSizeMode =  accessInfo.emojiSizeMode(),
            ).decodeHTML(column.getHeaderDesc())
        }
    }

    override fun onViewRecycled() {
    }
}
