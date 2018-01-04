package jp.juggler.subwaytooter.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.SparseArrayCompat;

import java.util.ArrayList;

public class WordTrieTree {
	
	public static class Match {
		public final int start;
		public final int end;
		@NonNull public final String word;
		
		Match( int start, int end, @NonNull String word ){
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
		// たとえば ABC と ABCDEF を登録してから ABCDEFG を探索したら、単語 ABC と単語 ABCDEF にマッチする。
	}
	
	private final Node node_root = new Node();
	
	// 単語の追加
	public void add( @NonNull String s ){
		CharacterGroup.Tokenizer t = grouper.tokenizer( s, 0, s.length() );
		
		int token_count = 0;
		Node node = node_root;
		for( ; ; ){
			
			int id = t.next();
			if( id == CharacterGroup.END ){
				
				// 単語を正規化したら長さ0だった場合、その単語は無視する
				if( token_count == 0 ) return;
				
				// より長いマッチ単語を覚えておく
				if( node.match_word == null || node.match_word.length() < t.text.length() ){
					node.match_word = t.text.toString();
				}
				
				return;
			}

			++ token_count;
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
	
	@SuppressWarnings("SameParameterValue")
	private Match matchShort( @NonNull CharSequence src, int start, int end ){
		
		CharacterGroup.Tokenizer t = grouper.tokenizer( src, start, end );
		
		for( int i = start ; i < end ; ++ i ){
			if( ! CharacterGroup.isWhitespace( src.charAt( i ) ) ){
				Match item = match( true, t.reset( src, i, end ) );
				if( item != null ) return item;
			}
		}
		return null;
	}
	
	// ハイライト用。複数マッチする。マッチした位置を覚える
	@Nullable public ArrayList< Match > matchList( @Nullable  CharSequence src ){
		return src==null ? null : matchList(src,0,src.length());
	}

	// ハイライト用。複数マッチする。マッチした位置を覚える
	@Nullable ArrayList< Match > matchList( @NonNull CharSequence src, int start, int end ){
		
		ArrayList< Match > dst = null;
		
		CharacterGroup.Tokenizer t = grouper.tokenizer( src, start, end );
		
		int i = start;
		while( i < end ){
			if( ! CharacterGroup.isWhitespace( src.charAt( i ) ) ){
				Match item = match( false, t.reset( src, i, end ) );
				if( item != null ){
					if( dst == null ) dst = new ArrayList<>();
					dst.add( item );
					i = item.end;
					continue;
				}
			}
			++ i;
		}
		
		return dst;
	}
	
}
