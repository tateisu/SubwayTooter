package jp.juggler.subwaytooter.emoji

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.http.*
import jp.juggler.subwaytooter.emoji.model.*
import kotlinx.coroutines.runBlocking
import org.apache.commons.text.StringEscapeUtils
import org.intellij.lang.annotations.Language
import java.io.*


//pngフォルダにある画像ファイルを参照する
//emoji-data/emoji.json を参照する
//
//以下のjavaコードを生成する
//- UTF-16文字列 => 画像リソースID のマップ。同一のIDに複数のUTF-16文字列が振られることがある。
//- shortcode => 画像リソースID のマップ。同一のIDに複数のshortcodeが振られることがある。
//- shortcode中の区切り文字はハイフンもアンダーバーもありうる。出力データではアンダーバーに寄せる
//- アプリはshortcodeの探索時にキー文字列の区切り文字をアンダーバーに正規化すること

const val pathCwebp = "C:/cygwin64/bin/cwebp.exe"

val emojiDataCodepointsVendors = arrayOf("docomo", "au", "softbank", "google")

fun copyFile(dst: File, src: File) {
	try {
		FileInputStream(src).use { streamIn ->
			FileOutputStream(dst).use { streamOut ->
				streamOut.write(streamIn.readAllBytes())
			}
		}
	} catch (ex: Throwable) {
		dst.delete()
		throw IOException("copyFile failed. src=$src dst=$dst", ex)
	}
}

//fun svgToVectorDrawable(dst: File, src: File) {
//
//	val tmp = ByteArrayOutputStream()
//
//	// Write all the error message during parsing into SvgTree. and return here as getErrorLog().
//	// We will also log the exceptions here.
//	try {
//		val svgTree = Svg2Vector.parse(src)
//		svgTree.mScaleFactor = 24 / max(svgTree.w, svgTree.h)
//		if (svgTree.canConvertToVectorDrawable()) {
//			Svg2Vector.writeFile(tmp, svgTree)
//		}
//		val errorLog = svgTree.errorLog
//		if (errorLog.isNotEmpty()) println("$src $errorLog")
//		FileOutputStream(dst).use { outStream ->
//			outStream.write(tmp.toByteArray())
//		}
//	} catch (e: Exception) {
//		println("svgToVectorDrawable: ${e.message} ${src.canonicalPath}")
//	}
//}


class App {
	companion object {
		const val cameFromCategory = "CategoryHtml"

		val reComment = """#.*""".toRegex()

		val ignoreShortName = arrayOf(
			"flag_for_texas_ustx"
		)

		private val ignoreImagePath = arrayOf(
			"LICENSE",
			// fe82b (フリーダイアル) はnoto-emoji では ?旗 になっていて使えない
			"noto-emoji/png/128/emoji_ufe82b.png",
			"noto-emoji/svg/emoji_ufe82b.svg",
			// mastodonのフォルダにある余計なファイル
			"mastodon/public/emoji/sheet_10.png"
		)

	}


	// 修正用データを読む
	private val fixCode = HashMap<CodepointList, String>()
	private val fixName = HashMap<ShortName, String>()
	private val fixCategory = ArrayList<Pair<String, String>>()
	private val fixUnified = HashMap<CodepointList, CodepointList>()

	private fun readFixData() {
		val fixFile = "./fix_code.txt"
		File(fixFile).forEachLine { lno, rawLine ->
			val line = rawLine
				.replace(reComment, "")
				.trim()

			val mr = """\A(\w+)\s*([\w._-]+)\s*(.*)""".toRegex().find(line)
			if (mr != null) {
				val type = mr.groupValues[1]
				val arg1 = mr.groupValues[2]

				if (type == "unified") {
					val code = arg1.toCodepointList("fixUnified")!!
					fixUnified[code.toKey("fixUnified")] = code
					return@forEachLine
				}

				val data = """([\w+-]+)""".toRegex().findAll(mr.groupValues[3]).map { it.groupValues[1] }.toList()
				if (data.size != 1) return@forEachLine
				when (type) {
					"code" -> fixCode[arg1.toCodepointList("fixCode")!!] = data.first()
					"name" -> fixName[arg1.toShortName("fixName")!!] = data.first()
					"category" -> Pair(arg1, data.first()).addTo(fixCategory)
					else -> error("$fixFile $lno : bad fix_data type=$type")
				}
			}
		}
	}

