package jp.juggler.subwaytooter

import android.graphics.Bitmap
import android.util.LruCache
import jp.juggler.subwaytooter.Column.Companion.READ_LIMIT
import jp.juggler.subwaytooter.Column.Companion.log
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.api.syncAccountByAcct
import jp.juggler.util.*
import java.util.*


internal inline fun <reified T : TimelineItem> addAll(
    dstArg: ArrayList<TimelineItem>?,
    src: List<T>,
    head: Boolean = false
): ArrayList<TimelineItem> =
    (dstArg ?: ArrayList(src.size)).apply {
        if (head) {
            addAll(0, src)
        } else {
            addAll(src)
        }
    }

internal fun addOne(
    dstArg: ArrayList<TimelineItem>?,
    item: TimelineItem?,
    head: Boolean = false
): ArrayList<TimelineItem> =
    (dstArg ?: ArrayList()).apply {
        if (item != null) {
            if (head) {
                add(0, item)
            } else {
                add(item)
            }
        }
    }

internal fun ColumnTask.addWithFilterStatus(
    dstArg: ArrayList<TimelineItem>?,
    srcArg: List<TootStatus>,
    head: Boolean = false
): ArrayList<TimelineItem> =
    (dstArg ?: ArrayList(srcArg.size)).apply {
        val src = srcArg.filter { !column.isFiltered(it) }
        if (head) {
            addAll(0, src)
        } else {
            addAll(src)
        }
    }

internal fun ColumnTask.addWithFilterConversationSummary(
    dstArg: ArrayList<TimelineItem>?,
    srcArg: List<TootConversationSummary>,
    head: Boolean = false
): ArrayList<TimelineItem> =
    (dstArg ?: ArrayList(srcArg.size)).apply {
        val src = srcArg.filter { !column.isFiltered(it.last_status) }
        if (head) {
            addAll(0, src)
        } else {
            addAll(src)
        }

    }

internal fun ColumnTask.addWithFilterNotification(
    dstArg: ArrayList<TimelineItem>?,
    srcArg: List<TootNotification>,
    head: Boolean = false
): ArrayList<TimelineItem> =
    (dstArg ?: ArrayList(srcArg.size)).apply {
        val src = srcArg.filter { !column.isFiltered(it) }
        if (head) {
            addAll(0, src)
        } else {
            addAll(src)
        }
    }

internal fun Column.dispatchProfileTabStatus() =
    when {
        isMisskey -> ColumnType.ProfileStatusMisskey
        else -> ColumnType.ProfileStatusMastodon
    }

internal fun Column.dispatchProfileTabFollowing() =
    when {
        misskeyVersion >= 11 -> ColumnType.FollowingMisskey11
        isMisskey -> ColumnType.FollowingMisskey10
        access_info.isPseudo -> ColumnType.FollowingMastodonPseudo
        else -> ColumnType.FollowingMastodon
    }

internal fun Column.dispatchProfileTabFollowers() =
    when {
        misskeyVersion >= 11 -> ColumnType.FollowersMisskey11
        isMisskey -> ColumnType.FollowersMisskey10
        access_info.isPseudo -> ColumnType.FollowersMastodonPseudo
        else -> ColumnType.FollowersMastodon
    }

internal fun ColumnTask.dispatchProfileTabStatus() =
    column.dispatchProfileTabStatus()

internal fun ColumnTask.dispatchProfileTabFollowing() =
    column.dispatchProfileTabFollowing()

internal fun ColumnTask.dispatchProfileTabFollowers() =
    column.dispatchProfileTabFollowers()

internal suspend fun Column.loadListInfo(client: TootApiClient, bForceReload: Boolean) {
    val parser = TootParser(context, access_info)
    if (bForceReload || this.list_info == null) {
        val result = if (isMisskey) {
            client.request(
                "/api/users/lists/show",
                makeMisskeyBaseParameter(parser).apply {
                    put("listId", profile_id)
                }.toPostRequestBuilder()
            )
        } else {
            client.request("/api/v1/lists/${profile_id.toString()}")
        }

        val jsonObject = result?.jsonObject
        if (jsonObject != null) {
            val data = parseItem(::TootList, parser, jsonObject)
            if (data != null) {
                this.list_info = data
                client.publishApiProgress("") // カラムヘッダの再表示
            }
        }
    }
}

