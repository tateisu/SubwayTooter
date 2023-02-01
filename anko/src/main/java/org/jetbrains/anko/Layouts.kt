@file:JvmName("Sdk28LayoutsKt")
@file:Suppress("MemberVisibilityCanBePrivate")

package org.jetbrains.anko

import android.app.ActionBar
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.*

@Suppress("ClassNaming")
open class _AppWidgetHostView(ctx: Context) : AppWidgetHostView(ctx) {

    inline fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    ): T {
        val layoutParams = LayoutParams(width, height)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        gravity: Int,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height, gravity)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        gravity: Int,
    ): T {
        val layoutParams = LayoutParams(width, height, gravity)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: ViewGroup.LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: ViewGroup.LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: MarginLayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: MarginLayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }
}

//open class _AbsoluteLayout(ctx: Context): AbsoluteLayout(ctx) {
//
//    inline fun <T: View> T.lparams(
//            width: Int = android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
//            height: Int = android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
//            x: Int,
//            y: Int,
//            init: AbsoluteLayout.LayoutParams.() -> Unit
//    ): T {
//        val layoutParams = AbsoluteLayout.LayoutParams(width, height, x, y)
//        layoutParams.init()
//        this@lparams.layoutParams = layoutParams
//        return this
//    }
//
//    inline fun <T: View> T.lparams(
//            width: Int = android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
//            height: Int = android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
//            x: Int,
//            y: Int
//    ): T {
//        val layoutParams = AbsoluteLayout.LayoutParams(width, height, x, y)
//        this@lparams.layoutParams = layoutParams
//        return this
//    }
//
//    inline fun <T: View> T.lparams(
//            c: Context?,
//            attrs: AttributeSet?,
//            init: AbsoluteLayout.LayoutParams.() -> Unit
//    ): T {
//        val layoutParams = AbsoluteLayout.LayoutParams(c!!, attrs!!)
//        layoutParams.init()
//        this@lparams.layoutParams = layoutParams
//        return this
//    }
//
//    inline fun <T: View> T.lparams(
//            c: Context?,
//            attrs: AttributeSet?
//    ): T {
//        val layoutParams = AbsoluteLayout.LayoutParams(c!!, attrs!!)
//        this@lparams.layoutParams = layoutParams
//        return this
//    }
//
//    inline fun <T: View> T.lparams(
//            source: ViewGroup.LayoutParams?,
//            init: AbsoluteLayout.LayoutParams.() -> Unit
//    ): T {
//        val layoutParams = AbsoluteLayout.LayoutParams(source!!)
//        layoutParams.init()
//        this@lparams.layoutParams = layoutParams
//        return this
//    }
//
//    inline fun <T: View> T.lparams(
//            source: ViewGroup.LayoutParams?
//    ): T {
//        val layoutParams = AbsoluteLayout.LayoutParams(source!!)
//        this@lparams.layoutParams = layoutParams
//        return this
//    }
//
//}

@Suppress("ClassNaming")
open class _ActionMenuView(ctx: Context) : ActionMenuView(ctx) {

