package jp.juggler.subwaytooter.util;

import android.support.annotation.Nullable;

import jp.juggler.subwaytooter.table.AcctColor;

public interface LinkClickContext {
	
	@Nullable AcctColor findAcctColor( @Nullable String url );
}
