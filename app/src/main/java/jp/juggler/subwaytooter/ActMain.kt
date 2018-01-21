package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.support.v4.view.ViewCompat
import android.support.v4.view.ViewPager
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.InputType
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.util.JsonReader
import android.view.Gravity
import android.view.View
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import jp.juggler.subwaytooter.action.*
import jp.juggler.subwaytooter.api.*

import org.apache.commons.io.IOUtils

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.HashSet
import java.util.regex.Pattern

import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.dialog.AccountPicker
import jp.juggler.subwaytooter.dialog.DlgTextInput
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.dialog.ProgressDialogEx
import jp.juggler.subwaytooter.view.ColumnStripLinearLayout
import jp.juggler.subwaytooter.view.GravitySnapHelper
import jp.juggler.subwaytooter.view.MyEditText

import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.span.MyClickableSpanClickCallback
import jp.juggler.subwaytooter.util.*

class ActMain : AppCompatActivity()
	, NavigationView.OnNavigationItemSelectedListener
	, View.OnClickListener
	, ViewPager.OnPageChangeListener
	, Column.Callback
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
		
		internal var sent_intent2 : Intent? = null
		
		@Suppress("HasPlatformType")
		val reUrlHashTag =
			Pattern.compile("\\Ahttps://([^/]+)/tags/([^?#・\\s\\-+.,:;/]+)(?:\\z|[?#])")
		
		@Suppress("HasPlatformType")
		val reUserPage = Pattern.compile("\\Ahttps://([^/]+)/@([A-Za-z0-9_]+)(?:\\z|[?#])")
		
		@Suppress("HasPlatformType")
		val reStatusPage = Pattern.compile("\\Ahttps://([^/]+)/@([A-Za-z0-9_]+)/(\\d+)(?:\\z|[?#])")
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
	private var posted_status_id : Long = 0
	
	var timeline_font_size_sp = Float.NaN
	var acct_font_size_sp = Float.NaN
	
	internal var bStart : Boolean = false
	
	// 画面上のUI操作で生成されて
	// onPause,onPageDestroy 等のタイミングで閉じられる
	// 状態保存の必要なし
	var listItemPopup : StatusButtonsPopup? = null
	
	private lateinit var llEmpty : View
	internal lateinit var drawer : DrawerLayout
	private lateinit var llColumnStrip : ColumnStripLinearLayout
	private lateinit var svColumnStrip : HorizontalScrollView
	private lateinit var btnMenu : ImageButton
	private lateinit var btnToot : ImageButton
	private lateinit var vFooterDivider1 : View
	private lateinit var vFooterDivider2 : View
	
	val viewPool = RecyclerView.RecycledViewPool()
	
	var timeline_font : Typeface? = null
	var timeline_font_bold : Typeface? = null
	var avatarIconSize : Int = 0
	
	private lateinit var llQuickTootBar : View
	private lateinit var etQuickToot : MyEditText
	private lateinit var btnQuickToot : ImageButton
	lateinit var post_helper : PostHelper
	
	class PhoneEnv {
		internal lateinit var pager : ViewPager
		internal lateinit var pager_adapter : ColumnPagerAdapter
	}
	
	class TabletEnv {
		internal lateinit var tablet_pager : RecyclerView
		internal lateinit var tablet_pager_adapter : TabletColumnPagerAdapter
		internal lateinit var tablet_layout_manager : LinearLayoutManager
		internal lateinit var tablet_snap_helper : GravitySnapHelper
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
		
		while(true) {
			val tag = view.tag
			if(tag is ItemViewHolder) {
				column = tag.column
				break
			} else if(tag is ViewHolderItem) {
				column = tag.ivh.column
				break
			} else if(tag is ViewHolderHeaderBase) {
				column = tag.column
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
							if(s.text.startsWith("#")) s.text else "#" + Uri.decode(m.group(2))
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
			tagList = tag_list
		).open()
	}
	
	////////////////////////////////////////////////////////////////////////////
	
	val follow_complete_callback : EmptyCallback = {
		showToast(this@ActMain, false, R.string.follow_succeeded)
	}
	
	val unfollow_complete_callback : EmptyCallback = {
		showToast(this@ActMain, false, R.string.unfollow_succeeded)
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
	
	private var nScreenColumn : Int = 0
	private var nColumnWidth : Int = 0
	
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
			},
			{ _ ->
				val db_id = Pref.lpTabletTootDefaultAccount(App1.pref)
				val a = SavedAccount.loadAccount(this@ActMain, db_id)
				return a?.db_id ?: - 1L
			}
		)
	
	// スマホモードなら現在のカラムを、タブレットモードなら-1Lを返す
	// (カラム一覧画面のデフォルト選択位置に使われる)
	val currentColumn : Int
		get() = phoneTab(
			{ pe -> pe.pager.currentItem },
			{ _ -> - 1 }
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
			{ pe -> pe.pager.currentItem + 1 },
			{ _ -> Integer.MAX_VALUE }
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
		
		initUI()
		
		updateColumnStrip()
		
		if(! app_state.column_list.isEmpty()) {
			
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
		
		super.onStop()
		
	}
	
	override fun onResume() {
		super.onResume()
		log.d("onResume")
		
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
		
		// 最後に表示していたカラムの位置
		val last_pos = phoneTab(
			{ env -> env.pager.currentItem },
			{ env -> env.tablet_layout_manager.findFirstVisibleItemPosition() })
		
		pref.edit().put(Pref.ipLastColumnPos, last_pos).apply()
		
		super.onPause()
	}
	
	private fun refreshAfterPost() {
		if(posted_acct?.isNotEmpty() == true) {
			val refresh_after_toot = Pref.ipRefreshAfterToot(pref)
			if(refresh_after_toot != Pref.RAT_DONT_REFRESH) {
				for(column in app_state.column_list) {
					val a = column.access_info
					if(a.acct == posted_acct) {
						column.startRefreshForPost(posted_status_id, refresh_after_toot)
					}
				}
			}
			posted_acct = null
		}
	}
	
	private fun handleSentIntent(intent : Intent) {
		sent_intent2 = intent
		AccountPicker.pick(
			this,
			bAllowPseudo = false,
			bAuto = true,
			message = getString(R.string.account_picker_toot)
			, dismiss_callback = { sent_intent2 = null }
		) { ai ->
			sent_intent2 = null
			ActPost.open(this@ActMain, REQUEST_CODE_POST, ai.db_id, intent)
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
			R.id.btnMenu -> if(! drawer.isDrawerOpen(Gravity.START)) {
				drawer.openDrawer(Gravity.START)
			}
			
			R.id.btnToot -> Action_Account.openPost(this@ActMain)
			
			R.id.btnQuickToot -> performQuickPost(null)
		}
	}
	
	private fun performQuickPost(account : SavedAccount?) {
		if(account == null) {
			phoneTab({ env ->
				// スマホモードなら表示中のカラムがあればそれで
				val c = app_state.column_list[env.pager.currentItem]
				// 表示中のカラムは疑似アカウントかもしれない
				if(! c.access_info.isPseudo) {
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
			}, { _ ->
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
		post_helper.visibility = account.visibility
		post_helper.bNSFW = false
		post_helper.in_reply_to_id = - 1L
		post_helper.attachment_list = null
		
		etQuickToot.hideKeyboard()
		
		post_helper.post(
			account
			, false
			, false
		) { target_account, status ->
			etQuickToot.setText("")
			posted_acct = target_account.acct
			posted_status_id = status.id
			refreshAfterPost()
		}
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
				post_helper.setInstance(if(column.access_info.isNA) null else column.access_info.host)
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
		log.d("onActivityResult")
		if(resultCode == Activity.RESULT_OK) {
			if(requestCode == REQUEST_CODE_COLUMN_LIST) {
				if(data != null) {
					val order = data.getIntegerArrayListExtra(ActColumnList.EXTRA_ORDER)
					if(order != null && isOrderChanged(order)) {
						setOrder(order)
					}
					
					if(! app_state.column_list.isEmpty()) {
						val select = data.getIntExtra(ActColumnList.EXTRA_SELECTION, - 1)
						if(0 <= select && select < app_state.column_list.size) {
							scrollToColumn(select)
						}
					}
				}
				
			} else if(requestCode == REQUEST_APP_ABOUT) {
				if(data != null) {
					val search = data.getStringExtra(ActAbout.EXTRA_SEARCH)
					if(search?.isNotEmpty() == true) {
						Action_Account.timeline(
							this@ActMain,
							defaultInsertPosition,
							true,
							Column.TYPE_SEARCH,
							search,
							true
						)
					}
					return
				}
			} else if(requestCode == REQUEST_CODE_NICKNAME) {
				
				updateColumnStrip()
				
				for(column in app_state.column_list) {
					column.fireShowColumnHeader()
				}
				
			} else if(requestCode == REQUEST_CODE_POST) {
				if(data != null) {
					etQuickToot.setText("")
					posted_acct = data.getStringExtra(ActPost.EXTRA_POSTED_ACCT)
					posted_status_id = data.getLongExtra(ActPost.EXTRA_POSTED_STATUS_ID, 0L)
				}
				
			} else if(requestCode == REQUEST_CODE_COLUMN_COLOR) {
				if(data != null) {
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
		
		if(requestCode == REQUEST_CODE_ACCOUNT_SETTING) {
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
		} else if(requestCode == REQUEST_CODE_APP_SETTING) {
			showFooterColor()
			
			if(resultCode == RESULT_APP_DATA_IMPORT) {
				if(data != null) {
					importAppData(data.data)
				}
			}
			
		} else if(requestCode == REQUEST_CODE_TEXT) {
			if(resultCode == ActText.RESULT_SEARCH_MSP) {
				val text = data?.getStringExtra(Intent.EXTRA_TEXT)
				addColumn(
					defaultInsertPosition,
					SavedAccount.na,
					Column.TYPE_SEARCH_MSP,
					text ?: ""
				)
			} else if(resultCode == ActText.RESULT_SEARCH_TS) {
				val text = data?.getStringExtra(Intent.EXTRA_TEXT)
				addColumn(defaultInsertPosition, SavedAccount.na, Column.TYPE_SEARCH_TS, text ?: "")
			}
		}
		
		super.onActivityResult(requestCode, resultCode, data)
	}
	
	override fun onBackPressed() {
		
		// メニューが開いていたら閉じる
		val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
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
		
		// カラムが1個以上ある場合は設定に合わせて挙動を変える
		when(Pref.ipBackButtonAction(pref)) {
			ActAppSetting.BACK_ASK_ALWAYS -> {
				val dialog = ActionsDialog()
				
				val add_column_closer = { current_column : Column ->
					if(! current_column.dont_close) {
						dialog.addAction(getString(R.string.close_column)) {
							closeColumn(
								true,
								current_column
							)
						}
					}
					
				}
				phoneTab({ env ->
					app_state.column_list[env.pager.currentItem].let(add_column_closer)
				}, { env ->
					val vs = env.tablet_layout_manager.findFirstVisibleItemPosition()
					val ve = env.tablet_layout_manager.findLastVisibleItemPosition()
					if(vs == ve && vs != RecyclerView.NO_POSITION) {
						app_state.column_list[vs].let(add_column_closer)
					}
				})
				
				dialog.addAction(getString(R.string.open_column_list)) { Action_App.columnList(this@ActMain) }
				
				dialog.addAction(getString(R.string.app_exit)) { this@ActMain.finish() }
				dialog.show(this, null)
			}
			
			ActAppSetting.BACK_CLOSE_COLUMN -> {
				
				val closer = { column : Column ->
					if(column.dont_close
						&& Pref.bpExitAppWhenCloseProtectedColumn(pref)
						&& Pref.bpDontConfirmBeforeCloseColumn(pref)
					) {
						this@ActMain.finish()
					} else {
						closeColumn(false, column)
					}
				}
				
				phoneTab({ env ->
					env.pager_adapter.getColumn(env.pager.currentItem)?.let(closer)
				}, { env ->
					val vs = env.tablet_layout_manager.findFirstVisibleItemPosition()
					val ve = env.tablet_layout_manager.findLastVisibleItemPosition()
					if(vs == ve && vs != RecyclerView.NO_POSITION) {
						app_state.column_list[vs].let(closer)
					} else {
						showToast(
							this,
							false,
							getString(R.string.cant_close_column_by_back_button_when_multiple_column_shown)
						)
					}
				})
			}
			
			ActAppSetting.BACK_EXIT_APP -> this@ActMain.finish()
			
			ActAppSetting.BACK_OPEN_COLUMN_LIST -> Action_App.columnList(this@ActMain)
			
			else -> {
				val dialog = ActionsDialog()
				val add_close_column = { current_column : Column ->
					if(! current_column.dont_close) {
						dialog.addAction(getString(R.string.close_column)) {
							closeColumn(
								true,
								current_column
							)
						}
					}
				}
				phoneTab({ env ->
					app_state.column_list[env.pager.currentItem].let(add_close_column)
				}, { env ->
					val vs = env.tablet_layout_manager.findFirstVisibleItemPosition()
					val ve = env.tablet_layout_manager.findLastVisibleItemPosition()
					if(vs == ve && vs != RecyclerView.NO_POSITION) {
						app_state.column_list[vs].let(add_close_column)
					}
				})
				dialog.addAction(getString(R.string.open_column_list)) { Action_App.columnList(this@ActMain) }
				dialog.addAction(getString(R.string.app_exit)) { this@ActMain.finish() }
				dialog.show(this, null)
			}
		}
	}
	
	override fun onCreateOptionsMenu(menu : Menu) : Boolean {
		// Inflate the menu; this adds items to the action bar if it is present.
		menuInflater.inflate(R.menu.act_main, menu)
		return true
	}
	
	override fun onOptionsItemSelected(item : MenuItem) : Boolean {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		val id = item.itemId
		
		
		return if(id == R.id.action_settings) {
			true
		} else super.onOptionsItemSelected(item)
		
	}
	
	// Handle navigation view item clicks here.
	override fun onNavigationItemSelected(item : MenuItem) : Boolean {
		val id = item.itemId
		
		
		when(id) {
		// アカウント
			R.id.nav_account_add -> Action_Account.add(this)
			R.id.nav_account_setting -> Action_Account.setting(this)
		
		// カラム
			R.id.nav_column_list -> Action_App.columnList(this)
			R.id.nav_add_tl_home -> Action_Account.timeline(
				this,
				defaultInsertPosition,
				false,
				Column.TYPE_HOME
			)
			R.id.nav_add_tl_local -> Action_Account.timeline(
				this,
				defaultInsertPosition,
				true,
				Column.TYPE_LOCAL
			)
			R.id.nav_add_tl_federate -> Action_Account.timeline(
				this,
				defaultInsertPosition,
				true,
				Column.TYPE_FEDERATE
			)
			R.id.nav_add_favourites -> Action_Account.timeline(
				this,
				defaultInsertPosition,
				false,
				Column.TYPE_FAVOURITES
			)
			R.id.nav_add_statuses -> Action_Account.timeline(
				this,
				defaultInsertPosition,
				false,
				Column.TYPE_PROFILE
			)
			R.id.nav_add_notifications -> Action_Account.timeline(
				this,
				defaultInsertPosition,
				false,
				Column.TYPE_NOTIFICATIONS
			)
			R.id.nav_add_tl_search -> Action_Account.timeline(
				this,
				defaultInsertPosition,
				false,
				Column.TYPE_SEARCH,
				"",
				false
			)
			R.id.nav_add_mutes -> Action_Account.timeline(
				this,
				defaultInsertPosition,
				false,
				Column.TYPE_MUTES
			)
			R.id.nav_add_blocks -> Action_Account.timeline(
				this,
				defaultInsertPosition,
				false,
				Column.TYPE_BLOCKS
			)
			R.id.nav_add_domain_blocks -> Action_Account.timeline(
				this,
				defaultInsertPosition,
				false,
				Column.TYPE_DOMAIN_BLOCKS
			)
			R.id.nav_add_list -> Action_Account.timeline(
				this,
				defaultInsertPosition,
				false,
				Column.TYPE_LIST_LIST
			)
			R.id.nav_follow_requests -> Action_Account.timeline(
				this,
				defaultInsertPosition,
				false,
				Column.TYPE_FOLLOW_REQUESTS
			)
		
		// トゥート検索
			R.id.mastodon_search_portal -> addColumn(
				defaultInsertPosition,
				SavedAccount.na,
				Column.TYPE_SEARCH_MSP,
				""
			)
			R.id.tootsearch -> addColumn(
				defaultInsertPosition,
				SavedAccount.na,
				Column.TYPE_SEARCH_TS,
				""
			)
		
		// 設定
			R.id.nav_app_setting -> ActAppSetting.open(this, REQUEST_CODE_APP_SETTING)
			R.id.nav_muted_app -> startActivity(Intent(this, ActMutedApp::class.java))
			R.id.nav_muted_word -> startActivity(Intent(this, ActMutedWord::class.java))
			R.id.nav_highlight_word -> startActivity(Intent(this, ActHighlightWordList::class.java))
			R.id.nav_app_about -> startActivityForResult(
				Intent(this, ActAbout::class.java),
				ActMain.REQUEST_APP_ABOUT
			)
			R.id.nav_oss_license -> startActivity(Intent(this, ActOSSLicense::class.java))
			R.id.nav_app_exit -> finish()
		}
		
		val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
		drawer.closeDrawer(GravityCompat.START)
		return true
	}
	
	internal fun initUI() {
		setContentView(R.layout.act_main)
		
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
			
		} else if(timeline_font != null) {
			try {
				timeline_font_bold = Typeface.create(timeline_font, Typeface.BOLD)
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			
		}
		
		
		run {
			var icon_size_dp = 48f
			try {
				sv = Pref.spAvatarIconSize(pref)
				val fv = if(sv.isEmpty()) Float.NaN else sv.toFloat()
				if(fv.isFinite() && fv >= 1f) {
					icon_size_dp = fv
				}
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			
			avatarIconSize = (0.5f + icon_size_dp * density).toInt()
		}
		
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
		
		
		llEmpty = findViewById(R.id.llEmpty)
		
		//		// toolbar
		//		Toolbar toolbar = (Toolbar) findViewById( R.id.toolbar );
		//		setSupportActionBar( toolbar );
		
		// navigation drawer
		drawer = findViewById(R.id.drawer_layout)
		//		ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
		//			this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close );
		drawer.addDrawerListener(this)
		//		toggle.syncState();
		
		val navigationView = findViewById<NavigationView>(R.id.nav_view)
		navigationView.setNavigationItemSelectedListener(this)
		
		btnMenu = findViewById(R.id.btnMenu)
		btnToot = findViewById(R.id.btnToot)
		vFooterDivider1 = findViewById(R.id.vFooterDivider1)
		vFooterDivider2 = findViewById(R.id.vFooterDivider2)
		llColumnStrip = findViewById(R.id.llColumnStrip)
		svColumnStrip = findViewById(R.id.svColumnStrip)
		llQuickTootBar = findViewById(R.id.llQuickTootBar)
		etQuickToot = findViewById(R.id.etQuickToot)
		btnQuickToot = findViewById(R.id.btnQuickToot)
		
		if(! Pref.bpQuickTootBar(pref)) {
			llQuickTootBar.visibility = View.GONE
		}
		
		btnToot.setOnClickListener(this)
		btnMenu.setOnClickListener(this)
		btnQuickToot.setOnClickListener(this)
		
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
				LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
			env.tablet_pager.adapter = env.tablet_pager_adapter
			env.tablet_pager.layoutManager = env.tablet_layout_manager
			env.tablet_pager.addOnScrollListener(object : RecyclerView.OnScrollListener() {
				override fun onScrollStateChanged(recyclerView : RecyclerView?, newState : Int) {
					super.onScrollStateChanged(recyclerView, newState)
					val vs = env.tablet_layout_manager.findFirstVisibleItemPosition()
					val ve = env.tablet_layout_manager.findLastVisibleItemPosition()
					// 端に近い方に合わせる
					val distance_left = Math.abs(vs)
					val distance_right = Math.abs(app_state.column_list.size - 1 - ve)
					if(distance_left < distance_right) {
						scrollColumnStrip(vs)
					} else {
						scrollColumnStrip(ve)
					}
				}
				
				override fun onScrolled(recyclerView : RecyclerView?, dx : Int, dy : Int) {
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
					return ! drawer.isDrawerOpen(Gravity.START)
				}
			})
	}
	
	internal fun updateColumnStrip() {
		llEmpty.visibility = if(app_state.column_list.isEmpty()) View.VISIBLE else View.GONE
		
		llColumnStrip.removeAllViews()
		for(i in 0 until app_state.column_list.size) {
			
			val column = app_state.column_list[i]
			
			val viewRoot = layoutInflater.inflate(R.layout.lv_column_strip, llColumnStrip, false)
			val ivIcon = viewRoot.findViewById<ImageView>(R.id.ivIcon)
			
			viewRoot.tag = i
			viewRoot.setOnClickListener { v -> scrollToColumn(v.tag as Int) }
			viewRoot.contentDescription = column.getColumnName(true)
			//
			
			var c = column.header_bg_color
			if(c == 0) {
				viewRoot.setBackgroundResource(R.drawable.btn_bg_ddd)
			} else {
				ViewCompat.setBackground(
					viewRoot, Styler.getAdaptiveRippleDrawable(
						c,
						if(column.header_fg_color != 0)
							column.header_fg_color
						else
							Styler.getAttributeColor(this, R.attr.colorRippleEffect)
					
					)
				)
			}
			
			c = column.header_fg_color
			if(c == 0) {
				Styler.setIconDefaultColor(this, ivIcon, column.getIconAttrId(column.column_type))
			} else {
				Styler.setIconCustomColor(this, ivIcon, c, column.getIconAttrId(column.column_type))
			}
			
			//
			val ac = AcctColor.load(column.access_info.acct)
			if(AcctColor.hasColorForeground(ac)) {
				val vAcctColor = viewRoot.findViewById<View>(R.id.vAcctColor)
				vAcctColor.setBackgroundColor(ac.color_fg)
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
					val first = env.tablet_layout_manager.findFirstVisibleItemPosition()
					var last = env.tablet_layout_manager.findLastVisibleItemPosition()
					
					if(last - first > nScreenColumn - 1) {
						last = first + nScreenColumn - 1
					}
					var slide_ratio = 0f
					if(first != RecyclerView.NO_POSITION && nColumnWidth > 0) {
						val child = env.tablet_layout_manager.findViewByPosition(first)
						slide_ratio = Math.abs(child.left / nColumnWidth.toFloat())
					}
					
					llColumnStrip.setVisibleRange(first, last, slide_ratio)
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
		val uri = data.data ?: return
		// ブラウザで開く
		try {
			val intent = Intent(Intent.ACTION_VIEW, uri)
			startActivity(intent)
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
	}
	
	// ActOAuthCallbackで受け取ったUriを処理する
	private fun handleIntentUri(uri : Uri) {
		
		if("subwaytooter" == uri.scheme) {
			try {
				handleOAuth2CallbackUri(uri)
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			
			return
		}
		
		val url = uri.toString()
		
		var m = reStatusPage.matcher(url)
		if(m.find()) {
			try {
				// https://mastodon.juggler.jp/@SubwayTooter/(status_id)
				val host = m.group(1)
				val status_id = m.group(3).toLong(10)
				// ステータスをアプリ内で開く
				Action_Toot.conversationOtherInstance(
					this@ActMain,
					defaultInsertPosition,
					uri.toString(),
					status_id,
					host,
					status_id
				)
			} catch(ex : Throwable) {
				showToast(this, ex, "can't parse status id.")
			}
			
			return
		}
		
		m = reUserPage.matcher(url)
		if(m.find()) {
			// https://mastodon.juggler.jp/@SubwayTooter
			// ユーザページをアプリ内で開く
			Action_User.profile(
				this@ActMain,
				defaultInsertPosition,
				null,
				uri.toString(),
				m.group(1),
				Uri.decode(m.group(2))
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
			val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://dummy.subwaytooter.club/"))
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
					val column =
						addColumn(defaultInsertPosition, account, Column.TYPE_NOTIFICATIONS)
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
		// subwaytooter://oauth/?...
		TootTaskRunner(this@ActMain).run(object : TootTask {
			
			internal var ta : TootAccount? = null
			internal var sa : SavedAccount? = null
			internal var host : String? = null
			
			override fun background(client : TootApiClient) : TootApiResult? {
				
				// エラー時
				// subwaytooter://oauth
				// ?error=access_denied
				// &error_description=%E3%83%AA%E3%82%BD%E3%83%BC%E3%82%B9%E3%81%AE%E6%89%80%E6%9C%89%E8%80%85%E3%81%BE%E3%81%9F%E3%81%AF%E8%AA%8D%E8%A8%BC%E3%82%B5%E3%83%BC%E3%83%90%E3%83%BC%E3%81%8C%E8%A6%81%E6%B1%82%E3%82%92%E6%8B%92%E5%90%A6%E3%81%97%E3%81%BE%E3%81%97%E3%81%9F%E3%80%82
				// &state=db%3A3
				val error = uri.getQueryParameter("error_description")
				if(error?.isNotEmpty() == true) {
					return TootApiResult(error)
				}
				
				// subwaytooter://oauth
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
				
				if(sv.startsWith("db:")) {
					try {
						val dataId = sv.substring(3).toLong(10)
						val sa = SavedAccount.loadAccount(this@ActMain, dataId)
							?: return TootApiResult("missing account db_id=" + dataId)
						this.sa = sa
						client.account = sa
					} catch(ex : Throwable) {
						log.trace(ex)
						return TootApiResult(ex.withCaption("invalid state"))
					}
					
				} else if(sv.startsWith("host:")) {
					val host = sv.substring(5)
					client.instance = host
				}
				
				val instance = client.instance
					?: return TootApiResult("missing instance  in callback url.")
				
				this.host = instance
				val client_name = Pref.spClientName(this@ActMain)
				val result = client.authentication2(client_name, code)
				this.ta = TootParser(this@ActMain, object : LinkHelper {})
					.account(result?.jsonObject)
				return result
			}
			
			override fun handleResult(result : TootApiResult?) {
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
			val row_id = SavedAccount.insert(host, user, jsonObject, token_info)
			val account = SavedAccount.loadAccount(this@ActMain, row_id)
			if(account != null) {
				var bModified = false
				if(account.loginAccount?.locked == true) {
					bModified = true
					account.visibility = TootStatus.VISIBILITY_PRIVATE
				}
				val source = ta.source
				if(source != null) {
					try {
						val privacy = source.privacy
						TootStatus.parseVisibility(privacy) // 失敗すると例外
						bModified = true
						account.visibility = privacy
					} catch(ignored : Throwable) {
						// privacyの値がおかしい
					}
					
					// XXX ta.source.sensitive パラメータを読んで「添付画像をデフォルトでNSFWにする」を実現する
					// 現在、アカウント設定にはこの項目はない( 「NSFWな添付メディアを隠さない」はあるが全く別の効果)
				}
				
				if(bModified) {
					account.saveSetting()
				}
				showToast(this@ActMain, false, R.string.account_confirmed)
				
				// 通知の更新が必要かもしれない
				PollingWorker.queueUpdateNotification(this@ActMain)
				
				// 適当にカラムを追加する
				val count = SavedAccount.count
				if(count > 1) {
					addColumn(defaultInsertPosition, account, Column.TYPE_HOME)
				} else {
					addColumn(defaultInsertPosition, account, Column.TYPE_HOME)
					addColumn(defaultInsertPosition, account, Column.TYPE_NOTIFICATIONS)
					addColumn(defaultInsertPosition, account, Column.TYPE_LOCAL)
					addColumn(defaultInsertPosition, account, Column.TYPE_FEDERATE)
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
			
			internal var ta : TootAccount? = null
			
			override fun background(client : TootApiClient) : TootApiResult? {
				val result = client.getUserCredential(access_token)
				this.ta =
					TootParser(this@ActMain, object : LinkHelper {}).account(result?.jsonObject)
				return result
			}
			
			override fun handleResult(result : TootApiResult?) {
				
				if(afterAccountVerify(result, ta, sa, host)) {
					try {
						dialog_host?.dismiss()
					} catch(ignored : Throwable) {
						// IllegalArgumentException がたまに出る
					}
					
					try {
						dialog_token?.dismiss()
					} catch(ignored : Throwable) {
						// IllegalArgumentException がたまに出る
					}
					
				}
			}
		})
	}
	
	// アクセストークンの手動入力(更新)
	private fun checkAccessToken2(db_id : Long) {
		
		val sa = SavedAccount.loadAccount(this, db_id) ?: return
		
		DlgTextInput.show(
			this,
			getString(R.string.access_token),
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
	
	fun closeColumn(bConfirm : Boolean, column : Column) {
		
		if(column.dont_close) {
			showToast(this, false, R.string.column_has_dont_close_option)
			return
		}
		
		if(! bConfirm && ! Pref.bpDontConfirmBeforeCloseColumn(pref)) {
			AlertDialog.Builder(this)
				.setMessage(R.string.confirm_close_column)
				.setNegativeButton(R.string.cancel, null)
				.setPositiveButton(R.string.ok) { _, _ -> closeColumn(true, column) }
				.show()
			return
		}
		
		val page_delete = app_state.column_list.indexOf(column)
		
		phoneTab({ env ->
			val page_showing = env.pager.currentItem
			
			removeColumn(column)
			
			if(! app_state.column_list.isEmpty() && page_delete > 0 && page_showing == page_delete) {
				val idx = page_delete - 1
				scrollToColumn(idx)
				val c = app_state.column_list[idx]
				if(! c.bFirstInitialized) {
					c.startLoading()
				}
			}
			
		}, { _ ->
			removeColumn(column)
			
			if(! app_state.column_list.isEmpty() && page_delete > 0) {
				val idx = page_delete - 1
				scrollToColumn(idx)
				val c = app_state.column_list[idx]
				if(! c.bFirstInitialized) {
					c.startLoading()
				}
			}
		})
	}
	
	//////////////////////////////////////////////////////////////
	// カラム追加系
	
	fun addColumn(indexArg : Int, ai : SavedAccount, type : Int, vararg params : Any) : Column {
		var index = indexArg
		// 既に同じカラムがあればそこに移動する
		for(column in app_state.column_list) {
			if(column.isSameSpec(ai, type, params)) {
				index = app_state.column_list.indexOf(column)
				scrollToColumn(index)
				return column
			}
		}
		
		//
		val col = Column(app_state, ai, this, type, *params)
		index = addColumn(col, index)
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
			if(opener.allowIntercept && accessInto != null) {
				
				// ハッシュタグはいきなり開くのではなくメニューがある
				var m = reUrlHashTag.matcher(opener.url)
				if(m.find()) {
					// https://mastodon.juggler.jp/tags/%E3%83%8F%E3%83%83%E3%82%B7%E3%83%A5%E3%82%BF%E3%82%B0
					val host = m.group(1)
					val tag_without_sharp = Uri.decode(m.group(2))
					Action_HashTag.dialog(
						this@ActMain,
						opener.pos,
						opener.url,
						host,
						tag_without_sharp,
						opener.tagList
					)
					
					return
				}
				
				// ステータスページをアプリから開く
				m = reStatusPage.matcher(opener.url)
				if(m.find()) {
					try {
						// https://mastodon.juggler.jp/@SubwayTooter/(status_id)
						val host = m.group(1)
						val status_id = m.group(3).toLong(10)
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
				
				// https://mastodon.juggler.jp/@SubwayTooter
				m = reUserPage.matcher(opener.url)
				if(m.find()) {
					// ユーザページをアプリ内で開く
					Action_User.profile(
						this@ActMain,
						opener.pos,
						accessInto,
						opener.url,
						m.group(1),
						Uri.decode(m.group(2))
					)
					return
				}
			}
			
			App1.openCustomTab(this, opener.url)
			
		} catch(ex : Throwable) {
			// log.trace( ex );
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
		var c = footer_button_bg_color
		if(c == 0) {
			btnMenu.setBackgroundResource(R.drawable.btn_bg_ddd)
			btnToot.setBackgroundResource(R.drawable.btn_bg_ddd)
			btnQuickToot.setBackgroundResource(R.drawable.btn_bg_ddd)
		} else {
			val fg = if(footer_button_fg_color != 0)
				footer_button_fg_color
			else
				Styler.getAttributeColor(this, R.attr.colorRippleEffect)
			ViewCompat.setBackground(btnToot, Styler.getAdaptiveRippleDrawable(c, fg))
			ViewCompat.setBackground(btnMenu, Styler.getAdaptiveRippleDrawable(c, fg))
			ViewCompat.setBackground(btnQuickToot, Styler.getAdaptiveRippleDrawable(c, fg))
		}
		
		c = footer_button_fg_color
		if(c == 0) {
			Styler.setIconDefaultColor(this, btnToot, R.attr.ic_edit)
			Styler.setIconDefaultColor(this, btnMenu, R.attr.ic_hamburger)
			Styler.setIconDefaultColor(this, btnQuickToot, R.attr.btn_post)
		} else {
			Styler.setIconCustomColor(this, btnToot, c, R.attr.ic_edit)
			Styler.setIconCustomColor(this, btnMenu, c, R.attr.ic_hamburger)
			Styler.setIconCustomColor(this, btnQuickToot, c, R.attr.btn_post)
		}
		
		c = footer_tab_bg_color
		if(c == 0) {
			svColumnStrip.setBackgroundColor(
				Styler.getAttributeColor(
					this,
					R.attr.colorColumnStripBackground
				)
			)
			llQuickTootBar.setBackgroundColor(
				Styler.getAttributeColor(
					this,
					R.attr.colorColumnStripBackground
				)
			)
		} else {
			svColumnStrip.setBackgroundColor(c)
			svColumnStrip.setBackgroundColor(
				Styler.getAttributeColor(
					this,
					R.attr.colorColumnStripBackground
				)
			)
		}
		
		c = footer_tab_divider_color
		if(c == 0) {
			vFooterDivider1.setBackgroundColor(
				Styler.getAttributeColor(
					this,
					R.attr.colorImageButton
				)
			)
			vFooterDivider2.setBackgroundColor(
				Styler.getAttributeColor(
					this,
					R.attr.colorImageButton
				)
			)
		} else {
			vFooterDivider1.setBackgroundColor(c)
			vFooterDivider2.setBackgroundColor(c)
		}
		
		llColumnStrip.indicatorColor = footer_tab_indicator_color
	}
	
	/////////////////////////////////////////////////////////////////////////
	// タブレット対応で必要になった関数など
	
	private fun closeColumnSetting() : Boolean {
		phoneTab({ env ->
			val vh = env.pager_adapter.getColumnViewHolder(env.pager.currentItem)
			if(vh.isColumnSettingShown) {
				vh.closeColumnSetting()
				return@closeColumnSetting true
			}
		}, { env ->
			for(i in 0 until env.tablet_layout_manager.childCount) {
				val v = env.tablet_layout_manager.getChildAt(i)
				val columnViewHolder =
					(env.tablet_pager.getChildViewHolder(v) as? TabletColumnViewHolder)?.columnViewHolder
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
		
		val tmp_list = ArrayList<Column>()
		val used_set = HashSet<Int>()
		
		for(i in new_order) {
			used_set.add(i)
			tmp_list.add(app_state.column_list[i])
		}
		
		var i = 0
		val ie = app_state.column_list.size
		while(i < ie) {
			if(used_set.contains(i)) {
				++ i
				continue
			}
			app_state.column_list[i].dispose()
			++ i
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
		
		val sw = dm.widthPixels
		
		val density = dm.density
		var column_w_min = (0.5f + column_w_min_dp * density).toInt()
		if(column_w_min < 1) column_w_min = 1
		
		if(sw < column_w_min * 2) {
			
			// 最小幅で2つ表示できないのなら1カラム表示
			env.tablet_pager_adapter.columnWidth = sw
			resizeAutoCW(sw)
		} else {
			
			// カラム最小幅から計算した表示カラム数
			nScreenColumn = sw / column_w_min
			if(nScreenColumn < 1) nScreenColumn = 1
			
			// データのカラム数より大きくならないようにする
			// (でも最小は1)
			val column_count = app_state.column_list.size
			if(column_count > 0 && column_count < nScreenColumn) {
				nScreenColumn = column_count
			}
			
			// 表示カラム数から計算したカラム幅
			var column_w = sw / nScreenColumn
			
			// 最小カラム幅の1.5倍よりは大きくならないようにする
			val column_w_max = (0.5f + column_w_min * 1.5f).toInt()
			if(column_w > column_w_max) {
				column_w = column_w_max
			}
			resizeAutoCW(column_w)
			
			nColumnWidth = column_w
			env.tablet_pager_adapter.columnWidth = column_w
			env.tablet_snap_helper.columnWidth = column_w
		}
		
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
	
	private fun importAppData(uri : Uri) {
		
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
			
			internal fun setProgressMessage(sv : String) {
				runOnMainLooper { progress.setMessage(sv) }
			}
			
			override fun doInBackground(vararg params : Void) : ArrayList<Column>? {
				try {
					setProgressMessage("import data to local storage...")
					
					val cacheDir = cacheDir
					
					cacheDir.mkdir()
					val file = File(
						cacheDir,
						"SubwayTooter." + android.os.Process.myPid() + "." + android.os.Process.myTid() + ".json"
					)
					
					// ローカルファイルにコピーする
					val source = contentResolver.openInputStream(uri)
					if(source == null) {
						showToast(this@ActMain, true, "openInputStream failed.")
						return null
					} else {
						source.use { inStream ->
							FileOutputStream(file).use { outStream ->
								IOUtils.copy(inStream, outStream)
							}
						}
					}
					
					// 通知サービスを止める
					setProgressMessage("syncing notification poller…")
					PollingWorker.queueAppDataImportBefore(this@ActMain)
					while(PollingWorker.mBusyAppDataImportBefore.get()) {
						Thread.sleep(1000L)
						log.d("syncing polling task...")
					}
					
					// JSONを読みだす
					setProgressMessage("reading app data...")
					InputStreamReader(FileInputStream(file), "UTF-8").use { inStream ->
						return AppDataExporter.decodeAppData(this@ActMain, JsonReader(inStream))
					}
				} catch(ex : Throwable) {
					log.trace(ex)
					showToast(this@ActMain, ex, "importAppData failed.")
				}
				
				return null
			}
			
			override fun onCancelled(result : ArrayList<Column>?) {
				onPostExecute(result)
			}
			
			override fun onPostExecute(result : ArrayList<Column>?) {
				
				progress.dismiss()
				
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
		if(timeline_font_size_sp.isNaN()) {
			tv.textSize = timeline_font_size_sp
		}
		if(timeline_font != null) {
			tv.typeface = timeline_font
		}
		tv.text = text
		tv.measure(
			View.MeasureSpec.makeMeasureSpec(nAutoCwCellWidth, View.MeasureSpec.EXACTLY),
			View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
		)
		val l = tv.layout
		if(l != null) {
			auto_cw.originalLineCount = l.lineCount
			val line_count = auto_cw.originalLineCount
			
			if(nAutoCwLines > 0
				&& line_count > nAutoCwLines
				&& status.spoiler_text?.isEmpty() != false
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
	
}
