package jp.juggler.subwaytooter;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.TootApiTask;
import jp.juggler.subwaytooter.api.entity.TootAttachment;
import jp.juggler.subwaytooter.dialog.ActionsDialog;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;
import jp.juggler.subwaytooter.view.PinchBitmapView;
import okhttp3.Call;
import okhttp3.Response;

public class ActMediaViewer extends AppCompatActivity implements View.OnClickListener {
	
	static final LogCategory log = new LogCategory( "ActMediaViewer" );
	
	static String encodeMediaList( @Nullable TootAttachment.List list ){
		JSONArray a = new JSONArray();
		if( list != null ){
			for( TootAttachment ta : list ){
				try{
					JSONObject item = ta.encodeJSON();
					a.put( item );
				}catch( JSONException ex ){
					log.e( ex, "encode failed." );
				}
			}
		}
		return a.toString();
	}
	
	static TootAttachment.List decodeMediaList( @Nullable String src ){
		TootAttachment.List dst_list = new TootAttachment.List();
		if( src != null ){
			try{
				JSONArray a = new JSONArray( src );
				for( int i = 0, ie = a.length() ; i < ie ; ++ i ){
					JSONObject obj = a.optJSONObject( i );
					TootAttachment ta = TootAttachment.parse( obj );
					if( ta != null ) dst_list.add( ta );
				}
			}catch( Throwable ex ){
				log.e( ex, "decodeMediaList failed." );
			}
		}
		return dst_list;
	}
	
	static final String EXTRA_IDX = "idx";
	static final String EXTRA_DATA = "data";
	