	val emojipediaShortNames = HashMap<CodepointList,ShortName>()

	// noto-emoji のファイル名はfeofが欠けているので、
	// あらかじめemoji 13.1 の qualified name を取得しておく
	@Suppress("FunctionName")
	private suspend fun readQualified13_1(client: HttpClient) {
		val cameFrom = "emojiQualified"
		for( url in arrayOf(
			"https://emojipedia.org/emoji-13.1/",
			"https://emojipedia.org/emoji-13.0/",
		)) {
			val root = client.cachedGetString(url, mapOf()).parseHtml(url)
			for( node in root.getElementsByClass("sidebar") ){
				node.remove()
			}
			for( node in root.getElementsByClass("categories") ){
				node.remove()
			}
			for( list in root.getElementsByTag("ul")){
				for( li in list.getElementsByTag("li")) {

					val span = li.getElementsByTag("span").find { it.hasClass("emoji") }
						?: continue
					val code = span.text().listCodePoints().toCodepointList(cameFrom)!!
					val key = code.toKey(cameFrom)

					fixUnified[ key] = code

					val href = li.getElementsByTag("a")!!.attr("href")
						.notEmpty()?:continue
					val shortName = href
						.replace("/", "")
						.toShortName(cameFrom)
						?: error("can't parse $href")

					if( !ignoreShortName.any{ it == shortName.name}){
						emojipediaShortNames[key] = shortName
					}
				}
			}
		}
	}

	private fun addEmojipediaShortnames(){
		for((key,shortName) in emojipediaShortNames){
			emojiMap[key]?.addShortName(shortName)
		}
	}

	// 画像ファイルをスキャンして絵文字コードとファイルの対応表を作る
	// マップのキーはvariation selectorとZWJが除去される
	private val emojiMap = HashMap<CodepointList, Emoji>()


	private fun scanEmojiImages() {
		fun HashMap<CodepointList, Emoji>.scanImageDir(
			cameFrom: String,
			dirPath: String,
			@Language("RegExp") codeSpec: String,
			unifiedQualifier: (CodepointList) -> CodepointList = { it }
		) {
			val dir = File(dirPath)
			val reCodeSpec = codeSpec.toRegex()
			var countFound = 0
			var countCreate = 0
			dir.listFiles()!!.forEach { imageFile ->
				if (!imageFile.isFile) return@forEach
				val unixPath = imageFile.path.replace("\\", "/")
				if (ignoreImagePath.any { unixPath.indexOf(it) != -1 }) return@forEach

				var name = imageFile.name
				if (name == "LICENSE") return@forEach
				name = name.replace("_border", "")

				val code = reCodeSpec.find(name)
					?.groupValues
					?.elementAtOrNull(1)
					?.toCodepointList(cameFrom)
					?: error("can't parse $name")

				// variation selector やZWJを除去したコードをキーにする
				val key = code.toKey(cameFrom)
				var emoji = get(key)
				if (emoji == null) {
					val unified2 = fixUnified[key]
					emoji = Emoji(key, unified2 ?: unifiedQualifier(code))
					put(key, emoji)
					++countCreate
				}
				emoji.imageFiles.add(Pair(imageFile, cameFrom))
				emoji.addCode(code)
				++countFound
			}
			log.d("scanImageDir: found=$countFound,create=$countCreate, dir=$dir")
		}

		emojiMap.scanImageDir("override", "override", """([0-9A-Fa-f_-]+)\.""")
		emojiMap.scanImageDir("mastodonSVG", "mastodon/public/emoji", """([0-9A-Fa-f_-]+)\.""")
		emojiMap.scanImageDir("twemojiSvg", "twemoji/assets/svg/", """([0-9A-Fa-f_-]+)\.""")
		emojiMap.scanImageDir("notoSvg", "noto-emoji/svg", """emoji_u([0-9A-Fa-f_-]+)\.""") { code ->
			if (code.list.last() != 0xfe0f)
				"${code.toHex()}-fe0f".toCodepointList("notoSvgFix")!!
			else
				code
		}
		emojiMap.scanImageDir("notoPng", "noto-emoji/png/72", """emoji_u([0-9A-Fa-f_-]+)\.""")
		emojiMap.scanImageDir("emojiDataTw", "emoji-data/img-twitter-72", """([0-9A-Fa-f_-]+)\.""")
		emojiMap.scanImageDir("emojiDataGo", "emoji-data/img-google-136", """([0-9A-Fa-f_-]+)\.""")
		emojiMap.scanImageDir("emojiOne", "emojione/assets/svg", """([0-9A-Fa-f_-]+)\.""")

	}

//	// resName => Resource
//	private val resNameMap = HashMap<ResName, Resource>()
//
//	// map: code => resName => Resource
//	private val codeMap = HashMap<CodepointList, HashMap<ResName, Resource>>()
//
//	// map shortname => resName => Resource
//	private val nameMap = HashMap<ShortName, HashMap<ResName, Resource>>()

