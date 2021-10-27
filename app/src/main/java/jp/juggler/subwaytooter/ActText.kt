package jp.juggler.subwaytooter

import android.app.SearchManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.dialog.pickAccount
import jp.juggler.subwaytooter.table.MutedWord
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.CustomShare
import jp.juggler.subwaytooter.util.CustomShareTarget
import jp.juggler.subwaytooter.util.TootTextEncoder
import jp.juggler.util.*

class ActText : AppCompatActivity() {

    companion object {

        internal val log = LogCategory("ActText")

        internal const val RESULT_SEARCH_MSP = RESULT_FIRST_USER + 1
        internal const val RESULT_SEARCH_TS = RESULT_FIRST_USER + 2
        internal const val RESULT_SEARCH_NOTESTOCK = RESULT_FIRST_USER + 3

        internal const val EXTRA_TEXT = "text"
        internal const val EXTRA_CONTENT_START = "content_start"
        internal const val EXTRA_CONTENT_END = "content_end"
        internal const val EXTRA_ACCOUNT_DB_ID = "account_db_id"

        fun createIntent(
            activity: ActMain,
            accessInfo: SavedAccount,
            status: TootStatus,
        ) = Intent(activity, ActText::class.java).apply {
            putExtra(EXTRA_ACCOUNT_DB_ID, accessInfo.db_id)
            TootTextEncoder.encodeStatus(this, activity, accessInfo, status)
        }

        fun createIntent(
            activity: ActMain,
            accessInfo: SavedAccount,
            who: TootAccount,
        ) = Intent(activity, ActText::class.java).apply {
            putExtra(EXTRA_ACCOUNT_DB_ID, accessInfo.db_id)
            TootTextEncoder.encodeAccount(this, activity, accessInfo, who)
        }
    }

    private var account: SavedAccount? = null
    private lateinit var etText: EditText

    private val selection: String
        get() {
            val s = etText.selectionStart
            val e = etText.selectionEnd
            val text = etText.text.toString()
            return if (s == e) {
                text
            } else {
                text.substring(s, e)
            }
        }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.btnCopy -> selection.copyToClipboard(this)

            R.id.btnSearch -> search()

            R.id.btnSend -> send()

            R.id.btnMuteWord -> muteWord()

            R.id.btnTranslate -> CustomShare.invokeText(
                CustomShareTarget.Translate,
                this,
                selection,
            )

            R.id.btnSearchTS -> searchToot(RESULT_SEARCH_TS)

            R.id.btnSearchNotestock -> searchToot(RESULT_SEARCH_NOTESTOCK)

            R.id.btnKeywordFilter -> keywordFilter()

            R.id.btnHighlight -> highlight()

            // If we got here, the user's action was not recognized.
            // Invoke the superclass to handle it.
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.act_text, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App1.setActivityTheme(this, noActionBar = true)

        val intent = intent

        account = SavedAccount.loadAccount(this, intent.getLongExtra(EXTRA_ACCOUNT_DB_ID, -1L))

        initUI()

        if (savedInstanceState == null) {
            val sv = intent.getStringExtra(EXTRA_TEXT) ?: ""
            val contentStart = intent.getIntExtra(EXTRA_CONTENT_START, 0)
            val contentEnd = intent.getIntExtra(EXTRA_CONTENT_END, sv.length)
            etText.setText(sv)

            // Android 9 以降ではフォーカスがないとsetSelectionできない
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                etText.requestFocus()
                etText.hideKeyboard()
            }

            etText.setSelection(contentStart, contentEnd)
        }
    }

    internal fun initUI() {
        setContentView(R.layout.act_text)
        etText = findViewById(R.id.etText)
        App1.initEdgeToEdge(this)
        Styler.fixHorizontalMargin(etText)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(false)
            setHomeButtonEnabled(false)
        }
    }

    private fun send() {
        selection.trim().notEmpty()?.let {
            try {

                val intent = Intent()
                intent.action = Intent.ACTION_SEND
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TEXT, it)
                startActivity(intent)
            } catch (ex: Throwable) {
                log.trace(ex)
                showToast(ex, "send failed.")
            }
        }
    }

    private fun search() {
        selection.trim().notEmpty()?.also {
            try {
                val intent = Intent(Intent.ACTION_WEB_SEARCH)
                intent.putExtra(SearchManager.QUERY, it)
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                }
            } catch (ex: Throwable) {
                log.trace(ex)
                showToast(ex, "search failed.")
            }
        }
    }

    private fun searchToot(@Suppress("SameParameterValue") resultCode: Int) {
        selection.trim().notEmpty()?.let {
            try {
                val data = Intent()
                data.putExtra(Intent.EXTRA_TEXT, it)
                setResult(resultCode, data)
                finish()
            } catch (ex: Throwable) {
                log.trace(ex)
            }
        }
    }

    private fun muteWord() {
        selection.trim().notEmpty()?.let {
            try {
                MutedWord.save(it)
                App1.getAppState(this).onMuteUpdated()
                showToast(false, R.string.word_was_muted)
            } catch (ex: Throwable) {
                log.trace(ex)
                showToast(ex, "muteWord failed.")
            }
        }
    }

    private fun keywordFilter() {
        selection.trim().notEmpty()?.let { text ->
            val account = this.account
            if (account?.isPseudo == false && account.isMastodon) {
                ActKeywordFilter.open(this, account, initialPhrase = text)
            } else {
                launchMain {
                    pickAccount(
                        bAllowPseudo = false,
                        bAllowMisskey = false,
                        bAllowMastodon = true,
                        bAuto = false,
                    )?.let {
                        ActKeywordFilter.open(this@ActText, it, initialPhrase = text)
                    }
                }
            }
        }
    }

    private fun highlight() {
        selection.trim().notEmpty()?.let {
            startActivity(ActHighlightWordEdit.createIntent(this, it))
        }
    }
}
