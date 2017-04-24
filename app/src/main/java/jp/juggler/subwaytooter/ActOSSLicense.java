package jp.juggler.subwaytooter;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import jp.juggler.subwaytooter.util.Utils;

public class ActOSSLicense extends AppCompatActivity{
	@Override protected void onCreate( @Nullable Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		setContentView( R.layout.act_oss_license);

		try{
			InputStream is = getResources().openRawResource( R.raw.oss_license );
			try{
				ByteArrayOutputStream bao = new ByteArrayOutputStream(  );
				IOUtils.copy( is,bao );
				String text = Utils.decodeUTF8(bao.toByteArray());
				TextView tv = (TextView) findViewById( R.id.tvText );
				tv.setText(text);
			}finally{
				IOUtils.closeQuietly( is );
			}
			
		}catch(Throwable ex){
			ex.printStackTrace(  );
		}

		
	}
}
