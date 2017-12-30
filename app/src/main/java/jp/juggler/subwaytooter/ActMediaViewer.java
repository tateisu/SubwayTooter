package jp.juggler.subwaytooter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import org.json.JSONArray;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.TootApiTask;
import jp.juggler.subwaytooter.api.entity.TootAttachment;
import jp.juggler.subwaytooter.dialog.ActionsDialog;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.ProgressResponseBody;
import jp.juggler.subwaytooter.util.Utils;
import jp.juggler.subwaytooter.view.PinchBitmapView;
import okhttp3.Call;
import okhttp3.Response;

public class ActMediaViewer extends AppCompatActivity implements View.OnClickListener {
	
	static final LogCategory log = new LogCategory( "ActMediaViewer" );
	
	static final String EXTRA_IDX = "idx";
	static final String EXTRA_DATA = "data";
	
	static String encodeMediaList( @Nullable TootAttachment.List list ){
		if( list == null ) return "[]";
		return list.encode().toString();
	}
	
	static TootAttachment.List decodeMediaList( @Nullable String src ){
		try{
			if( src != null ){
				return TootAttachment.parseList( new JSONArray( src ) );
			}
		}catch( Throwable ex ){
			log.trace( ex );
			log.e( ex, "decodeMediaList failed." );
		}
		return new TootAttachment.List();
	}
	
	public static void open( @NonNull ActMain activity, @NonNull TootAttachment.List list, int idx ){
		Intent intent = new Intent( activity, ActMediaViewer.class );
		intent.putExtra( EXTRA_IDX, idx );
		intent.putExtra( EXTRA_DATA, encodeMediaList( list ) );
		activity.startActivity( intent );
	}
	
	int idx;
	TootAttachment.List media_list;
	
	@Override protected void onSaveInstanceState( Bundle outState ){
		super.onSaveInstanceState( outState );
		outState.putInt( EXTRA_IDX, idx );
		outState.putString( EXTRA_DATA, encodeMediaList( media_list ) );
	}
	
	@Override protected void onCreate( @Nullable Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		App1.setActivityTheme( this, true, true );
		requestWindowFeature( Window.FEATURE_NO_TITLE );
		
		if( savedInstanceState == null ){
			Intent intent = getIntent();
			this.idx = intent.getIntExtra( EXTRA_IDX, idx );
			this.media_list = decodeMediaList( intent.getStringExtra( EXTRA_DATA ) );
		}else{
			this.idx = savedInstanceState.getInt( EXTRA_IDX );
			this.media_list = decodeMediaList( savedInstanceState.getString( EXTRA_DATA ) );
		}
		
		if( idx < 0 || idx >= media_list.size() ) idx = 0;
		
		initUI();
		
		load();
	}
	
	@Override protected void onDestroy(){
		super.onDestroy();
		pbvImage.setBitmap( null );
		exoPlayer.release();
		exoPlayer = null;
	}
	
	PinchBitmapView pbvImage;
	View btnPrevious;
	View btnNext;
	TextView tvError;
	SimpleExoPlayer exoPlayer;
	SimpleExoPlayerView exoView;
	View svDescription;
	TextView tvDescription;
	TextView tvStatus;
	
	void initUI(){
		setContentView( R.layout.act_media_viewer );
		pbvImage = findViewById( R.id.pbvImage );
		btnPrevious = findViewById( R.id.btnPrevious );
		btnNext = findViewById( R.id.btnNext );
		exoView = findViewById( R.id.exoView );
		tvError = findViewById( R.id.tvError );
		svDescription = findViewById( R.id.svDescription );
		tvDescription = findViewById( R.id.tvDescription );
		tvStatus = findViewById( R.id.tvStatus );
		
		boolean enablePaging = media_list.size() > 1;
		btnPrevious.setEnabled( enablePaging );
		btnNext.setEnabled( enablePaging );
		btnPrevious.setAlpha( enablePaging ? 1f : 0.3f );
		btnNext.setAlpha( enablePaging ? 1f : 0.3f );
		
		btnPrevious.setOnClickListener( this );
		btnNext.setOnClickListener( this );
		findViewById( R.id.btnDownload ).setOnClickListener( this );
		findViewById( R.id.btnMore ).setOnClickListener( this );
		
		pbvImage.setCallback( new PinchBitmapView.Callback() {
			@Override public void onSwipe( int delta ){
				if( isDestroyed() ) return;
				loadDelta( delta );
			}
			
			@Override
			public void onMove( final float bitmap_w, final float bitmap_h, final float tx, final float ty, final float scale ){
				App1.app_state.handler.post( new Runnable() {
					@Override public void run(){
						if( isDestroyed() ) return;
						if( tvStatus.getVisibility() == View.VISIBLE ){
							tvStatus.setText( getString( R.string.zooming_of,(int) bitmap_w, (int)bitmap_h, scale ) );
						}
					}
				} );
			}
		} );
		
		exoPlayer = ExoPlayerFactory.newSimpleInstance( this, new DefaultTrackSelector() );
		exoPlayer.addListener( player_listener );
		
		exoView.setPlayer( exoPlayer );
	}
	
