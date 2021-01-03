package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.text.InputType
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.view.*
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.JustifyContent
import com.omadahealth.github.swipyrefreshlayout.library.SwipyRefreshLayout
import com.omadahealth.github.swipyrefreshlayout.library.SwipyRefreshLayoutDirection
import jp.juggler.subwaytooter.action.Action_List
import jp.juggler.subwaytooter.action.Action_Notification
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootTask
import jp.juggler.subwaytooter.api.TootTaskRunner
import jp.juggler.subwaytooter.api.entity.TootAnnouncement
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.dialog.EmojiPicker
import jp.juggler.subwaytooter.span.NetworkEmojiSpan
import jp.juggler.subwaytooter.streaming.*
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.util.*
import jp.juggler.subwaytooter.view.ListDivider
import jp.juggler.subwaytooter.view.MyLinkMovementMethod
import jp.juggler.subwaytooter.view.MyTextView
import jp.juggler.subwaytooter.view.OutsideDrawerLayout
import jp.juggler.util.*
import kotlinx.coroutines.*
import org.jetbrains.anko.*
import org.jetbrains.anko.custom.customView
import java.io.Closeable
import java.lang.Runnable
import java.lang.reflect.Field
import java.util.regex.Pattern

@SuppressLint("ClickableViewAccessibility")
class ColumnViewHolder(
    val activity: ActMain,
    parent: ViewGroup
) : View.OnClickListener,
    SwipyRefreshLayout.OnRefreshListener,
    CompoundButton.OnCheckedChangeListener, View.OnLongClickListener {

    companion object {

        private val log = LogCategory("ColumnViewHolder")

        val fieldRecycler: Field by lazy {
            val field = RecyclerView::class.java.getDeclaredField("mRecycler")
            field.isAccessible = true
            field
        }

        val fieldState: Field by lazy {
            val field = RecyclerView::class.java.getDeclaredField("mState")
            field.isAccessible = true
            field
        }

        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

        //		var lastRefreshError : String = ""
        //		var lastRefreshErrorShown : Long = 0L
    }

    var column: Column? = null
    private var status_adapter: ItemListAdapter? = null
    private var page_idx: Int = 0

    private lateinit var tvLoading: TextView
    lateinit var listView: RecyclerView
    lateinit var refreshLayout: SwipyRefreshLayout

    lateinit var listLayoutManager: LinearLayoutManager

    private lateinit var llColumnHeader: View
    private lateinit var tvColumnIndex: TextView
    private lateinit var tvColumnStatus: TextView
    private lateinit var tvColumnContext: TextView
    private lateinit var ivColumnIcon: ImageView
    private lateinit var tvColumnName: TextView

    private lateinit var llColumnSetting: View
    private lateinit var llColumnSettingInside: LinearLayout

    private lateinit var btnSearch: ImageButton
    private lateinit var btnSearchClear: ImageButton
    private lateinit var etSearch: EditText
    private lateinit var cbResolve: CheckBox
    private lateinit var etRegexFilter: EditText
    private lateinit var tvRegexFilterError: TextView

    private lateinit var btnAnnouncementsBadge: ImageView
    private lateinit var btnAnnouncements: ImageButton
    private lateinit var btnAnnouncementsCutout: Paint
    private lateinit var btnColumnSetting: ImageButton
    private lateinit var btnColumnReload: ImageButton
    private lateinit var btnColumnClose: ImageButton

    private lateinit var flColumnBackground: View
    private lateinit var ivColumnBackgroundImage: ImageView
    private lateinit var llSearch: View
    private lateinit var cbDontCloseColumn: CheckBox
    private lateinit var cbRemoteOnly: CheckBox
    private lateinit var cbWithAttachment: CheckBox
    private lateinit var cbWithHighlight: CheckBox
    private lateinit var cbDontShowBoost: CheckBox
    private lateinit var cbDontShowFollow: CheckBox
    private lateinit var cbDontShowFavourite: CheckBox
    private lateinit var cbDontShowReply: CheckBox
    private lateinit var cbDontShowReaction: CheckBox
    private lateinit var cbDontShowVote: CheckBox
    private lateinit var cbDontShowNormalToot: CheckBox
    private lateinit var cbDontShowNonPublicToot: CheckBox
    private lateinit var cbInstanceLocal: CheckBox
    private lateinit var cbDontStreaming: CheckBox
    private lateinit var cbDontAutoRefresh: CheckBox
    private lateinit var cbHideMediaDefault: CheckBox
    private lateinit var cbSystemNotificationNotRelated: CheckBox
    private lateinit var cbEnableSpeech: CheckBox
    private lateinit var cbOldApi: CheckBox
    private lateinit var llRegexFilter: View
    private lateinit var btnDeleteNotification: Button
    private lateinit var btnColor: Button
    private lateinit var btnLanguageFilter: Button

    private lateinit var svQuickFilter: HorizontalScrollView
    private lateinit var btnQuickFilterAll: Button
    private lateinit var btnQuickFilterMention: ImageButton
    private lateinit var btnQuickFilterFavourite: ImageButton
    private lateinit var btnQuickFilterBoost: ImageButton
    private lateinit var btnQuickFilterFollow: ImageButton
    private lateinit var btnQuickFilterPost: ImageButton

    private lateinit var btnQuickFilterReaction: ImageButton
    private lateinit var btnQuickFilterVote: ImageButton

    private lateinit var llRefreshError: FrameLayout
    private lateinit var ivRefreshError: ImageView
    private lateinit var tvRefreshError: TextView

    private lateinit var llListList: View
    private lateinit var etListName: EditText
    private lateinit var btnListAdd: View

    private lateinit var llHashtagExtra: LinearLayout
    private lateinit var etHashtagExtraAny: EditText
    private lateinit var etHashtagExtraAll: EditText
    private lateinit var etHashtagExtraNone: EditText

    private lateinit var llAnnouncementsBox: View
    private lateinit var tvAnnouncementsCaption: TextView
    private lateinit var tvAnnouncementsIndex: TextView
    private lateinit var btnAnnouncementsPrev: ImageButton
    private lateinit var btnAnnouncementsNext: ImageButton
    private lateinit var llAnnouncements: View
    private lateinit var tvAnnouncementPeriod: TextView
    private lateinit var tvAnnouncementContent: MyTextView
    private lateinit var llAnnouncementExtra: LinearLayout

    private val announcementContentInvalidator: NetworkEmojiInvalidator

    var lastAnnouncementShown = 0L

    private val extra_invalidator_list = ArrayList<NetworkEmojiInvalidator>()

    private val isPageDestroyed: Boolean
        get() = column == null || activity.isFinishing

    private var binding_busy: Boolean = false

    private var last_image_uri: String? = null
    private var last_image_bitmap: Bitmap? = null
    private var last_image_task: Job? = null

    private fun checkRegexFilterError(src: String): String? {
        try {
            if (src.isEmpty()) {
                return null
            }
            val m = Pattern.compile(src).matcher("")
            if (m.find()) {
                // 空文字列にマッチする正規表現はエラー扱いにする
                // そうしないとCWの警告テキストにマッチしてしまう
                return activity.getString(R.string.regex_filter_matches_empty_string)
            }
            return null
        } catch (ex: Throwable) {
            val message = ex.message
            return if (message != null && message.isNotEmpty()) {
                message
            } else {
                ex.withCaption(activity.resources, R.string.regex_error)
            }
        }
    }

    private fun isRegexValid(): Boolean {
        val s = etRegexFilter.text.toString()
        val error = checkRegexFilterError(s)
        tvRegexFilterError.text = error ?: ""
        return error == null
    }

    val isColumnSettingShown: Boolean
        get() = llColumnSetting.visibility == View.VISIBLE

    //	val headerView : HeaderViewHolderBase?
    //		get() = status_adapter?.header

    val scrollPosition: ScrollPosition
        get() = ScrollPosition(this)

    inner class ErrorFlickListener(
        context: Context
    ) : View.OnTouchListener, GestureDetector.OnGestureListener {

        private val gd = GestureDetector(context, this)
        private val density = context.resources.displayMetrics.density

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            return gd.onTouchEvent(event)
        }

        override fun onShowPress(e: MotionEvent?) {
        }

        override fun onLongPress(e: MotionEvent?) {
        }

        override fun onSingleTapUp(e: MotionEvent?): Boolean {
            return true
        }

        override fun onDown(e: MotionEvent?): Boolean {
            return true
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent?,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent?,
            velocityX: Float,
            velocityY: Float
        ): Boolean {

            val vx = velocityX.abs()
            val vy = velocityY.abs()
            if (vy < vx * 1.5f) {
                // フリック方向が上下ではない
                log.d("fling? not vertical view. $vx $vy")
            } else {

                val vydp = vy / density
                val limit = 1024f
                log.d("fling? $vydp/$limit")
                if (vydp >= limit) {

                    val column = column
                    if (column != null && column.lastTask == null) {
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

    val viewRoot: View = inflate(activity, parent)

    init {

        viewRoot.scan { v ->
            try {
                // ボタンではないTextViewのフォントを変更する
                if (v is TextView && v !is Button) {
                    v.typeface = ActMain.timeline_font
                }
            } catch (ex: Throwable) {
                log.trace(ex)
            }
        }



        if (Pref.bpShareViewPool(activity.pref)) {
            listView.setRecycledViewPool(activity.viewPool)
        }
        listView.itemAnimator = null
        //
        //		val animator = listView.itemAnimator
        //		if( animator is DefaultItemAnimator){
        //			animator.supportsChangeAnimations = false
        //		}

        btnListAdd.setOnClickListener(this)

        etListName.setOnEditorActionListener { _, actionId, _ ->
            var handled = false
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                btnListAdd.performClick()
                handled = true
            }
            handled
        }

        btnQuickFilterAll.setOnClickListener(this)
        btnQuickFilterMention.setOnClickListener(this)
        btnQuickFilterFavourite.setOnClickListener(this)
        btnQuickFilterBoost.setOnClickListener(this)
        btnQuickFilterFollow.setOnClickListener(this)
        btnQuickFilterPost.setOnClickListener(this)
        btnQuickFilterReaction.setOnClickListener(this)
        btnQuickFilterVote.setOnClickListener(this)

        llColumnHeader.setOnClickListener(this)
        btnAnnouncements.setOnClickListener(this)
        btnColumnSetting.setOnClickListener(this)
        btnColumnReload.setOnClickListener(this)
        btnColumnClose.setOnClickListener(this)
        btnColumnClose.setOnLongClickListener(this)
        btnDeleteNotification.setOnClickListener(this)

        btnColor.setOnClickListener(this)
        btnLanguageFilter.setOnClickListener(this)

        refreshLayout.setOnRefreshListener(this)
        refreshLayout.setDistanceToTriggerSync((0.5f + 20f * activity.density).toInt())

        llRefreshError.setOnClickListener(this)

        btnAnnouncementsPrev.setOnClickListener(this)
        btnAnnouncementsNext.setOnClickListener(this)

        cbDontCloseColumn.setOnCheckedChangeListener(this)
        cbRemoteOnly.setOnCheckedChangeListener(this)
        cbWithAttachment.setOnCheckedChangeListener(this)
        cbWithHighlight.setOnCheckedChangeListener(this)
        cbDontShowBoost.setOnCheckedChangeListener(this)
        cbDontShowFollow.setOnCheckedChangeListener(this)
        cbDontShowFavourite.setOnCheckedChangeListener(this)
        cbDontShowReply.setOnCheckedChangeListener(this)
        cbDontShowReaction.setOnCheckedChangeListener(this)
        cbDontShowVote.setOnCheckedChangeListener(this)
        cbDontShowNormalToot.setOnCheckedChangeListener(this)
        cbDontShowNonPublicToot.setOnCheckedChangeListener(this)
        cbInstanceLocal.setOnCheckedChangeListener(this)
        cbDontStreaming.setOnCheckedChangeListener(this)
        cbDontAutoRefresh.setOnCheckedChangeListener(this)
        cbHideMediaDefault.setOnCheckedChangeListener(this)
        cbSystemNotificationNotRelated.setOnCheckedChangeListener(this)
        cbEnableSpeech.setOnCheckedChangeListener(this)
        cbOldApi.setOnCheckedChangeListener(this)

        if (Pref.bpMoveNotificationsQuickFilter(activity.pref)) {
            (svQuickFilter.parent as? ViewGroup)?.removeView(svQuickFilter)
            llColumnSettingInside.addView(svQuickFilter, 0)

            svQuickFilter.setOnTouchListener { v, event ->
                val action = event.action
                if (action == MotionEvent.ACTION_DOWN) {
                    val sv = v as? HorizontalScrollView
                    if (sv != null && sv.getChildAt(0).width > sv.width) {
                        sv.requestDisallowInterceptTouchEvent(true)
                    }
                }
                v.onTouchEvent(event)
            }
        }

        if (!activity.header_text_size_sp.isNaN()) {
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

        btnAnnouncements.layoutParams.width = wh
        btnAnnouncements.layoutParams.height = wh
        btnAnnouncements.setPaddingRelative(pad, pad, pad, pad)

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
            if (!binding_busy) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    btnSearch.performClick()
                    return@OnEditorActionListener true
                }
            }
            false
        })

        // 入力の追跡
        etRegexFilter.addTextChangedListener(CustomTextWatcher {
            if (binding_busy || isPageDestroyed) return@CustomTextWatcher
            if (!isRegexValid()) return@CustomTextWatcher
            column?.regex_text = etRegexFilter.text.toString()
            activity.app_state.saveColumnList()
            activity.handler.removeCallbacks(proc_start_filter)
            activity.handler.postDelayed(proc_start_filter, 666L)
        })

        etHashtagExtraAny.addTextChangedListener(CustomTextWatcher {
            if (binding_busy || isPageDestroyed) return@CustomTextWatcher
            column?.hashtag_any = etHashtagExtraAny.text.toString()
            activity.app_state.saveColumnList()
            activity.handler.removeCallbacks(proc_start_filter)
            activity.handler.postDelayed(proc_start_filter, 666L)
        })

        etHashtagExtraAll.addTextChangedListener(CustomTextWatcher {
            if (binding_busy || isPageDestroyed) return@CustomTextWatcher
            column?.hashtag_all = etHashtagExtraAll.text.toString()
            activity.app_state.saveColumnList()
            activity.handler.removeCallbacks(proc_start_filter)
            activity.handler.postDelayed(proc_start_filter, 666L)
        })

        etHashtagExtraNone.addTextChangedListener(CustomTextWatcher {
            if (binding_busy || isPageDestroyed) return@CustomTextWatcher
            column?.hashtag_none = etHashtagExtraNone.text.toString()
            activity.app_state.saveColumnList()
            activity.handler.removeCallbacks(proc_start_filter)
            activity.handler.postDelayed(proc_start_filter, 666L)
        })

        announcementContentInvalidator =
            NetworkEmojiInvalidator(activity.handler, tvAnnouncementContent)
        tvAnnouncementContent.movementMethod = MyLinkMovementMethod
    }

    private val proc_start_filter: Runnable = Runnable {
        if (binding_busy || isPageDestroyed) return@Runnable
        column?.startLoading()
    }

    private val proc_restoreScrollPosition = object : Runnable {
        override fun run() {
            activity.handler.removeCallbacks(this)

            if (isPageDestroyed) {
                log.d("restoreScrollPosition [%d], page is destroyed.")
                return
            }

            val column = this@ColumnViewHolder.column
            if (column == null) {
                log.d("restoreScrollPosition [%d], column==null", page_idx)
                return
            }

            if (column.is_dispose.get()) {
                log.d("restoreScrollPosition [%d], column is disposed", page_idx)
                return
            }

            if (column.hasMultipleViewHolder()) {
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
            if (sp == null) {
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
                    "restoreScrollPosition [$page_idx] %s , column has no saved scroll position.",
                    column.getColumnName(true)
                )
                return
            }

            column.scroll_save = null

            if (listView.visibility != View.VISIBLE) {
                log.d(
                    "restoreScrollPosition [$page_idx] %s , listView is not visible. saved position %s,%s is dropped.",
                    column.getColumnName(true),
                    sp.adapterIndex,
                    sp.offset
                )
            } else {
                log.d(
                    "restoreScrollPosition [%d] %s , listView is visible. resume %s,%s",
                    page_idx,
                    column.getColumnName(true),
                    sp.adapterIndex,
                    sp.offset
                )
                sp.restore(this@ColumnViewHolder)
            }

        }
    }

    fun onPageDestroy(page_idx: Int) {
        // タブレットモードの場合、onPageCreateより前に呼ばれる
        val column = this@ColumnViewHolder.column
        if (column != null) {
            log.d("onPageDestroy [%d] %s", page_idx, tvColumnName.text)
            saveScrollPosition()
            listView.adapter = null
            column.removeColumnViewHolder(this)
            this@ColumnViewHolder.column = null
        }

        closeBitmaps()

        activity.closeListItemPopup()
    }

    fun onPageCreate(column: Column, page_idx: Int, page_count: Int) {
        binding_busy = true
        try {
            this.column = column
            this.page_idx = page_idx

            log.d("onPageCreate [%d] %s", page_idx, column.getColumnName(true))

            val bSimpleList =
                column.type != ColumnType.CONVERSATION && Pref.bpSimpleList(activity.pref)

            tvColumnIndex.text = activity.getString(R.string.column_index, page_idx + 1, page_count)
            tvColumnStatus.text = "?"
            ivColumnIcon.setImageResource(column.getIconId())

            listView.adapter = null
            if (listView.itemDecorationCount == 0) {
                listView.addItemDecoration(ListDivider(activity))
            }

            val status_adapter = ItemListAdapter(activity, column, this, bSimpleList)
            this.status_adapter = status_adapter

            val isNotificationColumn = column.isNotificationColumn

            // 添付メディアや正規表現のフィルタ
            val bAllowFilter = column.canStatusFilter()

            showColumnSetting(false)



            cbDontCloseColumn.isCheckedNoAnime = column.dont_close
            cbRemoteOnly.isCheckedNoAnime = column.remote_only
            cbWithAttachment.isCheckedNoAnime = column.with_attachment
            cbWithHighlight.isCheckedNoAnime = column.with_highlight
            cbDontShowBoost.isCheckedNoAnime = column.dont_show_boost
            cbDontShowFollow.isCheckedNoAnime = column.dont_show_follow
            cbDontShowFavourite.isCheckedNoAnime = column.dont_show_favourite
            cbDontShowReply.isCheckedNoAnime = column.dont_show_reply
            cbDontShowReaction.isCheckedNoAnime = column.dont_show_reaction
            cbDontShowVote.isCheckedNoAnime = column.dont_show_vote
            cbDontShowNormalToot.isCheckedNoAnime = column.dont_show_normal_toot
            cbDontShowNonPublicToot.isCheckedNoAnime = column.dont_show_non_public_toot
            cbInstanceLocal.isCheckedNoAnime = column.instance_local
            cbDontStreaming.isCheckedNoAnime = column.dont_streaming
            cbDontAutoRefresh.isCheckedNoAnime = column.dont_auto_refresh
            cbHideMediaDefault.isCheckedNoAnime = column.hide_media_default
            cbSystemNotificationNotRelated.isCheckedNoAnime = column.system_notification_not_related
            cbEnableSpeech.isCheckedNoAnime = column.enable_speech
            cbOldApi.isCheckedNoAnime = column.use_old_api

            etRegexFilter.setText(column.regex_text)
            etSearch.setText(column.search_query)
            cbResolve.isCheckedNoAnime = column.search_resolve

            cbRemoteOnly.vg(column.canRemoteOnly())

            cbWithAttachment.vg(bAllowFilter)
            cbWithHighlight.vg(bAllowFilter)
            etRegexFilter.vg(bAllowFilter)
            llRegexFilter.vg(bAllowFilter)
            btnLanguageFilter.vg(bAllowFilter)

            cbDontShowBoost.vg(column.canFilterBoost())
            cbDontShowReply.vg(column.canFilterReply())
            cbDontShowNormalToot.vg(column.canFilterNormalToot())
            cbDontShowNonPublicToot.vg(column.canFilterNonPublicToot())
            cbDontShowReaction.vg(isNotificationColumn && column.isMisskey)
            cbDontShowVote.vg(isNotificationColumn)
            cbDontShowFavourite.vg(isNotificationColumn && !column.isMisskey)
            cbDontShowFollow.vg(isNotificationColumn)

            cbInstanceLocal.vg(column.type == ColumnType.HASHTAG)


            cbDontStreaming.vg(column.canStreaming())
            cbDontAutoRefresh.vg(column.canAutoRefresh())
            cbHideMediaDefault.vg(column.canNSFWDefault())
            cbSystemNotificationNotRelated.vg(column.isNotificationColumn)
            cbEnableSpeech.vg(column.canSpeech())
            cbOldApi.vg(column.type == ColumnType.DIRECT_MESSAGES)


            btnDeleteNotification.vg(column.isNotificationColumn)

            llSearch.vg(column.isSearchColumn)?.let {
                btnSearchClear.vg(Pref.bpShowSearchClear(activity.pref))
            }

            llListList.vg(column.type == ColumnType.LIST_LIST)
            cbResolve.vg(column.type == ColumnType.SEARCH)

            llHashtagExtra.vg(column.hasHashtagExtra())
            etHashtagExtraAny.setText(column.hashtag_any)
            etHashtagExtraAll.setText(column.hashtag_all)
            etHashtagExtraNone.setText(column.hashtag_none)

            // tvRegexFilterErrorの表示を更新
            if (bAllowFilter) {
                isRegexValid()
            }

            val canRefreshTop = column.canRefreshTopBySwipe()
            val canRefreshBottom = column.canRefreshBottomBySwipe()

            refreshLayout.isEnabled = canRefreshTop || canRefreshBottom
            refreshLayout.direction = if (canRefreshTop && canRefreshBottom) {
                SwipyRefreshLayoutDirection.BOTH
            } else if (canRefreshTop) {
                SwipyRefreshLayoutDirection.TOP
            } else {
                SwipyRefreshLayoutDirection.BOTTOM
            }

            bRefreshErrorWillShown = false
            llRefreshError.clearAnimation()
            llRefreshError.visibility = View.GONE

            //
            listView.adapter = status_adapter

            //XXX FastScrollerのサポートを諦める。ライブラリはいくつかあるんだけど、設定でON/OFFできなかったり頭文字バブルを無効にできなかったり
            // listView.isFastScrollEnabled = ! Pref.bpDisableFastScroller(Pref.pref(activity))

            column.addColumnViewHolder(this)

            lastAnnouncementShown = -1L

            fun dip(dp: Int): Int = (activity.density * dp + 0.5f).toInt()
            val context = activity

            val announcementsBgColor = Pref.ipAnnouncementsBgColor(App1.pref).notZero()
                ?: context.getAttributeColor(R.attr.colorSearchFormBackground)

            btnAnnouncementsCutout.apply {
                color = announcementsBgColor
            }

            llAnnouncementsBox.apply {
                background = createRoundDrawable(dip(6).toFloat(), announcementsBgColor)
                val pad_tb = dip(2)
                setPadding(0, pad_tb, 0, pad_tb)
            }

            val searchBgColor = Pref.ipSearchBgColor(App1.pref).notZero()
                ?: context.getAttributeColor(R.attr.colorSearchFormBackground)

            llSearch.apply {
                backgroundColor = searchBgColor
                startPadding = dip(12)
                endPadding = dip(12)
                topPadding = dip(3)
                bottomPadding = dip(3)
            }

            llListList.apply {
                backgroundColor = searchBgColor
                startPadding = dip(12)
                endPadding = dip(12)
                topPadding = dip(3)
                bottomPadding = dip(3)
            }

            showColumnColor()

            showContent(reason = "onPageCreate", reset = true)
        } finally {
            binding_busy = false
        }
    }

    private val procShowColumnStatus: Runnable = Runnable {

        val column = this.column
        if (column == null || column.is_dispose.get()) return@Runnable

        val sb = SpannableStringBuilder()
        try {

            val task = column.lastTask
            if (task != null) {
                sb.append(task.ctType.marker) // L,T,B,G
                sb.append(
                    when {
                        task.isCancelled -> "~"
                        task.ctClosed.get() -> "!"
                        task.ctStarted.get() -> ""
                        else -> "?"
                    }
                )
            }
            val streamStatus =column.getStreamingStatus()
            log.d("procShowColumnStatus: streamStatus=${streamStatus}, column=${column.access_info.acct}/${column.getColumnName(true)}")

            when (streamStatus) {
                StreamStatus.Missing,StreamStatus.Closed-> {
                }
                StreamStatus.Connecting,StreamStatus.Open->{
                    sb.appendColorShadeIcon(activity, R.drawable.ic_pulse, "Streaming")
                    sb.append("?")
                }
                StreamStatus.Subscribed->{
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
        if (column == null || column.is_dispose.get()) return

        // カラムヘッダ背景
        column.setHeaderBackground(llColumnHeader)

        // カラムヘッダ文字色(A)
        var c = column.getHeaderNameColor()
        val csl = ColorStateList.valueOf(c)
        tvColumnName.textColor = c
        ivColumnIcon.imageTintList = csl
        btnAnnouncements.imageTintList = csl
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

        showAnnouncements(force = false)
    }

    private fun closeBitmaps() {
        try {
            ivColumnBackgroundImage.visibility = View.GONE
            ivColumnBackgroundImage.setImageDrawable(null)

            last_image_bitmap?.recycle()
            last_image_bitmap = null

            last_image_task?.cancel()
            last_image_task = null

            last_image_uri = null

        } catch (ex: Throwable) {
            log.trace(ex)
        }

    }

    @SuppressLint("StaticFieldLeak")
    private fun loadBackgroundImage(iv: ImageView, url: String?) {
        try {
            if (url == null || url.isEmpty() || Pref.bpDontShowColumnBackgroundImage(activity.pref)) {
                // 指定がないなら閉じる
                closeBitmaps()
                return
            }

            if (url == last_image_uri) {
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
            last_image_task = GlobalScope.launch(Dispatchers.Main) {
                val bitmap = try {
                    withContext(Dispatchers.IO) {
                        try {
                            createResizedBitmap(
                                activity, url.toUri(),
                                if (screen_w > screen_h)
                                    screen_w
                                else
                                    screen_h
                            )
                        } catch (ex: Throwable) {
                            log.trace(ex)
                            null
                        }
                    }
                } catch (ex: Throwable) {
                    null
                }
                if (bitmap != null) {
                    if (!coroutineContext.isActive || url != last_image_uri) {
                        bitmap.recycle()
                    } else {
                        last_image_bitmap = bitmap
                        iv.setImageBitmap(last_image_bitmap)
                        iv.visibility = View.VISIBLE
                    }
                }
            }
        } catch (ex: Throwable) {
            log.trace(ex)
        }

    }

    fun showColumnSetting(show: Boolean): Boolean {
        llColumnSetting.vg(show)
        llColumnHeader.invalidate()
        return show
    }

    fun onListListUpdated() {
        etListName.setText("")
    }

    override fun onRefresh(direction: SwipyRefreshLayoutDirection) {
        val column = this.column ?: return

        // カラムを追加/削除したときに ColumnからColumnViewHolderへの参照が外れることがある
        // リロードやリフレッシュ操作で直るようにする
        column.addColumnViewHolder(this)

        if (direction == SwipyRefreshLayoutDirection.TOP && column.canReloadWhenRefreshTop()) {
            refreshLayout.isRefreshing = false
            activity.handler.post {
                this@ColumnViewHolder.column?.startLoading()
            }
            return
        }

        column.startRefresh(false, direction == SwipyRefreshLayoutDirection.BOTTOM)
    }

    override fun onCheckedChanged(view: CompoundButton, isChecked: Boolean) {
        val column = this@ColumnViewHolder.column

        if (binding_busy || column == null || status_adapter == null) return

        // カラムを追加/削除したときに ColumnからColumnViewHolderへの参照が外れることがある
        // リロードやリフレッシュ操作で直るようにする
        column.addColumnViewHolder(this)

        when (view) {

            cbDontCloseColumn -> {
                column.dont_close = isChecked
                showColumnCloseButton()
                activity.app_state.saveColumnList()
            }

            cbWithAttachment -> {
                column.with_attachment = isChecked
                activity.app_state.saveColumnList()
                column.startLoading()
            }

            cbRemoteOnly -> {
                column.remote_only = isChecked
                activity.app_state.saveColumnList()
                column.startLoading()
            }

            cbWithHighlight -> {
                column.with_highlight = isChecked
                activity.app_state.saveColumnList()
                column.startLoading()
            }

            cbDontShowBoost -> {
                column.dont_show_boost = isChecked
                activity.app_state.saveColumnList()
                column.startLoading()
            }

            cbDontShowReply -> {
                column.dont_show_reply = isChecked
                activity.app_state.saveColumnList()
                column.startLoading()
            }

            cbDontShowReaction -> {
                column.dont_show_reaction = isChecked
                activity.app_state.saveColumnList()
                column.startLoading()
            }

            cbDontShowVote -> {
                column.dont_show_vote = isChecked
                activity.app_state.saveColumnList()
                column.startLoading()
            }

            cbDontShowNormalToot -> {
                column.dont_show_normal_toot = isChecked
                activity.app_state.saveColumnList()
                column.startLoading()
            }

            cbDontShowNonPublicToot -> {
                column.dont_show_non_public_toot = isChecked
                activity.app_state.saveColumnList()
                column.startLoading()
            }

            cbDontShowFavourite -> {
                column.dont_show_favourite = isChecked
                activity.app_state.saveColumnList()
                column.startLoading()
            }

            cbDontShowFollow -> {
                column.dont_show_follow = isChecked
                activity.app_state.saveColumnList()
                column.startLoading()
            }

            cbInstanceLocal -> {
                column.instance_local = isChecked
                activity.app_state.saveColumnList()
                column.startLoading()
            }

            cbDontStreaming -> {
                column.dont_streaming = isChecked
                activity.app_state.saveColumnList()
                activity.app_state.streamManager.updateStreamingColumns()
            }

            cbDontAutoRefresh -> {
                column.dont_auto_refresh = isChecked
                activity.app_state.saveColumnList()
            }

            cbHideMediaDefault -> {
                column.hide_media_default = isChecked
                activity.app_state.saveColumnList()
                column.fireShowContent(reason = "HideMediaDefault in ColumnSetting", reset = true)
            }

            cbSystemNotificationNotRelated -> {
                column.system_notification_not_related = isChecked
                activity.app_state.saveColumnList()
            }

            cbEnableSpeech -> {
                column.enable_speech = isChecked
                activity.app_state.saveColumnList()
            }

            cbOldApi -> {
                column.use_old_api = isChecked
                activity.app_state.saveColumnList()
                column.startLoading()
            }
        }
    }

    override fun onClick(v: View) {
        val column = this.column
        val status_adapter = this.status_adapter
        if (binding_busy || column == null || status_adapter == null) return

        // カラムを追加/削除したときに ColumnからColumnViewHolderへの参照が外れることがある
        // リロードやリフレッシュ操作で直るようにする
        column.addColumnViewHolder(this)

        when (v) {
            btnColumnClose -> activity.closeColumn(column)

            btnColumnReload -> {
                App1.custom_emoji_cache.clearErrorCache()

                if (column.isSearchColumn) {
                    etSearch.hideKeyboard()
                    etSearch.setText(column.search_query)
                    cbResolve.isCheckedNoAnime = column.search_resolve
                }
                refreshLayout.isRefreshing = false
                column.startLoading()
            }

            btnSearch -> {
                etSearch.hideKeyboard()
                column.search_query = etSearch.text.toString().trim { it <= ' ' }
                column.search_resolve = cbResolve.isChecked
                activity.app_state.saveColumnList()
                column.startLoading()
            }

            btnSearchClear -> {
                etSearch.setText("")
                column.search_query = ""
                column.search_resolve = cbResolve.isChecked
                activity.app_state.saveColumnList()
                column.startLoading()
            }

            llColumnHeader -> scrollToTop2()

            btnColumnSetting -> {
                if (showColumnSetting(!isColumnSettingShown)) {
                    hideAnnouncements()
                }
            }

            btnDeleteNotification -> Action_Notification.deleteAll(
                activity,
                column.access_info,
                false
            )

            btnColor ->
                activity.app_state.columnIndex(column)?.let {
                    ActColumnCustomize.open(activity, it, ActMain.REQUEST_CODE_COLUMN_COLOR)
                }

            btnLanguageFilter ->
                activity.app_state.columnIndex(column)?.let {
                    ActLanguageFilter.open(activity, it, ActMain.REQUEST_CODE_LANGUAGE_FILTER)
                }

            btnListAdd -> {
                val tv = etListName.text.toString().trim { it <= ' ' }
                if (tv.isEmpty()) {
                    activity.showToast(true, R.string.list_name_empty)
                    return
                }
                Action_List.create(activity, column.access_info, tv, null)
            }

            llRefreshError -> {
                column.mRefreshLoadingErrorPopupState = 1 - column.mRefreshLoadingErrorPopupState
                showRefreshError()
            }

            btnQuickFilterAll -> clickQuickFilter(Column.QUICK_FILTER_ALL)
            btnQuickFilterMention -> clickQuickFilter(Column.QUICK_FILTER_MENTION)
            btnQuickFilterFavourite -> clickQuickFilter(Column.QUICK_FILTER_FAVOURITE)
            btnQuickFilterBoost -> clickQuickFilter(Column.QUICK_FILTER_BOOST)
            btnQuickFilterFollow -> clickQuickFilter(Column.QUICK_FILTER_FOLLOW)
            btnQuickFilterPost -> clickQuickFilter(Column.QUICK_FILTER_POST)
            btnQuickFilterReaction -> clickQuickFilter(Column.QUICK_FILTER_REACTION)
            btnQuickFilterVote -> clickQuickFilter(Column.QUICK_FILTER_VOTE)

            btnAnnouncements -> toggleAnnouncements()

            btnAnnouncementsPrev -> {
                column.announcementId =
                    TootAnnouncement.move(column.announcements, column.announcementId, -1)
                activity.app_state.saveColumnList()
                showAnnouncements()
            }

            btnAnnouncementsNext -> {
                column.announcementId =
                    TootAnnouncement.move(column.announcements, column.announcementId, +1)
                activity.app_state.saveColumnList()
                showAnnouncements()
            }
        }
    }

    override fun onLongClick(v: View): Boolean {
        return when (v) {
            btnColumnClose ->
                activity.app_state.columnIndex(column)?.let {
                    activity.closeColumnAll(it)
                    true
                } ?: false

            else -> false
        }
    }

    private fun showError(message: String) {
        hideRefreshError()
        tvLoading.visibility = View.VISIBLE
        tvLoading.text = message

        refreshLayout.isRefreshing = false
        refreshLayout.visibility = View.GONE

    }

    private fun showColumnCloseButton() {
        val dont_close = column?.dont_close ?: return
        btnColumnClose.isEnabled = !dont_close
        btnColumnClose.alpha = if (dont_close) 0.3f else 1f
    }

    // 相対時刻を更新する
    fun updateRelativeTime() = rebindAdapterItems()

    fun rebindAdapterItems() {
        for (childIndex in 0 until listView.childCount) {
            val adapterIndex = listView.getChildAdapterPosition(listView.getChildAt(childIndex))
            if (adapterIndex == RecyclerView.NO_POSITION) continue
            status_adapter?.notifyItemChanged(adapterIndex)
        }
    }

    private val procShowColumnHeader: Runnable = Runnable {

        val column = this.column
        if (column == null || column.is_dispose.get()) return@Runnable

        val ac = AcctColor.load(column.access_info)

        tvColumnContext.text = ac.nickname
        tvColumnContext.setTextColor(
            ac.color_fg.notZero()
                ?: activity.getAttributeColor(R.attr.colorTimeSmall)
        )

        tvColumnContext.setBackgroundColor(ac.color_bg)
        tvColumnContext.setPaddingRelative(activity.acct_pad_lr, 0, activity.acct_pad_lr, 0)

        tvColumnName.text = column.getColumnName(false)

        showColumnCloseButton()

        showAnnouncements(force = false)
    }

    // カラムヘッダなど、負荷が低い部分の表示更新
    fun showColumnHeader() {
        activity.handler.removeCallbacks(procShowColumnHeader)
        activity.handler.postDelayed(procShowColumnHeader, 50L)

    }

    internal fun showContent(
        reason: String,
        changeList: List<AdapterChange>? = null,
        reset: Boolean = false
    ) {
        // クラッシュレポートにadapterとリストデータの状態不整合が多かったので、
        // とりあえずリストデータ変更の通知だけは最優先で行っておく
        try {
            status_adapter?.notifyChange(reason, changeList, reset)
        } catch (ex: Throwable) {
            log.trace(ex)
        }

        showColumnHeader()
        showColumnStatus()

        val column = this.column
        if (column == null || column.is_dispose.get()) {
            showError("column was disposed.")
            return
        }

        if (!column.bFirstInitialized) {
            showError("initializing")
            return
        }

        if (column.bInitialLoading) {
            var message: String? = column.task_progress
            if (message == null) message = "loading?"
            showError(message)
            return
        }

        val initialLoadingError = column.mInitialLoadingError
        if (initialLoadingError.isNotEmpty()) {
            showError(initialLoadingError)
            return
        }

        val status_adapter = this.status_adapter

        if (status_adapter == null || status_adapter.itemCount == 0) {
            showError(activity.getString(R.string.list_empty))
            return
        }

        tvLoading.visibility = View.GONE

        refreshLayout.visibility = View.VISIBLE

        status_adapter.findHeaderViewHolder(listView)?.bindData(column)

        if (column.bRefreshLoading) {
            hideRefreshError()
        } else {
            refreshLayout.isRefreshing = false
            showRefreshError()
        }
        proc_restoreScrollPosition.run()

    }

    private var bRefreshErrorWillShown = false

    private fun hideRefreshError() {
        if (!bRefreshErrorWillShown) return
        bRefreshErrorWillShown = false
        if (llRefreshError.visibility == View.GONE) return
        val aa = AlphaAnimation(1f, 0f)
        aa.duration = 666L
        aa.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationRepeat(animation: Animation?) {
            }

            override fun onAnimationStart(animation: Animation?) {
            }

            override fun onAnimationEnd(animation: Animation?) {
                if (!bRefreshErrorWillShown) llRefreshError.visibility = View.GONE
            }
        })
        llRefreshError.clearAnimation()
        llRefreshError.startAnimation(aa)
    }

    private fun showRefreshError() {
        val column = column
        if (column == null) {
            hideRefreshError()
            return
        }

        val refreshError = column.mRefreshLoadingError
        //		val refreshErrorTime = column.mRefreshLoadingErrorTime
        if (refreshError.isEmpty()) {
            hideRefreshError()
            return
        }

        tvRefreshError.text = refreshError
        when (column.mRefreshLoadingErrorPopupState) {
            // initially expanded
            0 -> {
                tvRefreshError.isSingleLine = false
                tvRefreshError.ellipsize = null
            }

            // tap to minimize
            1 -> {
                tvRefreshError.isSingleLine = true
                tvRefreshError.ellipsize = TextUtils.TruncateAt.END
            }
        }

        if (!bRefreshErrorWillShown) {
            bRefreshErrorWillShown = true
            if (llRefreshError.visibility == View.GONE) {
                llRefreshError.visibility = View.VISIBLE
                val aa = AlphaAnimation(0f, 1f)
                aa.duration = 666L
                llRefreshError.clearAnimation()
                llRefreshError.startAnimation(aa)
            }
        }
    }

    fun saveScrollPosition(): Boolean {
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
                    "saveScrollPosition [%d] %s , listView is not visible, save %s,%s",
                    page_idx,
                    column.getColumnName(true),
                    scroll_save.adapterIndex,
                    scroll_save.offset
                )
                return true
            }

            else -> {
                val scroll_save = ScrollPosition(this)
                column.scroll_save = scroll_save
                log.d(
                    "saveScrollPosition [%d] %s , listView is visible, save %s,%s",
                    page_idx,
                    column.getColumnName(true),
                    scroll_save.adapterIndex,
                    scroll_save.offset
                )
                return true
            }
        }
        return false
    }

    fun setScrollPosition(sp: ScrollPosition, deltaDp: Float = 0f) {
        val last_adapter = listView.adapter
        if (column == null || last_adapter == null) return

        sp.restore(this)

        // 復元した後に意図的に少し上下にずらしたい
        val dy = (deltaDp * activity.density + 0.5f).toInt()
        if (dy != 0) listView.postDelayed(Runnable {
            if (column == null || listView.adapter !== last_adapter) return@Runnable

            try {
                val recycler = fieldRecycler.get(listView) as RecyclerView.Recycler
                val state = fieldState.get(listView) as RecyclerView.State
                listLayoutManager.scrollVerticallyBy(dy, recycler, state)
            } catch (ex: Throwable) {
                log.trace(ex)
                log.e("can't access field in class %s", RecyclerView::class.java.simpleName)
            }
        }, 20L)
    }

    internal inner class AdapterItemHeightWorkarea internal constructor(val adapter: ItemListAdapter) :
        Closeable {

        private val item_width: Int
        private val widthSpec: Int
        private var lastViewType: Int = -1
        private var lastViewHolder: RecyclerView.ViewHolder? = null

        init {
            this.item_width = listView.width - listView.paddingLeft - listView.paddingRight
            this.widthSpec = View.MeasureSpec.makeMeasureSpec(item_width, View.MeasureSpec.EXACTLY)
        }

        override fun close() {
            val childViewHolder = lastViewHolder
            if (childViewHolder != null) {
                adapter.onViewRecycled(childViewHolder)
                lastViewHolder = null
            }
        }

        // この関数はAdapterViewの項目の(marginを含む)高さを返す
        fun getAdapterItemHeight(adapterIndex: Int): Int {

            fun View.getTotalHeight(): Int {
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
            if (childViewHolder == null || lastViewType != viewType) {
                if (childViewHolder != null) {
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
    fun setListItemTop(listIndex: Int, yArg: Int) {
        var adapterIndex = column?.toAdapterIndex(listIndex) ?: return

        val adapter = status_adapter
        if (adapter == null) {
            log.e("setListItemTop: missing status adapter")
            return
        }

        var y = yArg
        AdapterItemHeightWorkarea(adapter).use { workarea ->
            while (y > 0 && adapterIndex > 0) {
                --adapterIndex
                y -= workarea.getAdapterItemHeight(adapterIndex)
                y -= ListDivider.height
            }
        }

        if (adapterIndex == 0 && y > 0) y = 0
        listLayoutManager.scrollToPositionWithOffset(adapterIndex, y)
    }

    // この関数は scrollToPositionWithOffset 用のオフセットを返す
    fun getListItemOffset(listIndex: Int): Int {

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

    fun findFirstVisibleListItem(): Int {

        val adapterIndex = listLayoutManager.findFirstVisibleItemPosition()

        if (adapterIndex == RecyclerView.NO_POSITION)
            throw IndexOutOfBoundsException()

        return column?.toListIndex(adapterIndex)
            ?: throw IndexOutOfBoundsException()

    }

    fun scrollToTop() {
        try {
            listView.stopScroll()
        } catch (ex: Throwable) {
            log.e(ex, "stopScroll failed.")
        }
        try {
            listLayoutManager.scrollToPositionWithOffset(0, 0)
        } catch (ex: Throwable) {
            log.e(ex, "scrollToPositionWithOffset failed.")
        }
    }

    fun scrollToTop2() {
        val status_adapter = this.status_adapter
        if (binding_busy || status_adapter == null) return
        if (status_adapter.itemCount > 0) {
            scrollToTop()
        }
    }

    private fun clickQuickFilter(filter: Int) {
        column?.quick_filter = filter
        showQuickFilter()
        activity.app_state.saveColumnList()
        column?.startLoading()
    }

    private fun showQuickFilter() {
        val column = this.column ?: return

        svQuickFilter.vg(column.isNotificationColumn) ?: return

        btnQuickFilterReaction.vg(column.isMisskey)
        btnQuickFilterFavourite.vg(!column.isMisskey)

        val insideColumnSetting = Pref.bpMoveNotificationsQuickFilter(activity.pref)

        val showQuickFilterButton: (btn: View, iconId: Int, selected: Boolean) -> Unit

        if (insideColumnSetting) {
            svQuickFilter.setBackgroundColor(0)

            val colorFg = activity.getAttributeColor(R.attr.colorContentText)
            val colorBgSelected = colorFg.applyAlphaMultiplier(0.25f)
            val colorFgList = ColorStateList.valueOf(colorFg)
            val colorBg = activity.getAttributeColor(R.attr.colorColumnSettingBackground)
            showQuickFilterButton = { btn, iconId, selected ->
                btn.backgroundDrawable =
                    getAdaptiveRippleDrawableRound(
                        activity,
                        if (selected) colorBgSelected else colorBg,
                        colorFg,
                        roundNormal = true
                    )

                when (btn) {
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

                btn.backgroundDrawable = getAdaptiveRippleDrawableRound(
                    activity,
                    if (selected) colorBgSelected else colorBg,
                    colorFg
                )

                when (btn) {
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
            btnQuickFilterPost,
            R.drawable.ic_send,
            column.quick_filter == Column.QUICK_FILTER_POST
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

    private fun inflate(activity: ActMain, parent: ViewGroup) = with(activity.UI {}) {
        val b = Benchmark(log, "Item-Inflate", 40L)
        var label: TextView? = null
        val rv = verticalLayout {
            // トップレベルのViewGroupのlparamsはイニシャライザ内部に置くしかないみたい
            val lp = parent.generateLayoutParamsEx()
            if (lp != null) {
                lp.width = matchParent
                lp.height = matchParent
                if (lp is ViewGroup.MarginLayoutParams) {
                    lp.setMargins(0, 0, 0, 0)
                }
                layoutParams = lp
            }

            llColumnHeader = customView<OutsideDrawerLayout> {
                lparams(matchParent, wrapContent)

                orientation = LinearLayout.VERTICAL

                background = ContextCompat.getDrawable(context, R.drawable.bg_column_header)
                startPadding = dip(12)
                endPadding = dip(12)
                topPadding = dip(3)
                bottomPadding = dip(3)

                linearLayout {
                    lparams(matchParent, wrapContent)
                    gravity = Gravity.BOTTOM

                    tvColumnContext = textView {
                        gravity = Gravity.END
                        startPadding = dip(4)
                        endPadding = dip(4)
                        textColor = context.getAttributeColor(R.attr.colorColumnHeaderAcct)
                        textSize = 12f

                    }.lparams(0, wrapContent) {
                        weight = 1f
                    }

                    tvColumnStatus = textView {
                        gravity = Gravity.END
                        textColor = context.getAttributeColor(R.attr.colorColumnHeaderPageNumber)
                        textSize = 12f

                    }.lparams(wrapContent, wrapContent) {
                        marginStart = dip(12)
                    }

                    tvColumnIndex = textView {
                        gravity = Gravity.END
                        textColor = context.getAttributeColor(R.attr.colorColumnHeaderPageNumber)
                        textSize = 12f

                    }.lparams(wrapContent, wrapContent) {
                        marginStart = dip(4)
                    }
                }

                linearLayout {
                    lparams(matchParent, wrapContent) {
                        topMargin = dip(0)
                    }
                    gravity = Gravity.CENTER_VERTICAL
                    isBaselineAligned = false

                    ivColumnIcon = imageView {

                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        scaleType = ImageView.ScaleType.FIT_CENTER

                    }.lparams(dip(32), dip(32)) {
                        endMargin = dip(4)
                    }

                    tvColumnName = textView {
                        // Kannada語の  "ಸ್ಥಳೀಯ ಟೈಮ್ ಲೈನ್" の上下が途切れることがあるらしい
                        // GS10+では再現しなかった
                    }.lparams(dip(0), wrapContent) {
                        weight = 1f
                    }

                    frameLayout {
                        lparams(wrapContent, wrapContent) {
                            gravity = Gravity.CENTER_VERTICAL
                            startMargin = dip(2)
                        }
                        clipChildren = false

                        btnAnnouncements = imageButton {
                            background =
                                ContextCompat.getDrawable(
                                    context,
                                    R.drawable.btn_bg_transparent_round6dp
                                )
                            contentDescription = context.getString(R.string.announcements)
                            setImageResource(R.drawable.ic_info_outline)
                            padding = dip(8)
                            scaleType = ImageView.ScaleType.FIT_CENTER

                            btnAnnouncementsCutout = Paint().apply {
                                isAntiAlias = true
                            }
                            val path = Path()
                            addOutsideDrawer(this) { canvas, parent, view, left, top ->
                                if (llAnnouncementsBox.visibility == View.VISIBLE) {
                                    val viewW = view.width
                                    val viewH = view.height
                                    val triTopX = (left + viewW / 2).toFloat()
                                    val triTopY = top.toFloat() + viewH * 0.75f
                                    val triBottomLeft = left.toFloat()
                                    val triBottomRight = (left + viewW).toFloat()
                                    val triBottom = parent.height.toFloat()
                                    path.reset()
                                    path.moveTo(triTopX, triTopY)
                                    path.lineTo(triBottomRight, triBottom)
                                    path.lineTo(triBottomLeft, triBottom)
                                    path.lineTo(triTopX, triTopY)
                                    canvas.drawPath(path, btnAnnouncementsCutout)
                                }
                            }
                        }.lparams(dip(40), dip(40))

                        btnAnnouncementsBadge = imageView {
                            setImageResource(R.drawable.announcements_dot)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        }.lparams(dip(7), dip(7)) {
                            gravity = Gravity.END or Gravity.TOP
                            endMargin = dip(4)
                            topMargin = dip(4)
                        }

                    }

                    frameLayout {
                        lparams(wrapContent, wrapContent) {
                            gravity = Gravity.CENTER_VERTICAL
                            startMargin = dip(2)
                        }
                        clipChildren = false

                        btnColumnSetting = imageButton {
                            background =
                                ContextCompat.getDrawable(
                                    context,
                                    R.drawable.btn_bg_transparent_round6dp
                                )
                            contentDescription = context.getString(R.string.setting)
                            setImageResource(R.drawable.ic_tune)
                            padding = dip(8)
                            scaleType = ImageView.ScaleType.FIT_CENTER

                            val paint = Paint().apply {
                                isAntiAlias = true
                                color =
                                    context.getAttributeColor(R.attr.colorColumnSettingBackground)
                            }
                            val path = Path()
                            addOutsideDrawer(this) { canvas, parent, view, left, top ->
                                if (llColumnSetting.visibility == View.VISIBLE) {
                                    val viewW = view.width
                                    val viewH = view.height
                                    val triTopX = (left + viewW / 2).toFloat()
                                    val triTopY = top.toFloat() + viewH * 0.75f
                                    val triBottomLeft = left.toFloat()
                                    val triBottomRight = (left + viewW).toFloat()
                                    val triBottom = parent.height.toFloat()
                                    path.reset()
                                    path.moveTo(triTopX, triTopY)
                                    path.lineTo(triBottomRight, triBottom)
                                    path.lineTo(triBottomLeft, triBottom)
                                    path.lineTo(triTopX, triTopY)
                                    canvas.drawPath(path, paint)
                                }
                            }
                        }.lparams(dip(40), dip(40))

                    }

                    btnColumnReload = imageButton {
                        background =
                            ContextCompat.getDrawable(
                                context,
                                R.drawable.btn_bg_transparent_round6dp
                            )
                        contentDescription = context.getString(R.string.reload)
                        setImageResource(R.drawable.ic_refresh)
                        padding = dip(8)
                        scaleType = ImageView.ScaleType.FIT_CENTER

                    }.lparams(dip(40), dip(40)) {
                        gravity = Gravity.CENTER_VERTICAL
                        startMargin = dip(2)

                    }

                    btnColumnClose = imageButton {
                        background =
                            ContextCompat.getDrawable(
                                context,
                                R.drawable.btn_bg_transparent_round6dp
                            )
                        contentDescription = context.getString(R.string.close_column)
                        setImageResource(R.drawable.ic_close)
                        padding = dip(8)
                        scaleType = ImageView.ScaleType.FIT_CENTER

                    }.lparams(dip(40), dip(40)) {
                        gravity = Gravity.CENTER_VERTICAL
                        startMargin = dip(2)

                    }
                }
            } // end of column header

            llColumnSetting = maxHeightScrollView {
                lparams(matchParent, wrapContent)
                isScrollbarFadingEnabled = false
                maxHeight = dip(240)

                backgroundColor =
                    context.getAttributeColor(R.attr.colorColumnSettingBackground)

                llColumnSettingInside = verticalLayout {
                    lparams(matchParent, wrapContent)

                    startPadding = dip(12)
                    endPadding = dip(12)
                    topPadding = dip(3)
                    bottomPadding = dip(3)

                    llHashtagExtra = verticalLayout {
                        lparams(matchParent, wrapContent)

                        label = textView {
                            textColor =
                                context.getAttributeColor(R.attr.colorColumnHeaderPageNumber)
                            text = context.getString(R.string.hashtag_extra_any)
                        }.lparams(matchParent, wrapContent)

                        etHashtagExtraAny = editText {
                            id = View.generateViewId()
                            inputType = InputType.TYPE_CLASS_TEXT
                            maxLines = 1
                            setHorizontallyScrolling(true)
                            isHorizontalScrollBarEnabled = true
                        }.lparams(matchParent, wrapContent)
                        label?.labelFor = etHashtagExtraAny.id

                        label = textView {
                            textColor =
                                context.getAttributeColor(R.attr.colorColumnHeaderPageNumber)
                            text = context.getString(R.string.hashtag_extra_all)
                        }.lparams(matchParent, wrapContent)

                        etHashtagExtraAll = editText {
                            id = View.generateViewId()
                            inputType = InputType.TYPE_CLASS_TEXT
                            maxLines = 1
                            setHorizontallyScrolling(true)
                            isHorizontalScrollBarEnabled = true
                        }.lparams(matchParent, wrapContent)
                        label?.labelFor = etHashtagExtraAll.id

                        label = textView {
                            textColor =
                                context.getAttributeColor(R.attr.colorColumnHeaderPageNumber)
                            text = context.getString(R.string.hashtag_extra_none)
                        }.lparams(matchParent, wrapContent)

                        etHashtagExtraNone = editText {
                            id = View.generateViewId()
                            inputType = InputType.TYPE_CLASS_TEXT
                            maxLines = 1
                            setHorizontallyScrolling(true)
                            isHorizontalScrollBarEnabled = true
                        }.lparams(matchParent, wrapContent)
                        label?.labelFor = etHashtagExtraNone.id
                    } // end of hashtag extra

                    cbDontCloseColumn = checkBox {
                        text = context.getString(R.string.dont_close_column)
                    }.lparams(matchParent, wrapContent)

                    cbRemoteOnly = checkBox {
                        text = context.getString(R.string.remote_only)
                    }.lparams(matchParent, wrapContent)

                    cbWithAttachment = checkBox {
                        text = context.getString(R.string.with_attachment)
                    }.lparams(matchParent, wrapContent)

                    cbWithHighlight = checkBox {
                        text = context.getString(R.string.with_highlight)
                    }.lparams(matchParent, wrapContent)

                    cbDontShowBoost = checkBox {
                        text = context.getString(R.string.dont_show_boost)
                    }.lparams(matchParent, wrapContent)

                    cbDontShowFavourite = checkBox {
                        text = context.getString(R.string.dont_show_favourite)
                    }.lparams(matchParent, wrapContent)

                    cbDontShowFollow = checkBox {
                        text = context.getString(R.string.dont_show_follow)
                    }.lparams(matchParent, wrapContent)

                    cbDontShowReply = checkBox {
                        text = context.getString(R.string.dont_show_reply)
                    }.lparams(matchParent, wrapContent)

                    cbDontShowReaction = checkBox {
                        text = context.getString(R.string.dont_show_reaction)
                    }.lparams(matchParent, wrapContent)

                    cbDontShowVote = checkBox {
                        text = context.getString(R.string.dont_show_vote)
                    }.lparams(matchParent, wrapContent)

                    cbDontShowNormalToot = checkBox {
                        text = context.getString(R.string.dont_show_normal_toot)
                    }.lparams(matchParent, wrapContent)

                    cbDontShowNonPublicToot = checkBox {
                        text = context.getString(R.string.dont_show_non_public_toot)
                    }.lparams(matchParent, wrapContent)

                    cbInstanceLocal = checkBox {
                        text = context.getString(R.string.instance_local)
                    }.lparams(matchParent, wrapContent)

                    cbDontStreaming = checkBox {
                        text = context.getString(R.string.dont_use_streaming_api)
                    }.lparams(matchParent, wrapContent)

                    cbDontAutoRefresh = checkBox {
                        text = context.getString(R.string.dont_refresh_on_activity_resume)
                    }.lparams(matchParent, wrapContent)

                    cbHideMediaDefault = checkBox {
                        text = context.getString(R.string.hide_media_default)
                    }.lparams(matchParent, wrapContent)

                    cbSystemNotificationNotRelated = checkBox {
                        text = context.getString(R.string.system_notification_not_related)
                    }.lparams(matchParent, wrapContent)

                    cbEnableSpeech = checkBox {
                        text = context.getString(R.string.enable_speech)
                    }.lparams(matchParent, wrapContent)

                    cbOldApi = checkBox {
                        text = context.getString(R.string.use_old_api)
                    }.lparams(matchParent, wrapContent)

                    llRegexFilter = linearLayout {
                        lparams(matchParent, wrapContent)

                        label = textView {
                            textColor =
                                context.getAttributeColor(R.attr.colorColumnHeaderPageNumber)
                            text = context.getString(R.string.regex_filter)
                        }.lparams(wrapContent, wrapContent)

                        tvRegexFilterError = textView {
                            textColor = context.getAttributeColor(R.attr.colorRegexFilterError)
                        }.lparams(0, wrapContent) {
                            weight = 1f
                            startMargin = dip(4)
                        }

                    }
                    etRegexFilter = editText {
                        id = View.generateViewId()
                        inputType = InputType.TYPE_CLASS_TEXT
                        maxLines = 1
                        setHorizontallyScrolling(true)
                        isHorizontalScrollBarEnabled = true

                    }.lparams(matchParent, wrapContent)
                    label?.labelFor = etRegexFilter.id

                    btnDeleteNotification = button {
                        isAllCaps = false
                        text = context.getString(R.string.notification_delete)

                    }.lparams(matchParent, wrapContent)

                    btnColor = button {
                        isAllCaps = false
                        text = context.getString(R.string.color_and_background)
                    }.lparams(matchParent, wrapContent)

                    btnLanguageFilter = button {
                        isAllCaps = false
                        text = context.getString(R.string.language_filter)
                    }.lparams(matchParent, wrapContent)

                }

            } // end of column setting scroll view

            llAnnouncementsBox = verticalLayout {
                lparams(matchParent, wrapContent) {
                    startMargin = dip(6)
                    endMargin = dip(6)
                    bottomMargin = dip(2)
                }

                val buttonHeight = ActMain.boostButtonSize
                val paddingH = (buttonHeight.toFloat() * 0.1f + 0.5f).toInt()
                val paddingV = (buttonHeight.toFloat() * 0.1f + 0.5f).toInt()

                linearLayout {
                    lparams(matchParent, wrapContent)
                    val pad_lr = dip(6)
                    setPadding(pad_lr, 0, pad_lr, 0)

                    background = ContextCompat.getDrawable(
                        context,
                        R.drawable.btn_bg_transparent_round6dp
                    )

                    gravity = Gravity.CENTER_VERTICAL or Gravity.END

                    tvAnnouncementsCaption = textView {
                        gravity = Gravity.END or Gravity.CENTER_VERTICAL
                        text = context.getString(R.string.announcements)
                    }.lparams(0, wrapContent) {
                        weight = 1f
                    }

                    btnAnnouncementsPrev = imageButton {

                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        contentDescription = context.getString(R.string.previous)
                        imageResource = R.drawable.ic_arrow_start
                        setPadding(paddingH, paddingV, paddingH, paddingV)
                        scaleType = ImageView.ScaleType.FIT_CENTER

                    }.lparams(buttonHeight, buttonHeight) {
                        marginStart = dip(4)
                    }

                    tvAnnouncementsIndex = textView {
                    }.lparams(wrapContent, wrapContent) {
                        marginStart = dip(4)
                    }

                    btnAnnouncementsNext = imageButton {

                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        contentDescription = context.getString(R.string.next)
                        imageResource = R.drawable.ic_arrow_end
                        setPadding(paddingH, paddingV, paddingH, paddingV)
                        scaleType = ImageView.ScaleType.FIT_CENTER

                    }.lparams(buttonHeight, buttonHeight) {
                        marginStart = dip(4)
                    }
                }

                llAnnouncements = maxHeightScrollView {
                    lparams(matchParent, wrapContent) {
                        topMargin = dip(1)
                    }

                    val pad_lr = dip(6)
                    val pad_tb = dip(2)
                    setPadding(pad_lr, pad_tb, pad_lr, pad_tb)

                    scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
                    isScrollbarFadingEnabled = false

                    maxHeight = dip(240)

                    verticalLayout {
                        lparams(matchParent, wrapContent)

                        // 期間があれば表示する
                        tvAnnouncementPeriod = textView {
                            gravity = Gravity.END
                        }.lparams(matchParent, wrapContent) {
                            bottomMargin = dip(3)
                        }

                        tvAnnouncementContent = myTextView {
                            setLineSpacing(lineSpacingExtra, 1.1f)
                            // tools:text="Contents\nContents"
                        }.lparams(matchParent, wrapContent) {
                            topMargin = dip(3)
                        }

                        llAnnouncementExtra = verticalLayout {
                            lparams(matchParent, wrapContent) {
                                topMargin = dip(3)
                            }
                        }
                    }
                }
            }

            llSearch = verticalLayout {
                lparams(matchParent, wrapContent)

                linearLayout {
                    lparams(matchParent, wrapContent)
                    isBaselineAligned = false
                    gravity = Gravity.CENTER

                    etSearch = editText {
                        id = View.generateViewId()
                        imeOptions = EditorInfo.IME_ACTION_SEARCH
                        inputType = InputType.TYPE_CLASS_TEXT
                        maxLines = 1

                    }.lparams(0, wrapContent) {
                        weight = 1f
                    }

                    btnSearchClear = imageButton {
                        backgroundResource = R.drawable.btn_bg_transparent_round6dp
                        contentDescription = context.getString(R.string.clear)
                        imageResource = R.drawable.ic_close
                        imageTintList = ColorStateList.valueOf(
                            context.getAttributeColor(R.attr.colorVectorDrawable)
                        )
                    }.lparams(dip(40), dip(40)) {
                        startMargin = dip(4)
                    }

                    btnSearch = imageButton {
                        backgroundResource = R.drawable.btn_bg_transparent_round6dp
                        contentDescription = context.getString(R.string.search)
                        imageResource = R.drawable.ic_search
                        imageTintList = ColorStateList.valueOf(
                            context.getAttributeColor(R.attr.colorVectorDrawable)
                        )
                    }.lparams(dip(40), dip(40)) {
                        startMargin = dip(4)
                    }
                }

                cbResolve = checkBox {
                    text = context.getString(R.string.resolve_non_local_account)
                }.lparams(wrapContent, wrapContent) // チェックボックスの余白はタッチ判定外

            } // end of search bar

            llListList = linearLayout {
                lparams(matchParent, wrapContent)

                isBaselineAligned = false
                gravity = Gravity.CENTER

                etListName = editText {
                    hint = context.getString(R.string.list_create_hint)
                    imeOptions = EditorInfo.IME_ACTION_SEND
                    inputType = InputType.TYPE_CLASS_TEXT
                }.lparams(0, wrapContent) {
                    weight = 1f
                }

                btnListAdd = imageButton {
                    backgroundResource = R.drawable.btn_bg_transparent_round6dp
                    contentDescription = context.getString(R.string.add)
                    imageResource = R.drawable.ic_add
                    imageTintList = ColorStateList.valueOf(
                        context.getAttributeColor(
                            R.attr.colorVectorDrawable
                        )
                    )
                }.lparams(dip(40), dip(40)) {
                    startMargin = dip(4)
                }
            } // end of list list header

            svQuickFilter = horizontalScrollView {
                lparams(matchParent, wrapContent)
                isFillViewport = true
                linearLayout {
                    lparams(matchParent, dip(40))

                    btnQuickFilterAll = button {
                        backgroundResource = R.drawable.btn_bg_transparent_round6dp
                        minWidthCompat = dip(40)
                        startPadding = dip(4)
                        endPadding = dip(4)
                        isAllCaps = false
                        stateListAnimator = null
                        text = context.getString(R.string.all)
                    }.lparams(wrapContent, matchParent) {
                        margin = 0
                    }

                    btnQuickFilterMention = imageButton {
                        backgroundResource = R.drawable.btn_bg_transparent_round6dp
                        contentDescription = context.getString(R.string.mention2)
                    }.lparams(dip(40), matchParent) {
                        margin = 0
                    }

                    btnQuickFilterFavourite = imageButton {
                        backgroundResource = R.drawable.btn_bg_transparent_round6dp
                        contentDescription = context.getString(R.string.favourite)
                    }.lparams(dip(40), matchParent) {
                        margin = 0
                    }

                    btnQuickFilterBoost = imageButton {
                        backgroundResource = R.drawable.btn_bg_transparent_round6dp
                        contentDescription = context.getString(R.string.boost)
                    }.lparams(dip(40), matchParent) {
                        margin = 0
                    }

                    btnQuickFilterFollow = imageButton {
                        backgroundResource = R.drawable.btn_bg_transparent_round6dp
                        contentDescription = context.getString(R.string.follow)
                    }.lparams(dip(40), matchParent) {
                        margin = 0
                    }

                    btnQuickFilterPost = imageButton {
                        backgroundResource = R.drawable.btn_bg_transparent_round6dp
                        contentDescription = context.getString(R.string.notification_type_post)
                    }.lparams(dip(40), matchParent) {
                        margin = 0
                    }

                    btnQuickFilterReaction = imageButton {
                        backgroundResource = R.drawable.btn_bg_transparent_round6dp
                        contentDescription = context.getString(R.string.reaction)
                    }.lparams(dip(40), matchParent) {
                        margin = 0
                    }


                    btnQuickFilterVote = imageButton {
                        backgroundResource = R.drawable.btn_bg_transparent_round6dp
                        contentDescription = context.getString(R.string.vote_polls)
                    }.lparams(dip(40), matchParent) {
                        margin = 0
                    }
                }
            } // end of notification quick filter bar

            flColumnBackground = frameLayout {

                ivColumnBackgroundImage = imageView {

                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    visibility = View.GONE

                }.lparams(matchParent, matchParent)

                tvLoading = textView {
                    gravity = Gravity.CENTER
                }.lparams(matchParent, matchParent)

                refreshLayout = swipyRefreshLayout {
                    lparams(matchParent, matchParent)

                    direction = SwipyRefreshLayoutDirection.BOTH

                    // スタイルで指定しないとAndroid 6 で落ちる…
                    listView = recyclerView {
                        listLayoutManager = LinearLayoutManager(activity)
                        layoutManager = listLayoutManager

                    }.lparams(matchParent, matchParent) {

                    }
                }

                llRefreshError = frameLayout {

                    foregroundGravity = Gravity.BOTTOM
                    backgroundResource = R.drawable.bg_refresh_error

                    startPadding = dip(6)
                    endPadding = dip(6)
                    topPadding = dip(3)
                    bottomPadding = dip(3)

                    ivRefreshError = imageView {

                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        imageResource = R.drawable.ic_error
                        imageTintList = ColorStateList.valueOf(Color.RED)

                    }.lparams(dip(24), dip(24)) {
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        startMargin = dip(4)
                    }

                    tvRefreshError = textView {
                        textColor = Color.WHITE

                    }.lparams(matchParent, wrapContent) {
                        gravity = Gravity.TOP or Gravity.START
                        startMargin = dip(32)
                    }
                }.lparams(matchParent, wrapContent) {
                    margin = dip(6)
                }
            }.lparams(matchParent, 0) {
                weight = 1f
            }
        }
        b.report()
        rv
    }

    private fun hideAnnouncements() {
        val column = column ?: return

        if (column.announcementHideTime <= 0L)
            column.announcementHideTime = System.currentTimeMillis()
        activity.app_state.saveColumnList()
        showAnnouncements()
    }

    private fun toggleAnnouncements() {
        val column = column ?: return

        if (llAnnouncementsBox.visibility == View.VISIBLE) {
            if (column.announcementHideTime <= 0L)
                column.announcementHideTime = System.currentTimeMillis()
        } else {
            showColumnSetting(false)
            column.announcementHideTime = 0L
        }
        activity.app_state.saveColumnList()
        showAnnouncements()
    }

    private fun showAnnouncements(force: Boolean = true) {
        val column = column ?: return

        if (!force && lastAnnouncementShown >= column.announcementUpdated) {
            return
        }
        lastAnnouncementShown = SystemClock.elapsedRealtime()

        fun clearExtras() {
            for (invalidator in extra_invalidator_list) {
                invalidator.register(null)
            }
            extra_invalidator_list.clear()
        }
        llAnnouncementExtra.removeAllViews()
        clearExtras()

        val listShown = TootAnnouncement.filterShown(column.announcements)
        if (listShown?.isEmpty() != false) {
            btnAnnouncements.vg(false)
            llAnnouncementsBox.vg(false)
            btnAnnouncementsBadge.vg(false)
            llColumnHeader.invalidate()
            return
        }

        btnAnnouncements.vg(true)

        val expand = column.announcementHideTime <= 0L

        llAnnouncementsBox.vg(expand)
        llColumnHeader.invalidate()

        btnAnnouncementsBadge.vg(false)
        if (!expand) {
            val newer = listShown.find { it.updated_at > column.announcementHideTime }
            if (newer != null) {
                column.announcementId = newer.id
                btnAnnouncementsBadge.vg(true)
            }
            return
        }

        val content_color = column.getContentColor()

        val item = listShown.find { it.id == column.announcementId }
            ?: listShown[0]

        val itemIndex = listShown.indexOf(item)

        val enablePaging = listShown.size > 1

        val alphaPrevNext = if (enablePaging) 1f else 0.3f

        setIconDrawableId(
            activity,
            btnAnnouncementsPrev,
            R.drawable.ic_arrow_start,
            color = content_color,
            alphaMultiplier = alphaPrevNext
        )

        setIconDrawableId(
            activity,
            btnAnnouncementsNext,
            R.drawable.ic_arrow_end,
            color = content_color,
            alphaMultiplier = alphaPrevNext
        )


        btnAnnouncementsPrev.vg(expand)?.run {
            isEnabled = enablePaging
        }
        btnAnnouncementsNext.vg(expand)?.run {
            isEnabled = enablePaging
        }

        tvAnnouncementsCaption.textColor = content_color
        tvAnnouncementsIndex.textColor = content_color
        tvAnnouncementPeriod.textColor = content_color

        val f = activity.timeline_font_size_sp
        if (!f.isNaN()) {
            tvAnnouncementsCaption.textSize = f
            tvAnnouncementsIndex.textSize = f
            tvAnnouncementPeriod.textSize = f
            tvAnnouncementContent.textSize = f
        }
        val spacing = activity.timeline_spacing
        if (spacing != null) {
            tvAnnouncementPeriod.setLineSpacing(0f, spacing)
            tvAnnouncementContent.setLineSpacing(0f, spacing)
        }
        tvAnnouncementsCaption.typeface = ActMain.timeline_font_bold
        val font_normal = ActMain.timeline_font
        tvAnnouncementsIndex.typeface = font_normal
        tvAnnouncementPeriod.typeface = font_normal
        tvAnnouncementContent.typeface = font_normal

        tvAnnouncementsIndex.vg(expand)?.text =
            activity.getString(R.string.announcements_index, itemIndex + 1, listShown.size)
        llAnnouncements.vg(expand)

        var periods: StringBuilder? = null
        fun String.appendPeriod() {
            val sb = periods
            if (sb == null) {
                periods = StringBuilder(this)
            } else {
                sb.append("\n")
                sb.append(this)
            }
        }

        val (strStart, strEnd) = TootStatus.formatTimeRange(
            item.starts_at,
            item.ends_at,
            item.all_day
        )

        when {

            // no periods.
            strStart == "" && strEnd == "" -> {
            }

            // single date
            strStart == strEnd -> {
                activity.getString(R.string.announcements_period1, strStart)
                    .appendPeriod()
            }

            else -> {
                activity.getString(R.string.announcements_period2, strStart, strEnd)
                    .appendPeriod()
            }
        }

        if (item.updated_at > item.published_at) {
            val strUpdateAt = TootStatus.formatTime(activity, item.updated_at, false)
            activity.getString(R.string.edited_at, strUpdateAt).appendPeriod()
        }

        val sb = periods
        tvAnnouncementPeriod.vg(sb != null)?.text = sb

        tvAnnouncementContent.textColor = content_color
        tvAnnouncementContent.text = item.decoded_content
        tvAnnouncementContent.tag = this@ColumnViewHolder
        announcementContentInvalidator.register(item.decoded_content)

        // リアクションの表示

        val density = activity.density

        val buttonHeight = ActMain.boostButtonSize
        val marginBetween = (buttonHeight.toFloat() * 0.2f + 0.5f).toInt()
        val marginBottom = (buttonHeight.toFloat() * 0.2f + 0.5f).toInt()

        val paddingH = (buttonHeight.toFloat() * 0.1f + 0.5f).toInt()
        val paddingV = (buttonHeight.toFloat() * 0.1f + 0.5f).toInt()

        val box = FlexboxLayout(activity).apply {
            flexWrap = FlexWrap.WRAP
            justifyContent = JustifyContent.FLEX_START
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (0.5f + density * 3f).toInt()
            }
        }

        // +ボタン
        run {
            val b = ImageButton(activity)
            val blp = FlexboxLayout.LayoutParams(
                buttonHeight,
                buttonHeight
            ).apply {
                bottomMargin = marginBottom
                endMargin = marginBetween
            }
            b.layoutParams = blp
            b.background = ContextCompat.getDrawable(
                activity,
                R.drawable.btn_bg_transparent_round6dp
            )

            b.contentDescription = activity.getString(R.string.reaction_add)
            b.scaleType = ImageView.ScaleType.FIT_CENTER
            b.padding = paddingV
            b.setOnClickListener {
                addReaction(item, null)
            }

            setIconDrawableId(
                activity,
                b,
                R.drawable.ic_add,
                color = content_color,
                alphaMultiplier = 1f
            )

            box.addView(b)
        }
        val reactions = item.reactions?.filter { it.count > 0L }?.notEmpty()
        if (reactions != null) {

            var lastButton: View? = null

            val options = DecodeOptions(
                activity,
                column.access_info,
                decodeEmoji = true,
                enlargeEmoji = 1.5f,
                mentionDefaultHostDomain = column.access_info
            )

            val actMain = activity
            val disableEmojiAnimation = Pref.bpDisableEmojiAnimation(actMain.pref)

            for (reaction in reactions) {

                val url = if (disableEmojiAnimation) {
                    reaction.static_url.notEmpty() ?: reaction.url.notEmpty()
                } else {
                    reaction.url.notEmpty() ?: reaction.static_url.notEmpty()
                }

                val b = Button(activity).also { btn ->
                    btn.layoutParams = FlexboxLayout.LayoutParams(
                        FlexboxLayout.LayoutParams.WRAP_CONTENT,
                        buttonHeight
                    ).apply {
                        endMargin = marginBetween
                        bottomMargin = marginBottom
                    }
                    btn.minWidthCompat = buttonHeight

                    btn.allCaps = false
                    btn.tag = reaction

                    btn.background = if (reaction.me == true) {
                        getAdaptiveRippleDrawableRound(
                            actMain,
                            actMain.getAttributeColor(R.attr.colorButtonBgCw),
                            actMain.getAttributeColor(R.attr.colorRippleEffect)
                        )
                    } else {
                        ContextCompat.getDrawable(actMain, R.drawable.btn_bg_transparent_round6dp)
                    }

                    btn.setTextColor(content_color)

                    btn.setPadding(paddingH, paddingV, paddingH, paddingV)


                    btn.text = if (url == null) {
                        EmojiDecoder.decodeEmoji(options, "${reaction.name} ${reaction.count}")
                    } else {
                        SpannableStringBuilder("${reaction.name} ${reaction.count}").also { sb ->
                            sb.setSpan(
                                NetworkEmojiSpan(url, scale = 1.5f),
                                0,
                                reaction.name.length,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            val invalidator =
                                NetworkEmojiInvalidator(actMain.handler, btn)
                            invalidator.register(sb)
                            extra_invalidator_list.add(invalidator)
                        }
                    }

                    btn.setOnClickListener {
                        if (reaction.me == true) {
                            removeReaction(item, reaction.name)
                        } else {
                            addReaction(item, TootAnnouncement.Reaction(jsonObject {
                                put("name", reaction.name)
                                put("count", 1)
                                put("me", true)
                                putNotNull("url", reaction.url)
                                putNotNull("static_url", reaction.static_url)
                            }))
                        }
                    }
                }
                box.addView(b)
                lastButton = b

            }

            lastButton
                ?.layoutParams
                ?.cast<ViewGroup.MarginLayoutParams>()
                ?.endMargin = 0
        }

        llAnnouncementExtra.addView(box)
    }

    private fun addReaction(item: TootAnnouncement, sample: TootAnnouncement.Reaction?) {
        val column = column ?: return
        if (sample == null) {
            EmojiPicker(activity, column.access_info,closeOnSelected = true) { name, _, _, unicode, customEmoji ->
                log.d("addReaction: $name")
                addReaction(item, TootAnnouncement.Reaction(jsonObject {
                    put("name", unicode ?: name)
                    put("count", 1)
                    put("me", true)
                    // 以下はカスタム絵文字のみ
                    if (customEmoji != null) {
                        putNotNull("url", customEmoji.url)
                        putNotNull("static_url", customEmoji.static_url)
                    }
                }))
            }.show()
            return
        }
        TootTaskRunner(activity).run(column.access_info, object : TootTask {
            override suspend fun background(client: TootApiClient): TootApiResult? {
                return client.request(
                    "/api/v1/announcements/${item.id}/reactions/${sample.name.encodePercent()}",
                    JsonObject().toPutRequestBuilder()
                )
                // 200 {}
            }

            override suspend fun handleResult(result: TootApiResult?) {
                result ?: return
                if (result.jsonObject == null) {
                    activity.showToast(true, result.error)
                } else {
                    sample.count = 0
                    val list = item.reactions
                    if (list == null) {
                        item.reactions = mutableListOf(sample)
                    } else {
                        val reaction = list.find { it.name == sample.name }
                        if (reaction == null) {
                            list.add(sample)
                        } else {
                            reaction.me = true
                            ++reaction.count
                        }
                    }
                    column.announcementUpdated = SystemClock.elapsedRealtime()
                    showAnnouncements()
                }
            }
        })
    }

    private fun removeReaction(item: TootAnnouncement, name: String) {
        val column = column ?: return
        TootTaskRunner(activity).run(column.access_info, object : TootTask {
            override suspend fun background(client: TootApiClient): TootApiResult? {
                return client.request(
                    "/api/v1/announcements/${item.id}/reactions/${name.encodePercent()}",
                    JsonObject().toDeleteRequestBuilder()
                )
                // 200 {}
            }

            override suspend fun handleResult(result: TootApiResult?) {
                result ?: return
                if (result.jsonObject == null) {
                    activity.showToast(true, result.error)
                } else {
                    val it = item.reactions?.iterator() ?: return
                    while (it.hasNext()) {
                        val reaction = it.next()
                        if (reaction.name == name) {
                            reaction.me = false
                            if (--reaction.count <= 0) it.remove()
                            break
                        }
                    }
                    column.announcementUpdated = SystemClock.elapsedRealtime()
                    showAnnouncements()
                }
            }
        })
    }
}
