package jp.juggler.subwaytooter

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.util.data.decodeUTF8
import jp.juggler.util.data.loadRawResource
import jp.juggler.util.log.LogCategory

class ActOSSLicense : AppCompatActivity() {

    companion object {
        private val log = LogCategory("ActOSSLicense")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App1.setActivityTheme(this, noActionBar = true)
        setContentView(R.layout.act_oss_license)
        App1.initEdgeToEdge(this)

        try {
            findViewById<TextView>(R.id.tvText)
                ?.text = loadRawResource(R.raw.oss_license).decodeUTF8()
        } catch (ex: Throwable) {
            log.e(ex, "can't show license text.")
        }
    }
}
