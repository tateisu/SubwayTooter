package jp.juggler.subwaytooter;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ActAbout extends AppCompatActivity {
	
	static final String EXTRA_SEARCH = "search";
	
	static final String url_store = "https://play.google.com/store/apps/details?id=jp.juggler.subwaytooter";
//	static final String url_enty = "https://enty.jp/3WtlzHG10wZv";
	static final String developer_acct = "tateisu@mastodon.juggler.jp";
	
	static final String url_futaba = "https://www.instagram.com/hinomoto_hutaba/";
	
	static final String[] contributors = new String[]{
		"@Balor@freeradical.zone", "update english language",
	};
	
	@Override protected void onCreate( @Nullable Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		App1.setActivityTheme( this, false );
		setContentView( R.layout.act_about );
		
		Styler.fixHorizontalPadding(findViewById( R.id.svContent ));
		
		
		try{
			PackageInfo pInfo = getPackageManager().getPackageInfo( getPackageName(), 0 );
			( (TextView) findViewById( R.id.tvVersion ) ).setText( getString( R.string.version_is, pInfo.versionName ) );
		}catch( PackageManager.NameNotFoundException ex ){
			ex.printStackTrace();
		}
		Button b;
		
		b = (Button) findViewById( R.id.btnDeveloper );
		b.setText( getString( R.string.search_for, developer_acct ) );
		b.setOnClickListener( new View.OnClickListener() {
			@Override public void onClick( View v ){
				Intent data = new Intent();
				data.putExtra( EXTRA_SEARCH, developer_acct );
				setResult( RESULT_OK, data );
				finish();
			}
		} );
		
		b = (Button) findViewById( R.id.btnRate );
		b.setText( url_store );
		b.setOnClickListener( new View.OnClickListener() {
			@Override public void onClick( View v ){
				open_browser( url_store );
			}
		} );
		
		b = (Button) findViewById( R.id.btnIconDesign );
		b.setText( url_futaba );
		b.setOnClickListener( new View.OnClickListener() {
			@Override public void onClick( View v ){
				open_browser( url_futaba );
			}
		} );
		
		
//		b = (Button) findViewById( R.id.btnDonate );
//		b.setText( url_enty );
//		b.setOnClickListener( new View.OnClickListener() {
//			@Override public void onClick( View v ){
//				open_browser( url_enty );
//			}
//		} );
		
		LinearLayout ll = (LinearLayout) findViewById( R.id.llContributors );
		float density = getResources().getDisplayMetrics().density;
		int margin_top = (int) ( 0.5f + density * 8 );
		int padding = (int) ( 0.5f + density * 8 );
		
		for( int i = 0, ie = contributors.length ; i < ie ; i += 2 ){
			final String acct = contributors[ i ];
			final String works = contributors[ i + 1 ];
			
			b = new Button( this );
			//
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams( LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT );
			if( i < 0 ) lp.topMargin = margin_top;
			b.setLayoutParams( lp );
			//
			b.setBackgroundResource( R.drawable.btn_bg_transparent );
			b.setPadding( padding, padding, padding, padding );
			b.setAllCaps( false );
			//
			b.setText( getString( R.string.search_for, acct ) + "\n" + getString( R.string.thanks_for, works ) );
			b.setOnClickListener( new View.OnClickListener() {
				@Override public void onClick( View v ){
					Intent data = new Intent();
					data.putExtra( EXTRA_SEARCH, acct );
					setResult( RESULT_OK, data );
					finish();
				}
			} );
			//
			ll.addView( b );
		}
	}
	
	void open_browser( String url ){
		try{
			Intent intent = new Intent( Intent.ACTION_VIEW, Uri.parse( url ) );
			startActivity( intent );
		}catch( Throwable ex ){
			ex.printStackTrace();
		}
	}
}
