package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.util.parseInt
import jp.juggler.subwaytooter.util.parseString
import org.json.JSONObject

class MisskeyNoteUpdate(src:JSONObject){
	
	enum class Type {
		REACTION,
		DELETED,
		VOTED
	}
	
	
	val noteId : EntityId
	val type: Type
	var reaction: String? = null
	var userId: EntityId? = null
	var deletedAt : Long? = null
	var choice : Int? = null

	init {
		noteId = EntityId.mayNull(src.parseString("id")) ?: error("MisskeyNoteUpdate: missing note id")

		val src2 = src.optJSONObject("body") ?: error("MisskeyNoteUpdate: missing body")

		val strType = src.parseString("type")
		when(strType) {
			"reacted" -> {
				type = Type.REACTION
				reaction = src2.parseString("reaction")
				userId = EntityId.mayDefault(src2.parseString("userId"))
			}
			
			"deleted" -> {
				type = Type.DELETED
				deletedAt = TootStatus.parseTime(src2.optString("deletedAt"))
			}
			
			"pollVoted" -> {
				type = Type.VOTED
				choice = src2.parseInt("choice")
				userId = EntityId.mayDefault(src2.parseString("userId"))
			}
			
			else -> error("MisskeyNoteUpdate: unknown type $strType")
		}
	}
}