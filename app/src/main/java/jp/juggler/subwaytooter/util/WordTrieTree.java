package jp.juggler.subwaytooter.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.SparseArrayCompat;

import java.util.ArrayList;

public class WordTrieTree {
	
	static class Match {
		String word;
		int start;
		int end;
	}
	
	//	private static class Matcher {
	//
	//		// ミュートの場合などは短いマッチでも構わない
	//		final boolean allowShortMatch;
	//
	//		// マッチ範囲の始端を覚えておく
	//		int start;
	//
	//		Matcher( boolean allowShortMatch ){
	//			this.allowShortMatch = allowShortMatch;
	//		}
	//
	//		void setTokenizer( CharacterGroup grouper, CharSequence src, int start, int end ){
	//			this.match = null;
	//			this.start = start;
	//			if( t == null ){
	//
	//			}else{
	//				t.reset( src, start, end );
	//			}
	//		}
	//	}
	
	private static final CharacterGroup grouper = new CharacterGroup();
	
	private static class Node {
		
		// 続くノード
		@NonNull final SparseArrayCompat< Node > child_nodes = new SparseArrayCompat<>();
		
		// このノードが終端なら、マッチした単語の元の表記がある
		@Nullable String match_word;
		
		// Trieツリー的には終端単語と続くノードの両方が存在する場合がありうる。
		// たとえば ABC と ABCDEF を登録してからABCDEF を探索したら、単語 ABC と単語 DEF にマッチする。
	}
	
	private final Node node_root = new Node();
	
	// 単語の追加
	public void add( @NonNull String s ){
		CharacterGroup.Tokenizer t = grouper.tokenizer( s, 0, s.length() );
		Node node = node_root;
		for( ; ; ){
			t.next();
			int id = t.c;
			if( id == CharacterGroup.END ){
				// より長いマッチ単語を覚えておく
				if( node.match_word == null || node.match_word.length() < t.text.length() ){
					node.match_word = t.text.toString();
				}
				return;
			}
			Node child = node.child_nodes.get( t.c );
			if( child == null ){
				node.child_nodes.put( id, child = new Node() );
			}
			node = child;
		}
	}
	
	// 前方一致でマッチング
	@Nullable
	private Match match( boolean allowShortMatch, @NonNull CharacterGroup.Tokenizer t ){
		
		int start = t.offset;
		Match dst = null;
		
		Node node = node_root;
		for( ; ; ){
			
			// このノードは単語の終端でもある
			if( node.match_word != null ){
				dst = new Match();
				dst.word = node.match_word;
				dst.start = start;
				dst.end = t.offset;
				
				// 最短マッチのみを調べるのなら、以降の処理は必要ない
				if( allowShortMatch ) break;
			}
			
			t.next();
			int id = t.c;
			if( id == CharacterGroup.END ) break;
			Node child = node.child_nodes.get( id );
			if( child == null ) break;
			node = child;
		}
		return dst;
	}
	
	public boolean matchShort( @Nullable CharSequence src ){
		return null != src && null != matchShort( src, 0, src.length() );
	}
	
	private Match matchShort( @NonNull CharSequence src, int start, int end ){
		CharacterGroup.Tokenizer t = grouper.tokenizer( src, start, end );
		for( int i = start ; i < end ; ++ i ){
			int c = src.charAt( i );
			if( CharacterGroup.isWhitespace( c ) ) continue;
			t.reset( src, i, end );
			Match item = match( true, t );
			if( item != null ) return item;
		}
		return null;
	}
	
	@Nullable ArrayList< Match > matchList( @NonNull CharSequence src, int start, int end ){
		ArrayList< Match > dst = null;
		
		CharacterGroup.Tokenizer t = grouper.tokenizer( src, start, end );
		for( int i = start ; i < end ; ++ i ){
			int c = src.charAt( i );
			if( CharacterGroup.isWhitespace( c ) ) continue;
			t.reset( src, i, end );
			Match item = match( false, t );
			if( item != null ){
				if( dst == null ) dst = new ArrayList<>();
				dst.add( item );
				i = item.end - 1;
			}
		}
		return dst;
	}
	
}
