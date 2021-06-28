package jp.juggler.subwaytooter.actpost

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

class ActPostRootLinearLayout : LinearLayout {

    constructor(context: Context) :
        super(context)

    constructor(context: Context, attrs: AttributeSet) :
        super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    var callbackOnSizeChanged: (w: Int, h: Int, oldW: Int, oldH: Int) -> Unit = { _, _, _, _ -> }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        callbackOnSizeChanged(w, h, oldW, oldH)
    }
}
