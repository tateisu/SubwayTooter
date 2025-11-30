package jp.juggler.subwaytooter.emoji

import androidx.annotation.StringRes
import jp.juggler.subwaytooter.R

enum class EmojiCategory(@StringRes val titleId: Int) {
    Recent(R.string.emoji_category_recent),
    Custom(R.string.emoji_category_custom),
    People(R.string.emoji_category_people),
    ComplexTones(R.string.emoji_category_composite_tones),
    Nature(R.string.emoji_category_nature),
    Foods(R.string.emoji_category_foods),
    Activities(R.string.emoji_category_activity),
    Places(R.string.emoji_category_places),
    Objects(R.string.emoji_category_objects),
    Symbols(R.string.emoji_category_symbols),
    Flags(R.string.emoji_category_flags),
    Others(R.string.emoji_category_others),

    ;

    val emojiList = ArrayList<UnicodeEmoji>()
}
