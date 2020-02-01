package jp.juggler.subwaytooter.util

import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.util.groupEx
import jp.juggler.util.notEmpty
import java.net.IDN

interface LinkHelper {
	
	// SavedAccountのロード時にhostを供給する必要があった
	val hostAscii : String? // punycode
	val hostPretty : String? // unicode
	
	//	fun findAcct(url : String?) : String? = null
	//	fun colorFromAcct(acct : String?) : AcctColor? = null
	
	// user とか user@host とかを user@host に変換する
	// nullや空文字列なら ?@? を返す
	fun getFullAcct(acctAscii : String?) : String = when {
		acctAscii?.isEmpty() != false -> "?@?"
		acctAscii.contains('@') -> acctAscii
		else -> "$acctAscii@$hostAscii"
	}
	
	// user とか user@host とかを user@host に変換する
	// nullや空文字列なら ?@? を返す
	fun getFullPrettyAcct(acctPretty : String?) : String = when {
		acctPretty?.isEmpty() != false -> "?@?"
		acctPretty.contains('@') -> acctPretty
		else -> "$acctPretty@$hostPretty"
	}
	
	val misskeyVersion : Int
		get() = 0
	
	val isMisskey : Boolean
		get() = misskeyVersion > 0
	
	// FIXME もし将来別のサービスにも対応するなら、ここは書き直す必要がある
	val isMastodon : Boolean
		get() = misskeyVersion <= 0
	
	fun matchHost(host : String?) : Boolean =
		host != null && (
			host.equals(hostAscii, ignoreCase = true) ||
				host.equals(hostPretty, ignoreCase = true)
			)
	
	companion object {
		
		fun newLinkHelper(hostArg : String?, misskeyVersion : Int = 0) = object : LinkHelper {
			
			override val hostAscii : String? =
				hostArg?.let { IDN.toASCII(hostArg, IDN.ALLOW_UNASSIGNED) }
			override val hostPretty : String? =
				hostArg?.let { IDN.toUnicode(hostArg, IDN.ALLOW_UNASSIGNED) }
			
			override val misskeyVersion : Int
				get() = misskeyVersion
		}
		
		val nullHost = object : LinkHelper {
			override val hostAscii : String? = null
			override val hostPretty : String? = null
		}
	}
}

// user や user@host から user@host を返す
// ただし host部分がpunycodeかunicodeかは分からない
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
	if(linkHelper?.hostAscii?.endsWith('?') == false)
		return "$rawAcct@${linkHelper.hostAscii}"
	
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
