package jp.juggler.subwaytooter

import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.util.JsonException
import jp.juggler.util.JsonObject
import jp.juggler.util.encodeBase64Url
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.HashMap

object ColumnEncoder {

    // いくつかのキーはカラム一覧画面でも使うのでprivateではない

    const val KEY_ACCOUNT_ROW_ID = "account_id"
    const val KEY_TYPE = "type"
    const val KEY_DONT_CLOSE = "dont_close"

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

    private const val KEY_HEADER_BACKGROUND_COLOR = "header_background_color"
    private const val KEY_HEADER_TEXT_COLOR = "header_text_color"
    private const val KEY_COLUMN_BACKGROUND_COLOR = "column_background_color"
    private const val KEY_COLUMN_ACCT_TEXT_COLOR = "column_acct_text_color"
    private const val KEY_COLUMN_CONTENT_TEXT_COLOR = "column_content_text_color"
    private const val KEY_COLUMN_BACKGROUND_IMAGE = "column_background_image"
    private const val KEY_COLUMN_BACKGROUND_IMAGE_ALPHA = "column_background_image_alpha"

    private const val KEY_PROFILE_ID = "profile_id"
    private const val KEY_PROFILE_TAB = "tab"
    private const val KEY_STATUS_ID = "status_id"

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
    fun encode(column: Column, dst: JsonObject, old_index: Int) {
        column.run {
            dst[KEY_ACCOUNT_ROW_ID] = access_info.db_id
            dst[KEY_TYPE] = type.id
            dst[KEY_COLUMN_ID] = column_id

            dst[KEY_ANNOUNCEMENT_HIDE_TIME] = announcementHideTime

            dst.putIfTrue(KEY_DONT_CLOSE, dont_close)
            dst.putIfTrue(KEY_WITH_ATTACHMENT, with_attachment)
            dst.putIfTrue(KEY_WITH_HIGHLIGHT, with_highlight)
            dst.putIfTrue(KEY_DONT_SHOW_BOOST, dont_show_boost)
            dst.putIfTrue(KEY_DONT_SHOW_FOLLOW, dont_show_follow)
            dst.putIfTrue(KEY_DONT_SHOW_FAVOURITE, dont_show_favourite)
            dst.putIfTrue(KEY_DONT_SHOW_REPLY, dont_show_reply)
            dst.putIfTrue(KEY_DONT_SHOW_REACTION, dont_show_reaction)
            dst.putIfTrue(KEY_DONT_SHOW_VOTE, dont_show_vote)
            dst.putIfTrue(KEY_DONT_SHOW_NORMAL_TOOT, dont_show_normal_toot)
            dst.putIfTrue(KEY_DONT_SHOW_NON_PUBLIC_TOOT, dont_show_non_public_toot)
            dst.putIfTrue(KEY_DONT_STREAMING, dont_streaming)
            dst.putIfTrue(KEY_DONT_AUTO_REFRESH, dont_auto_refresh)
            dst.putIfTrue(KEY_HIDE_MEDIA_DEFAULT, hide_media_default)
            dst.putIfTrue(KEY_SYSTEM_NOTIFICATION_NOT_RELATED, system_notification_not_related)
            dst.putIfTrue(KEY_INSTANCE_LOCAL, instance_local)
            dst.putIfTrue(KEY_ENABLE_SPEECH, enable_speech)
            dst.putIfTrue(KEY_USE_OLD_API, use_old_api)
            dst[KEY_QUICK_FILTER] = quick_filter

            last_viewing_item_id?.putTo(dst, KEY_LAST_VIEWING_ITEM)

            dst[KEY_REGEX_TEXT] = regex_text

            val ov = language_filter
            if (ov != null) dst[KEY_LANGUAGE_FILTER] = ov

            dst[KEY_HEADER_BACKGROUND_COLOR] = header_bg_color
            dst[KEY_HEADER_TEXT_COLOR] = header_fg_color
            dst[KEY_COLUMN_BACKGROUND_COLOR] = column_bg_color
            dst[KEY_COLUMN_ACCT_TEXT_COLOR] = acct_color
            dst[KEY_COLUMN_CONTENT_TEXT_COLOR] = content_color
            dst[KEY_COLUMN_BACKGROUND_IMAGE] = column_bg_image
            dst[KEY_COLUMN_BACKGROUND_IMAGE_ALPHA] = column_bg_image_alpha.toDouble()

            when (type) {

                ColumnType.CONVERSATION,
                ColumnType.BOOSTED_BY,
                ColumnType.FAVOURITED_BY,
                ColumnType.LOCAL_AROUND,
                ColumnType.ACCOUNT_AROUND ->
                    dst[KEY_STATUS_ID] = status_id.toString()

                ColumnType.FEDERATED_AROUND -> {
                    dst[KEY_STATUS_ID] = status_id.toString()
                    dst[KEY_REMOTE_ONLY] = remote_only
                }

                ColumnType.FEDERATE -> {
                    dst[KEY_REMOTE_ONLY] = remote_only
                }

                ColumnType.PROFILE -> {
                    dst[KEY_PROFILE_ID] = profile_id.toString()
                    dst[KEY_PROFILE_TAB] = profile_tab.id
                }

                ColumnType.LIST_MEMBER, ColumnType.LIST_TL,
                ColumnType.MISSKEY_ANTENNA_TL -> {
                    dst[KEY_PROFILE_ID] = profile_id.toString()
                }

                ColumnType.HASHTAG -> {
                    dst[KEY_HASHTAG] = hashtag
                    dst[KEY_HASHTAG_ANY] = hashtag_any
                    dst[KEY_HASHTAG_ALL] = hashtag_all
                    dst[KEY_HASHTAG_NONE] = hashtag_none
                }

                ColumnType.HASHTAG_FROM_ACCT -> {
                    dst[KEY_HASHTAG_ACCT] = hashtag_acct
                    dst[KEY_HASHTAG] = hashtag
                    dst[KEY_HASHTAG_ANY] = hashtag_any
                    dst[KEY_HASHTAG_ALL] = hashtag_all
                    dst[KEY_HASHTAG_NONE] = hashtag_none
                }

                ColumnType.NOTIFICATION_FROM_ACCT -> {
                    dst[KEY_HASHTAG_ACCT] = hashtag_acct
                }

                ColumnType.SEARCH -> {
                    dst[KEY_SEARCH_QUERY] = search_query
                    dst[KEY_SEARCH_RESOLVE] = search_resolve
                }

                ColumnType.SEARCH_MSP, ColumnType.SEARCH_TS, ColumnType.SEARCH_NOTESTOCK -> {
                    dst[KEY_SEARCH_QUERY] = search_query
                }

                ColumnType.INSTANCE_INFORMATION -> {
                    dst[KEY_INSTANCE_URI] = instance_uri
                }

                ColumnType.PROFILE_DIRECTORY -> {
                    dst[KEY_SEARCH_QUERY] = search_query
                    dst[KEY_SEARCH_RESOLVE] = search_resolve
                    dst[KEY_INSTANCE_URI] = instance_uri
                }

                ColumnType.DOMAIN_TIMELINE -> {
                    dst[KEY_INSTANCE_URI] = instance_uri
                }

                else -> {
                    // no extra parameter
                }
            }

            // 以下は保存には必要ないが、カラムリスト画面で使う
            val ac = AcctColor.load(access_info)
            dst[KEY_COLUMN_ACCESS_ACCT] = access_info.acct.ascii
            dst[KEY_COLUMN_ACCESS_STR] = ac.nickname
            dst[KEY_COLUMN_ACCESS_COLOR] = ac.color_fg
            dst[KEY_COLUMN_ACCESS_COLOR_BG] = ac.color_bg
            dst[KEY_COLUMN_NAME] = getColumnName(true)
            dst[KEY_OLD_INDEX] = old_index
        }
    }

