@file:Suppress("TooManyFunctions", "ClassNaming")
@file:JvmName("Sdk28ViewGroupsKt")

package org.jetbrains.anko

import android.app.*
import android.content.Context
import android.view.ViewManager
import android.widget.*
import androidx.appcompat.widget.*
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.Toolbar
import org.jetbrains.anko.custom.*

@PublishedApi
@Suppress("MatchingDeclarationName")
internal object `$$Anko$Factories$Sdk28ViewGroup` {
    val APP_WIDGET_HOST_VIEW = { ctx: Context -> _AppWidgetHostView(ctx) }

    //val ABSOLUTE_LAYOUT = { ctx: Context -> _AbsoluteLayout(ctx) }
    val ACTION_MENU_VIEW = { ctx: Context -> _ActionMenuView(ctx) }
    val FRAME_LAYOUT = { ctx: Context -> _FrameLayout(ctx) }

    //val GALLERY = { ctx: Context -> _Gallery(ctx) }
    val GRID_LAYOUT = { ctx: Context -> _GridLayout(ctx) }
    val GRID_VIEW = { ctx: Context -> _GridView(ctx) }
    val HORIZONTAL_SCROLL_VIEW = { ctx: Context -> _HorizontalScrollView(ctx) }
    val IMAGE_SWITCHER = { ctx: Context -> _ImageSwitcher(ctx) }
    val LINEAR_LAYOUT = { ctx: Context -> _LinearLayout(ctx) }
    val RADIO_GROUP = { ctx: Context -> _RadioGroup(ctx) }
    val RELATIVE_LAYOUT = { ctx: Context -> _RelativeLayout(ctx) }
    val SCROLL_VIEW = { ctx: Context -> _ScrollView(ctx) }
    val TABLE_LAYOUT = { ctx: Context -> _TableLayout(ctx) }
    val TABLE_ROW = { ctx: Context -> _TableRow(ctx) }
    val TEXT_SWITCHER = { ctx: Context -> _TextSwitcher(ctx) }
    val TOOLBAR = { ctx: Context -> _Toolbar(ctx) }
    val VIEW_ANIMATOR = { ctx: Context -> _ViewAnimator(ctx) }
    val VIEW_SWITCHER = { ctx: Context -> _ViewSwitcher(ctx) }
}

fun ViewManager.appWidgetHostView(): android.appwidget.AppWidgetHostView =
    appWidgetHostView {}

inline fun ViewManager.appWidgetHostView(init: (@AnkoViewDslMarker _AppWidgetHostView).() -> Unit): android.appwidget.AppWidgetHostView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.APP_WIDGET_HOST_VIEW, theme = 0) { init() }
}

fun ViewManager.themedAppWidgetHostView(theme: Int = 0): android.appwidget.AppWidgetHostView =
    themedAppWidgetHostView(theme) {}

inline fun ViewManager.themedAppWidgetHostView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _AppWidgetHostView).() -> Unit,
): android.appwidget.AppWidgetHostView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.APP_WIDGET_HOST_VIEW, theme) { init() }
}

fun Context.appWidgetHostView(): android.appwidget.AppWidgetHostView = appWidgetHostView {}
inline fun Context.appWidgetHostView(init: (@AnkoViewDslMarker _AppWidgetHostView).() -> Unit): android.appwidget.AppWidgetHostView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.APP_WIDGET_HOST_VIEW, theme = 0) { init() }
}

fun Context.themedAppWidgetHostView(theme: Int = 0): android.appwidget.AppWidgetHostView =
    themedAppWidgetHostView(theme) {}

inline fun Context.themedAppWidgetHostView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _AppWidgetHostView).() -> Unit,
): android.appwidget.AppWidgetHostView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.APP_WIDGET_HOST_VIEW, theme) { init() }
}

fun Activity.appWidgetHostView(): android.appwidget.AppWidgetHostView =
    appWidgetHostView {}

inline fun Activity.appWidgetHostView(init: (@AnkoViewDslMarker _AppWidgetHostView).() -> Unit): android.appwidget.AppWidgetHostView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.APP_WIDGET_HOST_VIEW, theme = 0) { init() }
}

fun Activity.themedAppWidgetHostView(theme: Int = 0): android.appwidget.AppWidgetHostView =
    themedAppWidgetHostView(theme) {}

inline fun Activity.themedAppWidgetHostView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _AppWidgetHostView).() -> Unit,
): android.appwidget.AppWidgetHostView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.APP_WIDGET_HOST_VIEW, theme) { init() }
}

