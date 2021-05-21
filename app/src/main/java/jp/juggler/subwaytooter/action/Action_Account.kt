package jp.juggler.subwaytooter.action

import android.app.Dialog
import android.os.Build
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.*
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.subwaytooter.util.openBrowser
import jp.juggler.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Action_Account {

    @Suppress("unused")
    private val log = LogCategory("Action_Account")

    // アカウントの追加
    fun add(activity: ActMain) {

        LoginForm.showLoginForm(
            activity,
            null
        ) { dialog, instance, action ->
            TootTaskRunner(activity).run(instance, object : TootTask {

                override suspend fun background(client: TootApiClient): TootApiResult? {
                    return when (action) {

                        LoginForm.Action.Existing ->
                            client.authentication1(Pref.spClientName(activity))

                        LoginForm.Action.Create ->
                            client.createUser1(Pref.spClientName(activity))

                        LoginForm.Action.Pseudo,
                        LoginForm.Action.Token -> {
                            val (ti, ri) = TootInstance.get(client)
                            if (ti != null) ri?.data = ti
                            ri
                        }
                    }
                }

                override suspend fun handleResult(result: TootApiResult?) {

                    result ?: return  // cancelled.

                    val data = result.data
                    if (result.error == null && data != null) {
                        when (action) {
                            LoginForm.Action.Existing -> if (data is String) {
                                // ブラウザ用URLが生成された
                                activity.openBrowser(data.toUri())
                                dialog.dismissSafe()
                                return
                            }

                            LoginForm.Action.Create -> if (data is JsonObject) {
                                // インスタンスを確認できた
                                createAccount(
                                    activity,
                                    instance,
                                    data,
                                    dialog
                                )
                                return
                            }

                            LoginForm.Action.Pseudo -> if (data is TootInstance) {
                                addPseudoAccount(
                                    activity,
                                    instance,
                                    instanceInfo = data
                                ) { a ->
                                    activity.showToast(false, R.string.server_confirmed)
                                    val pos = activity.app_state.columnCount
                                    activity.addColumn(pos, a, ColumnType.LOCAL)
                                    dialog.dismissSafe()
                                }
                            }

                            LoginForm.Action.Token -> if (data is TootInstance) {
                                DlgTextInput.show(
                                    activity,
                                    activity.getString(R.string.access_token_or_api_token),
                                    null,
                                    callback = object : DlgTextInput.Callback {

                                        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
                                        override fun onOK(
                                            dialog_token: Dialog,
                                            text: String
                                        ) {

                                            // dialog引数が二つあるのに注意
                                            activity.checkAccessToken(
                                                dialog,
                                                dialog_token,
                                                instance,
                                                text,
                                                null
                                            )

                                        }

                                        override fun onEmptyError() {
                                            activity.showToast(true, R.string.token_not_specified)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    val errorText = result.error ?: "(no error information)"
                    if (errorText.contains("SSLHandshakeException")
                        && (Build.VERSION.RELEASE.startsWith("7.0")
                            || Build.VERSION.RELEASE.startsWith("7.1")
                            && !Build.VERSION.RELEASE.startsWith("7.1.")
                            )
                    ) {
                        AlertDialog.Builder(activity)
                            .setMessage(errorText + "\n\n" + activity.getString(R.string.ssl_bug_7_0))
                            .setNeutralButton(R.string.close, null)
                            .show()
                    } else {
                        activity.showToast(true, "$errorText ${result.requestInfo}".trim())
                    }
                }
            })
        }
    }

    private fun createAccount(
        activity: ActMain,
        apiHost: Host,
        client_info: JsonObject,
        dialog_host: Dialog
    ) {
        DlgCreateAccount(
            activity,
            apiHost
        ) { dialog_create, username, email, password, agreement, reason ->
            // dialog引数が二つあるのに注意
            TootTaskRunner(activity).run(apiHost, object : TootTask {

                var ta: TootAccount? = null
                var apDomain: Host? = null

                override suspend fun background(client: TootApiClient): TootApiResult? {
                    val r1 = client.createUser2Mastodon(
                        client_info,
                        username,
                        email,
                        password,
                        agreement,
                        reason
                    )
                    val tokenJson = r1?.jsonObject ?: return r1
                    val misskeyVersion = TootInstance.parseMisskeyVersion(tokenJson)
                    val parser = TootParser(
                        activity,
                        linkHelper = LinkHelper.create(apiHost, misskeyVersion = misskeyVersion)
                    )

                    // ここだけMastodon専用
                    val access_token = tokenJson.string("access_token")
                        ?: return TootApiResult("can't get user access token")

                    client.apiHost = apiHost
                    val (ti, ri) = TootInstance.getEx(client, forceAccessToken = access_token)
                    ti ?: return ri
                    this.apDomain = ti.uri?.let { Host.parse(it) }

                    val r2 = client.getUserCredential(access_token, misskeyVersion = misskeyVersion)
                    this.ta = parser.account(r2?.jsonObject)
                    if (this.ta != null) return r2

                    val jsonObject = jsonObject {
                        put("id", EntityId.CONFIRMING.toString())
                        put("username", username)
                        put("acct", username)
                        put("url", "https://$apiHost/@$username")
                    }

                    this.ta = parser.account(jsonObject)
                    r1.data = jsonObject
                    r1.tokenInfo = tokenJson
                    return r1
                }

                override suspend fun handleResult(result: TootApiResult?) {
                    val sa: SavedAccount? = null
                    if (activity.afterAccountVerify(result, ta, sa, apiHost, apDomain)) {
                        dialog_host.dismissSafe()
                        dialog_create.dismissSafe()
                    }
                }
            })
        }.show()
    }

    // アカウント設定
    fun setting(activity: ActMain) {
        AccountPicker.pick(
            activity,
            bAllowPseudo = true,
            bAuto = true,
            message = activity.getString(R.string.account_picker_open_setting)
        ) { ai ->

            activity.arAccountSetting.launch(
                ActAccountSetting.createIntent(activity, ai)
            )
        }
    }


    // アカウントを選んでタイムラインカラムを追加
    fun timeline(
        activity: ActMain,
        pos: Int,
        type: ColumnType,
        args: Array<out Any> = emptyArray()
    ) {

        AccountPicker.pick(
            activity,
            bAllowPseudo = type.bAllowPseudo,
            bAllowMisskey = type.bAllowMisskey,
            bAllowMastodon = type.bAllowMastodon,
            bAuto = true,
            message = activity.getString(
                R.string.account_picker_add_timeline_of,
                type.name1(activity)
            )
        ) { ai ->
            when (type) {

                ColumnType.PROFILE -> {
                    val id = ai.loginAccount?.id
                    if (id != null) activity.addColumn(pos, ai, type, id)
                }

                ColumnType.PROFILE_DIRECTORY ->
                    activity.addColumn(pos, ai, type, ai.apiHost)

                else -> activity.addColumn(pos, ai, type, *args)
            }
        }
    }

    // 投稿画面を開く。初期テキストを指定する
    fun openPost(
        activity: ActMain,
        initial_text: String? = activity.quickTootText
    ) {
        activity.post_helper.closeAcctPopup()

        val db_id = activity.currentPostTarget?.db_id ?: -1L
        if (db_id != -1L) {
            activity.launchActPost(
                ActPost.createIntent(
                    activity,
                    db_id,
                    initial_text = initial_text
                )
            )
        } else {
            AccountPicker.pick(
                activity,
                bAllowPseudo = false,
                bAuto = true,
                message = activity.getString(R.string.account_picker_toot)
            ) { ai ->
                activity.launchActPost(
                    ActPost.createIntent(
                        activity,
                        ai.db_id,
                        initial_text = initial_text
                    )
                )
            }
        }
    }

    fun endorse(
        activity: ActMain,
        access_info: SavedAccount,
        who: TootAccount,
        bSet: Boolean
    ) {
        if (access_info.isMisskey) {
            activity.showToast(false, "This feature is not provided on Misskey account.")
            return
        }


        TootTaskRunner(activity).run(access_info, object : TootTask {

            var relation: UserRelation? = null

            override suspend fun background(client: TootApiClient): TootApiResult? {
                val result = client.request(
                    "/api/v1/accounts/${who.id}/" + when (bSet) {
                        true -> "pin"
                        false -> "unpin"
                    },
                    "".toFormRequestBody().toPost()
                )
                val jsonObject = result?.jsonObject
                if (jsonObject != null) {
                    val tr = parseItem(
                        ::TootRelationShip,
                        TootParser(client.context, access_info),
                        jsonObject
                    )
                    if (tr != null) {
                        this.relation = saveUserRelation(access_info, tr)
                    }
                }
                return result
            }

            override suspend fun handleResult(result: TootApiResult?) {
                result ?: return

                if (result.error != null) {
                    activity.showToast(true, result.error)
                } else {
                    activity.showToast(
                        false, when (bSet) {
                            true -> R.string.endorse_succeeded
                            else -> R.string.remove_endorse_succeeded
                        }
                    )
                }
            }
        })
    }

    private val mailRegex =
        """\A[a-z0-9_+&*-]+(?:\.[a-z0-9_+&*-]+)*@(?:[a-z0-9-]+\.)+[a-z]{2,12}\z""".toRegex(
            RegexOption.IGNORE_CASE
        )

    fun resendConfirmMail(activity: AppCompatActivity, accessInfo: SavedAccount) {
        DlgConfirmMail(
            activity,
            accessInfo
        ) { email ->
            TootTaskRunner(activity).run(accessInfo, object : TootTask {
                override suspend fun background(client: TootApiClient): TootApiResult? {
                    email?.let {
                        if (!mailRegex.matches(it))
                            return TootApiResult("email address is not valid.")
                    }

                    return client.request(
                        "/api/v1/emails/confirmations",
                        ArrayList<String>().apply {
                            if (email != null) add("email=${email.encodePercent()}")
                        }.joinToString("&").toFormRequestBody().toPost()
                    )
                }

                override suspend fun handleResult(result: TootApiResult?) {
                    result ?: return // cancelled.
                    when (val error = result.error) {
                        null ->
                            activity.showToast(true, R.string.resend_confirm_mail_requested)
                        else ->
                            activity.showToast(true, error)
                    }
                }
            })
        }.show()
    }

    fun getReactionableAccounts(
        activity: ActMain,
        allowMisskey: Boolean = true,
        block: (ArrayList<SavedAccount>) -> Unit
    ) {
        TootTaskRunner(activity).run(object : TootTask {
            var list: List<SavedAccount>? = null
            override suspend fun background(client: TootApiClient): TootApiResult? {
                list = SavedAccount.loadAccountList(activity).filter { a ->
                    when {
                        client.isApiCancelled -> false
                        a.isPseudo -> false
                        a.isMisskey -> allowMisskey
                        else -> {
                            val (ti, ri) = TootInstance.getEx(client, account = a)
                            if (ti == null) {
                                ri?.error?.let { log.w(it) }
                                false
                            } else
                                ti.fedibird_capabilities?.contains("emoji_reaction") == true
                        }
                    }
                }
                return if (client.isApiCancelled) null else TootApiResult()
            }

            override suspend fun handleResult(result: TootApiResult?) {
                result ?: return
                if (list != null) block(ArrayList(list))
            }
        })
    }

    // アカウントを選んでタイムラインカラムを追加
    fun timelineWithFilter(
        activity: ActMain,
        pos: Int,
        type: ColumnType,
        args: Array<out Any> = emptyArray(),
        filter: suspend (SavedAccount) -> Boolean
    ) {
        activity.launch {
            val accountList = withContext(Dispatchers.IO) {
                SavedAccount.loadAccountList(activity)
                    .filter {
                        if (it.isPseudo && !type.bAllowPseudo) false
                        else if (it.isMisskey && !type.bAllowMisskey) false
                        else if (it.isMastodon && !type.bAllowMastodon) false
                        else filter(it)
                    }
            }.toMutableList()
            AccountPicker.pick(
                activity,
                accountListArg = accountList,
                bAuto = true,
                message = activity.getString(
                    R.string.account_picker_add_timeline_of,
                    type.name1(activity)
                )
            ) { ai ->
                when (type) {

                    ColumnType.PROFILE -> {
                        val id = ai.loginAccount?.id
                        if (id != null) activity.addColumn(pos, ai, type, id)
                    }

                    ColumnType.PROFILE_DIRECTORY ->
                        activity.addColumn(pos, ai, type, ai.apiHost)

                    else -> activity.addColumn(pos, ai, type, *args)
                }
            }
        }
    }

}
