package jp.juggler.subwaytooter

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.RingtoneManager
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.SpannableString
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import jp.juggler.subwaytooter.Styler.defaultColorIcon
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.dialog.ProgressDialogEx
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.*
import jp.juggler.subwaytooter.view.MyNetworkImageView
import jp.juggler.util.*
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.textColor
import org.json.JSONObject
import java.io.*
import kotlin.math.max

class ActAccountSetting
	: AppCompatActivity(), View.OnClickListener, CompoundButton.OnCheckedChangeListener {
	
	companion object {
		
		internal val log = LogCategory("ActAccountSetting")
		
		internal const val KEY_ACCOUNT_DB_ID = "account_db_id"
		
		internal const val REQUEST_CODE_ACCT_CUSTOMIZE = 1
		internal const val REQUEST_CODE_NOTIFICATION_SOUND = 2
		private const val REQUEST_CODE_AVATAR_ATTACHMENT = 3
		private const val REQUEST_CODE_HEADER_ATTACHMENT = 4
		private const val REQUEST_CODE_AVATAR_CAMERA = 5
		private const val REQUEST_CODE_HEADER_CAMERA = 6
		
		internal const val RESULT_INPUT_ACCESS_TOKEN = Activity.RESULT_FIRST_USER + 10
		internal const val EXTRA_DB_ID = "db_id"
		
		internal const val max_length_display_name = 30
		internal const val max_length_note = 160
		internal const val max_length_fields = 255
		
		private const val PERMISSION_REQUEST_AVATAR = 1
		private const val PERMISSION_REQUEST_HEADER = 2
		
		internal const val MIME_TYPE_JPEG = "image/jpeg"
		internal const val MIME_TYPE_PNG = "image/png"
		
		fun open(activity : Activity, ai : SavedAccount, requestCode : Int) {
			val intent = Intent(activity, ActAccountSetting::class.java)
			intent.putExtra(KEY_ACCOUNT_DB_ID, ai.db_id)
			activity.startActivityForResult(intent, requestCode)
		}
		
	}
	
	internal lateinit var account : SavedAccount
	internal lateinit var pref : SharedPreferences
	
	private lateinit var tvInstance : TextView
	private lateinit var tvUser : TextView
	private lateinit var btnAccessToken : Button
	private lateinit var btnInputAccessToken : Button
	private lateinit var btnAccountRemove : Button
	private lateinit var btnLoadPreference : Button
	
	private lateinit var btnVisibility : Button
	
	private lateinit var swNSFWOpen : Switch
	private lateinit var swDontShowTimeout : Switch
	private lateinit var swExpandCW : Switch
	private lateinit var swMarkSensitive : Switch
	
	private lateinit var btnOpenBrowser : Button
	private lateinit var btnPushSubscription : Button
	private lateinit var cbNotificationMention : CheckBox
	private lateinit var cbNotificationBoost : CheckBox
	private lateinit var cbNotificationFavourite : CheckBox
	private lateinit var cbNotificationFollow : CheckBox
	private lateinit var cbNotificationReaction : CheckBox
	private lateinit var cbNotificationVote : CheckBox
	
	private lateinit var cbConfirmFollow : CheckBox
	private lateinit var cbConfirmFollowLockedUser : CheckBox
	private lateinit var cbConfirmUnfollow : CheckBox
	private lateinit var cbConfirmBoost : CheckBox
	private lateinit var cbConfirmFavourite : CheckBox
	private lateinit var cbConfirmUnboost : CheckBox
	private lateinit var cbConfirmUnfavourite : CheckBox
	private lateinit var cbConfirmToot : CheckBox
	
	private lateinit var tvUserCustom : TextView
	private lateinit var btnUserCustom : View
	private lateinit var full_acct : String
	
	private lateinit var btnNotificationSoundEdit : Button
	private lateinit var btnNotificationSoundReset : Button
	private lateinit var btnNotificationStyleEdit : Button
	
	private var notification_sound_uri : String? = null
	
	private lateinit var ivProfileHeader : MyNetworkImageView
	private lateinit var ivProfileAvatar : MyNetworkImageView
	private lateinit var btnProfileAvatar : View
	private lateinit var btnProfileHeader : View
	private lateinit var etDisplayName : EditText
	private lateinit var btnDisplayName : View
	private lateinit var etNote : EditText
	private lateinit var cbLocked : CheckBox
	private lateinit var btnNote : View
	private lateinit var etDefaultText : EditText
	
	private lateinit var name_invalidator : NetworkEmojiInvalidator
	private lateinit var note_invalidator : NetworkEmojiInvalidator
	private lateinit var default_text_invalidator : NetworkEmojiInvalidator
	internal lateinit var handler : Handler
	
	internal var loading = false
	
	private lateinit var listEtFieldName : List<EditText>
	private lateinit var listEtFieldValue : List<EditText>
	private lateinit var listFieldNameInvalidator : List<NetworkEmojiInvalidator>
	private lateinit var listFieldValueInvalidator : List<NetworkEmojiInvalidator>
	private lateinit var btnFields : View
	
	private lateinit var etMaxTootChars : EditText
	
	///////////////////////////////////////////////////
	
	internal var visibility = TootVisibility.Public
	
	private var uriCameraImage : Uri? = null
	
	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		
		App1.setActivityTheme(this, false)
		this.pref = App1.pref
		
		initUI()
		
		val a = SavedAccount.loadAccount(this, intent.getLongExtra(KEY_ACCOUNT_DB_ID, - 1L))
		if(a == null) {
			finish()
			return
		}
		this.account = a
		
		loadUIFromData(account)
		
		initializeProfile()
		
		btnOpenBrowser.text = getString(R.string.open_instance_website, account.host)
	}
	
	override fun onStop() {
		PollingWorker.queueUpdateNotification(this)
		super.onStop()
	}
	
	override fun onActivityResult(requestCode : Int, resultCode : Int, data : Intent?) {
		when(requestCode) {
			REQUEST_CODE_ACCT_CUSTOMIZE -> {
				if(resultCode == Activity.RESULT_OK) {
					showAcctColor()
				}
			}
			
			REQUEST_CODE_NOTIFICATION_SOUND -> {
				if(resultCode == Activity.RESULT_OK) {
					// RINGTONE_PICKERからの選択されたデータを取得する
					val uri = data?.extras?.get(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
					if(uri is Uri) {
						notification_sound_uri = uri.toString()
						saveUIToData()
						//			Ringtone ringtone = RingtoneManager.getRingtone(getApplicationContext(), uri);
						//			TextView ringView = (TextView) findViewById(R.id.ringtone);
						//			ringView.setText(ringtone.getTitle(getApplicationContext()));
						//			ringtone.setStreamType(AudioManager.STREAM_ALARM);
						//			ringtone.play();
						//			SystemClock.sleep(1000);
						//			ringtone.stop();
					}
				}
			}
			
			REQUEST_CODE_AVATAR_ATTACHMENT, REQUEST_CODE_HEADER_ATTACHMENT -> {
				
				if(resultCode == Activity.RESULT_OK && data != null) {
					data.handleGetContentResult(contentResolver).firstOrNull()?.let {
						addAttachment(
							requestCode,
							it.uri,
							it.mimeType?.notEmpty() ?: contentResolver.getType(it.uri)
						)
					}
				}
			}
			
			REQUEST_CODE_AVATAR_CAMERA, REQUEST_CODE_HEADER_CAMERA -> {
				
				if(resultCode != Activity.RESULT_OK) {
					// 失敗したら DBからデータを削除
					val uriCameraImage = this@ActAccountSetting.uriCameraImage
					if(uriCameraImage != null) {
						contentResolver.delete(uriCameraImage, null, null)
						this@ActAccountSetting.uriCameraImage = null
					}
				} else {
					// 画像のURL
					val uri = data?.data ?: uriCameraImage
					if(uri != null) {
						val type = contentResolver.getType(uri)
						addAttachment(requestCode, uri, type)
					}
				}
			}
			
			else -> super.onActivityResult(requestCode, resultCode, data)
		}
	}
	
	var density : Float = 1f
	
	private fun initUI() {
		this.density = resources.displayMetrics.density
		this.handler = Handler()
		setContentView(R.layout.act_account_setting)
		
		val root :View = findViewById(R.id.svContent)
		
		Styler.fixHorizontalPadding(root)

		ActAppSettingChild.setSwitchColor(this,pref,root)
		
		tvInstance = findViewById(R.id.tvInstance)
		tvUser = findViewById(R.id.tvUser)
		btnAccessToken = findViewById(R.id.btnAccessToken)
		btnInputAccessToken = findViewById(R.id.btnInputAccessToken)
		btnAccountRemove = findViewById(R.id.btnAccountRemove)
		btnLoadPreference = findViewById(R.id.btnLoadPreference)
		btnVisibility = findViewById(R.id.btnVisibility)
		swNSFWOpen = findViewById(R.id.swNSFWOpen)
		swDontShowTimeout = findViewById(R.id.swDontShowTimeout)
		swExpandCW = findViewById(R.id.swExpandCW)
		swMarkSensitive = findViewById(R.id.swMarkSensitive)
		btnOpenBrowser = findViewById(R.id.btnOpenBrowser)
		btnPushSubscription = findViewById(R.id.btnPushSubscription)
		cbNotificationMention = findViewById(R.id.cbNotificationMention)
		cbNotificationBoost = findViewById(R.id.cbNotificationBoost)
		cbNotificationFavourite = findViewById(R.id.cbNotificationFavourite)
		cbNotificationFollow = findViewById(R.id.cbNotificationFollow)
		cbNotificationReaction = findViewById(R.id.cbNotificationReaction)
		cbNotificationVote = findViewById(R.id.cbNotificationVote)
		
		cbConfirmFollow = findViewById(R.id.cbConfirmFollow)
		cbConfirmFollowLockedUser = findViewById(R.id.cbConfirmFollowLockedUser)
		cbConfirmUnfollow = findViewById(R.id.cbConfirmUnfollow)
		cbConfirmBoost = findViewById(R.id.cbConfirmBoost)
		cbConfirmFavourite = findViewById(R.id.cbConfirmFavourite)
		cbConfirmUnboost = findViewById(R.id.cbConfirmUnboost)
		cbConfirmUnfavourite = findViewById(R.id.cbConfirmUnfavourite)
		cbConfirmToot = findViewById(R.id.cbConfirmToot)
		
		tvUserCustom = findViewById(R.id.tvUserCustom)
		btnUserCustom = findViewById(R.id.btnUserCustom)
		
		ivProfileHeader = findViewById(R.id.ivProfileHeader)
		ivProfileAvatar = findViewById(R.id.ivProfileAvatar)
		btnProfileAvatar = findViewById(R.id.btnProfileAvatar)
		btnProfileHeader = findViewById(R.id.btnProfileHeader)
		etDisplayName = findViewById(R.id.etDisplayName)
		etDefaultText = findViewById(R.id.etDefaultText)
		etMaxTootChars = findViewById(R.id.etMaxTootChars)
		btnDisplayName = findViewById(R.id.btnDisplayName)
		etNote = findViewById(R.id.etNote)
		btnNote = findViewById(R.id.btnNote)
		cbLocked = findViewById(R.id.cbLocked)
		
		listEtFieldName = arrayOf(
			R.id.etFieldName1,
			R.id.etFieldName2,
			R.id.etFieldName3,
			R.id.etFieldName4
		).map { requireNotNull(findViewById<EditText>(it)) }
		
		listEtFieldValue = arrayOf(
			R.id.etFieldValue1,
			R.id.etFieldValue2,
			R.id.etFieldValue3,
			R.id.etFieldValue4
		).map { requireNotNull(findViewById<EditText>(it)) }
		
		btnFields = findViewById(R.id.btnFields)
		
		btnOpenBrowser.setOnClickListener(this)
		btnPushSubscription.setOnClickListener(this)
		btnAccessToken.setOnClickListener(this)
		btnInputAccessToken.setOnClickListener(this)
		btnAccountRemove.setOnClickListener(this)
		btnLoadPreference.setOnClickListener(this)
		btnVisibility.setOnClickListener(this)
		btnUserCustom.setOnClickListener(this)
		btnProfileAvatar.setOnClickListener(this)
		btnProfileHeader.setOnClickListener(this)
		btnDisplayName.setOnClickListener(this)
		btnNote.setOnClickListener(this)
		btnFields.setOnClickListener(this)
		
		swNSFWOpen.setOnCheckedChangeListener(this)
		swDontShowTimeout.setOnCheckedChangeListener(this)
		swExpandCW.setOnCheckedChangeListener(this)
		swMarkSensitive.setOnCheckedChangeListener(this)
		cbNotificationMention.setOnCheckedChangeListener(this)
		cbNotificationBoost.setOnCheckedChangeListener(this)
		cbNotificationFavourite.setOnCheckedChangeListener(this)
		cbNotificationFollow.setOnCheckedChangeListener(this)
		cbNotificationReaction.setOnCheckedChangeListener(this)
		cbNotificationVote.setOnCheckedChangeListener(this)
		cbLocked.setOnCheckedChangeListener(this)
		
		cbConfirmFollow.setOnCheckedChangeListener(this)
		cbConfirmFollowLockedUser.setOnCheckedChangeListener(this)
		cbConfirmUnfollow.setOnCheckedChangeListener(this)
		cbConfirmBoost.setOnCheckedChangeListener(this)
		cbConfirmFavourite.setOnCheckedChangeListener(this)
		cbConfirmUnboost.setOnCheckedChangeListener(this)
		cbConfirmUnfavourite.setOnCheckedChangeListener(this)
		cbConfirmToot.setOnCheckedChangeListener(this)
		
		btnNotificationSoundEdit = findViewById(R.id.btnNotificationSoundEdit)
		btnNotificationSoundReset = findViewById(R.id.btnNotificationSoundReset)
		btnNotificationStyleEdit = findViewById(R.id.btnNotificationStyleEdit)
		btnNotificationSoundEdit.setOnClickListener(this)
		btnNotificationSoundReset.setOnClickListener(this)
		btnNotificationStyleEdit.setOnClickListener(this)
		
		name_invalidator = NetworkEmojiInvalidator(handler, etDisplayName)
		note_invalidator = NetworkEmojiInvalidator(handler, etNote)
		default_text_invalidator = NetworkEmojiInvalidator(handler, etDefaultText)

		listFieldNameInvalidator = listEtFieldName.map {
			NetworkEmojiInvalidator(handler, it)
		}
		
		listFieldValueInvalidator = listEtFieldValue.map {
			NetworkEmojiInvalidator(handler, it)
		}
		
		etDefaultText.addTextChangedListener(object : TextWatcher {
			override fun afterTextChanged(s : Editable?) {
				saveUIToData()
			}
			
			override fun beforeTextChanged(
				s : CharSequence?,
				start : Int,
				count : Int,
				after : Int
			) {
			}
			
			override fun onTextChanged(
				s : CharSequence?,
				start : Int,
				before : Int,
				count : Int
			) {
			}
		})
		
		etMaxTootChars.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(
				s : CharSequence?,
				start : Int,
				count : Int,
				after : Int
			) {
			}
			
			override fun onTextChanged(
				s : CharSequence?,
				start : Int,
				before : Int,
				count : Int
			) {
			}
			
			override fun afterTextChanged(s : Editable?) {
				val num = etMaxTootChars.parseInt()
				if( num != null && num >= 0){
					saveUIToData()
				}
			}
		})
		
	}
	
	private fun EditText.parseInt():Int?{
		val sv = this.text?.toString() ?: return null
		return try{
			Integer.parseInt(sv,10)
		}catch(ex:Throwable){
			null
		}
	}
	
	private fun loadUIFromData(a : SavedAccount) {
		
		this.full_acct = a.acct
		
		tvInstance.text = a.host
		tvUser.text = a.acct
		
		this.visibility = a.visibility
		
		loading = true
		
		swNSFWOpen.isChecked = a.dont_hide_nsfw
		swDontShowTimeout.isChecked = a.dont_show_timeout
		swExpandCW.isChecked = a.expand_cw
		swMarkSensitive.isChecked = a.default_sensitive
		cbNotificationMention.isChecked = a.notification_mention
		cbNotificationBoost.isChecked = a.notification_boost
		cbNotificationFavourite.isChecked = a.notification_favourite
		cbNotificationFollow.isChecked = a.notification_follow
		cbNotificationReaction.isChecked = a.notification_reaction
		cbNotificationVote.isChecked = a.notification_vote
		
		cbConfirmFollow.isChecked = a.confirm_follow
		cbConfirmFollowLockedUser.isChecked = a.confirm_follow_locked
		cbConfirmUnfollow.isChecked = a.confirm_unfollow
		cbConfirmBoost.isChecked = a.confirm_boost
		cbConfirmFavourite.isChecked = a.confirm_favourite
		cbConfirmUnboost.isChecked = a.confirm_unboost
		cbConfirmUnfavourite.isChecked = a.confirm_unfavourite
		
		
		cbConfirmToot.isChecked = a.confirm_post
		
		notification_sound_uri = a.sound_uri
		
		etDefaultText.setText(a.default_text)
		etMaxTootChars.setText(a.max_toot_chars.toString())

		loading = false
		
		val enabled = ! a.isPseudo
		btnAccessToken.isEnabled = enabled
		btnInputAccessToken.isEnabled = enabled
		btnVisibility.isEnabled = enabled
		btnPushSubscription.isEnabled = enabled
		
		btnNotificationSoundEdit.isEnabled = Build.VERSION.SDK_INT < 26 && enabled
		btnNotificationSoundReset.isEnabled = Build.VERSION.SDK_INT < 26 && enabled
		btnNotificationStyleEdit.isEnabled = Build.VERSION.SDK_INT >= 26 && enabled
		
		cbNotificationMention.isEnabled = enabled
		cbNotificationBoost.isEnabled = enabled
		cbNotificationFavourite.isEnabled = enabled
		cbNotificationFollow.isEnabled = enabled
		cbNotificationReaction.isEnabled = enabled
		cbNotificationVote.isEnabled = enabled
		
		cbConfirmFollow.isEnabled = enabled
		cbConfirmFollowLockedUser.isEnabled = enabled
		cbConfirmUnfollow.isEnabled = enabled
		cbConfirmBoost.isEnabled = enabled
		cbConfirmFavourite.isEnabled = enabled
		cbConfirmUnboost.isEnabled = enabled
		cbConfirmUnfavourite.isEnabled = enabled
		cbConfirmToot.isEnabled = enabled
		
		
		
		showVisibility()
		showAcctColor()
	}
	
	private fun showAcctColor() {
		val ac = AcctColor.load(full_acct)
		tvUserCustom.backgroundColor = ac.color_bg
		tvUserCustom.text = ac.nickname?.notEmpty() ?: full_acct
		tvUserCustom.textColor = ac.color_fg.notZero()
			?: getAttributeColor(this, R.attr.colorTimeSmall)
	}
	
	private fun saveUIToData() {
		if(! ::account.isInitialized) return
		
		if(loading) return
		
		account.visibility = visibility
		account.dont_hide_nsfw = swNSFWOpen.isChecked
		account.dont_show_timeout = swDontShowTimeout.isChecked
		account.expand_cw = swExpandCW.isChecked
		account.default_sensitive = swMarkSensitive.isChecked
		account.notification_mention = cbNotificationMention.isChecked
		account.notification_boost = cbNotificationBoost.isChecked
		account.notification_favourite = cbNotificationFavourite.isChecked
		account.notification_follow = cbNotificationFollow.isChecked
		account.notification_reaction = cbNotificationReaction.isChecked
		account.notification_vote = cbNotificationVote.isChecked
		
		account.sound_uri = notification_sound_uri ?: ""
		
		account.confirm_follow = cbConfirmFollow.isChecked
		account.confirm_follow_locked = cbConfirmFollowLockedUser.isChecked
		account.confirm_unfollow = cbConfirmUnfollow.isChecked
		account.confirm_boost = cbConfirmBoost.isChecked
		account.confirm_favourite = cbConfirmFavourite.isChecked
		account.confirm_unboost = cbConfirmUnboost.isChecked
		account.confirm_unfavourite = cbConfirmUnfavourite.isChecked
		account.confirm_post = cbConfirmToot.isChecked
		account.default_text = etDefaultText.text.toString()

		val num = etMaxTootChars.parseInt()
		account.max_toot_chars = if( num != null && num >= 0){
			num
		}else{
			0
		}

		account.saveSetting()
		
	}
	
	override fun onCheckedChanged(buttonView : CompoundButton, isChecked : Boolean) {
		if(buttonView == cbLocked) {
			if(! profile_busy) sendLocked(isChecked)
		} else {
			saveUIToData()
		}
	}
	
	override fun onClick(v : View) {
		when(v.id) {
			R.id.btnAccessToken -> performAccessToken()
			R.id.btnInputAccessToken -> inputAccessToken()
			
			R.id.btnAccountRemove -> performAccountRemove()
			R.id.btnLoadPreference -> performLoadPreference()
			R.id.btnVisibility -> performVisibility()
			R.id.btnOpenBrowser -> App1.openBrowser(
				this@ActAccountSetting,
				"https://" + account.host + "/"
			)
			R.id.btnPushSubscription -> startTest()
			
			R.id.btnUserCustom -> ActNickname.open(
				this,
				full_acct,
				false,
				REQUEST_CODE_ACCT_CUSTOMIZE
			)
			
			R.id.btnNotificationSoundEdit -> openNotificationSoundPicker()
			
			R.id.btnNotificationSoundReset -> {
				notification_sound_uri = ""
				saveUIToData()
			}
			
			R.id.btnProfileAvatar -> pickAvatarImage()
			
			R.id.btnProfileHeader -> pickHeaderImage()
			
			R.id.btnDisplayName -> sendDisplayName()
			
			R.id.btnNote -> sendNote()
			
			R.id.btnFields -> sendFields()
			
			R.id.btnNotificationStyleEdit -> if(Build.VERSION.SDK_INT >= 26) {
				val channel = NotificationHelper.createNotificationChannel(this, account)
				val intent = Intent("android.settings.CHANNEL_NOTIFICATION_SETTINGS")
				intent.putExtra(Settings.EXTRA_CHANNEL_ID, channel.id)
				intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
				startActivity(intent)
				
			}
		}
	}
	
	private fun showVisibility() {
		btnVisibility.text = Styler.getVisibilityString(this, account.isMisskey, visibility)
	}
	
	private fun performVisibility() {
		
		val list = if(account.isMisskey) {
			arrayOf(
				//	TootVisibility.WebSetting,
				TootVisibility.Public,
				TootVisibility.UnlistedHome,
				TootVisibility.PrivateFollowers,
				TootVisibility.LocalPublic,
				TootVisibility.LocalHome,
				TootVisibility.LocalFollowers,
				TootVisibility.DirectSpecified,
				TootVisibility.DirectPrivate
			)
		} else {
			arrayOf(
				TootVisibility.WebSetting,
				TootVisibility.Public,
				TootVisibility.UnlistedHome,
				TootVisibility.PrivateFollowers,
				TootVisibility.DirectSpecified
			)
		}
		
		val caption_list = list.map {
			Styler.getVisibilityCaption(this, account.isMisskey, it)
		}.toTypedArray()
		
		AlertDialog.Builder(this)
			.setTitle(R.string.choose_visibility)
			.setItems(caption_list) { _, which ->
				if(which in list.indices) {
					visibility = list[which]
					showVisibility()
					saveUIToData()
				}
			}
			.setNegativeButton(R.string.cancel, null)
			.show()
		
	}
	
	private fun performLoadPreference() {
		
		TootTaskRunner(this).run(account, object : TootTask {
			override fun background(client : TootApiClient) : TootApiResult? {
				return client.request("/api/v1/preferences")
			}
			
			override fun handleResult(result : TootApiResult?) {
				result ?: return
				
				val json = result.jsonObject
				if(json == null) {
					showToast(this@ActAccountSetting, true, result.error)
					return
				}
				
				var bChanged = false
				try {
					loading = true

					val tmpVisibility =
						TootVisibility.parseMastodon(json.parseString("posting:default:visibility"))
					if(tmpVisibility != null) {
						bChanged = true
						visibility = tmpVisibility
						showVisibility()
					}
					
					val tmpDefaultSensitive = json.parseBoolean("posting:default:sensitive")
					if(tmpDefaultSensitive != null) {
						bChanged = true
						swMarkSensitive.isChecked = tmpDefaultSensitive
					}
					
					val tmpExpandMedia = json.parseString("reading:expand:media")
					if(tmpExpandMedia?.isNotEmpty() == true) {
						bChanged = true
						swNSFWOpen.isChecked = (tmpExpandMedia == "show_all")
					}
					
					val tmpExpandCW = json.parseBoolean("reading:expand:spoilers")
					if(tmpExpandCW != null) {
						bChanged = true
						swExpandCW.isChecked = tmpExpandCW
					}
					
				} finally {
					loading = false
					if(bChanged) saveUIToData()
				}
			}
		})
	}
	
	///////////////////////////////////////////////////
	private fun performAccountRemove() {
		AlertDialog.Builder(this)
			.setTitle(R.string.confirm)
			.setMessage(R.string.confirm_account_remove)
			.setNegativeButton(R.string.cancel, null)
			.setPositiveButton(R.string.ok) { _, _ ->
				account.delete()
				
				val pref = Pref.pref(this@ActAccountSetting)
				if(account.db_id == Pref.lpTabletTootDefaultAccount(pref)) {
					pref.edit().put(Pref.lpTabletTootDefaultAccount, - 1L).apply()
				}
				
				finish()
				
				val task = @SuppressLint("StaticFieldLeak")
				object : AsyncTask<Void, Void, String?>() {
					
					fun unregister() {
						try {
							
							val install_id = PrefDevice.prefDevice(this@ActAccountSetting)
								.getString(PrefDevice.KEY_INSTALL_ID, null)
							if(install_id?.isEmpty() != false) {
								log.d("performAccountRemove: missing install_id")
								return
							}
							
							val tag = account.notification_tag
							if(tag?.isEmpty() != false) {
								log.d("performAccountRemove: missing notification_tag")
								return
							}
							
							val call = App1.ok_http_client.newCall(
								("instance_url=" + ("https://" + account.host).encodePercent()
									+ "&app_id=" + packageName.encodePercent()
									+ "&tag=" + tag
									)
									.toFormRequestBody()
									.toPost()
									.url(PollingWorker.APP_SERVER + "/unregister")
									.build()
							)
							
							val response = call.execute()
							
							log.e("performAccountRemove: %s", response)
							
						} catch(ex : Throwable) {
							log.trace(ex, "performAccountRemove failed.")
						}
						
					}
					
					override fun doInBackground(vararg params : Void) : String? {
						unregister()
						return null
					}
					
					override fun onCancelled(s : String?) {
						onPostExecute(s)
					}
					
					override fun onPostExecute(s : String?) {
					
					}
				}
				task.executeOnExecutor(App1.task_executor)
			}
			.show()
		
	}
	
	///////////////////////////////////////////////////
	private fun performAccessToken() {
		
		TootTaskRunner(this@ActAccountSetting).run(account, object : TootTask {
			override fun background(client : TootApiClient) : TootApiResult? {
				return client.authentication1(
					Pref.spClientName(this@ActAccountSetting),
					forceUpdateClient = false
				)
			}
			
			override fun handleResult(result : TootApiResult?) {
				result ?: return // cancelled.
				
				val uri = result.string.mayUri()
				val error = result.error
				when {
					uri != null -> {
						val data = Intent()
						data.data = uri
						setResult(Activity.RESULT_OK, data)
						finish()
					}
					
					error != null -> {
						showToast(this@ActAccountSetting, true, error)
						log.e("can't get oauth browser URL. $error")
					}
				}
			}
		})
		
	}
	
	private fun inputAccessToken() {
		
		val data = Intent()
		data.putExtra(EXTRA_DB_ID, account.db_id)
		setResult(RESULT_INPUT_ACCESS_TOKEN, data)
		finish()
	}
	
	private fun openNotificationSoundPicker() {
		val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
		intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
		intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, R.string.notification_sound)
		intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
		intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
		
		notification_sound_uri.mayUri()?.let { uri ->
			intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri)
		}
		
		val chooser = Intent.createChooser(intent, getString(R.string.notification_sound))
		startActivityForResult(chooser, REQUEST_CODE_NOTIFICATION_SOUND)
	}
	
	//////////////////////////////////////////////////////////////////////////
	
	private fun initializeProfile() {
		// 初期状態
		val question_id = R.drawable.wide_question
		ivProfileAvatar.setErrorImage(defaultColorIcon(this, question_id))
		ivProfileAvatar.setDefaultImage(defaultColorIcon(this, question_id))
		
		val loadingText = when(account.isPseudo) {
			true -> "(disabled for pseudo account)"
			else -> "(loading…)"
		}
		etDisplayName.setText(loadingText)
		etNote.setText(loadingText)
		
		// 初期状態では編集不可能
		btnProfileAvatar.isEnabled = false
		btnProfileHeader.isEnabled = false
		etDisplayName.isEnabled = false
		btnDisplayName.isEnabled = false
		etNote.isEnabled = false
		btnNote.isEnabled = false
		cbLocked.isEnabled = false
		
		for(et in listEtFieldName) {
			et.setText(loadingText)
			et.isEnabled = false
		}
		for(et in listEtFieldValue) {
			et.setText(loadingText)
			et.isEnabled = false
		}
		
		// 疑似アカウントなら編集不可のまま
		if(! account.isPseudo) loadProfile()
	}
	
	private fun loadProfile() {
		// サーバから情報をロードする
		
		TootTaskRunner(this).run(account, object : TootTask {
			
			var data : TootAccount? = null
			override fun background(client : TootApiClient) : TootApiResult? {
				if(account.isMisskey) {
					val params = account.putMisskeyApiToken(JSONObject())
					val result = client.request("/api/i", params.toPostRequestBuilder())
					val jsonObject = result?.jsonObject
					if(jsonObject != null) {
						data = TootParser(this@ActAccountSetting, account).account(jsonObject)
						if(data == null) return TootApiResult("TootAccount parse failed.")
					}
					return result
					
				} else {
					
					var result = account.checkConfirmed(this@ActAccountSetting, client)
					if(result == null || result.error != null) return result
					
					result = client.request("/api/v1/accounts/verify_credentials")
					val jsonObject = result?.jsonObject
					if(jsonObject != null) {
						data = TootParser(this@ActAccountSetting, account).account(jsonObject)
						if(data == null) return TootApiResult("TootAccount parse failed.")
					}
					return result
					
				}
			}
			
			override fun handleResult(result : TootApiResult?) {
				if(result == null) return  // cancelled.
				
				val data = this.data
				if(data != null) {
					showProfile(data)
				} else {
					showToast(this@ActAccountSetting, true, result.error)
				}
				
			}
		})
	}
	
	var profile_busy : Boolean = false
	
	internal fun showProfile(src : TootAccount) {
		profile_busy = true
		try {
			ivProfileAvatar.setImageUrl(
				App1.pref,
				Styler.calcIconRound(ivProfileAvatar.layoutParams),
				src.avatar_static,
				src.avatar
			)
			
			ivProfileHeader.setImageUrl(
				App1.pref,
				0f,
				src.header_static,
				src.header
			)
			
			val decodeOptions = DecodeOptions(
				context = this@ActAccountSetting,
				linkHelper = account,
				emojiMapProfile = src.profile_emojis,
				emojiMapCustom = src.custom_emojis
			)
			// fieldsのnameにはカスタム絵文字が適用されない
			val decodeOptionsNoCustomEmoji = DecodeOptions(
				context = this@ActAccountSetting,
				linkHelper = account,
				emojiMapProfile = src.profile_emojis
			)
			
			val display_name = src.display_name
			val name = decodeOptions.decodeEmoji(display_name)
			etDisplayName.setText(name)
			name_invalidator.register(name)
			
			val noteString = src.source?.note ?: src.note
			val noteSpannable = when {
				account.isMisskey -> {
					SpannableString(noteString ?: "")
				}
				
				else -> {
					decodeOptions.decodeEmoji(noteString)
				}
			}
			
			etNote.setText(noteSpannable)
			note_invalidator.register(noteSpannable)
			
			cbLocked.isChecked = src.locked
			
			// 編集可能にする
			btnProfileAvatar.isEnabled = true
			btnProfileHeader.isEnabled = true
			etDisplayName.isEnabled = true
			btnDisplayName.isEnabled = true
			etNote.isEnabled = true
			btnNote.isEnabled = true
			cbLocked.isEnabled = true
			
			if(src.source?.fields != null) {
				val fields = src.source.fields
				listEtFieldName.forEachIndexed { i, et ->
					val text = decodeOptionsNoCustomEmoji.decodeEmoji(
						when {
							i >= fields.size -> ""
							else -> fields[i].name
						}
					)
					et.setText(text)
					et.isEnabled = true
					val invalidator = NetworkEmojiInvalidator(et.handler, et)
					invalidator.register(text)
				}
				
				listEtFieldValue.forEachIndexed { i, et ->
					val text = decodeOptions.decodeEmoji(
						when {
							i >= fields.size -> ""
							else -> fields[i].value
						}
					)
					et.setText(text)
					et.isEnabled = true
					val invalidator = NetworkEmojiInvalidator(et.handler, et)
					invalidator.register(text)
				}
				
			} else {
				val fields = src.fields
				
				listEtFieldName.forEachIndexed { i, et ->
					val text = decodeOptionsNoCustomEmoji.decodeEmoji(
						when {
							fields == null || i >= fields.size -> ""
							else -> fields[i].name
						}
					)
					et.setText(text)
					et.isEnabled = true
					val invalidator = NetworkEmojiInvalidator(et.handler, et)
					invalidator.register(text)
				}
				
				listEtFieldValue.forEachIndexed { i, et ->
					val text = decodeOptions.decodeHTML(
						when {
							fields == null || i >= fields.size -> ""
							else -> fields[i].value
						}
					)
					et.text = text
					et.isEnabled = true
					val invalidator = NetworkEmojiInvalidator(et.handler, et)
					invalidator.register(text)
				}
			}
			
		} finally {
			profile_busy = false
		}
	}
	
	private fun updateCredential(key : String, value : Any) {
		updateCredential(listOf(Pair(key, value)))
	}
	
	private fun updateCredential(args : List<Pair<String, Any>>) {
		
		TootTaskRunner(this).run(account, object : TootTask {
			
			private fun uploadImageMisskey(
				client : TootApiClient,
				opener : InputStreamOpener
			) : Pair<TootApiResult?, TootAttachment?> {
				
				val size = getStreamSize(true, opener.open())
				
				val multipart_builder = MultipartBody.Builder()
					.setType(MultipartBody.FORM)
				
				val apiKey =
					account.token_info?.parseString(TootApiClient.KEY_API_KEY_MISSKEY)
				if(apiKey?.isNotEmpty() == true) {
					multipart_builder.addFormDataPart("i", apiKey)
				}
				
				multipart_builder.addFormDataPart(
					"file",
					getDocumentName(contentResolver, opener.uri),
					object : RequestBody() {
						override fun contentType() : MediaType? {
							return opener.mimeType.toMediaType()
						}
						
						@Throws(IOException::class)
						override fun contentLength() : Long {
							return size
						}
						
						@Throws(IOException::class)
						override fun writeTo(sink : BufferedSink) {
							opener.open().use { inData ->
								val tmp = ByteArray(4096)
								while(true) {
									val r = inData.read(tmp, 0, tmp.size)
									if(r <= 0) break
									sink.write(tmp, 0, r)
								}
							}
						}
					}
				)
				
				var ta : TootAttachment? = null
				val result = client.request(
					"/api/drive/files/create",
					multipart_builder.build().toPost()
				)?.also { result ->
					val jsonObject = result.jsonObject
					if(jsonObject != null) {
						ta = parseItem(::TootAttachment, ServiceType.MISSKEY, jsonObject)
						if(ta == null) result.error = "TootAttachment.parse failed"
					}
				}
				
				return Pair(result, ta)
			}
			
			var data : TootAccount? = null
			override fun background(client : TootApiClient) : TootApiResult? {
				
				try {
					if(account.isMisskey) {
						val params = account.putMisskeyApiToken()
						
						for(arg in args) {
							val key = arg.first
							val value = arg.second
							
							val misskeyKey = when(key) {
								"header" -> "bannerId"
								"avatar" -> "avatarId"
								"display_name" -> "name"
								"note" -> "description"
								"locked" -> "isLocked"
								else -> return TootApiResult("Misskey does not support property '${key}'")
							}
							
							when(value) {
								is String -> params.put(misskeyKey, value)
								is Boolean -> params.put(misskeyKey, value)
								
								is InputStreamOpener -> {
									val (result, ta) = uploadImageMisskey(client, value)
									ta ?: return result
									params.put(misskeyKey, ta.id)
								}
							}
						}
						
						val result =
							client.request("/api/i/update", params.toPostRequestBuilder())
						val jsonObject = result?.jsonObject
						if(jsonObject != null) {
							val a = TootParser(this@ActAccountSetting, account).account(jsonObject)
								?: return TootApiResult("TootAccount parse failed.")
							data = a
						}
						
						return result
						
					} else {
						val multipart_body_builder = MultipartBody.Builder()
							.setType(MultipartBody.FORM)
						
						for(arg in args) {
							val key = arg.first
							val value = arg.second
							
							if(value is String) {
								multipart_body_builder.addFormDataPart(key, value)
							} else if(value is Boolean) {
								multipart_body_builder.addFormDataPart(
									key,
									if(value) "true" else "false"
								)
								
							} else if(value is InputStreamOpener) {
								
								val fileName = "%x".format(System.currentTimeMillis())
								
								multipart_body_builder.addFormDataPart(
									key,
									fileName,
									object : RequestBody() {
										override fun contentType() : MediaType? {
											return value.mimeType.toMediaType()
										}
										
										override fun writeTo(sink : BufferedSink) {
											value.open().use { inData ->
												val tmp = ByteArray(4096)
												while(true) {
													val r = inData.read(tmp, 0, tmp.size)
													if(r <= 0) break
													sink.write(tmp, 0, r)
												}
											}
										}
									})
							}
						}
						
						val result = client.request(
							"/api/v1/accounts/update_credentials",
							multipart_body_builder.build().toPatch()
						)
						val jsonObject = result?.jsonObject
						if(jsonObject != null) {
							val a = TootParser(this@ActAccountSetting, account).account(jsonObject)
								?: return TootApiResult("TootAccount parse failed.")
							data = a
						}
						
						return result
						
					}
					
				} finally {
					for(arg in args) {
						val value = arg.second
						(value as? InputStreamOpener)?.deleteTempFile()
					}
				}
			}
			
			override fun handleResult(result : TootApiResult?) {
				if(result == null) return  // cancelled.
				
				val data = this.data
				if(data != null) {
					showProfile(data)
				} else {
					showToast(this@ActAccountSetting, true, result.error)
					for(arg in args) {
						val key = arg.first
						val value = arg.second
						if(key == "locked" && value is Boolean) {
							profile_busy = true
							cbLocked.isChecked = ! value
							profile_busy = false
						}
					}
				}
			}
		})
		
	}
	
	private fun sendDisplayName(bConfirmed : Boolean = false) {
		val sv = etDisplayName.text.toString()
		if(! bConfirmed) {
			val length = sv.codePointCount(0, sv.length)
			if(length > max_length_display_name) {
				AlertDialog.Builder(this)
					.setMessage(
						getString(
							R.string.length_warning,
							getString(R.string.display_name),
							length,
							max_length_display_name
						)
					)
					.setNegativeButton(R.string.cancel, null)
					.setPositiveButton(R.string.ok) { _, _ -> sendDisplayName(bConfirmed = true) }
					.setCancelable(true)
					.show()
				return
			}
		}
		updateCredential("display_name", EmojiDecoder.decodeShortCode(sv))
	}
	
	private fun sendNote(bConfirmed : Boolean = false) {
		val sv = etNote.text.toString()
		if(! bConfirmed) {
			
			val length = TootStatus.countText(sv)
			if(length > max_length_note) {
				AlertDialog.Builder(this)
					.setMessage(
						getString(
							R.string.length_warning,
							getString(R.string.note),
							length,
							max_length_note
						)
					)
					.setNegativeButton(R.string.cancel, null)
					.setPositiveButton(R.string.ok) { _, _ -> sendNote(bConfirmed = true) }
					.setCancelable(true)
					.show()
				return
			}
		}
		updateCredential("note", EmojiDecoder.decodeShortCode(sv))
	}
	
	private fun sendLocked(willLocked : Boolean) {
		updateCredential("locked", willLocked)
	}
	
	private fun sendFields(bConfirmed : Boolean = false) {
		val args = ArrayList<Pair<String, String>>()
		var lengthLongest = - 1
		for(i in listEtFieldName.indices) {
			val k = listEtFieldName[i].text.toString().trim()
			val v = listEtFieldValue[i].text.toString().trim()
			args.add(Pair("fields_attributes[$i][name]", k))
			args.add(Pair("fields_attributes[$i][value]", v))
			
			lengthLongest = max(
				lengthLongest,
				max(
					k.codePointCount(0, k.length),
					v.codePointCount(0, v.length)
				)
			)
		}
		if(! bConfirmed && lengthLongest > max_length_fields) {
			AlertDialog.Builder(this)
				.setMessage(
					getString(
						R.string.length_warning,
						getString(R.string.profile_metadata),
						lengthLongest,
						max_length_fields
					)
				)
				.setNegativeButton(R.string.cancel, null)
				.setPositiveButton(R.string.ok) { _, _ -> sendFields(bConfirmed = true) }
				.setCancelable(true)
				.show()
			return
		}
		
		updateCredential(args)
	}
	
	private fun pickAvatarImage() {
		openPicker(PERMISSION_REQUEST_AVATAR)
	}
	
	private fun pickHeaderImage() {
		openPicker(PERMISSION_REQUEST_HEADER)
	}
	
	private fun openPicker(permission_request_code : Int) {
		val permissionCheck = ContextCompat.checkSelfPermission(
			this,
			Manifest.permission.WRITE_EXTERNAL_STORAGE
		)
		if(permissionCheck != PackageManager.PERMISSION_GRANTED) {
			preparePermission(permission_request_code)
			return
		}
		
		val a = ActionsDialog()
		a.addAction(getString(R.string.pick_image)) {
			performAttachment(
				if(permission_request_code == PERMISSION_REQUEST_AVATAR)
					REQUEST_CODE_AVATAR_ATTACHMENT
				else
					REQUEST_CODE_HEADER_ATTACHMENT
			)
		}
		a.addAction(getString(R.string.image_capture)) {
			performCamera(
				if(permission_request_code == PERMISSION_REQUEST_AVATAR)
					REQUEST_CODE_AVATAR_CAMERA
				else
					REQUEST_CODE_HEADER_CAMERA
			)
		}
		a.show(this, null)
	}
	
	private fun preparePermission(request_code : Int) {
		if(Build.VERSION.SDK_INT >= 23) {
			// No explanation needed, we can request the permission.
			
			ActivityCompat.requestPermissions(
				this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), request_code
			)
			return
		}
		showToast(this, true, R.string.missing_permission_to_access_media)
	}
	
	override fun onRequestPermissionsResult(
		requestCode : Int, permissions : Array<String>, grantResults : IntArray
	) {
		when(requestCode) {
			PERMISSION_REQUEST_AVATAR, PERMISSION_REQUEST_HEADER ->
				// If request is cancelled, the result arrays are empty.
				if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					openPicker(requestCode)
				} else {
					showToast(this, true, R.string.missing_permission_to_access_media)
				}
		}
	}
	
	private fun performAttachment(request_code : Int) {
		try {
			val intent = intentGetContent(false, getString(R.string.pick_image), arrayOf("image/*"))
			startActivityForResult(intent, request_code)
		} catch(ex : Throwable) {
			log.trace(ex, "performAttachment failed.")
			showToast(this, ex, "performAttachment failed.")
		}
		
	}
	
	private fun performCamera(request_code : Int) {
		
		try {
			// カメラで撮影
			val filename = System.currentTimeMillis().toString() + ".jpg"
			val values = ContentValues()
			values.put(MediaStore.Images.Media.TITLE, filename)
			values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
			uriCameraImage =
				contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
			
			val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
			intent.putExtra(MediaStore.EXTRA_OUTPUT, uriCameraImage)
			
			startActivityForResult(intent, request_code)
		} catch(ex : Throwable) {
			log.trace(ex, "opening camera app failed.")
			showToast(this, ex, "opening camera app failed.")
		}
		
	}
	
	internal interface InputStreamOpener {
		
		val mimeType : String
		
		val uri : Uri
		
		@Throws(IOException::class)
		fun open() : InputStream
		
		fun deleteTempFile()
	}
	
	private fun createOpener(uriArg : Uri, mime_type : String) : InputStreamOpener {
		
		while(true) {
			try {
				
				// 画像の種別
				val is_jpeg = MIME_TYPE_JPEG == mime_type
				val is_png = MIME_TYPE_PNG == mime_type
				if(! is_jpeg && ! is_png) {
					log.d("createOpener: source is not jpeg or png")
					break
				}
				
				// 設定からリサイズ指定を読む
				val resize_to = 1280
				
				val bitmap = createResizedBitmap(this, uriArg, resize_to)
				if(bitmap != null) {
					try {
						val cache_dir = externalCacheDir
						if(cache_dir == null) {
							showToast(this, false, "getExternalCacheDir returns null.")
							break
						}
						
						cache_dir.mkdir()
						
						val temp_file = File(
							cache_dir,
							"tmp." + System.currentTimeMillis() + "." + Thread.currentThread().id
						)
						FileOutputStream(temp_file).use { os ->
							if(is_jpeg) {
								bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os)
							} else {
								bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
							}
						}
						
						return object : InputStreamOpener {
							
							override val mimeType : String
								get() = mime_type
							
							override val uri : Uri
								get() = uriArg
							
							@Throws(IOException::class)
							override fun open() = FileInputStream(temp_file)
							
							override fun deleteTempFile() {
								temp_file.delete()
							}
						}
					} finally {
						bitmap.recycle()
					}
				}
				
			} catch(ex : Throwable) {
				log.trace(ex, "Resizing image failed.")
				showToast(this, ex, "Resizing image failed.")
			}
			
			break
		}
		
		return object : InputStreamOpener {
			
			override val mimeType : String
				get() = mime_type
			
			override val uri : Uri
				get() = uriArg
			
			@Throws(IOException::class)
			override fun open() : InputStream {
				return contentResolver.openInputStream(uri) ?: error("openInputStream returns null")
			}
			
			override fun deleteTempFile() {
			
			}
		}
	}
	
	private fun addAttachment(request_code : Int, uri : Uri, mime_type : String?) {
		
		if(mime_type == null) {
			showToast(this, false, "mime type is not provided.")
			return
		}
		
		if(! mime_type.startsWith("image/")) {
			showToast(this, false, "mime type is not image.")
			return
		}
		
		val progress = ProgressDialogEx(this)
		
		val task = @SuppressLint("StaticFieldLeak")
		object : AsyncTask<Void, Void, InputStreamOpener?>() {
			
			override fun doInBackground(vararg params : Void) : InputStreamOpener? {
				return try {
					createOpener(uri, mime_type)
				} catch(ex : Throwable) {
					showToast(this@ActAccountSetting, ex, "image converting failed.")
					null
				}
			}
			
			override fun onPostExecute(opener : InputStreamOpener?) {
				progress.dismissSafe()
				if(opener != null) {
					updateCredential(
						when(request_code) {
							REQUEST_CODE_HEADER_ATTACHMENT, REQUEST_CODE_HEADER_CAMERA -> "header"
							else -> "avatar"
						},
						opener
					)
				}
			}
			
		}
		
		progress.isIndeterminate = true
		progress.setMessage("preparing image…")
		progress.setOnCancelListener {
			task.cancel(true)
		}
		progress.show()
		
		task.executeOnExecutor(App1.task_executor)
	}
	
	@SuppressLint("StaticFieldLeak")
	private fun startTest() {
		TootTaskRunner(this).run(account, object : TootTask {
			val wps = PushSubscriptionHelper(
				this@ActAccountSetting,
				account,
				verbose = true
			)
			
			override fun background(client : TootApiClient) : TootApiResult? {
				return wps.updateSubscription(client)
			}
			
			override fun handleResult(result : TootApiResult?) {
				result ?: return
				val log = wps.log
				if(log.isNotEmpty()) {
					AlertDialog.Builder(this@ActAccountSetting)
						.setMessage(log)
						.setPositiveButton(R.string.close, null)
						.show()
				}
			}
		})
	}
	
}

