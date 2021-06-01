package jp.juggler.subwaytooter.action

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.widget.*
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.ReportForm
import jp.juggler.subwaytooter.dialog.pickAccount
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.util.matchHost
import jp.juggler.subwaytooter.util.openCustomTab
import jp.juggler.util.*
import kotlinx.coroutines.*
import okhttp3.Request
import java.lang.StringBuilder


// ユーザをミュート/ミュート解除する
private fun ActMain.userMute(
    access_info: SavedAccount,
    whoArg: TootAccount,
    whoAccessInfo: SavedAccount,
    bMute: Boolean,
    bMuteNotification: Boolean,
    duration: Int?,
) {
    val whoAcct = whoAccessInfo.getFullAcct(whoArg)
    if (access_info.isMe(whoAcct)) {
        showToast(false, R.string.it_is_you)
        return
    }

    launchMain {
        var resultRelation: UserRelation? = null
        var resultWhoId: EntityId? = null
        runApiTask(access_info) { client ->
            val parser = TootParser(this, access_info)
            if (access_info.isPseudo) {
                if (!whoAcct.isValidFull) {
                    TootApiResult("can't mute pseudo acct ${whoAcct.pretty}")
                } else {
                    val relation = UserRelation.loadPseudo(whoAcct)
                    relation.muting = bMute
                    relation.savePseudo(whoAcct.ascii)
                    resultRelation = relation
                    resultWhoId = whoArg.id
                    TootApiResult()
                }
            } else {
                val whoId = if (access_info.matchHost(whoAccessInfo)) {
                    whoArg.id
                } else {
                    val (result, accountRef) = client.syncAccountByAcct(access_info, whoAcct)
                    accountRef?.get()?.id ?: return@runApiTask result
                }
                resultWhoId = whoId

                if (access_info.isMisskey) {
                    client.request(
                        when (bMute) {
                            true -> "/api/mute/create"
                            else -> "/api/mute/delete"
                        },
                        access_info.putMisskeyApiToken().apply {
                            put("userId", whoId.toString())
                        }.toPostRequestBuilder()
                    )?.apply {
                        if (jsonObject != null) {
                            // 204 no content

                            // update user relation
                            val ur = UserRelation.load(access_info.db_id, whoId)
                            ur.muting = bMute
                            access_info.saveUserRelationMisskey(
                                whoId,
                                parser
                            )
                            resultRelation = ur
                        }
                    }
                } else {
                    client.request(
                        "/api/v1/accounts/${whoId}/${if (bMute) "mute" else "unmute"}",
                        when {
                            !bMute -> "".toFormRequestBody()
                            else ->
                                jsonObject {
                                    put("notifications", bMuteNotification)
                                    if (duration != null) put("duration", duration)
                                }
                                    .toRequestBody()
                        }.toPost()
                    )?.apply {
                        val jsonObject = jsonObject
                        if (jsonObject != null) {
                            resultRelation = access_info.saveUserRelation(
                                parseItem(::TootRelationShip, parser, jsonObject)
                            )
                        }
                    }
                }
            }
        }?.let { result ->
            val relation = resultRelation
            val whoId = resultWhoId
            if (relation == null || whoId == null) {
                showToast(false, result.error)
            } else {
                // 未確認だが、自分をミュートしようとするとリクエストは成功するがレスポンス中のmutingはfalseになるはず
                if (bMute && !relation.muting) {
                    showToast(false, R.string.not_muted)
                    return@launchMain
                }

                for (column in app_state.columnList) {
                    if (column.access_info.isPseudo) {
                        if (relation.muting && column.type != ColumnType.PROFILE) {
                            // ミュートしたユーザの情報はTLから消える
                            column.removeAccountInTimelinePseudo(whoAcct)
                        }
                        // フォローアイコンの表示更新が走る
                        column.updateFollowIcons(access_info)
                    } else if (column.access_info == access_info) {
                        when {
                            !relation.muting -> {
                                if (column.type == ColumnType.MUTES) {
                                    // ミュート解除したら「ミュートしたユーザ」カラムから消える
                                    column.removeUser(access_info, ColumnType.MUTES, whoId)
                                } else {
                                    // 他のカラムではフォローアイコンの表示更新が走る
                                    column.updateFollowIcons(access_info)
                                }

                            }

                            column.type == ColumnType.PROFILE && column.profile_id == whoId -> {
                                // 該当ユーザのプロフページのトゥートはミュートしてても見れる
                                // しかしフォローアイコンの表示更新は必要
                                column.updateFollowIcons(access_info)
                            }

                            else -> {
                                // ミュートしたユーザの情報はTLから消える
                                column.removeAccountInTimeline(access_info, whoId)
                            }
                        }
                    }
                }

                showToast(
                    false,
                    if (relation.muting) R.string.mute_succeeded else R.string.unmute_succeeded
                )
            }
        }
    }
}

