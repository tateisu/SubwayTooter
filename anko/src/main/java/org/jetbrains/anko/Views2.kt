@file:Suppress("TooManyFunctions", "ClassNaming")
@file:JvmName("Sdk28Views2Kt")

package org.jetbrains.anko

import android.app.*
import android.content.Context
import android.view.ViewManager
import android.widget.*
import androidx.appcompat.widget.*
import androidx.appcompat.widget.SearchView
import org.jetbrains.anko.custom.*

fun ViewManager.expandableListView(): ExpandableListView =
    expandableListView {}

inline fun ViewManager.expandableListView(init: (@AnkoViewDslMarker ExpandableListView).() -> Unit): ExpandableListView {
    return ankoView(`$$Anko$Factories$Sdk28View`.EXPANDABLE_LIST_VIEW, theme = 0) { init() }
}

fun ViewManager.themedExpandableListView(theme: Int = 0): ExpandableListView =
    themedExpandableListView(theme) {}

inline fun ViewManager.themedExpandableListView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker ExpandableListView).() -> Unit,
): ExpandableListView {
    return ankoView(`$$Anko$Factories$Sdk28View`.EXPANDABLE_LIST_VIEW, theme) { init() }
}

fun Context.expandableListView(): ExpandableListView = expandableListView {}
inline fun Context.expandableListView(init: (@AnkoViewDslMarker ExpandableListView).() -> Unit): ExpandableListView {
    return ankoView(`$$Anko$Factories$Sdk28View`.EXPANDABLE_LIST_VIEW, theme = 0) { init() }
}

fun Context.themedExpandableListView(theme: Int = 0): ExpandableListView =
    themedExpandableListView(theme) {}

inline fun Context.themedExpandableListView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker ExpandableListView).() -> Unit,
): ExpandableListView {
    return ankoView(`$$Anko$Factories$Sdk28View`.EXPANDABLE_LIST_VIEW, theme) { init() }
}

fun Activity.expandableListView(): ExpandableListView =
    expandableListView {}

inline fun Activity.expandableListView(init: (@AnkoViewDslMarker ExpandableListView).() -> Unit): ExpandableListView {
    return ankoView(`$$Anko$Factories$Sdk28View`.EXPANDABLE_LIST_VIEW, theme = 0) { init() }
}

fun Activity.themedExpandableListView(theme: Int = 0): ExpandableListView =
    themedExpandableListView(theme) {}

inline fun Activity.themedExpandableListView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker ExpandableListView).() -> Unit,
): ExpandableListView {
    return ankoView(`$$Anko$Factories$Sdk28View`.EXPANDABLE_LIST_VIEW, theme) { init() }
}

fun ViewManager.imageButton(): AppCompatImageButton = imageButton {}
inline fun ViewManager.imageButton(init: (@AnkoViewDslMarker AppCompatImageButton).() -> Unit): AppCompatImageButton {
    return ankoView(`$$Anko$Factories$Sdk28View`.IMAGE_BUTTON, theme = 0) { init() }
}

fun ViewManager.themedImageButton(theme: Int = 0): AppCompatImageButton =
    themedImageButton(theme) {}

inline fun ViewManager.themedImageButton(
    theme: Int = 0,
    init: (@AnkoViewDslMarker AppCompatImageButton).() -> Unit,
): AppCompatImageButton {
    return ankoView(`$$Anko$Factories$Sdk28View`.IMAGE_BUTTON, theme) { init() }
}

fun ViewManager.imageButton(imageDrawable: android.graphics.drawable.Drawable?): AppCompatImageButton {
    return ankoView(`$$Anko$Factories$Sdk28View`.IMAGE_BUTTON, theme = 0) {
        setImageDrawable(imageDrawable)
    }
}

inline fun ViewManager.imageButton(
    imageDrawable: android.graphics.drawable.Drawable?,
    init: (@AnkoViewDslMarker AppCompatImageButton).() -> Unit,
): AppCompatImageButton {
    return ankoView(`$$Anko$Factories$Sdk28View`.IMAGE_BUTTON, theme = 0) {
        init()
        setImageDrawable(imageDrawable)
    }
}

fun ViewManager.themedImageButton(
    imageDrawable: android.graphics.drawable.Drawable?,
    theme: Int,
): AppCompatImageButton {
    return ankoView(`$$Anko$Factories$Sdk28View`.IMAGE_BUTTON, theme) {
        setImageDrawable(imageDrawable)
    }
}

inline fun ViewManager.themedImageButton(
    imageDrawable: android.graphics.drawable.Drawable?,
    theme: Int,
    init: (@AnkoViewDslMarker AppCompatImageButton).() -> Unit,
): AppCompatImageButton {
    return ankoView(`$$Anko$Factories$Sdk28View`.IMAGE_BUTTON, theme) {
        init()
        setImageDrawable(imageDrawable)
    }
}

fun ViewManager.imageButton(imageResource: Int): AppCompatImageButton {
    return ankoView(`$$Anko$Factories$Sdk28View`.IMAGE_BUTTON, theme = 0) {
        setImageResource(imageResource)
    }
}

inline fun ViewManager.imageButton(
    imageResource: Int,
    init: (@AnkoViewDslMarker AppCompatImageButton).() -> Unit,
): AppCompatImageButton {
    return ankoView(`$$Anko$Factories$Sdk28View`.IMAGE_BUTTON, theme = 0) {
        init()
        setImageResource(imageResource)
    }
}

fun ViewManager.themedImageButton(
    imageResource: Int,
    theme: Int,
): AppCompatImageButton {
    return ankoView(`$$Anko$Factories$Sdk28View`.IMAGE_BUTTON, theme) {
        setImageResource(imageResource)
    }
}

inline fun ViewManager.themedImageButton(
    imageResource: Int,
    theme: Int,
    init: (@AnkoViewDslMarker AppCompatImageButton).() -> Unit,
): AppCompatImageButton {
    return ankoView(`$$Anko$Factories$Sdk28View`.IMAGE_BUTTON, theme) {
        init()
        setImageResource(imageResource)
    }
}

