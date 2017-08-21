package jp.juggler.subwaytooter.util;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.Pref;
import jp.juggler.subwaytooter.api.entity.TootAttachment;
import jp.juggler.subwaytooter.api.entity.TootMention;
import jp.juggler.subwaytooter.table.SavedAccount;

public class HTMLDecoder {
	private static final LogCategory log = new LogCategory( "HTMLDecoder" );
	
	private static final int OPEN_TYPE_OPEN_CLOSE = 1;
	private static final int OPEN_TYPE_OPEN = 2;
	private static final int OPEN_TYPE_CLOSE = 3;
	
	private static final String TAG_TEXT = "<>text";
	private static final String TAG_END = "<>end";
	
	private static final Pattern reTag = Pattern.compile( "<(/?)(\\w+)" );
	private static final Pattern reTagEnd = Pattern.compile( "(/?)>$" );
	private static final Pattern reHref = Pattern.compile( "\\bhref=\"([^\"]*)\"" );
	
	private static class TokenParser {
		
		final String src;
		int next;
		
		String tag;
		int open_type;
		String text;
		
		TokenParser( String src ){
			this.src = src;
			this.next = 0;
			eat();
		}
		
		void eat(){
			// end?
			if( next >= src.length() ){
				tag = TAG_END;
				open_type = OPEN_TYPE_OPEN_CLOSE;
				return;
			}
			// text ?
			int end = src.indexOf( '<', next );
			if( end == - 1 ) end = src.length();
			if( end > next ){
				this.text = src.substring( next, end );
				this.tag = TAG_TEXT;
				this.open_type = OPEN_TYPE_OPEN_CLOSE;
				next = end;
				return;
			}
			// tag ?
			end = src.indexOf( '>', next );
			if( end == - 1 ){
				end = src.length();
			}else{
				++ end;
			}
			text = src.substring( next, end );
			next = end;
			Matcher m = reTag.matcher( text );
			if( m.find() ){
				boolean is_close = ! TextUtils.isEmpty( m.group( 1 ) );
				tag = m.group( 2 ).toLowerCase();
				Matcher m2 = reTagEnd.matcher( text );
				boolean is_openclose = false;
				if( m2.find() ){
					is_openclose = ! TextUtils.isEmpty( m2.group( 1 ) );
				}
				open_type = is_close ? OPEN_TYPE_CLOSE : is_openclose ? OPEN_TYPE_OPEN_CLOSE : OPEN_TYPE_OPEN;
				if( tag.equals( "br" ) ) open_type = OPEN_TYPE_OPEN_CLOSE;
			}else{
				tag = TAG_TEXT;
				this.open_type = OPEN_TYPE_OPEN_CLOSE;
			}
		}
	}
	
	private static final boolean DEBUG_HTML_PARSER = false;
	
	static final Pattern reUserPage = Pattern.compile( "\\Ahttps://([^/]+)/@([^?#/]+)(?:\\z|\\?)" );
	
	private static class Node {
		final ArrayList< Node > child_nodes = new ArrayList<>();
		
		String tag;
		String text;
		
		Node(){
			tag = "<>root";
			text = "";
		}
		
		Node( TokenParser t ){
			this.tag = t.tag;
			this.text = t.text;
		}
		
		void parseChild( TokenParser t, String indent ){
			if( DEBUG_HTML_PARSER ) log.d( "parseChild: %s(%s", indent, tag );
			for( ; ; ){
				if( TAG_END.equals( t.tag ) ) break;
				if( OPEN_TYPE_CLOSE == t.open_type ){
					t.eat();
					break;
				}
				int open_type = t.open_type;
				Node child = new Node( t );
				child_nodes.add( child );
				t.eat();
				
				if( DEBUG_HTML_PARSER )
					log.d( "parseChild: %s|%s %s [%s]", indent, child.tag, open_type, child.text );
				
				if( OPEN_TYPE_OPEN == open_type ){
					child.parseChild( t, indent + "--" );
				}
			}
			if( DEBUG_HTML_PARSER ) log.d( "parseChild: %s)%s", indent, tag );
		}
		
