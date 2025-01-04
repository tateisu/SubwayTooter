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
    insetView: View? = contentView,
) {
    setContentView(contentView)
    if (insetView != null) {
        ViewCompat.setOnApplyWindowInsetsListener(insetView) { v, windowInsets ->
            val insetsList = buildList {
                // システムバー
                add(windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()))
                // ソフトウェアキーボード
                val typeIme = WindowInsetsCompat.Type.ime()
                if (windowInsets.isVisible(typeIme)) {
                    add(windowInsets.getInsets(typeIme))
                }
            }
            val lp = v.layoutParams
            if (lp !is MarginLayoutParams) {
                log.w("activity=${this.javaClass.simpleName}, parent=${v.parent}, view=${v}, layoutParams=$lp, can't apply windowInsets=$windowInsets")
            } else {
                log.w("activity=${this.javaClass.simpleName}, parent=${v.parent}, view=${v}, layoutParams=$lp, apply windowInsets=$windowInsets")
                v.layoutParams = lp.apply {
                    topMargin = insetsList.maxOfOrNull { it.top } ?: 0
                    bottomMargin = insetsList.maxOfOrNull { it.bottom } ?: 0
                    leftMargin = insetsList.maxOfOrNull { it.left } ?: 0
                    rightMargin = insetsList.maxOfOrNull { it.right } ?: 0
                }
            }
            // Return CONSUMED if you don't want want the window insets to keep passing
            // down to descendant views.
            WindowInsetsCompat.CONSUMED
        }
    }
}