internal suspend fun Column.loadAntennaInfo(client: TootApiClient, bForceReload: Boolean) {
    val parser = TootParser(context, access_info)
    if (bForceReload || this.antenna_info == null) {

        val result = if (isMisskey) {
            client.request(
                "/api/antennas/show",
                makeMisskeyBaseParameter(parser).apply {
                    put("antennaId", profile_id)
                }.toPostRequestBuilder()
            )
        } else {
            TootApiResult("antenna feature is not supported on Mastodon")
        }

        val jsonObject = result?.jsonObject
        if (jsonObject != null) {
            val data = parseItem(::MisskeyAntenna, jsonObject)
            if (data != null) {
                this.antenna_info = data
                client.publishApiProgress("") // カラムヘッダの再表示
            }
        }
    }
}

internal fun JsonObject.putMisskeyUntil(id: EntityId?): JsonObject {
    if (id != null) put("untilId", id.toString())
    return this
}

internal fun JsonObject.putMisskeySince(id: EntityId?): JsonObject {
    if (id != null) put("sinceId", id.toString())
    return this
}

internal fun JsonObject.addRangeMisskey(column: Column, bBottom: Boolean): JsonObject {
    if (bBottom) {
        putMisskeyUntil(column.idOld)
    } else {
        putMisskeySince(column.idRecent)
    }

    return this
}

internal fun JsonObject.addMisskeyNotificationFilter(column: Column): JsonObject {
    when (column.quick_filter) {
        Column.QUICK_FILTER_ALL -> {
            val excludeList = jsonArray {
                // Misskeyのお気に入りは通知されない
                // if(dont_show_favourite) ...

                if (column.dont_show_boost) {
                    add("renote")
                    add("quote")
                }
                if (column.dont_show_follow) {
                    add("follow")
                    add("receiveFollowRequest")
                }
                if (column.dont_show_reply) {
                    add("mention")
                    add("reply")
                }
                if (column.dont_show_reaction) {
                    add("reaction")
                }
                if (column.dont_show_vote) {
                    add("poll_vote")
                }
//				// FIXME Misskeyには特定フォロー者からの投稿を通知する機能があるのか？
//				if(column.dont_show_normal_toot) {
//				}
            }

            if (excludeList.isNotEmpty()) put("excludeTypes", excludeList)
        }

        // QUICK_FILTER_FAVOURITE // misskeyはお気に入りの通知はない
        Column.QUICK_FILTER_BOOST -> put(
            "includeTypes",
            jsonArray("renote", "quote")
        )
        Column.QUICK_FILTER_FOLLOW -> put(
            "includeTypes",
            jsonArray("follow", "receiveFollowRequest")
        )
        Column.QUICK_FILTER_MENTION -> put(
            "includeTypes",
            jsonArray("mention", "reply")
        )
        Column.QUICK_FILTER_REACTION -> put("includeTypes", jp.juggler.util.jsonArray("reaction"))
        Column.QUICK_FILTER_VOTE -> put("includeTypes", jp.juggler.util.jsonArray("poll_vote"))

        Column.QUICK_FILTER_POST -> {
            // FIXME Misskeyには特定フォロー者からの投稿を通知する機能があるのか？
        }
    }

    return this
}

internal fun JsonObject.putMisskeyParamsTimeline(column: Column): JsonObject {
    if (column.with_attachment && !column.with_highlight) {
        put("mediaOnly", true)
        put("withMedia", true)
        put("withFiles", true)
        put("media", true)
    }
    return this
}

