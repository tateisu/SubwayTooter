package jp.juggler.subwaytooter.column

import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.table.daoAcctColor
import jp.juggler.util.data.JsonException
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.encodeBase64Url
import java.lang.ref.WeakReference
import java.nio.ByteBuffer

// カラムデータのJSONエンコーダ、デコーダ

object ColumnEncoder {

    // いくつかのキーはカラム一覧画面でも使うのでprivateではない

    const val KEY_ACCOUNT_ROW_ID = "account_id"
    const val KEY_TYPE = "type"
    const val KEY_DONT_CLOSE = "dont_close"

    private const val KEY_SHOW_MEDIA_DESCRIPTION = "showMediaDescription"
    private const val KEY_COLUMN_ID = "column_id"
    private const val KEY_WITH_ATTACHMENT = "with_attachment"
    private const val KEY_WITH_HIGHLIGHT = "with_highlight"
    private const val KEY_DONT_SHOW_BOOST = "dont_show_boost"
    private const val KEY_DONT_SHOW_FAVOURITE = "dont_show_favourite"
    private const val KEY_DONT_SHOW_FOLLOW = "dont_show_follow"
    private const val KEY_DONT_SHOW_REPLY = "dont_show_reply"
    private const val KEY_DONT_SHOW_REACTION = "dont_show_reaction"
    private const val KEY_DONT_SHOW_VOTE = "dont_show_vote"
    private const val KEY_DONT_SHOW_NORMAL_TOOT = "dont_show_normal_toot"
    private const val KEY_DONT_SHOW_NON_PUBLIC_TOOT = "dont_show_non_public_toot"
    private const val KEY_DONT_STREAMING = "dont_streaming"
    private const val KEY_DONT_AUTO_REFRESH = "dont_auto_refresh"
    private const val KEY_HIDE_MEDIA_DEFAULT = "hide_media_default"
    private const val KEY_SYSTEM_NOTIFICATION_NOT_RELATED = "system_notification_not_related"
    private const val KEY_INSTANCE_LOCAL = "instance_local"

    private const val KEY_ENABLE_SPEECH = "enable_speech"
    private const val KEY_USE_OLD_API = "use_old_api"
    private const val KEY_LAST_VIEWING_ITEM = "lastViewingItem"
    private const val KEY_QUICK_FILTER = "quickFilter"

    private const val KEY_REGEX_TEXT = "regex_text"
    private const val KEY_LANGUAGE_FILTER = "language_filter"

    const val KEY_HEADER_BACKGROUND_COLOR = "header_background_color"
    const val KEY_HEADER_TEXT_COLOR = "header_text_color"
    private const val KEY_COLUMN_BACKGROUND_COLOR = "column_background_color"
    private const val KEY_COLUMN_ACCT_TEXT_COLOR = "column_acct_text_color"
    private const val KEY_COLUMN_CONTENT_TEXT_COLOR = "column_content_text_color"
    private const val KEY_COLUMN_BACKGROUND_IMAGE = "column_background_image"
    private const val KEY_COLUMN_BACKGROUND_IMAGE_ALPHA = "column_background_image_alpha"

    private const val KEY_PROFILE_ID = "profile_id"
    private const val KEY_PROFILE_TAB = "tab"
    private const val KEY_STATUS_ID = "status_id"
    private const val KEY_ORIGINAL_STATUS = "original_status"

    private const val KEY_HASHTAG = "hashtag"
    private const val KEY_HASHTAG_ANY = "hashtag_any"
    private const val KEY_HASHTAG_ALL = "hashtag_all"
    private const val KEY_HASHTAG_NONE = "hashtag_none"
    private const val KEY_HASHTAG_ACCT = "hashtag_acct"

    private const val KEY_SEARCH_QUERY = "search_query"
    private const val KEY_SEARCH_RESOLVE = "search_resolve"
    private const val KEY_INSTANCE_URI = "instance_uri"

    private const val KEY_REMOTE_ONLY = "remoteOnly"

    const val KEY_COLUMN_ACCESS_ACCT = "column_access"
    const val KEY_COLUMN_ACCESS_STR = "column_access_str"
    const val KEY_COLUMN_ACCESS_COLOR = "column_access_color"
    const val KEY_COLUMN_ACCESS_COLOR_BG = "column_access_color_bg"
    const val KEY_COLUMN_NAME = "column_name"
    const val KEY_OLD_INDEX = "old_index"

    private const val KEY_ANNOUNCEMENT_HIDE_TIME = "announcementHideTime"

    private val columnIdMap = HashMap<String, WeakReference<Column>?>()

    fun registerColumnId(id: String, column: Column) {
        synchronized(columnIdMap) {
            columnIdMap[id] = WeakReference(column)
        }
    }

