package jp.juggler.subwaytooter

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.util.JsonWriter
import android.view.KeyEvent
import android.view.View
import android.view.View.FOCUS_FORWARD
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.widget.*
import android.widget.TextView.OnEditorActionListener
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jrummyapps.android.colorpicker.ColorPickerDialog
import com.jrummyapps.android.colorpicker.ColorPickerDialogListener
import jp.juggler.subwaytooter.appsetting.AppDataExporter
import jp.juggler.subwaytooter.appsetting.AppSettingItem
import jp.juggler.subwaytooter.appsetting.SettingType
import jp.juggler.subwaytooter.appsetting.appSettingRoot
import jp.juggler.subwaytooter.databinding.ActAppSettingBinding
import jp.juggler.subwaytooter.databinding.LvSettingItemBinding
import jp.juggler.subwaytooter.dialog.DlgAppPicker
import jp.juggler.subwaytooter.notification.restartAllWorker
import jp.juggler.subwaytooter.pref.FILE_PROVIDER_AUTHORITY
import jp.juggler.subwaytooter.pref.impl.BooleanPref
import jp.juggler.subwaytooter.pref.impl.FloatPref
import jp.juggler.subwaytooter.pref.impl.IntPref
import jp.juggler.subwaytooter.pref.impl.StringPref
import jp.juggler.subwaytooter.pref.lazyPref
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.daoAcctColor
import jp.juggler.subwaytooter.table.daoLogData
import jp.juggler.subwaytooter.util.CustomShare
import jp.juggler.subwaytooter.util.CustomShareTarget
import jp.juggler.subwaytooter.util.cn
import jp.juggler.subwaytooter.view.MyTextView
import jp.juggler.util.*
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.coroutine.launchProgress
import jp.juggler.util.data.*
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.ui.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStreamWriter
import java.text.NumberFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs

class ActAppSetting : AppCompatActivity(), ColorPickerDialogListener, View.OnClickListener {

    companion object {

        internal val log = LogCategory("ActAppSetting")

        fun createIntent(activity: ActMain) =
            Intent(activity, ActAppSetting::class.java)

        private const val COLOR_DIALOG_ID = 1

        private const val STATE_CHOOSE_INTENT_TARGET = "customShareTarget"

        // 他の設定子画面と重複しない値にすること
//		const val REQUEST_CODE_OTHER = 0
//		const val REQUEST_CODE_APP_DATA_IMPORT = 1
//		const val REQUEST_CODE_TIMELINE_FONT = 2

        val reLinefeed = Regex("[\\x0d\\x0a]+")
    }

    private var customShareTarget: CustomShareTarget? = null

    lateinit var handler: Handler

    val views by lazy {
        ActAppSettingBinding.inflate(layoutInflater)
    }

    private val adapter by lazy {
        MyAdapter()
    }

    private val arNoop = ActivityResultHandler(log) { }

    private val arImportAppData = ActivityResultHandler(log) { r ->
        if (r.isNotOk) return@ActivityResultHandler
        r.data?.handleGetContentResult(contentResolver)
            ?.firstOrNull()
            ?.uri?.let { importAppData2(false, it) }
    }

    val arTimelineFont = ActivityResultHandler(log) { r ->
        if (r.isNotOk) return@ActivityResultHandler
        r.data?.let { handleFontResult(AppSettingItem.TIMELINE_FONT, it, "TimelineFont") }
    }

    val arTimelineFontBold = ActivityResultHandler(log) { r ->
        if (r.isNotOk) return@ActivityResultHandler
        r.data?.let {
            handleFontResult(
                AppSettingItem.TIMELINE_FONT_BOLD,
                it,
                "TimelineFontBold"
            )
        }
    }

    private var pendingQuery: String? = null

    private val procQuery: Runnable = Runnable {
        if (pendingQuery != null) load(null, pendingQuery)
    }

    private val divider = Any()

    private var lastSection: AppSettingItem? = null
    private var lastQuery: String? = null
    private var colorTarget: AppSettingItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        backPressed {
            when {
                lastQuery != null -> load(lastSection, null)
                lastSection != null -> load(null, null)
                else -> finish()
            }
        }

        arNoop.register(this)
        arImportAppData.register(this)
        arTimelineFont.register(this)
        arTimelineFontBold.register(this)

        App1.setActivityTheme(this)

        this.handler = App1.getAppState(this).handler

        //		val intent = this.intent
        //		val layoutId = intent.getIntExtra(EXTRA_LAYOUT_ID, 0)
        //		val titleId = intent.getIntExtra(EXTRA_TITLE_ID, 0)
        //		this.title = getString(titleId)

        if (savedInstanceState != null) {
            try {
                val sv = savedInstanceState.getString(STATE_CHOOSE_INTENT_TARGET)
                customShareTarget = CustomShareTarget.values().firstOrNull { it.name == sv }
            } catch (ex: Throwable) {
                log.e(ex, "can't restore customShareTarget.")
            }
        }

