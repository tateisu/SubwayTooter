package jp.juggler.subwaytooter.span



interface AnimatableSpanInvalidator {
	val timeFromStart : Long
	fun delayInvalidate(delay : Long)
}

interface AnimatableSpan{
	
	fun setInvalidateCallback(
		draw_target_tag : Any,
		invalidate_callback : AnimatableSpanInvalidator
	)
}
