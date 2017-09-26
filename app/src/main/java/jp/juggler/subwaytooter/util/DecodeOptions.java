package jp.juggler.subwaytooter.util;

import android.content.Context;
import android.support.annotation.Nullable;
import android.text.SpannableStringBuilder;

import jp.juggler.subwaytooter.api.entity.CustomEmoji;
import jp.juggler.subwaytooter.api.entity.TootAttachment;

@SuppressWarnings("WeakerAccess")
public class DecodeOptions {
	
	boolean bShort = false;
	public DecodeOptions setShort(boolean b){
		bShort = b;
		return this;
	}

	boolean bDecodeEmoji;
	public DecodeOptions setDecodeEmoji(boolean b){
		bDecodeEmoji = b;
		return this;
	}
	
	@Nullable TootAttachment.List list_attachment;
	public DecodeOptions setAttachment(TootAttachment.List list_attachment){
		this.list_attachment = list_attachment;
		return this;
	}
	
	
	@Nullable Object link_tag;
	public DecodeOptions setLinkTag(Object link_tag){
		this.link_tag = link_tag;
		return this;
	}
	
	@Nullable CustomEmoji.Map customEmojiMap;
	public DecodeOptions setEmojiMap(CustomEmoji.Map customEmojiMap){
		this.customEmojiMap = customEmojiMap;
		return this;
	}
	
	public SpannableStringBuilder decodeHTML( Context context, LinkClickContext lcc, String html ){
		return HTMLDecoder.decodeHTML( context,lcc,html ,this);
	}
}