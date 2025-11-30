package jp.juggler.subwaytooter.actmain

import android.text.SpannableStringBuilder
import android.view.View
import android.widget.LinearLayout
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.pref.PrefS
import jp.juggler.subwaytooter.span.NetworkEmojiSpan
import jp.juggler.subwaytooter.view.MyTextView
import java.lang.ref.WeakReference

/**
 * 各カラムの各投稿のコンテンツ部分の幅を覚える
 */
fun ActMain.saveContentTextWidth(columnW: Int) {
    val lvPad = (0.5f + 12 * density).toInt()
    val iconWidth = avatarIconSize
    val iconEnd = (0.5f + 4 * density).toInt()

    val contentTextWidth = columnW - lvPad * 2 - iconWidth - iconEnd

    // CW部分の絵文字とか、リアクションなら数字と枠を避けたサイズとか
    val maxEmojiWidth = contentTextWidth - (64f * density + 0.5f)

    // NetworkEmojiSpanに覚える
    NetworkEmojiSpan.maxEmojiWidth = maxEmojiWidth

    // 自動CW用の値に覚える
    val sv = PrefS.spAutoCWLines.value
    nAutoCwLines = sv.toIntOrNull() ?: -1
    if (nAutoCwLines > 0) {
        nAutoCwCellWidth = contentTextWidth
    }
    // この後各カラムは再描画される
}

// 与えられたステータスのTootStatus.AutoCWオブジェクトを更新する
// TextViewを作って実際にレイアウトさせて表示行数を数える黒魔術
fun ActMain.checkAutoCW(status: TootStatus, text: CharSequence) {
    if (nAutoCwCellWidth <= 0) {
        // 設定が無効
        status.auto_cw = null
        return
    }

    var autoCw = status.auto_cw
    if (autoCw != null &&
        autoCw.refActivity?.get() === this &&
        autoCw.cellWidth == nAutoCwCellWidth
    ) {
        // 以前に計算した値がまだ使える
        return
    }

    if (autoCw == null) {
        autoCw = TootStatus.AutoCW()
        status.auto_cw = autoCw
    }

    // 計算時の条件(文字フォント、文字サイズ、カラム幅）を覚えておいて、再利用時に同じか確認する
    autoCw.refActivity = WeakReference(this)
    autoCw.cellWidth = nAutoCwCellWidth
    autoCw.decodedSpoilerText = null

    // テキストをレイアウトして行数を測定
    val tv = MyTextView(this).apply {
        layoutParams =
            LinearLayout.LayoutParams(nAutoCwCellWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
        ActMain.timelineFontSizeSp.takeIf { it.isFinite() }
            ?.let{ textSize = ActMain.timelineFontSizeSp }

        ActMain.timelineSpacing
            ?.let{ setLineSpacing(0f, it) }

        typeface = ActMain.timelineFont
        this.text = text
    }

    tv.measure(
        View.MeasureSpec.makeMeasureSpec(nAutoCwCellWidth, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    val l = tv.layout
    if (l != null) {
        autoCw.originalLineCount = l.lineCount
        val lineCount = autoCw.originalLineCount

        if ((nAutoCwLines > 0 && lineCount > nAutoCwLines) &&
            status.spoiler_text.isEmpty() &&
            (status.mentions?.size ?: 0) <= nAutoCwLines
        ) {
            val sb = SpannableStringBuilder()
            sb.append(getString(R.string.auto_cw_prefix))
            sb.append(text, 0, l.getLineEnd(nAutoCwLines - 1))
            var last = sb.length
            while (last > 0) {
                val c = sb[last - 1]
                if (c == '\n' || Character.isWhitespace(c)) {
                    --last
                    continue
                }
                break
            }
            if (last < sb.length) {
                sb.delete(last, sb.length)
            }
            sb.append('…')
            autoCw.decodedSpoilerText = sb
        }
    }
}
