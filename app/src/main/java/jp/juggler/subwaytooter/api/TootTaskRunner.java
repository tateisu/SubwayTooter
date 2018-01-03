package jp.juggler.subwaytooter.api;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.lang.ref.WeakReference;
import java.text.NumberFormat;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.table.SavedAccount;

/*
	非同期タスク(TootTask)を実行します。
	- 内部でAsyncTaskを使います。Android Lintの警告を抑制します
	- ProgressDialogを表示します。抑制することも可能です。
	- TootApiClientの初期化を行います
	- TootApiClientからの進捗イベントをProgressDialogに伝達します。
*/

public class TootTaskRunner extends TootApiClient.Callback {
	
	@SuppressWarnings("WeakerAccess") public static final int PROGRESS_NONE = - 1;
	@SuppressWarnings("WeakerAccess") public static final int PROGRESS_SPINNER = ProgressDialog.STYLE_SPINNER;
	@SuppressWarnings("WeakerAccess") public static final int PROGRESS_HORIZONTAL = ProgressDialog.STYLE_HORIZONTAL;
	
	private static int getDefaultProgressStyle( boolean bShowProgress ){
		return bShowProgress ? PROGRESS_SPINNER : PROGRESS_NONE;
	}
	
	private static class ProgressInfo {
		
		// HORIZONTALスタイルの場合、初期メッセージがないと後からメッセージを指定しても表示されない
		@NonNull String message = " ";
		
		boolean isIndeterminate = true;
		int value = 0;
		int max = 1;
	}
	
	@NonNull protected final Handler handler;
	@NonNull protected final TootApiClient client;
	@NonNull private final MyTask task;
	@NonNull protected final ProgressInfo info = new ProgressInfo();
	@Nullable private ProgressDialog progress;
	@Nullable private String progress_prefix;
	
	@NonNull private final WeakReference<Context> refContext;
	private final int progress_style;
	
	private TootTask callback;
	private long last_message_shown;
	
	private static final NumberFormat percent_format;
	
	static{
		percent_format = NumberFormat.getPercentInstance();
		percent_format.setMaximumFractionDigits( 0 );
	}
	
	public TootTaskRunner( @NonNull Context context ){
		this( context,PROGRESS_NONE);
	}

	public TootTaskRunner( @NonNull Context context, boolean bShowProgress ){
		this( context, getDefaultProgressStyle( bShowProgress ) );
	}

	public TootTaskRunner( @NonNull Context context, int progress_style ){
		this.refContext = new WeakReference<>( context );
		this.progress_style = progress_style;
		this.handler = new Handler();
		this.client = new TootApiClient( context, this );
		this.task = new MyTask(this);
	}

	public void run( @NonNull TootTask callback){
		openProgress();
		
		this.callback = callback;
		
		task.executeOnExecutor( App1.task_executor );
	}

	public void run( @NonNull SavedAccount access_info,@NonNull TootTask callback){
		client.setAccount( access_info );
		run(callback);
	}

	public void run( @NonNull String instance,@NonNull TootTask callback){
		client.setInstance( instance );
		run(callback);
	}
	
	public TootTaskRunner progressPrefix( String s ){
		this.progress_prefix = s;
		return this;
	}
	
	//////////////////////////////////////////////////////
	// has AsyncTask

	protected static class MyTask extends AsyncTask< Void, Void, TootApiResult >{
		
		private TootTaskRunner runner;

		MyTask(TootTaskRunner runner){
			this.runner = runner;
		}

		@Override protected TootApiResult doInBackground( Void... voids ){
			return runner.callback.background( runner.client );
		}
		
		@Override protected final void onCancelled( TootApiResult result ){
			onPostExecute( result );
		}
		
		@Override protected final void onPostExecute( TootApiResult result ){
			runner.dismissProgress();
			runner.callback.handleResult( result );
		}
	}
	
	//////////////////////////////////////////////////////
	// implements TootApiClient.Callback
	
	@Override public boolean isApiCancelled(){
		return task.isCancelled();
	}
	
	@Override public void publishApiProgress( @NonNull final String s ){
		synchronized( this ){
			info.message = s;
			info.isIndeterminate = true;
		}
		delayProgressMessage();
	}
	
	@Override public void publishApiProgressRatio( final int value, final int max ){
		synchronized( this ){
			info.isIndeterminate = false;
			info.value = value;
			info.max = max;
		}
		delayProgressMessage();
	}
	
	//////////////////////////////////////////////////////
	// ProgressDialog
	
	private void openProgress(){
		// open progress
		if( progress_style != PROGRESS_NONE ){
			Context context = refContext.get();
			if( context != null && context instanceof Activity ){
				//noinspection deprecation
				progress = new ProgressDialog( context );
				progress.setCancelable( true );
				progress.setOnCancelListener( new DialogInterface.OnCancelListener() {
					@Override public void onCancel( DialogInterface dialog ){
						task.cancel( true );
					}
				} );
				progress.setProgressStyle( progress_style );
				showProgressMessage();
				progress.show();
			}
		}
	}
	
	// ダイアログを閉じる
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
			}finally{
				progress = null;
			}
		}
	}
	
	// ダイアログのメッセージを更新する
	// 初期化時とメッセージ更新時に呼ばれる
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
	// どのスレッドから呼ばれるか分からない
	private void delayProgressMessage(){
		long wait = 100L + last_message_shown - SystemClock.elapsedRealtime();
		wait = wait < 0L ? 0L : wait > 100L ? 100L : wait;

		synchronized( this ){
			handler.removeCallbacks( proc_progress_message );
			handler.postDelayed( proc_progress_message, wait );
		}
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
