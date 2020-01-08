package jp.juggler.subwaytooter

import jp.juggler.util.jsonArray
import jp.juggler.util.jsonObject
import jp.juggler.util.decodeJsonValue
import jp.juggler.util.writeJsonValue
import org.junit.Test
import org.junit.Assert.*
import java.io.StringWriter

class TestJson {
	companion object{
		fun Any?.encodeSimpleJsonValue(indentFactor : Int):String{
			val sw = StringWriter()
			synchronized(sw.buffer) {
				return sw.writeJsonValue(indentFactor, 0, this).toString()
			}
		}
	}
	
	enum class En{
		Aa,
		Bb,
		Cc
	}
	
	@Test
	fun encode1() {
		assertEquals("null",null.encodeSimpleJsonValue(0))
		assertEquals("true",true.encodeSimpleJsonValue(0))
		assertEquals("false",false.encodeSimpleJsonValue(0))

		assertEquals("0",0.encodeSimpleJsonValue(0))
		assertEquals("1",1.encodeSimpleJsonValue(0))
		assertEquals("-1",(-1).encodeSimpleJsonValue(0))
		assertEquals("-2147483648",Int.MIN_VALUE.encodeSimpleJsonValue(0))
		assertEquals("2147483647",Int.MAX_VALUE.encodeSimpleJsonValue(0))
		
		assertEquals("0",0L.encodeSimpleJsonValue(0))
		assertEquals("1",1L.encodeSimpleJsonValue(0))
		assertEquals("-1",(-1L).encodeSimpleJsonValue(0))
		assertEquals("-9223372036854775808",Long.MIN_VALUE.encodeSimpleJsonValue(0))
		assertEquals("9223372036854775807",Long.MAX_VALUE.encodeSimpleJsonValue(0))
		
		assertEquals("0",0f.encodeSimpleJsonValue(0))
		assertEquals("1",1f.encodeSimpleJsonValue(0))
		assertEquals("-1",(-1f).encodeSimpleJsonValue(0))
		assertEquals("1.4E-45",Float.MIN_VALUE.encodeSimpleJsonValue(0))
		assertEquals("3.4028235E38",Float.MAX_VALUE.encodeSimpleJsonValue(0))
		
		assertEquals("0",0.0.encodeSimpleJsonValue(0))
		assertEquals("1",1.0.encodeSimpleJsonValue(0))
		assertEquals("-1",(-1.0).encodeSimpleJsonValue(0))
		assertEquals("4.9E-324",Double.MIN_VALUE.encodeSimpleJsonValue(0))
		assertEquals("1.7976931348623157E308",Double.MAX_VALUE.encodeSimpleJsonValue(0))
		
		assertEquals("0",0.toChar().encodeSimpleJsonValue(0))
		assertEquals("1",1.toChar().encodeSimpleJsonValue(0))
		assertEquals("65535",(-1).toChar().encodeSimpleJsonValue(0))
		assertEquals("0",Char.MIN_VALUE.encodeSimpleJsonValue(0))
		assertEquals("65535",Char.MAX_VALUE.encodeSimpleJsonValue(0))
		
		// 空文字列
		assertEquals("\"\"","".encodeSimpleJsonValue(0))
		
		val escaped = ArrayList<String>()
		// Unicode1文字(エスケープのテスト
		for( i in 0 until 0x10000){
			val c = i.toChar()
			val raw = c.toString()
			val encoded = raw.encodeSimpleJsonValue(0)
			val decoded = encoded.decodeJsonValue()
			assertEquals(raw,decoded)
			if(encoded.length > 3) escaped.add(encoded.substring(1,encoded.length-1))
		}
		assertEquals(
			"""\",\\,\b,\f,\n,\r,\t,\u0000,\u0001,\u0002,\u0003,\u0004,\u0005,\u0006,\u0007,\u000b,\u000e,\u000f,\u0010,\u0011,\u0012,\u0013,\u0014,\u0015,\u0016,\u0017,\u0018,\u0019,\u001a,\u001b,\u001c,\u001d,\u001e,\u001f,\u0080,\u0081,\u0082,\u0083,\u0084,\u0085,\u0086,\u0087,\u0088,\u0089,\u008a,\u008b,\u008c,\u008d,\u008e,\u008f,\u0090,\u0091,\u0092,\u0093,\u0094,\u0095,\u0096,\u0097,\u0098,\u0099,\u009a,\u009b,\u009c,\u009d,\u009e,\u009f,\u2000,\u2001,\u2002,\u2003,\u2004,\u2005,\u2006,\u2007,\u2008,\u2009,\u200a,\u200b,\u200c,\u200d,\u200e,\u200f,\u2010,\u2011,\u2012,\u2013,\u2014,\u2015,\u2016,\u2017,\u2018,\u2019,\u201a,\u201b,\u201c,\u201d,\u201e,\u201f,\u2020,\u2021,\u2022,\u2023,\u2024,\u2025,\u2026,\u2027,\u2028,\u2029,\u202a,\u202b,\u202c,\u202d,\u202e,\u202f,\u2030,\u2031,\u2032,\u2033,\u2034,\u2035,\u2036,\u2037,\u2038,\u2039,\u203a,\u203b,\u203c,\u203d,\u203e,\u203f,\u2040,\u2041,\u2042,\u2043,\u2044,\u2045,\u2046,\u2047,\u2048,\u2049,\u204a,\u204b,\u204c,\u204d,\u204e,\u204f,\u2050,\u2051,\u2052,\u2053,\u2054,\u2055,\u2056,\u2057,\u2058,\u2059,\u205a,\u205b,\u205c,\u205d,\u205e,\u205f,\u2060,\u2061,\u2062,\u2063,\u2064,\u2065,\u2066,\u2067,\u2068,\u2069,\u206a,\u206b,\u206c,\u206d,\u206e,\u206f,\u2070,\u2071,\u2072,\u2073,\u2074,\u2075,\u2076,\u2077,\u2078,\u2079,\u207a,\u207b,\u207c,\u207d,\u207e,\u207f,\u2080,\u2081,\u2082,\u2083,\u2084,\u2085,\u2086,\u2087,\u2088,\u2089,\u208a,\u208b,\u208c,\u208d,\u208e,\u208f,\u2090,\u2091,\u2092,\u2093,\u2094,\u2095,\u2096,\u2097,\u2098,\u2099,\u209a,\u209b,\u209c,\u209d,\u209e,\u209f,\u20a0,\u20a1,\u20a2,\u20a3,\u20a4,\u20a5,\u20a6,\u20a7,\u20a8,\u20a9,\u20aa,\u20ab,\u20ac,\u20ad,\u20ae,\u20af,\u20b0,\u20b1,\u20b2,\u20b3,\u20b4,\u20b5,\u20b6,\u20b7,\u20b8,\u20b9,\u20ba,\u20bb,\u20bc,\u20bd,\u20be,\u20bf,\u20c0,\u20c1,\u20c2,\u20c3,\u20c4,\u20c5,\u20c6,\u20c7,\u20c8,\u20c9,\u20ca,\u20cb,\u20cc,\u20cd,\u20ce,\u20cf,\u20d0,\u20d1,\u20d2,\u20d3,\u20d4,\u20d5,\u20d6,\u20d7,\u20d8,\u20d9,\u20da,\u20db,\u20dc,\u20dd,\u20de,\u20df,\u20e0,\u20e1,\u20e2,\u20e3,\u20e4,\u20e5,\u20e6,\u20e7,\u20e8,\u20e9,\u20ea,\u20eb,\u20ec,\u20ed,\u20ee,\u20ef,\u20f0,\u20f1,\u20f2,\u20f3,\u20f4,\u20f5,\u20f6,\u20f7,\u20f8,\u20f9,\u20fa,\u20fb,\u20fc,\u20fd,\u20fe,\u20ff""",
			escaped.sorted().joinToString(",")
		)
		
		// enum
		assertEquals("\"Aa\"",En.Aa.encodeSimpleJsonValue(0))

		// object
		val o = jsonObject{}
		assertEquals("{}",o.encodeSimpleJsonValue(0))
		o["a"]="b"
		assertEquals("{\"a\":\"b\"}",o.encodeSimpleJsonValue(0))
		
		// Collection
		val a = jsonArray{}
		assertEquals("[]",a.encodeSimpleJsonValue(0))
		a.add("b")
		assertEquals("[\"b\"]",a.encodeSimpleJsonValue(0))
		a[0]="c"
		assertEquals("[\"c\"]",a.encodeSimpleJsonValue(0))
		
	}
	
}