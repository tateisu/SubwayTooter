package jp.juggler.subwaytooter.global

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import jp.juggler.subwaytooter.table.*
import jp.juggler.util.LogCategory

// 2017/4/25 v10 1=>2 SavedAccount に通知設定を追加
// 2017/4/25 v10 1=>2 NotificationTracking テーブルを追加
// 2017/4/29 v20 2=>5 MediaShown,ContentWarningのインデクスが間違っていたので貼り直す
// 2017/4/29 v23 5=>6 MutedAppテーブルの追加、UserRelationテーブルの追加
// 2017/5/01 v26 6=>7 AcctSetテーブルの追加
// 2017/5/02 v32 7=>8 (この変更は取り消された)
// 2017/5/02 v32 8=>9 AcctColor テーブルの追加
// 2017/5/04 v33 9=>10 SavedAccountに項目追加
// 2017/5/08 v41 10=>11 MutedWord テーブルの追加
// 2017/5/17 v59 11=>12 PostDraft テーブルの追加
// 2017/5/23 v68 12=>13 SavedAccountに項目追加
// 2017/5/25 v69 13=>14 SavedAccountに項目追加
// 2017/5/27 v73 14=>15 TagSetテーブルの追加
// 2017/7/22 v99 15=>16 SavedAccountに項目追加
// 2017/7/22 v106 16=>17 AcctColor に項目追加
// 2017/9/23 v161 17=>18 SavedAccountに項目追加
// 2017/9/23 v161 18=>19 ClientInfoテーブルを置き換える
// 2017/12/01 v175 19=>20 UserRelation に項目追加
// 2018/1/03 v197 20=>21 HighlightWord テーブルを追加
// 2018/3/16 v226 21=>22 FavMuteテーブルを追加
// 2018/4/17 v236 22=>23 SavedAccountテーブルに項目追加
// 2018/4/20 v240 23=>24 SavedAccountテーブルに項目追加
// 2018/5/16 v252 24=>25 SubscriptionServerKey テーブルを追加
// 2018/5/16 v252 25=>26 SubscriptionServerKey テーブルを丸ごと変更
// 2018/8/5 v264 26 => 27 SavedAccountテーブルに項目追加
// 2018/8/17 v267 27 => 28 SavedAccountテーブルに項目追加
// 2018/8/19 v267 28 => 29 (失敗)ContentWarningMisskey, MediaShownMisskey テーブルを追加
// 2018/8/19 v268 29 => 30 ContentWarningMisskey, MediaShownMisskey, UserRelationMisskeyテーブルを追加
// 2018/8/19 v268 30 => 31 (29)で失敗しておかしくなったContentWarningとMediaShownを作り直す
// 2018/8/28 v279 31 => 32 UserRelation,UserRelationMisskey にendorsedを追加
// 2018/8/28 v280 32 => 33 NotificationTracking テーブルの作り直し。SavedAccountに通知二種類を追加
// 2018/10/31 v296 33 => 34 UserRelationMisskey に blocked_by を追加
// 2018/10/31 v296 34 => 35 UserRelationMisskey に requested_by を追加
// 2018/12/6 v317 35 => 36 ContentWarningテーブルの作り直し。
// 2019/6/4 v351 36 => 37 SavedAccount テーブルに項目追加。
// 2019/6/4 v351 37 => 38 SavedAccount テーブルに項目追加。
// 2019/8/12 v362 38 => 39 SavedAccount テーブルに項目追加。
// 2019/10/22 39 => 40 NotificationTracking テーブルに項目追加。
// 2019/10/22 40 => 41 NotificationCache テーブルに項目追加。
// 2019/10/23 41=> 42 SavedAccount テーブルに項目追加。
// 2019/11/15 42=> 43 HighlightWord テーブルに項目追加。
// 2019/12/17 43=> 44 SavedAccount テーブルに項目追加。
// 2019/12/18 44=> 45 SavedAccount テーブルに項目追加。
// 2019/12/18 44=> 46 SavedAccount テーブルに項目追加。
// 2020/6/8 46 => 54 別ブランチで色々してた。このブランチには影響ないが onDowngrade()を実装してないので上げてしまう
// 2020/7/19 54=>55 UserRelation テーブルに項目追加。
// 2020/9/7 55=>56 SavedAccountテーブルにCOL_DOMAINを追加。
// 2020/9/20 56=>57 SavedAccountテーブルに項目追加
// 2020/9/20 57=>58 UserRelationテーブルに項目追加
// 2021/2/10 58=>59 SavedAccountテーブルに項目追加
// 2021/5/11 59=>60 SavedAccountテーブルに項目追加
// 2021/5/23 60=>61 SavedAccountテーブルに項目追加

const val DB_VERSION = 61
const val DB_NAME = "app_db"
private val log = LogCategory("AppDatabase")

val TABLE_LIST = arrayOf(
    LogData,
    SavedAccount,
    ClientInfo,
    MediaShown,
    ContentWarning,
    NotificationTracking,
    NotificationCache,
    MutedApp,
    UserRelation,
    AcctSet,
    AcctColor,
    MutedWord,
    PostDraft,
    TagSet,
    HighlightWord,
    FavMute,
    SubscriptionServerKey
)

private class DBOpenHelper(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        for (ti in TABLE_LIST) {
            ti.onDBCreate(db)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        for (ti in TABLE_LIST) {
            ti.onDBUpgrade(db, oldVersion, newVersion)
        }
    }
}

interface AppDatabaseHolder {
    val database: SQLiteDatabase
}

class AppDatabaseHolderImpl(context: Context) : AppDatabaseHolder {
    private var openHelperNullable: DBOpenHelper? = null

    override val database: SQLiteDatabase
        get() = openHelperNullable!!.writableDatabase

    init {
        if (openHelperNullable == null) {
            log.d("create DBOpenHelper")
            openHelperNullable = DBOpenHelper(context.applicationContext)
        }

        //			if( BuildConfig.DEBUG){
        //				SQLiteDatabase db = db_open_helper.getWritableDatabase();
        //				db_open_helper.onCreate( db );
        //			}

        log.d("call deleteOld…")
        val now = System.currentTimeMillis()
        AcctSet.deleteOld(now)
        UserRelation.deleteOld(now)
        ContentWarning.deleteOld(now)
        MediaShown.deleteOld(now)
    }
}
