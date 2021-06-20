package jp.juggler.subwaytooter.util

import android.view.View
import android.view.ViewGroup
import android.widget.TextView

// marginStart,marginEnd と leftMargin,topMargin の表記ゆれの対策
var ViewGroup.MarginLayoutParams.startMargin: Int
    get() = marginStart
    set(start) {
        marginStart = start
    }

// marginStart,marginEnd と leftMargin,topMargin の表記ゆれの対策
var ViewGroup.MarginLayoutParams.endMargin: Int
    get() = marginEnd
    set(end) {
        marginEnd = end
    }

// paddingStart,paddingEndにはsetterが提供されてない問題の対策
// 表記もtopPadding,bottomPaddingと揃えてある
var View.startPadding: Int
    get() = paddingStart
    set(start) {
        setPaddingRelative(start, paddingTop, paddingEnd, paddingBottom)
    }

// paddingStart,paddingEndにはsetterが提供されてない問題の対策
// 表記もtopPadding,bottomPaddingと揃えてある
var View.endPadding: Int
    get() = paddingEnd
    set(end) {
        setPaddingRelative(paddingStart, paddingTop, end, paddingBottom)
    }

// paddingStart,paddingEndにはsetterが提供されてない問題の対策
fun View.setPaddingStartEnd(start: Int, end: Int = start) {
    setPaddingRelative(start, paddingTop, end, paddingBottom)
}

// XMLのandroid:minWidthと同じことをしたい場合、View#setMinimumWidthとTextView#setMinWidthの両方を呼び出す必要がある
// http://www.thekingsmuseum.info/entry/2015/12/01/233134

var TextView.minWidthCompat: Int
    get() = minWidth
    set(value) {
        minimumWidth = value
        minWidth = value
    }

var TextView.minHeightCompat: Int
    get() = minHeight
    set(value) {
        minimumHeight = value
        minHeight = value
    }