		void encodeSpan(
			Context context
			, LinkClickContext account
			, SpannableStringBuilder sb
			, boolean bShort
			, boolean bDecodeEmoji
			, @Nullable TootAttachment.List list_attachment
		){
			if( TAG_TEXT.equals( tag ) ){
				if( bDecodeEmoji ){
					sb.append( Emojione.decodeEmoji( context, decodeEntity( text ) ) );
				}else{
					sb.append( decodeEntity( text ) );
				}
				return;
			}
			if( DEBUG_HTML_PARSER ) sb.append( "(start " ).append( tag ).append( ")" );
			
			SpannableStringBuilder sb_tmp;
			if( "a".equals( tag ) ){
				sb_tmp = new SpannableStringBuilder();
			}else{
				sb_tmp = sb;
			}
			
			int start = sb_tmp.length();
			
			for( Node child : child_nodes ){
				child.encodeSpan( context, account, sb_tmp, bShort, bDecodeEmoji, list_attachment );
			}
			
			int end = sb_tmp.length();
			
			if( "a".equals( tag ) ){
				start = sb.length();
				sb.append( encodeUrl( bShort, context, sb_tmp.toString(), getHref(), list_attachment ) );
				end = sb.length();
			}
			
			if( end > start && "a".equals( tag ) ){
				String href = getHref();
				if( href != null ){
					MyClickableSpan span = new MyClickableSpan( account, href, account.findAcctColor( href ) );
					sb.setSpan( span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE );
				}
			}
			
			if( DEBUG_HTML_PARSER ) sb.append( "(end " ).append( tag ).append( ")" );
			
			if( "br".equals( tag ) ) sb.append( '\n' );
			
			if( "p".equals( tag ) || "li".equals( tag ) ){
				if( sb.length() > 0 ){
					if( sb.charAt( sb.length() - 1 ) != '\n' ) sb.append( '\n' );
					sb.append( '\n' );
				}
			}
		}
		
		private String getHref(){
			Matcher m = reHref.matcher( text );
			if( m.find() ){
				final String href = decodeEntity( m.group( 1 ) );
				if( ! TextUtils.isEmpty( href ) ){
					return href;
				}
			}
			return null;
		}
		
		boolean is_media_attachment( @Nullable TootAttachment.List list_attachment, @Nullable String href ){
			if( href == null || list_attachment == null ) return false;
			for( TootAttachment a : list_attachment ){
				if( href.equals( a.remote_url )
					|| href.equals( a.url )
					|| href.equals( a.text_url )
					) return true;
			}
			return false;
		}
		
		private CharSequence encodeUrl(
			boolean bShort
			, Context context
			, String display_url
			, @Nullable String href
			, @Nullable TootAttachment.List list_attachment
		){
			if( ! display_url.startsWith( "http" ) ){
				if( display_url.startsWith( "@" ) && href != null && App1.pref.getBoolean( Pref.KEY_MENTION_FULL_ACCT, false ) ){
					// メンションをfull acct にする
					Matcher m = reUserPage.matcher( href );
					if( m.find() ){
						return "@" + m.group( 2 ) + "@" + m.group( 1 );
					}
				}
				// ハッシュタグやメンションは変更しない
				return display_url;
			}
			
			if( ! bShort ){
				return display_url;
			}
			
			if( is_media_attachment( list_attachment, href ) ){
				return Emojione.decodeEmoji( context, ":frame_photo:" );
			}
			
			try{
				Uri uri = Uri.parse( display_url );
				StringBuilder sb = new StringBuilder();
				sb.append( uri.getAuthority() );
				String a = uri.getEncodedPath();
				String q = uri.getEncodedQuery();
				String f = uri.getEncodedFragment();
				String remain = a + ( q == null ? "" : "?" + q ) + ( f == null ? "" : "#" + f );
				if( remain.length() > 10 ){
					sb.append( remain.substring( 0, 10 ) );
					sb.append( "…" );
				}else{
					sb.append( remain );
				}
				return sb;
			}catch( Throwable ex ){
				log.trace( ex );
				return display_url;
			}
		}
	}
	
