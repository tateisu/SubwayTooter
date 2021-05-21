package jp.juggler.subwaytooter

import android.content.Context
import android.os.Environment
import android.util.LruCache
import jp.juggler.subwaytooter.api.ApiPath.READ_LIMIT
import jp.juggler.subwaytooter.Column.Companion.log
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.*
import java.io.File
import java.util.*

val Column.isMastodon: Boolean
    get() = access_info.isMastodon

val Column.isMisskey: Boolean
    get() = access_info.isMisskey

val Column.misskeyVersion: Int
    get() = access_info.misskeyVersion

val Column.isSearchColumn: Boolean
    get() {
        return when (type) {
            ColumnType.SEARCH,
            ColumnType.SEARCH_MSP,
            ColumnType.SEARCH_TS,
            ColumnType.SEARCH_NOTESTOCK -> true
            else -> false
        }
    }

val Column.isNotificationColumn: Boolean
    get() = when (type) {
        ColumnType.NOTIFICATIONS, ColumnType.NOTIFICATION_FROM_ACCT -> true
        else -> false
    }

// 公開ストリームなら真
val Column.isPublicStream: Boolean
    get() {
        return when (type) {
            ColumnType.LOCAL,
            ColumnType.FEDERATE,
            ColumnType.HASHTAG,
            ColumnType.LOCAL_AROUND,
            ColumnType.FEDERATED_AROUND,
            ColumnType.DOMAIN_TIMELINE -> true

            else -> false
        }
    }

fun Column.canAutoRefresh() =
    !access_info.isNA && type.canAutoRefresh

/////////////////////////////////////////////////////////////////////////////
// 読み込み処理の内部で使うメソッド

fun Column.getNotificationTypeString(): String {
    val sb = StringBuilder()
    sb.append("(")

    when (quick_filter) {
        Column.QUICK_FILTER_ALL -> {
            var n = 0
            if (!dont_show_reply) {
                if (n++ > 0) sb.append(", ")
                sb.append(context.getString(R.string.notification_type_mention))
            }
            if (!dont_show_follow) {
                if (n++ > 0) sb.append(", ")
                sb.append(context.getString(R.string.notification_type_follow))
            }
            if (!dont_show_boost) {
                if (n++ > 0) sb.append(", ")
                sb.append(context.getString(R.string.notification_type_boost))
            }
            if (!dont_show_favourite) {
                if (n++ > 0) sb.append(", ")
                sb.append(context.getString(R.string.notification_type_favourite))
            }
            if (isMisskey && !dont_show_reaction) {
                if (n++ > 0) sb.append(", ")
                sb.append(context.getString(R.string.notification_type_reaction))
            }
            if (!dont_show_vote) {
                if (n++ > 0) sb.append(", ")
                sb.append(context.getString(R.string.notification_type_vote))
            }
            val n_max = if (isMisskey) {
                6
            } else {
                5
            }
            if (n == 0 || n == n_max) return "" // 全部か皆無なら部分表記は要らない
        }

        Column.QUICK_FILTER_MENTION -> sb.append(context.getString(R.string.notification_type_mention))
        Column.QUICK_FILTER_FAVOURITE -> sb.append(context.getString(R.string.notification_type_favourite))
        Column.QUICK_FILTER_BOOST -> sb.append(context.getString(R.string.notification_type_boost))
        Column.QUICK_FILTER_FOLLOW -> sb.append(context.getString(R.string.notification_type_follow))
        Column.QUICK_FILTER_REACTION -> sb.append(context.getString(R.string.notification_type_reaction))
        Column.QUICK_FILTER_VOTE -> sb.append(context.getString(R.string.notification_type_vote))
        Column.QUICK_FILTER_POST -> sb.append(context.getString(R.string.notification_type_post))
    }

    sb.append(")")
    return sb.toString()
}

