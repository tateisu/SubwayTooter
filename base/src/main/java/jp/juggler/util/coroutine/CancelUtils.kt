package jp.juggler.util.coroutine

fun cancellationException(message: String? = null) =
    kotlin.coroutines.cancellation.CancellationException(message)

fun cancellationException(cause: Throwable, message: String? = null) =
    kotlin.coroutines.cancellation.CancellationException(message, cause)
