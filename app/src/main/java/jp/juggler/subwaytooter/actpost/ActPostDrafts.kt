package jp.juggler.subwaytooter.actpost

import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.ActPost
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootApiCallback
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.TootAttachment
import jp.juggler.subwaytooter.api.entity.TootAttachment.Companion.tootAttachmentJson
import jp.juggler.subwaytooter.api.entity.TootPolls
import jp.juggler.subwaytooter.api.entity.TootPollsType
import jp.juggler.subwaytooter.api.entity.TootVisibility
import jp.juggler.subwaytooter.dialog.DlgDraftPicker
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.daoPostDraft
import jp.juggler.subwaytooter.table.daoSavedAccount
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.PostAttachment
import jp.juggler.util.coroutine.launchProgress
import jp.juggler.util.data.JsonException
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.decodeJsonObject
import jp.juggler.util.data.notEmpty
import jp.juggler.util.data.toJsonArray
import jp.juggler.util.log.LogCategory
import kotlinx.coroutines.isActive
import okhttp3.Request
import ru.gildor.coroutines.okhttp.await
import kotlin.math.min

private val log = LogCategory("ActPostDrafts")

// DlgDraftPickerから参照される
const val DRAFT_CONTENT = "content"
const val DRAFT_CONTENT_WARNING = "content_warning"

private const val DRAFT_CONTENT_WARNING_CHECK = "content_warning_check"
private const val DRAFT_NSFW_CHECK = "nsfw_check"
private const val DRAFT_VISIBILITY = "visibility"
private const val DRAFT_ACCOUNT_DB_ID = "account_db_id"
private const val DRAFT_ATTACHMENT_LIST = "attachment_list"
private const val DRAFT_REPLY_ID = "reply_id"
private const val DRAFT_REPLY_TEXT = "reply_text"
private const val DRAFT_REPLY_IMAGE = "reply_image"
private const val DRAFT_REPLY_URL = "reply_url"
private const val DRAFT_POLL_TYPE = "poll_type"
private const val DRAFT_POLL_MULTIPLE = "poll_multiple"
private const val DRAFT_POLL_HIDE_TOTALS = "poll_hide_totals"
private const val DRAFT_POLL_EXPIRE_DAY = "poll_expire_day"
private const val DRAFT_POLL_EXPIRE_HOUR = "poll_expire_hour"
private const val DRAFT_POLL_EXPIRE_MINUTE = "poll_expire_minute"
private const val DRAFT_ENQUETE_ITEMS = "enquete_items"
private const val DRAFT_QUOTE = "quotedRenote" // 歴史的な理由で名前がMisskey用になってる
private const val DRAFT_IS_ENQUETE = "is_enquete" // deprecated. old draft may use this.

// poll type string to spinner index
private fun String?.toPollTypeIndex() = when (this) {
    "mastodon" -> 1
    "friendsNico" -> 2
    else -> 0
}

private fun Int?.toPollTypeString() = when (this) {
    1 -> "mastodon"
    2 -> "friendsNico"
    else -> ""
}

private suspend fun checkExist(url: String?): Boolean {
    if (url?.isEmpty() != false) return false
    try {
        val request = Request.Builder().url(url).build()
        App1.ok_http_client.newCall(request).await().use { response ->
            if (response.isSuccessful) return true
            log.e(TootApiClient.formatResponse(response, "check_exist failed."))
        }
    } catch (ex: Throwable) {
        log.e(ex, "checkExist failed.")
    }
    return false
}