fun ViewManager.imageView(): AppCompatImageView = imageView {}
inline fun ViewManager.imageView(init: (@AnkoViewDslMarker AppCompatImageView).() -> Unit): AppCompatImageView {
    return ankoView(`$$Anko$Factories$Sdk28View`.IMAGE_VIEW, theme = 0) { init() }
}

fun ViewManager.themedImageView(theme: Int = 0): AppCompatImageView =
    themedImageView(theme) {}

inline fun ViewManager.themedImageView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker AppCompatImageView).() -> Unit,
): AppCompatImageView {
    return ankoView(`$$Anko$Factories$Sdk28View`.IMAGE_VIEW, theme) { init() }
}

fun ViewManager.imageView(imageDrawable: android.graphics.drawable.Drawable?): AppCompatImageView {
    return ankoView(`$$Anko$Factories$Sdk28View`.IMAGE_VIEW, theme = 0) {
        setImageDrawable(imageDrawable)
    }
}

inline fun ViewManager.imageView(
    imageDrawable: android.graphics.drawable.Drawable?,
    init: (@AnkoViewDslMarker AppCompatImageView).() -> Unit,
): AppCompatImageView {
    return ankoView(`$$Anko$Factories$Sdk28View`.IMAGE_VIEW, theme = 0) {
        init()
        setImageDrawable(imageDrawable)
    }
}

fun ViewManager.themedImageView(
    imageDrawable: android.graphics.drawable.Drawable?,
    theme: Int,
): AppCompatImageView {
    return ankoView(`$$Anko$Factories$Sdk28View`.IMAGE_VIEW, theme) {
        setImageDrawable(imageDrawable)
    }
}

inline fun ViewManager.themedImageView(
    imageDrawable: android.graphics.drawable.Drawable?,
    theme: Int,
    init: (@AnkoViewDslMarker AppCompatImageView).() -> Unit,
): AppCompatImageView {
    return ankoView(`$$Anko$Factories$Sdk28View`.IMAGE_VIEW, theme) {
        init()
        setImageDrawable(imageDrawable)
    }
}

fun ViewManager.imageView(imageResource: Int): AppCompatImageView {
    return ankoView(`$$Anko$Factories$Sdk28View`.IMAGE_VIEW, theme = 0) {
        setImageResource(imageResource)
    }
}

inline fun ViewManager.imageView(
    imageResource: Int,
    init: (@AnkoViewDslMarker AppCompatImageView).() -> Unit,
): AppCompatImageView {
    return ankoView(`$$Anko$Factories$Sdk28View`.IMAGE_VIEW, theme = 0) {
        init()
        setImageResource(imageResource)
    }
}

fun ViewManager.themedImageView(imageResource: Int, theme: Int): AppCompatImageView {
    return ankoView(`$$Anko$Factories$Sdk28View`.IMAGE_VIEW, theme) {
        setImageResource(imageResource)
    }
}

inline fun ViewManager.themedImageView(
    imageResource: Int,
    theme: Int,
    init: (@AnkoViewDslMarker AppCompatImageView).() -> Unit,
): AppCompatImageView {
    return ankoView(`$$Anko$Factories$Sdk28View`.IMAGE_VIEW, theme) {
        init()
        setImageResource(imageResource)
    }
}

fun ViewManager.listView(): ListView = listView {}
inline fun ViewManager.listView(init: (@AnkoViewDslMarker ListView).() -> Unit): ListView {
    return ankoView(`$$Anko$Factories$Sdk28View`.LIST_VIEW, theme = 0) { init() }
}

fun ViewManager.themedListView(theme: Int = 0): ListView =
    themedListView(theme) {}

inline fun ViewManager.themedListView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker ListView).() -> Unit,
): ListView {
    return ankoView(`$$Anko$Factories$Sdk28View`.LIST_VIEW, theme) { init() }
}

fun Context.listView(): ListView = listView {}
inline fun Context.listView(init: (@AnkoViewDslMarker ListView).() -> Unit): ListView {
    return ankoView(`$$Anko$Factories$Sdk28View`.LIST_VIEW, theme = 0) { init() }
}

fun Context.themedListView(theme: Int = 0): ListView =
    themedListView(theme) {}

inline fun Context.themedListView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker ListView).() -> Unit,
): ListView {
    return ankoView(`$$Anko$Factories$Sdk28View`.LIST_VIEW, theme) { init() }
}

fun Activity.listView(): ListView = listView {}
inline fun Activity.listView(init: (@AnkoViewDslMarker ListView).() -> Unit): ListView {
    return ankoView(`$$Anko$Factories$Sdk28View`.LIST_VIEW, theme = 0) { init() }
}

fun Activity.themedListView(theme: Int = 0): ListView =
    themedListView(theme) {}

inline fun Activity.themedListView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker ListView).() -> Unit,
): ListView {
    return ankoView(`$$Anko$Factories$Sdk28View`.LIST_VIEW, theme) { init() }
}

fun ViewManager.multiAutoCompleteTextView(): AppCompatMultiAutoCompleteTextView =
    multiAutoCompleteTextView {}

inline fun ViewManager.multiAutoCompleteTextView(init: (@AnkoViewDslMarker AppCompatMultiAutoCompleteTextView).() -> Unit): AppCompatMultiAutoCompleteTextView {
    return ankoView(
        `$$Anko$Factories$Sdk28View`.MULTI_AUTO_COMPLETE_TEXT_VIEW,
        theme = 0
    ) { init() }
}

fun ViewManager.themedMultiAutoCompleteTextView(theme: Int = 0): AppCompatMultiAutoCompleteTextView =
    themedMultiAutoCompleteTextView(theme) {}

inline fun ViewManager.themedMultiAutoCompleteTextView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker AppCompatMultiAutoCompleteTextView).() -> Unit,
): AppCompatMultiAutoCompleteTextView {
    return ankoView(`$$Anko$Factories$Sdk28View`.MULTI_AUTO_COMPLETE_TEXT_VIEW, theme) { init() }
}

