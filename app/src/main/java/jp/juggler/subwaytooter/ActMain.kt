package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.InputType
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.util.JsonReader
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.google.android.material.navigation.NavigationView
import jp.juggler.subwaytooter.action.*
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.*
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.span.MyClickableSpanClickCallback
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.ChromeTabOpener
import jp.juggler.subwaytooter.util.EmptyCallback
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.subwaytooter.util.PostHelper
import jp.juggler.subwaytooter.view.*
import jp.juggler.util.*
import org.apache.commons.io.IOUtils
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.backgroundDrawable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.util.*
import java.util.regex.Pattern
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.math.min

class ActMain : AppCompatActivity()
	, Column.Callback
	, View.OnClickListener
	, ViewPager.OnPageChangeListener
	, DrawerLayout.DrawerListener {
	
	companion object {
		
		val log = LogCategory("ActMain")
		
		// リザルト
		const val RESULT_APP_DATA_IMPORT = Activity.RESULT_FIRST_USER
		
		// リクエスト
		const val REQUEST_CODE_COLUMN_LIST = 1
		const val REQUEST_CODE_ACCOUNT_SETTING = 2
		const val REQUEST_APP_ABOUT = 3
		const val REQUEST_CODE_NICKNAME = 4
		const val REQUEST_CODE_POST = 5
		const val REQUEST_CODE_COLUMN_COLOR = 6
		const val REQUEST_CODE_APP_SETTING = 7
		const val REQUEST_CODE_TEXT = 8
		
		const val COLUMN_WIDTH_MIN_DP = 300
		
		const val STATE_CURRENT_PAGE = "current_page"
		
		// 外部からインテントを受信した後、アカウント選択中に画面回転したらアカウント選択からやり直す
		internal var sent_intent2 : Intent? = null
		
		internal val reUrlHashTag =
			Pattern.compile("""\Ahttps://([^/]+)/tags/([^?#・\s\-+.,:;/]+)(?:\z|[?#])""")
		
		var boostButtonSize = 1
		var replyIconSize = 1
		var headerIconSize = 1
		var stripIconSize = 1
		var timeline_font : Typeface = Typeface.DEFAULT
		var timeline_font_bold : Typeface = Typeface.DEFAULT_BOLD
	}
	
	//	@Override
	//	protected void attachBaseContext(Context newBase) {
	//		super.attachBaseContext( CalligraphyContextWrapper.wrap(newBase));
	//	}
	
	var density : Float = 0.toFloat()
	var acct_pad_lr : Int = 0
	
	lateinit var pref : SharedPreferences
	lateinit var handler : Handler
	lateinit var app_state : AppState
	
	// onActivityResultで設定されてonResumeで消化される
	// 状態保存の必要なし
	private var posted_acct : String? = null
	private var posted_status_id : EntityId? = null
	private var posted_reply_id : EntityId? = null
	private var posted_redraft_id : EntityId? = null
	
	var timeline_font_size_sp = Float.NaN
	var acct_font_size_sp = Float.NaN
	var notification_tl_font_size_sp = Float.NaN
	var header_text_size_sp = Float.NaN
	
	internal var bStart : Boolean = false
	
	// 画面上のUI操作で生成されて
	// onPause,onPageDestroy 等のタイミングで閉じられる
	// 状態保存の必要なし
	internal var listItemPopup : StatusButtonsPopup? = null
	
	private lateinit var llEmpty : View
	internal lateinit var drawer : DrawerLayout
	private lateinit var llColumnStrip : ColumnStripLinearLayout
	private lateinit var svColumnStrip : HorizontalScrollView
	private lateinit var btnMenu : ImageButton
	private lateinit var btnToot : ImageButton
	private lateinit var vFooterDivider1 : View
	private lateinit var vFooterDivider2 : View
	
	val viewPool = RecyclerView.RecycledViewPool()
	
	var avatarIconSize : Int = 0
	var notificationTlIconSize : Int = 0
	
	private lateinit var llQuickTootBar : View
	private lateinit var etQuickToot : MyEditText
	private lateinit var btnQuickToot : ImageButton
	private lateinit var btnQuickTootMenu : ImageButton
	lateinit var post_helper : PostHelper
	
	private var quickTootVisibility : TootVisibility = TootVisibility.AccountSetting
	
	class PhoneEnv {
		internal lateinit var pager : MyViewPager
		internal lateinit var pager_adapter : ColumnPagerAdapter
	}
	
	class TabletEnv {
		internal lateinit var tablet_pager : RecyclerView
		internal lateinit var tablet_pager_adapter : TabletColumnPagerAdapter
		internal lateinit var tablet_layout_manager : LinearLayoutManager
		internal lateinit var tablet_snap_helper : GravitySnapHelper
		
	}
	
	private val TabletEnv.visibleRange : IntRange
		get() {
			val vs = tablet_layout_manager.findFirstVisibleItemPosition()
			val ve = tablet_layout_manager.findLastVisibleItemPosition()
			return if(vs == RecyclerView.NO_POSITION || ve == RecyclerView.NO_POSITION) {
				IntRange(- 1, - 2) // empty and less than zero
			} else {
				IntRange(vs, min(ve, vs + nScreenColumn - 1))
			}
		}
	
	private var phoneEnv : PhoneEnv? = null
	private var tabletEnv : TabletEnv? = null
	
	// スマホモードとタブレットモードでコードを切り替える
	private inline fun <R> phoneTab(
		codePhone : (PhoneEnv) -> R,
		codeTablet : (TabletEnv) -> R
	) : R {
		
		val pe = phoneEnv
		if(pe != null) return codePhone(pe)
		
		val te = tabletEnv
		if(te != null) return codeTablet(te)
		
		throw RuntimeException("missing phoneEnv or tabletEnv")
	}
	
	// スマホモードならラムダを実行する。タブレットモードならnullを返す
	private inline fun <R> phoneOnly(code : (PhoneEnv) -> R) : R? {
		val pe = phoneEnv
		return if(pe != null) code(pe) else null
	}
	
	// タブレットモードならラムダを実行する。スマホモードならnullを返す
	@Suppress("unused")
	private inline fun <R> tabOnly(code : (TabletEnv) -> R) : R? {
		val te = tabletEnv
		return if(te != null) code(te) else null
	}
	
	///////////////////////////////////////////////////////////////////////////////////////////////
	
	private val link_click_listener : MyClickableSpanClickCallback = { viewClicked, span ->
		
		var view = viewClicked
		var column : Column? = null
		var whoRef : TootAccountRef? = null
		
		while(true) {
			val tag = view.tag
			if(tag is ItemViewHolder) {
				column = tag.column
				whoRef = tag.getAccount()
				
				break
			} else if(tag is ViewHolderItem) {
				column = tag.ivh.column
				whoRef = tag.ivh.getAccount()
				break
			} else if(tag is ViewHolderHeaderBase) {
				column = tag.column
				whoRef = tag.getAccount()
				break
			} else if(tag is TabletColumnViewHolder) {
				column = tag.columnViewHolder.column
				break
			} else {
				val parent = view.parent
				if(parent is View) {
					view = parent
				} else {
					break
				}
			}
		}
		val pos = nextPosition(column)
		val access_info = column?.access_info
		
		var tag_list : ArrayList<String>? = null
		
		try {
			
			val cs = (viewClicked as TextView).text
			if(cs is Spannable) {
				for(s in cs.getSpans(0, cs.length, MyClickableSpan::class.java)) {
					val m = reUrlHashTag.matcher(s.url)
					if(m.find()) {
						val s_tag =
							if(s.text.startsWith("#")) s.text else "#" + m.group(2).decodePercent()
						if(tag_list == null) tag_list = ArrayList()
						tag_list.add(s_tag)
					}
				}
			}
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
		ChromeTabOpener(
			this@ActMain,
			pos,
			span.url,
			accessInfo = access_info,
			tagList = tag_list,
			whoRef = whoRef
		).open()
	}
	
	////////////////////////////////////////////////////////////////////////////
	
	val follow_complete_callback : EmptyCallback = {
		showToast(this@ActMain, false, R.string.follow_succeeded)
	}
	
	val unfollow_complete_callback : EmptyCallback = {
		showToast(this@ActMain, false, R.string.unfollow_succeeded)
	}
	val cancel_follow_request_complete_callback : EmptyCallback = {
		showToast(this@ActMain, false, R.string.follow_request_cancelled)
	}
	val favourite_complete_callback : EmptyCallback = {
		showToast(this@ActMain, false, R.string.favourite_succeeded)
	}
	
	val unfavourite_complete_callback : EmptyCallback = {
		showToast(this@ActMain, false, R.string.unfavourite_succeeded)
	}
	
	val boost_complete_callback : EmptyCallback = {
		showToast(this@ActMain, false, R.string.boost_succeeded)
	}
	
	val unboost_complete_callback : EmptyCallback = {
		showToast(this@ActMain, false, R.string.unboost_succeeded)
	}
	
	val reaction_complete_callback : EmptyCallback = {
		showToast(this@ActMain, false, R.string.reaction_succeeded)
	}
	
	private var nScreenColumn : Int = 0
	private var nColumnWidth : Int = 0 // dividerの幅を含む
	
	// 相対時刻の表記を定期的に更新する
	private val proc_updateRelativeTime = object : Runnable {
		override fun run() {
			handler.removeCallbacks(this)
			if(! bStart) return
			if(Pref.bpRelativeTimestamp(pref)) {
				for(c in app_state.column_list) {
					c.fireRelativeTime()
				}
				handler.postDelayed(this, 10000L)
			}
		}
	}
	
	private var nAutoCwCellWidth = 0
	private var nAutoCwLines = 0
	
	// 簡易投稿入力のテキストを取得
	val quickTootText : String
		get() = etQuickToot.text.toString()
	
	// デフォルトの投稿先アカウントのdb_idを返す
	val currentPostTargetId : Long
		get() = phoneTab(
			{ pe ->
				val c = pe.pager_adapter.getColumn(pe.pager.currentItem)
				if(c != null && ! c.access_info.isPseudo) {
					return c.access_info.db_id
				}
				return - 1L
			}
		) { env ->
			
			val db_id = Pref.lpTabletTootDefaultAccount(App1.pref)
			SavedAccount.loadAccount(this@ActMain, db_id)?.let {
				return it.db_id
			}
			
			val accounts = ArrayList<SavedAccount>()
			for(i in env.visibleRange) {
				try {
					val a = app_state.column_list[i].access_info
					if(a.isPseudo) {
						accounts.clear()
						break
					} else if(null == accounts.find { it.acct == a.acct }) {
						accounts.add(a)
					}
				} catch(ex : Throwable) {
				
				}
			}
			if(accounts.size == 1) {
				return accounts.first().db_id
			}
			return - 1L
		}
	
	// スマホモードなら現在のカラムを、タブレットモードなら-1Lを返す
	// (カラム一覧画面のデフォルト選択位置に使われる)
	val currentColumn : Int
		get() = phoneTab(
			{ it.pager.currentItem },
			{ - 1 }
		)
	
	// 新しいカラムをどこに挿入するか
	fun nextPosition(column : Column?) : Int {
		if(column != null) {
			val pos = app_state.column_list.indexOf(column)
			if(pos != - 1) return pos + 1
		}
		return defaultInsertPosition
	}
	
	// 新しいカラムをどこに挿入するか
	private val defaultInsertPosition : Int
		get() = phoneTab(
			{ it.pager.currentItem + 1 },
			{ Integer.MAX_VALUE }
		)
	
	private fun validateFloat(fv : Float) : Float {
		return if(fv.isNaN()) fv else if(fv < 1f) 1f else fv
	}
	
	override fun onCreate(savedInstanceState : Bundle?) {
		log.d("onCreate")
		super.onCreate(savedInstanceState)
		App1.setActivityTheme(this, true)
		requestWindowFeature(Window.FEATURE_NO_TITLE)
		
		handler = Handler()
		app_state = App1.getAppState(this)
		pref = App1.pref
		
		this.density = app_state.density
		this.acct_pad_lr = (0.5f + 4f * density).toInt()
		
		timeline_font_size_sp = validateFloat(Pref.fpTimelineFontSize(pref))
		acct_font_size_sp = validateFloat(Pref.fpAcctFontSize(pref))
		notification_tl_font_size_sp = validateFloat(Pref.fpNotificationTlFontSize(pref))
		header_text_size_sp = validateFloat(Pref.fpHeaderTextSize(pref))
		
		
		
		initUI()
		
		updateColumnStrip()
		
		if(app_state.column_list.isNotEmpty()) {
			
			// 前回最後に表示していたカラムの位置にスクロールする
			val column_pos = Pref.ipLastColumnPos(pref)
			if(column_pos >= 0 && column_pos < app_state.column_list.size) {
				scrollToColumn(column_pos, false)
			}
			
			// 表示位置に合わせたイベントを発行
			phoneTab(
				{ env -> onPageSelected(env.pager.currentItem) },
				{ env -> resizeColumnWidth(env) }
			)
		}
		
		PollingWorker.queueUpdateNotification(this)
		
		if(savedInstanceState != null) {
			sent_intent2?.let { handleSentIntent(it) }
		}
		
		checkPrivacyPolicy()
	}
	
	override fun onDestroy() {
		log.d("onDestroy")
		super.onDestroy()
		post_helper.onDestroy()
		
		// このアクティビティに関連する ColumnViewHolder への参照を全カラムから除去する
		for(c in app_state.column_list) {
			c.removeColumnViewHolderByActivity(this)
		}
	}
	
	override fun onNewIntent(intent : Intent?) {
		super.onNewIntent(intent)
		log.w("onNewIntent: isResumed = isResumed")
	}
	
	override fun onSaveInstanceState(outState : Bundle?) {
		log.d("onSaveInstanceState")
		super.onSaveInstanceState(outState)
		outState ?: return
		
		phoneTab(
			{ env -> outState.putInt(STATE_CURRENT_PAGE, env.pager.currentItem) },
			{ env ->
				val ve = env.tablet_layout_manager.findLastVisibleItemPosition()
				if(ve != RecyclerView.NO_POSITION) {
					outState.putInt(STATE_CURRENT_PAGE, ve)
				}
			})
		
		for(column in app_state.column_list) {
			column.saveScrollPosition()
		}
		
	}
	
	override fun onRestoreInstanceState(savedInstanceState : Bundle) {
		log.d("onRestoreInstanceState")
		super.onRestoreInstanceState(savedInstanceState)
		val pos = savedInstanceState.getInt(STATE_CURRENT_PAGE)
		if(pos > 0 && pos < app_state.column_list.size) {
			phoneTab(
				{ env -> env.pager.currentItem = pos },
				{ env ->
					env.tablet_layout_manager
						.smoothScrollToPosition(env.tablet_pager, null, pos)
				}
			)
		}
	}
	
	override val isActivityStart : Boolean
		get() = bStart
	
	override fun onStart() {
		super.onStart()
		
		bStart = true
		log.d("onStart")
		
		// カラーカスタマイズを読み直す
		ListDivider.color = Pref.ipListDividerColor(pref)
		TabletColumnDivider.color = Pref.ipListDividerColor(pref)
		ItemViewHolder.toot_color_unlisted = Pref.ipTootColorUnlisted(pref)
		ItemViewHolder.toot_color_follower = Pref.ipTootColorFollower(pref)
		ItemViewHolder.toot_color_direct_user = Pref.ipTootColorDirectUser(pref)
		ItemViewHolder.toot_color_direct_me = Pref.ipTootColorDirectMe(pref)
		MyClickableSpan.showLinkUnderline = Pref.bpShowLinkUnderline(pref)
		MyClickableSpan.defaultLinkColor = Pref.ipLinkColor(pref).notZero()
			?: getAttributeColor(this, R.attr.colorLink)
		CustomShare.reloadCache(this, pref)
		
		// 背景画像を表示しない設定が変更された時にカラムの背景を設定しなおす
		for(column in app_state.column_list) {
			column.fireColumnColor()
		}
		
		var tz = TimeZone.getDefault()
		try {
			val tz_id = Pref.spTimeZone(pref)
			if(tz_id.isNotEmpty()) {
				tz = TimeZone.getTimeZone(tz_id)
			}
		} catch(ex : Throwable) {
			log.e(ex, "getTimeZone failed.")
		}
		TootStatus.date_format.timeZone = tz
		
		// アカウント設定から戻ってきたら、カラムを消す必要があるかもしれない
		run {
			val new_order = ArrayList<Int>()
			for(i in 0 until app_state.column_list.size) {
				val column = app_state.column_list[i]
				
				if(! column.access_info.isNA) {
					// 存在確認
					SavedAccount.loadAccount(this@ActMain, column.access_info.db_id)
						?: continue
				}
				new_order.add(i)
			}
			
			if(new_order.size != app_state.column_list.size) {
				setOrder(new_order)
			}
		}
		
		// 各カラムのアカウント設定を読み直す
		reloadAccountSetting()
		
		// 投稿直後ならカラムの再取得を行う
		refreshAfterPost()
		
		// 画面復帰時に再取得やストリーミング開始を行う
		for(column in app_state.column_list) {
			column.onStart(this)
		}
		
		// カラムの表示範囲インジケータを更新
		updateColumnStripSelection(- 1, - 1f)
		
		for(c in app_state.column_list) {
			c.fireShowContent(reason = "ActMain onStart", reset = true)
		}
		
		// 相対時刻表示
		proc_updateRelativeTime.run()
		
	}
	
	override fun onStop() {
		
		log.d("onStop")
		
		bStart = false
		
		handler.removeCallbacks(proc_updateRelativeTime)
		
		post_helper.closeAcctPopup()
		
		closeListItemPopup()
		
		app_state.stream_reader.stopAll()
		
		for(column in app_state.column_list) {
			column.saveScrollPosition()
		}
		app_state.saveColumnList(bEnableSpeech = false)
		
		super.onStop()
		
	}
	
	private var isResumed = false
	
	override fun onResume() {
		super.onResume()
		log.d("onResume")
		isResumed = true
		
		MyClickableSpan.link_callback = WeakReference(link_click_listener)
		
		if(Pref.bpDontScreenOff(pref)) {
			window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		} else {
			window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		}
		
		// 外部から受け取ったUriの処理
		val uri = ActCallback.last_uri.getAndSet(null)
		if(uri != null) {
			handleIntentUri(uri)
		}
		
		// 外部から受け取ったUriの処理
		val intent = ActCallback.sent_intent.getAndSet(null)
		if(intent != null) {
			handleSentIntent(intent)
		}
		
	}
	
	override fun onPause() {
		log.d("onPause")
		isResumed = false
		
		// 最後に表示していたカラムの位置
		val last_pos = phoneTab(
			{ env -> env.pager.currentItem },
			{ env -> env.tablet_layout_manager.findFirstVisibleItemPosition() })
		
		pref.edit().put(Pref.ipLastColumnPos, last_pos).apply()
		
		for(column in app_state.column_list) {
			column.saveScrollPosition()
		}
		
		app_state.saveColumnList(bEnableSpeech = false)
		
		super.onPause()
	}
	
	private fun refreshAfterPost() {
		val posted_acct = this.posted_acct
		val posted_status_id = this.posted_status_id
		
		if(posted_acct?.isNotEmpty() == true && posted_status_id == null) {
			// 予約投稿なら予約投稿リストをリロードする
			for(column in app_state.column_list) {
				if(column.type == ColumnType.SCHEDULED_STATUS
					&& column.access_info.acct == posted_acct
				) {
					column.startLoading()
				}
			}
			
		} else if(posted_acct?.isNotEmpty() == true && posted_status_id != null) {
			
			val posted_redraft_id = this.posted_redraft_id
			if(posted_redraft_id != null) {
				val delm = posted_acct.indexOf('@')
				if(delm != - 1) {
					val host = posted_acct.substring(delm + 1)
					for(column in app_state.column_list) {
						column.onStatusRemoved(host, posted_redraft_id)
					}
				}
				this.posted_redraft_id = null
			}
			
			val refresh_after_toot = Pref.ipRefreshAfterToot(pref)
			if(refresh_after_toot != Pref.RAT_DONT_REFRESH) {
				for(column in app_state.column_list) {
					if(column.access_info.acct != posted_acct) continue
					column.startRefreshForPost(
						refresh_after_toot,
						posted_status_id,
						posted_reply_id
					)
				}
			}
		}
		this.posted_acct = null
		this.posted_status_id = null
	}
	
	private fun handleSentIntent(intent : Intent) {
		sent_intent2 = intent
		
		// Galaxy S8+ で STのSSを取った後に出るポップアップからそのまま共有でSTを選ぶと何も起きない問題への対策
		handler.post {
			AccountPicker.pick(
				this,
				bAllowPseudo = false,
				bAuto = true,
				message = getString(R.string.account_picker_toot)
				, dismiss_callback = { sent_intent2 = null }
			) { ai ->
				sent_intent2 = null
				ActPost.open(this@ActMain, REQUEST_CODE_POST, ai.db_id, sent_intent = intent)
			}
		}
	}
	
	fun closeListItemPopup() {
		try {
			listItemPopup?.dismiss()
		} catch(ignored : Throwable) {
		}
		listItemPopup = null
	}
	
	override fun onClick(v : View) {
		when(v.id) {
			R.id.btnMenu -> if(! drawer.isDrawerOpen(GravityCompat.START)) {
				drawer.openDrawer(GravityCompat.START)
			}
			
			R.id.btnToot -> Action_Account.openPost(this@ActMain)
			
			R.id.btnQuickToot -> performQuickPost(null)
			
			R.id.btnQuickTootMenu -> performQuickTootMenu()
		}
	}
	
	private val dlgQuickTootMenu = DlgQuickTootMenu(this, object : DlgQuickTootMenu.Callback {
		
		override var visibility : TootVisibility
			get() = quickTootVisibility
			set(value) {
				if(value != quickTootVisibility) {
					quickTootVisibility = value
					pref.edit().put(Pref.spQuickTootVisibility, value.id.toString()).apply()
				}
			}
		
		override fun onMacro(text : String) {
			val editable = etQuickToot.text
			if(editable?.isNotEmpty() == true) {
				val start = etQuickToot.selectionStart
				val end = etQuickToot.selectionEnd
				editable.replace(start, end, text)
				etQuickToot.requestFocus()
				etQuickToot.setSelection(start + text.length)
			} else {
				etQuickToot.setText(text)
				etQuickToot.requestFocus()
				etQuickToot.setSelection(text.length)
			}
		}
	})
	
	private fun performQuickTootMenu() {
		dlgQuickTootMenu.toggle()
	}
	
	private fun performQuickPost(account : SavedAccount?) {
		if(account == null) {
			phoneTab({ env ->
				
				// スマホモードなら表示中のカラムがあればそれで
				val c = try {
					app_state.column_list[env.pager.currentItem]
				} catch(ex : Throwable) {
					null
				}
				
				if(c?.access_info?.isPseudo == false) {
					// 疑似アカウントではない
					performQuickPost(c.access_info)
				} else {
					// アカウント選択してやり直し
					AccountPicker.pick(
						this,
						bAllowPseudo = false,
						bAuto = true,
						message = getString(R.string.account_picker_toot)
					) { ai -> performQuickPost(ai) }
				}
			}, {
				// アカウント選択してやり直し
				AccountPicker.pick(
					this,
					bAllowPseudo = false,
					bAuto = true,
					message = getString(R.string.account_picker_toot)
				) { ai -> performQuickPost(ai) }
			})
			return
		}
		
		post_helper.content = etQuickToot.text.toString().trim { it <= ' ' }
		post_helper.spoiler_text = null
		
		post_helper.visibility = when(quickTootVisibility) {
			TootVisibility.AccountSetting -> account.visibility
			else -> quickTootVisibility
		}
		
		post_helper.bNSFW = false
		post_helper.in_reply_to_id = null
		post_helper.attachment_list = null
		post_helper.emojiMapCustom =
			App1.custom_emoji_lister.getMap(account.host, account.isMisskey)
		
		
		etQuickToot.hideKeyboard()
		
		post_helper.post(account, callback = object : PostHelper.PostCompleteCallback {
			override fun onPostComplete(
				target_account : SavedAccount,
				status : TootStatus
			) {
				etQuickToot.setText("")
				posted_acct = target_account.acct
				posted_status_id = status.id
				posted_reply_id = status.in_reply_to_id
				posted_redraft_id = null
				refreshAfterPost()
			}
			
			override fun onScheduledPostComplete(target_account : SavedAccount) {
			}
		})
	}
	
	override fun onPageScrolled(
		position : Int,
		positionOffset : Float,
		positionOffsetPixels : Int
	) {
		updateColumnStripSelection(position, positionOffset)
	}
	
	override fun onPageSelected(position : Int) {
		handler.post {
			if(position >= 0 && position < app_state.column_list.size) {
				val column = app_state.column_list[position]
				if(! column.bFirstInitialized) {
					column.startLoading()
				}
				scrollColumnStrip(position)
				when {
					column.access_info.isNA -> post_helper.setInstance(null, false)
					else -> post_helper.setInstance(
						column.access_info.host,
						column.access_info.isMisskey
					)
				}
			}
		}
	}
	
	override fun onPageScrollStateChanged(state : Int) {
	}
	
	private fun isOrderChanged(new_order : ArrayList<Int>) : Boolean {
		if(new_order.size != app_state.column_list.size) return true
		var i = 0
		val ie = new_order.size
		while(i < ie) {
			if(new_order[i] != i) return true
			++ i
		}
		return false
	}
	
	override fun onActivityResult(requestCode : Int, resultCode : Int, data : Intent?) {
		log.d("onActivityResult req=$requestCode res=$resultCode data=$data")
		
		if(resultCode == Activity.RESULT_OK) {
			when(requestCode) {
				REQUEST_CODE_COLUMN_LIST -> if(data != null) {
					val order = data.getIntegerArrayListExtra(ActColumnList.EXTRA_ORDER)
					if(order != null && isOrderChanged(order)) {
						setOrder(order)
					}
					
					if(app_state.column_list.isNotEmpty()) {
						val select = data.getIntExtra(ActColumnList.EXTRA_SELECTION, - 1)
						if(0 <= select && select < app_state.column_list.size) {
							scrollToColumn(select)
						}
					}
				}
				
				REQUEST_APP_ABOUT -> if(data != null) {
					val search = data.getStringExtra(ActAbout.EXTRA_SEARCH)
					if(search?.isNotEmpty() == true) {
						Action_Account.timeline(
							this@ActMain,
							defaultInsertPosition,
							ColumnType.SEARCH,
							bAllowPseudo = true,
							args = arrayOf(search, true)
						)
					}
					return
				}
				
				REQUEST_CODE_NICKNAME -> {
					
					updateColumnStrip()
					
					for(column in app_state.column_list) {
						column.fireShowColumnHeader()
					}
					
				}
				
				REQUEST_CODE_POST -> if(data != null) {
					etQuickToot.setText("")
					posted_acct = data.getStringExtra(ActPost.EXTRA_POSTED_ACCT)
					if(data.extras?.containsKey(ActPost.EXTRA_POSTED_STATUS_ID) == true) {
						posted_status_id = EntityId.from(data, ActPost.EXTRA_POSTED_STATUS_ID)
						posted_reply_id = EntityId.from(data, ActPost.EXTRA_POSTED_REPLY_ID)
						posted_redraft_id = EntityId.from(data, ActPost.EXTRA_POSTED_REDRAFT_ID)
					} else {
						posted_status_id = null
					}
				}
				
				REQUEST_CODE_COLUMN_COLOR -> if(data != null) {
					app_state.saveColumnList()
					val idx = data.getIntExtra(ActColumnCustomize.EXTRA_COLUMN_INDEX, 0)
					if(idx >= 0 && idx < app_state.column_list.size) {
						app_state.column_list[idx].fireColumnColor()
						app_state.column_list[idx].fireShowContent(
							reason = "ActMain column color changed",
							reset = true
						)
					}
					updateColumnStrip()
				}
			}
		}
		
		when(requestCode) {
			
			REQUEST_CODE_ACCOUNT_SETTING -> {
				updateColumnStrip()
				
				for(column in app_state.column_list) {
					column.fireShowColumnHeader()
				}
				
				if(resultCode == Activity.RESULT_OK && data != null) {
					startAccessTokenUpdate(data)
				} else if(resultCode == ActAccountSetting.RESULT_INPUT_ACCESS_TOKEN && data != null) {
					val db_id = data.getLongExtra(ActAccountSetting.EXTRA_DB_ID, - 1L)
					checkAccessToken2(db_id)
				}
			}
			
			REQUEST_CODE_APP_SETTING -> {
				Column.reloadDefaultColor(this, pref)
				showFooterColor()
				updateColumnStrip()
				
				if(resultCode == RESULT_APP_DATA_IMPORT) {
					importAppData(data?.data)
				}
			}
			
			REQUEST_CODE_TEXT -> when(resultCode) {
				ActText.RESULT_SEARCH_MSP -> {
					val text = data?.getStringExtra(Intent.EXTRA_TEXT) ?: ""
					addColumn(
						false,
						defaultInsertPosition,
						SavedAccount.na,
						ColumnType.SEARCH_MSP,
						text
					)
				}
				
				ActText.RESULT_SEARCH_TS -> {
					val text = data?.getStringExtra(Intent.EXTRA_TEXT) ?: ""
					addColumn(
						false,
						defaultInsertPosition,
						SavedAccount.na,
						ColumnType.SEARCH_TS,
						text
					)
				}
			}
		}
		
		super.onActivityResult(requestCode, resultCode, data)
	}
	
	override fun onBackPressed() {
		
		// メニューが開いていたら閉じる
		if(drawer.isDrawerOpen(GravityCompat.START)) {
			drawer.closeDrawer(GravityCompat.START)
			return
		}
		
		// カラムが0個ならアプリを終了する
		if(app_state.column_list.isEmpty()) {
			this@ActMain.finish()
			return
		}
		
		// カラム設定が開いているならカラム設定を閉じる
		if(closeColumnSetting()) {
			return
		}
		
		fun getClosableColumnList() : List<Column> {
			val visibleColumnList = ArrayList<Column>()
			phoneTab({ env ->
				try {
					visibleColumnList.add(app_state.column_list[env.pager.currentItem])
				} catch(ex : Throwable) {
				}
			}, { env ->
				for(i in env.visibleRange) {
					try {
						visibleColumnList.add(app_state.column_list[i])
					} catch(ex : Throwable) {
					}
				}
			})
			
			return visibleColumnList.filter { ! it.dont_close }
			
		}
		
		// カラムが1個以上ある場合は設定に合わせて挙動を変える
		when(Pref.ipBackButtonAction(pref)) {
			
			Pref.BACK_EXIT_APP -> this@ActMain.finish()
			
			Pref.BACK_OPEN_COLUMN_LIST -> Action_App.columnList(this@ActMain)
			
			Pref.BACK_CLOSE_COLUMN -> {
				
				val closeableColumnList = getClosableColumnList()
				when(closeableColumnList.size) {
					0 -> {
						if(Pref.bpExitAppWhenCloseProtectedColumn(pref)
							&& Pref.bpDontConfirmBeforeCloseColumn(pref)
						) {
							this@ActMain.finish()
						} else {
							showToast(this@ActMain, false, R.string.missing_closeable_column)
						}
					}
					
					1 -> {
						closeColumn(closeableColumnList.first())
					}
					
					else -> {
						showToast(
							this@ActMain,
							false,
							R.string.cant_close_column_by_back_button_when_multiple_column_shown
						)
					}
				}
			}
			
			// ActAppSetting.BACK_ASK_ALWAYS
			else -> {
				
				val closeableColumnList = getClosableColumnList()
				
				val dialog = ActionsDialog()
				
				
				if(closeableColumnList.size == 1) {
					val column = closeableColumnList.first()
					dialog.addAction(getString(R.string.close_column)) {
						closeColumn(column, bConfirmed = true)
					}
				}
				
				dialog.addAction(getString(R.string.open_column_list)) { Action_App.columnList(this@ActMain) }
				dialog.addAction(getString(R.string.app_exit)) { this@ActMain.finish() }
				dialog.show(this, null)
			}
		}
	}
	
	internal fun initUI() {
		setContentView(R.layout.act_main)
		
		quickTootVisibility =
			TootVisibility.parseSavedVisibility(Pref.spQuickTootVisibility(pref))
				?: quickTootVisibility
		
		Column.reloadDefaultColor(this, pref)
		
		var sv = Pref.spTimelineFont(pref)
		if(sv.isNotEmpty()) {
			try {
				timeline_font = Typeface.createFromFile(sv)
			} catch(ex : Throwable) {
				log.trace(ex)
			}
		}
		
		sv = Pref.spTimelineFontBold(pref)
		if(sv.isNotEmpty()) {
			try {
				timeline_font_bold = Typeface.createFromFile(sv)
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			
		} else {
			try {
				timeline_font_bold = Typeface.create(timeline_font, Typeface.BOLD)
			} catch(ex : Throwable) {
				log.trace(ex)
			}
		}
		
		fun parseIconSize(stringPref : StringPref) : Int {
			var icon_size_dp = stringPref.defVal.toFloat()
			try {
				sv = stringPref(pref)
				val fv = if(sv.isEmpty()) Float.NaN else sv.toFloat()
				if(fv.isFinite() && fv >= 1f) {
					icon_size_dp = fv
				}
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			return (0.5f + icon_size_dp * density).toInt()
		}
		
		avatarIconSize = parseIconSize(Pref.spAvatarIconSize)
		notificationTlIconSize = parseIconSize(Pref.spNotificationTlIconSize)
		boostButtonSize = parseIconSize(Pref.spBoostButtonSize)
		replyIconSize = parseIconSize(Pref.spReplyIconSize)
		headerIconSize = parseIconSize(Pref.spHeaderIconSize)
		stripIconSize = parseIconSize(Pref.spStripIconSize)
		run {
			var round_ratio = 33f
			try {
				if(Pref.bpDontRound(pref)) {
					round_ratio = 0f
				} else {
					sv = Pref.spRoundRatio(pref)
					if(sv.isNotEmpty()) {
						val fv = sv.toFloat()
						if(fv.isFinite()) {
							round_ratio = fv
						}
					}
				}
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			Styler.round_ratio = clipRange(0f, 1f, round_ratio / 100f) * 0.5f
		}
		
		run {
			var boost_alpha = 0.8f
			try {
				val f = (Pref.spBoostAlpha.toInt(pref).toFloat() + 0.5f) / 100f
				boost_alpha = when {
					f >= 1f -> 1f
					f < 0f -> 0.66f
					else -> f
				}
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			Styler.boost_alpha = boost_alpha
		}
		
		llEmpty = findViewById(R.id.llEmpty)
		
		drawer = findViewById(R.id.drawer_layout)
		drawer.addDrawerListener(this)
		
		val navigationView = findViewById<NavigationView>(R.id.nav_view)
		initSideMenu(navigationView)
		
		btnMenu = findViewById(R.id.btnMenu)
		btnToot = findViewById(R.id.btnToot)
		vFooterDivider1 = findViewById(R.id.vFooterDivider1)
		vFooterDivider2 = findViewById(R.id.vFooterDivider2)
		llColumnStrip = findViewById(R.id.llColumnStrip)
		svColumnStrip = findViewById(R.id.svColumnStrip)
		llQuickTootBar = findViewById(R.id.llQuickTootBar)
		etQuickToot = findViewById(R.id.etQuickToot)
		btnQuickToot = findViewById(R.id.btnQuickToot)
		btnQuickTootMenu = findViewById(R.id.btnQuickTootMenu)
		
		if(! Pref.bpQuickTootBar(pref)) {
			llQuickTootBar.visibility = View.GONE
		}
		
		btnToot.setOnClickListener(this)
		btnMenu.setOnClickListener(this)
		btnQuickToot.setOnClickListener(this)
		btnQuickTootMenu.setOnClickListener(this)
		
		if(Pref.bpDontUseActionButtonWithQuickTootBar(pref)) {
			etQuickToot.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
			etQuickToot.imeOptions = EditorInfo.IME_ACTION_NONE
			// 最後に指定する必要がある？
			etQuickToot.maxLines = 5
			etQuickToot.isVerticalScrollBarEnabled = true
			etQuickToot.isScrollbarFadingEnabled = false
		} else {
			etQuickToot.inputType = InputType.TYPE_CLASS_TEXT
			etQuickToot.imeOptions = EditorInfo.IME_ACTION_SEND
			etQuickToot.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, _ ->
				if(actionId == EditorInfo.IME_ACTION_SEND) {
					btnQuickToot.performClick()
					return@OnEditorActionListener true
				}
				false
			})
			// 最後に指定する必要がある？
			etQuickToot.maxLines = 1
		}
		
		svColumnStrip.isHorizontalFadingEdgeEnabled = true
		
		post_helper = PostHelper(this, pref, app_state.handler)
		
		val dm = resources.displayMetrics
		
		val density = dm.density
		
		var media_thumb_height = 64
		sv = Pref.spMediaThumbHeight(pref)
		if(sv.isNotEmpty()) {
			try {
				val iv = Integer.parseInt(sv)
				if(iv >= 32) {
					media_thumb_height = iv
				}
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			
		}
		app_state.media_thumb_height = (0.5f + media_thumb_height * density).toInt()
		
		var column_w_min_dp = COLUMN_WIDTH_MIN_DP
		sv = Pref.spColumnWidth(pref)
		if(sv.isNotEmpty()) {
			try {
				val iv = Integer.parseInt(sv)
				if(iv >= 100) {
					column_w_min_dp = iv
				}
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			
		}
		val column_w_min = (0.5f + column_w_min_dp * density).toInt()
		
		val sw = dm.widthPixels
		
		if(Pref.bpDisableTabletMode(pref) || sw < column_w_min * 2) {
			// SmartPhone mode
			findViewById<View>(R.id.rvPager).visibility = View.GONE
			phoneEnv = PhoneEnv()
		} else {
			// Tablet mode
			findViewById<View>(R.id.viewPager).visibility = View.GONE
			tabletEnv = TabletEnv()
		}
		
		phoneTab({ env ->
			env.pager = findViewById(R.id.viewPager)
			env.pager_adapter = ColumnPagerAdapter(this)
			env.pager.adapter = env.pager_adapter
			env.pager.addOnPageChangeListener(this)
			
			resizeAutoCW(sw)
			
		}, { env ->
			env.tablet_pager = findViewById(R.id.rvPager)
			env.tablet_pager_adapter = TabletColumnPagerAdapter(this)
			env.tablet_layout_manager =
				LinearLayoutManager(
					this,
					LinearLayoutManager.HORIZONTAL,
					false
				)
			
			if(env.tablet_pager.itemDecorationCount == 0) {
				env.tablet_pager.addItemDecoration(TabletColumnDivider(this@ActMain))
			}
			
			
			env.tablet_pager.adapter = env.tablet_pager_adapter
			env.tablet_pager.layoutManager = env.tablet_layout_manager
			env.tablet_pager.addOnScrollListener(object :
				RecyclerView.OnScrollListener() {
				
				override fun onScrollStateChanged(
					recyclerView : RecyclerView,
					newState : Int
				) {
					super.onScrollStateChanged(recyclerView, newState)
					
					val vs = env.tablet_layout_manager.findFirstVisibleItemPosition()
					val ve = env.tablet_layout_manager.findLastVisibleItemPosition()
					// 端に近い方に合わせる
					val distance_left = abs(vs)
					val distance_right = abs(app_state.column_list.size - 1 - ve)
					if(distance_left < distance_right) {
						scrollColumnStrip(vs)
					} else {
						scrollColumnStrip(ve)
					}
				}
				
				override fun onScrolled(
					recyclerView : RecyclerView,
					dx : Int,
					dy : Int
				) {
					super.onScrolled(recyclerView, dx, dy)
					updateColumnStripSelection(- 1, - 1f)
				}
			})
			
			env.tablet_pager.itemAnimator = null
			//			val animator = env.tablet_pager.itemAnimator
			//			if( animator is DefaultItemAnimator){
			//				animator.supportsChangeAnimations = false
			//			}
			
			env.tablet_snap_helper = GravitySnapHelper(Gravity.START)
			env.tablet_snap_helper.attachToRecyclerView(env.tablet_pager)
			
		})
		
		showFooterColor()
		
		post_helper.attachEditText(
			findViewById(R.id.llFormRoot),
			etQuickToot,
			true,
			object : PostHelper.Callback2 {
				override fun onTextUpdate() {}
				
				override fun canOpenPopup() : Boolean {
					return ! drawer.isDrawerOpen(GravityCompat.START)
				}
			})
	}
	
	private fun isVisibleColumn(idx : Int) = phoneTab({ env ->
		val c = env.pager.currentItem
		c == idx
	}, { env ->
		idx >= 0 && idx in env.visibleRange
		
	})
	
	internal fun updateColumnStrip() {
		vg(llEmpty, app_state.column_list.isEmpty())
		
		val iconSize = stripIconSize
		val rootW = (iconSize * 1.25f + 0.5f).toInt()
		val rootH = (iconSize * 1.5f + 0.5f).toInt()
		val iconTopMargin = (iconSize * 0.125f + 0.5f).toInt()
		val barHeight = (iconSize * 0.094f + 0.5f).toInt()
		val barTopMargin = (iconSize * 0.094f + 0.5f).toInt()
		
		// 両端のメニューと投稿ボタンの大きさ
		val pad = (rootH - iconSize) shr 1
		btnToot.layoutParams.width = rootH // not W
		btnToot.layoutParams.height = rootH
		btnToot.setPaddingRelative(pad, pad, pad, pad)
		btnMenu.layoutParams.width = rootH // not W
		btnMenu.layoutParams.height = rootH
		btnMenu.setPaddingRelative(pad, pad, pad, pad)
		
		llColumnStrip.removeAllViews()
		for(i in 0 until app_state.column_list.size) {
			
			val column = app_state.column_list[i]
			
			val viewRoot = layoutInflater.inflate(R.layout.lv_column_strip, llColumnStrip, false)
			val ivIcon = viewRoot.findViewById<ImageView>(R.id.ivIcon)
			val vAcctColor = viewRoot.findViewById<View>(R.id.vAcctColor)
			
			// root: 48x48dp LinearLayout(vertical), gravity=center
			viewRoot.layoutParams.width = rootW
			viewRoot.layoutParams.height = rootH
			
			// ivIcon: 32x32dp marginTop="4dp" 図柄が32x32dp、パディングなし
			ivIcon.layoutParams.width = iconSize
			ivIcon.layoutParams.height = iconSize
			(ivIcon.layoutParams as? LinearLayout.LayoutParams)?.topMargin = iconTopMargin
			
			// vAcctColor: 32x3dp marginTop="3dp"
			vAcctColor.layoutParams.width = iconSize
			vAcctColor.layoutParams.height = barHeight
			(vAcctColor.layoutParams as? LinearLayout.LayoutParams)?.topMargin = barTopMargin
			
			viewRoot.tag = i
			viewRoot.setOnClickListener { v ->
				val idx = v.tag as Int
				if(Pref.bpScrollTopFromColumnStrip(pref) && isVisibleColumn(idx)) {
					app_state.column_list[i].viewHolder?.scrollToTop2()
					return@setOnClickListener
				}
				scrollToColumn(idx)
			}
			viewRoot.contentDescription = column.getColumnName(true)
			//
			
			column.setHeaderBackground(viewRoot)
			
			ivIcon.setImageResource(column.getIconId())
			ivIcon.imageTintList = ColorStateList.valueOf(column.getHeaderNameColor())
			
			//
			val ac = AcctColor.load(column.access_info.acct)
			if(AcctColor.hasColorForeground(ac)) {
				vAcctColor.setBackgroundColor(ac.color_fg)
			} else {
				vAcctColor.visibility = View.INVISIBLE
			}
			
			//
			llColumnStrip.addView(viewRoot)
		}
		svColumnStrip.requestLayout()
		updateColumnStripSelection(- 1, - 1f)
		
	}
	
	private fun updateColumnStripSelection(position : Int, positionOffset : Float) {
		handler.post(Runnable {
			if(isFinishing) return@Runnable
			
			if(app_state.column_list.isEmpty()) {
				llColumnStrip.setVisibleRange(- 1, - 1, 0f)
			} else {
				phoneTab({ env ->
					if(position >= 0) {
						llColumnStrip.setVisibleRange(position, position, positionOffset)
					} else {
						val c = env.pager.currentItem
						llColumnStrip.setVisibleRange(c, c, 0f)
					}
					
				}, { env ->
					val vr = env.visibleRange
					var slide_ratio = 0f
					if(vr.first <= vr.last) {
						val child = env.tablet_layout_manager.findViewByPosition(vr.first)
						slide_ratio =
							clipRange(0f, 1f, abs((child?.left ?: 0) / nColumnWidth.toFloat()))
					}
					
					llColumnStrip.setVisibleRange(vr.first, vr.last, slide_ratio)
				})
			}
		})
	}
	
	private fun scrollColumnStrip(select : Int) {
		val child_count = llColumnStrip.childCount
		if(select < 0 || select >= child_count) {
			return
		}
		
		val icon = llColumnStrip.getChildAt(select)
		
		val sv_width = (llColumnStrip.parent as View).width
		val ll_width = llColumnStrip.width
		val icon_width = icon.width
		val icon_left = icon.left
		
		if(sv_width == 0 || ll_width == 0 || icon_width == 0) {
			handler.postDelayed({ scrollColumnStrip(select) }, 20L)
		}
		
		val sx = icon_left + icon_width / 2 - sv_width / 2
		svColumnStrip.smoothScrollTo(sx, 0)
		
	}
	
	fun startAccessTokenUpdate(data : Intent) {
		App1.openBrowser(this, data.data)
	}
	
	// ActOAuthCallbackで受け取ったUriを処理する
	private fun handleIntentUri(uri : Uri) {
		
		log.d("handleIntentUri ${uri}")
		
		when(uri.scheme) {
			"subwaytooter", "misskeyclientproto" -> return try {
				handleOAuth2CallbackUri(uri)
			} catch(ex : Throwable) {
				log.trace(ex)
			}
		}
		
		val url = uri.toString()
		
		// https://mastodon.juggler.jp/@SubwayTooter/(status_id)
		var m = TootStatus.reStatusPage.matcher(url)
		if(m.find()) {
			try {
				val host = m.group(1)
				val status_id = EntityId(m.group(3))
				
				// ステータスをアプリ内で開く
				Action_Toot.conversationOtherInstance(
					this@ActMain,
					defaultInsertPosition,
					url,
					status_id,
					host,
					status_id
				)
				
			} catch(ex : Throwable) {
				showToast(this, ex, "can't parse status id.")
			}
			
			return
		}
		
		// https://misskey.xyz/notes/(id)
		m = TootStatus.reStatusPageMisskey.matcher(url)
		if(m.find()) {
			try {
				val host = m.group(1)
				val status_id = EntityId(m.group(2))
				// ステータスをアプリ内で開く
				Action_Toot.conversationOtherInstance(
					this@ActMain,
					defaultInsertPosition,
					url,
					status_id,
					host,
					status_id
				)
			} catch(ex : Throwable) {
				showToast(this, ex, "can't parse status id.")
			}
			
			return
		}
		
		// ユーザページをアプリ内で開く
		m = TootAccount.reAccountUrl.matcher(url)
		if(m.find()) {
			val host = m.group(1)
			val user = m.group(2).decodePercent()
			val instance = m.groupOrNull(3)?.decodePercent()
			
			if(instance?.isNotEmpty() == true) {
				Action_User.profile(
					this@ActMain,
					defaultInsertPosition,
					null,
					"https://$instance/@$user",
					instance,
					user,
					original_url = url
				)
			} else {
				Action_User.profile(
					this@ActMain,
					defaultInsertPosition,
					null,
					url,
					host,
					user
				)
			}
			return
		}
		
		// intentFilterの都合でこの形式のURLが飛んでくることはないのだが…。
		m = TootAccount.reAccountUrl2.matcher(url)
		if(m.find()) {
			val host = m.group(1)
			val user = m.group(2).decodePercent()
			
			Action_User.profile(
				this@ActMain,
				defaultInsertPosition,
				null,
				url,
				host,
				user
			)
			return
		}
		
		// このアプリでは処理できないURLだった
		// 外部ブラウザを開きなおそうとすると無限ループの恐れがある
		// アプリケーションチューザーを表示する
		
		val error_message = getString(R.string.cant_handle_uri_of, url)
		
		try {
			val query_flag = if(Build.VERSION.SDK_INT >= 23) {
				// Android 6.0以降
				// MATCH_DEFAULT_ONLY だと標準の設定に指定されたアプリがあるとソレしか出てこない
				// MATCH_ALL を指定すると 以前と同じ挙動になる
				PackageManager.MATCH_ALL
			} else {
				// Android 5.xまでは MATCH_DEFAULT_ONLY でマッチするすべてのアプリを取得できる
				PackageManager.MATCH_DEFAULT_ONLY
			}
			
			// queryIntentActivities に渡すURLは実在しないホストのものにする
			val intent = Intent(Intent.ACTION_VIEW, "https://dummy.subwaytooter.club/".toUri())
			intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
			val resolveInfoList = packageManager.queryIntentActivities(intent, query_flag)
			if(resolveInfoList.isEmpty()) {
				throw RuntimeException("resolveInfoList is empty.")
			}
			
			// このアプリ以外の選択肢を集める
			val my_name = packageName
			val choice_list = ArrayList<Intent>()
			for(ri in resolveInfoList) {
				
				// 選択肢からこのアプリを除外
				if(my_name == ri.activityInfo.packageName) continue
				
				// 選択肢のIntentは目的のUriで作成する
				val choice = Intent(Intent.ACTION_VIEW, uri)
				intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
				choice.`package` = ri.activityInfo.packageName
				choice.setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
				choice_list.add(choice)
			}
			
			if(choice_list.isEmpty()) {
				throw RuntimeException("choice_list is empty.")
			}
			// 指定した選択肢でチューザーを作成して開く
			val chooser = Intent.createChooser(choice_list.removeAt(0), error_message)
			chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, choice_list.toTypedArray())
			startActivity(chooser)
			return
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
		AlertDialog.Builder(this)
			.setCancelable(true)
			.setMessage(error_message)
			.setPositiveButton(R.string.close, null)
			.show()
		
	}
	
	private fun handleOAuth2CallbackUri(uri : Uri) {
		
		// 通知タップ
		// subwaytooter://notification_click/?db_id=(db_id)
		val dataIdString = uri.getQueryParameter("db_id")
		if(dataIdString != null) {
			try {
				val dataId = dataIdString.toLong(10)
				val account = SavedAccount.loadAccount(this@ActMain, dataId)
				if(account != null) {
					var column = app_state.column_list.firstOrNull {
						it.type == ColumnType.NOTIFICATIONS
							&& account.acct == it.access_info.acct
							&& ! it.system_notification_not_related
					}
					if(column != null) {
						val index = app_state.column_list.indexOf(column)
						scrollToColumn(index)
					} else {
						column = addColumn(
							true,
							defaultInsertPosition,
							account,
							ColumnType.NOTIFICATIONS
						)
					}
					
					// 通知を読み直す
					if(! column.bInitialLoading) {
						column.startLoading()
					}
					
					PollingWorker.queueNotificationClicked(this, dataId)
					
				}
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			
			return
		}
		
		// OAuth2 認証コールバック
		// subwaytooter://oauth(\d*)/?...
		TootTaskRunner(this@ActMain).run(object : TootTask {
			
			var ta : TootAccount? = null
			var sa : SavedAccount? = null
			var host : String? = null
			
			override fun background(client : TootApiClient) : TootApiResult? {
				
				val uriStr = uri.toString()
				if(uriStr.startsWith("subwaytooter://misskey/auth_callback")
					|| uriStr.startsWith("misskeyclientproto://misskeyclientproto/auth_callback")
				) {
					
					// Misskey 認証コールバック
					val token = uri.getQueryParameter("token")
					if(token?.isEmpty() != false) {
						return TootApiResult("missing token in callback URL")
					}
					val prefDevice = PrefDevice.prefDevice(this@ActMain)
					
					val db_id = prefDevice.getLong(PrefDevice.LAST_AUTH_DB_ID, - 1L)
					
					val instance = prefDevice.getString(PrefDevice.LAST_AUTH_INSTANCE, null)
						?: return TootApiResult("missing instance name.")
					
					if(db_id != - 1L) {
						try {
							val sa = SavedAccount.loadAccount(this@ActMain, db_id)
								?: return TootApiResult("missing account db_id=$db_id")
							this.sa = sa
							client.account = sa
						} catch(ex : Throwable) {
							log.trace(ex)
							return TootApiResult(ex.withCaption("invalid state"))
						}
					} else {
						client.instance = instance
					}
					
					val (r2, ti) = client.parseInstanceInformation(client.getInstanceInformation())
					if(ti == null) return r2
					val misskeyVersion = when {
						ti.versionGE(TootInstance.MISSKEY_VERSION_11) -> 11
						else -> 10
					}
					
					
					
					this.host = instance
					val client_name = Pref.spClientName(this@ActMain)
					val result = client.authentication2Misskey(client_name, token, misskeyVersion)
					this.ta = TootParser(
						this@ActMain
						, LinkHelper.newLinkHelper(instance, misskeyVersion = misskeyVersion)
					).account(result?.jsonObject)
					return result
					
				} else {
					// Mastodon 認証コールバック
					
					// エラー時
					// subwaytooter://oauth(\d*)/
					// ?error=access_denied
					// &error_description=%E3%83%AA%E3%82%BD%E3%83%BC%E3%82%B9%E3%81%AE%E6%89%80%E6%9C%89%E8%80%85%E3%81%BE%E3%81%9F%E3%81%AF%E8%AA%8D%E8%A8%BC%E3%82%B5%E3%83%BC%E3%83%90%E3%83%BC%E3%81%8C%E8%A6%81%E6%B1%82%E3%82%92%E6%8B%92%E5%90%A6%E3%81%97%E3%81%BE%E3%81%97%E3%81%9F%E3%80%82
					// &state=db%3A3
					val error = uri.getQueryParameter("error_description")
					if(error?.isNotEmpty() == true) {
						return TootApiResult(error)
					}
					
					// subwaytooter://oauth(\d*)/
					//    ?code=113cc036e078ac500d3d0d3ad345cd8181456ab087abc67270d40f40a4e9e3c2
					//    &state=host%3Amastodon.juggler.jp
					
					val code = uri.getQueryParameter("code")
					if(code?.isEmpty() != false) {
						return TootApiResult("missing code in callback url.")
					}
					
					val sv = uri.getQueryParameter("state")
					if(sv?.isEmpty() != false) {
						return TootApiResult("missing state in callback url.")
					}
					
					for(param in sv.split(",")) {
						when {
							
							param.startsWith("db:") -> try {
								val dataId = param.substring(3).toLong(10)
								val sa = SavedAccount.loadAccount(this@ActMain, dataId)
									?: return TootApiResult("missing account db_id=$dataId")
								this.sa = sa
								client.account = sa
							} catch(ex : Throwable) {
								log.trace(ex)
								return TootApiResult(ex.withCaption("invalid state"))
							}
							
							param.startsWith("host:") -> {
								val host = param.substring(5)
								client.instance = host
							}
							
							else -> {
								// ignore other parameter
							}
						}
					}
					
					val instance = client.instance
						?: return TootApiResult("missing instance in callback url.")
					
					this.host = instance
					val client_name = Pref.spClientName(this@ActMain)
					val result = client.authentication2(client_name, code)
					this.ta = TootParser(
						this@ActMain
						, LinkHelper.newLinkHelper(instance)
					).account(result?.jsonObject)
					return result
				}
				
			}
			
			override fun handleResult(result : TootApiResult?) {
				val host = this.host
				val ta = this.ta
				var sa = this.sa
				
				if(ta != null && host != null && sa == null) {
					val user = ta.username + "@" + host
					// アカウント追加時に、アプリ内に既にあるアカウントと同じものを登録していたかもしれない
					sa = SavedAccount.loadAccountByAcct(this@ActMain, user)
				}
				
				afterAccountVerify(result, ta, sa, host)
			}
			
		})
	}
	
	internal fun afterAccountVerify(
		result : TootApiResult?,
		ta : TootAccount?,
		sa : SavedAccount?,
		host : String?
	) : Boolean {
		
		val jsonObject = result?.jsonObject
		val token_info = result?.tokenInfo
		val error = result?.error
		
		if(result == null) {
			// cancelled.
			
		} else if(error != null) {
			showToast(this@ActMain, true, result.error)
			
		} else if(token_info == null) {
			showToast(this@ActMain, true, "can't get access token.")
			
		} else if(jsonObject == null) {
			showToast(this@ActMain, true, "can't parse json response.")
			
		} else if(ta == null) {
			// 自分のユーザネームを取れなかった
			// …普通はエラーメッセージが設定されてるはずだが
			showToast(this@ActMain, true, "can't verify user credential.")
			
		} else if(sa != null) {
			// アクセストークン更新時
			
			// インスタンスは同じだと思うが、ユーザ名が異なる可能性がある
			if(sa.username != ta.username) {
				showToast(this@ActMain, true, R.string.user_name_not_match)
			} else {
				showToast(this@ActMain, false, R.string.access_token_updated_for, sa.acct)
				
				// DBの情報を更新する
				sa.updateTokenInfo(token_info)
				
				// 各カラムの持つアカウント情報をリロードする
				reloadAccountSetting()
				
				// 自動でリロードする
				for(it in app_state.column_list) {
					if(it.access_info.acct == sa.acct) {
						it.startLoading()
					}
				}
				
				// 通知の更新が必要かもしれない
				PollingWorker.queueUpdateNotification(this@ActMain)
				return true
			}
		} else if(host != null) {
			// アカウント追加時
			val user = ta.username + "@" + host
			
			val row_id = SavedAccount.insert(
				host,
				user,
				jsonObject,
				token_info,
				misskeyVersion = TootApiClient.parseMisskeyVersion(token_info)
			)
			val account = SavedAccount.loadAccount(this@ActMain, row_id)
			if(account != null) {
				var bModified = false
				
				if(account.loginAccount?.locked == true) {
					bModified = true
					account.visibility = TootVisibility.PrivateFollowers
				}
				if(! account.isMisskey) {
					val source = ta.source
					if(source != null) {
						val privacy = TootVisibility.parseMastodon(source.privacy)
						if(privacy != null) {
							bModified = true
							account.visibility = privacy
						}
						
						// XXX ta.source.sensitive パラメータを読んで「添付画像をデフォルトでNSFWにする」を実現する
						// 現在、アカウント設定にはこの項目はない( 「NSFWな添付メディアを隠さない」はあるが全く別の効果)
					}
					
					if(bModified) {
						account.saveSetting()
					}
				}
				
				showToast(this@ActMain, false, R.string.account_confirmed)
				
				// 通知の更新が必要かもしれない
				PollingWorker.queueUpdateNotification(this@ActMain)
				
				// 適当にカラムを追加する
				val count = SavedAccount.count
				if(count > 1) {
					addColumn(false, defaultInsertPosition, account, ColumnType.HOME)
				} else {
					addColumn(false, defaultInsertPosition, account, ColumnType.HOME)
					addColumn(false, defaultInsertPosition, account, ColumnType.NOTIFICATIONS)
					addColumn(false, defaultInsertPosition, account, ColumnType.LOCAL)
					addColumn(false, defaultInsertPosition, account, ColumnType.FEDERATE)
				}
				
				return true
			}
		}
		return false
	}
	
	// アクセストークンを手動で入力した場合
	fun checkAccessToken(
		dialog_host : Dialog?,
		dialog_token : Dialog?,
		host : String,
		access_token : String,
		sa : SavedAccount?
	) {
		
		TootTaskRunner(this@ActMain).run(host, object : TootTask {
			
			var ta : TootAccount? = null
			
			override fun background(client : TootApiClient) : TootApiResult? {
				val r1 = client.getInstanceInformation()
				val ti = r1?.jsonObject ?: return r1
				val misskeyVersion = TootApiClient.parseMisskeyVersion(ti)
				
				val linkHelper = LinkHelper.newLinkHelper(host, misskeyVersion = misskeyVersion)
				val result = client.getUserCredential(access_token, misskeyVersion = misskeyVersion)
				this.ta = TootParser(this@ActMain, linkHelper)
					.account(result?.jsonObject)
				return result
			}
			
			override fun handleResult(result : TootApiResult?) {
				
				if(afterAccountVerify(result, ta, sa, host)) {
					dialog_host?.dismissSafe()
					dialog_token?.dismissSafe()
				}
			}
		})
	}
	
	// アクセストークンの手動入力(更新)
	private fun checkAccessToken2(db_id : Long) {
		
		val sa = SavedAccount.loadAccount(this, db_id) ?: return
		
		DlgTextInput.show(
			this,
			getString(R.string.access_token_or_api_token),
			null,
			object : DlgTextInput.Callback {
				override fun onOK(dialog : Dialog, text : String) {
					checkAccessToken(null, dialog, sa.host, text, sa)
				}
				
				override fun onEmptyError() {
					showToast(this@ActMain, true, R.string.token_not_specified)
				}
			})
	}
	
	private fun reloadAccountSetting() {
		val done_list = ArrayList<SavedAccount>()
		for(column in app_state.column_list) {
			val a = column.access_info
			if(done_list.contains(a)) continue
			done_list.add(a)
			if(! a.isNA) a.reloadSetting(this@ActMain)
			column.fireShowColumnHeader()
		}
	}
	
	fun reloadAccountSetting(account : SavedAccount) {
		val done_list = ArrayList<SavedAccount>()
		for(column in app_state.column_list) {
			val a = column.access_info
			if(a.acct != account.acct) continue
			if(done_list.contains(a)) continue
			done_list.add(a)
			if(! a.isNA) a.reloadSetting(this@ActMain)
			column.fireShowColumnHeader()
		}
	}
	
	fun closeColumn(column : Column, bConfirmed : Boolean = false) {
		
		if(column.dont_close) {
			showToast(this, false, R.string.column_has_dont_close_option)
			return
		}
		
		if(! bConfirmed && ! Pref.bpDontConfirmBeforeCloseColumn(pref)) {
			AlertDialog.Builder(this)
				.setMessage(R.string.confirm_close_column)
				.setNegativeButton(R.string.cancel, null)
				.setPositiveButton(R.string.ok) { _, _ -> closeColumn(column, bConfirmed = true) }
				.show()
			return
		}
		
		val page_delete = app_state.column_list.indexOf(column)
		
		phoneTab({ env ->
			val page_showing = env.pager.currentItem
			
			removeColumn(column)
			
			if(app_state.column_list.isNotEmpty() && page_delete > 0 && page_showing == page_delete) {
				val idx = page_delete - 1
				scrollToColumn(idx)
				val c = app_state.column_list[idx]
				if(! c.bFirstInitialized) {
					c.startLoading()
				}
			}
			
		}, {
			removeColumn(column)
			
			if(app_state.column_list.isNotEmpty() && page_delete > 0) {
				val idx = page_delete - 1
				scrollToColumn(idx)
				val c = app_state.column_list[idx]
				if(! c.bFirstInitialized) {
					c.startLoading()
				}
			}
		})
	}
	
	fun closeColumnAll(
		_lastColumnIndex : Int = - 1,
		bConfirmed : Boolean = false
	) {
		
		if(! bConfirmed) {
			AlertDialog.Builder(this)
				.setMessage(R.string.confirm_close_column_all)
				.setNegativeButton(R.string.cancel, null)
				.setPositiveButton(R.string.ok) { _, _ -> closeColumnAll(_lastColumnIndex, true) }
				.show()
			return
		}
		
		var lastColumnIndex = when(_lastColumnIndex) {
			- 1 -> phoneTab(
				{ it.pager.currentItem },
				{ 0 }
			)
			else -> _lastColumnIndex
		}
		
		phoneOnly { env -> env.pager.adapter = null }
		
		for(i in (0 until app_state.column_list.size).reversed()) {
			val column = app_state.column_list[i]
			if(column.dont_close) continue
			app_state.column_list.removeAt(i).dispose()
			if(lastColumnIndex >= i) -- lastColumnIndex
		}
		
		phoneTab(
			{ env -> env.pager.adapter = env.pager_adapter },
			{ env -> resizeColumnWidth(env) }
		)
		
		app_state.saveColumnList()
		updateColumnStrip()
		
		if(app_state.column_list.isNotEmpty() && lastColumnIndex >= 0 && lastColumnIndex < app_state.column_list.size) {
			scrollToColumn(lastColumnIndex)
			val c = app_state.column_list[lastColumnIndex]
			if(! c.bFirstInitialized) {
				c.startLoading()
			}
		}
	}
	
	//////////////////////////////////////////////////////////////
	// カラム追加系
	
	fun addColumn(
		indexArg : Int,
		ai : SavedAccount,
		type : ColumnType,
		vararg params : Any
	) : Column {
		return addColumn(
			Pref.bpAllowColumnDuplication(pref),
			indexArg,
			ai,
			type,
			*params
		)
	}
	
	fun addColumn(
		allowColumnDuplication : Boolean,
		indexArg : Int,
		ai : SavedAccount,
		type : ColumnType,
		vararg params : Any
	) : Column {
		if(! allowColumnDuplication) {
			// 既に同じカラムがあればそこに移動する
			for(column in app_state.column_list) {
				if(column.isSameSpec(ai, type, params)) {
					val indexColumn = app_state.column_list.indexOf(column)
					scrollToColumn(indexColumn)
					return column
				}
			}
		}
		//
		val col = Column(app_state, ai, this, type.id, *params)
		val index = addColumn(col, indexArg)
		scrollToColumn(index)
		if(! col.bFirstInitialized) {
			col.startLoading()
		}
		return col
	}
	
	fun openChromeTab(opener : ChromeTabOpener) {
		
		try {
			log.d("openChromeTab url=%s", opener.url)
			
			val accessInto = opener.accessInfo
			val whoRef = opener.whoRef
			val whoAcct = if(whoRef != null) {
				accessInto?.getFullAcct(whoRef.get())
			} else {
				null
			}
			
			if(opener.allowIntercept && accessInto != null) {
				
				// ハッシュタグはいきなり開くのではなくメニューがある
				var m = reUrlHashTag.matcher(opener.url)
				if(m.find()) {
					// https://mastodon.juggler.jp/tags/%E3%83%8F%E3%83%83%E3%82%B7%E3%83%A5%E3%82%BF%E3%82%B0
					val host = m.group(1)
					val tag_without_sharp = m.group(2).decodePercent()
					Action_HashTag.dialog(
						this@ActMain,
						opener.pos,
						opener.url,
						host,
						tag_without_sharp,
						opener.tagList,
						whoAcct
					)
					return
				}
				
				// ステータスページをアプリから開く
				m = TootStatus.reStatusPage.matcher(opener.url)
				if(m.find()) {
					try {
						// https://mastodon.juggler.jp/@SubwayTooter/(status_id)
						val host = m.group(1)
						val status_id = EntityId(m.group(3))
						if(accessInto.isNA || ! host.equals(accessInto.host, ignoreCase = true)) {
							Action_Toot.conversationOtherInstance(
								this@ActMain,
								opener.pos,
								opener.url,
								status_id,
								host,
								status_id
							)
						} else {
							Action_Toot.conversationLocal(
								this@ActMain,
								opener.pos,
								accessInto,
								status_id
							)
						}
					} catch(ex : Throwable) {
						showToast(this, ex, "can't parse status id.")
					}
					
					return
				}
				
				// ステータスページをアプリから開く
				m = TootStatus.reStatusPageMisskey.matcher(opener.url)
				if(m.find()) {
					try {
						// https://misskey.xyz/notes/(id)
						val host = m.group(1)
						val status_id = EntityId(m.group(2))
						if(accessInto.isNA || ! host.equals(accessInto.host, ignoreCase = true)) {
							Action_Toot.conversationOtherInstance(
								this@ActMain,
								opener.pos,
								opener.url,
								status_id,
								host,
								status_id
							)
						} else {
							Action_Toot.conversationLocal(
								this@ActMain,
								opener.pos,
								accessInto,
								status_id
							)
						}
					} catch(ex : Throwable) {
						showToast(this, ex, "can't parse status id.")
					}
					
					return
				}
				
				
				m = TootStatus.reStatusPageObjects.matcher(opener.url)
				if(m.find()) {
					try {
						// https://misskey.xyz/objects/(id)
						val host = m.group(1)
						// ステータスIDではないのでどのタンスで開くにせよ検索APIを通すことになるval object_id = EntityId(m.group(2))
						Action_Toot.conversationOtherInstance(
							this@ActMain,
							opener.pos,
							opener.url,
							null,
							host,
							null
						)
					} catch(ex : Throwable) {
						showToast(this, ex, "can't parse status id.")
					}
					
					return
				}
				
				// https://pl.telteltel.com/notice/9fGFPu4LAgbrTby0xc
				m = TootStatus.reStatusPageNotice.matcher(opener.url)
				if(m.find()) {
					try {
						// https://misskey.xyz/notes/(id)
						val host = m.group(1)
						val status_id = EntityId(m.group(2))
						if(accessInto.isNA || ! host.equals(accessInto.host, ignoreCase = true)) {
							Action_Toot.conversationOtherInstance(
								this@ActMain,
								opener.pos,
								opener.url,
								status_id,
								host,
								status_id
							)
						} else {
							Action_Toot.conversationLocal(
								this@ActMain,
								opener.pos,
								accessInto,
								status_id
							)
						}
					} catch(ex : Throwable) {
						showToast(this, ex, "can't parse status id.")
					}
					
					return
				}
				
				// ユーザページをアプリ内で開く
				m = TootAccount.reAccountUrl.matcher(opener.url)
				if(m.find()) {
					val host = m.group(1)
					val user = m.group(2).decodePercent()
					val instance = m.groupOrNull(3)?.decodePercent()
					// https://misskey.xyz/@tateisu@github.com
					// https://misskey.xyz/@tateisu@twitter.com
					
					if(instance?.isNotEmpty() == true) {
						when(instance.toLowerCase()) {
							"github.com", "twitter.com" -> {
								App1.openCustomTab(this, "https://$instance/$user")
							}
							
							"gmail.com" -> {
								App1.openBrowser(this, "mailto:$user@$instance")
							}
							
							else -> {
								Action_User.profile(
									this@ActMain,
									opener.pos,
									null,
									"https://$instance/@$user",
									instance,
									user,
									original_url = opener.url
								)
							}
						}
					} else {
						Action_User.profile(
							this@ActMain,
							opener.pos,
							accessInto,
							opener.url,
							host,
							user
						)
					}
					return
				}
				
				m = TootAccount.reAccountUrl2.matcher(opener.url)
				if(m.find()) {
					val host = m.group(1)
					val user = m.group(2).decodePercent()
					
					Action_User.profile(
						this@ActMain,
						opener.pos,
						accessInto,
						opener.url,
						host,
						user
					)
					return
				}
			}
			
			App1.openCustomTab(this, opener.url)
			
		} catch(ex : Throwable) {
			// warning.trace( ex );
			log.e(ex, "openChromeTab failed. url=%s", opener.url)
		}
		
	}
	
	/////////////////////////////////////////////////////////////////////////
	
	fun showColumnMatchAccount(account : SavedAccount) {
		for(column in app_state.column_list) {
			if(account.acct == column.access_info.acct) {
				column.fireRebindAdapterItems()
			}
		}
	}
	
	private fun showFooterColor() {
		val footer_button_bg_color = Pref.ipFooterButtonBgColor(pref)
		val footer_button_fg_color = Pref.ipFooterButtonFgColor(pref)
		val footer_tab_bg_color = Pref.ipFooterTabBgColor(pref)
		val footer_tab_divider_color = Pref.ipFooterTabDividerColor(pref)
		val footer_tab_indicator_color = Pref.ipFooterTabIndicatorColor(pref)
		
		val colorBg = footer_button_bg_color.notZero() ?: getAttributeColor(
			this,
			R.attr.colorStatusButtonsPopupBg
		)
		val colorRipple =
			footer_button_fg_color.notZero() ?: getAttributeColor(this, R.attr.colorRippleEffect)
		btnMenu.backgroundDrawable = getAdaptiveRippleDrawable(colorBg, colorRipple)
		btnToot.backgroundDrawable = getAdaptiveRippleDrawable(colorBg, colorRipple)
		btnQuickToot.backgroundDrawable = getAdaptiveRippleDrawable(colorBg, colorRipple)
		btnQuickTootMenu.backgroundDrawable = getAdaptiveRippleDrawable(colorBg, colorRipple)
		
		val csl = ColorStateList.valueOf(
			footer_button_fg_color.notZero()
				?: getAttributeColor(this, R.attr.colorVectorDrawable)
		)
		btnToot.imageTintList = csl
		btnMenu.imageTintList = csl
		btnQuickToot.imageTintList = csl
		btnQuickTootMenu.imageTintList = csl
		
		var c = footer_tab_bg_color.notZero()
			?: getAttributeColor(this, R.attr.colorColumnStripBackground)
		svColumnStrip.setBackgroundColor(c)
		llQuickTootBar.setBackgroundColor(c)
		
		c = footer_tab_divider_color.notZero()
			?: getAttributeColor(this, R.attr.colorImageButton)
		vFooterDivider1.setBackgroundColor(c)
		vFooterDivider2.setBackgroundColor(c)
		
		llColumnStrip.indicatorColor = footer_tab_indicator_color
	}
	
	/////////////////////////////////////////////////////////////////////////
	// タブレット対応で必要になった関数など
	
	private fun closeColumnSetting() : Boolean {
		phoneTab({ env ->
			val vh = env.pager_adapter.getColumnViewHolder(env.pager.currentItem)
			if(vh?.isColumnSettingShown == true) {
				vh.closeColumnSetting()
				return@closeColumnSetting true
			}
		}, { env ->
			for(i in 0 until env.tablet_layout_manager.childCount) {
				
				val columnViewHolder = when(val v = env.tablet_layout_manager.getChildAt(i)) {
					null -> null
					else -> (env.tablet_pager.getChildViewHolder(v) as? TabletColumnViewHolder)?.columnViewHolder
				}
				
				if(columnViewHolder?.isColumnSettingShown == true) {
					columnViewHolder.closeColumnSetting()
					return@closeColumnSetting true
				}
			}
		})
		return false
	}
	
	private fun addColumn(column : Column, indexArg : Int) : Int {
		var index = indexArg
		val size = app_state.column_list.size
		if(index > size) index = size
		
		phoneOnly { env -> env.pager.adapter = null }
		
		app_state.column_list.add(index, column)
		
		phoneTab(
			{ env -> env.pager.adapter = env.pager_adapter },
			{ env -> resizeColumnWidth(env) }
		)
		
		app_state.saveColumnList()
		updateColumnStrip()
		
		return index
	}
	
	private fun removeColumn(column : Column) {
		val idx_column = app_state.column_list.indexOf(column)
		if(idx_column == - 1) return
		
		phoneOnly { env -> env.pager.adapter = null }
		
		app_state.column_list.removeAt(idx_column).dispose()
		
		phoneTab(
			{ env -> env.pager.adapter = env.pager_adapter },
			{ env -> resizeColumnWidth(env) }
		)
		
		app_state.saveColumnList()
		updateColumnStrip()
	}
	
	private fun setOrder(new_order : ArrayList<Int>) {
		
		phoneOnly { env -> env.pager.adapter = null }
		
		val ie = app_state.column_list.size
		
		val tmp_list = ArrayList<Column>()
		val used_set = HashSet<Int>()
		
		// copy by new_order
		for(i in new_order) {
			if(0 <= i && i < ie) {
				used_set.add(i)
				tmp_list.add(app_state.column_list[i])
			}
		}
		
		// dispose unused elements.
		for(i in 0 until ie) {
			if(used_set.contains(i)) continue
			app_state.column_list[i].dispose()
		}
		app_state.column_list.clear()
		app_state.column_list.addAll(tmp_list)
		
		phoneTab(
			{ env -> env.pager.adapter = env.pager_adapter },
			{ env -> resizeColumnWidth(env) }
		)
		
		app_state.saveColumnList()
		updateColumnStrip()
	}
	
	private fun resizeColumnWidth(env : TabletEnv) {
		
		var column_w_min_dp = COLUMN_WIDTH_MIN_DP
		val sv = Pref.spColumnWidth(pref)
		if(sv.isNotEmpty()) {
			try {
				val iv = Integer.parseInt(sv)
				if(iv >= 100) {
					column_w_min_dp = iv
				}
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			
		}
		
		val dm = resources.displayMetrics
		
		val screen_width = dm.widthPixels
		
		val density = dm.density
		var column_w_min = (0.5f + column_w_min_dp * density).toInt()
		if(column_w_min < 1) column_w_min = 1
		
		var column_w : Int
		
		if(screen_width < column_w_min * 2) {
			// 最小幅で2つ表示できないのなら1カラム表示
			column_w = screen_width
		} else {
			
			// カラム最小幅から計算した表示カラム数
			nScreenColumn = screen_width / column_w_min
			if(nScreenColumn < 1) nScreenColumn = 1
			
			// データのカラム数より大きくならないようにする
			// (でも最小は1)
			val column_count = app_state.column_list.size
			if(column_count > 0 && column_count < nScreenColumn) {
				nScreenColumn = column_count
			}
			
			// 表示カラム数から計算したカラム幅
			column_w = screen_width / nScreenColumn
			
			// 最小カラム幅の1.5倍よりは大きくならないようにする
			val column_w_max = (0.5f + column_w_min * 1.5f).toInt()
			if(column_w > column_w_max) {
				column_w = column_w_max
			}
		}
		
		nColumnWidth = column_w // dividerの幅を含む
		
		val divider_width = (0.5f + 1f * density).toInt()
		column_w -= divider_width
		env.tablet_pager_adapter.columnWidth = column_w // dividerの幅を含まない
		// env.tablet_snap_helper.columnWidth = column_w //使われていない
		
		resizeAutoCW(column_w) // dividerの幅を含まない
		
		// 並べ直す
		env.tablet_pager_adapter.notifyDataSetChanged()
	}
	
	private fun scrollToColumn(index : Int, smoothScroll : Boolean = true) {
		scrollColumnStrip(index)
		phoneTab(
			
			// スマホはスムーススクロール基本ありだがたまにしない
			{ env -> env.pager.setCurrentItem(index, smoothScroll) },
			
			// タブレットでスムーススクロールさせると頻繁にオーバーランするので絶対しない
			{ env -> env.tablet_pager.scrollToPosition(index) }
		)
	}
	
	//////////////////////////////////////////////////////////////////////////////////////////////
	
	private fun importAppData(uri : Uri?) {
		uri ?: return
		
		// remove all columns
		run {
			phoneOnly { env -> env.pager.adapter = null }
			
			for(c in app_state.column_list) {
				c.dispose()
			}
			app_state.column_list.clear()
			
			phoneTab(
				{ env -> env.pager.adapter = env.pager_adapter },
				{ env -> resizeColumnWidth(env) }
			)
			
			
			updateColumnStrip()
		}
		
		@Suppress("DEPRECATION")
		val progress = ProgressDialogEx(this)
		
		val task = @SuppressLint("StaticFieldLeak") object :
			AsyncTask<Void, String, ArrayList<Column>?>() {
			
			fun setProgressMessage(sv : String) {
				runOnMainLooper { progress.setMessage(sv) }
			}
			
			override fun doInBackground(vararg params : Void) : ArrayList<Column>? {
				
				var newColumnList : ArrayList<Column>? = null
				
				try {
					
					setProgressMessage("import data to local storage...")
					
					// アプリ内領域に一時ファイルを作ってコピーする
					val cacheDir = cacheDir
					cacheDir.mkdir()
					val file = File(
						cacheDir,
						"SubwayTooter.${android.os.Process.myPid()}.${android.os.Process.myTid()}.tmp"
					)
					val source = contentResolver.openInputStream(uri)
					if(source == null) {
						showToast(this@ActMain, true, "openInputStream failed.")
						return null
					}
					source.use { inStream ->
						FileOutputStream(file).use { outStream ->
							IOUtils.copy(inStream, outStream)
						}
					}
					
					// 通知サービスを止める
					setProgressMessage("syncing notification poller…")
					PollingWorker.queueAppDataImportBefore(this@ActMain)
					while(PollingWorker.mBusyAppDataImportBefore.get()) {
						Thread.sleep(1000L)
						log.d("syncing polling task...")
					}
					
					// データを読み込む
					setProgressMessage("reading app data...")
					var zipEntryCount = 0
					try {
						ZipInputStream(FileInputStream(file)).use { zipStream ->
							while(true) {
								val entry = zipStream.nextEntry ?: break
								++ zipEntryCount
								try {
									//
									val entryName = entry.name
									if(entryName.endsWith(".json")) {
										newColumnList = AppDataExporter.decodeAppData(
											this@ActMain,
											JsonReader(InputStreamReader(zipStream, "UTF-8"))
										)
										continue
									}
									
									if(AppDataExporter.restoreBackgroundImage(
											this@ActMain,
											newColumnList,
											zipStream,
											entryName
										)
									) {
										continue
									}
								} finally {
									zipStream.closeEntry()
								}
							}
						}
					} catch(ex : Throwable) {
						log.trace(ex)
						if(zipEntryCount != 0) {
							showToast(this@ActMain, ex, "importAppData failed.")
						}
					}
					// zipではなかった場合、zipEntryがない状態になる。例外はPH-1では出なかったが、出ても問題ないようにする。
					if(zipEntryCount == 0) {
						InputStreamReader(FileInputStream(file), "UTF-8").use { inStream ->
							newColumnList = AppDataExporter.decodeAppData(
								this@ActMain,
								JsonReader(inStream)
							)
						}
					}
					
				} catch(ex : Throwable) {
					log.trace(ex)
					showToast(this@ActMain, ex, "importAppData failed.")
				}
				
				return newColumnList
			}
			
			override fun onCancelled(result : ArrayList<Column>?) {
				onPostExecute(result)
			}
			
			override fun onPostExecute(result : ArrayList<Column>?) {
				
				progress.dismissSafe()
				
				try {
					window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
				} catch(ignored : Throwable) {
				}
				
				try {
					if(isCancelled || result == null) {
						// cancelled.
						return
					}
					
					run {
						
						phoneOnly { env -> env.pager.adapter = null }
						
						app_state.column_list.clear()
						app_state.column_list.addAll(result)
						app_state.saveColumnList()
						
						phoneTab(
							{ env -> env.pager.adapter = env.pager_adapter },
							{ env -> resizeColumnWidth(env) }
						)
						updateColumnStrip()
					}
				} finally {
					// 通知サービスをリスタート
					PollingWorker.queueAppDataImportAfter(this@ActMain)
				}
			}
		}
		
		try {
			window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		} catch(ignored : Throwable) {
		}
		
		progress.isIndeterminate = true
		progress.setCancelable(false)
		progress.setOnCancelListener { task.cancel(true) }
		progress.show()
		task.executeOnExecutor(App1.task_executor)
	}
	
	override fun onDrawerSlide(drawerView : View, slideOffset : Float) {
		post_helper.closeAcctPopup()
	}
	
	override fun onDrawerOpened(drawerView : View) {
		post_helper.closeAcctPopup()
	}
	
	override fun onDrawerClosed(drawerView : View) {
		post_helper.closeAcctPopup()
	}
	
	override fun onDrawerStateChanged(newState : Int) {
		post_helper.closeAcctPopup()
	}
	
	private fun resizeAutoCW(column_w : Int) {
		val sv = Pref.spAutoCWLines(pref)
		nAutoCwLines = sv.optInt() ?: - 1
		if(nAutoCwLines > 0) {
			val lv_pad = (0.5f + 12 * density).toInt()
			val icon_width = avatarIconSize
			val icon_end = (0.5f + 4 * density).toInt()
			nAutoCwCellWidth = column_w - lv_pad * 2 - icon_width - icon_end
		}
		// この後各カラムは再描画される
	}
	
	fun checkAutoCW(status : TootStatus, text : CharSequence) {
		if(nAutoCwCellWidth <= 0) {
			// 設定が無効
			status.auto_cw = null
			return
		}
		
		var auto_cw = status.auto_cw
		if(auto_cw != null && auto_cw.refActivity?.get() === this@ActMain && auto_cw.cell_width == nAutoCwCellWidth) {
			// 以前に計算した値がまだ使える
			return
		}
		
		if(auto_cw == null) {
			auto_cw = TootStatus.AutoCW()
			status.auto_cw = auto_cw
		}
		
		// 計算時の条件(文字フォント、文字サイズ、カラム幅）を覚えておいて、再利用時に同じか確認する
		auto_cw.refActivity = WeakReference(this@ActMain)
		auto_cw.cell_width = nAutoCwCellWidth
		auto_cw.decoded_spoiler_text = null
		
		// テキストをレイアウトして行数を測定
		
		val lp = LinearLayout.LayoutParams(nAutoCwCellWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
		val tv = TextView(this)
		tv.layoutParams = lp
		if(! timeline_font_size_sp.isNaN()) {
			tv.textSize = timeline_font_size_sp
		}
		tv.typeface = timeline_font
		tv.text = text
		tv.measure(
			View.MeasureSpec.makeMeasureSpec(nAutoCwCellWidth, View.MeasureSpec.EXACTLY),
			View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
		)
		val l = tv.layout
		if(l != null) {
			auto_cw.originalLineCount = l.lineCount
			val line_count = auto_cw.originalLineCount
			
			if((nAutoCwLines > 0 && line_count > nAutoCwLines)
				&& status.spoiler_text.isEmpty()
				&& (status.mentions?.size ?: 0) <= nAutoCwLines
			) {
				val sb = SpannableStringBuilder()
				sb.append(getString(R.string.auto_cw_prefix))
				sb.append(text, 0, l.getLineEnd(nAutoCwLines - 1))
				var last = sb.length
				while(last > 0) {
					val c = sb[last - 1]
					if(c == '\n' || Character.isWhitespace(c)) {
						-- last
						continue
					}
					break
				}
				if(last < sb.length) {
					sb.delete(last, sb.length)
				}
				sb.append('…')
				auto_cw.decoded_spoiler_text = sb
			}
		}
	}
	
	private var dlgPrivacyPolicy : WeakReference<Dialog>? = null
	
	private fun checkPrivacyPolicy() {
		
		// 既に表示中かもしれない
		if(dlgPrivacyPolicy?.get()?.isShowing == true) return
		
		val res_id = when(getString(R.string.language_code)) {
			"ja" -> R.raw.privacy_policy_ja
			"fr" -> R.raw.privacy_policy_fr
			else -> R.raw.privacy_policy_en
		}
		
		// プライバシーポリシーデータの読み込み
		val bytes = loadRawResource(res_id)
		if(bytes.isEmpty()) return
		
		// 同意ずみなら表示しない
		val digest = bytes.digestSHA256().encodeBase64Url()
		if(digest == Pref.spAgreedPrivacyPolicyDigest(pref)) return
		
		val dialog = AlertDialog.Builder(this)
			.setTitle(R.string.privacy_policy)
			.setMessage(bytes.decodeUTF8())
			.setNegativeButton(R.string.cancel) { _, _ ->
				finish()
			}
			.setOnCancelListener {
				finish()
			}
			.setPositiveButton(R.string.agree) { _, _ ->
				pref.edit().put(Pref.spAgreedPrivacyPolicyDigest, digest).apply()
			}
			.create()
		dlgPrivacyPolicy = WeakReference(dialog)
		dialog.show()
	}
	
	class MyMenuItem(val title : Int, val icon : Int, val action : () -> Unit = {})
	
	val sideMenuContents = ArrayList<MyMenuItem>().apply {
		fun add(title : Int = 0, icon : Int = 0, action : () -> Unit = {}) {
			add(MyMenuItem(title, icon, action))
		}
		
		add(title = R.string.account)
		
		add(title = R.string.account_add, icon = R.drawable.ic_account_add) {
			Action_Account.add(this@ActMain)
		}
		
		/* android:id="@+id/nav_account_setting" */
		add(icon = R.drawable.ic_settings, title = R.string.account_setting) {
			Action_Account.setting(this@ActMain)
		}
		
		add()
		add(title = R.string.column)
		/* android:id="@+id/nav_column_list" */
		add(icon = R.drawable.ic_list_numbered, title = R.string.column_list) {
			Action_App.columnList(this@ActMain)
		}
		/* android:id="@+id/nav_close_all_columns" */
		add(
			icon = R.drawable.ic_close, title = R.string.close_all_columns
		) {
			closeColumnAll()
		}
		/* android:id="@+id/nav_add_tl_home" */
		
		add(icon = R.drawable.ic_home, title = R.string.home) {
			Action_Account.timeline(
				this@ActMain
				, defaultInsertPosition
				, ColumnType.HOME
				, bAllowPseudo = false
			)
		}
		/* android:id="@+id/nav_add_notifications" */
		add(icon = R.drawable.ic_announcement, title = R.string.notifications) {
			Action_Account.timeline(
				this@ActMain
				, defaultInsertPosition
				, ColumnType.NOTIFICATIONS
				, bAllowPseudo = false
			)
		}
		
		/* android:id="@+id/nav_add_direct_message" */
		add(icon = R.drawable.ic_mail, title = R.string.direct_messages) {
			Action_Account.timeline(
				this@ActMain
				, defaultInsertPosition
				, ColumnType.DIRECT_MESSAGES
				, bAllowPseudo = false
				, bAllowMisskey = false
			)
		}
		
		/* android:id="@+id/nav_add_tl_misskey_hybrid" */
		add(icon = R.drawable.ic_share, title = R.string.misskey_hybrid_timeline_long) {
			Action_Account.timeline(
				this@ActMain
				, defaultInsertPosition
				, ColumnType.MISSKEY_HYBRID
				, bAllowPseudo = true
				, bAllowMastodon = false
			)
		}
		
		/* android:id="@+id/nav_add_tl_local" */
		add(icon = R.drawable.ic_run, title = R.string.local_timeline) {
			Action_Account.timeline(
				this@ActMain
				, defaultInsertPosition
				, ColumnType.LOCAL
				, bAllowPseudo = true
			)
		}
		
		/* android:id="@+id/nav_add_tl_federate" */
		add(icon = R.drawable.ic_bike, title = R.string.federate_timeline) {
			Action_Account.timeline(
				this@ActMain
				, defaultInsertPosition
				, ColumnType.FEDERATE
				, bAllowPseudo = true
			)
		}
		
		/* android:id="@+id/nav_add_list" */
		add(icon = R.drawable.ic_list_list, title = R.string.lists) {
			Action_Account.timeline(
				this@ActMain
				, defaultInsertPosition
				, ColumnType.LIST_LIST
				, bAllowPseudo = false
			)
		}
		
		/* android:id="@+id/nav_add_tl_search" */
		add(icon = R.drawable.ic_search, title = R.string.search) {
			Action_Account.timeline(
				this@ActMain
				, defaultInsertPosition
				, ColumnType.SEARCH
				, bAllowPseudo = false
				, args = arrayOf("", false)
			)
		}
		
		/* android:id="@+id/nav_trend_tag" */
		add(icon = R.drawable.ic_hashtag, title = R.string.trend_tag) {
			Action_Account.timeline(
				this@ActMain,
				defaultInsertPosition,
				ColumnType.TREND_TAG,
				bAllowPseudo = true,
				bAllowMastodon = true,
				bAllowMisskey = false
			)
		}
		/* android:id="@+id/nav_add_favourites" */
		add(icon = R.drawable.ic_star, title = R.string.favourites) {
			Action_Account.timeline(
				this@ActMain
				, defaultInsertPosition
				, ColumnType.FAVOURITES
				, bAllowPseudo = false
			)
		}
		
		/* android:id="@+id/nav_add_statuses" */
		add(icon = R.drawable.ic_account_box, title = R.string.profile) {
			Action_Account.timeline(
				this@ActMain
				, defaultInsertPosition
				, ColumnType.PROFILE
				, bAllowPseudo = false
			)
		}
		
		/* android:id="@+id/nav_follow_requests" */
		add(icon = R.drawable.ic_follow_wait, title = R.string.follow_requests) {
			Action_Account.timeline(
				this@ActMain
				, defaultInsertPosition
				, ColumnType.FOLLOW_REQUESTS
				, bAllowPseudo = false
			)
		}
		
		/* android:id="@+id/nav_follow_suggestion" */
		add(icon = R.drawable.ic_follow_plus, title = R.string.follow_suggestion) {
			Action_Account.timeline(
				this@ActMain
				, defaultInsertPosition
				, ColumnType.FOLLOW_SUGGESTION
				, bAllowPseudo = false
			)
		}
		/* android:id="@+id/nav_endorsement" */
		add(icon = R.drawable.ic_follow_plus, title = R.string.endorse_set) {
			Action_Account.timeline(
				this@ActMain
				, defaultInsertPosition
				, ColumnType.ENDORSEMENT
				, bAllowPseudo = false
				, bAllowMisskey = false
			)
		}
		/* android:id="@+id/nav_add_mutes" */
		add(icon = R.drawable.ic_volume_off, title = R.string.muted_users) {
			Action_Account.timeline(
				this@ActMain
				, defaultInsertPosition
				, ColumnType.MUTES
				, bAllowPseudo = false
			)
		}
		
		/* android:id="@+id/nav_add_blocks" */
		add(icon = R.drawable.ic_block, title = R.string.blocked_users) {
			Action_Account.timeline(
				this@ActMain
				, defaultInsertPosition
				, ColumnType.BLOCKS
				, bAllowPseudo = false
			)
		}
		
		/* android:id="@+id/nav_keyword_filter" */
		add(icon = R.drawable.ic_volume_off, title = R.string.keyword_filters) {
			Action_Account.timeline(
				this@ActMain
				, defaultInsertPosition
				, ColumnType.KEYWORD_FILTER
				, bAllowPseudo = false
				, bAllowMisskey = false
			)
		}
		
		/* android:id="@+id/nav_add_domain_blocks" */
		add(icon = R.drawable.ic_cloud_off, title = R.string.blocked_domains) {
			Action_Account.timeline(
				this@ActMain
				, defaultInsertPosition
				, ColumnType.DOMAIN_BLOCKS
				, bAllowPseudo = false
				, bAllowMisskey = false
			)
		}
		
		/* android:id="@+id/nav_scheduled_statuses_list" */
		add(icon = R.drawable.ic_timer, title = R.string.scheduled_status_list) {
			Action_Account.timeline(
				this@ActMain
				, defaultInsertPosition
				, ColumnType.SCHEDULED_STATUS
				, bAllowPseudo = false
				, bAllowMisskey = false
			)
		}
		
		
		add()
		add(title = R.string.toot_search)
		
		add(icon = R.drawable.ic_search, title = R.string.tootsearch) {
			addColumn(
				defaultInsertPosition
				, SavedAccount.na
				, ColumnType.SEARCH_TS
				, ""
			)
		}
		
		add()
		add(title = R.string.setting)
		
		add(icon = R.drawable.ic_settings, title = R.string.app_setting) {
			ActAppSetting.open(this@ActMain, REQUEST_CODE_APP_SETTING)
		}
		
		add(icon = R.drawable.ic_settings, title = R.string.highlight_word) {
			startActivity(Intent(this@ActMain, ActHighlightWordList::class.java))
		}
		
		add(icon = R.drawable.ic_volume_off, title = R.string.muted_app) {
			startActivity(Intent(this@ActMain, ActMutedApp::class.java))
		}
		
		add(icon = R.drawable.ic_volume_off, title = R.string.muted_word) {
			startActivity(Intent(this@ActMain, ActMutedWord::class.java))
		}
		
		add(icon = R.drawable.ic_volume_off, title = R.string.fav_muted_user) {
			startActivity(Intent(this@ActMain, ActFavMute::class.java))
		}
		
		add(
			icon = R.drawable.ic_volume_off,
			title = R.string.muted_users_from_pseudo_account
		) {
			startActivity(Intent(this@ActMain, ActMutedPseudoAccount::class.java))
		}
		
		add(icon = R.drawable.ic_info, title = R.string.app_about) {
			startActivityForResult(Intent(this@ActMain, ActAbout::class.java), REQUEST_APP_ABOUT)
		}
		
		add(icon = R.drawable.ic_info, title = R.string.oss_license) {
			startActivity(Intent(this@ActMain, ActOSSLicense::class.java))
		}
		
		add(icon = R.drawable.ic_hot_tub, title = R.string.app_exit) {
			finish()
		}
		
	}
	
	inner class SideMenuAdapter : BaseAdapter() {
		
		private val iconColor = getAttributeColor(this@ActMain, R.attr.colorTimeSmall)
		
		override fun getCount() : Int =
			sideMenuContents.size
		
		override fun getItem(position : Int) : Any =
			sideMenuContents[position]
		
		override fun getItemId(position : Int) : Long = 0L
		
		override fun getViewTypeCount() : Int = 3
		
		override fun getItemViewType(position : Int) : Int {
			val item = sideMenuContents[position]
			return when {
				item.title == 0 -> 0
				item.icon == 0 -> 1
				else -> 2
			}
		}
		
		private inline fun <reified T : View> viewOrInflate(
			view : View?,
			parent : ViewGroup?,
			resId : Int
		) : T {
			val v = view ?: layoutInflater.inflate(resId, parent, false)
			return if(v is T) v else error("invalid view type! $v")
		}
		
		override fun getView(position : Int, view : View?, parent : ViewGroup?) : View {
			
			val item = sideMenuContents[position]
			return when {
				item.title == 0 -> viewOrInflate(view, parent, R.layout.lv_sidemenu_separator)
				
				item.icon == 0 -> viewOrInflate<TextView>(view, parent, R.layout.lv_sidemenu_group)
					.apply {
						text = getString(item.title)
						
					}
				else -> viewOrInflate<TextView>(view, parent, R.layout.lv_sidemenu_item)
					.apply {
						isAllCaps = false
						text = getString(item.title)
						val drawable = createColoredDrawable(this@ActMain, item.icon, iconColor, 1f)
						setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null)
						
						setOnClickListener {
							item.action()
							drawer.closeDrawer(GravityCompat.START)
						}
					}
			}
		}
	}
	
	private fun initSideMenu(navigationView : NavigationView) {
		
		navigationView.addView(ListView(this).apply {
			adapter = SideMenuAdapter()
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT
			)
			backgroundColor = getAttributeColor(this@ActMain, R.attr.colorWindowBackground)
			selector = StateListDrawable()
			divider = null
			dividerHeight = 0
			
			val pad_tb = (density *12f + 0.5f) .toInt()
			setPadding(0,pad_tb,0,pad_tb)
			clipToPadding = false
			scrollBarStyle =ListView.SCROLLBARS_OUTSIDE_OVERLAY
		})
	}
}
