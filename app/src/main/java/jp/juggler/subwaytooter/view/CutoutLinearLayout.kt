package jp.juggler.subwaytooter.view

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import jp.juggler.util.LogCategory
import jp.juggler.util.cast
import java.util.*

class CutoutLinearLayout : LinearLayout {
	companion object {
		private val log = LogCategory("CutoutLinearLayout")
	}
	
	constructor(context : Context) :
		super(context) {
		init()
	}
	
	constructor(context : Context, attrs : AttributeSet) :
		super(context, attrs) {
		init()
	}
	
	constructor(context : Context, attrs : AttributeSet, defStyleAttr : Int) :
		super(context, attrs, defStyleAttr) {
		init()
	}
	
	fun init() {
		setWillNotDraw(false)
	}
	
	//	override fun dispatchDraw(canvas : Canvas?) {
	//		super.dispatchDraw(canvas)
	//	}
	
	class CutoutDrawer(
		val view : View,
		val draw : (
			canvas : Canvas,
			parent : ViewGroup,
			descendant : View,
			left : Int,
			top : Int
		) -> Unit
	)
	
	private val cutoutList = LinkedList<CutoutDrawer>()
	
	fun addCutoutDrawer(
		view : View,
		draw : (
			canvas : Canvas,
			parent : ViewGroup,
			descendant : View,
			left : Int,
			top : Int
		) -> Unit
	) {
		if(null == cutoutList.find { it.view == view && it.draw == draw }) cutoutList.add(
			CutoutDrawer(view, draw)
		)
	}
	
	@Suppress("unused")
	fun removeCutoutDrawer(view : View) {
		val it = cutoutList.iterator()
		while(it.hasNext()) {
			val cb = it.next()
			if(cb.view == view) it.remove()
		}
	}
	
	override fun onDraw(canvas : Canvas?) {
		super.onDraw(canvas)
		canvas ?: return
		log.d("CutoutLinearLayout.onDraw!")
		
		val it = cutoutList.iterator()
		while(it.hasNext()) {
			val drawer = it.next()
			
			var left = 0
			var top = 0
			var v = drawer.view
			while(true) {
				if(v == this) break
				val parent = v.parent.cast<ViewGroup>() ?: break
				left += v.left
				top += v.top
				v = parent
			}
			canvas.save()
			try {
				drawer.draw(canvas, this, drawer.view, left, top)
			} finally {
				canvas.restore()
			}
		}
	}
}