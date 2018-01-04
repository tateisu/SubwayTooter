package jp.juggler.subwaytooter.util

import android.support.v4.util.SparseArrayCompat

import java.util.ArrayList

class WordTrieTree {
	
	companion object {
		
		private val grouper = CharacterGroup()
	}
	
	private class Node {
		
		// 続くノード
		internal val child_nodes = SparseArrayCompat<Node>()
		
		// このノードが終端なら、マッチした単語の元の表記がある
		internal var match_word : String? = null
		
		// Trieツリー的には終端単語と続くノードの両方が存在する場合がありうる。
		// たとえば ABC と ABCDEF を登録してから ABCDEFG を探索したら、単語 ABC と単語 ABCDEF にマッチする。
	}
	
	private val node_root = Node()
	
	val isEmpty :Boolean
		get() = node_root.child_nodes.size() == 0
	
	// 単語の追加
	fun add(s : String) {
		val t = grouper.tokenizer().reset( s,0,s.length)
		
		var token_count = 0
		var node = node_root
		while(true) {
			
			val id = t.next()
			if(id == CharacterGroup.END) {
				
				// 単語を正規化したら長さ0だった場合、その単語は無視する
				if(token_count == 0) return
				
				// より長いマッチ単語を覚えておく
				val old_word = node.match_word
				if(old_word == null ||old_word.length < t.text.length) {
					node.match_word = t.text.toString()
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
	private fun match(allowShortMatch : Boolean, t : CharacterGroup.Tokenizer) : Match? {
		
		val start = t.offset
		var dst : Match? = null
		
		var node = node_root
		while(true) {
			
			// このノードは単語の終端でもある
			val match_word = node.match_word
			if(match_word != null) {
				dst = Match(start, t.offset, match_word)
				// ミュート用途の場合、ひとつでも単語にマッチすればより長い探索は必要ない
				if(allowShortMatch) break
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
	internal fun matchList(src : CharSequence, start : Int, end : Int) : ArrayList<Match>? {
		
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
