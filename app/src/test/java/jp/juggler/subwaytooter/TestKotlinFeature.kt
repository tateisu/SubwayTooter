@file:Suppress(
	"USELESS_CAST", "unused", "DEPRECATED_IDENTITY_EQUALS", "UNUSED_VARIABLE",
	"UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER",
	"ReplaceCallWithComparison"
)

package jp.juggler.subwaytooter

import android.view.View
import org.junit.Test

import org.junit.Assert.*
import kotlin.concurrent.thread

//import kotlin.test.*

typealias TestLambdaCallback = (x : Int) -> Int

class TestKotlinFeature {
	
	private val CODE_A = 1
	private val CODE_A2 = 2
	
	@Test
	fun testWhenExpression() {
		
		// ifを式として扱えるように、whenも式として扱える
		run {
			val code = 2
			val s = "prefix:" + when(code) {
				CODE_A, CODE_A2 -> "a"
				else -> "b"
			} + ":suffix"
			
			assertEquals("prefix:a:suffix", s)
		}
		
		// tryも式として扱える
		run {
			val p = try {
				if(stringNull.isNullOrEmpty()) throw RuntimeException("foo")
				true
			} catch(ex : RuntimeException) {
				false
			}
			assertEquals(false, p)
		}
	}
	
	private val stringNull : String? = null
	private val stringEmpty : String? = ""
	private val stringBlank : String? = "    "
	private val stringABC : String? = "ABC"
	
	@Test
	fun testNullString() {
		
		// Nullable型には値がnullでも呼び出せるメソッドがある
		assertEquals(true, stringNull.isNullOrEmpty())
		assertEquals(true, stringEmpty.isNullOrEmpty())
		assertEquals(false, stringBlank.isNullOrEmpty())
		assertEquals(false, stringABC.isNullOrEmpty())
		
		assertEquals(true, stringNull.isNullOrBlank())
		assertEquals(true, stringEmpty.isNullOrBlank())
		assertEquals(true, stringBlank.isNullOrBlank())
		assertEquals(false, stringABC.isNullOrBlank())
		
		// ?. 演算子の後に プリミティブ型を返すメソッドを呼び出して、そのままifの条件式に指定する
		// 「stringNull?.isNotEmpty()」の型はBoolean? なので trueと比較できるが、 ==true を省略することはできない
		// true,false,null の3値論理になるので、条件を反転させたい場合は == false と != true で結果が異なる
		assertEquals(0, if(stringNull?.isNotEmpty() == true) 1 else 0)
		assertEquals(0, if(stringEmpty?.isNotEmpty() == true) 1 else 0)
		assertEquals(1, if(stringBlank?.isNotEmpty() == true) 1 else 0)
		assertEquals(1, if(stringABC?.isNotEmpty() == true) 1 else 0)
		
		// isNotBlank で空白のみを含む文字列をfalseにできる
		assertEquals(0, if(stringNull?.isNotBlank() == true) 1 else 0)
		assertEquals(0, if(stringEmpty?.isNotBlank() == true) 1 else 0)
		assertEquals(0, if(stringBlank?.isNotBlank() == true) 1 else 0)
		assertEquals(1, if(stringABC?.isNotBlank() == true) 1 else 0)
		
		if(! stringABC.isNullOrEmpty()) {
			// このブロック内部で string がまだNullableとして扱われるのが残念極まりない
		}
	}
	
	// コンパイラに予測できない方法でIntの10を生成する
	private fun generate10A() : Int {
		return ("1" + "0").toInt()
		
	}
	
	// コンパイラに予測できない方法でIntの10を生成する
	private fun generate10B() : Int {
		var i = 0
		while(true) {
			++ i
			if(i % 5 == 0 && i % 2 == 0) return i
		}
	}
	