    inline fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        other: ViewGroup.LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(other!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        other: ViewGroup.LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(other!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        other: LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(other!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        other: LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(other!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    ): T {
        val layoutParams = LayoutParams(width, height)
        this@lparams.layoutParams = layoutParams
        return this
    }
}

@Suppress("ClassNaming")
open class _FrameLayout(ctx: Context) : FrameLayout(ctx) {

    inline fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    ): T {
        val layoutParams = LayoutParams(width, height)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        gravity: Int,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height, gravity)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        gravity: Int,
    ): T {
        val layoutParams = LayoutParams(width, height, gravity)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: ViewGroup.LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: ViewGroup.LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: MarginLayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: MarginLayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }
}

//open class _Gallery(ctx: Context): Gallery(ctx) {
//
//    inline fun <T: View> T.lparams(
//            c: Context?,
//            attrs: AttributeSet?,
//            init: Gallery.LayoutParams.() -> Unit
//    ): T {
//        val layoutParams = Gallery.LayoutParams(c!!, attrs!!)
//        layoutParams.init()
//        this@lparams.layoutParams = layoutParams
//        return this
//    }
//
//    inline fun <T: View> T.lparams(
//            c: Context?,
//            attrs: AttributeSet?
//    ): T {
//        val layoutParams = Gallery.LayoutParams(c!!, attrs!!)
//        this@lparams.layoutParams = layoutParams
//        return this
//    }
//
//    inline fun <T: View> T.lparams(
//            width: Int = android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
//            height: Int = android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
//            init: Gallery.LayoutParams.() -> Unit
//    ): T {
//        val layoutParams = Gallery.LayoutParams(width, height)
//        layoutParams.init()
//        this@lparams.layoutParams = layoutParams
//        return this
//    }
//
//    inline fun <T: View> T.lparams(
//            width: Int = android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
//            height: Int = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
//    ): T {
//        val layoutParams = Gallery.LayoutParams(width, height)
//        this@lparams.layoutParams = layoutParams
//        return this
//    }
//
//    inline fun <T: View> T.lparams(
//            source: ViewGroup.LayoutParams?,
//            init: Gallery.LayoutParams.() -> Unit
//    ): T {
//        val layoutParams = Gallery.LayoutParams(source!!)
//        layoutParams.init()
//        this@lparams.layoutParams = layoutParams
//        return this
//    }
//
//    inline fun <T: View> T.lparams(
//            source: ViewGroup.LayoutParams?
//    ): T {
//        val layoutParams = Gallery.LayoutParams(source!!)
//        this@lparams.layoutParams = layoutParams
//        return this
//    }
//
//}

@Suppress("ClassNaming")
open class _GridLayout(ctx: Context) : GridLayout(ctx) {

    inline fun <T : View> T.lparams(
        rowSpec: Spec?,
        columnSpec: Spec?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(rowSpec!!, columnSpec!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        rowSpec: Spec?,
        columnSpec: Spec?,
    ): T {
        val layoutParams = LayoutParams(rowSpec!!, columnSpec!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams()
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(): T {
        val layoutParams = LayoutParams()
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        params: ViewGroup.LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(params!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        params: ViewGroup.LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(params!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        params: MarginLayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(params!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        params: MarginLayoutParams?,
    ): T {
        val layoutParams = LayoutParams(params!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        context: Context?,
        attrs: AttributeSet?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(context!!, attrs!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        context: Context?,
        attrs: AttributeSet?,
    ): T {
        val layoutParams = LayoutParams(context!!, attrs!!)
        this@lparams.layoutParams = layoutParams
        return this
    }
}

@Suppress("ClassNaming")
open class _GridView(ctx: Context) : GridView(ctx) {

    inline fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    ): T {
        val layoutParams = LayoutParams(width, height)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        viewType: Int,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height, viewType)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        viewType: Int,
    ): T {
        val layoutParams = LayoutParams(width, height, viewType)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: ViewGroup.LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: ViewGroup.LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }
}

@Suppress("ClassNaming")
open class _HorizontalScrollView(ctx: Context) : HorizontalScrollView(ctx) {

    inline fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    ): T {
        val layoutParams = LayoutParams(width, height)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        gravity: Int,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height, gravity)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        gravity: Int,
    ): T {
        val layoutParams = LayoutParams(width, height, gravity)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: ViewGroup.LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: ViewGroup.LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: MarginLayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: MarginLayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }
}

@Suppress("ClassNaming")
open class _ImageSwitcher(ctx: Context) : ImageSwitcher(ctx) {

    inline fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    ): T {
        val layoutParams = LayoutParams(width, height)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        gravity: Int,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height, gravity)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        gravity: Int,
    ): T {
        val layoutParams = LayoutParams(width, height, gravity)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: ViewGroup.LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: ViewGroup.LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: MarginLayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: MarginLayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }
}

@Suppress("ClassNaming")
open class _LinearLayout(ctx: Context) : LinearLayout(ctx) {

    inline fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    ): T {
        val layoutParams = LayoutParams(width, height)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        weight: Float,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height, weight)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        weight: Float,
    ): T {
        val layoutParams = LayoutParams(width, height, weight)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        p: ViewGroup.LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(p!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        p: ViewGroup.LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(p!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: MarginLayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: MarginLayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }
}

@Suppress("ClassNaming")
open class _RadioGroup(ctx: Context) : RadioGroup(ctx) {

    inline fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    ): T {
        val layoutParams = LayoutParams(width, height)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        initWeight: Float,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height, initWeight)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        initWeight: Float,
    ): T {
        val layoutParams = LayoutParams(width, height, initWeight)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        p: ViewGroup.LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(p!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        p: ViewGroup.LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(p!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: MarginLayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: MarginLayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }
}

@Suppress("ClassNaming")
open class _RelativeLayout(ctx: Context) : RelativeLayout(ctx) {

    inline fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    ): T {
        val layoutParams = LayoutParams(width, height)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: ViewGroup.LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: ViewGroup.LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: MarginLayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: MarginLayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }
}

@Suppress("ClassNaming")
open class _ScrollView(ctx: Context) : ScrollView(ctx) {

