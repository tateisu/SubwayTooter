package jp.juggler.subwaytooter.api.entity

import android.graphics.drawable.PictureDrawable
import android.widget.ImageView
import com.bumptech.glide.Glide
import jp.juggler.emoji.EmojiMap
import jp.juggler.subwaytooter.ActMain

private fun findSvgFile(utf16 : String):EmojiMap.EmojiResource{
	return EmojiMap.sUTF16ToEmojiResource[utf16]!!
}

enum class MisskeyReaction(
	val shortcode : String,
	val emojiUtf16 : String,
	private val emojiResource : EmojiMap.EmojiResource = findSvgFile(emojiUtf16),
	val showOnPicker : Boolean = true
) {
	
	Like(
		"like",
		"\ud83d\udc4d"
	),
	Love(
		"love",
		"\u2665"
	),
	Laugh(
		"laugh",
		"\ud83d\ude06"
	),
	Hmm(
		"hmm",
		"\ud83e\udd14"
	),
	Surprise(
		"surprise",
		"\ud83d\ude2e"
	),
	Congrats(
		"congrats",
		"\ud83c\udf89"
	),
	Angry(
		"angry",
		"\ud83d\udca2"
	),
	Confused(
		"confused",
		"\ud83d\ude25"
	),
	Rip(
		"rip",
		"\ud83d\ude07"
	),
	Pudding(
		"pudding",
		"\ud83c\udf6e"
	),
	Star(
		"star",
		"\u2B50",
		showOnPicker = false
	)
	
	;
	
	
	companion object {
		val shortcodeMap : HashMap<String, MisskeyReaction> by lazy {
			HashMap<String, MisskeyReaction>().apply {
				for(e in values()) {
					put(e.shortcode, e)
				}
			}
		}
	}
	
	fun loadToImageView(activity: ActMain, view: ImageView){
		if(emojiResource.isSvg) {
			Glide.with(activity)
				.`as`(PictureDrawable::class.java)
				.load("file:///android_asset/${emojiResource.assetsName}")
				.into(view)
		} else {
			Glide.with(activity)
				.load(emojiResource.drawableId)
				.into(view)
		}
	}

}