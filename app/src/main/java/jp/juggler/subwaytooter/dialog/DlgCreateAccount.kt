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
import jp.juggler.subwaytooter.api.auth.CreateUserParams
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.subwaytooter.util.openCustomTab
import jp.juggler.util.data.*
import jp.juggler.util.log.showToast
import jp.juggler.util.ui.*

class DlgCreateAccount(
    val activity: AppCompatActivity,
    val apiHost: Host,
    val onClickOk: (dialog: Dialog, params: CreateUserParams) -> Unit,
) : View.OnClickListener {

    companion object {
        // private val log = LogCategory("DlgCreateAccount")

        fun AppCompatActivity.showUserCreateDialog(
            apiHost: Host,
            onClickOk: (dialog: Dialog, params: CreateUserParams) -> Unit,
        ) = DlgCreateAccount(this, apiHost, onClickOk).show()
    }

    @SuppressLint("InflateParams")
    private val viewRoot = activity.layoutInflater
        .inflate(R.layout.dlg_account_create, null, false)

    private val etUserName: EditText = viewRoot.findViewById(R.id.etUserName)
    private val etEmail: EditText = viewRoot.findViewById(R.id.etEmail)
    private val etPassword: EditText = viewRoot.findViewById(R.id.etPassword)
    private val cbAgreement: CheckBox = viewRoot.findViewById(R.id.cbAgreement)
    private val tvDescription: TextView = viewRoot.findViewById(R.id.tvDescription)
    private val etReason: EditText = viewRoot.findViewById(R.id.etReason)
    private val tvReasonCaption: TextView = viewRoot.findViewById(R.id.tvReasonCaption)

    private val dialog = Dialog(activity)

    init {
        viewRoot.findViewById<TextView>(R.id.tvInstance).text = apiHost.pretty

        intArrayOf(
            R.id.btnRules,
            R.id.btnTerms,
            R.id.btnCancel,
            R.id.btnOk
        ).forEach {
            viewRoot.findViewById<Button>(it)?.setOnClickListener(this)
        }

        val instanceInfo = TootInstance.getCached(apiHost)

        tvDescription.text =
            DecodeOptions(
                activity,
                linkHelper = LinkHelper.create(
                    apiHost,
                    misskeyVersion = instanceInfo?.misskeyVersionMajor ?: 0
                ),
            ).decodeHTML(
                instanceInfo?.description?.notBlank()
                    ?: instanceInfo?.descriptionOld?.notBlank()
                    ?: TootInstance.DESCRIPTION_DEFAULT
            ).neatSpaces()

        val showReason = instanceInfo?.approval_required ?: false
        tvReasonCaption.vg(showReason)
        etReason.vg(showReason)
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
            R.id.btnRules ->
                activity.openCustomTab("https://$apiHost/about/more")

            R.id.btnTerms ->
                activity.openCustomTab("https://$apiHost/terms")

            R.id.btnCancel ->
                dialog.cancel()

            R.id.btnOk -> {
                val username = etUserName.text.toString().trim()
                val email = etEmail.text.toString().trim()
                val password = etPassword.text.toString().trim()

                when {
                    username.isEmpty() ->
                        activity.showToast(true, R.string.username_empty)

                    email.isEmpty() ->
                        activity.showToast(true, R.string.email_empty)

                    password.isEmpty() ->
                        activity.showToast(true, R.string.password_empty)

                    username.contains("/") || username.contains("@") ->
                        activity.showToast(true, R.string.username_not_need_atmark)

                    else -> onClickOk(
                        dialog,
                        CreateUserParams(
                            username = username,
                            email = email,
                            password = password,
                            agreement = cbAgreement.isChecked,
                            reason = when (etReason.visibility) {
                                View.VISIBLE -> etReason.text.toString().trim()
                                else -> null
                            },
                        )
                    )
                }
            }
        }
    }
}
