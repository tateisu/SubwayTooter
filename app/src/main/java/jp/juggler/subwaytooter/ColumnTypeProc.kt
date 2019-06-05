package jp.juggler.subwaytooter

import android.util.SparseArray
import android.view.Gravity
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.util.encodePercent
import jp.juggler.util.toPostRequestBuilder
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

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

class ColumnTypeProc(
	val loading : ColumnTask_Loading.(client : TootApiClient) -> TootApiResult?,
	val refresh : ColumnTask_Refresh.(client : TootApiClient) -> TootApiResult? = unsupportedRefresh,
	val gap : ColumnTask_Gap.(client : TootApiClient) -> TootApiResult? = unsupportedGap
)

private fun SparseArray<ColumnTypeProc>.add(
	type : Int,
	loading : ColumnTask_Loading.(client : TootApiClient) -> TootApiResult?,
	refresh : ColumnTask_Refresh.(client : TootApiClient) -> TootApiResult? = unsupportedRefresh,
	gap : ColumnTask_Gap.(client : TootApiClient) -> TootApiResult? = unsupportedGap
) = put(type, ColumnTypeProc(loading, refresh, gap))

private val profileStatusMastodon = ColumnTypeProc(
	
	loading = { client ->
		var instance = access_info.instance
		
		// まだ取得してない
		// 疑似アカウントの場合は過去のデータが別タンスかもしれない?
		if(instance == null || access_info.isPseudo) {
			getInstanceInformation(client, null)
			if(instance_tmp != null) {
				instance = instance_tmp
				access_info.instance = instance
			}
		}
		
		val path = column.makeProfileStatusesUrl(column.profile_id)
		
		if(instance?.versionGE(TootInstance.VERSION_1_6) == true
		// 将来的に正しく判定できる見込みがないので、Pleroma条件でのフィルタは行わない
		// && instance.instanceType != TootInstance.InstanceType.Pleroma
		) {
			getStatusesPinned(client, "$path&pinned=true")
		}
		
		getStatusList(client, path)
	},
	
	refresh = { client -> getStatusList(client, column.makeProfileStatusesUrl(column.profile_id)) },
	gap = { client -> getStatusList(client, column.makeProfileStatusesUrl(column.profile_id)) }
)

private val profileStatusMisskey = ColumnTypeProc(
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
)

private val followingMastodon = ColumnTypeProc(
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
)

private val followingMastodonPseudo = ColumnTypeProc(
	loading = {
		column.idRecent = null
		column.idOld = null
		list_tmp = addOne(
			list_tmp,
			TootMessageHolder(context.getString(R.string.pseudo_account_cant_get_follow_list))
		)
		TootApiResult()
	}
)

private val followingMisskey10 = ColumnTypeProc(
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
)

private val followingMisskey11 = ColumnTypeProc(
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
)

private val followersMisskey11 = ColumnTypeProc(
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
)

private val followersMisskey10 = ColumnTypeProc(
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
)

private val followersMastodonPseudo = ColumnTypeProc(
	loading = {
		column.idRecent = null
		column.idOld = null
		list_tmp = addOne(
			list_tmp,
			TootMessageHolder(context.getString(R.string.pseudo_account_cant_get_follow_list))
		)
		TootApiResult()
	}
)

private val followersMastodon = ColumnTypeProc(
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
)

private val profileTabProcMap = SparseArray<ColumnTypeProc>().apply {
	
	add(Column.TAB_STATUS,
		
		loading = { client ->
			when {
				isMisskey -> profileStatusMisskey.loading(this, client)
				else -> profileStatusMastodon.loading(this, client)
			}
		},
		
		refresh = { client ->
			when {
				isMisskey -> profileStatusMisskey.refresh(this, client)
				else -> profileStatusMastodon.refresh(this, client)
			}
		},
		
		gap = { client ->
			when {
				isMisskey -> profileStatusMisskey.gap(this, client)
				else -> profileStatusMastodon.gap(this, client)
			}
		}
	)
	
	
	add(Column.TAB_FOLLOWING,
		loading = { client ->
			when {
				misskeyVersion >= 11 -> followingMisskey11.loading(this, client)
				isMisskey -> followingMisskey10.loading(this, client)
				access_info.isPseudo -> followingMastodonPseudo.loading(this, client)
				else -> followingMastodon.loading(this, client)
			}
		},
		
		refresh = { client ->
			when {
				misskeyVersion >= 11 -> followingMisskey11.refresh(this, client)
				isMisskey -> followingMisskey10.refresh(this, client)
				else -> followingMastodon.refresh(this, client)
			}
		},
		
		gap = { client ->
			when {
				misskeyVersion >= 11 -> followingMisskey11.gap(this, client)
				isMisskey -> followingMisskey10.gap(this, client)
				else -> followingMastodon.gap(this, client)
			}
		}
	)
	
	
	add(Column.TAB_FOLLOWERS,
		
		loading = { client ->
			when {
				misskeyVersion >= 11 -> followersMisskey11.loading(this, client)
				isMisskey -> followersMisskey10.loading(this, client)
				access_info.isPseudo -> followersMastodonPseudo.loading(this, client)
				else -> followersMastodon.loading(this, client)
			}
		},
		
		refresh = { client ->
			when {
				misskeyVersion >= 11 -> followersMisskey11.refresh(this, client)
				isMisskey -> followersMisskey10.refresh(this, client)
				access_info.isPseudo -> followersMastodonPseudo.refresh(this, client)
				else -> followersMastodon.refresh(this, client)
			}
		},
		
		gap = { client ->
			when {
				misskeyVersion >= 11 -> followersMisskey11.gap(this, client)
				isMisskey -> followersMisskey10.gap(this, client)
				access_info.isPseudo -> followersMastodonPseudo.gap(this, client)
				else -> followersMastodon.gap(this, client)
			}
		}
	)
}

