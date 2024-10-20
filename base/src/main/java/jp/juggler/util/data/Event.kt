package jp.juggler.util.data

import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Objects

/**
 * one-time consumed event
 */
class Event<T : Any?>(private val data: T) {
    private var isConsumed = false

    fun get(): T? = when {
        isConsumed -> null
        else -> {
            isConsumed = true
            data
        }
    }

    override fun hashCode() = Objects.hash(isConsumed, data)

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is Event<*> -> false
        else -> isConsumed == other.isConsumed &&
                data == other.data
    }
}

fun <T : Any?> eventFlow() = MutableStateFlow<Event<T>?>(null)

fun <T : Any?> MutableStateFlow<Event<T>?>.setEvent(newValue: T?) {
    value = newValue?.let { Event(it) }
}
