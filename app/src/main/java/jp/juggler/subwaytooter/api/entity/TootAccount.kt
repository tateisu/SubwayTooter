package jp.juggler.subwaytooter.api.entity

import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.widget.TextView
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.MisskeyAccountDetailMap
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.emoji.CustomEmoji
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.subwaytooter.util.NetworkEmojiInvalidator
import jp.juggler.subwaytooter.util.matchHost
import jp.juggler.subwaytooter.view.MyLinkMovementMethod
import jp.juggler.util.*
import java.util.*
import java.util.regex.Pattern

open class TootAccount(parser: TootParser, src: JsonObject) : HostAndDomain {

    //URL of the user's profile page (can be remote)
    // https://mastodon.juggler.jp/@tateisu
    // 疑似アカウントではnullになります
    val url: String?

    //	The ID of the account
    val id: EntityId

    // 	The username of the account  /[A-Za-z0-9_]{1,30}/
    val username: String

    final override val apiHost: Host
    final override val apDomain: Host

    //	Equals username for local users, includes @domain for remote ones
    val acct: Acct

    //	The account's display name
    val display_name: String

    //Boolean for when the account cannot be followed without waiting for approval first
    val locked: Boolean

    //	The time the account was created
    // ex: "2017-04-13T11:06:08.289Z"
    val created_at: String?
    val time_created_at: Long

    //	The number of followers for the account
    var followers_count: Long? = null

    //The number of accounts the given account is following
    var following_count: Long? = null

    //	The number of statuses the account has made
    var statuses_count: Long? = null

    // Biography of user
    // 説明文。改行は\r\n。リンクなどはHTMLタグで書かれている
    val note: String?

    //	URL to the avatar image
    val avatar: String?

    //	URL to the avatar static image (gif)
    val avatar_static: String?

    //URL to the header image
    val header: String?

    //	URL to the header static image (gif)
    val header_static: String?

    val source: Source?

    val profile_emojis: HashMap<String, NicoProfileEmoji>?

    val movedRef: TootAccountRef?

    val moved: TootAccount?
        get() = movedRef?.get()

    class Field(
        val name: String,
        val value: String,
        val verified_at: Long // 0L if not verified
    )

    val fields: ArrayList<Field>?

    val custom_emojis: HashMap<String, CustomEmoji>?

    val bot: Boolean
    val isCat: Boolean
    val isAdmin: Boolean
    val isPro: Boolean

    @Suppress("unused")
    val isLocal: Boolean
        get() = acct.host == null

    val isRemote: Boolean
        get() = acct.host != null

    fun getUserUrl() = url ?: "https://${apDomain.pretty}/@${username}"

    // user_hides_network is preference, not exposed in API
    // val user_hides_network : Boolean

    var pinnedNotes: ArrayList<TootStatus>? = null
    private var pinnedNoteIds: ArrayList<String>? = null

    // misskey (only /api/users/show)
    var location: String? = null
    var birthday: String? = null

    // mastodon 3.0.0-dev // last_status_at : "2019-08-29T12:42:08.838Z" or null
    // mastodon 3.1       // last_status_at : "2019-08-29" or null
    private var last_status_at = 0L

    // mastodon 3.3.0
    var suspended = false


    val json: JsonObject

