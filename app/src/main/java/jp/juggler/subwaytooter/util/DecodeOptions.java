package jp.juggler.subwaytooter.util;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;

import jp.juggler.subwaytooter.api.entity.CustomEmoji;
import jp.juggler.subwaytooter.api.entity.NicoProfileEmoji;
import jp.juggler.subwaytooter.api.entity.TootAttachment;

@SuppressWarnings("WeakerAccess")
public class DecodeOptions {
	
	boolean bShort = false;
	
	public DecodeOptions setShort( boolean b ){
		bShort = b;
		return this;
	}
	
	boolean bDecodeEmoji;
	
	public DecodeOptions setDecodeEmoji( boolean b ){
		bDecodeEmoji = b;
		return this;
	}
	
	@Nullable TootAttachment.List list_attachment;
	
	public DecodeOptions setAttachment( TootAttachment.List list_attachment ){
		this.list_attachment = list_attachment;
		return this;
	}
	
	@Nullable Object link_tag;
	
	public DecodeOptions setLinkTag( Object link_tag ){
		this.link_tag = link_tag;
		return this;
	}
	
	@Nullable CustomEmoji.Map customEmojiMap;
	
	public DecodeOptions setCustomEmojiMap( CustomEmoji.Map customEmojiMap ){
		this.customEmojiMap = customEmojiMap;
		return this;
	}
	
	@Nullable NicoProfileEmoji.Map profile_emojis;
	
	public DecodeOptions setProfileEmojis( NicoProfileEmoji.Map profile_emojis ){
		this.profile_emojis = profile_emojis;
		return this;
	}
	
	public SpannableStringBuilder decodeHTML( @NonNull Context context, @NonNull LinkClickContext lcc, String html ){
		return HTMLDecoder.decodeHTML( context, lcc, html, this );
	}
	
	public Spannable decodeEmoji( @NonNull final Context context, @NonNull final String s ){
		return EmojiDecoder.decodeEmoji( context, s, this );
	}
}