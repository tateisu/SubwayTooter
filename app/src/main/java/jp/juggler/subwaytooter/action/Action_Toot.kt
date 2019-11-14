package jp.juggler.subwaytooter.action

import android.text.SpannableStringBuilder
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.AccountPicker
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.dialog.DlgConfirm
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.EmptyCallback
import jp.juggler.subwaytooter.util.SavedAccountCallback
import jp.juggler.util.*
import okhttp3.Request
import org.json.JSONObject
import java.util.*
import java.util.regex.Pattern

object Action_Toot {
	
	private val log = LogCategory("Action_Toot")
	
	private val reDetailedStatusTime =
		Pattern.compile("""<a\b[^>]*?\bdetailed-status__datetime\b[^>]*href="https://[^/]+/@[^/]+/([^\s?#/"]+)""")
	
	// アカウントを選んでお気に入り
	fun favouriteFromAnotherAccount(
		activity : ActMain,
		timeline_account : SavedAccount,
		status : TootStatus?
	) {
		if(status == null) return
		val who_host = timeline_account.host
		
		AccountPicker.pick(
			activity,
			bAllowPseudo = false,
			bAuto = false,
			message = activity.getString(R.string.account_picker_favourite),
			accountListArg = makeAccountListNonPseudo(activity, who_host)
		) { action_account ->
			favourite(
				activity,
				action_account,
				status,
				calcCrossAccountMode(timeline_account, action_account),
				callback = activity.favourite_complete_callback
			)
		}
	}
	
	// お気に入りの非同期処理
	fun favourite(
		activity : ActMain,
		access_info : SavedAccount,
		arg_status : TootStatus,
		nCrossAccountMode : Int,
		callback : EmptyCallback?,
		bSet : Boolean = true,
		bConfirmed : Boolean = false
	) {
		if(App1.getAppState(activity).isBusyFav(access_info, arg_status)) {
			showToast(activity, false, R.string.wait_previous_operation)
			return
		}
		
		// 必要なら確認を出す
		if(! bConfirmed && ! access_info.isMisskey) {
			DlgConfirm.open(
				activity,
				activity.getString(
					when(bSet) {
						true -> R.string.confirm_favourite_from
						else -> R.string.confirm_unfavourite_from
					},
					AcctColor.getNickname(access_info.acct)
				),
				object : DlgConfirm.Callback {
					
					override fun onOK() {
						favourite(
							activity,
							access_info,
							arg_status,
							nCrossAccountMode,
							callback,
							bSet = bSet,
							bConfirmed = true
						)
					}
					
					override var isConfirmEnabled : Boolean
						get() = when(bSet) {
							true -> access_info.confirm_favourite
							else -> access_info.confirm_unfavourite
							
						}
						set(value) {
							when(bSet) {
								true -> access_info.confirm_favourite = value
								else -> access_info.confirm_unfavourite = value
							}
							access_info.saveSetting()
							activity.reloadAccountSetting(access_info)
						}
					
				})
			return
		}
		
		//
		App1.getAppState(activity).setBusyFav(access_info, arg_status)
		
		//
		TootTaskRunner(activity, TootTaskRunner.PROGRESS_NONE).run(access_info, object : TootTask {
			
			var new_status : TootStatus? = null
			override fun background(client : TootApiClient) : TootApiResult? {
				
				val target_status = if(nCrossAccountMode == CROSS_ACCOUNT_REMOTE_INSTANCE) {
					
					val (result, status) = client.syncStatus(access_info, arg_status)
					status ?: return result
					if(status.favourited) {
						return TootApiResult(activity.getString(R.string.already_favourited))
					}
					status
				} else {
					arg_status
				}
				
				
				return if(access_info.isMisskey) {
					client.request(
						if(bSet) {
							"/api/notes/favorites/create"
						} else {
							"/api/notes/favorites/delete"
						},
						access_info
							.putMisskeyApiToken(JSONObject())
							.put("noteId", target_status.id.toString())
							.toPostRequestBuilder()
					)?.also { result ->
						// 正常レスポンスは 204 no content
						// 既にお気に入り済みならエラー文字列に'already favorited' が返る
						if(result.response?.code == 204
							|| result.error?.contains("already favorited") == true
							|| result.error?.contains("already not favorited") == true
						) {
							// 成功した
							new_status = target_status.apply {
								favourited = bSet
							}
						}
					}
				} else {
					client.request(
						"/api/v1/statuses/${target_status.id}/${if(bSet) "favourite" else "unfavourite"}",
						"".toFormRequestBody().toPost()
					)?.also { result ->
						new_status = TootParser(activity, access_info).status(result.jsonObject)
					}
				}
			}
			
			override fun handleResult(result : TootApiResult?) {
				
				App1.getAppState(activity).resetBusyFav(access_info, arg_status)
				
				val new_status = this.new_status
				when {
					result == null -> {
					} // cancelled.
					new_status != null -> {
						
						val old_count = arg_status.favourites_count
						val new_count = new_status.favourites_count
						if(old_count != null && new_count != null) {
							if(access_info.isMisskey) {
								new_status.favourited = bSet
							}
							if(bSet && new_status.favourited && new_count <= old_count) {
								// 星をつけたのにカウントが上がらないのは違和感あるので、表示をいじる
								new_status.favourites_count = old_count + 1L
							} else if(! bSet && ! new_status.favourited && new_count >= old_count) {
								// 星を外したのにカウントが下がらないのは違和感あるので、表示をいじる
								// 0未満にはならない
								new_status.favourites_count =
									if(old_count < 1L) 0L else old_count - 1L
							}
						}
						
						for(column in App1.getAppState(activity).column_list) {
							column.findStatus(access_info.host, new_status.id) { account, status ->
								
								// 同タンス別アカウントでもカウントは変化する
								status.favourites_count = new_status.favourites_count
								
								// 同アカウントならfav状態を変化させる
								if(access_info.acct == account.acct) {
									status.favourited = new_status.favourited
								}
								
								true
							}
						}
						if(callback != null) callback()
						
					}
					
					else -> showToast(activity, true, result.error)
				}
				// 結果に関わらず、更新中状態から復帰させる
				activity.showColumnMatchAccount(access_info)
				
			}
		})
		
		// ファボ表示を更新中にする
		activity.showColumnMatchAccount(access_info)
	}
	
