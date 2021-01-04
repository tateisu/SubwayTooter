package jp.juggler.subwaytooter.api.entity

import android.text.Spannable
import android.text.SpannableStringBuilder
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.span.NetworkEmojiSpan
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.EmojiDecoder

//private fun findSvgFile(utf16: String) =
//    EmojiMap.sUTF16ToEmojiResource[utf16]
//
//fun EmojiMap.EmojiResource.loadToImageView(activity: ActMain, view: ImageView) {
//    if (isSvg) {
//        Glide.with(activity)
//            .`as`(PictureDrawable::class.java)
//            .load("file:///android_asset/${assetsName}")
//            .into(view)
//    } else {
//        Glide.with(activity)
//            .load(drawableId)
//            .into(view)
//    }
//}

object MisskeyReaction {
	private val oldReactions = mapOf(
		"like" to "\ud83d\udc4d",
		"love" to "\u2665",
		"laugh" to "\ud83d\ude06",
		"hmm" to "\ud83e\udd14",
		"surprise" to "\ud83d\ude2e",
		"congrats" to "\ud83c\udf89",
		"angry" to "\ud83d\udca2",
		"confused" to "\ud83d\ude25",
		"rip" to "\ud83d\ude07",
		"pudding" to "\ud83c\udf6e",
		"star" to "\u2B50", // リモートからのFavを示す代替リアクション。ピッカーには表示しない
	)

	private val reCustomEmoji = """\A:([^:]+):\z""".toRegex()

	fun getAnotherExpression(reaction: String): String? {
		val customCode = reCustomEmoji.find(reaction)?.groupValues?.elementAtOrNull(1) ?: return null
		val cols = customCode.split("@")
		val name = cols.elementAtOrNull(0)
		val domain = cols.elementAtOrNull(1)
		return if (domain == null) ":$name@.:" else if (domain == ".") ":$name:" else null
	}

	fun equals(a:String?,b:String?) = when {
		a==null -> b==null
		b==null -> false
		else -> a ==b || getAnotherExpression(a) == b || a == getAnotherExpression(b)
	}

    fun toSpannableStringBuilder(
		code: String,
		options: DecodeOptions,
		status:TootStatus?
	): SpannableStringBuilder {

        // 古い形式の絵文字はUnicode絵文字にする
        oldReactions[code]?.let {
            return EmojiDecoder.decodeEmoji(options, it)
        }

		fun CustomEmoji.toSpannableStringBuilder():SpannableStringBuilder?{
			return if (Pref.bpDisableEmojiAnimation(App1.pref)) {
				static_url
			} else {
				url
			}?.let{
				SpannableStringBuilder(code).apply {
					setSpan(
						NetworkEmojiSpan(it, scale = options.enlargeCustomEmoji),
						0,
						length,
						Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
					)
				}
			}
		}

		// カスタム絵文字
		val customCode = reCustomEmoji.find(code)?.groupValues?.elementAtOrNull(1)
		if(customCode != null){
			var ce = status?.custom_emojis?.get( customCode)
			if(ce != null) return ce.toSpannableStringBuilder()?: EmojiDecoder.decodeEmoji(options, code)

			val accessInfo = options.linkHelper as? SavedAccount

			val cols = customCode.split("@",limit = 2)
			val key = cols.elementAtOrNull(0)
			val domain = cols.elementAtOrNull(1)
			if( domain == null || domain=="" || domain=="." || domain == accessInfo?.apiHost?.ascii ){
				if( accessInfo != null){
					ce = App1.custom_emoji_lister
						.getMap(accessInfo)
						?.get(key)
					if(ce != null) return ce.toSpannableStringBuilder()?: EmojiDecoder.decodeEmoji(options, code)
				}
			}
		}

		// unicode絵文字、もしくは :xxx: などのshortcode表現
		return EmojiDecoder.decodeEmoji(options, code)
    }
}
