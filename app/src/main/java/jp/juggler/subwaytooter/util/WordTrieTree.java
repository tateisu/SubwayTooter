package jp.juggler.subwaytooter.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.SparseArrayCompat;

import java.util.ArrayList;

public class WordTrieTree {
	
	static class Match {
		final int start;
		final int end;
		@NonNull final String word;
		
		Match( int start, int end, String word ){
			this.start = start;
			this.end = end;
			this.word = word;
		}
	}
	
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
			
			int id = t.next();
			if( id == CharacterGroup.END ){
				// より長いマッチ単語を覚えておく
				if( node.match_word == null || node.match_word.length() < t.text.length() ){
					node.match_word = t.text.toString();
				}
				return;
			}
			Node child = node.child_nodes.get( id );
			if( child == null ){
				node.child_nodes.put( id, child = new Node() );
			}
			node = child;
		}
	}
	
	// Tokenizer が列挙する文字を使って Trie Tree を探索する
	@Nullable
	private Match match( boolean allowShortMatch, @NonNull CharacterGroup.Tokenizer t ){
		
		int start = t.offset;
		Match dst = null;
		
		Node node = node_root;
		for( ; ; ){
			
			// このノードは単語の終端でもある
			if( node.match_word != null ){
				dst = new Match( start, t.offset, node.match_word );
				
				// ミュート用途の場合、ひとつでも単語にマッチすればより長い探索は必要ない
				if( allowShortMatch ) break;
			}
			
			int id = t.next();
			if( id == CharacterGroup.END ) break;
			Node child = node.child_nodes.get( id );
			if( child == null ) break;
			node = child;
		}
		return dst;
	}
	
	// ミュート用。マッチするかどうかだけを調べる
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

	// ハイライト用。複数マッチする。マッチした位置を覚える
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
