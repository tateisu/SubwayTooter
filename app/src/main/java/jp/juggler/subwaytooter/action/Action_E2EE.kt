package jp.juggler.subwaytooter.action

import android.app.Dialog
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.api.TootApiCallback
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.syncAccountByAcct
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.dialog.DlgTextInput
import jp.juggler.subwaytooter.table.E2EEAccount
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.*
import kotlinx.coroutines.*
import org.matrix.olm.OlmUtility
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object Action_E2EE {
	
	private val log = LogCategory("Action_E2EE")
	
	private val secureRandom = SecureRandom()
	
	private fun sendMessage(
		activity : ActMain,
		accessInfo : SavedAccount,
		who : Acct,
		a1 : TootAccount,
		text : String,
		client : TootApiClient,
		device : JsonObject
	) : Boolean {
		
		val targetAccountId = a1.id
		val targetDeviceId = device.string("device_id")!!
		
		//////////////
		
		val(encryptedMessage,initializeResult)= E2EEAccount
			.load(activity, accessInfo.acct)
			.encrypt(activity, who,targetAccountId, targetDeviceId, device, text,client)

		if(encryptedMessage==null){
			if(initializeResult!=null)
				showToast(activity, true,initializeResult.error)
			return false
		}

		//////////////
		
		val HMAC_SHA256 = "HmacSHA256"
		
		fun genHmacKey() : ByteArray {
			val keygen = KeyGenerator.getInstance(HMAC_SHA256)
			keygen.init(secureRandom)
			val key = keygen.generateKey()
			return key.encoded
		}
		
		val hmacKey = genHmacKey()
		val hmac = Mac.getInstance(HMAC_SHA256)
			.apply { init(SecretKeySpec(hmacKey, HMAC_SHA256)) }
			.doFinal(text.encodeUTF8())
		
		//////////////
		
		val result = client.request(
			"/api/v1/crypto/deliveries",
			jsonObject {
				put("device", jsonArray {
					add(jsonObject {
						put("account_id", a1.id.toString())
						put("device_id", device.string("device_id"))
						put("body", encryptedMessage.mCipherText)
						put("type", encryptedMessage.mType)
						put("hmac", hmac.encodeHexLower())
					})
				})
			}.toPostRequestBuilder()
		) ?: return false
		
		val error = result.error
		if(error != null) {
			showToast(activity, true, error)
			return false
		}
		
		// 200 {} メッセージIDがない…。
		
		
		return true
	}
	
	fun sendMessageUI(activity : ActMain, accessInfo : SavedAccount, who : Acct) {
		GlobalScope.launch(Dispatchers.IO) {
			try {
				val client = TootApiClient(
					activity,
					callback = object : TootApiCallback {
						override val isApiCancelled : Boolean
							get() = false
					}
				)
				client.account = accessInfo
				
				// sync account
				val (r1, a1ref) = client.syncAccountByAcct(accessInfo, who)
				r1 ?: return@launch
				
				if(a1ref == null) {
					showToast(activity, true, r1.error)
					return@launch
				}
				val a1 = a1ref.get()
				
				//
				val result = client.request(
					"/api/v1/crypto/keys/query",
					jsonObject {
						put("id", jsonArray {
							add(a1.id)
						})
					}.toPostRequestBuilder()
				)
					?: return@launch
				
				if(result.error != null) {
					showToast(activity, true, result.error)
					return@launch
				}
				
				// [{"account_id":"1","devices":[{"device_id":"ce…cd","name":"KDDI…SCV42","identity_key":"v6…zM","fingerprint_key":"/x…F4"}]}]
				val account = result.jsonArray
					?.mapNotNull { it as? JsonObject }
					?.find { it.string("account_id") == a1.id.toString() }
				
				if(account == null) {
					showToast(
						activity,
						true,
						"query result does not contains information for ${who}."
					)
					return@launch
				}
				
				val devices = account.jsonArray("devices")
				if(devices == null || devices.isEmpty()) {
					showToast(activity, true, "this user has no E2EE device registrations.")
					return@launch
				}
				
				val device = withContext(Dispatchers.Main) {
					suspendCoroutine<JsonObject?> { continuation ->
						val ad = ActionsDialog()
						ad.onCancel = { continuation.resume(null) }
						devices.forEach {
							if(it is JsonObject) {
								val name = it.string("name") ?: it.string("device_id")
								if(name != null) {
									ad.addAction(name) {
										continuation.resume(it)
									}
								}
							}
						}
						ad.show(activity, who.toString())
					}
				} ?: return@launch
				
				
				
				withContext(Dispatchers.Main) {
					DlgTextInput.show(activity,
						"message",
						"hello",
						object : DlgTextInput.Callback {
							override fun onEmptyError() {
							}
							
							override fun onOK(dialog : Dialog, text : String) {
								GlobalScope.launch(Dispatchers.IO) {
									try {
										repeat(10){
											sendMessage(
												activity = activity,
												accessInfo = accessInfo,
												who = who,
												a1 = a1,
												text = "$text $it",
												client = client,
												device = device
											)
											delay(1000L)
										}
										if(sendMessage(
												activity = activity,
												accessInfo = accessInfo,
												who = who,
												a1 = a1,
												text = text,
												client = client,
												device = device
											)) {
											showToast(activity, false, "message was sent")
											dialog.dismissSafe()
										}
										// 失敗したらダイアログを閉じない
									} catch(ex : Throwable) {
										log.trace(ex)
										showToast(activity, ex, "error")
									}
								}
							}
						})
				}
				
			} catch(ex : Throwable) {
				log.trace(ex)
				showToast(activity, ex, "error")
			}
		}
	}
}
