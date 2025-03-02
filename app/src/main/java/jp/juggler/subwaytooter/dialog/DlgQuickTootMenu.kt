package jp.juggler.subwaytooter.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.api.entity.TootVisibility
import jp.juggler.subwaytooter.databinding.DlgQuickTootMenuBinding
import jp.juggler.subwaytooter.pref.PrefS
import jp.juggler.util.data.notEmpty
import jp.juggler.util.ui.dismissSafe
import java.lang.ref.WeakReference

class DlgQuickTootMenu(
    internal val activity: ActMain,
    internal val callback: Callback,
) {
    companion object {
        val visibilityList = arrayOf(
            TootVisibility.AccountSetting,
            TootVisibility.WebSetting,
            TootVisibility.Public,
            TootVisibility.UnlistedHome,
            TootVisibility.PrivateFollowers,
            TootVisibility.DirectSpecified,
        )
    }

    interface Callback {
        fun onMacro(text: String)
        var visibility: TootVisibility
    }

    private class MemoViews(
        val editText: EditText,
        val btnUse: View,
    )

    private var refDialog: WeakReference<Dialog>? = null

    private fun loadStrings() =
        PrefS.spQuickTootMacro.value.split("\n")

    private fun saveStrings(newValue: String) {
        PrefS.spQuickTootMacro.value = newValue
    }

    private fun show() {
        val strings = loadStrings()
        val dialog = Dialog(activity).also { refDialog = WeakReference(it) }
        val views = DlgQuickTootMenuBinding.inflate(activity.layoutInflater).apply {
            btnCancel.setOnClickListener { dialog.dismissSafe() }
            val memoList = listOf(
                MemoViews(etText0, btnText0),
                MemoViews(etText1, btnText1),
                MemoViews(etText2, btnText2),
                MemoViews(etText3, btnText3),
                MemoViews(etText4, btnText4),
                MemoViews(etText5, btnText5),
            )
            memoList.forEachIndexed { i, m ->
                val initialText = strings.elementAtOrNull(i) ?: ""
                m.editText.setText(initialText)
                m.btnUse.setOnClickListener {
                    m.editText.text?.toString()?.notEmpty()?.let {
                        dialog.dismissSafe()
                        callback.onMacro(it)
                    }
                }
            }
            dialog.setOnDismissListener {
                saveStrings(
                    memoList.map { it.editText.text?.toString()?.replace("\n", " ") ?: "" }
                        .joinToString("\n")
                )
            }

            fun showVisibility() {
                btnVisibility.text = getVisibilityCaption(activity, false, callback.visibility)
            }

            fun changeVisivility(newVisibility: TootVisibility?) {
                newVisibility ?: return
                callback.visibility = newVisibility
                showVisibility()
            }

            showVisibility()

            btnVisibility.setOnClickListener {
                val captionList = visibilityList
                    .map { getVisibilityCaption(activity, false, it) }
                    .toTypedArray()

                AlertDialog.Builder(activity)
                    .setTitle(R.string.choose_visibility)
                    .setItems(captionList) { _, which ->
                        changeVisivility(visibilityList.elementAtOrNull(which))
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
        dialog.apply {
            setContentView(views.root)
            setCanceledOnTouchOutside(true)
            window?.apply {
                attributes = attributes.apply {
                    gravity = Gravity.BOTTOM or Gravity.START
                    flags = flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
                }
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                )
            }
            show()
        }
    }

    fun toggle() {
        val dialog = refDialog?.get()
        when {
            dialog?.isShowing == true -> dialog.dismissSafe()
            else -> show()
        }
    }
}
