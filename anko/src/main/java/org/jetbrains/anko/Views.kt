@file:Suppress("TooManyFunctions", "ClassNaming")
@file:JvmName("Sdk28ViewsKt")

package org.jetbrains.anko

import android.app.*
import android.content.Context
import android.widget.*
import org.jetbrains.anko.custom.*
import androidx.appcompat.widget.*
import android.gesture.GestureOverlayView
import android.inputmethodservice.ExtractEditText
import android.media.tv.TvView
import android.opengl.GLSurfaceView
import android.view.*
import android.webkit.WebView
import androidx.appcompat.widget.SearchView

@PublishedApi
@Suppress("MatchingDeclarationName")
internal object `$$Anko$Factories$Sdk28View` {
    val MEDIA_ROUTE_BUTTON = { ctx: Context -> MediaRouteButton(ctx) }
    val GESTURE_OVERLAY_VIEW = { ctx: Context -> GestureOverlayView(ctx) }
    val EXTRACT_EDIT_TEXT = { ctx: Context -> ExtractEditText(ctx) }
    val TV_VIEW = { ctx: Context -> TvView(ctx) }
    val G_L_SURFACE_VIEW = { ctx: Context -> GLSurfaceView(ctx) }
    val SURFACE_VIEW = { ctx: Context -> SurfaceView(ctx) }
    val TEXTURE_VIEW = { ctx: Context -> TextureView(ctx) }
    val VIEW = { ctx: Context -> View(ctx) }
    val VIEW_STUB = { ctx: Context -> ViewStub(ctx) }
    val WEB_VIEW = { ctx: Context -> WebView(ctx) }
    val ADAPTER_VIEW_FLIPPER = { ctx: Context -> AdapterViewFlipper(ctx) }

    //val ANALOG_CLOCK = { ctx: Context -> android.widget.AnalogClock(ctx) }
    val AUTO_COMPLETE_TEXT_VIEW = { ctx: Context -> AppCompatAutoCompleteTextView(ctx) }
    val BUTTON = { ctx: Context -> AppCompatButton(ctx) }
    val CALENDAR_VIEW = { ctx: Context -> CalendarView(ctx) }
    val CHECK_BOX = { ctx: Context -> AppCompatCheckBox(ctx) }
    val CHECKED_TEXT_VIEW = { ctx: Context -> AppCompatCheckedTextView(ctx) }
    val CHRONOMETER = { ctx: Context -> Chronometer(ctx) }
    val DATE_PICKER = { ctx: Context -> DatePicker(ctx) }

    //val DIALER_FILTER = { ctx: Context -> android.widget.DialerFilter(ctx) }
    //val DIGITAL_CLOCK = { ctx: Context -> android.widget.DigitalClock(ctx) }
    val EDIT_TEXT = { ctx: Context -> AppCompatEditText(ctx) }
    val EXPANDABLE_LIST_VIEW = { ctx: Context -> ExpandableListView(ctx) }
    val IMAGE_BUTTON = { ctx: Context -> AppCompatImageButton(ctx) }
    val IMAGE_VIEW = { ctx: Context -> AppCompatImageView(ctx) }
    val LIST_VIEW = { ctx: Context -> ListView(ctx) }
    val MULTI_AUTO_COMPLETE_TEXT_VIEW =
        { ctx: Context -> AppCompatMultiAutoCompleteTextView(ctx) }
    val NUMBER_PICKER = { ctx: Context -> NumberPicker(ctx) }
    val PROGRESS_BAR = { ctx: Context -> ProgressBar(ctx) }
    val QUICK_CONTACT_BADGE = { ctx: Context -> QuickContactBadge(ctx) }
    val RADIO_BUTTON = { ctx: Context -> AppCompatRadioButton(ctx) }
    val RATING_BAR = { ctx: Context -> AppCompatRatingBar(ctx) }
    val SEARCH_VIEW = { ctx: Context -> SearchView(ctx) }
    val SEEK_BAR = { ctx: Context -> AppCompatSeekBar(ctx) }

    // val SLIDING_DRAWER = { ctx: Context -> android.widget.SlidingDrawer(ctx, null) }
    val SPACE = { ctx: Context -> Space(ctx) }
    val SPINNER = { ctx: Context -> AppCompatSpinner(ctx) }
    val STACK_VIEW = { ctx: Context -> StackView(ctx) }
    val SWITCH = { ctx: Context -> SwitchCompat(ctx) }

    // val TAB_HOST = { ctx: Context -> android.widget.TabHost(ctx) }
    // val TAB_WIDGET = { ctx: Context -> android.widget.TabWidget(ctx) }
    val TEXT_CLOCK = { ctx: Context -> TextClock(ctx) }
    val TEXT_VIEW = { ctx: Context -> AppCompatTextView(ctx) }
    val TIME_PICKER = { ctx: Context -> TimePicker(ctx) }
    val TOGGLE_BUTTON = { ctx: Context -> AppCompatToggleButton(ctx) }

