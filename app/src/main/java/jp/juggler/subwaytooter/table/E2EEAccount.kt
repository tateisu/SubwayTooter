package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.provider.BaseColumns
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.util.CipherAES
import jp.juggler.util.*
import org.apache.commons.io.IOUtils
import org.matrix.olm.*
import java.io.*
import java.util.*
import java.util.concurrent.CancellationException

class E2EEAccount private constructor(
	val acct : Acct,
	var deviceName : String,
	private val deviceId : String = UUID.randomUUID().toString(),
	private var lastUploadKey : Long = 0L,
	private val olmAccount : OlmAccount = OlmAccount()
) {
	
	companion object : TableCompanion {
		private val log = LogCategory("E2EEAccount")
		
		private const val serializeKeyFileName = "e2eeAccountSerializeKey"
		
		private var serializeKey : ByteArray? = null
		
		@Synchronized
		internal fun getSerializeKey(context : Context) : ByteArray =
			serializeKey ?: try {
				context.openFileInput(serializeKeyFileName).use { inStream ->
					val bao = ByteArrayOutputStream(32)
					IOUtils.copy(inStream, bao)
					val keyBytes = bao.toByteArray()
					serializeKey = keyBytes
					keyBytes
				}
			} catch(ex : IOException) {
				log.e(ex, "getStreamKey failed. generate serializeKey…")
				val keyBytes = CipherAES.genKey()
				context.openFileOutput(serializeKeyFileName, Context.MODE_PRIVATE).use {
					it.write(keyBytes)
				}
				serializeKey = keyBytes
				keyBytes
			}
		
		private fun loadOlmAccount(
			context : Context,
			iv : ByteArray,
			data : ByteArray
		) : OlmAccount {
			try {
				ObjectInputStream(
					ByteArrayInputStream(
						CipherAES.d(getSerializeKey(context), iv, data)
					)
				)
					.use {
						return it.readObject() as OlmAccount
					}
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "loadOlmAccount failed.")
				return OlmAccount()
			}
		}
		
		/////////////////////////////////////
		
		const val table = "e2ee_account"
		
		private const val COL_ID = BaseColumns._ID
		private const val COL_ACCT = "a"
		private const val COL_DEVICE_ID = "di"
		private const val COL_DEVICE_NAME = "dn"
		private const val COL_ACCOUNT_IV = "ai"
		private const val COL_ACCOUNT_DATA = "ad"
		private const val COL_LAST_UPLOAD_KEY = "luk"
		
		override fun onDBCreate(db : SQLiteDatabase) {
			db.execSQL(
				"create table if not exists $table"
					+ "($COL_ID INTEGER PRIMARY KEY"
					+ ",$COL_ACCT text unique not null"
					+ ",$COL_DEVICE_ID text not null"
					+ ",$COL_DEVICE_NAME text not null"
					+ ",$COL_ACCOUNT_IV blob not null"
					+ ",$COL_ACCOUNT_DATA blob not null"
					+ ",$COL_LAST_UPLOAD_KEY integer not null default 0"
					+ ")"
			)
			db.execSQL("create index if not exists ${table}_acct on ${table}($COL_ACCT)")
		}
		
		override fun onDBUpgrade(db : SQLiteDatabase, oldVersion : Int, newVersion : Int) {
			if(oldVersion < 49 && newVersion >= 49) {
				db.execSQL("drop table if exists $table")
				onDBCreate(db)
			}
			
			if(oldVersion < 50 && newVersion >= 50) {
				try {
					db.execSQL("alter table $table add column $COL_LAST_UPLOAD_KEY integer not null default 0")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
			}
		}
		
		private val cache = HashMap<String, E2EEAccount>()
		
		@Synchronized
		fun load(context : Context, acct : Acct) : E2EEAccount {
			try {
				val cached = cache[acct.ascii]
				if(cached != null) return cached
				
				App1.database.query(
					table,
					null,
					"$COL_ACCT=?",
					arrayOf(acct.ascii),
					null,
					null,
					null
				).use { cursor ->
					if(cursor.moveToNext()) {
						return E2EEAccount(
							acct = acct,
							deviceName = cursor.getString(cursor.getColumnIndex(COL_DEVICE_NAME)),
							deviceId = cursor.getString(cursor.getColumnIndex(COL_DEVICE_ID)),
							lastUploadKey = cursor.getLong(cursor.getColumnIndex(COL_LAST_UPLOAD_KEY)),
							olmAccount = loadOlmAccount(
								context,
								cursor.getBlob(cursor.getColumnIndex(COL_ACCOUNT_IV)),
								cursor.getBlob(cursor.getColumnIndex(COL_ACCOUNT_DATA))
							)
						).also {
							cache[acct.ascii] = it
							log.d("load. acct=${acct}")
						}
					}
				}
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "load failed.")
			}
			
			val deviceName = "${TootStatus.formatTime(
				context,
				System.currentTimeMillis(),
				bAllowRelative = false
			)} ${Build.BRAND} ${Build.MANUFACTURER} ${Build.MODEL}"
			
			return E2EEAccount(
				acct = acct,
				deviceName = deviceName
			).also {
				cache[acct.ascii] = it
				log.d("create. acct=${acct}")
			}
		}
		
		init {
			// JNIライブラリのロード
			val olmManager = OlmManager()
			log.d("olmManager: version=${olmManager.version}, olmLibVersion=${olmManager.olmLibVersion}")
		}
		
		fun reset(context : Context) {
			context.deleteFile(serializeKeyFileName)
			App1.database.execSQL(
				"delete from $table"
			)
		}
	}
	
	@Synchronized
	fun save(context : Context) {
		try {
			val bao = ByteArrayOutputStream()
			ObjectOutputStream(bao).use { outputStream ->
				outputStream.writeObject(olmAccount)
			}
			val (iv, data) =
				CipherAES.e(getSerializeKey(context), bao.toByteArray())
			
			val cv = ContentValues()
			cv.put(COL_ACCT, acct.ascii)
			cv.put(COL_DEVICE_ID, deviceId)
			cv.put(COL_DEVICE_NAME, deviceName)
			cv.put(COL_LAST_UPLOAD_KEY, lastUploadKey)
			cv.put(COL_ACCOUNT_IV, iv)
			cv.put(COL_ACCOUNT_DATA, data)
			App1.database.replace(table, null, cv)
			
		} catch(ex : Throwable) {
			log.e(ex, "save failed.")
		}
	}
	
	@Synchronized
	fun uploadKeys(
		context : Context,
		client : TootApiClient,
		dontSkip : Boolean = false
	) : TootApiResult? {
		
		val now = System.currentTimeMillis()
		if(now - lastUploadKey <= 1800000L && ! dontSkip) return TootApiResult() // skipped.
		
		try {
			lastUploadKey = now
			
			// サーバ上のワンタイムキーの残りを調べる
			val result2 = client.request("/api/v1/crypto/keys/count")
			
			// 404はサーバ側にAPIがない場合と既に登録されたデバイス情報がない場合の両方で発生する
			// 正常なら {"one_time_keys":500} などが返る
			val remainOnServer = result2?.jsonObject?.int("one_time_keys") ?: 0
			log.d("one time key server count=${remainOnServer}")
			
			return client.request(
				"/api/v1/crypto/keys/upload",
				jsonObject {
					put("device", jsonObject {
						val identityKeys = olmAccount.identityKeys()
						put("device_id", deviceId)
						put("name", deviceName)
						put("fingerprint_key", identityKeys[OlmAccount.JSON_KEY_FINGER_PRINT_KEY])
						put("identity_key", identityKeys[OlmAccount.JSON_KEY_IDENTITY_KEY])
					})
					val maxKeyCount = olmAccount.maxOneTimeKeys().toInt()
					var delta = maxKeyCount - remainOnServer
					
					if(delta > 0) {
						log.d("generate $delta one time key. $acct")
						
						// 未公開のワンタイムキーが残ってるかもしれない
						delta -= olmAccount.oneTimeKeys()[OlmAccount.JSON_KEY_ONE_TIME_KEY] !!.size
						if(delta > 0) {
							// 未公開のワンタイムキーを作る
							olmAccount.generateOneTimeKeys(delta)
						}
						
						// 未公開のキーの集合をサーバに送る
						val map = olmAccount.oneTimeKeys()[OlmAccount.JSON_KEY_ONE_TIME_KEY] !!
						put("one_time_keys", jsonArray {
							for(pair in map) {
								add(jsonObject {
									put("key_id", pair.key)
									put("key", pair.value)
									put("signature", olmAccount.signMessage(pair.value))
								})
							}
						})
					}
				}.toPostRequestBuilder()
			)?.also { result ->
				val jsonObject = result.jsonObject
				if(jsonObject != null) {
					// {"device_id":"ce…cd","name":"KDDI…SCV42","identity_key":"v6…zM","fingerprint_key":"/x…F4"}
					
					// 未公開のキーを公開状態にする
					olmAccount.markOneTimeKeysAsPublished()
				}
			}
		} finally {
			save(context)
		}
	}
	
	private val sessionCache = HashMap<String, E2EESession>()
	
	@Synchronized
	fun loadSession(
		context : Context,
		partnerAccountId : EntityId,
		partnerDeviceId : String,
		initializer : (OlmSession) -> Unit
	) : E2EESession {
		
		// check memory cache
		val cacheKey = "$partnerAccountId,$partnerDeviceId"
		val cached = sessionCache[cacheKey]
		if(cached != null) return cached
		
		// check database cache or create
		return E2EESession
			.load(context, this.acct, partnerAccountId, partnerDeviceId, initializer)
			.also { sessionCache[cacheKey] = it }
	}
	
	@Synchronized
	fun removeSession(session : E2EESession){
		val cacheKey = "${session.partnerAccountId},${session.partnerDeviceId}"
		sessionCache.remove(cacheKey)
		session.dispose()
	}
	
	@Synchronized
	fun decrypt(
		context : Context,
		senderAccountId : EntityId,
		senderDeviceId : String,
		encryptedMsg : OlmMessage
	) : String{
		try {
			val session = loadSession(context, senderAccountId, senderDeviceId) {
				it.initInboundSession(olmAccount, encryptedMsg.mCipherText) // may throw
			}
			try {
				val decryptedMessage = session.olmSession.decryptMessage(encryptedMsg)
				log.d("decryptedMessage=$decryptedMessage")
				olmAccount.removeOneTimeKeys(session.olmSession)
				return decryptedMessage
			} finally {
				save(context)
				session.save(context)
			}
		} catch(ex : Throwable) {
			log.trace(ex, "message decode failed.")
			return ex.withCaption("message decode failed.")
		}
	}
	
	@Synchronized
	fun encrypt(
		context : Context,
		whoAcct : Acct,
		targetAccountId : EntityId,
		targetDeviceId : String,
		device : JsonObject,
		text : String,
		client : TootApiClient
	) : Pair<OlmMessage?, TootApiResult?> {
		
		val session = loadSession(context, targetAccountId, targetDeviceId) { olmSession ->
			
			val result = client.request(
				"/api/v1/crypto/keys/claim",
				jsonObject {
					put("device", jsonArray {
						add(jsonObject {
							put("account_id", targetAccountId.toString())
							put("device_id", targetDeviceId)
						})
					})
				}.toPostRequestBuilder()
			)
				?: throw CancellationException("/api/v1/crypto/keys/claim was cancelled")
			
			if(result.error != null)
				error("${result.error} in POST /api/v1/crypto/keys/claim on ${client.instance}")
			
			// [{"account_id":"1","device_id":"ce…cd","key_id":"AAABAw","key":"Zo…QQ","signature":"IR…Bg"}]
			
			val claim = result.jsonArray
				?.mapNotNull { it as? JsonObject }
				?.find { it.string("account_id") == targetAccountId.toString() }
				?: error("claim result does not contains information for ${whoAcct.pretty}.")
			
			val util = OlmUtility()
			try {
				util.verifyEd25519Signature(
					claim.string("signature"),
					device.string("fingerprint_key"),
					claim.string("key")
				)
			} finally {
				util.releaseUtility()
			}
			
			olmSession.initOutboundSession(
				olmAccount,
				device.string("identity_key"),
				claim.string("key")
			)
		}
		
		try {
			return Pair(session.olmSession.encryptMessage(text), null)
		} finally {
			save(context)
			session.save(context)
		}
	}
}
