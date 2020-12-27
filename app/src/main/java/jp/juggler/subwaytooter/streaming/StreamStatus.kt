package jp.juggler.subwaytooter.streaming

import jp.juggler.subwaytooter.Column

// ストリーミング接続の状態
enum class StreamStatus {
    Missing,
    Closed,
    Connecting,
    Open,
    Subscribed
}

fun Column.getStreamingStatus() =
    app_state.streamManager.getStreamStatus(this)