    //  val TWO_LINE_LIST_ITEM = { ctx: Context -> android.widget.TwoLineListItem(ctx) }
    val VIDEO_VIEW = { ctx: Context -> VideoView(ctx) }
    val VIEW_FLIPPER = { ctx: Context -> ViewFlipper(ctx) }
    // val ZOOM_BUTTON = { ctx: Context -> android.widget.ZoomButton(ctx) }
    // val ZOOM_CONTROLS = { ctx: Context -> android.widget.ZoomControls(ctx) }
}

fun ViewManager.mediaRouteButton(): MediaRouteButton = mediaRouteButton {}
inline fun ViewManager.mediaRouteButton(init: (@AnkoViewDslMarker MediaRouteButton).() -> Unit): MediaRouteButton {
    return ankoView(`$$Anko$Factories$Sdk28View`.MEDIA_ROUTE_BUTTON, theme = 0) { init() }
}

fun ViewManager.themedMediaRouteButton(theme: Int = 0): MediaRouteButton =
    themedMediaRouteButton(theme) {}

inline fun ViewManager.themedMediaRouteButton(
    theme: Int = 0,
    init: (@AnkoViewDslMarker MediaRouteButton).() -> Unit,
): MediaRouteButton {
    return ankoView(`$$Anko$Factories$Sdk28View`.MEDIA_ROUTE_BUTTON, theme) { init() }
}

fun ViewManager.gestureOverlayView(): GestureOverlayView =
    gestureOverlayView {}

inline fun ViewManager.gestureOverlayView(init: (@AnkoViewDslMarker GestureOverlayView).() -> Unit): GestureOverlayView {
    return ankoView(`$$Anko$Factories$Sdk28View`.GESTURE_OVERLAY_VIEW, theme = 0) { init() }
}

fun ViewManager.themedGestureOverlayView(theme: Int = 0): GestureOverlayView =
    themedGestureOverlayView(theme) {}

inline fun ViewManager.themedGestureOverlayView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker GestureOverlayView).() -> Unit,
): GestureOverlayView {
    return ankoView(`$$Anko$Factories$Sdk28View`.GESTURE_OVERLAY_VIEW, theme) { init() }
}

fun Context.gestureOverlayView(): GestureOverlayView =
    gestureOverlayView {}

inline fun Context.gestureOverlayView(init: (@AnkoViewDslMarker GestureOverlayView).() -> Unit): GestureOverlayView {
    return ankoView(`$$Anko$Factories$Sdk28View`.GESTURE_OVERLAY_VIEW, theme = 0) { init() }
}

fun Context.themedGestureOverlayView(theme: Int = 0): GestureOverlayView =
    themedGestureOverlayView(theme) {}

inline fun Context.themedGestureOverlayView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker GestureOverlayView).() -> Unit,
): GestureOverlayView {
    return ankoView(`$$Anko$Factories$Sdk28View`.GESTURE_OVERLAY_VIEW, theme) { init() }
}

fun Activity.gestureOverlayView(): GestureOverlayView =
    gestureOverlayView {}

inline fun Activity.gestureOverlayView(init: (@AnkoViewDslMarker GestureOverlayView).() -> Unit): GestureOverlayView {
    return ankoView(`$$Anko$Factories$Sdk28View`.GESTURE_OVERLAY_VIEW, theme = 0) { init() }
}

fun Activity.themedGestureOverlayView(theme: Int = 0): GestureOverlayView =
    themedGestureOverlayView(theme) {}

inline fun Activity.themedGestureOverlayView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker GestureOverlayView).() -> Unit,
): GestureOverlayView {
    return ankoView(`$$Anko$Factories$Sdk28View`.GESTURE_OVERLAY_VIEW, theme) { init() }
}

fun ViewManager.extractEditText(): ExtractEditText =
    extractEditText {}

inline fun ViewManager.extractEditText(init: (@AnkoViewDslMarker ExtractEditText).() -> Unit): ExtractEditText {
    return ankoView(`$$Anko$Factories$Sdk28View`.EXTRACT_EDIT_TEXT, theme = 0) { init() }
}

fun ViewManager.themedExtractEditText(theme: Int = 0): ExtractEditText =
    themedExtractEditText(theme) {}

inline fun ViewManager.themedExtractEditText(
    theme: Int = 0,
    init: (@AnkoViewDslMarker ExtractEditText).() -> Unit,
): ExtractEditText {
    return ankoView(`$$Anko$Factories$Sdk28View`.EXTRACT_EDIT_TEXT, theme) { init() }
}

fun ViewManager.tvView(): TvView = tvView {}
inline fun ViewManager.tvView(init: (@AnkoViewDslMarker TvView).() -> Unit): TvView {
    return ankoView(`$$Anko$Factories$Sdk28View`.TV_VIEW, theme = 0) { init() }
}

fun ViewManager.themedTvView(theme: Int = 0): TvView =
    themedTvView(theme) {}

inline fun ViewManager.themedTvView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker TvView).() -> Unit,
): TvView {
    return ankoView(`$$Anko$Factories$Sdk28View`.TV_VIEW, theme) { init() }
}

fun Context.tvView(): TvView = tvView {}
inline fun Context.tvView(init: (@AnkoViewDslMarker TvView).() -> Unit): TvView {
    return ankoView(`$$Anko$Factories$Sdk28View`.TV_VIEW, theme = 0) { init() }
}

