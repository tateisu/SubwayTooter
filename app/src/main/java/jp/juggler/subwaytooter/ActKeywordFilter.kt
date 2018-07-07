package jp.juggler.subwaytooter

import android.app.Activity
import android.content.Intent
import android.os.*
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.*
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.TootFilter
import jp.juggler.subwaytooter.api.entity.TootStatus

import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.*
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject

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
			filter_id : Long = - 1L,
			initial_phrase : String? = null
		) {
			val intent = Intent(activity, ActKeywordFilter::class.java)
			intent.putExtra(EXTRA_ACCOUNT_DB_ID, ai.db_id)
			intent.putExtra(EXTRA_FILTER_ID, filter_id)
			if(initial_phrase != null) intent.putExtra(EXTRA_INITIAL_PHRASE, initial_phrase)
			activity.startActivity(intent)
		}
		
		internal const val STATE_EXPIRE_SPINNER = "expire_spinner"
		
		private val expire_duration_list = arrayOf(
			-1, // dont change
			0, // unlimited
			1800,
			3600,
			3600*6,
			3600*12,
			86400,
			86400*7
		)
		
	}
	
	internal lateinit var account : SavedAccount
	
	private lateinit var etPhrase : EditText
	private lateinit var cbContextHome : CheckBox
	private lateinit var cbContextNotification : CheckBox
	private lateinit var cbContextPublic : CheckBox
	private lateinit var cbContextThread : CheckBox
	private lateinit var cbFilterIrreversible : CheckBox
	private lateinit var tvExpire : TextView
	private lateinit var spExpire : Spinner
	
	private var loading = false
	private var density : Float = 1f
	private var filter_id : Long = - 1L
	
	///////////////////////////////////////////////////
	
	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		
		App1.setActivityTheme(this, false)
		
		val intent = this.intent
		
		// filter ID の有無はUIに影響するのでinitUIより先に呼び出す
		this.filter_id = intent.getLongExtra(EXTRA_FILTER_ID, - 1L)
		
		initUI()
		
		val a = SavedAccount.loadAccount(this, intent.getLongExtra(EXTRA_ACCOUNT_DB_ID, - 1L))
		if(a == null) {
			finish()
			return
		}
		this.account = a
		
		if(savedInstanceState == null) {
			if(filter_id != - 1L) {
				startLoading()
			} else {
				val sv = intent.getStringExtra(EXTRA_INITIAL_PHRASE)
				if(sv?.isNotEmpty() == true) {
					etPhrase.setText(sv)
				}
				tvExpire.setText(R.string.filter_expire_unlimited)
			}
		} else {
			val iv = savedInstanceState.getInt(STATE_EXPIRE_SPINNER, - 1)
			if(iv != - 1) {
				spExpire.setSelection(iv)
			}
		}
	}
	
	override fun onSaveInstanceState(outState : Bundle) {
		super.onSaveInstanceState(outState)
		if(! loading) {
			outState.putInt(STATE_EXPIRE_SPINNER, spExpire.selectedItemPosition)
		}
	}
	
	private fun initUI() {
		title = getString(if(filter_id==-1L) R.string.keyword_filter_new else R.string.keyword_filter_edit)
		
		this.density = resources.displayMetrics.density
		setContentView(R.layout.act_keyword_filter)
		
		Styler.fixHorizontalPadding(findViewById(R.id.svContent))
		
		etPhrase = findViewById(R.id.etPhrase)
		cbContextHome = findViewById(R.id.cbContextHome)
		cbContextNotification = findViewById(R.id.cbContextNotification)
		cbContextPublic = findViewById(R.id.cbContextPublic)
		cbContextThread = findViewById(R.id.cbContextThread)
		cbFilterIrreversible = findViewById(R.id.cbFilterIrreversible)
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

	private fun onLoadComplete(filter:TootFilter){
		loading = false
		
		etPhrase.setText( filter.phrase)
		setContextChecked(filter,cbContextHome,TootFilter.CONTEXT_HOME)
		setContextChecked(filter,cbContextNotification,TootFilter.CONTEXT_NOTIFICATIONS)
		setContextChecked(filter,cbContextPublic,TootFilter.CONTEXT_PUBLIC)
		setContextChecked(filter,cbContextThread,TootFilter.CONTEXT_THREAD)

		cbFilterIrreversible.isChecked = filter.irreversible
		
		tvExpire.text = if( filter.time_expires_at == 0L ){
			getString(R.string.filter_expire_unlimited)
		}else{
			TootStatus.formatTime(this,filter.time_expires_at,false)
		}
	}
	
	override fun onClick(v : View) {
		when(v.id) {
			R.id.btnSave -> save()
		}
	}
	
	private fun setContextChecked(filter:TootFilter,cb:CheckBox,bit:Int){
		cb.isChecked = ( (filter.context and bit) != 0)
	}
	private fun JSONArray.putContextChecked(cb:CheckBox,key:String){
		if( cb.isChecked ) this.put(key)
	}

	private fun save(){
		if(loading) return
		
		val params = JSONObject().apply {

			put("phrase",etPhrase.text.toString())

			put( "context",JSONArray().apply {
				putContextChecked(cbContextHome,"home")
				putContextChecked(cbContextNotification,"notifications")
				putContextChecked(cbContextPublic,"public")
				putContextChecked(cbContextThread,"thread")
			})

			put( "irreversible", cbFilterIrreversible.isChecked)
			
			var seconds = -1
			val i = spExpire.selectedItemPosition
			if( i >= 0 && i < expire_duration_list.size) {
				seconds = expire_duration_list[i]
			}
			if( seconds == -1 && filter_id == - 1L) {
				seconds = 0
			}
			when(seconds){
				-1 ->{ // dont change
					put("expires_in","")
				}
				0 ->{ // set unlimited
					if( filter_id == - 1L) {
						// set blank to dont set expire
						put("expires_in","")
					}else {
						// FIXME: currently no way to remove expire from existing filter
						put("expires_in",(Int.MAX_VALUE shr 5) )
					}
				}
				else->{
					put("expires_in",seconds)
				}
			}
		}.toString()
		
		TootTaskRunner(this).run(account, object : TootTask {
			override fun background(client : TootApiClient) : TootApiResult? {
				
				val result = if( filter_id == -1L){
					val builder = Request.Builder()
						.post(RequestBody.create(TootApiClient.MEDIA_TYPE_JSON,params))
					client.request(Column.PATH_FILTERS,builder)
				}else{
					val builder = Request.Builder()
						.put(RequestBody.create(TootApiClient.MEDIA_TYPE_JSON,params))
					client.request("${Column.PATH_FILTERS}/${filter_id}",builder)
				}
				return result
			}
			
			override fun handleResult(result : TootApiResult?) {
				result ?: return
				val error = result.error
				if( error!=null){
					showToast(this@ActKeywordFilter,true,result.error)
				}else{
					val app_state = App1.prepare(applicationContext)
					for( column in app_state.column_list ){
						if( column.access_info.acct == account.acct
							&& column.column_type == Column.TYPE_KEYWORD_FILTER
						){
							column.filter_reload_required = true
						}
					}
					finish()
				}
			}
		})
	}
	
}

