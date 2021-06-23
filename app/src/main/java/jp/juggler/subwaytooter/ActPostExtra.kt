package jp.juggler.subwaytooter

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.table.PostDraft
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.*
import jp.juggler.util.*

fun ActPost.appendContentText(
    src: String?,
    selectBefore: Boolean = false,
) {
    if (src?.isEmpty() != false) return
    val svEmoji = DecodeOptions(
        context = this,
        decodeEmoji = true,
        mentionDefaultHostDomain = account ?: unknownHostAndDomain
    ).decodeEmoji(src)
    if (svEmoji.isEmpty()) return

    val editable = etContent.text
    if (editable == null) {
        val sb = StringBuilder()
        if (selectBefore) {
            val start = 0
            sb.append(' ')
            sb.append(svEmoji)
            etContent.setText(sb)
            etContent.setSelection(start)
        } else {
            sb.append(svEmoji)
            etContent.setText(sb)
            etContent.setSelection(sb.length)
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
            etContent.text = editable
            etContent.setSelection(start)
        } else {
            editable.append(svEmoji)
            etContent.text = editable
            etContent.setSelection(editable.length)
        }
    }
}

fun ActPost.appendContentText(src: Intent) {
    val list = ArrayList<String>()

    var sv: String?
    sv = src.getStringExtra(Intent.EXTRA_SUBJECT)
    if (sv?.isNotEmpty() == true) list.add(sv)
    sv = src.getStringExtra(Intent.EXTRA_TEXT)
    if (sv?.isNotEmpty() == true) list.add(sv)

    if (list.isNotEmpty()) {
        appendContentText(list.joinToString(" "))
    }
}

// returns true if has content
fun ActPost.hasContent(): Boolean {
    val content = etContent.text.toString()
    val contentWarning =
        if (cbContentWarning.isChecked) etContentWarning.text.toString() else ""

    return when {
        content.isNotBlank() -> true
        contentWarning.isNotBlank() -> true
        hasPoll() -> true
        else -> false
    }
}

