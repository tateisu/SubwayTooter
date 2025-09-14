package jp.juggler.subwaytooter.columnviewholder

import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.RadioButton
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.column.getContentColor
import jp.juggler.subwaytooter.databinding.LvHeaderProfileDirectoryBinding

internal class ViewHolderHeaderProfileDirectory(
    override val activity: ActMain,
    parent: ViewGroup,
    val views: LvHeaderProfileDirectoryBinding =
        LvHeaderProfileDirectoryBinding.inflate(activity.layoutInflater, parent, false),
) : ViewHolderHeaderBase(views.root), CompoundButton.OnCheckedChangeListener {

    private var busy = false

    init {
        views.root.tag = this
        val holder = this
        views.run {
            rbOrderActive.setOnCheckedChangeListener(holder)
            rbOrderNew.setOnCheckedChangeListener(holder)
            cbResolve.setOnCheckedChangeListener(holder)
        }
    }

    override fun showColor() {
        views.run {
            val c = column.getContentColor()
            rbOrderActive.setTextColor(c)
            rbOrderNew.setTextColor(c)
            cbResolve.setTextColor(c)
        }
    }

    override fun bindData(column: Column) {
        super.bindData(column)

        busy = true
        try {
            views.run {
                cbResolve.isChecked = column.searchResolve

                if (column.searchQuery == "new") {
                    rbOrderNew.isChecked = true
                } else {
                    rbOrderActive.isChecked = true
                }
            }
        } finally {
            busy = false
        }
    }

    override fun onViewRecycled() {
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (busy) return

        if (buttonView is RadioButton && !isChecked) return

        when (buttonView.id) {
            R.id.rbOrderActive -> column.searchQuery = "active"
            R.id.rbOrderNew -> column.searchQuery = "new"
            R.id.cbResolve -> column.searchResolve = isChecked
        }

        reloadBySettingChange()
    }
}
