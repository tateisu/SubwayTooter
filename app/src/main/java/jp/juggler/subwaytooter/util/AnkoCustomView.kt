package jp.juggler.subwaytooter.util

import android.view.ViewManager
import jp.juggler.subwaytooter.view.MyNetworkImageView
import jp.juggler.subwaytooter.view.MyTextView
import org.jetbrains.anko.custom.ankoView

// Anko Layout中にカスタムビューを指定する為に拡張関数を定義する

inline fun ViewManager.myNetworkImageView(init: MyNetworkImageView.() -> Unit): MyNetworkImageView {
	return ankoView({ MyNetworkImageView(it) }, theme = 0, init = init)
}

inline fun ViewManager.myTextView(init: MyTextView.() -> Unit): MyTextView {
	return ankoView({ MyTextView(it) }, theme = 0, init = init)
}

