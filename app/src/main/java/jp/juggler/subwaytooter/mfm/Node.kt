package jp.juggler.subwaytooter.mfm

import java.util.*

// マークダウン要素
class Node(
    val type: NodeType, // ノード種別
    val args: Array<String> = emptyArray(), // 引数
    parentNode: Node?,
) {

    val childNodes = LinkedList<Node>()

    val quoteNest: Int = (parentNode?.quoteNest ?: 0) + when (type) {
        NodeType.QUOTE_BLOCK, NodeType.QUOTE_INLINE -> 1
        else -> 0
    }
}
