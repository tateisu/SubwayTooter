package jp.juggler.subwaytooter.streaming

import jp.juggler.subwaytooter.Column


fun Column.getStreamingStatus() =
    app_state.streamManager.getStreamingStatus(access_info, internalId)
        ?: StreamIndicatorState.NONE

fun Column.canSpeech() =
    canStreaming() && !isNotificationColumn
