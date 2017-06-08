package jp.juggler.subwaytooter;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import jp.juggler.subwaytooter.util.Utils;

public class ActCallback extends AppCompatActivity {
	static final AtomicReference< Uri > last_uri = new AtomicReference<>( null );
	static final AtomicReference< Intent > sent_intent = new AtomicReference<>( null );
	
	@Override protected void onCreate( @Nullable Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		Intent intent = getIntent();
		if( intent != null ){
			if( Intent.ACTION_SEND.equals( intent.getAction() )
				|| Intent.ACTION_SEND_MULTIPLE.equals( intent.getAction() )
				){
				
				// Google Photo などから送られるIntentに含まれるuriの有効期間はActivityが閉じられるまで
				// http://qiita.com/pside/items/a821e2fe9ae6b7c1a98c
				
				// 有効期間を延長する
				intent = remake(intent);
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
		try{
			final String action = src.getAction();
			final String type = src.getType();
			
			if( type == null ) return null;
			
			if( type.startsWith( "image/" ) ){
				if( Intent.ACTION_SEND.equals( action ) ){
					Uri uri = src.getParcelableExtra( Intent.EXTRA_STREAM );
					if( uri == null ) return null;
					try{
						uri = saveToCache( uri );
						
						Intent dst = new Intent( action );
						dst.setType( type );
						dst.putExtra( Intent.EXTRA_STREAM, uri );
						return dst;
					}catch( Throwable ex ){
						ex.printStackTrace();
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
								ex.printStackTrace();
							}
						}
					}
					if( list_dst.isEmpty() ) return null;
					Intent dst = new Intent( action );
					dst.setType( type );
					dst.putParcelableArrayListExtra( Intent.EXTRA_STREAM, list_dst );
					return dst;
				}
			}else if( type.startsWith( "text/" ) ){
				if( Intent.ACTION_SEND.equals( action ) ){
					String sv = src.getStringExtra( Intent.EXTRA_TEXT );
					if( sv == null ) return null;
					Intent dst = new Intent( action );
					dst.setType( type );
					dst.putExtra( Intent.EXTRA_TEXT, sv );
					return dst;
				}
			}
		}catch(Throwable ex){
			ex.printStackTrace();
		}
		return null;
	}
	
	private Uri saveToCache( Uri uri ) throws Throwable{
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
}
