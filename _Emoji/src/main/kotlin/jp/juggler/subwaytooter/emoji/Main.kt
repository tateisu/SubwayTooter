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
	companion object{

		const val fromCategoryHtml = "CategoryHtml"


		val reComment = """#.*""".toRegex()

		private val ignoreImagePath = arrayOf(
			"LICENSE",
			// fe82b (フリーダイアル) はnoto-emoji では ?旗 になっていて使えない
			"noto-emoji/png/128/emoji_ufe82b.png",
			"noto-emoji/svg/emoji_ufe82b.svg",
			// mastodonのフォルダにある余計なファイル
			"mastodon/public/emoji/sheet_10.png"
		)

		// emojipediaにあるデータのうち、次のショートネームを持つ絵文字は無視する
		val ignoreShortName = arrayOf(
			"flag_for_texas_ustx"
		)

		private val ignoreEmojiOneShortNames = setOf(
			"man_in_tuxedo",
			"man_in_tuxedo_tone1", "tuxedo_tone1",
			"man_in_tuxedo_tone2", "tuxedo_tone2",
			"man_in_tuxedo_tone3", "tuxedo_tone3",
			"man_in_tuxedo_tone4", "tuxedo_tone4",
			"man_in_tuxedo_tone5", "tuxedo_tone5",
		)
	}

	// 修正用データ
	private val fixCode = HashMap<CodepointList, String>()
	private val fixName = HashMap<ShortName, String>()
	private val fixCategory = ArrayList<Pair<String, String>>()
	private val fixUnified = HashMap<CodepointList, CodepointList>()

	// 画像ファイルをスキャンして絵文字コードとファイルの対応表を作る
	// マップのキーはvariation selectorとZWJが除去される
	private val emojiMap = HashMap<CodepointList, Emoji>()

	private val emojipediaShortNames = HashMap<CodepointList, ShortName>()

	/////////////////////////////////////////////////

	private fun readFixData() {
		val fileFixData = "./fix_code.txt"

		File(fileFixData).forEachLine { lno, rawLine ->
			val line = rawLine
				.replace(reComment, "")
				.trim()

			val mr = """\A(\w+)\s*([\w._-]+)\s*(.*)""".toRegex().find(line)
			if (mr != null) {
				val type = mr.groupValues[1]
				val arg1 = mr.groupValues[2]

				if (type == "unified") {
					val code = arg1.toCodepointList("fixUnified")!!
					val key = code.toKey("fixUnified")!!
					fixUnified[key] = code
					return@forEachLine
				}

				val data = """([\w+-]+)""".toRegex().findAll(mr.groupValues[3]).map { it.groupValues[1] }.toList()
				if (data.size != 1) return@forEachLine
				when (type) {
					"code" -> fixCode[arg1.toCodepointList("fixCode")!!] = data.first()
					"name" -> fixName[arg1.toShortName("fixName")!!] = data.first()
					"category" -> Pair(arg1, data.first()).addTo(fixCategory)
					else -> error("$fileFixData $lno : bad fix_data type=$type")
				}
			}
		}
	}

	// Emojipediaのバージョン別一覧とカテゴリ別一覧を読んでJSONに保存しておく
	// サイトにアクセスできなくなったら困るからな…
	@Suppress("FunctionName")
	private suspend fun readEmojipedia(client: HttpClient) :JsonObject {

		val fileEmojipedia = File("Emojipedia.json")
		if( fileEmojipedia.isFile) return fileEmojipedia.readAllBytes().decodeUtf8().decodeJsonObject()

		val dstRoot = JsonObject()
		val dstQualified = JsonArray().also{ dstRoot["qualifiedCode"] = it }

		for (url in arrayOf(
			"https://emojipedia.org/emoji-13.1/",
			"https://emojipedia.org/emoji-13.0/",
			"https://emojipedia.org/emoji-12.1/",
			"https://emojipedia.org/emoji-12.0/",
			"https://emojipedia.org/emoji-11.0/",
			"https://emojipedia.org/emoji-5.0/",
			"https://emojipedia.org/emoji-4.0/",
			"https://emojipedia.org/emoji-3.0/",
			"https://emojipedia.org/emoji-2.0/",
			"https://emojipedia.org/emoji-1.0/",
		)) {
			val root = client.cachedGetString(url, mapOf()).parseHtml(url)

			root.getElementsByClass("sidebar").forEach { it.remove() }
			root.getElementsByClass("categories").forEach { it.remove() }

			for (list in root.getElementsByTag("ul")) {
				for (li in list.getElementsByTag("li")) {

					val href = li.getElementsByTag("a")?.attr("href")
						.notEmpty() ?: continue

					val spanText = li.getElementsByTag("span").find { it.hasClass("emoji") }?.text()
						?.notEmpty() ?: continue

					dstQualified.add(jsonArray(spanText, href))
				}
			}
		}

		val dstCategory = JsonObject().also{ dstRoot["categories"]=it}
		categoryNames.forEach { category ->
			if (category.url == null) return@forEach

			val dstCategoryItems = JsonArray().also { dstCategory[category.name] = it }

			val root = client.cachedGetString(category.url, mapOf()).parseHtml(category.url)
			val list = root.getElementsByClass("emoji-list").first()
			for (li in list.getElementsByTag("li")) {
				val href = li.getElementsByTag("a").attr("href")
					.notEmpty() ?: continue

				val spanText = li.getElementsByTag("span").find { it.hasClass("emoji") }?.text()
					?.notEmpty() ?: continue

				dstCategoryItems.add(jsonArray(spanText, href))
			}
		}

		dstRoot.toString(2).encodeUtf8().saveTo(fileEmojipedia)
		return dstRoot
	}

	////////////////////////////////////////////////////////////////////////

	// noto-emoji のファイル名はfe0fが欠けている
	// あらかじめEmojipediaのデータを参照してqualified name の一覧を作っておく
	private fun readEmojipediaQualified(root:JsonObject) {

		val ignoreName2 = setOf(
			"zero_width_joiner",
			"variation_selector_16",
		)

		val cameFrom = "emojiQualified"

		val hrefList = ArrayList<Pair<String, CodepointList>>()

		var countError = 0

		for( cols in  root.jsonArray("qualifiedCode")!!.filterIsInstance<JsonArray>()) {
			val spanText = cols[0] as String
			var href = cols[1] as String

			var code = spanText.listCodePoints().toCodepointList(cameFrom)
				?: error("can't get code from $spanText $href")



			if (hrefList.any { it.first == href })
				error("duplicate href: $href")

			hrefList.add(Pair(href, code))

			// https://emojipedia.org/80030/ Couple With Heart: Light Skin Tone
			// ページ名が名前じゃないのを直す
			if (href == "80030") href = "couple-with-heart-light-skin-tone"

			val shortName = href.replace("/", "").toShortName(cameFrom)
				?: error("can't parse $href")

			if (ignoreName2.contains(shortName.name)) {
				log.w("skip ${shortName.name}")
				continue
			}

			val key = code.toKey(cameFrom)
				?: error("can't get key from ${code.toHex()} ${shortName.name}")

			if (!fixUnified.containsKey(key)) {
				if (code.list.size == 1 && code.list.first() < 256) {
					++countError
					log.e("bad unified code: $code")
				} else {
					fixUnified[key] = code
				}
			}

			if (ignoreShortName.any { it == shortName.name }) {
				log.w("skip shortname $shortName $code")
				continue
			}

			emojipediaShortNames[key] = shortName
		}

		// hrefList.sortedBy{ it.first }.forEach { log.d("href=${it.first} ${it.second}") }

		if(countError>0) error("please fix unified codes. countError=$countError")
	}

	private fun addEmojipediaShortnames() {
		for ((key, shortName) in emojipediaShortNames) {
			emojiMap[key]?.addShortName(shortName)
		}
	}

	// Emojipediaのデータを使ってカテゴリ別に絵文字一覧を用意する
	private fun readCategoryShortName(root:JsonObject) {
		for(category in categoryNames){
			val list = root.jsonObject("categories")?.jsonArray(category.name) ?: continue
			for( cols in list.filterIsInstance<JsonArray>()){
				val spanText = cols[0] as String
				val href = cols[1] as String

				val shortName = href.replace("/", "").toShortName(fromCategoryHtml)
					?: error("can't parse $href")

				if (ignoreShortName.any { shortName.name == it }) continue

				val code = spanText.listCodePoints().toCodepointList(fromCategoryHtml)
					?: error("can't parse code from $spanText")

				val key = code.toKey(fromCategoryHtml)
				val emoji = emojiMap[key]
					?: error("can't find emoji. category=${category.name}, href=$href, spanText=$spanText")

				category.addEmoji(emoji, allowDuplicate = true, addingName = shortName.toString())
			}
		}
	}

	// サブフォルダをスキャンして絵文字別に画像データを確定する
	private fun scanImageDir(
		cameFrom: String,
		dirPath: String,
		@Language("RegExp") codeSpec: String,
		unifiedQualifier: (CodepointList) -> CodepointList = { it }
	) {
		val dir = File(dirPath)
		val reCodeSpec = codeSpec.toRegex()
		var countFound = 0
		var countCreate = 0
		var countError = 0
		for( imageFile in dir.listFiles()!!){
			if (!imageFile.isFile) continue
			val unixPath = imageFile.path.replace("\\", "/")
			if (ignoreImagePath.any { unixPath.endsWith(it) }) continue

			val name = imageFile.name.replace("_border.", ".")

			val code = reCodeSpec.find(name)
				?.groupValues
				?.elementAtOrNull(1)
				?.toCodepointList(cameFrom)
				?: error("can't parse $name")

			++countFound

			val key = code.toKey(cameFrom)!!

			var emoji = emojiMap[key]
			if (emoji == null) {
				val unified2 = fixUnified[key] ?: unifiedQualifier(code)
				if( unified2.list.size==1 && unified2.list.first()<256){
					++countError
					log.e("bad unified code: $unified2")
				}
				emoji = Emoji(key, unified2)

				emojiMap[key] = emoji
				++countCreate
			}

			emoji.imageFiles.add(Pair(imageFile, cameFrom))
			emoji.addCode(code)
		}

		log.d("scanImageDir: found=$countFound,create=$countCreate, dir=$dir")
		if(countError>0) error("please fix unified codes. countError=$countError")
	}

	// サブフォルダをスキャンして絵文字別に画像データを確定する
	private fun scanEmojiImages() {

		scanImageDir("override", "override", """([0-9A-Fa-f_-]+)\.""")
		scanImageDir("mastodonSVG", "mastodon/public/emoji", """([0-9A-Fa-f_-]+)\.""")
		scanImageDir("twemojiSvg", "twemoji/assets/svg/", """([0-9A-Fa-f_-]+)\.""")
		scanImageDir("notoSvg", "noto-emoji/svg", """emoji_u([0-9A-Fa-f_-]+)\.""") { code ->
			if (code.list.last() != 0xfe0f)
				"${code.toHex()}-fe0f".toCodepointList("notoSvgFix")!!
			else
				code
		}
		scanImageDir("notoPng", "noto-emoji/png/72", """emoji_u([0-9A-Fa-f_-]+)\.""")
		scanImageDir("emojiDataTw", "emoji-data/img-twitter-72", """([0-9A-Fa-f_-]+)\.""")
		scanImageDir("emojiDataGo", "emoji-data/img-google-136", """([0-9A-Fa-f_-]+)\.""")
		scanImageDir("emojiOne", "emojione/assets/svg", """([0-9A-Fa-f_-]+)\.""")
	}

	// 絵文字ごとにファイルをコピーする
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

	// emojiDataのjsonを読んで変換コードポイントやショートネームを追加する
	private fun readEmojiData() {
		for( src in  File("./emoji-data/emoji.json")
			.readAllBytes()
			.decodeUtf8()
			.decodeJsonArray()
			.objectList()
		){
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
				for ((k, data) in skinVariations.entries ) {
					if (data !is JsonObject) continue

					// 再帰呼び出しあり
					fun handleCode(list: IntArray, idx: Int, parentSuffix: Array<String>, suffixIndex: Int) {
						val code = list.elementAtOrNull(idx) ?: return
						val modifier = skinToneModifiers[code]
							?: error("missing skinToneModifier u${list[idx].toString(16)} for $parentName")
						skinToneUsed.add(code)
						val lastSuffix = modifier.suffixList[suffixIndex]
						val suffix =
							if (parentSuffix.contains(lastSuffix))
								parentSuffix
							else
								arrayOf(*parentSuffix, lastSuffix)
						if (idx < list.size - 1) {
							handleCode(list, idx + 1, suffix, suffixIndex)
						} else {
							unified = data.string("unified")!!.toCodepointList("EmojiData(skinTone)")!!
							key = unified.toKey("EmojiData(skinTone)")
							emoji = emojiMap[key] ?: error("can't find emoji for $key")

							emoji.addCode(unified)
							shortNames
								.mapNotNull { (it.name + suffix.joinToString("")).toShortName("EmojiData(skinTone)") }
								.forEach { emoji.addShortName(it) }

							emoji.addToneParent(parentEmoji)
						}
					}

					val codeList = k.toCodepointList("toneSpec")!!.list
					for (suffixIndex in skinToneModifiers.values.first().suffixList.indices) {
						handleCode(codeList, 0, emptyArray(), suffixIndex)
					}
				}
				if (skinToneUsed.size != skinToneModifiers.size) {
					log.w("skin tone code not fully used: $parentName")
				}
			}
		}
	}



	private fun readEmojiOne() {
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
			names
				.mapNotNull { it.toShortName(cameFrom) }
				.filter { !ignoreEmojiOneShortNames.contains(it.name) }
				.forEach { emoji.addShortName(it) }
		}
	}



	private fun fixCategory() {
		val nameMap = HashMap<ShortName, Emoji>().apply {
			for (emoji in emojiMap.values)
				for (shortName in emoji.shortNames)
					this[shortName] = emoji
		}
		for ((name, strShortName) in fixCategory) {
			val category = categoryNames.find { it.name == name }
				?: error("fixCategory: missing category for $name")
			val shortName = strShortName.toShortName("fixCategory")
				?: error("fixCategory: can't parse $strShortName")
			val emoji = nameMap[shortName]
				?: error("fixCategory: missing emoji for $strShortName")

			category.addEmoji(emoji, addingName = shortName.toString())
			log.d("fixCategory $category ${emoji.resName} ${shortName}")
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
			if (emoji.shortNames.any { it.cameFrom != fromCategoryHtml }) {
				emoji.removeShortNameByCameFrom(fromCategoryHtml)
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
				?: froms.find { it.second == fromCategoryHtml }

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
			log.e("checkShortNameConflict: name $name froms=${froms.joinToString(",") { "${it.first.resName}${it.second}" }}")
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

	private fun fixToneParent() {
		var hasError = false
		val nameMap = HashMap<String, Emoji>()
		for (emoji in emojiMap.values) {
			for (shortName in emoji.shortNames) {
				nameMap[shortName.name] = emoji
			}
		}
		val suffixList = skinToneModifiers.values
			.flatMap { it.suffixList.toList() }
			.sortedByDescending { it.length }


		for (emoji in emojiMap.values) {
			// トーンの絵文字の一部は内部に他のトーンの名前を含むので誤検出を回避する
			if (emoji.resName in arrayOf("emj_1f3fc", "emj_1f3fe")) continue

			fun String.removeToneSuffix(): String {
				var name = this
				when (name) {
					"kiss_light_skin_tone" -> return "couplekiss"
					"kiss_medium_light_skin_tone" -> return "couplekiss"
					"kiss_medium_skin_tone" -> return "couplekiss"
					"kiss_medium_dark_skin_tone" -> return "couplekiss"
					"kiss_dark_skin_tone" -> return "couplekiss"
				}

				suffixList.forEach { name = name.replace(it, "") }
				return when (name) {
					"couple_with_heart_person_person" -> "couple_with_heart"
					"kiss_person_person" -> "couplekiss"
					"kiss_woman_woman" -> "woman_kiss_woman"
					"kiss_woman_man" -> "woman_kiss_man"
					"kiss_man_man" -> "man_kiss_man"
					else -> name
				}
			}

			for (shortName in emoji.shortNames) {
				val parent = nameMap[shortName.name.removeToneSuffix()]
				if (parent == emoji) continue
				if (parent == null) {
					log.e("${emoji.resName} $shortName looks like tone variation,but can't find parent.")
					hasError = true
					continue
				}
				emoji.addToneParent(parent)
			}
		}

		for (emoji in emojiMap.values) {
			val parents = emoji.toneParents
			if (parents.isEmpty()) continue
			if (parents.size > 1) {
				log.e("${emoji.resName} has many parents. ${parents.joinToString(",")}")
				hasError = true
				continue
			}
			parents.forEach { parent ->
				val toneCode = emoji.key.getToneCode("makeToneMap")
					?: error("emoji $emoji has parent, but has no toneCode.")
				when (val old = parent.toneChildren[toneCode]) {
					null -> parent.toneChildren[toneCode] = emoji
					emoji -> {
					}
					else -> error("conflict toneChildren. emoji ${parent.resName} has $old and $emoji.")
				}
			}
		}

		if (hasError) error("toneParent error.")
	}

	private fun writeData(){
		val outFile = "emoji_map.txt"
		UnixPrinter(File(outFile)).use { writer ->

			// 絵文字をskipするか事前に調べる
			for (emoji in emojiMap.values.sortedBy { it.key }) {

				val codeSet = emoji.codeSet.sorted()
				if (codeSet.isEmpty()) {
					log.w("skip emoji ${emoji.unified} ${emoji.resName} that has no valid codes")
					emoji.skip = true
				} else if (emoji.unified.list.isAsciiEmoji()) {
					log.w("skip emoji ${emoji.unified} ${emoji.resName} that has no valid codes")
					emoji.skip = true
				}
			}

			for (emoji in emojiMap.values.sortedBy { it.key }) {
				if (emoji.skip) continue

				// 画像リソースID
				val strResName = emoji.resName
				if (File("assets/$strResName.svg").isFile) {
					writer.println("svg:$strResName.svg//${emoji.imageFiles.first().second}")
				} else {
					writer.println("drawable:$strResName//${emoji.imageFiles.first().second}")
				}

				// unified
				writer.println("un:${emoji.unified.toRawString()}//${emoji.unified.from}")

				// Unicodeシーケンス
				val codeSet = emoji.codeSet.sorted()
				for (code in codeSet) {
					if (code == emoji.unified) continue
					val raw = code.toRawString()
					if (raw.isEmpty()) error("too short code! ${emoji.resName}")
					writer.println("u:$raw//${code.from}")
				}

				// 画像リソースIDとshortcodeの関連付けを出力する
				// 投稿時にshortcodeをユニコードに変換するため、shortcodeとUTF-16シーケンスの関連付けを出力する
				val nameList = emoji.nameList.notEmpty()
					?: error("missing shortName. ${emoji.resName}")
				nameList.forEachIndexed { index, triple ->
					val (_, name, froms) = triple
					val header = if (index == 0) "sn" else "s"
					writer.println("${header}:$name//${froms.joinToString(",")}")
				}
			}

			fun Category.printCategory(list:List<Emoji>){
				writer.println("cn:${this.name}")
				for(emoji in list){
					writer.println("c:${emoji.unified.toRawString()}")
					emoji.usedInCategory = this
				}
			}

			categoryNames.forEach { category ->
				category.printCategory(category.emojis.filter { !it.skip })
			}

			run{
				val category = categoryNames.find{ it.name == "Others"}!!
				category.printCategory(
					emojiMap.values
						.filter { it.usedInCategory == null && it.toneParents.isEmpty() }
						.sortedBy { it.shortNames.first() }
				)
			}

			// スキントーン
			emojiMap.values
				.filter { it.toneChildren.isNotEmpty() }
				.sortedBy { it.key }
				.forEach { parent ->
					if( parent.usedInCategory==null){
						log.e("parent ${parent.resName} not used in any category!")
					}
					parent.toneChildren.entries
						.toList()
						.sortedBy { it.key }
						.forEach eachChild@{
							val child = it.value
							if (child.skip) return@eachChild
							writer.println("t:${parent.unified.toRawString()},${it.key.toRawString()},${child.unified.toRawString()}")
						}
				}

			// 複合トーン
			run{
				val category = categoryNames.find { it.name == "ComplexTones" }!!
				category.printCategory(
					emojiMap.values
						.filter { it.toneChildren.isNotEmpty() }
						.sortedBy { it.key }
						.flatMap { parent ->
							if( parent.usedInCategory==null){
								log.e("parent ${parent.resName} not used in any category!")
							}
							parent.toneChildren.entries
								.toList()
								.filter { it.key.list.size > 1 }
								.sortedBy { it.key }
								.map{ it.value}
						}
				)
			}
		}

		log.d("wrote $outFile")
	}

	suspend fun run() {

		// 修正用データを読む
		readFixData()

		// emojipediaからバージョン別一覧とカテゴリ別一覧を読む
		val emojipediaData = HttpClient {
			install(HttpTimeout) {
				val t = 30000L
				requestTimeoutMillis = t
				connectTimeoutMillis = t
				socketTimeoutMillis = t
			}
		}.use { client ->
			readEmojipedia(client)
		}

		// 画像をスキャンする前に絵文字のqualified codeを調べておく
		readEmojipediaQualified(emojipediaData)

		// サブフォルダから絵文字の画像を収集する
		scanEmojiImages()
		// 収集した画像をコピーする
		copyImages()

		addEmojipediaShortnames()

		readVendorCode()
		readEmojiData()
		readEmojiOne()
		readCategoryShortName(emojipediaData)

		checkCodeConflict()
		checkShortNameConflict()

		fixToneParent()


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

		writeData()

		log.d("codeCameFroms: ${Emoji.codeCameFroms.joinToString(",")}")
		log.d("nameCameFroms: ${Emoji.nameCameFroms.joinToString(",")}")
	}
}

fun main(args: Array<String>) = runBlocking {
	log.d("args=${args.joinToString(",")}")
	App().run()
}
