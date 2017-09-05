package jp.juggler.subwaytooter.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.math.BigInteger;
import java.util.ArrayList;

public class VersionString {
	// static final LogCategory log = new LogCategory( "VersionString" );
	
	@NonNull private final String src;
	
	@Override public String toString(){
		return src;
	}
	
	private final ArrayList< Object > node_list = new ArrayList<>();
	
	public boolean isEmpty(){
		return node_list.isEmpty();
	}
	
	private static boolean isDelimiter( char c ){
		return c == '.' || c == ' ';
	}
	
	public VersionString( @Nullable String src ){
		this.src = src == null ? "" : src;
		if( TextUtils.isEmpty( src ) ) return;
		int end = src.length();
		int next = 0;
		while( next < end ){
			char c = src.charAt( next );
			
			if( isDelimiter( c ) ){
				// 先頭の区切り文字を無視する
				++ next;
			}else if( Character.isDigit( c ) ){
				// 数字列のノード
				int start = next++;
				while( next < end && Character.isDigit( src.charAt( next ) ) ) ++ next;
				BigInteger value = new BigInteger( src.substring( start, next ) );
				node_list.add( value );
			}else{
				// 区切り文字と数字以外の文字が並ぶノード
				int start = next++;
				while( next < end ){
					c = src.charAt( next );
					if( isDelimiter( c ) ) break;
					if( Character.isDigit( c ) ) break;
					++ next;
				}
				String value = src.substring( start, next );
				node_list.add( value );
			}
		}
	}
	
	// return -1 if a<b , return 1 if a>b , return 0 if a==b
	public static int compare( @NonNull VersionString a, @NonNull VersionString b ){
		
		for( int idx = 0 ; ; ++ idx ){
			Object ao = ( idx >= a.node_list.size() ? null : a.node_list.get( idx ) );
			Object bo = ( idx >= b.node_list.size() ? null : b.node_list.get( idx ) );
			if( ao == null ){
				// 1.0 < 1.0.n
				// 1.0 < 1.0 xxx
				return bo == null ? 0 : - 1;
			}else if( bo == null ){
				// 1.0.n > 1.0
				// 1.0 xxx > 1.0
				return 1;
			}
			
			if( ao instanceof BigInteger ){
				if( bo instanceof BigInteger ){
					// 数字同士の場合
					int i = ( (BigInteger) ao ).compareTo( (BigInteger) bo );
					if( i == 0 ) continue;
					return i;
				}else{
					// 数字 > 数字以外
					// 1.5.n > 1.5 xxx
					return 1;
				}
			}else if( bo instanceof BigInteger ){
				// 数字以外 < 数字
				// 1.5 xxx < 1.5.n
				return - 1;
			}else{
				// 数字じゃない文字列どうしは辞書順で比較
				int i = ( (String) ao ).compareTo( (String) bo );
				if( i == 0 ) continue;
				return i;
			}
		}
	}
}