	void loadDelta( int delta ){
		if( media_list.size() < 2 ) return;
		int size = media_list.size();
		idx = ( idx + size + delta ) % size;
		load();
	}
	
	void load(){
		
		exoPlayer.stop();
		pbvImage.setVisibility( View.GONE );
		exoView.setVisibility( View.GONE );
		tvError.setVisibility( View.GONE );
		svDescription.setVisibility( View.GONE );
		tvStatus.setVisibility( View.GONE );
		
		if( media_list == null
			|| media_list.isEmpty()
			|| idx < 0
			|| idx >= media_list.size()
			){
			showError( getString( R.string.media_attachment_empty ) );
			return;
		}
		
		TootAttachment ta = media_list.get( idx );
		
		if( ! TextUtils.isEmpty( ta.description ) ){
			svDescription.setVisibility( View.VISIBLE );
			tvDescription.setText( ta.description );
		}
		
		if( TootAttachment.TYPE_IMAGE.equals( ta.type ) ){
			loadBitmap( ta );
		}else if( TootAttachment.TYPE_VIDEO.equals( ta.type ) || TootAttachment.TYPE_GIFV.equals( ta.type ) ){
			loadVideo( ta );
		}else{
			// maybe TYPE_UNKNOWN
			showError( getString( R.string.media_attachment_type_error, ta.type ) );
		}
	}
	
	void showError( @NonNull String message ){
		exoView.setVisibility( View.GONE );
		pbvImage.setVisibility( View.GONE );
		tvError.setVisibility( View.VISIBLE );
		tvError.setText( message );
		
	}
	
	@SuppressLint("StaticFieldLeak")
	void loadVideo( TootAttachment ta ){
		
		final String url = ta.getLargeUrl( App1.pref );
		if( url == null ){
			showError( "missing media attachment url." );
			return;
		}
		
		exoView.setVisibility( View.VISIBLE );
		
		DefaultBandwidthMeter defaultBandwidthMeter = new DefaultBandwidthMeter();
		ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
		
		DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(
			this
			, Util.getUserAgent( this, getString( R.string.app_name ) )
			, defaultBandwidthMeter
		);
		
		MediaSource mediaSource = new ExtractorMediaSource( Uri.parse( url )
			, dataSourceFactory
			, extractorsFactory
			, App1.getAppState( this ).handler
			, new ExtractorMediaSource.EventListener() {
			@Override public void onLoadError( IOException error ){
				showError( Utils.formatError( error, "load error." ) );
			}
		}
		);
		exoPlayer.prepare( mediaSource );
		exoPlayer.setPlayWhenReady( true );
		if( TootAttachment.TYPE_GIFV.equals( ta.type ) ){
			exoPlayer.setRepeatMode( Player.REPEAT_MODE_ALL );
		}else{
			exoPlayer.setRepeatMode( Player.REPEAT_MODE_OFF );
			
		}
	}
	
