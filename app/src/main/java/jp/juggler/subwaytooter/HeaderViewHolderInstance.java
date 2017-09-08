package jp.juggler.subwaytooter;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import jp.juggler.subwaytooter.api.entity.TootInstance;
import jp.juggler.subwaytooter.util.HTMLDecoder;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;
import jp.juggler.subwaytooter.view.MyLinkMovementMethod;
import jp.juggler.subwaytooter.view.MyListView;

class HeaderViewHolderInstance extends HeaderViewHolderBase implements View.OnClickListener {
	
	private static final LogCategory log = new LogCategory( "HeaderViewHolderInstance" );
	
	private final TextView btnInstance;
	private final TextView tvVersion;
	private final TextView tvTitle;
	private final TextView btnEmail;
	private final TextView tvDescription;
	
	HeaderViewHolderInstance( ActMain arg_activity, Column arg_column, MyListView parent ){
		super( arg_activity, arg_column
			, arg_activity.getLayoutInflater().inflate( R.layout.lv_header_instance, parent, false )
		);
		
		if( activity.timeline_font != null ){
			Utils.scanView( viewRoot, new Utils.ScanViewCallback() {
				@Override public void onScanView( View v ){
					try{
						if( v instanceof Button ){
							// ボタンは太字なので触らない
						}else if( v instanceof TextView ){
							( (TextView) v ).setTypeface( activity.timeline_font );
						}
					}catch( Throwable ex ){
						log.trace( ex );
					}
				}
			} );
		}
		//
		//		CharSequence sv = HTMLDecoder.decodeHTML( activity, access_info, html, false, true, null );
		//
		//		TextView tvSearchDesc = (TextView) viewRoot.findViewById( R.id.tvSearchDesc );
		//		tvSearchDesc.setVisibility( View.VISIBLE );
		//		tvSearchDesc.setMovementMethod( MyLinkMovementMethod.getInstance() );
		//		tvSearchDesc.setText( sv );
		
		btnInstance = viewRoot.findViewById( R.id.btnInstance );
		tvVersion = viewRoot.findViewById( R.id.tvVersion );
		tvTitle = viewRoot.findViewById( R.id.tvTitle );
		btnEmail = viewRoot.findViewById( R.id.btnEmail );
		tvDescription = viewRoot.findViewById( R.id.tvDescription );
		
		btnInstance.setOnClickListener( this );
		btnEmail.setOnClickListener( this );
		
		tvDescription.setMovementMethod( MyLinkMovementMethod.getInstance() );
	}
	
	@Override void showColor(){
		//
	}
	
	private TootInstance instance;
	private Column column;
	
	@Override void bindData( Column column ){
		this.column = column;
		this.instance = column.instance_information;
		
		if( instance == null ){
			btnInstance.setText( "?" );
			tvVersion.setText( "?" );
			tvTitle.setText( "?" );
			btnEmail.setText( "?" );
			btnEmail.setEnabled( false );
			tvDescription.setText( "?" );
		}else{
			btnInstance.setText( supplyEmpty( instance.uri ) );
			btnInstance.setEnabled( ! TextUtils.isEmpty( instance.uri ) );
			tvVersion.setText( supplyEmpty( instance.version ) );
			tvTitle.setText( supplyEmpty( instance.title ) );
			btnEmail.setText( supplyEmpty( instance.email ) );
			btnEmail.setEnabled( ! TextUtils.isEmpty( instance.email ) );
			
			SpannableStringBuilder sb = HTMLDecoder.decodeHTML( activity, access_info, "<p>"+supplyEmpty( instance.description )+"</p>", false, true, null ,null);
			int previous_br_count =0;
			for(int i=0;i<sb.length();++i){
				char c = sb.charAt( i );
				if( c != '\n' ){
					previous_br_count = 0;
				}else{
					++previous_br_count;
					if( previous_br_count >= 3){
						sb.delete(  i,i+1 );
						--previous_br_count;
						--i;
					}
				}
			}
			tvDescription.setText( sb );
		}
	}
	
	@Override public void onClick( View v ){
		switch( v.getId() ){
		case R.id.btnInstance:
			if( instance != null && instance.uri != null ){
				activity.openChromeTab( activity.nextPosition( column ), column.access_info, "https://"+ instance.uri +"/about" ,true);
			}
			break;
		case R.id.btnEmail:
			if( instance != null && instance.email != null ){
				try{
					Intent intent = new Intent( Intent.ACTION_SEND );
					intent.setType( "text/plain" );
					intent.putExtra( Intent.EXTRA_EMAIL, new String[]{ instance.email } );
					intent.putExtra( Intent.EXTRA_TEXT, instance.email );
					activity.startActivity( intent );
					
				}catch( Throwable ex ){
					ex.printStackTrace();
					Utils.showToast( activity, true, R.string.missing_mail_app );
				}
			}
			break;
		}
	}
	
	//	private void setContent( @NonNull WebView wv, @NonNull String html, @NonNull String mime_type ){
	//		html = "<html><meta charset=\"UTF-8\"><p>"+html+"</p></html>";
	//		wv.clearHistory();
	//		wv.loadData( Base64.encodeToString( Utils.encodeUTF8(html) ,Base64.NO_WRAP), mime_type+";charset=UTF-8", "base64");
	//	}
	
	@NonNull private String supplyEmpty( @Nullable String s ){
		return s != null ? s : "";
	}
}
