package jp.juggler.subwaytooter.emoji

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.jsoup.Jsoup
import java.io.*
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.*

fun String.isTruth() = when {
	this == "" -> false
	this == "0" -> false
	this.startsWith("f", ignoreCase = true) -> false
	this.startsWith("t", ignoreCase = true) -> true
	this == "on" -> true
	else -> true
}

// split CharSequence to Unicode codepoints
fun CharSequence.eachCodePoint(block: (Int) -> Unit) {
	val end = length
	var i = 0
	while (i < end) {
		val c1 = get(i++)
		if (Character.isHighSurrogate(c1) && i < length) {
			val c2 = get(i)
			if (Character.isLowSurrogate(c2)) {
				i++
				block(Character.toCodePoint(c1, c2))
				continue
			}
		}
		block(c1.toInt())
	}
}
// split CharSequence to Unicode codepoints
fun CharSequence.toCodePointList() = ArrayList<Int>().also{ dst->
	val end = length
	var i = 0
	while (i < end) {
		val c1 = get(i++)
		if (Character.isHighSurrogate(c1) && i < length) {
			val c2 = get(i)
			if (Character.isLowSurrogate(c2)) {
				i++
				dst.add(Character.toCodePoint(c1, c2))
				continue
			}
		}
		dst.add(c1.toInt())
	}
}

// split codepoint to UTF-8 bytes
fun codePointToUtf8(cp: Int, block: (Int) -> Unit) {
	// incorrect codepoint
	if (cp < 0 || cp > 0x10FFFF) codePointToUtf8('?'.toInt(), block)

	if (cp >= 128) {
		if (cp >= 2048) {
			if (cp >= 65536) {
				block(0xF0.or(cp.shr(18)))
				block(0x80.or(cp.shr(12).and(0x3f)))
			} else {
				block(0xE0.or(cp.shr(12)))
			}
			block(0x80.or(cp.shr(6).and(0x3f)))
		} else {
			block(0xC0.or(cp.shr(6)))
		}
		block(0x80.or(cp.and(0x3f)))
	} else {
		block(cp)
	}
}

private const val hexString = "0123456789ABCDEF"

private val encodePercentSkipChars by lazy {
	HashSet<Int>().apply {
		('0'..'9').forEach { add(it.toInt()) }
		('A'..'Z').forEach { add(it.toInt()) }
		('a'..'z').forEach { add(it.toInt()) }
		add('-'.toInt())
		add('_'.toInt())
		add('.'.toInt())
	}
}

fun String.encodePercent(): String =
	StringBuilder(length).also { sb ->
		eachCodePoint { cp ->
			if (encodePercentSkipChars.contains(cp)) {
				sb.append(cp.toChar())
			} else {
				codePointToUtf8(cp) { b ->
					sb.append('%')
						.append(hexString[b shr 4])
						.append(hexString[b and 15])
				}
			}
		}
	}.toString()

// same as x?.let{ dst.add(it) }
fun <T> T.addTo(dst: ArrayList<T>) = dst.add(this)
fun <T> T.addTo(dst: HashSet<T>) = dst.add(this)

fun <E : List<*>> E?.notEmpty(): E? =
	if (this?.isNotEmpty() == true) this else null

fun <E : Map<*, *>> E?.notEmpty(): E? =
	if (this?.isNotEmpty() == true) this else null

fun <T : CharSequence> T?.notEmpty(): T? =
	if (this?.isNotEmpty() == true) this else null

fun ByteArray.digestSha256() =
	MessageDigest.getInstance("SHA-256")?.let {
		it.update(this@digestSha256)
		it.digest()
	}!!

fun ByteArray.encodeBase64UrlSafe(): String {
	val bytes = Base64.getUrlEncoder().encode(this)
	return StringBuilder(bytes.size).apply {
		for (b in bytes) {
			val c = b.toChar()
			if (c != '=') append(c)
		}
	}.toString()
}

fun ByteArray.decodeUtf8() = toString(Charsets.UTF_8)
fun String.encodeUtf8() = toByteArray(Charsets.UTF_8)


inline fun <reified T> Any?.castOrThrow(name:String,block: T.() -> Unit){
	if (this !is T) error("type mismatch. $name is ${T::class.qualifiedName}")
	block()
}

// 型推論できる文脈だと型名を書かずにすむ
@Suppress("unused")
inline fun <reified T : Any> Any?.cast(): T? = this as? T

@Suppress("unused")
inline fun <reified T : Any> Any.castNotNull(): T = this as T

fun <T : Comparable<T>> minComparable(a: T, b: T): T = if (a <= b) a else b
fun <T : Comparable<T>> maxComparable(a: T, b: T): T = if (a >= b) a else b

fun <T : Any> MutableCollection<T>.removeFirst(check: (T) -> Boolean): T? {
	val it = iterator()
	while (it.hasNext()) {
		val item = it.next()
		if (check(item)) {
			it.remove()
			return item
		}
	}
	return null
}

fun File.readAllBytes() =
	FileInputStream(this).use { it.readBytes() }

fun File.save(data: ByteArray) {
	val tmpFile = File("$absolutePath.tmp")
	FileOutputStream(tmpFile).use { it.write(data) }
	this.delete()
	if (!tmpFile.renameTo(this)) error("$this: rename failed.")
}

fun ByteArray.saveTo(file: File) = file.save(this)

fun File.forEachLine(charset: Charset = Charsets.UTF_8, block:(Int, String)->Unit)=
	BufferedReader(InputStreamReader(FileInputStream(this),charset)).use { reader ->
		var lno = 0
		reader.forEachLine {
			block(++lno, it)
		}
		lno
	}

inline fun <K,V> HashMap<K,V>.prepare(key:K,creator:()->V):V{
	var value = get(key)
	if( value == null) {
		value = creator()
		put(key,value)
	}
	return value!!
}


private val reFileNameBadChars = """[\\/:*?"<>|-]+""".toRegex()
private val cacheDir by lazy{ File("./cache").apply { mkdirs() }}

fun clearCache(){
	cacheDir.list()?.forEach { name->
		File(cacheDir,name).takeIf { it.isFile }?.delete()
	}
}

private val cacheExpire  by lazy{ 8  * 3600000L }

suspend fun HttpClient.cachedGetBytes(url: String, headers: Map<String, String>): ByteArray {
	val fName = reFileNameBadChars.replace(url, "-")
	val cacheFile = File(cacheDir, fName)
	if (System.currentTimeMillis() - cacheFile.lastModified() <= cacheExpire) {
		println("GET(cached) $url")
		return cacheFile.readAllBytes()
	}
	println("GET $url")

	get<HttpResponse>(url) {
		headers.entries.forEach {
			header(it.key, it.value)
		}
	}.let { res ->
		return when (res.status) {
			HttpStatusCode.OK ->
				res.receive<ByteArray>().also { it.saveTo(cacheFile) }
			else -> {
				cacheFile.delete()
				error("get failed. $url ${res.status}")
			}
		}
	}
}

suspend fun HttpClient.cachedGetString(url: String, headers: Map<String, String>): String =
	cachedGetBytes(url,headers).decodeUtf8()

fun String.parseHtml(baseUri: String) =
	Jsoup.parse(this, baseUri)

