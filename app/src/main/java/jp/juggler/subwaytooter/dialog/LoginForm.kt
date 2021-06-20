package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.annotation.StringRes
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.util.LogCategory
import jp.juggler.util.showToast
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.IDN
import java.util.*

object LoginForm {

    private val log = LogCategory("LoginForm")

    private class StringArray : ArrayList<String>()

    enum class Action(
        val pos: Int,
        @StringRes val idName: Int,
        @StringRes val idDesc: Int,
    ) {

        Existing(0, R.string.existing_account, R.string.existing_account_desc),
        Pseudo(1, R.string.pseudo_account, R.string.pseudo_account_desc),
        Create(2, R.string.create_account, R.string.create_account_desc),
        Token(3, R.string.input_access_token, R.string.input_access_token_desc),
    }

    @SuppressLint("InflateParams")
    fun showLoginForm(
        activity: Activity,
        instanceArg: String?,
        onClickOk: (
            dialog: Dialog,
            instance: Host,
            action: Action,
        ) -> Unit,
    ) {
        val view = activity.layoutInflater.inflate(R.layout.dlg_account_add, null, false)
        val etInstance: AutoCompleteTextView = view.findViewById(R.id.etInstance)
        val btnOk: View = view.findViewById(R.id.btnOk)

        val tvActionDesc: TextView = view.findViewById(R.id.tvActionDesc)

        fun Spinner.getActionDesc(): String =
            Action.values()
                .find { it.pos == selectedItemPosition }
                ?.let { activity.getString(it.idDesc) }
                ?: "(null)"

        val spAction = view.findViewById<Spinner>(R.id.spAction).also { sp ->

            sp.adapter = ArrayAdapter(
                activity,
                android.R.layout.simple_spinner_item,
                Action.values().map { activity.getString(it.idName) }.toTypedArray()
            ).apply {
                setDropDownViewResource(R.layout.lv_spinner_dropdown)
            }

            sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) { // TODO
                    tvActionDesc.text = sp.getActionDesc()
                }

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    tvActionDesc.text = sp.getActionDesc()
                }
            }
        }

        tvActionDesc.text = spAction.getActionDesc()

        if (instanceArg != null && instanceArg.isNotEmpty()) {
            etInstance.setText(instanceArg)
            etInstance.inputType = InputType.TYPE_NULL
            etInstance.isEnabled = false
            etInstance.isFocusable = false
        } else {
            etInstance.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    btnOk.performClick()
                    return@OnEditorActionListener true
                }
                false
            })
        }
        val dialog = Dialog(activity)
        dialog.setContentView(view)
        // 警告がでるが、パラメータ名の指定を削ってはいけない
        btnOk.setOnClickListener { _ ->
            val instance = etInstance.text.toString().trim { it <= ' ' }

            when {

                instance.isEmpty() ->
                    activity.showToast(true, R.string.instance_not_specified)

                instance.contains("/") || instance.contains("@") ->
                    activity.showToast(true, R.string.instance_not_need_slash)

                else -> {
                    val actionPos = spAction.selectedItemPosition
                    when (val action = Action.values().find { it.pos == actionPos }) {
                        null -> {
                        } // will no happened
                        else -> onClickOk(dialog, Host.parse(instance), action)
                    }
                }
            }
        }
        view.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.cancel() }

        val instance_list = HashSet<String>().apply {
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
                log.trace(ex)
            }
        }.toList().sorted()

        val adapter = object : ArrayAdapter<String>(
            activity, R.layout.lv_spinner_dropdown, ArrayList()
        ) {

            val nameFilter: Filter = object : Filter() {
                override fun convertResultToString(value: Any): CharSequence {
                    return value as String
                }

                override fun performFiltering(constraint: CharSequence?): FilterResults =
                    FilterResults().also { result ->
                        if (constraint?.isNotEmpty() == true) {
                            val key = constraint.toString().lowercase()
                            // suggestions リストは毎回生成する必要がある。publishResultsと同時にアクセスされる場合がある
                            val suggestions = StringArray()
                            for (s in instance_list) {
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
                    val values = results?.values
                    if (values is StringArray) {
                        for (s in values) {
                            add(s)
                        }
                    }
                    notifyDataSetChanged()
                }
            }

            override fun getFilter(): Filter {
                return nameFilter
            }
        }
        adapter.setDropDownViewResource(R.layout.lv_spinner_dropdown)
        etInstance.setAdapter<ArrayAdapter<String>>(adapter)

        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }
}
