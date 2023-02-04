package jp.juggler.subwaytooter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import jp.juggler.subwaytooter.api.ApiPath
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.auth.AuthRepo
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.databinding.ActKeywordFilterBinding
import jp.juggler.subwaytooter.databinding.LvKeywordFilterBinding
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.daoAcctColor
import jp.juggler.subwaytooter.table.daoSavedAccount
import jp.juggler.util.backPressed
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.*
import jp.juggler.util.int
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.long
import jp.juggler.util.network.toPostRequestBuilder
import jp.juggler.util.network.toPut
import jp.juggler.util.network.toRequestBody
import jp.juggler.util.string
import jp.juggler.util.ui.setNavigationBack

class ActKeywordFilter : AppCompatActivity() {

    companion object {

        private val log = LogCategory("ActKeywordFilter")

        private const val EXTRA_ACCOUNT_DB_ID = "account_db_id"
        private const val EXTRA_FILTER_ID = "filter_id"
        private const val EXTRA_INITIAL_PHRASE = "initial_phrase"

        private const val STATE_EXPIRE_SPINNER = "expire_spinner"
        private const val STATE_EXPIRE_AT = "expire_at"
        private const val STATE_KEYWORDS = "keywords"
        private const val STATE_DELETE_IDS = "deleteIds"

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

    private val deleteIds = HashSet<String>()

    val authRepo by lazy {
        AuthRepo(this)
    }

    ///////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        backPressed { confirmBack() }
        super.onCreate(savedInstanceState)
        App1.setActivityTheme(this)
        initUI()

        launchAndShowError {

            // filter ID の有無はUIに影響するのでinitUIより先に初期化する
            filterId = EntityId.from(intent, EXTRA_FILTER_ID)

            val a = intent.long(EXTRA_ACCOUNT_DB_ID)
                ?.let { daoSavedAccount.loadAccount(it) }
            if (a == null) {
                finish()
                return@launchAndShowError
            }
            account = a


            showAccount()

            if (savedInstanceState == null) {
                if (filterId != null) {
                    startLoading()
                } else {
                    views.spExpire.setSelection(1)
                    val initialText = intent.string(EXTRA_INITIAL_PHRASE)?.trim() ?: ""
                    views.etTitle.setText(initialText)
                    addKeywordArea(TootFilterKeyword(keyword = initialText))
                }
            } else {

                savedInstanceState.getStringArrayList(STATE_DELETE_IDS)
                    ?.let { deleteIds.addAll(it) }

                savedInstanceState.getStringArrayList(STATE_KEYWORDS)
                    ?.mapNotNull { it?.decodeJsonObject() }
                    ?.forEach {
                        try {
                            addKeywordArea(TootFilterKeyword(it))
                        } catch (ex: Throwable) {
                            log.e(ex, "can't decode TootFilterKeyword")
                        }
                    }

                savedInstanceState.int(STATE_EXPIRE_SPINNER)
                    ?.let { views.spExpire.setSelection(it) }

                savedInstanceState.long(STATE_EXPIRE_AT)
                    ?.let { filterExpire = it }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!loading) {
            outState.putInt(STATE_EXPIRE_SPINNER, views.spExpire.selectedItemPosition)
            outState.putLong(STATE_EXPIRE_AT, filterExpire)

            outState.putStringArrayList(STATE_DELETE_IDS, ArrayList<String>(deleteIds))

            views.llKeywords.children
                .mapNotNull { (it.tag as? VhKeyword)?.encodeJson()?.toString() }
                .toList()
                .let { outState.putStringArrayList(STATE_KEYWORDS, ArrayList<String>(it)) }
        }
    }

    private fun initUI() {
        setContentView(views.root)
        setSupportActionBar(views.toolbar)
        setNavigationBack(views.toolbar)
        fixHorizontalMargin(views.llContent)

        this.density = resources.displayMetrics.density

        title = getString(
            when (filterId) {
                null -> R.string.keyword_filter_new
                else -> R.string.keyword_filter_edit
            }
        )

        views.btnSave.setOnClickListener { save() }
        views.btnAddKeyword.setOnClickListener {
            val ti = TootInstance.getCached(account)
            when {
                ti == null ->
                    showToast(true, "can't get server information")
                !ti.versionGE(TootInstance.VERSION_4_0_0) && views.llKeywords.childCount >= 1 ->
                    showToast(true, "before mastodon 4.0, allowed 1 keyword per 1 filter.")
                else -> addKeywordArea(TootFilterKeyword(keyword = ""))
            }
        }

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

    private fun confirmBack() {
        AlertDialog.Builder(this)
            .setMessage(R.string.keyword_filter_quit_waring)
            .setPositiveButton(R.string.ok) { _, _ -> finish() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAccount() {
        views.tvAccount.text = daoAcctColor.getNicknameWithColor(account.acct)
    }

    private fun startLoading() {
        loading = true
        launchMain {
            var resultFilter: TootFilter? = null
            runApiTask(account) { client ->

                // try v2
                var result = client.request("${ApiPath.PATH_FILTERS_V2}/$filterId")
                result?.jsonObject?.let {
                    try {
                        resultFilter = TootFilter(it)
                        return@runApiTask result
                    } catch (ex: Throwable) {
                        log.e(ex, "parse error.")
                    }
                }

                if (result?.response?.code == 404) {
                    // try v1
                    result = client.request("${ApiPath.PATH_FILTERS}/$filterId")
                    result?.jsonObject?.let {
                        try {
                            resultFilter = TootFilter(it)
                            return@runApiTask result
                        } catch (ex: Throwable) {
                            log.e(ex, "parse error.")
                        }
                    }
                }

                result
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

        views.rgAction.check(if (filter.hide) views.rbHide.id else views.rbWarn.id)

        if (filter.keywords.isEmpty()) {
            filter.keywords = listOf(TootFilterKeyword(keyword = ""))
        }

        filter.keywords.forEach { addKeywordArea(it) }

        views.etTitle.setText(
            filter.title.notEmpty() ?: filter.keywords.firstOrNull()?.keyword ?: ""
        )

        views.tvExpire.text = if (filter.time_expires_at == 0L) {
            getString(R.string.filter_expire_unlimited)
        } else {
            TootStatus.formatTime(this, filter.time_expires_at, false)
        }
    }

    private fun setContextChecked(filter: TootFilter, cb: CheckBox, fc: TootFilterContext) {
        cb.isChecked = filter.hasContext(fc)
    }

    private fun save() {
        if (loading) return

        val vhList = views.llKeywords.children.mapNotNull { it.tag as? VhKeyword }.toList()
        if (vhList.isEmpty() || vhList.any { it.keyword.isEmpty() }) {
            showToast(true, R.string.filter_keyword_empty)
            return
        }

        val title = views.etTitle.text.toString().trim()
        if (title.isEmpty()) {
            showToast(true, R.string.filter_title_empty)
            return
        }

        launchMain {

            var result = saveV2(vhList, title)
            if (result?.response?.code == 404) {
                result = saveV1(vhList)
            }
            result ?: return@launchMain // cancelled

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

    private fun filterParamBase() = buildJsonObject {
        fun JsonArray.putContextChecked(cb: CheckBox, fc: TootFilterContext) {
            if (cb.isChecked) add(fc.apiName)
        }

        put("context", JsonArray().apply {
            putContextChecked(views.cbContextHome, TootFilterContext.Home)
            putContextChecked(views.cbContextNotification, TootFilterContext.Notifications)
            putContextChecked(views.cbContextPublic, TootFilterContext.Public)
            putContextChecked(views.cbContextThread, TootFilterContext.Thread)
            putContextChecked(views.cbContextProfile, TootFilterContext.Account)
        })

        when (val seconds = expire_duration_list
            .elementAtOrNull(views.spExpire.selectedItemPosition)
            ?: -1
        ) {
            // dont change
            -1 -> Unit

            // unlimited
            0 -> when {
                // already unlimited. don't change.
                filterExpire <= 0L -> Unit
                // XXX: currently there is no way to remove expires from existing filter.
                else -> put("expires_in", Int.MAX_VALUE)
            }

            // set seconds
            else -> put("expires_in", seconds)
        }
    }

    private suspend fun saveV1(vhList: List<VhKeyword>): TootApiResult? {
        if (vhList.size != 1) return TootApiResult("V1 API allow only 1 keyword.")

        val params = filterParamBase().apply {
            put("irreversible", views.rgAction.checkedRadioButtonId == views.rbHide.id)
            val vh = vhList.first()
            put("phrase", vh.keyword)
            put("whole_word", vh.wholeWord)
        }

        return runApiTask(account) { client ->
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
        }
    }

    private suspend fun saveV2(vhList: List<VhKeyword>, title: String): TootApiResult? {
        val params = filterParamBase().apply {
            put("title", title)
            put(
                "filter_action",
                if (views.rbHide.isChecked) "hide" else "warn"
            )
            put("keywords_attributes", buildJsonArray {
                vhList.forEach { vh ->
                    add(buildJsonObject {
                        put("keyword", vh.keyword)
                        put("whole_word", vh.wholeWord)
                        vh.id?.let { put("id", it) }
                    })
                }
                deleteIds.forEach { id ->
                    add(buildJsonObject {
                        put("id", id)
                        put("_destroy", id)
                    })
                }
            })
        }
        return runApiTask(account) { client ->
            if (filterId == null) {
                client.request(
                    ApiPath.PATH_FILTERS_V2,
                    params.toPostRequestBuilder()
                )
            } else {
                client.request(
                    "${ApiPath.PATH_FILTERS_V2}/$filterId",
                    params.toRequestBody().toPut()
                )
            }
        }
    }

    private fun addKeywordArea(keyword: TootFilterKeyword) {
        views.llKeywords.addView(VhKeyword(fk = keyword).views.root)
    }

    private fun deleteKeywordArea(vh: VhKeyword) {
        views.llKeywords.children.find { it.tag == vh }
            ?.let { views.llKeywords.removeView(it) }
        vh.id?.let { deleteIds.add(it) }
    }

    private inner class VhKeyword(
        val fk: TootFilterKeyword,
        val views: LvKeywordFilterBinding = LvKeywordFilterBinding.inflate(layoutInflater),
    ) {
        init {
            views.root.tag = this
            views.etKeyword.setText(fk.keyword.trim())
            views.cbFilterWordMatch.isChecked = fk.whole_word

            views.btnDelete.setOnClickListener {
                deleteKeywordArea(this)
            }
        }

        // onSaveInstanceや保存時に呼ばれる
        fun encodeJson() =
            fk.encodeNewParam(newKeyword = keyword, newWholeWord = wholeWord)

        val keyword: String
            get() = views.etKeyword.text.toString().trim()

        val wholeWord: Boolean
            get() = views.cbFilterWordMatch.isChecked

        val id: String?
            get() = fk.id?.toString()?.notEmpty()
    }
}
