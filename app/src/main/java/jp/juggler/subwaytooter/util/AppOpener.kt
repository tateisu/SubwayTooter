package jp.juggler.subwaytooter.util

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.app.Activity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.action.Action_HashTag
import jp.juggler.subwaytooter.action.Action_Toot
import jp.juggler.subwaytooter.action.Action_User
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootAccountRef
import jp.juggler.subwaytooter.api.entity.TootAttachment
import jp.juggler.subwaytooter.api.entity.TootStatus.Companion.findStatusIdFromUrl
import jp.juggler.subwaytooter.api.entity.TootTag.Companion.findHashtagFromUrl
import jp.juggler.subwaytooter.dialog.DlgAppPicker
import jp.juggler.subwaytooter.pref
import jp.juggler.subwaytooter.span.LinkInfo
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.*
import java.util.ArrayList

// Subway Tooterの「アプリ設定/挙動/リンクを開く際にCustom Tabsを使わない」をONにして
// 投稿のコンテキストメニューの「トゥートへのアクション/Webページを開く」「ユーザへのアクション/Webページを開く」を使うと
// 投げたインテントをST自身が受け取って「次のアカウントから開く」ダイアログが出て
// 「Webページを開く」をまた押すと無限ループしてダイアログの影が徐々に濃くなりそのうち壊れる
// これを避けるには、投稿やトゥートを開く際に bpDontUseCustomTabs がオンならST以外のアプリを列挙したアプリ選択ダイアログを出すしかない

private val log = LogCategory("AppOpener")

// returns true if activity is opened.
// returns false if fallback required
private fun Activity.startActivityExcludeMyApp(
    intent: Intent,
    startAnimationBundle: Bundle? = null
): Boolean {
    try {
        // このアプリのパッケージ名
        val myName = packageName

        val filter: (ResolveInfo) -> Boolean = {
            when{
                it.activityInfo.packageName == myName -> false
                !it.activityInfo.exported -> false

                // Huaweiの謎Activityのせいでうまく働かないことがある
                -1 != it.activityInfo.packageName.indexOf("com.huawei.android.internal") -> false

                // 標準アプリが設定されていない場合、アプリを選択するためのActivityが出てくる場合がある
                it.activityInfo.packageName == "android" -> false
                it.activityInfo.javaClass.name.startsWith( "com.android.internal") -> false
                it.activityInfo.javaClass.name.startsWith("com.android.systemui") -> false

                // たぶんChromeとかfirefoxとか
                else -> true
            }
        }

        // resolveActivity がこのアプリ以外のActivityを返すなら、それがベストなんだろう
        // ただしAndroid M以降はMATCH_DEFAULT_ONLYだと「常時」が設定されてないとnullを返す
        val ri = packageManager!!.resolveActivity(
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PackageManager.MATCH_ALL
            } else {
                PackageManager.MATCH_DEFAULT_ONLY
            }
        )?.takeIf(filter)

        return when {

            ri != null -> {
                intent.setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
                log.d("startActivityExcludeMyApp(1) $intent")
                startActivity(intent, startAnimationBundle)
                true
            }

            else -> DlgAppPicker(
                this,
                intent,
                autoSelect = true,
                filter = filter
            ) {
                try {
                    intent.component = it.cn()
                    log.d("startActivityExcludeMyApp(2) $intent")
                    startActivity(intent, startAnimationBundle)
                } catch (ex: Throwable) {
                    log.trace(ex)
                    showToast(ex, "can't open. ${intent.data}")
                }
            }.show()
        }
    } catch (ex: Throwable) {
        log.trace(ex)
        showToast(ex, "can't open. ${intent.data}")
        return true // fallback not required in this case
    }
}

fun Activity.openBrowser(uri: Uri?) {
    uri ?: return
    val rv = startActivityExcludeMyApp(Intent(Intent.ACTION_VIEW, uri))
    if (!rv) showToast(true, "there is no app that can open $uri")
}

fun Activity.openBrowser(url: String?) = openBrowser(url.mayUri())

// Chrome Custom Tab を開く
fun Activity.openCustomTab(url: String?) {
    url ?: return

    if (url.isEmpty()) {
        showToast(false, "URL is empty string.")
        return
    }

    val pref = pref()
    if (Pref.bpDontUseCustomTabs(pref)) {
        openBrowser(url)
        return
    }

    try {
        fun startCustomTabIntent(cn: ComponentName?) =
            CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(
                    CustomTabColorSchemeParams.Builder()
                        .setToolbarColor(getAttributeColor(R.attr.colorPrimary))
                        .build()
                )
                .setShowTitle(true)
                .build()
                .let {
                    log.w("startCustomTabIntent ComponentName=$cn")
                    startActivityExcludeMyApp(
                        it.intent.also { intent ->
                            if (cn != null) intent.component = cn
                            intent.data = url.toUri()
                        },
                        it.startAnimationBundle
                    )
                }

        if (url.startsWith("http") && Pref.bpPriorChrome(pref)) {
            try {
                // 初回はChrome指定で試す
                val cn = ComponentName(
                    "com.android.chrome",
                    "com.google.android.apps.chrome.Main"
                )
                if (startCustomTabIntent(cn)) return
            } catch (ex2: Throwable) {
                log.e(ex2, "openCustomTab: missing chrome. retry to other application.")
            }
        }

        // Chromeがないようなのでcomponent指定なしでリトライ
        if (startCustomTabIntent(null)) return
        showToast(true, "the browser app is not installed.")

    } catch (ex: Throwable) {
        log.trace(ex)
        val scheme = url.mayUri()?.scheme ?: url
        showToast(true, "can't open browser app for %s", scheme)
    }
}