	private fun copyImages() {
		var countSvg = 0
		var countPng = 0
		for (emoji in emojiMap.values) {
			val strResName = emoji.key.toResourceId()
			emoji.resName = strResName
			val (src, _) = emoji.imageFiles.first()
			if (src.name.endsWith("svg")) {
				++countSvg
				val dst = File("assets/$strResName.svg")
				if (!dst.exists()) {
					//svgToVectorDrawable(dst, src)
					copyFile(dst, src)
				}
			} else {
				++countPng
				val dst = File("drawable-nodpi/$strResName.webp")
				if (!dst.exists()) {
					val pb = ProcessBuilder(pathCwebp, src.path, "-quiet", "-o", dst.path)
					val rv = pb.start().waitFor()
					if (rv != 0) error("cwebp failed. dst=$dst src=$src")
				}
			}
		}
		log.d("copyImage: countSvg=$countSvg, countPng=$countPng")
	}

//	private fun updateCodeMap() {
//		codeMap.clear()
//		resNameMap.values.forEach { res_info ->
//			res_info.codePoints.forEach { cp ->
//				codeMap.prepare(cp) { HashMap() }[res_info.res_name] = res_info
//				codeMap.prepare(cp.removeZWJ()) { HashMap() }[res_info.res_name] = res_info
//			}
//		}
//	}
//
//	private fun updateNameMap() {
//		nameMap.clear()
//		resNameMap.values.forEach { res_info ->
//			res_info.shortNames.forEach { name ->
//				nameMap.prepare(name) { HashMap() }[res_info.res_name] = res_info
//			}
//		}
//	}

