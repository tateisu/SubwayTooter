package jp.juggler.subwaytooter.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.PictureDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.flexbox.*
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.databinding.EmojiPickerDialogBinding
import jp.juggler.subwaytooter.emoji.*
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.pref.PrefS
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.emojiSizeMode
import jp.juggler.subwaytooter.util.minHeightCompat
import jp.juggler.subwaytooter.util.minWidthCompat
import jp.juggler.subwaytooter.view.NetworkEmojiView
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.data.*
import jp.juggler.util.log.*
import jp.juggler.util.ui.*
import org.jetbrains.anko.wrapContent
import kotlin.math.abs
import kotlin.math.sign

private class EmojiPicker(
    private val activity: AppCompatActivity,
    private val accessInfo: SavedAccount?,
    private val closeOnSelected: Boolean,
    private val onPicked: suspend (EmojiBase, bInstanceHasCustomEmoji: Boolean) -> Unit,
) {
    companion object {
        private val log = LogCategory("EmojiPicker")

        private const val VT_CATEGORY = 0
        private const val VT_CUSTOM_EMOJI = 1
        private const val VT_TWEMOJI = 2
        private const val VT_COMPAT_EMOJI = 3

        private const val gridCols = 6

        private fun EmojiCategory.getUnicodeEmojis() =
            emojiList.map { PickerItemUnicode(unicodeEmoji = it) }
    }

    private class SkinTone(val codeInt: Int) {
        val code = StringBuilder().apply { appendCodePoint(codeInt) }.toString()
    }

    private sealed interface PickerItem

    private class PickerItemUnicode(val unicodeEmoji: UnicodeEmoji) : PickerItem {
        var isWrapBefore = false
    }

    private class PickerItemCustom(val customEmoji: CustomEmoji) : PickerItem {
        var isWrapBefore = false
    }

    private open class PickerItemCategory(
        var name: String,
        val category: EmojiCategory,
        val original: PickerItemCategory? = null,
        var next: PickerItemCategory? = null,
    ) : PickerItem {
        val items = ArrayList<PickerItem>()
        open fun createFiltered(keywordLower: String?) =
            PickerItemCategory(
                name = name,
                category = category,
                original = this,
            ).also { dst ->
                dst.items.addAll(
                    if (keywordLower.isNullOrEmpty()) {
                        items
                    } else {
                        items.filter {
                            when (it) {
                                is PickerItemCustom ->
                                    it.customEmoji.shortcode.contains(keywordLower) ||
                                            it.customEmoji.aliases?.any { a ->
                                                a.contains(keywordLower, ignoreCase = true)
                                            } == true

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
        val accessInfo: SavedAccount?,
    ) : PickerItemCategory(name, EmojiCategory.Recent) {

        private val recentsJsonList: List<JsonObject>?
            get() = try {
                PrefS.spEmojiPickerRecent.value.decodeJsonArray().objectList()
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
                log.w(ex, "loadRecents failed.")
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
                PrefS.spEmojiPickerRecent.value = list.toJsonArray().toString()
            } catch (ex: Throwable) {
                log.e(ex, "can't save spEmojiPickerRecent")
            }
            // カテゴリ内のPickerItemの更新
            try {
                list.loadRecents()
            } catch (ex: Throwable) {
                log.e(ex, "loadRecents failed.")
            }
        }
    }

    private sealed class ViewHolderBase(view: View) : RecyclerView.ViewHolder(view) {
        abstract fun bind(item: PickerItem)
    }

    private inner class VhCategory(
        view: FrameLayout = FrameLayout(activity),
    ) : ViewHolderBase(view) {
        var lastItem: PickerItemCategory? = null

        val tv = AppCompatTextView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(matchParent, wrapContent)
            minHeightCompat = (density * 48f + 0.5f).toInt()
            background = ContextCompat.getDrawable(
                this@EmojiPicker.activity,
                R.drawable.btn_bg_transparent_round6dp
            )
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            includeFontPadding = false
            val paddingH = (density * 4f + 0.5f).toInt()
            val paddingV = (density * 2f + 0.5f).toInt()
            setPadding(paddingH, paddingV, paddingH, paddingV)
            compoundDrawablePadding = (density * 4f + 0.5f).toInt()

            setOnClickListener {
                val orig = lastItem?.original
                    ?: return@setOnClickListener
                lastExpandCategory = if (lastExpandCategory != orig) orig else lastItem?.next
                // 再表示
                showFiltered(
                    lastSelectedCategory,
                    lastSelectedKeyword,
                    scrollCategoryTab = false,
                    scrollToCategory = true,
                )
            }
        }

        init {
            view.layoutParams = FlexboxLayoutManager.LayoutParams(wrapContent, wrapContent)
                .apply {
                    flexGrow = 1f
                    isWrapBefore = true
                }
            view.setPadding(cellMargin, cellMargin, cellMargin, cellMargin)
            view.addView(tv)
        }

        override fun bind(item: PickerItem) {
            if (item !is PickerItemCategory) return
            lastItem = item
            tv.text = item.name

            val drawable = when {
                !canCollapse -> null
                lastExpandCategory == item.original -> R.drawable.ic_arrow_drop_down
                else -> R.drawable.ic_arrow_drop_up
            }?.let {
                activity.resDrawable(it).wrapAndTint(activity.attrColor(R.attr.colorTextContent))
            }
            tv.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null)
        }
    }

    private fun View.updateWrapBefore(isWrapBefore: Boolean) {
        val lp = layoutParams as FlexboxLayoutManager.LayoutParams
        lp.isWrapBefore = isWrapBefore
        layoutParams = lp
    }

    private inner class VhCustomEmoji(
        val view: FrameLayout = FrameLayout(activity),
    ) : ViewHolderBase(view) {
        val niv = NetworkEmojiView(
            activity,
            sizeMode = accessInfo.emojiSizeMode(),
        ).apply {
            layoutParams = FrameLayout.LayoutParams(gridSize, gridSize)
        }

        init {
            view.setButtonBackground()
            view.setOnClickListener(pickerItemClickListener)
            view.setOnLongClickListener(pickerItemLongClickListener)
            view.layoutParams = FlexboxLayoutManager.LayoutParams(wrapContent, wrapContent)
            view.setPadding(cellMargin, cellMargin, cellMargin, cellMargin)
            view.addView(niv)
        }

        override fun bind(item: PickerItem) {
            if (activity.isDestroyed) return
            if (item is PickerItemCustom) {
                view.updateWrapBefore(item.isWrapBefore)
                view.setTag(R.id.btnAbout, item)
                niv.setEmoji(
                    url = if (disableAnimation) {
                        item.customEmoji.staticUrl
                    } else {
                        item.customEmoji.url
                    },
                    initialAspect = item.customEmoji.aspect,
                    defaultHeight = gridSize,
                )
            }
        }
    }

    private inner class VhTwemoji(
        val view: FrameLayout = FrameLayout(activity),
    ) : ViewHolderBase(view) {
        val iv = AppCompatImageView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(gridSize, gridSize)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        init {
            view.setButtonBackground()
            view.setOnClickListener(pickerItemClickListener)
            view.setOnLongClickListener(pickerItemLongClickListener)
            view.layoutParams = FlexboxLayoutManager.LayoutParams(wrapContent, wrapContent)
            view.setPadding(cellMargin, cellMargin, cellMargin, cellMargin)
            view.addView(iv)
        }

        override fun bind(item: PickerItem) {
            if (activity.isDestroyed) return
            if (item is PickerItemUnicode) {
                view.updateWrapBefore(item.isWrapBefore)
                view.setTag(R.id.btnAbout, item)
                val emoji = applySkinTone(item.unicodeEmoji)
                if (emoji.isSvg) {
                    Glide.with(activity)
                        .`as`(PictureDrawable::class.java)
                        .load("file:///android_asset/${emoji.assetsName}")
                        .into(iv)
                } else {
                    Glide.with(activity)
                        .load(emoji.drawableId)
                        .into(iv)
                }
            }
        }
    }

    private inner class VhAppCompatEmoji(
        val view: FrameLayout = FrameLayout(activity),
    ) : ViewHolderBase(view) {
        val tv = AppCompatTextView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(gridSize, gridSize)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setLineSpacing(0f, 0f)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, gridSize.toFloat() * 0.7f)
        }

        init {
            view.setButtonBackground()
            view.setOnClickListener(pickerItemClickListener)
            view.setOnLongClickListener(pickerItemLongClickListener)
            view.layoutParams = FlexboxLayoutManager.LayoutParams(wrapContent, wrapContent)
            view.addView(tv)
        }

        override fun bind(item: PickerItem) {
            if (activity.isDestroyed) return
            if (item is PickerItemUnicode) {
                view.updateWrapBefore(item.isWrapBefore)
                view.setTag(R.id.btnAbout, item)
                val unicodeEmoji = applySkinTone(item.unicodeEmoji)
                tv.text = unicodeEmoji.unifiedCode
            }
        }
    }

    class FlexboxLayoutManagerWrapper(context: Context) : FlexboxLayoutManager(context) {
        override fun generateLayoutParams(lp: ViewGroup.LayoutParams): RecyclerView.LayoutParams {
            return LayoutParams(lp)
        }
    }

    private inner class GridAdapter : RecyclerView.Adapter<ViewHolderBase>() {

        var list: List<PickerItem> = emptyList()
            set(value) {
                field = value
                @Suppress("NotifyDataSetChanged")
                notifyDataSetChanged()
            }

        override fun getItemCount() = list.size

        override fun getItemViewType(position: Int) =
            when (list[position]) {
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
                else -> error("unknown viewType=$viewType")
            }

        override fun onBindViewHolder(viewHolder: ViewHolderBase, position: Int) {
            viewHolder.bind(list[position])
        }
    }

    private enum class FlickStatus {
        None,
        Start,
        Intercepted,
    }

    private lateinit var pickerCategries: List<PickerItemCategory>

    private val gridAdapter = GridAdapter()

    private val views = EmojiPickerDialogBinding.inflate(activity.layoutInflater)

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
                gridAdapter.notifyDataSetChanged()
            }
        }
    }

    private val matchParent = RecyclerView.LayoutParams.MATCH_PARENT

    private val useTwemoji = PrefB.bpUseTwemoji.value
    private val disableAnimation = PrefB.bpDisableEmojiAnimation.value

    private var selectedTone: SkinTone = (ibSkinTone[0].tag as SkinTone)

    private lateinit var dialog: Dialog

    private var bInstanceHasCustomEmoji = false

    private var lastSelectedCategory: EmojiCategory? = null
    private var lastSelectedKeyword: String? = null

    private var recentCategory: PickerItemCategoryRecent? = null

    private var lastExpandCategory: PickerItemCategory? = null
    private var canCollapse = true

    private val density = activity.resources.displayMetrics.density
    val cellMargin = (density * 1f + 0.5f).toInt()
    val gridSize = (density * 48f + 0.5f).toInt()
    private val cancelY = 16f
    private val interceptX = 40f
    private var tracker: VelocityTracker? = null
    private var dragging = FlickStatus.None
    private var startX = 0f
    private var startY = 0f

    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            showFiltered(null, s?.toString())
        }
    }

    private val pickerItemClickListener = View.OnClickListener { v ->
        val targetEmoji: EmojiBase
        val targetName: String
        val targetInstance: String?
        when (val item = v.getTag(R.id.btnAbout)) {
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

        recentCategory?.update(targetName, targetInstance)

        if (closeOnSelected) {
            dialog.dismissSafe()
        } else if (lastSelectedCategory == null || lastSelectedCategory == EmojiCategory.Recent) {
            // 全カテゴリ表示や最近の表示は最近の絵文字の順序を変えるため更新してしまう
            showFiltered(lastSelectedCategory, lastSelectedKeyword)
            // XXX: タップ状態の表示が行えない…
        } else {
            // この場合はビューの更新は不要で、タップ状態の表示を行える
        }
        activity.launchAndShowError {
            onPicked(targetEmoji, bInstanceHasCustomEmoji)
        }
    }

    private val pickerItemLongClickListener = View.OnLongClickListener { v ->
        when (val item = v.getTag(R.id.btnAbout)) {
            is PickerItemCustom -> {
                activity.showEmojiDetailDialog(
                    detail = item.customEmoji.json.toString(indentFactor = 2, sort = true),
                    initialzeNiv = {
                        setEmoji(
                            url = when {
                                disableAnimation -> item.customEmoji.staticUrl
                                else -> item.customEmoji.url
                            },
                            initialAspect = item.customEmoji.aspect,
                            defaultHeight = layoutParams.height,
                        )
                    }
                )
            }

            is PickerItemUnicode -> when {
                useTwemoji -> {
                    activity.showEmojiDetailDialog(
                        detail = item.unicodeEmoji.namesLower.joinToString("\n"),
                        initializeImage = {
                            val emoji = applySkinTone(item.unicodeEmoji)
                            if (emoji.isSvg) {
                                Glide.with(this@EmojiPicker.activity)
                                    .`as`(PictureDrawable::class.java)
                                    .load("file:///android_asset/${emoji.assetsName}")
                                    .into(this)
                            } else {
                                Glide.with(this@EmojiPicker.activity)
                                    .load(emoji.drawableId)
                                    .into(this)
                            }
                        }
                    )
                }

                else -> {
                    activity.showEmojiDetailDialog(
                        detail = item.unicodeEmoji.namesLower.joinToString("\n"),
                        initializeText = {
                            this.text = applySkinTone(item.unicodeEmoji).unifiedCode
                        }
                    )
                }
            }

            else -> Unit
        }
        true
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
            log.w(ex, "createCustomEmojiCategories failed.")
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
        if (PrefB.bpEmojiPickerCategoryOther.value) {
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
        scrollCategoryTab: Boolean = true,
        scrollToCategory: Boolean = false,
    ) {
        lastSelectedCategory = selectedCategory
        lastSelectedKeyword = selectedKeyword
        val keywordLower = selectedKeyword?.lowercase()?.trim()
        this.canCollapse = PrefB.bpCollapseEmojiPickerCategory.value &&
                keywordLower.isNullOrEmpty() &&
                (selectedCategory == null || selectedCategory == EmojiCategory.Custom)

        val list = buildList {
            val filteredCategories = pickerCategries.filter {
                selectedCategory == null || it.category == selectedCategory
            }.mapNotNull { category ->
                category.createFiltered(keywordLower)
                    .takeIf { it.items.isNotEmpty() }
            }

            for (i in filteredCategories.indices) {
                filteredCategories[i].next =
                    filteredCategories.elementAtOrNull(i + 1)?.original
                        ?: filteredCategories.elementAtOrNull(i - 1)?.original
            }

            if (lastExpandCategory == null ||
                filteredCategories.none { it.original == lastExpandCategory }
            ) lastExpandCategory = filteredCategories.firstOrNull()?.original

            filteredCategories.forEach {
                if (selectedCategory == null || it.category == EmojiCategory.Custom) {
                    // 見出し付き表示の場合は折りたたむ可能性がある
                    add(it)
                    if (!canCollapse || lastExpandCategory == it.original) addAll(it.items)
                } else {
                    // カスタム以外のカテゴリが選択されている場合、（ヘッダがなく解除できないので) 折りたたみはできない
                    addAll(it.items)
                }
            }
        }
        list.forEachIndexed { i, v ->
            fun isPreviousHeader() = list.elementAtOrNull(i - 1) is PickerItemCategory
            when (v) {
                is PickerItemUnicode -> v.isWrapBefore = isPreviousHeader()
                is PickerItemCustom -> v.isWrapBefore = isPreviousHeader()
                else -> Unit
            }
        }

        gridAdapter.list = list

        val targetCategory = lastExpandCategory
        if (scrollToCategory && targetCategory != null) {
            views.root.handler?.postDelayed({
                gridAdapter.list.indexOfFirst { (it as? PickerItemCategory)?.original == targetCategory }
                    .takeIf { it != -1 }
                    ?.let { views.rvGrid.smoothScrollToPosition(it) }
            }, 100L)
        }

        for (it in views.llCategories.children) {
            val backgroundId = when (it.tag) {
                selectedCategory -> R.drawable.bg_button_cw
                else -> R.drawable.btn_bg_transparent_round6dp
            }
            it.background = ContextCompat.getDrawable(it.context, backgroundId)

            if (it.tag == selectedCategory && scrollCategoryTab) {
                val oldScrollX = views.svCategories.scrollX
                val visibleWidth = views.svCategories.width
                log.i("left=${it.left},r=${it.right},s=$oldScrollX")
                when {
                    oldScrollX > it.left ->
                        views.svCategories.smoothScrollTo(it.left, 0)

                    oldScrollX + visibleWidth < it.right ->
                        views.svCategories.smoothScrollTo(it.right - visibleWidth, 0)
                }
            }
        }
    }

    private fun addCategoryButton(category: EmojiCategory) {
        val density = activity.resources.displayMetrics.density
        val wrapContent = FlexboxLayout.LayoutParams.WRAP_CONTENT
        val minWidth = (density * 48f + 0.5f).toInt()
        val padTb = (density * 4f + 0.5f).toInt()
        val padLr = (density * 6f + 0.5f).toInt()
        AppCompatButton(activity).apply {
            tag = category
            layoutParams = FlexboxLayout.LayoutParams(wrapContent, wrapContent)
            background =
                ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent_round6dp)
            minWidthCompat = minWidth
            minHeightCompat = minWidth
            setPadding(padLr, padTb, padLr, padTb)
            text = activity?.getString(category.titleId)
            setOnClickListener {
                views.etFilter.removeTextChangedListener(textWatcher)
                views.etFilter.setText("")
                views.etFilter.addTextChangedListener(textWatcher)
                showFiltered(category, null)
            }
        }.let { views.llCategories.addView(it) }
    }

    private fun movePage(delta: Int) {
        val categories = buildList {
            addAll(pickerCategries.map { it.category }.distinct().sorted())
        }
        val newIndex = when (
            val oldIndex = categories.indexOfFirst { it == lastSelectedCategory }
        ) {
            -1 -> if (delta > 0) 0 else categories.size - 1
            else -> (oldIndex + categories.size + delta) % categories.size
        }
        val newCategory = categories[newIndex]
        showFiltered(newCategory, null)
    }

    private fun View.setButtonBackground() {
        background = ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent_round6dp)
    }

    private fun handleTouch(ev: MotionEvent, wasIntercept: Boolean) =
        try {
            when (ev.actionMasked) {
                MotionEvent.ACTION_CANCEL -> {
                    log.i("ACTION_CANCEL wasIntercept=$wasIntercept")
                    dragging = FlickStatus.None
                    wasIntercept
                }

                MotionEvent.ACTION_UP -> {
                    try {
                        log.i("ACTION_UP wasIntercept=$wasIntercept")
                        if (dragging == FlickStatus.Intercepted) {
                            tracker?.let {
                                it.addMovement(ev)
                                it.computeCurrentVelocity(1000)
                                val vx = it.xVelocity
                                val vy = it.yVelocity
                                val vxDp = vx / density
                                val aspect = abs(vx) / abs(vy)
                                log.i("vx=$vx vy=$vy")
                                if (aspect < 2f) {
                                    log.i("not gesture: aspect=$aspect")
                                } else if (abs(vxDp) < 40f) {
                                    log.i("not gesture: vxDp=$vxDp")
                                } else {
                                    movePage((vxDp.sign * -1f).toInt())
                                }
                            }
                        }
                    } finally {
                        dragging = FlickStatus.None
                    }
                    wasIntercept
                }

                MotionEvent.ACTION_DOWN -> {
                    log.i("ACTION_DOWN wasIntercept=$wasIntercept")
                    // ドラッグ開始
                    dragging = FlickStatus.Start
                    if (tracker == null) {
                        tracker = VelocityTracker.obtain()
                    }
                    tracker?.clear()
                    startX = ev.x
                    startY = ev.y
                    wasIntercept
                }

                MotionEvent.ACTION_MOVE -> {
                    if (dragging == FlickStatus.None) {
                        wasIntercept
                    } else {
                        // 移動量追跡
                        tracker?.addMovement(ev)
                        val deltaX = abs(ev.x - startX) / density
                        val deltaY = abs(ev.y - startY) / density
                        when {
                            // すでにインターセプトしている
                            wasIntercept -> true

                            // 上下方向に大きく動かしたらそれ以上追跡しない
                            deltaY >= cancelY -> {
                                log.i("not flick! $deltaY")
                                dragging = FlickStatus.None
                                false
                            }

                            // 横方向に大きく動かしたらインターセプトする
                            deltaX >= interceptX && deltaX > deltaY -> {
                                log.i("intercept! $deltaX")
                                dragging = FlickStatus.Intercepted
                                true
                            }

                            else -> {
                                log.d("not yet intercept. $deltaX, $deltaY")
                                false
                            }
                        }
                    }
                }

                else -> log.w("handleTouch else $ev")
            }
        } catch (ex: Throwable) {
            log.e(ex, "handleTouch failed. ev=$ev, wasIntercept=$wasIntercept")
            wasIntercept
        }

    suspend fun start() {
        pickerCategries = buildCategoryList()

        bInstanceHasCustomEmoji = pickerCategries.any { it.category == EmojiCategory.Custom }

        pickerCategries.map { it.category }.distinct().sorted().forEach {
            addCategoryButton(it)
        }

        views.etFilter.addTextChangedListener(textWatcher)

        showFiltered(null, null)

        views.giGrid.intercept = { handleTouch(it, wasIntercept = false) }
        views.giGrid.touch = { handleTouch(it, wasIntercept = true) }

        views.rvGrid.apply {
            layoutManager = FlexboxLayoutManagerWrapper(context).apply {
                flexDirection = FlexDirection.ROW
                flexWrap = FlexWrap.WRAP
                justifyContent = JustifyContent.FLEX_START
            }
            adapter = gridAdapter
        }

        showSkinTone()

        this.dialog = Dialog(activity)
        dialog.setContentView(views.root)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener {
            tracker?.recycle()
        }
        dialog.window?.let { w ->
            @Suppress("DEPRECATION")
            w.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            )
        }
        dialog.show()
    }
}

fun launchEmojiPicker(
    activity: AppCompatActivity,
    accessInfo: SavedAccount?,
    closeOnSelected: Boolean,
    onPicked: suspend (EmojiBase, bInstanceHasCustomEmoji: Boolean) -> Unit,
) = activity.launchAndShowError {
    EmojiPicker(
        activity = activity,
        accessInfo = accessInfo,
        closeOnSelected = closeOnSelected,
        onPicked = onPicked,
    ).start()
}
