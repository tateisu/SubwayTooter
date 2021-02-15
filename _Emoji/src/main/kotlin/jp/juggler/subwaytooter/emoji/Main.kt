package jp.juggler.subwaytooter.emoji

import com.android.ide.common.vectordrawable.Svg2Vector
import io.ktor.client.*
import io.ktor.client.features.*
import kotlinx.coroutines.runBlocking
import java.io.*
import kotlin.math.max
import java.io.FileInputStream
import java.lang.StringBuilder


//pngフォルダにある画像ファイルを参照する
//emoji-data/emoji.json を参照する
//
//以下のjavaコードを生成する
//- UTF-16文字列 => 画像リソースID のマップ。同一のIDに複数のUTF-16文字列が振られることがある。
//- shortcode => 画像リソースID のマップ。同一のIDに複数のshortcodeが振られることがある。
//- shortcode中の区切り文字はハイフンもアンダーバーもありうる。出力データではアンダーバーに寄せる
//- アプリはshortcodeの探索時にキー文字列の区切り文字をアンダーバーに正規化すること

const val pathCwebp = "C:/cygwin64/bin/cwebp.exe"

private const val hexChars = "0123456789abcdef"

private val reHex = """([0-9A-Fa-f]+)""".toRegex()

class Category(
	val nameLower: String,
	val enumId: String,
	val url: String?
) {
	//
	override fun equals(other: Any?) = enumId == (other as? Category)?.enumId
	override fun hashCode(): Int = enumId.hashCode()

	// ショートコード登場順序がある
	val shortcodes = ArrayList<String>()
}


//	my $utf8 = Encode::find_encoding("utf8");
//	my $utf16 = Encode::find_encoding("UTF-16BE");
var utf16_max_length = 0


// list of codepoints
class CodepointList(val list: ArrayList<Int>) {

	override fun equals(other: Any?): Boolean = list == (other as?CodepointList)?.list
	override fun hashCode(): Int = list.hashCode()

	// make string "uuuu-uuuu-uuuu-uuuu"
	override fun toString() = StringBuilder(list.size * 5).also {
		list.forEachIndexed { i, v ->
			if (i > 0) it.append('-')
			it.append(String.format("%04x", v))
		}
	}.toString()

	// make raw string
	fun toRawString() = StringBuilder(list.size + 10).also { sb ->
		for (cp in list) {
			sb.appendCodePoint(cp)
		}
	}.toString()

	fun makeUtf16(): String {
		// java の文字列にする

		// UTF-16にエンコード
		val bytesUtf16 = toRawString().toByteArray(Charsets.UTF_16BE)

		// UTF-16の符号単位数
		val length = bytesUtf16.size / 2
		if (length > utf16_max_length) {
			utf16_max_length = length
		}

		val sb = StringBuilder()
		for (i in bytesUtf16.indices step 2) {
			val v0 = bytesUtf16[i].toInt()
			val v1 = bytesUtf16[i + 1].toInt()
			sb.append("\\u")
			sb.append(hexChars[v0.and(255).shr(4)])
			sb.append(hexChars[v0.and(15)])
			sb.append(hexChars[v1.and(255).shr(4)])
			sb.append(hexChars[v1.and(15)])
		}
		return sb.toString()
	}
}

// hh-hh-hh-hh => arrayOf("hh","hh","hh","hh")
fun String.parseCodePoints() = ArrayList<Int>()
	.also { dst ->
		reHex.findAll(this).forEach { mr ->
			dst.add(mr.groupValues[1].toInt(16))
		}
	}.notEmpty()?.let { CodepointList(it) }

// ショートコードの名前を正規化する
fun String.parseShortName() = toLowerCase().replace("-", "_")

private val reColonHead = """\A:""".toRegex()
private val reColonTail = """:\z""".toRegex()
private val reZWJ = """-(?:200d|fe0f)""".toRegex(RegexOption.IGNORE_CASE)

// 前後の::を取り除いてショートネーム正規化
fun String.parseAlphaCode() =
	this.replace(reColonHead, "").replace(reColonTail, "").parseShortName()