    init {
        this.json = src
        src["_fromStream"] = parser.fromStream

        when (parser.serviceType) {
            ServiceType.MISSKEY -> {


                this.custom_emojis =
                    parseMapOrNull(CustomEmoji.decodeMisskey,  parser.apDomain, src.jsonArray("emojis"))
                this.profile_emojis = null

                this.username = src.stringOrThrow("username")

                this.apiHost = src.string("host")?.let { Host.parse(it) } ?: parser.apiHost

                this.url = "https://${apiHost.ascii}/@$username"

                this.apDomain = apiHost // FIXME apiHostとapDomainが異なる場合はMisskeyだとどうなの…？

                @Suppress("LeakingThis")
                this.acct = when {
                    // アクセス元から見て内部ユーザなら short acct
                    parser.linkHelper.matchHost(this) -> Acct.parse(username)

                    // アクセス元から見て外部ユーザならfull acct
                    else -> Acct.parse(username, apDomain)
                }

                //
                val sv = src.string("name")
                this.display_name = if (sv?.isNotEmpty() == true) sv.sanitizeBDI() else username

                //
                this.note = src.string("description")

                this.source = null
                this.movedRef = null
                this.locked = src.optBoolean("isLocked")



                this.bot = src.optBoolean("isBot", false)
                this.isCat = src.optBoolean("isCat", false)
                this.isAdmin = src.optBoolean("isAdmin", false)
                this.isPro = src.optBoolean("isPro", false)

                // this.user_hides_network = src.optBoolean("user_hides_network")

                this.id = EntityId.mayDefault(src.string("id"))


                this.followers_count = src.long("followersCount") ?: -1L
                this.following_count = src.long("followingCount") ?: -1L
                this.statuses_count = src.long("notesCount") ?: -1L

                this.created_at = src.string("createdAt")
                this.time_created_at = TootStatus.parseTime(this.created_at)

                // https://github.com/syuilo/misskey/blob/develop/src/client/scripts/get-static-image-url.ts
                fun String.getStaticImageUrl(): String? {
                    val uri = this.mayUri() ?: return null
                    val dummy = "${uri.encodedAuthority}${uri.encodedPath}"
                    return "https://${parser.linkHelper.apiHost.ascii}/proxy/$dummy?url=${encodePercent()}&static=1"
                }

                this.avatar = src.string("avatarUrl")
                this.avatar_static = src.string("avatarUrl")?.getStaticImageUrl()
                this.header = src.string("bannerUrl")
                this.header_static = src.string("bannerUrl")?.getStaticImageUrl()


                this.pinnedNoteIds = src.stringArrayList("pinnedNoteIds")
                if (parser.misskeyDecodeProfilePin) {
                    val list = parseList(::TootStatus, parser, src.jsonArray("pinnedNotes"))
                    list.forEach { it.pinned = true }
                    this.pinnedNotes = if (list.isNotEmpty()) list else null
                }

                val profile = src.jsonObject("profile")
                this.location = profile?.string("location")
                this.birthday = profile?.string("birthday")


                this.fields = parseMisskeyFields(src)


                UserRelation.fromAccount(parser, src, id)

                @Suppress("LeakingThis")
                MisskeyAccountDetailMap.fromAccount(parser, this, id)

            }
            ServiceType.NOTESTOCK -> {

                // notestock はActivityPub 準拠のサービスなので、サーバ内IDというのは特にない
                this.id = EntityId.DEFAULT

                this.username = src.stringOrThrow("display_name") // notestockはdisplay_nameとusernameが入れ替わってる？
                this.display_name = src.stringOrThrow("username")

                val tmpAcct = src.string("subject")?.let { Acct.parse(it) }
                val apDomain = tmpAcct?.takeIf { it.isValidFull }?.host
                    ?: Host.parse(
                        src.string("id").mayUri()?.authority?.notEmpty()
                            ?: error("can't get apDomain from account's AP id.")
                    )
                this.url = src.string("url")
                val apiHost = Host.parse(
                    url.mayUri()?.authority?.notEmpty()
                        ?: error("can't get apiHost from account's AP url.")
                )

                this.apiHost = apiHost
                this.apDomain = apDomain
                this.acct = Acct.parse(this.username, apDomain)


                this.avatar = src.string("avatar")
                this.avatar_static = src.string("avatar_static")
                this.header = src.string("header")
                this.header_static = src.string("header_static")

                this.locked = src.boolean("manuallyApprovesFollowers") ?: false

                this.note = src.string("note")

                val apTag = APTag(parser, src.jsonArray("tag"))
                this.custom_emojis = apTag.emojiList.notEmpty()
                this.profile_emojis = apTag.profileEmojiList.notEmpty()

                // APだと attachment にデータはあるが、検索結果に表示しないので読まない
                this.fields = null

                this.source = null
                this.movedRef = null

                this.followers_count = null
                this.following_count = null
                this.statuses_count = null

                this.created_at = null
                this.time_created_at = 0L

                this.bot = false
                this.isCat = false
                this.isAdmin = false
                this.isPro = false
            }

            else -> {

                // 絵文字データは先に読んでおく
                this.custom_emojis = parseMapOrNull(CustomEmoji.decode,  parser.apDomain, src.jsonArray("emojis"))

                this.profile_emojis = when (val o = src["profile_emojis"]) {
                    is JsonArray -> parseMapOrNull(::NicoProfileEmoji, o, TootStatus.log)
                    is JsonObject -> parseProfileEmoji2(::NicoProfileEmoji, o, TootStatus.log)
                    else -> null
                }

                // 疑似アカウントにacctとusernameだけ
                this.url = src.string("url")
                this.username = src.stringOrThrow("username")

                //
                val sv = src.string("display_name")
                this.display_name = if (sv?.isNotEmpty() == true) sv.sanitizeBDI() else username

                //
                this.note = src.string("note")

                this.source = parseSource(src.jsonObject("source"))
                this.movedRef = TootAccountRef.mayNull(
                    parser,
                    src.jsonObject("moved")?.let {
                        TootAccount(parser, it)
                    }
                )
                this.locked = src.optBoolean("locked")

                this.fields = parseFields(src.jsonArray("fields"))

                this.bot = src.optBoolean("bot", false)
                this.suspended = src.optBoolean("suspended", false)
                this.isAdmin = false
                this.isCat = false
                this.isPro = false
                // this.user_hides_network = src.optBoolean("user_hides_network")

                this.last_status_at = TootStatus.parseTime(src.string("last_status_at"))

                when (parser.serviceType) {
                    ServiceType.MASTODON -> {

                        this.id = EntityId.mayDefault(src.string("id"))

                        val tmpAcct = src.stringOrThrow("acct")

                        val (apiHost, apDomain) = findHostFromUrl(tmpAcct, parser.linkHelper, url)
                        apiHost ?: error("can't get apiHost from acct or url")
                        apDomain ?: error("can't get apDomain from acct or url")
                        this.apiHost = apiHost
                        this.apDomain = apDomain

                        this.acct = Acct.parse(username, if (tmpAcct.contains('@')) apDomain else null)

                        this.followers_count = src.long("followers_count")
                        this.following_count = src.long("following_count")
                        this.statuses_count = src.long("statuses_count")

                        this.created_at = src.string("created_at")
                        this.time_created_at = TootStatus.parseTime(this.created_at)

                        this.avatar = src.string("avatar")
                        this.avatar_static = src.string("avatar_static")
                        this.header = src.string("header")
                        this.header_static = src.string("header_static")

                    }

                    ServiceType.TOOTSEARCH -> {
                        // tootsearch のアカウントのIDはどのタンス上のものか分からないので役に立たない
                        this.id = EntityId.DEFAULT

                        val tmpAcct = src.stringOrThrow("acct")

                        val (apiHost, apDomain) = findHostFromUrl(tmpAcct, null, url)
                        apiHost ?: error("can't get apiHost from acct or url")
                        apDomain ?: error("can't get apDomain from acct or url")
                        this.apiHost = apiHost
                        this.apDomain = apDomain

                        this.acct = Acct.parse(this.username, this.apDomain)

                        this.followers_count = src.long("followers_count")
                        this.following_count = src.long("following_count")
                        this.statuses_count = src.long("statuses_count")

                        this.created_at = src.string("created_at")
                        this.time_created_at = TootStatus.parseTime(this.created_at)

                        this.avatar = src.string("avatar")
                        this.avatar_static = src.string("avatar_static")
                        this.header = src.string("header")
                        this.header_static = src.string("header_static")
                    }


                    ServiceType.MSP -> {
                        this.id = EntityId.mayDefault(src.string("id"))

                        // MSPはLTLの情報しか持ってないのでacctは常にホスト名部分を持たない
                        val (apiHost, apDomain) = findHostFromUrl(null, null, url)
                        apiHost ?: error("can't get apiHost from acct or url")
                        apDomain ?: error("can't get apDomain from acct or url")

                        this.apiHost = apiHost
                        this.apDomain = apiHost

                        this.acct = Acct.parse(this.username, this.apDomain)

                        this.followers_count = null
                        this.following_count = null
                        this.statuses_count = null

                        this.created_at = null
                        this.time_created_at = 0L

                        val avatar = src.string("avatar")
                        this.avatar = avatar
                        this.avatar_static = avatar
                        this.header = null
                        this.header_static = null

                    }

                    ServiceType.MISSKEY, ServiceType.NOTESTOCK -> error("will not happen")
                }
            }
        }
    }