fun ActPost.saveDraft() {
    val content = views.etContent.text.toString()
    val contentWarning =
        if (views.cbContentWarning.isChecked) views.etContentWarning.text.toString() else ""

    val isEnquete = views.spPollType.selectedItemPosition > 0

    val strChoice = arrayOf(
        if (isEnquete) etChoices[0].text.toString() else "",
        if (isEnquete) etChoices[1].text.toString() else "",
        if (isEnquete) etChoices[2].text.toString() else "",
        if (isEnquete) etChoices[3].text.toString() else ""
    )

    val hasContent = when {
        content.isNotBlank() -> true
        contentWarning.isNotBlank() -> true
        strChoice.any { it.isNotBlank() } -> true
        else -> false
    }

    if (!hasContent) {
        log.d("saveDraft: dont save empty content")
        return
    }

    try {
        val tmpAttachmentList = attachmentList
            .mapNotNull { it.attachment?.encodeJson() }
            .toJsonArray()

        val json = JsonObject()
        json[DRAFT_CONTENT] = content
        json[DRAFT_CONTENT_WARNING] = contentWarning
        json[DRAFT_CONTENT_WARNING_CHECK] = views.cbContentWarning.isChecked
        json[DRAFT_NSFW_CHECK] = views.cbNSFW.isChecked
        json[DRAFT_ACCOUNT_DB_ID] = account?.db_id ?: -1L
        json[DRAFT_ATTACHMENT_LIST] = tmpAttachmentList
        json[DRAFT_REPLY_TEXT] = states.inReplyToText
        json[DRAFT_REPLY_IMAGE] = states.inReplyToImage
        json[DRAFT_REPLY_URL] = states.inReplyToUrl
        json[DRAFT_QUOTE] = views.cbQuote.isChecked
        json[DRAFT_POLL_TYPE] = views.spPollType.selectedItemPosition.toPollTypeString()
        json[DRAFT_POLL_MULTIPLE] = views.cbMultipleChoice.isChecked
        json[DRAFT_POLL_HIDE_TOTALS] = views.cbHideTotals.isChecked
        json[DRAFT_POLL_EXPIRE_DAY] = views.etExpireDays.text.toString()
        json[DRAFT_POLL_EXPIRE_HOUR] = views.etExpireHours.text.toString()
        json[DRAFT_POLL_EXPIRE_MINUTE] = views.etExpireMinutes.text.toString()
        json[DRAFT_ENQUETE_ITEMS] = strChoice.toJsonArray()

        states.visibility?.id?.toString()?.let { json.put(DRAFT_VISIBILITY, it) }
        states.inReplyToId?.putTo(json, DRAFT_REPLY_ID)

        daoPostDraft.save(System.currentTimeMillis(), json)
    } catch (ex: Throwable) {
        log.e(ex, "saveDraft failed.")
    }
}

fun ActPost.openDraftPicker() {
    DlgDraftPicker().open(this) { draft -> restoreDraft(draft) }
}

