package jp.juggler.subwaytooter.actpost

import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.ActPost
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.api.entity.TootVisibility
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.api.syncStatus
import jp.juggler.subwaytooter.calcIconRound
import jp.juggler.subwaytooter.span.emojiSizeMode
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.decodeJsonObject
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.ui.vg

private val log = LogCategory("ActPostReply")

fun ActPost.resetReply() {
    states.inReplyToId = null
    states.inReplyToText = null
    states.inReplyToImage = null
    states.inReplyToUrl = null
}

fun ActPost.showQuotedRenote() {
    views.cbQuote.vg(states.inReplyToId != null)
}

suspend fun ActPost.showReplyTo() {
    views.llReply.vg(states.inReplyToId != null)?.let {
        views.tvReplyTo.text = DecodeOptions(
            this,
            linkHelper = account,
            short = true,
            decodeEmoji = true,
            emojiSizeMode =  account.emojiSizeMode(),
        ).decodeHTML(states.inReplyToText)
        views.ivReply.setImageUrl(
            calcIconRound(views.ivReply.layoutParams),
            states.inReplyToImage
        )
    }
}

suspend fun ActPost.removeReply() {
    states.inReplyToId = null
    states.inReplyToText = null
    states.inReplyToImage = null
    states.inReplyToUrl = null
    showReplyTo()
    showQuotedRenote()
}

suspend fun ActPost.initializeFromReplyStatus(account: SavedAccount, jsonText: String) {
    try {
        val replyStatus =
            TootParser(this, account).status(jsonText.decodeJsonObject())
                ?: error("initializeFromReplyStatus: parse failed.")

        val isQuote = intent.getBooleanExtra(ActPost.KEY_QUOTE, false)
        if (isQuote) {
            views.cbQuote.isChecked = true

            // 引用リノートはCWやメンションを引き継がない
        } else {

            // CW をリプライ元に合わせる
            if (replyStatus.spoiler_text.isNotEmpty()) {
                views.cbContentWarning.isChecked = true
                views.etContentWarning.setText(replyStatus.spoiler_text)
            }

            // 新しいメンションリスト
            val mentionList = ArrayList<Acct>()

            // 自己レス以外なら元レスへのメンションを追加
            // 最初に追加する https://github.com/tateisu/SubwayTooter/issues/94
            if (!account.isMe(replyStatus.account)) {
                mentionList.add(account.getFullAcct(replyStatus.account))
            }

            // 元レスに含まれていたメンションを複製
            replyStatus.mentions?.forEach { mention ->

                val whoAcct = mention.acct

                // 空データなら追加しない
                if (!whoAcct.isValid) return@forEach

                // 自分なら追加しない
                if (account.isMe(whoAcct)) return@forEach

                // 既出でないなら追加する
                val acct = account.getFullAcct(whoAcct)
                if (!mentionList.contains(acct)) mentionList.add(acct)
            }

            if (mentionList.isNotEmpty()) {
                appendContentText(
                    StringBuilder().apply {
                        for (acct in mentionList) {
                            if (isNotEmpty()) append(' ')
                            append("@${acct.ascii}")
                        }
                        append(' ')
                    }.toString()
                )
            }
        }

        // リプライ表示をつける
        states.inReplyToId = replyStatus.id
        states.inReplyToText = replyStatus.content
        states.inReplyToImage = replyStatus.account.avatar_static
        states.inReplyToUrl = replyStatus.url

        // 公開範囲
        try {
            // 比較する前にデフォルトの公開範囲を計算する

            states.visibility = states.visibility
                ?: account.visibility
            //	?: TootVisibility.Public
            // VISIBILITY_WEB_SETTING だと 1.5未満のタンスでトラブルになる

            if (states.visibility == TootVisibility.Unknown) {
                states.visibility = TootVisibility.PrivateFollowers
            }

            val sample = when (val base = replyStatus.visibility) {
                TootVisibility.Unknown -> TootVisibility.PrivateFollowers
                else -> base
            }

            if (TootVisibility.WebSetting == states.visibility) {
                // 「Web設定に合わせる」だった場合は無条件にリプライ元の公開範囲に変更する
                states.visibility = sample
            } else if (TootVisibility.isVisibilitySpoilRequired(states.visibility, sample)) {
                // デフォルトの方が公開範囲が大きい場合、リプライ元に合わせて公開範囲を狭める
                states.visibility = sample
            }
        } catch (ex: Throwable) {
            log.e(ex, "initializeFromReplyStatus: can't initialize visibility.")
        }
    } catch (ex: Throwable) {
        log.e(ex, "initializeFromReplyStatus failed.")
    }
}

fun ActPost.startReplyConversion(accessInfo: SavedAccount) {

    val inReplyToUrl = states.inReplyToUrl

    if (inReplyToUrl == null) {
        // 下書きが古い形式の場合、URLがないので別タンスへの移動ができない
        AlertDialog.Builder(this@startReplyConversion)
            .setMessage(R.string.account_change_failed_old_draft_has_no_in_reply_to_url)
            .setNeutralButton(R.string.close, null)
            .show()
        return
    }

    launchMain {
        var resultStatus: TootStatus? = null
        runApiTask(
            accessInfo,
            progressPrefix = getString(R.string.progress_synchronize_toot)
        ) { client ->
            val pair = client.syncStatus(accessInfo, inReplyToUrl)
            resultStatus = pair.second
            pair.first
        }?.let { result ->
            when (val targetStatus = resultStatus) {
                null -> showToast(
                    true,
                    getString(R.string.in_reply_to_id_conversion_failed) + "\n" + result.error
                )
                else -> {
                    states.inReplyToId = targetStatus.id
                    setAccountWithVisibilityConversion(accessInfo)
                }
            }
        }
    }
}
