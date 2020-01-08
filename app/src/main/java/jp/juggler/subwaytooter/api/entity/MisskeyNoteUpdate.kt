package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.JsonObject

class MisskeyNoteUpdate(src : JsonObject) {
	
	enum class Type {
		REACTION,
		UNREACTION,
		DELETED,
		VOTED
	}
	
	val noteId : EntityId
	val type : Type
	var reaction : String? = null
	var userId : EntityId? = null
	var deletedAt : Long? = null
	var choice : Int? = null
	
	init {
		noteId =
			EntityId.mayNull(src.string("id")) ?: error("MisskeyNoteUpdate: missing note id")
		
		val src2 = src.jsonObject("body") ?: error("MisskeyNoteUpdate: missing body")
		
		when(val strType = src.string("type")) {
			"reacted" -> {
				type = Type.REACTION
				reaction = src2.string("reaction")
				userId = EntityId.mayDefault(src2.string("userId"))
			}
			
			"unreacted" -> {
				type = Type.UNREACTION
				reaction = src2.string("reaction")
				userId = EntityId.mayDefault(src2.string("userId"))
			}
			
			"deleted" -> {
				type = Type.DELETED
				deletedAt = TootStatus.parseTime(src2.string("deletedAt"))
			}
			
			"pollVoted" -> {
				type = Type.VOTED
				choice = src2.int("choice")
				userId = EntityId.mayDefault(src2.string("userId"))
			}
			
			else -> error("MisskeyNoteUpdate: unknown type $strType")
		}
	}
}