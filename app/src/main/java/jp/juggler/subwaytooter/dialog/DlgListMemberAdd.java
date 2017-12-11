package jp.juggler.subwaytooter.dialog;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.ActMain;
import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.Column;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.Styler;
import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootList;
import jp.juggler.subwaytooter.api.entity.TootRelationShip;
import jp.juggler.subwaytooter.api.entity.TootResults;
import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.NetworkEmojiInvalidator;
import jp.juggler.subwaytooter.util.Utils;
import jp.juggler.subwaytooter.view.MyNetworkImageView;
import okhttp3.Request;
import okhttp3.RequestBody;

public class DlgListMemberAdd implements View.OnClickListener {
	
	private static final LogCategory log = new LogCategory( "DlgListMemberAdd" );
	
	@NonNull private final ActMain activity;
	
	//	@NonNull private final TootAccount target_user;
	@NonNull private final String target_user_full_acct;
	
	private SavedAccount list_owner;
	private long list_id;
	
	@NonNull private final Dialog dialog;
	private final Button btnListOwner;
	private final Button btnList;
	
	@NonNull private final ArrayList< SavedAccount > account_list;
	@Nullable private ArrayList< TootList > list_list;
	
	public DlgListMemberAdd( @NonNull ActMain _activity, @NonNull TootAccount who, @NonNull SavedAccount _list_owner, long list_id ){
		this.activity = _activity;
		//	this.target_user = who;
		this.target_user_full_acct = _list_owner.getFullAcct( who );
		this.account_list = activity.makeAccountListNonPseudo( log, null );
		
		if( _list_owner.isPseudo() ){
			this.list_owner = null;
			this.list_id = - 1L;
		}else{
			this.list_owner = _list_owner;
			this.list_id = list_id; // -1Lならリスト無し
		}
		
		@SuppressLint("InflateParams") final View view = activity.getLayoutInflater().inflate( R.layout.dlg_list_member_add, null, false );
		
		MyNetworkImageView ivUser = view.findViewById( R.id.ivUser );
		TextView tvUserName = view.findViewById( R.id.tvUserName );
		TextView tvUserAcct = view.findViewById( R.id.tvUserAcct );
		btnListOwner = view.findViewById( R.id.btnListOwner );
		btnList = view.findViewById( R.id.btnList );
		
		view.findViewById( R.id.btnCancel ).setOnClickListener( this );
		view.findViewById( R.id.btnOk ).setOnClickListener( this );
		
		ivUser.setImageUrl( App1.pref, 16f, who.avatar_static, who.avatar );
		
		NetworkEmojiInvalidator user_name_invalidator = new NetworkEmojiInvalidator( activity.handler, tvUserName );
		Spannable name = who.decodeDisplayName( activity );
		tvUserName.setText( name );
		user_name_invalidator.register( name );
		
		tvUserAcct.setText( target_user_full_acct );
		
		btnListOwner.setOnClickListener( this );
		btnList.setOnClickListener( this );
		
		setListOwner( list_owner, list_id );
		
		this.dialog = new Dialog( activity );
		dialog.setContentView( view );
		
	}
	
	public void show(){
		//noinspection ConstantConditions
		dialog.getWindow().setLayout( WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT );
		dialog.show();
	}
	
	@Override public void onClick( View v ){
		switch( v.getId() ){
		case R.id.btnListOwner:
			AccountPicker.pick( activity, false, false, null, account_list, new AccountPicker.AccountPickerCallback() {
				@Override public void onAccountPicked( @NonNull SavedAccount ai ){
					// アカウントが変更された時だけリストIDを変更する
					long new_list_id = ( list_owner != null && ai.acct.equals( list_owner.acct ) ? list_id : - 1L );
					setListOwner( ai, new_list_id );
				}
			} );
			break;
		case R.id.btnList:
			openListPicker();
			break;
		case R.id.btnCancel:
			try{
				dialog.cancel();
			}catch( Throwable ignored ){
			}
			break;
		case R.id.btnOk:
			addListMember( false );
			break;
		}
	}
	
