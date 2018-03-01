package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.content.Context
import android.os.AsyncTask
import android.os.SystemClock
import jp.juggler.subwaytooter.api.*

import org.json.JSONException
import org.json.JSONObject

import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.HashSet
import java.util.LinkedList
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.AcctSet
import jp.juggler.subwaytooter.table.HighlightWord
import jp.juggler.subwaytooter.table.MutedApp
import jp.juggler.subwaytooter.table.MutedWord
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.TagSet
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.util.*

class Column(
	val app_state : AppState,
	val context : Context,
	val access_info : SavedAccount,
	val column_type : Int
) {
	
	companion object {
		private val log = LogCategory("Column")
		
		private const val READ_LIMIT = 80 // API側の上限が80です。ただし指定しても40しか返ってこないことが多い
		private const val LOOP_TIMEOUT = 10000L
		private const val LOOP_READ_ENOUGH = 30 // フィルタ後のデータ数がコレ以上ならループを諦めます
		private const val RELATIONSHIP_LOAD_STEP = 40
		private const val ACCT_DB_STEP = 100
		
		// ステータスのリストを返すAPI
		private const val PATH_HOME = "/api/v1/timelines/home?limit=" + READ_LIMIT
		private const val PATH_LOCAL = "/api/v1/timelines/public?limit=$READ_LIMIT&local=1"
		private const val PATH_FEDERATE = "/api/v1/timelines/public?limit=" + READ_LIMIT
		private const val PATH_FAVOURITES = "/api/v1/favourites?limit=" + READ_LIMIT
		private const val PATH_ACCOUNT_STATUSES =
			"/api/v1/accounts/%d/statuses?limit=" + READ_LIMIT // 1:account_id
		private const val PATH_HASHTAG =
			"/api/v1/timelines/tag/%s?limit=" + READ_LIMIT // 1: hashtag(url encoded)
		private const val PATH_LIST_TL = "/api/v1/timelines/list/%s?limit=" + READ_LIMIT
		
		// アカウントのリストを返すAPI
		private const val PATH_ACCOUNT_FOLLOWING =
			"/api/v1/accounts/%d/following?limit=" + READ_LIMIT // 1:account_id
		private const val PATH_ACCOUNT_FOLLOWERS =
			"/api/v1/accounts/%d/followers?limit=" + READ_LIMIT // 1:account_id
		private const val PATH_MUTES = "/api/v1/mutes?limit=" + READ_LIMIT // 1:account_id
		private const val PATH_BLOCKS = "/api/v1/blocks?limit=" + READ_LIMIT // 1:account_id
		private const val PATH_FOLLOW_REQUESTS =
			"/api/v1/follow_requests?limit=" + READ_LIMIT // 1:account_id
		private const val PATH_BOOSTED_BY =
			"/api/v1/statuses/%s/reblogged_by?limit=" + READ_LIMIT // 1:status_id
		private const val PATH_FAVOURITED_BY =
			"/api/v1/statuses/%s/favourited_by?limit=" + READ_LIMIT // 1:status_id
		private const val PATH_LIST_MEMBER = "/api/v1/lists/%s/accounts?limit=" + READ_LIMIT
		
		// 他のリストを返すAPI
		private const val PATH_REPORTS = "/api/v1/reports?limit=" + READ_LIMIT
		private const val PATH_NOTIFICATIONS = "/api/v1/notifications?limit=" + READ_LIMIT
		private const val PATH_DOMAIN_BLOCK = "/api/v1/domain_blocks?limit=" + READ_LIMIT
		private const val PATH_LIST_LIST = "/api/v1/lists?limit=" + READ_LIMIT
		
		// リストではなくオブジェクトを返すAPI
		private const val PATH_ACCOUNT = "/api/v1/accounts/%d" // 1:account_id
		private const val PATH_STATUSES = "/api/v1/statuses/%d" // 1:status_id
		private const val PATH_STATUSES_CONTEXT = "/api/v1/statuses/%d/context" // 1:status_id
		const val PATH_SEARCH = "/api/v1/search?q=%s"
		// search args 1: query(urlencoded) , also, append "&resolve=1" if resolve non-local accounts
		private const val PATH_INSTANCE = "/api/v1/instance"
		private const val PATH_LIST_INFO = "/api/v1/lists/%s"
		
		internal const val KEY_ACCOUNT_ROW_ID = "account_id"
		internal const val KEY_TYPE = "type"
		internal const val KEY_DONT_CLOSE = "dont_close"
		private const val KEY_WITH_ATTACHMENT = "with_attachment"
		private const val KEY_WITH_HIGHLIGHT = "with_highlight"
		private const val KEY_DONT_SHOW_BOOST = "dont_show_boost"
		private const val KEY_DONT_SHOW_FAVOURITE = "dont_show_favourite"
		private const val KEY_DONT_SHOW_FOLLOW = "dont_show_follow"
		private const val KEY_DONT_SHOW_REPLY = "dont_show_reply"
		private const val KEY_DONT_SHOW_NORMAL_TOOT = "dont_show_normal_toot"
		private const val KEY_DONT_STREAMING = "dont_streaming"
		private const val KEY_DONT_AUTO_REFRESH = "dont_auto_refresh"
		private const val KEY_HIDE_MEDIA_DEFAULT = "hide_media_default"
		private const val KEY_SYSTEM_NOTIFICATION_NOT_RELATED = "system_notification_not_related"
		
		private const val KEY_ENABLE_SPEECH = "enable_speech"
		
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
		internal const val TAB_STATUS = 0
		internal const val TAB_FOLLOWING = 1
		internal const val TAB_FOLLOWERS = 2
		
		@Suppress("UNCHECKED_CAST")
		private inline fun <reified T> getParamAt(params : Array<out Any>, idx : Int) : T {
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
		
		fun getColumnTypeName(context : Context, type : Int) : String {
			return when(type) {
				TYPE_HOME -> context.getString(R.string.home)
				TYPE_LOCAL -> context.getString(R.string.local_timeline)
				TYPE_FEDERATE -> context.getString(R.string.federate_timeline)
				TYPE_PROFILE -> context.getString(R.string.profile)
				TYPE_FAVOURITES -> context.getString(R.string.favourites)
				TYPE_REPORTS -> context.getString(R.string.reports)
				TYPE_NOTIFICATIONS -> context.getString(R.string.notifications)
				TYPE_CONVERSATION -> context.getString(R.string.conversation)
				TYPE_BOOSTED_BY -> context.getString(R.string.boosted_by)
				TYPE_FAVOURITED_BY -> context.getString(R.string.favourited_by)
				TYPE_HASHTAG -> context.getString(R.string.hashtag)
				TYPE_MUTES -> context.getString(R.string.muted_users)
				TYPE_BLOCKS -> context.getString(R.string.blocked_users)
				TYPE_DOMAIN_BLOCKS -> context.getString(R.string.blocked_domains)
				TYPE_SEARCH -> context.getString(R.string.search)
				TYPE_SEARCH_MSP -> context.getString(R.string.toot_search_msp)
				TYPE_SEARCH_TS -> context.getString(R.string.toot_search_ts)
				TYPE_INSTANCE_INFORMATION -> context.getString(R.string.instance_information)
				TYPE_FOLLOW_REQUESTS -> context.getString(R.string.follow_requests)
				TYPE_LIST_LIST -> context.getString(R.string.lists)
				TYPE_LIST_MEMBER -> context.getString(R.string.list_member)
				TYPE_LIST_TL -> context.getString(R.string.list_timeline)
				else -> "?"
			}
		}
		
		internal fun getIconAttrId(acct : String, type : Int) : Int {
			return when(type) {
				TYPE_REPORTS -> R.attr.ic_info
				TYPE_HOME -> R.attr.btn_home
				TYPE_LOCAL -> R.attr.btn_local_tl
				TYPE_FEDERATE -> R.attr.btn_federate_tl
				TYPE_PROFILE -> R.attr.btn_statuses
				TYPE_FAVOURITES -> if(SavedAccount.isNicoru(acct)) R.attr.ic_nicoru else R.attr.btn_favourite
				TYPE_NOTIFICATIONS -> R.attr.btn_notification
				TYPE_CONVERSATION -> R.attr.ic_conversation
				TYPE_BOOSTED_BY -> R.attr.btn_boost
				TYPE_FAVOURITED_BY -> if(SavedAccount.isNicoru(acct)) R.attr.ic_nicoru else R.attr.btn_favourite
				TYPE_HASHTAG -> R.attr.ic_hashtag
				TYPE_MUTES -> R.attr.ic_mute
				TYPE_BLOCKS -> R.attr.ic_block
				TYPE_DOMAIN_BLOCKS -> R.attr.ic_domain_block
				TYPE_SEARCH, TYPE_SEARCH_MSP, TYPE_SEARCH_TS -> R.attr.ic_search
				TYPE_INSTANCE_INFORMATION -> R.attr.ic_info
				TYPE_FOLLOW_REQUESTS -> R.attr.ic_account_add
				TYPE_LIST_LIST -> R.attr.ic_list_list
				TYPE_LIST_MEMBER -> R.attr.ic_list_member
				TYPE_LIST_TL -> R.attr.ic_list_tl
				else -> R.attr.ic_info
			}
		}
		
		internal val version_1_6 = VersionString("1.6")
		
		@Suppress("HasPlatformType")
		val reMaxId = Pattern.compile("[&?]max_id=(\\d+)") // より古いデータの取得に使う
		
		@Suppress("HasPlatformType")
		private val reSinceId = Pattern.compile("[&?]since_id=(\\d+)") // より新しいデータの取得に使う
		
		val COLUMN_REGEX_FILTER_DEFAULT = { _ : CharSequence? -> false }
		
	}
	
	private var callback_ref : WeakReference<Callback>? = null
	
	private val isActivityStart : Boolean
		get() {
			return callback_ref?.get()?.isActivityStart ?: false
		}
	
	private val streamPath : String?
		get() {
			return when(column_type) {
				TYPE_HOME, TYPE_NOTIFICATIONS -> "/api/v1/streaming/?stream=user"
				TYPE_LOCAL -> "/api/v1/streaming/?stream=public:local"
				TYPE_FEDERATE -> "/api/v1/streaming/?stream=public"
				TYPE_HASHTAG -> "/api/v1/streaming/?stream=hashtag&tag=" + hashtag.encodePercent() // タグ先頭の#を含まない
				TYPE_LIST_TL -> "/api/v1/streaming/?stream=list&list=" + profile_id.toString()
				else -> null
			}
		}
	
	private val isPublicStream : Boolean
		get() {
			return when(column_type) {
				TYPE_LOCAL, TYPE_FEDERATE, TYPE_HASHTAG -> true
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
	internal var dont_streaming : Boolean = false
	internal var dont_auto_refresh : Boolean = false
	internal var hide_media_default : Boolean = false
	internal var system_notification_not_related : Boolean = false
	var enable_speech : Boolean = false
	
	internal var regex_text : String = ""
	
	internal var header_bg_color : Int = 0
	internal var header_fg_color : Int = 0
	internal var column_bg_color : Int = 0
	internal var acct_color : Int = 0
	internal var content_color : Int = 0
	internal var column_bg_image : String = ""
	internal var column_bg_image_alpha = 1f
	
	internal var profile_tab = TAB_STATUS
	
	private var status_id : Long = 0
	
	// プロフカラムではアカウントのID。リストカラムではリストのID
	private var profile_id : Long = 0
	
	internal var search_query : String = ""
	internal var search_resolve : Boolean = false
	private var hashtag : String = ""
	internal var instance_uri : String = ""
	
	// プロフカラムでのアカウント情報
	@Volatile
	internal var who_account : TootAccount? = null
	
	// リストカラムでのリスト情報
	@Volatile
	private var list_info : TootList? = null
	
	// 「インスタンス情報」カラムに表示するインスタンス情報
	// (SavedAccount中のインスタンス情報とは異なるので注意)
	internal var instance_information : TootInstance? = null
	
	internal var scroll_save : ScrollPosition? = null
	
	internal val is_dispose = AtomicBoolean()
	
	internal var bFirstInitialized = false
	
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
	
	private var last_task : AsyncTask<Void, Void, TootApiResult?>? = null
	
	internal var bInitialLoading : Boolean = false
	internal var bRefreshLoading : Boolean = false
	
	internal var mInitialLoadingError : String = ""
	internal var mRefreshLoadingError : String = ""
	
	internal var task_progress : String? = null
	
	internal val list_data = BucketList<TimelineItem>()
	private val duplicate_map = DuplicateMap()
	
	private val isFilterEnabled : Boolean
		get() = (with_attachment
			|| with_highlight
			|| dont_show_boost
			|| dont_show_favourite
			|| dont_show_follow
			|| dont_show_reply
			|| dont_show_normal_toot
			|| regex_text.isNotEmpty()
			)
	
	private var column_regex_filter = COLUMN_REGEX_FILTER_DEFAULT
	private var muted_app : HashSet<String>? = null
	private var muted_word : WordTrieTree? = null
	private var highlight_trie : WordTrieTree? = null
	
	private var max_id : String = ""
	private var since_id : String = ""
	
	private var bRefreshingTop : Boolean = false
	
	// ListViewの表示更新が追いつかないとスクロール位置が崩れるので
	// 一定時間より短期間にはデータ更新しないようにする
	private var last_show_stream_data : Long = 0
	private val stream_data_queue = LinkedList<TimelineItem>()
	
	private var bPutGap : Boolean = false
	
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
	val listId : Long
		get() {
			return when(column_type) {
				TYPE_LIST_MEMBER, TYPE_LIST_TL -> profile_id
				else -> - 1L
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
		: this(app_state, app_state.context, access_info, type) {
		this.callback_ref = WeakReference(callback)
		when(type) {
			TYPE_CONVERSATION, TYPE_BOOSTED_BY, TYPE_FAVOURITED_BY -> status_id =
				getParamAt(params, 0)
			TYPE_PROFILE, TYPE_LIST_TL, TYPE_LIST_MEMBER -> profile_id = getParamAt(params, 0)
			TYPE_HASHTAG -> hashtag = getParamAt(params, 0)
			
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
		src.optInt(KEY_TYPE)
	) {
		dont_close = src.optBoolean(KEY_DONT_CLOSE)
		with_attachment = src.optBoolean(KEY_WITH_ATTACHMENT)
		with_highlight = src.optBoolean(KEY_WITH_HIGHLIGHT)
		dont_show_boost = src.optBoolean(KEY_DONT_SHOW_BOOST)
		dont_show_follow = src.optBoolean(KEY_DONT_SHOW_FOLLOW)
		dont_show_favourite = src.optBoolean(KEY_DONT_SHOW_FAVOURITE)
		dont_show_reply = src.optBoolean(KEY_DONT_SHOW_REPLY)
		dont_show_normal_toot = src.optBoolean(KEY_DONT_SHOW_NORMAL_TOOT)
		dont_streaming = src.optBoolean(KEY_DONT_STREAMING)
		dont_auto_refresh = src.optBoolean(KEY_DONT_AUTO_REFRESH)
		hide_media_default = src.optBoolean(KEY_HIDE_MEDIA_DEFAULT)
		system_notification_not_related = src.optBoolean(KEY_SYSTEM_NOTIFICATION_NOT_RELATED)
		
		enable_speech = src.optBoolean(KEY_ENABLE_SPEECH)
		
		regex_text = src.parseString(KEY_REGEX_TEXT) ?: ""
		
		header_bg_color = src.optInt(KEY_HEADER_BACKGROUND_COLOR)
		header_fg_color = src.optInt(KEY_HEADER_TEXT_COLOR)
		column_bg_color = src.optInt(KEY_COLUMN_BACKGROUND_COLOR)
		acct_color = src.optInt(KEY_COLUMN_ACCT_TEXT_COLOR)
		content_color = src.optInt(KEY_COLUMN_CONTENT_TEXT_COLOR)
		column_bg_image = src.parseString(KEY_COLUMN_BACKGROUND_IMAGE) ?: ""
		column_bg_image_alpha = src.optDouble(KEY_COLUMN_BACKGROUND_IMAGE_ALPHA, 1.0).toFloat()
		
		when(column_type) {
			
			TYPE_CONVERSATION, TYPE_BOOSTED_BY, TYPE_FAVOURITED_BY -> status_id =
				src.parseLong(KEY_STATUS_ID) ?: - 1L
			
			TYPE_PROFILE -> {
				profile_id = src.parseLong(KEY_PROFILE_ID) ?: - 1L
				profile_tab = src.optInt(KEY_PROFILE_TAB)
			}
			
			TYPE_LIST_MEMBER, TYPE_LIST_TL -> profile_id = src.parseLong(KEY_PROFILE_ID) ?: - 1L
			
			TYPE_HASHTAG -> hashtag = src.optString(KEY_HASHTAG)
			
			TYPE_SEARCH -> {
				search_query = src.optString(KEY_SEARCH_QUERY)
				search_resolve = src.optBoolean(KEY_SEARCH_RESOLVE, false)
			}
			
			TYPE_SEARCH_MSP, TYPE_SEARCH_TS -> search_query = src.optString(KEY_SEARCH_QUERY)
			
			TYPE_INSTANCE_INFORMATION -> instance_uri = src.optString(KEY_INSTANCE_URI)
		}
	}
	
	@Throws(JSONException::class)
	fun encodeJSON(dst : JSONObject, old_index : Int) {
		dst.put(KEY_ACCOUNT_ROW_ID, access_info.db_id)
		dst.put(KEY_TYPE, column_type)
		dst.put(KEY_DONT_CLOSE, dont_close)
		dst.put(KEY_WITH_ATTACHMENT, with_attachment)
		dst.put(KEY_WITH_HIGHLIGHT, with_highlight)
		dst.put(KEY_DONT_SHOW_BOOST, dont_show_boost)
		dst.put(KEY_DONT_SHOW_FOLLOW, dont_show_follow)
		dst.put(KEY_DONT_SHOW_FAVOURITE, dont_show_favourite)
		dst.put(KEY_DONT_SHOW_REPLY, dont_show_reply)
		dst.put(KEY_DONT_SHOW_NORMAL_TOOT, dont_show_normal_toot)
		dst.put(KEY_DONT_STREAMING, dont_streaming)
		dst.put(KEY_DONT_AUTO_REFRESH, dont_auto_refresh)
		dst.put(KEY_HIDE_MEDIA_DEFAULT, hide_media_default)
		dst.put(KEY_SYSTEM_NOTIFICATION_NOT_RELATED, system_notification_not_related)
		
		dst.put(KEY_ENABLE_SPEECH, enable_speech)
		
		dst.put(KEY_REGEX_TEXT, regex_text)
		
		dst.put(KEY_HEADER_BACKGROUND_COLOR, header_bg_color)
		dst.put(KEY_HEADER_TEXT_COLOR, header_fg_color)
		dst.put(KEY_COLUMN_BACKGROUND_COLOR, column_bg_color)
		dst.put(KEY_COLUMN_ACCT_TEXT_COLOR, acct_color)
		dst.put(KEY_COLUMN_CONTENT_TEXT_COLOR, content_color)
		dst.put(KEY_COLUMN_BACKGROUND_IMAGE, column_bg_image)
		dst.put(KEY_COLUMN_BACKGROUND_IMAGE_ALPHA, column_bg_image_alpha.toDouble())
		
		when(column_type) {
			TYPE_CONVERSATION, TYPE_BOOSTED_BY, TYPE_FAVOURITED_BY -> dst.put(
				KEY_STATUS_ID,
				status_id
			)
			TYPE_PROFILE -> dst.put(KEY_PROFILE_ID, profile_id).put(KEY_PROFILE_TAB, profile_tab)
			TYPE_LIST_MEMBER, TYPE_LIST_TL -> dst.put(KEY_PROFILE_ID, profile_id)
			TYPE_HASHTAG -> dst.put(KEY_HASHTAG, hashtag)
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
		dst.put(KEY_COLUMN_ACCESS_COLOR, if(AcctColor.hasColorForeground(ac)) ac.color_fg else 0)
		dst.put(KEY_COLUMN_ACCESS_COLOR_BG, if(AcctColor.hasColorBackground(ac)) ac.color_bg else 0)
		dst.put(KEY_COLUMN_NAME, getColumnName(true))
		dst.put(KEY_OLD_INDEX, old_index)
	}
	
	internal fun isSameSpec(ai : SavedAccount, type : Int, params : Array<out Any>) : Boolean {
		if(type != column_type || ai.acct != access_info.acct) return false
		
		return try {
			when(type) {
				
				TYPE_PROFILE, TYPE_LIST_TL, TYPE_LIST_MEMBER -> getParamAt<Long>(
					params,
					0
				) == profile_id
				
				TYPE_CONVERSATION, TYPE_BOOSTED_BY, TYPE_FAVOURITED_BY -> getParamAt<Long>(
					params,
					0
				) == status_id
				
				TYPE_HASHTAG -> getParamAt<String>(params, 0) == hashtag
				
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
	
	internal fun getColumnName(bLong : Boolean) : String {
		return when(column_type) {
			
			TYPE_PROFILE -> context.getString(
				R.string.profile_of,
				if(who_account != null)
					AcctColor.getNickname(access_info.getFullAcct(who_account))
				else
					profile_id.toString()
			)
			
			TYPE_LIST_MEMBER -> context.getString(
				R.string.list_member_of,
				list_info?.title ?: profile_id.toString()
			)
			
			TYPE_LIST_TL -> context.getString(
				R.string.list_tl_of,
				list_info?.title ?: profile_id.toString()
			)
			
			TYPE_CONVERSATION -> context.getString(R.string.conversation_around, status_id)
			
			TYPE_HASHTAG -> context.getString(R.string.hashtag_of, hashtag)
			
			TYPE_SEARCH ->
				if(bLong) context.getString(R.string.search_of, search_query)
				else getColumnTypeName(context, column_type)
			
			TYPE_SEARCH_MSP ->
				if(bLong) context.getString(R.string.toot_search_msp_of, search_query)
				else getColumnTypeName(context, column_type)
			
			TYPE_SEARCH_TS ->
				if(bLong) context.getString(R.string.toot_search_ts_of, search_query)
				else getColumnTypeName(context, column_type)
			
			TYPE_INSTANCE_INFORMATION ->
				if(bLong) context.getString(R.string.instance_information_of, instance_uri)
				else getColumnTypeName(context, column_type)
			
			TYPE_NOTIFICATIONS ->
				context.getString(R.string.notifications) + getNotificationTypeString()
			
			else -> getColumnTypeName(context, column_type)
		}
	}
	
	private fun getNotificationTypeString() : String {
		return if(! dont_show_reply && ! dont_show_follow && ! dont_show_boost && ! dont_show_favourite) {
			""
		} else if(dont_show_reply && dont_show_follow && dont_show_boost && dont_show_favourite) {
			""
		} else {
			val sb = StringBuilder()
			if(! dont_show_reply) {
				if(sb.isNotEmpty()) sb.append(", ")
				sb.append(context.getString(R.string.notification_type_mention))
			}
			if(! dont_show_follow) {
				if(sb.isNotEmpty()) sb.append(", ")
				sb.append(context.getString(R.string.notification_type_follow))
			}
			if(! dont_show_boost) {
				if(sb.isNotEmpty()) sb.append(", ")
				sb.append(context.getString(R.string.notification_type_boost))
			}
			if(! dont_show_favourite) {
				if(sb.isNotEmpty()) sb.append(", ")
				sb.append(context.getString(R.string.notification_type_favourite))
			}
			sb.insert(0, "(")
			sb.append(")")
			sb.toString()
		}
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
	
	internal fun getIconAttrId(type : Int) : Int {
		return getIconAttrId(access_info.acct, type)
	}
	
	// ブーストやお気に入りの更新に使う。ステータスを列挙する。
	fun findStatus(
		target_instance : String,
		target_status_id : Long,
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
	fun removeAccountInTimeline(target_account : SavedAccount, who_id : Long) {
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
			} else if(o is TootAccount) {
				if(who_id == o.id) continue
			}
			
			tmp_list.add(o)
		}
		if(tmp_list.size != list_data.size) {
			list_data.clear()
			list_data.addAll(tmp_list)
			fireShowContent(reason = "removeAccountInTimeline")
			
		}
	}
	
	// ミュート解除が成功した時に呼ばれる
	fun removeFromMuteList(target_account : SavedAccount, who_id : Long) {
		if(column_type == TYPE_MUTES && target_account.acct == access_info.acct) {
			val tmp_list = ArrayList<TimelineItem>(list_data.size)
			for(o in list_data) {
				if(o is TootAccount) {
					if(o.id == who_id) continue
				}
				tmp_list.add(o)
			}
			if(tmp_list.size != list_data.size) {
				list_data.clear()
				list_data.addAll(tmp_list)
				fireShowContent(reason = "removeFromMuteList")
			}
		}
	}
	
	// ブロック解除が成功したので、ブロックリストから削除する
	fun removeFromBlockList(target_account : SavedAccount, who_id : Long) {
		if(column_type == TYPE_BLOCKS && target_account.acct == access_info.acct) {
			val tmp_list = ArrayList<TimelineItem>(list_data.size)
			for(o in list_data) {
				if(o is TootAccount) {
					if(o.id == who_id) continue
				}
				tmp_list.add(o)
			}
			if(tmp_list.size != list_data.size) {
				list_data.clear()
				list_data.addAll(tmp_list)
				fireShowContent(reason = "removeFromBlockList")
			}
			
		}
	}
	
	fun removeFollowRequest(target_account : SavedAccount, who_id : Long) {
		if(target_account.acct != access_info.acct) return
		
		if(column_type == TYPE_FOLLOW_REQUESTS) {
			val tmp_list = ArrayList<TimelineItem>(list_data.size)
			for(o in list_data) {
				if(o is TootAccount) {
					if(o.id == who_id) continue
				}
				tmp_list.add(o)
			}
			if(tmp_list.size != list_data.size) {
				list_data.clear()
				list_data.addAll(tmp_list)
				fireShowContent(reason = "removeFollowRequest 1")
			}
		} else {
			// 他のカラムでもフォロー状態の表示更新が必要
			fireRebindAdapterItems()
		}
	}
	
	// 自分のステータスを削除した時に呼ばれる
	fun removeStatus(target_account : SavedAccount, status_id : Long) {
		
		if(target_account.host != access_info.host) return
		
		val tmp_list = ArrayList<TimelineItem>(list_data.size)
		for(o in list_data) {
			if(o is TootStatus) {
				if(status_id == o.id) continue
				if(status_id == (o.reblog?.id ?: - 1L)) continue
			}
			if(o is TootNotification) {
				if(status_id == (o.status?.id ?: - 1L)) continue
				if(status_id == (o.status?.reblog?.id ?: - 1L)) continue
			}
			
			tmp_list.add(o)
		}
		if(tmp_list.size != list_data.size) {
			list_data.clear()
			list_data.addAll(tmp_list)
			fireShowContent(reason = "removeStatus")
		}
	}
	
	fun removeNotifications() {
		cancelLastTask()
		
		mRefreshLoadingError = ""
		bRefreshLoading = false
		mInitialLoadingError = ""
		bInitialLoading = false
		max_id = ""
		since_id = ""
		
		list_data.clear()
		duplicate_map.clear()
		fireShowContent(reason = "removeNotifications", reset = true)
		
		PollingWorker.queueNotificationCleared(context, access_info.db_id)
	}
	
	fun removeNotificationOne(target_account : SavedAccount, notification : TootNotification) {
		if(column_type != TYPE_NOTIFICATIONS) return
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
	
	fun onMuteAppUpdated() {
		val tmp_list = ArrayList<TimelineItem>(list_data.size)
		
		val muted_app = MutedApp.nameSet
		val muted_word = MutedWord.nameSet
		
		val checker = { status : TootStatus? -> status?.checkMuted(muted_app, muted_word) ?: false }
		
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
			fireShowContent(reason = "onMuteAppUpdated")
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
				this.list_info = item
				fireShowColumnHeader()
			}
		}
	}
	
	fun onListMemberUpdated(
		account : SavedAccount,
		list_id : Long,
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
		if(last_task != null) {
			last_task?.cancel(true)
			last_task = null
			//
			bInitialLoading = false
			bRefreshLoading = false
			mInitialLoadingError = context.getString(R.string.cancelled)
			//
		}
	}
	
	private fun initFilter() {
		column_regex_filter = COLUMN_REGEX_FILTER_DEFAULT
		val regex_text = this.regex_text
		if(regex_text.isNotEmpty()) {
			try {
				val re = Pattern.compile(regex_text)
				column_regex_filter =
					{ text : CharSequence? -> if(text == null) false else re.matcher(text).find() }
			} catch(ex : Throwable) {
				log.trace(ex)
			}
		}
		
		muted_app = MutedApp.nameSet
		muted_word = MutedWord.nameSet
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
	
	private fun isFiltered(status : TootStatus) : Boolean {
		
		if(isFilteredByAttachment(status)) return true
		
		if(dont_show_boost) {
			if(status.reblog != null) return true
		}
		
		if(dont_show_reply) {
			if(status.in_reply_to_id?.isNotEmpty() == true) return true
			if(status.reblog?.in_reply_to_id?.isNotEmpty() == true) return true
		}
		
		if(dont_show_normal_toot) {
			if( status.in_reply_to_id?.isEmpty() != false
				&&  status.reblog == null
			) return true
		}
		
		if(column_regex_filter(status.decoded_content)) return true
		if(column_regex_filter(status.reblog?.decoded_content)) return true
		
		return status.checkMuted(muted_app, muted_word)
		
	}
	
	private inline fun <reified T : TimelineItem> addAll(
		dstArg : ArrayList<TimelineItem>?,
		src : ArrayList<T>
	) : ArrayList<TimelineItem> {
		val dst = dstArg ?: ArrayList()
		for(item in src) {
			dst.add(item as TimelineItem)
		}
		return dst
	}
	
	private fun addOne(
		dstArg : ArrayList<TimelineItem>?,
		item : TimelineItem
	) : ArrayList<TimelineItem> {
		val dst = dstArg ?: ArrayList()
		dst.add(item)
		return dst
	}
	
	private fun addWithFilterStatus(
		dstArg : ArrayList<TimelineItem>?,
		src : ArrayList<TootStatus>
	) : ArrayList<TimelineItem> {
		val dst = dstArg ?: ArrayList()
		for(status in src) {
			if(! isFiltered(status)) {
				dst.add(status)
			}
		}
		return dst
	}
	
	private fun addWithFilterNotification(
		dstArg : ArrayList<TimelineItem>?,
		src : ArrayList<TootNotification>
	) : ArrayList<TimelineItem> {
		val dst = dstArg ?: ArrayList()
		for(item in src) {
			if(! isFiltered(item)) dst.add(item)
		}
		return dst
	}
	
	private fun isFiltered(item : TootNotification) : Boolean {
		
		if(dont_show_favourite && TootNotification.TYPE_FAVOURITE == item.type) {
			log.d("isFiltered: favourite notification filtered.")
			return true
		}
		
		if(dont_show_boost && TootNotification.TYPE_REBLOG == item.type) {
			log.d("isFiltered: reblog notification filtered.")
			return true
		}
		
		if(dont_show_follow && TootNotification.TYPE_FOLLOW == item.type) {
			log.d("isFiltered: follow notification filtered.")
			return true
		}
		
		if(dont_show_reply && TootNotification.TYPE_MENTION == item.type) {
			log.d("isFiltered: mention notification filtered.")
			return true
		}
		
		val status = item.status
		if(status != null) {
			if(status.checkMuted(muted_app, muted_word)) {
				log.d("isFiltered: status muted.")
				return true
			}
		}
		return false
	}
	
	//	@Nullable String parseMaxId( TootApiResult result ){
	//		if( result != null && result.link_older != null ){
	//			Matcher m = reMaxId.matcher( result.link_older );
	//			if( m.find() ) return m.group( 1 );
	//		}
	//		return null;
	//	}
	
	internal fun loadProfileAccount(client : TootApiClient, bForceReload : Boolean) {
		if(bForceReload || this.who_account == null) {
			val result = client.request(String.format(Locale.JAPAN, PATH_ACCOUNT, profile_id))
			val a = TootParser(context, access_info).account(result?.jsonObject)
			if(a != null) {
				this.who_account = a
				client.publishApiProgress("") // カラムヘッダの再表示
			}
		}
	}
	
	internal fun loadListInfo(client : TootApiClient, bForceReload : Boolean) {
		if(bForceReload || this.list_info == null) {
			val result = client.request(String.format(Locale.JAPAN, PATH_LIST_INFO, profile_id))
			val jsonObject = result?.jsonObject
			if(jsonObject != null) {
				val data = parseItem(::TootList, jsonObject)
				if(data != null) {
					this.list_info = data
					client.publishApiProgress("") // カラムヘッダの再表示
				}
			}
		}
	}
	
	private inner class UpdateRelationEnv {
		internal val who_set = HashSet<Long>()
		internal val acct_set = HashSet<String>()
		internal val tag_set = HashSet<String>()
		
		internal fun add(a : TootAccount?) {
			if(a == null) return
			who_set.add(a.id)
			acct_set.add("@" + access_info.getFullAcct(a))
			//
			add(a.moved)
		}
		
		internal fun add(s : TootStatus?) {
			if(s == null) return
			add(s.account)
			add(s.reblog)
			s.tags?.forEach { tag_set.add(it.name) }
		}
		
		internal fun add(n : TootNotification?) {
			if(n == null) return
			add(n.account)
			add(n.status)
		}
		
		internal fun update(client : TootApiClient) {
			
			var n : Int
			var size : Int
			
			// アカウントIDの集合からRelationshipを取得してデータベースに記録する
			size = who_set.size
			if(size > 0) {
				val who_list = ArrayList<Long>(size)
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
					val list = parseList(::TootRelationShip, result.jsonArray)
					if(list.size > 0) UserRelation.saveList(now, access_info.db_id, list)
				}
				log.d("updateRelation: update %d relations.", n)
				
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
	private fun updateRelation(
		client : TootApiClient,
		list : ArrayList<TimelineItem>?,
		who : TootAccount?
	) {
		if(access_info.isPseudo) return
		
		val env = UpdateRelationEnv()
		
		env.add(who)
		
		list?.forEach {
			when(it) {
				is TootAccount -> env.add(it)
				is TootStatus -> env.add(it)
				is TootNotification -> env.add(it)
			}
		}
		env.update(client)
	}
	
	internal fun startLoading() {
		cancelLastTask()
		
		stopStreaming()
		
		initFilter()
		
		mRefreshLoadingError = ""
		mInitialLoadingError = ""
		bFirstInitialized = true
		bInitialLoading = true
		bRefreshLoading = false
		max_id = ""
		since_id = ""
		
		duplicate_map.clear()
		list_data.clear()
		fireShowContent(reason = "loading start", reset = true)
		
		val task = @SuppressLint("StaticFieldLeak")
		object : AsyncTask<Void, Void, TootApiResult?>() {
			internal var parser = TootParser(context, access_info, highlightTrie = highlight_trie)
			
			internal var instance_tmp : TootInstance? = null
			
			internal var list_pinned : ArrayList<TimelineItem>? = null
			
			internal var list_tmp : ArrayList<TimelineItem>? = null
			
			internal fun getInstanceInformation(
				client : TootApiClient,
				instance_name : String?
			) : TootApiResult? {
				if(instance_name != null) {
					// 「インスタンス情報」カラムをNAアカウントで開く場合
					client.instance = instance_name
				} else {
					// カラムに紐付けられたアカウントのタンスのインスタンス情報
				}
				val result = client.request("/api/v1/instance")
				val jsonObject = result?.jsonObject
				if(jsonObject != null) {
					instance_tmp = parseItem(::TootInstance, jsonObject)
				}
				return result
			}
			
			internal fun getStatusesPinned(client : TootApiClient, path_base : String) {
				val result = client.request(path_base)
				val jsonArray = result?.jsonArray
				if(jsonArray != null) {
					//
					val src = TootParser(
						context,
						access_info,
						pinned = true,
						highlightTrie = highlight_trie
					).statusList(jsonArray)
					
					this.list_pinned = addWithFilterStatus(null, src)
					
					// pinned tootにはページングの概念はない
				}
				log.d("getStatusesPinned: list size=%s", list_pinned?.size ?: - 1)
			}
			
			internal fun getStatuses(client : TootApiClient, path_base : String) : TootApiResult? {
				
				val time_start = SystemClock.elapsedRealtime()
				val result = client.request(path_base)
				var jsonArray = result?.jsonArray
				if(jsonArray != null) {
					saveRange(result, true, true)
					//
					var src = parser.statusList(jsonArray)
					
					this.list_tmp = addWithFilterStatus(ArrayList(src.size), src)
					//
					val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
					while(true) {
						if(client.isApiCancelled) {
							log.d("loading-statuses: cancelled.")
							break
						}
						if(! isFilterEnabled) {
							log.d("loading-statuses: isFiltered is false.")
							break
						}
						if(max_id.isEmpty()) {
							log.d("loading-statuses: max_id is empty.")
							break
						}
						if((list_tmp?.size ?: 0) >= LOOP_READ_ENOUGH) {
							log.d("loading-statuses: read enough data.")
							break
						}
						if(src.isEmpty()) {
							log.d("loading-statuses: previous response is empty.")
							break
						}
						if(SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
							log.d("loading-statuses: timeout.")
							break
						}
						val path = path_base + delimiter + "max_id=" + max_id
						val result2 = client.request(path)
						jsonArray = result2?.jsonArray
						if(jsonArray == null) {
							log.d("loading-statuses: error or cancelled.")
							break
						}
						
						src = parser.statusList(jsonArray)
						
						addWithFilterStatus(list_tmp, src)
						
						if(! saveRangeEnd(result2)) {
							log.d("loading-statuses: missing range info.")
							break
						}
					}
				}
				return result
			}
			
			internal fun parseAccountList(
				client : TootApiClient,
				path_base : String
			) : TootApiResult? {
				val result = client.request(path_base)
				if(result != null) {
					saveRange(result, true, true)
					this.list_tmp = addAll(null, parser.accountList(result.jsonArray))
				}
				return result
			}
			
			internal fun parseDomainList(
				client : TootApiClient,
				path_base : String
			) : TootApiResult? {
				val result = client.request(path_base)
				if(result != null) {
					saveRange(result, true, true)
					this.list_tmp = addAll(null, TootDomainBlock.parseList(result.jsonArray))
				}
				return result
			}
			
			internal fun parseReports(client : TootApiClient, path_base : String) : TootApiResult? {
				val result = client.request(path_base)
				if(result != null) {
					saveRange(result, true, true)
					list_tmp = addAll(null, parseList(::TootReport, result.jsonArray))
				}
				return result
			}
			
			internal fun parseListList(
				client : TootApiClient,
				path_base : String
			) : TootApiResult? {
				val result = client.request(path_base)
				if(result != null) {
					saveRange(result, true, true)
					val src = parseList(::TootList, result.jsonArray)
					src.sort()
					this.list_tmp = addAll(null, src)
				}
				return result
			}
			
			internal fun parseNotifications( client : TootApiClient ) : TootApiResult? {
				val path_base  = makeNotificationUrl()
				
				val time_start = SystemClock.elapsedRealtime()
				val result = client.request(path_base)
				var jsonArray = result?.jsonArray
				if(jsonArray != null) {
					saveRange(result, true, true)
					//
					var src = parser.notificationList(jsonArray)
					this.list_tmp = addWithFilterNotification(ArrayList(src.size), src)
					//
					if(! src.isEmpty()) {
						PollingWorker.injectData(context, access_info.db_id, src)
					}
					//
					val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
					while(true) {
						if(client.isApiCancelled) {
							log.d("loading-notifications: cancelled.")
							break
						}
						if(! isFilterEnabled) {
							log.d("loading-notifications: isFiltered is false.")
							break
						}
						if(max_id.isEmpty()) {
							log.d("loading-notifications: max_id is empty.")
							break
						}
						if((list_tmp?.size ?: 0) >= LOOP_READ_ENOUGH) {
							log.d("loading-notifications: read enough data.")
							break
						}
						if(src.isEmpty()) {
							log.d("loading-notifications: previous response is empty.")
							break
						}
						if(SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
							log.d("loading-notifications: timeout.")
							break
						}
						val path = path_base + delimiter + "max_id=" + max_id
						val result2 = client.request(path)
						jsonArray = result2?.jsonArray
						if(jsonArray == null) {
							log.d("loading-notifications: error or cancelled.")
							break
						}
						
						src = parser.notificationList(jsonArray)
						
						addWithFilterNotification(list_tmp, src)
						
						if(! saveRangeEnd(result2)) {
							log.d("loading-notifications: missing range info.")
							break
						}
					}
				}
				return result
			}
			
			override fun doInBackground(vararg params : Void) : TootApiResult? {
				val client = TootApiClient(context, callback = object : TootApiCallback {
					override val isApiCancelled : Boolean
						get() = isCancelled || is_dispose.get()
					
					override fun publishApiProgress(s : String) {
						runOnMainLooper {
							if(isCancelled) return@runOnMainLooper
							task_progress = s
							fireShowContent(reason = "loading progress", changeList = ArrayList())
						}
					}
				})
				
				client.account = access_info
				
				try {
					var result : TootApiResult?
					val q : String
					
					when(column_type) {
						TYPE_HOME -> return getStatuses(client, PATH_HOME)
						
						TYPE_LOCAL -> return getStatuses(client, PATH_LOCAL)
						
						TYPE_FEDERATE -> return getStatuses(client, PATH_FEDERATE)
						
						TYPE_PROFILE -> {
							
							loadProfileAccount(client, true)
							
							when(profile_tab) {
								
								TAB_FOLLOWING -> return parseAccountList(
									client,
									String.format(Locale.JAPAN, PATH_ACCOUNT_FOLLOWING, profile_id)
								)
								
								TAB_FOLLOWERS -> return parseAccountList(
									client,
									String.format(Locale.JAPAN, PATH_ACCOUNT_FOLLOWERS, profile_id)
								)
								
								TAB_STATUS -> {
									
									var instance = access_info.instance
									// まだ取得してない
									// 疑似アカウントの場合は過去のデータが別タンスかもしれない?
									if(instance == null || access_info.isPseudo) {
										val r2 = getInstanceInformation(client, null)
										if(instance_tmp != null) {
											instance = instance_tmp
											access_info.instance = instance
										}
										if(access_info.isPseudo) return r2
									}
									
									var s = String.format(
										Locale.JAPAN,
										PATH_ACCOUNT_STATUSES,
										profile_id
									)
									if(with_attachment && ! with_highlight) s += "&only_media=1"
									
									if(instance?.isEnoughVersion(version_1_6) == true) {
										getStatusesPinned(client, s + "&pinned=1")
									}
									
									return getStatuses(client, s)
								}
								
								else -> throw RuntimeException("profile_tab : invalid value.")
							}
						}
						
						TYPE_MUTES -> return parseAccountList(client, PATH_MUTES)
						
						TYPE_BLOCKS -> return parseAccountList(client, PATH_BLOCKS)
						
						TYPE_DOMAIN_BLOCKS -> return parseDomainList(client, PATH_DOMAIN_BLOCK)
						
						TYPE_LIST_LIST -> return parseListList(client, PATH_LIST_LIST)
						
						TYPE_LIST_TL -> {
							loadListInfo(client, true)
							return getStatuses(
								client,
								String.format(Locale.JAPAN, PATH_LIST_TL, profile_id)
							)
						}
						
						TYPE_LIST_MEMBER -> {
							loadListInfo(client, true)
							return parseAccountList(
								client,
								String.format(Locale.JAPAN, PATH_LIST_MEMBER, profile_id)
							)
						}
						
						TYPE_FOLLOW_REQUESTS -> return parseAccountList(
							client,
							PATH_FOLLOW_REQUESTS
						)
						
						TYPE_FAVOURITES -> return getStatuses(client, PATH_FAVOURITES)
						
						TYPE_HASHTAG -> return getStatuses(
							client,
							String.format(Locale.JAPAN, PATH_HASHTAG, hashtag.encodePercent())
						)
						
						TYPE_REPORTS -> return parseReports(client, PATH_REPORTS)
						
						TYPE_NOTIFICATIONS -> return parseNotifications(client)
						
						TYPE_BOOSTED_BY -> return parseAccountList(
							client,
							String.format(Locale.JAPAN, PATH_BOOSTED_BY, status_id)
						)
						
						TYPE_FAVOURITED_BY -> return parseAccountList(
							client,
							String.format(Locale.JAPAN, PATH_FAVOURITED_BY, status_id)
						)
						
						TYPE_CONVERSATION -> {
							
							// 指定された発言そのもの
							result = client.request(
								String.format(Locale.JAPAN, PATH_STATUSES, status_id)
							)
							var jsonObject = result?.jsonObject ?: return result
							val target_status = parser.status(jsonObject)
								?: return TootApiResult("TootStatus parse failed.")
							
							// 前後の会話
							result = client.request(
								String.format(Locale.JAPAN, PATH_STATUSES_CONTEXT, status_id)
							)
							jsonObject = result?.jsonObject ?: return result
							val conversation_context = parseItem(::TootContext, parser, jsonObject)
							
							// 一つのリストにまとめる
							target_status.conversation_main = true
							if(conversation_context != null) {
								
								this.list_tmp = ArrayList(
									1
										+ (conversation_context.ancestors?.size ?: 0)
										+ (conversation_context.descendants?.size ?: 0)
								)
								//
								if(conversation_context.ancestors != null)
									addWithFilterStatus(
										this.list_tmp,
										conversation_context.ancestors
									)
								//
								addOne(list_tmp, target_status)
								//
								if(conversation_context.descendants != null)
									addWithFilterStatus(
										this.list_tmp,
										conversation_context.descendants
									)
								//
							} else {
								showToast(context, true, "TootContext parse failed.")
								this.list_tmp = addOne(this.list_tmp, target_status)
							}
							
							// カードを取得する
							this.list_tmp?.forEach { o ->
								if(o is TootStatus)
									o.card = parseItem(
										::TootCard,
										client.request("/api/v1/statuses/" + o.id + "/card")?.jsonObject
									)
							}
							
							//
							return result
						}
						
						TYPE_SEARCH -> {
							if(access_info.isPseudo) {
								// 1.5.0rc からマストドンの検索APIは認証を要求するようになった
								return TootApiResult(context.getString(R.string.search_is_not_available_on_pseudo_account))
							}
							var path =
								String.format(
									Locale.JAPAN,
									PATH_SEARCH,
									search_query.encodePercent()
								)
							if(search_resolve) path += "&resolve=1"
							
							result = client.request(path)
							val jsonObject = result?.jsonObject
							if(result == null || jsonObject == null) return result
							
							val tmp = parser.results(jsonObject)
							if(tmp != null) {
								list_tmp = ArrayList()
								addAll(list_tmp, tmp.hashtags)
								addAll(list_tmp, tmp.accounts)
								addAll(list_tmp, tmp.statuses)
							}
							return result
						}
						
						TYPE_SEARCH_MSP -> {
							
							max_id = ""
							q = search_query.trim { it <= ' ' }
							if(q.isEmpty()) {
								list_tmp = ArrayList()
								result = TootApiResult()
							} else {
								result = client.searchMsp(search_query, max_id)
								val jsonArray = result?.jsonArray
								if(jsonArray != null) {
									// max_id の更新
									max_id = TootApiClient.getMspMaxId(jsonArray, max_id)
									// リストデータの用意
									parser.serviceType = ServiceType.MSP
									list_tmp =
										addWithFilterStatus(null, parser.statusList(jsonArray))
								}
							}
							return result
						}
						
						TYPE_SEARCH_TS -> {
							max_id = "0"
							q = search_query.trim { it <= ' ' }
							if(q.isEmpty()) {
								list_tmp = ArrayList()
								result = TootApiResult()
							} else {
								result = client.searchTootsearch(search_query, max_id)
								val jsonObject = result?.jsonObject
								if(jsonObject != null) {
									// max_id の更新
									max_id = TootApiClient.getTootsearchMaxId(jsonObject, max_id)
									// リストデータの用意
									val search_result =
										TootStatus.parseListTootsearch(parser, jsonObject)
									this.list_tmp = addWithFilterStatus(null, search_result)
									if(search_result.isEmpty()) {
										log.d("search result is empty. %s", result?.bodyString)
									}
								}
							}
							return result
						}
						
						TYPE_INSTANCE_INFORMATION -> {
							result = getInstanceInformation(client, instance_uri)
							if(instance_tmp != null) {
								instance_information = instance_tmp
							}
							return result
						}
						
						else -> return getStatuses(client, PATH_HOME)
					}
				} finally {
					try {
						updateRelation(client, list_tmp, who_account)
					} catch(ex : Throwable) {
						log.trace(ex)
					}
					
				}
			}
			
			override fun onCancelled(result : TootApiResult?) {
				onPostExecute(null)
			}
			
			override fun onPostExecute(result : TootApiResult?) {
				if(is_dispose.get()) return
				
				if(isCancelled || result == null) {
					return
				}
				
				bInitialLoading = false
				last_task = null
				
				if(result.error != null) {
					this@Column.mInitialLoadingError = result.error ?: ""
				} else {
					duplicate_map.clear()
					list_data.clear()
					val list_tmp = this.list_tmp
					if(list_tmp != null) {
						val list_pinned = this.list_pinned
						if(list_pinned?.isNotEmpty() == true) {
							val list_new = duplicate_map.filterDuplicate(list_pinned)
							list_data.addAll(list_new)
						}
						val list_new = duplicate_map.filterDuplicate(list_tmp)
						list_data.addAll(list_new)
					}
					
					resumeStreaming(false)
				}
				fireShowContent(reason = "loading updated", reset = true)
				
				// 初期ロードの直後は先頭に移動する
				viewHolder?.scrollToTop()
			}
		}
		this.last_task = task
		task.executeOnExecutor(App1.task_executor)
	}
	// int scroll_hack;
	
	private fun saveRange(result : TootApiResult?, bBottom : Boolean, bTop : Boolean) {
		if(result != null) {
			if(bBottom) {
				if(result.link_older == null) {
					max_id = ""
				} else {
					val m = reMaxId.matcher(result.link_older)
					if(m.find()) max_id = m.group(1)
				}
			}
			if(bTop && result.link_newer != null) {
				val m = reSinceId.matcher(result.link_newer)
				if(m.find()) since_id = m.group(1)
			}
		}
	}
	
	private fun saveRangeEnd(result : TootApiResult?) : Boolean {
		if(result != null) {
			if(result.link_older == null) {
				max_id = ""
			} else {
				val m = reMaxId.matcher(result.link_older)
				if(m.find()) {
					max_id = m.group(1)
					return true
				}
			}
		}
		return false
	}
	
	private fun addRange(bBottom : Boolean, path : String) : String {
		val delimiter = if(- 1 != path.indexOf('?')) '&' else '?'
		if(bBottom) {
			if(max_id.isNotEmpty()) return path + delimiter + "max_id=" + max_id
		} else {
			if(since_id.isNotEmpty()) return path + delimiter + "since_id=" + since_id
		}
		return path
	}
	
	internal fun startRefreshForPost(
		refresh_after_post : Int,
		posted_status_id : Long,
		posted_reply_id : String?
	) {
		when(column_type) {
			TYPE_HOME, TYPE_LOCAL, TYPE_FEDERATE -> startRefresh(
				true, false, posted_status_id,
				refresh_after_post
			)
			
			TYPE_PROFILE -> if(profile_tab == TAB_STATUS && profile_id == access_info.loginAccount?.id) {
				startRefresh(true, false, posted_status_id, refresh_after_post)
			}
			
			TYPE_CONVERSATION -> {
				// 会話への返信が行われたなら会話を更新する
				try {
					val reply_id = posted_reply_id?.toLong()
					if(reply_id != null) {
						for(item in list_data) {
							if(item is TootStatus && item.id == reply_id) {
								startLoading()
								break
							}
						}
					}
				} catch(ignored : Throwable) {
				}
			}
		}
	}
	
	internal fun startRefresh(
		bSilent : Boolean,
		bBottom : Boolean,
		posted_status_id : Long,
		refresh_after_toot : Int
	) {
		
		if(last_task != null) {
			if(! bSilent) {
				showToast(context, true, R.string.column_is_busy)
				val holder = viewHolder
				if(holder != null) holder.refreshLayout.isRefreshing = false
			}
			return
		} else if(bBottom && max_id.isEmpty()) {
			if(! bSilent) {
				showToast(context, true, R.string.end_of_list)
				val holder = viewHolder
				if(holder != null) holder.refreshLayout.isRefreshing = false
			}
			return
		} else if(! bBottom && since_id.isEmpty()) {
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
		
		val task = @SuppressLint("StaticFieldLeak")
		object : AsyncTask<Void, Void, TootApiResult?>() {
			internal var parser = TootParser(context, access_info, highlightTrie = highlight_trie)
			
			internal var list_tmp : ArrayList<TimelineItem>? = null
			
			internal fun getAccountList(
				client : TootApiClient,
				path_base : String
			) : TootApiResult? {
				val time_start = SystemClock.elapsedRealtime()
				val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
				val last_since_id = since_id
				
				val result = client.request(addRange(bBottom, path_base))
				var jsonArray = result?.jsonArray
				if(jsonArray != null) {
					saveRange(result, bBottom, ! bBottom)
					var src = parser.accountList(jsonArray)
					list_tmp = addAll(null, src)
					if(! bBottom) {
						var bGapAdded = false
						while(true) {
							if(isCancelled) {
								log.d("refresh-account-offset: cancelled.")
								break
							}
							
							// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
							// 直前のデータが0個なら終了とみなすしかなさそう
							if(src.isEmpty()) {
								log.d("refresh-account-offset: previous size == 0.")
								break
							}
							
							// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
							val max_id = src[src.size - 1].id.toString()
							
							if(SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
								log.d("refresh-account-offset: timeout. make gap.")
								// タイムアウト
								// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
								addOne(list_tmp, TootGap(max_id, last_since_id))
								bGapAdded = true
								break
							}
							
							val path =
								path_base + delimiter + "max_id=" + max_id + "&since_id=" + last_since_id
							val result2 = client.request(path)
							jsonArray = result2?.jsonArray
							if(jsonArray == null) {
								log.d("refresh-account-offset: error or cancelled. make gap.")
								// エラー
								// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
								addOne(list_tmp, TootGap(max_id, last_since_id))
								bGapAdded = true
								break
							}
							
							src = parser.accountList(jsonArray)
							addAll(list_tmp, src)
						}
						if(Pref.bpForceGap(context) && ! isCancelled && ! bGapAdded && list_tmp?.isNotEmpty() == true) {
							addOne(list_tmp, TootGap(max_id, last_since_id))
						}
					}
				}
				return result
			}
			
			internal fun getDomainList(
				client : TootApiClient,
				path_base : String
			) : TootApiResult? {
				val time_start = SystemClock.elapsedRealtime()
				val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
				val last_since_id = since_id
				
				val result = client.request(addRange(bBottom, path_base))
				var jsonArray = result?.jsonArray
				if(jsonArray != null) {
					saveRange(result, bBottom, ! bBottom)
					var src = TootDomainBlock.parseList(jsonArray)
					list_tmp = addAll(null, src)
					if(! bBottom) {
						while(true) {
							if(isCancelled) {
								log.d("refresh-domain-offset: cancelled.")
								break
							}
							
							// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
							// 直前のデータが0個なら終了とみなすしかなさそう
							if(src.isEmpty()) {
								log.d("refresh-domain-offset: previous size == 0.")
								break
							}
							
							if(SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
								log.d("refresh-domain-offset: timeout.")
								// タイムアウト
								break
							}
							
							val path =
								path_base + delimiter + "max_id=" + max_id + "&since_id=" + last_since_id
							val result2 = client.request(path)
							jsonArray = result2?.jsonArray
							if(jsonArray == null) {
								log.d("refresh-domain-offset: error or cancelled.")
								// エラー
								break
							}
							
							src = TootDomainBlock.parseList(jsonArray)
							addAll(list_tmp, src)
						}
					}
				}
				return result
			}
			
			internal fun getListList(client : TootApiClient, path_base : String) : TootApiResult? {
				val time_start = SystemClock.elapsedRealtime()
				val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
				val last_since_id = since_id
				val result = client.request(addRange(bBottom, path_base))
				var jsonArray = result?.jsonArray
				if(jsonArray != null) {
					saveRange(result, bBottom, ! bBottom)
					var src = parseList(::TootList, jsonArray)
					src.sort()
					list_tmp = addAll(null, src)
					if(! bBottom) {
						var bGapAdded = false
						while(true) {
							if(isCancelled) {
								log.d("refresh-list-offset: cancelled.")
								break
							}
							
							// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
							// 直前のデータが0個なら終了とみなすしかなさそう
							if(src.isEmpty()) {
								log.d("refresh-list-offset: previous size == 0.")
								break
							}
							
							// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
							val max_id = src[src.size - 1].id.toString()
							
							if(SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
								log.d("refresh-list-offset: timeout. make gap.")
								// タイムアウト
								// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
								addOne(list_tmp, TootGap(max_id, last_since_id))
								bGapAdded = true
								break
							}
							
							val path =
								path_base + delimiter + "max_id=" + max_id + "&since_id=" + last_since_id
							val result2 = client.request(path)
							jsonArray = result2?.jsonArray
							if(jsonArray == null) {
								log.d("refresh-list-offset: timeout. error or retry. make gap.")
								// エラー
								// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
								addOne(list_tmp, TootGap(max_id, last_since_id))
								bGapAdded = true
								break
							}
							
							src = parseList(::TootList, jsonArray)
							src.sort()
							addAll(list_tmp, src)
						}
						if(Pref.bpForceGap(context) && ! isCancelled && ! bGapAdded && list_tmp?.isNotEmpty() == true) {
							addOne(list_tmp, TootGap(max_id, last_since_id))
						}
					}
				}
				return result
			}
			
			internal fun getReportList(
				client : TootApiClient,
				path_base : String
			) : TootApiResult? {
				val time_start = SystemClock.elapsedRealtime()
				val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
				val last_since_id = since_id
				val result = client.request(addRange(bBottom, path_base))
				var jsonArray = result?.jsonArray
				if(jsonArray != null) {
					saveRange(result, bBottom, ! bBottom)
					var src = parseList(::TootReport, jsonArray)
					list_tmp = addAll(null, src)
					if(! bBottom) {
						var bGapAdded = false
						while(true) {
							if(isCancelled) {
								log.d("refresh-report-offset: cancelled.")
								break
							}
							
							// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
							// 直前のデータが0個なら終了とみなすしかなさそう
							if(src.isEmpty()) {
								log.d("refresh-report-offset: previous size == 0.")
								break
							}
							
							// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
							val max_id = src[src.size - 1].id.toString()
							
							if(SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
								log.d("refresh-report-offset: timeout. make gap.")
								// タイムアウト
								// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
								addOne(list_tmp, TootGap(max_id, last_since_id))
								bGapAdded = true
								break
							}
							
							val path =
								path_base + delimiter + "max_id=" + max_id + "&since_id=" + last_since_id
							val result2 = client.request(path)
							jsonArray = result2?.jsonArray
							if(jsonArray == null) {
								log.d("refresh-report-offset: timeout. error or retry. make gap.")
								// エラー
								// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
								addOne(list_tmp, TootGap(max_id, last_since_id))
								bGapAdded = true
								break
							}
							
							src = parseList(::TootReport, jsonArray)
							addAll(list_tmp, src)
						}
						if(Pref.bpForceGap(context) && ! isCancelled && ! bGapAdded && list_tmp?.isNotEmpty() == true) {
							addOne(list_tmp, TootGap(max_id, last_since_id))
						}
					}
				}
				return result
			}
			
			internal fun getNotificationList( client : TootApiClient ) : TootApiResult? {
				val path_base  = makeNotificationUrl()
				val time_start = SystemClock.elapsedRealtime()
				val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
				val last_since_id = since_id
				
				val result = client.request(addRange(bBottom, path_base))
				var jsonArray = result?.jsonArray
				if(jsonArray != null) {
					saveRange(result, bBottom, ! bBottom)
					var src = parser.notificationList(jsonArray)
					list_tmp = addWithFilterNotification(null, src)
					
					if(! bBottom) {
						
						if(! src.isEmpty()) {
							PollingWorker.injectData(context, access_info.db_id, src)
						}
						var bGapAdded = false
						while(true) {
							if(isCancelled) {
								log.d("refresh-notification-offset: cancelled.")
								break
							}
							
							// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
							// 直前のデータが0個なら終了とみなすしかなさそう
							if(src.isEmpty()) {
								log.d("refresh-notification-offset: previous size == 0.")
								break
							}
							
							// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
							val max_id = src[src.size - 1].id.toString()
							
							if(SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
								log.d("refresh-notification-offset: timeout. make gap.")
								// タイムアウト
								// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
								addOne(list_tmp, TootGap(max_id, last_since_id))
								bGapAdded = true
								break
							}
							
							val path =
								path_base + delimiter + "max_id=" + max_id + "&since_id=" + last_since_id
							val result2 = client.request(path)
							jsonArray = result2?.jsonArray
							if(jsonArray == null) {
								log.d("refresh-notification-offset: error or cancelled. make gap.")
								// エラー
								// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
								addOne(list_tmp, TootGap(max_id, last_since_id))
								bGapAdded = true
								break
							}
							
							src = parser.notificationList(jsonArray)
							if(! src.isEmpty()) {
								addWithFilterNotification(list_tmp, src)
								PollingWorker.injectData(context, access_info.db_id, src)
							}
						}
						if(Pref.bpForceGap(context) && ! isCancelled && ! bGapAdded && list_tmp?.isNotEmpty() == true) {
							addOne(list_tmp, TootGap(max_id, last_since_id))
						}
					} else {
						while(true) {
							if(isCancelled) {
								log.d("refresh-notification-bottom: cancelled.")
								break
							}
							
							// bottomの場合、フィルタなしなら繰り返さない
							if(! isFilterEnabled) {
								log.d("refresh-notification-bottom: isFiltered is false.")
								break
							}
							
							// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
							// 直前のデータが0個なら終了とみなすしかなさそう
							if(src.isEmpty()) {
								log.d("refresh-notification-bottom: previous size == 0.")
								break
							}
							
							// 十分読んだらそれで終了
							if((list_tmp?.size ?: 0) >= LOOP_READ_ENOUGH) {
								log.d("refresh-notification-bottom: read enough data.")
								break
							}
							
							if(SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
								// タイムアウト
								log.d("refresh-notification-bottom: loop timeout.")
								break
							}
							
							val path = path_base + delimiter + "max_id=" + max_id
							val result2 = client.request(path)
							jsonArray = result2?.jsonArray
							if(jsonArray == null) {
								log.d("refresh-notification-bottom: error or cancelled.")
								break
							}
							
							src = parser.notificationList(jsonArray)
							
							addWithFilterNotification(list_tmp, src)
							
							if(! saveRangeEnd(result2)) {
								log.d("refresh-notification-bottom: saveRangeEnd failed.")
								break
							}
						}
					}
				}
				return result
			}
			
			internal fun getStatusList(
				client : TootApiClient,
				path_base : String
			) : TootApiResult? {
				
				val time_start = SystemClock.elapsedRealtime()
				
				val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
				val last_since_id = since_id
				
				val result = client.request(addRange(bBottom, path_base))
				var jsonArray = result?.jsonArray
				if(jsonArray != null) {
					saveRange(result, bBottom, ! bBottom)
					var src = parser.statusList(jsonArray)
					list_tmp = addWithFilterStatus(null, src)
					
					if(bBottom) {
						while(true) {
							if(isCancelled) {
								log.d("refresh-status-bottom: cancelled.")
								break
							}
							
							// bottomの場合、フィルタなしなら繰り返さない
							if(! isFilterEnabled) {
								log.d("refresh-status-bottom: isFiltered is false.")
								break
							}
							
							// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
							// 直前のデータが0個なら終了とみなすしかなさそう
							if(src.isEmpty()) {
								log.d("refresh-status-bottom: previous size == 0.")
								break
							}
							
							// 十分読んだらそれで終了
							if((list_tmp?.size ?: 0) >= LOOP_READ_ENOUGH) {
								log.d("refresh-status-bottom: read enough data.")
								break
							}
							
							if(SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
								// タイムアウト
								log.d("refresh-status-bottom: loop timeout.")
								break
							}
							
							val path = path_base + delimiter + "max_id=" + max_id
							val result2 = client.request(path)
							jsonArray = result2?.jsonArray
							if(jsonArray == null) {
								log.d("refresh-status-bottom: error or cancelled.")
								break
							}
							
							src = parser.statusList(jsonArray)
							
							addWithFilterStatus(list_tmp, src)
							
							if(! saveRangeEnd(result2)) {
								log.d("refresh-status-bottom: saveRangeEnd failed.")
								break
							}
						}
					} else {
						var bGapAdded = false
						while(true) {
							if(isCancelled) {
								log.d("refresh-status-offset: cancelled.")
								break
							}
							
							// 頭の方を読む時は隙間を減らすため、フィルタの有無に関係なく繰り返しを行う
							
							// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
							// 直前のデータが0個なら終了とみなすしかなさそう
							if(src.isEmpty()) {
								log.d("refresh-status-offset: previous size == 0.")
								break
							}
							
							// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
							val max_id = src[src.size - 1].id.toString()
							
							if((list_tmp?.size ?: 0) >= LOOP_READ_ENOUGH) {
								log.d("refresh-status-offset: read enough. make gap.")
								// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
								addOne(list_tmp, TootGap(max_id, last_since_id))
								bGapAdded = true
								break
							}
							
							if(SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
								log.d("refresh-status-offset: timeout. make gap.")
								// タイムアウト
								// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
								addOne(list_tmp, TootGap(max_id, last_since_id))
								bGapAdded = true
								break
							}
							
							val path =
								path_base + delimiter + "max_id=" + max_id + "&since_id=" + last_since_id
							val result2 = client.request(path)
							jsonArray = result2?.jsonArray
							if(jsonArray == null) {
								log.d("refresh-status-offset: error or cancelled. make gap.")
								// エラー
								// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
								addOne(list_tmp, TootGap(max_id, last_since_id))
								bGapAdded = true
								break
							}
							
							src = parser.statusList(jsonArray)
							addWithFilterStatus(list_tmp, src)
						}
						if(Pref.bpForceGap(context) && ! isCancelled && ! bGapAdded && list_tmp?.isNotEmpty() == true) {
							addOne(list_tmp, TootGap(max_id, last_since_id))
						}
					}
				}
				return result
			}
			
			override fun doInBackground(vararg params : Void) : TootApiResult? {
				val client = TootApiClient(context, callback = object : TootApiCallback {
					override val isApiCancelled : Boolean
						get() = isCancelled || is_dispose.get()
					
					override fun publishApiProgress(s : String) {
						runOnMainLooper {
							if(isCancelled) return@runOnMainLooper
							task_progress = s
							fireShowContent(reason = "refresh progress", changeList = ArrayList())
						}
					}
				})
				
				client.account = access_info
				try {
					
					return when(column_type) {
						TYPE_HOME -> getStatusList(client, PATH_HOME)
						
						TYPE_LOCAL -> getStatusList(client, PATH_LOCAL)
						
						TYPE_FEDERATE -> getStatusList(client, PATH_FEDERATE)
						
						TYPE_FAVOURITES -> getStatusList(client, PATH_FAVOURITES)
						
						TYPE_REPORTS -> getReportList(client, PATH_REPORTS)
						
						TYPE_NOTIFICATIONS -> getNotificationList(client)
						
						TYPE_BOOSTED_BY -> getAccountList(
							client, String.format(
								Locale.JAPAN, PATH_BOOSTED_BY,
								posted_status_id
							)
						)
						
						TYPE_FAVOURITED_BY -> getAccountList(
							client, String.format(
								Locale.JAPAN, PATH_FAVOURITED_BY,
								posted_status_id
							)
						)
						
						TYPE_PROFILE -> {
							loadProfileAccount(client, false)
							when(profile_tab) {
								TAB_FOLLOWING -> getAccountList(
									client,
									String.format(Locale.JAPAN, PATH_ACCOUNT_FOLLOWING, profile_id)
								)
								
								TAB_FOLLOWERS -> return getAccountList(
									client,
									String.format(Locale.JAPAN, PATH_ACCOUNT_FOLLOWERS, profile_id)
								)
								
								else -> {
									if(access_info.isPseudo) {
										client.request(PATH_INSTANCE)
									} else {
										var s = String.format(
											Locale.JAPAN,
											PATH_ACCOUNT_STATUSES,
											profile_id
										)
										if(with_attachment && ! with_highlight) s += "&only_media=1"
										getStatusList(client, s)
									}
								}
							}
						}
						
						TYPE_LIST_LIST -> getListList(client, PATH_LIST_LIST)
						
						TYPE_LIST_TL -> {
							loadListInfo(client, false)
							getStatusList(
								client,
								String.format(Locale.JAPAN, PATH_LIST_TL, profile_id)
							)
						}
						
						TYPE_LIST_MEMBER -> {
							loadListInfo(client, false)
							getAccountList(
								client,
								String.format(Locale.JAPAN, PATH_LIST_MEMBER, profile_id)
							)
						}
						
						TYPE_MUTES -> getAccountList(client, PATH_MUTES)
						
						TYPE_BLOCKS -> getAccountList(client, PATH_BLOCKS)
						
						TYPE_DOMAIN_BLOCKS -> getDomainList(client, PATH_DOMAIN_BLOCK)
						
						TYPE_FOLLOW_REQUESTS -> getAccountList(client, PATH_FOLLOW_REQUESTS)
						
						TYPE_HASHTAG -> getStatusList(
							client,
							String.format(Locale.JAPAN, PATH_HASHTAG, hashtag.encodePercent())
						)
						
						TYPE_SEARCH_MSP ->
							if(! bBottom) {
								TootApiResult("head of list.")
							} else {
								val result : TootApiResult?
								val q = search_query.trim { it <= ' ' }
								if(q.isEmpty()) {
									list_tmp = ArrayList()
									result = TootApiResult(context.getString(R.string.end_of_list))
								} else {
									result = client.searchMsp(search_query, max_id)
									val jsonArray = result?.jsonArray
									if(jsonArray != null) {
										// max_id の更新
										max_id = TootApiClient.getMspMaxId(jsonArray, max_id)
										// リストデータの用意
										parser.serviceType = ServiceType.MSP
										list_tmp = addWithFilterStatus(
											list_tmp,
											parser.statusList(jsonArray)
										)
									}
								}
								result
							}
						
						TYPE_SEARCH_TS -> if(! bBottom) {
							TootApiResult("head of list.")
						} else {
							val result : TootApiResult?
							val q = search_query.trim { it <= ' ' }
							if(q.isEmpty() || max_id.isEmpty()) {
								list_tmp = ArrayList()
								result = TootApiResult(context.getString(R.string.end_of_list))
							} else {
								result = client.searchTootsearch(search_query, max_id)
								val jsonObject = result?.jsonObject
								if(jsonObject != null) {
									// max_id の更新
									max_id = TootApiClient.getTootsearchMaxId(jsonObject, max_id)
									// リストデータの用意
									val search_result =
										TootStatus.parseListTootsearch(parser, jsonObject)
									list_tmp = addWithFilterStatus(list_tmp, search_result)
								}
							}
							result
						}
						
						else -> getStatusList(client, PATH_HOME)
					}
				} finally {
					try {
						updateRelation(client, list_tmp, who_account)
					} catch(ex : Throwable) {
						log.trace(ex)
					}
					
				}
			}
			
			override fun onCancelled(result : TootApiResult?) {
				onPostExecute(null)
			}
			
			override fun onPostExecute(result : TootApiResult?) {
				if(is_dispose.get()) return
				
				if(isCancelled || result == null) {
					return
				}
				try {
					last_task = null
					bRefreshLoading = false
					
					val error = result.error
					if(error != null) {
						mRefreshLoadingError = error
						fireShowContent(reason = "refresh error", changeList = ArrayList())
						return
					}
					
					val list_new = duplicate_map.filterDuplicate(list_tmp)
					if(list_new.isEmpty()) {
						fireShowContent(
							reason = "refresh list_new is empty",
							changeList = ArrayList()
						)
						return
					}
					
					// 事前にスクロール位置を覚えておく
					var sp : ScrollPosition? = null
					val holder = viewHolder
					if(holder != null) {
						sp = holder.scrollPosition
					}
					
					val added = list_new.size
					
					if(bBottom) {
						val changeList = listOf(
							AdapterChange(
								AdapterChangeType.RangeInsert,
								list_data.size,
								added
							)
						)
						list_data.addAll(list_new)
						fireShowContent(reason = "refresh updated bottom", changeList = changeList)
						
						// 新着が少しだけ見えるようにスクロール位置を移動する
						if(sp != null) {
							holder?.setScrollPosition(sp, 20f)
						}
					} else {
						
						for(o in list_new) {
							if(o is TootStatus) {
								val highlight_sound = o.highlight_sound
								if(highlight_sound != null) {
									App1.sound(highlight_sound)
									break
								}
							}
						}
						
						// 投稿後のリフレッシュなら当該投稿の位置を探す
						var status_index = - 1
						for(i in 0 until added) {
							val o = list_new[i]
							if(o is TootStatus && o.id == posted_status_id) {
								status_index = i
								break
							}
						}
						
						val changeList =
							listOf(AdapterChange(AdapterChangeType.RangeInsert, 0, added))
						list_data.addAll(0, list_new)
						fireShowContent(reason = "refresh updated head", changeList = changeList)
						
						if(status_index >= 0 && refresh_after_toot == Pref.RAT_REFRESH_SCROLL) {
							// 投稿後にその投稿にスクロールする
							if(holder != null) {
								holder.setScrollPosition(
									ScrollPosition(toAdapterIndex(status_index), 0),
									0f
								)
							} else {
								scroll_save = ScrollPosition(toAdapterIndex(status_index), 0)
							}
						} else {
							//
							val scroll_save = this@Column.scroll_save
							when {
							// ViewHolderがある場合は増加件数分+deltaの位置にスクロールする
								sp != null -> {
									sp.adapterIndex += added
									val delta = if(bSilent) 0f else - 20f
									holder?.setScrollPosition(sp, delta)
								}
							// ViewHolderがなくて保存中の位置がある場合、増加件数分ずらす。deltaは難しいので反映しない
								scroll_save != null -> scroll_save.adapterIndex += added
							// 保存中の位置がない場合、保存中の位置を新しく作る
								else -> this@Column.scroll_save =
									ScrollPosition(toAdapterIndex(added), 0)
							}
						}
					}
				} finally {
					if(! bBottom) {
						bRefreshingTop = false
						resumeStreaming(false)
					}
				}
			}
		}
		this.last_task = task
		task.executeOnExecutor(App1.task_executor)
	}
	
	internal fun startGap(gap : TootGap?) {
		if(gap == null) {
			showToast(context, true, "gap is null")
			return
		}
		if(last_task != null) {
			showToast(context, true, R.string.column_is_busy)
			return
		}
		
		viewHolder?.refreshLayout?.isRefreshing = true
		
		bRefreshLoading = true
		mRefreshLoadingError = ""
		
		val task = @SuppressLint("StaticFieldLeak")
		object : AsyncTask<Void, Void, TootApiResult?>() {
			internal var max_id = gap.max_id
			internal val since_id = gap.since_id
			internal var list_tmp : ArrayList<TimelineItem>? = null
			
			internal var parser = TootParser(context, access_info, highlightTrie = highlight_trie)
			
			internal fun getAccountList(
				client : TootApiClient,
				path_base : String
			) : TootApiResult? {
				val time_start = SystemClock.elapsedRealtime()
				val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
				list_tmp = ArrayList()
				
				var result : TootApiResult? = null
				while(true) {
					if(isCancelled) {
						log.d("gap-account: cancelled.")
						break
					}
					
					if(result != null && SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
						log.d("gap-account: timeout. make gap.")
						// タイムアウト
						// 隙間が残る
						addOne(list_tmp, TootGap(max_id, since_id))
						break
					}
					
					val path = path_base + delimiter + "max_id=" + max_id + "&since_id=" + since_id
					val r2 = client.request(path)
					val jsonArray = r2?.jsonArray
					if(jsonArray == null) {
						log.d("gap-account: error timeout. make gap.")
						
						if(result == null) result = r2
						
						// 隙間が残る
						addOne(list_tmp, TootGap(max_id, since_id))
						break
					}
					result = r2
					val src = parser.accountList(jsonArray)
					
					if(src.isEmpty()) {
						log.d("gap-account: empty.")
						break
					}
					
					addAll(list_tmp, src)
					
					// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
					max_id = src[src.size - 1].id.toString()
					
				}
				return result
			}
			
			internal fun getReportList(
				client : TootApiClient,
				path_base : String
			) : TootApiResult? {
				val time_start = SystemClock.elapsedRealtime()
				val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
				list_tmp = ArrayList()
				
				var result : TootApiResult? = null
				while(true) {
					if(isCancelled) {
						log.d("gap-report: cancelled.")
						break
					}
					
					if(result != null && SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
						log.d("gap-report: timeout. make gap.")
						// タイムアウト
						// 隙間が残る
						addOne(list_tmp, TootGap(max_id, since_id))
						break
					}
					
					val path = path_base + delimiter + "max_id=" + max_id + "&since_id=" + since_id
					val r2 = client.request(path)
					val jsonArray = r2?.jsonArray
					if(jsonArray == null) {
						log.d("gap-report: error or cancelled. make gap.")
						if(result == null) result = r2
						// 隙間が残る
						addOne(list_tmp, TootGap(max_id, since_id))
						break
					}
					
					result = r2
					val src = parseList(::TootReport, jsonArray)
					if(src.isEmpty()) {
						log.d("gap-report: empty.")
						// コレ以上取得する必要はない
						break
					}
					
					addAll(list_tmp, src)
					
					// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
					max_id = src[src.size - 1].id.toString()
				}
				return result
			}
			
			internal fun getNotificationList( client : TootApiClient ) : TootApiResult? {
				val path_base  = makeNotificationUrl()
				
				val time_start = SystemClock.elapsedRealtime()
				val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
				
				
				
				list_tmp = ArrayList()
				
				var result : TootApiResult? = null
				while(true) {
					if(isCancelled) {
						log.d("gap-notification: cancelled.")
						break
					}
					
					if(result != null && SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
						log.d("gap-notification: timeout. make gap.")
						// タイムアウト
						// 隙間が残る
						addOne(list_tmp, TootGap(max_id, since_id))
						break
					}
					val path = path_base + delimiter + "max_id=" + max_id + "&since_id=" + since_id
					val r2 = client.request(path)
					val jsonArray = r2?.jsonArray
					if(jsonArray == null) {
						// エラー
						log.d("gap-notification: error or response. make gap.")
						
						if(result == null) result = r2
						
						// 隙間が残る
						addOne(list_tmp, TootGap(max_id, since_id))
						break
					}
					
					result = r2
					val src = parser.notificationList(jsonArray)
					
					if(src.isEmpty()) {
						log.d("gap-notification: empty.")
						break
					}
					
					// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
					max_id = src[src.size - 1].id.toString()
					
					addWithFilterNotification(list_tmp, src)
					
					PollingWorker.injectData(context, access_info.db_id, src)
					
				}
				return result
			}
			
			internal fun getStatusList(
				client : TootApiClient,
				path_base : String
			) : TootApiResult? {
				val time_start = SystemClock.elapsedRealtime()
				val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
				list_tmp = ArrayList()
				
				var result : TootApiResult? = null
				while(true) {
					if(isCancelled) {
						log.d("gap-statuses: cancelled.")
						break
					}
					
					if(result != null && SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
						log.d("gap-statuses: timeout.")
						// タイムアウト
						// 隙間が残る
						addOne(list_tmp, TootGap(max_id, since_id))
						break
					}
					
					val path = path_base + delimiter + "max_id=" + max_id + "&since_id=" + since_id
					
					val r2 = client.request(path)
					val jsonArray = r2?.jsonArray
					if(jsonArray == null) {
						log.d("gap-statuses: error or cancelled. make gap.")
						
						// 成功データがない場合だけ、今回のエラーを返すようにする
						if(result == null) result = r2
						
						// 隙間が残る
						addOne(list_tmp, TootGap(max_id, since_id))
						
						break
					}
					
					// 成功した場合はそれを返したい
					result = r2
					
					val src = parser.statusList(jsonArray)
					if(src.size == 0) {
						// 直前の取得でカラのデータが帰ってきたら終了
						log.d("gap-statuses: empty.")
						break
					}
					// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
					max_id = src[src.size - 1].id.toString()
					
					addWithFilterStatus(list_tmp, src)
				}
				return result
			}
			
			override fun doInBackground(vararg params : Void) : TootApiResult? {
				val client = TootApiClient(context, callback = object : TootApiCallback {
					override val isApiCancelled : Boolean
						get() = isCancelled || is_dispose.get()
					
					override fun publishApiProgress(s : String) {
						runOnMainLooper {
							if(isCancelled) return@runOnMainLooper
							task_progress = s
							fireShowContent(reason = "gap progress", changeList = ArrayList())
						}
					}
				})
				
				client.account = access_info
				
				try {
					return when(column_type) {
						TYPE_LOCAL -> getStatusList(client, PATH_LOCAL)
						
						TYPE_FEDERATE -> getStatusList(client, PATH_FEDERATE)
						
						TYPE_LIST_TL -> getStatusList(
							client,
							String.format(Locale.JAPAN, PATH_LIST_TL, profile_id)
						)
						
						TYPE_FAVOURITES -> getStatusList(client, PATH_FAVOURITES)
						
						TYPE_REPORTS -> getReportList(client, PATH_REPORTS)
						
						TYPE_NOTIFICATIONS -> getNotificationList(client)
						
						TYPE_HASHTAG -> getStatusList(
							client,
							String.format(Locale.JAPAN, PATH_HASHTAG, hashtag.encodePercent())
						)
						
						TYPE_BOOSTED_BY -> getAccountList(
							client,
							String.format(Locale.JAPAN, PATH_BOOSTED_BY, status_id)
						)
						
						TYPE_FAVOURITED_BY -> getAccountList(
							client,
							String.format(Locale.JAPAN, PATH_FAVOURITED_BY, status_id)
						)
						
						TYPE_MUTES -> getAccountList(client, PATH_MUTES)
						
						TYPE_BLOCKS -> getAccountList(client, PATH_BLOCKS)
						
						TYPE_FOLLOW_REQUESTS -> getAccountList(client, PATH_FOLLOW_REQUESTS)
						
						TYPE_PROFILE -> when(profile_tab) {
							TAB_FOLLOWING -> getAccountList(
								client,
								String.format(Locale.JAPAN, PATH_ACCOUNT_FOLLOWING, profile_id)
							)
							
							TAB_FOLLOWERS -> getAccountList(
								client,
								String.format(Locale.JAPAN, PATH_ACCOUNT_FOLLOWERS, profile_id)
							)
							
							else -> if(access_info.isPseudo) {
								client.request(PATH_INSTANCE)
							} else {
								var s =
									String.format(Locale.JAPAN, PATH_ACCOUNT_STATUSES, profile_id)
								if(with_attachment && ! with_highlight) s += "&only_media=1"
								getStatusList(client, s)
							}
						}
						
						else -> return getStatusList(client, PATH_HOME)
					}
				} finally {
					try {
						updateRelation(client, list_tmp, who_account)
					} catch(ex : Throwable) {
						log.trace(ex)
					}
					
				}
			}
			
			override fun onCancelled(result : TootApiResult?) {
				onPostExecute(null)
			}
			
			override fun onPostExecute(result : TootApiResult?) {
				if(is_dispose.get()) return
				
				if(isCancelled || result == null) {
					return
				}
				
				last_task = null
				bRefreshLoading = false
				
				val error = result.error
				if(error != null) {
					mRefreshLoadingError = error
					fireShowContent(reason = "gap error", changeList = ArrayList())
					return
				}
				
				val position = list_data.indexOf(gap)
				if(position == - 1) {
					log.d("gap not found..")
					fireShowContent(reason = "gap not found", changeList = ArrayList())
					return
				}
				
				val list_tmp = this.list_tmp
				if(list_tmp == null) {
					fireShowContent(reason = "gap list_tmp is null", changeList = ArrayList())
					return
				}
				
				// 0個でもギャップを消すために以下の処理を続ける
				
				val list_new = duplicate_map.filterDuplicate(list_tmp)
				
				// idx番目の要素がListViewのtopから何ピクセル下にあるか
				var restore_idx = position + 1
				var restore_y = 0
				val holder = viewHolder
				if(holder != null) {
					try {
						restore_y = holder.getListItemTop(restore_idx)
					} catch(ex : IndexOutOfBoundsException) {
						restore_idx = position
						try {
							restore_y = holder.getListItemTop(restore_idx)
						} catch(ex2 : IndexOutOfBoundsException) {
							restore_idx = - 1
						}
					}
				}
				
				val added = list_new.size // may 0
				list_data.removeAt(position)
				list_data.addAll(position, list_new)
				
				val changeList = ArrayList<AdapterChange>()
				changeList.add(AdapterChange(AdapterChangeType.RangeRemove, position))
				if(added > 0) {
					changeList.add(AdapterChange(AdapterChangeType.RangeInsert, position, added))
				}
				fireShowContent(reason = "gap updated", changeList = changeList)
				
				if(holder != null) {
					if(restore_idx >= 0) {
						// ギャップが画面内にあるなら
						holder.setListItemTop(restore_idx + added - 1, restore_y)
					} else {
						// ギャップが画面内にない場合、何もしない
					}
				} else {
					val scroll_save = this@Column.scroll_save
					if(scroll_save != null) {
						scroll_save.adapterIndex += added - 1
					}
				}
			}
		}
		this.last_task = task
		
		task.executeOnExecutor(App1.task_executor)
	}
	
	enum class HeaderType(val viewType : Int) {
		Profile(1),
		Search(2),
		Instance(3),
	}
	
	val headerType : HeaderType?
		get() = when(column_type) {
			Column.TYPE_PROFILE -> HeaderType.Profile
			Column.TYPE_SEARCH -> HeaderType.Search
			Column.TYPE_SEARCH_MSP -> HeaderType.Search
			Column.TYPE_SEARCH_TS -> HeaderType.Search
			Column.TYPE_INSTANCE_INFORMATION -> HeaderType.Instance
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
		return context.loadRawResource(res_id)?.decodeUTF8() ?: "?"
	}
	
	private var cacheHeaderDesc : String? = null
	
	fun getHeaderDesc() : String? {
		var cache = cacheHeaderDesc
		if(cache != null) return cache
		cache = when(column_type) {
			Column.TYPE_SEARCH -> context.getString(R.string.search_desc_mastodon_api)
			Column.TYPE_SEARCH_MSP -> loadSearchDesc(
				R.raw.search_desc_msp_en,
				R.raw.search_desc_msp_ja
			)
			Column.TYPE_SEARCH_TS -> loadSearchDesc(
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
	
	private fun getId(o : Any) : Long {
		return when(o) {
			is TootNotification -> o.id
			is TootStatus -> o.id
			is TootAccount -> o.id
			else -> throw RuntimeException("getId: object is not status,notification")
		}
	}
	
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
			startRefresh(true, false, - 1L, - 1)
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
		return when(column_type) {
			TYPE_REPORTS, TYPE_MUTES, TYPE_BLOCKS, TYPE_DOMAIN_BLOCKS, TYPE_FOLLOW_REQUESTS,
			TYPE_BOOSTED_BY, TYPE_FAVOURITED_BY, TYPE_INSTANCE_INFORMATION, TYPE_LIST_LIST, TYPE_LIST_MEMBER -> false
			else -> true
		}
		
	}
	
	// カラム設定に「すべての画像を隠す」ボタンを含めるなら真
	internal fun canNSFWDefault() : Boolean {
		return canStatusFilter()
	}
	
	// カラム設定に「ブーストを表示しない」ボタンを含めるなら真
	fun canFilterBoost() : Boolean {
		return when(column_type) {
			TYPE_HOME, TYPE_PROFILE, TYPE_NOTIFICATIONS, TYPE_LIST_TL -> true
			else -> false
		}
	}
	
	// カラム設定に「返信を表示しない」ボタンを含めるなら真
	fun canFilterReply() : Boolean {
		return when(column_type) {
			TYPE_HOME, TYPE_PROFILE, TYPE_LIST_TL, TYPE_NOTIFICATIONS -> true
			else -> false
		}
	}
	
	fun canFilterNormalToot() : Boolean {
		return when(column_type) {
			TYPE_HOME -> true
			else -> false
		}
	}
	
	internal fun canAutoRefresh() : Boolean {
		return streamPath != null
	}
	
	fun canReloadWhenRefreshTop() : Boolean {
		return when(column_type) {
			TYPE_SEARCH, TYPE_SEARCH_MSP, TYPE_SEARCH_TS, TYPE_CONVERSATION, TYPE_LIST_LIST -> true
			else -> false
		}
	}
	
	internal fun canSpeech() : Boolean {
		return canStreaming() && column_type != TYPE_NOTIFICATIONS
	}
	
	internal fun canStreaming() : Boolean {
		return ! access_info.isNA && if(access_info.isPseudo) isPublicStream else streamPath != null
	}
	
	private fun resumeStreaming(bPutGap : Boolean) {
		
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
		
		app_state.stream_reader.register(
			access_info, stream_path, highlight_trie, onStreamingMessage
		)
	}
	
	// onPauseの時はまとめて止められるが
	// カラム破棄やリロード開始時は個別にストリーミングを止める必要がある
	internal fun stopStreaming() {
		val stream_path = streamPath
		if(stream_path != null) {
			app_state.stream_reader.unregister(
				access_info, stream_path, onStreamingMessage
			)
		}
	}
	
	private val onStreamingMessage = fun(event_type : String, item : Any?) {
		if(is_dispose.get()) return
		
		if(item is Long) {
			if("delete" == event_type) {
				removeStatus(access_info, item)
			}
			return
		} else if(item is TimelineItem) {
			if(item is TootNotification) {
				if(column_type != TYPE_NOTIFICATIONS) return
				if(isFiltered(item)) return
			} else if(item is TootStatus) {
				if(column_type == TYPE_NOTIFICATIONS) return
				if(column_type == TYPE_LOCAL && item.account.acct.indexOf('@') != - 1) return
				if(isFiltered(item)) return
				
				if(this.enable_speech) {
					App1.getAppState(context).addSpeech(item.reblog ?: item)
				}
			}
			stream_data_queue.addFirst(item)
			mergeStreamingMessage.run()
		}
		
	}
	
	private val mergeStreamingMessage = object : Runnable {
		override fun run() {
			App1.getAppState(context).handler.removeCallbacks(this)
			
			val now = SystemClock.elapsedRealtime()
			
			// 前回マージしてから暫くは待機する
			val remain = last_show_stream_data + 333L - now
			if(remain > 0) {
				App1.getAppState(context).handler.postDelayed(this, remain)
				return
			}
			last_show_stream_data = now
			
			val list_new = duplicate_map.filterDuplicate(stream_data_queue)
			stream_data_queue.clear()
			
			if(list_new.isEmpty()) return
			
			// 通知カラムならストリーミング経由で届いたデータを通知ワーカーに伝達する
			if(column_type == TYPE_NOTIFICATIONS) {
				val list = ArrayList<TootNotification>()
				for(o in list_new) {
					if(o is TootNotification) {
						list.add(o)
					}
				}
				if(! list.isEmpty()) {
					PollingWorker.injectData(context, access_info.db_id, list)
				}
			}
			
			// 最新のIDをsince_idとして覚える(ソートはしない)
			var new_id_max = Long.MIN_VALUE
			var new_id_min = Long.MAX_VALUE
			for(o in list_new) {
				try {
					val id = getId(o)
					if(id < 0) continue
					if(id > new_id_max) new_id_max = id
					if(id < new_id_min) new_id_min = id
				} catch(ex : Throwable) {
					// IDを取得できないタイプのオブジェクトだった
					// ストリームに来るのは通知かステータスだから、多分ここは通らない
					log.trace(ex)
				}
			}
			if(new_id_max != Long.MAX_VALUE) {
				since_id = new_id_max.toString()
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
						restore_y = holder.getListItemTop(restore_idx)
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
					if(list_data.size > 0 && new_id_min != Long.MAX_VALUE) {
						val since = getId(list_data[0])
						if(new_id_min > since) {
							val gap = TootGap(new_id_min, since)
							list_new.add(gap)
						}
					}
				} catch(ex : Throwable) {
					log.e(ex, "can't put gap.")
				}
				
			}
			
			for(o in list_new) {
				if(o is TootStatus) {
					val highlight_sound = o.highlight_sound
					if(highlight_sound != null) {
						App1.sound(highlight_sound)
						break
					}
				}
			}
			
			val added = list_new.size
			val changeList = listOf(AdapterChange(AdapterChangeType.RangeInsert, 0, added))
			list_data.addAll(0, list_new)
			fireShowContent(reason = "mergeStreamingMessage", changeList = changeList)
			
			if(holder != null) {
				if(holder_sp == null) {
					// スクロール位置が先頭なら先頭にする
					log.d("mergeStreamingMessage: has VH. missing scroll position.")
					viewHolder?.scrollToTop()
					
				} else if(holder_sp.adapterIndex == 0 && holder_sp.offset == 0) {
					// スクロール位置が先頭なら先頭にする
					log.d(
						"mergeStreamingMessage: has VH. keep head. offset=%s,offset=%s"
						, holder_sp.adapterIndex
						, holder_sp.offset
					)
					holder.setScrollPosition(ScrollPosition(0, 0))
				} else if(restore_idx < - 1) {
					// 可視範囲の検出に失敗
					log.d("mergeStreamingMessage: has VH. can't find visible range.")
				} else {
					// 現在の要素が表示され続けるようにしたい
					log.d("mergeStreamingMessage: has VH. added=$added")
					holder.setListItemTop(restore_idx + added, restore_y)
				}
			} else {
				val scroll_save = this@Column.scroll_save
				if(scroll_save == null || (scroll_save.adapterIndex == 0 && scroll_save.offset == 0)) {
					// スクロール位置が先頭なら先頭のまま
				} else {
					// 現在の要素が表示され続けるようにしたい
					scroll_save.adapterIndex += added
				}
			}
		}
	}
	
	private fun makeNotificationUrl():String{
		return if(!dont_show_favourite && !dont_show_boost && !dont_show_follow && !dont_show_reply){
			PATH_NOTIFICATIONS
		}else {
			val sb = StringBuilder(PATH_NOTIFICATIONS) // always contain "?limit=XX"
			if(dont_show_favourite) sb.append("&exclude_types[]=favourite")
			if(dont_show_boost) sb.append("&exclude_types[]=reblog")
			if(dont_show_follow) sb.append("&exclude_types[]=follow")
			if(dont_show_reply) sb.append("&exclude_types[]=mention")
			sb.toString()
		}
	}
}
