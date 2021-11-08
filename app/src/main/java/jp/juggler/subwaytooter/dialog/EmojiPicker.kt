package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.graphics.Typeface
import android.graphics.drawable.PictureDrawable
import android.util.SparseArray
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.annotation.StringRes
import androidx.viewpager.widget.ViewPager
import com.astuetz.PagerSlidingTabStrip
import com.bumptech.glide.Glide
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.emoji.*
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.pref.PrefS
import jp.juggler.subwaytooter.pref.put
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.view.HeaderGridView
import jp.juggler.subwaytooter.view.MyViewPager
import jp.juggler.subwaytooter.view.NetworkEmojiView
import jp.juggler.util.*
import org.jetbrains.anko.padding
import org.jetbrains.anko.textColor
import java.util.*

class SkinTone(codeInt: Int) {
    val code = StringBuilder().apply { appendCodePoint(codeInt) }.toString()
}

data class EmojiPickerResult(
    val bInstanceHasCustomEmoji: Boolean,
    val emoji: EmojiBase,
)

@SuppressLint("InflateParams")
class EmojiPicker(
    private val activity: Activity,
    private val accessInfo: SavedAccount?,
    val closeOnSelected: Boolean,
    private val onEmojiPickerSelected: (EmojiPickerResult) -> Unit,
    // onEmojiPickedのinstance引数は通常の絵文字ならnull、カスタム絵文字なら非null、
) : View.OnClickListener, ViewPager.OnPageChangeListener {

    internal class EmojiItem(
        val unicodeEmoji: UnicodeEmoji? = null,
        val name: String = "",
        val instance: String? = null,
    )

    internal class CustomCategory(
        val rangeStart: Int,
        val rangeLength: Int,
        val view: View,
    )

    companion object {

        internal val log = LogCategory("EmojiPicker")

        internal val toneList = arrayOf(
            SkinTone(0x1F3FB),
            SkinTone(0x1F3FC),
            SkinTone(0x1F3FD),
            SkinTone(0x1F3FE),
            SkinTone(0x1F3FF),
        )
    }

    private val viewRoot: View

    private val pagerAdapter: EmojiPickerPagerAdapter

    private val pageList = ArrayList<EmojiPickerPage>()

    private val pager: MyViewPager

    private val dialog: Dialog

    private val pagerStrip: PagerSlidingTabStrip

    private val ibSkinTone: Array<ImageButton>

    private var selectedTone: Int = 0

    private val recentList = ArrayList<EmojiItem>()

    private var customList = ArrayList<EmojiItem>()
    private var customCategories = ArrayList<CustomCategory>()

    private val customEmojiMap = HashMap<String, CustomEmoji>()

    private val recentPageIdx: Int

    private val customPageIdx: Int

    init {

        // recentをロードする
        val pref = App1.pref
        val sv = PrefS.spEmojiPickerRecent(pref)
        if (sv.isNotEmpty()) {
            try {
                for (item in sv.decodeJsonArray().objectList()) {
                    val name = item.string("name")
                    val instance = item.string("instance")
                    if (name.isNullOrEmpty()) continue
                    if (instance == null) {
                        val unicodeEmoji = EmojiMap.shortNameMap[name]
                        if (unicodeEmoji != null) {
                            recentList.add(EmojiItem(unicodeEmoji = unicodeEmoji))
                            continue
                        }
                    }
                    recentList.add(EmojiItem(name = name, instance = instance))
                }
            } catch (ex: Throwable) {
                log.trace(ex)
            }
        }

        // create page
        this.recentPageIdx = pageList.size
        pageList.add(EmojiPickerPage(false, EmojiCategory.Recent, R.string.emoji_category_recent))

        this.customPageIdx = pageList.size
        pageList.add(EmojiPickerPage(false, EmojiCategory.Custom, R.string.emoji_category_custom))

        pageList.add(
            EmojiPickerPage(
                true,
                EmojiCategory.People,
                R.string.emoji_category_people
            )
        )
        pageList.add(
            EmojiPickerPage(
                false,
                EmojiCategory.ComplexTones,
                R.string.emoji_category_composite_tones
            )
        )
        pageList.add(
            EmojiPickerPage(
                true,
                EmojiCategory.Nature,
                R.string.emoji_category_nature
            )
        )
        pageList.add(
            EmojiPickerPage(
                true,
                EmojiCategory.Foods,
                R.string.emoji_category_foods
            )
        )
        pageList.add(
            EmojiPickerPage(
                true,
                EmojiCategory.Activities,
                R.string.emoji_category_activity
            )
        )
        pageList.add(
            EmojiPickerPage(
                true,
                EmojiCategory.Places,
                R.string.emoji_category_places
            )
        )
        pageList.add(
            EmojiPickerPage(
                true,
                EmojiCategory.Objects,
                R.string.emoji_category_objects
            )
        )
        pageList.add(
            EmojiPickerPage(
                true,
                EmojiCategory.Symbols,
                R.string.emoji_category_symbols
            )
        )
        pageList.add(
            EmojiPickerPage(
                true,
                EmojiCategory.Flags,
                R.string.emoji_category_flags
            )
        )
        if (PrefB.bpEmojiPickerCategoryOther(activity)) {
            pageList.add(
                EmojiPickerPage(
                    true,
                    EmojiCategory.Others,
                    R.string.emoji_category_others
                )
            )
        }

        this.viewRoot = activity.layoutInflater.inflate(R.layout.dlg_picker_emoji, null, false)
        this.pager = viewRoot.findViewById(R.id.pager)
        this.pagerStrip = viewRoot.findViewById(R.id.pager_strip)

        this.ibSkinTone = arrayOf(
            initSkinTone(0, viewRoot.findViewById(R.id.btnSkinTone1)),
            initSkinTone(1, viewRoot.findViewById(R.id.btnSkinTone2)),
            initSkinTone(2, viewRoot.findViewById(R.id.btnSkinTone3)),
            initSkinTone(3, viewRoot.findViewById(R.id.btnSkinTone4)),
            initSkinTone(4, viewRoot.findViewById(R.id.btnSkinTone5))
        )
        showSkinTone()

        this.pagerAdapter = EmojiPickerPagerAdapter()
        pager.adapter = pagerAdapter
        pagerStrip.setViewPager(pager)

        pager.addOnPageChangeListener(this)
        onPageSelected(0)

        // カスタム絵文字をロードする
        if (accessInfo != null) {
            setCustomEmojiList(
                App1.custom_emoji_lister.getList(accessInfo) {
                    setCustomEmojiList(it) // ロード完了時に呼ばれる
                }
            )
        }

        this.dialog = Dialog(activity)
        dialog.setContentView(viewRoot)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        val w = dialog.window

        // XXX Android 11 で SOFT_INPUT_ADJUST_RESIZE はdeprecatedになった
        @Suppress("DEPRECATION")
        w?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private var bInstanceHasCustomEmoji = false

    private fun setCustomEmojiList(list: ArrayList<CustomEmoji>?) {
        if (list == null) return
        bInstanceHasCustomEmoji = true

        // make categories
        val newList = TreeMap<String, ArrayList<EmojiItem>>()
        for (emoji in list) {
            if (!emoji.visibleInPicker) continue
            val category = emoji.category ?: ""
            var subList = newList[category]
            if (subList == null) {
                subList = ArrayList()
                newList[category] = subList
            }
            subList.add(EmojiItem(name = emoji.shortcode, instance = accessInfo!!.apiHost.ascii))
            customEmojiMap[emoji.shortcode] = emoji
        }
        // compose categories data list
        val entries = newList.entries
        customList.clear()
        customCategories.clear()
        customList.ensureCapacity(entries.sumOf { it.value.size })
        customCategories.ensureCapacity(entries.size)
        entries.forEach {
            val rangeStart = customList.size
            customList.addAll(it.value)
            val rangeLength = customList.size - rangeStart

            customCategories.add(CustomCategory(
                rangeStart,
                rangeLength,
                TextView(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    )
                    padding = (resources.displayMetrics.density * 2f + 0.5f).toInt()
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL

                    setTypeface(typeface, Typeface.BOLD)

                    textColor = this@EmojiPicker.activity.attrColor(R.attr.colorContentText)
                    textSize = 16f // SP単位

                    text = when (val name = it.key) {
                        "" -> this@EmojiPicker.activity.getString(R.string.custom_emoji)
                        else -> name
                    }
                }
            ))
        }

        pagerAdapter.getPageViewHolder(customPageIdx)?.reloadCustomEmoji()
        pagerAdapter.getPageViewHolder(recentPageIdx)?.notifyDataSetChanged()
    }

    internal fun show() {
        dialog.show()
    }

    override fun onPageScrollStateChanged(state: Int) {
    }

    override fun onPageScrolled(
        position: Int,
        positionOffset: Float,
        positionOffsetPixels: Int,
    ) {
    }

    override fun onPageSelected(position: Int) {
        try {
            val hasSkinTone = pageList[position].hasSkinTone
            val visibility = if (hasSkinTone) View.VISIBLE else View.INVISIBLE
            ibSkinTone.forEach { it.visibility = visibility }
        } catch (ex: Throwable) {
            log.trace(ex)
        }
    }

    private fun applySkinTone(emojiArg: UnicodeEmoji): UnicodeEmoji {

        var emoji = emojiArg

        val tone = if (selectedTone == 0) {
            null
        } else {
            viewRoot.findViewById<View>(selectedTone).tag as SkinTone
        } ?: return emoji

        // Recentなどでは既にsuffixがついた名前が用意されている
        // suffixを除去する
        emoji.toneParent?.let { emoji = it }

        // 指定したトーンのサフィックスを追加して、絵文字が存在すればその名前にする
        emoji.toneChildren.find { it.first == tone.code }?.let { return it.second }

        // なければトーンなしの絵文字
        return emoji
    }

    private fun initSkinTone(idx: Int, ib: ImageButton): ImageButton {
        ib.tag = toneList[idx]
        ib.setOnClickListener(this)
        return ib
    }

    private fun showSkinTone() {
        for (button in ibSkinTone) {
            if (selectedTone == button.id) {
                button.setImageResource(R.drawable.check_mark)
            } else {
                button.setImageDrawable(null)
            }
        }
    }

    override fun onClick(view: View) {
        val id = view.id
        selectedTone = if (selectedTone == id) 0 else id
        showSkinTone()
        pagerAdapter.eachViewHolder { _, vh -> vh.reloadSkinTone() }
    }

    internal inner class EmojiPickerPage(
        val hasSkinTone: Boolean,
        val category: EmojiCategory,
        @StringRes titleId: Int,
    ) {

        val title: String = activity.getString(titleId)

        val emojiList = when (category) {

            EmojiCategory.Custom -> customList

            EmojiCategory.Recent -> ArrayList<EmojiItem>().apply {
                for (item in recentList) {
                    if (item.instance != null && item.instance != accessInfo?.apiHost?.ascii) continue
                    add(item)
                }
            }

            else -> ArrayList<EmojiItem>().apply {
                category.emojiList.forEach { emoji ->
                    add(EmojiItem(unicodeEmoji = emoji, name = emoji.unifiedName))
                }
            }
        }
    }

    inner class EmojiPickerPageViewHolder(activity: Activity, root: View) : BaseAdapter(),
        AdapterView.OnItemClickListener {

        private val gridView: HeaderGridView = root.findViewById(R.id.gridView)
        private val wh = (0.5f + 48f * activity.resources.displayMetrics.density).toInt()

        private var page: EmojiPickerPage? = null

        internal fun onPageCreate(page: EmojiPickerPage) {
            this.page = page
            if (page.category == EmojiCategory.Custom) {
                reloadCustomEmoji()
            } else {
                gridView.adapter = this
            }
            gridView.onItemClickListener = this
        }

        internal fun onPageDestroy() {
        }

        internal fun reloadSkinTone() {
            val page = this.page ?: error("page is not assigned")
            if (page.category != EmojiCategory.Custom) {
                this.notifyDataSetChanged()
            }
        }

        fun reloadCustomEmoji() {
            gridView.reset()
            if (customCategories.size >= 2) {
                for (item in customCategories) {
                    gridView.addHeaderView(
                        rangeStart = item.rangeStart,
                        rangeLength = item.rangeLength,
                        itemHeight = wh,
                        v = item.view,
                        isSelectable = false
                    )
                }
            }
            gridView.adapter = this
        }

        override fun getCount(): Int {
            return page?.emojiList?.size ?: 0
        }

        override fun getItem(i: Int): Any? {
            return page?.emojiList?.get(i)
        }

        override fun getItemId(i: Int): Long {
            return 0
        }

        override fun getViewTypeCount(): Int {
            return 2
        }

        override fun getItemViewType(position: Int): Int {
            return if (page?.emojiList?.get(position)?.instance != null) 1 else 0
        }

        override fun getView(position: Int, viewOld: View?, viewGroup: ViewGroup): View {
            val page = this.page ?: error("page is not assigned")
            val view: View
            val item = page.emojiList[position]
            var unicodeEmoji = item.unicodeEmoji
            if (unicodeEmoji == null) {
                view = viewOld ?: NetworkEmojiView(activity).apply {
                    layoutParams = AbsListView.LayoutParams(wh, wh)
                }
                view.setTag(R.id.btnAbout, item)
                (view as? NetworkEmojiView)?.setEmoji(customEmojiMap[item.name]?.url)
            } else if(PrefB.bpUseTwemoji(App1.pref)){
                view = viewOld
                    ?: ImageView(activity).apply {
                        layoutParams = AbsListView.LayoutParams(wh, wh)
                    }

                view.setTag(R.id.btnAbout, item)

                if (view is ImageView && view.activity?.isDestroyed == false) {
                    if (page.hasSkinTone) unicodeEmoji = applySkinTone(unicodeEmoji)

                    if (unicodeEmoji.isSvg) {
                        Glide.with(activity)
                            .`as`(PictureDrawable::class.java)
                            .load("file:///android_asset/${unicodeEmoji.assetsName}")
                            .into(view)
                    } else {
                        Glide.with(activity)
                            .load(unicodeEmoji.drawableId)
                            .into(view)
                    }
                }
            }else{
                view = viewOld
                    ?: TextView(activity).apply {
                        layoutParams = AbsListView.LayoutParams(wh, wh)
                    }

                view.setTag(R.id.btnAbout, item)

                if (view is TextView && view.activity?.isDestroyed == false) {
                    if (page.hasSkinTone) unicodeEmoji = applySkinTone(unicodeEmoji)

                    view.text = unicodeEmoji.unifiedCode
                    view.gravity = Gravity.CENTER
                    view.setLineSpacing(0f,0f)
                    view.setTextSize(TypedValue.COMPLEX_UNIT_PX, wh.toFloat()*0.7f)
                }
            }

            return view
        }

        override fun onItemClick(
            adapterView: AdapterView<*>,
            view: View,
            idxArg: Int,
            l: Long,
        ) {
            val page = this.page ?: return

            val idx = gridView.findListItemIndex(idxArg)
            val item = page.emojiList.elementAtOrNull(idx) ?: return

            // unicode絵文字
            item.unicodeEmoji?.let {
                selected(if (page.hasSkinTone) applySkinTone(it) else it)
                return
            }

            // カスタム絵文字
            customEmojiMap[item.name]?.let {
                selected(it)
                return
            }
        }
    }

    // name はスキントーン適用済みであること
    internal fun selected(emoji: EmojiBase) {

        val pref = App1.pref

        if (closeOnSelected) dialog.dismissSafe()

        // Recentをロード(他インスタンスの絵文字を含む)
        val list: MutableList<JsonObject> = try {
            PrefS.spEmojiPickerRecent(pref).decodeJsonArray().objectList()
        } catch (_: Throwable) {
            emptyList()
        }.toMutableList()

        // 選択された絵文字と同じ項目を除去
        // 項目が増えすぎたら減らす

        val name: String
        val instance: String?
        when (emoji) {
            is UnicodeEmoji -> {
                name = emoji.unifiedName
                instance = null
            }
            is CustomEmoji -> {
                name = emoji.shortcode
                instance = emoji.apDomain.ascii
            }
        }

        run {
            var nCount = 0
            val it = list.iterator()
            while (it.hasNext()) {
                val item = it.next()
                if (instance == item.string("instance")) {
                    if (name == item.string("name") || ++nCount >= 256) {
                        it.remove()
                    }
                }
            }
        }

        // 先頭に項目を追加
        list.add(0, JsonObject().apply {
            put("name", name)
            if (instance != null) put("instance", instance)
        })

        // 保存する
        try {
            val sv = list.toJsonArray().toString()
            App1.pref.edit().put(PrefS.spEmojiPickerRecent, sv).apply()
        } catch (ignored: Throwable) {
        }

        onEmojiPickerSelected(EmojiPickerResult(bInstanceHasCustomEmoji, emoji))
    }

    internal inner class EmojiPickerPagerAdapter : androidx.viewpager.widget.PagerAdapter() {

        private val inflater: LayoutInflater
        private val holderList = SparseArray<EmojiPickerPageViewHolder>()

        init {
            this.inflater = activity.layoutInflater
        }

        override fun getCount(): Int {
            return pageList.size
        }

        private fun Int.validPage() = this >= 0 && this < pageList.size

        private fun getPage(idx: Int): EmojiPickerPage? {
            return if (idx.validPage()) pageList[idx] else null
        }

        fun getPageViewHolder(idx: Int): EmojiPickerPageViewHolder? {
            return if (idx.validPage()) holderList.get(idx) else null
        }

        inline fun eachViewHolder(block: (Int, EmojiPickerPageViewHolder) -> Unit) {
            for (i in 0 until pageList.size) {
                val vh = holderList.get(i) ?: continue
                block(i, vh)
            }
        }

        override fun getPageTitle(page_idx: Int): CharSequence? {
            return getPage(page_idx)?.title
        }

        override fun isViewFromObject(view: View, obj: Any): Boolean {
            return view === obj
        }

        override fun instantiateItem(container: ViewGroup, page_idx: Int): Any {
            val root = inflater.inflate(R.layout.page_emoji_picker, container, false)
            container.addView(root, 0)

            val page = pageList[page_idx]
            val holder = EmojiPickerPageViewHolder(activity, root)
            //
            holderList.put(page_idx, holder)
            //
            holder.onPageCreate(page)

            return root
        }

        override fun destroyItem(container: ViewGroup, page_idx: Int, obj: Any) {
            if (obj is View) {
                container.removeView(obj)
                //
                val holder = holderList.get(page_idx)
                holderList.remove(page_idx)
                holder?.onPageDestroy()
            }
        }
    }
}