	private void setListOwner( @Nullable SavedAccount a, long new_list_id ){
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
		
		loadLists( new_list_id );
	}
	
	@SuppressLint("StaticFieldLeak")
	private void loadLists( final long new_list_id ){
		
		if( list_owner == null ){
			showList( null, - 1L );
			return;
		}
		
		//noinspection deprecation
		final ProgressDialog progress = new ProgressDialog( activity );
		
		final AsyncTask< Void, String, TootApiResult > task = new AsyncTask< Void, String, TootApiResult >() {
			
			ArrayList< TootList > list_list = new ArrayList<>();
			
			void showProgress( final String sv ){
				Utils.runOnMainThread( new Runnable() {
					@Override public void run(){
						progress.setMessage( sv );
					}
				} );
			}
			
			@Override protected TootApiResult doInBackground( Void... params ){
				
				TootApiClient client = new TootApiClient( activity, new TootApiClient.Callback() {
					@Override public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override public void publishApiProgress( final String sv ){
						showProgress( sv );
					}
				} );
				
				client.setAccount( list_owner );
				String path_base = "/api/v1/lists?limit=" + Column.READ_LIMIT;
				
				// head
				TootApiResult result = client.request( path_base );
				if( result == null || result.array == null ){
					list_list = null;
					return result;
				}
				showProgress( activity.getString( R.string.parsing_response ) );
				list_list.addAll( TootList.parseList( result.array ) );
				String max_id;
				if( result.link_older == null ){
					max_id = null;
				}else{
					Matcher m = Column.reMaxId.matcher( result.link_older );
					max_id = m.find() ? m.group( 1 ) : null;
				}
				
				// trail
				while( max_id != null ){
					result = client.request( path_base + "&max_id=" + max_id );
					if( result == null || result.array == null ){
						list_list = null;
						return result;
					}
					showProgress( activity.getString( R.string.parsing_response ) );
					list_list.addAll( TootList.parseList( result.array ) );
					if( result.link_older == null ){
						max_id = null;
					}else{
						Matcher m = Column.reMaxId.matcher( result.link_older );
						max_id = m.find() ? m.group( 1 ) : null;
					}
				}
				return result;
			}
			
			@Override
			protected void onCancelled( TootApiResult result ){
				onPostExecute( null );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				try{
					progress.dismiss();
				}catch( Throwable ignored ){
				}
				
				showList( list_list, new_list_id );
				
				//noinspection StatementWithEmptyBody
				if( result != null
					&& ! TextUtils.isEmpty( result.error )
					&& ! ( result.response != null && result.response.code() == 404 )
					){
					Utils.showToast( activity, true, result.error );
				}
			}
		};
		
		progress.setIndeterminate( true );
		progress.setCancelable( false );
		progress.setOnCancelListener( new DialogInterface.OnCancelListener() {
			@Override public void onCancel( DialogInterface dialog ){
				task.cancel( true );
			}
		} );
		progress.show();
		task.executeOnExecutor( App1.task_executor );
		
	}
	
	private void showList( @Nullable ArrayList< TootList > _list, long new_list_id ){
		this.list_list = _list;
		
		if( list_list == null ){
			list_id = - 1L;
			btnList.setText( R.string.cant_access_list );
			return;
		}
		
		for( TootList l : list_list ){
			if( l.id == new_list_id ){
				this.list_id = new_list_id;
				btnList.setText( l.title );
				return;
			}
		}
		
		this.list_id = - 1L;
		btnList.setText( R.string.not_selected );
		
	}
	
	private void openListPicker(){
		if( list_list == null ) return;
		
		ActionsDialog ad = new ActionsDialog();
		ad.addAction( activity.getString( R.string.list_create ), new Runnable() {
			@Override public void run(){
				openListCreator();
			}
		} );
		for( TootList l : list_list ){
			final long list_id = l.id;
			ad.addAction( ! TextUtils.isEmpty( l.title ) ? l.title : Long.toString( list_id ), new Runnable() {
				@Override public void run(){
					showList( list_list, list_id );
				}
			} );
		}
		ad.show( activity, null );
	}
	
