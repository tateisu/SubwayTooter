package jp.juggler.subwaytooter.util;

import android.support.v4.util.SparseArrayCompat;

public class WordTrieTree {
	
	static class Node{
		
		final SparseArrayCompat<Node> child_nodes = new SparseArrayCompat<>(  );
		
		boolean is_end;
		
		boolean match( String s, int offset, int length ){

			if( is_end ) return true;

			if( length <= 0 ){
				return false;
			}
			
			int c = s.charAt( offset );
			++ offset;
			-- length;
			Node n = child_nodes.get( c );
			return n != null && n.match( s, offset, length );
		}
		
		public void add( String s, int offset, int length ){
			// NGワード用なので、既に終端を含むならより詳細な情報は必要がない
			if( is_end ) return;
			
			// このノードは終端を持つ
			if( length <= 0 ){
				is_end = true;
				return;
			}
			
			int c = s.charAt( offset );
			++ offset;
			-- length;
			Node n = child_nodes.get( c );
			if( n == null ){
				child_nodes.put( c, n = new Node() );
			}
			n.add( s, offset, length );
		}
	}
	
	final Node node_root = new Node();
	
	public void add( String s ){
		node_root.add( s ,0,s.length() );
	}
	
	public boolean containsWord(String src){
		if( src==null ) return false;

		for(int i=0,ie=src.length();i<ie;++i){
			if( node_root.match( src, i,ie-i) ) return true;
		}
		return false;
	}
}
