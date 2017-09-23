package jp.juggler.subwaytooter.util;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.api.entity.CustomEmoji;

public abstract class EmojiDecoder {
	private static final Pattern SHORTNAME_PATTERN = Pattern.compile( ":([-+\\w]+):" );
	
	public static final HashMap< String, String > map_name2unicode = EmojiMap._shortNameToUnicode;
	private static final HashSet< String > set_unicode = EmojiMap._unicode_set;
	
	private static class DecodeEnv {
		@NonNull final Context context;
		
		SpannableStringBuilder sb = new SpannableStringBuilder();
		int last_span_start = - 1;
		int last_span_end = - 1;
		
		@Nullable CustomEmoji.Map custom_map;
		
		DecodeEnv( @NonNull Context context, @Nullable CustomEmoji.Map custom_map ){
			this.context = context;
			this.custom_map = custom_map;
		}
		
		void closeSpan(){
			if( last_span_start >= 0 ){
				if( last_span_end > last_span_start && App1.typeface_emoji != null ){
					EmojiSpan typefaceSpan = new EmojiSpan( App1.typeface_emoji );
					sb.setSpan( typefaceSpan, last_span_start, last_span_end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE );
				}
				last_span_start = - 1;
			}
		}
		
		void addEmoji( String s ){
			if( last_span_start < 0 ){
				last_span_start = sb.length();
			}
			sb.append( s );
			last_span_end = sb.length();
		}
		
		void addUnicodeString( String s ){
			int i = 0;
			int end = s.length();
			while( i < end ){
				int remain = end - i;
				String emoji = null;
				if( App1.USE_OLD_EMOJIONE ){
					for( int j = EmojiMap.max_length ; j > 0 ; -- j ){
						if( j > remain ) continue;
						String check = s.substring( i, i + j );
						if( set_unicode.contains( check ) ){
							emoji = check;
							break;
						}
					}
					if( emoji != null ){
						addEmoji( emoji );
						i += emoji.length();
						continue;
					}
				}else{
					Integer image_id = null;
					for( int j = EmojiMap201709.utf16_max_length ; j > 0 ; -- j ){
						if( j > remain ) continue;
						String check = s.substring( i, i + j );
						image_id = EmojiMap201709.sUTF16ToImageId.get( check );
						if( image_id != null ){
							emoji = check;
							break;
						}
					}
					if( image_id != null ){
						addImageSpan( emoji, image_id );
						i += emoji.length();
						continue;
					}
				}
				closeSpan();
				int length = Character.charCount( s.codePointAt( i ) );
				if( length == 1 ){
					sb.append( s.charAt( i ) );
					++ i;
				}else{
					sb.append( s.substring( i, i + length ) );
					i += length;
				}
			}
		}
		
		void addImageSpan( String text, int res_id ){
			closeSpan();
			int start = sb.length();
			sb.append( text );
			int end = sb.length();
			sb.setSpan( new EmojiImageSpan( context, res_id ), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE );
		}
		
		void addNetworkEmojiSpan( String text, @NonNull String url ){
			closeSpan();
			int start = sb.length();
			sb.append( text );
			int end = sb.length();
			sb.setSpan( new NetworkEmojiSpan( url ), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE );
		}
	}
	
	private static final Pattern reNicoru = Pattern.compile( "\\Anicoru\\d*\\z", Pattern.CASE_INSENSITIVE );
	private static final Pattern reHohoemi = Pattern.compile( "\\Ahohoemi\\d*\\z", Pattern.CASE_INSENSITIVE );
	
	public static Spannable decodeEmoji( Context context, String s, @Nullable CustomEmoji.Map custom_map ){
		
		DecodeEnv decode_env = new DecodeEnv( context, custom_map );
		Matcher matcher = SHORTNAME_PATTERN.matcher( s );
		int last_end = 0;
		while( matcher.find() ){
			int start = matcher.start();
			int end = matcher.end();
			if( start > last_end ){
				decode_env.addUnicodeString( s.substring( last_end, start ) );
			}
			last_end = end;
			//
			if( App1.USE_OLD_EMOJIONE ){
				String unicode = map_name2unicode.get( matcher.group( 1 ) );
				if( unicode != null ){
					decode_env.addEmoji( unicode );
					continue;
				}
			}else{
				String name = matcher.group( 1 ).toLowerCase().replace( '-', '_' );
				Integer image_id = EmojiMap201709.sShortNameToImageId.get( name );
				if( image_id != null ){
					decode_env.addImageSpan( s.substring( start, end ), image_id );
					continue;
				}
			}
			
			String url = ( custom_map == null ? null : custom_map.get( matcher.group( 1 ) ) );
			if( ! TextUtils.isEmpty( url ) ){
				decode_env.addNetworkEmojiSpan( s.substring( start, end ), url );
				
			}else if( reHohoemi.matcher( matcher.group( 1 ) ).find() ){
				decode_env.addImageSpan( s.substring( start, end ), R.drawable.emoji_hohoemi );
			}else if( reNicoru.matcher( matcher.group( 1 ) ).find() ){
				decode_env.addImageSpan( s.substring( start, end ), R.drawable.emoji_nicoru );
			}else{
				decode_env.addUnicodeString( s.substring( start, end ) );
			}
		}
		// copy remain
		int end = s.length();
		if( end > last_end ){
			decode_env.addUnicodeString( s.substring( last_end, end ) );
		}
		// close span
		decode_env.closeSpan();
		
		return decode_env.sb;
	}
	
	public static ArrayList< CharSequence > searchShortCode( Context context, String prefix, int limit ){
		ArrayList< CharSequence > dst = new ArrayList<>();
		if( ! App1.USE_OLD_EMOJIONE ){
			for( String shortCode : EmojiMap201709.sShortNameList ){
				if( dst.size() >= limit ) break;
				if( ! shortCode.contains( prefix )) continue;
				
				SpannableStringBuilder sb = new SpannableStringBuilder();
				sb.append( ' ' );
				int start = 0;
				int end = sb.length();
				sb.setSpan( new EmojiImageSpan( context, EmojiMap201709.sShortNameToImageId.get( shortCode ) ), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE );
				sb.append( ' ' );
				sb.append( ':' );
				sb.append( shortCode );
				sb.append( ':' );
				dst.add( sb );
			}
		}
		return dst;
	}
	
}
