@file:Suppress(
	"USELESS_CAST", "unused", "DEPRECATED_IDENTITY_EQUALS", "UNUSED_VARIABLE",
	"UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER",
	"ReplaceCallWithComparison"
)

package jp.juggler.subwaytooter

import jp.juggler.subwaytooter.util.VersionString
import org.junit.Test

import org.junit.Assert.*

class TestVersionString {
	@Test
	fun test1() {
		val v233 = VersionString("2.3.3")
		val v240rc0 = VersionString("2.4.0rc")
		val v240rc1 = VersionString("2.4.0rc1")
		val v240rc2 = VersionString("2.4.0rc2")
		val v240 = VersionString("2.4.0")
		val v240xxx = VersionString("2.4.0xxx")
		val v241 = VersionString("2.4.1")
		
		assertTrue(v233 < v240rc0)
		assertTrue(v240rc0 < v240rc1)
		assertTrue(v240rc1 < v240rc2)
		assertTrue(v240rc2 < v240)
		assertTrue(v240 < v240xxx)
		assertTrue(v240xxx < v241)
		
		assertTrue(v240rc0 > v233)
		assertTrue(v240rc1 > v240rc0)
		assertTrue(v240rc2 > v240rc1)
		assertTrue(v240 > v240rc2)
		assertTrue(v240xxx > v240)
		assertTrue(v241 > v240xxx)
		
		assertTrue(0 == v233.compareTo(v233))
		assertTrue(0 == v240rc0.compareTo(v240rc0))
		assertTrue(0 == v240rc1.compareTo(v240rc1))
		assertTrue(0 == v240rc2.compareTo(v240rc2))
		assertTrue(0 == v240.compareTo(v240))
		assertTrue(0 == v240xxx.compareTo(v240xxx))
		assertTrue(0 == v241.compareTo(v241))
	}
	
	@Test
	fun test2() {
		val v240 = VersionString("2.4.0")
		val v240xxx = VersionString("2.4.0xxx")
		assertTrue(v240 < v240xxx)
	}
}
