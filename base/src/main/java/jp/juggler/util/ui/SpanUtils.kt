package jp.juggler.util.ui

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.SpannedString
import android.text.style.StyleSpan
import androidx.annotation.StringRes
import androidx.core.text.buildSpannedString
import jp.juggler.util.ui.getSpannedString

fun CharSequence.bold() = buildSpannedString {
    append(this@bold)
    setSpan(
        StyleSpan(android.graphics.Typeface.BOLD),
        0,
        length,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
    )
}

private val reFormatArgs = """%(\d+)${"\\$"}s""".toRegex()

/**
 * getStringの引数に装飾付き文字列を使えるバージョン
 * ただし書式指示子の詳細は無視される。
 */
fun Context.getSpannedString(
    @StringRes stringId: Int,
    vararg args: Any?,
): SpannedString = buildSpannedString {
    val src = getString(stringId)
    var lastEnd = 0
    fun addText(end: Int) {
        if (end > lastEnd) {
            append(src.substring(lastEnd, end))
        }
    }
    reFormatArgs.findAll(src).forEach { mr ->
        addText(mr.range.first)
        val index = mr.groupValues[1].toInt() - 1
        when (val arg = args.elementAtOrNull(index)) {
            null -> append("(null)")
            is CharSequence -> append(arg)
            else -> append(arg.toString())
        }
        lastEnd = mr.range.last + 1
    }
    addText(src.length)
}

/**
 * joinToStringの装飾文字列版
 */
fun <T : Any?> List<T>.joinToSpannedString(
    separator: CharSequence = ", ",
    prefix: CharSequence = "",
    postfix: CharSequence = "",
    limit: Int = -1,
    truncated: CharSequence = "...",
    transform: ((T) -> CharSequence)? = null,
) = buildSpannedString {
    append(prefix)
    for (i in this@joinToSpannedString.indices) {
        if (limit >= 0 && i >= limit) {
            append(truncated)
            break
        }
        if (i > 0) append(separator)
        val v = this@joinToSpannedString.get(i)
        if (transform == null) {
            when (v) {
                is CharSequence -> append(v)
                else -> append(v.toString())
            }
        } else {
            append(transform(v))
        }
    }
    append(postfix)
}

