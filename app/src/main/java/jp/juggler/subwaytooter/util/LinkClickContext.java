package jp.juggler.subwaytooter.util;

import jp.juggler.subwaytooter.table.AcctColor;

/**
 * Created by tateisu on 2017/04/24.
 */

public interface LinkClickContext {

	AcctColor findAcctColor( String url );
}
