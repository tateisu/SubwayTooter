package jp.juggler.util.ui

import android.content.Context
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import androidx.annotation.AttrRes

/**
 * ForegroundColorSpanと似ているが、色attributeへのアクセスを描画直前まで遅らせる
 * テーマが初期化される前にSPANを組む場合に有効
 */
class ForegroundAttrColorSpan(
    @AttrRes private val attrId: Int,
) : CharacterStyle(), UpdateAppearance {
    // context は後から変更したい
    var context: Context? = null

    override fun toString(): String =
        "AttrColorSpan{attrId=#${"%08X".format(attrId)}}"

    override fun updateDrawState(tp: TextPaint?) {
        context?.attrColor(attrId)?.let { tp?.setColor(it) }
    }
}
