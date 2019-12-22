package jp.juggler.subwaytooter.util

import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.util.groupEx
import jp.juggler.util.notEmpty
import java.util.regex.Pattern

interface LinkHelper {
	
	// SavedAccountのロード時にhostを供給する必要があった
	val host : String?
	
	//	fun findAcct(url : String?) : String? = null
	//	fun colorFromAcct(acct : String?) : AcctColor? = null
	
	// user とか user@host とかを user@host に変換する
	// nullや空文字列なら ?@? を返す
	fun getFullAcct(acct : String?) : String {
		return when {
			acct?.isEmpty() != false -> "?@?"
			acct.contains('@') -> acct
			else -> "$acct@$host"
		}
	}
	
	val misskeyVersion : Int
		get() = 0
	
	val isMisskey : Boolean
		get() = misskeyVersion > 0
	
	// FIXME もし将来別のサービスにも対応するなら、ここは書き直す必要がある
	val isMastodon : Boolean
		get() = misskeyVersion <= 0
	
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

// user や user@host から user@host を返す
fun getFullAcctOrNull(
	linkHelper : LinkHelper?,
	rawAcct : String,
	url : String
) : String? {
	
	// 既にFull Acctだった
	if(rawAcct.contains('@')) return rawAcct
	
	// URLが既知のパターンだった
	val fullAcct = TootAccount.getAcctFromUrl(url)
	if(fullAcct != null) return fullAcct

	// URLのホスト名部分を補う
	val m = TootAccount.reUrlHost.matcher(url)
	if(m.find()) return "${rawAcct}@${m.groupEx(1)}"
	
	// https://fedibird.com/@noellabo/103350050191159092
	// に含まれるメンションををリモートから見るとmentions メタデータがない。
	// この場合アクセス元のホストを補うのは誤りなのだが、他の方法で解決できないなら仕方ない…
	if(linkHelper?.host?.endsWith('?') == false)
		return "$rawAcct@${linkHelper.host}"
	
	return null
}

// @user や @user@host から user@host を返す
fun getFullAcctFromMention(
	linkHelper : LinkHelper?,
	text : String,
	url : String
) = when {
	text.startsWith('@') -> getFullAcctOrNull(linkHelper, text.substring(1), url)
	else -> null
}

fun String.splitFullAcct() : Pair<String, String?> = when(val delimiter = indexOf('@')) {
	- 1 -> Pair(this, null)
	else -> Pair(substring(0, delimiter), substring(delimiter + 1).notEmpty())
}
