package jp.juggler.subwaytooter.actmain

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.action.conversationOtherInstance
import jp.juggler.subwaytooter.action.openActPostImpl
import jp.juggler.subwaytooter.action.userProfile
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.api.entity.TootStatus.Companion.findStatusIdFromUrl
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.column.startLoading
import jp.juggler.subwaytooter.dialog.pickAccount
import jp.juggler.subwaytooter.notification.PushSubscriptionHelper
import jp.juggler.subwaytooter.notification.checkNotificationImmediate
import jp.juggler.subwaytooter.notification.checkNotificationImmediateAll
import jp.juggler.subwaytooter.notification.recycleClickedNotification
import jp.juggler.subwaytooter.pref.PrefDevice
import jp.juggler.subwaytooter.pref.PrefS
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.util.*
import java.util.concurrent.atomic.AtomicReference

private val log = LogCategory("ActMainIntent")

// ActOAuthCallbackで受け取ったUriを処理する
fun ActMain.handleIntentUri(uri: Uri) {
    try {
        log.d("handleIntentUri $uri")
        when (uri.scheme) {
            "subwaytooter", "misskeyclientproto" -> handleCustomSchemaUri(uri)
            else -> handleOtherUri(uri)
        }
    } catch (ex: Throwable) {
        log.trace(ex)
        showToast(ex, "handleIntentUri failed.")
    }
}

fun ActMain.handleOtherUri(uri: Uri): Boolean {
    val url = uri.toString()

    url.findStatusIdFromUrl()?.let { statusInfo ->
        // ステータスをアプリ内で開く
        conversationOtherInstance(
            defaultInsertPosition,
            statusInfo.url,
            statusInfo.statusId,
            statusInfo.host,
            statusInfo.statusId,
            isReference = statusInfo.isReference,
        )
        return true
    }

    TootAccount.reAccountUrl.matcher(url).takeIf { it.find() }?.let { m ->
        // ユーザページをアプリ内で開く
        val host = m.groupEx(1)!!
        val user = m.groupEx(2)!!.decodePercent()
        val instance = m.groupEx(3)?.decodePercent()

        if (instance?.isNotEmpty() == true) {
            userProfile(
                defaultInsertPosition,
                null,
                Acct.parse(user, instance),
                userUrl = "https://$instance/@$user",
                originalUrl = url
            )
        } else {
            userProfile(
                defaultInsertPosition,
                null,
                acct = Acct.parse(user, host),
                userUrl = url,
            )
        }
        return true
    }

    TootAccount.reAccountUrl2.matcher(url).takeIf { it.find() }?.let { m ->
        // intentFilterの都合でこの形式のURLが飛んでくることはないのだが…。
        val host = m.groupEx(1)!!
        val user = m.groupEx(2)!!.decodePercent()
        userProfile(
            defaultInsertPosition,
            null,
            acct = Acct.parse(user, host),
            userUrl = url,
        )
        return true
    }

    // このアプリでは処理できないURLだった
    // 外部ブラウザを開きなおそうとすると無限ループの恐れがある
    // アプリケーションチューザーを表示する

    val errorMessage = getString(R.string.cant_handle_uri_of, url)

    try {
        val queryFlag = if (Build.VERSION.SDK_INT >= 23) {
            // Android 6.0以降
            // MATCH_DEFAULT_ONLY だと標準の設定に指定されたアプリがあるとソレしか出てこない
            // MATCH_ALL を指定すると 以前と同じ挙動になる
            PackageManager.MATCH_ALL
        } else {
            // Android 5.xまでは MATCH_DEFAULT_ONLY でマッチするすべてのアプリを取得できる
            PackageManager.MATCH_DEFAULT_ONLY
        }

        // queryIntentActivities に渡すURLは実在しないホストのものにする
        val intent = Intent(Intent.ACTION_VIEW, "https://dummy.subwaytooter.club/".toUri())
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        val myName = packageName
        val resolveInfoList = packageManager.queryIntentActivities(intent, queryFlag)
            .filter { myName != it.activityInfo.packageName }

        if (resolveInfoList.isEmpty()) error("resolveInfoList is empty.")

        // このアプリ以外の選択肢を集める
        val choiceList = resolveInfoList
            .map {
                Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    `package` = it.activityInfo.packageName
                    setClassName(it.activityInfo.packageName, it.activityInfo.name)
                }
            }.toMutableList()

        val chooser = Intent.createChooser(choiceList.removeAt(0), errorMessage)
        // 2つめ以降はEXTRAに渡す
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, choiceList.toTypedArray())

        // 指定した選択肢でチューザーを作成して開く
        startActivity(chooser)
        return true
    } catch (ex: Throwable) {
        log.trace(ex)
    }

    AlertDialog.Builder(this)
        .setCancelable(true)
        .setMessage(errorMessage)
        .setPositiveButton(R.string.close, null)
        .show()
    return false
}

