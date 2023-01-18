package jp.juggler.subwaytooter.action

import android.app.Dialog
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.actmain.addColumn
import jp.juggler.subwaytooter.actmain.afterAccountVerify
import jp.juggler.subwaytooter.actmain.defaultInsertPosition
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.auth.Auth2Result
import jp.juggler.subwaytooter.api.auth.AuthBase
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.dialog.*
import jp.juggler.subwaytooter.dialog.DlgCreateAccount.Companion.showUserCreateDialog
import jp.juggler.subwaytooter.dialog.LoginForm.Companion.showLoginForm
import jp.juggler.subwaytooter.notification.APP_SERVER
import jp.juggler.subwaytooter.pref.*
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.subwaytooter.util.openBrowser
import jp.juggler.util.*
import jp.juggler.util.coroutine.launchIO
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.encodePercent
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.network.toFormRequestBody
import jp.juggler.util.network.toPost
import jp.juggler.util.ui.dismissSafe
import kotlinx.coroutines.*
import ru.gildor.coroutines.okhttp.await

private val log = LogCategory("Action_Account")

// Androidでは \w や \d がUnicode文字にマッチしてしまうので、IDEの警告を無視する
@Suppress("RegExpSimplifiable")
private val mailRegex =
    """\A[a-z0-9_+&*-]+(?:\.[a-z0-9_+&*-]+)*@(?:[a-z0-9-]+\.)+[a-z]{2,12}\z""".toRegex(
        RegexOption.IGNORE_CASE
    )

fun isAndroid7TlsBug(errorText: String) =
    if (!errorText.contains("SSLHandshakeException")) {
        false
    } else {
        val release = Build.VERSION.RELEASE
        when {
            release.startsWith("7.1.") -> false // 含まない 7.1.x
            release.startsWith("7.1") -> true   // 含む 7.1
            release.startsWith("7.0") -> true   // 含む 7.0
            else -> false
        }
    }

/**
 * サイドメニューで「アカウントの追加」を選ぶと呼び出される。
 * - サーバ名とアクションを指定するダイアログを開く。
 * - 選択されたアクションに応じて分岐する。
 */
fun ActMain.accountAdd() {
    showLoginForm { dialogHost, apiHost, serverInfo, action ->
        launchMain {
            try {
                when (action) {
                    LoginForm.Action.Login -> {
                        val authUri = runApiTask2(apiHost) { it.authStep1() }
                        openBrowser(authUri)
                        dialogHost.dismissSafe()
                    }
                    LoginForm.Action.Pseudo -> {
                        val tootInstance = runApiTask2(apiHost) { TootInstance.getOrThrow(it) }
                        addPseudoAccount(apiHost, tootInstance)?.let { a ->
                            showToast(false, R.string.server_confirmed)
                            addColumn(defaultInsertPosition, a, ColumnType.LOCAL)
                            dialogHost.dismissSafe()
                        }
                    }
                    LoginForm.Action.Create ->
                        createUser(apiHost, serverInfo) { dialogHost.dismissSafe() }
                    LoginForm.Action.Token ->
                        accessTokenPrompt(apiHost) { dialogHost.dismissSafe() }
                }
            } catch (ex: Throwable) {
                showApiError(ex)
            }
        }
    }
}

private suspend fun ActMain.createUser(
    apiHost: Host,
    serverInfo: TootInstance?,
    onComplete: (() -> Unit)? = null,
) {
    serverInfo ?: error(
        getString(
            R.string.user_creation_not_supported,
            apiHost.pretty,
            "(unknown)",
        )
    )

    fun TootApiClient.authUserCreate() =
        AuthBase.findAuthForCreateUser(this, serverInfo)
            ?: error(
                getString(
                    R.string.user_creation_not_supported,
                    apiHost.pretty,
                    serverInfo.instanceType.toString(),
                )
            )

    // クライアント情報を取得。サーバ種別によってはユーザ作成ができないのでエラーとなる
    val clientInfo = runApiTask2(apiHost) {
        it.authUserCreate().prepareClient(serverInfo)
    }

    showUserCreateDialog(apiHost) { dialogCreate, params ->
        launchMain {
            try {
                val auth2Result = runApiTask2(apiHost) {
                    it.authUserCreate().createUser(clientInfo = clientInfo, params = params)
                }
                if (afterAccountVerify(auth2Result)) {
                    dialogCreate.dismissSafe()
                    onComplete?.invoke()
                }
            } catch (ex: Throwable) {
                showApiError(ex)
            }
        }
    }
}

