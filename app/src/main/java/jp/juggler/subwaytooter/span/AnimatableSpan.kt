package jp.juggler.subwaytooter.span

interface AnimatableSpanInvalidator {
    val timeFromStart: Long
    fun delayInvalidate(delay: Long)
    fun requestLayout()
}

interface AnimatableSpan {

    fun setInvalidateCallback(
        drawTargetTag: Any,
        invalidateCallback: AnimatableSpanInvalidator
    )
}
