package jp.juggler.util.os

import android.content.Context
import androidx.annotation.StringRes

/**
 * インストゥルメントテストのContextは
 * applicationContext がnullを返す。
 * この場合は元のcontextを補うのがベストだろう。
 */
val Context.applicationContextSafe: Context
    get() = try {
        applicationContext ?: this
    } catch (ex: Throwable) {
        // applicationContextへのアクセスは例外を出すことがある
        this
    }

fun Context.error(@StringRes resId: Int, vararg args: Any?): Nothing =
    error(getString(resId, *args))
