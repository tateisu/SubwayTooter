package jp.juggler.subwaytooter.pref

import android.content.Context
import android.content.SharedPreferences
import jp.juggler.subwaytooter.pref.impl.*

fun Context.pref(): SharedPreferences =
    this.getSharedPreferences(this.packageName + "_preferences", Context.MODE_PRIVATE)

fun SharedPreferences.Editor.remove(item: BasePref<*>): SharedPreferences.Editor {
    item.remove(this)
    return this
}

fun SharedPreferences.Editor.put(item: BooleanPref, v: Boolean) =
    this.apply { item.put(this, v) }

fun SharedPreferences.Editor.put(item: StringPref, v: String) =
    this.apply { item.put(this, v) }

fun SharedPreferences.Editor.put(item: IntPref, v: Int) =
    this.apply { item.put(this, v) }

fun SharedPreferences.Editor.put(item: LongPref, v: Long) =
    this.apply { item.put(this, v) }

fun SharedPreferences.Editor.put(item: FloatPref, v: Float) =
    this.apply { item.put(this, v) }
