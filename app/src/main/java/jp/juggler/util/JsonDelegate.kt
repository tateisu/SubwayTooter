package jp.juggler.util

import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation

private interface JsonAnnotationBase :Annotation
annotation class JsonPropInt(val key:String,val defVal:Int)
annotation class JsonPropString(val key:String,val defVal:String)
annotation class JsonPropBoolean(val key:String,val defVal:Boolean)

class JsonDelegate<T>(val parent: JsonDelegates)

class JsonDelegates(val src: JsonObject){
    val int = JsonDelegate<Int>(this)
    val string = JsonDelegate<String>(this)
    val boolean = JsonDelegate<Boolean>(this)
}

private fun getMetaString(property: KProperty<*>) =
    property.findAnnotation<JsonPropString>()
        ?:  error("${property.name}, required=String, defined=(missing)")

operator fun JsonDelegate<String>.getValue(thisRef: Any?, property: KProperty<*>): String =
    getMetaString(property).let { parent.src.string(it.key) ?: it.defVal }

operator fun JsonDelegate<String>.setValue(thisRef: Any?, property: KProperty<*>, value: String) {
    getMetaString(property).let { parent.src[it.key] = value }
}

private fun getMetaInt(property: KProperty<*>) =
    property.findAnnotation<JsonPropInt>()
        ?:  error("${property.name}, required=Int, defined=(missing)")

operator fun JsonDelegate<Int>.getValue(thisRef: Any?, property: KProperty<*>): Int =
    getMetaInt(property).let { parent.src.int(it.key) ?: it.defVal }

operator fun JsonDelegate<Int>.setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
    getMetaInt(property).let { parent.src[it.key] = value }
}

private fun getMetaBoolean(property: KProperty<*>) =
    property.findAnnotation<JsonPropBoolean>()
        ?:  error("${property.name}, required=Boolean, defined=(missing)")

operator fun JsonDelegate<Boolean>.getValue(thisRef: Any?, property: KProperty<*>): Boolean =
    getMetaBoolean(property).let { parent.src.boolean(it.key) ?: it.defVal }

operator fun JsonDelegate<Boolean>.setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
    getMetaBoolean(property).let { parent.src[it.key] = value }
}
