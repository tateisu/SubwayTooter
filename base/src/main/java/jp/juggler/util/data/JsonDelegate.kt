package jp.juggler.util.data

import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation

/*
    JsonObjectにデータを格納する委譲プロパティの実装

    - キー名とデフォルト値をアノテーションで指定できる
    - プロパティ数が増えてもメモリ消費が増えない

    usage:
    class SampleEntity{
        var json = JsonObject()
        private val delegates = JsonDelegates{ json }

        @JsonPropString(key="key1",defVal="")
        val prop1 by delegates.string

        @JsonPropBoolean(key="key2",defVal=false)
        val prop2 by delegates.boolean

        @JsonPropInt(key="key3",defVal=0)
        val prop3 by delegates.int
    }
 */

// プロパティの移乗先を提供する。JSON取得ラムダを保持する。
class JsonDelegates(val getJson: () -> JsonObject) {
    val int = JsonDelegate<Int>(this)
    val string = JsonDelegate<String>(this)
    val boolean = JsonDelegate<Boolean>(this)
}

class JsonDelegate<T>(val parent: JsonDelegates)

// 委譲プロパティにつけるアノテーション
annotation class JsonPropInt(val key: String, val defVal: Int)
annotation class JsonPropString(val key: String, val defVal: String)
annotation class JsonPropBoolean(val key: String, val defVal: Boolean)

// 委譲プロパティにつけたアノテーションを取得する
private fun KProperty<*>.stringAnnotation() =
    findAnnotation<JsonPropString>()
        ?: error("$name, required=String, defined=(missing)")

private fun KProperty<*>.intAnnotation() =
    findAnnotation<JsonPropInt>()
        ?: error("$name, required=Int, defined=(missing)")

private fun KProperty<*>.booleanAnnotation() =
    findAnnotation<JsonPropBoolean>()
        ?: error("$name, required=Boolean, defined=(missing)")

// getter
operator fun JsonDelegate<String>.getValue(thisRef: Any?, property: KProperty<*>): String =
    property.stringAnnotation().run { parent.getJson().string(key) ?: defVal }

operator fun JsonDelegate<Int>.getValue(thisRef: Any?, property: KProperty<*>): Int =
    property.intAnnotation().run { parent.getJson().int(key) ?: defVal }

operator fun JsonDelegate<Boolean>.getValue(thisRef: Any?, property: KProperty<*>): Boolean =
    property.booleanAnnotation().run { parent.getJson().boolean(key) ?: defVal }

// setter
operator fun JsonDelegate<String>.setValue(thisRef: Any?, property: KProperty<*>, value: String) {
    property.stringAnnotation().run { parent.getJson()[key] = value }
}

operator fun JsonDelegate<Int>.setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
    property.intAnnotation().run { parent.getJson()[key] = value }
}

operator fun JsonDelegate<Boolean>.setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
    property.booleanAnnotation().run { parent.getJson()[key] = value }
}
