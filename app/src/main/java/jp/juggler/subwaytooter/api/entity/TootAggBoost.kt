package jp.juggler.subwaytooter.api.entity

class TootAggBoost(
    val originalStatus: TootStatus,
    val boosterStatuses : List<TootStatus>
) : TimelineItem()