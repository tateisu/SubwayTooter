package jp.juggler.subwaytooter.ui.languageFilter

import android.content.Context
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.util.data.decodeJsonArray
import jp.juggler.util.data.loadRawResource
import jp.juggler.util.data.notBlank
import jp.juggler.util.log.LogCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import ru.gildor.coroutines.okhttp.await
import java.util.Locale

private val log = LogCategory("GetLanguageNames")

data class LanguageInfo(
    // code,name from https://mastodon.social/api/v1/instance/languages
    val code: String,
    val name: String,
    // displayName from locale
    var displayName: String = "",
) {
    val locales = HashSet<Locale>()
}

fun Context.langDesc(code: String, nameMap: Map<String, LanguageInfo>) =
    nameMap[code]?.displayName ?: getString(R.string.custom)

suspend fun loadMastodonLanguages(
    apiHost: Host,
    accessToken: String? = null,
) = withContext(Dispatchers.IO) {
    val request = Request.Builder().apply {
        url("https://${apiHost.ascii}/api/v1/instance/languages")
        get()
        accessToken?.notBlank()?.let {
            header("Authorization", "Bearer $it")
        }
    }.build()
    val response = App1.ok_http_client.newCall(request).await()
    if (!response.isSuccessful) error("HTTP ${response.code}")
    response.body.string()
}

private val languageStringIds = buildMap {
    put("cnd", R.string.language_name_montenegrin)
}

fun mergeLanguageName(
    context: Context,
    jsonString: String,
) = HashMap<String, LanguageInfo>().also { dst ->
    // read from mastodon languages
    for (it in jsonString.decodeJsonArray().objectList()) {
        val code = it.string("code")?.notBlank() ?: error("missing item.code")
        val name = it.string("name")?.notBlank() ?: error("missing item.name")
        dst[code] = LanguageInfo(code = code, name = name)
    }

    // using Locale.forLanguageTag to find Java Locale
    for (item in dst.values) {
        val locale = Locale.forLanguageTag(item.code)
        log.i("code=${item.code} locale=$locale lang=${locale.language} displayName=${locale.displayName}")
        // 2018年に、ISO 639-2およびISO 639-3にモンテネグロ語の言語コード(cnr)が追加された
        // Android Javaには定義がないが、この場合にdisplayNameは言語コードと同じ値になる
        if (locale.displayName == item.code || locale.displayName.isNullOrBlank()) continue
        item.locales.add(locale)
    }

    // 今度はJavaのロケールを順に処理する
    loop@ for (locale in Locale.getAvailableLocales().sortedBy { it.toLanguageTag() }) {
        // 言語名が空やundはスキップ
        if (locale.language.isEmpty() || locale.language == "und") continue

        // 既に対応するmastodon言語コードが判明している
        if (dst.values.any { c -> c.locales.any { it == locale } }) continue

        // language名またはlanguage tagで検索
        for (code in arrayOf(locale.language, locale.toLanguageTag())) {
            val item = dst.values.find { it.code.equals(code, ignoreCase = true) }
            if (item != null) {
                item.locales.add(locale)
                continue@loop
            }
        }
        // log.w("Java locale not match to mastodon lang list. ${locale.displayLanguage}(${locale.language}, ${locale.toLanguageTag()})")
    }

    // 互換性があるなかで最も言語タグが短いロケールの表示名を使う
    // fallback 1: 言語コード別の文字列リソースを参照する
    // fallback 2: mastodon api のフォールバック用のname
    // fallback 3: "?"
    for (item in dst.values) {
        val locale = item.locales.sortedBy { it.toLanguageTag() }.firstOrNull()
        item.displayName = locale?.displayName?.notBlank()
            ?: languageStringIds[item.code]?.let { context.getString(it) }
                    ?: item.name.notBlank()
                    ?: "?"
    }

    return dst
}

/**
 * - Mastodonの言語リストをロードする。なければフォールバックのrawリソースを読む。
 * - 言語リストを
 * 現在の表示言語にあう言語コード→名前マップを返す
 */
fun getLanguageNames(
    context: Context,
    jsonString: String?,
): HashMap<String, LanguageInfo> {
    if (jsonString?.contains("{") == true) {
        try {
            val map = mergeLanguageName(
                context,
                jsonString,
            )
            if (map.isEmpty()) error("map is empty. (1)")
            return map
        } catch (ex: Throwable) {
            log.e(ex, "loadMastodonLanguages failed.")
        }
    }
    // fallback
    try {
        val map = mergeLanguageName(
            context,
            context.loadRawResource(R.raw.languages_fallback)
                .decodeToString()
        )
        if (map.isEmpty()) error("map is empty. (2)")
        return map
    } catch (ex: Throwable) {
        log.e(ex, "loadRawResource failed.")
    }
    // error
    return HashMap()
}
