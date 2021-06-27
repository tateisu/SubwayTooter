package jp.juggler.subwaytooter

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.TootAttachment
import jp.juggler.subwaytooter.api.entity.TootScheduled
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.api.entity.parseItem
import jp.juggler.subwaytooter.dialog.DlgDateTime
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.PostAttachment
import jp.juggler.util.LogCategory
import jp.juggler.util.cast
import jp.juggler.util.decodeJsonObject
import jp.juggler.util.notEmpty

private val log = LogCategory("ActPostSchedule")

fun ActPost.showSchedule() {
    tvSchedule.text = when (states.timeSchedule) {
        0L -> getString(R.string.unspecified)
        else -> TootStatus.formatTime(this, states.timeSchedule, true)
    }
}

fun ActPost.performSchedule() {
    DlgDateTime(this).open(states.timeSchedule) { t ->
        states.timeSchedule = t
        showSchedule()
    }
}

fun ActPost.resetSchedule() {
    states.timeSchedule = 0L
    showSchedule()
}

fun ActPost.initializeFromScheduledStatus(account: SavedAccount, jsonText: String) {
    try {
        val item = parseItem(
            ::TootScheduled,
            TootParser(this, account),
            jsonText.decodeJsonObject(),
            log
        ) ?: error("initializeFromScheduledStatus: parse failed.")

        scheduledStatus = item

        states.timeSchedule = item.timeScheduledAt
        states.visibility = item.visibility
        cbNSFW.isChecked = item.sensitive

        etContent.setText(item.text)

        val cw = item.spoilerText
        etContentWarning.setText(cw ?: "")
        cbContentWarning.isChecked = cw?.isNotEmpty() == true

        // 2019/1/7 どうも添付データを古い投稿から引き継げないようだ…。
        // 2019/1/22 https://github.com/tootsuite/mastodon/pull/9894 で直った。
        item.mediaAttachments
            ?.mapNotNull { src ->
                src.cast<TootAttachment>()
                    ?.apply { redraft = true }
                    ?.let { PostAttachment(it) }
            }
            ?.notEmpty()
            ?.let {
                saveAttachmentList()
                this.attachmentList.clear()
                this.attachmentList.addAll(it)
            }
    } catch (ex: Throwable) {
        log.trace(ex)
    }
}
