package jp.juggler.subwaytooter.api;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.lang.ref.WeakReference;
import java.text.NumberFormat;

import jp.juggler.subwaytooter.table.SavedAccount;

/*
	AsyncTask customized version:
	 - avoid warning from android lint.
	 - has ProgressDialog.
	 - has TootApiClient.
	 - pass progress message from TootApiClient to ProgressDialog.
*/
@SuppressWarnings("FieldCanBeLocal")
public abstract class TootApiTask extends AsyncTask< Void, Void, TootApiResult > implements TootApiClient.Callback {
	
	@SuppressWarnings("WeakerAccess") public static final int PROGRESS_NONE = - 1;
	@SuppressWarnings("WeakerAccess") public static final int PROGRESS_SPINNER = ProgressDialog.STYLE_SPINNER;
	@SuppressWarnings("WeakerAccess") public static final int PROGRESS_HORIZONTAL = ProgressDialog.STYLE_HORIZONTAL;
	
	private static class ProgressInfo {
		
		// HORIZONTALスタイルの場合、初期メッセージがないと後からメッセージを指定しても表示されない
		@NonNull String message = " ";
		
		boolean isIndeterminate = true;
		int value = 0;
		int max = 1;
	}
	
	@NonNull private WeakReference< Activity > refActivity;
	@NonNull protected Handler handler;
	@NonNull protected final TootApiClient client;
	@NonNull private final ProgressInfo info = new ProgressInfo();
	@Nullable private ProgressDialog progress;
	@Nullable private String progress_prefix;
	
	private final int progress_style;
	private boolean isAlive = true;
	private long last_message_shown;
	
	private static final NumberFormat percent_format;
	
	static{
		percent_format = NumberFormat.getPercentInstance();
		percent_format.setMaximumFractionDigits( 0 );
	}
	
	@SuppressWarnings("WeakerAccess")
	public TootApiTask( @NonNull Activity _activity, int progress_style ){
		this.refActivity = new WeakReference<>( _activity );
		this.handler = new Handler();
		this.client = new TootApiClient( _activity, this );
		this.progress_style = progress_style;
		if( progress_style != PROGRESS_NONE ){
			// ダイアログの遅延表示を実装したけど、すぐにダイアログを出した方が下のUIのタッチ判定を隠せて良いので使わないんだ…
			// handler.postDelayed( proc_progress_opener ,1000L );
			proc_progress_opener.run();
		}
	}
	
	@SuppressWarnings("WeakerAccess")
	public TootApiTask( @NonNull Activity _activity, SavedAccount access_info, int progress_style ){
		this( _activity, progress_style );
		client.setAccount( access_info );
	}
	
	@SuppressWarnings("WeakerAccess")
	public TootApiTask( @NonNull Activity _activity, String instance, int progress_style ){
		this( _activity, progress_style );
		client.setInstance( instance );
	}
	
	private static int getDefaultProgressStyle( boolean bShowProgress ){
		return bShowProgress ? PROGRESS_SPINNER : PROGRESS_NONE;
	}
	
	@SuppressWarnings("WeakerAccess")
	public TootApiTask( @NonNull Activity _activity, boolean bShowProgress ){
		this( _activity, getDefaultProgressStyle( bShowProgress ) );
	}
	
	@SuppressWarnings("WeakerAccess")
	public TootApiTask( @NonNull Activity _activity, SavedAccount access_info, boolean bShowProgress ){
		this( _activity, access_info, getDefaultProgressStyle( bShowProgress ) );
	}
	
	@SuppressWarnings("WeakerAccess")
	public TootApiTask( @NonNull Activity _activity, String instance, boolean bShowProgress ){
		this( _activity, instance, getDefaultProgressStyle( bShowProgress ) );
	}
	
	public TootApiTask setProgressPrefix( String s ){
		this.progress_prefix = s;
		return this;
	}
	
	//////////////////////////////////////////////////////
	// implements AsyncTask
	
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
	// implements TootApiClient.Callback
	
	@Override public boolean isApiCancelled(){
		return isCancelled();
	}
	
	@Override public void publishApiProgress( final String s ){
		synchronized( this ){
			info.message = s;
			info.isIndeterminate = true;
		}
		requestShowMessage();
	}
	
	//////////////////////////////////////////////////////
	// 内蔵メディアビューアのローディング進捗の表示に使う
	
	public void publishApiProgressRatio( final int value, final int max ){
		synchronized( this ){
			info.isIndeterminate = false;
			info.value = value;
			info.max = max;
		}
		requestShowMessage();
	}
	
	//////////////////////////////////////////////////////
	
	// ダイアログを閉じる
	private void dismissProgress(){
		synchronized( this ){
			isAlive = false;
		}
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
	
	// ダイアログを開く
	private final Runnable proc_progress_opener = new Runnable() {
		@Override public void run(){
			synchronized( this ){
				Activity activity = refActivity.get();
				if( isAlive
					&& activity != null
					&& progress == null
					&& progress_style != PROGRESS_NONE
					){
					//noinspection deprecation
					progress = new ProgressDialog( activity );
					progress.setCancelable( true );
					progress.setOnCancelListener( new DialogInterface.OnCancelListener() {
						@Override public void onCancel( DialogInterface dialog ){
							TootApiTask.this.cancel( true );
						}
					} );
					progress.setProgressStyle( progress_style );
					showProgressMessage();
					progress.show();
				}
			}
		}
	};
	
	// ダイアログのメッセージを更新する
	private void showProgressMessage(){
		if( progress == null ) return;
		
		if( ! TextUtils.isEmpty( progress_prefix ) ){
			progress.setMessage( progress_prefix + ( TextUtils.isEmpty( info.message.trim() ) ? "" : "\n" + info.message ) );
		}else{
			progress.setMessage( info.message );
		}
		
		progress.setIndeterminate( info.isIndeterminate );
		if( info.isIndeterminate ){
			progress.setProgressNumberFormat( null );
			progress.setProgressPercentFormat( null );
		}else{
			progress.setProgress( info.value );
			progress.setMax( info.max );
			progress.setProgressNumberFormat( "%1$,d / %2$,d" );
			progress.setProgressPercentFormat( percent_format );
		}
		
		last_message_shown = SystemClock.elapsedRealtime();
	}

	// 少し後にダイアログのメッセージを更新する
	// あまり頻繁に更新せず、しかし繰り返し呼ばれ続けても時々は更新したい
	private void requestShowMessage(){
		long wait = 100L + last_message_shown - SystemClock.elapsedRealtime();
		wait = wait < 0L ? 0L : wait > 100L ? 100L : wait;
		handler.removeCallbacks( proc_progress_message );
		handler.postDelayed( proc_progress_message, wait );
	}
	
	private final Runnable proc_progress_message = new Runnable() {
		@Override public void run(){
			synchronized( this ){
				if( progress != null && progress.isShowing() ){
					showProgressMessage();
				}
			}
		}
	};
	
}