// xxxx-xxxx-xxxx から ZWJのコードを除去する
fun String.removeZWJ() = replace(reZWJ, "")

class EmojiVariant(val dir: String) {
	val used = ArrayList<String>()
}

val emojiVariants = arrayOf(
	EmojiVariant("img-twitter-64"),
	EmojiVariant("img-google-64"),
	EmojiVariant("img-apple-64"),
	EmojiVariant("img-apple-160"),
	EmojiVariant("img-facebook-64"),
	EmojiVariant("img-messenger-64"),
)

val emojiDataCodepointsVendors = arrayOf("docomo", "au", "softbank", "google")

// returns path of emoji
fun findEmojiImage(image: String): String? {
	for (variant in emojiVariants) {
		val path = "emoji-data/${variant.dir}/$image"
		if (File(path).isFile) {
			if (variant.used.size < 5) variant.used.add(image)
			return path
		}
	}
	return null
}

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

fun svgToVectorDrawable(dst: File, src: File) {

	val tmp = ByteArrayOutputStream()

	// Write all the error message during parsing into SvgTree. and return here as getErrorLog().
	// We will also log the exceptions here.
	try {
		val svgTree = Svg2Vector.parse(src)
		svgTree.mScaleFactor = 24 / max(svgTree.w, svgTree.h)
		if (svgTree.canConvertToVectorDrawable()) {
			Svg2Vector.writeFile(tmp, svgTree)
		}
		val errorLog = svgTree.errorLog
		if (errorLog.isNotEmpty()) println("$src $errorLog")
		FileOutputStream(dst).use { outStream ->
			outStream.write(tmp.toByteArray())
		}
	} catch (e: Exception) {
		println("svgToVectorDrawable: ${e.message} ${src.canonicalPath}")
	}
}


class Resource(
	val res_name: String,
	val unified: CodepointList,
	var isToneVariation: Boolean = false
) {
	val codepointMap = HashMap<String, CodepointList>()
	val shortnames = HashSet<String>()
	val categories = HashSet<Category>()

	fun addCodePoints(listCode: Iterable<CodepointList>) {
		listCode.forEach { codepointMap[it.toString()] = it }
	}

	fun addShortnames(listName: Iterable<String>) {
		shortnames.addAll(listName)
	}
}

class SkinToneModifier(val code: String, val suffixList: Array<String>)


