package jp.juggler.subwaytooter

import android.content.Context

import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule

@GlideModule
class MyAppGlideModule : AppGlideModule() {
	
	// v3との互換性のためにAndroidManifestを読むかどうか(デフォルトtrue)
	override fun isManifestParsingEnabled() : Boolean {
		return false
	}
	
	override fun registerComponents(context : Context, glide : Glide, registry : Registry) {
		// デフォルト実装は何もしないらしい
		super.registerComponents(context, glide, registry)
		
		// App1を初期化してからOkHttp3Factoryと連動させる
		App1.prepare(context.applicationContext)
		App1.registerGlideComponents(context, glide, registry)
	}
	
	override fun applyOptions(context : Context, builder : GlideBuilder) {
		// デフォルト実装は何もしないらしい
		super.applyOptions(context, builder)
		
		// App1を初期化してから色々する
		App1.prepare(context.applicationContext)
		App1.applyGlideOptions(context, builder)
	}
	
}