fun ViewManager.numberPicker(): NumberPicker = numberPicker {}
inline fun ViewManager.numberPicker(init: (@AnkoViewDslMarker NumberPicker).() -> Unit): NumberPicker {
    return ankoView(`$$Anko$Factories$Sdk28View`.NUMBER_PICKER, theme = 0) { init() }
}

fun ViewManager.themedNumberPicker(theme: Int = 0): NumberPicker =
    themedNumberPicker(theme) {}

inline fun ViewManager.themedNumberPicker(
    theme: Int = 0,
    init: (@AnkoViewDslMarker NumberPicker).() -> Unit,
): NumberPicker {
    return ankoView(`$$Anko$Factories$Sdk28View`.NUMBER_PICKER, theme) { init() }
}

fun Context.numberPicker(): NumberPicker = numberPicker {}
inline fun Context.numberPicker(init: (@AnkoViewDslMarker NumberPicker).() -> Unit): NumberPicker {
    return ankoView(`$$Anko$Factories$Sdk28View`.NUMBER_PICKER, theme = 0) { init() }
}

fun Context.themedNumberPicker(theme: Int = 0): NumberPicker =
    themedNumberPicker(theme) {}

inline fun Context.themedNumberPicker(
    theme: Int = 0,
    init: (@AnkoViewDslMarker NumberPicker).() -> Unit,
): NumberPicker {
    return ankoView(`$$Anko$Factories$Sdk28View`.NUMBER_PICKER, theme) { init() }
}

fun Activity.numberPicker(): NumberPicker = numberPicker {}
inline fun Activity.numberPicker(init: (@AnkoViewDslMarker NumberPicker).() -> Unit): NumberPicker {
    return ankoView(`$$Anko$Factories$Sdk28View`.NUMBER_PICKER, theme = 0) { init() }
}

fun Activity.themedNumberPicker(theme: Int = 0): NumberPicker =
    themedNumberPicker(theme) {}

inline fun Activity.themedNumberPicker(
    theme: Int = 0,
    init: (@AnkoViewDslMarker NumberPicker).() -> Unit,
): NumberPicker {
    return ankoView(`$$Anko$Factories$Sdk28View`.NUMBER_PICKER, theme) { init() }
}

fun ViewManager.progressBar(): ProgressBar = progressBar {}
inline fun ViewManager.progressBar(init: (@AnkoViewDslMarker ProgressBar).() -> Unit): ProgressBar {
    return ankoView(`$$Anko$Factories$Sdk28View`.PROGRESS_BAR, theme = 0) { init() }
}

fun ViewManager.themedProgressBar(theme: Int = 0): ProgressBar =
    themedProgressBar(theme) {}

inline fun ViewManager.themedProgressBar(
    theme: Int = 0,
    init: (@AnkoViewDslMarker ProgressBar).() -> Unit,
): ProgressBar {
    return ankoView(`$$Anko$Factories$Sdk28View`.PROGRESS_BAR, theme) { init() }
}

fun ViewManager.quickContactBadge(): QuickContactBadge =
    quickContactBadge {}

inline fun ViewManager.quickContactBadge(init: (@AnkoViewDslMarker QuickContactBadge).() -> Unit): QuickContactBadge {
    return ankoView(`$$Anko$Factories$Sdk28View`.QUICK_CONTACT_BADGE, theme = 0) { init() }
}

fun ViewManager.themedQuickContactBadge(theme: Int = 0): QuickContactBadge =
    themedQuickContactBadge(theme) {}

inline fun ViewManager.themedQuickContactBadge(
    theme: Int = 0,
    init: (@AnkoViewDslMarker QuickContactBadge).() -> Unit,
): QuickContactBadge {
    return ankoView(`$$Anko$Factories$Sdk28View`.QUICK_CONTACT_BADGE, theme) { init() }
}

fun ViewManager.radioButton(): AppCompatRadioButton = radioButton {}
inline fun ViewManager.radioButton(init: (@AnkoViewDslMarker AppCompatRadioButton).() -> Unit): AppCompatRadioButton {
    return ankoView(`$$Anko$Factories$Sdk28View`.RADIO_BUTTON, theme = 0) { init() }
}

fun ViewManager.themedRadioButton(theme: Int = 0): AppCompatRadioButton =
    themedRadioButton(theme) {}

inline fun ViewManager.themedRadioButton(
    theme: Int = 0,
    init: (@AnkoViewDslMarker AppCompatRadioButton).() -> Unit,
): AppCompatRadioButton {
    return ankoView(`$$Anko$Factories$Sdk28View`.RADIO_BUTTON, theme) { init() }
}

fun ViewManager.ratingBar(): AppCompatRatingBar = ratingBar {}
inline fun ViewManager.ratingBar(init: (@AnkoViewDslMarker AppCompatRatingBar).() -> Unit): AppCompatRatingBar {
    return ankoView(`$$Anko$Factories$Sdk28View`.RATING_BAR, theme = 0) { init() }
}

fun ViewManager.themedRatingBar(theme: Int = 0): AppCompatRatingBar =
    themedRatingBar(theme) {}

inline fun ViewManager.themedRatingBar(
    theme: Int = 0,
    init: (@AnkoViewDslMarker AppCompatRatingBar).() -> Unit,
): AppCompatRatingBar {
    return ankoView(`$$Anko$Factories$Sdk28View`.RATING_BAR, theme) { init() }
}

fun ViewManager.searchView(): SearchView = searchView {}
inline fun ViewManager.searchView(init: (@AnkoViewDslMarker SearchView).() -> Unit): SearchView {
    return ankoView(`$$Anko$Factories$Sdk28View`.SEARCH_VIEW, theme = 0) { init() }
}

fun ViewManager.themedSearchView(theme: Int = 0): SearchView =
    themedSearchView(theme) {}

