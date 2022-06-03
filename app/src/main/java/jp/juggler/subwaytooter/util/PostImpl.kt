package jp.juggler.subwaytooter.util

import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.Styler
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.DlgConfirm.confirm
import jp.juggler.subwaytooter.emoji.CustomEmoji
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.TagSet
import jp.juggler.util.*
import kotlinx.coroutines.*
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.lang.ref.WeakReference
import java.util.*

sealed class PostResult {
    class Normal(
        val targetAccount: SavedAccount,
        val status: TootStatus,
    ) : PostResult()

    class Scheduled(
        val targetAccount: SavedAccount,
    ) : PostResult()
}

@Suppress("LongParameterList")
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
    val editStatusId: EntityId?,
    val emojiMapCustom: HashMap<String, CustomEmoji>?,
    var useQuoteToot: Boolean,
    var lang: String,
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

    private val choiceMaxChars = when {
        account.isMisskey -> 15
        pollType == TootPollsType.FriendsNico -> 15
        else -> 40 // TootPollsType.Mastodon
    }

    private fun preCheckPollItemOne(list: List<String>, idx: Int, item: String) {

        // 選択肢が長すぎる
        val cpCount = item.codePointCount(0, item.length)
        if (cpCount > choiceMaxChars) {
            val over = cpCount - choiceMaxChars
            activity.errorString(R.string.enquete_item_too_long, idx + 1, over)
        }

        // 他の項目と重複している
        if ((0 until idx).any { list[it] == item }) {
            activity.errorString(R.string.enquete_item_duplicate, idx + 1)
        }
    }

    private var resultStatus: TootStatus? = null
    private var resultCredentialTmp: TootAccount? = null
    private var resultScheduledStatusSucceeded = false

    private suspend fun getCredential(
        client: TootApiClient,
        parser: TootParser,
    ): TootApiResult? {
        return client.request("/api/v1/accounts/verify_credentials")?.also { result ->
            resultCredentialTmp = parser.account(result.jsonObject)
        }
    }

    private suspend fun getWebVisibility(
        client: TootApiClient,
        parser: TootParser,
        instance: TootInstance,
    ): TootVisibility? {
        if (account.isMisskey || instance.versionGE(TootInstance.VERSION_1_6)) return null

        val r2 = getCredential(client, parser)

        val credentialTmp = resultCredentialTmp
            ?: errorApiResult(r2)

        val privacy = credentialTmp.source?.privacy
            ?: errorApiResult(activity.getString(R.string.cant_get_web_setting_visibility))

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
        errorApiResult(
            activity.getString(
                R.string.server_has_no_support_of_visibility,
                strVisibility
            )
        )
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
        checkServerHasVisibility(
            v,
            TootVisibility.Mutual,
            instance,
            InstanceCapability::visibilityMutual
        )
        checkServerHasVisibility(
            v,
            TootVisibility.Limited,
            instance,
            InstanceCapability::visibilityLimited
        )
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
                    if (r == null || r.error != null) errorApiResult(r)
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

        when (val lang = lang.trim()) {
            // Web設定に従うなら指定しない
            SavedAccount.LANG_WEB, "" -> Unit
            // 端末の言語コード
            SavedAccount.LANG_DEVICE ->
                json["language"] = Locale.getDefault().language
            // その他
            else ->
                json["language"] = lang
        }

        visibilityChecked?.let { json["visibility"] = it.strMastodon }

        json["status"] = EmojiDecoder.decodeShortCode(content, emojiMapCustom = emojiMapCustom)
        json["sensitive"] = bNSFW
        json["spoiler_text"] =
            EmojiDecoder.decodeShortCode(spoilerText ?: "", emojiMapCustom = emojiMapCustom)

        inReplyToId?.toString()
            ?.let { json[if (useQuoteToot) "quote_id" else "in_reply_to_id"] = it }

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
                errorApiResult(activity.getString(R.string.scheduled_status_requires_mastodon_2_7_0))
            }
            // UTCの日時を渡す
            val c = GregorianCalendar.getInstance(TimeZone.getTimeZone("UTC"))
            c.timeInMillis = scheduledAt
            val sv = String.format(
                Locale.JAPANESE,
                "%d-%02d-%02dT%02d:%02d:%02d.%03dZ",
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH),
                c.get(Calendar.HOUR_OF_DAY),
                c.get(Calendar.MINUTE),
                c.get(Calendar.SECOND),
                c.get(Calendar.MILLISECOND)
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

    suspend fun runSuspend(): PostResult {

        if (account.isMisskey) {
            val duplicateCheck = buildMap {
                attachmentList?.forEach {
                    put(it.id.toString(), (get(it.id.toString()) ?: 0) + 1)
                }
            }
            if (duplicateCheck.values.all { it >= 2 }) {
                activity.errorString(R.string.post_error_attachments_duplicated)
            }
        }

        if (content.isEmpty() && attachmentList == null) {
            activity.errorString(R.string.post_error_contents_empty)
        }

        // nullはCWチェックなしを示す // nullじゃなくてカラならエラー
        if (spoilerText != null && spoilerText.isEmpty()) {
            activity.errorString(R.string.post_error_contents_warning_empty)
        }

        if (!enqueteItems.isNullOrEmpty()) {
            if (enqueteItems.size < 2) {
                activity.errorString(R.string.enquete_item_is_empty, enqueteItems.size + 1)
            }
            enqueteItems.forEachIndexed { i, v ->
                preCheckPollItemOne(enqueteItems, i, v)
            }
        }

        if (scheduledAt != 0L && account.isMisskey) {
            error("misskey has no scheduled status API")
        }

        if (PrefB.bpWarnHashtagAsciiAndNonAscii()) {
            TootTag.findHashtags(content, account.isMisskey)
                ?.filter {
                    val hasAscii = reAscii.matcher(it).find()
                    val hasNotAscii = reNotAscii.matcher(it).find()
                    hasAscii && hasNotAscii
                }?.map { "#$it" }
                ?.notEmpty()
                ?.let { badTags ->
                    activity.confirm(
                        R.string.hashtag_contains_ascii_and_not_ascii,
                        badTags.joinToString(", ")
                    )
                }
        }

        val isMisskey = account.isMisskey
        if (!visibilityArg.isTagAllowed(isMisskey)) {
            TootTag.findHashtags(content, isMisskey)
                ?.notEmpty()
                ?.let { tags ->
                    log.d("findHashtags ${tags.joinToString(",")}")
                    activity.confirm(
                        R.string.hashtag_and_visibility_not_match
                    )
                }
        }

        if (redraftStatusId != null) {
            activity.confirm(R.string.delete_base_status_before_toot)
        }

        if (scheduledId != null) {
            activity.confirm(R.string.delete_scheduled_status_before_update)
        }

        activity.confirm(
            activity.getString(R.string.confirm_post_from, AcctColor.getNickname(account)),
            account.confirm_post
        ) { newConfirmEnabled ->
            account.confirm_post = newConfirmEnabled
            account.saveSetting()
        }

        // 投稿中に再度投稿ボタンが押された
        if (postJob?.get()?.isActive == true) {
            log.e("other postJob is active!")
            activity.showToast(false, R.string.post_button_tapped_repeatly)
            throw CancellationException("preCheck failed.")
        }

        // ボタン連打判定
        val now = SystemClock.elapsedRealtime()
        val delta = now - lastPostTapped
        lastPostTapped = now
        if (delta < 1000L) {
            log.e("lastPostTapped within 1 sec!")
            activity.showToast(false, R.string.post_button_tapped_repeatly)
            throw CancellationException("post_button_tapped_repeatly")
        }

        val job = Job().also { postJob = it.wrapWeakReference }
        return withContext(Dispatchers.Main + job) {

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

                if (redraftStatusId != null) {
                    // 元の投稿を削除する
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

                fun createRequestBuilder(isPut: Boolean = false): Request.Builder {
                    val requestBody = bodyString.toRequestBody(MEDIA_TYPE_JSON)
                    return when {
                        isPut -> Request.Builder().put(requestBody)
                        else -> Request.Builder().post(requestBody)
                    }.also {
                        if (!PrefB.bpDontDuplicationCheck()) {
                            val digest = (bodyString + account.acct.ascii).digestSHA256Hex()
                            it.header("Idempotency-Key", digest)
                        }
                    }
                }

                when {
                    account.isMisskey -> client.request(
                        "/api/notes/create",
                        createRequestBuilder()
                    )

                    editStatusId != null -> client.request(
                        "/api/v1/statuses/$editStatusId",
                        createRequestBuilder(isPut = true)
                    )

                    else -> client.request(
                        "/api/v1/statuses",
                        createRequestBuilder()
                    )
                }?.also { result ->
                    val jsonObject = result.jsonObject

                    if (scheduledAt != 0L && jsonObject != null) {
                        // {"id":"3","scheduled_at":"2019-01-06T07:08:00.000Z","media_attachments":[]}
                        resultScheduledStatusSucceeded = true
                        return@runApiTask result
                    } else {
                        val status = parser.status(
                            when {
                                account.isMisskey -> jsonObject?.jsonObject("createdNote")
                                    ?: jsonObject
                                else -> jsonObject
                            }
                        )
                        resultStatus = status
                        saveStatusTag(status)
                    }
                }
            }.let { result ->
                if (result == null) throw CancellationException()

                val status = resultStatus
                when {
                    resultScheduledStatusSucceeded ->
                        PostResult.Scheduled(account)

                    // 連投してIdempotency が同じだった場合もエラーにはならず、ここを通る
                    status != null ->
                        PostResult.Normal(account, status)

                    else -> error(result.error ?: "(result.error is null)")
                }
            }
        }
    }
}
