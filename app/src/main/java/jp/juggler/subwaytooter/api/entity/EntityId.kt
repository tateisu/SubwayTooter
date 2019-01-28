package jp.juggler.subwaytooter.api.entity

import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import jp.juggler.util.getStringOrNull
import jp.juggler.util.notZero
import org.json.JSONObject

abstract class EntityId : Comparable<EntityId> {
	
	companion object {
		
		const val INVALID_ID_LONG = - 1L
		
		const val CONFIRMING_ID_LONG = - 2L
		
		// val defaultLong = EntityIdLong(INVALID_ID_LONG)
		val defaultString = EntityIdString("")
		
		// マストドンのアカウント作成APIで作成した直後は「IDが発行されてない」状態になる
		val confirmingId =  EntityIdString("-2")
		
//		fun mayDefault(x : Long?) = when(x) {
//			null -> defaultLong
//			else -> EntityIdLong(x)
//		}
		
		fun mayDefault(x : String?) = when(x) {
			null -> defaultString
			else -> EntityIdString(x)
		}
		
//		fun mayNull(x : Long?) = when(x) {
//			null -> null
//			else -> EntityIdLong(x)
//		}
		
		fun mayNull(x : String?) = when(x) {
			null -> null
			else -> EntityIdString(x)
		}
		
		fun String.decode() : EntityId? {
			if(this.isEmpty()) return null
			// first character is 'L' for EntityIdLong, 'S' for EntityIdString.
			// integer id is removed at https://source.joinmastodon.org/mastodon/docs/commit/e086d478afa140e7b0b9a60183655315966ad9ff
			return EntityIdString(this.substring(1))
		}
		
		fun from(intent : Intent?, key : String) =
			intent?.getStringExtra(key)?.decode()
		
		fun from(bundle : Bundle?, key : String) =
			bundle?.getString(key)?.decode()
		
		// 内部保存データのデコード用。APIレスポンスのパースに使ってはいけない
		fun from(data : JSONObject?, key : String) : EntityId? {
			val o = data?.opt(key)
			if(o is Long) return EntityIdString(o.toString())
			return (o as? String)?.decode()
		}
		
		fun from(cursor : Cursor, key : String) =
			cursor.getStringOrNull(key)?.decode()
	}
	
	private fun encode() : String {
		val prefix = when(this) {
			// integer id is removed at https://source.joinmastodon.org/mastodon/docs/commit/e086d478afa140e7b0b9a60183655315966ad9ff
			//	is EntityIdLong -> 'L'
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
			// integer id is removed at https://source.joinmastodon.org/mastodon/docs/commit/e086d478afa140e7b0b9a60183655315966ad9ff
			// is EntityIdLong -> this == defaultLong
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

//class EntityIdLong(val x : Long) : EntityId() {
//
//	override fun compareTo(other : EntityId) = when(other) {
//		this -> 0
//		is EntityIdLong -> x.compareTo(other.x)
//		else -> error("EntityIdLong: compare with ${other::javaClass.name}")
//	}
//
//	override fun equals(other : Any?) = when(other) {
//		is EntityIdLong -> x == other.x
//		is EntityIdString -> x.toString() == other.x
//		else -> false
//	}
//
//	override fun hashCode() = (x xor x.ushr(32)).toInt()
//
//	override fun toString() = x.toString()
//
//	override fun toLong() = x
//
//	override fun putMisskeyUntil(dst : JSONObject) : JSONObject = dst.put("untilDate", x)
//	override fun putMisskeySince(dst : JSONObject) : JSONObject = dst.put("sinceDate", x)
//
//}

class EntityIdString(val x : String) : EntityId() {
	
	override fun compareTo(other : EntityId) = when(other) {
		is EntityIdString -> {
			// first: compare by length. '9' is smaller than '11'
			// second: lexically order
			(x.length - other.x.length).notZero() ?: x.compareTo(other.x)
		}
		else -> error("EntityIdString: compare with ${other::class.java.simpleName}")
	}
	
	override fun equals(other : Any?) = when(other) {
		is EntityIdString -> x == other.x
		// integer id is removed at https://source.joinmastodon.org/mastodon/docs/commit/e086d478afa140e7b0b9a60183655315966ad9ff
		// is EntityIdLong -> x == other.x.toString()
		else -> false
	}
	
	override fun hashCode() = x.hashCode()
	
	override fun toString() = x
	
//	override fun toLong() = x.toLong() // may throw exception.
	
	//	abstract fun toLong() : Long

}
