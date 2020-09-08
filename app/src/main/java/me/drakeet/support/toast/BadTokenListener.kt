package me.drakeet.support.toast

import android.widget.Toast

/**
 * @author drakeet
 */
fun interface BadTokenListener {
	fun onBadTokenCaught( toast : Toast)
}
