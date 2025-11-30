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
import jp.juggler.subwaytooter.api.entity.InstanceType
import jp.juggler.subwaytooter.api.entity.ServiceType
import jp.juggler.subwaytooter.api.entity.TootAttachment
import jp.juggler.subwaytooter.api.entity.TootAttachment.Companion.tootAttachment
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.api.entity.parseItem
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.coroutine.launchIO
import jp.juggler.util.data.UriAndType
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import java.nio.channels.ClosedChannelException
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
    private val channel = Channel<AttachmentRequest>(capacity = Channel.UNLIMITED)

    init {
        launchIO {
            while (true) {
                try {
                    val request = channel.receive()
                    if (request.pa.isCancelled) continue
                    withContext(AppDispatchers.MainImmediate) {
                        val pa = request.pa
                        pa.status = try {
                            withContext(pa.job + AppDispatchers.IO) {
                                request.upload()
                            }
                            request.pa.progress = ""

                            val now = System.currentTimeMillis()
                            if (now - lastAttachmentComplete >= 5000L) {
                                safeContext.showToast(false, R.string.attachment_uploaded)
                            }
                            lastAttachmentComplete = now

                            PostAttachment.Status.Ok
                        } catch (ex: Throwable) {
                            if (ex is CancellationException) {
                                // キャンセルはメッセージを出さない
                            } else if (ex.message?.contains("cancel", ignoreCase = true) == true) {
                                // キャンセルはメッセージを出さない
                            } else if (ex is IllegalStateException) {
                                safeContext.showToast(true, "${ex.message}")
                            } else {
                                safeContext.showToast(true, ex.withCaption("upload failed."))
                            }
                            PostAttachment.Status.Error
                        }
                        // 投稿中に画面回転があった場合、新しい画面のコールバックを呼び出す必要がある
                        pa.callback?.onPostAttachmentComplete(pa)
                    }
                } catch (ex: Throwable) {
                    when (ex) {
                        is CancellationException -> {
                            log.i("AttachmentUploader: channel cancelled.")
                            break
                        }

                        is ClosedChannelException, is ClosedReceiveChannelException -> {
                            log.i("AttachmentUploader: channel closed.")
                            break
                        }

                        else -> safeContext.showToast(ex)
                    }
                }
            }
        }
    }

    fun onActivityDestroy() {
        try {
            channel.close()
        } catch (ignored: Throwable) {
        }
    }

    fun addRequest(request: AttachmentRequest) {
        request.pa.progress = safeContext.getString(R.string.attachment_handling_start)

        // マストドンは添付メディアをID順に表示するため
        // 画像が複数ある場合は一つずつ処理する必要がある
        // 投稿画面ごとに作成したチャネルにsendして、受け側は順次処理する
        launchIO {
            try {
                val now = System.currentTimeMillis()

                // アップロード開始トースト(連発しない)
                if (now - lastAttachmentAdd >= 5000L) {
                    safeContext.showToast(false, R.string.attachment_uploading)
                }

                lastAttachmentAdd = now
                channel.send(request)
            } catch (ex: Throwable) {
                log.e(ex, "addRequest failed.")
            }
        }
    }

    @WorkerThread
    private suspend fun AttachmentRequest.upload() {
        val account = this.account
        val instance = this.instance()
        val mediaConfig = this.mediaConfig()

        // ensure mimeType
        this.mimeType

        if (instance.instanceType == InstanceType.Pixelfed && isReply) {
            error(safeContext.getString(R.string.pixelfed_does_not_allow_reply_with_media))
        }

        val client = TootApiClient(safeContext, callback = object : TootApiCallback {
            override suspend fun isApiCancelled() = !coroutineContext.isActive
        })

        client.account = account
        client.currentCallCallback = {}

        // 入力データの変換など
        val opener = this.createOpener()
        try {
            val maxBytes = when (opener.isImage) {
                true -> maxBytesImage(instance, mediaConfig)
                else -> maxBytesVideo(instance, mediaConfig)
            }
            if (opener.contentLength > maxBytes.toLong()) {
                error(safeContext.getString(R.string.file_size_too_big, maxBytes / 1_000_000))
            } else if (!opener.mimeType.mimeTypeIsSupported(instance)) {
                error(safeContext.getString(R.string.mime_type_not_acceptable, opener.mimeType))
            }

            val fileName = fixDocumentName(
                getDocumentName(safeContext.contentResolver, uri),
                fixExt = opener.fixExt,
            )
            pa.progress = safeContext.getString(R.string.attachment_handling_uploading, 0)
            fun writeProgress(percent: Int) {
                pa.progress = if (percent < 100) {
                    safeContext.getString(R.string.attachment_handling_uploading, percent)
                } else {
                    safeContext.getString(R.string.attachment_handling_waiting)
                }
            }

            if (account.isMisskey) {
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

                result ?: throw CancellationException()

                val jsonObject = result.jsonObject
                    ?: error(result.error ?: "missing error detail")
                pa.attachment =
                    parseItem(jsonObject) { tootAttachment(ServiceType.MISSKEY, it) }
                        ?: error("TootAttachment.parse failed")
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
                    if (!instance.versionGE(TootInstance.VERSION_3_1_3)) {
                        return postV1()
                    }

                    // v2 APIを試す
                    val result = postMedia("/api/v2/media")
                        ?: throw CancellationException()
                    val code = result.response?.code // complete,or 4xx error
                    when {
                        // 404ならv1 APIにフォールバック
                        code == 404 -> return postV1()
                        // 202 accepted 以外はポーリングしない
                        code != 202 -> return result
                    }

                    // ポーリングして処理完了を待つ
                    pa.progress = safeContext.getString(R.string.attachment_handling_waiting_async)

                    val id = parseItem(result.jsonObject) {
                        tootAttachment(ServiceType.MASTODON, it)
                    }?.id ?: error("/api/v2/media did not return the media ID.")

                    var lastResponse = SystemClock.elapsedRealtime()
                    while (true) {
                        delay(1000L)

                        val r2 = client.request("/api/v1/media/$id")
                            ?: throw CancellationException()

                        val now = SystemClock.elapsedRealtime()
                        when (r2.response?.code) {
                            // complete,or 4xx error
                            200, in 400 until 500 -> return r2

                            // continue to wait
                            206 -> lastResponse = now

                            // temporary errors, check timeout without 206 response.
                            else -> if (now - lastResponse >= 120000L) error("timeout.")
                        }
                    }
                }

                val result = postV2()
                    ?: throw CancellationException()

                val jsonObject = result.jsonObject
                    ?: error(result.error ?: "missing error detail")

                pa.attachment = parseItem(jsonObject) { tootAttachment(ServiceType.MASTODON, it) }
                    ?: error("TootAttachment.parse failed")
            }
        } finally {
            opener.deleteTempFile()
        }
    }

    private fun fixDocumentName(s: String, fixExt: String?): String {
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
        var escaped = sb.toString()
        if (!fixExt.isNullOrEmpty()) {
            escaped = """\.[^./\\]*\z""".toRegex().replace(escaped, "") + ".${fixExt}"
        }
        return escaped
    }

    ///////////////////////////////////////////////////////////////
    // 添付データのカスタムサムネイル
    suspend fun uploadCustomThumbnail(
        account: SavedAccount,
        src: UriAndType,
        pa: PostAttachment,
    ): TootApiResult? = try {
        safeContext.runApiTask(account) { client ->
            val ar = AttachmentRequest(
                context = safeContext,
                account = account,
                pa = pa,
                uri = src.uri,
                mimeTypeArg = src.mimeType,
                imageResizeConfig = ResizeConfig(ResizeType.SquarePixel, 400),
                maxBytesImage = { _, _ -> 1000000 },
                maxBytesVideo = { _, _ -> 1000000 },
            )
            val instance = ar.instance()
            val mediaConfig = ar.mediaConfig()
            val maxBytesImage = ar.maxBytesImage(instance, mediaConfig)

            val opener = ar.createOpener()
            pa.progress = ""

            try {
                if (opener.contentLength > maxBytesImage.toLong()) {
                    return@runApiTask TootApiResult(
                        getString(
                            R.string.file_size_too_big,
                            maxBytesImage / 1000000
                        )
                    )
                }

                val fileName = fixDocumentName(
                    getDocumentName(safeContext.contentResolver, src.uri),
                    fixExt = opener.fixExt,
                )

                if (account.isMisskey) {
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
            } finally {
                opener.deleteTempFile()
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
