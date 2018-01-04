package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.AsyncTask
import android.support.v4.view.ViewCompat
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView

import com.omadahealth.github.swipyrefreshlayout.library.SwipyRefreshLayout
import com.omadahealth.github.swipyrefreshlayout.library.SwipyRefreshLayoutDirection

import java.util.regex.Pattern

import jp.juggler.subwaytooter.action.Action_List
import jp.juggler.subwaytooter.action.Action_Notification
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.view.MyListView
import jp.juggler.subwaytooter.util.ScrollPosition
import jp.juggler.subwaytooter.util.Utils
import java.util.regex.Matcher

internal class ColumnViewHolder(val activity : ActMain, root : View) : View.OnClickListener, SwipyRefreshLayout.OnRefreshListener, CompoundButton.OnCheckedChangeListener {
	
	
	companion object {
		private val log = LogCategory("ColumnViewHolder")
		
		private fun vg(v : View, visible : Boolean) {
			v.visibility = if(visible) View.VISIBLE else View.GONE
		}
	}
	
	var column : Column? = null
	private var status_adapter : ItemListAdapter? = null
	private var page_idx : Int = 0
	
	private val tvLoading : TextView
	val listView : MyListView
	val refreshLayout : SwipyRefreshLayout
	
	private val llColumnHeader : View
	private val tvColumnIndex : TextView
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
	private val cbDontStreaming : CheckBox
	private val cbDontAutoRefresh : CheckBox
	private val cbHideMediaDefault : CheckBox
	private val cbEnableSpeech : CheckBox
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
	private var last_image_task : AsyncTask<Void, Void, Bitmap>? = null
	
	
	private val isRegexValid : Boolean
		get() {
			val s = etRegexFilter.text.toString()
			if(s.isEmpty()) {
				tvRegexFilterError.text = ""
				return true
			}
			val m : Matcher
			try{
				m = Pattern.compile(s).matcher("")
			}catch(ex : Throwable) {
				val message = ex.message
				tvRegexFilterError.text = if(message != null && message.isNotEmpty()) {
					message
				} else {
					Utils.formatError(ex, activity.resources, R.string.regex_error)
				}
				return false
			}
			if( m.find() ) {
				// FIXME: 空文字列にマッチする正規表現はエラー扱いにした方がいいんじゃないだろうか
			}
			return true
		}
	
	val isColumnSettingShown : Boolean
		get() = llColumnSetting.visibility == View.VISIBLE
	
	
	val headerView : HeaderViewHolderBase?
		get() = if(status_adapter == null) null else status_adapter !!.header
	