	@Test
	fun testOperator() {
		// is 演算子と !is 演算子
		open class A(val name : String = "nanasi")
		
		class B(name : String = "nanasi", val count : Int = 0) : A(name)
		class C
		
		val b = B("b", 10)
		val c = C()
		assertEquals(true, b as Any is A)
		assertEquals(true, c as Any !is A)
		
		// in 演算子と !in 演算子
		val range1 = 0 .. 10
		val range2 = 0 until 10 // 10を含まない。 until は言語機能ではなく infix関数
		assertEquals(true, 10 in range1)
		assertEquals(true, 10 !in range2)
		
		// ==,=== 演算子とプリミティブ型
		val long10 = 10L
		val int10 = 10
		val int10b : Int = generate10A()
		val int10c : Int = generate10B()
		
		// IntとLongを直接比較しようとするとコンパイルエラーになる
		// assertEquals( true, int10 == long10 )
		
		// 型が分からないようにするとビルドできるが、intの10とlongの10は異なると判断される
		// Int.equals も同じ結果
		assertEquals(false, int10 as Any == long10 as Any)
		assertEquals(false, int10.equals(long10))
		
		// Long.equals でも同じ結果
		assertEquals(false, long10 as Any == int10 as Any)
		assertEquals(false, long10.equals(int10))
		
		// 同じ型に変換すると数値が同じだと判定できる
		assertEquals(true, int10.toLong() == long10)
		assertEquals(true, int10.toLong().equals(long10))
		
		// === 演算子 とプリミティブ型
		
		// 型が違うとコンパイルエラー
		// assertEquals( true, int10 === long10 )
		
		// 型が同じでも記述した時点で deprecatedという警告が出る。
		// クラスファイルを読むと if_icmpne を使って数値比較していた。equalsではない
		assertEquals(true, int10 === int10b)
		assertEquals(true, int10 === int10c)
		assertEquals(true, int10 === 10)
		assertEquals(true, int10 === generate10A())
		
		// Any型に変換して比較。警告はでない
		// Anyへのキャストは java/lang/Integer.valueOf でboxingしてから checkcast java/lang/Objectして if_acmpne していた
		assertEquals(true, int10 as Any === int10b as Any)
		assertEquals(true, int10 as Any === int10c as Any)
		assertEquals(true, int10 as Any === 10 as Any)
		assertEquals(true, int10 as Any === generate10A() as Any)
		
		// valueOfは-128..127は内部でキャッシュを行うから、値によっては同じアドレスだったり異なったりする
		var intA = 0
		var intB = 0
		for(i in 126 .. 127) {
			println("i=$i")
			intA = i
			intB = i
			assertEquals(true, intA as Any === intB as Any)
		}
		for(i in 128 .. 130) {
			println("i=$i")
			intA = i
			intB = i
			assertEquals(false, intA as Any === intB as Any)
		}
		
		/*
			蛇足だが、クラスファイルを読むのは
			app/build/tmp/kotlin-classes/*UnitTest\**/TestKotlinFeature.class を
			javap.exe  -c TestKotlinFeature.class > javap.log とすると逆アセンブルできる
		 */
		
	}
	
	@Test
	fun testNullableIf() {
		
		// Boolean? は true false null の3値論理となる
		// stringNull?.isNotEmpty() など、 ?.演算子を使うと Nullable型が発生するシチュエーションがよくある
		
		val nullableTrue : Boolean? = true
		val nullableFalse : Boolean? = false
		val nullableNull : Boolean? = null
		
		// ifの条件式部分は Boolean? の値を直接扱うことはできない。コンパイルエラーとなる
		//	assertEquals( 1 , if( nullableTrue ) 1 else 0 )
		//	assertEquals( 1 , if( nullableFalse ) 1 else 0 )
		//	assertEquals( 1 , if( nullableNull ) 1 else 0 )
		
		// == != 演算子はnullを取り扱える
		assertEquals(1, if(nullableTrue == true) 1 else 0)
		assertEquals(0, if(nullableTrue == false) 1 else 0)
		assertEquals(0, if(nullableTrue == null) 1 else 0)
		assertEquals(0, if(nullableTrue != true) 1 else 0)
		assertEquals(1, if(nullableTrue != false) 1 else 0)
		assertEquals(1, if(nullableTrue != null) 1 else 0)
		
		assertEquals(0, if(nullableFalse == true) 1 else 0)
		assertEquals(1, if(nullableFalse == false) 1 else 0)
		assertEquals(0, if(nullableFalse == null) 1 else 0)
		assertEquals(1, if(nullableFalse != true) 1 else 0)
		assertEquals(0, if(nullableFalse != false) 1 else 0)
		assertEquals(1, if(nullableFalse != null) 1 else 0)
		
		assertEquals(0, if(nullableNull == true) 1 else 0)
		assertEquals(0, if(nullableNull == false) 1 else 0)
		assertEquals(1, if(nullableNull == null) 1 else 0)
		assertEquals(1, if(nullableNull != true) 1 else 0)
		assertEquals(1, if(nullableNull != false) 1 else 0)
		assertEquals(0, if(nullableNull != null) 1 else 0)
	}
	
	interface MyKotlinInterface {
		fun method(x : Int) : Int
	}
	
	@Test
	fun testSAM() {
		
		// 定義例(文脈あり)
		Thread({ println("SAM 1") }).start()
		Thread { println("SAM 2") }.start()
		
		// 定義例(文脈不明)
		val a = Runnable({ println("SAM a") })
		
		// 参照型の定義
		val ref : Runnable = a
		
		// 参照型の呼び出し
		ref.run()
		
		// Nullableな参照型の定義
		val refNullable : Runnable? = a
		if(refNullable != null) {
			// 呼び出し
			Thread(refNullable).start()
		}
		
		View.OnClickListener { _ ->
			println("clicked")
		}.onClick(null)
		
		// kotlinで定義したインタフェースに対してSAMコンストラクタを使えるか？
		// ダメでした
		//		val ki = MyKotlinInterface{
		//			it * it
		//		}
	}
	
