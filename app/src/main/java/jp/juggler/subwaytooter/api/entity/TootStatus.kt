package jp.juggler.subwaytooter.api.entity

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.StringRes
import android.text.Spannable
import android.text.SpannableString
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.PrefB
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.emoji.CustomEmoji
import jp.juggler.subwaytooter.mfm.SpannableStringBuilderEx
import jp.juggler.subwaytooter.table.HighlightWord
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.*
import jp.juggler.util.*
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FilterTrees(
    val treeIrreversible: WordTrieTree = WordTrieTree(),
    val treeReversible: WordTrieTree = WordTrieTree(),
    val treeAll: WordTrieTree = WordTrieTree(),
)

@Suppress("MemberVisibilityCanPrivate")
class TootStatus(parser: TootParser, src: JsonObject) : TimelineItem() {

    val json: JsonObject

    // A Fediverse-unique resource ID
    // MSP から取得したデータだと uri は提供されずnullになる
    val uri: String

    // URL to the status page (can be remote)
    // ブーストだとnullになる
    val url: String?

    // 投稿元タンスのホスト名
    val originalApDomain: Host
        get() = account.apDomain

    // 取得タンスのホスト名。トゥート検索サービスでは提供されずnullになる
    val readerApDomain: Host?

    // ステータスID。
    // host_access が null の場合は投稿元タンスでのIDかもしれない。
    // 取得に失敗するとINVALID_IDになる
    // Misskeyでは文字列のID。
    val id: EntityId

    // misskeyではページングIDにRelation ID が別途提供されることがある
    internal var _orderId: EntityId? = null

    override fun getOrderId() = _orderId ?: id

    // The TootAccount which posted the status
    val accountRef: TootAccountRef

    val account: TootAccount
        get() = TootAccountMap.find(accountRef.mapId)

    //The number of reblogs for the status
    var reblogs_count: Long? = null  // アプリから変更する。検索サービスでは提供されない(null)

    //The number of favourites for the status
    var favourites_count: Long? = null // アプリから変更する。検索サービスでは提供されない(null)

    //	Whether the authenticated user has reblogged the status
    var reblogged: Boolean = false // アプリから変更する

    //	Whether the authenticated user has favourited the status
    var favourited: Boolean = false  // アプリから変更する

    //	Whether the authenticated user has bookmarked the status
    var bookmarked: Boolean = false  // アプリから変更する

    // Whether the authenticated user has muted the conversation this status from
    var muted: Boolean = false // アプリから変更する

    // 固定されたトゥート
    var pinned: Boolean = false  // アプリから変更する

    //Whether media attachments should be hidden by default
    val sensitive: Boolean

    // The detected language for the status, if detected
    val language: String?

    //If not empty, warning text that should be displayed before the actual content
    // アプリ内部では空文字列はCWなしとして扱う
    // マストドンは「null:CWなし」「空じゃない文字列：CWあり」の2種類
    // Pleromaは「空文字列：CWなし」「空じゃない文字列：CWあり」の2種類
    // Misskeyは「CWなし」「空欄CW」「CWあり」の3通り。空欄CWはパース時に書き換えてしまう
    // Misskeyで投稿が削除された時に変更されるため、val変数にできない
    var spoiler_text: String = ""
    var decoded_spoiler_text: Spannable

    //	Body of the status; this will contain HTML (remote HTML already sanitized)
    var content: String?
    var decoded_content: Spannable

    //Application from which the status was posted
    val application: TootApplication?

    var custom_emojis: HashMap<String, CustomEmoji>? = null

    val profile_emojis: HashMap<String, NicoProfileEmoji>?

    //	The time the status was created
    private val created_at: String?

    //	null or the ID of the status it replies to
    val in_reply_to_id: EntityId?

    //	null or the ID of the account it replies to
    val in_reply_to_account_id: EntityId?

    //	null or the reblogged Status
    val reblog: TootStatus?

    //One of: public, unlisted, private, direct
    val visibility: TootVisibility

    private val misskeyVisibleIds: ArrayList<String>?

    //	An array of Attachments
    val media_attachments: ArrayList<TootAttachmentLike>?

    //	An array of Mentions
    var mentions: ArrayList<TootMention>? = null

    //An array of Tags
    var tags: List<TootTag>? = null

    // public Spannable decoded_tags;
    var decoded_mentions: Spannable = EMPTY_SPANNABLE

    var conversation_main: Boolean = false

    var enquete: TootPolls? = null

    //
    var replies_count: Long? = null

    var viaMobile: Boolean = false

    var reactionSet: TootReactionSet? = null

    var reply: TootStatus?

    val serviceType: ServiceType

    private val deletedAt: String?
    val time_deleted_at: Long

    private var localOnly: Boolean = false

    var myRenoteId: EntityId? = null

    // reblog,reply された投稿からその外側を参照する
    var reblogParent: TootStatus? = null

    // quote toot かどうか。
    var isQuoteToot = false
    private var quote_id: EntityId? = null

    // このstatusがquoteだった場合、ミュート済みかどうか示すフラグ
    var quote_muted = false

    // Misskey 12.3
    var isPromoted = false
    var isFeatured = false

    ///////////////////////////////////////////////////////////////////
    // 以下はentityから取得したデータではなく、アプリ内部で使う

    class AutoCW(
        var refActivity: WeakReference<Any>? = null,
        var cellWidth: Int = 0,
        var decodedSpoilerText: Spannable? = null,
        var originalLineCount: Int = 0,
    )

    // アプリ内部で使うワークエリア
    var auto_cw: AutoCW? = null

    // 会話の流れビューで後から追加する
    var card: TootCard? = null

    var highlightSound: HighlightWord? = null
    var highlightSpeech: HighlightWord? = null
    var highlightAny: HighlightWord? = null

    val time_created_at: Long

    var conversationSummary: TootConversationSummary? = null

    ////////////////////////////////////////////////////////

