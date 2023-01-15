package jp.juggler.util.data

// same as x?.let{ dst.add(it) }
fun <T> T.addTo(dst: ArrayList<T>) = dst.add(this)

fun <E : List<*>> E?.notEmpty(): E? =
    if (this?.isNotEmpty() == true) this else null

fun <E : Map<*, *>> E?.notEmpty(): E? =
    if (this?.isNotEmpty() == true) this else null

fun ByteArray?.notEmpty(): ByteArray? =
    if (this?.isNotEmpty() == true) this else null

fun <K, V : Any?> Iterable<Pair<K, V>>.toMutableMap() =
    LinkedHashMap<K, V>().also { map -> forEach { map[it.first] = it.second } }

// 配列中の要素をラムダ式で変換して、戻り値が非nullならそこで処理を打ち切る
inline fun <T, V> Array<out T>.firstNonNull(predicate: (T) -> V?): V? {
    for (element in this) return predicate(element) ?: continue
    return null
}

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
