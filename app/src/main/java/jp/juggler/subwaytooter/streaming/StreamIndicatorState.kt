package jp.juggler.subwaytooter.streaming

enum class StreamIndicatorState {
    NONE,
    REGISTERED, // registered, but not listening
    LISTENING,
}
