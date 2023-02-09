package jp.juggler.subwaytooter.actmain

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.BuildConfig
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.action.conversationOtherInstance
import jp.juggler.subwaytooter.action.openActPostImpl
import jp.juggler.subwaytooter.action.userProfile
import jp.juggler.subwaytooter.api.auth.Auth2Result
import jp.juggler.subwaytooter.api.auth.AuthBase
import jp.juggler.subwaytooter.api.auth.authRepo
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootStatus.Companion.findStatusIdFromUrl
import jp.juggler.subwaytooter.api.entity.TootVisibility
import jp.juggler.subwaytooter.api.runApiTask2
import jp.juggler.subwaytooter.api.showApiError
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.column.startLoading
import jp.juggler.subwaytooter.dialog.actionsDialog
import jp.juggler.subwaytooter.dialog.pickAccount
import jp.juggler.subwaytooter.dialog.runInProgress
import jp.juggler.subwaytooter.notification.checkNotificationImmediate
import jp.juggler.subwaytooter.notification.checkNotificationImmediateAll
import jp.juggler.subwaytooter.notification.recycleClickedNotification
import jp.juggler.subwaytooter.pref.PrefDevice
import jp.juggler.subwaytooter.pref.prefDevice
import jp.juggler.subwaytooter.push.fcmHandler
import jp.juggler.subwaytooter.push.pushRepo
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.daoSavedAccount
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.decodePercent
import jp.juggler.util.data.groupEx
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.queryIntentActivitiesCompat
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.unifiedpush.android.connector.UnifiedPush

private val log = LogCategory("ActMainIntent")

// ActOAuthCallbackで受け取ったUriを処理する
fun ActMain.handleIntentUri(uri: Uri) {
    try {
        log.i("handleIntentUri $uri")
        when (uri.scheme) {
            BuildConfig.customScheme -> handleCustomSchemaUri(uri)
            else -> handleOtherUri(uri)
        }
    } catch (ex: Throwable) {
        log.e(ex, "handleIntentUri failed.")
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
        // Android 6.0以降
        // MATCH_DEFAULT_ONLY だと標準の設定に指定されたアプリがあるとソレしか出てこない
        // MATCH_ALL を指定すると 以前と同じ挙動になる
        val queryFlag = PackageManager.MATCH_ALL

        // queryIntentActivities に渡すURLは実在しないホストのものにする
        val intent = Intent(Intent.ACTION_VIEW, "https://dummy.subwaytooter.club/".toUri())
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        val myName = packageName
        val resolveInfoList = packageManager.queryIntentActivitiesCompat(intent, queryFlag)
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
        log.e(ex, "can't open app to handle intent.")
    }

    AlertDialog.Builder(this)
        .setCancelable(true)
        .setMessage(errorMessage)
        .setPositiveButton(R.string.close, null)
        .show()
    return false
}

private fun ActMain.handleCustomSchemaUri(uri: Uri) = launchAndShowError {
    val dataIdString = uri.getQueryParameter("db_id")
    if (dataIdString == null) {
        // OAuth2 認証コールバック
        // subwaytooter://oauth(\d*)/?...
        handleOAuth2Callback(uri)
    } else {
        // ${BuildConfig.customScheme}://notification_click/?db_id=(db_id)
        handleNotificationClick(uri, dataIdString)
    }
}

private fun ActMain.handleNotificationClick(uri: Uri, dataIdString: String) {
    try {
        val account = dataIdString.toLongOrNull()
            ?.let { daoSavedAccount.loadAccount(it) }
        if (account == null) {
            showToast(true, "handleNotificationClick: missing SavedAccount. id=$dataIdString")
            return
        }

        pushRepo.onTapNotification(account)

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
        log.e(ex, "handleNotificationClick failed.")
    }
}

private fun ActMain.handleOAuth2Callback(uri: Uri) {
    launchMain {
        try {
            val auth2Result = runApiTask2 { client ->
                AuthBase.findAuthForAuthCallback(client, uri.toString())
                    .authStep2(uri)
            }
            afterAccountVerify(auth2Result)
        } catch (ex: Throwable) {
            showApiError(ex)
        }
    }
}

val accountVerifyMutex = Mutex()

/**
 * アカウントを確認した後に呼ばれる
 * @return 何かデータを更新したら真
 */
suspend fun ActMain.afterAccountVerify(auth2Result: Auth2Result): Boolean = auth2Result.run {
    accountVerifyMutex.withLock {
        // ユーザ情報中のacctはfull acct ではないので、組み立てる
        val newAcct = Acct.parse(tootAccount.username, apDomain)

        // full acctだよな？
        """\A[^@]+@[^@]+\z""".toRegex().find(newAcct.ascii)
            ?: error("afterAccountAdd: incorrect userAcct. ${newAcct.ascii}")

        // 「アカウント追加のハズが既存アカウントで認証していた」
        // 「アクセストークン更新のハズが別アカウントで認証していた」
        // などを防止するため、full acctでアプリ内DBを検索
        when (val sa = daoSavedAccount.loadAccountByAcct(newAcct)) {
            null -> afterAccountAdd(newAcct, auth2Result)
            else -> afterAccessTokenUpdate(auth2Result, sa)
        }
    }
}

