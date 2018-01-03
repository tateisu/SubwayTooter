package jp.juggler.subwaytooter.dialog;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

import jp.juggler.subwaytooter.ActMain;
import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.Styler;
import jp.juggler.subwaytooter.action.ActionUtils;
import jp.juggler.subwaytooter.action.Action_List;
import jp.juggler.subwaytooter.action.Action_ListMember;
import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.TootTask;
import jp.juggler.subwaytooter.api.TootTaskRunner;
import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootList;
import jp.juggler.subwaytooter.api.entity.TootResults;
import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.NetworkEmojiInvalidator;
import jp.juggler.subwaytooter.util.Utils;
import jp.juggler.subwaytooter.view.MyListView;
import jp.juggler.subwaytooter.view.MyNetworkImageView;

public class DlgListMember implements View.OnClickListener {
	
	@NonNull private final ActMain activity;
	@NonNull private final Dialog dialog;
	
	@NonNull private final Button btnListOwner;
	@NonNull private final Button btnCreateList;
	
	@NonNull private final ArrayList< SavedAccount > account_list;
	@NonNull private final String target_user_full_acct;
	
	@Nullable private SavedAccount list_owner;
	@Nullable private TootAccount local_who;
	
	public DlgListMember( @NonNull ActMain _activity, @NonNull TootAccount who, @NonNull SavedAccount _list_owner ){
		this.activity = _activity;
		this.account_list = ActionUtils.makeAccountListNonPseudo( _activity, null );
		this.target_user_full_acct = _list_owner.getFullAcct( who );
		
		if( _list_owner.isPseudo() ){
			this.list_owner = null;
		}else{
			this.list_owner = _list_owner;
		}
		
		@SuppressLint("InflateParams") final View view = activity.getLayoutInflater().inflate( R.layout.dlg_list_member, null, false );
		
		MyNetworkImageView ivUser = view.findViewById( R.id.ivUser );
		TextView tvUserName = view.findViewById( R.id.tvUserName );
		TextView tvUserAcct = view.findViewById( R.id.tvUserAcct );
		btnListOwner = view.findViewById( R.id.btnListOwner );
		btnCreateList = view.findViewById( R.id.btnCreateList );
		MyListView listView = view.findViewById( R.id.listView );
		
		this.adapter = new MyListAdapter();
		listView.setAdapter( adapter );
		
		btnCreateList.setOnClickListener( this );
		btnListOwner.setOnClickListener( this );
		view.findViewById( R.id.btnClose ).setOnClickListener( this );
		
		ivUser.setImageUrl( App1.pref, 16f, who.avatar_static, who.avatar );
		NetworkEmojiInvalidator user_name_invalidator = new NetworkEmojiInvalidator( activity.handler, tvUserName );
		Spannable name = who.decodeDisplayName( activity );
		tvUserName.setText( name );
		user_name_invalidator.register( name );
		tvUserAcct.setText( target_user_full_acct );
		
		setListOwner( list_owner );
		
		this.dialog = new Dialog( activity );
		
		Window w = dialog.getWindow();
		if( w != null ){
			w.setFlags( 0, Window.FEATURE_NO_TITLE );
			w.setLayout(
				WindowManager.LayoutParams.MATCH_PARENT
				, WindowManager.LayoutParams.MATCH_PARENT
			);
		}
		
		dialog.setTitle( R.string.your_lists );
		dialog.setContentView( view );
	}
	
	public void show(){
		Window w = dialog.getWindow();
		if( w != null ){
			w.setLayout(
				WindowManager.LayoutParams.MATCH_PARENT
				, WindowManager.LayoutParams.MATCH_PARENT
			);
		}
		dialog.show();
	}
	
	@Override public void onClick( View v ){
		switch( v.getId() ){
		
		case R.id.btnClose:
			try{
				dialog.dismiss();
			}catch( Throwable ignored ){
			}
			break;
		
		case R.id.btnListOwner:
			AccountPicker.pick( activity, false, false, null, account_list, new AccountPicker.AccountPickerCallback() {
				@Override public void onAccountPicked( @NonNull SavedAccount ai ){
					setListOwner( ai );
				}
			} );
			break;
		
		case R.id.btnCreateList:
			openListCreator();
			break;
			
		}
	}
	
