package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.support.v4.util.LruCache

import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootRelationShip
import jp.juggler.subwaytooter.util.LogCategory

class UserRelation private constructor() {
	
	private var following : Boolean = false   // 認証ユーザからのフォロー状態にある
	var followed_by : Boolean = false // 認証ユーザは被フォロー状態にある
	var blocking : Boolean = false
	var muting : Boolean = false
	private var requested : Boolean = false  // 認証ユーザからのフォローは申請中である
	
	var following_reblogs : Int = 0 // このユーザからのブーストをTLに表示する
	
	// 認証ユーザからのフォロー状態
	fun getFollowing(who : TootAccount?) : Boolean {
		return if(requested && ! following && who != null && ! who.locked) true else following
	}
	
	// 認証ユーザからのフォローリクエスト申請中状態
	fun getRequested(who : TootAccount?) : Boolean {
		return if(requested && ! following && who != null && ! who.locked) false else requested
	}
	
	companion object : TableCompanion {
		
		private val log = LogCategory("UserRelation")
		
		private const val table = "user_relation"
		private const val COL_TIME_SAVE = "time_save"
		private const val COL_DB_ID = "db_id" // SavedAccount のDB_ID
		private const val COL_WHO_ID = "who_id" // ターゲットアカウントのID
		private const val COL_FOLLOWING = "following"
		private const val COL_FOLLOWED_BY = "followed_by"
		private const val COL_BLOCKING = "blocking"
		private const val COL_MUTING = "muting"
		private const val COL_REQUESTED = "requested"
		
		// (mastodon 2.1 or later) per-following-user setting.
		// Whether the boosts from target account will be shown on authorized user's home TL.
		private const val COL_FOLLOWING_REBLOGS = "following_reblogs"
		
		const val REBLOG_HIDE =
			0 // don't show the boosts from target account will be shown on authorized user's home TL.
		const val REBLOG_SHOW =
			1 // show the boosts from target account will be shown on authorized user's home TL.
		const val REBLOG_UNKNOWN = 2 // not following, or instance don't support hide reblog.
		
		override fun onDBCreate(db : SQLiteDatabase) {
			log.d("onDBCreate!")
			db.execSQL(
				"create table if not exists " + table
					+ "(_id INTEGER PRIMARY KEY"
					+ "," + COL_TIME_SAVE + " integer not null"
					+ "," + COL_DB_ID + " integer not null"
					+ "," + COL_WHO_ID + " integer not null"
					+ "," + COL_FOLLOWING + " integer not null"
					+ "," + COL_FOLLOWED_BY + " integer not null"
					+ "," + COL_BLOCKING + " integer not null"
					+ "," + COL_MUTING + " integer not null"
					+ "," + COL_REQUESTED + " integer not null"
					+ "," + COL_FOLLOWING_REBLOGS + " integer not null"
					+ ")"
			)
			db.execSQL(
				"create unique index if not exists " + table + "_id on " + table + "(" + COL_DB_ID + "," + COL_WHO_ID + ")"
			)
			db.execSQL(
				"create index if not exists " + table + "_time on " + table + "(" + COL_TIME_SAVE + ")"
			)
		}
		
		override fun onDBUpgrade(db : SQLiteDatabase, oldVersion : Int, newVersion : Int) {
			if(oldVersion < 6 && newVersion >= 6) {
				onDBCreate(db)
			}
			if(oldVersion < 20 && newVersion >= 20) {
				try {
					db.execSQL("alter table $table add column $COL_FOLLOWING_REBLOGS integer default 1")
					/*
						(COL_FOLLOWING_REBLOGS カラムのデフォルト値について)
						1.7.5でboolean値を保存していた関係でデフォルト値は1(REBLOG_SHOW)になってしまっている
						1.7.6以降では3値論理にしたのでデフォルトは2(REBLOG_UNKNOWN)の方が適切だが、SQLiteにはカラムのデフォルト制約の変更を行う機能がない
						データは適当に更新されるはずだから、今のままでも多分問題ないはず…
					*/
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
			}
		}
		
		fun deleteOld(now : Long) {
			try {
				// 古いデータを掃除する
				val expire = now - 86400000L * 365
				App1.database.delete(table, "$COL_TIME_SAVE<?", arrayOf(expire.toString()))
				
			} catch(ex : Throwable) {
				log.e(ex, "deleteOld failed.")
			}
			
		}
		
		fun save1(now : Long, db_id : Long, src : TootRelationShip) : UserRelation {
			
			try {
				val cv = ContentValues()
				cv.put(COL_TIME_SAVE, now)
				cv.put(COL_DB_ID, db_id)
				cv.put(COL_WHO_ID, src.id?.toLong() ?: -1L)
				// TODO misskey用にIDがStringのテーブルを用意する？
				cv.put(COL_FOLLOWING, if(src.following) 1 else 0)
				cv.put(COL_FOLLOWED_BY, if(src.followed_by) 1 else 0)
				cv.put(COL_BLOCKING, if(src.blocking) 1 else 0)
				cv.put(COL_MUTING, if(src.muting) 1 else 0)
				cv.put(COL_REQUESTED, if(src.requested) 1 else 0)
				cv.put(COL_FOLLOWING_REBLOGS, src.showing_reblogs)
				App1.database.replace(table, null, cv)
				
				val key = String.format("%s:%s", db_id, src.id)
				mMemoryCache.remove(key)
			} catch(ex : Throwable) {
				log.e(ex, "save failed.")
			}
			
			return load(db_id, src.id)
		}
		
		fun saveList(now : Long, db_id : Long, src_list : ArrayList<TootRelationShip>) {
			
			val cv = ContentValues()
			cv.put(COL_TIME_SAVE, now)
			cv.put(COL_DB_ID, db_id)
			
			var bOK = false
			val db = App1.database
			db.execSQL("BEGIN TRANSACTION")
			try {
				for(src in src_list) {
					cv.put(COL_WHO_ID, src.id?.toLong() ?: -1L)
					// TODO misskey用にidがStringのテーブルを用意する？
					cv.put(COL_FOLLOWING, src.following.b2i())
					cv.put(COL_FOLLOWED_BY, src.followed_by.b2i())
					cv.put(COL_BLOCKING, src.blocking.b2i())
					cv.put(COL_MUTING, src.muting.b2i())
					cv.put(COL_REQUESTED, src.requested.b2i())
					cv.put(COL_FOLLOWING_REBLOGS, src.showing_reblogs)
					db.replace(table, null, cv)
					
				}
				bOK = true
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "saveList failed.")
			}
			
			if(bOK) {
				db.execSQL("COMMIT TRANSACTION")
				for(src in src_list) {
					val key = String.format("%s:%s", db_id, src.id)
					mMemoryCache.remove(key)
				}
			} else {
				db.execSQL("ROLLBACK TRANSACTION")
			}
		}
		
		private val mMemoryCache = LruCache<String, UserRelation>(2048)
		
		private const val load_where = "$COL_DB_ID=? and $COL_WHO_ID=?"
		
		private val load_where_arg = object : ThreadLocal<Array<String?>>() {
			override fun initialValue() : Array<String?> {
				return Array(2) { _ -> null }
			}
		}
		
		// TODO UserRelationテーブルのMisskey対応
		// 文字列IDなら別テーブル参照とかできそう
		fun load(db_id : Long, who_id : EntityId?)=
			load(db_id,who_id?.toLong() ?: -1L)

		fun load(db_id : Long, who_id : Long) : UserRelation {
			
			val key = String.format("%s:%s", db_id, who_id)
			
			val cached : UserRelation? = mMemoryCache.get(key)
			if(cached != null) return cached
			
			try {
				val where_arg = load_where_arg.get()
				where_arg[0] = db_id.toString()
				where_arg[1] = who_id.toString()
				App1.database.query(table, null, load_where, where_arg, null, null, null)
					.use { cursor ->
						if(cursor.moveToNext()) {
							val dst = UserRelation()
							dst.following = 0 != cursor.getInt(cursor.getColumnIndex(COL_FOLLOWING))
							dst.followed_by = 0 !=
								cursor.getInt(cursor.getColumnIndex(COL_FOLLOWED_BY))
							dst.blocking = 0 != cursor.getInt(cursor.getColumnIndex(COL_BLOCKING))
							dst.muting = 0 != cursor.getInt(cursor.getColumnIndex(COL_MUTING))
							dst.requested = 0 != cursor.getInt(cursor.getColumnIndex(COL_REQUESTED))
							dst.following_reblogs =
								cursor.getInt(cursor.getColumnIndex(COL_FOLLOWING_REBLOGS))
							return dst
						}
					}
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "load failed.")
			}
			
			val dst = UserRelation()
			mMemoryCache.put(key, dst)
			return dst
		}
	}
	
	//	public static Cursor createCursor(){
	//		return App1.getDB().query( table, null, null, null, null, null, COL_NAME + " asc" );
	//	}
	//
	//	public static void delete( String name ){
	//		try{
	//			App1.getDB().delete( table, COL_NAME + "=?", new String[]{ name } );
	//		}catch( Throwable ex ){
	//			warning.e( ex, "delete failed." );
	//		}
	//	}
	//
	//	public static HashSet< String > getNameSet(){
	//		HashSet< String > dst = new HashSet<>();
	//		try{
	//			Cursor cursor = App1.getDB().query( table, null, null, null, null, null, null );
	//			if( cursor != null ){
	//				try{
	//					int idx_name = cursor.getColumnIndex( COL_NAME );
	//					while( cursor.moveToNext() ){
	//						String s = cursor.getString( idx_name );
	//						dst.add( s );
	//					}
	//				}finally{
	//					cursor.close();
	//				}
	//			}
	//		}catch( Throwable ex ){
	//			warning.e(ex,"getNameSet() failed.")
	//		}
	//		return dst;
	//	}
	
	//	private static final String[] isMuted_projection = new String[]{COL_NAME};
	//	private static final String   isMuted_where = COL_NAME+"=?";
	//	private static final ThreadLocal<String[]> isMuted_where_arg = new ThreadLocal<String[]>() {
	//		@Override protected String[] initialValue() {
	//			return new String[1];
	//		}
	//	};
	//	public static boolean isMuted( String app_name ){
	//		if( app_name == null ) return false;
	//		try{
	//			String[] where_arg = isMuted_where_arg.get();
	//			where_arg[0] = app_name;
	//			Cursor cursor = App1.getDB().query( table, isMuted_projection,isMuted_where , where_arg, null, null, null );
	//			try{
	//				if( cursor.moveToFirst() ){
	//					return true;
	//				}
	//			}finally{
	//				cursor.close();
	//			}
	//		}catch( Throwable ex ){
	//			warning.e( ex, "load failed." );
	//		}
	//		return false;
	//	}
	
}
