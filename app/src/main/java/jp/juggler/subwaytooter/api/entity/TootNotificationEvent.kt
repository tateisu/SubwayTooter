package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.entity.TootNotificationEventType.Companion.toTootNotificationEventType
import jp.juggler.util.data.JsonObject

class TootNotificationEvent(
    val id: EntityId,
    val type: TootNotificationEventType,
    val purged: Boolean? = null,
    val targetName: String? = null,
    val followersCount: Int? = null,
    val followingCount: Int? = null,
    val timeCreatedAt: Long,

    ) {
    companion object {
        fun JsonObject.parseTootNotififcationEvent() = string("type")?.let {
            TootNotificationEvent(
                id = EntityId.mayDefault(string("id")),
                type = it.toTootNotificationEventType(),
                purged = boolean("purged"),
                targetName = string("target_name"),
                followersCount = int("followers_count"),
                followingCount = int("following_count"),
                timeCreatedAt = TootStatus.parseTime(string("created_at")),
            )
        }
    }
}
