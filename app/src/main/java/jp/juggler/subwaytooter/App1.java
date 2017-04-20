package jp.juggler.subwaytooter;

import android.app.Application;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import jp.juggler.subwaytooter.table.AccessToken;
import jp.juggler.subwaytooter.table.ClientInfo;
import jp.juggler.subwaytooter.table.LogData;
import jp.juggler.subwaytooter.table.SavedAccount;

public class App1 extends Application{
	
	@Override
	public void onCreate(){
		super.onCreate();
		if( db_open_helper == null ){
			db_open_helper = new DBOpenHelper( getApplicationContext() );
		}
	}
	
	@Override
	public void onTerminate(){
		super.onTerminate();
	}
	
	
	static final String DB_NAME = "app_db";
	static final int DB_VERSION = 1;

	static DBOpenHelper db_open_helper;
	
	public static SQLiteDatabase getDB(){
		return db_open_helper.getWritableDatabase();
	}
	
	static class DBOpenHelper extends SQLiteOpenHelper {
		
		public DBOpenHelper( Context context ){
			super( context, DB_NAME, null , DB_VERSION );
		}
		
		@Override
		public void onCreate( SQLiteDatabase db ){
			LogData.onDBCreate( db);
			//
			AccessToken.onDBCreate(db);
			SavedAccount.onDBCreate(db);
			ClientInfo.onDBCreate( db);
		}
		
		@Override
		public void onUpgrade( SQLiteDatabase db, int oldVersion, int newVersion ){
			LogData.onDBUpgrade( db,oldVersion,newVersion );
			AccessToken.onDBUpgrade( db,oldVersion,newVersion );
			SavedAccount.onDBUpgrade( db,oldVersion,newVersion );
			ClientInfo.onDBUpgrade( db,oldVersion,newVersion );
		}
	}
}