    fun generateColumnId(): String {
        synchronized(columnIdMap) {
            val buffer = ByteBuffer.allocate(8)
            var id = ""
            while (id.isEmpty() || columnIdMap.containsKey(id)) {
                if (id.isNotEmpty()) Thread.sleep(1L)
                buffer.clear()
                buffer.putLong(System.currentTimeMillis())
                id = buffer.array().encodeBase64Url()
            }
            columnIdMap[id] = null
            return id
        }
    }

    fun decodeColumnId(src: JsonObject): String {
        return src.string(KEY_COLUMN_ID) ?: generateColumnId()
    }

    fun findColumnById(id: String): Column? {
        synchronized(columnIdMap) {
            return columnIdMap[id]?.get()
        }
    }

    @Throws(JsonException::class)
    fun encode(column: Column, dst: JsonObject, oldIndex: Int) {
        column.run {
            dst[KEY_ACCOUNT_ROW_ID] = accessInfo.db_id
            dst[KEY_TYPE] = type.id
            dst[KEY_COLUMN_ID] = columnId

            dst[KEY_ANNOUNCEMENT_HIDE_TIME] = announcementHideTime

            dst.putIfTrue(KEY_DONT_CLOSE, dontClose)

            dst.putIfTrue(KEY_WITH_ATTACHMENT, withAttachment)
            dst.putIfTrue(KEY_WITH_HIGHLIGHT, withHighlight)
            dst.putIfTrue(KEY_DONT_SHOW_BOOST, dontShowBoost)
            dst.putIfTrue(KEY_DONT_SHOW_FOLLOW, dontShowFollow)
            dst.putIfTrue(KEY_DONT_SHOW_FAVOURITE, dontShowFavourite)
            dst.putIfTrue(KEY_DONT_SHOW_REPLY, dontShowReply)
            dst.putIfTrue(KEY_DONT_SHOW_REACTION, dontShowReaction)
            dst.putIfTrue(KEY_DONT_SHOW_VOTE, dontShowVote)
            dst.putIfTrue(KEY_DONT_SHOW_NORMAL_TOOT, dontShowNormalToot)
            dst.putIfTrue(KEY_DONT_SHOW_NON_PUBLIC_TOOT, dontShowNonPublicToot)
            dst.putIfTrue(KEY_DONT_STREAMING, dontStreaming)
            dst.putIfTrue(KEY_DONT_AUTO_REFRESH, dontAutoRefresh)
            dst.putIfTrue(KEY_HIDE_MEDIA_DEFAULT, hideMediaDefault)
            dst.putIfTrue(KEY_SYSTEM_NOTIFICATION_NOT_RELATED, systemNotificationNotRelated)
            dst.putIfTrue(KEY_INSTANCE_LOCAL, instanceLocal)
            dst.putIfTrue(KEY_ENABLE_SPEECH, enableSpeech)
            dst.putIfTrue(KEY_USE_OLD_API, useOldApi)

            // この項目はdefault true
            dst.putIfNotDefault(KEY_SHOW_MEDIA_DESCRIPTION, showMediaDescription, true)

            dst[KEY_QUICK_FILTER] = quickFilter

            lastViewingItemId?.putTo(dst, KEY_LAST_VIEWING_ITEM)

            dst[KEY_REGEX_TEXT] = regexText

            val ov = languageFilter
            if (ov != null) dst[KEY_LANGUAGE_FILTER] = ov

            dst[KEY_HEADER_BACKGROUND_COLOR] = headerBgColor
            dst[KEY_HEADER_TEXT_COLOR] = headerFgColor
            dst[KEY_COLUMN_BACKGROUND_COLOR] = columnBgColor
            dst[KEY_COLUMN_ACCT_TEXT_COLOR] = acctColor
            dst[KEY_COLUMN_CONTENT_TEXT_COLOR] = contentColor
            dst[KEY_COLUMN_BACKGROUND_IMAGE] = columnBgImage
            dst[KEY_COLUMN_BACKGROUND_IMAGE_ALPHA] = columnBgImageAlpha.toDouble()

            when (type) {

                ColumnType.CONVERSATION,
                ColumnType.CONVERSATION_WITH_REFERENCE,
                ColumnType.BOOSTED_BY,
                ColumnType.FAVOURITED_BY,
                ColumnType.LOCAL_AROUND,
                ColumnType.ACCOUNT_AROUND,
                ->
                    dst[KEY_STATUS_ID] = statusId.toString()

                ColumnType.STATUS_HISTORY -> {
                    dst[KEY_STATUS_ID] = statusId.toString()
                    dst[KEY_ORIGINAL_STATUS] = originalStatus
                }

                ColumnType.FEDERATED_AROUND -> {
                    dst[KEY_STATUS_ID] = statusId.toString()
                    dst[KEY_REMOTE_ONLY] = remoteOnly
                }

                ColumnType.FEDERATE -> {
                    dst[KEY_REMOTE_ONLY] = remoteOnly
                }

                ColumnType.PROFILE -> {
                    dst[KEY_PROFILE_ID] = profileId.toString()
                    dst[KEY_PROFILE_TAB] = profileTab.id
                }

                ColumnType.LIST_MEMBER, ColumnType.LIST_TL,
                ColumnType.MISSKEY_ANTENNA_TL,
                -> {
                    dst[KEY_PROFILE_ID] = profileId.toString()
                }

                ColumnType.HASHTAG -> {
                    dst[KEY_HASHTAG] = hashtag
                    dst[KEY_HASHTAG_ANY] = hashtagAny
                    dst[KEY_HASHTAG_ALL] = hashtagAll
                    dst[KEY_HASHTAG_NONE] = hashtagNone
                }

                ColumnType.HASHTAG_FROM_ACCT -> {
                    dst[KEY_HASHTAG_ACCT] = hashtagAcct
                    dst[KEY_HASHTAG] = hashtag
                    dst[KEY_HASHTAG_ANY] = hashtagAny
                    dst[KEY_HASHTAG_ALL] = hashtagAll
                    dst[KEY_HASHTAG_NONE] = hashtagNone
                }

                ColumnType.NOTIFICATION_FROM_ACCT -> {
                    dst[KEY_HASHTAG_ACCT] = hashtagAcct
                }

                ColumnType.SEARCH -> {
                    dst[KEY_SEARCH_QUERY] = searchQuery
                    dst[KEY_SEARCH_RESOLVE] = searchResolve
                }

                ColumnType.REACTIONS,
                ColumnType.SEARCH_MSP,
                ColumnType.SEARCH_TS,
                ColumnType.SEARCH_NOTESTOCK,
                -> {
                    dst[KEY_SEARCH_QUERY] = searchQuery
                }

                ColumnType.INSTANCE_INFORMATION -> {
                    dst[KEY_INSTANCE_URI] = instanceUri
                }

                ColumnType.PROFILE_DIRECTORY -> {
                    dst[KEY_SEARCH_QUERY] = searchQuery
                    dst[KEY_SEARCH_RESOLVE] = searchResolve
                    dst[KEY_INSTANCE_URI] = instanceUri
                }

                ColumnType.DOMAIN_TIMELINE -> {
                    dst[KEY_INSTANCE_URI] = instanceUri
                }

                else -> {
                    // no extra parameter
                }
            }

            // 以下は保存には必要ないが、カラムリスト画面で使う
            val ac = daoAcctColor.load(accessInfo)
            dst[KEY_COLUMN_ACCESS_ACCT] = accessInfo.acct.ascii
            dst[KEY_COLUMN_ACCESS_STR] = ac.nickname
            dst[KEY_COLUMN_ACCESS_COLOR] = ac.colorFg
            dst[KEY_COLUMN_ACCESS_COLOR_BG] = ac.colorBg
            dst[KEY_COLUMN_NAME] = getColumnName(true)
            dst[KEY_OLD_INDEX] = oldIndex
        }
    }

