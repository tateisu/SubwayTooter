package jp.juggler.subwaytooter.util

import java.math.BigInteger
import java.util.ArrayList

class VersionString(src : String?) {
	
	private val src : String
	
	private val node_list = ArrayList<Any>()
	
	val isEmpty : Boolean
		get() = node_list.isEmpty()
	
	override fun toString() : String {
		return src
	}
	
	init {
		this.src = src ?: ""
		if( src != null && src.isNotEmpty() ){
			val end = src.length
			var next = 0
			while(next < end) {
				var c = src[next]
				
				if(isDelimiter(c)) {
					// 先頭の区切り文字を無視する
					++ next
				} else if(Character.isDigit(c)) {
					// 数字列のノード
					val start = next ++
					while(next < end && Character.isDigit(src[next])) ++ next
					val value = BigInteger(src.substring(start, next))
					node_list.add(value)
				} else {
					// 区切り文字と数字以外の文字が並ぶノード
					val start = next ++
					while(next < end) {
						c = src[next]
						if(isDelimiter(c)) break
						if(Character.isDigit(c)) break
						++ next
					}
					val value = src.substring(start, next)
					node_list.add(value)
				}
			}
		}
	}
	
	companion object {

		// private val warning = new LogCategory( "VersionString" )
		
		private fun isDelimiter(c : Char) : Boolean {
			return c == '.' || c == ' '
		}
		
		// return -1 if a<b , return 1 if a>b , return 0 if a==b
		fun compare(a : VersionString, b : VersionString) : Int {
			
			var idx = 0
			while(true) {
				val ao = if(idx >= a.node_list.size) null else a.node_list[idx]
				val bo = if(idx >= b.node_list.size) null else b.node_list[idx]
				if(ao == null) {
					// 1.0 < 1.0.n
					// 1.0 < 1.0 xxx
					return if(bo == null) 0 else - 1
				} else if(bo == null) {
					// 1.0.n > 1.0
					// 1.0 xxx > 1.0
					return 1
				}
				
				return if(ao is BigInteger) {
					if(bo is BigInteger) {
						// 数字同士の場合
						val i = ao.compareTo(bo)
						if(i == 0) {
							++ idx
							continue
						}else {
							i
						}
					} else {
						// 数字 > 数字以外
						// 1.5.n > 1.5 xxx
						1
					}
				} else if(bo is BigInteger) {
					// 数字以外 < 数字
					// 1.5 xxx < 1.5.n
					- 1
				} else if( ao is String && bo is String ){
					// 文字列どうしは辞書順で比較
					val i =ao.compareTo(bo )
					if(i == 0) {
						++ idx
						continue
					}
					i
				}else{
					throw RuntimeException("node is not string")
				}
			}
		}
	}
	
	
}
