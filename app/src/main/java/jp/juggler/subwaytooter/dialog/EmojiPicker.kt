package jp.juggler.subwaytooter.dialog

import android.app.Dialog
import android.graphics.Rect
import android.graphics.drawable.PictureDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import com.bumptech.glide.Glide
import com.google.android.flexbox.FlexboxLayout
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.databinding.EmojiPickerDialogBinding
import jp.juggler.subwaytooter.emoji.*
import jp.juggler.subwaytooter.global.appPref
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.pref.PrefS
import jp.juggler.subwaytooter.pref.put
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.minHeightCompat
import jp.juggler.subwaytooter.util.minWidthCompat
import jp.juggler.subwaytooter.view.NetworkEmojiView
import jp.juggler.util.*

private class EmojiPicker(
    private val activity: AppCompatActivity,
    private val accessInfo: SavedAccount?,
    private val closeOnSelected: Boolean,
    private val onPicked: (EmojiBase, bInstanceHasCustomEmoji: Boolean) -> Unit,
) {
    companion object {
        private val log = LogCategory("EmojiPicker")

        private const val VT_CATEGORY = 0
        private const val VT_CUSTOM_EMOJI = 1
        private const val VT_TWEMOJI = 2
        private const val VT_COMPAT_EMOJI = 3
        private const val VT_SPACE = 4

        private const val gridCols = 6

        private fun EmojiCategory.getUnicodeEmojis() =
            emojiList.map { PickerItemUnicode(unicodeEmoji = it) }
    }

    private class SkinTone(val codeInt: Int) {
        val code = StringBuilder().apply { appendCodePoint(codeInt) }.toString()
    }

    private sealed interface PickerItem
    private object PickerItemSpace : PickerItem
    private class PickerItemUnicode(val unicodeEmoji: UnicodeEmoji) : PickerItem
    private class PickerItemCustom(val customEmoji: CustomEmoji) : PickerItem

    private open class PickerItemCategory(
        var name: String,
        val category: EmojiCategory? = null,
    ) : PickerItem {
        val items = ArrayList<PickerItem>()

        open fun createFiltered(keywordLower: String?) =
            PickerItemCategory(name = name, category = category).also { dst ->
                dst.items.addAll(
                    if (keywordLower.isNullOrEmpty()) {
                        items
                    } else {
                        items.filter {
                            when (it) {
                                is PickerItemCustom ->
                                    it.customEmoji.shortcode.contains(keywordLower)
                                is PickerItemUnicode ->
                                    it.unicodeEmoji.namesLower.any { n -> n.contains(keywordLower) }
                                else -> false
                            }
                        }
                    }
                )
            }
    }

    private class PickerItemCategoryRecent(
        name: String,
        category: EmojiCategory = EmojiCategory.Recent,
        val accessInfo: SavedAccount?,
    ) : PickerItemCategory(name, category) {

        private val recentsJsonList: List<JsonObject>?
            get() = try {
                PrefS.spEmojiPickerRecent().decodeJsonArray().objectList()
            } catch (ex: Throwable) {
                log.w(ex, "can't load spEmojiPickerRecent")
                null
            }

        private fun JsonObject.parseRecent1(
            customEmojiMap: HashMap<String, CustomEmoji>?,
        ): PickerItem? {
            val name = string("name")
            val instance = string("instance")
            try {
                name ?: error("missing emoji name")
                when (instance) {
                    null -> EmojiMap.shortNameMap[name]?.let {
                        return PickerItemUnicode(unicodeEmoji = it)
                    }
                    accessInfo?.apiHost?.ascii ->
                        customEmojiMap?.get(name)?.let {
                            return PickerItemCustom(customEmoji = it)
                        }
                }
            } catch (ex: Throwable) {
                log.w(ex, "can't add emoji. $name, $instance")
            }
            return null
        }

        private fun List<JsonObject>.loadRecents() {
            try {
                val customEmojiMap =
                    accessInfo?.let { App1.custom_emoji_lister.getMapNonBlocking(it) }
                val newItems = mapNotNull { it.parseRecent1(customEmojiMap) }
                items.clear()
                items.addAll(newItems)
            } catch (ex: Throwable) {
                log.w(ex)
            }
        }

        // 最近使用した絵文字のPickerCategoryを作る
        fun load() = recentsJsonList?.loadRecents()

        fun update(
            targetName: String,
            targetInstance: String?,
        ) {
            // Recentをロード(他インスタンスの絵文字を含む)
            val list = recentsJsonList?.toMutableList() ?: ArrayList()

            // 選択された絵文字と同じ項目を除去
            // 項目が増えすぎたら減らす
            // ユニコード絵文字256個、カスタム絵文字はインスタンス別256個まで
            var nCount = 0
            val it = list.iterator()
            while (it.hasNext()) {
                val item = it.next()
                val itemName = item.string("name")
                val itemInstance = item.string("instance")
                if (itemInstance != targetInstance) continue
                if (itemName == targetName || ++nCount >= 256) {
                    it.remove()
                }
            }

            // 先頭に項目を追加
            list.add(0, JsonObject().apply {
                put("name", targetName)
                targetInstance?.let { put("instance", it) }
            })

            // 保存する
            try {
                val sv = list.toJsonArray().toString()
                appPref.edit().put(PrefS.spEmojiPickerRecent, sv).apply()
            } catch (ex: Throwable) {
                log.e(ex)
            }
            // カテゴリ内のPickerItemの更新
            try {
                list.loadRecents()
            } catch (ex: Throwable) {
                log.e(ex)
            }
        }
    }

    private sealed class ViewHolderBase(view: View) : RecyclerView.ViewHolder(view) {
        abstract fun bind(item: PickerItem)
    }

    private inner class VhCategory(
        val view: AppCompatTextView = AppCompatTextView(activity),
    ) : ViewHolderBase(view) {
        init {
            view.layoutParams = RecyclerView.LayoutParams(matchParent, gridSize)
            view.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            view.includeFontPadding = false
        }

        override fun bind(item: PickerItem) {
            if (item is PickerItemCategory) {
                view.text = item.name
            }
        }
    }

    private inner class VhSpace(
        view: View = View(activity),
    ) : ViewHolderBase(view) {
        init {
            view.layoutParams = RecyclerView.LayoutParams(matchParent, gridSize)
        }

        override fun bind(item: PickerItem) {
        }
    }

    private inner class VhCustomEmoji(
        val view: NetworkEmojiView = NetworkEmojiView(activity),
    ) : ViewHolderBase(view) {

        init {
            view.setOnClickListener(pickerItemClickListener)
            view.layoutParams = RecyclerView.LayoutParams(matchParent, gridSize)
        }

        override fun bind(item: PickerItem) {
            if (activity.isDestroyed) return
            if (item is PickerItemCustom) {
                view.setTag(R.id.btnAbout, item)
                view.setEmoji(
                    if (disableAnimation) {
                        item.customEmoji.staticUrl
                    } else {
                        item.customEmoji.url
                    }
                )
            }
        }
    }

    private inner class VhTwemoji(
        val view: AppCompatImageView = AppCompatImageView(activity),
    ) : ViewHolderBase(view) {
        init {
            view.setOnClickListener(pickerItemClickListener)
            view.layoutParams = RecyclerView.LayoutParams(matchParent, gridSize)
            view.scaleType = ImageView.ScaleType.FIT_CENTER
        }

        override fun bind(item: PickerItem) {
            if (activity.isDestroyed) return
            if (item is PickerItemUnicode) {
                view.setTag(R.id.btnAbout, item)
                val emoji = applySkinTone(item.unicodeEmoji)
                if (emoji.isSvg) {
                    Glide.with(activity)
                        .`as`(PictureDrawable::class.java)
                        .load("file:///android_asset/${emoji.assetsName}")
                        .into(view)
                } else {
                    Glide.with(activity)
                        .load(emoji.drawableId)
                        .into(view)
                }
            }
        }
    }

    private inner class VhAppCompatEmoji(
        val view: AppCompatTextView = AppCompatTextView(activity),
    ) : ViewHolderBase(view) {
        init {
            view.setOnClickListener(pickerItemClickListener)
            view.layoutParams = RecyclerView.LayoutParams(matchParent, gridSize)
            view.gravity = Gravity.CENTER
            view.setLineSpacing(0f, 0f)
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, gridSize.toFloat() * 0.7f)
            view.includeFontPadding = false
        }

        override fun bind(item: PickerItem) {
            if (activity.isDestroyed) return
            if (item is PickerItemUnicode) {
                view.setTag(R.id.btnAbout, item)
                val unicodeEmoji = applySkinTone(item.unicodeEmoji)
                view.text = unicodeEmoji.unifiedCode
            }
        }
    }

    class GridDecoration(private val space: Int) : ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State,
        ) {
            outRect.left = space
            outRect.right = space
            outRect.bottom = space
            // Add top margin only for the first item to avoid double space between items
            outRect.top = if (parent.getChildLayoutPosition(view) == 0) {
                space
            } else {
                0
            }
        }
    }

    private inner class GridAdapter : RecyclerView.Adapter<ViewHolderBase>() {

        var list: List<PickerItem> = emptyList()
            set(value) {
                field = value
                @Suppress("NotifyDataSetChanged")
                notifyDataSetChanged()
            }

        val spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) =
                when {
                    list[position] is PickerItemCategory -> gridCols
                    else -> 1
                }
        }

        override fun getItemCount() = list.size

        override fun getItemViewType(position: Int) =
            when (list[position]) {
                is PickerItemSpace -> VT_SPACE
                is PickerItemCategory -> VT_CATEGORY
                is PickerItemCustom -> VT_CUSTOM_EMOJI
                is PickerItemUnicode -> when {
                    useTwemoji -> VT_TWEMOJI
                    else -> VT_COMPAT_EMOJI
                }
            }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int) =
            when (viewType) {
                VT_CATEGORY -> VhCategory()
                VT_CUSTOM_EMOJI -> VhCustomEmoji()
                VT_TWEMOJI -> VhTwemoji()
                VT_COMPAT_EMOJI -> VhAppCompatEmoji()
                VT_SPACE -> VhSpace()
                else -> error("unknown viewType=$viewType")
            }

        override fun onBindViewHolder(viewHolder: ViewHolderBase, position: Int) {
            viewHolder.bind(list[position])
        }
    }

    private val views = EmojiPickerDialogBinding.inflate(activity.layoutInflater)

    private lateinit var pickerCategries: List<PickerItemCategory>

    private val adapter = GridAdapter()

    private val ibSkinTone = listOf(
        Pair(R.id.btnSkinTone0, 0),
        Pair(R.id.btnSkinTone1, 0x1F3FB),
        Pair(R.id.btnSkinTone2, 0x1F3FC),
        Pair(R.id.btnSkinTone3, 0x1F3FD),
        Pair(R.id.btnSkinTone4, 0x1F3FE),
        Pair(R.id.btnSkinTone5, 0x1F3FF),
    ).map { (btnId, skinToneCode) ->
        views.root.findViewById<ImageButton>(btnId).apply {
            tag = SkinTone(skinToneCode)
            setOnClickListener {
                selectedTone = (it.tag as SkinTone)
                showSkinTone()
                @Suppress("NotifyDataSetChanged")
                adapter.notifyDataSetChanged()
            }
        }
    }

    private val gridSize = (0.5f + 48f * activity.resources.displayMetrics.density).toInt()
    private val matchParent = RecyclerView.LayoutParams.MATCH_PARENT

    private val useTwemoji = PrefB.bpUseTwemoji()
    private val disableAnimation = PrefB.bpDisableEmojiAnimation()

    private var selectedTone: SkinTone = (ibSkinTone[0].tag as SkinTone)

    private lateinit var dialog: Dialog

    private var bInstanceHasCustomEmoji = false

    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            showFiltered(null, s?.toString())
        }
    }

    private var lastSelectedCategory: EmojiCategory? = null
    private var lastSelectedKeyword: String? = null

    private var recentCategory: PickerItemCategoryRecent? = null

    private val pickerItemClickListener = View.OnClickListener {
        val targetEmoji: EmojiBase
        val targetName: String
        val targetInstance: String?
        when (val item = it.getTag(R.id.btnAbout)) {
            is PickerItemUnicode -> {
                targetEmoji = applySkinTone(item.unicodeEmoji)
                targetName = targetEmoji.unifiedName
                targetInstance = null
            }
            is PickerItemCustom -> {
                targetEmoji = item.customEmoji
                targetName = item.customEmoji.shortcode
                targetInstance = accessInfo!!.apiHost.ascii
            }
            else -> return@OnClickListener
        }

        if (closeOnSelected) dialog.dismissSafe()

        recentCategory?.update(targetName, targetInstance)
        @Suppress("NotifyDataSetChanged")
        showFiltered(lastSelectedCategory, lastSelectedKeyword)
        onPicked(targetEmoji, bInstanceHasCustomEmoji)
    }

    private suspend fun createCustomEmojiCategories(): List<PickerItemCategory> {
        accessInfo ?: error("missing accessInfo")
        val context = activity
        val srcList = App1.custom_emoji_lister.getList(accessInfo)
        val nameMap = HashMap<String, PickerItemCategory>()
        for (emoji in srcList) {
            if (!emoji.visibleInPicker) continue
            val categoryName = emoji.category ?: ""
            (nameMap[categoryName]
                ?: PickerItemCategory(
                    name = categoryName,
                    category = EmojiCategory.Custom,
                ).also { nameMap[categoryName] = it })
                .items.add(PickerItemCustom(emoji))
        }
        val otherCategory = nameMap[""]

        // カテゴリ名の頭に「カスタム」を追加
        return nameMap.values.onEach {
            it.name = when (it) {
                otherCategory -> when (nameMap.size) {
                    0 -> context.getString(R.string.emoji_category_custom)
                    else -> context.getString(
                        R.string.emoji_picker_custom_of,
                        context.getString(R.string.others)
                    )
                }
                else ->
                    context.getString(R.string.emoji_picker_custom_of, it.name)
            }
        }.sortedWith { l, r ->
            if (l == otherCategory) 1
            else if (r == otherCategory) -1
            else l.name.compareTo(r.name)
        }
    }

    private suspend fun buildCategoryList() = buildList {
        // 最近使った絵文字
        PickerItemCategoryRecent(
            name = activity.getString(R.string.emoji_category_recent),
            accessInfo = accessInfo,
        ).also {
            recentCategory = it
            it.load()
            add(it)
        }

        // カスタム絵文字
        try {
            addAll(createCustomEmojiCategories())
        } catch (ex: Throwable) {
            log.w(ex)
        }

        val categories = mutableListOf(
            EmojiCategory.People,
            EmojiCategory.ComplexTones,
            EmojiCategory.Nature,
            EmojiCategory.Foods,
            EmojiCategory.Activities,
            EmojiCategory.Places,
            EmojiCategory.Objects,
            EmojiCategory.Symbols,
            EmojiCategory.Flags,
        )
        if (PrefB.bpEmojiPickerCategoryOther(activity)) {
            categories.add(EmojiCategory.Others)
        }
        for (category in categories) {
            val pc = PickerItemCategory(
                category = category,
                name = activity.getString(category.titleId),
            )
            pc.items.addAll(category.getUnicodeEmojis())
            add(pc)
        }
    }

    private fun applySkinTone(emojiArg: UnicodeEmoji): UnicodeEmoji {
        // トーン指定がないなら元のコード
        val selectedTone = selectedTone.takeIf { it.codeInt > 0 } ?: return emojiArg

        var emoji = emojiArg

        // Recentなどでは既にsuffixがついた名前が用意されている
        // suffixを除去する
        emoji.toneParent?.let { emoji = it }

        // 指定したトーンのサフィックスを追加して、絵文字が存在すればその名前にする
        emoji.toneChildren.find { it.first == selectedTone.code }
            ?.let { return it.second }

        // なければトーンなしの絵文字
        return emoji
    }

    private fun showSkinTone() {
        ibSkinTone.forEach {
            when (selectedTone) {
                it.tag -> it.setImageResource(R.drawable.check_mark)
                else -> it.setImageDrawable(null)
            }
        }
    }

    private fun showFiltered(
        selectedCategory: EmojiCategory?,
        selectedKeyword: String?,
    ) {
        lastSelectedCategory = selectedCategory
        lastSelectedKeyword = selectedKeyword
        adapter.list = buildList {
            val keywordLower = selectedKeyword?.lowercase()?.trim()
            pickerCategries.filter {
                selectedCategory == null || it.category == selectedCategory
            }.mapNotNull { category ->
                category.createFiltered(keywordLower)
                    .takeIf { it.items.isNotEmpty() }
            }.forEach {
                add(it)
                addAll(it.items)
                val mod = it.items.size % gridCols
                if (mod > 0) {
                    repeat(gridCols - mod) {
                        add(PickerItemSpace)
                    }
                }
            }
        }
    }

    suspend fun start() {
        pickerCategries = buildCategoryList()

        bInstanceHasCustomEmoji = pickerCategries.any { it.category == EmojiCategory.Custom }

        val wrapContent = FlexboxLayout.LayoutParams.WRAP_CONTENT
        val density = activity.resources.displayMetrics.density
        val minWidth = (density * 48f + 0.5f).toInt()
        val padTb = (density * 4f + 0.5f).toInt()
        val padLr = (density * 6f + 0.5f).toInt()
        arrayOf(
            null,
            EmojiCategory.Recent,
            EmojiCategory.Custom,
            EmojiCategory.People,
            EmojiCategory.ComplexTones,
            EmojiCategory.Nature,
            EmojiCategory.Foods,
            EmojiCategory.Activities,
            EmojiCategory.Places,
            EmojiCategory.Objects,
            EmojiCategory.Symbols,
            EmojiCategory.Flags,
            EmojiCategory.Others,
        ).forEach {
            AppCompatButton(activity).apply {
                layoutParams = FlexboxLayout.LayoutParams(wrapContent, wrapContent)
                background =
                    ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent_round6dp)
                minWidthCompat = minWidth
                minHeightCompat = minWidth
                setPadding(padLr, padTb, padLr, padTb)
                text = activity?.getString(it?.titleId ?: R.string.all)
                setOnClickListener { _ ->
                    views.etFilter.removeTextChangedListener(textWatcher)
                    views.etFilter.setText("")
                    views.etFilter.addTextChangedListener(textWatcher)
                    showFiltered(it, null)
                }
            }.let { views.llCategories.addView(it) }
        }

        views.etFilter.addTextChangedListener(textWatcher)

        showFiltered(null, null)

        views.rvGrid.adapter = adapter
        views.rvGrid.layoutManager = GridLayoutManager(
            activity,
            gridCols,
            RecyclerView.VERTICAL,
            false
        ).also {
            it.spanSizeLookup = adapter.spanSizeLookup
        }

        val cellSpacing = (density * 1f + 0.5f).toInt()
        views.rvGrid.addItemDecoration(GridDecoration(cellSpacing))
        showSkinTone()

        this.dialog = Dialog(activity)
        dialog.setContentView(views.root)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        val w = dialog.window
        // XXX Android 11 で SOFT_INPUT_ADJUST_RESIZE はdeprecatedになった
        @Suppress("DEPRECATION")
        w?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
        dialog.show()
    }
}

fun launchEmojiPicker(
    activity: AppCompatActivity,
    accessInfo: SavedAccount?,
    closeOnSelected: Boolean,
    onPicked: (EmojiBase, bInstanceHasCustomEmoji: Boolean) -> Unit,
) = activity.launchAndShowError {
    EmojiPicker(
        activity = activity,
        accessInfo = accessInfo,
        closeOnSelected = closeOnSelected,
        onPicked = onPicked,
    ).start()
}