/**
 * アクセストークンを手動入力する。
 *
 * @param apiHost アクセストークンと関係のあるサーバのAPIホスト
 * @param onComplete 非nullならアカウント認証が終わったタイミングで呼ばれる
 */
// アクセストークンの手動入力(更新)
fun ActMain.accessTokenPrompt(
    apiHost: Host,
    onComplete: (() -> Unit)? = null,
) {
    DlgTextInput.show(
        this,
        getString(R.string.access_token_or_api_token),
        null,
        callback = object : DlgTextInput.Callback {
            override fun onEmptyError() {
                showToast(true, R.string.token_not_specified)
            }

            override fun onOK(dialog: Dialog, text: String) {
                launchMain {
                    try {
                        val accessToken = text.trim()
                        val auth2Result = runApiTask2(apiHost) { client ->
                            val ti =
                                TootInstance.getExOrThrow(client, forceAccessToken = accessToken)

                            val tokenJson = JsonObject()

                            val userJson = client.verifyAccount(
                                accessToken,
                                outTokenInfo = tokenJson, // 更新される
                                misskeyVersion = ti.misskeyVersionMajor
                            )

                            val parser = TootParser(this, linkHelper = LinkHelper.create(ti))

                            Auth2Result(
                                tootInstance = ti,
                                tokenJson = tokenJson,
                                accountJson = userJson,
                                tootAccount = parser.account(userJson)
                                    ?: error("can't parse user information."),
                            )
                        }
                        if (afterAccountVerify(auth2Result)) {
                            dialog.dismissSafe()
                            onComplete?.invoke()
                        }
                    } catch (ex: Throwable) {
                        showApiError(ex)
                    }
                }
            }
        }
    )
}

fun AppCompatActivity.accountRemove(account: SavedAccount) {
    // if account is default account of tablet mode,
    // reset default.
    val pref = pref()
    if (account.db_id == PrefL.lpTabletTootDefaultAccount(pref)) {
        pref.edit().put(PrefL.lpTabletTootDefaultAccount, -1L).apply()
    }

    account.delete()
    appServerUnregister(applicationContext, account)
}

private fun appServerUnregister(context: Context, account: SavedAccount) {
    launchIO {
        try {
            val installId = PrefDevice.from(context).getString(PrefDevice.KEY_INSTALL_ID, null)
            if (installId?.isEmpty() != false) {
                error("missing install_id")
            }

            val tag = account.notification_tag
            if (tag?.isEmpty() != false) {
                error("missing notification_tag")
            }

            val call = App1.ok_http_client.newCall(
                "instance_url=${
                    "https://${account.apiHost.ascii}".encodePercent()
                }&app_id=${
                    context.packageName.encodePercent()
                }&tag=$tag"
                    .toFormRequestBody()
                    .toPost()
                    .url("$APP_SERVER/unregister")
                    .build()
            )

            val response = call.await()
            if (!response.isSuccessful) {
                log.e("appServerUnregister: $response")
            }
        } catch (ex: Throwable) {
            log.e(ex, "appServerUnregister failed.")
        }
    }
}

// アカウント設定
fun ActMain.accountOpenSetting() {
    launchMain {
        pickAccount(
            bAllowPseudo = true,
            bAuto = true,
            message = getString(R.string.account_picker_open_setting)
        )?.let {
            arAccountSetting.launch(ActAccountSetting.createIntent(this@accountOpenSetting, it))
        }
    }
}

