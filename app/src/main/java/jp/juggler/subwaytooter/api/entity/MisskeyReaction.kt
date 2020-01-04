package jp.juggler.subwaytooter.api.entity

import android.graphics.drawable.PictureDrawable
import android.widget.ImageView
import com.bumptech.glide.Glide
import jp.juggler.emoji.EmojiMap
import jp.juggler.subwaytooter.ActMain

private fun findSvgFile(utf16 : String):EmojiMap.EmojiResource{
	return EmojiMap.sUTF16ToEmojiResource[utf16]!!
}

class MisskeyReaction(
	val shortcode : String,
	val emojiUtf16 : String,
	private val emojiResource : EmojiMap.EmojiResource = findSvgFile(emojiUtf16),
	val showOnPicker : Boolean = true
) {
	companion object{
		private val LIST = listOf(
			MisskeyReaction(
				"like",
				"\ud83d\udc4d"
			),
			MisskeyReaction(
				"love",
				"\u2665"
			),
			MisskeyReaction(
				"laugh",
				"\ud83d\ude06"
			),
			MisskeyReaction(
				"hmm",
				"\ud83e\udd14"
			),
			MisskeyReaction(
				"surprise",
				"\ud83d\ude2e"
			),
			MisskeyReaction(
				"congrats",
				"\ud83c\udf89"
			),
			MisskeyReaction(
				"angry",
				"\ud83d\udca2"
			),
			MisskeyReaction(
				"confused",
				"\ud83d\ude25"
			),
			MisskeyReaction(
				"rip",
				"\ud83d\ude07"
			),
			MisskeyReaction(
				"pudding",
				"\ud83c\udf6e"
			),
			MisskeyReaction(
				"star",
				"\u2B50",
				showOnPicker = false
			)
		)
		
		fun values() =LIST
		
		val shortcodeMap = HashMap<String, MisskeyReaction>().apply{
			LIST.forEach { put(it.shortcode,it) }
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