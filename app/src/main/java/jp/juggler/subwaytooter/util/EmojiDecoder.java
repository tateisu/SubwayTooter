package jp.juggler.subwaytooter.util;

import android.content.Context;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.api.entity.CustomEmoji;
import jp.juggler.subwaytooter.api.entity.NicoProfileEmoji;

@SuppressWarnings("WeakerAccess")
public class EmojiDecoder {
	
	private static class DecodeEnv {
		@NonNull final Context context;
		@NonNull final SpannableStringBuilder sb = new SpannableStringBuilder();
		
		DecodeEnv( @NonNull Context context ){
			this.context = context;
		}
		
		void addUnicodeString( String s ){
			int i = 0;
			int end = s.length();
			while( i < end ){
				int remain = end - i;
				String emoji = null;
				Integer image_id = null;
				for( int j = EmojiMap201709.utf16_max_length ; j > 0 ; -- j ){
					if( j > remain ) continue;
					String check = s.substring( i, i + j );
					image_id = EmojiMap201709.sUTF16ToImageId.get( check );
					if( image_id != null ){
						if( j < remain && s.charAt( i + j ) == 0xFE0E ){
							// 絵文字バリエーション・シーケンス（EVS）のU+FE0E（VS-15）が直後にある場合
							// その文字を絵文字化しない
							emoji = s.substring( i, i + j + 1 );
							image_id = 0;
						}else{
							emoji = check;
						}
						break;
					}
				}
				
				if( image_id != null ){
					if( image_id == 0 ){
						// 絵文字バリエーション・シーケンス（EVS）のU+FE0E（VS-15）が直後にある場合
						// その文字を絵文字化しない
						sb.append( emoji );
					}else{
						addImageSpan( emoji, image_id );
					}
					i += emoji.length();
					continue;
				}
				
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
		
		void addImageSpan( String text, @DrawableRes int res_id ){
			int start = sb.length();
			sb.append( text );
			int end = sb.length();
			sb.setSpan( new EmojiImageSpan( context, res_id ), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE );
		}
		
		void addNetworkEmojiSpan( String text, @NonNull String url ){
			int start = sb.length();
			sb.append( text );
			int end = sb.length();
			sb.setSpan( new NetworkEmojiSpan( url ), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE );
		}
	}
	
	public static boolean isWhitespaceBeforeEmoji( int cp ){
		switch( cp ){
		case 0x0009: // HORIZONTAL TABULATION
		case 0x000A: // LINE FEED
		case 0x000B: // VERTICAL TABULATION
		case 0x000C: // FORM FEED
		case 0x000D: // CARRIAGE RETURN
		case 0x001C: // FILE SEPARATOR
		case 0x001D: // GROUP SEPARATOR
		case 0x001E: // RECORD SEPARATOR
		case 0x001F: // UNIT SEPARATOR
		case 0x0020:
		case 0x00A0: //非区切りスペース
		case 0x1680:
		case 0x180E:
		case 0x2000:
		case 0x2001:
		case 0x2002:
		case 0x2003:
		case 0x2004:
		case 0x2005:
		case 0x2006:
		case 0x2007: //非区切りスペース
		case 0x2008:
		case 0x2009:
		case 0x200A:
		case 0x200B:
		case 0x202F: //非区切りスペース
		case 0x205F:
		case 0x2060:
		case 0x3000:
		case 0x3164:
		case 0xFEFF:
			return true;
		default:
			return Character.isWhitespace( cp );
		}
	}
	
	public static boolean isShortCodeCharacter( int cp ){
		return ( 'A' <= cp && cp <= 'Z' )
			|| ( 'a' <= cp && cp <= 'z' )
			|| ( '0' <= cp && cp <= '9' )
			|| cp == '-'
			|| cp == '+'
			|| cp == '_'
			|| cp == '@'
			;
	}
	
	interface ShortCodeSplitterCallback {
		void onString( @NonNull String part );
		
		void onShortCode( @NonNull String part, @NonNull String name );
	}
	
	static void splitShortCode( @NonNull String s, int start, int end, @NonNull ShortCodeSplitterCallback callback ){
		int i = start;
		while( i < end ){
			// 絵文字パターンの開始位置を探索する
			start = i;
			while( i < end ){
				int c = s.codePointAt( i );
				int width = Character.charCount( c );
				if( c == ':' ){
					if( App1.allow_non_space_before_emoji_shortcode ){
						// アプリ設定により、: の手前に空白を要求しない
						break;
					}else if( i + width < end && s.codePointAt( i + width ) == '@' ){
						// フレニコのプロフ絵文字 :@who: は手前の空白を要求しない
						break;
					}else if( i == 0 || isWhitespaceBeforeEmoji( s.codePointBefore( i ) ) ){
						// ショートコードの手前は始端か改行か空白文字でないとならない
						// 空白文字の判定はサーバサイドのそれにあわせる
						break;
					}
					// shortcodeの開始とみなせないケースだった
				}
				i += width;
			}
			if( i > start ){
				callback.onString( s.substring( start, i ) );
			}
			if( i >= end ) break;
			
			start = i++; // start=コロンの位置 i=その次の位置
			
			int emoji_end = - 1;
			while( i < end ){
				int c = s.codePointAt( i );
				if( c == ':' ){
					emoji_end = i;
					break;
				}
				if( ! isShortCodeCharacter( c ) ){
					break;
				}
				i += Character.charCount( c );
			}
			
			// 絵文字がみつからなかったら、startの位置のコロンだけを処理して残りは次のループで処理する
			if( emoji_end == - 1 || emoji_end - start < 3 ){
				callback.onString( ":" );
				i = start + 1;
				continue;
			}
			
			callback.onShortCode(
				s.substring( start, emoji_end + 1 ) // ":shortcode:"
				, s.substring( start + 1, emoji_end ) // "shortcode"
			);
			
			i = emoji_end + 1;// コロンの次の位置
		}
	}
	
	private static final Pattern reNicoru = Pattern.compile( "\\Anicoru\\d*\\z", Pattern.CASE_INSENSITIVE );
	private static final Pattern reHohoemi = Pattern.compile( "\\Ahohoemi\\d*\\z", Pattern.CASE_INSENSITIVE );
	
	public static Spannable decodeEmoji(
		@NonNull final Context context
		, @NonNull final String s
		, @NonNull DecodeOptions options
	){
		final DecodeEnv decode_env = new DecodeEnv( context );
		final CustomEmoji.Map custom_map = options.customEmojiMap;
		final NicoProfileEmoji.Map profile_emojis = options.profile_emojis;
		
		splitShortCode( s, 0, s.length(), new ShortCodeSplitterCallback() {
			@Override public void onString( @NonNull String part ){
				decode_env.addUnicodeString( part );
			}
			
			@Override public void onShortCode( @NonNull String part, @NonNull String name ){
				EmojiMap201709.EmojiInfo info = EmojiMap201709.sShortNameToImageId.get( name.toLowerCase().replace( '-', '_' ) );
				if( info != null ){
					decode_env.addImageSpan( part, info.image_id );
					return;
				}
				
				// part=":@name:" name="@name"
				if( name.length() >= 2 && name.charAt( 0 ) == '@' ){
					if( profile_emojis != null ){
						NicoProfileEmoji emoji = profile_emojis.get( name );
						if( emoji == null) emoji = profile_emojis.get( name.substring( 1 ) );
						if( emoji != null ){
							decode_env.addNetworkEmojiSpan( part, emoji.url );
						}
					}
					return;
				}
				
				{
					CustomEmoji emoji = ( custom_map == null ? null : custom_map.get( name ) );
					if( emoji != null ){
						String url = ( App1.disable_emoji_animation && ! TextUtils.isEmpty( emoji.static_url ) ) ? emoji.static_url : emoji.url;
						decode_env.addNetworkEmojiSpan( part, url );
						return;
					}
				}
				
				if( reHohoemi.matcher( name ).find() ){
					decode_env.addImageSpan( part, R.drawable.emoji_hohoemi );
				}else if( reNicoru.matcher( name ).find() ){
					decode_env.addImageSpan( part, R.drawable.emoji_nicoru );
				}else{
					decode_env.addUnicodeString( part );
				}
			}
		} );
		
		return decode_env.sb;
	}
	
	// 投稿などの際、表示は不要だがショートコード=>Unicodeの解決を行いたい場合がある
	// カスタム絵文字の変換も行わない
	public static String decodeShortCode( @NonNull final String s ){
		
		final StringBuilder sb = new StringBuilder();
		
		splitShortCode( s, 0, s.length(), new ShortCodeSplitterCallback() {
			@Override public void onString( @NonNull String part ){
				sb.append( part );
			}
			
			@Override public void onShortCode( @NonNull String part, @NonNull String name ){
				EmojiMap201709.EmojiInfo info = EmojiMap201709.sShortNameToImageId.get( name.toLowerCase().replace( '-', '_' ) );
				sb.append( info != null ? info.unified : part );
			}
		} );
		
		return sb.toString();
	}
	
	// 入力補完用。絵文字ショートコード一覧を部分一致で絞り込む
	static ArrayList< CharSequence > searchShortCode( Context context, String prefix, int limit ){
		ArrayList< CharSequence > dst = new ArrayList<>();
		for( String shortCode : EmojiMap201709.sShortNameList ){
			if( dst.size() >= limit ) break;
			if( ! shortCode.contains( prefix ) ) continue;
			
			EmojiMap201709.EmojiInfo info = EmojiMap201709.sShortNameToImageId.get( shortCode );
			if( info == null ) continue;
			
			SpannableStringBuilder sb = new SpannableStringBuilder();
			sb.append( ' ' );
			int start = 0;
			int end = sb.length();
			
			sb.setSpan( new EmojiImageSpan( context, info.image_id ), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE );
			sb.append( ' ' );
			sb.append( ':' );
			sb.append( shortCode );
			sb.append( ':' );
			dst.add( sb );
		}
		return dst;
	}
	
}
