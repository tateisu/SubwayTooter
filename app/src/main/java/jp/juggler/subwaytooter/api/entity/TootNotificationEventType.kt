package jp.juggler.subwaytooter.api.entity

sealed class TootNotificationEventType(val code: String) {
    class Unknown(c: String) : TootNotificationEventType(c)
    data object DomainBlock : TootNotificationEventType("domain_block")

    companion object {
        val allKnown: List<TootNotificationEventType> by lazy {
            TootNotificationEventType::class.sealedSubclasses.mapNotNull { it.objectInstance }
        }

        val map by lazy {
            allKnown.associateBy { it.code }
        }

        fun String?.toTootNotificationEventType(): TootNotificationEventType =
            this?.let { map[it] } ?: Unknown(this ?: "(null)")
    }
}
