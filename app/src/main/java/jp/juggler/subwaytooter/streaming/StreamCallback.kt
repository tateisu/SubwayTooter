package jp.juggler.subwaytooter.streaming

import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.MisskeyNoteUpdate
import jp.juggler.subwaytooter.api.entity.TimelineItem
import jp.juggler.subwaytooter.api.entity.TootAnnouncement
import jp.juggler.util.JsonArray

interface StreamCallback {
    fun onStreamStatusChanged(status: StreamStatus)

    fun onTimelineItem(item: TimelineItem, channelId: String?,stream: JsonArray?)
    fun onNoteUpdated(ev: MisskeyNoteUpdate, channelId: String?)
    fun onAnnouncementUpdate(item: TootAnnouncement)
    fun onAnnouncementDelete(id: EntityId)
    fun onAnnouncementReaction(reaction: TootAnnouncement.Reaction)
}
