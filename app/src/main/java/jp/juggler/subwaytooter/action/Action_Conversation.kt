package jp.juggler.subwaytooter.action

import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.ColumnType
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.entity.TootConversationSummary
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.findStatus
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.matchHost
import jp.juggler.subwaytooter.util.openCustomTab
import jp.juggler.util.*
import java.util.ArrayList

object Action_Conversation {

    private val log= LogCategory("Action_Conversation")

    private val reDetailedStatusTime =
        """<a\b[^>]*?\bdetailed-status__datetime\b[^>]*href="https://[^/]+/@[^/]+/([^\s?#/"]+)"""
            .asciiPattern()

    /////////////////////////////////////////////////////////////////////////////////////
    // open conversation

    internal fun clearConversationUnread(
        activity: ActMain,
        access_info: SavedAccount,
        conversationSummary: TootConversationSummary?
    ) {
        conversationSummary ?: return
        TootTaskRunner(activity, progress_style = TootTaskRunner.PROGRESS_NONE)
            .run(access_info, object : TootTask {
                override suspend fun background(client: TootApiClient): TootApiResult? {
                    return client.request(
                        "/api/v1/conversations/${conversationSummary.id}/read",
                        "".toFormRequestBody().toPost()
                    )
                }

                override suspend fun handleResult(result: TootApiResult?) {
                    // 何もしない
                }
            })

    }

    // ローカルかリモートか判断する
    fun conversation(
        activity: ActMain,
        pos: Int,
        access_info: SavedAccount,
        status: TootStatus
    ) {
        if (access_info.isNA || !access_info.matchHost(status.readerApDomain)) {
            conversationOtherInstance(activity, pos, status)
        } else {

            conversationLocal(activity, pos, access_info, status.id)
        }
    }

    // ローカルから見える会話の流れを表示する
    fun conversationLocal(
        activity: ActMain,
        pos: Int,
        access_info: SavedAccount,
        status_id: EntityId
    ) {
        activity.addColumn(pos, access_info, ColumnType.CONVERSATION, status_id)
    }

    // リモートかもしれない会話の流れを表示する
    fun conversationOtherInstance(
        activity: ActMain, pos: Int, status: TootStatus?
    ) {
        if (status == null) return
        val url = status.url

        if (url == null || url.isEmpty()) {
            // URLが不明なトゥートというのはreblogの外側のアレ
            return
        }

        when {

            // 検索サービスではステータスTLをどのタンスから読んだのか分からない
            status.readerApDomain == null ->
                conversationOtherInstance(
                    activity, pos, url, TootStatus.validStatusId(status.id)
                        ?: TootStatus.findStatusIdFromUri(
                            status.uri,
                            status.url
                        )
                )

            // TLアカウントのホストとトゥートのアカウントのホストが同じ
            status.originalApDomain == status.readerApDomain ->
                conversationOtherInstance(
                    activity, pos, url, TootStatus.validStatusId(status.id)
                        ?: TootStatus.findStatusIdFromUri(
                            status.uri,
                            status.url
                        )
                )

            else -> {
                // トゥートを取得したタンスと投稿元タンスが異なる場合
                // status.id はトゥートを取得したタンスでのIDである
                // 投稿元タンスでのIDはuriやURLから調べる
                // pleromaではIDがuuidなので失敗する(その時はURLを検索してIDを見つける)
                conversationOtherInstance(
                    activity, pos, url, TootStatus.findStatusIdFromUri(
                        status.uri,
                        status.url
                    ), status.readerApDomain, TootStatus.validStatusId(status.id)
                )
            }
        }
    }