	// アカウントを選んでお気に入り
	fun bookmarkFromAnotherAccount(
		activity : ActMain,
		timeline_account : SavedAccount,
		status : TootStatus?
	) {
		if(status == null) return
		val who_host = timeline_account.host
		
		AccountPicker.pick(
			activity,
			bAllowPseudo = false,
			bAuto = false,
			message = activity.getString(R.string.account_picker_bookmark),
			accountListArg = makeAccountListNonPseudo(activity, who_host)
		) { action_account ->
			bookmark(
				activity,
				action_account,
				status,
				calcCrossAccountMode(timeline_account, action_account),
				callback = activity.bookmark_complete_callback
			)
		}
	}
	
	// お気に入りの非同期処理
	fun bookmark(
		activity : ActMain,
		access_info : SavedAccount,
		arg_status : TootStatus,
		nCrossAccountMode : Int,
		callback : EmptyCallback?,
		bSet : Boolean = true,
		bConfirmed : Boolean = false
	) {
		if(App1.getAppState(activity).isBusyFav(access_info, arg_status)) {
			showToast(activity, false, R.string.wait_previous_operation)
			return
		}
		if( access_info.isMisskey ){
			showToast(activity, false, R.string.misskey_account_not_supported)
			return
		}
		
		
		// 必要なら確認を出す
		// ブックマークは解除する時だけ確認する
		if(! bConfirmed && !bSet ) {
			DlgConfirm.openSimple(
				activity,
				activity.getString(
					R.string.confirm_unfavourite_from,
					AcctColor.getNickname(access_info.acct)
				)
			){
				bookmark(
					activity,
					access_info,
					arg_status,
					nCrossAccountMode,
					callback,
					bSet = bSet,
					bConfirmed = true
				)
			}
			return
		}
		
		//
		App1.getAppState(activity).setBusyBookmark(access_info, arg_status)
		
		//
		TootTaskRunner(activity, TootTaskRunner.PROGRESS_NONE).run(access_info, object : TootTask {
			
			var new_status : TootStatus? = null
			override fun background(client : TootApiClient) : TootApiResult? {
				
				val target_status = if(nCrossAccountMode == CROSS_ACCOUNT_REMOTE_INSTANCE) {
					val (result, status) = client.syncStatus(access_info, arg_status)
					status ?: return result
					if(status.bookmarked) {
						return TootApiResult(activity.getString(R.string.already_bookmarked))
					}
					status
				} else {
					arg_status
				}
				
				return client.request(
					"/api/v1/statuses/${target_status.id}/${if(bSet) "bookmark" else "unbookmark"}",
					"".toFormRequestBody().toPost()
				)?.also { result ->
					new_status = TootParser(activity, access_info).status(result.jsonObject)
				}
			}
			
			override fun handleResult(result : TootApiResult?) {
				
				App1.getAppState(activity).resetBusyBookmark(access_info, arg_status)
				
				val new_status = this.new_status
				when {
					result == null -> {
					} // cancelled.

					new_status != null -> {
						for(column in App1.getAppState(activity).column_list) {
							column.findStatus(access_info.host, new_status.id) { account, status ->
								
								// 同アカウントならブックマーク状態を伝播する
								if(access_info.acct == account.acct) {
									status.bookmarked = new_status.bookmarked
								}
								
								true
							}
						}
						if(callback != null) callback()
					}
					
					else -> showToast(activity, true, result.error)
				}
				
				// 結果に関わらず、更新中状態から復帰させる
				activity.showColumnMatchAccount(access_info)
			}
		})
		
		// ファボ表示を更新中にする
		activity.showColumnMatchAccount(access_info)
	}
	
	
	fun boostFromAnotherAccount(
		activity : ActMain,
		timeline_account : SavedAccount,
		status : TootStatus?
	) {
		status ?: return
		
		val who_host = timeline_account.host
		val status_owner = timeline_account.getFullAcct(status.account)
		
		val isPrivateToot =
			! timeline_account.isMisskey && status.visibility == TootVisibility.PrivateFollowers
		if(isPrivateToot) {
			val list = ArrayList<SavedAccount>()
			for(a in SavedAccount.loadAccountList(activity)) {
				if(a.acct == status_owner) list.add(a)
			}
			if(list.isEmpty()) {
				showToast(activity, false, R.string.boost_private_toot_not_allowed)
				return
			}
			AccountPicker.pick(
				activity,
				bAllowPseudo = false,
				bAuto = false,
				message = activity.getString(R.string.account_picker_boost),
				accountListArg = list
			) { action_account ->
				boost(
					activity,
					action_account,
					status,
					status_owner,
					calcCrossAccountMode(timeline_account, action_account),
					activity.boost_complete_callback
				)
			}
		} else {
			AccountPicker.pick(
				activity,
				bAllowPseudo = false,
				bAuto = false,
				message = activity.getString(R.string.account_picker_boost),
				accountListArg = makeAccountListNonPseudo(activity, who_host)
			) { action_account ->
				boost(
					activity,
					action_account,
					status,
					status_owner,
					calcCrossAccountMode(timeline_account, action_account),
					activity.boost_complete_callback
				)
			}
		}
	}
	
