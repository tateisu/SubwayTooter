package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.text.InputType
import android.text.SpannableStringBuilder
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.omadahealth.github.swipyrefreshlayout.library.SwipyRefreshLayout
import com.omadahealth.github.swipyrefreshlayout.library.SwipyRefreshLayoutDirection
import jp.juggler.subwaytooter.streaming.*
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.util.*
import jp.juggler.subwaytooter.view.MyLinkMovementMethod
import jp.juggler.subwaytooter.view.MyTextView
import jp.juggler.subwaytooter.view.OutsideDrawerLayout
import jp.juggler.util.*
import kotlinx.coroutines.*
import org.jetbrains.anko.*
import org.jetbrains.anko.custom.customView
import java.lang.Runnable
import java.lang.reflect.Field

@SuppressLint("ClickableViewAccessibility")
class ColumnViewHolder(
    val activity: ActMain,
    parent: ViewGroup
) : View.OnClickListener,
    SwipyRefreshLayout.OnRefreshListener,
    CompoundButton.OnCheckedChangeListener, View.OnLongClickListener {

    companion object {

        val log = LogCategory("ColumnViewHolder")

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
    var status_adapter: ItemListAdapter? = null
    var page_idx: Int = 0

    lateinit var llLoading: View
    lateinit var btnConfirmMail: Button
    lateinit var tvLoading: TextView

    lateinit var listView: RecyclerView
    lateinit var refreshLayout: SwipyRefreshLayout

    lateinit var listLayoutManager: LinearLayoutManager

    lateinit var llColumnHeader: View
    lateinit var tvColumnIndex: TextView
    lateinit var tvColumnStatus: TextView
    lateinit var tvColumnContext: TextView
    lateinit var ivColumnIcon: ImageView
    lateinit var tvColumnName: TextView

    lateinit var llColumnSetting: View
    private lateinit var llColumnSettingInside: LinearLayout

    lateinit var btnSearch: ImageButton
    lateinit var btnSearchClear: ImageButton
    lateinit var etSearch: EditText
    lateinit var cbResolve: CheckBox
    lateinit var etRegexFilter: EditText
    lateinit var tvRegexFilterError: TextView

    lateinit var btnAnnouncementsBadge: ImageView
    lateinit var btnAnnouncements: ImageButton
    lateinit var btnAnnouncementsCutout: Paint
    lateinit var btnColumnSetting: ImageButton
    lateinit var btnColumnReload: ImageButton
    lateinit var btnColumnClose: ImageButton

    lateinit var flColumnBackground: View
    lateinit var ivColumnBackgroundImage: ImageView
    lateinit var llSearch: View
    lateinit var cbDontCloseColumn: CheckBox
    lateinit var cbRemoteOnly: CheckBox
    lateinit var cbWithAttachment: CheckBox
    lateinit var cbWithHighlight: CheckBox
    lateinit var cbDontShowBoost: CheckBox
    lateinit var cbDontShowFollow: CheckBox
    lateinit var cbDontShowFavourite: CheckBox
    lateinit var cbDontShowReply: CheckBox
    lateinit var cbDontShowReaction: CheckBox
    lateinit var cbDontShowVote: CheckBox
    lateinit var cbDontShowNormalToot: CheckBox
    lateinit var cbDontShowNonPublicToot: CheckBox
    lateinit var cbInstanceLocal: CheckBox
    lateinit var cbDontStreaming: CheckBox
    lateinit var cbDontAutoRefresh: CheckBox
    lateinit var cbHideMediaDefault: CheckBox
    lateinit var cbSystemNotificationNotRelated: CheckBox
    lateinit var cbEnableSpeech: CheckBox
    lateinit var cbOldApi: CheckBox
    lateinit var llRegexFilter: View
    lateinit var btnDeleteNotification: Button
    lateinit var btnColor: Button
    lateinit var btnLanguageFilter: Button

    lateinit var svQuickFilter: HorizontalScrollView
    lateinit var btnQuickFilterAll: Button
    lateinit var btnQuickFilterMention: ImageButton
    lateinit var btnQuickFilterFavourite: ImageButton
    lateinit var btnQuickFilterBoost: ImageButton
    lateinit var btnQuickFilterFollow: ImageButton
    lateinit var btnQuickFilterPost: ImageButton

    lateinit var btnQuickFilterReaction: ImageButton
    lateinit var btnQuickFilterVote: ImageButton

    lateinit var llRefreshError: FrameLayout
    lateinit var ivRefreshError: ImageView
    lateinit var tvRefreshError: TextView

    lateinit var llListList: View
    lateinit var etListName: EditText
    lateinit var btnListAdd: View

    lateinit var llHashtagExtra: LinearLayout
    lateinit var etHashtagExtraAny: EditText
    lateinit var etHashtagExtraAll: EditText
    lateinit var etHashtagExtraNone: EditText

    lateinit var llAnnouncementsBox: View
    lateinit var tvAnnouncementsCaption: TextView
    lateinit var tvAnnouncementsIndex: TextView
    lateinit var btnAnnouncementsPrev: ImageButton
    lateinit var btnAnnouncementsNext: ImageButton
    lateinit var llAnnouncements: View
    lateinit var tvAnnouncementPeriod: TextView
    lateinit var tvAnnouncementContent: MyTextView
    lateinit var llAnnouncementExtra: LinearLayout

    var lastAnnouncementShown = 0L

    var binding_busy: Boolean = false

    var last_image_uri: String? = null
    var last_image_bitmap: Bitmap? = null
    var last_image_task: Job? = null

    var bRefreshErrorWillShown = false

    val extra_invalidator_list = ArrayList<NetworkEmojiInvalidator>()

    val announcementContentInvalidator: NetworkEmojiInvalidator

    val viewRoot: View = inflate(activity, parent)

    /////////////////////////////////

    val scrollPosition: ScrollPosition
        get() = ScrollPosition(this)

    val isColumnSettingShown: Boolean
        get() = llColumnSetting.visibility == View.VISIBLE

    //	val headerView : HeaderViewHolderBase?
    //		get() = status_adapter?.header

    /////////////////////////////////

    val isPageDestroyed: Boolean
        get() = column == null || activity.isFinishing

    /////////////////////////////////

    private val procStartLoading: Runnable = Runnable {
        if (binding_busy || isPageDestroyed) return@Runnable
        column?.startLoading()
    }

    val procShowColumnHeader: Runnable = Runnable {

        val column = this.column
        if (column == null || column.is_dispose.get()) return@Runnable

        val ac = AcctColor.load(column.access_info)

        tvColumnContext.text = ac.nickname
        tvColumnContext.setTextColor(
            ac.color_fg.notZero()
                ?: activity.attrColor(R.attr.colorTimeSmall)
        )

        tvColumnContext.setBackgroundColor(ac.color_bg)
        tvColumnContext.setPaddingRelative(activity.acct_pad_lr, 0, activity.acct_pad_lr, 0)

        tvColumnName.text = column.getColumnName(false)

        showColumnCloseButton()

        showAnnouncements(force = false)
    }

    val proc_restoreScrollPosition = object : Runnable {
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

    val procShowColumnStatus: Runnable = Runnable {

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
            val streamStatus = column.getStreamingStatus()
            log.d(
                "procShowColumnStatus: streamStatus=${streamStatus}, column=${column.access_info.acct}/${
                    column.getColumnName(
                        true
                    )
                }"
            )

            when (streamStatus) {
                StreamStatus.Missing, StreamStatus.Closed -> {
                }
                StreamStatus.Connecting, StreamStatus.Open -> {
                    sb.appendColorShadeIcon(activity, R.drawable.ic_pulse, "Streaming")
                    sb.append("?")
                }
                StreamStatus.Subscribed -> {
                    sb.appendColorShadeIcon(activity, R.drawable.ic_pulse, "Streaming")
                }
            }

        } finally {
            log.d("showColumnStatus ${sb}")
            tvColumnStatus.text = sb
        }
    }

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
        btnConfirmMail.setOnClickListener(this)

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
            activity.handler.removeCallbacks(procStartLoading)
            activity.handler.postDelayed(procStartLoading, 666L)
        })

        etHashtagExtraAny.addTextChangedListener(CustomTextWatcher {
            if (binding_busy || isPageDestroyed) return@CustomTextWatcher
            column?.hashtag_any = etHashtagExtraAny.text.toString()
            activity.app_state.saveColumnList()
            activity.handler.removeCallbacks(procStartLoading)
            activity.handler.postDelayed(procStartLoading, 666L)
        })

        etHashtagExtraAll.addTextChangedListener(CustomTextWatcher {
            if (binding_busy || isPageDestroyed) return@CustomTextWatcher
            column?.hashtag_all = etHashtagExtraAll.text.toString()
            activity.app_state.saveColumnList()
            activity.handler.removeCallbacks(procStartLoading)
            activity.handler.postDelayed(procStartLoading, 666L)
        })

        etHashtagExtraNone.addTextChangedListener(CustomTextWatcher {
            if (binding_busy || isPageDestroyed) return@CustomTextWatcher
            column?.hashtag_none = etHashtagExtraNone.text.toString()
            activity.app_state.saveColumnList()
            activity.handler.removeCallbacks(procStartLoading)
            activity.handler.postDelayed(procStartLoading, 666L)
        })

        announcementContentInvalidator =
            NetworkEmojiInvalidator(activity.handler, tvAnnouncementContent)
        tvAnnouncementContent.movementMethod = MyLinkMovementMethod
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


    override fun onClick(v: View?) = onClickImpl(v)
    override fun onLongClick(v: View?): Boolean = onLongClickImpl(v)
    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) =
        onCheckedChangedImpl(buttonView, isChecked)

    fun inflate(activity: ActMain, parent: ViewGroup) = with(activity.UI {}) {
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
                        textColor = context.attrColor(R.attr.colorColumnHeaderAcct)
                        textSize = 12f

                    }.lparams(0, wrapContent) {
                        weight = 1f
                    }

                    tvColumnStatus = textView {
                        gravity = Gravity.END
                        textColor = context.attrColor(R.attr.colorColumnHeaderPageNumber)
                        textSize = 12f

                    }.lparams(wrapContent, wrapContent) {
                        marginStart = dip(12)
                    }

                    tvColumnIndex = textView {
                        gravity = Gravity.END
                        textColor = context.attrColor(R.attr.colorColumnHeaderPageNumber)
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
                                    context.attrColor(R.attr.colorColumnSettingBackground)
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
                    context.attrColor(R.attr.colorColumnSettingBackground)

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
                                context.attrColor(R.attr.colorColumnHeaderPageNumber)
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
                                context.attrColor(R.attr.colorColumnHeaderPageNumber)
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
                                context.attrColor(R.attr.colorColumnHeaderPageNumber)
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
                                context.attrColor(R.attr.colorColumnHeaderPageNumber)
                            text = context.getString(R.string.regex_filter)
                        }.lparams(wrapContent, wrapContent)

                        tvRegexFilterError = textView {
                            textColor = context.attrColor(R.attr.colorRegexFilterError)
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
                            context.attrColor(R.attr.colorVectorDrawable)
                        )
                    }.lparams(dip(40), dip(40)) {
                        startMargin = dip(4)
                    }

                    btnSearch = imageButton {
                        backgroundResource = R.drawable.btn_bg_transparent_round6dp
                        contentDescription = context.getString(R.string.search)
                        imageResource = R.drawable.ic_search
                        imageTintList = ColorStateList.valueOf(
                            context.attrColor(R.attr.colorVectorDrawable)
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
                        context.attrColor(
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

                llLoading = verticalLayout {
                    lparams(matchParent, matchParent)

                    isBaselineAligned = false

                    gravity = Gravity.CENTER

                    tvLoading = textView {
                        gravity = Gravity.CENTER
                    }.lparams(matchParent, wrapContent)

                    btnConfirmMail = button {
                        text = activity.getString(R.string.resend_confirm_mail)
                        background = ContextCompat.getDrawable(
                            activity,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                    }.lparams(matchParent, wrapContent) {
                        topMargin = dip(8)
                    }
                }

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

}
