package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import jp.juggler.subwaytooter.pref.PrefI
import jp.juggler.util.data.*
import jp.juggler.util.log.LogCategory
import jp.juggler.util.time.formatLocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LogData private constructor() {
    companion object : TableCompanion {
        private val log = LogCategory("LogData")
        override val table = "warning"
        private const val COL_TIME = "t"
        private const val COL_LEVEL = "l"
        private const val COL_CATEGORY = "c"
        private const val COL_MESSAGE = "m"

        override fun onDBCreate(db: SQLiteDatabase) {
            db.execSQL(
                """create table if not exists $table
			(_id INTEGER PRIMARY KEY
			,$COL_TIME integer not null
			,$COL_LEVEL integer not null
			,$COL_CATEGORY text not null
			,$COL_MESSAGE text not null
			)""".trimIndent()
            )
            db.execSQL("create index if not exists ${table}_time on $table($COL_TIME,$COL_LEVEL)")
        }

        override fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 66 && newVersion >= 66) {
                onDBCreate(db)
            }
        }

        @Suppress("unused")
        private fun Int.getLogLevelString() =
            when (this) {
                Log.ERROR -> "E"
                Log.WARN -> "W"
                Log.INFO -> "I"
                Log.DEBUG -> "D"
                Log.VERBOSE -> "V"
                Log.ASSERT -> "A"
                else -> this.toString()
            }
    }

    class Access(val db: SQLiteDatabase) {
        fun deleteOld(now: Long) {
            try {
                val expiredAt = now - TimeUnit.DAYS.toMillis(7)
                db.execSQL(
                    "delete from $table where $COL_TIME<=?",
                    arrayOf(expiredAt.toString()),
                )
            } catch (ex: Throwable) {
                log.e(ex, "deleteOld failed.")
            }
        }

        fun insert(level: Int, category: String, message: String) {
            try {
                if (level < PrefI.ipLogSaveLevel.value) return
                ContentValues().apply {
                    put(COL_TIME, System.currentTimeMillis())
                    put(COL_LEVEL, level)
                    put(COL_CATEGORY, category)
                    put(COL_MESSAGE, message)
                }.replaceTo(db, table)
            } catch (ex: Throwable) {
                log.e(ex, "insert failed.")
            }
        }

        @Suppress("BlockingMethodInNonBlockingContext")
        suspend fun createLogFile(context: Context): File {
            return withContext(Dispatchers.IO) {
                val cacheDir = context.externalCacheDir ?: context.cacheDir
                cacheDir.mkdirs()
                val fileNamePrefix =
                    SimpleDateFormat("'log'-yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
                val outFile = File.createTempFile(fileNamePrefix, ".zip", cacheDir)
                ZipOutputStream(FileOutputStream(outFile)).use { zos ->
                    val ze = ZipEntry("$fileNamePrefix.txt")
                    zos.putNextEntry(ze)
                    val crlf = "\n".toByteArray()
                    val expiredAt = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
                    db.rawQuery(
                        "select * from $table where $COL_TIME >=? order by $COL_TIME asc",
                        arrayOf(expiredAt.toString())
                    )?.use { cursor ->
                        val idxTime = cursor.getColumnIndex(COL_TIME)
                        val idxLevel = cursor.getColumnIndex(COL_LEVEL)
                        val idxCategory = cursor.getColumnIndex(COL_CATEGORY)
                        val idxMessage = cursor.getColumnIndex(COL_MESSAGE)
                        while (cursor.moveToNext()) {
                            val time = cursor.getLongOrNull(idxTime)
                            val level = cursor.getIntOrNull(idxLevel)
                            val category = cursor.getStringOrNull(idxCategory)
                            val message = cursor.getStringOrNull(idxMessage)
                            val line =
                                "${time?.formatLocalTime()} ${level?.getLogLevelString()}/$category $message"
                            zos.write(line.toByteArray())
                            zos.write(crlf)
                        }
                    }
                    zos.closeEntry()
                }
                outFile
            }
        }
    }
}
