package jp.juggler.subwaytooter.column

import android.content.Context
import android.view.Gravity
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.ApiPath
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.entity.TimelineItem
import jp.juggler.subwaytooter.api.entity.TootAccountRef.Companion.tootAccountRef
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.api.entity.TootMessageHolder
import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.api.finder.mastodonFollowSuggestion2ListParser
import jp.juggler.subwaytooter.api.finder.misskey11FollowersParser
import jp.juggler.subwaytooter.api.finder.misskey11FollowingParser
import jp.juggler.subwaytooter.api.finder.misskeyArrayFinderUsers
import jp.juggler.subwaytooter.api.finder.misskeyCustomParserBlocks
import jp.juggler.subwaytooter.api.finder.misskeyCustomParserFavorites
import jp.juggler.subwaytooter.api.finder.misskeyCustomParserFollowRequest
import jp.juggler.subwaytooter.api.finder.misskeyCustomParserMutes
import jp.juggler.subwaytooter.search.MspHelper.loadingMSP
import jp.juggler.subwaytooter.search.MspHelper.refreshMSP
import jp.juggler.subwaytooter.search.NotestockHelper.loadingNotestock
import jp.juggler.subwaytooter.search.NotestockHelper.refreshNotestock
import jp.juggler.subwaytooter.search.TootsearchHelper.loadingTootsearch
import jp.juggler.subwaytooter.search.TootsearchHelper.refreshTootsearch
import jp.juggler.subwaytooter.streaming.StreamSpec
import jp.juggler.subwaytooter.table.daoAcctColor
import jp.juggler.util.data.JsonArray
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.appendIf
import jp.juggler.util.data.ellipsizeDot3
import jp.juggler.util.data.jsonArrayOf
import jp.juggler.util.data.jsonObjectOf
import jp.juggler.util.data.notEmpty
import jp.juggler.util.data.toJsonArray
import jp.juggler.util.log.LogCategory
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/*
    カラム種別ごとの処理
    - Loading : 初回ロード
    - Refresh : (始端/終端の)差分更新
    - Gap : ギャップ部分の読み込み

    loading,refresh,gap はそれぞれ this の種類が異なるので注意
    同じ関数を呼び出してるように見えても実際には異なるクラスの異なる関数を呼び出している場合がある
 */

private val unsupportedRefresh: suspend ColumnTask_Refresh.(client: TootApiClient) -> TootApiResult? =
    { TootApiResult("edge reading not supported.") }

private val unsupportedGap: suspend ColumnTask_Gap.(client: TootApiClient) -> TootApiResult? =
    { TootApiResult("gap reading not supported.") }

private val unusedIconId: (Acct) -> Int =
    { R.drawable.ic_question }

private val unusedName: (context: Context) -> String =
    { "?" }

private val unusedName2: Column.(long: Boolean) -> String? =
    { null }

private val gapDirectionNone: Column.(head: Boolean) -> Boolean = { false }

private val gapDirectionBoth: Column.(head: Boolean) -> Boolean = { true }

private val gapDirectionHead: Column.(head: Boolean) -> Boolean = { it }

// Pagination in some Mastodon APIs has no relation between the content ID and the pagination ID,
// so the app cannot filter the data using the content ID.
// (max_id..since_id) API request is worked,
// but (max_id..min_id) API is not worked on v3.2.0
// related: https://github.com/tootsuite/mastodon/pull/14776
private val gapDirectionMastodonWorkaround: Column.(head: Boolean) -> Boolean =
    { head ->
        when {
            isMisskey -> true
            isMastodon -> head
            else -> false
        }
    }

private val streamingTypeYes: Column.() -> Boolean = { true }
private val streamingTypeNo: Column.() -> Boolean = { false }

