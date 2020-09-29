package jp.juggler.subwaytooter.dialog

import android.content.Context
import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.R
import java.util.*

class ActionsDialog {
	
	private val action_list = ArrayList<Action>()
	
	private class Action(val caption : CharSequence, val action : () -> Unit)
	
	fun addAction(caption : CharSequence, action : () -> Unit) : ActionsDialog {
		
		action_list.add(Action(caption, action))
		
		return this
	}
	
	fun show(context : Context, title : CharSequence? = null) : ActionsDialog {
		val caption_list = arrayOfNulls<CharSequence>(action_list.size)
		var i = 0
		val ie = caption_list.size
		while(i < ie) {
			caption_list[i] = action_list[i].caption
			++ i
		}
		val b = AlertDialog.Builder(context)
			.setNegativeButton(R.string.cancel, null)
			.setItems(caption_list) { _, which ->
				if(which >= 0 && which < action_list.size) {
					action_list[which].action()
				}
			}
		
		if(title != null && title.isNotEmpty()) b.setTitle(title)
		
		b.show()
		
		return this
	}
}
