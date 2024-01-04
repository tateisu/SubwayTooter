package jp.juggler.subwaytooter.notification

enum class PollingState(val desc: String) {
    Complete("complete"),
    Cancelled("cancelled"),
    Error("error"),
    Timeout("timeout"),
    PrepareInstallId("preparing install id"),
    CheckNetworkConnection("check network connection"),
    CheckServerInformation("check server information"),
    CheckPushSubscription("check push subscription"),
    CheckNotifications("check notifications"),
    ;
}