	fun boost(
		activity : ActMain,
		access_info : SavedAccount,
		arg_status : TootStatus,
		status_owner_acct : String,
		nCrossAccountMode : Int,
		callback : EmptyCallback?,
		bSet : Boolean = true,
		bConfirmed : Boolean = false,
		visibility : TootVisibility? = null
	) {
		
		// アカウントからステータスにブースト操作を行っているなら、何もしない
		if(App1.getAppState(activity).isBusyBoost(access_info, arg_status)) {
			showToast(activity, false, R.string.wait_previous_operation)
			return
		}
		
		// 非公開トゥートをブーストできるのは本人だけ
		val isPrivateToot =
			! access_info.isMisskey && arg_status.visibility == TootVisibility.PrivateFollowers
		if(isPrivateToot && access_info.acct != status_owner_acct) {
			showToast(activity, false, R.string.boost_private_toot_not_allowed)
			return
		}
		
		// 必要なら確認を出す
		if(! bConfirmed) {
			DlgConfirm.open(
				activity,
				activity.getString(
					when {
						! bSet -> R.string.confirm_unboost_from
						isPrivateToot -> R.string.confirm_boost_private_from
						visibility == TootVisibility.PrivateFollowers -> R.string.confirm_private_boost_from
						else -> R.string.confirm_boost_from
					},
					AcctColor.getNickname(access_info.acct)
				),
				object : DlgConfirm.Callback {
					override fun onOK() {
						boost(
							activity,
							access_info,
							arg_status,
							status_owner_acct,
							nCrossAccountMode,
							callback,
							bSet = bSet,
							bConfirmed = true,
							visibility = visibility
						)
					}
					
					override var isConfirmEnabled : Boolean
						get() = when(bSet) {
							true -> access_info.confirm_boost
							else -> access_info.confirm_unboost
						}
						set(value) {
							when(bSet) {
								true -> access_info.confirm_boost = value
								else -> access_info.confirm_unboost = value
							}
							access_info.saveSetting()
							activity.reloadAccountSetting(access_info)
						}
				})
			return
		}
		
		App1.getAppState(activity).setBusyBoost(access_info, arg_status)
		
		TootTaskRunner(activity, TootTaskRunner.PROGRESS_NONE).run(access_info, object : TootTask {
			
			var new_status : TootStatus? = null
			override fun background(client : TootApiClient) : TootApiResult? {
				
				val parser = TootParser(activity, access_info)
				
				val target_status = if(nCrossAccountMode == CROSS_ACCOUNT_REMOTE_INSTANCE) {
					val (result, status) = client.syncStatus(access_info, arg_status)
					if(status == null) return result
					if(status.reblogged) {
						return TootApiResult(activity.getString(R.string.already_boosted))
					}
					status
				} else {
					// 既に自タンスのステータスがある
					arg_status
				}
				
				if(access_info.isMisskey) {
					if(! bSet) {
						return TootApiResult("Misskey has no 'unrenote' API.")
					} else {
						
						val params = access_info.putMisskeyApiToken(JSONObject())
							.put("renoteId", target_status.id.toString())
						
						val result =
							client.request("/api/notes/create", params.toPostRequestBuilder())
						val jsonObject = result?.jsonObject
						if(jsonObject != null) {
							val new_status = parser.status(
								jsonObject.optJSONObject("createdNote") ?: jsonObject
							)
							// renoteそのものではなくrenoteされた元noteが欲しい
							this.new_status = new_status?.reblog ?: new_status
						}
						
						return result
					}
					
				} else {
					val b = JSONObject().apply {
						if(visibility != null) put("visibility", visibility.strMastodon)
					}.toPostRequestBuilder()
					
					val result = client.request(
						"/api/v1/statuses/${target_status.id}/${if(bSet) "reblog" else "unreblog"}",
						b
					)
					// reblogはreblogを表すStatusを返す
					// unreblogはreblogしたStatusを返す
					val s = parser.status(result?.jsonObject)
					this.new_status = s?.reblog ?: s
					
					return result
				}
			}
			
			override fun handleResult(result : TootApiResult?) {
				App1.getAppState(activity).resetBusyBoost(access_info, arg_status)
				
				val new_status = this.new_status
				
				when {
					
					// cancelled.
					result == null -> {
					}
					
					new_status != null -> {
						// カウント数は遅延があるみたいなので、恣意的に表示を変更する
						// ブーストカウント数を加工する
						val old_count = arg_status.reblogs_count
						val new_count = new_status.reblogs_count
						if(old_count != null && new_count != null) {
							if(bSet && new_status.reblogged && new_count <= old_count) {
								// 星をつけたのにカウントが上がらないのは違和感あるので、表示をいじる
								new_status.reblogs_count = old_count + 1
							} else if(! bSet && ! new_status.reblogged && new_count >= old_count) {
								// 星を外したのにカウントが下がらないのは違和感あるので、表示をいじる
								// 0未満にはならない
								new_status.reblogs_count = if(old_count < 1) 0 else old_count - 1
							}
							
						}
						
						for(column in App1.getAppState(activity).column_list) {
							column.findStatus(access_info.host, new_status.id) { account, status ->
								
								// 同タンス別アカウントでもカウントは変化する
								status.reblogs_count = new_status.reblogs_count
								
								if(access_info.acct == account.acct) {
									// 同アカウントならreblog状態を変化させる
									status.reblogged = new_status.reblogged
								}
								true
							}
						}
						if(callback != null) callback()
					}
					
					else -> showToast(activity, true, result.error)
				}
				
				// 結果に関わらず、更新中状態から復帰させる
				activity.showColumnMatchAccount(access_info)
				
			}
		})
		
		// ブースト表示を更新中にする
		activity.showColumnMatchAccount(access_info)
	}
	
