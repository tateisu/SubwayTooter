package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.R

enum class MisskeyReaction(val shortcode:String,val drawableId:Int){


	Like("like", R.drawable.emj_1f44d),
	Love("love",R.drawable.emj_2665),
	Laugh("laugh",R.drawable.emj_1f606),
	Hmm("hmm", R.drawable.emj_1f914),
	Surprise("surprise",R.drawable.emj_1f62e),
	Congrats("congrats",R.drawable.emj_1f389),
	Angry("angry",R.drawable.emj_1f4a2),
	Confused("confused",R.drawable.emj_1f625),
	Rip("rip", R.drawable.emj_1f607),
	Pudding("pudding", R.drawable.emj_1f36e)	;

	companion object {
		val shortcodeMap: HashMap<String,MisskeyReaction> by lazy {
			HashMap<String,MisskeyReaction>().apply {
				for( e in MisskeyReaction.values()){
					put( e.shortcode,e)
				}
			}
		}
	}
}