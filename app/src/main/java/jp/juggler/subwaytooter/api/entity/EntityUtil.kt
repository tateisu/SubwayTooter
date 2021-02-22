package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.util.JsonArray
import jp.juggler.util.JsonException
import jp.juggler.util.JsonObject
import jp.juggler.util.LogCategory
import java.util.HashMap

object EntityUtil {
	val log = LogCategory("EntityUtil")
}

////////////////////////////////////////

// JSONObjectを渡してEntityを生成するコードのnullチェックと例外補足
inline fun <reified T> parseItem(
	factory : (src : JsonObject) -> T,
	src : JsonObject?,
	log : LogCategory = EntityUtil.log
) : T? {
	if(src == null) return null
	return try {
		factory(src)
	} catch(ex : Throwable) {
		log.trace(ex)
		log.e(ex, "${T::class.simpleName} parse failed.")
		null
	}
}

inline fun <reified T > parseList(
	factory : (src : JsonObject) -> T,
	src : JsonArray?,
	log : LogCategory = EntityUtil.log
) : ArrayList<T> {
	val dst = ArrayList<T>()
	if(src != null) {
		val src_length = src.size
		if(src_length > 0) {
			dst.ensureCapacity(src_length)
			for(i in 0 until src_length) {
				val item = parseItem(factory, src.jsonObject(i), log)
				if(item != null) dst.add(item)
			}
		}
	}
	return dst
}

inline fun <S,reified T> parseList(
	factory : (serviceType : S, src : JsonObject) -> T,
	serviceType : S,
	src : JsonArray?,
	log : LogCategory = EntityUtil.log
) : ArrayList<T> {
	val dst = ArrayList<T>()
	if(src != null) {
		val src_length = src.size
		if(src_length > 0) {
			dst.ensureCapacity(src_length)
			for(i in 0 until src_length) {
				val item = parseItem(factory, serviceType, src.jsonObject(i), log)
				if(item != null) dst.add(item)
			}
		}
	}
	return dst
}

inline fun <reified T> parseListOrNull(
	factory : (src : JsonObject) -> T,
	src : JsonArray?,
	log : LogCategory = EntityUtil.log
) : ArrayList<T>? {
	if(src != null) {
		val src_length = src.size
		if(src_length > 0) {
			val dst = ArrayList<T>(src_length)
			for(i in 0 until src.size) {
				val item = parseItem(factory, src.jsonObject(i), log)
				if(item != null) dst.add(item)
			}
			if(dst.isNotEmpty()) return dst
		}
	}
	return null
}

@Suppress("unused")
inline fun <reified K, reified V> parseMap(
	factory : (src : JsonObject) -> V,
	src : JsonArray?,
	log : LogCategory = EntityUtil.log
) : HashMap<K, V> where V : Mappable<K> {
	val dst = HashMap<K, V>()
	if(src != null) {
		for(i in src.indices ) {
			val item = parseItem(factory, src.jsonObject(i), log)
			if(item != null) dst[item.mapKey] = item
		}
	}
	return dst
}

inline fun <reified K, reified V> parseMapOrNull(
	factory : (src : JsonObject) -> V,
	src : JsonArray?,
	log : LogCategory = EntityUtil.log
) : HashMap<K, V>? where V : Mappable<K> {
	if(src != null) {
		val size = src.size
		if(size > 0) {
			val dst = HashMap<K, V>()
			for(i in 0 until size) {
				val item = parseItem(factory, src.jsonObject(i), log)
				if(item != null) dst[item.mapKey] = item
			}
			if(dst.isNotEmpty()) return dst
		}
	}
	return null
}
inline fun <reified K, reified V> parseMapOrNull(
	factory : (host:Host, src : JsonObject) -> V,
	host: Host,
	src : JsonArray?,
	log : LogCategory = EntityUtil.log
) : HashMap<K, V>? where V : Mappable<K> {
	if(src != null) {
		val size = src.size
		if(size > 0) {
			val dst = HashMap<K, V>()
			for(i in 0 until size) {
				val item = parseItem(factory, host, src.jsonObject(i), log)
				if(item != null) dst[item.mapKey] = item
			}
			if(dst.isNotEmpty()) return dst
		}
	}
	return null
}

inline fun <reified V> parseProfileEmoji2(
	factory : (src : JsonObject,shortcode:String) -> V,
	src : JsonObject?,
	log : LogCategory = EntityUtil.log
) : HashMap<String, V>? {
	if(src != null) {
		val size = src.size
		if(size > 0) {
			val dst = HashMap<String, V>()
			for( key in src.keys){
				val v = src.jsonObject(key) ?: continue
				val item = try{
					factory(v,key)
				} catch(ex : Throwable) {
					log.trace(ex)
					log.e(ex, "parseProfileEmoji2 failed.")
					null
				}
				if(item != null) dst[key] = item
			}
			if(dst.isNotEmpty()) return dst
		}
	}
	return null
}

////////////////////////////////////////

inline fun <P,reified T> parseItem(
	factory : (parser : P, src : JsonObject) -> T,
	parser : P,
	src : JsonObject?,
	log : LogCategory = EntityUtil.log
) : T? {
	if(src == null) return null
	return try {
		factory(parser, src)
	} catch(ex : Throwable) {
		log.trace(ex)
		log.e(ex, "${T::class.simpleName} parse failed.")
		null
	}
}

inline fun <reified T> parseItem(
	factory : (serviceType : ServiceType, src : JsonObject) -> T,
	serviceType : ServiceType,
	src : JsonObject?,
	log : LogCategory = EntityUtil.log
) : T? {
	if(src == null) return null
	return try {
		factory(serviceType, src)
	} catch(ex : Throwable) {
		log.trace(ex)
		log.e(ex, "${T::class.simpleName} parse failed.")
		null
	}
}

inline fun <reified T> parseList(
	factory : (parser : TootParser, src : JsonObject) -> T,
	parser : TootParser,
	src : JsonArray?,
	log : LogCategory = EntityUtil.log
) : ArrayList<T> {
	val dst = ArrayList<T>()
	if(src != null) {
		val src_length = src.size
		if(src_length > 0) {
			dst.ensureCapacity(src_length)
			for(i in src.indices) {
				val item = parseItem(factory, parser, src.jsonObject(i), log)
				if(item != null) dst.add(item)
			}
		}
	}
	return dst
}

@Suppress("unused")
inline fun <reified T> parseListOrNull(
	factory : (parser : TootParser, src : JsonObject) -> T,
	parser : TootParser,
	src : JsonArray?,
	log : LogCategory = EntityUtil.log
) : ArrayList<T>? {
	if(src != null) {
		val src_length = src.size
		if(src_length > 0) {
			val dst = ArrayList<T>()
			dst.ensureCapacity(src_length)
			for(i in src.indices) {
				val item = parseItem(factory, parser, src.jsonObject(i), log)
				if(item != null) dst.add(item)
			}
			if(dst.isNotEmpty()) return dst
		}
	}
	return null
}

////////////////////////////////////////

fun <T : TootAttachmentLike> ArrayList<T>.encodeJson() : JsonArray {
	val a = JsonArray()
	forEach { ta->
		if(ta !is TootAttachment) return@forEach
		try {
			a.add(ta.encodeJson())
		} catch(ex : JsonException) {
			EntityUtil.log.e(ex, "encode failed.")
		}
	}
	return a
}
