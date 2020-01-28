package jp.juggler.subwaytooter.util

import android.content.Context
import android.content.Intent
import jp.juggler.subwaytooter.ActText
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.table.SavedAccount
import java.util.*

object TootTextEncoder {
	private fun StringBuilder.addAfterLine(text : CharSequence) {
		if(isNotEmpty() && this[length - 1] != '\n') {
			append('\n')
		}
		append(text)
	}
	
	private fun addHeader(
		context : Context,
		sb : StringBuilder,
		key_str_id : Int,
		value : Any?
	) {
		if(sb.isNotEmpty() && sb[sb.length - 1] != '\n') {
			sb.append('\n')
		}
		sb.addAfterLine(context.getString(key_str_id))
		sb.append(": ")
		sb.append(value?.toString() ?: "(null)")
	}
	
	fun encodeStatus(
		intent : Intent,
		context : Context,
		access_info : SavedAccount,
		status : TootStatus
	) {
		val sb = StringBuilder()
		
		addHeader(context, sb, R.string.send_header_url, status.url)
		
		addHeader(
			context,
			sb,
			R.string.send_header_date,
			TootStatus.formatTime(context, status.time_created_at, false)
		)
		
		
		addHeader(
			context,
			sb,
			R.string.send_header_from_acct,
			access_info.getFullAcct(status.account)
		)
		
		val sv : String? = status.spoiler_text
		if(sv != null && sv.isNotEmpty()) {
			addHeader(context, sb, R.string.send_header_content_warning, sv)
		}
		
		sb.addAfterLine("\n")
		
		intent.putExtra(ActText.EXTRA_CONTENT_START, sb.length)
		sb.append(
			DecodeOptions(
				context,
				access_info,
				mentions = status.mentions
			).decodeHTML(status.content)
		)
		
		encodePolls(sb, context, status)
		
		intent.putExtra(ActText.EXTRA_CONTENT_END, sb.length)
		
		dumpAttachment(sb, status.media_attachments)
		
		sb.addAfterLine(String.format(Locale.JAPAN, "Status-Source: %s", status.json.toString(2)))
		sb.addAfterLine("")
		intent.putExtra(ActText.EXTRA_TEXT, sb.toString())
	}
	
	fun encodeStatusForTranslate(
		context : Context,
		access_info : SavedAccount,
		status : TootStatus
	) : String {
		val sb = StringBuilder()
		
		val sv : String? = status.spoiler_text
		if(sv != null && sv.isNotEmpty()) {
			sb.append(sv).append("\n\n")
		}
		
		sb.append(
			DecodeOptions(
				context,
				access_info,
				mentions = status.mentions
			).decodeHTML(status.content)
		)
		
		encodePolls(sb, context, status)
		
		return sb.toString()
	}
	
	private fun dumpAttachment(sb : StringBuilder, src : ArrayList<TootAttachmentLike>?) {
		if(src == null) return
		var i = 0
		for(ma in src) {
			++ i
			if(ma is TootAttachment) {
				sb.addAfterLine("\n")
				sb.addAfterLine(String.format(Locale.JAPAN, "Media-%d-Url: %s", i, ma.url))
				sb.addAfterLine(
					String.format(Locale.JAPAN, "Media-%d-Remote-Url: %s", i, ma.remote_url)
				)
				sb.addAfterLine(
					String.format(Locale.JAPAN, "Media-%d-Preview-Url: %s", i, ma.preview_url)
				)
				sb.addAfterLine(
					String.format(Locale.JAPAN, "Media-%d-Text-Url: %s", i, ma.text_url)
				)
			} else if(ma is TootAttachmentMSP) {
				sb.addAfterLine("\n")
				sb.addAfterLine(
					String.format(Locale.JAPAN, "Media-%d-Preview-Url: %s", i, ma.preview_url)
				)
			}
		}
	}
	
	private fun encodePolls(sb : StringBuilder, context : Context, status : TootStatus) {
		val enquete = status.enquete ?: return
		val items = enquete.items ?: return
		val now = System.currentTimeMillis()
		
		val canVote = when(enquete.pollType) {
			
			// friends.nico の場合は本文に投票の選択肢が含まれるので
			// アプリ側での文字列化は不要
			TootPollsType.FriendsNico -> return
			
			// MastodonとMisskeyは投票の選択肢が本文に含まれないので
			// アプリ側で文字列化する
			
			TootPollsType.Mastodon -> when {
				enquete.expired -> false
				now >= enquete.expired_at -> false
				enquete.ownVoted -> false
				else -> true
			}
			
			TootPollsType.Misskey -> enquete.ownVoted
		}
		
		sb.addAfterLine("\n")
		
		items.forEach { choice ->
			encodePollChoice(sb, context, enquete, canVote, choice)
		}
		
		when(enquete.pollType) {
			TootPollsType.Mastodon -> encodePollFooterMastodon(sb, context, enquete)
			
			else -> {
			}
		}
	}
	
