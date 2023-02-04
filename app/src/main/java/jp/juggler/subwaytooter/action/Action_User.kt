package jp.juggler.subwaytooter.action

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.widget.AppCompatButton
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.actmain.addColumn
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.column.*
import jp.juggler.subwaytooter.dialog.DlgConfirm.confirm
import jp.juggler.subwaytooter.dialog.ReportForm
import jp.juggler.subwaytooter.dialog.pickAccount
import jp.juggler.subwaytooter.table.*
import jp.juggler.subwaytooter.util.matchHost
import jp.juggler.subwaytooter.util.openCustomTab
import jp.juggler.util.*
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.buildJsonArray
import jp.juggler.util.data.buildJsonObject
import jp.juggler.util.data.intoStringResource
import jp.juggler.util.data.jsonObjectOf
import jp.juggler.util.log.showToast
import jp.juggler.util.network.*
import jp.juggler.util.ui.dismissSafe
import jp.juggler.util.ui.vg
import kotlinx.coroutines.*
import okhttp3.Request
import java.util.*

// private val log = LogCategory("Action_User")

fun ActMain.clickMute(
    accessInfo: SavedAccount,
    who: TootAccount,
    relation: UserRelation,
) = when {
    relation.muting -> userUnmute(accessInfo, who, accessInfo)
    else -> userMuteConfirm(accessInfo, who, accessInfo)
}

fun ActMain.clickBlock(
    accessInfo: SavedAccount,
    who: TootAccount,
    relation: UserRelation,
) = when {
    relation.blocking -> userBlock(accessInfo, who, accessInfo, false)
    else -> userBlockConfirm(accessInfo, who, accessInfo)
}

fun ActMain.clickNicknameCustomize(
    accessInfo: SavedAccount,
    who: TootAccount,
) = arNickname.launch(ActNickname.createIntent(this, accessInfo.getFullAcct(who), true))

fun ActMain.openAvatarImage(who: TootAccount) {
    openCustomTab(
        when {
            who.avatar.isNullOrEmpty() -> who.avatar_static
            else -> who.avatar
        }
    )
}

fun ActMain.clickHideFavourite(
    accessInfo: SavedAccount,
    who: TootAccount,
) = launchAndShowError {
    val acct = accessInfo.getFullAcct(who)
    daoFavMute.save(acct)
    showToast(false, R.string.changed)
    for (column in appState.columnList) {
        column.onHideFavouriteNotification(acct)
    }
}

fun ActMain.clickShowFavourite(
    accessInfo: SavedAccount,
    who: TootAccount,
) = launchAndShowError {
    daoFavMute.delete(accessInfo.getFullAcct(who))
    showToast(false, R.string.changed)
}

fun ActMain.clickStatusNotification(
    accessInfo: SavedAccount,
    who: TootAccount,
    relation: UserRelation,
) {
    if (!accessInfo.isPseudo &&
        accessInfo.isMastodon &&
        relation.following
    ) {
        userSetStatusNotification(accessInfo, who.id, enabled = !relation.notifying)
    }
}

