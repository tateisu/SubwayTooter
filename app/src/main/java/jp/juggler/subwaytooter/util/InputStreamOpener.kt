package jp.juggler.subwaytooter.util

import android.content.ContentResolver
import android.net.Uri
import jp.juggler.util.data.getStreamSize
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

// contentLengthの測定などで複数回オープンする必要がある
abstract class InputStreamOpener {
    abstract val mimeType: String
    abstract val isImage: Boolean

    open val fixExt :String? = null

    @Throws(IOException::class)
    abstract fun open(): InputStream

    abstract fun deleteTempFile()

    val contentLength by lazy { getStreamSize(true, open()) }

    // okhttpのRequestBodyにする
    fun toRequestBody(onWrote: (percent: Int) -> Unit = {}) =
        object : RequestBody() {
            override fun contentType() = mimeType.toMediaType()

            @Throws(IOException::class)
            override fun contentLength(): Long = contentLength

            @Throws(IOException::class)
            override fun writeTo(sink: BufferedSink) {
                val length = contentLength.toFloat()
                open().use { inStream ->
                    val tmp = ByteArray(4096)
                    var nWrite = 0L
                    while (true) {
                        val delta = inStream.read(tmp, 0, tmp.size)
                        if (delta <= 0) break
                        sink.write(tmp, 0, delta)
                        nWrite += delta
                        val percent = (100f * nWrite.toFloat() / length).toInt()
                        onWrote(percent)
                    }
                }
            }
        }
}

// contentResolver.openInputStream を使うOpener
fun contentUriOpener(
    contentResolver: ContentResolver,
    uri: Uri,
    mimeType: String,
    isImage: Boolean,
) = object : InputStreamOpener() {
    override val mimeType = mimeType
    override val isImage = isImage

    @Throws(IOException::class)
    override fun open(): InputStream {
        return contentResolver.openInputStream(uri)
            ?: error("openInputStream returns null")
    }

    override fun deleteTempFile() = Unit
}

// 一時ファイルを使うOpener
fun tempFileOpener(
    file: File,
    mimeType: String,
    isImage: Boolean,
    fixExt:String? = null,
) = object : InputStreamOpener() {
    override val mimeType = mimeType
    override val isImage = isImage
    override val fixExt = fixExt

    @Throws(IOException::class)
    override fun open() = FileInputStream(file)
    override fun deleteTempFile() {
        file.delete()
    }
}