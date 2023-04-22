package jp.juggler.subwaytooter.actpost

import android.content.Intent
import android.net.Uri
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.EditText
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.ActPost
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.databinding.DlgPluginMissingBinding
import jp.juggler.util.log.LogCategory
import jp.juggler.util.ui.attrColor

private val log = LogCategory("ActPostMushroom")

fun ActPost.resetMushroom() {
    states.mushroomInput = 0
    states.mushroomStart = 0
    states.mushroomEnd = 0
}

fun ActPost.openPluginList() {
    val url = "https://github.com/tateisu/SubwayTooter/wiki/Simeji-Mushroom-Plugins"
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

fun ActPost.showRecommendedPlugin(@StringRes titleId: Int) {
    val linkCaption = getString(R.string.plugin_app_intro)
    val linkSpan = object : ClickableSpan() {
        override fun onClick(view: View) = openPluginList()
        override fun updateDrawState(ds: TextPaint) {
            super.updateDrawState(ds)
            ds.color = attrColor(R.attr.colorLink)
        }
    }

    val views = DlgPluginMissingBinding.inflate(layoutInflater)
    views.tvText.movementMethod = LinkMovementMethod.getInstance()
    views.tvText.text = SpannableStringBuilder().apply {
        val spanStart = length
        append(linkCaption)
        val spanEnd = length
        setSpan(linkSpan, spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    AlertDialog.Builder(this).apply {
        setTitle(titleId)
        setView(views.root)
        setCancelable(true)
        setPositiveButton(R.string.ok, null)
    }.show()
}

fun ActPost.openMushroom() {
    try {
        var text: String? = null
        when {
            views.etContentWarning.hasFocus() -> {
                states.mushroomInput = 1
                text = prepareMushroomText(views.etContentWarning)
            }

            views.etContent.hasFocus() -> {
                states.mushroomInput = 0
                text = prepareMushroomText(views.etContent)
            }

            else -> for (i in 0..3) {
                if (etChoices[i].hasFocus()) {
                    states.mushroomInput = i + 2
                    text = prepareMushroomText(etChoices[i])
                }
            }
        }
        if (text == null) {
            states.mushroomInput = 0
            text = prepareMushroomText(views.etContent)
        }

        val intent = Intent("com.adamrocker.android.simeji.ACTION_INTERCEPT")
        intent.addCategory("com.adamrocker.android.simeji.REPLACE")
        intent.putExtra("replace_key", text)

        // Create intent to show chooser
        val chooser = Intent.createChooser(intent, getString(R.string.select_plugin))

        // Verify the intent will resolve to at least one activity
        if (intent.resolveActivity(packageManager) == null) {
            showRecommendedPlugin(R.string.plugin_not_installed)
            return
        }

        arMushroom.launch(chooser)
    } catch (ex: Throwable) {
        log.e(ex, "openMushroom failed.")
        showRecommendedPlugin(R.string.plugin_not_installed)
    }
}

fun ActPost.prepareMushroomText(et: EditText): String {
    states.mushroomStart = et.selectionStart
    states.mushroomEnd = et.selectionEnd
    return when {
        states.mushroomStart >= states.mushroomEnd -> ""
        else -> et.text.toString().substring(states.mushroomStart, states.mushroomEnd)
    }
}

fun ActPost.applyMushroomText(et: EditText, text: String) {
    val src = et.text.toString()
    if (states.mushroomStart > src.length) states.mushroomStart = src.length
    if (states.mushroomEnd > src.length) states.mushroomEnd = src.length

    val sb = StringBuilder()
    sb.append(src.substring(0, states.mushroomStart))
    // int new_sel_start = sb.length();
    sb.append(text)
    val newSelEnd = sb.length
    sb.append(src.substring(states.mushroomEnd))
    et.setText(sb)
    et.setSelection(newSelEnd, newSelEnd)
}
