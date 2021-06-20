@file:Suppress(
    "USELESS_CAST", "unused", "DEPRECATED_IDENTITY_EQUALS", "UNUSED_VARIABLE",
    "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER",
    "ReplaceCallWithComparison"
)

package jp.juggler.subwaytooter

import org.junit.Test

//import kotlin.test.*

typealias TestLambdaCallback = (x: Int) -> Int

class TestKotlinFeature {

    private val CODE_A = 1
    private val CODE_A2 = 2

    // コンパイラに予測できない方法でIntの10を生成する
    private fun generate10A(): Int {
        return ("1" + "0").toInt()
    }

    // コンパイラに予測できない方法でIntの10を生成する
    private fun generate10B(): Int {
        var i = 0
        while (true) {
            ++i
            if (i % 5 == 0 && i % 2 == 0) return i
        }
    }

    interface MyKotlinInterface {

        fun method(x: Int): Int
    }

    @Test
    fun testLambda() {
        // 定義例(文脈あり)
        println(10.let { x -> x * x })
        println(10)
        // 定義例(文脈不明)
        val a = { println("testLambda") }

        // 参照型の定義
        val ref: (x: Int) -> Int = { it * it }

        // 参照型の呼び出し
        println(ref(10))

        // 参照型の定義(Nullable)
        @Suppress("RedundantNullableReturnType")
        val refNullable: TestLambdaCallback? = { it * it }
        if (refNullable != null) {
            refNullable(10)
        }
    }

    @Test
    fun testAnonymousFunction() {
        // 定義例(文脈あり)
        println(10.let(fun(x: Int) = x * x))
        println(10)
        // 定義例(文脈不明)
        val a = fun(x: Int) = x * x

        // 参照型の定義
        val ref: (x: Int) -> Int = a

        // 参照型の呼び出し
        println(ref(10))

        // 参照型の定義(Nullable)
        @Suppress("RedundantNullableReturnType")
        val refNullable: TestLambdaCallback? = fun(i: Int) = i * i
        if (refNullable != null) {
            refNullable(10)
        }
    }

    @Test
    fun testObjectExpression() {
        // 定義例(文脈あり)
        @Suppress("UnnecessaryAbstractClass")
        abstract class Base {
            abstract fun method(x: Int): Int
        }

        val a = object : Base() {
            override fun method(x: Int): Int {
                return x * x
            }
        }

        // 定義例(文脈の有無で変化しない)
        // 参照型の定義
        val ref: Base = a

        // 参照型の定義(Nullable)
        @Suppress("RedundantNullableReturnType")
        val refNullable: Base? = a

        if (refNullable != null) {
            val v = refNullable.method(10)
            println("OE v=$v")
        }

        fun caller(b: Base) {
            val v = b.method(10)
            println("OE b $v")
        }

        caller(object : Base() {
            override fun method(x: Int): Int {
                return x * x * x
            }
        })
    }

    private fun member(x: Int) = x * x

    @Test
    fun testMemberReference() {
        fun caller(a: (receiver: TestKotlinFeature, x: Int) -> Int) {
            val v = a(this, 10)
            println("testMemberReference caller $v")
        }
        caller(TestKotlinFeature::member)

        val b = TestKotlinFeature::member
        val a: (receiver: TestKotlinFeature, x: Int) -> Int = TestKotlinFeature::member
    }

    fun methodNotInline(callback: (x: Int) -> Int): Int {
        return callback(3)
    }

    inline fun methodInline(callback: (x: Int) -> Int): Int {
        return callback(5)
    }

    @Test
    fun testReturn() {

        //		loop@ for( i in 1..2) {
        //			// 関数の引数以外の場所で定義したラムダ式
        //			var x = { x : Int ->
        //				break // コンパイルエラー
        //				break@loop // コンパイルエラー
        //				// return // コンパイルエラー
        //				x * x
        //			}(10)
        //			println("testReturn A:$x")
        //
        //			// 非インライン関数の引数として定義したラムダ式
        //			x = methodNotInline { x : Int ->
        //				break // コンパイルエラー
        //				break@loop // コンパイルエラー
        //
        //				// return // コンパイルエラー
        //
        //				return@methodNotInline x * x
        //			}
        //			println("testReturn B:$x")
        //
        //			// インライン関数の引数として定義したラムダ式
        //			methodInline { x : Int ->
        //				break // コンパイルエラー
        //				break@loop // コンパイルエラー
        //
        //				return 10 // できる
        //
        //				return@methodInline 10 // できる
        //			}
        //		}
    }

    private fun <A, B> A.letNotInline(code: (A) -> B): B {
        return code(this)
    }

    @Test
    fun testInline0() {
        var result: Int
        val n = 11
        for (i in 1..10) {
            println(n.letNotInline { v ->
                val rv = v * i
                result = rv
                rv
            })
        }
    }

    @Test
    fun testInline1() {
        var result: Int
        val n = 12
        for (i in 1..10) {
            println(n.let { v ->
                val rv = v * i
                result = rv
                rv
            })
        }
    }

    @Test
    fun testInline2() {
        var result: Int
        val n = 13
        for (i in 1..10) {
            val rv = n * i
            result = rv
            println(rv)
        }
    }

    @Test
    fun testRawArray() {
        // サイズを指定して生成
        val a = IntArray(4)
        for (i in a.indices) {
            a[i] = i * 2
        }
        println(a.joinToString(","))

        // サイズと初期化ラムダを指定して生成
        val b = IntArray(4) { index -> index * 3 }
        println(b.joinToString(","))

        // 可変長引数で初期化するライブラリ関数
        val b2 = intArrayOf(0, 1, 2, 3)

        // 参照型の配列だと初期化ラムダが必須
        val c = Array<CharSequence>(4) { (it * 4).toString() }
        println(c.joinToString(","))
        val d = Array<CharSequence?>(4) { if (it % 2 == 0) null else (it * 5).toString() }
        println(d.joinToString(","))

        // ラムダ式の戻り値の型から配列の型パラメータが推測される
        val e = Array(4) { if (it % 2 == 0) null else (it * 6).toString() }
        println(e.joinToString(","))

        // 可変長引数で初期化するライブラリ関数
        val e2 = arrayOf(null, 1, null, 2)
    }

    @Test
    fun testOutProjectedType() {
        fun foo(args: Array<out Number>) {
            val sb = StringBuilder()
            for (s in args) {
                if (sb.isNotEmpty()) sb.append(',')
                sb
                    .append(s.toString())
                    .append('#')
                    .append(s.javaClass.simpleName)
            }
            println(sb)
            println(args.contains(6)) // 禁止されていない。inポジションって何だ…？
            // args[0]=6 //禁止されている
        }
        foo(arrayOf(1, 2, 3))
        foo(arrayOf(1f, 2f, 3f))
    }
}
