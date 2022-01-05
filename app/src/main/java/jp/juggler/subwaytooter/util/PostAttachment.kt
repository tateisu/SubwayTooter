package jp.juggler.subwaytooter.util

import jp.juggler.subwaytooter.api.entity.TootAttachment
import kotlinx.coroutines.Job

class PostAttachment : Comparable<PostAttachment> {
    interface Callback {
        fun onPostAttachmentComplete(pa: PostAttachment)
        fun onPostAttachmentProgress()
    }

    enum class Status(val id: Int) {
        Progress(1),
        Ok(2),
        Error(3),
    }

    var isCancelled = false
    val job = Job()
    var status: Status
    var attachment: TootAttachment? = null
    var callback: Callback? = null
    var progress =""
        set(value){
            if( field!=value){
                field = value
                callback?.onPostAttachmentProgress()
            }
        }

    constructor(callback: Callback) {
        this.status = Status.Progress
        this.callback = callback
    }

    constructor(a: TootAttachment) {
        this.status = Status.Ok
        this.attachment = a
    }

    override fun compareTo(other: PostAttachment): Int {
        val ta = this.attachment
        val tb = other.attachment
        return if (ta != null) {
            if (tb == null) 1 else ta.id.compareTo(tb.id)
        } else {
            if (tb == null) 0 else -1
        }
    }
}
