package jp.juggler.subwaytooter;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import java.util.concurrent.atomic.AtomicReference;

public class ActCallback extends AppCompatActivity {
	static final AtomicReference< Uri > last_uri = new AtomicReference<>( null );
	
	@Override protected void onCreate( @Nullable Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		Intent intent = getIntent();
		if( intent != null ){
			Uri uri = intent.getData();
			if( uri != null ){
				
				last_uri.set( uri );
				
				intent = new Intent( this, ActMain.class );
				intent.addFlags( Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY | Intent.FLAG_ACTIVITY_NEW_TASK );
				startActivity( intent );
				finish();
			}
		}
	}
}
