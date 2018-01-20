package jp.juggler.subwaytooter.api.entity

import java.util.concurrent.atomic.AtomicLong

// カラムに表示する要素全てのベースクラス
open class TimelineItem{

	companion object {
		val idSeed = AtomicLong(3) // ヘッダ用にいくつか空けておく
	}

	// AdapterView用のIDを採番する
	val listViewItemId :Long = idSeed.incrementAndGet()
}