	@SuppressLint("StaticFieldLeak")
	void loadBitmap( TootAttachment ta ){
		final String url = ta.getLargeUrl( App1.pref );
		if( url == null ){
			showError( "missing media attachment url." );
			return;
		}
		
		tvStatus.setVisibility( View.VISIBLE );
		tvStatus.setText( null );
		
		pbvImage.setVisibility( View.VISIBLE );
		pbvImage.setBitmap( null );
		
		new TootApiTask( this, TootApiTask.PROGRESS_HORIZONTAL ) {
			
			private final BitmapFactory.Options options = new BitmapFactory.Options();
			
			private Bitmap decodeBitmap( byte[] data, @SuppressWarnings("SameParameterValue") int pixel_max ){
				options.inJustDecodeBounds = true;
				options.inScaled = false;
				options.outWidth = 0;
				options.outHeight = 0;
				BitmapFactory.decodeByteArray( data, 0, data.length, options );
				int w = options.outWidth;
				int h = options.outHeight;
				if( w <= 0 || h <= 0 ){
					log.e( "can't decode bounds." );
					return null;
				}
				int bits = 0;
				while( w > pixel_max || h > pixel_max ){
					++ bits;
					w >>= 1;
					h >>= 1;
				}
				options.inJustDecodeBounds = false;
				options.inSampleSize = 1 << bits;
				return BitmapFactory.decodeByteArray( data, 0, data.length, options );
			}
			
			@NonNull TootApiResult getHttpCached( @NonNull String url ){
				Response response;
				
				try{
					okhttp3.Request request = new okhttp3.Request.Builder()
						.url( url )
						.cacheControl( App1.CACHE_5MIN )
						.build();
					
					publishApiProgress( getString( R.string.request_api, request.method(), url ) );
					Call call = App1.ok_http_client2.newCall( request );
					response = call.execute();
				}catch( Throwable ex ){
					return new TootApiResult( Utils.formatError( ex, "network error." ) );
				}
				
				if( ! response.isSuccessful() ){
					return new TootApiResult( Utils.formatResponse( response, "response error" ) );
				}
				
				try{
					//noinspection ConstantConditions
					data = ProgressResponseBody.bytes( response, new ProgressResponseBody.Callback() {
						@Override public void progressBytes( long bytesRead, long bytesTotal ){
							// 50MB以上のデータはキャンセルする
							if( Math.max( bytesRead, bytesTotal ) >= 50000000 ){
								throw new RuntimeException( "media attachment is larger than 50000000" );
							}
							publishApiProgressRatio( (int) bytesRead, (int) bytesTotal );
						}
					} );
					
					return new TootApiResult( "" );
				}catch( Throwable ex ){
					return new TootApiResult( Utils.formatError( ex, "content error." ) );
				}
			}
			
			byte[] data;
			Bitmap bitmap;
			
			@Override protected TootApiResult doInBackground( Void... voids ){
				TootApiResult result = getHttpCached( url );
				if( data == null ) return result;
				publishApiProgress( "image decoding.." );
				this.bitmap = decodeBitmap( data, 2048 );
				if( bitmap == null ) return new TootApiResult( "image decode failed." );
				return result;
			}
			
			@Override protected void handleResult( @Nullable TootApiResult result ){
				if( bitmap != null ){
					pbvImage.setBitmap( bitmap );
					return;
				}
				if( result != null ) Utils.showToast( ActMediaViewer.this, true, result.error );
			}
		}.executeOnExecutor( App1.task_executor );
		
	}
	
	@Override public void onClick( View v ){
		try{
			switch( v.getId() ){
			
			case R.id.btnPrevious:
				loadDelta( - 1 );
				break;
			case R.id.btnNext:
				loadDelta( + 1 );
				break;
			case R.id.btnDownload:
				download( media_list.get( idx ) );
				break;
			
			//			case R.id.btnBrowser:
			//				share( Intent.ACTION_VIEW, media_list.get( idx ) );
			//				break;
			//			case R.id.btnShare:
			//				share( Intent.ACTION_SEND, media_list.get( idx ) );
			//				break;
			//			case R.id.btnCopy:
			//				copy( media_list.get( idx ) );
			//				break;
			
			case R.id.btnMore:
				more( media_list.get( idx ) );
				break;
			}
		}catch( Throwable ex ){
			Utils.showToast( this, ex, "action failed." );
		}
	}
	
	static class DownloadHistory {
		final long time;
		@NonNull final String url;
		
		DownloadHistory( long time, @NonNull String url ){
			this.time = time;
			this.url = url;
		}
	}
	
	static final LinkedList< DownloadHistory > download_history_list = new LinkedList<>();
	static final long DOWNLOAD_REPEAT_EXPIRE = 3000L;
	
