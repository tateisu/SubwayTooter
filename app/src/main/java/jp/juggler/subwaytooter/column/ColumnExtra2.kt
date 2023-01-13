package jp.juggler.subwaytooter.column

import android.content.Context
import android.os.Environment
import androidx.annotation.RawRes
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.columnviewholder.saveScrollPosition
import jp.juggler.util.data.*
import jp.juggler.util.log.LogCategory
import jp.juggler.util.network.toPostRequestBuilder
import java.io.File

private val log = LogCategory("ColumnExtra2")

val Column.isMastodon: Boolean
    get() = accessInfo.isMastodon

val Column.isMisskey: Boolean
    get() = accessInfo.isMisskey

val Column.misskeyVersion: Int
    get() = accessInfo.misskeyVersion

val Column.isSearchColumn: Boolean
    get() {
        return when (type) {
            ColumnType.SEARCH,
            ColumnType.SEARCH_MSP,
            ColumnType.SEARCH_TS,
            ColumnType.SEARCH_NOTESTOCK,
            -> true
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
            ColumnType.DOMAIN_TIMELINE,
            -> true

            else -> false
        }
    }

fun Column.canAutoRefresh() =
    !accessInfo.isNA && type.canAutoRefresh

val Column.isConversation
    get() = when (type) {
        ColumnType.CONVERSATION,
        ColumnType.CONVERSATION_WITH_REFERENCE,
        -> true
        else -> false
    }

/////////////////////////////////////////////////////////////////////////////
// 読み込み処理の内部で使うメソッド

fun Column.getNotificationTypeString(): String {
    val list = buildList {
        when (quickFilter) {
            Column.QUICK_FILTER_MENTION -> add(R.string.notification_type_mention)
            Column.QUICK_FILTER_FAVOURITE -> add(R.string.notification_type_favourite)
            Column.QUICK_FILTER_BOOST -> add(R.string.notification_type_boost)
            Column.QUICK_FILTER_FOLLOW -> add(R.string.notification_type_follow)
            Column.QUICK_FILTER_REACTION -> add(R.string.notification_type_reaction)
            Column.QUICK_FILTER_VOTE -> add(R.string.notification_type_vote)
            Column.QUICK_FILTER_POST -> add(R.string.notification_type_post)
            Column.QUICK_FILTER_ALL -> {
                if (!dontShowReply) add(R.string.notification_type_mention)
                if (!dontShowFollow) add(R.string.notification_type_follow)
                if (!dontShowBoost) add(R.string.notification_type_boost)
                if (!dontShowFavourite) add(R.string.notification_type_favourite)
                if (isMisskey && !dontShowReaction) add(R.string.notification_type_reaction)
                if (!dontShowVote) add(R.string.notification_type_vote)
            }
        }
    }
    val nMax = when {
        isMisskey -> 6
        else -> 5
    }
    return when {
        // 全部か皆無なら部分表記は要らない
        list.isEmpty() || list.size == nMax -> ""
        else -> "(${list.joinToString(", ") { context.getString(it) }})"
    }
}

suspend fun Column.loadProfileAccount(
    client: TootApiClient,
    parser: TootParser,
    bForceReload: Boolean,
): TootApiResult? =
    when {
        // リロード不要なら何もしない
        this.whoAccount != null && !bForceReload -> null

        isMisskey -> client.request(
            "/api/users/show",
            accessInfo.putMisskeyApiToken().apply {
                put("userId", profileId)
            }.toPostRequestBuilder()
        )?.also { result1 ->
            // ユーザリレーションの取り扱いのため、別のparserを作ってはいけない
            parser.misskeyDecodeProfilePin = true
            try {
                TootAccountRef.mayNull(parser, parser.account(result1.jsonObject))?.also { a ->
                    this.whoAccount = a
                    client.publishApiProgress("") // カラムヘッダの再表示
                }
            } finally {
                parser.misskeyDecodeProfilePin = false
            }
        }

        else -> client.request(
            "/api/v1/accounts/$profileId"
        )?.also { result1 ->
            TootAccountRef.mayNull(parser, parser.account(result1.jsonObject))?.also { a ->
                this.whoAccount = a

                this.whoFeaturedTags = null
                client.request("/api/v1/accounts/$profileId/featured_tags")
                    ?.also { result2 ->

                        this.whoFeaturedTags =
                            TootTag.parseListOrNull(parser, result2.jsonArray)
                    }

                client.publishApiProgress("") // カラムヘッダの再表示
            }
        }
    }

fun Column.loadSearchDesc(@RawRes rawEn: Int, @RawRes rawJa: Int): String {
    @RawRes val rawId = if ("ja" == context.getString(R.string.language_code)) rawJa else rawEn
    return context.loadRawResource(rawId).decodeUTF8()
}

suspend fun Column.updateRelation(
    client: TootApiClient,
    list: ArrayList<TimelineItem>?,
    whoRef: TootAccountRef?,
    parser: TootParser,
) {
    if (accessInfo.isPseudo) return

    val env = UserRelationLoader(this)

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
    list: List<TimelineItem>?,
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
        idMin = Column.reMaxId.matcher(result?.linkOlder ?: "").findOrNull()
            ?.let {
                EntityId(it.groupEx(1)!!)
            }

        idMax = Column.reMinId.matcher(result?.linkNewer ?: "").findOrNull()
            ?.let {
                // min_idとsince_idの読み分けは現在利用してない it.groupEx(1)=="min_id"
                EntityId(it.groupEx(2)!!)
            }
    }

    return Pair(idMin, idMax)
}
// int scroll_hack;

