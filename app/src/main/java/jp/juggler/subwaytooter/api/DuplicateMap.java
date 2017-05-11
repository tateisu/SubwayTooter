package jp.juggler.subwaytooter.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootNotification;
import jp.juggler.subwaytooter.api.entity.TootReport;
import jp.juggler.subwaytooter.api.entity.TootStatus;

/**
 * Created by tateisu on 2017/05/10.
 */

public class DuplicateMap {
	
	HashSet< Long > set_status_id = new HashSet<>();
	HashSet< Long > set_notification_id = new HashSet<>();
	HashSet< Long > set_report_id = new HashSet<>();
	HashSet< Long > set_account_id = new HashSet<>();
	
	public void clear(){
		set_status_id.clear();
		set_notification_id.clear();
		set_report_id.clear();
		set_account_id.clear();
	}
	
	boolean isDuplicate(Object o){

		if( o instanceof TootStatus ){
			if( set_status_id.contains( ( (TootStatus) o ).id ) ) return true;
			set_status_id.add( ( (TootStatus) o ).id );

		}else if( o instanceof TootNotification ){
			if( set_notification_id.contains( ( (TootNotification) o ).id ) ) return true;
			set_notification_id.add( ( (TootNotification) o ).id );

		}else if( o instanceof TootReport ){
			if( set_report_id.contains( ( (TootReport) o ).id ) )  return true;
			set_report_id.add( ( (TootReport) o ).id );

		}else if( o instanceof TootAccount ){
			if( set_account_id.contains( ( (TootAccount) o ).id ) ) return true;
			set_account_id.add( ( (TootAccount) o ).id );
		}

		return false;
	}
	
	public ArrayList<Object> filterDuplicate( Collection< Object > src ){
		ArrayList< Object > list_new = new ArrayList<>();
		for( Object o : src ){
			if( isDuplicate( o )) continue;
			list_new.add( o );
		}

		return list_new;
	}
	
}
