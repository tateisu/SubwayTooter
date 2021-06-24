package jp.juggler.subwaytooter

import android.net.Uri
import android.os.Process
import android.util.JsonReader
import android.view.WindowManager
import androidx.annotation.WorkerThread
import jp.juggler.subwaytooter.notification.PollingWorker
import jp.juggler.util.*
import kotlinx.coroutines.delay
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.ArrayList
import java.util.zip.ZipInputStream

private val log = LogCategory("ActMainImportAppData")

@WorkerThread
fun ActMain.importAppData(uri: Uri) {
    launchMain {

        // remove all columns
        phoneOnly { env -> env.pager.adapter = null }

        appState.editColumnList(save = false) { list ->
            list.forEach { it.dispose() }
            list.clear()
        }

        phoneTab(
            { env -> env.pager.adapter = env.pagerAdapter },
            { env -> resizeColumnWidth(env) }
        )

        updateColumnStrip()

        runWithProgress(
            "importing app data",

            doInBackground = { progress ->
                fun setProgressMessage(sv: String) =
                    runOnMainLooper { progress.setMessageEx(sv) }

                var newColumnList: ArrayList<Column>? = null

                setProgressMessage("import data to local storage...")

                // アプリ内領域に一時ファイルを作ってコピーする
                val cacheDir = cacheDir
                cacheDir.mkdir()
                val file = File(
                    cacheDir,
                    "SubwayTooter.${Process.myPid()}.${Process.myTid()}.tmp"
                )
                val source = contentResolver.openInputStream(uri)
                if (source == null) {
                    showToast(true, "openInputStream failed.")
                    return@runWithProgress null
                }
                source.use { inStream ->
                    FileOutputStream(file).use { outStream ->
                        IOUtils.copy(inStream, outStream)
                    }
                }

                // 通知サービスを止める
                setProgressMessage("syncing notification poller…")
                PollingWorker.queueAppDataImportBefore(this@importAppData)
                while (PollingWorker.mBusyAppDataImportBefore.get()) {
                    delay(1000L)
                    log.d("syncing polling task...")
                }

                // データを読み込む
                setProgressMessage("reading app data...")
                var zipEntryCount = 0
                try {
                    ZipInputStream(FileInputStream(file)).use { zipStream ->
                        while (true) {
                            val entry = zipStream.nextEntry ?: break
                            ++zipEntryCount
                            try {
                                //
                                val entryName = entry.name
                                if (entryName.endsWith(".json")) {
                                    newColumnList = AppDataExporter.decodeAppData(
                                        this@importAppData,
                                        JsonReader(InputStreamReader(zipStream, "UTF-8"))
                                    )
                                    continue
                                }

                                if (AppDataExporter.restoreBackgroundImage(
                                        this@importAppData,
                                        newColumnList,
                                        zipStream,
                                        entryName
                                    )
                                ) {
                                    continue
                                }
                            } finally {
                                zipStream.closeEntry()
                            }
                        }
                    }
                } catch (ex: Throwable) {
                    log.trace(ex)
                    if (zipEntryCount != 0) {
                        showToast(ex, "importAppData failed.")
                    }
                }
                // zipではなかった場合、zipEntryがない状態になる。例外はPH-1では出なかったが、出ても問題ないようにする。
                if (zipEntryCount == 0) {
                    InputStreamReader(FileInputStream(file), "UTF-8").use { inStream ->
                        newColumnList = AppDataExporter.decodeAppData(
                            this@importAppData,
                            JsonReader(inStream)
                        )
                    }
                }

                newColumnList
            },
            afterProc = {
                // cancelled.
                if (it == null) return@runWithProgress

                try {
                    phoneOnly { env -> env.pager.adapter = null }

                    appState.editColumnList { list ->
                        list.clear()
                        list.addAll(it)
                    }

                    phoneTab(
                        { env -> env.pager.adapter = env.pagerAdapter },
                        { env -> resizeColumnWidth(env) }
                    )
                    updateColumnStrip()
                } finally {
                    // 通知サービスをリスタート
                    PollingWorker.queueAppDataImportAfter(this@importAppData)
                }

                showToast(true, R.string.import_completed_please_restart_app)
                finish()
            },
            preProc = {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            },
            postProc = {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        )
    }
}
