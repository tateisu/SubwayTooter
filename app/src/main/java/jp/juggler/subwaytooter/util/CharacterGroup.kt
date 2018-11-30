package jp.juggler.subwaytooter.util

import android.util.SparseBooleanArray
import android.util.SparseIntArray
import java.util.regex.Pattern

class CharacterGroup {
	
	companion object {
		
		// Tokenizerが終端に達したことを示す
		const val END = - 1
		
		// 文字コードから文字列を作る
		fun c2s(tmp : CharArray, c : Char) : String {
			tmp[0] = c
			return String(tmp, 0, 1)
		}
		
		fun i2s(tmp : CharArray, c : Int) : String {
			tmp[0] = c.toChar()
			return String(tmp, 0, 1)
		}
		
		private val mapWhitespace = SparseBooleanArray().apply {
			intArrayOf(
				0x0009 // HORIZONTAL TABULATION
				, 0x000A // LINE FEED
				, 0x000B // VERTICAL TABULATION
				, 0x000C // FORM FEED
				, 0x000D // CARRIAGE RETURN
				, 0x001C // FILE SEPARATOR
				, 0x001D // GROUP SEPARATOR
				, 0x001E // RECORD SEPARATOR
				, 0x001F // UNIT SEPARATOR
				, 0x0020, 0x0085 // next line (latin-1)
				, 0x00A0 //非区切りスペース
				, 0x1680, 0x180E, 0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006, 0x2007 //非区切りスペース
				, 0x2008, 0x2009, 0x200A, 0x200B, 0x200C, 0x200D, 0x2028 // line separator
				, 0x2029 // paragraph separator
				, 0x202F //非区切りスペース
				, 0x205F, 0x2060, 0x3000, 0x3164, 0xFEFF
			).forEach {
				put(it,true)
			}
		}
		
		// 空白とみなす文字なら真
		fun isWhitespace(cp : Int) : Boolean  = mapWhitespace.get(cp,false)

		internal val reWhitespace =Pattern.compile(
			StringBuilder().apply{
				 append("[\\s\\t\\x0d\\x0a")
				 for(i in 0 until mapWhitespace.size()){
					 val k = mapWhitespace.keyAt(i)
					 if( k > 0x20 ) append(k.toChar())
				 }
				 append("]+")
			 }.toString()
		)
		
		
		// 文字列のリストからグループIDを決定する
		private fun findGroupId(list : Array<String>) : Int {
			// グループのIDは、グループ中の文字(長さ1)のunicode値の最小
			var id = Integer.MAX_VALUE
			for(s in list) {
				if(s.length == 1) {
					val c = s[0].toInt()
					if(c < id) id = c
				}
			}
			if(id == Integer.MAX_VALUE) {
				throw RuntimeException("missing group id")
			}
			return id
		}
	}
	
	// 文字列からグループIDを調べるマップ
	
	// 文字数1: unicode => group_id
	private val map1 = SparseIntArray()
	
	// 文字数2: unicode 二つを合成した数値 => group_id。半角カナ＋濁音など
	private val map2 = SparseIntArray()

	// ユニコード文字を正規化する。
	// 簡易版なので全ての文字には対応していない
	fun getUnifiedCharacter(c:Char):Char{
		val v1 = map1[c.toInt()]
		return if( v1 != 0 ) v1.toChar() else c
	}
	
	// グループをmapに登録する
	private fun addGroup(list : Array<String>) {
		
		val group_id = findGroupId(list)
		
		// 文字列からグループIDを調べるマップを更新
		for(s in list) {
			
			val map : SparseIntArray
			val key : Int
			
			val v1 = s[0].toInt()
			if(s.length == 1) {
				map = map1
				key = v1
			} else {
				map = map2
				val v2 = s[1].toInt()
				key = v1 or (v2 shl 16)
			}
			
			val old = map.get(key)
			if(old != 0 && old != group_id) {
				throw RuntimeException("group conflict: $s")
			}
			map.put(key, group_id)
		}
	}
	
	// 入力された文字列から 文字,グループ,終端 のどれかを順に列挙する
	inner class Tokenizer {
		
		internal var text : CharSequence = ""
		internal var end : Int = 0
		var offset : Int = 0
		
		internal fun reset(text : CharSequence, start : Int, end : Int) : Tokenizer {
			this.text = text
			this.offset = start
			this.end = end
			return this
		}
		
		// returns END or group_id or UTF-16 character
		operator fun next() : Int {
			
			var pos = offset
			
			// 空白を読み飛ばす
			while(pos < end && isWhitespace(text[pos].toInt())) ++ pos
			
			// 終端までの文字数
			val remain = end - pos
			if(remain <= 0) {
				// 空白を読み飛ばしたら終端になった
				// 終端の場合、末尾の空白はoffsetに含めない
				return END
			}
			
			val v1 = text[pos].toInt()
			
			// グループに登録された文字を長い順にチェック
			var check_len = if(remain > 2) 2 else remain
			while(check_len > 0) {
				val group_id = if(check_len == 1)
					map1.get(v1)
				else
					map2.get(v1 or (text[pos + 1].toInt() shl 16))
				if(group_id != 0) {
					this.offset = pos + check_len
					return group_id
				}
				-- check_len
			}
			
			this.offset = pos + 1
			return v1
		}
	}
	
