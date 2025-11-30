package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.data.JsonArray
import jp.juggler.util.data.JsonException
import jp.juggler.util.data.JsonObject
import jp.juggler.util.log.LogCategory

object EntityUtil {
    val log = LogCategory("EntityUtil")
}

////////////////////////////////////////

// creator()を呼び出して例外チェックを行う
inline fun <reified T> parseItem(
    creator: () -> T?,
): T? = try {
    creator()
} catch (ex: Throwable) {
    EntityUtil.log.e(ex, "parseItem failed. ${T::class.simpleName}")
    null
}

inline fun <P1 : Any, reified T> parseItem(
    p1: P1?,
    creator: (P1) -> T?,
): T? = try {
    p1?.let { creator(it) }
} catch (ex: Throwable) {
    EntityUtil.log.e(ex, "parseItemP1 failed. ${T::class.simpleName}")
    null
}

// creator(JsonObject)を呼び出して例外チェックを行う
inline fun <reified T> parseList(
    src: JsonArray?,
    creator: (JsonObject) -> T,
): ArrayList<T> {
    val dstList = ArrayList<T>()
    if (src != null) {
        val srcSize = src.size
        for (i in 0 until srcSize) {
            try {
                val dst = src.jsonObject(i)?.let { creator(it) }
                    ?: continue
                dstList.add(dst)
            } catch (ex: Throwable) {
                EntityUtil.log.w("parseList failed. ${T::class.simpleName}")
            }
        }
    }
    return dstList
}

inline fun <reified T> parseListOrNull(
    src: JsonArray?,
    creator: (JsonObject) -> T?,
): ArrayList<T>? {
    var dstList: ArrayList<T>? = null
    if (src != null) {
        val srcSize = src.size
        for (i in src.indices) {
            try {
                val dst = src.jsonObject(i)?.let { creator(it) }
                    ?: continue
                (dstList ?: ArrayList<T>(srcSize).also { dstList = it }).add(dst)
            } catch (ex: Throwable) {
                EntityUtil.log.w("parseListOrNull failed. ${T::class.simpleName}")
            }
        }
    }
    return dstList
}

@Suppress("unused")
inline fun <reified K, reified V> parseMap(
    src: JsonArray?,
    creator: (JsonObject) -> V?,
): HashMap<K, V> where V : Mappable<K> {
    val dstMap = HashMap<K, V>()
    if (src != null) {
        for (i in src.indices) {
            try {
                val dst = src.jsonObject(i)?.let { creator(it) } ?: continue
                dstMap[dst.mapKey] = dst
            } catch (ex: Throwable) {
                EntityUtil.log.w("parseMap failed. ${V::class.simpleName}")
            }
        }
    }
    return dstMap
}

inline fun <reified K, reified V> parseMapOrNull(
    src: JsonArray?,
    creator: (src: JsonObject) -> V?,
): HashMap<K, V>? where V : Mappable<K> {
    var dstMap: HashMap<K, V>? = null
    if (src != null) {
        for (i in src.indices) {
            try {
                val dst = src.jsonObject(i)?.let { creator(it) } ?: continue
                (dstMap ?: HashMap<K, V>().also { dstMap = it })[dst.mapKey] = dst
            } catch (ex: Throwable) {
                EntityUtil.log.w("parseMapOrNull failed. ${V::class.simpleName}")
            }
        }
    }
    return dstMap
}

inline fun <reified V> parseProfileEmoji2(
    srcMap: JsonObject?,
    creator: (src: JsonObject, shortcode: String) -> V,
): HashMap<String, V>? {
    var dstMap: HashMap<String, V>? = null
    if (srcMap != null) {
        for (key in srcMap.keys) {
            try {
                val dst = srcMap.jsonObject(key)
                    ?.let { creator(it, key) }
                    ?: continue
                (dstMap ?: HashMap<String, V>().also { dstMap = it })[key] = dst
            } catch (ex: Throwable) {
                EntityUtil.log.w("parseProfileEmoji2 failed. ${V::class.simpleName}")
            }
        }
    }
    return dstMap
}

// 添付データのJSON表現のリストを作る
fun <T : TootAttachmentLike> ArrayList<T>.encodeJson(): JsonArray {
    val a = JsonArray()
    forEach { ta ->
        if (ta !is TootAttachment) return@forEach
        try {
            a.add(ta.encodeJson())
        } catch (ex: JsonException) {
            EntityUtil.log.e(ex, "encode failed.")
        }
    }
    return a
}
