package jp.juggler.subwaytooter.emoji.model

import jp.juggler.subwaytooter.emoji.log
import java.io.File

class Emoji(
	val key:CodepointList,
	val unified:CodepointList
) {
	companion object{
		val codeCameFroms = HashSet<String>()
	}

	////////////////

	private val _shortNames = ArrayList<ShortName>()
	val shortNames: List<ShortName> get() = _shortNames
	fun addShortName(src: ShortName) {
		_shortNames.add(src)
		if( src.name.indexOf("_skin_tone") != -1) isToneVariation = true
	}

	fun removeShortName(name: String) {
		_shortNames.removeIf { it.name == name }
	}
	fun removeShortNameByCameFrom(cameFrom: String) {
		_shortNames.removeIf{ it.cameFrom == cameFrom}
	}

	////////////////
	private val _codes = ArrayList<CodepointList> ()
	val codes: List<CodepointList> get() = _codes
	fun addCode(item:CodepointList) {
		if( item==unified ) return
		_codes.add(item)
	}

	fun removeCodeByCode(code:CodepointList) =
		_codes.removeIf { it == code }

	// 重複排除したコード一覧を返す
	val codeSet: List<CodepointList>
		get() = ArrayList<CodepointList>().also{ dst->
			_codes.forEach { code->

				// twemoji svg のコードは末尾にfe0fを含む場合がある。そのまま使う
				// emojiData(google)のコードは末尾にfe0fを含む場合がある。そのまま使う
				// emojiData(twitter)のコードは末尾にfe0fを含む場合がある。そのまま使う
				// mastodonSVG (emoji-dataの派生だ) のコードは末尾にfe0fを含む場合がある。そのまま使う
				// 他のは工夫できるけど、とりあえずそのまま

				if(!code.list.isAsciiEmoji() && !dst.any{ it == code }){
					dst.add(code)
					codeCameFroms.add( code.from )
				}
			}
		}

	/////////////////////////////

	val imageFiles = ArrayList<Pair<File,String>> ()

	var resName: String =""

	var toneParent :Emoji? = null
	var isToneVariation = false

	var usedInCategory : Category? = null
	var skip = false


	init{
		_codes.add(unified)
	}
}
