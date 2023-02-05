package jp.juggler.subwaytooter.actpost

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.ActPost
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.actmain.onCompleteActPost
import jp.juggler.subwaytooter.api.entity.TootPollsType
import jp.juggler.subwaytooter.api.entity.TootVisibility
import jp.juggler.subwaytooter.api.entity.unknownHostAndDomain
import jp.juggler.subwaytooter.dialog.DlgConfirm.confirm
import jp.juggler.subwaytooter.dialog.actionsDialog
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.daoPostDraft
import jp.juggler.subwaytooter.table.daoSavedAccount
import jp.juggler.subwaytooter.table.sortedByNickname
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.PostAttachment
import jp.juggler.subwaytooter.util.PostImpl
import jp.juggler.subwaytooter.util.PostResult
import jp.juggler.util.*
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.data.CharacterGroup
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.ui.vg

private val log = LogCategory("ActPostExtra")

fun ActPost.appendContentText(
    src: String?,
    selectBefore: Boolean = false,
) {
    if (src?.isEmpty() != false) return
    val svEmoji = DecodeOptions(
        context = this,
        decodeEmoji = true,
        authorDomain = account ?: unknownHostAndDomain,
    ).decodeEmoji(src)
    if (svEmoji.isEmpty()) return

    val editable = views.etContent.text
    if (editable == null) {
        val sb = StringBuilder()
        if (selectBefore) {
            val start = 0
            sb.append(' ')
            sb.append(svEmoji)
            views.etContent.setText(sb)
            views.etContent.setSelection(start)
        } else {
            sb.append(svEmoji)
            views.etContent.setText(sb)
            views.etContent.setSelection(sb.length)
        }
    } else {
        if (editable.isNotEmpty() &&
            !CharacterGroup.isWhitespace(editable[editable.length - 1].code)
        ) {
            editable.append(' ')
        }

        if (selectBefore) {
            val start = editable.length
            editable.append(' ')
            editable.append(svEmoji)
            views.etContent.text = editable
            views.etContent.setSelection(start)
        } else {
            editable.append(svEmoji)
            views.etContent.text = editable
            views.etContent.setSelection(editable.length)
        }
    }
}

fun ActPost.appendContentText(src: Intent) {
    val list = ArrayList<String>()

    var sv: String?
    sv = src.string(Intent.EXTRA_SUBJECT)
    if (sv?.isNotEmpty() == true) list.add(sv)
    sv = src.string(Intent.EXTRA_TEXT)
    if (sv?.isNotEmpty() == true) list.add(sv)

    if (list.isNotEmpty()) {
        appendContentText(list.joinToString(" "))
    }
}

// returns true if has content
fun ActPost.hasContent(): Boolean {
    val content = views.etContent.text.toString()
    val contentWarning =
        if (views.cbContentWarning.isChecked) views.etContentWarning.text.toString() else ""

    return when {
        content.isNotBlank() -> true
        contentWarning.isNotBlank() -> true
        hasPoll() -> true
        else -> false
    }
}

suspend fun ActPost.resetText() {
    isPostComplete = false

    resetReply()

    resetMushroom()
    states.redraftStatusId = null
    states.editStatusId = null
    states.timeSchedule = 0L
    attachmentPicker.reset()
    scheduledStatus = null
    attachmentList.clear()
    views.cbQuote.isChecked = false
    views.etContent.setText("")
    views.spPollType.setSelection(0, false)
    etChoices.forEach { it.setText("") }
    accountList = daoSavedAccount.loadAccountList().sortedByNickname()
    if (accountList.isEmpty()) {
        showToast(true, R.string.please_add_account)
        finish()
    }
}

fun ActPost.afterUpdateText() {
    // 2017/9/13 VISIBILITY_WEB_SETTING から VISIBILITY_PUBLICに変更した
    // VISIBILITY_WEB_SETTING だと 1.5未満のタンスでトラブルになるので…
    states.visibility = states.visibility ?: account?.visibility ?: TootVisibility.Public

    // アカウント未選択なら表示を更新する
    // 選択済みなら変えない
    if (account == null) selectAccount(null)

    showContentWarningEnabled()
    showMediaAttachment()
    showVisibility()
    showReplyTo()
    showPoll()
    showQuotedRenote()
    showSchedule()
    updateTextCount()
}

