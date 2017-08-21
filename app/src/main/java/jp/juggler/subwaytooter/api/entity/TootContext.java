package jp.juggler.subwaytooter.api.entity;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONObject;

import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.LogCategory;

public class TootContext {
	private static final LogCategory log = new LogCategory( "TootContext" );
	
	//	The ancestors of the status in the conversation, as a list of Statuses
	public TootStatus.List ancestors;
	
	// descendants	The descendants of the status in the conversation, as a list of Statuses
	public TootStatus.List descendants;
	
	@Nullable
	public static TootContext parse( @NonNull Context context, @NonNull SavedAccount access_info, JSONObject src ){
		if( src == null ) return null;
		try{
			TootContext dst = new TootContext();
			dst.ancestors = TootStatus.parseList( context, access_info, src.optJSONArray( "ancestors" ) );
			dst.descendants = TootStatus.parseList( context, access_info, src.optJSONArray( "descendants" ) );
			return dst;
		}catch( Throwable ex ){
			log.trace( ex );
			log.e( ex, "TootContext.parse failed." );
			return null;
		}
	}
}
