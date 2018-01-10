package jp.juggler.subwaytooter.api.entity

import org.json.JSONObject

class TootTag(
	val name : String, // The hashtag, not including the preceding #
	val url : String // The URL of the hashtag
) {
	constructor(src : JSONObject):this(
		name = src.notEmptyOrThrow("name"),
		url = src.notEmptyOrThrow("url")
	)
}