inline fun ViewManager.themedSearchView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker SearchView).() -> Unit,
): SearchView {
    return ankoView(`$$Anko$Factories$Sdk28View`.SEARCH_VIEW, theme) { init() }
}

fun Context.searchView(): SearchView = searchView {}
inline fun Context.searchView(init: (@AnkoViewDslMarker SearchView).() -> Unit): SearchView {
    return ankoView(`$$Anko$Factories$Sdk28View`.SEARCH_VIEW, theme = 0) { init() }
}

fun Context.themedSearchView(theme: Int = 0): SearchView =
    themedSearchView(theme) {}

inline fun Context.themedSearchView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker SearchView).() -> Unit,
): SearchView {
    return ankoView(`$$Anko$Factories$Sdk28View`.SEARCH_VIEW, theme) { init() }
}

fun Activity.searchView(): SearchView = searchView {}
inline fun Activity.searchView(init: (@AnkoViewDslMarker SearchView).() -> Unit): SearchView {
    return ankoView(`$$Anko$Factories$Sdk28View`.SEARCH_VIEW, theme = 0) { init() }
}

fun Activity.themedSearchView(theme: Int = 0): SearchView =
    themedSearchView(theme) {}

inline fun Activity.themedSearchView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker SearchView).() -> Unit,
): SearchView {
    return ankoView(`$$Anko$Factories$Sdk28View`.SEARCH_VIEW, theme) { init() }
}

fun ViewManager.seekBar(): AppCompatSeekBar = seekBar {}
inline fun ViewManager.seekBar(init: (@AnkoViewDslMarker AppCompatSeekBar).() -> Unit): AppCompatSeekBar {
    return ankoView(`$$Anko$Factories$Sdk28View`.SEEK_BAR, theme = 0) { init() }
}

fun ViewManager.themedSeekBar(theme: Int = 0): AppCompatSeekBar =
    themedSeekBar(theme) {}

inline fun ViewManager.themedSeekBar(
    theme: Int = 0,
    init: (@AnkoViewDslMarker AppCompatSeekBar).() -> Unit,
): AppCompatSeekBar {
    return ankoView(`$$Anko$Factories$Sdk28View`.SEEK_BAR, theme) { init() }
}

//inline fun ViewManager.slidingDrawer(): android.widget.SlidingDrawer = slidingDrawer() {}
//inline fun ViewManager.slidingDrawer(init: (@AnkoViewDslMarker android.widget.SlidingDrawer).() -> Unit): android.widget.SlidingDrawer {
//    return ankoView(`$$Anko$Factories$Sdk28View`.SLIDING_DRAWER, theme = 0) { init() }
//}
//
//inline fun ViewManager.themedSlidingDrawer(theme: Int = 0): android.widget.SlidingDrawer =
//    themedSlidingDrawer(theme) {}
//
//inline fun ViewManager.themedSlidingDrawer(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker android.widget.SlidingDrawer).() -> Unit,
//): android.widget.SlidingDrawer {
//    return ankoView(`$$Anko$Factories$Sdk28View`.SLIDING_DRAWER, theme) { init() }
//}
//
//inline fun Context.slidingDrawer(): android.widget.SlidingDrawer = slidingDrawer() {}
//inline fun Context.slidingDrawer(init: (@AnkoViewDslMarker android.widget.SlidingDrawer).() -> Unit): android.widget.SlidingDrawer {
//    return ankoView(`$$Anko$Factories$Sdk28View`.SLIDING_DRAWER, theme = 0) { init() }
//}
//
//inline fun Context.themedSlidingDrawer(theme: Int = 0): android.widget.SlidingDrawer =
//    themedSlidingDrawer(theme) {}
//
//inline fun Context.themedSlidingDrawer(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker android.widget.SlidingDrawer).() -> Unit,
//): android.widget.SlidingDrawer {
//    return ankoView(`$$Anko$Factories$Sdk28View`.SLIDING_DRAWER, theme) { init() }
//}
//
//inline fun Activity.slidingDrawer(): android.widget.SlidingDrawer = slidingDrawer() {}
//inline fun Activity.slidingDrawer(init: (@AnkoViewDslMarker android.widget.SlidingDrawer).() -> Unit): android.widget.SlidingDrawer {
//    return ankoView(`$$Anko$Factories$Sdk28View`.SLIDING_DRAWER, theme = 0) { init() }
//}
//
//inline fun Activity.themedSlidingDrawer(theme: Int = 0): android.widget.SlidingDrawer =
//    themedSlidingDrawer(theme) {}
//
//inline fun Activity.themedSlidingDrawer(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker android.widget.SlidingDrawer).() -> Unit,
//): android.widget.SlidingDrawer {
//    return ankoView(`$$Anko$Factories$Sdk28View`.SLIDING_DRAWER, theme) { init() }
//}

fun ViewManager.space(): Space = space {}
inline fun ViewManager.space(init: (@AnkoViewDslMarker Space).() -> Unit): Space {
    return ankoView(`$$Anko$Factories$Sdk28View`.SPACE, theme = 0) { init() }
}

fun ViewManager.themedSpace(theme: Int = 0): Space = themedSpace(theme) {}
inline fun ViewManager.themedSpace(
    theme: Int = 0,
    init: (@AnkoViewDslMarker Space).() -> Unit,
): Space {
    return ankoView(`$$Anko$Factories$Sdk28View`.SPACE, theme) { init() }
}

fun ViewManager.spinner(): AppCompatSpinner = spinner {}
inline fun ViewManager.spinner(init: (@AnkoViewDslMarker AppCompatSpinner).() -> Unit): AppCompatSpinner {
    return ankoView(`$$Anko$Factories$Sdk28View`.SPINNER, theme = 0) { init() }
}

fun ViewManager.themedSpinner(theme: Int = 0): AppCompatSpinner =
    themedSpinner(theme) {}

