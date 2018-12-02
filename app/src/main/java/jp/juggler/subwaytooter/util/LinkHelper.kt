package jp.juggler.subwaytooter.util

import jp.juggler.subwaytooter.table.AcctColor

interface LinkHelper {
	
	// SavedAccountのロード時にhostを供給する必要があった
	val host : String?
	
	fun findAcctColor(url : String?) : AcctColor? = null
	
	// user とか user@host とかを user@host に変換する
	// nullや空文字列なら ?@? を返す
	fun getFullAcct(acct : String?) : String {
		return when {
			acct?.isEmpty() !=false -> "?@?"
			acct.contains('@') -> acct
			else -> "$acct@$host"
		}
	}
	
	val isMisskey : Boolean
		get() = false
	
	companion object {
		
		fun newLinkHelper(host : String?, isMisskey : Boolean = false) = object : LinkHelper {

			override val host : String?
				get() = host

			override val isMisskey : Boolean
				get() = isMisskey
		}
		
		val nullHost = object : LinkHelper {

			override val host : String?
				get() = null
		}
	}
}
