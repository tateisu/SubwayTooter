package jp.juggler.subwaytooter.api.entity

class TootGap(
    var maxId: EntityId?, // TLの先頭にギャップを置くかもしれない
    val sinceId: EntityId,
) : TimelineItem() {

    companion object {
        fun mayNull(maxId: EntityId?, sinceId: EntityId?) =
            sinceId?.let { TootGap(maxId, it) }
    }

    override fun getOrderId(): EntityId = sinceId

    //	constructor(max_id : Long, since_id : Long) : this(EntityIdLong(max_id), EntityIdLong(since_id))
}