inline fun ViewManager.themedSpinner(
    theme: Int = 0,
    init: (@AnkoViewDslMarker AppCompatSpinner).() -> Unit,
): AppCompatSpinner {
    return ankoView(`$$Anko$Factories$Sdk28View`.SPINNER, theme) { init() }
}

fun Context.spinner(): AppCompatSpinner = spinner {}
inline fun Context.spinner(init: (@AnkoViewDslMarker AppCompatSpinner).() -> Unit): AppCompatSpinner {
    return ankoView(`$$Anko$Factories$Sdk28View`.SPINNER, theme = 0) { init() }
}

fun Context.themedSpinner(theme: Int = 0): AppCompatSpinner = themedSpinner(theme) {}
inline fun Context.themedSpinner(
    theme: Int = 0,
    init: (@AnkoViewDslMarker AppCompatSpinner).() -> Unit,
): AppCompatSpinner {
    return ankoView(`$$Anko$Factories$Sdk28View`.SPINNER, theme) { init() }
}

fun Activity.spinner(): AppCompatSpinner = spinner {}
inline fun Activity.spinner(init: (@AnkoViewDslMarker AppCompatSpinner).() -> Unit): AppCompatSpinner {
    return ankoView(`$$Anko$Factories$Sdk28View`.SPINNER, theme = 0) { init() }
}

fun Activity.themedSpinner(theme: Int = 0): AppCompatSpinner = themedSpinner(theme) {}
inline fun Activity.themedSpinner(
    theme: Int = 0,
    init: (@AnkoViewDslMarker AppCompatSpinner).() -> Unit,
): AppCompatSpinner {
    return ankoView(`$$Anko$Factories$Sdk28View`.SPINNER, theme) { init() }
}

fun ViewManager.stackView(): StackView = stackView {}
inline fun ViewManager.stackView(init: (@AnkoViewDslMarker StackView).() -> Unit): StackView {
    return ankoView(`$$Anko$Factories$Sdk28View`.STACK_VIEW, theme = 0) { init() }
}

fun ViewManager.themedStackView(theme: Int = 0): StackView =
    themedStackView(theme) {}

inline fun ViewManager.themedStackView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker StackView).() -> Unit,
): StackView {
    return ankoView(`$$Anko$Factories$Sdk28View`.STACK_VIEW, theme) { init() }
}

fun Context.stackView(): StackView = stackView {}
inline fun Context.stackView(init: (@AnkoViewDslMarker StackView).() -> Unit): StackView {
    return ankoView(`$$Anko$Factories$Sdk28View`.STACK_VIEW, theme = 0) { init() }
}

fun Context.themedStackView(theme: Int = 0): StackView =
    themedStackView(theme) {}

inline fun Context.themedStackView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker StackView).() -> Unit,
): StackView {
    return ankoView(`$$Anko$Factories$Sdk28View`.STACK_VIEW, theme) { init() }
}

fun Activity.stackView(): StackView = stackView {}
inline fun Activity.stackView(init: (@AnkoViewDslMarker StackView).() -> Unit): StackView {
    return ankoView(`$$Anko$Factories$Sdk28View`.STACK_VIEW, theme = 0) { init() }
}

fun Activity.themedStackView(theme: Int = 0): StackView =
    themedStackView(theme) {}

inline fun Activity.themedStackView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker StackView).() -> Unit,
): StackView {
    return ankoView(`$$Anko$Factories$Sdk28View`.STACK_VIEW, theme) { init() }
}

fun ViewManager.switch(): SwitchCompat = switch {}
inline fun ViewManager.switch(init: (@AnkoViewDslMarker SwitchCompat).() -> Unit): SwitchCompat {
    return ankoView(`$$Anko$Factories$Sdk28View`.SWITCH, theme = 0) { init() }
}

fun ViewManager.themedSwitch(theme: Int = 0): SwitchCompat = themedSwitch(theme) {}
inline fun ViewManager.themedSwitch(
    theme: Int = 0,
    init: (@AnkoViewDslMarker SwitchCompat).() -> Unit,
): SwitchCompat {
    return ankoView(`$$Anko$Factories$Sdk28View`.SWITCH, theme) { init() }
}