fun ActMain.userUnmute(
    access_info: SavedAccount,
    whoArg: TootAccount,
    whoAccessInfo: SavedAccount,
) = userMute(
    access_info,
    whoArg,
    whoAccessInfo,
    bMute = false,
    bMuteNotification = false,
    duration = null,
)

fun ActMain.userMuteConfirm(
    access_info: SavedAccount,
    who: TootAccount,
    whoAccessInfo: SavedAccount,
) {
    val activity = this@userMuteConfirm

    // Mastodon 3.3から時限ミュート設定ができる
    val choiceList = arrayOf(
        Pair(0, getString(R.string.duration_indefinite)),
        Pair(300, getString(R.string.duration_minutes_5)),
        Pair(1800, getString(R.string.duration_minutes_30)),
        Pair(3600, getString(R.string.duration_hours_1)),
        Pair(21600, getString(R.string.duration_hours_6)),
        Pair(86400, getString(R.string.duration_days_1)),
        Pair(259200, getString(R.string.duration_days_3)),
        Pair(604800, getString(R.string.duration_days_7)),
    )

    @SuppressLint("InflateParams")
    val view = layoutInflater.inflate(R.layout.dlg_confirm, null, false)

    val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
    tvMessage.text = getString(R.string.confirm_mute_user, who.username)
    tvMessage.text = getString(R.string.confirm_mute_user, who.username)

    // 「次回以降スキップ」のチェックボックスは「このユーザからの通知もミュート」に再利用する
    // このオプションはMisskeyや疑似アカウントにはない
    val cbMuteNotification = view.findViewById<CheckBox>(R.id.cbSkipNext)
    val hasMuteNotification = !access_info.isMisskey && !access_info.isPseudo
    cbMuteNotification.isChecked = hasMuteNotification
    cbMuteNotification.vg(hasMuteNotification)?.apply {
        setText(R.string.confirm_mute_notification_for_user)
    }

    launchMain {

        val spMuteDuration: Spinner = view.findViewById(R.id.spMuteDuration)

        val hasMuteDuration = try {
            when {
                access_info.isMisskey || access_info.isPseudo -> false
                else -> {
                    var resultBoolean = false
                    runApiTask(access_info) { client ->
                        val (ti, ri) = TootInstance.get(client)
                        resultBoolean = ti?.versionGE(TootInstance.VERSION_3_3_0_rc1) == true
                        ri
                    }
                    resultBoolean
                }
            }
        } catch (ex: CancellationException) {
            // not show error
            return@launchMain
        } catch (ex: RuntimeException) {
            showToast(true, ex.message)
            return@launchMain
        }

        if (hasMuteDuration) {
            view.findViewById<View>(R.id.llMuteDuration).vg(true)
            spMuteDuration.apply {
                adapter = ArrayAdapter(
                    activity,
                    android.R.layout.simple_spinner_item,
                    choiceList.map { it.second }.toTypedArray(),
                ).apply {
                    setDropDownViewResource(R.layout.lv_spinner_dropdown)
                }
            }
        }

        AlertDialog.Builder(activity)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok)
            { _, _ ->
                userMute(
                    access_info,
                    who,
                    whoAccessInfo,
                    bMute = true,
                    bMuteNotification = cbMuteNotification.isChecked,
                    duration = spMuteDuration.selectedItemPosition
                        .takeIf { hasMuteDuration && it in choiceList.indices }
                        ?.let { choiceList[it].first }
                )
            }
            .show()
    }
}

