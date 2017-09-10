package jp.juggler.subwaytooter;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class ActCallback extends AppCompatActivity {
	private static final LogCategory log = new LogCategory( "ActCallback" );
	
	public static final String ACTION_NOTIFICATION_CLICK = "notification_click";
	
	static final AtomicReference< Uri > last_uri = new AtomicReference<>( null );
	static final AtomicReference< Intent > sent_intent = new AtomicReference<>( null );
	
	@Override protected void onCreate( @Nullable Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		Intent intent = getIntent();
		if( intent != null ){
			String action = intent.getAction();
			String type = intent.getType();
			if( Intent.ACTION_SEND.equals( action )
				|| Intent.ACTION_SEND_MULTIPLE.equals( action )
				|| ( type != null && type.startsWith( "image/" ) && Intent.ACTION_VIEW.equals( action ) )
				){
				
				// Google Photo などから送られるIntentに含まれるuriの有効期間はActivityが閉じられるまで
				// http://qiita.com/pside/items/a821e2fe9ae6b7c1a98c
				
				// 有効期間を延長する
				intent = remake( intent );
				if( intent != null ){
					sent_intent.set( intent );
				}
			}else{
				Uri uri = intent.getData();
				if( uri != null ){
					last_uri.set( uri );
				}
			}
		}
		// どうであれメイン画面に戻る
		intent = new Intent( this, ActMain.class );
		intent.addFlags( Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY | Intent.FLAG_ACTIVITY_NEW_TASK );
		startActivity( intent );
		finish();
	}
	
	@Nullable Intent remake( @NonNull Intent src ){
		
		sweepOldCache();
		
		try{
			final String action = src.getAction();
			final String type = src.getType();
			
			if( type != null && (type.startsWith( "image/" ) || type.startsWith( "video/" )) ){
				if( Intent.ACTION_VIEW.equals( action ) ){
					Uri uri = src.getData();
					if( uri == null ) return null;
					try{
						uri = saveToCache( uri );
						Intent dst = new Intent( action );
						dst.setDataAndType( uri, type );
						return dst;
					}catch( Throwable ex ){
						log.trace( ex );
					}
				}else if( Intent.ACTION_SEND.equals( action ) ){
					Uri uri = src.getParcelableExtra( Intent.EXTRA_STREAM );
					if( uri == null ){
						// text/plain
						return src;
					}else{
						try{
							uri = saveToCache( uri );
							
							Intent dst = new Intent( action );
							dst.setType( type );
							dst.putExtra( Intent.EXTRA_STREAM, uri );
							return dst;
						}catch( Throwable ex ){
							log.trace( ex );
						}
					}
				}else if( Intent.ACTION_SEND_MULTIPLE.equals( action ) ){
					ArrayList< Uri > list_uri = src.getParcelableArrayListExtra( Intent.EXTRA_STREAM );
					if( list_uri == null ) return null;
					ArrayList< Uri > list_dst = new ArrayList<>();
					for( Uri uri : list_uri ){
						if( uri != null ){
							try{
								uri = saveToCache( uri );
								list_dst.add( uri );
							}catch( Throwable ex ){
								log.trace( ex );
							}
						}
					}
					if( list_dst.isEmpty() ) return null;
					Intent dst = new Intent( action );
					dst.setType( type );
					dst.putParcelableArrayListExtra( Intent.EXTRA_STREAM, list_dst );
					return dst;
				}
			}else if( Intent.ACTION_SEND.equals( action ) ){
				
				// Swarmアプリから送られたインテントは getType()==null だが EXTRA_TEXT は含まれている
				// EXTRA_TEXT の存在を確認してからtypeがnullもしくは text/plain なら受け取る
				
				String sv = src.getStringExtra( Intent.EXTRA_TEXT );
				if( ! TextUtils.isEmpty( sv ) && ( type == null || type.startsWith( "text/" ) ) ){
					String subject = src.getStringExtra( Intent.EXTRA_SUBJECT );
					if( ! TextUtils.isEmpty( subject ) ){
						sv = subject + " " + sv;
					}
					Intent dst = new Intent( action );
					dst.setType( "text/plain" );
					dst.putExtra( Intent.EXTRA_TEXT, sv );
					return dst;
				}
			}
		}catch( Throwable ex ){
			log.trace( ex );
		}
		return null;
	}
	
	private Uri saveToCache( Uri uri ) throws Throwable{
		
		// prepare cache directory
		File cache_dir = getCacheDir();
		//noinspection ResultOfMethodCallIgnored
		cache_dir.mkdirs();
		
		String name = "img." + Long.toString( System.currentTimeMillis() ) + "." + Utils.digestSHA256( uri.toString() );
		
		File dst = new File( cache_dir, name );
		
		FileOutputStream os = new FileOutputStream( dst );
		try{
			InputStream is = getContentResolver().openInputStream( uri );
			if( is == null )
				throw new RuntimeException( "getContentResolver.openInputStream returns null." );
			try{
				IOUtils.copy( is, os );
			}finally{
				IOUtils.closeQuietly( is );
			}
		}finally{
			IOUtils.closeQuietly( os );
		}
		return FileProvider.getUriForFile( this, App1.FILE_PROVIDER_AUTHORITY, dst );
	}
	
	private void sweepOldCache(){
		// sweep old cache
		try{
			// prepare cache directory
			File cache_dir = getCacheDir();
			//noinspection ResultOfMethodCallIgnored
			cache_dir.mkdirs();
			
			long now = System.currentTimeMillis();
			for( File f : cache_dir.listFiles() ){
				try{
					if( f.isFile()
						&& f.getName().startsWith( "img." )
						&& now - f.lastModified() >= 86400000L
						){
						//noinspection ResultOfMethodCallIgnored
						f.delete();
					}
				}catch( Throwable ex ){
					log.trace( ex );
				}
			}
		}catch( Throwable ex ){
			log.trace( ex );
		}
	}
}