	private static boolean isWhitespace( char c ){
		return Character.isWhitespace( c ) || c == 0x0a || c == 0x0d;
	}
	
	public static SpannableStringBuilder decodeHTML(
		Context context
		, LinkClickContext account
		, String src
		, boolean bShort
		, boolean bDecodeEmoji
		, @Nullable TootAttachment.List list_attachment
	){
		SpannableStringBuilder sb = new SpannableStringBuilder();
		try{
			if( src != null ){
				TokenParser tracker = new TokenParser( src );
				Node rootNode = new Node();
				rootNode.parseChild( tracker, "" );
				
				rootNode.encodeSpan( context, account, sb, bShort, bDecodeEmoji, list_attachment );
				int end = sb.length();
				while( end > 0 && isWhitespace( sb.charAt( end - 1 ) ) ) -- end;
				if( end < sb.length() ){
					sb.delete( end, sb.length() );
				}
				
				//				sb.append( "\n" );
				//				sb.append(src);
			}
		}catch( Throwable ex ){
			log.trace( ex );
		}
		
		return sb;
	}
	
	//	public static Spannable decodeTags( final LinkClickContext account, TootTag.List src_list ){
	//		if( src_list == null || src_list.isEmpty() ) return null;
	//		SpannableStringBuilder sb = new SpannableStringBuilder();
	//		for( TootTag item : src_list ){
	//			if( sb.length() > 0 ) sb.append( " " );
	//			int start = sb.length();
	//			sb.append( '#' );
	//			sb.append( item.name );
	//			int end = sb.length();
	//			if( end > start ){
	//				final String item_url = item.url;
	//				sb.setSpan( new ClickableSpan() {
	//					@Override public void onClick( View widget ){
	//						if( link_callback != null ){
	//							link_callback.onClickLink( account, item_url );
	//						}
	//					}
	//				}, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE );
	//			}
	//		}
	//		return sb;
	//	}
	
	public static Spannable decodeMentions( final SavedAccount access_info, TootMention.List src_list ){
		if( src_list == null || src_list.isEmpty() ) return null;
		SpannableStringBuilder sb = new SpannableStringBuilder();
		for( TootMention item : src_list ){
			if( sb.length() > 0 ) sb.append( " " );
			int start = sb.length();
			sb.append( '@' );
			if( App1.pref.getBoolean( Pref.KEY_MENTION_FULL_ACCT, false ) ){
				sb.append( access_info.getFullAcct( item.acct ) );
			}else{
				sb.append( item.acct );
			}
			int end = sb.length();
			if( end > start ){
				MyClickableSpan span = new MyClickableSpan( access_info, item.url, access_info.findAcctColor( item.url ) );
				
				sb.setSpan( span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE );
			}
		}
		return sb;
	}
	
	//////////////////////////////////////////////////////////////////////////////////////
	
	private static final HashMap< String, Character > entity_map = new HashMap<>();
	
	private static void _addEntity( String s, char c ){
		entity_map.put( s, c );
	}
	
	private static char chr( int num ){
		return (char) num;
	}
	
	private static final Pattern reEntity = Pattern.compile( "&(#?)(\\w+);" );
	
	public static String decodeEntity( String src ){
		StringBuilder sb = null;
		Matcher m = reEntity.matcher( src );
		int last_end = 0;
		while( m.find() ){
			if( sb == null ) sb = new StringBuilder();
			int start = m.start();
			int end = m.end();
			try{
				if( start > last_end ){
					sb.append( src.substring( last_end, start ) );
				}
				boolean is_numeric = m.group( 1 ).length() > 0;
				String part = m.group( 2 );
				if( ! is_numeric ){
					Character c = entity_map.get( part );
					if( c != null ){
						sb.append( (char) c );
						continue;
					}
				}else{
					int c;
					try{
						if( part.charAt( 0 ) == 'x' ){
							c = Integer.parseInt( part.substring( 1 ), 16 );
						}else{
							c = Integer.parseInt( part, 10 );
						}
						sb.append( (char) c );
						continue;
					}catch( Throwable ex ){
						log.trace( ex );
					}
				}
				sb.append( src.substring( start, end ) );
			}finally{
				last_end = end;
			}
		}
		
		// 全くマッチしなかった
		if( sb == null ) return src;
		
		int end = src.length();
		if( end > last_end ){
			sb.append( src.substring( last_end, end ) );
		}
		return sb.toString();
	}
	
