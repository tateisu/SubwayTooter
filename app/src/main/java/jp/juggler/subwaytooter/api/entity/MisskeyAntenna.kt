package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.JsonArray
import jp.juggler.util.JsonObject

class MisskeyAntenna(src : JsonObject) :TimelineItem(){
	
	private val timeCreatedAt:Long // "2020-02-19T09:08:41.929Z"

	val id: EntityId
	
	val name:String
	
	val src :String
	//	"src":"all",
	//	"src":"group",
	//	"src":"home",
	//	"src":"list",
	//	"src":"users",
	
	private val keywords: Array<Array<String>>
//	"keywords":[[""]],
//	"keywords":[[""]],
//	"keywords":[[""]],
//	"keywords":[["Misskey"],["MisskeyApi"],["API"],["Subway"]],
//	"keywords":[["Subway","Tooter"],["Subway"],["Tooter"]],
//	"keywords":[["test"]],

	// src=="group" の場合。他はnull
	private val userGroupId : EntityId?
	
	// src=="list" の場合。他はnull
	private val userListId : EntityId?
	
	val users : Array<String>
//	"users":[""],
//	"users":["@tateisu","@syuilo"],
	
	private val caseSensitive:Boolean
	private val hasUnreadNote:Boolean
	private val notify: Boolean
	private val withFile : Boolean
	private val withReplies : Boolean
	
	init{
		timeCreatedAt = TootStatus.parseTime(src.string("createdAt"))
		id = EntityId(src.stringOrThrow("id"))
		name = src.string("name") ?: "?"
		this.src = src.string("src") ?: "?"
		
		keywords = src.jsonArray("keywords")?.mapNotNull{line->
			if(line is JsonArray){
				line.map{col -> col.toString()}.toTypedArray()
			}else{
				null
			}
		}?.toTypedArray() ?: emptyArray()
		
		// src=="group" の場合。他はnull
		userGroupId = EntityId.mayNull(src.string("userGroupId"))
		
		userListId = EntityId.mayNull(src.string("userListId"))
		
		users = src.jsonArray("users")
			?.mapNotNull{ it.toString() }
			?.toTypedArray()
			?: emptyArray()
		
		caseSensitive = src.boolean("caseSensitive") ?: false
		hasUnreadNote = src.boolean("hasUnreadNote") ?: false
		notify = src.boolean("notify") ?: false
		withFile = src.boolean("withFile") ?: false
		withReplies = src.boolean("withReplies") ?: false
	}
}