package jp.juggler.subwaytooter.mfm

// マークダウン要素の出現位置
class NodeDetected(
    val node: Node,
    val start: Int, // テキスト中の開始位置
    val end: Int, // テキスト中の終了位置
    val textInside: String, // 内部範囲。親から継承する場合もあるし独自に作る場合もある
    val startInside: Int, // 内部範囲の開始位置
    private val lengthInside: Int, // 内部範囲の終了位置
) {

    val endInside: Int
        get() = startInside + lengthInside
}
