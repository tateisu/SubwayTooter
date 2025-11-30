package jp.juggler.subwaytooter.ui.languageFilter

import android.content.Context
import androidx.annotation.StringRes

class StringResAndArgs(
    @StringRes val stringId: Int,
    // composeのstringResource ではnullを受け付けなくなった
    vararg val args: Any,
) {
    fun toCharSequence(context: Context) = when {
        args.isNotEmpty() -> context.getString(stringId, *args)
        else -> context.getText(stringId)
    }

    fun toString(context: Context) = when {
        args.isNotEmpty() -> context.getString(stringId, *args)
        else -> context.getString(stringId)
    }
}
