package jp.juggler.subwaytooter.util

import jp.juggler.util.data.*
import java.math.BigInteger
import java.util.*

class VersionString(src: String?) : Comparable<VersionString> {

    private val src: String

    private val nodeList = ArrayList<Any>()

    val isEmpty: Boolean
        get() = nodeList.isEmpty()

    override fun toString(): String {
        return src
    }

    private class RC(val x: Int) : Comparable<RC> {

        override fun compareTo(other: RC): Int {
            val i = x - other.x
            return if (i > 0) 1 else if (i < 0) -1 else 0
        }

        override fun toString(): String {
            return "RC($x)"
        }
    }

    override fun compareTo(other: VersionString): Int {
        return compare(this, other)
    }

    init {
        this.src = src ?: ""
        if (src?.isNotEmpty() == true) {
            val end = src.length
            var next = 0
            while (next < end) {
                var c = src[next]

                when {
                    isDelimiter(c) -> {
                        // 先頭の区切り文字を無視する
                        ++next
                    }

                    Character.isDigit(c) -> {
                        // 数字列のノード
                        val start = next++
                        while (next < end && Character.isDigit(src[next])) ++next
                        val value = BigInteger(src.substring(start, next))
                        nodeList.add(value)
                    }

                    else -> {
                        val m = reRcX.matcher(src)

                        if (DUMP) {
                            if (m.find(next)) {
                                @Suppress("ForbiddenMethodCall")
                                println("next=$next, match_start=${m.start()}")
                            } else {
                                @Suppress("ForbiddenMethodCall")
                                println("next=$next, not match.")
                            }
                        }

                        if (m.find(next) && m.start() == next) {
                            // RCノード
                            next = m.end()
                            val numStr = m.groupEx(1)
                            val num = if (numStr?.isNotEmpty() == true) {
                                numStr.toInt()
                            } else {
                                0
                            }
                            nodeList.add(RC(num))
                        } else {
                            // 区切り文字と数字以外の文字が並ぶノード
                            val start = next++
                            while (next < end) {
                                c = src[next]
                                if (isDelimiter(c)) break
                                if (Character.isDigit(c)) break
                                ++next
                            }
                            val value = src.substring(start, next)
                            nodeList.add(value)
                        }
                    }
                }
            }
        }
    }

    companion object : Comparator<VersionString> {

        private const val DUMP = false

        private fun isDelimiter(c: Char): Boolean {
            return c == '.' || c == ' '
        }

        private val reRcX = "rc(\\d*)".asciiPattern()

        private fun checkTail(b: Any): Int {
            // 1.0 < 1.0.n  => -1
            // 1.0 < 1.0xxx => -1
            // 1.0 > 1.0rc1  => 1
            return if (b is RC) 1 else -1
        }

        private fun checkBigInteger(a: BigInteger, b: Any): Int {
            if (b is BigInteger) return a.compareTo(b)
            // 数字 > 数字以外
            // 1.5.n > 1.5xxx
            // 1.5.n > 1.5rc1
            return 1
        }

        private fun checkRc(a: RC, b: Any): Int {
            if (b is RC) return a.compareTo(b)
            // RC < string
            // 1.5rc < 1.5xxx
            return -1
        }

        // return -1 if a<b , return 1 if a>b , return 0 if a==b
        override fun compare(a: VersionString, b: VersionString): Int {
            var idx = 0
            loop@ while (true) {
                val ao = if (idx >= a.nodeList.size) null else a.nodeList[idx]
                val bo = if (idx >= b.nodeList.size) null else b.nodeList[idx]
                if (DUMP) {
                    @Suppress("ForbiddenMethodCall")
                    println("a=$ao,b=$bo")
                }

                val i = when {
                    ao == null -> {
                        if (bo == null) return 0
                        checkTail(bo)
                    }

                    bo == null -> -checkTail(ao)
                    ao is BigInteger -> checkBigInteger(ao, bo)
                    bo is BigInteger -> -checkBigInteger(bo, ao)
                    ao is RC -> checkRc(ao, bo)
                    bo is RC -> -checkRc(bo, ao)
                    else -> a.toString().compareTo(b.toString())
                }
                if (i == 0) {
                    ++idx
                    continue@loop
                }
                return i
            }
        }
    }

    // false if this is empty or argument is empty
    // else, true is this is greater or equal argument version
    fun ge(other: VersionString) = when {
        this.isEmpty || other.isEmpty -> false
        else -> this >= other
    }

    val majorVersion: Int?
        get() {
            nodeList.forEach {
                if (it is BigInteger) {
                    return it.toString().toIntOrNull()
                }
            }
            return null
        }
}
