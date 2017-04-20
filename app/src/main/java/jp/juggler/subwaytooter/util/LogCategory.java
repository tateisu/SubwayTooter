package jp.juggler.subwaytooter.util;

import android.content.ContentValues;
import android.content.res.Resources;

import jp.juggler.subwaytooter.table.LogData;

public class LogCategory {
	
	final ContentValues cv = new ContentValues();
	final String category;
	
	public LogCategory( String category ){
		this.category = category;
	}
	
	@SuppressWarnings("unused")
	public void addLog( int level, String message ){
		synchronized( cv ){
			LogData.insert( cv, System.currentTimeMillis(), level, category, message );
		}
	}
	
	@SuppressWarnings("unused")
	public void e( String fmt, Object... args ){
		if( args.length > 0 ) fmt = String.format( fmt, args );
		synchronized( cv ){
			LogData.insert( cv, System.currentTimeMillis(), LogData.LEVEL_ERROR, category, fmt );
		}
	}
	
	@SuppressWarnings("unused")
	public void w( String fmt, Object... args ){
		if( args.length > 0 ) fmt = String.format( fmt, args );
		synchronized( cv ){
			LogData.insert( cv, System.currentTimeMillis(), LogData.LEVEL_WARNING, category, fmt );
		}
	}
	
	@SuppressWarnings("unused")
	public void i( String fmt, Object... args ){
		if( args.length > 0 ) fmt = String.format( fmt, args );
		synchronized( cv ){
			LogData.insert( cv, System.currentTimeMillis(), LogData.LEVEL_INFO, category, fmt );
		}
	}
	
	@SuppressWarnings("unused")
	public void v( String fmt, Object... args ){
		if( args.length > 0 ) fmt = String.format( fmt, args );
		synchronized( cv ){
			LogData.insert( cv, System.currentTimeMillis(), LogData.LEVEL_VERBOSE, category, fmt );
		}
	}
	
	@SuppressWarnings("unused")
	public void d( String fmt, Object... args ){
		if( args.length > 0 ) fmt = String.format( fmt, args );
		synchronized( cv ){
			LogData.insert( cv, System.currentTimeMillis(), LogData.LEVEL_DEBUG, category, fmt );
		}
	}
	
	@SuppressWarnings("unused")
	public void h( String fmt, Object... args ){
		if( args.length > 0 ) fmt = String.format( fmt, args );
		synchronized( cv ){
			LogData.insert( cv, System.currentTimeMillis(), LogData.LEVEL_HEARTBEAT, category, fmt );
		}
	}
	
	@SuppressWarnings("unused")
	public void f( String fmt, Object... args ){
		if( args.length > 0 ) fmt = String.format( fmt, args );
		synchronized( cv ){
			LogData.insert( cv, System.currentTimeMillis(), LogData.LEVEL_FLOOD, category, fmt );
		}
	}
	
	@SuppressWarnings("unused")
	public void e( Resources res, int string_id, Object... args ){
		String fmt = res.getString( string_id, args );
		synchronized( cv ){
			LogData.insert( cv, System.currentTimeMillis(), LogData.LEVEL_ERROR, category, fmt );
		}
	}
	
	@SuppressWarnings("unused")
	public void w( Resources res, int string_id, Object... args ){
		String fmt = res.getString( string_id, args );
		synchronized( cv ){
			LogData.insert( cv, System.currentTimeMillis(), LogData.LEVEL_WARNING, category, fmt );
		}
	}
	
	@SuppressWarnings("unused")
	public void i( Resources res, int string_id, Object... args ){
		String fmt = res.getString( string_id, args );
		synchronized( cv ){
			LogData.insert( cv, System.currentTimeMillis(), LogData.LEVEL_INFO, category, fmt );
		}
	}
	
	@SuppressWarnings("unused")
	public void v( Resources res, int string_id, Object... args ){
		String fmt = res.getString( string_id, args );
		synchronized( cv ){
			LogData.insert( cv, System.currentTimeMillis(), LogData.LEVEL_VERBOSE, category, fmt );
		}
	}
	
	@SuppressWarnings("unused")
	public void d( Resources res, int string_id, Object... args ){
		String fmt = res.getString( string_id, args );
		synchronized( cv ){
			LogData.insert( cv, System.currentTimeMillis(), LogData.LEVEL_DEBUG, category, fmt );
		}
	}
	
	@SuppressWarnings("unused")
	public void h( Resources res, int string_id, Object... args ){
		String fmt = res.getString( string_id, args );
		synchronized( cv ){
			LogData.insert( cv, System.currentTimeMillis(), LogData.LEVEL_HEARTBEAT, category, fmt );
		}
	}
	
	@SuppressWarnings("unused")
	public void f( Resources res, int string_id, Object... args ){
		String fmt = res.getString( string_id, args );
		synchronized( cv ){
			LogData.insert( cv, System.currentTimeMillis(), LogData.LEVEL_FLOOD, category, fmt );
		}
	}
	
	@SuppressWarnings("unused")
	public void e( Throwable ex, String fmt, Object... args ){
		if( args.length > 0 ) fmt = String.format( fmt, args );
		synchronized( cv ){
			LogData.insert( cv, System.currentTimeMillis(), LogData.LEVEL_ERROR, category, fmt + String.format( ":%s %s", ex.getClass().getSimpleName(), ex.getMessage() ) );
		}
	}
	
	@SuppressWarnings("unused")
	public void e( Throwable ex, Resources res, int string_id, Object... args ){
		String fmt = res.getString( string_id, args );
		synchronized( cv ){
			LogData.insert( cv, System.currentTimeMillis(), LogData.LEVEL_ERROR, category, fmt + String.format( ":%s %s", ex.getClass().getSimpleName(), ex.getMessage() ) );
		}
	}
	
}