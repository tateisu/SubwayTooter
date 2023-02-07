package jp.juggler.subwaytooter.api.entity

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableString
import androidx.annotation.StringRes
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootAccountMap
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.TootAttachment.Companion.tootAttachment
import jp.juggler.subwaytooter.emoji.CustomEmoji
import jp.juggler.subwaytooter.mfm.SpannableStringBuilderEx
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.table.*
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.HTMLDecoder
import jp.juggler.util.*
import jp.juggler.util.data.*
import jp.juggler.util.log.LogCategory
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FilterTrees(
    val treeHide: WordTrieTree = WordTrieTree(),
    val treeWarn: WordTrieTree = WordTrieTree(),
    val treeAll: WordTrieTree = WordTrieTree(),
)

@Suppress("MemberVisibilityCanPrivate")
class TootStatus(
    parser: TootParser,

    val json: JsonObject,

    // A Fediverse-unique resource ID
    // MSP から取得したデータだと uri は提供されずnullになる
    val uri: String,

    // URL to the status page (can be remote)
    // ブーストだとnullになる
    val url: String?,

    // 取得タンスのホスト名。トゥート検索サービスでは提供されずnullになる
    val readerApDomain: Host?,

    // ステータスID。
    // host_access が null の場合は投稿元タンスでのIDかもしれない。
    // 取得に失敗するとINVALID_IDになる
    // Misskeyでは文字列のID。
    val id: EntityId,

    // misskeyではページングIDにRelation ID が別途提供されることがある
    var _orderId: EntityId? = null,

    // The TootAccount which posted the status
    val accountRef: TootAccountRef,

    //The number of reblogs for the status
    // アプリから変更する。検索サービスでは提供されない(null)
    var reblogs_count: Long? = null,

    //The number of favourites for the status
    // アプリから変更する。検索サービスでは提供されない(null)
    var favourites_count: Long? = null,

    //	Whether the authenticated user has reblogged the status
    // アプリから変更する
    var reblogged: Boolean = false,

    //	Whether the authenticated user has favourited the status
    // アプリから変更する
    var favourited: Boolean = false,

    //	Whether the authenticated user has bookmarked the status
    // アプリから変更する
    var bookmarked: Boolean = false,

    // Whether the authenticated user has muted the conversation this status from
    // アプリから変更する
    var muted: Boolean = false,

    // 固定されたトゥート
    // アプリから変更する
    var pinned: Boolean = false,

    //Whether media attachments should be hidden by default
    val sensitive: Boolean,

    // The detected language for the status, if detected
    val language: String?,

    //If not empty, warning text that should be displayed before the actual content
    // アプリ内部では空文字列はCWなしとして扱う
    // マストドンは「null:CWなし」「空じゃない文字列：CWあり」の2種類
    // Pleromaは「空文字列：CWなし」「空じゃない文字列：CWあり」の2種類
    // Misskeyは「CWなし」「空欄CW」「CWあり」の3通り。空欄CWはパース時に書き換えてしまう
    // Misskeyで投稿が削除された時に変更されるため、val変数にできない
    var spoiler_text: String = "",
    var decoded_spoiler_text: Spannable,

    //	Body of the status; this will contain HTML (remote HTML already sanitized)
    var content: String?,
    var decoded_content: Spannable,

    //Application from which the status was posted
    val application: TootApplication?,

    var custom_emojis: HashMap<String, CustomEmoji>? = null,

    val profile_emojis: HashMap<String, NicoProfileEmoji>?,

    //	The time the status was created
    private val created_at: String?,

    //	null or the ID of the status it replies to
    val in_reply_to_id: EntityId?,

    //	null or the ID of the account it replies to
    val in_reply_to_account_id: EntityId?,

    // null or the reblogged Status
    // 投稿の更新が実装されたのでvarになった
    var reblog: TootStatus? = null,

    //One of: public, unlisted, private, direct
    val visibility: TootVisibility,

    private val misskeyVisibleIds: ArrayList<String>?,

    //	An array of Attachments
    val media_attachments: ArrayList<TootAttachmentLike>?,

    //	An array of Mentions
    var mentions: ArrayList<TootMention>? = null,

    //An array of Tags
    var tags: List<TootTag>? = null,

    // public Spannable decoded_tags;
    var decoded_mentions: Spannable = EMPTY_SPANNABLE,

    var enquete: TootPolls? = null,

    //
    var replies_count: Long? = null,

    var viaMobile: Boolean = false,

    var reactionSet: TootReactionSet? = null,

    var reply: TootStatus?,

    val serviceType: ServiceType,

    val deletedAt: String?,

    val time_deleted_at: Long,

    private var localOnly: Boolean = false,

    var myRenoteId: EntityId? = null,

    // reblog,reply された投稿からその外側を参照する
    var reblogParent: TootStatus? = null,

    // quote toot かどうか。
    var isQuoteToot: Boolean = false,

    private var quote_id: EntityId? = null,

    // このstatusがquoteだった場合、ミュート済みかどうか示すフラグ
    var quote_muted: Boolean = false,

    // Misskey 12.3
    var isPromoted: Boolean = false,
    var isFeatured: Boolean = false,

    // Mastodon 3.5.0
    var time_edited_at: Long = 0L,

    // Mastodon 4.0.0
    var filteredV4: List<TootFilterResult>? = null,

    ///////////////////////////////////////////////////////////////////
    // 以下はentityから取得したデータではなく、アプリ内部で使う

    // アプリ内部で使うワークエリア
    var auto_cw: AutoCW? = null,

    // 会話の流れビューで後から追加する
    var card: TootCard? = null,

    var highlightSound: HighlightWord? = null,
    var highlightSpeech: HighlightWord? = null,
    var highlightAny: HighlightWord? = null,

    val time_created_at: Long,

    ) : TimelineItem() {
    // 会話カラムの場合に使う
    var conversationSummary: TootConversationSummary? = null
    var conversation_main: Boolean = false

    // 投稿元タンスのホスト名
    val originalApDomain: Host
        get() = account.apDomain

    val account: TootAccount
        get() = TootAccountMap.find(accountRef.mapId)

    override fun getOrderId() = _orderId ?: id

    class AutoCW(
        var refActivity: WeakReference<Any>? = null,
        var cellWidth: Int = 0,
        var decodedSpoilerText: Spannable? = null,
        var originalLineCount: Int = 0,
    )

    init {
        decoded_mentions = HTMLDecoder.decodeMentions(parser, this) ?: EMPTY_SPANNABLE

        this.reblog?.reblogParent = this
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
        if (application?.name?.let { muted_app?.contains(it) } == true) {
            return true
        }

        // word mute
        muted_word?.run {
            if (matchShort(decoded_content)) return true
            if (matchShort(decoded_spoiler_text)) return true
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
        matchedFiltersV4: List<TootFilterResult>? = null,
        // フィルタ更新時などは隠すフィルタも含めてチェックする
        checkAll: Boolean = false,
    ) {
        trees ?: return
        val desc = if (accessInfo.isMe(account) || accessInfo.isMe(reblog?.account)) {
            null
        } else {
            val tree = if (checkAll) trees.treeAll else trees.treeWarn
            val m1 = matchKeywordFilter(accessInfo, tree)
            val m2 = reblog?.matchKeywordFilter(accessInfo, tree)

            if (m1.isNullOrEmpty() &&
                m2.isNullOrEmpty() &&
                matchedFiltersV4.isNullOrEmpty()
            ) {
                null
            } else {
                val list = ArrayList<String>()
                fun String.addToList() {
                    if (this.isNotEmpty() && !list.contains(this)) list.add(this)
                }

                fun List<String>.addToList() {
                    for (s in this) s.addToList()
                }

                matchedFiltersV4?.forEach { it.filter?.title?.addToList() }
                m1?.forEach { m ->
                    m.tags?.mapNotNull { (it as? TootFilter)?.title }
                        ?.addToList()
                }
                m2?.forEach { m ->
                    m.tags?.mapNotNull { (it as? TootFilter)?.title }
                        ?.addToList()
                }
                if (list.isEmpty()) {
                    matchedFiltersV4?.forEach { fr ->
                        fr.filter?.keywords?.map { it.keyword }?.addToList()
                    }
                    m1?.forEach { m ->
                        m.word.notEmpty()?.let { list.add(it) }
                    }
                    m2?.forEach { m ->
                        m.word.notEmpty()?.let { list.add(it) }
                    }
                }
                list.joinToString(", ")
            }
        }
        _filteredWord = desc
        reblog?._filteredWord = desc
    }

    fun matchKeywordFilterWithReblog(
        accessInfo: SavedAccount,
        tree: WordTrieTree?,
    ): List<WordTrieTree.Match>? {
        matchKeywordFilter(accessInfo, tree)
            ?.notEmpty()?.let { return it }

        reblog?.matchKeywordFilter(accessInfo, tree)
            ?.notEmpty()?.let { return it }

        return null
    }

    private fun matchKeywordFilter(
        accessInfo: SavedAccount,
        tree: WordTrieTree?,
    ): ArrayList<WordTrieTree.Match>? {
        // フィルタ単語がない、または
        if (tree.isNullOrEmpty() || accessInfo.isMe(account)) return null

        var list: ArrayList<WordTrieTree.Match>? = null

        fun check(t: CharSequence?) {
            if (t.isNullOrEmpty()) return
            val matches = tree.matchList(t) ?: return
            (list ?: ArrayList<WordTrieTree.Match>().also { list = it })
                .addAll(matches)
        }
        check(decoded_spoiler_text)
        check(decoded_content)
        media_attachments?.forEach { check(it.description) }
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

        if (PrefB.bpDontRemoveDeletedToot.value) return false

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
        val isReference: Boolean = false,
    ) {
        val host = Host.parse(hostArg)
    }

    companion object {

        internal val log = LogCategory("TootStatus")

        @Volatile
        internal var muted_app: Set<String>? = null

        @Volatile
        internal var muted_word: WordTrieTree? = null

        @Volatile
        internal var favMuteSet: Set<Acct>? = null

        private val timeMuteData = AtomicLong(0L)
        private const val MUTE_DATA_EXPIRE = 120_000L

        private fun mergeMentions(
            mentions1: List<TootMention>?,
            mentions2: List<TootMention>?,
        ): ArrayList<TootMention>? {
            val size = (mentions1?.size ?: 0) + (mentions2?.size ?: 0)
            if (size == 0) return null
            val dst = ArrayList<TootMention>(size)
            if (mentions1 != null) dst.addAll(mentions1)
            if (mentions2 != null) dst.addAll(mentions2)
            return dst
        }

        private fun statusMisskey(parser: TootParser, src: JsonObject): TootStatus {
            src["_fromStream"] = parser.fromStream
            val apiHost = parser.apiHost
            val misskeyId = src.string("id")
            val id = EntityId.mayDefault(misskeyId)
            var uri = "https://$apiHost/notes/$misskeyId"
            var url = "https://$apiHost/notes/$misskeyId"
            // リモート投稿には uriが含まれる
            src.string("uri")?.let {
                uri = it
                url = it
            }
            val who = parser.account(src.jsonObject("user"))
                ?: error("missing account")
            val accountRef = TootAccountRef.tootAccountRef(parser, who)
            val account = accountRef.get()
            val created_at = src.string("createdAt")
            // 絵文字マップはすぐ後で使うので、最初の方で読んでおく
            val custom_emojis = parseMapOrNull(src.jsonArray("emojis")) {
                CustomEmoji.decodeMisskey(parser.apDomain, parser.apiHost, it)
            }

            // Misskeyは画像毎にNSFWフラグがある。どれか１枚でもNSFWならトゥート全体がNSFWということにする
            var sensitive = src.optBoolean("sensitive")
            val media_attachments = parseListOrNull(
                src.jsonArray("files") ?: src.jsonArray("media") // v11,v10
            ) {
                @Suppress("USELESS_CAST")
                tootAttachment(parser, it) as TootAttachmentLike
            }
            media_attachments?.forEach {
                if ((it as? TootAttachment)?.isSensitive == true) {
                    sensitive = true
                }
            }
            val spoilerRaw = src.string("cw")?.cleanCW()
            val profile_emojis = null
            val options1 = DecodeOptions(
                parser.context,
                parser.linkHelper,
                short = true,
                decodeEmoji = true,
                emojiMapCustom = custom_emojis,
                emojiMapProfile = profile_emojis,
                attachmentList = media_attachments,
                highlightTrie = parser.highlightTrie,
                mentions = null, // MisskeyはMFMをパースし終わるまでメンションが分からない
                authorDomain = accountRef.get()
            )
            // ハイライト検出のためにDecodeOptionsを作り直す？
            val options2 = DecodeOptions(
                parser.context,
                parser.linkHelper,
                short = true,
                decodeEmoji = true,
                emojiMapCustom = custom_emojis,
                emojiMapProfile = profile_emojis,
                attachmentList = media_attachments,
                highlightTrie = parser.highlightTrie,
                mentions = null, // MisskeyはMFMをパースし終わるまでメンションが分からない
                authorDomain = accountRef.get()
            )

            val content = src.string("text")

            val spoiler_text = when {
                spoilerRaw == null -> "" // CWなし
                spoilerRaw.replace('\u0323', ' ').isBlank() ->
                    parser.context.getString(R.string.blank_cw)
                else -> spoilerRaw
            }

            // Markdownのデコード結果からmentionsを読む
            val decoded_content = options1.decodeHTML(content)
            val mentions1 = (decoded_content as? SpannableStringBuilderEx)?.mentions
            var highlightSound = options1.highlightSound
            var highlightSpeech = options1.highlightSpeech
            var highlightAny = options1.highlightAny

            val decoded_spoiler_text = options2.decodeHTML(spoiler_text)
            val mentions2 = (decoded_spoiler_text as? SpannableStringBuilderEx)?.mentions
            if (highlightSound == null) highlightSound = options2.highlightSound
            if (highlightSpeech == null) highlightSpeech = options2.highlightSpeech
            if (highlightAny == null) highlightAny = options2.highlightAny

            val reply = parser.status(src.jsonObject("reply"))
            val reblog = parser.status(src.jsonObject("renote"))

            val isQuoteToot = when (reblog) {
                // 別の投稿を参照していない
                null -> false

                // 別の投稿を参照して、かつ この投稿自体が何かコンテンツを持つなら引用トゥートである
                else -> content?.isNotEmpty() == true ||
                        spoiler_text.isNotEmpty() ||
                        media_attachments?.isNotEmpty() == true ||
                        src.jsonObject("poll") != null
            }

            val card: TootCard? = when {
                // 引用Renoteにプレビューカードをでっちあげる
                reblog != null && isQuoteToot -> {
                    TootCard.tootCard(parser, reblog)
                }
                // 返信にプレビューカードをでっちあげる
                reply != null -> {
                    TootCard.tootCard(parser, reply)
                }
                else -> null
            }

            // めいめいフォークでは myRenoteIdというものがあるらしい
            // https://github.com/mei23/misskey/blob/mei-m544/src/models/note.ts#L384-L394
            // 直近の一つのrenoteのIdを得られるらしい。
            var reblogged = false
            val myRenoteId = EntityId.mayNull(src.string("myRenoteId"))
            if (myRenoteId != null) reblogged = true
            // しかしTLにRenoteが露出してるならそのIDを使う方が賢明であろう
            // 外側ステータスが自分なら、内側ステータスのmyRenoteIdを設定する
            if (reblog != null && parser.linkHelper.cast<SavedAccount>()
                    ?.isMe(account) == true
            ) {
                reblog.myRenoteId = id
                reblog.reblogged = true
            }
            val deletedAt = src.string("deletedAt")
            val localOnly = src.optBoolean("localOnly")

            // お気に入りカラムなどではパース直後に変更することがある
            return TootStatus(
                // "mentionedRemoteUsers" -> "[{"uri":"https:\/\/mastodon.juggler.jp\/users\/tateisu","username":"tateisu","host":"mastodon.juggler.jp"}]"
                // this.decoded_tags = HTMLDecoder.decodeTags( account,status.tags );
                accountRef = accountRef,
                // auto_cw
                // bookmarked
                card = card,
                // conversationSummary
                // conversation_main
                content = content,
                created_at = created_at,
                custom_emojis = custom_emojis,
                // decoded_mentions
                decoded_content = decoded_content,
                decoded_spoiler_text = decoded_spoiler_text,
                deletedAt = deletedAt,
                favourited = src.optBoolean("isFavorited"),
                favourites_count = 0L,
                // filteredV4
                highlightAny = highlightAny,
                highlightSound = highlightSound,
                highlightSpeech = highlightSpeech,
                id = id,
                in_reply_to_account_id = reply?.account?.id,
                in_reply_to_id = EntityId.mayNull(src.string("replyId")),
                isFeatured = src.string("_featuredId_")?.isNotEmpty() == true,
                isPromoted = src.string("_prId_")?.isNotEmpty() == true,
                isQuoteToot = isQuoteToot,
                json = src,
                language = null,
                localOnly = localOnly,
                media_attachments = media_attachments,
                mentions = mergeMentions(mentions1, mentions2),
                misskeyVisibleIds = parseStringArray(src.jsonArray("visibleUserIds")),
                muted = false,
                myRenoteId = myRenoteId,
                parser = parser,
                pinned = parser.pinned,
                // quote_id 下記
                profile_emojis = profile_emojis,
                quote_muted = src.boolean("quote_muted") ?: false,
                // reactionSet 下記
                readerApDomain = parser.apDomain,
                reblog = reblog,
                // reblogParent
                reblogged = reblogged,
                reblogs_count = src.long("renoteCount") ?: 0L,
                replies_count = src.long("repliesCount") ?: 0L,
                reply = reply,
                sensitive = sensitive,
                serviceType = parser.serviceType,
                spoiler_text = spoiler_text,
                tags = parseMisskeyTags(src.jsonArray("tags")),
                time_created_at = parseTime(created_at),
                time_deleted_at = parseTime(deletedAt),
                // time_edited_at
                uri = uri,
                url = url,
                viaMobile = src.optBoolean("viaMobile"),

                application = parseItem(src.jsonObject("app")) {
                    TootApplication(parser, it)
                },
                reactionSet = TootReactionSet.parseMisskey(
                    src.jsonObject("reactions") ?: src.jsonObject("reactionCounts"),
                    src.string("myReaction")
                ),
                quote_id = when {
                    isQuoteToot -> reblog?.id
                    else -> null
                },
                visibility = TootVisibility.parseMisskey(
                    src.string("visibility"),
                    localOnly
                ) ?: TootVisibility.Unknown,
            ).apply {
                // contentを読んだ後にアンケートのデコード
                enquete = TootPolls.parse(
                    parser,
                    TootPollsType.Misskey,
                    this,
                    media_attachments,
                    src.jsonObject("poll"),
                )
            }
        }

        private fun statusNoteStock(parser: TootParser, src: JsonObject): TootStatus {

            src["_fromStream"] = parser.fromStream

            val apTag = APTag(parser, src.jsonArray("tag"))

            val who = parser.account(src.jsonObject("account"))
                ?: error("missing account")
            val accountRef = TootAccountRef.tootAccountRef(parser, who)
            val account = accountRef.get()

            val uri = src.string("id") ?: error("missing uri")
            val url = src.string("url") ?: uri

            val quote = when {
                !parser.decodeQuote -> null
                else -> try {
                    parser.decodeQuote = false
                    parser.status(src.jsonObject("quote"))
                } finally {
                    parser.decodeQuote = true
                }
            }
            val quote_id = quote?.id ?: EntityId.mayNull(src.string("quote_id"))

            val apAttachment = APAttachment(src.jsonArray("attachment"))
            val media_attachments = apAttachment.mediaAttachments.notEmpty()

            val custom_emojis = apTag.emojiList.notEmpty()
            val profile_emojis = apTag.profileEmojiList.notEmpty()
            val created_at = src.string("published")
            val mentions = apTag.mentions

            val options1 = DecodeOptions(
                parser.context,
                parser.linkHelper,
                short = true,
                decodeEmoji = true,
                emojiMapCustom = custom_emojis,
                emojiMapProfile = profile_emojis,
                attachmentList = media_attachments,
                highlightTrie = parser.highlightTrie,
                mentions = mentions,
                authorDomain = account,
                unwrapEmojiImageTag = true, // notestockはカスタム絵文字がimageタグになってる
            )

            // ハイライト検出のためにDecodeOptionsを作り直す？
            val options2 = DecodeOptions(
                parser.context,
                emojiMapCustom = custom_emojis,
                emojiMapProfile = profile_emojis,
                highlightTrie = parser.highlightTrie,
                mentions = mentions,
                authorDomain = account,
                unwrapEmojiImageTag = true, // notestockはカスタム絵文字がimageタグになってる
            )

            val content = src.string("content")
            val decoded_content = options1.decodeHTML(content)
            var highlightSound = options1.highlightSound
            var highlightSpeech = options1.highlightSpeech
            var highlightAny = options1.highlightAny

            val summaryRaw = (src.string("summary") ?: "").cleanCW()
            val spoiler_text = when {
                summaryRaw.isEmpty() -> "" // CWなし
                summaryRaw.isBlank() -> parser.context.getString(R.string.blank_cw)
                else -> summaryRaw
            }
            val decoded_spoiler_text = options2.decodeEmoji(spoiler_text)
            if (highlightSound == null) highlightSound = options2.highlightSound
            if (highlightSpeech == null) highlightSpeech = options2.highlightSpeech
            if (highlightAny == null) highlightAny = options2.highlightAny


            return TootStatus(
                accountRef = accountRef,
                application = null,
                // bookmarked
                card = quote?.let { TootCard.tootCard(parser, it) },
                // conversationSummary
                // conversation_main
                content = content,
                created_at = created_at,
                custom_emojis = custom_emojis,
                decoded_content = decoded_content,
                // decoded_mentions 下記
                decoded_spoiler_text = decoded_spoiler_text,
                deletedAt = null,
                // enquete 下記
                // favourited
                favourites_count = null,
                // filteredV4
                highlightAny = highlightAny,
                highlightSound = highlightSound,
                highlightSpeech = highlightSpeech,
                id = findStatusIdFromUri(uri, url) ?: EntityId.DEFAULT,
                in_reply_to_account_id = null,
                in_reply_to_id = null,
                // isFeatured
                // isPromoted
                //
                isQuoteToot = quote_id != null,
                // localOnly
                json = src,
                language = null,
                media_attachments = media_attachments,
                mentions = mentions,
                misskeyVisibleIds = null,
                muted = false,
                // myRenoteId
                parser = parser,
                pinned = parser.pinned || src.optBoolean("pinned"),
                profile_emojis = profile_emojis,
                quote_id = quote_id,
                quote_muted = src.boolean("quote_muted") ?: false,
                // reactionSet
                readerApDomain = null,
                reblog = null,
                // reblogParent
                // reblogged
                reblogs_count = null,
                replies_count = null,
                reply = null,
                sensitive = src.optBoolean("sensitive"),
                serviceType = parser.serviceType,
                spoiler_text = spoiler_text,
                tags = apTag.hashtags,
                time_created_at = parseTime(created_at),
                time_deleted_at = 0L,
                uri = uri,
                url = url,
                // viaMobile

                visibility = when (src.jsonArray("to")
                    ?.any { it == "https://www.w3.org/ns/activitystreams#Public" }) {
                    true -> TootVisibility.Public
                    else -> TootVisibility.UnlistedHome
                },
            ).apply {
                decoded_mentions =
                    HTMLDecoder.decodeMentions(parser, this)
                        ?: EMPTY_SPANNABLE
                enquete = (src.jsonArray("oneOf") ?: src.jsonArray("anyOf"))?.let {
                    try {
                        TootPolls.tootPolls(
                            TootPollsType.Notestock,
                            parser,
                            this,
                            media_attachments,
                            src,
                            it
                        )
                    } catch (ex: Throwable) {
                        log.e(ex, "TootStatus ctor failed. enquete (NoteStock)")
                        null
                    }
                }
            }
        }

        private fun statusMastodon(parser: TootParser, src: JsonObject): TootStatus {

            src["_fromStream"] = parser.fromStream

            val url = src.string("url") // ブースト等では頻繁にnullになる
            val created_at = src.string("created_at")

            // 絵文字マップはすぐ後で使うので、最初の方で読んでおく
            val custom_emojis = parseMapOrNull(src.jsonArray("emojis")) {
                CustomEmoji.decode(parser.apDomain, parser.apiHost, it)
            }

            val profile_emojis = when (val o = src["profile_emojis"]) {
                is JsonArray -> parseMapOrNull(o) { NicoProfileEmoji(it) }
                is JsonObject -> parseProfileEmoji2(o) { j, k -> NicoProfileEmoji(j, k) }
                else -> null
            }

            val mentions = parseListOrNull(src.jsonArray("mentions")) { TootMention(it) }

            val who = parser.account(src.jsonObject("account"))
                ?: error("missing account")
            val accountRef = TootAccountRef.tootAccountRef(parser, who)
            val account = accountRef.get()

            val readerApDomain: Host?
            val id: EntityId
            val uri: String
            var reblogged = false
            var favourited = false
            var bookmarked = false
            val time_created_at: Long
            var media_attachments: ArrayList<TootAttachmentLike>? = null
            val visibility: TootVisibility
            val sensitive: Boolean
            var filteredV4: List<TootFilterResult>? = null
            when (parser.serviceType) {
                ServiceType.MASTODON -> {
                    readerApDomain = parser.apDomain
                    id = EntityId.mayDefault(src.string("id"))
                    uri = src.string("uri") ?: error("missing uri")
                    reblogged = src.optBoolean("reblogged")
                    favourited = src.optBoolean("favourited")
                    bookmarked = src.optBoolean("bookmarked")

                    time_created_at = parseTime(created_at)
                    media_attachments =
                        parseListOrNull(src.jsonArray("media_attachments")) {
                            tootAttachment(parser,it)
                        }
                    val visibilityString = when {
                        src.boolean("limited") == true -> "limited"
                        else -> src.string("visibility")
                    }
                    visibility = TootVisibility.parseMastodon(visibilityString)
                        ?: TootVisibility.Unknown
                    sensitive = src.optBoolean("sensitive")

                    filteredV4 = TootFilterResult.parseList(src.jsonArray("filtered"))
                }

                ServiceType.TOOTSEARCH -> {
                    readerApDomain = null

                    // 投稿元タンスでのIDを調べる。失敗するかもしれない
                    // XXX: Pleromaだとダメそうな印象
                    uri = src.string("uri") ?: error("missing uri")
                    id = findStatusIdFromUri(uri, url) ?: EntityId.DEFAULT

                    time_created_at = parseTime(created_at)
                    media_attachments = parseList(src.jsonArray("media_attachments")) {
                        tootAttachment(parser, it)
                    }
                    visibility = TootVisibility.Public
                    sensitive = src.optBoolean("sensitive")
                }

                ServiceType.MSP -> {
                    readerApDomain = parser.apDomain

                    // MSPのデータはLTLから呼んだものなので、常に投稿元タンスでのidが得られる
                    id = EntityId.mayDefault(src.string("id"))

                    // MSPだとuriは提供されない。LTL限定なのでURL的なものを作れるはず
                    uri =
                        "https://${account.apiHost}/users/${who.username}/statuses/$id"

                    time_created_at = parseTimeMSP(created_at)
                    media_attachments =
                        TootAttachmentMSP.parseList(src.jsonArray("media_attachments"))
                    visibility = TootVisibility.Public
                    sensitive = src.optInt("sensitive", 0) != 0
                }

                ServiceType.MISSKEY, ServiceType.NOTESTOCK -> error("will not happen")
            }

            val quote = when {
                !parser.decodeQuote -> null
                else -> try {
                    parser.decodeQuote = false
                    parser.status(src.jsonObject("quote"))
                } finally {
                    parser.decodeQuote = true
                }
            }

            val quote_id = quote?.id ?: EntityId.mayNull(src.string("quote_id"))
            val isQuoteToot = quote_id != null
            val quote_muted = src.boolean("quote_muted") ?: false

            // Pinned TL を取得した時にreblogが登場することはないので、reblogについてpinned 状態を気にする必要はない
            // Hostdon QT と通常のreblogが同時に出ることはないので、quoteが既出ならreblogのjsonデータは見ない
            val reblog = quote ?: parser.status(src.jsonObject("reblog"))

            val removeQt = false

            // content
            val content = src.string("content")?.let { sv ->
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

            val options1 = DecodeOptions(
                parser.context,
                parser.linkHelper,
                short = true,
                decodeEmoji = true,
                emojiMapCustom = custom_emojis,
                emojiMapProfile = profile_emojis,
                attachmentList = media_attachments,
                highlightTrie = parser.highlightTrie,
                mentions = mentions,
                authorDomain = account
            )

            val decoded_content = options1.decodeHTML(content)
            var highlightSound = options1.highlightSound
            var highlightSpeech = options1.highlightSpeech
            var highlightAny = options1.highlightAny

            val sv = (src.string("spoiler_text") ?: "").cleanCW()
            val spoiler_text = when {
                sv.isEmpty() -> "" // CWなし
                sv.isBlank() -> parser.context.getString(R.string.blank_cw)
                else -> sv
            }

            // ハイライト検出のためにDecodeOptionsを作り直す？
            val options2 = DecodeOptions(
                parser.context,
                emojiMapCustom = custom_emojis,
                emojiMapProfile = profile_emojis,
                highlightTrie = parser.highlightTrie,
                mentions = mentions,
                authorDomain = account
            )
            val decoded_spoiler_text = options2.decodeEmoji(spoiler_text)
            if (highlightSound == null) highlightSound = options2.highlightSound
            if (highlightSpeech == null) highlightSpeech = options2.highlightSpeech
            if (highlightAny == null) highlightAny = options2.highlightAny

            return TootStatus(
                accountRef = accountRef,
                bookmarked = bookmarked,
                // card 下記
                // conversationSummary
                // conversation_main
                content = content,
                created_at = created_at,
                custom_emojis = custom_emojis,
                decoded_content = decoded_content,
                // decoded_mentions
                decoded_spoiler_text = decoded_spoiler_text,
                deletedAt = null,
                // enquete 下記
                favourited = favourited,
                favourites_count = src.long("favourites_count"),
                filteredV4 = filteredV4,
                highlightAny = highlightAny,
                highlightSound = highlightSound,
                highlightSpeech = highlightSpeech,
                id = id,
                in_reply_to_account_id = EntityId.mayNull(src.string("in_reply_to_account_id")),
                in_reply_to_id = EntityId.mayNull(src.string("in_reply_to_id")),
                // isFeatured
                // isPromoted
                isQuoteToot = isQuoteToot,
                json = src,
                language = src.string("language")?.notEmpty(),
                // localOnly
                media_attachments = media_attachments,
                mentions = mentions,
                misskeyVisibleIds = null,
                muted = src.optBoolean("muted"),
                // myRenoteId
                parser = parser,
                pinned = parser.pinned || src.optBoolean("pinned"),
                profile_emojis = profile_emojis,
                quote_id = quote_id,
                quote_muted = quote_muted,
                // reactionSet 下記
                readerApDomain = readerApDomain,
                reblog = reblog,
                reblogged = reblogged,
                reblogs_count = src.long("reblogs_count"),
                replies_count = src.long("replies_count"),
                reply = null,
                sensitive = sensitive,
                serviceType = parser.serviceType,
                spoiler_text = spoiler_text,
                tags = TootTag.parseListOrNull(parser, src.jsonArray("tags")),
                time_created_at = time_created_at,
                time_deleted_at = 0L,
                time_edited_at = parseTime(src.string("edited_at")),
                uri = uri,
                url = url,
                // viaMobile
                visibility = visibility,

                reactionSet = TootReactionSet.parseFedibird(
                    src.jsonArray("emoji_reactions")
                        ?: src.jsonObject("pleroma")?.jsonArray("emoji_reactions")
                ),
                application = parseItem(src.jsonObject("application")) {
                    TootApplication(parser, it)
                },
            ).apply {
                enquete = try {
                    src.string("enquete")?.notEmpty()?.let {
                        TootPolls.tootPolls(
                            TootPollsType.FriendsNico,
                            parser,
                            this,
                            media_attachments,
                            it.decodeJsonObject(),
                        )
                    } ?: src.jsonObject("poll")?.let {
                        TootPolls.tootPolls(
                            TootPollsType.Mastodon,
                            parser,
                            this,
                            media_attachments,
                            it,
                        )
                    }
                } catch (ex: Throwable) {
                    log.e(ex, "TootStatus ctor failed. enquete")
                    null
                }

                // 2.6.0からステータスにもカード情報が含まれる
                card = parseItem(src.jsonObject("card")) { TootCard.tootCard(it) }
                if (card == null && quote != null) {
                    // 引用Renoteにプレビューカードをでっちあげる
                    card = TootCard.tootCard(parser, quote)
                    // content中のQTの表現が四角括弧の有無とか色々あるみたいだし
                    // 選択してコピーのことを考えたらむしろ削らない方が良い気がしてきた
                    // removeQt = ! PrefB.bpDontShowPreviewCard(Pref.pref(parser.context))
                }

                decoded_mentions =
                    HTMLDecoder.decodeMentions(parser, this)
                        ?: EMPTY_SPANNABLE
            }
        }

        fun tootStatus(parser: TootParser, src: JsonObject): TootStatus =
            when (parser.serviceType) {
                ServiceType.MISSKEY -> statusMisskey(parser, src)
                ServiceType.NOTESTOCK -> statusNoteStock(parser, src)
                else -> statusMastodon(parser, src)
            }

        fun updateMuteData(force: Boolean = false) {
            val now = SystemClock.elapsedRealtime()
            if (force || muted_app == null || muted_word == null ||
                now >= timeMuteData.get() + MUTE_DATA_EXPIRE
            ) {
                timeMuteData.set(now)
                muted_app = daoMutedApp.nameSet()
                muted_word = daoMutedWord.nameSet()
                favMuteSet = daoFavMute.acctSet()
            }
        }

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

        // fedibird ステータスの参照のURL
        private val reStatusWithReference =
            """\Ahttps://([^/]+)/@(\w+)/([^?#/\s]+)/references(?:\z|[?#])"""
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

            // https://fedibird.com/@noellabo/108730353756004469/references
            var m = reStatusWithReference.matcher(this)
            if (m.find()) {
                return FindStatusIdFromUrlResult(
                    EntityId(m.groupEx(3)!!),
                    m.groupEx(1)!!,
                    this,
                    isReference = true,
                )
            }

            // https://mastodon.juggler.jp/@SubwayTooter/(status_id)
            m = reStatusPage.matcher(this)
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

        private val reDate = """\A(\d+\D+\d+\D+\d+)\z"""
            .asciiRegex()

        private val reTime = """\A(\d+)\D+(\d+)\D+(\d+)\D+(\d+)\D+(\d+)\D+(\d+)(?:\D+(\d+))?"""
            .asciiRegex()

        private val reTimeWithZone =
            """\A(\d+)\D+(\d+)\D+(\d+)\D+(\d+)\D+(\d+)\D+(\d+)(?:\D+(\d+))?(?:(Z|[+-])(\d+):?(\d*))?"""
                .asciiRegex()

        private val reMSPTime = """\A(\d+)\D+(\d+)\D+(\d+)\D+(\d+)\D+(\d+)\D+(\d+)"""
            .asciiRegex()

        private val tzUtc = TimeZone.getTimeZone("UTC")

        @Throws(Throwable::class)
        fun parseTimeUtc(strTime: String): Long {
            val gv = reTime.find(strTime)?.groupValues
                ?: error("time format not match.")
            return GregorianCalendar.getInstance()
                .apply {
                    timeZone = tzUtc
                    set(
                        gv.elementAtOrNull(1)?.toIntOrNull() ?: 1,
                        (gv.elementAtOrNull(2)?.toIntOrNull() ?: 1) - 1,
                        gv.elementAtOrNull(3)?.toIntOrNull() ?: 1,
                        gv.elementAtOrNull(4)?.toIntOrNull() ?: 0,
                        gv.elementAtOrNull(5)?.toIntOrNull() ?: 0,
                        gv.elementAtOrNull(6)?.toIntOrNull() ?: 0,
                    )
                    set(Calendar.MILLISECOND, (gv.elementAtOrNull(7)?.toIntOrNull() ?: 0))
                }.timeInMillis
        }

        // ISO-8601の Z,[+-]HH:mm,[+-]HHmm,[+-]HH 部分を解釈してオフセット(ミリ秒)を返す
        @Throws(Throwable::class)
        fun parseTimeZoneOffset(sign: String?, hArg: String?, mArg: String?): Long {
            val minutes = when {
                sign == null || sign == "Z" || hArg == null || hArg.isEmpty() -> {
                    // Z or missing hour part
                    0
                }
                mArg != null && mArg.isNotEmpty() -> {
                    // HH:mm or H:m
                    val h = hArg.toInt()
                    val m = mArg.toInt()
                    h * 60 + m
                }
                hArg.length >= 3 -> {
                    // HHmm or Hmm
                    val h = hArg.substring(0, hArg.length - 2).toInt()
                    val m = hArg.substring(hArg.length - 2).toInt()
                    h * 60 + m
                }
                else -> {
                    // HH or H
                    val h = hArg.toInt()
                    h * 60
                }
            }
            return minutes.toLong() * 60000L * (if (sign == "-") -1L else 1L)
        }

        @Throws(Throwable::class)
        fun parseTimeIso8601(strTime: String): Long {
            val gv = reTimeWithZone.find(strTime)?.groupValues
                ?: error("time format not match.")
            return GregorianCalendar.getInstance()
                .apply {
                    timeZone = tzUtc
                    set(
                        gv.elementAtOrNull(1)?.toIntOrNull() ?: 1,
                        (gv.elementAtOrNull(2)?.toIntOrNull() ?: 1) - 1,
                        gv.elementAtOrNull(3)?.toIntOrNull() ?: 1,
                        gv.elementAtOrNull(4)?.toIntOrNull() ?: 0,
                        gv.elementAtOrNull(5)?.toIntOrNull() ?: 0,
                        gv.elementAtOrNull(6)?.toIntOrNull() ?: 0,
                    )
                    set(Calendar.MILLISECOND, (gv.elementAtOrNull(7)?.toIntOrNull() ?: 0))
                }.timeInMillis -
                    parseTimeZoneOffset(
                        gv.elementAtOrNull(8),
                        gv.elementAtOrNull(9),
                        gv.elementAtOrNull(10),
                    )
        }

        // 時刻を解釈してエポック秒(ミリ単位)を返す
        // 解釈に失敗すると0Lを返す
        fun parseTime(strTime: String?): Long {
            if (strTime.isNullOrBlank()) return 0L

            // last_status_at などでは YYYY-MM-DD になることがある
            reDate.find(strTime)?.groupValues?.let { gv ->
                return parseTime("${gv[1]}T00:00:00.000Z")
            }

            // タイムゾーン指定を考慮したパース
            try {
                return parseTimeIso8601(strTime)
            } catch (ex: Throwable) {
                log.w(ex, "parseTime2 failed. $strTime")
            }

            // 古い処理にフォールバック
            try {
                return parseTimeUtc(strTime)
            } catch (ex: Throwable) {
                log.w(ex, "parseTime1 failed. $strTime")
            }

            return 0L
        }

        private fun parseTimeMSP(strTime: String?): Long {
            if (strTime?.isNotBlank() != true) return 0L
            try {
                val gv = reMSPTime.find(strTime)?.groupValues
                    ?: error("time format not match.")
                return GregorianCalendar.getInstance()
                    .apply {
                        timeZone = tzUtc
                        set(
                            gv.elementAtOrNull(1)?.toIntOrNull() ?: 1,
                            (gv.elementAtOrNull(2)?.toIntOrNull() ?: 1) - 1,
                            gv.elementAtOrNull(3)?.toIntOrNull() ?: 1,
                            gv.elementAtOrNull(4)?.toIntOrNull() ?: 0,
                            gv.elementAtOrNull(5)?.toIntOrNull() ?: 0,
                            gv.elementAtOrNull(6)?.toIntOrNull() ?: 0,
                        )
                        set(Calendar.MILLISECOND, 500)
                    }.timeInMillis
            } catch (ex: Throwable) {
                log.w(ex, "parseTimeMSP failed. src=$strTime")
            }
            return 0L
        }

        @SuppressLint("SimpleDateFormat")
        val dateFormatFull = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

        @SuppressLint("SimpleDateFormat")
        val date_format2 = SimpleDateFormat("yyyy-MM-dd")

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

            if (bAllowRelative && PrefB.bpRelativeTimestamp.value) {

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

            return formatDate(t, dateFormatFull, omitZeroSecond = false, omitYear = false)
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
                else -> formatDate(start, dateFormatFull, omitZeroSecond = true, omitYear = true)
            }
            val strEnd = when {
                end <= 0L -> ""
                allDay -> formatDate(end, date_format2, omitZeroSecond = false, omitYear = true)
                else -> formatDate(end, dateFormatFull, omitZeroSecond = true, omitYear = true)
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

        private val supplyEditHistoryKeys = arrayOf(
            "id",
            "uri",
            "url",
            "visibility",
        )

        // 編集履歴のデータはTootStatusとしては不足があるので、srcを元に補う
        fun supplyEditHistory(array: JsonArray?, src: JsonObject?) {
            src ?: return
            array?.objectList()?.forEach {
                for (key in supplyEditHistoryKeys) {
                    if (it.containsKey(key)) continue
                    it[key] = src[key]
                }
            }
        }
    }
}