// 初期化時と投稿完了時とリセット確認後に呼ばれる
suspend fun ActPost.updateText(
    intent: Intent,
    saveDraft: Boolean = true,
    resetAccount: Boolean = true,
) {
    if (!canSwitchAccount()) return

    if (saveDraft && hasContent()) {
        confirm(R.string.post_reset_confirm)
        saveDraft()
    }

    resetText()

    // Android 9 から、明示的にフォーカスを当てる必要がある
    views.etContent.requestFocus()

    this.attachmentList.clear()
    saveAttachmentList()

    if (resetAccount) {
        states.visibility = null
        this.account = null
        intent.long(ActPost.KEY_ACCOUNT_DB_ID)
            ?.let { dbId -> accountList.find { it.db_id == dbId } }
            ?.let { selectAccount(it) }
    }

    val sharedIntent = intent.getIntentExtra(ActPost.KEY_SHARED_INTENT)

    if (sharedIntent != null) {
        initializeFromSharedIntent(sharedIntent)
    }

    appendContentText(intent.string(ActPost.KEY_INITIAL_TEXT))

    val account = this.account

    if (account != null) {
        intent.string(ActPost.KEY_REPLY_STATUS)
            ?.let { initializeFromReplyStatus(account, it) }
    }

    appendContentText(account?.default_text, selectBefore = true)
    views.cbNSFW.isChecked = account?.default_sensitive ?: false

    if (account != null) {
        // 再編集
        intent.string(ActPost.KEY_REDRAFT_STATUS)
            ?.let { initializeFromRedraftStatus(account, it) }

        // 再編集
        intent.string(ActPost.KEY_EDIT_STATUS)
            ?.let { initializeFromEditStatus(account, it) }

        // 予約編集の再編集
        intent.string(ActPost.KEY_SCHEDULED_STATUS)
            ?.let { initializeFromScheduledStatus(account, it) }
    }

    afterUpdateText()
}

