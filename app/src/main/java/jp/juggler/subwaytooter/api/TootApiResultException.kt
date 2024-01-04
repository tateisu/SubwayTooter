package jp.juggler.subwaytooter.api

import android.content.Context
import androidx.annotation.StringRes

class TootApiResultException(val result: TootApiResult?) :
    Exception(result?.error ?: "cancelled.") {
    constructor(error: String) : this(TootApiResult(error))
}

fun errorApiResult(result: TootApiResult): Nothing =
    throw TootApiResultException(result)

fun errorApiResult(error: String): Nothing =
    throw TootApiResultException(error)

fun Context.errorApiResult(@StringRes stringId: Int, vararg args: Any?): Nothing =
    errorApiResult(getString(stringId, *args))
