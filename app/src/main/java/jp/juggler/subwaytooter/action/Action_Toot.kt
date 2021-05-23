package jp.juggler.subwaytooter.action

import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.AccountPicker
import jp.juggler.subwaytooter.dialog.DlgConfirm
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount

import jp.juggler.subwaytooter.util.matchHost
import jp.juggler.util.*
import okhttp3.Request
import java.util.*
import kotlin.math.max

object Action_Toot {

    // アカウントを選んでお気に入り
    fun favouriteFromAnotherAccount(
        activity: ActMain,
        timeline_account: SavedAccount,
        status: TootStatus?
    ) {
        if (status == null) return

        AccountPicker.pick(
            activity,
            bAllowPseudo = false,
            bAuto = false,
            message = activity.getString(R.string.account_picker_favourite),
            accountListArg = makeAccountListNonPseudo(activity, timeline_account.apDomain)
        ) { action_account ->
            favourite(
                activity,
                action_account,
                status,
                calcCrossAccountMode(timeline_account, action_account),
                callback = activity.favourite_complete_callback
            )
        }
    }

    // お気に入りの非同期処理
    fun favourite(
        activity: ActMain,
        access_info: SavedAccount,
        arg_status: TootStatus,
        crossAccountMode: CrossAccountMode,
        callback: () -> Unit,
        bSet: Boolean = true,
        bConfirmed: Boolean = false
    ) {
        if (activity.app_state.isBusyFav(access_info, arg_status)) {
            activity.showToast(false, R.string.wait_previous_operation)
            return
        }

        // 必要なら確認を出す
        if (!bConfirmed && access_info.isMastodon) {
            DlgConfirm.open(
                activity,
                activity.getString(
                    when (bSet) {
                        true -> R.string.confirm_favourite_from
                        else -> R.string.confirm_unfavourite_from
                    },
                    AcctColor.getNickname(access_info)
                ),
                object : DlgConfirm.Callback {

                    override fun onOK() {
                        favourite(
                            activity,
                            access_info,
                            arg_status,
                            crossAccountMode,
                            callback,
                            bSet = bSet,
                            bConfirmed = true
                        )
                    }

                    override var isConfirmEnabled: Boolean
                        get() = when (bSet) {
                            true -> access_info.confirm_favourite
                            else -> access_info.confirm_unfavourite

                        }
                        set(value) {
                            when (bSet) {
                                true -> access_info.confirm_favourite = value
                                else -> access_info.confirm_unfavourite = value
                            }
                            access_info.saveSetting()
                            activity.reloadAccountSetting(access_info)
                        }

                })
            return
        }

        //
        activity.app_state.setBusyFav(access_info, arg_status)

        //
        TootTaskRunner(activity, TootTaskRunner.PROGRESS_NONE).run(access_info, object : TootTask {

            var new_status: TootStatus? = null
            override suspend fun background(client: TootApiClient): TootApiResult? {

                val target_status = if (crossAccountMode.isRemote) {

                    val (result, status) = client.syncStatus(access_info, arg_status)
                    status ?: return result
                    if (status.favourited) {
                        return TootApiResult(activity.getString(R.string.already_favourited))
                    }
                    status
                } else {
                    arg_status
                }


                return if (access_info.isMisskey) {
                    client.request(
                        if (bSet) {
                            "/api/notes/favorites/create"
                        } else {
                            "/api/notes/favorites/delete"
                        },
                        access_info.putMisskeyApiToken().apply {
                            put("noteId", target_status.id.toString())
                        }
                            .toPostRequestBuilder()
                    )?.also { result ->
                        // 正常レスポンスは 204 no content
                        // 既にお気に入り済みならエラー文字列に'already favorited' が返る
                        if (result.response?.code == 204
                            || result.error?.contains("already favorited") == true
                            || result.error?.contains("already not favorited") == true
                        ) {
                            // 成功した
                            new_status = target_status.apply {
                                favourited = bSet
                            }
                        }
                    }
                } else {
                    client.request(
                        "/api/v1/statuses/${target_status.id}/${if (bSet) "favourite" else "unfavourite"}",
                        "".toFormRequestBody().toPost()
                    )?.also { result ->
                        new_status = TootParser(activity, access_info).status(result.jsonObject)
                    }
                }
            }

            override suspend fun handleResult(result: TootApiResult?) {

                activity.app_state.resetBusyFav(access_info, arg_status)

                val new_status = this.new_status
                when {
                    result == null -> {
                    } // cancelled.
                    new_status != null -> {

                        val old_count = arg_status.favourites_count
                        val new_count = new_status.favourites_count
                        if (old_count != null && new_count != null) {
                            if (access_info.isMisskey) {
                                new_status.favourited = bSet
                            }
                            if (bSet && new_status.favourited && new_count <= old_count) {
                                // 星をつけたのにカウントが上がらないのは違和感あるので、表示をいじる
                                new_status.favourites_count = old_count + 1L
                            } else if (!bSet && !new_status.favourited && new_count >= old_count) {
                                // 星を外したのにカウントが下がらないのは違和感あるので、表示をいじる
                                // 0未満にはならない
                                new_status.favourites_count =
                                    if (old_count < 1L) 0L else old_count - 1L
                            }
                        }

                        for (column in activity.app_state.columnList) {
                            column.findStatus(
                                access_info.apDomain,
                                new_status.id
                            ) { account, status ->

                                // 同タンス別アカウントでもカウントは変化する
                                status.favourites_count = new_status.favourites_count

                                // 同アカウントならfav状態を変化させる
                                if (access_info == account) {
                                    status.favourited = new_status.favourited
                                }

                                true
                            }
                        }
                        callback()
                    }

                    else -> activity.showToast(true, result.error)
                }
                // 結果に関わらず、更新中状態から復帰させる
                activity.showColumnMatchAccount(access_info)

            }
        })

        // ファボ表示を更新中にする
        activity.showColumnMatchAccount(access_info)
    }

