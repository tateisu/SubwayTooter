package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.notZero
import java.net.IDN
import java.util.concurrent.ConcurrentHashMap

private val reUrlSchema = """\A\w+://""".toRegex()

fun String.removeUrlSchema() = replace(reUrlSchema, "")

class Host private constructor(
    val ascii: String,
    val pretty: String = ascii
) : Comparable<Host> {

    override fun toString(): String = ascii

    override fun hashCode(): Int =
        ascii.hashCode()

    override fun equals(other: Any?): Boolean =
        if (other is Host) ascii == other.ascii else false

    // ソートはprettyのコード順
    override fun compareTo(other: Host): Int =
        pretty.compareTo(other.pretty).notZero() ?: ascii.compareTo(other.ascii)

    fun match(src: String?): Boolean =
        ascii == src || pretty == src

    val isValid: Boolean
        get() = ascii.isNotEmpty() && ascii != "?"

    fun valid(): Host? = if (isValid) this else null

    companion object {
        // declare this first!
        private val hostSet = ConcurrentHashMap<String, Host>()


        val EMPTY = Host("")
        val UNKNOWN = Host("?")
        val FRIENDS_NICO = Host("friends.nico")

        fun parse(srcArg: String): Host {
            val cached = hostSet[srcArg]
            if (cached != null) return cached
            val src = srcArg.removeUrlSchema()
            val ascii = IDN.toASCII(src, IDN.ALLOW_UNASSIGNED).lowercase()
            val pretty = IDN.toUnicode(src, IDN.ALLOW_UNASSIGNED)
            val host = if (ascii == pretty) Host(ascii) else Host(ascii, pretty)
            hostSet[src] = host
            return host
        }
    }
}

class Acct private constructor(
    val username: String,
    val host: Host?
) : Comparable<Acct> {

    val ascii: String = if (host == null) username else "$username@${host.ascii}"
    val pretty: String = if (host == null) username else "$username@${host.pretty}"

    override fun toString(): String = ascii

    override fun hashCode(): Int = ascii.hashCode()

    override fun equals(other: Any?): Boolean =
        if (other is Acct) ascii == other.ascii else false

    // ソートはprettyのコード順
    override fun compareTo(other: Acct): Int {
        return pretty.compareTo(other.pretty).notZero() ?: ascii.compareTo(other.ascii)
    }

    fun followHost(accessHost: Host?): Acct {
        return if (this.host != null) this else Acct(username, accessHost)
    }

    val isValid: Boolean
        get() = username.isNotEmpty() && username != "?" && (host?.isValid != false)

    @Suppress("unused")
    private fun valid(): Acct? = if (isValid) this else null

    val isValidFull: Boolean
        get() = username.isNotEmpty() && username != "?" && (host?.isValid == true)

    fun validFull(): Acct? = if (isValidFull) this else null


    companion object {
        // declare this first!
        private val acctSet = ConcurrentHashMap<String, Acct>()

        val UNKNOWN = Acct("?", Host.UNKNOWN)

        fun parse(src: String): Acct {
            val cached = acctSet[src]
            if (cached != null) return cached

            if (src.isEmpty()) return UNKNOWN

            val pos = src.indexOf('@')
            val acct = if (pos != -1)
                Acct(src.substring(0, pos), Host.parse(src.substring(pos + 1)))
            else
                Acct(src, null)

            acctSet[src] = acct
            return acct
        }

        fun parse(user: String, host: String?) =
            if (host != null) parse("$user@$host") else parse(user)

        fun parse(user: String, host: Host?) = Acct(user, host)
    }
}

interface HostAndDomain {
    val apiHost: Host
    val apDomain: Host
}

val unknownHostAndDomain = object : HostAndDomain {
    override val apiHost = Host.UNKNOWN
    override val apDomain = Host.UNKNOWN
}
