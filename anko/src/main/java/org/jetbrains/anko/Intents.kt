/*
 * Copyright 2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.anko

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.Fragment
import org.jetbrains.anko.internals.AnkoInternals

inline fun <reified T : Activity> Context.startActivity(vararg params: Pair<String, Any?>) =
    AnkoInternals.internalStartActivity(this, T::class.java, params)

inline fun <reified T : Activity> AnkoContext<*>.startActivity(vararg params: Pair<String, Any?>) =
    AnkoInternals.internalStartActivity(ctx, T::class.java, params)

inline fun <reified T : Activity> Fragment.startActivity(vararg params: Pair<String, Any?>) =
    AnkoInternals.internalStartActivity(requireActivity(), T::class.java, params)

inline fun <reified T : Activity> Activity.startActivityForResult(
    requestCode: Int,
    vararg params: Pair<String, Any?>,
) =
    AnkoInternals.internalStartActivityForResult(this, T::class.java, requestCode, params)

@Suppress("DEPRECATION")
@Deprecated(
    message = "startActivityForResult is deprecated at Android 12",
    replaceWith = ReplaceWith("ActivityResult")
)
inline fun <reified T : Activity> Fragment.startActivityForResult(
    requestCode: Int,
    vararg params: Pair<String, Any?>,
) = startActivityForResult(
    AnkoInternals.createIntent(requireActivity(), T::class.java, params),
    requestCode
)

inline fun <reified T : Service> Context.startService(vararg params: Pair<String, Any?>) =
    AnkoInternals.internalStartService(this, T::class.java, params)

inline fun <reified T : Service> AnkoContext<*>.startService(vararg params: Pair<String, Any?>) =
    AnkoInternals.internalStartService(ctx, T::class.java, params)

inline fun <reified T : Service> Fragment.startService(vararg params: Pair<String, Any?>) =
    AnkoInternals.internalStartService(requireActivity(), T::class.java, params)

inline fun <reified T : Service> Context.stopService(vararg params: Pair<String, Any?>) =
    AnkoInternals.internalStopService(this, T::class.java, params)

inline fun <reified T : Service> AnkoContext<*>.stopService(vararg params: Pair<String, Any?>) =
    AnkoInternals.internalStopService(ctx, T::class.java, params)

inline fun <reified T : Service> Fragment.stopService(vararg params: Pair<String, Any?>) =
    AnkoInternals.internalStopService(requireActivity(), T::class.java, params)

inline fun <reified T : Any> Context.intentFor(vararg params: Pair<String, Any?>): Intent =
    AnkoInternals.createIntent(this, T::class.java, params)

inline fun <reified T : Any> AnkoContext<*>.intentFor(vararg params: Pair<String, Any?>): Intent =
    AnkoInternals.createIntent(ctx, T::class.java, params)

inline fun <reified T : Any> Fragment.intentFor(vararg params: Pair<String, Any?>): Intent =
    AnkoInternals.createIntent(requireActivity(), T::class.java, params)

/**
 * Add the [Intent.FLAG_ACTIVITY_CLEAR_TASK] flag to the [Intent].
 *
 * @return the same intent with the flag applied.
 */
fun Intent.clearTask(): Intent = apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK) }

/**
 * Add the [Intent.FLAG_ACTIVITY_CLEAR_TOP] flag to the [Intent].
 *
 * @return the same intent with the flag applied.
 */
fun Intent.clearTop(): Intent = apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) }

/**
 * Add the [Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET] flag to the [Intent].
 *
 * @return the same intent with the flag applied.
 */
@Deprecated(
    message = "Deprecated in Android",
    replaceWith = ReplaceWith("org.jetbrains.anko.newDocument")
)
fun Intent.clearWhenTaskReset(): Intent = apply {
    @Suppress("DEPRECATION")
    addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET)
}

/**
 * Add the [Intent.FLAG_ACTIVITY_NEW_DOCUMENT] flag to the [Intent].
 *
 * @return the same intent with the flag applied.
 */
