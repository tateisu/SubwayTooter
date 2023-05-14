package jp.juggler.subwaytooter.actpost

import android.graphics.Bitmap
import android.net.Uri
import android.text.InputType
import android.view.View
import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.ActPost
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.ApiTask
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.entity.InstanceType
import jp.juggler.subwaytooter.api.entity.ServiceType
import jp.juggler.subwaytooter.api.entity.TootAttachment
import jp.juggler.subwaytooter.api.entity.TootAttachment.Companion.tootAttachment
import jp.juggler.subwaytooter.api.entity.TootAttachment.Companion.tootAttachmentJson
import jp.juggler.subwaytooter.api.entity.TootAttachmentType
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.api.entity.parseItem
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.calcIconRound
import jp.juggler.subwaytooter.defaultColorIcon
import jp.juggler.subwaytooter.dialog.actionsDialog
import jp.juggler.subwaytooter.dialog.decodeAttachmentBitmap
import jp.juggler.subwaytooter.dialog.focusPointDialog
import jp.juggler.subwaytooter.dialog.showTextInputDialog
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.util.AttachmentRequest
import jp.juggler.subwaytooter.util.AttachmentUploader
import jp.juggler.subwaytooter.util.PostAttachment
import jp.juggler.subwaytooter.util.resolveMimeType
import jp.juggler.subwaytooter.view.MyNetworkImageView
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.CharacterGroup
import jp.juggler.util.data.GetContentResultEntry
import jp.juggler.util.data.buildJsonObject
import jp.juggler.util.data.decodeJsonArray
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.dialogOrToast
import jp.juggler.util.log.showToast
import jp.juggler.util.log.withCaption
import jp.juggler.util.network.toPutRequestBuilder
import jp.juggler.util.ui.isLiveActivity
import jp.juggler.util.ui.vg
import kotlin.math.min

private val log = LogCategory("ActPostAttachment")

// AppStateに保存する
fun ActPost.saveAttachmentList() {
    if (!isMultiWindowPost) appState.attachmentList = this.attachmentList
}

fun ActPost.decodeAttachments(sv: String) {
    attachmentList.clear()
    try {
        sv.decodeJsonArray().objectList().forEach {
            try {
                attachmentList.add(PostAttachment(tootAttachmentJson(it)))
            } catch (ex: Throwable) {
                log.e(ex, "can't parse TootAttachment.")
            }
        }
    } catch (ex: Throwable) {
        log.e(ex, "decodeAttachments failed.")
    }
}

fun ActPost.showMediaAttachment() {
    if (isFinishing) return
    views.llAttachment.vg(attachmentList.isNotEmpty())
    ivMedia.forEachIndexed { i, v -> showMediaAttachmentOne(v, i) }
}

fun ActPost.showMediaAttachmentProgress() {
    val mergedProgress = attachmentList
        .mapNotNull { it.progress.notEmpty() }
        .joinToString("\n")
    views.tvAttachmentProgress
        .vg(mergedProgress.isNotEmpty())
        ?.text = mergedProgress
}

fun ActPost.showMediaAttachmentOne(iv: MyNetworkImageView, idx: Int) {
    if (idx >= attachmentList.size) {
        iv.visibility = View.GONE
    } else {
        iv.visibility = View.VISIBLE
        val pa = attachmentList[idx]
        val a = pa.attachment
        when {
            a == null || pa.status != PostAttachment.Status.Ok -> {
                iv.setDefaultImage(defaultColorIcon(this, R.drawable.ic_upload))
                iv.setErrorImage(defaultColorIcon(this, R.drawable.ic_clip))
                iv.setImageUrl(calcIconRound(iv.layoutParams.width), null)
            }

            else -> {
                val defaultIconId = when (a.type) {
                    TootAttachmentType.Image -> R.drawable.ic_image
                    TootAttachmentType.Video,
                    TootAttachmentType.GIFV,
                    -> R.drawable.ic_videocam

                    TootAttachmentType.Audio -> R.drawable.ic_music_note
                    else -> R.drawable.ic_clip
                }
                iv.setDefaultImage(defaultColorIcon(this, defaultIconId))
                iv.setErrorImage(defaultColorIcon(this, defaultIconId))
                iv.setImageUrl(calcIconRound(iv.layoutParams.width), a.preview_url)
            }
        }
    }
}

fun ActPost.openAttachment() {
    when {
        attachmentList.size >= 4 -> showToast(false, R.string.attachment_too_many)
        account == null -> showToast(false, R.string.account_select_please)
        else -> attachmentPicker.openPicker()
    }
}

