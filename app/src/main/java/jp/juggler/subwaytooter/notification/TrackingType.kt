package jp.juggler.subwaytooter.notification

enum class TrackingType(
    val str: String,
    val typeName: String,
) {
    All("all", PullNotification.TRACKING_NAME_DEFAULT),
    Reply("reply", PullNotification.TRACKING_NAME_REPLY),
    NotReply("notReply", PullNotification.TRACKING_NAME_DEFAULT);

    companion object {
        private val valuesCache = values()
        fun parseStr(str: String?) = valuesCache.firstOrNull { it.str == str } ?: All
    }
}