//inline fun ViewManager.tabHost(): android.widget.TabHost = tabHost() {}
//inline fun ViewManager.tabHost(init: (@AnkoViewDslMarker android.widget.TabHost).() -> Unit): android.widget.TabHost {
//    return ankoView(`$$Anko$Factories$Sdk28View`.TAB_HOST, theme = 0) { init() }
//}
//
//inline fun ViewManager.themedTabHost(theme: Int = 0): android.widget.TabHost =
//    themedTabHost(theme) {}
//
//inline fun ViewManager.themedTabHost(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker android.widget.TabHost).() -> Unit,
//): android.widget.TabHost {
//    return ankoView(`$$Anko$Factories$Sdk28View`.TAB_HOST, theme) { init() }
//}
//
//inline fun Context.tabHost(): android.widget.TabHost = tabHost() {}
//inline fun Context.tabHost(init: (@AnkoViewDslMarker android.widget.TabHost).() -> Unit): android.widget.TabHost {
//    return ankoView(`$$Anko$Factories$Sdk28View`.TAB_HOST, theme = 0) { init() }
//}
//
//inline fun Context.themedTabHost(theme: Int = 0): android.widget.TabHost = themedTabHost(theme) {}
//inline fun Context.themedTabHost(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker android.widget.TabHost).() -> Unit,
//): android.widget.TabHost {
//    return ankoView(`$$Anko$Factories$Sdk28View`.TAB_HOST, theme) { init() }
//}
//
//inline fun Activity.tabHost(): android.widget.TabHost = tabHost() {}
//inline fun Activity.tabHost(init: (@AnkoViewDslMarker android.widget.TabHost).() -> Unit): android.widget.TabHost {
//    return ankoView(`$$Anko$Factories$Sdk28View`.TAB_HOST, theme = 0) { init() }
//}
//
//inline fun Activity.themedTabHost(theme: Int = 0): android.widget.TabHost = themedTabHost(theme) {}
//inline fun Activity.themedTabHost(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker android.widget.TabHost).() -> Unit,
//): android.widget.TabHost {
//    return ankoView(`$$Anko$Factories$Sdk28View`.TAB_HOST, theme) { init() }
//}
//
//inline fun ViewManager.tabWidget(): android.widget.TabWidget = tabWidget() {}
//inline fun ViewManager.tabWidget(init: (@AnkoViewDslMarker android.widget.TabWidget).() -> Unit): android.widget.TabWidget {
//    return ankoView(`$$Anko$Factories$Sdk28View`.TAB_WIDGET, theme = 0) { init() }
//}
//
//inline fun ViewManager.themedTabWidget(theme: Int = 0): android.widget.TabWidget =
//    themedTabWidget(theme) {}
//
//inline fun ViewManager.themedTabWidget(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker android.widget.TabWidget).() -> Unit,
//): android.widget.TabWidget {
//    return ankoView(`$$Anko$Factories$Sdk28View`.TAB_WIDGET, theme) { init() }
//}
//
//inline fun Context.tabWidget(): android.widget.TabWidget = tabWidget() {}
//inline fun Context.tabWidget(init: (@AnkoViewDslMarker android.widget.TabWidget).() -> Unit): android.widget.TabWidget {
//    return ankoView(`$$Anko$Factories$Sdk28View`.TAB_WIDGET, theme = 0) { init() }
//}
//
//inline fun Context.themedTabWidget(theme: Int = 0): android.widget.TabWidget =
//    themedTabWidget(theme) {}
//
//inline fun Context.themedTabWidget(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker android.widget.TabWidget).() -> Unit,
//): android.widget.TabWidget {
//    return ankoView(`$$Anko$Factories$Sdk28View`.TAB_WIDGET, theme) { init() }
//}
//
//inline fun Activity.tabWidget(): android.widget.TabWidget = tabWidget() {}
//inline fun Activity.tabWidget(init: (@AnkoViewDslMarker android.widget.TabWidget).() -> Unit): android.widget.TabWidget {
//    return ankoView(`$$Anko$Factories$Sdk28View`.TAB_WIDGET, theme = 0) { init() }
//}
//
//inline fun Activity.themedTabWidget(theme: Int = 0): android.widget.TabWidget =
//    themedTabWidget(theme) {}
//
//inline fun Activity.themedTabWidget(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker android.widget.TabWidget).() -> Unit,
//): android.widget.TabWidget {
//    return ankoView(`$$Anko$Factories$Sdk28View`.TAB_WIDGET, theme) { init() }
//}

fun ViewManager.textClock(): TextClock = textClock {}
inline fun ViewManager.textClock(init: (@AnkoViewDslMarker TextClock).() -> Unit): TextClock {
    return ankoView(`$$Anko$Factories$Sdk28View`.TEXT_CLOCK, theme = 0) { init() }
}

fun ViewManager.themedTextClock(theme: Int = 0): TextClock =
    themedTextClock(theme) {}

inline fun ViewManager.themedTextClock(
    theme: Int = 0,
    init: (@AnkoViewDslMarker TextClock).() -> Unit,
): TextClock {
    return ankoView(`$$Anko$Factories$Sdk28View`.TEXT_CLOCK, theme) { init() }
}

fun ViewManager.textView(): AppCompatTextView = textView {}
inline fun ViewManager.textView(init: (@AnkoViewDslMarker AppCompatTextView).() -> Unit): AppCompatTextView {
    return ankoView(`$$Anko$Factories$Sdk28View`.TEXT_VIEW, theme = 0) { init() }
}

fun ViewManager.themedTextView(theme: Int = 0): AppCompatTextView =
    themedTextView(theme) {}

inline fun ViewManager.themedTextView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker AppCompatTextView).() -> Unit,
): AppCompatTextView {
    return ankoView(`$$Anko$Factories$Sdk28View`.TEXT_VIEW, theme) { init() }
}

fun ViewManager.textView(text: CharSequence?): AppCompatTextView {
    return ankoView(`$$Anko$Factories$Sdk28View`.TEXT_VIEW, theme = 0) {
        setText(text)
    }
}

inline fun ViewManager.textView(
    text: CharSequence?,
    init: (@AnkoViewDslMarker AppCompatTextView).() -> Unit,
): AppCompatTextView {
    return ankoView(`$$Anko$Factories$Sdk28View`.TEXT_VIEW, theme = 0) {
        init()
        setText(text)
    }
}

fun ViewManager.themedTextView(text: CharSequence?, theme: Int): AppCompatTextView {
    return ankoView(`$$Anko$Factories$Sdk28View`.TEXT_VIEW, theme) {
        setText(text)
    }
}

inline fun ViewManager.themedTextView(
    text: CharSequence?,
    theme: Int,
    init: (@AnkoViewDslMarker AppCompatTextView).() -> Unit,
): AppCompatTextView {
    return ankoView(`$$Anko$Factories$Sdk28View`.TEXT_VIEW, theme) {
        init()
        setText(text)
    }
}

fun ViewManager.textView(text: Int): AppCompatTextView {
    return ankoView(`$$Anko$Factories$Sdk28View`.TEXT_VIEW, theme = 0) {
        setText(text)
    }
}

inline fun ViewManager.textView(
    text: Int,
    init: (@AnkoViewDslMarker AppCompatTextView).() -> Unit,
): AppCompatTextView {
    return ankoView(`$$Anko$Factories$Sdk28View`.TEXT_VIEW, theme = 0) {
        init()
        setText(text)
    }
}

fun ViewManager.themedTextView(text: Int, theme: Int): AppCompatTextView {
    return ankoView(`$$Anko$Factories$Sdk28View`.TEXT_VIEW, theme) {
        setText(text)
    }
}