fun ActPost.addAttachment(
    uri: Uri,
    mimeTypeArg: String? = null,
//    onUploadEnd: () -> Unit = {},
) {
    val account = this.account
    if (account == null) {
        dialogOrToast(R.string.account_select_please)
        return
    }
    val instance = TootInstance.getCached(account)
    if (instance == null) {
        dialogOrToast("missing instance imformation.")
        return
    }
    val mimeType = uri.resolveMimeType(mimeTypeArg, this, instance)
        ?.notEmpty()

    val isReply = states.inReplyToId != null

    when {
        attachmentList.size >= 4 -> {
            dialogOrToast(R.string.attachment_too_many)
            return
        }

        mimeType == null -> {
            dialogOrToast(R.string.mime_type_missing)
            return
        }

        instance.instanceType == InstanceType.Pixelfed && isReply -> {
            AttachmentUploader.log.e("pixelfed_does_not_allow_reply_with_media")
            dialogOrToast(R.string.pixelfed_does_not_allow_reply_with_media)
            return
        }

        else -> {
            saveAttachmentList()
            val pa = PostAttachment(this)
            attachmentList.add(pa)
            showMediaAttachment()
            val mediaConfig = instance.configuration?.jsonObject("media_attachments")
            attachmentUploader.addRequest(
                AttachmentRequest(
                    context = applicationContext,
                    account = account,
                    pa = pa,
                    uri = uri,
                    mimeType = mimeType,
                    instance = instance,
                    mediaConfig = mediaConfig,
                    serverMaxSqPixel = mediaConfig?.int("image_matrix_limit")?.takeIf { it > 0 },
                    imageResizeConfig = account.getResizeConfig(),
                    maxBytesVideo = min(
                        account.getMovieMaxBytes(instance),
                        mediaConfig?.int("video_size_limit")
                            ?.takeIf { it > 0 } ?: Int.MAX_VALUE,
                    ),
                    maxBytesImage = min(
                        account.getImageMaxBytes(instance),
                        mediaConfig?.int("image_size_limit")
                            ?.takeIf { it > 0 } ?: Int.MAX_VALUE,
                    ),
                    //      onUploadEnd = onUploadEnd
                )
            )
        }
    }
}

fun ActPost.onPostAttachmentCompleteImpl(pa: PostAttachment) {
    // この添付メディアはリストにない
    if (!attachmentList.contains(pa)) {
        log.w("onPostAttachmentComplete: not in attachment list.")
        return
    }

    when (pa.status) {
        PostAttachment.Status.Error -> {
            log.w("onPostAttachmentComplete: upload failed.")
            attachmentList.remove(pa)
            showMediaAttachment()
        }

        PostAttachment.Status.Progress -> {
            // アップロード中…？
            log.w("onPostAttachmentComplete: ?? status=${pa.status}")
        }

        PostAttachment.Status.Ok -> {
            when (val a = pa.attachment) {
                null -> log.w("onPostAttachmentComplete: upload complete, but missing attachment entity.")
                else -> {
                    // アップロード完了
                    log.i("onPostAttachmentComplete: upload complete.")

                    // 投稿欄の末尾に追記する
                    if (PrefB.bpAppendAttachmentUrlToContent.value) {
                        appendArrachmentUrl(a)
                    }
                }
            }
            showMediaAttachment()
        }
    }
}

/**
 * 添付メディアのURLを編集中テキストに挿入する
 */
private fun ActPost.appendArrachmentUrl(a: TootAttachment) {
    // text_url is not provided on recent mastodon.
    val textUrl = a.text_url ?: a.url
    if (textUrl == null) {
        log.w("missing attachment.textUrl")
        return
    }
    val e = views.etContent.editableText
    if (e == null) {
        // 編注中テキストにアクセスできないなら先頭に空白とURLを置いて、先頭を選択する
        val appendText = " $textUrl"
        views.etContent.setText(appendText)
        views.etContent.setSelection(0, 0)
    } else {
        // 末尾に空白とURLを置く。選択位置は変わらない。
        val selStart = views.etContent.selectionStart
        val selEnd = views.etContent.selectionEnd
        if (e.lastOrNull()?.let { CharacterGroup.isWhitespace(it.code) } != true) {
            e.append(" ")
        }
        e.append(textUrl)
        views.etContent.setSelection(selStart, selEnd)
    }
}

// 添付した画像をタップ
fun ActPost.performAttachmentClick(idx: Int) {
    launchAndShowError {
        val pa = attachmentList.elementAtOrNull(idx)
            ?: error("can't get attachment item[$idx].")

        actionsDialog(getString(R.string.media_attachment)) {
            action(getString(R.string.set_description)) {
                editAttachmentDescription(pa)
            }
            if (pa.attachment?.canFocus == true) {
                action(getString(R.string.set_focus_point)) {
                    openFocusPoint(pa)
                }
            }
            if (account?.isMastodon == true) {
                if (pa.attachment?.isEdit == true) {
                    // https://github.com/tateisu/SubwayTooter/issues/237
                    // 既存の投稿の編集時にサムネイルを更新できるようにするのが著しく面倒くさい
                    // 一旦未対応とする
                } else when (pa.attachment?.type) {
                    TootAttachmentType.Audio,
                    TootAttachmentType.GIFV,
                    TootAttachmentType.Video,
                    -> action(getString(R.string.custom_thumbnail)) {
                        attachmentPicker.openCustomThumbnail(pa)
                    }

                    else -> Unit
                }
            }
            action(getString(R.string.delete)) {
                deleteAttachment(pa)
            }
        }
    }
}

