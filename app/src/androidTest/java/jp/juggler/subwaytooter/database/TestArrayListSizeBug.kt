package jp.juggler.subwaytooter.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestArrayListSizeBug {

    @Test
    fun testArrayListSize() {
        val list = ArrayList(arrayOf("c", "b", "a").toList())
        assertEquals("ArrayList size", 3, list.size)
    }

    class ArrayListDerived<E>(args: List<E>) : ArrayList<E>(args)

    @Test
    fun testArrayListDerived() {
        val list = ArrayListDerived(arrayOf("c", "b", "a").toList())
        assertEquals("ArrayListDerived size", 3, list.size)
        // kotlin 1.5.31で(Javaの) size() ではなく getSize() にアクセスしようとして例外を出していた
        // kotlin 1.5.30では大丈夫だったが、JetPack Composeは 1.5.31を要求するのだった…。
    }
}
