package jp.juggler.subwaytooter.api;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.Utils;

/*
	AsyncTask customized version:
	 - avoid warning from android lint.
	 - has ProgressDialog.
	 - has TootApiClient.
	 - pass progress message from TootApiClient to ProgressDialog.
*/
public abstract class TootApiTask extends AsyncTask< Void, Void, TootApiResult > implements TootApiClient.Callback {
	
	@NonNull protected final TootApiClient client;
	
	@SuppressWarnings("WeakerAccess") public static final int PROGRESS_NONE = - 1;
	@SuppressWarnings("WeakerAccess") public static final int PROGRESS_SPINNER = ProgressDialog.STYLE_SPINNER;
	@SuppressWarnings("WeakerAccess") public static final int PROGRESS_HORIZONTAL = ProgressDialog.STYLE_HORIZONTAL;
	
	@SuppressWarnings("WeakerAccess")
	public TootApiTask( Activity _activity, SavedAccount access_info, boolean bShowProgress ){
		this( _activity, access_info, bShowProgress ? PROGRESS_SPINNER : PROGRESS_NONE );
	}
	
	@SuppressWarnings("WeakerAccess")
	public TootApiTask( Activity _activity, SavedAccount access_info, int progress_style ){
		this.client = new TootApiClient( _activity, this );
		client.setAccount( access_info );
		showProgress( _activity, progress_style );
	}
	
	@SuppressWarnings("WeakerAccess")
	public TootApiTask( Activity _activity, String instance, boolean bShowProgress ){
		this( _activity, instance, bShowProgress ? PROGRESS_SPINNER : PROGRESS_NONE );
	}
	
	@SuppressWarnings("WeakerAccess")
	public TootApiTask( Activity _activity, String instance, int progress_style ){
		this.client = new TootApiClient( _activity, this );
		client.setInstance( instance );
		showProgress( _activity, progress_style );
	}
	
	@SuppressWarnings("WeakerAccess")
	public TootApiTask( Activity _activity, boolean bShowProgress ){
		this( _activity, bShowProgress ? PROGRESS_SPINNER : PROGRESS_NONE );
	}
	
	@SuppressWarnings("WeakerAccess")
	public TootApiTask( Activity _activity, int progress_style ){
		this.client = new TootApiClient( _activity, this );
		showProgress( _activity, progress_style );
	}
	
	public TootApiTask setProgressPrefix( String s ){
		this.progress_prefix = s;
		return this;
	}
	
	//////////////////////////////////////////////////////
	
	@Override public boolean isApiCancelled(){
		return isCancelled();
	}
	
	@Override public void publishApiProgress( final String s ){
		if( progress != null ){
			Utils.runOnMainThread( new Runnable() {
				@Override public void run(){
					progress.setIndeterminate( true );
					if( ! TextUtils.isEmpty( progress_prefix ) ){
						progress.setMessage( progress_prefix + "\n" + s );
					}else{
						progress.setMessage( s );
					}
				}
			} );
		}
	}
	
	public void publishApiProgressRatio( final int value, final int max ){
		if( progress != null ){
			Utils.runOnMainThread( new Runnable() {
				@Override public void run(){
					progress.setIndeterminate( false );
					progress.setProgress( value );
					progress.setMax( max );
				}
			} );
		}
	}
	
	//////////////////////////////////////////////////////
	
	@Override protected abstract TootApiResult doInBackground( Void... voids );
	
	protected abstract void handleResult( @Nullable TootApiResult result );
	
	@Override protected final void onCancelled( TootApiResult result ){
		onPostExecute( result );
	}
	
	@Override protected final void onPostExecute( TootApiResult result ){
		dismissProgress();
		handleResult( result );
	}
	
	//////////////////////////////////////////////////////
	
	private ProgressDialog progress;
	private String progress_prefix;
	
	private void showProgress( Activity activity, int progressStyle ){
		
		if( progressStyle == PROGRESS_NONE ) return;
		
		//noinspection deprecation
		this.progress = new ProgressDialog( activity );
		progress.setCancelable( true );
		progress.setProgressStyle( progressStyle );
		progress.setIndeterminate( true );
		progress.setMax( 1 );
		if( ! TextUtils.isEmpty( progress_prefix ) ){
			progress.setMessage( progress_prefix );
		}else{
			// HORIZONTALスタイルの場合、初期メッセージがないと後からメッセージを指定しても表示されない
			progress.setMessage( " " );
		}
		progress.setOnCancelListener( new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel( DialogInterface dialog ){
				TootApiTask.this.cancel( true );
			}
		} );
		progress.show();
	}
	
	private void dismissProgress(){
		if( progress != null ){
			try{
				progress.dismiss();
			}catch( Throwable ignored ){
				// java.lang.IllegalArgumentException:
				// at android.view.WindowManagerGlobal.findViewLocked(WindowManagerGlobal.java:396)
				// at android.view.WindowManagerGlobal.removeView(WindowManagerGlobal.java:322)
				// at android.view.WindowManagerImpl.removeViewImmediate(WindowManagerImpl.java:116)
				// at android.app.Dialog.dismissDialog(Dialog.java:341)
				// at android.app.Dialog.dismiss(Dialog.java:324)
				// at jp.juggler.subwaytooter.ActMain$10$1.onPostExecute(ActMain.java:867)
				// at jp.juggler.subwaytooter.ActMain$10$1.onPostExecute(ActMain.java:837)
			}finally{
				progress = null;
			}
		}
	}
	
}