internal suspend fun Column.makeHashtagAcctUrl(client: TootApiClient): String? {
    return if (isMisskey) {
        // currently not supported
        null
    } else {
        if (profile_id == null) {
            val (result, whoRef) = client.syncAccountByAcct(access_info, hashtag_acct)
            result ?: return null // cancelled.
            if (whoRef == null) {
                log.w("makeHashtagAcctUrl: ${result.error ?: "?"}")
                return null
            }
            profile_id = whoRef.get().id
        }

        val sb = StringBuilder("/api/v1/accounts/${profile_id}/statuses")
            .append("?limit=").append(READ_LIMIT)
            .append("&tagged=").append(hashtag.encodePercent())

        if (with_attachment) sb.append("&only_media=true")
        if (instance_local) sb.append("&local=true")

        makeHashtagQueryParams(tagKey = null).encodeQuery().notEmpty()?.let {
            sb.append('&').append(it)
        }

        sb.toString()
    }
}

internal fun Column.makeMisskeyBaseParameter(parser: TootParser?) =
    access_info.putMisskeyApiToken().apply {
        if (access_info.isMisskey) {
            if (parser != null) parser.serviceType = ServiceType.MISSKEY
            put("limit", 40)
        }
    }

internal fun Column.makeMisskeyParamsUserId(parser: TootParser) =
    makeMisskeyBaseParameter(parser).apply {
        put("userId", profile_id.toString())
    }

internal fun Column.makeMisskeyTimelineParameter(parser: TootParser) =
    makeMisskeyBaseParameter(parser).apply {
        putMisskeyParamsTimeline(this@makeMisskeyTimelineParameter)
    }

internal fun Column.makeMisskeyParamsProfileStatuses(parser: TootParser) =
    makeMisskeyParamsUserId(parser).apply {
        putMisskeyParamsTimeline(this@makeMisskeyParamsProfileStatuses)
        if (!dont_show_reply) put("includeReplies", true)
        if (!dont_show_boost) put("includeMyRenotes", true)
    }

const val PATH_LOCAL = "/api/v1/timelines/public?local=true&limit=$READ_LIMIT"

internal fun Column.makePublicLocalUrl(): String {
    return when {
        access_info.isMisskey -> "/api/notes/local-timeline"
        with_attachment -> "${PATH_LOCAL}&only_media=true" // mastodon 2.3 or later
        else -> PATH_LOCAL
    }
}

internal fun Column.makeMisskeyHybridTlUrl(): String {
    return when {
        access_info.isMisskey -> "/api/notes/hybrid-timeline"
        else -> makePublicLocalUrl()
    }
}

internal fun Column.makeDomainTimelineUrl(): String {
    val base = "/api/v1/timelines/public?domain=$instance_uri&limit=$READ_LIMIT"
    return when {
        access_info.isMisskey -> "/api/notes/local-timeline"
        with_attachment -> "$base&only_media=true"
        else -> base
    }
}

internal fun Column.makePublicFederateUrl(): String {

    return if (access_info.isMisskey) {
        "/api/notes/global-timeline"
    } else {
        val sb = StringBuilder("/api/v1/timelines/public?limit=$READ_LIMIT")
        if (with_attachment) sb.append("&only_media=true")
        if (remote_only) sb.append("&remote=true")
        sb.toString()
    }
}

const val PATH_HOME = "/api/v1/timelines/home?limit=$READ_LIMIT"

internal fun Column.makeHomeTlUrl(): String {
    return when {
        access_info.isMisskey -> "/api/notes/timeline"
        with_attachment -> "$PATH_HOME&only_media=true"
        else -> PATH_HOME
    }
}

