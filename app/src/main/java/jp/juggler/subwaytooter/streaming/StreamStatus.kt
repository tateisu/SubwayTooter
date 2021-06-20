package jp.juggler.subwaytooter.streaming

import jp.juggler.subwaytooter.Column
import jp.juggler.subwaytooter.canStreamingType

// ストリーミング接続の状態
enum class StreamStatus {
    Missing,
    Closed,
    Connecting,
    Open,
    Subscribed
}

fun Column.getStreamingStatus() = when {
    canStreamingType() && !dontStreaming -> appState.streamManager.getStreamStatus(this)
    else -> StreamStatus.Missing
}