fun ActPost.deleteAttachment(pa: PostAttachment) {
    AlertDialog.Builder(this)
        .setTitle(R.string.confirm_delete_attachment)
        .setPositiveButton(R.string.ok) { _, _ ->
            try {
                pa.isCancelled = true
                pa.status = PostAttachment.Status.Error
                pa.job.cancel()
                attachmentList.remove(pa)
            } catch (ignored: Throwable) {
            }

            showMediaAttachment()
        }
        .setNegativeButton(R.string.cancel, null)
        .show()
}

suspend fun ActPost.openFocusPoint(pa: PostAttachment) {
    val attachment = pa.attachment ?: return
    focusPointDialog(
        attachment = attachment,
        callback = { x, y -> sendFocusPoint(pa, attachment, x, y) }
    )
}

suspend fun ActPost.sendFocusPoint(
    pa: PostAttachment,
    attachment: TootAttachment,
    x: Float,
    y: Float,
): Boolean {
    val account = this.account ?: error("missing account")
    if (attachment.isEdit) {
        attachment.focusX = x
        attachment.focusY = y
        attachment.updateFocus = formatFocusParameter(x, y)
        showToast(false, R.string.applied_when_post)
        showMediaAttachment()
        return true
    }

    var resultAttachment: TootAttachment? = null
    val result = runApiTask(account, progressStyle = ApiTask.PROGRESS_NONE) { client ->
        try {
            client.request(
                "/api/v1/media/${attachment.id}",
                buildJsonObject {
                    put("focus", formatFocusParameter(x, y))
                }.toPutRequestBuilder()
            )?.also { result ->
                resultAttachment = parseItem(result.jsonObject) {
                    tootAttachment(ServiceType.MASTODON, it)
                }
            }
        } catch (ex: Throwable) {
            TootApiResult(ex.withCaption("set focus point failed."))
        }
    }
    result ?: return true
    return when (val newAttachment = resultAttachment) {
        null -> {
            showToast(true, result.error)
            false
        }

        else -> {
            pa.attachment = newAttachment
            true
        }
    }
}

private fun formatFocusParameter(x: Float, y: Float) = "%.2f,%.2f".format(x, y)

suspend fun ActPost.editAttachmentDescription(
    pa: PostAttachment,
) {
    val account = this.account ?: return

    val a = pa.attachment
    if (a == null) {
        showToast(true, R.string.attachment_description_cant_edit_while_uploading)
        return
    }
    // 既存の投稿を編集中なら真
    val isEdit = a.isEdit
    val attachmentId = a.id
    var bitmap: Bitmap? = null
    try {
        // サムネイルをロード
        val url = a.preview_url
        if (url != null) {
            val result = runApiTask { client ->
                try {
                    val (result, data) = client.getHttpBytes(url)
                    data?.let {
                        bitmap = decodeAttachmentBitmap(it, 1024)
                            ?: return@runApiTask TootApiResult("image decode failed.")
                    }
                    result
                } catch (ex: Throwable) {
                    TootApiResult(ex.withCaption("preview loading failed."))
                }
            }
            result ?: return
            if (!isLiveActivity) return
            result.error?.let {
                showToast(true, result.error ?: "error")
                // not exit
            }
        }
        // ダイアログを表示
        showTextInputDialog(
            title = getString(R.string.attachment_description),
            bitmap = bitmap,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            initialText = a.description,
            onEmptyText = { showToast(true, R.string.description_empty) },
        ) { text ->
            if (isEdit) {
                a.description = text
                a.updateDescription = text
                showToast(false, R.string.applied_when_post)
                showMediaAttachment()
                true
            } else {
                val (result, newAttachment) = attachmentUploader.setAttachmentDescription(
                    account,
                    attachmentId,
                    text
                )
                when {
                    result == null -> true
                    newAttachment == null -> {
                        result.error?.let { showToast(true, it) }
                        false
                    }

                    else -> {
                        pa.attachment = newAttachment
                        showMediaAttachment()
                        true
                    }
                }
            }
        }
    } finally {
        bitmap?.recycle()
    }
}

fun ActPost.onPickCustomThumbnailImpl(pa: PostAttachment, src: GetContentResultEntry) {
    when (val account = this.account) {
        null -> showToast(false, R.string.account_select_please)
        else -> launchMain {
            if (pa.attachment?.isEdit == true) {
                showToast(
                    true,
                    "Sorry, updateing thumbnail is not yet supported in case of editing post."
                )
            } else {
                val result = attachmentUploader.uploadCustomThumbnail(account, src, pa)
                result?.error?.let { showToast(true, it) }
                showMediaAttachment()
            }
        }
    }
}