	private static void init1(){
		_addEntity( "amp", '&' ); // ampersand
		_addEntity( "gt", '>' ); // greater than
		_addEntity( "lt", '<' ); // less than
		_addEntity( "quot", '"' ); // double quote
		_addEntity( "apos", '\'' ); // single quote
		_addEntity( "AElig", chr( 198 ) ); // capital AE diphthong (ligature)
		_addEntity( "Aacute", chr( 193 ) ); // capital A, acute accent
		_addEntity( "Acirc", chr( 194 ) ); // capital A, circumflex accent
		_addEntity( "Agrave", chr( 192 ) ); // capital A, grave accent
		_addEntity( "Aring", chr( 197 ) ); // capital A, ring
		_addEntity( "Atilde", chr( 195 ) ); // capital A, tilde
		_addEntity( "Auml", chr( 196 ) ); // capital A, dieresis or umlaut mark
		_addEntity( "Ccedil", chr( 199 ) ); // capital C, cedilla
		_addEntity( "ETH", chr( 208 ) ); // capital Eth, Icelandic
		_addEntity( "Eacute", chr( 201 ) ); // capital E, acute accent
		_addEntity( "Ecirc", chr( 202 ) ); // capital E, circumflex accent
		_addEntity( "Egrave", chr( 200 ) ); // capital E, grave accent
		_addEntity( "Euml", chr( 203 ) ); // capital E, dieresis or umlaut mark
		_addEntity( "Iacute", chr( 205 ) ); // capital I, acute accent
		_addEntity( "Icirc", chr( 206 ) ); // capital I, circumflex accent
		_addEntity( "Igrave", chr( 204 ) ); // capital I, grave accent
		_addEntity( "Iuml", chr( 207 ) ); // capital I, dieresis or umlaut mark
		_addEntity( "Ntilde", chr( 209 ) ); // capital N, tilde
		_addEntity( "Oacute", chr( 211 ) ); // capital O, acute accent
		_addEntity( "Ocirc", chr( 212 ) ); // capital O, circumflex accent
		_addEntity( "Ograve", chr( 210 ) ); // capital O, grave accent
		_addEntity( "Oslash", chr( 216 ) ); // capital O, slash
		_addEntity( "Otilde", chr( 213 ) ); // capital O, tilde
		_addEntity( "Ouml", chr( 214 ) ); // capital O, dieresis or umlaut mark
		_addEntity( "THORN", chr( 222 ) ); // capital THORN, Icelandic
		_addEntity( "Uacute", chr( 218 ) ); // capital U, acute accent
		_addEntity( "Ucirc", chr( 219 ) ); // capital U, circumflex accent
		_addEntity( "Ugrave", chr( 217 ) ); // capital U, grave accent
		_addEntity( "Uuml", chr( 220 ) ); // capital U, dieresis or umlaut mark
		_addEntity( "Yacute", chr( 221 ) ); // capital Y, acute accent
		_addEntity( "aacute", chr( 225 ) ); // small a, acute accent
		_addEntity( "acirc", chr( 226 ) ); // small a, circumflex accent
		_addEntity( "aelig", chr( 230 ) ); // small ae diphthong (ligature)
		_addEntity( "agrave", chr( 224 ) ); // small a, grave accent
		_addEntity( "aring", chr( 229 ) ); // small a, ring
		_addEntity( "atilde", chr( 227 ) ); // small a, tilde
		_addEntity( "auml", chr( 228 ) ); // small a, dieresis or umlaut mark
		_addEntity( "ccedil", chr( 231 ) ); // small c, cedilla
		_addEntity( "eacute", chr( 233 ) ); // small e, acute accent
		_addEntity( "ecirc", chr( 234 ) ); // small e, circumflex accent
		_addEntity( "egrave", chr( 232 ) ); // small e, grave accent
		_addEntity( "eth", chr( 240 ) ); // small eth, Icelandic
		_addEntity( "euml", chr( 235 ) ); // small e, dieresis or umlaut mark
		_addEntity( "iacute", chr( 237 ) ); // small i, acute accent
		_addEntity( "icirc", chr( 238 ) ); // small i, circumflex accent
		_addEntity( "igrave", chr( 236 ) ); // small i, grave accent
		_addEntity( "iuml", chr( 239 ) ); // small i, dieresis or umlaut mark
		_addEntity( "ntilde", chr( 241 ) ); // small n, tilde
		_addEntity( "oacute", chr( 243 ) ); // small o, acute accent
		_addEntity( "ocirc", chr( 244 ) ); // small o, circumflex accent
		_addEntity( "ograve", chr( 242 ) ); // small o, grave accent
		_addEntity( "oslash", chr( 248 ) ); // small o, slash
		_addEntity( "otilde", chr( 245 ) ); // small o, tilde
		_addEntity( "ouml", chr( 246 ) ); // small o, dieresis or umlaut mark
		_addEntity( "szlig", chr( 223 ) ); // small sharp s, German (sz ligature)
		_addEntity( "thorn", chr( 254 ) ); // small thorn, Icelandic
		_addEntity( "uacute", chr( 250 ) ); // small u, acute accent
		_addEntity( "ucirc", chr( 251 ) ); // small u, circumflex accent
		_addEntity( "ugrave", chr( 249 ) ); // small u, grave accent
		_addEntity( "uuml", chr( 252 ) ); // small u, dieresis or umlaut mark
		_addEntity( "yacute", chr( 253 ) ); // small y, acute accent
		_addEntity( "yuml", chr( 255 ) ); // small y, dieresis or umlaut mark
		_addEntity( "copy", chr( 169 ) ); // copyright sign
		_addEntity( "reg", chr( 174 ) ); // registered sign
		_addEntity( "nbsp", chr( 160 ) ); // non breaking space
		_addEntity( "iexcl", chr( 161 ) );
		_addEntity( "cent", chr( 162 ) );
		_addEntity( "pound", chr( 163 ) );
		_addEntity( "curren", chr( 164 ) );
		_addEntity( "yen", chr( 165 ) );
		_addEntity( "brvbar", chr( 166 ) );
		_addEntity( "sect", chr( 167 ) );
		_addEntity( "uml", chr( 168 ) );
		_addEntity( "ordf", chr( 170 ) );
		_addEntity( "laquo", chr( 171 ) );
		_addEntity( "not", chr( 172 ) );
		_addEntity( "shy", chr( 173 ) );
		_addEntity( "macr", chr( 175 ) );
		_addEntity( "deg", chr( 176 ) );
		_addEntity( "plusmn", chr( 177 ) );
		_addEntity( "sup1", chr( 185 ) );
		_addEntity( "sup2", chr( 178 ) );
		_addEntity( "sup3", chr( 179 ) );
		_addEntity( "acute", chr( 180 ) );
		_addEntity( "micro", chr( 181 ) );
		_addEntity( "para", chr( 182 ) );
		_addEntity( "middot", chr( 183 ) );
		_addEntity( "cedil", chr( 184 ) );
		_addEntity( "ordm", chr( 186 ) );
		_addEntity( "raquo", chr( 187 ) );
		_addEntity( "frac14", chr( 188 ) );
		_addEntity( "frac12", chr( 189 ) );
		_addEntity( "frac34", chr( 190 ) );
		_addEntity( "iquest", chr( 191 ) );
		_addEntity( "times", chr( 215 ) );
		
	}
	
