package jp.juggler.subwaytooter.ui.ossLicense

import android.net.Uri
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import jp.juggler.subwaytooter.util.StColorScheme
import jp.juggler.subwaytooter.util.annotateUrl
import jp.juggler.subwaytooter.util.isNotEmpty
import jp.juggler.subwaytooter.util.joinAnnotatedString
import jp.juggler.subwaytooter.util.toAnnotatedString
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.notEmpty
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 依存ライブラリの装飾付きテキスト
 */
class LibText(
    val nameBig: AnnotatedString,
    val nameSmall: AnnotatedString?,
    val desc: AnnotatedString,
) {
    val nameSort = nameBig.toString().lowercase()
}

/**
 * レシーバの文字列がカラでなければdstに追加する
 * @receiver 追加先
 * @param text 装飾付き文字列
 * @param prefix nullや空でなければtextより先に追加される
 */
private fun AnnotatedString.Builder.appendLine(
    text: CharSequence?,
    prefix: CharSequence? = null,
) {
    if (text.isNullOrBlank()) return
    if (isNotEmpty()) append("\n")
    if (!prefix.isNullOrBlank()) append(prefix)
    append(text)
}

/**
 * ライブラリ情報をLibTextに変換する
 */
fun parseLibText(
    lib: JsonObject,
    licenses: Map<String?, JsonObject>,
    stColorScheme: StColorScheme,
    linkOpener: (Uri) -> Unit,
): LibText {
    val colorLink = stColorScheme.colorTextLink

    val webSite = lib.string("website")?.toHttpUrlOrNull()?.toString()
    val name = lib.string("name")?.notEmpty()
    val id = lib.string("id")?.notEmpty()

    val nameBig: CharSequence
    val nameSmall: CharSequence?
    if (name.isNullOrBlank()) {
        // nameがない場合はnameBigはidを大きく表示する
        nameBig = (id ?: "(no name, no id)").annotateUrl(webSite, colorLink, opener = linkOpener)
        nameSmall = null
    } else {
        nameBig = name.annotateUrl(webSite, colorLink, opener = linkOpener)
        nameSmall = id
        // idがない場合はnameSmallはnullとなる
    }

    val licenseText = lib.jsonArray("licenses")?.stringList()
        ?.asSequence()
        ?.mapNotNull { licenses[it] }
        ?.map {
            val uri =
                it.jsonArray("urls")?.stringList()?.firstOrNull()?.toHttpUrlOrNull()?.toString()
            it.string("name")!!.annotateUrl(uri, colorLink, opener = linkOpener)
        }
        ?.toList()?.joinAnnotatedString(AnnotatedString(", "))

    val devNames = lib.jsonArray("developers")?.objectList()
        ?.map { it.string("name") }
        ?.filter { !it.isNullOrBlank() }
        ?.joinToString(", ")

    val libDesc = buildAnnotatedString {
        appendLine(lib.string("description")?.takeIf { it != name })
        appendLine(devNames, "- Developers: ")
        appendLine(licenseText, "- License: ")
    }
    return LibText(
        nameBig = nameBig.toAnnotatedString(),
        nameSmall = nameSmall?.toAnnotatedString(),
        desc = libDesc.toAnnotatedString(),
    )
}
