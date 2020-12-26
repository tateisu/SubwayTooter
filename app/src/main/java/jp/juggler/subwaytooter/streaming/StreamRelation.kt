package jp.juggler.subwaytooter.streaming

import jp.juggler.subwaytooter.Column
import jp.juggler.util.LogCategory
import java.lang.ref.WeakReference

class StreamRelation(
    column: Column,
    val spec: StreamSpec
) {
    companion object{
        private val log =LogCategory("StreamDestination")
    }

    val columnInternalId = column.internalId

    val refColumn = WeakReference(column)
    val refCallback = WeakReference(column.streamCallback)

    fun canStartStreaming(): Boolean {
        return when (val column = refColumn.get()) {
            null -> {
                log.w("${spec.name} canStartStreaming: missing column.")
                false
            }
            else -> column.canStartStreaming()
        }
    }
}

fun Column.getStreamDestination() =
    streamSpec?.let { StreamRelation( spec = it, column = this ) }