	private static void init2(){
		_addEntity( "divide", chr( 247 ) );
		_addEntity( "OElig", chr( 338 ) );
		_addEntity( "oelig", chr( 339 ) );
		_addEntity( "Scaron", chr( 352 ) );
		_addEntity( "scaron", chr( 353 ) );
		_addEntity( "Yuml", chr( 376 ) );
		_addEntity( "fnof", chr( 402 ) );
		_addEntity( "circ", chr( 710 ) );
		_addEntity( "tilde", chr( 732 ) );
		_addEntity( "Alpha", chr( 913 ) );
		_addEntity( "Beta", chr( 914 ) );
		_addEntity( "Gamma", chr( 915 ) );
		_addEntity( "Delta", chr( 916 ) );
		_addEntity( "Epsilon", chr( 917 ) );
		_addEntity( "Zeta", chr( 918 ) );
		_addEntity( "Eta", chr( 919 ) );
		_addEntity( "Theta", chr( 920 ) );
		_addEntity( "Iota", chr( 921 ) );
		_addEntity( "Kappa", chr( 922 ) );
		_addEntity( "Lambda", chr( 923 ) );
		_addEntity( "Mu", chr( 924 ) );
		_addEntity( "Nu", chr( 925 ) );
		_addEntity( "Xi", chr( 926 ) );
		_addEntity( "Omicron", chr( 927 ) );
		_addEntity( "Pi", chr( 928 ) );
		_addEntity( "Rho", chr( 929 ) );
		_addEntity( "Sigma", chr( 931 ) );
		_addEntity( "Tau", chr( 932 ) );
		_addEntity( "Upsilon", chr( 933 ) );
		_addEntity( "Phi", chr( 934 ) );
		_addEntity( "Chi", chr( 935 ) );
		_addEntity( "Psi", chr( 936 ) );
		_addEntity( "Omega", chr( 937 ) );
		_addEntity( "alpha", chr( 945 ) );
		_addEntity( "beta", chr( 946 ) );
		_addEntity( "gamma", chr( 947 ) );
		_addEntity( "delta", chr( 948 ) );
		_addEntity( "epsilon", chr( 949 ) );
		_addEntity( "zeta", chr( 950 ) );
		_addEntity( "eta", chr( 951 ) );
		_addEntity( "theta", chr( 952 ) );
		_addEntity( "iota", chr( 953 ) );
		_addEntity( "kappa", chr( 954 ) );
		_addEntity( "lambda", chr( 955 ) );
		_addEntity( "mu", chr( 956 ) );
		_addEntity( "nu", chr( 957 ) );
		_addEntity( "xi", chr( 958 ) );
		_addEntity( "omicron", chr( 959 ) );
		_addEntity( "pi", chr( 960 ) );
		_addEntity( "rho", chr( 961 ) );
		_addEntity( "sigmaf", chr( 962 ) );
		_addEntity( "sigma", chr( 963 ) );
		_addEntity( "tau", chr( 964 ) );
		_addEntity( "upsilon", chr( 965 ) );
		_addEntity( "phi", chr( 966 ) );
		_addEntity( "chi", chr( 967 ) );
		_addEntity( "psi", chr( 968 ) );
		_addEntity( "omega", chr( 969 ) );
		_addEntity( "thetasym", chr( 977 ) );
		_addEntity( "upsih", chr( 978 ) );
		_addEntity( "piv", chr( 982 ) );
		_addEntity( "ensp", chr( 8194 ) );
		_addEntity( "emsp", chr( 8195 ) );
		_addEntity( "thinsp", chr( 8201 ) );
		_addEntity( "zwnj", chr( 8204 ) );
		_addEntity( "zwj", chr( 8205 ) );
		_addEntity( "lrm", chr( 8206 ) );
		_addEntity( "rlm", chr( 8207 ) );
		_addEntity( "ndash", chr( 8211 ) );
		_addEntity( "mdash", chr( 8212 ) );
		_addEntity( "lsquo", chr( 8216 ) );
		_addEntity( "rsquo", chr( 8217 ) );
		_addEntity( "sbquo", chr( 8218 ) );
		_addEntity( "ldquo", chr( 8220 ) );
		_addEntity( "rdquo", chr( 8221 ) );
		_addEntity( "bdquo", chr( 8222 ) );
		_addEntity( "dagger", chr( 8224 ) );
		_addEntity( "Dagger", chr( 8225 ) );
		_addEntity( "bull", chr( 8226 ) );
		_addEntity( "hellip", chr( 8230 ) );
		_addEntity( "permil", chr( 8240 ) );
		_addEntity( "prime", chr( 8242 ) );
		_addEntity( "Prime", chr( 8243 ) );
		_addEntity( "lsaquo", chr( 8249 ) );
		_addEntity( "rsaquo", chr( 8250 ) );
		_addEntity( "oline", chr( 8254 ) );
		_addEntity( "frasl", chr( 8260 ) );
		_addEntity( "euro", chr( 8364 ) );
		_addEntity( "image", chr( 8465 ) );
		_addEntity( "weierp", chr( 8472 ) );
		_addEntity( "real", chr( 8476 ) );
		_addEntity( "trade", chr( 8482 ) );
		_addEntity( "alefsym", chr( 8501 ) );
		_addEntity( "larr", chr( 8592 ) );
		_addEntity( "uarr", chr( 8593 ) );
		_addEntity( "rarr", chr( 8594 ) );
		_addEntity( "darr", chr( 8595 ) );
		_addEntity( "harr", chr( 8596 ) );
		_addEntity( "crarr", chr( 8629 ) );
		_addEntity( "lArr", chr( 8656 ) );
		
	}
	