fun Context.themedTvView(theme: Int = 0): TvView = themedTvView(theme) {}
inline fun Context.themedTvView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker TvView).() -> Unit,
): TvView {
    return ankoView(`$$Anko$Factories$Sdk28View`.TV_VIEW, theme) { init() }
}

fun Activity.tvView(): TvView = tvView {}
inline fun Activity.tvView(init: (@AnkoViewDslMarker TvView).() -> Unit): TvView {
    return ankoView(`$$Anko$Factories$Sdk28View`.TV_VIEW, theme = 0) { init() }
}

fun Activity.themedTvView(theme: Int = 0): TvView = themedTvView(theme) {}
inline fun Activity.themedTvView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker TvView).() -> Unit,
): TvView {
    return ankoView(`$$Anko$Factories$Sdk28View`.TV_VIEW, theme) { init() }
}

fun ViewManager.gLSurfaceView(): GLSurfaceView = gLSurfaceView {}
inline fun ViewManager.gLSurfaceView(init: (@AnkoViewDslMarker GLSurfaceView).() -> Unit): GLSurfaceView {
    return ankoView(`$$Anko$Factories$Sdk28View`.G_L_SURFACE_VIEW, theme = 0) { init() }
}

fun ViewManager.themedGLSurfaceView(theme: Int = 0): GLSurfaceView =
    themedGLSurfaceView(theme) {}

inline fun ViewManager.themedGLSurfaceView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker GLSurfaceView).() -> Unit,
): GLSurfaceView {
    return ankoView(`$$Anko$Factories$Sdk28View`.G_L_SURFACE_VIEW, theme) { init() }
}

fun ViewManager.surfaceView(): SurfaceView = surfaceView {}
inline fun ViewManager.surfaceView(init: (@AnkoViewDslMarker SurfaceView).() -> Unit): SurfaceView {
    return ankoView(`$$Anko$Factories$Sdk28View`.SURFACE_VIEW, theme = 0) { init() }
}

fun ViewManager.themedSurfaceView(theme: Int = 0): SurfaceView =
    themedSurfaceView(theme) {}

inline fun ViewManager.themedSurfaceView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker SurfaceView).() -> Unit,
): SurfaceView {
    return ankoView(`$$Anko$Factories$Sdk28View`.SURFACE_VIEW, theme) { init() }
}

fun ViewManager.textureView(): TextureView = textureView {}
inline fun ViewManager.textureView(init: (@AnkoViewDslMarker TextureView).() -> Unit): TextureView {
    return ankoView(`$$Anko$Factories$Sdk28View`.TEXTURE_VIEW, theme = 0) { init() }
}

fun ViewManager.themedTextureView(theme: Int = 0): TextureView =
    themedTextureView(theme) {}

inline fun ViewManager.themedTextureView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker TextureView).() -> Unit,
): TextureView {
    return ankoView(`$$Anko$Factories$Sdk28View`.TEXTURE_VIEW, theme) { init() }
}

fun ViewManager.view(): View = view {}
inline fun ViewManager.view(init: (@AnkoViewDslMarker View).() -> Unit): View {
    return ankoView(`$$Anko$Factories$Sdk28View`.VIEW, theme = 0) { init() }
}

fun ViewManager.themedView(theme: Int = 0): View = themedView(theme) {}
inline fun ViewManager.themedView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker View).() -> Unit,
): View {
    return ankoView(`$$Anko$Factories$Sdk28View`.VIEW, theme) { init() }
}

fun ViewManager.viewStub(): ViewStub = viewStub {}
inline fun ViewManager.viewStub(init: (@AnkoViewDslMarker ViewStub).() -> Unit): ViewStub {
    return ankoView(`$$Anko$Factories$Sdk28View`.VIEW_STUB, theme = 0) { init() }
}

fun ViewManager.themedViewStub(theme: Int = 0): ViewStub =
    themedViewStub(theme) {}

inline fun ViewManager.themedViewStub(
    theme: Int = 0,
    init: (@AnkoViewDslMarker ViewStub).() -> Unit,
): ViewStub {
    return ankoView(`$$Anko$Factories$Sdk28View`.VIEW_STUB, theme) { init() }
}

fun ViewManager.webView(): WebView = webView {}
inline fun ViewManager.webView(init: (@AnkoViewDslMarker WebView).() -> Unit): WebView {
    return ankoView(`$$Anko$Factories$Sdk28View`.WEB_VIEW, theme = 0) { init() }
}

fun ViewManager.themedWebView(theme: Int = 0): WebView =
    themedWebView(theme) {}

inline fun ViewManager.themedWebView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker WebView).() -> Unit,
): WebView {
    return ankoView(`$$Anko$Factories$Sdk28View`.WEB_VIEW, theme) { init() }
}

fun Context.webView(): WebView = webView {}
inline fun Context.webView(init: (@AnkoViewDslMarker WebView).() -> Unit): WebView {
    return ankoView(`$$Anko$Factories$Sdk28View`.WEB_VIEW, theme = 0) { init() }
}

