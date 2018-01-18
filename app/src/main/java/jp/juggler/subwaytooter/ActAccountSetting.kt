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
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Base64
import android.util.Base64OutputStream
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView

import org.apache.commons.io.IOUtils

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.TootTask
import jp.juggler.subwaytooter.api.TootTaskRunner
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.EmojiDecoder
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.NetworkEmojiInvalidator
import jp.juggler.subwaytooter.util.NotificationHelper
import jp.juggler.subwaytooter.util.Utils
import jp.juggler.subwaytooter.view.MyNetworkImageView
import okhttp3.Request
import okhttp3.RequestBody

class ActAccountSetting : AppCompatActivity(), View.OnClickListener, CompoundButton.OnCheckedChangeListener {
	
	lateinit internal var account : SavedAccount
	lateinit internal var pref : SharedPreferences
	
	private lateinit var tvInstance : TextView
	private lateinit var tvUser : TextView
	private lateinit var btnAccessToken : View
	private lateinit var btnInputAccessToken : View
	private lateinit var btnAccountRemove : View
	private lateinit var btnVisibility : Button
	private lateinit var swNSFWOpen : Switch
	private lateinit var swDontShowTimeout : Switch
	private lateinit var btnOpenBrowser : Button
	private lateinit var cbNotificationMention : CheckBox
	private lateinit var cbNotificationBoost : CheckBox
	private lateinit var cbNotificationFavourite : CheckBox
	private lateinit var cbNotificationFollow : CheckBox
	
	private lateinit var cbConfirmFollow : CheckBox
	private lateinit var cbConfirmFollowLockedUser : CheckBox
	private lateinit var cbConfirmUnfollow : CheckBox
	private lateinit var cbConfirmBoost : CheckBox
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
	private lateinit var btnNote : View
	private lateinit var name_invalidator : NetworkEmojiInvalidator
	private lateinit var note_invalidator : NetworkEmojiInvalidator
	lateinit internal var handler : Handler
	
	internal var loading = false
	
	///////////////////////////////////////////////////
	
	internal var visibility = TootStatus.VISIBILITY_PUBLIC
	
	private var uriCameraImage : Uri? = null
	
	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		
		App1.setActivityTheme(this, false)
		this.pref = App1.pref
		
		initUI()
		
		val a = SavedAccount.loadAccount(this, intent.getLongExtra(KEY_ACCOUNT_DB_ID, - 1L))
		if(a == null){
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
					val uri = Utils.getExtraObject(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
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
					val uri1 = data.data
					if(uri1 != null) {
						// 単一選択
						val type = data.type
						addAttachment(requestCode, uri1, if(type?.isNotEmpty() == true) type else contentResolver.getType(uri1))
					} else {
						// 複数選択
						data.clipData?.let { clipData ->
							if(clipData.itemCount > 0) {
								clipData.getItemAt(0)?.uri?.let { uri2 ->
									val type = contentResolver.getType(uri2)
									addAttachment(requestCode, uri2, type)
								}
							}
						}
					}
				}
			}
			
