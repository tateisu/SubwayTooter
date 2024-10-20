package jp.juggler.subwaytooter.util

import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.TextUnit
import jp.juggler.util.data.mayUri

fun CharSequence.toAnnotatedString() = when (this) {
    is AnnotatedString -> this
    else -> AnnotatedString(toString())
}

fun AnnotatedString.Builder.isEmpty() = length == 0
fun AnnotatedString.Builder.isNotEmpty() = length != 0

/**
 * 装飾付き文字列のリストを連結する
 * @receiver 装飾付き文字列のリスト
 * @param separator 文字列と文字列の間に挿入される区切り
 */
fun List<CharSequence>.joinAnnotatedString(
    separator: CharSequence,
): AnnotatedString = AnnotatedString.Builder().also { dst ->
    for (item in this) {
        if (dst.isNotEmpty()) dst.append(separator)
        dst.append(item)
    }
}.toAnnotatedString()

/**
 * クリック可能な装飾付き文字列を作る
 * @receiver 表示文字列
 * @param uri クリックしたら開くUri。nullなら装飾しない
 * @return CharSequence, 実際には Stringまたは AnnotatedString
 */
fun String.annotateUrl(
    url: String?,
    colorLink: Color,
    fontSize: TextUnit = TextUnit.Unspecified,
    opener: (Uri) -> Unit,
): CharSequence = when (val uri = url?.mayUri()) {
    null -> this
    else -> buildAnnotatedString {
        withLink(
            LinkAnnotation.Clickable(
                tag = "uri",
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = colorLink,
                        textDecoration = TextDecoration.Underline,
                        fontSize = fontSize,
                    )
                ),
            ) { opener(uri) }
        ) {
            append(this@annotateUrl)
        }
    }
}
