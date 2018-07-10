package jp.juggler.subwaytooter.util

import android.support.v4.util.SparseArrayCompat

import java.util.ArrayList

class WordTrieTree {
	
	companion object {
		
		private val grouper = CharacterGroup()
		
		val EMPTY_VALIDATOR = { _ : CharSequence, _ : Int, _ : Int -> true }
		
		// マストドン2.4.3rc2でキーワードフィルタは単語の前後に 正規表現 \b を仮定するようになった
		// Trie木でマッチ候補が出たらマッチ範囲と前後の文字で単語区切りを検証する
		val WORD_VALIDATOR = { sequence : CharSequence, start : Int, end : Int ->
			
			// 文字種を正規化してから正規表現の単語構成文字 \w [A-Za-z0-9_] にマッチするか調べる
			// 全角半角大文字小文字の違いは吸収されるが、英字数字アンダーバー以外にはマッチしない
			fun isWordCharacter(c : Char) : Boolean {
				val uc = grouper.getUnifiedCharacter(c)
				return when {
					'A' <= uc && uc <= 'Z' -> true
					'a' <= uc && uc <= 'z' -> true
					'0' <= uc && uc <= '9' -> true
					uc == '_' -> true
					else -> false
				}
			}
			
			when {
			// マッチ範囲の始端とその直前がともに単語構成文字だった場合、\bを満たさない
				isWordCharacter(sequence[start])
					&& start > 0
					&& isWordCharacter(sequence[start - 1]) -> false
			
			// マッチ範囲の終端とその直後がともに単語構成文字だった場合、\bを満たさない
				isWordCharacter(sequence[end - 1])
					&& end < sequence.length
					&& isWordCharacter(sequence[end]) -> false
				
				else -> true
			}
		}
	}
	
	private class Node {
		
		// 続くノード
		internal val child_nodes = SparseArrayCompat<Node>()
		
		// このノードが終端なら、マッチした単語の元の表記がある
		internal var match_word : String? = null
		
		// Trieツリー的には終端単語と続くノードの両方が存在する場合がありうる。
		// たとえば ABC と ABCDEF を登録してから ABCDEFG を探索したら、単語 ABC と単語 ABCDEF にマッチする。
		
		// このノードが終端なら、単語マッチの有無を覚えておく
		internal var validator : (src : CharSequence, start : Int, end : Int) -> Boolean =
			EMPTY_VALIDATOR
	}
	
	private val node_root = Node()
	
	val isEmpty : Boolean
		get() = node_root.child_nodes.size() == 0
	
	// 単語の追加
	fun add(
		s : String,
		validator : (src : CharSequence, start : Int, end : Int) -> Boolean = EMPTY_VALIDATOR
	) {
		val t = grouper.tokenizer().reset(s, 0, s.length)
		
		var token_count = 0
		var node = node_root
		while(true) {
			
			val id = t.next()
			if(id == CharacterGroup.END) {
				
				// 単語を正規化したら長さ0だった場合、その単語は無視する
				if(token_count == 0) return
				
				// より長いマッチ単語を覚えておく
				val old_word = node.match_word
				if(old_word == null || old_word.length < t.text.length) {
					node.match_word = t.text.toString()
					node.validator = validator
				}
				
				return
			}
			
			++ token_count
			var child : Node? = node.child_nodes.get(id)
			if(child == null) {
				child = Node()
				node.child_nodes.put(id, child)
			}
			node = child
		}
	}
	
	// マッチ結果
	class Match internal constructor(val start : Int, val end : Int, val word : String)
	
	// Tokenizer が列挙する文字を使って Trie Tree を探索する
	private fun match(
		allowShortMatch : Boolean,
		t : CharacterGroup.Tokenizer
	) : Match? {
		
		val start = t.offset
		var dst : Match? = null
		
		var node = node_root
		while(true) {
			
			// match_wordが定義されたノードは単語の終端を示す
			val match_word = node.match_word
			// マッチ候補はvalidatorで単語区切りなどの検査を行う
			if(match_word != null
				&& node.validator(t.text, start, t.offset)
			) {
				
				// マッチしたことを覚えておく
				dst = Match(start, t.offset, match_word)
				
				// ミュート用途の場合、ひとつでも単語にマッチすればより長い探索は必要ない
				if(allowShortMatch) break
				
				// それ以外の場合は最長マッチを探索する
			}
			
			val id = t.next()
			if(id == CharacterGroup.END) break
			val child = node.child_nodes.get(id) ?: break
			node = child
		}
		return dst
	}
	
	// ミュート用。マッチするかどうかだけを調べる
	fun matchShort(src : CharSequence?) : Boolean {
		return null != src && null != matchShort(src, 0, src.length)
	}
	
	private fun matchShort(src : CharSequence, start : Int, end : Int) : Match? {
		
		val t = grouper.tokenizer()
		
		for(i in start until end) {
			if(! CharacterGroup.isWhitespace(src[i].toInt())) {
				val item = match(true, t.reset(src, i, end))
				if(item != null) return item
			}
		}
		return null
	}
	
	// ハイライト用。複数マッチする。マッチした位置を覚える
	fun matchList(src : CharSequence?) : ArrayList<Match>? {
		return if(src == null) null else matchList(src, 0, src.length)
	}
	
	// ハイライト用。複数マッチする。マッチした位置を覚える
	fun matchList(src : CharSequence, start : Int, end : Int) : ArrayList<Match>? {
		
		var dst : ArrayList<Match>? = null
		
		val t = grouper.tokenizer()
		
		var i = start
		while(i < end) {
			if(! CharacterGroup.isWhitespace(src[i].toInt())) {
				val item = match(false, t.reset(src, i, end))
				if(item != null) {
					if(dst == null) dst = ArrayList()
					dst.add(item)
					i = item.end
					continue
				}
			}
			++ i
		}
		
		return dst
	}
	
}