fun ActMain.userMuteFromAnotherAccount(
    who: TootAccount,
    whoAccessInfo: SavedAccount
) {
    launchMain {
        pickAccount(
            bAllowPseudo = false,
            bAuto = false,
            message = getString(R.string.account_picker_mute, who.acct.pretty),
            accountListArg = accountListNonPseudo(who.apDomain)
        )?.let {
            userMuteConfirm(it, who, whoAccessInfo)
        }
    }
}

// ユーザをブロック/ブロック解除する
fun ActMain.userBlock(
    access_info: SavedAccount,
    whoArg: TootAccount,
    whoAccessInfo: SavedAccount,
    bBlock: Boolean
) {
    val whoAcct = whoArg.acct
    if (access_info.isMe(whoAcct)) {
        showToast(false, R.string.it_is_you)
        return
    }

    launchMain {
        var relationResult: UserRelation? = null
        var whoIdResult: EntityId? = null
        runApiTask(access_info) { client ->
            if (access_info.isPseudo) {
                if (whoAcct.ascii.contains('?')) {
                    TootApiResult("can't block pseudo account ${whoAcct.pretty}")
                } else {
                    val relation = UserRelation.loadPseudo(whoAcct)
                    relation.blocking = bBlock
                    relation.savePseudo(whoAcct.ascii)
                    relationResult = relation
                    TootApiResult()
                }
            } else {
                val whoId = if (access_info.matchHost(whoAccessInfo)) {
                    whoArg.id
                } else {
                    val (result, accountRef) = client.syncAccountByAcct(access_info, whoAcct)
                    accountRef?.get()?.id ?: return@runApiTask result
                }
                whoIdResult = whoId

                if (access_info.isMisskey) {

                    fun saveBlock(v: Boolean) {
                        val ur = UserRelation.load(access_info.db_id, whoId)
                        ur.blocking = v
                        UserRelation.save1Misskey(
                            System.currentTimeMillis(),
                            access_info.db_id,
                            whoId.toString(),
                            ur
                        )
                        relationResult = ur
                    }

                    client.request(
                        "/api/blocking/${if (bBlock) "create" else "delete"}",
                        access_info.putMisskeyApiToken().apply {
                            put("userId", whoId.toString())
                        }.toPostRequestBuilder()
                    )?.apply {
                        val error = this.error
                        when {
                            // success
                            error == null -> saveBlock(bBlock)

                            // already
                            error.contains("already blocking") -> saveBlock(bBlock)
                            error.contains("already not blocking") -> saveBlock(bBlock)

                            // else something error
                        }
                    }
                } else {
                    client.request(
                        "/api/v1/accounts/${whoId}/${if (bBlock) "block" else "unblock"}",
                        "".toFormRequestBody().toPost()
                    )?.also { result ->
                        val parser = TootParser(this, access_info)
                        relationResult = access_info.saveUserRelation(
                            parseItem(::TootRelationShip, parser, result.jsonObject)
                        )
                    }
                }
            }
        }?.let { result ->

            val relation = relationResult
            val whoId = whoIdResult
            when {
                relation == null || whoId == null ->
                    showToast(false, result.error)

                else -> {

                    // 自分をブロックしようとすると、blocking==falseで帰ってくる
                    if (bBlock && !relation.blocking) {
                        showToast(false, R.string.not_blocked)
                        return@launchMain
                    }

                    for (column in app_state.columnList) {
                        if (column.access_info.isPseudo) {
                            if (relation.blocking) {
                                // ミュートしたユーザの情報はTLから消える
                                column.removeAccountInTimelinePseudo(whoAcct)
                            }
                            // フォローアイコンの表示更新が走る
                            column.updateFollowIcons(access_info)
                        } else if (column.access_info == access_info) {
                            when {
                                !relation.blocking -> {
                                    if (column.type == ColumnType.BLOCKS) {
                                        // ブロック解除したら「ブロックしたユーザ」カラムのリストから消える
                                        column.removeUser(access_info, ColumnType.BLOCKS, whoId)
                                    } else {
                                        // 他のカラムではフォローアイコンの更新を行う
                                        column.updateFollowIcons(access_info)
                                    }
                                }

                                access_info.isMisskey -> {
                                    // Misskeyのブロックはフォロー解除とフォロー拒否だけなので
                                    // カラム中の投稿を消すなどの効果はない
                                    // しかしカラム中のフォローアイコン表示の更新は必要
                                    column.updateFollowIcons(access_info)
                                }

                                // 該当ユーザのプロフカラムではブロックしててもトゥートを見れる
                                // しかしカラム中のフォローアイコン表示の更新は必要
                                column.type == ColumnType.PROFILE && whoId == column.profile_id -> {
                                    column.updateFollowIcons(access_info)
                                }

                                // MastodonではブロックしたらTLからそのアカウントの投稿が消える
                                else -> column.removeAccountInTimeline(access_info, whoId)
                            }
                        }
                    }

                    showToast(
                        false,
                        if (relation.blocking)
                            R.string.block_succeeded
                        else
                            R.string.unblock_succeeded
                    )
                }
            }
        }
    }
}

