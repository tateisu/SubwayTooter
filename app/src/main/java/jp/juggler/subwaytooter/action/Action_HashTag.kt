package jp.juggler.subwaytooter.action

import java.util.ArrayList

import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Column
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount

object Action_HashTag {
	
	// ハッシュタグへの操作を選択する
	fun dialog(
		activity : ActMain, pos : Int, url : String, host : String, tag_without_sharp : String, tag_list : ArrayList<String>?
	) {
		val tag_with_sharp = "#" + tag_without_sharp
		
		val d = ActionsDialog()
			.addAction(activity.getString(R.string.open_hashtag_column)) { timelineOtherInstance(activity, pos, url, host, tag_without_sharp) }
			.addAction(activity.getString(R.string.open_in_browser)) { App1.openCustomTab(activity, url) }
			.addAction(activity.getString(R.string.quote_hashtag_of, tag_with_sharp)) { Action_Account.openPost(activity, tag_with_sharp + " ") }
		
		if(tag_list != null && tag_list.size > 1) {
			val sb = StringBuilder()
			for(s in tag_list) {
				if(sb.isNotEmpty()) sb.append(' ')
				sb.append(s)
			}
			val tag_all = sb.toString()
			d.addAction(activity.getString(R.string.quote_all_hashtag_of, tag_all)) { Action_Account.openPost(activity, tag_all + " ") }
		}
		
		d.show(activity, tag_with_sharp)
	}
	
	fun timeline(
		activity : ActMain, pos : Int, access_info : SavedAccount, tag_without_sharp : String
	) {
		activity.addColumn(pos, access_info, Column.TYPE_HASHTAG, tag_without_sharp)
	}
	
	// 他インスタンスのハッシュタグの表示
	private fun timelineOtherInstance(
		activity : ActMain, pos : Int, url : String, host : String, tag_without_sharp : String
	) {
		timelineOtherInstance_sub(activity, pos, url, host, tag_without_sharp)
	}
	
	// 他インスタンスのハッシュタグの表示
	private fun timelineOtherInstance_sub(
		activity : ActMain, pos : Int, url : String, host : String, tag_without_sharp : String
	) {
		
		val dialog = ActionsDialog()
		
		// 各アカウント
		val account_list = SavedAccount.loadAccountList(activity)
		
		// ソートする
		SavedAccount.sort(account_list)
		
		val list_original = ArrayList<SavedAccount>()
		val list_original_pseudo = ArrayList<SavedAccount>()
		val list_other = ArrayList<SavedAccount>()
		for(a in account_list) {
			if(! host.equals(a.host, ignoreCase = true)) {
				list_other.add(a)
			} else if(a.isPseudo) {
				list_original_pseudo.add(a)
			} else {
				list_original.add(a)
			}
		}
		
		// ブラウザで表示する
		dialog.addAction(activity.getString(R.string.open_web_on_host, host)) { App1.openCustomTab(activity, url) }
		
		if(list_original.isEmpty() && list_original_pseudo.isEmpty()) {
			// 疑似アカウントを作成して開く
			dialog.addAction(activity.getString(R.string.open_in_pseudo_account, "?@" + host)) {
				val sa = ActionUtils.addPseudoAccount(activity, host)
				if(sa != null) {
					timeline(activity, pos, sa, tag_without_sharp)
				}
			}
		}
		
		//
		for(a in list_original) {
			
			dialog.addAction(AcctColor.getStringWithNickname(activity, R.string.open_in_account, a.acct)) { timeline(activity, pos, a, tag_without_sharp) }
		}
		//
		for(a in list_original_pseudo) {
			dialog.addAction(AcctColor.getStringWithNickname(activity, R.string.open_in_account, a.acct)) { timeline(activity, pos, a, tag_without_sharp) }
		}
		//
		for(a in list_other) {
			dialog.addAction(AcctColor.getStringWithNickname(activity, R.string.open_in_account, a.acct)) { timeline(activity, pos, a, tag_without_sharp) }
		}
		
		dialog.show(activity, "#" + tag_without_sharp)
	}
	
}