	public static void open( @NonNull ActMain activity, @NonNull TootAttachment.List list, int idx ){
		Intent intent = new Intent( activity, ActMediaViewer.class );
		intent.putExtra( EXTRA_IDX, idx );
		JSONArray a = new JSONArray();
		for( TootAttachment ta : list ){
			try{
				JSONObject item = ta.encodeJSON();
				a.put( item );
			}catch( JSONException ex ){
				log.e( ex, "encode failed." );
			}
		}
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
		App1.setActivityTheme( this, true );
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
	
	void initUI(){
		setContentView( R.layout.act_media_viewer );
		pbvImage = findViewById( R.id.pbvImage );
		btnPrevious = findViewById( R.id.btnPrevious );
		btnNext = findViewById( R.id.btnNext );
		exoView = findViewById( R.id.exoView );
		
		tvError = findViewById( R.id.tvError );
		
		boolean enablePaging = media_list.size() > 1;
		btnPrevious.setEnabled( enablePaging );
		btnNext.setEnabled( enablePaging );
		btnPrevious.setAlpha( enablePaging ? 1f : 0.3f );
		btnNext.setAlpha( enablePaging ? 1f : 0.3f );
		
		btnPrevious.setOnClickListener( this );
		btnNext.setOnClickListener( this );
		findViewById( R.id.btnDownload ).setOnClickListener( this );
		findViewById( R.id.btnMore ).setOnClickListener( this );
		
		//		findViewById( R.id.btnBrowser ).setOnClickListener( this );
		//		findViewById( R.id.btnShare ).setOnClickListener( this );
		//		findViewById( R.id.btnCopy ).setOnClickListener( this );
		
		pbvImage.setCallback( new PinchBitmapView.Callback() {
			@Override public void onSwipe( int delta ){
				if( isDestroyed() ) return;
				loadDelta( delta );
			}
		} );
		
		exoPlayer = ExoPlayerFactory.newSimpleInstance( this, new DefaultTrackSelector());

		exoView.setPlayer( exoPlayer );

		exoPlayer.addListener( new Player.EventListener() {
			@Override public void onTimelineChanged( Timeline timeline, Object manifest ){
				log.d("exoPlayer onTimelineChanged");
			}
			
			@Override
			public void onTracksChanged( TrackGroupArray trackGroups, TrackSelectionArray trackSelections ){
				log.d("exoPlayer onTracksChanged");
				
			}
			
			@Override public void onLoadingChanged( boolean isLoading ){
				log.d("exoPlayer onLoadingChanged");
			}
			
			@Override public void onPlayerStateChanged( boolean playWhenReady, int playbackState ){
				log.d("exoPlayer onPlayerStateChanged %s %s",playWhenReady,playbackState);
				
			}
			
			@Override public void onRepeatModeChanged( int repeatMode ){
				log.d("exoPlayer onRepeatModeChanged %d",repeatMode);
			}
			
			@Override public void onPlayerError( ExoPlaybackException error ){
				log.d("exoPlayer onPlayerError");
				Utils.showToast( ActMediaViewer.this,error,"player error." );
			}
			
			@Override public void onPositionDiscontinuity(){
				log.d("exoPlayer onPositionDiscontinuity");
			}
			
			@Override
			public void onPlaybackParametersChanged( PlaybackParameters playbackParameters ){
				log.d("exoPlayer onPlaybackParametersChanged");
				
			}
		} );
	}
	
	
	void loadDelta( int delta ){
		if( media_list.size() < 2 ) return;
		int size = media_list.size();
		idx = ( idx + size + delta ) % size;
		load();
	}
	
	
	void load(){
		
		exoPlayer.stop();

		if( media_list.isEmpty() ){
			pbvImage.setVisibility( View.GONE );
			exoView.setVisibility( View.GONE );
			tvError.setVisibility( View.VISIBLE );
			tvError.setText( R.string.media_attachment_empty );
			return;
		}

		TootAttachment ta = media_list.get( idx );
		
		// TODO ta.description をどこかに表示する

		if( TootAttachment.TYPE_IMAGE.equals( ta.type )){
			loadBitmap(ta);
		}else if( TootAttachment.TYPE_VIDEO.equals( ta.type ) || TootAttachment.TYPE_GIFV.equals( ta.type ) ){
			pbvImage.setVisibility( View.GONE );
			loadVideo(ta);
		}else{
			// maybe TYPE_UNKNOWN
			showError(  getString( R.string.media_attachment_type_error, ta.type )  );
		}
	}
	
	void showError(@NonNull String message){
		exoView.setVisibility( View.GONE );
		pbvImage.setVisibility( View.GONE );
		tvError.setVisibility( View.VISIBLE );
		tvError.setText( message );

	}
	
	@SuppressLint("StaticFieldLeak")
	void loadVideo(TootAttachment ta){
		
		final String url = ta.getLargeUrl( App1.pref );
		if( url == null ){
			showError( "missing media attachment url.");
			return;
		}
		
		tvError.setVisibility( View.GONE );
		pbvImage.setVisibility( View.GONE );
		exoView.setVisibility( View.VISIBLE );
		
		DefaultBandwidthMeter defaultBandwidthMeter =new DefaultBandwidthMeter();
		ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
		
		DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(
			this
			, Util.getUserAgent(this, getString(R.string.app_name))
			, defaultBandwidthMeter
		);
		
		MediaSource mediaSource = new ExtractorMediaSource( Uri.parse( url )
			, dataSourceFactory
			, extractorsFactory
			, App1.getAppState( this ).handler
			, new ExtractorMediaSource.EventListener() {
				@Override public void onLoadError( IOException error ){
					showError( Utils.formatError( error,"load error." ));
				}
			}
		);
		exoPlayer.prepare(mediaSource);
		exoPlayer.setPlayWhenReady( true);
		if( TootAttachment.TYPE_GIFV.equals( ta.type ) ){
			exoPlayer.setRepeatMode( Player.REPEAT_MODE_ALL );
		}else{
			exoPlayer.setRepeatMode( Player.REPEAT_MODE_OFF );
			
		}
	}

	@SuppressLint("StaticFieldLeak")
	void loadBitmap(TootAttachment ta){
		final String url = ta.getLargeUrl( App1.pref );
		if( url == null ){
			showError( "missing media attachment url.");
			return;
		}
		
		tvError.setVisibility( View.GONE );
		exoView.setVisibility( View.GONE );
		pbvImage.setVisibility( View.VISIBLE );
		pbvImage.setBitmap( null );
		
		new TootApiTask( this, true ) {
			
			private final BitmapFactory.Options options = new BitmapFactory.Options();
			
			private Bitmap decodeBitmap( byte[] data, @SuppressWarnings("SameParameterValue") int pixel_max ){
				publishApiProgress( "image decoding.." );
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
					data = response.body().bytes();
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
	
	void download( @NonNull TootAttachment ta ){
		
		String url = ta.getLargeUrl( App1.pref );
		if( url == null ) return;
		
		DownloadManager downLoadManager = (DownloadManager) getSystemService( DOWNLOAD_SERVICE );
		if( downLoadManager == null ){
			Utils.showToast( this, false, "download manager is not on your device." );
			return;
		}
		
		String fname = url.replaceFirst( "https?://", "" ).replaceAll( "[^.\\w\\d_-]+", "-" );
		if( fname.length() >= 20 ) fname = fname.substring( fname.length() - 20 );
		
		DownloadManager.Request request = new DownloadManager.Request( Uri.parse( url ) );
		request.setDestinationInExternalPublicDir( Environment.DIRECTORY_DOWNLOADS, fname );
		request.setTitle( fname );
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
	
}