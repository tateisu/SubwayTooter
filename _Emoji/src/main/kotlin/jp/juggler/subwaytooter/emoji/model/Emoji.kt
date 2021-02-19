package jp.juggler.subwaytooter.emoji.model

import java.io.File

class Emoji(val key:CodepointList) {
	companion object{
		val nameCameFroms = HashSet<String>()
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
	val _codes = ArrayList<Pair<CodepointList,String>> ()
	val codes: List<Pair<CodepointList,String>> get() = _codes
	fun addCode(code:CodepointList,cameFrom:String) {
		if (!_codes.any{ it.first == code}) _codes.add(Pair(code,cameFrom))
	}
	fun removeCode(code:CodepointList) {
		_codes.removeIf { it.first == code }
	}

	val imageFiles = ArrayList<Pair<File,String>> ()

	var resName: String =""

	var toneParent :Emoji? = null
	var isToneVariation = false

	var usedInCategory : Category? = null

	fun updateCodes() {
		val oldCodes = ArrayList<Pair<CodepointList,String>>().also{ it.addAll(_codes)}
		_codes.clear()
		fun add(code: CodepointList, cameFrom: String):Boolean{
			return if( !_codes.any{ it.first == code}){
				_codes.add(Pair(code,cameFrom))
				true
			}else {
				false
			}
		}
		// 長い順に
		oldCodes.sortedByDescending { it.first.list.size }.forEach { pair->
			when(pair.second){
				// twemoji svg のコードは末尾にfe0fを含む場合がある。そのまま使う
				// emojiData(google)のコードは末尾にfe0fを含む場合がある。そのまま使う
				// emojiData(twitter)のコードは末尾にfe0fを含む場合がある。そのまま使う
				// mastodonSVG (emoji-dataの派生だ) のコードは末尾にfe0fを含む場合がある。そのまま使う
				//
				"twemojiSvg","emojiDataGo","emojiDataTw","mastodonSVG",
				"EmojiDataJson(docomo)",
				"EmojiDataJson(au)",
				"EmojiDataJson(google)",
				"EmojiDataJson(softbank)" ->{
					add(pair.first, pair.second)
				}

				// 他のは工夫できるけど、とりあえずそのまま
				else->{
					add(pair.first, pair.second)
				}
			}
		}

		nameCameFroms.addAll( codes.map{it.second} )
	}
}