//inline fun ViewManager.absoluteLayout(): android.widget.AbsoluteLayout = absoluteLayout() {}
//inline fun ViewManager.absoluteLayout(init: (@AnkoViewDslMarker _AbsoluteLayout).() -> Unit): android.widget.AbsoluteLayout {
//    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.ABSOLUTE_LAYOUT, theme = 0) { init() }
//}
//
//inline fun ViewManager.themedAbsoluteLayout(theme: Int = 0): android.widget.AbsoluteLayout =
//    themedAbsoluteLayout(theme) {}
//
//inline fun ViewManager.themedAbsoluteLayout(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker _AbsoluteLayout).() -> Unit,
//): android.widget.AbsoluteLayout {
//    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.ABSOLUTE_LAYOUT, theme) { init() }
//}
//
//inline fun Context.absoluteLayout(): android.widget.AbsoluteLayout = absoluteLayout() {}
//inline fun Context.absoluteLayout(init: (@AnkoViewDslMarker _AbsoluteLayout).() -> Unit): android.widget.AbsoluteLayout {
//    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.ABSOLUTE_LAYOUT, theme = 0) { init() }
//}
//
//inline fun Context.themedAbsoluteLayout(theme: Int = 0): android.widget.AbsoluteLayout =
//    themedAbsoluteLayout(theme) {}
//
//inline fun Context.themedAbsoluteLayout(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker _AbsoluteLayout).() -> Unit,
//): android.widget.AbsoluteLayout {
//    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.ABSOLUTE_LAYOUT, theme) { init() }
//}
//
//inline fun Activity.absoluteLayout(): android.widget.AbsoluteLayout = absoluteLayout() {}
//inline fun Activity.absoluteLayout(init: (@AnkoViewDslMarker _AbsoluteLayout).() -> Unit): android.widget.AbsoluteLayout {
//    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.ABSOLUTE_LAYOUT, theme = 0) { init() }
//}
//
//inline fun Activity.themedAbsoluteLayout(theme: Int = 0): android.widget.AbsoluteLayout =
//    themedAbsoluteLayout(theme) {}
//
//inline fun Activity.themedAbsoluteLayout(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker _AbsoluteLayout).() -> Unit,
//): android.widget.AbsoluteLayout {
//    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.ABSOLUTE_LAYOUT, theme) { init() }
//}

fun ViewManager.actionMenuView(): ActionMenuView = actionMenuView {}
inline fun ViewManager.actionMenuView(init: (@AnkoViewDslMarker _ActionMenuView).() -> Unit): ActionMenuView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.ACTION_MENU_VIEW, theme = 0) { init() }
}

fun ViewManager.themedActionMenuView(theme: Int = 0): ActionMenuView =
    themedActionMenuView(theme) {}

inline fun ViewManager.themedActionMenuView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _ActionMenuView).() -> Unit,
): ActionMenuView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.ACTION_MENU_VIEW, theme) { init() }
}

fun Context.actionMenuView(): ActionMenuView = actionMenuView {}
inline fun Context.actionMenuView(init: (@AnkoViewDslMarker _ActionMenuView).() -> Unit): ActionMenuView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.ACTION_MENU_VIEW, theme = 0) { init() }
}

fun Context.themedActionMenuView(theme: Int = 0): ActionMenuView =
    themedActionMenuView(theme) {}

inline fun Context.themedActionMenuView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _ActionMenuView).() -> Unit,
): ActionMenuView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.ACTION_MENU_VIEW, theme) { init() }
}

fun Activity.actionMenuView(): ActionMenuView = actionMenuView {}
inline fun Activity.actionMenuView(init: (@AnkoViewDslMarker _ActionMenuView).() -> Unit): ActionMenuView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.ACTION_MENU_VIEW, theme = 0) { init() }
}

fun Activity.themedActionMenuView(theme: Int = 0): ActionMenuView =
    themedActionMenuView(theme) {}

inline fun Activity.themedActionMenuView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _ActionMenuView).() -> Unit,
): ActionMenuView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.ACTION_MENU_VIEW, theme) { init() }
}

fun ViewManager.frameLayout(): FrameLayout = frameLayout {}
inline fun ViewManager.frameLayout(init: (@AnkoViewDslMarker _FrameLayout).() -> Unit): FrameLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.FRAME_LAYOUT, theme = 0) { init() }
}

