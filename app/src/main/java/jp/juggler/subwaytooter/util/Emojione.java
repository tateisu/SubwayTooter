package jp.juggler.subwaytooter.util;

import android.text.SpannableStringBuilder;
import android.text.Spanned;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.App1;
import uk.co.chrisjenx.calligraphy.CalligraphyTypefaceSpan;

public abstract class Emojione
{
	private static final Pattern SHORTNAME_PATTERN = Pattern.compile(":([-+\\w]+):");
	
	public static final HashMap<String,String> map_name2unicode = EmojiMap._shortNameToUnicode;
	public static final HashMap<String,String> map_unicode2name = EmojiMap._unicodeToShortName;

	static class DecodeEnv{
		SpannableStringBuilder sb = new SpannableStringBuilder();
		int last_span_start = -1;
		int last_span_end = -1;
		
		void closeSpan(){
			if( last_span_start >= 0 ){
				CalligraphyTypefaceSpan typefaceSpan = new CalligraphyTypefaceSpan( App1.typeface_emoji );
				sb.setSpan(typefaceSpan, last_span_start,last_span_end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
				last_span_start = -1;
			}
		}

		void addEmoji(String s){
			if( last_span_start < 0 ){
				last_span_start = sb.length();
			}
			sb.append(s);
			last_span_end = sb.length();
		}
		
		void addUnicodeString(String s){
			int i = 0;
			int end = s.length();
			while( i < end ){
				int remain = end - i;
				if( remain >= 4 ){
					String check = s.substring( i, i + 4 );
					if( map_unicode2name.containsKey( check ) ){
						addEmoji( check );
						i += 4;
						continue;
					}
				}
				if( remain >= 3 ){
					String check = s.substring( i, i + 3 );
					if( map_unicode2name.containsKey( check ) ){
						addEmoji( check );
						i += 3;
						continue;
					}
				}
				if( remain >= 2 ){
					String check = s.substring( i, i + 2 );
					if( map_unicode2name.containsKey( check ) ){
						addEmoji( check );
						i += 2;
						continue;
					}
				}
				if( remain >= 1 ){
					String check = s.substring( i, i + 1 );
					if( map_unicode2name.containsKey( check ) ){
						addEmoji( check );
						i += 1;
						continue;
					}
				}
				closeSpan();
				sb.append( s.charAt( i ) );
				++ i;
			}
		}
	}
	
	public static CharSequence decodeEmoji( String s ){
		DecodeEnv decode_env = new DecodeEnv();
		Matcher matcher = SHORTNAME_PATTERN.matcher(s);
		int last_end = 0;
		while( matcher.find() ){
			int start = matcher.start();
			int end = matcher.end();
			if( start > last_end ){
				decode_env.addUnicodeString(s.substring( last_end,start ));
			}
			last_end = end;
			//
			String unicode = map_name2unicode.get(matcher.group(1));
			if( unicode == null ){
				decode_env.addUnicodeString(s.substring( start, end ));
			}else{
				decode_env.addEmoji( unicode );
			}
		}
		// close span
		decode_env.closeSpan();
		// copy remain
		int end = s.length();
		if( end > last_end ){
			decode_env.addUnicodeString(s.substring( last_end, end ));
		}
		return decode_env.sb;
	}
}
