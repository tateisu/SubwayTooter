package jp.juggler.subwaytooter.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import jp.juggler.util.log.LogCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine

private val log = LogCategory("LoadIcon")

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Context.loadIcon(url: String?, size: Int): Bitmap? = try {
    suspendCancellableCoroutine { cont ->
        @Suppress("ThrowableNotThrown")
        val target = object : CustomTarget<Bitmap>() {
            override fun onLoadFailed(errorDrawable: Drawable?) {
                if (cont.isActive) cont.resume(null) {}
                if (!url.isNullOrEmpty()) log.w("onLoadFailed. url=$url")
            }

            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                if (cont.isActive) cont.resume(resource) { resource.recycle() }
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                if (cont.isActive) cont.resume(null) {}
                if (!url.isNullOrEmpty()) log.w("onLoadCleared. url=$url")
            }
        }
        Glide.with(this)
            .asBitmap()
            .load(url)
            .override(size)
            .into(target)
        cont.invokeOnCancellation {
            Glide.with(this).clear(target)
        }
    }
} catch (ex: Throwable) {
    log.w(ex, "url=$url")
    null
}