fun Context.themedWebView(theme: Int = 0): WebView = themedWebView(theme) {}
inline fun Context.themedWebView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker WebView).() -> Unit,
): WebView {
    return ankoView(`$$Anko$Factories$Sdk28View`.WEB_VIEW, theme) { init() }
}

fun Activity.webView(): WebView = webView {}
inline fun Activity.webView(init: (@AnkoViewDslMarker WebView).() -> Unit): WebView {
    return ankoView(`$$Anko$Factories$Sdk28View`.WEB_VIEW, theme = 0) { init() }
}

fun Activity.themedWebView(theme: Int = 0): WebView = themedWebView(theme) {}
inline fun Activity.themedWebView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker WebView).() -> Unit,
): WebView {
    return ankoView(`$$Anko$Factories$Sdk28View`.WEB_VIEW, theme) { init() }
}

fun ViewManager.adapterViewFlipper(): AdapterViewFlipper =
    adapterViewFlipper {}

inline fun ViewManager.adapterViewFlipper(init: (@AnkoViewDslMarker AdapterViewFlipper).() -> Unit): AdapterViewFlipper {
    return ankoView(`$$Anko$Factories$Sdk28View`.ADAPTER_VIEW_FLIPPER, theme = 0) { init() }
}

fun ViewManager.themedAdapterViewFlipper(theme: Int = 0): AdapterViewFlipper =
    themedAdapterViewFlipper(theme) {}

inline fun ViewManager.themedAdapterViewFlipper(
    theme: Int = 0,
    init: (@AnkoViewDslMarker AdapterViewFlipper).() -> Unit,
): AdapterViewFlipper {
    return ankoView(`$$Anko$Factories$Sdk28View`.ADAPTER_VIEW_FLIPPER, theme) { init() }
}

fun Context.adapterViewFlipper(): AdapterViewFlipper = adapterViewFlipper {}
inline fun Context.adapterViewFlipper(init: (@AnkoViewDslMarker AdapterViewFlipper).() -> Unit): AdapterViewFlipper {
    return ankoView(`$$Anko$Factories$Sdk28View`.ADAPTER_VIEW_FLIPPER, theme = 0) { init() }
}

fun Context.themedAdapterViewFlipper(theme: Int = 0): AdapterViewFlipper =
    themedAdapterViewFlipper(theme) {}

inline fun Context.themedAdapterViewFlipper(
    theme: Int = 0,
    init: (@AnkoViewDslMarker AdapterViewFlipper).() -> Unit,
): AdapterViewFlipper {
    return ankoView(`$$Anko$Factories$Sdk28View`.ADAPTER_VIEW_FLIPPER, theme) { init() }
}

fun Activity.adapterViewFlipper(): AdapterViewFlipper =
    adapterViewFlipper {}

inline fun Activity.adapterViewFlipper(init: (@AnkoViewDslMarker AdapterViewFlipper).() -> Unit): AdapterViewFlipper {
    return ankoView(`$$Anko$Factories$Sdk28View`.ADAPTER_VIEW_FLIPPER, theme = 0) { init() }
}

fun Activity.themedAdapterViewFlipper(theme: Int = 0): AdapterViewFlipper =
    themedAdapterViewFlipper(theme) {}

inline fun Activity.themedAdapterViewFlipper(
    theme: Int = 0,
    init: (@AnkoViewDslMarker AdapterViewFlipper).() -> Unit,
): AdapterViewFlipper {
    return ankoView(`$$Anko$Factories$Sdk28View`.ADAPTER_VIEW_FLIPPER, theme) { init() }
}

//inline fun ViewManager.analogClock(): android.widget.AnalogClock = analogClock() {}
//inline fun ViewManager.analogClock(init: (@AnkoViewDslMarker android.widget.AnalogClock).() -> Unit): android.widget.AnalogClock {
//    return ankoView(`$$Anko$Factories$Sdk28View`.ANALOG_CLOCK, theme = 0) { init() }
//}
//
//inline fun ViewManager.themedAnalogClock(theme: Int = 0): android.widget.AnalogClock =
//    themedAnalogClock(theme) {}
//
//inline fun ViewManager.themedAnalogClock(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker android.widget.AnalogClock).() -> Unit,
//): android.widget.AnalogClock {
//    return ankoView(`$$Anko$Factories$Sdk28View`.ANALOG_CLOCK, theme) { init() }
//}

fun ViewManager.autoCompleteTextView(): AppCompatAutoCompleteTextView =
    autoCompleteTextView {}

inline fun ViewManager.autoCompleteTextView(init: (@AnkoViewDslMarker AppCompatAutoCompleteTextView).() -> Unit): AppCompatAutoCompleteTextView {
    return ankoView(`$$Anko$Factories$Sdk28View`.AUTO_COMPLETE_TEXT_VIEW, theme = 0) { init() }
}

fun ViewManager.themedAutoCompleteTextView(theme: Int = 0): AppCompatAutoCompleteTextView =
    themedAutoCompleteTextView(theme) {}

