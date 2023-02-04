package jp.juggler.util.os

import android.content.Context

/**
 * インストゥルメントテストのContextは
 * applicationContext がnullを返す。
 * この場合は元のcontextを補うのがベストだろう。
 */
val Context.applicationContextSafe: Context
    get() = applicationContext ?: this
