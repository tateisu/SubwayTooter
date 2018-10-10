package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.AsyncTask
import android.os.SystemClock
import android.support.v4.view.ViewCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import com.omadahealth.github.swipyrefreshlayout.library.SwipyRefreshLayout
import com.omadahealth.github.swipyrefreshlayout.library.SwipyRefreshLayoutDirection
import jp.juggler.subwaytooter.action.Action_List
import jp.juggler.subwaytooter.action.Action_Notification
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.util.*
import jp.juggler.subwaytooter.view.ListDivider
import java.io.Closeable
import java.lang.reflect.Field
import java.util.regex.Pattern

class ColumnViewHolder(
	val activity : ActMain,
	viewRoot : View
) : View.OnClickListener,
	SwipyRefreshLayout.OnRefreshListener,
	CompoundButton.OnCheckedChangeListener, View.OnLongClickListener {
	
	companion object {
		private val log = LogCategory("ColumnViewHolder")
		
		private fun vg(v : View, visible : Boolean) {
			v.visibility = if(visible) View.VISIBLE else View.GONE
		}
		
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
		
		var lastRefreshError : String = ""
		var lastRefreshErrorShown : Long = 0L
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
	
	private val btnSearch : View
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
	
	private val llListList : View
	private val etListName : EditText
	private val btnListAdd : View
	
	private val isPageDestroyed : Boolean
		get() = column == null || activity.isFinishing
	
	private var loading_busy : Boolean = false
	
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
	
	/////////////////////////////////////////////////////////////////
	// Column から呼ばれる
	
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
		
		// 入力の追跡
		etRegexFilter.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(
				s : CharSequence,
				start : Int,
				count : Int,
				after : Int
			) {
			}
			
			override fun onTextChanged(s : CharSequence, start : Int, before : Int, count : Int) {}
			
			override fun afterTextChanged(s : Editable) {
				if(loading_busy) return
				activity.handler.removeCallbacks(proc_start_filter)
				if(isRegexValid()) {
					activity.handler.postDelayed(proc_start_filter, 666L)
				}
			}
		})
		
		btnSearch.setOnClickListener(this)
		etSearch.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, _ ->
			if(! loading_busy) {
				if(actionId == EditorInfo.IME_ACTION_SEARCH) {
					btnSearch.performClick()
					return@OnEditorActionListener true
				}
			}
			false
		})
		
	}
	
	private val proc_start_filter = Runnable {
		if(! isPageDestroyed && isRegexValid()) {
			val column = this.column ?: return@Runnable
			column.regex_text = etRegexFilter.text.toString()
			activity.app_state.saveColumnList()
			column.startLoading()
		}
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
		loading_busy = true
		try {
			this.column = column
			this.page_idx = page_idx
			
			log.d("onPageCreate [%d] %s", page_idx, column.getColumnName(true))
			
			val bSimpleList =
				column.column_type != Column.TYPE_CONVERSATION && Pref.bpSimpleList(activity.pref)
			
			tvColumnIndex.text = activity.getString(R.string.column_index, page_idx + 1, page_count)
			tvColumnStatus.text = "?"
			
			listView.adapter = null
			if(listView.itemDecorationCount == 0) {
				listView.addItemDecoration(ListDivider(activity))
			}
			
			val status_adapter = ItemListAdapter(activity, column, this, bSimpleList)
			this.status_adapter = status_adapter
			
			val isNotificationColumn = column.column_type == Column.TYPE_NOTIFICATIONS
			
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
			vg(cbDontShowVote, isNotificationColumn && column.isMisskey)
			vg(cbDontShowFavourite, isNotificationColumn)
			vg(cbDontShowFollow, isNotificationColumn)
			
			vg(cbInstanceLocal, column.column_type == Column.TYPE_HASHTAG)
			
			
			vg(cbDontStreaming, column.canStreaming())
			vg(cbDontAutoRefresh, column.canAutoRefresh())
			vg(cbHideMediaDefault, column.canNSFWDefault())
			vg(cbSystemNotificationNotRelated, column.column_type == Column.TYPE_NOTIFICATIONS)
			vg(cbEnableSpeech, column.canSpeech())
			vg(cbOldApi, column.column_type == Column.TYPE_DIRECT_MESSAGES)
			
			
			vg(btnDeleteNotification, column.column_type == Column.TYPE_NOTIFICATIONS)
			vg(llSearch, column.isSearchColumn)
			vg(llListList, column.column_type == Column.TYPE_LIST_LIST)
			vg(cbResolve, column.column_type == Column.TYPE_SEARCH)
			
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
			loading_busy = false
		}
	}
	
	fun showColumnStatus() {
		
		val sb = SpannableStringBuilder()
		
		try {
			val column = this.column
			if(column == null) {
				sb.append('?')
			} else {
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
			}
			
		} finally {
			log.d("showColumnStatus ${sb}")
			tvColumnStatus.text = sb
		}
	}
	
	fun showColumnColor() {
		val column = this.column
		
		if(column != null) {
			
			var c = column.header_bg_color
			if(c == 0) {
				llColumnHeader.setBackgroundResource(R.drawable.btn_bg_ddd)
			} else {
				ViewCompat.setBackground(
					llColumnHeader, Styler.getAdaptiveRippleDrawable(
						c,
						if(column.header_fg_color != 0)
							column.header_fg_color
						else
							Styler.getAttributeColor(activity, R.attr.colorRippleEffect)
					)
				)
			}
			
			c = column.header_fg_color
			if(c == 0) {
				tvColumnIndex.setTextColor(
					Styler.getAttributeColor(
						activity,
						R.attr.colorColumnHeaderPageNumber
					)
				)
				tvColumnStatus.setTextColor(
					Styler.getAttributeColor(
						activity,
						R.attr.colorColumnHeaderPageNumber
					)
				)
				tvColumnName.setTextColor(
					Styler.getAttributeColor(
						activity,
						android.R.attr.textColorPrimary
					)
				)
				Styler.setIconDefaultColor(
					activity,
					ivColumnIcon,
					column.getIconAttrId(column.column_type)
				)
				Styler.setIconDefaultColor(activity, btnColumnSetting, R.attr.ic_tune)
				Styler.setIconDefaultColor(activity, btnColumnReload, R.attr.btn_refresh)
				Styler.setIconDefaultColor(activity, btnColumnClose, R.attr.btn_close)
			} else {
				tvColumnIndex.setTextColor(c)
				tvColumnStatus.setTextColor(c)
				tvColumnName.setTextColor(c)
				Styler.setIconCustomColor(
					activity,
					ivColumnIcon,
					c,
					column.getIconAttrId(column.column_type)
				)
				Styler.setIconCustomColor(activity, btnColumnSetting, c, R.attr.ic_tune)
				Styler.setIconCustomColor(activity, btnColumnReload, c, R.attr.btn_refresh)
				Styler.setIconCustomColor(activity, btnColumnClose, c, R.attr.btn_close)
			}
			
			c = column.column_bg_color
			if(c == 0) {
				ViewCompat.setBackground(flColumnBackground, null)
			} else {
				flColumnBackground.setBackgroundColor(c)
			}
			
			ivColumnBackgroundImage.alpha = column.column_bg_image_alpha
			
			loadBackgroundImage(ivColumnBackgroundImage, column.column_bg_image)
			
			status_adapter?.findHeaderViewHolder(listView)?.showColor()
		}
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
			if(url == null || url.isEmpty()) {
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
							activity,
							Uri.parse(url),
							if(screen_w > screen_h) screen_w else screen_h
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
		
		if(loading_busy || column == null || status_adapter == null) return
		
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
		if(loading_busy || column == null || status_adapter == null) return
		
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
			if(adapterIndex == RecyclerView.NO_POSITION) continue
			status_adapter?.notifyItemChanged(adapterIndex)
		}
	}
	
	// カラムヘッダなど、負荷が低い部分の表示更新
	fun showColumnHeader() {
		val column = this.column ?: return
		
		val acct = column.access_info.acct
		val ac = AcctColor.load(acct)
		
		val nickname = ac.nickname
		tvColumnContext.text = if(nickname != null && nickname.isNotEmpty()) nickname else acct
		
		var c : Int
		
		c = ac.color_fg
		tvColumnContext.setTextColor(
			if(c != 0) c else Styler.getAttributeColor(
				activity,
				R.attr.colorTimeSmall
			)
		)
		
		c = ac.color_bg
		if(c == 0) {
			ViewCompat.setBackground(tvColumnContext, null)
		} else {
			tvColumnContext.setBackgroundColor(c)
		}
		tvColumnContext.setPaddingRelative(activity.acct_pad_lr, 0, activity.acct_pad_lr, 0)
		
		tvColumnName.text = column.getColumnName(false)
		
		showColumnCloseButton()
		
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
		
		if(! column.bRefreshLoading) {
			refreshLayout.isRefreshing = false
			val refreshError = column.mRefreshLoadingError
			val refreshErrorTime = column.mRefreshLoadingErrorTime
			if(refreshError.isNotEmpty()) {
				showRefreshError(refreshError, refreshErrorTime)
				column.mRefreshLoadingError = ""
			}
		}
		proc_restoreScrollPosition.run()
	}
	
	private fun showRefreshError(
		refreshError : String,
		@Suppress("UNUSED_PARAMETER") refreshErrorTime : Long
	) {
		// XXX: 同じメッセージを連投しないようにするべきかどうか
		//		if( refreshError == lastRefreshError && refreshErrorTime <= lastRefreshErrorShown + 300000L ){
		//			return
		//		}
		lastRefreshError = refreshError
		lastRefreshErrorShown = SystemClock.elapsedRealtime()
		showToast(activity, true, refreshError)
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
				val scroll_save = ScrollPosition(0, 0)
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
		
		val dy = (deltaDp * activity.density + 0.5f).toInt()
		if(dy != 0) listView.postDelayed(Runnable {
			if(column == null || listView.adapter !== last_adapter) return@Runnable
			
			try {
				val recycler = fieldRecycler.get(listView) as RecyclerView.Recycler
				val state = fieldState.get(listView) as RecyclerView.State
				listLayoutManager.scrollVerticallyBy(dy, recycler, state)
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e("can't access field in class %s", RecyclerView::class.java.simpleName)
			}
		}, 20L)
	}
	
	inner class AdapterItemHeightWorkarea internal constructor(val adapter : ItemListAdapter) :
		Closeable {
		
		private val item_width : Int
		private val widthSpec : Int
		private var lastViewType : Int = - 1
		private var lastViewHolder : RecyclerView.ViewHolder? = null
		
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
		
		fun getAdapterItemHeight(adapterIndex : Int) : Int {
			
			var childViewHolder = listView.findViewHolderForAdapterPosition(adapterIndex)
			if(childViewHolder != null) {
				childViewHolder.itemView.measure(widthSpec, heightSpec)
				return childViewHolder.itemView.measuredHeight
			}
			
			
			log.d("getAdapterItemHeight idx=$adapterIndex createView")
			
			val viewType = adapter.getItemViewType(adapterIndex)
			
			childViewHolder = lastViewHolder
			if(childViewHolder == null || lastViewType != viewType) {
				if(childViewHolder != null) {
					adapter.onViewRecycled(childViewHolder)
				}
				childViewHolder = adapter.onCreateViewHolder(listView, viewType)
				lastViewHolder = childViewHolder
				lastViewType = viewType
			}
			adapter.onBindViewHolder(childViewHolder, adapterIndex)
			childViewHolder.itemView.measure(widthSpec, heightSpec)
			return childViewHolder.itemView.measuredHeight
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
	
	fun getListItemTop(listIndex : Int) : Int {
		
		val adapterIndex = column?.toAdapterIndex(listIndex)
			?: return 0
		
		val childView = listLayoutManager.findViewByPosition(adapterIndex)
			?: throw IndexOutOfBoundsException("findViewByPosition($adapterIndex) returns null.")
		
		return childView.top
	}
	
	fun findFirstVisibleListItem() : Int {
		
		val adapterIndex = listLayoutManager.findFirstVisibleItemPosition()
		
		if(adapterIndex == RecyclerView.NO_POSITION)
			throw IndexOutOfBoundsException()
		
		return column?.toListIndex(adapterIndex)
			?: throw IndexOutOfBoundsException()
		
	}
	
	fun scrollToTop() {
		try {
			listLayoutManager.scrollToPositionWithOffset(0, 0)
		} catch(ignored : Throwable) {
		}
	}
	
	fun scrollToTop2() {
		val status_adapter = this.status_adapter
		if(loading_busy || status_adapter == null) return
		if(status_adapter.itemCount > 0) {
			scrollToTop()
		}
	}
}