        initUi()

        removeDefaultPref()

        load(null, null)
    }

    private fun initUi() {
        setContentView(views.root)

        fixHorizontalPadding(views.llContent, 0f)

        views.lvList.layoutManager = LinearLayoutManager(this)
        views.lvList.adapter = adapter

        views.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                pendingQuery = p0?.toString()
                this@ActAppSetting.handler.removeCallbacks(procQuery)
                this@ActAppSetting.handler.postDelayed(procQuery, 166L)
            }

            override fun beforeTextChanged(
                p0: CharSequence?,
                p1: Int,
                p2: Int,
                p3: Int,
            ) {
            }

            override fun onTextChanged(
                p0: CharSequence?,
                p1: Int,
                p2: Int,
                p3: Int,
            ) {
            }
        })

        views.btnSearchReset.setOnClickListener(this)
    }

    private fun removeDefaultPref() {
        val e = lazyPref.edit()
        var changed = false
        appSettingRoot.scan {
            when {
                (it.pref as? IntPref)?.noRemove == true -> Unit
                it.pref?.removeDefault(lazyPref, e) == true -> changed = true
            }
        }
        if (changed) e.apply()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val sv = customShareTarget?.name
        if (sv != null) outState.putString(STATE_CHOOSE_INTENT_TARGET, sv)
    }

    override fun dispatchKeyEvent(event: KeyEvent) = try {
        super.dispatchKeyEvent(event)
    } catch (ex: Throwable) {
        log.e(ex, "dispatchKeyEvent error")
        false
    }

    override fun onStop() {
        super.onStop()

        // Pull通知チェック間隔を変更したかもしれないのでジョブを再設定する
        restartAllWorker(context = this)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btnSearchReset -> {
                handler.removeCallbacks(procQuery)
                views.etSearch.setText("")
                views.etSearch.hideKeyboard()
                load(lastSection, null)
            }
        }
    }

    private fun load(section: AppSettingItem?, query: String?) {
        adapter.items = buildList {
            var lastPath: String? = null
            fun addParentPath(item: AppSettingItem) {
                add(divider)

                val pathList = ArrayList<String>()
                var parent = item.parent
                while (parent != null) {
                    if (parent.caption != 0) pathList.add(0, getString(parent.caption))
                    parent = parent.parent
                }
                val path = pathList.joinToString("/")
                if (path != lastPath) {
                    lastPath = path
                    add(path)
                    add(divider)
                }
            }

            when {
                // 検索キーワードあり
                query?.isNotBlank() == true -> {
                    lastQuery = query
                    fun scanGroup(level: Int, item: AppSettingItem) {
                        if (item.caption == 0) return
                        if (item.type != SettingType.Section) {
                            when (item.type) {
                                SettingType.Group -> {
                                    var caption = getString(item.caption)
                                    var match = caption.contains(query, ignoreCase = true)
                                    // log.d("group match=$match caption=$caption")
                                    for (child in item.items) {
                                        if (child.caption == 0) continue
                                        caption = getString(child.caption)
                                        match = caption.contains(query, ignoreCase = true)
                                        // log.d("group.item match=$match caption=$caption")
                                        if (match) break
                                    }
                                    if (match) {
                                        // put entire group
                                        addParentPath(item)
                                        add(item)
                                        addAll(item.items)
                                    }
                                    return
                                }
                                else -> {
                                    val caption = getString(item.caption)
                                    val match = caption.contains(query, ignoreCase = true)
                                    // log.d("item match=$match caption=$caption")
                                    if (match) {
                                        addParentPath(item)
                                        add(item)
                                    }
                                }
                            }
                        }
                        for (child in item.items) {
                            scanGroup(level + 1, child)
                        }
                    }
                    scanGroup(0, appSettingRoot)
                }
                // show root page
                section == null -> {
                    val root = appSettingRoot
                    lastQuery = null
                    lastSection = null
                    for (child in root.items) {
                        add(divider)
                        add(child)
                    }
                }
                // show section page
                else -> {
                    lastSection = section
                    lastQuery = null
                    fun scanGroup(level: Int, parent: AppSettingItem?) {
                        parent ?: return
                        for (item in parent.items) {
                            add(divider)
                            add(item)
                            if (item.items.isNotEmpty()) {
                                if (item.type == SettingType.Group) {
                                    addAll(item.items)
                                } else {
                                    scanGroup(level + 1, item)
                                }
                            }
                        }
                    }
                    scanGroup(0, section.cast())
                }
            }
            if (isNotEmpty()) add(divider)
        }
        views.lvList.scrollToPosition(0)
    }

    private fun dip(dp: Float): Int =
        (resources.displayMetrics.density * dp + 0.5f).toInt()

    private fun dip(dp: Int): Int = dip(dp.toFloat())

    override fun onDialogDismissed(dialogId: Int) {
    }

    override fun onColorSelected(dialogId: Int, @ColorInt newColor: Int) {
        val colorTarget = this.colorTarget ?: return
        val ip: IntPref = colorTarget.pref.cast() ?: error("$colorTarget has no in pref")
        val c = when (colorTarget.type) {
            SettingType.ColorAlpha -> newColor.notZero() ?: 1
            else -> newColor or Color.BLACK
        }
        ip.value = c
        val vh = findItemViewHolder(colorTarget)
        vh?.showColor()
        colorTarget.changed(this)
    }

    private val settingHolderList =
        ConcurrentHashMap<Int, VhSettingItem>()

    inner class MyAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var items: List<Any> = emptyList()
            set(newItems) {
                val oldItems = field
                field = newItems
                DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                    override fun getOldListSize() = oldItems.size
                    override fun getNewListSize() = newItems.size
                    override fun areItemsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int,
                    ) = oldItems.elementAtOrNull(oldItemPosition) == newItems.elementAtOrNull(
                        newItemPosition
                    )

                    override fun areContentsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int,
                    ) = oldItems.elementAtOrNull(oldItemPosition) == newItems.elementAtOrNull(
                        newItemPosition
                    )
                }, true).dispatchUpdatesTo(this)
            }

        override fun getItemCount() = items.size

        override fun getItemViewType(position: Int) =
            when (val item = items.elementAtOrNull(position)) {
                divider -> SettingType.Divider.id
                is String -> SettingType.Path.id
                is AppSettingItem -> item.type.id
                else -> error("can't generate view for type $item")
            }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            when (SettingType.map[viewType]) {
                SettingType.Divider -> VhDivider()
                SettingType.Path -> VhPath(parent)
                else -> VhSettingItem(this@ActAppSetting, parent)
            }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            super.onViewRecycled(holder)
            // 古い紐付けを削除
            settingHolderList.entries.filter {
                when (it.value) {
                    holder -> true
                    else -> false
                }
            }.forEach { settingHolderList.remove(it.key) }
        }

        override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items.elementAtOrNull(position)) {
                divider -> viewHolder.cast<VhDivider>()
                is String -> viewHolder.cast<VhPath>()?.bind(item)
                is AppSettingItem -> if (viewHolder is VhSettingItem) {
                    viewHolder.bind(item)
                    // 古い紐付けを削除
                    settingHolderList.entries.filter {
                        when (it.value) {
                            viewHolder -> true
                            else -> false
                        }
                    }.forEach { settingHolderList.remove(it.key) }
                    // 新しい紐付けを覚える
                    settingHolderList[item.id] = viewHolder
                }
            }
        }

        fun findVhSetting(item: AppSettingItem) =
            settingHolderList[item.id]
    }

    private inner class VhDivider(
        viewRoot: FrameLayout = FrameLayout(this@ActAppSetting).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
            addView(View(this@ActAppSetting).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    dip(1)
                ).apply {
                    val marginX = 0
                    val marginY = dip(6)
                    setMargins(marginX, marginY, marginX, marginY)
                }
                setBackgroundColor(context.attrColor(R.attr.colorSettingDivider))
            })
        },
    ) : RecyclerView.ViewHolder(viewRoot)

    private inner class VhPath(
        val parent: ViewGroup,
        val viewRoot: MyTextView = MyTextView(this@ActAppSetting).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
            val padX = 0
            val padY = dip(3)
            setTypeface(typeface, Typeface.BOLD)
            setPaddingRelative(padX, padY, padX, padY)
        },
    ) : RecyclerView.ViewHolder(viewRoot) {
        fun bind(path: String) {
            viewRoot.text = path
        }
    }

    // not private
    class VhSettingItem(
        private val actAppSetting: ActAppSetting,
        parent: ViewGroup,
        val views: LvSettingItemBinding = LvSettingItemBinding
            .inflate(actAppSetting.layoutInflater, parent, false),
    ) : RecyclerView.ViewHolder(views.root),
        TextWatcher,
        AdapterView.OnItemSelectedListener,
        CompoundButton.OnCheckedChangeListener {

        init {
            views.checkBox.setOnCheckedChangeListener(this)
            views.swSwitch.setOnCheckedChangeListener(this)
            views.spSpinner.onItemSelectedListener = this
            views.etEditText.addTextChangedListener(this)

            // https://stackoverflow.com/questions/13614101/fatal-crash-focus-search-returned-a-view-that-wasnt-able-to-take-focus
            views.etEditText.setOnEditorActionListener(OnEditorActionListener { textView, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_NEXT) {
                    @Suppress("WrongConstant")
                    textView.focusSearch(FOCUS_FORWARD)?.requestFocus(FOCUS_FORWARD)
                    // 結果に関わらずこのアクションを処理したとみなす
                    return@OnEditorActionListener true
                }
                false
            })
        }

        private val tvDesc = views.tvDesc
        private val tvError = views.tvError

        var item: AppSettingItem? = null

        private var bindingBusy = false

        fun bind(item: AppSettingItem) {
            bindingBusy = true
            try {
                this.item = item

                views.tvCaption.vg(false)
                views.btnAction.vg(false)
                views.checkBox.vg(false)
                views.swSwitch.vg(false)
                views.llExtra.vg(false)
                views.textView1.vg(false)
                views.llButtonBar.vg(false)
                views.vColor.vg(false)
                views.spSpinner.vg(false)
                views.etEditText.vg(false)
                views.tvDesc.vg(false)
                views.tvError.vg(false)

                val name = if (item.caption == 0) "" else actAppSetting.getString(item.caption)

                tvDesc.vg(item.desc != 0)?.run {
                    text = context.getString(item.desc)
                    if (item.descClickSet) {
                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        setOnClickListener { item.descClick.invoke(actAppSetting) }
                    } else {
                        background = null
                        setOnClickListener(null)
                        isClickable = false
                    }
                }

                when (item.type) {

                    SettingType.Section -> views.btnAction.vg(true)?.run {
                        text = name
                        isEnabledAlpha = item.enabled
                        setOnClickListener {
                            actAppSetting.load(item.cast()!!, null)
                        }
                    }

                    SettingType.Action -> views.btnAction.vg(true)?.run {
                        text = name
                        isEnabledAlpha = item.enabled
                        setOnClickListener { item.action(actAppSetting) }
                    }

                    SettingType.CheckBox -> views.checkBox.run {
                        val bp: BooleanPref = item.pref.cast()
                            ?: error("$name has no boolean pref")
                        vg(false) // skip animation
                        text = name
                        isEnabledAlpha = item.enabled
                        isChecked = bp.value
                        vg(true)
                    }

                    SettingType.Switch -> views.swSwitch.run {
                        val bp: BooleanPref = item.pref.cast()
                            ?: error("$name has no boolean pref")
                        showCaption(name)
                        vg(false) // skip animation
                        actAppSetting.setSwitchColor(views.swSwitch)
                        isEnabledAlpha = item.enabled
                        isChecked = bp.value
                        vg(true)
                    }

                    SettingType.Group -> showCaption(name)

                    SettingType.Sample -> views.llExtra.run {
                        vg(true)
                        removeAllViews()
                        actAppSetting.layoutInflater.inflate(
                            item.sampleLayoutId,
                            views.llExtra,
                            true
                        )
                        item.sampleUpdate(actAppSetting, this)
                    }

                    SettingType.ColorAlpha, SettingType.ColorOpaque -> {
                        val ip = item.pref.cast<IntPref>() ?: error("$name has no int pref")
                        showCaption(name)
                        views.llButtonBar.vg(true)
                        views.vColor.vg(true)
                        views.vColor.setBackgroundColor(ip.value)
                        views.btnEdit.isEnabledAlpha = item.enabled
                        views.btnReset.isEnabledAlpha = item.enabled
                        views.btnEdit.setOnClickListener {
                            actAppSetting.colorTarget = item
                            val color = ip.value
                            val builder = ColorPickerDialog.newBuilder()
                                .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                                .setAllowPresets(true)
                                .setShowAlphaSlider(item.type == SettingType.ColorAlpha)
                                .setDialogId(COLOR_DIALOG_ID)
                            if (color != 0) builder.setColor(color)
                            builder.show(actAppSetting)
                        }
                        views.btnReset.setOnClickListener {
                            ip.removeValue()
                            showColor()
                            item.changed.invoke(actAppSetting)
                        }
                    }

                    SettingType.Spinner -> {
                        showCaption(name)

                        views.spSpinner.vg(true)
                        views.spSpinner.isEnabledAlpha = item.enabled

                        val pi = item.pref
                        if (pi is IntPref) {
                            // 整数型の設定のSpinnerは全て選択肢を単純に覚える
                            val argsInt = item.spinnerArgs
                            actAppSetting.initSpinner(
                                views.spSpinner,
                                argsInt?.map { actAppSetting.getString(it) }
                                    ?: item.spinnerArgsProc(actAppSetting)
                            )
                            views.spSpinner.setSelection(pi.value)
                        } else {
                            item.spinnerInitializer.invoke(actAppSetting, views.spSpinner)
                        }
                    }

                    SettingType.EditText -> {
                        showCaption(name)
                        views.etEditText.vg(true)?.let { etEditText ->
                            val text = when (val pi = item.pref) {
                                is FloatPref ->
                                    item.fromFloat.invoke(actAppSetting, pi.value)
                                is StringPref ->
                                    pi.value
                                else -> error("EditText has incorrect pref $pi")
                            }

                            etEditText.hint = item.hint ?: ""
                            etEditText.inputType = item.inputType
                            etEditText.setText(text)
                            etEditText.setSelection(0, text.length)
                        }
                        updateErrorView()
                    }

                    SettingType.TextWithSelector -> {
                        showCaption(name)
                        views.llButtonBar.vg(true)
                        views.vColor.vg(false)
                        views.textView1.vg(true)

                        item.showTextView.invoke(actAppSetting, views.textView1)

                        views.btnEdit.setOnClickListener {
                            item.onClickEdit.invoke(actAppSetting)
                        }
                        views.btnReset.setOnClickListener {
                            item.onClickReset.invoke(actAppSetting)
                        }
                    }

                    else -> error("unknown type ${item.type}")
                }
            } finally {
                bindingBusy = false
            }
        }

        private fun showCaption(caption: String) {
            if (caption.isNotEmpty()) {
                views.tvCaption.vg(true)?.text = caption
                updateCaption()
            }
        }

        fun updateCaption() {
            val item = item ?: return
            val key = item.pref?.key ?: return

            val sample = views.tvCaption
            var defaultExtra = actAppSetting.defaultLineSpacingExtra[key]
            if (defaultExtra == null) {
                defaultExtra = sample.lineSpacingExtra
                actAppSetting.defaultLineSpacingExtra[key] = defaultExtra
            }
            var defaultMultiplier = actAppSetting.defaultLineSpacingMultiplier[key]
            if (defaultMultiplier == null) {
                defaultMultiplier = sample.lineSpacingMultiplier
                actAppSetting.defaultLineSpacingMultiplier[key] = defaultMultiplier
            }

            val size = item.captionFontSize.invoke(actAppSetting)
            if (size != null) sample.textSize = size

            val spacing = item.captionSpacing.invoke(actAppSetting)
            if (spacing == null || !spacing.isFinite()) {
                sample.setLineSpacing(defaultExtra, defaultMultiplier)
            } else {
                sample.setLineSpacing(0f, spacing)
            }
        }

        private fun updateErrorView() {
            val item = item ?: return
            val sv = views.etEditText.text.toString()
            val error = item.getError.invoke(actAppSetting, sv)
            tvError.vg(error != null)?.text = error
        }

        fun showColor() {
            val item = item ?: return
            val ip = item.pref.cast<IntPref>() ?: return
            val c = ip.value
            views.vColor.setBackgroundColor(c)
        }

        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
        }

        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
        }

        override fun afterTextChanged(p0: Editable?) {
            if (bindingBusy) return
            val item = item ?: return

            val sv = item.filter.invoke(p0?.toString() ?: "")

            when (val pi = item.pref) {
                is StringPref -> pi.value = sv

                is FloatPref -> {
                    val fv = item.toFloat.invoke(actAppSetting, sv)
                    if (fv.isFinite()) {
                        pi.value = fv
                    } else {
                        pi.removeValue()
                    }
                }

                else -> {
                    error("not FloatPref or StringPref")
                }
            }

            item.changed.invoke(actAppSetting)
            updateErrorView()
        }

        override fun onNothingSelected(v: AdapterView<*>?) = Unit

        override fun onItemSelected(
            parent: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long,
        ) {
            if (bindingBusy) return
            val item = item ?: return
            when (val pi = item.pref) {
                is IntPref -> pi.value = views.spSpinner.selectedItemPosition
                else -> item.spinnerOnSelected.invoke(actAppSetting, views.spSpinner, position)
            }
            item.changed.invoke(actAppSetting)
        }

        override fun onCheckedChanged(v: CompoundButton?, isChecked: Boolean) {
            if (bindingBusy) return
            val item = item ?: return
            when (val pi = item.pref) {
                is BooleanPref -> pi.value = isChecked
                else -> error("CompoundButton has no booleanPref $pi")
            }
            item.changed.invoke(actAppSetting)
        }
    }

    private fun initSpinner(spinner: Spinner, captions: List<String>) {
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            captions.toTypedArray()
        ).apply {
            setDropDownViewResource(R.layout.lv_spinner_dropdown)
        }
    }

    ///////////////////////////////////////////////////////////////

    @Suppress("BlockingMethodInNonBlockingContext")
    fun exportAppData() {
        val activity = this
        launchProgress(
            "export app data",
            doInBackground = {
                val cacheDir = activity.cacheDir

                cacheDir.mkdir()

                val file = File(
                    cacheDir,
                    "SubwayTooter.${android.os.Process.myPid()}.${android.os.Process.myTid()}.zip"
                )

                // ZipOutputStreamオブジェクトの作成
                ZipOutputStream(FileOutputStream(file)).use { zipStream ->

                    // アプリデータjson
                    zipStream.putNextEntry(ZipEntry("AppData.json"))
                    try {
                        val jw = JsonWriter(OutputStreamWriter(zipStream, "UTF-8"))
                        AppDataExporter.encodeAppData(activity, jw)
                        jw.flush()
                    } finally {
                        zipStream.closeEntry()
                    }

                    // カラム背景画像
                    val appState = App1.getAppState(activity)
                    for (column in appState.columnList) {
                        AppDataExporter.saveBackgroundImage(activity, zipStream, column)
                    }
                }

                file
            },
            afterProc = {
                val uri = FileProvider.getUriForFile(activity, FILE_PROVIDER_AUTHORITY, it)
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = contentResolver.getType(uri)
                intent.putExtra(Intent.EXTRA_SUBJECT, "SubwayTooter app data")
                intent.putExtra(Intent.EXTRA_STREAM, uri)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                arNoop.launch(intent)
            }
        )
    }

    // open data picker
    fun importAppData1() {
        try {
            val intent = intentOpenDocument("*/*")
            arImportAppData.launch(intent)
        } catch (ex: Throwable) {
            showToast(ex, "importAppData(1) failed.")
        }
    }

    // after data picked
    private fun importAppData2(bConfirm: Boolean, uri: Uri) {

        val type = contentResolver.getType(uri)
        log.d("importAppData type=$type")

        if (!bConfirm) {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.app_data_import_confirm))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ -> importAppData2(true, uri) }
                .show()
            return
        }

        val data = Intent()
        data.data = uri
        setResult(ActMain.RESULT_APP_DATA_IMPORT, data)
        finish()
    }

    fun findItemViewHolder(item: AppSettingItem?) =
        item?.let { adapter.findVhSetting(it) }

    fun showSample(item: AppSettingItem?) {
        item ?: error("showSample: missing item…")
        findItemViewHolder(item)?.let {
            item.sampleUpdate.invoke(this, it.views.llExtra)
        }
    }

    // リスト内部のSwitchCompat全ての色を更新する
    fun setSwitchColor() = setSwitchColor(views.lvList)

    //////////////////////////////////////////////////////

    fun formatFontSize(fv: Float): String =
        when {
            fv.isFinite() -> String.format(defaultLocale(this), "%.1f", fv)
            else -> ""
        }

    fun parseFontSize(src: String): Float {
        try {
            if (src.isNotEmpty()) {
                val f = NumberFormat.getInstance(defaultLocale(this)).parse(src)?.toFloat()
                return when {
                    f == null -> Float.NaN
                    f.isNaN() -> Float.NaN
                    f < 0f -> 0f
                    f > 999f -> 999f
                    else -> f
                }
            }
        } catch (ex: Throwable) {
            log.e(ex, "parseFontSize failed.")
        }

        return Float.NaN
    }

    val defaultLineSpacingExtra = HashMap<String, Float>()
    val defaultLineSpacingMultiplier = HashMap<String, Float>()

    private fun handleFontResult(item: AppSettingItem?, data: Intent, fileName: String) {
        item ?: error("handleFontResult : setting item is null")
        data.handleGetContentResult(contentResolver).firstOrNull()?.uri?.let {
            val file = saveTimelineFont(it, fileName)
            if (file != null) {
                (item.pref as? StringPref)?.value = file.absolutePath
                showTimelineFont(item)
            }
        }
    }

    fun showTimelineFont(item: AppSettingItem?) {
        item ?: return
        val holder = findItemViewHolder(item) ?: return
        item.showTextView.invoke(this, holder.views.textView1)
    }

    fun showTimelineFont(item: AppSettingItem, tv: TextView) {
        try {
            item.pref.cast<StringPref>()?.value.notEmpty()?.let { url ->
                tv.typeface = Typeface.DEFAULT
                val face = Typeface.createFromFile(url)
                tv.typeface = face
                tv.text = url
                return
            }
        } catch (ex: Throwable) {
            log.e(ex, "showTimelineFont failed.")
        }
        // fallback
        tv.text = getString(R.string.not_selected)
        tv.typeface = Typeface.DEFAULT
    }

    private fun saveTimelineFont(uri: Uri?, fileName: String): File? {
        try {
            if (uri == null) {
                showToast(false, "missing uri.")
                return null
            }

            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

            val dir = filesDir

            dir.mkdir()

            val tmpFile = File(dir, "$fileName.tmp")

            val source: InputStream? = contentResolver.openInputStream(uri)
            if (source == null) {
                showToast(false, "openInputStream returns null. uri=$uri")
                return null
            } else {
                source.use { inStream ->
                    FileOutputStream(tmpFile).use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
            }

            val face = Typeface.createFromFile(tmpFile)
            if (face == null) {
                showToast(false, "Typeface.createFromFile() failed.")
                return null
            }

            val file = File(dir, fileName)
            if (!tmpFile.renameTo(file)) {
                showToast(false, "File operation failed.")
                return null
            }

            return file
        } catch (ex: Throwable) {
            log.e(ex, "saveTimelineFont failed.")
            showToast(ex, "saveTimelineFont failed.")
            return null
        }
    }

    //////////////////////////////////////////////////////

    inner class AccountAdapter(val list: List<SavedAccount>) : BaseAdapter() {

        override fun getCount() = 1 + list.size
        override fun getItemId(position: Int) = 0L
        override fun getItem(position: Int) = if (position == 0) null else list[position - 1]

        override fun getView(position: Int, viewOld: View?, parent: ViewGroup): View {
            val view = viewOld ?: layoutInflater.inflate(
                R.layout.lv_spinner_wrap_text,
                parent,
                false
            )
            view.findViewById<TextView>(android.R.id.text1).text = when (position) {
                0 -> getString(R.string.default_post_account_default_action)
                else -> daoAcctColor.getNickname(list[position - 1])
            }
            return view
        }

        override fun getDropDownView(position: Int, viewOld: View?, parent: ViewGroup): View {
            val view =
                viewOld ?: layoutInflater.inflate(R.layout.lv_spinner_dropdown, parent, false)
            view.findViewById<TextView>(android.R.id.text1).text = when (position) {
                0 -> getString(R.string.default_post_account_default_action)
                else -> daoAcctColor.getNickname(list[position - 1])
            }
            return view
        }

        /**
         * 設定に保存したdbId から アダプターのインデクス値に変換
         */

        // 見つからなければ0,見つかったら1以上
        internal fun getIndexFromId(dbId: Long): Int =
            1 + list.indexOfFirst { it.db_id == dbId }

        /**
         * アダプターのインデクス値から設定に保存するdbIdに変換
         * - -1L : タブレットモードなら毎回尋ねる。スマホモードなら現在開いているカラム。
         */
        internal fun getIdFromIndex(position: Int): Long =
            if (position > 0) list[position - 1].db_id else -1L
    }

    private class Item(
        val id: String,
        val caption: String,
        val offset: Int,
    )

    inner class TimeZoneAdapter internal constructor() : BaseAdapter() {

        private val list = ArrayList<Item>()

        init {

            for (id in TimeZone.getAvailableIDs()) {
                val tz = TimeZone.getTimeZone(id)

                // GMT数字を指定するタイプのタイムゾーンは無視する。ただしGMT-12:00の１項目だけは残す
                // 3文字のIDは曖昧な場合があるので非推奨
                // '/' を含まないIDは列挙しない
                if (!when {
                        !tz.id.contains('/') -> false
                        tz.id == "Etc/GMT+12" -> true
                        tz.id.startsWith("Etc/") -> false
                        else -> true
                    }
                ) continue

                var offset = tz.rawOffset.toLong()
                val caption = when (offset) {
                    0L -> "(UTC\u00B100:00) ${tz.id} ${tz.displayName}"

                    else -> {

                        val format = when {
                            offset > 0 -> "(UTC+%02d:%02d) %s %s"
                            else -> "(UTC-%02d:%02d) %s %s"
                        }

                        offset = abs(offset)

                        val hours = TimeUnit.MILLISECONDS.toHours(offset)
                        val minutes =
                            TimeUnit.MILLISECONDS.toMinutes(offset) - TimeUnit.HOURS.toMinutes(hours)

                        String.format(format, hours, minutes, tz.id, tz.displayName)
                    }
                }
                if (list.none { it.caption == caption }) {
                    list.add(Item(id, caption, tz.rawOffset))
                }
            }

            list.sortWith { a, b ->
                (a.offset - b.offset).notZero() ?: a.caption.compareTo(b.caption)
            }

            list.add(0, Item("", getString(R.string.device_timezone), 0))
        }

        override fun getCount(): Int {
            return list.size
        }

        override fun getItem(position: Int): Any {
            return list[position]
        }

        override fun getItemId(position: Int): Long {
            return 0
        }

        override fun getView(position: Int, viewOld: View?, parent: ViewGroup): View {
            val view = viewOld ?: layoutInflater.inflate(
                android.R.layout.simple_spinner_item,
                parent,
                false
            )
            val item = list[position]
            view.findViewById<TextView>(android.R.id.text1).text = item.caption
            return view
        }

        override fun getDropDownView(position: Int, viewOld: View?, parent: ViewGroup): View {
            val view =
                viewOld ?: layoutInflater.inflate(R.layout.lv_spinner_dropdown, parent, false)
            val item = list[position]
            view.findViewById<TextView>(android.R.id.text1).text = item.caption
            return view
        }

        internal fun getIndexFromId(tzId: String): Int {
            val index = list.indexOfFirst { it.id == tzId }
            return if (index == -1) 0 else index
        }

        internal fun getIdFromIndex(position: Int): String {
            return list[position].id
        }
    }

    fun openCustomShareChooser(appSettingItem: AppSettingItem, target: CustomShareTarget) {
        try {
            val rv = DlgAppPicker(
                this,
                intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, getString(R.string.content_sample))
                },
                addCopyAction = true
            ) { setCustomShare(appSettingItem, target, it) }
                .show()
            if (!rv) showToast(true, "share target app is not installed.")
        } catch (ex: Throwable) {
            log.e(ex, "openCustomShareChooser failed.")
            showToast(ex, "openCustomShareChooser failed.")
        }
    }

    fun setCustomShare(appSettingItem: AppSettingItem, target: CustomShareTarget, value: String) {
        val sp: StringPref = appSettingItem.pref.cast() ?: error("$target: not StringPref")
        sp.value = value

        showCustomShareIcon(findItemViewHolder(appSettingItem)?.views?.textView1, target)
    }

    fun showCustomShareIcon(tv: TextView?, target: CustomShareTarget) {
        tv ?: return
        val cn = CustomShare.getCustomShareComponentName(target)
        val (label, icon) = CustomShare.getInfo(this, cn)
        tv.text = label ?: getString(R.string.not_selected)
        tv.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
        tv.compoundDrawablePadding = (resources.displayMetrics.density * 4f + 0.5f).toInt()
    }

    fun openWebBrowserChooser(
        appSettingItem: AppSettingItem,
        intent: Intent,
        filter: (ResolveInfo) -> Boolean,
    ) {
        try {
            val rv = DlgAppPicker(
                this,
                intent = intent,
                filter = filter,
                addCopyAction = false
            ) { setWebBrowser(appSettingItem, it) }
                .show()
            if (!rv) showToast(true, "share target app is not installed.")
        } catch (ex: Throwable) {
            log.e(ex, "openCustomShareChooser failed.")
            showToast(ex, "openCustomShareChooser failed.")
        }
    }

    private fun setWebBrowser(appSettingItem: AppSettingItem, value: String) {
        val sp: StringPref = appSettingItem.pref.cast()
            ?: error("${getString(appSettingItem.caption)}: not StringPref")
        sp.value = value

        showWebBrowser(findItemViewHolder(appSettingItem)?.views?.textView1, value)
    }

    private fun showWebBrowser(tv: TextView?, prefValue: String) {
        tv ?: return
        val cn = prefValue.cn()
        val (label, icon) = CustomShare.getInfo(this, cn)
        tv.text = label ?: getString(R.string.not_selected)
        tv.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
        tv.compoundDrawablePadding = (resources.displayMetrics.density * 4f + 0.5f).toInt()
    }

    fun exportLog() {
        val context = this
        launchAndShowError {
            val logZipFile = daoLogData.createLogFile(context)
            val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, logZipFile)

            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_EMAIL, arrayOf("tateisu@gmail.com"))
                putExtra(Intent.EXTRA_SUBJECT, "SubwayTooter bug report")
                val soc = if (Build.VERSION.SDK_INT >= 31) {
                    "manufacturer=${Build.SOC_MANUFACTURER} product=${Build.SOC_MODEL}"
                } else {
                    "(no information)"
                }
                val text = """
                    |Please write about the problem.
                    |…
                    |…
                    |…
                    |…
                    |    
                    |Don't rewrite below lines.
                    |SubwayTooter version: $currentVersion $packageName
                    |Android version: ${Build.VERSION.RELEASE}
                    |Device: manufacturer=${Build.MANUFACTURER} product=${Build.PRODUCT} model=${Build.MODEL} device=${Build.DEVICE}
                    |$soc
                    """.trimMargin("|")

                // ログに警告がでるが偽陽性だった。
                // extras!!.putCharSequenceArrayList に変えると警告は出なくなるが、Gmailに本文が表示されない。
                putExtra(Intent.EXTRA_TEXT, text)
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // 送る前にgrantしておく
            val chooserIntent = Intent.createChooser(intent, null)
            grantFileProviderUri(intent, uri)
            grantFileProviderUri(chooserIntent, uri)
            startActivity(chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private val currentVersion: String
        get() = try {
            packageManager.getPackageInfoCompat(packageName)!!.versionName
        } catch (ignored: Throwable) {
            "??"
        }

    private fun Context.grantFileProviderUri(
        intent: Intent,
        uri: Uri,
        permission: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION,
    ) {
        try {
            intent.addFlags(permission)
            packageManager.queryIntentActivitiesCompat(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            ).forEach {
                grantUriPermission(
                    it.activityInfo.packageName,
                    uri,
                    permission
                )
            }
        } catch (ex: Throwable) {
            log.e(ex, "grantFileProviderUri failed.")
        }
    }
}
