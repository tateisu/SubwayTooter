package jp.juggler.subwaytooter;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator;
import com.bumptech.glide.load.engine.executor.GlideExecutor;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;

import jp.juggler.subwaytooter.util.LogCategory;

import static com.bumptech.glide.load.engine.executor.GlideExecutor.newDiskCacheExecutor;
import static com.bumptech.glide.load.engine.executor.GlideExecutor.newSourceExecutor;

@GlideModule
public class MyAppGlideModule extends AppGlideModule {
static final LogCategory log = new LogCategory( "MyAppGlideModule" );
	
	// v3との互換性のためにAndroidManifestを読むかどうか(デフォルトtrue)
	@Override public boolean isManifestParsingEnabled() {
		return false;
	}
	
	@Override public void registerComponents( @NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
		// デフォルト実装は何もしないらしい
		super.registerComponents( context,glide,registry );
		
		// App1を初期化してからOkHttp3Factoryと連動させる
		App1.Companion.prepare( context.getApplicationContext() );
		App1.Companion.registerGlideComponents(context,glide,registry);
	}
	
	@Override
	public void applyOptions(Context context, GlideBuilder builder) {
		// デフォルト実装は何もしないらしい
		super.applyOptions( context,builder );
		
		// App1を初期化してから色々する
		App1.Companion.prepare( context.getApplicationContext() );
		App1.Companion.applyGlideOptions(context,builder);
	}
}