fun ActMain.accountResendConfirmMail(accessInfo: SavedAccount) {
    DlgConfirmMail(
        this,
        accessInfo
    ) { email ->
        launchMain {
            runApiTask(accessInfo) { client ->
                email?.let {
                    if (!mailRegex.matches(it)) {
                        return@runApiTask TootApiResult("email address is not valid.")
                    }
                }

                client.request(
                    "/api/v1/emails/confirmations",
                    ArrayList<String>().apply {
                        if (email != null) add("email=${email.encodePercent()}")
                    }.joinToString("&").toFormRequestBody().toPost()
                )
            }?.let { result ->
                when (val error = result.error) {
                    null -> showToast(true, R.string.resend_confirm_mail_requested)
                    else -> showToast(true, error)
                }
            }
        }
    }.show()
}

//
fun accountListReorder(
    src: List<SavedAccount>,
    pickupHost: Host?,
    filter: (SavedAccount) -> Boolean = { true },
): MutableList<SavedAccount> {
    val listSameHost = java.util.ArrayList<SavedAccount>()
    val listOtherHost = java.util.ArrayList<SavedAccount>()
    for (a in src) {
        if (!filter(a)) continue
        when (pickupHost) {
            null, a.apDomain, a.apiHost -> listSameHost
            else -> listOtherHost
        }.add(a)
    }
    SavedAccount.sort(listSameHost)
    SavedAccount.sort(listOtherHost)
    listSameHost.addAll(listOtherHost)
    return listSameHost
}

// 疑似アカ以外のアカウントのリスト
fun Context.accountListNonPseudo(
    pickupHost: Host?,
) = accountListReorder(
    SavedAccount.loadAccountList(this),
    pickupHost
) { !it.isPseudo }

// 条件でフィルタする。サーバ情報を読む場合がある。
suspend fun Context.accountListWithFilter(
    pickupHost: Host?,
    check: suspend (TootApiClient, SavedAccount) -> Boolean,
): MutableList<SavedAccount>? {
    var resultList: MutableList<SavedAccount>? = null
    runApiTask { client ->
        supervisorScope {
            resultList = SavedAccount.loadAccountList(this@accountListWithFilter)
                .map {
                    async {
                        try {
                            if (check(client, it)) it else null
                        } catch (ex: Throwable) {
                            log.e(ex, "accountListWithFilter failed.")
                            null
                        }
                    }
                }
                .mapNotNull { it.await() }
                .let { accountListReorder(it, pickupHost) }
        }
        if (client.isApiCancelled()) null else TootApiResult()
    }
    return resultList
}

suspend fun ActMain.accountListCanQuote(pickupHost: Host? = null) =
    accountListWithFilter(pickupHost) { client, a ->
        when {
            client.isApiCancelled() -> false
            a.isPseudo -> false
            a.isMisskey -> true
            else -> {
                val (ti, ri) = TootInstance.getEx(client.copy(), account = a)
                if (ti == null) {
                    ri?.error?.let { log.w(it) }
                    false
                } else InstanceCapability.quote(ti)
            }
        }
    }

suspend fun ActMain.accountListCanReaction(pickupHost: Host? = null) =
    accountListWithFilter(pickupHost) { client, a ->
        when {
            client.isApiCancelled() -> false
            a.isPseudo -> false
            a.isMisskey -> true
            else -> {
                val (ti, ri) = TootInstance.getEx(client.copy(), account = a)
                if (ti == null) {
                    ri?.error?.let { log.w(it) }
                    false
                } else InstanceCapability.emojiReaction(a, ti)
            }
        }
    }

suspend fun ActMain.accountListCanSeeMyReactions(pickupHost: Host? = null) =
    accountListWithFilter(pickupHost) { client, a ->
        when {
            client.isApiCancelled() -> false
            a.isPseudo -> false
            else -> {
                val (ti, ri) = TootInstance.getEx(client.copy(), account = a)
                if (ti == null) {
                    ri?.error?.let { log.w(it) }
                    false
                } else InstanceCapability.listMyReactions(a, ti)
            }
        }
    }