    init {
        this.json = src
        this.serviceType = parser.serviceType
        src["_fromStream"] = parser.fromStream

        when (parser.serviceType) {
            ServiceType.MISSKEY -> {
                val apiHost = parser.apiHost
                val misskeyId = src.string("id")
                this.readerApDomain = parser.apDomain

                val uri = src.string("uri")
                if (uri != null) {
                    // リモート投稿には uriが含まれる
                    this.uri = uri
                    this.url = uri
                } else {
                    this.uri = "https://$apiHost/notes/$misskeyId"
                    this.url = "https://$apiHost/notes/$misskeyId"
                }

                this.created_at = src.string("createdAt")
                this.time_created_at = parseTime(this.created_at)
                this.id = EntityId.mayDefault(misskeyId)

                // お気に入りカラムなどではパース直後に変更することがある

                // 絵文字マップはすぐ後で使うので、最初の方で読んでおく
                this.custom_emojis =
                    parseMapOrNull(
                        CustomEmoji.decodeMisskey,
                        parser.apDomain,
                        src.jsonArray("emojis"),
                        log
                    )
                this.profile_emojis = null

                val who = parser.account(src.jsonObject("user"))
                    ?: error("missing account")

                this.accountRef = TootAccountRef(parser, who)

                this.reblogs_count = src.long("renoteCount") ?: 0L
                this.favourites_count = 0L
                this.replies_count = src.long("repliesCount") ?: 0L

                this.reblogged = false
                this.favourited = src.optBoolean("isFavorited")

                this.localOnly = src.optBoolean("localOnly")
                this.visibility = TootVisibility.parseMisskey(
                    src.string("visibility"),
                    localOnly
                ) ?: TootVisibility.Unknown

                this.misskeyVisibleIds = parseStringArray(src.jsonArray("visibleUserIds"))

                this.media_attachments =
                    parseListOrNull(
                        ::TootAttachment,
                        parser,
                        src.jsonArray("files") ?: src.jsonArray("media") // v11,v10
                    )

                // Misskeyは画像毎にNSFWフラグがある。どれか１枚でもNSFWならトゥート全体がNSFWということにする
                var bv = src.optBoolean("sensitive")
                media_attachments?.forEach {
                    if ((it as? TootAttachment)?.isSensitive == true) {
                        bv = true
                    }
                }
                this.sensitive = bv

                this.reply = parser.status(src.jsonObject("reply"))
                this.in_reply_to_id = EntityId.mayNull(src.string("replyId"))
                this.in_reply_to_account_id = reply?.account?.id

                this.pinned = parser.pinned
                this.muted = false
                this.language = null

                // "mentionedRemoteUsers" -> "[{"uri":"https:\/\/mastodon.juggler.jp\/users\/tateisu","username":"tateisu","host":"mastodon.juggler.jp"}]"

                this.tags = parseMisskeyTags(src.jsonArray("tags"))

                this.application = parseItem(::TootApplication, parser, src.jsonObject("app"), log)

                this.viaMobile = src.optBoolean("viaMobile")

                // this.decoded_tags = HTMLDecoder.decodeTags( account,status.tags );

                // content
                this.content = src.string("text")

                var options = DecodeOptions(
                    parser.context,
                    parser.linkHelper,
                    short = true,
                    decodeEmoji = true,
                    emojiMapCustom = custom_emojis,
                    emojiMapProfile = profile_emojis,
                    attachmentList = media_attachments,
                    highlightTrie = parser.highlightTrie,
                    mentions = null, // MisskeyはMFMをパースし終わるまでメンションが分からない
                    mentionDefaultHostDomain = account
                )

                this.decoded_content = options.decodeHTML(content)
                if (this.highlightSound == null) this.highlightSound = options.highlightSound
                if (this.highlightSpeech == null) this.highlightSpeech = options.highlightSpeech
                if (this.highlightAny == null) this.highlightAny = options.highlightAny

                // Markdownのデコード結果からmentionsを読むのだった
                val mentions1 =
                    (decoded_content as? SpannableStringBuilderEx)?.mentions

                val sv = src.string("cw")?.cleanCW()
                this.spoiler_text = when {
                    sv == null -> "" // CWなし

                    sv.replace('\u0323', ' ').isBlank() ->
                        parser.context.getString(R.string.blank_cw)

                    else -> sv
                }

                // ハイライト検出のためにDecodeOptionsを作り直す？
                options = DecodeOptions(
                    parser.context,
                    parser.linkHelper,
                    short = true,
                    decodeEmoji = true,
                    emojiMapCustom = custom_emojis,
                    emojiMapProfile = profile_emojis,
                    attachmentList = media_attachments,
                    highlightTrie = parser.highlightTrie,
                    mentions = null, // MisskeyはMFMをパースし終わるまでメンションが分からない
                    mentionDefaultHostDomain = account
                )
                this.decoded_spoiler_text = options.decodeHTML(spoiler_text)

                if (this.highlightSound == null) this.highlightSound = options.highlightSound
                if (this.highlightSpeech == null) this.highlightSpeech = options.highlightSpeech
                if (this.highlightAny == null) this.highlightAny = options.highlightAny

                val mentions2 =
                    (decoded_spoiler_text as? SpannableStringBuilderEx)?.mentions

                this.mentions = mergeMentions(mentions1, mentions2)
                this.decoded_mentions =
                    HTMLDecoder.decodeMentions(parser.linkHelper, this)
                        ?: EMPTY_SPANNABLE

                // contentを読んだ後にアンケートのデコード
                this.enquete = TootPolls.parse(
                    parser,
                    TootPollsType.Misskey,
                    this,
                    media_attachments,
                    src.jsonObject("poll"),
                )

                this.reactionSet = TootReactionSet.parseMisskey(
                    src.jsonObject("reactions") ?: src.jsonObject("reactionCounts"),
                    src.string("myReaction")
                )

                val reblog = parser.status(src.jsonObject("renote"))
                this.reblog = reblog

                // めいめいフォークでは myRenoteIdというものがあるらしい
                // https://github.com/mei23/misskey/blob/mei-m544/src/models/note.ts#L384-L394
                // 直近の一つのrenoteのIdを得られるらしい。
                this.myRenoteId = EntityId.mayNull(src.string("myRenoteId"))
                if (myRenoteId != null) reblogged = true

                // しかしTLにRenoteが露出してるならそのIDを使う方が賢明であろう
                // 外側ステータスが自分なら、内側ステータスのmyRenoteIdを設定する
                if (reblog != null && parser.linkHelper.cast<SavedAccount>()
                        ?.isMe(account) == true
                ) {
                    reblog.myRenoteId = id
                    reblog.reblogged = true
                }

                quote_muted = src.boolean("quote_muted") ?: false
                isQuoteToot = when (reblog) {
                    // 別の投稿を参照していない
                    null -> false

                    // 別の投稿を参照して、かつこの投稿自体が何かコンテンツを持つなら引用トゥートである
                    else -> content?.isNotEmpty() == true ||
                        spoiler_text.isNotEmpty() ||
                        media_attachments?.isNotEmpty() == true ||
                        enquete != null
                }

                this.quote_id = when {
                    isQuoteToot -> reblog?.id
                    else -> null
                }

                this.deletedAt = src.string("deletedAt")
                this.time_deleted_at = parseTime(deletedAt)

                if (card == null) {
                    if (reblog != null && isQuoteToot) {
                        // 引用Renoteにプレビューカードをでっちあげる
                        card = TootCard(parser, reblog)
                    } else if (reply != null) {
                        // 返信にプレビューカードをでっちあげる
                        card = TootCard(parser, reply!!)
                    }
                }

                this.isPromoted = src.string("_prId_")?.isNotEmpty() == true
                this.isFeatured = src.string("_featuredId_")?.isNotEmpty() == true
            }

            ServiceType.NOTESTOCK -> {
                misskeyVisibleIds = null
                reply = null
                deletedAt = null
                time_deleted_at = 0L

                this.url = src.string("url")
                this.created_at = src.string("published")

                val apTag = APTag(parser, src.jsonArray("tag"))
                this.custom_emojis = apTag.emojiList.notEmpty()
                this.profile_emojis = apTag.profileEmojiList.notEmpty()
                this.mentions = apTag.mentions
                this.tags = apTag.hashtags

                val who = parser.account(src.jsonObject("account")) ?: error("missing account")

                this.accountRef = TootAccountRef(parser, who)

                this.reblogs_count = null
                this.favourites_count = null
                this.replies_count = null

                this.readerApDomain = null
                this.uri = src.string("id") ?: error("missing uri")
                this.id = findStatusIdFromUri(uri, url) ?: EntityId.DEFAULT

                this.time_created_at = parseTime(this.created_at)

                val apAttachment = APAttachment(src.jsonArray("attachment"))
                this.media_attachments = apAttachment.mediaAttachments.notEmpty()

                this.visibility = when (src.jsonArray("to")
                    ?.any { it == "https://www.w3.org/ns/activitystreams#Public" }) {
                    true -> TootVisibility.Public
                    else -> TootVisibility.UnlistedHome
                }

                this.sensitive = src.optBoolean("sensitive")

                // TODO APには inReplyTo と inReplyToAtomUri があるけど
                //  リモート投稿のURLからサーバ内IDを計算するのはサーバへのリクエストなしでは無理
                this.in_reply_to_id = null
                this.in_reply_to_account_id = null

                this.application = null
                this.pinned = parser.pinned || src.optBoolean("pinned")
                this.muted = false
                this.language = null
                this.decoded_mentions =
                    HTMLDecoder.decodeMentions(parser.linkHelper, this)
                        ?: EMPTY_SPANNABLE

                val quote = when {
                    !parser.decodeQuote -> null
                    else -> try {
                        parser.decodeQuote = false
                        parser.status(src.jsonObject("quote"))
                    } finally {
                        parser.decodeQuote = true
                    }
                }

                this.quote_id = quote?.id ?: EntityId.mayNull(src.string("quote_id"))
                this.isQuoteToot = quote_id != null
                this.quote_muted = src.boolean("quote_muted") ?: false

                this.reblog = null

                this.card = if (quote != null) {
                    // 引用Renoteにプレビューカードをでっちあげる
                    TootCard(parser, quote)
                    // content中のQTの表現が四角括弧の有無とか色々あるみたいだし
                    // 選択してコピーのことを考えたらむしろ削らない方が良い気がしてきた
                    // removeQt = ! PrefB.bpDontShowPreviewCard(Pref.pref(parser.context))
                } else {
                    null
                }

                // content
                this.content = src.string("content")

                var options = DecodeOptions(
                    parser.context,
                    parser.linkHelper,
                    short = true,
                    decodeEmoji = true,
                    emojiMapCustom = custom_emojis,
                    emojiMapProfile = profile_emojis,
                    attachmentList = media_attachments,
                    highlightTrie = parser.highlightTrie,
                    mentions = mentions,
                    mentionDefaultHostDomain = account,
                    unwrapEmojiImageTag = true, // notestockはカスタム絵文字がimageタグになってる
                )

                this.decoded_content = options.decodeHTML(content)
                if (this.highlightSound == null) this.highlightSound = options.highlightSound
                if (this.highlightSpeech == null) this.highlightSpeech = options.highlightSpeech
                if (this.highlightAny == null) this.highlightAny = options.highlightAny

                val sv = (src.string("summary") ?: "").cleanCW()
                this.spoiler_text = when {
                    sv.isEmpty() -> "" // CWなし
                    sv.isBlank() -> parser.context.getString(R.string.blank_cw)
                    else -> sv
                }

                // ハイライト検出のためにDecodeOptionsを作り直す？
                options = DecodeOptions(
                    parser.context,
                    emojiMapCustom = custom_emojis,
                    emojiMapProfile = profile_emojis,
                    highlightTrie = parser.highlightTrie,
                    mentions = mentions,
                    mentionDefaultHostDomain = account,
                    unwrapEmojiImageTag = true, // notestockはカスタム絵文字がimageタグになってる
                )

                this.decoded_spoiler_text = options.decodeEmoji(spoiler_text)

                if (this.highlightSound == null) this.highlightSound = options.highlightSound
                if (this.highlightSpeech == null) this.highlightSpeech = options.highlightSpeech
                if (this.highlightAny == null) this.highlightAny = options.highlightAny

                this.enquete = (src.jsonArray("oneOf") ?: src.jsonArray("anyOf"))?.let {
                    try {
                        TootPolls(
                            parser,
                            TootPollsType.Notestock,
                            this,
                            media_attachments,
                            src,
                            it
                        )
                    } catch (ex: Throwable) {
                        log.trace(ex)
                        null
                    }
                }
            }

            else -> {
                misskeyVisibleIds = null
                reply = null
                deletedAt = null
                time_deleted_at = 0L

                this.url = src.string("url") // ブースト等では頻繁にnullになる
                this.created_at = src.string("created_at")

                // 絵文字マップはすぐ後で使うので、最初の方で読んでおく
                this.custom_emojis =
                    parseMapOrNull(
                        CustomEmoji.decode,
                        parser.apDomain,
                        src.jsonArray("emojis"),
                        log
                    )

                this.profile_emojis = when (val o = src["profile_emojis"]) {
                    is JsonArray -> parseMapOrNull(::NicoProfileEmoji, o, log)
                    is JsonObject -> parseProfileEmoji2(::NicoProfileEmoji, o, log)
                    else -> null
                }

                val who = parser.account(src.jsonObject("account"))
                    ?: error("missing account")

                this.accountRef = TootAccountRef(parser, who)

                this.reblogs_count = src.long("reblogs_count")
                this.favourites_count = src.long("favourites_count")
                this.replies_count = src.long("replies_count")

                this.reactionSet = TootReactionSet.parseFedibird(
                    src.jsonArray("emoji_reactions")
                        ?: src.jsonObject("pleroma")?.jsonArray("emoji_reactions")
                )

                when (parser.serviceType) {
                    ServiceType.MASTODON -> {
                        this.readerApDomain = parser.apDomain

                        this.id = EntityId.mayDefault(src.string("id"))
                        this.uri = src.string("uri") ?: error("missing uri")

                        this.reblogged = src.optBoolean("reblogged")
                        this.favourited = src.optBoolean("favourited")
                        this.bookmarked = src.optBoolean("bookmarked")

                        this.time_created_at = parseTime(this.created_at)
                        this.media_attachments =
                            parseListOrNull(
                                ::TootAttachment,
                                parser,
                                src.jsonArray("media_attachments"),
                                log
                            )
                        val visibilityString = when {
                            src.boolean("limited") == true -> "limited"
                            else -> src.string("visibility")
                        }
                        this.visibility = TootVisibility.parseMastodon(visibilityString)
                            ?: TootVisibility.Unknown
                        this.sensitive = src.optBoolean("sensitive")
                    }

                    ServiceType.TOOTSEARCH -> {
                        this.readerApDomain = null

                        // 投稿元タンスでのIDを調べる。失敗するかもしれない
                        // XXX: Pleromaだとダメそうな印象
                        this.uri = src.string("uri") ?: error("missing uri")
                        this.id = findStatusIdFromUri(uri, url) ?: EntityId.DEFAULT

                        this.time_created_at = parseTime(this.created_at)
                        this.media_attachments = parseListOrNull(
                            ::TootAttachment,
                            parser,
                            src.jsonArray("media_attachments"),
                            log
                        )
                        this.visibility = TootVisibility.Public
                        this.sensitive = src.optBoolean("sensitive")
                    }

                    ServiceType.MSP -> {
                        this.readerApDomain = parser.apDomain

                        // MSPのデータはLTLから呼んだものなので、常に投稿元タンスでのidが得られる
                        this.id = EntityId.mayDefault(src.string("id"))

                        // MSPだとuriは提供されない。LTL限定なのでURL的なものを作れるはず
                        this.uri =
                            "https://${account.apiHost}/users/${who.username}/statuses/$id"

                        this.time_created_at = parseTimeMSP(created_at)
                        this.media_attachments =
                            TootAttachmentMSP.parseList(src.jsonArray("media_attachments"))
                        this.visibility = TootVisibility.Public
                        this.sensitive = src.optInt("sensitive", 0) != 0
                    }

                    ServiceType.MISSKEY, ServiceType.NOTESTOCK -> error("will not happen")
                }

                this.in_reply_to_id = EntityId.mayNull(src.string("in_reply_to_id"))
                this.in_reply_to_account_id =
                    EntityId.mayNull(src.string("in_reply_to_account_id"))
                this.mentions = parseListOrNull(::TootMention, src.jsonArray("mentions"), log)
                this.tags = TootTag.parseListOrNull(parser, src.jsonArray("tags"))
                this.application =
                    parseItem(::TootApplication, parser, src.jsonObject("application"), log)
                this.pinned = parser.pinned || src.optBoolean("pinned")
                this.muted = src.optBoolean("muted")
                this.language = src.string("language")?.notEmpty()
                this.decoded_mentions =
                    HTMLDecoder.decodeMentions(parser.linkHelper, this)
                        ?: EMPTY_SPANNABLE

                val quote = when {
                    !parser.decodeQuote -> null
                    else -> try {
                        parser.decodeQuote = false
                        parser.status(src.jsonObject("quote"))
                    } finally {
                        parser.decodeQuote = true
                    }
                }

                this.quote_id = quote?.id ?: EntityId.mayNull(src.string("quote_id"))
                this.isQuoteToot = quote_id != null
                this.quote_muted = src.boolean("quote_muted") ?: false

                // Pinned TL を取得した時にreblogが登場することはないので、reblogについてpinned 状態を気にする必要はない
                // Hostdon QT と通常のreblogが同時に出ることはないので、quoteが既出ならreblogのjsonデータは見ない
                this.reblog = quote ?: parser.status(src.jsonObject("reblog"))

                val removeQt = false

                // 2.6.0からステータスにもカード情報が含まれる
                this.card = parseItem(::TootCard, src.jsonObject("card"))
                if (card == null && quote != null) {
                    // 引用Renoteにプレビューカードをでっちあげる
                    card = TootCard(parser, quote)
                    // content中のQTの表現が四角括弧の有無とか色々あるみたいだし
                    // 選択してコピーのことを考えたらむしろ削らない方が良い気がしてきた
                    // removeQt = ! PrefB.bpDontShowPreviewCard(Pref.pref(parser.context))
                }

                // content
                this.content = src.string("content")?.let { sv ->
                    when {
                        removeQt -> {
                            log.d("removeQt? $sv")
                            val reQuoteTootRemover =
                                """(?:\s|<br/>)*QT:\s*\[[^]]+]((?:</\w+>)*)\z""".toRegex()
                            sv.replace(reQuoteTootRemover) {
                                it.groupValues.elementAtOrNull(1) ?: ""
                            }.also { after ->
                                log.d("removeQt? after = $after")
                            }
                        }
                        else -> sv
                    }
                }

                var options = DecodeOptions(
                    parser.context,
                    parser.linkHelper,
                    short = true,
                    decodeEmoji = true,
                    emojiMapCustom = custom_emojis,
                    emojiMapProfile = profile_emojis,
                    attachmentList = media_attachments,
                    highlightTrie = parser.highlightTrie,
                    mentions = mentions,
                    mentionDefaultHostDomain = account
                )

                this.decoded_content = options.decodeHTML(content)
                if (this.highlightSound == null) this.highlightSound = options.highlightSound
                if (this.highlightSpeech == null) this.highlightSpeech = options.highlightSpeech
                if (this.highlightAny == null) this.highlightAny = options.highlightAny

                val sv = (src.string("spoiler_text") ?: "").cleanCW()
                this.spoiler_text = when {
                    sv.isEmpty() -> "" // CWなし
                    sv.isBlank() -> parser.context.getString(R.string.blank_cw)
                    else -> sv
                }

                // ハイライト検出のためにDecodeOptionsを作り直す？
                options = DecodeOptions(
                    parser.context,
                    emojiMapCustom = custom_emojis,
                    emojiMapProfile = profile_emojis,
                    highlightTrie = parser.highlightTrie,
                    mentions = mentions,
                    mentionDefaultHostDomain = account
                )

                this.decoded_spoiler_text = options.decodeEmoji(spoiler_text)

                if (this.highlightSound == null) this.highlightSound = options.highlightSound
                if (this.highlightSpeech == null) this.highlightSpeech = options.highlightSpeech
                if (this.highlightAny == null) this.highlightAny = options.highlightAny

                this.enquete = try {
                    src.string("enquete")?.notEmpty()?.let {
                        TootPolls(
                            parser,
                            TootPollsType.FriendsNico,
                            this,
                            media_attachments,
                            it.decodeJsonObject(),
                        )
                    } ?: src.jsonObject("poll")?.let {
                        TootPolls(
                            parser,
                            TootPollsType.Mastodon,
                            this,
                            media_attachments,
                            it,
                        )
                    }
                } catch (ex: Throwable) {
                    log.trace(ex)
                    null
                }
            }
        }

        this.reblog?.reblogParent = this
    }

