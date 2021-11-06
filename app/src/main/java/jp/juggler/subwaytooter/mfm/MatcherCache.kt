package jp.juggler.subwaytooter.mfm

import java.util.HashMap
import java.util.regex.Matcher
import java.util.regex.Pattern


// 正規表現パターンごとにMatcherをキャッシュする
// 対象テキストが変わったらキャッシュを捨てて更新する
// Matcher#region(start,text.length) を設定してから返す
// (同一テキストに対してMatcher.usePatternで正規表現パターンを切り替えるのも検討したが、usePatternの方が多分遅くなる)
internal object MatcherCache {

    private class MatcherCacheItem(
        var matcher: Matcher,
        var text: String,
        var textHashCode: Int,
    )

    // スレッドごとにキャッシュ用のマップを持つ
    private val matcherCacheMap =
        object : ThreadLocal<HashMap<Pattern, MatcherCacheItem>>() {
            override fun initialValue(): HashMap<Pattern, MatcherCacheItem> = HashMap()
        }

    internal fun matcher(
        pattern: Pattern,
        text: String,
        start: Int = 0,
        end: Int = text.length,
    ): Matcher {
        val m: Matcher
        val textHashCode = text.hashCode()
        val map = matcherCacheMap.get()!!
        val item = map[pattern]
        if (item != null) {
            if (item.textHashCode != textHashCode || item.text != text) {
                item.matcher = pattern.matcher(text).apply {
                    useAnchoringBounds(true)
                }
                item.text = text
                item.textHashCode = textHashCode
            }
            m = item.matcher
        } else {
            m = pattern.matcher(text).apply {
                useAnchoringBounds(true)
            }
            map[pattern] = MatcherCacheItem(m, text, textHashCode)
        }
        m.region(start, end)
        return m
    }
}
