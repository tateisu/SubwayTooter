package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.util.JsonObject

class TootRelationShip(parser:TootParser,src : JsonObject) {
	
	// Target account id
	// MisskeyのユーザリレーションはuserIdを含まないので後から何か設定する必要がある
	var id : EntityId
	
	// Whether the authorized user is currently following the target account.
	// maybe faked in response of follow-request.
	val following : Boolean
	
	// Whether the authorized user is currently being followed by the target account.
	val followed_by : Boolean
	
	// Whether the authorized user is currently blocking the target account.
	val blocking : Boolean

	// misskeyとMastodon 2.8.0以降
	val blocked_by : Boolean
	
	// Whether the authorized user is currently muting the target account.
	val muting : Boolean
	
	// Whether the authorized user has requested to follow the target account.
	// maybe true while follow-request is progress on server, even if the user is not locked.
	val requested : Boolean
	
	// (mastodon 2.1 or later) per-following-user setting.
	// Whether the boosts from target account will be shown on authorized user's home TL.
	val showing_reblogs : Int
	
	// 「プロフィールで紹介する」「プロフィールから外す」
	val endorsed : Boolean
	
	// misskey用
	val requested_by : Boolean
	
	// (Mastodon 3.2)
	var note : String? = null

	init {
		
		if( parser.serviceType == ServiceType.MISSKEY){
			this.id = EntityId.DEFAULT
			
			following = src.optBoolean("isFollowing")
			followed_by = src.optBoolean("isFollowed")
			muting = src.optBoolean("isMuted")
			blocking = src.optBoolean("isBlocking")
			blocked_by = src.optBoolean("isBlocked")
			requested = src.optBoolean("hasPendingFollowRequestFromYou")
			requested_by = src.optBoolean("hasPendingFollowRequestToYou")
			
			endorsed = false
			showing_reblogs = UserRelation.REBLOG_UNKNOWN
			
		}else{
			this.id = EntityId.mayDefault( src.string("id") )
			
			var ov = src["following"]
			if(ov is JsonObject) {
				// https://github.com/tootsuite/mastodon/issues/5856
				// 一部の開発版ではこうなっていた
				
				this.following = true
				
				ov = ov["reblogs"]
				if(ov is Boolean) {
					this.showing_reblogs = if(ov) UserRelation.REBLOG_SHOW else UserRelation.REBLOG_HIDE
				} else {
					this.showing_reblogs = UserRelation.REBLOG_UNKNOWN
				}
			} else {
				// 2.0 までの挙動
				this.following = if(ov is Boolean) ov else false
				
				// 2.1 の挙動
				ov = src["showing_reblogs"]
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
			this.endorsed = src.optBoolean("endorsed")
			this.note = src.optString( "note")
			
			// https://github.com/tootsuite/mastodon/commit/9745de883b198375ba23f7fde879f6d75ce2df0f
			// Mastodon 2.8.0から
			this.blocked_by = src.optBoolean("blocked_by")
			
			requested_by = false
		}
		
	}
}