inline fun ViewManager.themedTextView(
    text: Int,
    theme: Int,
    init: (@AnkoViewDslMarker AppCompatTextView).() -> Unit,
): AppCompatTextView {
    return ankoView(`$$Anko$Factories$Sdk28View`.TEXT_VIEW, theme) {
        init()
        setText(text)
    }
}

fun ViewManager.timePicker(): TimePicker = timePicker {}
inline fun ViewManager.timePicker(init: (@AnkoViewDslMarker TimePicker).() -> Unit): TimePicker {
    return ankoView(`$$Anko$Factories$Sdk28View`.TIME_PICKER, theme = 0) { init() }
}

fun ViewManager.themedTimePicker(theme: Int = 0): TimePicker =
    themedTimePicker(theme) {}

inline fun ViewManager.themedTimePicker(
    theme: Int = 0,
    init: (@AnkoViewDslMarker TimePicker).() -> Unit,
): TimePicker {
    return ankoView(`$$Anko$Factories$Sdk28View`.TIME_PICKER, theme) { init() }
}

fun Context.timePicker(): TimePicker = timePicker {}
inline fun Context.timePicker(init: (@AnkoViewDslMarker TimePicker).() -> Unit): TimePicker {
    return ankoView(`$$Anko$Factories$Sdk28View`.TIME_PICKER, theme = 0) { init() }
}

fun Context.themedTimePicker(theme: Int = 0): TimePicker =
    themedTimePicker(theme) {}

inline fun Context.themedTimePicker(
    theme: Int = 0,
    init: (@AnkoViewDslMarker TimePicker).() -> Unit,
): TimePicker {
    return ankoView(`$$Anko$Factories$Sdk28View`.TIME_PICKER, theme) { init() }
}

fun Activity.timePicker(): TimePicker = timePicker {}
inline fun Activity.timePicker(init: (@AnkoViewDslMarker TimePicker).() -> Unit): TimePicker {
    return ankoView(`$$Anko$Factories$Sdk28View`.TIME_PICKER, theme = 0) { init() }
}

fun Activity.themedTimePicker(theme: Int = 0): TimePicker =
    themedTimePicker(theme) {}

inline fun Activity.themedTimePicker(
    theme: Int = 0,
    init: (@AnkoViewDslMarker TimePicker).() -> Unit,
): TimePicker {
    return ankoView(`$$Anko$Factories$Sdk28View`.TIME_PICKER, theme) { init() }
}

fun ViewManager.toggleButton(): AppCompatToggleButton = toggleButton {}
inline fun ViewManager.toggleButton(init: (@AnkoViewDslMarker AppCompatToggleButton).() -> Unit): AppCompatToggleButton {
    return ankoView(`$$Anko$Factories$Sdk28View`.TOGGLE_BUTTON, theme = 0) { init() }
}

fun ViewManager.themedToggleButton(theme: Int = 0): AppCompatToggleButton =
    themedToggleButton(theme) {}

inline fun ViewManager.themedToggleButton(
    theme: Int = 0,
    init: (@AnkoViewDslMarker AppCompatToggleButton).() -> Unit,
): AppCompatToggleButton {
    return ankoView(`$$Anko$Factories$Sdk28View`.TOGGLE_BUTTON, theme) { init() }
}

//inline fun ViewManager.twoLineListItem(): android.widget.TwoLineListItem = twoLineListItem() {}
//inline fun ViewManager.twoLineListItem(init: (@AnkoViewDslMarker android.widget.TwoLineListItem).() -> Unit): android.widget.TwoLineListItem {
//    return ankoView(`$$Anko$Factories$Sdk28View`.TWO_LINE_LIST_ITEM, theme = 0) { init() }
//}
//
//inline fun ViewManager.themedTwoLineListItem(theme: Int = 0): android.widget.TwoLineListItem =
//    themedTwoLineListItem(theme) {}
//
//inline fun ViewManager.themedTwoLineListItem(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker android.widget.TwoLineListItem).() -> Unit,
//): android.widget.TwoLineListItem {
//    return ankoView(`$$Anko$Factories$Sdk28View`.TWO_LINE_LIST_ITEM, theme) { init() }
//}
//
//inline fun Context.twoLineListItem(): android.widget.TwoLineListItem = twoLineListItem() {}
//inline fun Context.twoLineListItem(init: (@AnkoViewDslMarker android.widget.TwoLineListItem).() -> Unit): android.widget.TwoLineListItem {
//    return ankoView(`$$Anko$Factories$Sdk28View`.TWO_LINE_LIST_ITEM, theme = 0) { init() }
//}
//
//inline fun Context.themedTwoLineListItem(theme: Int = 0): android.widget.TwoLineListItem =
//    themedTwoLineListItem(theme) {}
//
//inline fun Context.themedTwoLineListItem(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker android.widget.TwoLineListItem).() -> Unit,
//): android.widget.TwoLineListItem {
//    return ankoView(`$$Anko$Factories$Sdk28View`.TWO_LINE_LIST_ITEM, theme) { init() }
//}
//
//inline fun Activity.twoLineListItem(): android.widget.TwoLineListItem = twoLineListItem() {}
//inline fun Activity.twoLineListItem(init: (@AnkoViewDslMarker android.widget.TwoLineListItem).() -> Unit): android.widget.TwoLineListItem {
//    return ankoView(`$$Anko$Factories$Sdk28View`.TWO_LINE_LIST_ITEM, theme = 0) { init() }
//}
//
//inline fun Activity.themedTwoLineListItem(theme: Int = 0): android.widget.TwoLineListItem =
//    themedTwoLineListItem(theme) {}
//
//inline fun Activity.themedTwoLineListItem(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker android.widget.TwoLineListItem).() -> Unit,
//): android.widget.TwoLineListItem {
//    return ankoView(`$$Anko$Factories$Sdk28View`.TWO_LINE_LIST_ITEM, theme) { init() }
//}

fun ViewManager.videoView(): VideoView = videoView {}
inline fun ViewManager.videoView(init: (@AnkoViewDslMarker VideoView).() -> Unit): VideoView {
    return ankoView(`$$Anko$Factories$Sdk28View`.VIDEO_VIEW, theme = 0) { init() }
}

