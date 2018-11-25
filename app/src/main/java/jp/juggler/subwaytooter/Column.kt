package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.AsyncTask
import android.os.Environment
import android.os.SystemClock
import android.support.v4.view.ViewCompat
import android.support.v7.app.AppCompatActivity
import android.view.Gravity
import android.view.View
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.table.*
import jp.juggler.subwaytooter.util.*
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
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern
import kotlin.collections.ArrayList

enum class StreamingIndicatorState {
	NONE,
	REGISTERED, // registered, but not listening
	LISTENING,
}

enum class ColumnTaskType {
	LOADING,
	REFRESH_TOP,
	REFRESH_BOTTOM,
	GAP
}

abstract class ColumnTask(
	val ctType : ColumnTaskType
) : AsyncTask<Void, Void, TootApiResult?>() {
	
	val ctStarted = AtomicBoolean(false)
	val ctClosed = AtomicBoolean(false)
}

class Column(
	val app_state : AppState,
	val context : Context,
	val access_info : SavedAccount,
	val column_type : Int,
	val column_id : String
) {
	
	companion object {
		private val log = LogCategory("Column")
		
		private const val DIR_BACKGROUND_IMAGE = "columnBackground"
		
		private const val READ_LIMIT = 80 // API側の上限が80です。ただし指定しても40しか返ってこないことが多い
		private const val LOOP_TIMEOUT = 10000L
		private const val LOOP_READ_ENOUGH = 30 // フィルタ後のデータ数がコレ以上ならループを諦めます
		private const val RELATIONSHIP_LOAD_STEP = 40
		private const val ACCT_DB_STEP = 100
		
		private const val MISSKEY_HASHTAG_LIMIT = 30
		
		// ステータスのリストを返すAPI
		private const val PATH_HOME = "/api/v1/timelines/home?limit=$READ_LIMIT"
		private const val PATH_DIRECT_MESSAGES = "/api/v1/timelines/direct?limit=$READ_LIMIT"
		private const val PATH_DIRECT_MESSAGES2 = "/api/v1/conversations?limit=$READ_LIMIT"
		
		private const val PATH_LOCAL = "/api/v1/timelines/public?limit=$READ_LIMIT&local=true"
		private const val PATH_TL_FEDERATE = "/api/v1/timelines/public?limit=$READ_LIMIT"
		private const val PATH_FAVOURITES = "/api/v1/favourites?limit=$READ_LIMIT"
		private const val PATH_ACCOUNT_STATUSES =
			"/api/v1/accounts/%s/statuses?limit=$READ_LIMIT" // 1:account_id
		private const val PATH_LIST_TL = "/api/v1/timelines/list/%s?limit=$READ_LIMIT"
		
		// アカウントのリストを返すAPI
		private const val PATH_ACCOUNT_FOLLOWING =
			"/api/v1/accounts/%s/following?limit=$READ_LIMIT" // 1:account_id
		private const val PATH_ACCOUNT_FOLLOWERS =
			"/api/v1/accounts/%s/followers?limit=$READ_LIMIT" // 1:account_id
		private const val PATH_MUTES = "/api/v1/mutes?limit=$READ_LIMIT"
		private const val PATH_BLOCKS = "/api/v1/blocks?limit=$READ_LIMIT"
		private const val PATH_FOLLOW_REQUESTS = "/api/v1/follow_requests?limit=$READ_LIMIT"
		private const val PATH_FOLLOW_SUGGESTION = "/api/v1/suggestions?limit=$READ_LIMIT"
		private const val PATH_ENDORSEMENT = "/api/v1/endorsements?limit=$READ_LIMIT"
		
		private const val PATH_BOOSTED_BY =
			"/api/v1/statuses/%s/reblogged_by?limit=$READ_LIMIT" // 1:status_id
		private const val PATH_FAVOURITED_BY =
			"/api/v1/statuses/%s/favourited_by?limit=$READ_LIMIT" // 1:status_id
		private const val PATH_LIST_MEMBER = "/api/v1/lists/%s/accounts?limit=$READ_LIMIT"
		
		// 他のリストを返すAPI
		private const val PATH_REPORTS = "/api/v1/reports?limit=$READ_LIMIT"
		private const val PATH_NOTIFICATIONS = "/api/v1/notifications?limit=$READ_LIMIT"
		private const val PATH_DOMAIN_BLOCK = "/api/v1/domain_blocks?limit=$READ_LIMIT"
		private const val PATH_LIST_LIST = "/api/v1/lists?limit=$READ_LIMIT"
		
		// リストではなくオブジェクトを返すAPI
		private const val PATH_ACCOUNT = "/api/v1/accounts/%s" // 1:account_id
		private const val PATH_STATUSES = "/api/v1/statuses/%s" // 1:status_id
		private const val PATH_STATUSES_CONTEXT = "/api/v1/statuses/%s/context" // 1:status_id
		const val PATH_SEARCH = "/api/v1/search?q=%s"
		const val PATH_SEARCH_V2 = "/api/v2/search?q=%s"
		// search args 1: query(urlencoded) , also, append "&resolve=1" if resolve non-local accounts
		private const val PATH_INSTANCE = "/api/v1/instance"
		private const val PATH_LIST_INFO = "/api/v1/lists/%s"
		
		const val PATH_FILTERS = "/api/v1/filters"
		
		const val PATH_MISSKEY_PROFILE_FOLLOWING = "/api/users/following"
		const val PATH_MISSKEY_PROFILE_FOLLOWERS = "/api/users/followers"
		const val PATH_MISSKEY_PROFILE_STATUSES = "/api/users/notes"
		
		const val PATH_MISSKEY_PROFILE = "/api/users/show"
		const val PATH_MISSKEY_MUTES = "/api/mute/list"
		const val PATH_MISSKEY_FOLLOW_REQUESTS = "/api/following/requests/list"
		const val PATH_MISSKEY_FOLLOW_SUGGESTION = "/api/users/recommendation"
		const val PATH_MISSKEY_FAVORITES = "/api/i/favorites"
		
		private enum class PagingType {
			Default,
			Cursor,
			Offset,
			None,
		}
		
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
		internal const val TYPE_DIRECT_MESSAGES = 23
		internal const val TYPE_TREND_TAG = 24
		internal const val TYPE_FOLLOW_SUGGESTION = 25
		internal const val TYPE_KEYWORD_FILTER = 26
		internal const val TYPE_MISSKEY_HYBRID = 27
		internal const val TYPE_ENDORSEMENT = 28
		internal const val TYPE_LOCAL_AROUND = 29
		internal const val TYPE_FEDERATED_AROUND = 30
		internal const val TYPE_ACCOUNT_AROUND = 31
		
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
				
				TYPE_LOCAL_AROUND -> context.getString(R.string.ltl_around)
				TYPE_FEDERATED_AROUND -> context.getString(R.string.ftl_around)
				TYPE_ACCOUNT_AROUND -> context.getString(R.string.account_tl_around)
				
				TYPE_LOCAL -> context.getString(R.string.local_timeline)
				TYPE_FEDERATE -> context.getString(R.string.federate_timeline)
				
				TYPE_MISSKEY_HYBRID -> context.getString(R.string.misskey_hybrid_timeline)
				
				TYPE_PROFILE -> context.getString(R.string.profile)
				TYPE_FAVOURITES -> context.getString(R.string.favourites)
				TYPE_REPORTS -> context.getString(R.string.reports)
				TYPE_NOTIFICATIONS -> context.getString(R.string.notifications)
				TYPE_CONVERSATION -> context.getString(R.string.conversation)
				TYPE_BOOSTED_BY -> context.getString(R.string.boosted_by)
				TYPE_FAVOURITED_BY -> context.getString(R.string.favourited_by)
				TYPE_HASHTAG -> context.getString(R.string.hashtag)
				TYPE_MUTES -> context.getString(R.string.muted_users)
				TYPE_KEYWORD_FILTER -> context.getString(R.string.keyword_filters)
				TYPE_BLOCKS -> context.getString(R.string.blocked_users)
				TYPE_DOMAIN_BLOCKS -> context.getString(R.string.blocked_domains)
				TYPE_SEARCH -> context.getString(R.string.search)
				TYPE_SEARCH_MSP -> context.getString(R.string.toot_search_msp)
				TYPE_SEARCH_TS -> context.getString(R.string.toot_search_ts)
				TYPE_INSTANCE_INFORMATION -> context.getString(R.string.instance_information)
				TYPE_FOLLOW_REQUESTS -> context.getString(R.string.follow_requests)
				TYPE_FOLLOW_SUGGESTION -> context.getString(R.string.follow_suggestion)
				TYPE_ENDORSEMENT -> context.getString(R.string.endorse_set)
				
				TYPE_LIST_LIST -> context.getString(R.string.lists)
				TYPE_LIST_MEMBER -> context.getString(R.string.list_member)
				TYPE_LIST_TL -> context.getString(R.string.list_timeline)
				TYPE_DIRECT_MESSAGES -> context.getString(R.string.direct_messages)
				TYPE_TREND_TAG -> context.getString(R.string.trend_tag)
				else -> "?"
			}
		}
		
		internal fun getIconAttrId(acct : String, type : Int) : Int {
			return when(type) {
				TYPE_REPORTS -> R.attr.ic_info
				TYPE_HOME -> R.attr.btn_home
				
				TYPE_LOCAL_AROUND -> R.attr.btn_local_tl
				TYPE_FEDERATED_AROUND -> R.attr.btn_federate_tl
				TYPE_ACCOUNT_AROUND -> R.attr.btn_statuses
				
				TYPE_LOCAL -> R.attr.btn_local_tl
				TYPE_FEDERATE -> R.attr.btn_federate_tl
				TYPE_MISSKEY_HYBRID -> R.attr.ic_share
				
				TYPE_PROFILE -> R.attr.btn_statuses
				TYPE_FAVOURITES -> if(SavedAccount.isNicoru(acct)) R.attr.ic_nicoru else R.attr.btn_favourite
				TYPE_NOTIFICATIONS -> R.attr.btn_notification
				TYPE_CONVERSATION -> R.attr.ic_conversation
				TYPE_BOOSTED_BY -> R.attr.btn_boost
				TYPE_FAVOURITED_BY -> if(SavedAccount.isNicoru(acct)) R.attr.ic_nicoru else R.attr.btn_favourite
				TYPE_HASHTAG -> R.attr.ic_hashtag
				TYPE_MUTES -> R.attr.ic_mute
				TYPE_KEYWORD_FILTER -> R.attr.ic_mute
				TYPE_BLOCKS -> R.attr.ic_block
				TYPE_DOMAIN_BLOCKS -> R.attr.ic_domain_block
				TYPE_SEARCH, TYPE_SEARCH_MSP, TYPE_SEARCH_TS -> R.attr.ic_search
				TYPE_INSTANCE_INFORMATION -> R.attr.ic_info
				TYPE_FOLLOW_REQUESTS -> R.attr.ic_follow_wait
				TYPE_FOLLOW_SUGGESTION -> R.attr.ic_follow_plus
				TYPE_ENDORSEMENT -> R.attr.ic_follow_plus
				TYPE_LIST_LIST -> R.attr.ic_list_list
				TYPE_LIST_MEMBER -> R.attr.ic_list_member
				TYPE_LIST_TL -> R.attr.ic_list_tl
				TYPE_DIRECT_MESSAGES -> R.attr.ic_mail
				TYPE_TREND_TAG -> R.attr.ic_hashtag
				else -> R.attr.ic_info
			}
		}
		
		internal val reMaxId = Pattern.compile("[&?]max_id=(\\d+)") // より古いデータの取得に使う
		
		private val reMinId = Pattern.compile("[&?]min_id=(\\d+)") // より新しいデータの取得に使う (マストドン2.6.0以降)
		
		private val reSinceId =
			Pattern.compile("[&?]since_id=(\\d+)") // より新しいデータの取得に使う(マストドン2.6.0未満)
		
		val COLUMN_REGEX_FILTER_DEFAULT = { _ : CharSequence? -> false }
		
		private val time_format_hhmm = SimpleDateFormat("HH:mm", Locale.getDefault())
		
		private fun getResetTimeString() : String {
			time_format_hhmm.timeZone = TimeZone.getDefault()
			return time_format_hhmm.format(Date(0L))
		}
		
		fun onFiltersChanged(context : Context, access_info : SavedAccount) {
			
			TootTaskRunner(context, progress_style = TootTaskRunner.PROGRESS_NONE).run(access_info,
				object : TootTask {
					
					var filter_list : ArrayList<TootFilter>? = null
					
					override fun background(client : TootApiClient) : TootApiResult? {
						val result = client.request(Column.PATH_FILTERS)
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
		
		private val misskeyArrayFinderUsers = { it : JSONObject -> it.optJSONArray("users") }
		
		private val misskeyCustomParserFollowRequest =
			{ parser : TootParser, jsonArray : JSONArray ->
				val dst = ArrayList<TootAccountRef>()
				for(i in 0 until jsonArray.length()) {
					val src = jsonArray.optJSONObject(i) ?: continue
					
					val accountRef = TootAccountRef.mayNull(
						parser,
						parser.account(src.optJSONObject("follower"))
					) ?: continue
					
					val requestId = EntityId.mayNull(src.parseString("id")) ?: continue
					
					accountRef.get()._orderId = requestId
					
					dst.add(accountRef)
				}
				dst
			}
		
		private val misskeyCustomParserBlocks =
			{ parser : TootParser, jsonArray : JSONArray ->
				val dst = ArrayList<TootAccountRef>()
				for(i in 0 until jsonArray.length()) {
					val src = jsonArray.optJSONObject(i) ?: continue
					
					val accountRef = TootAccountRef.mayNull(
						parser,
						parser.account(src.optJSONObject("blockee"))
					) ?: continue
					
					val requestId = EntityId.mayNull(src.parseString("id")) ?: continue
					
					accountRef.get()._orderId = requestId
					
					dst.add(accountRef)
				}
				dst
			}
		private val misskeyCustomParserFavorites =
			{ parser : TootParser, jsonArray : JSONArray ->
				val dst = ArrayList<TootStatus>()
				for(i in 0 until jsonArray.length()) {
					val src = jsonArray.optJSONObject(i) ?: continue
					val note = parser.status(src.optJSONObject("note")) ?: continue
					val favId = EntityId.mayNull(src.parseString("id")) ?: continue
					note.favourited = true
					note._orderId = favId
					dst.add(note)
				}
				dst
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
			val backgroundDir = context.getDir(Column.DIR_BACKGROUND_IMAGE, Context.MODE_PRIVATE)
			log.i("backgroundDir: ${backgroundDir} exists=${backgroundDir.exists()}")
			return backgroundDir
		}
		
		private var defaultColorHeaderBg = 0
		private var defaultColorHeaderName = 0
		private var defaultColorHeaderPageNumber = 0
		private var defaultColorContentBg = 0
		private var defaultColorContentAcct = 0
		private var defaultColorContentText = 0
		
		fun reloadDefaultColor(activity : AppCompatActivity, pref : SharedPreferences) {
			var c : Int
			
			//
			c = Pref.ipCcdHeaderBg(pref)
			if(c == 0) c = Styler.getAttributeColor(activity, R.attr.color_column_header)
			defaultColorHeaderBg = c
			//
			c = Pref.ipCcdHeaderFg(pref)
			if(c == 0) c = Styler.getAttributeColor(activity, R.attr.colorColumnHeaderName)
			defaultColorHeaderName = c
			//
			c = Pref.ipCcdHeaderFg(pref)
			if(c == 0) c = Styler.getAttributeColor(activity, R.attr.colorColumnHeaderPageNumber)
			defaultColorHeaderPageNumber = c
			//
			c = Pref.ipCcdContentBg(pref)
			defaultColorContentBg = c
			//
			c = Pref.ipCcdContentAcct(pref)
			if(c == 0) c = Styler.getAttributeColor(activity, R.attr.colorTimeSmall)
			defaultColorContentAcct = c
			//
			c = Pref.ipCcdContentText(pref)
			if(c == 0) c = Styler.getAttributeColor(activity, R.attr.colorContentText)
			defaultColorContentText = c
			
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
		} else {
			when(column_type) {
				TYPE_HOME, TYPE_NOTIFICATIONS -> "/api/v1/streaming/?stream=user"
				TYPE_LOCAL -> "/api/v1/streaming/?stream=public:local"
				TYPE_FEDERATE -> "/api/v1/streaming/?stream=public"
				TYPE_LIST_TL -> "/api/v1/streaming/?stream=list&list=$profile_id"
				
				TYPE_DIRECT_MESSAGES -> "/api/v1/streaming/?stream=direct"
				
				TYPE_HASHTAG -> when(instance_local) {
					true -> "/api/v1/streaming/?stream=" + Uri.encode("hashtag:local") + "&tag=" + hashtag.encodePercent()
					else -> "/api/v1/streaming/?stream=hashtag&tag=" + hashtag.encodePercent()
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
	internal var dont_show_reaction : Boolean = false
	internal var dont_show_vote : Boolean = false
	
	internal var dont_show_normal_toot : Boolean = false
	internal var dont_show_favourite : Boolean = false // 通知カラムのみ
	internal var dont_show_follow : Boolean = false // 通知カラムのみ
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
	
	private var status_id : EntityId? = null
	
	// プロフカラムではアカウントのID。リストカラムではリストのID
	internal var profile_id : EntityId? = null
	
	internal var search_query : String = ""
	internal var search_resolve : Boolean = false
	private var hashtag : String = ""
	internal var instance_uri : String = ""
	
	// プロフカラムでのアカウント情報
	@Volatile
	internal var who_account : TootAccountRef? = null
	
	// リストカラムでのリスト情報
	@Volatile
	private var list_info : TootList? = null
	
	// 「インスタンス情報」カラムに表示するインスタンス情報
	// (SavedAccount中のインスタンス情報とは異なるので注意)
	internal var instance_information : TootInstance? = null
	
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
	private val duplicate_map = DuplicateMap()
	
	private val isFilterEnabled : Boolean
		get() = (with_attachment
			|| with_highlight
			|| dont_show_boost
			|| dont_show_favourite
			|| dont_show_follow
			|| dont_show_reply
			|| dont_show_reaction
			|| dont_show_vote
			|| dont_show_normal_toot
			|| regex_text.isNotEmpty()
			)
	
	@Volatile
	private var column_regex_filter = COLUMN_REGEX_FILTER_DEFAULT
	
	@Volatile
	private var muted_word2 : WordTrieTree? = null
	
	@Volatile
	private var favMuteSet : HashSet<String>? = null
	
	@Volatile
	private var highlight_trie : WordTrieTree? = null
	
	// タイムライン中のデータの始端と終端
	// misskeyは
	private var idRecent : EntityId? = null
	private var idOld : EntityId? = null
	private var offsetNext : Int = 0
	private var pagingType : PagingType = PagingType.Default
	
	var bRefreshingTop : Boolean = false
	
	// ListViewの表示更新が追いつかないとスクロール位置が崩れるので
	// 一定時間より短期間にはデータ更新しないようにする
	private val last_show_stream_data = AtomicLong(0L)
	private val stream_data_queue = ConcurrentLinkedQueue<TimelineItem>()
	
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
			
			TYPE_CONVERSATION, TYPE_BOOSTED_BY, TYPE_FAVOURITED_BY, TYPE_LOCAL_AROUND, TYPE_FEDERATED_AROUND, TYPE_ACCOUNT_AROUND -> status_id =
				when(isMisskey) {
					true -> EntityId.mayNull(src.parseString(KEY_STATUS_ID))
					else -> EntityId.mayNull(src.parseLong(KEY_STATUS_ID))
				}
			
			TYPE_PROFILE -> {
				profile_id = when(isMisskey) {
					true -> EntityId.mayNull(src.parseString(KEY_PROFILE_ID))
					else -> EntityId.mayNull(src.parseLong(KEY_PROFILE_ID))
				}
				profile_tab = src.optInt(KEY_PROFILE_TAB)
			}
			
			TYPE_LIST_MEMBER, TYPE_LIST_TL -> {
				profile_id = when(isMisskey) {
					true -> EntityId.mayNull(src.parseString(KEY_PROFILE_ID))
					else -> EntityId.mayNull(src.parseLong(KEY_PROFILE_ID))
				}
			}
			
			TYPE_HASHTAG -> hashtag = src.optString(KEY_HASHTAG)
			
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
				
				TYPE_PROFILE, TYPE_LIST_TL, TYPE_LIST_MEMBER ->
					profile_id == when(isMisskey) {
						true -> EntityIdString(getParamAt(params, 0))
						else -> EntityIdLong(getParamAt(params, 0))
					}
				
				TYPE_CONVERSATION, TYPE_BOOSTED_BY, TYPE_FAVOURITED_BY, TYPE_LOCAL_AROUND, TYPE_FEDERATED_AROUND, TYPE_ACCOUNT_AROUND ->
					status_id == when(isMisskey) {
						true -> EntityIdString(getParamAt(params, 0))
						else -> EntityIdLong(getParamAt(params, 0))
					}
				
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
			
			TYPE_PROFILE -> {
				val who = who_account?.get()
				context.getString(
					R.string.profile_of,
					if(who != null)
						AcctColor.getNickname(access_info.getFullAcct(who))
					else
						profile_id.toString()
				)
			}
			
			TYPE_LIST_MEMBER -> context.getString(
				R.string.list_member_of,
				list_info?.title ?: profile_id.toString()
			)
			
			TYPE_LIST_TL -> context.getString(
				R.string.list_tl_of,
				list_info?.title ?: profile_id.toString()
			)
			
			TYPE_CONVERSATION -> context.getString(
				R.string.conversation_around,
				(status_id?.toString() ?: "null")
			)
			
			TYPE_LOCAL_AROUND -> context.getString(
				R.string.ltl_around_of,
				(status_id?.toString() ?: "null")
			)
			
			TYPE_FEDERATED_AROUND -> context.getString(
				R.string.ftl_around_of,
				(status_id?.toString() ?: "null")
			)
			
			TYPE_ACCOUNT_AROUND -> context.getString(
				R.string.account_tl_around_of,
				(status_id?.toString() ?: "null")
			)
			
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
		val sb = StringBuilder()
		sb.append("(")
		
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
		if(isMisskey && ! dont_show_vote) {
			if(n ++ > 0) sb.append(", ")
			sb.append(context.getString(R.string.notification_type_vote))
		}
		val n_max = if(isMisskey) {
			6
		} else {
			4
		}
		if(n == 0 || n == n_max) return "" // 全部か皆無なら部分表記は要らない
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
	
	internal fun getIconAttrId(type : Int) : Int {
		return getIconAttrId(access_info.acct, type)
	}
	
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
		
		mRefreshLoadingErrorPopupState =0
		mRefreshLoadingError = ""
		bRefreshLoading = false
		mInitialLoadingError = ""
		bInitialLoading = false
		idOld = null
		idRecent = null
		offsetNext = 0
		pagingType = PagingType.Default
		
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
		if(column_type != TYPE_NOTIFICATIONS) return
		
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
	
	private fun isFiltered(status : TootStatus) : Boolean {
		
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
	
	private inline fun <reified T : TimelineItem> addAll(
		dstArg : ArrayList<TimelineItem>?,
		src : List<T>
	) : ArrayList<TimelineItem> {
		val dst = dstArg ?: ArrayList(src.size)
		dst.addAll(src)
		return dst
	}
	
	private fun addOne(
		dstArg : ArrayList<TimelineItem>?,
		item : TimelineItem?
	) : ArrayList<TimelineItem> {
		val dst = dstArg ?: ArrayList()
		if(item != null) dst.add(item)
		return dst
	}
	
	private fun addOneFirst(
		dstArg : ArrayList<TimelineItem>?,
		item : TimelineItem?
	) : ArrayList<TimelineItem> {
		val dst = dstArg ?: ArrayList()
		if(item != null) dst.add(0, item)
		return dst
	}
	
	private fun addWithFilterStatus(
		dstArg : ArrayList<TimelineItem>?,
		src : List<TootStatus>
	) : ArrayList<TimelineItem> {
		val dst = dstArg ?: ArrayList(src.size)
		for(status in src) {
			if(! isFiltered(status)) {
				dst.add(status)
			}
		}
		return dst
	}
	
	private fun addWithFilterConversationSummary(
		dstArg : ArrayList<TimelineItem>?,
		src : List<TootConversationSummary>
	) : ArrayList<TimelineItem> {
		val dst = dstArg ?: ArrayList(src.size)
		for(cs in src) {
			if(! isFiltered(cs.last_status)) {
				dst.add(cs)
			}
		}
		return dst
	}
	
	private fun addWithFilterNotification(
		dstArg : ArrayList<TimelineItem>?,
		src : List<TootNotification>
	) : ArrayList<TimelineItem> {
		val dst = dstArg ?: ArrayList(src.size)
		for(item in src) {
			if(! isFiltered(item)) dst.add(item)
		}
		return dst
	}
	
	private fun isFiltered(item : TootNotification) : Boolean {
		
		when(item.type) {
			TootNotification.TYPE_FAVOURITE -> if(dont_show_favourite) {
				log.d("isFiltered: favourite notification filtered.")
				return true
			}
			TootNotification.TYPE_REBLOG,
			TootNotification.TYPE_RENOTE,
			TootNotification.TYPE_QUOTE -> if(dont_show_boost) {
				log.d("isFiltered: reblog notification filtered.")
				return true
			}
			TootNotification.TYPE_FOLLOW -> if(dont_show_follow) {
				log.d("isFiltered: follow notification filtered.")
				return true
			}
			TootNotification.TYPE_MENTION,
			TootNotification.TYPE_REPLY -> if(dont_show_reply) {
				log.d("isFiltered: mention notification filtered.")
				return true
			}
			TootNotification.TYPE_REACTION -> if(dont_show_reaction) {
				log.d("isFiltered: reaction notification filtered.")
				return true
			}
			TootNotification.TYPE_VOTE -> if(dont_show_vote) {
				log.d("isFiltered: vote notification filtered.")
				return true
			}
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
						UserRelationMisskey.saveList(now, access_info.db_id, who_list, start, step)
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
							UserRelationMisskey.saveList2(now, access_info.db_id, list)
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
						if(list.size > 0) UserRelation.saveList(now, access_info.db_id, list)
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
	private fun updateRelation(
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
		
		mRefreshLoadingErrorPopupState =0
		mRefreshLoadingError = ""
		mInitialLoadingError = ""
		bFirstInitialized = true
		bInitialLoading = true
		bRefreshLoading = false
		idOld = null
		idRecent = null
		offsetNext = 0
		pagingType = PagingType.Default
		
		duplicate_map.clear()
		list_data.clear()
		fireShowContent(reason = "loading start", reset = true)
		
		val task = @SuppressLint("StaticFieldLeak")
		object : ColumnTask(ColumnTaskType.LOADING) {
			var parser = TootParser(context, access_info, highlightTrie = highlight_trie)
			
			var instance_tmp : TootInstance? = null
			
			var list_pinned : ArrayList<TimelineItem>? = null
			
			var list_tmp : ArrayList<TimelineItem>? = null
			
			fun getInstanceInformation(
				client : TootApiClient,
				instance_name : String?
			) : TootApiResult? {
				if(instance_name != null) {
					// 「インスタンス情報」カラムをNAアカウントで開く場合
					client.instance = instance_name
				} else {
					// カラムに紐付けられたアカウントのタンスのインスタンス情報
				}
				val result = client.parseInstanceInformation(client.getInstanceInformation())
				instance_tmp = result?.data as? TootInstance
				return result
			}
			
			fun getStatusesPinned(client : TootApiClient, path_base : String) {
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
			
			fun getStatuses(
				client : TootApiClient,
				path_base : String,
				aroundMin : Boolean = false,
				aroundMax : Boolean = false,
				
				misskeyParams : JSONObject? = null,
				misskeyCustomParser : (parser : TootParser, jsonArray : JSONArray) -> ArrayList<TootStatus> =
					{ parser, jsonArray -> parser.statusList(jsonArray) },
				initialUntilDate : Boolean = false
			) : TootApiResult? {
				
				val params = misskeyParams ?: makeMisskeyTimelineParameter(parser)
				
				val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
				
				val time_start = SystemClock.elapsedRealtime()
				
				// 初回の取得
				val result = when {
					isMisskey -> {
						if(initialUntilDate) {
							params.put("untilDate", System.currentTimeMillis() + (86400000L * 365))
						}
						client.request(path_base, params.toPostRequestBuilder())
					}
					
					aroundMin -> client.request("$path_base&min_id=$status_id")
					aroundMax -> client.request("$path_base&max_id=$status_id")
					else -> client.request(path_base)
				}
				
				var jsonArray = result?.jsonArray
				if(jsonArray != null) {
					
					var src = misskeyCustomParser(parser, jsonArray)
					
					if(list_tmp == null) {
						list_tmp = ArrayList(src.size)
					}
					this.list_tmp = addWithFilterStatus(list_tmp, src)
					
					saveRange(true, true, result, src)
					
					if(aroundMin) {
						while(true) {
							if(client.isApiCancelled) {
								log.d("loading-statuses: cancelled.")
								break
							}
							
							if(! isFilterEnabled) {
								log.d("loading-statuses: isFiltered is false.")
								break
							}
							
							if(idRecent == null) {
								log.d("loading-statuses: idRecent is empty.")
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
							
							// フィルタなどが有効な場合は2回目以降の取得
							val result2 = if(isMisskey) {
								idOld?.putMisskeyUntil(params)
								client.request(path_base, params.toPostRequestBuilder())
							} else {
								val path = "$path_base${delimiter}min_id=${idRecent}"
								client.request(path)
							}
							
							jsonArray = result2?.jsonArray
							
							if(jsonArray == null) {
								log.d("loading-statuses: error or cancelled.")
								break
							}
							
							src = misskeyCustomParser(parser, jsonArray)
							
							addWithFilterStatus(list_tmp, src)
							
							if(! saveRangeStart(result = result2, list = src)) {
								log.d("loading-statuses: missing range info.")
								break
							}
						}
						
					} else {
						while(true) {
							
							if(client.isApiCancelled) {
								log.d("loading-statuses: cancelled.")
								break
							}
							
							if(! isFilterEnabled) {
								log.d("loading-statuses: isFiltered is false.")
								break
							}
							
							if(idOld == null) {
								log.d("loading-statuses: idOld is empty.")
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
							
							// フィルタなどが有効な場合は2回目以降の取得
							val result2 = if(isMisskey) {
								idOld?.putMisskeyUntil(params)
								client.request(path_base, params.toPostRequestBuilder())
							} else {
								val path = "$path_base${delimiter}max_id=${idOld}"
								client.request(path)
							}
							
							jsonArray = result2?.jsonArray
							
							if(jsonArray == null) {
								log.d("loading-statuses: error or cancelled.")
								break
							}
							
							src = misskeyCustomParser(parser, jsonArray)
							
							addWithFilterStatus(list_tmp, src)
							
							if(! saveRangeEnd(result = result2, list = src)) {
								log.d("loading-statuses: missing range info.")
								break
							}
						}
					}
				}
				return result
			}
			
			fun getConversationSummary(
				client : TootApiClient,
				path_base : String,
				aroundMin : Boolean = false,
				aroundMax : Boolean = false,
				misskeyParams : JSONObject? = null,
				misskeyCustomParser : (parser : TootParser, jsonArray : JSONArray) -> ArrayList<TootConversationSummary> =
					{ parser, jsonArray -> parseList(::TootConversationSummary, parser, jsonArray) }
			) : TootApiResult? {
				
				val params = misskeyParams ?: makeMisskeyTimelineParameter(parser)
				
				val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
				
				val time_start = SystemClock.elapsedRealtime()
				
				// 初回の取得
				val result = when {
					isMisskey -> client.request(path_base, params.toPostRequestBuilder())
					aroundMin -> client.request("$path_base&min_id=$status_id")
					aroundMax -> client.request("$path_base&max_id=$status_id")
					else -> client.request(path_base)
				}
				
				var jsonArray = result?.jsonArray
				if(jsonArray != null) {
					
					var src = misskeyCustomParser(parser, jsonArray !!)
					
					if(list_tmp == null) {
						list_tmp = ArrayList(src.size)
					}
					this.list_tmp = addWithFilterConversationSummary(list_tmp, src)
					
					saveRange(true, true, result, src)
					
					if(aroundMin) {
						while(true) {
							if(client.isApiCancelled) {
								log.d("loading-ConversationSummary: cancelled.")
								break
							}
							
							if(! isFilterEnabled) {
								log.d("loading-ConversationSummary: isFiltered is false.")
								break
							}
							
							if(idRecent == null) {
								log.d("loading-ConversationSummary: idRecent is empty.")
								break
							}
							
							if((list_tmp?.size ?: 0) >= LOOP_READ_ENOUGH) {
								log.d("loading-ConversationSummary: read enough data.")
								break
							}
							
							if(src.isEmpty()) {
								log.d("loading-ConversationSummary: previous response is empty.")
								break
							}
							
							if(SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
								log.d("loading-ConversationSummary: timeout.")
								break
							}
							
							// フィルタなどが有効な場合は2回目以降の取得
							val result2 = if(isMisskey) {
								idOld?.putMisskeyUntil(params)
								client.request(path_base, params.toPostRequestBuilder())
							} else {
								val path = "$path_base${delimiter}min_id=${idRecent}"
								client.request(path)
							}
							
							jsonArray = result2?.jsonArray
							
							if(jsonArray == null) {
								log.d("loading-ConversationSummary: error or cancelled.")
								break
							}
							
							src = misskeyCustomParser(parser, jsonArray !!)
							
							addWithFilterConversationSummary(list_tmp, src)
							
							if(! saveRangeStart(result = result2, list = src)) {
								log.d("loading-ConversationSummary: missing range info.")
								break
							}
						}
						
					} else {
						while(true) {
							
							if(client.isApiCancelled) {
								log.d("loading-ConversationSummary: cancelled.")
								break
							}
							
							if(! isFilterEnabled) {
								log.d("loading-ConversationSummary: isFiltered is false.")
								break
							}
							
							if(idOld == null) {
								log.d("loading-ConversationSummary: idOld is empty.")
								break
							}
							
							if((list_tmp?.size ?: 0) >= LOOP_READ_ENOUGH) {
								log.d("loading-ConversationSummary: read enough data.")
								break
							}
							
							if(src.isEmpty()) {
								log.d("loading-ConversationSummary: previous response is empty.")
								break
							}
							
							if(SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
								log.d("loading-ConversationSummary: timeout.")
								break
							}
							
							// フィルタなどが有効な場合は2回目以降の取得
							val result2 = if(isMisskey) {
								idOld?.putMisskeyUntil(params)
								client.request(path_base, params.toPostRequestBuilder())
							} else {
								val path = "$path_base${delimiter}max_id=${idOld}"
								client.request(path)
							}
							
							jsonArray = result2?.jsonArray
							
							if(jsonArray == null) {
								log.d("loading-ConversationSummary: error or cancelled.")
								break
							}
							
							src = misskeyCustomParser(parser, jsonArray !!)
							
							addWithFilterConversationSummary(list_tmp, src)
							
							if(! saveRangeEnd(result = result2, list = src)) {
								log.d("loading-ConversationSummary: missing range info.")
								break
							}
						}
					}
				}
				return result
			}
			
			fun parseAccountList(
				client : TootApiClient,
				path_base : String,
				emptyMessage : String? = null,
				misskeyParams : JSONObject? = null,
				misskeyArrayFinder : (JSONObject) -> JSONArray? = { null },
				misskeyCustomParser : (parser : TootParser, jsonArray : JSONArray) -> ArrayList<TootAccountRef> =
					{ parser, jsonArray -> parser.accountList(jsonArray) }
			) : TootApiResult? {
				
				val result = if(misskeyParams != null) {
					client.request(path_base, misskeyParams.toPostRequestBuilder())
				} else {
					client.request(path_base)
				}
				
				if(result != null && result.error == null) {
					val jsonObject = result.jsonObject
					if(jsonObject != null) {
						if(pagingType == PagingType.Cursor) {
							idOld = EntityId.mayNull(jsonObject.parseString("next"))
						}
						result.data = misskeyArrayFinder(jsonObject)
					}
					val jsonArray = result.jsonArray
						?: return result.setError("missing JSON data.")
					
					val src = misskeyCustomParser(parser, jsonArray)
					when(pagingType) {
						PagingType.Default -> {
							saveRange(true, true, result, src)
						}
						
						PagingType.Offset -> {
							offsetNext += src.size
						}
						
						else -> {
						}
					}
					
					val tmp = ArrayList<TimelineItem>()
					
					if(emptyMessage != null) {
						// フォロー/フォロワー一覧には警告の表示が必要だった
						val who = who_account?.get()
						if(! access_info.isMe(who)) {
							if(who != null && access_info.isRemoteUser(who)) tmp.add(
								TootMessageHolder(
									context.getString(R.string.follow_follower_list_may_restrict)
								)
							)
							
							if(src.isEmpty()) {
								tmp.add(TootMessageHolder(emptyMessage))
								
							}
						}
					}
					tmp.addAll(src)
					list_tmp = addAll(null, tmp)
				}
				return result
			}
			
			fun parseFilterList(
				client : TootApiClient,
				path_base : String
			) : TootApiResult? {
				val result = client.request(path_base)
				if(result != null) {
					val src = TootFilter.parseList(result.jsonArray)
					this.list_tmp = addAll(null, src)
				}
				return result
			}
			
			fun parseDomainList(
				client : TootApiClient,
				path_base : String
			) : TootApiResult? {
				val result = client.request(path_base)
				if(result != null) {
					val src = TootDomainBlock.parseList(result.jsonArray)
					saveRange(true, true, result, src)
					this.list_tmp = addAll(null, src)
				}
				return result
			}
			
			fun parseReports(client : TootApiClient, path_base : String) : TootApiResult? {
				val result = client.request(path_base)
				if(result != null) {
					val src = parseList(::TootReport, result.jsonArray)
					saveRange(true, true, result, src)
					list_tmp = addAll(null, src)
				}
				return result
			}
			
			fun parseListList(
				client : TootApiClient,
				path_base : String,
				misskeyParams : JSONObject? = null
			) : TootApiResult? {
				val result = if(misskeyParams != null) {
					client.request(path_base, misskeyParams.toPostRequestBuilder())
				} else {
					client.request(path_base)
				}
				if(result != null) {
					val src = parseList(::TootList, parser, result.jsonArray)
					src.sort()
					saveRange(true, true, result, src)
					this.list_tmp = addAll(null, src)
				}
				return result
			}
			
			fun parseNotifications(client : TootApiClient) : TootApiResult? {
				
				val params = makeMisskeyBaseParameter(parser)
				
				val path_base = makeNotificationUrl()
				
				val time_start = SystemClock.elapsedRealtime()
				val result = if(isMisskey) {
					client.request(path_base, params.toPostRequestBuilder())
				} else {
					client.request(path_base)
				}
				var jsonArray = result?.jsonArray
				if(jsonArray != null) {
					var src = parser.notificationList(jsonArray)
					saveRange(true, true, result, src)
					this.list_tmp = addWithFilterNotification(null, src)
					//
					if(! src.isEmpty()) {
						PollingWorker.injectData(context, access_info, src)
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
						if(idOld == null) {
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
						
						val result2 = if(isMisskey) {
							idOld?.putMisskeyUntil(params)
							client.request(path_base, params.toPostRequestBuilder())
						} else {
							val path = "$path_base${delimiter}max_id=$idOld"
							client.request(path)
						}
						
						jsonArray = result2?.jsonArray
						if(jsonArray == null) {
							log.d("loading-notifications: error or cancelled.")
							break
						}
						
						src = parser.notificationList(jsonArray)
						
						addWithFilterNotification(list_tmp, src)
						
						if(! saveRangeEnd(result2, src)) {
							log.d("loading-notifications: missing range info.")
							break
						}
					}
				}
				return result
			}
			
			fun getPublicAroundStatuses(client : TootApiClient, url : String) : TootApiResult? {
				// (Mastodonのみ対応)
				
				var instance = access_info.instance
				if(instance == null) {
					getInstanceInformation(client, null)
					if(instance_tmp != null) {
						instance = instance_tmp
						access_info.instance = instance
					}
				}
				
				// ステータスIDに該当するトゥート
				// タンスをまたいだりすると存在しないかもしれないが、エラーは出さない
				var result : TootApiResult? =
					client.request(String.format(Locale.JAPAN, PATH_STATUSES, status_id))
				val target_status = parser.status(result?.jsonObject)
				if(target_status != null) {
					list_tmp = addOne(list_tmp, target_status)
				}
				
				idOld = null
				idRecent = null
				
				var bInstanceTooOld = false
				if(instance?.versionGE(TootInstance.VERSION_2_6_0) == true) {
					// 指定より新しいトゥート
					result = getStatuses(client, url, aroundMin = true)
					if(result == null || result.error != null) return result
				} else {
					bInstanceTooOld = true
				}
				
				// 指定位置より古いトゥート
				result = getStatuses(client, url, aroundMax = true)
				if(result == null || result.error != null) return result
				
				list_tmp?.sortBy { it.getOrderId() }
				list_tmp?.reverse()
				if(bInstanceTooOld) {
					list_tmp?.add(
						0,
						TootMessageHolder(context.getString(R.string.around_toot_limitation_warning))
					)
				}
				
				return result
				
			}
			
			fun getAccountAroundStatuses(client : TootApiClient) : TootApiResult? {
				// (Mastodonのみ対応)
				
				var instance = access_info.instance
				if(instance == null) {
					getInstanceInformation(client, null)
					if(instance_tmp != null) {
						instance = instance_tmp
						access_info.instance = instance
					}
				}
				
				// ステータスIDに該当するトゥート
				// タンスをまたいだりすると存在しないかもしれない
				var result : TootApiResult? =
					client.request(String.format(Locale.JAPAN, PATH_STATUSES, status_id))
				val target_status = parser.status(result?.jsonObject) ?: return result
				list_tmp = addOne(list_tmp, target_status)
				
				// ↑のトゥートのアカウントのID
				profile_id = target_status.account.id
				
				var path = String.format(
					Locale.JAPAN,
					PATH_ACCOUNT_STATUSES,
					profile_id
				)
				if(with_attachment && ! with_highlight) path += "&only_media=1"
				
				idOld = null
				idRecent = null
				
				var bInstanceTooOld = false
				if(instance?.versionGE(TootInstance.VERSION_2_6_0) == true) {
					// 指定より新しいトゥート
					result = getStatuses(client, path, aroundMin = true)
					if(result == null || result?.error != null) return result
				} else {
					bInstanceTooOld = true
				}
				
				// 指定位置より古いトゥート
				result = getStatuses(client, path, aroundMax = true)
				if(result == null || result?.error != null) return result
				
				list_tmp?.sortBy { it.getOrderId() }
				list_tmp?.reverse()
				if(bInstanceTooOld) {
					list_tmp?.add(
						0,
						TootMessageHolder(context.getString(R.string.around_toot_limitation_warning))
					)
				}
				
				return result
				
			}
			
			override fun doInBackground(vararg unused : Void) : TootApiResult? {
				ctStarted.set(true)
				
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
					
					muted_word2 = encodeFilterTree(loadFilter2(client))
					
					when(column_type) {
						
						TYPE_DIRECT_MESSAGES -> {
							
							useConversationSummarys = false
							if(! use_old_api) {
								
								// try 2.6.0 new API https://github.com/tootsuite/mastodon/pull/8832
								val resultCS = getConversationSummary(client, PATH_DIRECT_MESSAGES2)
								
								when {
									// cancelled
									resultCS == null -> return null
									
									//  not error
									resultCS.error.isNullOrBlank() -> {
										useConversationSummarys = true
										return resultCS
									}
								}
							}
							
							// fallback to old api
							return getStatuses(client, PATH_DIRECT_MESSAGES)
						}
						
						TYPE_LOCAL -> return getStatuses(client, makePublicLocalUrl())
						
						TYPE_LOCAL_AROUND -> return getPublicAroundStatuses(
							client,
							makePublicLocalUrl()
						)
						
						TYPE_FEDERATED_AROUND -> return getPublicAroundStatuses(
							client,
							makePublicFederateUrl()
						)
						TYPE_ACCOUNT_AROUND -> return getAccountAroundStatuses(
							client
						
						)
						
						TYPE_MISSKEY_HYBRID -> return getStatuses(client, makeMisskeyHybridTlUrl())
						
						TYPE_FEDERATE -> return getStatuses(client, makePublicFederateUrl())
						
						TYPE_PROFILE -> {
							
							val who_result = loadProfileAccount(client, parser, true)
							if(client.isApiCancelled || who_account == null) return who_result
							
							
							when(profile_tab) {
								
								TAB_FOLLOWING -> return if(isMisskey) {
									pagingType = PagingType.Cursor
									parseAccountList(
										client
										,
										PATH_MISSKEY_PROFILE_FOLLOWING
										,
										emptyMessage = context.getString(R.string.none_or_hidden_following)
										,
										misskeyParams = makeMisskeyParamsUserId(parser)
										,
										misskeyArrayFinder = misskeyArrayFinderUsers
									)
								} else {
									parseAccountList(
										client,
										String.format(
											Locale.JAPAN,
											PATH_ACCOUNT_FOLLOWING,
											profile_id
										),
										emptyMessage = context.getString(R.string.none_or_hidden_following)
									)
								}
								
								TAB_FOLLOWERS -> return if(isMisskey) {
									pagingType = PagingType.Cursor
									parseAccountList(
										client,
										PATH_MISSKEY_PROFILE_FOLLOWERS,
										emptyMessage = context.getString(R.string.none_or_hidden_followers),
										misskeyParams = makeMisskeyParamsUserId(parser),
										misskeyArrayFinder = misskeyArrayFinderUsers
									)
								} else {
									parseAccountList(
										client,
										String.format(
											Locale.JAPAN,
											PATH_ACCOUNT_FOLLOWERS,
											profile_id
										),
										emptyMessage = context.getString(R.string.none_or_hidden_followers)
									)
								}
								
								else -> {
									
									var instance = access_info.instance
									
									return if(! isMisskey) {
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
										
										var path = String.format(
											Locale.JAPAN,
											PATH_ACCOUNT_STATUSES,
											profile_id
										)
										if(with_attachment && ! with_highlight) path += "&only_media=1"
										
										if(instance?.versionGE(TootInstance.VERSION_1_6) == true
										// 将来的に正しく判定できる見込みがないので、Pleroma条件でのフィルタは行わない
										// && instance.instanceType != TootInstance.InstanceType.Pleroma
										) {
											getStatusesPinned(client, "$path&pinned=true")
										}
										getStatuses(client, path)
									} else {
										// 固定トゥートの取得
										val pinnedNotes = who_account?.get()?.pinnedNotes
										if(pinnedNotes != null) {
											this.list_pinned =
												addWithFilterStatus(null, pinnedNotes)
										}
										
										// 通常トゥートの取得
										getStatuses(
											client,
											PATH_MISSKEY_PROFILE_STATUSES,
											misskeyParams = makeMisskeyParamsProfileStatuses(parser),
											initialUntilDate = true
										)
									}
								}
							}
						}
						
						TYPE_MUTES -> return if(isMisskey) {
							pagingType = PagingType.Cursor
							parseAccountList(
								client
								, PATH_MISSKEY_MUTES
								, misskeyParams = access_info.putMisskeyApiToken(JSONObject())
								, misskeyArrayFinder = misskeyArrayFinderUsers
							)
						} else {
							parseAccountList(client, PATH_MUTES)
						}
						TYPE_KEYWORD_FILTER -> return parseFilterList(client, PATH_FILTERS)
						
						TYPE_BLOCKS -> return if(isMisskey) {
							pagingType = PagingType.Default
							val params = access_info.putMisskeyApiToken(JSONObject())
							parseAccountList(
								client,
								"/api/blocking/list",
								misskeyParams = params,
								misskeyCustomParser = misskeyCustomParserBlocks
							)
						} else {
							parseAccountList(client, PATH_BLOCKS)
						}
						
						TYPE_DOMAIN_BLOCKS -> return parseDomainList(client, PATH_DOMAIN_BLOCK)
						
						TYPE_LIST_LIST -> return if(isMisskey) {
							val params = makeMisskeyBaseParameter(parser)
							parseListList(
								client,
								"/api/users/lists/list",
								misskeyParams = params
							)
						} else {
							parseListList(client, PATH_LIST_LIST)
							
						}
						
						TYPE_LIST_TL -> {
							loadListInfo(client, true)
							return if(isMisskey) {
								val params = makeMisskeyTimelineParameter(parser)
									.put("listId", profile_id)
								getStatuses(client, makeListTlUrl(), misskeyParams = params)
							} else {
								getStatuses(client, makeListTlUrl())
							}
						}
						
						TYPE_LIST_MEMBER -> {
							loadListInfo(client, true)
							return if(isMisskey) {
								pagingType = PagingType.None
								val params = access_info.putMisskeyApiToken(JSONObject())
									.put("userIds", JSONArray().apply {
										list_info?.userIds?.forEach {
											this.put(it.toString())
										}
									})
								parseAccountList(
									client,
									"/api/users/show",
									misskeyParams = params
								)
								
							} else {
								parseAccountList(
									client,
									String.format(Locale.JAPAN, PATH_LIST_MEMBER, profile_id)
								)
							}
						}
						
						TYPE_FOLLOW_REQUESTS -> return if(isMisskey) {
							pagingType = PagingType.None
							parseAccountList(
								client
								, PATH_MISSKEY_FOLLOW_REQUESTS
								, misskeyParams = access_info.putMisskeyApiToken(JSONObject())
								, misskeyCustomParser = misskeyCustomParserFollowRequest
							)
						} else {
							parseAccountList(
								client,
								PATH_FOLLOW_REQUESTS
							)
						}
						
						TYPE_FOLLOW_SUGGESTION -> return if(isMisskey) {
							pagingType = PagingType.Offset
							parseAccountList(
								client
								, PATH_MISSKEY_FOLLOW_SUGGESTION
								, misskeyParams = access_info.putMisskeyApiToken(JSONObject())
							)
						} else {
							parseAccountList(
								client,
								PATH_FOLLOW_SUGGESTION
							)
						}
						
						TYPE_ENDORSEMENT -> return parseAccountList(
							client,
							PATH_ENDORSEMENT
						)
						
						TYPE_FAVOURITES -> return if(isMisskey) {
							getStatuses(
								client
								, PATH_MISSKEY_FAVORITES
								, misskeyParams = makeMisskeyTimelineParameter(parser)
								, misskeyCustomParser = misskeyCustomParserFavorites
							)
						} else {
							getStatuses(client, PATH_FAVOURITES)
						}
						
						TYPE_HASHTAG -> return if(isMisskey) {
							getStatuses(
								client
								, makeHashtagUrl(hashtag)
								, misskeyParams = makeMisskeyTimelineParameter(parser)
									.put("tag", hashtag)
									.put("limit", MISSKEY_HASHTAG_LIMIT)
							
							)
						} else {
							getStatuses(client, makeHashtagUrl(hashtag))
						}
						
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
							if(isMisskey) {
								// 指定された発言そのもの
								val queryParams = makeMisskeyBaseParameter(parser)
									.put("noteId", status_id)
								result = client.request(
									"/api/notes/show"
									, queryParams.toPostRequestBuilder()
								)
								val jsonObject = result?.jsonObject ?: return result
								val target_status = parser.status(jsonObject)
									?: return TootApiResult("TootStatus parse failed.")
								target_status.conversation_main = true
								
								// 祖先
								val list_asc = ArrayList<TootStatus>()
								while(true) {
									if(client.isApiCancelled) return null
									queryParams.put("offset", list_asc.size)
									result = client.request(
										"/api/notes/conversation"
										, queryParams.toPostRequestBuilder()
									)
									val jsonArray = result?.jsonArray ?: return result
									val src = parser.statusList(jsonArray)
									if(src.isEmpty()) break
									list_asc.addAll(src)
								}
								
								// 直接の子リプライ。(子孫をたどることまではしない)
								val list_desc = ArrayList<TootStatus>()
								while(true) {
									if(client.isApiCancelled) return null
									queryParams.put("offset", list_desc.size)
									result = client.request(
										"/api/notes/replies"
										, queryParams.toPostRequestBuilder()
									)
									val jsonArray = result?.jsonArray ?: return result
									val src = parser.statusList(jsonArray)
									if(src.isEmpty()) break
									list_desc.addAll(src)
								}
								
								// 一つのリストにまとめる
								this.list_tmp = ArrayList<TimelineItem>(
									list_asc.size + list_desc.size + 2
								).apply {
									addAll(list_asc.sortedBy { it.time_created_at })
									add(target_status)
									addAll(list_desc.sortedBy { it.time_created_at })
									add(TootMessageHolder(context.getString(R.string.misskey_cant_show_all_descendants)))
								}
								
								//
								return result
								
							} else {
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
								val conversation_context =
									parseItem(::TootContext, parser, jsonObject)
								
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
									this.list_tmp = addOne(this.list_tmp, target_status)
									this.list_tmp = addOne(
										this.list_tmp,
										TootMessageHolder(context.getString(R.string.toot_context_parse_failed))
									)
								}
								
								// マストドン2.6でTLにカード情報がレンダリングされたことにより、
								// 個別にカードを取得する機能は ST3.0.7で廃止される
								// この機能はRate limitの問題を引き起こすことが多かった
								// if(! Pref.bpDontRetrievePreviewCard(context)) {
								// 									this.list_tmp?.forEach { o ->
								//										// カードを取得する
								//										if(o is TootStatus && o.card == null)
								//											o.card = parseItem(
								//												::TootCard,
								//												client.request("/api/v1/statuses/" + o.id + "/card")?.jsonObject
								//											)
								//									}
								//								}
								
								return result
							}
						}
						
						TYPE_TREND_TAG -> {
							result = client.request("/api/v1/trends")
							val src = parser.trendTagList(result?.jsonArray)
							
							this.list_tmp = addAll(this.list_tmp, src)
							this.list_tmp = addOne(
								this.list_tmp, TootMessageHolder(
									context.getString(
										R.string.trend_tag_desc,
										getResetTimeString()
									),
									gravity = Gravity.END
								)
							)
							return result
							
						}
						
						TYPE_SEARCH -> if(isMisskey) {
							result = TootApiResult()
							val parser = TootParser(context, access_info)
							var params : JSONObject
							
							list_tmp = ArrayList()
							
							val queryAccount = search_query.trim().replace("^@".toRegex(), "")
							if(queryAccount.isNotEmpty()) {
								
								params = access_info.putMisskeyApiToken(JSONObject())
									.put("query", queryAccount)
									.put("localOnly", ! search_resolve)
								
								result = client.request(
									"/api/users/search",
									params.toPostRequestBuilder()
								)
								val jsonArray = result?.jsonArray
								if(jsonArray != null) {
									val src =
										TootParser(context, access_info).accountList(jsonArray)
									list_tmp = addAll(list_tmp, src)
								}
							}
							
							val queryTag = search_query.trim().replace("^#".toRegex(), "")
							if(queryTag.isNotEmpty()) {
								params = access_info.putMisskeyApiToken(JSONObject())
									.put("query", queryTag)
								result = client.request(
									"/api/hashtags/search",
									params.toPostRequestBuilder()
								)
								val jsonArray = result?.jsonArray
								if(jsonArray != null) {
									val src = TootTag.parseTootTagList(parser, jsonArray)
									list_tmp = addAll(list_tmp, src)
								}
							}
							if(search_query.isNotEmpty()) {
								params = access_info.putMisskeyApiToken(JSONObject())
									.put("query", search_query)
								result = client.request(
									"/api/notes/search",
									params.toPostRequestBuilder()
								)
								val jsonArray = result?.jsonArray
								if(jsonArray != null) {
									val src = parser.statusList(jsonArray)
									list_tmp = addWithFilterStatus(list_tmp, src)
								}
							}
							
							// 検索機能が無効だとsearch_query が 400を返すが、他のAPIがデータを返したら成功したことにする
							return if(list_tmp?.isNotEmpty() == true) {
								TootApiResult()
							} else {
								result
							}
						} else {
							if(access_info.isPseudo) {
								// 1.5.0rc からマストドンの検索APIは認証を要求するようになった
								return TootApiResult(context.getString(R.string.search_is_not_available_on_pseudo_account))
							}
							
							var instance = access_info.instance
							if(instance == null) {
								getInstanceInformation(client, null)
								if(instance_tmp != null) {
									instance = instance_tmp
									access_info.instance = instance
								}
							}
							
							if(instance?.versionGE(TootInstance.VERSION_2_4_0) == true) {
								// v2 api を試す
								var path = String.format(
									Locale.JAPAN,
									PATH_SEARCH_V2,
									search_query.encodePercent()
								)
								if(search_resolve) path += "&resolve=1"
								
								result = client.request(path)
								val jsonObject = result?.jsonObject
								if(jsonObject != null) {
									val tmp = parser.resultsV2(jsonObject)
									if(tmp != null) {
										list_tmp = ArrayList()
										addAll(list_tmp, tmp.hashtags)
										addAll(list_tmp, tmp.accounts)
										addAll(list_tmp, tmp.statuses)
										return result
									}
								}
								if(instance?.versionGE(TootInstance.VERSION_2_4_1_rc1) == true) {
									// 2.4.1rc1以降はv2が確実に存在するはずなので、v1へのフォールバックを行わない
									return result
								}
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
							idOld = null
							q = search_query.trim { it <= ' ' }
							if(q.isEmpty()) {
								list_tmp = ArrayList()
								result = TootApiResult()
							} else {
								result = client.searchMsp(search_query, idOld?.toString())
								val jsonArray = result?.jsonArray
								if(jsonArray != null) {
									// max_id の更新
									idOld = EntityId.mayNull(
										TootApiClient.getMspMaxId(
											jsonArray,
											idOld?.toString()
										)
									)
									// リストデータの用意
									parser.serviceType = ServiceType.MSP
									list_tmp =
										addWithFilterStatus(null, parser.statusList(jsonArray))
								}
							}
							return result
						}
						
						TYPE_SEARCH_TS -> {
							idOld = null
							q = search_query.trim { it <= ' ' }
							if(q.isEmpty()) {
								list_tmp = ArrayList()
								result = TootApiResult()
							} else {
								result = client.searchTootsearch(search_query, idOld?.toLong())
								val jsonObject = result?.jsonObject
								if(jsonObject != null) {
									// max_id の更新
									idOld = EntityId.mayNull(
										TootApiClient.getTootsearchMaxId(
											jsonObject,
											idOld?.toLong()
										)
									)
									
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
						
						else -> return getStatuses(client, makeHomeTlUrl())
					}
				} finally {
					try {
						updateRelation(client, list_tmp, who_account, parser)
					} catch(ex : Throwable) {
						log.trace(ex)
					}
					ctClosed.set(true)
					runOnMainLooperDelayed(333L) {
						if(! isCancelled) fireShowColumnStatus()
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
				lastTask = null
				
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
				
				updateMisskeyCapture()
			}
		}
		this.lastTask = task
		task.executeOnExecutor(App1.task_executor)
	}
	
	private var bMinIdMatched : Boolean = false
	
	private fun parseRange(
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
				val m = reMaxId.matcher(result.link_older)
				if(m.find()) {
					EntityIdLong(m.group(1).toLong())
				} else {
					null
				}
			}
			
			idMax = if(result.link_newer == null) {
				null
			} else {
				var m = reMinId.matcher(result.link_newer)
				if(m.find()) {
					bMinIdMatched = true
					EntityIdLong(m.group(1).toLong())
				} else {
					m = reSinceId.matcher(result.link_newer)
					if(m.find()) {
						bMinIdMatched = false
						EntityIdLong(m.group(1).toLong())
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
	private fun saveRange(
		bBottom : Boolean,
		bTop : Boolean,
		result : TootApiResult?,
		list : List<TimelineItem>?
	) : Boolean {
		val (idMin, idMax) = parseRange(result, list)
		
		var hasBottomRemain = false
		
		if(bBottom) {
			when {
				idMin == null -> idOld = null // リストの終端
				idMin is EntityIdString && ! isMisskey -> {
					log.e("EntityId should be Long for non-misskey column! columnType=$column_type")
				}
				
				else -> {
					val i = idOld?.compareTo(idMin)
					if(i == null || i > 0) {
						idOld = idMin
						hasBottomRemain = true
					}
				}
			}
		}
		
		if(bTop) {
			when {
				// リロードを許容するため、取得内容がカラでもidRecentを変更しない
				idMax == null -> {
				}
				
				idMax is EntityIdString && ! isMisskey -> {
					log.e("EntityId should be Long for non-misskey column! columnType=$column_type")
				}
				
				else -> {
					val i = idRecent?.compareTo(idMax)
					if(i == null || i < 0) {
						idRecent = idMax
					}
				}
			}
		}
		
		return hasBottomRemain
	}
	
	// return true if list bottom may have unread remain
	private fun saveRangeEnd(result : TootApiResult?, list : List<TimelineItem>?) =
		saveRange(true, false, result = result, list = list)
	
	// return true if list bottom may have unread remain
	private fun saveRangeStart(result : TootApiResult?, list : List<TimelineItem>?) =
		saveRange(false, true, result = result, list = list)
	
	private fun addRange(bBottom : Boolean, path : String) : String {
		val delimiter = if(- 1 != path.indexOf('?')) '&' else '?'
		if(bBottom) {
			if(idOld != null) return "$path${delimiter}max_id=${idOld}"
		} else {
			if(idRecent != null) return "$path${delimiter}since_id=${idRecent}"
		}
		return path
	}
	
	private fun addRangeMin(path : String) : String {
		val delimiter = if(- 1 != path.indexOf('?')) '&' else '?'
		if(idRecent != null) return "$path${delimiter}min_id=${idRecent}"
		return path
	}
	
	private fun addRangeMisskey(bBottom : Boolean, params : JSONObject) : JSONObject {
		if(bBottom) {
			idOld?.putMisskeyUntil(params)
		} else {
			idRecent?.putMisskeySince(params)
		}
		return params
	}
	
	internal fun startRefreshForPost(
		refresh_after_post : Int,
		posted_status_id : EntityId,
		posted_reply_id : EntityId?
	) {
		when(column_type) {
			TYPE_HOME, TYPE_LOCAL, TYPE_FEDERATE, TYPE_DIRECT_MESSAGES, TYPE_MISSKEY_HYBRID -> {
				startRefresh(
					true,
					false,
					posted_status_id,
					refresh_after_post
				)
			}
			
			TYPE_PROFILE -> {
				if(profile_tab == TAB_STATUS
					&& profile_id == access_info.loginAccount?.id
				) {
					startRefresh(
						true,
						false,
						posted_status_id,
						refresh_after_post
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
		
		val task = @SuppressLint("StaticFieldLeak")
		object : ColumnTask(
			when {
				bBottom -> ColumnTaskType.REFRESH_BOTTOM
				else -> ColumnTaskType.REFRESH_TOP
			}
		) {
			var parser = TootParser(context, access_info, highlightTrie = highlight_trie)
			
			var list_tmp : ArrayList<TimelineItem>? = null
			
			fun getAccountList(
				client : TootApiClient,
				path_base : String,
				misskeyParams : JSONObject? = null,
				misskeyArrayFinder : (JSONObject) -> JSONArray? = { null },
				misskeyCustomParser : (parser : TootParser, jsonArray : JSONArray) -> ArrayList<TootAccountRef> =
					{ parser, jsonArray -> parser.accountList(jsonArray) }
			) : TootApiResult? {
				
				@Suppress("NON_EXHAUSTIVE_WHEN")
				when(bBottom) {
					false -> when(pagingType) {
						PagingType.Cursor,
						PagingType.None,
						PagingType.Offset -> {
							return TootApiResult("can't refresh top.")
						}
					}
					true -> when(pagingType) {
						PagingType.Cursor -> if(idOld == null) {
							return TootApiResult(context.getString(R.string.end_of_list))
						}
						
						PagingType.None -> {
							return TootApiResult(context.getString(R.string.end_of_list))
						}
					}
				}
				
				val params = misskeyParams ?: makeMisskeyBaseParameter(parser)
				val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
				
				val last_since_id = idRecent
				
				val time_start = SystemClock.elapsedRealtime()
				
				var result = if(isMisskey) {
					@Suppress("NON_EXHAUSTIVE_WHEN")
					when(pagingType) {
						PagingType.Default -> addRangeMisskey(bBottom, params)
						PagingType.Offset -> params.put("offset", offsetNext)
						PagingType.Cursor -> params.put("cursor", idOld)
					}
					client.request(path_base, params.toPostRequestBuilder())
				} else {
					client.request(addRange(bBottom, path_base))
				}
				val firstResult = result
				
				var jsonObject = result?.jsonObject
				if(jsonObject != null) {
					if(pagingType == PagingType.Cursor) {
						idOld = EntityId.mayNull(jsonObject.parseString("next"))
					}
					result !!.data = misskeyArrayFinder(jsonObject)
				}
				
				var array = result?.jsonArray
				if(array != null) {
					
					var src = misskeyCustomParser(parser, array)
					@Suppress("NON_EXHAUSTIVE_WHEN")
					when(pagingType) {
						PagingType.Default -> {
							saveRange(bBottom, ! bBottom, firstResult, src)
						}
						
						PagingType.Offset -> {
							offsetNext += src.size
						}
					}
					list_tmp = addAll(null, src)
					
					if(! bBottom) {
						
						if(isMisskey) {
							var bHeadGap = false
							
							// misskeyの場合、sinceIdを指定したら未読範囲の古い方から読んでしまう
							// 最新まで読めるとは限らない
							// 先頭にギャップを置くかもしれない
							while(true) {
								
								if(isCancelled) {
									log.d("refresh-account-top: cancelled.")
									break
								}
								
								if(src.isEmpty()) {
									// 直前のデータが0個なら終了とみなす
									log.d("refresh-account-top: previous size == 0.")
									break
								}
								
								if(SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
									log.d("refresh-account-top: timeout.")
									bHeadGap = true
									break
								}
								
								idRecent?.putMisskeySince(params)
								
								result = client.request(path_base, params.toPostRequestBuilder())
								
								jsonObject = result?.jsonObject
								if(jsonObject != null) {
									// pagingType is always default.
									result !!.data = misskeyArrayFinder(jsonObject)
								}
								
								array = result?.jsonArray
								if(array == null) {
									log.d("refresh-account-top: error or cancelled.")
									bHeadGap = true
									break
								}
								
								src = misskeyCustomParser(parser, array)
								
								addAll(list_tmp, src)
								
								// pagingType is always default.
								saveRange(false, true, result, src)
							}
							
							// pagingType is always default.
							if(isMisskey && ! bBottom) {
								list_tmp?.sortBy { it.getOrderId() }
								list_tmp?.reverse()
							}
							
							if(! isCancelled
								&& list_tmp?.isNotEmpty() == true
								&& (bHeadGap || Pref.bpForceGap(context))
							) {
								addOneFirst(list_tmp, TootGap.mayNull(null, idRecent))
							}
							
						} else {
							var bGapAdded = false
							var max_id : EntityId? = null
							while(true) {
								if(isCancelled) {
									log.d("refresh-account-top: cancelled.")
									break
								}
								
								// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
								// 直前のデータが0個なら終了とみなすしかなさそう
								if(src.isEmpty()) {
									log.d("refresh-account-top: previous size == 0.")
									break
								}
								
								max_id = parseRange(result, src).first
								
								if(SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
									log.d("refresh-account-top: timeout. make gap.")
									// タイムアウト
									// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
									addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
									bGapAdded = true
									break
								}
								
								val path =
									"$path_base${delimiter}max_id=$max_id&since_id=$last_since_id"
								result = client.request(path)
								
								jsonObject = result?.jsonObject
								if(jsonObject != null) {
									result?.data = misskeyArrayFinder(jsonObject)
								}
								
								val jsonArray = result?.jsonArray
								
								if(jsonArray == null) {
									log.d("refresh-account-top: error or cancelled. make gap.")
									// エラー
									// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
									addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
									bGapAdded = true
									break
								}
								
								src = misskeyCustomParser(parser, jsonArray)
								addAll(list_tmp, src)
							}
							if(Pref.bpForceGap(context) && ! isCancelled && ! bGapAdded && list_tmp?.isNotEmpty() == true) {
								addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
							}
						}
					}
					// フィルタがないので下端更新の繰り返しは発生しない
				}
				return firstResult
			}
			
			fun getDomainList(
				client : TootApiClient,
				path_base : String
			) : TootApiResult? {
				
				if(isMisskey) return TootApiResult("misskey support is not yet implemented.")
				
				val time_start = SystemClock.elapsedRealtime()
				val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
				val last_since_id = idRecent
				
				var result = client.request(addRange(bBottom, path_base))
				val firstResult = result
				
				var jsonArray = result?.jsonArray
				if(jsonArray != null) {
					var src = TootDomainBlock.parseList(jsonArray)
					// ページネーションはサーバ側の内部パラメータで行われる
					saveRange(bBottom, ! bBottom, result, src)
					list_tmp = addAll(null, src)
					if(! bBottom) {
						if(isMisskey) {
							// Misskey非対応
						} else {
							var bGapAdded = false
							var max_id : EntityId? = null
							while(true) {
								
								if(isCancelled) {
									log.d("refresh-domain-top: cancelled.")
									break
								}
								
								// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
								// 直前のデータが0個なら終了とみなすしかなさそう
								if(src.isEmpty()) {
									log.d("refresh-domain-top: previous size == 0.")
									break
								}
								
								// 直前に読んだ範囲のmaxIdを調べる
								max_id = parseRange(result, src).first
								
								if(SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
									log.d("refresh-domain-top: timeout.")
									
									// タイムアウト
									// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
									addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
									bGapAdded = true
									break
								}
								
								val path =
									"$path_base${delimiter}max_id=$max_id&since_id=$last_since_id"
								result = client.request(path)
								jsonArray = result?.jsonArray
								if(jsonArray == null) {
									log.d("refresh-domain-top: error or cancelled.")
									// エラー
									// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
									addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
									bGapAdded = true
									break
								}
								
								src = TootDomainBlock.parseList(jsonArray)
								addAll(list_tmp, src)
							}
							if(Pref.bpForceGap(context) && ! isCancelled && ! bGapAdded && list_tmp?.isNotEmpty() == true) {
								addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
							}
						}
						
					}
					// フィルタがないので下端更新の繰り返しはない
				}
				return firstResult
			}
			
			//			fun getListList(client : TootApiClient, path_base : String) : TootApiResult? {
			//
			//				if(isMisskey) return TootApiResult("misskey support is not yet implemented.")
			//
			//				return TootApiResult("Mastodon's /api/v1/lists has no pagination.")
			//			}
			
			fun getReportList(
				client : TootApiClient,
				path_base : String
			) : TootApiResult? {
				
				if(isMisskey) return TootApiResult("misskey support is not yet implemented.")
				
				val time_start = SystemClock.elapsedRealtime()
				val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
				val last_since_id = idRecent
				var result = client.request(addRange(bBottom, path_base))
				val firstResult = result
				var jsonArray = result?.jsonArray
				if(jsonArray != null) {
					var src = parseList(::TootReport, jsonArray)
					list_tmp = addAll(null, src)
					saveRange(bBottom, ! bBottom, result, src)
					
					if(! bBottom) {
						var bGapAdded = false
						var max_id : EntityId? = null
						while(true) {
							if(isCancelled) {
								log.d("refresh-report-top: cancelled.")
								break
							}
							
							// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
							// 直前のデータが0個なら終了とみなすしかなさそう
							if(src.isEmpty()) {
								log.d("refresh-report-top: previous size == 0.")
								break
							}
							
							// 直前に読んだ範囲のmaxIdを調べる
							max_id = parseRange(result, src).first
							
							if(SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
								log.d("refresh-report-top: timeout. make gap.")
								// タイムアウト
								// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
								addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
								bGapAdded = true
								break
							}
							
							val path =
								"$path_base${delimiter}max_id=$max_id&since_id=$last_since_id"
							result = client.request(path)
							jsonArray = result?.jsonArray
							if(jsonArray == null) {
								log.d("refresh-report-top: timeout. error or retry. make gap.")
								// エラー
								// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
								addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
								bGapAdded = true
								break
							}
							
							src = parseList(::TootReport, jsonArray)
							addAll(list_tmp, src)
						}
						if(Pref.bpForceGap(context) && ! isCancelled && ! bGapAdded && list_tmp?.isNotEmpty() == true) {
							addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
						}
					}
					// レポートにはフィルタがないので下端更新は繰り返さない
				}
				return firstResult
			}
			
			fun getNotificationList(client : TootApiClient) : TootApiResult? {
				
				val path_base = makeNotificationUrl()
				val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
				val last_since_id = idRecent
				
				val params = makeMisskeyBaseParameter(parser)
				
				val time_start = SystemClock.elapsedRealtime()
				
				var result = if(isMisskey) {
					addRangeMisskey(bBottom, params)
					client.request(path_base, params.toPostRequestBuilder())
				} else {
					client.request(addRange(bBottom, path_base))
				}
				val firstResult = result
				var jsonArray = result?.jsonArray
				if(jsonArray != null) {
					var src = parser.notificationList(jsonArray)
					
					list_tmp = addWithFilterNotification(null, src)
					saveRange(bBottom, ! bBottom, result, src)
					
					if(! src.isEmpty()) {
						PollingWorker.injectData(context, access_info, src)
					}
					
					if(! bBottom) {
						// 頭の方を読む時は隙間を減らすため、フィルタの有無に関係なく繰り返しを行う
						
						if(isMisskey) {
							// misskey ではsinceIdを指定すると古い方から読める
							// 先頭にギャップを追加するかもしれない
							var bHeadGap = false
							
							while(true) {
								
								if(isCancelled) {
									log.d("refresh-notification-top: cancelled.")
									break
								}
								
								// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
								// 直前のデータが0個なら終了とみなすしかなさそう
								if(src.isEmpty()) {
									log.d("refresh-notification-top: previous size == 0.")
									break
								}
								
								if(SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
									log.d("refresh-notification-top: timeout. make gap.")
									// タイムアウト
									bHeadGap = true
									break
								}
								
								idRecent?.putMisskeySince(params)
								result = client.request(path_base, params.toPostRequestBuilder())
								jsonArray = result?.jsonArray
								if(jsonArray == null) {
									log.d("refresh-notification-top: error or cancelled. make gap.")
									// エラー
									bHeadGap = true
									break
								}
								
								src = parser.notificationList(jsonArray)
								
								saveRange(false, true, result, src)
								if(! src.isEmpty()) {
									addWithFilterNotification(list_tmp, src)
									PollingWorker.injectData(context, access_info, src)
								}
							}
							
							if(isMisskey && ! bBottom) {
								list_tmp?.sortBy { it.getOrderId() }
								list_tmp?.reverse()
							}
							
							if(! isCancelled
								&& list_tmp?.isNotEmpty() == true
								&& (bHeadGap || Pref.bpForceGap(context))
							) {
								addOneFirst(list_tmp, TootGap.mayNull(null, idRecent))
							}
							
						} else {
							
							var bGapAdded = false
							var max_id : EntityId? = null
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
								
								max_id = parseRange(result, src).first
								
								if(SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
									log.d("refresh-notification-offset: timeout. make gap.")
									// タイムアウト
									// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
									addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
									bGapAdded = true
									break
								}
								
								val path =
									"$path_base${delimiter}max_id=$max_id&since_id=$last_since_id"
								result = client.request(path)
								jsonArray = result?.jsonArray
								if(jsonArray == null) {
									log.d("refresh-notification-offset: error or cancelled. make gap.")
									// エラー
									// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
									addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
									bGapAdded = true
									break
								}
								
								src = parser.notificationList(jsonArray)
								if(! src.isEmpty()) {
									addWithFilterNotification(list_tmp, src)
									PollingWorker.injectData(context, access_info, src)
								}
							}
							if(Pref.bpForceGap(context) && ! isCancelled && ! bGapAdded && list_tmp?.isNotEmpty() == true) {
								addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
							}
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
							
							result = if(isMisskey) {
								idOld?.putMisskeyUntil(params)
								client.request(path_base)
							} else {
								val path = path_base + delimiter + "max_id=" + idOld
								client.request(path)
								
							}
							jsonArray = result?.jsonArray
							if(jsonArray == null) {
								log.d("refresh-notification-bottom: error or cancelled.")
								break
							}
							
							src = parser.notificationList(jsonArray)
							
							addWithFilterNotification(list_tmp, src)
							
							if(! saveRangeEnd(result, src)) {
								log.d("refresh-notification-bottom: saveRangeEnd failed.")
								break
							}
						}
					}
				}
				return firstResult
			}
			
			fun getConversationSummaryList(
				client : TootApiClient,
				path_base : String,
				aroundMin : Boolean = false,
				misskeyParams : JSONObject? = null,
				misskeyCustomParser : (parser : TootParser, jsonArray : JSONArray) -> ArrayList<TootConversationSummary> =
					{ parser, jsonArray -> parseList(::TootConversationSummary, parser, jsonArray) }
			) : TootApiResult? {
				
				val isMisskey = access_info.isMisskey
				
				val params = misskeyParams ?: makeMisskeyTimelineParameter(parser)
				
				val time_start = SystemClock.elapsedRealtime()
				
				val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
				val last_since_id = idRecent
				
				var result = when {
					isMisskey -> {
						addRangeMisskey(bBottom, params)
						client.request(path_base, params.toPostRequestBuilder())
					}
					
					aroundMin -> client.request(addRangeMin(path_base))
					else -> client.request(addRange(bBottom, path_base))
				}
				val firstResult = result
				
				val jsonArray = result?.jsonArray
				if(jsonArray != null) {
					var src = misskeyCustomParser(parser, jsonArray)
					
					saveRange(bBottom, ! bBottom, result, src)
					list_tmp = addWithFilterConversationSummary(null, src)
					
					if(! bBottom) {
						if(isMisskey) {
							// Misskeyの場合はsinceIdを指定しても取得できるのは未読のうち古い範囲に偏る
							var bHeadGap = false
							while(true) {
								if(isCancelled) {
									log.d("refresh-ConversationSummary-top: cancelled.")
									break
								}
								
								// 頭の方を読む時は隙間を減らすため、フィルタの有無に関係なく繰り返しを行う
								
								// 直前のデータが0個なら終了とみなす
								if(src.isEmpty()) {
									log.d("refresh-ConversationSummary-top: previous size == 0.")
									break
								}
								
								if((list_tmp?.size ?: 0) >= LOOP_READ_ENOUGH) {
									log.d("refresh-ConversationSummary-top: read enough. make gap.")
									bHeadGap = true
									break
								}
								
								if(SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
									log.d("refresh-ConversationSummary-top: timeout. make gap.")
									bHeadGap = true
									break
								}
								
								idRecent?.putMisskeySince(params)
								result =
									client.request(path_base, params.toPostRequestBuilder())
								
								val jsonArray2 = result?.jsonArray
								if(jsonArray2 == null) {
									log.d("refresh-ConversationSummary-top: error or cancelled. make gap.")
									bHeadGap = true
									break
								}
								
								src = misskeyCustomParser(parser, jsonArray2)
								
								saveRange(false, true, result, src)
								
								addWithFilterConversationSummary(list_tmp, src)
							}
							
							if(isMisskey && ! bBottom) {
								list_tmp?.sortBy { it.getOrderId() }
								list_tmp?.reverse()
							}
							
							if(! isCancelled
								&& list_tmp?.isNotEmpty() == true
								&& (bHeadGap || Pref.bpForceGap(context))
							) {
								addOneFirst(list_tmp, TootGap.mayNull(null, idRecent))
							}
							
						} else if(aroundMin) {
							while(true) {
								
								saveRangeStart(result, src)
								
								if(isCancelled) {
									log.d("refresh-ConversationSummary-aroundMin: cancelled.")
									break
								}
								
								// 頭の方を読む時は隙間を減らすため、フィルタの有無に関係なく繰り返しを行う
								
								// 直前のデータが0個なら終了とみなすしかなさそう
								if(src.isEmpty()) {
									log.d("refresh-ConversationSummary-aroundMin: previous size == 0.")
									break
								}
								
								if((list_tmp?.size ?: 0) >= LOOP_READ_ENOUGH) {
									log.d("refresh-ConversationSummary-aroundMin: read enough.")
									break
								}
								
								if(SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
									log.d("refresh-ConversationSummary-aroundMin: timeout.")
									break
								}
								
								val path = "$path_base${delimiter}min_id=$idRecent"
								result = client.request(path)
								
								val jsonArray2 = result?.jsonArray
								if(jsonArray2 == null) {
									log.d("refresh-ConversationSummary-aroundMin: error or cancelled.")
									break
								}
								
								src = misskeyCustomParser(parser, jsonArray2)
								addWithFilterConversationSummary(list_tmp, src)
							}
						} else {
							var bGapAdded = false
							var max_id : EntityId? = null
							while(true) {
								if(isCancelled) {
									log.d("refresh-ConversationSummary-top: cancelled.")
									break
								}
								
								// 頭の方を読む時は隙間を減らすため、フィルタの有無に関係なく繰り返しを行う
								
								// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
								// 直前のデータが0個なら終了とみなすしかなさそう
								if(src.isEmpty()) {
									log.d("refresh-ConversationSummary-top: previous size == 0.")
									break
								}
								
								max_id = parseRange(result, src).first
								
								if((list_tmp?.size ?: 0) >= LOOP_READ_ENOUGH) {
									log.d("refresh-ConversationSummary-top: read enough. make gap.")
									// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
									addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
									bGapAdded = true
									break
								}
								
								if(SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
									log.d("refresh-ConversationSummary-top: timeout. make gap.")
									// タイムアウト
									// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
									addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
									bGapAdded = true
									break
								}
								
								val path =
									"$path_base${delimiter}max_id=$max_id&since_id=$last_since_id"
								result = client.request(path)
								
								val jsonArray2 = result?.jsonArray
								if(jsonArray2 == null) {
									log.d("refresh-ConversationSummary-top: error or cancelled. make gap.")
									// エラー
									// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
									addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
									bGapAdded = true
									break
								}
								
								src = misskeyCustomParser(parser, jsonArray2)
								addWithFilterConversationSummary(list_tmp, src)
							}
							
							if(Pref.bpForceGap(context) && ! isCancelled && ! bGapAdded && list_tmp?.isNotEmpty() == true) {
								addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
							}
						}
						
					} else {
						while(true) {
							if(isCancelled) {
								log.d("refresh-ConversationSummary-bottom: cancelled.")
								break
							}
							
							// bottomの場合、フィルタなしなら繰り返さない
							if(! isFilterEnabled) {
								log.d("refresh-ConversationSummary-bottom: isFiltered is false.")
								break
							}
							
							// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
							// 直前のデータが0個なら終了とみなすしかなさそう
							if(src.isEmpty()) {
								log.d("refresh-ConversationSummary-bottom: previous size == 0.")
								break
							}
							
							// 十分読んだらそれで終了
							if((list_tmp?.size ?: 0) >= LOOP_READ_ENOUGH) {
								log.d("refresh-ConversationSummary-bottom: read enough data.")
								break
							}
							
							if(SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
								// タイムアウト
								log.d("refresh-ConversationSummary-bottom: loop timeout.")
								break
							}
							
							result = if(isMisskey) {
								idOld?.putMisskeyUntil(params)
								client.request(path_base, params.toPostRequestBuilder())
							} else {
								val path = "$path_base${delimiter}max_id=$idOld"
								client.request(path)
							}
							
							val jsonArray2 = result?.jsonArray
							if(jsonArray2 == null) {
								log.d("refresh-ConversationSummary-bottom: error or cancelled.")
								break
							}
							
							src = misskeyCustomParser(parser, jsonArray2)
							addWithFilterConversationSummary(list_tmp, src)
							
							if(! saveRangeEnd(result, src)) {
								log.d("refresh-ConversationSummary-bottom: saveRangeEnd failed.")
								break
							}
						}
					}
				}
				return firstResult
			}
			
			fun getStatusList(
				client : TootApiClient,
				path_base : String,
				aroundMin : Boolean = false,
				misskeyParams : JSONObject? = null,
				misskeyCustomParser : (parser : TootParser, jsonArray : JSONArray) -> ArrayList<TootStatus> =
					{ parser, jsonArray -> parser.statusList(jsonArray) }
			) : TootApiResult? {
				
				val isMisskey = access_info.isMisskey
				
				val params = misskeyParams ?: makeMisskeyTimelineParameter(parser)
				
				val time_start = SystemClock.elapsedRealtime()
				
				val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
				val last_since_id = idRecent
				
				var result = when {
					isMisskey -> {
						addRangeMisskey(bBottom, params)
						client.request(path_base, params.toPostRequestBuilder())
					}
					
					aroundMin -> client.request(addRangeMin(path_base))
					else -> client.request(addRange(bBottom, path_base))
				}
				val firstResult = result
				
				val jsonArray = result?.jsonArray
				if(jsonArray != null) {
					var src = misskeyCustomParser(parser, jsonArray)
					
					saveRange(bBottom, ! bBottom, result, src)
					list_tmp = addWithFilterStatus(null, src)
					
					if(! bBottom) {
						if(isMisskey) {
							// Misskeyの場合はsinceIdを指定しても取得できるのは未読のうち古い範囲に偏る
							var bHeadGap = false
							while(true) {
								if(isCancelled) {
									log.d("refresh-status-top: cancelled.")
									break
								}
								
								// 頭の方を読む時は隙間を減らすため、フィルタの有無に関係なく繰り返しを行う
								
								// 直前のデータが0個なら終了とみなす
								if(src.isEmpty()) {
									log.d("refresh-status-top: previous size == 0.")
									break
								}
								
								if((list_tmp?.size ?: 0) >= LOOP_READ_ENOUGH) {
									log.d("refresh-status-top: read enough. make gap.")
									bHeadGap = true
									break
								}
								
								if(SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
									log.d("refresh-status-top: timeout. make gap.")
									bHeadGap = true
									break
								}
								
								idRecent?.putMisskeySince(params)
								result =
									client.request(path_base, params.toPostRequestBuilder())
								
								val jsonArray2 = result?.jsonArray
								if(jsonArray2 == null) {
									log.d("refresh-status-top: error or cancelled. make gap.")
									bHeadGap = true
									break
								}
								
								src = misskeyCustomParser(parser, jsonArray2)
								
								saveRange(false, true, result, src)
								
								addWithFilterStatus(list_tmp, src)
							}
							
							if(isMisskey && ! bBottom) {
								list_tmp?.sortBy { it.getOrderId() }
								list_tmp?.reverse()
							}
							
							if(! isCancelled
								&& list_tmp?.isNotEmpty() == true
								&& (bHeadGap || Pref.bpForceGap(context))
							) {
								addOneFirst(list_tmp, TootGap.mayNull(null, idRecent))
							}
							
						} else if(aroundMin) {
							while(true) {
								
								saveRangeStart(result, src)
								
								if(isCancelled) {
									log.d("refresh-status-aroundMin: cancelled.")
									break
								}
								
								// 頭の方を読む時は隙間を減らすため、フィルタの有無に関係なく繰り返しを行う
								
								// 直前のデータが0個なら終了とみなすしかなさそう
								if(src.isEmpty()) {
									log.d("refresh-status-aroundMin: previous size == 0.")
									break
								}
								
								if((list_tmp?.size ?: 0) >= LOOP_READ_ENOUGH) {
									log.d("refresh-status-aroundMin: read enough.")
									break
								}
								
								if(SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
									log.d("refresh-status-aroundMin: timeout.")
									break
								}
								
								val path = "$path_base${delimiter}min_id=$idRecent"
								result = client.request(path)
								
								val jsonArray2 = result?.jsonArray
								if(jsonArray2 == null) {
									log.d("refresh-status-aroundMin: error or cancelled.")
									break
								}
								
								src = misskeyCustomParser(parser, jsonArray2)
								addWithFilterStatus(list_tmp, src)
							}
						} else {
							var bGapAdded = false
							var max_id : EntityId? = null
							while(true) {
								if(isCancelled) {
									log.d("refresh-status-top: cancelled.")
									break
								}
								
								// 頭の方を読む時は隙間を減らすため、フィルタの有無に関係なく繰り返しを行う
								
								// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
								// 直前のデータが0個なら終了とみなすしかなさそう
								if(src.isEmpty()) {
									log.d("refresh-status-top: previous size == 0.")
									break
								}
								
								max_id = parseRange(result, src).first
								
								if((list_tmp?.size ?: 0) >= LOOP_READ_ENOUGH) {
									log.d("refresh-status-top: read enough. make gap.")
									// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
									addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
									bGapAdded = true
									break
								}
								
								if(SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
									log.d("refresh-status-top: timeout. make gap.")
									// タイムアウト
									// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
									addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
									bGapAdded = true
									break
								}
								
								val path =
									"$path_base${delimiter}max_id=$max_id&since_id=$last_since_id"
								result = client.request(path)
								
								val jsonArray2 = result?.jsonArray
								if(jsonArray2 == null) {
									log.d("refresh-status-top: error or cancelled. make gap.")
									// エラー
									// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
									addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
									bGapAdded = true
									break
								}
								
								src = misskeyCustomParser(parser, jsonArray2)
								addWithFilterStatus(list_tmp, src)
							}
							
							if(Pref.bpForceGap(context) && ! isCancelled && ! bGapAdded && list_tmp?.isNotEmpty() == true) {
								addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
							}
						}
						
					} else {
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
							
							result = if(isMisskey) {
								idOld?.putMisskeyUntil(params)
								client.request(path_base, params.toPostRequestBuilder())
							} else {
								val path = "$path_base${delimiter}max_id=$idOld"
								client.request(path)
							}
							
							val jsonArray2 = result?.jsonArray
							if(jsonArray2 == null) {
								log.d("refresh-status-bottom: error or cancelled.")
								break
							}
							
							src = misskeyCustomParser(parser, jsonArray2)
							addWithFilterStatus(list_tmp, src)
							
							if(! saveRangeEnd(result, src)) {
								log.d("refresh-status-bottom: saveRangeEnd failed.")
								break
							}
						}
					}
				}
				return firstResult
			}
			
			var filterUpdated = false
			
			override fun doInBackground(vararg unused : Void) : TootApiResult? {
				ctStarted.set(true)
				
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
					
					if(! bBottom) {
						val filterList = loadFilter2(client)
						if(filterList != null) {
							muted_word2 = encodeFilterTree(filterList)
							filterUpdated = true
						}
					}
					
					return when(column_type) {
						
						TYPE_DIRECT_MESSAGES -> if(useConversationSummarys) {
							// try 2.6.0 new API https://github.com/tootsuite/mastodon/pull/8832
							getConversationSummaryList(client, PATH_DIRECT_MESSAGES2)
						} else {
							// fallback to old api
							getStatusList(client, PATH_DIRECT_MESSAGES)
						}
						
						TYPE_LOCAL -> getStatusList(client, makePublicLocalUrl())
						
						TYPE_LOCAL_AROUND -> {
							if(bBottom) {
								// 通常と同じ
								getStatusList(client, makePublicLocalUrl())
							} else {
								val rv =
									getStatusList(client, makePublicLocalUrl(), aroundMin = true)
								list_tmp?.sortBy { it.getOrderId() }
								list_tmp?.reverse()
								rv
							}
						}
						
						TYPE_FEDERATED_AROUND -> {
							if(bBottom) {
								// 通常と同じ
								getStatusList(client, makePublicFederateUrl())
							} else {
								val rv =
									getStatusList(client, makePublicFederateUrl(), aroundMin = true)
								list_tmp?.sortBy { it.getOrderId() }
								list_tmp?.reverse()
								rv
							}
						}
						
						TYPE_ACCOUNT_AROUND -> {
							var s = String.format(
								Locale.JAPAN,
								PATH_ACCOUNT_STATUSES,
								profile_id
							)
							if(with_attachment && ! with_highlight) s += "&only_media=1"
							getStatusList(client, s)
							
							if(bBottom) {
								getStatusList(client, s)
							} else {
								val rv =
									getStatusList(client, s, aroundMin = true)
								list_tmp?.sortBy { it.getOrderId() }
								list_tmp?.reverse()
								rv
							}
						}
						
						TYPE_MISSKEY_HYBRID -> getStatusList(client, makeMisskeyHybridTlUrl())
						
						TYPE_FEDERATE -> getStatusList(client, makePublicFederateUrl())
						
						TYPE_FAVOURITES -> if(isMisskey) {
							getStatusList(
								client
								, PATH_MISSKEY_FAVORITES
								, misskeyParams = makeMisskeyTimelineParameter(parser)
								, misskeyCustomParser = misskeyCustomParserFavorites
							)
						} else {
							getStatusList(client, PATH_FAVOURITES)
						}
						
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
							loadProfileAccount(client, parser, false)
							
							
							when(profile_tab) {
								TAB_FOLLOWING -> if(isMisskey) {
									getAccountList(
										client,
										PATH_MISSKEY_PROFILE_FOLLOWING,
										misskeyParams = makeMisskeyParamsUserId(parser),
										misskeyArrayFinder = misskeyArrayFinderUsers
									)
								} else {
									getAccountList(
										client,
										String.format(
											Locale.JAPAN,
											PATH_ACCOUNT_FOLLOWING,
											profile_id
										)
									)
								}
								
								TAB_FOLLOWERS -> if(isMisskey) {
									getAccountList(
										client,
										PATH_MISSKEY_PROFILE_FOLLOWERS,
										misskeyParams = makeMisskeyParamsUserId(parser),
										misskeyArrayFinder = misskeyArrayFinderUsers
									)
								} else {
									getAccountList(
										client,
										String.format(
											Locale.JAPAN,
											PATH_ACCOUNT_FOLLOWERS,
											profile_id
										)
									)
								}
								
								else -> if(isMisskey) {
									getStatusList(
										client,
										PATH_MISSKEY_PROFILE_STATUSES,
										misskeyParams = makeMisskeyParamsProfileStatuses(parser)
									)
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
						
						TYPE_LIST_LIST -> {
							TootApiResult("list API does not support refresh loading.")
						}
						
						TYPE_LIST_TL -> {
							loadListInfo(client, false)
							if(isMisskey) {
								val params = makeMisskeyTimelineParameter(parser)
									.put("listId", profile_id)
								getStatusList(client, makeListTlUrl(), misskeyParams = params)
							} else {
								getStatusList(client, makeListTlUrl())
							}
						}
						
						TYPE_LIST_MEMBER -> {
							loadListInfo(client, false)
							getAccountList(
								client,
								String.format(Locale.JAPAN, PATH_LIST_MEMBER, profile_id)
							)
						}
						
						TYPE_MUTES -> if(isMisskey) {
							getAccountList(
								client
								, PATH_MISSKEY_MUTES
								, misskeyParams = access_info.putMisskeyApiToken(JSONObject())
								, misskeyArrayFinder = misskeyArrayFinderUsers
							)
						} else {
							getAccountList(client, PATH_MUTES)
						}
						
						TYPE_BLOCKS -> if(isMisskey) {
							pagingType = PagingType.Default
							val params = access_info.putMisskeyApiToken(JSONObject())
							getAccountList(
								client,
								"/api/blocking/list",
								misskeyParams = params,
								misskeyCustomParser = misskeyCustomParserBlocks
							)
						} else {
							getAccountList(client, PATH_BLOCKS)
						}
						
						TYPE_DOMAIN_BLOCKS -> getDomainList(client, PATH_DOMAIN_BLOCK)
						
						TYPE_FOLLOW_REQUESTS -> if(isMisskey) {
							getAccountList(
								client
								, PATH_MISSKEY_FOLLOW_REQUESTS
								, misskeyParams = access_info.putMisskeyApiToken(JSONObject())
								, misskeyCustomParser = misskeyCustomParserFollowRequest
							)
							
						} else {
							getAccountList(client, PATH_FOLLOW_REQUESTS)
						}
						TYPE_FOLLOW_SUGGESTION -> if(isMisskey) {
							getAccountList(
								client
								, PATH_MISSKEY_FOLLOW_SUGGESTION
								, misskeyParams = access_info.putMisskeyApiToken(JSONObject())
							)
						} else {
							getAccountList(client, PATH_FOLLOW_SUGGESTION)
						}
						TYPE_ENDORSEMENT -> getAccountList(client, PATH_ENDORSEMENT)
						
						TYPE_HASHTAG -> if(isMisskey) {
							getStatusList(
								client
								, makeHashtagUrl(hashtag)
								, misskeyParams = makeMisskeyTimelineParameter(parser)
									.put("tag", hashtag)
									.put("limit", MISSKEY_HASHTAG_LIMIT)
							)
						} else {
							getStatusList(client, makeHashtagUrl(hashtag))
						}
						
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
									result = client.searchMsp(search_query, idOld?.toString())
									val jsonArray = result?.jsonArray
									if(jsonArray != null) {
										// max_id の更新
										idOld = EntityId.mayNull(
											TootApiClient.getMspMaxId(
												jsonArray,
												idOld?.toString()
											)
										)
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
							if(q.isEmpty() || idOld == null) {
								list_tmp = ArrayList()
								result = TootApiResult(context.getString(R.string.end_of_list))
							} else {
								result = client.searchTootsearch(search_query, idOld?.toLong())
								val jsonObject = result?.jsonObject
								if(jsonObject != null) {
									// max_id の更新
									idOld = EntityId.mayNull(
										TootApiClient.getTootsearchMaxId(
											jsonObject,
											idOld?.toLong()
										)
									)
									// リストデータの用意
									val search_result =
										TootStatus.parseListTootsearch(parser, jsonObject)
									list_tmp = addWithFilterStatus(list_tmp, search_result)
								}
							}
							result
						}
						
						else -> getStatusList(client, makeHomeTlUrl())
					}
				} finally {
					try {
						updateRelation(client, list_tmp, who_account, parser)
					} catch(ex : Throwable) {
						log.trace(ex)
					}
					ctClosed.set(true)
					runOnMainLooperDelayed(333L) {
						if(! isCancelled) fireShowColumnStatus()
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
					lastTask = null
					bRefreshLoading = false
					
					if(filterUpdated) {
						checkFiltersForListData(muted_word2)
					}
					
					val error = result.error
					if(error != null) {
						mRefreshLoadingError = error
						mRefreshLoadingErrorTime = SystemClock.elapsedRealtime()
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
					
					
					
					if(bBottom) {
						val changeList = listOf(
							AdapterChange(
								AdapterChangeType.RangeInsert,
								list_data.size,
								list_new.size
							)
						)
						list_data.addAll(list_new)
						fireShowContent(reason = "refresh updated bottom", changeList = changeList)
						
						// 新着が少しだけ見えるようにスクロール位置を移動する
						if(sp != null) {
							holder?.setScrollPosition(sp, 20f)
						}
					} else {
						
						val changeList = ArrayList<AdapterChange>()
						
						if(list_data.isNotEmpty() && list_data[0] is TootGap) {
							changeList.add(AdapterChange(AdapterChangeType.RangeRemove, 0, 1))
							list_data.removeAt(0)
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
						
						replaceConversationSummary(changeList, list_new, list_data)
						
						val added = list_new.size // may 0
						
						// 投稿後のリフレッシュなら当該投稿の位置を探す
						var status_index = - 1
						for(i in 0 until added) {
							val o = list_new[i]
							if(o is TootStatus && o.id == posted_status_id) {
								status_index = i
								break
							}
						}
						
						changeList.add(AdapterChange(AdapterChangeType.RangeInsert, 0, added))
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
					
					updateMisskeyCapture()
					
				} finally {
					fireShowColumnStatus()
					
					if(! bBottom) {
						bRefreshingTop = false
						resumeStreaming(false)
					}
				}
			}
		}
		this.lastTask = task
		task.executeOnExecutor(App1.task_executor)
		fireShowColumnStatus()
	}
	
	internal fun startGap(gap : TootGap?) {
		
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
		
		val task = @SuppressLint("StaticFieldLeak")
		object : ColumnTask(ColumnTaskType.GAP) {
			
			var max_id : EntityId? = gap.max_id
			var since_id : EntityId? = gap.since_id
			
			var list_tmp : ArrayList<TimelineItem>? = null
			
			var parser = TootParser(context, access_info, highlightTrie = highlight_trie)
			
			fun getAccountList(
				client : TootApiClient,
				path_base : String,
				misskeyParams : JSONObject? = null,
				misskeyCustomParser : (parser : TootParser, jsonArray : JSONArray) -> ArrayList<TootAccountRef> =
					{ parser, jsonArray -> parser.accountList(jsonArray) },
				misskeyArrayFinder : (jsonObject : JSONObject) -> JSONArray? = { null }
			
			) : TootApiResult? {
				
				@Suppress("NON_EXHAUSTIVE_WHEN")
				when(pagingType) {
					PagingType.Offset,
					PagingType.Cursor,
					PagingType.None -> {
						return TootApiResult("can't support gap")
					}
				}
				
				val params = misskeyParams ?: makeMisskeyBaseParameter(parser)
				val time_start = SystemClock.elapsedRealtime()
				val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
				list_tmp = ArrayList()
				
				var result : TootApiResult? = null
				
				if(isMisskey) {
					
					// missKeyではgapを下から読む
					var bHeadGap = false
					
					while(true) {
						
						if(isCancelled) {
							log.d("gap-account: cancelled.")
							break
						}
						
						if(result != null && SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
							log.d("gap-account: timeout. make gap.")
							bHeadGap = true
							break
						}
						
						since_id?.putMisskeySince(params)
						val r2 = client.request(path_base, params.toPostRequestBuilder())
						
						val jsonObject = r2?.jsonObject
						if(jsonObject != null) {
							r2.data = misskeyArrayFinder(jsonObject)
						}
						
						val jsonArray = r2?.jsonArray
						if(jsonArray == null) {
							log.d("gap-account: error. make gap.")
							if(result == null) result = r2
							bHeadGap = true
							break
						}
						result = r2
						
						val src = misskeyCustomParser(parser, jsonArray)
						if(src.isEmpty()) {
							log.d("gap-account: empty.")
							break
						}
						
						addAll(list_tmp, src)
						since_id = parseRange(result, src).second
					}
					if(isMisskey) {
						list_tmp?.sortBy { it.getOrderId() }
						list_tmp?.reverse()
					}
					if(bHeadGap) {
						addOneFirst(list_tmp, TootGap.mayNull(max_id, since_id))
					}
					
				} else {
					while(true) {
						
						if(isCancelled) {
							log.d("gap-account: cancelled.")
							break
						}
						
						if(result != null && SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
							log.d("gap-account: timeout. make gap.")
							// タイムアウト
							// 隙間が残る
							addOne(list_tmp, TootGap.mayNull(max_id, since_id))
							break
						}
						
						val path = "$path_base${delimiter}max_id=$max_id&since_id=$since_id"
						val r2 = client.request(path)
						
						val jsonArray = r2?.jsonArray
						if(jsonArray == null) {
							log.d("gap-account: error timeout. make gap.")
							
							if(result == null) result = r2
							
							// 隙間が残る
							addOne(list_tmp, TootGap.mayNull(max_id, since_id))
							break
						}
						result = r2
						val src = misskeyCustomParser(parser, jsonArray)
						
						if(src.isEmpty()) {
							log.d("gap-account: empty.")
							break
						}
						
						addAll(list_tmp, src)
						max_id = parseRange(result, src).first
					}
				}
				return result
			}
			
			fun getReportList(
				client : TootApiClient,
				path_base : String
			) : TootApiResult? {
				val time_start = SystemClock.elapsedRealtime()
				val params = makeMisskeyBaseParameter(parser)
				val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
				list_tmp = ArrayList()
				
				var result : TootApiResult? = null
				
				if(isMisskey) {
					var bHeadGap = false
					while(true) {
						if(isCancelled) {
							log.d("gap-report: cancelled.")
							break
						}
						
						if(result != null && SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
							log.d("gap-report: timeout. make gap.")
							bHeadGap = true
							break
						}
						
						since_id?.putMisskeySince(params)
						val r2 = client.request(path_base, params.toPostRequestBuilder())
						val jsonArray = r2?.jsonArray
						if(jsonArray == null) {
							log.d("gap-report: error or cancelled. make gap.")
							if(result == null) result = r2
							bHeadGap = true
							break
						}
						
						result = r2
						val src = parseList(::TootReport, jsonArray)
						if(src.isEmpty()) {
							log.d("gap-report: empty.")
							break
						}
						
						addAll(list_tmp, src)
						
						// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
						since_id = parseRange(result, src).second
					}
					
					// レポート一覧ってそもそもMisskey対応してないので、ここをどうするかは不明
					// 多分 sinceIDによるページングではないと思う
					if(isMisskey) {
						list_tmp?.sortBy { it.getOrderId() }
						list_tmp?.reverse()
					}
					
					if(bHeadGap) {
						addOneFirst(list_tmp, TootGap.mayNull(max_id, since_id))
					}
				} else {
					while(true) {
						if(isCancelled) {
							log.d("gap-report: cancelled.")
							break
						}
						
						if(result != null && SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
							log.d("gap-report: timeout. make gap.")
							// タイムアウト
							// 隙間が残る
							addOne(list_tmp, TootGap.mayNull(max_id, since_id))
							break
						}
						
						val path =
							path_base + delimiter + "max_id=" + max_id + "&since_id=" + since_id
						val r2 = client.request(path)
						val jsonArray = r2?.jsonArray
						if(jsonArray == null) {
							log.d("gap-report: error or cancelled. make gap.")
							if(result == null) result = r2
							// 隙間が残る
							addOne(list_tmp, TootGap.mayNull(max_id, since_id))
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
						max_id = parseRange(result, src).first
					}
				}
				return result
			}
			
			fun getNotificationList(client : TootApiClient) : TootApiResult? {
				val path_base = makeNotificationUrl()
				val params = makeMisskeyBaseParameter(parser)
				val time_start = SystemClock.elapsedRealtime()
				val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
				
				list_tmp = ArrayList()
				
				var result : TootApiResult? = null
				
				if(isMisskey) {
					var bHeadGap = false
					while(true) {
						if(isCancelled) {
							log.d("gap-notification: cancelled.")
							break
						}
						
						if(result != null && SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
							log.d("gap-notification: timeout. make gap.")
							bHeadGap = true
							break
						}
						
						since_id?.putMisskeySince(params)
						val r2 = client.request(path_base, params.toPostRequestBuilder())
						val jsonArray = r2?.jsonArray
						if(jsonArray == null) {
							// エラー
							log.d("gap-notification: error or response. make gap.")
							if(result == null) result = r2
							// 隙間が残る
							bHeadGap = true
							break
						}
						
						result = r2
						val src = parser.notificationList(jsonArray)
						
						if(src.isEmpty()) {
							log.d("gap-notification: empty.")
							break
						}
						
						// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
						since_id = parseRange(result, src).second
						
						addWithFilterNotification(list_tmp, src)
						
						PollingWorker.injectData(context, access_info, src)
					}
					
					if(isMisskey) {
						list_tmp?.sortBy { it.getOrderId() }
						list_tmp?.reverse()
					}
					
					if(bHeadGap) {
						addOneFirst(list_tmp, TootGap.mayNull(max_id, since_id))
					}
				} else {
					while(true) {
						if(isCancelled) {
							log.d("gap-notification: cancelled.")
							break
						}
						
						if(result != null && SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
							log.d("gap-notification: timeout. make gap.")
							// タイムアウト
							// 隙間が残る
							addOne(list_tmp, TootGap.mayNull(max_id, since_id))
							break
						}
						val path =
							path_base + delimiter + "max_id=" + max_id + "&since_id=" + since_id
						val r2 = client.request(path)
						val jsonArray = r2?.jsonArray
						if(jsonArray == null) {
							// エラー
							log.d("gap-notification: error or response. make gap.")
							
							if(result == null) result = r2
							
							// 隙間が残る
							addOne(list_tmp, TootGap.mayNull(max_id, since_id))
							break
						}
						
						result = r2
						val src = parser.notificationList(jsonArray)
						
						if(src.isEmpty()) {
							log.d("gap-notification: empty.")
							break
						}
						
						// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
						max_id = parseRange(result, src).first
						
						addWithFilterNotification(list_tmp, src)
						
						PollingWorker.injectData(context, access_info, src)
						
					}
				}
				
				return result
			}
			
			fun getStatusList(
				client : TootApiClient,
				path_base : String,
				misskeyParams : JSONObject? = null
				,
				misskeyCustomParser : (parser : TootParser, jsonArray : JSONArray) -> ArrayList<TootStatus> =
					{ parser, jsonArray -> parser.statusList(jsonArray) }
			) : TootApiResult? {
				
				val isMisskey = access_info.isMisskey
				
				val params = misskeyParams ?: makeMisskeyTimelineParameter(parser)
				
				val time_start = SystemClock.elapsedRealtime()
				
				val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
				
				list_tmp = ArrayList()
				
				var result : TootApiResult? = null
				if(isMisskey) {
					var bHeadGap = false
					while(true) {
						if(isCancelled) {
							log.d("gap-statuses: cancelled.")
							break
						}
						
						if(result != null && SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
							log.d("gap-statuses: timeout.")
							bHeadGap = true
							break
						}
						
						since_id?.putMisskeySince(params)
						val r2 = client.request(path_base, params.toPostRequestBuilder())
						
						val jsonArray = r2?.jsonArray
						if(jsonArray == null) {
							log.d("gap-statuses: error or cancelled. make gap.")
							
							// 成功データがない場合だけ、今回のエラーを返すようにする
							if(result == null) result = r2
							
							bHeadGap = true
							
							break
						}
						
						// 成功した場合はそれを返したい
						result = r2
						
						val src = misskeyCustomParser(parser, jsonArray)
						
						if(src.isEmpty()) {
							// 直前の取得でカラのデータが帰ってきたら終了
							log.d("gap-statuses: empty.")
							break
						}
						
						// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
						since_id = parseRange(result, src).second
						
						addWithFilterStatus(list_tmp, src)
					}
					
					if(isMisskey) {
						list_tmp?.sortBy { it.getOrderId() }
						list_tmp?.reverse()
					}
					
					if(bHeadGap) {
						addOneFirst(list_tmp, TootGap.mayNull(max_id, since_id))
					}
					
				} else {
					var bLastGap = false
					while(true) {
						if(isCancelled) {
							log.d("gap-statuses: cancelled.")
							break
						}
						
						if(result != null && SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
							log.d("gap-statuses: timeout.")
							// タイムアウト
							bLastGap = true
							break
						}
						
						val path = "${path_base}${delimiter}max_id=${max_id}&since_id=${since_id}"
						val r2 = client.request(path)
						
						val jsonArray = r2?.jsonArray
						if(jsonArray == null) {
							log.d("gap-statuses: error or cancelled. make gap.")
							
							// 成功データがない場合だけ、今回のエラーを返すようにする
							if(result == null) result = r2
							
							bLastGap = true
							
							break
						}
						
						// 成功した場合はそれを返したい
						result = r2
						
						val src = misskeyCustomParser(parser, jsonArray)
						
						if(src.isEmpty()) {
							// 直前の取得でカラのデータが帰ってきたら終了
							log.d("gap-statuses: empty.")
							break
						}
						
						// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
						max_id = parseRange(result, src).first
						
						addWithFilterStatus(list_tmp, src)
					}
					if(bLastGap) {
						addOne(list_tmp, TootGap.mayNull(max_id, since_id))
					}
				}
				return result
			}
			
			fun getConversationSummaryList(
				client : TootApiClient,
				path_base : String,
				misskeyParams : JSONObject? = null
				,
				misskeyCustomParser : (parser : TootParser, jsonArray : JSONArray) -> ArrayList<TootConversationSummary> =
					{ parser, jsonArray -> parseList(::TootConversationSummary, parser, jsonArray) }
			) : TootApiResult? {
				
				val isMisskey = access_info.isMisskey
				
				val params = misskeyParams ?: makeMisskeyTimelineParameter(parser)
				
				val time_start = SystemClock.elapsedRealtime()
				
				val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
				
				list_tmp = ArrayList()
				
				var result : TootApiResult? = null
				if(isMisskey) {
					var bHeadGap = false
					while(true) {
						if(isCancelled) {
							log.d("gap-statuses: cancelled.")
							break
						}
						
						if(result != null && SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
							log.d("gap-statuses: timeout.")
							bHeadGap = true
							break
						}
						
						since_id?.putMisskeySince(params)
						val r2 = client.request(path_base, params.toPostRequestBuilder())
						
						val jsonArray = r2?.jsonArray
						if(jsonArray == null) {
							log.d("gap-statuses: error or cancelled. make gap.")
							
							// 成功データがない場合だけ、今回のエラーを返すようにする
							if(result == null) result = r2
							
							bHeadGap = true
							
							break
						}
						
						// 成功した場合はそれを返したい
						result = r2
						
						val src = misskeyCustomParser(parser, jsonArray)
						
						if(src.isEmpty()) {
							// 直前の取得でカラのデータが帰ってきたら終了
							log.d("gap-statuses: empty.")
							break
						}
						
						// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
						since_id = parseRange(result, src).second
						
						addWithFilterConversationSummary(list_tmp, src)
					}
					
					if(isMisskey) {
						list_tmp?.sortBy { it.getOrderId() }
						list_tmp?.reverse()
					}
					
					if(bHeadGap) {
						addOneFirst(list_tmp, TootGap.mayNull(max_id, since_id))
					}
					
				} else {
					var bLastGap = false
					while(true) {
						if(isCancelled) {
							log.d("gap-statuses: cancelled.")
							break
						}
						
						if(result != null && SystemClock.elapsedRealtime() - time_start > LOOP_TIMEOUT) {
							log.d("gap-statuses: timeout.")
							// タイムアウト
							bLastGap = true
							break
						}
						
						val path = "${path_base}${delimiter}max_id=${max_id}&since_id=${since_id}"
						val r2 = client.request(path)
						
						val jsonArray = r2?.jsonArray
						if(jsonArray == null) {
							log.d("gap-statuses: error or cancelled. make gap.")
							
							// 成功データがない場合だけ、今回のエラーを返すようにする
							if(result == null) result = r2
							
							bLastGap = true
							
							break
						}
						
						// 成功した場合はそれを返したい
						result = r2
						
						val src = misskeyCustomParser(parser, jsonArray)
						
						if(src.isEmpty()) {
							// 直前の取得でカラのデータが帰ってきたら終了
							log.d("gap-statuses: empty.")
							break
						}
						
						// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
						max_id = parseRange(result, src).first
						
						addWithFilterConversationSummary(list_tmp, src)
					}
					if(bLastGap) {
						addOne(list_tmp, TootGap.mayNull(max_id, since_id))
					}
				}
				return result
			}
			
			override fun doInBackground(vararg unused : Void) : TootApiResult? {
				ctStarted.set(true)
				
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
						
						TYPE_LOCAL -> getStatusList(client, makePublicLocalUrl())
						TYPE_MISSKEY_HYBRID -> getStatusList(client, makeMisskeyHybridTlUrl())
						
						TYPE_FEDERATE -> getStatusList(client, makePublicFederateUrl())
						
						TYPE_LIST_TL -> if(isMisskey) {
							val params = makeMisskeyTimelineParameter(parser)
								.put("listId", profile_id)
							getStatusList(client, makeListTlUrl(), misskeyParams = params)
						} else {
							getStatusList(client, makeListTlUrl())
						}
						
						TYPE_FAVOURITES -> if(isMisskey) {
							getStatusList(
								client,
								PATH_MISSKEY_FAVORITES
								, misskeyParams = makeMisskeyTimelineParameter(parser)
								, misskeyCustomParser = misskeyCustomParserFavorites
							)
						} else {
							getStatusList(client, PATH_FAVOURITES)
							
						}
						
						TYPE_REPORTS -> getReportList(client, PATH_REPORTS)
						
						TYPE_NOTIFICATIONS -> getNotificationList(client)
						
						TYPE_HASHTAG -> if(isMisskey) {
							getStatusList(
								client
								, makeHashtagUrl(hashtag)
								, misskeyParams = makeMisskeyTimelineParameter(parser)
									.put("tag", hashtag)
									.put("limit", MISSKEY_HASHTAG_LIMIT)
							)
						} else {
							getStatusList(client, makeHashtagUrl(hashtag))
						}
						
						TYPE_BOOSTED_BY -> getAccountList(
							client,
							String.format(Locale.JAPAN, PATH_BOOSTED_BY, status_id)
						)
						
						TYPE_FAVOURITED_BY -> getAccountList(
							client,
							String.format(Locale.JAPAN, PATH_FAVOURITED_BY, status_id)
						)
						
						TYPE_MUTES -> if(isMisskey) {
							getAccountList(
								client
								, PATH_MISSKEY_MUTES
								, misskeyParams = access_info.putMisskeyApiToken(JSONObject())
								
								, misskeyArrayFinder = misskeyArrayFinderUsers
							)
						} else {
							getAccountList(client, PATH_MUTES)
						}
						
						TYPE_BLOCKS -> if(isMisskey) {
							pagingType = PagingType.Default
							val params = access_info.putMisskeyApiToken(JSONObject())
							getAccountList(
								client,
								"/api/blocking/list",
								misskeyParams = params,
								misskeyCustomParser = misskeyCustomParserBlocks
							)
						} else {
							getAccountList(client, PATH_BLOCKS)
						}
						
						TYPE_FOLLOW_REQUESTS -> if(isMisskey) {
							getAccountList(
								client
								, PATH_MISSKEY_FOLLOW_REQUESTS
								, misskeyParams = access_info.putMisskeyApiToken(JSONObject())
								, misskeyCustomParser = misskeyCustomParserFollowRequest
							)
						} else {
							getAccountList(
								client
								, PATH_FOLLOW_REQUESTS
							)
						}
						TYPE_FOLLOW_SUGGESTION -> if(isMisskey) {
							getAccountList(
								client
								, PATH_MISSKEY_FOLLOW_SUGGESTION
								, misskeyParams = access_info.putMisskeyApiToken(JSONObject())
							)
						} else {
							getAccountList(
								client
								, PATH_FOLLOW_SUGGESTION
							)
						}
						
						TYPE_ENDORSEMENT -> getAccountList(client, PATH_ENDORSEMENT)
						
						TYPE_PROFILE
						-> when(profile_tab) {
							
							TAB_FOLLOWING -> if(isMisskey) {
								getAccountList(
									client
									, PATH_MISSKEY_PROFILE_FOLLOWING
									, misskeyParams = makeMisskeyParamsUserId(parser)
								)
							} else {
								getAccountList(
									client,
									String.format(Locale.JAPAN, PATH_ACCOUNT_FOLLOWING, profile_id)
								)
							}
							
							TAB_FOLLOWERS -> if(isMisskey) {
								getAccountList(
									client
									, PATH_MISSKEY_PROFILE_FOLLOWERS
									, misskeyParams = makeMisskeyParamsUserId(parser)
								)
								
							} else {
								getAccountList(
									client,
									String.format(Locale.JAPAN, PATH_ACCOUNT_FOLLOWERS, profile_id)
								)
							}
							
							else -> if(isMisskey) {
								getStatusList(
									client
									, PATH_MISSKEY_PROFILE_STATUSES
									, misskeyParams = makeMisskeyParamsProfileStatuses(parser)
								)
							} else {
								if(access_info.isPseudo) {
									client.request(PATH_INSTANCE)
								} else {
									var s =
										String.format(
											Locale.JAPAN,
											PATH_ACCOUNT_STATUSES,
											profile_id
										)
									if(with_attachment && ! with_highlight) s += "&only_media=1"
									getStatusList(client, s)
								}
							}
						}
						
						TYPE_DIRECT_MESSAGES -> if(useConversationSummarys) {
							// try 2.6.0 new API https://github.com/tootsuite/mastodon/pull/8832
							getConversationSummaryList(client, PATH_DIRECT_MESSAGES2)
						} else {
							// fallback to old api
							getStatusList(client, PATH_DIRECT_MESSAGES)
						}
						
						else -> getStatusList(client, makeHomeTlUrl())
					}
				} finally {
					try {
						updateRelation(client, list_tmp, who_account, parser)
					} catch(ex : Throwable) {
						log.trace(ex)
					}
					
					ctClosed.set(true)
					runOnMainLooperDelayed(333L) {
						if(! isCancelled) fireShowColumnStatus()
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
					
					lastTask = null
					bRefreshLoading = false
					
					val error = result.error
					if(error != null) {
						mRefreshLoadingError = error
						fireShowContent(reason = "gap error", changeList = ArrayList())
						return
					}
					
					val list_tmp = this.list_tmp
					if(list_tmp == null) {
						fireShowContent(reason = "gap list_tmp is null", changeList = ArrayList())
						return
					}
					
					val list_new = duplicate_map.filterDuplicate(list_tmp)
					// 0個でもギャップを消すために以下の処理を続ける
					
					val changeList = ArrayList<AdapterChange>()
					
					replaceConversationSummary(changeList, list_new, list_data)
					
					val added = list_new.size // may 0
					
					val position = list_data.indexOf(gap)
					if(position == - 1) {
						log.d("gap not found..")
						fireShowContent(reason = "gap not found", changeList = ArrayList())
						return
					}
					
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
					
					list_data.removeAt(position)
					list_data.addAll(position, list_new)
					
					changeList.add(AdapterChange(AdapterChangeType.RangeRemove, position))
					if(added > 0) {
						changeList.add(
							AdapterChange(
								AdapterChangeType.RangeInsert,
								position,
								added
							)
						)
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
					
					updateMisskeyCapture()
				} finally {
					fireShowColumnStatus()
				}
			}
		}
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
			Column.TYPE_PROFILE -> HeaderType.Profile
			Column.TYPE_SEARCH -> HeaderType.Search
			Column.TYPE_SEARCH_MSP -> HeaderType.Search
			Column.TYPE_SEARCH_TS -> HeaderType.Search
			Column.TYPE_INSTANCE_INFORMATION -> HeaderType.Instance
			Column.TYPE_KEYWORD_FILTER -> HeaderType.Filter
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
			startRefresh(true, false)
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
		TYPE_NOTIFICATIONS -> TootFilter.CONTEXT_NOTIFICATIONS
		TYPE_CONVERSATION -> TootFilter.CONTEXT_THREAD
		TYPE_LOCAL, TYPE_FEDERATE, TYPE_HASHTAG, TYPE_PROFILE, TYPE_SEARCH -> TootFilter.CONTEXT_PUBLIC
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
			TYPE_HOME, TYPE_MISSKEY_HYBRID, TYPE_PROFILE, TYPE_NOTIFICATIONS, TYPE_LIST_TL -> true
			TYPE_LOCAL, TYPE_FEDERATE, TYPE_HASHTAG, TYPE_SEARCH -> isMisskey
			TYPE_CONVERSATION, TYPE_DIRECT_MESSAGES -> isMisskey
			else -> false
		}
	}
	
	// カラム設定に「返信を表示しない」ボタンを含めるなら真
	fun canFilterReply() : Boolean {
		return when(column_type) {
			TYPE_HOME, TYPE_MISSKEY_HYBRID, TYPE_PROFILE, TYPE_NOTIFICATIONS, TYPE_LIST_TL, TYPE_DIRECT_MESSAGES -> true
			TYPE_LOCAL, TYPE_FEDERATE, TYPE_HASHTAG, TYPE_SEARCH -> isMisskey
			else -> false
		}
	}
	
	fun canFilterNormalToot() : Boolean {
		return when(column_type) {
			TYPE_HOME, TYPE_MISSKEY_HYBRID, TYPE_LIST_TL -> true
			TYPE_LOCAL, TYPE_FEDERATE, TYPE_HASHTAG, TYPE_SEARCH -> isMisskey
			else -> false
		}
	}
	
	internal fun canAutoRefresh() : Boolean {
		return streamPath != null
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
			PagingType.Default -> idRecent != null
			else -> false
		}
	}
	
	// データ的にリフレッシュを許容するかどうか
	private fun canRefreshBottom() : Boolean {
		return when(pagingType) {
			PagingType.Default, PagingType.Cursor -> idOld != null
			PagingType.None -> false
			PagingType.Offset -> true
		}
	}
	
	internal fun canSpeech() : Boolean {
		return canStreaming() && column_type != TYPE_NOTIFICATIONS
	}
	
	internal fun canStreaming() = when {
		access_info.isNA -> false
		access_info.isMisskey -> streamPath != null
		access_info.isPseudo -> isPublicStream
		else -> streamPath != null
	}
	
	private val streamCallback = object : StreamReader.StreamCallback {
		
		override fun onListeningStateChanged() {
			if(is_dispose.get()) return
			runOnMainLooper {
				when {
					is_dispose.get() -> {
					
					}
					
					else -> {
						fireShowColumnStatus()
						updateMisskeyCapture()
					}
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
				if(column_type != TYPE_NOTIFICATIONS) return
				if(isFiltered(item)) return
			} else if(item is TootStatus) {
				if(column_type == TYPE_NOTIFICATIONS) return
				
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
			if(column_type == TYPE_NOTIFICATIONS) {
				val list = ArrayList<TootNotification>()
				for(o in list_new) {
					if(o is TootNotification) {
						list.add(o)
					}
				}
				if(! list.isEmpty()) {
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
			
			if(tmpNewMax is EntityIdString && ! isMisskey) {
				log.e("EntityId should be Long for non-misskey column! columnType=$column_type")
			} else if(tmpRecent is EntityIdString && tmpNewMax is EntityIdLong) {
				log.e("EntityId type mismatch! recent=String,newMax=Long,columnType=$column_type")
			} else if(tmpRecent is EntityIdLong && tmpNewMax is EntityIdString) {
				log.e("EntityId type mismatch! recent=Long,newMax=String,columnType=$column_type")
			} else if(tmpNewMax != null && (tmpRecent?.compareTo(tmpNewMax) ?: - 1) == - 1) {
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
						holder.setScrollPosition(ScrollPosition(0, 0))
					}

					restore_idx < - 1 ->{
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
				if(scroll_save == null || scroll_save.isHead ) {
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
	
	private fun updateMisskeyCapture() {
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
	
	private fun replaceConversationSummary(
		changeList : ArrayList<AdapterChange>,
		list_new : ArrayList<TimelineItem>,
		list_data : BucketList<TimelineItem>
	) {
		
		val newMap = HashMap<EntityId, TootConversationSummary>()
		
		for(o in list_new) {
			if(o is TootConversationSummary) newMap[o.id] = o
		}
		
		if( list_data.isEmpty() || newMap.isEmpty()) return
		
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
		while(it.hasNext()){
			val o = it.next() as? TootConversationSummary ?: continue
			if(removeSet.contains(o.id)) it.remove()
		}
	}
	
	private fun makeMisskeyBaseParameter(parser : TootParser?) : JSONObject =
		access_info
			.putMisskeyApiToken(JSONObject())
			.apply {
				if(access_info.isMisskey) {
					if(parser != null) parser.serviceType = ServiceType.MISSKEY
					put("limit", 100)
				}
			}
	
	private fun JSONObject.putMisskeyParamsTimeline() : JSONObject {
		if(with_attachment && ! with_highlight) {
			put("mediaOnly", true)
			put("withMedia", true)
			put("withFiles", true)
			put("media", true)
		}
		return this
	}
	
	private fun makeMisskeyParamsUserId(parser : TootParser) : JSONObject =
		makeMisskeyBaseParameter(parser).put("userId", profile_id.toString())
	
	private fun makeMisskeyTimelineParameter(parser : TootParser) =
		makeMisskeyBaseParameter(parser).putMisskeyParamsTimeline()
	
	private fun makeMisskeyParamsProfileStatuses(parser : TootParser) =
		makeMisskeyParamsUserId(parser)
			.putMisskeyParamsTimeline()
			.put("includeReplies", true)
	
	private fun makePublicLocalUrl() : String {
		return when {
			access_info.isMisskey -> "/api/notes/local-timeline"
			with_attachment -> "$PATH_LOCAL&only_media=true" // mastodon 2.3 or later
			else -> PATH_LOCAL
		}
	}
	
	private fun makeMisskeyHybridTlUrl() : String {
		return when {
			access_info.isMisskey -> "/api/notes/hybrid-timeline"
			with_attachment -> "$PATH_LOCAL&only_media=true" // mastodon 2.3 or later
			else -> PATH_LOCAL
		}
	}
	
	private fun makePublicFederateUrl() : String {
		return when {
			access_info.isMisskey -> "/api/notes/global-timeline"
			with_attachment -> "$PATH_TL_FEDERATE&only_media=true"
			else -> PATH_TL_FEDERATE
		}
	}
	
	private fun makeHomeTlUrl() : String {
		return when {
			access_info.isMisskey -> "/api/notes/timeline"
			with_attachment -> "$PATH_HOME&only_media=true"
			else -> PATH_HOME
		}
	}
	
	private fun makeNotificationUrl() : String {
		return when {
			access_info.isMisskey -> "/api/i/notifications"
			
			else -> {
				val sb = StringBuilder(PATH_NOTIFICATIONS) // always contain "?limit=XX"
				if(dont_show_favourite) sb.append("&exclude_types[]=favourite")
				if(dont_show_boost) sb.append("&exclude_types[]=reblog")
				if(dont_show_follow) sb.append("&exclude_types[]=follow")
				if(dont_show_reply) sb.append("&exclude_types[]=mention")
				// reaction,voteはmastodonにはない
				sb.toString()
			}
		}
	}
	
	private fun makeListTlUrl() : String {
		return if(isMisskey) {
			"/api/notes/user-list-timeline"
		} else {
			String.format(Locale.JAPAN, PATH_LIST_TL, profile_id)
		}
	}
	
	private fun makeHashtagUrl(
		hashtag : String // 先頭の#を含まない
	) : String {
		return if(isMisskey) {
			"/api/notes/search_by_tag"
		} else {
			val sb = StringBuilder("/api/v1/timelines/tag/")
				.append(hashtag.encodePercent())
				.append("?limit=")
				.append(READ_LIMIT)
			if(with_attachment) sb.append("&only_media=true")
			if(instance_local) sb.append("&local=true")
			sb.toString()
		}
	}
	
	private fun loadFilter2(client : TootApiClient) : ArrayList<TootFilter>? {
		if(access_info.isPseudo || access_info.isMisskey) return null
		val column_context = getFilterContext()
		if(column_context == 0) return null
		val result = client.request(PATH_FILTERS)
		
		val jsonArray = result?.jsonArray ?: return null
		return TootFilter.parseList(jsonArray)
	}
	
	private fun encodeFilterTree(filterList : ArrayList<TootFilter>?) : WordTrieTree? {
		val column_context = getFilterContext()
		if(column_context == 0 || filterList == null) return null
		val tree = WordTrieTree()
		for(filter in filterList) {
			if((filter.context and column_context) != 0) {
				tree.add(
					filter.phrase, validator = when(filter.whole_word) {
						true -> WordTrieTree.WORD_VALIDATOR
						else -> WordTrieTree.EMPTY_VALIDATOR
					}
				)
			}
		}
		return tree
	}
	
	private fun checkFiltersForListData(tree : WordTrieTree?) {
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
	
	val isMisskey : Boolean = access_info.isMisskey
	
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
		ViewCompat.setBackground(
			view,
			Styler.getAdaptiveRippleDrawable(
				getHeaderBackgroundColor(),
				getHeaderNameColor()
			)
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
