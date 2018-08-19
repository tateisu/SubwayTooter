package jp.juggler.subwaytooter.api.entity

import android.content.Intent
import android.os.Bundle
import org.json.JSONObject

abstract class EntityId : Comparable<EntityId> {
	
	abstract fun toLong() : Long
	abstract fun putMisskeyUntil(dst : JSONObject) : JSONObject
	abstract fun putMisskeySince(dst : JSONObject) : JSONObject
	
	
	companion object {
		
		fun from(x : Long) =  EntityIdLong(x)
		
		fun mayNull(x : Long?) = when(x) {
			null -> null
			else -> EntityIdLong(x)
		}

		fun from(x : String) =  EntityIdString(x)
		
		fun mayNull(x : String?) = when(x) {
			null -> null
			else -> EntityIdString(x)
		}
		
		fun String.decode():EntityId?{
			if(this.isEmpty()) return null
			if(this[0]=='L') return from(this.substring(1).toLong())
			if(this[0]=='S') return from(this.substring(1))
			return null
		}
		
		fun from(intent: Intent, key:String)=
			intent.getStringExtra(key)?.decode()

		fun from(bundle: Bundle, key:String)=
			bundle.getString(key)?.decode()
		
		fun from(data : JSONObject, key : String): EntityId?{
			val o = data.opt(key)
			if(o is Long) return EntityIdLong(o)
			return (o as? String)?.decode()
		}
	}
	
	private fun encode():String{
		val prefix = when(this){
			is EntityIdLong ->'L'
			is EntityIdString->'S'
			else -> error("unknown type")
		}
		return "$prefix$this"
	}
	
	fun putTo(data : Intent, key : String) :Intent = data.putExtra( key,encode())
	
	fun putTo(bundle:Bundle, key : String)  = bundle.putString( key,encode())
	
	fun putTo(data:JSONObject, key : String):JSONObject = data.put( key,encode())

}

class EntityIdLong(val x : Long) : EntityId() {
	
	override fun compareTo(other : EntityId) = when(other) {
		this -> 0
		is EntityIdLong -> x.compareTo(other.x)
		else -> error("EntityIdLong: compare with ${other::javaClass.name}")
	}
	
	override fun equals(other : Any?) =when(other) {
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
		else -> error("EntityIdLong: compare with ${other::javaClass.name}")
	}
	
	override fun equals(other : Any?) =when(other) {
		is EntityIdString -> x == other.x
		is EntityIdLong -> x == other.x.toString()
		else -> false
	}

	override fun hashCode() = x.hashCode()

	override fun toString() = x
	
	override fun toLong() = TootStatus.INVALID_ID // error("can't convert string ID to long")
	
	override fun putMisskeyUntil(dst : JSONObject) : JSONObject = dst.put("untilId", x)
	override fun putMisskeySince(dst : JSONObject) : JSONObject = dst.put("sinceId", x)
	
}