	void download( @NonNull TootAttachment ta ){
		
		int permissionCheck = ContextCompat.checkSelfPermission( this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE );
		if( permissionCheck != PackageManager.PERMISSION_GRANTED ){
			preparePermission();
			return;
		}
		
		DownloadManager downLoadManager = (DownloadManager) getSystemService( DOWNLOAD_SERVICE );
		if( downLoadManager == null ){
			Utils.showToast( this, false, "download manager is not on your device." );
			return;
		}
		
		String url = ta.getLargeUrl( App1.pref );
		if( url == null ) return;
		
		// ボタン連打対策
		{
			long now = SystemClock.elapsedRealtime();
			
			// 期限切れの履歴を削除
			Iterator< DownloadHistory > it = download_history_list.iterator();
			while( it.hasNext() ){
				DownloadHistory dh = it.next();
				if( now - dh.time >= DOWNLOAD_REPEAT_EXPIRE ){
					// この履歴は十分に古いので捨てる
					it.remove();
				}else if( url.equals( dh.url ) ){
					// 履歴に同じURLがあればエラーとする
					Utils.showToast( this, false, R.string.dont_repeat_download_to_same_url );
					return;
				}
			}
			// 履歴の末尾に追加(履歴は古い順に並ぶ)
			download_history_list.addLast( new DownloadHistory( now, url ) );
		}
		
		String fileName = null;
		
		try{
			List< String > pathSegments = Uri.parse( url ).getPathSegments();
			if( pathSegments != null ){
				int size = pathSegments.size();
				for( int i = size - 1 ; i >= 0 ; -- i ){
					String s = pathSegments.get( i );
					if( ! TextUtils.isEmpty( s ) ){
						fileName = s;
						break;
					}
				}
			}
		}catch( Throwable ex ){
			log.trace( ex );
		}
		
		if( fileName == null ){
			fileName = url
				.replaceFirst( "https?://", "" )
				.replaceAll( "[^.\\w\\d]+", "-" );
		}
		if( fileName.length() >= 20 ) fileName = fileName.substring( fileName.length() - 20 );
		
		DownloadManager.Request request = new DownloadManager.Request( Uri.parse( url ) );
		request.setDestinationInExternalPublicDir( Environment.DIRECTORY_DOWNLOADS, fileName );
		request.setTitle( fileName );
		request.setAllowedNetworkTypes( DownloadManager.Request.NETWORK_MOBILE | DownloadManager.Request.NETWORK_WIFI );
		//メディアスキャンを許可する
		request.allowScanningByMediaScanner();
		
		//ダウンロード中・ダウンロード完了時にも通知を表示する
		request.setNotificationVisibility( DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED );
		
		downLoadManager.enqueue( request );
		Utils.showToast( this, false, R.string.downloading );
	}
	