	private void openListCreator(){
		DlgTextInput.show( activity, activity.getString( R.string.list_create ), null, new DlgTextInput.Callback() {
			@Override public void onEmptyError(){
				Utils.showToast( activity, false, R.string.list_name_empty );
			}
			
			@Override public void onOK( final Dialog dialog, final String title ){
				
				//noinspection deprecation
				final ProgressDialog progress = new ProgressDialog( activity );
				
				@SuppressLint("StaticFieldLeak") final AsyncTask< Void, String, TootApiResult > task
					= new AsyncTask< Void, String, TootApiResult >() {
					
					void showProgress( final String sv ){
						Utils.runOnMainThread( new Runnable() {
							@Override public void run(){
								progress.setMessage( sv );
							}
						} );
					}
					
					@Override protected TootApiResult doInBackground( Void... params ){
						TootApiClient client = new TootApiClient( activity, new TootApiClient.Callback() {
							@Override public boolean isApiCancelled(){
								return isCancelled();
							}
							
							@Override public void publishApiProgress( String s ){
							}
						} );
						
						client.setAccount( list_owner );
						
						JSONObject content = new JSONObject();
						try{
							content.put( "title", title );
						}catch( Throwable ex ){
							return new TootApiResult( Utils.formatError( ex, "can't encoding json parameter." ) );
						}
						
						Request.Builder request_builder = new Request.Builder().post(
							RequestBody.create(
								TootApiClient.MEDIA_TYPE_JSON
								, content.toString()
							) );
						
						TootApiResult result = client.request( "/api/v1/lists", request_builder );
						showProgress( activity.getString( R.string.parsing_response ) );
						if( result != null ){
							if( result.object != null ){
								list = TootList.parse( result.object );
								
							}
						}
						
						return result;
					}
					
					TootList list;
					
					@Override
					protected void onCancelled( TootApiResult result ){
						onPostExecute( null );
					}
					
					@Override
					protected void onPostExecute( TootApiResult result ){
						
						try{
							progress.dismiss();
						}catch( Throwable ignored ){
						}
						
						//noinspection StatementWithEmptyBody
						if( result == null ){
							// cancelled.
						}else if( list != null ){
							for( Column column : activity.app_state.column_list ){
								column.onListListUpdated( list_owner );
							}
							
							Utils.showToast( activity, false, R.string.list_created );
							
							loadLists( list.id );
							
							try{
								dialog.dismiss();
							}catch( Throwable ignored ){
							}
						}else{
							Utils.showToast( activity, true, result.error );
						}
					}
					
				};
				
				progress.setIndeterminate( true );
				progress.setCancelable( false );
				progress.setOnCancelListener( new DialogInterface.OnCancelListener() {
					@Override public void onCancel( DialogInterface dialog ){
						task.cancel( true );
					}
				} );
				progress.show();
				task.executeOnExecutor( App1.task_executor );
			}
			
		} );
	}
	
	static final Pattern reFollowError = Pattern.compile( "follow", Pattern.CASE_INSENSITIVE );
	
