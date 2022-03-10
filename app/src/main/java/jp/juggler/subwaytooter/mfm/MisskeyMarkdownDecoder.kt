package jp.juggler.subwaytooter.mfm

import android.graphics.Color
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.util.LogCategory
import jp.juggler.util.removeEndWhitespaces
import java.util.*

object MisskeyMarkdownDecoder {

    internal val log = LogCategory("MisskeyMarkdownDecoder")

    internal const val DEBUG = false

    ////////////////////////////////////////////////////////////////////////////

    private fun mixColor(
        @Suppress("SameParameterValue") col1: Int,
        col2: Int,
    ): Int = Color.rgb(
        (Color.red(col1) + Color.red(col2)) ushr 1,
        (Color.green(col1) + Color.green(col2)) ushr 1,
        (Color.blue(col1) + Color.blue(col2)) ushr 1
    )

    val quoteNestColors = intArrayOf(
        mixColor(Color.GRAY, 0x0000ff),
        mixColor(Color.GRAY, 0x0080ff),
        mixColor(Color.GRAY, 0x00ff80),
        mixColor(Color.GRAY, 0x00ff00),
        mixColor(Color.GRAY, 0x80ff00),
        mixColor(Color.GRAY, 0xff8000),
        mixColor(Color.GRAY, 0xff0000),
        mixColor(Color.GRAY, 0xff0080),
        mixColor(Color.GRAY, 0x8000ff)
    )

    // 入力テキストからタグを抽出するために使う
    // #を含まないタグ文字列のリスト、またはnullを返す
    fun findHashtags(src: String?): ArrayList<String>? {
        try {
            if (src != null) {
                val root = Node(NodeType.ROOT, emptyArray(), null)
                NodeParseEnv(useFunction = true, root, src, 0, src.length).parseInside()
                val result = ArrayList<String>()
                fun track(n: Node) {
                    if (n.type == NodeType.HASHTAG) result.add(n.args[0])
                    n.childNodes.forEach { track(it) }
                }
                track(root)
                if (result.isNotEmpty()) return result
            }
        } catch (ex: Throwable) {
            log.e(ex, "findHashtags failed.")
        }
        return null
    }

    // このファイルのエントリーポイント
    fun decodeMarkdown(options: DecodeOptions, src: String?) =
        SpannableStringBuilderEx().apply {
            val save = options.enlargeCustomEmoji
            options.enlargeCustomEmoji = 2.5f
            try {
                val env = SpanOutputEnv(options, this)

                if (src != null) {
                    val root = Node(NodeType.ROOT, emptyArray(), null)
                    NodeParseEnv(
                        useFunction = (options.linkHelper?.misskeyVersion
                            ?: 12) >= 11, root, src, 0, src.length
                    ).parseInside()
                    env.fireRender(root).setSpan(env.sb)
                }

                // 末尾の空白を取り除く
                this.removeEndWhitespaces()
            } catch (ex: Throwable) {
                log.trace(ex)
                log.e(ex, "decodeMarkdown failed")
            } finally {
                options.enlargeCustomEmoji = save
            }
        }
}