// ユーザをミュート/ミュート解除する
private fun ActMain.userMute(
    accessInfo: SavedAccount,
    whoArg: TootAccount,
    whoAccessInfo: SavedAccount,
    bMute: Boolean,
    bMuteNotification: Boolean,
    duration: Int?,
) = launchAndShowError {
    val whoAcct = whoAccessInfo.getFullAcct(whoArg)
    if (accessInfo.isMe(whoAcct)) {
        showToast(false, R.string.it_is_you)
        return@launchAndShowError
    }

    var resultRelation: UserRelation? = null
    var resultWhoId: EntityId? = null
    runApiTask(accessInfo) { client ->
        val parser = TootParser(this, accessInfo)
        if (accessInfo.isPseudo) {
            if (!whoAcct.isValidFull) {
                TootApiResult("can't mute pseudo acct ${whoAcct.pretty}")
            } else {
                val relation = daoUserRelation.loadPseudo(whoAcct)
                relation.muting = bMute
                daoUserRelation.savePseudo(whoAcct.ascii, relation)
                resultRelation = relation
                resultWhoId = whoArg.id
                TootApiResult()
            }
        } else {
            val whoId = if (accessInfo.matchHost(whoAccessInfo)) {
                whoArg.id
            } else {
                val (result, accountRef) = client.syncAccountByAcct(accessInfo, whoAcct)
                accountRef?.get()?.id ?: return@runApiTask result
            }
            resultWhoId = whoId

            if (accessInfo.isMisskey) {
                client.request(
                    when (bMute) {
                        true -> "/api/mute/create"
                        else -> "/api/mute/delete"
                    },
                    accessInfo.putMisskeyApiToken().apply {
                        put("userId", whoId.toString())
                    }.toPostRequestBuilder()
                )?.apply {
                    if (jsonObject != null) {
                        // 204 no content

                        // update user relation
                        val ur = daoUserRelation.load(accessInfo.db_id, whoId)
                        ur.muting = bMute
                        daoUserRelation.saveUserRelationMisskey(
                            accessInfo,
                            whoId,
                            parser
                        )

                        resultRelation = ur
                    }
                }
            } else {
                client.request(
                    "/api/v1/accounts/$whoId/${if (bMute) "mute" else "unmute"}",
                    when {
                        !bMute -> "".toFormRequestBody()
                        else ->
                            buildJsonObject {
                                put("notifications", bMuteNotification)
                                if (duration != null) put("duration", duration)
                            }.toRequestBody()
                    }.toPost()
                )?.apply {
                    val jsonObject = jsonObject
                    if (jsonObject != null) {
                        resultRelation = daoUserRelation.saveUserRelation(
                            accessInfo,
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
                return@launchAndShowError
            }

            for (column in appState.columnList) {
                if (column.accessInfo.isPseudo) {
                    if (relation.muting && column.type != ColumnType.PROFILE) {
                        // ミュートしたユーザの情報はTLから消える
                        column.removeAccountInTimelinePseudo(whoAcct)
                    }
                    // フォローアイコンの表示更新が走る
                    column.updateFollowIcons(accessInfo)
                } else if (column.accessInfo == accessInfo) {
                    when {
                        !relation.muting -> {
                            if (column.type == ColumnType.MUTES) {
                                // ミュート解除したら「ミュートしたユーザ」カラムから消える
                                column.removeUser(accessInfo, ColumnType.MUTES, whoId)
                            } else {
                                // 他のカラムではフォローアイコンの表示更新が走る
                                column.updateFollowIcons(accessInfo)
                            }
                        }

                        column.type == ColumnType.PROFILE && column.profileId == whoId -> {
                            // 該当ユーザのプロフページのトゥートはミュートしてても見れる
                            // しかしフォローアイコンの表示更新は必要
                            column.updateFollowIcons(accessInfo)
                        }

                        else -> {
                            // ミュートしたユーザの情報はTLから消える
                            column.removeAccountInTimeline(accessInfo, whoId)
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

fun ActMain.userUnmute(
    accessInfo: SavedAccount,
    whoArg: TootAccount,
    whoAccessInfo: SavedAccount,
) = userMute(
    accessInfo,
    whoArg,
    whoAccessInfo,
    bMute = false,
    bMuteNotification = false,
    duration = null,
)

fun ActMain.userMuteConfirm(
    accessInfo: SavedAccount,
    who: TootAccount,
    whoAccessInfo: SavedAccount,
) = launchAndShowError {
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
    val hasMuteNotification = !accessInfo.isMisskey && !accessInfo.isPseudo
    cbMuteNotification.isChecked = hasMuteNotification
    cbMuteNotification.vg(hasMuteNotification)
        ?.setText(R.string.confirm_mute_notification_for_user)

    val spMuteDuration: Spinner = view.findViewById(R.id.spMuteDuration)
    val hasMuteDuration = try {
        when {
            accessInfo.isMisskey || accessInfo.isPseudo -> false
            else -> {
                var resultBoolean = false
                runApiTask(accessInfo) { client ->
                    val (ti, ri) = TootInstance.get(client)
                    resultBoolean = ti?.versionGE(TootInstance.VERSION_3_3_0_rc1) == true
                    ri
                }
                resultBoolean
            }
        }
    } catch (ignored: CancellationException) {
        // not show error
        return@launchAndShowError
    } catch (ex: RuntimeException) {
        showToast(true, ex.message)
        return@launchAndShowError
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
        .setPositiveButton(R.string.ok) { _, _ ->
            userMute(
                accessInfo,
                who,
                whoAccessInfo,
                bMute = true,
                bMuteNotification = cbMuteNotification.isChecked,
                duration = spMuteDuration.selectedItemPosition
                    .takeIf { hasMuteDuration && it in choiceList.indices }
                    ?.let { choiceList[it].first }
            )
        }.show()
}

fun ActMain.userMuteFromAnotherAccount(
    who: TootAccount?,
    whoAccessInfo: SavedAccount,
) {
    who ?: return
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
    accessInfo: SavedAccount,
    whoArg: TootAccount,
    whoAccessInfo: SavedAccount,
    bBlock: Boolean,
) {
    val whoAcct = whoArg.acct
    if (accessInfo.isMe(whoAcct)) {
        showToast(false, R.string.it_is_you)
        return
    }

    launchMain {
        var relationResult: UserRelation? = null
        var whoIdResult: EntityId? = null
        runApiTask(accessInfo) { client ->
            if (accessInfo.isPseudo) {
                if (whoAcct.ascii.contains('?')) {
                    TootApiResult("can't block pseudo account ${whoAcct.pretty}")
                } else {
                    val relation = daoUserRelation.loadPseudo(whoAcct)
                    relation.blocking = bBlock
                    daoUserRelation.savePseudo(whoAcct.ascii, relation)
                    relationResult = relation
                    TootApiResult()
                }
            } else {
                val whoId = if (accessInfo.matchHost(whoAccessInfo)) {
                    whoArg.id
                } else {
                    val (result, accountRef) = client.syncAccountByAcct(accessInfo, whoAcct)
                    accountRef?.get()?.id ?: return@runApiTask result
                }
                whoIdResult = whoId

                if (accessInfo.isMisskey) {
                    fun saveBlock(v: Boolean) {
                        val ur = daoUserRelation.load(accessInfo.db_id, whoId)
                        ur.blocking = v
                        daoUserRelation.save1Misskey(
                            System.currentTimeMillis(),
                            accessInfo.db_id,
                            whoId.toString(),
                            ur
                        )
                        relationResult = ur
                    }

                    client.request(
                        "/api/blocking/${if (bBlock) "create" else "delete"}",
                        accessInfo.putMisskeyApiToken().apply {
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
                        "/api/v1/accounts/$whoId/${if (bBlock) "block" else "unblock"}",
                        "".toFormRequestBody().toPost()
                    )?.also { result ->
                        val parser = TootParser(this, accessInfo)
                        relationResult = daoUserRelation.saveUserRelation(
                            accessInfo,
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

                    for (column in appState.columnList) {
                        if (column.accessInfo.isPseudo) {
                            if (relation.blocking) {
                                // ミュートしたユーザの情報はTLから消える
                                column.removeAccountInTimelinePseudo(whoAcct)
                            }
                            // フォローアイコンの表示更新が走る
                            column.updateFollowIcons(accessInfo)
                        } else if (column.accessInfo == accessInfo) {
                            when {
                                !relation.blocking -> {
                                    if (column.type == ColumnType.BLOCKS) {
                                        // ブロック解除したら「ブロックしたユーザ」カラムのリストから消える
                                        column.removeUser(accessInfo, ColumnType.BLOCKS, whoId)
                                    } else {
                                        // 他のカラムではフォローアイコンの更新を行う
                                        column.updateFollowIcons(accessInfo)
                                    }
                                }

                                accessInfo.isMisskey -> {
                                    // Misskeyのブロックはフォロー解除とフォロー拒否だけなので
                                    // カラム中の投稿を消すなどの効果はない
                                    // しかしカラム中のフォローアイコン表示の更新は必要
                                    column.updateFollowIcons(accessInfo)
                                }

                                // 該当ユーザのプロフカラムではブロックしててもトゥートを見れる
                                // しかしカラム中のフォローアイコン表示の更新は必要
                                column.type == ColumnType.PROFILE && whoId == column.profileId -> {
                                    column.updateFollowIcons(accessInfo)
                                }

                                // MastodonではブロックしたらTLからそのアカウントの投稿が消える
                                else -> column.removeAccountInTimeline(accessInfo, whoId)
                            }
                        }
                    }

                    showToast(
                        false,
                        when {
                            relation.blocking -> R.string.block_succeeded
                            else -> R.string.unblock_succeeded
                        }
                    )
                }
            }
        }
    }
}

fun ActMain.userBlockConfirm(
    accessInfo: SavedAccount,
    who: TootAccount,
    whoAccessInfo: SavedAccount,
) {
    AlertDialog.Builder(this)
        .setMessage(getString(R.string.confirm_block_user, who.username))
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(R.string.ok) { _, _ ->
            userBlock(
                accessInfo,
                who,
                whoAccessInfo,
                bBlock = true
            )
        }
        .show()
}

fun ActMain.userBlockFromAnotherAccount(
    who: TootAccount?,
    whoAccessInfo: SavedAccount,
) {
    who ?: return
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
    accessInfo: SavedAccount,
    acct: Acct,
    whoUrl: String,
) {
    launchMain {

        var resultWho: TootAccount? = null
        runApiTask(accessInfo) { client ->
            val (result, ar) = client.syncAccountByUrl(accessInfo, whoUrl)
            if (result == null) {
                null
            } else {
                resultWho = ar?.get()
                if (resultWho != null) {
                    result
                } else {
                    val (r2, ar2) = client.syncAccountByAcct(accessInfo, acct)
                    resultWho = ar2?.get()
                    r2
                }
            }
        }?.let { result ->
            when (val who = resultWho) {
                null -> {
                    showToast(true, result.error)
                    // 仕方ないのでchrome tab で開く
                    openCustomTab(whoUrl)
                }

                else -> addColumn(pos, accessInfo, ColumnType.PROFILE, who.id)
            }
        }
    }
}

// アカウントを選んでユーザプロフを開く
fun ActMain.userProfileFromAnotherAccount(
    pos: Int,
    accessInfo: SavedAccount,
    who: TootAccount?,
) {
    who?.url ?: return
    launchMain {
        pickAccount(
            bAllowPseudo = false,
            bAuto = false,
            message = getString(
                R.string.account_picker_open_user_who,
                daoAcctColor.getNickname(accessInfo, who)
            ),
            accountListArg = accountListNonPseudo(who.apDomain)
        )?.let { ai ->
            if (ai.matchHost(accessInfo)) {
                addColumn(pos, ai, ColumnType.PROFILE, who.id)
            } else {
                userProfileFromUrlOrAcct(pos, ai, accessInfo.getFullAcct(who), who.url)
            }
        }
    }
}

// 今のアカウントでユーザプロフを開く
fun ActMain.userProfileLocal(
    pos: Int,
    accessInfo: SavedAccount,
    who: TootAccount,
) {
    when {
        accessInfo.isNA -> userProfileFromAnotherAccount(pos, accessInfo, who)
        else -> addColumn(pos, accessInfo, ColumnType.PROFILE, who.id)
    }
}

// user@host で指定されたユーザのプロフを開く
// Intent-Filter や openChromeTabから 呼ばれる
fun ActMain.userProfile(
    pos: Int,
    accessInfo: SavedAccount?,
    acct: Acct,
    userUrl: String,
    originalUrl: String = userUrl,
) = launchAndShowError {
    if (accessInfo?.isPseudo == false) {
        // 文脈のアカウントがあり、疑似アカウントではない

        if (!accessInfo.matchHost(acct.host)) {
            // 文脈のアカウントと異なるインスタンスなら、別アカウントで開く
            userProfileFromUrlOrAcct(pos, accessInfo, acct, userUrl)
        } else {
            // 文脈のアカウントと同じインスタンスなら、アカウントIDを探して開いてしまう
            launchMain {
                var resultWho: TootAccount? = null
                runApiTask(accessInfo) { client ->
                    val (result, ar) = client.syncAccountByAcct(accessInfo, acct)
                    resultWho = ar?.get()
                    result
                }?.let {
                    when (val who = resultWho) {
                        // ダメならchromeで開く
                        null -> openCustomTab(userUrl)
                        // 変換できたアカウント情報で開く
                        else -> userProfileLocal(pos, accessInfo, who)
                    }
                }
            }
        }
        return@launchAndShowError
    }

    // 文脈がない、もしくは疑似アカウントだった
    // 疑似アカウントでは検索APIを使えないため、IDが分からない

    if (!daoSavedAccount.hasRealAccount()) {
        // 疑似アカウントしか登録されていない
        // chrome tab で開く
        openCustomTab(originalUrl)
        return@launchAndShowError
    }
    val activity = this@userProfile
    pickAccount(
        bAllowPseudo = false,
        bAuto = false,
        message = getString(
            R.string.account_picker_open_user_who,
            daoAcctColor.getNickname(acct)
        ),
        accountListArg = accountListNonPseudo(acct.host),
        extraCallback = { ll, pad_se, pad_tb ->
            // chrome tab で開くアクションを追加
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val b = AppCompatButton(activity)
            b.setPaddingRelative(pad_se, pad_tb, pad_se, pad_tb)
            b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            b.isAllCaps = false
            b.layoutParams = lp
            b.minHeight = (0.5f + 32f * activity.density).toInt()
            b.text = getString(R.string.open_in_browser)
            b.setBackgroundResource(R.drawable.btn_bg_transparent_round6dp)

            b.setOnClickListener {
                openCustomTab(originalUrl)
            }
            ll.addView(b, 0)
        }
    )?.let {
        userProfileFromUrlOrAcct(pos, it, acct, userUrl)
    }
}

//////////////////////////////////////////////////////////////////////////////////////

// 通報フォームを開く
fun ActMain.userReportForm(
    accessInfo: SavedAccount,
    who: TootAccount,
    status: TootStatus? = null,
) {
    ReportForm.showReportForm(this, accessInfo, who, status) { dialog, comment, forward ->
        userReport(accessInfo, who, status, comment, forward) {
            dialog.dismissSafe()
        }
    }
}

// 通報する
private fun ActMain.userReport(
    accessInfo: SavedAccount,
    who: TootAccount,
    status: TootStatus?,
    comment: String,
    forward: Boolean,
    onReportComplete: (result: TootApiResult) -> Unit,
) {
    if (accessInfo.isMe(who)) {
        showToast(false, R.string.it_is_you)
        return
    }
    launchMain {
        runApiTask(accessInfo) { client ->
            if (accessInfo.isMisskey) {
                client.request(
                    "/api/users/report-abuse",
                    accessInfo.putMisskeyApiToken().apply {
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
                    buildJsonObject {
                        put("account_id", who.id.toString())
                        put("comment", comment)
                        put("forward", forward)
                        if (status != null) {
                            put("status_ids", buildJsonArray {
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
    accessInfo: SavedAccount,
    who: TootAccount,
    bShow: Boolean,
) {

    if (accessInfo.isMe(who)) {
        showToast(false, R.string.it_is_you)
        return
    }

    launchMain {
        var resultRelation: UserRelation? = null

        runApiTask(accessInfo) { client ->
            client.request(
                "/api/v1/accounts/${who.id}/follow",
                jsonObjectOf("reblogs" to bShow).toPostRequestBuilder()
            )?.also { result ->
                val parser = TootParser(this, accessInfo)
                resultRelation = daoUserRelation.saveUserRelation(
                    accessInfo,
                    parseItem(::TootRelationShip, parser, result.jsonObject)
                )
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
    accessInfo: SavedAccount,
    who: TootAccount,
) {
    val activity = this
    launchAndShowError {
        confirm(
            who.decodeDisplayName(activity)
                .intoStringResource(activity, R.string.delete_succeeded_confirm)
        )
        runApiTask(accessInfo) { client ->
            client.request("/api/v1/suggestions/${who.id}", Request.Builder().delete())
        }?.let { result ->
            when (result.error) {
                null -> {
                    showToast(false, R.string.delete_succeeded)
                    // update suggestion column
                    for (column in appState.columnList) {
                        column.removeUser(accessInfo, ColumnType.FOLLOW_SUGGESTION, who.id)
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
    enabled: Boolean,
) {
    launchMain {
        runApiTask(accessInfo) { client ->
            client.request(
                "/api/v1/accounts/$whoId/follow",
                jsonObjectOf("notify" to enabled)
                    .toPostRequestBuilder()
            )?.also { result ->
                val relation = parseItem(
                    ::TootRelationShip,
                    TootParser(this, accessInfo),
                    result.jsonObject
                )
                if (relation != null) {
                    daoUserRelation.save1Mastodon(
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
    accessInfo: SavedAccount,
    who: TootAccount,
    bSet: Boolean,
) {
    if (accessInfo.isMisskey) {
        showToast(false, "This feature is not provided on Misskey account.")
        return
    }

    launchMain {

        @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
        var resultRelation: UserRelation? = null

        runApiTask(accessInfo) { client ->
            client.request(
                "/api/v1/accounts/${who.id}/" + when (bSet) {
                    true -> "pin"
                    false -> "unpin"
                },
                "".toFormRequestBody().toPost()
            )
                ?.also { result ->
                    val parser = TootParser(this, accessInfo)
                    resultRelation = daoUserRelation.saveUserRelation(
                        accessInfo,
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
