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
import android.widget.TextView;

public class ActAbout extends AppCompatActivity {
	
	static final String url_store = "https://play.google.com/store/apps/details?id=jp.juggler.subwaytooter";
	static final String url_enty = "https://enty.jp/3WtlzHG10wZv";
	
	@Override protected void onCreate( @Nullable Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		setContentView( R.layout.act_about );
		
		try{
			PackageInfo pInfo = getPackageManager().getPackageInfo( getPackageName(), 0 );
			( (TextView) findViewById( R.id.tvVersion ) ).setText( getString( R.string.version_is, pInfo.versionName ) );
		}catch( PackageManager.NameNotFoundException ex ){
			ex.printStackTrace();
		}
		
		Button b = (Button) findViewById( R.id.btnRate );
		b.setText( url_store );
		b.setOnClickListener( new View.OnClickListener() {
			@Override public void onClick( View v ){
				open_browser( url_store );
			}
		} );

		b = (Button) findViewById( R.id.btnDonate );
		b.setText( url_enty );
		b.setOnClickListener( new View.OnClickListener() {
			@Override public void onClick( View v ){
				open_browser( url_enty );
			}
		} );
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
