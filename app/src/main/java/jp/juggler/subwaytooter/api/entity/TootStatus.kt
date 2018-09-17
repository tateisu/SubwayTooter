package jp.juggler.subwaytooter.api.entity

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootAccountMap
import jp.juggler.subwaytooter.api.TootApiClient

import org.json.JSONObject

import java.lang.ref.WeakReference
import java.util.regex.Pattern

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.table.HighlightWord
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.*
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

@Suppress("MemberVisibilityCanPrivate")
class TootStatus(parser : TootParser, src : JSONObject) : TimelineItem() {
	
	val json : JSONObject
	
	// A Fediverse-unique resource ID
	// MSP から取得したデータだと uri は提供されずnullになる
	val uri : String?
	
	// URL to the status page (can be remote)
	// ブーストだとnullになる
	val url : String?
	
	// 投稿元タンスのホスト名
	val host_original : String
		get() = account.host
	
	// 取得タンスのホスト名。トゥート検索サービスでは提供されずnullになる
	val host_access : String?
	
	// ステータスID。
	// host_access が null の場合は投稿元タンスでのIDかもしれない。
	// 取得に失敗するとINVALID_IDになる
	// Misskeyでは文字列のID。
	val id : EntityId
	
	// misskey ではページネーションにIDではなくエポック秒を使う
	internal var _orderId : EntityId
	
	override fun getOrderId() = _orderId
	
	// The TootAccount which posted the status
	val accountRef : TootAccountRef
	
	val account : TootAccount
		get() = TootAccountMap.find(accountRef.mapId)
	
	//The number of reblogs for the status
	var reblogs_count : Long? = null  // アプリから変更する。検索サービスでは提供されない(null)
	
	//The number of favourites for the status
	var favourites_count : Long? = null // アプリから変更する。検索サービスでは提供されない(null)
	
	//	Whether the authenticated user has reblogged the status
	var reblogged : Boolean = false // アプリから変更する
	
	//	Whether the authenticated user has favourited the status
	var favourited : Boolean = false  // アプリから変更する
	
	// Whether the authenticated user has muted the conversation this status from
	var muted : Boolean = false // アプリから変更する
	
	// 固定されたトゥート
	var pinned : Boolean = false  // アプリから変更する
	
	//Whether media attachments should be hidden by default
	val sensitive : Boolean
	
	// The detected language for the status, if detected
	private val language : String?
	
	//If not empty, warning text that should be displayed before the actual content
	val spoiler_text : String?
	val decoded_spoiler_text : Spannable
	
	//	Body of the status; this will contain HTML (remote HTML already sanitized)
	val content : String?
	val decoded_content : Spannable
	
	//Application from which the status was posted
	val application : TootApplication?
	
	val custom_emojis : HashMap<String, CustomEmoji>?
	
	val profile_emojis : HashMap<String, NicoProfileEmoji>?
	
	//	The time the status was created
	private val created_at : String?
	
	//	null or the ID of the status it replies to
	val in_reply_to_id : EntityId?
	
	//	null or the ID of the account it replies to
	private val in_reply_to_account_id : EntityId?
	
	//	null or the reblogged Status
	val reblog : TootStatus?
	
	//One of: public, unlisted, private, direct
	val visibility : TootVisibility
	
	val misskeyVisibleIds : ArrayList<String>?
	
	//	An array of Attachments
	val media_attachments : ArrayList<TootAttachmentLike>?
	
	//	An array of Mentions
	var mentions : ArrayList<TootMention>? = null
	
	//An array of Tags
	var tags : ArrayList<TootTag>? = null
	
	// public Spannable decoded_tags;
	var decoded_mentions : Spannable = EMPTY_SPANNABLE
	
	var conversation_main : Boolean = false
	
	var enquete : NicoEnquete? = null
	
	//
	var replies_count : Long? = null
	
	var viaMobile : Boolean = false
	
	var reactionCounts : HashMap<String, Int>? = null
	var myReaction :String? =null
	
	var reply : TootStatus?
	
	val serviceType :ServiceType
	
