package jp.juggler.subwaytooter.api.entity;

public class TootGap {
	public String max_id;
	public String since_id;
	public TootGap( String max_id, String since_id ){
		this.max_id = max_id;
		this.since_id = since_id;
	}
	public TootGap( long max_id, String since_id ){
		this.max_id = Long.toString(max_id);
		this.since_id = since_id;
	}
}
