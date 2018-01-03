package jp.juggler.subwaytooter.api;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootContext;
import jp.juggler.subwaytooter.api.entity.TootNotification;
import jp.juggler.subwaytooter.api.entity.TootResults;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.WordTrieTree;

public class TootParser {
	@NonNull public final Context context;
	@NonNull public final SavedAccount access_info;
	
	public TootParser( @NonNull Context context, @NonNull SavedAccount access_info ){
		this.context = context;
		this.access_info = access_info;
	}
	
	////////////////////////////////////////////////////////
	// parser options
	
	// プロフィールカラムからpinned TL を読んだ時だけ真
	public boolean isPinned;
	
	public TootParser setPinned( boolean isPinned ){
		this.isPinned = isPinned;
		return this;
	}
	
	@Nullable public WordTrieTree highlight_trie;
	public TootParser setHighlightTrie( @Nullable WordTrieTree highlight_trie ){
		this.highlight_trie = highlight_trie;
		return this;
	}
	
	/////////////////////////////////////////////////////////
	// parser methods
	
	@Nullable public TootAccount account( @Nullable JSONObject src ){
		return TootAccount.parse( context,access_info, src );
	}
	
	@Nullable public TootStatus status( @Nullable JSONObject src ){
		return TootStatus.parse( this, src );
	}
	
	@NonNull public TootStatus.List statusList( @Nullable JSONArray array ){
		return TootStatus.parseList( this, array );
	}
	
	@Nullable public TootNotification notification( @Nullable JSONObject src ){
		return TootNotification.parse(this,src);
	}
	
	@NonNull public TootNotification.List notificationList( @Nullable JSONArray src ){
		return TootNotification.parseList( this, src );
	}
	
	@Nullable public TootResults results( @Nullable JSONObject src ){
		return TootResults.parse( this, src );
	}

	@Nullable public TootContext context( @Nullable JSONObject src ){
		return TootContext.parse(this, src );
	}
	

}
