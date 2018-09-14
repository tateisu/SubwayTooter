package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import jp.juggler.subwaytooter.api.entity.EntityIdLong
import jp.juggler.subwaytooter.api.entity.EntityIdString
import jp.juggler.subwaytooter.table.*

import org.json.JSONException
import org.json.JSONObject

import java.io.IOException
import java.util.ArrayList
import java.util.HashMap
import java.util.Locale

import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.parseLong

object AppDataExporter {
	
	internal val log = LogCategory("AppDataExporter")
	
	private const val MAGIC_NAN = - 76287755398823900.0
	
	private const val KEY_PREF = "pref"
	private const val KEY_ACCOUNT = "account"
	private const val KEY_COLUMN = "column"
	private const val KEY_ACCT_COLOR = "acct_color"
	private const val KEY_MUTED_APP = "muted_app"
	private const val KEY_MUTED_WORD = "muted_word"
	private const val KEY_FAV_MUTE = "fav_mute"
	private const val KEY_CLIENT_INFO = "client_info2"
	private const val KEY_HIGHLIGHT_WORD = "highlight_word"
	
	@Throws(IOException::class, JSONException::class)
	private fun writeJSONObject(writer : JsonWriter, src : JSONObject) {
		writer.beginObject()
		val it = src.keys()
		while(it.hasNext()) {
			val k = it.next()
			if(src.isNull(k)) {
				writer.name(k)
				writer.nullValue()
			} else {
				val o = src.get(k)
				when(o) {
					is String -> {
						writer.name(k)
						writer.value(o)
						
					}
					
					is Boolean -> {
						writer.name(k)
						writer.value(o)
						
					}
					
					is Number -> {
						
						writer.name(k)
						writer.value(o)
						
					}
					
					is EntityIdLong ->{
						writer.name(k)
						writer.value(o.toLong())
					}
					
					is EntityIdString ->{
						writer.name(k)
						writer.value(o.toString())
					}
					
					else -> throw RuntimeException(
						String.format(
							Locale.JAPAN,
							"bad data type: JSONObject key =%s",
							k
						)
					)
				}
			}
		}
		writer.endObject()
	}
	
	@Throws(IOException::class, JSONException::class)
	private fun readJsonObject(reader : JsonReader) : JSONObject {
		val dst = JSONObject()
		
		reader.beginObject()
		while(reader.hasNext()) {
			val name = reader.nextName()
			val token = reader.peek()
			when(token) {
				
				JsonToken.NULL -> reader.nextNull()
				
				JsonToken.STRING -> dst.put(name, reader.nextString())
				
				JsonToken.BOOLEAN -> dst.put(name, reader.nextBoolean())
				
				JsonToken.NUMBER -> dst.put(name, reader.nextDouble())
				
				else -> throw RuntimeException(
					String.format(
						Locale.JAPAN,
						"bad data type: %s key =%s",
						token,
						name
					)
				)
			}
		}
		reader.endObject()
		
		return dst
	}
	
	@Throws(IOException::class)
	private fun writeFromTable(writer : JsonWriter, json_key : String, table : String) {
		
		writer.name(json_key)
		writer.beginArray()
		
		App1.database.query(table, null, null, null, null, null, null)?.use { cursor ->
			val names = ArrayList<String>()
			val column_count = cursor.columnCount
			for(i in 0 until column_count) {
				names.add(cursor.getColumnName(i))
			}
			while(cursor.moveToNext()) {
				writer.beginObject()
				
				for(i in 0 until column_count) {
					when(cursor.getType(i)) {
						Cursor.FIELD_TYPE_NULL -> {
							writer.name(names[i])
							writer.nullValue()
						}
						
						Cursor.FIELD_TYPE_INTEGER -> {
							writer.name(names[i])
							writer.value(cursor.getLong(i))
						}
						
						Cursor.FIELD_TYPE_STRING -> {
							writer.name(names[i])
							writer.value(cursor.getString(i))
						}
						
						Cursor.FIELD_TYPE_FLOAT -> {
							val d = cursor.getDouble(i)
							if(d.isNaN() || d.isInfinite()) {
								log.w("column %s is nan or infinite value.", names[i])
							} else {
								writer.name(names[i])
								writer.value(d)
							}
						}
						
						Cursor.FIELD_TYPE_BLOB -> log.w("column %s is blob.", names[i])
					}
				}
				
				writer.endObject()
			}
		}
		writer.endArray()
	}
	
