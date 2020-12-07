package jp.juggler.subwaytooter.api.entity

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.util.*

enum class TootPollsType {
	Mastodon, // Mastodon 2.8
	Misskey, // Misskey
	FriendsNico, // friends.nico API
	Notestock, // notestock
}

class TootPollsChoice(
	val text : String,
	val decoded_text : Spannable,
	var isVoted : Boolean = false, // misskey
	var votes : Int? = 0, // misskey
	var checked : Boolean = false // Mastodon
)

class TootPolls (
	parser : TootParser,
	val pollType : TootPollsType,
	status : TootStatus,
	list_attachment : ArrayList<TootAttachmentLike>?,
	src : JsonObject,
	srcArray: JsonArray? = null
) {
	
	// one of enquete,enquete_result
	val type : String?
	
	val question : String? // HTML text
	
	val decoded_question : Spannable // 表示用にデコードしてしまうのでNonNullになる
	
	// array of text with emoji
	val items : ArrayList<TootPollsChoice>?
	
	// 結果の数値 // null or array of number
	var ratios : MutableList<Float>? = null
	
	// 結果の数値のテキスト // null or array of string
	private var ratios_text : MutableList<String>? = null
	
	// 以下はJSONには存在しないが内部で使う
	val time_start : Long
	val status_id : EntityId
	
	// Mastodon poll API
	var expired_at = Long.MAX_VALUE
	var expired = false
	var multiple = false
	var votes_count : Int? = null
	var maxVotesCount : Int? = null
	var pollId : EntityId? = null
	
	var ownVoted : Boolean
	
	init {
		
		this.time_start = status.time_created_at
		this.status_id = status.id
		
		when(pollType) {
			TootPollsType.Misskey -> {
				
				this.items = parseChoiceListMisskey(
					
					src.jsonArray("choices")
				)
				
				val votesList = ArrayList<Int>()
				var votesMax = 1
				var ownVoted = false
				items?.forEach { choice ->
					if(choice.isVoted) ownVoted = true
					val votes = choice.votes ?: 0
					votesList.add(votes)
					if(votes > votesMax) votesMax = votes
				}
				this.ownVoted = ownVoted
				
				if(votesList.isNotEmpty()) {
					this.ratios =
						votesList.map { (it.toFloat() / votesMax.toFloat()) }.toMutableList()
					this.ratios_text =
						votesList.map { parser.context.getString(R.string.vote_count_text, it) }
							.toMutableList()
				} else {
					this.ratios = null
					this.ratios_text = null
				}
				
				this.type = TYPE_ENQUETE
				
				this.question = status.content
				this.decoded_question = DecodeOptions(
					parser.context,
					parser.linkHelper,
					short = true,
					decodeEmoji = true,
					attachmentList = list_attachment,
					linkTag = status,
					emojiMapCustom = status.custom_emojis,
					emojiMapProfile = status.profile_emojis,
					mentions = status.mentions,
					mentionDefaultHostDomain = status.account
				).decodeHTML(this.question ?: "?")
				
			}
			
			TootPollsType.Mastodon -> {
				this.type = "enquete"
				
				this.question = status.content
				this.decoded_question = DecodeOptions(
					parser.context,
					parser.linkHelper,
					short = true,
					decodeEmoji = true,
					attachmentList = list_attachment,
					linkTag = status,
					emojiMapCustom = status.custom_emojis,
					emojiMapProfile = status.profile_emojis,
					mentions = status.mentions,
					mentionDefaultHostDomain = status.account
				).decodeHTML(this.question ?: "?")
				
				this.items = parseChoiceListMastodon(
					parser.context,
					status,
					src.jsonArray("options")?.objectList()
				)
				
				this.pollId = EntityId.mayNull(src.string("id"))
				this.expired_at =
					TootStatus.parseTime(src.string("expires_at")).notZero() ?: Long.MAX_VALUE
				this.expired = src.optBoolean("expired", false)
				this.multiple = src.optBoolean("multiple", false)
				this.votes_count = src.int("votes_count")
				
				var ownVoted = src.optBoolean("voted", false)
				
				src.jsonArray("own_votes")?.forEach {
					if(it is Number) {
						val i = it.toInt()
						items?.get(i)?.isVoted = true
						ownVoted = true
					}
				}
				
				this.ownVoted = ownVoted
				
				when {
					this.items == null -> maxVotesCount = null
					
					this.multiple -> {
						var max : Int? = null
						for(item in items) {
							val v = item.votes
							if(v != null && (max == null || v > max)) max = v
							
						}
						maxVotesCount = max
					}
					
					else -> {
						var sum : Int? = null
						for(item in items) {
							val v = item.votes
							if(v != null) sum = (sum ?: 0) + v
						}
						maxVotesCount = sum
					}
				}
				
			}
			
			TootPollsType.FriendsNico -> {
				this.type = src.string("type")
				
				this.question = src.string("question")
				this.decoded_question = DecodeOptions(
					parser.context,
					parser.linkHelper,
					short = true,
					decodeEmoji = true,
					attachmentList = list_attachment,
					linkTag = status,
					emojiMapCustom = status.custom_emojis,
					emojiMapProfile = status.profile_emojis,
					mentions = status.mentions,
					mentionDefaultHostDomain = status.account
				).decodeHTML(this.question ?: "?")
				
				this.items = parseChoiceListFriendsNico(
					parser.context,
					status,
					src.stringArrayList("items")
				)
				
				this.ratios = src.floatArrayList("ratios")
				this.ratios_text = src.stringArrayList("ratios_text")
				
				this.ownVoted = false
			}

			TootPollsType.Notestock->{
				this.type = "enquete"

				this.question = status.content
				this.decoded_question = DecodeOptions(
					parser.context,
					parser.linkHelper,
					short = true,
					decodeEmoji = true,
					attachmentList = list_attachment,
					linkTag = status,
					emojiMapCustom = status.custom_emojis,
					emojiMapProfile = status.profile_emojis,
					mentions = status.mentions,
					mentionDefaultHostDomain = status.account
				).decodeHTML(this.question ?: "?")

				this.items = parseChoiceListNotestock(
					parser.context,
					status,
					srcArray?.objectList()
				)

				this.pollId = EntityId.DEFAULT
				this.expired_at =
					TootStatus.parseTime(src.string("endTime")).notZero() ?: Long.MAX_VALUE
				this.expired = expired_at >= System.currentTimeMillis()
				this.multiple =   src.containsKey("anyOf")
				this.votes_count = items?.sumBy{ it.votes?: 0 }?.notZero()

				this.ownVoted = false

				when {
					this.items == null -> maxVotesCount = null

					this.multiple -> {
						var max : Int? = null
						for(item in items) {
							val v = item.votes
							if(v != null && (max == null || v > max)) max = v

						}
						maxVotesCount = max
					}

					else -> {
						var sum : Int? = null
						for(item in items) {
							val v = item.votes
							if(v != null) sum = (sum ?: 0) + v
						}
						maxVotesCount = sum
					}
				}
			}
		}
	}
	
	companion object {
		
		internal val log = LogCategory("TootPolls")
		
		const val ENQUETE_EXPIRE = 30000L
		
		const val TYPE_ENQUETE = "enquete"
		
		@Suppress("unused")
		const val TYPE_ENQUETE_RESULT = "enquete_result"
		
		@Suppress("HasPlatformType")
		private val reWhitespace = """[\s\t\x0d\x0a]+""".asciiPattern()
		
		fun parse(
			parser : TootParser,
			pollType : TootPollsType,
			status : TootStatus,
			list_attachment : ArrayList<TootAttachmentLike>?,
			src : JsonObject?,
		) : TootPolls? {
			src ?: return null
			return try {
				TootPolls(
					parser,
					pollType,
					status,
					list_attachment,
					src
				)
			} catch(ex : Throwable) {
				log.trace(ex)
				null
			}
		}



		private fun parseChoiceListMastodon(
			context : Context,
			status : TootStatus,
			objectArray : List<JsonObject>?
		) : ArrayList<TootPollsChoice>? {
			if(objectArray != null) {
				val size = objectArray.size
				val items = ArrayList<TootPollsChoice>(size)
				val options = DecodeOptions(
					context,
					emojiMapCustom = status.custom_emojis,
					emojiMapProfile = status.profile_emojis,
					decodeEmoji = true,
					mentionDefaultHostDomain = status.account
				)
				for(o in objectArray) {
					val text = reWhitespace
						.matcher((o.string("title") ?: "?").sanitizeBDI())
						.replaceAll(" ")
					val decoded_text = options.decodeEmoji(text)
					
					items.add(
						TootPollsChoice(
							text,
							decoded_text,
							votes = o.int("votes_count") // may null
						)
					)
				}
				if(items.isNotEmpty()) return items
			}
			return null
		}


		private fun parseChoiceListNotestock(
			context : Context,
			status : TootStatus,
			objectArray : List<JsonObject>?
		) : ArrayList<TootPollsChoice>? {
			if(objectArray != null) {
				val size = objectArray.size
				val items = ArrayList<TootPollsChoice>(size)
				val options = DecodeOptions(
					context,
					emojiMapCustom = status.custom_emojis,
					emojiMapProfile = status.profile_emojis,
					decodeEmoji = true,
					mentionDefaultHostDomain = status.account
				)
				for(o in objectArray) {
					val text = reWhitespace
						.matcher((o.string("name") ?: "?").sanitizeBDI())
						.replaceAll(" ")
					val decoded_text = options.decodeEmoji(text)

					items.add(
						TootPollsChoice(
							text,
							decoded_text,
							votes = o.jsonObject("replies")?.int("totalItems") // may null
						)
					)
				}
				if(items.isNotEmpty()) return items
			}
			return null
		}

		private fun parseChoiceListFriendsNico(
			context : Context,
			status : TootStatus,
			stringArray : ArrayList<String>?
		) : ArrayList<TootPollsChoice>? {
			if(stringArray != null) {
				val size = stringArray.size
				val items = ArrayList<TootPollsChoice>(size)
				val options = DecodeOptions(
					context,
					emojiMapCustom = status.custom_emojis,
					emojiMapProfile = status.profile_emojis,
					decodeEmoji = true,
					mentionDefaultHostDomain = status.account
				)
				for(i in 0 until size) {
					val text = reWhitespace
						.matcher(stringArray[i].sanitizeBDI())
						.replaceAll(" ")
					val decoded_text = options.decodeHTML(text)
					
					items.add(
						TootPollsChoice(
							text,
							decoded_text
						)
					)
				}
				if(items.isNotEmpty()) return items
			}
			return null
		}
		
		private fun parseChoiceListMisskey(
			choices : JsonArray?
		) : ArrayList<TootPollsChoice>? {
			if(choices != null) {
				val items = ArrayList<TootPollsChoice>()
				choices.forEach {
					it.cast<JsonObject>()?.let { src ->
						val text = reWhitespace
							.matcher(src.string("text")?.sanitizeBDI() ?: "")
							.replaceAll(" ")
						val decoded_text = SpannableString(text) // misskey ではマークダウン不可で絵文字もない
						
						val dst = TootPollsChoice(
							text = text,
							decoded_text = decoded_text,
							// 配列インデクスと同じだった id = EntityId.mayNull(src.long("id")),
							votes = src.int("votes") ?: 0,
							isVoted = src.optBoolean("isVoted")
						)
						items.add(dst)
					}
					
				}
				
				
				if(items.isNotEmpty()) return items
			}
			return null
		}
	}
	
	// misskey用
	fun increaseVote(context : Context, argChoice : Int?, isMyVoted : Boolean) : Boolean {
		argChoice ?: return false
		
		synchronized(this) {
			try {
				// 既に投票済み状態なら何もしない
				if(ownVoted) return false
				
				val item = this.items?.get(argChoice) ?: return false
				item.votes = (item.votes ?: 0) + 1
				if(isMyVoted) item.isVoted = true
				
				// update ratios
				val votesList = ArrayList<Int>()
				var votesMax = 1
				items.forEach { choice ->
					if(choice.isVoted) ownVoted = true
					val votes = choice.votes ?: 0
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
