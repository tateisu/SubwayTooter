package jp.juggler.subwaytooter.api;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public interface TootTask {

	// 非同期処理をここに実装する
	TootApiResult background( @NonNull TootApiClient client );

	// 非同期処理が終わったらメインスレッドから実行される
	// 処理がキャンセルされた場合、 resultはnullになるかもしれない
	void handleResult( @Nullable TootApiResult result );
	
}
