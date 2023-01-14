package jp.juggler.subwaytooter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.api.ApiPath
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.TootFilter
import jp.juggler.subwaytooter.api.entity.TootFilterContext
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.databinding.ActKeywordFilterBinding
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.JsonArray
import jp.juggler.util.data.buildJsonObject
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.network.toPostRequestBuilder
import jp.juggler.util.network.toPut
import jp.juggler.util.network.toRequestBody

class ActKeywordFilter
    : AppCompatActivity(), View.OnClickListener {

    companion object {

        internal val log = LogCategory("ActKeywordFilter")

        internal const val EXTRA_ACCOUNT_DB_ID = "account_db_id"
        internal const val EXTRA_FILTER_ID = "filter_id"
        internal const val EXTRA_INITIAL_PHRASE = "initial_phrase"

        fun open(
            activity: Activity,
            ai: SavedAccount,
            filterId: EntityId? = null,
            initialPhrase: String? = null,
        ) {
            val intent = Intent(activity, ActKeywordFilter::class.java)
            intent.putExtra(EXTRA_ACCOUNT_DB_ID, ai.db_id)
            filterId?.putTo(intent, EXTRA_FILTER_ID)
            initialPhrase?.notEmpty()?.let { intent.putExtra(EXTRA_INITIAL_PHRASE, it) }
            activity.startActivity(intent)
        }

        internal const val STATE_EXPIRE_SPINNER = "expire_spinner"
        internal const val STATE_EXPIRE_AT = "expire_at"

        private val expire_duration_list = intArrayOf(
            -1, // dont change
            0, // unlimited
            1800,
            3600,
            3600 * 6,
            3600 * 12,
            86400,
            86400 * 7
        )
    }

    private lateinit var account: SavedAccount

    private val views by lazy {
        ActKeywordFilterBinding.inflate(layoutInflater)
    }

//    private lateinit var tvAccount: TextView
//    private lateinit var etPhrase: EditText
//    private lateinit var cbContextHome: CheckBox
//    private lateinit var cbContextNotification: CheckBox
//    private lateinit var cbContextPublic: CheckBox
//    private lateinit var cbContextThread: CheckBox
//    private lateinit var cbContextProfile: CheckBox
//
//    private lateinit var cbFilterIrreversible: CheckBox
//    private lateinit var cbFilterWordMatch: CheckBox
//    private lateinit var tvExpire: TextView
//    private lateinit var spExpire: Spinner

    private var loading = false
    private var density: Float = 1f
    private var filterId: EntityId? = null
    private var filterExpire: Long = 0L

    ///////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App1.setActivityTheme(this)

        val intent = this.intent

        // filter ID の有無はUIに影響するのでinitUIより先に初期化する
        this.filterId = EntityId.from(intent, EXTRA_FILTER_ID)

        val a = SavedAccount.loadAccount(this, intent.getLongExtra(EXTRA_ACCOUNT_DB_ID, -1L))
        if (a == null) {
            finish()
            return
        }
        this.account = a

        initUI()

        showAccount()

        if (savedInstanceState == null) {
            if (filterId != null) {
                startLoading()
            } else {
                views. spExpire.setSelection(1)
                views.  etPhrase.setText(intent.getStringExtra(EXTRA_INITIAL_PHRASE) ?: "")
            }
        } else {
            val iv = savedInstanceState.getInt(STATE_EXPIRE_SPINNER, -1)
            if (iv != -1) {
                views.  spExpire.setSelection(iv)
            }
            filterExpire = savedInstanceState.getLong(STATE_EXPIRE_AT, filterExpire)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!loading) {
            outState.putInt(STATE_EXPIRE_SPINNER, views.spExpire.selectedItemPosition)
            outState.putLong(STATE_EXPIRE_AT, filterExpire)
        }
    }

    private fun initUI() {
        title = getString(
            when (filterId) {
                null -> R.string.keyword_filter_new
                else -> R.string.keyword_filter_edit
            }
        )

        this.density = resources.displayMetrics.density
        setContentView(views.root)
        App1.initEdgeToEdge(this)

        fixHorizontalPadding(findViewById(R.id.svContent))

//        tvAccount = findViewById(R.id.tvAccount)
//        etPhrase = findViewById(R.id.etPhrase)
//        cbContextHome = findViewById(R.id.cbContextHome)
//        cbContextNotification = findViewById(R.id.cbContextNotification)
//        cbContextPublic = findViewById(R.id.cbContextPublic)
//        cbContextThread = findViewById(R.id.cbContextThread)
//        cbContextProfile = findViewById(R.id.cbContextProfile)
//        cbFilterIrreversible = findViewById(R.id.cbFilterIrreversible)
//        cbFilterWordMatch = findViewById(R.id.cbFilterWordMatch)
//        tvExpire = findViewById(R.id.tvExpire)
//        spExpire = findViewById(R.id.spExpire)

        views.btnSave.setOnClickListener(this)

        val captionList = arrayOf(
            getString(R.string.dont_change),
            getString(R.string.filter_expire_unlimited),
            getString(R.string.filter_expire_30min),
            getString(R.string.filter_expire_1hour),
            getString(R.string.filter_expire_6hour),
            getString(R.string.filter_expire_12hour),
            getString(R.string.filter_expire_1day),
            getString(R.string.filter_expire_1week)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, captionList)
        adapter.setDropDownViewResource(R.layout.lv_spinner_dropdown)
        views.spExpire.adapter = adapter
    }

    private fun showAccount() {
        views.tvAccount.text = AcctColor.getNicknameWithColor(account.acct)
    }

    private fun startLoading() {
        loading = true

        launchMain {
            var resultFilter: TootFilter? = null
            runApiTask(account) { client ->
                client.request("${ApiPath.PATH_FILTERS}/$filterId")
                    ?.also { result ->
                        result.jsonObject?.let {
                            resultFilter = TootFilter(it)
                        }
                    }
            }?.let { result ->
                loading = false
                when (val filter = resultFilter) {
                    null -> {
                        showToast(true, result.error ?: "?")
                        finish()
                    }
                    else -> onLoadComplete(filter)
                }
            }
            // キャンセル時はloadingはtrueのまま
        }
    }

    private fun onLoadComplete(filter: TootFilter) {
        loading = false

        filterExpire = filter.time_expires_at

        setContextChecked(filter, views.cbContextHome, TootFilterContext.Home)
        setContextChecked(filter, views.cbContextNotification, TootFilterContext.Notifications)
        setContextChecked(filter, views.cbContextPublic, TootFilterContext.Public)
        setContextChecked(filter, views.cbContextThread, TootFilterContext.Thread)
        setContextChecked(filter, views.cbContextProfile, TootFilterContext.Account)

        views.etPhrase.setText(filter.phrase)
        views.cbFilterIrreversible.isChecked = filter.irreversible
        views.cbFilterWordMatch.isChecked = filter.whole_word

        views.tvExpire.text = if (filter.time_expires_at == 0L) {
            getString(R.string.filter_expire_unlimited)
        } else {
            TootStatus.formatTime(this, filter.time_expires_at, false)
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btnSave -> save()
        }
    }

    private fun setContextChecked(filter: TootFilter, cb: CheckBox, fc: TootFilterContext) {
        cb.isChecked = filter.hasContext(fc)
    }

    private fun JsonArray.putContextChecked(cb: CheckBox, fc:TootFilterContext) {
        if (cb.isChecked) add(fc.apiName)
    }

    private fun save() {
        if (loading) return

        val params = buildJsonObject {

            put("context", JsonArray().apply {
                putContextChecked(views.cbContextHome, TootFilterContext.Home)
                putContextChecked(views.cbContextNotification, TootFilterContext.Notifications)
                putContextChecked(views.cbContextPublic,  TootFilterContext.Public)
                putContextChecked(views.cbContextThread,  TootFilterContext.Thread)
                putContextChecked(views.cbContextProfile,  TootFilterContext.Account)
            })

            put("phrase", views.etPhrase.text.toString())

            put("irreversible",views. cbFilterIrreversible.isChecked)
            put("whole_word", views.cbFilterWordMatch.isChecked)

            var seconds = -1

            val i = views.spExpire.selectedItemPosition
            if (i >= 0 && i < expire_duration_list.size) {
                seconds = expire_duration_list[i]
            }

            when (seconds) {

                // dont change
                -1 -> {
                }

                // unlimited
                0 -> when {
                    // already unlimited. don't change.
                    filterExpire <= 0L -> {
                    }
                    // XXX: currently there is no way to remove expires from existing filter.
                    else -> put("expires_in", Int.MAX_VALUE)
                }

                // set seconds
                else -> put("expires_in", seconds)
            }
        }

        launchMain {
            runApiTask(account) { client ->
                if (filterId == null) {
                    client.request(
                        ApiPath.PATH_FILTERS,
                        params.toPostRequestBuilder()
                    )
                } else {
                    client.request(
                        "${ApiPath.PATH_FILTERS}/$filterId",
                        params.toRequestBody().toPut()
                    )
                }
            }?.let { result ->
                val error = result.error
                if (error != null) {
                    showToast(true, result.error)
                } else {
                    val appState = App1.prepare(applicationContext, "ActKeywordFilter.save()")
                    for (column in appState.columnList) {
                        if (column.type == ColumnType.KEYWORD_FILTER && column.accessInfo == account) {
                            column.filterReloadRequired = true
                        }
                    }
                    finish()
                }
            }
        }
    }
}