			REQUEST_CODE_AVATAR_CAMERA, REQUEST_CODE_HEADER_CAMERA -> {
				
				if(resultCode != Activity.RESULT_OK) {
					// 失敗したら DBからデータを削除
					val uriCameraImage = this@ActAccountSetting.uriCameraImage
					if(uriCameraImage != null) {
						contentResolver.delete(uriCameraImage , null, null)
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
	
	var density: Float = 1f
	
	private fun initUI() {
		this.density = resources.displayMetrics.density
		this.handler = Handler()
		setContentView(R.layout.act_account_setting)
		
		Styler.fixHorizontalPadding(findViewById(R.id.svContent))
		
		tvInstance = findViewById(R.id.tvInstance)
		tvUser = findViewById(R.id.tvUser)
		btnAccessToken = findViewById(R.id.btnAccessToken)
		btnInputAccessToken = findViewById(R.id.btnInputAccessToken)
		btnAccountRemove = findViewById(R.id.btnAccountRemove)
		btnVisibility = findViewById(R.id.btnVisibility)
		swNSFWOpen = findViewById(R.id.swNSFWOpen)
		swDontShowTimeout = findViewById(R.id.swDontShowTimeout)
		btnOpenBrowser = findViewById(R.id.btnOpenBrowser)
		cbNotificationMention = findViewById(R.id.cbNotificationMention)
		cbNotificationBoost = findViewById(R.id.cbNotificationBoost)
		cbNotificationFavourite = findViewById(R.id.cbNotificationFavourite)
		cbNotificationFollow = findViewById(R.id.cbNotificationFollow)
		
		cbConfirmFollow = findViewById(R.id.cbConfirmFollow)
		cbConfirmFollowLockedUser = findViewById(R.id.cbConfirmFollowLockedUser)
		cbConfirmUnfollow = findViewById(R.id.cbConfirmUnfollow)
		cbConfirmBoost = findViewById(R.id.cbConfirmBoost)
		cbConfirmToot = findViewById(R.id.cbConfirmToot)
		
		tvUserCustom = findViewById(R.id.tvUserCustom)
		btnUserCustom = findViewById(R.id.btnUserCustom)
		
		ivProfileHeader = findViewById(R.id.ivProfileHeader)
		ivProfileAvatar = findViewById(R.id.ivProfileAvatar)
		btnProfileAvatar = findViewById(R.id.btnProfileAvatar)
		btnProfileHeader = findViewById(R.id.btnProfileHeader)
		etDisplayName = findViewById(R.id.etDisplayName)
		btnDisplayName = findViewById(R.id.btnDisplayName)
		etNote = findViewById(R.id.etNote)
		btnNote = findViewById(R.id.btnNote)
		
		btnOpenBrowser.setOnClickListener(this)
		btnAccessToken.setOnClickListener(this)
		btnInputAccessToken.setOnClickListener(this)
		btnAccountRemove.setOnClickListener(this)
		btnVisibility.setOnClickListener(this)
		btnUserCustom.setOnClickListener(this)
		btnProfileAvatar.setOnClickListener(this)
		btnProfileHeader.setOnClickListener(this)
		btnDisplayName.setOnClickListener(this)
		btnNote.setOnClickListener(this)
		
		swNSFWOpen.setOnCheckedChangeListener(this)
		swDontShowTimeout.setOnCheckedChangeListener(this)
		cbNotificationMention.setOnCheckedChangeListener(this)
		cbNotificationBoost.setOnCheckedChangeListener(this)
		cbNotificationFavourite.setOnCheckedChangeListener(this)
		cbNotificationFollow.setOnCheckedChangeListener(this)
		
		cbConfirmFollow.setOnCheckedChangeListener(this)
		cbConfirmFollowLockedUser.setOnCheckedChangeListener(this)
		cbConfirmUnfollow.setOnCheckedChangeListener(this)
		cbConfirmBoost.setOnCheckedChangeListener(this)
		cbConfirmToot.setOnCheckedChangeListener(this)
		
		btnNotificationSoundEdit = findViewById(R.id.btnNotificationSoundEdit)
		btnNotificationSoundReset = findViewById(R.id.btnNotificationSoundReset)
		btnNotificationStyleEdit = findViewById(R.id.btnNotificationStyleEdit)
		btnNotificationSoundEdit.setOnClickListener(this)
		btnNotificationSoundReset.setOnClickListener(this)
		btnNotificationStyleEdit.setOnClickListener(this)
		
		name_invalidator = NetworkEmojiInvalidator(handler, etDisplayName)
		note_invalidator = NetworkEmojiInvalidator(handler, etNote)
		
	}
	
	private fun loadUIFromData(a : SavedAccount) {
		
		this.full_acct = a.acct
		
		tvInstance.text = a.host
		tvUser.text = a.acct
		
		val sv = a.visibility
		if(sv != null) {
			visibility = sv
		}
		
		loading = true
		
		swNSFWOpen.isChecked = a.dont_hide_nsfw
		swDontShowTimeout.isChecked = a.dont_show_timeout
		cbNotificationMention.isChecked = a.notification_mention
		cbNotificationBoost.isChecked = a.notification_boost
		cbNotificationFavourite.isChecked = a.notification_favourite
		cbNotificationFollow.isChecked = a.notification_follow
		
		cbConfirmFollow.isChecked = a.confirm_follow
		cbConfirmFollowLockedUser.isChecked = a.confirm_follow_locked
		cbConfirmUnfollow.isChecked = a.confirm_unfollow
		cbConfirmBoost.isChecked = a.confirm_boost
		cbConfirmToot.isChecked = a.confirm_post
		
		notification_sound_uri = a.sound_uri
		
		loading = false
		
		val enabled = ! a.isPseudo
		btnAccessToken.isEnabled = enabled
		btnInputAccessToken.isEnabled = enabled
		btnVisibility.isEnabled = enabled
		
		btnNotificationSoundEdit.isEnabled = Build.VERSION.SDK_INT < 26 && enabled
		btnNotificationSoundReset.isEnabled = Build.VERSION.SDK_INT < 26 && enabled
		btnNotificationStyleEdit.isEnabled = Build.VERSION.SDK_INT >= 26 && enabled
		
		cbNotificationMention.isEnabled = enabled
		cbNotificationBoost.isEnabled = enabled
		cbNotificationFavourite.isEnabled = enabled
		cbNotificationFollow.isEnabled = enabled
		
		cbConfirmFollow.isEnabled = enabled
		cbConfirmFollowLockedUser.isEnabled = enabled
		cbConfirmUnfollow.isEnabled = enabled
		cbConfirmBoost.isEnabled = enabled
		cbConfirmToot.isEnabled = enabled
		
		updateVisibility()
		showAcctColor()
	}
	
	private fun showAcctColor() {
		val ac = AcctColor.load(full_acct)
		val nickname = ac.nickname
		tvUserCustom.text = if(nickname?.isNotEmpty() == true) nickname else full_acct
		tvUserCustom.setTextColor(if(ac.color_fg != 0) ac.color_fg else Styler.getAttributeColor(this, R.attr.colorTimeSmall))
		tvUserCustom.setBackgroundColor(ac.color_bg)
	}
	
	private fun saveUIToData() {
		if(! ::account.isInitialized) return
		
		if(loading) return
		
		account.visibility = visibility
		account.dont_hide_nsfw = swNSFWOpen.isChecked
		account.dont_show_timeout = swDontShowTimeout.isChecked
		account.notification_mention = cbNotificationMention.isChecked
		account.notification_boost = cbNotificationBoost.isChecked
		account.notification_favourite = cbNotificationFavourite.isChecked
		account.notification_follow = cbNotificationFollow.isChecked
		
		account.sound_uri = notification_sound_uri ?: ""
		
		account.confirm_follow = cbConfirmFollow.isChecked
		account.confirm_follow_locked = cbConfirmFollowLockedUser.isChecked
		account.confirm_unfollow = cbConfirmUnfollow.isChecked
		account.confirm_boost = cbConfirmBoost.isChecked
		account.confirm_post = cbConfirmToot.isChecked
		
		account.saveSetting()
		
	}
	
	override fun onCheckedChanged(buttonView : CompoundButton, isChecked : Boolean) {
		saveUIToData()
	}
	
	override fun onClick(v : View) {
		when(v.id) {
			R.id.btnAccessToken -> performAccessToken()
			R.id.btnInputAccessToken -> inputAccessToken()
			
			R.id.btnAccountRemove -> performAccountRemove()
			R.id.btnVisibility -> performVisibility()
			R.id.btnOpenBrowser -> open_browser("https://" + account.host + "/")
			
			R.id.btnUserCustom -> ActNickname.open(this, full_acct, false, REQUEST_CODE_ACCT_CUSTOMIZE)
			
			R.id.btnNotificationSoundEdit -> openNotificationSoundPicker()
			
			R.id.btnNotificationSoundReset -> {
				notification_sound_uri = ""
				saveUIToData()
			}
			
			R.id.btnProfileAvatar -> pickAvatarImage()
			
			R.id.btnProfileHeader -> pickHeaderImage()
			
			R.id.btnDisplayName -> sendDisplayName(false)
			
			R.id.btnNote -> sendNote(false)
			
			R.id.btnNotificationStyleEdit -> if(Build.VERSION.SDK_INT >= 26) {
				val channel = NotificationHelper.createNotificationChannel(this, account)
				val intent = Intent("android.settings.CHANNEL_NOTIFICATION_SETTINGS")
				intent.putExtra(Settings.EXTRA_CHANNEL_ID, channel.id)
				intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
				startActivity(intent)
			}
		}
	}
	
	private fun open_browser(url : String) {
		try {
			val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
			startActivity(intent)
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
	}
	
	private fun updateVisibility() {
		btnVisibility.text = Styler.getVisibilityString(this, visibility)
	}
	
	private fun performVisibility() {
		val caption_list = arrayOf(Styler.getVisibilityCaption(this, TootStatus.VISIBILITY_WEB_SETTING), Styler.getVisibilityCaption(this, TootStatus.VISIBILITY_PUBLIC), Styler.getVisibilityCaption(this, TootStatus.VISIBILITY_UNLISTED), Styler.getVisibilityCaption(this, TootStatus.VISIBILITY_PRIVATE), Styler.getVisibilityCaption(this, TootStatus.VISIBILITY_DIRECT))
		
		AlertDialog.Builder(this)
			.setTitle(R.string.choose_visibility)
			.setItems(caption_list) { _, which ->
				when(which) {
					0 -> visibility = TootStatus.VISIBILITY_WEB_SETTING
					1 -> visibility = TootStatus.VISIBILITY_PUBLIC
					2 -> visibility = TootStatus.VISIBILITY_UNLISTED
					3 -> visibility = TootStatus.VISIBILITY_PRIVATE
					4 -> visibility = TootStatus.VISIBILITY_DIRECT
				}
				updateVisibility()
				saveUIToData()
			}
			.setNegativeButton(R.string.cancel, null)
			.show()
		
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
				if(account.db_id == Pref.lpTabletTootDefaultAccount(pref) ) {
					pref.edit().put(Pref.lpTabletTootDefaultAccount, - 1L).apply()
				}
				
				finish()
				
				val task = @SuppressLint("StaticFieldLeak")
				object : AsyncTask<Void, Void, String?>() {
					
					internal fun unregister() {
						try {
							
							val install_id = PrefDevice.prefDevice(this@ActAccountSetting).getString(PrefDevice.KEY_INSTALL_ID, null)
							if(install_id?.isEmpty() != false) {
								log.d("performAccountRemove: missing install_id")
								return
							}
							
							val tag = account.notification_tag
							if(tag?.isEmpty() != false) {
								log.d("performAccountRemove: missing notification_tag")
								return
							}
							
							val post_data = ("instance_url=" + Uri.encode("https://" + account.host)
								+ "&app_id=" + Uri.encode(packageName)
								+ "&tag=" + tag)
							
							val request = Request.Builder()
								.url(PollingWorker.APP_SERVER + "/unregister")
								.post(RequestBody.create(TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED, post_data))
								.build()
							
							val call = App1.ok_http_client.newCall(request)
							
							val response = call.execute()
							
							log.e("performAccountRemove: %s", response)
							
						} catch(ex : Throwable) {
							log.trace(ex)
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
				return client.authentication1(Pref.spClientName(this@ActAccountSetting))
			}
			
			override fun handleResult(result : TootApiResult?) {
				result ?: return // cancelled.
				
				val url = result.string
				val error = result.error
				when {
				// URLをブラウザで開く
					url != null -> {
						val data = Intent()
						data.data = Uri.parse(url)
						setResult(Activity.RESULT_OK, data)
						finish()
					}
				// エラーを表示
					error != null -> {
						Utils.showToast(this@ActAccountSetting, true, error)
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
		try {
			val notification_sound_uri = this.notification_sound_uri
			if(notification_sound_uri?.isNotEmpty() == true){
				intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,  Uri.parse(notification_sound_uri))
			}
		} catch(ignored : Throwable) {
		}
		
		val chooser = Intent.createChooser(intent, getString(R.string.notification_sound))
		startActivityForResult(chooser, REQUEST_CODE_NOTIFICATION_SOUND)
	}
	
	//////////////////////////////////////////////////////////////////////////
	
	private fun initializeProfile() {
		// 初期状態
		ivProfileAvatar.setErrorImageResId(Styler.getAttributeResourceId(this, R.attr.ic_question))
		ivProfileAvatar.setDefaultImageResId(Styler.getAttributeResourceId(this, R.attr.ic_question))
		etDisplayName.setText("(loading...)")
		etNote.setText("(loading...)")
		// 初期状態では編集不可能
		btnProfileAvatar.isEnabled = false
		btnProfileHeader.isEnabled = false
		etDisplayName.isEnabled = false
		btnDisplayName.isEnabled = false
		etNote.isEnabled = false
		btnNote.isEnabled = false
		// 疑似アカウントなら編集不可のまま
		if(account.isPseudo) return
		
		loadProfile()
	}
	
	private fun loadProfile() {
		// サーバから情報をロードする
		
		TootTaskRunner(this).run(account, object : TootTask {
			
			internal var data : TootAccount? = null
			override fun background(client : TootApiClient) : TootApiResult? {
				val result = client.request("/api/v1/accounts/verify_credentials")
				val jsonObject = result?.jsonObject
				if(jsonObject != null) {
					data = TootParser(this@ActAccountSetting, account).account(jsonObject)
					if(data == null) return TootApiResult("TootAccount parse failed.")
				}
				return result
			}
			
			override fun handleResult(result : TootApiResult?) {
				if(result == null) return  // cancelled.
				
				val data = this.data
				if(data != null) {
					showProfile(data)
				} else {
					Utils.showToast(this@ActAccountSetting, true, result.error)
				}
				
			}
		})
	}
	
	internal fun showProfile(src : TootAccount) {
		
		ivProfileAvatar.setImageUrl(
			App1.pref,
			Styler.calcIconRound(ivProfileAvatar.layoutParams),
			src.avatar_static,
			src.avatar
		)
		ivProfileHeader.setImageUrl(App1.pref, 0f, src.header_static, src.header)
		
		val display_name = src.display_name
		val name = DecodeOptions(
			emojiMapProfile = src.profile_emojis
		).decodeEmoji(this, display_name)
		etDisplayName.setText(name)
		name_invalidator.register(name)
		
		val noteString = src.source?.note ?: src.note
		val noteSpannable = DecodeOptions(
			emojiMapProfile = src.profile_emojis
		).decodeEmoji(this, noteString)
		
		etNote.setText(noteSpannable)
		note_invalidator.register(noteSpannable)
		
		// 編集可能にする
		btnProfileAvatar.isEnabled = true
		btnProfileHeader.isEnabled = true
		etDisplayName.isEnabled = true
		btnDisplayName.isEnabled = true
		etNote.isEnabled = true
		btnNote.isEnabled = true
	}
	
	internal fun updateCredential(form_data : String) {
		
		TootTaskRunner(this).run(account, object : TootTask {
			
			internal var data : TootAccount? = null
			override fun background(client : TootApiClient) : TootApiResult? {
				val request_builder = Request.Builder()
					.patch(RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED, form_data
					))
				
				val result = client.request("/api/v1/accounts/update_credentials", request_builder)
				val jsonObject = result?.jsonObject
				if(jsonObject != null) {
					data = TootParser(this@ActAccountSetting, account).account(jsonObject)
					if(data == null) return TootApiResult("TootAccount parse failed.")
				}
				return result
			}
			
			override fun handleResult(result : TootApiResult?) {
				if(result == null) return  // cancelled.
				
				val data = this.data
				if(data != null) {
					showProfile(data)
				} else {
					Utils.showToast(this@ActAccountSetting, true, result.error)
				}
				
			}
		})
		
	}
	
	private fun sendDisplayName(bConfirmed : Boolean) {
		val sv = etDisplayName.text.toString()
		if(! bConfirmed) {
			val length = sv.codePointCount(0, sv.length)
			if(length > max_length_display_name) {
				AlertDialog.Builder(this)
					.setMessage(getString(R.string.length_warning, getString(R.string.display_name), length, max_length_display_name
					))
					.setNegativeButton(R.string.cancel, null)
					.setPositiveButton(R.string.ok) { _, _ -> sendDisplayName(true) }
					.setCancelable(true)
					.show()
				return
			}
		}
		updateCredential("display_name=" + Uri.encode(EmojiDecoder.decodeShortCode(sv)))
	}
	
	private fun sendNote(bConfirmed : Boolean) {
		val sv = etNote.text.toString()
		if(! bConfirmed) {
			val length = sv.codePointCount(0, sv.length)
			if(length > max_length_note) {
				AlertDialog.Builder(this)
					.setMessage(getString(R.string.length_warning, getString(R.string.note), length, max_length_note
					))
					.setNegativeButton(R.string.cancel, null)
					.setPositiveButton(R.string.ok) { _, _ -> sendNote(true) }
					.setCancelable(true)
					.show()
				return
			}
		}
		updateCredential("note=" + Uri.encode(EmojiDecoder.decodeShortCode(sv)))
	}
	
	private fun pickAvatarImage() {
		openPicker(PERMISSION_REQUEST_AVATAR)
	}
	
	private fun pickHeaderImage() {
		openPicker(PERMISSION_REQUEST_HEADER)
	}
	
	private fun openPicker(permission_request_code : Int) {
		val permissionCheck = ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
		if(permissionCheck != PackageManager.PERMISSION_GRANTED) {
			preparePermission(permission_request_code)
			return
		}
		
		val a = ActionsDialog()
		a.addAction(getString(R.string.image_pick)) {
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
			
			ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), request_code
			)
			return
		}
		Utils.showToast(this, true, R.string.missing_permission_to_access_media)
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
					Utils.showToast(this, true, R.string.missing_permission_to_access_media)
				}
		}
	}
	
	private fun performAttachment(request_code : Int) {
		// SAFのIntentで開く
		try {
			val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
			intent.addCategory(Intent.CATEGORY_OPENABLE)
			intent.type = "*/*"
			intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
			intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
			startActivityForResult(intent, request_code)
		} catch(ex : Throwable) {
			log.trace(ex)
			Utils.showToast(this, ex, "ACTION_OPEN_DOCUMENT failed.")
		}
		
	}
	
	private fun performCamera(request_code : Int) {
		
		try {
			// カメラで撮影
			val filename = System.currentTimeMillis().toString() + ".jpg"
			val values = ContentValues()
			values.put(MediaStore.Images.Media.TITLE, filename)
			values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
			uriCameraImage = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
			
			val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
			intent.putExtra(MediaStore.EXTRA_OUTPUT, uriCameraImage)
			
			startActivityForResult(intent, request_code)
		} catch(ex : Throwable) {
			log.trace(ex)
			Utils.showToast(this, ex, "opening camera app failed.")
		}
		
	}
	
	internal interface InputStreamOpener {
		
		val mimeType : String
		@Throws(IOException::class)
		fun open() : InputStream
		
		fun deleteTempFile()
	}
	
	private fun createOpener(uri : Uri, mime_type : String) : InputStreamOpener {
		
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
				
				val bitmap = Utils.createResizedBitmap(log, this, uri, false, resize_to)
				if(bitmap != null) {
					try {
						val cache_dir = externalCacheDir
						if(cache_dir == null) {
							Utils.showToast(this, false, "getExternalCacheDir returns null.")
							break
						}
						
						
						cache_dir.mkdir()
						
						val temp_file = File(cache_dir, "tmp." + Thread.currentThread().id)
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
							
							@Throws(IOException::class)
							override fun open() : InputStream {
								return FileInputStream(temp_file)
							}
							
							override fun deleteTempFile() {
								
								temp_file.delete()
							}
						}
					} finally {
						bitmap.recycle()
					}
				}
				
			} catch(ex : Throwable) {
				log.trace(ex)
				Utils.showToast(this, ex, "Resizing image failed.")
			}
			
			break
		}
		return object : InputStreamOpener {
			
			override val mimeType : String
				get() = mime_type
			
			@Throws(IOException::class)
			override fun open() : InputStream {
				return contentResolver.openInputStream(uri)
			}
			
			override fun deleteTempFile() {
			
			}
		}
	}
	
