package jp.juggler.subwaytooter.action

import android.app.Dialog
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.actmain.addColumn
import jp.juggler.subwaytooter.actmain.afterAccountVerify
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.auth.Auth2Result
import jp.juggler.subwaytooter.api.auth.MastodonAuth
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.dialog.*
import jp.juggler.subwaytooter.notification.APP_SERVER
import jp.juggler.subwaytooter.pref.*
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.subwaytooter.util.openBrowser
import jp.juggler.util.*
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.coroutine.launchIO
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.buildJsonObject
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

private fun ActMain.accountCreate(
    apiHost: Host,
    clientInfo: JsonObject,
    dialogHost: Dialog,
) {
    val activity = this
    DlgCreateAccount(
        activity,
        apiHost
    ) { dialog_create, username, email, password, agreement, reason ->
        // dialog引数が二つあるのに注意
        launchMain {
            try {
                val auth2Result = runApiTask2(apiHost) { client ->
                    // Mastodon限定
                    val misskeyVersion = 0 // TootInstance.parseMisskeyVersion(tokenJson)

                    val auth = MastodonAuth(client)

                    val tokenJson = auth.createUser(
                        clientInfo,
                        username,
                        email,
                        password,
                        agreement,
                        reason
                    )

                    val accessToken = tokenJson.string("access_token")
                        ?: error("can't get user access token")

                    var accountJson = auth.verifyAccount(
                        accessToken = accessToken,
                        outTokenJson = tokenJson,
                        misskeyVersion = misskeyVersion
                    )

                    client.apiHost = apiHost
                    val (ti, ri) = TootInstance.getEx(client, forceAccessToken = accessToken)
                    ti ?: error("missing server information. ${ri?.error}")

                    val parser = TootParser(
                        activity,
                        linkHelper = LinkHelper.create(ti)
                    )

                    var ta = parser.account(accountJson)
                    if (ta == null) {
                        accountJson = buildJsonObject {
                            put("id", EntityId.CONFIRMING.toString())
                            put("username", username)
                            put("acct", username)
                            put("url", "https://$apiHost/@$username")
                        }
                        ta = parser.account(accountJson)!!
                    }
                    Auth2Result(
                        tootInstance = ti,
                        accountJson = accountJson,
                        tootAccount = ta,
                        tokenJson = tokenJson,
                    )
                }
                val verified = activity.afterAccountVerify(auth2Result)
                if (verified) {
                    dialogHost.dismissSafe()
                    dialog_create.dismissSafe()
                }
            } catch (ex: Throwable) {
                showApiError(ex)
            }
        }
    }.show()
}

// アカウントの追加
fun ActMain.accountAdd() {
    val activity = this
    LoginForm.showLoginForm(this, null) { dialogHost, instance, action ->
        launchMain {
            try {
                when (action) {
                    // ログイン画面を開く
                    LoginForm.Action.Existing ->
                        runApiTask2(instance) { client ->
                            val authUri = client.authStep1()
                            withContext(AppDispatchers.MainImmediate) {
                                openBrowser(authUri)
                                dialogHost.dismissSafe()
                            }
                        }

                    // ユーザ作成
                    LoginForm.Action.Create ->
                        runApiTask2(instance) { client ->
                            val clientInfo = client.prepareClient()
                            withContext(AppDispatchers.MainImmediate) {
                                accountCreate(instance, clientInfo, dialogHost)
                            }
                        }

                    // 疑似アカウント
                    LoginForm.Action.Pseudo ->
                        runApiTask2(instance) { client ->
                            val (ti, ri) = TootInstance.get(client)
                            ti ?: error("${ri?.error}")
                            withContext(AppDispatchers.MainImmediate) {
                                addPseudoAccount(instance, ti)?.let { a ->
                                    showToast(false, R.string.server_confirmed)
                                    val pos = activity.appState.columnCount
                                    addColumn(pos, a, ColumnType.LOCAL)
                                    dialogHost.dismissSafe()
                                }
                            }
                        }

                    LoginForm.Action.Token ->
                        runApiTask2(instance) { client ->
                            val (ti, ri) = TootInstance.get(client)
                            ti ?: error("${ri?.error}")
                            withContext(AppDispatchers.MainImmediate) {
                                DlgTextInput.show(
                                    activity,
                                    getString(R.string.access_token_or_api_token),
                                    null,
                                    callback = object : DlgTextInput.Callback {
                                        override fun onEmptyError() {
                                            showToast(true, R.string.token_not_specified)
                                        }

                                        override fun onOK(dialog: Dialog, text: String) {
                                            // dialog引数が二つあるのに注意
                                            activity.checkAccessToken(
                                                dialogHost = dialogHost,
                                                dialogToken = dialog,
                                                apiHost = instance,
                                                accessToken = text,
                                            )
                                        }
                                    }
                                )
                            }
                        }
                }
            } catch (ex: Throwable) {
                showApiError(ex)
            }
        }
    }
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

// アクセストークンを手動で入力した場合
fun ActMain.checkAccessToken(
    dialogHost: Dialog?,
    dialogToken: Dialog?,
    apiHost: Host,
    accessToken: String,
) {
    launchMain {
        try {
            val auth2Result = runApiTask2(apiHost) { client ->
                val (ti, ri) = TootInstance.getEx(client, forceAccessToken = accessToken)
                ti ?: error("missing uri in Instance Information. ${ri?.error}")

                val tokenJson = JsonObject()

                val userJson = client.getUserCredential(
                    accessToken,
                    outTokenInfo = tokenJson, // 更新される
                    misskeyVersion = ti.misskeyVersionMajor
                )

                val parser = TootParser(this, linkHelper = LinkHelper.create(ti))

                Auth2Result(
                    tootInstance = ti,
                    accountJson = userJson,
                    tootAccount = parser.account(userJson)
                        ?: error("can't parse user information."),
                    tokenJson = tokenJson,
                )
            }
            val verified = afterAccountVerify(auth2Result)
            if (verified) {
                dialogHost?.dismissSafe()
                dialogToken?.dismissSafe()
            }
        } catch (ex: Throwable) {
            showApiError(ex)
        }
    }
}

// アクセストークンの手動入力(更新)
fun ActMain.checkAccessToken2(dbId: Long) {
    val apiHost = SavedAccount.loadAccount(this, dbId)
        ?.apiHost
        ?: return

    DlgTextInput.show(
        this,
        getString(R.string.access_token_or_api_token),
        null,
        callback = object : DlgTextInput.Callback {
            override fun onEmptyError() {
                showToast(true, R.string.token_not_specified)
            }

            override fun onOK(dialog: Dialog, text: String) {
                checkAccessToken(
                    dialogHost = null,
                    dialogToken = dialog,
                    apiHost = apiHost,
                    accessToken = text,
                )
            }
        })
}