    private fun mergeMentions(
        mentions1: java.util.ArrayList<TootMention>?,
        mentions2: java.util.ArrayList<TootMention>?,
    ): java.util.ArrayList<TootMention>? {
        val size = (mentions1?.size ?: 0) + (mentions2?.size ?: 0)
        if (size == 0) return null
        val dst = ArrayList<TootMention>(size)
        if (mentions1 != null) dst.addAll(mentions1)
        if (mentions2 != null) dst.addAll(mentions2)
        return dst
    }

    ///////////////////////////////////////////////////
    // ユーティリティ

    // メディア表示を隠したかどうかのキーに使われる
    // APドメイン名
    val hostAccessOrOriginal: Host
        get() = readerApDomain?.valid() ?: originalApDomain.valid() ?: Host.UNKNOWN

    val busyKey: String
        get() = "${hostAccessOrOriginal.ascii}:$id"

    fun checkMuted(): Boolean {

        // app mute
        val muted_app = muted_app
        if (muted_app != null) {
            val name = application?.name
            if (name != null && muted_app.contains(name)) return true
        }

        // word mute
        val muted_word = muted_word
        if (muted_word != null) {
            if (muted_word.matchShort(decoded_content)) return true
            if (muted_word.matchShort(decoded_spoiler_text)) return true
        }

        // reblog
        return true == reblog?.checkMuted()
    }