fun ActPost.resetText() {
    isPostComplete = false
    states.inReplyToId = null
    states.inReplyToText = null
    states.inReplyToImage = null
    states.inReplyToUrl = null
    states.mushroomInput = 0
    states.mushroomStart = 0
    states.mushroomEnd = 0
    states.redraftStatusId = null
    states.timeSchedule = 0L
    attachmentPicker.reset()
    scheduledStatus = null
    attachmentList.clear()
    cbQuote.isChecked = false
    etContent.setText("")
    spEnquete.setSelection(0, false)
    etChoices.forEach { it.setText("") }
    accountList = SavedAccount.loadAccountList(this)
    SavedAccount.sort(accountList)
    if (accountList.isEmpty()) {
        showToast(true, R.string.please_add_account)
        finish()
        return
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
fun ActPost.updateText(
    intent: Intent,
    confirmed: Boolean = false,
    saveDraft: Boolean = true,
    resetAccount: Boolean = true,
) {
    if (!canSwitchAccount()) return

    if (!confirmed && hasContent()) {
        AlertDialog.Builder(this)
            .setMessage("編集中のテキストや文脈を下書きに退避して、新しい投稿を編集しますか？ ")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                updateText(intent, confirmed = true)
            }
            .setCancelable(true)
            .show()
        return
    }

    if (saveDraft) saveDraft()

    resetText()

    // Android 9 から、明示的にフォーカスを当てる必要がある
    etContent.requestFocus()

    this.attachmentList.clear()
    saveAttachmentList()

    if (resetAccount) {
        states.visibility = null
        this.account = null
        val accountDbId = intent.getLongExtra(ActPost.KEY_ACCOUNT_DB_ID, SavedAccount.INVALID_DB_ID)
        accountList.find { it.db_id == accountDbId }?.let { selectAccount(it) }
    }

    val sharedIntent = intent.getParcelableExtra<Intent>(ActPost.KEY_SHARED_INTENT)
    if (sharedIntent != null) {
        initializeFromSharedIntent(sharedIntent)
    }

    appendContentText(intent.getStringExtra(ActPost.KEY_INITIAL_TEXT))

    val account = this.account

    if (account != null) {
        intent.getStringExtra(ActPost.KEY_REPLY_STATUS)
            ?.let { initializeFromReplyStatus(account, it) }
    }

    appendContentText(account?.default_text, selectBefore = true)
    cbNSFW.isChecked = account?.default_sensitive ?: false

    if (account != null) {
        // 再編集
        intent.getStringExtra(ActPost.KEY_REDRAFT_STATUS)
            ?.let { initializeFromRedraftStatus(account, it) }

        // 予約編集の再編集
        intent.getStringExtra(ActPost.KEY_SCHEDULED_STATUS)
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
                val uri = sharedIntent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                val type = sharedIntent.type
                if (uri != null) {
                    addAttachment(uri, type)
                    true
                } else {
                    false
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val listUri =
                    sharedIntent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
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

        if (!hasUri || !PrefB.bpIgnoreTextInSharedMedia(pref)) {
            appendContentText(sharedIntent)
        }
    } catch (ex: Throwable) {
        ActPost.log.trace(ex)
    }
}

fun ActPost.performMore() {
    val dialog = ActionsDialog()

    dialog.addAction(getString(R.string.open_picker_emoji)) {
        completionHelper.openEmojiPickerFromMore()
    }

    dialog.addAction(getString(R.string.clear_text)) {
        etContent.setText("")
        etContentWarning.setText("")
    }

    dialog.addAction(getString(R.string.clear_text_and_media)) {
        etContent.setText("")
        etContentWarning.setText("")
        attachmentList.clear()
        showMediaAttachment()
    }

    if (PostDraft.hasDraft()) dialog.addAction(getString(R.string.restore_draft)) {
        openDraftPicker()
    }

    dialog.addAction(getString(R.string.recommended_plugin)) {
        showRecommendedPlugin(null)
    }

    dialog.show(this, null)
}

fun ActPost.performPost() {
    // アップロード中は投稿できない
    if (attachmentList.any { it.status == PostAttachment.Status.Progress }) {
        showToast(false, R.string.media_attachment_still_uploading)
        return
    }

    val account = this.account ?: return
    var pollType: TootPollsType? = null
    var pollItems: ArrayList<String>? = null
    var pollExpireSeconds = 0
    var pollHideTotals = false
    var pollMultipleChoice = false
    when (spEnquete.selectedItemPosition) {
        1 -> {
            pollType = TootPollsType.Mastodon
            pollItems = pollChoiceList()
            pollExpireSeconds = pollExpireSeconds()
            pollHideTotals = cbHideTotals.isChecked
            pollMultipleChoice = cbMultipleChoice.isChecked
        }
        2 -> {
            pollType = TootPollsType.FriendsNico
            pollItems = pollChoiceList()
        }
    }

    PostImpl(
        activity = this,
        account = account,
        content = etContent.text.toString().trim { it <= ' ' },
        spoilerText = when {
            !cbContentWarning.isChecked -> null
            else -> etContentWarning.text.toString().trim { it <= ' ' }
        },
        visibilityArg = states.visibility ?: TootVisibility.Public,
        bNSFW = cbNSFW.isChecked,
        inReplyToId = states.inReplyToId,
        attachmentListArg = this.attachmentList,
        enqueteItemsArg = pollItems,
        pollType = pollType,
        pollExpireSeconds = pollExpireSeconds,
        pollHideTotals = pollHideTotals,
        pollMultipleChoice = pollMultipleChoice,
        scheduledAt = states.timeSchedule,
        scheduledId = scheduledStatus?.id,
        redraftStatusId = states.redraftStatusId,
        emojiMapCustom = App1.custom_emoji_lister.getMap(account),
        useQuoteToot = cbQuote.isChecked,
        callback = object : PostCompleteCallback {
            override fun onPostComplete(targetAccount: SavedAccount, status: TootStatus) {
                val data = Intent()
                data.putExtra(ActPost.EXTRA_POSTED_ACCT, targetAccount.acct.ascii)
                status.id.putTo(data, ActPost.EXTRA_POSTED_STATUS_ID)
                states.redraftStatusId?.putTo(data, ActPost.EXTRA_POSTED_REDRAFT_ID)
                status.in_reply_to_id?.putTo(data, ActPost.EXTRA_POSTED_REPLY_ID)
                ActMain.refActMain?.get()?.onCompleteActPost(data)

                if (isMultiWindowPost) {
                    resetText()
                    updateText(Intent(), confirmed = true, saveDraft = false, resetAccount = false)
                    afterUpdateText()
                } else {
                    // ActMainの復元が必要な場合に備えてintentのdataでも渡す
                    setResult(AppCompatActivity.RESULT_OK, data)
                    isPostComplete = true
                    this@performPost.finish()
                }
            }

            override fun onScheduledPostComplete(targetAccount: SavedAccount) {
                showToast(false, getString(R.string.scheduled_status_sent))
                val data = Intent()
                data.putExtra(ActPost.EXTRA_POSTED_ACCT, targetAccount.acct.ascii)

                if (isMultiWindowPost) {
                    resetText()
                    updateText(Intent(), confirmed = true, saveDraft = false, resetAccount = false)
                    afterUpdateText()
                    ActMain.refActMain?.get()?.onCompleteActPost(data)
                } else {
                    setResult(AppCompatActivity.RESULT_OK, data)
                    isPostComplete = true
                    this@performPost.finish()
                }
            }
        }
    ).run()
}
