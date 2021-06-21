package jp.juggler.subwaytooter.util

import android.os.SystemClock
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.Styler
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.dialog.DlgConfirm
import jp.juggler.subwaytooter.emoji.CustomEmoji
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.TagSet
import jp.juggler.util.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.lang.Exception
import java.lang.ref.WeakReference
import java.util.*

interface PostCompleteCallback {

    fun onPostComplete(targetAccount: SavedAccount, status: TootStatus)
    fun onScheduledPostComplete(targetAccount: SavedAccount)
}

class PostImpl(
    val activity: AppCompatActivity,
    val account: SavedAccount,
    val content: String,

    // nullはCWチェックなしを示す // nullじゃなくてカラならエラー
    val spoilerText: String?,

    val visibilityArg: TootVisibility,
    val bNSFW: Boolean,
    val inReplyToId: EntityId?,
    val attachmentListArg: List<PostAttachment>?,
    enqueteItemsArg: List<String>?,
    val pollType: TootPollsType?,
    val pollExpireSeconds: Int,
    val pollHideTotals: Boolean,
    val pollMultipleChoice: Boolean,
    val scheduledAt: Long,
    val scheduledId: EntityId?,
    val redraftStatusId: EntityId?,
    val emojiMapCustom: HashMap<String, CustomEmoji>?,
    var useQuoteToot: Boolean,

    val callback: PostCompleteCallback,
) {
    companion object {
        private val log = LogCategory("PostImpl")
        private val reAscii = """[\x00-\x7f]""".asciiPattern()
        private val reNotAscii = """[^\x00-\x7f]""".asciiPattern()

        private var lastPostTapped: Long = 0L
        private var postJob: WeakReference<Job>? = null
    }

    private val attachmentList = attachmentListArg?.mapNotNull { it.attachment }?.notEmpty()

    // null だと投票を作成しない。空リストはエラー
    private val enqueteItems = enqueteItemsArg?.filter { it.isNotEmpty() }

    private var visibilityChecked: TootVisibility? = null

    var bConfirmTag: Boolean = false
    var bConfirmAccount: Boolean = false
    var bConfirmRedraft: Boolean = false
    var bConfirmTagCharacter: Boolean = false

    private val choiceMaxChars = when {
        account.isMisskey -> 15
        pollType == TootPollsType.FriendsNico -> 15
        else -> 25 // TootPollsType.Mastodon
    }

    private fun preCheckPollItemOne(list: List<String>, idx: Int, item: String): Boolean {

        // 選択肢が長すぎる
        val cpCount = item.codePointCount(0, item.length)
        if (cpCount > choiceMaxChars) {
            val over = cpCount - choiceMaxChars
            activity.showToast(true, R.string.enquete_item_too_long, idx + 1, over)
            return false
        }

        // 他の項目と重複している
        if ((0 until idx).any { list[it] == item }) {
            activity.showToast(true, R.string.enquete_item_duplicate, idx + 1)
            return false
        }

        return true
    }

    private fun preCheck(): Boolean {
        if (content.isEmpty() && attachmentList == null) {
            activity.showToast(true, R.string.post_error_contents_empty)
            return false
        }

        // nullはCWチェックなしを示す // nullじゃなくてカラならエラー
        if (spoilerText != null && spoilerText.isEmpty()) {
            activity.showToast(true, R.string.post_error_contents_warning_empty)
            return false
        }

        if (enqueteItems != null) {
            if (enqueteItems.size < 2) {
                activity.showToast(true, R.string.enquete_item_is_empty, enqueteItems.size + 1)
                return false
            }
            enqueteItems.forEachIndexed { i, v ->
                if (!preCheckPollItemOne(enqueteItems, i, v)) return false
            }
        }

        if (scheduledAt != 0L && account.isMisskey) {
            activity.showToast(true, "misskey has no scheduled status API")
            return false
        }


        return true
    }

    private fun confirm(): Boolean {
        if (!bConfirmAccount) {
            DlgConfirm.open(
                activity,
                activity.getString(R.string.confirm_post_from, AcctColor.getNickname(account)),
                object : DlgConfirm.Callback {
                    override var isConfirmEnabled: Boolean
                        get() = account.confirm_post
                        set(bv) {
                            account.confirm_post = bv
                            account.saveSetting()
                        }

                    override fun onOK() {
                        bConfirmAccount = true
                        run()
                    }
                })
            return false
        }

        if (!bConfirmTagCharacter && Pref.bpWarnHashtagAsciiAndNonAscii(App1.pref)) {
            val tags = TootTag.findHashtags(content, account.isMisskey)
            val badTags = tags
                ?.filter {
                    val hasAscii = reAscii.matcher(it).find()
                    val hasNotAscii = reNotAscii.matcher(it).find()
                    hasAscii && hasNotAscii
                }
                ?.map { "#$it" }

            if (badTags?.isNotEmpty() == true) {
                AlertDialog.Builder(activity)
                    .setCancelable(true)
                    .setMessage(
                        activity.getString(
                            R.string.hashtag_contains_ascii_and_not_ascii,
                            badTags.joinToString(", ")
                        )
                    )
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        bConfirmTagCharacter = true
                        run()
                    }
                    .show()
                return false
            }
        }

        if (!bConfirmTag) {
            val isMisskey = account.isMisskey
            if (!visibilityArg.isTagAllowed(isMisskey)) {
                val tags = TootTag.findHashtags(content, isMisskey)
                if (tags != null) {
                    log.d("findHashtags ${tags.joinToString(",")}")

                    AlertDialog.Builder(activity)
                        .setCancelable(true)
                        .setMessage(R.string.hashtag_and_visibility_not_match)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            bConfirmTag = true
                            run()
                        }
                        .show()
                    return false
                }
            }
        }

        if (!bConfirmRedraft && redraftStatusId != null) {
            AlertDialog.Builder(activity)
                .setCancelable(true)
                .setMessage(R.string.delete_base_status_before_toot)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ ->
                    bConfirmRedraft = true
                    run()
                }
                .show()
            return false
        }

        if (!bConfirmRedraft && scheduledId != null) {
            AlertDialog.Builder(activity)
                .setCancelable(true)
                .setMessage(R.string.delete_scheduled_status_before_update)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ ->
                    bConfirmRedraft = true
                    run()
                }
                .show()
            return false
        }

        return true
    }

    var resultStatus: TootStatus? = null
    var resultCredentialTmp: TootAccount? = null
    var resultScheduledStatusSucceeded = false

    private suspend fun getCredential(
        client: TootApiClient,
        parser: TootParser,
    ): TootApiResult? {
        return client.request("/api/v1/accounts/verify_credentials")?.also { result ->
            resultCredentialTmp = parser.account(result.jsonObject)
        }
    }

    private class TootApiResultException(val result: TootApiResult?) : Exception(result?.error ?: "cancelled.") {
        constructor(error: String) : this(TootApiResult(error))
    }

    private suspend fun getWebVisibility(
        client: TootApiClient,
        parser: TootParser,
        instance: TootInstance,
    ): TootVisibility? {
        if (account.isMisskey || instance.versionGE(TootInstance.VERSION_1_6)) return null

        val r2 = getCredential(client, parser)

        val credentialTmp = resultCredentialTmp
            ?: throw TootApiResultException(r2)

        val privacy = credentialTmp.source?.privacy
            ?: throw TootApiResultException(activity.getString(R.string.cant_get_web_setting_visibility))

        return TootVisibility.parseMastodon(privacy)
        // may null, not error
    }

    private fun checkServerHasVisibility(
        actual: TootVisibility?,
        extra: TootVisibility,
        instance: TootInstance,
        checkFun: (TootInstance) -> Boolean,
    ) {
        if (actual != extra || checkFun(instance)) return
        val strVisibility = Styler.getVisibilityString(activity, account.isMisskey, extra)
        throw TootApiResultException(activity.getString(R.string.server_has_no_support_of_visibility, strVisibility))
    }

    private suspend fun checkVisibility(
        client: TootApiClient,
        parser: TootParser,
        instance: TootInstance,
    ): TootVisibility? {
        val v = when (visibilityArg) {
            TootVisibility.WebSetting -> getWebVisibility(client, parser, instance)
            else -> visibilityArg
        }
        checkServerHasVisibility(v, TootVisibility.Mutual, instance, InstanceCapability::visibilityMutual)
        checkServerHasVisibility(v, TootVisibility.Limited, instance, InstanceCapability::visibilityLimited)
        return v
    }

    private suspend fun deleteStatus(client: TootApiClient) {
        val result = if (account.isMisskey) {
            client.request(
                "/api/notes/delete",
                account.putMisskeyApiToken(JsonObject()).apply {
                    put("noteId", redraftStatusId)
                }.toPostRequestBuilder()
            )
        } else {
            client.request(
                "/api/v1/statuses/$redraftStatusId",
                Request.Builder().delete()
            )
        }
        log.d("delete redraft. result=$result")
        delay(2000L)
    }

    private suspend fun deleteScheduledStatus(client: TootApiClient) {
        val result = client.request(
            "/api/v1/scheduled_statuses/$scheduledId",
            Request.Builder().delete()
        )

        log.d("delete old scheduled status. result=$result")
        delay(2000L)
    }

    private suspend fun encodeParamsMisskey(json: JsonObject, client: TootApiClient) {
        account.putMisskeyApiToken(json)

        json["viaMobile"] = true

        json["text"] = EmojiDecoder.decodeShortCode(content, emojiMapCustom = emojiMapCustom)

        spoilerText?.notEmpty()?.let {
            json["cw"] = EmojiDecoder.decodeShortCode(it, emojiMapCustom = emojiMapCustom)
        }

        inReplyToId?.toString()?.let {
            json[if (useQuoteToot) "renoteId" else "replyId"] = it
        }

        when (val visibility = visibilityChecked) {
            null -> Unit
            TootVisibility.DirectSpecified, TootVisibility.DirectPrivate -> {
                val userIds = JsonArray()
                val m = TootAccount.reMisskeyMentionPost.matcher(content)
                while (m.find()) {
                    val username = m.groupEx(1)
                    val host = m.groupEx(2) // may null

                    client.request(
                        "/api/users/show",
                        account.putMisskeyApiToken().apply {
                            username.notEmpty()?.let { put("username", it) }
                            host.notEmpty()?.let { put("host", it) }
                        }.toPostRequestBuilder()
                    )?.let { result ->
                        result.jsonObject?.string("id").notEmpty()?.let { userIds.add(it) }
                    }
                }
                json["visibility"] = when {
                    userIds.isNotEmpty() -> {
                        json["visibleUserIds"] = userIds
                        "specified"
                    }
                    account.misskeyVersion >= 11 -> "specified"
                    else -> "private"
                }
            }

            else -> {
                val localVis = visibility.strMisskey.replace("^local-".toRegex(), "")
                if (localVis != visibility.strMisskey) json["localOnly"] = true
                json["visibility"] = localVis
            }
        }

        if (attachmentList != null) {
            val array = JsonArray()
            for (a in attachmentList) {

                // Misskeyは画像の再利用に問題がないので redraftとバージョンのチェックは行わない
                array.add(a.id.toString())

                // Misskeyの場合、NSFWするにはアップロード済みの画像を drive/files/update で更新する
                if (bNSFW) {
                    val r = client.request(
                        "/api/drive/files/update",
                        account.putMisskeyApiToken().apply {
                            put("fileId", a.id.toString())
                            put("isSensitive", true)
                        }
                            .toPostRequestBuilder()
                    )
                    if (r == null || r.error != null) throw TootApiResultException(r)
                }
            }
            if (array.isNotEmpty()) json["mediaIds"] = array
        }

        if (enqueteItems?.isNotEmpty() == true) {
            val choices = JsonArray().apply {
                for (item in enqueteItems) {
                    val text = EmojiDecoder.decodeShortCode(item, emojiMapCustom = emojiMapCustom)
                    if (text.isEmpty()) continue
                    add(text)
                }
            }
            if (choices.isNotEmpty()) {
                json["poll"] = jsonObjectOf("choices" to choices)
            }
        }
    }

    private fun encodeParamsMastodon(json: JsonObject, instance: TootInstance) {
        visibilityChecked?.let { json["visibility"] = it.strMastodon }

        json["status"] = EmojiDecoder.decodeShortCode(content, emojiMapCustom = emojiMapCustom)
        json["sensitive"] = bNSFW
        json["spoiler_text"] = EmojiDecoder.decodeShortCode(spoilerText ?: "", emojiMapCustom = emojiMapCustom)

        inReplyToId?.toString()?.let { json[if (useQuoteToot) "quote_id" else "in_reply_to_id"] = it }

        if (attachmentList != null) {
            json["media_ids"] = jsonArray {
                for (a in attachmentList) {
                    if (a.redraft && !instance.versionGE(TootInstance.VERSION_2_4_1)) continue
                    add(a.id.toString())
                }
            }
        }

        if (enqueteItems != null) {
            if (pollType == TootPollsType.Mastodon) {
                json["poll"] = jsonObject {
                    put("multiple", pollMultipleChoice)
                    put("hide_totals", pollHideTotals)
                    put("expires_in", pollExpireSeconds)
                    put("options", enqueteItems.map {
                        EmojiDecoder.decodeShortCode(it, emojiMapCustom = emojiMapCustom)
                    }.toJsonArray())
                }
            } else {
                json["isEnquete"] = true
                json["enquete_items"] = enqueteItems.map {
                    EmojiDecoder.decodeShortCode(it, emojiMapCustom = emojiMapCustom)
                }.toJsonArray()
            }
        }

        if (scheduledAt != 0L) {
            if (!instance.versionGE(TootInstance.VERSION_2_7_0_rc1)) {
                throw TootApiResultException(activity.getString(R.string.scheduled_status_requires_mastodon_2_7_0))
            }
            // UTCの日時を渡す
            val c = GregorianCalendar.getInstance(TimeZone.getTimeZone("UTC"))
            c.timeInMillis = scheduledAt
            val sv = String.format(
                Locale.JAPANESE,
                "%d-%02d-%02d %02d:%02d:%02d",
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH),
                c.get(Calendar.HOUR_OF_DAY),
                c.get(Calendar.MINUTE),
                c.get(Calendar.SECOND)
            )
            json["scheduled_at"] = sv
        }
    }

    private fun saveStatusTag(status: TootStatus?) {
        status ?: return

        val s = status.decoded_content
        val spanList = s.getSpans(0, s.length, MyClickableSpan::class.java)
        if (spanList != null) {
            val tagList = ArrayList<String?>(spanList.size)
            for (span in spanList) {
                val start = s.getSpanStart(span)
                val end = s.getSpanEnd(span)
                val text = s.subSequence(start, end).toString()
                if (text.startsWith("#")) {
                    tagList.add(text.substring(1))
                }
            }
            val count = tagList.size
            if (count > 0) {
                TagSet.saveList(System.currentTimeMillis(), tagList, 0, count)
            }
        }
    }

    fun run() {
        if (!preCheck()) return
        if (!confirm()) return

        // 投稿中に再度投稿ボタンが押された
        if (postJob?.get()?.isActive == true) {
            activity.showToast(false, R.string.post_button_tapped_repeatly)
            return
        }

        // ボタン連打判定
        val now = SystemClock.elapsedRealtime()
        val delta = now - lastPostTapped
        lastPostTapped = now
        if (delta < 1000L) {
            activity.showToast(false, R.string.post_button_tapped_repeatly)
            return
        }

        postJob = launchMain {
            activity.runApiTask(
                account,
                progressSetup = { it.setCanceledOnTouchOutside(false) },
            ) { client ->
                // 全ての確認を終えたらバックグラウンドでの処理を開始する

                val (instance, ri) = TootInstance.get(client)
                instance ?: return@runApiTask ri

                if (instance.instanceType == InstanceType.Pixelfed) {
                    // Pixelfedは返信に画像を添付できない
                    if (inReplyToId != null && attachmentList != null) {
                        return@runApiTask TootApiResult(getString(R.string.pixelfed_does_not_allow_reply_with_media))
                    }

                    // Pixelfedの返信ではない投稿は画像添付が必須
                    if (inReplyToId == null && attachmentList == null) {
                        return@runApiTask TootApiResult(getString(R.string.pixelfed_does_not_allow_post_without_media))
                    }
                }

                val parser = TootParser(this, account)

                this@PostImpl.visibilityChecked = try {
                    checkVisibility(client, parser, instance) // may null
                } catch (ex: TootApiResultException) {
                    return@runApiTask ex.result
                }

                // 元の投稿を削除する
                if (redraftStatusId != null) {
                    deleteStatus(client)
                } else if (scheduledId != null) {
                    deleteScheduledStatus(client)
                }

                val json = JsonObject()
                try {
                    if (account.isMisskey) {
                        encodeParamsMisskey(json, client)
                    } else {
                        encodeParamsMastodon(json, instance)
                    }
                } catch (ex: TootApiResultException) {
                    return@runApiTask ex.result
                } catch (ex: JsonException) {
                    log.trace(ex)
                    log.e(ex, "status encoding failed.")
                }

                val bodyString = json.toString()

                val requestBuilder = bodyString.toRequestBody(MEDIA_TYPE_JSON).toPost()

                if (!Pref.bpDontDuplicationCheck(App1.pref)) {
                    val digest = (bodyString + account.acct.ascii).digestSHA256Hex()
                    requestBuilder.header("Idempotency-Key", digest)
                }

                if (account.isMisskey) {
                    client.request("/api/notes/create", requestBuilder)
                } else {
                    client.request("/api/v1/statuses", requestBuilder)
                }?.also { result ->
                    val jsonObject = result.jsonObject

                    if (scheduledAt != 0L && jsonObject != null) {
                        // {"id":"3","scheduled_at":"2019-01-06T07:08:00.000Z","media_attachments":[]}
                        resultScheduledStatusSucceeded = true
                        return@runApiTask result
                    } else {
                        val status = parser.status(
                            when {
                                account.isMisskey -> jsonObject?.jsonObject("createdNote") ?: jsonObject
                                else -> jsonObject
                            }
                        )
                        resultStatus = status
                        saveStatusTag(status)
                    }
                }
            }?.let { result ->
                val status = resultStatus
                val scheduledStatusSucceeded = resultScheduledStatusSucceeded
                when {
                    scheduledStatusSucceeded ->
                        callback.onScheduledPostComplete(account)

                    // 連投してIdempotency が同じだった場合もエラーにはならず、ここを通る
                    status != null -> callback.onPostComplete(account, status)

                    else -> activity.showToast(true, result.error)
                }
            }
        }.wrapWeakReference
    }
}