	private void addListMember( final boolean bFollow ){
		if( list_owner == null || list_id == - 1L ){
			Utils.showToast( activity, false, R.string.list_not_selected );
			return;
		}
		
		//noinspection deprecation
		final ProgressDialog progress = new ProgressDialog( activity );
		
		@SuppressLint("StaticFieldLeak") final AsyncTask< Void, String, TootApiResult > task
			= new AsyncTask< Void, String, TootApiResult >() {
			
			void showProgress( final String sv ){
				Utils.runOnMainThread( new Runnable() {
					@Override public void run(){
						progress.setMessage( sv );
					}
				} );
			}
			
			@Override protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( activity, new TootApiClient.Callback() {
					@Override public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override public void publishApiProgress( String s ){
						showProgress( s );
					}
				} );
				
				TootApiResult result;
				
				client.setAccount( list_owner );
				
				// リストに追加したいアカウントの自タンスでのアカウントIDを取得する
				String path = "/api/v1/search?resolve=true&q=" + Uri.encode( target_user_full_acct );
				result = client.request( path );
				if( result == null || result.object == null ){
					return result;
				}else{
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
				}
				
				if( bFollow ){
					TootRelationShip relation;
					if( list_owner.isLocalUser( local_who ) ){
						Request.Builder request_builder = new Request.Builder().post(
							RequestBody.create(
								TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
								, "" // 空データ
							) );
						
						result = client.request( "/api/v1/accounts/" + local_who.id + "/follow", request_builder );
					}else{
						// リモートフォローする
						Request.Builder request_builder = new Request.Builder().post(
							RequestBody.create(
								TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
								, "uri=" + Uri.encode( local_who.acct )
							) );
						
						result = client.request( "/api/v1/follows", request_builder );
						if( result == null || result.object == null ) return result;
						
						TootAccount a = TootAccount.parse( activity, list_owner, result.object );
						if( a == null ){
							return new TootApiResult( "parse error." );
						}
						
						result = client.request( "/api/v1/accounts/relationships?id[]=" + a.id );
					}
					if( result == null || result.array == null ) return result;
					TootRelationShip.List relation_list = TootRelationShip.parseList( result.array );
					relation = relation_list.isEmpty() ? null : relation_list.get( 0 );
					
					if( relation == null ){
						return new TootApiResult( "parse error." );
					}else if( ! relation.following ){
						if( relation.requested ){
							return new TootApiResult( activity.getString( R.string.cant_add_list_follow_requesting ) );
						}else{
							// リモートフォローの場合、正常ケースでもここを通る場合がある
							// 何もしてはいけない…
						}
					}
				}
				
				JSONObject content = new JSONObject();
				try{
					JSONArray account_ids = new JSONArray();
					account_ids.put( Long.toString( local_who.id ) );
					content.put( "account_ids", account_ids );
				}catch( Throwable ex ){
					return new TootApiResult( Utils.formatError( ex, "can't encoding json parameter." ) );
				}
				
				Request.Builder request_builder = new Request.Builder().post(
					RequestBody.create(
						TootApiClient.MEDIA_TYPE_JSON
						, content.toString()
					) );
				
				return client.request( "/api/v1/lists/" + list_id + "/accounts", request_builder );
				
			}
			
			TootAccount local_who;
			
			@Override
			protected void onCancelled( TootApiResult result ){
				onPostExecute( null );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				
				try{
					progress.dismiss();
				}catch( Throwable ignored ){
				}
				
				//noinspection StatementWithEmptyBody
				if( result == null ){
					// cancelled.
				}else if( result.object != null ){
					for( Column column : activity.app_state.column_list ){
						column.onListMemberUpdated( list_owner, list_id, local_who, true );
					}
					
					Utils.showToast( activity, false, R.string.list_member_added );
					
					try{
						dialog.dismiss();
					}catch( Throwable ignored ){
					}
				}else{
					
					if( result.response != null
						&& result.response.code() == 422
						&& result.error != null && reFollowError.matcher( result.error ).find()
						){
						
						if( ! bFollow ){
							DlgConfirm.openSimple(
								activity
								, activity.getString( R.string.list_retry_with_follow, target_user_full_acct )
								, new Runnable() {
									@Override public void run(){
										addListMember( true );
									}
								}
							);
						}else{
							new AlertDialog.Builder( activity )
								.setCancelable( true )
								.setMessage( R.string.cant_add_list_follow_requesting )
								.setNeutralButton( R.string.close, null )
								.show();
						}
						return;
					}
					
					Utils.showToast( activity, true, result.error );
					
				}
			}
			
		};
		
		progress.setIndeterminate( true );
		progress.setCancelable( false );
		progress.setOnCancelListener( new DialogInterface.OnCancelListener() {
			@Override public void onCancel( DialogInterface dialog ){
				task.cancel( true );
			}
		} );
		progress.show();
		task.executeOnExecutor( App1.task_executor );
	}
	
}
