package jp.juggler.subwaytooter.streaming

import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.util.JsonArray

interface StreamCallback {
    fun onStreamStatusChanged(status: StreamStatus)

    fun onTimelineItem(item: TimelineItem, channelId: String?, stream: JsonArray?)
    fun onEmojiReactionNotification(notification: TootNotification)
    fun onEmojiReactionEvent(reaction: TootReaction)
    fun onNoteUpdated(ev: MisskeyNoteUpdate, channelId: String?)
    fun onAnnouncementUpdate(item: TootAnnouncement)
    fun onAnnouncementDelete(id: EntityId)
    fun onAnnouncementReaction(reaction: TootReaction)
}
