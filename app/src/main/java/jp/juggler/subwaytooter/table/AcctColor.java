package jp.juggler.subwaytooter.table;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.LruCache;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;

import java.util.Locale;

public class AcctColor {
	
	private static final LogCategory log = new LogCategory( "AcctColor" );
	
	public static final String table = "acct_color";
	private static final String COL_TIME_SAVE = "time_save";
	private static final String COL_ACCT = "ac"; //@who@host ascii文字の大文字小文字は(sqliteにより)同一視される
	private static final String COL_COLOR_FG = "cf"; // 未設定なら0、それ以外は色
	private static final String COL_COLOR_BG = "cb"; // 未設定なら0、それ以外は色
	private static final String COL_NICKNAME = "nick"; // 未設定ならnullか空文字列
	private static final String COL_NOTIFICATION_SOUND = "notification_sound"; // 未設定ならnullか空文字列
	
	public static void onDBCreate( SQLiteDatabase db ){
		log.d( "onDBCreate!" );
		db.execSQL(
			"create table if not exists " + table
				+ "(_id INTEGER PRIMARY KEY"
				+ "," + COL_TIME_SAVE + " integer not null"
				+ "," + COL_ACCT + " text not null"
				+ "," + COL_COLOR_FG + " integer"
				+ "," + COL_COLOR_BG + " integer"
				+ "," + COL_NICKNAME + " text "
				+ "," + COL_NOTIFICATION_SOUND + " text default ''"
				+ ")"
		);
		db.execSQL(
			"create unique index if not exists " + table + "_acct on " + table + "(" + COL_ACCT + ")"
		);
		db.execSQL(
			"create index if not exists " + table + "_time on " + table + "(" + COL_TIME_SAVE + ")"
		);
	}
	
	public static void onDBUpgrade( SQLiteDatabase db, int oldVersion, int newVersion ){
		if( oldVersion < 9 && newVersion >= 9 ){
			onDBCreate( db );
			return;
		}

		if( oldVersion < 17 && newVersion >= 17 ){
			try{
				db.execSQL( "alter table " + table + " add column " + COL_NOTIFICATION_SOUND + " text default ''" );
			}catch( Throwable ex ){
				log.trace( ex );
			}
		}
	}
	
	@NonNull public String acct;
	public int color_fg;
	public int color_bg;
	public String nickname;
	public String notification_sound ;
	
	public AcctColor( @NonNull String acct, String nickname, int color_fg, int color_bg ,String notification_sound){
		this.acct = acct;
		this.nickname = nickname;
		this.color_fg = color_fg;
		this.color_bg = color_bg;
		this.notification_sound = notification_sound;
	}
	
	private AcctColor( @NonNull String acct ){
		this.acct = acct;
	}
	
	public void save( long now ){
		
		acct = acct.toLowerCase( Locale.ENGLISH );
		
		try{
			ContentValues cv = new ContentValues();
			cv.put( COL_TIME_SAVE, now );
			cv.put( COL_ACCT, acct );
			cv.put( COL_COLOR_FG, color_fg );
			cv.put( COL_COLOR_BG, color_bg );
			cv.put( COL_NICKNAME, nickname == null ? "" : nickname );
			cv.put( COL_NOTIFICATION_SOUND, notification_sound == null ? "" : notification_sound );
			App1.getDB().replace( table, null, cv );
			mMemoryCache.remove( acct );
		}catch( Throwable ex ){
			log.trace( ex );
			log.e( ex, "save failed." );
		}
	}
	
	private static final String load_where = COL_ACCT + "=?";
	
	private static final ThreadLocal< String[] > load_where_arg = new ThreadLocal< String[] >() {
		@Override protected String[] initialValue(){
			return new String[ 1 ];
		}
	};
	
	private static final LruCache< String, AcctColor > mMemoryCache = new LruCache<>( 2048 );
	
	@NonNull public static AcctColor load( @NonNull String acct ){
		
		acct = acct.toLowerCase( Locale.ENGLISH );
		
		AcctColor dst = mMemoryCache.get( acct );
		
		if( dst != null ) return dst;
		
		try{
			String[] where_arg = load_where_arg.get();
			where_arg[ 0 ] = acct;
			Cursor cursor = App1.getDB().query( table, null, load_where, where_arg, null, null, null );
			if( cursor != null ){
				try{
					if( cursor.moveToNext() ){
						dst = new AcctColor( acct );
						int idx;
						
						idx = cursor.getColumnIndex( COL_COLOR_FG );
						dst.color_fg = cursor.isNull( idx ) ? 0 : cursor.getInt( idx );
						
						idx = cursor.getColumnIndex( COL_COLOR_BG );
						dst.color_bg = cursor.isNull( idx ) ? 0 : cursor.getInt( idx );
						
						idx = cursor.getColumnIndex( COL_NICKNAME );
						dst.nickname = cursor.isNull( idx ) ? null : cursor.getString( idx );
						
						idx = cursor.getColumnIndex( COL_NOTIFICATION_SOUND );
						dst.notification_sound = cursor.isNull( idx ) ? null : cursor.getString( idx );
						
						mMemoryCache.put( acct, dst );
						return dst;
					}
				}finally{
					cursor.close();
				}
			}
		}catch( Throwable ex ){
			log.trace( ex );
			log.e( ex, "load failed." );
		}
		log.d( "lruCache size=%s,hit=%s,miss=%s", mMemoryCache.size(), mMemoryCache.hitCount(), mMemoryCache.missCount() );
		dst = new AcctColor( acct );
		mMemoryCache.put( acct, dst );
		return dst;
	}
	
	@NonNull public static String getNickname( @NonNull String acct ){
		AcctColor ac = load( acct );
		return ! TextUtils.isEmpty( ac.nickname ) ? Utils.sanitizeBDI( ac.nickname ) : acct;
	}
	
	@Nullable public static String getNotificationSound( @NonNull String acct ){
		AcctColor ac = load( acct );
		return ! TextUtils.isEmpty( ac.notification_sound ) ? ac.notification_sound : null;
	}
	
	public static boolean hasNickname( @Nullable AcctColor ac ){
		return ac != null && ! TextUtils.isEmpty( ac.nickname );
	}
	
	public static boolean hasColorForeground( @Nullable AcctColor ac ){
		return ac != null && ac.color_fg != 0;
	}
	
	public static boolean hasColorBackground( @Nullable AcctColor ac ){
		return ac != null && ac.color_bg != 0;
	}
	
	public static void clearMemoryCache(){
		mMemoryCache.evictAll ();
	}
	
	private static final char CHAR_REPLACE = 0x328A;
	
	@NonNull public static CharSequence getStringWithNickname( @NonNull Context context, int string_id , @NonNull String acct ){
		AcctColor ac = load( acct );
		String name = ! TextUtils.isEmpty( ac.nickname ) ? Utils.sanitizeBDI( ac.nickname ) : acct ;
		SpannableStringBuilder sb = new SpannableStringBuilder( context.getString( string_id,new String(new char[]{CHAR_REPLACE})) );
		for(int i=sb.length()-1;i>=0;--i){
			char c = sb.charAt( i );
			if( c != CHAR_REPLACE) continue;
			sb.replace( i,i+1,name );
			if( ac.color_fg != 0){
				sb.setSpan( new ForegroundColorSpan( ac.color_fg ), i, i + name.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE );
			}
			if( ac.color_bg != 0){
				sb.setSpan( new BackgroundColorSpan( ac.color_bg ), i, i + name.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE );
			}
		}
		return sb;
	}
	
}