	val scrollPosition : ScrollPosition
		get() = ScrollPosition(listView)
	
	
	/////////////////////////////////////////////////////////////////
	// Column から呼ばれる
	
	
	init {
		
		if(activity.timeline_font != null) {
			Utils.scanView(root) { v ->
				try {
					if(v is Button) {
						// ボタンは触らない
					} else if(v is TextView) {
						v.typeface = activity.timeline_font
					}
				} catch(ex : Throwable) {
					log.trace(ex)
				}
			}
		}
		
		flColumnBackground = root.findViewById(R.id.flColumnBackground)
		ivColumnBackgroundImage = root.findViewById(R.id.ivColumnBackgroundImage)
		llColumnHeader = root.findViewById(R.id.llColumnHeader)
		
		tvColumnIndex = root.findViewById(R.id.tvColumnIndex)
		
		tvColumnName = root.findViewById(R.id.tvColumnName)
		tvColumnContext = root.findViewById(R.id.tvColumnContext)
		ivColumnIcon = root.findViewById(R.id.ivColumnIcon)
		
		btnColumnSetting = root.findViewById(R.id.btnColumnSetting)
		btnColumnReload = root.findViewById(R.id.btnColumnReload)
		btnColumnClose = root.findViewById(R.id.btnColumnClose)
		
		tvLoading = root.findViewById(R.id.tvLoading)
		listView = root.findViewById(R.id.listView)
		
		btnSearch = root.findViewById(R.id.btnSearch)
		etSearch = root.findViewById(R.id.etSearch)
		cbResolve = root.findViewById(R.id.cbResolve)
		
		llSearch = root.findViewById(R.id.llSearch)
		llListList = root.findViewById(R.id.llListList)
		
		btnListAdd = root.findViewById(R.id.btnListAdd)
		etListName = root.findViewById(R.id.etListName)
		btnListAdd.setOnClickListener(this)
		
		etListName.setOnEditorActionListener { _, actionId, _ ->
			var handled = false
			if(actionId == EditorInfo.IME_ACTION_SEND) {
				btnListAdd.performClick()
				handled = true
			}
			handled
		}
		
		llColumnSetting = root.findViewById(R.id.llColumnSetting)
		
		cbDontCloseColumn = root.findViewById(R.id.cbDontCloseColumn)
		cbWithAttachment = root.findViewById(R.id.cbWithAttachment)
		cbWithHighlight = root.findViewById(R.id.cbWithHighlight)
		cbDontShowBoost = root.findViewById(R.id.cbDontShowBoost)
		cbDontShowFollow = root.findViewById(R.id.cbDontShowFollow)
		cbDontShowFavourite = root.findViewById(R.id.cbDontShowFavourite)
		cbDontShowReply = root.findViewById(R.id.cbDontShowReply)
		cbDontStreaming = root.findViewById(R.id.cbDontStreaming)
		cbDontAutoRefresh = root.findViewById(R.id.cbDontAutoRefresh)
		cbHideMediaDefault = root.findViewById(R.id.cbHideMediaDefault)
		cbEnableSpeech = root.findViewById(R.id.cbEnableSpeech)
		etRegexFilter = root.findViewById(R.id.etRegexFilter)
		llRegexFilter = root.findViewById(R.id.llRegexFilter)
		tvRegexFilterError = root.findViewById(R.id.tvRegexFilterError)
		
		btnDeleteNotification = root.findViewById(R.id.btnDeleteNotification)
		
		llColumnHeader.setOnClickListener(this)
		btnColumnSetting.setOnClickListener(this)
		btnColumnReload.setOnClickListener(this)
		btnColumnClose.setOnClickListener(this)
		btnDeleteNotification.setOnClickListener(this)
		
		root.findViewById<View>(R.id.btnColor).setOnClickListener(this)
		
		this.refreshLayout = root.findViewById(R.id.swipyRefreshLayout)
		refreshLayout.setOnRefreshListener(this)
		refreshLayout.setDistanceToTriggerSync((0.5f + 20f * activity.density).toInt())
		
		cbDontCloseColumn.setOnCheckedChangeListener(this)
		cbWithAttachment.setOnCheckedChangeListener(this)
		cbWithHighlight.setOnCheckedChangeListener(this)
		cbDontShowBoost.setOnCheckedChangeListener(this)
		cbDontShowFollow.setOnCheckedChangeListener(this)
		cbDontShowFavourite.setOnCheckedChangeListener(this)
		cbDontShowReply.setOnCheckedChangeListener(this)
		cbDontStreaming.setOnCheckedChangeListener(this)
		cbDontAutoRefresh.setOnCheckedChangeListener(this)
		cbHideMediaDefault.setOnCheckedChangeListener(this)
		cbEnableSpeech.setOnCheckedChangeListener(this)
		
		// 入力の追跡
		etRegexFilter.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s : CharSequence, start : Int, count : Int, after : Int) {}
			
			override fun onTextChanged(s : CharSequence, start : Int, before : Int, count : Int) {}
			
