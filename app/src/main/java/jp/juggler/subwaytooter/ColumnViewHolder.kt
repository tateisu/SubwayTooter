package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.os.AsyncTask
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.omadahealth.github.swipyrefreshlayout.library.SwipyRefreshLayout
import com.omadahealth.github.swipyrefreshlayout.library.SwipyRefreshLayoutDirection
import jp.juggler.subwaytooter.action.Action_List
import jp.juggler.subwaytooter.action.Action_Notification
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.util.ScrollPosition
import jp.juggler.subwaytooter.view.ListDivider
import jp.juggler.util.*
import org.jetbrains.anko.backgroundDrawable
import org.jetbrains.anko.textColor
import java.io.Closeable
import java.lang.reflect.Field
import java.util.regex.Pattern

@SuppressLint("ClickableViewAccessibility")
class ColumnViewHolder(
	val activity : ActMain,
	val viewRoot : View
) : View.OnClickListener,
	SwipyRefreshLayout.OnRefreshListener,
	CompoundButton.OnCheckedChangeListener, View.OnLongClickListener {
	
	companion object {
		private val log = LogCategory("ColumnViewHolder")
		
		val fieldRecycler : Field by lazy {
			val field = RecyclerView::class.java.getDeclaredField("mRecycler")
			field.isAccessible = true
			field
		}
		
		val fieldState : Field by lazy {
			val field = RecyclerView::class.java.getDeclaredField("mState")
			field.isAccessible = true
			field
		}
		
		val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
		
		//		var lastRefreshError : String = ""
		//		var lastRefreshErrorShown : Long = 0L
	}
	
	var column : Column? = null
	private var status_adapter : ItemListAdapter? = null
	private var page_idx : Int = 0
	
	private val tvLoading : TextView
	val listView : RecyclerView
	val refreshLayout : SwipyRefreshLayout
	lateinit var listLayoutManager : LinearLayoutManager
	
	private val llColumnHeader : View
	private val tvColumnIndex : TextView
	private val tvColumnStatus : TextView
	private val tvColumnContext : TextView
	private val ivColumnIcon : ImageView
	private val tvColumnName : TextView
	
	private val llColumnSetting : View
	
	private val btnSearch : ImageButton
	private val btnSearchClear : ImageButton
	private val etSearch : EditText
	private val cbResolve : CheckBox
	private val etRegexFilter : EditText
	private val tvRegexFilterError : TextView
	
	private val btnColumnSetting : ImageButton
	private val btnColumnReload : ImageButton
	private val btnColumnClose : ImageButton
	
	private val flColumnBackground : View
	private val ivColumnBackgroundImage : ImageView
	private val llSearch : View
	private val cbDontCloseColumn : CheckBox
	private val cbWithAttachment : CheckBox
	private val cbWithHighlight : CheckBox
	private val cbDontShowBoost : CheckBox
	private val cbDontShowFollow : CheckBox
	private val cbDontShowFavourite : CheckBox
	private val cbDontShowReply : CheckBox
	private val cbDontShowReaction : CheckBox
	private val cbDontShowVote : CheckBox
	private val cbDontShowNormalToot : CheckBox
	private val cbInstanceLocal : CheckBox
	private val cbDontStreaming : CheckBox
	private val cbDontAutoRefresh : CheckBox
	private val cbHideMediaDefault : CheckBox
	private val cbSystemNotificationNotRelated : CheckBox
	private val cbEnableSpeech : CheckBox
	private val cbOldApi : CheckBox
	private val llRegexFilter : View
	private val btnDeleteNotification : Button
	
	private val svQuickFilter : HorizontalScrollView
	private val btnQuickFilterAll : Button
	private val btnQuickFilterMention : ImageButton
	private val btnQuickFilterFavourite : ImageButton
	private val btnQuickFilterBoost : ImageButton
	private val btnQuickFilterFollow : ImageButton
	private val btnQuickFilterReaction : ImageButton
	private val btnQuickFilterVote : ImageButton
	
	private val llRefreshError : FrameLayout
	private val ivRefreshError : ImageView
	private val tvRefreshError : TextView
	
	private val llListList : View
	private val etListName : EditText
	private val btnListAdd : View
	
	private val llHashtagExtra : LinearLayout
	private val etHashtagExtraAny : EditText
	private val etHashtagExtraAll : EditText
	private val etHashtagExtraNone : EditText
	
	private val isPageDestroyed : Boolean
		get() = column == null || activity.isFinishing
	
	private var binding_busy : Boolean = false
	
	private var last_image_uri : String? = null
	private var last_image_bitmap : Bitmap? = null
	private var last_image_task : AsyncTask<Void, Void, Bitmap?>? = null
	
	private fun checkRegexFilterError(src : String) : String? {
		try {
			if(src.isEmpty()) {
				return null
			}
			val m = Pattern.compile(src).matcher("")
			if(m.find()) {
				// 空文字列にマッチする正規表現はエラー扱いにする
				// そうしないとCWの警告テキストにマッチしてしまう
				return activity.getString(R.string.regex_filter_matches_empty_string)
			}
			return null
		} catch(ex : Throwable) {
			val message = ex.message
			return if(message != null && message.isNotEmpty()) {
				message
			} else {
				ex.withCaption(activity.resources, R.string.regex_error)
			}
		}
	}
	
	private fun isRegexValid() : Boolean {
		val s = etRegexFilter.text.toString()
		val error = checkRegexFilterError(s)
		tvRegexFilterError.text = error ?: ""
		return error == null
	}
	
	val isColumnSettingShown : Boolean
		get() = llColumnSetting.visibility == View.VISIBLE
	
	//	val headerView : HeaderViewHolderBase?
	//		get() = status_adapter?.header
	
	val scrollPosition : ScrollPosition
		get() = ScrollPosition(this)
	
	inner class ErrorFlickListener(
		context : Context
	) : View.OnTouchListener, GestureDetector.OnGestureListener {
		
		private val gd = GestureDetector(context, this)
		private val density = context.resources.displayMetrics.density
		
		@SuppressLint("ClickableViewAccessibility")
		override fun onTouch(v : View?, event : MotionEvent?) : Boolean {
			return gd.onTouchEvent(event)
		}
		
		override fun onShowPress(e : MotionEvent?) {
		}
		
		override fun onLongPress(e : MotionEvent?) {
		}
		
		override fun onSingleTapUp(e : MotionEvent?) : Boolean {
			return true
		}
		
		override fun onDown(e : MotionEvent?) : Boolean {
			return true
		}
		
		override fun onScroll(
			e1 : MotionEvent?,
			e2 : MotionEvent?,
			distanceX : Float,
			distanceY : Float
		) : Boolean {
			return true
		}
		
		override fun onFling(
			e1 : MotionEvent?,
			e2 : MotionEvent?,
			velocityX : Float,
			velocityY : Float
		) : Boolean {
			
			val vx = velocityX.abs()
			val vy = velocityY.abs()
			if(vy < vx * 1.5f) {
				// フリック方向が上下ではない
				log.d("fling? not vertical view. $vx $vy")
			} else {
				
				val vydp = vy / density
				val limit = 1024f
				log.d("fling? $vydp/$limit")
				if(vydp >= limit) {
					
					val column = column
					if(column != null && column.lastTask == null) {
						column.startLoading()
					}
				}
			}
			return true
		}
	}
	
	@SuppressLint("ClickableViewAccessibility")
	private fun initLoadingTextView() {
		tvLoading.setOnTouchListener(ErrorFlickListener(activity))
	}
	
	init {
		
		viewRoot.scan { v ->
			try {
				if(v is Button) {
					// ボタンは触らない
				} else if(v is TextView) {
					v.typeface = ActMain.timeline_font
				}
			} catch(ex : Throwable) {
				log.trace(ex)
			}
		}
		
		flColumnBackground = viewRoot.findViewById(R.id.flColumnBackground)
		ivColumnBackgroundImage = viewRoot.findViewById(R.id.ivColumnBackgroundImage)
		llColumnHeader = viewRoot.findViewById(R.id.llColumnHeader)
		
		tvColumnIndex = viewRoot.findViewById(R.id.tvColumnIndex)
		tvColumnStatus = viewRoot.findViewById(R.id.tvColumnStatus)
		
		tvColumnName = viewRoot.findViewById(R.id.tvColumnName)
		tvColumnContext = viewRoot.findViewById(R.id.tvColumnContext)
		ivColumnIcon = viewRoot.findViewById(R.id.ivColumnIcon)
		
		btnColumnSetting = viewRoot.findViewById(R.id.btnColumnSetting)
		btnColumnReload = viewRoot.findViewById(R.id.btnColumnReload)
		btnColumnClose = viewRoot.findViewById(R.id.btnColumnClose)
		
		tvLoading = viewRoot.findViewById(R.id.tvLoading)
		listView = viewRoot.findViewById(R.id.listView)
		
		if(Pref.bpShareViewPool(activity.pref)) {
			listView.setRecycledViewPool(activity.viewPool)
		}
		listView.itemAnimator = null
		//
		//		val animator = listView.itemAnimator
		//		if( animator is DefaultItemAnimator){
		//			animator.supportsChangeAnimations = false
		//		}
		
		btnSearch = viewRoot.findViewById(R.id.btnSearch)
		btnSearchClear = viewRoot.findViewById(R.id.btnSearchClear)
		etSearch = viewRoot.findViewById(R.id.etSearch)
		cbResolve = viewRoot.findViewById(R.id.cbResolve)
		
		llSearch = viewRoot.findViewById(R.id.llSearch)
		llListList = viewRoot.findViewById(R.id.llListList)
		
		btnListAdd = viewRoot.findViewById(R.id.btnListAdd)
		etListName = viewRoot.findViewById(R.id.etListName)
		btnListAdd.setOnClickListener(this)
		
		etListName.setOnEditorActionListener { _, actionId, _ ->
			var handled = false
			if(actionId == EditorInfo.IME_ACTION_SEND) {
				btnListAdd.performClick()
				handled = true
			}
			handled
		}
		
		llColumnSetting = viewRoot.findViewById(R.id.llColumnSetting)
		
		cbDontCloseColumn = viewRoot.findViewById(R.id.cbDontCloseColumn)
		cbWithAttachment = viewRoot.findViewById(R.id.cbWithAttachment)
		cbWithHighlight = viewRoot.findViewById(R.id.cbWithHighlight)
		cbDontShowBoost = viewRoot.findViewById(R.id.cbDontShowBoost)
		cbDontShowFollow = viewRoot.findViewById(R.id.cbDontShowFollow)
		cbDontShowFavourite = viewRoot.findViewById(R.id.cbDontShowFavourite)
		cbDontShowReply = viewRoot.findViewById(R.id.cbDontShowReply)
		cbDontShowReaction = viewRoot.findViewById(R.id.cbDontShowReaction)
		cbDontShowVote = viewRoot.findViewById(R.id.cbDontShowVote)
		cbDontShowNormalToot = viewRoot.findViewById(R.id.cbDontShowNormalToot)
		cbInstanceLocal = viewRoot.findViewById(R.id.cbInstanceLocal)
		cbDontStreaming = viewRoot.findViewById(R.id.cbDontStreaming)
		cbDontAutoRefresh = viewRoot.findViewById(R.id.cbDontAutoRefresh)
		cbHideMediaDefault = viewRoot.findViewById(R.id.cbHideMediaDefault)
		cbSystemNotificationNotRelated = viewRoot.findViewById(R.id.cbSystemNotificationNotRelated)
		cbEnableSpeech = viewRoot.findViewById(R.id.cbEnableSpeech)
		cbOldApi = viewRoot.findViewById(R.id.cbOldApi)
		etRegexFilter = viewRoot.findViewById(R.id.etRegexFilter)
		llRegexFilter = viewRoot.findViewById(R.id.llRegexFilter)
		tvRegexFilterError = viewRoot.findViewById(R.id.tvRegexFilterError)
		
		btnDeleteNotification = viewRoot.findViewById(R.id.btnDeleteNotification)
		
		svQuickFilter = viewRoot.findViewById(R.id.svQuickFilter)
		btnQuickFilterAll = viewRoot.findViewById(R.id.btnQuickFilterAll)
		btnQuickFilterMention = viewRoot.findViewById(R.id.btnQuickFilterMention)
		btnQuickFilterFavourite = viewRoot.findViewById(R.id.btnQuickFilterFavourite)
		btnQuickFilterBoost = viewRoot.findViewById(R.id.btnQuickFilterBoost)
		btnQuickFilterFollow = viewRoot.findViewById(R.id.btnQuickFilterFollow)
		btnQuickFilterReaction = viewRoot.findViewById(R.id.btnQuickFilterReaction)
		btnQuickFilterVote = viewRoot.findViewById(R.id.btnQuickFilterVote)
		val llColumnSettingInside : LinearLayout = viewRoot.findViewById(R.id.llColumnSettingInside)
		
		llHashtagExtra = viewRoot.findViewById(R.id.llHashtagExtra)
		etHashtagExtraAny = viewRoot.findViewById(R.id.etHashtagExtraAny)
		etHashtagExtraAll = viewRoot.findViewById(R.id.etHashtagExtraAll)
		etHashtagExtraNone = viewRoot.findViewById(R.id.etHashtagExtraNone)
		
		
		
		btnQuickFilterAll.setOnClickListener(this)
		btnQuickFilterMention.setOnClickListener(this)
		btnQuickFilterFavourite.setOnClickListener(this)
		btnQuickFilterBoost.setOnClickListener(this)
		btnQuickFilterFollow.setOnClickListener(this)
		btnQuickFilterReaction.setOnClickListener(this)
		btnQuickFilterVote.setOnClickListener(this)
		
		
		llColumnHeader.setOnClickListener(this)
		btnColumnSetting.setOnClickListener(this)
		btnColumnReload.setOnClickListener(this)
		btnColumnClose.setOnClickListener(this)
		btnColumnClose.setOnLongClickListener(this)
		btnDeleteNotification.setOnClickListener(this)
		
		viewRoot.findViewById<View>(R.id.btnColor).setOnClickListener(this)
		
		this.refreshLayout = viewRoot.findViewById(R.id.swipyRefreshLayout)
		refreshLayout.setOnRefreshListener(this)
		refreshLayout.setDistanceToTriggerSync((0.5f + 20f * activity.density).toInt())
		
		llRefreshError = viewRoot.findViewById(R.id.llRefreshError)
		ivRefreshError = viewRoot.findViewById(R.id.ivRefreshError)
		tvRefreshError = viewRoot.findViewById(R.id.tvRefreshError)
		llRefreshError.setOnClickListener(this)
		
		
		cbDontCloseColumn.setOnCheckedChangeListener(this)
		cbWithAttachment.setOnCheckedChangeListener(this)
		cbWithHighlight.setOnCheckedChangeListener(this)
		cbDontShowBoost.setOnCheckedChangeListener(this)
		cbDontShowFollow.setOnCheckedChangeListener(this)
		cbDontShowFavourite.setOnCheckedChangeListener(this)
		cbDontShowReply.setOnCheckedChangeListener(this)
		cbDontShowReaction.setOnCheckedChangeListener(this)
		cbDontShowVote.setOnCheckedChangeListener(this)
		cbDontShowNormalToot.setOnCheckedChangeListener(this)
		cbInstanceLocal.setOnCheckedChangeListener(this)
		cbDontStreaming.setOnCheckedChangeListener(this)
		cbDontAutoRefresh.setOnCheckedChangeListener(this)
		cbHideMediaDefault.setOnCheckedChangeListener(this)
		cbSystemNotificationNotRelated.setOnCheckedChangeListener(this)
		cbEnableSpeech.setOnCheckedChangeListener(this)
		cbOldApi.setOnCheckedChangeListener(this)
		
		if(Pref.bpMoveNotificationsQuickFilter(activity.pref)) {
			(svQuickFilter.parent as? ViewGroup)?.removeView(svQuickFilter)
			llColumnSettingInside.addView(svQuickFilter, 0)
			
			svQuickFilter.setOnTouchListener { v, event ->
				val action = event.action
				if(action == MotionEvent.ACTION_DOWN) {
					val sv = v as? HorizontalScrollView
					if(sv != null && sv.getChildAt(0).width > sv.width) {
						sv.requestDisallowInterceptTouchEvent(true)
					}
				}
				v.onTouchEvent(event)
			}
		}
		
		if(! activity.header_text_size_sp.isNaN()) {
			tvColumnName.textSize = activity.header_text_size_sp
			
			val acctSize = activity.header_text_size_sp * 0.857f
			tvColumnContext.textSize = acctSize
			tvColumnStatus.textSize = acctSize
			tvColumnIndex.textSize = acctSize
		}
		
		initLoadingTextView()
		
		var pad = 0
		var wh = ActMain.headerIconSize + pad * 2
		ivColumnIcon.layoutParams.width = wh
		ivColumnIcon.layoutParams.height = wh
		ivColumnIcon.setPaddingRelative(pad, pad, pad, pad)
		
		pad = (ActMain.headerIconSize * 0.125f + 0.5f).toInt()
		wh = ActMain.headerIconSize + pad * 2
		btnColumnSetting.layoutParams.width = wh
		btnColumnSetting.layoutParams.height = wh
		btnColumnSetting.setPaddingRelative(pad, pad, pad, pad)
		btnColumnReload.layoutParams.width = wh
		btnColumnReload.layoutParams.height = wh
		btnColumnReload.setPaddingRelative(pad, pad, pad, pad)
		btnColumnClose.layoutParams.width = wh
		btnColumnClose.layoutParams.height = wh
		btnColumnClose.setPaddingRelative(pad, pad, pad, pad)
		
		btnSearch.setOnClickListener(this)
		btnSearchClear.setOnClickListener(this)
		etSearch.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, _ ->
			if(! binding_busy) {
				if(actionId == EditorInfo.IME_ACTION_SEARCH) {
					btnSearch.performClick()
					return@OnEditorActionListener true
				}
			}
			false
		})
		
		// 入力の追跡
		etRegexFilter.addTextChangedListener(CustomTextWatcher {
			if(binding_busy || isPageDestroyed) return@CustomTextWatcher
			if(! isRegexValid()) return@CustomTextWatcher
			column?.regex_text = etRegexFilter.text.toString()
			activity.app_state.saveColumnList()
			activity.handler.removeCallbacks(proc_start_filter)
			activity.handler.postDelayed(proc_start_filter, 666L)
		})
		
		etHashtagExtraAny.addTextChangedListener(CustomTextWatcher {
			if(binding_busy || isPageDestroyed) return@CustomTextWatcher
			column?.hashtag_any = etHashtagExtraAny.text.toString()
			activity.app_state.saveColumnList()
			activity.handler.removeCallbacks(proc_start_filter)
			activity.handler.postDelayed(proc_start_filter, 666L)
		})
		
		etHashtagExtraAll.addTextChangedListener(CustomTextWatcher {
			if(binding_busy || isPageDestroyed) return@CustomTextWatcher
			column?.hashtag_all = etHashtagExtraAll.text.toString()
			activity.app_state.saveColumnList()
			activity.handler.removeCallbacks(proc_start_filter)
			activity.handler.postDelayed(proc_start_filter, 666L)
		})
		
		etHashtagExtraNone.addTextChangedListener(CustomTextWatcher {
			if(binding_busy || isPageDestroyed) return@CustomTextWatcher
			column?.hashtag_none = etHashtagExtraNone.text.toString()
			activity.app_state.saveColumnList()
			activity.handler.removeCallbacks(proc_start_filter)
			activity.handler.postDelayed(proc_start_filter, 666L)
		})
	}
	
	private val proc_start_filter : Runnable = Runnable {
		if(binding_busy || isPageDestroyed) return@Runnable
		column?.startLoading()
	}
	
	private val proc_restoreScrollPosition = object : Runnable {
		override fun run() {
			activity.handler.removeCallbacks(this)
			
			if(isPageDestroyed) {
				log.d("restoreScrollPosition [%d], page is destroyed.")
				return
			}
			
			val column = this@ColumnViewHolder.column
			if(column == null) {
				log.d("restoreScrollPosition [%d], column==null", page_idx)
				return
			}
			
			if(column.is_dispose.get()) {
				log.d("restoreScrollPosition [%d], column is disposed", page_idx)
				return
			}
			
			if(column.hasMultipleViewHolder()) {
				log.d(
					"restoreScrollPosition [%d] %s , column has multiple view holder. retry later.",
					page_idx,
					column.getColumnName(true)
				)
				
				// タブレットモードでカラムを追加/削除した際に発生する。
				// このタイミングでスクロール位置を復元してもうまくいかないので延期する
				activity.handler.postDelayed(this, 100L)
				return
			}
			
			//復元後にもここを通るがこれは正常である
			val sp = column.scroll_save
			if(sp == null) {
				//				val lvi = column.last_viewing_item_id
				//				if( lvi != null ){
				//					column.last_viewing_item_id = null
				//					val listIndex = column.findListIndexByTimelineId(lvi)
				//					if( listIndex != null){
				//						log.d(
				//							"restoreScrollPosition [$page_idx] %s , restore from last_viewing_item_id %d"
				//							, column.getColumnName( true )
				//							,listIndex
				//						)
				//						ScrollPosition(column.toAdapterIndex(listIndex),0).restore(this@ColumnViewHolder)
				//						return
				//					}
				//				}
				
				log.d(
					"restoreScrollPosition [$page_idx] %s , column has no saved scroll position."
					, column.getColumnName(true)
				)
				return
			}
			
			column.scroll_save = null
			
			if(listView.visibility != View.VISIBLE) {
				log.d(
					"restoreScrollPosition [$page_idx] %s , listView is not visible. saved position %s,%s is dropped."
					, column.getColumnName(true)
					, sp.adapterIndex
					, sp.offset
				)
			} else {
				log.d(
					"restoreScrollPosition [%d] %s , listView is visible. resume %s,%s"
					, page_idx
					, column.getColumnName(true)
					, sp.adapterIndex
					, sp.offset
				)
				sp.restore(this@ColumnViewHolder)
			}
			
		}
	}
	
	fun onPageDestroy(page_idx : Int) {
		// タブレットモードの場合、onPageCreateより前に呼ばれる
		val column = this@ColumnViewHolder.column
		if(column != null) {
			log.d("onPageDestroy [%d] %s", page_idx, tvColumnName.text)
			saveScrollPosition()
			listView.adapter = null
			column.removeColumnViewHolder(this)
			this@ColumnViewHolder.column = null
		}
		
		closeBitmaps()
		
		activity.closeListItemPopup()
	}
	
	fun onPageCreate(column : Column, page_idx : Int, page_count : Int) {
		binding_busy = true
		try {
			this.column = column
			this.page_idx = page_idx
			
			log.d("onPageCreate [%d] %s", page_idx, column.getColumnName(true))
			
			val bSimpleList =
				column.column_type != Column.TYPE_CONVERSATION && Pref.bpSimpleList(activity.pref)
			
			tvColumnIndex.text = activity.getString(R.string.column_index, page_idx + 1, page_count)
			tvColumnStatus.text = "?"
			ivColumnIcon.setImageResource(column.getIconId(column.column_type))
			
			listView.adapter = null
			if(listView.itemDecorationCount == 0) {
				listView.addItemDecoration(ListDivider(activity))
			}
			
			val status_adapter = ItemListAdapter(activity, column, this, bSimpleList)
			this.status_adapter = status_adapter
			
			val isNotificationColumn = column.isNotificationColumn
			
			// 添付メディアや正規表現のフィルタ
			val bAllowFilter = column.canStatusFilter()
			
			llColumnSetting.visibility = View.GONE
			
			cbDontCloseColumn.isChecked = column.dont_close
			cbWithAttachment.isChecked = column.with_attachment
			cbWithHighlight.isChecked = column.with_highlight
			cbDontShowBoost.isChecked = column.dont_show_boost
			cbDontShowFollow.isChecked = column.dont_show_follow
			cbDontShowFavourite.isChecked = column.dont_show_favourite
			cbDontShowReply.isChecked = column.dont_show_reply
			cbDontShowReaction.isChecked = column.dont_show_reaction
			cbDontShowVote.isChecked = column.dont_show_vote
			cbDontShowNormalToot.isChecked = column.dont_show_normal_toot
			cbInstanceLocal.isChecked = column.instance_local
			cbDontStreaming.isChecked = column.dont_streaming
			cbDontAutoRefresh.isChecked = column.dont_auto_refresh
			cbHideMediaDefault.isChecked = column.hide_media_default
			cbSystemNotificationNotRelated.isChecked = column.system_notification_not_related
			cbEnableSpeech.isChecked = column.enable_speech
			cbOldApi.isChecked = column.use_old_api
			
			etRegexFilter.setText(column.regex_text)
			etSearch.setText(column.search_query)
			cbResolve.isChecked = column.search_resolve
			
			vg(cbWithAttachment, bAllowFilter)
			vg(cbWithHighlight, bAllowFilter)
			vg(etRegexFilter, bAllowFilter)
			vg(llRegexFilter, bAllowFilter)
			
			vg(cbDontShowBoost, column.canFilterBoost())
			vg(cbDontShowReply, column.canFilterReply())
			vg(cbDontShowNormalToot, column.canFilterNormalToot())
			vg(cbDontShowReaction, isNotificationColumn && column.isMisskey)
			vg(cbDontShowVote, isNotificationColumn )
			vg(cbDontShowFavourite, isNotificationColumn && ! column.isMisskey)
			vg(cbDontShowFollow, isNotificationColumn)
			
			vg(cbInstanceLocal, column.column_type == Column.TYPE_HASHTAG)
			
			
			vg(cbDontStreaming, column.canStreaming())
			vg(cbDontAutoRefresh, column.canAutoRefresh())
			vg(cbHideMediaDefault, column.canNSFWDefault())
			vg(cbSystemNotificationNotRelated, column.isNotificationColumn)
			vg(cbEnableSpeech, column.canSpeech())
			vg(cbOldApi, column.column_type == Column.TYPE_DIRECT_MESSAGES)
			
			
			vg(btnDeleteNotification, column.isNotificationColumn)
			
			if( vg(llSearch, column.isSearchColumn) ){
				vg(btnSearchClear,Pref.bpShowSearchClear(activity.pref))
			}

			vg(llListList, column.column_type == Column.TYPE_LIST_LIST)
			vg(cbResolve, column.column_type == Column.TYPE_SEARCH)
			
			vg(llHashtagExtra, column.column_type == Column.TYPE_HASHTAG && ! column.isMisskey)
			etHashtagExtraAny.setText(column.hashtag_any)
			etHashtagExtraAll.setText(column.hashtag_all)
			etHashtagExtraNone.setText(column.hashtag_none)
			
			// tvRegexFilterErrorの表示を更新
			if(bAllowFilter) {
				isRegexValid()
			}
			
			val canRefreshTop = column.canRefreshTopBySwipe()
			val canRefreshBottom = column.canRefreshBottomBySwipe()
			
			refreshLayout.isEnabled = canRefreshTop || canRefreshBottom
			refreshLayout.direction = if(canRefreshTop && canRefreshBottom) {
				SwipyRefreshLayoutDirection.BOTH
			} else if(canRefreshTop) {
				SwipyRefreshLayoutDirection.TOP
			} else {
				SwipyRefreshLayoutDirection.BOTTOM
			}
			
			bRefreshErrorWillShown = false
			llRefreshError.clearAnimation()
			llRefreshError.visibility = View.GONE
			
			//
			listLayoutManager = LinearLayoutManager(activity)
			listView.layoutManager = listLayoutManager
			listView.adapter = status_adapter
			
			//XXX FastScrollerのサポートを諦める。ライブラリはいくつかあるんだけど、設定でON/OFFできなかったり頭文字バブルを無効にできなかったり
			// listView.isFastScrollEnabled = ! Pref.bpDisableFastScroller(Pref.pref(activity))
			
			column.addColumnViewHolder(this)
			
			showColumnColor()
			
			showContent(reason = "onPageCreate", reset = true)
		} finally {
			binding_busy = false
		}
	}
	
	private val procShowColumnStatus : Runnable = Runnable {
		
		val column = this.column
		if(column == null || column.is_dispose.get()) return@Runnable
		
		val sb = SpannableStringBuilder()
		try {
			
			val task = column.lastTask
			if(task != null) {
				sb.append(
					when(task.ctType) {
						ColumnTaskType.LOADING -> 'L'
						ColumnTaskType.REFRESH_TOP -> 'T'
						ColumnTaskType.REFRESH_BOTTOM -> 'B'
						ColumnTaskType.GAP -> 'G'
					}
				)
				sb.append(
					when {
						task.isCancelled -> "~"
						task.ctClosed.get() -> "!"
						task.ctStarted.get() -> ""
						else -> "?"
					}
				)
			}
			when(column.getStreamingStatus()) {
				StreamingIndicatorState.NONE -> {
				}
				
				StreamingIndicatorState.REGISTERED -> {
					sb.appendColorShadeIcon(activity, R.drawable.ic_pulse, "Streaming")
					sb.append("?")
				}
				
				StreamingIndicatorState.LISTENING -> {
					sb.appendColorShadeIcon(activity, R.drawable.ic_pulse, "Streaming")
				}
			}
			
		} finally {
			log.d("showColumnStatus ${sb}")
			tvColumnStatus.text = sb
		}
	}
	
	fun showColumnStatus() {
		activity.handler.removeCallbacks(procShowColumnStatus)
		activity.handler.postDelayed(procShowColumnStatus, 50L)
	}
	
	fun showColumnColor() {
		val column = this.column
		if(column == null || column.is_dispose.get()) return
		
		// カラムヘッダ背景
		column.setHeaderBackground(llColumnHeader)
		
		// カラムヘッダ文字色(A)
		var c = column.getHeaderNameColor()
		val csl = ColorStateList.valueOf(c)
		tvColumnName.textColor = c
		ivColumnIcon.imageTintList = csl
		btnColumnSetting.imageTintList = csl
		btnColumnReload.imageTintList = csl
		btnColumnClose.imageTintList = csl
		
		// カラムヘッダ文字色(B)
		c = column.getHeaderPageNumberColor()
		tvColumnIndex.textColor = c
		tvColumnStatus.textColor = c
		
		// カラム内部の背景色
		flColumnBackground.setBackgroundColor(
			column.column_bg_color.notZero()
				?: Column.defaultColorContentBg
		)
		
		// カラム内部の背景画像
		ivColumnBackgroundImage.alpha = column.column_bg_image_alpha
		loadBackgroundImage(ivColumnBackgroundImage, column.column_bg_image)
		
		// エラー表示
		tvLoading.textColor = column.getContentColor()
		
		status_adapter?.findHeaderViewHolder(listView)?.showColor()
		
		// カラム色を変更したらクイックフィルタの色も変わる場合がある
		showQuickFilter()
	}
	
	private fun closeBitmaps() {
		try {
			ivColumnBackgroundImage.visibility = View.GONE
			ivColumnBackgroundImage.setImageDrawable(null)
			
			last_image_bitmap?.recycle()
			last_image_bitmap = null
			
			last_image_task?.cancel(true)
			last_image_task = null
			
			last_image_uri = null
			
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
	}
	
	@SuppressLint("StaticFieldLeak")
	private fun loadBackgroundImage(iv : ImageView, url : String?) {
		try {
			if(url == null || url.isEmpty() || Pref.bpDontShowColumnBackgroundImage(activity.pref) ) {
				// 指定がないなら閉じる
				closeBitmaps()
				return
			}
			
			if(url == last_image_uri) {
				// 今表示してるのと同じ
				return
			}
			
			// 直前の処理をキャンセルする。Bitmapも破棄する
			closeBitmaps()
			
			// ロード開始
			last_image_uri = url
			val screen_w = iv.resources.displayMetrics.widthPixels
			val screen_h = iv.resources.displayMetrics.heightPixels
			
			// 非同期処理を開始
			val task = object : AsyncTask<Void, Void, Bitmap?>() {
				override fun doInBackground(vararg params : Void) : Bitmap? {
					return try {
						createResizedBitmap(
							activity, url.toUri(),
							if(screen_w > screen_h)
								screen_w
							else
								screen_h
						)
					} catch(ex : Throwable) {
						log.trace(ex)
						null
					}
				}
				
				override fun onCancelled(bitmap : Bitmap?) {
					onPostExecute(bitmap)
				}
				
				override fun onPostExecute(bitmap : Bitmap?) {
					if(bitmap != null) {
						if(isCancelled || url != last_image_uri) {
							bitmap.recycle()
						} else {
							last_image_bitmap = bitmap
							iv.setImageBitmap(last_image_bitmap)
							iv.visibility = View.VISIBLE
						}
					}
				}
			}
			last_image_task = task
			task.executeOnExecutor(App1.task_executor)
			
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
	}
	
	fun closeColumnSetting() {
		llColumnSetting.visibility = View.GONE
	}
	
	fun onListListUpdated() {
		etListName.setText("")
	}
	
	override fun onRefresh(direction : SwipyRefreshLayoutDirection) {
		val column = this.column ?: return
		
		// カラムを追加/削除したときに ColumnからColumnViewHolderへの参照が外れることがある
		// リロードやリフレッシュ操作で直るようにする
		column.addColumnViewHolder(this)
		
		if(direction == SwipyRefreshLayoutDirection.TOP && column.canReloadWhenRefreshTop()) {
			refreshLayout.isRefreshing = false
			activity.handler.post {
				this@ColumnViewHolder.column?.startLoading()
			}
			return
		}
		
		column.startRefresh(false, direction == SwipyRefreshLayoutDirection.BOTTOM)
	}
	
	override fun onCheckedChanged(view : CompoundButton, isChecked : Boolean) {
		val column = this@ColumnViewHolder.column
		
		if(binding_busy || column == null || status_adapter == null) return
		
		// カラムを追加/削除したときに ColumnからColumnViewHolderへの参照が外れることがある
		// リロードやリフレッシュ操作で直るようにする
		column.addColumnViewHolder(this)
		
		when(view.id) {
			
			R.id.cbDontCloseColumn -> {
				column.dont_close = isChecked
				showColumnCloseButton()
				activity.app_state.saveColumnList()
			}
			
			R.id.cbWithAttachment -> {
				column.with_attachment = isChecked
				activity.app_state.saveColumnList()
				column.startLoading()
			}
			
			R.id.cbWithHighlight -> {
				column.with_highlight = isChecked
				activity.app_state.saveColumnList()
				column.startLoading()
			}
			
			R.id.cbDontShowBoost -> {
				column.dont_show_boost = isChecked
				activity.app_state.saveColumnList()
				column.startLoading()
			}
			
			R.id.cbDontShowReply -> {
				column.dont_show_reply = isChecked
				activity.app_state.saveColumnList()
				column.startLoading()
			}
			
			R.id.cbDontShowReaction -> {
				column.dont_show_reaction = isChecked
				activity.app_state.saveColumnList()
				column.startLoading()
			}
			
			R.id.cbDontShowVote -> {
				column.dont_show_vote = isChecked
				activity.app_state.saveColumnList()
				column.startLoading()
			}
			
			R.id.cbDontShowNormalToot -> {
				column.dont_show_normal_toot = isChecked
				activity.app_state.saveColumnList()
				column.startLoading()
			}
			
			R.id.cbDontShowFavourite -> {
				column.dont_show_favourite = isChecked
				activity.app_state.saveColumnList()
				column.startLoading()
			}
			
			R.id.cbDontShowFollow -> {
				column.dont_show_follow = isChecked
				activity.app_state.saveColumnList()
				column.startLoading()
			}
			
			R.id.cbInstanceLocal -> {
				column.instance_local = isChecked
				activity.app_state.saveColumnList()
				column.startLoading()
			}
			
			R.id.cbDontStreaming -> {
				column.dont_streaming = isChecked
				activity.app_state.saveColumnList()
				if(isChecked) {
					column.stopStreaming()
				} else {
					column.onStart(activity)
				}
			}
			
			R.id.cbDontAutoRefresh -> {
				column.dont_auto_refresh = isChecked
				activity.app_state.saveColumnList()
			}
			
			R.id.cbHideMediaDefault -> {
				column.hide_media_default = isChecked
				activity.app_state.saveColumnList()
				column.fireShowContent(reason = "HideMediaDefault in ColumnSetting", reset = true)
			}
			
			R.id.cbSystemNotificationNotRelated -> {
				column.system_notification_not_related = isChecked
				activity.app_state.saveColumnList()
			}
			
			R.id.cbEnableSpeech -> {
				column.enable_speech = isChecked
				activity.app_state.saveColumnList()
			}
			
			R.id.cbOldApi -> {
				column.use_old_api = isChecked
				activity.app_state.saveColumnList()
				column.startLoading()
			}
		}
	}
	
	override fun onClick(v : View) {
		val column = this.column
		val status_adapter = this.status_adapter
		if(binding_busy || column == null || status_adapter == null) return
		
		// カラムを追加/削除したときに ColumnからColumnViewHolderへの参照が外れることがある
		// リロードやリフレッシュ操作で直るようにする
		column.addColumnViewHolder(this)
		
		when(v.id) {
			R.id.btnColumnClose -> activity.closeColumn(column)
			
			R.id.btnColumnReload -> {
				App1.custom_emoji_cache.clearErrorCache()
				
				if(column.isSearchColumn) {
					etSearch.hideKeyboard()
					etSearch.setText(column.search_query)
					cbResolve.isChecked = column.search_resolve
				}
				refreshLayout.isRefreshing = false
				column.startLoading()
			}
			
			R.id.btnSearch -> {
				etSearch.hideKeyboard()
				column.search_query = etSearch.text.toString().trim { it <= ' ' }
				column.search_resolve = cbResolve.isChecked
				activity.app_state.saveColumnList()
				column.startLoading()
			}
			
			R.id.btnSearchClear -> {
				etSearch.setText("")
				column.search_query = ""
				column.search_resolve = cbResolve.isChecked
				activity.app_state.saveColumnList()
				column.startLoading()
			}
			
			R.id.llColumnHeader -> scrollToTop2()
			
			R.id.btnColumnSetting -> llColumnSetting.visibility =
				if(llColumnSetting.visibility == View.VISIBLE) View.GONE else View.VISIBLE
			
			R.id.btnDeleteNotification -> Action_Notification.deleteAll(
				activity,
				column.access_info,
				false
			)
			
			R.id.btnColor -> {
				val idx = activity.app_state.column_list.indexOf(column)
				ActColumnCustomize.open(activity, idx, ActMain.REQUEST_CODE_COLUMN_COLOR)
			}
			
			R.id.btnListAdd -> {
				val tv = etListName.text.toString().trim { it <= ' ' }
				if(tv.isEmpty()) {
					showToast(activity, true, R.string.list_name_empty)
					return
				}
				Action_List.create(activity, column.access_info, tv, null)
			}
			
			R.id.llRefreshError -> {
				column.mRefreshLoadingErrorPopupState = 1 - column.mRefreshLoadingErrorPopupState
				showRefreshError()
			}
			
			R.id.btnQuickFilterAll -> clickQuickFilter(Column.QUICK_FILTER_ALL)
			R.id.btnQuickFilterMention -> clickQuickFilter(Column.QUICK_FILTER_MENTION)
			R.id.btnQuickFilterFavourite -> clickQuickFilter(Column.QUICK_FILTER_FAVOURITE)
			R.id.btnQuickFilterBoost -> clickQuickFilter(Column.QUICK_FILTER_BOOST)
			R.id.btnQuickFilterFollow -> clickQuickFilter(Column.QUICK_FILTER_FOLLOW)
			R.id.btnQuickFilterReaction -> clickQuickFilter(Column.QUICK_FILTER_REACTION)
			R.id.btnQuickFilterVote -> clickQuickFilter(Column.QUICK_FILTER_VOTE)
			
		}
		
	}
	
	override fun onLongClick(v : View) : Boolean {
		return when(v.id) {
			R.id.btnColumnClose -> {
				val idx = activity.app_state.column_list.indexOf(column)
				activity.closeColumnAll(idx)
				true
			}
			
			else -> false
		}
	}
	
	private fun showError(message : String) {
		hideRefreshError()
		tvLoading.visibility = View.VISIBLE
		tvLoading.text = message
		
		refreshLayout.isRefreshing = false
		refreshLayout.visibility = View.GONE
		
	}
	
	private fun showColumnCloseButton() {
		val dont_close = column?.dont_close ?: return
		btnColumnClose.isEnabled = ! dont_close
		btnColumnClose.alpha = if(dont_close) 0.3f else 1f
	}
	
	// 相対時刻を更新する
	fun updateRelativeTime() = rebindAdapterItems()
	
	fun rebindAdapterItems() {
		for(childIndex in 0 until listView.childCount) {
			val adapterIndex = listView.getChildAdapterPosition(listView.getChildAt(childIndex))
			if(adapterIndex == androidx.recyclerview.widget.RecyclerView.NO_POSITION) continue
			status_adapter?.notifyItemChanged(adapterIndex)
		}
	}
	
	private val procShowColumnHeader : Runnable = Runnable {
		
		val column = this.column
		if(column == null || column.is_dispose.get()) return@Runnable
		
		val acct = column.access_info.acct
		val ac = AcctColor.load(acct)
		
		val nickname = ac.nickname
		tvColumnContext.text = if(nickname != null && nickname.isNotEmpty())
			nickname
		else
			acct
		
		
		tvColumnContext.setTextColor(
			ac.color_fg.notZero()
				?: getAttributeColor(activity, R.attr.colorTimeSmall)
		)
		
		tvColumnContext.setBackgroundColor(ac.color_bg)
		tvColumnContext.setPaddingRelative(activity.acct_pad_lr, 0, activity.acct_pad_lr, 0)
		
		tvColumnName.text = column.getColumnName(false)
		
		showColumnCloseButton()
		
	}
	
	// カラムヘッダなど、負荷が低い部分の表示更新
	fun showColumnHeader() {
		activity.handler.removeCallbacks(procShowColumnHeader)
		activity.handler.postDelayed(procShowColumnHeader, 50L)
	}
	
	internal fun showContent(
		reason : String,
		changeList : List<AdapterChange>? = null,
		reset : Boolean = false
	) {
		// クラッシュレポートにadapterとリストデータの状態不整合が多かったので、
		// とりあえずリストデータ変更の通知だけは最優先で行っておく
		try {
			status_adapter?.notifyChange(reason, changeList, reset)
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
		showColumnHeader()
		showColumnStatus()
		
		val column = this.column
		if(column == null || column.is_dispose.get()) {
			showError("column was disposed.")
			return
		}
		
		if(! column.bFirstInitialized) {
			showError("initializing")
			return
		}
		
		if(column.bInitialLoading) {
			var message : String? = column.task_progress
			if(message == null) message = "loading?"
			showError(message)
			return
		}
		
		val initialLoadingError = column.mInitialLoadingError
		if(initialLoadingError.isNotEmpty()) {
			showError(initialLoadingError)
			return
		}
		
		val status_adapter = this.status_adapter
		
		if(status_adapter == null || status_adapter.itemCount == 0) {
			showError(activity.getString(R.string.list_empty))
			return
		}
		
		tvLoading.visibility = View.GONE
		
		refreshLayout.visibility = View.VISIBLE
		
		status_adapter.findHeaderViewHolder(listView)?.bindData(column)
		
		if(column.bRefreshLoading) {
			hideRefreshError()
		} else {
			refreshLayout.isRefreshing = false
			showRefreshError()
		}
		proc_restoreScrollPosition.run()
	}
	
	private var bRefreshErrorWillShown = false
	
	private fun hideRefreshError() {
		if(! bRefreshErrorWillShown) return
		bRefreshErrorWillShown = false
		if(llRefreshError.visibility == View.GONE) return
		val aa = AlphaAnimation(1f, 0f)
		aa.duration = 666L
		aa.setAnimationListener(object : Animation.AnimationListener {
			override fun onAnimationRepeat(animation : Animation?) {
			}
			
			override fun onAnimationStart(animation : Animation?) {
			}
			
			override fun onAnimationEnd(animation : Animation?) {
				if(! bRefreshErrorWillShown) llRefreshError.visibility = View.GONE
			}
		})
		llRefreshError.clearAnimation()
		llRefreshError.startAnimation(aa)
	}
	
	private fun showRefreshError() {
		val column = column
		if(column == null) {
			hideRefreshError()
			return
		}
		
		val refreshError = column.mRefreshLoadingError
		//		val refreshErrorTime = column.mRefreshLoadingErrorTime
		if(refreshError.isEmpty()) {
			hideRefreshError()
			return
		}
		
		tvRefreshError.text = refreshError
		when(column.mRefreshLoadingErrorPopupState) {
			// initially expanded
			0 -> {
				tvRefreshError.setSingleLine(false)
				tvRefreshError.ellipsize = null
			}
			
			// tap to minimize
			1 -> {
				tvRefreshError.setSingleLine(true)
				tvRefreshError.ellipsize = TextUtils.TruncateAt.END
			}
		}
		
		if(! bRefreshErrorWillShown) {
			bRefreshErrorWillShown = true
			if(llRefreshError.visibility == View.GONE) {
				llRefreshError.visibility = View.VISIBLE
				val aa = AlphaAnimation(0f, 1f)
				aa.duration = 666L
				llRefreshError.clearAnimation()
				llRefreshError.startAnimation(aa)
			}
		}
	}
	
	fun saveScrollPosition() : Boolean {
		val column = this.column
		when {
			column == null -> log.d("saveScrollPosition [%d] , column==null", page_idx)
			
			column.is_dispose.get() -> log.d(
				"saveScrollPosition [%d] , column is disposed",
				page_idx
			)
			
			listView.visibility != View.VISIBLE -> {
				val scroll_save = ScrollPosition()
				column.scroll_save = scroll_save
				log.d(
					"saveScrollPosition [%d] %s , listView is not visible, save %s,%s"
					, page_idx
					, column.getColumnName(true)
					, scroll_save.adapterIndex
					, scroll_save.offset
				)
				return true
			}
			
			else -> {
				val scroll_save = ScrollPosition(this)
				column.scroll_save = scroll_save
				log.d(
					"saveScrollPosition [%d] %s , listView is visible, save %s,%s"
					, page_idx
					, column.getColumnName(true)
					, scroll_save.adapterIndex
					, scroll_save.offset
				)
				return true
			}
		}
		return false
	}
	
	fun setScrollPosition(sp : ScrollPosition, deltaDp : Float = 0f) {
		val last_adapter = listView.adapter
		if(column == null || last_adapter == null) return
		
		sp.restore(this)
		
		// 復元した後に意図的に少し上下にずらしたい
		val dy = (deltaDp * activity.density + 0.5f).toInt()
		if(dy != 0) listView.postDelayed(Runnable {
			if(column == null || listView.adapter !== last_adapter) return@Runnable
			
			try {
				val recycler = fieldRecycler.get(listView) as androidx.recyclerview.widget.RecyclerView.Recycler
				val state = fieldState.get(listView) as androidx.recyclerview.widget.RecyclerView.State
				listLayoutManager.scrollVerticallyBy(dy, recycler, state)
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e("can't access field in class %s", androidx.recyclerview.widget.RecyclerView::class.java.simpleName)
			}
		}, 20L)
	}
	
	internal inner class AdapterItemHeightWorkarea internal constructor(val adapter : ItemListAdapter) :
		Closeable {
		
		private val item_width : Int
		private val widthSpec : Int
		private var lastViewType : Int = - 1
		private var lastViewHolder : androidx.recyclerview.widget.RecyclerView.ViewHolder? = null
		
		init {
			this.item_width = listView.width - listView.paddingLeft - listView.paddingRight
			this.widthSpec = View.MeasureSpec.makeMeasureSpec(item_width, View.MeasureSpec.EXACTLY)
		}
		
		override fun close() {
			val childViewHolder = lastViewHolder
			if(childViewHolder != null) {
				adapter.onViewRecycled(childViewHolder)
				lastViewHolder = null
			}
		}
		
		// この関数はAdapterViewの項目の(marginを含む)高さを返す
		fun getAdapterItemHeight(adapterIndex : Int) : Int {
			
			fun View.getTotalHeight() : Int {
				measure(widthSpec, heightSpec)
				val lp = layoutParams as? ViewGroup.MarginLayoutParams
				return measuredHeight + (lp?.topMargin ?: 0) + (lp?.bottomMargin ?: 0)
			}
			
			listView.findViewHolderForAdapterPosition(adapterIndex)?.itemView?.let {
				return it.getTotalHeight()
			}
			
			log.d("getAdapterItemHeight idx=$adapterIndex createView")
			
			val viewType = adapter.getItemViewType(adapterIndex)
			
			var childViewHolder = lastViewHolder
			if(childViewHolder == null || lastViewType != viewType) {
				if(childViewHolder != null) {
					adapter.onViewRecycled(childViewHolder)
				}
				childViewHolder = adapter.onCreateViewHolder(listView, viewType)
				lastViewHolder = childViewHolder
				lastViewType = viewType
			}
			adapter.onBindViewHolder(childViewHolder, adapterIndex)
			return childViewHolder.itemView.getTotalHeight()
		}
	}
	
	// 特定の要素が特定の位置に来るようにスクロール位置を調整する
	fun setListItemTop(listIndex : Int, yArg : Int) {
		var adapterIndex = column?.toAdapterIndex(listIndex) ?: return
		
		val adapter = status_adapter
		if(adapter == null) {
			log.e("setListItemTop: missing status adapter")
			return
		}
		
		var y = yArg
		AdapterItemHeightWorkarea(adapter).use { workarea ->
			while(y > 0 && adapterIndex > 0) {
				-- adapterIndex
				y -= workarea.getAdapterItemHeight(adapterIndex)
				y -= ListDivider.height
			}
		}
		
		if(adapterIndex == 0 && y > 0) y = 0
		listLayoutManager.scrollToPositionWithOffset(adapterIndex, y)
	}
	
	// この関数は scrollToPositionWithOffset 用のオフセットを返す
	fun getListItemOffset(listIndex : Int) : Int {
		
		val adapterIndex = column?.toAdapterIndex(listIndex)
			?: return 0
		
		val childView = listLayoutManager.findViewByPosition(adapterIndex)
			?: throw IndexOutOfBoundsException("findViewByPosition($adapterIndex) returns null.")
		
		// スクロールとともにtopは減少する
		// しかしtopMarginがあるので最大値は4である
		// この関数は scrollToPositionWithOffset 用のオフセットを返すので top - topMargin を返す
		return childView.top - ((childView.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin
			?: 0)
	}
	
	fun findFirstVisibleListItem() : Int {
		
		val adapterIndex = listLayoutManager.findFirstVisibleItemPosition()
		
		if(adapterIndex == androidx.recyclerview.widget.RecyclerView.NO_POSITION)
			throw IndexOutOfBoundsException()
		
		return column?.toListIndex(adapterIndex)
			?: throw IndexOutOfBoundsException()
		
	}
	
	fun scrollToTop() {
		try {
			listView.stopScroll()
		} catch(ex : Throwable) {
			log.e(ex,"stopScroll failed.")
		}
		try {
			listLayoutManager.scrollToPositionWithOffset(0, 0)
		} catch(ex : Throwable) {
			log.e(ex,"scrollToPositionWithOffset failed.")
		}
	}
	
	fun scrollToTop2() {
		val status_adapter = this.status_adapter
		if(binding_busy || status_adapter == null) return
		if(status_adapter.itemCount > 0) {
			scrollToTop()
		}
	}
	
	private fun clickQuickFilter(filter : Int) {
		column?.quick_filter = filter
		showQuickFilter()
		activity.app_state.saveColumnList()
		column?.startLoading()
	}
	
	private fun showQuickFilter() {
		val column = this.column ?: return
		
		val isNotificationColumn = column.isNotificationColumn
		vg(svQuickFilter, isNotificationColumn)
		if(! isNotificationColumn) return
		
		vg(btnQuickFilterReaction, column.isMisskey)
		vg(btnQuickFilterFavourite, ! column.isMisskey)
		
		val insideColumnSetting = Pref.bpMoveNotificationsQuickFilter(activity.pref)
		
		val showQuickFilterButton : (btn : View, iconId : Int, selected : Boolean) -> Unit
		
		if(insideColumnSetting) {
			svQuickFilter.setBackgroundColor(0)
			
			val colorFg = getAttributeColor(activity, R.attr.colorContentText)
			val colorBgSelected = colorFg.applyAlphaMultiplier(0.25f)
			val colorFgList = ColorStateList.valueOf(colorFg)
			showQuickFilterButton = { btn, iconId, selected ->
				btn.backgroundDrawable = if(selected) {
					getAdaptiveRippleDrawable(
						colorBgSelected,
						colorFg
					)
				} else {
					ContextCompat.getDrawable(activity, R.drawable.btn_bg_transparent)
				}
				
				when(btn) {
					is TextView -> btn.textColor = colorFg
					
					is ImageButton -> {
						btn.setImageResource(iconId)
						btn.imageTintList = colorFgList
					}
				}
			}
		} else {
			val colorBg = column.getHeaderBackgroundColor()
			val colorFg = column.getHeaderNameColor()
			val colorFgList = ColorStateList.valueOf(colorFg)
			val colorBgSelected = Color.rgb(
				(Color.red(colorBg) * 3 + Color.red(colorFg)) / 4,
				(Color.green(colorBg) * 3 + Color.green(colorFg)) / 4,
				(Color.blue(colorBg) * 3 + Color.blue(colorFg)) / 4
			)
			svQuickFilter.setBackgroundColor(colorBg)
			
			showQuickFilterButton = { btn, iconId, selected ->
				
				btn.backgroundDrawable = getAdaptiveRippleDrawable(
					if(selected) colorBgSelected else colorBg,
					colorFg
				)
				
				when(btn) {
					is TextView -> btn.textColor = colorFg
					
					is ImageButton -> {
						btn.setImageResource(iconId)
						btn.imageTintList = colorFgList
					}
				}
			}
		}
		
		showQuickFilterButton(
			btnQuickFilterAll,
			0,
			column.quick_filter == Column.QUICK_FILTER_ALL
		)
		
		showQuickFilterButton(
			btnQuickFilterMention,
			R.drawable.ic_reply,
			column.quick_filter == Column.QUICK_FILTER_MENTION
		)
		
		showQuickFilterButton(
			btnQuickFilterFavourite,
			R.drawable.ic_star,
			column.quick_filter == Column.QUICK_FILTER_FAVOURITE
		)
		
		showQuickFilterButton(
			btnQuickFilterBoost,
			R.drawable.ic_repeat,
			column.quick_filter == Column.QUICK_FILTER_BOOST
		)
		
		showQuickFilterButton(
			btnQuickFilterFollow,
			R.drawable.ic_follow_plus,
			column.quick_filter == Column.QUICK_FILTER_FOLLOW
		)
		
		showQuickFilterButton(
			btnQuickFilterReaction,
			R.drawable.ic_add,
			column.quick_filter == Column.QUICK_FILTER_REACTION
		)
		
		showQuickFilterButton(
			btnQuickFilterVote,
			R.drawable.ic_vote,
			column.quick_filter == Column.QUICK_FILTER_VOTE
		)
	}
	
}
