package jp.juggler.util.ui

import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import jp.juggler.util.log.LogCategory

private val log = LogCategory("ActivityUtils")

fun ComponentActivity.setContentViewAndInsets(
    contentView: View,
    insetView:View? = contentView,
){
    setContentView(contentView)
    if (insetView != null) {
        ViewCompat.setOnApplyWindowInsetsListener(insetView) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply the insets as a margin to the view. This solution sets
            // only the bottom, left, and right dimensions, but you can apply whichever
            // insets are appropriate to your layout. You can also update the view padding
            // if that's more appropriate.
            val lp = v.layoutParams
            if (lp !is MarginLayoutParams) {
                log.w("activity=${this.javaClass.simpleName}, parent=${v.parent}, view=${v}, layoutParams=$lp, can't apply insets=$insets")
            } else {
                log.w("activity=${this.javaClass.simpleName}, parent=${v.parent}, view=${v}, layoutParams=$lp, apply insets=$insets")
                lp.topMargin = insets.top
                lp.leftMargin = insets.left
                lp.bottomMargin = insets.bottom
                lp.rightMargin = insets.right
                v.layoutParams = lp
            }
            // Return CONSUMED if you don't want want the window insets to keep passing
            // down to descendant views.
            WindowInsetsCompat.CONSUMED
        }
    }
}