    // アカウントを選んでお気に入り
    fun bookmarkFromAnotherAccount(
        activity: ActMain,
        timeline_account: SavedAccount,
        status: TootStatus?
    ) {
        status ?: return

        AccountPicker.pick(
            activity,
            bAllowPseudo = false,
            bAuto = false,
            message = activity.getString(R.string.account_picker_bookmark),
            accountListArg = makeAccountListNonPseudo(activity, timeline_account.apDomain)
        ) { action_account ->
            bookmark(
                activity,
                action_account,
                status,
                calcCrossAccountMode(timeline_account, action_account),
                callback = activity.bookmark_complete_callback
            )
        }
    }

    // お気に入りの非同期処理
    fun bookmark(
        activity: ActMain,
        access_info: SavedAccount,
        arg_status: TootStatus,
        crossAccountMode: CrossAccountMode,
        callback: () -> Unit,
        bSet: Boolean = true,
        bConfirmed: Boolean = false
    ) {
        if (activity.app_state.isBusyFav(access_info, arg_status)) {
            activity.showToast(false, R.string.wait_previous_operation)
            return
        }
        if (access_info.isMisskey) {
            activity.showToast(false, R.string.misskey_account_not_supported)
            return
        }

        // 必要なら確認を出す
        // ブックマークは解除する時だけ確認する
        if (!bConfirmed && !bSet) {
            DlgConfirm.openSimple(
                activity,
                activity.getString(
                    R.string.confirm_unbookmark_from,
                    AcctColor.getNickname(access_info)
                )
            ) {
                bookmark(
                    activity,
                    access_info,
                    arg_status,
                    crossAccountMode,
                    callback,
                    bSet = bSet,
                    bConfirmed = true
                )
            }
            return
        }

        //
        activity.app_state.setBusyBookmark(access_info, arg_status)

        //
        TootTaskRunner(activity, TootTaskRunner.PROGRESS_NONE).run(access_info, object : TootTask {

            var new_status: TootStatus? = null
            override suspend fun background(client: TootApiClient): TootApiResult? {

                val target_status = if (crossAccountMode.isRemote) {
                    val (result, status) = client.syncStatus(access_info, arg_status)
                    status ?: return result
                    if (status.bookmarked) {
                        return TootApiResult(activity.getString(R.string.already_bookmarked))
                    }
                    status
                } else {
                    arg_status
                }

                return client.request(
                    "/api/v1/statuses/${target_status.id}/${if (bSet) "bookmark" else "unbookmark"}",
                    "".toFormRequestBody().toPost()
                )?.also { result ->
                    new_status = TootParser(activity, access_info).status(result.jsonObject)
                }
            }

            override suspend fun handleResult(result: TootApiResult?) {

                activity.app_state.resetBusyBookmark(access_info, arg_status)

                val new_status = this.new_status
                when {
                    result == null -> {
                    } // cancelled.

                    new_status != null -> {
                        for (column in activity.app_state.columnList) {
                            column.findStatus(
                                access_info.apDomain,
                                new_status.id
                            ) { account, status ->

                                // 同アカウントならブックマーク状態を伝播する
                                if (access_info == account) {
                                    status.bookmarked = new_status.bookmarked
                                }

                                true
                            }
                        }
                        callback()
                    }

                    else -> activity.showToast(true, result.error)
                }

                // 結果に関わらず、更新中状態から復帰させる
                activity.showColumnMatchAccount(access_info)
            }
        })

        // ファボ表示を更新中にする
        activity.showColumnMatchAccount(access_info)
    }

