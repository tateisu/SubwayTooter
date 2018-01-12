package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import jp.juggler.subwaytooter.api.TootApiClient

import org.hjson.JsonValue

import java.util.regex.Pattern

import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils
import okhttp3.Request

class ActCustomStreamListener : AppCompatActivity(), View.OnClickListener, TextWatcher {
	
	private lateinit var etStreamListenerConfigurationUrl : EditText
	private lateinit var etStreamListenerSecret : EditText
	private lateinit var tvLog : TextView
	private lateinit var btnDiscard : View
	private lateinit var btnTest : View
	private lateinit var btnSave : View
	
	internal var stream_config_json : String? = null
	
	private var bLoading = false
	
	private val isTestRunning : Boolean
		get() = last_task?.isCancelled ?: false
	
	internal var last_task : AsyncTask<Void, Void, String?>? = null
	
	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		App1.setActivityTheme(this, false)
		
		initUI()
		
		if(savedInstanceState != null) {
			stream_config_json = savedInstanceState.getString(STATE_STREAM_CONFIG_JSON)
		} else {
			load()
		}
		
		showButtonState()
	}
	
	override fun onSaveInstanceState(outState : Bundle?) {
		super.onSaveInstanceState(outState)
		outState ?: return
		
		outState.putString(STATE_STREAM_CONFIG_JSON, stream_config_json)
		
	}
	
	private fun initUI() {
		setContentView(R.layout.act_custom_stream_listener)
		
		Styler.fixHorizontalPadding(findViewById(R.id.llContent))
		
		etStreamListenerConfigurationUrl = findViewById(R.id.etStreamListenerConfigurationUrl)
		etStreamListenerSecret = findViewById(R.id.etStreamListenerSecret)
		etStreamListenerConfigurationUrl.addTextChangedListener(this)
		etStreamListenerSecret.addTextChangedListener(this)
		
		tvLog = findViewById(R.id.tvLog)
		
		btnDiscard = findViewById(R.id.btnDiscard)
		btnTest = findViewById(R.id.btnTest)
		btnSave = findViewById(R.id.btnSave)
		
		btnDiscard.setOnClickListener(this)
		btnTest.setOnClickListener(this)
		btnSave.setOnClickListener(this)
	}
	
	private fun load() {
		bLoading = true
		
		val pref = Pref.pref(this)
		
		etStreamListenerConfigurationUrl.setText(pref.getString(Pref.KEY_STREAM_LISTENER_CONFIG_URL, ""))
		etStreamListenerSecret.setText(pref.getString(Pref.KEY_STREAM_LISTENER_SECRET, ""))
		stream_config_json = null
		tvLog.text = getString(R.string.input_url_and_secret_then_test)
		
		bLoading = false
	}
	
	override fun beforeTextChanged(s : CharSequence, start : Int, count : Int, after : Int) {
	
	}
	
	override fun onTextChanged(s : CharSequence, start : Int, before : Int, count : Int) {
	
	}
	
	override fun afterTextChanged(s : Editable) {
		tvLog.text = getString(R.string.input_url_and_secret_then_test)
		stream_config_json = null
		showButtonState()
	}
	
	private fun showButtonState() {
		btnSave.isEnabled = stream_config_json != null
		btnTest.isEnabled = ! isTestRunning
	}
	
	override fun onClick(v : View) {
		when(v.id) {
			R.id.btnDiscard -> {
				Utils.hideKeyboard(this, etStreamListenerConfigurationUrl)
				finish()
			}
			R.id.btnTest -> {
				Utils.hideKeyboard(this, etStreamListenerConfigurationUrl)
				startTest()
			}
			R.id.btnSave -> {
				Utils.hideKeyboard(this, etStreamListenerConfigurationUrl)
				if(save()) {
					SavedAccount.clearRegistrationCache()
					PollingWorker.queueUpdateListener(this)
					finish()
				}
			}
		}
	}
	
	private fun save() : Boolean {
		if(stream_config_json == null) {
			Utils.showToast(this, false, "please test before save.")
			return false
		}
		
		Pref.pref(this).edit()
			.putString(Pref.KEY_STREAM_LISTENER_CONFIG_URL, etStreamListenerConfigurationUrl.text.toString().trim { it <= ' ' })
			.putString(Pref.KEY_STREAM_LISTENER_SECRET, etStreamListenerSecret.text.toString().trim { it <= ' ' })
			.putString(Pref.KEY_STREAM_LISTENER_CONFIG_DATA, stream_config_json)
			.apply()
		return true
	}
	
	internal fun addLog(line : String) {
		Utils.runOnMainThread{
			val old = tvLog.text.toString()
			tvLog.text = if(old.isEmpty()) line else old + "\n" + line
		}
	}
	
	@SuppressLint("StaticFieldLeak")
	private fun startTest() {
		val strSecret = etStreamListenerSecret.text.toString().trim { it <= ' ' }
		val strUrl = etStreamListenerConfigurationUrl.text.toString().trim { it <= ' ' }
		stream_config_json = null
		showButtonState()
		
		val task = object : AsyncTask<Void, Void, String?>() {
			override fun doInBackground(vararg params : Void) : String? {
				try {
					
					while(true) {
						if( strSecret.isEmpty() ) {
							addLog("Secret is empty. Custom Listener is not used.")
							break
						} else if(strUrl.isEmpty() ) {
							addLog("Configuration URL is empty. Custom Listener is not used.")
							break
						}
						
						addLog("try to loading Configuration data from URL…")
						var builder : Request.Builder = Request.Builder()
							.url(strUrl)
						
						var call = App1.ok_http_client.newCall(builder.build())
						
						val response = call.execute()
						
						val bodyString : String? = try {
							response.body()?.string()
						} catch(ex : Throwable) {
							log.trace(ex)
							null
						}

						if(! response.isSuccessful || bodyString?.isEmpty() != false ){
							addLog(TootApiClient.formatResponse(response, "Can't get configuration from URL.",bodyString))
							break
						}
						
						val jv : JsonValue = try {
							JsonValue.readHjson(bodyString)
						} catch(ex : Throwable) {
							log.trace(ex)
							addLog(Utils.formatError(ex, "Can't parse configuration data."))
							break
						}
						
						if(! jv.isObject) {
							addLog("configuration data is not JSON Object.")
							break
						}
						val root = jv.asObject()
						
						var has_wildcard = false
						var has_error = false
						for(member in root) {
							val strInstance = member.name
							if("*" == strInstance) {
								has_wildcard = true
							} else if(reUpperCase.matcher(strInstance).find()) {
								addLog(strInstance + " : instance URL must be lower case.")
								has_error = true
								continue
							} else if(strInstance[strInstance.length - 1] == '/') {
								addLog(strInstance + " : instance URL must not be trailed with '/'.")
								has_error = true
								continue
							} else if(! reInstanceURL.matcher(strInstance).find()) {
								addLog(strInstance + " : instance URL is not like https://.....")
								has_error = true
								continue
							}
							val entry_value = member.value
							if(! entry_value.isObject) {
								addLog(strInstance + " : value for this instance is not JSON Object.")
								has_error = true
								continue
							}
							val entry = entry_value.asObject()
							
							val keys = arrayOf("urlStreamingListenerRegister", "urlStreamingListenerUnregister", "appId")
							for(key in keys) {
								val v = entry.get(key)
								if(! v.isString) {
									addLog("$strInstance.$key : missing parameter, or data type is not string.")
									has_error = true
									continue
								}
								val sv = v.asString()
								if(sv.isEmpty()) {
									addLog("$strInstance.$key : empty parameter.")
									has_error = true
								} else if(sv.contains(" ")) {
									addLog("$strInstance.$key : contains whitespace.")
									has_error = true
								}
								
								if("appId" != key) {
									if(! reUrl.matcher(sv).find()) {
										addLog("$strInstance.$key : not like Url.")
										has_error = true
									} else if(Uri.parse(sv).scheme == "https") {
										try {
											addLog("check access to $sv …")
											builder = Request.Builder().url(sv)
											call = App1.ok_http_client.newCall(builder.build())
											call.execute()
										} catch(ex : Throwable) {
											log.trace(ex)
											addLog(strInstance + "." + key + " : " + Utils.formatError(ex, "connect failed."))
											has_error = true
										}
										
									}
								}
							}
						}
						
						if(! has_wildcard) {
							addLog("Warning: This configuration has no wildcard entry.")
							if(! has_error) {
								for(sa in SavedAccount.loadAccountList(this@ActCustomStreamListener)) {
									if(sa.isPseudo) continue
									val instanceUrl = ("https://" + sa.host).toLowerCase()
									val v = root.get(instanceUrl)
									if(v == null || ! v.isObject) {
										addLog("Warning: $instanceUrl : is found in account, but not found in configuration data.")
									}
								}
							}
						}
						
						if(has_error) {
							addLog("This configuration has error. ")
							break
						}
						
						return bodyString
					}
					
				} catch(ex : Throwable) {
					log.trace(ex)
					addLog(Utils.formatError(ex, "Can't read configuration from URL."))
				}
				
				return null
			}
			
			override fun onCancelled(s : String?) {
				onPostExecute(s)
			}
			
			override fun onPostExecute(s : String?) {
				last_task = null
				if(s != null) {
					stream_config_json = s
					addLog("seems configuration is ok.")
				} else {
					addLog("error detected.")
				}
				showButtonState()
			}
			
		}
		last_task = task
		task.executeOnExecutor(App1.task_executor)
	}
	
	companion object {
		
		internal val log = LogCategory("ActCustomStreamListener")
		
		// internal val EXTRA_ACCT = "acct"
		
		fun open(activity : Activity) {
			val intent = Intent(activity, ActCustomStreamListener::class.java)
			activity.startActivity(intent)
		}
		
		internal val STATE_STREAM_CONFIG_JSON = "stream_config_json"
		internal val reInstanceURL = Pattern.compile("\\Ahttps://[a-z0-9.-_:]+\\z")
		internal val reUpperCase = Pattern.compile("[A-Z]")
		internal val reUrl = Pattern.compile("\\Ahttps?://[\\w\\-?&#%~!$'()*+,/:;=@._\\[\\]]+\\z")
	}
}
