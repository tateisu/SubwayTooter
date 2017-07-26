package jp.juggler.subwaytooter.dialog;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;

import jp.juggler.subwaytooter.ActMain;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.Utils;

import android.widget.LinearLayout;
import android.widget.TextView;

public class AccountPicker {
	
	public interface AccountPickerCallback {
		void onAccountPicked( @NonNull SavedAccount ai );
	}
	
	
	public static void pick(
		@NonNull ActMain activity
		, boolean bAllowPseudo
		, boolean bAuto
		, String message
		, @NonNull final AccountPickerCallback callback
	){
		
		ArrayList< SavedAccount > account_list = SavedAccount.loadAccountList( activity,ActMain.log );
		pick( activity, bAllowPseudo, bAuto, message, account_list, callback ,null );
	}
	
	public static void pick(
		@NonNull ActMain activity
		, boolean bAllowPseudo
		, boolean bAuto
		, String message
		, @NonNull final AccountPickerCallback callback
		, @Nullable final DialogInterface.OnDismissListener dissmiss_callback
	){
		ArrayList< SavedAccount > account_list = SavedAccount.loadAccountList( activity,ActMain.log );
		pick( activity, bAllowPseudo, bAuto, message, account_list, callback ,dissmiss_callback );
	}
	
	public static void pick(
		@NonNull ActMain activity
		, boolean bAllowPseudo
		, boolean bAuto
		, String message
		, @NonNull final ArrayList< SavedAccount > account_list
		, @NonNull final AccountPickerCallback callback
	){
		pick( activity, bAllowPseudo, bAuto, message, account_list, callback ,null );
	}
	
	public static void pick(
		@NonNull ActMain activity
		, boolean bAllowPseudo
		, boolean bAuto
		, String message
		, @NonNull final ArrayList< SavedAccount > account_list
		, @NonNull final AccountPickerCallback callback
		, @Nullable final DialogInterface.OnDismissListener dismiss_callback
	){
		if( account_list.isEmpty() ){
			Utils.showToast( activity, false, R.string.account_empty );
			return;
		}
		
		if( ! bAllowPseudo ){
			ArrayList< SavedAccount > tmp_list = new ArrayList<>();
			for( SavedAccount a : account_list ){
				if( a.isPseudo() ) continue;
				tmp_list.add( a );
			}
			account_list.clear();
			account_list.addAll( tmp_list );
			if( account_list.isEmpty() ){
				Utils.showToast( activity, false, R.string.not_available_for_pseudo_account );
				return;
			}
		}
		
		if( bAuto && account_list.size() == 1 ){
			callback.onAccountPicked( account_list.get( 0 ) );
			return;
		}
		
		Collections.sort( account_list, new Comparator< SavedAccount >() {
			@Override public int compare( SavedAccount a, SavedAccount b ){
				return String.CASE_INSENSITIVE_ORDER.compare( AcctColor.getNickname( a.acct ), AcctColor.getNickname( b.acct ) );
			}
		} );
		
		@SuppressLint("InflateParams") View viewRoot = activity.getLayoutInflater().inflate( R.layout.dlg_account_picker, null, false );

		final Dialog dialog = new Dialog( activity );
		final AtomicBoolean isDialogClosed = new AtomicBoolean( false );
		
		if( dismiss_callback != null ){
			dialog.setOnDismissListener( dismiss_callback );
		}

		dialog.setContentView( viewRoot );
		if( ! TextUtils.isEmpty( message ) ){
			TextView tv = (TextView) viewRoot.findViewById( R.id.tvMessage );
			tv.setVisibility( View.VISIBLE );
			tv.setText( message );
		}
		viewRoot.findViewById( R.id.btnCancel ).setOnClickListener( new View.OnClickListener() {
			@Override public void onClick( View v ){
				isDialogClosed.set(true);
				dialog.cancel();
			}
		} );
		dialog.setCancelable( true );
		dialog.setCanceledOnTouchOutside( true );
		dialog.setOnCancelListener( new DialogInterface.OnCancelListener() {
			@Override public void onCancel( DialogInterface dialog ){
				isDialogClosed.set(true);
			}
		} );
		
		
		
		
		LinearLayout llAccounts = (LinearLayout) viewRoot.findViewById( R.id.llAccounts );
		int pad_se = (int) ( 0.5f + 12f * activity.density );
		int pad_tb = (int) ( 0.5f + 6f * activity.density );
		
		for( SavedAccount a : account_list ){
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams( LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT );
			
			AcctColor ac = AcctColor.load( a.acct );
			
			Button b = new Button( activity );
			
			if( AcctColor.hasColorBackground( ac ) ){
				b.setBackgroundColor( ac.color_bg );
			}else{
				b.setBackgroundResource( R.drawable.btn_bg_transparent );
			}
			if( AcctColor.hasColorForeground( ac ) ){
				b.setTextColor( ac.color_fg );
			}
			
			b.setPaddingRelative( pad_se, pad_tb, pad_se, pad_tb );
			b.setGravity( Gravity.START | Gravity.CENTER_VERTICAL );
			b.setAllCaps( false );
			b.setLayoutParams( lp );
			b.setMinHeight( (int) ( 0.5f + 32f * activity.density ) );
			b.setText( AcctColor.hasNickname( ac ) ? ac.nickname : a.acct );
			
			final SavedAccount _a = a;
			b.setOnClickListener( new View.OnClickListener() {
				@Override public void onClick( View v ){
					isDialogClosed.set(true);
					callback.onAccountPicked( _a );
					dialog.dismiss();
				}
			} );
			llAccounts.addView( b );
		}
		
		dialog.show();
	}
	
}