    fun boostFromAnotherAccount(
        activity: ActMain,
        timeline_account: SavedAccount,
        status: TootStatus?
    ) {
        status ?: return

        val status_owner = timeline_account.getFullAcct(status.account)

        val isPrivateToot = timeline_account.isMastodon &&
            status.visibility == TootVisibility.PrivateFollowers

        if (isPrivateToot) {
            val list = ArrayList<SavedAccount>()
            for (a in SavedAccount.loadAccountList(activity)) {
                if (a.acct == status_owner) list.add(a)
            }
            if (list.isEmpty()) {
                activity.showToast(false, R.string.boost_private_toot_not_allowed)
                return
            }
            AccountPicker.pick(
                activity,
                bAllowPseudo = false,
                bAuto = false,
                message = activity.getString(R.string.account_picker_boost),
                accountListArg = list
            ) { action_account ->
                boost(
                    activity,
                    action_account,
                    status,
                    status_owner,
                    calcCrossAccountMode(timeline_account, action_account),
                    callback = activity.boost_complete_callback
                )
            }
        } else {
            AccountPicker.pick(
                activity,
                bAllowPseudo = false,
                bAuto = false,
                message = activity.getString(R.string.account_picker_boost),
                accountListArg = makeAccountListNonPseudo(activity, timeline_account.apDomain)
            ) { action_account ->
                boost(
                    activity,
                    action_account,
                    status,
                    status_owner,
                    calcCrossAccountMode(timeline_account, action_account),
                    callback = activity.boost_complete_callback
                )
            }
        }
    }

