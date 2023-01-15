package jp.juggler.base

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.juggler.base.JugglerBase.Companion.jugglerBase
import jp.juggler.base.JugglerBase.Companion.jugglerBaseNullable
import jp.juggler.base.JugglerBase.Companion.prepareJugglerBase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class JugglerBaseTest {
    @Test
    fun initializeJubblerBase() {
        assertNotNull("JubblerBase is initialized for a test.", jugglerBaseNullable)
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        appContext.prepareJugglerBase
        assertNotNull("JubblerBase is initialized after prepare.", jugglerBase)
    }
}
