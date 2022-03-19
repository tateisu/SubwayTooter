package jp.juggler.util

import kotlin.reflect.KProperty

class JsonProperty<ValueType>(
    val src: JsonObject,
    val key: String,
    val defVal: ValueType,
)

operator fun JsonProperty<String>.getValue(thisRef: Any?, property: KProperty<*>): String {
    return src.string(key) ?: defVal
}

operator fun JsonProperty<String>.setValue(thisRef: Any?, property: KProperty<*>, value: String) {
    src[key] = value
}

operator fun JsonProperty<Int>.getValue(thisRef: Any?, property: KProperty<*>): Int {
    return src.int(key) ?: defVal
}

operator fun JsonProperty<Int>.setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
    src[key] = value
}
operator fun JsonProperty<Boolean>.getValue(thisRef: Any?, property: KProperty<*>): Boolean {
    return src.boolean(key) ?: defVal
}

operator fun JsonProperty<Boolean>.setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
    src[key] = value
}
