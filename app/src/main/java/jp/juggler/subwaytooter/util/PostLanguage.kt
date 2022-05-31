package jp.juggler.subwaytooter.util

import android.content.Context
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.table.SavedAccount
import java.util.*

// 言語コードと表示文字列のペアのリストを返す
fun Context.loadLanguageList() =
    ArrayList<Pair<String, String>>().apply {
        add(Pair(SavedAccount.LANG_WEB, getString(R.string.use_web_settings)))
        add(Pair(SavedAccount.LANG_DEVICE, getString(R.string.device_language)))
        val nameMap = HashMap<String, String>()
        addAll(
            Locale.getAvailableLocales().mapNotNull { locale ->
                locale.language.takeIf { it.length == 2 || it.contains('-') }
                    ?.also { code -> nameMap[code] = "$code ${locale.displayLanguage}" }
            }
                .toSet()
                .toList()
                .sorted()
                .map { Pair(it, nameMap[it]!!) }
        )
    }
