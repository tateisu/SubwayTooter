package jp.juggler.subwaytooter.util

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import jp.juggler.subwaytooter.ActCallback
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.action.conversationLocal
import jp.juggler.subwaytooter.action.conversationOtherInstance
import jp.juggler.subwaytooter.action.tagDialog
import jp.juggler.subwaytooter.action.userProfile
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.api.entity.TootStatus.Companion.findStatusIdFromUrl
import jp.juggler.subwaytooter.api.entity.TootTag.Companion.findHashtagFromUrl
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.span.LinkInfo
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.data.*
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.log.withCaption
import jp.juggler.util.ui.attrColor

// Subway Tooterの「アプリ設定/挙動/リンクを開く際にCustom Tabsを使わない」をONにして
// 投稿のコンテキストメニューの「トゥートへのアクション/Webページを開く」「ユーザへのアクション/Webページを開く」を使うと
// 投げたインテントをST自身が受け取って「次のアカウントから開く」ダイアログが出て
// 「Webページを開く」をまた押すと無限ループしてダイアログの影が徐々に濃くなりそのうち壊れる
// これを避けるには、投稿やトゥートを開く際に bpDontUseCustomTabs がオンならST以外のアプリを列挙したアプリ選択ダイアログを出すしかない

private val log = LogCategory("AppOpener")

// 例外を出す
private fun Activity.openBrowserExcludeMe(intent: Intent) {
//    if (intent.component == null) {
//        val cn = PrefS.spWebBrowser(pref).cn()
//        if (cn?.exists(this) == true) {
//            intent.component = cn
//        }
//    }
    ActCallback.setUriFromApp(intent.data)
    startActivity(intent)
//
//        // このアプリのパッケージ名
//        val myName = packageName
//
//        val filter: (ResolveInfo) -> Boolean = {
//            when {
//                it.activityInfo.packageName == myName -> false
//                !it.activityInfo.exported -> false
//
//                // Huaweiの謎Activityのせいでうまく働かないことがある
//                -1 != it.activityInfo.packageName.indexOf("com.huawei.android.internal") -> false
//
//                // 標準アプリが設定されていない場合、アプリを選択するためのActivityが出てくる場合がある
//                it.activityInfo.packageName == "android" -> false
//                it.activityInfo.javaClass.name.startsWith("com.android.internal") -> false
//                it.activityInfo.javaClass.name.startsWith("com.android.systemui") -> false
//
//                // たぶんChromeとかfirefoxとか
//                else -> true
//            }
//        }
//
//        // resolveActivity がこのアプリ以外のActivityを返すなら、それがベストなんだろう
//        // ただしAndroid M以降はMATCH_DEFAULT_ONLYだと「常時」が設定されてないとnullを返す
//        val ri = packageManager!!.resolveActivity(
//            intent,
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                PackageManager.MATCH_ALL
//            } else {
//                PackageManager.MATCH_DEFAULT_ONLY
//            }
//        )?.takeIf(filter)
//
//        return when {
//
//            ri != null -> {
//                intent.setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
//                log.d("startActivityExcludeMyApp(1) $intent")
//                startActivity(intent, startAnimationBundle)
//                true
//            }
//
//            else -> DlgAppPicker(
//                this,
//                intent,
//                autoSelect = true,
//                filter = filter,
//                addCopyAction = false
//            ) {
//                try {
//                    intent.component = it.cn()
//                    log.d("startActivityExcludeMyApp(2) $intent")
//                    startActivity(intent, startAnimationBundle)
//                } catch (ex: Throwable) {
//                    log.trace(ex)
//                    showToast(ex, "can't open. ${intent.data}")
//                }
//            }.show()
//        }
//    } catch (ex: Throwable) {
//        log.trace(ex)
//        showToast(ex, "can't open. ${intent.data}")
//    }
}

fun Activity.openBrowser(uri: Uri?) {
    uri ?: return
    try {
        openBrowserExcludeMe(
            Intent(Intent.ACTION_VIEW, uri).apply { addCategory(Intent.CATEGORY_BROWSABLE) }
        )
    } catch (ex: Throwable) {
        log.e(ex, "openBrowser failed. uri=$uri")
        showToast(ex, "openBrowser failed. uri=$uri")
    }
}

fun Activity.openBrowser(url: String?) =
    openBrowser(url.mayUri())