fun ViewManager.themedVideoView(theme: Int = 0): VideoView =
    themedVideoView(theme) {}

inline fun ViewManager.themedVideoView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker VideoView).() -> Unit,
): VideoView {
    return ankoView(`$$Anko$Factories$Sdk28View`.VIDEO_VIEW, theme) { init() }
}

fun ViewManager.viewFlipper(): ViewFlipper = viewFlipper {}
inline fun ViewManager.viewFlipper(init: (@AnkoViewDslMarker ViewFlipper).() -> Unit): ViewFlipper {
    return ankoView(`$$Anko$Factories$Sdk28View`.VIEW_FLIPPER, theme = 0) { init() }
}

fun ViewManager.themedViewFlipper(theme: Int = 0): ViewFlipper =
    themedViewFlipper(theme) {}

inline fun ViewManager.themedViewFlipper(
    theme: Int = 0,
    init: (@AnkoViewDslMarker ViewFlipper).() -> Unit,
): ViewFlipper {
    return ankoView(`$$Anko$Factories$Sdk28View`.VIEW_FLIPPER, theme) { init() }
}

fun Context.viewFlipper(): ViewFlipper = viewFlipper {}
inline fun Context.viewFlipper(init: (@AnkoViewDslMarker ViewFlipper).() -> Unit): ViewFlipper {
    return ankoView(`$$Anko$Factories$Sdk28View`.VIEW_FLIPPER, theme = 0) { init() }
}

fun Context.themedViewFlipper(theme: Int = 0): ViewFlipper =
    themedViewFlipper(theme) {}

inline fun Context.themedViewFlipper(
    theme: Int = 0,
    init: (@AnkoViewDslMarker ViewFlipper).() -> Unit,
): ViewFlipper {
    return ankoView(`$$Anko$Factories$Sdk28View`.VIEW_FLIPPER, theme) { init() }
}

fun Activity.viewFlipper(): ViewFlipper = viewFlipper {}
inline fun Activity.viewFlipper(init: (@AnkoViewDslMarker ViewFlipper).() -> Unit): ViewFlipper {
    return ankoView(`$$Anko$Factories$Sdk28View`.VIEW_FLIPPER, theme = 0) { init() }
}

fun Activity.themedViewFlipper(theme: Int = 0): ViewFlipper =
    themedViewFlipper(theme) {}

inline fun Activity.themedViewFlipper(
    theme: Int = 0,
    init: (@AnkoViewDslMarker ViewFlipper).() -> Unit,
): ViewFlipper {
    return ankoView(`$$Anko$Factories$Sdk28View`.VIEW_FLIPPER, theme) { init() }
}

//inline fun ViewManager.zoomButton(): android.widget.ZoomButton = zoomButton() {}
//inline fun ViewManager.zoomButton(init: (@AnkoViewDslMarker android.widget.ZoomButton).() -> Unit): android.widget.ZoomButton {
//    return ankoView(`$$Anko$Factories$Sdk28View`.ZOOM_BUTTON, theme = 0) { init() }
//}
//
//inline fun ViewManager.themedZoomButton(theme: Int = 0): android.widget.ZoomButton =
//    themedZoomButton(theme) {}
//
//inline fun ViewManager.themedZoomButton(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker android.widget.ZoomButton).() -> Unit,
//): android.widget.ZoomButton {
//    return ankoView(`$$Anko$Factories$Sdk28View`.ZOOM_BUTTON, theme) { init() }
//}
//
//inline fun ViewManager.zoomControls(): android.widget.ZoomControls = zoomControls() {}
//inline fun ViewManager.zoomControls(init: (@AnkoViewDslMarker android.widget.ZoomControls).() -> Unit): android.widget.ZoomControls {
//    return ankoView(`$$Anko$Factories$Sdk28View`.ZOOM_CONTROLS, theme = 0) { init() }
//}
//
//inline fun ViewManager.themedZoomControls(theme: Int = 0): android.widget.ZoomControls =
//    themedZoomControls(theme) {}
//
//inline fun ViewManager.themedZoomControls(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker android.widget.ZoomControls).() -> Unit,
//): android.widget.ZoomControls {
//    return ankoView(`$$Anko$Factories$Sdk28View`.ZOOM_CONTROLS, theme) { init() }
//}
//
//inline fun Context.zoomControls(): android.widget.ZoomControls = zoomControls() {}
//inline fun Context.zoomControls(init: (@AnkoViewDslMarker android.widget.ZoomControls).() -> Unit): android.widget.ZoomControls {
//    return ankoView(`$$Anko$Factories$Sdk28View`.ZOOM_CONTROLS, theme = 0) { init() }
//}
//
//inline fun Context.themedZoomControls(theme: Int = 0): android.widget.ZoomControls =
//    themedZoomControls(theme) {}
//
//inline fun Context.themedZoomControls(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker android.widget.ZoomControls).() -> Unit,
//): android.widget.ZoomControls {
//    return ankoView(`$$Anko$Factories$Sdk28View`.ZOOM_CONTROLS, theme) { init() }
//}
//
//inline fun Activity.zoomControls(): android.widget.ZoomControls = zoomControls() {}
//inline fun Activity.zoomControls(init: (@AnkoViewDslMarker android.widget.ZoomControls).() -> Unit): android.widget.ZoomControls {
//    return ankoView(`$$Anko$Factories$Sdk28View`.ZOOM_CONTROLS, theme = 0) { init() }
//}
//
//inline fun Activity.themedZoomControls(theme: Int = 0): android.widget.ZoomControls =
//    themedZoomControls(theme) {}
//
//inline fun Activity.themedZoomControls(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker android.widget.ZoomControls).() -> Unit,
//): android.widget.ZoomControls {
//    return ankoView(`$$Anko$Factories$Sdk28View`.ZOOM_CONTROLS, theme) { init() }
//}
