package jp.juggler.subwaytooter.util

import android.view.ViewManager
import androidx.appcompat.view.ContextThemeWrapper
import androidx.emoji2.widget.EmojiButton
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import com.omadahealth.github.swipyrefreshlayout.library.SwipyRefreshLayout
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.view.*
import org.jetbrains.anko.custom.ankoView

// Anko Layout中にカスタムビューを指定する為に拡張関数を定義する

inline fun ViewManager.myNetworkImageView(init: MyNetworkImageView.() -> Unit): MyNetworkImageView {
    return ankoView({ MyNetworkImageView(it) }, theme = 0, init = init)
}

inline fun ViewManager.myTextView(init: MyTextView.() -> Unit): MyTextView {
    return ankoView({ MyTextView(it) }, theme = 0, init = init)
}

inline fun ViewManager.myEditText(init: MyEditText.() -> Unit): MyEditText {
    return ankoView({ MyEditText(it) }, theme = 0, init = init)
}

inline fun ViewManager.compatButton(init: EmojiButton.() -> Unit): EmojiButton {
    return ankoView({ EmojiButton(it) }, theme = 0, init = init)
}

inline fun ViewManager.trendTagHistoryView(init: TagHistoryView.() -> Unit): TagHistoryView {
    return ankoView({ TagHistoryView(it) }, theme = 0, init = init)
}

inline fun ViewManager.blurhashView(init: BlurhashView.() -> Unit): BlurhashView {
    return ankoView({ BlurhashView(it) }, theme = 0, init = init)
}

inline fun ViewManager.maxHeightScrollView(init: MaxHeightScrollView.() -> Unit): MaxHeightScrollView {
    return ankoView({ MaxHeightScrollView(it) }, theme = 0, init = init)
}

inline fun ViewManager.swipyRefreshLayout(init: SwipyRefreshLayout.() -> Unit): SwipyRefreshLayout {
    return ankoView({ SwipyRefreshLayout(it) }, theme = 0, init = init)
}

inline fun ViewManager.recyclerView(init: RecyclerView.() -> Unit): RecyclerView {
    return ankoView({
        RecyclerView(ContextThemeWrapper(it, R.style.recycler_view_with_scroll_bar))
    }, theme = 0, init = init)
}

inline fun ViewManager.flexboxLayout(init: FlexboxLayout.() -> Unit): FlexboxLayout {
    return ankoView({ FlexboxLayout(it) }, theme = 0, init = init)
}
