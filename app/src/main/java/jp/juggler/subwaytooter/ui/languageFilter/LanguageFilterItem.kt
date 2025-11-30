package jp.juggler.subwaytooter.ui.languageFilter

import jp.juggler.subwaytooter.api.entity.TootStatus

data class LanguageFilterItem(
    val code: String,
    var allow: Boolean,
)

val languageFilterItemComparator = Comparator<LanguageFilterItem> { a, b ->
    when {
        a.code == TootStatus.LANGUAGE_CODE_DEFAULT -> -1
        b.code == TootStatus.LANGUAGE_CODE_DEFAULT -> 1
        a.code == TootStatus.LANGUAGE_CODE_UNKNOWN -> -1
        b.code == TootStatus.LANGUAGE_CODE_UNKNOWN -> 1
        else -> a.code.compareTo(b.code)
    }
}
