package jp.juggler.subwaytooter.util

import jp.juggler.subwaytooter.table.AcctColor

// 派生クラスがあるのでラムダ式に変換してはいけない
interface LinkClickContext {
	
	fun findAcctColor(url : String?) : AcctColor?  = null
	
	// SavedAccountのロード時にhostを供給する必要があった
	val host : String?
		get() = null
}
