package jp.juggler.subwaytooter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import jp.juggler.subwaytooter.pref.FILE_PROVIDER_AUTHORITY
import jp.juggler.util.*
import jp.juggler.util.data.digestSHA256Hex
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.atomic.AtomicReference

class ActCallback : AppCompatActivity() {

    companion object {
        private val log = LogCategory("ActCallback")

        internal val lastUri = AtomicReference<Uri>(null)
        internal val sharedIntent = AtomicReference<Intent>(null)

        private fun String?.isMediaMimeType() = when {
            this == null -> false
            this.startsWith("image/") -> true
            this.startsWith("video/") -> true
            this.startsWith("audio/") -> true
            else -> false
        }

        @Volatile
        private var uriFromApp: Uri? = null

        fun setUriFromApp(uri: Uri?) {
            synchronized(this) {
                uriFromApp = uri
            }
        }

        fun containsUriFromApp(uri: Uri?) =
            synchronized(this) {
                uri == uriFromApp
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        App1.setActivityTheme(this)

        var intent = this.intent
        log.d("onCreate flags=0x${intent?.flags?.toString(radix = 16)}")
        super.onCreate(savedInstanceState)

        when {
            intent == null -> {
                // 多分起きないと思う
            }

            (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0 -> {
                // 履歴から開いた場合はIntentの中味を読まない
            }

            else -> {
                val action = intent.action
                val type = intent.type
                // ACTION_SEND か ACTION_SEND_MULTIPLE
                // ACTION_VIEW かつ  type が 画像かビデオか音声
                when {
                    Intent.ACTION_SEND == action ||
                            Intent.ACTION_SEND_MULTIPLE == action ||
                            (Intent.ACTION_VIEW == action && type.isMediaMimeType()) -> {

                        // Google Photo などから送られるIntentに含まれるuriの有効期間はActivityが閉じられるまで
                        // http://qiita.com/pside/items/a821e2fe9ae6b7c1a98c

                        // 有効期間を延長する
                        intent = remake(intent)
                        if (intent != null) {
                            sharedIntent.set(intent)
                        }
                    }

                    forbidUriFromApp(intent) -> {
                        // last_uriをクリアする
                        lastUri.set(null)
                        // ダイアログを閉じるまで画面遷移しない
                        return
                    }

                    else -> {
                        val uri = intent.data
                        if (uri != null) {
                            lastUri.set(uri)
                        }
                    }
                }
            }
        }

        // どうであれメイン画面に戻る
        afterDispatch()
    }

    private fun afterDispatch() {
        val intent = Intent(this, ActMain::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }

    private fun copyExtraTexts(dst: Intent, src: Intent) {
        src.string(Intent.EXTRA_TEXT)
            ?.let { dst.putExtra(Intent.EXTRA_TEXT, it) }
        //
        src.string(Intent.EXTRA_SUBJECT)
            ?.let { dst.putExtra(Intent.EXTRA_SUBJECT, it) }
    }

    private fun remake(src: Intent): Intent? {

        sweepOldCache()

        try {
            val action = src.action
            val type = src.type

            if (type.isMediaMimeType()) {
                when (action) {
                    Intent.ACTION_VIEW -> {
                        src.data?.let { uriOriginal ->
                            try {
                                val uri = saveToCache(uriOriginal)
                                val dst = Intent(action)
                                dst.setDataAndType(uri, type)
                                copyExtraTexts(dst, src)
                                return dst
                            } catch (ex: Throwable) {
                                log.e(ex, "remake failed. src=$src")
                            }
                        }
                    }

                    Intent.ACTION_SEND -> {
                        var uri = src.getStreamUriExtra()
                            ?: return src // text/plainの場合
                        try {
                            uri = saveToCache(uri)

                            val dst = Intent(action)
                            dst.type = type
                            dst.putExtra(Intent.EXTRA_STREAM, uri)
                            copyExtraTexts(dst, src)
                            return dst
                        } catch (ex: Throwable) {
                            log.e(ex, "remake failed. src=$src")
                        }
                    }

                    Intent.ACTION_SEND_MULTIPLE -> {
                        val listUri = src.getStreamUriListExtra()
                            ?: return null
                        val listDst = ArrayList<Uri>()
                        for (uriOriginal in listUri) {
                            if (uriOriginal != null) {
                                try {
                                    val uri = saveToCache(uriOriginal)
                                    listDst.add(uri)
                                } catch (ex: Throwable) {
                                    log.e(ex, "remake failed. src=$src")
                                }
                            }
                        }
                        if (listDst.isEmpty()) return null
                        val dst = Intent(action)
                        dst.type = type
                        dst.putParcelableArrayListExtra(Intent.EXTRA_STREAM, listDst)
                        copyExtraTexts(dst, src)
                        return dst
                    }
                }
            } else if (Intent.ACTION_SEND == action) {

                // Swarmアプリから送られたインテントは getType()==null だが EXTRA_TEXT は含まれている
                // EXTRA_TEXT の存在を確認してからtypeがnullもしくは text/plain なら受け取る

                val sv = src.string(Intent.EXTRA_TEXT)
                if (sv?.isNotEmpty() == true && (type == null || type.startsWith("text/"))) {
                    val dst = Intent(action)
                    dst.type = "text/plain"
                    copyExtraTexts(dst, src)

                    return dst
                }
            }
        } catch (ex: Throwable) {
            log.e(ex, "remake failed. src=$src")
        }

        return null
    }

    @Throws(Throwable::class)
    private fun saveToCache(uri: Uri): Uri {

        // prepare cache directory
        val cacheDir = this.cacheDir

        cacheDir.mkdirs()

        val name =
            "img." + System.currentTimeMillis().toString() + "." + uri.toString().digestSHA256Hex()

        val dst = File(cacheDir, name)

        FileOutputStream(dst).use { outStream ->
            val source = contentResolver.openInputStream(uri)
                ?: error("getContentResolver.openInputStream returns null.")
            source.use { inStream ->
                inStream.copyTo(outStream)
            }
        }
        return FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, dst)
    }

    private fun sweepOldCache() {
        // sweep old cache
        try {
            // prepare cache directory
            val cacheDir = this.cacheDir

            cacheDir.mkdirs()

            val now = System.currentTimeMillis()
            val files = cacheDir.listFiles()
            if (files != null) for (f in files) {
                try {
                    if (f.isFile &&
                        f.name.startsWith("img.") &&
                        now - f.lastModified() >= 86400000L
                    ) {
                        f.delete()
                    }
                } catch (ex: Throwable) {
                    log.e(ex, "sweepOldCache: delete item failed.")
                }
            }
        } catch (ex: Throwable) {
            log.e(ex, "sweepOldCache failed.")
        }
    }

    // return true if open app chooser dialog
    private fun forbidUriFromApp(src: Intent): Boolean {
        if (src.action != Intent.ACTION_VIEW) return false

        val uri = src.data ?: return false
        if (!containsUriFromApp(uri)) return false
        setUriFromApp(null)

        try {
            startActivity(Intent.createChooser(src, uri.toString()))
            finish()
        } catch (ex: Throwable) {
            showToast(ex, "can't open chooser for $uri")
        }
        return true
    }
}
