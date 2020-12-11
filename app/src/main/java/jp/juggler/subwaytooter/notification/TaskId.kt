package jp.juggler.subwaytooter.notification

// タスクID
enum class TaskId(val int: Int) {

    Polling(1),
    DataInjected(2),
    Clear(3),
    AppDataImportBefore(4),
    AppDataImportAfter(5),
    FcmDeviceToken(6),
    FcmMessage(7),
    BootCompleted(8),
    PackageReplaced(9),
    NotificationDelete(10),
    NotificationClick(11),
    AccountUpdated(12),
    ResetTrackingState(13),
    ;

    companion object {
        fun from(int: Int) = values().firstOrNull { it.int == int }
    }
}