fun ActPost.initializeFromSharedIntent(sharedIntent: Intent) {
    try {
        val hasUri = when (sharedIntent.action) {
            Intent.ACTION_VIEW -> {
                val uri = sharedIntent.data
                val type = sharedIntent.type
                if (uri != null) {
                    addAttachment(uri, type)
                    true
                } else {
                    false
                }
            }

            Intent.ACTION_SEND -> {
                val uri = sharedIntent.getStreamUriExtra()
                val type = sharedIntent.type
                if (uri != null) {
                    addAttachment(uri, type)
                    true
                } else {
                    false
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val listUri = sharedIntent.getStreamUriListExtra()
                    ?.filterNotNull()
                if (listUri?.isNotEmpty() == true) {
                    for (uri in listUri) {
                        addAttachment(uri)
                    }
                    true
                } else {
                    false
                }
            }

            else -> false
        }

        if (!hasUri || !PrefB.bpIgnoreTextInSharedMedia.value) {
            appendContentText(sharedIntent)
        }
    } catch (ex: Throwable) {
        log.e(ex, "initializeFromSharedIntent failed.")
    }
}

fun ActPost.performMore() {
    launchAndShowError {
        actionsDialog {
            action(getString(R.string.open_picker_emoji)) {
                completionHelper.openEmojiPickerFromMore()
            }

            action(getString(R.string.clear_text)) {
                views.etContent.setText("")
                views.etContentWarning.setText("")
            }

            action(getString(R.string.clear_text_and_media)) {
                views.etContent.setText("")
                views.etContentWarning.setText("")
                attachmentList.clear()
                showMediaAttachment()
            }

            if (daoPostDraft.hasDraft()) action(getString(R.string.restore_draft)) {
                openDraftPicker()
            }

            action(getString(R.string.recommended_plugin)) {
                showRecommendedPlugin(null)
            }
        }
    }
}

fun ActPost.performPost() {
    val activity = this
    launchAndShowError {
        // アップロード中は投稿できない
        if (attachmentList.any { it.status == PostAttachment.Status.Progress }) {
            showToast(false, R.string.media_attachment_still_uploading)
            return@launchAndShowError
        }

        val account = activity.account ?: return@launchAndShowError
        var pollType: TootPollsType? = null
        var pollItems: ArrayList<String>? = null
        var pollExpireSeconds = 0
        var pollHideTotals = false
        var pollMultipleChoice = false
        when (views.spPollType.selectedItemPosition) {
            0 -> Unit // not poll
            else -> {
                pollType = TootPollsType.Mastodon
                pollItems = pollChoiceList()
                pollExpireSeconds = pollExpireSeconds()
                pollHideTotals = views.cbHideTotals.isChecked
                pollMultipleChoice = views.cbMultipleChoice.isChecked
            }
        }

        val postResult = PostImpl(
            activity = activity,
            account = account,
            content = views.etContent.text.toString().trim { it <= ' ' },
            spoilerText = when {
                !views.cbContentWarning.isChecked -> null
                else -> views.etContentWarning.text.toString().trim { it <= ' ' }
            },
            visibilityArg = states.visibility ?: TootVisibility.Public,
            bNSFW = views.cbNSFW.isChecked,
            inReplyToId = states.inReplyToId,
            attachmentListArg = activity.attachmentList,
            enqueteItemsArg = pollItems,
            pollType = pollType,
            pollExpireSeconds = pollExpireSeconds,
            pollHideTotals = pollHideTotals,
            pollMultipleChoice = pollMultipleChoice,
            scheduledAt = states.timeSchedule,
            scheduledId = scheduledStatus?.id,
            redraftStatusId = states.redraftStatusId,
            editStatusId = states.editStatusId,
            emojiMapCustom = App1.custom_emoji_lister.getMapNonBlocking(account),
            useQuoteToot = views.cbQuote.isChecked,
            lang = languages.elementAtOrNull(views.spLanguage.selectedItemPosition)?.first
                ?: SavedAccount.LANG_WEB
        ).runSuspend()
        when (postResult) {
            is PostResult.Normal -> {
                val data = Intent()
                data.putExtra(ActPost.EXTRA_POSTED_ACCT, postResult.targetAccount.acct.ascii)
                postResult.status.id.putTo(data, ActPost.EXTRA_POSTED_STATUS_ID)
                states.redraftStatusId?.putTo(data, ActPost.EXTRA_POSTED_REDRAFT_ID)
                postResult.status.in_reply_to_id?.putTo(data, ActPost.EXTRA_POSTED_REPLY_ID)
                if (states.editStatusId != null) {
                    data.putExtra(ActPost.KEY_EDIT_STATUS, postResult.status.json.toString())
                }
                ActMain.refActMain?.get()?.onCompleteActPost(data)

                if (isMultiWindowPost) {
                    resetText()
                    updateText(Intent(), saveDraft = false, resetAccount = false)
                    afterUpdateText()
                } else {
                    // ActMainの復元が必要な場合に備えてintentのdataでも渡す
                    setResult(AppCompatActivity.RESULT_OK, data)
                    isPostComplete = true
                    this@performPost.finish()
                }
            }
            is PostResult.Scheduled -> {
                showToast(false, getString(R.string.scheduled_status_sent))
                val data = Intent()
                data.putExtra(ActPost.EXTRA_POSTED_ACCT, postResult.targetAccount.acct.ascii)

                if (isMultiWindowPost) {
                    resetText()
                    updateText(Intent(), saveDraft = false, resetAccount = false)
                    afterUpdateText()
                    ActMain.refActMain?.get()?.onCompleteActPost(data)
                } else {
                    setResult(AppCompatActivity.RESULT_OK, data)
                    isPostComplete = true
                    this@performPost.finish()
                }
            }
        }
    }
}

fun ActPost.showContentWarningEnabled() {
    views.etContentWarning.vg(views.cbContentWarning.isChecked)
}
