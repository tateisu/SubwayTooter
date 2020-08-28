package jp.juggler.subwaytooter

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import jp.juggler.subwaytooter.action.Action_Account
import jp.juggler.subwaytooter.action.Action_App
import jp.juggler.subwaytooter.action.Action_Instance
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.VersionString
import jp.juggler.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.anko.backgroundColor
import java.lang.ref.WeakReference

class SideMenuAdapter(
	private val actMain : ActMain,
	val handler : Handler,
	navigationView : ViewGroup,
	private val drawer : DrawerLayout
) : BaseAdapter() {
	
	companion object {
		private val itemTypeCount = ItemType.values().size
		
		private var lastVersionView : WeakReference<TextView>? = null
		
		private var versionRow = SpannableStringBuilder("")
		private fun getVersionRow() = versionRow
		
		private var releaseInfo : JsonObject? = null
		
		private fun checkVersion(appContext : Context, handler : Handler) {
			val currentVersion = try {
				val pInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
				pInfo.versionName
			} catch(ex : PackageManager.NameNotFoundException) {
				"??"
			}
			
			versionRow = SpannableStringBuilder().apply {
				append(
					appContext.getString(
						R.string.app_name_with_version,
						appContext.getString(R.string.app_name),
						currentVersion
					)
				)
			}
			
			fun afterGet() {
				
				versionRow = SpannableStringBuilder().apply {
					
					append(
						appContext.getString(
							R.string.app_name_with_version,
							appContext.getString(R.string.app_name),
							currentVersion
						)
					)
					val newRelease = when(Pref.bpCheckBetaVersion(Pref.pref(appContext))){
						false -> releaseInfo ?.jsonObject("stable")
						else -> releaseInfo ?.jsonObject("beta")
					}
					
					val newVersion = newRelease
						?.string("name")
						?.replace("""version\s*""".toRegex(RegexOption.IGNORE_CASE), "")
						?.trim()
					
					if( newVersion?.isNotEmpty() != true || VersionString(currentVersion) >= VersionString(newVersion) ) {
						val url =  "https://github.com/tateisu/SubwayTooter/releases"
						append("\n")
						val start = length
						append(appContext.getString(R.string.release_note))
						setSpan(
							object:ClickableSpan() {
								override fun onClick(widget : View) {
									App1.openBrowser(widget.activity as ActMain, url)
								}
								
								override fun updateDrawState(ds : TextPaint) {
									super.updateDrawState(ds)
									ds.isUnderlineText = false
								}
							},
							start, length,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
						)
					} else {
						
						append("\n")
						var start = length
						append(
							appContext.getString(
								R.string.new_version_available,
								newVersion
							)
						)
						setSpan(
							ForegroundColorSpan(
								getAttributeColor(
									appContext,
									R.attr.colorRegexFilterError
								)
							),
							start, length,
							Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
						)
						
						newRelease.string("html_url")?.let{url->
							append("\n")
							start = length
							append(appContext.getString(R.string.release_note_with_assets))
							setSpan(
								object:ClickableSpan() {
									override fun onClick(widget : View) {
										App1.openBrowser(widget.activity as ActMain, url)
									}
									
									override fun updateDrawState(ds : TextPaint) {
										super.updateDrawState(ds)
										ds.isUnderlineText = false
									}
								},
								start, length,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
							)
						}
					}
				}
				handler.post { lastVersionView?.get()?.let { it.text = versionRow } }
			}
			
			val lastUpdated = releaseInfo?.string("updated_at")?.let { TootStatus.parseTime(it) }
			if(lastUpdated != null && System.currentTimeMillis() - lastUpdated < 86400000L) {
				afterGet()
			} else {
				GlobalScope.launch(Dispatchers.IO) {
					val json =
						App1.getHttpCached("https://mastodon-msg.juggler.jp/appVersion/appVersion.json")
							?.decodeUTF8()?.decodeJsonObject()
					if(json != null) {
						releaseInfo = json
						afterGet()
					}
				}
			}
		}
		
	}
	
	private enum class ItemType(val id : Int) {
		IT_NORMAL(0),
		IT_GROUP_HEADER(1),
		IT_DIVIDER(2),
		IT_VERSION(3)
	}
	
	private class Item(
		val title : Int = 0,
		val icon : Int = 0,
		val action : ActMain.() -> Unit = {}
	) {
		
		internal val itemType : ItemType
			get() = when {
				title == 0 -> ItemType.IT_DIVIDER
				title == 1 -> ItemType.IT_VERSION
				icon == 0 -> ItemType.IT_GROUP_HEADER
				else -> ItemType.IT_NORMAL
			}
	}
	
	/*
		no title => section divider
		else no icon => section header with title
		else => menu item with icon and title
	*/
	
	private val list = arrayOf(
		
		Item(icon = R.drawable.ic_info, title = 1) ,
		
		Item(),
		Item(title = R.string.account),
		
		Item(title = R.string.account_add, icon = R.drawable.ic_account_add) {
			Action_Account.add(this)
		},
		
		Item(icon = R.drawable.ic_settings, title = R.string.account_setting) {
			Action_Account.setting(this)
		},
		
		Item(),
		Item(title = R.string.column),
		
		Item(icon = R.drawable.ic_list_numbered, title = R.string.column_list) {
			Action_App.columnList(this)
		},
		
		Item(icon = R.drawable.ic_close, title = R.string.close_all_columns) {
			closeColumnAll()
		},
		
		Item(icon = R.drawable.ic_home, title = R.string.home) {
			Action_Account.timeline(this, defaultInsertPosition, ColumnType.HOME)
		},
		
		Item(icon = R.drawable.ic_announcement, title = R.string.notifications) {
			Action_Account.timeline(this, defaultInsertPosition, ColumnType.NOTIFICATIONS)
		},
		
		Item(icon = R.drawable.ic_mail, title = R.string.direct_messages) {
			Action_Account.timeline(this, defaultInsertPosition, ColumnType.DIRECT_MESSAGES)
		},
		
		Item(icon = R.drawable.ic_share, title = R.string.misskey_hybrid_timeline_long) {
			Action_Account.timeline(this, defaultInsertPosition, ColumnType.MISSKEY_HYBRID)
		},
		
		Item(icon = R.drawable.ic_run, title = R.string.local_timeline) {
			Action_Account.timeline(this, defaultInsertPosition, ColumnType.LOCAL)
		},
		
		Item(icon = R.drawable.ic_bike, title = R.string.federate_timeline) {
			Action_Account.timeline(this, defaultInsertPosition, ColumnType.FEDERATE)
		},
		
		Item(icon = R.drawable.ic_list_list, title = R.string.lists) {
			Action_Account.timeline(this, defaultInsertPosition, ColumnType.LIST_LIST)
		},
		
		Item(icon = R.drawable.ic_satellite, title = R.string.antenna_list_misskey) {
			Action_Account.timeline(this, defaultInsertPosition, ColumnType.MISSKEY_ANTENNA_LIST)
		},
		
		Item(icon = R.drawable.ic_search, title = R.string.search) {
			Action_Account.timeline(
				this,
				defaultInsertPosition,
				ColumnType.SEARCH,
				args = arrayOf("", false)
			)
		},
		
		Item(icon = R.drawable.ic_hashtag, title = R.string.trend_tag) {
			Action_Account.timeline(this, defaultInsertPosition, ColumnType.TREND_TAG)
		},
		
		Item(icon = R.drawable.ic_star, title = R.string.favourites) {
			Action_Account.timeline(this, defaultInsertPosition, ColumnType.FAVOURITES)
		},
		
		Item(icon = R.drawable.ic_bookmark, title = R.string.bookmarks) {
			Action_Account.timeline(this, defaultInsertPosition, ColumnType.BOOKMARKS)
		},
		
		Item(icon = R.drawable.ic_account_box, title = R.string.profile) {
			Action_Account.timeline(this, defaultInsertPosition, ColumnType.PROFILE)
		},
		
		Item(icon = R.drawable.ic_follow_wait, title = R.string.follow_requests) {
			Action_Account.timeline(this, defaultInsertPosition, ColumnType.FOLLOW_REQUESTS)
		},
		
		Item(icon = R.drawable.ic_follow_plus, title = R.string.follow_suggestion) {
			Action_Account.timeline(this, defaultInsertPosition, ColumnType.FOLLOW_SUGGESTION)
		},
		
		Item(icon = R.drawable.ic_follow_plus, title = R.string.endorse_set) {
			Action_Account.timeline(this, defaultInsertPosition, ColumnType.ENDORSEMENT)
		},
		
		Item(icon = R.drawable.ic_follow_plus, title = R.string.profile_directory) {
			Action_Instance.profileDirectoryFromSideMenu(this)
		},
		
		Item(icon = R.drawable.ic_volume_off, title = R.string.muted_users) {
			Action_Account.timeline(this, defaultInsertPosition, ColumnType.MUTES)
		},
		
		Item(icon = R.drawable.ic_block, title = R.string.blocked_users) {
			Action_Account.timeline(this, defaultInsertPosition, ColumnType.BLOCKS)
		},
		
		Item(icon = R.drawable.ic_volume_off, title = R.string.keyword_filters) {
			Action_Account.timeline(this, defaultInsertPosition, ColumnType.KEYWORD_FILTER)
		},
		
		Item(icon = R.drawable.ic_cloud_off, title = R.string.blocked_domains) {
			Action_Account.timeline(this, defaultInsertPosition, ColumnType.DOMAIN_BLOCKS)
		},
		
		Item(icon = R.drawable.ic_timer, title = R.string.scheduled_status_list) {
			Action_Account.timeline(this, defaultInsertPosition, ColumnType.SCHEDULED_STATUS)
		},
		
		Item(),
		Item(title = R.string.toot_search),
		
		Item(icon = R.drawable.ic_search, title = R.string.tootsearch) {
			addColumn(defaultInsertPosition, SavedAccount.na, ColumnType.SEARCH_TS, "")
		},
		
		Item(),
		Item(title = R.string.setting),
		
		Item(icon = R.drawable.ic_settings, title = R.string.app_setting) {
			ActAppSetting.open(this, ActMain.REQUEST_CODE_APP_SETTING)
		},
		
		Item(icon = R.drawable.ic_settings, title = R.string.highlight_word) {
			startActivity(Intent(this, ActHighlightWordList::class.java))
		},
		
		Item(icon = R.drawable.ic_volume_off, title = R.string.muted_app) {
			startActivity(Intent(this, ActMutedApp::class.java))
		},
		
		Item(icon = R.drawable.ic_volume_off, title = R.string.muted_word) {
			startActivity(Intent(this, ActMutedWord::class.java))
		},
		
		Item(icon = R.drawable.ic_volume_off, title = R.string.fav_muted_user) {
			startActivity(Intent(this, ActFavMute::class.java))
		},
		
		Item(
			icon = R.drawable.ic_volume_off,
			title = R.string.muted_users_from_pseudo_account
		) {
			startActivity(Intent(this, ActMutedPseudoAccount::class.java))
		},
		
		Item(icon = R.drawable.ic_info, title = R.string.app_about) {
			startActivityForResult(
				Intent(this, ActAbout::class.java),
				ActMain.REQUEST_APP_ABOUT
			)
		},
		
		Item(icon = R.drawable.ic_info, title = R.string.oss_license) {
			startActivity(Intent(this, ActOSSLicense::class.java))
		},
		
		Item(icon = R.drawable.ic_hot_tub, title = R.string.app_exit) {
			finish()
		}
	)
	
	private val iconColor = getAttributeColor(actMain, R.attr.colorTimeSmall)
	
	override fun getCount() : Int = list.size
	override fun getItem(position : Int) : Any = list[position]
	override fun getItemId(position : Int) : Long = 0L
	
	override fun getViewTypeCount() : Int = itemTypeCount
	override fun getItemViewType(position : Int) : Int = list[position].itemType.id
	
	private inline fun <reified T : View> viewOrInflate(
		view : View?,
		parent : ViewGroup?,
		resId : Int
	) : T =
		(view ?: actMain.layoutInflater.inflate(resId, parent, false))
			as? T ?: error("invalid view type! ${T::class.java.simpleName}")
	
	override fun getView(position : Int, view : View?, parent : ViewGroup?) : View =
		list[position].run {
			when(itemType) {
				ItemType.IT_DIVIDER ->
					viewOrInflate(view, parent, R.layout.lv_sidemenu_separator)
				ItemType.IT_GROUP_HEADER ->
					viewOrInflate<TextView>(view, parent, R.layout.lv_sidemenu_group).apply {
						text = actMain.getString(title)
					}
				ItemType.IT_NORMAL ->
					viewOrInflate<TextView>(view, parent, R.layout.lv_sidemenu_item).apply {
						isAllCaps = false
						text = actMain.getString(title)
						val drawable = createColoredDrawable(actMain, icon, iconColor, 1f)
						setCompoundDrawablesRelativeWithIntrinsicBounds(
							drawable,
							null,
							null,
							null
						)
						
						setOnClickListener {
							action(actMain)
							drawer.closeDrawer(GravityCompat.START)
						}
					}
				ItemType.IT_VERSION ->
					viewOrInflate<TextView>(view, parent, R.layout.lv_sidemenu_item).apply {
						lastVersionView = WeakReference(this)
						movementMethod = LinkMovementMethod.getInstance();
						textSize = 18f
						isAllCaps = false
						background= null
						//						val drawable = createColoredDrawable(actMain, icon, iconColor, 1f)
						//						setCompoundDrawablesRelativeWithIntrinsicBounds(
						//							drawable,
						//							null,
						//							null,
						//							null
						//						)

						text = getVersionRow()
					}
			}
		}
	
	init {
		checkVersion(actMain.applicationContext, handler)
		
		ListView(actMain).apply {
			adapter = this@SideMenuAdapter
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT
			)
			backgroundColor = getAttributeColor(actMain, R.attr.colorWindowBackground)
			selector = StateListDrawable()
			divider = null
			dividerHeight = 0
			
			val pad_tb = (actMain.density * 12f + 0.5f).toInt()
			setPadding(0, pad_tb, 0, pad_tb)
			clipToPadding = false
			scrollBarStyle = ListView.SCROLLBARS_OUTSIDE_OVERLAY
			
			navigationView.addView(this)
		}
		
	}
}
