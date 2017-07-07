package jp.juggler.subwaytooter;

import android.app.SearchManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.table.MutedWord;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.HTMLDecoder;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class ActText extends AppCompatActivity implements View.OnClickListener {
	
	static final LogCategory log = new LogCategory( "ActText" );
	static final String EXTRA_TEXT = "text";
	static final String EXTRA_CONTENT_START = "content_start";
	
	static void encodeStatus( Intent intent, Context context, SavedAccount access_info, TootStatus status ){
		StringBuilder sb = new StringBuilder();
		sb.append( context.getString( R.string.send_header_url ) );
		sb.append( ": " );
		sb.append( status.url );
		sb.append( "\n" );
		sb.append( context.getString( R.string.send_header_date ) );
		sb.append( ": " );
		sb.append( TootStatus.formatTime( status.time_created_at ) );
		sb.append( "\n" );
		sb.append( context.getString( R.string.send_header_from_acct ) );
		sb.append( ": " );
		sb.append( access_info.getFullAcct( status.account ) );
		sb.append( "\n" );
		if( status.account != null ){
			sb.append( context.getString( R.string.send_header_from_name ) );
			sb.append( ": " );
			sb.append( status.account.display_name );
			sb.append( "\n" );
		}
		if( ! TextUtils.isEmpty( status.spoiler_text ) ){
			sb.append( context.getString( R.string.send_header_content_warning ) );
			sb.append( ": " );
			sb.append( HTMLDecoder.decodeHTML( access_info, status.spoiler_text ,false,null) );
			sb.append( "\n" );
		}
		sb.append( "\n" );

		intent.putExtra( EXTRA_CONTENT_START, sb.length() );

		sb.append( HTMLDecoder.decodeHTML( access_info, status.content ,false,null) );
	
		intent.putExtra( EXTRA_TEXT, sb.toString() );
		
		
	}
	

	public static void open( ActMain activity, SavedAccount access_info, TootStatus status ){
		Intent intent = new Intent( activity, ActText.class );
		encodeStatus( intent,activity, access_info, status );
		
		activity.startActivity( intent );
	}
	
	@Override protected void onCreate( @Nullable Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		App1.setActivityTheme( this, false );
		initUI();
		
		
		if( savedInstanceState == null ){
			Intent intent = getIntent();
			String sv = intent.getStringExtra( EXTRA_TEXT );
			int content_start = intent.getIntExtra( EXTRA_CONTENT_START, 0);
			etText.setText(sv);
			etText.setSelection( content_start,sv.length() );
		}
	}

	EditText etText;
	
	void initUI(){
		setContentView( R.layout.act_text );
		
		Styler.fixHorizontalMargin(findViewById( R.id.svFooterBar ));
		Styler.fixHorizontalMargin(findViewById( R.id.svContent ));
		
		
		
		etText = (EditText) findViewById( R.id.etText );
		
		findViewById( R.id.btnCopy ).setOnClickListener( this );
		findViewById( R.id.btnSearch ).setOnClickListener( this );
		findViewById( R.id.btnSend ).setOnClickListener( this );
		findViewById( R.id.btnMuteWord ).setOnClickListener( this );
		
	}
	
	@Override public void onClick( View v ){
		switch( v.getId() ){
		case R.id.btnCopy:
			copy();
			break;
		case R.id.btnSearch:
			search();
			break;
		case R.id.btnSend:
			send();
			
			break;
		case R.id.btnMuteWord:
			muteWord();
			break;
			
		}
	}
	
	private String getSelection(){
		int s = etText.getSelectionStart();
		int e = etText.getSelectionEnd();
		String text = etText.getText().toString();
		if( s == e ){
			return text;
		}else{
			return text.substring( s, e );
		}
	}
	
	private void copy(){
		try{
			// Gets a handle to the clipboard service.
			ClipboardManager clipboard = (ClipboardManager)
				getSystemService( Context.CLIPBOARD_SERVICE );
			// Creates a new text clip to put on the clipboard
			ClipData clip = ClipData.newPlainText( "text", getSelection() );
			// Set the clipboard's primary clip.
			clipboard.setPrimaryClip( clip );
			
			Utils.showToast( this,false,R.string.copy_complete );
		}catch( Throwable ex ){
			ex.printStackTrace();
			Utils.showToast( this, ex, "copy failed." );
		}
	}
	
	private void search(){
		try{
			Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
			intent.putExtra( SearchManager.QUERY, getSelection() );
			if( intent.resolveActivity(getPackageManager()) != null ) {
				startActivity(intent);
			}
		}catch( Throwable ex ){
			ex.printStackTrace();
			Utils.showToast( this, ex, "search failed." );
		}
		
	}
	
	private void send(){
		try{
			
			Intent intent = new Intent();
			intent.setAction( Intent.ACTION_SEND );
			intent.setType( "text/plain" );
			intent.putExtra( Intent.EXTRA_TEXT, getSelection() );
			startActivity( intent );
			
		}catch( Throwable ex ){
			ex.printStackTrace();
			Utils.showToast( this, ex, "send failed." );
		}
	}
	
	private void muteWord(){
		try{
			MutedWord.save( getSelection() );
			for( Column column : App1.getAppState( this ).column_list ){
				column.removeMuteApp();
			}
			Utils.showToast( this, false, R.string.word_was_muted );
		}catch( Throwable ex ){
			ex.printStackTrace();
			Utils.showToast( this, ex, "muteWord failed." );
		}
	}
	
}
