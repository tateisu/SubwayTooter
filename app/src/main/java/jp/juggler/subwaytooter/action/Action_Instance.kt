package jp.juggler.subwaytooter.action

import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.dialog.AccountPicker
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.*
import java.util.*

object Action_Instance {
	
	// profile directory を開く
	private fun profileDirectory(
		activity : ActMain,
		accessInfo : SavedAccount,
		host : String,
		instance : TootInstance? = null,
		pos : Int = activity.defaultInsertPosition
	) {
		when {
			// インスタンスのバージョン情報がなければ取得してやり直し
			instance == null -> TootTaskRunner(activity).run(host, object : TootTask {
				var targetInstance : TootInstance? = null
				override fun background(client : TootApiClient) : TootApiResult? {
					val (ri, ti) = client.parseInstanceInformation(client.getInstanceInformation())
					targetInstance = ti
					return ri
				}
				
				override fun handleResult(result : TootApiResult?) {
					result ?: return // cancelled.
					when(val ti = targetInstance) {
						null -> showToast(activity, true, result.error)
						else -> profileDirectory(activity, accessInfo, host, ti, pos)
					}
				}
			})
			
			// Misskey非対応
			instance.instanceType == TootInstance.InstanceType.Misskey ->
				showToast(activity, false, R.string.profile_directory_not_supported_on_misskey)
			
			// バージョンが足りないならWebページを開く
			! instance.versionGE(TootInstance.VERSION_3_0_0_rc1) ->
				App1.openBrowser(activity, "https://$host/explore")
			
			// ホスト名部分が一致するならそのアカウントで開く
			accessInfo.host == host ->
				activity.addColumn(
					false,
					pos,
					accessInfo,
					ColumnType.PROFILE_DIRECTORY,
					host
				)
			
			// 疑似アカウントで開く
			else -> addPseudoAccount(activity, host, misskeyVersion = 0) { ai ->
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
			profileDirectory(activity, ai, ai.host)
		}
	}
	
	// インスタンス情報カラムやコンテキストメニューからprofile directoryを開く
	fun profileDirectoryFromInstanceInformation(
		activity : ActMain,
		currentColumn : Column,
		host : String,
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
		host : String
	) {
		activity.addColumn(
			false,
			pos,
			SavedAccount.na,
			ColumnType.INSTANCE_INFORMATION,
			host
		)
	}
	
	// 指定タンスのローカルタイムラインを開く
	fun timelineLocal(
		activity : ActMain, pos : Int, host : String
	) {
		// 指定タンスのアカウントを持ってるか？
		val account_list = ArrayList<SavedAccount>()
		for(a in SavedAccount.loadAccountList(activity)) {
			if(host.equals(a.host, ignoreCase = true)) account_list.add(a)
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
		activity : ActMain, access_info : SavedAccount, domain : String, bBlock : Boolean
	) {
		
		if(access_info.host.equals(domain, ignoreCase = true)) {
			showToast(activity, false, R.string.it_is_you)
			return
		}
		
		TootTaskRunner(activity).run(access_info, object : TootTask {
			override fun background(client : TootApiClient) : TootApiResult? {
				return client.request(
					"/api/v1/domain_blocks",
					"domain=${domain.encodePercent()}"
						.toFormRequestBody()
						.toRequest(if(bBlock) "POST" else "DELETE")
				)
			}
			
			override fun handleResult(result : TootApiResult?) {
				if(result == null) return  // cancelled.
				
				if(result.jsonObject != null) {
					
					for(column in App1.getAppState(activity).column_list) {
						column.onDomainBlockChanged(access_info, domain, bBlock)
					}
					
					showToast(
						activity,
						false,
						if(bBlock) R.string.block_succeeded else R.string.unblock_succeeded
					)
					
				} else {
					showToast(activity, false, result.error)
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
			override fun background(client : TootApiClient) : TootApiResult? {
				val (result, localStatus) = client.syncStatus(access_info, status)
				this.localStatus = localStatus
				return result
			}
			
			override fun handleResult(result : TootApiResult?) {
				result ?: return
				val localStatus = this.localStatus
				if(localStatus != null) {
					timelinePublicAround2(activity, access_info, pos, localStatus.id, type)
				} else {
					showToast(activity, true, result.error)
				}
			}
		})
	}
	
	// 指定タンスのローカルタイムラインを開く
	fun timelinePublicAround(
		activity : ActMain,
		access_info : SavedAccount,
		pos : Int,
		host : String?,
		status : TootStatus?,
		type : ColumnType,
		allowPseudo : Boolean = true
	) {
		if(host?.isEmpty() != false || host == "?") return
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
				a.host.equals(access_info.host, ignoreCase = true) -> {
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
				if(! ai.isNA && ai.host.equals(access_info.host, ignoreCase = true)) {
					timelinePublicAround2(activity, account_list1[0], pos, status.id, type)
				} else {
					timelinePublicAround3(activity, ai, pos, status, type)
				}
			}
			return
		}
		
		showToast(activity, false, R.string.missing_available_account)
	}
	
}