fun ActPost.restoreDraft(draft: JsonObject) {
    val listWarning = ArrayList<String>()
    var targetAccount: SavedAccount? = null
    launchProgress(
        "restore from draft",
        doInBackground = { progress ->
            fun isTaskCancelled() = !coroutineContext.isActive

            var content = draft.string(DRAFT_CONTENT) ?: ""
            val tmpAttachmentList =
                draft.jsonArray(DRAFT_ATTACHMENT_LIST)?.objectList()?.toMutableList()

            val accountDbId = draft.long(DRAFT_ACCOUNT_DB_ID) ?: -1L
            val account = daoSavedAccount.loadAccount(accountDbId)
            if (account == null) {
                listWarning.add(getString(R.string.account_in_draft_is_lost))
                try {
                    if (tmpAttachmentList != null) {
                        // 本文からURLを除去する
                        tmpAttachmentList.forEach {
                            val textUrl = tootAttachmentJson(it).text_url
                            if (textUrl?.isNotEmpty() == true) {
                                content = content.replace(textUrl, "")
                            }
                        }
                        tmpAttachmentList.clear()
                        draft[DRAFT_ATTACHMENT_LIST] = tmpAttachmentList.toJsonArray()
                        draft[DRAFT_CONTENT] = content
                        draft.remove(DRAFT_REPLY_ID)
                        draft.remove(DRAFT_REPLY_TEXT)
                        draft.remove(DRAFT_REPLY_IMAGE)
                        draft.remove(DRAFT_REPLY_URL)
                    }
                } catch (ignored: JsonException) {
                }

                return@launchProgress "OK"
            }

            targetAccount = account

            // アカウントがあるなら基本的にはすべての情報を復元できるはずだが、いくつか確認が必要だ
            val apiClient = TootApiClient(this@restoreDraft, callback = object : TootApiCallback {
                override suspend fun isApiCancelled() = isTaskCancelled()

                override suspend fun publishApiProgress(s: String) {
                    progress.setMessageEx(s)
                }
            })
            apiClient.account = account

            // 返信ステータスが存在するかどうか
            EntityId.entityId(draft, DRAFT_REPLY_ID)?.let { inReplyToId ->
                val result = apiClient.request("/api/v1/statuses/$inReplyToId")
                if (isTaskCancelled()) return@launchProgress null
                if (result?.jsonObject == null) {
                    listWarning.add(getString(R.string.reply_to_in_draft_is_lost))
                    draft.remove(DRAFT_REPLY_ID)
                    draft.remove(DRAFT_REPLY_TEXT)
                    draft.remove(DRAFT_REPLY_IMAGE)
                }
            }

            try {
                if (tmpAttachmentList != null) {
                    // 添付メディアの存在確認
                    var isSomeAttachmentRemoved = false
                    val it = tmpAttachmentList.iterator()
                    while (it.hasNext()) {
                        if (isTaskCancelled()) return@launchProgress null
                        val ta = tootAttachmentJson(it.next())
                        if (checkExist(ta.url)) continue
                        it.remove()
                        isSomeAttachmentRemoved = true
                        // 本文からURLを除去する
                        val textUrl = ta.text_url
                        if (textUrl?.isNotEmpty() == true) {
                            content = content.replace(textUrl, "")
                        }
                    }
                    if (isSomeAttachmentRemoved) {
                        listWarning.add(getString(R.string.attachment_in_draft_is_lost))
                        draft[DRAFT_ATTACHMENT_LIST] = tmpAttachmentList.toJsonArray()
                        draft[DRAFT_CONTENT] = content
                    }
                }
            } catch (ex: JsonException) {
                log.e(ex, "can't parse tmpAttachmentList.")
            }

            "OK"
        },
        afterProc = { result ->
            // cancelled.
            if (result == null) return@launchProgress

            val content = draft.string(DRAFT_CONTENT) ?: ""
            val contentWarning = draft.string(DRAFT_CONTENT_WARNING) ?: ""
            val contentWarningChecked = draft.optBoolean(DRAFT_CONTENT_WARNING_CHECK)
            val nsfwChecked = draft.optBoolean(DRAFT_NSFW_CHECK)
            val tmpAttachmentList = draft.jsonArray(DRAFT_ATTACHMENT_LIST)
            val replyId = EntityId.entityId(draft, DRAFT_REPLY_ID)

            val draftVisibility =
                TootVisibility.parseSavedVisibility(draft.string(DRAFT_VISIBILITY))

            val evEmoji = DecodeOptions(this@restoreDraft, decodeEmoji = true)
                .decodeEmoji(content)

            views.etContent.setText(evEmoji)
            views.etContent.setSelection(evEmoji.length)
            views.etContentWarning.setText(contentWarning)
            views.etContentWarning.setSelection(contentWarning.length)
            views.cbContentWarning.isChecked = contentWarningChecked
            views.cbNSFW.isChecked = nsfwChecked
            if (draftVisibility != null) states.visibility = draftVisibility

            views.cbQuote.isChecked = draft.optBoolean(DRAFT_QUOTE)

            val sv = draft.string(DRAFT_POLL_TYPE)
            if (sv != null) {
                views.spPollType.setSelection(min(1, sv.toPollTypeIndex()))
            } else {
                // old draft
                val bv = draft.optBoolean(DRAFT_IS_ENQUETE, false)
                views.spPollType.setSelection(if (bv) 1 else 0)
            }

            views.cbMultipleChoice.isChecked = draft.optBoolean(DRAFT_POLL_MULTIPLE)
            views.cbHideTotals.isChecked = draft.optBoolean(DRAFT_POLL_HIDE_TOTALS)
            views.etExpireDays.setText(draft.optString(DRAFT_POLL_EXPIRE_DAY, "1"))
            views.etExpireHours.setText(draft.optString(DRAFT_POLL_EXPIRE_HOUR, ""))
            views.etExpireMinutes.setText(draft.optString(DRAFT_POLL_EXPIRE_MINUTE, ""))

            val array = draft.jsonArray(DRAFT_ENQUETE_ITEMS)
            if (array != null) {
                var srcIndex = 0
                for (et in etChoices) {
                    if (srcIndex < array.size) {
                        et.setText(array.optString(srcIndex))
                        ++srcIndex
                    } else {
                        et.setText("")
                    }
                }
            }

            if (targetAccount != null) selectAccount(targetAccount)

            if (tmpAttachmentList?.isNotEmpty() == true) {
                attachmentList.clear()
                tmpAttachmentList.forEach {
                    if (it !is JsonObject) return@forEach
                    val pa = PostAttachment(tootAttachmentJson(it))
                    attachmentList.add(pa)
                }
            }

            if (replyId != null) {
                states.inReplyToId = replyId
                states.inReplyToText = draft.string(DRAFT_REPLY_TEXT)
                states.inReplyToImage = draft.string(DRAFT_REPLY_IMAGE)
                states.inReplyToUrl = draft.string(DRAFT_REPLY_URL)
            }

            showContentWarningEnabled()
            showMediaAttachment()
            showVisibility()
            updateTextCount()
            showReplyTo()
            showPoll()
            showQuotedRenote()

            if (listWarning.isNotEmpty()) {
                val sb = StringBuilder()
                for (s in listWarning) {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append(s)
                }
                AlertDialog.Builder(this@restoreDraft)
                    .setMessage(sb)
                    .setNeutralButton(R.string.close, null)
                    .show()
            }
        }
    )
}

