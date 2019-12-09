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
	
	val misskeyVersion : Int
		get() = 0
	
	val isMisskey :Boolean
		get() = misskeyVersion > 0
	
	// FIXME もし将来別のサービスにも対応するなら、ここは書き直す必要がある
	val isMastodon :Boolean
		get() = misskeyVersion <=0
	
	companion object {
		
		fun newLinkHelper(host : String?, misskeyVersion : Int = 0) = object : LinkHelper {

			override val host : String?
				get() = host

			override val misskeyVersion : Int
				get() = misskeyVersion
		}
		
		val nullHost = object : LinkHelper {

			override val host : String?
				get() = null
		}
	}
}