	fun delete(activity : ActMain, access_info : SavedAccount, status_id : EntityId) {
		
		TootTaskRunner(activity).run(access_info, object : TootTask {
			override fun background(client : TootApiClient) : TootApiResult? {
				return if(access_info.isMisskey) {
					val params = access_info.putMisskeyApiToken()
						.put("noteId", status_id)
					
					client.request("/api/notes/delete", params.toPostRequestBuilder())
					
					// 204 no content
					
				} else {
					val request_builder = Request.Builder().delete()
					
					client.request("/api/v1/statuses/$status_id", request_builder)
					
				}
			}
			
			override fun handleResult(result : TootApiResult?) {
				if(result == null) return  // cancelled.
				
				if(result.jsonObject != null) {
					showToast(activity, false, R.string.delete_succeeded)
					for(column in App1.getAppState(activity).column_list) {
						column.onStatusRemoved(access_info.host, status_id)
					}
				} else {
					showToast(activity, false, result.error)
				}
				
			}
		})
	}
	
	/////////////////////////////////////////////////////////////////////////////////////
	// open conversation
	
	internal fun clearConversationUnread(
		activity : ActMain,
		access_info : SavedAccount,
		conversationSummary : TootConversationSummary?
	) {
		conversationSummary ?: return
		TootTaskRunner(activity, progress_style = TootTaskRunner.PROGRESS_NONE)
			.run(access_info, object : TootTask {
				override fun background(client : TootApiClient) : TootApiResult? {
					return client.request(
						"/api/v1/conversations/${conversationSummary.id}/read",
						"".toFormRequestBody().toPost()
					)
				}
				
				override fun handleResult(result : TootApiResult?) {
					// 何もしない
				}
			})
		
	}
	
	// ローカルかリモートか判断する
	fun conversation(
		activity : ActMain,
		pos : Int,
		access_info : SavedAccount,
		status : TootStatus
	) {
		if(access_info.isNA || ! access_info.host.equals(status.host_access, ignoreCase = true)) {
			conversationOtherInstance(activity, pos, status)
		} else {
			
			conversationLocal(activity, pos, access_info, status.id)
		}
	}
	
	// ローカルから見える会話の流れを表示する
	fun conversationLocal(
		activity : ActMain,
		pos : Int,
		access_info : SavedAccount,
		status_id : EntityId
	) {
		activity.addColumn(pos, access_info, ColumnType.CONVERSATION, status_id)
	}
	
