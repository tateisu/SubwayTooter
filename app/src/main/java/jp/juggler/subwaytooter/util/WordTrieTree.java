package jp.juggler.subwaytooter.util;

import android.support.annotation.NonNull;
import android.support.v4.util.SparseArrayCompat;

public class WordTrieTree {
	
	private static class Node {
		
		final SparseArrayCompat< Node > child_nodes = new SparseArrayCompat<>();
		
		boolean is_end;
		
		boolean match( String s, int offset, int remain ){
			
			if( is_end ){
				// ワードの始端から終端までマッチした
				return true;
			}
			
			if( remain <= 0 ){
				// テスト文字列の終端に達した
				return false;
			}
			
			int c = s.charAt( offset );
			++ offset;
			-- remain;
			
			Node n = child_nodes.get( c );
			return n != null && n.match( s, offset, remain );
		}
		
		public void add( String s, int offset, int remain ){
			
			if( is_end ){
				// NGワード用なので、既に終端を含むなら後続ノードの情報は不要
				return;
			}
			
			if( remain <= 0 ){
				
				// 終端マークを設定
				is_end = true;
				
				// 後続ノードは不要になる
				child_nodes.clear();
				
				return;
			}
			
			int c = s.charAt( offset );
			++ offset;
			-- remain;
			
			// 文字別に後続ノードを作成
			Node n = child_nodes.get( c );
			if( n == null ) child_nodes.put( c, n = new Node() );
			
			n.add( s, offset, remain );
		}
	}
	
	private final Node node_root = new Node();
	
	public void add(  @NonNull String s ){
		node_root.add( s, 0, s.length() );
	}
	
	public boolean containsWord( @NonNull String src ){
		for( int i = 0, ie = src.length() ; i < ie ; ++ i ){
			if( node_root.match( src, i, ie - i ) ) return true;
		}
		return false;
	}
}
