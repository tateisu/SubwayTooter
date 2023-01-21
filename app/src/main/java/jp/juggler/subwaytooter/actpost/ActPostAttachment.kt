package jp.juggler.subwaytooter.actpost

import android.app.Dialog
import android.net.Uri
import android.view.View
import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.ActPost
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.ApiTask
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.calcIconRound
import jp.juggler.subwaytooter.defaultColorIcon
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.dialog.DlgFocusPoint
import jp.juggler.subwaytooter.dialog.DlgTextInput
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.util.AttachmentRequest
import jp.juggler.subwaytooter.util.PostAttachment
import jp.juggler.subwaytooter.view.MyNetworkImageView
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.*
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.log.withCaption
import jp.juggler.util.network.toPutRequestBuilder
import jp.juggler.util.ui.dismissSafe
import jp.juggler.util.ui.vg

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
                attachmentList.add(PostAttachment(TootAttachment.decodeJson(it)))
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

fun ActPost.showMedisAttachmentProgress() {
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
    val mimeType = attachmentUploader.getMimeType(uri, mimeTypeArg)
    val isReply = states.inReplyToId != null
    val instance = account?.let { TootInstance.getCached(it) }

    when {
        attachmentList.size >= 4 -> showToast(false, R.string.attachment_too_many)
        account == null -> showToast(false, R.string.account_select_please)
        mimeType?.isEmpty() != false -> showToast(false, R.string.mime_type_missing)
        !attachmentUploader.isAcceptableMimeType(
            instance,
            mimeType,
            isReply
        ) -> Unit // エラーメッセージ出力済み
        else -> {
            saveAttachmentList()
            val pa = PostAttachment(this)
            attachmentList.add(pa)
            showMediaAttachment()
            attachmentUploader.addRequest(
                AttachmentRequest(
                    account,
                    pa,
                    uri,
                    mimeType,
                    isReply = isReply,
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
                    if (PrefB.bpAppendAttachmentUrlToContent.invoke(pref)) {
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
    val pa = try {
        attachmentList[idx]
    } catch (ex: Throwable) {
        showToast(false, ex.withCaption("can't get attachment item[$idx]."))
        return
    }

    val a = ActionsDialog()
        .addAction(getString(R.string.set_description)) {
            editAttachmentDescription(pa)
        }

    if (pa.attachment?.canFocus == true) {
        a.addAction(getString(R.string.set_focus_point)) {
            openFocusPoint(pa)
        }
    }
    if (account?.isMastodon == true) {
        when (pa.attachment?.type) {
            TootAttachmentType.Audio,
            TootAttachmentType.GIFV,
            TootAttachmentType.Video,
            -> a.addAction(getString(R.string.custom_thumbnail)) {
                attachmentPicker.openCustomThumbnail(pa)
            }

            else -> Unit
        }
    }

    a.addAction(getString(R.string.delete)) {
        deleteAttachment(pa)
    }

    a.show(this, title = getString(R.string.media_attachment))
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

fun ActPost.openFocusPoint(pa: PostAttachment) {
    val attachment = pa.attachment ?: return
    DlgFocusPoint(this, attachment)
        .setCallback { x, y -> sendFocusPoint(pa, attachment, x, y) }
        .show()
}

fun ActPost.sendFocusPoint(pa: PostAttachment, attachment: TootAttachment, x: Float, y: Float) {
    val account = this.account ?: return
    launchMain {
        var resultAttachment: TootAttachment? = null
        runApiTask(account, progressStyle = ApiTask.PROGRESS_NONE) { client ->
            try {
                client.request(
                    "/api/v1/media/${attachment.id}",
                    buildJsonObject {
                        put("focus", "%.2f,%.2f".format(x, y))
                    }.toPutRequestBuilder()
                )?.also { result ->
                    resultAttachment =
                        parseItem(::TootAttachment, ServiceType.MASTODON, result.jsonObject)
                }
            } catch (ex: Throwable) {
                TootApiResult(ex.withCaption("set focus point failed."))
            }
        }?.let { result ->
            when (val newAttachment = resultAttachment) {
                null -> showToast(true, result.error)
                else -> pa.attachment = newAttachment
            }
        }
    }
}

fun ActPost.editAttachmentDescription(pa: PostAttachment) {
    val a = pa.attachment
    if (a == null) {
        showToast(true, R.string.attachment_description_cant_edit_while_uploading)
        return
    }

    DlgTextInput.show(
        this,
        getString(R.string.attachment_description),
        a.description,
        callback = object : DlgTextInput.Callback {
            override fun onEmptyError() {
                showToast(true, R.string.description_empty)
            }

            override fun onOK(dialog: Dialog, text: String) {
                val attachmentId = pa.attachment?.id ?: return
                val account = this@editAttachmentDescription.account ?: return
                launchMain {
                    val (result, newAttachment) = attachmentUploader.setAttachmentDescription(
                        account,
                        attachmentId,
                        text
                    )
                    when (newAttachment) {
                        null -> result?.error?.let { showToast(true, it) }
                        else -> {
                            pa.attachment = newAttachment
                            showMediaAttachment()
                            dialog.dismissSafe()
                        }
                    }
                }
            }
        })
}

fun ActPost.onPickCustomThumbnailImpl(pa: PostAttachment, src: GetContentResultEntry) {
    when (val account = this.account) {
        null -> showToast(false, R.string.account_select_please)
        else -> launchMain {
            val result = attachmentUploader.uploadCustomThumbnail(account, src, pa)
            result?.error?.let { showToast(true, it) }
            showMediaAttachment()
        }
    }
}
