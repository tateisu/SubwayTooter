package jp.juggler.subwaytooter.util;

import android.support.annotation.NonNull;
import android.support.v4.util.SparseArrayCompat;

import java.util.Locale;

public class CharacterGroup {
	
	// Tokenizerが終端に達したことを示す
	static final int END = - 1;
	
	// 同じと見なす文字列の集合
	static class Group {
		
		// 文字列の配列
		@NonNull final String[] list;
		
		// 代表する文字のコード
		final int id;
		
		Group( @NonNull String[] list ){
			this.list = list;
			this.id = findGroupId();
		}
		
		private int findGroupId(){
			// グループのIDは、グループ中の文字(長さ1)のunicodeのどれか
			for( String s : list ){
				if( s.length() == 1 ){
					return s.charAt( 0 );
				}
			}
			throw new RuntimeException( "group has not id!!" );
		}
	}

	
	// テキスト中に出現した文字列からグループを探すためのマップ
	
	// キー文字数1
	private final SparseArrayCompat< Group > map1 = new SparseArrayCompat<>();
	
	// キー文字数2
	private final SparseArrayCompat< Group > map2 = new SparseArrayCompat<>();
	
	// グループをmapに登録する
	private void addGroup( @NonNull String[] list ){
		
		// グループを生成
		Group g = new Group( list );
		
		// 含まれる各文字列をマップに登録する
		for( String s : list ){
			int len = s.length();
			int v1 = s.charAt( 0 );
			
			SparseArrayCompat< Group > map;
			int key;
			if( len == 1 ){
				map = map1;
				key = v1;
			}else{
				map = map2;
				int v2 = s.charAt( 1 );
				key = v1 | ( v2 << 16 );
			}
			
			Group old = map.get( key );
			if( old != null && old != g ){
				throw new RuntimeException( String.format( Locale.JAPAN, "group conflict: %s", s ) );
			}
			map.put( key, g );
		}
	}
	
	// CharSequence の範囲 から 文字,グループ,終端 のどれかを列挙する
	class Tokenizer {
		public CharSequence text;
		public int end;
		
		// next() を読むと以下の変数が更新される
		public int offset;
		public int c; // may END or group.id or UTF-16 character
		public Group group;
		
		Tokenizer( @NonNull CharSequence text, int start, int end ){
			reset( text, start, end );
		}
		
		public void reset( CharSequence text, int start, int end ){
			this.text = text;
			this.offset = start;
			this.end = end;
		}
		
		public void next(){
			
			int pos = offset;
			
			// 空白を読み飛ばす
			while( pos < end && isWhitespace( text.charAt( pos ) ) ) ++ pos;
			
			// 終端までの文字数
			int remain = end - pos;
			if( remain <= 0 ){
				// 空白を読み飛ばしたら終端になった
				// 終端の場合、末尾の空白はoffsetに含めない
				this.group = null;
				this.c = END;
				return;
			}
			
			int v1 = text.charAt( pos );
			int v2 = remain > 1 ? text.charAt( pos + 1 ) : 0;
			
			// グループに登録された文字を長い順にチェック
			int check_len = remain > 2 ? 2 : remain;
			while( check_len > 0 ){
				Group g = check_len == 1 ? map1.get( v1 ) : map2.get( v1 | ( v2 << 16 ) );
				if( g != null ){
					this.group = g;
					this.c = g.id;
					this.offset = pos + check_len;
					return;
				}
				-- check_len;
			}
			
			this.group = null;
			this.c = v1;
			this.offset = pos + 1;
		}
	}
	
	Tokenizer tokenizer( CharSequence text, int start, int end ){
		return new Tokenizer( text, start, end );
	}
	