fun ViewManager.themedFrameLayout(theme: Int = 0): FrameLayout =
    themedFrameLayout(theme) {}

inline fun ViewManager.themedFrameLayout(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _FrameLayout).() -> Unit,
): FrameLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.FRAME_LAYOUT, theme) { init() }
}

fun Context.frameLayout(): FrameLayout = frameLayout {}
inline fun Context.frameLayout(init: (@AnkoViewDslMarker _FrameLayout).() -> Unit): FrameLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.FRAME_LAYOUT, theme = 0) { init() }
}

fun Context.themedFrameLayout(theme: Int = 0): FrameLayout =
    themedFrameLayout(theme) {}

inline fun Context.themedFrameLayout(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _FrameLayout).() -> Unit,
): FrameLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.FRAME_LAYOUT, theme) { init() }
}

fun Activity.frameLayout(): FrameLayout = frameLayout {}
inline fun Activity.frameLayout(init: (@AnkoViewDslMarker _FrameLayout).() -> Unit): FrameLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.FRAME_LAYOUT, theme = 0) { init() }
}

fun Activity.themedFrameLayout(theme: Int = 0): FrameLayout =
    themedFrameLayout(theme) {}

inline fun Activity.themedFrameLayout(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _FrameLayout).() -> Unit,
): FrameLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.FRAME_LAYOUT, theme) { init() }
}

//inline fun ViewManager.gallery(): android.widget.Gallery = gallery() {}
//inline fun ViewManager.gallery(init: (@AnkoViewDslMarker _Gallery).() -> Unit): android.widget.Gallery {
//    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.GALLERY, theme = 0) { init() }
//}
//
//inline fun ViewManager.themedGallery(theme: Int = 0): android.widget.Gallery =
//    themedGallery(theme) {}
//
//inline fun ViewManager.themedGallery(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker _Gallery).() -> Unit,
//): android.widget.Gallery {
//    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.GALLERY, theme) { init() }
//}

//inline fun Context.gallery(): android.widget.Gallery = gallery() {}
//inline fun Context.gallery(init: (@AnkoViewDslMarker _Gallery).() -> Unit): android.widget.Gallery {
//    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.GALLERY, theme = 0) { init() }
//}
//
//inline fun Context.themedGallery(theme: Int = 0): android.widget.Gallery = themedGallery(theme) {}
//inline fun Context.themedGallery(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker _Gallery).() -> Unit,
//): android.widget.Gallery {
//    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.GALLERY, theme) { init() }
//}

//inline fun Activity.gallery(): android.widget.Gallery = gallery() {}
//inline fun Activity.gallery(init: (@AnkoViewDslMarker _Gallery).() -> Unit): android.widget.Gallery {
//    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.GALLERY, theme = 0) { init() }
//}
//
//inline fun Activity.themedGallery(theme: Int = 0): android.widget.Gallery = themedGallery(theme) {}
//inline fun Activity.themedGallery(
//    theme: Int = 0,
//    init: (@AnkoViewDslMarker _Gallery).() -> Unit,
//): android.widget.Gallery {
//    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.GALLERY, theme) { init() }
//}

fun ViewManager.gridLayout(): GridLayout = gridLayout {}
inline fun ViewManager.gridLayout(init: (@AnkoViewDslMarker _GridLayout).() -> Unit): GridLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.GRID_LAYOUT, theme = 0) { init() }
}

fun ViewManager.themedGridLayout(theme: Int = 0): GridLayout =
    themedGridLayout(theme) {}

inline fun ViewManager.themedGridLayout(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _GridLayout).() -> Unit,
): GridLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.GRID_LAYOUT, theme) { init() }
}

fun Context.gridLayout(): GridLayout = gridLayout {}
inline fun Context.gridLayout(init: (@AnkoViewDslMarker _GridLayout).() -> Unit): GridLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.GRID_LAYOUT, theme = 0) { init() }
}

fun Context.themedGridLayout(theme: Int = 0): GridLayout =
    themedGridLayout(theme) {}

inline fun Context.themedGridLayout(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _GridLayout).() -> Unit,
): GridLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.GRID_LAYOUT, theme) { init() }
}

fun Activity.gridLayout(): GridLayout = gridLayout {}
inline fun Activity.gridLayout(init: (@AnkoViewDslMarker _GridLayout).() -> Unit): GridLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.GRID_LAYOUT, theme = 0) { init() }
}

