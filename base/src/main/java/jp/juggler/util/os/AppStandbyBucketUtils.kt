package jp.juggler.util.os

import android.app.ActivityManager
import jp.juggler.util.log.LogCategory

private val log = LogCategory("AppStandbyCheck")

private val importanceMap = listOf(
    100 to "IMPORTANCE_FOREGROUND",
    125 to "IMPORTANCE_FOREGROUND_SERVICE",
    130 to "IMPORTANCE_PERCEPTIBLE_PRE_26",
    150 to "IMPORTANCE_TOP_SLEEPING_PRE_28",
    170 to "IMPORTANCE_CANT_SAVE_STATE_PRE_26",
    200 to "IMPORTANCE_VISIBLE",
    230 to "IMPORTANCE_PERCEPTIBLE",
    300 to "IMPORTANCE_SERVICE",
    325 to "IMPORTANCE_TOP_SLEEPING",
    350 to "IMPORTANCE_CANT_SAVE_STATE",
    400 to "IMPORTANCE_CACHED",
    500 to "IMPORTANCE_EMPTY",
    1000 to "IMPORTANCE_GONE",
)

fun importanceString(n: Int): String =
    importanceMap.firstOrNull { it.first >= n }?.second ?: "(not found)"

fun checkAppForeground(caption: String) {
    val appProcessInfo = ActivityManager.RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(appProcessInfo)
    when (val importance = appProcessInfo.importance) {
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE,
        -> {
            log.i("$caption: app is foreground. $importance ${importanceString(importance)} thread=${Thread.currentThread().name}")
        }
        else -> {
            log.w("$caption: app is background. $importance ${importanceString(importance)} thread=${Thread.currentThread().name}")
        }
    }
}
