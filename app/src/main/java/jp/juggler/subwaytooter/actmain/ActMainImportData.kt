package jp.juggler.subwaytooter.actmain

import android.net.Uri
import android.os.Process
import android.util.JsonReader
import android.view.WindowManager
import androidx.annotation.WorkerThread
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.appsetting.AppDataExporter
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.notification.setImportProtector
import jp.juggler.util.coroutine.launchProgress
import jp.juggler.util.coroutine.runOnMainLooper
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

private val log = LogCategory("ActMainImportAppData")

@WorkerThread
fun ActMain.importAppData(uri: Uri) {

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

    launchProgress(
        "importing app data",
        doInBackground = { progress ->
            fun setProgressMessage(sv: String) = runOnMainLooper { progress.setMessageEx(sv) }

            setProgressMessage("import data to local storage...")

            // アプリ内領域に一時ファイルを作ってコピーする
            val cacheDir = cacheDir
            cacheDir.mkdir()
            val file = File(cacheDir, "SubwayTooter.${Process.myPid()}.${Process.myTid()}.tmp")
            val copyBytes = contentResolver.openInputStream(uri)?.use { inStream ->
                FileOutputStream(file).use { outStream ->
                    inStream.copyTo(outStream)
                }
            }
            if (copyBytes == null) {
                showToast(true, "openInputStream failed.")
                return@launchProgress null
            }

            // 通知サービスを止める
            setProgressMessage("syncing notification poller…")
            setImportProtector(this@importAppData, true)

            // データを読み込む
            setProgressMessage("reading app data...")
            var newColumnList: ArrayList<Column>? = null
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
                if (zipEntryCount != 0) {
                    log.e(ex, "importAppData failed.")
                    showToast(ex, "importAppData failed.")
                }
            }
            // zipではなかった場合、zipEntryがない状態になる。例外はPH-1では出なかったが、出ても問題ないようにする。
            if (zipEntryCount == 0) {
                InputStreamReader(FileInputStream(file), "UTF-8").use { inStream ->
                    newColumnList =
                        AppDataExporter.decodeAppData(this@importAppData, JsonReader(inStream))
                }
            }

            newColumnList
        },
        afterProc = {
            // cancelled.
            if (it == null) return@launchProgress

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
                setImportProtector(this@importAppData, false)
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