fun Activity.themedGridLayout(theme: Int = 0): GridLayout =
    themedGridLayout(theme) {}

inline fun Activity.themedGridLayout(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _GridLayout).() -> Unit,
): GridLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.GRID_LAYOUT, theme) { init() }
}

fun ViewManager.gridView(): GridView = gridView {}
inline fun ViewManager.gridView(init: (@AnkoViewDslMarker _GridView).() -> Unit): GridView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.GRID_VIEW, theme = 0) { init() }
}

fun ViewManager.themedGridView(theme: Int = 0): GridView =
    themedGridView(theme) {}

inline fun ViewManager.themedGridView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _GridView).() -> Unit,
): GridView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.GRID_VIEW, theme) { init() }
}

fun Context.gridView(): GridView = gridView {}
inline fun Context.gridView(init: (@AnkoViewDslMarker _GridView).() -> Unit): GridView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.GRID_VIEW, theme = 0) { init() }
}

fun Context.themedGridView(theme: Int = 0): GridView =
    themedGridView(theme) {}

inline fun Context.themedGridView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _GridView).() -> Unit,
): GridView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.GRID_VIEW, theme) { init() }
}

fun Activity.gridView(): GridView = gridView {}
inline fun Activity.gridView(init: (@AnkoViewDslMarker _GridView).() -> Unit): GridView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.GRID_VIEW, theme = 0) { init() }
}

fun Activity.themedGridView(theme: Int = 0): GridView =
    themedGridView(theme) {}

inline fun Activity.themedGridView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _GridView).() -> Unit,
): GridView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.GRID_VIEW, theme) { init() }
}

fun ViewManager.horizontalScrollView(): HorizontalScrollView =
    horizontalScrollView {}

inline fun ViewManager.horizontalScrollView(init: (@AnkoViewDslMarker _HorizontalScrollView).() -> Unit): HorizontalScrollView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.HORIZONTAL_SCROLL_VIEW, theme = 0) { init() }
}

fun ViewManager.themedHorizontalScrollView(theme: Int = 0): HorizontalScrollView =
    themedHorizontalScrollView(theme) {}

inline fun ViewManager.themedHorizontalScrollView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _HorizontalScrollView).() -> Unit,
): HorizontalScrollView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.HORIZONTAL_SCROLL_VIEW, theme) { init() }
}

fun Context.horizontalScrollView(): HorizontalScrollView =
    horizontalScrollView {}

inline fun Context.horizontalScrollView(init: (@AnkoViewDslMarker _HorizontalScrollView).() -> Unit): HorizontalScrollView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.HORIZONTAL_SCROLL_VIEW, theme = 0) { init() }
}

fun Context.themedHorizontalScrollView(theme: Int = 0): HorizontalScrollView =
    themedHorizontalScrollView(theme) {}

inline fun Context.themedHorizontalScrollView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _HorizontalScrollView).() -> Unit,
): HorizontalScrollView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.HORIZONTAL_SCROLL_VIEW, theme) { init() }
}

fun Activity.horizontalScrollView(): HorizontalScrollView =
    horizontalScrollView {}

inline fun Activity.horizontalScrollView(init: (@AnkoViewDslMarker _HorizontalScrollView).() -> Unit): HorizontalScrollView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.HORIZONTAL_SCROLL_VIEW, theme = 0) { init() }
}

fun Activity.themedHorizontalScrollView(theme: Int = 0): HorizontalScrollView =
    themedHorizontalScrollView(theme) {}

inline fun Activity.themedHorizontalScrollView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _HorizontalScrollView).() -> Unit,
): HorizontalScrollView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.HORIZONTAL_SCROLL_VIEW, theme) { init() }
}

fun ViewManager.imageSwitcher(): ImageSwitcher = imageSwitcher {}
inline fun ViewManager.imageSwitcher(init: (@AnkoViewDslMarker _ImageSwitcher).() -> Unit): ImageSwitcher {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.IMAGE_SWITCHER, theme = 0) { init() }
}

fun ViewManager.themedImageSwitcher(theme: Int = 0): ImageSwitcher =
    themedImageSwitcher(theme) {}

inline fun ViewManager.themedImageSwitcher(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _ImageSwitcher).() -> Unit,
): ImageSwitcher {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.IMAGE_SWITCHER, theme) { init() }
}

