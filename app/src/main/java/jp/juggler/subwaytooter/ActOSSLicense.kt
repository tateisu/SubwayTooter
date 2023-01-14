package jp.juggler.subwaytooter

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.databinding.ActOssLicenseBinding
import jp.juggler.util.data.decodeUTF8
import jp.juggler.util.data.loadRawResource
import jp.juggler.util.log.LogCategory
import jp.juggler.util.ui.setNavigationBack

class ActOSSLicense : AppCompatActivity() {

    companion object {
        private val log = LogCategory("ActOSSLicense")
    }

    private val views by lazy {
        ActOssLicenseBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App1.setActivityTheme(this)
        setContentView(views.root)
        setSupportActionBar(views.toolbar)
        setNavigationBack(views.toolbar)
        fixHorizontalMargin(views.svContent)

        try {
            views.tvText.text = loadRawResource(R.raw.oss_license).decodeUTF8()
        } catch (ex: Throwable) {
            log.e(ex, "can't show license text.")
        }
    }
}