fun ActMain.userBlockConfirm(
    access_info: SavedAccount,
    who: TootAccount,
    whoAccessInfo: SavedAccount
) {
    AlertDialog.Builder(this)
        .setMessage(getString(R.string.confirm_block_user, who.username))
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(R.string.ok) { _, _ ->
            userBlock(
                access_info,
                who,
                whoAccessInfo,
                bBlock = true
            )
        }
        .show()
}

fun ActMain.userBlockFromAnotherAccount(
    who: TootAccount,
    whoAccessInfo: SavedAccount
) {
    launchMain {
        pickAccount(
            bAllowPseudo = false,
            bAuto = false,
            message = getString(R.string.account_picker_block, who.acct.pretty),
            accountListArg = accountListNonPseudo(who.apDomain)
        )?.let { ai ->
            userBlockConfirm(ai, who, whoAccessInfo)
        }
    }
}

//////////////////////////////////////////////////////////////////////////////////////

// ユーザURLを同期してプロフカラムを開く
private fun ActMain.userProfileFromUrlOrAcct(
    pos: Int,
    access_info: SavedAccount,
    acct: Acct,
    who_url: String,
) {
    launchMain {

        var resultWho: TootAccount? = null
        runApiTask(access_info) { client ->
            val (result, ar) = client.syncAccountByUrl(access_info, who_url)
            if (result == null) {
                null
            } else {
                resultWho = ar?.get()
                if (resultWho != null) {
                    result
                } else {
                    val (r2, ar2) = client.syncAccountByAcct(access_info, acct)
                    resultWho = ar2?.get()
                    r2
                }
            }
        }?.let { result ->
            when (val who = resultWho) {
                null -> {
                    showToast(true, result.error)
                    // 仕方ないのでchrome tab で開く
                    openCustomTab(who_url)
                }

                else -> addColumn(pos, access_info, ColumnType.PROFILE, who.id)
            }
        }
    }
}

// アカウントを選んでユーザプロフを開く
fun ActMain.userProfileFromAnotherAccount(
    pos: Int,
    access_info: SavedAccount,
    who: TootAccount?
) {
    who?.url ?: return
    launchMain {
        pickAccount(
            bAllowPseudo = false,
            bAuto = false,
            message = getString(
                R.string.account_picker_open_user_who,
                AcctColor.getNickname(access_info, who)
            ),
            accountListArg = accountListNonPseudo(who.apDomain)
        )?.let { ai ->
            if (ai.matchHost(access_info)) {
                addColumn(pos, ai, ColumnType.PROFILE, who.id)
            } else {
                userProfileFromUrlOrAcct(pos, ai, access_info.getFullAcct(who), who.url)
            }
        }
    }
}

// 今のアカウントでユーザプロフを開く
fun ActMain.userProfileLocal(
    pos: Int,
    access_info: SavedAccount,
    who: TootAccount
) {
    when {
        access_info.isNA -> userProfileFromAnotherAccount(pos, access_info, who)
        else -> addColumn(pos, access_info, ColumnType.PROFILE, who.id)
    }
}