fun Context.imageSwitcher(): ImageSwitcher = imageSwitcher {}
inline fun Context.imageSwitcher(init: (@AnkoViewDslMarker _ImageSwitcher).() -> Unit): ImageSwitcher {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.IMAGE_SWITCHER, theme = 0) { init() }
}

fun Context.themedImageSwitcher(theme: Int = 0): ImageSwitcher =
    themedImageSwitcher(theme) {}

inline fun Context.themedImageSwitcher(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _ImageSwitcher).() -> Unit,
): ImageSwitcher {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.IMAGE_SWITCHER, theme) { init() }
}

fun Activity.imageSwitcher(): ImageSwitcher = imageSwitcher {}
inline fun Activity.imageSwitcher(init: (@AnkoViewDslMarker _ImageSwitcher).() -> Unit): ImageSwitcher {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.IMAGE_SWITCHER, theme = 0) { init() }
}

fun Activity.themedImageSwitcher(theme: Int = 0): ImageSwitcher =
    themedImageSwitcher(theme) {}

inline fun Activity.themedImageSwitcher(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _ImageSwitcher).() -> Unit,
): ImageSwitcher {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.IMAGE_SWITCHER, theme) { init() }
}

fun ViewManager.linearLayout(): LinearLayout = linearLayout {}
inline fun ViewManager.linearLayout(init: (@AnkoViewDslMarker _LinearLayout).() -> Unit): LinearLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.LINEAR_LAYOUT, theme = 0) { init() }
}

fun ViewManager.themedLinearLayout(theme: Int = 0): LinearLayout =
    themedLinearLayout(theme) {}

inline fun ViewManager.themedLinearLayout(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _LinearLayout).() -> Unit,
): LinearLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.LINEAR_LAYOUT, theme) { init() }
}

fun Context.linearLayout(): LinearLayout = linearLayout {}
inline fun Context.linearLayout(init: (@AnkoViewDslMarker _LinearLayout).() -> Unit): LinearLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.LINEAR_LAYOUT, theme = 0) { init() }
}

fun Context.themedLinearLayout(theme: Int = 0): LinearLayout =
    themedLinearLayout(theme) {}

inline fun Context.themedLinearLayout(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _LinearLayout).() -> Unit,
): LinearLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.LINEAR_LAYOUT, theme) { init() }
}

fun Activity.linearLayout(): LinearLayout = linearLayout {}
inline fun Activity.linearLayout(init: (@AnkoViewDslMarker _LinearLayout).() -> Unit): LinearLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.LINEAR_LAYOUT, theme = 0) { init() }
}

fun Activity.themedLinearLayout(theme: Int = 0): LinearLayout =
    themedLinearLayout(theme) {}

inline fun Activity.themedLinearLayout(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _LinearLayout).() -> Unit,
): LinearLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.LINEAR_LAYOUT, theme) { init() }
}

fun ViewManager.radioGroup(): RadioGroup = radioGroup {}
inline fun ViewManager.radioGroup(init: (@AnkoViewDslMarker _RadioGroup).() -> Unit): RadioGroup {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.RADIO_GROUP, theme = 0) { init() }
}

fun ViewManager.themedRadioGroup(theme: Int = 0): RadioGroup =
    themedRadioGroup(theme) {}

inline fun ViewManager.themedRadioGroup(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _RadioGroup).() -> Unit,
): RadioGroup {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.RADIO_GROUP, theme) { init() }
}

fun Context.radioGroup(): RadioGroup = radioGroup {}
inline fun Context.radioGroup(init: (@AnkoViewDslMarker _RadioGroup).() -> Unit): RadioGroup {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.RADIO_GROUP, theme = 0) { init() }
}

fun Context.themedRadioGroup(theme: Int = 0): RadioGroup =
    themedRadioGroup(theme) {}

inline fun Context.themedRadioGroup(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _RadioGroup).() -> Unit,
): RadioGroup {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.RADIO_GROUP, theme) { init() }
}

fun Activity.radioGroup(): RadioGroup = radioGroup {}
inline fun Activity.radioGroup(init: (@AnkoViewDslMarker _RadioGroup).() -> Unit): RadioGroup {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.RADIO_GROUP, theme = 0) { init() }
}

fun Activity.themedRadioGroup(theme: Int = 0): RadioGroup =
    themedRadioGroup(theme) {}

inline fun Activity.themedRadioGroup(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _RadioGroup).() -> Unit,
): RadioGroup {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.RADIO_GROUP, theme) { init() }
}

