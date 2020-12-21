package jp.juggler.subwaytooter.streaming

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