	@Throws(IOException::class)
	private fun importTable(reader : JsonReader, table : String, id_map : HashMap<Long, Long>?) {
		val db = App1.database
		if(table == SavedAccount.table) {
			SavedAccount.onDBDelete(db)
			SavedAccount.onDBCreate(db)
		}
		
		db.execSQL("BEGIN TRANSACTION")
		try {
			db.execSQL("delete from $table")
			
			val cv = ContentValues()
			
			reader.beginArray()
			while(reader.hasNext()) {
				
				var old_id = - 1L
				cv.clear()
				
				reader.beginObject()
				while(reader.hasNext()) {
					val name = reader.nextName()
					
					if(BaseColumns._ID == name) {
						old_id = reader.nextLong()
						continue
					}
					
					if(SavedAccount.table == table) {
						// 一時的に存在したが現在のDBスキーマにはない項目は読み飛ばす
						if("nickname" == name || "color" == name) {
							reader.skipValue()
							continue
						}
						
						// リアルタイム通知に関連する項目は読み飛ばす
						if(SavedAccount.COL_NOTIFICATION_TAG == name
							|| SavedAccount.COL_REGISTER_KEY == name
							|| SavedAccount.COL_REGISTER_TIME == name) {
							reader.skipValue()
							continue
						}
					}
					
					val token = reader.peek()
					when(token) {
						JsonToken.NULL -> {
							reader.skipValue()
							cv.putNull(name)
						}
						
						JsonToken.BOOLEAN -> cv.put(name, if(reader.nextBoolean()) 1 else 0)
						
						JsonToken.NUMBER -> cv.put(name, reader.nextLong())
						
						JsonToken.STRING -> cv.put(name, reader.nextString())
						
						else -> reader.skipValue()
					} // 無視する
				}
				reader.endObject()
				val new_id =
					db.insertWithOnConflict(table, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
				if(new_id == - 1L) {
					throw RuntimeException("importTable: invalid row_id")
				}
				id_map?.put(old_id, new_id)
			}
			reader.endArray()
			db.execSQL("COMMIT TRANSACTION")
		} catch(ex : Throwable) {
			log.trace(ex)
			log.e(ex, "importTable failed.")
			try {
				db.execSQL("ROLLBACK TRANSACTION")
			} catch(ignored : Throwable) {
			}
			
			throw ex
		}
		
	}
	
	@Throws(IOException::class)
	private fun writePref(writer : JsonWriter, pref : SharedPreferences) {
		writer.beginObject()
		for((k, v) in pref.all) {
			writer.name(k)
			when(v) {
				null -> writer.nullValue()
				is String -> writer.value(v)
				is Boolean -> writer.value(v)
				is Number -> when {
					(v is Double && v.isNaN()) -> writer.value(MAGIC_NAN)
					(v is Float && v.isNaN()) -> writer.value(MAGIC_NAN)
					else -> writer.value(v)
				}
				else -> throw RuntimeException(
					String.format(
						Locale.JAPAN,
						"writePref. bad data type: Preference key =%s",
						k
					)
				)
			}
		}
		writer.endObject()
	}
	
	@Throws(IOException::class)
	private fun importPref(reader : JsonReader, pref : SharedPreferences) {
		val e = pref.edit()
		reader.beginObject()
		while(reader.hasNext()) {
			val k = reader.nextName() ?: throw RuntimeException("importPref: name is null")
			
			val token = reader.peek()
			if(token == JsonToken.NULL) {
				reader.nextNull()
				e.remove(k)
				continue
			}
			
			val prefItem = Pref.map[k]
			when(prefItem) {
				is BooleanPref -> e.putBoolean(k, reader.nextBoolean())
				is IntPref -> e.putInt(k, reader.nextInt())
				is LongPref -> e.putLong(k, reader.nextLong())
				
				is StringPref -> if(prefItem.skipImport) {
					reader.skipValue()
					e.remove(k)
				} else {
					e.putString(k, reader.nextString())
				}
				
				is FloatPref -> {
					val dv = reader.nextDouble()
					e.putFloat(k, if(dv <= MAGIC_NAN) Float.NaN else dv.toFloat())
				}
				
				else -> {
					// ignore or force reset
					reader.skipValue()
					e.remove(k)
				}
			}
		}
		reader.endObject()
		e.apply()
	}
	
	@Throws(IOException::class, JSONException::class)
	private fun writeColumn(app_state : AppState, writer : JsonWriter) {
		writer.beginArray()
		for(column in app_state.column_list) {
			val dst = JSONObject()
			column.encodeJSON(dst, 0)
			writeJSONObject(writer, dst)
		}
		writer.endArray()
	}
	
	@Throws(IOException::class, JSONException::class)
	private fun readColumn(
		app_state : AppState,
		reader : JsonReader,
		id_map : HashMap<Long, Long>
	) : ArrayList<Column> {
		val result = ArrayList<Column>()
		reader.beginArray()
		while(reader.hasNext()) {
			val item = readJsonObject(reader)
			val old_id = item.parseLong(Column.KEY_ACCOUNT_ROW_ID) ?: - 1L
			if(old_id == - 1L) {
				// 検索カラムは NAアカウントと紐ついている。変換の必要はない
			} else {
				val new_id =
					id_map[old_id] ?: throw RuntimeException("readColumn: can't convert account id")
				item.put(Column.KEY_ACCOUNT_ROW_ID, new_id)
			}
			try {
				result.add(Column(app_state, item))
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "column load failed.")
				throw ex
			}
			
		}
		reader.endArray()
		return result
	}
	
