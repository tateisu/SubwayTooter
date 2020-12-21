package jp.juggler.subwaytooter

import android.content.Context
import android.view.Gravity
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.search.MspHelper.loadingMSP
import jp.juggler.subwaytooter.search.MspHelper.refreshMSP
import jp.juggler.subwaytooter.search.NotestockHelper.loadingNotestock
import jp.juggler.subwaytooter.search.NotestockHelper.refreshNotestock
import jp.juggler.subwaytooter.search.TootsearchHelper.loadingTootsearch
import jp.juggler.subwaytooter.search.TootsearchHelper.refreshTootsearch
import jp.juggler.util.*
import java.util.*
import kotlin.math.max
import kotlin.math.min
import jp.juggler.subwaytooter.streaming.*

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
	val streamKeyMastodon: Column.() -> JsonObject? = { null },
	val streamFilterMastodon: Column.(String?, TimelineItem) -> Boolean = { _, _ -> true },
	val streamNameMisskey: String? = null,
	val streamParamMisskey: Column.() -> JsonObject? = { null },
	val streamPathMisskey10: Column.() -> String? = { null },
) {

    ProfileStatusMastodon(
		loading = { client ->
			val (instance, instanceResult) = TootInstance.get(client)
			if (instance == null) {
				instanceResult
			} else {
				val path = column.makeProfileStatusesUrl(column.profile_id)

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
				column.makeProfileStatusesUrl(column.profile_id)
			)
		},
		gap = { client ->
			getStatusList(
				client,
				column.makeProfileStatusesUrl(column.profile_id),
				mastodonFilterByIdRange = true,
			)
		},
		gapDirection = gapDirectionBoth,
	),

    ProfileStatusMisskey(

		loading = { client ->
			// 固定トゥートの取得
			val pinnedNotes = column.who_account?.get()?.pinnedNotes
			if (pinnedNotes != null) {
				this.list_pinned = addWithFilterStatus(null, pinnedNotes)
			}

			// 通常トゥートの取得
			getStatusList(
				client,
				Column.PATH_MISSKEY_PROFILE_STATUSES,
				misskeyParams = column.makeMisskeyParamsProfileStatuses(parser)
			)
		},
		refresh = { client ->
			getStatusList(
				client,
				Column.PATH_MISSKEY_PROFILE_STATUSES,
				misskeyParams = column.makeMisskeyParamsProfileStatuses(parser)
			)
		},
		gap = { client ->
			getStatusList(
				client,
				Column.PATH_MISSKEY_PROFILE_STATUSES,
				mastodonFilterByIdRange = true,
				misskeyParams = column.makeMisskeyParamsProfileStatuses(parser)
			)
		},
		gapDirection = gapDirectionMastodonWorkaround,
	),

    FollowingMastodon(

		loading = { client ->
			getAccountList(
				client,
				String.format(Locale.JAPAN, Column.PATH_ACCOUNT_FOLLOWING, column.profile_id),
				emptyMessage = context.getString(R.string.none_or_hidden_following)
			)
		},
		refresh = { client ->
			getAccountList(
				client,
				String.format(Locale.JAPAN, Column.PATH_ACCOUNT_FOLLOWING, column.profile_id)
			)
		},
		gap = { client ->
			getAccountList(
				client,
				String.format(Locale.JAPAN, Column.PATH_ACCOUNT_FOLLOWING, column.profile_id),
				mastodonFilterByIdRange = false,
			)
		},
		gapDirection = gapDirectionMastodonWorkaround,
	),

    FollowingMastodonPseudo(

		loading = {
			column.idRecent = null
			column.idOld = null
			list_tmp = addOne(
				list_tmp,
				TootMessageHolder(context.getString(R.string.pseudo_account_cant_get_follow_list))
			)
			TootApiResult()
		}
	),

    FollowingMisskey10(

		loading = { client ->
			column.pagingType = ColumnPagingType.Cursor
			getAccountList(
				client,
				Column.PATH_MISSKEY_PROFILE_FOLLOWING,
				emptyMessage = context.getString(R.string.none_or_hidden_following),
				misskeyParams = column.makeMisskeyParamsUserId(parser),
				arrayFinder = misskeyArrayFinderUsers
			)
		},
		refresh = { client ->
			getAccountList(
				client,
				Column.PATH_MISSKEY_PROFILE_FOLLOWING,
				misskeyParams = column.makeMisskeyParamsUserId(parser),
				arrayFinder = misskeyArrayFinderUsers
			)
		},
		gap = { client ->
			getAccountList(
				client,
				Column.PATH_MISSKEY_PROFILE_FOLLOWING,
				mastodonFilterByIdRange = false,
				misskeyParams = column.makeMisskeyParamsUserId(parser),
				arrayFinder = misskeyArrayFinderUsers
			)
		},
		gapDirection = gapDirectionMastodonWorkaround,
	),

    FollowingMisskey11(

		loading = { client ->
			column.pagingType = ColumnPagingType.Default
			getAccountList(
				client,
				Column.PATH_MISSKEY_PROFILE_FOLLOWING,
				emptyMessage = context.getString(R.string.none_or_hidden_following),
				misskeyParams = column.makeMisskeyParamsUserId(parser),
				listParser = misskey11FollowingParser
			)
		},
		refresh = { client ->
			getAccountList(
				client,
				Column.PATH_MISSKEY_PROFILE_FOLLOWING,
				misskeyParams = column.makeMisskeyParamsUserId(parser),
				listParser = misskey11FollowingParser
			)
		},
		gap = { client ->
			getAccountList(
				client,
				Column.PATH_MISSKEY_PROFILE_FOLLOWING,
				mastodonFilterByIdRange = false,
				misskeyParams = column.makeMisskeyParamsUserId(parser),
				listParser = misskey11FollowingParser
			)
		},
		gapDirection = gapDirectionMastodonWorkaround,
	),

    FollowersMisskey11(

		loading = { client ->
			column.pagingType = ColumnPagingType.Default
			getAccountList(
				client,
				Column.PATH_MISSKEY_PROFILE_FOLLOWERS,
				emptyMessage = context.getString(R.string.none_or_hidden_followers),
				misskeyParams = column.makeMisskeyParamsUserId(parser),
				listParser = misskey11FollowersParser
			)
		},

		refresh = { client ->
			getAccountList(
				client,
				Column.PATH_MISSKEY_PROFILE_FOLLOWERS,
				misskeyParams = column.makeMisskeyParamsUserId(parser),
				listParser = misskey11FollowersParser
			)
		},
		gap = { client ->
			getAccountList(
				client,
				Column.PATH_MISSKEY_PROFILE_FOLLOWING,
				mastodonFilterByIdRange = false,
				misskeyParams = column.makeMisskeyParamsUserId(parser),
				listParser = misskey11FollowersParser
			)
		},
		gapDirection = gapDirectionMastodonWorkaround,
	),

    FollowersMisskey10(

		loading = { client ->
			column.pagingType = ColumnPagingType.Cursor
			getAccountList(
				client,
				Column.PATH_MISSKEY_PROFILE_FOLLOWERS,
				emptyMessage = context.getString(R.string.none_or_hidden_followers),
				misskeyParams = column.makeMisskeyParamsUserId(parser),
				arrayFinder = misskeyArrayFinderUsers
			)
		},

		refresh = { client ->
			getAccountList(
				client,
				Column.PATH_MISSKEY_PROFILE_FOLLOWERS,
				misskeyParams = column.makeMisskeyParamsUserId(parser),
				arrayFinder = misskeyArrayFinderUsers
			)
		},
		gap = { client ->
			getAccountList(
				client,
				Column.PATH_MISSKEY_PROFILE_FOLLOWERS,
				mastodonFilterByIdRange = false,
				misskeyParams = column.makeMisskeyParamsUserId(parser)
			)
		},
		gapDirection = gapDirectionMastodonWorkaround,
	),

    FollowersMastodonPseudo(

		loading = {
			column.idRecent = null
			column.idOld = null
			list_tmp = addOne(
				list_tmp,
				TootMessageHolder(context.getString(R.string.pseudo_account_cant_get_follow_list))
			)
			TootApiResult()
		}
	),

    FollowersMastodon(

		loading = { client ->
			getAccountList(
				client,
				String.format(Locale.JAPAN, Column.PATH_ACCOUNT_FOLLOWERS, column.profile_id),
				emptyMessage = context.getString(R.string.none_or_hidden_followers)
			)
		},

		refresh = { client ->
			getAccountList(
				client,
				String.format(Locale.JAPAN, Column.PATH_ACCOUNT_FOLLOWERS, column.profile_id)
			)
		},
		gap = { client ->
			getAccountList(
				client,
				String.format(Locale.JAPAN, Column.PATH_ACCOUNT_FOLLOWERS, column.profile_id),
				mastodonFilterByIdRange = false
			)
		},
		gapDirection = gapDirectionMastodonWorkaround,
	),

    TabStatus(
		loading = { dispatchProfileTabStatus().loading(this, it) },
		refresh = { dispatchProfileTabStatus().refresh(this, it) },
		gap = { dispatchProfileTabStatus().gap(this, it) },
		gapDirection = { dispatchProfileTabStatus().gapDirection(this, it) },
	),

    TabFollowing(
		loading = { dispatchProfileTabFollowing().loading(this, it) },
		refresh = { dispatchProfileTabFollowing().refresh(this, it) },
		gap = { dispatchProfileTabFollowing().gap(this, it) },
		gapDirection = { dispatchProfileTabFollowing().gapDirection(this, it) },
	),

    TabFollowers(
		loading = { dispatchProfileTabFollowers().loading(this, it) },
		refresh = { dispatchProfileTabFollowers().refresh(this, it) },
		gap = { dispatchProfileTabFollowers().gap(this, it) },
		gapDirection = { dispatchProfileTabFollowers().gapDirection(this, it) },
	),

    HOME(
		id = 1,
		iconId = { R.drawable.ic_home },
		name1 = { it.getString(R.string.home) },

		loading = { client ->
			val ra = getAnnouncements(client, force = true)
			if (ra == null || ra.error != null)
				ra
			else
				getStatusList(client, column.makeHomeTlUrl())
		},
		refresh = { client ->
			val ra = getAnnouncements(client)
			if (ra == null || ra.error != null)
				ra
			else
				getStatusList(client, column.makeHomeTlUrl())
		},
		gap = { client ->
			val ra = getAnnouncements(client)
			if (ra == null || ra.error != null)
				ra
			else
				getStatusList(
					client,
					column.makeHomeTlUrl(),
					mastodonFilterByIdRange = true
				)
		},
		gapDirection = gapDirectionBoth,
		bAllowPseudo = false,

		canAutoRefresh = true,

		streamKeyMastodon = {
			jsonObject(StreamSpec.STREAM to "user")
		},
		streamFilterMastodon = { stream, item ->
			item is TootStatus && (stream == null || stream == "user")
		},

		streamNameMisskey = "homeTimeline",
		streamParamMisskey = { null },
		streamPathMisskey10 = { "/" },
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

		streamKeyMastodon = {
			jsonObject(StreamSpec.STREAM to
				"public:local"
					.appendIf(":media", with_attachment)
			)
		},
		streamFilterMastodon = { stream, item ->
			when {
				item !is TootStatus -> false
				(stream != null && !stream.startsWith("public:local")) -> false
				with_attachment && item.media_attachments.isNullOrEmpty() -> false
				else -> true
			}
		},
		streamNameMisskey = "localTimeline",
		streamParamMisskey = { null },
		streamPathMisskey10 = { "/local-timeline" },
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

		streamKeyMastodon = {
			jsonObject(StreamSpec.STREAM to
				"public"
					.appendIf(":remote", remote_only)
					.appendIf(":media", with_attachment)
			)
		},

		streamFilterMastodon = { stream, item ->
			when {
				item !is TootStatus -> false
				(stream != null && !stream.startsWith("public")) -> false
				(stream != null && stream.contains(":local")) -> false
				remote_only && item.account.acct == access_info.acct -> false
				with_attachment && item.media_attachments.isNullOrEmpty() -> false
				else -> true
			}
		},

		streamNameMisskey = "globalTimeline",
		streamParamMisskey = { null },
		streamPathMisskey10 = { "/global-timeline" },
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

		streamNameMisskey = "hybridTimeline",
		streamParamMisskey = { null },
		streamPathMisskey10 = { "/hybrid-timeline" },
	),

    DOMAIN_TIMELINE(
		id = 38,
		iconId = { R.drawable.ic_domain },
		name1 = { it.getString(R.string.domain_timeline) },
		name2 = {
			context.getString(
				R.string.domain_timeline_of,
				instance_uri.notEmpty() ?: "?"
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

		streamKeyMastodon = {
			jsonObject(StreamSpec.STREAM to
				"public:domain"
					.appendIf(":media", with_attachment),
				"domain" to instance_uri
			)
		},

		streamFilterMastodon = { stream, item ->
			when {
				item !is TootStatus -> false
				(stream != null && !stream.startsWith("public:domain")) -> false
				(stream != null && !stream.endsWith(instance_uri)) -> false
				with_attachment && item.media_attachments.isNullOrEmpty() -> false
				else -> true
			}
		}
	),

    LOCAL_AROUND(29,
		iconId = { R.drawable.ic_run },
		name1 = { it.getString(R.string.ltl_around) },
		name2 = {
			context.getString(
				R.string.ltl_around_of,
				status_id?.toString() ?: "null"
			)
		},

		loading = { client -> getPublicTlAroundTime(client, column.makePublicLocalUrl()) },
		refresh = { client -> getStatusList(client, column.makePublicLocalUrl(), useMinId = true) }
	),

    FEDERATED_AROUND(30,
		iconId = { R.drawable.ic_bike },
		name1 = { it.getString(R.string.ftl_around) },
		name2 = {
			context.getString(
				R.string.ftl_around_of,
				status_id?.toString() ?: "null"
			)
		},

		loading = { client -> getPublicTlAroundTime(client, column.makePublicFederateUrl()) },
		refresh = { client ->
			getStatusList(client, column.makePublicFederateUrl(), useMinId = true)
		}

	),

    PROFILE(
		4,
		iconId = { R.drawable.ic_account_box },
		name1 = { it.getString(R.string.profile) },
		name2 = {
			val who = who_account?.get()
			context.getString(
				R.string.profile_of,
				if (who != null)
					AcctColor.getNickname(access_info, who)
				else
					profile_id.toString()
			)
		},
		bAllowPseudo = false,
		headerType = HeaderType.Profile,

		loading = { client ->
			val who_result = column.loadProfileAccount(client, parser, true)
			if (client.isApiCancelled || column.who_account == null)
				who_result
			else
				column.profile_tab.ct.loading(this, client)

		},

		refresh = { client ->
			column.loadProfileAccount(client, parser, false)
			column.profile_tab.ct.refresh(this, client)
		},

		gap = { column.profile_tab.ct.gap(this, it) },
		gapDirection = { profile_tab.ct.gapDirection(this, it) },
	),

    FAVOURITES(
		5,
		iconId = { if (SavedAccount.isNicoru(it)) R.drawable.ic_nicoru else R.drawable.ic_star },
		name1 = { it.getString(R.string.favourites) },
		bAllowPseudo = false,

		loading = { client ->
			if (isMisskey) {
				getStatusList(
					client,
					Column.PATH_MISSKEY_FAVORITES,
					misskeyParams = column.makeMisskeyTimelineParameter(parser),
					listParser = misskeyCustomParserFavorites
				)
			} else {
				getStatusList(client, Column.PATH_FAVOURITES)
			}
		},

		refresh = { client ->
			if (isMisskey) {
				getStatusList(
					client,
					Column.PATH_MISSKEY_FAVORITES,
					misskeyParams = column.makeMisskeyTimelineParameter(parser),
					listParser = misskeyCustomParserFavorites
				)
			} else {
				getStatusList(client, Column.PATH_FAVOURITES)
			}
		},

		gap = { client ->
			if (isMisskey) {
				getStatusList(
					client,
					Column.PATH_MISSKEY_FAVORITES,
					mastodonFilterByIdRange = false,
					misskeyParams = column.makeMisskeyTimelineParameter(parser),
					listParser = misskeyCustomParserFavorites
				)
			} else {
				getStatusList(
					client,
					Column.PATH_FAVOURITES,
					mastodonFilterByIdRange = false
				)
			}
		},
		gapDirection = gapDirectionMastodonWorkaround,
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
				getStatusList(client, Column.PATH_BOOKMARKS)
			}
		},

		refresh = { client ->
			if (isMisskey) {
				TootApiResult("Misskey has no bookmarks feature.")
			} else {
				getStatusList(client, Column.PATH_BOOKMARKS)
			}
		},

		gap = { client ->
			if (isMisskey) {
				TootApiResult("Misskey has no bookmarks feature.")
			} else {
				getStatusList(client, Column.PATH_BOOKMARKS, mastodonFilterByIdRange = false)
			}
		},
		gapDirection = gapDirectionMastodonWorkaround,
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

		streamKeyMastodon = {
			jsonObject(StreamSpec.STREAM to "user")
		},
		streamFilterMastodon = { stream, item ->
			when {
				item !is TootNotification -> false
				(stream != null && stream != "user") -> false
				else -> true
			}
		},

		streamNameMisskey = "main",
		streamParamMisskey = { null },
		streamPathMisskey10 = { "/" },
	),

    NOTIFICATION_FROM_ACCT(
		35,
		iconId = { R.drawable.ic_announcement },
		name1 = { it.getString(R.string.notifications_from_acct) },
		name2 = {
			context.getString(
				R.string.notifications_from,
				hashtag_acct
			) + getNotificationTypeString()
		},

		loading = { client -> getNotificationList(client, column.hashtag_acct) },
		refresh = { client -> getNotificationList(client, column.hashtag_acct) },
		gap = { client ->
			getNotificationList(client, column.hashtag_acct, mastodonFilterByIdRange = true)
		},
		gapDirection = gapDirectionBoth,
	),

    CONVERSATION(8,
		iconId = { R.drawable.ic_forum },
		name1 = { it.getString(R.string.conversation) },
		name2 = {
			context.getString(
				R.string.conversation_around,
				status_id?.toString() ?: "null"
			)
		},
		loading = { client -> getConversation(client) }
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
				.appendHashtagExtra()
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

		streamKeyMastodon = {
			jsonObject(
				StreamSpec.STREAM to "hashtag".appendIf(":local", instance_local),
				"tag" to hashtag
			)
		},

		streamFilterMastodon = { stream, item ->
			when {
				item !is TootStatus -> false
				(stream != null && !stream.startsWith("hashtag")) -> false
				instance_local && (stream != null && !stream.contains(":local")) -> false

				else -> this.checkHashtagExtra(item)
			}
		},
		streamNameMisskey = "hashtag",
		streamParamMisskey = {  jsonObject ("q" to hashtag)  },
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
					hashtag_acct
				)
			)
				.appendHashtagExtra()
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
	),

    SEARCH(
		10,
		iconId = { R.drawable.ic_search },
		name1 = { it.getString(R.string.search) },
		name2 = { long ->
			when {
				long -> context.getString(R.string.search_of, search_query)
				else -> context.getString(R.string.search)
			}
		},
		bAllowPseudo = false,
		headerType = HeaderType.Search,

		loading = { client -> getSearch(client) },
		gap = { client -> getSearchGap(client) },
		gapDirection = gapDirectionHead,
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
						Column.PATH_MISSKEY_MUTES,
						misskeyParams = access_info.putMisskeyApiToken(),
						listParser = misskeyCustomParserMutes
					)

				}

				else -> getAccountList(client, Column.PATH_MUTES)
			}
		},

		refresh = { client ->
			when {
				isMisskey -> getAccountList(
					client,
					Column.PATH_MISSKEY_MUTES,
					misskeyParams = access_info.putMisskeyApiToken(),
					arrayFinder = misskeyArrayFinderUsers,
					listParser = misskeyCustomParserMutes
				)
				else -> getAccountList(client, Column.PATH_MUTES)
			}
		},

		gap = { client ->
			when {
				isMisskey -> getAccountList(
					client,
					Column.PATH_MISSKEY_MUTES,
					mastodonFilterByIdRange = false,
					misskeyParams = access_info.putMisskeyApiToken(),
					arrayFinder = misskeyArrayFinderUsers,
					listParser = misskeyCustomParserMutes
				)
				else -> getAccountList(
					client,
					Column.PATH_MUTES,
					mastodonFilterByIdRange = false
				)
			}
		},
		gapDirection = gapDirectionMastodonWorkaround,
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
						Column.PATH_MISSKEY_BLOCKS,
						misskeyParams = access_info.putMisskeyApiToken(),
						listParser = misskeyCustomParserBlocks
					)
				}

				else -> getAccountList(client, Column.PATH_BLOCKS)
			}
		},

		refresh = { client ->
			when {
				isMisskey -> {
					getAccountList(
						client,
						Column.PATH_MISSKEY_BLOCKS,
						misskeyParams = access_info.putMisskeyApiToken(),
						listParser = misskeyCustomParserBlocks
					)
				}

				else -> getAccountList(client, Column.PATH_BLOCKS)
			}
		},

		gap = { client ->
			when {
				isMisskey -> {
					getAccountList(
						client,
						Column.PATH_MISSKEY_BLOCKS,
						mastodonFilterByIdRange = false,
						misskeyParams = access_info.putMisskeyApiToken(),
						listParser = misskeyCustomParserBlocks
					)
				}

				else -> getAccountList(client, Column.PATH_BLOCKS, mastodonFilterByIdRange = false)
			}
		},
		gapDirection = gapDirectionMastodonWorkaround,
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
					Column.PATH_MISSKEY_FOLLOW_REQUESTS,
					misskeyParams = access_info.putMisskeyApiToken(),
					listParser = misskeyCustomParserFollowRequest
				)
			} else {
				getAccountList(client, Column.PATH_FOLLOW_REQUESTS)
			}
		},
		refresh = { client ->
			if (isMisskey) {
				getAccountList(
					client,
					Column.PATH_MISSKEY_FOLLOW_REQUESTS,
					misskeyParams = access_info.putMisskeyApiToken(),
					listParser = misskeyCustomParserFollowRequest
				)
			} else {
				getAccountList(client, Column.PATH_FOLLOW_REQUESTS)
			}
		},
		gap = { client ->
			if (isMisskey) {
				getAccountList(
					client,
					Column.PATH_MISSKEY_FOLLOW_REQUESTS,
					mastodonFilterByIdRange = false,
					misskeyParams = access_info.putMisskeyApiToken(),
					listParser = misskeyCustomParserFollowRequest
				)
			} else {
				getAccountList(
					client,
					Column.PATH_FOLLOW_REQUESTS,
					mastodonFilterByIdRange = false
				)
			}
		},
		gapDirection = gapDirectionMastodonWorkaround,
	),

    BOOSTED_BY(
		14,
		iconId = { R.drawable.ic_repeat },
		name1 = { it.getString(R.string.boosted_by) },

		loading = { client ->
			getAccountList(
				client,
				String.format(Locale.JAPAN, Column.PATH_BOOSTED_BY, column.status_id)
			)
		},
		refresh = { client ->
			getAccountList(
				client,
				String.format(Locale.JAPAN, Column.PATH_BOOSTED_BY, posted_status_id)
			)
		},
		gap = { client ->
			getAccountList(
				client,
				String.format(Locale.JAPAN, Column.PATH_BOOSTED_BY, column.status_id),
				mastodonFilterByIdRange = false,
			)
		},
		gapDirection = gapDirectionMastodonWorkaround,
	),

    FAVOURITED_BY(
		15,
		iconId = { if (SavedAccount.isNicoru(it)) R.drawable.ic_nicoru else R.drawable.ic_star },
		name1 = { it.getString(R.string.favourited_by) },

		loading = { client ->
			getAccountList(
				client,
				String.format(Locale.JAPAN, Column.PATH_FAVOURITED_BY, column.status_id)
			)
		},
		refresh = { client ->
			getAccountList(
				client,
				String.format(Locale.JAPAN, Column.PATH_FAVOURITED_BY, posted_status_id)
			)
		},
		gap = { client ->
			getAccountList(
				client,
				String.format(Locale.JAPAN, Column.PATH_FAVOURITED_BY, column.status_id),
				mastodonFilterByIdRange = false,
			)
		},
		gapDirection = gapDirectionMastodonWorkaround,
	),

    DOMAIN_BLOCKS(16,
		iconId = { R.drawable.ic_cloud_off },
		name1 = { it.getString(R.string.blocked_domains) },
		bAllowPseudo = false,
		bAllowMisskey = false,

		loading = { client -> getDomainBlockList(client, Column.PATH_DOMAIN_BLOCK) },
		refresh = { client -> getDomainList(client, Column.PATH_DOMAIN_BLOCK) }
	),

    SEARCH_MSP(
		17,
		iconId = { R.drawable.ic_search },
		name1 = { it.getString(R.string.toot_search_msp) },
		name2 = { long ->
			when {
				long -> context.getString(R.string.toot_search_msp_of, search_query)
				else -> context.getString(R.string.toot_search_msp)
			}
		},
		headerType = HeaderType.Search,

		loading = { loadingMSP(it) },
		refresh = { refreshMSP(it) },
	),

    SEARCH_TS(
		22,
		iconId = { R.drawable.ic_search },
		name1 = { it.getString(R.string.toot_search_ts) },
		name2 = { long ->
			when {
				long -> context.getString(R.string.toot_search_ts_of, search_query)
				else -> context.getString(R.string.toot_search_ts)
			}
		},
		headerType = HeaderType.Search,

		loading = { loadingTootsearch(it) },
		refresh = { refreshTootsearch(it) },
	),

    SEARCH_NOTESTOCK(
		41,
		iconId = { R.drawable.ic_search },
		name1 = { it.getString(R.string.toot_search_notestock) },
		name2 = { long ->
			when {
				long -> context.getString(R.string.toot_search_notestock_of, search_query)
				else -> context.getString(R.string.toot_search_notestock)
			}
		},
		headerType = HeaderType.Search,

		loading = { loadingNotestock(it) },
		refresh = { refreshNotestock(it) },
	),

    INSTANCE_INFORMATION(18,
		iconId = { R.drawable.ic_info },
		name1 = { it.getString(R.string.instance_information) },
		name2 = { long ->
			when {
				long -> context.getString(R.string.instance_information_of, instance_uri)
				else -> context.getString(R.string.instance_information)
			}
		},
		headerType = HeaderType.Instance,

		loading = { client ->
			val (instance, instanceResult) = TootInstance.get(
				client,
				Host.parse(column.instance_uri),
				allowPixelfed = true,
				forceUpdate = true
			)
			if (instance != null) {
				column.instance_information = instance
				column.handshake = instanceResult?.response?.handshake
			}
			instanceResult
			//
			//			// 「インスタンス情報」カラムをNAアカウントで開く場合
			//			instance_name != null -> client.instance = instance_name
			//
			//	val (result, ti) = client.parseInstanceInformation(client.getInstanceInformation())
			//	instance_tmp = ti
			//				return result
			//			}
			//
			//			val result = getInstanceInformation(client, column.instance_uri)
			//			if(instance_tmp != null) {
			//
			//			}
			//			result
		}
	),

    LIST_LIST(19,
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
				getListList(client, Column.PATH_LIST_LIST)
			}
		}
	),

    LIST_TL(
		20,
		iconId = { R.drawable.ic_list_tl },
		name1 = { it.getString(R.string.list_timeline) },
		name2 = {
			context.getString(
				R.string.list_tl_of,
				list_info?.title ?: profile_id.toString()
			)
		},

		loading = { client ->
			column.loadListInfo(client, true)
			if (isMisskey) {
				getStatusList(
					client,
					column.makeListTlUrl(),
					misskeyParams = column.makeMisskeyTimelineParameter(parser).apply {
						put("listId", column.profile_id)
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
						put("listId", column.profile_id)
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
						put("listId", column.profile_id)
					}
				)
			} else {
				getStatusList(client, column.makeListTlUrl(), mastodonFilterByIdRange = true)
			}
		},
		gapDirection = gapDirectionBoth,

		canAutoRefresh = true,

		streamKeyMastodon = {
			jsonObject(StreamSpec.STREAM to "list", "list" to profile_id.toString())
		},

		streamFilterMastodon = { stream, item ->
			when {
				item !is TootStatus -> false
				(stream != null && stream != "list:${profile_id}") -> false
				else -> true
			}
		},
		streamNameMisskey = "userList",
		streamParamMisskey = { jsonObject("listId" to profile_id.toString()) },
		streamPathMisskey10 = { "/user-list?listId=${profile_id.toString()}" },

	),

    LIST_MEMBER(21,
		iconId = { R.drawable.ic_list_member },
		name1 = { it.getString(R.string.list_member) },
		name2 = {
			context.getString(
				R.string.list_member_of,
				list_info?.title ?: profile_id.toString()
			)
		},

		loading = { client ->
			column.loadListInfo(client, true)
			if (isMisskey) {
				column.pagingType = ColumnPagingType.None
				getAccountList(
					client,
					"/api/users/show",
					misskeyParams = access_info.putMisskeyApiToken().apply {
						val list = column.list_info?.userIds?.map { it.toString() }?.toJsonArray()
						if (list != null) put("userIds", list)
					}
				)

			} else {
				getAccountList(
					client,
					String.format(Locale.JAPAN, Column.PATH_LIST_MEMBER, column.profile_id)
				)
			}
		},

		refresh = { client ->
			column.loadListInfo(client, false)
			getAccountList(
				client,
				String.format(Locale.JAPAN, Column.PATH_LIST_MEMBER, column.profile_id)
			)
		}
	),

    DIRECT_MESSAGES(
		23,
		iconId = { R.drawable.ic_mail },
		name1 = { it.getString(R.string.direct_messages) },

		loading = { client ->
			column.useConversationSummarys = false
			if (column.use_old_api) {
				getStatusList(client, Column.PATH_DIRECT_MESSAGES)
			} else {
				// try 2.6.0 new API https://github.com/tootsuite/mastodon/pull/8832
				val result = getConversationSummary(client, Column.PATH_DIRECT_MESSAGES2)
				when {
					// cancelled
					result == null -> null

					//  not error
					result.error.isNullOrBlank() -> {
						column.useConversationSummarys = true
						result
					}

					// fallback to old api
					else -> getStatusList(client, Column.PATH_DIRECT_MESSAGES)
				}
			}
		},

		refresh = { client ->
			if (column.useConversationSummarys) {
				// try 2.6.0 new API https://github.com/tootsuite/mastodon/pull/8832
				getConversationSummaryList(client, Column.PATH_DIRECT_MESSAGES2)
			} else {
				// fallback to old api
				getStatusList(client, Column.PATH_DIRECT_MESSAGES)
			}
		},

		gap = { client ->
			if (column.useConversationSummarys) {
				// try 2.6.0 new API https://github.com/tootsuite/mastodon/pull/8832
				getConversationSummaryList(
					client,
					Column.PATH_DIRECT_MESSAGES2,
					mastodonFilterByIdRange = false
				)
			} else {
				// fallback to old api
				getStatusList(client, Column.PATH_DIRECT_MESSAGES, mastodonFilterByIdRange = false)
			}
		},
		gapDirection = gapDirectionMastodonWorkaround,

		bAllowPseudo = false,
		bAllowMisskey = false,

		canAutoRefresh = true,

		streamKeyMastodon = {
			jsonObject(StreamSpec.STREAM to "direct")
		},

		streamFilterMastodon = { stream, _ ->
			when {
				(stream != null && stream != "direct") -> false

				else -> true
			}
		}
	),

    TREND_TAG(24,
		iconId = { R.drawable.ic_hashtag },
		name1 = { it.getString(R.string.trend_tag) },
		bAllowPseudo = true,
		bAllowMastodon = true,
		bAllowMisskey = false,

		loading = { client ->
			val result = client.request("/api/v1/trends")
			val src = parser.tagList(result?.jsonArray)

			this.list_tmp = addAll(this.list_tmp, src)
			this.list_tmp = addOne(
				this.list_tmp, TootMessageHolder(
				context.getString(R.string.trend_tag_desc),
				gravity = Gravity.END
			)
			)
			result
		}
	),

    FOLLOW_SUGGESTION(
		25,
		iconId = { R.drawable.ic_follow_plus },
		name1 = { it.getString(R.string.follow_suggestion) },
		bAllowPseudo = false,

		loading = { client ->
			if (isMisskey) {
				column.pagingType = ColumnPagingType.Offset
				getAccountList(
					client,
					Column.PATH_MISSKEY_FOLLOW_SUGGESTION,
					misskeyParams = access_info.putMisskeyApiToken()
				)
			} else {
				getAccountList(client, Column.PATH_FOLLOW_SUGGESTION)
			}
		},

		refresh = { client ->
			if (isMisskey) {
				getAccountList(
					client,
					Column.PATH_MISSKEY_FOLLOW_SUGGESTION,
					misskeyParams = access_info.putMisskeyApiToken()
				)
			} else {
				getAccountList(client, Column.PATH_FOLLOW_SUGGESTION)
			}
		},

		gap = { client ->
			if (isMisskey) {
				getAccountList(
					client,
					Column.PATH_MISSKEY_FOLLOW_SUGGESTION,
					mastodonFilterByIdRange = false,
					misskeyParams = access_info.putMisskeyApiToken()
				)
			} else {
				getAccountList(
					client,
					Column.PATH_FOLLOW_SUGGESTION,
					mastodonFilterByIdRange = false
				)
			}
		},
		gapDirection = gapDirectionMastodonWorkaround,
	),

    ENDORSEMENT(
		28,
		iconId = { R.drawable.ic_follow_plus },
		name1 = { it.getString(R.string.endorse_set) },
		bAllowPseudo = false,
		bAllowMisskey = false,

		loading = { client -> getAccountList(client, Column.PATH_ENDORSEMENT) },
		refresh = { client -> getAccountList(client, Column.PATH_ENDORSEMENT) },
		gap = { client ->
			getAccountList(
				client,
				Column.PATH_ENDORSEMENT,
				mastodonFilterByIdRange = false
			)
		},
		gapDirection = gapDirectionMastodonWorkaround,
	),

    PROFILE_DIRECTORY(36,
		iconId = { R.drawable.ic_follow_plus },
		name1 = { it.getString(R.string.profile_directory) },
		name2 = { context.getString(R.string.profile_directory_of, instance_uri) },
		bAllowPseudo = true,
		headerType = HeaderType.ProfileDirectory,
		loading = { client ->
			column.pagingType = ColumnPagingType.Offset
			getAccountList(client, profileDirectoryPath)
		},
		refresh = { client ->
			getAccountList(client, profileDirectoryPath)
		}
	),

    ACCOUNT_AROUND(31,
		iconId = { R.drawable.ic_account_box },
		name1 = { it.getString(R.string.account_tl_around) },
		name2 = {
			val id = status_id?.toString() ?: "null"
			context.getString(R.string.account_tl_around_of, id)
		},

		loading = { client -> getAccountTlAroundTime(client) },

		refresh = { client ->
			getStatusList(client, column.makeProfileStatusesUrl(column.profile_id), useMinId = true)
		}
	),

    @Suppress("unused")
    REPORTS(
		6,
		iconId = { R.drawable.ic_info },
		name1 = { it.getString(R.string.reports) },

		loading = { client -> getReportList(client, Column.PATH_REPORTS) },
		refresh = { client -> getReportList(client, Column.PATH_REPORTS) },
		gap = { client ->
			getReportList(
				client,
				Column.PATH_REPORTS,
				mastodonFilterByIdRange = false
			)
		},
		gapDirection = gapDirectionMastodonWorkaround,
	),

    KEYWORD_FILTER(26,
		iconId = { R.drawable.ic_volume_off },
		name1 = { it.getString(R.string.keyword_filters) },
		bAllowPseudo = false,
		bAllowMisskey = false,
		headerType = HeaderType.Filter,

		loading = { client -> getFilterList(client, Column.PATH_FILTERS) }
	),

    SCHEDULED_STATUS(33,
		iconId = { R.drawable.ic_timer },
		name1 = { it.getString(R.string.scheduled_status) },
		bAllowPseudo = false,
		bAllowMisskey = false,

		loading = { client ->
			val result = client.request("/api/v1/accounts/verify_credentials")
			if (result == null || result.error != null) {
				result
			} else {
				val a = parser.account(result.jsonObject) ?: access_info.loginAccount
				if (a == null) {
					TootApiResult("can't parse account information")
				} else {
					column.who_account = TootAccountRef(parser, a)
					getScheduledStatuses(client)
				}
			}
		},

		refresh = { client -> getScheduledStatuses(client) }
	),

    MISSKEY_ANTENNA_LIST(39,
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
		}
	),

    MISSKEY_ANTENNA_TL(
		40,
		iconId = { R.drawable.ic_satellite },
		name1 = { it.getString(R.string.antenna_timeline) },
		name2 = {
			context.getString(
				R.string.antenna_timeline_of,
				antenna_info?.name ?: profile_id.toString()
			)
		},

		loading = { client ->
			column.loadAntennaInfo(client, true)

			if (isMisskey) {
				getStatusList(
					client,
					column.makeAntennaTlUrl(),
					misskeyParams = column.makeMisskeyTimelineParameter(parser).apply {
						put("antennaId", column.profile_id)
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
						put("antennaId", column.profile_id)
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
						put("antennaId", column.profile_id)
					}
				)
			} else {
				getStatusList(client, column.makeAntennaTlUrl(), mastodonFilterByIdRange = true)
			}
		},
		gapDirection = gapDirectionBoth,

		canAutoRefresh = true,
		streamNameMisskey = "antenna",
		streamParamMisskey = { jsonObject("antennaId" to profile_id.toString()) },
		// Misskey10 にアンテナはない
	),

    ;

    init {
        val old = Column.typeMap[id]
        if (id > 0 && old != null) error("ColumnType: duplicate id $id. name=$name, ${old.name}")
        Column.typeMap.put(id, this)
    }

    companion object {

        private val log = LogCategory("ColumnType")

        fun dump() {
            var min = Int.MAX_VALUE
            var max = Int.MIN_VALUE
            values().forEach {
                val id = it.id
                min = min(min, id)
                max = max(max, id)
            }
            log.d("dump: ColumnType range=$min..$max")
        }

        fun parse(id: Int) = Column.typeMap[id] ?: HOME
    }
}

