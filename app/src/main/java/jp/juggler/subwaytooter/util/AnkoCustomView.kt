package jp.juggler.subwaytooter.util

import android.view.ViewManager
import androidx.recyclerview.widget.RecyclerView
import com.omadahealth.github.swipyrefreshlayout.library.SwipyRefreshLayout
import jp.juggler.subwaytooter.view.*
import org.jetbrains.anko.custom.ankoView
import androidx.appcompat.view.ContextThemeWrapper
import jp.juggler.subwaytooter.R

// Anko Layout中にカスタムビューを指定する為に拡張関数を定義する

inline fun ViewManager.myNetworkImageView(init : MyNetworkImageView.() -> Unit) : MyNetworkImageView {
	return ankoView({ MyNetworkImageView(it) }, theme = 0, init = init)
}

inline fun ViewManager.myTextView(init : MyTextView.() -> Unit) : MyTextView {
	return ankoView({ MyTextView(it) }, theme = 0, init = init)
}

inline fun ViewManager.trendTagHistoryView(init : TagHistoryView.() -> Unit) : TagHistoryView {
	return ankoView({ TagHistoryView(it) }, theme = 0, init = init)
}

inline fun ViewManager.blurhashView(init : BlurhashView.() -> Unit) : BlurhashView {
	return ankoView({ BlurhashView(it) }, theme = 0, init = init)
}

inline fun ViewManager.maxHeightScrollView(init : MaxHeightScrollView.() -> Unit) : MaxHeightScrollView {
	return ankoView({ MaxHeightScrollView(it) }, theme = 0, init = init)
}

inline fun ViewManager.swipyRefreshLayout(init : SwipyRefreshLayout.() -> Unit) : SwipyRefreshLayout {
	return ankoView({ SwipyRefreshLayout(it) }, theme = 0, init = init)
}

inline fun ViewManager.recyclerView(init : RecyclerView.() -> Unit) : RecyclerView {
	return ankoView({
		RecyclerView(ContextThemeWrapper(it, R.style.recycler_view_with_scroll_bar))
	}, theme = 0, init = init)
}