	private fun readEmojiData() {
		File("./emoji-data/emoji.json")
			.readAllBytes()
			.decodeUtf8()
			.decodeJsonArray()
			.objectList()
			.forEach { src ->
				// 絵文字のコードポイント一覧
				var unified = src.string("unified")?.toCodepointList("EmojiDataJsonUnified")!!
				var key = unified.toKey("EmojiDataJsonUnifiedKey")
				var emoji = emojiMap[key] ?: error("can't find emoji for $key")

				if (emoji.unified != unified) {
					log.d("readEmojiData: unified not match. emoji=${emoji.unified}, emojiData=${unified}")
					emoji.addCode(unified)
				}

				src.stringArrayList("variations")
					?.mapNotNull { it.toCodepointList("EmojiDataJsonVariation") }
					?.forEach { emoji.addCode(it) }

				for (k in emojiDataCodepointsVendors) {
					src.string(k)?.toCodepointList("EmojiDataJson($k)")
						?.let { emoji.addCode(it) }
				}

				// short_name のリスト
				val shortNames = HashSet<String>().also { dst ->
					src.string("short_name")?.addTo(dst)
					src.stringArrayList("short_names")?.forEach {
						it.addTo(dst)
					}
				}.mapNotNull { it.toShortName("EmojiDataJson") }

				if (shortNames.isEmpty())
					error("emojiData ${src.string("unified")} has no shortName")
				shortNames.forEach { emoji.addShortName(it) }

				val parentEmoji = emoji

				// スキントーン
				src.jsonObject("skin_variations")?.let { skinVariations ->
					val parentName = shortNames.first()
					val skinToneUsed = HashSet<Int>()
					for ((k, data) in skinVariations.entries) {
						if (data !is JsonObject) continue

						fun handleCode(list: IntArray, idx: Int, parentSuffix: String, suffixIndex: Int) {
							val code = list.elementAtOrNull(idx) ?: return
							val modifier = skinToneModifiers[code]
								?: error("missing skinToneModifier u${list[idx].toString(16)} for $parentName")
							skinToneUsed.add(code)
							val lastSuffix = modifier.suffixList[suffixIndex]
							val suffix =
								if (!parentSuffix.contains(lastSuffix))
									parentSuffix + lastSuffix
								else
									parentSuffix
							if (idx <= list.size - 1) {
								handleCode(list, idx + 1, suffix, suffixIndex)
							} else {
								unified = data.string("unified")!!.toCodepointList("EmojiData(skinTone)")!!
								key = unified.toKey("EmojiData(skinTone)")
								emoji = emojiMap[key] ?: error("can't find emoji for $key")

								emoji.addCode(unified)
								shortNames
									.mapNotNull { (it.name + suffix).toShortName("EmojiData(skinTone)") }
									.forEach { emoji.addShortName(it) }

								emoji.toneParent = parentEmoji
								emoji.isToneVariation = true
							}
						}

						val codeList = k.toCodepointList("toneSpec")!!.list
						for (suffixIndex in skinToneModifiers.values.first().suffixList.indices) {
							handleCode(codeList, 0, "", suffixIndex)
						}
					}
					if (skinToneUsed.size != skinToneModifiers.size) {
						log.w("skin tone code not fully used: $parentName")
					}
				}
			}
	}

	private fun readEmojione() {
		val cameFrom = "EmojiOneJson"
		val root = File("./old-emojione.json")
			.readAllBytes()
			.decodeUtf8()
			.decodeJsonObject()
		for ((strCode, item) in root.entries) {
			if (item !is JsonObject) continue

			// コードを確認する
			val code = strCode.toCodepointList(cameFrom)
				?: error("can't parse $strCode")

			val key = code.toKey(cameFrom)
			val emoji = emojiMap[key] ?: error("missing emoji for $key")

			val names = ArrayList<String>()
			item.string("alpha code")?.let { names.add(it) }
			item.string("aliases")?.split("|")?.let { names.addAll(it) }
			val shortNames = names.mapNotNull { it.toShortName(cameFrom) }
			if (shortNames.isEmpty()) error("readEmojione: missing name for code $strCode")
			shortNames.forEach { emoji.addShortName(it) }
		}
	}

