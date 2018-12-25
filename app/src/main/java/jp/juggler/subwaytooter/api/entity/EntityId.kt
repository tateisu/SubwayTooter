package jp.juggler.subwaytooter.api.entity

import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import jp.juggler.util.getStringOrNull
import org.json.JSONObject

abstract class EntityId : Comparable<EntityId> {
	
	abstract fun toLong() : Long
	abstract fun putMisskeyUntil(dst : JSONObject) : JSONObject
	abstract fun putMisskeySince(dst : JSONObject) : JSONObject
	
	companion object {
		
		const val INVALID_ID_LONG = - 1L
		
		// マストドンのアカウント作成APIで作成した直後は「IDが発行されてない」状態になる
		const val CONFIRMING_ID_LONG = - 2L
		
		val defaultLong = EntityIdLong(INVALID_ID_LONG)
		val defaultString = EntityIdString("")
		
		fun mayDefault(x : Long?) = when(x) {
			null -> defaultLong
			else -> EntityIdLong(x)
		}
		
		fun mayDefault(x : String?) = when(x) {
			null -> defaultString
			else -> EntityIdString(x)
		}
		
		fun mayNull(x : Long?) = when(x) {
			null -> null
			else -> EntityIdLong(x)
		}
		
		fun mayNull(x : String?) = when(x) {
			null -> null
			else -> EntityIdString(x)
		}
		
		fun String.decode() : EntityId? {
			if(this.isEmpty()) return null
			if(this[0] == 'L') return EntityIdLong(this.substring(1).toLong())
			if(this[0] == 'S') return EntityIdString(this.substring(1))
			return null
		}
		
		fun from(intent : Intent?, key : String) =
			intent?.getStringExtra(key)?.decode()
		
		fun from(bundle : Bundle?, key : String) =
			bundle?.getString(key)?.decode()
		
		// 内部保存データのデコード用。APIレスポンスのパースに使ってはいけない
		fun from(data : JSONObject?, key : String) : EntityId? {
			val o = data?.opt(key)
			if(o is Long) return EntityIdLong(o)
			return (o as? String)?.decode()
		}
		
		fun from(cursor : Cursor, key : String) =
			cursor.getStringOrNull(key)?.decode()
	}
	
	private fun encode() : String {
		val prefix = when(this) {
			is EntityIdLong -> 'L'
			is EntityIdString -> 'S'
			else -> error("unknown type")
		}
		return "$prefix$this"
	}
	
	fun putTo(data : Intent, key : String) : Intent = data.putExtra(key, encode())
	
	fun putTo(bundle : Bundle, key : String) = bundle.putString(key, encode())
	
	fun putTo(data : JSONObject, key : String) : JSONObject = data.put(key, encode())
	
	fun putTo(cv : ContentValues, key : String) {
		cv.put(key, encode())
	}
	
	val isDefault : Boolean
		get() = when(this) {
			is EntityIdLong -> this == defaultLong
			is EntityIdString -> this == defaultString
			else -> false
		}
	
	val notDefault : Boolean
		get() = ! isDefault
}

fun EntityId?.putMayNull(cv : ContentValues, key : String) {
	if(this == null) {
		cv.putNull(key)
	} else {
		this.putTo(cv, key)
	}
}

class EntityIdLong(val x : Long) : EntityId() {
	
	override fun compareTo(other : EntityId) = when(other) {
		this -> 0
		is EntityIdLong -> x.compareTo(other.x)
		else -> error("EntityIdLong: compare with ${other::javaClass.name}")
	}
	
	override fun equals(other : Any?) = when(other) {
		is EntityIdLong -> x == other.x
		is EntityIdString -> x.toString() == other.x
		else -> false
	}
	
	override fun hashCode() = (x xor x.ushr(32)).toInt()
	
	override fun toString() = x.toString()
	
	override fun toLong() = x
	
	override fun putMisskeyUntil(dst : JSONObject) : JSONObject = dst.put("untilDate", x)
	override fun putMisskeySince(dst : JSONObject) : JSONObject = dst.put("sinceDate", x)
	
}

class EntityIdString(val x : String) : EntityId() {
	
	override fun compareTo(other : EntityId) = when(other) {
		is EntityIdString -> x.compareTo(other.x)
		else -> error("EntityIdLong: compare with ${other::class.java.simpleName}")
	}
	
	override fun equals(other : Any?) = when(other) {
		is EntityIdString -> x == other.x
		is EntityIdLong -> x == other.x.toString()
		else -> false
	}
	
	override fun hashCode() = x.hashCode()
	
	override fun toString() = x
	
	override fun toLong() = EntityId.INVALID_ID_LONG // error("can't convert string ID to long")
	
	override fun putMisskeyUntil(dst : JSONObject) : JSONObject = dst.put("untilId", x)
	override fun putMisskeySince(dst : JSONObject) : JSONObject = dst.put("sinceId", x)
	
}
