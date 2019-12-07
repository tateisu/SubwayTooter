package jp.juggler.subwaytooter

import jp.juggler.subwaytooter.Column.Companion.READ_LIMIT
import jp.juggler.subwaytooter.Column.Companion.log
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.api.syncAccountByAcct
import jp.juggler.util.encodePercent
import jp.juggler.util.jsonArray
import jp.juggler.util.parseString
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

internal inline fun <reified T : TimelineItem> addAll(
	dstArg : ArrayList<TimelineItem>?,
	src : List<T>
) : ArrayList<TimelineItem> {
	val dst = dstArg ?: ArrayList(src.size)
	dst.addAll(src)
	return dst
}

internal fun JSONObject.putMisskeyUntil(column : Column, id : EntityId?) : JSONObject {
	if(id != null) {
		if(column.useDate) {
			put("untilDate", id.toString().toLong())
		} else {
			put("untilId", id.toString())
		}
	}
	return this
}

internal fun JSONObject.putMisskeySince(column : Column, id : EntityId?) : JSONObject {
	if(id != null) {
		if(column.useDate) {
			put("sinceDate", id.toString().toLong())
		} else {
			put("sinceId", id.toString())
		}
	}
	return this
}

internal fun JSONObject.addRangeMisskey(column : Column, bBottom : Boolean) : JSONObject {
	if(bBottom) {
		putMisskeyUntil(column, column.idOld)
	} else {
		putMisskeySince(column, column.idRecent)
	}
	return this
}

internal fun JSONObject.addMisskeyNotificationFilter(column : Column) : JSONObject {
	when(column.quick_filter) {
		Column.QUICK_FILTER_ALL -> {
			val excludeList = JSONArray()
			
			// Misskeyのお気に入りは通知されない
			// if(dont_show_favourite) ...
			
			if(column.dont_show_boost) {
				excludeList.put("renote")
				excludeList.put("quote")
			}
			if(column.dont_show_follow) {
				excludeList.put("follow")
				excludeList.put("receiveFollowRequest")
			}
			if(column.dont_show_reply) {
				excludeList.put("mention")
				excludeList.put("reply")
			}
			if(column.dont_show_reaction) {
				excludeList.put("reaction")
			}
			if(column.dont_show_vote) {
				excludeList.put("poll_vote")
			}
			if(excludeList.length() > 0) put("excludeTypes", excludeList)
		}
		
		// QUICK_FILTER_FAVOURITE // misskeyはお気に入りの通知はない
		Column.QUICK_FILTER_BOOST -> put("includeTypes", jsonArray("renote", "quote"))
		Column.QUICK_FILTER_FOLLOW -> put(
			"includeTypes",
			jsonArray("follow", "receiveFollowRequest")
		)
		Column.QUICK_FILTER_MENTION -> put("includeTypes", jsonArray("mention", "reply"))
		Column.QUICK_FILTER_REACTION -> put("includeTypes", jsonArray("reaction"))
		Column.QUICK_FILTER_VOTE -> put("includeTypes", jsonArray("poll_vote"))
	}
	
	return this
}

internal fun JSONObject.putMisskeyParamsTimeline(column : Column) : JSONObject {
	if(column.with_attachment && ! column.with_highlight) {
		put("mediaOnly", true)
		put("withMedia", true)
		put("withFiles", true)
		put("media", true)
	}
	return this
}

internal fun Column.makeHashtagAcctUrl(client : TootApiClient) : String? {
	return if(isMisskey) {
		// currently not supported
		null
	} else {
		if(profile_id == null) {
			val (result, whoRef) = client.syncAccountByAcct(access_info, hashtag_acct)
			result ?: return null // cancelled.
			if( whoRef == null){
				log.w("makeHashtagAcctUrl: ${result.error ?: "?"}")
				return null
			}
			profile_id = whoRef.get().id
		}
		
		val sb = StringBuilder("/api/v1/accounts/${profile_id}/statuses")
			.append("?limit=").append(Column.READ_LIMIT)
			.append("&tagged=").append(hashtag.encodePercent())
		
		if(with_attachment) sb.append("&only_media=true")
		if(instance_local) sb.append("&local=true")
		
		sb.append(makeHashtagExtraQuery())
		
		sb.toString()
	}
}

internal fun Column.makeMisskeyBaseParameter(parser : TootParser?) : JSONObject =
	access_info
		.putMisskeyApiToken(JSONObject())
		.apply {
			if(access_info.isMisskey) {
				if(parser != null) parser.serviceType = ServiceType.MISSKEY
				put("limit", 40)
			}
		}

internal fun Column.makeMisskeyParamsUserId(parser : TootParser) : JSONObject =
	makeMisskeyBaseParameter(parser).put("userId", profile_id.toString())

