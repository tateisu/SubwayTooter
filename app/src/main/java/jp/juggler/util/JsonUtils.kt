package jp.juggler.util

import jp.juggler.subwaytooter.api.TootApiClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject

fun removeJsonNull(o : Any?) = if(JSONObject.NULL === o) null else o

// for in でループを回せるようにする。
// インライン展開できずIteratorを生成
fun JSONArray.iterator() : Iterator<Any?> {
	val self = this
	val end = length()
	return object : Iterator<Any?> {
		var i = 0
		override fun hasNext() : Boolean = i < end
		override fun next() : Any? = self.opt(i ++)
	}
}

fun JSONArray.reverseIterator() : Iterator<Any?> {
	val self = this
	return object : Iterator<Any?> {
		var i = length() - 1
		override fun hasNext() : Boolean = i >= 0
		override fun next() : Any? = self.opt(i --)
	}
}

inline fun JSONArray.forEach(block : (v : Any?) -> Unit) {
	val e = this.length()
	var i = 0
	while(i < e) {
		block(removeJsonNull(this.opt(i)))
		++ i
	}
}

inline fun JSONArray.forEachIndexed(block : (i : Int, v : Any?) -> Unit) {
	val e = this.length()
	var i = 0
	while(i < e) {
		block(i, removeJsonNull(this.opt(i)))
		++ i
	}
}

inline fun JSONArray.downForEach(block : (v : Any?) -> Unit) {
	var i = this.length() - 1
	while(i >= 0) {
		block(removeJsonNull(this.opt(i)))
		-- i
	}
}

inline fun JSONArray.downForEachIndexed(block : (i : Int, v : Any?) -> Unit) {
	var i = this.length() - 1
	while(i >= 0) {
		block(i, removeJsonNull(this.opt(i)))
		-- i
	}
}

//fun JSONArray.toAnyList() : ArrayList<Any> {
//	val dst_list = ArrayList<Any>(length())
//	forEach { if(it != null) dst_list.add(it) }
//	return dst_list
//}

fun JSONArray.toObjectList() : ArrayList<JSONObject> {
	val dst_list = ArrayList<JSONObject>(length())
	forEach { if(it is JSONObject) dst_list.add(it) }
	return dst_list
}

fun List<JSONObject>.toJsonArray() : JSONArray {
	val dst_list = JSONArray()
	forEach { dst_list.put(it) }
	return dst_list
}

fun JSONArray.toStringArrayList() : ArrayList<String> {
	val dst_list = ArrayList<String>(length())
	forEach { o ->
		val sv = o?.toString()
		if(sv != null) dst_list.add(sv)
	}
	return dst_list
}

fun JSONObject.parseStringArrayList(name : String) : ArrayList<String>? {
	val array = optJSONArray(name)
	if(array != null) {
		val dst = array.toStringArrayList()
		if(dst.isNotEmpty()) return dst
	}
	return null
}

fun JSONObject.parseFloatArrayList(name : String) : ArrayList<Float>? {
	val array = optJSONArray(name)
	if(array != null) {
		val size = array.length()
		val dst = ArrayList<Float>(size)
		for(i in 0 until size) {
			val dv = array.optDouble(i)
			dst.add(dv.toFloat())
		}
		if(dst.isNotEmpty()) return dst
	}
	return null
}

fun String.toJsonObject() = JSONObject(this)
fun String.toJsonArray() = JSONArray(this)

fun JSONObject.parseString(key : String) : String? {
	val o = this.opt(key)
	return if(o == null || o == JSONObject.NULL) null else o.toString()
}

fun JSONArray.parseString(key : Int) : String? {
	val o = this.opt(key)
	return if(o == null || o == JSONObject.NULL) null else o.toString()
}

fun notEmptyOrThrow(name : String, value : String?) =
	if(value?.isNotEmpty() == true) value else throw RuntimeException("$name is empty")

fun JSONObject.notEmptyOrThrow(name : String) =
	notEmptyOrThrow(name, this.parseString(name))

// 文字列データをLong精度で取得できる代替品
// (JsonObject.optLong はLong精度が出ない)
fun JSONObject.parseLong(key : String) : Long? {
	val o = this.opt(key)
	return when(o) {
		is Long -> return o
		is Number -> return o.toLong()
		
		is String -> {
			if(o.indexOf('.') == - 1 && o.indexOf(',') == - 1) {
				try {
					return o.toLong(10)
				} catch(ignored : NumberFormatException) {
				}
			}
			try {
				o.toDouble().toLong()
			} catch(ignored : NumberFormatException) {
				null
			}
		}
		
		else -> null // may null or JSONObject.NULL or object,array,boolean
	}
}

fun JSONObject.parseInt(key : String) : Int? {
	val o = this.opt(key)
	return when(o) {
		is Int -> return o
		
		is Number -> return try {
			o.toInt()
		} catch(ignored : NumberFormatException) {
			null
		}
		
		is String -> {
			if(o.indexOf('.') == - 1 && o.indexOf(',') == - 1) {
				try {
					return o.toInt(10)
				} catch(ignored : NumberFormatException) {
				}
			}
			try {
				o.toDouble().toInt()
			} catch(ignored : NumberFormatException) {
				null
			}
		}
		
		else -> null // may null or JSONObject.NULL or object,array,boolean
	}
}

fun JSONObject.toPostRequestBuilder() : Request.Builder =
	Request.Builder().post(RequestBody.create(TootApiClient.MEDIA_TYPE_JSON, this.toString()))

fun jsonObject(initializer : JSONObject.() -> Unit) : JSONObject {
	val dst = JSONObject()
	dst.initializer()
	return dst
}
