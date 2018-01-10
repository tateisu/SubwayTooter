package jp.juggler.subwaytooter.api.entity

import org.json.JSONObject

import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.util.Utils

class TootRelationShip(src : JSONObject) {
	
	// Target account id
	val id : Long
	
	// Whether the authorized user is currently following the target account.
	// maybe faked in response of follow-request.
	val following : Boolean
	
	// Whether the authorized user is currently being followed by the target account.
	val followed_by : Boolean
	
	// Whether the authorized user is currently blocking the target account.
	val blocking : Boolean
	
	// Whether the authorized user is currently muting the target account.
	val muting : Boolean
	
	// Whether the authorized user has requested to follow the target account.
	// maybe true while follow-request is progress on server, even if the user is not locked.
	val requested : Boolean
	
	// (mastodon 2.1 or later) per-following-user setting.
	// Whether the boosts from target account will be shown on authorized user's home TL.
	val showing_reblogs : Int
	
	init {
		this.id = Utils.optLongX(src, "id")
		
		var ov = src.opt("following")
		if(ov is JSONObject) {
			// https://github.com/tootsuite/mastodon/issues/5856
			// 一部の開発版ではこうなっていた
			
			this.following = true
			
			ov = ov.opt("reblogs")
			if(ov is Boolean) {
				this.showing_reblogs = if(ov) UserRelation.REBLOG_SHOW else UserRelation.REBLOG_HIDE
			} else {
				this.showing_reblogs = UserRelation.REBLOG_UNKNOWN
			}
			
		} else {
			// 2.0 までの挙動
			this.following = if(ov is Boolean) ov else false
			
			// 2.1 の挙動
			ov = src.opt("showing_reblogs")
			if(this.following && ov is Boolean) {
				this.showing_reblogs = if(ov) UserRelation.REBLOG_SHOW else UserRelation.REBLOG_HIDE
			} else {
				this.showing_reblogs = UserRelation.REBLOG_UNKNOWN
			}
		}
		
		this.followed_by = src.optBoolean("followed_by")
		this.blocking = src.optBoolean("blocking")
		this.muting = src.optBoolean("muting")
		this.requested = src.optBoolean("requested")
	}
}
