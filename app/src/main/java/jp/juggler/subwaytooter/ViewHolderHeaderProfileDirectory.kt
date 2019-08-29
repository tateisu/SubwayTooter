package jp.juggler.subwaytooter

import android.view.View
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.RadioButton

internal class ViewHolderHeaderProfileDirectory(
	arg_activity : ActMain,
	viewRoot : View
) : ViewHolderHeaderBase(arg_activity, viewRoot), CompoundButton.OnCheckedChangeListener {
	
	private val rbOrderActive : RadioButton = viewRoot.findViewById(R.id.rbOrderActive)
	private val rbOrderNew : RadioButton = viewRoot.findViewById(R.id.rbOrderNew)
	private val cbResolve : CheckBox = viewRoot.findViewById(R.id.cbResolve)
	
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
	
	override fun bindData(column : Column) {
		super.bindData(column)
		
		busy = true
		try {
			cbResolve.isChecked = column.search_resolve
			
			if(column.search_query == "new") {
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
	
	override fun onCheckedChanged(buttonView : CompoundButton?, isChecked : Boolean) {
		buttonView ?: return

		if(busy) return

		if( buttonView is RadioButton && !isChecked ) return

		when(buttonView.id) {
			R.id.rbOrderActive -> column.search_query = "active"
			R.id.rbOrderNew -> column.search_query = "new"
			R.id.cbResolve -> column.search_resolve = isChecked
		}

		activity.app_state.saveColumnList()
		column.startLoading()
	}
}
