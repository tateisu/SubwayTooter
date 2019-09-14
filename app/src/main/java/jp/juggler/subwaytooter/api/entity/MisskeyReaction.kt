package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.R

enum class MisskeyReaction(
	val shortcode : String,
	val emojiUtf16 : String,
	val btnDrawableId : Int
) {
	
	Like("like","\ud83d\udc4d",  R.drawable.btn_reaction_like),
	Love("love", "\u2665", R.drawable.btn_reaction_love),
	Laugh("laugh","\ud83d\ude06", R.drawable.btn_reaction_laugh),
	Hmm("hmm", "\ud83e\udd14", R.drawable.btn_reaction_hmm),
	Surprise("surprise", "\ud83d\ude2e", R.drawable.btn_reaction_surprise),
	Congrats("congrats", "\ud83c\udf89",R.drawable.btn_reaction_congrats),
	Angry("angry", "\ud83d\udca2", R.drawable.btn_reaction_angry),
	Confused("confused", "\ud83d\ude25",R.drawable.btn_reaction_confused),
	Rip("rip", "\ud83d\ude07", R.drawable.btn_reaction_rip),
	Pudding("pudding", "\ud83c\udf6e",  R.drawable.btn_reaction_pudding)
	
	;
	
	companion object {
		val shortcodeMap : HashMap<String, MisskeyReaction> by lazy {
			HashMap<String, MisskeyReaction>().apply {
				for(e in MisskeyReaction.values()) {
					put(e.shortcode, e)
				}
			}
		}
	}
}