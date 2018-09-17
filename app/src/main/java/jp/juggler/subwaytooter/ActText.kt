package jp.juggler.subwaytooter

import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.EditText
import jp.juggler.subwaytooter.api.entity.*

import java.util.Locale

import jp.juggler.subwaytooter.table.MutedWord
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.showToast

class ActText : AppCompatActivity(), View.OnClickListener {
	
	companion object {
		
		internal val log = LogCategory("ActText")
		
		internal const val RESULT_SEARCH_MSP = RESULT_FIRST_USER + 1
		internal const val RESULT_SEARCH_TS = RESULT_FIRST_USER + 2
		
		internal const val EXTRA_TEXT = "text"
		internal const val EXTRA_CONTENT_START = "content_start"
		internal const val EXTRA_CONTENT_END = "content_end"
		internal const val EXTRA_ACCOUNT_DB_ID = "account_db_id"
		
		private fun addAfterLine(sb : StringBuilder, text : String) {
			if(sb.isNotEmpty() && sb[sb.length - 1] != '\n') {
				sb.append('\n')
			}
			sb.append(text)
		}
		
		private fun addHeader(context : Context, sb : StringBuilder, key_str_id : Int, value : Any?) {
			if(sb.isNotEmpty() && sb[sb.length - 1] != '\n') {
				sb.append('\n')
			}
			addAfterLine(sb, context.getString(key_str_id))
			sb.append(": ")
			sb.append(value?.toString() ?: "(null)")
		}
		
		private fun encodeStatus(intent : Intent, context : Context, access_info : SavedAccount, status : TootStatus) {
			val sb = StringBuilder()
			
			addHeader(context, sb, R.string.send_header_url, status.url)
			
			addHeader(context, sb, R.string.send_header_date, TootStatus.formatTime(context, status.time_created_at, false))
			
			
			addHeader(context, sb, R.string.send_header_from_acct, access_info.getFullAcct(status.account))
			
			val sv :String? = status.spoiler_text
			if( sv != null && sv.isNotEmpty() ) {
				addHeader(context, sb, R.string.send_header_content_warning, sv)
			}
			
			addAfterLine(sb, "\n")
			
			intent.putExtra(EXTRA_CONTENT_START, sb.length)
			sb.append(DecodeOptions(context, access_info).decodeHTML( status.content))
			intent.putExtra(EXTRA_CONTENT_END, sb.length)
			
			dumpAttachment(sb, status.media_attachments)
			
			addAfterLine(sb, String.format(Locale.JAPAN, "Status-Source: %s", status.json))
			
			addAfterLine(sb, "")
			intent.putExtra(EXTRA_TEXT, sb.toString())
		}
		
		private fun dumpAttachment(sb : StringBuilder, src : ArrayList<TootAttachmentLike> ?) {
			if(src == null) return
			var i = 0
			for(ma in src) {
				++ i
				if( ma is TootAttachment ) {
					addAfterLine(sb, "\n")
					addAfterLine(sb, String.format(Locale.JAPAN, "Media-%d-Url: %s", i, ma.url))
					addAfterLine(sb, String.format(Locale.JAPAN, "Media-%d-Remote-Url: %s", i, ma.remote_url))
					addAfterLine(sb, String.format(Locale.JAPAN, "Media-%d-Preview-Url: %s", i, ma.preview_url))
					addAfterLine(sb, String.format(Locale.JAPAN, "Media-%d-Text-Url: %s", i, ma.text_url))
				}else if( ma is TootAttachmentMSP){
					addAfterLine(sb, "\n")
					addAfterLine(sb, String.format(Locale.JAPAN, "Media-%d-Preview-Url: %s", i, ma.preview_url))
				}
			}
		}
		
		private fun encodeAccount(intent : Intent, context : Context, access_info : SavedAccount, who : TootAccount) {
			val sb = StringBuilder()
			
			intent.putExtra(EXTRA_CONTENT_START, sb.length)
			sb.append(who.display_name)
			sb.append("\n")
			sb.append("@")
			sb.append(access_info.getFullAcct(who))
			sb.append("\n")
			
			intent.putExtra(EXTRA_CONTENT_START, sb.length)
			sb.append(who.url)
			intent.putExtra(EXTRA_CONTENT_END, sb.length)
			
			addAfterLine(sb, "\n")
			
			sb.append(DecodeOptions(context, access_info).decodeHTML( who.note))
			
			addAfterLine(sb, "\n")
			
			addHeader(context, sb, R.string.send_header_account_name, who.display_name)
			addHeader(context, sb, R.string.send_header_account_acct, access_info.getFullAcct(who))
			addHeader(context, sb, R.string.send_header_account_url, who.url)
			
			addHeader(context, sb, R.string.send_header_account_image_avatar, who.avatar)
			addHeader(context, sb, R.string.send_header_account_image_avatar_static, who.avatar_static)
			addHeader(context, sb, R.string.send_header_account_image_header, who.header)
			addHeader(context, sb, R.string.send_header_account_image_header_static, who.header_static)
			
			addHeader(context, sb, R.string.send_header_account_created_at, who.created_at)
			addHeader(context, sb, R.string.send_header_account_statuses_count, who.statuses_count)
			addHeader(context, sb, R.string.send_header_account_followers_count, who.followers_count)
			addHeader(context, sb, R.string.send_header_account_following_count, who.following_count)
			addHeader(context, sb, R.string.send_header_account_locked, who.locked)
			
			addAfterLine(sb, "")
			intent.putExtra(EXTRA_TEXT, sb.toString())
		}
		
		fun open(activity : ActMain, request_code : Int, access_info : SavedAccount, status : TootStatus) {
			val intent = Intent(activity, ActText::class.java)
			intent.putExtra(EXTRA_ACCOUNT_DB_ID,access_info.db_id)
			encodeStatus(intent, activity, access_info, status)
			
			activity.startActivityForResult(intent, request_code)
		}
		
		fun open(activity : ActMain, request_code : Int, access_info : SavedAccount, who : TootAccount) {
			val intent = Intent(activity, ActText::class.java)
			intent.putExtra(EXTRA_ACCOUNT_DB_ID,access_info.db_id)
			encodeAccount(intent, activity, access_info, who)
			
			activity.startActivityForResult(intent, request_code)
		}
		
	}
	
