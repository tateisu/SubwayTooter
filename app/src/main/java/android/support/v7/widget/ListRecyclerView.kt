package android.support.v7.widget

import android.content.Context
import android.util.AttributeSet

class ListRecyclerView : RecyclerView {
	
	companion object {
		// private val warning = LogCategory("ListRecyclerView")
	}
	
	constructor(context : Context) : super(context)
	constructor(context : Context, attrs : AttributeSet) : super(context, attrs)
	constructor(context : Context, attrs : AttributeSet, defStyleAttr : Int) : super(context, attrs, defStyleAttr)
	
}
