package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import jp.juggler.util.data.*
import jp.juggler.util.log.LogCategory
import kotlin.math.abs

// リスト要素のデータ
class ImageAspect(
    var id: Long = 0L,
    var url: String = "",
    var aspect: Float = 1f,
) {
    companion object : TableCompanion {
        private val log = LogCategory("ImageAspect")
        override val table = "image_aspect"
        private const val COL_ID = "_id"
        private const val COL_URL = "u"
        private const val COL_ASPECT = "aspect"

        val columnList = MetaColumns(table, 67).apply {
            column(0, COL_ID, "INTEGER PRIMARY KEY")
            column(0, COL_URL, "text not null")
            column(0, COL_ASPECT, "real not null")
            createExtra={
                arrayOf(
                    "create unique index if not exists ${table}_u on $table($COL_URL)",
                )
            }
        }
        override fun onDBCreate(db: SQLiteDatabase) {
            log.d("onDBCreate!")
            columnList.onDBCreate(db)
        }

        override fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            columnList.onDBUpgrade(db,oldVersion,newVersion)
        }
    }

    class Access(val db: SQLiteDatabase) {
        fun load(url:String) :Float?=
            db.rawQuery(
                "select $COL_ASPECT from $table where $COL_URL=?",
                arrayOf(url),
            ).use { cursor->
                when {
                    cursor.moveToNext() -> cursor.getFloat(0)
                    else -> null
                }
            }

        fun save(url:String,aspect:Float) {
            val oldAspect = load(url)
            if( oldAspect != null && abs(oldAspect-aspect) <= 1.4E-40F) return
            ContentValues().apply {
                put(COL_URL, url)
                put(COL_ASPECT, aspect)
            }.replaceTo(db, table)
        }
    }
}