    fun hasMedia(): Boolean {
        return (media_attachments?.size ?: 0) > 0
    }

    fun canPin(accessInfo: SavedAccount): Boolean =
        reblog == null &&
            accessInfo.isMe(account) &&
            visibility.canPin(accessInfo.isMisskey)

    // 内部で使う
    private var _filteredWord: String? = null

    val filteredWord: String?
        get() = _filteredWord ?: reblog?._filteredWord

    val filtered: Boolean
        get() = filteredWord != null

    private fun hasReceipt(accessInfo: SavedAccount): TootVisibility {
        val fullAcctMe = accessInfo.getFullAcct(account)

        val reply_account = reply?.account
        if (reply_account != null && fullAcctMe != accessInfo.getFullAcct(reply_account)) {
            return TootVisibility.DirectSpecified
        }

        val in_reply_to_account_id = this.in_reply_to_account_id
        if (in_reply_to_account_id != null && in_reply_to_account_id != account.id) {
            return TootVisibility.DirectSpecified
        }

        mentions?.forEach {
            if (fullAcctMe != accessInfo.getFullAcct(it.acct)) {
                return@hasReceipt TootVisibility.DirectSpecified
            }
        }

        return TootVisibility.DirectPrivate
    }

    fun getBackgroundColorType(accessInfo: SavedAccount) =
        when (visibility) {
            TootVisibility.DirectPrivate,
            TootVisibility.DirectSpecified,
            -> hasReceipt(accessInfo)
            else -> visibility
        }

