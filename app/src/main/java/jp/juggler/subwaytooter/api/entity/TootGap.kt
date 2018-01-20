package jp.juggler.subwaytooter.api.entity

class TootGap(
	val max_id : String,
	val since_id : String
) :TimelineItem(){
	
	constructor(max_id : Long, since_id : Long) : this(max_id.toString(), since_id.toString())
}
