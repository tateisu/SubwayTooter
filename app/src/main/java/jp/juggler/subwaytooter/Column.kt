package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.table.*
import jp.juggler.subwaytooter.util.BucketList
import jp.juggler.subwaytooter.util.ScrollPosition
import jp.juggler.util.*
import okhttp3.Handshake
import org.jetbrains.anko.backgroundDrawable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern
import kotlin.collections.ArrayList

enum class StreamingIndicatorState {
	NONE,
	REGISTERED, // registered, but not listening
	LISTENING,
}

enum class ColumnPagingType {
	Default,
	Cursor,
	Offset,
	None,
}

class Column(
	val app_state : AppState,
	val context : Context,
	val access_info : SavedAccount,
	val column_type : Int,
	val column_id : String
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
		internal const val PATH_HOME = "/api/v1/timelines/home?limit=$READ_LIMIT"
		internal const val PATH_DIRECT_MESSAGES = "/api/v1/timelines/direct?limit=$READ_LIMIT"
		internal const val PATH_DIRECT_MESSAGES2 = "/api/v1/conversations?limit=$READ_LIMIT"
		
		internal const val PATH_LOCAL = "/api/v1/timelines/public?limit=$READ_LIMIT&local=true"
		internal const val PATH_TL_FEDERATE = "/api/v1/timelines/public?limit=$READ_LIMIT"
		internal const val PATH_FAVOURITES = "/api/v1/favourites?limit=$READ_LIMIT"
		
		internal const val PATH_LIST_TL = "/api/v1/timelines/list/%s?limit=$READ_LIMIT"
		
		// アカウントのリストを返すAPI
		internal const val PATH_ACCOUNT_FOLLOWING =
			"/api/v1/accounts/%s/following?limit=$READ_LIMIT" // 1:account_id
		internal const val PATH_ACCOUNT_FOLLOWERS =
			"/api/v1/accounts/%s/followers?limit=$READ_LIMIT" // 1:account_id
		internal const val PATH_MUTES = "/api/v1/mutes?limit=$READ_LIMIT"
		internal const val PATH_BLOCKS = "/api/v1/blocks?limit=$READ_LIMIT"
		internal const val PATH_FOLLOW_REQUESTS = "/api/v1/follow_requests?limit=$READ_LIMIT"
		internal const val PATH_FOLLOW_SUGGESTION = "/api/v1/suggestions?limit=$READ_LIMIT"
		internal const val PATH_ENDORSEMENT = "/api/v1/endorsements?limit=$READ_LIMIT"
		
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
		internal const val PATH_ACCOUNT = "/api/v1/accounts/%s" // 1:account_id
		internal const val PATH_STATUSES = "/api/v1/statuses/%s" // 1:status_id
		internal const val PATH_STATUSES_CONTEXT = "/api/v1/statuses/%s/context" // 1:status_id
		const val PATH_SEARCH = "/api/v1/search?q=%s"
		const val PATH_SEARCH_V2 = "/api/v2/search?q=%s"
		// search args 1: query(urlencoded) , also, append "&resolve=1" if resolve non-local accounts
		// internal const val PATH_INSTANCE = "/api/v1/instance"
		internal const val PATH_LIST_INFO = "/api/v1/lists/%s"
		
		const val PATH_FILTERS = "/api/v1/filters"
		
		const val PATH_MISSKEY_PROFILE_FOLLOWING = "/api/users/following"
		const val PATH_MISSKEY_PROFILE_FOLLOWERS = "/api/users/followers"
		const val PATH_MISSKEY_PROFILE_STATUSES = "/api/users/notes"
		
		const val PATH_MISSKEY_PROFILE = "/api/users/show"
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
		
		internal const val KEY_COLUMN_ACCESS = "column_access"
		internal const val KEY_COLUMN_ACCESS_COLOR = "column_access_color"
		internal const val KEY_COLUMN_ACCESS_COLOR_BG = "column_access_color_bg"
		internal const val KEY_COLUMN_NAME = "column_name"
		internal const val KEY_OLD_INDEX = "old_index"
		
		internal const val TYPE_HOME = 1
		const val TYPE_LOCAL = 2
		internal const val TYPE_FEDERATE = 3
		const val TYPE_PROFILE = 4
		internal const val TYPE_FAVOURITES = 5
		internal const val TYPE_REPORTS = 6
		const val TYPE_NOTIFICATIONS = 7
		const val TYPE_CONVERSATION = 8
		const val TYPE_HASHTAG = 9
		internal const val TYPE_SEARCH = 10
		internal const val TYPE_MUTES = 11
		internal const val TYPE_BLOCKS = 12
		internal const val TYPE_FOLLOW_REQUESTS = 13
		internal const val TYPE_BOOSTED_BY = 14
		internal const val TYPE_FAVOURITED_BY = 15
		internal const val TYPE_DOMAIN_BLOCKS = 16
		internal const val TYPE_SEARCH_MSP = 17
		const val TYPE_INSTANCE_INFORMATION = 18
		internal const val TYPE_LIST_LIST = 19
		internal const val TYPE_LIST_TL = 20
		internal const val TYPE_LIST_MEMBER = 21
		internal const val TYPE_SEARCH_TS = 22
		internal const val TYPE_DIRECT_MESSAGES = 23
		internal const val TYPE_TREND_TAG = 24
		internal const val TYPE_FOLLOW_SUGGESTION = 25
		internal const val TYPE_KEYWORD_FILTER = 26
		internal const val TYPE_MISSKEY_HYBRID = 27
		internal const val TYPE_ENDORSEMENT = 28
		internal const val TYPE_LOCAL_AROUND = 29
		internal const val TYPE_FEDERATED_AROUND = 30
		internal const val TYPE_ACCOUNT_AROUND = 31
		internal const val TYPE_SCHEDULED_STATUS = 33
		internal const val TYPE_HASHTAG_FROM_ACCT = 34
		internal const val TYPE_NOTIFICATION_FROM_ACCT = 35
		
		internal const val TAB_STATUS = 0
		internal const val TAB_FOLLOWING = 1
		internal const val TAB_FOLLOWERS = 2
		
		internal var useInstanceTicker = false
		
		internal const val QUICK_FILTER_ALL = 0
		internal const val QUICK_FILTER_MENTION = 1
		internal const val QUICK_FILTER_FAVOURITE = 2
		internal const val QUICK_FILTER_BOOST = 3
		internal const val QUICK_FILTER_FOLLOW = 4
		internal const val QUICK_FILTER_REACTION = 5
		internal const val QUICK_FILTER_VOTE = 6
		
		internal const val HASHTAG_ELLIPSIZE = 26
		
		@Suppress("UNCHECKED_CAST")
		private inline fun <reified T> getParamAt(params : Array<out Any>, idx : Int) : T {
			return params[idx] as T
		}
		
		@Suppress("UNCHECKED_CAST")
		private inline fun <reified T> getParamAtNullable(params : Array<out Any>, idx : Int) : T? {
			if(idx >= params.size) return null
			return params[idx] as T
		}
		
		fun loadAccount(context : Context, src : JSONObject) : SavedAccount {
			val account_db_id = src.parseLong(KEY_ACCOUNT_ROW_ID) ?: - 1L
			return if(account_db_id >= 0) {
				SavedAccount.loadAccount(context, account_db_id)
					?: throw RuntimeException("missing account")
			} else {
				SavedAccount.na
			}
			
		}
		
		fun getIconId(acct : String, type : Int) =
			when(val item = columnTypeProcMap[type]) {
				null -> R.drawable.ic_info
				else -> item.iconId(acct)
			}
		
		private val channelIdSeed = AtomicInteger(0)
		
		// より古いデータの取得に使う
		internal val reMaxId =
			Pattern.compile("""[&?]max_id=([^&?;\s]+)""")
		
		// より新しいデータの取得に使う (マストドン2.6.0以降)
		private val reMinId =
			Pattern.compile("""[&?]min_id=([^&?;\s]+)""")
		
		// より新しいデータの取得に使う(マストドン2.6.0未満)
		private val reSinceId =
			Pattern.compile("""[&?]since_id=([^&?;\s]+)""")
		
		val COLUMN_REGEX_FILTER_DEFAULT : (CharSequence?) -> Boolean = { false }
		
		private val time_format_hhmm = SimpleDateFormat("HH:mm", Locale.JAPAN)
		
		internal fun getResetTimeString() : String {
			time_format_hhmm.timeZone = TimeZone.getDefault()
			return time_format_hhmm.format(Date(0L))
		}
		
		fun onFiltersChanged(context : Context, access_info : SavedAccount) {
			
			TootTaskRunner(context, progress_style = TootTaskRunner.PROGRESS_NONE).run(access_info,
				object : TootTask {
					
					var filter_list : ArrayList<TootFilter>? = null
					
					override fun background(client : TootApiClient) : TootApiResult? {
						val result = client.request(PATH_FILTERS)
						val jsonArray = result?.jsonArray
						if(jsonArray != null) {
							filter_list = TootFilter.parseList(jsonArray)
						}
						return result
					}
					
					override fun handleResult(result : TootApiResult?) {
						val filter_list = this.filter_list
						if(filter_list != null) {
							val stream_acct = access_info.acct
							log.d("update filters for $stream_acct")
							for(column in App1.getAppState(context).column_list) {
								if(column.access_info.acct == stream_acct) {
									column.onFiltersChanged2(filter_list)
								}
							}
						}
					}
				})
		}
		
		private val columnIdMap = HashMap<String, WeakReference<Column>?>()
		private fun registerColumnId(id : String, column : Column) {
			synchronized(columnIdMap) {
				columnIdMap[id] = WeakReference(column)
			}
		}
		
		private fun generateColumnId() : String {
			synchronized(columnIdMap) {
				val buffer = ByteBuffer.allocate(8)
				var id = ""
				while(id.isEmpty() || columnIdMap.containsKey(id)) {
					if(id.isNotEmpty()) Thread.sleep(1L)
					buffer.clear()
					buffer.putLong(System.currentTimeMillis())
					id = buffer.array().encodeBase64Url()
				}
				columnIdMap[id] = null
				return id
			}
		}
		
		private fun decodeColumnId(src : JSONObject) : String {
			return src.parseString(KEY_COLUMN_ID) ?: generateColumnId()
		}
		
		fun findColumnById(id : String) : Column? {
			synchronized(columnIdMap) {
				return columnIdMap[id]?.get()
			}
		}
		
		fun getBackgroundImageDir(context : Context) : File {
			val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
			if(externalDir == null) {
				log.e("getExternalFilesDir is null.")
			} else {
				val state = Environment.getExternalStorageState()
				if(state != Environment.MEDIA_MOUNTED) {
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
		
		private var defaultColorHeaderBg = 0
		private var defaultColorHeaderName = 0
		private var defaultColorHeaderPageNumber = 0
		var defaultColorContentBg = 0
		private var defaultColorContentAcct = 0
		private var defaultColorContentText = 0
		
		fun reloadDefaultColor(activity : AppCompatActivity, pref : SharedPreferences) {
			
			defaultColorHeaderBg = Pref.ipCcdHeaderBg(pref).notZero()
				?: getAttributeColor(activity, R.attr.color_column_header)
			
			defaultColorHeaderName = Pref.ipCcdHeaderFg(pref).notZero()
				?: getAttributeColor(activity, R.attr.colorColumnHeaderName)
			
			defaultColorHeaderPageNumber = Pref.ipCcdHeaderFg(pref).notZero()
				?: getAttributeColor(activity, R.attr.colorColumnHeaderPageNumber)
			
			defaultColorContentBg = Pref.ipCcdContentBg(pref)
			// may zero
			
			defaultColorContentAcct = Pref.ipCcdContentAcct(pref).notZero()
				?: getAttributeColor(activity, R.attr.colorTimeSmall)
			
			defaultColorContentText = Pref.ipCcdContentText(pref).notZero()
				?: getAttributeColor(activity, R.attr.colorContentText)
			
		}
	}
	
	private var callback_ref : WeakReference<Callback>? = null
	
	private val isActivityStart : Boolean
		get() {
			return callback_ref?.get()?.isActivityStart ?: false
		}
	
	private val streamPath : String?
		get() = if(isMisskey) {
			val misskeyApiToken = access_info.misskeyApiToken
			if(access_info.misskeyVersion >= 11) {
				when {
					makeMisskeyChannelArg() == null -> null
					misskeyApiToken == null -> "/?_=$column_id" // 認証無し
					else -> "/?_=$column_id&i=$misskeyApiToken"
				}
			} else {
				if(misskeyApiToken == null) {
					// Misskey 8.25 からLTLだけ認証なしでも見れるようになった
					when(column_type) {
						TYPE_LOCAL -> "/local-timeline"
						else -> null
					}
				} else {
					when(column_type) {
						TYPE_HOME, TYPE_NOTIFICATIONS -> "/?i=$misskeyApiToken"
						TYPE_LOCAL -> "/local-timeline?i=$misskeyApiToken"
						TYPE_MISSKEY_HYBRID -> "/hybrid-timeline?i=$misskeyApiToken"
						TYPE_FEDERATE -> "/global-timeline?i=$misskeyApiToken"
						TYPE_LIST_TL -> "/user-list?i=$misskeyApiToken&listId=$profile_id"
						else -> null
					}
				}
			}
		} else {
			when(column_type) {
				TYPE_HOME, TYPE_NOTIFICATIONS -> "/api/v1/streaming/?stream=user"
				TYPE_LOCAL -> "/api/v1/streaming/?stream=public:local"
				TYPE_FEDERATE -> "/api/v1/streaming/?stream=public"
				TYPE_LIST_TL -> "/api/v1/streaming/?stream=list&list=$profile_id"
				
				TYPE_DIRECT_MESSAGES -> "/api/v1/streaming/?stream=direct"
				
				TYPE_HASHTAG -> when(instance_local) {
					true -> {
						"/api/v1/streaming/?stream=" + Uri.encode("hashtag:local") +
							"&tag=" + hashtag.encodePercent() + makeHashtagExtraQuery()
					}
					
					else -> "/api/v1/streaming/?stream=hashtag&tag=" + hashtag.encodePercent() +
						makeHashtagExtraQuery()
					// タグ先頭の#を含まない
				}
				else -> null
			}
			
		}
	
	private val isPublicStream : Boolean
		get() {
			return when(column_type) {
				TYPE_LOCAL, TYPE_FEDERATE, TYPE_HASHTAG, TYPE_LOCAL_AROUND, TYPE_FEDERATED_AROUND -> true
				else -> false
			}
		}
	
	internal var dont_close : Boolean = false
	
	internal var with_attachment : Boolean = false
	internal var with_highlight : Boolean = false
	internal var dont_show_boost : Boolean = false
	internal var dont_show_reply : Boolean = false
	
	internal var dont_show_normal_toot : Boolean = false
	
	internal var dont_show_favourite : Boolean = false // 通知カラムのみ
	internal var dont_show_follow : Boolean = false // 通知カラムのみ
	internal var dont_show_reaction : Boolean = false // 通知カラムのみ
	internal var dont_show_vote : Boolean = false // 通知カラムのみ
	
	internal var quick_filter = QUICK_FILTER_ALL
	
	internal var dont_streaming : Boolean = false
	internal var dont_auto_refresh : Boolean = false
	internal var hide_media_default : Boolean = false
	internal var system_notification_not_related : Boolean = false
	internal var instance_local : Boolean = false
	
	internal var enable_speech : Boolean = false
	internal var use_old_api = false
	
	internal var regex_text : String = ""
	
	internal var header_bg_color : Int = 0
	internal var header_fg_color : Int = 0
	internal var column_bg_color : Int = 0
	internal var acct_color : Int = 0
	internal var content_color : Int = 0
	internal var column_bg_image : String = ""
	internal var column_bg_image_alpha = 1f
	
	internal var profile_tab = TAB_STATUS
	
	internal var status_id : EntityId? = null
	
	// プロフカラムではアカウントのID。リストカラムではリストのID
	internal var profile_id : EntityId? = null
	
	internal var search_query : String = ""
	internal var search_resolve : Boolean = false
	internal var instance_uri : String = ""
	internal var hashtag : String = ""
	internal var hashtag_any : String = ""
	internal var hashtag_all : String = ""
	internal var hashtag_none : String = ""
	internal var hashtag_acct : String = ""
	
	// プロフカラムでのアカウント情報
	@Volatile
	internal var who_account : TootAccountRef? = null
	
	// リストカラムでのリスト情報
	@Volatile
	internal var list_info : TootList? = null
	
	// 「インスタンス情報」カラムに表示するインスタンス情報
	// (SavedAccount中のインスタンス情報とは異なるので注意)
	internal var instance_information : TootInstance? = null
	internal var handshake : Handshake? = null
	
	internal var scroll_save : ScrollPosition? = null
	private var last_viewing_item_id : EntityId? = null
	
	internal val is_dispose = AtomicBoolean()
	
	internal var bFirstInitialized = false
	
	var filter_reload_required : Boolean = false
	
	//////////////////////////////////////////////////////////////////////////////////////
	
	// カラムを閉じた後のnotifyDataSetChangedのタイミングで、add/removeされる順序が期待通りにならないので
	// 参照を１つだけ持つのではなく、リストを保持して先頭の要素を使うことにする
	
	private val _holder_list = LinkedList<ColumnViewHolder>()
	
	internal // 複数のリスナがある場合、最も新しいものを返す
	val viewHolder : ColumnViewHolder?
		get() {
			if(is_dispose.get()) return null
			return if(_holder_list.isEmpty()) null else _holder_list.first
		}
	
	//////////////////////////////////////////////////////////////////////////////////////
	
	internal var lastTask : ColumnTask? = null
	
	internal var bInitialLoading : Boolean = false
	internal var bRefreshLoading : Boolean = false
	
	internal var mInitialLoadingError : String = ""
	internal var mRefreshLoadingError : String = ""
	internal var mRefreshLoadingErrorTime : Long = 0L
	internal var mRefreshLoadingErrorPopupState : Int = 0
	
	internal var task_progress : String? = null
	
	internal val list_data = BucketList<TimelineItem>()
	internal val duplicate_map = DuplicateMap()
	
	internal val isFilterEnabled : Boolean
		get() = (with_attachment
			|| with_highlight
			|| regex_text.isNotEmpty()
			|| dont_show_normal_toot
			|| quick_filter != QUICK_FILTER_ALL
			|| dont_show_boost
			|| dont_show_favourite
			|| dont_show_follow
			|| dont_show_reply
			|| dont_show_reaction
			|| dont_show_vote
			)
	
	@Volatile
	private var column_regex_filter = COLUMN_REGEX_FILTER_DEFAULT
	
	@Volatile
	internal var muted_word2 : WordTrieTree? = null
	
	@Volatile
	private var favMuteSet : HashSet<String>? = null
	
	@Volatile
	internal var highlight_trie : WordTrieTree? = null
	
	// タイムライン中のデータの始端と終端
	// misskeyは
	internal var idRecent : EntityId? = null
	internal var idOld : EntityId? = null
	internal var offsetNext : Int = 0
	internal var pagingType : ColumnPagingType = ColumnPagingType.Default
	
	var bRefreshingTop : Boolean = false
	
	// ListViewの表示更新が追いつかないとスクロール位置が崩れるので
	// 一定時間より短期間にはデータ更新しないようにする
	private val last_show_stream_data = AtomicLong(0L)
	private val stream_data_queue = ConcurrentLinkedQueue<TimelineItem>()
	
	private var bPutGap : Boolean = false
	
	internal var useDate : Boolean = false
	
	@Suppress("unused")
	val listTitle : String
		get() {
			return when(column_type) {
				TYPE_LIST_MEMBER, TYPE_LIST_TL -> {
					val sv = list_info?.title
					if(sv != null && sv.isNotEmpty()) sv else profile_id.toString()
				}
				
				else -> "?"
			}
		}
	
	@Suppress("unused")
	val listId : EntityId?
		get() {
			return when(column_type) {
				TYPE_LIST_MEMBER, TYPE_LIST_TL -> profile_id
				else -> null
			}
		}
	
	val isSearchColumn : Boolean
		get() {
			return when(column_type) {
				TYPE_SEARCH, TYPE_SEARCH_MSP, TYPE_SEARCH_TS -> true
				else -> false
			}
		}
	
	internal interface Callback {
		val isActivityStart : Boolean
	}
	
	internal constructor(
		app_state : AppState,
		access_info : SavedAccount,
		callback : Callback,
		type : Int,
		vararg params : Any
	)
		: this(app_state, app_state.context, access_info, type, generateColumnId()) {
		this.callback_ref = WeakReference(callback)
		when(type) {
			TYPE_CONVERSATION, TYPE_BOOSTED_BY, TYPE_FAVOURITED_BY, TYPE_LOCAL_AROUND, TYPE_FEDERATED_AROUND, TYPE_ACCOUNT_AROUND -> status_id =
				getParamAt(params, 0)
			TYPE_PROFILE, TYPE_LIST_TL, TYPE_LIST_MEMBER -> profile_id = getParamAt(params, 0)
			TYPE_HASHTAG -> hashtag = getParamAt(params, 0)
			
			TYPE_HASHTAG_FROM_ACCT -> {
				hashtag = getParamAt(params, 0)
				hashtag_acct = getParamAt(params, 1)
			}
			
			TYPE_NOTIFICATION_FROM_ACCT -> {
				hashtag_acct = getParamAt(params, 0)
			}
			
			TYPE_SEARCH -> {
				search_query = getParamAt(params, 0)
				search_resolve = getParamAt(params, 1)
			}
			
			TYPE_SEARCH_MSP, TYPE_SEARCH_TS -> search_query = getParamAt(params, 0)
			TYPE_INSTANCE_INFORMATION -> instance_uri = getParamAt(params, 0)
		}
	}
	
	internal constructor(app_state : AppState, src : JSONObject)
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
		dont_streaming = src.optBoolean(KEY_DONT_STREAMING)
		dont_auto_refresh = src.optBoolean(KEY_DONT_AUTO_REFRESH)
		hide_media_default = src.optBoolean(KEY_HIDE_MEDIA_DEFAULT)
		system_notification_not_related = src.optBoolean(KEY_SYSTEM_NOTIFICATION_NOT_RELATED)
		instance_local = src.optBoolean(KEY_INSTANCE_LOCAL)
		quick_filter = src.optInt(KEY_QUICK_FILTER, 0)
		
		enable_speech = src.optBoolean(KEY_ENABLE_SPEECH)
		use_old_api = src.optBoolean(KEY_USE_OLD_API)
		last_viewing_item_id = EntityId.from(src, KEY_LAST_VIEWING_ITEM)
		
		regex_text = src.parseString(KEY_REGEX_TEXT) ?: ""
		
		header_bg_color = src.optInt(KEY_HEADER_BACKGROUND_COLOR)
		header_fg_color = src.optInt(KEY_HEADER_TEXT_COLOR)
		column_bg_color = src.optInt(KEY_COLUMN_BACKGROUND_COLOR)
		acct_color = src.optInt(KEY_COLUMN_ACCT_TEXT_COLOR)
		content_color = src.optInt(KEY_COLUMN_CONTENT_TEXT_COLOR)
		column_bg_image = src.parseString(KEY_COLUMN_BACKGROUND_IMAGE) ?: ""
		column_bg_image_alpha = src.optDouble(KEY_COLUMN_BACKGROUND_IMAGE_ALPHA, 1.0).toFloat()
		
		when(column_type) {
			
			TYPE_CONVERSATION, TYPE_BOOSTED_BY, TYPE_FAVOURITED_BY, TYPE_LOCAL_AROUND, TYPE_FEDERATED_AROUND, TYPE_ACCOUNT_AROUND ->
				status_id = EntityId.mayNull(src.parseString(KEY_STATUS_ID))
			
			TYPE_PROFILE -> {
				profile_id = EntityId.mayNull(src.parseString(KEY_PROFILE_ID))
				profile_tab = src.optInt(KEY_PROFILE_TAB)
			}
			
			TYPE_LIST_MEMBER, TYPE_LIST_TL -> {
				profile_id = EntityId.mayNull(src.parseString(KEY_PROFILE_ID))
			}
			
			TYPE_HASHTAG -> {
				hashtag = src.optString(KEY_HASHTAG)
				hashtag_any = src.optString(KEY_HASHTAG_ANY)
				hashtag_all = src.optString(KEY_HASHTAG_ALL)
				hashtag_none = src.optString(KEY_HASHTAG_NONE)
			}
			
			TYPE_HASHTAG_FROM_ACCT -> {
				hashtag_acct = src.optString(KEY_HASHTAG_ACCT)
				hashtag = src.optString(KEY_HASHTAG)
				hashtag_any = src.optString(KEY_HASHTAG_ANY)
				hashtag_all = src.optString(KEY_HASHTAG_ALL)
				hashtag_none = src.optString(KEY_HASHTAG_NONE)
			}
			
			TYPE_NOTIFICATION_FROM_ACCT -> {
				hashtag_acct = src.optString(KEY_HASHTAG_ACCT)
			}
			
			TYPE_SEARCH -> {
				search_query = src.optString(KEY_SEARCH_QUERY)
				search_resolve = src.optBoolean(KEY_SEARCH_RESOLVE, false)
			}
			
			TYPE_SEARCH_MSP, TYPE_SEARCH_TS -> search_query = src.optString(KEY_SEARCH_QUERY)
			
			TYPE_INSTANCE_INFORMATION -> instance_uri = src.optString(KEY_INSTANCE_URI)
		}
	}
	
	private fun JSONObject.putIfTrue(key : String, value : Boolean) {
		if(value) put(key, true)
	}
	
	@Throws(JSONException::class)
	fun encodeJSON(dst : JSONObject, old_index : Int) {
		dst.put(KEY_ACCOUNT_ROW_ID, access_info.db_id)
		dst.put(KEY_TYPE, column_type)
		dst.put(KEY_COLUMN_ID, column_id)
		
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
		dst.putIfTrue(KEY_DONT_STREAMING, dont_streaming)
		dst.putIfTrue(KEY_DONT_AUTO_REFRESH, dont_auto_refresh)
		dst.putIfTrue(KEY_HIDE_MEDIA_DEFAULT, hide_media_default)
		dst.putIfTrue(KEY_SYSTEM_NOTIFICATION_NOT_RELATED, system_notification_not_related)
		dst.putIfTrue(KEY_INSTANCE_LOCAL, instance_local)
		dst.putIfTrue(KEY_ENABLE_SPEECH, enable_speech)
		dst.putIfTrue(KEY_USE_OLD_API, use_old_api)
		dst.put(KEY_QUICK_FILTER, quick_filter)
		
		last_viewing_item_id?.putTo(dst, KEY_LAST_VIEWING_ITEM)
		
		dst.put(KEY_REGEX_TEXT, regex_text)
		
		dst.put(KEY_HEADER_BACKGROUND_COLOR, header_bg_color)
		dst.put(KEY_HEADER_TEXT_COLOR, header_fg_color)
		dst.put(KEY_COLUMN_BACKGROUND_COLOR, column_bg_color)
		dst.put(KEY_COLUMN_ACCT_TEXT_COLOR, acct_color)
		dst.put(KEY_COLUMN_CONTENT_TEXT_COLOR, content_color)
		dst.put(KEY_COLUMN_BACKGROUND_IMAGE, column_bg_image)
		dst.put(KEY_COLUMN_BACKGROUND_IMAGE_ALPHA, column_bg_image_alpha.toDouble())
		
		when(column_type) {
			
			TYPE_CONVERSATION, TYPE_BOOSTED_BY, TYPE_FAVOURITED_BY, TYPE_LOCAL_AROUND, TYPE_FEDERATED_AROUND, TYPE_ACCOUNT_AROUND ->
				dst.put(KEY_STATUS_ID, status_id.toString())
			
			TYPE_PROFILE ->
				dst.put(KEY_PROFILE_ID, profile_id.toString()).put(KEY_PROFILE_TAB, profile_tab)
			
			TYPE_LIST_MEMBER, TYPE_LIST_TL ->
				dst.put(KEY_PROFILE_ID, profile_id.toString())
			
			TYPE_HASHTAG -> {
				dst.put(KEY_HASHTAG, hashtag)
				dst.put(KEY_HASHTAG_ANY, hashtag_any)
				dst.put(KEY_HASHTAG_ALL, hashtag_all)
				dst.put(KEY_HASHTAG_NONE, hashtag_none)
			}
			
			TYPE_HASHTAG_FROM_ACCT -> {
				dst.put(KEY_HASHTAG_ACCT, hashtag_acct)
				dst.put(KEY_HASHTAG, hashtag)
				dst.put(KEY_HASHTAG_ANY, hashtag_any)
				dst.put(KEY_HASHTAG_ALL, hashtag_all)
				dst.put(KEY_HASHTAG_NONE, hashtag_none)
			}
			
			TYPE_NOTIFICATION_FROM_ACCT -> {
				dst.put(KEY_HASHTAG_ACCT, hashtag_acct)
			}
			
			TYPE_SEARCH -> dst.put(KEY_SEARCH_QUERY, search_query).put(
				KEY_SEARCH_RESOLVE,
				search_resolve
			)
			
			TYPE_SEARCH_MSP, TYPE_SEARCH_TS -> dst.put(KEY_SEARCH_QUERY, search_query)
			
			TYPE_INSTANCE_INFORMATION -> dst.put(KEY_INSTANCE_URI, instance_uri)
		}
		
		// 以下は保存には必要ないが、カラムリスト画面で使う
		val ac = AcctColor.load(access_info.acct)
		dst.put(KEY_COLUMN_ACCESS, if(AcctColor.hasNickname(ac)) ac.nickname else access_info.acct)
		dst.put(KEY_COLUMN_ACCESS_COLOR, ac.color_fg)
		dst.put(KEY_COLUMN_ACCESS_COLOR_BG, ac.color_bg)
		dst.put(KEY_COLUMN_NAME, getColumnName(true))
		dst.put(KEY_OLD_INDEX, old_index)
	}
	
	internal fun isSameSpec(ai : SavedAccount, type : Int, params : Array<out Any>) : Boolean {
		if(type != column_type || ai.acct != access_info.acct) return false
		
		return try {
			when(type) {
				
				TYPE_PROFILE, TYPE_LIST_TL, TYPE_LIST_MEMBER ->
					profile_id == EntityId(getParamAt(params, 0))
				
				TYPE_CONVERSATION, TYPE_BOOSTED_BY, TYPE_FAVOURITED_BY, TYPE_LOCAL_AROUND, TYPE_FEDERATED_AROUND, TYPE_ACCOUNT_AROUND ->
					status_id == EntityId(getParamAt(params, 0))
				
				TYPE_HASHTAG -> {
					(getParamAt<String>(params, 0) == hashtag)
						&& ((getParamAtNullable<String>(params, 1) ?: "") == hashtag_any)
						&& ((getParamAtNullable<String>(params, 2) ?: "") == hashtag_all)
						&& ((getParamAtNullable<String>(params, 3) ?: "") == hashtag_none)
				}
				
				TYPE_HASHTAG_FROM_ACCT -> {
					(getParamAt<String>(params, 0) == hashtag)
						&& ((getParamAtNullable<String>(params, 1) ?: "") == hashtag_acct)
				}
				
				TYPE_NOTIFICATION_FROM_ACCT -> {
					((getParamAtNullable<String>(params, 0) ?: "") == hashtag_acct)
				}
				
				TYPE_SEARCH -> getParamAt<String>(params, 0) == search_query && getParamAt<Boolean>(
					params,
					1
				) == search_resolve
				
				TYPE_SEARCH_MSP, TYPE_SEARCH_TS -> getParamAt<String>(params, 0) == search_query
				
				TYPE_INSTANCE_INFORMATION -> getParamAt<String>(params, 0) == instance_uri
				
				else -> true
			}
		} catch(ex : Throwable) {
			log.trace(ex)
			false
		}
	}
	
	internal fun getColumnName(long : Boolean) =
		when(val item = columnTypeProcMap[column_type]) {
			null -> "?"
			else -> item.name2(this, long) ?: item.name(context)
		}
	
	fun getNotificationTypeString() : String {
		val sb = StringBuilder()
		sb.append("(")
		
		when(quick_filter) {
			QUICK_FILTER_ALL -> {
				var n = 0
				if(! dont_show_reply) {
					if(n ++ > 0) sb.append(", ")
					sb.append(context.getString(R.string.notification_type_mention))
				}
				if(! dont_show_follow) {
					if(n ++ > 0) sb.append(", ")
					sb.append(context.getString(R.string.notification_type_follow))
				}
				if(! dont_show_boost) {
					if(n ++ > 0) sb.append(", ")
					sb.append(context.getString(R.string.notification_type_boost))
				}
				if(! dont_show_favourite) {
					if(n ++ > 0) sb.append(", ")
					sb.append(context.getString(R.string.notification_type_favourite))
				}
				if(isMisskey && ! dont_show_reaction) {
					if(n ++ > 0) sb.append(", ")
					sb.append(context.getString(R.string.notification_type_reaction))
				}
				if(! dont_show_vote) {
					if(n ++ > 0) sb.append(", ")
					sb.append(context.getString(R.string.notification_type_vote))
				}
				val n_max = if(isMisskey) {
					6
				} else {
					5
				}
				if(n == 0 || n == n_max) return "" // 全部か皆無なら部分表記は要らない
			}
			
			QUICK_FILTER_MENTION -> sb.append(context.getString(R.string.notification_type_mention))
			QUICK_FILTER_FAVOURITE -> sb.append(context.getString(R.string.notification_type_favourite))
			QUICK_FILTER_BOOST -> sb.append(context.getString(R.string.notification_type_boost))
			QUICK_FILTER_FOLLOW -> sb.append(context.getString(R.string.notification_type_follow))
			QUICK_FILTER_REACTION -> sb.append(context.getString(R.string.notification_type_reaction))
			QUICK_FILTER_VOTE -> sb.append(context.getString(R.string.notification_type_vote))
		}
		
		sb.append(")")
		return sb.toString()
	}
	
	internal fun dispose() {
		is_dispose.set(true)
		stopStreaming()
		
		for(vh in _holder_list) {
			try {
				vh.listView.adapter = null
			} catch(ignored : Throwable) {
			}
		}
	}
	
	internal fun getIconId(type : Int) =
		getIconId(access_info.acct, type)
	
	// ブーストやお気に入りの更新に使う。ステータスを列挙する。
	fun findStatus(
		target_instance : String,
		target_status_id : EntityId,
		callback : (account : SavedAccount, status : TootStatus) -> Boolean
		// callback return true if rebind view required
	) {
		if(! access_info.host.equals(target_instance, ignoreCase = true)) return
		
		var bChanged = false
		
		fun procStatus(status : TootStatus?) {
			if(status != null) {
				if(target_status_id == status.id) {
					if(callback(access_info, status)) bChanged = true
				}
				procStatus(status.reblog)
			}
		}
		
		for(data in list_data) {
			when(data) {
				is TootNotification -> procStatus(data.status)
				is TootStatus -> procStatus(data)
			}
		}
		
		if(bChanged) fireRebindAdapterItems()
	}
	
	// ミュート、ブロックが成功した時に呼ばれる
	// リストメンバーカラムでメンバーをリストから除去した時に呼ばれる
	fun removeAccountInTimeline(
		target_account : SavedAccount,
		who_id : EntityId,
		removeFromUserList : Boolean = false
	) {
		if(target_account.acct != access_info.acct) return
		
		val INVALID_ACCOUNT = - 1L
		
		val tmp_list = ArrayList<TimelineItem>(list_data.size)
		for(o in list_data) {
			if(o is TootStatus) {
				if(who_id == (o.account.id)) continue
				if(who_id == (o.reblog?.account?.id ?: INVALID_ACCOUNT)) continue
			} else if(o is TootNotification) {
				if(who_id == (o.account?.id ?: INVALID_ACCOUNT)) continue
				if(who_id == (o.status?.account?.id ?: INVALID_ACCOUNT)) continue
				if(who_id == (o.status?.reblog?.account?.id ?: INVALID_ACCOUNT)) continue
			} else if(o is TootAccountRef && removeFromUserList) {
				if(who_id == o.get().id) continue
			}
			
			tmp_list.add(o)
		}
		if(tmp_list.size != list_data.size) {
			list_data.clear()
			list_data.addAll(tmp_list)
			fireShowContent(reason = "removeAccountInTimeline")
		}
	}
	
	// misskeyカラムやプロフカラムでブロック成功した時に呼ばれる
	fun updateFollowIcons(target_account : SavedAccount) {
		if(target_account.acct != access_info.acct) return
		
		fireShowContent(reason = "updateFollowIcons", reset = true)
	}
	
	fun removeUser(targetAccount : SavedAccount, columnType : Int, who_id : EntityId) {
		if(column_type == columnType && targetAccount.acct == access_info.acct) {
			val tmp_list = ArrayList<TimelineItem>(list_data.size)
			for(o in list_data) {
				if(o is TootAccountRef) {
					if(o.get().id == who_id) continue
				}
				tmp_list.add(o)
			}
			if(tmp_list.size != list_data.size) {
				list_data.clear()
				list_data.addAll(tmp_list)
				fireShowContent(reason = "removeUser")
			}
		}
	}
	
	// ステータスが削除された時に呼ばれる
	fun onStatusRemoved(tl_host : String, status_id : EntityId) {
		
		if(is_dispose.get() || bInitialLoading || bRefreshLoading) return
		
		if(tl_host.equals(access_info.host, ignoreCase = true)) {
			val tmp_list = ArrayList<TimelineItem>(list_data.size)
			for(o in list_data) {
				if(o is TootStatus) {
					if(status_id == o.id) continue
					if(status_id == (o.reblog?.id ?: - 1L)) continue
				} else if(o is TootNotification) {
					val s = o.status
					if(s != null) {
						if(status_id == s.id) continue
						if(status_id == (s.reblog?.id ?: - 1L)) continue
					}
				}
				
				tmp_list.add(o)
			}
			if(tmp_list.size != list_data.size) {
				list_data.clear()
				list_data.addAll(tmp_list)
				fireShowContent(reason = "removeStatus")
			}
			
		}
		
	}
	
	fun removeNotifications() {
		cancelLastTask()
		
		mRefreshLoadingErrorPopupState = 0
		mRefreshLoadingError = ""
		bRefreshLoading = false
		mInitialLoadingError = ""
		bInitialLoading = false
		idOld = null
		idRecent = null
		offsetNext = 0
		pagingType = ColumnPagingType.Default
		
		list_data.clear()
		duplicate_map.clear()
		fireShowContent(reason = "removeNotifications", reset = true)
		
		PollingWorker.queueNotificationCleared(context, access_info.db_id)
	}
	
	val isNotificationColumn : Boolean
		get() = when(column_type) {
			TYPE_NOTIFICATIONS, TYPE_NOTIFICATION_FROM_ACCT -> true
			else -> false
		}
	
	fun removeNotificationOne(target_account : SavedAccount, notification : TootNotification) {
		if(! isNotificationColumn) return
		
		if(access_info.acct != target_account.acct) return
		
		val tmp_list = ArrayList<TimelineItem>(list_data.size)
		for(o in list_data) {
			if(o is TootNotification) {
				if(o.id == notification.id) continue
			}
			
			tmp_list.add(o)
		}
		
		if(tmp_list.size != list_data.size) {
			list_data.clear()
			list_data.addAll(tmp_list)
			fireShowContent(reason = "removeNotificationOne")
		}
	}
	
	fun onMuteUpdated() {
		
		val checker = { status : TootStatus? -> status?.checkMuted() ?: false }
		
		val tmp_list = ArrayList<TimelineItem>(list_data.size)
		for(o in list_data) {
			if(o is TootStatus) {
				if(checker(o)) continue
			}
			if(o is TootNotification) {
				if(checker(o.status)) continue
			}
			tmp_list.add(o)
		}
		if(tmp_list.size != list_data.size) {
			list_data.clear()
			list_data.addAll(tmp_list)
			fireShowContent(reason = "onMuteUpdated")
		}
	}
	
	fun onHideFavouriteNotification(acct : String) {
		if(! isNotificationColumn) return
		
		val tmp_list = ArrayList<TimelineItem>(list_data.size)
		
		for(o in list_data) {
			if(o is TootNotification && o.type != TootNotification.TYPE_MENTION) {
				val a = o.account
				if(a != null) {
					val a_acct = access_info.getFullAcct(a)
					if(a_acct == acct) continue
				}
			}
			tmp_list.add(o)
		}
		if(tmp_list.size != list_data.size) {
			list_data.clear()
			list_data.addAll(tmp_list)
			fireShowContent(reason = "onHideFavouriteNotification")
		}
	}
	
	fun onDomainBlockChanged(target_account : SavedAccount, domain : String, bBlocked : Boolean) {
		if(target_account.host != access_info.host) return
		if(access_info.isPseudo) return
		
		if(column_type == TYPE_DOMAIN_BLOCKS) {
			// ドメインブロック一覧を読み直す
			startLoading()
			return
		}
		
		if(bBlocked) {
			// ブロックしたのとドメイン部分が一致するアカウントからのステータスと通知をすべて除去する
			val reDomain = Pattern.compile("[^@]+@\\Q$domain\\E\\z", Pattern.CASE_INSENSITIVE)
			val checker =
				{ acct : String? -> if(acct == null) false else reDomain.matcher(acct).find() }
			
			val tmp_list = ArrayList<TimelineItem>(list_data.size)
			
			for(o in list_data) {
				if(o is TootStatus) {
					if(checker(o.account.acct)) continue
					if(checker(o.reblog?.account?.acct)) continue
				} else if(o is TootNotification) {
					if(checker(o.account?.acct)) continue
					if(checker(o.status?.account?.acct)) continue
					if(checker(o.status?.reblog?.account?.acct)) continue
				}
				tmp_list.add(o)
			}
			if(tmp_list.size != list_data.size) {
				list_data.clear()
				list_data.addAll(tmp_list)
				fireShowContent(reason = "onDomainBlockChanged")
			}
			
		}
		
	}
	
	fun onListListUpdated(account : SavedAccount) {
		if(column_type == TYPE_LIST_LIST && access_info.acct == account.acct) {
			startLoading()
			val vh = viewHolder
			vh?.onListListUpdated()
		}
	}
	
	fun onListNameUpdated(account : SavedAccount, item : TootList) {
		if(access_info.acct != account.acct) return
		when(column_type) {
			TYPE_LIST_LIST -> {
				startLoading()
			}
			
			TYPE_LIST_TL, TYPE_LIST_MEMBER -> {
				if(item.id == profile_id) {
					this.list_info = item
					fireShowColumnHeader()
				}
			}
		}
	}
	
	fun onListMemberUpdated(
		account : SavedAccount,
		list_id : EntityId,
		who : TootAccount,
		bAdd : Boolean
	) {
		if(column_type == TYPE_LIST_TL && access_info.acct == account.acct && list_id == profile_id) {
			if(! bAdd) {
				removeAccountInTimeline(account, who.id)
			}
		} else if(column_type == TYPE_LIST_MEMBER && access_info.acct == account.acct && list_id == profile_id) {
			if(! bAdd) {
				removeAccountInTimeline(account, who.id)
			}
		}
		
	}
	
	internal fun addColumnViewHolder(cvh : ColumnViewHolder) {
		
		// 現在のリストにあるなら削除する
		removeColumnViewHolder(cvh)
		
		// 最後に追加されたものが先頭にくるようにする
		// 呼び出しの後に必ず追加されているようにする
		_holder_list.addFirst(cvh)
	}
	
	internal fun removeColumnViewHolder(cvh : ColumnViewHolder) {
		val it = _holder_list.iterator()
		while(it.hasNext()) {
			if(cvh == it.next()) it.remove()
		}
	}
	
	internal fun removeColumnViewHolderByActivity(activity : ActMain) {
		val it = _holder_list.iterator()
		while(it.hasNext()) {
			val cvh = it.next()
			if(cvh.activity == activity) {
				it.remove()
			}
		}
	}
	
	internal fun hasMultipleViewHolder() : Boolean {
		return _holder_list.size > 1
	}
	
	internal fun fireShowContent(
		reason : String,
		changeList : List<AdapterChange>? = null,
		reset : Boolean = false
	) {
		if(! isMainThread) {
			throw RuntimeException("fireShowContent: not on main thread.")
		}
		viewHolder?.showContent(reason, changeList, reset)
	}
	
	internal fun fireShowColumnHeader() {
		if(! isMainThread) {
			throw RuntimeException("fireShowColumnHeader: not on main thread.")
		}
		viewHolder?.showColumnHeader()
	}
	
	internal fun fireShowColumnStatus() {
		if(! isMainThread) {
			throw RuntimeException("fireShowColumnStatus: not on main thread.")
		}
		viewHolder?.showColumnStatus()
		
	}
	
	internal fun fireColumnColor() {
		if(! isMainThread) {
			throw RuntimeException("fireColumnColor: not on main thread.")
		}
		viewHolder?.showColumnColor()
	}
	
	fun fireRelativeTime() {
		if(! isMainThread) {
			throw RuntimeException("fireRelativeTime: not on main thread.")
		}
		viewHolder?.updateRelativeTime()
	}
	
	fun fireRebindAdapterItems() {
		if(! isMainThread) {
			throw RuntimeException("fireRelativeTime: not on main thread.")
		}
		viewHolder?.rebindAdapterItems()
	}
	
	private fun cancelLastTask() {
		if(lastTask != null) {
			lastTask?.cancel(true)
			lastTask = null
			//
			bInitialLoading = false
			bRefreshLoading = false
			mInitialLoadingError = context.getString(R.string.cancelled)
		}
	}
	
	private fun initFilter() {
		column_regex_filter = COLUMN_REGEX_FILTER_DEFAULT
		val regex_text = this.regex_text
		if(regex_text.isNotEmpty()) {
			try {
				val re = Pattern.compile(regex_text)
				column_regex_filter =
					{ text : CharSequence? ->
						if(text?.isEmpty() != false) false else re.matcher(
							text
						).find()
					}
			} catch(ex : Throwable) {
				log.trace(ex)
			}
		}
		
		favMuteSet = FavMute.acctSet
		highlight_trie = HighlightWord.nameSet
	}
	
	private fun isFilteredByAttachment(status : TootStatus) : Boolean {
		// オプションがどれも設定されていないならフィルタしない(false)
		if(! (with_attachment || with_highlight)) return false
		
		val matchMedia = with_attachment && status.reblog?.hasMedia() ?: status.hasMedia()
		val matchHighlight = with_highlight && status.reblog?.hasHighlight ?: status.hasHighlight
		
		// どれかの条件を満たすならフィルタしない(false)、どれも満たさないならフィルタする(true)
		return ! (matchMedia || matchHighlight)
	}
	
	internal fun isFiltered(status : TootStatus) : Boolean {
		
		// word mute2
		status.updateFiltered(muted_word2)
		
		if(isFilteredByAttachment(status)) return true
		
		if(dont_show_boost) {
			if(status.reblog != null) return true
		}
		
		if(dont_show_reply) {
			if(status.in_reply_to_id != null) return true
			if(status.reblog?.in_reply_to_id != null) return true
		}
		
		if(dont_show_normal_toot) {
			if(status.in_reply_to_id == null && status.reblog == null) return true
		}
		
		if(column_regex_filter(status.decoded_content)) return true
		if(column_regex_filter(status.reblog?.decoded_content)) return true
		if(column_regex_filter(status.decoded_spoiler_text)) return true
		if(column_regex_filter(status.reblog?.decoded_spoiler_text)) return true
		
		return status.checkMuted()
	}
	
	internal fun isFiltered(item : TootNotification) : Boolean {
		
		if(when(quick_filter) {
				QUICK_FILTER_ALL -> when(item.type) {
					TootNotification.TYPE_FAVOURITE -> dont_show_favourite
					
					TootNotification.TYPE_REBLOG,
					TootNotification.TYPE_RENOTE,
					TootNotification.TYPE_QUOTE -> dont_show_boost
					
					TootNotification.TYPE_FOLLOW_REQUEST,
					TootNotification.TYPE_FOLLOW -> dont_show_follow
					
					TootNotification.TYPE_MENTION,
					TootNotification.TYPE_REPLY -> dont_show_reply
					
					TootNotification.TYPE_REACTION -> dont_show_reaction
					
					TootNotification.TYPE_VOTE,
					TootNotification.TYPE_POLL -> dont_show_vote
					else -> false
				}
				
				else -> when(item.type) {
					TootNotification.TYPE_FAVOURITE -> quick_filter != QUICK_FILTER_FAVOURITE
					TootNotification.TYPE_REBLOG,
					TootNotification.TYPE_RENOTE,
					TootNotification.TYPE_QUOTE -> quick_filter != QUICK_FILTER_BOOST
					TootNotification.TYPE_FOLLOW_REQUEST,
					TootNotification.TYPE_FOLLOW -> quick_filter != QUICK_FILTER_FOLLOW
					TootNotification.TYPE_MENTION,
					TootNotification.TYPE_REPLY -> quick_filter != QUICK_FILTER_MENTION
					TootNotification.TYPE_REACTION -> quick_filter != QUICK_FILTER_REACTION
					TootNotification.TYPE_VOTE,
					TootNotification.TYPE_POLL -> quick_filter != QUICK_FILTER_VOTE
					else -> true
				}
			}) {
			log.d("isFiltered: ${item.type} notification filtered.")
			return true
		}
		
		val status = item.status
		
		status?.updateFiltered(muted_word2)
		
		if(status?.checkMuted() == true) {
			log.d("isFiltered: status muted.")
			return true
		}
		
		// ふぁぼ魔ミュート
		when(item.type) {
			TootNotification.TYPE_REBLOG,
			TootNotification.TYPE_RENOTE,
			TootNotification.TYPE_QUOTE,
			TootNotification.TYPE_FAVOURITE,
			TootNotification.TYPE_REACTION,
			TootNotification.TYPE_VOTE,
			TootNotification.TYPE_FOLLOW -> {
				val who = item.account
				if(who != null && favMuteSet?.contains(access_info.getFullAcct(who)) == true) {
					PollingWorker.log.d("%s is in favMuteSet.", access_info.getFullAcct(who))
					return true
				}
			}
		}
		
		return false
	}
	
	//	@Nullable String parseMaxId( TootApiResult result ){
	//		if( result != null && result.link_older != null ){
	//			Matcher m = reMaxId.matcher( result.link_older );
	//			if( m.get() ) return m.group( 1 );
	//		}
	//		return null;
	//	}
	
	internal fun loadProfileAccount(
		client : TootApiClient,
		parser : TootParser,
		bForceReload : Boolean
	) : TootApiResult? {
		
		return if(this.who_account != null && ! bForceReload) {
			// リロード不要なら何もしない
			null
		} else if(isMisskey) {
			val params = access_info.putMisskeyApiToken(JSONObject())
				.put("userId", profile_id)
			
			val result = client.request(PATH_MISSKEY_PROFILE, params.toPostRequestBuilder())
			
			// ユーザリレーションの取り扱いのため、別のparserを作ってはいけない
			parser.misskeyDecodeProfilePin = true
			try {
				val a = TootAccountRef.mayNull(parser, parser.account(result?.jsonObject))
				if(a != null) {
					this.who_account = a
					client.publishApiProgress("") // カラムヘッダの再表示
				}
			} finally {
				parser.misskeyDecodeProfilePin = false
			}
			
			result
			
		} else {
			val result = client.request(String.format(Locale.JAPAN, PATH_ACCOUNT, profile_id))
			val a = TootAccountRef.mayNull(parser, parser.account(result?.jsonObject))
			if(a != null) {
				this.who_account = a
				client.publishApiProgress("") // カラムヘッダの再表示
			}
			result
		}
		
	}
	
	internal fun loadListInfo(client : TootApiClient, bForceReload : Boolean) {
		val parser = TootParser(context, access_info)
		if(bForceReload || this.list_info == null) {
			val result = if(isMisskey) {
				val params = makeMisskeyBaseParameter(parser)
					.put("listId", profile_id)
				client.request("/api/users/lists/show", params.toPostRequestBuilder())
			} else {
				client.request(String.format(Locale.JAPAN, PATH_LIST_INFO, profile_id))
			}
			val jsonObject = result?.jsonObject
			if(jsonObject != null) {
				val data = parseItem(::TootList, parser, jsonObject)
				if(data != null) {
					this.list_info = data
					client.publishApiProgress("") // カラムヘッダの再表示
				}
			}
		}
	}
	
	private inner class UpdateRelationEnv {
		internal val who_set = HashSet<EntityId>()
		internal val acct_set = HashSet<String>()
		internal val tag_set = HashSet<String>()
		
		internal fun add(whoRef : TootAccountRef?) {
			add(whoRef?.get())
		}
		
		internal fun add(who : TootAccount?) {
			who ?: return
			who_set.add(who.id)
			acct_set.add("@" + access_info.getFullAcct(who))
			//
			add(who.movedRef)
		}
		
		internal fun add(s : TootStatus?) {
			if(s == null) return
			add(s.accountRef)
			add(s.reblog)
			s.tags?.forEach { tag_set.add(it.name) }
		}
		
		internal fun add(n : TootNotification?) {
			if(n == null) return
			add(n.accountRef)
			add(n.status)
		}
		
		internal fun update(client : TootApiClient, parser : TootParser) {
			
			var n : Int
			var size : Int
			
			if(isMisskey) {
				
				// parser内部にアカウントIDとRelationのマップが生成されるので、それをデータベースに記録する
				run {
					val now = System.currentTimeMillis()
					val who_list = parser.misskeyUserRelationMap.entries.toMutableList()
					var start = 0
					val end = who_list.size
					while(start < end) {
						var step = end - start
						if(step > RELATIONSHIP_LOAD_STEP) step = RELATIONSHIP_LOAD_STEP
						UserRelation.saveListMisskey(now, access_info.db_id, who_list, start, step)
						start += step
					}
					log.d("updateRelation: update %d relations.", end)
				}
				
				// 2018/11/1 Misskeyにもリレーション取得APIができた
				// アカウントIDの集合からRelationshipを取得してデータベースに記録する
				
				size = who_set.size
				if(size > 0) {
					val who_list = ArrayList<EntityId>(size)
					who_list.addAll(who_set)
					
					val now = System.currentTimeMillis()
					
					n = 0
					while(n < who_list.size) {
						val userIdList = ArrayList<EntityId>(RELATIONSHIP_LOAD_STEP)
						for(i in 0 until RELATIONSHIP_LOAD_STEP) {
							if(n >= size) break
							if(! parser.misskeyUserRelationMap.containsKey(who_list[n])) {
								userIdList.add(who_list[n])
							}
							++ n
						}
						if(userIdList.isEmpty()) continue
						
						val params = access_info.putMisskeyApiToken()
							.put("userId", JSONArray().apply {
								for(id in userIdList) put(id.toString())
							})
						
						val result =
							client.request("/api/users/relation", params.toPostRequestBuilder())
						
						if(result == null || result.response?.code() in 400 until 500) break
						
						val list = parseList(::TootRelationShip, parser, result.jsonArray)
						if(list.size == userIdList.size) {
							for(i in 0 until list.size) {
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
				if(size > 0) {
					val who_list = ArrayList<EntityId>(size)
					who_list.addAll(who_set)
					
					val now = System.currentTimeMillis()
					
					n = 0
					while(n < who_list.size) {
						val sb = StringBuilder()
						sb.append("/api/v1/accounts/relationships")
						for(i in 0 until RELATIONSHIP_LOAD_STEP) {
							if(n >= size) break
							sb.append(if(i == 0) '?' else '&')
							sb.append("id[]=")
							sb.append(who_list[n ++].toString())
						}
						val result = client.request(sb.toString()) ?: break // cancelled.
						val list = parseList(::TootRelationShip, parser, result.jsonArray)
						if(list.size > 0) UserRelation.saveListMastodon(
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
			if(size > 0) {
				val acct_list = ArrayList<String?>(size)
				acct_list.addAll(acct_set)
				
				val now = System.currentTimeMillis()
				
				n = 0
				while(n < acct_list.size) {
					var length = size - n
					if(length > ACCT_DB_STEP) length = ACCT_DB_STEP
					AcctSet.saveList(now, acct_list, n, length)
					n += length
				}
				log.d("updateRelation: update %d acct.", n)
				
			}
			
			// 出現したタグをデータベースに記録する
			size = tag_set.size
			if(size > 0) {
				val tag_list = ArrayList<String?>(size)
				tag_list.addAll(tag_set)
				
				val now = System.currentTimeMillis()
				
				n = 0
				while(n < tag_list.size) {
					var length = size - n
					if(length > ACCT_DB_STEP) length = ACCT_DB_STEP
					TagSet.saveList(now, tag_list, n, length)
					n += length
				}
				log.d("updateRelation: update %d tag.", n)
			}
		}
		
	}
	
	//
	internal fun updateRelation(
		client : TootApiClient,
		list : ArrayList<TimelineItem>?,
		whoRef : TootAccountRef?,
		parser : TootParser
	) {
		if(access_info.isPseudo) return
		
		val env = UpdateRelationEnv()
		
		env.add(whoRef)
		
		list?.forEach {
			when(it) {
				is TootAccountRef -> env.add(it)
				is TootStatus -> env.add(it)
				is TootNotification -> env.add(it)
				is TootConversationSummary -> env.add(it.last_status)
			}
		}
		env.update(client, parser)
	}
	
	// DMカラム更新時に新APIの利用に成功したなら真
	internal var useConversationSummarys = false
	
	// DMカラムのストリーミングイベントで新形式のイベントを利用できたなら真
	internal var useConversationSummaryStreaming = false
	
	internal fun startLoading() {
		cancelLastTask()
		
		stopStreaming()
		
		initFilter()
		
		useInstanceTicker = Pref.bpInstanceTicker(app_state.pref)
		
		mRefreshLoadingErrorPopupState = 0
		mRefreshLoadingError = ""
		mInitialLoadingError = ""
		bFirstInitialized = true
		bInitialLoading = true
		bRefreshLoading = false
		idOld = null
		idRecent = null
		offsetNext = 0
		pagingType = ColumnPagingType.Default
		
		duplicate_map.clear()
		list_data.clear()
		fireShowContent(reason = "loading start", reset = true)
		
		@SuppressLint("StaticFieldLeak")
		val task = ColumnTask_Loading(this)
		this.lastTask = task
		task.executeOnExecutor(App1.task_executor)
	}
	
	private var bMinIdMatched : Boolean = false
	
	internal fun parseRange(
		result : TootApiResult?,
		list : List<TimelineItem>?
	) : Pair<EntityId?, EntityId?> {
		var idMin : EntityId? = null
		var idMax : EntityId? = null
		
		if(isMisskey && list != null) {
			// MisskeyはLinkヘッダがないので、常にデータからIDを読む
			for(item in list) {
				val id = item.getOrderId()
				if(idMin == null || id < idMin) idMin = id
				if(idMax == null || id > idMax) idMax = id
			}
		} else if(result != null) {
			// Linkヘッダを読む
			idMin = if(result.link_older == null) {
				null
			} else {
				val m = reMaxId.matcher(result.link_older ?: "")
				if(m.find()) {
					EntityId(m.group(1))
				} else {
					null
				}
			}
			
			idMax = if(result.link_newer == null) {
				null
			} else {
				var m = reMinId.matcher(result.link_newer ?: "")
				if(m.find()) {
					bMinIdMatched = true
					EntityId(m.group(1))
				} else {
					m = reSinceId.matcher(result.link_newer ?: "")
					if(m.find()) {
						bMinIdMatched = false
						EntityId(m.group(1))
					} else {
						null
					}
				}
			}
		}
		
		return Pair(idMin, idMax)
	}
	// int scroll_hack;
	
	// return true if list bottom may have unread remain
	internal fun saveRange(
		bBottom : Boolean,
		bTop : Boolean,
		result : TootApiResult?,
		list : List<TimelineItem>?
	) : Boolean {
		val (idMin, idMax) = parseRange(result, list)
		
		var hasBottomRemain = false
		
		if(bBottom) when(idMin) {
			null -> idOld = null // リストの終端
			else -> {
				val i = idOld?.compareTo(idMin)
				if(i == null || i > 0) {
					idOld = idMin
					hasBottomRemain = true
				}
			}
		}
		
		if(bTop) when(idMax) {
			null -> {
				// リロードを許容するため、取得内容がカラでもidRecentを変更しない
			}
			
			else -> {
				val i = idRecent?.compareTo(idMax)
				if(i == null || i < 0) {
					idRecent = idMax
				}
			}
		}
		
		return hasBottomRemain
	}
	
	// return true if list bottom may have unread remain
	internal fun saveRangeEnd(result : TootApiResult?, list : List<TimelineItem>?) =
		saveRange(true, bTop = false, result = result, list = list)
	
	// return true if list bottom may have unread remain
	internal fun saveRangeStart(result : TootApiResult?, list : List<TimelineItem>?) =
		saveRange(false, bTop = true, result = result, list = list)
	
	internal fun addRange(bBottom : Boolean, path : String) : String {
		val delimiter = if(- 1 != path.indexOf('?')) '&' else '?'
		if(bBottom) {
			if(idOld != null) return "$path${delimiter}max_id=${idOld}"
		} else {
			if(idRecent != null) return "$path${delimiter}since_id=${idRecent}"
		}
		return path
	}
	
	internal fun addRangeMin(path : String) : String {
		val delimiter = if(- 1 != path.indexOf('?')) '&' else '?'
		if(idRecent != null) return "$path${delimiter}min_id=${idRecent}"
		return path
	}
	
	internal fun startRefreshForPost(
		refresh_after_post : Int,
		posted_status_id : EntityId,
		posted_reply_id : EntityId?
	) {
		when(column_type) {
			TYPE_HOME, TYPE_LOCAL, TYPE_FEDERATE, TYPE_DIRECT_MESSAGES, TYPE_MISSKEY_HYBRID -> {
				startRefresh(
					bSilent = true,
					bBottom = false,
					posted_status_id = posted_status_id,
					refresh_after_toot = refresh_after_post
				)
			}
			
			TYPE_PROFILE -> {
				if(profile_tab == TAB_STATUS
					&& profile_id == access_info.loginAccount?.id
				) {
					startRefresh(
						bSilent = true,
						bBottom = false,
						posted_status_id = posted_status_id,
						refresh_after_toot = refresh_after_post
					)
				}
			}
			
			TYPE_CONVERSATION -> {
				// 会話への返信が行われたなら会話を更新する
				try {
					if(posted_reply_id != null) {
						for(item in list_data) {
							if(item is TootStatus && item.id == posted_reply_id) {
								startLoading()
								break
							}
						}
					}
				} catch(_ : Throwable) {
				}
			}
		}
	}
	
	internal fun startRefresh(
		bSilent : Boolean,
		bBottom : Boolean,
		posted_status_id : EntityId? = null,
		refresh_after_toot : Int = - 1
	) {
		
		if(lastTask != null) {
			if(! bSilent) {
				showToast(context, true, R.string.column_is_busy)
				val holder = viewHolder
				if(holder != null) holder.refreshLayout.isRefreshing = false
			}
			return
		} else if(bBottom && ! canRefreshBottom()) {
			if(! bSilent) {
				showToast(context, true, R.string.end_of_list)
				val holder = viewHolder
				if(holder != null) holder.refreshLayout.isRefreshing = false
			}
			return
		} else if(! bBottom && ! canRefreshTop()) {
			val holder = viewHolder
			if(holder != null) holder.refreshLayout.isRefreshing = false
			startLoading()
			return
		}
		
		if(bSilent) {
			val holder = viewHolder
			if(holder != null) {
				holder.refreshLayout.isRefreshing = true
			}
		}
		
		if(! bBottom) {
			bRefreshingTop = true
			stopStreaming()
		}
		
		bRefreshLoading = true
		mRefreshLoadingError = ""
		
		@SuppressLint("StaticFieldLeak")
		val task = ColumnTask_Refresh(this, bSilent, bBottom, posted_status_id, refresh_after_toot)
		this.lastTask = task
		task.executeOnExecutor(App1.task_executor)
		fireShowColumnStatus()
	}
	
	internal fun startGap(gap : TimelineItem?) {
		
		if(gap == null) {
			showToast(context, true, "gap is null")
			return
		}
		
		if(lastTask != null) {
			showToast(context, true, R.string.column_is_busy)
			return
		}
		
		@Suppress("UNNECESSARY_SAFE_CALL")
		viewHolder?.refreshLayout?.isRefreshing = true
		
		bRefreshLoading = true
		mRefreshLoadingError = ""
		
		@SuppressLint("StaticFieldLeak")
		val task = ColumnTask_Gap(this, gap)
		
		this.lastTask = task
		task.executeOnExecutor(App1.task_executor)
		fireShowColumnStatus()
	}
	
	enum class HeaderType(val viewType : Int) {
		Profile(1),
		Search(2),
		Instance(3),
		Filter(4),
	}
	
	val headerType : HeaderType?
		get() = when(column_type) {
			TYPE_PROFILE -> HeaderType.Profile
			TYPE_SEARCH -> HeaderType.Search
			TYPE_SEARCH_MSP -> HeaderType.Search
			TYPE_SEARCH_TS -> HeaderType.Search
			TYPE_INSTANCE_INFORMATION -> HeaderType.Instance
			TYPE_KEYWORD_FILTER -> HeaderType.Filter
			else -> null
		}
	
	fun toAdapterIndex(listIndex : Int) : Int {
		return if(headerType != null) listIndex + 1 else listIndex
	}
	
	fun toListIndex(adapterIndex : Int) : Int {
		return if(headerType != null) adapterIndex - 1 else adapterIndex
	}
	
	private fun loadSearchDesc(raw_en : Int, raw_ja : Int) : String {
		val res_id = if("ja" == context.getString(R.string.language_code)) raw_ja else raw_en
		return context.loadRawResource(res_id).decodeUTF8()
	}
	
	private var cacheHeaderDesc : String? = null
	
	fun getHeaderDesc() : String? {
		var cache = cacheHeaderDesc
		if(cache != null) return cache
		cache = when(column_type) {
			TYPE_SEARCH -> context.getString(R.string.search_desc_mastodon_api)
			TYPE_SEARCH_MSP -> loadSearchDesc(
				R.raw.search_desc_msp_en,
				R.raw.search_desc_msp_ja
			)
			TYPE_SEARCH_TS -> loadSearchDesc(
				R.raw.search_desc_ts_en,
				R.raw.search_desc_ts_ja
			)
			else -> ""
		}
		cacheHeaderDesc = cache
		return cache
	}
	
	////////////////////////////////////////////////////////////////////////
	// Streaming
	
	internal fun onStart(callback : Callback) {
		this.callback_ref = WeakReference(callback)
		
		// 破棄されたカラムなら何もしない
		if(is_dispose.get()) {
			log.d("onStart: column was disposed.")
			return
		}
		
		// 未初期化なら何もしない
		if(! bFirstInitialized) {
			log.d("onStart: column is not initialized.")
			return
		}
		
		// 初期ロード中なら何もしない
		if(bInitialLoading) {
			log.d("onStart: column is in initial loading.")
			return
		}
		
		// フィルタ一覧のリロードが必要
		if(filter_reload_required) {
			filter_reload_required = false
			startLoading()
			return
		}
		
		// 始端リフレッシュの最中だった
		// リフレッシュ終了時に自動でストリーミング開始するはず
		if(bRefreshingTop) {
			log.d("onStart: bRefreshingTop is true.")
			return
		}
		
		if(! bRefreshLoading
			&& canAutoRefresh()
			&& ! Pref.bpDontRefreshOnResume(App1.getAppState(context).pref)
			&& ! dont_auto_refresh
		) {
			// リフレッシュしてからストリーミング開始
			log.d("onStart: start auto refresh.")
			startRefresh(bSilent = true, bBottom = false)
		} else if(isSearchColumn) {
			// 検索カラムはリフレッシュもストリーミングもないが、表示開始のタイミングでリストの再描画を行いたい
			fireShowContent(reason = "Column onStart isSearchColumn", reset = true)
		} else {
			// ギャップつきでストリーミング開始
			log.d("onStart: start streaming with gap.")
			resumeStreaming(true)
		}
	}
	
	// カラム設定に正規表現フィルタを含めるなら真
	fun canStatusFilter() : Boolean {
		if(getFilterContext() != TootFilter.CONTEXT_NONE) return true
		
		return when(column_type) {
			TYPE_SEARCH_MSP, TYPE_SEARCH_TS -> true
			else -> false
		}
	}
	
	// マストドン2.4.3rcのキーワードフィルタのコンテキスト
	private fun getFilterContext() = when(column_type) {
		TYPE_HOME, TYPE_LIST_TL, TYPE_MISSKEY_HYBRID -> TootFilter.CONTEXT_HOME
		TYPE_NOTIFICATIONS, TYPE_NOTIFICATION_FROM_ACCT -> TootFilter.CONTEXT_NOTIFICATIONS
		TYPE_CONVERSATION -> TootFilter.CONTEXT_THREAD
		TYPE_LOCAL, TYPE_FEDERATE, TYPE_HASHTAG, TYPE_HASHTAG_FROM_ACCT, TYPE_PROFILE, TYPE_SEARCH -> TootFilter.CONTEXT_PUBLIC
		TYPE_DIRECT_MESSAGES -> TootFilter.CONTEXT_PUBLIC
		else -> TootFilter.CONTEXT_NONE
		// TYPE_MISSKEY_HYBRID はHOMEでもPUBLICでもある… Misskeyだし関係ないが、NONEにするとアプリ内で完結するフィルタも働かなくなる
	}
	
	// カラム設定に「すべての画像を隠す」ボタンを含めるなら真
	internal fun canNSFWDefault() : Boolean {
		return canStatusFilter()
	}
	
	// カラム設定に「ブーストを表示しない」ボタンを含めるなら真
	fun canFilterBoost() : Boolean {
		return when(column_type) {
			TYPE_HOME, TYPE_MISSKEY_HYBRID, TYPE_PROFILE, TYPE_NOTIFICATIONS, TYPE_NOTIFICATION_FROM_ACCT, TYPE_LIST_TL -> true
			TYPE_LOCAL, TYPE_FEDERATE, TYPE_HASHTAG, TYPE_SEARCH -> isMisskey
			TYPE_HASHTAG_FROM_ACCT -> false
			TYPE_CONVERSATION, TYPE_DIRECT_MESSAGES -> isMisskey
			else -> false
		}
	}
	
	// カラム設定に「返信を表示しない」ボタンを含めるなら真
	fun canFilterReply() : Boolean {
		return when(column_type) {
			TYPE_HOME, TYPE_MISSKEY_HYBRID, TYPE_PROFILE, TYPE_NOTIFICATIONS, TYPE_NOTIFICATION_FROM_ACCT, TYPE_LIST_TL, TYPE_DIRECT_MESSAGES -> true
			TYPE_LOCAL, TYPE_FEDERATE, TYPE_HASHTAG, TYPE_SEARCH -> isMisskey
			TYPE_HASHTAG_FROM_ACCT -> true
			else -> false
		}
	}
	
	fun canFilterNormalToot() : Boolean {
		return when(column_type) {
			TYPE_HOME, TYPE_MISSKEY_HYBRID, TYPE_LIST_TL -> true
			TYPE_LOCAL, TYPE_FEDERATE, TYPE_HASHTAG, TYPE_SEARCH -> isMisskey
			TYPE_HASHTAG_FROM_ACCT -> true
			else -> false
		}
	}
	
	internal fun canAutoRefresh() : Boolean {
		return streamPath != null
	}
	
	internal fun hasHashtagExtra() = when {
		isMisskey -> false
		column_type == TYPE_HASHTAG -> true
		
		// TYPE_HASHTAG_FROM_ACCT は追加のタグを指定しても結果に反映されない
		
		else -> false
	}
	
	fun StringBuilder.appendHashtagExtra() : StringBuilder {
		val limit = (HASHTAG_ELLIPSIZE * 2 - min(length, HASHTAG_ELLIPSIZE)) / 3
		if(hashtag_any.isNotBlank()) append(' ').append(
			context.getString(
				R.string.hashtag_title_any,
				hashtag_any.ellipsizeDot3(limit)
			)
		)
		if(hashtag_all.isNotBlank()) append(' ').append(
			context.getString(
				R.string.hashtag_title_all,
				hashtag_all.ellipsizeDot3(limit)
			)
		)
		if(hashtag_none.isNotBlank()) append(' ').append(
			context.getString(
				R.string.hashtag_title_none,
				hashtag_none.ellipsizeDot3(limit)
			)
		)
		return this
	}
	
	fun canReloadWhenRefreshTop() : Boolean {
		return when(column_type) {
			
			TYPE_KEYWORD_FILTER,
			TYPE_SEARCH,
			TYPE_SEARCH_MSP,
			TYPE_SEARCH_TS,
			TYPE_CONVERSATION,
			TYPE_LIST_LIST,
			TYPE_TREND_TAG,
			TYPE_FOLLOW_SUGGESTION -> true
			
			TYPE_LIST_MEMBER,
			TYPE_MUTES,
			TYPE_FOLLOW_REQUESTS -> isMisskey
			
			else -> false
		}
	}
	
	// カラム操作的にリフレッシュを許容するかどうか
	fun canRefreshTopBySwipe() : Boolean {
		return canReloadWhenRefreshTop() || when(column_type) {
			TYPE_CONVERSATION,
			TYPE_INSTANCE_INFORMATION -> false
			else -> true
		}
	}
	
	// カラム操作的にリフレッシュを許容するかどうか
	fun canRefreshBottomBySwipe() : Boolean {
		return when(column_type) {
			TYPE_LIST_LIST,
			TYPE_CONVERSATION,
			TYPE_INSTANCE_INFORMATION,
			TYPE_KEYWORD_FILTER,
			TYPE_SEARCH,
			TYPE_TREND_TAG,
			TYPE_FOLLOW_SUGGESTION -> false
			
			TYPE_FOLLOW_REQUESTS -> isMisskey
			
			TYPE_LIST_MEMBER -> ! isMisskey
			
			else -> true
		}
	}
	
	// データ的にリフレッシュを許容するかどうか
	private fun canRefreshTop() : Boolean {
		return when(pagingType) {
			ColumnPagingType.Default -> idRecent != null
			else -> false
		}
	}
	
	// データ的にリフレッシュを許容するかどうか
	private fun canRefreshBottom() : Boolean {
		return when(pagingType) {
			ColumnPagingType.Default, ColumnPagingType.Cursor -> idOld != null
			ColumnPagingType.None -> false
			ColumnPagingType.Offset -> true
		}
	}
	
	internal fun canSpeech() : Boolean {
		return canStreaming() && ! isNotificationColumn
	}
	
	internal fun canStreaming() = when {
		access_info.isNA -> false
		access_info.isMisskey -> streamPath != null
		access_info.isPseudo -> isPublicStream
		else -> streamPath != null
	}
	
	private fun createMisskeyConnectChannelMessage(
		channel : String,
		params : JSONObject = JSONObject()
	) =
		JSONObject().apply {
			put("type", "connect")
			put("body", JSONObject().apply {
				put("channel", channel)
				put("id", streamCallback._channelId)
				put("params", params)
			})
		}
	
	private fun makeMisskeyChannelArg() : JSONObject? {
		return if(access_info.misskeyVersion < 11) {
			null
		} else {
			val misskeyApiToken = access_info.misskeyApiToken
			if(misskeyApiToken == null) {
				when(column_type) {
					TYPE_LOCAL -> createMisskeyConnectChannelMessage("localTimeline")
					else -> null
				}
			} else {
				when(column_type) {
					TYPE_HOME -> createMisskeyConnectChannelMessage("homeTimeline")
					TYPE_LOCAL -> createMisskeyConnectChannelMessage("localTimeline")
					TYPE_MISSKEY_HYBRID -> createMisskeyConnectChannelMessage("hybridTimeline")
					TYPE_FEDERATE -> createMisskeyConnectChannelMessage("globalTimeline")
					TYPE_NOTIFICATIONS -> createMisskeyConnectChannelMessage("main")
					// TYPE_LIST_TL
					// TYPE_HASHTAG
					else -> null
				}
			}
		}
	}
	
	private val streamCallback = object : StreamReader.StreamCallback {
		
		val _channelId = channelIdSeed.incrementAndGet().toString()
		
		override fun channelId() = _channelId
		
		override fun onListeningStateChanged(bListen : Boolean) {
			if(is_dispose.get()) return
			runOnMainLooper {
				if(is_dispose.get()) return@runOnMainLooper
				fireShowColumnStatus()
				
				if(bListen) {
					streamReader?.registerMisskeyChannel(makeMisskeyChannelArg())
					updateMisskeyCapture()
				}
				
			}
		}
		
		override fun onTimelineItem(item : TimelineItem) {
			if(is_dispose.get()) return
			
			if(item is TootConversationSummary) {
				if(column_type != TYPE_DIRECT_MESSAGES) return
				if(isFiltered(item.last_status)) return
				if(use_old_api) {
					useConversationSummaryStreaming = false
					return
				} else {
					useConversationSummaryStreaming = true
				}
				
			} else if(item is TootNotification) {
				if(! isNotificationColumn) return
				if(isFiltered(item)) return
				
			} else if(item is TootStatus) {
				if(isNotificationColumn) return
				
				// マストドン2.6.0形式のDMカラム用イベントを利用したならば、その直後に発生する普通の投稿イベントを無視する
				if(useConversationSummaryStreaming) return
				
				if(column_type == TYPE_LOCAL && ! isMisskey && item.account.acct.indexOf('@') != - 1) return
				if(isFiltered(item)) return
			}
			
			stream_data_queue.add(item)
			
			val handler = App1.getAppState(context).handler
			handler.post(mergeStreamingMessage)
		}
		
		override fun onNoteUpdated(ev : MisskeyNoteUpdate) {
			// userId が自分かどうか調べる
			// アクセストークンの更新をして自分のuserIdが分かる状態でないとキャプチャ結果を反映させない
			// （でないとリアクションの2重カウントなどが発生してしまう)
			val myId = EntityId.from(access_info.token_info, TootApiClient.KEY_USER_ID)
			if(myId == null) {
				log.w("onNoteUpdated: missing my userId. updating access token is recommenced!!")
				return
			}
			
			val byMe = myId == ev.userId
			
			runOnMainLooper {
				if(is_dispose.get()) return@runOnMainLooper
				
				val changeList = ArrayList<AdapterChange>()
				
				fun scanStatus1(s : TootStatus?, idx : Int, block : (s : TootStatus) -> Boolean) {
					s ?: return
					if(s.id == ev.noteId) {
						if(block(s)) {
							changeList.add(AdapterChange(AdapterChangeType.RangeChange, idx, 1))
						}
					}
					scanStatus1(s.reblog, idx, block)
					scanStatus1(s.reply, idx, block)
				}
				
				fun scanStatusAll(block : (s : TootStatus) -> Boolean) {
					for(i in 0 until list_data.size) {
						val o = list_data[i]
						if(o is TootStatus) {
							scanStatus1(o, i, block)
						} else if(o is TootNotification) {
							scanStatus1(o.status, i, block)
						}
					}
				}
				
				when(ev.type) {
					MisskeyNoteUpdate.Type.REACTION -> {
						scanStatusAll { s ->
							s.increaseReaction(ev.reaction, byMe, "onNoteUpdated ${ev.userId}")
						}
					}
					
					MisskeyNoteUpdate.Type.UNREACTION -> {
						scanStatusAll { s ->
							s.decreaseReaction(ev.reaction, byMe, "onNoteUpdated ${ev.userId}")
						}
					}
					
					MisskeyNoteUpdate.Type.VOTED -> {
						scanStatusAll { s ->
							s.enquete?.increaseVote(context, ev.choice, byMe) ?: false
						}
					}
					
					MisskeyNoteUpdate.Type.DELETED -> {
						scanStatusAll { s ->
							s.markDeleted(context, ev.deletedAt) ?: false
						}
					}
				}
				
				if(changeList.isNotEmpty()) {
					fireShowContent(reason = "onNoteUpdated", changeList = changeList)
				}
			}
		}
	}
	
	internal fun resumeStreaming(bPutGap : Boolean) {
		
		// カラム種別によってはストリーミングAPIを利用できない
		val stream_path = streamPath ?: return
		
		// 疑似アカウントではストリーミングAPIを利用できない
		// 2.1 では公開ストリームのみ利用できるらしい
		if(access_info.isNA || access_info.isPseudo && ! isPublicStream) {
			return
		}
		
		if(! isActivityStart) {
			log.d("resumeStreaming: isActivityStart is false.")
			return
		}
		
		// 破棄されたカラムなら何もしない
		if(is_dispose.get()) {
			log.d("resumeStreaming: column was disposed.")
			return
		}
		
		// 未初期化なら何もしない
		if(! bFirstInitialized) {
			log.d("resumeStreaming: column is not initialized.")
			return
		}
		
		// 初期ロード中なら何もしない
		if(bInitialLoading) {
			log.d("resumeStreaming: is in initial loading.")
			return
		}
		
		if(Pref.bpDontUseStreaming(context)) {
			log.d("resumeStreaming: disabled in app setting.")
			return
		}
		
		if(dont_streaming) {
			log.d("resumeStreaming: disabled in column setting.")
			return
		}
		
		this.bPutGap = bPutGap
		
		stream_data_queue.clear()
		
		streamReader = app_state.stream_reader.register(
			access_info,
			stream_path,
			highlight_trie,
			streamCallback
		)
		fireShowColumnStatus()
	}
	
	private var streamReader : StreamReader.Reader? = null
	
	// onPauseの時はまとめて止められるが
	// カラム破棄やリロード開始時は個別にストリーミングを止める必要がある
	internal fun stopStreaming() {
		streamReader = null
		val stream_path = streamPath
		if(stream_path != null) {
			app_state.stream_reader.unregister(access_info, stream_path, streamCallback)
			fireShowColumnStatus()
		}
	}
	
	fun getStreamingStatus() : StreamingIndicatorState {
		if(is_dispose.get() || ! bFirstInitialized) return StreamingIndicatorState.NONE
		val stream_path = streamPath ?: return StreamingIndicatorState.NONE
		return app_state.stream_reader.getStreamingStatus(access_info, stream_path, streamCallback)
	}
	
	private val mergeStreamingMessage : Runnable = object : Runnable {
		override fun run() {
			
			// 前回マージしてから暫くは待機する
			val handler = App1.getAppState(context).handler
			val now = SystemClock.elapsedRealtime()
			val remain = last_show_stream_data.get() + 333L - now
			if(remain > 0) {
				handler.removeCallbacks(this)
				handler.postDelayed(this, remain)
				return
			}
			last_show_stream_data.set(now)
			
			val tmpList = ArrayList<TimelineItem>()
			while(stream_data_queue.isNotEmpty()) {
				tmpList.add(stream_data_queue.poll())
			}
			if(tmpList.isEmpty()) return
			
			// キューから読めた件数が0の場合を除き、少し後に再処理させることでマージ漏れを防ぐ
			handler.postDelayed(this, 333L)
			
			tmpList.sortBy { it.getOrderId() }
			tmpList.reverse()
			
			val list_new = duplicate_map.filterDuplicate(tmpList)
			if(list_new.isEmpty()) return
			
			for(item in list_new) {
				if(enable_speech && item is TootStatus) {
					App1.getAppState(context).addSpeech(item.reblog ?: item)
				}
			}
			
			// 通知カラムならストリーミング経由で届いたデータを通知ワーカーに伝達する
			if(isNotificationColumn) {
				val list = ArrayList<TootNotification>()
				for(o in list_new) {
					if(o is TootNotification) {
						list.add(o)
					}
				}
				if(list.isNotEmpty()) {
					PollingWorker.injectData(context, access_info, list)
				}
			}
			
			// 最新のIDをsince_idとして覚える(ソートはしない)
			var new_id_max : EntityId? = null
			var new_id_min : EntityId? = null
			for(o in list_new) {
				try {
					val id = o.getOrderId()
					if(id.toString().isEmpty()) continue
					if(new_id_max == null || id > new_id_max) new_id_max = id
					if(new_id_min == null || id < new_id_min) new_id_min = id
				} catch(ex : Throwable) {
					// IDを取得できないタイプのオブジェクトだった
					// ストリームに来るのは通知かステータスだから、多分ここは通らない
					log.trace(ex)
				}
			}
			
			val tmpRecent = idRecent
			val tmpNewMax = new_id_max
			
			if(tmpNewMax != null && (tmpRecent?.compareTo(tmpNewMax) ?: - 1) == - 1) {
				idRecent = tmpNewMax
				// XXX: コレはリフレッシュ時に取得漏れを引き起こすのでは…？
				// しかしコレなしだとリフレッシュ時に大量に読むことになる…
			}
			
			val holder = viewHolder
			
			// 事前にスクロール位置を覚えておく
			val holder_sp : ScrollPosition? = holder?.scrollPosition
			
			// idx番目の要素がListViewの上端から何ピクセル下にあるか
			var restore_idx = - 2
			var restore_y = 0
			if(holder != null) {
				if(list_data.size > 0) {
					try {
						restore_idx = holder.findFirstVisibleListItem()
						restore_y = holder.getListItemOffset(restore_idx)
					} catch(ex : IndexOutOfBoundsException) {
						restore_idx = - 2
						restore_y = 0
					}
				}
			}
			
			// 画面復帰時の自動リフレッシュではギャップが残る可能性がある
			if(bPutGap) {
				bPutGap = false
				try {
					if(list_data.size > 0 && new_id_min != null) {
						val since = list_data[0].getOrderId()
						if(new_id_min > since) {
							val gap = TootGap(new_id_min, since)
							list_new.add(gap)
						}
					}
				} catch(ex : Throwable) {
					log.e(ex, "can't put gap.")
				}
				
			}
			
			val changeList = ArrayList<AdapterChange>()
			
			replaceConversationSummary(changeList, list_new, list_data)
			
			val added = list_new.size  // may 0
			
			loop@ for(o in list_new) {
				when(o) {
					
					is TootStatus -> {
						val highlight_sound = o.highlight_sound
						if(highlight_sound != null) {
							App1.sound(highlight_sound)
							break@loop
						}
					}
				}
			}
			
			changeList.add(AdapterChange(AdapterChangeType.RangeInsert, 0, added))
			list_data.addAll(0, list_new)
			
			fireShowContent(reason = "mergeStreamingMessage", changeList = changeList)
			
			if(holder != null) {
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
					
					restore_idx < - 1 -> {
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
				if(scroll_save == null || scroll_save.isHead) {
					// スクロール位置が先頭なら先頭のまま
				} else {
					// 現在の要素が表示され続けるようにしたい
					scroll_save.adapterIndex += added
				}
			}
			
			updateMisskeyCapture()
		}
	}
	
	private fun min(a : Int, b : Int) : Int = if(a < b) a else b
	
	internal fun updateMisskeyCapture() {
		if(! isMisskey) return
		streamReader ?: return
		
		val max = 40
		val list = ArrayList<EntityId>(max * 2) // リブログなどで膨れる場合がある
		
		fun add(s : TootStatus?) {
			s ?: return
			list.add(s.id)
			add(s.reblog)
			add(s.reply)
		}
		
		for(i in 0 until min(max, list_data.size)) {
			val o = list_data[i]
			if(o is TootStatus) {
				add(o)
			} else if(o is TootNotification) {
				add(o.status)
			}
		}
		
		if(list.isNotEmpty()) streamReader?.capture(list)
	}
	
	internal fun replaceConversationSummary(
		changeList : ArrayList<AdapterChange>,
		list_new : ArrayList<TimelineItem>,
		list_data : BucketList<TimelineItem>
	) {
		
		val newMap = HashMap<EntityId, TootConversationSummary>()
		
		for(o in list_new) {
			if(o is TootConversationSummary) newMap[o.id] = o
		}
		
		if(list_data.isEmpty() || newMap.isEmpty()) return
		
		val removeSet = HashSet<EntityId>()
		for(i in list_data.size - 1 downTo 0) {
			val o = list_data[i] as? TootConversationSummary ?: continue
			val newItem = newMap[o.id] ?: continue
			
			if(o.last_status.uri == newItem.last_status.uri) {
				// 投稿が同じなので順序を入れ替えず、その場所で更新する
				changeList.add(AdapterChange(AdapterChangeType.RangeChange, i, 1))
				list_data[i] = newItem
				removeSet.add(newItem.id)
				log.d("replaceConversationSummary: in-place update")
			} else {
				// 投稿が異なるので古い方を削除して、リストの順序を変える
				changeList.add(AdapterChange(AdapterChangeType.RangeRemove, i, 1))
				list_data.removeAt(i)
				log.d("replaceConversationSummary: order change")
			}
		}
		val it = list_new.iterator()
		while(it.hasNext()) {
			val o = it.next() as? TootConversationSummary ?: continue
			if(removeSet.contains(o.id)) it.remove()
		}
	}
	
	internal fun loadFilter2(client : TootApiClient) : ArrayList<TootFilter>? {
		if(access_info.isPseudo || access_info.isMisskey) return null
		val column_context = getFilterContext()
		if(column_context == 0) return null
		val result = client.request(PATH_FILTERS)
		
		val jsonArray = result?.jsonArray ?: return null
		return TootFilter.parseList(jsonArray)
	}
	
	internal fun encodeFilterTree(filterList : ArrayList<TootFilter>?) : WordTrieTree? {
		val column_context = getFilterContext()
		if(column_context == 0 || filterList == null) return null
		val tree = WordTrieTree()
		val now = System.currentTimeMillis()
		for(filter in filterList) {
			if(filter.time_expires_at > 0L && now >= filter.time_expires_at) continue
			if((filter.context and column_context) != 0) {
				tree.add(
					filter.phrase,
					validator = when(filter.whole_word) {
						true -> WordTrieTree.WORD_VALIDATOR
						else -> WordTrieTree.EMPTY_VALIDATOR
					}
				)
			}
		}
		return tree
	}
	
	internal fun checkFiltersForListData(tree : WordTrieTree?) {
		tree ?: return
		
		val changeList = ArrayList<AdapterChange>()
		list_data.forEachIndexed { idx, item ->
			when(item) {
				is TootStatus -> {
					val old_filtered = item.filtered
					item.updateFiltered(tree)
					if(old_filtered != item.filtered) {
						changeList.add(AdapterChange(AdapterChangeType.RangeChange, idx))
					}
				}
				
				is TootNotification -> {
					val s = item.status
					if(s != null) {
						val old_filtered = s.filtered
						s.updateFiltered(tree)
						if(old_filtered != s.filtered) {
							changeList.add(AdapterChange(AdapterChangeType.RangeChange, idx))
						}
					}
				}
			}
		}
		
		fireShowContent(reason = "filter updated", changeList = changeList)
		
	}
	
	private fun onFiltersChanged2(filterList : ArrayList<TootFilter>) {
		val newFilter = encodeFilterTree(filterList) ?: return
		this.muted_word2 = newFilter
		checkFiltersForListData(newFilter)
	}
	
	fun onFilterDeleted(filter : TootFilter, filterList : ArrayList<TootFilter>) {
		if(column_type == TYPE_KEYWORD_FILTER) {
			val tmp_list = ArrayList<TimelineItem>(list_data.size)
			for(o in list_data) {
				if(o is TootFilter) {
					if(o.id == filter.id) continue
				}
				tmp_list.add(o)
			}
			if(tmp_list.size != list_data.size) {
				list_data.clear()
				list_data.addAll(tmp_list)
				fireShowContent(reason = "onFilterDeleted")
			}
		} else {
			val context = getFilterContext()
			if(context != TootFilter.CONTEXT_NONE) {
				onFiltersChanged2(filterList)
			}
		}
	}
	
	fun onScheduleDeleted(item : TootScheduled) {
		val tmp_list = ArrayList<TimelineItem>(list_data.size)
		for(o in list_data) {
			if(o === item) continue
			tmp_list.add(o)
		}
		if(tmp_list.size != list_data.size) {
			list_data.clear()
			list_data.addAll(tmp_list)
			fireShowContent(reason = "onScheduleDeleted")
		}
	}
	
	val isMisskey : Boolean = access_info.isMisskey
	
	val misskeyVersion = access_info.misskeyVersion
	
	fun saveScrollPosition() {
		try {
			if(viewHolder?.saveScrollPosition() == true) {
				val ss = this.scroll_save
				if(ss != null) {
					val idx = toListIndex(ss.adapterIndex)
					if(0 <= idx && idx < list_data.size) {
						val item = list_data[idx]
						this.last_viewing_item_id = item.getOrderId()
						// とりあえず保存はするが
						// TLデータそのものを永続化しないかぎり出番はないっぽい
					}
				}
			}
		} catch(ex : Throwable) {
			log.e(ex, "can't get last_viewing_item_id.")
		}
	}
	
	fun getContentColor() : Int = when {
		content_color != 0 -> content_color
		else -> defaultColorContentText
	}
	
	fun getAcctColor() : Int = when {
		acct_color != 0 -> acct_color
		else -> defaultColorContentAcct
	}
	
	fun getHeaderPageNumberColor() = when {
		header_fg_color != 0 -> header_fg_color
		else -> defaultColorHeaderPageNumber
	}
	
	fun getHeaderNameColor() = when {
		header_fg_color != 0 -> header_fg_color
		else -> defaultColorHeaderName
	}
	
	fun getHeaderBackgroundColor() = when {
		header_bg_color != 0 -> header_bg_color
		else -> defaultColorHeaderBg
	}
	
	fun setHeaderBackground(view : View) {
		view.backgroundDrawable = getAdaptiveRippleDrawable(
			getHeaderBackgroundColor(),
			getHeaderNameColor()
		)
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