fun ActPost.initializeFromRedraftStatus(account: SavedAccount, jsonText: String) {
    try {
        val baseStatus =
            TootParser(this, account).status(jsonText.decodeJsonObject())
                ?: error("initializeFromRedraftStatus: parse failed.")

        states.redraftStatusId = baseStatus.id

        states.visibility = baseStatus.visibility

        val srcAttachments = baseStatus.media_attachments
        if (srcAttachments?.isNotEmpty() == true) {
            this.attachmentList.clear()
            try {
                for (src in srcAttachments) {
                    if (src is TootAttachment) {
                        src.redraft = true
                        val pa = PostAttachment(src)
                        pa.status = PostAttachment.Status.Ok
                        this.attachmentList.add(pa)
                    }
                }
            } catch (ex: Throwable) {
                log.e(ex, "can't initialize attachments from redraft.")
            }
            saveAttachmentList()
        }

        views.cbNSFW.isChecked = baseStatus.sensitive == true

        // 再編集の場合はdefault_textは反映されない

        val decodeOptions = DecodeOptions(
            this,
            mentionFullAcct = true,
            mentions = baseStatus.mentions,
            linkHelper = account,
        )

        var text: CharSequence = if (account.isMisskey) {
            baseStatus.content ?: ""
        } else {
            decodeOptions.decodeHTML(baseStatus.content)
        }
        views.etContent.setText(text)
        views.etContent.setSelection(text.length)

        text = decodeOptions.decodeEmoji(baseStatus.spoiler_text)
        views.etContentWarning.setText(text)
        views.etContentWarning.setSelection(text.length)
        views.cbContentWarning.isChecked = text.isNotEmpty()

        val srcEnquete = baseStatus.enquete
        val srcItems = srcEnquete?.items
        when {
            srcItems == null -> {
                //
            }

            srcEnquete.pollType == TootPollsType.FriendsNico &&
                    srcEnquete.type != TootPolls.TYPE_ENQUETE -> {
                // フレニコAPIのアンケート結果は再編集の対象外
            }

            else -> {
                views.spPollType.setSelection(1)
                text = decodeOptions.decodeHTML(srcEnquete.question)
                views.etContent.text = text
                views.etContent.setSelection(text.length)

                var srcIndex = 0
                for (et in etChoices) {
                    if (srcIndex < srcItems.size) {
                        val choice = srcItems[srcIndex]
                        when {
                            srcIndex == srcItems.size - 1 && choice.text == "\uD83E\uDD14" -> {
                                // :thinking_face: は再現しない
                            }

                            else -> {
                                et.setText(decodeOptions.decodeEmoji(choice.text))
                                ++srcIndex
                                continue
                            }
                        }
                    }
                    et.setText("")
                }
            }
        }
    } catch (ex: Throwable) {
        log.e(ex, "initializeFromRedraftStatus failed.")
    }
}

