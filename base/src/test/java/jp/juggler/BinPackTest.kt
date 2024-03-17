package jp.juggler

import jp.juggler.util.data.BinPackList
import jp.juggler.util.data.BinPackMap
import jp.juggler.util.data.decodeBinPack
import jp.juggler.util.data.encodeBinPack
import kotlin.test.DefaultAsserter.assertEquals
import kotlin.test.DefaultAsserter.assertNotNull
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.Test
import kotlin.test.assertContentEquals

class BinPackTest {
    @Test
    fun testTypes() {
        fun ByteArray.dump() = joinToString(" ") { "%d".format(it) }

        fun encodeDecode(v: Any?, expected: Any? = v) {
            val encoded = v.encodeBinPack()
            val decoded = encoded.decodeBinPack()
            val message = "($v ${v?.javaClass?.simpleName}) dump=${encoded.dump()}"
            when {
                expected is ByteArray -> assertContentEquals(
                    expected,
                    decoded as? ByteArray,
                    "${v?.javaClass?.simpleName} $v",
                )

                v is Set<*> -> {
                    val decodedSet = (decoded as? BinPackList)?.toSet()
                    assertNotNull("$message decoded?", decodedSet)
                    assertEquals("$message same size", v.size, decodedSet!!.size)
                    assertTrue("$message containsAll 1", v.containsAll(decodedSet))
                    assertTrue("$message containsAll 2", decodedSet.containsAll(v))
                }

                else -> assertEquals(
                    message,
                    expected,
                    decoded
                )
            }
        }

        encodeDecode(null)
        encodeDecode(true)
        encodeDecode(false)
        encodeDecode(ByteArray(0))
        encodeDecode(ByteArray(1) { it.toByte() })
        encodeDecode(ByteArray(3) { it.toByte() })
        encodeDecode("")
        encodeDecode("日本語")
        encodeDecode(emptyArray<Any?>(), BinPackList())
        encodeDecode(emptyList<Any?>(), BinPackList())
        encodeDecode(emptySet<Any?>(), BinPackList())
        encodeDecode(arrayOf<Any?>(null), BinPackList(null))
        encodeDecode(listOf<Any?>(null), BinPackList(null))
        encodeDecode(setOf<Any?>(null))
        encodeDecode(arrayOf("a"), BinPackList("a"))
        encodeDecode(listOf("a"), BinPackList("a"))
        encodeDecode(setOf("a"))
        encodeDecode(emptyMap<Any?, Any?>(), BinPackMap())
        encodeDecode(mapOf<Any?, Any?>(null to null), BinPackMap(null to null))
        encodeDecode(mapOf<Any?, Any?>(1 to 1), BinPackMap(1 to 1))

        fun doubleStepSequence(start: Double, endInclusive: Double, step: Double) =
            sequence {
                var v = start
                while (true) {
                    yield(v)
                    val newValue = v + step
                    // 範囲を超えたか、オーバーフローで同じ値になるか
                    if (newValue > endInclusive || newValue == v) break
                    v = newValue
                }
            }

        fun floatStepSequence(start: Float, endInclusive: Float, step: Float) =
            sequence {
                var v = start
                while (true) {
                    yield(v)
                    val newValue = v + step
                    // 範囲を超えたか、オーバーフローで同じ値になるか
                    if (newValue > endInclusive || newValue == v) break
                    v = newValue
                }
            }

        // - ビット数の多い数値型は適当に端折るが、下位ビットが毎回同じにならないよう stepを工夫する

        encodeDecode(0.0)
        encodeDecode(Double.NaN)
        encodeDecode(Double.NEGATIVE_INFINITY)
        encodeDecode(Double.POSITIVE_INFINITY)
        encodeDecode(Double.MIN_VALUE) // nealy 0 positive value
        encodeDecode(-Double.MAX_VALUE) // negative min end
        encodeDecode(Double.MAX_VALUE)
        run {
            var callCount = 0
            for (n in doubleStepSequence(
                -Double.MAX_VALUE,
                Double.MAX_VALUE,
                Double.MAX_VALUE / (256 - 7 - Double.MIN_VALUE)
            )) {
                encodeDecode(n)
                ++callCount
            }
            assertEquals("callCount", 498, callCount)
        }

        encodeDecode(0f)
        encodeDecode(Float.NaN)
        encodeDecode(Float.NEGATIVE_INFINITY)
        encodeDecode(Float.POSITIVE_INFINITY)
        encodeDecode(Float.MIN_VALUE) // nealy 0 positive value
        encodeDecode(-Float.MAX_VALUE) // negative min end
        encodeDecode(Float.MAX_VALUE)
        run {
            var callCount = 0
            for (n in floatStepSequence(
                -Float.MAX_VALUE,
                Float.MAX_VALUE,
                Float.MAX_VALUE / (256 - 7 - Float.MIN_VALUE)
            )) {
                encodeDecode(n)
                ++callCount
            }
            assertEquals("callCount", 499, callCount)
        }

        // - ByteとShortにはIntRangeと同等のクラスがない

        encodeDecode(0.toByte())
        encodeDecode(Byte.MIN_VALUE)
        encodeDecode(Byte.MAX_VALUE)
        for (n in Byte.MIN_VALUE.toInt()..Byte.MAX_VALUE.toInt()) {
            encodeDecode(n.toByte())
        }

        encodeDecode(0.toShort())
        encodeDecode(Short.MIN_VALUE)
        encodeDecode(Short.MAX_VALUE)
        for (n in Short.MIN_VALUE.toInt()..Short.MAX_VALUE.toInt() step 1.shl(Short.SIZE_BITS - 8) - 7) {
            encodeDecode(n.toShort())
        }

        encodeDecode(0.toChar())
        encodeDecode(Char.MIN_VALUE)
        encodeDecode(Char.MAX_VALUE)
        for (n in Char.MIN_VALUE..Char.MAX_VALUE step 1.shl(Char.SIZE_BITS - 8) - 7) {
            encodeDecode(n)
        }

        encodeDecode(0)
        encodeDecode(Int.MIN_VALUE)
        encodeDecode(Int.MAX_VALUE)
        for (n in Int.MIN_VALUE..Int.MAX_VALUE step 1.shl(Int.SIZE_BITS - 8) - 7) {
            encodeDecode(n)
        }

        encodeDecode(0.toLong())
        encodeDecode(Long.MIN_VALUE)
        encodeDecode(Long.MAX_VALUE)
        for (n in Long.MIN_VALUE..Long.MAX_VALUE step 1L.shl(Long.SIZE_BITS - 8) - 7) {
            encodeDecode(n)
        }
    }
}
