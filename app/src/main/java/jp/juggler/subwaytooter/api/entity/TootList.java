package jp.juggler.subwaytooter.api.entity;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class TootList implements Comparable< TootList > {
	private static final LogCategory log = new LogCategory( "TootList" );
	
	public long id;
	
	@Nullable public String title;

	// タイトルの数字列部分は数字の大小でソートされるようにしたい
	@Nullable private ArrayList< Object > title_for_sort;
	
	@Nullable
	public static TootList parse( JSONObject src ){
		if( src == null ) return null;
		try{
			TootList dst = new TootList();
			dst.id = Utils.optLongX( src, "id" );
			dst.title = Utils.optStringX( src, "title" );
			dst.title_for_sort = makeTitleForSort( dst.title );
			return dst;
		}catch( Throwable ex ){
			log.trace( ex );
			log.e( ex, "parse failed." );
			return null;
		}
	}
	
	private static final Pattern reNumber = Pattern.compile( "(\\d+)" );
	
	private static @NonNull ArrayList< Object > makeTitleForSort( @Nullable String title ){
		ArrayList< Object > list = new ArrayList<>();
		if( title != null ){
			Matcher m = reNumber.matcher( title );
			int last_end = 0;
			while( m.find() ){
				int match_start = m.start();
				int match_end = m.end();
				if( match_start > last_end ){
					list.add( title.substring( last_end, match_start ) );
				}
				try{
					list.add( Long.parseLong( m.group( 1 ), 10 ) );
				}catch( Throwable ex ){
					list.clear();
					list.add( title );
					return list;
				}
				last_end = match_end;
			}
			int end = title.length();
			if( end > last_end ){
				list.add( title.substring( last_end, end ) );
			}
		}
		return list;
	}
	
	@Override public int compareTo( @NonNull TootList b ){
		ArrayList< Object > la = this.title_for_sort;
		ArrayList< Object > lb = b.title_for_sort;
		
		if( la == null ){
			return lb == null ? 0 : - 1;
		}else if( lb == null ){
			return 1;
		}
		
		int sa = la.size();
		int sb = lb.size();
		
		for( int i = 0 ; ; ++ i ){
			
			Object oa = i >= sa ? null : la.get( i );
			Object ob = i >= sb ? null : lb.get( i );
			if( oa == null ){
				return ob == null ? 0 : - 1;
			}else if( ob == null ){
				return 1;
			}
			
			if( oa instanceof Long && ob instanceof Long ){
				long na = (Long) oa;
				long nb = (Long) ob;
				if( na < nb ) return - 1;
				if( na > nb ) return 1;
				continue;
			}
			
			int delta = oa.toString().compareTo( ob.toString() );
			if( delta != 0 ) return delta;
		}
	}
	
	public static class List extends ArrayList< TootList > {
	}
	
	@NonNull public static List parseList( JSONArray array ){
		TootList.List result = new TootList.List();
		if( array != null ){
			int array_size = array.length();
			result.ensureCapacity( array_size );
			for( int i = 0 ; i < array_size ; ++ i ){
				JSONObject obj = array.optJSONObject( i );
				if( obj != null ){
					TootList dst = TootList.parse( obj );
					if( dst != null ) result.add( dst );
				}
			}
		}
		return result;
	}
}
