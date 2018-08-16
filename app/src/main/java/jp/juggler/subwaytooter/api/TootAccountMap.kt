package jp.juggler.subwaytooter.api

import jp.juggler.subwaytooter.api.entity.ServiceType
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootAccountRef
import java.util.concurrent.ConcurrentHashMap

object TootAccountMap{
	
	private class AccountUniqueKey(parser : TootParser, who : TootAccount) : Comparable<AccountUniqueKey>{
		val acct :String
		val watcher: String
		
		init{
			this.acct = parser.linkHelper.getFullAcct(who.acct)
			
			this.watcher =when(parser.serviceType){
				ServiceType.MASTODON -> requireNotNull(parser.linkHelper.host)
				ServiceType.TOOTSEARCH -> "?tootsearch"
				ServiceType.MSP -> "?msp"
				ServiceType.MISSKEY -> "?misskey"
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
			val i1 = acct.compareTo(other.acct)
			if(i1 != 0) return i1
			return watcher.compareTo(other.watcher)
		}
		
		override fun hashCode() : Int {
			val x = 961L + acct.hashCode().toLong() * 31L + watcher.hashCode()
			return (x and 0x7fffffffL).toInt()
		}
	}
	
	private val accountMap = ConcurrentHashMap<Int,TootAccount>()
	private var serialSeed = Integer.MIN_VALUE
	private val accountEnum = HashMap<AccountUniqueKey,Int>()
	
	fun find(id : Int) : TootAccount {
		return requireNotNull(accountMap[id])
	}
	fun find(ref: TootAccountRef) : TootAccount {
		return requireNotNull(accountMap[ref.id])
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