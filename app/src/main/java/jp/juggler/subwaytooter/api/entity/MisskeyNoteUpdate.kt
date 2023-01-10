package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.emoji.CustomEmoji
import jp.juggler.util.JsonObject
import jp.juggler.util.LogCategory

class MisskeyNoteUpdate(apDomain: Host, apiHost: Host, src: JsonObject) {
    companion object {
        private val log = LogCategory("MisskeyNoteUpdate")
    }

    enum class Type {
        REACTION,
        UNREACTION,
        DELETED,
        VOTED
    }

    val noteId: EntityId
    val type: Type
    var reaction: String? = null
    var userId: EntityId? = null
    var deletedAt: Long? = null
    var choice: Int? = null
    var emoji: CustomEmoji? = null

    init {
        noteId = EntityId.mayNull(src.string("id")) ?: error("MisskeyNoteUpdate: missing note id")

        // root.body.body
        val body2 = src.jsonObject("body") ?: error("MisskeyNoteUpdate: missing body")

        when (val strType = src.string("type")) {
            "reacted" -> {
                type = Type.REACTION
                reaction = body2.string("reaction")
                userId = EntityId.mayDefault(body2.string("userId"))
                emoji = body2.jsonObject("emoji")?.let {
                    try {
                        CustomEmoji.decodeMisskey(apDomain, apiHost, it)
                    } catch (ex: Throwable) {
                        log.e(ex, "can't parse custom emoji.")
                        null
                    }
                }
            }

            "unreacted" -> {
                type = Type.UNREACTION
                reaction = body2.string("reaction")
                userId = EntityId.mayDefault(body2.string("userId"))
            }

            "deleted" -> {
                type = Type.DELETED
                deletedAt = TootStatus.parseTime(body2.string("deletedAt"))
            }

            "pollVoted" -> {
                type = Type.VOTED
                choice = body2.int("choice")
                userId = EntityId.mayDefault(body2.string("userId"))
            }

            else -> error("MisskeyNoteUpdate: unknown type $strType")
        }
    }
}