// Chrome Custom Tab を開く
fun Activity.openCustomTab(url: String?) {
    url ?: return

    if (url.isEmpty()) {
        showToast(false, "URL is empty string.")
        return
    }

    if (PrefB.bpDontUseCustomTabs.value) {
        openBrowser(url)
        return
    }

    try {
        // 例外を出す
        fun startCustomTabIntent(cn: ComponentName?) =
            CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(
                    CustomTabColorSchemeParams.Builder()
                        .setToolbarColor(attrColor(android.R.attr.colorPrimary))
                        .build()
                )
                .setShowTitle(true)
                .build()
                .let {
                    log.i("startCustomTabIntent ComponentName=$cn")
                    openBrowserExcludeMe(
                        it.intent.also { intent ->
                            if (cn != null) intent.component = cn
                            intent.data = url.toUri()
                        },
                    )
                }

        if (url.startsWith("http") && PrefB.bpPriorChrome.value) {
            try {
                // 初回はChrome指定で試す
                val cn = ComponentName(
                    "com.android.chrome",
                    "com.google.android.apps.chrome.Main"
                )
                startCustomTabIntent(cn)
                return
            } catch (ex2: Throwable) {
                log.e(ex2.withCaption("openCustomTab: missing chrome. retry to other application."))
            }
        }

        // Chromeがないようなのでcomponent指定なしでリトライ
        startCustomTabIntent(null)
    } catch (ex: Throwable) {
        val errMsg = "can't open browser app for ${url.mayUri()?.scheme?.notBlank() ?: url}"
        log.e(ex, errMsg)
        showToast(true, errMsg)
    }
}

fun Activity.openCustomTab(ta: TootAttachment) =
    openCustomTab(ta.getLargeUrl())

fun openCustomTab(
    activity: ActMain,
    pos: Int,
    url: String,
    accessInfo: SavedAccount? = null,
    tagList: ArrayList<String>? = null,
    allowIntercept: Boolean = true,
    whoRef: TootAccountRef? = null,
    linkInfo: LinkInfo? = null,
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
                activity.tagDialog(
                    accessInfo,
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
                when {
                    // fedibirdの参照のURLだった && 閲覧アカウントが参照を扱える
                    // 参照カラムを開く
                    statusInfo.statusId != null &&
                            statusInfo.isReference &&
                            TootInstance.getCached(accessInfo)?.canUseReference == true ->
                        activity.conversationLocal(
                            pos,
                            accessInfo,
                            statusInfo.statusId,
                            isReference = statusInfo.isReference,
                        )

                    // 疑似アカウント?
                    // 別サーバ？
                    // ステータスIDがない?(Pleroma)
                    accessInfo.isNA ||
                            !accessInfo.matchHost(statusInfo.host) ||
                            statusInfo.statusId == null ->
                        activity.conversationOtherInstance(
                            pos,
                            statusInfo.url,
                            statusInfo.statusId,
                            statusInfo.host,
                            statusInfo.statusId,
                            isReference = statusInfo.isReference,
                        )

                    else ->
                        activity.conversationLocal(
                            pos,
                            accessInfo,
                            statusInfo.statusId,
                            isReference = statusInfo.isReference,
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
                            "twitter.com",
                            ->
                                activity.openCustomTab(mention.url)
                            "gmail.com" ->
                                activity.openBrowser("mailto:${fullAcct.pretty}")

                            else ->
                                activity.userProfile(
                                    pos,
                                    accessInfo, // FIXME nullが必要なケースがあったっけなかったっけ…
                                    acct = fullAcct,
                                    userUrl = mention.url,
                                    originalUrl = url,
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
                            activity.userProfile(
                                pos,
                                null, // Misskeyだと疑似アカが必要なんだっけ…？
                                acct = Acct.parse(user, instanceHost),
                                userUrl = "https://$instance/@$user",
                                originalUrl = url,
                            )
                        }
                    }
                } else {
                    activity.userProfile(
                        pos,
                        accessInfo,
                        Acct.parse(user, host),
                        url
                    )
                }
                return
            }

            m = TootAccount.reAccountUrl2.matcher(url)
            if (m.find()) {
                val host = m.groupEx(1)!!
                val user = m.groupEx(2)!!.decodePercent()

                activity.userProfile(
                    pos,
                    accessInfo,
                    Acct.parse(user, host),
                    url,
                )
                return
            }
        }

        activity.openCustomTab(url)
    } catch (ex: Throwable) {
        log.e(ex, "openCustomTab failed. $url")
    }
}
