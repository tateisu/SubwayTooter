package jp.juggler.subwaytooter;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import jp.juggler.subwaytooter.util.HTMLDecoder;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;
import jp.juggler.subwaytooter.view.MyLinkMovementMethod;
import jp.juggler.subwaytooter.view.MyListView;

class HeaderViewHolderSearchDesc extends HeaderViewHolderBase {
	private static final LogCategory log = new LogCategory( "HeaderViewHolderSearchDesc" );
	
	HeaderViewHolderSearchDesc( ActMain arg_activity, Column arg_column, MyListView parent, String html ){
		super( arg_activity, arg_column
			, arg_activity.getLayoutInflater().inflate( R.layout.lv_header_search_desc, parent, false )
		);
		
		Utils.scanView( viewRoot, new Utils.ScanViewCallback() {
			@Override public void onScanView( View v ){
				try{
					if( v instanceof Button ){
						// ボタンは太字なので触らない
					}else if( v instanceof TextView ){
						if( activity.timeline_font != null ){
							( (TextView) v ).setTypeface( activity.timeline_font );
						}
						if( ! Float.isNaN( activity.timeline_font_size_sp ) ){
							( (TextView) v ).setTextSize( activity.timeline_font_size_sp );
						}
					}
				}catch( Throwable ex ){
					log.trace( ex );
				}
			}
		} );
		
		CharSequence sv = HTMLDecoder.decodeHTML( activity, access_info, html, false, true, null ,null);
		
		TextView tvSearchDesc = viewRoot.findViewById( R.id.tvSearchDesc );
		tvSearchDesc.setVisibility( View.VISIBLE );
		tvSearchDesc.setMovementMethod( MyLinkMovementMethod.getInstance() );
		tvSearchDesc.setText( sv );
	}
	
	@Override void showColor(){
		//
	}
	
	@Override void bindData( Column column ){
		//
	}
}
