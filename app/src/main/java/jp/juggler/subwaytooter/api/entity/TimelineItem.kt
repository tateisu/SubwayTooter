package jp.juggler.subwaytooter.api.entity

import java.util.concurrent.atomic.AtomicLong

// カラムに表示する要素全てのベースクラス
abstract class TimelineItem {

    companion object {
        val listViewItemIdSeed = AtomicLong(3) // ヘッダ用にいくつか空けておく
    }

    // AdapterView用のIDを採番する
    val listViewItemId: Long = listViewItemIdSeed.incrementAndGet()

    // 大小比較のためのIDを取得する
    // 比較が不要な場合は defaultString を返す
    open fun getOrderId(): EntityId = EntityId.DEFAULT

    fun isInjected() = when (this) {
        is TootStatus -> isFeatured || isPromoted
        else -> false
    }
}
