package jp.juggler.subwaytooter.column

import android.content.Context
import android.util.SparseArray
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.AppState
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.DuplicateMap
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.columnviewholder.ColumnViewHolder
import jp.juggler.subwaytooter.pref.PrefI
import jp.juggler.subwaytooter.streaming.StreamCallback
import jp.juggler.subwaytooter.streaming.StreamStatus
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.daoSavedAccount
import jp.juggler.subwaytooter.util.BucketList
import jp.juggler.subwaytooter.util.ScrollPosition
import jp.juggler.util.data.*
import jp.juggler.util.ui.attrColor
import okhttp3.Handshake
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class ColumnPagingType { Default, Cursor, Offset, None, }

enum class ProfileTab(val id: Int, val ct: ColumnType) {
    Status(0, ColumnType.TabStatus),
    Following(1, ColumnType.TabFollowing),
    Followers(2, ColumnType.TabFollowers)
}

enum class HeaderType(val viewType: Int) {
    Profile(1), Search(2), Instance(3), Filter(4), ProfileDirectory(5),
}

class Column(
    val appState: AppState,
    val context: Context,
    val accessInfo: SavedAccount,
    typeId: Int,
    val columnId: String,
) {
    companion object {
        internal const val LOOP_TIMEOUT = 10000L
        internal const val LOOP_READ_ENOUGH = 30 // フィルタ後のデータ数がコレ以上ならループを諦めます
        internal const val RELATIONSHIP_LOAD_STEP = 40
        internal const val ACCT_DB_STEP = 100
        internal const val MISSKEY_HASHTAG_LIMIT = 30
        internal const val HASHTAG_ELLIPSIZE = 26

        val typeMap: SparseArray<ColumnType> = SparseArray()

        internal var showOpenSticker = false

        internal const val QUICK_FILTER_ALL = 0
        internal const val QUICK_FILTER_MENTION = 1
        internal const val QUICK_FILTER_FAVOURITE = 2
        internal const val QUICK_FILTER_BOOST = 3
        internal const val QUICK_FILTER_FOLLOW = 4
        internal const val QUICK_FILTER_REACTION = 5
        internal const val QUICK_FILTER_VOTE = 6
        internal const val QUICK_FILTER_POST = 7

        fun loadAccount(src: JsonObject): SavedAccount {
            val account_db_id = src.long(ColumnEncoder.KEY_ACCOUNT_ROW_ID) ?: -1L
            return if (account_db_id > 0) {
                daoSavedAccount.loadAccount(account_db_id)
                    ?: error("missing account for db_id $account_db_id")
            } else {
                SavedAccount.na
            }
        }

        // private val channelIdSeed = AtomicInteger(0)

        // より古いデータの取得に使う
        internal val reMaxId = """[&?]max_id=([^&?;\s]+)""".asciiPattern()

        // より新しいデータの取得に使う
        val reMinId = """[&?](min_id|since_id)=([^&?;\s]+)""".asciiPattern()

        val COLUMN_REGEX_FILTER_DEFAULT: (CharSequence?) -> Boolean = { false }

        var defaultColorHeaderBg = 0
        var defaultColorHeaderName = 0
        var defaultColorHeaderPageNumber = 0
        var defaultColorContentBg = 0
        var defaultColorContentAcct = 0
        var defaultColorContentText = 0

        fun reloadDefaultColor(activity: AppCompatActivity) {

            defaultColorHeaderBg = PrefI.ipCcdHeaderBg.value.notZero()
                ?: activity.attrColor(R.attr.color_column_header)

            defaultColorHeaderName = PrefI.ipCcdHeaderFg.value.notZero()
                ?: activity.attrColor(R.attr.colorColumnHeaderName)

            defaultColorHeaderPageNumber = PrefI.ipCcdHeaderFg.value.notZero()
                ?: activity.attrColor(R.attr.colorColumnHeaderPageNumber)

            defaultColorContentBg = PrefI.ipCcdContentBg.value
            // may zero

            defaultColorContentAcct = PrefI.ipCcdContentAcct.value.notZero()
                ?: activity.attrColor(R.attr.colorTimeSmall)

            defaultColorContentText = PrefI.ipCcdContentText.value.notZero()
                ?: activity.attrColor(R.attr.colorTextContent)
        }

        private val internalIdSeed = AtomicInteger(0)
    }

    // カラムオブジェクトの識別に使うID。
    val internalId = internalIdSeed.incrementAndGet()

    val type = ColumnType.parse(typeId)

    internal var dontClose = false

    internal var withAttachment = false
    internal var withHighlight = false
    internal var dontShowBoost = false
    internal var dontShowReply = false

    internal var dontShowNormalToot = false
    internal var dontShowNonPublicToot = false

    internal var dontShowFavourite = false // 通知カラムのみ
    internal var dontShowFollow = false // 通知カラムのみ
    internal var dontShowReaction = false // 通知カラムのみ
    internal var dontShowVote = false // 通知カラムのみ

    internal var quickFilter = QUICK_FILTER_ALL

    internal var showMediaDescription = true

    @Volatile
    internal var dontStreaming = false

    internal var dontAutoRefresh = false
    internal var hideMediaDefault = false
    internal var systemNotificationNotRelated = false
    internal var instanceLocal = false

    internal var enableSpeech = false
    internal var useOldApi = false

    internal var regexText: String = ""

    internal var headerBgColor = 0
    internal var headerFgColor = 0
    internal var columnBgColor = 0
    internal var acctColor = 0
    internal var contentColor = 0
    internal var columnBgImage = ""
    internal var columnBgImageAlpha = 1f

    internal var profileTab = ProfileTab.Status

    internal var statusId: EntityId? = null
    internal var originalStatus: JsonObject? = null

    // プロフカラムではアカウントのID。リストカラムではリストのID
    internal var profileId: EntityId? = null

    internal var searchQuery = ""
    internal var searchResolve = false
    internal var remoteOnly = false
    internal var instanceUri = ""
    internal var hashtag = ""
    internal var hashtagAny = ""
    internal var hashtagAll = ""
    internal var hashtagNone = ""
    internal var hashtagAcct = ""
    internal var aggStatusLimit = 400

    internal var languageFilter: JsonObject? = null

    // 告知のリスト
    internal var announcements: MutableList<TootAnnouncement>? = null

    // 表示中の告知
    internal var announcementId: EntityId? = null

    // 告知を閉じた時刻, 0なら閉じていない
    internal var announcementHideTime = 0L

    // 告知データを更新したタイミング
    internal var announcementUpdated = 0L

    // プロフカラムでのアカウント情報
    @Volatile
    internal var whoAccount: TootAccountRef? = null

    // プロフカラムでのfeatured tag 情報(Mastodon3.3.0)
    @Volatile
    internal var whoFeaturedTags: List<TootTag>? = null

    // リストカラムでのリスト情報
    @Volatile
    internal var listInfo: TootList? = null

    // アンテナカラムでのリスト情報
    @Volatile
    internal var antennaInfo: MisskeyAntenna? = null

    // 「インスタンス情報」カラムに表示するインスタンス情報
    // (SavedAccount中のインスタンス情報とは異なるので注意)
    internal var instanceInformation: TootInstance? = null
    internal var handshake: Handshake? = null

    internal var scrollSave: ScrollPosition? = null
    var lastViewingItemId: EntityId? = null

    internal val isDispose = AtomicBoolean()

    @Volatile
    internal var bFirstInitialized = false

    var filterReloadRequired = false

    //////////////////////////////////////////////////////////////////////////////////////

    // カラムを閉じた後のnotifyDataSetChangedのタイミングで、add/removeされる順序が期待通りにならないので
    // 参照を１つだけ持つのではなく、リストを保持して先頭の要素を使うことにする

    val listViewHolder = LinkedList<ColumnViewHolder>()

    //////////////////////////////////////////////////////////////////////////////////////

    internal var lastTask: ColumnTask? = null

    @Volatile
    internal var bInitialLoading: Boolean = false

    @Volatile
    internal var bRefreshLoading: Boolean = false

    internal var mInitialLoadingError: String = ""
    internal var mRefreshLoadingError: String = ""
    internal var mRefreshLoadingErrorTime: Long = 0L
    internal var mRefreshLoadingErrorPopupState: Int = 0

    internal var taskProgress: String? = null

    internal val listData = BucketList<TimelineItem>()
    internal val duplicateMap = DuplicateMap()

    @Volatile
    var columnRegexFilter = COLUMN_REGEX_FILTER_DEFAULT

    @Volatile
    var keywordFilterTrees: FilterTrees? = null

    @Volatile
    var favMuteSet: Set<Acct>? = null

    @Volatile
    var highlightTrie: WordTrieTree? = null

    // タイムライン中のデータの始端と終端
    // misskeyは
    internal var idRecent: EntityId? = null
    internal var idOld: EntityId? = null
    internal var offsetNext: Int = 0
    internal var pagingType: ColumnPagingType = ColumnPagingType.Default

    var bRefreshingTop: Boolean = false

    // ListViewの表示更新が追いつかないとスクロール位置が崩れるので
    // 一定時間より短期間にはデータ更新しないようにする
    val lastShowStreamData = AtomicLong(0L)
    val streamDataQueue = ConcurrentLinkedQueue<TimelineItem>()

    @Volatile
    var bPutGap: Boolean = false

    var cacheHeaderDesc: String? = null

    // DMカラム更新時に新APIの利用に成功したなら真
    internal var useConversationSummaries = false

    // DMカラムのストリーミングイベントで新形式のイベントを利用できたなら真
    internal var useConversationSummaryStreaming = false

    ////////////////////////////////////////////////////////////////

    val procMergeStreamingMessage =
        Runnable { this@Column.mergeStreamingMessage() }

    val streamCallback = object : StreamCallback {
        override fun onStreamStatusChanged(status: StreamStatus) =
            this@Column.onStreamStatusChanged(status)

        override fun onTimelineItem(item: TimelineItem, channelId: String?, stream: JsonArray?) =
            this@Column.onStreamingTimelineItem(item)

        override fun onEmojiReactionNotification(notification: TootNotification) =
            runOnMainLooperForStreamingEvent {
                this@Column.updateEmojiReactionByApiResponse(
                    notification.status
                )
            }

        override fun onEmojiReactionEvent(reaction: TootReaction) =
            runOnMainLooperForStreamingEvent { this@Column.updateEmojiReactionByEvent(reaction) }

        override fun onNoteUpdated(ev: MisskeyNoteUpdate, channelId: String?) =
            runOnMainLooperForStreamingEvent { this@Column.onMisskeyNoteUpdated(ev) }

        override fun onAnnouncementUpdate(item: TootAnnouncement) =
            runOnMainLooperForStreamingEvent { this@Column.onAnnouncementUpdate(item) }

        override fun onAnnouncementDelete(id: EntityId) =
            runOnMainLooperForStreamingEvent { this@Column.onAnnouncementDelete(id) }

        override fun onAnnouncementReaction(reaction: TootReaction) =
            runOnMainLooperForStreamingEvent { this@Column.onAnnouncementReaction(reaction) }
    }

    // create from column spec
    internal constructor(
        appState: AppState,
        accessInfo: SavedAccount,
        type: Int,
        params: Array<out Any>,
    ) : this(
        appState = appState,
        context = appState.context,
        accessInfo = accessInfo,
        typeId = type,
        columnId = ColumnEncoder.generateColumnId()
    ) {
        ColumnSpec.decode(this, params)
    }

    internal constructor(appState: AppState, src: JsonObject) : this(
        appState,
        appState.context,
        loadAccount(src),
        src.optInt(ColumnEncoder.KEY_TYPE),
        ColumnEncoder.decodeColumnId(src)
    ) {
        ColumnEncoder.decode(this, src)
    }

    override fun hashCode(): Int = internalId

    override fun equals(other: Any?): Boolean = this === other

    internal fun dispose() {
        isDispose.set(true)
        appState.streamManager.updateStreamingColumns()

        for (vh in listViewHolder) {
            try {
                vh.listView.adapter = null
            } catch (ignored: Throwable) {
            }
        }
    }

    init {
        ColumnEncoder.registerColumnId(columnId, this)
    }
}