    fun updateKeywordFilteredFlag(
        accessInfo: SavedAccount,
        trees: FilterTrees?,
        checkIrreversible: Boolean = false,
    ) {

        trees ?: return

        // status from me or boosted by me is not filtered.
        if (accessInfo.isMe(account)) {
            _filteredWord = null
            return
        }

        _filteredWord =
            isKeywordFilteredSub(if (checkIrreversible) trees.treeAll else trees.treeReversible)
                ?.joinToString(", ")

        reblog?.updateKeywordFilteredFlag(accessInfo, trees, checkIrreversible)
    }

    fun isKeywordFiltered(accessInfo: SavedAccount, tree: WordTrieTree?): Boolean {
        tree ?: return false

        // status from me or boosted by me is not filtered.
        if (accessInfo.isMe(account)) return false

        if (isKeywordFilteredSub(tree) != null) return true
        if (reblog?.isKeywordFilteredSub(tree) != null) return true

        return false
    }

    private fun isKeywordFilteredSub(tree: WordTrieTree): ArrayList<String>? {

        var list: ArrayList<String>? = null

        fun check(t: CharSequence?) {
            if (t?.isEmpty() != false) return
            val matches = tree.matchList(t) ?: return
            var dst = list
            if (dst == null) {
                dst = ArrayList()
                list = dst
            }
            for (m in matches)
                dst.add(m.word)
        }

        check(decoded_spoiler_text)
        check(decoded_content)

        return list
    }

