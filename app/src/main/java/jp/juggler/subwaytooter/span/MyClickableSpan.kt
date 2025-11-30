package jp.juggler.subwaytooter.span

import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View
import jp.juggler.subwaytooter.api.entity.TootMention
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.util.data.cast
import jp.juggler.util.data.notZero
import jp.juggler.util.ui.activity

interface MyClickableSpanHandler {
    fun onMyClickableSpanClicked(viewClicked: View, span: MyClickableSpan)
}

class LinkInfo(
    var url: String,
    var ac: AcctColor? = null,
    var tag: Any? = null,
    var caption: CharSequence = "",
    var mention: TootMention? = null,
) {
    val text: String
        get() = caption.toString()
}

class MyClickableSpan(val linkInfo: LinkInfo) : ClickableSpan() {

    companion object {
        var defaultLinkColor: Int = 0
        var showLinkUnderline = true
    }

    private val colorFg: Int
    val colorBg: Int

    init {
        val ac = linkInfo.ac
        if (ac != null) {
            this.colorFg = ac.colorFg
            this.colorBg = ac.colorBg
        } else {
            this.colorFg = 0
            this.colorBg = 0
        }
    }

    override fun onClick(view: View) {
        view.activity
            ?.cast<MyClickableSpanHandler>()
            ?.onMyClickableSpanClicked(view, this)
    }

    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds)
        if (colorBg != 0) ds.bgColor = colorBg

        ds.color = colorFg.notZero() ?: defaultLinkColor
        ds.isUnderlineText = showLinkUnderline
    }
}
