package jp.juggler.subwaytooter.util

import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.util.data.findOrNull
import jp.juggler.util.data.groupEx

interface LinkHelper : HostAndDomain {

    // SavedAccountのロード時にhostを供給する必要があった
    override val apiHost: Host
    //	fun findAcct(url : String?) : String? = null
    //	fun colorFromAcct(acct : String?) : AcctColor? = null

    override val apDomain: Host

    val misskeyVersion: Int
        get() = 0

    val isMisskey: Boolean
        get() = misskeyVersion > 0

    // FIXME もし将来別のサービスにも対応するなら、ここは書き直す必要がある
    val isMastodon: Boolean
        get() = misskeyVersion <= 0

    // user とか user@host とかを user@host に変換する
    // nullや空文字列なら ?@? を返す
    fun getFullAcct(src: Acct?): Acct = when {
        src?.username?.isEmpty() != false -> Acct.UNKNOWN
        src.host?.isValid == true -> src
        else -> src.followHost(apDomain.valid() ?: apiHost.valid() ?: Host.UNKNOWN)
    }

    companion object {

        val unknown = object : LinkHelper {
            override val apiHost: Host = Host.UNKNOWN
            override val apDomain: Host = Host.UNKNOWN
        }

        fun create(ti: TootInstance) = create(
            apiHostArg = ti.apiHost,
            apDomainArg = ti.apDomain,
            misskeyVersion = ti.misskeyVersionMajor
        )

        fun create(apiHostArg: Host, apDomainArg: Host? = null, misskeyVersion: Int = 0) =
            object : LinkHelper {

                override val apiHost: Host = apiHostArg
                //	fun findAcct(url : String?) : String? = null
                //	fun colorFromAcct(acct : String?) : AcctColor? = null

                override val apDomain: Host = apDomainArg ?: apiHostArg

                override val misskeyVersion: Int
                    get() = misskeyVersion
            }
    }
}

fun LinkHelper.matchHost(src: String?) = apiHost.match(src) || apDomain.match(src)
fun LinkHelper.matchHost(src: Host?) = apiHost == src || apDomain == src
fun LinkHelper.matchHost(src: LinkHelper) =
    apiHost == src.apiHost || apDomain == src.apDomain ||
            apDomain == src.apiHost || apiHost == src.apDomain

fun LinkHelper.matchHost(src: TootAccount) =
    apiHost == src.apiHost || apDomain == src.apDomain ||
            apDomain == src.apiHost || apiHost == src.apDomain

fun LinkHelper.matchHost(srcApiHost: Host, srcApDomain: Host) =
    apiHost == srcApiHost ||
            apDomain == srcApDomain ||
            apDomain == srcApiHost ||
            apiHost == srcApDomain

// user や user@host から user@host を返す
fun getFullAcctOrNull(
    rawAcct: Acct,
    url: String?,
    hostDomain1: HostAndDomain? = null,
    hostDomain2: HostAndDomain? = null,
): Acct? {
    // 最初から有効なfull acctがあればそれを使う
    if (rawAcct.isValidFull) return rawAcct

    // (MFMのみ)URLがなければ引数から適当に補う
    if (url == null) {
        return (hostDomain1?.apDomain?.valid()
            ?: hostDomain2?.apDomain?.valid())
            ?.let { Acct.parse(rawAcct.username, it) }
    }

    var host = TootAccount.reHostInUrl.matcher(url).findOrNull()
        ?.groupEx(1)?.let { Host.parse(it).valid() }
    // URL中のホスト名が引数で指定されたドメインなら、APIホストとAPドメインの変換を行う
    when (host) {
        null -> return null
        hostDomain1?.apiHost -> host = hostDomain1.apDomain
        hostDomain2?.apiHost -> host = hostDomain2.apDomain
    }
    return Acct.parse(rawAcct.username, host).validFull()
}
