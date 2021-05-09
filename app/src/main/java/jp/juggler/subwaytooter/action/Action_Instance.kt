package jp.juggler.subwaytooter.action

import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.AccountPicker
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.matchHost
import jp.juggler.subwaytooter.util.openBrowser
import jp.juggler.util.*
import java.util.*

object Action_Instance {
	
	// profile directory を開く
	private fun profileDirectory(
		activity : ActMain,
		accessInfo : SavedAccount,
		host : Host,
		instance : TootInstance? = null,
		pos : Int = activity.defaultInsertPosition
	) {
		when {
			// インスタンスのバージョン情報がなければ取得してやり直し
			instance == null -> TootTaskRunner(activity).run(host, object : TootTask {
				var targetInstance : TootInstance? = null
				override suspend fun background(client : TootApiClient) : TootApiResult? {
					val (ti, ri) = TootInstance.getEx(client, host, allowPixelfed = true)
					targetInstance = ti
					return ri
				}
				
				override suspend fun handleResult(result : TootApiResult?) {
					result ?: return // cancelled.
					when(val ti = targetInstance) {
						null -> activity.showToast(true, result.error)
						else -> profileDirectory(activity, accessInfo, host, ti, pos)
					}
				}
			})
			
			// Misskey非対応
			instance.instanceType == InstanceType.Misskey ->
				activity.showToast(false, R.string.profile_directory_not_supported_on_misskey)
			
			// バージョンが足りないならWebページを開く
			! instance.versionGE(TootInstance.VERSION_3_0_0_rc1) ->
				activity.openBrowser("https://${host.ascii}/explore")
			
			// ホスト名部分が一致するならそのアカウントで開く
			accessInfo.matchHost(host) ->
				activity.addColumn(
					false,
					pos,
					accessInfo,
					ColumnType.PROFILE_DIRECTORY,
					host
				)
			
			// 疑似アカウントで開く
			else -> addPseudoAccount(activity, host, instanceInfo = instance) { ai ->
				activity.addColumn(
					false,
					pos,
					ai,
					ColumnType.PROFILE_DIRECTORY,
					host
				)
			}
		}
	}
	
	// サイドメニューからprofile directory を開く
	fun profileDirectoryFromSideMenu(activity : ActMain) {
		AccountPicker.pick(
			activity,
			bAllowPseudo = true,
			bAllowMisskey = false,
			bAllowMastodon = true,
			bAuto = true,
			message = activity.getString(
				R.string.account_picker_add_timeline_of,
				ColumnType.PROFILE_DIRECTORY.name1(activity)
			)
		) { ai ->
			profileDirectory(activity, ai, ai.apiHost)
		}
	}
	
	// インスタンス情報カラムやコンテキストメニューからprofile directoryを開く
	fun profileDirectoryFromInstanceInformation(
		activity : ActMain,
		currentColumn : Column,
		host : Host,
		instance : TootInstance? = null
	) {
		profileDirectory(
			activity, currentColumn.access_info, host,
			instance = instance,
			pos = activity.nextPosition(currentColumn)
		)
	}
	
	// インスタンス情報カラムを開く
	fun information(
		activity : ActMain,
		pos : Int,
		host : Host
	) {
		activity.addColumn(
			false,
			pos,
			SavedAccount.na,
			ColumnType.INSTANCE_INFORMATION,
			host
		)
	}
	
	// 指定アカウントで指定タンスのドメインタイムラインを開く
	// https://fedibird.com/@noellabo/103266814160117397
	fun timelineDomain(
		activity : ActMain,
		pos : Int,
		accessInfo : SavedAccount,
		host : Host
	) {
		activity.addColumn(pos, accessInfo, ColumnType.DOMAIN_TIMELINE, host)
	}
	
	// 指定タンスのローカルタイムラインを開く
	fun timelineLocal(
		activity : ActMain,
		pos : Int,
		host : Host
	) {
		// 指定タンスのアカウントを持ってるか？
		val account_list = ArrayList<SavedAccount>()
		for(a in SavedAccount.loadAccountList(activity)) {
			if(a.matchHost(host)) account_list.add(a)
		}
		if(account_list.isEmpty()) {
			// 持ってないなら疑似アカウントを追加する
			addPseudoAccount(activity, host) { ai ->
				activity.addColumn(pos, ai, ColumnType.LOCAL)
			}
		} else {
			// 持ってるならアカウントを選んで開く
			SavedAccount.sort(account_list)
			AccountPicker.pick(
				activity,
				bAllowPseudo = true,
				bAuto = false,
				message = activity.getString(R.string.account_picker_add_timeline_of, host),
				accountListArg = account_list
			) { ai -> activity.addColumn(pos, ai, ColumnType.LOCAL) }
		}
	}
	
