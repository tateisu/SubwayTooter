package jp.juggler.subwaytooter.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.SystemClock
import androidx.annotation.WorkerThread
import jp.juggler.subwaytooter.ActPost
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootApiCallback
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.*
import kotlinx.coroutines.delay
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.*
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class AttachmentRequest(
    val account: SavedAccount,
    val pa: PostAttachment,
    val uri: Uri,
    val mimeType: String,
    val isReply: Boolean,
    val onUploadEnd: () -> Unit,
)

class AttachmentUploader(
    contextArg: Context,
    private val handler: Handler,
) {
    companion object {
        val log = LogCategory("AttachmentUploader")

        internal const val MIME_TYPE_JPEG = "image/jpeg"
        internal const val MIME_TYPE_PNG = "image/png"

        val acceptableMimeTypes = HashSet<String>().apply {
            //
            add("image/*") // Android標準のギャラリーが image/* を出してくることがあるらしい
            add("video/*") // Android標準のギャラリーが image/* を出してくることがあるらしい
            add("audio/*") // Android標準のギャラリーが image/* を出してくることがあるらしい
            //
            add("image/jpeg")
            add("image/png")
            add("image/gif")
            add("video/webm")
            add("video/mp4")
            add("video/quicktime")
            //
            add("audio/webm")
            add("audio/ogg")
            add("audio/mpeg")
            add("audio/mp3")
            add("audio/wav")
            add("audio/wave")
            add("audio/x-wav")
            add("audio/x-pn-wav")
            add("audio/flac")
            add("audio/x-flac")

            // https://github.com/tootsuite/mastodon/pull/11342
            add("audio/aac")
            add("audio/m4a")
            add("audio/3gpp")
        }

        val acceptableMimeTypesPixelfed = HashSet<String>().apply {
            //
            add("image/*") // Android標準のギャラリーが image/* を出してくることがあるらしい
            add("video/*") // Android標準のギャラリーが image/* を出してくることがあるらしい
            //
            add("image/jpeg")
            add("image/png")
            add("image/gif")
            add("video/mp4")
            add("video/m4v")
        }

        private val imageHeaderList = arrayOf(
            Pair(
                "image/jpeg",
                intArrayOf(0xff, 0xd8, 0xff).toByteArray()
            ),
            Pair(
                "image/png",
                intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A).toByteArray()
            ),
            Pair(
                "image/gif",
                charArrayOf('G', 'I', 'F').toLowerByteArray()
            ),
            Pair(
                "audio/wav",
                charArrayOf('R', 'I', 'F', 'F').toLowerByteArray()
            ),
            Pair(
                "audio/ogg",
                charArrayOf('O', 'g', 'g', 'S').toLowerByteArray()
            ),
            Pair(
                "audio/flac",
                charArrayOf('f', 'L', 'a', 'C').toLowerByteArray()
            )
        )

        private val sig3gp = arrayOf(
            "3ge6",
            "3ge7",
            "3gg6",
            "3gp1",
            "3gp2",
            "3gp3",
            "3gp4",
            "3gp5",
            "3gp6",
            "3gp7",
            "3gr6",
            "3gr7",
            "3gs6",
            "3gs7",
            "kddi"
        ).map { it.toCharArray().toLowerByteArray() }

        private val sigM4a = arrayOf(
            "M4A ",
            "M4B ",
            "M4P "
        ).map { it.toCharArray().toLowerByteArray() }

        private val sigFtyp = "ftyp".toCharArray().toLowerByteArray()

        private fun matchSig(
            data: ByteArray,
            dataOffset: Int,
            sig: ByteArray,
            sigSize: Int = sig.size,
        ): Boolean {
            for (i in 0 until sigSize) {
                if (data[dataOffset + i] != sig[i]) return false
            }
            return true
        }
    }

    private val context: Context = contextArg.applicationContext
    private val attachmentQueue = ConcurrentLinkedQueue<AttachmentRequest>()
    private var attachmentWorker: AttachmentWorker? = null
    private var lastAttachmentAdd = 0L
    private var lastAttachmentComplete = 0L

    fun onActivityDestroy() {
        attachmentWorker?.cancel()
    }

    fun addRequest(
        request: AttachmentRequest,
    ) {
        // アップロード開始トースト(連発しない)
        val now = System.currentTimeMillis()
        if (now - lastAttachmentAdd >= 5000L) {
            context.showToast(false, R.string.attachment_uploading)
        }
        lastAttachmentAdd = now

        // マストドンは添付メディアをID順に表示するため
        // 画像が複数ある場合は一つずつ処理する必要がある
        // 投稿画面ごとに1スレッドだけ作成してバックグラウンド処理を行う
        attachmentQueue.add(request)
        val oldWorker = attachmentWorker
        if (oldWorker == null || !oldWorker.isAlive || oldWorker.isCancelled.get()) {
            oldWorker?.cancel()
            attachmentWorker = AttachmentWorker()
        } else {
            oldWorker.notifyEx()
        }
    }

    fun handleResult(request: AttachmentRequest, result: TootApiResult?) {
        val pa = request.pa
        pa.status = when (pa.attachment) {
            null -> {
                if (result != null) {
                    context.showToast(
                        true,
                        "${result.error} ${result.response?.request?.method} ${result.response?.request?.url}"
                    )
                }
                PostAttachment.Status.Error
            }
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastAttachmentComplete >= 5000L) {
                    context.showToast(false, R.string.attachment_uploaded)
                }
                lastAttachmentComplete = now

                PostAttachment.Status.Ok
            }
        }

        // 投稿中に画面回転があった場合、新しい画面のコールバックを呼び出す必要がある
        pa.callback?.onPostAttachmentComplete(pa)
    }

    inner class AttachmentWorker : WorkerBase() {

        internal val isCancelled = AtomicBoolean(false)

        override fun cancel() {
            isCancelled.set(true)
            notifyEx()
        }

        override suspend fun run() {
            try {
                while (!isCancelled.get()) {
                    val request = attachmentQueue.poll()
                    if (request == null) {
                        waitEx(86400000L)
                        continue
                    }
                    val result = request.upload()
                    handler.post { handleResult(request, result) }
                }
            } catch (ex: Throwable) {
                log.trace(ex)
                log.e(ex, "AttachmentWorker")
            }
        }

        @WorkerThread
        private suspend fun AttachmentRequest.upload(): TootApiResult? {
            if (mimeType.isEmpty()) return TootApiResult("mime_type is empty.")

            try {

                val client = TootApiClient(context, callback = object : TootApiCallback {
                    override val isApiCancelled: Boolean
                        get() = isCancelled.get()
                })

                client.account = account

                val (ti, tiResult) = TootInstance.get(client)
                ti ?: return tiResult

                if (ti.instanceType == InstanceType.Pixelfed) {
                    if (isReply) {
                        return TootApiResult(context.getString(R.string.pixelfed_does_not_allow_reply_with_media))
                    }
                    if (!acceptableMimeTypesPixelfed.contains(mimeType)) {
                        return TootApiResult(context.getString(R.string.mime_type_not_acceptable, mimeType))
                    }
                }
                // 設定からリサイズ指定を読む
                val resizeConfig = account.getResizeConfig()

                val opener = createOpener(uri, mimeType, resizeConfig)

                val mediaSizeMax = when {
                    mimeType.startsWith("video") || mimeType.startsWith("audio") ->
                        account.getMovieMaxBytes(ti)

                    else ->
                        account.getImageMaxBytes(ti)
                }

                val contentLength = getStreamSize(true, opener.open())
                if (contentLength > mediaSizeMax) {
                    return TootApiResult(
                        context.getString(R.string.file_size_too_big, mediaSizeMax / 1000000)
                    )
                }

                fun fixDocumentName(s: String): String {
                    val sLength = s.length
                    val m = """([^\x20-\x7f])""".asciiPattern().matcher(s)
                    m.reset()
                    val sb = StringBuilder(sLength)
                    var lastEnd = 0
                    while (m.find()) {
                        sb.append(s.substring(lastEnd, m.start()))
                        val escaped = m.groupEx(1)!!.encodeUTF8().encodeHex()
                        sb.append(escaped)
                        lastEnd = m.end()
                    }
                    if (lastEnd < sLength) sb.append(s.substring(lastEnd, sLength))
                    return sb.toString()
                }

                val fileName = fixDocumentName(getDocumentName(context.contentResolver, uri))

                return if (account.isMisskey) {
                    val multipartBuilder = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)

                    val apiKey = account.token_info?.string(TootApiClient.KEY_API_KEY_MISSKEY)
                    if (apiKey?.isNotEmpty() == true) {
                        multipartBuilder.addFormDataPart("i", apiKey)
                    }

                    multipartBuilder.addFormDataPart(
                        "file",
                        fileName,
                        object : RequestBody() {
                            override fun contentType(): MediaType {
                                return opener.mimeType.toMediaType()
                            }

                            @Throws(IOException::class)
                            override fun contentLength(): Long {
                                return contentLength
                            }

                            @Throws(IOException::class)
                            override fun writeTo(sink: BufferedSink) {
                                opener.open().use { inData ->
                                    val tmp = ByteArray(4096)
                                    while (true) {
                                        val r = inData.read(tmp, 0, tmp.size)
                                        if (r <= 0) break
                                        sink.write(tmp, 0, r)
                                    }
                                }
                            }
                        }
                    )

                    val result = client.request(
                        "/api/drive/files/create",
                        multipartBuilder.build().toPost()
                    )

                    opener.deleteTempFile()
                    onUploadEnd()

                    val jsonObject = result?.jsonObject
                    if (jsonObject != null) {
                        val a = parseItem(::TootAttachment, ServiceType.MISSKEY, jsonObject)
                        if (a == null) {
                            result.error = "TootAttachment.parse failed"
                        } else {
                            pa.attachment = a
                        }
                    }
                    result
                } else {
                    suspend fun postMedia(path: String) = client.request(
                        path,
                        MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart(
                                "file",
                                fileName,
                                object : RequestBody() {
                                    override fun contentType(): MediaType {
                                        return opener.mimeType.toMediaType()
                                    }

                                    @Throws(IOException::class)
                                    override fun contentLength(): Long {
                                        return contentLength
                                    }

                                    @Throws(IOException::class)
                                    override fun writeTo(sink: BufferedSink) {
                                        opener.open().use { inData ->
                                            val tmp = ByteArray(4096)
                                            while (true) {
                                                val r = inData.read(tmp, 0, tmp.size)
                                                if (r <= 0) break
                                                sink.write(tmp, 0, r)
                                            }
                                        }
                                    }
                                }
                            )
                            .build().toPost()
                    )

                    suspend fun postV1() = postMedia("/api/v1/media")

                    suspend fun postV2(): TootApiResult? {
                        // 3.1.3未満は v1 APIを使う
                        if (!ti.versionGE(TootInstance.VERSION_3_1_3)) {
                            return postV1()
                        }

                        // v2 APIを試す
                        val result = postMedia("/api/v2/media")
                        val code = result?.response?.code // complete,or 4xx error
                        when {
                            // 404ならv1 APIにフォールバック
                            code == 404 -> return postV1()
                            // 202 accepted 以外はポーリングしない
                            code != 202 -> return result
                        }

                        // ポーリングして処理完了を待つ
                        val id = parseItem(::TootAttachment, ServiceType.MASTODON, result?.jsonObject)
                            ?.id
                            ?: return TootApiResult("/api/v2/media did not return the media ID.")

                        var lastResponse = SystemClock.elapsedRealtime()
                        loop@ while (true) {

                            delay(1000L)
                            val r2 = client.request("/api/v1/media/$id")
                                ?: return null // cancelled

                            val now = SystemClock.elapsedRealtime()
                            when (r2.response?.code) {
                                // complete,or 4xx error
                                200, in 400 until 500 -> return r2

                                // continue to wait
                                206 -> lastResponse = now

                                // too many temporary error without 206 response.
                                else -> if (now - lastResponse >= 120000L) {
                                    return TootApiResult("timeout.")
                                }
                            }
                        }
                    }

                    val result = postV2()
                    opener.deleteTempFile()
                    onUploadEnd()

                    val jsonObject = result?.jsonObject
                    if (jsonObject != null) {
                        when (val a = parseItem(::TootAttachment, ServiceType.MASTODON, jsonObject)) {
                            null -> result.error = "TootAttachment.parse failed"
                            else -> pa.attachment = a
                        }
                    }
                    result
                }
            } catch (ex: Throwable) {
                return TootApiResult(ex.withCaption("read failed."))
            }
        }
    }

    internal interface InputStreamOpener {

        val mimeType: String

        @Throws(IOException::class)
        fun open(): InputStream

        fun deleteTempFile()
    }

    private fun createOpener(
        uri: Uri,
        mimeType: String,
        resizeConfig: ResizeConfig,
    ): InputStreamOpener {

        while (true) {
            try {
                // 画像の種別
                val isJpeg = MIME_TYPE_JPEG == mimeType
                val isPng = MIME_TYPE_PNG == mimeType
                if (!isJpeg && !isPng) {
                    ActPost.log.d("createOpener: source is not jpeg or png")
                    break
                }

                val bitmap = createResizedBitmap(
                    context,
                    uri,
                    resizeConfig,
                    skipIfNoNeedToResizeAndRotate = true
                )
                if (bitmap != null) {
                    try {
                        val cacheDir = context.externalCacheDir
                        if (cacheDir == null) {
                            context.showToast(false, "getExternalCacheDir returns null.")
                            break
                        }

                        cacheDir.mkdir()

                        val tempFile = File(cacheDir, "tmp." + Thread.currentThread().id)
                        FileOutputStream(tempFile).use { os ->
                            if (isJpeg) {
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os)
                            } else {
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                            }
                        }

                        return object : InputStreamOpener {

                            override val mimeType: String
                                get() = mimeType

                            @Throws(IOException::class)
                            override fun open(): InputStream {
                                return FileInputStream(tempFile)
                            }

                            override fun deleteTempFile() {
                                tempFile.delete()
                            }
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
            } catch (ex: Throwable) {
                ActPost.log.trace(ex)
                context.showToast(ex, "Resizing image failed.")
            }

            break
        }

        return object : InputStreamOpener {

            override val mimeType: String
                get() = mimeType

            @Throws(IOException::class)
            override fun open(): InputStream {
                return context.contentResolver.openInputStream(uri) ?: error("openInputStream returns null")
            }

            override fun deleteTempFile() {
            }
        }
    }

    fun getMimeType(uri: Uri, mimeTypeArg: String?): String? {
        // image/j()pg だの image/j(e)pg だの、mime type を誤記するアプリがあまりに多い
        // クレームで消耗するのを減らすためにファイルヘッダを確認する
        if (mimeTypeArg == null || mimeTypeArg.startsWith("image/")) {
            val sv = findMimeTypeByFileHeader(context.contentResolver, uri)
            if (sv != null) return sv
        }

        // 既に引数で与えられてる
        if (mimeTypeArg?.isNotEmpty() == true) {
            return mimeTypeArg
        }

        // ContentResolverに尋ねる
        var sv = context.contentResolver.getType(uri)
        if (sv?.isNotEmpty() == true) return sv

        // gboardのステッカーではUriのクエリパラメータにmimeType引数がある
        sv = uri.getQueryParameter("mimeType")
        if (sv?.isNotEmpty() == true) return sv

        return null
    }

    private fun findMimeTypeByFileHeader(
        contentResolver: ContentResolver,
        uri: Uri,
    ): String? {
        try {
            contentResolver.openInputStream(uri)?.use { inStream ->
                val data = ByteArray(65536)
                val nRead = inStream.read(data, 0, data.size)
                for (pair in imageHeaderList) {
                    val type = pair.first
                    val header = pair.second
                    if (nRead >= header.size && data.startWith(header)) return type
                }

                // scan frame header
                for (i in 0 until nRead - 8) {

                    if (!matchSig(data, i, sigFtyp)) continue

                    // 3gpp check
                    for (s in sig3gp) {
                        if (matchSig(data, i + 4, s)) return "audio/3gpp"
                    }

                    // m4a check
                    for (s in sigM4a) {
                        if (matchSig(data, i + 4, s)) return "audio/m4a"
                    }
                }

                // scan frame header
                loop@ for (i in 0 until nRead - 2) {

                    // mpeg frame header
                    val b0 = data[i].toInt() and 255
                    if (b0 != 255) continue
                    val b1 = data[i + 1].toInt() and 255
                    if ((b1 and 0b11100000) != 0b11100000) continue

                    val mpegVersionId = ((b1 shr 3) and 3)
                    // 00 mpeg 2.5
                    // 01 not used
                    // 10 (mp3) mpeg 2 / (AAC) mpeg-4
                    // 11 (mp3) mpeg 1 / (AAC) mpeg-2

                    @Suppress("MoveVariableDeclarationIntoWhen")
                    val mpegLayerId = ((b1 shr 1) and 3)
                    // 00 (mp3)not used / (AAC) always 0
                    // 01 (mp3)layer III
                    // 10 (mp3)layer II
                    // 11 (mp3)layer I

                    when (mpegLayerId) {
                        0 -> when (mpegVersionId) {
                            2, 3 -> return "audio/aac"

                            else -> {
                            }
                        }
                        1 -> when (mpegVersionId) {
                            0, 2, 3 -> return "audio/mp3"

                            else -> {
                            }
                        }
                    }
                }
            }
        } catch (ex: Throwable) {
            log.e(ex, "findMimeTypeByFileHeader failed.")
        }
        return null
    }

    ///////////////////////////////////////////////////////////////

    suspend fun uploadCustomThumbnail(
        account: SavedAccount,
        src: GetContentResultEntry,
        pa: PostAttachment,
    ): TootApiResult? = try {
        context.runApiTask(account) { client ->
            val mimeType = getMimeType(src.uri, src.mimeType)
            if (mimeType?.isEmpty() != false) {
                return@runApiTask TootApiResult(context.getString(R.string.mime_type_missing))
            }

            val (ti, ri) = TootInstance.get(client)
            ti ?: return@runApiTask ri

            val resizeConfig = ResizeConfig(ResizeType.SquarePixel, 400)

            val opener = createOpener(src.uri, mimeType, resizeConfig)

            val mediaSizeMax = 1000000

            val contentLength = getStreamSize(true, opener.open())
            if (contentLength > mediaSizeMax) {
                return@runApiTask TootApiResult(
                    getString(R.string.file_size_too_big, mediaSizeMax / 1000000)
                )
            }

            fun fixDocumentName(s: String): String {
                val sLength = s.length
                val m = """([^\x20-\x7f])""".asciiPattern().matcher(s)
                m.reset()
                val sb = StringBuilder(sLength)
                var lastEnd = 0
                while (m.find()) {
                    sb.append(s.substring(lastEnd, m.start()))
                    val escaped = m.groupEx(1)!!.encodeUTF8().encodeHex()
                    sb.append(escaped)
                    lastEnd = m.end()
                }
                if (lastEnd < sLength) sb.append(s.substring(lastEnd, sLength))
                return sb.toString()
            }

            val fileName = fixDocumentName(getDocumentName(context.contentResolver, src.uri))

            if (account.isMisskey) {
                opener.deleteTempFile()
                TootApiResult("custom thumbnail is not supported on misskey account.")
            } else {
                val result = client.request(
                    "/api/v1/media/${pa.attachment?.id}",
                    MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart(
                            "thumbnail",
                            fileName,
                            object : RequestBody() {
                                override fun contentType(): MediaType {
                                    return opener.mimeType.toMediaType()
                                }

                                @Throws(IOException::class)
                                override fun contentLength(): Long {
                                    return contentLength
                                }

                                @Throws(IOException::class)
                                override fun writeTo(sink: BufferedSink) {
                                    opener.open().use { inData ->
                                        val tmp = ByteArray(4096)
                                        while (true) {
                                            val r = inData.read(tmp, 0, tmp.size)
                                            if (r <= 0) break
                                            sink.write(tmp, 0, r)
                                        }
                                    }
                                }
                            }
                        )
                        .build().toPut()
                )
                opener.deleteTempFile()

                val jsonObject = result?.jsonObject
                if (jsonObject != null) {
                    val a = parseItem(::TootAttachment, ServiceType.MASTODON, jsonObject)
                    if (a == null) {
                        result.error = "TootAttachment.parse failed"
                    } else {
                        pa.attachment = a
                    }
                }
                result
            }
        }
    } catch (ex: Throwable) {
        TootApiResult(ex.withCaption("uploadCustomThumbnail failed."))
    }

    suspend fun setAttachmentDescription(
        account: SavedAccount,
        attachmentId: EntityId,
        description: String,
    ): Pair<TootApiResult?, TootAttachment?> {
        var resultAttachment: TootAttachment? = null
        val result = try {
            context.runApiTask(account) { client ->
                client.request(
                    "/api/v1/media/$attachmentId",
                    jsonObject {
                        put("description", description)
                    }
                        .toPutRequestBuilder()
                )?.also { result ->
                    resultAttachment = parseItem(::TootAttachment, ServiceType.MASTODON, result.jsonObject)
                }
            }
        } catch (ex: Throwable) {
            log.trace(ex, "setAttachmentDescription failed.")
            TootApiResult(ex.withCaption("setAttachmentDescription failed."))
        }
        return Pair(result, resultAttachment)
    }

    fun isAcceptableMimeType(instance: TootInstance?, mimeType: String, isReply: Boolean): Boolean {
        if (instance?.instanceType == InstanceType.Pixelfed) {
            if (isReply) {
                context.showToast(true, R.string.pixelfed_does_not_allow_reply_with_media)
                return false
            }
            if (!acceptableMimeTypesPixelfed.contains(mimeType)) {
                context.showToast(true, R.string.mime_type_not_acceptable, mimeType)
                return false
            }
        } else {
            if (!acceptableMimeTypes.contains(mimeType)) {
                context.showToast(true, R.string.mime_type_not_acceptable, mimeType)
                return false
            }
        }

        return true
    }
}
