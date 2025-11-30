package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.emoji.CustomEmoji
import jp.juggler.util.data.JsonObject

class MisskeyNoteUpdate(
    val noteId: EntityId,
    val type: Type,
    var reaction: String? = null,
    var userId: EntityId? = null,
    var deletedAt: Long? = null,
    var choice: Int? = null,
    var emoji: CustomEmoji? = null,
) {
    enum class Type {
        REACTION,
        UNREACTION,
        DELETED,
        VOTED
    }

    companion object {

        fun misskeyNoteUpdate(src: JsonObject): MisskeyNoteUpdate {

            val noteId = EntityId.mayNull(src.string("id"))
                ?: error("MisskeyNoteUpdate: missing note id")

            // root.body.body
            val body2 = src.jsonObject("body")
                ?: error("MisskeyNoteUpdate: missing body")

            val type: Type = when (val strType = src.string("type")) {
                "reacted" -> Type.REACTION
                "unreacted" -> Type.UNREACTION
                "deleted" -> Type.DELETED
                "pollVoted" -> Type.VOTED
                else -> error("MisskeyNoteUpdate: unknown type $strType")
            }

            return MisskeyNoteUpdate(
                noteId = noteId,
                type = type,
                reaction = body2.string("reaction"),
                userId = EntityId.mayNull(body2.string("userId")),
                deletedAt = body2.string("deletedAt")?.let { TootStatus.parseTime(it) },
                choice = body2.int("choice"),
                emoji = parseItem(body2.jsonObject("emoji"), CustomEmoji::decodeMisskey),
            )
        }
    }
}