inline fun ViewManager.themedAutoCompleteTextView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker AppCompatAutoCompleteTextView).() -> Unit,
): AppCompatAutoCompleteTextView {
    return ankoView(`$$Anko$Factories$Sdk28View`.AUTO_COMPLETE_TEXT_VIEW, theme) { init() }
}

fun ViewManager.button(): AppCompatButton = button {}
inline fun ViewManager.button(init: (@AnkoViewDslMarker AppCompatButton).() -> Unit): AppCompatButton {
    return ankoView(`$$Anko$Factories$Sdk28View`.BUTTON, theme = 0) { init() }
}

fun ViewManager.themedButton(theme: Int = 0): AppCompatButton = themedButton(theme) {}
inline fun ViewManager.themedButton(
    theme: Int = 0,
    init: (@AnkoViewDslMarker AppCompatButton).() -> Unit,
): AppCompatButton {
    return ankoView(`$$Anko$Factories$Sdk28View`.BUTTON, theme) { init() }
}

fun ViewManager.button(text: CharSequence?): AppCompatButton {
    return ankoView(`$$Anko$Factories$Sdk28View`.BUTTON, theme = 0) {
        setText(text)
    }
}

inline fun ViewManager.button(
    text: CharSequence?,
    init: (@AnkoViewDslMarker AppCompatButton).() -> Unit,
): AppCompatButton {
    return ankoView(`$$Anko$Factories$Sdk28View`.BUTTON, theme = 0) {
        init()
        setText(text)
    }
}

fun ViewManager.themedButton(text: CharSequence?, theme: Int): AppCompatButton {
    return ankoView(`$$Anko$Factories$Sdk28View`.BUTTON, theme) {
        setText(text)
    }
}

inline fun ViewManager.themedButton(
    text: CharSequence?,
    theme: Int,
    init: (@AnkoViewDslMarker AppCompatButton).() -> Unit,
): AppCompatButton {
    return ankoView(`$$Anko$Factories$Sdk28View`.BUTTON, theme) {
        init()
        setText(text)
    }
}

fun ViewManager.button(text: Int): AppCompatButton {
    return ankoView(`$$Anko$Factories$Sdk28View`.BUTTON, theme = 0) {
        setText(text)
    }
}

inline fun ViewManager.button(
    text: Int,
    init: (@AnkoViewDslMarker AppCompatButton).() -> Unit,
): AppCompatButton {
    return ankoView(`$$Anko$Factories$Sdk28View`.BUTTON, theme = 0) {
        init()
        setText(text)
    }
}

fun ViewManager.themedButton(text: Int, theme: Int): AppCompatButton {
    return ankoView(`$$Anko$Factories$Sdk28View`.BUTTON, theme) {
        setText(text)
    }
}

inline fun ViewManager.themedButton(
    text: Int,
    theme: Int,
    init: (@AnkoViewDslMarker AppCompatButton).() -> Unit,
): AppCompatButton {
    return ankoView(`$$Anko$Factories$Sdk28View`.BUTTON, theme) {
        init()
        setText(text)
    }
}

fun ViewManager.calendarView(): CalendarView = calendarView {}
inline fun ViewManager.calendarView(init: (@AnkoViewDslMarker CalendarView).() -> Unit): CalendarView {
    return ankoView(`$$Anko$Factories$Sdk28View`.CALENDAR_VIEW, theme = 0) { init() }
}

fun ViewManager.themedCalendarView(theme: Int = 0): CalendarView =
    themedCalendarView(theme) {}

inline fun ViewManager.themedCalendarView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker CalendarView).() -> Unit,
): CalendarView {
    return ankoView(`$$Anko$Factories$Sdk28View`.CALENDAR_VIEW, theme) { init() }
}

fun Context.calendarView(): CalendarView = calendarView {}
inline fun Context.calendarView(init: (@AnkoViewDslMarker CalendarView).() -> Unit): CalendarView {
    return ankoView(`$$Anko$Factories$Sdk28View`.CALENDAR_VIEW, theme = 0) { init() }
}

fun Context.themedCalendarView(theme: Int = 0): CalendarView =
    themedCalendarView(theme) {}

inline fun Context.themedCalendarView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker CalendarView).() -> Unit,
): CalendarView {
    return ankoView(`$$Anko$Factories$Sdk28View`.CALENDAR_VIEW, theme) { init() }
}

fun Activity.calendarView(): CalendarView = calendarView {}
inline fun Activity.calendarView(init: (@AnkoViewDslMarker CalendarView).() -> Unit): CalendarView {
    return ankoView(`$$Anko$Factories$Sdk28View`.CALENDAR_VIEW, theme = 0) { init() }
}

fun Activity.themedCalendarView(theme: Int = 0): CalendarView =
    themedCalendarView(theme) {}

inline fun Activity.themedCalendarView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker CalendarView).() -> Unit,
): CalendarView {
    return ankoView(`$$Anko$Factories$Sdk28View`.CALENDAR_VIEW, theme) { init() }
}

fun ViewManager.checkBox(): AppCompatCheckBox = checkBox {}
inline fun ViewManager.checkBox(init: (@AnkoViewDslMarker AppCompatCheckBox).() -> Unit): AppCompatCheckBox {
    return ankoView(`$$Anko$Factories$Sdk28View`.CHECK_BOX, theme = 0) { init() }
}

