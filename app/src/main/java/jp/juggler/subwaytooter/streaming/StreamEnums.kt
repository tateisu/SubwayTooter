package jp.juggler.subwaytooter.streaming

import jp.juggler.subwaytooter.Column

// ストリーミング接続の状態
enum class StreamStatus {
    Closed,
    Connecting,
    Open,
}

// インジケータの状態
enum class StreamIndicatorState {
    NONE,
    REGISTERED, // registered, but not listening
    LISTENING,
}

fun Column.getStreamingStatus() =
    app_state.streamManager.getStreamingStatus(access_info, internalId)
        ?: StreamIndicatorState.NONE
