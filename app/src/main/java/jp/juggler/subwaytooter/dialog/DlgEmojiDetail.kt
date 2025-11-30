package jp.juggler.subwaytooter.dialog

import android.app.Dialog
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import jp.juggler.subwaytooter.databinding.DlgEmojiDetailBinding
import jp.juggler.subwaytooter.view.NetworkEmojiView
import jp.juggler.subwaytooter.R
import jp.juggler.util.ui.dismissSafe
import jp.juggler.util.ui.vg

fun AppCompatActivity.showEmojiDetailDialog(
    detail: String,
    initialzeNiv: (NetworkEmojiView.() -> Unit)? = null,
    initializeImage: (AppCompatImageView.() -> Unit)? = null,
    initializeText: (AppCompatTextView.() -> Unit)? = null,
) {
    val dialog = Dialog(this)

    val views = DlgEmojiDetailBinding.inflate(layoutInflater)
    views.btnOk.setOnClickListener { dialog.dismissSafe() }
    views.nivEmoji.vg(initialzeNiv != null)?.let {
        initialzeNiv?.invoke(it)
    }
    views.ivEmoji.vg(initializeImage != null)?.let {
        initializeImage?.invoke(it)
    }
    views.tvEmoji.vg(initializeText != null)?.let {
        initializeText?.invoke(it)
    }
    views.etJson.setText(detail)

    dialog.setTitle(R.string.emoji_detail)
    dialog.setContentView(views.root)
    dialog.window?.setLayout(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT
    )
    dialog.show()
}
