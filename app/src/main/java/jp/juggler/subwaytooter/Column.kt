package jp.juggler.subwaytooter

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.os.SystemClock
import android.util.SparseArray
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.notification.PollingWorker
import jp.juggler.subwaytooter.streaming.*
import jp.juggler.subwaytooter.table.*
import jp.juggler.subwaytooter.util.BucketList
import jp.juggler.subwaytooter.util.ScrollPosition
import jp.juggler.util.*
import okhttp3.Handshake
import java.io.File
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.ArrayList
import kotlin.math.max

class Column(
    val app_state: AppState,
    val context: Context,
    val access_info: SavedAccount,
    typeId: Int,
    val column_id: String
) {
    companion object {

        internal val log = LogCategory("Column")

        private const val DIR_BACKGROUND_IMAGE = "columnBackground"

        internal const val READ_LIMIT = 80 // API側の上限が80です。ただし指定しても40しか返ってこないことが多い
        internal const val LOOP_TIMEOUT = 10000L
        internal const val LOOP_READ_ENOUGH = 30 // フィルタ後のデータ数がコレ以上ならループを諦めます
        internal const val RELATIONSHIP_LOAD_STEP = 40
        internal const val ACCT_DB_STEP = 100

        internal const val MISSKEY_HASHTAG_LIMIT = 30

        // ステータスのリストを返すAPI
        internal const val PATH_DIRECT_MESSAGES = "/api/v1/timelines/direct?limit=$READ_LIMIT"
        internal const val PATH_DIRECT_MESSAGES2 = "/api/v1/conversations?limit=$READ_LIMIT"

        internal const val PATH_FAVOURITES = "/api/v1/favourites?limit=$READ_LIMIT"
        internal const val PATH_BOOKMARKS = "/api/v1/bookmarks?limit=$READ_LIMIT"

        // アカウントのリストを返すAPI
        internal const val PATH_ACCOUNT_FOLLOWING =
            "/api/v1/accounts/%s/following?limit=$READ_LIMIT" // 1:account_id
        internal const val PATH_ACCOUNT_FOLLOWERS =
            "/api/v1/accounts/%s/followers?limit=$READ_LIMIT" // 1:account_id
        internal const val PATH_MUTES = "/api/v1/mutes?limit=$READ_LIMIT"
        internal const val PATH_BLOCKS = "/api/v1/blocks?limit=$READ_LIMIT"
        internal const val PATH_FOLLOW_REQUESTS = "/api/v1/follow_requests?limit=$READ_LIMIT"
        internal const val PATH_FOLLOW_SUGGESTION = "/api/v1/suggestions?limit=$READ_LIMIT"
        internal const val PATH_FOLLOW_SUGGESTION2 = "/api/v2/suggestions?limit=$READ_LIMIT"
        internal const val PATH_ENDORSEMENT = "/api/v1/endorsements?limit=$READ_LIMIT"

        internal const val PATH_PROFILE_DIRECTORY = "/api/v1/directory?limit=$READ_LIMIT"

        internal const val PATH_BOOSTED_BY =
            "/api/v1/statuses/%s/reblogged_by?limit=$READ_LIMIT" // 1:status_id
        internal const val PATH_FAVOURITED_BY =
            "/api/v1/statuses/%s/favourited_by?limit=$READ_LIMIT" // 1:status_id
        internal const val PATH_LIST_MEMBER = "/api/v1/lists/%s/accounts?limit=$READ_LIMIT"

        // 他のリストを返すAPI
        internal const val PATH_REPORTS = "/api/v1/reports?limit=$READ_LIMIT"
        internal const val PATH_NOTIFICATIONS = "/api/v1/notifications?limit=$READ_LIMIT"
        internal const val PATH_DOMAIN_BLOCK = "/api/v1/domain_blocks?limit=$READ_LIMIT"
        internal const val PATH_LIST_LIST = "/api/v1/lists?limit=$READ_LIMIT"
        internal const val PATH_SCHEDULED_STATUSES = "/api/v1/scheduled_statuses?limit=$READ_LIMIT"

        // リストではなくオブジェクトを返すAPI
        internal const val PATH_STATUSES = "/api/v1/statuses/%s" // 1:status_id
        internal const val PATH_STATUSES_CONTEXT = "/api/v1/statuses/%s/context" // 1:status_id
        // search args 1: query(urlencoded) , also, append "&resolve=1" if resolve non-local accounts

        const val PATH_FILTERS = "/api/v1/filters"

        const val PATH_MISSKEY_PROFILE_FOLLOWING = "/api/users/following"
        const val PATH_MISSKEY_PROFILE_FOLLOWERS = "/api/users/followers"
        const val PATH_MISSKEY_PROFILE_STATUSES = "/api/users/notes"

        const val PATH_MISSKEY_MUTES = "/api/mute/list"
        const val PATH_MISSKEY_BLOCKS = "/api/blocking/list"
        const val PATH_MISSKEY_FOLLOW_REQUESTS = "/api/following/requests/list"
        const val PATH_MISSKEY_FOLLOW_SUGGESTION = "/api/users/recommendation"
        const val PATH_MISSKEY_FAVORITES = "/api/i/favorites"

        internal const val KEY_ACCOUNT_ROW_ID = "account_id"
        internal const val KEY_TYPE = "type"
        internal const val KEY_COLUMN_ID = "column_id"
        internal const val KEY_DONT_CLOSE = "dont_close"
        private const val KEY_WITH_ATTACHMENT = "with_attachment"
        private const val KEY_WITH_HIGHLIGHT = "with_highlight"
        private const val KEY_DONT_SHOW_BOOST = "dont_show_boost"
        private const val KEY_DONT_SHOW_FAVOURITE = "dont_show_favourite"
        private const val KEY_DONT_SHOW_FOLLOW = "dont_show_follow"
        private const val KEY_DONT_SHOW_REPLY = "dont_show_reply"
        private const val KEY_DONT_SHOW_REACTION = "dont_show_reaction"
        private const val KEY_DONT_SHOW_VOTE = "dont_show_vote"
        private const val KEY_DONT_SHOW_NORMAL_TOOT = "dont_show_normal_toot"
        private const val KEY_DONT_SHOW_NON_PUBLIC_TOOT = "dont_show_non_public_toot"
        private const val KEY_DONT_STREAMING = "dont_streaming"
        private const val KEY_DONT_AUTO_REFRESH = "dont_auto_refresh"
        private const val KEY_HIDE_MEDIA_DEFAULT = "hide_media_default"
        private const val KEY_SYSTEM_NOTIFICATION_NOT_RELATED = "system_notification_not_related"
        private const val KEY_INSTANCE_LOCAL = "instance_local"

        private const val KEY_ENABLE_SPEECH = "enable_speech"
        private const val KEY_USE_OLD_API = "use_old_api"
        private const val KEY_LAST_VIEWING_ITEM = "lastViewingItem"
        private const val KEY_QUICK_FILTER = "quickFilter"

        private const val KEY_REGEX_TEXT = "regex_text"
        private const val KEY_LANGUAGE_FILTER = "language_filter"

        private const val KEY_HEADER_BACKGROUND_COLOR = "header_background_color"
        private const val KEY_HEADER_TEXT_COLOR = "header_text_color"
        private const val KEY_COLUMN_BACKGROUND_COLOR = "column_background_color"
        private const val KEY_COLUMN_ACCT_TEXT_COLOR = "column_acct_text_color"
        private const val KEY_COLUMN_CONTENT_TEXT_COLOR = "column_content_text_color"
        private const val KEY_COLUMN_BACKGROUND_IMAGE = "column_background_image"
        private const val KEY_COLUMN_BACKGROUND_IMAGE_ALPHA = "column_background_image_alpha"

        private const val KEY_PROFILE_ID = "profile_id"
        private const val KEY_PROFILE_TAB = "tab"
        private const val KEY_STATUS_ID = "status_id"

        private const val KEY_HASHTAG = "hashtag"
        private const val KEY_HASHTAG_ANY = "hashtag_any"
        private const val KEY_HASHTAG_ALL = "hashtag_all"
        private const val KEY_HASHTAG_NONE = "hashtag_none"
        private const val KEY_HASHTAG_ACCT = "hashtag_acct"

        private const val KEY_SEARCH_QUERY = "search_query"
        private const val KEY_SEARCH_RESOLVE = "search_resolve"
        private const val KEY_INSTANCE_URI = "instance_uri"

        private const val KEY_REMOTE_ONLY = "remoteOnly"

        internal const val KEY_COLUMN_ACCESS_ACCT = "column_access"
        internal const val KEY_COLUMN_ACCESS_STR = "column_access_str"
        internal const val KEY_COLUMN_ACCESS_COLOR = "column_access_color"
        internal const val KEY_COLUMN_ACCESS_COLOR_BG = "column_access_color_bg"
        internal const val KEY_COLUMN_NAME = "column_name"
        internal const val KEY_OLD_INDEX = "old_index"

        internal const val KEY_ANNOUNCEMENT_HIDE_TIME = "announcementHideTime"

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

        internal const val HASHTAG_ELLIPSIZE = 26


        @Suppress("UNCHECKED_CAST")
        private inline fun <reified T> getParamAt(params: Array<out Any>, idx: Int): T {
            return params[idx] as T
        }

        private fun getParamEntityId(
            params: Array<out Any>,
            @Suppress("SameParameterValue") idx: Int
        ): EntityId =
            when (val o = params[idx]) {
                is EntityId -> o
                is String -> EntityId(o)
                else -> error("getParamEntityId [$idx] bad type. $o")
            }

        private fun getParamString(params: Array<out Any>, idx: Int): String =
            when (val o = params[idx]) {
                is String -> o
                is EntityId -> o.toString()
                is Host -> o.ascii
                is Acct -> o.ascii
                else -> error("getParamString [$idx] bad type. $o")
            }

        @Suppress("UNCHECKED_CAST")
        private inline fun <reified T> getParamAtNullable(params: Array<out Any>, idx: Int): T? {
            if (idx >= params.size) return null
            return params[idx] as T
        }

        fun loadAccount(context: Context, src: JsonObject): SavedAccount {
            val account_db_id = src.long(KEY_ACCOUNT_ROW_ID) ?: -1L
            return if (account_db_id >= 0) {
                SavedAccount.loadAccount(context, account_db_id)
                    ?: throw RuntimeException("missing account")
            } else {
                SavedAccount.na
            }

        }

        // private val channelIdSeed = AtomicInteger(0)

        // より古いデータの取得に使う
        internal val reMaxId = """[&?]max_id=([^&?;\s]+)""".asciiPattern()

        // より新しいデータの取得に使う
        private val reMinId = """[&?](min_id|since_id)=([^&?;\s]+)""".asciiPattern()

        val COLUMN_REGEX_FILTER_DEFAULT: (CharSequence?) -> Boolean = { false }

        fun onFiltersChanged(context: Context, access_info: SavedAccount) {

            TootTaskRunner(context, progress_style = TootTaskRunner.PROGRESS_NONE).run(access_info,
                object : TootTask {

                    var filter_list: ArrayList<TootFilter>? = null

                    override suspend fun background(client: TootApiClient): TootApiResult? {
                        val result = client.request(PATH_FILTERS)
                        val jsonArray = result?.jsonArray
                        if (jsonArray != null) {
                            filter_list = TootFilter.parseList(jsonArray)
                        }
                        return result
                    }

                    override suspend fun handleResult(result: TootApiResult?) {
                        val filter_list = this.filter_list
                        if (filter_list != null) {
                            log.d("update filters for ${access_info.acct.pretty}")
                            for (column in App1.getAppState(context).columnList) {
                                if (column.access_info == access_info) {
                                    column.onFiltersChanged2(filter_list)
                                }
                            }
                        }
                    }
                })
        }

        private val columnIdMap = HashMap<String, WeakReference<Column>?>()
        private fun registerColumnId(id: String, column: Column) {
            synchronized(columnIdMap) {
                columnIdMap[id] = WeakReference(column)
            }
        }

        private fun generateColumnId(): String {
            synchronized(columnIdMap) {
                val buffer = ByteBuffer.allocate(8)
                var id = ""
                while (id.isEmpty() || columnIdMap.containsKey(id)) {
                    if (id.isNotEmpty()) Thread.sleep(1L)
                    buffer.clear()
                    buffer.putLong(System.currentTimeMillis())
                    id = buffer.array().encodeBase64Url()
                }
                columnIdMap[id] = null
                return id
            }
        }

        private fun decodeColumnId(src: JsonObject): String {
            return src.string(KEY_COLUMN_ID) ?: generateColumnId()
        }

        fun findColumnById(id: String): Column? {
            synchronized(columnIdMap) {
                return columnIdMap[id]?.get()
            }
        }

        fun getBackgroundImageDir(context: Context): File {
            val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            if (externalDir == null) {
                log.e("getExternalFilesDir is null.")
            } else {
                val state = Environment.getExternalStorageState()
                if (state != Environment.MEDIA_MOUNTED) {
                    log.e("getExternalStorageState: ${state}")
                } else {
                    log.i("externalDir: ${externalDir}")
                    externalDir.mkdir()
                    val backgroundDir = File(externalDir, DIR_BACKGROUND_IMAGE)
                    backgroundDir.mkdir()
                    log.i("backgroundDir: ${backgroundDir} exists=${backgroundDir.exists()}")
                    return backgroundDir
                }
            }
            val backgroundDir = context.getDir(DIR_BACKGROUND_IMAGE, Context.MODE_PRIVATE)
            log.i("backgroundDir: ${backgroundDir} exists=${backgroundDir.exists()}")
            return backgroundDir
        }

        var defaultColorHeaderBg = 0
        var defaultColorHeaderName = 0
        var defaultColorHeaderPageNumber = 0
        var defaultColorContentBg = 0
        var defaultColorContentAcct = 0
        var defaultColorContentText = 0

        fun reloadDefaultColor(activity: AppCompatActivity, pref: SharedPreferences) {

            defaultColorHeaderBg = Pref.ipCcdHeaderBg(pref).notZero()
                ?: activity.attrColor(R.attr.color_column_header)

            defaultColorHeaderName = Pref.ipCcdHeaderFg(pref).notZero()
                ?: activity.attrColor(R.attr.colorColumnHeaderName)

            defaultColorHeaderPageNumber = Pref.ipCcdHeaderFg(pref).notZero()
                ?: activity.attrColor(R.attr.colorColumnHeaderPageNumber)

            defaultColorContentBg = Pref.ipCcdContentBg(pref)
            // may zero

            defaultColorContentAcct = Pref.ipCcdContentAcct(pref).notZero()
                ?: activity.attrColor(R.attr.colorTimeSmall)

            defaultColorContentText = Pref.ipCcdContentText(pref).notZero()
                ?: activity.attrColor(R.attr.colorContentText)

        }

        private val internalIdSeed = AtomicInteger(0)
    }

    // カラムオブジェクトの識別に使うID。
    val internalId = internalIdSeed.incrementAndGet()

    val type = ColumnType.parse(typeId)

    internal var dont_close: Boolean = false

    internal var with_attachment: Boolean = false
    internal var with_highlight: Boolean = false
    internal var dont_show_boost: Boolean = false
    internal var dont_show_reply: Boolean = false

    internal var dont_show_normal_toot: Boolean = false
    internal var dont_show_non_public_toot: Boolean = false

    internal var dont_show_favourite: Boolean = false // 通知カラムのみ
    internal var dont_show_follow: Boolean = false // 通知カラムのみ
    internal var dont_show_reaction: Boolean = false // 通知カラムのみ
    internal var dont_show_vote: Boolean = false // 通知カラムのみ

    internal var quick_filter = QUICK_FILTER_ALL

    @Volatile
    internal var dont_streaming: Boolean = false

    internal var dont_auto_refresh: Boolean = false
    internal var hide_media_default: Boolean = false
    internal var system_notification_not_related: Boolean = false
    internal var instance_local: Boolean = false

    internal var enable_speech: Boolean = false
    internal var use_old_api = false

    internal var regex_text: String = ""

    internal var header_bg_color: Int = 0
    internal var header_fg_color: Int = 0
    internal var column_bg_color: Int = 0
    internal var acct_color: Int = 0
    internal var content_color: Int = 0
    internal var column_bg_image: String = ""
    internal var column_bg_image_alpha = 1f

    internal var profile_tab = ProfileTab.Status

    internal var status_id: EntityId? = null

    // プロフカラムではアカウントのID。リストカラムではリストのID
    internal var profile_id: EntityId? = null

    internal var search_query: String = ""
    internal var search_resolve: Boolean = false
    internal var remote_only: Boolean = false
    internal var instance_uri: String = ""
    internal var hashtag: String = ""
    internal var hashtag_any: String = ""
    internal var hashtag_all: String = ""
    internal var hashtag_none: String = ""
    internal var hashtag_acct: String = ""

    internal var language_filter: JsonObject? = null

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
    internal var who_account: TootAccountRef? = null

    // プロフカラムでのfeatured tag 情報(Mastodon3.3.0)
    @Volatile
    internal var who_featured_tags: List<TootTag>? = null

    // リストカラムでのリスト情報
    @Volatile
    internal var list_info: TootList? = null

    // アンテナカラムでのリスト情報
    @Volatile
    internal var antenna_info: MisskeyAntenna? = null

    // 「インスタンス情報」カラムに表示するインスタンス情報
    // (SavedAccount中のインスタンス情報とは異なるので注意)
    internal var instance_information: TootInstance? = null
    internal var handshake: Handshake? = null

    internal var scroll_save: ScrollPosition? = null
    private var last_viewing_item_id: EntityId? = null

    internal val is_dispose = AtomicBoolean()

    @Volatile
    internal var bFirstInitialized = false

    var filter_reload_required: Boolean = false

    //////////////////////////////////////////////////////////////////////////////////////

    // カラムを閉じた後のnotifyDataSetChangedのタイミングで、add/removeされる順序が期待通りにならないので
    // 参照を１つだけ持つのではなく、リストを保持して先頭の要素を使うことにする

    private val _holder_list = LinkedList<ColumnViewHolder>()

    internal // 複数のリスナがある場合、最も新しいものを返す
    val viewHolder: ColumnViewHolder?
        get() {
            if (is_dispose.get()) return null
            return if (_holder_list.isEmpty()) null else _holder_list.first
        }

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

    internal var task_progress: String? = null

    internal val list_data = BucketList<TimelineItem>()
    internal val duplicate_map = DuplicateMap()

    internal val isFilterEnabled: Boolean
        get() = (with_attachment
            || with_highlight
            || regex_text.isNotEmpty()
            || dont_show_normal_toot
            || dont_show_non_public_toot
            || quick_filter != QUICK_FILTER_ALL
            || dont_show_boost
            || dont_show_favourite
            || dont_show_follow
            || dont_show_reply
            || dont_show_reaction
            || dont_show_vote
            || (language_filter?.isNotEmpty() == true)
            )

    @Volatile
    var column_regex_filter = COLUMN_REGEX_FILTER_DEFAULT

    @Volatile
    var keywordFilterTrees: FilterTrees? = null

    @Volatile
    var favMuteSet: HashSet<Acct>? = null

    @Volatile
    var highlight_trie: WordTrieTree? = null

    // タイムライン中のデータの始端と終端
    // misskeyは
    internal var idRecent: EntityId? = null
    internal var idOld: EntityId? = null
    internal var offsetNext: Int = 0
    internal var pagingType: ColumnPagingType = ColumnPagingType.Default

    var bRefreshingTop: Boolean = false

    // ListViewの表示更新が追いつかないとスクロール位置が崩れるので
    // 一定時間より短期間にはデータ更新しないようにする
    private val last_show_stream_data = AtomicLong(0L)
    private val stream_data_queue = ConcurrentLinkedQueue<TimelineItem>()

    @Volatile
    private var bPutGap: Boolean = false

    var cacheHeaderDesc: String? = null

    // DMカラム更新時に新APIの利用に成功したなら真
    internal var useConversationSummaries = false

    // DMカラムのストリーミングイベントで新形式のイベントを利用できたなら真
    internal var useConversationSummaryStreaming = false

    ////////////////////////////////////////////////////////////////

    private fun runOnMainLooperForStreamingEvent(proc: () -> Unit) {
        runOnMainLooper {
            if (!canHandleStreamingMessage())
                return@runOnMainLooper
            proc()
        }
    }

    val streamCallback = object : StreamCallback {

        override fun onStreamStatusChanged(status: StreamStatus) {
            log.d(
                "onStreamStatusChanged status=${status}, bFirstInitialized=$bFirstInitialized, bInitialLoading=$bInitialLoading, column=${access_info.acct}/${
                    getColumnName(
                        true
                    )
                }"
            )

            if (status == StreamStatus.Subscribed) {
                updateMisskeyCapture()
            }

            runOnMainLooperForStreamingEvent {
                if (is_dispose.get()) return@runOnMainLooperForStreamingEvent
                fireShowColumnStatus()
            }
        }

        override fun onTimelineItem(item: TimelineItem, channelId: String?, stream: JsonArray?) {
            if (StreamManager.traceDelivery) log.v("${access_info.acct} onTimelineItem")
            if (!canHandleStreamingMessage()) return

            when (item) {
                is TootConversationSummary -> {
                    if (type != ColumnType.DIRECT_MESSAGES) return
                    if (isFiltered(item.last_status)) return
                    if (use_old_api) {
                        useConversationSummaryStreaming = false
                        return
                    } else {
                        useConversationSummaryStreaming = true
                    }
                }

                is TootNotification -> {
                    if (!isNotificationColumn) return
                    if (isFiltered(item)) return
                }

                is TootStatus -> {
                    if (isNotificationColumn) return

                    // マストドン2.6.0形式のDMカラム用イベントを利用したならば、その直後に発生する普通の投稿イベントを無視する
                    if (useConversationSummaryStreaming) return

                    // マストドンはLTLに外部ユーザの投稿を表示しない
                    if (type == ColumnType.LOCAL && isMastodon && item.account.isRemote) return

                    if (isFiltered(item)) return
                }
            }

            stream_data_queue.add(item)
            app_state.handler.post(mergeStreamingMessage)
        }

        override fun onEmojiReaction(item: TootNotification) {
            runOnMainLooperForStreamingEvent {
                this@Column.updateEmojiReaction(item.status)
            }
        }

        override fun onNoteUpdated(ev: MisskeyNoteUpdate, channelId: String?) {
            runOnMainLooperForStreamingEvent {
                this@Column.onMisskeyNoteUpdated(ev)

            }
        }

        override fun onAnnouncementUpdate(item: TootAnnouncement) {
            runOnMainLooperForStreamingEvent {
                this@Column.onAnnouncementUpdate(item)
            }
        }

        override fun onAnnouncementDelete(id: EntityId) {
            runOnMainLooperForStreamingEvent {
                this@Column.onAnnouncementDelete(id)
            }
        }

        override fun onAnnouncementReaction(reaction: TootReaction) {
            runOnMainLooperForStreamingEvent {
                this@Column.onAnnouncementReaction(reaction)
            }
        }
    }

    private val mergeStreamingMessage = object : Runnable {
        override fun run() {
            val handler = app_state.handler

            // 未初期化や初期ロード中ならキューをクリアして何もしない
            if (!canHandleStreamingMessage()) {
                stream_data_queue.clear()
                handler.removeCallbacks(this)
                return
            }

            // 前回マージしてから暫くは待機してリトライ
            // カラムがビジー状態なら待機してリトライ
            val now = SystemClock.elapsedRealtime()
            var remain = last_show_stream_data.get() + 333L - now
            if (bRefreshLoading) remain = max(333L, remain)
            if (remain > 0) {
                handler.removeCallbacks(this)
                handler.postDelayed(this, remain)
                return
            }

            last_show_stream_data.set(now)

            val tmpList = ArrayList<TimelineItem>()
            while (true) tmpList.add(stream_data_queue.poll() ?: break)
            if (tmpList.isEmpty()) return

            // キューから読めた件数が0の場合を除き、少し後に再処理させることでマージ漏れを防ぐ
            handler.postDelayed(this, 333L)

            // ストリーミングされるデータは全てID順に並んでいるはず
            tmpList.sortByDescending { it.getOrderId() }

            val list_new = duplicate_map.filterDuplicate(tmpList)
            if (list_new.isEmpty()) return

            for (item in list_new) {
                if (enable_speech && item is TootStatus) {
                    app_state.addSpeech(item.reblog ?: item)
                }
            }

            // 通知カラムならストリーミング経由で届いたデータを通知ワーカーに伝達する
            if (isNotificationColumn) {
                val list = ArrayList<TootNotification>()
                for (o in list_new) {
                    if (o is TootNotification) {
                        list.add(o)
                    }
                }
                if (list.isNotEmpty()) {
                    PollingWorker.injectData(context, access_info, list)
                }
            }

            // 最新のIDをsince_idとして覚える(ソートはしない)
            var new_id_max: EntityId? = null
            var new_id_min: EntityId? = null
            for (o in list_new) {
                try {
                    val id = o.getOrderId()
                    if (id.toString().isEmpty()) continue
                    if (new_id_max == null || id > new_id_max) new_id_max = id
                    if (new_id_min == null || id < new_id_min) new_id_min = id
                } catch (ex: Throwable) {
                    // IDを取得できないタイプのオブジェクトだった
                    // ストリームに来るのは通知かステータスだから、多分ここは通らない
                    log.trace(ex)
                }
            }

            val tmpRecent = idRecent
            val tmpNewMax = new_id_max

            if (tmpNewMax != null && (tmpRecent?.compareTo(tmpNewMax) ?: -1) == -1) {
                idRecent = tmpNewMax
                // XXX: コレはリフレッシュ時に取得漏れを引き起こすのでは…？
                // しかしコレなしだとリフレッシュ時に大量に読むことになる…
            }

            val holder = viewHolder

            // 事前にスクロール位置を覚えておく
            val holder_sp: ScrollPosition? = holder?.scrollPosition

            // idx番目の要素がListViewの上端から何ピクセル下にあるか
            var restore_idx = -2
            var restore_y = 0
            if (holder != null) {
                if (list_data.size > 0) {
                    try {
                        restore_idx = holder.findFirstVisibleListItem()
                        restore_y = holder.getListItemOffset(restore_idx)
                    } catch (ex: IndexOutOfBoundsException) {
                        restore_idx = -2
                        restore_y = 0
                    }
                }
            }

            // 画面復帰時の自動リフレッシュではギャップが残る可能性がある
            if (bPutGap) {
                bPutGap = false
                try {
                    if (list_data.size > 0 && new_id_min != null) {
                        val since = list_data[0].getOrderId()
                        if (new_id_min > since) {
                            val gap = TootGap(new_id_min, since)
                            list_new.add(gap)
                        }
                    }
                } catch (ex: Throwable) {
                    log.e(ex, "can't put gap.")
                }

            }

            val changeList = ArrayList<AdapterChange>()

            replaceConversationSummary(changeList, list_new, list_data)

            val added = list_new.size  // may 0

            var doneSound = false
            for (o in list_new) {
                if (o is TootStatus) {
                    o.highlightSound?.let {
                        if (!doneSound) {
                            doneSound = true
                            App1.sound(it)
                        }
                    }
                    o.highlightSpeech?.let {
                        app_state.addSpeech(it.name, dedupMode = DedupMode.RecentExpire)
                    }
                }
            }

            changeList.add(AdapterChange(AdapterChangeType.RangeInsert, 0, added))
            list_data.addAll(0, list_new)

            fireShowContent(reason = "mergeStreamingMessage", changeList = changeList)

            if (holder != null) {
                when {
                    holder_sp == null -> {
                        // スクロール位置が先頭なら先頭にする
                        log.d("mergeStreamingMessage: has VH. missing scroll position.")
                        viewHolder?.scrollToTop()
                    }

                    holder_sp.isHead -> {
                        // スクロール位置が先頭なら先頭にする
                        log.d("mergeStreamingMessage: has VH. keep head. $holder_sp")
                        holder.setScrollPosition(ScrollPosition())
                    }

                    restore_idx < -1 -> {
                        // 可視範囲の検出に失敗
                        log.d("mergeStreamingMessage: has VH. can't get visible range.")
                    }

                    else -> {
                        // 現在の要素が表示され続けるようにしたい
                        log.d("mergeStreamingMessage: has VH. added=$added")
                        holder.setListItemTop(restore_idx + added, restore_y)
                    }
                }
            } else {
                val scroll_save = this@Column.scroll_save
                when {

                    // スクロール位置が先頭なら先頭のまま
                    scroll_save == null || scroll_save.isHead -> {

                    }

                    // 現在の要素が表示され続けるようにしたい
                    else -> scroll_save.adapterIndex += added
                }
            }

            updateMisskeyCapture()
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////

    internal constructor(
        app_state: AppState,
        access_info: SavedAccount,
        type: Int,
        vararg params: Any
    ) : this(
        app_state, app_state.context, access_info, type, generateColumnId()
    ) {
        when (typeMap[type]) {

            ColumnType.CONVERSATION,
            ColumnType.BOOSTED_BY,
            ColumnType.FAVOURITED_BY,
            ColumnType.LOCAL_AROUND,
            ColumnType.FEDERATED_AROUND,
            ColumnType.ACCOUNT_AROUND ->
                status_id = getParamEntityId(params, 0)

            ColumnType.PROFILE, ColumnType.LIST_TL, ColumnType.LIST_MEMBER,
            ColumnType.MISSKEY_ANTENNA_TL ->
                profile_id = getParamEntityId(params, 0)

            ColumnType.HASHTAG ->
                hashtag = getParamString(params, 0)

            ColumnType.HASHTAG_FROM_ACCT -> {
                hashtag = getParamString(params, 0)
                hashtag_acct = getParamString(params, 1)
            }

            ColumnType.NOTIFICATION_FROM_ACCT -> {
                hashtag_acct = getParamString(params, 0)
            }

            ColumnType.SEARCH -> {
                search_query = getParamString(params, 0)
                search_resolve = getParamAt(params, 1)
            }

            ColumnType.SEARCH_MSP, ColumnType.SEARCH_TS, ColumnType.SEARCH_NOTESTOCK ->
                search_query = getParamString(params, 0)

            ColumnType.INSTANCE_INFORMATION ->
                instance_uri = getParamString(params, 0)

            ColumnType.PROFILE_DIRECTORY -> {
                instance_uri = getParamString(params, 0)
                search_resolve = true
            }

            ColumnType.DOMAIN_TIMELINE -> {
                instance_uri = getParamString(params, 0)
            }

            else -> {

            }
        }
    }

    internal constructor(app_state: AppState, src: JsonObject)
        : this(
        app_state,
        app_state.context,
        loadAccount(app_state.context, src),
        src.optInt(KEY_TYPE),
        decodeColumnId(src)
    ) {
        dont_close = src.optBoolean(KEY_DONT_CLOSE)
        with_attachment = src.optBoolean(KEY_WITH_ATTACHMENT)
        with_highlight = src.optBoolean(KEY_WITH_HIGHLIGHT)
        dont_show_boost = src.optBoolean(KEY_DONT_SHOW_BOOST)
        dont_show_follow = src.optBoolean(KEY_DONT_SHOW_FOLLOW)
        dont_show_favourite = src.optBoolean(KEY_DONT_SHOW_FAVOURITE)
        dont_show_reply = src.optBoolean(KEY_DONT_SHOW_REPLY)
        dont_show_reaction = src.optBoolean(KEY_DONT_SHOW_REACTION)
        dont_show_vote = src.optBoolean(KEY_DONT_SHOW_VOTE)
        dont_show_normal_toot = src.optBoolean(KEY_DONT_SHOW_NORMAL_TOOT)
        dont_show_non_public_toot = src.optBoolean(KEY_DONT_SHOW_NON_PUBLIC_TOOT)
        dont_streaming = src.optBoolean(KEY_DONT_STREAMING)
        dont_auto_refresh = src.optBoolean(KEY_DONT_AUTO_REFRESH)
        hide_media_default = src.optBoolean(KEY_HIDE_MEDIA_DEFAULT)
        system_notification_not_related = src.optBoolean(KEY_SYSTEM_NOTIFICATION_NOT_RELATED)
        instance_local = src.optBoolean(KEY_INSTANCE_LOCAL)
        quick_filter = src.optInt(KEY_QUICK_FILTER, 0)

        announcementHideTime = src.optLong(KEY_ANNOUNCEMENT_HIDE_TIME, 0L)

        enable_speech = src.optBoolean(KEY_ENABLE_SPEECH)
        use_old_api = src.optBoolean(KEY_USE_OLD_API)
        last_viewing_item_id = EntityId.from(src, KEY_LAST_VIEWING_ITEM)

        regex_text = src.string(KEY_REGEX_TEXT) ?: ""
        language_filter = src.jsonObject(KEY_LANGUAGE_FILTER)

        header_bg_color = src.optInt(KEY_HEADER_BACKGROUND_COLOR)
        header_fg_color = src.optInt(KEY_HEADER_TEXT_COLOR)
        column_bg_color = src.optInt(KEY_COLUMN_BACKGROUND_COLOR)
        acct_color = src.optInt(KEY_COLUMN_ACCT_TEXT_COLOR)
        content_color = src.optInt(KEY_COLUMN_CONTENT_TEXT_COLOR)
        column_bg_image = src.string(KEY_COLUMN_BACKGROUND_IMAGE) ?: ""
        column_bg_image_alpha = src.optFloat(KEY_COLUMN_BACKGROUND_IMAGE_ALPHA, 1f)

        when (type) {

            ColumnType.CONVERSATION, ColumnType.BOOSTED_BY, ColumnType.FAVOURITED_BY,
            ColumnType.LOCAL_AROUND, ColumnType.ACCOUNT_AROUND ->
                status_id = EntityId.mayNull(src.string(KEY_STATUS_ID))

            ColumnType.FEDERATED_AROUND -> {
                status_id = EntityId.mayNull(src.string(KEY_STATUS_ID))
                remote_only = src.optBoolean(KEY_REMOTE_ONLY, false)
            }

            ColumnType.FEDERATE -> {
                remote_only = src.optBoolean(KEY_REMOTE_ONLY, false)
            }

            ColumnType.PROFILE -> {
                profile_id = EntityId.mayNull(src.string(KEY_PROFILE_ID))
                val tabId = src.optInt(KEY_PROFILE_TAB)
                profile_tab = ProfileTab.values().find { it.id == tabId } ?: ProfileTab.Status
            }

            ColumnType.LIST_MEMBER, ColumnType.LIST_TL,
            ColumnType.MISSKEY_ANTENNA_TL -> {
                profile_id = EntityId.mayNull(src.string(KEY_PROFILE_ID))
            }

            ColumnType.HASHTAG -> {
                hashtag = src.optString(KEY_HASHTAG)
                hashtag_any = src.optString(KEY_HASHTAG_ANY)
                hashtag_all = src.optString(KEY_HASHTAG_ALL)
                hashtag_none = src.optString(KEY_HASHTAG_NONE)
            }

            ColumnType.HASHTAG_FROM_ACCT -> {
                hashtag_acct = src.optString(KEY_HASHTAG_ACCT)
                hashtag = src.optString(KEY_HASHTAG)
                hashtag_any = src.optString(KEY_HASHTAG_ANY)
                hashtag_all = src.optString(KEY_HASHTAG_ALL)
                hashtag_none = src.optString(KEY_HASHTAG_NONE)
            }

            ColumnType.NOTIFICATION_FROM_ACCT -> {
                hashtag_acct = src.optString(KEY_HASHTAG_ACCT)
            }

            ColumnType.SEARCH -> {
                search_query = src.optString(KEY_SEARCH_QUERY)
                search_resolve = src.optBoolean(KEY_SEARCH_RESOLVE, false)
            }

            ColumnType.SEARCH_MSP, ColumnType.SEARCH_TS, ColumnType.SEARCH_NOTESTOCK -> search_query =
                src.optString(KEY_SEARCH_QUERY)

            ColumnType.INSTANCE_INFORMATION -> instance_uri = src.optString(KEY_INSTANCE_URI)

            ColumnType.PROFILE_DIRECTORY -> {
                instance_uri = src.optString(KEY_INSTANCE_URI)
                search_query = src.optString(KEY_SEARCH_QUERY)
                search_resolve = src.optBoolean(KEY_SEARCH_RESOLVE, false)
            }

            ColumnType.DOMAIN_TIMELINE -> {
                instance_uri = src.optString(KEY_INSTANCE_URI)
            }

            else -> {

            }
        }
    }

    override fun hashCode(): Int = internalId

    override fun equals(other: Any?): Boolean = this === other

    fun getIconId(): Int = type.iconId(access_info.acct)

    fun getColumnName(long: Boolean) =
        type.name2(this, long) ?: type.name1(context)

    @Throws(JsonException::class)
    fun encodeJSON(dst: JsonObject, old_index: Int) {
        dst[KEY_ACCOUNT_ROW_ID] = access_info.db_id
        dst[KEY_TYPE] = type.id
        dst[KEY_COLUMN_ID] = column_id

        dst[KEY_ANNOUNCEMENT_HIDE_TIME] = announcementHideTime

        dst.putIfTrue(KEY_DONT_CLOSE, dont_close)
        dst.putIfTrue(KEY_WITH_ATTACHMENT, with_attachment)
        dst.putIfTrue(KEY_WITH_HIGHLIGHT, with_highlight)
        dst.putIfTrue(KEY_DONT_SHOW_BOOST, dont_show_boost)
        dst.putIfTrue(KEY_DONT_SHOW_FOLLOW, dont_show_follow)
        dst.putIfTrue(KEY_DONT_SHOW_FAVOURITE, dont_show_favourite)
        dst.putIfTrue(KEY_DONT_SHOW_REPLY, dont_show_reply)
        dst.putIfTrue(KEY_DONT_SHOW_REACTION, dont_show_reaction)
        dst.putIfTrue(KEY_DONT_SHOW_VOTE, dont_show_vote)
        dst.putIfTrue(KEY_DONT_SHOW_NORMAL_TOOT, dont_show_normal_toot)
        dst.putIfTrue(KEY_DONT_SHOW_NON_PUBLIC_TOOT, dont_show_non_public_toot)
        dst.putIfTrue(KEY_DONT_STREAMING, dont_streaming)
        dst.putIfTrue(KEY_DONT_AUTO_REFRESH, dont_auto_refresh)
        dst.putIfTrue(KEY_HIDE_MEDIA_DEFAULT, hide_media_default)
        dst.putIfTrue(KEY_SYSTEM_NOTIFICATION_NOT_RELATED, system_notification_not_related)
        dst.putIfTrue(KEY_INSTANCE_LOCAL, instance_local)
        dst.putIfTrue(KEY_ENABLE_SPEECH, enable_speech)
        dst.putIfTrue(KEY_USE_OLD_API, use_old_api)
        dst[KEY_QUICK_FILTER] = quick_filter

        last_viewing_item_id?.putTo(dst, KEY_LAST_VIEWING_ITEM)

        dst[KEY_REGEX_TEXT] = regex_text

        val ov = language_filter
        if (ov != null) dst[KEY_LANGUAGE_FILTER] = ov

        dst[KEY_HEADER_BACKGROUND_COLOR] = header_bg_color
        dst[KEY_HEADER_TEXT_COLOR] = header_fg_color
        dst[KEY_COLUMN_BACKGROUND_COLOR] = column_bg_color
        dst[KEY_COLUMN_ACCT_TEXT_COLOR] = acct_color
        dst[KEY_COLUMN_CONTENT_TEXT_COLOR] = content_color
        dst[KEY_COLUMN_BACKGROUND_IMAGE] = column_bg_image
        dst[KEY_COLUMN_BACKGROUND_IMAGE_ALPHA] = column_bg_image_alpha.toDouble()

        when (type) {

            ColumnType.CONVERSATION,
            ColumnType.BOOSTED_BY,
            ColumnType.FAVOURITED_BY,
            ColumnType.LOCAL_AROUND,
            ColumnType.ACCOUNT_AROUND ->
                dst[KEY_STATUS_ID] = status_id.toString()

            ColumnType.FEDERATED_AROUND -> {
                dst[KEY_STATUS_ID] = status_id.toString()
                dst[KEY_REMOTE_ONLY] = remote_only
            }

            ColumnType.FEDERATE -> {
                dst[KEY_REMOTE_ONLY] = remote_only
            }

            ColumnType.PROFILE -> {
                dst[KEY_PROFILE_ID] = profile_id.toString()
                dst[KEY_PROFILE_TAB] = profile_tab.id
            }

            ColumnType.LIST_MEMBER, ColumnType.LIST_TL,
            ColumnType.MISSKEY_ANTENNA_TL -> {
                dst[KEY_PROFILE_ID] = profile_id.toString()
            }

            ColumnType.HASHTAG -> {
                dst[KEY_HASHTAG] = hashtag
                dst[KEY_HASHTAG_ANY] = hashtag_any
                dst[KEY_HASHTAG_ALL] = hashtag_all
                dst[KEY_HASHTAG_NONE] = hashtag_none
            }

            ColumnType.HASHTAG_FROM_ACCT -> {
                dst[KEY_HASHTAG_ACCT] = hashtag_acct
                dst[KEY_HASHTAG] = hashtag
                dst[KEY_HASHTAG_ANY] = hashtag_any
                dst[KEY_HASHTAG_ALL] = hashtag_all
                dst[KEY_HASHTAG_NONE] = hashtag_none
            }

            ColumnType.NOTIFICATION_FROM_ACCT -> {
                dst[KEY_HASHTAG_ACCT] = hashtag_acct
            }

            ColumnType.SEARCH -> {
                dst[KEY_SEARCH_QUERY] = search_query
                dst[KEY_SEARCH_RESOLVE] = search_resolve
            }

            ColumnType.SEARCH_MSP, ColumnType.SEARCH_TS, ColumnType.SEARCH_NOTESTOCK -> {
                dst[KEY_SEARCH_QUERY] = search_query
            }

            ColumnType.INSTANCE_INFORMATION -> {
                dst[KEY_INSTANCE_URI] = instance_uri
            }

            ColumnType.PROFILE_DIRECTORY -> {
                dst[KEY_SEARCH_QUERY] = search_query
                dst[KEY_SEARCH_RESOLVE] = search_resolve
                dst[KEY_INSTANCE_URI] = instance_uri
            }

            ColumnType.DOMAIN_TIMELINE -> {
                dst[KEY_INSTANCE_URI] = instance_uri
            }

            else -> {
                // no extra parameter
            }
        }

        // 以下は保存には必要ないが、カラムリスト画面で使う
        val ac = AcctColor.load(access_info)
        dst[KEY_COLUMN_ACCESS_ACCT] = access_info.acct.ascii
        dst[KEY_COLUMN_ACCESS_STR] = ac.nickname
        dst[KEY_COLUMN_ACCESS_COLOR] = ac.color_fg
        dst[KEY_COLUMN_ACCESS_COLOR_BG] = ac.color_bg
        dst[KEY_COLUMN_NAME] = getColumnName(true)
        dst[KEY_OLD_INDEX] = old_index
    }

    internal fun isSameSpec(
        ai: SavedAccount,
        type: ColumnType,
        params: Array<out Any>
    ): Boolean {
        if (type != this.type || ai != access_info) return false

        return try {
            when (type) {

                ColumnType.PROFILE,
                ColumnType.LIST_TL,
                ColumnType.LIST_MEMBER,
                ColumnType.MISSKEY_ANTENNA_TL ->
                    profile_id == getParamEntityId(params, 0)

                ColumnType.CONVERSATION,
                ColumnType.BOOSTED_BY,
                ColumnType.FAVOURITED_BY,
                ColumnType.LOCAL_AROUND,
                ColumnType.FEDERATED_AROUND,
                ColumnType.ACCOUNT_AROUND ->
                    status_id == getParamEntityId(params, 0)

                ColumnType.HASHTAG -> {
                    (getParamString(params, 0) == hashtag)
                        && ((getParamAtNullable<String>(params, 1) ?: "") == hashtag_any)
                        && ((getParamAtNullable<String>(params, 2) ?: "") == hashtag_all)
                        && ((getParamAtNullable<String>(params, 3) ?: "") == hashtag_none)
                }

                ColumnType.HASHTAG_FROM_ACCT -> {
                    (getParamString(params, 0) == hashtag)
                        && ((getParamAtNullable<String>(params, 1) ?: "") == hashtag_acct)
                }

                ColumnType.NOTIFICATION_FROM_ACCT -> {
                    ((getParamAtNullable<String>(params, 0) ?: "") == hashtag_acct)
                }

                ColumnType.SEARCH ->
                    getParamString(params, 0) == search_query &&
                        getParamAtNullable<Boolean>(params, 1) == search_resolve

                ColumnType.SEARCH_MSP,
                ColumnType.SEARCH_TS,
                ColumnType.SEARCH_NOTESTOCK ->
                    getParamString(params, 0) == search_query

                ColumnType.INSTANCE_INFORMATION ->
                    getParamString(params, 0) == instance_uri

                ColumnType.PROFILE_DIRECTORY ->
                    getParamString(params, 0) == instance_uri &&
                        getParamAtNullable<String>(params, 1) == search_query &&
                        getParamAtNullable<Boolean>(params, 2) == search_resolve

                ColumnType.DOMAIN_TIMELINE ->
                    getParamString(params, 0) == instance_uri

                else -> true
            }
        } catch (ex: Throwable) {
            log.trace(ex)
            false
        }
    }

    fun getNotificationTypeString(): String {
        val sb = StringBuilder()
        sb.append("(")

        when (quick_filter) {
            QUICK_FILTER_ALL -> {
                var n = 0
                if (!dont_show_reply) {
                    if (n++ > 0) sb.append(", ")
                    sb.append(context.getString(R.string.notification_type_mention))
                }
                if (!dont_show_follow) {
                    if (n++ > 0) sb.append(", ")
                    sb.append(context.getString(R.string.notification_type_follow))
                }
                if (!dont_show_boost) {
                    if (n++ > 0) sb.append(", ")
                    sb.append(context.getString(R.string.notification_type_boost))
                }
                if (!dont_show_favourite) {
                    if (n++ > 0) sb.append(", ")
                    sb.append(context.getString(R.string.notification_type_favourite))
                }
                if (isMisskey && !dont_show_reaction) {
                    if (n++ > 0) sb.append(", ")
                    sb.append(context.getString(R.string.notification_type_reaction))
                }
                if (!dont_show_vote) {
                    if (n++ > 0) sb.append(", ")
                    sb.append(context.getString(R.string.notification_type_vote))
                }
                val n_max = if (isMisskey) {
                    6
                } else {
                    5
                }
                if (n == 0 || n == n_max) return "" // 全部か皆無なら部分表記は要らない
            }

            QUICK_FILTER_MENTION -> sb.append(context.getString(R.string.notification_type_mention))
            QUICK_FILTER_FAVOURITE -> sb.append(context.getString(R.string.notification_type_favourite))
            QUICK_FILTER_BOOST -> sb.append(context.getString(R.string.notification_type_boost))
            QUICK_FILTER_FOLLOW -> sb.append(context.getString(R.string.notification_type_follow))
            QUICK_FILTER_REACTION -> sb.append(context.getString(R.string.notification_type_reaction))
            QUICK_FILTER_VOTE -> sb.append(context.getString(R.string.notification_type_vote))
            QUICK_FILTER_POST -> sb.append(context.getString(R.string.notification_type_post))
        }

        sb.append(")")
        return sb.toString()
    }

    internal fun dispose() {
        is_dispose.set(true)
        app_state.streamManager.updateStreamingColumns()

        for (vh in _holder_list) {
            try {
                vh.listView.adapter = null
            } catch (ignored: Throwable) {
            }
        }
    }

    internal fun addColumnViewHolder(cvh: ColumnViewHolder) {

        // 現在のリストにあるなら削除する
        removeColumnViewHolder(cvh)

        // 最後に追加されたものが先頭にくるようにする
        // 呼び出しの後に必ず追加されているようにする
        _holder_list.addFirst(cvh)
    }

    internal fun removeColumnViewHolder(cvh: ColumnViewHolder) {
        val it = _holder_list.iterator()
        while (it.hasNext()) {
            if (cvh == it.next()) it.remove()
        }
    }

    internal fun removeColumnViewHolderByActivity(activity: ActMain) {
        val it = _holder_list.iterator()
        while (it.hasNext()) {
            val cvh = it.next()
            if (cvh.activity == activity) {
                it.remove()
            }
        }
    }

    internal fun hasMultipleViewHolder(): Boolean = _holder_list.size > 1

    internal fun fireShowContent(
        reason: String,
        changeList: List<AdapterChange>? = null,
        reset: Boolean = false
    ) {
        if (!isMainThread) {
            throw RuntimeException("fireShowContent: not on main thread.")
        }
        viewHolder?.showContent(reason, changeList, reset)
    }

    internal fun fireShowColumnHeader() {
        if (!isMainThread) {
            throw RuntimeException("fireShowColumnHeader: not on main thread.")
        }
        viewHolder?.showColumnHeader()
    }

    internal fun fireShowColumnStatus() {
        if (!isMainThread) {
            throw RuntimeException("fireShowColumnStatus: not on main thread.")
        }
        viewHolder?.showColumnStatus()
    }

    internal fun fireColumnColor() {
        if (!isMainThread) {
            throw RuntimeException("fireColumnColor: not on main thread.")
        }
        viewHolder?.showColumnColor()
    }

    fun fireRelativeTime() {
        if (!isMainThread) {
            throw RuntimeException("fireRelativeTime: not on main thread.")
        }
        viewHolder?.updateRelativeTime()
    }

    fun fireRebindAdapterItems() {
        if (!isMainThread) {
            throw RuntimeException("fireRelativeTime: not on main thread.")
        }
        viewHolder?.rebindAdapterItems()
    }

    fun cancelLastTask() {
        if (lastTask != null) {
            lastTask?.cancel()
            lastTask = null
            //
            bInitialLoading = false
            bRefreshLoading = false
            mInitialLoadingError = context.getString(R.string.cancelled)
        }
    }

    //	@Nullable String parseMaxId( TootApiResult result ){
    //		if( result != null && result.link_older != null ){
    //			Matcher m = reMaxId.matcher( result.link_older );
    //			if( m.get() ) return m.group( 1 );
    //		}
    //		return null;
    //	}

    private inner class UpdateRelationEnv {

        val who_set = HashSet<EntityId>()
        val acct_set = HashSet<String>()
        val tag_set = HashSet<String>()

        fun add(whoRef: TootAccountRef?) {
            add(whoRef?.get())
        }

        fun add(who: TootAccount?) {
            who ?: return
            who_set.add(who.id)
            val fullAcct = access_info.getFullAcct(who)
            acct_set.add("@${fullAcct.ascii}")
            acct_set.add("@${fullAcct.pretty}")
            //
            add(who.movedRef)
        }

        fun add(s: TootStatus?) {
            if (s == null) return
            add(s.accountRef)
            add(s.reblog)
            s.tags?.forEach { tag_set.add(it.name) }
        }

        fun add(n: TootNotification?) {
            if (n == null) return
            add(n.accountRef)
            add(n.status)
        }

        suspend fun update(client: TootApiClient, parser: TootParser) {

            var n: Int
            var size: Int

            if (isMisskey) {

                // parser内部にアカウントIDとRelationのマップが生成されるので、それをデータベースに記録する
                run {
                    val now = System.currentTimeMillis()
                    val who_list =
                        parser.misskeyUserRelationMap.entries.toMutableList()
                    var start = 0
                    val end = who_list.size
                    while (start < end) {
                        var step = end - start
                        if (step > RELATIONSHIP_LOAD_STEP) step = RELATIONSHIP_LOAD_STEP
                        UserRelation.saveListMisskey(now, access_info.db_id, who_list, start, step)
                        start += step
                    }
                    log.d("updateRelation: update %d relations.", end)
                }

                // 2018/11/1 Misskeyにもリレーション取得APIができた
                // アカウントIDの集合からRelationshipを取得してデータベースに記録する

                size = who_set.size
                if (size > 0) {
                    val who_list = ArrayList<EntityId>(size)
                    who_list.addAll(who_set)

                    val now = System.currentTimeMillis()

                    n = 0
                    while (n < who_list.size) {
                        val userIdList = ArrayList<EntityId>(RELATIONSHIP_LOAD_STEP)
                        for (i in 0 until RELATIONSHIP_LOAD_STEP) {
                            if (n >= size) break
                            if (!parser.misskeyUserRelationMap.containsKey(who_list[n])) {
                                userIdList.add(who_list[n])
                            }
                            ++n
                        }
                        if (userIdList.isEmpty()) continue

                        val result = client.request(
                            "/api/users/relation",
                            access_info.putMisskeyApiToken().apply {
                                put(
                                    "userId",
                                    userIdList.map { it.toString() }.toJsonArray()
                                )
                            }.toPostRequestBuilder()
                        )

                        if (result == null || result.response?.code in 400 until 500) break

                        val list = parseList(::TootRelationShip, parser, result.jsonArray)
                        if (list.size == userIdList.size) {
                            for (i in 0 until list.size) {
                                list[i].id = userIdList[i]
                            }
                            UserRelation.saveList2(now, access_info.db_id, list)
                        }
                    }
                    log.d("updateRelation: update %d relations.", n)

                }

            } else {
                // アカウントIDの集合からRelationshipを取得してデータベースに記録する
                size = who_set.size
                if (size > 0) {
                    val who_list = ArrayList<EntityId>(size)
                    who_list.addAll(who_set)

                    val now = System.currentTimeMillis()

                    n = 0
                    while (n < who_list.size) {
                        val sb = StringBuilder()
                        sb.append("/api/v1/accounts/relationships")
                        for (i in 0 until RELATIONSHIP_LOAD_STEP) {
                            if (n >= size) break
                            sb.append(if (i == 0) '?' else '&')
                            sb.append("id[]=")
                            sb.append(who_list[n++].toString())
                        }
                        val result = client.request(sb.toString()) ?: break // cancelled.
                        val list = parseList(::TootRelationShip, parser, result.jsonArray)
                        if (list.size > 0) UserRelation.saveListMastodon(
                            now,
                            access_info.db_id,
                            list
                        )
                    }
                    log.d("updateRelation: update %d relations.", n)
                }
            }

            // 出現したacctをデータベースに記録する
            size = acct_set.size
            if (size > 0) {
                val acct_list = ArrayList<String?>(size)
                acct_list.addAll(acct_set)

                val now = System.currentTimeMillis()

                n = 0
                while (n < acct_list.size) {
                    var length = size - n
                    if (length > ACCT_DB_STEP) length = ACCT_DB_STEP
                    AcctSet.saveList(now, acct_list, n, length)
                    n += length
                }
                log.d("updateRelation: update %d acct.", n)

            }

            // 出現したタグをデータベースに記録する
            size = tag_set.size
            if (size > 0) {
                val tag_list = ArrayList<String?>(size)
                tag_list.addAll(tag_set)

                val now = System.currentTimeMillis()

                n = 0
                while (n < tag_list.size) {
                    var length = size - n
                    if (length > ACCT_DB_STEP) length = ACCT_DB_STEP
                    TagSet.saveList(now, tag_list, n, length)
                    n += length
                }
                log.d("updateRelation: update %d tag.", n)
            }
        }

    }

    //
    internal suspend fun updateRelation(
        client: TootApiClient,
        list: ArrayList<TimelineItem>?,
        whoRef: TootAccountRef?,
        parser: TootParser
    ) {
        if (access_info.isPseudo) return

        val env = UpdateRelationEnv()

        env.add(whoRef)

        list?.forEach {
            when (it) {
                is TootAccountRef -> env.add(it)
                is TootStatus -> env.add(it)
                is TootNotification -> env.add(it)
                is TootConversationSummary -> env.add(it.last_status)
            }
        }
        env.update(client, parser)
    }

    internal fun parseRange(
        result: TootApiResult?,
        list: List<TimelineItem>?
    ): Pair<EntityId?, EntityId?> {
        var idMin: EntityId? = null
        var idMax: EntityId? = null

        if (isMisskey && list != null) {
            // MisskeyはLinkヘッダがないので、常にデータからIDを読む

            for (item in list) {
                // injectされたデータをデータ範囲に追加しない
                if (item.isInjected()) continue

                val id = item.getOrderId()
                if (id.notDefaultOrConfirming) {
                    if (idMin == null || id < idMin) idMin = id
                    if (idMax == null || id > idMax) idMax = id
                }
            }
        } else {
            // Linkヘッダを読む
            idMin = reMaxId.matcher(result?.link_older ?: "").findOrNull()
                ?.let {
                    EntityId(it.groupEx(1)!!)
                }

            idMax = reMinId.matcher(result?.link_newer ?: "").findOrNull()
                ?.let {
                    // min_idとsince_idの読み分けは現在利用してない it.groupEx(1)=="min_id"
                    EntityId(it.groupEx(2)!!)
                }
        }

        return Pair(idMin, idMax)
    }
    // int scroll_hack;

    // return true if list bottom may have unread remain
    internal fun saveRange(
        bBottom: Boolean,
        bTop: Boolean,
        result: TootApiResult?,
        list: List<TimelineItem>?
    ): Boolean {
        val (idMin, idMax) = parseRange(result, list)

        var hasBottomRemain = false

        if (bBottom) when (idMin) {
            null -> idOld = null // リストの終端
            else -> {
                val i = idOld?.compareTo(idMin)
                if (i == null || i > 0) {
                    idOld = idMin
                    hasBottomRemain = true
                }
            }
        }

        if (bTop) when (idMax) {
            null -> {
                // リロードを許容するため、取得内容がカラでもidRecentを変更しない
            }

            else -> {
                val i = idRecent?.compareTo(idMax)
                if (i == null || i < 0) {
                    idRecent = idMax
                }
            }
        }

        return hasBottomRemain
    }

    // return true if list bottom may have unread remain
    internal fun saveRangeBottom(result: TootApiResult?, list: List<TimelineItem>?) =
        saveRange(true, bTop = false, result = result, list = list)

    // return true if list bottom may have unread remain
    internal fun saveRangeTop(result: TootApiResult?, list: List<TimelineItem>?) =
        saveRange(false, bTop = true, result = result, list = list)

    internal fun addRange(
        bBottom: Boolean,
        path: String,
        delimiter: Char = if (-1 == path.indexOf('?')) '?' else '&'
    ) = if (bBottom) {
        if (idOld != null) "$path${delimiter}max_id=${idOld}" else path
    } else {
        if (idRecent != null) "$path${delimiter}since_id=${idRecent}" else path
    }

    internal fun addRangeMin(
        path: String,
        delimiter: Char = if (-1 != path.indexOf('?')) '&' else '?'
    ) = if (idRecent == null) path else "$path${delimiter}min_id=${idRecent}"

    fun toAdapterIndex(listIndex: Int): Int {
        return if (type.headerType != null) listIndex + 1 else listIndex
    }

    fun toListIndex(adapterIndex: Int): Int {
        return if (type.headerType != null) adapterIndex - 1 else adapterIndex
    }

    ////////////////////////////////////////////////////////////////////////
    // Streaming

    internal fun onStart() {

        // 破棄されたカラムなら何もしない
        if (is_dispose.get()) {
            log.d("onStart: column was disposed.")
            return
        }

        // 未初期化なら何もしない
        if (!bFirstInitialized) {
            log.d("onStart: column is not initialized.")
            return
        }

        // 初期ロード中なら何もしない
        if (bInitialLoading) {
            log.d("onStart: column is in initial loading.")
            return
        }

        // フィルタ一覧のリロードが必要
        if (filter_reload_required) {
            filter_reload_required = false
            startLoading()
            return
        }

        // 始端リフレッシュの最中だった
        // リフレッシュ終了時に自動でストリーミング開始するはず
        if (bRefreshingTop) {
            log.d("onStart: bRefreshingTop is true.")
            return
        }

        if (!bRefreshLoading
            && canAutoRefresh()
            && !Pref.bpDontRefreshOnResume(app_state.pref)
            && !dont_auto_refresh
        ) {
            // リフレッシュしてからストリーミング開始
            log.d("onStart: start auto refresh.")
            startRefresh(bSilent = true, bBottom = false)
        } else if (isSearchColumn) {
            // 検索カラムはリフレッシュもストリーミングもないが、表示開始のタイミングでリストの再描画を行いたい
            fireShowContent(reason = "Column onStart isSearchColumn", reset = true)
        } else if (canStartStreaming() && streamSpec != null) {
            // ギャップつきでストリーミング開始
            this.bPutGap = true
            fireShowColumnStatus()
        }
    }

    fun saveScrollPosition() {
        try {
            if (viewHolder?.saveScrollPosition() == true) {
                val ss = this.scroll_save
                if (ss != null) {
                    val idx = toListIndex(ss.adapterIndex)
                    if (0 <= idx && idx < list_data.size) {
                        val item = list_data[idx]
                        this.last_viewing_item_id = item.getOrderId()
                        // とりあえず保存はするが
                        // TLデータそのものを永続化しないかぎり出番はないっぽい
                    }
                }
            }
        } catch (ex: Throwable) {
            log.e(ex, "can't get last_viewing_item_id.")
        }
    }

    //	fun findListIndexByTimelineId(orderId : EntityId) : Int? {
    //		list_data.forEachIndexed { i, v ->
    //			if(v.getOrderId() == orderId) return i
    //		}
    //		return null
    //	}

    init {
        registerColumnId(column_id, this)
    }
}