internal suspend fun Column.makeNotificationUrl(
    client: TootApiClient,
    fromAcct: String? = null
): String {
    return when {
        access_info.isMisskey -> "/api/i/notifications"

        else -> {
            val sb = StringBuilder(Column.PATH_NOTIFICATIONS) // always contain "?limit=XX"
            when (val quick_filter = quick_filter) {
                Column.QUICK_FILTER_ALL -> {
                    if (dont_show_favourite) sb.append("&exclude_types[]=favourite")
                    if (dont_show_boost) sb.append("&exclude_types[]=reblog")
                    if (dont_show_follow) sb.append("&exclude_types[]=follow")
                    if (dont_show_reply) sb.append("&exclude_types[]=mention")
                    if (dont_show_vote) sb.append("&exclude_types[]=poll")
                    if (dont_show_normal_toot) sb.append("&exclude_types[]=status")
                }

                else -> {
                    if (quick_filter != Column.QUICK_FILTER_FAVOURITE) sb.append("&exclude_types[]=favourite")
                    if (quick_filter != Column.QUICK_FILTER_BOOST) sb.append("&exclude_types[]=reblog")
                    if (quick_filter != Column.QUICK_FILTER_FOLLOW) sb.append("&exclude_types[]=follow")
                    if (quick_filter != Column.QUICK_FILTER_MENTION) sb.append("&exclude_types[]=mention")
                    if (quick_filter != Column.QUICK_FILTER_POST) sb.append("&exclude_types[]=status")
                }
            }

            if (fromAcct?.isNotEmpty() == true) {
                if (profile_id == null) {
                    val (result, whoRef) = client.syncAccountByAcct(access_info, hashtag_acct)
                    if (result != null) {
                        whoRef ?: error(result.error ?: "unknown error")
                        profile_id = whoRef.get().id
                    }
                }
                if (profile_id != null) {
                    sb.append("&account_id=").append(profile_id.toString())
                }
            }

            // reaction,voteはmastodonにはない
            sb.toString()
        }
    }
}

internal fun Column.makeListTlUrl(): String {
    return if (isMisskey) {
        "/api/notes/user-list-timeline"
    } else {
        "/api/v1/timelines/list/${profile_id}?limit=$READ_LIMIT"
    }
}

internal fun Column.makeAntennaTlUrl(): String {
    return if (isMisskey) {
        "/api/antennas/notes"
    } else {
        "/nonexistent" // Mastodonにはアンテナ機能はない
    }
}

fun JsonObject.encodeQuery(): String {
    val sb = StringBuilder()
    entries.forEach { pair ->
        val (k, v) = pair
        when (v) {
            null, is String, is Number, is Boolean -> {
                if (sb.isNotEmpty()) sb.append('&')
                sb.append(k).append('=').append(v.toString().encodePercent())
            }
            is JsonArray -> {
                v.forEach {
                    if (sb.isNotEmpty()) sb.append('&')
                    sb.append(k).append("[]=").append(it.toString().encodePercent())
                }
            }
            else -> error("encodeQuery: unsupported type ${v.javaClass.name}")
        }
    }
    return sb.toString()
}

private val extraTagCache by lazy {
    object : LruCache<String, List<String>>(1024 * 80) {
        override fun sizeOf(key: String, value: List<String>): Int =
            key.length
    }
}

// parse extra tags with LRU cache.
private fun String.parseExtraTag() = synchronized(extraTagCache) {
    var result = extraTagCache.get(this)
    if (result == null) {
        result = this.split(" ").filter { it.isNotEmpty() }
        extraTagCache.put(this, result)
    }
    result
}

internal fun Column.makeHashtagQueryParams(tagKey: String? = "tag") = JsonObject().apply {

    if (tagKey != null) put(tagKey, hashtag)

    hashtag_any.parseExtraTag().notEmpty()?.let { put("any", it) }
    hashtag_all.parseExtraTag().notEmpty()?.let { put("all", it) }
    hashtag_none.parseExtraTag().notEmpty()?.let { put("none", it) }
}

fun Column.checkHashtagExtra(item: TootStatus): Boolean {
    hashtag_any.parseExtraTag().notEmpty()
        ?.any { item.tags?.any { tag -> tag.name.equals(it,ignoreCase  = true) } ?: false }
        ?.let { if (!it) return false }

    hashtag_all.parseExtraTag().notEmpty()
        .notEmpty()
        ?.all { item.tags?.any { tag -> tag.name.equals(it,ignoreCase  = true) } ?: false }
        ?.let { if (!it) return false }

    hashtag_none.parseExtraTag().notEmpty()
        ?.any { item.tags?.any { tag -> tag.name.equals(it,ignoreCase  = true) } ?: false }
        ?.not()
        ?.let { if (!it) return false }

    return true
}