	private fun addAttachment(request_code : Int, uri : Uri, mime_type : String?) {
		
		if(mime_type == null) {
			Utils.showToast(this, false, "mime type is not provided.")
			return
		}
		
		if(! mime_type.startsWith("image/")) {
			Utils.showToast(this, false, "mime type is not image.")
			return
		}
		
		val task = @SuppressLint("StaticFieldLeak")
		object : AsyncTask<Void, Void, String?>() {
			
			override fun doInBackground(vararg params : Void) : String? {
				try {
					val opener = createOpener(uri, mime_type)
					try {
						opener.open().use { inData ->
							val bao = ByteArrayOutputStream()
							//
							bao.write(Utils.encodeUTF8("data:" + opener.mimeType + ";base64,"))
							//
							Base64OutputStream(bao, Base64.NO_WRAP).use { base64 -> IOUtils.copy(inData, base64) }
							val value = Utils.decodeUTF8(bao.toByteArray())
							
							return when(request_code) {
								REQUEST_CODE_HEADER_ATTACHMENT, REQUEST_CODE_HEADER_CAMERA -> "header="
								else -> "avatar="
							} + Uri.encode(value)
						}
					} finally {
						opener.deleteTempFile()
					}
					
				} catch(ex : Throwable) {
					Utils.showToast(this@ActAccountSetting, ex, "image converting failed.")
				}
				
				return null
			}
			
			override fun onPostExecute(form_data : String?) {
				if(form_data != null) {
					updateCredential(form_data)
				}
			}
			
		}
		task.executeOnExecutor(App1.task_executor)
	}
	
