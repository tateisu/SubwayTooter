package jp.juggler.subwaytooter.actpost

import jp.juggler.subwaytooter.ActPost
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.ApiTask
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.api.runApiTask2
import jp.juggler.subwaytooter.util.EmojiDecoder
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.wrapWeakReference
import jp.juggler.util.log.LogCategory
import jp.juggler.util.ui.attrColor

private val log = LogCategory("ActPostCharCount")

// 最大文字数を取得する
// 暫定で仮の値を返すことがある
// 裏で取得し終わったら updateTextCount() を呼び出す
private fun ActPost.getMaxCharCount(): Int {
    val account = account
    if (account != null && !account.isPseudo) {
        // インスタンス情報を確認する
        val info = TootInstance.getCached(account)
        if (info == null || info.isExpired) {
            // 情報がないか古いなら再取得

            // 同時に実行するタスクは1つまで
            if (jobMaxCharCount?.get()?.isActive != true) {
                jobMaxCharCount = launchMain {
                    try {
                        runApiTask2(account, progressStyle = ApiTask.PROGRESS_NONE) {
                            TootInstance.getOrThrow(it)
                        }
                        if (isFinishing || isDestroyed) return@launchMain
                        updateTextCount()
                    } catch (ex: Throwable) {
                        log.w(ex, "getMaxCharCount failed.")
                    }
                }.wrapWeakReference
            }
            // fall thru
        }

        info?.configuration
            ?.jsonObject("statuses")
            ?.int("max_characters")
            ?.takeIf { it > 0 }
            ?.let { return it }

        info?.max_toot_chars
            ?.takeIf { it > 0 }
            ?.let { return it }
    }

    // アカウント設定で指定した値があるならそれを使う
    val forceMaxTootChars = account?.maxTootChars
    return when {
        forceMaxTootChars != null && forceMaxTootChars > 0 -> forceMaxTootChars
        else -> 500
    }
}

// 残り文字数を計算してビューに設定する
fun ActPost.updateTextCount() {
    var length = 0

    length += TootAccount.countText(
        EmojiDecoder.decodeShortCode(views.etContent.text.toString())
    )

    if (views.cbContentWarning.isChecked) {
        length += TootAccount.countText(
            EmojiDecoder.decodeShortCode(views.etContentWarning.text.toString())
        )
    }

    var max = getMaxCharCount()

    fun checkEnqueteLength() {
        for (et in etChoices) {
            length += TootAccount.countText(
                EmojiDecoder.decodeShortCode(et.text.toString())
            )
        }
    }

    when (views.spPollType.selectedItemPosition) {
        1 -> checkEnqueteLength()

        2 -> {
            max -= 150 // フレニコ固有。500-150で350になる
            checkEnqueteLength()
        }
    }

    val remain = max - length

    views.tvCharCount.text = remain.toString()
    views.tvCharCount.setTextColor(
        attrColor(
            when {
                remain < 0 -> R.attr.colorRegexFilterError
                else -> android.R.attr.textColorPrimary
            }
        )
    )
}