internal fun Column.makeHashtagUrl(): String {
    return if (isMisskey) {
        "/api/notes/search_by_tag"
    } else {
        // hashtag : String // 先頭の#を含まない
        val sb = StringBuilder("/api/v1/timelines/tag/")
            .append(hashtag.encodePercent())
            .append("?limit=").append(READ_LIMIT)

        if (with_attachment) sb.append("&only_media=true")
        if (instance_local) sb.append("&local=true")

        makeHashtagQueryParams(tagKey = null).encodeQuery().notEmpty()?.let {
            sb.append('&').append(it)
        }

        sb.toString()
    }
}

internal fun Column.makeHashtagParams(parser: TootParser) =
    makeMisskeyTimelineParameter(parser).apply {
        put("tag", hashtag)
        put("limit", Column.MISSKEY_HASHTAG_LIMIT)
    }

// mastodon用
internal fun Column.makeProfileStatusesUrl(profile_id: EntityId?): String {
    var path = "/api/v1/accounts/$profile_id/statuses?limit=$READ_LIMIT"
    if (with_attachment && !with_highlight) path += "&only_media=1"
    if (dont_show_boost) path += "&exclude_reblogs=1"
    if (dont_show_reply) path += "&exclude_replies=1"
    return path
}

internal val misskeyArrayFinderUsers = { it: JsonObject ->
    it.jsonArray("users")
}

////////////////////////////////////////////////////////////////////////////////
// account list parser

internal val nullArrayFinder: (JsonObject) -> JsonArray? =
    { null }

internal val defaultAccountListParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootAccountRef> =
    { parser, jsonArray -> parser.accountList(jsonArray) }

private fun misskeyUnwrapRelationAccount(parser: TootParser, srcList: JsonArray, key: String) =
    srcList.objectList().mapNotNull {
        when (val relationId = EntityId.mayNull(it.string("id"))) {
            null -> null
            else -> TootAccountRef.mayNull(parser, parser.account(it.jsonObject(key)))
                ?.apply { _orderId = relationId }
        }
    }

internal val misskey11FollowingParser: (TootParser, JsonArray) -> List<TootAccountRef> =
    { parser, jsonArray -> misskeyUnwrapRelationAccount(parser, jsonArray, "followee") }

internal val misskey11FollowersParser: (TootParser, JsonArray) -> List<TootAccountRef> =
    { parser, jsonArray -> misskeyUnwrapRelationAccount(parser, jsonArray, "follower") }

internal val misskeyCustomParserFollowRequest: (TootParser, JsonArray) -> List<TootAccountRef> =
    { parser, jsonArray -> misskeyUnwrapRelationAccount(parser, jsonArray, "follower") }

internal val misskeyCustomParserMutes: (TootParser, JsonArray) -> List<TootAccountRef> =
    { parser, jsonArray -> misskeyUnwrapRelationAccount(parser, jsonArray, "mutee") }

internal val misskeyCustomParserBlocks: (TootParser, JsonArray) -> List<TootAccountRef> =
    { parser, jsonArray -> misskeyUnwrapRelationAccount(parser, jsonArray, "blockee") }

////////////////////////////////////////////////////////////////////////////////
// status list parser

internal val defaultStatusListParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootStatus> =
    { parser, jsonArray -> parser.statusList(jsonArray) }

internal val misskeyCustomParserFavorites: (TootParser, JsonArray) -> List<TootStatus> =
    { parser, jsonArray ->
        jsonArray.objectList().mapNotNull {
            when (val relationId = EntityId.mayNull(it.string("id"))) {
                null -> null
                else -> parser.status(it.jsonObject("note"))?.apply {
                    favourited = true
                    _orderId = relationId
                }
            }
        }
    }

////////////////////////////////////////////////////////////////////////////////
// notification list parser

val defaultNotificationListParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootNotification> =
    { parser, jsonArray -> parser.notificationList(jsonArray) }

val defaultDomainBlockListParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootDomainBlock> =
    { _, jsonArray -> TootDomainBlock.parseList(jsonArray) }

val defaultReportListParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootReport> =
    { _, jsonArray -> parseList(::TootReport, jsonArray) }

val defaultConversationSummaryListParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootConversationSummary> =
    { parser, jsonArray -> parseList(::TootConversationSummary, parser, jsonArray) }