	@Throws(IOException::class, JSONException::class)
	fun encodeAppData(context : Context, writer : JsonWriter) {
		writer.setIndent(" ")
		writer.beginObject()
		
		val app_state = App1.getAppState(context)
		//////////////////////////////////////
		run {
			writer.name(KEY_PREF)
			writePref(writer, app_state.pref)
		}
		//////////////////////////////////////
		writeFromTable(writer, KEY_ACCOUNT, SavedAccount.table)
		writeFromTable(writer, KEY_ACCT_COLOR, AcctColor.table)
		writeFromTable(writer, KEY_MUTED_APP, MutedApp.table)
		writeFromTable(writer, KEY_MUTED_WORD, MutedWord.table)
		writeFromTable(writer, KEY_FAV_MUTE, FavMute.table)
		writeFromTable(writer, KEY_CLIENT_INFO, ClientInfo.table)
		writeFromTable(writer, KEY_HIGHLIGHT_WORD, HighlightWord.table)
		
		//////////////////////////////////////
		run {
			writer.name(KEY_COLUMN)
			writeColumn(app_state, writer)
			
		}
		
		writer.endObject()
	}
	
	@SuppressLint("UseSparseArrays")
	@Throws(IOException::class, JSONException::class)
	internal fun decodeAppData(context : Context, reader : JsonReader) : ArrayList<Column> {
		
		var result : ArrayList<Column>? = null
		
		val app_state = App1.getAppState(context)
		reader.beginObject()
		
		val account_id_map = HashMap<Long, Long>()
		
		while(reader.hasNext()) {
			val name = reader.nextName()
			
			when(name) {
				KEY_PREF -> importPref(reader, app_state.pref)
				KEY_ACCOUNT -> importTable(reader, SavedAccount.table, account_id_map)
				
				KEY_ACCT_COLOR -> {
					importTable(reader, AcctColor.table, null)
					AcctColor.clearMemoryCache()
				}
				
				KEY_MUTED_APP -> importTable(reader, MutedApp.table, null)
				KEY_MUTED_WORD -> importTable(reader, MutedWord.table, null)
				KEY_FAV_MUTE -> importTable(reader, FavMute.table, null)
				KEY_HIGHLIGHT_WORD -> importTable(reader, HighlightWord.table, null)
				KEY_CLIENT_INFO -> importTable(reader, ClientInfo.table, null)
				KEY_COLUMN -> result = readColumn(app_state, reader, account_id_map)
			}
		}
		
		run {
			val old_id = Pref.lpTabletTootDefaultAccount(app_state.pref)
			if(old_id != - 1L) {
				val new_id = account_id_map[old_id]
				app_state.pref.edit().put(Pref.lpTabletTootDefaultAccount, new_id ?: - 1L).apply()
			}
		}
		
		if(result == null) {
			throw RuntimeException("import data does not includes column list!")
		}
		
		return result
	}
	
}