    fun updateReactionMastodon(newReactionSet: TootReactionSet) {
        synchronized(this) {
            this.reactionSet = newReactionSet
        }
    }

    fun updateReactionMastodonByEvent(newReaction: TootReaction) {
        synchronized(this) {
            var reactionSet = this.reactionSet
            if (newReaction.count <= 0) {
                reactionSet?.get(newReaction.name)?.let { reactionSet?.remove(it) }
            } else {
                if (reactionSet == null) {
                    reactionSet = TootReactionSet(isMisskey = false)
                    this.reactionSet = reactionSet
                }

                when (val old = reactionSet[newReaction.name]) {
                    null -> reactionSet.add(newReaction)

                    // 同一オブジェクトならマージは不要
                    newReaction -> {
                    }

                    // 異なるオブジェクトの場合はmeを壊さないようにカウントだけ更新する
                    else -> old.count = newReaction.count
                }
            }
        }
    }

    // return true if updated
    fun increaseReactionMisskey(
        code: String?,
        byMe: Boolean,
        emoji: CustomEmoji? = null,
        caller: String,
    ): Boolean {
        code ?: return false

        synchronized(this) {

            if (emoji != null) {
                if (custom_emojis == null) custom_emojis = HashMap()
                custom_emojis?.put(emoji.mapKey, emoji)
            }

            var reactionSet = this.reactionSet
            if (reactionSet == null) {
                reactionSet = TootReactionSet(isMisskey = true)
                this.reactionSet = reactionSet
            }

            if (byMe) {
                // 自分でリアクションしたらUIで更新した後にストリーミングイベントが届くことがある
                // その場合はカウントを変更しない
                if (reactionSet.any { it.me && it.name == code }) return false
            }

            log.d("increaseReaction noteId=$id byMe=$byMe caller=$caller")

            // カウントを増やす
            val reaction =
                reactionSet[code]?.also { it.count = max(0, it.count + 1L) }
                    ?: TootReaction(name = code, count = 1L).also { reactionSet.add(it) }

            if (byMe) reaction.me = true

            return true
        }
    }

    fun decreaseReactionMisskey(
        code: String?,
        byMe: Boolean,
        caller: String,
    ): Boolean {
        code ?: return false

        synchronized(this) {

            val reactionSet = this.reactionSet ?: return false

            if (byMe) {
                // 自分でリアクションしたらUIで更新した後にストリーミングイベントが届くことがある
                // その場合はカウントを変更しない
                if (reactionSet.any { !it.me && it.name == code }) return false
            }

            log.d("decreaseReaction noteId=$id byMe=$byMe caller=$caller")

            // カウントを減らす
            val reaction = reactionSet[code]
                ?.also { it.count = max(0L, it.count - 1L) }

            if (byMe) reaction?.me = false

            return true
        }
    }

    fun markDeleted(context: Context, deletedAt: Long?): Boolean {

        if (PrefB.bpDontRemoveDeletedToot(App1.getAppState(context).pref)) return false

        var sv = if (deletedAt != null) {
            context.getString(R.string.status_deleted_at, formatTime(context, deletedAt, false))
        } else {
            context.getString(R.string.status_deleted)
        }
        this.content = sv
        this.decoded_content = SpannableString(sv)

        sv = ""
        this.spoiler_text = sv
        this.decoded_spoiler_text = SpannableString(sv)

        return true
    }

    class FindStatusIdFromUrlResult(
        val statusId: EntityId?, // may null
        hostArg: String,
        val url: String,
    ) {

        val host = Host.parse(hostArg)
    }