	// リモートかもしれない会話の流れを表示する
	fun conversationOtherInstance(
		activity : ActMain, pos : Int, status : TootStatus?
	) {
		if(status == null) return
		val url = status.url
		
		if(url == null || url.isEmpty()) {
			// URLが不明なトゥートというのはreblogの外側のアレ
			return
		}
		
		when {
			
			// 検索サービスではステータスTLをどのタンスから読んだのか分からない
			status.host_access == null ->
				conversationOtherInstance(
					activity
					, pos
					, url
					, TootStatus.validStatusId(status.id)
						?: TootStatus.findStatusIdFromUri(
							status.uri,
							status.url
						)
				)
			
			// TLアカウントのホストとトゥートのアカウントのホストが同じ
			status.host_original == status.host_access ->
				conversationOtherInstance(
					activity
					, pos
					, url
					, TootStatus.validStatusId(status.id)
						?: TootStatus.findStatusIdFromUri(
							status.uri,
							status.url
						)
				)
			
			else -> {
				// トゥートを取得したタンスと投稿元タンスが異なる場合
				// status.id はトゥートを取得したタンスでのIDである
				// 投稿元タンスでのIDはuriやURLから調べる
				// pleromaではIDがuuidなので失敗する(その時はURLを検索してIDを見つける)
				conversationOtherInstance(
					activity
					, pos
					, url
					, TootStatus.findStatusIdFromUri(
						status.uri,
						status.url
					)
					, status.host_access
					, TootStatus.validStatusId(status.id)
				)
			}
		}
	}
	
	// アプリ外部からURLを渡された場合に呼ばれる
	fun conversationOtherInstance(
		activity : ActMain,
		pos : Int,
		url : String,
		status_id_original : EntityId? = null,
		host_access : String? = null,
		status_id_access : EntityId? = null
	) {
		
		val dialog = ActionsDialog()
		
		val host_original = url.toUri().authority ?: ""
		
		// 選択肢：ブラウザで表示する
		dialog.addAction(
			activity.getString(
				R.string.open_web_on_host,
				host_original
			)
		) { App1.openCustomTab(activity, url) }
		
		// トゥートの投稿元タンスにあるアカウント
		val local_account_list = ArrayList<SavedAccount>()
		
		// TLを読んだタンスにあるアカウント
		val access_account_list = ArrayList<SavedAccount>()
		
		// その他のタンスにあるアカウント
		val other_account_list = ArrayList<SavedAccount>()
		
		for(a in SavedAccount.loadAccountList(activity)) {
			
			// 疑似アカウントは後でまとめて処理する
			if(a.isPseudo) continue
			
			if(status_id_original != null && a.host.equals(host_original, ignoreCase = true)) {
				// アクセス情報＋ステータスID でアクセスできるなら
				// 同タンスのアカウントならステータスIDの変換なしに表示できる
				local_account_list.add(a)
			} else if(status_id_access != null && a.host.equals(host_access, ignoreCase = true)) {
				// 既に変換済みのステータスIDがあるなら、そのアカウントでもステータスIDの変換は必要ない
				access_account_list.add(a)
			} else {
				// 別タンスでも実アカウントなら検索APIでステータスIDを変換できる
				other_account_list.add(a)
			}
		}
		
		// 同タンスのアカウントがないなら、疑似アカウントで開く選択肢
		if(local_account_list.isEmpty()) {
			if(status_id_original != null) {
				dialog.addAction(
					activity.getString(R.string.open_in_pseudo_account, "?@$host_original")
				) {
					addPseudoAccount(activity, host_original) { sa ->
						conversationLocal(activity, pos, sa, status_id_original)
					}
				}
			} else {
				dialog.addAction(
					activity.getString(R.string.open_in_pseudo_account, "?@$host_original")
				) {
					addPseudoAccount(activity, host_original) { sa ->
						conversationRemote(activity, pos, sa, url)
					}
				}
			}
		}
		
		// ローカルアカウント
		if(status_id_original != null) {
			SavedAccount.sort(local_account_list)
			for(a in local_account_list) {
				dialog.addAction(
					AcctColor.getStringWithNickname(
						activity,
						R.string.open_in_account,
						a.acct
					)
				) { conversationLocal(activity, pos, a, status_id_original) }
			}
		}
		
		// アクセスしたアカウント
		if(status_id_access != null) {
			SavedAccount.sort(access_account_list)
			for(a in access_account_list) {
				dialog.addAction(
					AcctColor.getStringWithNickname(
						activity,
						R.string.open_in_account,
						a.acct
					)
				) { conversationLocal(activity, pos, a, status_id_access) }
			}
		}
		
		// その他の実アカウント
		SavedAccount.sort(other_account_list)
		for(a in other_account_list) {
			dialog.addAction(
				AcctColor.getStringWithNickname(
					activity,
					R.string.open_in_account,
					a.acct
				)
			) { conversationRemote(activity, pos, a, url) }
		}
		
		dialog.show(activity, activity.getString(R.string.open_status_from))
	}
	
