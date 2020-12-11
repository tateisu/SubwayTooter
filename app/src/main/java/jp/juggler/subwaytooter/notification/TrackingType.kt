package jp.juggler.subwaytooter.notification

enum class TrackingType(
    val str: String,
    val typeName: String
) {
    All("all", NotificationHelper.TRACKING_NAME_DEFAULT),
    Reply("reply", NotificationHelper.TRACKING_NAME_REPLY),
    NotReply("notReply", NotificationHelper.TRACKING_NAME_DEFAULT);

    companion object {
        fun parseStr(str: String?) = values().firstOrNull { it.str == str } ?: All
    }
}
