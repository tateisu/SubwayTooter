package jp.juggler.subwaytooter.span

interface AnimatableSpanInvalidator {
    val timeFromStart: Long
    fun delayInvalidate(delay: Long)
}

interface AnimatableSpan {

    fun setInvalidateCallback(
        drawTargetTag: Any,
        invalidateCallback: AnimatableSpanInvalidator
    )
}
