package jp.juggler.subwaytooter.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.juggler.subwaytooter.table.AppDatabaseHolder
import jp.juggler.subwaytooter.table.DB_VERSION
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestDatabase {

    // 新規インストールで最新バージョンのDBを作る
    @Test
    fun testCreateAll() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "testCreateAll"
        context.deleteDatabase(dbName)
        val db = AppDatabaseHolder(context, dbName, DB_VERSION).database
        assertEquals("db version", DB_VERSION, db.version)
    }

    // スキーマバージョン1で作って順にアップグレードをかける
    @Test
    fun testUpgrade() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "testUpgrade"
        context.deleteDatabase(dbName)
        for (v in 1..DB_VERSION) {
            run {
                val holder = AppDatabaseHolder(context, dbName, v)
                assertEquals("db version", v, holder.database.version)
                holder.close()
            }
        }
    }
}