suspend fun Column.loadProfileAccount(client: TootApiClient, parser: TootParser, bForceReload: Boolean): TootApiResult? =
    when {
        // リロード不要なら何もしない
        this.who_account != null && !bForceReload -> null

        isMisskey -> client.request(
            "/api/users/show",
            access_info.putMisskeyApiToken().apply {
                put("userId", profile_id)
            }.toPostRequestBuilder()
        )?.also { result1 ->
            // ユーザリレーションの取り扱いのため、別のparserを作ってはいけない
            parser.misskeyDecodeProfilePin = true
            try {
                TootAccountRef.mayNull(parser, parser.account(result1.jsonObject))?.also { a ->
                    this.who_account = a
                    client.publishApiProgress("") // カラムヘッダの再表示
                }
            } finally {
                parser.misskeyDecodeProfilePin = false
            }
        }

        else -> client.request(
            "/api/v1/accounts/${profile_id}"
        )?.also { result1 ->
            TootAccountRef.mayNull(parser, parser.account(result1.jsonObject))?.also { a ->
                this.who_account = a

                this.who_featured_tags = null
                client.request("/api/v1/accounts/${profile_id}/featured_tags")
                    ?.also { result2 ->

                        this.who_featured_tags =
                            TootTag.parseListOrNull(parser, result2.jsonArray)
                    }

                client.publishApiProgress("") // カラムヘッダの再表示
            }
        }
    }

fun Column.loadSearchDesc(raw_en: Int, raw_ja: Int): String {
    val res_id = if ("ja" == context.getString(R.string.language_code)) raw_ja else raw_en
    return context.loadRawResource(res_id).decodeUTF8()
}

suspend fun Column.updateRelation(
    client: TootApiClient,
    list: ArrayList<TimelineItem>?,
    whoRef: TootAccountRef?,
    parser: TootParser
) {
    if (access_info.isPseudo) return

    val env = UpdateRelationEnv(this)

    env.add(whoRef)

    list?.forEach {
        when (it) {
            is TootAccountRef -> env.add(it)
            is TootStatus -> env.add(it)
            is TootNotification -> env.add(it)
            is TootConversationSummary -> env.add(it.last_status)
        }
    }
    env.update(client, parser)
}

fun Column.parseRange(
    result: TootApiResult?,
    list: List<TimelineItem>?
): Pair<EntityId?, EntityId?> {
    var idMin: EntityId? = null
    var idMax: EntityId? = null

    if (isMisskey && list != null) {
        // MisskeyはLinkヘッダがないので、常にデータからIDを読む

        for (item in list) {
            // injectされたデータをデータ範囲に追加しない
            if (item.isInjected()) continue

            val id = item.getOrderId()
            if (id.notDefaultOrConfirming) {
                if (idMin == null || id < idMin) idMin = id
                if (idMax == null || id > idMax) idMax = id
            }
        }
    } else {
        // Linkヘッダを読む
        idMin = Column.reMaxId.matcher(result?.link_older ?: "").findOrNull()
            ?.let {
                EntityId(it.groupEx(1)!!)
            }

        idMax = Column.reMinId.matcher(result?.link_newer ?: "").findOrNull()
            ?.let {
                // min_idとsince_idの読み分けは現在利用してない it.groupEx(1)=="min_id"
                EntityId(it.groupEx(2)!!)
            }
    }

    return Pair(idMin, idMax)
}
// int scroll_hack;

// return true if list bottom may have unread remain
fun Column.saveRange(
    bBottom: Boolean,
    bTop: Boolean,
    result: TootApiResult?,
    list: List<TimelineItem>?
): Boolean {
    val (idMin, idMax) = parseRange(result, list)

    var hasBottomRemain = false

    if (bBottom) when (idMin) {
        null -> idOld = null // リストの終端
        else -> {
            val i = idOld?.compareTo(idMin)
            if (i == null || i > 0) {
                idOld = idMin
                hasBottomRemain = true
            }
        }
    }

    if (bTop) when (idMax) {
        null -> {
            // リロードを許容するため、取得内容がカラでもidRecentを変更しない
        }

        else -> {
            val i = idRecent?.compareTo(idMax)
            if (i == null || i < 0) {
                idRecent = idMax
            }
        }
    }

    return hasBottomRemain
}

// return true if list bottom may have unread remain
fun Column.saveRangeBottom(result: TootApiResult?, list: List<TimelineItem>?) =
    saveRange(true, bTop = false, result = result, list = list)

// return true if list bottom may have unread remain
fun Column.saveRangeTop(result: TootApiResult?, list: List<TimelineItem>?) =
    saveRange(false, bTop = true, result = result, list = list)

fun Column.addRange(
    bBottom: Boolean,
    path: String,
    delimiter: Char = if (-1 == path.indexOf('?')) '?' else '&'
) = if (bBottom) {
    if (idOld != null) "$path${delimiter}max_id=${idOld}" else path
} else {
    if (idRecent != null) "$path${delimiter}since_id=${idRecent}" else path
}