// user@host で指定されたユーザのプロフを開く
// Intent-Filter や openChromeTabから 呼ばれる
fun ActMain.userProfile(
    pos: Int,
    access_info: SavedAccount?,
    acct: Acct,
    userUrl: String,
    original_url: String = userUrl
) {
    if (access_info?.isPseudo == false) {
        // 文脈のアカウントがあり、疑似アカウントではない

        if (!access_info.matchHost(acct.host)) {
            // 文脈のアカウントと異なるインスタンスなら、別アカウントで開く
            userProfileFromUrlOrAcct(pos, access_info, acct, userUrl)
        } else {
            // 文脈のアカウントと同じインスタンスなら、アカウントIDを探して開いてしまう
            launchMain {
                var resultWho: TootAccount? = null
                runApiTask(access_info) { client ->
                    val (result, ar) = client.syncAccountByAcct(access_info, acct)
                    resultWho = ar?.get()
                    result
                }?.let {
                    when (val who = resultWho) {
                        // ダメならchromeで開く
                        null -> openCustomTab(userUrl)
                        // 変換できたアカウント情報で開く
                        else -> userProfileLocal(pos, access_info, who)
                    }
                }
            }
        }
        return
    }

    // 文脈がない、もしくは疑似アカウントだった
    // 疑似アカウントでは検索APIを使えないため、IDが分からない

    if (!SavedAccount.hasRealAccount()) {
        // 疑似アカウントしか登録されていない
        // chrome tab で開く
        openCustomTab(original_url)
        return
    }
    launchMain {
        val activity = this@userProfile
        pickAccount(
            bAllowPseudo = false,
            bAuto = false,
            message = getString(
                R.string.account_picker_open_user_who,
                AcctColor.getNickname(acct)
            ),
            accountListArg = accountListNonPseudo(acct.host),
            extra_callback = { ll, pad_se, pad_tb ->
                // chrome tab で開くアクションを追加
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                val b = Button(activity)
                b.setPaddingRelative(pad_se, pad_tb, pad_se, pad_tb)
                b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                b.isAllCaps = false
                b.layoutParams = lp
                b.minHeight = (0.5f + 32f * activity.density).toInt()
                b.text = getString(R.string.open_in_browser)
                b.setBackgroundResource(R.drawable.btn_bg_transparent_round6dp)

                b.setOnClickListener {
                    openCustomTab(original_url)
                }
                ll.addView(b, 0)
            }
        )?.let {
            userProfileFromUrlOrAcct(pos, it, acct, userUrl)
        }
    }
}

//////////////////////////////////////////////////////////////////////////////////////

// 通報フォームを開く
fun ActMain.userReportForm(
    access_info: SavedAccount,
    who: TootAccount,
    status: TootStatus? = null
) {
    ReportForm.showReportForm(this, access_info, who, status) { dialog, comment, forward ->
        userReport(access_info, who, status, comment, forward) {
            dialog.dismissSafe()
        }
    }
}

// 通報する
private fun ActMain.userReport(
    access_info: SavedAccount,
    who: TootAccount,
    status: TootStatus?,
    comment: String,
    forward: Boolean,
    onReportComplete: (result: TootApiResult) -> Unit
) {
    if (access_info.isMe(who)) {
        showToast(false, R.string.it_is_you)
        return
    }
    launchMain {
        runApiTask(access_info) { client ->
            if (access_info.isMisskey) {
                client.request(
                    "/api/users/report-abuse",
                    access_info.putMisskeyApiToken().apply {
                        put("userId", who.id.toString())
                        put(
                            "comment",
                            StringBuilder().apply {
                                status?.let {
                                    append(it.url)
                                    append("\n")
                                }
                                append(comment)
                            }.toString()
                        )
                    }.toPostRequestBuilder()
                )
            } else {
                client.request(
                    "/api/v1/reports",
                    JsonObject().apply {
                        put("account_id", who.id.toString())
                        put("comment", comment)
                        put("forward", forward)
                        if (status != null) {
                            put("status_ids", jsonArray {
                                add(status.id.toString())
                            })
                        }
                    }.toPostRequestBuilder()
                )
            }
        }?.let { result ->
            when (result.jsonObject) {
                null -> showToast(true, result.error)
                else -> {
                    onReportComplete(result)
                    showToast(false, R.string.report_completed)
                }
            }
        }
    }
}

