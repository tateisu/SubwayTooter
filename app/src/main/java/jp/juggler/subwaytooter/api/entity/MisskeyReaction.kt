package jp.juggler.subwaytooter.api.entity

import android.graphics.drawable.PictureDrawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.widget.ImageView
import com.bumptech.glide.Glide
import jp.juggler.emoji.EmojiMap
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.api.TootApiClient
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

    fun toSpannableStringBuilder(code: String, options: DecodeOptions): SpannableStringBuilder {
        // 古い形式の絵文字はUnicode絵文字にする
        oldReactions[code]?.let {
            return EmojiDecoder.decodeEmoji(options, it)
        }

        // カスタム絵文字
        val customCode = code.replace(":", "")
        if (customCode != code) {
			val accessInfo = options.linkHelper as? SavedAccount
			if (accessInfo != null) {
				val emojiUrl =
					App1.custom_emoji_lister
						.getMap(accessInfo)
						?.get(customCode)
						?.let {
							if (Pref.bpDisableEmojiAnimation(App1.pref)) {
								it.static_url
							} else {
								it.url
							}
						}
				if (emojiUrl != null)
					return SpannableStringBuilder(code).apply {
						setSpan(
							NetworkEmojiSpan(emojiUrl, scale = options.enlargeCustomEmoji),
							0,
							length,
							Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
						)
					}

			}
		}
		// unicode絵文字、もしくは :xxx: などのshortcode表現
		return EmojiDecoder.decodeEmoji(options, code)
    }

	// Misskey v12 未満はレガシーなリアクションを送ることになる
	suspend fun toLegacyReaction(client: TootApiClient, code: String): String {
		val(ti,ri) = TootInstance.get(client)
		if(ti!=null && ! ti.versionGE(TootInstance.MISSKEY_VERSION_12)){
			val entry = oldReactions.entries.firstOrNull { it.value == code }
			if( entry != null && entry.key != "star") return entry.key
		}
		return code
	}
}
