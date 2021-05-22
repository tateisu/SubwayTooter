package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.isEnabledAlpha

class DlgConfirmMail(
    val activity: AppCompatActivity,
    val accessInfo: SavedAccount,
    val onClickOk: (email: String?) -> Unit
) : View.OnClickListener {

    @SuppressLint("InflateParams")
    private val viewRoot = activity.layoutInflater
        .inflate(R.layout.dlg_confirm_mail, null, false)

    private val cbUpdateMailAddress: CheckBox = viewRoot.findViewById(R.id.cbUpdateMailAddress)
    private val etEmail: EditText = viewRoot.findViewById(R.id.etEmail)

    private val dialog = Dialog(activity)

    init {
        viewRoot.findViewById<TextView>(R.id.tvUserName).text = accessInfo.acct.pretty

        viewRoot.findViewById<TextView>(R.id.tvInstance).text =
            if (accessInfo.apiHost != accessInfo.apDomain) {
                "${accessInfo.apiHost.pretty} (${accessInfo.apDomain.pretty})"
            } else {
                accessInfo.apiHost.pretty
            }

        cbUpdateMailAddress.setOnCheckedChangeListener { _, isChecked ->
            etEmail.isEnabledAlpha = isChecked
        }

        arrayOf(
            R.id.btnCancel,
            R.id.btnOk
        ).forEach {
            viewRoot.findViewById<Button>(it)?.setOnClickListener(this)
        }
    }

    fun show() {
        dialog.setContentView(viewRoot)

        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnCancel ->
                dialog.cancel()

            R.id.btnOk ->
                onClickOk(
                    if (cbUpdateMailAddress.isChecked) etEmail.text.toString().trim() else null
                )
        }
    }
}