	private fun conversationRemote(
		activity : ActMain, pos : Int, access_info : SavedAccount, remote_status_url : String
	) {
		TootTaskRunner(activity)
			.progressPrefix(activity.getString(R.string.progress_synchronize_toot))
			.run(access_info, object : TootTask {
				
				var local_status_id : EntityId? = null
				override fun background(client : TootApiClient) : TootApiResult? =
					if(access_info.isPseudo) {
						// 疑似アカウントではURLからIDを取得するのにHTMLと正規表現を使う
						val result = client.getHttp(remote_status_url)
						val string = result?.string
						if(string != null) {
							try {
								val m = reDetailedStatusTime.matcher(string)
								if(m.find()) {
									local_status_id = EntityId(m.groupEx(1) !!)
								}
							} catch(ex : Throwable) {
								log.e(ex, "openStatusRemote: can't parse status id from HTML data.")
							}
							
							if(result.error == null && local_status_id == null) {
								result.setError(activity.getString(R.string.status_id_conversion_failed))
							}
						}
						result
					} else {
						val (result, status) = client.syncStatus(access_info, remote_status_url)
						if(status != null) {
							local_status_id = status.id
							log.d("status id conversion %s => %s", remote_status_url, status.id)
						}
						result
					}
				
				override fun handleResult(result : TootApiResult?) {
					if(result == null) return // cancelled.
					
					val local_status_id = this.local_status_id
					if(local_status_id != null) {
						conversationLocal(activity, pos, access_info, local_status_id)
					} else {
						showToast(activity, true, result.error)
					}
				}
			})
	}
	
	// tootsearch APIは投稿の返信元を示すreplyの情報がない。
	// in_reply_to_idを参照するしかない
	// ところがtootsearchでは投稿をどのタンスから読んだか分からないので、IDは全面的に信用できない。
	// 疑似ではないアカウントを選んだ後に表示中の投稿を検索APIで調べて、そのリプライのIDを取得しなおす
	fun showReplyTootsearch(
		activity : ActMain,
		pos : Int,
		statusArg : TootStatus?
	) {
		statusArg ?: return
		
		// step2: 選択したアカウントで投稿を検索して返信元の投稿のIDを調べる
		fun step2(a : SavedAccount) = TootTaskRunner(activity).run(a, object : TootTask {
			var tmp : TootStatus? = null
			override fun background(client : TootApiClient) : TootApiResult? {
				val (result, status) = client.syncStatus(a, statusArg)
				this.tmp = status
				return result
			}
			
			override fun handleResult(result : TootApiResult?) {
				result ?: return
				val status = tmp
				val replyId = status?.in_reply_to_id
				when {
					status == null -> showToast(activity, true, result.error ?: "?")
					replyId == null -> showToast(
						activity,true,
						"showReplyTootsearch: in_reply_to_id is null"
					)
					else -> conversationLocal(activity, pos, a, replyId)
				}
			}
		})
		
		// step 1: choose account
		
		val host = statusArg.account.host
		val local_account_list = ArrayList<SavedAccount>()
		val other_account_list = ArrayList<SavedAccount>()
		
		for(a in SavedAccount.loadAccountList(activity)) {
			
			// 検索APIはログイン必須なので疑似アカウントは使えない
			if(a.isPseudo) continue
			
			if(a.host.equals(host, ignoreCase = true)) {
				local_account_list.add(a)
			} else {
				other_account_list.add(a)
			}
		}
		
		val dialog = ActionsDialog()
		
		SavedAccount.sort(local_account_list)
		for(a in local_account_list) {
			dialog.addAction(
				AcctColor.getStringWithNickname(
					activity,
					R.string.open_in_account,
					a.acct
				)
			) { step2(a) }
		}
		
		SavedAccount.sort(other_account_list)
		for(a in other_account_list) {
			dialog.addAction(
				AcctColor.getStringWithNickname(
					activity,
					R.string.open_in_account,
					a.acct
				)
			) { step2(a) }
		}
		
		dialog.show(activity, activity.getString(R.string.open_status_from))
	}
	
	////////////////////////////////////////
	// profile pin
	
	fun pin(
		activity : ActMain, access_info : SavedAccount, status : TootStatus, bSet : Boolean
	) {
		
		TootTaskRunner(activity)
			.progressPrefix(activity.getString(R.string.profile_pin_progress))
			
			.run(access_info, object : TootTask {
				
				var new_status : TootStatus? = null
				override fun background(client : TootApiClient) : TootApiResult? {
					
					val result = client.request(
						"/api/v1/statuses/${status.id}/${if(bSet) "pin" else "unpin"}",
						"".toFormRequestBody().toPost()
					)
					
					new_status = TootParser(activity, access_info).status(result?.jsonObject)
					
					return result
				}
				
				override fun handleResult(result : TootApiResult?) {
					
					val new_status = this.new_status
					
					when {
						result == null -> {
							// cancelled.
						}
						
						new_status != null -> {
							for(column in App1.getAppState(activity).column_list) {
								if(access_info.acct == column.access_info.acct) {
									column.findStatus(
										access_info.host,
										new_status.id
									) { _, status ->
										status.pinned = bSet
										true
									}
								}
							}
						}
						
						else -> showToast(activity, true, result.error)
					}
					
					// 結果に関わらず、更新中状態から復帰させる
					activity.showColumnMatchAccount(access_info)
					
				}
			})
		
	}
	
	/////////////////////////////////////////////////////////////////////////////////
	// reply
	
	fun reply(
		activity : ActMain,
		access_info : SavedAccount,
		status : TootStatus,
		quotedRenote : Boolean = false
	) {
		ActPost.open(
			activity,
			ActMain.REQUEST_CODE_POST,
			access_info.db_id,
			reply_status = status,
			quotedRenote = quotedRenote
		)
	}
	