fun ViewManager.themedCheckBox(theme: Int = 0): AppCompatCheckBox =
    themedCheckBox(theme) {}

inline fun ViewManager.themedCheckBox(
    theme: Int = 0,
    init: (@AnkoViewDslMarker AppCompatCheckBox).() -> Unit,
): AppCompatCheckBox {
    return ankoView(`$$Anko$Factories$Sdk28View`.CHECK_BOX, theme) { init() }
}

fun ViewManager.checkBox(text: CharSequence?): AppCompatCheckBox {
    return ankoView(`$$Anko$Factories$Sdk28View`.CHECK_BOX, theme = 0) {
        setText(text)
    }
}

inline fun ViewManager.checkBox(
    text: CharSequence?,
    init: (@AnkoViewDslMarker AppCompatCheckBox).() -> Unit,
): AppCompatCheckBox {
    return ankoView(`$$Anko$Factories$Sdk28View`.CHECK_BOX, theme = 0) {
        init()
        setText(text)
    }
}

fun ViewManager.themedCheckBox(text: CharSequence?, theme: Int): AppCompatCheckBox {
    return ankoView(`$$Anko$Factories$Sdk28View`.CHECK_BOX, theme) {
        setText(text)
    }
}

inline fun ViewManager.themedCheckBox(
    text: CharSequence?,
    theme: Int,
    init: (@AnkoViewDslMarker AppCompatCheckBox).() -> Unit,
): AppCompatCheckBox {
    return ankoView(`$$Anko$Factories$Sdk28View`.CHECK_BOX, theme) {
        init()
        setText(text)
    }
}

fun ViewManager.checkBox(text: Int): AppCompatCheckBox {
    return ankoView(`$$Anko$Factories$Sdk28View`.CHECK_BOX, theme = 0) {
        setText(text)
    }
}

inline fun ViewManager.checkBox(
    text: Int,
    init: (@AnkoViewDslMarker AppCompatCheckBox).() -> Unit,
): AppCompatCheckBox {
    return ankoView(`$$Anko$Factories$Sdk28View`.CHECK_BOX, theme = 0) {
        init()
        setText(text)
    }
}

fun ViewManager.themedCheckBox(text: Int, theme: Int): AppCompatCheckBox {
    return ankoView(`$$Anko$Factories$Sdk28View`.CHECK_BOX, theme) {
        setText(text)
    }
}

inline fun ViewManager.themedCheckBox(
    text: Int,
    theme: Int,
    init: (@AnkoViewDslMarker AppCompatCheckBox).() -> Unit,
): AppCompatCheckBox {
    return ankoView(`$$Anko$Factories$Sdk28View`.CHECK_BOX, theme) {
        init()
        setText(text)
    }
}

fun ViewManager.checkBox(text: CharSequence?, checked: Boolean): AppCompatCheckBox {
    return ankoView(`$$Anko$Factories$Sdk28View`.CHECK_BOX, theme = 0) {
        setText(text)
        isChecked = checked
    }
}

inline fun ViewManager.checkBox(
    text: CharSequence?,
    checked: Boolean,
    init: (@AnkoViewDslMarker AppCompatCheckBox).() -> Unit,
): AppCompatCheckBox {
    return ankoView(`$$Anko$Factories$Sdk28View`.CHECK_BOX, theme = 0) {
        init()
        setText(text)
        isChecked = checked
    }
}

fun ViewManager.themedCheckBox(
    text: CharSequence?,
    checked: Boolean,
    theme: Int,
): AppCompatCheckBox {
    return ankoView(`$$Anko$Factories$Sdk28View`.CHECK_BOX, theme) {
        setText(text)
        isChecked = checked
    }
}

inline fun ViewManager.themedCheckBox(
    text: CharSequence?,
    checked: Boolean,
    theme: Int,
    init: (@AnkoViewDslMarker AppCompatCheckBox).() -> Unit,
): AppCompatCheckBox {
    return ankoView(`$$Anko$Factories$Sdk28View`.CHECK_BOX, theme) {
        init()
        setText(text)
        isChecked = checked
    }
}

fun ViewManager.checkBox(text: Int, checked: Boolean): AppCompatCheckBox {
    return ankoView(`$$Anko$Factories$Sdk28View`.CHECK_BOX, theme = 0) {
        setText(text)
        isChecked = checked
    }
}

inline fun ViewManager.checkBox(
    text: Int,
    checked: Boolean,
    init: (@AnkoViewDslMarker AppCompatCheckBox).() -> Unit,
): AppCompatCheckBox {
    return ankoView(`$$Anko$Factories$Sdk28View`.CHECK_BOX, theme = 0) {
        init()
        setText(text)
        isChecked = checked
    }
}

fun ViewManager.themedCheckBox(
    text: Int,
    checked: Boolean,
    theme: Int,
): AppCompatCheckBox {
    return ankoView(`$$Anko$Factories$Sdk28View`.CHECK_BOX, theme) {
        setText(text)
        isChecked = checked
    }
}

