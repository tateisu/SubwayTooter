package jp.juggler.subwaytooter.actpost

import jp.juggler.subwaytooter.ActPost
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.TootAttachment
import jp.juggler.subwaytooter.api.entity.TootScheduled
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.api.entity.parseItem
import jp.juggler.subwaytooter.dialog.DlgDateTime
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.PostAttachment
import jp.juggler.util.data.cast
import jp.juggler.util.data.decodeJsonObject
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory

private val log = LogCategory("ActPostSchedule")

fun ActPost.showSchedule() {
    views.tvSchedule.text = when (states.timeSchedule) {
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

suspend fun ActPost.initializeFromScheduledStatus(account: SavedAccount, jsonText: String) {
    try {
        val item = parseItem(jsonText.decodeJsonObject()) {
            val parser = TootParser(this, account)
            TootScheduled(parser, it)
        } ?: error("initializeFromScheduledStatus: parse failed.")

        scheduledStatus = item

        states.timeSchedule = item.timeScheduledAt
        states.visibility = item.visibility
        views.cbNSFW.isChecked = item.sensitive

        views.etContent.setText(item.text)

        val cw = item.spoilerText
        views.etContentWarning.setText(cw ?: "")
        views.cbContentWarning.isChecked = cw?.isNotEmpty() == true

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
        log.e(ex, "initializeFromScheduledStatus failed.")
    }
}
