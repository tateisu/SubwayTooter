package jp.juggler.subwaytooter.mfm

import java.util.*

// あるノードが内部に持てるノード種別のマップ
val mapAllowInside = HashMap<NodeType, HashSet<NodeType>>().apply {

    fun <T> hashSetOf(vararg values: T) = HashSet<T>().apply { addAll(values) }

    infix fun NodeType.wraps(inner: HashSet<NodeType>) = put(this, inner)

    // EMOJI, HASHTAG, MENTION, CODE_BLOCK, QUOTE_INLINE, SEARCH 等はマークダウン要素のネストを許可しない

    NodeType.BIG wraps
            hashSetOf(
                NodeType.EMOJI,
                NodeType.HASHTAG,
                NodeType.MENTION,
                NodeType.FUNCTION,
                NodeType.LATEX,
                NodeType.STRIKE,
                NodeType.SMALL,
                NodeType.ITALIC
            )

    NodeType.BOLD wraps
            hashSetOf(
                NodeType.EMOJI,
                NodeType.HASHTAG,
                NodeType.MENTION,
                NodeType.FUNCTION,
                NodeType.LATEX,
                NodeType.URL,
                NodeType.LINK,
                NodeType.STRIKE,
                NodeType.SMALL,
                NodeType.ITALIC
            )

    NodeType.STRIKE wraps
            hashSetOf(
                NodeType.EMOJI,
                NodeType.HASHTAG,
                NodeType.MENTION,
                NodeType.FUNCTION,
                NodeType.LATEX,
                NodeType.URL,
                NodeType.LINK,
                NodeType.BIG,
                NodeType.BOLD,
                NodeType.SMALL,
                NodeType.ITALIC
            )

    NodeType.SMALL wraps
            hashSetOf(
                NodeType.EMOJI,
                NodeType.HASHTAG,
                NodeType.MENTION,
                NodeType.FUNCTION,
                NodeType.LATEX,
                NodeType.URL,
                NodeType.LINK,
                NodeType.BOLD,
                NodeType.STRIKE,
                NodeType.ITALIC
            )

    NodeType.ITALIC wraps
            hashSetOf(
                NodeType.EMOJI,
                NodeType.HASHTAG,
                NodeType.MENTION,
                NodeType.FUNCTION,
                NodeType.LATEX,
                NodeType.URL,
                NodeType.LINK,
                NodeType.BIG,
                NodeType.BOLD,
                NodeType.STRIKE,
                NodeType.SMALL
            )

    NodeType.MOTION wraps
            hashSetOf(
                NodeType.EMOJI,
                NodeType.HASHTAG,
                NodeType.MENTION,
                NodeType.FUNCTION,
                NodeType.LATEX,
                NodeType.URL,
                NodeType.LINK,
                NodeType.BOLD,
                NodeType.STRIKE,
                NodeType.SMALL,
                NodeType.ITALIC
            )

    NodeType.LINK wraps
            hashSetOf(
                NodeType.EMOJI,
                NodeType.MOTION,
                NodeType.FUNCTION,
                NodeType.LATEX,
                NodeType.BIG,
                NodeType.BOLD,
                NodeType.STRIKE,
                NodeType.SMALL,
                NodeType.ITALIC
            )

    NodeType.TITLE wraps
            hashSetOf(
                NodeType.EMOJI,
                NodeType.HASHTAG,
                NodeType.MENTION,
                NodeType.FUNCTION,
                NodeType.LATEX,
                NodeType.URL,
                NodeType.LINK,
                NodeType.BIG,
                NodeType.BOLD,
                NodeType.STRIKE,
                NodeType.SMALL,
                NodeType.ITALIC,
                NodeType.MOTION,
                NodeType.CODE_INLINE
            )

    NodeType.CENTER wraps
            hashSetOf(
                NodeType.EMOJI,
                NodeType.HASHTAG,
                NodeType.MENTION,
                NodeType.FUNCTION,
                NodeType.LATEX,
                NodeType.URL,
                NodeType.LINK,
                NodeType.BIG,
                NodeType.BOLD,
                NodeType.STRIKE,
                NodeType.SMALL,
                NodeType.ITALIC,
                NodeType.MOTION,
                NodeType.CODE_INLINE
            )

    NodeType.FUNCTION wraps hashSetOf(
        NodeType.CODE_BLOCK,
        NodeType.QUOTE_INLINE,
        NodeType.SEARCH,
        NodeType.EMOJI,
        NodeType.HASHTAG,
        NodeType.MENTION,
        NodeType.LATEX,
        NodeType.URL,
        NodeType.LINK,
        NodeType.BIG,
        NodeType.BOLD,
        NodeType.STRIKE,
        NodeType.SMALL,
        NodeType.ITALIC,
        NodeType.MOTION,
        NodeType.CODE_INLINE,
        NodeType.TITLE,
        NodeType.CENTER,
        NodeType.QUOTE_BLOCK
    )

    NodeType.LATEX wraps hashSetOf(
        NodeType.CODE_BLOCK,
        NodeType.QUOTE_INLINE,
        NodeType.SEARCH,
        NodeType.EMOJI,
        NodeType.HASHTAG,
        NodeType.MENTION,
        NodeType.FUNCTION,
        NodeType.URL,
        NodeType.LINK,
        NodeType.BIG,
        NodeType.BOLD,
        NodeType.STRIKE,
        NodeType.SMALL,
        NodeType.ITALIC,
        NodeType.MOTION,
        NodeType.CODE_INLINE,
        NodeType.TITLE,
        NodeType.CENTER,
        NodeType.QUOTE_BLOCK
    )

    // all except ROOT,TEXT
    val allSet = hashSetOf(
        NodeType.CODE_BLOCK,
        NodeType.QUOTE_INLINE,
        NodeType.SEARCH,
        NodeType.EMOJI,
        NodeType.HASHTAG,
        NodeType.MENTION,
        NodeType.FUNCTION,
        NodeType.LATEX,
        NodeType.URL,
        NodeType.LINK,
        NodeType.BIG,
        NodeType.BOLD,
        NodeType.STRIKE,
        NodeType.SMALL,
        NodeType.ITALIC,
        NodeType.MOTION,
        NodeType.CODE_INLINE,
        NodeType.TITLE,
        NodeType.CENTER,
        NodeType.QUOTE_BLOCK
    )

    NodeType.QUOTE_BLOCK wraps allSet

    NodeType.ROOT wraps allSet
}