    companion object {

        internal val log = LogCategory("TootStatus")

        @Volatile
        internal var muted_app: HashSet<String>? = null

        @Volatile
        internal var muted_word: WordTrieTree? = null

        const val LANGUAGE_CODE_UNKNOWN = "unknown"
        const val LANGUAGE_CODE_DEFAULT = "default"

        val EMPTY_SPANNABLE = SpannableString("")

        // val reHostIdn = TootAccount.reHostIdn

        // OStatus
        private val reTootUriOS = """tag:([^,]*),[^:]*:objectId=([^:?#/\s]+):objectType=Status"""
            .asciiPattern(Pattern.CASE_INSENSITIVE)

        // ActivityPub 1
        private val reTootUriAP1 = """https?://([^/]+)/users/\w+/statuses/([^?#/\s]+)"""
            .asciiPattern()

        // ActivityPub 2
        private val reTootUriAP2 = """https?://([^/]+)/@\w+/([^?#/\s]+)"""
            .asciiPattern()

        // 公開ステータスページのURL マストドン
        private val reStatusPage = """\Ahttps://([^/]+)/@(\w+)/([^?#/\s]+)(?:\z|[?#])"""
            .asciiPattern()

        // 公開ステータスページのURL Misskey
        internal val reStatusPageMisskey =
            """\Ahttps://([^/]+)/notes/([0-9a-f]{24}|[0-9a-z]{10})\b"""
                .asciiPattern(Pattern.CASE_INSENSITIVE)

        // PleromaのStatusのUri
        private val reStatusPageObjects = """\Ahttps://([^/]+)/objects/([^?#/\s]+)(?:\z|[?#])"""
            .asciiPattern()

        // PleromaのStatusの公開ページ
        private val reStatusPageNotice = """\Ahttps://([^/]+)/notice/([^?#/\s]+)(?:\z|[?#])"""
            .asciiPattern()

        // PixelfedのStatusの公開ページ
        // https://pixelfed.tokyo/p/tateisu/84169185147621376
        private val reStatusPagePixelfed =
            """\Ahttps://([^/]+)/p/([A-Za-z0-9_]+)/([^?#/\s]+)(?:\z|[?#])"""
                .asciiPattern()

        // returns null or pair( status_id, host ,url )
        fun String.findStatusIdFromUrl(): FindStatusIdFromUrlResult? {
            // https://mastodon.juggler.jp/@SubwayTooter/(status_id)
            var m = reStatusPage.matcher(this)
            if (m.find()) {
                return FindStatusIdFromUrlResult(EntityId(m.groupEx(3)!!), m.groupEx(1)!!, this)
            }

            // https://misskey.xyz/notes/(id)
            m = reStatusPageMisskey.matcher(this)
            if (m.find()) {
                return FindStatusIdFromUrlResult(EntityId(m.groupEx(2)!!), m.groupEx(1)!!, this)
            }

            // https://misskey.xyz/objects/(id)
            m = reStatusPageObjects.matcher(this)
            if (m.find()) {
                return FindStatusIdFromUrlResult(
                    null, // ステータスIDではないのでどのタンスで開くにせよ検索APIを通すことになる
                    m.groupEx(1)!!,
                    this
                )
            }

            // https://pl.telteltel.com/notice/9fGFPu4LAgbrTby0xc
            m = reStatusPageNotice.matcher(this)
            if (m.find()) {
                return FindStatusIdFromUrlResult(
                    EntityId(m.groupEx(2)!!),
                    m.groupEx(1)!!,
                    this
                )
            }

            m = reStatusPagePixelfed.matcher(this)
            if (m.find()) {
                return FindStatusIdFromUrlResult(
                    EntityId(m.groupEx(3)!!),
                    m.groupEx(1)!!,
                    this
                )
            }

            return null
        }

        private val tz_utc = TimeZone.getTimeZone("UTC")

        private val reDate = """\A\d+\D+\d+\D+\d+\z""".asciiPattern()

        private val reTime = """\A(\d+)\D+(\d+)\D+(\d+)\D+(\d+)\D+(\d+)\D+(\d+)(?:\D+(\d+))?"""
            .asciiPattern()

        private val reMSPTime = """\A(\d+)\D+(\d+)\D+(\d+)\D+(\d+)\D+(\d+)\D+(\d+)"""
            .asciiPattern()

        fun parseTime(strTime: String?): Long {
            if (strTime != null && strTime.isNotEmpty()) {
                try {
                    var m = reTime.matcher(strTime)
                    if (m.find()) {
                        val g = GregorianCalendar(tz_utc)
                        g.set(
                            m.groupEx(1).optInt() ?: 1,
                            (m.groupEx(2).optInt() ?: 1) - 1,
                            m.groupEx(3).optInt() ?: 1,
                            m.groupEx(4).optInt() ?: 0,
                            m.groupEx(5).optInt() ?: 0,
                            m.groupEx(6).optInt() ?: 0
                        )
                        g.set(Calendar.MILLISECOND, m.groupEx(7).optInt() ?: 0)
                        return g.timeInMillis
                    }
                    // last_status_at などでは YYYY-MM-DD になることがある
                    m = reDate.matcher(strTime)
                    if (m.find()) return parseTime("${strTime}T00:00:00.000Z")

                    log.w("invalid time format: $strTime")
                } catch (ex: Throwable) { // ParseException,  ArrayIndexOutOfBoundsException
                    log.trace(ex)
                    log.e(ex, "TootStatus.parseTime failed. src=$strTime")
                }
            }
            return 0L
        }

        private fun parseTimeMSP(strTime: String?): Long {
            if (strTime != null && strTime.isNotEmpty()) {
                try {
                    val m = reMSPTime.matcher(strTime)
                    if (!m.find()) {
                        log.d("invalid time format: $strTime")
                    } else {
                        val g = GregorianCalendar(tz_utc)
                        g.set(
                            m.groupEx(1).optInt() ?: 1,
                            (m.groupEx(2).optInt() ?: 1) - 1,
                            m.groupEx(3).optInt() ?: 1,
                            m.groupEx(4).optInt() ?: 0,
                            m.groupEx(5).optInt() ?: 0,
                            m.groupEx(6).optInt() ?: 0
                        )
                        g.set(Calendar.MILLISECOND, 500)
                        return g.timeInMillis
                    }
                } catch (ex: Throwable) { // ParseException,  ArrayIndexOutOfBoundsException
                    log.trace(ex)
                    log.e(ex, "parseTimeMSP failed. src=$strTime")
                }
            }
            return 0L
        }

        @SuppressLint("SimpleDateFormat")
        internal val date_format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

        @SuppressLint("SimpleDateFormat")
        internal val date_format2 = SimpleDateFormat("yyyy-MM-dd")

        fun formatTime(
            context: Context,
            t: Long,
            bAllowRelative: Boolean,
            onlyDate: Boolean = false,
        ): String {

            val now = System.currentTimeMillis()
            var delta = now - t

            @StringRes val phraseId = when {
                delta >= 0 -> R.string.relative_time_phrase_past
                else -> R.string.relative_time_phrase_future
            }

            fun f(v: Long, unit1: Int, units: Int): String {
                val vi = v.toInt()
                return context.getString(
                    phraseId,
                    vi,
                    context.getString(if (vi <= 1) unit1 else units)
                )
            }

            if (onlyDate) return when {
                delta < 40 * 86400000L -> f(
                    delta / 86400000L,
                    R.string.relative_time_unit_day1,
                    R.string.relative_time_unit_days
                )
                else ->
                    formatDate(t, date_format2, omitZeroSecond = false, omitYear = true)
            }

            if (bAllowRelative && PrefB.bpRelativeTimestamp(App1.pref)) {

                delta = abs(delta)

                when {
                    delta < 1000L -> return context.getString(R.string.time_within_second)

                    delta < 60000L -> return f(
                        delta / 1000L,
                        R.string.relative_time_unit_second1,
                        R.string.relative_time_unit_seconds
                    )

                    delta < 3600000L -> return f(
                        delta / 60000L,
                        R.string.relative_time_unit_minute1,
                        R.string.relative_time_unit_minutes
                    )

                    delta < 86400000L -> return f(
                        delta / 3600000L,
                        R.string.relative_time_unit_hour1,
                        R.string.relative_time_unit_hours
                    )

                    delta < 40 * 86400000L -> return f(
                        delta / 86400000L,
                        R.string.relative_time_unit_day1,
                        R.string.relative_time_unit_days
                    )
                }
                // fall back to absolute time
            }

            return formatDate(t, date_format, omitZeroSecond = false, omitYear = false)
        }

        // 告知の開始/終了日付
        private fun formatDate(
            t: Long,
            format: SimpleDateFormat,
            omitZeroSecond: Boolean,
            omitYear: Boolean,
        ): String {
            var dateTarget = format.format(Date(t))

            // 秒の部分を省略する
            if (omitZeroSecond && dateTarget.endsWith(":00")) {
                dateTarget = dateTarget.substring(0, dateTarget.length - 3)
            }

            // 年の部分が現在と同じなら省略する
            if (omitYear) {
                val dateNow = format.format(Date())
                val delm = dateNow.indexOf('-')
                if (delm != -1 &&
                    dateNow.substring(0, delm + 1) == dateTarget.substring(0, delm + 1)
                ) {
                    dateTarget = dateTarget.substring(delm + 1)
                }
            }

            return dateTarget
        }

        fun formatTimeRange(start: Long, end: Long, allDay: Boolean): Pair<String, String> {
            val strStart = when {
                start <= 0L -> ""
                allDay -> formatDate(start, date_format2, omitZeroSecond = false, omitYear = true)
                else -> formatDate(start, date_format, omitZeroSecond = true, omitYear = true)
            }
            val strEnd = when {
                end <= 0L -> ""
                allDay -> formatDate(end, date_format2, omitZeroSecond = false, omitYear = true)
                else -> formatDate(end, date_format, omitZeroSecond = true, omitYear = true)
            }
            // 終了日は先頭と同じ部分を省略する
            var skip = 0
            for (i in 0 until min(strStart.length, strEnd.length)) {
                val c = strStart[i]
                if (c != strEnd[i]) break
                if (c.isDigit()) continue
                skip = i + 1
                if (c == ' ') break // 時間以降は省略しない
            }
            return Pair(strStart, strEnd.substring(skip, strEnd.length))
        }

        fun parseStringArray(src: JsonArray?): ArrayList<String>? {
            var rv: ArrayList<String>? = null
            if (src != null) {
                for (i in src.indices) {
                    val s = src.string(i)
                    if (s?.isNotEmpty() == true) {
                        if (rv == null) rv = ArrayList()
                        rv.add(s)
                    }
                }
            }
            return rv
        }

        private fun parseMisskeyTags(src: JsonArray?): ArrayList<TootTag>? {
            var rv: ArrayList<TootTag>? = null
            if (src != null) {
                for (i in src.indices) {
                    val sv = src.string(i)
                    if (sv?.isNotEmpty() == true) {
                        if (rv == null) rv = ArrayList()
                        rv.add(TootTag(name = sv))
                    }
                }
            }
            return rv
        }

        fun validStatusId(src: EntityId?): EntityId? =
            when {
                src == null -> null
                src == EntityId.DEFAULT -> null
                src.toString().startsWith("-") -> null
                else -> src
            }

        private fun String.cleanCW() =
            CharacterGroup.reWhitespace.matcher(this).replaceAll(" ").sanitizeBDI()
        /* 空欄かどうかがCW判定条件に影響するので、trimしてはいけない */

        // 投稿元タンスでのステータスIDを調べる
        fun findStatusIdFromUri(
            uri: String?,
            url: String?,
        ): EntityId? {

            try {
                if (uri?.isNotEmpty() == true) {
                    // https://friends.nico/users/(who)/statuses/(status_id)
                    var m = reTootUriAP1.matcher(uri)
                    if (m.find()) return EntityId(m.groupEx(2)!!)

                    // https://server/@user/(status_id)
                    m = reTootUriAP2.matcher(uri)
                    if (m.find()) return EntityId(m.groupEx(2)!!)

                    // https://misskey.xyz/notes/5b802367744b650030a13640
                    m = reStatusPageMisskey.matcher(uri)
                    if (m.find()) return EntityId(m.groupEx(2)!!)

                    // https://pl.at7s.me/objects/feeb4399-cd7a-48c8-8999-b58868daaf43
                    // tootsearch中の投稿からIDを読めるようにしたい
                    // しかしこのURL中のuuidはステータスIDではないので、無意味
                    // m = reObjects.matcher(uri)
                    // if(m.find()) return EntityId(m.groupEx(2))

                    // https://pl.telteltel.com/notice/9fGFPu4LAgbrTby0xc
                    m = reStatusPageNotice.matcher(uri)
                    if (m.find()) return EntityId(m.groupEx(2)!!)

                    // tag:mstdn.osaka,2017-12-19:objectId=5672321:objectType=Status
                    m = reTootUriOS.matcher(uri)
                    if (m.find()) return EntityId(m.groupEx(2)!!)

                    log.w("findStatusIdFromUri: unsupported uri. $uri")
                }

                if (url?.isNotEmpty() == true) {

                    // https://friends.nico/users/(who)/statuses/(status_id)
                    var m = reTootUriAP1.matcher(url)
                    if (m.find()) return EntityId(m.groupEx(2)!!)

                    // https://friends.nico/@(who)/(status_id)
                    m = reTootUriAP2.matcher(url)
                    if (m.find()) return EntityId(m.groupEx(2)!!)

                    // https://misskey.xyz/notes/5b802367744b650030a13640
                    m = reStatusPageMisskey.matcher(url)
                    if (m.find()) return EntityId(m.groupEx(2)!!)

                    // https://pl.at7s.me/objects/feeb4399-cd7a-48c8-8999-b58868daaf43
                    // tootsearch中の投稿からIDを読めるようにしたい
                    // しかしこのURL中のuuidはステータスIDではないので、無意味
                    // m = reObjects.matcher(url)
                    // if(m.find()) return EntityId(m.groupEx(2))

                    // https://pl.telteltel.com/notice/9fGFPu4LAgbrTby0xc
                    m = reStatusPageNotice.matcher(url)
                    if (m.find()) return EntityId(m.groupEx(2)!!)

                    log.w("findStatusIdFromUri: unsupported url. $url")
                }
            } catch (ex: Throwable) {
                log.e(ex, "can't parse status from: $uri,$url")
            }

            return null
        }
    }
}
