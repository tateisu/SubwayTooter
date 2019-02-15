package jp.juggler.subwaytooter;

import android.content.Context;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

// Android instrumentation test は run configuration を編集しないと Empty tests とかいうエラーになります

@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
	@Test
	public void useAppContext() throws Exception{
		// Context of the app under test.
		Context appContext = InstrumentationRegistry.getTargetContext();

		assertEquals( "jp.juggler.subwaytooter", appContext.getPackageName() );
	}
}
