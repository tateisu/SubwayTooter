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
			EntityId.mayNull(src.parseString("id")) ?: error("MisskeyNoteUpdate: missing note id")
		
		val src2 = src.parseJsonObject("body") ?: error("MisskeyNoteUpdate: missing body")
		
		when(val strType = src.parseString("type")) {
			"reacted" -> {
				type = Type.REACTION
				reaction = src2.parseString("reaction")
				userId = EntityId.mayDefault(src2.parseString("userId"))
			}
			
			"unreacted" -> {
				type = Type.UNREACTION
				reaction = src2.parseString("reaction")
				userId = EntityId.mayDefault(src2.parseString("userId"))
			}
			
			"deleted" -> {
				type = Type.DELETED
				deletedAt = TootStatus.parseTime(src2.parseString("deletedAt"))
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