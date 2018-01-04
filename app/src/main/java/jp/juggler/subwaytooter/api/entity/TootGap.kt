package jp.juggler.subwaytooter.api.entity

class TootGap {
	
	val max_id : String
	val since_id : String
	
	constructor(max_id : String, since_id : String) {
		this.max_id = max_id
		this.since_id = since_id
	}
	
	constructor(max_id : Long, since_id : Long) {
		this.max_id = max_id.toString()
		this.since_id = since_id.toString()
	}
}
