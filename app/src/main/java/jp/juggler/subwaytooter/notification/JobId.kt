package jp.juggler.subwaytooter.notification

// ジョブID
enum class JobId(val int: Int) {

    // polling notifications periodically
    Polling(1),

    // task added by application
    Task(2),

    // invoked by push messaging
    Push(3),
    ;

    companion object {
        fun from(int: Int) = values().firstOrNull { it.int == int }
    }
}