fun ViewManager.relativeLayout(): RelativeLayout = relativeLayout {}
inline fun ViewManager.relativeLayout(init: (@AnkoViewDslMarker _RelativeLayout).() -> Unit): RelativeLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.RELATIVE_LAYOUT, theme = 0) { init() }
}

fun ViewManager.themedRelativeLayout(theme: Int = 0): RelativeLayout =
    themedRelativeLayout(theme) {}

inline fun ViewManager.themedRelativeLayout(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _RelativeLayout).() -> Unit,
): RelativeLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.RELATIVE_LAYOUT, theme) { init() }
}

fun Context.relativeLayout(): RelativeLayout = relativeLayout {}
inline fun Context.relativeLayout(init: (@AnkoViewDslMarker _RelativeLayout).() -> Unit): RelativeLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.RELATIVE_LAYOUT, theme = 0) { init() }
}

fun Context.themedRelativeLayout(theme: Int = 0): RelativeLayout =
    themedRelativeLayout(theme) {}

inline fun Context.themedRelativeLayout(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _RelativeLayout).() -> Unit,
): RelativeLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.RELATIVE_LAYOUT, theme) { init() }
}

fun Activity.relativeLayout(): RelativeLayout = relativeLayout {}
inline fun Activity.relativeLayout(init: (@AnkoViewDslMarker _RelativeLayout).() -> Unit): RelativeLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.RELATIVE_LAYOUT, theme = 0) { init() }
}

fun Activity.themedRelativeLayout(theme: Int = 0): RelativeLayout =
    themedRelativeLayout(theme) {}

inline fun Activity.themedRelativeLayout(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _RelativeLayout).() -> Unit,
): RelativeLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.RELATIVE_LAYOUT, theme) { init() }
}

fun ViewManager.scrollView(): ScrollView = scrollView {}
inline fun ViewManager.scrollView(init: (@AnkoViewDslMarker _ScrollView).() -> Unit): ScrollView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.SCROLL_VIEW, theme = 0) { init() }
}

fun ViewManager.themedScrollView(theme: Int = 0): ScrollView =
    themedScrollView(theme) {}

inline fun ViewManager.themedScrollView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _ScrollView).() -> Unit,
): ScrollView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.SCROLL_VIEW, theme) { init() }
}

fun Context.scrollView(): ScrollView = scrollView {}
inline fun Context.scrollView(init: (@AnkoViewDslMarker _ScrollView).() -> Unit): ScrollView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.SCROLL_VIEW, theme = 0) { init() }
}

fun Context.themedScrollView(theme: Int = 0): ScrollView =
    themedScrollView(theme) {}

inline fun Context.themedScrollView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _ScrollView).() -> Unit,
): ScrollView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.SCROLL_VIEW, theme) { init() }
}

fun Activity.scrollView(): ScrollView = scrollView {}
inline fun Activity.scrollView(init: (@AnkoViewDslMarker _ScrollView).() -> Unit): ScrollView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.SCROLL_VIEW, theme = 0) { init() }
}

fun Activity.themedScrollView(theme: Int = 0): ScrollView =
    themedScrollView(theme) {}

inline fun Activity.themedScrollView(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _ScrollView).() -> Unit,
): ScrollView {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.SCROLL_VIEW, theme) { init() }
}

fun ViewManager.tableLayout(): TableLayout = tableLayout {}
inline fun ViewManager.tableLayout(init: (@AnkoViewDslMarker _TableLayout).() -> Unit): TableLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.TABLE_LAYOUT, theme = 0) { init() }
}

fun ViewManager.themedTableLayout(theme: Int = 0): TableLayout =
    themedTableLayout(theme) {}

inline fun ViewManager.themedTableLayout(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _TableLayout).() -> Unit,
): TableLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.TABLE_LAYOUT, theme) { init() }
}

fun Context.tableLayout(): TableLayout = tableLayout {}
inline fun Context.tableLayout(init: (@AnkoViewDslMarker _TableLayout).() -> Unit): TableLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.TABLE_LAYOUT, theme = 0) { init() }
}

fun Context.themedTableLayout(theme: Int = 0): TableLayout =
    themedTableLayout(theme) {}

inline fun Context.themedTableLayout(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _TableLayout).() -> Unit,
): TableLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.TABLE_LAYOUT, theme) { init() }
}

fun Activity.tableLayout(): TableLayout = tableLayout {}
inline fun Activity.tableLayout(init: (@AnkoViewDslMarker _TableLayout).() -> Unit): TableLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.TABLE_LAYOUT, theme = 0) { init() }
}

