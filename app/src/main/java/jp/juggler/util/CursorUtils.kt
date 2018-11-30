package jp.juggler.util

import android.database.Cursor

fun Cursor.getInt(key : String) =
	getInt(getColumnIndex(key))

fun Cursor.getIntOrNull(idx : Int) =
	if(isNull(idx)) null else getInt(idx)

fun Cursor.getIntOrNull(key : String) =
	getIntOrNull(getColumnIndex(key))

fun Cursor.getLong(key : String) =
	getLong(getColumnIndex(key))

//fun Cursor.getLongOrNull(idx:Int) =
//	if(isNull(idx)) null else getLong(idx)

//fun Cursor.getLongOrNull(key:String) =
//	getLongOrNull(getColumnIndex(key))

fun Cursor.getString(key : String) : String =
	getString(getColumnIndex(key))

fun Cursor.getStringOrNull(keyIdx : Int) =
	if(isNull(keyIdx)) null else getString(keyIdx)

fun Cursor.getStringOrNull(key : String) =
	getStringOrNull(getColumnIndex(key))
