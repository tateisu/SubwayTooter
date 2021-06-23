package jp.juggler.subwaytooter

import android.os.Bundle
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.util.AttachmentPicker
import jp.juggler.subwaytooter.util.PostAttachment
import jp.juggler.util.LogCategory
import jp.juggler.util.decodeJsonObject
import jp.juggler.util.toJsonArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

private val log = LogCategory("ActPostStates")

@Serializable
data class ActPostStates(
    ////////////
    // states requires special handling
    var accountDbId: Long? = null,
    var pickerState: String? = null,
    var attachmentListEncoded: String? = null,
    var scheduledStatusEncoded: String? = null,
    ////////////

    var visibility: TootVisibility? = null,

    @Serializable(with = EntityIdSerializer::class)
    var redraftStatusId: EntityId? = null,

    var mushroomInput: Int = 0,
    var mushroomStart: Int = 0,
    var mushroomEnd: Int = 0,

    var timeSchedule: Long = 0L,

    @Serializable(with = EntityIdSerializer::class)
    var inReplyToId: EntityId? = null,
    var inReplyToText: String? = null,
    var inReplyToImage: String? = null,
    var inReplyToUrl: String? = null,
)

// 画面状態の保存
fun ActPost.saveState(outState: Bundle) {
    states.accountDbId = account?.db_id
    states.pickerState = attachmentPicker.encodeState()

    states.scheduledStatusEncoded = scheduledStatus?.encodeSimple()?.toString()

    // アップロード完了したものだけ保持する
    states.attachmentListEncoded = attachmentList
        .filter { it.status == PostAttachment.Status.Ok }
        .mapNotNull { it.attachment?.encodeJson() }
        .toJsonArray()
        .toString()

    val encoded = kJson.encodeToString(states)
    log.d("onSaveInstanceState: $encoded")
    outState.putString(ActPost.STATE_ALL, encoded)

    // test decoding
    kJson.decodeFromString<AttachmentPicker.States>(encoded)
}

// 画面状態の復元
fun ActPost.restoreState(savedInstanceState: Bundle) {

    resetText() // also load account list

    savedInstanceState.getString(ActPost.STATE_ALL)?.let { jsonText ->
        states = kJson.decodeFromString(jsonText)
        states.pickerState?.let { attachmentPicker.restoreState(it) }
        this.account = null // いちど選択を外してから再選択させる
        accountList.find { it.db_id == states.accountDbId }?.let { selectAccount(it) }

        account?.let { a ->
            states.scheduledStatusEncoded?.let { jsonText ->
                scheduledStatus = parseItem(
                    ::TootScheduled,
                    TootParser(this, a),
                    jsonText.decodeJsonObject(),
                    log
                )
            }
        }
        val stateAttachmentList = appState.attachmentList
        if (!isMultiWindowPost && stateAttachmentList != null) {
            // static なデータが残ってるならそれを使う
            this.attachmentList = stateAttachmentList
            // コールバックを新しい画面に差し替える
            for (pa in attachmentList) {
                pa.callback = this
            }
        } else {
            // state から復元する
            states.attachmentListEncoded?.let {
                saveAttachmentList()
                decodeAttachments(it)
            }
        }
    }

    afterUpdateText()
}
