package jp.juggler.subwaytooter.dialog;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import jp.juggler.subwaytooter.ActMain;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.Utils;

public class AccountPicker {
	
	public interface AccountPickerCallback {
		void onAccountPicked( SavedAccount ai );
	}
	
	public static void pick( @NonNull ActMain activity, boolean bAllowPseudo, boolean bAuto
		, @NonNull final AccountPickerCallback callback
	){
		
		ArrayList< SavedAccount > account_list = SavedAccount.loadAccountList( ActMain.log );
		pick( activity, bAllowPseudo, bAuto, account_list, callback );
	}
	
	public static void pick( @NonNull ActMain activity, boolean bAllowPseudo, boolean bAuto
		, @NonNull final ArrayList< SavedAccount > account_list
		, @NonNull final AccountPickerCallback callback
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
			@Override
			public int compare( SavedAccount o1, SavedAccount o2 ){
				return String.CASE_INSENSITIVE_ORDER.compare( o1.acct, o2.acct );
			}
		} );
		
		String[] caption_list = new String[ account_list.size() ];
		
		for( int i = 0, ie = account_list.size() ; i < ie ; ++ i ){
			SavedAccount ai = account_list.get( i );
			caption_list[ i ] = ai.acct;
		}
		
		new AlertDialog.Builder( activity )
			.setNegativeButton( R.string.cancel, null )
			.setItems( caption_list, new DialogInterface.OnClickListener() {
				@Override
				public void onClick( DialogInterface dialog, int which ){
					if( which >= 0 && which < account_list.size() ){
						callback.onAccountPicked( account_list.get( which ) );
						dialog.dismiss();
					}
				}
			} )
			.setTitle( R.string.account_pick )
			.show();
	}
}