			override fun afterTextChanged(s : Editable) {
				if(loading_busy) return
				activity.handler.removeCallbacks(proc_start_filter)
				if(isRegexValid) {
					activity.handler.postDelayed(proc_start_filter, 1500L)
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
		if(isPageDestroyed) return@Runnable
		if(isRegexValid) {
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
			
			if(column == null) {
				log.d("restoreScrollPosition [%d], column==null", page_idx)
				return
			}
			
			if(column !!.is_dispose.get()) {
				log.d("restoreScrollPosition [%d], column is disposed", page_idx)
				return
			}
			
			if(column !!.hasMultipleViewHolder()) {
				log.d("restoreScrollPosition [%d] %s , column has multiple view holder. retry later.", page_idx, column !!.getColumnName(true))
				
				// タブレットモードでカラムを追加/削除した際に発生する。
				// このタイミングでスクロール位置を復元してもうまくいかないので延期する
				activity.handler.postDelayed(this, 100L)
				return
			}
			
			val sp = column !!.scroll_save ?: //復元後にもここを通るがこれは正常である
				// log.d( "restoreScrollPosition [%d] %s , column has no saved scroll position.", page_idx, column.getColumnName( true ) );
				return
			
			column !!.scroll_save = null
			
			if(listView.visibility != View.VISIBLE) {
				log.d("restoreScrollPosition [%d] %s , listView is not visible. saved position %s,%s is dropped.", page_idx, column !!.getColumnName(true), sp.pos, sp.top
				)
			} else {
				log.d("restoreScrollPosition [%d] %s , listView is visible. resume %s,%s", page_idx, column !!.getColumnName(true), sp.pos, sp.top
				)
				sp.restore(listView)
			}
			
		}
	}
	
	fun onPageDestroy(page_idx : Int) {
		// タブレットモードの場合、onPageCreateより前に呼ばれる
		
		if(column != null) {
			log.d("onPageDestroy [%d] %s", page_idx, tvColumnName.text)
			saveScrollPosition()
			listView.adapter = null
			column !!.removeColumnViewHolder(this)
			column = null
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
			
			val bSimpleList = column.column_type != Column.TYPE_CONVERSATION && activity.pref.getBoolean(Pref.KEY_SIMPLE_LIST, true)
			
			tvColumnIndex.text = activity.getString(R.string.column_index, page_idx + 1, page_count)
			
			listView.adapter = null
			
			val status_adapter = ItemListAdapter(activity, column, bSimpleList)
			this.status_adapter = status_adapter
			
			status_adapter.header = when(column.column_type) {
				Column.TYPE_PROFILE -> HeaderViewHolderProfile(activity, column, listView)
				Column.TYPE_SEARCH -> HeaderViewHolderSearchDesc(activity, column, listView, activity.getString(R.string.search_desc_mastodon_api))
				Column.TYPE_SEARCH_MSP -> HeaderViewHolderSearchDesc(activity, column, listView, getSearchDesc(R.raw.search_desc_msp_en, R.raw.search_desc_msp_ja))
				Column.TYPE_SEARCH_TS -> HeaderViewHolderSearchDesc(activity, column, listView, getSearchDesc(R.raw.search_desc_ts_en, R.raw.search_desc_ts_ja))
				Column.TYPE_INSTANCE_INFORMATION -> HeaderViewHolderInstance(activity, column, listView)
				else -> null
			}
			
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
			cbDontStreaming.isChecked = column.dont_streaming
			cbDontAutoRefresh.isChecked = column.dont_auto_refresh
			cbHideMediaDefault.isChecked = column.hide_media_default
			cbEnableSpeech.isChecked = column.enable_speech
			
			etRegexFilter.setText(column.regex_text )
			etSearch.setText(column.search_query)
			cbResolve.isChecked = column.search_resolve
			
			vg(cbWithAttachment, bAllowFilter)
			vg(cbWithHighlight, bAllowFilter)
			vg(etRegexFilter, bAllowFilter)
			vg(llRegexFilter, bAllowFilter)
			
			vg(cbDontShowBoost, column.canFilterBoost())
			vg(cbDontShowReply, column.canFilterReply())
			vg(cbDontShowFavourite, isNotificationColumn)
			vg(cbDontShowFollow, isNotificationColumn)
			
			vg(cbDontStreaming, column.canStreaming())
			vg(cbDontAutoRefresh, column.canAutoRefresh())
			vg(cbHideMediaDefault, column.canNSFWDefault())
			vg(cbEnableSpeech, column.canSpeech())
			
			vg(btnDeleteNotification, column.column_type == Column.TYPE_NOTIFICATIONS)
			vg(llSearch, column.isSearchColumn)
			vg(llListList, column.column_type == Column.TYPE_LIST_LIST)
			vg(cbResolve, column.column_type == Column.TYPE_SEARCH)
			
			// tvRegexFilterErrorの表示を更新
			if(bAllowFilter) {
				isRegexValid
			}
			
			when(column.column_type) {
				
				Column.TYPE_CONVERSATION, Column.TYPE_INSTANCE_INFORMATION -> refreshLayout.isEnabled = false
				
				Column.TYPE_SEARCH -> {
					refreshLayout.isEnabled = true
					refreshLayout.direction = SwipyRefreshLayoutDirection.TOP
				}
				else -> {
					refreshLayout.isEnabled = true
					refreshLayout.direction = SwipyRefreshLayoutDirection.BOTH
				}
			}
			
			//
			listView.adapter = status_adapter
			listView.isFastScrollEnabled = ! Pref.pref(activity).getBoolean(Pref.KEY_DISABLE_FAST_SCROLLER, true)
			listView.onItemClickListener = status_adapter
			
			column.addColumnViewHolder(this)
			
			showColumnColor()
			
			showContent()
		} finally {
			loading_busy = false
		}
	}
	
	private fun getSearchDesc(raw_en : Int, raw_ja : Int) : String {
		val res_id = if("ja" == activity.getString(R.string.language_code)) raw_ja else raw_en
		val data = Utils.loadRawResource(activity, res_id)
		return if(data == null) "?" else Utils.decodeUTF8(data)
	}
	
	fun showColumnColor() {
		val column = this.column
		
		if(column != null) {
			
			var c = column.header_bg_color
			if(c == 0) {
				llColumnHeader.setBackgroundResource(R.drawable.btn_bg_ddd)
			} else {
				ViewCompat.setBackground(llColumnHeader, Styler.getAdaptiveRippleDrawable(
					c,
					if(column.header_fg_color != 0)
						column.header_fg_color
					else
						Styler.getAttributeColor(activity, R.attr.colorRippleEffect)
				))
			}
			
			c = column.header_fg_color
			if(c == 0) {
				tvColumnIndex.setTextColor(Styler.getAttributeColor(activity, R.attr.colorColumnHeaderPageNumber))
				tvColumnName.setTextColor(Styler.getAttributeColor(activity, android.R.attr.textColorPrimary))
				Styler.setIconDefaultColor(activity, ivColumnIcon, column.getIconAttrId(column.column_type))
				Styler.setIconDefaultColor(activity, btnColumnSetting, R.attr.ic_tune)
				Styler.setIconDefaultColor(activity, btnColumnReload, R.attr.btn_refresh)
				Styler.setIconDefaultColor(activity, btnColumnClose, R.attr.btn_close)
			} else {
				tvColumnIndex.setTextColor(c)
				tvColumnName.setTextColor(c)
				Styler.setIconCustomColor(activity, ivColumnIcon, c, column.getIconAttrId(column.column_type))
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
			
			status_adapter?.header?.showColor()
		}
	}
	
	private fun closeBitmaps() {
		try {
			ivColumnBackgroundImage.visibility = View.GONE
			ivColumnBackgroundImage.setImageDrawable(null)
			
			if(last_image_bitmap != null) {
				last_image_bitmap !!.recycle()
				last_image_bitmap = null
			}
			
			if(last_image_task != null) {
				last_image_task !!.cancel(true)
				last_image_task = null
			}
			
			last_image_uri = null
			
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
	}
	
	@SuppressLint("StaticFieldLeak")
	private fun loadBackgroundImage(iv : ImageView, url : String?) {
		try {
			if(url == null || url.isEmpty() ) {
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
			last_image_task = object : AsyncTask<Void, Void, Bitmap>() {
				override fun doInBackground(vararg params : Void) : Bitmap? {
					try {
						val resize_max = if(screen_w > screen_h) screen_w else screen_h
						val uri = Uri.parse(url)
						return Utils.createResizedBitmap(log, activity, uri, false, resize_max)
						
					} catch(ex : Throwable) {
						log.trace(ex)
					}
					
					return null
				}
				
				override fun onCancelled(bitmap : Bitmap) {
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
			last_image_task !!.executeOnExecutor(App1.task_executor)
			
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
		if(column == null) return
		
		// カラムを追加/削除したときに ColumnからColumnViewHolderへの参照が外れることがある
		// リロードやリフレッシュ操作で直るようにする
		column !!.addColumnViewHolder(this)
		
		if(direction == SwipyRefreshLayoutDirection.TOP && column !!.canReloadWhenRefreshTop()) {
			refreshLayout.isRefreshing = false
			activity.handler.post { if(column != null) column !!.startLoading() }
			return
		}
		
		column !!.startRefresh(false, direction == SwipyRefreshLayoutDirection.BOTTOM, - 1L, - 1)
	}
	
	override fun onCheckedChanged(view : CompoundButton, isChecked : Boolean) {
		if(loading_busy || column == null || status_adapter == null) return
		
		// カラムを追加/削除したときに ColumnからColumnViewHolderへの参照が外れることがある
		// リロードやリフレッシュ操作で直るようにする
		column !!.addColumnViewHolder(this)
		
		when(view.id) {
			
			R.id.cbDontCloseColumn -> {
				column !!.dont_close = isChecked
				showColumnCloseButton()
				activity.app_state.saveColumnList()
			}
			
			R.id.cbWithAttachment -> {
				column !!.with_attachment = isChecked
				activity.app_state.saveColumnList()
				column !!.startLoading()
			}
			
			R.id.cbWithHighlight -> {
				column !!.with_highlight = isChecked
				activity.app_state.saveColumnList()
				column !!.startLoading()
			}
			
			
			R.id.cbDontShowBoost -> {
				column !!.dont_show_boost = isChecked
				activity.app_state.saveColumnList()
				column !!.startLoading()
			}
			
			R.id.cbDontShowReply -> {
				column !!.dont_show_reply = isChecked
				activity.app_state.saveColumnList()
				column !!.startLoading()
			}
			
			R.id.cbDontShowFavourite -> {
				column !!.dont_show_favourite = isChecked
				activity.app_state.saveColumnList()
				column !!.startLoading()
			}
			
			R.id.cbDontShowFollow -> {
				column !!.dont_show_follow = isChecked
				activity.app_state.saveColumnList()
				column !!.startLoading()
			}
			
			R.id.cbDontStreaming -> {
				column !!.dont_streaming = isChecked
				activity.app_state.saveColumnList()
				if(isChecked) {
					column !!.stopStreaming()
				} else {
					column !!.onStart(activity)
				}
			}
			
			R.id.cbDontAutoRefresh -> {
				column !!.dont_auto_refresh = isChecked
				activity.app_state.saveColumnList()
			}
			
			R.id.cbHideMediaDefault -> {
				column !!.hide_media_default = isChecked
				activity.app_state.saveColumnList()
				column !!.fireShowContent()
			}
			
			R.id.cbEnableSpeech -> {
				column !!.enable_speech = isChecked
				activity.app_state.saveColumnList()
			}
		}
	}
	
	override fun onClick(v : View) {
		val column = this.column
		if(loading_busy || column == null || status_adapter == null) return
		
		// カラムを追加/削除したときに ColumnからColumnViewHolderへの参照が外れることがある
		// リロードやリフレッシュ操作で直るようにする
		column.addColumnViewHolder(this)
		
		when(v.id) {
			R.id.btnColumnClose -> activity.closeColumn(false, column)
			
			R.id.btnColumnReload -> {
				App1.custom_emoji_cache.clearErrorCache()
				
				if(column.isSearchColumn) {
					Utils.hideKeyboard(activity, etSearch)
					etSearch.setText(column.search_query)
					cbResolve.isChecked = column.search_resolve
				}
				refreshLayout.isRefreshing = false
				column.startLoading()
			}
			
			R.id.btnSearch -> {
				Utils.hideKeyboard(activity, etSearch)
				column.search_query = etSearch.text.toString().trim { it <= ' ' }
				column.search_resolve = cbResolve.isChecked
				activity.app_state.saveColumnList()
				column.startLoading()
			}
			
			R.id.llColumnHeader -> if(status_adapter !!.count > 0) listView.setSelectionFromTop(0, 0)
			
			R.id.btnColumnSetting -> llColumnSetting.visibility = if(llColumnSetting.visibility == View.VISIBLE) View.GONE else View.VISIBLE
			
			R.id.btnDeleteNotification -> Action_Notification.deleteAll(activity, column.access_info, false)
			
			R.id.btnColor -> {
				val idx = activity.app_state.column_list.indexOf(column)
				ActColumnCustomize.open(activity, idx, ActMain.REQUEST_CODE_COLUMN_COLOR)
			}
			
			R.id.btnListAdd -> {
				val tv = etListName.text.toString().trim { it <= ' ' }
				if(tv.isEmpty()) {
					Utils.showToast(activity, true, R.string.list_name_empty)
					return
				}
				Action_List.create(activity, column.access_info, tv, null)
			}
		}
		
	}
	
	private fun showError(message : String) {
		tvLoading.visibility = View.VISIBLE
		tvLoading.text = message
		
		refreshLayout.isRefreshing = false
		refreshLayout.visibility = View.GONE
		
	}
	
	private fun showColumnCloseButton() {
		if(column == null) return
		// カラム保護の状態
		btnColumnClose.isEnabled = ! column !!.dont_close
		btnColumnClose.alpha = if(column !!.dont_close) 0.3f else 1f
	}
	
	// カラムヘッダなど、負荷が低い部分の表示更新
	fun showColumnHeader() {
		val column = this.column
		if(column == null) return
		
		val acct = column.access_info.acct
		val ac = AcctColor.load(acct)
		
		val nickname = ac.nickname
		tvColumnContext.text = if( nickname != null && nickname.isNotEmpty() ) nickname else acct
		
		var c : Int

		c = ac.color_fg
		tvColumnContext.setTextColor(if(c != 0) c else Styler.getAttributeColor(activity, R.attr.colorTimeSmall))
		
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
	
	fun showContent() {
		
		// クラッシュレポートにadapterとリストデータの状態不整合が多かったので、
		// とりあえずリストデータ変更の通知だけは最優先で行っておく
		try {
			if(status_adapter != null) status_adapter !!.notifyDataSetChanged()
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
		showColumnHeader()
		
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
		if( initialLoadingError.isNotEmpty() ) {
			showError(initialLoadingError)
			return
		}
		
		val status_adapter = this.status_adapter
		
		if(status_adapter == null || status_adapter.count == 0) {
			showError(activity.getString(R.string.list_empty))
			return
		}
		
		tvLoading.visibility = View.GONE
		
		refreshLayout.visibility = View.VISIBLE
		
		status_adapter.header?.bindData(column)
		
		if(! column.bRefreshLoading) {
			refreshLayout.isRefreshing = false
			val refreshError = column.mRefreshLoadingError
			if(refreshError.isNotEmpty() ) {
				Utils.showToast(activity, true, refreshError)
				column.mRefreshLoadingError = ""
			}
		}
		
		// 表示状態が変わった後にもう一度呼び出す必要があるらしい。。。
		status_adapter.notifyDataSetChanged()
		
		proc_restoreScrollPosition.run()
	}
	
	private fun saveScrollPosition() {
		val column = this.column
		when {
			column == null -> log.d("saveScrollPosition [%d] , column==null", page_idx)
			column.is_dispose.get() -> log.d("saveScrollPosition [%d] , column is disposed", page_idx)
			
			listView.visibility != View.VISIBLE -> {
				val scroll_save = ScrollPosition(0, 0)
				column.scroll_save = scroll_save
				log.d("saveScrollPosition [%d] %s , listView is not visible, save %s,%s"
					, page_idx
					, column.getColumnName(true)
					, scroll_save.pos
					, scroll_save.top
				)
			}
			
			else -> {
				val scroll_save = ScrollPosition(listView)
				column.scroll_save = scroll_save
				log.d("saveScrollPosition [%d] %s , listView is visible, save %s,%s"
					, page_idx
					, column.getColumnName(true)
					, scroll_save.pos
					, scroll_save.top
				)
			}
		}
	}
	
	fun setScrollPosition(sp : ScrollPosition, delta : Float) {
		val last_adapter = listView.adapter
		if(column == null || last_adapter == null) return
		
		sp.restore(listView)
		
		listView.postDelayed(Runnable {
			if(column == null || listView.adapter !== last_adapter) return@Runnable
			listView.scrollListBy((delta * activity.density).toInt())
		}, 20L)
	}
	
	
}