fun Intent.newDocument(): Intent = apply {
    addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
}

/**
 * Add the [Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS] flag to the [Intent].
 *
 * @return the same intent with the flag applied.
 */
fun Intent.excludeFromRecents(): Intent =
    apply { addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) }

/**
 * Add the [Intent.FLAG_ACTIVITY_MULTIPLE_TASK] flag to the [Intent].
 *
 * @return the same intent with the flag applied.
 */
fun Intent.multipleTask(): Intent = apply { addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK) }

/**
 * Add the [Intent.FLAG_ACTIVITY_NEW_TASK] flag to the [Intent].
 *
 * @return the same intent with the flag applied.
 */
fun Intent.newTask(): Intent = apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

/**
 * Add the [Intent.FLAG_ACTIVITY_NO_ANIMATION] flag to the [Intent].
 *
 * @return the same intent with the flag applied.
 */
fun Intent.noAnimation(): Intent = apply { addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION) }

/**
 * Add the [Intent.FLAG_ACTIVITY_NO_HISTORY] flag to the [Intent].
 *
 * @return the same intent with the flag applied.
 */
fun Intent.noHistory(): Intent = apply { addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY) }

/**
 * Add the [Intent.FLAG_ACTIVITY_SINGLE_TOP] flag to the [Intent].
 *
 * @return the same intent with the flag applied.
 */
fun Intent.singleTop(): Intent = apply { addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) }

fun AnkoContext<*>.browse(url: String, newTask: Boolean = false) = ctx.browse(url, newTask)

fun Fragment.browse(url: String, newTask: Boolean = false) =
    requireActivity().browse(url, newTask)

fun Context.browse(url: String, newTask: Boolean = false) {
    val intent = Intent(Intent.ACTION_VIEW)
    intent.data = Uri.parse(url)
    if (newTask) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
    // ActivityNotFoundException
}

fun AnkoContext<*>.share(text: String, subject: String = "", title: String? = null) =
    ctx.share(text, subject, title)

fun Fragment.share(text: String, subject: String = "", title: String? = null) =
    requireActivity().share(text, subject, title)

fun Context.share(text: String, subject: String = "", title: String? = null) {
    val intent = Intent(Intent.ACTION_SEND)
    intent.type = "text/plain"
    intent.putExtra(Intent.EXTRA_SUBJECT, subject)
    intent.putExtra(Intent.EXTRA_TEXT, text)
    startActivity(Intent.createChooser(intent, title))
    // ActivityNotFoundException
}

fun AnkoContext<*>.email(email: String, subject: String = "", text: String = "") =
    ctx.email(email, subject, text)

fun Fragment.email(email: String, subject: String = "", text: String = "") =
    requireActivity().email(email, subject, text)

@SuppressLint("QueryPermissionsNeeded")
fun Context.email(email: String, subject: String = "", text: String = ""): Boolean {
    val intent = Intent(Intent.ACTION_SENDTO)
    intent.data = Uri.parse("mailto:")
    intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
    if (subject.isNotEmpty())
        intent.putExtra(Intent.EXTRA_SUBJECT, subject)
    if (text.isNotEmpty())
        intent.putExtra(Intent.EXTRA_TEXT, text)
    if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
        return true
    }
    return false
}

fun AnkoContext<*>.makeCall(number: String) = ctx.makeCall(number)

fun Fragment.makeCall(number: String) = requireActivity().makeCall(number)

fun Context.makeCall(number: String) {
    val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
    startActivity(intent)
    // ActivityNotFoundException
}

fun AnkoContext<*>.sendSMS(number: String, text: String = "") =
    ctx.sendSMS(number, text)

fun Fragment.sendSMS(number: String, text: String = "") =
    requireActivity().sendSMS(number, text)

fun Context.sendSMS(number: String, text: String = "") {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$number"))
    intent.putExtra("sms_body", text)
    startActivity(intent)
    // ActivityNotFoundException
}
