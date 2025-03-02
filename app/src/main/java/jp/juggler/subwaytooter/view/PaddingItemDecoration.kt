package jp.juggler.subwaytooter.view

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * 各行にパディングを設定する RecyclerView.ItemDecoration
 * - 最初の行のtop と最後の行のbottomを変更できる
 *
 * Note: RecyclerView全体にpaddingを設定してclip To Paddingすると
 * requiresFadingEdgeした時に描画が崩れるので、そのワークアラウンド
 */
class PaddingItemDecoration(
    private val horizontal: Int,
    private val vertical: Int,
    private val firstTop: Int?,
    private val lastBottom: Int?,
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val position = parent.getChildAdapterPosition(view)
        val size = parent.adapter?.itemCount ?: 0
        super.getItemOffsets(outRect, view, parent, state)
        outRect.set(
            horizontal,
            when {
                position == 0 -> firstTop ?: vertical
                else -> vertical
            },
            horizontal,
            when {
                position == size - 1 -> lastBottom ?: vertical
                else -> vertical
            },
        )
    }
}