	// リストオーナボタンの文字列を更新する
	// リスト一覧を取得する
	private void setListOwner( @Nullable SavedAccount a ){
		this.list_owner = a;
		if( a == null ){
			btnListOwner.setText( R.string.not_selected );
			btnListOwner.setTextColor( Styler.getAttributeColor( activity, android.R.attr.textColorPrimary ) );
			btnListOwner.setBackgroundResource( R.drawable.btn_bg_transparent );
			//
			
		}else{
			String acct = a.getFullAcct( a );
			AcctColor ac = AcctColor.load( acct );
			String nickname = AcctColor.hasNickname( ac ) ? ac.nickname : acct;
			btnListOwner.setText( nickname );
			
			if( AcctColor.hasColorBackground( ac ) ){
				btnListOwner.setBackgroundColor( ac.color_bg );
			}else{
				btnListOwner.setBackgroundResource( R.drawable.btn_bg_transparent );
			}
			if( AcctColor.hasColorForeground( ac ) ){
				btnListOwner.setTextColor( ac.color_fg );
			}else{
				btnListOwner.setTextColor( Styler.getAttributeColor( activity, android.R.attr.textColorPrimary ) );
			}
		}
		
		loadLists();
	}
	
	// リストの一覧とターゲットユーザの登録状況を取得する
	private void loadLists(){
		if( list_owner == null ){
			showList( null );
			return;
		}
		
		new TootTaskRunner( activity, true ).run( list_owner, new TootTask() {
			@Override public TootApiResult background( @NonNull TootApiClient client ){
				// リストに追加したいアカウントの自タンスでのアカウントIDを取得する
				local_who = null;
				TootApiResult result = client.request( "/api/v1/search?resolve=true&q=" + Uri.encode( target_user_full_acct ) );
				if( result == null || result.object == null ){
					return result;
				}
				
				TootResults search_result = TootResults.parse( activity, list_owner, result.object );
				if( search_result != null ){
					for( TootAccount a : search_result.accounts ){
						if( target_user_full_acct.equalsIgnoreCase( list_owner.getFullAcct( a ) ) ){
							local_who = a;
							break;
						}
					}
				}
				
				if( local_who == null ){
					return new TootApiResult( activity.getString( R.string.account_sync_failed ) );
				}
				
				// リスト登録状況を取得
				result = client.request( "/api/v1/accounts/" + local_who.id + "/lists" );
				if( result == null || result.array == null ){
					return result;
				}
				
				// 結果を解釈する
				HashSet< Long > set_registered = new HashSet<>();
				for( TootList a : TootList.parseList( result.array ) ){
					set_registered.add( a.id );
				}
				
				// リスト一覧を取得
				result = client.request( "/api/v1/lists" );
				if( result == null || result.array == null ){
					return result;
				}
				new_list = TootList.parseList( result.array );
				Collections.sort( new_list );
				
				// isRegistered を設定する
				for( TootList a : new_list ){
					if( set_registered.contains( a.id ) ) a.isRegistered = true;
				}
				
				return result;
			}
			
			TootList.List new_list;
			
			@Override public void handleResult( @Nullable TootApiResult result ){
				showList( new_list );
				
				//noinspection StatementWithEmptyBody
				if( result != null
					&& ! TextUtils.isEmpty( result.error )
					&& ! ( result.response != null && result.response.code() == 404 )
					){
					Utils.showToast( activity, true, result.error );
				}
				
			}
		} );
		
	}
	
	private void showList( @Nullable TootList.List _list ){
		btnCreateList.setEnabled( _list != null );
		adapter.item_list.clear();
		if( _list == null ){
			adapter.item_list.add( new ErrorItem( activity.getString( R.string.cant_access_list ) ) );
		}else if( _list.isEmpty() ){
			adapter.item_list.add( new ErrorItem( activity.getString( R.string.list_not_created ) ) );
		}else{
			adapter.item_list.addAll( _list );
		}
		adapter.notifyDataSetChanged();
	}
	