	fun tokenizer() : Tokenizer {
		return Tokenizer()
	}
	
	init {
		val tmp = CharArray(1)
		val array2 = arrayOf("", "")
		val array4 = arrayOf("", "", "", "")
		// 数字
		for(i in 0 .. 8) {
			array2[0] = c2s(tmp, '0' + i)
			array2[1] = c2s(tmp, '０' + i)
			addGroup(array2)
		}
		
		// 英字
		for(i in 0 .. 25) {
			array4[0] = c2s(tmp, 'a' + i)
			array4[1] = c2s(tmp, 'A' + i)
			array4[2] = c2s(tmp, 'ａ' + i)
			array4[3] = c2s(tmp, 'Ａ' + i)
			addGroup(array4)
		}
		
		// ハイフン
		addGroup(
			arrayOf(
				i2s(tmp, 0x002D), // ASCIIのハイフン
				i2s(tmp, 0x30FC), // 全角カナの長音 Shift_JIS由来
				i2s(tmp, 0x2010),
				i2s(tmp, 0x2011),
				i2s(tmp, 0x2013),
				i2s(tmp, 0x2014),
				i2s(tmp, 0x2015), // 全角カナのダッシュ Shift_JIS由来
				i2s(tmp, 0x2212),
				i2s(tmp, 0xFF0d), // 全角カナの長音 MS932由来
				i2s(tmp, 0xFF70) // 半角カナの長音 MS932由来
			)
		)
		
		addGroup(arrayOf("！", "!"))
		addGroup(arrayOf("＂", "\""))
		addGroup(arrayOf("＃", "#"))
		addGroup(arrayOf("＄", "$"))
		addGroup(arrayOf("％", "%"))
		addGroup(arrayOf("＆", "&"))
		addGroup(arrayOf("＇", "'"))
		addGroup(arrayOf("（", "("))
		addGroup(arrayOf("）", ")"))
		addGroup(arrayOf("＊", "*"))
		addGroup(arrayOf("＋", "+"))
		addGroup(arrayOf("，", ",", "、", "､"))
		addGroup(arrayOf("．", ".", "。", "｡"))
		addGroup(arrayOf("／", "/"))
		addGroup(arrayOf("：", ":"))
		addGroup(arrayOf("；", ";"))
		addGroup(arrayOf("＜", "<"))
		addGroup(arrayOf("＝", "="))
		addGroup(arrayOf("＞", ">"))
		addGroup(arrayOf("？", "?"))
		addGroup(arrayOf("＠", "@"))
		addGroup(arrayOf("［", "["))
		addGroup(arrayOf("＼", "\\", "￥"))
		addGroup(arrayOf("］", "]"))
		addGroup(arrayOf("＾", "^"))
		addGroup(arrayOf("＿", "_"))
		addGroup(arrayOf("｀", "`"))
		addGroup(arrayOf("｛", "{"))
		addGroup(arrayOf("｜", "|", "￤"))
		addGroup(arrayOf("｝", "}"))
		
		addGroup(arrayOf("・", "･", "・"))
		addGroup(arrayOf("「", "｢", "「"))
		addGroup(arrayOf("」", "｣", "」"))
		
		// チルダ
		addGroup(arrayOf("~", i2s(tmp, 0x301C), i2s(tmp, 0xFF5E)))
		
		// 半角カナの濁音,半濁音は2文字になる
		addGroup(arrayOf("ガ", "が", "ｶﾞ"))
		addGroup(arrayOf("ギ", "ぎ", "ｷﾞ"))
		addGroup(arrayOf("グ", "ぐ", "ｸﾞ"))
		addGroup(arrayOf("ゲ", "げ", "ｹﾞ"))
		addGroup(arrayOf("ゴ", "ご", "ｺﾞ"))
		addGroup(arrayOf("ザ", "ざ", "ｻﾞ"))
		addGroup(arrayOf("ジ", "じ", "ｼﾞ"))
		addGroup(arrayOf("ズ", "ず", "ｽﾞ"))
		addGroup(arrayOf("ゼ", "ぜ", "ｾﾞ"))
		addGroup(arrayOf("ゾ", "ぞ", "ｿﾞ"))
		addGroup(arrayOf("ダ", "だ", "ﾀﾞ"))
		addGroup(arrayOf("ヂ", "ぢ", "ﾁﾞ"))
		addGroup(arrayOf("ヅ", "づ", "ﾂﾞ"))
		addGroup(arrayOf("デ", "で", "ﾃﾞ"))
		addGroup(arrayOf("ド", "ど", "ﾄﾞ"))
		addGroup(arrayOf("バ", "ば", "ﾊﾞ"))
		addGroup(arrayOf("ビ", "び", "ﾋﾞ"))
		addGroup(arrayOf("ブ", "ぶ", "ﾌﾞ"))
		addGroup(arrayOf("ベ", "べ", "ﾍﾞ"))
		addGroup(arrayOf("ボ", "ぼ", "ﾎﾞ"))
		addGroup(arrayOf("パ", "ぱ", "ﾊﾟ"))
		addGroup(arrayOf("ピ", "ぴ", "ﾋﾟ"))
		addGroup(arrayOf("プ", "ぷ", "ﾌﾟ"))
		addGroup(arrayOf("ペ", "ぺ", "ﾍﾟ"))
		addGroup(arrayOf("ポ", "ぽ", "ﾎﾟ"))
		addGroup(arrayOf("ヴ", "う゛", "ｳﾞ"))
		
		addGroup(arrayOf("あ", "ｱ", "ア", "ぁ", "ｧ", "ァ"))
		addGroup(arrayOf("い", "ｲ", "イ", "ぃ", "ｨ", "ィ"))
		addGroup(arrayOf("う", "ｳ", "ウ", "ぅ", "ｩ", "ゥ"))
		addGroup(arrayOf("え", "ｴ", "エ", "ぇ", "ｪ", "ェ"))
		addGroup(arrayOf("お", "ｵ", "オ", "ぉ", "ｫ", "ォ"))
		addGroup(arrayOf("か", "ｶ", "カ"))
		addGroup(arrayOf("き", "ｷ", "キ"))
		addGroup(arrayOf("く", "ｸ", "ク"))
		addGroup(arrayOf("け", "ｹ", "ケ"))
		addGroup(arrayOf("こ", "ｺ", "コ"))
		addGroup(arrayOf("さ", "ｻ", "サ"))
		addGroup(arrayOf("し", "ｼ", "シ"))
		addGroup(arrayOf("す", "ｽ", "ス"))
		addGroup(arrayOf("せ", "ｾ", "セ"))
		addGroup(arrayOf("そ", "ｿ", "ソ"))
		addGroup(arrayOf("た", "ﾀ", "タ"))
		addGroup(arrayOf("ち", "ﾁ", "チ"))
		addGroup(arrayOf("つ", "ﾂ", "ツ", "っ", "ｯ", "ッ"))
		addGroup(arrayOf("て", "ﾃ", "テ"))
		addGroup(arrayOf("と", "ﾄ", "ト"))
		addGroup(arrayOf("な", "ﾅ", "ナ"))
		addGroup(arrayOf("に", "ﾆ", "ニ"))
		addGroup(arrayOf("ぬ", "ﾇ", "ヌ"))
		addGroup(arrayOf("ね", "ﾈ", "ネ"))
		addGroup(arrayOf("の", "ﾉ", "ノ"))
		addGroup(arrayOf("は", "ﾊ", "ハ"))
		addGroup(arrayOf("ひ", "ﾋ", "ヒ"))
		addGroup(arrayOf("ふ", "ﾌ", "フ"))
		addGroup(arrayOf("へ", "ﾍ", "ヘ"))
		addGroup(arrayOf("ほ", "ﾎ", "ホ"))
		addGroup(arrayOf("ま", "ﾏ", "マ"))
		addGroup(arrayOf("み", "ﾐ", "ミ"))
		addGroup(arrayOf("む", "ﾑ", "ム"))
		addGroup(arrayOf("め", "ﾒ", "メ"))
		addGroup(arrayOf("も", "ﾓ", "モ"))
		addGroup(arrayOf("や", "ﾔ", "ヤ", "ゃ", "ｬ", "ャ"))
		addGroup(arrayOf("ゆ", "ﾕ", "ユ", "ゅ", "ｭ", "ュ"))
		addGroup(arrayOf("よ", "ﾖ", "ヨ", "ょ", "ｮ", "ョ"))
		addGroup(arrayOf("ら", "ﾗ", "ラ"))
		addGroup(arrayOf("り", "ﾘ", "リ"))
		addGroup(arrayOf("る", "ﾙ", "ル"))
		addGroup(arrayOf("れ", "ﾚ", "レ"))
		addGroup(arrayOf("ろ", "ﾛ", "ロ"))
		addGroup(arrayOf("わ", "ﾜ", "ワ"))
		addGroup(arrayOf("を", "ｦ", "ヲ"))
		addGroup(arrayOf("ん", "ﾝ", "ン"))
	}
}