	private fun replyRemote(
		activity : ActMain,
		access_info : SavedAccount,
		remote_status_url : String?,
		quotedRenote : Boolean = false
	) {
		if(remote_status_url == null || remote_status_url.isEmpty()) return
		
		TootTaskRunner(activity)
			.progressPrefix(activity.getString(R.string.progress_synchronize_toot))
			
			.run(access_info, object : TootTask {
				
				var local_status : TootStatus? = null
				override fun background(client : TootApiClient) : TootApiResult? {
					val (result, status) = client.syncStatus(access_info, remote_status_url)
					local_status = status
					return result
				}
				
				override fun handleResult(result : TootApiResult?) {
					
					result ?: return // cancelled.
					
					val ls = local_status
					if(ls != null) {
						reply(activity, access_info, ls, quotedRenote = quotedRenote)
					} else {
						showToast(activity, true, result.error)
					}
				}
			})
	}
	
	fun replyFromAnotherAccount(
		activity : ActMain,
		timeline_account : SavedAccount,
		status : TootStatus?,
		quotedRenote : Boolean = false
	) {
		status ?: return
		val who_host = timeline_account.host
		
		val accountCallback : SavedAccountCallback = { ai ->
			if(ai.host.equals(status.host_access, ignoreCase = true)) {
				// アクセス元ホストが同じならステータスIDを使って返信できる
				reply(activity, ai, status, quotedRenote = quotedRenote)
			} else {
				// それ以外の場合、ステータスのURLを検索APIに投げることで返信できる
				replyRemote(activity, ai, status.url, quotedRenote = quotedRenote)
			}
		}
		
		if(quotedRenote) {
			AccountPicker.pick(
				activity,
				bAllowPseudo = false,
				bAllowMisskey = true,
				bAllowMastodon = false,
				bAuto = true,
				message = activity.getString(R.string.account_picker_quoted_renote),
				callback = accountCallback
			)
		} else {
			AccountPicker.pick(
				activity,
				bAllowPseudo = false,
				bAuto = false,
				message = activity.getString(R.string.account_picker_reply),
				accountListArg = makeAccountListNonPseudo(activity, who_host),
				callback = accountCallback
			)
		}
	}
	
	// 投稿画面を開く。初期テキストを指定する
	fun redraft(
		activity : ActMain,
		accessInfo : SavedAccount,
		status : TootStatus
	) {
		activity.post_helper.closeAcctPopup()
		
		if(accessInfo.isMisskey) {
			ActPost.open(
				activity,
				ActMain.REQUEST_CODE_POST,
				accessInfo.db_id,
				redraft_status = status,
				reply_status = status.reply
			)
			return
		}
		
		if(status.in_reply_to_id == null) {
			ActPost.open(
				activity,
				ActMain.REQUEST_CODE_POST,
				accessInfo.db_id,
				redraft_status = status
			)
			return
		}
		
		TootTaskRunner(activity).run(accessInfo, object : TootTask {
			
			var reply_status : TootStatus? = null
			override fun background(client : TootApiClient) : TootApiResult? {
				val result = client.request("/api/v1/statuses/${status.in_reply_to_id}")
				reply_status = TootParser(activity, accessInfo).status(result?.jsonObject)
				return result
			}
			
			override fun handleResult(result : TootApiResult?) {
				if(result == null) return  // cancelled.
				
				val reply_status = this.reply_status
				if(reply_status != null) {
					ActPost.open(
						activity,
						ActMain.REQUEST_CODE_POST,
						accessInfo.db_id,
						redraft_status = status,
						reply_status = reply_status
					)
					return
				}
				val error = result.error ?: "(no information)"
				showToast(activity, true, activity.getString(R.string.cant_sync_toot) + " : $error")
			}
		})
	}
	////////////////////////////////////////
	
	fun muteConversation(
		activity : ActMain, access_info : SavedAccount, status : TootStatus
	) {
		// toggle change
		val bMute = ! status.muted
		
		TootTaskRunner(activity).run(access_info, object : TootTask {
			
			var local_status : TootStatus? = null
			
			override fun background(client : TootApiClient) : TootApiResult? {
				
				val result = client.request(
					"/api/v1/statuses/${status.id}/${if(bMute) "mute" else "unmute"}",
					"".toFormRequestBody().toPost()
				)
				
				local_status = TootParser(activity, access_info).status(result?.jsonObject)
				
				return result
			}
			
			override fun handleResult(result : TootApiResult?) {
				result ?: return // cancelled.
				
				val ls = local_status
				if(ls != null) {
					for(column in App1.getAppState(activity).column_list) {
						if(access_info.acct == column.access_info.acct) {
							column.findStatus(access_info.host, ls.id) { _, status ->
								status.muted = bMute
								true
							}
						}
					}
					showToast(
						activity,
						true,
						if(bMute) R.string.mute_succeeded else R.string.unmute_succeeded
					)
				} else {
					showToast(activity, true, result.error)
				}
			}
		})
	}
	
