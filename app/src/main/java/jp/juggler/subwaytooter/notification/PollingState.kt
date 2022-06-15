package jp.juggler.subwaytooter.notification

enum class PollingState(val desc: String) {
    PrepareInstallId("preparing install id"),
    CheckNetworkConnection("check network connection"),
    CheckServerInformation("check server information"),
    CheckPushSubscription("check push subscription"),
    CheckNotifications("check notifications"),
    Complete("complete"),
    Error("error"),
    Cancelled("cancelled"),
    Timeout("timeout"),
    ;

    companion object {
        val valuesCache = values()
    }
}