private suspend fun ActMain.afterAccessTokenUpdate(
    auth2Result: Auth2Result,
    sa: SavedAccount,
): Boolean {
    log.i("afterAccessTokenUpdate token ${sa.bearerAccessToken ?: sa.misskeyApiToken} =>${auth2Result.tokenJson}")
    // DBの情報を更新する
    authRepo.updateTokenInfo(sa, auth2Result)

    // 各カラムの持つアカウント情報をリロードする
    reloadAccountSetting(daoSavedAccount.loadAccountList())

    // 自動でリロードする
    appState.columnList
        .filter { it.accessInfo == sa }
        .forEach { it.startLoading() }

    // 通知の更新が必要かもしれない
    checkNotificationImmediateAll(this, onlyEnqueue = true)
    checkNotificationImmediate(this, sa.db_id)
    updatePushDistributer()

    showToast(false, R.string.access_token_updated_for, sa.acct.pretty)
    return true
}

private suspend fun ActMain.afterAccountAdd(
    newAcct: Acct,
    auth2Result: Auth2Result,
): Boolean {
    val ta = auth2Result.tootAccount

    val rowId = daoSavedAccount.saveNew(
        acct = newAcct.ascii,
        host = auth2Result.apiHost.ascii,
        domain = auth2Result.apDomain.ascii,
        account = auth2Result.accountJson,
        token = auth2Result.tokenJson,
        misskeyVersion = auth2Result.tootInstance.misskeyVersionMajor,
    )
    val account = daoSavedAccount.loadAccount(rowId)
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
            daoSavedAccount.save(account)
        }
    }

    // 適当にカラムを追加する
    addColumn(false, defaultInsertPosition, account, ColumnType.HOME, protect = true)
    if (daoSavedAccount.isSingleAccount()) {
        addColumn(false, defaultInsertPosition, account, ColumnType.NOTIFICATIONS, protect = true)
        addColumn(false, defaultInsertPosition, account, ColumnType.LOCAL, protect = true)
        addColumn(false, defaultInsertPosition, account, ColumnType.FEDERATE, protect = true)
    }

    // 通知の更新が必要かもしれない
    checkNotificationImmediateAll(this, onlyEnqueue = true)
    checkNotificationImmediate(this, account.db_id)
    updatePushDistributer()
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

// アカウントを追加/更新したらappServerHashの取得をやりなおす
suspend fun ActMain.updatePushDistributer() {
    when {
        fcmHandler.noFcm && prefDevice.pushDistributor.isNullOrEmpty() -> {
            selectPushDistributor()
            // 選択しなかった場合は購読の更新を行わない
        }
        else -> {
            runInProgress(cancellable = false) { reporter ->
                withContext(AppDispatchers.DEFAULT) {
                    pushRepo.switchDistributor(
                        prefDevice.pushDistributor,
                        reporter = reporter
                    )
                }
            }
        }
    }
}

fun AppCompatActivity.selectPushDistributor() {
    val context = this
    launchAndShowError {
        val prefDevice = prefDevice
        val lastDistributor = prefDevice.pushDistributor

        fun String.appendChecked(checked: Boolean) = when (checked) {
            true -> "$this ✅"
            else -> this
        }

        actionsDialog(getString(R.string.select_push_delivery_service)) {
            if (fcmHandler.hasFcm) {
                action(
                    getString(R.string.firebase_cloud_messaging)
                        .appendChecked(lastDistributor == PrefDevice.PUSH_DISTRIBUTOR_FCM)
                ) {
                    runInProgress(cancellable = false) { reporter ->
                        withContext(AppDispatchers.DEFAULT) {
                            pushRepo.switchDistributor(
                                PrefDevice.PUSH_DISTRIBUTOR_FCM,
                                reporter = reporter
                            )
                        }
                    }
                }
            }
            for (packageName in UnifiedPush.getDistributors(
                context,
                features = ArrayList(listOf(UnifiedPush.FEATURE_BYTES_MESSAGE))
            )) {
                action(
                    packageName.appendChecked(lastDistributor == packageName)
                ) {
                    runInProgress(cancellable = false) { reporter ->
                        withContext(AppDispatchers.DEFAULT) {
                            pushRepo.switchDistributor(
                                packageName,
                                reporter = reporter
                            )
                        }
                    }
                }
            }
            action(
                getString(R.string.none)
                    .appendChecked(lastDistributor == PrefDevice.PUSH_DISTRIBUTOR_NONE)
            ) {
                runInProgress(cancellable = false) { reporter ->
                    withContext(AppDispatchers.DEFAULT) {
                        pushRepo.switchDistributor(
                            PrefDevice.PUSH_DISTRIBUTOR_NONE,
                            reporter = reporter
                        )
                    }
                }
            }
        }
    }
}
