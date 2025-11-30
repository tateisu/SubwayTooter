package jp.juggler.apng.sample

import android.graphics.Color
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun ComponentActivity.edgeToEdgeEx(
    contentView: View,
    insetView: View = contentView,
){
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(
            lightScrim = Color.WHITE,
            darkScrim = Color.BLACK,
        ),
        navigationBarStyle = SystemBarStyle.auto(
            lightScrim = Color.WHITE,
            darkScrim = Color.BLACK,
        ),
    )
    setContentView(contentView)
    ViewCompat.setOnApplyWindowInsetsListener(insetView) { v, windowInsets ->
        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        // Apply the insets as a margin to the view. This solution sets
        // only the bottom, left, and right dimensions, but you can apply whichever
        // insets are appropriate to your layout. You can also update the view padding
        // if that's more appropriate.
        val lp = v.layoutParams
        if (lp is MarginLayoutParams) {
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