    // アプリ外部からURLを渡された場合に呼ばれる
    fun conversationOtherInstance(
        activity: ActMain,
        pos: Int,
        url: String,
        status_id_original: EntityId? = null,
        host_access: Host? = null,
        status_id_access: EntityId? = null
    ) {

        val dialog = ActionsDialog()

        val host_original = Host.parse(url.toUri().authority ?: "")

        // 選択肢：ブラウザで表示する
        dialog.addAction(activity.getString(R.string.open_web_on_host, host_original.pretty))
        { activity.openCustomTab(url) }

        // トゥートの投稿元タンスにあるアカウント
        val local_account_list = ArrayList<SavedAccount>()

        // TLを読んだタンスにあるアカウント
        val access_account_list = ArrayList<SavedAccount>()

        // その他のタンスにあるアカウント
        val other_account_list = ArrayList<SavedAccount>()

        for (a in SavedAccount.loadAccountList(activity)) {

            // 疑似アカウントは後でまとめて処理する
            if (a.isPseudo) continue

            if (status_id_original != null && a.matchHost(host_original)) {
                // アクセス情報＋ステータスID でアクセスできるなら
                // 同タンスのアカウントならステータスIDの変換なしに表示できる
                local_account_list.add(a)
            } else if (status_id_access != null && a.matchHost(host_access)) {
                // 既に変換済みのステータスIDがあるなら、そのアカウントでもステータスIDの変換は必要ない
                access_account_list.add(a)
            } else {
                // 別タンスでも実アカウントなら検索APIでステータスIDを変換できる
                other_account_list.add(a)
            }
        }

        // 同タンスのアカウントがないなら、疑似アカウントで開く選択肢
        if (local_account_list.isEmpty()) {
            if (status_id_original != null) {
                dialog.addAction(
                    activity.getString(R.string.open_in_pseudo_account, "?@${host_original.pretty}")
                ) {
                    addPseudoAccount(activity, host_original) { sa ->
                        conversationLocal(activity, pos, sa, status_id_original)
                    }
                }
            } else {
                dialog.addAction(
                    activity.getString(R.string.open_in_pseudo_account, "?@${host_original.pretty}")
                ) {
                    addPseudoAccount(activity, host_original) { sa ->
                        conversationRemote(activity, pos, sa, url)
                    }
                }
            }
        }

        // ローカルアカウント
        if (status_id_original != null) {
            SavedAccount.sort(local_account_list)
            for (a in local_account_list) {
                dialog.addAction(
                    AcctColor.getStringWithNickname(
                        activity,
                        R.string.open_in_account,
                        a.acct
                    )
                ) { conversationLocal(activity, pos, a, status_id_original) }
            }
        }

        // アクセスしたアカウント
        if (status_id_access != null) {
            SavedAccount.sort(access_account_list)
            for (a in access_account_list) {
                dialog.addAction(
                    AcctColor.getStringWithNickname(
                        activity,
                        R.string.open_in_account,
                        a.acct
                    )
                ) { conversationLocal(activity, pos, a, status_id_access) }
            }
        }

        // その他の実アカウント
        SavedAccount.sort(other_account_list)
        for (a in other_account_list) {
            dialog.addAction(
                AcctColor.getStringWithNickname(
                    activity,
                    R.string.open_in_account,
                    a.acct
                )
            ) { conversationRemote(activity, pos, a, url) }
        }

        dialog.show(activity, activity.getString(R.string.open_status_from))
    }

    private fun conversationRemote(
        activity: ActMain, pos: Int, access_info: SavedAccount, remote_status_url: String
    ) {
        TootTaskRunner(activity)
            .progressPrefix(activity.getString(R.string.progress_synchronize_toot))
            .run(access_info, object : TootTask {

                var local_status_id: EntityId? = null
                override suspend fun background(client: TootApiClient): TootApiResult? =
                    if (access_info.isPseudo) {
                        // 疑似アカウントではURLからIDを取得するのにHTMLと正規表現を使う
                        val result = client.getHttp(remote_status_url)
                        val string = result?.string
                        if (string != null) {
                            try {
                                val m = reDetailedStatusTime.matcher(string)
                                if (m.find()) {
                                    local_status_id = EntityId(m.groupEx(1)!!)
                                }
                            } catch (ex: Throwable) {
                                log.e(ex, "openStatusRemote: can't parse status id from HTML data.")
                            }

                            if (result.error == null && local_status_id == null) {
                                result.setError(activity.getString(R.string.status_id_conversion_failed))
                            }
                        }
                        result
                    } else {
                        val (result, status) = client.syncStatus(access_info, remote_status_url)
                        if (status != null) {
                            local_status_id = status.id
                            log.d("status id conversion %s => %s", remote_status_url, status.id)
                        }
                        result
                    }

                override suspend fun handleResult(result: TootApiResult?) {
                    if (result == null) return // cancelled.

                    val local_status_id = this.local_status_id
                    if (local_status_id != null) {
                        conversationLocal(activity, pos, access_info, local_status_id)
                    } else {
                        activity.showToast(true, result.error)
                    }
                }
            })
    }

    ////////////////////////////////////////

    fun muteConversation(
        activity: ActMain, access_info: SavedAccount, status: TootStatus
    ) {
        // toggle change
        val bMute = !status.muted

        TootTaskRunner(activity).run(access_info, object : TootTask {

            var local_status: TootStatus? = null

            override suspend fun background(client: TootApiClient): TootApiResult? {

                val result = client.request(
                    "/api/v1/statuses/${status.id}/${if (bMute) "mute" else "unmute"}",
                    "".toFormRequestBody().toPost()
                )

                local_status = TootParser(activity, access_info).status(result?.jsonObject)

                return result
            }

            override suspend fun handleResult(result: TootApiResult?) {
                result ?: return // cancelled.

                val ls = local_status
                if (ls != null) {
                    for (column in activity.app_state.columnList) {
                        if (access_info == column.access_info) {
                            column.findStatus(access_info.apDomain, ls.id) { _, status ->
                                status.muted = bMute
                                true
                            }
                        }
                    }
                    activity.showToast(
                        true,
                        if (bMute) R.string.mute_succeeded else R.string.unmute_succeeded
                    )
                } else {
                    activity.showToast(true, result.error)
                }
            }
        })
    }

}