	private void openListCreator(){
		DlgTextInput.show( activity, activity.getString( R.string.list_create ), null, new DlgTextInput.Callback() {
			
			@Override public void onEmptyError(){
				Utils.showToast( activity, false, R.string.list_name_empty );
			}
			
			@Override public void onOK( final Dialog dialog, final String title ){
				if( list_owner == null ){
					Utils.showToast( activity, false, "list owner is not selected." );
					return;
				}
				
				Action_List.create( activity, list_owner, title, new Action_List.CreateCallback() {
					@Override public void onCreated( @NonNull TootList list ){
						try{
							dialog.dismiss();
						}catch( Throwable ignored ){
						}
						loadLists();
						
					}
				} );
			}
			
		} );
	}
	
	static class ErrorItem {
		final String message;
		
		ErrorItem( String message ){
			this.message = message;
		}
	}
	
	private final MyListAdapter adapter;
	
	private class MyListAdapter extends BaseAdapter {
		final ArrayList< Object > item_list = new ArrayList<>();
		
		@Override public int getCount(){
			return item_list.size();
		}
		
		@Override public Object getItem( int position ){
			if( position >= 0 && position < item_list.size() ) return item_list.get( position );
			return null;
		}
		
		@Override public long getItemId( int position ){
			return 0;
		}
		
		@Override public int getViewTypeCount(){
			return 2;
		}
		
		@Override public int getItemViewType( int position ){
			Object o = getItem( position );
			if( o instanceof TootList ) return 0;
			return 1;
		}
		
		@Override public View getView( int position, View view, ViewGroup parent ){
			Object o = getItem( position );
			if( o instanceof TootList ){
				VH_List holder;
				if( view != null ){
					holder = (VH_List) view.getTag();
				}else{
					view = activity.getLayoutInflater().inflate( R.layout.lv_list_member_list, parent, false );
					holder = new VH_List( view );
					view.setTag( holder );
				}
				holder.bind( (TootList) o );
			}else if( o instanceof ErrorItem ){
				VH_Error holder;
				if( view != null ){
					holder = (VH_Error) view.getTag();
				}else{
					view = activity.getLayoutInflater().inflate( R.layout.lv_list_member_error, parent, false );
					holder = new VH_Error( view );
					view.setTag( holder );
				}
				holder.bind( (ErrorItem) o );
			}
			return view;
		}
	}
	
	class VH_List implements CompoundButton.OnCheckedChangeListener, Action_ListMember.Callback {
		final CheckBox cbItem;
		boolean bBusy;
		TootList item;
		
		VH_List( View view ){
			this.cbItem = view.findViewById( R.id.cbItem );
			cbItem.setOnCheckedChangeListener( this );
		}
		
		public void bind( TootList item ){
			this.item = item;
			cbItem.setText( item.title );
			bBusy = true;
			cbItem.setChecked( item.isRegistered );
			bBusy = false;
		}
		
		@Override public void onCheckedChanged( CompoundButton view, boolean isChecked ){
			if( bBusy ){
				// ユーザ操作以外で変更されたなら何もしない
				return;
			}
			
			if( list_owner == null ){
				Utils.showToast( activity, false, "list owner is not selected" );
				return;
			}
			
			if( local_who == null ){
				Utils.showToast( activity, false, "target user is not synchronized" );
				return;
			}
			
			// 状態をサーバに伝える
			if( isChecked ){
				Action_ListMember.add( activity, list_owner, item.id, local_who, false, this );
			}else{
				Action_ListMember.delete( activity, list_owner, item.id, local_who, this );
			}
		}
		
		@Override public void onListMemberUpdated( boolean willRegistered, boolean bSuccess ){
			if( ! bSuccess ){
				item.isRegistered = ! willRegistered;
				adapter.notifyDataSetChanged();
			}
		}
	}
	
	class VH_Error {
		final TextView tvError;
		
		VH_Error( View view ){
			this.tvError = view.findViewById( R.id.tvError );
		}
		
		public void bind( ErrorItem o ){
			this.tvError.setText( o.message );
		}
	}
}
