package jp.juggler.util.coroutine

import kotlin.coroutines.Continuation
import kotlin.coroutines.resumeWithException

fun cancellationException(message: String? = null) =
    kotlin.coroutines.cancellation.CancellationException(message)

fun cancellationException(cause: Throwable, message: String? = null) =
    kotlin.coroutines.cancellation.CancellationException(message, cause)

fun  <T> Continuation<T>.resumeWithCancellationException(message: String? = null)=
    resumeWithException(cancellationException(message))