	companion object {
		
		internal val log = LogCategory("ActAccountSetting")
		
		internal val KEY_ACCOUNT_DB_ID = "account_db_id"
		
		fun open(activity : Activity, ai : SavedAccount, requestCode : Int) {
			val intent = Intent(activity, ActAccountSetting::class.java)
			intent.putExtra(KEY_ACCOUNT_DB_ID, ai.db_id)
			activity.startActivityForResult(intent, requestCode)
		}
		
		internal val REQUEST_CODE_ACCT_CUSTOMIZE = 1
		internal val REQUEST_CODE_NOTIFICATION_SOUND = 2
		private val REQUEST_CODE_AVATAR_ATTACHMENT = 3
		private val REQUEST_CODE_HEADER_ATTACHMENT = 4
		private val REQUEST_CODE_AVATAR_CAMERA = 5
		private val REQUEST_CODE_HEADER_CAMERA = 6
		
		internal val RESULT_INPUT_ACCESS_TOKEN = Activity.RESULT_FIRST_USER + 10
		internal val EXTRA_DB_ID = "db_id"
		
		internal val max_length_display_name = 30
		internal val max_length_note = 160
		
		private val PERMISSION_REQUEST_AVATAR = 1
		private val PERMISSION_REQUEST_HEADER = 2
		
		internal val MIME_TYPE_JPEG = "image/jpeg"
		internal val MIME_TYPE_PNG = "image/png"
	}
	
}

