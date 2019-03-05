package jp.juggler.subwaytooter.api.entity

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.util.*
import jp.juggler.util.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

@Suppress("MemberVisibilityCanPrivate")
class NicoEnquete(
	parser : TootParser,
	status : TootStatus,
	list_attachment : ArrayList<TootAttachmentLike>?,
	src : JSONObject
) {
	enum class PollType{
		Mastodon, // Mastodon 2.8's poll
		Misskey, // Misskey's poll
		FriendsNico, // friends.nico
	}
	
	val poll_type : PollType
	
	// one of enquete,enquete_result
	val type : String?
	
	val question : String? // HTML text
	
	val decoded_question : Spannable // 表示用にデコードしてしまうのでNonNullになる
	
	// array of text with emoji
	val items : ArrayList<Choice>?
	
	// 結果の数値 // null or array of number
	private var ratios : MutableList<Float>?
	
	// 結果の数値のテキスト // null or array of string
	private var ratios_text : MutableList<String>?
	
	var myVoted : Int? = null
	
	// 以下はJSONには存在しないが内部で使う
	val time_start : Long
	val status_id : EntityId
	
	init {
		
		this.time_start = status.time_created_at
		this.status_id = status.id
		
		if(parser.serviceType == ServiceType.MISSKEY) {
			this.poll_type = PollType.Misskey
			
			this.items = parseChoiceListMisskey(
				
				src.optJSONArray("choices")
			)
			
			val votesList = ArrayList<Int>()
			var votesMax = 1
			items?.forEachIndexed { index, choice ->
				if(choice.isVoted) this.myVoted = index
				val votes = choice.votes
				votesList.add(votes)
				if(votes > votesMax) votesMax = votes
			}
			
			if(votesList.isNotEmpty()) {
				this.ratios = votesList.map { (it.toFloat() / votesMax.toFloat()) }.toMutableList()
				this.ratios_text =
					votesList.map { parser.context.getString(R.string.vote_count_text, it) }
						.toMutableList()
			} else {
				this.ratios = null
				this.ratios_text = null
			}
			
			this.type = NicoEnquete.TYPE_ENQUETE
			
			this.question = status.content
			this.decoded_question = DecodeOptions(
				parser.context,
				parser.linkHelper,
				short = true,
				decodeEmoji = true,
				attachmentList = list_attachment,
				linkTag = status,
				emojiMapCustom = status.custom_emojis,
				emojiMapProfile = status.profile_emojis
			).decodeHTML(this.question ?: "?")
			
		} else {
			// TODO Mastodonのpollとfriends.nicoのアンケートを区別する
			this.poll_type = PollType.FriendsNico
			this.type = src.parseString("type")
			
			this.question = src.parseString("question")
			this.decoded_question = DecodeOptions(
				parser.context,
				parser.linkHelper,
				short = true,
				decodeEmoji = true,
				attachmentList = list_attachment,
				linkTag = status,
				emojiMapCustom = status.custom_emojis,
				emojiMapProfile = status.profile_emojis
			).decodeHTML(this.question ?: "?")
			
			this.items = parseChoiceList(
				parser.context,
				status,
				src.parseStringArrayList("items")
			)
			
			this.ratios = src.parseFloatArrayList("ratios")
			this.ratios_text = src.parseStringArrayList( "ratios_text")
		}
		
	}
	
	class Choice(
		val text : String,
		val decoded_text : Spannable,
		var isVoted : Boolean = false, // misskey
		var votes : Int = 0 // misskey
	)
	
	companion object {
		internal val log = LogCategory("NicoEnquete")
		
		const val ENQUETE_EXPIRE = 30000L
		
		const val TYPE_ENQUETE = "enquete"
		
		@Suppress("unused")
		const val TYPE_ENQUETE_RESULT = "enquete_result"
		
		@Suppress("HasPlatformType")
		private val reWhitespace = Pattern.compile("[\\s\\t\\x0d\\x0a]+")
		
		fun parse(
			parser : TootParser,
			status : TootStatus,
			list_attachment : ArrayList<TootAttachmentLike>?,
			jsonString : String?
		) : NicoEnquete? {
			jsonString ?: return null
			return try {
				NicoEnquete(
					parser,
					status,
					list_attachment,
					jsonString.toJsonObject()
				)
			} catch(ex : Throwable) {
				log.trace(ex)
				null
			}
		}
		
		fun parse(
			parser : TootParser,
			status : TootStatus,
			list_attachment : ArrayList<TootAttachmentLike>?,
			src : JSONObject?
		) : NicoEnquete? {
			src ?: return null
			return try {
				NicoEnquete(
					parser,
					status,
					list_attachment,
					src
				)
			} catch(ex : Throwable) {
				log.trace(ex)
				null
			}
		}
		
		private fun parseChoiceList(
			context : Context,
			status : TootStatus,
			stringArray : ArrayList<String>?
		) : ArrayList<Choice>? {
			if(stringArray != null) {
				val size = stringArray.size
				val items = ArrayList<Choice>(size)
				val options = DecodeOptions(
					context,
					emojiMapCustom = status.custom_emojis,
					emojiMapProfile = status.profile_emojis,
					decodeEmoji = true
				)
				for(i in 0 until size) {
					val text = reWhitespace
						.matcher(stringArray[i].sanitizeBDI())
						.replaceAll(" ")
					val decoded_text = options.decodeHTML(text)
					
					items.add(Choice(text, decoded_text))
				}
				if(items.isNotEmpty()) return items
			}
			return null
		}
		
		private fun parseChoiceListMisskey(
			choices : JSONArray?
		) : ArrayList<Choice>? {
			if(choices != null) {
				val items = ArrayList<Choice>()
				for(i in 0 until choices.length()) {
					val src = choices.optJSONObject(i)
					
					val text = reWhitespace
						.matcher(src.parseString("text")?.sanitizeBDI() ?: "")
						.replaceAll(" ")
					val decoded_text = SpannableString(text) // misskey ではマークダウン不可で絵文字もない
					
					val dst = Choice(
						text = text,
						decoded_text = decoded_text,
						// 配列インデクスと同じだった id = EntityId.mayNull(src.parseLong("id")),
						votes = src.parseInt("votes") ?: 0,
						isVoted = src.optBoolean("isVoted")
					)
					items.add(dst)
				}
				
				if(items.isNotEmpty()) return items
			}
			return null
		}
	}
	
	// misskey用
	fun increaseVote(context : Context, argChoice : Int?, isMyVoted : Boolean) : Boolean {
		argChoice ?: return false
		
		synchronized(this){
			try {
				// 既に投票済み状態なら何もしない
				if(myVoted != null) return false
				
				val item = this.items?.get(argChoice) ?: return false
				item.votes += 1
				if(isMyVoted) item.isVoted = true
				
				// update ratios
				val votesList = ArrayList<Int>()
				var votesMax = 1
				items.forEachIndexed { index, choice ->
					if(choice.isVoted) this.myVoted = index
					val votes = choice.votes
					votesList.add(votes)
					if(votes > votesMax) votesMax = votes
				}
				
				if(votesList.isNotEmpty()) {
					
					this.ratios = votesList.asSequence()
						.map { (it.toFloat() / votesMax.toFloat()) }
						.toMutableList()
					
					this.ratios_text = votesList.asSequence()
						.map { context.getString(R.string.vote_count_text, it) }
						.toMutableList()
					
				} else {
					this.ratios = null
					this.ratios_text = null
				}
				
				return true
				
			} catch(ex : Throwable) {
				log.e(ex, "increaseVote failed")
				return false
			}
			
		}
	}
	
}