private fun ActMain.handleCustomSchemaUri(uri: Uri) {
    val dataIdString = uri.getQueryParameter("db_id")
    if (dataIdString != null) {
        // subwaytooter://notification_click/?db_id=(db_id)
        handleNotificationClick(uri, dataIdString)
    } else {
        // OAuth2 認証コールバック
        // subwaytooter://oauth(\d*)/?...
        handleOAuth2Callback(uri)
    }
}

private fun ActMain.handleNotificationClick(uri: Uri, dataIdString: String) {
    try {
        val account = dataIdString.toLongOrNull()?.let { SavedAccount.loadAccount(this, it) }
        if (account == null) {
            showToast(true, "handleNotificationClick: missing SavedAccount. id=$dataIdString")
            return
        }

        recycleClickedNotification(this, uri)

        val columnList = appState.columnList
        val column = columnList.firstOrNull {
            it.type == ColumnType.NOTIFICATIONS &&
                    it.accessInfo == account &&
                    !it.systemNotificationNotRelated
        }?.also {
            scrollToColumn(columnList.indexOf(it))
        } ?: addColumn(
            true,
            defaultInsertPosition,
            account,
            ColumnType.NOTIFICATIONS
        )

        // 通知を読み直す
        if (!column.bInitialLoading) column.startLoading()
    } catch (ex: Throwable) {
        log.trace(ex)
    }
}

