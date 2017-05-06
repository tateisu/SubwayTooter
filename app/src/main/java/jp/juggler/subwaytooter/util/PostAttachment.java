package jp.juggler.subwaytooter.util;

import jp.juggler.subwaytooter.api.entity.TootAttachment;

public class PostAttachment {
	
	public interface Callback{
		void onPostAttachmentComplete(PostAttachment pa);
	}
	
	public static final int ATTACHMENT_UPLOADING = 1;
	public static final int ATTACHMENT_UPLOADED = 2;
	public static final int ATTACHMENT_UPLOAD_FAILED = 3;

	public int status;
	public TootAttachment attachment;
	public Callback callback;

	
	public PostAttachment(Callback callback){
		this.status = ATTACHMENT_UPLOADING;
		this.callback = callback;
	}
	public PostAttachment( TootAttachment a ){
		this.status = ATTACHMENT_UPLOADED;
		this.attachment = a;
	}
}