	public static boolean isWhitespace( int cp ){
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
		case 0x0085: // next line (latin-1)
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
		case 0x200C:
		case 0x200D:
		case 0x2028: // line separator
		case 0x2029: // paragraph separator
		
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
	
	// 文字コードから文字列を作る
	private static String c2s( char[] tmp, int c ){
		tmp[ 0 ] = (char) c;
		return new String( tmp, 0, 1 );
	}
	
	CharacterGroup(){
		char[] tmp = new char[ 1 ];
		
		// 数字
		for( int i = 0 ; i < 9 ; ++ i ){
			String[] list = new String[ 2 ];
			list[ 0 ] = c2s( tmp, '0' + i );
			list[ 1 ] = c2s( tmp, '０' + i );
			addGroup( list );
		}
		
		// 英字
		for( int i = 0 ; i < 26 ; ++ i ){
			String[] list = new String[ 4 ];
			list[ 0 ] = c2s( tmp, 'a' + i );
			list[ 1 ] = c2s( tmp, 'A' + i );
			list[ 2 ] = c2s( tmp, 'ａ' + i );
			list[ 3 ] = c2s( tmp, 'Ａ' + i );
			addGroup( list );
		}
		
		// ハイフン
		addGroup( new String[]{
			c2s( tmp, 0x002D ), // ASCIIのハイフン
			c2s( tmp, 0x30FC ), // 全角カナの長音 Shift_JIS由来
			c2s( tmp, 0x2010 ),
			c2s( tmp, 0x2011 ),
			c2s( tmp, 0x2013 ),
			c2s( tmp, 0x2014 ),
			c2s( tmp, 0x2015 ), // 全角カナのダッシュ Shift_JIS由来
			c2s( tmp, 0x2212 ),
			c2s( tmp, 0xFF0d ), // 全角カナの長音 MS932由来
			c2s( tmp, 0xFF70 ), // 半角カナの長音 MS932由来
		} );
		
		addGroup( new String[]{ "！", "!" } );
		addGroup( new String[]{ "＂", "\"" } );
		addGroup( new String[]{ "＃", "#" } );
		addGroup( new String[]{ "＄", "$" } );
		addGroup( new String[]{ "％", "%" } );
		addGroup( new String[]{ "＆", "&" } );
		addGroup( new String[]{ "＇", "'" } );
		addGroup( new String[]{ "（", "(" } );
		addGroup( new String[]{ "）", ")" } );
		addGroup( new String[]{ "＊", "*" } );
		addGroup( new String[]{ "＋", "+" } );
		addGroup( new String[]{ "，", ",", "、", "､" } );
		addGroup( new String[]{ "．", ".", "。", "｡" } );
		addGroup( new String[]{ "／", "/" } );
		addGroup( new String[]{ "：", ":" } );
		addGroup( new String[]{ "；", ";" } );
		addGroup( new String[]{ "＜", "<" } );
		addGroup( new String[]{ "＝", "=" } );
		addGroup( new String[]{ "＞", ">" } );
		addGroup( new String[]{ "？", "?" } );
		addGroup( new String[]{ "＠", "@" } );
		addGroup( new String[]{ "［", "[" } );
		addGroup( new String[]{ "＼", "\\", "￥" } );
		addGroup( new String[]{ "］", "]" } );
		addGroup( new String[]{ "＾", "^" } );
		addGroup( new String[]{ "＿", "_" } );
		addGroup( new String[]{ "｀", "`" } );
		addGroup( new String[]{ "｛", "{" } );
		addGroup( new String[]{ "｜", "|", "￤" } );
		addGroup( new String[]{ "｝", "}" } );
		
		addGroup( new String[]{ "・", "･", "・" } );
		addGroup( new String[]{ "「", "｢", "「" } );
		addGroup( new String[]{ "」", "｣", "」" } );
		
		// チルダ
		addGroup( new String[]{  "~", c2s(tmp,0x301C),c2s(tmp,0xFF5E) } );
		
		// 半角カナの濁音,半濁音は2文字になる
		addGroup( new String[]{ "ガ", "が", "ｶﾞ" } );
		addGroup( new String[]{ "ギ", "ぎ", "ｷﾞ" } );
		addGroup( new String[]{ "グ", "ぐ", "ｸﾞ" } );
		addGroup( new String[]{ "ゲ", "げ", "ｹﾞ" } );
		addGroup( new String[]{ "ゴ", "ご", "ｺﾞ" } );
		addGroup( new String[]{ "ザ", "ざ", "ｻﾞ" } );
		addGroup( new String[]{ "ジ", "じ", "ｼﾞ" } );
		addGroup( new String[]{ "ズ", "ず", "ｽﾞ" } );
		addGroup( new String[]{ "ゼ", "ぜ", "ｾﾞ" } );
		addGroup( new String[]{ "ゾ", "ぞ", "ｿﾞ" } );
		addGroup( new String[]{ "ダ", "だ", "ﾀﾞ" } );
		addGroup( new String[]{ "ヂ", "ぢ", "ﾁﾞ" } );
		addGroup( new String[]{ "ヅ", "づ", "ﾂﾞ" } );
		addGroup( new String[]{ "デ", "で", "ﾃﾞ" } );
		addGroup( new String[]{ "ド", "ど", "ﾄﾞ" } );
		addGroup( new String[]{ "バ", "ば", "ﾊﾞ" } );
		addGroup( new String[]{ "ビ", "び", "ﾋﾞ" } );
		addGroup( new String[]{ "ブ", "ぶ", "ﾌﾞ" } );
		addGroup( new String[]{ "ベ", "べ", "ﾍﾞ" } );
		addGroup( new String[]{ "ボ", "ぼ", "ﾎﾞ" } );
		addGroup( new String[]{ "パ", "ぱ", "ﾊﾟ" } );
		addGroup( new String[]{ "ピ", "ぴ", "ﾋﾟ" } );
		addGroup( new String[]{ "プ", "ぷ", "ﾌﾟ" } );
		addGroup( new String[]{ "ペ", "ぺ", "ﾍﾟ" } );
		addGroup( new String[]{ "ポ", "ぽ", "ﾎﾟ" } );
		addGroup( new String[]{ "ヴ", "う゛", "ｳﾞ" } );

		addGroup( new String[]{ "あ", "ｱ", "ア", "ぁ", "ｧ", "ァ" } );
		addGroup( new String[]{ "い", "ｲ", "イ", "ぃ", "ｨ", "ィ" } );
		addGroup( new String[]{ "う", "ｳ", "ウ", "ぅ", "ｩ", "ゥ" } );
		addGroup( new String[]{ "え", "ｴ", "エ", "ぇ", "ｪ", "ェ" } );
		addGroup( new String[]{ "お", "ｵ", "オ", "ぉ", "ｫ", "ォ" } );
		addGroup( new String[]{ "か", "ｶ", "カ" } );
		addGroup( new String[]{ "き", "ｷ", "キ" } );
		addGroup( new String[]{ "く", "ｸ", "ク" } );
		addGroup( new String[]{ "け", "ｹ", "ケ" } );
		addGroup( new String[]{ "こ", "ｺ", "コ" } );
		addGroup( new String[]{ "さ", "ｻ", "サ" } );
		addGroup( new String[]{ "し", "ｼ", "シ" } );
		addGroup( new String[]{ "す", "ｽ", "ス" } );
		addGroup( new String[]{ "せ", "ｾ", "セ" } );
		addGroup( new String[]{ "そ", "ｿ", "ソ" } );
		addGroup( new String[]{ "た", "ﾀ", "タ" } );
		addGroup( new String[]{ "ち", "ﾁ", "チ" } );
		addGroup( new String[]{ "つ", "ﾂ", "ツ", "っ", "ｯ", "ッ" } );
		addGroup( new String[]{ "て", "ﾃ", "テ" } );
		addGroup( new String[]{ "と", "ﾄ", "ト" } );
		addGroup( new String[]{ "な", "ﾅ", "ナ" } );
		addGroup( new String[]{ "に", "ﾆ", "ニ" } );
		addGroup( new String[]{ "ぬ", "ﾇ", "ヌ" } );
		addGroup( new String[]{ "ね", "ﾈ", "ネ" } );
		addGroup( new String[]{ "の", "ﾉ", "ノ" } );
		addGroup( new String[]{ "は", "ﾊ", "ハ" } );
		addGroup( new String[]{ "ひ", "ﾋ", "ヒ" } );
		addGroup( new String[]{ "ふ", "ﾌ", "フ" } );
		addGroup( new String[]{ "へ", "ﾍ", "ヘ" } );
		addGroup( new String[]{ "ほ", "ﾎ", "ホ" } );
		addGroup( new String[]{ "ま", "ﾏ", "マ" } );
		addGroup( new String[]{ "み", "ﾐ", "ミ" } );
		addGroup( new String[]{ "む", "ﾑ", "ム" } );
		addGroup( new String[]{ "め", "ﾒ", "メ" } );
		addGroup( new String[]{ "も", "ﾓ", "モ" } );
		addGroup( new String[]{ "や", "ﾔ", "ヤ", "ゃ", "ｬ", "ャ" } );
		addGroup( new String[]{ "ゆ", "ﾕ", "ユ", "ゅ", "ｭ", "ュ" } );
		addGroup( new String[]{ "よ", "ﾖ", "ヨ", "ょ", "ｮ", "ョ" } );
		addGroup( new String[]{ "ら", "ﾗ", "ラ" } );
		addGroup( new String[]{ "り", "ﾘ", "リ" } );
		addGroup( new String[]{ "る", "ﾙ", "ル" } );
		addGroup( new String[]{ "れ", "ﾚ", "レ" } );
		addGroup( new String[]{ "ろ", "ﾛ", "ロ" } );
		addGroup( new String[]{ "わ", "ﾜ", "ワ" } );
		addGroup( new String[]{ "を", "ｦ", "ヲ" } );
		addGroup( new String[]{ "ん", "ﾝ", "ン" } );
	}
}
