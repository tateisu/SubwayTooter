package jp.juggler.subwaytooter.actpost

import android.annotation.SuppressLint
import android.content.Intent
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.RawRes
import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.ActPost
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.util.LogCategory
import jp.juggler.util.decodeUTF8
import jp.juggler.util.loadRawResource

private val log = LogCategory("ActPostMushroom")

fun ActPost.resetMushroom() {
    states.mushroomInput = 0
    states.mushroomStart = 0
    states.mushroomEnd = 0
}

@SuppressLint("InflateParams")
fun ActPost.showRecommendedPlugin(title: String?) {

    @RawRes val resId = when (getString(R.string.language_code)) {
        "ja" -> R.raw.recommended_plugin_ja
        "fr" -> R.raw.recommended_plugin_fr
        else -> R.raw.recommended_plugin_en
    }

    this.loadRawResource(resId).let { data ->
        val text = data.decodeUTF8()
        val viewRoot = layoutInflater.inflate(R.layout.dlg_plugin_missing, null, false)

        val tvText = viewRoot.findViewById<TextView>(R.id.tvText)

        val sv = DecodeOptions(this, linkHelper = LinkHelper.unknown).decodeHTML(text)

        tvText.text = sv
        tvText.movementMethod = LinkMovementMethod.getInstance()

        val tvTitle = viewRoot.findViewById<TextView>(R.id.tvTitle)
        if (title?.isEmpty() != false) {
            tvTitle.visibility = View.GONE
        } else {
            tvTitle.text = title
        }

        AlertDialog.Builder(this)
            .setView(viewRoot)
            .setCancelable(true)
            .setNeutralButton(R.string.close, null)
            .show()
    }
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
            showRecommendedPlugin(getString(R.string.plugin_not_installed))
            return
        }

        arMushroom.launch(chooser)
    } catch (ex: Throwable) {
        log.e(ex, "openMushroom failed.")
        showRecommendedPlugin(getString(R.string.plugin_not_installed))
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
