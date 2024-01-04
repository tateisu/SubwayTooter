package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.R
import jp.juggler.util.data.JsonArray
import jp.juggler.util.log.LogCategory

enum class TootFilterContext(
    // アプリ内で管理するビット
    val bit: Int,
    // API中の識別子
    val apiName: String,
    // アプリに表示する文字列のID
    val caption_id: Int,
) {
    Home(1, "home", R.string.filter_home),
    Notifications(2, "notifications", R.string.filter_notification),
    Public(4, "public", R.string.filter_public),
    Thread(8, "thread", R.string.filter_thread),
    Account(16, "account", R.string.filter_profile),

    ;

    companion object {
        private val log = LogCategory("TootFilterContext")

        private val apiNameMap = entries.associateBy { it.apiName }

        fun parseBits(src: JsonArray?): Int =
            src?.stringList()?.mapNotNull { apiNameMap[it]?.bit }?.sum() ?: 0

        fun bitsToNames(mask: Int) =
            entries.filter { it.bit.and(mask) != 0 }.map { it.caption_id }
    }
}
