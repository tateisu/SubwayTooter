package jp.juggler.subwaytooter.dialog

import android.content.Context
import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.R
import jp.juggler.util.data.notEmpty
import java.util.*

class ActionsDialog {

    private val actionList = ArrayList<Action>()

    private class Action(val caption: CharSequence, val action: () -> Unit)

    fun addAction(caption: CharSequence, action: () -> Unit): ActionsDialog {

        actionList.add(Action(caption, action))

        return this
    }

    fun show(context: Context, title: CharSequence? = null): ActionsDialog {
        AlertDialog.Builder(context).apply {
            setNegativeButton(R.string.cancel, null)
            setItems(actionList.map { it.caption }.toTypedArray()) { _, which ->
                if (which >= 0 && which < actionList.size) {
                    actionList[which].action()
                }
            }
            title?.notEmpty()?.let { setTitle(it) }
        }.show()

        return this
    }
}
