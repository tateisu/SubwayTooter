package jp.juggler.subwaytooter.util

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.view.Gravity
import android.view.View
import android.widget.CheckedTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.view.MyEditText
import jp.juggler.util.*
import java.util.*

@SuppressLint("InflateParams")
internal class PopupAutoCompleteAcct(
    val activity: Activity,
    private val etContent: EditText,
    private val formRoot: View,
    private val bMainScreen: Boolean,
) {
    companion object {
        internal val log = LogCategory("PopupAutoCompleteAcct")

        // 絵文字ショートコードにマッチするとても雑な正規表現
        private val reLastShortCode = """:([^\s:]+):\z""".asciiPattern()
    }

    private val acctPopup: PopupWindow
    private val llItems: LinearLayout
    val density: Float
    private val popupWidth: Int
    val handler: Handler

    private var popupRows: Int = 0

    val isShowing: Boolean
        get() = acctPopup.isShowing

    fun dismiss() {
        try {
            acctPopup.dismiss()
        } catch (ex: Throwable) {
            log.e(ex, "acctPopup.dismiss failed.")
        }
    }

    init {
        this.density = activity.resources.displayMetrics.density
        this.handler = App1.getAppState(activity, "PopupAutoCompleteAcct.ctor").handler

        popupWidth = (0.5f + 240f * density).toInt()

        val viewRoot = activity.layoutInflater.inflate(R.layout.acct_complete_popup, null, false)
        llItems = viewRoot.findViewById(R.id.llItems)
        //
        acctPopup = PopupWindow(activity)
        acctPopup.setBackgroundDrawable(
            ContextCompat.getDrawable(
                activity,
                R.drawable.acct_popup_bg
            )
        )
        acctPopup.contentView = viewRoot
        acctPopup.isTouchable = true
    }

    fun setList(
        et: MyEditText,
        selStart: Int,
        selEnd: Int,
        acctList: ArrayList<CharSequence>?,
        pickerCaption: String?,
        pickerCallback: Runnable?,
    ) {

        llItems.removeAllViews()

        popupRows = 0

        run {
            val v = activity.layoutInflater
                .inflate(R.layout.lv_spinner_dropdown, llItems, false) as CheckedTextView
            v.setTextColor(activity.attrColor(android.R.attr.textColorPrimary))
            v.setText(R.string.close)
            v.setOnClickListener { acctPopup.dismiss() }
            llItems.addView(v)
            ++popupRows
        }

        if (pickerCaption != null && pickerCallback != null) {
            val v = activity.layoutInflater
                .inflate(R.layout.lv_spinner_dropdown, llItems, false) as CheckedTextView
            v.setTextColor(activity.attrColor(android.R.attr.textColorPrimary))
            v.text = pickerCaption
            v.setOnClickListener {
                acctPopup.dismiss()
                pickerCallback.run()
            }
            llItems.addView(v)
            ++popupRows
        }

        acctList?.forEach { acct ->
            val v = activity.layoutInflater
                .inflate(R.layout.lv_spinner_dropdown, llItems, false) as CheckedTextView
            v.setTextColor(activity.attrColor(android.R.attr.textColorPrimary))
            v.text = acct
            if (acct is Spannable) {
                NetworkEmojiInvalidator(handler, v).register(acct)
            }
            v.setOnClickListener { handleItemClick(et, selStart, selEnd, acct) }
            llItems.addView(v)
            ++popupRows
        }

        updatePosition()
    }

    private fun handleItemClick(et: EditText, selStart: Int, selEnd: Int, acct: CharSequence) {
        val src = et.text ?: ""
        val srcLength = src.length
        val start = selStart.clip(0, srcLength)
        val end = selEnd.clip(0, srcLength)

        val sb = SpannableStringBuilder()
        sb.append(src.subSequence(0, start))
        val remain = src.subSequence(end, srcLength)

        when (acct[0]) {
            '#' -> {
                // #hashtag
                // 直後に空白を付与する
                sb.append(acct).append(" ")
            }
            '@' -> if (acct.any { it >= 0x80.toChar() }) {
                // @user@host IDNドメインを含む
                // 直後に空白を付与する
                sb.append("@" + Acct.parse(acct.toString().substring(1)).ascii).append(" ")
            } else {
                // @user@host
                // 直後に空白を付与する
                sb.append(acct).append(" ")
            }
            ' ' -> {
                // 絵文字ショートコード(カスタム絵文字 or twemoji)
                val separator = EmojiDecoder.customEmojiSeparator()
                if (!EmojiDecoder.canStartShortCode(sb, start)) sb.append(separator)
                sb.append(findShortCode(acct.toString()))
                // セパレータにZWSPを使う設定なら、補完した次の位置にもZWSPを追加する。連続して入力補完できるようになる。
                if (separator != ' ') sb.append(separator)
            }
            else -> {
                // 絵文字(noto unicode emoji)
                val delm = acct.indexOf(' ')
                if (delm != -1) {
                    sb.append(acct.subSequence(0, delm))
                }
            }
        }
        val newSelection = sb.length
        sb.append(remain)

        et.text = sb
        et.setSelection(newSelection)
        acctPopup.dismiss()
    }

    private fun findShortCode(acct: String): String {
        val m = reLastShortCode.matcher(acct)
        if (m.find()) return m.groupEx(0)!!
        return acct
    }

    fun updatePosition() {

        val location = IntArray(2)
        etContent.getLocationOnScreen(location)
        val textTop = location[1]

        var popupTop: Int
        var popupHeight: Int

        if (bMainScreen) {
            val popupBottom = textTop + etContent.totalPaddingTop - etContent.scrollY
            val max = popupBottom - (0.5f + 48f * 1f * density).toInt()
            val min = (0.5f + 48f * 2f * density).toInt()
            popupHeight = (0.5f + 48f * popupRows.toFloat() * density).toInt()
            if (popupHeight < min) popupHeight = min
            if (popupHeight > max) popupHeight = max
            popupTop = popupBottom - popupHeight
        } else {
            formRoot.getLocationOnScreen(location)
            val formTop = location[1]
            val formBottom = location[1] + formRoot.height

            val layout = etContent.layout

            popupTop = try {
                (textTop + etContent.totalPaddingTop + layout.getLineBottom(layout.lineCount - 1)) - etContent.scrollY
            } catch (ignored: Throwable) {
                // java.lang.IllegalStateException
                0
            }

            if (popupTop < formTop) popupTop = formTop

            popupHeight = formBottom - popupTop

            val min = (0.5f + 48f * 2f * density).toInt()
            val max = (0.5f + 48f * popupRows.toFloat() * density).toInt()

            if (popupHeight < min) popupHeight = min
            if (popupHeight > max) popupHeight = max
        }

        if (acctPopup.isShowing) {
            acctPopup.update(0, popupTop, popupWidth, popupHeight)
        } else {
            acctPopup.width = popupWidth
            acctPopup.height = popupHeight
            acctPopup.showAtLocation(
                etContent, Gravity.CENTER_HORIZONTAL or Gravity.TOP, 0, popupTop
            )
        }
    }
}
