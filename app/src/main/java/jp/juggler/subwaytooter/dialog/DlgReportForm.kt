package jp.juggler.subwaytooter.dialog

import android.app.Activity
import android.app.Dialog
import android.view.WindowManager
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.databinding.DlgReportUserBinding
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.log.*
import jp.juggler.util.ui.vg

fun Activity.showReportDialog(
    accessInfo: SavedAccount,
    who: TootAccount,
    status: TootStatus?,
    canForward: Boolean,
    onClickOk: (dialog: Dialog, comment: String, forward: Boolean) -> Unit,
) {
    val dialog = Dialog(this)
    val views = DlgReportUserBinding.inflate(layoutInflater).apply {
        tvUser.text = who.acct.pretty
        tvStatusCaption.vg(status != null)
        tvStatus.vg(status != null)?.text = status?.decoded_content
        cbForward.vg(canForward)?.apply {
            isChecked = true
            text = getString(R.string.report_forward_to, who.apDomain.pretty)
        }
        btnCancel.setOnClickListener { dialog.cancel() }
        btnOk.setOnClickListener {
            when (val comment = etComment.text?.toString()?.trim()) {
                null, "" -> showToast(true, R.string.comment_empty)
                else -> onClickOk(dialog, comment, canForward && cbForward.isChecked)
            }
        }
    }
    dialog.apply {
        setContentView(views.root)
        window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        show()
    }
}
