package jp.juggler.subwaytooter.notification

import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.table.SavedAccount

// 通知領域に表示したいデータ
class NotificationData(
    val accessInfo: SavedAccount,
    val notification: TootNotification,
)
