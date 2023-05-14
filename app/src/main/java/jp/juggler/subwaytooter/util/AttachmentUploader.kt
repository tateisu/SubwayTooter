package jp.juggler.subwaytooter.util

import android.os.Handler
import android.os.SystemClock
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootApiCallback
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.auth.AuthBase
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.ServiceType
import jp.juggler.subwaytooter.api.entity.TootAttachment
import jp.juggler.subwaytooter.api.entity.TootAttachment.Companion.tootAttachment
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.api.entity.parseItem
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.coroutine.launchIO
import jp.juggler.util.data.GetContentResultEntry
import jp.juggler.util.data.asciiPattern
import jp.juggler.util.data.buildJsonObject
import jp.juggler.util.data.encodeHex
import jp.juggler.util.data.encodeUTF8
import jp.juggler.util.data.getDocumentName
import jp.juggler.util.data.groupEx
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.log.withCaption
import jp.juggler.util.media.ResizeConfig
import jp.juggler.util.media.ResizeType
import jp.juggler.util.network.toPost
import jp.juggler.util.network.toPostRequestBuilder
import jp.juggler.util.network.toPut
import jp.juggler.util.network.toPutRequestBuilder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import java.util.concurrent.CancellationException
import kotlin.coroutines.coroutineContext

class AttachmentUploader(
    activity: AppCompatActivity,
    private val handler: Handler,
) {
    companion object {
        val log = LogCategory("AttachmentUploader")
    }

    private val safeContext = activity.applicationContext!!
    private var lastAttachmentAdd = 0L
    private var lastAttachmentComplete = 0L
    private var channel: Channel<AttachmentRequest>? = null

    private fun prepareChannel(): Channel<AttachmentRequest> {
        // double check before/after lock
        channel?.let { return it }
        synchronized(this) {
            channel?.let { return it }
            return Channel<AttachmentRequest>(capacity = Channel.UNLIMITED)
                .also {
                    channel = it
                    launchIO {
                        while (true) {
                            val request = try {
                                it.receive()
                            } catch (ex: Throwable) {
                                when (ex) {
                                    is CancellationException, is ClosedReceiveChannelException -> break
                                    else -> {
                                        safeContext.showToast(ex)
                                        continue
                                    }
                                }
                            }
                            val result = try {
                                if (request.pa.isCancelled) continue
                                withContext(request.pa.job + AppDispatchers.IO) {
                                    request.upload()
                                }
                            } catch (ex: Throwable) {
                                TootApiResult(ex.withCaption("upload failed."))
                            }
                            try {
                                request.pa.progress = ""
                                withContext(AppDispatchers.MainImmediate) {
                                    handleResult(request, result)
                                }
                            } catch (ex: Throwable) {
                                when (ex) {
                                    is CancellationException, is ClosedReceiveChannelException -> break
                                    else -> {
                                        safeContext.showToast(ex)
                                        continue
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }

    fun onActivityDestroy() {
        try {
            synchronized(this) {
                channel?.close()
                channel = null
            }
        } catch (ex: Throwable) {
            log.e(ex, "can't close channel.")
        }
    }

    fun addRequest(request: AttachmentRequest) {

        request.pa.progress = safeContext.getString(R.string.attachment_handling_start)

        // アップロード開始トースト(連発しない)
        val now = System.currentTimeMillis()
        if (now - lastAttachmentAdd >= 5000L) {
            safeContext.showToast(false, R.string.attachment_uploading)
        }
        lastAttachmentAdd = now

        // マストドンは添付メディアをID順に表示するため
        // 画像が複数ある場合は一つずつ処理する必要がある
        // 投稿画面ごとに1スレッドだけ作成してバックグラウンド処理を行う
        launchIO { prepareChannel().send(request) }
    }

    @WorkerThread
    private suspend fun AttachmentRequest.upload(): TootApiResult? {
        try {
            if (mimeType.isEmpty()) return TootApiResult("mime_type is empty.")

            val client = TootApiClient(safeContext, callback = object : TootApiCallback {
                override suspend fun isApiCancelled() = !coroutineContext.isActive
            })

            client.account = account
            client.currentCallCallback = {}

            val (ti, tiResult) = TootInstance.get(client)
            ti ?: return tiResult

            // 入力データの変換など
            val opener = this.createOpener()
            val maxBytes = when (opener.isImage) {
                true -> maxBytesImage
                else -> maxBytesVideo
            }
            if (opener.contentLength > maxBytes) {
                return TootApiResult(
                    safeContext.getString(R.string.file_size_too_big, maxBytes / 1_000_000)
                )
            }

            if (!opener.mimeType.mimeTypeIsSupported(instance)) {
                return TootApiResult(
                    safeContext.getString(R.string.mime_type_not_acceptable, opener.mimeType)
                )
            }

            val fileName = fixDocumentName(getDocumentName(safeContext.contentResolver, uri))
            pa.progress = safeContext.getString(R.string.attachment_handling_uploading, 0)
            fun writeProgress(percent: Int) {
                if (percent < 100) {
                    pa.progress =
                        safeContext.getString(R.string.attachment_handling_uploading, percent)
                } else {
                    pa.progress = safeContext.getString(R.string.attachment_handling_waiting)
                }
            }

            return if (account.isMisskey) {
                val multipartBuilder = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)

                val apiKey = account.tokenJson?.string(AuthBase.KEY_API_KEY_MISSKEY)
                if (apiKey?.isNotEmpty() == true) {
                    multipartBuilder.addFormDataPart("i", apiKey)
                }

                multipartBuilder.addFormDataPart(
                    "file",
                    fileName,
                    opener.toRequestBody { writeProgress(it) },
                )

                val result = client.request(
                    "/api/drive/files/create",
                    multipartBuilder.build().toPost()
                )
                opener.deleteTempFile()

                val jsonObject = result?.jsonObject
                if (jsonObject != null) {
                    val a = parseItem(jsonObject) { tootAttachment(ServiceType.MISSKEY, it) }
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
                            opener.toRequestBody { writeProgress(it) },
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
                    pa.progress = safeContext.getString(R.string.attachment_handling_waiting_async)
                    val id = parseItem(result?.jsonObject) {
                        tootAttachment(ServiceType.MASTODON, it)
                    }?.id
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

                            // temporary errors, check timeout without 206 response.
                            else -> if (now - lastResponse >= 120000L) {
                                return TootApiResult("timeout.")
                            }
                        }
                    }
                }

                val result = postV2()
                opener.deleteTempFile()

                val jsonObject = result?.jsonObject
                if (jsonObject != null) {
                    when (val a = parseItem(jsonObject) {
                        tootAttachment(ServiceType.MASTODON, it)
                    }) {
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

    private fun fixDocumentName(s: String): String {
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

    private fun handleResult(request: AttachmentRequest, result: TootApiResult?) {
        val pa = request.pa
        pa.status = when (pa.attachment) {
            null -> {
                if (result != null) {
                    when {
                        // キャンセルはトーストを出さない
                        result.error?.contains("cancel", ignoreCase = true) == true -> Unit
                        else -> safeContext.showToast(
                            true,
                            "${result.error} ${result.response?.request?.method} ${result.response?.request?.url}"
                        )
                    }
                }
                PostAttachment.Status.Error
            }

            else -> {
                val now = System.currentTimeMillis()
                if (now - lastAttachmentComplete >= 5000L) {
                    safeContext.showToast(false, R.string.attachment_uploaded)
                }
                lastAttachmentComplete = now

                PostAttachment.Status.Ok
            }
        }

        // 投稿中に画面回転があった場合、新しい画面のコールバックを呼び出す必要がある
        pa.callback?.onPostAttachmentComplete(pa)
    }

    ///////////////////////////////////////////////////////////////
    // 添付データのカスタムサムネイル
    suspend fun uploadCustomThumbnail(
        account: SavedAccount,
        src: GetContentResultEntry,
        pa: PostAttachment,
    ): TootApiResult? = try {
        safeContext.runApiTask(account) { client ->
            val (ti, ri) = TootInstance.get(client)
            ti ?: return@runApiTask ri

            val mimeType = src.uri.resolveMimeType(src.mimeType, safeContext)
            if (mimeType.isNullOrEmpty()) {
                return@runApiTask TootApiResult(safeContext.getString(R.string.mime_type_missing))
            }

            val mediaConfig = ti.configuration?.jsonObject("media_attachments")
            val ar = AttachmentRequest(
                context = safeContext,
                account = account,
                pa = pa,
                uri = src.uri,
                mimeType = mimeType,
                instance = ti,
                mediaConfig = mediaConfig,
                imageResizeConfig = ResizeConfig(ResizeType.SquarePixel, 400),
                serverMaxSqPixel = mediaConfig?.int("image_matrix_limit")?.takeIf { it > 0 },
                maxBytesImage = 1000000,
                maxBytesVideo = 1000000,
            )
            val opener = ar.createOpener()
            if (opener.contentLength > ar.maxBytesImage) {
                return@runApiTask TootApiResult(
                    getString(
                        R.string.file_size_too_big,
                        ar.maxBytesImage / 1000000
                    )
                )
            }

            val fileName = fixDocumentName(getDocumentName(safeContext.contentResolver, src.uri))

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
                            opener.toRequestBody(),
                        )
                        .build().toPut()
                )
                opener.deleteTempFile()

                val jsonObject = result?.jsonObject
                if (jsonObject != null) {
                    val a = parseItem(jsonObject) { tootAttachment(ServiceType.MASTODON, it) }
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
            safeContext.runApiTask(account) { client ->
                if (account.isMisskey) {
                    client.request(
                        "/api/drive/files/update",
                        account.putMisskeyApiToken().apply {
                            put("fileId", attachmentId.toString())
                            put("comment", description)
                        }.toPostRequestBuilder()
                    )?.also { result ->
                        resultAttachment = parseItem(result.jsonObject) {
                            tootAttachment(ServiceType.MISSKEY, it)
                        }
                    }
                } else {
                    client.request(
                        "/api/v1/media/$attachmentId",
                        buildJsonObject {
                            put("description", description)
                        }.toPutRequestBuilder()
                    )?.also { result ->
                        resultAttachment = parseItem(result.jsonObject) {
                            tootAttachment(ServiceType.MASTODON, it)
                        }
                    }
                }
            }
        } catch (ex: Throwable) {
            log.e(ex, "setAttachmentDescription failed.")
            TootApiResult(ex.withCaption("setAttachmentDescription failed."))
        }
        return Pair(result, resultAttachment)
    }
}
