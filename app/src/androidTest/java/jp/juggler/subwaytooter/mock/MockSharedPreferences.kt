package jp.juggler.subwaytooter.mock

import android.content.SharedPreferences

class MockSharedPreferences(
	val map : HashMap<String, Any> = HashMap()
) : SharedPreferences {
	
	override fun contains(key : String?) = map.contains(key)
	
	override fun getBoolean(key : String?, defValue : Boolean)
		= map.get(key) as? Boolean ?: defValue
	
	override fun getInt(key : String?, defValue : Int)
		= map.get(key) as? Int ?: defValue
	
	override fun getLong(key : String?, defValue : Long)
		= map.get(key) as? Long ?: defValue
	
	override fun getFloat(key : String?, defValue : Float)
		= map.get(key) as? Float ?: defValue
	
	override fun getString(key : String?, defValue : String?)
		= map.get(key) as? String ?: defValue
	
	override fun getStringSet(key : String?, defValues : MutableSet<String>?)
		= map.get(key) as? MutableSet<String> ?: defValues
	

	override fun edit() : SharedPreferences.Editor {
		return Editor(this)
	}
	
	override fun getAll() : MutableMap<String, *> {
		TODO("not implemented")
	}
	
	override fun registerOnSharedPreferenceChangeListener(
		listener : SharedPreferences.OnSharedPreferenceChangeListener?
	) {
		TODO("not implemented")
	}
	
	override fun unregisterOnSharedPreferenceChangeListener(
		listener : SharedPreferences.OnSharedPreferenceChangeListener?
	) {
		TODO("not implemented")
	}
	
	companion object {
		val REMOVED_OBJECT = Any()
	}
	
	class Editor(private val pref : MockSharedPreferences) : SharedPreferences.Editor {
		private val changeSet = HashMap<String, Any>()
		
		override fun commit() : Boolean {
			for((k, v) in changeSet) {
				if(v === REMOVED_OBJECT) {
					pref.map.remove(k)
				} else {
					pref.map.put(k, v)
				}
			}
			return true
		}
		
		override fun apply() {
			commit()
		}
		
		override fun clear() : SharedPreferences.Editor {
			changeSet.clear()
			return this
		}
		
		override fun remove(key : String) : SharedPreferences.Editor {
			changeSet.put(key, REMOVED_OBJECT)
			return this
		}
		
		private fun putAny(k : String, v : Any?) : SharedPreferences.Editor {
			changeSet.put(k, v ?: REMOVED_OBJECT)
			return this
		}
		
		override fun putLong(key : String, value : Long) = putAny(key, value)
		override fun putInt(key : String, value : Int) = putAny(key, value)
		override fun putBoolean(key : String, value : Boolean) = putAny(key, value)
		override fun putFloat(key : String, value : Float) = putAny(key, value)
		override fun putString(key : String, value : String?) = putAny(key, value)
		override fun putStringSet(key : String, value : MutableSet<String>?) = putAny(key, value)
		
	}
	
}
