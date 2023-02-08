package jp.juggler.subwaytooter.actmain

import android.content.Context
import android.content.Intent
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Handler
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.action.*
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.dialog.pickAccount
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.pref.PrefDevice.Companion.PUSH_DISTRIBUTOR_NONE
import jp.juggler.subwaytooter.pref.PrefS
import jp.juggler.subwaytooter.pref.prefDevice
import jp.juggler.subwaytooter.push.fcmHandler
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.accountListCanSeeMyReactions
import jp.juggler.subwaytooter.util.VersionString
import jp.juggler.subwaytooter.util.openBrowser
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.coroutine.launchIO
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.decodeJsonObject
import jp.juggler.util.data.decodeUTF8
import jp.juggler.util.data.notEmpty
import jp.juggler.util.getPackageInfoCompat
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.ui.activity
import jp.juggler.util.ui.attrColor
import jp.juggler.util.ui.createColoredDrawable
import kotlinx.coroutines.withContext
import org.jetbrains.anko.backgroundColor
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class SideMenuAdapter(
    private val actMain: ActMain,
    val handler: Handler,
    navigationView: ViewGroup,
    private val drawer: DrawerLayout,
) : BaseAdapter() {

    companion object {
        private val log = LogCategory("SideMenuAdapter")

        private const val urlAppVersion =
            "https://mastodon-msg.juggler.jp/appVersion/appVersion.json"
        private const val urlGithubReleases =
            "https://github.com/tateisu/SubwayTooter/releases"
        private const val urlOlderDevices =
            "https://github.com/tateisu/SubwayTooter/discussions/192"

        private val itemTypeCount = ItemType.values().size

        private var lastVersionView: WeakReference<TextView>? = null

        private var versionText = SpannableStringBuilder("")

        private var releaseInfo: JsonObject? = null

        private fun clickableSpan(
            url: String,
            showUnderline: Boolean = false,
        ) = object : ClickableSpan() {
            override fun onClick(widget: View) {
                widget.activity?.openBrowser(url)
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = showUnderline
            }
        }

        private fun SpannableStringBuilder.appendSpanLine(
            text: String,
            vararg spans: Any,
        ) = this.apply {
            if (isNotEmpty()) {
                append("\n")
            }
            val start = length
            append(text)
            for (span in spans) {
                setSpan(span, start, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        // バージョン情報と更新履歴と新リリース告知の文字列を組み立てる
        // メインスレッドでもそれ以外でも動作すること
        private fun Context.createVersionRow() = SpannableStringBuilder().apply {
            val currentVersion = try {
                packageManager.getPackageInfoCompat(packageName)!!.versionName
            } catch (ignored: Throwable) {
                "??"
            }

            append(
                getString(
                    R.string.app_name_with_version,
                    getString(R.string.app_name),
                    currentVersion
                )
            )
            val newRelease = releaseInfo?.jsonObject(
                if (PrefB.bpCheckBetaVersion.value) "beta" else "stable"
            )

            // 使用中のアプリバージョンより新しいリリースがある？
            val newVersion =
                (newRelease?.string("name")?.notEmpty() ?: newRelease?.string("tag_name"))
                    ?.replace("""(v(ersion)?)\s*""".toRegex(RegexOption.IGNORE_CASE), "")
                    ?.trim()
                    ?.notEmpty()
                    ?.takeIf {
                        log.i("newVersion=$it, currentVersion=$currentVersion")
                        VersionString(it) > VersionString(currentVersion)
                    }

            val releaseMinSdkVersion = newRelease?.int("minSdkVersion")
                ?: Build.VERSION.SDK_INT
            val releaseMinSdkVersionScheduled = newRelease?.int("minSdkVersionScheduled")
                ?: Build.VERSION.SDK_INT

            when {
                // 新しいバージョンがある
                // それはこの端末にインストール可能である
                newVersion != null && Build.VERSION.SDK_INT >= releaseMinSdkVersion -> {
                    appendSpanLine(
                        getString(
                            R.string.new_version_available,
                            newVersion
                        ),
                        ForegroundColorSpan(
                            attrColor(R.attr.colorRegexFilterError)
                        ),
                    )
                    newRelease?.string("html_url")?.let {
                        appendSpanLine(
                            getString(R.string.release_note_with_assets),
                            clickableSpan(it)
                        )
                    }
                }

                // 通常時は更新履歴へのリンク
                else -> appendSpanLine(
                    getString(R.string.release_note),
                    UnderlineSpan(),
                    clickableSpan(urlGithubReleases),
                )
            }

            // 端末のOSバージョンがサポートから外れる予定なら、サイドメニューにリンクを追加する
            if (Build.VERSION.SDK_INT < releaseMinSdkVersionScheduled) {
                appendSpanLine(
                    getString(R.string.old_devices_warning),
                    clickableSpan(urlOlderDevices, showUnderline = true),
                )
            }
        }

        // メインスレッドから呼ばれる
        private fun Context.checkVersion() {
            // サイドメニューから参照されるバージョン文字列を初期化する
            // この時点ではreleaseInfoはnullかもしれない
            versionText = createVersionRow()

            // releaseInfoが既にあり、更新時刻が十分に新しいなら情報を取得し直す必要はない
            releaseInfo?.string("updated_at")
                ?.let { TootStatus.parseTime(it) }
                ?.takeIf { it >= System.currentTimeMillis() - 86400000L }
                ?.let { return }

            // リリース情報を取得し直す
            launchIO {
                try {
                    val json = App1.getHttpCached(urlAppVersion)
                        ?.decodeUTF8()
                        ?.decodeJsonObject()
                        ?: error("missing appVersion json")
                    releaseInfo = json
                    versionText = createVersionRow()
                    withContext(AppDispatchers.MainImmediate) {
                        lastVersionView?.get()?.text = versionText
                    }
                } catch (ex: Throwable) {
                    log.e(ex, "checkVersion failed")
                }
            }
        }
    }

    private enum class ItemType(val id: Int) {
        IT_NORMAL(0),
        IT_GROUP_HEADER(1),
        IT_DIVIDER(2),
        IT_VERSION(3),
        IT_TIMEZONE(4),
        IT_NOTIFICATION_PERMISSION(5),
    }

    private class Item(
        // 項目の文字列リソース or 0: divider, 1: バージョン表記, 2: タイムゾーン
        val title: Int = 0,
        val icon: Int = 0,
        val action: ActMain.() -> Unit = {},
    ) {

        val itemType: ItemType
            get() = when {
                title == 0 -> ItemType.IT_DIVIDER
                title == 1 -> ItemType.IT_VERSION
                title == 2 -> ItemType.IT_TIMEZONE
                title == 3 -> ItemType.IT_NOTIFICATION_PERMISSION
                icon == 0 -> ItemType.IT_GROUP_HEADER
                else -> ItemType.IT_NORMAL
            }
    }

    /*
        no title => section divider
        else no icon => section header with title
        else => menu item with icon and title
    */
    private val originalList = listOf(

        Item(icon = R.drawable.ic_info_outline, title = 1),
        Item(icon = R.drawable.ic_info_outline, title = 2),
        Item(icon = R.drawable.ic_info_outline, title = 3),

        Item(),
        Item(title = R.string.account),

        Item(title = R.string.account_add, icon = R.drawable.ic_person_add) {
            accountAdd()
        },

        Item(icon = R.drawable.ic_settings, title = R.string.account_setting) {
            accountOpenSetting()
        },

        Item(icon = R.drawable.outline_delivery_dining_24, title = R.string.push_message_history) {
            startActivity(Intent(this, ActPushMessageList::class.java))
        },

        Item(),
        Item(title = R.string.column),

        Item(icon = R.drawable.ic_list_numbered, title = R.string.column_list) {
            openColumnList()
        },

        Item(icon = R.drawable.ic_close, title = R.string.close_all_columns) {
            closeColumnAll()
        },

        Item(icon = R.drawable.ic_paste, title = R.string.open_column_from_url) {
            openColumnFromUrl()
        },

        Item(icon = R.drawable.ic_home, title = R.string.home) {
            timeline(defaultInsertPosition, ColumnType.HOME)
        },

        Item(icon = R.drawable.ic_announcement, title = R.string.notifications) {
            timeline(defaultInsertPosition, ColumnType.NOTIFICATIONS)
        },

        Item(icon = R.drawable.ic_mail, title = R.string.direct_messages) {
            timeline(defaultInsertPosition, ColumnType.DIRECT_MESSAGES)
        },

        Item(icon = R.drawable.ic_share, title = R.string.misskey_hybrid_timeline_long) {
            timeline(defaultInsertPosition, ColumnType.MISSKEY_HYBRID)
        },

        Item(icon = R.drawable.ic_run, title = R.string.local_timeline) {
            timeline(defaultInsertPosition, ColumnType.LOCAL)
        },

        Item(icon = R.drawable.ic_bike, title = R.string.federate_timeline) {
            timeline(defaultInsertPosition, ColumnType.FEDERATE)
        },

        Item(icon = R.drawable.ic_list_list, title = R.string.lists) {
            timeline(defaultInsertPosition, ColumnType.LIST_LIST)
        },

        Item(icon = R.drawable.ic_satellite, title = R.string.antenna_list_misskey) {
            timeline(defaultInsertPosition, ColumnType.MISSKEY_ANTENNA_LIST)
        },

        Item(icon = R.drawable.ic_hashtag, title = R.string.followed_tags) {
            timeline(defaultInsertPosition, ColumnType.FOLLOWED_HASHTAGS)
        },

        Item(icon = R.drawable.ic_search, title = R.string.search) {
            timeline(defaultInsertPosition, ColumnType.SEARCH, args = arrayOf("", false))
        },

        Item(icon = R.drawable.ic_trend, title = R.string.trend_tag) {
            timeline(defaultInsertPosition, ColumnType.TREND_TAG)
        },
        Item(icon = R.drawable.ic_trend, title = R.string.trend_link) {
            timeline(defaultInsertPosition, ColumnType.TREND_LINK)
        },
        Item(icon = R.drawable.ic_trend, title = R.string.trend_post) {
            timeline(defaultInsertPosition, ColumnType.TREND_POST)
        },
        Item(icon = R.drawable.ic_star_outline, title = R.string.favourites) {
            timeline(defaultInsertPosition, ColumnType.FAVOURITES)
        },

        Item(icon = R.drawable.ic_bookmark, title = R.string.bookmarks) {
            timeline(defaultInsertPosition, ColumnType.BOOKMARKS)
        },
        Item(icon = R.drawable.ic_face, title = R.string.reactioned_posts) {
            launchAndShowError {
                accountListCanSeeMyReactions()?.let { list ->
                    if (list.isEmpty()) {
                        showToast(false, R.string.not_available_for_current_accounts)
                    } else {
                        val columnType = ColumnType.REACTIONS
                        pickAccount(
                            accountListArg = list.toMutableList(),
                            bAuto = true,
                            message = getString(
                                R.string.account_picker_add_timeline_of,
                                columnType.name1(applicationContext)
                            )
                        )?.let { addColumn(defaultInsertPosition, it, columnType) }
                    }
                }
            }
        },

        Item(icon = R.drawable.ic_account_box, title = R.string.profile) {
            timeline(defaultInsertPosition, ColumnType.PROFILE)
        },

        Item(icon = R.drawable.ic_follow_wait, title = R.string.follow_requests) {
            timeline(defaultInsertPosition, ColumnType.FOLLOW_REQUESTS)
        },

        Item(icon = R.drawable.ic_person_add, title = R.string.follow_suggestion) {
            timeline(defaultInsertPosition, ColumnType.FOLLOW_SUGGESTION)
        },

        Item(icon = R.drawable.ic_person_add, title = R.string.endorse_set) {
            timeline(defaultInsertPosition, ColumnType.ENDORSEMENT)
        },

        Item(icon = R.drawable.ic_person_add, title = R.string.profile_directory) {
            serverProfileDirectoryFromSideMenu()
        },

        Item(icon = R.drawable.ic_volume_off, title = R.string.muted_users) {
            timeline(defaultInsertPosition, ColumnType.MUTES)
        },

        Item(icon = R.drawable.ic_block, title = R.string.blocked_users) {
            timeline(defaultInsertPosition, ColumnType.BLOCKS)
        },

        Item(icon = R.drawable.ic_volume_off, title = R.string.keyword_filters) {
            timeline(defaultInsertPosition, ColumnType.KEYWORD_FILTER)
        },

        Item(icon = R.drawable.ic_cloud_off, title = R.string.blocked_domains) {
            timeline(defaultInsertPosition, ColumnType.DOMAIN_BLOCKS)
        },

        Item(icon = R.drawable.ic_timer, title = R.string.scheduled_status_list) {
            timeline(defaultInsertPosition, ColumnType.SCHEDULED_STATUS)
        },

        Item(),
        Item(title = R.string.toot_search),

//        Item(icon = R.drawable.ic_search, title = R.string.mastodon_search_portal) {
//            addColumn(defaultInsertPosition, SavedAccount.na, ColumnType.SEARCH_MSP, "")
//        },
//        Item(icon = R.drawable.ic_search, title = R.string.tootsearch) {
//            addColumn(defaultInsertPosition, SavedAccount.na, ColumnType.SEARCH_TS, "")
//        },

        Item(icon = R.drawable.ic_search, title = R.string.notestock) {
            addColumn(
                defaultInsertPosition,
                SavedAccount.na,
                ColumnType.SEARCH_NOTESTOCK,
                params = arrayOf("")
            )
        },

        Item(),
        Item(title = R.string.setting),

        Item(icon = R.drawable.ic_settings, title = R.string.app_setting) {
            arAppSetting.launch(
                ActAppSetting.createIntent(this)
            )
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

        Item(icon = R.drawable.ic_info_outline, title = R.string.app_about) {

            arAbout.launch(
                Intent(this, ActAbout::class.java)
            )
        },

        Item(icon = R.drawable.ic_info_outline, title = R.string.oss_license) {
            startActivity(Intent(this, ActOSSLicense::class.java))
        },

        Item(icon = R.drawable.ic_hot_tub, title = R.string.app_exit) {
            finish()
        }
    )

    private var list = originalList

    private val iconColor = actMain.attrColor(R.attr.colorTimeSmall)

    override fun getCount(): Int = list.size
    override fun getItem(position: Int): Any = list[position]
    override fun getItemId(position: Int): Long = 0L

    override fun getViewTypeCount(): Int = itemTypeCount
    override fun getItemViewType(position: Int): Int = list[position].itemType.id

    private inline fun <reified T : View> viewOrInflate(
        view: View?,
        parent: ViewGroup?,
        resId: Int,
    ): T =
        (view ?: actMain.layoutInflater.inflate(resId, parent, false))
                as? T ?: error("invalid view type! ${T::class.java.simpleName}")

    override fun getView(position: Int, view: View?, parent: ViewGroup?): View =
        list[position].run {
            when (itemType) {
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
                        movementMethod = LinkMovementMethod.getInstance()
                        textSize = 18f
                        isAllCaps = false
                        setLineSpacing(
                            1f,
                            1.1f
                        )
                        background = null
                        text = versionText
                    }
                ItemType.IT_TIMEZONE ->
                    viewOrInflate<TextView>(view, parent, R.layout.lv_sidemenu_item).apply {
                        textSize = 14f
                        isAllCaps = false
                        background = null
                        text = getTimeZoneString(context)
                    }
                ItemType.IT_NOTIFICATION_PERMISSION ->
                    viewOrInflate<TextView>(view, parent, R.layout.lv_sidemenu_item).apply {
                        isAllCaps = false
                        val action = notificationActionRecommend() ?: return@apply
                        text = actMain.getString(action.first)
                        val drawable = createColoredDrawable(actMain, icon, iconColor, 1f)
                        setCompoundDrawablesRelativeWithIntrinsicBounds(
                            drawable,
                            null,
                            null,
                            null
                        )
                        setOnClickListener {
                            drawer.closeDrawer(GravityCompat.START)
                            notificationActionRecommend()?.second?.invoke()
                            filterListItems()
                        }
                    }
            }
        }

    private fun getTimeZoneString(context: Context): String {
        try {
            var tz = TimeZone.getDefault()
            val tzId = PrefS.spTimeZone.value
            if (tzId.isBlank()) {
                return tz.displayName + "(" + context.getString(R.string.device_timezone) + ")"
            }
            tz = TimeZone.getTimeZone(tzId)
            var offset = tz.rawOffset.toLong()
            return when (offset) {
                0L -> "(UTC\u00B100:00) ${tz.id} ${tz.displayName}"
                else -> {

                    val format = when {
                        offset > 0 -> "(UTC+%02d:%02d) %s %s"
                        else -> "(UTC-%02d:%02d) %s %s"
                    }

                    offset = abs(offset)

                    val hours = TimeUnit.MILLISECONDS.toHours(offset)
                    val minutes =
                        TimeUnit.MILLISECONDS.toMinutes(offset) - TimeUnit.HOURS.toMinutes(hours)

                    String.format(format, hours, minutes, tz.id, tz.displayName)
                }
            }
        } catch (ex: Throwable) {
            log.w(ex, "getTimeZoneString failed.")
            return "(incorrect TimeZone)"
        }
    }

    fun onActivityStart() {
        this.notifyDataSetChanged()
    }

    private fun notificationActionRecommend(): Pair<Int, () -> Unit>? = when {
        actMain.prNotification.spec.listNotGranded(actMain).isNotEmpty() ->
            Pair(R.string.notification_permission_not_granted) {
                actMain.prNotification.openAppSetting(actMain)
            }
        (actMain.prefDevice.pushDistributor.isNullOrEmpty() && actMain.fcmHandler.noFcm) ||
                actMain.prefDevice.pushDistributor == PUSH_DISTRIBUTOR_NONE ->
            Pair(R.string.notification_push_distributor_disabled) {
                actMain.selectPushDistributor()
            }
        else -> null
    }

    fun filterListItems() {
        list = originalList.filter {
            when (it.itemType) {
                ItemType.IT_NOTIFICATION_PERMISSION ->
                    notificationActionRecommend() != null
                else -> true
            }
        }
        notifyDataSetChanged()
    }

    init {
        actMain.applicationContext.checkVersion()
        filterListItems()

        ListView(actMain).apply {
            adapter = this@SideMenuAdapter
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            backgroundColor = actMain.attrColor(R.attr.colorWindowBackground)
            selector = StateListDrawable()
            divider = null
            dividerHeight = 0
            isScrollbarFadingEnabled = false

            val padV = (actMain.density * 12f + 0.5f).toInt()
            setPadding(0, padV, 0, padV)
            clipToPadding = false
            scrollBarStyle = ListView.SCROLLBARS_OUTSIDE_OVERLAY

            navigationView.addView(this)
        }
    }
}