	@Test
	fun testLambda() {
		// 定義例(文脈あり)
		thread(start = true) { println("testLambda") }
		println(10.let { x -> x * x })
		10.let { println(it) }
		// 定義例(文脈不明)
		val a = { println("testLambda") }
		
		// 参照型の定義
		val ref : (x : Int) -> Int = { it * it }
		
		// 参照型の呼び出し
		println(ref(10))
		
		// 参照型の定義(Nullable)
		val refNullable : TestLambdaCallback? = { it * it }
		if(refNullable != null) {
			refNullable(10)
		}
		
	}
	
	@Test
	fun testAnonymousFunction() {
		// 定義例(文脈あり)
		thread(start = true, block = fun() { println("testAnonymousFunction") })
		println(10.let(fun(x : Int) = x * x))
		10.let { println(it) }
		// 定義例(文脈不明)
		val a = fun(x : Int) = x * x
		
		// 参照型の定義
		val ref : (x : Int) -> Int = a
		
		// 参照型の呼び出し
		println(ref(10))
		
		// 参照型の定義(Nullable)
		val refNullable : TestLambdaCallback? = fun(i : Int) = i * i
		if(refNullable != null) {
			refNullable(10)
		}
	}
	
	@Test
	fun testObjectExpression() {
		// 定義例(文脈あり)
		abstract class Base {
			
			abstract fun method(x : Int) : Int
		}
		
		val a = object : Base() {
			override fun method(x : Int) : Int {
				return x * x
			}
		}
		
		// 定義例(文脈の有無で変化しない)
		// 参照型の定義
		val ref : Base = a
		
		// 参照型の定義(Nullable)
		val refNullable : Base? = a
		if(refNullable != null) {
			val v = refNullable.method(10)
			println("OE v=$v")
		}
		
		
		fun caller(b : Base) {
			val v = b.method(10)
			println("OE b $v")
		}
		caller(object : Base() {
			override fun method(x : Int) : Int {
				return x * x * x
			}
			
		})
	}
	
	private fun member(x : Int) = x * x
	@Test
	fun testMemberReference() {
		fun caller(a : (receiver : TestKotlinFeature, x : Int) -> Int) {
			val v = a(this, 10)
			println("testMemberReference caller $v")
		}
		caller(TestKotlinFeature::member)
		
		val b = TestKotlinFeature::member
		val a : (receiver : TestKotlinFeature, x : Int) -> Int = TestKotlinFeature::member
	}
	
	fun methodNotInline(callback : (x : Int) -> Int) : Int {
		return callback(3)
	}
	
	inline fun methodInline(callback : (x : Int) -> Int) : Int {
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
	
	private fun <A, B> A.letNotInline(code : (A) -> B) : B {
		return code(this)
	}
	
	@Test
	fun testInline0() {
		var result : Int
		val n = 11
		for(i in 1 .. 10) {
			println(n.letNotInline { v ->
				val rv = v * i
				result = rv
				rv
			})
		}
	}
	
	@Test
	fun testInline1() {
		var result : Int
		val n = 12
		for(i in 1 .. 10) {
			println(n.let { v ->
				val rv = v * i
				result = rv
				rv
			})
		}
	}
	
	@Test
	fun testInline2() {
		var result : Int
		val n = 13
		for(i in 1 .. 10) {
			val rv = n * i
			result = rv
			println(rv)
		}
	}
	
	@Test
	fun testRawArray() {
		// サイズを指定して生成
		val a = IntArray(4)
		for(i in 0 until a.size) {
			a[i] = i * 2
		}
		println(a.joinToString(","))
		
		// サイズと初期化ラムダを指定して生成
		val b = IntArray(4) { index -> index * 3 }
		println(b.joinToString(","))
		
		// 可変長引数で初期化するライブラリ関数
		var b2 = intArrayOf(0, 1, 2, 3)
		
		// 参照型の配列だと初期化ラムダが必須
		val c = Array<CharSequence>(4) { (it * 4).toString() }
		println(c.joinToString(","))
		val d = Array<CharSequence?>(4) { if(it % 2 == 0) null else (it * 5).toString() }
		println(d.joinToString(","))
		
		// ラムダ式の戻り値の型から配列の型パラメータが推測される
		val e = Array(4) { if(it % 2 == 0) null else (it * 6).toString() }
		println(e.joinToString(","))
		
		// 可変長引数で初期化するライブラリ関数
		var e2 = arrayOf(null, 1, null, 2)
		
	}
	
	@Test
	fun testOutProjectedType() {
		fun foo(args : Array<out Number>) {
			val sb = StringBuilder()
			for(s in args) {
				if(sb.isNotEmpty()) sb.append(',')
				sb
					.append(s.toString())
					.append('#')
					.append(s.javaClass.simpleName)
				
			}
			println(sb)
			println(args.contains(6)) // 禁止されていない。inポジションって何だ…？
			// arggs[0]=6 //禁止されている
		}
		foo(arrayOf(1, 2, 3))
		foo(arrayOf(1f, 2f, 3f))
	}
}