	void share( String action, @NonNull TootAttachment ta ){
		String url = ta.getLargeUrl( App1.pref );
		if( url == null ) return;
		
		try{
			Intent intent = new Intent( action );
			intent.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK );
			if( action.equals( Intent.ACTION_SEND ) ){
				intent.setType( "text/plain" );
				intent.putExtra( Intent.EXTRA_TEXT, url );
			}else{
				intent.setData( Uri.parse( url ) );
			}
			
			startActivity( intent );
		}catch( Throwable ex ){
			Utils.showToast( this, ex, "can't open app." );
		}
		
	}
	
	void copy( @NonNull TootAttachment ta ){
		String url = ta.getLargeUrl( App1.pref );
		if( url == null ) return;
		
		ClipboardManager cm = (ClipboardManager) getSystemService( CLIPBOARD_SERVICE );
		if( cm == null ){
			Utils.showToast( this, false, "can't access to ClipboardManager" );
			return;
		}
		
		try{
			//クリップボードに格納するItemを作成
			ClipData.Item item = new ClipData.Item( url );
			
			String[] mimeType = new String[ 1 ];
			mimeType[ 0 ] = ClipDescription.MIMETYPE_TEXT_PLAIN;
			
			//クリップボードに格納するClipDataオブジェクトの作成
			ClipData cd = new ClipData( new ClipDescription( "media URL", mimeType ), item );
			
			//クリップボードにデータを格納
			cm.setPrimaryClip( cd );
			
			Utils.showToast( this, false, R.string.url_is_copied );
			
		}catch( Throwable ex ){
			Utils.showToast( this, ex, "clipboard access failed." );
		}
		
	}
	
	void more( @NonNull TootAttachment ta ){
		ActionsDialog ad = new ActionsDialog();
		
		ad.addAction( getString( R.string.open_in_browser ), new Runnable() {
			@Override public void run(){
				share( Intent.ACTION_VIEW, media_list.get( idx ) );
			}
		} );
		ad.addAction( getString( R.string.share_url ), new Runnable() {
			@Override public void run(){
				share( Intent.ACTION_SEND, media_list.get( idx ) );
			}
		} );
		ad.addAction( getString( R.string.copy_url ), new Runnable() {
			@Override public void run(){
				copy( media_list.get( idx ) );
			}
		} );
		
		addMoreMenu( ad, "url", ta.url, Intent.ACTION_VIEW );
		addMoreMenu( ad, "remote_url", ta.remote_url, Intent.ACTION_VIEW );
		addMoreMenu( ad, "preview_url", ta.preview_url, Intent.ACTION_VIEW );
		addMoreMenu( ad, "text_url", ta.text_url, Intent.ACTION_VIEW );
		
		ad.show( this, null );
	}
	
	void addMoreMenu( ActionsDialog ad, String caption_prefix, final String url, final String action ){
		if( TextUtils.isEmpty( url ) ) return;
		
		String caption = getString( R.string.open_browser_of, caption_prefix );
		
		ad.addAction( caption, new Runnable() {
			@Override public void run(){
				try{
					Intent intent = new Intent( action, Uri.parse( url ) );
					intent.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK );
					startActivity( intent );
				}catch( Throwable ex ){
					Utils.showToast( ActMediaViewer.this, ex, "can't open app." );
				}
			}
		} );
	}
	
	long buffering_last_shown;
	static final long short_limit = 5000L;
	
	private final Player.EventListener player_listener = new Player.EventListener() {
		@Override public void onTimelineChanged( Timeline timeline, Object manifest ){
			log.d( "exoPlayer onTimelineChanged" );
		}
		
		@Override
		public void onTracksChanged( TrackGroupArray trackGroups, TrackSelectionArray trackSelections ){
			log.d( "exoPlayer onTracksChanged" );
			
		}
		
		@Override public void onLoadingChanged( boolean isLoading ){
			// かなり頻繁に呼ばれる
			// log.d( "exoPlayer onLoadingChanged %s" ,isLoading );
		}
		
		@Override public void onPlayerStateChanged( boolean playWhenReady, int playbackState ){
			// かなり頻繁に呼ばれる
			// log.d( "exoPlayer onPlayerStateChanged %s %s", playWhenReady, playbackState );
			if( playWhenReady && playbackState == Player.STATE_BUFFERING ){
				long now = SystemClock.elapsedRealtime();
				if( now - buffering_last_shown >= short_limit && exoPlayer.getDuration() >= short_limit ){
					buffering_last_shown = now;
					Utils.showToast( ActMediaViewer.this, false, R.string.video_buffering );
				}
				/*
					exoPlayer.getDuration() may returns negative value (TIME_UNSET ,same as Long.MIN_VALUE + 1).
				*/
			}
		}
		
		@Override public void onRepeatModeChanged( int repeatMode ){
			log.d( "exoPlayer onRepeatModeChanged %d", repeatMode );
		}
		
		@Override public void onPlayerError( ExoPlaybackException error ){
			log.d( "exoPlayer onPlayerError" );
			Utils.showToast( ActMediaViewer.this, error, "player error." );
		}
		
		@Override public void onPositionDiscontinuity(){
			log.d( "exoPlayer onPositionDiscontinuity" );
		}
		
		@Override
		public void onPlaybackParametersChanged( PlaybackParameters playbackParameters ){
			log.d( "exoPlayer onPlaybackParametersChanged" );
			
		}
	};
	
	private static final int PERMISSION_REQUEST_CODE = 1;
	
	private void preparePermission(){
		if( Build.VERSION.SDK_INT >= 23 ){
			ActivityCompat.requestPermissions( this
				, new String[]{
					Manifest.permission.WRITE_EXTERNAL_STORAGE,
				}
				, PERMISSION_REQUEST_CODE
			);
		}else{
			Utils.showToast( this, true, R.string.missing_permission_to_access_media );
		}
	}
	
	@Override public void onRequestPermissionsResult(
		int requestCode
		, @NonNull String permissions[]
		, @NonNull int[] grantResults
	){
		switch( requestCode ){
		case PERMISSION_REQUEST_CODE:
			boolean bNotGranted = false;
			for( int i = 0, ie = permissions.length ; i < ie ; ++ i ){
				if( grantResults[ i ] != PackageManager.PERMISSION_GRANTED ){
					bNotGranted = true;
				}
			}
			if( bNotGranted ){
				Utils.showToast( this, true, R.string.missing_permission_to_access_media );
			}else{
				download( media_list.get( idx ) );
			}
			break;
		}
	}
}