package jp.juggler.subwaytooter.view;

import android.graphics.Bitmap;

import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.resource.gif.GifDrawable;

class MyGifDrawable extends GifDrawable {
	
	MyGifDrawable( GifDrawable other, Bitmap firstFrame, Transformation< Bitmap > frameTransformation ){
		super( other, firstFrame, frameTransformation );
	}
	
	// このクラスに追加された機能は特にないが、GifDrawableから差し替え済みであることを検出できるようにするため派生クラスとしている
}
