package jp.juggler.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compat.kt の val Thread.idCompat のテスト
 * - API 26 以上のエミュで動作確認する
 */
@RunWith(AndroidJUnit4::class)
class ThreadIdCompatTest {
    @Test
    fun threadIdCompatTest() {
        val result = runCatching {
            Thread.currentThread().idCompat
        }
        assertNull(
            "not raise exception.",
            result.exceptionOrNull(),

            )
        assertNotNull(
            "got thread id.",
            result.getOrNull(),
        )
    }
}
