package jp.juggler.util.ui

import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import jp.juggler.util.log.LogCategory
import kotlin.math.max

private val log = LogCategory("ActivityUtils")

fun ComponentActivity.setContentViewAndInsets(
    // setContentView に指定するView
    contentView: View,
    // setOnApplyWindowInsetsListenerに指定するView
    insetView: View = contentView,
    // Marignを設定するなら真。Paddingを設定するなら偽
    useMargin: Boolean = true,
) {
    setContentView(contentView)
    ViewCompat.setOnApplyWindowInsetsListener(insetView) { v, windowInsets ->
        val barInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
        log.i(
            "setContentViewAndInsets: activity=${
                javaClass.simpleName
            }, parent=${
                v.parent
            }, view=${
                v
            }, layoutParams=${
                v.layoutParams.javaClass
            }, horiz=${
                barInsets.left
            }～${
                barInsets.right
            }, verti=${
                barInsets.top
            }～${
                max(barInsets.bottom,imeInsets.bottom)
            }"
        )
        if (useMargin) {
            v.updateLayoutParams<MarginLayoutParams> {
                topMargin = barInsets.top
                bottomMargin = max(barInsets.bottom, imeInsets.bottom)
                leftMargin = barInsets.left
                rightMargin = barInsets.right
            }
        } else {
            v.setPadding(
                barInsets.left,
                barInsets.top,
                barInsets.right,
                max(barInsets.bottom, imeInsets.bottom),
            )
        }
        // Return CONSUMED if you don't want want the window insets to keep passing
        // down to descendant views.
        WindowInsetsCompat.CONSUMED
    }
}
