package jp.juggler.subwaytooter.action

import android.os.Build
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
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.subwaytooter.util.openBrowser
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.encodePercent
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.network.toFormRequestBody
import jp.juggler.util.network.toPost
import jp.juggler.util.ui.dismissSafe

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
    @Suppress("UNUSED_ANONYMOUS_PARAMETER")
    showLoginForm { dialogHost, apiHost, serverInfo, action ->
        launchMain {
            try {
                when (action) {
                    LoginForm.Action.Login -> {
                        val authUri = runApiTask2(apiHost) { it.authStep1() }
                        log.i("authUri=$authUri")
                        openBrowser(authUri)
                        dialogHost.dismissSafe()
                    }

                    LoginForm.Action.Pseudo -> {
                        val tootInstance = runApiTask2(apiHost) { TootInstance.getOrThrow(it) }
                        addPseudoAccount(apiHost, tootInstance)?.let { a ->
                            showToast(false, R.string.server_confirmed)
                            addColumn(defaultInsertPosition, a, ColumnType.LOCAL, protect = true)
                            dialogHost.dismissSafe()
                        }
                    }
//                    LoginForm.Action.Create ->
//                        createUser(apiHost, serverInfo) { dialogHost.dismissSafe() }
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
suspend fun ActMain.accessTokenPrompt(
    apiHost: Host,
    onComplete: (() -> Unit)? = null,
) {
    showTextInputDialog(
        title = getString(R.string.access_token_or_api_token),
        initialText = null,
        onEmptyText = { showToast(true, R.string.token_not_specified) },
    ) { text ->
        try {
            val accessToken = text.trim()
            val auth2Result = runApiTask2(apiHost) { client ->
                val ti = TootInstance.getExOrThrow(client, forceAccessToken = accessToken)

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
            when (afterAccountVerify(auth2Result)) {
                false -> false
                else -> {
                    onComplete?.invoke()
                    true
                }
            }
        } catch (ex: Throwable) {
            showApiError(ex)
            false
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
