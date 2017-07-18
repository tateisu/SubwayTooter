package jp.juggler.subwaytooter.api.entity;

import org.json.JSONObject;

import jp.juggler.subwaytooter.util.LinkClickContext;
import jp.juggler.subwaytooter.util.LogCategory;

public class TootContext {
	
	//	The ancestors of the status in the conversation, as a list of Statuses
	public TootStatus.List ancestors;
	
	// descendants	The descendants of the status in the conversation, as a list of Statuses
	public TootStatus.List descendants;
	
	public static TootContext parse( LogCategory log, LinkClickContext lcc,String status_host,JSONObject src ){
		if( src==null) return null;
		try{
			TootContext dst = new TootContext();
			dst.ancestors = TootStatus.parseList( log, lcc,status_host,src.optJSONArray( "ancestors" ) );
			dst.descendants = TootStatus.parseList(log, lcc, status_host,src.optJSONArray( "descendants" ) );
			return dst;
		}catch( Throwable ex ){
			ex.printStackTrace();
			log.e(ex,"TootContext.parse failed.");
			return null;
		}
	}
}
