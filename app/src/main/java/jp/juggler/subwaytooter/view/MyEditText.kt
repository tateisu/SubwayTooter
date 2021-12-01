package jp.juggler.subwaytooter.view

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.ContentInfoCompat
import androidx.core.view.OnReceiveContentListener
import androidx.core.view.ViewCompat
import jp.juggler.util.LogCategory

class MyEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    companion object {
        private val log = LogCategory("MyEditText")
        val MIME_TYPES = arrayOf("image/*")
    }

    // 選択範囲変更リスナ
    var onSelectionChange: ((selStart: Int, selEnd: Int) -> Unit)? = null

    // キーボードやDnDから画像を挿入するリスナ
    var contentCallback: ((Uri) -> Unit)? = null

    ///////////////////////////////////////////////////////
    // IMEから画像を送られてくることがあるらしい

    var contentMineTypeArray: Array<String>? = null

    private val receiveContentListener =
        OnReceiveContentListener { _: View, payload: ContentInfoCompat ->
            // コールバックが設定されていないなら何も受け取らない
            val contentCallback = contentCallback
                ?: return@OnReceiveContentListener payload

            // URIを含むかデータとそれ以外とに分離する
            // どちらもNullable
            val pair = payload.partition { it.uri != null }

            pair.first?.let { uriContent ->
                val clip = uriContent.clip
                for (i in 0 until clip.itemCount) {
                    val uri = clip.getItemAt(i).uri
                    contentCallback(uri)
                }
            }

            // returns remaining (may null)
            pair.second
        }

    init {
        ViewCompat.setOnReceiveContentListener(this, MIME_TYPES, receiveContentListener)
    }

    ////////////////////////////////////////////////////
    // 選択範囲変更の傍受

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChange?.invoke(selStart, selEnd)
    }

    ////////////////////////////////////////////////////
    // Android 6.0 でのクラッシュ対応

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return try {
            super.onTouchEvent(event)
        } catch (ex: Throwable) {
            log.trace(ex)
            false
            //		java.lang.NullPointerException:
            //		at android.widget.Editor$SelectionModifierCursorController.onTouchEvent (Editor.java:4889)
            //		at android.widget.Editor.onTouchEvent (Editor.java:1223)
            //		at android.widget.TextView.onTouchEvent (TextView.java:8304)
            //		at android.view.View.dispatchTouchEvent (View.java:9303)
        }
    }
}
