package jp.juggler.subwaytooter.util;

import android.content.Context;
import android.icu.util.Output;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.LinkedList;

public class TaskList {
	
	private static final LogCategory log = new LogCategory( "TaskList" );
	private static final String FILE_TASK_LIST = "JOB_TASK_LIST";
	
	private LinkedList< JSONObject > list;
	
	private synchronized void prepareArray( @NonNull Context context ){
		if( list != null ) return;
		list = new LinkedList<>();
		try{
			InputStream is = context.openFileInput( FILE_TASK_LIST );
			try{
				ByteArrayOutputStream bao = new ByteArrayOutputStream();
				IOUtils.copy( is, bao );
				JSONArray array = new JSONArray( Utils.decodeUTF8( bao.toByteArray() ) );
				for( int i = 0, ie = array.length() ; i < ie ; ++ i ){
					JSONObject item = array.optJSONObject( i );
					if( item != null ) list.add( item );
				}
			}finally{
				IOUtils.closeQuietly( is );
			}
		}catch( Throwable ex ){
			log.trace( ex );
			log.e( ex, "TaskList: prepareArray failed." );
		}
	}
	
	private synchronized void saveArray( @NonNull Context context ){
		try{
			JSONArray array = new JSONArray();
			for( JSONObject item : list ){
				array.put( item );
			}
			byte[] data = Utils.encodeUTF8( array.toString() );
			OutputStream os = context.openFileOutput( FILE_TASK_LIST, Context.MODE_PRIVATE );
			try{
				IOUtils.write( data, os );
			}finally{
				IOUtils.closeQuietly( os );
			}
		}catch( Throwable ex ){
			log.trace( ex );
			log.e( ex, "TaskList: saveArray failed." );
		}
	}
	
	public synchronized void addLast( @NonNull Context context, boolean removeOld, @NonNull JSONObject taskData ){
		prepareArray( context );
		if( removeOld ){
			Iterator< JSONObject > it = list.iterator();
			while( it.hasNext() ){
				JSONObject item = it.next();
				if( taskData.equals( item ) ) it.remove();
			}
		}
		list.addLast( taskData );
		saveArray( context );
	}
	
	public synchronized boolean hasNext( @NonNull Context context ){
		prepareArray( context );
		return list.size() > 0 ;
	}
	
	public synchronized @NonNull JSONObject next( @NonNull Context context ){
		prepareArray( context );
		JSONObject item = list.removeFirst();
		saveArray( context );
		return item;
	}
	
}