	fun reaction(
		activity : ActMain,
		access_info : SavedAccount,
		arg_status : TootStatus,
		status_owner_acct : String,
		nCrossAccountMode : Int,
		callback : EmptyCallback?,
		bSet : Boolean = true,
		code : String? = null
	) {
		if(access_info.isPseudo || ! access_info.isMisskey) return
		
		// 自分の投稿にはリアクション出来ない
		if(access_info.acct == status_owner_acct) {
			showToast(activity, false, R.string.it_is_you)
			return
		}
		
		if(bSet && code == null) {
			val ad = ActionsDialog()
			for(mr in MisskeyReaction.values()) {
				
				val newCode = mr.shortcode
				
				val sb = SpannableStringBuilder()
					.appendMisskeyReaction(activity, mr.emojiUtf16, " ")
					.append(' ')
					.append(mr.shortcode)
				
				ad.addAction(sb) {
					reaction(
						activity,
						access_info,
						arg_status,
						status_owner_acct,
						nCrossAccountMode,
						callback,
						bSet,
						newCode
					)
				}
			}
			ad.show(activity)
			return
		}
		
		
		TootTaskRunner(activity, TootTaskRunner.PROGRESS_NONE).run(access_info, object : TootTask {
			
			override fun background(client : TootApiClient) : TootApiResult? {
				
				val target_status = if(nCrossAccountMode == CROSS_ACCOUNT_REMOTE_INSTANCE) {
					
					val (result, status) = client.syncStatus(access_info, arg_status)
					
					status ?: return result
					
					if(status.myReaction != null) {
						return TootApiResult(activity.getString(R.string.already_reactioned))
					}
					
					status
					
				} else {
					// 既に自タンスのステータスがある
					arg_status
				}
				
				
				return if(! bSet) {
					client.request(
						"/api/notes/reactions/delete",
						access_info.putMisskeyApiToken()
							.put("noteId", target_status.id.toString())
							.toPostRequestBuilder()
					)
					// 成功すると204 no content
				} else {
					client.request(
						"/api/notes/reactions/create",
						access_info.putMisskeyApiToken()
							.put("noteId", target_status.id.toString())
							.put("reaction", code)
							.toPostRequestBuilder()
					)
					// 成功すると204 no content
				}
			}
			
			override fun handleResult(result : TootApiResult?) {
				
				result ?: return
				
				val error = result.error
				if(error != null) {
					showToast(activity, false, error)
					return
				}
				
				if(callback != null) callback()
			}
		})
	}
	
	fun reactionFromAnotherAccount(
		activity : ActMain,
		timeline_account : SavedAccount,
		status : TootStatus?,
		code : String? = null
	) {
		status ?: return
		
		val status_owner = timeline_account.getFullAcct(status.account)
		
		AccountPicker.pick(
			activity,
			bAllowPseudo = false,
			bAllowMisskey = true,
			bAllowMastodon = false,
			bAuto = false,
			message = activity.getString(R.string.account_picker_reaction)
		) { action_account ->
			reaction(
				activity,
				action_account,
				status,
				status_owner,
				calcCrossAccountMode(timeline_account, action_account),
				activity.reaction_complete_callback,
				code = code
			)
		}
	}
	
	fun deleteScheduledPost(
		activity : ActMain,
		access_info : SavedAccount,
		item : TootScheduled,
		bConfirmed : Boolean = false,
		callback : () -> Unit
	) {
		if(! bConfirmed) {
			DlgConfirm.openSimple(
				activity,
				activity.getString(R.string.scheduled_status_delete_confirm)
			) {
				deleteScheduledPost(
					activity,
					access_info,
					item,
					bConfirmed = true,
					callback = callback
				)
			}
			return
		}
		
		TootTaskRunner(activity).run(access_info, object : TootTask {
			override fun background(client : TootApiClient) : TootApiResult? {
				
				return client.request(
					"/api/v1/scheduled_statuses/${item.id}",
					Request.Builder().delete()
				)
			}
			
			override fun handleResult(result : TootApiResult?) {
				
				result ?: return
				
				val error = result.error
				if(error != null) {
					showToast(activity, false, error)
					return
				}
				
				callback()
			}
		})
	}
	
	fun editScheduledPost(
		activity : ActMain,
		access_info : SavedAccount,
		item : TootScheduled
	) {
		TootTaskRunner(activity).run(access_info, object : TootTask {
			
			var reply_status : TootStatus? = null
			override fun background(client : TootApiClient) : TootApiResult? {
				val reply_status_id = item.in_reply_to_id
					?: return TootApiResult()
				
				return client.request("/api/v1/statuses/$reply_status_id")?.also { result ->
					reply_status = TootParser(activity, access_info).status(result.jsonObject)
				}
			}
			
			override fun handleResult(result : TootApiResult?) {
				result ?: return
				
				val error = result.error
				if(error != null) {
					showToast(activity, false, error)
					return
				}
				
				ActPost.open(
					activity,
					ActMain.REQUEST_CODE_POST,
					access_info.db_id,
					scheduledStatus = item,
					reply_status = reply_status
				)
				
			}
		})
	}
	
}
