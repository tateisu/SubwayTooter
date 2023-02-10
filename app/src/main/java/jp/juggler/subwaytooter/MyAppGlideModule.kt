package jp.juggler.subwaytooter

import android.content.Context
import android.graphics.drawable.PictureDrawable
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.webp.decoder.*
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.resource.SimpleResource
import com.bumptech.glide.load.resource.drawable.DrawableResource
import com.bumptech.glide.load.resource.transcode.ResourceTranscoder
import com.bumptech.glide.module.AppGlideModule
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGParseException
import com.github.penfeizhou.animation.apng.APNGDrawable
import com.github.penfeizhou.animation.apng.decode.APNGDecoder
import com.github.penfeizhou.animation.apng.decode.APNGParser
import com.github.penfeizhou.animation.io.ByteBufferReader
import com.github.penfeizhou.animation.loader.ByteBufferLoader
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

    // SVG: InputStream => SVG
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

    // SVG: transcode SVG => PictureDrawable
    class SvgDrawableTranscoder : ResourceTranscoder<SVG, PictureDrawable> {
        override fun transcode(
            toTranscode: Resource<SVG>,
            options: Options,
        ) = try {
            SimpleResource(PictureDrawable(toTranscode.get().renderToPicture()))
        } catch (ex: Throwable) {
            throw IOException("Cannot render SVG.", ex)
        }
    }

    // APNG: transcode ByteBuffer => APNGDecoder
    class ApngByteBufferDecoder : ResourceDecoder<ByteBuffer, APNGDecoder> {
        override fun handles(source: ByteBuffer, options: Options) =
            source.limit() >= 8 && APNGParser.isAPNG(ByteBufferReader(source))

        override fun decode(
            source: ByteBuffer,
            width: Int,
            height: Int,
            options: Options,
        ) = object : Resource<APNGDecoder> {
            val decoder = object : ByteBufferLoader() {
                override fun getByteBuffer() = source.apply { position(0) }
            }.let { APNGDecoder(it, null) }

            override fun getResourceClass() = APNGDecoder::class.java
            override fun getSize() = source.limit()
            override fun get() = decoder
            override fun recycle() = decoder.stop()
        }
    }

    // APNG: transcode APNGDecoder => APNGDrawable
    class ApngTranscoder : ResourceTranscoder<APNGDecoder, APNGDrawable> {
        override fun transcode(
            toTranscode: Resource<APNGDecoder>,
            options: Options,
        ) = APNGDrawable(toTranscode.get()).apply { setAutoPlay(false) }.let {
            object : DrawableResource<APNGDrawable>(it) {
                override fun getResourceClass() = APNGDrawable::class.java
                override fun getSize() = it.memorySize
                override fun recycle() = it.stop()
            }
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
            .prepend(ByteBuffer::class.java, APNGDecoder::class.java, ApngByteBufferDecoder())
            .register(APNGDecoder::class.java, APNGDrawable::class.java, ApngTranscoder())
            .register(SVG::class.java, PictureDrawable::class.java, SvgDrawableTranscoder())
            .append(InputStream::class.java, SVG::class.java, SvgDecoder())
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // デフォルト実装は何もしないらしい
        super.applyOptions(context, builder)

        // App1を初期化してから色々する
        App1.prepare(context.applicationContext, "MyAppGlideModule.applyOptions()")
        App1.applyGlideOptions(context, builder)
    }
}
