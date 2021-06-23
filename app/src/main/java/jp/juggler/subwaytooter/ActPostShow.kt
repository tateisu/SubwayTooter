package jp.juggler.subwaytooter

import android.view.View
import jp.juggler.subwaytooter.api.entity.TootVisibility

fun ActPost.showContentWarningEnabled() {
    etContentWarning.visibility = if (cbContentWarning.isChecked) View.VISIBLE else View.GONE
}

