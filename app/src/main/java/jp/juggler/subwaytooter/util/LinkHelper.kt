package jp.juggler.subwaytooter.util

import jp.juggler.subwaytooter.table.AcctColor

interface LinkHelper {
	
	// SavedAccountのロード時にhostを供給する必要があった
	val host : String?
		get() = null
	
	fun findAcctColor(url : String?) : AcctColor? = null
	
	fun getFullAcct(acct : String?) : String {
		return when {
			acct == null -> "?@?"
			acct.contains('@') -> acct
			else -> "$acct@$host"
		}
	}
}