inline fun ViewManager.themedCheckBox(
    text: Int,
    checked: Boolean,
    theme: Int,
    init: (@AnkoViewDslMarker AppCompatCheckBox).() -> Unit,
): AppCompatCheckBox {
    return ankoView(`$$Anko$Factories$Sdk28View`.CHECK_BOX, theme) {
        init()
        setText(text)
        isChecked = checked
    }
}

fun ViewManager.checkedTextView(): AppCompatCheckedTextView = checkedTextView {}
inline fun ViewManager.checkedTextView(init: (@AnkoViewDslMarker AppCompatCheckedTextView).() -> Unit): AppCompatCheckedTextView {
    return ankoView(`$$Anko$Factories$Sdk28View`.CHECKED_TEXT_VIEW, theme = 0) { init() }
}

fun ViewManager.themedCheckedTextView(theme: Int = 0): AppCompatCheckedTextView =
    themedCheckedTextView(theme) {}

inline fun ViewManager.themedCheckedTextView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker AppCompatCheckedTextView).() -> Unit,
): AppCompatCheckedTextView {
    return ankoView(`$$Anko$Factories$Sdk28View`.CHECKED_TEXT_VIEW, theme) { init() }
}

fun ViewManager.chronometer(): Chronometer = chronometer {}
inline fun ViewManager.chronometer(init: (@AnkoViewDslMarker Chronometer).() -> Unit): Chronometer {
    return ankoView(`$$Anko$Factories$Sdk28View`.CHRONOMETER, theme = 0) { init() }
}

fun ViewManager.themedChronometer(theme: Int = 0): Chronometer =
    themedChronometer(theme) {}

inline fun ViewManager.themedChronometer(
    theme: Int = 0,
    init: (@AnkoViewDslMarker Chronometer).() -> Unit,
): Chronometer {
    return ankoView(`$$Anko$Factories$Sdk28View`.CHRONOMETER, theme) { init() }
}

fun ViewManager.datePicker(): DatePicker = datePicker {}
inline fun ViewManager.datePicker(init: (@AnkoViewDslMarker DatePicker).() -> Unit): DatePicker {
    return ankoView(`$$Anko$Factories$Sdk28View`.DATE_PICKER, theme = 0) { init() }
}

fun ViewManager.themedDatePicker(theme: Int = 0): DatePicker =
    themedDatePicker(theme) {}

inline fun ViewManager.themedDatePicker(
    theme: Int = 0,
    init: (@AnkoViewDslMarker DatePicker).() -> Unit,
): DatePicker {
    return ankoView(`$$Anko$Factories$Sdk28View`.DATE_PICKER, theme) { init() }
}

fun Context.datePicker(): DatePicker = datePicker {}
inline fun Context.datePicker(init: (@AnkoViewDslMarker DatePicker).() -> Unit): DatePicker {
    return ankoView(`$$Anko$Factories$Sdk28View`.DATE_PICKER, theme = 0) { init() }
}

fun Context.themedDatePicker(theme: Int = 0): DatePicker =
    themedDatePicker(theme) {}

inline fun Context.themedDatePicker(
    theme: Int = 0,
    init: (@AnkoViewDslMarker DatePicker).() -> Unit,
): DatePicker {
    return ankoView(`$$Anko$Factories$Sdk28View`.DATE_PICKER, theme) { init() }
}

fun Activity.datePicker(): DatePicker = datePicker {}
inline fun Activity.datePicker(init: (@AnkoViewDslMarker DatePicker).() -> Unit): DatePicker {
    return ankoView(`$$Anko$Factories$Sdk28View`.DATE_PICKER, theme = 0) { init() }
}

fun Activity.themedDatePicker(theme: Int = 0): DatePicker =
    themedDatePicker(theme) {}

inline fun Activity.themedDatePicker(
    theme: Int = 0,
    init: (@AnkoViewDslMarker DatePicker).() -> Unit,
): DatePicker {
    return ankoView(`$$Anko$Factories$Sdk28View`.DATE_PICKER, theme) { init() }
}

