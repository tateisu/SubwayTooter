package jp.juggler.subwaytooter.util

import android.content.SharedPreferences
import android.os.Handler
import android.os.SystemClock
import android.text.*
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.Styler
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.dialog.DlgConfirm
import jp.juggler.subwaytooter.dialog.EmojiPicker
import jp.juggler.subwaytooter.dialog.EmojiPickerResult
import jp.juggler.subwaytooter.emoji.CustomEmoji
import jp.juggler.subwaytooter.emoji.EmojiBase
import jp.juggler.subwaytooter.emoji.UnicodeEmoji
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.span.NetworkEmojiSpan
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.AcctSet
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.TagSet
import jp.juggler.subwaytooter.view.MyEditText
import jp.juggler.util.*
import kotlinx.coroutines.Job
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.lang.ref.WeakReference
import java.util.*
import kotlin.math.min

class PostHelper(
    private val activity: AppCompatActivity,
    private val pref: SharedPreferences,
    private val handler: Handler,
) {

    companion object {

        private val log = LogCategory("PostHelper")

        private val reCharsNotEmoji = "[^0-9A-Za-z_-]".asciiPattern()
        private val reAscii = """[\x00-\x7f]""".asciiPattern()
        private val reNotAscii = """[^\x00-\x7f]""".asciiPattern()
    }

    interface PostCompleteCallback {

        fun onPostComplete(targetAccount: SavedAccount, status: TootStatus)
        fun onScheduledPostComplete(targetAccount: SavedAccount)
    }

    ///////////////////////////////////////////////////////////////////////////////////
    // 投稿機能

    var content: String? = null
    var spoilerText: String? = null
    var visibility: TootVisibility = TootVisibility.Public
    var bNSFW = false
    var inReplyToId: EntityId? = null
    var attachmentList: ArrayList<PostAttachment>? = null
    var enqueteItems: ArrayList<String>? = null
    var pollType: TootPollsType? = null
    var pollExpireSeconds = 0
    var pollHideTotals = false
    var pollMultipleChoice = false

    var emojiMapCustom: HashMap<String, CustomEmoji>? = null
    var redraftStatusId: EntityId? = null
    var useQuoteToot = false
    var scheduledAt = 0L
    var scheduledId: EntityId? = null

    private var lastPostTapped: Long = 0L

    private var postJob: WeakReference<Job>? = null

    fun post(account: SavedAccount, callback: PostCompleteCallback) = post(
        account,
        callback,
        bConfirmTag = false,
        bConfirmAccount = false,
        bConfirmRedraft = false,
        bConfirmTagCharacter = false
    )

    fun post(
        account: SavedAccount,
        callback: PostCompleteCallback,
        bConfirmTag: Boolean,
        bConfirmAccount: Boolean,
        bConfirmRedraft: Boolean,
        bConfirmTagCharacter: Boolean,
    ) {
        val content = this.content ?: ""
        val spoilerText = this.spoilerText
        val bNSFW = this.bNSFW
        val inReplyToId = this.inReplyToId
        val attachmentList = this.attachmentList
        val enqueteItems = this.enqueteItems
        val pollType = this.pollType
        val pollExpireSeconds = this.pollExpireSeconds
        val pollHideTotals = this.pollHideTotals
        val pollMultipleChoice = this.pollMultipleChoice

        val visibility = this.visibility
        val scheduledAt = this.scheduledAt

        val hasAttachment = attachmentList?.isNotEmpty() ?: false

        if (!hasAttachment && content.isEmpty()) {
            activity.showToast(true, R.string.post_error_contents_empty)
            return
        }

        // nullはCWチェックなしを示す
        // nullじゃなくてカラならエラー
        if (spoilerText != null && spoilerText.isEmpty()) {
            activity.showToast(true, R.string.post_error_contents_warning_empty)
            return
        }

        if (enqueteItems?.isNotEmpty() == true) {

            val choiceMaxChars = when {
                account.isMisskey -> 15
                pollType == TootPollsType.FriendsNico -> 15
                else -> 25 // TootPollsType.Mastodon
            }

            for (n in 0 until enqueteItems.size) {
                val item = enqueteItems[n]

                if (item.isEmpty()) {
                    if (n < 2) {
                        activity.showToast(true, R.string.enquete_item_is_empty, n + 1)
                        return
                    }
                } else {
                    val cpCount = item.codePointCount(0, item.length)
                    if (cpCount > choiceMaxChars) {
                        val over = cpCount - choiceMaxChars
                        activity.showToast(true, R.string.enquete_item_too_long, n + 1, over)
                        return
                    } else if (n > 0) {
                        for (i in 0 until n) {
                            if (item == enqueteItems[i]) {
                                activity.showToast(true, R.string.enquete_item_duplicate, n + 1)
                                return
                            }
                        }
                    }
                }
            }
        }

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
                        post(
                            account, callback,
                            bConfirmTag = bConfirmTag,
                            bConfirmAccount = true,
                            bConfirmRedraft = bConfirmRedraft,
                            bConfirmTagCharacter = bConfirmTagCharacter
                        )
                    }
                })
            return
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
                        post(
                            account, callback,
                            bConfirmTag = bConfirmTag,
                            bConfirmAccount = bConfirmAccount,
                            bConfirmRedraft = bConfirmRedraft,
                            bConfirmTagCharacter = true
                        )
                    }
                    .show()
                return
            }
        }

        if (!bConfirmTag) {
            val isMisskey = account.isMisskey
            if (!visibility.isTagAllowed(isMisskey)) {
                val tags = TootTag.findHashtags(content, isMisskey)
                if (tags != null) {

                    log.d("findHashtags ${tags.joinToString(",")}")

                    AlertDialog.Builder(activity)
                        .setCancelable(true)
                        .setMessage(R.string.hashtag_and_visibility_not_match)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            post(
                                account, callback,
                                bConfirmTag = true,
                                bConfirmAccount = bConfirmAccount,
                                bConfirmRedraft = bConfirmRedraft,
                                bConfirmTagCharacter = bConfirmTagCharacter
                            )
                        }
                        .show()
                    return
                }
            }
        }

        if (!bConfirmRedraft && redraftStatusId != null) {
            AlertDialog.Builder(activity)
                .setCancelable(true)
                .setMessage(R.string.delete_base_status_before_toot)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ ->
                    post(
                        account, callback,
                        bConfirmTag = bConfirmTag,
                        bConfirmAccount = bConfirmAccount,
                        bConfirmRedraft = true,
                        bConfirmTagCharacter = bConfirmTagCharacter
                    )
                }
                .show()
            return
        }
        if (!bConfirmRedraft && scheduledId != null) {
            AlertDialog.Builder(activity)
                .setCancelable(true)
                .setMessage(R.string.delete_scheduled_status_before_update)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ ->
                    post(
                        account, callback,
                        bConfirmTag = bConfirmTag,
                        bConfirmAccount = bConfirmAccount,
                        bConfirmRedraft = true,
                        bConfirmTagCharacter = bConfirmTagCharacter
                    )
                }
                .show()
            return
        }

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

            var resultStatus: TootStatus? = null
            var resultCredentialTmp: TootAccount? = null
            var resultScheduledStatusSucceeded = false

            suspend fun getCredential(
                client: TootApiClient,
                parser: TootParser,
            ): TootApiResult? {
                val result = client.request("/api/v1/accounts/verify_credentials")
                resultCredentialTmp = parser.account(result?.jsonObject)
                return result
            }

            // 全ての確認を終えたらバックグラウンドでの処理を開始する
            activity.runApiTask(
                account,
                progressSetup = { it.setCanceledOnTouchOutside(false) },
            ) { client ->

                val (instance, ri) = TootInstance.get(client)
                instance ?: return@runApiTask ri

                val parser = TootParser(this, account)

                var visibilityChecked: TootVisibility? = visibility
                if (visibility == TootVisibility.WebSetting) {
                    visibilityChecked = when {
                        account.isMisskey || instance.versionGE(TootInstance.VERSION_1_6) -> null
                        else -> {
                            val r2 = getCredential(client, parser)

                            val credentialTmp = resultCredentialTmp
                                ?: return@runApiTask r2

                            val privacy = credentialTmp.source?.privacy
                                ?: return@runApiTask TootApiResult(getString(R.string.cant_get_web_setting_visibility))

                            TootVisibility.parseMastodon(privacy)
                        }
                    }
                }

                for (pair in arrayOf(
                    Pair(TootVisibility.Mutual, InstanceCapability::visibilityMutual),
                    Pair(TootVisibility.Limited, InstanceCapability::visibilityLimited),
                )) {
                    val (checkVisibility, checkFun) = pair
                    if (visibility == checkVisibility && !checkFun(instance)) {
                        val strVisibility = Styler.getVisibilityString(activity, account.isMisskey, checkVisibility)
                        return@runApiTask TootApiResult(
                            getString(R.string.server_has_no_support_of_visibility, strVisibility)
                        )
                    }
                }

                // 元の投稿を削除する
                var result: TootApiResult?
                if (redraftStatusId != null) {
                    result = if (account.isMisskey) {
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
                    Thread.sleep(2000L)
                } else if (scheduledId != null) {
                    val r1 = client.request(
                        "/api/v1/scheduled_statuses/$scheduledId",
                        Request.Builder().delete()
                    )

                    log.d("delete old scheduled status. result=$r1")
                    Thread.sleep(2000L)
                }

                if (instance.instanceType == InstanceType.Pixelfed) {

                    // Pixelfedは返信に画像を添付できない
                    if (inReplyToId != null && attachmentList?.isNotEmpty() == true) {
                        return@runApiTask TootApiResult(getString(R.string.pixelfed_does_not_allow_reply_with_media))
                    }

                    // Pixelfedの返信ではない投稿は画像添付が必須
                    if (inReplyToId == null && attachmentList?.isNotEmpty() != true) {
                        return@runApiTask TootApiResult(getString(R.string.pixelfed_does_not_allow_post_without_media))
                    }
                }

                val json = JsonObject()
                try {
                    if (account.isMisskey) {
                        account.putMisskeyApiToken(json)
                        json["text"] = EmojiDecoder.decodeShortCode(
                            content,
                            emojiMapCustom = emojiMapCustom
                        )
                        if (visibilityChecked != null) {
                            if (visibilityChecked == TootVisibility.DirectSpecified ||
                                visibilityChecked == TootVisibility.DirectPrivate
                            ) {
                                val userIds = JsonArray()

                                val m = TootAccount.reMisskeyMentionPost.matcher(content)
                                while (m.find()) {
                                    val username = m.groupEx(1)
                                    val host = m.groupEx(2) // may null

                                    result = client.request(
                                        "/api/users/show",
                                        account.putMisskeyApiToken().apply {
                                            username.notEmpty()?.let { put("username", it) }
                                            host.notEmpty()?.let { put("host", it) }
                                        }.toPostRequestBuilder()
                                    )
                                    result?.jsonObject?.string("id").notEmpty()?.let { userIds.add(it) }
                                }
                                json["visibility"] = when {
                                    userIds.isNotEmpty() -> {
                                        json["visibleUserIds"] = userIds
                                        "specified"
                                    }

                                    account.misskeyVersion >= 11 -> "specified"
                                    else -> "private"
                                }
                            } else {
                                val localVis = visibilityChecked.strMisskey.replace(
                                    "^local-".toRegex(),
                                    ""
                                )
                                if (localVis != visibilityChecked.strMisskey) {
                                    json["localOnly"] = true
                                    json["visibility"] = localVis
                                } else {
                                    json["visibility"] = visibilityChecked.strMisskey
                                }
                            }
                        }

                        if (spoilerText?.isNotEmpty() == true) {
                            json["cw"] = EmojiDecoder.decodeShortCode(
                                spoilerText,
                                emojiMapCustom = emojiMapCustom
                            )
                        }

                        if (inReplyToId != null) {
                            if (useQuoteToot) {
                                json["renoteId"] = inReplyToId.toString()
                            } else {
                                json["replyId"] = inReplyToId.toString()
                            }
                        }

                        json["viaMobile"] = true

                        if (attachmentList != null) {
                            val array = JsonArray()
                            for (pa in attachmentList) {
                                val a = pa.attachment ?: continue
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
                                    if (r == null || r.error != null) return@runApiTask r
                                }
                            }
                            if (array.isNotEmpty()) json["mediaIds"] = array
                        }

                        if (enqueteItems?.isNotEmpty() == true) {
                            val choices = JsonArray().apply {
                                for (item in enqueteItems) {
                                    val text = EmojiDecoder.decodeShortCode(
                                        item,
                                        emojiMapCustom = emojiMapCustom
                                    )
                                    if (text.isEmpty()) continue
                                    add(text)
                                }
                            }
                            if (choices.isNotEmpty()) {
                                json["poll"] = jsonObject {
                                    put("choices", choices)
                                }
                            }
                        }

                        if (scheduledAt != 0L) {
                            return@runApiTask TootApiResult("misskey has no scheduled status API")
                        }
                    } else {
                        json["status"] = EmojiDecoder.decodeShortCode(
                            content,
                            emojiMapCustom = emojiMapCustom
                        )
                        if (visibilityChecked != null) {
                            json["visibility"] = visibilityChecked.strMastodon
                        }
                        json["sensitive"] = bNSFW
                        json["spoiler_text"] = EmojiDecoder.decodeShortCode(
                            spoilerText ?: "",
                            emojiMapCustom = emojiMapCustom
                        )

                        if (inReplyToId != null) {
                            if (useQuoteToot) {
                                json["quote_id"] = inReplyToId.toString()
                            } else {
                                json["in_reply_to_id"] = inReplyToId.toString()
                            }
                        }

                        if (attachmentList != null) {
                            json["media_ids"] = jsonArray {
                                for (pa in attachmentList) {
                                    val a = pa.attachment ?: continue
                                    if (a.redraft && !instance.versionGE(TootInstance.VERSION_2_4_1)) continue
                                    add(a.id.toString())
                                }
                            }
                        }

                        if (enqueteItems?.isNotEmpty() == true) {
                            if (pollType == TootPollsType.Mastodon) {
                                json["poll"] = jsonObject {
                                    put("multiple", pollMultipleChoice)
                                    put("hide_totals", pollHideTotals)
                                    put("expires_in", pollExpireSeconds)
                                    put("options",
                                        enqueteItems.map {
                                            EmojiDecoder.decodeShortCode(
                                                it,
                                                emojiMapCustom = emojiMapCustom
                                            )
                                        }
                                            .toJsonArray()
                                    )
                                }
                            } else {
                                json["isEnquete"] = true
                                json["enquete_items"] = enqueteItems.map {
                                    EmojiDecoder.decodeShortCode(
                                        it,
                                        emojiMapCustom = emojiMapCustom
                                    )
                                }.toJsonArray()
                            }
                        }

                        if (scheduledAt != 0L) {
                            if (!instance.versionGE(TootInstance.VERSION_2_7_0_rc1)) {
                                return@runApiTask TootApiResult(activity.getString(R.string.scheduled_status_requires_mastodon_2_7_0))
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
                } catch (ex: JsonException) {
                    log.trace(ex)
                    log.e(ex, "status encoding failed.")
                }

                val bodyString = json.toString()

                val requestBuilder = bodyString.toRequestBody(MEDIA_TYPE_JSON).toPost()

                if (!Pref.bpDontDuplicationCheck(pref)) {
                    val digest = (bodyString + account.acct.ascii).digestSHA256Hex()
                    requestBuilder.header("Idempotency-Key", digest)
                }

                result = if (account.isMisskey) {
                    // log.d("misskey json %s", body_string)
                    client.request("/api/notes/create", requestBuilder)
                } else {
                    client.request("/api/v1/statuses", requestBuilder)
                }

                val jsonObject = result?.jsonObject

                if (scheduledAt != 0L && jsonObject != null) {
                    // {"id":"3","scheduled_at":"2019-01-06T07:08:00.000Z","media_attachments":[]}
                    resultScheduledStatusSucceeded = true
                    return@runApiTask result
                }

                val status = parser.status(
                    if (account.isMisskey) {
                        result?.jsonObject?.jsonObject("createdNote") ?: result?.jsonObject
                    } else {
                        result?.jsonObject
                    }
                )
                resultStatus = status
                if (status != null) {
                    // タグを覚えておく
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
                result
            }?.let { result ->
                val status = resultStatus
                val scheduledStatusSucceeded = resultScheduledStatusSucceeded
                when {
                    // 連投してIdempotency が同じだった場合もエラーにはならず、ここを通る
                    status != null ->
                        callback.onPostComplete(account, status)

                    scheduledStatusSucceeded ->
                        callback.onScheduledPostComplete(account)

                    else ->
                        activity.showToast(true, result.error)
                }
            }
        }.wrapWeakReference
    }

    ///////////////////////////////////////////////////////////////////////////////////
    // 入力補完機能

    private val pickerCaptionEmoji: String by lazy {
        activity.getString(R.string.open_picker_emoji)
    }
    //	private val picker_caption_tag : String by lazy {
    //		activity.getString(R.string.open_picker_tag)
    //	}
    //	private val picker_caption_mention : String by lazy {
    //		activity.getString(R.string.open_picker_mention)
    //	}

    private var callback2: Callback2? = null
    private var et: MyEditText? = null
    private var popup: PopupAutoCompleteAcct? = null
    private var formRoot: View? = null
    private var bMainScreen: Boolean = false

    private var accessInfo: SavedAccount? = null

    private val onEmojiListLoad: (list: ArrayList<CustomEmoji>) -> Unit =
        {
            val popup = this@PostHelper.popup
            if (popup?.isShowing == true) procTextChanged.run()
        }

    private val procTextChanged = object : Runnable {

        override fun run() {
            val et = this@PostHelper.et
            if (et == null || // EditTextを特定できない
                et.selectionStart != et.selectionEnd || // 範囲選択中
                callback2?.canOpenPopup() != true // 何らかの理由でポップアップが許可されていない
            ) {
                closeAcctPopup()
                return
            }

            checkMention(et, et.text.toString())
        }

        private fun checkMention(et: MyEditText, src: String) {

            fun matchUserNameOrAsciiDomain(cp: Int): Boolean {
                if (cp >= 0x7f) return false
                val c = cp.toChar()

                return '0' <= c && c <= '9' ||
                    'A' <= c && c <= 'Z' ||
                    'a' <= c && c <= 'z' ||
                    c == '_' || c == '-' || c == '.'
            }

            // Letter | Mark | Decimal_Number | Connector_Punctuation
            fun matchIdnWord(cp: Int) = when (Character.getType(cp).toByte()) {
                // Letter
                // LCはエイリアスなので文字から得られることはないはず
                Character.UPPERCASE_LETTER,
                Character.LOWERCASE_LETTER,
                Character.TITLECASE_LETTER,
                Character.MODIFIER_LETTER,
                Character.OTHER_LETTER,
                -> true
                // Mark
                Character.NON_SPACING_MARK,
                Character.COMBINING_SPACING_MARK,
                Character.ENCLOSING_MARK,
                -> true
                // Decimal_Number
                Character.DECIMAL_DIGIT_NUMBER -> true
                // Connector_Punctuation
                Character.CONNECTOR_PUNCTUATION -> true

                else -> false
            }

            var count_atMark = 0
            val end = et.selectionEnd
            var start: Int = -1
            var i = end
            while (i > 0) {
                val cp = src.codePointBefore(i)
                i -= Character.charCount(cp)

                if (cp == '@'.code) {
                    start = i
                    if (++count_atMark >= 2) break else continue
                } else if (count_atMark == 1) {
                    // @username@host の username部分はUnicodeを含まない
                    if (matchUserNameOrAsciiDomain(cp)) continue else break
                } else {
                    // @username@host のhost 部分か、 @username のusername部分
                    // ここはUnicodeを含むかもしれない
                    if (matchUserNameOrAsciiDomain(cp) || matchIdnWord(cp)) continue else break
                }
            }

            if (start == -1) {
                checkTag(et, src)
                return
            }

            // 最低でも2文字ないと補完しない
            if (end - start < 2) {
                closeAcctPopup()
                return
            }

            val limit = 100
            val s = src.substring(start, end)
            val acct_list = AcctSet.searchPrefix(s, limit)
            log.d("search for $s, result=${acct_list.size}")
            if (acct_list.isEmpty()) {
                closeAcctPopup()
            } else {
                openPopup()?.setList(et, start, end, acct_list, null, null)
            }
        }

        private fun checkTag(et: MyEditText, src: String) {

            val end = et.selectionEnd

            val last_sharp = src.lastIndexOf('#', end - 1)

            if (last_sharp == -1 || end - last_sharp < 2) {
                checkEmoji(et, src)
                return
            }

            val part = src.substring(last_sharp + 1, end)
            if (!TootTag.isValid(part, accessInfo?.isMisskey == true)) {
                checkEmoji(et, src)
                return
            }

            val limit = 100
            val s = src.substring(last_sharp + 1, end)
            val tag_list = TagSet.searchPrefix(s, limit)
            log.d("search for $s, result=${tag_list.size}")
            if (tag_list.isEmpty()) {
                closeAcctPopup()
            } else {
                openPopup()?.setList(et, last_sharp, end, tag_list, null, null)
            }
        }

        private fun checkEmoji(et: MyEditText, src: String) {

            val end = et.selectionEnd
            val last_colon = src.lastIndexOf(':', end - 1)
            if (last_colon == -1 || end - last_colon < 1) {
                closeAcctPopup()
                return
            }

            if (!EmojiDecoder.canStartShortCode(src, last_colon)) {
                // : の手前は始端か改行か空白でなければならない
                log.d("checkEmoji: invalid character before shortcode.")
                closeAcctPopup()
                return
            }

            val part = src.substring(last_colon + 1, end)

            if (part.isEmpty()) {
                // :を入力した直後は候補は0で、「閉じる」と「絵文字を選ぶ」だけが表示されたポップアップを出す
                openPopup()?.setList(
                    et, last_colon, end, null, pickerCaptionEmoji, openPickerEmoji
                )
                return
            }

            if (reCharsNotEmoji.matcher(part).find()) {
                // 範囲内に絵文字に使えない文字がある
                closeAcctPopup()
                return
            }

            val code_list = ArrayList<CharSequence>()
            val limit = 100

            // カスタム絵文字の候補を部分一致検索
            code_list.addAll(customEmojiCodeList(accessInfo, limit, part))

            // 通常の絵文字を部分一致で検索
            val remain = limit - code_list.size
            if (remain > 0) {
                val s =
                    src.substring(last_colon + 1, end).lowercase().replace('-', '_')
                val matches = EmojiDecoder.searchShortCode(activity, s, remain)
                log.d("checkEmoji: search for $s, result=${matches.size}")
                code_list.addAll(matches)
            }

            openPopup()?.setList(
                et,
                last_colon,
                end,
                code_list,
                pickerCaptionEmoji,
                openPickerEmoji
            )
        }

        // カスタム絵文字の候補を作る
        private fun customEmojiCodeList(
            accessInfo: SavedAccount?,
            @Suppress("SameParameterValue") limit: Int,
            needle: String,
        ) = ArrayList<CharSequence>().also { dst ->

            accessInfo ?: return@also

            val custom_list =
                App1.custom_emoji_lister.getListWithAliases(accessInfo, onEmojiListLoad)
                    ?: return@also

            for (item in custom_list) {
                if (dst.size >= limit) break
                if (!item.shortcode.contains(needle)) continue

                val sb = SpannableStringBuilder()
                sb.append(' ')
                sb.setSpan(
                    NetworkEmojiSpan(item.url),
                    0,
                    sb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                sb.append(' ')
                if (item.alias != null) {
                    val start = sb.length
                    sb.append(":")
                    sb.append(item.alias)
                    sb.append(": → ")
                    sb.setSpan(
                        ForegroundColorSpan(activity.attrColor(R.attr.colorTimeSmall)),
                        start,
                        sb.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                sb.append(':')
                sb.append(item.shortcode)
                sb.append(':')

                dst.add(sb)
            }
        }
    }

    private fun openPopup(): PopupAutoCompleteAcct? {
        var popup = this@PostHelper.popup
        if (popup?.isShowing == true) return popup
        val et = this@PostHelper.et ?: return null
        val formRoot = this@PostHelper.formRoot ?: return null
        popup = PopupAutoCompleteAcct(activity, et, formRoot, bMainScreen)
        this@PostHelper.popup = popup
        return popup
    }

    interface Callback2 {

        fun onTextUpdate()

        fun canOpenPopup(): Boolean
    }

    fun setInstance(accessInfo: SavedAccount?) {
        this.accessInfo = accessInfo

        if (accessInfo != null) {
            App1.custom_emoji_lister.getList(accessInfo, onEmojiListLoad)
        }

        val popup = this.popup
        if (popup?.isShowing == true) {
            procTextChanged.run()
        }
    }

    fun closeAcctPopup() {
        popup?.dismiss()
        popup = null
    }

    fun onScrollChanged() {
        if (popup?.isShowing == true) {
            popup?.updatePosition()
        }
    }

    fun onDestroy() {
        handler.removeCallbacks(procTextChanged)
        closeAcctPopup()
    }

    fun attachEditText(
        formRoot: View,
        et: MyEditText,
        bMainScreen: Boolean,
        callback2: Callback2,
    ) {
        this.formRoot = formRoot
        this.et = et
        this.callback2 = callback2
        this.bMainScreen = bMainScreen

        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence,
                start: Int,
                count: Int,
                after: Int,
            ) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                handler.removeCallbacks(procTextChanged)
                handler.postDelayed(procTextChanged, if (popup?.isShowing == true) 100L else 500L)
            }

            override fun afterTextChanged(s: Editable) {
                this@PostHelper.callback2?.onTextUpdate()
            }
        })

        et.setOnSelectionChangeListener(object : MyEditText.OnSelectionChangeListener {
            override fun onSelectionChanged(selStart: Int, selEnd: Int) {
                if (selStart != selEnd) {
                    // 範囲選択されてるならポップアップは閉じる
                    log.d("onSelectionChanged: range selected")
                    closeAcctPopup()
                }
            }
        })

        // 全然動いてなさそう…
        // et.setCustomSelectionActionModeCallback( action_mode_callback );
    }

    private fun SpannableStringBuilder.appendEmoji(result: EmojiPickerResult) =
        appendEmoji(result.bInstanceHasCustomEmoji, result.emoji)

    private fun SpannableStringBuilder.appendEmoji(
        bInstanceHasCustomEmoji: Boolean,
        emoji: EmojiBase,
    ): SpannableStringBuilder {

        val separator = EmojiDecoder.customEmojiSeparator(pref)
        when (emoji) {
            is CustomEmoji -> {
                // カスタム絵文字は常にshortcode表現
                if (!EmojiDecoder.canStartShortCode(this, this.length)) append(separator)
                this.append(SpannableString(":${emoji.shortcode}:"))
                // セパレータにZWSPを使う設定なら、補完した次の位置にもZWSPを追加する。連続して入力補完できるようになる。
                if (separator != ' ') append(separator)
            }
            is UnicodeEmoji -> {
                if (!bInstanceHasCustomEmoji) {
                    // 古いタンスだとshortcodeを使う。見た目は絵文字に変える。
                    if (!EmojiDecoder.canStartShortCode(this, this.length)) append(separator)
                    this.append(DecodeOptions(activity).decodeEmoji(":${emoji.unifiedName}:"))
                    // セパレータにZWSPを使う設定なら、補完した次の位置にもZWSPを追加する。連続して入力補完できるようになる。
                    if (separator != ' ') append(separator)
                } else {
                    // 十分に新しいタンスなら絵文字のunicodeを使う。見た目は絵文字に変える。
                    this.append(DecodeOptions(activity).decodeEmoji(emoji.unifiedCode))
                }
            }
        }
        return this
    }

    private val openPickerEmoji: Runnable = Runnable {
        EmojiPicker(
            activity, accessInfo,
            closeOnSelected = Pref.bpEmojiPickerCloseOnSelected(pref)
        ) { result ->
            val et = this.et ?: return@EmojiPicker

            val src = et.text ?: ""
            val srcLength = src.length
            val end = min(srcLength, et.selectionEnd)
            val start = src.lastIndexOf(':', end - 1)
            if (start == -1 || end - start < 1) return@EmojiPicker

            val sb = SpannableStringBuilder()
                .append(src.subSequence(0, start))
                .appendEmoji(result)

            val newSelection = sb.length
            if (end < srcLength) sb.append(src.subSequence(end, srcLength))

            et.text = sb
            et.setSelection(newSelection)

            procTextChanged.run()

            // キーボードを再度表示する
            App1.getAppState(
                activity,
                "PostHelper/EmojiPicker/cb"
            ).handler.post { et.showKeyboard() }
        }.show()
    }

    fun openEmojiPickerFromMore() {
        EmojiPicker(
            activity, accessInfo,
            closeOnSelected = Pref.bpEmojiPickerCloseOnSelected(pref)
        ) { result ->
            val et = this.et ?: return@EmojiPicker

            val src = et.text ?: ""
            val srcLength = src.length
            val start = min(srcLength, et.selectionStart)
            val end = min(srcLength, et.selectionEnd)

            val sb = SpannableStringBuilder()
                .append(src.subSequence(0, start))
                .appendEmoji(result)

            val newSelection = sb.length
            if (end < srcLength) sb.append(src.subSequence(end, srcLength))

            et.text = sb
            et.setSelection(newSelection)

            procTextChanged.run()
        }.show()
    }

    private fun SpannableStringBuilder.appendHashTag(tagWithoutSharp: String): SpannableStringBuilder {
        val separator = ' '
        if (!EmojiDecoder.canStartHashtag(this, this.length)) append(separator)
        this.append('#').append(tagWithoutSharp)
        append(separator)
        return this
    }

    fun openFeaturedTagList(list: List<TootTag>?) {
        val ad = ActionsDialog()
        list?.forEach { tag ->
            ad.addAction("#${tag.name}") {
                val et = this.et ?: return@addAction

                val src = et.text ?: ""
                val srcLength = src.length
                val start = min(srcLength, et.selectionStart)
                val end = min(srcLength, et.selectionEnd)

                val sb = SpannableStringBuilder()
                    .append(src.subSequence(0, start))
                    .appendHashTag(tag.name)
                val newSelection = sb.length
                if (end < srcLength) sb.append(src.subSequence(end, srcLength))

                et.text = sb
                et.setSelection(newSelection)

                procTextChanged.run()
            }
        }
        ad.addAction(activity.getString(R.string.input_sharp_itself)) {
            val et = this.et ?: return@addAction

            val src = et.text ?: ""
            val srcLength = src.length
            val start = min(srcLength, et.selectionStart)
            val end = min(srcLength, et.selectionEnd)

            val sb = SpannableStringBuilder()
            sb.append(src.subSequence(0, start))
            if (!EmojiDecoder.canStartHashtag(sb, sb.length)) sb.append(' ')
            sb.append('#')

            val newSelection = sb.length
            if (end < srcLength) sb.append(src.subSequence(end, srcLength))
            et.text = sb
            et.setSelection(newSelection)

            procTextChanged.run()
        }
        ad.show(activity, activity.getString(R.string.featured_hashtags))
    }

    //	final ActionMode.Callback action_mode_callback = new ActionMode.Callback() {
    //		@Override public boolean onCreateActionMode( ActionMode actionMode, Menu menu ){
    //			actionMode.getMenuInflater().inflate(R.menu.toot_long_tap, menu);
    //			return true;
    //		}
    //		@Override public void onDestroyActionMode( ActionMode actionMode ){
    //
    //		}
    //		@Override public boolean onPrepareActionMode( ActionMode actionMode, Menu menu ){
    //			return false;
    //		}
    //
    //		@Override
    //		public boolean onActionItemClicked( ActionMode actionMode, MenuItem item ){
    //			if (item.getItemId() == R.id.action_pick_emoji) {
    //				actionMode.finish();
    //				EmojiPicker.open( activity, instance, new EmojiPicker.Callback() {
    //					@Override public void onPickedEmoji( String name ){
    //						int end = et.getSelectionEnd();
    //						String src = et.getText().toString();
    //						CharSequence svInsert = ":" + name + ":";
    //						src = src.substring( 0, end ) + svInsert + " " + ( end >= src.length() ? "" : src.substring( end ) );
    //						et.setText( src );
    //						et.setSelection( end + svInsert.length() + 1 );
    //
    //						proc_text_changed.run();
    //					}
    //				} );
    //				return true;
    //			}
    //
    //			return false;
    //		}
    //	};
}