    inline fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    ): T {
        val layoutParams = LayoutParams(width, height)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        gravity: Int,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height, gravity)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        gravity: Int,
    ): T {
        val layoutParams = LayoutParams(width, height, gravity)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: ViewGroup.LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: ViewGroup.LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: MarginLayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: MarginLayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }
}

@Suppress("ClassNaming")
open class _TableLayout(ctx: Context) : TableLayout(ctx) {

    inline fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    ): T {
        val layoutParams = LayoutParams(width, height)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        initWeight: Float,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height, initWeight)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        initWeight: Float,
    ): T {
        val layoutParams = LayoutParams(width, height, initWeight)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams()
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(): T {
        val layoutParams = LayoutParams()
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        p: ViewGroup.LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(p!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        p: ViewGroup.LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(p!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: MarginLayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: MarginLayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }
}

@Suppress("ClassNaming")
open class _TableRow(ctx: Context) : TableRow(ctx) {

    inline fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    ): T {
        val layoutParams = LayoutParams(width, height)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        initWeight: Float,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height, initWeight)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        initWeight: Float,
    ): T {
        val layoutParams = LayoutParams(width, height, initWeight)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams()
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(): T {
        val layoutParams = LayoutParams()
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        column: Int,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(column)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        column: Int,
    ): T {
        val layoutParams = LayoutParams(column)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        p: ViewGroup.LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(p!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        p: ViewGroup.LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(p!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: MarginLayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: MarginLayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }
}

@Suppress("ClassNaming")
open class _TextSwitcher(ctx: Context) : TextSwitcher(ctx) {

    inline fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    ): T {
        val layoutParams = LayoutParams(width, height)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        gravity: Int,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height, gravity)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        gravity: Int,
    ): T {
        val layoutParams = LayoutParams(width, height, gravity)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: ViewGroup.LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: ViewGroup.LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: MarginLayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: MarginLayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }
}

@Suppress("ClassNaming")
open class _Toolbar(ctx: Context) : Toolbar(ctx) {

    inline fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    ): T {
        val layoutParams = LayoutParams(width, height)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        gravity: Int,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height, gravity)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        gravity: Int,
    ): T {
        val layoutParams = LayoutParams(width, height, gravity)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        gravity: Int,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(gravity)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        gravity: Int,
    ): T {
        val layoutParams = LayoutParams(gravity)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: ActionBar.LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: ActionBar.LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: MarginLayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: MarginLayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: ViewGroup.LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: ViewGroup.LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }
}

@Suppress("ClassNaming")
open class _ViewAnimator(ctx: Context) : ViewAnimator(ctx) {

    inline fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    ): T {
        val layoutParams = LayoutParams(width, height)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        gravity: Int,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height, gravity)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        gravity: Int,
    ): T {
        val layoutParams = LayoutParams(width, height, gravity)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: ViewGroup.LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: ViewGroup.LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: MarginLayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: MarginLayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }
}

@Suppress("ClassNaming")
open class _ViewSwitcher(ctx: Context) : ViewSwitcher(ctx) {

    inline fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        c: Context?,
        attrs: AttributeSet?,
    ): T {
        val layoutParams = LayoutParams(c!!, attrs!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    ): T {
        val layoutParams = LayoutParams(width, height)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        gravity: Int,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(width, height, gravity)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        gravity: Int,
    ): T {
        val layoutParams = LayoutParams(width, height, gravity)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: ViewGroup.LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: ViewGroup.LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: MarginLayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: MarginLayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }

    inline fun <T : View> T.lparams(
        source: LayoutParams?,
        init: LayoutParams.() -> Unit,
    ): T {
        val layoutParams = LayoutParams(source!!)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }

    fun <T : View> T.lparams(
        source: LayoutParams?,
    ): T {
        val layoutParams = LayoutParams(source!!)
        this@lparams.layoutParams = layoutParams
        return this
    }
}
