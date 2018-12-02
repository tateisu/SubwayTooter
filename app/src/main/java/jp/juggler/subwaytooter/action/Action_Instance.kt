package jp.juggler.subwaytooter.action

import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Column
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.dialog.AccountPicker
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.encodePercent
import jp.juggler.util.showToast
import okhttp3.Request
import okhttp3.RequestBody
import java.util.*

object Action_Instance {
	
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
			Column.TYPE_INSTANCE_INFORMATION,
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
				activity.addColumn(pos, ai, Column.TYPE_LOCAL)
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
			) { ai -> activity.addColumn(pos, ai, Column.TYPE_LOCAL) }
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
					Request.Builder()
						.method(
							if(bBlock) "POST" else "DELETE",
							"domain=${domain.encodePercent()}".toRequestBody()
						)
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
		columnType : Int
	) {
		activity.addColumn(pos, access_info, columnType, id)
	}
	
	private fun timelinePublicAround3(
		activity : ActMain,
		access_info : SavedAccount,
		pos : Int,
		status : TootStatus,
		columnType : Int
	) {
		TootTaskRunner(activity).run(access_info, object : TootTask {
			override fun background(client : TootApiClient) : TootApiResult? {
				return client.syncStatus(access_info, status)
			}
			
			override fun handleResult(result : TootApiResult?) {
				result ?: return
				val localStatus = result.data as? TootStatus
				if(localStatus != null) {
					timelinePublicAround2(activity, access_info, pos, localStatus.id, columnType)
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
		columnType : Int,
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
					timelinePublicAround2(activity, account_list1[0], pos, status.id, columnType)
				} else {
					timelinePublicAround3(activity, ai, pos, status, columnType)
				}
			}
			return
		}
		
		showToast(activity, false, R.string.missing_available_account)
	}
	
}