    fun boost(
        activity: ActMain,
        access_info: SavedAccount,
        arg_status: TootStatus,
        status_owner: Acct,
        crossAccountMode: CrossAccountMode,
        bSet: Boolean = true,
        bConfirmed: Boolean = false,
        visibility: TootVisibility? = null,
        callback: () -> Unit
    ) {

        // アカウントからステータスにブースト操作を行っているなら、何もしない
        if (activity.app_state.isBusyBoost(access_info, arg_status)) {
            activity.showToast(false, R.string.wait_previous_operation)
            return
        }

        // Mastodonは非公開トゥートをブーストできるのは本人だけ
        val isPrivateToot = access_info.isMastodon &&
            arg_status.visibility == TootVisibility.PrivateFollowers

        if (isPrivateToot && access_info.acct != status_owner) {
            activity.showToast(false, R.string.boost_private_toot_not_allowed)
            return
        }
        // DMとかのブーストはAPI側がエラーを出すだろう？

        // 必要なら確認を出す
        if (!bConfirmed) {
            DlgConfirm.open(
                activity,
                activity.getString(
                    when {
                        !bSet -> R.string.confirm_unboost_from
                        isPrivateToot -> R.string.confirm_boost_private_from
                        visibility == TootVisibility.PrivateFollowers -> R.string.confirm_private_boost_from
                        else -> R.string.confirm_boost_from
                    },
                    AcctColor.getNickname(access_info)
                ),
                object : DlgConfirm.Callback {
                    override fun onOK() {
                        boost(
                            activity,
                            access_info,
                            arg_status,
                            status_owner,
                            crossAccountMode,
                            bSet = bSet,
                            bConfirmed = true,
                            visibility = visibility,
                            callback = callback,
                        )
                    }

                    override var isConfirmEnabled: Boolean
                        get() = when (bSet) {
                            true -> access_info.confirm_boost
                            else -> access_info.confirm_unboost
                        }
                        set(value) {
                            when (bSet) {
                                true -> access_info.confirm_boost = value
                                else -> access_info.confirm_unboost = value
                            }
                            access_info.saveSetting()
                            activity.reloadAccountSetting(access_info)
                        }
                })
            return
        }

        activity.app_state.setBusyBoost(access_info, arg_status)

        TootTaskRunner(activity, TootTaskRunner.PROGRESS_NONE).run(access_info, object : TootTask {

            var new_status: TootStatus? = null
            var unrenoteId: EntityId? = null
            override suspend fun background(client: TootApiClient): TootApiResult? {

                val parser = TootParser(activity, access_info)

                val target_status = if (crossAccountMode.isRemote) {
                    val (result, status) = client.syncStatus(access_info, arg_status)
                    if (status == null) return result
                    if (status.reblogged) {
                        return TootApiResult(activity.getString(R.string.already_boosted))
                    }
                    status
                } else {
                    // 既に自タンスのステータスがある
                    arg_status
                }

                if (access_info.isMisskey) {
                    return if (!bSet) {
                        val myRenoteId = target_status.myRenoteId
                            ?: return TootApiResult("missing renote id.")

                        client.request(
                            "/api/notes/delete",
                            access_info.putMisskeyApiToken().apply {
                                put("noteId", myRenoteId.toString())
                                put("renoteId", target_status.id.toString())
                            }
                                .toPostRequestBuilder()
                        )
                            ?.also {
                                if (it.response?.code == 204)
                                    unrenoteId = myRenoteId
                            }
                    } else {
                        client.request(
                            "/api/notes/create",
                            access_info.putMisskeyApiToken().apply {
                                put("renoteId", target_status.id.toString())
                            }
                                .toPostRequestBuilder()
                        )
                            ?.also { result ->
                                val jsonObject = result.jsonObject
                                if (jsonObject != null) {
                                    val outerStatus = parser.status(
                                        jsonObject.jsonObject("createdNote")
                                            ?: jsonObject
                                    )
                                    val innerStatus = outerStatus?.reblog ?: outerStatus
                                    if (outerStatus != null && innerStatus != null && outerStatus != innerStatus) {
                                        innerStatus.myRenoteId = outerStatus.id
                                        innerStatus.reblogged = true
                                    }
                                    // renoteそのものではなくrenoteされた元noteが欲しい
                                    this.new_status = innerStatus
                                }
                            }
                    }

                } else {
                    val b = JsonObject().apply {
                        if (visibility != null) put("visibility", visibility.strMastodon)
                    }.toPostRequestBuilder()

                    val result = client.request(
                        "/api/v1/statuses/${target_status.id}/${if (bSet) "reblog" else "unreblog"}",
                        b
                    )
                    // reblogはreblogを表すStatusを返す
                    // unreblogはreblogしたStatusを返す
                    val s = parser.status(result?.jsonObject)
                    this.new_status = s?.reblog ?: s

                    return result
                }
            }

            override suspend fun handleResult(result: TootApiResult?) {
                activity.app_state.resetBusyBoost(access_info, arg_status)

                val unrenoteId = this.unrenoteId
                val new_status = this.new_status

                when {

                    // cancelled.
                    result == null -> {
                    }

                    // Misskeyでunrenoteに成功した
                    unrenoteId != null -> {

                        // 星を外したのにカウントが下がらないのは違和感あるので、表示をいじる
                        // 0未満にはならない
                        val count = max(0, (arg_status.reblogs_count ?: 1) - 1)

                        for (column in activity.app_state.columnList) {
                            column.findStatus(
                                access_info.apDomain,
                                arg_status.id
                            ) { account, status ->

                                // 同タンス別アカウントでもカウントは変化する
                                status.reblogs_count = count

                                // 同アカウントならreblogged状態を変化させる
                                if (access_info == account && status.myRenoteId == unrenoteId) {
                                    status.myRenoteId = null
                                    status.reblogged = false
                                }
                                true
                            }
                        }
                        callback()
                    }

                    new_status != null -> {
                        // カウント数は遅延があるみたいなので、恣意的に表示を変更する
                        // ブーストカウント数を加工する
                        val old_count = arg_status.reblogs_count
                        val new_count = new_status.reblogs_count
                        if (old_count != null && new_count != null) {
                            if (bSet && new_status.reblogged && new_count <= old_count) {
                                // 星をつけたのにカウントが上がらないのは違和感あるので、表示をいじる
                                new_status.reblogs_count = old_count + 1
                            } else if (!bSet && !new_status.reblogged && new_count >= old_count) {
                                // 星を外したのにカウントが下がらないのは違和感あるので、表示をいじる
                                // 0未満にはならない
                                new_status.reblogs_count = if (old_count < 1) 0 else old_count - 1
                            }

                        }

                        for (column in activity.app_state.columnList) {
                            column.findStatus(
                                access_info.apDomain,
                                new_status.id
                            ) { account, status ->

                                // 同タンス別アカウントでもカウントは変化する
                                status.reblogs_count = new_status.reblogs_count

                                if (access_info == account) {

                                    // 同アカウントならreblog状態を変化させる
                                    when {
                                        access_info.isMastodon ->
                                            status.reblogged = new_status.reblogged

                                        bSet && status.myRenoteId == null -> {
                                            status.myRenoteId = new_status.myRenoteId
                                            status.reblogged = true
                                        }
                                    }
                                    // Misskey のunrenote時はここを通らない
                                }
                                true
                            }
                        }
                        callback()
                    }

                    else -> activity.showToast(true, result.error)
                }

                // 結果に関わらず、更新中状態から復帰させる
                activity.showColumnMatchAccount(access_info)

            }
        })

        // ブースト表示を更新中にする
        activity.showColumnMatchAccount(access_info)
        // misskeyは非公開トゥートをブーストできないっぽい
    }

