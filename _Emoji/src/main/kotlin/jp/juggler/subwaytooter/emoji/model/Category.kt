package jp.juggler.subwaytooter.emoji.model

import jp.juggler.subwaytooter.emoji.log

class Category(val name: String, val url: String?) {
	override fun equals(other: Any?) = name == (other as? Category)?.name
	override fun hashCode(): Int = name.hashCode()

	// ショートコード登場順序がある
	private val _emojis = ArrayList<Emoji>()
	val emojis:List<Emoji> get()=_emojis

	fun addEmoji(item: Emoji, allowDuplicate: Boolean = false, addingName: String? = "?") {
		if (!allowDuplicate) {
			val oldCategory = item.usedInCategory
			if (oldCategory != null) {
				log.w("emoji ${item.shortNames.first()}, $addingName is already in category ${oldCategory.name}")
				return
			}
		}
		item.usedInCategory = this
		if (!_emojis.contains(item)) _emojis.add(item)
	}
}

val categoryNames = arrayOf(
	Category("People", "https://emojipedia.org/people/"),
	Category("ComplexTones", null),
	Category("Nature", "https://emojipedia.org/nature/"),
	Category("Foods", "https://emojipedia.org/food-drink/"),
	Category("Activities", "https://emojipedia.org/activity/"),
	Category("Places", "https://emojipedia.org/travel-places/"),
	Category("Objects", "https://emojipedia.org/objects/"),
	Category("Symbols", "https://emojipedia.org/symbols/"),
	Category("Flags", "https://emojipedia.org/flags/"),
	Category("Others", null),
)

