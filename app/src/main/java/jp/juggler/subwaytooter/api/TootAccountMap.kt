package jp.juggler.subwaytooter.api

import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.ServiceType
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootAccountRef
import jp.juggler.util.notZero
import java.util.concurrent.ConcurrentHashMap

object TootAccountMap{
	
	private class AccountUniqueKey(parser : TootParser, who : TootAccount) : Comparable<AccountUniqueKey>{
		val acct : Acct
		val watcher: String
		
		init{
			this.acct = parser.getFullAcct(who.acct)
			
			this.watcher =when(parser.serviceType){
				ServiceType.MASTODON -> requireNotNull(parser.accessHost?.ascii)
				ServiceType.MISSKEY -> requireNotNull(parser.accessHost?.ascii)
				ServiceType.TOOTSEARCH -> "?tootsearch"
				ServiceType.MSP -> "?msp"
			}
		}
		
		override fun toString() = "[$watcher=>$acct]"
		
		override fun equals(other : Any?) = when {
			other !is AccountUniqueKey -> false
			other.acct != acct -> false
			other.watcher != watcher -> false
			else -> true
		}

		override fun compareTo(other : AccountUniqueKey) : Int {
			return acct.compareTo(other.acct).notZero() ?: watcher.compareTo(other.watcher)
		}
		
		override fun hashCode() : Int {
			val x = acct.hashCode().toLong() * 31L + watcher.hashCode().toLong() + 961L
			return (x and 0x7fffffffL).toInt()
		}
	}
	
	private val accountMap = ConcurrentHashMap<Int,TootAccount>()
	private var serialSeed = Integer.MIN_VALUE
	private val accountEnum = HashMap<AccountUniqueKey,Int>()
	
	fun find(mapId : Int) : TootAccount {
		return requireNotNull(accountMap[mapId])
	}
	
	fun find(ref: TootAccountRef) : TootAccount {
		return requireNotNull(accountMap[ref.mapId])
	}
	
	fun register(parser:TootParser, who : TootAccount) : Int {
		val key = AccountUniqueKey(parser,who)
		val id =synchronized(accountEnum) {
			var x = accountEnum[key]
			if( x == null ){
				x = ++ serialSeed
				accountEnum[key]=x
				x
			}else {
				x
			}
		}
		accountMap[id] = who
		return id
	}
}