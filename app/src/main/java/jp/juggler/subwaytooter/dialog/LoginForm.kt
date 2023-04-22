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
import jp.juggler.subwaytooter.api.getApiHostFromWebFinger
import jp.juggler.subwaytooter.api.runApiTask2
import jp.juggler.subwaytooter.databinding.DlgAccountAddBinding
import jp.juggler.subwaytooter.databinding.LvAuthTypeBinding
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.notBlank
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.*
import jp.juggler.util.ui.*
import kotlinx.coroutines.withContext
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

    enum class Action(
        @StringRes val idName: Int,
        @StringRes val idDesc: Int,
    ) {
        Login(R.string.existing_account, R.string.existing_account_desc),
        Pseudo(R.string.pseudo_account, R.string.pseudo_account_desc),
    //    Create(2, R.string.create_account, R.string.create_account_desc),
        Token(R.string.input_access_token, R.string.input_access_token_desc),
    }

    // 実行時キャストのためGenericsを含まない型を定義する
    private class StringArrayList : ArrayList<String>()

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

        validateAndShow()

        dialog.setContentView(views.root)
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        dialog.show()

        initServerNameList()
    }

    private fun initServerNameList() {
        val progress = ProgressDialogEx(activity)
        progress.setMessageEx(activity.getString(R.string.autocomplete_list_loading))
        progress.show()
        launchMain {
            try {
                val instanceList = HashSet<String>().apply {
                    try {
                        withContext(AppDispatchers.IO) {
                            activity.resources.openRawResource(R.raw.server_list).use { inStream ->
                                val br = BufferedReader(InputStreamReader(inStream, "UTF-8"))
                                while (true) {
                                    (br.readLine() ?: break)
                                        .trim { it <= ' ' }
                                        .notEmpty()
                                        ?.lowercase()
                                        ?.let {
                                            add(it)
                                            add(IDN.toASCII(it, IDN.ALLOW_UNASSIGNED))
                                            add(IDN.toUnicode(it, IDN.ALLOW_UNASSIGNED))
                                        }
                                }
                            }
                        }
                    } catch (ex: Throwable) {
                        log.e(ex, "can't load server list.")
                    }
                }.toList().sorted()

                val adapter = object : ArrayAdapter<String>(
                    activity, R.layout.lv_spinner_dropdown, ArrayList()
                ) {
                    override fun getFilter(): Filter = nameFilter

                    val nameFilter: Filter = object : Filter() {
                        override fun convertResultToString(value: Any) =
                            value as String

                        override fun performFiltering(constraint: CharSequence?) =
                            FilterResults().also { result ->
                                constraint?.notEmpty()?.toString()?.lowercase()?.let { key ->
                                    // suggestions リストは毎回生成する必要がある。publishResultsと同時にアクセスされる場合がある
                                    val suggestions = StringArrayList()
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

                        override fun publishResults(
                            constraint: CharSequence?,
                            results: FilterResults?,
                        ) {
                            clear()
                            (results?.values as? StringArrayList)?.let { addAll(it) }
                            notifyDataSetChanged()
                        }
                    }
                }
                adapter.setDropDownViewResource(R.layout.lv_spinner_dropdown)
                views.etInstance.setAdapter<ArrayAdapter<String>>(adapter)
            } catch (ex: Throwable) {
                activity.showToast(ex, "initServerNameList failed.")
            } finally {
                progress.dismissSafe()
            }
        }
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
                var host = Host.parse(validateAndShow() ?: return@launchAndShowError)
                var error: String? = null
                val tootInstance = runApiTask2(host) { client ->
                    try {
                        // ユーザの入力がホスト名かドメイン名かは分からない。
                        // WebFingerでホストを調べる
                        client.getApiHostFromWebFinger(host)?.let {
                            if (it != host) {
                                host = it
                                client.apiHost = it
                            }
                        }

                        // サーバ情報を読む
                        TootInstance.getExOrThrow(client, forceUpdate = true)
                    } catch (ex: Throwable) {
                        error = ex.message
                        null
                    }
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