	// ドメインブロック
	fun blockDomain(
		activity : ActMain,
		access_info : SavedAccount,
		domain : Host,
		bBlock : Boolean
	) {
		
		if(access_info.matchHost(domain)) {
			activity.showToast(false, R.string.it_is_you)
			return
		}
		
		TootTaskRunner(activity).run(access_info, object : TootTask {
			override suspend fun background(client : TootApiClient) : TootApiResult? {
				return client.request(
					"/api/v1/domain_blocks",
					"domain=${domain.ascii.encodePercent()}"
						.toFormRequestBody()
						.toRequest(if(bBlock) "POST" else "DELETE")
				)
			}
			
			override suspend fun handleResult(result : TootApiResult?) {
				if(result == null) return  // cancelled.
				
				if(result.jsonObject != null) {
					
					for(column in activity.app_state.columnList) {
						column.onDomainBlockChanged(access_info, domain, bBlock)
					}
					
					activity.showToast(
						false,
						if(bBlock) R.string.block_succeeded else R.string.unblock_succeeded
					)
					
				} else {
					activity.showToast(false, result.error)
				}
			}
		})
	}
	
	// 指定タンスのローカルタイムラインを開く
	fun timelinePublicAround2(
		activity : ActMain,
		access_info : SavedAccount,
		pos : Int,
		id : EntityId,
		type : ColumnType
	) {
		activity.addColumn(pos, access_info, type, id)
	}
	
	private fun timelinePublicAround3(
		activity : ActMain,
		access_info : SavedAccount,
		pos : Int,
		status : TootStatus,
		type : ColumnType
	) {
		TootTaskRunner(activity).run(access_info, object : TootTask {
			var localStatus : TootStatus? = null
			override suspend fun background(client : TootApiClient) : TootApiResult? {
				val (result, localStatus) = client.syncStatus(access_info, status)
				this.localStatus = localStatus
				return result
			}
			
			override suspend fun handleResult(result : TootApiResult?) {
				result ?: return
				val localStatus = this.localStatus
				if(localStatus != null) {
					timelinePublicAround2(activity, access_info, pos, localStatus.id, type)
				} else {
					activity.showToast(true, result.error)
				}
			}
		})
	}
	
	// 指定タンスのローカルタイムラインを開く
	fun timelinePublicAround(
		activity : ActMain,
		access_info : SavedAccount,
		pos : Int,
		host : Host?,
		status : TootStatus?,
		type : ColumnType,
		allowPseudo : Boolean = true
	) {
		host?.valid() ?: return
		status ?: return
		
		// 利用可能なアカウントを列挙する
		val account_list1 = ArrayList<SavedAccount>() // 閲覧アカウントとホストが同じ
		val account_list2 = ArrayList<SavedAccount>() // その他実アカウント
		label@ for(a in SavedAccount.loadAccountList(activity)) {
			
			when {
				//
				a.isNA -> continue@label
				
				// Misskeyアカウントはステータスの同期が出来ないので選択させない
				a.isMisskey -> continue@label
				
				// 閲覧アカウントとホスト名が同じならステータスIDの変換が必要ない
				a.matchHost(access_info) -> {
					if(! allowPseudo && a.isPseudo) continue@label
					account_list1.add(a)
				}
				
				// 実アカウントならステータスを同期して同時間帯のTLを見れる
				! a.isPseudo -> {
					account_list2.add(a)
				}
			}
		}
		SavedAccount.sort(account_list1)
		SavedAccount.sort(account_list2)
		account_list1.addAll(account_list2)
		
		if(account_list1.isNotEmpty()) {
			AccountPicker.pick(
				activity,
				bAuto = true,
				message = "select account to read timeline",
				accountListArg = account_list1
			) { ai ->
				if(! ai.isNA && ai.matchHost(access_info)) {
					timelinePublicAround2(activity, ai, pos, status.id, type)
				} else {
					timelinePublicAround3(activity, ai, pos, status, type)
				}
			}
			return
		}
		
		activity.showToast(false, R.string.missing_available_account)
	}
	
}
