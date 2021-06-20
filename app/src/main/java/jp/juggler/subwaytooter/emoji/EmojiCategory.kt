package jp.juggler.subwaytooter.emoji

import java.util.ArrayList

enum class EmojiCategory(val special: Boolean = false) {
    Recent(special = true),
    Custom(special = true),
    People,
    ComplexTones,
    Nature,
    Foods,
    Activities,
    Places,
    Objects,
    Symbols,
    Flags,
    Others,

    ;

    val emojiList = ArrayList<UnicodeEmoji>()
}
