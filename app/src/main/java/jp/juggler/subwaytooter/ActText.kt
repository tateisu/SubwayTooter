package jp.juggler.subwaytooter

import android.app.SearchManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.util.CustomShare
import jp.juggler.subwaytooter.util.CustomShareTarget
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.table.MutedWord
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.TootTextEncoder
import jp.juggler.util.LogCategory
import jp.juggler.util.copyToClipboard
import jp.juggler.util.hideKeyboard
import jp.juggler.util.showToast

class ActText : AppCompatActivity(), View.OnClickListener {
	
	companion object {
		
		internal val log = LogCategory("ActText")
		
		internal const val RESULT_SEARCH_MSP = RESULT_FIRST_USER + 1
		internal const val RESULT_SEARCH_TS = RESULT_FIRST_USER + 2
		
		internal const val EXTRA_TEXT = "text"
		internal const val EXTRA_CONTENT_START = "content_start"
		internal const val EXTRA_CONTENT_END = "content_end"
		internal const val EXTRA_ACCOUNT_DB_ID = "account_db_id"
		
		fun open(
			activity : ActMain,
			request_code : Int,
			access_info : SavedAccount,
			status : TootStatus
		) {
			val intent = Intent(activity, ActText::class.java)
			intent.putExtra(EXTRA_ACCOUNT_DB_ID, access_info.db_id)
			TootTextEncoder.encodeStatus(intent, activity, access_info, status)
			activity.startActivityForResult(intent, request_code)
		}
		
		fun open(
			activity : ActMain,
			request_code : Int,
			access_info : SavedAccount,
			who : TootAccount
		) {
			val intent = Intent(activity, ActText::class.java)
			intent.putExtra(EXTRA_ACCOUNT_DB_ID, access_info.db_id)
			TootTextEncoder.encodeAccount(intent, activity, access_info, who)
			activity.startActivityForResult(intent, request_code)
		}
		
	}
	
	private lateinit var etText : EditText
	private lateinit var btnTranslate : Button
	
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
		App1.setActivityTheme(this)
		
		val intent = intent
		
		account = SavedAccount.loadAccount(this, intent.getLongExtra(EXTRA_ACCOUNT_DB_ID, - 1L))
		
		initUI()
		
		if(savedInstanceState == null) {
			val sv = intent.getStringExtra(EXTRA_TEXT) ?: ""
			val content_start = intent.getIntExtra(EXTRA_CONTENT_START, 0)
			val content_end = intent.getIntExtra(EXTRA_CONTENT_END, sv.length)
			etText.setText(sv)
			
			// Android 9 以降ではフォーカスがないとsetSelectionできない
			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
				etText.requestFocus()
				etText.hideKeyboard()
			}
			
			etText.setSelection(content_start, content_end)
		}
		
	}
	
	internal fun initUI() {
		setContentView(R.layout.act_text)
		App1.initEdgeToEdge(this)
		
		Styler.fixHorizontalMargin(findViewById(R.id.svFooterBar))
		Styler.fixHorizontalMargin(findViewById(R.id.svContent))
		
		etText = findViewById(R.id.etText)
		btnTranslate = findViewById(R.id.btnTranslate)
		
		btnTranslate.setOnClickListener(this)
		findViewById<View>(R.id.btnCopy).setOnClickListener(this)
		findViewById<View>(R.id.btnSearch).setOnClickListener(this)
		findViewById<View>(R.id.btnSend).setOnClickListener(this)
		findViewById<View>(R.id.btnMuteWord).setOnClickListener(this)
		findViewById<View>(R.id.btnSearchTS).setOnClickListener(this)
		
		val btnKeywordFilter : View = findViewById(R.id.btnKeywordFilter)
		btnKeywordFilter.setOnClickListener(this)
		btnKeywordFilter.isEnabled = account?.isPseudo == false
		
	}
	
	override fun onClick(v : View) {
		when(v.id) {
			
			R.id.btnCopy -> selection.copyToClipboard(this)
			
			R.id.btnSearch -> search()
			
			R.id.btnSend -> send()
			
			R.id.btnMuteWord -> muteWord()
			
			R.id.btnTranslate -> CustomShare.invoke(
				this,
				selection,
				CustomShareTarget.Translate
			)
			
			R.id.btnSearchTS -> searchToot(RESULT_SEARCH_TS)
			
			R.id.btnKeywordFilter -> keywordFilter()
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
		if(sv.isEmpty()) {
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
	
	private fun searchToot(@Suppress("SameParameterValue") resultCode : Int) {
		val sv = selection
		if(sv.isEmpty()) {
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
		if(account?.isPseudo != false) return
		ActKeywordFilter.open(this, account, initial_phrase = selection)
	}
	
}
