package jp.juggler.subwaytooter.mfm

import jp.juggler.util.firstNonNull
import java.util.*
import java.util.regex.Pattern

class NodeParseEnv(
    val useFunction: Boolean,
    private val parentNode: Node,
    val text: String,
    start: Int,
    val end: Int,
) {

    private val childNodes = parentNode.childNodes
    private val allowInside: HashSet<NodeType> =
        mapAllowInside[parentNode.type] ?: hashSetOf()

    // 直前のノードの終了位置
    internal var lastEnd = start

    // 注目中の位置
    internal var pos: Int = 0

    // 直前のノードの終了位置から次のノードの開始位置の手前までをresultに追加する
    private fun closeText(endText: Int) {
        val length = endText - lastEnd
        if (length <= 0) return
        val textInside = text.substring(lastEnd, endText)
        childNodes.add(Node(NodeType.TEXT, arrayOf(textInside), null))
    }

    fun remainMatcher(pattern: Pattern) =
        MatcherCache.matcher(pattern, text, pos, end)

    fun parseInside() {
        if (allowInside.isEmpty()) return

        var i = lastEnd //スキャン中の位置
        while (i < end) {
            // 注目位置の文字に関連するパーサー
            val lastParsers = nodeParserMap[text[i].code]
            if (lastParsers == null) {
                ++i
                continue
            }

            // パーサー用のパラメータを用意する
            // 部分文字列のコストは高くないと信じたい
            pos = i

            val detected = lastParsers.firstNonNull {
                val d = this.it()
                if (d == null) {
                    null
                } else {
                    val n = d.node
                    if (!allowInside.contains(d.node.type)) {
                        MisskeyMarkdownDecoder.log.w(
                            "not allowed : ${parentNode.type} => ${n.type} ${
                                text.substring(
                                    d.start,
                                    d.end
                                )
                            }"
                        )
                        null
                    } else {
                        d
                    }
                }
            }

            if (detected == null) {
                ++i
                continue
            }

            closeText(detected.start)
            childNodes.add(detected.node)
            i = detected.end
            lastEnd = i

            NodeParseEnv(
                useFunction,
                detected.node,
                detected.textInside,
                detected.startInside,
                detected.endInside
            ).parseInside()
        }
        closeText(end)
    }

    fun makeDetected(
        type: NodeType,
        args: Array<String>,
        start: Int,
        end: Int,
        textInside: String,
        startInside: Int,
        lengthInside: Int,
    ): NodeDetected {

        val node = Node(type, args, parentNode)

        if (MisskeyMarkdownDecoder.DEBUG) MisskeyMarkdownDecoder.log.d(
            "NodeDetected: ${node.type} inside=${
                textInside.substring(startInside, startInside + lengthInside)
            }"
        )

        return NodeDetected(
            node,
            start,
            end,
            textInside,
            startInside,
            lengthInside
        )
    }
}
