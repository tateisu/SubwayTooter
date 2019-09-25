package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.graphics.drawable.PictureDrawable
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.viewpager.widget.ViewPager
import com.astuetz.PagerSlidingTabStrip
import com.bumptech.glide.Glide

import jp.juggler.emoji.EmojiMap
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.api.entity.CustomEmoji
import jp.juggler.subwaytooter.view.HeaderGridView
import jp.juggler.subwaytooter.view.MyViewPager
import jp.juggler.subwaytooter.view.NetworkEmojiView
import jp.juggler.util.*
import org.jetbrains.anko.padding
import org.jetbrains.anko.textColor
import org.json.JSONObject
import java.util.*

@SuppressLint("InflateParams")
class EmojiPicker(
	private val activity : Activity,
	private val instance : String?,
	@Suppress("CanBeParameter") private val isMisskey : Boolean,
	private val onEmojiPicked : (name : String, instance : String?, bInstanceHasCustomEmoji : Boolean) -> Unit
	// onEmojiPickedのinstance引数は通常の絵文字ならnull、カスタム絵文字なら非null、
) : View.OnClickListener, ViewPager.OnPageChangeListener {
	
	class SkinTone(val suffix_list : Array<out String>) {
		companion object {
			fun create(vararg suffix_list : String) : SkinTone {
				return SkinTone(suffix_list)
			}
		}
	}
	
	internal class EmojiItem(val name : String, val instance : String?)
	
	internal class CustomCategory(
		val rangeStart : Int,
		val rangeLength : Int,
		val view : View
	)
	
	companion object {
		
		internal val log = LogCategory("EmojiPicker")
		
		const val CATEGORY_RECENT = - 2
		const val CATEGORY_CUSTOM = - 1
		
		internal val tone_list = arrayOf(
			SkinTone.create("_light_skin_tone", "_tone1"),
			SkinTone.create("_medium_light_skin_tone", "_tone2"),
			SkinTone.create("_medium_skin_tone", "_tone3"),
			SkinTone.create("_medium_dark_skin_tone", "_tone4"),
			SkinTone.create("_dark_skin_tone", "_tone5")
		)
	}
	
	private val viewRoot : View
	
	private val pager_adapter : EmojiPickerPagerAdapter
	
	private val page_list = ArrayList<EmojiPickerPage>()
	
	private val pager : MyViewPager
	
	private val dialog : Dialog
	
	private val pager_strip : PagerSlidingTabStrip
	
	private val ibSkinTone : Array<ImageButton>
	
	private var selected_tone : Int = 0
	
	private val recent_list = ArrayList<EmojiItem>()
	
	private var custom_list = ArrayList<EmojiItem>()
	private var custom_categories = ArrayList<CustomCategory>()
	
	private val emoji_url_map = HashMap<String, String>()
	
	private val recent_page_idx : Int
	
	private val custom_page_idx : Int
	
	init {
		
		// recentをロードする
		val pref = App1.pref
		val sv = Pref.spEmojiPickerRecent(pref)
		if(sv.isNotEmpty()) {
			try {
				val array = sv.toJsonArray()
				for(i in 0 until array.length()) {
					val item = array.optJSONObject(i)
					val name = item.parseString("name")
					if(name?.isNotEmpty() == true) {
						val instance = item.parseString("instance")
						recent_list.add(EmojiItem(name, instance))
					}
				}
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			
		}
		
		// create page
		this.recent_page_idx = page_list.size
		page_list.add(EmojiPickerPage(false, CATEGORY_RECENT, R.string.emoji_category_recent))
		
		
		this.custom_page_idx = page_list.size
		page_list.add(EmojiPickerPage(false, CATEGORY_CUSTOM, R.string.emoji_category_custom))
		
		page_list.add(
			EmojiPickerPage(
				true,
				EmojiMap.CATEGORY_PEOPLE,
				R.string.emoji_category_people
			)
		)
		page_list.add(
			EmojiPickerPage(
				true,
				EmojiMap.CATEGORY_NATURE,
				R.string.emoji_category_nature
			)
		)
		page_list.add(
			EmojiPickerPage(
				true,
				EmojiMap.CATEGORY_FOODS,
				R.string.emoji_category_foods
			)
		)
		page_list.add(
			EmojiPickerPage(
				true,
				EmojiMap.CATEGORY_ACTIVITY,
				R.string.emoji_category_activity
			)
		)
		page_list.add(
			EmojiPickerPage(
				true,
				EmojiMap.CATEGORY_PLACES,
				R.string.emoji_category_places
			)
		)
		page_list.add(
			EmojiPickerPage(
				true,
				EmojiMap.CATEGORY_OBJECTS,
				R.string.emoji_category_objects
			)
		)
		page_list.add(
			EmojiPickerPage(
				true,
				EmojiMap.CATEGORY_SYMBOLS,
				R.string.emoji_category_symbols
			)
		)
		page_list.add(
			EmojiPickerPage(
				true,
				EmojiMap.CATEGORY_FLAGS,
				R.string.emoji_category_flags
			)
		)
		
		this.viewRoot = activity.layoutInflater.inflate(R.layout.dlg_picker_emoji, null, false)
		this.pager = viewRoot.findViewById(R.id.pager)
		this.pager_strip = viewRoot.findViewById(R.id.pager_strip)
		
		this.ibSkinTone = arrayOf(
			initSkinTone(0, viewRoot.findViewById(R.id.btnSkinTone1)),
			initSkinTone(1, viewRoot.findViewById(R.id.btnSkinTone2)),
			initSkinTone(2, viewRoot.findViewById(R.id.btnSkinTone3)),
			initSkinTone(3, viewRoot.findViewById(R.id.btnSkinTone4)),
			initSkinTone(4, viewRoot.findViewById(R.id.btnSkinTone5))
		)
		showSkinTone()
		
		this.pager_adapter = EmojiPickerPagerAdapter()
		pager.adapter = pager_adapter
		pager_strip.setViewPager(pager)
		
		pager.addOnPageChangeListener(this)
		onPageSelected(0)
		
		// カスタム絵文字をロードする
		if(instance != null && instance.isNotEmpty()) {
			setCustomEmojiList(
				App1.custom_emoji_lister.getList(instance, isMisskey = isMisskey) {
					setCustomEmojiList(it) // ロード完了時に呼ばれる
				}
			)
		}
		
		this.dialog = Dialog(activity)
		dialog.setContentView(viewRoot)
		dialog.setCancelable(true)
		dialog.setCanceledOnTouchOutside(true)
		val w = dialog.window
		w?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
	}
	
	private var bInstanceHasCustomEmoji = false
	
	private fun setCustomEmojiList(list : ArrayList<CustomEmoji>?) {
		if(list == null) return
		bInstanceHasCustomEmoji = true
		
		// make categories
		val newList = TreeMap<String, ArrayList<EmojiItem>>()
		for(emoji in list) {
			if(! emoji.visible_in_picker) continue
			val category = emoji.category ?: ""
			var subList = newList[category]
			if(subList == null) {
				subList = ArrayList()
				newList[category] = subList
			}
			subList.add(EmojiItem(emoji.shortcode, instance))
			emoji_url_map[emoji.shortcode] = emoji.url
		}
		// compose categories data list
		val entries = newList.entries
		custom_list.clear()
		custom_categories.clear()
		custom_list.ensureCapacity(entries.sumBy { it.value.size })
		custom_categories.ensureCapacity(entries.size)
		entries.forEach {
			val rangeStart = custom_list.size
			custom_list.addAll(it.value)
			val rangeLength = custom_list.size - rangeStart
			
			custom_categories.add(CustomCategory(
				rangeStart,
				rangeLength,
				TextView(activity).apply {
					layoutParams = FrameLayout.LayoutParams(
						FrameLayout.LayoutParams.MATCH_PARENT,
						FrameLayout.LayoutParams.WRAP_CONTENT
					)
					text = when(val name = it.key) {
						"" -> this@EmojiPicker.activity.getString(R.string.custom_emoji)
						else -> name
					}
					textColor =
						getAttributeColor(this@EmojiPicker.activity, R.attr.colorContentText)
					textSize = 16f // SP単位
					padding = (resources.displayMetrics.density * 2f + 0.5f).toInt()
				}
			))
		}
		
		pager_adapter.getPageViewHolder(custom_page_idx)?.reloadCustomEmoji()
		pager_adapter.getPageViewHolder(recent_page_idx)?.notifyDataSetChanged()
	}
	
	internal fun show() {
		dialog.show()
	}
	
	override fun onPageScrollStateChanged(state : Int) {
	}
	
	override fun onPageScrolled(
		position : Int,
		positionOffset : Float,
		positionOffsetPixels : Int
	) {
	}
	
	override fun onPageSelected(position : Int) {
		try {
			val hasSkinTone = page_list[position].hasSkinTone
			val visibility = if(hasSkinTone) View.VISIBLE else View.INVISIBLE
			ibSkinTone.forEach { it.visibility = visibility }
		} catch(ex : Throwable) {
			log.trace(ex)
		}
	}
	
	private fun applySkinTone(nameArg : String) : String {
		if(selected_tone == 0) return nameArg
		
		var name = nameArg
		
		// Recentなどでは既にsuffixがついた名前が用意されている
		// suffixを除去する
		for(tone in tone_list) {
			for(suffix in tone.suffix_list) {
				if(name.endsWith(suffix)) {
					name = name.substring(0, name.length - suffix.length)
					break
				}
			}
		}
		
		// 指定したトーンのサフィックスを追加して、絵文字が存在すればその名前にする
		val tone = viewRoot.findViewById<View>(selected_tone).tag as SkinTone
		for(suffix in tone.suffix_list) {
			val new_name = name + suffix
			val info = EmojiMap.sShortNameToEmojiInfo[new_name]
			if(info != null) return new_name
		}
		return name
	}
	
	private fun initSkinTone(idx : Int, ib : ImageButton) : ImageButton {
		ib.tag = tone_list[idx]
		ib.setOnClickListener(this)
		return ib
	}
	
	private fun showSkinTone() {
		for(button in ibSkinTone) {
			if(selected_tone == button.id) {
				button.setImageResource(R.drawable.emj_2714_fe0f)
			} else {
				button.setImageDrawable(null)
			}
		}
	}
	
	override fun onClick(view : View) {
		val id = view.id
		selected_tone = if(selected_tone == id) 0 else id
		showSkinTone()
		pager_adapter.eachViewHolder { _, vh -> vh.reloadSkinTone() }
	}
	
	internal inner class EmojiPickerPage(
		val hasSkinTone : Boolean,
		val category_id : Int,
		title_id : Int
	) {
		
		val title : String = activity.getString(title_id)
		
		val emoji_list = when(category_id) {
			
			CATEGORY_CUSTOM -> custom_list
			
			CATEGORY_RECENT -> ArrayList<EmojiItem>().apply {
				for(item in recent_list) {
					if(item.instance != null && item.instance != instance) continue
					add(item)
				}
			}
			
			else -> ArrayList<EmojiItem>().apply {
				EmojiMap.sCategoryMap.get(category_id)?.emoji_list?.forEach { name ->
					add(EmojiItem(name, null))
				}
			}
		}
	}
	
	inner class EmojiPickerPageViewHolder(activity : Activity, root : View) : BaseAdapter(),
		AdapterView.OnItemClickListener {
		
		private val gridView : HeaderGridView = root.findViewById(R.id.gridView)
		private val wh = (0.5f + 48f * activity.resources.displayMetrics.density).toInt()
		
		private var page : EmojiPickerPage? = null
		
		internal fun onPageCreate(page : EmojiPickerPage) {
			this.page = page
			if(page.category_id != CATEGORY_CUSTOM) {
				gridView.adapter = this
			} else {
				reloadCustomEmoji()
			}
			gridView.onItemClickListener = this
		}
		
		internal fun onPageDestroy() {
		}
		
		internal fun reloadSkinTone() {
			val page = this.page ?: throw RuntimeException("page is not assigned")
			if(page.category_id != CATEGORY_CUSTOM) {
				this.notifyDataSetChanged()
			}
		}
		
		fun reloadCustomEmoji() {
			gridView.reset()
			if(custom_categories.size >= 2) {
				for(item in custom_categories) {
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
		
		override fun getCount() : Int {
			return page?.emoji_list?.size ?: 0
		}
		
		override fun getItem(i : Int) : Any? {
			return page?.emoji_list?.get(i)
		}
		
		override fun getItemId(i : Int) : Long {
			return 0
		}
		
		override fun getViewTypeCount() : Int {
			return 2
		}
		
		override fun getItemViewType(position : Int) : Int {
			return if(page?.emoji_list?.get(position)?.instance != null) 1 else 0
		}
		
		override fun getView(position : Int, viewOld : View?, viewGroup : ViewGroup) : View {
			val page = this.page ?: throw RuntimeException("page is not assigned")
			val view : View
			val item = page.emoji_list[position]
			if(item.instance != null) {
				view = viewOld ?: NetworkEmojiView(activity).apply {
					layoutParams = AbsListView.LayoutParams(wh, wh)
				}
				view.setTag(R.id.btnAbout, item)
				(view as? NetworkEmojiView)?.setEmoji(emoji_url_map[item.name])
			} else {
				if(viewOld == null) {
					view = ImageView(activity)
					val lp = AbsListView.LayoutParams(wh, wh)
					view.layoutParams = lp
				} else {
					view = viewOld
				}
				view.setTag(R.id.btnAbout, item)
				if(view is ImageView) {
					val name = if(page.hasSkinTone) {
						applySkinTone(item.name)
					} else {
						item.name
					}
					
					val info = EmojiMap.sShortNameToEmojiInfo[name]
					if(info != null) {
						val er = info.er
						if(er.isSvg) {
							Glide.with(activity)
								.`as`(PictureDrawable::class.java)
								
								.load("file:///android_asset/${er.assetsName}")
								.into(view)
						} else {
							Glide.with(activity)
								.load(er.drawableId)
								.into(view)
						}
					}
				}
				
			}
			
			return view
		}
		
		override fun onItemClick(
			adapterView : AdapterView<*>,
			view : View,
			idxArg : Int,
			l : Long
		) {
			val page = this.page ?: return
			
			val idx = gridView.findListItemIndex(idxArg)
			
			if(idx in 0 until page.emoji_list.size) {
				val item = page.emoji_list[idx]
				var name = item.name
				if(item.instance != null && item.instance.isNotEmpty()) {
					// カスタム絵文字
					selected(name, item.instance)
				} else {
					// 普通の絵文字
					EmojiMap.sShortNameToEmojiInfo[name] ?: return
					
					if(page.hasSkinTone) {
						val sv = applySkinTone(name)
						if(EmojiMap.sShortNameToEmojiInfo[sv] != null) {
							name = sv
						}
					}
					
					selected(name, null)
				}
			}
		}
	}
	
	// name はスキントーン適用済みであること
	internal fun selected(name : String, instance : String?) {
		
		dialog.dismissSafe()
		
		val pref = App1.pref
		
		// Recentをロード(他インスタンスの絵文字を含む)
		val list : ArrayList<JSONObject> = try {
			Pref.spEmojiPickerRecent(pref).toJsonArray().toObjectList()
		} catch(ignored : Throwable) {
			ArrayList()
		}
		
		// 選択された絵文字と同じ項目を除去
		// 項目が増えすぎたら減らす
		run {
			var nCount = 0
			val it = list.iterator()
			while(it.hasNext()) {
				val item = it.next()
				if(name == item.parseString("name")
					&& instance == item.parseString("instance")
				) {
					it.remove()
				} else if(++ nCount >= 256) {
					it.remove()
				}
			}
		}
		
		// 先頭に項目を追加
		list.add(0, JSONObject().apply {
			put("name", name)
			if(instance != null) put("instance", instance)
		})
		
		// 保存する
		try {
			val sv = list.toJsonArray().toString()
			App1.pref.edit().put(Pref.spEmojiPickerRecent, sv).apply()
		} catch(ignored : Throwable) {
		
		}
		
		onEmojiPicked(name, instance, bInstanceHasCustomEmoji)
	}
	
	internal inner class EmojiPickerPagerAdapter : androidx.viewpager.widget.PagerAdapter() {
		
		private val inflater : LayoutInflater
		private val holder_list = SparseArray<EmojiPickerPageViewHolder>()
		
		init {
			this.inflater = activity.layoutInflater
		}
		
		override fun getCount() : Int {
			return page_list.size
		}
		
		private fun Int.validPage() = this >= 0 && this < page_list.size
		
		private fun getPage(idx : Int) : EmojiPickerPage? {
			return if(idx.validPage()) page_list[idx] else null
		}
		
		fun getPageViewHolder(idx : Int) : EmojiPickerPageViewHolder? {
			return if(idx.validPage()) holder_list.get(idx) else null
		}
		
		inline fun eachViewHolder(block : (Int, EmojiPickerPageViewHolder) -> Unit) {
			for(i in 0 until page_list.size) {
				val vh = holder_list.get(i) ?: continue
				block(i, vh)
			}
		}
		
		override fun getPageTitle(page_idx : Int) : CharSequence? {
			return getPage(page_idx)?.title
		}
		
		override fun isViewFromObject(view : View, obj : Any) : Boolean {
			return view === obj
		}
		
		override fun instantiateItem(container : ViewGroup, page_idx : Int) : Any {
			val root = inflater.inflate(R.layout.page_emoji_picker, container, false)
			container.addView(root, 0)
			
			val page = page_list[page_idx]
			val holder = EmojiPickerPageViewHolder(activity, root)
			//
			holder_list.put(page_idx, holder)
			//
			holder.onPageCreate(page)
			
			return root
		}
		
		override fun destroyItem(container : ViewGroup, page_idx : Int, obj : Any) {
			if(obj is View) {
				container.removeView(obj)
				//
				val holder = holder_list.get(page_idx)
				holder_list.remove(page_idx)
				holder?.onPageDestroy()
			}
		}
	}
	
}