    fun delete(activity: ActMain, access_info: SavedAccount, status_id: EntityId) {

        TootTaskRunner(activity).run(access_info, object : TootTask {
            override suspend fun background(client: TootApiClient): TootApiResult? {
                return if (access_info.isMisskey) {
                    client.request(
                        "/api/notes/delete",
                        access_info.putMisskeyApiToken().apply {
                            put("noteId", status_id)
                        }
                            .toPostRequestBuilder()
                    )
                    // 204 no content
                } else {
                    client.request(
                        "/api/v1/statuses/$status_id",
                        Request.Builder().delete()
                    )
                }
            }

            override suspend fun handleResult(result: TootApiResult?) {
                if (result == null) return  // cancelled.

                if (result.jsonObject != null) {
                    activity.showToast(false, R.string.delete_succeeded)
                    for (column in activity.app_state.columnList) {
                        column.onStatusRemoved(access_info.apDomain, status_id)
                    }
                } else {
                    activity.showToast(false, result.error)
                }

            }
        })
    }

    ////////////////////////////////////////
    // profile pin

    fun pin(
        activity: ActMain, access_info: SavedAccount, status: TootStatus, bSet: Boolean
    ) {

        TootTaskRunner(activity)
            .progressPrefix(activity.getString(R.string.profile_pin_progress))

            .run(access_info, object : TootTask {

                var new_status: TootStatus? = null
                override suspend fun background(client: TootApiClient): TootApiResult? {

                    val result = client.request(
                        "/api/v1/statuses/${status.id}/${if (bSet) "pin" else "unpin"}",
                        "".toFormRequestBody().toPost()
                    )

                    new_status = TootParser(activity, access_info).status(result?.jsonObject)

                    return result
                }

                override suspend fun handleResult(result: TootApiResult?) {

                    val new_status = this.new_status

                    when {
                        result == null -> {
                            // cancelled.
                        }

                        new_status != null -> {
                            for (column in activity.app_state.columnList) {
                                if (access_info == column.access_info) {
                                    column.findStatus(
                                        access_info.apDomain,
                                        new_status.id
                                    ) { _, status ->
                                        status.pinned = bSet
                                        true
                                    }
                                }
                            }
                        }

                        else -> activity.showToast(true, result.error)
                    }

                    // 結果に関わらず、更新中状態から復帰させる
                    activity.showColumnMatchAccount(access_info)

                }
            })

    }