	private suspend fun readEmojiSpec(client: HttpClient) {
		// 絵文字のショートネームを外部から拾ってくる
		for (url in arrayOf(
			"https://unicode.org/Public/emoji/13.1/emoji-sequences.txt",
			"https://unicode.org/Public/emoji/13.1/emoji-zwj-sequences.txt"
		)) {
			client.cachedGetString(url, mapOf())
				.split("""[\x0d\x0a]""".toRegex())
				.forEach { rawLine ->
					val line = rawLine.replace(reComment, "").trim()
					if (line.isEmpty()) return@forEach
					val cols = line.split(";", limit = 3).map { it.trim() }
					if (cols.size != 3) return@forEach

					val (strCode, _, descriptionSpec) = cols
					if (strCode.indexOf("..") != -1) return@forEach

					val code = strCode.toCodepointList("EmojiSpec")!!

					val key = code.toKey("EmojiSpec")
					val emoji = emojiMap[key] ?: error("can't find emoji for $key")

					val strShortName = descriptionSpec.toLowerCase()
						.replace("medium-light skin tone", "medium_light_skin_tone")
						.replace("medium skin tone", "medium_skin_tone")
						.replace("medium-dark skin tone", "medium_dark_skin_tone")
						.replace("light skin tone", "light_skin_tone")
						.replace("dark skin tone", "dark_skin_tone")
						.replace("""[^\w\d]+""".toRegex(), "_")

					val shortName = strShortName.toShortName("EmojiSpec")
						?: error("can't parse $strShortName")

					emoji.addShortName(shortName)
				}
		}
	}


	// カテゴリ別
	// 絵文字のショートネームを外部から拾ってくる
	private suspend fun readCategoryShortName(client: HttpClient) {
		categoryNames.values.forEach { category ->
			if (category.url == null) return@forEach
			val root = client.cachedGetString(category.url, mapOf()).parseHtml(category.url)
			val list = root.getElementsByClass("emoji-list").first()
			list.getElementsByTag("li").forEach liLoop@{ node ->
				val shortName = node.getElementsByTag("a")!!.attr("href")
					.replace("/", "")
					.toShortName(cameFromCategory)
					?: error("can't parse ${node.getElementsByTag("a")!!.attr("href")}")

				if (ignoreShortName.any { shortName.name == it }) return@liLoop

				val text = node.getElementsByClass("emoji").text()
				val code = text.listCodePoints()
					.takeIf { it.isNotEmpty() }?.toCodepointList("CategoryHtml")
					?: error("can't parse code from $text")

				val key = code.toKey("CategoryHtml")
				val emoji = emojiMap[key]
					?: error("can't find emoji for ${category.url} $shortName $key $text")
				category.addEmoji(emoji, allowDuplicate = true, addingName = shortName.toString())
			}
		}
	}

	private fun fixCategory() {
		val nameMap = HashMap<ShortName, Emoji>().apply {
			for (emoji in emojiMap.values)
				for (shortName in emoji.shortNames)
					this[shortName] = emoji
		}
		for ((enumId, strShortName) in fixCategory) {
			val category = categoryNames.values.find { it.enumId == enumId }
				?: error("fixCategory: missing category for $enumId")
			val shortName = strShortName.toShortName("fixCategory")
				?: error("fixCategory: can't parse $strShortName")
			val emoji = nameMap[shortName]
				?: error("fixCategory: missing emoji for $strShortName")
			category.addEmoji(emoji, addingName = shortName.toString())
		}
	}

	private fun String.unescapeXml() = StringEscapeUtils.unescapeXml(this)

	private val vendorText = HashMap<CodepointList, ArrayList<String>>()
	private val vendorUnicodeMap = HashMap<CodepointList, Pair<CodepointList, String>>()

