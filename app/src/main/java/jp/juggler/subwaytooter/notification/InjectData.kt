package jp.juggler.subwaytooter.notification

import jp.juggler.subwaytooter.api.entity.TootNotification
import java.util.ArrayList

class InjectData(
    var accountDbId: Long = 0,
    source: List<TootNotification>,
) {
    // copy to holder
    val list = ArrayList(source)
}
