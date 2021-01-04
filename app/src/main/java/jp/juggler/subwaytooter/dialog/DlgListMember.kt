package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.*
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.Styler
import jp.juggler.subwaytooter.action.Action_List
import jp.juggler.subwaytooter.action.Action_ListMember
import jp.juggler.subwaytooter.action.makeAccountListNonPseudo
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.NetworkEmojiInvalidator
import jp.juggler.subwaytooter.view.MyListView
import jp.juggler.subwaytooter.view.MyNetworkImageView
import jp.juggler.util.*
import org.jetbrains.anko.textColor
import java.util.*

@SuppressLint("InflateParams")
class DlgListMember(
	private val activity : ActMain,
	who : TootAccount,
	_list_owner : SavedAccount
) : View.OnClickListener {
	
	private val dialog : Dialog
	
	private val btnListOwner : Button
	private val btnCreateList : Button
	
	private val account_list : ArrayList<SavedAccount>
	private val target_user_full_acct : Acct
	
	private var list_owner : SavedAccount? = null
	private var local_who : TootAccount? = null
	
	private val adapter : MyListAdapter
	
	init {
		this.account_list = makeAccountListNonPseudo(activity, null)
		this.target_user_full_acct = _list_owner.getFullAcct(who)
		
		this.list_owner = if(_list_owner.isPseudo) {
			null
		} else {
			_list_owner
		}
		
		val view = activity.layoutInflater.inflate(R.layout.dlg_list_member, null, false)
		
		val ivUser = view.findViewById<MyNetworkImageView>(R.id.ivUser)
		val tvUserName = view.findViewById<TextView>(R.id.tvUserName)
		val tvUserAcct = view.findViewById<TextView>(R.id.tvUserAcct)
		btnListOwner = view.findViewById(R.id.btnListOwner)
		btnCreateList = view.findViewById(R.id.btnCreateList)
		val listView = view.findViewById<MyListView>(R.id.listView)
		
		this.adapter = MyListAdapter()
		listView.adapter = adapter
		
		btnCreateList.setOnClickListener(this)
		btnListOwner.setOnClickListener(this)
		view.findViewById<View>(R.id.btnClose).setOnClickListener(this)
		
		ivUser.setImageUrl(
			App1.pref,
			Styler.calcIconRound(ivUser.layoutParams),
			who.avatar_static,
			who.avatar
		)
		val user_name_invalidator = NetworkEmojiInvalidator(activity.handler, tvUserName)
		val name = who.decodeDisplayName(activity)
		tvUserName.text = name
		user_name_invalidator.register(name)
		tvUserAcct.text = target_user_full_acct.pretty
		
		setListOwner(list_owner)
		
		dialog = Dialog(activity).apply {
			window?.apply {
				setFlags(0, Window.FEATURE_NO_TITLE)
				setLayout(
					WindowManager.LayoutParams.MATCH_PARENT,
					WindowManager.LayoutParams.MATCH_PARENT
				)
			}
			setTitle(R.string.your_lists)
			setContentView(view)
		}
		
	}
	
	fun show() = dialog.apply {
		window?.setLayout(
			WindowManager.LayoutParams.MATCH_PARENT,
			WindowManager.LayoutParams.MATCH_PARENT
		)
		show()
	}
	
	override fun onClick(v : View) {
		when(v.id) {
			
			R.id.btnClose -> dialog.dismissSafe()
			
			R.id.btnListOwner -> {
				AccountPicker.pick(
					activity,
					bAllowPseudo = false,
					bAuto = false,
					accountListArg = account_list
				) { ai ->
					setListOwner(ai)
				}
				
			}
			
			R.id.btnCreateList -> openListCreator()
		}
	}
	
	// リストオーナボタンの文字列を更新する
	// リスト一覧を取得する
	private fun setListOwner(a : SavedAccount?) {
		this.list_owner = a
		if(a == null) {
			btnListOwner.setText(R.string.not_selected)
			btnListOwner.setTextColor(activity.attrColor( android.R.attr.textColorPrimary))
			btnListOwner.setBackgroundResource(R.drawable.btn_bg_transparent_round6dp)
			//
			
		} else {
			val ac = AcctColor.load(a)
			btnListOwner.text = ac.nickname
			
			if(AcctColor.hasColorBackground(ac)) {
				btnListOwner.setBackgroundColor(ac.color_bg)
			} else {
				btnListOwner.setBackgroundResource(R.drawable.btn_bg_transparent_round6dp)
			}
			btnListOwner.textColor = ac.color_fg.notZero()
				?: activity.attrColor( android.R.attr.textColorPrimary)
		}
		
		loadLists()
	}
	
	// リストの一覧とターゲットユーザの登録状況を取得する
	private fun loadLists() {
		val list_owner = this.list_owner
		
		if(list_owner == null) {
			showList(null)
			return
		}
		
		TootTaskRunner(activity).run(list_owner, object : TootTask {
			
			var new_list : ArrayList<TootList>? = null
			
			override suspend fun background(client : TootApiClient) : TootApiResult? {
				
				// 現在の登録状況を知るため、対象ユーザの自タンスでのアカウントIDを取得する
				// ドメインブロックなどの影響で同期できない場合があるが、
				// 一覧そのものは取得できるのでこの段階ではエラーにはしない
				val (r1, ar) = client.syncAccountByAcct(list_owner, target_user_full_acct)
				r1 ?: return null // cancelled.
				val local_who = ar?.get() // may null
				if(local_who == null) activity.showToast( true, r1.error)
				
				this@DlgListMember.local_who = local_who
				
				return if(list_owner.isMisskey) {
					// 今のmisskeyではリスト全スキャンしないとユーザの登録状況が分からない
					client.request(
						"/api/users/lists/list",
						list_owner
							.putMisskeyApiToken()
							.toPostRequestBuilder()
					)?.also { result ->
						this.new_list = parseList(
							::TootList,
							TootParser(activity, list_owner),
							result.jsonArray ?: return@also
						).apply {
							if(local_who != null) {
								forEach { list ->
									list.isRegistered =
										null != list.userIds?.find { it == local_who.id }
								}
							}
						}
					}
				} else {
					
					val set_registered = HashSet<EntityId>()
					
					// メンバーを指定してリスト登録状況を取得
					if(local_who != null) client.request(
						"/api/v1/accounts/${local_who.id}/lists"
					)?.also { result ->
						val jsonArray = result.jsonArray ?: return result
						parseList(
							::TootList,
							TootParser(activity, list_owner),
							jsonArray
						).forEach {
							set_registered.add(it.id)
						}
					}
					
					// リスト一覧を取得
					client.request("/api/v1/lists")?.also { result ->
						this.new_list = parseList(
							::TootList,
							TootParser(activity, list_owner),
							result.jsonArray ?: return@also
						).apply {
							sort()
							forEach {
								it.isRegistered = set_registered.contains(it.id)
							}
						}
					}
				}
			}
			
			override suspend fun handleResult(result : TootApiResult?) {
				showList(new_list)
				
				result ?: return // cancelled.
				
				val error = result.error
				if(error?.isNotEmpty() == true) {
					activity.showToast( true, result.error)
				}
				
			}
		})
		
	}
	
	private fun showList(_list : ArrayList<TootList>?) {
		btnCreateList.isEnabled = _list != null
		adapter.item_list.clear()
		when {
			_list == null -> adapter.item_list.add(ErrorItem(activity.getString(R.string.cant_access_list)))
			_list.isEmpty() -> adapter.item_list.add(ErrorItem(activity.getString(R.string.list_not_created)))
			else -> adapter.item_list.addAll(_list)
		}
		adapter.notifyDataSetChanged()
	}
	
	private fun openListCreator() {
		DlgTextInput.show(
			activity,
			activity.getString(R.string.list_create),
			null,
			callback = object : DlgTextInput.Callback {
				
				override fun onEmptyError() {
					activity.showToast( false, R.string.list_name_empty)
				}
				
				override fun onOK(dialog : Dialog, text : String) {
					val list_owner = this@DlgListMember.list_owner
					
					if(list_owner == null) {
						activity.showToast( false, "list owner is not selected.")
						return
					}
					
					Action_List.create(
						activity,
						list_owner,
						text
					) {
						dialog.dismissSafe()
						loadLists()
					}
				}
				
			})
	}
	
	internal class ErrorItem(val message : String)
	
	private inner class MyListAdapter : BaseAdapter() {

		val item_list = ArrayList<Any>()
		
		override fun getCount() : Int = item_list.size
		
		override fun getItem(position : Int) : Any? =
			if(position >= 0 && position < item_list.size) item_list[position] else null
		
		override fun getItemId(position : Int) : Long =
			0L
		
		override fun getViewTypeCount() : Int = 2
		
		override fun getItemViewType(position : Int) : Int =
			when(getItem(position)) {
				is TootList -> 0
				else -> 1
			}
		
		override fun getView(position : Int, viewOld : View?, parent : ViewGroup) : View =
			when(val o = getItem(position)) {
				is TootList ->
					if(viewOld != null) {
						viewOld.apply { (tag as VH_List).bind(o) }
					} else {
						val view : View = activity.layoutInflater.inflate(
							R.layout.lv_list_member_list,
							parent,
							false
						)
						view.apply { tag = VH_List(this).bind(o) }
					}
				
				is ErrorItem ->
					if(viewOld != null) {
						viewOld.apply { (tag as VH_Error).bind(o) }
					} else {
						val view : View = activity.layoutInflater.inflate(
							R.layout.lv_list_member_error,
							parent,
							false
						)
						view.apply { tag = VH_Error(view).bind(o) }
					}
				
				else -> activity.layoutInflater.inflate(
					R.layout.lv_list_member_error,
					parent,
					false
				)
			}
	}
	
	internal inner class VH_List(view : View) : CompoundButton.OnCheckedChangeListener,
		Action_ListMember.Callback {
		
		private val cbItem : CheckBox
		private var bBusy : Boolean = false
		var item : TootList? = null
		
		init {
			this.cbItem = view.findViewById(R.id.cbItem)
			cbItem.setOnCheckedChangeListener(this)
		}
		
		fun bind(item : TootList) = this.apply {
			bBusy = true
			
			this.item = item
			cbItem.text = item.title
			cbItem.isChecked = item.isRegistered
			
			bBusy = false
		}
		
		override fun onCheckedChanged(view : CompoundButton, isChecked : Boolean) {
			// ユーザ操作以外で変更されたなら何もしない
			if(bBusy) return
			
			val list_owner = this@DlgListMember.list_owner
			if(list_owner == null) {
				activity.showToast( false, "list owner is not selected")
				revokeCheckedChanged(isChecked)
				return
			}
			
			val local_who = this@DlgListMember.local_who
			if(local_who == null) {
				activity.showToast( false, "target user is not synchronized")
				revokeCheckedChanged(isChecked)
				return
			}
			
			// 状態をサーバに伝える
			val item = this.item ?: return
			if(isChecked) {
				Action_ListMember.add(activity, list_owner, item.id, local_who, callback = this)
			} else {
				Action_ListMember.delete(activity, list_owner, item.id, local_who, this)
			}
		}
		
		override fun onListMemberUpdated(willRegistered : Boolean, bSuccess : Boolean) {
			if(! bSuccess) revokeCheckedChanged(willRegistered)
		}
		
		private fun revokeCheckedChanged(willRegistered : Boolean) {
			item?.isRegistered = ! willRegistered
			adapter.notifyDataSetChanged()
		}
	}
	
	internal inner class VH_Error(view : View) {
		private val tvError : TextView = view.findViewById(R.id.tvError)
		
		fun bind(o : ErrorItem) = this.apply {
			tvError.text = o.message
		}
	}
}
