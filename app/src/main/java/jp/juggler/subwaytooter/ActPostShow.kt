package jp.juggler.subwaytooter

import android.view.View

fun ActPost.showContentWarningEnabled() {
    etContentWarning.visibility = if (cbContentWarning.isChecked) View.VISIBLE else View.GONE
}
