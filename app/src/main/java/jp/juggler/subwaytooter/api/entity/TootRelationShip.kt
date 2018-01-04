package jp.juggler.subwaytooter.api.entity

import org.json.JSONArray
import org.json.JSONObject

import java.util.ArrayList

import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils

class TootRelationShip {
	
	// Target account id
	var id : Long = 0
	
	// Whether the authorized user is currently following the target account.
	// maybe faked in response of follow-request.
	var following : Boolean = false
	
	// Whether the authorized user is currently being followed by the target account.
	var followed_by : Boolean = false
	
	// Whether the authorized user is currently blocking the target account.
	var blocking : Boolean = false
	
	// Whether the authorized user is currently muting the target account.
	var muting : Boolean = false
	
	// Whether the authorized user has requested to follow the target account.
	// maybe true while follow-request is progress on server, even if the user is not locked.
	var requested : Boolean = false
	
	// (mastodon 2.1 or later) per-following-user setting.
	// Whether the boosts from target account will be shown on authorized user's home TL.
	var showing_reblogs = UserRelation.REBLOG_UNKNOWN
	
	class List : ArrayList<TootRelationShip> {
		constructor() : super() {}
		
		constructor(capacity : Int) : super(capacity) {}
	}
	
	companion object {
		
		private val log = LogCategory("TootRelationShip")
		
		fun parse(src : JSONObject?) : TootRelationShip? {
			if(src == null) return null
			try {
				val dst = TootRelationShip()
				dst.id = Utils.optLongX(src, "id")
				
				var ov = src.opt("following")
				if(ov is JSONObject) {
					// https://github.com/tootsuite/mastodon/issues/5856
					// 一部の開発版ではこうなっていた
					
					dst.following = true
					
					ov = ov.opt("reblogs")
					if(ov is Boolean) {
						dst.showing_reblogs = if(ov) UserRelation.REBLOG_SHOW else UserRelation.REBLOG_HIDE
					} else {
						dst.showing_reblogs = UserRelation.REBLOG_UNKNOWN
					}
					
				} else {
					// 2.0 までの挙動
					dst.following = if(ov is Boolean) ov else false
					
					// 2.1 の挙動
					ov = src.opt("showing_reblogs")
					if(dst.following && ov is Boolean) {
						dst.showing_reblogs = if(ov) UserRelation.REBLOG_SHOW else UserRelation.REBLOG_HIDE
					} else {
						dst.showing_reblogs = UserRelation.REBLOG_UNKNOWN
					}
				}
				
				dst.followed_by = src.optBoolean("followed_by")
				dst.blocking = src.optBoolean("blocking")
				dst.muting = src.optBoolean("muting")
				dst.requested = src.optBoolean("requested")
				return dst
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "TootRelationShip.parse failed.")
				return null
			}
			
		}
		
		fun parseList(array : JSONArray?) : List {
			val result = List()
			if(array != null) {
				val array_size = array.length()
				result.ensureCapacity(array_size)
				for(i in 0 until array_size) {
					val src = array.optJSONObject(i) ?: continue
					val item = parse(src)
					if(item != null) result.add(item)
				}
			}
			return result
		}
	}
}
