package jp.juggler.util.data

import android.os.Build
import java.util.LinkedList
import kotlin.jvm.Throws

// same as x?.let{ dst.add(it) }
fun <T> T.addTo(dst: ArrayList<T>) = dst.add(this)

fun <E : List<*>> E?.notEmpty(): E? =
    if (this?.isNotEmpty() == true) this else null

fun <E : Map<*, *>> E?.notEmpty(): E? =
    if (this?.isNotEmpty() == true) this else null

fun ByteArray?.notEmpty(): ByteArray? =
    if (this?.isNotEmpty() == true) this else null

fun <K, V : Any?> Iterable<Pair<K, V>>.toMutableMap() =
    LinkedHashMap<K, V>().also { map -> forEach { map[it.first] = it.second } }

// 配列中の要素をラムダ式で変換して、戻り値が非nullならそこで処理を打ち切る
inline fun <T, V> Array<out T>.firstNonNull(predicate: (T) -> V?): V? {
    for (element in this) return predicate(element) ?: continue
    return null
}

// 使われてない？
// 使われてるなら関数名を removeFirstIf に変えたい…
//fun <T : Any> MutableCollection<T>.removeFirst(check: (T) -> Boolean): T? {
//    val it = iterator()
//    while (it.hasNext()) {
//        val item = it.next()
//        if (check(item)) {
//            it.remove()
//            return item
//        }
//    }
//    return null
//}

/**
 * compileSdk >= 35 での互換性変更
 * - JavaのList型はKotlinのMutableList型にマッピングされます。
 * - Kotlinコンパイラはそれらをkotlin-stdlibの拡張関数ではなく、新しいList APIに静的に解決します。
 * - List.removeFirst()およびList.removeLast() は API level 35で導入されます。
 * - compileSdk>=35でminSdk<=34のアプリをAndroid 14以下で実行すると、実行時エラーが発生します。
 * Lint警告は出るのでソースをgrepして removeLastCompat に置き換えるべき
 *
 * Note: LinkedListのremoveLast,removeFirstは昔から存在するので、実行時エラーは出ない
 */
@Throws(NoSuchElementException::class, IndexOutOfBoundsException::class)
fun <T : Any?> MutableList<T>.removeLastCompat(): T = when {
    this is LinkedList -> removeLast()
    Build.VERSION.SDK_INT >= 35 -> removeLast()
    else -> removeAt(lastIndex)
}

@Throws(NoSuchElementException::class, IndexOutOfBoundsException::class)
fun <T : Any?> MutableList<T>.removeFirstCompat(): T = when {
    this is LinkedList -> removeFirst()
    Build.VERSION.SDK_INT >= 35 -> removeFirst()
    else -> removeAt(0)
}