internal fun Column.makeMisskeyTimelineParameter(parser : TootParser) =
	makeMisskeyBaseParameter(parser).putMisskeyParamsTimeline(this)

internal fun Column.makeMisskeyParamsProfileStatuses(parser : TootParser) =
	makeMisskeyParamsUserId(parser).apply {
		putMisskeyParamsTimeline(this@makeMisskeyParamsProfileStatuses)
		if(! dont_show_reply) put("includeReplies", true)
		if(! dont_show_boost) put("includeMyRenotes", true)
	}

internal fun Column.makePublicLocalUrl() : String {
	return when {
		access_info.isMisskey -> "/api/notes/local-timeline"
		with_attachment -> "${Column.PATH_LOCAL}&only_media=true" // mastodon 2.3 or later
		else -> Column.PATH_LOCAL
	}
}

internal fun Column.makeDomainTimelineUrl() : String{
	val base = "/api/v1/timelines/public?limit=$READ_LIMIT&domain=$instance_uri"
	return when {
		access_info.isMisskey -> "/api/notes/local-timeline"
		with_attachment -> "$base&only_media=true"
		else -> base
	}
}

internal fun Column.makeMisskeyHybridTlUrl() : String {
	return when {
		access_info.isMisskey -> "/api/notes/hybrid-timeline"
		with_attachment -> "${Column.PATH_LOCAL}&only_media=true" // mastodon 2.3 or later
		else -> Column.PATH_LOCAL
	}
}

internal fun Column.makePublicFederateUrl() : String {
	return when {
		access_info.isMisskey -> "/api/notes/global-timeline"
		with_attachment -> "${Column.PATH_TL_FEDERATE}&only_media=true"
		else -> Column.PATH_TL_FEDERATE
	}
}

internal fun Column.makeHomeTlUrl() : String {
	return when {
		access_info.isMisskey -> "/api/notes/timeline"
		with_attachment -> "${Column.PATH_HOME}&only_media=true"
		else -> Column.PATH_HOME
	}
}

internal fun Column.makeNotificationUrl(
	client : TootApiClient,
	fromAcct : String? = null
) : String {
	return when {
		access_info.isMisskey -> "/api/i/notifications"
		
		else -> {
			val sb = StringBuilder(Column.PATH_NOTIFICATIONS) // always contain "?limit=XX"
			when(val quick_filter = quick_filter) {
				Column.QUICK_FILTER_ALL -> {
					if(dont_show_favourite) sb.append("&exclude_types[]=favourite")
					if(dont_show_boost) sb.append("&exclude_types[]=reblog")
					if(dont_show_follow) sb.append("&exclude_types[]=follow")
					if(dont_show_reply) sb.append("&exclude_types[]=mention")
					if(dont_show_vote) sb.append("&exclude_types[]=poll")
				}
				
				else -> {
					if(quick_filter != Column.QUICK_FILTER_FAVOURITE) sb.append("&exclude_types[]=favourite")
					if(quick_filter != Column.QUICK_FILTER_BOOST) sb.append("&exclude_types[]=reblog")
					if(quick_filter != Column.QUICK_FILTER_FOLLOW) sb.append("&exclude_types[]=follow")
					if(quick_filter != Column.QUICK_FILTER_MENTION) sb.append("&exclude_types[]=mention")
				}
			}
			
			if(fromAcct?.isNotEmpty() == true) {
				if(profile_id == null) {
					val (result, whoRef) = client.syncAccountByAcct(access_info, hashtag_acct)
					if(result != null) {
						whoRef ?: error(result.error ?: "unknown error")
						profile_id = whoRef.get().id
					}
				}
				if(profile_id != null) {
					sb.append("&account_id=").append(profile_id.toString())
				}
			}
			
			// reaction,voteはmastodonにはない
			sb.toString()
		}
	}
}

internal fun Column.makeListTlUrl() : String {
	return if(isMisskey) {
		"/api/notes/user-list-timeline"
	} else {
		String.format(Locale.JAPAN, Column.PATH_LIST_TL, profile_id)
	}
}

internal fun Column.makeHashtagExtraQuery() : String {
	val sb = StringBuilder()
	hashtag_any.split(" ").filter { it.isNotEmpty() }.forEach {
		sb.append("&any[]=").append(it.encodePercent())
	}
	
	hashtag_all.split(" ").filter { it.isNotEmpty() }.forEach {
		sb.append("&all[]=").append(it.encodePercent())
	}
	
	hashtag_none.split(" ").filter { it.isNotEmpty() }.forEach {
		sb.append("&none[]=").append(it.encodePercent())
	}
	return sb.toString()
}

