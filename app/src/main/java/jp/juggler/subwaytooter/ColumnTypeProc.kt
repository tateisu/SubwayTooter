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

procLoading,procRefresh,procGap はそれぞれ this の種類が異なるので注意
同じ関数を呼び出してるように見えても実際には異なるクラスの異なる関数を呼び出している場合がある
 
 */

private val unsupportedProcRefresh : ColumnTask_Refresh.(client : TootApiClient) -> TootApiResult? =
	{ TootApiResult("edge reading not supported.") }

private val unsupportedProcGap : ColumnTask_Gap.(client : TootApiClient) -> TootApiResult? =
	{ TootApiResult("gap reading not supported.") }

class ColumnTypeProc(
	val procLoading : ColumnTask_Loading.(client : TootApiClient) -> TootApiResult?,
	val procRefresh : ColumnTask_Refresh.(client : TootApiClient) -> TootApiResult? = unsupportedProcRefresh,
	val procGap : ColumnTask_Gap.(client : TootApiClient) -> TootApiResult? = unsupportedProcGap
)
private fun SparseArray<ColumnTypeProc>.add(
	type : Int,
	procLoading : ColumnTask_Loading.(client : TootApiClient) -> TootApiResult?,
	procRefresh : ColumnTask_Refresh.(client : TootApiClient) -> TootApiResult? = unsupportedProcRefresh,
	procGap : ColumnTask_Gap.(client : TootApiClient) -> TootApiResult? = unsupportedProcGap
) = put(
	type,
	ColumnTypeProc(procLoading, procRefresh, procGap)
)

private val profileStatusMastodon = ColumnTypeProc(
	
	procLoading = { client ->
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
		
		getStatuses(client, path)
	},
	
	procRefresh = { client ->
		getStatusList(client, column.makeProfileStatusesUrl(column.profile_id))
	},
	
	procGap = { client ->
		getStatusList(client, column.makeProfileStatusesUrl(column.profile_id))
	}
)

private val profileStatusMisskey = ColumnTypeProc(
	procLoading = { client ->
		// 固定トゥートの取得
		val pinnedNotes = column.who_account?.get()?.pinnedNotes
		if(pinnedNotes != null) {
			this.list_pinned = addWithFilterStatus(null, pinnedNotes)
		}
		
		// 通常トゥートの取得
		getStatuses(
			client,
			Column.PATH_MISSKEY_PROFILE_STATUSES,
			misskeyParams = column.makeMisskeyParamsProfileStatuses(parser),
			initialUntilDate = true
		)
	},
	procRefresh = { client ->
		getStatusList(
			client,
			Column.PATH_MISSKEY_PROFILE_STATUSES,
			misskeyParams = column.makeMisskeyParamsProfileStatuses(parser)
		)
	},
	procGap = { client ->
		getStatusList(
			client
			, Column.PATH_MISSKEY_PROFILE_STATUSES
			, misskeyParams = column.makeMisskeyParamsProfileStatuses(parser)
		)
	}
)

private val followingMastodon = ColumnTypeProc(
	procLoading = { client ->
		parseAccountList(
			client,
			String.format(Locale.JAPAN, Column.PATH_ACCOUNT_FOLLOWING, column.profile_id),
			emptyMessage = context.getString(R.string.none_or_hidden_following)
		)
	},
	procRefresh = { client ->
		getAccountList(
			client,
			String.format(Locale.JAPAN, Column.PATH_ACCOUNT_FOLLOWING, column.profile_id)
		)
	},
	procGap = { client ->
		getAccountList(
			client,
			String.format(Locale.JAPAN, Column.PATH_ACCOUNT_FOLLOWING, column.profile_id)
		)
	}
)

