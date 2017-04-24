//package jp.juggler.subwaytooter;
//
//import android.os.AsyncTask;
//import android.os.SystemClock;
//import android.support.annotation.NonNull;
//
//import java.util.HashMap;
//
//import jp.juggler.subwaytooter.api.TootApiClient;
//import jp.juggler.subwaytooter.api.TootApiResult;
//import jp.juggler.subwaytooter.api.entity.TootRelationShip;
//import jp.juggler.subwaytooter.table.SavedAccount;
//import jp.juggler.subwaytooter.util.LogCategory;
//
//class RelationshipMap {
//
//	private static final LogCategory log = new LogCategory( "RelationshipMap" );
//
//
//	interface UpdateCallback {
//		void onRelationShipUpdate();
//	}
//
//	private static class RelationshipPerAccount extends HashMap< Long, TootRelationShip > {
//		long last_update;
//	}
//
//	private final HashMap< String, RelationshipPerAccount > map_account = new HashMap<>();
//
//	@NonNull
//	private RelationshipPerAccount getRelationshipPerAccount( @NonNull SavedAccount access_info ){
//		RelationshipPerAccount ra;
//		ra = map_account.get( access_info.acct );
//		if( ra == null ){
//			ra = new RelationshipPerAccount();
//			map_account.put( access_info.acct, ra );
//		}
//		return ra;
//	}
//
//	public TootRelationShip get( @NonNull SavedAccount access_info, long id ){
//		return getRelationshipPerAccount( access_info ).get( id );
//	}
//
//	public void put( @NonNull SavedAccount access_info, @NonNull TootRelationShip relation ){
//		getRelationshipPerAccount( access_info ).put( relation.id,relation );
//	}
//
//	public void addFollowing( SavedAccount access_info, long id ){
//		RelationshipPerAccount ra = getRelationshipPerAccount( access_info );
//		TootRelationShip rs = ra.get( id);
//		if(rs == null ){
//			rs = new TootRelationShip();
//			ra.put( id, rs );
//		}
//		rs.following = true;
//	}
//
//	void checkUpdate( @NonNull final ActMain activity, @NonNull final SavedAccount access_info, final UpdateCallback callback ){
//		final RelationshipPerAccount ra = getRelationshipPerAccount( access_info );
//		long now = SystemClock.elapsedRealtime();
//		if( now - ra.last_update < 300000L ) return;
//		ra.last_update = now;
//
//		new AsyncTask< Void, Void, TootApiResult >() {
//
//			TootRelationShip.List list;
//
//			@Override protected TootApiResult doInBackground( Void... params ){
//
//				TootApiClient client = new TootApiClient( activity, new TootApiClient.Callback() {
//					@Override public boolean isApiCancelled(){
//						return isCancelled();
//					}
//
//					@Override
//					public void publishApiProgress( final String s ){
//					}
//				} );
//
//				client.setAccount( access_info );
//				TootApiResult result = client.request( "/api/v1/accounts/relationships" );
//				if( result != null && result.array != null ){
//					list = TootRelationShip.parseList( log, result.array );
//				}
//				return result;
//			}
//
//			@Override
//			protected void onCancelled( TootApiResult result ){
//				onPostExecute( null );
//			}
//
//			@Override
//			protected void onPostExecute( TootApiResult result ){
//
//				if( isCancelled() || result == null ){
//					return;
//				}
//				if( list != null ){
//					for( TootRelationShip item : list ){
//						ra.put( item.id, item );
//					}
//					callback.onRelationShipUpdate();
//				}
//			}
//		}.execute();
//	}
//}
