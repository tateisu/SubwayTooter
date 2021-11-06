package jp.juggler.subwaytooter.mfm

import android.text.SpannableStringBuilder
import android.text.Spanned
import java.util.*


// 文字装飾の指定を溜めておいてノードの親子関係に応じて順序を調整して、最後にまとめて適用する
class SpanList {

    private class SpanPos(var start: Int, var end: Int, val span: Any)

    private val list = LinkedList<SpanPos>()

    fun setSpan(sb: SpannableStringBuilder) =
        list.forEach { sb.setSpan(it.span, it.start, it.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }

    fun addAll(other: SpanList) = list.addAll(other.list)

    fun addWithOffset(src: SpanList, offset: Int) {
        src.list.forEach { addLast(it.start + offset, it.end + offset, it.span) }
    }

    fun addFirst(start: Int, end: Int, span: Any) {
        when {
            start == end -> {
                // empty span allowed
            }

            start > end -> {
                MisskeyMarkdownDecoder.log.e("SpanList.add: range error! start=$start,end=$end,span=$span")
            }

            else -> {
                list.addFirst(SpanPos(start, end, span))
            }
        }
    }

    fun addLast(start: Int, end: Int, span: Any) {
        when {
            start == end -> {
                // empty span allowed
            }

            start > end -> {
                MisskeyMarkdownDecoder.log.e("SpanList.add: range error! start=$start,end=$end,span=$span")
            }

            else -> {
                list.addLast(SpanPos(start, end, span))
            }
        }
    }

    fun insert(offset: Int, length: Int) {
        for (sp in list) {
            when {
                sp.end <= offset -> {
                    // nothing to do
                }

                sp.start <= offset -> {
                    sp.end += length
                }

                else -> {
                    sp.start += length
                    sp.end += length
                }
            }
        }
    }
}
