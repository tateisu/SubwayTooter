package jp.juggler.subwaytooter.emoji

import java.util.ArrayList

enum class EmojiCategory {
    Recent,
    Custom,
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
