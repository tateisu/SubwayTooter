package jp.juggler.subwaytooter.emoji.model

import jp.juggler.subwaytooter.emoji.cast
import java.io.File

class Emoji(
	val key: CodepointList,
	val unified: CodepointList
) : Comparable<Emoji> {

	companion object {
		val codeCameFroms = HashSet<String>()
		val nameCameFroms = HashSet<String>()
	}

	override fun equals(other: Any?): Boolean =
		key == other.cast<Emoji>()?.key

	override fun hashCode(): Int =
		key.hashCode()

	override fun toString(): String =
		"Emoji(${resName},${imageFiles.first().second})"

	override fun compareTo(other: Emoji): Int =
		key.compareTo(other.key)

	////////////////

	private val _shortNames = ArrayList<ShortName>()
	val shortNames: List<ShortName> get() = _shortNames
	fun addShortName(src: ShortName) {
		nameCameFroms.add(src.cameFrom)
		_shortNames.add(src)
	}

	fun removeShortName(name: String) {
		_shortNames.removeIf { it.name == name }
	}

	fun removeShortNameByCameFrom(cameFrom: String) {
		_shortNames.removeIf { it.cameFrom == cameFrom }
	}

	// 重複排除したコード一覧を返す
	val nameList: List<Triple<Int,String,List<String>>>
		get() {
			val dst = ArrayList<Triple<Int, String, List<String>>>()
			for (name in _shortNames.map { it.name }.toSet().sorted()) {
				val froms = _shortNames.filter { it.name == name }.map { it.cameFrom }.sorted()
				val priority = when {
					froms.contains("fixName") -> 0
					froms.contains("EmojiDataJson") -> 1
					froms.contains("EmojiData(skinTone)") -> 2
					froms.contains("EmojiOneJson") -> 3
					froms.contains("emojiQualified") -> 4
					else -> Int.MAX_VALUE
				}
				dst.add(Triple(priority, name, froms))
			}
			return dst.sortedBy { it.first }
		}

	////////////////
	private val _codes = ArrayList<CodepointList>()
	val codes: List<CodepointList> get() = _codes
	fun addCode(item: CodepointList) {
		if (item == unified) return
		_codes.add(item)
	}

	fun removeCodeByCode(code: CodepointList) =
		_codes.removeIf { it == code }


	// 重複排除したコード一覧を返す
	val codeSet: List<CodepointList>
		get() = ArrayList<CodepointList>().also { dst ->
			_codes.forEach { code ->

				// twemoji svg のコードは末尾にfe0fを含む場合がある。そのまま使う
				// emojiData(google)のコードは末尾にfe0fを含む場合がある。そのまま使う
				// emojiData(twitter)のコードは末尾にfe0fを含む場合がある。そのまま使う
				// mastodonSVG (emoji-dataの派生だ) のコードは末尾にfe0fを含む場合がある。そのまま使う
				// 他のは工夫できるけど、とりあえずそのまま

				if (!code.list.isAsciiEmoji() && !dst.any { it == code }) {
					dst.add(code)
					codeCameFroms.add(code.from)
				}
			}
		}

	/////////////////////////////

	val imageFiles = ArrayList<Pair<File, String>>()

	var resName: String = ""

	// set of parent.unified
	private val _toneParents = HashSet<Emoji>()
	fun addToneParent(parent: Emoji) = _toneParents.add(parent)
	val toneParents: Set<Emoji> get() = _toneParents
	val toneChildren = HashMap<CodepointList,Emoji>()

	var usedInCategory: Category? = null
	var skip = false


	init {
		_codes.add(unified)
	}
}