	private fun encodePollChoice(
		sb : StringBuilder,
		context : Context,
		enquete : TootPolls,
		canVote : Boolean,
		item : TootPollsChoice
	) {
		
		val text = when(enquete.pollType) {
			TootPollsType.Misskey -> {
				val sb2 = StringBuilder().append(item.decoded_text)
				if(enquete.ownVoted) {
					sb2.append(" / ")
					sb2.append(context.getString(R.string.vote_count_text, item.votes))
					if(item.isVoted) sb2.append(' ').append(0x2713.toChar())
				}
				sb2
			}
			
			TootPollsType.FriendsNico -> {
				item.decoded_text
			}
			
			TootPollsType.Mastodon -> if(canVote) {
				item.decoded_text
			} else {
				val sb2 = StringBuilder().append(item.decoded_text)
				if(! canVote) {
					sb2.append(" / ")
					sb2.append(
						when(val v = item.votes) {
							null -> context.getString(R.string.vote_count_unavailable)
							else -> context.getString(R.string.vote_count_text, v)
						}
					)
				}
				sb2
			}
		}
		
		sb.addAfterLine(text)
	}
	
	private fun encodePollFooterMastodon(
		sb : StringBuilder,
		context : Context,
		enquete : TootPolls
	) {
		val line = StringBuilder()
		
		val votes_count = enquete.votes_count ?: 0
		when {
			votes_count == 1 -> line.append(context.getString(R.string.vote_1))
			votes_count > 1 -> line.append(context.getString(R.string.vote_2, votes_count))
		}
		
		when(val t = enquete.expired_at) {
			
			Long.MAX_VALUE -> {
			}
			
			else -> {
				if(line.isNotEmpty()) line.append(" ")
				line.append(
					context.getString(
						R.string.vote_expire_at,
						TootStatus.formatTime(context, t, false)
					)
				)
			}
		}
		sb.addAfterLine(line)
	}
	
	fun encodeAccount(
		intent : Intent,
		context : Context,
		access_info : SavedAccount,
		who : TootAccount
	) {
		val sb = StringBuilder()
		
		intent.putExtra(ActText.EXTRA_CONTENT_START, sb.length)
		sb.append(who.display_name)
		sb.append("\n")
		sb.append("@")
		sb.append(access_info.getFullAcct(who))
		sb.append("\n")
		
		intent.putExtra(ActText.EXTRA_CONTENT_START, sb.length)
		sb.append(who.url)
		intent.putExtra(ActText.EXTRA_CONTENT_END, sb.length)
		
		sb.addAfterLine("\n")
		
		sb.append(DecodeOptions(context, access_info).decodeHTML(who.note))
		
		sb.addAfterLine("\n")
		
		addHeader(context, sb, R.string.send_header_account_name, who.display_name)
		addHeader(context, sb, R.string.send_header_account_acct, access_info.getFullAcct(who))
		addHeader(context, sb, R.string.send_header_account_url, who.url)
		
		addHeader(context, sb, R.string.send_header_account_image_avatar, who.avatar)
		addHeader(
			context,
			sb,
			R.string.send_header_account_image_avatar_static,
			who.avatar_static
		)
		addHeader(context, sb, R.string.send_header_account_image_header, who.header)
		addHeader(
			context,
			sb,
			R.string.send_header_account_image_header_static,
			who.header_static
		)
		
		addHeader(context, sb, R.string.send_header_account_created_at, who.created_at)
		addHeader(context, sb, R.string.send_header_account_statuses_count, who.statuses_count)
		
		if(! Pref.bpHideFollowCount( App1.getAppState(context).pref)) {
			addHeader(
				context,
				sb,
				R.string.send_header_account_followers_count,
				who.followers_count
			)
			addHeader(
				context,
				sb,
				R.string.send_header_account_following_count,
				who.following_count
			)
		}
		addHeader(context, sb, R.string.send_header_account_locked, who.locked)
		
		sb.addAfterLine(String.format(Locale.JAPAN, "Account-Source: %s", who.json.toString(2)))
		sb.addAfterLine("")
		
		intent.putExtra(ActText.EXTRA_TEXT, sb.toString())
	}
	
}