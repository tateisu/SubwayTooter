package jp.juggler.subwaytooter

import android.content.Context
import android.view.Gravity
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.LogCategory
import jp.juggler.util.ellipsizeDot3
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
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

private val unsupportedRefresh : ColumnTask_Refresh.(client : TootApiClient) -> TootApiResult? =
	{ TootApiResult("edge reading not supported.") }

private val unsupportedGap : ColumnTask_Gap.(client : TootApiClient) -> TootApiResult? =
	{ TootApiResult("gap reading not supported.") }

private val unusedIconId : (acct : String) -> Int =
	{ R.drawable.ic_question }

private val unusedName : (context : Context) -> String =
	{ "?" }

private val unusedName2 : Column.(long : Boolean) -> String? =
	{ null }

enum class ColumnType(
	val id : Int = 0,
	val iconId : (acct : String) -> Int = unusedIconId,
	val name1 : (context : Context) -> String = unusedName,
	val name2 : Column.(long : Boolean) -> String? = unusedName2,
	val loading : ColumnTask_Loading.(client : TootApiClient) -> TootApiResult?,
	val refresh : ColumnTask_Refresh.(client : TootApiClient) -> TootApiResult? = unsupportedRefresh,
	val gap : ColumnTask_Gap.(client : TootApiClient) -> TootApiResult? = unsupportedGap,
	val bAllowPseudo : Boolean = true,
	val bAllowMisskey : Boolean = true,
	val bAllowMastodon : Boolean = true,
	val headerType : HeaderType? = null
) {
	
	ProfileStatusMastodon(
		loading = { client ->
			val(instanceResult,instance) = TootInstance.get(client)
			if(instance==null){
				instanceResult
			}else {
				val path = column.makeProfileStatusesUrl(column.profile_id)
				
				if(instance.versionGE(TootInstance.VERSION_1_6)
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
		gap = { client -> getStatusList(client, column.makeProfileStatusesUrl(column.profile_id)) }
	),
	
	ProfileStatusMisskey(
		
		loading = { client ->
			// 固定トゥートの取得
			val pinnedNotes = column.who_account?.get()?.pinnedNotes
			if(pinnedNotes != null) {
				this.list_pinned = addWithFilterStatus(null, pinnedNotes)
			}
			
			// 通常トゥートの取得
			getStatusList(
				client,
				Column.PATH_MISSKEY_PROFILE_STATUSES,
				misskeyParams = column.makeMisskeyParamsProfileStatuses(parser),
				initialUntilDate = true
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
				client
				, Column.PATH_MISSKEY_PROFILE_STATUSES
				, misskeyParams = column.makeMisskeyParamsProfileStatuses(parser)
			)
		}
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
				String.format(Locale.JAPAN, Column.PATH_ACCOUNT_FOLLOWING, column.profile_id)
			)
		}
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
				misskeyArrayFinder = misskeyArrayFinderUsers
			)
		},
		refresh = { client ->
			getAccountList(
				client,
				Column.PATH_MISSKEY_PROFILE_FOLLOWING,
				misskeyParams = column.makeMisskeyParamsUserId(parser),
				misskeyArrayFinder = misskeyArrayFinderUsers
			)
		},
		gap = { client ->
			getAccountList(
				client,
				Column.PATH_MISSKEY_PROFILE_FOLLOWING,
				misskeyParams = column.makeMisskeyParamsUserId(parser),
				misskeyArrayFinder = misskeyArrayFinderUsers
			)
		}
	),
	
	FollowingMisskey11(
		
		loading = { client ->
			column.pagingType = ColumnPagingType.Default
			column.useDate = false
			getAccountList(
				client,
				Column.PATH_MISSKEY_PROFILE_FOLLOWING,
				emptyMessage = context.getString(R.string.none_or_hidden_following),
				misskeyParams = column.makeMisskeyParamsUserId(parser),
				misskeyCustomParser = misskeyFollowingParser
			)
		},
		refresh = { client ->
			column.useDate = false
			getAccountList(
				client,
				Column.PATH_MISSKEY_PROFILE_FOLLOWING,
				misskeyParams = column.makeMisskeyParamsUserId(parser),
				misskeyCustomParser = misskeyFollowingParser
			)
		},
		gap = { client ->
			getAccountList(
				client,
				Column.PATH_MISSKEY_PROFILE_FOLLOWING,
				misskeyParams = column.makeMisskeyParamsUserId(parser),
				misskeyCustomParser = misskeyFollowingParser
			)
		}
	),
	
	FollowersMisskey11(
		
		loading = { client ->
			column.pagingType = ColumnPagingType.Default
			getAccountList(
				client,
				Column.PATH_MISSKEY_PROFILE_FOLLOWERS,
				emptyMessage = context.getString(R.string.none_or_hidden_followers),
				misskeyParams = column.makeMisskeyParamsUserId(parser),
				misskeyCustomParser = misskeyFollowersParser
			)
		},
		
		refresh = { client ->
			column.useDate = false
			getAccountList(
				client,
				Column.PATH_MISSKEY_PROFILE_FOLLOWERS,
				misskeyParams = column.makeMisskeyParamsUserId(parser),
				misskeyCustomParser = misskeyFollowersParser
			)
		},
		gap = { client ->
			getAccountList(
				client,
				Column.PATH_MISSKEY_PROFILE_FOLLOWING,
				misskeyParams = column.makeMisskeyParamsUserId(parser),
				misskeyCustomParser = misskeyFollowersParser
			)
		}
	),
	
	FollowersMisskey10(
		
		loading = { client ->
			column.pagingType = ColumnPagingType.Cursor
			getAccountList(
				client,
				Column.PATH_MISSKEY_PROFILE_FOLLOWERS,
				emptyMessage = context.getString(R.string.none_or_hidden_followers),
				misskeyParams = column.makeMisskeyParamsUserId(parser),
				misskeyArrayFinder = misskeyArrayFinderUsers
			)
		},
		
		refresh = { client ->
			getAccountList(
				client,
				Column.PATH_MISSKEY_PROFILE_FOLLOWERS,
				misskeyParams = column.makeMisskeyParamsUserId(parser),
				misskeyArrayFinder = misskeyArrayFinderUsers
			)
		},
		gap = { client ->
			getAccountList(
				client
				, Column.PATH_MISSKEY_PROFILE_FOLLOWERS
				, misskeyParams = column.makeMisskeyParamsUserId(parser)
			)
		}
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
				String.format(Locale.JAPAN, Column.PATH_ACCOUNT_FOLLOWERS, column.profile_id)
			)
		}
	),
	
	TabStatus(
		loading = { client ->
			when {
				isMisskey -> ProfileStatusMisskey.loading(this, client)
				else -> ProfileStatusMastodon.loading(this, client)
			}
		},
		
		refresh = { client ->
			when {
				isMisskey -> ProfileStatusMisskey.refresh(this, client)
				else -> ProfileStatusMastodon.refresh(this, client)
			}
		},
		
		gap = { client ->
			when {
				isMisskey -> ProfileStatusMisskey.gap(this, client)
				else -> ProfileStatusMastodon.gap(this, client)
			}
		}
	),
	
	TabFollowing(
		loading = { client ->
			when {
				misskeyVersion >= 11 -> FollowingMisskey11.loading(this, client)
				isMisskey -> FollowingMisskey10.loading(this, client)
				access_info.isPseudo -> FollowingMastodonPseudo.loading(this, client)
				else -> FollowingMastodon.loading(this, client)
			}
		},
		
		refresh = { client ->
			when {
				misskeyVersion >= 11 -> FollowingMisskey11.refresh(this, client)
				isMisskey -> FollowingMisskey10.refresh(this, client)
				else -> FollowingMastodon.refresh(this, client)
			}
		},
		
		gap = { client ->
			when {
				misskeyVersion >= 11 -> FollowingMisskey11.gap(this, client)
				isMisskey -> FollowingMisskey10.gap(this, client)
				else -> FollowingMastodon.gap(this, client)
			}
		}
	),
	
	TabFollowers(
		
		loading = { client ->
			when {
				misskeyVersion >= 11 -> FollowersMisskey11.loading(this, client)
				isMisskey -> FollowersMisskey10.loading(this, client)
				access_info.isPseudo -> FollowersMastodonPseudo.loading(this, client)
				else -> FollowersMastodon.loading(this, client)
			}
		},
		
		refresh = { client ->
			when {
				misskeyVersion >= 11 -> FollowersMisskey11.refresh(this, client)
				isMisskey -> FollowersMisskey10.refresh(this, client)
				access_info.isPseudo -> FollowersMastodonPseudo.refresh(this, client)
				else -> FollowersMastodon.refresh(this, client)
			}
		},
		
		gap = { client ->
			when {
				misskeyVersion >= 11 -> FollowersMisskey11.gap(this, client)
				isMisskey -> FollowersMisskey10.gap(this, client)
				access_info.isPseudo -> FollowersMastodonPseudo.gap(this, client)
				else -> FollowersMastodon.gap(this, client)
			}
		}
	),
	
	HOME(
		id = 1,
		iconId = { R.drawable.ic_home },
		name1 = { it.getString(R.string.home) },
		
		loading = { client -> getStatusList(client, column.makeHomeTlUrl()) },
		refresh = { client -> getStatusList(client, column.makeHomeTlUrl()) },
		gap = { client -> getStatusList(client, column.makeHomeTlUrl()) },
		bAllowPseudo = false
	),
	
	LOCAL(
		id = 2,
		iconId = { R.drawable.ic_run },
		name1 = { it.getString(R.string.local_timeline) },
		bAllowPseudo = true,
		
		loading = { client -> getStatusList(client, column.makePublicLocalUrl()) },
		refresh = { client -> getStatusList(client, column.makePublicLocalUrl()) },
		gap = { client -> getStatusList(client, column.makePublicLocalUrl()) }
	),
	
	FEDERATE(3,
		iconId = { R.drawable.ic_bike },
		name1 = { it.getString(R.string.federate_timeline) },
		bAllowPseudo = true,
		
		loading = { client -> getStatusList(client, column.makePublicFederateUrl()) },
		refresh = { client -> getStatusList(client, column.makePublicFederateUrl()) },
		gap = { client -> getStatusList(client, column.makePublicFederateUrl()) }
	),
	
	MISSKEY_HYBRID(27,
		iconId = { R.drawable.ic_share },
		name1 = { it.getString(R.string.misskey_hybrid_timeline) },
		bAllowPseudo = true,
		bAllowMastodon = false,
		
		loading = { client -> getStatusList(client, column.makeMisskeyHybridTlUrl()) },
		refresh = { client -> getStatusList(client, column.makeMisskeyHybridTlUrl()) },
		gap = { client -> getStatusList(client, column.makeMisskeyHybridTlUrl()) }
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
		
		loading = { client -> getPublicAroundStatuses(client, column.makePublicLocalUrl()) },
		refresh = { client ->
			if(bBottom) {
				getStatusList(client, column.makePublicLocalUrl())
			} else {
				val rv = getStatusList(client, column.makePublicLocalUrl(), aroundMin = true)
				list_tmp?.sortBy { it.getOrderId() }
				list_tmp?.reverse()
				rv
			}
		}
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
		
		loading = { client -> getPublicAroundStatuses(client, column.makePublicFederateUrl()) },
		refresh = { client ->
			if(bBottom) {
				getStatusList(client, column.makePublicFederateUrl())
			} else {
				val rv = getStatusList(client, column.makePublicFederateUrl(), aroundMin = true)
				list_tmp?.sortBy { it.getOrderId() }
				list_tmp?.reverse()
				rv
			}
		}
	),
	
	PROFILE(4,
		iconId = { R.drawable.ic_account_box },
		name1 = { it.getString(R.string.profile) },
		name2 = {
			val who = who_account?.get()
			context.getString(
				R.string.profile_of,
				if(who != null)
					AcctColor.getNickname(access_info.getFullAcct(who))
				else
					profile_id.toString()
			)
		},
		bAllowPseudo = false,
		headerType = HeaderType.Profile,
		
		loading = { client ->
			val who_result = column.loadProfileAccount(client, parser, true)
			if(client.isApiCancelled || column.who_account == null)
				who_result
			else
				column.profile_tab.ct.loading(this, client)
			
		},
		
		refresh = { client ->
			column.loadProfileAccount(client, parser, false)
			column.profile_tab.ct.refresh(this, client)
		},
		
		gap = { client ->
			column.profile_tab.ct.gap(this, client)
		}
	),
	
	FAVOURITES(5,
		iconId = { if(SavedAccount.isNicoru(it)) R.drawable.ic_nicoru else R.drawable.ic_star },
		name1 = { it.getString(R.string.favourites) },
		bAllowPseudo = false,
		
		loading = { client ->
			if(isMisskey) {
				column.useDate = false
				getStatusList(
					client
					, Column.PATH_MISSKEY_FAVORITES
					, misskeyParams = column.makeMisskeyTimelineParameter(parser)
					, misskeyCustomParser = misskeyCustomParserFavorites
				)
			} else {
				getStatusList(client, Column.PATH_FAVOURITES)
			}
		},
		
		refresh = { client ->
			if(isMisskey) {
				column.useDate = false
				getStatusList(
					client
					, Column.PATH_MISSKEY_FAVORITES
					, misskeyParams = column.makeMisskeyTimelineParameter(parser)
					, misskeyCustomParser = misskeyCustomParserFavorites
				)
			} else {
				getStatusList(client, Column.PATH_FAVOURITES)
			}
		},
		
		gap = { client ->
			if(isMisskey) {
				column.useDate = false
				getStatusList(
					client,
					Column.PATH_MISSKEY_FAVORITES
					, misskeyParams = column.makeMisskeyTimelineParameter(parser)
					, misskeyCustomParser = misskeyCustomParserFavorites
				)
			} else {
				getStatusList(client, Column.PATH_FAVOURITES)
			}
		}
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
		gap = { client -> getNotificationList(client) },
		bAllowPseudo = false
	),
	
	NOTIFICATION_FROM_ACCT(35,
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
		gap = { client -> getNotificationList(client, column.hashtag_acct) }
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
		
		loading = { client ->
			getConversation(client)
		}
	),
	
	HASHTAG(9,
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
			if(isMisskey) {
				getStatusList(
					client
					, column.makeHashtagUrl()
					, misskeyParams = column.makeHashtagParams(parser)
				
				)
			} else {
				getStatusList(client, column.makeHashtagUrl())
			}
		},
		
		refresh = { client ->
			if(isMisskey) {
				getStatusList(
					client
					, column.makeHashtagUrl()
					, misskeyParams = column.makeHashtagParams(parser)
				)
			} else {
				getStatusList(client, column.makeHashtagUrl())
			}
		},
		
		gap = { client ->
			if(isMisskey) {
				getStatusList(
					client
					, column.makeHashtagUrl()
					, misskeyParams = column.makeHashtagParams(parser)
				)
			} else {
				getStatusList(client, column.makeHashtagUrl())
			}
		}
	),
	
	HASHTAG_FROM_ACCT(34,
		
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
			if(isMisskey) {
				// currently not supported
				getStatusList(
					client
					, column.makeHashtagAcctUrl(client)
					, misskeyParams = column.makeHashtagParams(parser)
				)
			} else {
				getStatusList(client, column.makeHashtagAcctUrl(client))
			}
		},
		
		refresh = { client ->
			if(isMisskey) {
				getStatusList(
					client
					, column.makeHashtagAcctUrl(client)
					, misskeyParams = column.makeHashtagParams(parser)
				)
			} else {
				getStatusList(client, column.makeHashtagAcctUrl(client))
			}
		},
		
		gap = { client ->
			if(isMisskey) {
				getStatusList(
					client
					, column.makeHashtagAcctUrl(client)
					, misskeyParams = column.makeHashtagParams(parser)
				)
			} else {
				getStatusList(client, column.makeHashtagAcctUrl(client))
			}
		}
	),
	
	SEARCH(10,
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
		gap = { client -> getSearchGap(client) }
	),
	
	// ミスキーのミュートとブロックののページングは misskey v10 の途中で変わった
	// https://github.com/syuilo/misskey/commit/f7069dcd18d72b52408a6bd80ad8f14492163e19
	// ST的には新しい方にだけ対応する
	
	MUTES(11,
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
						misskeyCustomParser = misskeyCustomParserMutes
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
					misskeyArrayFinder = misskeyArrayFinderUsers,
					misskeyCustomParser = misskeyCustomParserMutes
				)
				else -> getAccountList(client, Column.PATH_MUTES)
			}
		},
		
		gap = { client ->
			when {
				isMisskey -> getAccountList(
					client,
					Column.PATH_MISSKEY_MUTES,
					misskeyParams = access_info.putMisskeyApiToken(),
					misskeyArrayFinder = misskeyArrayFinderUsers,
					misskeyCustomParser = misskeyCustomParserMutes
				)
				else -> getAccountList(client, Column.PATH_MUTES)
			}
		}
	),
	
	BLOCKS(12,
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
						misskeyCustomParser = misskeyCustomParserBlocks
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
						misskeyCustomParser = misskeyCustomParserBlocks
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
						misskeyParams = access_info.putMisskeyApiToken(),
						misskeyCustomParser = misskeyCustomParserBlocks
					)
				}
				
				else -> getAccountList(client, Column.PATH_BLOCKS)
			}
		}
	),
	
	FOLLOW_REQUESTS(13,
		iconId = { R.drawable.ic_follow_wait },
		name1 = { it.getString(R.string.follow_requests) },
		bAllowPseudo = false,
		
		loading = { client ->
			if(isMisskey) {
				column.pagingType = ColumnPagingType.None
				getAccountList(
					client
					, Column.PATH_MISSKEY_FOLLOW_REQUESTS
					, misskeyParams = access_info.putMisskeyApiToken(JSONObject())
					, misskeyCustomParser = misskeyCustomParserFollowRequest
				)
			} else {
				getAccountList(client, Column.PATH_FOLLOW_REQUESTS)
			}
		},
		refresh = { client ->
			if(isMisskey) {
				getAccountList(
					client
					, Column.PATH_MISSKEY_FOLLOW_REQUESTS
					, misskeyParams = access_info.putMisskeyApiToken(JSONObject())
					, misskeyCustomParser = misskeyCustomParserFollowRequest
				)
			} else {
				getAccountList(client, Column.PATH_FOLLOW_REQUESTS)
			}
		},
		gap = { client ->
			if(isMisskey) {
				getAccountList(
					client
					, Column.PATH_MISSKEY_FOLLOW_REQUESTS
					, misskeyParams = access_info.putMisskeyApiToken(JSONObject())
					, misskeyCustomParser = misskeyCustomParserFollowRequest
				)
			} else {
				getAccountList(client, Column.PATH_FOLLOW_REQUESTS)
			}
		}
	),
	
	BOOSTED_BY(14,
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
				String.format(Locale.JAPAN, Column.PATH_BOOSTED_BY, column.status_id)
			)
		}
	),
	
	FAVOURITED_BY(15,
		iconId = { if(SavedAccount.isNicoru(it)) R.drawable.ic_nicoru else R.drawable.ic_star },
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
				String.format(Locale.JAPAN, Column.PATH_FAVOURITED_BY, column.status_id)
			)
		}
	),
	
	DOMAIN_BLOCKS(16,
		iconId = { R.drawable.ic_cloud_off },
		name1 = { it.getString(R.string.blocked_domains) },
		bAllowPseudo = false,
		bAllowMisskey = false,
		
		loading = { client -> getDomainList(client, Column.PATH_DOMAIN_BLOCK) },
		refresh = { client -> getDomainList(client, Column.PATH_DOMAIN_BLOCK) }
	),
	
	SEARCH_MSP(17,
		iconId = { R.drawable.ic_search },
		name1 = { it.getString(R.string.toot_search_msp) },
		name2 = { long ->
			when {
				long -> context.getString(R.string.toot_search_msp_of, search_query)
				else -> context.getString(R.string.toot_search_msp)
			}
		},
		headerType = HeaderType.Search,
		
		loading = { client ->
			column.idOld = null
			val result : TootApiResult?
			val q = column.search_query.trim { it <= ' ' }
			if(q.isEmpty()) {
				list_tmp = ArrayList()
				result = TootApiResult()
			} else {
				result = client.searchMsp(column.search_query, column.idOld?.toString())
				val jsonArray = result?.jsonArray
				if(jsonArray != null) {
					// max_id の更新
					column.idOld = EntityId.mayNull(
						TootApiClient.getMspMaxId(
							jsonArray,
							column.idOld?.toString()
						)
					)
					// リストデータの用意
					parser.serviceType = ServiceType.MSP
					list_tmp =
						addWithFilterStatus(null, parser.statusList(jsonArray))
				}
			}
			result
		},
		
		refresh = { client ->
			
			if(! bBottom) {
				TootApiResult("head of list.")
			} else {
				val result : TootApiResult?
				val q = column.search_query.trim { it <= ' ' }
				if(q.isEmpty()) {
					list_tmp = ArrayList()
					result = TootApiResult(context.getString(R.string.end_of_list))
				} else {
					result = client.searchMsp(column.search_query, column.idOld?.toString())
					val jsonArray = result?.jsonArray
					if(jsonArray != null) {
						// max_id の更新
						column.idOld = EntityId.mayNull(
							TootApiClient.getMspMaxId(
								jsonArray,
								column.idOld?.toString()
							)
						)
						// リストデータの用意
						parser.serviceType = ServiceType.MSP
						list_tmp = addWithFilterStatus(list_tmp, parser.statusList(jsonArray))
					}
				}
				result
			}
		}
	),
	
	SEARCH_TS(22,
		iconId = { R.drawable.ic_search },
		name1 = { it.getString(R.string.toot_search_ts) },
		name2 = { long ->
			when {
				long -> context.getString(R.string.toot_search_ts_of, search_query)
				else -> context.getString(R.string.toot_search_ts)
			}
		},
		headerType = HeaderType.Search,
		
		loading = { client ->
			column.idOld = null
			val result : TootApiResult?
			val q = column.search_query.trim { it <= ' ' }
			if(q.isEmpty()) {
				list_tmp = ArrayList()
				result = TootApiResult()
			} else {
				result =
					client.searchTootsearch(column.search_query, column.idOld?.toLong())
				val jsonObject = result?.jsonObject
				if(jsonObject != null) {
					// max_id の更新
					column.idOld = EntityId.mayNull(
						TootApiClient.getTootsearchMaxId(
							jsonObject,
							column.idOld?.toLong()
						)?.toString()
					)
					
					// リストデータの用意
					val search_result =
						TootStatus.parseListTootsearch(parser, jsonObject)
					this.list_tmp = addWithFilterStatus(null, search_result)
					if(search_result.isEmpty()) {
						log.d("search result is empty. %s", result.bodyString)
					}
				}
			}
			result
		},
		
		refresh = { client ->
			if(! bBottom) {
				TootApiResult("head of list.")
			} else {
				val result : TootApiResult?
				val q = column.search_query.trim { it <= ' ' }
				if(q.isEmpty() || column.idOld == null) {
					list_tmp = ArrayList()
					result = TootApiResult(context.getString(R.string.end_of_list))
				} else {
					result = client.searchTootsearch(
						column.search_query,
						column.idOld?.toString()?.toLong()
					)
					val jsonObject = result?.jsonObject
					if(jsonObject != null) {
						// max_id の更新
						column.idOld = EntityId.mayNull(
							TootApiClient.getTootsearchMaxId(
								jsonObject,
								column.idOld?.toString()?.toLong()
							)?.toString()
						)
						// リストデータの用意
						val search_result = TootStatus.parseListTootsearch(parser, jsonObject)
						list_tmp = addWithFilterStatus(list_tmp, search_result)
					}
				}
				result
			}
		}
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
			val(instanceResult,instance) = TootInstance.get(client,column.instance_uri,allowPixelfed = true)
			if(instance!=null) {
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
			if(isMisskey) {
				parseListList(
					client,
					"/api/users/lists/list",
					misskeyParams = column.makeMisskeyBaseParameter(parser)
				)
			} else {
				parseListList(client, Column.PATH_LIST_LIST)
			}
		}
	),
	
	LIST_TL(20,
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
			if(isMisskey) {
				getStatusList(
					client,
					column.makeListTlUrl(),
					misskeyParams = column.makeMisskeyTimelineParameter(parser)
						.put("listId", column.profile_id)
				)
			} else {
				getStatusList(client, column.makeListTlUrl())
			}
		},
		
		refresh = { client ->
			column.loadListInfo(client, false)
			if(isMisskey) {
				getStatusList(
					client,
					column.makeListTlUrl(),
					misskeyParams = column.makeMisskeyTimelineParameter(parser)
						.put("listId", column.profile_id)
				)
			} else {
				getStatusList(client, column.makeListTlUrl())
			}
		},
		
		gap = { client ->
			if(isMisskey) {
				getStatusList(
					client,
					column.makeListTlUrl(),
					misskeyParams = column.makeMisskeyTimelineParameter(parser)
						.put("listId", column.profile_id)
				)
			} else {
				getStatusList(client, column.makeListTlUrl())
			}
		}
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
			if(isMisskey) {
				column.pagingType = ColumnPagingType.None
				getAccountList(
					client,
					"/api/users/show",
					misskeyParams = access_info.putMisskeyApiToken()
						.put("userIds", JSONArray().apply {
							column.list_info?.userIds?.forEach {
								this.put(it.toString())
							}
						})
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
			getDirectMessages(client)
			
		},
		
		refresh = { client ->
			if(column.useConversationSummarys) {
				// try 2.6.0 new API https://github.com/tootsuite/mastodon/pull/8832
				getConversationSummaryList(client, Column.PATH_DIRECT_MESSAGES2)
			} else {
				// fallback to old api
				getStatusList(client, Column.PATH_DIRECT_MESSAGES)
			}
		},
		
		gap = { client ->
			if(column.useConversationSummarys) {
				// try 2.6.0 new API https://github.com/tootsuite/mastodon/pull/8832
				getConversationSummaryList(client, Column.PATH_DIRECT_MESSAGES2)
			} else {
				// fallback to old api
				getStatusList(client, Column.PATH_DIRECT_MESSAGES)
			}
		},
		bAllowPseudo = false,
		bAllowMisskey = false
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
	
	FOLLOW_SUGGESTION(25,
		iconId = { R.drawable.ic_follow_plus },
		name1 = { it.getString(R.string.follow_suggestion) },
		bAllowPseudo = false,
		
		loading = { client ->
			if(isMisskey) {
				column.pagingType = ColumnPagingType.Offset
				getAccountList(
					client
					, Column.PATH_MISSKEY_FOLLOW_SUGGESTION
					, misskeyParams = access_info.putMisskeyApiToken(JSONObject())
				)
			} else {
				getAccountList(client, Column.PATH_FOLLOW_SUGGESTION)
			}
		},
		
		refresh = { client ->
			if(isMisskey) {
				getAccountList(
					client
					, Column.PATH_MISSKEY_FOLLOW_SUGGESTION
					, misskeyParams = access_info.putMisskeyApiToken(JSONObject())
				)
			} else {
				getAccountList(client, Column.PATH_FOLLOW_SUGGESTION)
			}
		},
		
		gap = { client ->
			if(isMisskey) {
				getAccountList(
					client
					, Column.PATH_MISSKEY_FOLLOW_SUGGESTION
					, misskeyParams = access_info.putMisskeyApiToken(JSONObject())
				)
			} else {
				getAccountList(client, Column.PATH_FOLLOW_SUGGESTION)
			}
		}
	),
	
	ENDORSEMENT(28,
		iconId = { R.drawable.ic_follow_plus },
		name1 = { it.getString(R.string.endorse_set) },
		bAllowPseudo = false,
		bAllowMisskey = false,
		
		loading = { client -> getAccountList(client, Column.PATH_ENDORSEMENT) },
		refresh = { client -> getAccountList(client, Column.PATH_ENDORSEMENT) },
		gap = { client -> getAccountList(client, Column.PATH_ENDORSEMENT) }
	),
	
	PROFILE_DIRECTORY(36,
		iconId = { R.drawable.ic_follow_plus },
		name1 = { it.getString(R.string.profile_directory) },
		name2 = { context.getString(R.string.profile_directory_of, instance_uri)},
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
			val id =status_id?.toString() ?: "null"
			context.getString(R.string.account_tl_around_of,id)
		},
		
		loading = { client -> getAccountAroundStatuses(client) },
		
		refresh = { client ->
			val path = column.makeProfileStatusesUrl(column.profile_id)
			if(bBottom) {
				getStatusList(client, path)
			} else {
				val rv = getStatusList(client, path, aroundMin = true)
				list_tmp?.sortBy { it.getOrderId() }
				list_tmp?.reverse()
				rv
			}
		}
	),
	
	@Suppress("unused")
	REPORTS(6,
		iconId = { R.drawable.ic_info },
		name1 = { it.getString(R.string.reports) },
		
		loading = { client -> getReportList(client, Column.PATH_REPORTS) },
		refresh = { client -> getReportList(client, Column.PATH_REPORTS) },
		gap = { client -> getReportList(client, Column.PATH_REPORTS) }
	),
	
	KEYWORD_FILTER(26,
		iconId = { R.drawable.ic_volume_off },
		name1 = { it.getString(R.string.keyword_filters) },
		bAllowPseudo = false,
		bAllowMisskey = false,
		headerType = HeaderType.Filter,
		
		loading = { client -> parseFilterList(client, Column.PATH_FILTERS) }
	),
	
	SCHEDULED_STATUS(33,
		iconId = { R.drawable.ic_timer },
		name1 = { it.getString(R.string.scheduled_status) },
		bAllowPseudo = false,
		bAllowMisskey = false,
		
		loading = { client -> getScheduledStatuses(client) },
		refresh = { client -> getScheduledStatuses(client) }
	),
	
	;
	
	init {
		val old = Column.typeMap[id]
		if(id > 0 && old != null) error("ColumnType: duplicate id $id. name=$name, ${old.name}")
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
		
		fun parse(id : Int) = Column.typeMap[id] ?: HOME
	}
}