    class Source(src: JsonObject) {

        // デフォルト公開範囲
        val privacy: String?

        // 添付画像をデフォルトでNSFWにする設定
        private val sensitive: Boolean

        // HTMLエンコードされていない、生のnote
        val note: String?

        // 2.4.0 から？
        val fields: ArrayList<Field>?

        init {
            this.privacy = src.string("privacy")
            this.note = src.string("note")
            // nullになることがあるが、falseと同じ扱いでよい
            this.sensitive = src.optBoolean("sensitive", false)
            //
            this.fields = parseFields(src.jsonArray("fields"))
        }
    }

    // リストメンバーダイアログや引っ越し先ユーザなど、TL以外の部分に名前を表示する場合は
    // Invalidator の都合でSpannableを別途生成する必要がある
    fun decodeDisplayName(context: Context): Spannable {

        // remove white spaces
        val sv = reWhitespace.matcher(display_name).replaceAll(" ")

        // decode emoji code
        return DecodeOptions(
            context,
            emojiMapProfile = profile_emojis,
            emojiMapCustom = custom_emojis,
            mentionDefaultHostDomain = this
        ).decodeEmoji(sv)
    }

    private fun SpannableStringBuilder.replaceAllEx(
        pattern: Pattern,
        replacement: String
    ): SpannableStringBuilder {
        val m = pattern.matcher(this)
        var buffer: SpannableStringBuilder? = null
        var lastEnd = 0
        while (m.find()) {
            val dst = buffer ?: SpannableStringBuilder().also { buffer = it }
            dst
                .append(this.subSequence(lastEnd, m.start()))
                .append(replacement) // 変数展開には未対応
            lastEnd = m.end()
        }
        return buffer
            ?.also { if (lastEnd < length) it.append(subSequence(lastEnd, length)) }
            ?: this
    }

