package jp.juggler.subwaytooter.api.entity

class TootGap(
	var max_id : EntityId?, // TLの先頭にギャップを置くかもしれない
	val since_id : EntityId
) :TimelineItem(){
	
	companion object {
		fun mayNull(max_id : EntityId?, since_id : EntityId?) =if(since_id!=null){
			TootGap(max_id,since_id)
		}else {
			null
		}
	}
	
	override fun getOrderId() : EntityId {
		return since_id
	}
	//	constructor(max_id : Long, since_id : Long) : this(EntityIdLong(max_id), EntityIdLong(since_id))
}