internal fun Column.makeHashtagUrl() : String {
	return if(isMisskey) {
		"/api/notes/search_by_tag"
	} else {
		// hashtag : String // 先頭の#を含まない
		val sb = StringBuilder("/api/v1/timelines/tag/")
			.append(hashtag.encodePercent())
			.append("?limit=").append(Column.READ_LIMIT)
		
		if(with_attachment) sb.append("&only_media=true")
		if(instance_local) sb.append("&local=true")
		
		sb
			.append(makeHashtagExtraQuery())
			.toString()
	}
}

internal fun Column.makeHashtagParams(parser : TootParser) : JSONObject {
	return makeMisskeyTimelineParameter(parser)
		.put("tag", hashtag)
		.put("limit", Column.MISSKEY_HASHTAG_LIMIT)
}

// mastodon用
internal fun Column.makeProfileStatusesUrl(profile_id : EntityId?) : String {
	var path = "/api/v1/accounts/$profile_id/statuses?limit=${Column.READ_LIMIT}"
	if(with_attachment && ! with_highlight) path += "&only_media=1"
	if(dont_show_boost) path += "&exclude_reblogs=1"
	if(dont_show_reply) path += "&exclude_replies=1"
	return path
}

internal val misskeyArrayFinderUsers = { it : JSONObject ->
	it.optJSONArray("users")
}

internal val misskeyFollowingParser =
	{ parser : TootParser, jsonArray : JSONArray ->
		val dst = ArrayList<TootAccountRef>()
		for(i in 0 until jsonArray.length()) {
			val src = jsonArray.optJSONObject(i) ?: continue
			
			val accountRef = TootAccountRef.mayNull(
				parser,
				parser.account(src.optJSONObject("followee"))
			) ?: continue
			
			val relationId = EntityId.mayNull(src.parseString("id")) ?: continue
			
			accountRef._orderId = relationId
			
			dst.add(accountRef)
		}
		dst
	}

internal val misskeyFollowersParser =
	{ parser : TootParser, jsonArray : JSONArray ->
		val dst = ArrayList<TootAccountRef>()
		for(i in 0 until jsonArray.length()) {
			val src = jsonArray.optJSONObject(i) ?: continue
			
			val accountRef = TootAccountRef.mayNull(
				parser,
				parser.account(src.optJSONObject("follower"))
			) ?: continue
			
			val relationId = EntityId.mayNull(src.parseString("id")) ?: continue
			
			accountRef._orderId = relationId
			
			dst.add(accountRef)
		}
		dst
	}

internal val misskeyCustomParserFollowRequest =
	{ parser : TootParser, jsonArray : JSONArray ->
		val dst = ArrayList<TootAccountRef>()
		for(i in 0 until jsonArray.length()) {
			val src = jsonArray.optJSONObject(i) ?: continue
			
			val accountRef = TootAccountRef.mayNull(
				parser,
				parser.account(src.optJSONObject("follower"))
			) ?: continue
			
			val requestId = EntityId.mayNull(src.parseString("id")) ?: continue
			
			accountRef._orderId = requestId
			
			dst.add(accountRef)
		}
		dst
	}

internal val misskeyCustomParserMutes =
	{ parser : TootParser, jsonArray : JSONArray ->
		val dst = ArrayList<TootAccountRef>()
		for(i in 0 until jsonArray.length()) {
			val src = jsonArray.optJSONObject(i) ?: continue
			
			val accountRef = TootAccountRef.mayNull(
				parser,
				parser.account(src.optJSONObject("mutee"))
			) ?: continue
			
			val requestId = EntityId.mayNull(src.parseString("id")) ?: continue
			
			accountRef._orderId = requestId
			
			dst.add(accountRef)
		}
		dst
	}

internal val misskeyCustomParserBlocks =
	{ parser : TootParser, jsonArray : JSONArray ->
		val dst = ArrayList<TootAccountRef>()
		for(i in 0 until jsonArray.length()) {
			val src = jsonArray.optJSONObject(i) ?: continue
			
			val accountRef = TootAccountRef.mayNull(
				parser,
				parser.account(src.optJSONObject("blockee"))
			) ?: continue
			
			val requestId = EntityId.mayNull(src.parseString("id")) ?: continue
			
			accountRef._orderId = requestId
			
			dst.add(accountRef)
		}
		dst
	}
internal val misskeyCustomParserFavorites =
	{ parser : TootParser, jsonArray : JSONArray ->
		val dst = ArrayList<TootStatus>()
		for(i in 0 until jsonArray.length()) {
			val src = jsonArray.optJSONObject(i) ?: continue
			val note = parser.status(src.optJSONObject("note")) ?: continue
			val favId = EntityId.mayNull(src.parseString("id")) ?: continue
			note.favourited = true
			note._orderId = favId
			dst.add(note)
		}
		
		dst
	}