    private fun SpannableStringBuilder.trimEx(isSpace: (c: Char) -> Boolean = { it <= ' ' }): CharSequence {
        var start = 0
        var end = length
        while (start < end && isSpace(this[start])) ++start
        while (end > start && isSpace(this[end - 1])) --end
        return when {
            start >= end -> ""
            start == 0 && end == length -> this
            else -> subSequence(start, end)
        }
    }

    fun setAccountExtra(
        accessInfo: SavedAccount,
        tv: TextView,
        invalidator: NetworkEmojiInvalidator?,
        fromProfileHeader: Boolean = false
    ): SpannableStringBuilder? {
        val pref = App1.pref
        val context = tv.context

        var sb: SpannableStringBuilder? = null
        fun prepareSb() = sb?.apply { append('\n') } ?: SpannableStringBuilder().also { sb = it }
        val delm = ": "

        if (Pref.bpDirectoryLastActive(pref) && last_status_at > 0L)
            prepareSb()
                .append(context.getString(R.string.last_active))
                .append(delm)
                .append(TootStatus.formatTime(context, last_status_at, bAllowRelative = true, onlyDate = true))

        if (!fromProfileHeader) {
            if (Pref.bpDirectoryTootCount(pref)
                && (statuses_count ?: 0L) > 0L)
                prepareSb()
                    .append(context.getString(R.string.toot_count))
                    .append(delm)
                    .append(statuses_count.toString())

            if (Pref.bpDirectoryFollowers(pref)
                && !Pref.bpHideFollowCount(pref)
                && (followers_count ?: 0L) > 0L)
                prepareSb()
                    .append(context.getString(R.string.followers))
                    .append(delm)
                    .append(followers_count.toString())

            if (Pref.bpDirectoryNote(pref) && note?.isNotEmpty() == true) {
                val decodedNote = DecodeOptions(
                    context,
                    accessInfo,
                    short = true,
                    decodeEmoji = true,
                    emojiMapProfile = profile_emojis,
                    emojiMapCustom = custom_emojis,
                    unwrapEmojiImageTag = true,
                    mentionDefaultHostDomain = this,
                ).decodeHTML(note)
                    .replaceAllEx(reNoteLineFeed, " ")
                    .trimEx()
                if (decodedNote.isNotBlank()) {
                    prepareSb().append(
                        if (decodedNote is SpannableStringBuilder && decodedNote.length > 200) {
                            decodedNote.replace(200, decodedNote.length, "…")
                        } else {
                            decodedNote
                        }
                    )
                }
            }
        }

        tv.vg(sb != null)
            ?.apply {
                text = sb
                movementMethod = MyLinkMovementMethod
                invalidator?.register(sb)
            }
            ?: invalidator?.clear()

        return sb
    }