fun Activity.openCustomTab(ta: TootAttachment) =
    openCustomTab(ta.getLargeUrl(pref()))

fun openCustomTab(
    activity: ActMain,
    pos: Int,
    url: String,
    accessInfo: SavedAccount? = null,
    tagList: ArrayList<String>? = null,
    allowIntercept: Boolean = true,
    whoRef: TootAccountRef? = null,
    linkInfo: LinkInfo? = null
) {
    try {
        log.d("openCustomTab: $url")

        val whoAcct = if (whoRef != null) {
            accessInfo?.getFullAcct(whoRef.get())
        } else {
            null
        }

        if (allowIntercept && accessInfo != null) {

            // ハッシュタグはいきなり開くのではなくメニューがある
            val tagInfo = url.findHashtagFromUrl()
            if (tagInfo != null) {
                Action_HashTag.dialog(
                    activity,
                    pos,
                    url,
                    Host.parse(tagInfo.second),
                    tagInfo.first,
                    tagList,
                    whoAcct
                )
                return
            }

            val statusInfo = url.findStatusIdFromUrl()
            if (statusInfo != null) {
                if (accessInfo.isNA ||
                    statusInfo.statusId == null ||
                    !accessInfo.matchHost(statusInfo.host)
                ) {
                    Action_Toot.conversationOtherInstance(
                        activity,
                        pos,
                        statusInfo.url,
                        statusInfo.statusId,
                        statusInfo.host,
                        statusInfo.statusId
                    )
                } else {
                    Action_Toot.conversationLocal(
                        activity,
                        pos,
                        accessInfo,
                        statusInfo.statusId
                    )
                }
                return
            }

            // opener.linkInfo をチェックしてメンションを判別する
            val mention = linkInfo?.mention
            if (mention != null) {
                val fullAcct = getFullAcctOrNull(mention.acct, mention.url, accessInfo)
                if (fullAcct != null) {
                    if (fullAcct.host != null) {
                        when (fullAcct.host.ascii) {
                            "github.com",
                            "twitter.com" ->
                                activity.openCustomTab(mention.url)
                            "gmail.com" ->
                                activity.openBrowser("mailto:${fullAcct.pretty}")

                            else ->
                                Action_User.profile(
                                    activity,
                                    pos,
                                    accessInfo, // FIXME nullが必要なケースがあったっけなかったっけ…
                                    mention.url,
                                    fullAcct.host,
                                    fullAcct.username,
                                    original_url = url
                                )
                        }
                        return
                    }
                }
            }

            // ユーザページをアプリ内で開く
            var m = TootAccount.reAccountUrl.matcher(url)
            if (m.find()) {
                val host = m.groupEx(1)!!
                val user = m.groupEx(2)!!.decodePercent()
                val instance = m.groupEx(3)?.decodePercent()?.notEmpty()
                // https://misskey.xyz/@tateisu@github.com
                // https://misskey.xyz/@tateisu@twitter.com

                if (instance != null) {
                    val instanceHost = Host.parse(instance)
                    when (instanceHost.ascii) {
                        "github.com", "twitter.com" -> {
                            activity.openCustomTab("https://$instance/$user")
                        }

                        "gmail.com" -> {
                            activity.openBrowser("mailto:$user@$instance")
                        }

                        else -> {
                            Action_User.profile(
                                activity,
                                pos,
                                null, // Misskeyだと疑似アカが必要なんだっけ…？
                                "https://$instance/@$user",
                                instanceHost,
                                user,
                                original_url = url
                            )
                        }
                    }
                } else {
                    Action_User.profile(
                        activity,
                        pos,
                        accessInfo,
                        url,
                        Host.parse(host),
                        user
                    )
                }
                return
            }

            m = TootAccount.reAccountUrl2.matcher(url)
            if (m.find()) {
                val host = m.groupEx(1)!!
                val user = m.groupEx(2)!!.decodePercent()

                Action_User.profile(
                    activity,
                    pos,
                    accessInfo,
                    url,
                    Host.parse(host),
                    user
                )
                return
            }

        }

        activity.openCustomTab(url)

    } catch (ex: Throwable) {
        log.trace(ex)
        log.e(ex, "openCustomTab failed. $url")
    }
}