val columnTypeProcMap = SparseArray<ColumnTypeProc>().apply {
	
	add(Column.TYPE_HOME,
		loading = { client -> getStatusList(client, column.makeHomeTlUrl()) },
		refresh = { client -> getStatusList(client, column.makeHomeTlUrl()) },
		gap = { client -> getStatusList(client, column.makeHomeTlUrl()) }
	)
	
	add(Column.TYPE_LOCAL,
		loading = { client -> getStatusList(client, column.makePublicLocalUrl()) },
		refresh = { client -> getStatusList(client, column.makePublicLocalUrl()) },
		gap = { client -> getStatusList(client, column.makePublicLocalUrl()) }
	)
	
	add(Column.TYPE_FEDERATE,
		loading = { client -> getStatusList(client, column.makePublicFederateUrl()) },
		refresh = { client -> getStatusList(client, column.makePublicFederateUrl()) },
		gap = { client -> getStatusList(client, column.makePublicFederateUrl()) }
	)
	
	add(Column.TYPE_MISSKEY_HYBRID,
		loading = { client -> getStatusList(client, column.makeMisskeyHybridTlUrl()) },
		refresh = { client -> getStatusList(client, column.makeMisskeyHybridTlUrl()) },
		gap = { client -> getStatusList(client, column.makeMisskeyHybridTlUrl()) }
	)
	
	add(Column.TYPE_LOCAL_AROUND,
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
	)
	
	add(Column.TYPE_FEDERATED_AROUND,
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
	)
	
	add(Column.TYPE_PROFILE,
		
		loading = { client ->
			val who_result = column.loadProfileAccount(client, parser, true)
			if(client.isApiCancelled || column.who_account == null) return@add who_result
			(profileTabProcMap[column.profile_tab] ?: profileTabProcMap[Column.TAB_STATUS])
				.loading(this, client)
		},
		
		refresh = { client ->
			column.loadProfileAccount(client, parser, false)
			(profileTabProcMap[column.profile_tab] ?: profileTabProcMap[Column.TAB_STATUS])
				.refresh(this, client)
			
		},
		
		gap = { client ->
			(profileTabProcMap[column.profile_tab] ?: profileTabProcMap[Column.TAB_STATUS])
				.gap(this, client)
		}
	)
	
	add(Column.TYPE_FAVOURITES,
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
	)
	
	add(Column.TYPE_NOTIFICATIONS,
		loading = { client -> getNotificationList(client) },
		refresh = { client -> getNotificationList(client) },
		gap = { client -> getNotificationList(client) }
	)
	
	add(Column.TYPE_NOTIFICATION_FROM_ACCT,
		loading = { client -> getNotificationList(client, column.hashtag_acct) },
		refresh = { client -> getNotificationList(client, column.hashtag_acct) },
		gap = { client -> getNotificationList(client, column.hashtag_acct) }
	)
	
	add(Column.TYPE_CONVERSATION,
		loading = { client ->
			
			if(isMisskey) {
				// 指定された発言そのもの
				val queryParams = column.makeMisskeyBaseParameter(parser)
					.put("noteId", column.status_id)
				var result = client.request(
					"/api/notes/show"
					, queryParams.toPostRequestBuilder()
				)
				val jsonObject = result?.jsonObject ?: return@add result
				val target_status = parser.status(jsonObject)
					?: return@add TootApiResult("TootStatus parse failed.")
				target_status.conversation_main = true
				
				// 祖先
				val list_asc = ArrayList<TootStatus>()
				while(true) {
					if(client.isApiCancelled) return@add null
					queryParams.put("offset", list_asc.size)
					result = client.request(
						"/api/notes/conversation"
						, queryParams.toPostRequestBuilder()
					)
					val jsonArray = result?.jsonArray ?: return@add result
					val src = parser.statusList(jsonArray)
					if(src.isEmpty()) break
					list_asc.addAll(src)
				}
				
				// 直接の子リプライ。(子孫をたどることまではしない)
				val list_desc = ArrayList<TootStatus>()
				val idSet = HashSet<EntityId>()
				var untilId : EntityId? = null
				
				while(true) {
					if(client.isApiCancelled) return@add null
					
					when {
						untilId == null -> {
							queryParams.remove("untilId")
							queryParams.remove("offset")
						}
						
						misskeyVersion >= 11 -> {
							queryParams.put("untilId", untilId.toString())
						}
						
						else -> queryParams.put("offset", list_desc.size)
					}
					
					result = client.request(
						"/api/notes/replies"
						, queryParams.toPostRequestBuilder()
					)
					val jsonArray = result?.jsonArray ?: return@add result
					val src = parser.statusList(jsonArray)
					untilId = null
					for(status in src) {
						if(idSet.contains(status.id)) continue
						idSet.add(status.id)
						list_desc.add(status)
						untilId = status.id
					}
					if(untilId == null) break
				}
				
				// 一つのリストにまとめる
				this.list_tmp = ArrayList<TimelineItem>(
					list_asc.size + list_desc.size + 2
				).apply {
					addAll(list_asc.sortedBy { it.time_created_at })
					add(target_status)
					addAll(list_desc.sortedBy { it.time_created_at })
					add(TootMessageHolder(context.getString(R.string.misskey_cant_show_all_descendants)))
				}
				
				//
				result
				
			} else {
				// 指定された発言そのもの
				var result = client.request(
					String.format(Locale.JAPAN, Column.PATH_STATUSES, column.status_id)
				)
				var jsonObject = result?.jsonObject ?: return@add result
				val target_status = parser.status(jsonObject)
					?: return@add TootApiResult("TootStatus parse failed.")
				
				// 前後の会話
				result = client.request(
					String.format(
						Locale.JAPAN,
						Column.PATH_STATUSES_CONTEXT, column.status_id
					)
				)
				jsonObject = result?.jsonObject ?: return@add result
				val conversation_context =
					parseItem(::TootContext, parser, jsonObject)
				
				// 一つのリストにまとめる
				target_status.conversation_main = true
				if(conversation_context != null) {
					
					this.list_tmp = ArrayList(
						1
							+ (conversation_context.ancestors?.size ?: 0)
							+ (conversation_context.descendants?.size ?: 0)
					)
					//
					if(conversation_context.ancestors != null)
						addWithFilterStatus(
							this.list_tmp,
							conversation_context.ancestors
						)
					//
					addOne(list_tmp, target_status)
					//
					if(conversation_context.descendants != null)
						addWithFilterStatus(
							this.list_tmp,
							conversation_context.descendants
						)
					//
				} else {
					this.list_tmp = addOne(this.list_tmp, target_status)
					this.list_tmp = addOne(
						this.list_tmp,
						TootMessageHolder(context.getString(R.string.toot_context_parse_failed))
					)
				}
				
				result
			}
			
		}
	)
	add(Column.TYPE_HASHTAG,
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
	)
	
	add(Column.TYPE_HASHTAG_FROM_ACCT,
		
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
	)
	
	add(Column.TYPE_SEARCH,
		loading = { client ->
			if(isMisskey) {
				var result : TootApiResult? = TootApiResult()
				val parser = TootParser(context, access_info)
				
				list_tmp = ArrayList()
				
				val queryAccount = column.search_query.trim().replace("^@".toRegex(), "")
				if(queryAccount.isNotEmpty()) {
					result = client.request(
						"/api/users/search",
						access_info.putMisskeyApiToken()
							.put("query", queryAccount)
							.put("localOnly", ! column.search_resolve).toPostRequestBuilder()
					)
					val jsonArray = result?.jsonArray
					if(jsonArray != null) {
						val src =
							TootParser(context, access_info).accountList(jsonArray)
						list_tmp = addAll(list_tmp, src)
					}
				}
				
				val queryTag = column.search_query.trim().replace("^#".toRegex(), "")
				if(queryTag.isNotEmpty()) {
					result = client.request(
						"/api/hashtags/search",
						access_info.putMisskeyApiToken()
							.put("query", queryTag)
							.toPostRequestBuilder()
					)
					val jsonArray = result?.jsonArray
					if(jsonArray != null) {
						val src = TootTag.parseTootTagList(parser, jsonArray)
						list_tmp = addAll(list_tmp, src)
					}
				}
				if(column.search_query.isNotEmpty()) {
					result = client.request(
						"/api/notes/search",
						access_info.putMisskeyApiToken()
							.put("query", column.search_query)
							.toPostRequestBuilder()
					)
					val jsonArray = result?.jsonArray
					if(jsonArray != null) {
						val src = parser.statusList(jsonArray)
						list_tmp = addWithFilterStatus(list_tmp, src)
					}
				}
				
				// 検索機能が無効だとsearch_query が 400を返すが、他のAPIがデータを返したら成功したことにする
				if(list_tmp?.isNotEmpty() == true) {
					TootApiResult()
				} else {
					result
				}
			} else {
				if(access_info.isPseudo) {
					// 1.5.0rc からマストドンの検索APIは認証を要求するようになった
					return@add TootApiResult(context.getString(R.string.search_is_not_available_on_pseudo_account))
				}
				
				var instance = access_info.instance
				if(instance == null) {
					getInstanceInformation(client, null)
					if(instance_tmp != null) {
						instance = instance_tmp
						access_info.instance = instance
					}
				}
				
				var result : TootApiResult?
				
				if(instance?.versionGE(TootInstance.VERSION_2_4_0) == true) {
					// v2 api を試す
					var path = String.format(
						Locale.JAPAN,
						Column.PATH_SEARCH_V2,
						column.search_query.encodePercent()
					)
					if(column.search_resolve) path += "&resolve=1"
					
					result = client.request(path)
					val jsonObject = result?.jsonObject
					if(jsonObject != null) {
						val tmp = parser.resultsV2(jsonObject)
						if(tmp != null) {
							list_tmp = ArrayList()
							addAll(list_tmp, tmp.hashtags)
							addAll(list_tmp, tmp.accounts)
							addAll(list_tmp, tmp.statuses)
							return@add result
						}
					}
					if(instance.versionGE(TootInstance.VERSION_2_4_1_rc1)) {
						// 2.4.1rc1以降はv2が確実に存在するはずなので、v1へのフォールバックを行わない
						return@add result
					}
				}
				
				var path = String.format(
					Locale.JAPAN,
					Column.PATH_SEARCH,
					column.search_query.encodePercent()
				)
				if(column.search_resolve) path += "&resolve=1"
				
				result = client.request(path)
				val jsonObject = result?.jsonObject
				if(jsonObject != null) {
					val tmp = parser.results(jsonObject)
					if(tmp != null) {
						list_tmp = ArrayList()
						addAll(list_tmp, tmp.hashtags)
						addAll(list_tmp, tmp.accounts)
						addAll(list_tmp, tmp.statuses)
					}
				}
				
				result
			}
			
		}
	)
	
	add(Column.TYPE_MUTES,
		loading = { client ->
			
			when {
				misskeyVersion >= 11 -> {
					column.pagingType = ColumnPagingType.Default
					getAccountList(
						client,
						Column.PATH_MISSKEY_MUTES,
						misskeyParams = access_info.putMisskeyApiToken(),
						misskeyCustomParser = misskeyCustomParserMutes
					)
					
				}
				isMisskey -> {
					// misskey v10
					column.pagingType = ColumnPagingType.Cursor
					getAccountList(
						client,
						Column.PATH_MISSKEY_MUTES,
						misskeyParams = access_info.putMisskeyApiToken(),
						misskeyArrayFinder = misskeyArrayFinderUsers
					)
				}
				else -> getAccountList(client, Column.PATH_MUTES)
			}
		},
		
		refresh = { client ->
			when {
				misskeyVersion >= 11 -> getAccountList(
					client, Column.PATH_MISSKEY_MUTES,
					misskeyParams = access_info.putMisskeyApiToken(),
					misskeyCustomParser = misskeyCustomParserMutes
				)
				isMisskey -> getAccountList(
					client, Column.PATH_MISSKEY_MUTES,
					misskeyParams = access_info.putMisskeyApiToken(),
					misskeyArrayFinder = misskeyArrayFinderUsers
				)
				else -> getAccountList(client, Column.PATH_MUTES)
			}
		},
		
		gap = { client ->
			when {
				misskeyVersion >= 11 -> getAccountList(
					client,
					Column.PATH_MISSKEY_MUTES,
					misskeyParams = access_info.putMisskeyApiToken(),
					misskeyCustomParser = misskeyCustomParserMutes
				)
				isMisskey -> getAccountList(
					client,
					Column.PATH_MISSKEY_MUTES,
					misskeyParams = access_info.putMisskeyApiToken(),
					misskeyArrayFinder = misskeyArrayFinderUsers
				)
				else -> getAccountList(client, Column.PATH_MUTES)
			}
		}
	)
	
	add(Column.TYPE_BLOCKS,
		loading = { client ->
			when {
				isMisskey -> {
					column.pagingType = ColumnPagingType.Default
					getAccountList(
						client,
						"/api/blocking/list",
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
					column.pagingType = ColumnPagingType.Default
					getAccountList(
						client,
						"/api/blocking/list",
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
					column.pagingType = ColumnPagingType.Default
					getAccountList(
						client,
						"/api/blocking/list",
						misskeyParams = access_info.putMisskeyApiToken(),
						misskeyCustomParser = misskeyCustomParserBlocks
					)
				}
				else -> getAccountList(client, Column.PATH_BLOCKS)
			}
		}
	)
	
	add(Column.TYPE_FOLLOW_REQUESTS,
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
	)
	
	add(Column.TYPE_BOOSTED_BY,
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
	)
	
	add(Column.TYPE_FAVOURITED_BY,
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
	)
	
	add(Column.TYPE_DOMAIN_BLOCKS,
		loading = { client -> getDomainList(client, Column.PATH_DOMAIN_BLOCK) },
		refresh = { client -> getDomainList(client, Column.PATH_DOMAIN_BLOCK) }
	)
	
	add(Column.TYPE_SEARCH_MSP,
		
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
	)
	
	add(Column.TYPE_SEARCH_TS,
		
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
						ColumnTask_Loading.log.d("search result is empty. %s", result.bodyString)
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
	)
	
	add(Column.TYPE_INSTANCE_INFORMATION,
		loading = { client ->
			val result = getInstanceInformation(client, column.instance_uri)
			if(instance_tmp != null) {
				column.instance_information = instance_tmp
				column.handshake = result?.response?.handshake()
			}
			result
		}
	)
	
	add(Column.TYPE_LIST_LIST,
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
	)
	
	add(Column.TYPE_LIST_TL,
		
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
	)
	
	add(Column.TYPE_LIST_MEMBER,
		
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
	)
	
	add(Column.TYPE_DIRECT_MESSAGES,
		loading = { client ->
			column.useConversationSummarys = false
			if(! column.use_old_api) {
				
				// try 2.6.0 new API https://github.com/tootsuite/mastodon/pull/8832
				val resultCS = getConversationSummary(
					client,
					Column.PATH_DIRECT_MESSAGES2
				)
				
				when {
					// cancelled
					resultCS == null -> return@add null
					
					//  not error
					resultCS.error.isNullOrBlank() -> {
						column.useConversationSummarys = true
						return@add resultCS
					}
				}
			}
			
			// fallback to old api
			return@add getStatusList(client, Column.PATH_DIRECT_MESSAGES)
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
		}
	)
	
	add(Column.TYPE_TREND_TAG,
		loading = { client ->
			val result = client.request("/api/v1/trends")
			val src = parser.trendTagList(result?.jsonArray)
			
			this.list_tmp = addAll(this.list_tmp, src)
			this.list_tmp = addOne(
				this.list_tmp, TootMessageHolder(
					context.getString(
						R.string.trend_tag_desc,
						Column.getResetTimeString()
					),
					gravity = Gravity.END
				)
			)
			result
		}
	)
	
	add(Column.TYPE_FOLLOW_SUGGESTION,
		
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
	)
	
	add(Column.TYPE_ENDORSEMENT,
		loading = { client -> getAccountList(client, Column.PATH_ENDORSEMENT) },
		refresh = { client -> getAccountList(client, Column.PATH_ENDORSEMENT) },
		gap = { client -> getAccountList(client, Column.PATH_ENDORSEMENT) }
	)
	
	add(Column.TYPE_ACCOUNT_AROUND,
		
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
	)
	
	add(Column.TYPE_REPORTS,
		loading = { client -> getReportList(client, Column.PATH_REPORTS) },
		refresh = { client -> getReportList(client, Column.PATH_REPORTS) },
		gap = { client -> getReportList(client, Column.PATH_REPORTS) }
	)
	
	add(Column.TYPE_KEYWORD_FILTER,
		loading = { client -> parseFilterList(client, Column.PATH_FILTERS) }
	)
	
	add(
		Column.TYPE_SCHEDULED_STATUS,
		loading = { client -> getScheduledStatuses(client) },
		refresh = { client -> getScheduledStatuses(client) }
	)
	
}