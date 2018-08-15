package jp.juggler.subwaytooter

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.support.v13.view.inputmethod.InputConnectionCompat
import android.support.v13.view.inputmethod.InputContentInfoCompat
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*

import org.apache.commons.io.IOUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.HashSet
import java.util.Locale

import jp.juggler.subwaytooter.dialog.*
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.PostDraft
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.span.MyClickableSpanClickCallback
import jp.juggler.subwaytooter.util.*
import jp.juggler.subwaytooter.view.FocusPointView
import jp.juggler.subwaytooter.view.MyEditText
import jp.juggler.subwaytooter.view.MyNetworkImageView
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink

class ActPost : AppCompatActivity(), View.OnClickListener, PostAttachment.Callback {
	
	companion object {
		internal val log = LogCategory("ActPost")
		
		internal const val EXTRA_POSTED_ACCT = "posted_acct"
		internal const val EXTRA_POSTED_STATUS_ID = "posted_status_id"
		internal const val EXTRA_POSTED_REPLY_ID = "posted_reply_id"
		internal const val EXTRA_POSTED_REDRAFT_ID = "posted_redraft_id"
		
		internal const val KEY_ACCOUNT_DB_ID = "account_db_id"
		internal const val KEY_REPLY_STATUS = "reply_status"
		internal const val KEY_REDRAFT_STATUS = "redraft_status"
		internal const val KEY_INITIAL_TEXT = "initial_text"
		internal const val KEY_SENT_INTENT = "sent_intent"
		
		internal const val KEY_ATTACHMENT_LIST = "attachment_list"
		internal const val KEY_VISIBILITY = "visibility"
		internal const val KEY_IN_REPLY_TO_ID = "in_reply_to_id"
		internal const val KEY_IN_REPLY_TO_TEXT = "in_reply_to_text"
		internal const val KEY_IN_REPLY_TO_IMAGE = "in_reply_to_image"
		internal const val KEY_IN_REPLY_TO_URL = "in_reply_to_url"
		
		private const val REQUEST_CODE_ATTACHMENT = 1
		private const val REQUEST_CODE_CAMERA = 2
		private const val REQUEST_CODE_MUSHROOM = 3
		private const val REQUEST_CODE_VIDEO = 4
		
		private const val PERMISSION_REQUEST_CODE = 1
		
		internal const val MIME_TYPE_JPEG = "image/jpeg"
		internal const val MIME_TYPE_PNG = "image/png"
		
		internal val list_resize_max = intArrayOf(0, 640, 800, 1024, 1280, 1600, 2048)
		
		internal val acceptable_mime_types = HashSet<String>().apply {
			//
			add("image/*") // Android標準のギャラリーが image/* を出してくることがあるらしい
			add("video/*") // Android標準のギャラリーが image/* を出してくることがあるらしい
			//
			add("image/jpeg")
			add("image/png")
			add("image/gif")
			add("video/webm")
			add("video/mp4")
		}
		
		//	private void performCameraVideo(){
		//
		//		try{
		//			Intent takeVideoIntent = new Intent( MediaStore.ACTION_VIDEO_CAPTURE );
		//			startActivityForResult( takeVideoIntent, REQUEST_CODE_VIDEO );
		//		}catch( Throwable ex ){
		//			warning.trace( ex );
		//			Utils.showToast( this, ex, "opening video app failed." );
		//		}
		//	}
		
		/////////////////////////////////////////////////
		
		const val DRAFT_CONTENT = "content"
		const val DRAFT_CONTENT_WARNING = "content_warning"
		internal const val DRAFT_CONTENT_WARNING_CHECK = "content_warning_check"
		internal const val DRAFT_NSFW_CHECK = "nsfw_check"
		internal const val DRAFT_VISIBILITY = "visibility"
		internal const val DRAFT_ACCOUNT_DB_ID = "account_db_id"
		internal const val DRAFT_ATTACHMENT_LIST = "attachment_list"
		internal const val DRAFT_REPLY_ID = "reply_id"
		internal const val DRAFT_REPLY_TEXT = "reply_text"
		internal const val DRAFT_REPLY_IMAGE = "reply_image"
		internal const val DRAFT_REPLY_URL = "reply_url"
		internal const val DRAFT_IS_ENQUETE = "is_enquete"
		internal const val DRAFT_ENQUETE_ITEMS = "enquete_items"
		
		private const val STATE_MUSHROOM_INPUT = "mushroom_input"
		private const val STATE_MUSHROOM_START = "mushroom_start"
		private const val STATE_MUSHROOM_END = "mushroom_end"
		private const val STATE_REDRAFT_STATUS_ID = "redraft_status_id"
		private const val STATE_URI_CAMERA_IMAGE = "uri_camera_image"
		
		fun open(
			activity : Activity,
			request_code : Int,
			account_db_id : Long,
			reply_status : TootStatus?
		) {
			val intent = Intent(activity, ActPost::class.java)
			intent.putExtra(KEY_ACCOUNT_DB_ID, account_db_id)
			if(reply_status != null) {
				intent.putExtra(KEY_REPLY_STATUS, reply_status.json.toString())
			}
			activity.startActivityForResult(intent, request_code)
		}
		
		fun openRedraft(
			activity : Activity,
			request_code : Int,
			account_db_id : Long,
			base_status : TootStatus,
			reply_status : TootStatus? = null
		) {
			val intent = Intent(activity, ActPost::class.java)
			intent.putExtra(KEY_ACCOUNT_DB_ID, account_db_id)
			intent.putExtra(KEY_REDRAFT_STATUS, base_status.json.toString())
			if(reply_status != null) {
				intent.putExtra(KEY_REPLY_STATUS, reply_status.json.toString())
			}
			activity.startActivityForResult(intent, request_code)
		}
		
		fun open(
			activity : Activity,
			request_code : Int,
			account_db_id : Long,
			initial_text : String?
		) {
			val intent = Intent(activity, ActPost::class.java)
			intent.putExtra(KEY_ACCOUNT_DB_ID, account_db_id)
			if(initial_text != null) {
				intent.putExtra(KEY_INITIAL_TEXT, initial_text)
			}
			activity.startActivityForResult(intent, request_code)
		}
		
		fun open(
			activity : Activity,
			request_code : Int,
			account_db_id : Long,
			sent_intent : Intent?
		) {
			val intent = Intent(activity, ActPost::class.java)
			intent.putExtra(KEY_ACCOUNT_DB_ID, account_db_id)
			if(sent_intent != null) {
				intent.putExtra(KEY_SENT_INTENT, sent_intent)
			}
			activity.startActivityForResult(intent, request_code)
		}
		
		internal fun check_exist(url : String?) : Boolean {
			if(url?.isEmpty() != false) return false
			try {
				val request = Request.Builder().url(url).build()
				val call = App1.ok_http_client.newCall(request)
				val response = call.execute()
				if(response.isSuccessful) {
					return true
				}
				log.e(TootApiClient.formatResponse(response, "check_exist failed."))
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			
			return false
		}
		
	}
	
	private lateinit var btnAccount : Button
	private lateinit var btnVisibility : ImageButton
	private lateinit var btnAttachment : View
	private lateinit var btnPost : View
	private lateinit var llAttachment : View
	private lateinit var ivMedia : List<MyNetworkImageView>
	internal lateinit var cbNSFW : CheckBox
	internal lateinit var cbContentWarning : CheckBox
	internal lateinit var etContentWarning : MyEditText
	internal lateinit var etContent : MyEditText
	
	internal lateinit var cbEnquete : CheckBox
	private lateinit var llEnquete : View
	internal lateinit var list_etChoice : List<MyEditText>
	
	private lateinit var tvCharCount : TextView
	internal lateinit var handler : Handler
	private lateinit var formRoot : View
	
	private lateinit var llReply : View
	private lateinit var tvReplyTo : TextView
	private lateinit var btnRemoveReply : View
	private lateinit var ivReply : MyNetworkImageView
	private lateinit var scrollView : ScrollView
	
	internal lateinit var pref : SharedPreferences
	internal lateinit var app_state : AppState
	private lateinit var post_helper : PostHelper
	internal var attachment_list = ArrayList<PostAttachment>()
	private var isPostComplete : Boolean = false
	
	internal var density : Float = 0f
	
	private lateinit var account_list : ArrayList<SavedAccount>
	
	private var redraft_status_id : Long = 0L
	
	private val text_watcher : TextWatcher = object : TextWatcher {
		override fun beforeTextChanged(charSequence : CharSequence, i : Int, i1 : Int, i2 : Int) {
		
		}
		
		override fun onTextChanged(charSequence : CharSequence, i : Int, i1 : Int, i2 : Int) {
		
		}
		
		override fun afterTextChanged(editable : Editable) {
			updateTextCount()
		}
	}
	
	private val scroll_listener : ViewTreeObserver.OnScrollChangedListener =
		ViewTreeObserver.OnScrollChangedListener { post_helper.onScrollChanged() }
	
	//////////////////////////////////////////////////////////
	// Account
	
	internal var account : SavedAccount? = null
	
	private var uriCameraImage : Uri? = null
	
	//////////////////////////////////////////////////////////////////////
	// visibility
	
	internal var visibility : String? = null
	
	/////////////////////////////////////////////////
	
	internal var in_reply_to_id = - 1L
	internal var in_reply_to_text : String? = null
	internal var in_reply_to_image : String? = null
	internal var in_reply_to_url : String? = null
	private var mushroom_input : Int = 0
	private var mushroom_start : Int = 0
	private var mushroom_end : Int = 0
	
	private val link_click_listener : MyClickableSpanClickCallback = { _, span ->
		try {
			// ブラウザで開く
			val intent = Intent(Intent.ACTION_VIEW, Uri.parse(span.url))
			startActivity(intent)
		} catch(ex : Throwable) {
			log.trace(ex)
		}
	}
	
	////////////////////////////////////////////////////////////////
	
	override fun onClick(v : View) {
		when(v.id) {
			R.id.btnAccount -> performAccountChooser()
			R.id.btnVisibility -> performVisibility()
			R.id.btnAttachment -> openAttachment()
			R.id.ivMedia1 -> performAttachmentClick(0)
			R.id.ivMedia2 -> performAttachmentClick(1)
			R.id.ivMedia3 -> performAttachmentClick(2)
			R.id.ivMedia4 -> performAttachmentClick(3)
			R.id.btnPost -> performPost()
			R.id.btnRemoveReply -> removeReply()
			R.id.btnMore -> performMore()
			R.id.btnPlugin -> openMushroom()
			R.id.btnEmojiPicker -> post_helper.openEmojiPickerFromMore()
		}
	}
	
	override fun onActivityResult(requestCode : Int, resultCode : Int, data : Intent?) {
		if(requestCode == REQUEST_CODE_ATTACHMENT && resultCode == Activity.RESULT_OK) {
			if(data != null) {
				// 単一選択
				data.data?.let { addAttachment(it, data.type) }
				// 複数選択
				val cd = data.clipData
				if(cd != null) {
					for(i in 0 until cd.itemCount) {
						cd.getItemAt(i)?.uri?.let { addAttachment(it) }
					}
				}
			}
		} else if(requestCode == REQUEST_CODE_CAMERA) {
			
			if(resultCode != Activity.RESULT_OK) {
				// 失敗したら DBからデータを削除
				val uriCameraImage = this.uriCameraImage
				if(uriCameraImage != null) {
					contentResolver.delete(uriCameraImage, null, null)
					this@ActPost.uriCameraImage = null
				}
			} else {
				// 画像のURL
				val uri = data?.data ?: uriCameraImage
				if(uri != null) {
					addAttachment(uri)
				} else {
					showToast(this@ActPost, false, "missing image uri")
				}
			}
		} else if(requestCode == REQUEST_CODE_VIDEO && resultCode == Activity.RESULT_OK) {
			data?.data?.let { addAttachment(it) }
			
		} else if(requestCode == REQUEST_CODE_MUSHROOM && resultCode == Activity.RESULT_OK) {
			data?.getStringExtra("replace_key")?.let { applyMushroomResult(it) }
		}
		super.onActivityResult(requestCode, resultCode, data)
	}
	
	override fun onBackPressed() {
		saveDraft()
		super.onBackPressed()
	}
	
	override fun onResume() {
		super.onResume()
		MyClickableSpan.link_callback = WeakReference(link_click_listener)
	}
	
	override fun onPause() {
		super.onPause()
		
		// 編集中にホーム画面を押したり他アプリに移動する場合は下書きを保存する
		// やや過剰な気がするが、自アプリに戻ってくるときにランチャーからアイコンタップされると
		// メイン画面より上にあるアクティビティはすべて消されてしまうので
		// このタイミングで保存するしかない
		if(! isPostComplete) {
			saveDraft()
		}
	}
	
	override fun onCreate(savedInstanceState : Bundle?) {
		
		var sv : String?
		
		super.onCreate(savedInstanceState)
		App1.setActivityTheme(this, true)
		
		app_state = App1.getAppState(this)
		pref = app_state.pref
		
		initUI()
		
		if(account_list.isEmpty()) {
			showToast(this, true, R.string.please_add_account)
			finish()
			return
		}
		
		if(savedInstanceState != null) {
			
			mushroom_input = savedInstanceState.getInt(STATE_MUSHROOM_INPUT, 0)
			mushroom_start = savedInstanceState.getInt(STATE_MUSHROOM_START, 0)
			mushroom_end = savedInstanceState.getInt(STATE_MUSHROOM_END, 0)
			redraft_status_id = savedInstanceState.getLong(STATE_REDRAFT_STATUS_ID)
			
			sv = savedInstanceState.getString(STATE_URI_CAMERA_IMAGE)
			if(sv?.isNotEmpty() == true) {
				uriCameraImage = Uri.parse(sv)
			}
			
			val account_db_id =
				savedInstanceState.getLong(KEY_ACCOUNT_DB_ID, SavedAccount.INVALID_DB_ID)
			if(account_db_id != SavedAccount.INVALID_DB_ID) {
				var i = 0
				val ie = account_list.size
				while(i < ie) {
					val a = account_list[i]
					if(a.db_id == account_db_id) {
						selectAccount(a)
						break
					}
					++ i
				}
			}
			
			this.visibility = savedInstanceState.getString(KEY_VISIBILITY)
			
			if(app_state.attachment_list != null) {
				
				val list_in_state = app_state.attachment_list
				if(list_in_state != null) {
					// static なデータが残ってるならそれを使う
					this.attachment_list = list_in_state
				}
				
				// コールバックを新しい画面に差し替える
				for(pa in attachment_list) {
					pa.callback = this
				}
				
			} else {
				sv = savedInstanceState.getString(KEY_ATTACHMENT_LIST)
				if(sv?.isNotEmpty() == true) {
					
					// state から復元する
					app_state.attachment_list = this.attachment_list
					this.attachment_list.clear()
					
					try {
						val array = sv.toJsonArray()
						for(i in 0 until array.length()) {
							try {
								val a = parseItem(::TootAttachment, array.optJSONObject(i))
								if(a != null) attachment_list.add(PostAttachment(a))
							} catch(ex : Throwable) {
								log.trace(ex)
							}
						}
					} catch(ex : Throwable) {
						log.trace(ex)
					}
					
				}
			}
			
			this.in_reply_to_id = savedInstanceState.getLong(KEY_IN_REPLY_TO_ID, - 1L)
			this.in_reply_to_text = savedInstanceState.getString(KEY_IN_REPLY_TO_TEXT)
			this.in_reply_to_image = savedInstanceState.getString(KEY_IN_REPLY_TO_IMAGE)
			this.in_reply_to_url = savedInstanceState.getString(KEY_IN_REPLY_TO_URL)
		} else {
			app_state.attachment_list = this.attachment_list
			this.attachment_list.clear()
			
			val intent = intent
			val account_db_id = intent.getLongExtra(KEY_ACCOUNT_DB_ID, SavedAccount.INVALID_DB_ID)
			if(account_db_id != SavedAccount.INVALID_DB_ID) {
				var i = 0
				val ie = account_list.size
				while(i < ie) {
					val a = account_list[i]
					if(a.db_id == account_db_id) {
						selectAccount(a)
						break
					}
					++ i
				}
			}
			
			val sent_intent = intent.getParcelableExtra<Intent>(KEY_SENT_INTENT)
			if(sent_intent != null) {
				
				appendContentText(sent_intent)
				val action = sent_intent.action
				when(action) {
					Intent.ACTION_VIEW -> {
						val uri = sent_intent.data
						val type = sent_intent.type
						if(uri != null) addAttachment(uri, type)
					}
					
					Intent.ACTION_SEND -> {
						val uri = sent_intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
						val type = sent_intent.type
						if(uri != null) addAttachment(uri, type)
					}
					
					Intent.ACTION_SEND_MULTIPLE -> {
						val list_uri =
							sent_intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
						if(list_uri != null) {
							for(uri in list_uri) {
								if(uri != null) addAttachment(uri)
							}
						}
					}
				}
			}
			
			appendContentText(intent.getStringExtra(KEY_INITIAL_TEXT))
			
			val account = this.account
			
			sv = intent.getStringExtra(KEY_REPLY_STATUS)
			if(sv != null && account != null) {
				try {
					val reply_status = TootParser(this@ActPost, account).status(sv.toJsonObject())
					
					if(reply_status != null) {
						// CW をリプライ元に合わせる
						if(reply_status.spoiler_text?.isNotEmpty() == true) {
							cbContentWarning.isChecked = true
							etContentWarning.setText(reply_status.spoiler_text)
						}
						
						val mention_list = ArrayList<String>()
						
						// 元レスにあった mention
						val old_mentions = reply_status.mentions
						if(old_mentions != null) {
							for(mention in old_mentions) {
								val who_acct = mention.acct
								if(who_acct.isNotEmpty()) {
									if(account.isMe(who_acct)) continue
									sv = "@" + account.getFullAcct(who_acct)
									if(! mention_list.contains(sv)) {
										mention_list.add(sv)
									}
								}
							}
						}
						
						val who_acct = account.getFullAcct(reply_status.account)
						if(mention_list.contains("@$who_acct")) {
							// 既に含まれている
						} else if(! account.isMe(reply_status.account)) {
							// 自分ではない
							mention_list.add("@$who_acct")
						}
						
						val sb = StringBuilder()
						for(acct in mention_list) {
							if(sb.isNotEmpty()) sb.append(' ')
							sb.append(acct)
						}
						if(sb.isNotEmpty()) {
							appendContentText(sb.append(' ').toString())
						}
						
						// リプライ表示をつける
						in_reply_to_id = reply_status.id
						in_reply_to_text = reply_status.content
						in_reply_to_image = reply_status.account.avatar_static
						in_reply_to_url = reply_status.url
						
						// 公開範囲
						try {
							// 比較する前にデフォルトの公開範囲を計算する
							visibility = when {
								visibility?.isNotEmpty() == true -> visibility
								account.visibility?.isNotEmpty() == true -> account.visibility
								else -> TootStatus.VISIBILITY_PUBLIC
								// VISIBILITY_WEB_SETTING だと 1.5未満のタンスでトラブルになる
							}
							
							if(TootStatus.VISIBILITY_WEB_SETTING == visibility) {
								// 「Web設定に合わせる」だった場合は無条件にリプライ元の公開範囲に変更する
								this.visibility = reply_status.visibility
							} else {
								// デフォルトの方が公開範囲が大きい場合、リプライ元に合わせて公開範囲を狭める
								if(TootStatus.isVisibilitySpoilRequired(
										this.visibility,
										reply_status.visibility
									)) {
									this.visibility = reply_status.visibility
								}
							}
							
						} catch(ex : Throwable) {
							log.trace(ex)
						}
						
					}
					
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
			}
			
			appendContentText(account?.default_text, selectBefore = true)
			
			// 再編集
			sv = intent.getStringExtra(KEY_REDRAFT_STATUS)
			if(sv != null && account != null) {
				try {
					val base_status = TootParser(this@ActPost, account).status(sv.toJsonObject())
					if(base_status != null) {
						
						redraft_status_id = base_status.id
						
						this.visibility = base_status.visibility
						
						val src_attachments = base_status.media_attachments
						if(src_attachments?.isNotEmpty() == true) {
							app_state.attachment_list = this.attachment_list
							this.attachment_list.clear()
							try {
								for(src in src_attachments) {
									if(src is TootAttachment) {
										src.redraft = true
										val pa = PostAttachment(src)
										pa.status = PostAttachment.STATUS_UPLOADED
										this.attachment_list.add(pa)
									}
								}
							} catch(ex : Throwable) {
								log.trace(ex)
							}
						}
						
						// 再編集の場合はdefault_textは反映されない
						
						val decodeOptions = DecodeOptions(this)
						etContent.text = decodeOptions.decodeHTML(base_status.content)
						etContent.setSelection(etContent.text.length)
						etContentWarning.setText(decodeOptions.decodeEmoji(base_status.spoiler_text))
						etContentWarning.setSelection(etContentWarning.text.length)
						cbContentWarning.isChecked = etContentWarning.text.isNotEmpty()
						cbNSFW.isChecked = base_status.sensitive == true
						
						val src_enquete = base_status.enquete
						val src_items = src_enquete?.items
						if(src_items != null && src_enquete.type == NicoEnquete.TYPE_ENQUETE) {
							cbEnquete.isChecked = true
							etContent.text = decodeOptions.decodeHTML(src_enquete.question)
							etContent.setSelection(etContent.text.length)
							
							var src_index = 0
							for(et in list_etChoice) {
								if(src_index < src_items.size) {
									val choice = src_items[src_index]
									if(src_index == src_items.size - 1 && choice.text == "\uD83E\uDD14") {
										// :thinking_face: は再現しない
									} else {
										et.setText(decodeOptions.decodeEmoji(choice.text))
										++ src_index
										continue
									}
								}
								et.setText("")
							}
						}
					}
					
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
			}
		}
		
		visibility = when {
			visibility?.isNotEmpty() == true -> visibility
			account?.visibility?.isNotEmpty() == true -> account?.visibility
			else -> TootStatus.VISIBILITY_PUBLIC
			
			// 2017/9/13 VISIBILITY_WEB_SETTING から VISIBILITY_PUBLICに変更した
			// VISIBILITY_WEB_SETTING だと 1.5未満のタンスでトラブルになるので…
		}
		
		if(this.account == null) {
			// 表示を未選択に更新
			selectAccount(null)
		}
		
		updateContentWarning()
		showMediaAttachment()
		showVisibility()
		updateTextCount()
		showReplyTo()
		showEnquete()
	}
	
	override fun onDestroy() {
		post_helper.onDestroy()
		
		super.onDestroy()
		
	}
	
	override fun onSaveInstanceState(outState : Bundle?) {
		super.onSaveInstanceState(outState)
		
		outState ?: return
		
		outState.putInt(STATE_MUSHROOM_INPUT, mushroom_input)
		outState.putInt(STATE_MUSHROOM_START, mushroom_start)
		outState.putInt(STATE_MUSHROOM_END, mushroom_end)
		outState.putLong(STATE_REDRAFT_STATUS_ID, redraft_status_id)
		if(uriCameraImage != null) {
			outState.putString(STATE_URI_CAMERA_IMAGE, uriCameraImage.toString())
		}
		
		val account = this.account
		if(account != null) {
			outState.putLong(KEY_ACCOUNT_DB_ID, account.db_id)
		}
		
		if(visibility != null) {
			outState.putString(KEY_VISIBILITY, visibility)
		}
		
		if(! attachment_list.isEmpty()) {
			val array = JSONArray()
			for(pa in attachment_list) {
				if(pa.status == PostAttachment.STATUS_UPLOADED) {
					// アップロード完了したものだけ保持する
					array.put(pa.attachment?.json)
				}
			}
			outState.putString(KEY_ATTACHMENT_LIST, array.toString())
		}
		
		outState.putLong(KEY_IN_REPLY_TO_ID, in_reply_to_id)
		outState.putString(KEY_IN_REPLY_TO_TEXT, in_reply_to_text)
		outState.putString(KEY_IN_REPLY_TO_IMAGE, in_reply_to_image)
		outState.putString(KEY_IN_REPLY_TO_URL, in_reply_to_url)
	}
	
	override fun onRestoreInstanceState(savedInstanceState : Bundle) {
		super.onRestoreInstanceState(savedInstanceState)
		updateContentWarning()
		showMediaAttachment()
		showVisibility()
		updateTextCount()
		showReplyTo()
		showEnquete()
	}
	
	private fun appendContentText(
		src : String?,
		selectBefore : Boolean = false
	) {
		if(src?.isEmpty() != false) return
		val svEmoji = DecodeOptions(context = this, decodeEmoji = true).decodeEmoji(src)
		if(svEmoji.isEmpty()) return
		
		val editable = etContent.text
		if(editable.isNotEmpty()
			&& ! CharacterGroup.isWhitespace(editable[editable.length - 1].toInt())
		) {
			editable.append(' ')
		}
		
		if(selectBefore) {
			val start = editable.length
			editable.append(' ')
			editable.append(svEmoji)
			etContent.text = editable
			etContent.setSelection(start)
		} else {
			editable.append(svEmoji)
			etContent.text = editable
			etContent.setSelection(editable.length)
		}
	}
	
	private fun appendContentText(src : Intent) {
		val list = ArrayList<String>()
		
		var sv : String?
		sv = src.getStringExtra(Intent.EXTRA_SUBJECT)
		if(sv?.isNotEmpty() == true) list.add(sv)
		sv = src.getStringExtra(Intent.EXTRA_TEXT)
		if(sv?.isNotEmpty() == true) list.add(sv)
		
		if(list.isNotEmpty()) {
			appendContentText(list.joinToString(" "))
		}
	}
	
	private fun initUI() {
		handler = Handler()
		density = resources.displayMetrics.density
		
		setContentView(R.layout.act_post)
		
		if(Pref.bpPostButtonBarTop(this)) {
			val bar = findViewById<View>(R.id.llFooterBar)
			val parent = bar.parent as ViewGroup
			parent.removeView(bar)
			parent.addView(bar, 0)
		}
		
		Styler.fixHorizontalMargin(findViewById(R.id.scrollView))
		Styler.fixHorizontalMargin(findViewById(R.id.llFooterBar))
		
		formRoot = findViewById(R.id.viewRoot)
		scrollView = findViewById(R.id.scrollView)
		btnAccount = findViewById(R.id.btnAccount)
		btnVisibility = findViewById(R.id.btnVisibility)
		btnAttachment = findViewById(R.id.btnAttachment)
		btnPost = findViewById(R.id.btnPost)
		llAttachment = findViewById(R.id.llAttachment)
		
		cbNSFW = findViewById(R.id.cbNSFW)
		cbContentWarning = findViewById(R.id.cbContentWarning)
		etContentWarning = findViewById(R.id.etContentWarning)
		etContent = findViewById(R.id.etContent)
		
		cbEnquete = findViewById(R.id.cbEnquete)
		llEnquete = findViewById(R.id.llEnquete)
		
		ivMedia = listOf(
			findViewById(R.id.ivMedia1),
			findViewById(R.id.ivMedia2),
			findViewById(R.id.ivMedia3),
			findViewById(R.id.ivMedia4)
		)
		
		list_etChoice = listOf(
			findViewById(R.id.etChoice1),
			findViewById(R.id.etChoice2),
			findViewById(R.id.etChoice3),
			findViewById(R.id.etChoice4)
		)
		
		tvCharCount = findViewById(R.id.tvCharCount)
		
		llReply = findViewById(R.id.llReply)
		tvReplyTo = findViewById(R.id.tvReplyTo)
		btnRemoveReply = findViewById(R.id.btnRemoveReply)
		ivReply = findViewById(R.id.ivReply)
		
		account_list = SavedAccount.loadAccountList(this@ActPost)
		SavedAccount.sort(account_list)
		
		btnAccount.setOnClickListener(this)
		btnVisibility.setOnClickListener(this)
		btnAttachment.setOnClickListener(this)
		btnPost.setOnClickListener(this)
		btnRemoveReply.setOnClickListener(this)
		
		findViewById<View>(R.id.btnPlugin).setOnClickListener(this)
		findViewById<View>(R.id.btnEmojiPicker).setOnClickListener(this)
		
		for(iv in ivMedia) {
			iv.setOnClickListener(this)
			iv.setDefaultImageResId(Styler.getAttributeResourceId(this, R.attr.ic_loading))
			iv.setErrorImageResId(Styler.getAttributeResourceId(this, R.attr.ic_unknown))
		}
		
		cbContentWarning.setOnCheckedChangeListener { _, _ ->
			updateContentWarning()
		}
		
		cbEnquete.setOnCheckedChangeListener { _, _ ->
			showEnquete()
			updateTextCount()
		}
		
		post_helper = PostHelper(this, pref, app_state.handler)
		post_helper.attachEditText(formRoot, etContent, false, object : PostHelper.Callback2 {
			override fun onTextUpdate() {
				updateTextCount()
			}
			
			override fun canOpenPopup() : Boolean {
				return true
			}
		})
		
		etContentWarning.addTextChangedListener(text_watcher)
		for(et in list_etChoice) {
			et.addTextChangedListener(text_watcher)
		}
		
		scrollView.viewTreeObserver.addOnScrollChangedListener(scroll_listener)
		
		val v = findViewById<View>(R.id.btnMore)
		v.setOnClickListener(this)
		
		etContent.contentMineTypeArray =
			acceptable_mime_types.toArray(arrayOfNulls<String>(ActPost.acceptable_mime_types.size))
		etContent.commitContentListener = commitContentListener
	}
	
	private var lastInstanceTask : TootTaskRunner? = null
	
	private fun getMaxCharCount() : Int {
		val account = account
		if(account != null && ! account.isPseudo) {
			val info = account.instance
			var lastTask = lastInstanceTask
			
			// 情報がないか古いなら再取得
			if(info == null || System.currentTimeMillis() - info.time_parse >= 300000L) {
				// 同時に実行するタスクは1つまで
				if(lastTask?.isActive != true) {
					lastTask = TootTaskRunner(this, TootTaskRunner.PROGRESS_NONE)
					lastInstanceTask = lastTask
					lastTask.run(account, object : TootTask {
						var newInfo : TootInstance? = null
						
						override fun background(client : TootApiClient) : TootApiResult? {
							val result = client.request("/api/v1/instance")
							newInfo = TootParser(this@ActPost, account).instance(result?.jsonObject)
							return result
						}
						
						override fun handleResult(result : TootApiResult?) {
							if(isFinishing || isDestroyed) return
							if(newInfo != null) {
								account.instance = newInfo
								updateTextCount()
							}
						}
					})
				}
			}
			
			if(info != null) {
				val max = info.max_toot_chars
				if(max != null && max > 0) return max
			}
		}
		return 500
	}
	
	private fun updateTextCount() {
		var length = 0
		
		var s = EmojiDecoder.decodeShortCode(etContent.text.toString())
		length += s.codePointCount(0, s.length)
		
		s = if(cbContentWarning.isChecked)
			EmojiDecoder.decodeShortCode(etContentWarning.text.toString())
		else
			""
		length += s.codePointCount(0, s.length)
		
		var max = getMaxCharCount()
		
		if(cbEnquete.isChecked) {
			max -= 150 // フレニコ固有。500-150で350になる
			for(et in list_etChoice) {
				s = EmojiDecoder.decodeShortCode(et.text.toString())
				length += s.codePointCount(0, s.length)
			}
		}
		
		val remain = max - length
		tvCharCount.text = Integer.toString(remain)
		val color = Styler.getAttributeColor(
			this,
			if(remain < 0) R.attr.colorRegexFilterError else android.R.attr.textColorPrimary
		)
		tvCharCount.setTextColor(color)
	}
	
	private fun updateContentWarning() {
		etContentWarning.visibility = if(cbContentWarning.isChecked) View.VISIBLE else View.GONE
	}
	
	internal fun selectAccount(a : SavedAccount?) {
		this.account = a
		if(a == null) {
			post_helper.setInstance(null)
			btnAccount.text = getString(R.string.not_selected)
			btnAccount.setTextColor(Styler.getAttributeColor(this, android.R.attr.textColorPrimary))
			btnAccount.setBackgroundResource(R.drawable.btn_bg_transparent)
		} else {
			post_helper.setInstance(a.host)
			
			// 先読みしてキャッシュに保持しておく
			App1.custom_emoji_lister.getList(a.host) {
				// 何もしない
			}
			
			val acct = a.acct
			val ac = AcctColor.load(acct)
			val nickname = if(AcctColor.hasNickname(ac)) ac.nickname else acct
			btnAccount.text = nickname
			
			if(AcctColor.hasColorBackground(ac)) {
				btnAccount.setBackgroundColor(ac.color_bg)
			} else {
				btnAccount.setBackgroundResource(R.drawable.btn_bg_transparent)
			}
			if(AcctColor.hasColorForeground(ac)) {
				btnAccount.setTextColor(ac.color_fg)
			} else {
				btnAccount.setTextColor(
					Styler.getAttributeColor(
						this,
						android.R.attr.textColorPrimary
					)
				)
			}
		}
	}
	
	private fun performAccountChooser() {
		
		if(! attachment_list.isEmpty()) {
			// 添付ファイルがあったら確認の上添付ファイルを捨てないと切り替えられない
			showToast(this, false, R.string.cant_change_account_when_attachment_specified)
			return
		}
		
		if(redraft_status_id != 0L) {
			// 添付ファイルがあったら確認の上添付ファイルを捨てないと切り替えられない
			showToast(this, false, R.string.cant_change_account_when_redraft)
			return
		}
		
		AccountPicker.pick(
			this,
			bAllowPseudo = false,
			bAuto = false,
			message = getString(R.string.choose_account)
		) { ai ->
			
			// 別タンスのアカウントに変更したならならin_reply_toの変換が必要
			if(in_reply_to_id != - 1L && ! ai.host.equals(account?.host, ignoreCase = true)) {
				startReplyConversion(ai)
			}
			
			setAccountWithVisibilityConversion(ai)
		}
		
		//		final ArrayList< SavedAccount > tmp_account_list = new ArrayList<>();
		//		tmp_account_list.addAll( account_list );
		//
		//		String[] caption_list = new String[ tmp_account_list.size() ];
		//		for( int i = 0, ie = tmp_account_list.size() ; i < ie ; ++ i ){
		//			caption_list[ i ] = tmp_account_list.get( i ).acct;
		//		}
		//
		//		new AlertDialog.Builder( this )
		//			.setTitle( R.string.choose_account )
		//			.setItems( caption_list, new DialogInterface.OnClickListener() {
		//				@Override
		//				public void onClick( DialogInterface dialog, int which ){
		//
		//					if( which < 0 || which >= tmp_account_list.size() ){
		//						// 範囲外
		//						return;
		//					}
		//
		//					SavedAccount ai = tmp_account_list.get( which );
		//
		//					if( ! ai.host.equals( account.host ) ){
		//						// 別タンスへの移動
		//						if( in_reply_to_id != - 1L ){
		//							// 別タンスのアカウントならin_reply_toの変換が必要
		//							startReplyConversion( ai );
		//
		//						}
		//					}
		//
		//					// リプライがないか、同タンスへの移動
		//					setAccountWithVisibilityConversion( ai );
		//				}
		//			} )
		//			.setNegativeButton( R.string.cancel, null )
		//			.show();
	}
	
	internal fun setAccountWithVisibilityConversion(a : SavedAccount) {
		selectAccount(a)
		try {
			if(TootStatus.isVisibilitySpoilRequired(this.visibility, a.visibility)) {
				showToast(this@ActPost, true, R.string.spoil_visibility_for_account)
				this.visibility = a.visibility
				showVisibility()
			}
			
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
	}
	
	@SuppressLint("StaticFieldLeak")
	private fun startReplyConversion(access_info : SavedAccount) {
		val in_reply_to_url = this.in_reply_to_url
		if(in_reply_to_url == null) {
			// 下書きが古い形式の場合、URLがないので別タンスへの移動ができない
			AlertDialog.Builder(this@ActPost)
				.setMessage(R.string.account_change_failed_old_draft_has_no_in_reply_to_url)
				.setNeutralButton(R.string.close, null)
				.show()
			return
		}
		
		TootTaskRunner(this)
			.progressPrefix(getString(R.string.progress_synchronize_toot))
			.run(access_info, object : TootTask {
				
				var target_status : TootStatus? = null
				override fun background(client : TootApiClient) : TootApiResult? {
					// 検索APIに他タンスのステータスのURLを投げると、自タンスのステータスを得られる
					val path = String.format(
						Locale.JAPAN,
						Column.PATH_SEARCH,
						in_reply_to_url.encodePercent()
					) + "&resolve=1"
					
					val result = client.request(path)
					val jsonObject = result?.jsonObject
					if(jsonObject != null) {
						val tmp = TootParser(this@ActPost, access_info).results(jsonObject)
						if(tmp?.statuses?.isNotEmpty() == true) {
							target_status = tmp.statuses[0]
						}
						if(target_status == null) {
							return TootApiResult(getString(R.string.status_id_conversion_failed))
						}
					}
					return result
				}
				
				override fun handleResult(result : TootApiResult?) {
					if(result == null) return  // cancelled.
					
					val target_status = this.target_status
					if(target_status != null) {
						in_reply_to_id = target_status.id
						setAccountWithVisibilityConversion(access_info)
					} else {
						showToast(
							this@ActPost,
							true,
							getString(R.string.in_reply_to_id_conversion_failed) + "\n" + result.error
						)
					}
				}
			})
		
	}
	
	//////////////////////////////////////////////////////////
	// Attachment
	
	private fun showMediaAttachment() {
		
		if(isFinishing) return
		
		if(attachment_list.isEmpty()) {
			llAttachment.visibility = View.GONE
		} else {
			llAttachment.visibility = View.VISIBLE
			var i = 0
			val ie = ivMedia.size
			while(i < ie) {
				showAttachment_sub(ivMedia[i], i)
				++ i
			}
		}
	}
	
	private fun showAttachment_sub(iv : MyNetworkImageView, idx : Int) {
		if(idx >= attachment_list.size) {
			iv.visibility = View.GONE
		} else {
			iv.visibility = View.VISIBLE
			val pa = attachment_list[idx]
			val a = pa.attachment
			if(pa.status == PostAttachment.STATUS_UPLOADED && a != null) {
				iv.setImageUrl(pref, Styler.calcIconRound(iv.layoutParams.width), a.preview_url)
			} else {
				iv.setImageUrl(pref, Styler.calcIconRound(iv.layoutParams.width), null)
			}
		}
	}
	
	// 添付した画像をタップ
	private fun performAttachmentClick(idx : Int) {
		val pa = try {
			attachment_list[idx]
		} catch(ex : Throwable) {
			showToast(this, false, ex.withCaption("can't get attachment item[$idx]."))
			return
		}
		
		AlertDialog.Builder(this)
			.setTitle(R.string.media_attachment)
			.setItems(
				arrayOf<CharSequence>(
					getString(R.string.set_description),
					getString(R.string.set_focus_point),
					getString(R.string.delete)
				)
			) { _, i ->
				when(i) {
					0 -> editAttachmentDescription(pa)
					1 -> openFocusPoint(pa)
					2 -> deleteAttachment(pa)
				}
			}
			.setNegativeButton(R.string.cancel, null)
			.show()
	}
	
	private fun openFocusPoint(pa : PostAttachment) {
		val attachment = pa.attachment
		if(attachment != null) {
			DlgFocusPoint(this, attachment)
				.setCallback(object : FocusPointView.Callback {
					override fun onFocusPointUpdate(x : Float, y : Float) {
						val account = this@ActPost.account ?: return
						
						TootTaskRunner(this@ActPost, TootTaskRunner.PROGRESS_NONE).run(account,
							object : TootTask {
								override fun background(client : TootApiClient) : TootApiResult? {
									try {
										val json = JSONObject()
										json.put("focus", "%.2f,%.2f".format(x, y))
										val result = client.request(
											"/api/v1/media/" + attachment.id,
											Request.Builder().put(
												RequestBody.create(
													TootApiClient.MEDIA_TYPE_JSON, json.toString()
												)
											)
										)
										new_attachment =
											parseItem(::TootAttachment, result?.jsonObject)
										return result
									} catch(ex : Throwable) {
										return TootApiResult(ex.withCaption("set focus point failed."))
									}
								}
								
								var new_attachment : TootAttachment? = null
								
								override fun handleResult(result : TootApiResult?) {
									result ?: return
									if(new_attachment != null) {
										pa.attachment = attachment
									} else {
										showToast(this@ActPost, true, result.error)
									}
								}
							})
					}
				})
				.show()
		}
	}
	
	private fun deleteAttachment(pa : PostAttachment) {
		AlertDialog.Builder(this)
			.setTitle(R.string.confirm_delete_attachment)
			.setPositiveButton(R.string.ok) { _, _ ->
				try {
					attachment_list.remove(pa)
				} catch(ignored : Throwable) {
				}
				
				showMediaAttachment()
			}
			.setNegativeButton(R.string.cancel, null)
			.show()
		
	}
	
	private fun editAttachmentDescription(pa : PostAttachment) {
		val a = pa.attachment
		if(a == null) {
			showToast(this, true, R.string.attachment_description_cant_edit_while_uploading)
			return
		}
		
		DlgTextInput.show(
			this,
			getString(R.string.attachment_description),
			a.description,
			object : DlgTextInput.Callback {
				override fun onOK(dialog : Dialog, text : String) {
					setAttachmentDescription(pa, dialog, text)
				}
				
				override fun onEmptyError() {
					showToast(this@ActPost, true, R.string.description_empty)
				}
			})
	}
	
	@SuppressLint("StaticFieldLeak")
	private fun setAttachmentDescription(pa : PostAttachment, dialog : Dialog, text : String) {
		
		val attachment_id = pa.attachment?.id ?: return
		
		TootTaskRunner(this).run(this@ActPost.account ?: return, object : TootTask {
			
			var new_attachment : TootAttachment? = null
			
			override fun background(client : TootApiClient) : TootApiResult? {
				val json = JSONObject()
				try {
					json.put("description", text)
				} catch(ex : JSONException) {
					log.trace(ex)
					log.e(ex, "description encoding failed.")
				}
				
				val body_string = json.toString()
				val request_body = RequestBody.create(
					TootApiClient.MEDIA_TYPE_JSON, body_string
				)
				val request_builder = Request.Builder().put(request_body)
				val result = client.request("/api/v1/media/$attachment_id", request_builder)
				new_attachment = parseItem(::TootAttachment, result?.jsonObject)
				return result
			}
			
			override fun handleResult(result : TootApiResult?) {
				if(result == null) return  // cancelled.
				
				val new_attachment = this.new_attachment
				if(new_attachment != null) {
					pa.attachment = new_attachment
					showMediaAttachment()
					
					try {
						dialog.dismiss()
					} catch(ignored : Throwable) {
					}
					
				} else {
					showToast(this@ActPost, true, result.error)
				}
			}
		})
	}
	
	private fun openAttachment() {
		val permissionCheck =
			ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
		if(permissionCheck != PackageManager.PERMISSION_GRANTED) {
			preparePermission()
			return
		}
		
		//		permissionCheck = ContextCompat.checkSelfPermission( this, Manifest.permission.CAMERA );
		//		if( permissionCheck != PackageManager.PERMISSION_GRANTED ){
		//			preparePermission();
		//			return;
		//		}
		
		val a = ActionsDialog()
		a.addAction(getString(R.string.image_pick)) { performAttachment() }
		a.addAction(getString(R.string.image_capture)) { performCamera() }
		
		//		a.addAction( getString( R.string.video_capture ), new Runnable() {
		//			@Override public void run(){
		//				performCameraVideo();
		//			}
		//		} );
		a.show(this, null)
		
	}
	
	private fun performAttachment() {
		
		if(attachment_list.size >= 4) {
			showToast(this, false, R.string.attachment_too_many)
			return
		}
		
		if(account == null) {
			showToast(this, false, R.string.account_select_please)
			return
		}
		
		// SAFのIntentで開く
		try {
			val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
			intent.addCategory(Intent.CATEGORY_OPENABLE)
			intent.type = "*/*"
			intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
			intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
			startActivityForResult(intent, REQUEST_CODE_ATTACHMENT)
		} catch(ex : Throwable) {
			log.trace(ex)
			showToast(this, ex, "ACTION_OPEN_DOCUMENT failed.")
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
				val resize_to = list_resize_max[Pref.ipResizeImage(pref)]
				
				val bitmap = createResizedBitmap(
					this,
					uri,
					resize_to,
					skipIfNoNeedToResizeAndRotate = true
				)
				if(bitmap != null) {
					try {
						val cache_dir = externalCacheDir
						if(cache_dir == null) {
							showToast(this, false, "getExternalCacheDir returns null.")
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
				showToast(this, ex, "Resizing image failed.")
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
	
	private fun getMimeType(uri : Uri, mimeTypeArg : String?) : String? {
		
		// 既に引数で与えられてる
		if(mimeTypeArg?.isNotEmpty() == true) return mimeTypeArg
		
		// ContentResolverに尋ねる
		var sv = contentResolver.getType(uri)
		if(sv?.isNotEmpty() == true) return sv
		
		// gboardのステッカーではUriのクエリパラメータにmimeType引数がある
		sv = uri.getQueryParameter("mimeType")
		if(sv?.isNotEmpty() == true) return sv
		
		return null
	}
	
	@SuppressLint("StaticFieldLeak")
	private fun addAttachment(
		uri : Uri,
		mimeTypeArg : String? = null,
		onUploadEnd : () -> Unit = {}
	) {
		
		if(attachment_list.size >= 4) {
			showToast(this, false, R.string.attachment_too_many)
			return
		}
		
		val account = this@ActPost.account
		if(account == null) {
			showToast(this, false, R.string.account_select_please)
			return
		}
		
		val mime_type = getMimeType(uri, mimeTypeArg)
		if(mime_type?.isEmpty() != false) {
			showToast(this, false, R.string.mime_type_missing)
			return
		}
		
		if(! acceptable_mime_types.contains(mime_type)) {
			showToast(this, true, R.string.mime_type_not_acceptable, mime_type)
			return
		}
		
		app_state.attachment_list = this.attachment_list
		
		val pa = PostAttachment(this)
		attachment_list.add(pa)
		showMediaAttachment()
		showToast(this, false, R.string.attachment_uploading)
		
		TootTaskRunner(this, TootTaskRunner.PROGRESS_NONE).run(account, object : TootTask {
			override fun background(client : TootApiClient) : TootApiResult? {
				if(mime_type.isEmpty()) {
					return TootApiResult("mime_type is empty.")
				}
				
				try {
					val opener = createOpener(uri, mime_type)
					
					val media_size_max =
						1000000 * Math.max(1, Pref.spMediaSizeMax.toInt(pref))
					
					val content_length = getStreamSize(true, opener.open())
					if(content_length > media_size_max) {
						return TootApiResult(
							getString(
								R.string.file_size_too_big,
								media_size_max / 1000000
							)
						)
					}
					val multipart_body = MultipartBody.Builder()
						.setType(MultipartBody.FORM)
						.addFormDataPart(
							"file", getDocumentName(uri), object : RequestBody() {
								override fun contentType() : MediaType? {
									return MediaType.parse(opener.mimeType)
								}
								
								@Throws(IOException::class)
								override fun contentLength() : Long {
									return content_length
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
						.build()
					
					val request_builder = Request.Builder()
						.post(multipart_body)
					
					val result = client.request("/api/v1/media", request_builder)
					
					opener.deleteTempFile()
					onUploadEnd()
					
					val jsonObject = result?.jsonObject
					if(jsonObject != null) {
						val a = parseItem(::TootAttachment, jsonObject)
						if(a == null) {
							result.error = "TootAttachment.parse failed"
						} else {
							pa.attachment = a
						}
					}
					return result
					
				} catch(ex : Throwable) {
					return TootApiResult(ex.withCaption("read failed."))
				}
				
			}
			
			override fun handleResult(result : TootApiResult?) {
				if(pa.attachment == null) {
					pa.status = PostAttachment.STATUS_UPLOAD_FAILED
					if(result != null) {
						showToast(this@ActPost, true, result.error)
					}
				} else {
					pa.status = PostAttachment.STATUS_UPLOADED
				}
				// 投稿中に画面回転があった場合、新しい画面のコールバックを呼び出す必要がある
				pa.callback?.onPostAttachmentComplete(pa)
			}
		})
	}
	
	// 添付メディア投稿が完了したら呼ばれる
	override fun onPostAttachmentComplete(pa : PostAttachment) {
		if(! attachment_list.contains(pa)) {
			// この添付メディアはリストにない
			return
		}
		
		when(pa.status) {
			PostAttachment.STATUS_UPLOAD_FAILED -> {
				// アップロード失敗
				attachment_list.remove(pa)
				showMediaAttachment()
			}
			
			PostAttachment.STATUS_UPLOADED -> {
				val a = pa.attachment
				if(a != null) {
					// アップロード完了
					showToast(this@ActPost, false, R.string.attachment_uploaded)
					
					if(Pref.bpAppendAttachmentUrlToContent(pref)) {
						// 投稿欄の末尾に追記する
						val selStart = etContent.selectionStart
						val selEnd = etContent.selectionEnd
						val e = etContent.editableText
						val len = e.length
						val last_char = if(len <= 0) ' ' else e[len - 1]
						if(! CharacterGroup.isWhitespace(last_char.toInt())) {
							e.append(" ").append(a.text_url)
						} else {
							e.append(a.text_url)
						}
						etContent.setSelection(selStart, selEnd)
					}
					
				}
				
				showMediaAttachment()
			}
			
			else -> {
				// アップロード中…？
			}
		}
	}
	
	private fun performCamera() {
		
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
			
			startActivityForResult(intent, REQUEST_CODE_CAMERA)
		} catch(ex : Throwable) {
			log.trace(ex)
			showToast(this, ex, "opening camera app failed.")
		}
		
	}
	
	private fun preparePermission() {
		if(Build.VERSION.SDK_INT >= 23) {
			// No explanation needed, we can request the permission.
			
			ActivityCompat.requestPermissions(
				this,
				arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE) //		Manifest.permission.CAMERA,
				,
				PERMISSION_REQUEST_CODE
			)
		} else {
			showToast(this, true, R.string.missing_permission_to_access_media)
		}
	}
	
	override fun onRequestPermissionsResult(
		requestCode : Int, permissions : Array<String>, grantResults : IntArray
	) {
		when(requestCode) {
			PERMISSION_REQUEST_CODE -> {
				var bNotGranted = false
				var i = 0
				val ie = permissions.size
				while(i < ie) {
					if(grantResults[i] != PackageManager.PERMISSION_GRANTED) {
						bNotGranted = true
					}
					++ i
				}
				if(bNotGranted) {
					showToast(this, true, R.string.missing_permission_to_access_media)
				} else {
					openAttachment()
				}
			}
		}
	}
	
	fun getDocumentName(uri : Uri) : String {
		val errorName = "no_name"
		return contentResolver.query(uri, null, null, null, null, null)
			?.use { cursor ->
				return if(! cursor.moveToFirst()) {
					errorName
				} else {
					val colIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
					if(cursor.isNull(colIdx)) {
						errorName
					} else {
						cursor.getString(colIdx)
					}
				}
			}
			?: errorName
	}
	
	@Throws(IOException::class)
	internal fun getStreamSize(bClose : Boolean, inStream : InputStream) : Long {
		try {
			var size = 0L
			while(true) {
				val r = IOUtils.skip(inStream, 16384)
				if(r <= 0) break
				size += r
			}
			return size
		} finally {
			@Suppress("DEPRECATION")
			if(bClose) IOUtils.closeQuietly(inStream)
		}
	}
	
	private fun showVisibility() {
		btnVisibility.setImageResource(Styler.getVisibilityIcon(this, visibility))
	}
	
	private fun performVisibility() {
		val caption_list = arrayOf(
			Styler.getVisibilityCaption(this, TootStatus.VISIBILITY_WEB_SETTING),
			Styler.getVisibilityCaption(this, TootStatus.VISIBILITY_PUBLIC),
			Styler.getVisibilityCaption(this, TootStatus.VISIBILITY_UNLISTED),
			Styler.getVisibilityCaption(this, TootStatus.VISIBILITY_PRIVATE),
			Styler.getVisibilityCaption(this, TootStatus.VISIBILITY_DIRECT)
		)
		
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
				showVisibility()
			}
			.setNegativeButton(R.string.cancel, null)
			.show()
		
	}
	
	///////////////////////////////////////////////////////////////////////////////////////
	
	private fun performMore() {
		val dialog = ActionsDialog()
		
		dialog.addAction(getString(R.string.open_picker_emoji)) {
			post_helper.openEmojiPickerFromMore()
		}
		
		dialog.addAction(getString(R.string.clear_text)) {
			etContent.setText("")
			etContentWarning.setText("")
		}
		
		dialog.addAction(getString(R.string.clear_text_and_media)) {
			etContent.setText("")
			etContentWarning.setText("")
			attachment_list.clear()
			showMediaAttachment()
		}
		
		if(PostDraft.hasDraft()) dialog.addAction(getString(R.string.restore_draft)) {
			openDraftPicker()
		}
		
		dialog.addAction(getString(R.string.recommended_plugin)) {
			showRecommendedPlugin(null)
		}
		
		dialog.show(this, null)
	}
	
	///////////////////////////////////////////////////////////////////////////////////////
	// post
	
	private fun performPost() {
		val account = this.account ?: return
		
		// アップロード中は投稿できない
		for(pa in attachment_list) {
			if(pa.status == PostAttachment.STATUS_UPLOADING) {
				showToast(this, false, R.string.media_attachment_still_uploading)
				return
			}
		}
		
		post_helper.content = etContent.text.toString().trim { it <= ' ' }
		
		if(! cbEnquete.isChecked) {
			post_helper.enquete_items = null
		} else {
			val enquete_items = ArrayList<String>()
			for(et in list_etChoice) {
				enquete_items.add(et.text.toString().trim { it <= ' ' })
			}
			post_helper.enquete_items = enquete_items
		}
		
		if(! cbContentWarning.isChecked) {
			post_helper.spoiler_text = null // nullはCWチェックなしを示す
		} else {
			post_helper.spoiler_text = etContentWarning.text.toString().trim { it <= ' ' }
		}
		
		post_helper.visibility = this.visibility
		post_helper.bNSFW = cbNSFW.isChecked
		
		post_helper.in_reply_to_id = this.in_reply_to_id
		
		post_helper.attachment_list = this.attachment_list
		
		post_helper.emojiMapCustom = App1.custom_emoji_lister.getMap(account.host)
		
		post_helper.redraft_status_id = redraft_status_id
		
		post_helper.post(account) { target_account, status ->
			val data = Intent()
			data.putExtra(EXTRA_POSTED_ACCT, target_account.acct)
			data.putExtra(EXTRA_POSTED_STATUS_ID, status.id)
			data.putExtra(EXTRA_POSTED_REDRAFT_ID, redraft_status_id)
			val reply_id = status.in_reply_to_id
			if(reply_id != null) data.putExtra(EXTRA_POSTED_REPLY_ID, reply_id)
			setResult(RESULT_OK, data)
			isPostComplete = true
			this@ActPost.finish()
		}
	}
	
	internal fun showReplyTo() {
		if(in_reply_to_id == - 1L) {
			llReply.visibility = View.GONE
		} else {
			llReply.visibility = View.VISIBLE
			tvReplyTo.text = DecodeOptions(
				this@ActPost,
				linkHelper = account,
				short = true,
				decodeEmoji = true
			
			).decodeHTML(in_reply_to_text)
			ivReply.setImageUrl(pref, Styler.calcIconRound(ivReply.layoutParams), in_reply_to_image)
		}
	}
	
	private fun removeReply() {
		in_reply_to_id = - 1L
		in_reply_to_text = null
		in_reply_to_image = null
		in_reply_to_url = null
		showReplyTo()
	}
	
	private fun saveDraft() {
		val content = etContent.text.toString()
		val content_warning =
			if(cbContentWarning.isChecked) etContentWarning.text.toString() else ""
		val isEnquete = cbEnquete.isChecked
		
		val str_choice = arrayOf(
			if(isEnquete) list_etChoice[0].text.toString() else "",
			if(isEnquete) list_etChoice[1].text.toString() else "",
			if(isEnquete) list_etChoice[2].text.toString() else "",
			if(isEnquete) list_etChoice[3].text.toString() else ""
		)
		
		var hasContent = false
		if(content.isNotBlank()) hasContent = true
		if(content_warning.isNotBlank()) hasContent = true
		for(s in str_choice) {
			if(s.isNotBlank()) hasContent = true
		}
		if(! hasContent) {
			log.d("saveDraft: dont save empty content")
			return
		}
		
		try {
			val tmp_attachment_list = JSONArray()
			for(pa in attachment_list) {
				val a = pa.attachment
				if(a != null) tmp_attachment_list.put(a.json)
			}
			
			val json = JSONObject()
			json.put(DRAFT_CONTENT, content)
			json.put(DRAFT_CONTENT_WARNING, content_warning)
			json.put(DRAFT_CONTENT_WARNING_CHECK, cbContentWarning.isChecked)
			json.put(DRAFT_NSFW_CHECK, cbNSFW.isChecked)
			json.put(DRAFT_VISIBILITY, visibility)
			json.put(DRAFT_ACCOUNT_DB_ID, account?.db_id ?: - 1L)
			json.put(DRAFT_ATTACHMENT_LIST, tmp_attachment_list)
			json.put(DRAFT_REPLY_ID, in_reply_to_id)
			json.put(DRAFT_REPLY_TEXT, in_reply_to_text)
			json.put(DRAFT_REPLY_IMAGE, in_reply_to_image)
			json.put(DRAFT_REPLY_URL, in_reply_to_url)
			
			json.put(DRAFT_IS_ENQUETE, isEnquete)
			val array = JSONArray()
			for(s in str_choice) {
				array.put(s)
			}
			json.put(DRAFT_ENQUETE_ITEMS, array)
			
			PostDraft.save(System.currentTimeMillis(), json)
			
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
	}
	
	private fun openDraftPicker() {
		
		DlgDraftPicker().open(this) { draft -> restoreDraft(draft) }
		
	}
	
	private fun restoreDraft(draft : JSONObject) {
		
		@Suppress("DEPRECATION")
		val progress = ProgressDialogEx(this)
		
		val task = @SuppressLint("StaticFieldLeak")
		object : AsyncTask<Void, String, String?>() {
			
			val list_warning = ArrayList<String>()
			var account : SavedAccount? = null
			
			override fun doInBackground(vararg params : Void) : String? {
				
				var content = draft.parseString(DRAFT_CONTENT) ?: ""
				val account_db_id = draft.parseLong(DRAFT_ACCOUNT_DB_ID) ?: - 1L
				var tmp_attachment_list = draft.optJSONArray(DRAFT_ATTACHMENT_LIST)
				
				val account = SavedAccount.loadAccount(this@ActPost, account_db_id)
				if(account == null) {
					list_warning.add(getString(R.string.account_in_draft_is_lost))
					try {
						var i = 0
						val ie = tmp_attachment_list.length()
						while(i < ie) {
							val ta =
								parseItem(::TootAttachment, tmp_attachment_list.optJSONObject(i))
							val text_url = ta?.text_url
							if(text_url?.isNotEmpty() == true) {
								content = content.replace(text_url, "")
							}
							++ i
						}
						tmp_attachment_list = JSONArray()
						draft.put(DRAFT_ATTACHMENT_LIST, tmp_attachment_list)
						draft.put(DRAFT_CONTENT, content)
						draft.remove(DRAFT_REPLY_ID)
						draft.remove(DRAFT_REPLY_TEXT)
						draft.remove(DRAFT_REPLY_IMAGE)
						draft.remove(DRAFT_REPLY_URL)
					} catch(ignored : JSONException) {
					}
					
					return "OK"
				}
				this.account = account
				
				// アカウントがあるなら基本的にはすべての情報を復元できるはずだが、いくつか確認が必要だ
				val api_client = TootApiClient(this@ActPost, callback = object : TootApiCallback {
					
					override val isApiCancelled : Boolean
						get() = isCancelled
					
					override fun publishApiProgress(s : String) {
						runOnMainLooper { progress.setMessage(s) }
					}
				})
				
				api_client.account = account
				
				if(in_reply_to_id != - 1L) {
					val result = api_client.request("/api/v1/statuses/$in_reply_to_id")
					if(isCancelled) return null
					val jsonObject = result?.jsonObject
					if(jsonObject == null) {
						list_warning.add(getString(R.string.reply_to_in_draft_is_lost))
						draft.remove(DRAFT_REPLY_ID)
						draft.remove(DRAFT_REPLY_TEXT)
						draft.remove(DRAFT_REPLY_IMAGE)
					}
				}
				try {
					var isSomeAttachmentRemoved = false
					for(i in tmp_attachment_list.length() - 1 downTo 0) {
						if(isCancelled) return null
						val ta = parseItem(::TootAttachment, tmp_attachment_list.optJSONObject(i))
						if(ta == null) {
							isSomeAttachmentRemoved = true
							tmp_attachment_list.remove(i)
						} else if(! check_exist(ta.url)) {
							isSomeAttachmentRemoved = true
							tmp_attachment_list.remove(i)
							val text_url = ta.text_url
							if(text_url?.isNotEmpty() == true) {
								content = content.replace(text_url, "")
							}
						}
					}
					if(isSomeAttachmentRemoved) {
						list_warning.add(getString(R.string.attachment_in_draft_is_lost))
						draft.put(DRAFT_ATTACHMENT_LIST, tmp_attachment_list)
						draft.put(DRAFT_CONTENT, content)
					}
				} catch(ex : JSONException) {
					log.trace(ex)
				}
				
				return "OK"
			}
			
			override fun onCancelled(result : String?) {
				onPostExecute(result)
			}
			
			override fun onPostExecute(result : String?) {
				progress.dismiss()
				
				if(isCancelled || result == null) {
					// cancelled.
					return
				}
				
				val content = draft.optString(DRAFT_CONTENT)
				val content_warning = draft.optString(DRAFT_CONTENT_WARNING)
				val content_warning_checked = draft.optBoolean(DRAFT_CONTENT_WARNING_CHECK)
				val nsfw_checked = draft.optBoolean(DRAFT_NSFW_CHECK)
				val tmp_attachment_list = draft.optJSONArray(DRAFT_ATTACHMENT_LIST)
				val reply_id = draft.parseLong(DRAFT_REPLY_ID) ?: - 1L
				val reply_text = draft.optString(DRAFT_REPLY_TEXT, null)
				val reply_image = draft.optString(DRAFT_REPLY_IMAGE, null)
				val reply_url = draft.optString(DRAFT_REPLY_URL, null)
				val draft_visibility = draft.parseString(DRAFT_VISIBILITY)
				
				val evEmoji = DecodeOptions(this@ActPost, decodeEmoji = true).decodeEmoji(content)
				etContent.setText(evEmoji)
				etContent.setSelection(evEmoji.length)
				etContentWarning.setText(content_warning)
				etContentWarning.setSelection(content_warning.length)
				cbContentWarning.isChecked = content_warning_checked
				cbNSFW.isChecked = nsfw_checked
				if(draft_visibility != null) this@ActPost.visibility = draft_visibility
				
				cbEnquete.isChecked = draft.optBoolean(DRAFT_IS_ENQUETE, false)
				val array = draft.optJSONArray(DRAFT_ENQUETE_ITEMS)
				if(array != null) {
					var src_index = 0
					for(et in list_etChoice) {
						if(src_index < array.length()) {
							et.setText(array.optString(src_index))
							++ src_index
						} else {
							et.setText("")
						}
					}
				}
				
				if(account != null) selectAccount(account)
				
				if(tmp_attachment_list.length() > 0) {
					attachment_list.clear()
					var i = 0
					val ie = tmp_attachment_list.length()
					while(i < ie) {
						val ta = parseItem(::TootAttachment, tmp_attachment_list.optJSONObject(i))
						if(ta != null) {
							val pa = PostAttachment(ta)
							attachment_list.add(pa)
						}
						++ i
					}
				}
				if(reply_id != - 1L) {
					in_reply_to_id = reply_id
					in_reply_to_text = reply_text
					in_reply_to_image = reply_image
					in_reply_to_url = reply_url
				}
				
				updateContentWarning()
				showMediaAttachment()
				showVisibility()
				updateTextCount()
				showReplyTo()
				showEnquete()
				
				if(! list_warning.isEmpty()) {
					val sb = StringBuilder()
					for(s in list_warning) {
						if(sb.isNotEmpty()) sb.append("\n")
						sb.append(s)
					}
					AlertDialog.Builder(this@ActPost)
						.setMessage(sb)
						.setNeutralButton(R.string.close, null)
						.show()
				}
			}
		}
		progress.isIndeterminate = true
		progress.setCancelable(true)
		progress.setOnCancelListener { task.cancel(true) }
		progress.show()
		task.executeOnExecutor(App1.task_executor)
	}
	
	private fun prepareMushroomText(et : EditText) : String {
		mushroom_start = et.selectionStart
		mushroom_end = et.selectionEnd
		return if(mushroom_end > mushroom_start) {
			et.text.toString().substring(mushroom_start, mushroom_end)
		} else {
			""
		}
	}
	
	private fun applyMushroomText(et : EditText, text : String) {
		val src = et.text.toString()
		if(mushroom_start > src.length) mushroom_start = src.length
		if(mushroom_end > src.length) mushroom_end = src.length
		
		val sb = StringBuilder()
		sb.append(src.substring(0, mushroom_start))
		// int new_sel_start = sb.length();
		sb.append(text)
		val new_sel_end = sb.length
		sb.append(src.substring(mushroom_end))
		et.setText(sb)
		et.setSelection(new_sel_end, new_sel_end)
	}
	
	private fun openMushroom() {
		try {
			var text : String? = null
			when {
				etContentWarning.hasFocus() -> {
					mushroom_input = 1
					text = prepareMushroomText(etContentWarning)
				}
				
				etContent.hasFocus() -> {
					mushroom_input = 0
					text = prepareMushroomText(etContent)
				}
				
				else -> for(i in 0 .. 3) {
					if(list_etChoice[i].hasFocus()) {
						mushroom_input = i + 2
						text = prepareMushroomText(list_etChoice[i])
					}
				}
			}
			if(text == null) {
				mushroom_input = 0
				text = prepareMushroomText(etContent)
			}
			
			val intent = Intent("com.adamrocker.android.simeji.ACTION_INTERCEPT")
			intent.addCategory("com.adamrocker.android.simeji.REPLACE")
			intent.putExtra("replace_key", text)
			
			// Create intent to show chooser
			val chooser = Intent.createChooser(intent, getString(R.string.select_plugin))
			
			// Verify the intent will resolve to at least one activity
			if(intent.resolveActivity(packageManager) == null) {
				showRecommendedPlugin(getString(R.string.plugin_not_installed))
				return
			}
			startActivityForResult(chooser, REQUEST_CODE_MUSHROOM)
			
		} catch(ex : Throwable) {
			log.trace(ex)
			showRecommendedPlugin(getString(R.string.plugin_not_installed))
		}
		
	}
	
	private fun applyMushroomResult(text : String) {
		when(mushroom_input) {
			0 -> applyMushroomText(etContent, text)
			1 -> applyMushroomText(etContentWarning, text)
			else -> for(i in 0 .. 3) {
				if(mushroom_input == i + 2) {
					applyMushroomText(list_etChoice[i], text)
				}
			}
		}
	}
	
	@SuppressLint("InflateParams")
	private fun showRecommendedPlugin(title : String?) {
		
		val res_id = when(getString(R.string.language_code)) {
			"ja" -> R.raw.recommended_plugin_ja
			"fr" -> R.raw.recommended_plugin_fr
			else -> R.raw.recommended_plugin_en
		}
		
		this.loadRawResource(res_id)?.let { data ->
			val text = data.decodeUTF8()
			val viewRoot = layoutInflater.inflate(R.layout.dlg_plugin_missing, null, false)
			
			val tvText = viewRoot.findViewById<TextView>(R.id.tvText)
			val lcc = object : LinkHelper {
				override val host : String?
					get() = null
			}
			val sv = DecodeOptions(this@ActPost, lcc).decodeHTML(text)
			tvText.text = sv
			tvText.movementMethod = LinkMovementMethod.getInstance()
			
			val tvTitle = viewRoot.findViewById<TextView>(R.id.tvTitle)
			if(title?.isEmpty() != false) {
				tvTitle.visibility = View.GONE
			} else {
				tvTitle.text = title
			}
			
			AlertDialog.Builder(this)
				.setView(viewRoot)
				.setCancelable(true)
				.setNeutralButton(R.string.close, null)
				.show()
			
		}
		
	}
	
	private fun showEnquete() {
		llEnquete.visibility = if(cbEnquete.isChecked) View.VISIBLE else View.GONE
	}
	
	private val commitContentListener =
		InputConnectionCompat.OnCommitContentListener { inputContentInfo : InputContentInfoCompat,
		                                                flags : Int,
		                                                _ : Bundle? ->
			
			// Intercepts InputConnection#commitContent API calls.
			// - inputContentInfo : content to be committed
			// - flags : {@code 0} or {@link #INPUT_CONTENT_GRANT_READ_URI_PERMISSION}
			// - opts : optional bundle data. This can be {@code null}
			// return
			// - true if this request is accepted by the application,
			//   no matter if the request is already handled or still being handled in background.
			// - false to use the default implementation
			
			// read and display inputContentInfo asynchronously
			
			if(Build.VERSION.SDK_INT >= 25
				&& flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION != 0
			) {
				try {
					inputContentInfo.requestPermission()
				} catch(e : Exception) {
					return@OnCommitContentListener false // return false if failed
				}
			}
			
			addAttachment(inputContentInfo.contentUri) {
				inputContentInfo.releasePermission()
			}
			
			true
		}
}
