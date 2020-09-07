package jp.juggler.subwaytooter.util

import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.groupEx

interface LinkHelper {
	
	// SavedAccountのロード時にhostを供給する必要があった
	val apiHost : Host
	//	fun findAcct(url : String?) : String? = null
	//	fun colorFromAcct(acct : String?) : AcctColor? = null
	
	val apDomain : Host
	
	val misskeyVersion : Int
		get() = 0
	
	val isMisskey : Boolean
		get() = misskeyVersion > 0
	
	// FIXME もし将来別のサービスにも対応するなら、ここは書き直す必要がある
	val isMastodon : Boolean
		get() = misskeyVersion <= 0
	
	fun matchHost(src : String?) = apiHost.match(src) || apDomain.match(src)
	fun matchHost(src : Host?) = apiHost == src || apDomain == src
	
	// user とか user@host とかを user@host に変換する
	// nullや空文字列なら ?@? を返す
	fun getFullAcct(src : Acct?) : Acct = when {
		src?.username?.isEmpty() != false -> Acct.UNKNOWN
		src.host?.isValid == true -> src
		else -> src.followHost(apDomain.valid() ?: apiHost.valid() ?: Host.UNKNOWN)
	}
	
	companion object {
		
		fun newLinkHelper(apiHostArg : Host, apDomainArg : Host? = null, misskeyVersion : Int = 0) =
			object : LinkHelper {
				
				override val apiHost : Host = apiHostArg
				//	fun findAcct(url : String?) : String? = null
				//	fun colorFromAcct(acct : String?) : AcctColor? = null
				
				override val apDomain : Host = apDomainArg ?: apiHostArg
				
				override val misskeyVersion : Int
					get() = misskeyVersion
			}
		
		val nullHost = object : LinkHelper {
			override val apiHost : Host = Host.parse("")
			override val apDomain : Host = Host.parse("")
		}
	}
}

// user や user@host から user@host を返す
fun getFullAcctOrNull(
	linkHelper : LinkHelper?,
	src : String,
	url : String
) : Acct? {
	
	// 既にFull Acctだった
	if(src.contains('@'))
		return Acct.parse(src)
	
	// トゥート検索等でないならアクセス元ホストを補って良いはず
	if(linkHelper is SavedAccount && ! linkHelper.isNA) {
		return Acct.parse(src, linkHelper.apDomain)
	}
	
	// URLが既知のパターンだった
	val fullAcct = TootAccount.getAcctFromUrl(url)
	if(fullAcct != null) return fullAcct
	
	// URLのホスト名部分を補う
	val m = TootAccount.reHostInUrl.matcher(url)
	if(m.find()) return Acct.parse(src, m.groupEx(1))
	
	// https://fedibird.com/@noellabo/103350050191159092
	// に含まれるメンションををリモートから見るとmentions メタデータがない。
	// この場合アクセス元のホストを補うのは誤りなのだが、他の方法で解決できないなら仕方ない…
	val apDomain = linkHelper?.apDomain
	if(apDomain?.isValid == true) return Acct.parse(src, apDomain)
	
	return null
}

// user や user@host から user@host を返す
fun getFullAcctOrNull(
	linkHelper : LinkHelper?,
	src : Acct,
	url : String
) : Acct? {
	
	// 既にFull Acctだった
	if(src.host != null) return src
	
	val apDomain = linkHelper?.apDomain
	
	// トゥート検索等でないならアクセス元ホストを補って良いはず
	if(linkHelper is SavedAccount && ! linkHelper.isNA && apDomain != null) {
		return Acct.parse(src.username, apDomain)
	}
	
	// URLが既知のパターンだった
	val fullAcct = TootAccount.getAcctFromUrl(url)
	if(fullAcct != null) return fullAcct
	
	// URLのホスト名部分を補う
	val m = TootAccount.reHostInUrl.matcher(url)
	if(m.find()) return src.followHost(Host.parse(m.groupEx(1) !!))
	
	// https://fedibird.com/@noellabo/103350050191159092
	// に含まれるメンションををリモートから見るとmentions メタデータがない。
	// この場合アクセス元のホストを補うのは誤りなのだが、他の方法で解決できないなら仕方ない…
	if(apDomain?.isValid == true) return src.followHost(apDomain)
	
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
