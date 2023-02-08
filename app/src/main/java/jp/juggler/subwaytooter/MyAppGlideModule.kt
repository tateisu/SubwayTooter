package jp.juggler.subwaytooter

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.PictureDrawable
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.webp.decoder.*
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.ArrayPool
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.SimpleResource
import com.bumptech.glide.load.resource.bitmap.BitmapDrawableDecoder
import com.bumptech.glide.load.resource.transcode.ResourceTranscoder
import com.bumptech.glide.module.AppGlideModule
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGParseException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.min

@GlideModule
class MyAppGlideModule : AppGlideModule() {

    companion object {

        private val svgSig = "<svg".toByteArray(Charsets.UTF_8)

        private fun findBytes(data: ByteArray, dataSize: Int = data.size, key: ByteArray): Int {

            fun check(start: Int): Boolean {
                for (j in key.indices) {
                    if (data[start + j] != key[j]) return false
                }
                return true
            }

            for (i in 0..dataSize - key.size) {
                if (check(i)) return i
            }
            return -1
        }
    }

    // Decodes an SVG internal representation from an [InputStream].
    inner class SvgDecoder : ResourceDecoder<InputStream, SVG> {

        @Throws(IOException::class)
        override fun handles(source: InputStream, options: Options): Boolean {
            val size = min(source.available(), 1024)
            if (size <= 0) return false
            val buf = ByteArray(size)
            val nRead = source.read(buf, 0, size)
            return -1 != findBytes(buf, nRead, svgSig)
        }

        @Throws(IOException::class)
        override fun decode(
            source: InputStream,
            width: Int,
            height: Int,
            options: Options,
        ): Resource<SVG> {
            try {
                val svg = SVG.getFromInputStream(source)
                return SimpleResource(svg)
            } catch (ex: SVGParseException) {
                throw IOException("Cannot load SVG from stream", ex)
            }
        }
    }

    // Convert the [SVG]'s internal representation to an Android-compatible one ([Picture]).
    class SvgDrawableTranscoder : ResourceTranscoder<SVG, PictureDrawable> {

        override fun transcode(
            toTranscode: Resource<SVG>,
            options: Options,
        ): Resource<PictureDrawable> {
            val svg = toTranscode.get()
            val picture = svg.renderToPicture()
            val drawable = PictureDrawable(picture)
            return SimpleResource(drawable)
        }
    }

    // v3との互換性のためにAndroidManifestを読むかどうか(デフォルトtrue)
    override fun isManifestParsingEnabled(): Boolean {
        return false
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        // デフォルト実装は何もしないらしい
        super.registerComponents(context, glide, registry)

        // App1を初期化してからOkHttp3Factoryと連動させる
        App1.prepare(context.applicationContext, "MyAppGlideModule.registerComponents()")
        App1.registerGlideComponents(context, glide, registry)

        //SVGデコーダーの追加
        registry
            .register(SVG::class.java, PictureDrawable::class.java, SvgDrawableTranscoder())
            .append(InputStream::class.java, SVG::class.java, SvgDecoder())

        ///////
        // Animated WebP

//        // We should put our decoder before the build-in decoders,
//        // because the Downsampler will consume arbitrary data and make the inputstream corrupt
//        // on some devices
//        val resources: Resources = context.resources
//        val bitmapPool: BitmapPool = glide.bitmapPool
//        val arrayPool: ArrayPool = glide.arrayPool
//        /* static webp decoders */
//        val webpDownsampler = WebpDownsampler(
//            registry.imageHeaderParsers,
//            resources.getDisplayMetrics(), bitmapPool, arrayPool
//        )
//        val bitmapDecoder = AnimatedWebpBitmapDecoder(arrayPool, bitmapPool)
//        val byteBufferBitmapDecoder = ByteBufferBitmapWebpDecoder(webpDownsampler)
//        val streamBitmapDecoder = StreamBitmapWebpDecoder(webpDownsampler, arrayPool)
//        /* animate webp decoders */
//        val byteBufferWebpDecoder = ByteBufferWebpDecoder(context, arrayPool, bitmapPool)
//        registry /* Bitmaps for static webp images */
//            .prepend(
//                Registry.BUCKET_BITMAP,
//                ByteBuffer::class.java,
//                Bitmap::class.java, byteBufferBitmapDecoder
//            )
//            .prepend(
//                Registry.BUCKET_BITMAP,
//                InputStream::class.java,
//                Bitmap::class.java, streamBitmapDecoder
//            ) /* BitmapDrawables for static webp images */
//            .prepend(
//                Registry.BUCKET_BITMAP_DRAWABLE,
//                ByteBuffer::class.java,
//                BitmapDrawable::class.java,
//                BitmapDrawableDecoder(resources, byteBufferBitmapDecoder)
//            )
//            .prepend(
//                Registry.BUCKET_BITMAP_DRAWABLE,
//                InputStream::class.java,
//                BitmapDrawable::class.java,
//                BitmapDrawableDecoder(resources, streamBitmapDecoder)
//            ) /* Bitmaps for animated webp images*/
//            .prepend(
//                Registry.BUCKET_BITMAP,
//                ByteBuffer::class.java,
//                Bitmap::class.java, ByteBufferAnimatedBitmapDecoder(bitmapDecoder)
//            )
//            .prepend(
//                Registry.BUCKET_BITMAP,
//                InputStream::class.java,
//                Bitmap::class.java, StreamAnimatedBitmapDecoder(bitmapDecoder)
//            ) /* Animated webp images */
//            .prepend(ByteBuffer::class.java, WebpDrawable::class.java, byteBufferWebpDecoder)
//            .prepend(
//                InputStream::class.java,
//                WebpDrawable::class.java, StreamWebpDecoder(byteBufferWebpDecoder, arrayPool)
//            )
//            .prepend(WebpDrawable::class.java, WebpDrawableEncoder())
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // デフォルト実装は何もしないらしい
        super.applyOptions(context, builder)

        // App1を初期化してから色々する
        App1.prepare(context.applicationContext, "MyAppGlideModule.applyOptions()")
        App1.applyGlideOptions(context, builder)
    }
}