class App {
	companion object {
		private val reSvgFile = """(.+?)\.svg\z""".toRegex(RegexOption.IGNORE_CASE)
		private val reExtPng = """\.png\z""".toRegex()

		private val skin_tone_modifier = arrayOf(
			SkinToneModifier("1F3FB", arrayOf("_tone1", "_light_skin_tone")),
			SkinToneModifier("1F3FC", arrayOf("_tone2", "_medium_light_skin_tone")),
			SkinToneModifier("1F3FD", arrayOf("_tone3", "_medium_skin_tone")),
			SkinToneModifier("1F3FE", arrayOf("_tone4", "_medium_dark_skin_tone")),
			SkinToneModifier("1F3FF", arrayOf("_tone5", "_dark_skin_tone")),
		)

		private val categoryNameMapping = HashMap<String, Category>().apply {
			fun a(nameLower: String, enumId: String, url: String?) {
				put(nameLower, Category(nameLower, enumId, url))
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

	}

	// resName => Resource
	private val resNameMap = HashMap<String, Resource>()

	// map: code => resName => Resource
	private val codeMap = HashMap<String, HashMap<String, Resource>>()

	// map shortname => resName => Resource
	private val nameMap = HashMap<String, HashMap<String, Resource>>()

	private val pngConverts = ArrayList<Pair<File, File>>()
	private val svgConverts = ArrayList<Pair<File, File>>() // dst,src
	private val mastodonSvg = ArrayList<String>()
	private val twemojiSvg = ArrayList<String>()
	private val overrideSvg = ArrayList<String>()
	private val overridePng = ArrayList<String>()
	private val emojiDataPng = ArrayList<String>()

//	val shortname2unified = HashMap<String, String>()

	private fun mayCopySvg(dst: File, src: File): Boolean {
		if (!src.isFile) return false
		if (!dst.exists()) copyFile(dst, src)
		return true
	}

	private fun mayCopyWebp(dst: File, src: File): Boolean {
		if (!src.isFile) return false
		if (!dst.exists()) pngConverts.add(Pair(dst, src))
		return true
	}


	// returns resName
	private fun getEmojiResId(image: String): String {
		// 小文字で拡張子なし
		val imageLc = image.toLowerCase().replace(reExtPng, "")

		// 画像リソースの名前
		val resName = "emj_$imageLc".replace("-", "_")

		// 出力先ファイル名
		val dstWebp = File("drawable-nodpi/$resName.webp")
		val dstSvg = File("assets/$resName.svg")

		// using override SVG?
		var src = "override/$imageLc.svg"
		if (mayCopySvg(dstSvg, File(src))) {
			overrideSvg.add(src)
			return resName
		}

		// using override PNG?
		src = "override/$imageLc.png"
		if (mayCopyWebp(dstWebp, File(src))) {
			overridePng.add(src)
			return resName
		}

		// using svg from mastodon folder?
		src = "mastodon/public/emoji/$imageLc.svg"
		if (mayCopySvg(dstSvg, File(src))) {
			mastodonSvg.add(src)
			return resName
		}

		// using svg from twemoji?
		src = "twemoji/assets/svg/$imageLc.svg"
		if (mayCopySvg(dstSvg, File(src))) {
			twemojiSvg.add(src)
			return resName
		}

		// using emoji-data PNG?
		src = findEmojiImage(image)
			?: error("emoji-data has no emoji for $image")
		if (mayCopyWebp(dstWebp, File(src))) {
			emojiDataPng.add(src)
			return resName
		}

		error("missing emoji: $imageLc")
	}

	// returns resName
	private fun getEmojiResIdOld(image: String): String {
		// コードポイントに合う画像ファイルがあるか調べる
		val imageFile = File("emojione/assets/png/$image.png")
		if (!imageFile.isFile) {
			error("getEmojiResIdOld: missing. imageFile=${imageFile.path}")
		}

		// 画像リソースの名前
		val resName = "emj_${image.toLowerCase()}".replace("-", "_")

		val dstPathWebp = "drawable-nodpi/$resName.webp"
		mayCopyWebp(File(dstPathWebp), imageFile)

		return resName
	}


	private fun registerResource(
		unified: CodepointList,
		image: String,
		list_code: Iterable<CodepointList>,
		list_name: Iterable<String>,
		has_tone: Boolean = false,
		isToneVariation: Boolean = false
	) {
		val resName = getEmojiResId(image)
		val self = resNameMap.prepare(resName) {
			Resource(
				res_name = resName,
				unified = unified,
				isToneVariation = isToneVariation
			)
		}
		if (self.unified != unified) {
			error("unified not match. res_name=$resName, unified = ")
		}
		self.addCodePoints(list_code)
		self.addShortnames(list_name)
	}

	private fun registerResourceEmojione(
		unified: CodepointList,
		image: String,
		list_code: Iterable<CodepointList>,
		list_name: Iterable<String>,
	) {
		val resName = getEmojiResIdOld(image)
		val self = resNameMap.prepare(resName) {
			Resource(
				res_name = resName,
				unified = unified,
			)
		}
		if (self.unified != unified) {
			error("unified not match. res_name=$resName")
		}
		self.addCodePoints(list_code)
		self.addShortnames(list_name)

	}

	private fun copyImages() {
		log.d("count mastodonSvg=${mastodonSvg.size}")
		log.d("count twemojiSvg =${twemojiSvg.size}")
		log.d("count overrideSvg =${overrideSvg.size}")
		log.d("count overridePng =${overridePng.size}")
		log.d("count emojiDataPng =${emojiDataPng.size}")

		log.d("converting svg... ${svgConverts.size}")
		svgConverts.forEach { pair ->
			val (dst, src) = pair
			svgToVectorDrawable(dst, src)
		}

		log.d("converting png... ${pngConverts.size}")
		pngConverts.forEach { pair ->
			val (dst, src) = pair
			val pb = ProcessBuilder(pathCwebp, src.path, "-quiet", "-o", dst.path)
			val rv = pb.start().waitFor()
			if (rv != 0) error("cwebp failed. dst=$dst src=$src")
		}
	}

	private fun updateCodeMap() {
		codeMap.clear()
		resNameMap.values.forEach { res_info ->
			res_info.codepointMap.keys.forEach { codeKey ->
				codeMap.prepare(codeKey) { HashMap() }[res_info.res_name] = res_info
				codeMap.prepare(codeKey.removeZWJ()) { HashMap() }[res_info.res_name] = res_info
			}
		}
	}

	private fun updateNameMap() {
		nameMap.clear()
		resNameMap.values.forEach { res_info ->
			res_info.shortnames.forEach { name ->
				nameMap.prepare(name) { HashMap() }[res_info.res_name] = res_info
			}
		}
	}


	private fun readEmojiData() {
		File("./emoji-data/emoji.json")
			.readAllBytes()
			.decodeUtf8()
			.decodeJsonArray()
			.objectList()
			.forEach { emoji ->
				// short_name のリスト
				val shortnames = ArrayList<String>().also{ dst->
					emoji.string("short_name")?.parseShortName()?.addTo(dst)
					emoji.stringArrayList("short_names")?.forEach {
						it.parseShortName().addTo(dst)
					}
				}.notEmpty() ?: error("emojiData ${emoji.string("unified")} has no shortName")

				// 絵文字のコードポイント一覧
				val codepoints = ArrayList<CodepointList>().also{ dst->
					emoji.string("unified")?.parseCodePoints()?.addTo(dst)
					emoji.stringArrayList("variations")?.forEach {
						it.parseCodePoints()?.addTo(dst)
					}
					for (k in emojiDataCodepointsVendors) {
						emoji.string(k)?.parseCodePoints()?.addTo(dst)
					}
				}.notEmpty() ?: error("emojiData ${emoji.string("unified")} has no codeponts")

				val shortName = shortnames.first()
				registerResource(
					unified = emoji.string("unified")!!.parseCodePoints()!!,
					image = emoji.string("image").notEmpty()!!,
					list_code = codepoints,
					list_name = shortnames,
					has_tone = emoji["skin_variations"] != null
				)

				// スキントーン
				emoji.jsonObject("skin_variations")?.let { skinVariations ->
					skin_tone_modifier.forEach { mod ->
						val data = skinVariations.jsonObject(mod.code)
						if (data == null) {
							log.w("$shortName : missing skin tone ${mod.code}")
						} else {
							mod.suffixList.forEach { suffix ->
								registerResource(
									unified = data.string("unified")!!.parseCodePoints()!!,
									image = data.string("image").notEmpty()!!,
									list_code = listOf(data.string("unified").notEmpty()!!.parseCodePoints()!!),
									list_name = shortnames.map { it + suffix },
									isToneVariation = true,
								)
							}
						}
					}
				}
			}
	}

	// twemojiのsvgファイルを直接読む
	private fun readTwemoji() {
		File("twemoji/assets/svg").listFiles()?.forEach { file ->
			val name = file.name
			val code = reSvgFile.find(name)?.groupValues?.elementAtOrNull(1)?.toLowerCase()
			if (code == null || codeMap.containsKey(code)) return@forEach
			val unified = code.parseCodePoints()!!
			log.d("twemoji $unified")
			registerResource(
				// FIXME: add shortName
				unified = unified,
				image = code,
				list_code = listOf(unified),
				list_name = emptyList(),
			)
		}
	}

	private fun readOldEmojione() {
		val root = File("./old-emojione.json")
			.readAllBytes()
			.decodeUtf8()
			.decodeJsonObject()

		val oldNames = HashMap<String, JsonObject>()
		val lostCodes = HashMap<String, String>()
		for ( (code,item) in root.entries) {
			if( item !is JsonObject) continue
			item["_code"] = code

			// 名前を集めておく
			val names = ArrayList<String>().also {  item["names"] = it }

			item.string("alpha code")?.parseAlphaCode()?.notEmpty()?.addTo(names)
			item.string("aliases")?.split("|")?.forEach {
				it.parseAlphaCode().notEmpty()?.addTo(names)
			}
			names.forEach { oldNames[it] = item }
			if( names.isEmpty()) error("readOldEmojione: missing name for code $code")

			// コードを確認する
			val code2 = code.removeZWJ()
			val rh = codeMap[code2]
			if (rh != null) {
				for (res_info in rh.values) {
					for (c in arrayOf(code, code2)) {
						res_info.codepointMap[c] = c.parseCodePoints()!!
					}
					names.forEach { it.addTo(res_info.shortnames) }
				}
				continue
			} else {
				// 該当するコードがないので、emojioneの画像を持ってくる
				lostCodes[code] = names.joinToString(",")
				registerResourceEmojione(
					unified = code.parseCodePoints()!!,
					image = code,
					list_code = listOf(code.parseCodePoints()!!),
					list_name = names
				)
			}
		}

		updateCodeMap()
		updateNameMap()
		val lost_names = HashMap<String, String>()
		for ((name, item) in oldNames) {
			if (!nameMap.containsKey(name))
				lost_names[name] = item.string("_code")!!
		}
		for (code in lostCodes.keys.sorted()) {
			log.w("old-emojione: load old emojione code $code ${lostCodes[code]}")
		}
		for (name in lost_names.keys.sorted()) {
			log.w("old-emojione: lost name $name ${lost_names[name]}")
		}
	}


	suspend fun run(){
		HttpClient {
			install(HttpTimeout) {
				val t = 30000L
				requestTimeoutMillis = t
				connectTimeoutMillis = t
				socketTimeoutMillis = t
			}
		}.use { client ->

			// emoji_data のデータを読む
			readEmojiData()
			emojiVariants.forEach { variant ->
				if (variant.used.size > 0)
					log.d("variant: ${variant.dir} ${variant.used.joinToString(",")}…")
			}

			// twemojiにはemoji_dataより多くの絵文字が登録されている
			updateCodeMap()
			updateNameMap()
			readTwemoji()

			// 古いemojioneのデータを読む
			updateCodeMap()
			updateNameMap()
			readOldEmojione()

			// 画像データのコピー
			copyImages()

			// 重複チェック
			val fix_code = ArrayList<Pair<String, String>>()
			val fix_name = ArrayList<Pair<String, String>>()
			val fix_category = ArrayList<Pair<String, String>>()

			val reComment = """#.*""".toRegex()
			val fixFile = "./fix_code.txt"
			File(fixFile).forEachLine { lno, rawLine ->
				val line = rawLine
					.replace(reComment, "")
					.trim()

				val mr = """\A(\w+)\s*(\w+)\s*(.*)""".toRegex().find(line)
				if (mr != null) {
					val type = mr.groupValues[1]
					val key = mr.groupValues[2]
					val data = """([\w+-]+)""".toRegex().findAll(mr.groupValues[3]).map { it.groupValues[1] }.toList()
					if (data.size != 1) return@forEachLine
					when (type) {
						"code" -> Pair(key, data.first()).addTo(fix_code)
						"name" -> Pair(key, data.first()).addTo(fix_name)
						"category" -> Pair(key, data.first()).addTo(fix_category)
						else -> error("$fixFile $lno : bad fix_data type=$type")
					}
				}
			}

			updateCodeMap()
			updateNameMap()

			// あるUnicodeが指す絵文字画像を1種類だけにする
			for ((code, selected_res_name) in fix_code) {
				val rh = codeMap[code]
				if (rh == null) {
					log.w("fix_code: code_map[$code] is null")
					continue
				}

				var found = false
				for ((res_name, res_info) in rh.entries.sortedBy { it.key }) {
					if (res_name == selected_res_name) {
						found = true
					} else {
						log.w("fix_code: remove $code from $res_name")
						res_info.codepointMap.remove(code)
					}
				}
				if (!found) log.w("fix_code: missing relation for $code and $selected_res_name")
			}

			updateCodeMap()
			updateNameMap()

			// あるshortcodeが指す絵文字画像を1種類だけにする
			for ((shortcode, selected_res_name) in fix_name) {
				val rh = nameMap[shortcode]
				if (rh == null) {
					val resInfo = resNameMap[ selected_res_name]
					if(resInfo==null){
						log.w("fix_name: missing both of shortcode=$shortcode,resName=$selected_res_name")
					}else{
						// ないなら追加する
						resInfo.shortnames.add(shortcode)
						if( shortcode.indexOf("_skin_tone") != -1){
							resInfo.isToneVariation = true
						}
					}
				}else{
					var found = false
					for ((res_name, res_info) in rh.entries.sortedBy { it.key }) {
						if (res_name == selected_res_name) {
							found = true
						} else {
							log.w("fix_name: remove $shortcode from $res_name")
							res_info.shortnames.remove(shortcode)
						}
					}
					if (!found) log.w("fix_name: missing relation for $shortcode and $selected_res_name")
				}
			}

			updateCodeMap()
			updateNameMap()

			// 絵文字のショートネームを外部から拾ってくる
			for( url in arrayOf("https://unicode.org/Public/emoji/13.1/emoji-sequences.txt",
				"https://unicode.org/Public/emoji/13.1/emoji-zwj-sequences.txt")
			){
				client.cachedGetString(url,mapOf())
					.split("""[\x0d\x0a]""".toRegex())
					.forEach { rawLine->
					val line = rawLine.replace(reComment,"").trim()
					if(line.isEmpty()) return@forEach
					val cols = line.split(";",limit = 3).map{it.trim()}
					if( cols.size == 3 ){
						val(codeSpec,_,descriptionSpec) = cols
						if(codeSpec.indexOf("..")!=-1) return@forEach

						val shortname = descriptionSpec.toLowerCase()
							.replace("medium-light skin tone","medium_light_skin_tone")
							.replace("medium skin tone","medium_skin_tone")
							.replace("medium-dark skin tone","medium_dark_skin_tone")
							.replace("light skin tone","light_skin_tone")
							.replace("dark skin tone","dark_skin_tone")
							.replace("""[^\w\d]+""".toRegex(),"_")

						val codePoints = codeSpec.parseCodePoints()

						val resInfo = codeMap[codePoints.toString()]?.values?.firstOrNull()
						if( resInfo==null){
							log.w("missing resource for codepoint=${codePoints.toString()} shortname=$shortname")
						}else if( resInfo.shortnames.isEmpty()){
							resInfo.shortnames.add(shortname)
							if( shortname.indexOf("_skin_tone") != -1){
								resInfo.isToneVariation = true
							}
						}
					}
				}
			}

			updateCodeMap()
			updateNameMap()

			val nameChars = HashSet<Char>()
			var nameConflict = false
			for ((name, rh) in nameMap.entries) {
				name.forEach { nameChars.add(it) }
				val resList = rh.values
				if (resList.size != 1) {
					log.w("name $name has multiple resource. ${resList.map { it.res_name }.joinToString(",")}")
					nameConflict = true
				}
			}
			log.w("nameChars: [${nameChars.sorted().joinToString("")}]")
			if (nameConflict) log.e("please fix name=>resource conflicts.")

			for ((code, rh) in codeMap.entries.sortedBy { it.key }) {
				val resList = rh.values
				if (resList.size != 1) {
					log.w("code $code ${
						resList.joinToString(",") {
							it.res_name
						}
					} #  / ${
						resList.joinToString(" / ") {
							"${it.unified} ${it.unified.toRawString()}"
						}
					}")
				}
			}




			categoryNameMapping.values.forEach { category ->
				if( category.url !=null){
					val root = client.cachedGetString(category.url, mapOf()).parseHtml(category.url)
					val list = root.getElementsByClass("emoji-list").first()
					list.getElementsByTag("li").forEach { node ->
						val shortName = node.getElementsByTag("a")!!.attr("href")
							.replace("/", "").parseShortName()
						val span = node.getElementsByClass("emoji").text()
							.toCodePointList().joinToString("-") { String.format("%04x", it) }

						val resInfo = codeMap[span]?.values?.first()
							?: nameMap[shortName]?.values?.firstOrNull()
						if (resInfo == null) {
							log.e("missing ${category.enumId} $shortName $span ")
						} else {
							resInfo.categories.add(category)
							category.shortcodes.add(resInfo.shortnames.first())
						}
					}
				}
			}
			for( (enumId,shortcode) in fix_category) {
				val category = categoryNameMapping.values.find { it.enumId == enumId }
				if (category == null) {
					log.w("fix_category: missing category $enumId")
					continue
				}
				var found = false
				nameMap[shortcode.parseShortName()]?.values?.forEach { resInfo->
					resInfo.categories.add(category)
					category.shortcodes.add(resInfo.shortnames.first())
					found=true
				}
				if(!found){
					// 見つからない場合のみ、画像リソース名でもカテゴリを指定できるようにする
					resNameMap[shortcode]?.let{ resInfo->
						if(resInfo.categories.isEmpty()){
							resInfo.categories.add(category)
							if(resInfo.shortnames.isEmpty()){
								resInfo.shortnames.add( resInfo.res_name)
							}
							category.shortcodes.add( resInfo.shortnames.first())
						}
					}
				}
			}


			for ((res_name, res_info) in resNameMap.entries) {
				if (res_info.isToneVariation) continue
				val shortnames = res_info.shortnames.sorted().joinToString(",")
				if (shortnames.isEmpty()) {
					log.w("missing shortnames for res_name=$res_name")
				}
			}

			val missing = ArrayList<String>()
			for ((_, res_info) in resNameMap.entries) {
				if (res_info.isToneVariation) continue
				val shortnames = res_info.shortnames.sorted().joinToString(",")
				if (shortnames.isNotEmpty() && res_info.categories.isEmpty()) {
					missing.add(shortnames)
				}
			}
			missing.sorted().forEach {
				log.w("missing category: $it")
			}


			// JSONコードを出力する
			val out_file = "EmojiData202102.java"
			PrintWriter(
				OutputStreamWriter(
					BufferedOutputStream(FileOutputStream(File(out_file))),
					Charsets.UTF_8
				)
			).use { stream ->
				val jcw = JavaCodeWriter(stream)

				// 画像リソースIDとUnidoceシーケンスの関連付けを出力する
				for ((res_name, res_info) in resNameMap.entries.sortedBy { it.key }) {
					for ((_, codepoints) in res_info.codepointMap.entries.sortedBy { it.key }) {
						val javaChars = codepoints.makeUtf16()
						if (File("assets/$res_name.svg").isFile) {
							jcw.addCode("code(\"$javaChars\", \"$res_name.svg\");")
						} else {
							jcw.addCode("code(\"$javaChars\", R.drawable.$res_name);")
						}
					}
				}

				// 画像リソースIDとshortcodeの関連付けを出力する
				// 投稿時にshortcodeをユニコードに変換するため、shortcodeとUTF-16シーケンスの関連付けを出力する
				for ((name, rh) in nameMap.entries.sortedBy { it.key }) {
					val resInfo = rh.values.first()
					val utf16Unified = resInfo.unified.makeUtf16()
					jcw.addCode("name(\"$name\", \"$utf16Unified\");")
				}

				categoryNameMapping.values.forEach { category ->
					for( code in category.shortcodes) {
						jcw.addCode("category( ${category.enumId}, \"$code\");")
					}
				}

				jcw.closeFunction()
				jcw.writeDefinition("public static final int utf16_max_length=$utf16_max_length;")
				jcw.writeInitializer()
			}

			log.d("wrote $out_file")


			// shortname => unicode
			JsonArray()
				.also { dst ->
					for ((name, rh) in nameMap.entries.sortedBy { it.key }) {
						val resInfo = rh.values.first()
						dst.add(jsonObject("shortcode" to name, "unicode" to resInfo.unified))
					}
				}
				.toString(2)
				.encodeUtf8()
				.saveTo(File("shortcode-emoji-data-and-old-emojione2.json"))
		}
	}
}

fun main(args: Array<String>) =runBlocking{
	App().run()
}