    fun decode(column: Column, src: JsonObject) {
        column.run {
            dont_close = src.optBoolean(KEY_DONT_CLOSE)
            with_attachment = src.optBoolean(KEY_WITH_ATTACHMENT)
            with_highlight = src.optBoolean(KEY_WITH_HIGHLIGHT)
            dont_show_boost = src.optBoolean(KEY_DONT_SHOW_BOOST)
            dont_show_follow = src.optBoolean(KEY_DONT_SHOW_FOLLOW)
            dont_show_favourite = src.optBoolean(KEY_DONT_SHOW_FAVOURITE)
            dont_show_reply = src.optBoolean(KEY_DONT_SHOW_REPLY)
            dont_show_reaction = src.optBoolean(KEY_DONT_SHOW_REACTION)
            dont_show_vote = src.optBoolean(KEY_DONT_SHOW_VOTE)
            dont_show_normal_toot = src.optBoolean(KEY_DONT_SHOW_NORMAL_TOOT)
            dont_show_non_public_toot = src.optBoolean(KEY_DONT_SHOW_NON_PUBLIC_TOOT)
            dont_streaming = src.optBoolean(KEY_DONT_STREAMING)
            dont_auto_refresh = src.optBoolean(KEY_DONT_AUTO_REFRESH)
            hide_media_default = src.optBoolean(KEY_HIDE_MEDIA_DEFAULT)
            system_notification_not_related = src.optBoolean(KEY_SYSTEM_NOTIFICATION_NOT_RELATED)
            instance_local = src.optBoolean(KEY_INSTANCE_LOCAL)
            quick_filter = src.optInt(KEY_QUICK_FILTER, 0)

            announcementHideTime = src.optLong(KEY_ANNOUNCEMENT_HIDE_TIME, 0L)

            enable_speech = src.optBoolean(KEY_ENABLE_SPEECH)
            use_old_api = src.optBoolean(KEY_USE_OLD_API)
            last_viewing_item_id = EntityId.from(src, KEY_LAST_VIEWING_ITEM)

            regex_text = src.string(KEY_REGEX_TEXT) ?: ""
            language_filter = src.jsonObject(KEY_LANGUAGE_FILTER)

            header_bg_color = src.optInt(KEY_HEADER_BACKGROUND_COLOR)
            header_fg_color = src.optInt(KEY_HEADER_TEXT_COLOR)
            column_bg_color = src.optInt(KEY_COLUMN_BACKGROUND_COLOR)
            acct_color = src.optInt(KEY_COLUMN_ACCT_TEXT_COLOR)
            content_color = src.optInt(KEY_COLUMN_CONTENT_TEXT_COLOR)
            column_bg_image = src.string(KEY_COLUMN_BACKGROUND_IMAGE) ?: ""
            column_bg_image_alpha = src.optFloat(KEY_COLUMN_BACKGROUND_IMAGE_ALPHA, 1f)

            when (type) {

                ColumnType.CONVERSATION, ColumnType.BOOSTED_BY, ColumnType.FAVOURITED_BY,
                ColumnType.LOCAL_AROUND, ColumnType.ACCOUNT_AROUND ->
                    status_id = EntityId.mayNull(src.string(KEY_STATUS_ID))

                ColumnType.FEDERATED_AROUND -> {
                    status_id = EntityId.mayNull(src.string(KEY_STATUS_ID))
                    remote_only = src.optBoolean(KEY_REMOTE_ONLY, false)
                }

                ColumnType.FEDERATE -> {
                    remote_only = src.optBoolean(KEY_REMOTE_ONLY, false)
                }

                ColumnType.PROFILE -> {
                    profile_id = EntityId.mayNull(src.string(KEY_PROFILE_ID))
                    val tabId = src.optInt(KEY_PROFILE_TAB)
                    profile_tab = ProfileTab.values().find { it.id == tabId } ?: ProfileTab.Status
                }

                ColumnType.LIST_MEMBER, ColumnType.LIST_TL,
                ColumnType.MISSKEY_ANTENNA_TL -> {
                    profile_id = EntityId.mayNull(src.string(KEY_PROFILE_ID))
                }

                ColumnType.HASHTAG -> {
                    hashtag = src.optString(KEY_HASHTAG)
                    hashtag_any = src.optString(KEY_HASHTAG_ANY)
                    hashtag_all = src.optString(KEY_HASHTAG_ALL)
                    hashtag_none = src.optString(KEY_HASHTAG_NONE)
                }

                ColumnType.HASHTAG_FROM_ACCT -> {
                    hashtag_acct = src.optString(KEY_HASHTAG_ACCT)
                    hashtag = src.optString(KEY_HASHTAG)
                    hashtag_any = src.optString(KEY_HASHTAG_ANY)
                    hashtag_all = src.optString(KEY_HASHTAG_ALL)
                    hashtag_none = src.optString(KEY_HASHTAG_NONE)
                }

                ColumnType.NOTIFICATION_FROM_ACCT -> {
                    hashtag_acct = src.optString(KEY_HASHTAG_ACCT)
                }

                ColumnType.SEARCH -> {
                    search_query = src.optString(KEY_SEARCH_QUERY)
                    search_resolve = src.optBoolean(KEY_SEARCH_RESOLVE, false)
                }

                ColumnType.SEARCH_MSP, ColumnType.SEARCH_TS, ColumnType.SEARCH_NOTESTOCK -> search_query =
                    src.optString(KEY_SEARCH_QUERY)

                ColumnType.INSTANCE_INFORMATION -> instance_uri = src.optString(KEY_INSTANCE_URI)

                ColumnType.PROFILE_DIRECTORY -> {
                    instance_uri = src.optString(KEY_INSTANCE_URI)
                    search_query = src.optString(KEY_SEARCH_QUERY)
                    search_resolve = src.optBoolean(KEY_SEARCH_RESOLVE, false)
                }

                ColumnType.DOMAIN_TIMELINE -> {
                    instance_uri = src.optString(KEY_INSTANCE_URI)
                }

                else -> {

                }
            }
        }
    }
}