//inline fun ViewManager.dialerFilter(): android.widget.DialerFilter = dialerFilter() {}
//inline fun ViewManager.dialerFilter(init: (@AnkoViewDslMarker android.widget.DialerFilter).() -> Unit): android.widget.DialerFilter {
//    return ankoView(`$$Anko$Factories$Sdk28View`.DIALER_FILTER, theme = 0) { init() }
//}
//
//inline fun ViewManager.themedDialerFilter(theme: Int = 0): android.widget.DialerFilter =
//    themedDialerFilter(theme) {}
//
//inline fun ViewManager.themedDialerFilter(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker android.widget.DialerFilter).() -> Unit,
//): android.widget.DialerFilter {
//    return ankoView(`$$Anko$Factories$Sdk28View`.DIALER_FILTER, theme) { init() }
//}
//
//inline fun Context.dialerFilter(): android.widget.DialerFilter = dialerFilter() {}
//inline fun Context.dialerFilter(init: (@AnkoViewDslMarker android.widget.DialerFilter).() -> Unit): android.widget.DialerFilter {
//    return ankoView(`$$Anko$Factories$Sdk28View`.DIALER_FILTER, theme = 0) { init() }
//}
//
//inline fun Context.themedDialerFilter(theme: Int = 0): android.widget.DialerFilter =
//    themedDialerFilter(theme) {}
//
//inline fun Context.themedDialerFilter(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker android.widget.DialerFilter).() -> Unit,
//): android.widget.DialerFilter {
//    return ankoView(`$$Anko$Factories$Sdk28View`.DIALER_FILTER, theme) { init() }
//}
//
//inline fun Activity.dialerFilter(): android.widget.DialerFilter = dialerFilter() {}
//inline fun Activity.dialerFilter(init: (@AnkoViewDslMarker android.widget.DialerFilter).() -> Unit): android.widget.DialerFilter {
//    return ankoView(`$$Anko$Factories$Sdk28View`.DIALER_FILTER, theme = 0) { init() }
//}
//
//inline fun Activity.themedDialerFilter(theme: Int = 0): android.widget.DialerFilter =
//    themedDialerFilter(theme) {}
//
//inline fun Activity.themedDialerFilter(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker android.widget.DialerFilter).() -> Unit,
//): android.widget.DialerFilter {
//    return ankoView(`$$Anko$Factories$Sdk28View`.DIALER_FILTER, theme) { init() }
//}
//
//inline fun ViewManager.digitalClock(): android.widget.DigitalClock = digitalClock() {}
//inline fun ViewManager.digitalClock(init: (@AnkoViewDslMarker android.widget.DigitalClock).() -> Unit): android.widget.DigitalClock {
//    return ankoView(`$$Anko$Factories$Sdk28View`.DIGITAL_CLOCK, theme = 0) { init() }
//}
//
//inline fun ViewManager.themedDigitalClock(theme: Int = 0): android.widget.DigitalClock =
//    themedDigitalClock(theme) {}
//
//inline fun ViewManager.themedDigitalClock(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker android.widget.DigitalClock).() -> Unit,
//): android.widget.DigitalClock {
//    return ankoView(`$$Anko$Factories$Sdk28View`.DIGITAL_CLOCK, theme) { init() }
//}

fun ViewManager.editText(): AppCompatEditText = editText {}
inline fun ViewManager.editText(init: (@AnkoViewDslMarker AppCompatEditText).() -> Unit): AppCompatEditText {
    return ankoView(`$$Anko$Factories$Sdk28View`.EDIT_TEXT, theme = 0) { init() }
}

fun ViewManager.themedEditText(theme: Int = 0): AppCompatEditText =
    themedEditText(theme) {}

inline fun ViewManager.themedEditText(
    theme: Int = 0,
    init: (@AnkoViewDslMarker AppCompatEditText).() -> Unit,
): AppCompatEditText {
    return ankoView(`$$Anko$Factories$Sdk28View`.EDIT_TEXT, theme) { init() }
}

fun ViewManager.editText(text: CharSequence?): AppCompatEditText {
    return ankoView(`$$Anko$Factories$Sdk28View`.EDIT_TEXT, theme = 0) {
        setText(text)
    }
}

inline fun ViewManager.editText(
    text: CharSequence?,
    init: (@AnkoViewDslMarker AppCompatEditText).() -> Unit,
): AppCompatEditText {
    return ankoView(`$$Anko$Factories$Sdk28View`.EDIT_TEXT, theme = 0) {
        init()
        setText(text)
    }
}

fun ViewManager.themedEditText(text: CharSequence?, theme: Int): AppCompatEditText {
    return ankoView(`$$Anko$Factories$Sdk28View`.EDIT_TEXT, theme) {
        setText(text)
    }
}

inline fun ViewManager.themedEditText(
    text: CharSequence?,
    theme: Int,
    init: (@AnkoViewDslMarker AppCompatEditText).() -> Unit,
): AppCompatEditText {
    return ankoView(`$$Anko$Factories$Sdk28View`.EDIT_TEXT, theme) {
        init()
        setText(text)
    }
}

fun ViewManager.editText(text: Int): AppCompatEditText {
    return ankoView(`$$Anko$Factories$Sdk28View`.EDIT_TEXT, theme = 0) {
        setText(text)
    }
}

inline fun ViewManager.editText(
    text: Int,
    init: (@AnkoViewDslMarker AppCompatEditText).() -> Unit,
): AppCompatEditText {
    return ankoView(`$$Anko$Factories$Sdk28View`.EDIT_TEXT, theme = 0) {
        init()
        setText(text)
    }
}

fun ViewManager.themedEditText(text: Int, theme: Int): AppCompatEditText {
    return ankoView(`$$Anko$Factories$Sdk28View`.EDIT_TEXT, theme) {
        setText(text)
    }
}

inline fun ViewManager.themedEditText(
    text: Int,
    theme: Int,
    init: (@AnkoViewDslMarker AppCompatEditText).() -> Unit,
): AppCompatEditText {
    return ankoView(`$$Anko$Factories$Sdk28View`.EDIT_TEXT, theme) {
        init()
        setText(text)
    }
}