fun Activity.themedTableLayout(theme: Int = 0): TableLayout =
    themedTableLayout(theme) {}

inline fun Activity.themedTableLayout(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _TableLayout).() -> Unit,
): TableLayout {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.TABLE_LAYOUT, theme) { init() }
}

fun ViewManager.tableRow(): TableRow = tableRow {}
inline fun ViewManager.tableRow(init: (@AnkoViewDslMarker _TableRow).() -> Unit): TableRow {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.TABLE_ROW, theme = 0) { init() }
}

fun ViewManager.themedTableRow(theme: Int = 0): TableRow =
    themedTableRow(theme) {}

inline fun ViewManager.themedTableRow(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _TableRow).() -> Unit,
): TableRow {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.TABLE_ROW, theme) { init() }
}

fun Context.tableRow(): TableRow = tableRow {}
inline fun Context.tableRow(init: (@AnkoViewDslMarker _TableRow).() -> Unit): TableRow {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.TABLE_ROW, theme = 0) { init() }
}

fun Context.themedTableRow(theme: Int = 0): TableRow =
    themedTableRow(theme) {}

inline fun Context.themedTableRow(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _TableRow).() -> Unit,
): TableRow {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.TABLE_ROW, theme) { init() }
}

fun Activity.tableRow(): TableRow = tableRow {}
inline fun Activity.tableRow(init: (@AnkoViewDslMarker _TableRow).() -> Unit): TableRow {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.TABLE_ROW, theme = 0) { init() }
}

fun Activity.themedTableRow(theme: Int = 0): TableRow =
    themedTableRow(theme) {}

inline fun Activity.themedTableRow(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _TableRow).() -> Unit,
): TableRow {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.TABLE_ROW, theme) { init() }
}

fun ViewManager.textSwitcher(): TextSwitcher = textSwitcher {}
inline fun ViewManager.textSwitcher(init: (@AnkoViewDslMarker _TextSwitcher).() -> Unit): TextSwitcher {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.TEXT_SWITCHER, theme = 0) { init() }
}

fun ViewManager.themedTextSwitcher(theme: Int = 0): TextSwitcher =
    themedTextSwitcher(theme) {}

inline fun ViewManager.themedTextSwitcher(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _TextSwitcher).() -> Unit,
): TextSwitcher {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.TEXT_SWITCHER, theme) { init() }
}

fun Context.textSwitcher(): TextSwitcher = textSwitcher {}
inline fun Context.textSwitcher(init: (@AnkoViewDslMarker _TextSwitcher).() -> Unit): TextSwitcher {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.TEXT_SWITCHER, theme = 0) { init() }
}

fun Context.themedTextSwitcher(theme: Int = 0): TextSwitcher =
    themedTextSwitcher(theme) {}

inline fun Context.themedTextSwitcher(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _TextSwitcher).() -> Unit,
): TextSwitcher {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.TEXT_SWITCHER, theme) { init() }
}

fun Activity.textSwitcher(): TextSwitcher = textSwitcher {}
inline fun Activity.textSwitcher(init: (@AnkoViewDslMarker _TextSwitcher).() -> Unit): TextSwitcher {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.TEXT_SWITCHER, theme = 0) { init() }
}

fun Activity.themedTextSwitcher(theme: Int = 0): TextSwitcher =
    themedTextSwitcher(theme) {}

inline fun Activity.themedTextSwitcher(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _TextSwitcher).() -> Unit,
): TextSwitcher {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.TEXT_SWITCHER, theme) { init() }
}

fun ViewManager.toolbar(): Toolbar = toolbar {}
inline fun ViewManager.toolbar(init: (@AnkoViewDslMarker _Toolbar).() -> Unit): Toolbar {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.TOOLBAR, theme = 0) { init() }
}

fun ViewManager.themedToolbar(theme: Int = 0): Toolbar =
    themedToolbar(theme) {}

inline fun ViewManager.themedToolbar(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _Toolbar).() -> Unit,
): Toolbar {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.TOOLBAR, theme) { init() }
}

fun Context.toolbar(): Toolbar = toolbar {}
inline fun Context.toolbar(init: (@AnkoViewDslMarker _Toolbar).() -> Unit): Toolbar {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.TOOLBAR, theme = 0) { init() }
}

