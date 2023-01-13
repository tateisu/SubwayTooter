package jp.juggler.subwaytooter.column

import android.util.LruCache
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.ApiPath
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.ServiceType
import jp.juggler.subwaytooter.api.entity.TootReaction
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.api.syncAccountByAcct
import jp.juggler.util.data.*
import jp.juggler.util.log.LogCategory

private val log = LogCategory("ColumnUrls")

private const val PATH_HOME = "/api/v1/timelines/home?limit=${ApiPath.READ_LIMIT}"
private const val PATH_LOCAL = "/api/v1/timelines/public?local=true&limit=${ApiPath.READ_LIMIT}"

fun Column.makeHomeTlUrl(): String {
    return when {
        accessInfo.isMisskey -> "/api/notes/timeline"
        withAttachment -> "$PATH_HOME&only_media=true"
        else -> PATH_HOME
    }
}

suspend fun Column.makeNotificationUrl(
    client: TootApiClient,
    fromAcct: String? = null,
): String {
    return when {
        accessInfo.isMisskey -> "/api/i/notifications"

        else -> {
            val sb = StringBuilder(ApiPath.PATH_NOTIFICATIONS) // always contain "?limit=XX"
            when (val quickFilter = this.quickFilter) {
                Column.QUICK_FILTER_ALL -> {
                    if (dontShowFavourite) sb.append("&exclude_types[]=favourite")
                    if (dontShowBoost) sb.append("&exclude_types[]=reblog")
                    if (dontShowFollow) sb.append("&exclude_types[]=follow")
                    if (dontShowReply) sb.append("&exclude_types[]=mention")
                    if (dontShowVote) sb.append("&exclude_types[]=poll")
                    if (dontShowNormalToot) sb.append("&exclude_types[]=status")
                }

                else -> {
                    if (quickFilter != Column.QUICK_FILTER_FAVOURITE) sb.append("&exclude_types[]=favourite")
                    if (quickFilter != Column.QUICK_FILTER_BOOST) sb.append("&exclude_types[]=reblog")
                    if (quickFilter != Column.QUICK_FILTER_FOLLOW) sb.append("&exclude_types[]=follow")
                    if (quickFilter != Column.QUICK_FILTER_MENTION) sb.append("&exclude_types[]=mention")
                    if (quickFilter != Column.QUICK_FILTER_POST) sb.append("&exclude_types[]=status")
                }
            }

            if (fromAcct?.isNotEmpty() == true) {
                if (profileId == null) {
                    val (result, whoRef) = client.syncAccountByAcct(accessInfo, hashtagAcct)
                    if (result != null) {
                        whoRef ?: error(result.error ?: "unknown error")
                        profileId = whoRef.get().id
                    }
                }
                if (profileId != null) {
                    sb.append("&account_id=").append(profileId.toString())
                }
            }

            // reaction,voteはmastodonにはない
            sb.toString()
        }
    }
}

fun Column.makeListTlUrl(): String {
    return if (isMisskey) {
        "/api/notes/user-list-timeline"
    } else {
        "/api/v1/timelines/list/$profileId?limit=${ApiPath.READ_LIMIT}"
    }
}

fun Column.makeReactionsUrl(): String {
    if (isMisskey) error("misskey has no api to list your reactions.")
    val basePath = ApiPath.PATH_REACTIONS
    val list = TootReaction.decodeEmojiQuery(searchQuery)
    if (list.isEmpty()) return basePath
    val delm = if (basePath.contains("?")) "&" else "?"
    return "$basePath$delm${list.joinToString("&") { "emojis[]=${it.name.encodePercent()}" }}"
}

fun Column.makeAntennaTlUrl(): String {
    return if (isMisskey) {
        "/api/antennas/notes"
    } else {
        "/nonexistent" // Mastodonにはアンテナ機能はない
    }
}

fun Column.makePublicLocalUrl(): String {
    return when {
        accessInfo.isMisskey -> "/api/notes/local-timeline"
        withAttachment -> "$PATH_LOCAL&only_media=true" // mastodon 2.3 or later
        else -> PATH_LOCAL
    }
}