enum class ColumnType(
    val id: Int = 0,
    val iconId: (Acct) -> Int = unusedIconId,
    val name1: (context: Context) -> String = unusedName,
    val name2: Column.(long: Boolean) -> String? = unusedName2,
    val loading: suspend ColumnTask_Loading.(client: TootApiClient) -> TootApiResult?,
    val refresh: suspend ColumnTask_Refresh.(client: TootApiClient) -> TootApiResult? = unsupportedRefresh,
    val gap: suspend ColumnTask_Gap.(client: TootApiClient) -> TootApiResult? = unsupportedGap,
    val bAllowPseudo: Boolean = true,
    val bAllowMisskey: Boolean = true,
    val bAllowMastodon: Boolean = true,
    val headerType: HeaderType? = null,
    val gapDirection: Column.(head: Boolean) -> Boolean = gapDirectionNone,
    val canAutoRefresh: Boolean = false,

    val canStreamingMastodon: Column.() -> Boolean,
    val canStreamingMisskey: Column.() -> Boolean,

    val streamKeyMastodon: Column.() -> JsonObject? = { null },
    val streamFilterMastodon: Column.(JsonArray, TimelineItem) -> Boolean = { _, _ -> true },

    val streamNameMisskey: String? = null,
    val streamParamMisskey: Column.() -> JsonObject? = { null },
    val streamPathMisskey9: Column.() -> String? = { null },
) {

    ProfileStatusMastodon(
        loading = { client ->
            val (instance, instanceResult) = TootInstance.get(client)
            if (instance == null) {
                instanceResult
            } else {
                val path = column.makeProfileStatusesUrl(column.profileId)

                if (instance.versionGE(TootInstance.VERSION_1_6)
                // 将来的に正しく判定できる見込みがないので、Pleroma条件でのフィルタは行わない
                // && instance.instanceType != TootInstance.InstanceType.Pleroma
                ) {
                    getStatusesPinned(client, "$path&pinned=true")
                }

                getStatusList(client, path)
            }
        },

        refresh = { client ->
            getStatusList(
                client,
                column.makeProfileStatusesUrl(column.profileId)
            )
        },
        gap = { client ->
            getStatusList(
                client,
                column.makeProfileStatusesUrl(column.profileId),
                mastodonFilterByIdRange = true,
            )
        },
        gapDirection = gapDirectionBoth,

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    ProfileStatusMisskey(

        loading = { client ->
            // 固定トゥートの取得
            val pinnedNotes = column.whoAccount?.get()?.pinnedNotes
            if (pinnedNotes != null) {
                this.listPinned = addWithFilterStatus(null, pinnedNotes)
            }

            // 通常トゥートの取得
            getStatusList(
                client,
                ApiPath.PATH_MISSKEY_PROFILE_STATUSES,
                misskeyParams = column.makeMisskeyParamsProfileStatuses(parser)
            )
        },
        refresh = { client ->
            getStatusList(
                client,
                ApiPath.PATH_MISSKEY_PROFILE_STATUSES,
                misskeyParams = column.makeMisskeyParamsProfileStatuses(parser)
            )
        },
        gap = { client ->
            getStatusList(
                client,
                ApiPath.PATH_MISSKEY_PROFILE_STATUSES,
                mastodonFilterByIdRange = true,
                misskeyParams = column.makeMisskeyParamsProfileStatuses(parser)
            )
        },
        gapDirection = gapDirectionMastodonWorkaround,

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,

        ),

    FollowingMastodon(

        loading = { client ->
            getAccountList(
                client,
                String.format(Locale.JAPAN, ApiPath.PATH_ACCOUNT_FOLLOWING, column.profileId),
                emptyMessage = context.getString(R.string.none_or_hidden_following)
            )
        },
        refresh = { client ->
            getAccountList(
                client,
                String.format(Locale.JAPAN, ApiPath.PATH_ACCOUNT_FOLLOWING, column.profileId)
            )
        },
        gap = { client ->
            getAccountList(
                client,
                String.format(Locale.JAPAN, ApiPath.PATH_ACCOUNT_FOLLOWING, column.profileId),
                mastodonFilterByIdRange = false,
            )
        },
        gapDirection = gapDirectionMastodonWorkaround,

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    FollowingMastodonPseudo(

        loading = {
            column.idRecent = null
            column.idOld = null
            listTmp = addOne(
                listTmp,
                TootMessageHolder(context.getString(R.string.pseudo_account_cant_get_follow_list))
            )
            TootApiResult()
        },

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    FollowingMisskey10(

        loading = { client ->
            column.pagingType = ColumnPagingType.Cursor
            getAccountList(
                client,
                ApiPath.PATH_MISSKEY_PROFILE_FOLLOWING,
                emptyMessage = context.getString(R.string.none_or_hidden_following),
                misskeyParams = column.makeMisskeyParamsUserId(parser),
                arrayFinder = misskeyArrayFinderUsers
            )
        },
        refresh = { client ->
            getAccountList(
                client,
                ApiPath.PATH_MISSKEY_PROFILE_FOLLOWING,
                misskeyParams = column.makeMisskeyParamsUserId(parser),
                arrayFinder = misskeyArrayFinderUsers
            )
        },
        gap = { client ->
            getAccountList(
                client,
                ApiPath.PATH_MISSKEY_PROFILE_FOLLOWING,
                mastodonFilterByIdRange = false,
                misskeyParams = column.makeMisskeyParamsUserId(parser),
                arrayFinder = misskeyArrayFinderUsers
            )
        },
        gapDirection = gapDirectionMastodonWorkaround,

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    FollowingMisskey11(

        loading = { client ->
            column.pagingType = ColumnPagingType.Default
            getAccountList(
                client,
                ApiPath.PATH_MISSKEY_PROFILE_FOLLOWING,
                emptyMessage = context.getString(R.string.none_or_hidden_following),
                misskeyParams = column.makeMisskeyParamsUserId(parser),
                listParser = misskey11FollowingParser
            )
        },
        refresh = { client ->
            getAccountList(
                client,
                ApiPath.PATH_MISSKEY_PROFILE_FOLLOWING,
                misskeyParams = column.makeMisskeyParamsUserId(parser),
                listParser = misskey11FollowingParser
            )
        },
        gap = { client ->
            getAccountList(
                client,
                ApiPath.PATH_MISSKEY_PROFILE_FOLLOWING,
                mastodonFilterByIdRange = false,
                misskeyParams = column.makeMisskeyParamsUserId(parser),
                listParser = misskey11FollowingParser
            )
        },
        gapDirection = gapDirectionMastodonWorkaround,

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    FollowersMisskey11(

        loading = { client ->
            column.pagingType = ColumnPagingType.Default
            getAccountList(
                client,
                ApiPath.PATH_MISSKEY_PROFILE_FOLLOWERS,
                emptyMessage = context.getString(R.string.none_or_hidden_followers),
                misskeyParams = column.makeMisskeyParamsUserId(parser),
                listParser = misskey11FollowersParser
            )
        },

        refresh = { client ->
            getAccountList(
                client,
                ApiPath.PATH_MISSKEY_PROFILE_FOLLOWERS,
                misskeyParams = column.makeMisskeyParamsUserId(parser),
                listParser = misskey11FollowersParser
            )
        },
        gap = { client ->
            getAccountList(
                client,
                ApiPath.PATH_MISSKEY_PROFILE_FOLLOWING,
                mastodonFilterByIdRange = false,
                misskeyParams = column.makeMisskeyParamsUserId(parser),
                listParser = misskey11FollowersParser
            )
        },
        gapDirection = gapDirectionMastodonWorkaround,

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    FollowersMisskey10(

        loading = { client ->
            column.pagingType = ColumnPagingType.Cursor
            getAccountList(
                client,
                ApiPath.PATH_MISSKEY_PROFILE_FOLLOWERS,
                emptyMessage = context.getString(R.string.none_or_hidden_followers),
                misskeyParams = column.makeMisskeyParamsUserId(parser),
                arrayFinder = misskeyArrayFinderUsers
            )
        },

        refresh = { client ->
            getAccountList(
                client,
                ApiPath.PATH_MISSKEY_PROFILE_FOLLOWERS,
                misskeyParams = column.makeMisskeyParamsUserId(parser),
                arrayFinder = misskeyArrayFinderUsers
            )
        },
        gap = { client ->
            getAccountList(
                client,
                ApiPath.PATH_MISSKEY_PROFILE_FOLLOWERS,
                mastodonFilterByIdRange = false,
                misskeyParams = column.makeMisskeyParamsUserId(parser)
            )
        },
        gapDirection = gapDirectionMastodonWorkaround,

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    FollowersMastodonPseudo(

        loading = {
            column.idRecent = null
            column.idOld = null
            listTmp = addOne(
                listTmp,
                TootMessageHolder(context.getString(R.string.pseudo_account_cant_get_follow_list))
            )
            TootApiResult()
        },

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    FollowersMastodon(

        loading = { client ->
            getAccountList(
                client,
                String.format(Locale.JAPAN, ApiPath.PATH_ACCOUNT_FOLLOWERS, column.profileId),
                emptyMessage = context.getString(R.string.none_or_hidden_followers)
            )
        },

        refresh = { client ->
            getAccountList(
                client,
                String.format(Locale.JAPAN, ApiPath.PATH_ACCOUNT_FOLLOWERS, column.profileId)
            )
        },
        gap = { client ->
            getAccountList(
                client,
                String.format(Locale.JAPAN, ApiPath.PATH_ACCOUNT_FOLLOWERS, column.profileId),
                mastodonFilterByIdRange = false
            )
        },
        gapDirection = gapDirectionMastodonWorkaround,

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    TabStatus(
        loading = { dispatchProfileTabStatus().loading(this, it) },
        refresh = { dispatchProfileTabStatus().refresh(this, it) },
        gap = { dispatchProfileTabStatus().gap(this, it) },
        gapDirection = { dispatchProfileTabStatus().gapDirection(this, it) },

        canStreamingMastodon = { dispatchProfileTabStatus().canStreamingMastodon(this) },
        canStreamingMisskey = { dispatchProfileTabStatus().canStreamingMisskey(this) },
    ),

    TabFollowing(
        loading = { dispatchProfileTabFollowing().loading(this, it) },
        refresh = { dispatchProfileTabFollowing().refresh(this, it) },
        gap = { dispatchProfileTabFollowing().gap(this, it) },
        gapDirection = { dispatchProfileTabFollowing().gapDirection(this, it) },

        canStreamingMastodon = { dispatchProfileTabFollowing().canStreamingMastodon(this) },
        canStreamingMisskey = { dispatchProfileTabFollowing().canStreamingMisskey(this) },
    ),

    TabFollowers(
        loading = { dispatchProfileTabFollowers().loading(this, it) },
        refresh = { dispatchProfileTabFollowers().refresh(this, it) },
        gap = { dispatchProfileTabFollowers().gap(this, it) },
        gapDirection = { dispatchProfileTabFollowers().gapDirection(this, it) },

        canStreamingMastodon = { dispatchProfileTabFollowers().canStreamingMastodon(this) },
        canStreamingMisskey = { dispatchProfileTabFollowers().canStreamingMisskey(this) },

        ),

    HOME(
        id = 1,
        iconId = { R.drawable.ic_home },
        name1 = { it.getString(R.string.home) },

        loading = { client ->
            val ra = getAnnouncements(client, force = true)
            when {
                ra == null || ra.error != null -> ra
                else -> getStatusList(client, column.makeHomeTlUrl())
            }
        },
        refresh = { client ->
            val ra = getAnnouncements(client)
            when {
                ra == null || ra.error != null -> ra
                else -> getStatusList(client, column.makeHomeTlUrl())
            }
        },
        gap = { client ->
            val ra = getAnnouncements(client)
            when {
                ra == null || ra.error != null -> ra
                else -> getStatusList(
                    client,
                    column.makeHomeTlUrl(),
                    mastodonFilterByIdRange = true
                )
            }
        },
        gapDirection = gapDirectionBoth,
        bAllowPseudo = false,

        canAutoRefresh = true,

        canStreamingMastodon = streamingTypeYes,
        canStreamingMisskey = streamingTypeYes,

        streamKeyMastodon = {
            jsonObjectOf(StreamSpec.STREAM to "user")
        },
        streamFilterMastodon = { stream, item ->
            when {
                item !is TootStatus -> false
                unmatchMastodonStream(stream, "user") -> false
                else -> true
            }
        },

        streamNameMisskey = "homeTimeline",
        streamParamMisskey = { null },
        streamPathMisskey9 = { "/" },
    ),

    LOCAL(
        id = 2,
        iconId = { R.drawable.ic_run },
        name1 = { it.getString(R.string.local_timeline) },
        bAllowPseudo = true,

        loading = { client -> getStatusList(client, column.makePublicLocalUrl()) },
        refresh = { client -> getStatusList(client, column.makePublicLocalUrl()) },
        gap = { client ->
            getStatusList(
                client,
                column.makePublicLocalUrl(),
                mastodonFilterByIdRange = true
            )
        },
        gapDirection = gapDirectionBoth,

        canAutoRefresh = true,

        canStreamingMastodon = streamingTypeYes,
        canStreamingMisskey = streamingTypeYes,

        streamKeyMastodon = { jsonObjectOf(StreamSpec.STREAM to streamKeyLtl()) },
        streamFilterMastodon = { stream, item ->
            when {
                item !is TootStatus -> false
                unmatchMastodonStream(stream, streamKeyLtl()) -> false
                else -> true
            }
        },
        streamNameMisskey = "localTimeline",
        streamParamMisskey = { null },
        streamPathMisskey9 = { "/local-timeline" },
    ),

    FEDERATE(
        3,
        iconId = { R.drawable.ic_bike },
        name1 = { it.getString(R.string.federate_timeline) },
        bAllowPseudo = true,

        loading = { client -> getStatusList(client, column.makePublicFederateUrl()) },
        refresh = { client -> getStatusList(client, column.makePublicFederateUrl()) },

        gap = { client ->
            getStatusList(
                client,
                column.makePublicFederateUrl(),
                mastodonFilterByIdRange = true
            )
        },
        gapDirection = gapDirectionBoth,

        canAutoRefresh = true,

        canStreamingMastodon = streamingTypeYes,
        canStreamingMisskey = streamingTypeYes,
        streamKeyMastodon = {
            jsonObjectOf(StreamSpec.STREAM to streamKeyFtl())
        },

        streamFilterMastodon = { stream, item ->
            when {
                item !is TootStatus -> false
                unmatchMastodonStream(stream, streamKeyFtl()) -> false
                remoteOnly && item.account.acct == accessInfo.acct -> false
                withAttachment && item.media_attachments.isNullOrEmpty() -> false
                else -> true
            }
        },

        streamNameMisskey = "globalTimeline",
        streamParamMisskey = { null },
        streamPathMisskey9 = { "/global-timeline" },
    ),

    MISSKEY_HYBRID(
        27,
        iconId = { R.drawable.ic_share },
        name1 = { it.getString(R.string.misskey_hybrid_timeline) },
        bAllowPseudo = false,
        bAllowMastodon = false,

        loading = { client -> getStatusList(client, column.makeMisskeyHybridTlUrl()) },
        refresh = { client -> getStatusList(client, column.makeMisskeyHybridTlUrl()) },
        gap = { client ->
            getStatusList(
                client,
                column.makeMisskeyHybridTlUrl(),
                mastodonFilterByIdRange = true
            )
        },
        gapDirection = gapDirectionBoth,

        canAutoRefresh = true,

        canStreamingMastodon = streamingTypeYes,
        canStreamingMisskey = streamingTypeYes,

        streamNameMisskey = "hybridTimeline",
        streamParamMisskey = { null },
        streamPathMisskey9 = { "/hybrid-timeline" },
    ),

    DOMAIN_TIMELINE(
        id = 38,
        iconId = { R.drawable.ic_domain },
        name1 = { it.getString(R.string.domain_timeline) },
        name2 = {
            context.getString(
                R.string.domain_timeline_of,
                instanceUri.notEmpty() ?: "?"
            )
        },
        bAllowPseudo = true, // サイドメニューから開けないのでこの値は参照されない
        loading = { client -> getStatusList(client, column.makeDomainTimelineUrl()) },
        refresh = { client -> getStatusList(client, column.makeDomainTimelineUrl()) },
        gap = { client ->
            getStatusList(
                client,
                column.makeDomainTimelineUrl(),
                mastodonFilterByIdRange = true
            )
        },
        gapDirection = gapDirectionBoth,

        canAutoRefresh = true,

        canStreamingMastodon = streamingTypeYes,
        canStreamingMisskey = streamingTypeYes,

        streamKeyMastodon = {
            jsonObjectOf(StreamSpec.STREAM to streamKeyDomainTl(), "domain" to instanceUri)
        },

        streamFilterMastodon = { stream, item ->
            when {
                item !is TootStatus -> false
                unmatchMastodonStream(stream, streamKeyDomainTl(), instanceUri) -> false
                withAttachment && item.media_attachments.isNullOrEmpty() -> false
                else -> true
            }
        }
    ),

    LOCAL_AROUND(
        29,
        iconId = { R.drawable.ic_run },
        name1 = { it.getString(R.string.ltl_around) },
        name2 = {
            context.getString(
                R.string.ltl_around_of,
                statusId?.toString() ?: "null"
            )
        },

        loading = { client -> getPublicTlAroundTime(client, column.makePublicLocalUrl()) },
        refresh = { client -> getStatusList(client, column.makePublicLocalUrl(), useMinId = true) },

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    FEDERATED_AROUND(
        30,
        iconId = { R.drawable.ic_bike },
        name1 = { it.getString(R.string.ftl_around) },
        name2 = {
            context.getString(
                R.string.ftl_around_of,
                statusId?.toString() ?: "null"
            )
        },

        loading = { client -> getPublicTlAroundTime(client, column.makePublicFederateUrl()) },
        refresh = { client ->
            getStatusList(
                client,
                column.makePublicFederateUrl(),
                useMinId = true
            )
        },

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    PROFILE(
        4,
        iconId = { R.drawable.ic_account_box },
        name1 = { it.getString(R.string.profile) },
        name2 = {
            val who = whoAccount?.get()
            context.getString(
                R.string.profile_of,
                when (who) {
                    null -> profileId.toString()
                    else -> daoAcctColor.getNickname(accessInfo, who)
                }
            )
        },
        bAllowPseudo = false,
        headerType = HeaderType.Profile,

        loading = { client ->
            val whoResult = column.loadProfileAccount(client, parser, true)
            when {
                client.isApiCancelled() || column.whoAccount == null -> whoResult
                else -> column.profileTab.ct.loading(this, client)
            }
        },

        refresh = { client ->
            column.loadProfileAccount(client, parser, false)
            column.profileTab.ct.refresh(this, client)
        },

        gap = { column.profileTab.ct.gap(this, it) },
        gapDirection = { profileTab.ct.gapDirection(this, it) },

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    FAVOURITES(
        5,
        iconId = { R.drawable.ic_star_outline },
        name1 = { it.getString(R.string.favourites) },
        bAllowPseudo = false,

        loading = { client ->
            if (isMisskey) {
                getStatusList(
                    client,
                    ApiPath.PATH_MISSKEY_FAVORITES,
                    misskeyParams = column.makeMisskeyTimelineParameter(parser),
                    listParser = misskeyCustomParserFavorites
                )
            } else {
                getStatusList(client, ApiPath.PATH_FAVOURITES)
            }
        },

        refresh = { client ->
            if (isMisskey) {
                getStatusList(
                    client,
                    ApiPath.PATH_MISSKEY_FAVORITES,
                    misskeyParams = column.makeMisskeyTimelineParameter(parser),
                    listParser = misskeyCustomParserFavorites
                )
            } else {
                getStatusList(client, ApiPath.PATH_FAVOURITES)
            }
        },

        gap = { client ->
            if (isMisskey) {
                getStatusList(
                    client,
                    ApiPath.PATH_MISSKEY_FAVORITES,
                    mastodonFilterByIdRange = false,
                    misskeyParams = column.makeMisskeyTimelineParameter(parser),
                    listParser = misskeyCustomParserFavorites
                )
            } else {
                getStatusList(
                    client,
                    ApiPath.PATH_FAVOURITES,
                    mastodonFilterByIdRange = false
                )
            }
        },
        gapDirection = gapDirectionMastodonWorkaround,

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    REACTIONS(
        42,
        iconId = { R.drawable.ic_face },
        name1 = { it.getString(R.string.reactioned_posts) },
        bAllowPseudo = false,
        bAllowMisskey = false,

        loading = { client ->
            if (isMisskey) {
                getStatusList(
                    client,
                    ApiPath.PATH_M544_REACTIONS,
                    misskeyParams = column.makeMisskeyTimelineParameter(parser),
                    listParser = misskeyCustomParserFavorites
                )
            } else {
                getStatusList(client, column.makeReactionsUrl())
            }
        },

        refresh = { client ->
            if (isMisskey) {
                getStatusList(
                    client,
                    ApiPath.PATH_M544_REACTIONS,
                    misskeyParams = column.makeMisskeyTimelineParameter(parser),
                    listParser = misskeyCustomParserFavorites
                )
            } else {
                getStatusList(client, column.makeReactionsUrl())
            }
        },

        gap = { client ->
            if (isMisskey) {
                getStatusList(
                    client,
                    ApiPath.PATH_M544_REACTIONS,
                    mastodonFilterByIdRange = false,
                    misskeyParams = column.makeMisskeyTimelineParameter(parser),
                    listParser = misskeyCustomParserFavorites
                )
            } else {
                getStatusList(
                    client,
                    column.makeReactionsUrl(),
                    mastodonFilterByIdRange = false
                )
            }
        },
        gapDirection = gapDirectionMastodonWorkaround,

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    BOOKMARKS(
        37,
        iconId = { R.drawable.ic_bookmark },
        name1 = { it.getString(R.string.bookmarks) },
        bAllowPseudo = false,

        loading = { client ->
            if (isMisskey) {
                TootApiResult("Misskey has no bookmarks feature.")
            } else {
                getStatusList(client, ApiPath.PATH_BOOKMARKS)
            }
        },

        refresh = { client ->
            if (isMisskey) {
                TootApiResult("Misskey has no bookmarks feature.")
            } else {
                getStatusList(client, ApiPath.PATH_BOOKMARKS)
            }
        },

        gap = { client ->
            if (isMisskey) {
                TootApiResult("Misskey has no bookmarks feature.")
            } else {
                getStatusList(client, ApiPath.PATH_BOOKMARKS, mastodonFilterByIdRange = false)
            }
        },
        gapDirection = gapDirectionMastodonWorkaround,

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    NOTIFICATIONS(
        7,
        iconId = { R.drawable.ic_announcement },
        name1 = { it.getString(R.string.notifications) },
        name2 = {
            context.getString(R.string.notifications) + getNotificationTypeString()
        },

        loading = { client -> getNotificationList(client) },
        refresh = { client -> getNotificationList(client) },
        gap = { client -> getNotificationList(client, mastodonFilterByIdRange = true) },
        gapDirection = gapDirectionBoth,

        bAllowPseudo = false,

        canAutoRefresh = true,

        canStreamingMastodon = streamingTypeYes,
        canStreamingMisskey = streamingTypeYes,

        streamKeyMastodon = {
            jsonObjectOf(StreamSpec.STREAM to "user")
        },
        streamFilterMastodon = { stream, item ->
            when {
                item !is TootNotification -> false
                unmatchMastodonStream(stream, "user") -> false
                else -> true
            }
        },
        streamNameMisskey = "main",
        streamParamMisskey = { null },
        streamPathMisskey9 = { "/" },
    ),

    NOTIFICATION_FROM_ACCT(
        35,
        iconId = { R.drawable.ic_announcement },
        name1 = { it.getString(R.string.notifications_from_acct) },
        name2 = {
            context.getString(
                R.string.notifications_from,
                hashtagAcct
            ) + getNotificationTypeString()
        },

        loading = { client -> getNotificationList(client, column.hashtagAcct) },
        refresh = { client -> getNotificationList(client, column.hashtagAcct) },
        gap = { client ->
            getNotificationList(client, column.hashtagAcct, mastodonFilterByIdRange = true)
        },
        gapDirection = gapDirectionBoth,

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    CONVERSATION(
        8,
        iconId = { R.drawable.ic_forum },
        name1 = { it.getString(R.string.conversation) },
        name2 = {
            context.getString(
                R.string.conversation_around,
                statusId?.toString() ?: "null"
            )
        },
        loading = { client -> getConversation(client) },

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    CONVERSATION_WITH_REFERENCE(
        47,
        iconId = { R.drawable.ic_link },
        name1 = { it.getString(R.string.conversation_with_reference) },
        name2 = { context.getString(R.string.conversation_with_reference) },
        loading = { client -> getConversation(client, withReference = true) },

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    HASHTAG(
        9,
        iconId = { R.drawable.ic_hashtag },
        name1 = { it.getString(R.string.hashtag) },
        name2 = {
            StringBuilder(
                context.getString(
                    R.string.hashtag_of,
                    hashtag.ellipsizeDot3(Column.HASHTAG_ELLIPSIZE)
                )
            )
                .appendHashtagExtra(this)
                .toString()
        },

        loading = { client ->
            if (isMisskey) {
                getStatusList(
                    client,
                    column.makeHashtagUrl(),
                    misskeyParams = column.makeHashtagParams(parser)

                )
            } else {
                getStatusList(client, column.makeHashtagUrl())
            }
        },

        refresh = { client ->
            if (isMisskey) {
                getStatusList(
                    client,
                    column.makeHashtagUrl(),
                    misskeyParams = column.makeHashtagParams(parser)
                )
            } else {
                getStatusList(client, column.makeHashtagUrl())
            }
        },

        gap = { client ->
            if (isMisskey) {
                getStatusList(
                    client,
                    column.makeHashtagUrl(),
                    mastodonFilterByIdRange = true,
                    misskeyParams = column.makeHashtagParams(parser)
                )
            } else {
                getStatusList(client, column.makeHashtagUrl(), mastodonFilterByIdRange = true)
            }
        },
        gapDirection = gapDirectionBoth,

        canAutoRefresh = true,

        canStreamingMastodon = streamingTypeYes,
        canStreamingMisskey = streamingTypeYes,

        streamKeyMastodon = {
            jsonObjectOf(
                StreamSpec.STREAM to streamKeyHashtagTl(),
                "tag" to hashtag
            )
        },

        streamFilterMastodon = { stream, item ->
            when {
                item !is TootStatus -> false
                //
                unmatchMastodonStream(stream, streamKeyHashtagTl(), hashtag) -> false
                instanceLocal && item.account.acct != accessInfo.acct -> false
                else -> this.checkHashtagExtra(item)
            }
        },
        // {"type":"connect","body":{"channel":"hashtag","id":"84970575","params":{"q":[["misskey"]]}}}
        streamNameMisskey = "hashtag",
        streamParamMisskey = { jsonObjectOf("q" to jsonArrayOf(jsonArrayOf(hashtag))) },
        // Misskey10 というかめいすきーでタグTLのストリーミングができるのか不明
        // streamPathMisskey10 = { "/???? ?q=${hashtag.encodePercent()}" },

    ),

    HASHTAG_FROM_ACCT(
        34,

        iconId = { R.drawable.ic_hashtag },
        name1 = { it.getString(R.string.hashtag_from_acct) },
        name2 = {
            StringBuilder(
                context.getString(
                    R.string.hashtag_of_from,
                    hashtag.ellipsizeDot3(Column.HASHTAG_ELLIPSIZE),
                    hashtagAcct
                )
            )
                .appendHashtagExtra(this)
                .toString()
        },

        loading = { client ->
            getStatusList(
                client,
                column.makeHashtagAcctUrl(client), // null if misskey
                misskeyParams = column.makeHashtagParams(parser)
            )
        },
        refresh = { client ->
            getStatusList(
                client,
                column.makeHashtagAcctUrl(client), // null if misskey
                misskeyParams = column.makeHashtagParams(parser)
            )
        },
        gap = { client ->
            getStatusList(
                client,
                column.makeHashtagAcctUrl(client), // null if misskey
                mastodonFilterByIdRange = true,
                misskeyParams = column.makeHashtagParams(parser)
            )
        },
        gapDirection = gapDirectionBoth,
        bAllowMisskey = false,

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    SEARCH(
        10,
        iconId = { R.drawable.ic_search },
        name1 = { it.getString(R.string.search) },
        name2 = { long ->
            when {
                long -> context.getString(R.string.search_of, searchQuery)
                else -> context.getString(R.string.search)
            }
        },
        bAllowPseudo = false,
        headerType = HeaderType.Search,

        loading = { client -> getSearch(client) },
        gap = { client -> getSearchGap(client) },
        gapDirection = gapDirectionHead,

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    // ミスキーのミュートとブロックののページングは misskey v10 の途中で変わった
    // https://github.com/syuilo/misskey/commit/f7069dcd18d72b52408a6bd80ad8f14492163e19
    // ST的には新しい方にだけ対応する

    MUTES(
        11,
        iconId = { R.drawable.ic_volume_off },
        name1 = { it.getString(R.string.muted_users) },
        bAllowPseudo = false,

        loading = { client ->
            when {
                isMisskey -> {
                    column.pagingType = ColumnPagingType.Default
                    getAccountList(
                        client,
                        ApiPath.PATH_MISSKEY_MUTES,
                        misskeyParams = accessInfo.putMisskeyApiToken(),
                        listParser = misskeyCustomParserMutes
                    )
                }

                else -> getAccountList(client, ApiPath.PATH_MUTES)
            }
        },

        refresh = { client ->
            when {
                isMisskey -> getAccountList(
                    client,
                    ApiPath.PATH_MISSKEY_MUTES,
                    misskeyParams = accessInfo.putMisskeyApiToken(),
                    arrayFinder = misskeyArrayFinderUsers,
                    listParser = misskeyCustomParserMutes
                )

                else -> getAccountList(client, ApiPath.PATH_MUTES)
            }
        },

        gap = { client ->
            when {
                isMisskey -> getAccountList(
                    client,
                    ApiPath.PATH_MISSKEY_MUTES,
                    mastodonFilterByIdRange = false,
                    misskeyParams = accessInfo.putMisskeyApiToken(),
                    arrayFinder = misskeyArrayFinderUsers,
                    listParser = misskeyCustomParserMutes
                )

                else -> getAccountList(
                    client,
                    ApiPath.PATH_MUTES,
                    mastodonFilterByIdRange = false
                )
            }
        },
        gapDirection = gapDirectionMastodonWorkaround,

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    BLOCKS(
        12,
        iconId = { R.drawable.ic_block },
        name1 = { it.getString(R.string.blocked_users) },
        bAllowPseudo = false,

        loading = { client ->
            when {
                isMisskey -> {
                    column.pagingType = ColumnPagingType.Default
                    getAccountList(
                        client,
                        ApiPath.PATH_MISSKEY_BLOCKS,
                        misskeyParams = accessInfo.putMisskeyApiToken(),
                        listParser = misskeyCustomParserBlocks
                    )
                }

                else -> getAccountList(client, ApiPath.PATH_BLOCKS)
            }
        },

        refresh = { client ->
            when {
                isMisskey -> {
                    getAccountList(
                        client,
                        ApiPath.PATH_MISSKEY_BLOCKS,
                        misskeyParams = accessInfo.putMisskeyApiToken(),
                        listParser = misskeyCustomParserBlocks
                    )
                }

                else -> getAccountList(client, ApiPath.PATH_BLOCKS)
            }
        },

        gap = { client ->
            when {
                isMisskey -> {
                    getAccountList(
                        client,
                        ApiPath.PATH_MISSKEY_BLOCKS,
                        mastodonFilterByIdRange = false,
                        misskeyParams = accessInfo.putMisskeyApiToken(),
                        listParser = misskeyCustomParserBlocks
                    )
                }

                else -> getAccountList(client, ApiPath.PATH_BLOCKS, mastodonFilterByIdRange = false)
            }
        },
        gapDirection = gapDirectionMastodonWorkaround,

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    FOLLOW_REQUESTS(
        13,
        iconId = { R.drawable.ic_follow_wait },
        name1 = { it.getString(R.string.follow_requests) },
        bAllowPseudo = false,

        loading = { client ->
            if (isMisskey) {
                column.pagingType = ColumnPagingType.None
                getAccountList(
                    client,
                    ApiPath.PATH_MISSKEY_FOLLOW_REQUESTS,
                    misskeyParams = accessInfo.putMisskeyApiToken(),
                    listParser = misskeyCustomParserFollowRequest
                )
            } else {
                getAccountList(client, ApiPath.PATH_FOLLOW_REQUESTS)
            }
        },
        refresh = { client ->
            if (isMisskey) {
                getAccountList(
                    client,
                    ApiPath.PATH_MISSKEY_FOLLOW_REQUESTS,
                    misskeyParams = accessInfo.putMisskeyApiToken(),
                    listParser = misskeyCustomParserFollowRequest
                )
            } else {
                getAccountList(client, ApiPath.PATH_FOLLOW_REQUESTS)
            }
        },
        gap = { client ->
            if (isMisskey) {
                getAccountList(
                    client,
                    ApiPath.PATH_MISSKEY_FOLLOW_REQUESTS,
                    mastodonFilterByIdRange = false,
                    misskeyParams = accessInfo.putMisskeyApiToken(),
                    listParser = misskeyCustomParserFollowRequest
                )
            } else {
                getAccountList(
                    client,
                    ApiPath.PATH_FOLLOW_REQUESTS,
                    mastodonFilterByIdRange = false
                )
            }
        },
        gapDirection = gapDirectionMastodonWorkaround,

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    BOOSTED_BY(
        14,
        iconId = { R.drawable.ic_repeat },
        name1 = { it.getString(R.string.boosted_by) },

        loading = { client ->
            getAccountList(
                client,
                String.format(Locale.JAPAN, ApiPath.PATH_BOOSTED_BY, column.statusId)
            )
        },
        refresh = { client ->
            getAccountList(
                client,
                String.format(Locale.JAPAN, ApiPath.PATH_BOOSTED_BY, postedStatusId)
            )
        },
        gap = { client ->
            getAccountList(
                client,
                String.format(Locale.JAPAN, ApiPath.PATH_BOOSTED_BY, column.statusId),
                mastodonFilterByIdRange = false,
            )
        },
        gapDirection = gapDirectionMastodonWorkaround,
        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    FAVOURITED_BY(
        15,
        iconId = { R.drawable.ic_star_outline },
        name1 = { it.getString(R.string.favourited_by) },

        loading = { client ->
            getAccountList(
                client,
                String.format(Locale.JAPAN, ApiPath.PATH_FAVOURITED_BY, column.statusId)
            )
        },
        refresh = { client ->
            getAccountList(
                client,
                String.format(Locale.JAPAN, ApiPath.PATH_FAVOURITED_BY, postedStatusId)
            )
        },
        gap = { client ->
            getAccountList(
                client,
                String.format(Locale.JAPAN, ApiPath.PATH_FAVOURITED_BY, column.statusId),
                mastodonFilterByIdRange = false,
            )
        },
        gapDirection = gapDirectionMastodonWorkaround,

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    DOMAIN_BLOCKS(
        16,
        iconId = { R.drawable.ic_cloud_off },
        name1 = { it.getString(R.string.blocked_domains) },
        bAllowPseudo = false,
        bAllowMisskey = false,

        loading = { client -> getDomainBlockList(client, ApiPath.PATH_DOMAIN_BLOCK) },
        refresh = { client -> getDomainList(client, ApiPath.PATH_DOMAIN_BLOCK) },

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    SEARCH_MSP(
        17,
        iconId = { R.drawable.ic_search },
        name1 = { it.getString(R.string.toot_search_msp) },
        name2 = { long ->
            when {
                long -> context.getString(R.string.toot_search_msp_of, searchQuery)
                else -> context.getString(R.string.toot_search_msp)
            }
        },
        headerType = HeaderType.Search,

        loading = { loadingMSP(it) },
        refresh = { refreshMSP(it) },

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    SEARCH_TS(
        22,
        iconId = { R.drawable.ic_search },
        name1 = { it.getString(R.string.toot_search_ts) },
        name2 = { long ->
            when {
                long -> context.getString(R.string.toot_search_ts_of, searchQuery)
                else -> context.getString(R.string.toot_search_ts)
            }
        },
        headerType = HeaderType.Search,

        loading = { loadingTootsearch(it) },
        refresh = { refreshTootsearch(it) },

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    SEARCH_NOTESTOCK(
        41,
        iconId = { R.drawable.ic_search },
        name1 = { it.getString(R.string.toot_search_notestock) },
        name2 = { long ->
            when {
                long -> context.getString(R.string.toot_search_notestock_of, searchQuery)
                else -> context.getString(R.string.toot_search_notestock)
            }
        },
        headerType = HeaderType.Search,

        loading = { loadingNotestock(it) },
        refresh = { refreshNotestock(it) },

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    INSTANCE_INFORMATION(
        18,
        iconId = { R.drawable.ic_info_outline },
        name1 = { it.getString(R.string.instance_information) },
        name2 = { long ->
            when {
                long -> context.getString(R.string.instance_information_of, instanceUri)
                else -> context.getString(R.string.instance_information)
            }
        },
        headerType = HeaderType.Instance,

        loading = { client ->
            val (ti, ri) = TootInstance.getEx(
                client,
                Host.parse(column.instanceUri),
                allowPixelfed = true,
                forceUpdate = true
            )
            if (ti != null) {
                column.instanceInformation = ti
                column.handshake = ri?.response?.handshake
            }
            ri
        },

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,

        ),

    LIST_LIST(
        19,
        iconId = { R.drawable.ic_list_list },
        name1 = { it.getString(R.string.lists) },
        bAllowPseudo = false,

        loading = { client ->
            if (isMisskey) {
                getListList(
                    client,
                    "/api/users/lists/list",
                    misskeyParams = column.makeMisskeyBaseParameter(parser)
                )
            } else {
                getListList(client, ApiPath.PATH_LIST_LIST)
            }
        },

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,

        ),

    LIST_TL(
        20,
        iconId = { R.drawable.ic_list_tl },
        name1 = { it.getString(R.string.list_timeline) },
        name2 = {
            context.getString(
                R.string.list_tl_of,
                listInfo?.title ?: profileId.toString()
            )
        },

        loading = { client ->
            column.loadListInfo(client, true)
            if (isMisskey) {
                getStatusList(
                    client,
                    column.makeListTlUrl(),
                    misskeyParams = column.makeMisskeyTimelineParameter(parser).apply {
                        put("listId", column.profileId)
                    }
                )
            } else {
                getStatusList(client, column.makeListTlUrl())
            }
        },

        refresh = { client ->
            column.loadListInfo(client, false)
            if (isMisskey) {
                getStatusList(
                    client,
                    column.makeListTlUrl(),
                    misskeyParams = column.makeMisskeyTimelineParameter(parser).apply {
                        put("listId", column.profileId)
                    }
                )
            } else {
                getStatusList(client, column.makeListTlUrl())
            }
        },

        gap = { client ->
            if (isMisskey) {
                getStatusList(
                    client,
                    column.makeListTlUrl(),
                    mastodonFilterByIdRange = true,
                    misskeyParams = column.makeMisskeyTimelineParameter(parser).apply {
                        put("listId", column.profileId)
                    }
                )
            } else {
                getStatusList(client, column.makeListTlUrl(), mastodonFilterByIdRange = true)
            }
        },
        gapDirection = gapDirectionBoth,

        canAutoRefresh = true,

        canStreamingMastodon = streamingTypeYes,
        canStreamingMisskey = streamingTypeYes,

        streamKeyMastodon = {
            jsonObjectOf(StreamSpec.STREAM to "list", "list" to profileId.toString())
        },

        streamFilterMastodon = { stream, item ->
            when {
                item !is TootStatus -> false
                unmatchMastodonStream(stream, "list", profileId?.toString()) -> false
                else -> true
            }
        },

        streamNameMisskey = "userList",
        streamParamMisskey = { jsonObjectOf("listId" to profileId.toString()) },
        streamPathMisskey9 = { "/user-list?listId=$profileId" },

        ),

    LIST_MEMBER(
        21,
        iconId = { R.drawable.ic_list_member },
        name1 = { it.getString(R.string.list_member) },
        name2 = {
            context.getString(
                R.string.list_member_of,
                listInfo?.title ?: profileId.toString()
            )
        },

        loading = { client ->
            column.loadListInfo(client, true)
            if (isMisskey) {
                column.pagingType = ColumnPagingType.None
                getAccountList(
                    client,
                    "/api/users/show",
                    misskeyParams = accessInfo.putMisskeyApiToken().apply {
                        val list = column.listInfo?.userIds?.map { it.toString() }?.toJsonArray()
                        if (list != null) put("userIds", list)
                    }
                )
            } else {
                getAccountList(
                    client,
                    String.format(Locale.JAPAN, ApiPath.PATH_LIST_MEMBER, column.profileId)
                )
            }
        },

        refresh = { client ->
            column.loadListInfo(client, false)
            getAccountList(
                client,
                String.format(Locale.JAPAN, ApiPath.PATH_LIST_MEMBER, column.profileId)
            )
        },

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    DIRECT_MESSAGES(
        23,
        iconId = { R.drawable.ic_mail },
        name1 = { it.getString(R.string.direct_messages) },

        loading = { client ->
            column.useConversationSummaries = false
            if (column.useOldApi) {
                getStatusList(client, ApiPath.PATH_DIRECT_MESSAGES)
            } else {
                // try 2.6.0 new API https://github.com/tootsuite/mastodon/pull/8832
                val result = getConversationSummary(client, ApiPath.PATH_DIRECT_MESSAGES2)
                when {
                    // cancelled
                    result == null -> null

                    //  not error
                    result.error.isNullOrBlank() -> {
                        column.useConversationSummaries = true
                        result
                    }

                    // fallback to old api
                    else -> getStatusList(client, ApiPath.PATH_DIRECT_MESSAGES)
                }
            }
        },

        refresh = { client ->
            if (column.useConversationSummaries) {
                // try 2.6.0 new API https://github.com/tootsuite/mastodon/pull/8832
                getConversationSummaryList(client, ApiPath.PATH_DIRECT_MESSAGES2)
            } else {
                // fallback to old api
                getStatusList(client, ApiPath.PATH_DIRECT_MESSAGES)
            }
        },

        gap = { client ->
            if (column.useConversationSummaries) {
                // try 2.6.0 new API https://github.com/tootsuite/mastodon/pull/8832
                getConversationSummaryList(
                    client,
                    ApiPath.PATH_DIRECT_MESSAGES2,
                    mastodonFilterByIdRange = false
                )
            } else {
                // fallback to old api
                getStatusList(client, ApiPath.PATH_DIRECT_MESSAGES, mastodonFilterByIdRange = false)
            }
        },
        gapDirection = gapDirectionMastodonWorkaround,

        bAllowPseudo = false,
        bAllowMisskey = false,

        canAutoRefresh = true,

        canStreamingMastodon = streamingTypeYes,
        canStreamingMisskey = streamingTypeYes,

        streamKeyMastodon = {
            jsonObjectOf(StreamSpec.STREAM to "direct")
        },

        streamFilterMastodon = { stream, _ ->
            when {
                unmatchMastodonStream(stream, "direct") -> false
                else -> true
            }
        }
    ),

    TREND_TAG(
        24,
        iconId = { R.drawable.ic_trend },
        name1 = { it.getString(R.string.trend_tag) },
        bAllowPseudo = true,
        bAllowMastodon = true,
        bAllowMisskey = false,

        loading = { client ->
            val result = client.request("/api/v1/trends")
            val src = parser.tagList(result?.jsonArray)

            this.listTmp = addAll(this.listTmp, src)
            this.listTmp = addOne(
                this.listTmp, TootMessageHolder(
                    context.getString(R.string.trend_tag_desc),
                    gravity = Gravity.END
                )
            )
            result
        },

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,

        ),
    TREND_LINK(
        44,
        iconId = { R.drawable.ic_trend },
        name1 = { it.getString(R.string.trend_link) },
        bAllowPseudo = false,
        bAllowMastodon = true,
        bAllowMisskey = false,

        loading = { client ->
            val result = client.request("/api/v1/trends/links")
            val src = parser.tagList(result?.jsonArray)

            this.listTmp = addAll(this.listTmp, src)
            this.listTmp = addOne(
                this.listTmp, TootMessageHolder(
                    context.getString(R.string.trend_tag_desc),
                    gravity = Gravity.END
                )
            )
            result
        },

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,

        ),
    TREND_POST(
        45,
        iconId = { R.drawable.ic_trend },
        name1 = { it.getString(R.string.trend_post) },
        bAllowPseudo = false,
        bAllowMastodon = true,
        bAllowMisskey = false,

        loading = { client ->
            val result = client.request("/api/v1/trends/statuses")
            val src = parser.statusList(result?.jsonArray)

            this.listTmp = addAll(this.listTmp, src)
//            this.listTmp = addOne(
//                this.listTmp, TootMessageHolder(
//                    context.getString(R.string.trend_tag_desc),
//                    gravity = Gravity.END
//                )
//            )
            result
        },

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,

        ),

    FOLLOW_SUGGESTION(
        25,
        iconId = { R.drawable.ic_person_add },
        name1 = { it.getString(R.string.follow_suggestion) },
        bAllowPseudo = false,

        loading = { client ->
            if (isMisskey) {
                column.pagingType = ColumnPagingType.Offset
                getAccountList(
                    client,
                    ApiPath.PATH_MISSKEY_FOLLOW_SUGGESTION,
                    misskeyParams = accessInfo.putMisskeyApiToken()
                )
            } else {
                val (ti, ri) = TootInstance.get(client)
                when {
                    ti == null -> ri
                    ti.versionGE(TootInstance.VERSION_3_4_0_rc1) ->
                        getAccountList(
                            client,
                            ApiPath.PATH_FOLLOW_SUGGESTION2,
                            listParser = mastodonFollowSuggestion2ListParser,
                        )

                    else ->
                        getAccountList(client, ApiPath.PATH_FOLLOW_SUGGESTION)
                }
            }
        },

        refresh = { client ->
            if (isMisskey) {
                getAccountList(
                    client,
                    ApiPath.PATH_MISSKEY_FOLLOW_SUGGESTION,
                    misskeyParams = accessInfo.putMisskeyApiToken()
                )
            } else {
                val (ti, ri) = TootInstance.get(client)
                when {
                    ti == null -> ri
                    ti.versionGE(TootInstance.VERSION_3_4_0_rc1) ->
                        getAccountList(
                            client,
                            ApiPath.PATH_FOLLOW_SUGGESTION2,
                            listParser = mastodonFollowSuggestion2ListParser,
                        )

                    else ->
                        getAccountList(client, ApiPath.PATH_FOLLOW_SUGGESTION)
                }
            }
        },

        gap = { client ->
            if (isMisskey) {
                getAccountList(
                    client,
                    ApiPath.PATH_MISSKEY_FOLLOW_SUGGESTION,
                    mastodonFilterByIdRange = false,
                    misskeyParams = accessInfo.putMisskeyApiToken()
                )
            } else {
                val (ti, ri) = TootInstance.get(client)
                when {
                    ti == null -> ri
                    ti.versionGE(TootInstance.VERSION_3_4_0_rc1) ->
                        getAccountList(
                            client,
                            ApiPath.PATH_FOLLOW_SUGGESTION2,
                            listParser = mastodonFollowSuggestion2ListParser,
                            mastodonFilterByIdRange = false
                        )

                    else ->
                        getAccountList(
                            client,
                            ApiPath.PATH_FOLLOW_SUGGESTION,
                            mastodonFilterByIdRange = false
                        )
                }
            }
        },
        gapDirection = gapDirectionMastodonWorkaround,

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    ENDORSEMENT(
        28,
        iconId = { R.drawable.ic_person_add },
        name1 = { it.getString(R.string.endorse_set) },
        bAllowPseudo = false,
        bAllowMisskey = false,

        loading = { client -> getAccountList(client, ApiPath.PATH_ENDORSEMENT) },
        refresh = { client -> getAccountList(client, ApiPath.PATH_ENDORSEMENT) },
        gap = { client ->
            getAccountList(
                client,
                ApiPath.PATH_ENDORSEMENT,
                mastodonFilterByIdRange = false
            )
        },
        gapDirection = gapDirectionMastodonWorkaround,

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,

        ),

    PROFILE_DIRECTORY(
        36,
        iconId = { R.drawable.ic_person_add },
        name1 = { it.getString(R.string.profile_directory) },
        name2 = { context.getString(R.string.profile_directory_of, instanceUri) },
        bAllowPseudo = true,
        headerType = HeaderType.ProfileDirectory,
        loading = { client ->
            column.pagingType = ColumnPagingType.Offset
            getAccountList(client, profileDirectoryPath)
        },
        refresh = { client ->
            getAccountList(client, profileDirectoryPath)
        },

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,

        ),

    ACCOUNT_AROUND(
        31,
        iconId = { R.drawable.ic_account_box },
        name1 = { it.getString(R.string.account_tl_around) },
        name2 = {
            val id = statusId?.toString() ?: "null"
            context.getString(R.string.account_tl_around_of, id)
        },

        loading = { client -> getAccountTlAroundTime(client) },

        refresh = { client ->
            getStatusList(client, column.makeProfileStatusesUrl(column.profileId), useMinId = true)
        },

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,

        ),

    @Suppress("unused")
    REPORTS(
        6,
        iconId = { R.drawable.ic_info_outline },
        name1 = { it.getString(R.string.reports) },

        loading = { client -> getReportList(client, ApiPath.PATH_REPORTS) },
        refresh = { client -> getReportList(client, ApiPath.PATH_REPORTS) },
        gap = { client ->
            getReportList(
                client,
                ApiPath.PATH_REPORTS,
                mastodonFilterByIdRange = false
            )
        },
        gapDirection = gapDirectionMastodonWorkaround,

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,

        ),

    KEYWORD_FILTER(
        26,
        iconId = { R.drawable.ic_volume_off },
        name1 = { it.getString(R.string.keyword_filters) },
        bAllowPseudo = false,
        bAllowMisskey = false,
        headerType = HeaderType.Filter,

        loading = { client ->
            getFilterList(client)
        },

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,

        ),

    SCHEDULED_STATUS(
        33,
        iconId = { R.drawable.ic_timer },
        name1 = { it.getString(R.string.scheduled_status) },
        bAllowPseudo = false,
        bAllowMisskey = false,

        loading = { client ->
            val result = client.request("/api/v1/accounts/verify_credentials")
            if (result == null || result.error != null) {
                result
            } else {
                val a = parser.account(result.jsonObject) ?: accessInfo.loginAccount
                if (a == null) {
                    TootApiResult("can't parse account information")
                } else {
                    column.whoAccount = tootAccountRef(parser, a)
                    getScheduledStatuses(client)
                }
            }
        },

        refresh = { client -> getScheduledStatuses(client) },

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,

        ),

    MISSKEY_ANTENNA_LIST(
        39,
        iconId = { R.drawable.ic_satellite },
        name1 = { it.getString(R.string.antenna_list) },
        bAllowPseudo = false,
        bAllowMastodon = false,

        loading = { client ->
            if (isMisskey) {
                getAntennaList(
                    client,
                    "/api/antennas/list",
                    misskeyParams = column.makeMisskeyBaseParameter(parser)
                )
            } else {
                TootApiResult("antenna is not supported on Mastodon")
            }
        },

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,

        ),

    MISSKEY_ANTENNA_TL(
        40,
        iconId = { R.drawable.ic_satellite },
        name1 = { it.getString(R.string.antenna_timeline) },
        name2 = {
            context.getString(
                R.string.antenna_timeline_of,
                antennaInfo?.name ?: profileId.toString()
            )
        },

        loading = { client ->
            column.loadAntennaInfo(client, true)

            if (isMisskey) {
                getStatusList(
                    client,
                    column.makeAntennaTlUrl(),
                    misskeyParams = column.makeMisskeyTimelineParameter(parser).apply {
                        put("antennaId", column.profileId)
                    }
                )
            } else {
                getStatusList(client, column.makeAntennaTlUrl())
            }
        },

        refresh = { client ->
            column.loadAntennaInfo(client, false)
            if (isMisskey) {
                getStatusList(
                    client,
                    column.makeAntennaTlUrl(),
                    misskeyParams = column.makeMisskeyTimelineParameter(parser).apply {
                        put("antennaId", column.profileId)
                    }
                )
            } else {
                getStatusList(client, column.makeAntennaTlUrl())
            }
        },

        gap = { client ->
            if (isMisskey) {
                getStatusList(
                    client,
                    column.makeAntennaTlUrl(),
                    mastodonFilterByIdRange = true,
                    misskeyParams = column.makeMisskeyTimelineParameter(parser).apply {
                        put("antennaId", column.profileId)
                    }
                )
            } else {
                getStatusList(client, column.makeAntennaTlUrl(), mastodonFilterByIdRange = true)
            }
        },
        gapDirection = gapDirectionBoth,

        canAutoRefresh = true,

        canStreamingMastodon = streamingTypeYes,
        canStreamingMisskey = streamingTypeYes,

        streamNameMisskey = "antenna",
        streamParamMisskey = { jsonObjectOf("antennaId" to profileId.toString()) },
        // Misskey10 にアンテナはない
    ),

    STATUS_HISTORY(
        43,
        iconId = { R.drawable.ic_history },
        name1 = { it.getString(R.string.edit_history) },
        bAllowPseudo = true,
        bAllowMisskey = false,

        loading = { client ->
            getEditHistory(client)
        },
        refresh = { client ->
            getEditHistory(client)
        },

        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    FOLLOWED_HASHTAGS(
        46,
        iconId = { R.drawable.ic_hashtag },
        name1 = { it.getString(R.string.followed_tags) },
        bAllowPseudo = false,
        bAllowMisskey = false,

        loading = { client ->
            getFollowedHashtags(client)
        },

        refresh = { client ->
            getFollowedHashtags(client)
        },
        canAutoRefresh = false,
        canStreamingMastodon = streamingTypeNo,
        canStreamingMisskey = streamingTypeNo,
    ),

    ;

    init {
        val old = Column.typeMap[id]
        if (id > 0 && old != null) error("ColumnType: duplicate id $id. name=$name, ${old.name}")
        Column.typeMap.put(id, this)
    }

    companion object {

        val log = LogCategory("ColumnType")

        fun dump() {
            var min = Int.MAX_VALUE
            var max = Int.MIN_VALUE
            for (it in entries) {
                val id = it.id
                min = min(min, id)
                max = max(max, id)
            }
            log.i("dump: ColumnType range=$min..$max")
        }

        fun parse(id: Int) = Column.typeMap[id] ?: HOME
    }
}

// public:local, public:local:media の2種類
fun Column.streamKeyLtl() =
    "public:local"
        .appendIf(":media", withAttachment)

// public, public:remote, public:remote:media, public:media の4種類
fun Column.streamKeyFtl() =
    "public"
        .appendIf(":remote", remoteOnly)
        .appendIf(":media", withAttachment)

// public:domain, public:domain:media の2種類
fun Column.streamKeyDomainTl() =
    "public:domain"
        .appendIf(":media", withAttachment)

// hashtag, hashtag:local
// fedibirdだとhashtag:localは無効でイベントが発生しないが、
// REST APIはフラグを無視するのでユーザからはストリーミングが動作していないように見える
fun Column.streamKeyHashtagTl() =
    "hashtag"
        .appendIf(":local", instanceLocal)

private fun unmatchMastodonStream(
    stream: JsonArray,
    name: String,
    expectArg: String? = null,
): Boolean {

    val key = stream.string(0)

//	when( key?.elementAtOrNull(0)){
//		'h' -> ColumnType.log.v("unmatchMastodonStream key=$key expect=$name")
//	}

    return when {
        // ストリーム名が合わない
        key != name -> true
        // 引数が合わない
        else -> unmatchMastodonStreamArg(stream.elementAtOrNull(1), expectArg)
    }
}

private fun unmatchMastodonStreamArg(actual: Any?, expect: String?) = when {
    expect == null -> actual != null
    actual == null -> true // unmatch
    else -> !expect.equals(actual.toString(), ignoreCase = true)
}