    companion object {

        private val log = LogCategory("TootAccount")

        internal val reWhitespace = "[\\s\\t\\x0d\\x0a]+".asciiPattern()

        // noteをディレクトリに表示する際、制御文字や空白を変換する
        private val reNoteLineFeed: Pattern = """[\x00-\x20\x7f　]+""".asciiPattern()

        // IDNドメインを含むホスト名の正規表現
        private const val reHostIdn = """(?:(?:[\p{L}\p{N}][\p{L}\p{N}-_]*\.)+[\p{L}\p{N}]{2,})"""

        internal val reHostInUrl: Pattern = """\Ahttps://($reHostIdn)/"""
            .asciiPattern()

        // 文字数カウントに使う正規表現
        private val reCountLink = """(https?://$reHostIdn[\w/:%#@${'$'}&?!()\[\]~.=+\-]*)"""
            .asciiPattern()

        // 投稿中のURLは23文字として扱う
        private val strUrlReplacement = (1..23).joinToString(transform = { " " })

        // \p{L} : アルファベット (Letter)。
        // 　　Ll(小文字)、Lm(擬似文字)、Lo(その他の文字)、Lt(タイトル文字)、Lu(大文字アルファベット)を含む
        // \p{M} : 記号 (Mark)
        // \p{Nd} : 10 進数字 (Decimal number)
        // \p{Pc} : 連結用句読記号 (Connector punctuation)

        // rubyの [:word:] ： 単語構成文字 (Letter | Mark | Decimal_Number | Connector_Punctuation)
        const val reRubyWord = """\p{L}\p{M}\p{Nd}\p{Pc}"""

        // rubyの [:alpha:] : 英字 (Letter | Mark)
        const val reRubyAlpha = """\p{L}\p{M}"""

        private const val reMastodonUserName = """[A-Za-z0-9_]+(?:[A-Za-z0-9_.-]+[A-Za-z0-9_]+)?"""
        private const val reMastodonMention =
            """(?<=^|[^/$reRubyWord])@(($reMastodonUserName)(?:@[$reRubyWord.-]+[A-Za-z0-9]+)?)"""

        val reCountMention = reMastodonMention.asciiPattern()

        fun countText(s: String): Int {
            return s
                .replaceAll(reCountLink, strUrlReplacement)
                .replaceAll(reCountMention, "@$2")
                .codePointCount()
        }

        // MisskeyのMFMのメンションのドメイン部分はIDN非対応
        private const val reMisskeyHost = """\w[\w.-]*\w"""

        // https://misskey.io/@tateisu@%E3%83%9E%E3%82%B9%E3%83%88%E3%83%89%E3%83%B33.juggler.jp
        // のようなURLがMisskeyのメンションから生成されることがある
        // %エンコーディングのデコードが必要
        private const val reMisskeyHostEncoded = """[\w%][\w.%-]*[\w%]"""

        // MFMのメンション @username @username@host
        // (Mastodonのカラムでは使われていない)
        // MisskeyのMFMはIDNをサポートしていない
        private val reMisskeyMentionBase = """@(\w+(?:[\w-]*\w)?)(?:@($reMisskeyHost))?"""
            .asciiPattern()

        // MFMパース時に使う
        internal val reMisskeyMentionMFM = """\A$reMisskeyMentionBase"""
            .asciiPattern()

        // 投稿送信時にメンションを見つけてuserIdを調べるために使う
        internal val reMisskeyMentionPost = """(?:\A|\s)$reMisskeyMentionBase"""
            .asciiPattern()

        // host, user ,(instance)
        // Misskeyだけではないのでusernameの定義が違う
        internal val reAccountUrl =
            """\Ahttps://($reHostIdn)/@(\w+[\w-]*)(?:@($reMisskeyHostEncoded))?(?=\z|[?#])"""
                .asciiPattern()

        // host,user
        internal val reAccountUrl2 =
            """\Ahttps://($reHostIdn)/users/(\w|\w+[\w-]*\w)(?=\z|[?#])"""
                .asciiPattern()

        // notestockはaccountのnotag先頭に

        fun getAcctFromUrl(url: String?): Acct? {

            url ?: return null

            var m = reAccountUrl.matcher(url)
            if (m.find()) {
                val host = m.groupEx(1)
                val user = m.groupEx(2)!!
                val instance = m.groupEx(3)?.decodePercent()
                return Acct.parse(user, instance?.notEmpty() ?: host)
            }

            m = reAccountUrl2.matcher(url)
            if (m.find()) {
                val host = m.groupEx(1)
                val user = m.groupEx(2)!!
                return Acct.parse(user, host)
            }

            return null
        }

        private fun parseSource(src: JsonObject?): Source? {
            src ?: return null
            return try {
                Source(src)
            } catch (ex: Throwable) {
                log.trace(ex)
                log.e("parseSource failed.")
                null
            }
        }

        private fun findApDomain(acctArg: String?, linkHelper: LinkHelper?): Host? {
            // acctから調べる
            if (acctArg != null) {
                val acct = Acct.parse(acctArg)
                if (acct.host != null) return acct.host
            }

            // Acctはnullか、hostを含まない
            if (linkHelper != null) return linkHelper.apDomain

            return null
        }

        private fun findApiHost(url: String?): Host? {
            // URLから調べる
            // たぶんどんなURLでもauthorityの部分にホスト名が来るだろう(慢心)
            url.mayUri()?.authority?.notEmpty()?.let { return Host.parse(it) }

            log.e("findApiHost: can't parse host from URL $url")
            return null
        }

        // Tootsearch用。URLやUriを使ってアカウントのインスタンス名を調べる
        fun findHostFromUrl(
            acctArg: String?,
            linkHelper: LinkHelper?,
            url: String?
        ): Pair<Host?, Host?> {
            val apDomain = findApDomain(acctArg, linkHelper)
            val apiHost = findApiHost(url)
            return Pair(apiHost ?: apDomain, apDomain ?: apiHost)
        }

        fun parseFields(src: JsonArray?): ArrayList<Field>? {
            src ?: return null
            val dst = ArrayList<Field>()
            for (item in src) {
                if (item !is JsonObject) continue
                val name = item.string("name") ?: continue
                val value = item.string("value") ?: continue
                val verifiedAt = when (val svVerifiedAt = item.string("verified_at")) {
                    null -> 0L
                    else -> TootStatus.parseTime(svVerifiedAt)
                }
                dst.add(Field(name, value, verifiedAt))
            }
            return dst.notEmpty()
        }

        private fun parseMisskeyFields(src: JsonObject): ArrayList<Field>? {

            var dst: ArrayList<Field>? = null

            // リモートユーザーはAP経由のフィールドが表示される
            // https://github.com/syuilo/misskey/pull/3590
            // https://github.com/syuilo/misskey/pull/3596
            src.jsonArray("fields")?.forEach { o ->
                if (o !is JsonObject) return@forEach
                //plain text
                val n = o.string("name") ?: return@forEach
                // mfm
                val v = o.string("value") ?: ""
                dst = (dst ?: ArrayList()).apply { add(Field(n, v, 0L)) }
            }

            // misskeyローカルユーザーはTwitter等の連携をフィールドに表示する
            // https://github.com/syuilo/misskey/pull/3499
            // https://github.com/syuilo/misskey/pull/3586

            fun appendField(name: String, caption: String, url: String) {
                val value = """[$caption]($url)"""
                dst = (dst ?: ArrayList()).apply { add(Field(name, value, 0L)) }
            }

            runCatching {
                src.jsonObject("twitter")?.let {
                    appendField(
                        "Twitter",
                        "@${it.string("screenName")}",
                        "https://twitter.com/intent/user?user_id=${it.string("userId")}"
                    )
                }
            }

            runCatching {
                src.jsonObject("github")?.string("login")?.let {
                    appendField(
                        "GitHub",
                        it,
                        "https://github.com/$it"
                    )
                }
            }

            runCatching {
                src.jsonObject("discord")?.let {
                    appendField(
                        "Discord",
                        "${it.string("username")}#${it.string("discriminator")}",
                        "https://discordapp.com/users/${it.string("id")}"
                    )
                }
            }

            return if (dst?.isNotEmpty() == true) dst else null
        }

    }
}