	private lateinit var etText : EditText
	
	private val selection : String
		get() {
			val s = etText.selectionStart
			val e = etText.selectionEnd
			val text = etText.text.toString()
			return if(s == e) {
				text
			} else {
				text.substring(s, e)
			}
		}
	
	private var account : SavedAccount? = null
	
	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		App1.setActivityTheme(this, false)
		
		val intent = intent
		
		account = SavedAccount.loadAccount(this,intent.getLongExtra(EXTRA_ACCOUNT_DB_ID,-1L))

		initUI()
		
		if(savedInstanceState == null) {
			val sv = intent.getStringExtra(EXTRA_TEXT)
			val content_start = intent.getIntExtra(EXTRA_CONTENT_START, 0)
			val content_end = intent.getIntExtra(EXTRA_CONTENT_END, sv.length)
			etText.setText(sv)
			etText.setSelection(content_start, content_end)
		}
	}
	
	internal fun initUI() {
		setContentView(R.layout.act_text)
		
		Styler.fixHorizontalMargin(findViewById(R.id.svFooterBar))
		Styler.fixHorizontalMargin(findViewById(R.id.svContent))
		
		etText = findViewById(R.id.etText)
		
		findViewById<View>(R.id.btnCopy).setOnClickListener(this)
		findViewById<View>(R.id.btnSearch).setOnClickListener(this)
		findViewById<View>(R.id.btnSend).setOnClickListener(this)
		findViewById<View>(R.id.btnMuteWord).setOnClickListener(this)
		
		findViewById<View>(R.id.btnSearchMSP).setOnClickListener(this)
		findViewById<View>(R.id.btnSearchTS).setOnClickListener(this)

		val btnKeywordFilter :View = findViewById(R.id.btnKeywordFilter)
		btnKeywordFilter.setOnClickListener(this)
		btnKeywordFilter.isEnabled = account?.isPseudo == false
		
	}
	
	override fun onClick(v : View) {
		when(v.id) {
			
			R.id.btnCopy -> copy()
			
			R.id.btnSearch -> search()
			
			R.id.btnSend -> send()
			
			R.id.btnMuteWord -> muteWord()
			
			R.id.btnSearchMSP -> searchToot(RESULT_SEARCH_MSP)
			
			R.id.btnSearchTS -> searchToot(RESULT_SEARCH_TS)
			
			R.id.btnKeywordFilter -> keywordFilter()
		}
	}
	
	private fun copy() {
		try {
			// Gets a handle to the clipboard service.
			val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
				?: throw NotImplementedError("missing ClipboardManager system service")
			
			// Creates a new text clip to put on the clipboard
			val clip = ClipData.newPlainText("text", selection)
			
			// Set the clipboard's primary clip.
			
			clipboard.primaryClip = clip
			
			showToast(this, false, R.string.copy_complete)
		} catch(ex : Throwable) {
			log.trace(ex)
			showToast(this, ex, "copy failed.")
		}
		
	}
	
	private fun send() {
		try {
			
			val intent = Intent()
			intent.action = Intent.ACTION_SEND
			intent.type = "text/plain"
			intent.putExtra(Intent.EXTRA_TEXT, selection)
			startActivity(intent)
			
		} catch(ex : Throwable) {
			log.trace(ex)
			showToast(this, ex, "send failed.")
		}
		
	}
	
	private fun search() {
		val sv = selection
		if( sv.isEmpty() ) {
			showToast(this, false, "please select search keyword")
			return
		}
		try {
			val intent = Intent(Intent.ACTION_WEB_SEARCH)
			intent.putExtra(SearchManager.QUERY, sv)
			if(intent.resolveActivity(packageManager) != null) {
				startActivity(intent)
			}
		} catch(ex : Throwable) {
			log.trace(ex)
			showToast(this, ex, "search failed.")
		}
		
	}
	
	private fun searchToot(resultCode : Int) {
		val sv = selection
		if(sv.isEmpty() ) {
			showToast(this, false, "please select search keyword")
			return
		}
		try {
			val data = Intent()
			data.putExtra(Intent.EXTRA_TEXT, sv)
			setResult(resultCode, data)
			finish()
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
	}
	
	private fun muteWord() {
		try {
			MutedWord.save(selection)
			App1.getAppState(this).onMuteUpdated()
			showToast(this, false, R.string.word_was_muted)
		} catch(ex : Throwable) {
			log.trace(ex)
			showToast(this, ex, "muteWord failed.")
		}
		
	}
	
	private fun keywordFilter() {
		val account = this.account
		if( account?.isPseudo != false ) return
		ActKeywordFilter.open(this,account,initial_phrase = selection)
	}
	
}
