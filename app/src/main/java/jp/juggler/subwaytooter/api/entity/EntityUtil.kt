package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.HashMap

object EntityUtil {
	val log = LogCategory("EntityUtil")
}

////////////////////////////////////////

// JSONObjectを渡してEntityを生成するコードのnullチェックと例外補足
inline fun <reified T> parseItem(factory : (src : JSONObject) -> T, src : JSONObject?, log : LogCategory = EntityUtil.log) : T? {
	if(src == null) return null
	return try {
		factory(src)
	} catch(ex : Throwable) {
		log.trace(ex)
		log.e(ex, "${T::class.simpleName} parse failed.")
		null
	}
}

inline fun <reified T> parseList(factory : (src : JSONObject) -> T, src : JSONArray?, log : LogCategory = EntityUtil.log) : ArrayList<T> {
	val dst = ArrayList<T>()
	if(src != null) {
		val src_length = src.length()
		if(src_length > 0) {
			dst.ensureCapacity(src_length)
			for(i in 0 until src.length()) {
				val item = parseItem(factory, src.optJSONObject(i), log)
				if(item != null) dst.add(item)
			}
		}
	}
	return dst
}

inline fun <reified T> parseListOrNull(factory : (src : JSONObject) -> T, src : JSONArray?, log : LogCategory = EntityUtil.log) : ArrayList<T>? {
	if(src != null) {
		val src_length = src.length()
		if(src_length > 0) {
			val dst = ArrayList<T>()
			dst.ensureCapacity(src_length)
			for(i in 0 until src.length()) {
				val item = parseItem(factory, src.optJSONObject(i), log)
				if(item != null) dst.add(item)
			}
			if(dst.isNotEmpty()) return dst
		}
	}
	return null
}

@Suppress("unused")
inline fun <reified K, reified V> parseMap(
	factory : (src : JSONObject) -> V,
	src : JSONArray?,
	log : LogCategory = EntityUtil.log
) : HashMap<K, V> where V : Mappable<K> {
	val dst = HashMap<K, V>()
	if(src != null) {
		val size = src.length()
		for(i in 0 until size) {
			val item = parseItem(factory, src.optJSONObject(i), log)
			if(item != null) dst.put(item.mapKey, item)
		}
	}
	return dst
}

inline fun <reified K, reified V> parseMapOrNull(
	factory : (src : JSONObject) -> V,
	src : JSONArray?,
	log : LogCategory = EntityUtil.log
) : HashMap<K, V>? where V : Mappable<K> {
	if(src != null) {
		val size = src.length()
		if(size > 0) {
			val dst = HashMap<K, V>()
			for(i in 0 until size) {
				val item = parseItem(factory, src.optJSONObject(i), log)
				if(item != null) dst.put(item.mapKey, item)
			}
			if(dst.isNotEmpty()) return dst
		}
	}
	return null
}

////////////////////////////////////////

inline fun <reified T> parseItem(factory : (parser : TootParser, src : JSONObject) -> T, parser : TootParser, src : JSONObject?, log : LogCategory = EntityUtil.log) : T? {
	if(src == null) return null
	return try {
		factory(parser, src)
	} catch(ex : Throwable) {
		log.trace(ex)
		log.e(ex, "${T::class.simpleName} parse failed.")
		null
	}
}

inline fun <reified T> parseList(factory : (parser : TootParser, src : JSONObject) -> T, parser : TootParser, src : JSONArray?, log : LogCategory = EntityUtil.log) : ArrayList<T> {
	val dst = ArrayList<T>()
	if(src != null) {
		val src_length = src.length()
		if(src_length > 0) {
			dst.ensureCapacity(src_length)
			for(i in 0 until src.length()) {
				val item = parseItem(factory, parser, src.optJSONObject(i), log)
				if(item != null) dst.add(item)
			}
		}
	}
	return dst
}

@Suppress("unused")
inline fun <reified T> parseListOrNull(factory : (parser : TootParser, src : JSONObject) -> T, parser : TootParser, src : JSONArray?, log : LogCategory = EntityUtil.log) : ArrayList<T>? {
	if(src != null) {
		val src_length = src.length()
		if(src_length > 0) {
			val dst = ArrayList<T>()
			dst.ensureCapacity(src_length)
			for(i in 0 until src.length()) {
				val item = parseItem(factory, parser, src.optJSONObject(i), log)
				if(item != null) dst.add(item)
			}
			if(dst.isNotEmpty()) return dst
		}
	}
	return null
}

////////////////////////////////////////

fun ArrayList<TootAttachmentLike>.encodeJson() : JSONArray {
	val a = JSONArray()
	for(ta in this) {
		if(ta is TootAttachment) {
			try {
				a.put(ta.json)
			} catch(ex : JSONException) {
				EntityUtil.log.e(ex, "encode failed.")
			}
		}
	}
	return a
}

////////////////////////////////////////

fun notEmptyOrThrow(name : String, value : String?) : String {
	return if(value?.isNotEmpty() == true) value else throw RuntimeException("$name is empty")
}

fun JSONObject.notEmptyOrThrow(name : String) : String {
	return notEmptyOrThrow(name, Utils.optStringX(this, name))
}