	val deletedAt : String?
	val time_deleted_at : Long
	
	///////////////////////////////////////////////////////////////////
	// 以下はentityから取得したデータではなく、アプリ内部で使う
	
	class AutoCW(
		var refActivity : WeakReference<Any>? = null,
		var cell_width : Int = 0,
		var decoded_spoiler_text : Spannable? = null,
		var originalLineCount : Int = 0
	)
	
	// アプリ内部で使うワークエリア
	var auto_cw : AutoCW? = null
	
	// 会話の流れビューで後から追加する
	var card : TootCard? = null
	
	var highlight_sound : HighlightWord? = null
	
	var hasHighlight : Boolean = false
	
	val time_created_at : Long
	
	////////////////////////////////////////////////////////
	
	init {
		this.json = src
		this.serviceType = parser.serviceType
		
		if(parser.serviceType == ServiceType.MISSKEY) {
			val instance = parser.linkHelper.host
			val misskeyId = src.parseString("id")
			this.host_access = parser.linkHelper.host
			
			val uri = src.parseString("uri")
			if( uri != null ){
				this.uri = uri
				this.url = uri
			}else {
				this.uri = "https://$instance/notes/$misskeyId"
				this.url = "https://$instance/notes/$misskeyId"
			}
			
			this.created_at = src.parseString("createdAt")
			this.time_created_at = parseTime(this.created_at)
			this.id = EntityIdString(src.parseString("id") ?: error("missing id"))
			
			// ページネーションには日時を使う
			this._orderId = EntityIdLong(time_created_at)
			// お気に入りカラムなどではパース直後に変更することがある
			
			// 絵文字マップはすぐ後で使うので、最初の方で読んでおく
			this.custom_emojis = null
			this.profile_emojis = null
			
			val who = parser.account(src.optJSONObject("user"))
				?: throw RuntimeException("missing account")
			
			this.accountRef = TootAccountRef(parser, who)
			
			this.reblogs_count = src.parseLong("renoteCount") ?: 0L
			this.favourites_count = 0L
			this.replies_count = src.parseLong("repliesCount") ?: 0L
			
			this.reblogged = false
			this.favourited = false
			
			this.visibility = TootVisibility.parseMisskey(src.parseString("visibility")) ?:
				TootVisibility.Public
			
			this.misskeyVisibleIds = parseStringArray(src.optJSONArray("visibleUserIds"))
			
			this.media_attachments =
				parseListOrNull(::TootAttachment, parser, src.optJSONArray("media"))
			
			// Misskeyは画像毎にNSFWフラグがある。どれか１枚でもNSFWならトゥート全体がNSFWということにする
			var bv = src.optBoolean("sensitive")
			media_attachments?.forEach {
				if((it as? TootAttachment)?.isSensitive == true) {
					bv = true
				}
			}
			this.sensitive = bv
			
			this.reply = parser.status(src.optJSONObject("reply"))
			this.in_reply_to_id = EntityId.mayNull(src.parseString("replyId"))
			this.in_reply_to_account_id = reply?.account?.id
			
			this.pinned = parser.pinned
			this.muted = false
			this.language = null
			
			// "mentionedRemoteUsers" -> "[{"uri":"https:\/\/mastodon.juggler.jp\/users\/tateisu","username":"tateisu","host":"mastodon.juggler.jp"}]"
			
			this.tags = parseMisskeyTags(src.optJSONArray("tags"))
			
			this.application = parseItem(::TootApplication, parser, src.optJSONObject("app"), log)
			
			this.viaMobile = src.optBoolean("viaMobile")
			
			
			// this.decoded_tags = HTMLDecoder.decodeTags( account,status.tags );
			
			// content
			this.content = src.parseString("text")
			
			var options = DecodeOptions(
				parser.context,
				parser.linkHelper,
				short = true,
				decodeEmoji = true,
				emojiMapCustom = custom_emojis,
				emojiMapProfile = profile_emojis,
				attachmentList = media_attachments,
				highlightTrie = parser.highlightTrie
			)
			
			this.decoded_content = options.decodeHTML(content)
			this.hasHighlight = this.hasHighlight || options.hasHighlight
			if(options.highlight_sound != null && this.highlight_sound == null) {
				this.highlight_sound = options.highlight_sound
			}
			// Markdownのデコード結果からmentionsを読むのだった
			this.mentions = (decoded_content as? MisskeyMarkdownDecoder.SpannableStringBuilderEx)?.mentions
			this.decoded_mentions = HTMLDecoder.decodeMentions(
				parser.linkHelper,
				this.mentions,
				this
			) ?: EMPTY_SPANNABLE
			
			// spoiler_text
			this.spoiler_text = reWhitespace
				.matcher(src.parseString("cw") ?: "")
				.replaceAll(" ")
				.sanitizeBDI()
			
			options = DecodeOptions(
				parser.context,
				emojiMapCustom = custom_emojis,
				emojiMapProfile = profile_emojis,
				highlightTrie = parser.highlightTrie
			)
			
			this.decoded_spoiler_text = options.decodeEmoji(spoiler_text)
			
			this.hasHighlight = this.hasHighlight || options.hasHighlight
			if(options.highlight_sound != null && this.highlight_sound == null) {
				this.highlight_sound = options.highlight_sound
			}
			
			// contentを読んだ後にアンケートのデコード
			this.enquete = NicoEnquete.parse(
				parser,
				this,
				media_attachments,
				src.optJSONObject("poll")
			)
			
			this.reactionCounts = parseReactionCounts(src.optJSONObject("reactionCounts"))
			this.myReaction = src.parseString("myReaction")
			this.reblog = parser.status(src.optJSONObject("renote"))
			
			this.deletedAt = src.parseString("deletedAt")
			this.time_deleted_at = parseTime(deletedAt)
		} else {
			misskeyVisibleIds = null
			reply = null
			deletedAt = null
			time_deleted_at =0L

			this.uri = src.parseString("uri") // MSPだとuriは提供されない
			this.url = src.parseString("url") // 頻繁にnullになる
			this.created_at = src.parseString("created_at")
			
			// 絵文字マップはすぐ後で使うので、最初の方で読んでおく
			this.custom_emojis = parseMapOrNull(::CustomEmoji, src.optJSONArray("emojis"), log)
			this.profile_emojis =
				parseMapOrNull(::NicoProfileEmoji, src.optJSONArray("profile_emojis"), log)
			
			val who = parser.account(src.optJSONObject("account"))
				?: throw RuntimeException("missing account")
			
			this.accountRef = TootAccountRef(parser, who)
			
			this.reblogs_count = src.parseLong("reblogs_count")
			this.favourites_count = src.parseLong("favourites_count")
			this.replies_count = src.parseLong("replies_count")
			
			when(parser.serviceType) {
				ServiceType.MASTODON -> {
					this.host_access = parser.linkHelper.host
					
					this.id = EntityIdLong(src.parseLong("id") ?: INVALID_ID)
					
					this.reblogged = src.optBoolean("reblogged")
					this.favourited = src.optBoolean("favourited")
					
					this.time_created_at = parseTime(this.created_at)
					this.media_attachments =
						parseListOrNull(
							::TootAttachment,
							parser,
							src.optJSONArray("media_attachments"),
							log
						)
					this.visibility = TootVisibility.parseMastodon(src.parseString("visibility")) ?:
						TootVisibility.Public
					this.sensitive = src.optBoolean("sensitive")
					
				}
				
				ServiceType.TOOTSEARCH -> {
					this.host_access = null
					
					// 投稿元タンスでのIDを調べる。失敗するかもしれない
					this.id = findStatusIdFromUri(uri, url) ?: EntityIdLong(INVALID_ID)
					
					this.time_created_at = TootStatus.parseTime(this.created_at)
					this.media_attachments =
						parseListOrNull(
							::TootAttachment,
							parser,
							src.optJSONArray("media_attachments"),
							log
						)
					this.visibility = TootVisibility.Public
					this.sensitive = src.optBoolean("sensitive")
					
				}
				
				ServiceType.MSP -> {
					this.host_access = null
					
					// MSPのデータはLTLから呼んだものなので、常に投稿元タンスでのidが得られる
					this.id = EntityIdLong(src.parseLong("id") ?: INVALID_ID)
					
					this.time_created_at = parseTimeMSP(created_at)
					this.media_attachments =
						TootAttachmentMSP.parseList(src.optJSONArray("media_attachments"))
					this.visibility = TootVisibility.Public
					this.sensitive = src.optInt("sensitive", 0) != 0
				}
				
				ServiceType.MISSKEY -> error("will not happen")
			}
			
			this._orderId = this.id
			this.in_reply_to_id = EntityId.mayNull(src.parseLong("in_reply_to_id"))
			this.in_reply_to_account_id = EntityId.mayNull(src.parseLong("in_reply_to_account_id"))
			this.mentions = parseListOrNull(::TootMention, src.optJSONArray("mentions"), log)
			this.tags = parseListOrNull(::TootTag, src.optJSONArray("tags"))
			this.application =
				parseItem(::TootApplication, parser, src.optJSONObject("application"), log)
			this.pinned = parser.pinned || src.optBoolean("pinned")
			this.muted = src.optBoolean("muted")
			this.language = src.parseString("language")
			this.decoded_mentions = HTMLDecoder.decodeMentions(
				parser.linkHelper,
				this.mentions,
				this
			) ?: EMPTY_SPANNABLE
			// this.decoded_tags = HTMLDecoder.decodeTags( account,status.tags );
			
			// content
			this.content = src.parseString("content")
			
			var options = DecodeOptions(
				parser.context,
				parser.linkHelper,
				short = true,
				decodeEmoji = true,
				emojiMapCustom = custom_emojis,
				emojiMapProfile = profile_emojis,
				attachmentList = media_attachments,
				highlightTrie = parser.highlightTrie
			)
			
			this.decoded_content = options.decodeHTML(content)
			this.hasHighlight = this.hasHighlight || options.hasHighlight
			if(options.highlight_sound != null && this.highlight_sound == null) {
				this.highlight_sound = options.highlight_sound
			}
			
			// spoiler_text
			this.spoiler_text = reWhitespace
				.matcher(src.parseString("spoiler_text") ?: "")
				.replaceAll(" ")
				.sanitizeBDI()
			
			options = DecodeOptions(
				parser.context,
				emojiMapCustom = custom_emojis,
				emojiMapProfile = profile_emojis,
				highlightTrie = parser.highlightTrie
			)
			
			this.decoded_spoiler_text = options.decodeEmoji(spoiler_text)
			
			this.hasHighlight = this.hasHighlight || options.hasHighlight
			if(options.highlight_sound != null && this.highlight_sound == null) {
				this.highlight_sound = options.highlight_sound
			}
			
			this.enquete = NicoEnquete.parse(
				parser,
				this,
				media_attachments,
				src.parseString("enquete")
			)
			
			// Pinned TL を取得した時にreblogが登場することはないので、reblogについてpinned 状態を気にする必要はない
			this.reblog = parser.status(src.optJSONObject("reblog"))
			
		}
	}
	