    fun decode(column: Column, src: JsonObject) {
        column.run {
            dontClose = src.optBoolean(KEY_DONT_CLOSE)
            showMediaDescription = src.optBoolean(KEY_SHOW_MEDIA_DESCRIPTION, true)
            withAttachment = src.optBoolean(KEY_WITH_ATTACHMENT)
            withHighlight = src.optBoolean(KEY_WITH_HIGHLIGHT)
            dontShowBoost = src.optBoolean(KEY_DONT_SHOW_BOOST)
            dontShowFollow = src.optBoolean(KEY_DONT_SHOW_FOLLOW)
            dontShowFavourite = src.optBoolean(KEY_DONT_SHOW_FAVOURITE)
            dontShowReply = src.optBoolean(KEY_DONT_SHOW_REPLY)
            dontShowReaction = src.optBoolean(KEY_DONT_SHOW_REACTION)
            dontShowVote = src.optBoolean(KEY_DONT_SHOW_VOTE)
            dontShowNormalToot = src.optBoolean(KEY_DONT_SHOW_NORMAL_TOOT)
            dontShowNonPublicToot = src.optBoolean(KEY_DONT_SHOW_NON_PUBLIC_TOOT)
            dontStreaming = src.optBoolean(KEY_DONT_STREAMING)
            dontAutoRefresh = src.optBoolean(KEY_DONT_AUTO_REFRESH)
            hideMediaDefault = src.optBoolean(KEY_HIDE_MEDIA_DEFAULT)
            systemNotificationNotRelated = src.optBoolean(KEY_SYSTEM_NOTIFICATION_NOT_RELATED)
            instanceLocal = src.optBoolean(KEY_INSTANCE_LOCAL)
            quickFilter = src.optInt(KEY_QUICK_FILTER, 0)

            announcementHideTime = src.optLong(KEY_ANNOUNCEMENT_HIDE_TIME, 0L)

            enableSpeech = src.optBoolean(KEY_ENABLE_SPEECH)
            useOldApi = src.optBoolean(KEY_USE_OLD_API)
            lastViewingItemId = EntityId.entityId(src, KEY_LAST_VIEWING_ITEM)

            regexText = src.string(KEY_REGEX_TEXT) ?: ""
            languageFilter = src.jsonObject(KEY_LANGUAGE_FILTER)

            headerBgColor = src.optInt(KEY_HEADER_BACKGROUND_COLOR)
            headerFgColor = src.optInt(KEY_HEADER_TEXT_COLOR)
            columnBgColor = src.optInt(KEY_COLUMN_BACKGROUND_COLOR)
            acctColor = src.optInt(KEY_COLUMN_ACCT_TEXT_COLOR)
            contentColor = src.optInt(KEY_COLUMN_CONTENT_TEXT_COLOR)
            columnBgImage = src.string(KEY_COLUMN_BACKGROUND_IMAGE) ?: ""
            columnBgImageAlpha = src.optFloat(KEY_COLUMN_BACKGROUND_IMAGE_ALPHA, 1f)

            when (type) {

                ColumnType.CONVERSATION,
                ColumnType.CONVERSATION_WITH_REFERENCE,
                ColumnType.BOOSTED_BY,
                ColumnType.FAVOURITED_BY,
                ColumnType.LOCAL_AROUND,
                ColumnType.ACCOUNT_AROUND,
                -> statusId = EntityId.mayNull(src.string(KEY_STATUS_ID))

                ColumnType.STATUS_HISTORY -> {
                    statusId = EntityId.mayNull(src.string(KEY_STATUS_ID))
                    originalStatus = src.jsonObject(KEY_ORIGINAL_STATUS)
                }

                ColumnType.FEDERATED_AROUND,
                -> {
                    statusId = EntityId.mayNull(src.string(KEY_STATUS_ID))
                    remoteOnly = src.optBoolean(KEY_REMOTE_ONLY, false)
                }

                ColumnType.FEDERATE,
                -> {
                    remoteOnly = src.optBoolean(KEY_REMOTE_ONLY, false)
                }

                ColumnType.PROFILE,
                -> {
                    profileId = EntityId.mayNull(src.string(KEY_PROFILE_ID))
                    val tabId = src.optInt(KEY_PROFILE_TAB)
                    profileTab = ProfileTab.values().find { it.id == tabId } ?: ProfileTab.Status
                }

                ColumnType.LIST_MEMBER,
                ColumnType.LIST_TL,
                ColumnType.MISSKEY_ANTENNA_TL,
                -> {
                    profileId = EntityId.mayNull(src.string(KEY_PROFILE_ID))
                }

                ColumnType.HASHTAG -> {
                    hashtag = src.optString(KEY_HASHTAG)
                    hashtagAny = src.optString(KEY_HASHTAG_ANY)
                    hashtagAll = src.optString(KEY_HASHTAG_ALL)
                    hashtagNone = src.optString(KEY_HASHTAG_NONE)
                }

                ColumnType.HASHTAG_FROM_ACCT -> {
                    hashtagAcct = src.optString(KEY_HASHTAG_ACCT)
                    hashtag = src.optString(KEY_HASHTAG)
                    hashtagAny = src.optString(KEY_HASHTAG_ANY)
                    hashtagAll = src.optString(KEY_HASHTAG_ALL)
                    hashtagNone = src.optString(KEY_HASHTAG_NONE)
                }

                ColumnType.NOTIFICATION_FROM_ACCT -> {
                    hashtagAcct = src.optString(KEY_HASHTAG_ACCT)
                }

                ColumnType.SEARCH -> {
                    searchQuery = src.optString(KEY_SEARCH_QUERY)
                    searchResolve = src.optBoolean(KEY_SEARCH_RESOLVE, false)
                }

                ColumnType.REACTIONS,
                ColumnType.SEARCH_MSP,
                ColumnType.SEARCH_TS,
                ColumnType.SEARCH_NOTESTOCK,
                -> {
                    searchQuery = src.optString(KEY_SEARCH_QUERY)
                }

                ColumnType.INSTANCE_INFORMATION ->
                    instanceUri = src.optString(KEY_INSTANCE_URI)

                ColumnType.PROFILE_DIRECTORY -> {
                    instanceUri = src.optString(KEY_INSTANCE_URI)
                    searchQuery = src.optString(KEY_SEARCH_QUERY)
                    searchResolve = src.optBoolean(KEY_SEARCH_RESOLVE, false)
                }

                ColumnType.DOMAIN_TIMELINE -> {
                    instanceUri = src.optString(KEY_INSTANCE_URI)
                }

                else -> Unit
            }
        }
    }
}