private fun ActMain.handleOAuth2Callback(uri: Uri) {
    launchMain {
        var resultTootAccount: TootAccount? = null
        var resultSavedAccount: SavedAccount? = null
        var resultApiHost: Host? = null
        var resultApDomain: Host? = null
        runApiTask { client ->

            val uriStr = uri.toString()
            if (uriStr.startsWith("subwaytooter://misskey/auth_callback") ||
                uriStr.startsWith("misskeyclientproto://misskeyclientproto/auth_callback")
            ) {
                // Misskey 認証コールバック
                val token = uri.getQueryParameter("token")?.notBlank()
                    ?: return@runApiTask TootApiResult("missing token in callback URL")

                val prefDevice = PrefDevice.from(this)

                val hostStr = prefDevice.getString(PrefDevice.LAST_AUTH_INSTANCE, null)?.notBlank()
                    ?: return@runApiTask TootApiResult("missing instance name.")

                val instance = Host.parse(hostStr)

                when (val dbId = prefDevice.getLong(PrefDevice.LAST_AUTH_DB_ID, -1L)) {

                    // new registration
                    -1L -> client.apiHost = instance

                    // update access token
                    else -> try {
                        val sa = SavedAccount.loadAccount(applicationContext, dbId)
                            ?: return@runApiTask TootApiResult("missing account db_id=$dbId")
                        resultSavedAccount = sa
                        client.account = sa
                    } catch (ex: Throwable) {
                        log.trace(ex)
                        return@runApiTask TootApiResult(ex.withCaption("invalid state"))
                    }
                }

                val (ti, r2) = TootInstance.get(client)
                ti ?: return@runApiTask r2

                resultApiHost = instance
                resultApDomain = ti.uri?.let { Host.parse(it) }

                val parser = TootParser(
                    applicationContext,
                    linkHelper = LinkHelper.create(instance, misskeyVersion = ti.misskeyVersion)
                )
                client.authentication2Misskey(PrefS.spClientName(pref), token, ti.misskeyVersion)
                    ?.also { resultTootAccount = parser.account(it.jsonObject) }
            } else {
                // Mastodon 認証コールバック

                // エラー時
                // subwaytooter://oauth(\d*)/
                // ?error=access_denied
                // &error_description=%E3%83%AA%E3%82%BD%E3%83%BC%E3%82%B9%E3%81%AE%E6%89%80%E6%9C%89%E8%80%85%E3%81%BE%E3%81%9F%E3%81%AF%E8%AA%8D%E8%A8%BC%E3%82%B5%E3%83%BC%E3%83%90%E3%83%BC%E3%81%8C%E8%A6%81%E6%B1%82%E3%82%92%E6%8B%92%E5%90%A6%E3%81%97%E3%81%BE%E3%81%97%E3%81%9F%E3%80%82
                // &state=db%3A3
                val error = uri.getQueryParameter("error")
                val errorDescription = uri.getQueryParameter("error_description")
                if (error != null || errorDescription != null) {
                    return@runApiTask TootApiResult(
                        errorDescription.notBlank() ?: error.notBlank() ?: "?"
                    )
                }

                // subwaytooter://oauth(\d*)/
                //    ?code=113cc036e078ac500d3d0d3ad345cd8181456ab087abc67270d40f40a4e9e3c2
                //    &state=host%3Amastodon.juggler.jp

                val code = uri.getQueryParameter("code")?.notBlank()
                    ?: return@runApiTask TootApiResult("missing code in callback url.")

                val sv = uri.getQueryParameter("state")?.notBlank()
                    ?: return@runApiTask TootApiResult("missing state in callback url.")

                for (param in sv.split(",")) {
                    when {
                        param.startsWith("db:") -> try {
                            val dataId = param.substring(3).toLong(10)
                            val sa = SavedAccount.loadAccount(applicationContext, dataId)
                                ?: return@runApiTask TootApiResult("missing account db_id=$dataId")
                            resultSavedAccount = sa
                            client.account = sa
                        } catch (ex: Throwable) {
                            log.trace(ex)
                            return@runApiTask TootApiResult(ex.withCaption("invalid state"))
                        }

                        param.startsWith("host:") -> {
                            val host = Host.parse(param.substring(5))
                            client.apiHost = host
                        }

                        // ignore other parameter
                    }
                }

                val apiHost = client.apiHost
                    ?: return@runApiTask TootApiResult("missing instance in callback url.")

                resultApiHost = apiHost

                val parser = TootParser(
                    applicationContext,
                    linkHelper = LinkHelper.create(apiHost)
                )

                val refToken = AtomicReference<String>(null)

                client.authentication2Mastodon(
                    PrefS.spClientName(pref),
                    code,
                    outAccessToken = refToken
                )?.also { result ->
                    val ta = parser.account(result.jsonObject)
                    if (ta != null) {
                        val (ti, ri) = TootInstance.getEx(client, forceAccessToken = refToken.get())
                        ti ?: return@runApiTask ri
                        resultTootAccount = ta
                        resultApDomain = ti.uri?.let { Host.parse(it) }
                    }
                }
            }
        }?.let { result ->
            val apiHost = resultApiHost
            val apDomain = resultApDomain
            val ta = resultTootAccount
            var sa = resultSavedAccount
            if (ta != null && apiHost?.isValid == true && sa == null) {
                val acct = Acct.parse(ta.username, apDomain ?: apiHost)
                // アカウント追加時に、アプリ内に既にあるアカウントと同じものを登録していたかもしれない
                sa = SavedAccount.loadAccountByAcct(applicationContext, acct.ascii)
            }
            afterAccountVerify(result, ta, sa, apiHost, apDomain)
        }
    }
}

