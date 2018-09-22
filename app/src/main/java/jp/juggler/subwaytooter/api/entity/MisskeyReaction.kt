package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.R

enum class MisskeyReaction(
	val shortcode : String,
	val drawableId : Int,
	val btnDrawableId : Int
) {
	
	Like("like", R.drawable.emj_1f44d,  R.drawable.btn_reaction_like),
	Love("love", R.drawable.emj_2665_fe0f, R.drawable.btn_reaction_love),
	Laugh("laugh", R.drawable.emj_1f606, R.drawable.btn_reaction_laugh),
	Hmm("hmm", R.drawable.emj_1f914, R.drawable.btn_reaction_hmm),
	Surprise("surprise", R.drawable.emj_1f62e, R.drawable.btn_reaction_surprise),
	Congrats("congrats", R.drawable.emj_1f389,R.drawable.btn_reaction_congrats),
	Angry("angry", R.drawable.emj_1f4a2, R.drawable.btn_reaction_angry),
	Confused("confused", R.drawable.emj_1f625,R.drawable.btn_reaction_confused),
	Rip("rip", R.drawable.emj_1f607, R.drawable.btn_reaction_rip),
	Pudding("pudding", R.drawable.emj_1f36e,  R.drawable.btn_reaction_pudding)
	
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