	///////////////////////////////////////////////////
	// ユーティリティ
	
	val hostAccessOrOriginal : String
		get() = validHost(host_access) ?: validHost(host_original) ?: "(null)"
	
	val busyKey : String
		get() = "$hostAccessOrOriginal:$id"
	
	fun checkMuted() : Boolean {
		
		// app mute
		val muted_app = TootStatus.muted_app
		if(muted_app != null) {
			val name = application?.name
			if(name != null && muted_app.contains(name)) return true
		}
		
		// word mute
		val muted_word = TootStatus.muted_word
		if(muted_word != null) {
			if(muted_word.matchShort(decoded_content)) return true
			if(muted_word.matchShort(decoded_spoiler_text)) return true
		}
		
		// reblog
		return true == reblog?.checkMuted()
		
	}
	
	fun hasMedia() : Boolean {
		return (media_attachments?.size ?: 0) > 0
	}
	
	fun canPin(access_info : SavedAccount) : Boolean {
		return reblog == null
			&& access_info.isMe(account)
			&& visibility.canPin(access_info.isMisskey)
	}
	
	// 内部で使う
	private var _filtered = false
	
	val filtered : Boolean
		get() = _filtered || reblog?._filtered == true

	

	private fun hasReceipt(access_info:SavedAccount):TootVisibility{
		val fullAcctMe = access_info.getFullAcct(account)
		
		val reply_account = reply?.account
		if( reply_account != null && fullAcctMe != access_info.getFullAcct(reply_account) ) {
			return TootVisibility.DirectSpecified
		}
		
		val in_reply_to_account_id = this.in_reply_to_account_id
		if( in_reply_to_account_id != null && in_reply_to_account_id != account.id) {
			return TootVisibility.DirectSpecified
		}
		
		mentions?.forEach{
			if(fullAcctMe != access_info.getFullAcct(it.acct))
				return@hasReceipt TootVisibility.DirectSpecified
		}
		
		return TootVisibility.DirectPrivate
	}

