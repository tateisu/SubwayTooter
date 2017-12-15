package jp.juggler.subwaytooter.api.entity;

public class TootGap {
	
	public final String max_id;
	public final String since_id;
	
	public TootGap( String max_id, String since_id ){
		this.max_id = max_id;
		this.since_id = since_id;
	}
	
	//	public TootGap( long max_id, String since_id ){
	//		this.max_id = Long.toString(max_id);
	//		this.since_id = since_id;
	//	}
	
	public TootGap( long max_id, long since_id ){
		this.max_id = Long.toString( max_id );
		this.since_id = Long.toString( since_id );
	}
}