// return true if list bottom may have unread remain
// カラムが既に範囲を持ってる場合、その範囲を拡張する。
fun Column.saveRange(
    bBottom: Boolean,
    bTop: Boolean,
    result: TootApiResult?,
    list: List<TimelineItem>?,
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

// no return value: can't find there may be more items.
fun Column.saveRangeTop(result: TootApiResult?, list: List<TimelineItem>?) {
    saveRange(false, bTop = true, result = result, list = list)
}

fun Column.addRange(
    bBottom: Boolean,
    path: String,
    delimiter: Char = if (-1 == path.indexOf('?')) '?' else '&',
) = if (bBottom) {
    if (idOld != null) "$path${delimiter}max_id=$idOld" else path
} else {
    if (idRecent != null) "$path${delimiter}since_id=$idRecent" else path
}

fun Column.addRangeMin(
    path: String,
    delimiter: Char = if (-1 != path.indexOf('?')) '&' else '?',
) = if (idRecent == null) path else "$path${delimiter}min_id=$idRecent"

fun Column.toAdapterIndex(listIndex: Int): Int {
    return if (type.headerType != null) listIndex + 1 else listIndex
}

fun Column.toListIndex(adapterIndex: Int): Int {
    return if (type.headerType != null) adapterIndex - 1 else adapterIndex
}

fun Column.saveScrollPosition() {
    try {
        if (viewHolder?.saveScrollPosition() == true) {
            val ss = this.scrollSave
            if (ss != null) {
                val idx = toListIndex(ss.adapterIndex)
                if (0 <= idx && idx < listData.size) {
                    val item = listData[idx]
                    this.lastViewingItemId = item.getOrderId()
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
    head: Boolean = false,
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
    head: Boolean = false,
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
    head: Boolean = false,
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
    head: Boolean = false,
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
    head: Boolean = false,
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
        accessInfo.isPseudo -> ColumnType.FollowingMastodonPseudo
        else -> ColumnType.FollowingMastodon
    }

fun Column.dispatchProfileTabFollowers() =
    when {
        misskeyVersion >= 11 -> ColumnType.FollowersMisskey11
        isMisskey -> ColumnType.FollowersMisskey10
        accessInfo.isPseudo -> ColumnType.FollowersMastodonPseudo
        else -> ColumnType.FollowersMastodon
    }

fun ColumnTask.dispatchProfileTabStatus() =
    column.dispatchProfileTabStatus()

fun ColumnTask.dispatchProfileTabFollowing() =
    column.dispatchProfileTabFollowing()

fun ColumnTask.dispatchProfileTabFollowers() =
    column.dispatchProfileTabFollowers()

suspend fun Column.loadListInfo(client: TootApiClient, bForceReload: Boolean) {
    val parser = TootParser(context, accessInfo)
    if (bForceReload || this.listInfo == null) {
        val result = if (isMisskey) {
            client.request(
                "/api/users/lists/show",
                makeMisskeyBaseParameter(parser).apply {
                    put("listId", profileId)
                }.toPostRequestBuilder()
            )
        } else {
            client.request("/api/v1/lists/$profileId")
        }

        val jsonObject = result?.jsonObject
        if (jsonObject != null) {
            val data = parseItem(::TootList, parser, jsonObject)
            if (data != null) {
                this.listInfo = data
                client.publishApiProgress("") // カラムヘッダの再表示
            }
        }
    }
}

suspend fun Column.loadAntennaInfo(client: TootApiClient, bForceReload: Boolean) {
    val parser = TootParser(context, accessInfo)
    if (bForceReload || this.antennaInfo == null) {

        val result = if (isMisskey) {
            client.request(
                "/api/antennas/show",
                makeMisskeyBaseParameter(parser).apply {
                    put("antennaId", profileId)
                }.toPostRequestBuilder()
            )
        } else {
            TootApiResult("antenna feature is not supported on Mastodon")
        }

        val jsonObject = result?.jsonObject
        if (jsonObject != null) {
            val data = parseItem(::MisskeyAntenna, jsonObject)
            if (data != null) {
                this.antennaInfo = data
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

///////////////////////////////////////////////////////////////////////

private const val DIR_BACKGROUND_IMAGE = "columnBackground"

fun getBackgroundImageDir(context: Context): File {
    val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    if (externalDir == null) {
        log.e("getExternalFilesDir is null.")
    } else {
        val state = Environment.getExternalStorageState()
        if (state != Environment.MEDIA_MOUNTED) {
            log.e("getExternalStorageState: $state")
        } else {
            log.i("externalDir: $externalDir")
            externalDir.mkdir()
            val backgroundDir = File(externalDir, DIR_BACKGROUND_IMAGE)
            backgroundDir.mkdir()
            log.i("backgroundDir: $backgroundDir exists=${backgroundDir.exists()}")
            return backgroundDir
        }
    }
    val backgroundDir = context.getDir(DIR_BACKGROUND_IMAGE, Context.MODE_PRIVATE)
    log.i("backgroundDir: $backgroundDir exists=${backgroundDir.exists()}")
    return backgroundDir
}
