package jp.juggler.subwaytooter.api.entity

import android.view.Gravity

class TootMessageHolder(
	val text: String,
	val gravity: Int = Gravity.CENTER_HORIZONTAL,
) : TimelineItem()
