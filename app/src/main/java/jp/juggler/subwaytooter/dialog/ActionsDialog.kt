package jp.juggler.subwaytooter.dialog

import android.content.Context
import androidx.appcompat.app.AlertDialog

import java.util.ArrayList

import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.util.EmptyCallback

class ActionsDialog {
	
	private val action_list = ArrayList<Action>()
	
	private class Action internal constructor(internal val caption : CharSequence, internal val r : EmptyCallback)
	
	private var choosed =false

	var onCancel :()->Unit = {}
	
	fun addAction(caption : CharSequence, r : EmptyCallback) : ActionsDialog {
		
		action_list.add(Action(caption, r))
		
		return this
	}
	
	fun show(context : Context, title : CharSequence? = null ) : ActionsDialog {
		val caption_list = arrayOfNulls<CharSequence>(action_list.size)
		var i = 0
		val ie = caption_list.size
		while(i < ie) {
			caption_list[i] = action_list[i].caption
			++ i
		}
		val b = AlertDialog.Builder(context)
			.setNegativeButton(R.string.cancel,null)
			.setItems(caption_list) { _, which ->
				if(which >= 0 && which < action_list.size) {
					choosed = true
					action_list[which].r()
				}
			}
		
		if( title != null && title.isNotEmpty() ) b.setTitle(title)
		
		val d = b.create()
		d.setOnDismissListener {
			if(!choosed) onCancel()
		}
		d.show()
		
		return this
	}
}
