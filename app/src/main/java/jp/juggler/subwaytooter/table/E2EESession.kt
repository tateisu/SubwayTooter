package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.util.CipherAES
import jp.juggler.util.LogCategory
import org.matrix.olm.OlmSession
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class E2EESession(
	private val accessAcct : Acct,
	internal val partnerAccountId : EntityId,
	internal val partnerDeviceId : String,
	private var lastUsed : Long,
	val olmSession : OlmSession
) {
	
	companion object : TableCompanion {
		
		private val log = LogCategory("E2EESession")
		
		const val table = "e2ee_session"
		
		private const val COL_ID = BaseColumns._ID
		private const val COL_ACCESS_ACCT = "aa"
		private const val COL_PARTNER_ACCOUNT_ID = "pai"
		private const val COL_PARTNER_DEVICE_ID = "pdi"
		private const val COL_SESSION_IV = "siv"
		private const val COL_SESSION_DATA = "sd"
		private const val COL_LAST_USED = "lu"
		
		override fun onDBCreate(db : SQLiteDatabase) {
			db.execSQL(
				"create table if not exists $table"
					+ "($COL_ID INTEGER PRIMARY KEY"
					+ ",$COL_ACCESS_ACCT text not null"
					+ ",$COL_PARTNER_ACCOUNT_ID text not null"
					+ ",$COL_PARTNER_DEVICE_ID text not null"
					+ ",$COL_SESSION_IV blob not null"
					+ ",$COL_SESSION_DATA blob not null"
					+ ",$COL_LAST_USED integer not null"
					+ ")"
			)
			db.execSQL("create unique index if not exists ${table}_unique on ${table}($COL_ACCESS_ACCT,$COL_PARTNER_ACCOUNT_ID,$COL_PARTNER_DEVICE_ID)")
		}
		
		override fun onDBUpgrade(db : SQLiteDatabase, oldVersion : Int, newVersion : Int) {
			if(oldVersion < 54 && newVersion >= 54) {
				onDBCreate(db)
			}
		}
		
		private fun loadOlmSession(
			context : Context,
			iv : ByteArray,
			data : ByteArray
		) : OlmSession? {
			try {
				ObjectInputStream(
					ByteArrayInputStream(
						CipherAES.d(E2EEAccount.getSerializeKey(context), iv, data)
					)
				).use {
					return it.readObject() as OlmSession
				}
			} catch(ex : Throwable) {
				log.trace(ex, "loadOlmSession failed")
				return null
			}
		}
		
		val cache = HashMap<String, E2EESession>()
		
		@Synchronized
		fun load(
			context : Context,
			accessAcct : Acct,
			partnerAccountId : EntityId,
			partnerDeviceId : String,
			initializer : (OlmSession) -> Unit
		) : E2EESession {
			try {
				App1.database.query(
					table,
					null,
					"$COL_ACCESS_ACCT=? and $COL_PARTNER_ACCOUNT_ID=? and $COL_PARTNER_DEVICE_ID=?",
					arrayOf(accessAcct.ascii, partnerAccountId.toString(), partnerDeviceId),
					null, null, null
				).use { cursor ->
					if(cursor.moveToNext()) {
						val iv = cursor.getBlob(cursor.getColumnIndex(COL_SESSION_IV))
						val data = cursor.getBlob(cursor.getColumnIndex(COL_SESSION_DATA))
						val olmSession = loadOlmSession(context, iv, data)
						if(olmSession != null) {
							return E2EESession(
								accessAcct = accessAcct,
								partnerAccountId = partnerAccountId,
								partnerDeviceId = partnerDeviceId,
								lastUsed = cursor.getLong(cursor.getColumnIndex(COL_LAST_USED)),
								olmSession = olmSession
							)
						}
					}
				}
			}catch(ex:Throwable){
				log.trace(ex,"load failed.")
			}
			
			return E2EESession(
				accessAcct = accessAcct,
				partnerAccountId = partnerAccountId,
				partnerDeviceId = partnerDeviceId,
				lastUsed = System.currentTimeMillis(),
				olmSession = OlmSession().also { initializer(it) }
			).also { it.save(context) }
		}
	}
	
	fun save(context : Context) {
		try {
			lastUsed = System.currentTimeMillis()
			
			val bao = ByteArrayOutputStream()
			
			ObjectOutputStream(bao).use { outputStream ->
				outputStream.writeObject(olmSession)
			}
			val (iv, data) =
				CipherAES.e(E2EEAccount.getSerializeKey(context), bao.toByteArray())
			
			val cv = ContentValues()
			cv.put(COL_ACCESS_ACCT, accessAcct.ascii)
			cv.put(COL_PARTNER_ACCOUNT_ID, partnerAccountId.toString())
			cv.put(COL_PARTNER_DEVICE_ID, partnerDeviceId)
			cv.put(COL_SESSION_IV, iv)
			cv.put(COL_SESSION_DATA, data)
			cv.put(COL_LAST_USED, lastUsed)
			App1.database.replace(table, null, cv)
		} catch(ex : Throwable) {
			log.e(ex, "save failed.")
		}
	}
	
	fun dispose() {
		App1.database.delete(
			table,
			"$COL_ACCESS_ACCT=? and $COL_PARTNER_ACCOUNT_ID=? and $COL_PARTNER_DEVICE_ID=?",
			arrayOf(accessAcct.ascii, partnerAccountId.toString(), partnerDeviceId)
		)
		olmSession.releaseSession()
	}
}