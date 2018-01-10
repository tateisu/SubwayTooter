package jp.juggler.subwaytooter.action

import android.net.Uri

import java.util.ArrayList

import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Column
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootTask
import jp.juggler.subwaytooter.api.TootTaskRunner
import jp.juggler.subwaytooter.dialog.AccountPicker
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.Utils
import okhttp3.Request
import okhttp3.RequestBody

object Action_Instance {
	
	// インスタンス情報カラムを開く
	fun information(
		activity : ActMain, pos : Int, host : String
	) {
		activity.addColumn(pos, SavedAccount.na, Column.TYPE_INSTANCE_INFORMATION, host)
	}
	
	// 指定タンスのローカルタイムラインを開く
	fun timelineLocal(
		activity : ActMain, host : String
	) {
		// 指定タンスのアカウントを持ってるか？
		val account_list = ArrayList<SavedAccount>()
		for(a in SavedAccount.loadAccountList(activity)) {
			if(host.equals(a.host, ignoreCase = true)) account_list.add(a)
		}
		if(account_list.isEmpty()) {
			// 持ってないなら疑似アカウントを追加する
			val ai = addPseudoAccount(activity, host)
			if(ai != null) {
				activity.addColumn(activity.defaultInsertPosition, ai, Column.TYPE_LOCAL)
			}
		} else {
			// 持ってるならアカウントを選んで開く
			SavedAccount.sort(account_list)
			AccountPicker.pick(activity, true, false, activity.getString(R.string.account_picker_add_timeline_of, host), account_list) { ai -> activity.addColumn(activity.defaultInsertPosition, ai, Column.TYPE_LOCAL) }
		}
	}
	
	// ドメインブロック
	fun blockDomain(
		activity : ActMain, access_info : SavedAccount, domain : String, bBlock : Boolean
	) {
		
		if(access_info.host.equals(domain, ignoreCase = true)) {
			Utils.showToast(activity, false, R.string.it_is_you)
			return
		}
		
		TootTaskRunner(activity).run(access_info, object : TootTask {
			override fun background(client : TootApiClient) : TootApiResult? {
				
				val body = RequestBody.create(
					TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED, "domain=" + Uri.encode(domain)
				)
				
				var request_builder = Request.Builder()
				request_builder = if(bBlock) request_builder.post(body) else request_builder.delete(body)
				
				return client.request("/api/v1/domain_blocks", request_builder)
			}
			
			override fun handleResult(result : TootApiResult?) {
				if(result == null) return  // cancelled.
				
				if(result.jsonObject != null) {
					
					for(column in App1.getAppState(activity).column_list) {
						column.onDomainBlockChanged(access_info, domain, bBlock)
					}
					
					Utils.showToast(activity, false, if(bBlock) R.string.block_succeeded else R.string.unblock_succeeded)
					
				} else {
					Utils.showToast(activity, false, result.error)
				}
			}
		})
	}
	
}