	private static void init3(){
		_addEntity( "uArr", chr( 8657 ) );
		_addEntity( "rArr", chr( 8658 ) );
		_addEntity( "dArr", chr( 8659 ) );
		_addEntity( "hArr", chr( 8660 ) );
		_addEntity( "forall", chr( 8704 ) );
		_addEntity( "part", chr( 8706 ) );
		_addEntity( "exist", chr( 8707 ) );
		_addEntity( "empty", chr( 8709 ) );
		_addEntity( "nabla", chr( 8711 ) );
		_addEntity( "isin", chr( 8712 ) );
		_addEntity( "notin", chr( 8713 ) );
		_addEntity( "ni", chr( 8715 ) );
		_addEntity( "prod", chr( 8719 ) );
		_addEntity( "sum", chr( 8721 ) );
		_addEntity( "minus", chr( 8722 ) );
		_addEntity( "lowast", chr( 8727 ) );
		_addEntity( "radic", chr( 8730 ) );
		_addEntity( "prop", chr( 8733 ) );
		_addEntity( "infin", chr( 8734 ) );
		_addEntity( "ang", chr( 8736 ) );
		_addEntity( "and", chr( 8743 ) );
		_addEntity( "or", chr( 8744 ) );
		_addEntity( "cap", chr( 8745 ) );
		_addEntity( "cup", chr( 8746 ) );
		_addEntity( "int", chr( 8747 ) );
		_addEntity( "there4", chr( 8756 ) );
		_addEntity( "sim", chr( 8764 ) );
		_addEntity( "cong", chr( 8773 ) );
		_addEntity( "asymp", chr( 8776 ) );
		_addEntity( "ne", chr( 8800 ) );
		_addEntity( "equiv", chr( 8801 ) );
		_addEntity( "le", chr( 8804 ) );
		_addEntity( "ge", chr( 8805 ) );
		_addEntity( "sub", chr( 8834 ) );
		_addEntity( "sup", chr( 8835 ) );
		_addEntity( "nsub", chr( 8836 ) );
		_addEntity( "sube", chr( 8838 ) );
		_addEntity( "supe", chr( 8839 ) );
		_addEntity( "oplus", chr( 8853 ) );
		_addEntity( "otimes", chr( 8855 ) );
		_addEntity( "perp", chr( 8869 ) );
		_addEntity( "sdot", chr( 8901 ) );
		_addEntity( "lceil", chr( 8968 ) );
		_addEntity( "rceil", chr( 8969 ) );
		_addEntity( "lfloor", chr( 8970 ) );
		_addEntity( "rfloor", chr( 8971 ) );
		_addEntity( "lang", chr( 9001 ) );
		_addEntity( "rang", chr( 9002 ) );
		_addEntity( "loz", chr( 9674 ) );
		_addEntity( "spades", chr( 9824 ) );
		_addEntity( "clubs", chr( 9827 ) );
		_addEntity( "hearts", chr( 9829 ) );
		_addEntity( "diams", chr( 9830 ) );
		
	}
	
	static{
		init1();
		init2();
		init3();
	}
}
