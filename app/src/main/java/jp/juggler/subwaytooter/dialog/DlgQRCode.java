package jp.juggler.subwaytooter.dialog;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v4.os.AsyncTaskCompat;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import jp.juggler.subwaytooter.ActMain;
import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.util.Utils;

import net.glxn.qrgen.android.QRCode;

public class DlgQRCode {
	
	interface QrCodeCallback{
		void onQrCode(Bitmap bitmap);
	}
	
	static void makeQrCode( final ActMain activity,final int size,final String url ,final QrCodeCallback callback){
		final ProgressDialog progress = new ProgressDialog( activity );
		final AsyncTask<Void,Void,Bitmap> task = new AsyncTask< Void, Void, Bitmap >() {
			@Override protected Bitmap doInBackground( Void... params ){
				try{
					return QRCode.from(url).withSize( size,size ).bitmap();
				}catch(Throwable ex){
					ex.printStackTrace(  );
					Utils.showToast( activity,ex,"makeQrCode failed." );
					return null;
				}
			}
			
			@Override protected void onCancelled( Bitmap result ){
				super.onCancelled( result );
			}
			@Override protected void onPostExecute( Bitmap result ){
				progress.dismiss();
				if( result!=null){
					callback.onQrCode( result );
				}
			}
			
		};
		progress.setIndeterminate( true );
		progress.setCancelable( true );
		progress.setMessage( activity.getString(R.string.generating_qr_code) );
		progress.setOnCancelListener( new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel( DialogInterface dialog ){
				task.cancel( true );
			}
		} );
		progress.show();
		
		task.executeOnExecutor( App1.task_executor);
	}
	
	public static void open( @NonNull final ActMain activity ,final CharSequence message,final String url ){
		
		int size = (int)(0.5f + 240f * activity.density );
		makeQrCode( activity, size, url, new QrCodeCallback() {
			@Override public void onQrCode( final Bitmap bitmap ){
				
				@SuppressLint("InflateParams") View viewRoot = activity.getLayoutInflater().inflate( R.layout.dlg_qr_code, null, false );
				final Dialog dialog = new Dialog( activity );
				dialog.setContentView( viewRoot );
				dialog.setCancelable( true );
				dialog.setCanceledOnTouchOutside( true );
				
				TextView tv = (TextView) viewRoot.findViewById( R.id.tvMessage );
				tv.setText( message );
				
				tv = (TextView) viewRoot.findViewById( R.id.tvUrl );
				tv.setText( "[ "+url+" ]" ); // なぜか素のURLだと@以降が表示されない
				
				final ImageView iv = (ImageView) viewRoot.findViewById( R.id.ivQrCode );
				iv.setImageBitmap( bitmap );
				
				dialog.setOnDismissListener( new DialogInterface.OnDismissListener() {
					@Override public void onDismiss( DialogInterface dialog ){
						iv.setImageDrawable( null );
						bitmap.recycle();
					}
				} );
				
				viewRoot.findViewById( R.id.btnCancel ).setOnClickListener( new View.OnClickListener() {
					@Override public void onClick( View v ){
						dialog.cancel();
					}
				} );
				
				dialog.show();
				
			}
		});
		
	}
	
}
