package jp.juggler.subwaytooter.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import jp.juggler.subwaytooter.App1
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestDatabase {

    private class MockDbHelper(
        context: Context,
        dbName: String,
        dbVersion: Int,
        val create: (SQLiteDatabase) -> Unit,
        val upgrade: (SQLiteDatabase, Int, Int) -> Unit,
    ) : SQLiteOpenHelper(context, dbName, null, dbVersion) {
        override fun onCreate(db: SQLiteDatabase) {
            create(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            upgrade(db, oldVersion, newVersion)
        }
    }

    @Test
    fun testCreateAll() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "testCreateAll"
        val helper = MockDbHelper(
            context,
            dbName,
            App1.DB_VERSION,
            create = { db ->
                App1.tableList.forEach {
                    val ex = try {
                        it.onDBCreate(db)
                        null
                    } catch (ex: Throwable) {
                        ex
                    }
                    assertNull("${it.table} onDBCreate", ex)
                }
            },
            upgrade = { db, oldV, newV ->
                App1.tableList.forEach {
                    val ex = try {
                        it.onDBUpgrade(db, oldV, newV)
                        null
                    } catch (ex: Throwable) {
                        ex
                    }
                    assertNull("${it.table} onDBUpgrade", ex)
                }
            }
        )
        context.deleteDatabase(dbName)
        helper.writableDatabase
    }
}