fun ActMain.afterAccountVerify(
    result: TootApiResult?,
    ta: TootAccount?,
    sa: SavedAccount?,
    apiHost: Host?,
    apDomain: Host?,
): Boolean {
    result ?: return false

    val jsonObject = result.jsonObject
    val tokenInfo = result.tokenInfo
    val error = result.error

    when {
        error != null -> showToast(true, "${result.error} ${result.requestInfo}".trim())
        tokenInfo == null -> showToast(true, "can't get access token.")
        jsonObject == null -> showToast(true, "can't parse json response.")

        // 自分のユーザネームを取れなかった
        // …普通はエラーメッセージが設定されてるはずだが
        ta == null -> showToast(true, "can't verify user credential.")

        // アクセストークン更新時
        // インスタンスは同じだと思うが、ユーザ名が異なる可能性がある
        sa != null -> return afterAccessTokenUpdate(ta, sa, tokenInfo)

        apiHost != null -> return afterAccountAdd(apDomain, apiHost, ta, jsonObject, tokenInfo)
    }
    return false
}

private fun ActMain.afterAccessTokenUpdate(
    ta: TootAccount,
    sa: SavedAccount,
    tokenInfo: JsonObject?,
): Boolean {
    if (sa.username != ta.username) {
        showToast(true, R.string.user_name_not_match)
        return false
    }

    // DBの情報を更新する
    sa.updateTokenInfo(tokenInfo)

    // 各カラムの持つアカウント情報をリロードする
    reloadAccountSetting()

    // 自動でリロードする
    appState.columnList
        .filter { it.accessInfo == sa }
        .forEach { it.startLoading() }

    // 通知の更新が必要かもしれない
    PushSubscriptionHelper.clearLastCheck(sa)
    checkNotificationImmediateAll(this, onlySubscription = true)
    checkNotificationImmediate(this, sa.db_id)

    showToast(false, R.string.access_token_updated_for, sa.acct.pretty)
    return true
}

private fun ActMain.afterAccountAdd(
    apDomain: Host?,
    apiHost: Host,
    ta: TootAccount,
    jsonObject: JsonObject,
    tokenInfo: JsonObject,
): Boolean {
    // アカウント追加時
    val user = Acct.parse(ta.username, apDomain ?: apiHost)

    val rowId = SavedAccount.insert(
        acct = user.ascii,
        host = apiHost.ascii,
        domain = (apDomain ?: apiHost).ascii,
        account = jsonObject,
        token = tokenInfo,
        misskeyVersion = TootInstance.parseMisskeyVersion(tokenInfo)
    )
    val account = SavedAccount.loadAccount(applicationContext, rowId)
    if (account == null) {
        showToast(false, "loadAccount failed.")
        return false
    }

    var bModified = false

    if (account.loginAccount?.locked == true) {
        bModified = true
        account.visibility = TootVisibility.PrivateFollowers
    }

    if (!account.isMisskey) {
        val source = ta.source
        if (source != null) {
            val privacy = TootVisibility.parseMastodon(source.privacy)
            if (privacy != null) {
                bModified = true
                account.visibility = privacy
            }

            // XXX ta.source.sensitive パラメータを読んで「添付画像をデフォルトでNSFWにする」を実現する
            // 現在、アカウント設定にはこの項目はない( 「NSFWな添付メディアを隠さない」はあるが全く別の効果)
        }

        if (bModified) {
            account.saveSetting()
        }
    }

    // 適当にカラムを追加する
    addColumn(false, defaultInsertPosition, account, ColumnType.HOME)
    if (SavedAccount.count == 1) {
        addColumn(false, defaultInsertPosition, account, ColumnType.NOTIFICATIONS)
        addColumn(false, defaultInsertPosition, account, ColumnType.LOCAL)
        addColumn(false, defaultInsertPosition, account, ColumnType.FEDERATE)
    }

    // 通知の更新が必要かもしれない
    checkNotificationImmediateAll(this, onlySubscription = true)
    checkNotificationImmediate(this, account.db_id)
    showToast(false, R.string.account_confirmed)
    return true
}

fun ActMain.handleSharedIntent(intent: Intent) {
    launchMain {
        ActMain.sharedIntent2 = intent
        val ai = pickAccount(
            bAllowPseudo = false,
            bAuto = true,
            message = getString(R.string.account_picker_toot),
        )
        ActMain.sharedIntent2 = null
        ai?.let { openActPostImpl(it.db_id, sharedIntent = intent) }
    }
}