    // 投稿画面を開く。初期テキストを指定する
    fun redraft(
        activity: ActMain,
        accessInfo: SavedAccount,
        status: TootStatus
    ) {
        activity.post_helper.closeAcctPopup()

        if (accessInfo.isMisskey) {
            activity.launchActPost(
                ActPost.createIntent(
                    activity,
                    accessInfo.db_id,
                    redraft_status = status,
                    reply_status = status.reply
                )
            )
            return
        }

        if (status.in_reply_to_id == null) {
            activity.launchActPost(
                ActPost.createIntent(
                    activity,
                    accessInfo.db_id,
                    redraft_status = status
                )
            )
            return
        }

        TootTaskRunner(activity).run(accessInfo, object : TootTask {

            var reply_status: TootStatus? = null
            override suspend fun background(client: TootApiClient): TootApiResult? {
                val result = client.request("/api/v1/statuses/${status.in_reply_to_id}")
                reply_status = TootParser(activity, accessInfo).status(result?.jsonObject)
                return result
            }

            override suspend fun handleResult(result: TootApiResult?) {
                if (result == null) return  // cancelled.

                val reply_status = this.reply_status
                if (reply_status != null) {
                    activity.launchActPost(
                        ActPost.createIntent(
                            activity,
                            accessInfo.db_id,
                            redraft_status = status,
                            reply_status = reply_status
                        )
                    )
                    return
                }
                val error = result.error ?: "(no information)"
                activity.showToast(true, activity.getString(R.string.cant_sync_toot) + " : $error")
            }
        })
    }

    fun deleteScheduledPost(
        activity: ActMain,
        access_info: SavedAccount,
        item: TootScheduled,
        bConfirmed: Boolean = false,
        callback: () -> Unit
    ) {
        if (!bConfirmed) {
            DlgConfirm.openSimple(
                activity,
                activity.getString(R.string.scheduled_status_delete_confirm)
            ) {
                deleteScheduledPost(
                    activity,
                    access_info,
                    item,
                    bConfirmed = true,
                    callback = callback
                )
            }
            return
        }

        TootTaskRunner(activity).run(access_info, object : TootTask {
            override suspend fun background(client: TootApiClient): TootApiResult? {

                return client.request(
                    "/api/v1/scheduled_statuses/${item.id}",
                    Request.Builder().delete()
                )
            }

            override suspend fun handleResult(result: TootApiResult?) {

                result ?: return

                val error = result.error
                if (error != null) {
                    activity.showToast(false, error)
                    return
                }

                callback()
            }
        })
    }

    fun editScheduledPost(
        activity: ActMain,
        access_info: SavedAccount,
        item: TootScheduled
    ) {
        TootTaskRunner(activity).run(access_info, object : TootTask {

            var reply_status: TootStatus? = null
            override suspend fun background(client: TootApiClient): TootApiResult? {
                val reply_status_id = item.in_reply_to_id
                    ?: return TootApiResult()

                return client.request("/api/v1/statuses/$reply_status_id")?.also { result ->
                    reply_status = TootParser(activity, access_info).status(result.jsonObject)
                }
            }

            override suspend fun handleResult(result: TootApiResult?) {
                result ?: return

                val error = result.error
                if (error != null) {
                    activity.showToast(false, error)
                    return
                }

                activity.launchActPost(
                    ActPost.createIntent(
                        activity,
                        access_info.db_id,
                        scheduledStatus = item,
                        reply_status = reply_status
                    )
                )

            }
        })
    }
}