fun Context.themedToolbar(theme: Int = 0): Toolbar = themedToolbar(theme) {}
inline fun Context.themedToolbar(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _Toolbar).() -> Unit,
): Toolbar {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.TOOLBAR, theme) { init() }
}

fun Activity.toolbar(): Toolbar = toolbar {}
inline fun Activity.toolbar(init: (@AnkoViewDslMarker _Toolbar).() -> Unit): Toolbar {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.TOOLBAR, theme = 0) { init() }
}

fun Activity.themedToolbar(theme: Int = 0): Toolbar = themedToolbar(theme) {}
inline fun Activity.themedToolbar(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _Toolbar).() -> Unit,
): Toolbar {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.TOOLBAR, theme) { init() }
}

fun ViewManager.viewAnimator(): ViewAnimator = viewAnimator {}
inline fun ViewManager.viewAnimator(init: (@AnkoViewDslMarker _ViewAnimator).() -> Unit): ViewAnimator {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.VIEW_ANIMATOR, theme = 0) { init() }
}

fun ViewManager.themedViewAnimator(theme: Int = 0): ViewAnimator =
    themedViewAnimator(theme) {}

inline fun ViewManager.themedViewAnimator(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _ViewAnimator).() -> Unit,
): ViewAnimator {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.VIEW_ANIMATOR, theme) { init() }
}

fun Context.viewAnimator(): ViewAnimator = viewAnimator {}
inline fun Context.viewAnimator(init: (@AnkoViewDslMarker _ViewAnimator).() -> Unit): ViewAnimator {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.VIEW_ANIMATOR, theme = 0) { init() }
}

fun Context.themedViewAnimator(theme: Int = 0): ViewAnimator =
    themedViewAnimator(theme) {}

inline fun Context.themedViewAnimator(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _ViewAnimator).() -> Unit,
): ViewAnimator {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.VIEW_ANIMATOR, theme) { init() }
}

fun Activity.viewAnimator(): ViewAnimator = viewAnimator {}
inline fun Activity.viewAnimator(init: (@AnkoViewDslMarker _ViewAnimator).() -> Unit): ViewAnimator {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.VIEW_ANIMATOR, theme = 0) { init() }
}

fun Activity.themedViewAnimator(theme: Int = 0): ViewAnimator =
    themedViewAnimator(theme) {}

inline fun Activity.themedViewAnimator(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _ViewAnimator).() -> Unit,
): ViewAnimator {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.VIEW_ANIMATOR, theme) { init() }
}

fun ViewManager.viewSwitcher(): ViewSwitcher = viewSwitcher {}
inline fun ViewManager.viewSwitcher(init: (@AnkoViewDslMarker _ViewSwitcher).() -> Unit): ViewSwitcher {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.VIEW_SWITCHER, theme = 0) { init() }
}

fun ViewManager.themedViewSwitcher(theme: Int = 0): ViewSwitcher =
    themedViewSwitcher(theme) {}

inline fun ViewManager.themedViewSwitcher(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _ViewSwitcher).() -> Unit,
): ViewSwitcher {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.VIEW_SWITCHER, theme) { init() }
}

fun Context.viewSwitcher(): ViewSwitcher = viewSwitcher {}
inline fun Context.viewSwitcher(init: (@AnkoViewDslMarker _ViewSwitcher).() -> Unit): ViewSwitcher {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.VIEW_SWITCHER, theme = 0) { init() }
}

fun Context.themedViewSwitcher(theme: Int = 0): ViewSwitcher =
    themedViewSwitcher(theme) {}

inline fun Context.themedViewSwitcher(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _ViewSwitcher).() -> Unit,
): ViewSwitcher {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.VIEW_SWITCHER, theme) { init() }
}

fun Activity.viewSwitcher(): ViewSwitcher = viewSwitcher {}
inline fun Activity.viewSwitcher(init: (@AnkoViewDslMarker _ViewSwitcher).() -> Unit): ViewSwitcher {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.VIEW_SWITCHER, theme = 0) { init() }
}

fun Activity.themedViewSwitcher(theme: Int = 0): ViewSwitcher =
    themedViewSwitcher(theme) {}

inline fun Activity.themedViewSwitcher(
    theme: Int = 0,
    init: (@AnkoViewDslMarker _ViewSwitcher).() -> Unit,
): ViewSwitcher {
    return ankoView(`$$Anko$Factories$Sdk28ViewGroup`.VIEW_SWITCHER, theme) { init() }
}
