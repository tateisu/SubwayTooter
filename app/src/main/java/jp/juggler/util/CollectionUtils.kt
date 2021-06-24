package jp.juggler.util

import java.util.LinkedHashMap

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
