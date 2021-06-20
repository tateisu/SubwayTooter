package jp.juggler.subwaytooter

import android.view.View
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.RadioButton

internal class ViewHolderHeaderProfileDirectory(
    activityArg: ActMain,
    viewRoot: View
) : ViewHolderHeaderBase(activityArg, viewRoot), CompoundButton.OnCheckedChangeListener {

    private val rbOrderActive: RadioButton = viewRoot.findViewById(R.id.rbOrderActive)
    private val rbOrderNew: RadioButton = viewRoot.findViewById(R.id.rbOrderNew)
    private val cbResolve: CheckBox = viewRoot.findViewById(R.id.cbResolve)

    private var busy = false

    init {
        rbOrderActive.setOnCheckedChangeListener(this)
        rbOrderNew.setOnCheckedChangeListener(this)
        cbResolve.setOnCheckedChangeListener(this)
    }

    override fun showColor() {
        val c = column.getContentColor()
        rbOrderActive.setTextColor(c)
        rbOrderNew.setTextColor(c)
        cbResolve.setTextColor(c)
    }

    override fun bindData(column: Column) {
        super.bindData(column)

        busy = true
        try {
            cbResolve.isChecked = column.searchResolve

            if (column.searchQuery == "new") {
                rbOrderNew.isChecked = true
            } else {
                rbOrderActive.isChecked = true
            }
        } finally {
            busy = false
        }
    }

    override fun onViewRecycled() {
    }

    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        buttonView ?: return

        if (busy) return

        if (buttonView is RadioButton && !isChecked) return

        when (buttonView.id) {
            R.id.rbOrderActive -> column.searchQuery = "active"
            R.id.rbOrderNew -> column.searchQuery = "new"
            R.id.cbResolve -> column.searchResolve = isChecked
        }

        activity.appState.saveColumnList()
        column.startLoading()
    }
}