	private fun readVendorCode() {
		var error = false
		// まとまったxmlを読む
		// 優先順位の都合でベンダ別に読み直す
		val xml1 = File("emoji4unicode/data/emoji4unicode.xml")
			.readAllBytes()
			.decodeUtf8()
		for (vendor in arrayOf("docomo", "kddi", "softbank")) {
			"""<e([^>]+)""".toRegex().findAll(xml1).forEach { mr1 ->
				val attrs = HashMap<String, String>()
				"""(\w+)="([^"]+)"""".toRegex().findAll(mr1.groupValues[1]).forEach { mr2 ->
					attrs[mr2.groupValues[1].unescapeXml()] = mr2.groupValues[2].unescapeXml()
				}
				val unicode = attrs["unicode"]?.toCodepointList("emoji4unicode") ?: return@forEach
				val strFrom = attrs[vendor] ?: return@forEach
				if (strFrom.indexOf(">") != -1) return@forEach
				val from = strFrom.toCodepointList("emoji4unicode") ?: return@forEach
				val text = "${attrs["name"]}/${attrs["text_fallback"]}"
				vendorText.prepare(from) { ArrayList() }.add(text)
				val old = vendorUnicodeMap[from]
				if (old != null) {
					if (old.second == "kddi" && vendor == "softbank") return@forEach
					error = true
					log.e("vendorUnicodeMap conflict. code=$from old=$old new=$unicode($vendor)")
				} else {
					vendorUnicodeMap[from] = Pair(unicode, vendor)
				}
			}
		}