fun Column.makeMisskeyHybridTlUrl(): String {
    return when {
        accessInfo.isMisskey -> "/api/notes/hybrid-timeline"
        else -> makePublicLocalUrl()
    }
}

fun Column.makeDomainTimelineUrl(): String {
    val base = "/api/v1/timelines/public?domain=$instanceUri&limit=${ApiPath.READ_LIMIT}"
    return when {
        accessInfo.isMisskey -> "/api/notes/local-timeline"
        withAttachment -> "$base&only_media=true"
        else -> base
    }
}

fun Column.makePublicFederateUrl(): String {

    return if (accessInfo.isMisskey) {
        "/api/notes/global-timeline"
    } else {
        val sb = StringBuilder("/api/v1/timelines/public?limit=${ApiPath.READ_LIMIT}")
        if (withAttachment) sb.append("&only_media=true")
        if (remoteOnly) sb.append("&remote=true")
        sb.toString()
    }
}

fun JsonObject.addMisskeyNotificationFilter(column: Column): JsonObject {
    when (column.quickFilter) {
        Column.QUICK_FILTER_ALL -> {
            val excludeList = buildJsonArray {
                // Misskeyのお気に入りは通知されない
                // if(dont_show_favourite) ...

                if (column.dontShowBoost) {
                    add("renote")
                    add("quote")
                }
                if (column.dontShowFollow) {
                    add("follow")
                    add("receiveFollowRequest")
                }
                if (column.dontShowReply) {
                    add("mention")
                    add("reply")
                }
                if (column.dontShowReaction) {
                    add("reaction")
                }
                if (column.dontShowVote) {
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
            jsonArrayOf("renote", "quote")
        )
        Column.QUICK_FILTER_FOLLOW -> put(
            "includeTypes",
            jsonArrayOf("follow", "receiveFollowRequest")
        )
        Column.QUICK_FILTER_MENTION -> put(
            "includeTypes",
            jsonArrayOf("mention", "reply")
        )
        Column.QUICK_FILTER_REACTION -> put("includeTypes", jsonArrayOf("reaction"))
        Column.QUICK_FILTER_VOTE -> put("includeTypes", jsonArrayOf("poll_vote"))

        Column.QUICK_FILTER_POST -> {
            // FIXME Misskeyには特定フォロー者からの投稿を通知する機能があるのか？
        }
    }

    return this
}

fun JsonObject.putMisskeyParamsTimeline(column: Column): JsonObject {
    if (column.withAttachment && !column.withHighlight) {
        put("mediaOnly", true)
        put("withMedia", true)
        put("withFiles", true)
        put("media", true)
    }
    return this
}

suspend fun Column.makeHashtagAcctUrl(client: TootApiClient): String? {
    return if (isMisskey) {
        // currently not supported
        null
    } else {
        if (profileId == null) {
            val (result, whoRef) = client.syncAccountByAcct(accessInfo, hashtagAcct)
            result ?: return null // cancelled.
            if (whoRef == null) {
                log.w("makeHashtagAcctUrl: ${result.error ?: "?"}")
                return null
            }
            profileId = whoRef.get().id
        }

        val sb = StringBuilder("/api/v1/accounts/$profileId/statuses")
            .append("?limit=").append(ApiPath.READ_LIMIT)
            .append("&tagged=").append(hashtag.encodePercent())

        if (withAttachment) sb.append("&only_media=true")
        if (instanceLocal) sb.append("&local=true")

        makeHashtagQueryParams(tagKey = null).encodeQuery().notEmpty()?.let {
            sb.append('&').append(it)
        }

        sb.toString()
    }
}

fun Column.makeMisskeyBaseParameter(parser: TootParser?) =
    accessInfo.putMisskeyApiToken().apply {
        if (accessInfo.isMisskey) {
            parser?.serviceType = ServiceType.MISSKEY
            put("limit", 40)
        }
    }

fun Column.makeMisskeyParamsUserId(parser: TootParser) =
    makeMisskeyBaseParameter(parser).apply {
        put("userId", profileId.toString())
    }

fun Column.makeMisskeyTimelineParameter(parser: TootParser) =
    makeMisskeyBaseParameter(parser).apply {
        putMisskeyParamsTimeline(this@makeMisskeyTimelineParameter)
    }

fun Column.makeMisskeyParamsProfileStatuses(parser: TootParser) =
    makeMisskeyParamsUserId(parser).apply {
        putMisskeyParamsTimeline(this@makeMisskeyParamsProfileStatuses)
        if (!dontShowReply) put("includeReplies", true)
        if (!dontShowBoost) put("includeMyRenotes", true)
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
            is List<*> -> {
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

fun Column.makeHashtagQueryParams(tagKey: String? = "tag") = JsonObject().apply {
    if (tagKey != null) put(tagKey, hashtag)
    hashtagAny.parseExtraTag().notEmpty()?.let { put("any", it.toJsonArray()) }
    hashtagAll.parseExtraTag().notEmpty()?.let { put("all", it.toJsonArray()) }
    hashtagNone.parseExtraTag().notEmpty()?.let { put("none", it.toJsonArray()) }
}

fun Column.checkHashtagExtra(item: TootStatus): Boolean {
    hashtagAny.parseExtraTag().notEmpty()
        ?.any { item.tags?.any { tag -> tag.name.equals(it, ignoreCase = true) } ?: false }
        ?.let { if (!it) return false }

    hashtagAll.parseExtraTag().notEmpty()
        .notEmpty()
        ?.all { item.tags?.any { tag -> tag.name.equals(it, ignoreCase = true) } ?: false }
        ?.let { if (!it) return false }

    hashtagNone.parseExtraTag().notEmpty()
        ?.any { item.tags?.any { tag -> tag.name.equals(it, ignoreCase = true) } ?: false }
        ?.not()
        ?.let { if (!it) return false }

    return true
}

fun Column.makeHashtagUrl(): String {
    return if (isMisskey) {
        "/api/notes/search_by_tag"
    } else {
        // hashtag : String // 先頭の#を含まない
        val sb = StringBuilder("/api/v1/timelines/tag/")
            .append(hashtag.encodePercent())
            .append("?limit=").append(ApiPath.READ_LIMIT)

        if (withAttachment) sb.append("&only_media=true")
        if (instanceLocal) sb.append("&local=true")

        makeHashtagQueryParams(tagKey = null).encodeQuery().notEmpty()?.let {
            sb.append('&').append(it)
        }

        sb.toString()
    }
}

fun Column.makeHashtagParams(parser: TootParser) =
    makeMisskeyTimelineParameter(parser).apply {
        put("tag", hashtag)
        put("limit", Column.MISSKEY_HASHTAG_LIMIT)
    }

// mastodon用
fun Column.makeProfileStatusesUrl(profileId: EntityId?): String {
    var path = "/api/v1/accounts/$profileId/statuses?limit=${ApiPath.READ_LIMIT}"
    if (withAttachment && !withHighlight) path += "&only_media=1"
    if (dontShowBoost) path += "&exclude_reblogs=1"
    if (dontShowReply) path += "&exclude_replies=1"
    return path
}

fun StringBuilder.appendHashtagExtra(column: Column): StringBuilder {
    val limit =
        (Column.HASHTAG_ELLIPSIZE * 2 - kotlin.math.min(length, Column.HASHTAG_ELLIPSIZE)) / 3
    if (column.hashtagAny.isNotBlank()) append(' ').append(
        column.context.getString(
            R.string.hashtag_title_any,
            column.hashtagAny.ellipsizeDot3(limit)
        )
    )
    if (column.hashtagAll.isNotBlank()) append(' ').append(
        column.context.getString(
            R.string.hashtag_title_all,
            column.hashtagAll.ellipsizeDot3(limit)
        )
    )
    if (column.hashtagNone.isNotBlank()) append(' ').append(
        column.context.getString(
            R.string.hashtag_title_none,
            column.hashtagNone.ellipsizeDot3(limit)
        )
    )
    return this
}