fun ActPost.initializeFromEditStatus(account: SavedAccount, jsonText: String) {
    try {
        val baseStatus =
            TootParser(this, account).status(jsonText.decodeJsonObject())
                ?: error("initializeFromEditStatus: parse failed.")

        states.editStatusId = baseStatus.id

        states.visibility = baseStatus.visibility

        baseStatus.media_attachments
            ?.mapNotNull { it as? TootAttachment }
            ?.notEmpty()
            ?.let { srcAttachments ->
                this.attachmentList.clear()
                for (src in srcAttachments) {
                    try {
                        src.isEdit = true
                        val pa = PostAttachment(src)
                        pa.status = PostAttachment.Status.Ok
                        this.attachmentList.add(pa)
                    } catch (ex: Throwable) {
                        log.e(ex, "can't initialize attachments from edit status")
                    }
                }
                saveAttachmentList()
            }

        views.cbNSFW.isChecked = baseStatus.sensitive == true

        // 再編集の場合はdefault_textは反映されない

        val decodeOptions = DecodeOptions(
            this,
            mentionFullAcct = true,
            mentions = baseStatus.mentions,
            linkHelper = account,
        )

        var text: CharSequence = if (account.isMisskey) {
            baseStatus.content ?: ""
        } else {
            decodeOptions.decodeHTML(baseStatus.content)
        }
        views.etContent.setText(text)
        views.etContent.setSelection(text.length)

        text = decodeOptions.decodeEmoji(baseStatus.spoiler_text)
        views.etContentWarning.setText(text)
        views.etContentWarning.setSelection(text.length)
        views.cbContentWarning.isChecked = text.isNotEmpty()

        val srcEnquete = baseStatus.enquete
        val srcItems = srcEnquete?.items
        when {
            srcItems == null -> {
                //
            }

            srcEnquete.pollType == TootPollsType.FriendsNico &&
                    srcEnquete.type != TootPolls.TYPE_ENQUETE -> {
                // フレニコAPIのアンケート結果は再編集の対象外
            }

            else -> {
                views.spPollType.setSelection(1)
                text = decodeOptions.decodeHTML(srcEnquete.question)
                views.etContent.text = text
                views.etContent.setSelection(text.length)

                var srcIndex = 0
                for (et in etChoices) {
                    if (srcIndex < srcItems.size) {
                        val choice = srcItems[srcIndex]
                        when {
                            srcIndex == srcItems.size - 1 && choice.text == "\uD83E\uDD14" -> {
                                // :thinking_face: は再現しない
                            }

                            else -> {
                                et.setText(decodeOptions.decodeEmoji(choice.text))
                                ++srcIndex
                                continue
                            }
                        }
                    }
                    et.setText("")
                }
            }
        }
    } catch (ex: Throwable) {
        log.e(ex, "initializeFromEditStatus failed.")
    }
}
