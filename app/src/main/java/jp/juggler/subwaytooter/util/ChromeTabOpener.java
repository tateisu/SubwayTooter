package jp.juggler.subwaytooter.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;

import jp.juggler.subwaytooter.ActMain;
import jp.juggler.subwaytooter.table.SavedAccount;

public class ChromeTabOpener {
	@NonNull public final ActMain activity;
	@NonNull public final String url;
	public final int pos;
	
	public ChromeTabOpener( @NonNull ActMain activity, int pos, @NonNull String url ){
		this.activity = activity;
		this.pos = pos;
		this.url = url;
	}
	
	public void open(){
		activity.openChromeTab( this );
	}
	
	@Nullable public SavedAccount access_info;
	
	public ChromeTabOpener accessInfo( @Nullable SavedAccount access_info ){
		this.access_info = access_info;
		return this;
	}
	
	public boolean bAllowIntercept = true;
	//	public ChromeTabOpener allowIntercept( boolean v){
	//		this.bAllowIntercept = v;
	//		return this;
	//	}
	
	@Nullable public ArrayList< String > tag_list;
	
	public ChromeTabOpener tagList( ArrayList< String > tag_list ){
		this.tag_list = tag_list;
		return this;
	}
	
}