fun Column.addRangeMin(
    path: String,
    delimiter: Char = if (-1 != path.indexOf('?')) '&' else '?'
) = if (idRecent == null) path else "$path${delimiter}min_id=${idRecent}"

fun Column.toAdapterIndex(listIndex: Int): Int {
    return if (type.headerType != null) listIndex + 1 else listIndex
}

fun Column.toListIndex(adapterIndex: Int): Int {
    return if (type.headerType != null) adapterIndex - 1 else adapterIndex
}

fun Column.saveScrollPosition() {
    try {
        if (viewHolder?.saveScrollPosition() == true) {
            val ss = this.scroll_save
            if (ss != null) {
                val idx = toListIndex(ss.adapterIndex)
                if (0 <= idx && idx < list_data.size) {
                    val item = list_data[idx]
                    this.last_viewing_item_id = item.getOrderId()
                    // とりあえず保存はするが
                    // TLデータそのものを永続化しないかぎり出番はないっぽい
                }
            }
        }
    } catch (ex: Throwable) {
        log.e(ex, "can't get last_viewing_item_id.")
    }
}


inline fun <reified T : TimelineItem> addAll(
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

fun addOne(
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

fun ColumnTask.addWithFilterStatus(
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

fun ColumnTask.addWithFilterConversationSummary(
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

fun ColumnTask.addWithFilterNotification(
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

fun Column.dispatchProfileTabStatus() =
    when {
        isMisskey -> ColumnType.ProfileStatusMisskey
        else -> ColumnType.ProfileStatusMastodon
    }

fun Column.dispatchProfileTabFollowing() =
    when {
        misskeyVersion >= 11 -> ColumnType.FollowingMisskey11
        isMisskey -> ColumnType.FollowingMisskey10
        access_info.isPseudo -> ColumnType.FollowingMastodonPseudo
        else -> ColumnType.FollowingMastodon
    }

fun Column.dispatchProfileTabFollowers() =
    when {
        misskeyVersion >= 11 -> ColumnType.FollowersMisskey11
        isMisskey -> ColumnType.FollowersMisskey10
        access_info.isPseudo -> ColumnType.FollowersMastodonPseudo
        else -> ColumnType.FollowersMastodon
    }

fun ColumnTask.dispatchProfileTabStatus() =
    column.dispatchProfileTabStatus()

fun ColumnTask.dispatchProfileTabFollowing() =
    column.dispatchProfileTabFollowing()

fun ColumnTask.dispatchProfileTabFollowers() =
    column.dispatchProfileTabFollowers()

suspend fun Column.loadListInfo(client: TootApiClient, bForceReload: Boolean) {
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

suspend fun Column.loadAntennaInfo(client: TootApiClient, bForceReload: Boolean) {
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

fun JsonObject.putMisskeyUntil(id: EntityId?): JsonObject {
    if (id != null) put("untilId", id.toString())
    return this
}

fun JsonObject.putMisskeySince(id: EntityId?): JsonObject {
    if (id != null) put("sinceId", id.toString())
    return this
}

fun JsonObject.addRangeMisskey(column: Column, bBottom: Boolean): JsonObject {
    if (bBottom) {
        putMisskeyUntil(column.idOld)
    } else {
        putMisskeySince(column.idRecent)
    }

    return this
}

fun JsonObject.addMisskeyNotificationFilter(column: Column): JsonObject {
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
    if (column.with_attachment && !column.with_highlight) {
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

fun Column.makeMisskeyBaseParameter(parser: TootParser?) =
    access_info.putMisskeyApiToken().apply {
        if (access_info.isMisskey) {
            if (parser != null) parser.serviceType = ServiceType.MISSKEY
            put("limit", 40)
        }
    }

fun Column.makeMisskeyParamsUserId(parser: TootParser) =
    makeMisskeyBaseParameter(parser).apply {
        put("userId", profile_id.toString())
    }

fun Column.makeMisskeyTimelineParameter(parser: TootParser) =
    makeMisskeyBaseParameter(parser).apply {
        putMisskeyParamsTimeline(this@makeMisskeyTimelineParameter)
    }

fun Column.makeMisskeyParamsProfileStatuses(parser: TootParser) =
    makeMisskeyParamsUserId(parser).apply {
        putMisskeyParamsTimeline(this@makeMisskeyParamsProfileStatuses)
        if (!dont_show_reply) put("includeReplies", true)
        if (!dont_show_boost) put("includeMyRenotes", true)
    }

private const val PATH_LOCAL = "/api/v1/timelines/public?local=true&limit=$READ_LIMIT"

fun Column.makePublicLocalUrl(): String {
    return when {
        access_info.isMisskey -> "/api/notes/local-timeline"
        with_attachment -> "${PATH_LOCAL}&only_media=true" // mastodon 2.3 or later
        else -> PATH_LOCAL
    }
}

fun Column.makeMisskeyHybridTlUrl(): String {
    return when {
        access_info.isMisskey -> "/api/notes/hybrid-timeline"
        else -> makePublicLocalUrl()
    }
}

fun Column.makeDomainTimelineUrl(): String {
    val base = "/api/v1/timelines/public?domain=$instance_uri&limit=$READ_LIMIT"
    return when {
        access_info.isMisskey -> "/api/notes/local-timeline"
        with_attachment -> "$base&only_media=true"
        else -> base
    }
}

fun Column.makePublicFederateUrl(): String {

    return if (access_info.isMisskey) {
        "/api/notes/global-timeline"
    } else {
        val sb = StringBuilder("/api/v1/timelines/public?limit=$READ_LIMIT")
        if (with_attachment) sb.append("&only_media=true")
        if (remote_only) sb.append("&remote=true")
        sb.toString()
    }
}

private const val PATH_HOME = "/api/v1/timelines/home?limit=$READ_LIMIT"

fun Column.makeHomeTlUrl(): String {
    return when {
        access_info.isMisskey -> "/api/notes/timeline"
        with_attachment -> "$PATH_HOME&only_media=true"
        else -> PATH_HOME
    }
}

suspend fun Column.makeNotificationUrl(
    client: TootApiClient,
    fromAcct: String? = null
): String {
    return when {
        access_info.isMisskey -> "/api/i/notifications"

        else -> {
            val sb = StringBuilder(ApiPath.PATH_NOTIFICATIONS) // always contain "?limit=XX"
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

fun Column.makeListTlUrl(): String {
    return if (isMisskey) {
        "/api/notes/user-list-timeline"
    } else {
        "/api/v1/timelines/list/${profile_id}?limit=$READ_LIMIT"
    }
}

fun Column.makeReactionsUrl(): String {
    if (isMisskey) error("misskey has no api to list your reactions.")
    val basePath = ApiPath.PATH_REACTIONS
    val list = TootReaction.decodeEmojiQuery(search_query)
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

    hashtag_any.parseExtraTag().notEmpty()?.let { put("any", it.toJsonArray()) }
    hashtag_all.parseExtraTag().notEmpty()?.let { put("all", it.toJsonArray()) }
    hashtag_none.parseExtraTag().notEmpty()?.let { put("none", it.toJsonArray()) }
}

fun Column.checkHashtagExtra(item: TootStatus): Boolean {
    hashtag_any.parseExtraTag().notEmpty()
        ?.any { item.tags?.any { tag -> tag.name.equals(it, ignoreCase = true) } ?: false }
        ?.let { if (!it) return false }

    hashtag_all.parseExtraTag().notEmpty()
        .notEmpty()
        ?.all { item.tags?.any { tag -> tag.name.equals(it, ignoreCase = true) } ?: false }
        ?.let { if (!it) return false }

    hashtag_none.parseExtraTag().notEmpty()
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
            .append("?limit=").append(READ_LIMIT)

        if (with_attachment) sb.append("&only_media=true")
        if (instance_local) sb.append("&local=true")

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
fun Column.makeProfileStatusesUrl(profile_id: EntityId?): String {
    var path = "/api/v1/accounts/$profile_id/statuses?limit=$READ_LIMIT"
    if (with_attachment && !with_highlight) path += "&only_media=1"
    if (dont_show_boost) path += "&exclude_reblogs=1"
    if (dont_show_reply) path += "&exclude_replies=1"
    return path
}

val misskeyArrayFinderUsers = { it: JsonObject ->
    it.jsonArray("users")
}

////////////////////////////////////////////////////////////////////////////////
// account list parser

val nullArrayFinder: (JsonObject) -> JsonArray? =
    { null }

val defaultAccountListParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootAccountRef> =
    { parser, jsonArray -> parser.accountList(jsonArray) }

private fun misskeyUnwrapRelationAccount(parser: TootParser, srcList: JsonArray, key: String) =
    srcList.objectList().mapNotNull {
        when (val relationId = EntityId.mayNull(it.string("id"))) {
            null -> null
            else -> TootAccountRef.mayNull(parser, parser.account(it.jsonObject(key)))
                ?.apply { _orderId = relationId }
        }
    }

val misskey11FollowingParser: (TootParser, JsonArray) -> List<TootAccountRef> =
    { parser, jsonArray -> misskeyUnwrapRelationAccount(parser, jsonArray, "followee") }

val misskey11FollowersParser: (TootParser, JsonArray) -> List<TootAccountRef> =
    { parser, jsonArray -> misskeyUnwrapRelationAccount(parser, jsonArray, "follower") }

val misskeyCustomParserFollowRequest: (TootParser, JsonArray) -> List<TootAccountRef> =
    { parser, jsonArray -> misskeyUnwrapRelationAccount(parser, jsonArray, "follower") }

val misskeyCustomParserMutes: (TootParser, JsonArray) -> List<TootAccountRef> =
    { parser, jsonArray -> misskeyUnwrapRelationAccount(parser, jsonArray, "mutee") }

val misskeyCustomParserBlocks: (TootParser, JsonArray) -> List<TootAccountRef> =
    { parser, jsonArray -> misskeyUnwrapRelationAccount(parser, jsonArray, "blockee") }

////////////////////////////////////////////////////////////////////////////////
// status list parser

val defaultStatusListParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootStatus> =
    { parser, jsonArray -> parser.statusList(jsonArray) }

val misskeyCustomParserFavorites: (TootParser, JsonArray) -> List<TootStatus> =
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

///////////////////////////////////////////////////////////////////////

val mastodonFollowSuggestion2ListParser : (parser: TootParser, jsonArray: JsonArray) -> List<TootAccountRef> =
    { parser, jsonArray ->
        TootAccountRef.wrapList(parser,
            jsonArray.objectList().mapNotNull{
                parser.account(it.jsonObject("account"))?.also{ a->
                    SuggestionSource.set(
                        (parser.linkHelper as? SavedAccount) ?.db_id ,
                        a.acct,
                        it.string("source")
                    )
                }
            }
        )
    }

///////////////////////////////////////////////////////////////////////

private const val DIR_BACKGROUND_IMAGE = "columnBackground"

fun getBackgroundImageDir(context: Context): File {
    val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    if (externalDir == null) {
        log.e("getExternalFilesDir is null.")
    } else {
        val state = Environment.getExternalStorageState()
        if (state != Environment.MEDIA_MOUNTED) {
            log.e("getExternalStorageState: ${state}")
        } else {
            log.i("externalDir: ${externalDir}")
            externalDir.mkdir()
            val backgroundDir = File(externalDir, DIR_BACKGROUND_IMAGE)
            backgroundDir.mkdir()
            log.i("backgroundDir: ${backgroundDir} exists=${backgroundDir.exists()}")
            return backgroundDir
        }
    }
    val backgroundDir = context.getDir(DIR_BACKGROUND_IMAGE, Context.MODE_PRIVATE)
    log.i("backgroundDir: ${backgroundDir} exists=${backgroundDir.exists()}")
    return backgroundDir
}

fun StringBuilder.appendHashtagExtra(column: Column): StringBuilder {
    val limit = (Column.HASHTAG_ELLIPSIZE * 2 - kotlin.math.min(length, Column.HASHTAG_ELLIPSIZE)) / 3
    if (column.hashtag_any.isNotBlank()) append(' ').append(
        column.context.getString(
            R.string.hashtag_title_any,
            column.hashtag_any.ellipsizeDot3(limit)
        )
    )
    if (column.hashtag_all.isNotBlank()) append(' ').append(
        column.context.getString(
            R.string.hashtag_title_all,
            column.hashtag_all.ellipsizeDot3(limit)
        )
    )
    if (column.hashtag_none.isNotBlank()) append(' ').append(
        column.context.getString(
            R.string.hashtag_title_none,
            column.hashtag_none.ellipsizeDot3(limit)
        )
    )
    return this
}
