package jp.juggler.subwaytooter.util;

import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;

import java.util.ArrayList;

import jp.juggler.subwaytooter.R;

public class ActionsDialog {
	
	static class Action {
		String caption;
		Runnable r;
	}
	
	final ArrayList< Action > action_list = new ArrayList<>();
	
	public void addAction( String caption, Runnable r ){
		Action action = new Action();
		action.caption = caption;
		action.r = r;
		action_list.add( action );
	}
	
	public void show( Context context, String title ){
		String[] caption_list = new String[ action_list.size() ];
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
		
	}
}
