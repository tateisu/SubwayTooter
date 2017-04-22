package jp.juggler.subwaytooter.dialog;

import android.app.AlertDialog;
import android.content.DialogInterface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import jp.juggler.subwaytooter.ActMain;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.table.SavedAccount;

public class AccountPicker {
	
	public interface AccountPickerCallback{
		void onAccountPicked(SavedAccount ai);
	}
	
	public static void pick( ActMain activity, final AccountPickerCallback callback){

		final ArrayList<SavedAccount > account_list = SavedAccount.loadAccountList(ActMain.log);

		Collections.sort( account_list, new Comparator< SavedAccount >() {
			@Override
			public int compare( SavedAccount o1, SavedAccount o2 ){
				int i = String.CASE_INSENSITIVE_ORDER.compare( o1.acct, o2.acct );
				if( i != 0 ) return i;
				return String.CASE_INSENSITIVE_ORDER.compare( o1.host, o2.host );
			}
		} );

		String[] caption_list = new String[ account_list.size() ];

		for(int i=0,ie=account_list.size();i<ie;++i){
			SavedAccount ai = account_list.get(i);
			caption_list[i] = ai.acct;
		}

		new AlertDialog.Builder(activity)
			.setNegativeButton( R.string.cancel,null )
			.setItems( caption_list, new DialogInterface.OnClickListener() {
				@Override
				public void onClick( DialogInterface dialog, int which ){
					if( which >= 0 && which < account_list.size() ){
						callback.onAccountPicked(account_list.get(which));
						dialog.dismiss();
					}
				}
			} )
			.setTitle( R.string.account_pick )
			.show();
	}
}
