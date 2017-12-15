package jp.juggler.subwaytooter.api.entity;

import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.json.JSONArray;

import java.util.ArrayList;

public class TootDomainBlock {
	
	public static class List extends ArrayList< TootDomainBlock > {
		
	}
	
	// domain
	public final String domain;
	
	private TootDomainBlock( String sv ){
		this.domain = sv;
	}
	
	@NonNull
	public static List parseList( JSONArray array ){
		List result = new List();
		if( array != null ){
			int array_size = array.length();
			result.ensureCapacity( array_size );
			for( int i = 0 ; i < array_size ; ++ i ){
				String sv = array.optString( i );
				if( ! TextUtils.isEmpty( sv ) ){
					result.add( new TootDomainBlock( sv ) );
				}
			}
		}
		return result;
	}
	
}