	fun getBackgroundColorType(access_info:SavedAccount) =
		when(visibility){
			TootVisibility.DirectPrivate,
			TootVisibility.DirectSpecified -> hasReceipt(access_info)
			else-> visibility
		}
	
	
	fun updateFiltered(muted_words : WordTrieTree?) {
		_filtered = checkFiltered(muted_words)
		reblog?.updateFiltered(muted_words)
	}
	
	private fun checkFiltered(filter_tree : WordTrieTree?) : Boolean {
		filter_tree ?: return false
		//
		var t = decoded_spoiler_text
		if(t.isNotEmpty() && filter_tree.matchShort(t)) return true
		//
		t = decoded_content
		if(t.isNotEmpty() && filter_tree.matchShort(t)) return true
		//
		return false
	}
	
	fun hasAnyContent() =when{
		reblog == null -> true // reblog以外はオリジナルコンテンツがあると見なす
		serviceType != ServiceType.MISSKEY -> false // misskey以外のreblogはコンテンツがないと見なす
		content?.isNotEmpty()== true
			|| spoiler_text?.isNotEmpty()== true
			|| media_attachments?.isNotEmpty()== true
			|| enquete != null -> true
		else-> false
	}
	
	companion object {
		
		internal val log = LogCategory("TootStatus")
		
		@Volatile
		internal var muted_app : HashSet<String>? = null
		
		@Volatile
		internal var muted_word : WordTrieTree? = null
		
		private val reWhitespace = Pattern.compile("[\\s\\t\\x0d\\x0a]+")
		
		val EMPTY_SPANNABLE = SpannableString("")
		
		// OStatus
		@Suppress("HasPlatformType")
		private val reTootUriOS = Pattern.compile(
			"tag:([^,]*),[^:]*:objectId=(\\d+):objectType=Status",
			Pattern.CASE_INSENSITIVE
		)
		
		// ActivityPub 1
		@Suppress("HasPlatformType")
		private val reTootUriAP1 =
			Pattern.compile("https?://([^/]+)/users/[A-Za-z0-9_]+/statuses/(\\d+)")
		
		// ActivityPub 2
		@Suppress("HasPlatformType")
		private val reTootUriAP2 = Pattern.compile("https?://([^/]+)/@[A-Za-z0-9_]+/(\\d+)")
		
		const val INVALID_ID = - 1L
		
		fun parseListTootsearch(
			parser : TootParser,
			root : JSONObject
		) : ArrayList<TootStatus> {
			
			parser.serviceType = ServiceType.TOOTSEARCH
			
			val result = ArrayList<TootStatus>()
			val array = TootApiClient.getTootsearchHits(root)
			if(array != null) {
				val array_size = array.length()
				result.ensureCapacity(array_size)
				for(i in 0 until array.length()) {
					try {
						val src = array.optJSONObject(i)?.optJSONObject("_source") ?: continue
						result.add(TootStatus(parser, src))
					} catch(ex : Throwable) {
						log.trace(ex)
					}
				}
			}
			return result
		}
		
		private val tz_utc = TimeZone.getTimeZone("UTC")
		
		private val reTime =
			Pattern.compile("\\A(\\d+)\\D+(\\d+)\\D+(\\d+)\\D+(\\d+)\\D+(\\d+)\\D+(\\d+)\\D+(\\d+)")
		
		private val reMSPTime =
			Pattern.compile("\\A(\\d+)\\D+(\\d+)\\D+(\\d+)\\D+(\\d+)\\D+(\\d+)\\D+(\\d+)")
		
		fun parseTime(strTime : String?) : Long {
			if(strTime != null && strTime.isNotEmpty()) {
				try {
					val m = reTime.matcher(strTime)
					if(! m.find()) {
						log.d("invalid time format: %s", strTime)
					} else {
						val g = GregorianCalendar(tz_utc)
						g.set(
							m.group(1).optInt() ?: 1,
							(m.group(2).optInt() ?: 1) - 1,
							m.group(3).optInt() ?: 1,
							m.group(4).optInt() ?: 0,
							m.group(5).optInt() ?: 0,
							m.group(6).optInt() ?: 0
						)
						g.set(Calendar.MILLISECOND, m.group(7).optInt() ?: 0)
						return g.timeInMillis
					}
				} catch(ex : Throwable) { // ParseException,  ArrayIndexOutOfBoundsException
					log.trace(ex)
					log.e(ex, "TootStatus.parseTime failed. src=%s", strTime)
				}
				
			}
			return 0L
		}
		
		private fun parseTimeMSP(strTime : String?) : Long {
			if(strTime != null && strTime.isNotEmpty()) {
				try {
					val m = reMSPTime.matcher(strTime)
					if(! m.find()) {
						log.d("invalid time format: %s", strTime)
					} else {
						val g = GregorianCalendar(tz_utc)
						g.set(
							m.group(1).optInt() ?: 1,
							(m.group(2).optInt() ?: 1) - 1,
							m.group(3).optInt() ?: 1,
							m.group(4).optInt() ?: 0,
							m.group(5).optInt() ?: 0,
							m.group(6).optInt() ?: 0
						)
						g.set(Calendar.MILLISECOND, 500)
						return g.timeInMillis
					}
				} catch(ex : Throwable) { // ParseException,  ArrayIndexOutOfBoundsException
					log.trace(ex)
					log.e(ex, "parseTimeMSP failed. src=%s", strTime)
				}
				
			}
			return 0L
		}
		
		private val date_format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
		
		fun formatTime(context : Context, t : Long, bAllowRelative : Boolean) : String {
			if(bAllowRelative && Pref.bpRelativeTimestamp(App1.pref)) {
				val now = System.currentTimeMillis()
				var delta = now - t
				val sign = context.getString(if(delta > 0) R.string.ago else R.string.later)
				delta = if(delta >= 0) delta else - delta
				when {
					delta < 1000L -> return context.getString(R.string.time_within_second)
					
					delta < 60000L -> {
						val v = (delta / 1000L).toInt()
						return context.getString(
							if(v > 1) R.string.relative_time_second_2 else R.string.relative_time_second_1,
							v,
							sign
						)
					}
					
					delta < 3600000L -> {
						val v = (delta / 60000L).toInt()
						return context.getString(
							if(v > 1) R.string.relative_time_minute_2 else R.string.relative_time_minute_1,
							v,
							sign
						)
					}
					
					delta < 86400000L -> {
						val v = (delta / 3600000L).toInt()
						return context.getString(
							if(v > 1) R.string.relative_time_hour_2 else R.string.relative_time_hour_1,
							v,
							sign
						)
					}
					
					delta < 40 * 86400000L -> {
						val v = (delta / 86400000L).toInt()
						return context.getString(
							if(v > 1) R.string.relative_time_day_2 else R.string.relative_time_day_1,
							v,
							sign
						)
					}
					
					else -> {
					}
				}
			}
			date_format.timeZone = TimeZone.getDefault()
			return date_format.format(Date(t))
		}
		
		fun parseStringArray(src : JSONArray?) : ArrayList<String>? {
			var rv : ArrayList<String>? = null
			if(src != null) {
				for(i in 0 until src.length()) {
					val s = src.optString(i, null)
					if(s?.isNotEmpty() == true) {
						if(rv == null) rv = ArrayList()
						rv.add(s)
					}
				}
			}
			return rv
		}
		
		private fun parseReactionCounts(src : JSONObject?) : HashMap<String, Int>? {
			var rv : HashMap<String, Int>? = null
			if(src != null) {
				for(key in src.keys()) {
					val v = src.parseInt(key) ?: continue
					MisskeyReaction.shortcodeMap[key] ?: continue
					if(rv == null) rv = HashMap()
					rv[key] = v
				}
			}
			return rv
		}
		
		private fun parseMisskeyTags(src : JSONArray?) : ArrayList<TootTag>? {
			var rv : ArrayList<TootTag>? = null
			if(src != null) {
				for(i in 0 until src.length()) {
					val sv = src.optString(i, null)
					if(sv?.isNotEmpty() == true) {
						if(rv == null) rv = ArrayList()
						rv.add(TootTag(name = sv))
					}
				}
			}
			return rv
		}
		
		private fun validHost(host : String?) : String? {
			return if(host != null && host.isNotEmpty() && host != "?") host else null
		}
		
		private val reMisskeyNoteUrl = Pattern.compile("""https://([^/]+)/notes/([0-9A-F]+)""",Pattern.CASE_INSENSITIVE)
		
		fun readMisskeyNoteId(url:String):EntityId?{
			// https://misskey.xyz/notes/5b802367744b650030a13640
			val m = reMisskeyNoteUrl.matcher(url)
			if(m.find() ) return EntityIdString(m.group(2))
			return null
		}
		
		fun validStatusId(src:EntityId?):EntityId?{
			return when{
				src == null -> null
				src is EntityIdLong && src.toLong() == TootStatus.INVALID_ID ->null
				else ->src
			}
		}
		
		
		// 投稿元タンスでのステータスIDを調べる
		fun findStatusIdFromUri(uri : String?, url : String?, bAllowStringId:Boolean =false) : EntityId? {
			
			// pleromaのuriやURL からはステータスIDは取れません
			// uri https://pleroma.miniwa.moe/objects/d6e83d3c-cf9e-46ac-8245-f91716088e17
			// url https://pleroma.miniwa.moe/objects/d6e83d3c-cf9e-46ac-8245-f91716088e17
			
			try {
				if(uri?.isNotEmpty() == true) {
					// https://friends.nico/users/(who)/statuses/(status_id)
					var m = reTootUriAP1.matcher(uri)
					if(m.find()) return EntityIdLong(m.group(2).toLong(10))
					
					// tag:mstdn.osaka,2017-12-19:objectId=5672321:objectType=Status
					m = reTootUriOS.matcher(uri)
					if(m.find()) return EntityIdLong(m.group(2).toLong(10))
					
					//
					m = reTootUriAP2.matcher(uri)
					if(m.find()) return EntityIdLong(m.group(2).toLong(10))
					
					if(bAllowStringId){
						val id = readMisskeyNoteId(uri)
						if(id!=null) return id
					}
					
					log.w("can't parse status uri: $uri")
				}
				
				if(url?.isNotEmpty() == true) {
					
					// https://friends.nico/users/(who)/statuses/(status_id)
					var m = reTootUriAP1.matcher(url)
					if(m.find()) return EntityIdLong(m.group(2).toLong(10))
					
					// https://friends.nico/@(who)/(status_id)
					m = reTootUriAP2.matcher(url)
					if(m.find()) return EntityIdLong(m.group(2).toLong(10))
					
					if(bAllowStringId){
						val id = readMisskeyNoteId(url)
						if(id!=null) return id
					}
					
					log.w("can't parse status URL: $url")
				}
				
			} catch(ex : Throwable) {
				log.e(ex, "can't parse status from: $uri,$url")
			}
			
			return null
		}
		
	}
	
}
