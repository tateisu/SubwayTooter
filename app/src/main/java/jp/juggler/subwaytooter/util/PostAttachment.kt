package jp.juggler.subwaytooter.util

import jp.juggler.subwaytooter.api.entity.TootAttachment

class PostAttachment {
	
	companion object {
		const val STATUS_UPLOADING = 1
		const val STATUS_UPLOADED = 2
		const val STATUS_UPLOAD_FAILED = 3
	}

	interface Callback {
		fun onPostAttachmentComplete(pa :PostAttachment)
	}
	
	var status : Int
	var attachment : TootAttachment? = null
	var callback : Callback? = null
	
	
	
	constructor(callback : Callback) {
		this.status = STATUS_UPLOADING
		this.callback = callback
	}
	
	constructor(a : TootAttachment) {
		this.status = STATUS_UPLOADED
		this.attachment = a
	}
	
}
