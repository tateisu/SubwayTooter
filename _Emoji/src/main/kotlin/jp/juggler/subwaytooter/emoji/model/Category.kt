package jp.juggler.subwaytooter.emoji.model

import jp.juggler.subwaytooter.emoji.log

class Category(val enumId: String, val url: String?) {
	override fun equals(other: Any?) = enumId == (other as? Category)?.enumId
	override fun hashCode(): Int = enumId.hashCode()

	// ショートコード登場順序がある
	private val emojis = ArrayList<Emoji>()

	fun addEmoji(item:Emoji,allowDuplicate:Boolean =false,addingName:String?="?") {
		if(!allowDuplicate){
			val oldCategory = item.usedInCategory
			if(oldCategory!=null) {
				log.w("emoji ${item.shortNames.first()}, $addingName is already in ${oldCategory.enumId}")
				return
			}
		}
		item.usedInCategory = this
		if (!emojis.contains(item)) emojis.add(item)
	}

	fun eachEmoji(block: (Emoji) -> Unit) {
		emojis.forEach(block)
	}

	fun containsEmoji(item:Emoji) =
		emojis.contains(item)
}

val categoryNames = HashMap<String, Category>().apply {
	fun a(nameLower: String, enumId: String, url: String?) {
		put(nameLower, Category(enumId, url))
	}
	a("smileys & people", "CATEGORY_PEOPLE", "https://emojipedia.org/people/")
	a("animals & nature", "CATEGORY_NATURE", "https://emojipedia.org/nature/")
	a("food & drink", "CATEGORY_FOODS", "https://emojipedia.org/food-drink/")
	a("activities", "CATEGORY_ACTIVITY", "https://emojipedia.org/activity/")
	a("travel & places", "CATEGORY_PLACES", "https://emojipedia.org/travel-places/")
	a("objects", "CATEGORY_OBJECTS", "https://emojipedia.org/objects/")
	a("symbols", "CATEGORY_SYMBOLS", "https://emojipedia.org/symbols/")
	a("flags", "CATEGORY_FLAGS", "https://emojipedia.org/flags/")
	a("other", "CATEGORY_OTHER", null)
}

