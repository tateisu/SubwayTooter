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

import android.content.Context
import android.widget.Toast
import androidx.fragment.app.Fragment

/**
 * Display the simple Toast message with the [Toast.LENGTH_SHORT] duration.
 *
 * @param message the message text resource.
 */
fun AnkoContext<*>.toast(message: Int) = ctx.toast(message)

/**
 * Display the simple Toast message with the [Toast.LENGTH_SHORT] duration.
 *
 * @param message the message text resource.
 */
fun Fragment.toast(message: Int) = requireContext().toast(message)

/**
 * Display the simple Toast message with the [Toast.LENGTH_SHORT] duration.
 *
 * @param message the message text resource.
 */
fun Context.toast(message: Int): Toast = Toast
    .makeText(this, message, Toast.LENGTH_SHORT)
    .apply {
        show()
    }

/**
 * Display the simple Toast message with the [Toast.LENGTH_SHORT] duration.
 *
 * @param message the message text.
 */
fun AnkoContext<*>.toast(message: CharSequence) = ctx.toast(message)

/**
 * Display the simple Toast message with the [Toast.LENGTH_SHORT] duration.
 *
 * @param message the message text.
 */
fun Fragment.toast(message: CharSequence) = requireContext().toast(message)

/**
 * Display the simple Toast message with the [Toast.LENGTH_SHORT] duration.
 *
 * @param message the message text.
 */
fun Context.toast(message: CharSequence): Toast = Toast
    .makeText(this, message, Toast.LENGTH_SHORT)
    .apply {
        show()
    }

/**
 * Display the simple Toast message with the [Toast.LENGTH_LONG] duration.
 *
 * @param message the message text resource.
 */
fun AnkoContext<*>.longToast(message: Int) = ctx.longToast(message)

/**
 * Display the simple Toast message with the [Toast.LENGTH_LONG] duration.
 *
 * @param message the message text resource.
 */
fun Fragment.longToast(message: Int) = requireContext().longToast(message)

/**
 * Display the simple Toast message with the [Toast.LENGTH_LONG] duration.
 *
 * @param message the message text resource.
 */
fun Context.longToast(message: Int): Toast = Toast
    .makeText(this, message, Toast.LENGTH_LONG)
    .apply {
        show()
    }

/**
 * Display the simple Toast message with the [Toast.LENGTH_LONG] duration.
 *
 * @param message the message text.
 */
fun AnkoContext<*>.longToast(message: CharSequence) = ctx.longToast(message)

/**
 * Display the simple Toast message with the [Toast.LENGTH_LONG] duration.
 *
 * @param message the message text.
 */
fun Fragment.longToast(message: CharSequence) = requireContext().longToast(message)

/**
 * Display the simple Toast message with the [Toast.LENGTH_LONG] duration.
 *
 * @param message the message text.
 */
fun Context.longToast(message: CharSequence): Toast = Toast
    .makeText(this, message, Toast.LENGTH_LONG)
    .apply {
        show()
    }