		for (vendor in arrayOf("docomo", "kddi", "softbank")) {

			// ベンダ個別ファイルから説明文を読む
			val xml = File("emoji4unicode/data/${vendor}/carrier_data.xml")
				.readAllBytes()
				.decodeUtf8()

			"""<e([^>]+)""".toRegex().findAll(xml).forEach { mr1 ->
				val attrs = HashMap<String, String>()
				"""(\w+)="([^"]+)"""".toRegex().findAll(mr1.groupValues[1]).forEach { mr2 ->
					attrs[mr2.groupValues[1].unescapeXml()] = mr2.groupValues[2].unescapeXml()
				}

				val code = attrs["unicode"]?.toCodepointList("emoji4unicode")
					?: return@forEach

				attrs["name_ja"]?.let { vendorText.prepare(code) { ArrayList() }.add("$it($vendor)") }
			}
		}

		if (error) error("readVendorCode failed.")
	}

	private var hasConflict = false

	// コード=>画像の重複を調べる
	private fun checkCodeConflict() {

		val codeMap = HashMap<CodepointList, HashSet<Emoji>>()
		for (emoji in emojiMap.values) {
			for (code in emoji.codes) {
				codeMap.prepare(code) { HashSet() }.add(emoji)
			}
		}

		for ((code, emojis) in codeMap.entries.sortedBy { it.key }) {
			if (emojis.size == 1) continue

			val fixResName = fixCode[code]
			if (fixResName != null) {
				var found = false
				for (emoji in emojis) {
					if (emoji.resName == fixResName) {
						found = true
					} else {
						emoji.codes.forEach {
							if (it == code) log.w("fixCode: delete(1) $it for ${emoji.resName}")
						}
						emoji.removeCodeByCode(code)
					}
				}
				if (!found) error("checkCodeConflict: missing emoji resName=$fixResName")
				continue
			}

			val onlyVendorCode = emojis.all { emoji ->
				when (emoji.codes.find { it == code }?.from) {
					"EmojiDataJson(au)", "EmojiDataJson(softbank)", "EmojiDataJson(docomo)" -> true
					else -> false
				}
			}

			if (onlyVendorCode) {
				val preferCode = vendorUnicodeMap[code]?.first
				if (preferCode != null) {
					val targetEmoji = emojis.find { emoji -> emoji.codes.any { it == preferCode } }
					if (targetEmoji != null) {
						emojis.forEach { emoji ->
							if (emoji != targetEmoji) {
								emoji.codes.forEach {
									if (it == code) log.w("fixCode: delete(2) $it for ${emoji.resName}")
								}
								emoji.removeCodeByCode(code)
							}
						}
						continue
					}
					log.e("checkCodeConflict: can't use vendorUnicodeMap. code=$code, preferCode=$preferCode")
				}
			}

			log.e("checkCodeConflict: code $code ${vendorText[code]} ${
				emojis.joinToString(" ") {
					"${it.resName}/${it.unified.toRawString()}"
				}
			}")
			hasConflict = true
		}

		// コードのない絵文字のチェック
		for (emoji in emojiMap.values) {
			if (emoji.codes.isNotEmpty()) continue
			val fixes = fixCode.entries.filter { it.value == emoji.resName }
			when (fixes.size) {
				0 -> {
					log.e("checkCodeConflict: emoji has no code. resName=${emoji.resName},cameFrom=${emoji.imageFiles.first().second}")
					hasConflict = true
				}
				1 -> {
					val fix = fixes.first()
					val code = fix.key
					emoji.addCode(code)
					log.i("fixCode code=$code resName=${emoji.resName}")
				}
				else -> {
					log.e("checkCodeConflict: multiple fix match for ${emoji.resName}")
					hasConflict = true
				}
			}
		}
	}

	private fun checkShortNameConflict() {
		val nameMap = HashMap<ShortName, HashSet<Emoji>>().apply {
			for (emoji in emojiMap.values) {
				for (name in emoji.shortNames) {
					prepare(name) { HashSet() }.add(emoji)
				}
			}
		}

		// cameFromCategory 以外のshortNameがあるなら、cameFromCategoryのshortNameは使わない
		for (emoji in emojiMap.values) {
			if (emoji.shortNames.any { it.cameFrom != cameFromCategory }) {
				emoji.removeShortNameByCameFrom(cameFromCategory)
			}
		}

		for ((name, emojis) in nameMap.entries.sortedBy { it.key }) {
			// shortNameからemojiを1意に解決できるなら正常
			if (emojis.size == 1) continue

			// fixNameで解決する
			val fixResName = fixName[name]
			if (fixResName != null) {
				var found = false
				for (emoji in emojis) {
					if (emoji.resName == fixResName) {
						found = true
					} else {
						emoji.removeShortName(name.name)
					}
				}
				if (!found) error("checkShortNameConflict: missing emoji resName=$fixResName")
				continue
			}

			// emoji,cameFrom のペアのリスト
			val froms = emojiMap.values
				.flatMap { emoji -> emoji.shortNames.map { Pair(emoji, it) } }
				.filter { it.second == name }
				.map { Pair(it.first, it.second.cameFrom) }

			// どこ由来のShortNameかで優先順位をつける
			val preferFrom = froms.find { it.second == "EmojiDataJson" }
				?: froms.find { it.second == "EmojiSpec" }
				?: froms.find { it.second == "EmojiOneJson" }
				?: froms.find { it.second == cameFromCategory }
			if (preferFrom != null) {
				// 優先順位の低いemojiからshortNameを除去する
				var found = false
				for (emoji in emojis) {
					if (emoji == preferFrom.first) {
						found = true
					} else {
						emoji.removeShortName(name.name)
					}
				}
				if (!found) error("checkShortNameConflict: missing emoji ${preferFrom.first.key}")
				continue
			}

			// 解決できなかった
			log.e("checkShortNameConflict: name $name froms=${froms.joinToString(",")}")
			hasConflict = true
		}

		// 名前のない絵文字のチェック
		for (emoji in emojiMap.values) {
			if (emoji.shortNames.isNotEmpty()) continue
			val fix = fixName.entries.filter { it.value == emoji.resName }
			if (fix.size > 1) error("checkShortNameConflict: multiple fix match for ${emoji.resName}")
			if (fix.size == 1) {
				emoji.addShortName(fix.first().key)
				continue
			}
			log.e("checkShortNameConflict: emoji has no shortName. resName=${emoji.resName},cameFrom=${emoji.imageFiles.first().second}")
			hasConflict = true
		}
	}

	suspend fun run() {
		HttpClient {
			install(HttpTimeout) {
				val t = 30000L
				requestTimeoutMillis = t
				connectTimeoutMillis = t
				socketTimeoutMillis = t
			}
		}.use { client ->

			readFixData()

			readQualified13_1(client)

			scanEmojiImages()
			copyImages()

			readVendorCode()
			readEmojiData()
			readEmojione()
			readEmojiSpec(client)
			readCategoryShortName(client)

			checkCodeConflict()


			checkShortNameConflict()
			fixCategory()

			if (hasConflict) error("please fix conflicts.")

			// shortcodeに含まれる文字の種類を列挙する
			val nameChars = HashSet<Char>()
			val nameMap = HashMap<ShortName, Emoji>()
			for (emoji in emojiMap.values) {
				for (shortName in emoji.shortNames) {
					nameMap[shortName] = emoji
					for (c in shortName.name)
						nameChars.add(c)
				}
			}
			log.w("nameChars: [${nameChars.sorted().joinToString("")}]")


			val outFile = "emoji_map.txt"
			UnixPrinter(File(outFile)).use { writer ->
				for (emoji in emojiMap.values.sortedBy { it.key }) {

					val codeSet = emoji.codeSet.sorted()

					// asciiコードだけの絵文字は処理しない
					if (codeSet.isEmpty()) {
						log.w("skip emoji ${emoji.unified} ${emoji.resName} that has no valid codes")
						emoji.skip = true

					} else if (emoji.unified.list.isAsciiEmoji()) {
						log.w("skip emoji ${emoji.unified} ${emoji.resName} that has no valid codes")
						emoji.skip = true
					}
					if (emoji.skip) continue

					// 画像リソースIDとUnicodeシーケンスの関連付けを出力する
					val strResName = emoji.resName
					if (File("assets/$strResName.svg").isFile) {
						writer.println("s1:$strResName.svg//${emoji.imageFiles.first().second}")
					} else {
						writer.println("s1d:$strResName//${emoji.imageFiles.first().second}")
					}
					codeSet.forEach { code ->
						val raw = code.toRawString()
						if(raw.isEmpty()) error("too short code! ${emoji.resName}")
						writer.println("s2:$raw//${code.from}")
					}
				}

				for (emoji in emojiMap.values.sortedBy { it.key }) {
					if (emoji.skip) continue

					// shortcodeから変換するunicode表現
					val unified = emoji.unified

					// 画像リソースIDとshortcodeの関連付けを出力する
					// 投稿時にshortcodeをユニコードに変換するため、shortcodeとUTF-16シーケンスの関連付けを出力する
					for (name in emoji.shortNames.map { it.name }.toSet().sorted()) {
						val froms = emoji.shortNames.filter { it.name == name }.map { it.cameFrom }.sorted()
						writer.println("n:$name,${unified.toRawString()}//${froms.joinToString(",")}")
					}
				}

				categoryNames.values.forEach { category ->
					writer.println("c1:${category.enumId}")
					category.eachEmoji { emoji ->
						if (emoji.skip) return@eachEmoji
						val shortName = emoji.shortNames.first()
						writer.println("c2:${shortName.name}//${shortName.cameFrom}")
					}
				}

				val enumId = "CATEGORY_OTHER"
				writer.println("c1:${enumId}")
				emojiMap.values
					.filter { it.usedInCategory == null && !it.isToneVariation }
					.sortedBy { it.shortNames.first() }
					.forEach { emoji ->
						if (emoji.skip) return@forEach
						val shortName = emoji.shortNames.first()
						writer.println("c2:${shortName.name}//${shortName.cameFrom}")
					}
			}

			log.d("wrote $outFile")

			log.d("codeCameFroms: ${Emoji.codeCameFroms.joinToString(",")}")

			// shortname => unicode
//			JsonArray()
//				.also { dst ->
//					for ((shortName, rh) in nameMap.entries.sortedBy { it.key }) {
//						val resInfo = rh.values.first()
//						dst.add(jsonObject("shortcode" to shortName.name, "unicode" to resInfo.unified))
//					}
//				}
//				.toString(2)
//				.encodeUtf8()
//				.saveTo(File("shortcode-emoji-data-and-old-emojione2.json"))
		}
	}
}


fun main(args: Array<String>) = runBlocking {
	log.d("args=${args.joinToString(",")}")
	App().run()
}
