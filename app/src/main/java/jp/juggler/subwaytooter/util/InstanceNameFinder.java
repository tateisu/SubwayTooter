//package jp.juggler.subwaytooter.util;
//
//import android.util.SparseArray;
//import android.util.SparseIntArray;
//
//import java.util.ArrayList;
//import java.util.Iterator;
//
//public class InstanceNameFinder implements Iterable<String>{
//
//	private static class Node{
//		final SparseArray<Node> next_map = new SparseArray<>(  );
//		final SparseIntArray name_list = new SparseIntArray(  );
//
//		void add( int name_id, String name_remain ){
//			name_list.put( name_id ,1);
//
//			if( name_remain.length() == 0 ) return;
//
//			int c = name_remain.charAt( 0 );
//			name_remain = name_remain.substring( 1 );
//
//			Node sub_node = next_map.get( c );
//			if( sub_node == null ){
//				sub_node = new Node();
//				next_map.put( c,sub_node);
//			}
//
//			sub_node.add( name_id, name_remain );
//		}
//	}
//
//	private final ArrayList<String> name_list = new ArrayList<>(  );
//	private final Node root_node = new Node();
//
//
//	void addName(String name){
//		int name_id = name_list.size();
//		name_list.add(name);
//
//		int max = 3;
//		for(int i=0,ie=name.length();i<ie;++i){
//			int remain = ie-i;
//			if( )
//		}
//
//		root_node.add( name_id, name );
//	}
//
//	@Override public Iterator< String > iterator(){
//
//		return null;
//	}
//
//}
