package jp.juggler.subwaytooter.dialog

import android.app.Dialog
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.api.runApiTask2
import jp.juggler.subwaytooter.databinding.DlgAccountAddBinding
import jp.juggler.subwaytooter.databinding.LvAuthTypeBinding
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.data.notBlank
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.*
import jp.juggler.util.ui.*
import org.jetbrains.anko.textColor
import org.jetbrains.anko.textResource
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.IDN
import java.util.*

class LoginForm(
    val activity: AppCompatActivity,
    val onClickOk: (
        dialog: Dialog,
        apiHost: Host,
        serverInfo: TootInstance?,
        action: Action,
    ) -> Unit,
) {
    companion object {
        private val log = LogCategory("LoginForm")

        @Suppress("RegExpSimplifiable")
        val reBadChars = """([^\p{L}\p{N}A-Za-z0-9:;._-]+)""".toRegex()

        fun AppCompatActivity.showLoginForm(
            onClickOk: (
                dialog: Dialog,
                apiHost: Host,
                serverInfo: TootInstance?,
                action: Action,
            ) -> Unit,
        ) = LoginForm(this, onClickOk)
    }

    private class StringArray : ArrayList<String>()

    enum class Action(
        val pos: Int,
        @StringRes val idName: Int,
        @StringRes val idDesc: Int,
    ) {
        Login(0, R.string.existing_account, R.string.existing_account_desc),
        Pseudo(1, R.string.pseudo_account, R.string.pseudo_account_desc),
        Create(2, R.string.create_account, R.string.create_account_desc),
        Token(3, R.string.input_access_token, R.string.input_access_token_desc),
    }

    val views = DlgAccountAddBinding.inflate(activity.layoutInflater)
    val dialog = Dialog(activity)

    private var targetServer: Host? = null
    private var targetServerInfo: TootInstance? = null

    init {
        for (a in Action.values()) {
            val subViews =
                LvAuthTypeBinding.inflate(activity.layoutInflater, views.llPageAuthType, true)
            subViews.btnAuthType.textResource = a.idName
            subViews.tvDesc.textResource = a.idDesc
            subViews.btnAuthType.setOnClickListener { onAuthTypeSelect(a) }
        }
        views.btnPrev.setOnClickListener { showPage(0) }
        views.btnNext.setOnClickListener { nextPage() }
        views.btnCancel.setOnClickListener { dialog.cancel() }
        views.etInstance.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                nextPage()
                return@OnEditorActionListener true
            }
            false
        })
        views.etInstance.addTextChangedListener { validateAndShow() }

        showPage(0)
        initServerNameList()
        validateAndShow()

        dialog.setContentView(views.root)
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }

    private fun initServerNameList() {
        val instanceList = HashSet<String>().apply {
            try {
                activity.resources.openRawResource(R.raw.server_list).use { inStream ->
                    val br = BufferedReader(InputStreamReader(inStream, "UTF-8"))
                    while (true) {
                        val s: String =
                            br.readLine()?.trim { it <= ' ' }?.lowercase() ?: break
                        if (s.isEmpty()) continue
                        add(s)
                        add(IDN.toASCII(s, IDN.ALLOW_UNASSIGNED))
                        add(IDN.toUnicode(s, IDN.ALLOW_UNASSIGNED))
                    }
                }
            } catch (ex: Throwable) {
                log.e(ex, "can't load server list.")
            }
        }.toList().sorted()

        val adapter = object : ArrayAdapter<String>(
            activity, R.layout.lv_spinner_dropdown, ArrayList()
        ) {
            val nameFilter: Filter = object : Filter() {
                override fun convertResultToString(value: Any) =
                    value as String

                override fun performFiltering(constraint: CharSequence?) =
                    FilterResults().also { result ->
                        if (constraint?.isNotEmpty() == true) {
                            val key = constraint.toString().lowercase()
                            // suggestions リストは毎回生成する必要がある。publishResultsと同時にアクセスされる場合がある
                            val suggestions = StringArray()
                            for (s in instanceList) {
                                if (s.contains(key)) {
                                    suggestions.add(s)
                                    if (suggestions.size >= 20) break
                                }
                            }
                            result.values = suggestions
                            result.count = suggestions.size
                        }
                    }

                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    clear()
                    val values = results?.values
                    if (values is StringArray) {
                        for (s in values) {
                            add(s)
                        }
                    }
                    notifyDataSetChanged()
                }
            }

            override fun getFilter(): Filter = nameFilter
        }
        adapter.setDropDownViewResource(R.layout.lv_spinner_dropdown)
        views.etInstance.setAdapter<ArrayAdapter<String>>(adapter)
    }

    // return validated name. else null
    private fun validateAndShow(): String? {
        fun showError(s: String) {
            views.btnNext.isEnabledAlpha = false
            views.tvError.visible().text = s
        }

        val s = views.etInstance.text.toString().trim()
        if (s.isEmpty()) {
            showError(activity.getString(R.string.instance_not_specified))
            return null
        }

        // コピペミスに合わせたガイド
        arrayOf(
            "http://",
            "https://",
        ).forEach {
            if (s.contains(it)) {
                showError(activity.getString(R.string.server_host_name_cant_contains_it, it))
                return null
            }
        }
        if (s.contains("/") || s.contains("@")) {
            showError(activity.getString(R.string.instance_not_need_slash))
            return null
        }

        reBadChars.findAll(s).joinToString("") { it.value }.notEmpty()?.let {
            showError(activity.getString(R.string.server_host_name_cant_contains_it, it))
            return null
        }
        views.tvError.invisible()
        views.btnNext.isEnabledAlpha = true
        return s
    }

    private fun showPage(n: Int) {
        views.etInstance.dismissDropDown()
        views.etInstance.hideKeyboard()
        views.llPageServerHost.vg(n == 0)
        views.llPageAuthType.vg(n == 1)
        val canBack = n != 0
        views.btnPrev.vg(canBack)
        val canNext = n == 0
        views.btnNext.visibleOrInvisible(canNext)
        views.tvHeader.textResource = when (n) {
            0 -> R.string.server_host_name
            else -> R.string.authentication_select
        }
    }

    private fun nextPage() {
        activity.run {
            launchAndShowError {
                val hostname = validateAndShow() ?: return@launchAndShowError
                val host = Host.parse(hostname)
                var error: String? = null
                val tootInstance = try {
                    runApiTask2(host) {
                        TootInstance.getExOrThrow(it, forceUpdate =true)
                    }
                } catch (ex: Throwable) {
                    error = ex.message
                    null
                }
                if (isDestroyed || isFinishing) return@launchAndShowError
                targetServer = host
                targetServerInfo = tootInstance
                views.tvServerHost.text = tootInstance?.apDomain?.pretty ?: host.pretty
                views.tvServerDesc.run {
                    when (tootInstance) {
                        null -> {
                            textColor = attrColor(R.attr.colorRegexFilterError)
                            text = error
                        }
                        else -> {
                            textColor = attrColor(R.attr.colorTextContent)
                            text = (tootInstance.short_description.notBlank()
                                ?: tootInstance.description.notBlank()
                                ?: "(empty server description)"
                                    ).let {
                                    DecodeOptions(
                                        applicationContext,
                                        LinkHelper.create(tootInstance),
                                        forceHtml = true,
                                        short = true,
                                    ).decodeHTML(it)
                                }.replace("""\n[\s\n]+""".toRegex(), "\n")
                                .trim()
                        }
                    }
                }

                showPage(1)
            }
        }
    }

    private fun onAuthTypeSelect(action: Action) {
        targetServer?.let { onClickOk(dialog, it, targetServerInfo, action) }
    }
}
