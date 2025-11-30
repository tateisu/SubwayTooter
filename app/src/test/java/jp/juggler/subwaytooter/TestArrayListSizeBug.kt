package jp.juggler.subwaytooter

import kotlin.test.Test
import kotlin.test.assertEquals

class TestArrayListSizeBug {

    @Test
    fun testArrayListSize() {
        val list = ArrayList(arrayOf("c", "b", "a").toList())
        assertEquals(
            expected = 3,
            actual = list.size,
            message = "ArrayList size",
        )
    }

    class ArrayListDerived<E>(args: List<E>) : ArrayList<E>(args)

    @Test
    fun testArrayListDerived() {
        val list = ArrayListDerived(arrayOf("c", "b", "a").toList())
        assertEquals(
            expected = 3,
            actual = list.size,
            message = "ArrayListDerived size",
        )
        // kotlin 1.5.31で(Javaの) size() ではなく getSize() にアクセスしようとして例外を出していた
        // kotlin 1.5.30では大丈夫だったが、JetPack Composeは 1.5.31を要求するのだった…。
    }
}
