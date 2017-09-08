package jp.juggler.subwaytooter.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;

import java.util.ArrayList;

import jp.juggler.subwaytooter.R;

public class ActionsDialog {
	
	private static class Action {
		@NonNull final CharSequence caption;
		@NonNull final Runnable r;
		
		Action( @NonNull CharSequence caption, @NonNull Runnable r ){
			this.caption = caption;
			this.r = r;
		}
	}
	
	private final ArrayList< Action > action_list = new ArrayList<>();
	
	public ActionsDialog addAction( @NonNull CharSequence caption, @NonNull Runnable r ){
		
		action_list.add( new Action( caption, r ) );
		
		return this;
	}
	
	public ActionsDialog show( @NonNull Context context, @Nullable CharSequence title ){
		CharSequence[] caption_list = new CharSequence[ action_list.size() ];
		for( int i = 0, ie = caption_list.length ; i < ie ; ++ i ){
			caption_list[ i ] = action_list.get( i ).caption;
		}
		AlertDialog.Builder b = new AlertDialog.Builder( context )
			.setNegativeButton( R.string.cancel, null )
			.setItems( caption_list, new DialogInterface.OnClickListener() {
				@Override public void onClick( DialogInterface dialog, int which ){
					if( which >= 0 && which < action_list.size() ){
						action_list.get( which ).r.run();
					}
				}
			} );
		
		if( ! TextUtils.isEmpty( title ) ) b.setTitle( title );
		
		b.show();
		
		return this;
	}
}