private val followingMastodonPseudo = ColumnTypeProc(
	procLoading = {
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
	procLoading = { client ->
		column.pagingType = ColumnPagingType.Cursor
		parseAccountList(
			client,
			Column.PATH_MISSKEY_PROFILE_FOLLOWING,
			emptyMessage = context.getString(R.string.none_or_hidden_following),
			misskeyParams = column.makeMisskeyParamsUserId(parser),
			misskeyArrayFinder = misskeyArrayFinderUsers
		)
	},
	procRefresh = { client ->
		getAccountList(
			client,
			Column.PATH_MISSKEY_PROFILE_FOLLOWING,
			misskeyParams = column.makeMisskeyParamsUserId(parser),
			misskeyArrayFinder = misskeyArrayFinderUsers
		)
	},
	procGap = { client ->
		getAccountList(
			client,
			Column.PATH_MISSKEY_PROFILE_FOLLOWING,
			misskeyParams = column.makeMisskeyParamsUserId(parser),
			misskeyArrayFinder = misskeyArrayFinderUsers
		)
	}
)

private val followingMisskey11 = ColumnTypeProc(
	procLoading = { client ->
		column.pagingType = ColumnPagingType.Default
		column.useDate = false
		parseAccountList(
			client,
			Column.PATH_MISSKEY_PROFILE_FOLLOWING,
			emptyMessage = context.getString(R.string.none_or_hidden_following),
			misskeyParams = column.makeMisskeyParamsUserId(parser),
			misskeyCustomParser = misskeyFollowingParser
		)
	},
	procRefresh = { client ->
		column.useDate = false
		getAccountList(
			client,
			Column.PATH_MISSKEY_PROFILE_FOLLOWING,
			misskeyParams = column.makeMisskeyParamsUserId(parser),
			misskeyCustomParser = misskeyFollowingParser
		)
	},
	procGap = { client ->
		getAccountList(
			client,
			Column.PATH_MISSKEY_PROFILE_FOLLOWING,
			misskeyParams = column.makeMisskeyParamsUserId(parser),
			misskeyCustomParser = misskeyFollowingParser
		)
	}
)

private val followersMisskey11 = ColumnTypeProc(
	procLoading = { client ->
		column.pagingType = ColumnPagingType.Default
		parseAccountList(
			client,
			Column.PATH_MISSKEY_PROFILE_FOLLOWERS,
			emptyMessage = context.getString(R.string.none_or_hidden_followers),
			misskeyParams = column.makeMisskeyParamsUserId(parser),
			misskeyCustomParser = misskeyFollowersParser
		)
	},
	
	procRefresh = { client ->
		column.useDate = false
		getAccountList(
			client,
			Column.PATH_MISSKEY_PROFILE_FOLLOWERS,
			misskeyParams = column.makeMisskeyParamsUserId(parser),
			misskeyCustomParser = misskeyFollowersParser
		)
	},
	procGap = { client ->
		getAccountList(
			client,
			Column.PATH_MISSKEY_PROFILE_FOLLOWING,
			misskeyParams = column.makeMisskeyParamsUserId(parser),
			misskeyCustomParser = misskeyFollowersParser
		)
	}
)

private val followersMisskey10 = ColumnTypeProc(
	procLoading = { client ->
		column.pagingType = ColumnPagingType.Cursor
		parseAccountList(
			client,
			Column.PATH_MISSKEY_PROFILE_FOLLOWERS,
			emptyMessage = context.getString(R.string.none_or_hidden_followers),
			misskeyParams = column.makeMisskeyParamsUserId(parser),
			misskeyArrayFinder = misskeyArrayFinderUsers
		)
	},
	
	procRefresh = { client ->
		getAccountList(
			client,
			Column.PATH_MISSKEY_PROFILE_FOLLOWERS,
			misskeyParams = column.makeMisskeyParamsUserId(parser),
			misskeyArrayFinder = misskeyArrayFinderUsers
		)
	},
	procGap = { client ->
		getAccountList(
			client
			, Column.PATH_MISSKEY_PROFILE_FOLLOWERS
			, misskeyParams = column.makeMisskeyParamsUserId(parser)
		)
	}
)

private val followersMastodonPseudo = ColumnTypeProc(
	procLoading = {
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
	procLoading = { client ->
		parseAccountList(
			client,
			String.format(Locale.JAPAN, Column.PATH_ACCOUNT_FOLLOWERS, column.profile_id),
			emptyMessage = context.getString(R.string.none_or_hidden_followers)
		)
	},
	
	procRefresh = { client ->
		getAccountList(
			client,
			String.format(Locale.JAPAN, Column.PATH_ACCOUNT_FOLLOWERS, column.profile_id)
		)
	},
	procGap = { client ->
		getAccountList(
			client,
			String.format(Locale.JAPAN, Column.PATH_ACCOUNT_FOLLOWERS, column.profile_id)
		)
	}
)

private val profileTabProcMap = SparseArray<ColumnTypeProc>().apply {
	
	add(Column.TAB_STATUS,
		
		procLoading = { client ->
			when {
				isMisskey -> profileStatusMisskey.procLoading(this, client)
				else -> profileStatusMastodon.procLoading(this, client)
			}
		},
		
		procRefresh = { client ->
			when {
				isMisskey -> profileStatusMisskey.procRefresh(this, client)
				else -> profileStatusMastodon.procRefresh(this, client)
			}
		},
		
		procGap = { client ->
			when {
				isMisskey -> profileStatusMisskey.procGap(this, client)
				else -> profileStatusMastodon.procGap(this, client)
			}
		}
	)
	
	
	add(Column.TAB_FOLLOWING,
		procLoading = { client ->
			when {
				misskeyVersion >= 11 -> followingMisskey11.procLoading(this, client)
				isMisskey -> followingMisskey10.procLoading(this, client)
				access_info.isPseudo -> followingMastodonPseudo.procLoading(this, client)
				else -> followingMastodon.procLoading(this, client)
			}
		},
		
		procRefresh = { client ->
			when {
				misskeyVersion >= 11 -> followingMisskey11.procRefresh(this, client)
				isMisskey -> followingMisskey10.procRefresh(this, client)
				else -> followingMastodon.procRefresh(this, client)
			}
		},

		procGap = { client ->
			when {
				misskeyVersion >= 11 -> followingMisskey11.procGap(this, client)
				isMisskey -> followingMisskey10.procGap(this, client)
				else -> followingMastodon.procGap(this, client)
			}
		}
	)
	
	
	add(Column.TAB_FOLLOWERS,
		
		procLoading = { client ->
			when {
				misskeyVersion >= 11 -> followersMisskey11.procLoading(this, client)
				isMisskey -> followersMisskey10.procLoading(this, client)
				access_info.isPseudo -> followersMastodonPseudo.procLoading(this, client)
				else -> followersMastodon.procLoading(this, client)
			}
		},
		
		procRefresh = { client ->
			when {
				misskeyVersion >= 11 -> followersMisskey11.procRefresh(this, client)
				isMisskey -> followersMisskey10.procRefresh(this, client)
				access_info.isPseudo -> followersMastodonPseudo.procRefresh(this, client)
				else -> followersMastodon.procRefresh(this, client)
			}
		},
		
		procGap = { client ->
			when {
				misskeyVersion >= 11 -> followersMisskey11.procGap(this, client)
				isMisskey -> followersMisskey10.procGap(this, client)
				access_info.isPseudo -> followersMastodonPseudo.procGap(this, client)
				else -> followersMastodon.procGap(this, client)
			}
		}
	)
}

val columnTypeProcMap = SparseArray<ColumnTypeProc>().apply {
	
	add(Column.TYPE_HOME,
		procLoading = { client -> getStatuses(client, column.makeHomeTlUrl()) },
		procRefresh = { client -> getStatusList(client, column.makeHomeTlUrl()) },
		procGap = { client -> getStatusList(client, column.makeHomeTlUrl()) }
	)
	
	add(Column.TYPE_LOCAL,
		procLoading = { client -> getStatuses(client, column.makePublicLocalUrl()) },
		procRefresh = { client -> getStatusList(client, column.makePublicLocalUrl()) },
		procGap = { client -> getStatusList(client, column.makePublicLocalUrl()) }
	)
	
	add(Column.TYPE_FEDERATE,
		procLoading = { client -> getStatuses(client, column.makePublicFederateUrl()) },
		procRefresh = { client -> getStatusList(client, column.makePublicFederateUrl()) },
		procGap = { client -> getStatusList(client, column.makePublicFederateUrl()) }
	)
	
	add(Column.TYPE_MISSKEY_HYBRID,
		procLoading = { client -> getStatuses(client, column.makeMisskeyHybridTlUrl()) },
		procRefresh = { client -> getStatusList(client, column.makeMisskeyHybridTlUrl()) },
		procGap = { client -> getStatusList(client, column.makeMisskeyHybridTlUrl()) }
	)
	
	add(Column.TYPE_LOCAL_AROUND,
		procLoading = { client ->
			getPublicAroundStatuses(client, column.makePublicLocalUrl())
		},
		procRefresh = { client ->
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
		
		procLoading = { client ->
			getPublicAroundStatuses(client, column.makePublicFederateUrl())
		},
		
		procRefresh = { client ->
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
		
		procLoading = { client ->
			val who_result = column.loadProfileAccount(client, parser, true)
			if(client.isApiCancelled || column.who_account == null) return@add who_result
			(profileTabProcMap[column.profile_tab] ?: profileTabProcMap[Column.TAB_STATUS])
				.procLoading(this, client)
		},
		
		procRefresh = { client ->
			column.loadProfileAccount(client, parser, false)
			(profileTabProcMap[column.profile_tab] ?: profileTabProcMap[Column.TAB_STATUS])
				.procRefresh(this, client)
			
		},
		
		procGap = { client ->
			(profileTabProcMap[column.profile_tab] ?: profileTabProcMap[Column.TAB_STATUS])
				.procGap(this, client)
		}
	)
	
	add(Column.TYPE_FAVOURITES,
		procLoading = { client ->
			if(isMisskey) {
				column.useDate = false
				getStatuses(
					client
					, Column.PATH_MISSKEY_FAVORITES
					, misskeyParams = column.makeMisskeyTimelineParameter(parser)
					, misskeyCustomParser = misskeyCustomParserFavorites
				)
			} else {
				getStatuses(client, Column.PATH_FAVOURITES)
			}
		},
		
		procRefresh = { client ->
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
		
		procGap = { client ->
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
		procLoading = { client -> parseNotifications(client) },
		procRefresh = { client -> getNotificationList(client) },
		procGap = { client -> getNotificationList(client) }
	)
	
	add(Column.TYPE_NOTIFICATION_FROM_ACCT,
		procLoading = { client -> parseNotifications(client, column.hashtag_acct) },
		procRefresh = { client -> getNotificationList(client, column.hashtag_acct) },
		procGap = { client -> getNotificationList(client, column.hashtag_acct) }
	)
	
	add(Column.TYPE_CONVERSATION,
		procLoading = { client ->
			
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
		procLoading = { client ->
			if(isMisskey) {
				getStatuses(
					client
					, column.makeHashtagUrl()
					, misskeyParams = column.makeHashtagParams(parser)
				
				)
			} else {
				getStatuses(client, column.makeHashtagUrl())
			}
		},
		
		procRefresh = { client ->
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
		
		procGap = { client ->
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
		
		procLoading = { client ->
			if(isMisskey) {
				// currently not supported
				getStatuses(
					client
					, column.makeHashtagAcctUrl(client)
					, misskeyParams = column.makeHashtagParams(parser)
				)
			} else {
				getStatuses(client, column.makeHashtagAcctUrl(client))
			}
		},
		
		procRefresh = { client ->
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
		
		procGap = { client ->
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
		procLoading = { client ->
			if(isMisskey) {
				var result : TootApiResult? = TootApiResult()
				val parser = TootParser(context, access_info)
				var params : JSONObject
				
				list_tmp = ArrayList()
				
				val queryAccount = column.search_query.trim().replace("^@".toRegex(), "")
				if(queryAccount.isNotEmpty()) {
					
					params = access_info.putMisskeyApiToken(JSONObject())
						.put("query", queryAccount)
						.put("localOnly", ! column.search_resolve)
					
					result = client.request(
						"/api/users/search",
						params.toPostRequestBuilder()
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
					params = access_info.putMisskeyApiToken(JSONObject())
						.put("query", queryTag)
					result = client.request(
						"/api/hashtags/search",
						params.toPostRequestBuilder()
					)
					val jsonArray = result?.jsonArray
					if(jsonArray != null) {
						val src = TootTag.parseTootTagList(parser, jsonArray)
						list_tmp = addAll(list_tmp, src)
					}
				}
				if(column.search_query.isNotEmpty()) {
					params = access_info.putMisskeyApiToken(JSONObject())
						.put("query", column.search_query)
					result = client.request(
						"/api/notes/search",
						params.toPostRequestBuilder()
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
		procLoading = { client ->
			if(isMisskey) {
				column.pagingType = ColumnPagingType.Cursor
				parseAccountList(
					client
					, Column.PATH_MISSKEY_MUTES
					, misskeyParams = access_info.putMisskeyApiToken(JSONObject())
					, misskeyArrayFinder = misskeyArrayFinderUsers
				)
			} else {
				parseAccountList(client, Column.PATH_MUTES)
			}
		},
		procRefresh = { client ->
			if(isMisskey) {
				getAccountList(
					client
					, Column.PATH_MISSKEY_MUTES
					, misskeyParams = access_info.putMisskeyApiToken(JSONObject())
					, misskeyArrayFinder = misskeyArrayFinderUsers
				)
			} else {
				getAccountList(client, Column.PATH_MUTES)
			}
		},
		procGap = { client ->
			if(isMisskey) {
				getAccountList(
					client
					, Column.PATH_MISSKEY_MUTES
					, misskeyParams = access_info.putMisskeyApiToken(JSONObject())
					
					, misskeyArrayFinder = misskeyArrayFinderUsers
				)
			} else {
				getAccountList(client, Column.PATH_MUTES)
			}
		}
	)
	
	add(Column.TYPE_BLOCKS,
		procLoading = { client ->
			if(isMisskey) {
				column.pagingType = ColumnPagingType.Default
				val params = access_info.putMisskeyApiToken(JSONObject())
				parseAccountList(
					client,
					"/api/blocking/list",
					misskeyParams = params,
					misskeyCustomParser = misskeyCustomParserBlocks
				)
			} else {
				parseAccountList(client, Column.PATH_BLOCKS)
			}
		},
		procRefresh = { client ->
			if(isMisskey) {
				column.pagingType = ColumnPagingType.Default
				val params = access_info.putMisskeyApiToken(JSONObject())
				getAccountList(
					client,
					"/api/blocking/list",
					misskeyParams = params,
					misskeyCustomParser = misskeyCustomParserBlocks
				)
			} else {
				getAccountList(client, Column.PATH_BLOCKS)
			}
		},
		procGap = { client ->
			if(isMisskey) {
				column.pagingType = ColumnPagingType.Default
				val params = access_info.putMisskeyApiToken(JSONObject())
				getAccountList(
					client,
					"/api/blocking/list",
					misskeyParams = params,
					misskeyCustomParser = misskeyCustomParserBlocks
				)
			} else {
				getAccountList(client, Column.PATH_BLOCKS)
			}
		}
	)
	
	add(Column.TYPE_FOLLOW_REQUESTS,
		procLoading = { client ->
			if(isMisskey) {
				column.pagingType = ColumnPagingType.None
				parseAccountList(
					client
					, Column.PATH_MISSKEY_FOLLOW_REQUESTS
					, misskeyParams = access_info.putMisskeyApiToken(JSONObject())
					, misskeyCustomParser = misskeyCustomParserFollowRequest
				)
			} else {
				parseAccountList(client, Column.PATH_FOLLOW_REQUESTS)
			}
		},
		procRefresh = { client ->
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
		procGap = { client ->
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
		procLoading = { client ->
			parseAccountList(
				client,
				String.format(Locale.JAPAN, Column.PATH_BOOSTED_BY, column.status_id)
			)
		},
		procRefresh = { client ->
			getAccountList(
				client,
				String.format(Locale.JAPAN, Column.PATH_BOOSTED_BY, posted_status_id)
			)
		},
		procGap = { client ->
			getAccountList(
				client,
				String.format(Locale.JAPAN, Column.PATH_BOOSTED_BY, column.status_id)
			)
		}
	)
	
	add(Column.TYPE_FAVOURITED_BY,
		procLoading = { client ->
			parseAccountList(
				client,
				String.format(Locale.JAPAN, Column.PATH_FAVOURITED_BY, column.status_id)
			)
		},
		procRefresh = { client ->
			getAccountList(
				client,
				String.format(Locale.JAPAN, Column.PATH_FAVOURITED_BY, posted_status_id)
			)
		},
		procGap = { client ->
			getAccountList(
				client,
				String.format(Locale.JAPAN, Column.PATH_FAVOURITED_BY, column.status_id)
			)
		}
	)
	
	add(Column.TYPE_DOMAIN_BLOCKS,
		procLoading = { client -> parseDomainList(client, Column.PATH_DOMAIN_BLOCK) },
		procRefresh = { client -> getDomainList(client, Column.PATH_DOMAIN_BLOCK) }
	)
	
	add(Column.TYPE_SEARCH_MSP,
		
		procLoading = { client ->
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
		
		procRefresh = { client ->
			
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
		
		procLoading = { client ->
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
		
		procRefresh = { client ->
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
		procLoading = { client ->
			val result = getInstanceInformation(client, column.instance_uri)
			if(instance_tmp != null) {
				column.instance_information = instance_tmp
				column.handshake = result?.response?.handshake()
			}
			result
		}
	)
	
	add(Column.TYPE_LIST_LIST,
		procLoading = { client ->
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
		
		procLoading = { client ->
			column.loadListInfo(client, true)
			if(isMisskey) {
				val params = column.makeMisskeyTimelineParameter(parser)
					.put("listId", column.profile_id)
				getStatuses(client, column.makeListTlUrl(), misskeyParams = params)
			} else {
				getStatuses(client, column.makeListTlUrl())
			}
		},
		
		procRefresh = { client ->
			column.loadListInfo(client, false)
			if(isMisskey) {
				val params = column.makeMisskeyTimelineParameter(parser)
					.put("listId", column.profile_id)
				getStatusList(client, column.makeListTlUrl(), misskeyParams = params)
			} else {
				getStatusList(client, column.makeListTlUrl())
			}
		},
		
		procGap = { client ->
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
		
		procLoading = { client ->
			column.loadListInfo(client, true)
			if(isMisskey) {
				column.pagingType = ColumnPagingType.None
				val params = access_info.putMisskeyApiToken(JSONObject())
					.put("userIds", JSONArray().apply {
						column.list_info?.userIds?.forEach {
							this.put(it.toString())
						}
					})
				parseAccountList(
					client,
					"/api/users/show",
					misskeyParams = params
				)
				
			} else {
				parseAccountList(
					client,
					String.format(Locale.JAPAN, Column.PATH_LIST_MEMBER, column.profile_id)
				)
			}
		},
		
		procRefresh = { client ->
			column.loadListInfo(client, false)
			getAccountList(
				client,
				String.format(Locale.JAPAN, Column.PATH_LIST_MEMBER, column.profile_id)
			)
		}
	)
	
	add(Column.TYPE_DIRECT_MESSAGES,
		procLoading = { client ->
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
			return@add getStatuses(client, Column.PATH_DIRECT_MESSAGES)
		},
		
		procRefresh = { client ->
			if(column.useConversationSummarys) {
				// try 2.6.0 new API https://github.com/tootsuite/mastodon/pull/8832
				getConversationSummaryList(client, Column.PATH_DIRECT_MESSAGES2)
			} else {
				// fallback to old api
				getStatusList(client, Column.PATH_DIRECT_MESSAGES)
			}
		},
		
		procGap = { client ->
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
		procLoading = { client ->
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
		
		procLoading = { client ->
			if(isMisskey) {
				column.pagingType = ColumnPagingType.Offset
				parseAccountList(
					client
					, Column.PATH_MISSKEY_FOLLOW_SUGGESTION
					, misskeyParams = access_info.putMisskeyApiToken(JSONObject())
				)
			} else {
				parseAccountList(client, Column.PATH_FOLLOW_SUGGESTION)
			}
		},
		
		procRefresh = { client ->
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
		
		procGap = { client ->
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
		procLoading = { client -> parseAccountList(client, Column.PATH_ENDORSEMENT) },
		procRefresh = { client -> getAccountList(client, Column.PATH_ENDORSEMENT) },
		procGap = { client -> getAccountList(client, Column.PATH_ENDORSEMENT) }
	)
	
	add(Column.TYPE_ACCOUNT_AROUND,
		
		procLoading = { client -> getAccountAroundStatuses(client) },
		
		procRefresh = { client ->
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
		procLoading = { client -> parseReports(client, Column.PATH_REPORTS) },
		procRefresh = { client -> getReportList(client, Column.PATH_REPORTS) },
		procGap = { client -> getReportList(client, Column.PATH_REPORTS) }
	)
	
	add(Column.TYPE_KEYWORD_FILTER,
		procLoading = { client -> parseFilterList(client, Column.PATH_FILTERS) }
	)
	
	add(
		Column.TYPE_SCHEDULED_STATUS,
		procLoading = { client -> getScheduledStatuses(client) },
		procRefresh = { client -> getScheduledStatuses(client) }
	)
	
}