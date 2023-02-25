package jp.juggler.subwaytooter.api.entity

import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.MisskeyAccountDetailMap
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.TootAccountRef.Companion.tootAccountRefOrNull
import jp.juggler.subwaytooter.api.entity.TootStatus.Companion.tootStatus
import jp.juggler.subwaytooter.emoji.CustomEmoji
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.util.emojiSizeMode
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.daoUserRelation
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.DecodeOptions.Companion.emojiScaleUserName
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.subwaytooter.util.NetworkEmojiInvalidator
import jp.juggler.subwaytooter.util.matchHost
import jp.juggler.subwaytooter.view.MyLinkMovementMethod
import jp.juggler.util.*
import jp.juggler.util.data.*
import jp.juggler.util.log.LogCategory
import jp.juggler.util.ui.vg
import java.util.*
import java.util.regex.Pattern

open class TootAccount(
    //URL of the user's profile page (can be remote)
    // https://mastodon.juggler.jp/@tateisu
    // 疑似アカウントではnullになります
    val url: String?,

    //	The ID of the account
    val id: EntityId,

    // 	The username of the account  /[A-Za-z0-9_]{1,30}/
    val username: String,

    final override val apiHost: Host,
    final override val apDomain: Host,

    //	Equals username for local users, includes @domain for remote ones
    val acct: Acct,

    //	The account's display name
    val display_name: String,

    //Boolean for when the account cannot be followed without waiting for approval first
    val locked: Boolean,

    //	The time the account was created
    // ex: "2017-04-13T11:06:08.289Z"
    val created_at: String?,
    val time_created_at: Long,

    //	The number of followers for the account
    var followers_count: Long? = null,

    //The number of accounts the given account is following
    var following_count: Long? = null,

    //	The number of statuses the account has made
    var statuses_count: Long? = null,

    // Biography of user
    // 説明文。改行は\r\n。リンクなどはHTMLタグで書かれている
    val note: String?,

    //	URL to the avatar image
    val avatar: String?,

    //	URL to the avatar static image (gif)
    val avatar_static: String?,

    //URL to the header image
    val header: String?,

    //	URL to the header static image (gif)
    val header_static: String?,

    val source: Source?,

    val profile_emojis: MutableMap<String, NicoProfileEmoji>?,

    val movedRef: TootAccountRef?,

    val fields: ArrayList<Field>?,

    val custom_emojis: MutableMap<String, CustomEmoji>?,

    val bot: Boolean,
    val isCat: Boolean,
    val isAdmin: Boolean,
    val isPro: Boolean,
    // user_hides_network is preference, not exposed in API
    // val user_hides_network : Boolean

    var pinnedNotes: ArrayList<TootStatus>? = null,
    private var pinnedNoteIds: ArrayList<String>? = null,

    // misskey (only /api/users/show)
    var location: String? = null,
    var birthday: String? = null,

    // mastodon 3.0.0-dev // last_status_at : "2019-08-29T12:42:08.838Z" or null
    // mastodon 3.1       // last_status_at : "2019-08-29" or null
    private var last_status_at: Long = 0L,

    // mastodon 3.3.0
    var suspended: Boolean = false,

    val json: JsonObject,

    ) : HostAndDomain {

    class Field(
        val name: String,
        val value: String,
        val verified_at: Long, // 0L if not verified
    )

    val moved: TootAccount?
        get() = movedRef?.get()

    @Suppress("unused")
    val isLocal: Boolean
        get() = acct.host == null

    val isRemote: Boolean
        get() = acct.host != null

    fun getUserUrl() = url ?: "https://${apDomain.pretty}/@$username"

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
            authorDomain = this,
            enlargeCustomEmoji = emojiScaleUserName,
            enlargeEmoji = emojiScaleUserName,
        ).decodeEmoji(sv)
    }

    // リストメンバーダイアログや引っ越し先ユーザなど、TL以外の部分に名前を表示する場合は
    // Invalidator の都合でSpannableを別途生成する必要がある
    fun decodeDisplayNameCached(context: Context): Spannable {
        // remove white spaces
        val sv = reWhitespace.matcher(display_name).replaceAll(" ")

        // decode emoji code
        return DecodeOptions(
            context,
            emojiMapProfile = profile_emojis,
            emojiMapCustom = custom_emojis,
            authorDomain = this,
            enlargeCustomEmoji = emojiScaleUserName,
            enlargeEmoji = emojiScaleUserName,
        ).decodeEmoji(sv)
    }

    private fun SpannableStringBuilder.replaceAllEx(
        pattern: Pattern,
        replacement: String,
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
        invalidator: NetworkEmojiInvalidator,
        fromProfileHeader: Boolean = false,
        suggestionSource: String? = null,
    ): SpannableStringBuilder? {
        val context = invalidator.view.context

        var sb: SpannableStringBuilder? = null
        fun prepareSb() = sb?.apply { append('\n') } ?: SpannableStringBuilder().also { sb = it }
        val delm = ": "

        if (suggestionSource?.isNotEmpty() == true) {
            prepareSb()
                .append(context.getString(R.string.suggestion_source))
                .append(delm)
                .append(suggestionSource)
        }

        if (PrefB.bpDirectoryLastActive.value && last_status_at > 0L) {
            prepareSb()
                .append(context.getString(R.string.last_active))
                .append(delm)
                .append(
                    TootStatus.formatTime(
                        context,
                        last_status_at,
                        bAllowRelative = true,
                        onlyDate = true
                    )
                )
        }

        if (!fromProfileHeader) {
            if (PrefB.bpDirectoryTootCount.value &&
                (statuses_count ?: 0L) > 0L
            ) {
                prepareSb()
                    .append(context.getString(R.string.toot_count))
                    .append(delm)
                    .append(statuses_count.toString())
            }

            if (PrefB.bpDirectoryFollowers.value &&
                !PrefB.bpHideFollowCount.value &&
                (followers_count ?: 0L) > 0L
            ) {
                prepareSb()
                    .append(context.getString(R.string.followers))
                    .append(delm)
                    .append(followers_count.toString())
            }

            if (PrefB.bpDirectoryNote.value && note?.isNotEmpty() == true) {
                val decodedNote = DecodeOptions(
                    context,
                    accessInfo,
                    short = true,
                    decodeEmoji = true,
                    emojiMapProfile = profile_emojis,
                    emojiMapCustom = custom_emojis,
                    unwrapEmojiImageTag = true,
                    authorDomain = this,
                    emojiSizeMode = accessInfo.emojiSizeMode(),
                    enlargeCustomEmoji = emojiScaleUserName,
                    enlargeEmoji = emojiScaleUserName,
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

        invalidator.view.vg(sb != null)?.apply {
            invalidator.text = sb!!
            movementMethod = MyLinkMovementMethod
        } ?: invalidator.clear()

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

        private fun tootAccountMisskey(parser: TootParser, src: JsonObject): TootAccount {

            val custom_emojis: MutableMap<String, CustomEmoji>? =
                when (src.jsonObject("emojis")?.values?.firstOrNull()) {
                    // Misskey13 は ショートコード→URLの単純なマップ
                    is String -> CustomEmoji.decodeMisskey12ReactionEmojis(src.jsonObject("emojis"))
                    // もっと古い形式
                    else -> parseMapOrNull(src.jsonArray("emojis"), CustomEmoji::decodeMisskey)
                }

            val username = src.stringOrThrow("username")

            // FIXME apiHostとapDomainが異なる場合はMisskeyだとどうなの…？
            val apiHost = src.string("host")?.let { Host.parse(it) } ?: parser.apiHost

            @Suppress("UnnecessaryVariable")
            val apDomain = apiHost

            @Suppress("LeakingThis")
            val acct: Acct = when {
                // アクセス元から見て内部ユーザなら short acct
                parser.linkHelper.matchHost(apiHost, apDomain) -> Acct.parse(username)

                // アクセス元から見て外部ユーザならfull acct
                else -> Acct.parse(username, apDomain)
            }

            val id = EntityId.mayDefault(src.string("id"))

            val created_at: String? = src.string("createdAt")

            val pinnedNotes: ArrayList<TootStatus>? = if (parser.misskeyDecodeProfilePin) {
                val list =
                    parseList(src.jsonArray("pinnedNotes")) { tootStatus(parser, it) }
                list.forEach { it.pinned = true }
                list.notEmpty()
            } else {
                null
            }

            // this.user_hides_network = src.optBoolean("user_hides_network")

            val profile = src.jsonObject("profile")
            daoUserRelation.fromAccount(parser, src, id)

            return TootAccount(
                acct = acct,
                apDomain = apDomain,
                apiHost = apiHost,
                avatar = src.string("avatarUrl"),
                avatar_static = src.string("avatarUrl"),
                birthday = profile?.string("birthday"),
                bot = src.optBoolean("isBot", false),
                created_at = created_at,
                custom_emojis = custom_emojis,
                display_name = src.string("name")?.notEmpty()?.sanitizeBDI() ?: username,
                fields = parseMisskeyFields(src),
                followers_count = src.long("followersCount") ?: -1L,
                following_count = src.long("followingCount") ?: -1L,
                header = src.string("bannerUrl"),
                header_static = src.string("bannerUrl"),
                id = id,
                isAdmin = src.optBoolean("isAdmin", false),
                isCat = src.optBoolean("isCat", false),
                isPro = src.optBoolean("isPro", false),
                json = src,
                location = profile?.string("location"),
                locked = src.optBoolean("isLocked"),
                movedRef = null,
                note = src.string("description"),
                pinnedNoteIds = src.stringArrayList("pinnedNoteIds"),
                pinnedNotes = pinnedNotes,
                profile_emojis = null,
                source = null,
                statuses_count = src.long("notesCount") ?: -1L,
                time_created_at = TootStatus.parseTime(created_at),
                url = "https://${apiHost.ascii}/@$username",
                username = username,
                suspended = false,
                last_status_at = 0L,
            ).apply {
                MisskeyAccountDetailMap.fromAccount(parser, this, id)
            }
        }

        private fun tootAccountNoteStock(parser: TootParser, src: JsonObject): TootAccount {

            // notestock はActivityPub 準拠のサービスなので、サーバ内IDというのは特にない
            val id: EntityId = EntityId.DEFAULT

            val tmpDisplayName = src.string("display_name")
            val tmpUserName = src.string("username")

            // notestockはdisplay_nameとusernameが入れ替わってる？
            val username = tmpDisplayName ?: tmpUserName ?: error("missing username,displayname")
            val display_name =
                tmpUserName ?: tmpDisplayName ?: error("missing username,displayname")

            val tmpAcct = src.string("subject")?.let { Acct.parse(it) }
            val url: String? = src.string("url")
            val apDomain: Host = tmpAcct?.takeIf { it.isValidFull }?.host
                ?: Host.parse(
                    src.string("id").mayUri()?.authority?.notEmpty()
                        ?: error("can't get apDomain from account's AP id.")
                )
            val apiHost: Host = Host.parse(
                url.mayUri()?.authority?.notEmpty()
                    ?: error("can't get apiHost from account's AP url.")
            )

            val apTag = APTag(parser, src.jsonArray("tag"))

            return TootAccount(
                acct = Acct.parse(username, apDomain),
                apDomain = apDomain,
                apiHost = apiHost,
                avatar = src.string("avatar"),
                avatar_static = src.string("avatar_static"),
                birthday = null,
                bot = false,
                created_at = null,
                custom_emojis = apTag.emojiList.notEmpty(),
                display_name = display_name,
                fields = null,
                followers_count = null,
                following_count = null,
                header = src.string("header"),
                header_static = src.string("header_static"),
                id = id,
                isAdmin = false,
                isCat = false,
                isPro = false,
                json = src,
                location = null,
                locked = src.boolean("manuallyApprovesFollowers") ?: false,
                movedRef = null,
                note = src.string("note"),
                pinnedNoteIds = null,
                pinnedNotes = null,
                profile_emojis = apTag.profileEmojiList.notEmpty(),
                source = null,
                statuses_count = null,
                time_created_at = 0L,
                url = url,
                username = username,
                suspended = false,
                last_status_at = 0L,
            )
        }

        private fun tootAccountMastodon(parser: TootParser, src: JsonObject): TootAccount {

            // 絵文字データは先に読んでおく
            val custom_emojis: HashMap<String, CustomEmoji>? =
                parseMapOrNull(src.jsonArray("emojis"), CustomEmoji::decodeMastodon)

            val profile_emojis: HashMap<String, NicoProfileEmoji>? =
                when (val o = src["profile_emojis"]) {
                    is JsonArray -> parseMapOrNull(o) { NicoProfileEmoji(it) }
                    is JsonObject -> parseProfileEmoji2(o) { j, k -> NicoProfileEmoji(j, k) }
                    else -> null
                }

            val acct: Acct
            val apDomain: Host
            val apiHost: Host
            val id: EntityId

            // 疑似アカウントにacctとusernameだけ
            val url: String? = src.string("url")

            val username: String = src.stringOrThrow("username")

            val movedRef: TootAccountRef? = tootAccountRefOrNull(
                parser,
                src.jsonObject("moved")?.let {
                    tootAccount(parser, it)
                }
            )

            val created_at: String? = src.string("created_at")

            // this.user_hides_network = src.optBoolean("user_hides_network")

            when (parser.serviceType) {
                ServiceType.MASTODON -> {
                    id = EntityId.mayDefault(src.string("id"))
                    val tmpAcct = src.stringOrThrow("acct")
                    val pair = findHostFromUrl(
                        tmpAcct,
                        parser.linkHelper,
                        url
                    )
                    apiHost = pair.first ?: error("can't get apiHost from acct or url")
                    apDomain = pair.second ?: error("can't get apDomain from acct or url")
                    acct = Acct.parse(username, if (tmpAcct.contains('@')) apDomain else null)
                }

                ServiceType.TOOTSEARCH -> {
                    // tootsearch のアカウントのIDはどのタンス上のものか分からないので役に立たない
                    id = EntityId.DEFAULT
                    val tmpAcct = src.stringOrThrow("acct")
                    val pair = findHostFromUrl(tmpAcct, null, url)
                    apiHost = pair.first ?: error("can't get apiHost from acct or url")
                    apDomain = pair.second ?: error("can't get apDomain from acct or url")
                    acct = Acct.parse(username, apDomain)
                }

                ServiceType.MSP -> {
                    id = EntityId.mayDefault(src.string("id"))
                    // MSPはLTLの情報しか持ってないのでacctは常にホスト名部分を持たない
                    val pair = findHostFromUrl(null, null, url)
                    apiHost = pair.first ?: error("can't get apiHost from acct or url")
                    apDomain = pair.second ?: error("can't get apDomain from acct or url")
                    acct = Acct.parse(username, apDomain)
                }
                else -> error("serverType missmatch: ${parser.serviceType}")
            }
            return TootAccount(
                acct = acct,
                apDomain = apDomain,
                apiHost = apiHost,
                avatar = src.string("avatar"),
                avatar_static = src.string("avatar_static") ?: src.string("avatar"),
                birthday = null,
                bot = src.optBoolean("bot", false),
                created_at = created_at,
                custom_emojis = custom_emojis,
                display_name = src.string("display_name")?.notEmpty()?.sanitizeBDI() ?: username,
                fields = parseFields(src.jsonArray("fields")),
                followers_count = src.long("followers_count"),
                following_count = src.long("following_count"),
                header = src.string("header"),
                header_static = src.string("header_static"),

                id = id,
                isAdmin = false,
                isCat = false,
                isPro = false,
                json = src,
                location = null,
                locked = src.optBoolean("locked"),
                movedRef = movedRef,
                note = src.string("note"),
                pinnedNoteIds = null,
                pinnedNotes = null,
                profile_emojis = profile_emojis,
                source = parseSource(src.jsonObject("source")),
                statuses_count = src.long("statuses_count"),
                time_created_at = TootStatus.parseTime(created_at),
                url = url,
                username = username,
                suspended = src.optBoolean("suspended", false),
                last_status_at = TootStatus.parseTime(src.string("last_status_at")),
            )
        }

        fun tootAccount(parser: TootParser, src: JsonObject): TootAccount {
            src["_fromStream"] = parser.fromStream
            return when (parser.serviceType) {
                ServiceType.MISSKEY -> tootAccountMisskey(parser, src)
                ServiceType.NOTESTOCK -> tootAccountNoteStock(parser, src)
                else -> tootAccountMastodon(parser, src)
            }
        }

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

        private fun parseSource(src: JsonObject?): Source? =
            try {
                src?.let { Source(it) }
            } catch (ex: Throwable) {
                log.e(ex, "parseSource failed.")
                null
            }

        private fun findApDomain(
            acctArg: String?,
            linkHelper: LinkHelper?,
        ): Host? {
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
            linkHelper: LinkHelper
            ?,
            url: String
            ?,
        )
                : Pair<Host?, Host?> {
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
