package jp.juggler.subwaytooter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.widget.*
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootTask
import jp.juggler.subwaytooter.api.TootTaskRunner
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.TootFilter
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.*

class ActKeywordFilter
	: AppCompatActivity(), View.OnClickListener {
	
	companion object {
		
		internal val log = LogCategory("ActKeywordFilter")
		
		internal const val EXTRA_ACCOUNT_DB_ID = "account_db_id"
		internal const val EXTRA_FILTER_ID = "filter_id"
		internal const val EXTRA_INITIAL_PHRASE = "initial_phrase"
		
		fun open(
			activity : Activity,
			ai : SavedAccount,
			filter_id : EntityId? = null,
			initial_phrase : String? = null
		) {
			val intent = Intent(activity, ActKeywordFilter::class.java)
			intent.putExtra(EXTRA_ACCOUNT_DB_ID, ai.db_id)
			filter_id?.putTo(intent, EXTRA_FILTER_ID)
			if(initial_phrase != null) intent.putExtra(EXTRA_INITIAL_PHRASE, initial_phrase)
			activity.startActivity(intent)
		}
		
		internal const val STATE_EXPIRE_SPINNER = "expire_spinner"
		internal const val STATE_EXPIRE_AT = "expire_at"
		
		private val expire_duration_list = arrayOf(
			- 1, // dont change
			0, // unlimited
			1800,
			3600,
			3600 * 6,
			3600 * 12,
			86400,
			86400 * 7
		)
		
	}
	
	internal lateinit var account : SavedAccount
	
	private lateinit var etPhrase : EditText
	private lateinit var cbContextHome : CheckBox
	private lateinit var cbContextNotification : CheckBox
	private lateinit var cbContextPublic : CheckBox
	private lateinit var cbContextThread : CheckBox
	private lateinit var cbFilterIrreversible : CheckBox
	private lateinit var cbFilterWordMatch : CheckBox
	private lateinit var tvExpire : TextView
	private lateinit var spExpire : Spinner
	
	private var loading = false
	private var density : Float = 1f
	private var filter_id : EntityId? = null
	private var filter_expire : Long = 0L
	
	///////////////////////////////////////////////////
	
	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		
		App1.setActivityTheme(this)
		
		val intent = this.intent
		
		// filter ID の有無はUIに影響するのでinitUIより先に初期化する
		this.filter_id = EntityId.from(intent, EXTRA_FILTER_ID)
		
		val a = SavedAccount.loadAccount(this, intent.getLongExtra(EXTRA_ACCOUNT_DB_ID, - 1L))
		if(a == null) {
			finish()
			return
		}
		this.account = a
		
		initUI()
		
		if(savedInstanceState == null) {
			if(filter_id != null) {
				startLoading()
			} else {
				spExpire.setSelection(1)
				etPhrase.setText(intent.getStringExtra(EXTRA_INITIAL_PHRASE) ?: "")
			}
		} else {
			val iv = savedInstanceState.getInt(STATE_EXPIRE_SPINNER, - 1)
			if(iv != - 1) {
				spExpire.setSelection(iv)
			}
			filter_expire = savedInstanceState.getLong(STATE_EXPIRE_AT, filter_expire)
		}
	}
	
	override fun onSaveInstanceState(outState : Bundle) {
		super.onSaveInstanceState(outState)
		if(! loading) {
			outState.putInt(STATE_EXPIRE_SPINNER, spExpire.selectedItemPosition)
			outState.putLong(STATE_EXPIRE_AT, filter_expire)
		}
	}
	
	private fun initUI() {
		title =
			getString(if(filter_id == null) R.string.keyword_filter_new else R.string.keyword_filter_edit)
		
		this.density = resources.displayMetrics.density
		setContentView(R.layout.act_keyword_filter)
		App1.initEdgeToEdge(this)
		
		Styler.fixHorizontalPadding(findViewById(R.id.svContent))
		
		etPhrase = findViewById(R.id.etPhrase)
		cbContextHome = findViewById(R.id.cbContextHome)
		cbContextNotification = findViewById(R.id.cbContextNotification)
		cbContextPublic = findViewById(R.id.cbContextPublic)
		cbContextThread = findViewById(R.id.cbContextThread)
		cbFilterIrreversible = findViewById(R.id.cbFilterIrreversible)
		cbFilterWordMatch = findViewById(R.id.cbFilterWordMatch)
		tvExpire = findViewById(R.id.tvExpire)
		spExpire = findViewById(R.id.spExpire)
		
		findViewById<View>(R.id.btnSave).setOnClickListener(this)
		
		val caption_list = arrayOf(
			getString(R.string.dont_change),
			getString(R.string.filter_expire_unlimited),
			getString(R.string.filter_expire_30min),
			getString(R.string.filter_expire_1hour),
			getString(R.string.filter_expire_6hour),
			getString(R.string.filter_expire_12hour),
			getString(R.string.filter_expire_1day),
			getString(R.string.filter_expire_1week)
		)
		val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, caption_list)
		adapter.setDropDownViewResource(R.layout.lv_spinner_dropdown)
		spExpire.adapter = adapter
	}
	
	private fun startLoading() {
		loading = true
		TootTaskRunner(this).run(account, object : TootTask {
			var filter : TootFilter? = null
			override fun background(client : TootApiClient) : TootApiResult? {
				val result = client.request("${Column.PATH_FILTERS}/${filter_id}")
				val jsonObject = result?.jsonObject
				if(jsonObject != null) {
					filter = TootFilter(jsonObject)
				}
				return result
			}
			
			override fun handleResult(result : TootApiResult?) {
				loading = false
				val filter = this.filter
				if(filter != null) {
					onLoadComplete(filter)
				} else {
					if(result != null) {
						showToast(this@ActKeywordFilter, true, result.error ?: "?")
					}
					finish()
				}
			}
		})
	}
	
	private fun onLoadComplete(filter : TootFilter) {
		loading = false
		
		filter_expire = filter.time_expires_at
		
		etPhrase.setText(filter.phrase)
		setContextChecked(filter, cbContextHome, TootFilter.CONTEXT_HOME)
		setContextChecked(filter, cbContextNotification, TootFilter.CONTEXT_NOTIFICATIONS)
		setContextChecked(filter, cbContextPublic, TootFilter.CONTEXT_PUBLIC)
		setContextChecked(filter, cbContextThread, TootFilter.CONTEXT_THREAD)
		
		cbFilterIrreversible.isChecked = filter.irreversible
		cbFilterWordMatch.isChecked = filter.whole_word
		
		tvExpire.text = if(filter.time_expires_at == 0L) {
			getString(R.string.filter_expire_unlimited)
		} else {
			TootStatus.formatTime(this, filter.time_expires_at, false)
		}
	}
	
	override fun onClick(v : View) {
		when(v.id) {
			R.id.btnSave -> save()
		}
	}
	
	private fun setContextChecked(filter : TootFilter, cb : CheckBox, bit : Int) {
		cb.isChecked = ((filter.context and bit) != 0)
	}
	
	private fun JsonArray.putContextChecked(cb : CheckBox, key : String) {
		if(cb.isChecked) add(key)
	}
	
	private fun save() {
		if(loading) return
		
		val params = jsonObject {
			
			put("phrase", etPhrase.text.toString())
			
			put("context", JsonArray().apply {
				putContextChecked(cbContextHome, "home")
				putContextChecked(cbContextNotification, "notifications")
				putContextChecked(cbContextPublic, "public")
				putContextChecked(cbContextThread, "thread")
			})
			
			put("irreversible", cbFilterIrreversible.isChecked)
			put("whole_word", cbFilterWordMatch.isChecked)
			
			var seconds = - 1
			
			val i = spExpire.selectedItemPosition
			if(i >= 0 && i < expire_duration_list.size) {
				seconds = expire_duration_list[i]
			}
			
			when(seconds) {
				
				// dont change
				- 1 -> {
				}
				
				// unlimited
				0 -> when {
					// already unlimited. don't change.
					filter_expire <= 0L -> {
					}
					// FIXME: currently there is no way to remove expires from existing filter.
					else -> put("expires_in", Int.MAX_VALUE)
				}
				
				// set seconds
				else -> put("expires_in", seconds)
			}
			
		}
		
		TootTaskRunner(this).run(account, object : TootTask {
			
			override fun background(client : TootApiClient) = if(filter_id == null) {
				client.request(
					Column.PATH_FILTERS,
					params.toPostRequestBuilder()
				)
			} else {
				client.request(
					"${Column.PATH_FILTERS}/$filter_id",
					params.toRequestBody().toPut()
				)
			}
			
			override fun handleResult(result : TootApiResult?) {
				result ?: return
				val error = result.error
				if(error != null) {
					showToast(this@ActKeywordFilter, true, result.error)
				} else {
					val app_state = App1.prepare(applicationContext)
					for(column in app_state.column_list) {
						if(column.type == ColumnType.KEYWORD_FILTER && column.access_info == account) {
							column.filter_reload_required = true
						}
					}
					finish()
				}
			}
		})
	}
	
}

