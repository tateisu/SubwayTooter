package jp.juggler.subwaytooter.streaming

import jp.juggler.subwaytooter.Column
import jp.juggler.util.JsonObject
import java.lang.ref.WeakReference

class StreamSpec(
    column: Column,
    val streamPath: String,
    streamParam: JsonObject,
    val streamCallback: StreamCallback
) {
    val streamKey = streamParam.toString(indentFactor = 0, sort = true)

    val columnInternalId = column.internalId
    val refColumn = WeakReference(column)
}