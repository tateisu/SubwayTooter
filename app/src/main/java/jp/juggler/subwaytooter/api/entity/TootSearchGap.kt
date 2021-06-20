package jp.juggler.subwaytooter.api.entity

class TootSearchGap(val type: SearchType) : TimelineItem() {

    enum class SearchType {
        Hashtag,
        Account,
        Status
    }

    override fun getOrderId(): EntityId {
        return EntityId.DEFAULT
    }
    //	constructor(max_id : Long, since_id : Long) : this(EntityIdLong(max_id), EntityIdLong(since_id))
}
