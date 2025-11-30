package jp.juggler.subwaytooter.view

import android.view.ViewGroup
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.databinding.ActionBarCustomTitleBinding
import jp.juggler.util.ui.textOrGone

fun AppCompatActivity.wrapTitleTextView(
    title: CharSequence? = null,
    subtitle: CharSequence? = null,
): ActionBarCustomTitleBinding {
    val views = ActionBarCustomTitleBinding.inflate(layoutInflater)

    views.tvTitle.textOrGone = (title ?: this.title)
    views.tvSubtitle.textOrGone = subtitle
    views.root.setTag(supportActionBar)

    supportActionBar?.apply {
        // 通常のタイトル表示をOFF
        setDisplayShowTitleEnabled(false)
        // カスタムビューの表示を許可する
        setDisplayOptions(
            ActionBar.DISPLAY_SHOW_CUSTOM, // bits
            ActionBar.DISPLAY_SHOW_CUSTOM, // mask
        )
        // カスタムビューをセット
        setCustomView(
            views.root,
            ActionBar.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        )
    }

    return views
}

var ActionBarCustomTitleBinding.title: CharSequence?
    get() = tvTitle.textOrGone ?: (root.getTag() as? ActionBar)?.title
    set(value) {
        tvTitle.textOrGone = value
    }

var ActionBarCustomTitleBinding.subtitle: CharSequence?
    get() = tvSubtitle.textOrGone ?: (root.getTag() as? ActionBar)?.subtitle
    set(value) {
        tvSubtitle.textOrGone = value
    }