// show/hide boosts from (following) user
fun ActMain.userSetShowBoosts(
    access_info: SavedAccount,
    who: TootAccount,
    bShow: Boolean
) {

    if (access_info.isMe(who)) {
        showToast(false, R.string.it_is_you)
        return
    }

    launchMain {
        var resultRelation: UserRelation? = null

        runApiTask(access_info) { client ->
            client.request(
                "/api/v1/accounts/${who.id}/follow",
                jsonObjectOf("reblogs" to bShow).toPostRequestBuilder()
            )?.also { result ->
                val parser = TootParser(this, access_info)
                resultRelation = access_info.saveUserRelation(parseItem(::TootRelationShip, parser, result.jsonObject))
            }
        }?.let { result ->
            when (resultRelation) {
                null -> showToast(true, result.error)
                else -> showToast(true, R.string.operation_succeeded)
            }
        }
    }
}

fun ActMain.userSuggestionDelete(
    access_info: SavedAccount,
    who: TootAccount,
    bConfirmed: Boolean = false
) {
    if (!bConfirmed) {
        val name = who.decodeDisplayName(applicationContext)
        AlertDialog.Builder(this)
            .setMessage(name.intoStringResource(applicationContext, R.string.delete_succeeded_confirm))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                userSuggestionDelete(access_info, who, bConfirmed = true)
            }
            .show()
        return
    }
    launchMain {
        runApiTask(access_info) { client ->
            client.request("/api/v1/suggestions/${who.id}", Request.Builder().delete())
        }?.let { result ->
            when (result.error) {
                null -> {
                    showToast(false, R.string.delete_succeeded)
                    // update suggestion column
                    for (column in app_state.columnList) {
                        column.removeUser(access_info, ColumnType.FOLLOW_SUGGESTION, who.id)
                    }

                }
                else -> showToast(true, result.error)
            }
        }
    }
}

fun ActMain.userSetStatusNotification(
    accessInfo: SavedAccount,
    whoId: EntityId,
    enabled: Boolean
) {
    launchMain {
        runApiTask(accessInfo) { client ->
            client.request(
                "/api/v1/accounts/$whoId/follow",
                jsonObject {
                    put("notify", enabled)
                }.toPostRequestBuilder()
            )?.also { result ->
                val relation = parseItem(
                    ::TootRelationShip,
                    TootParser(this, accessInfo),
                    result.jsonObject
                )
                if (relation != null) {
                    UserRelation.save1Mastodon(
                        System.currentTimeMillis(),
                        accessInfo.db_id,
                        relation
                    )
                }
            }
        }?.let { result ->
            when (val error = result.error) {
                null -> showToast(false, R.string.operation_succeeded)
                else -> showToast(true, error)
            }
        }
    }
}

fun ActMain.userEndorsement(
    access_info: SavedAccount,
    who: TootAccount,
    bSet: Boolean
) {
    if (access_info.isMisskey) {
        showToast(false, "This feature is not provided on Misskey account.")
        return
    }

    launchMain {

        @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
        var resultRelation: UserRelation? = null

        runApiTask(access_info) { client ->
            client.request(
                "/api/v1/accounts/${who.id}/" + when (bSet) {
                    true -> "pin"
                    false -> "unpin"
                },
                "".toFormRequestBody().toPost()
            )
                ?.also { result ->
                    val parser = TootParser(this, access_info)
                    resultRelation = access_info.saveUserRelation(
                        parseItem(::TootRelationShip, parser, result.jsonObject)
                    )
                }
        }?.let { result ->

            when (val error = result.error) {
                null -> showToast(
                    false, when (bSet) {
                        true -> R.string.endorse_succeeded
                        else -> R.string.remove_endorse_succeeded
                    }
                )
                else -> showToast(true, error)
            }
        }
    }
}
