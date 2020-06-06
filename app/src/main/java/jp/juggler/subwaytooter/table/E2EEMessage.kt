package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.TimelineItem
import jp.juggler.util.LogCategory
import jp.juggler.util.getLongOrNull
import jp.juggler.util.getStringOrNull
import java.util.*

class E2EEMessage(
	val readerAcct : Acct,
	val senderAccountId : EntityId,
	val senderDeviceId : String,
	val messageId : EntityId,
	val messageBody : String,
	val createdAt : Long
) : TimelineItem() {
	
	override fun getOrderId() : EntityId = messageId
	
	companion object : TableCompanion {
		private val log = LogCategory("E2EEMessage")
		
		const val table = "e2ee_message"
		
		private const val COL_ID = BaseColumns._ID
		private const val COL_READER_ACCT = "ra"
		private const val COL_SENDER_ACCOUNT_ID = "sai"
		private const val COL_SENDER_DEVICE_ID = "sdi"
		private const val COL_MESSAGE_ID = "mi"
		private const val COL_MESSAGE_ID_INT = "mii"
		private const val COL_MESSAGE_BODY = "mb"
		private const val COL_CREATED_AT = "ca"
		
		override fun onDBCreate(db : SQLiteDatabase) {
			db.execSQL(
				"create table if not exists $table"
					+ "($COL_ID INTEGER PRIMARY KEY"
					+ ",$COL_READER_ACCT text not null"
					+ ",$COL_SENDER_ACCOUNT_ID text not null"
					+ ",$COL_SENDER_DEVICE_ID text "
					+ ",$COL_MESSAGE_ID text not null"
					+ ",$COL_MESSAGE_ID_INT integer not null"
					+ ",$COL_MESSAGE_BODY text not null"
					+ ",$COL_CREATED_AT integer not null default 0"
					+ ")"
			)
			db.execSQL("create unique index if not exists ${table}_message_id on ${table}($COL_READER_ACCT,$COL_MESSAGE_ID)")
			db.execSQL("create unique index if not exists ${table}_message_id_int on ${table}($COL_READER_ACCT,$COL_MESSAGE_ID_INT)")
		}
		
		override fun onDBUpgrade(db : SQLiteDatabase, oldVersion : Int, newVersion : Int) {
			if(oldVersion < 51 && newVersion >= 51) {
				onDBCreate(db)
			}
			if(oldVersion < 52 && newVersion >= 52) {
				try {
					db.execSQL("alter table $table add column $COL_SENDER_DEVICE_ID text")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				try {
					db.execSQL("alter table $table add column $COL_CREATED_AT integer not null default 0")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
			}
		}
		
		fun saveList(src_list : ArrayList<E2EEMessage>) {
			val cv = ContentValues()
			var bOK = false
			val db = App1.database
			db.execSQL("BEGIN TRANSACTION")
			try {
				for(src in src_list) {
					cv.put(COL_READER_ACCT, src.readerAcct.ascii)
					cv.put(COL_SENDER_ACCOUNT_ID, src.senderAccountId.toString())
					cv.put(COL_SENDER_DEVICE_ID, src.senderDeviceId)
					cv.put(COL_MESSAGE_ID, src.messageId.toString())
					cv.put(COL_MESSAGE_ID_INT, src.messageId.toString().toLongOrNull() ?: 0L)
					cv.put(COL_MESSAGE_BODY, src.messageBody)
					cv.put(COL_CREATED_AT, src.createdAt)
					db.insertWithOnConflict(table, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
				}
				bOK = true
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "saveList failed.")
			}
			if(bOK) {
				db.execSQL("COMMIT TRANSACTION")
			} else {
				db.execSQL("ROLLBACK TRANSACTION")
			}
		}
		
		fun load(acct : Acct, maxId : EntityId?, sortInt : Boolean = true) =
			ArrayList<E2EEMessage>().also { dst ->
				var where = "$COL_READER_ACCT=?"
				val whereArg = mutableListOf(acct.ascii)
				val order = if(sortInt) {
					"$COL_MESSAGE_ID_INT desc"
				} else {
					"$COL_MESSAGE_ID desc"
				}
				if(maxId != null) {
					where += if(sortInt) {
						" and $COL_MESSAGE_ID_INT <? "
					} else {
						" and $COL_MESSAGE_ID <? "
					}
					whereArg.add(maxId.toString())
				}
				App1.database.query(
					table,
					null,
					where,
					whereArg.toTypedArray(),
					null,
					null,
					order,
					"40"
				)
					.use { cursor ->
						val idxSenderDeviceId = cursor.getColumnIndex(COL_SENDER_DEVICE_ID)
						val idxMessageId = cursor.getColumnIndex(COL_MESSAGE_ID)
						val idxMessageBody = cursor.getColumnIndex(COL_MESSAGE_BODY)
						val idxCreatedAt = cursor.getColumnIndex(COL_CREATED_AT)
						while(cursor.moveToNext()) {
							val idxSenderAccountId = cursor.getColumnIndex(COL_SENDER_ACCOUNT_ID)
							dst.add(
								E2EEMessage(
									readerAcct = acct,
									senderAccountId = EntityId.mayDefault(
										cursor.getStringOrNull(
											idxSenderAccountId
										)
									),
									senderDeviceId = cursor.getStringOrNull(idxSenderDeviceId)
										?: "",
									messageId = EntityId.mayDefault(
										cursor.getStringOrNull(
											idxMessageId
										)
									),
									messageBody = cursor.getStringOrNull(idxMessageBody) ?: "",
									createdAt = cursor.getLongOrNull(idxCreatedAt) ?: 0L
								)
							)
						}
					}
			}
	}
}
