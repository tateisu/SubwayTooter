package jp.juggler.subwaytooter

import android.content.Intent
import android.text.SpannableStringBuilder
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RawRes
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.*
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.min


fun ActMain.resizeAutoCW(columnW: Int) {
    val sv = PrefS.spAutoCWLines(pref)
    nAutoCwLines = sv.optInt() ?: -1
    if (nAutoCwLines > 0) {
        val lvPad = (0.5f + 12 * density).toInt()
        val iconWidth = avatarIconSize
        val iconEnd = (0.5f + 4 * density).toInt()
        nAutoCwCellWidth = columnW - lvPad * 2 - iconWidth - iconEnd
    }
    // この後各カラムは再描画される
}

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
    val tv = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(nAutoCwCellWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
        if (!timelineFontSizeSp.isNaN()) {
            textSize = timelineFontSizeSp
        }

        val fv = timelineSpacing
        if (fv != null) setLineSpacing(0f, fv)

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

fun ActMain.checkPrivacyPolicy() {

    // 既に表示中かもしれない
    if (dlgPrivacyPolicy?.get()?.isShowing == true) return

    @RawRes val resId = when (getString(R.string.language_code)) {
        "ja" -> R.raw.privacy_policy_ja
        "fr" -> R.raw.privacy_policy_fr
        else -> R.raw.privacy_policy_en
    }

    // プライバシーポリシーデータの読み込み
    val bytes = loadRawResource(resId)
    if (bytes.isEmpty()) return

    // 同意ずみなら表示しない
    val digest = bytes.digestSHA256().encodeBase64Url()
    if (digest == PrefS.spAgreedPrivacyPolicyDigest(pref)) return

    val dialog = AlertDialog.Builder(this)
        .setTitle(R.string.privacy_policy)
        .setMessage(bytes.decodeUTF8())
        .setNegativeButton(R.string.cancel) { _, _ ->
            finish()
        }
        .setOnCancelListener {
            finish()
        }
        .setPositiveButton(R.string.agree) { _, _ ->
            pref.edit().put(PrefS.spAgreedPrivacyPolicyDigest, digest).apply()
        }
        .create()
    dlgPrivacyPolicy = WeakReference(dialog)
    dialog.show()
}

fun ActMain.closeListItemPopup() {
    try {
        listItemPopup?.dismiss()
    } catch (ignored: Throwable) {
    }
    listItemPopup = null
}