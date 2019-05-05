package jp.juggler.subwaytooter.util

import android.view.ViewManager
import jp.juggler.subwaytooter.view.BlurhashView
import jp.juggler.subwaytooter.view.MyNetworkImageView
import jp.juggler.subwaytooter.view.MyTextView
import jp.juggler.subwaytooter.view.TrendTagHistoryView
import org.jetbrains.anko.custom.ankoView

// Anko Layout中にカスタムビューを指定する為に拡張関数を定義する

inline fun ViewManager.myNetworkImageView(init: MyNetworkImageView.() -> Unit): MyNetworkImageView {
	return ankoView({ MyNetworkImageView(it) }, theme = 0, init = init)
}

inline fun ViewManager.myTextView(init: MyTextView.() -> Unit) : MyTextView {
	return ankoView({ MyTextView(it) }, theme = 0, init = init)
}


inline fun ViewManager.trendTagHistoryView(init: TrendTagHistoryView.() -> Unit): TrendTagHistoryView {
	return ankoView({ TrendTagHistoryView(it) }, theme = 0, init = init)
}

inline fun ViewManager.blurhashView(init: BlurhashView.() -> Unit): BlurhashView {
	return ankoView({ BlurhashView(it) }, theme = 0, init = init)
}
