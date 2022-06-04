package jp.juggler.subwaytooter.actpost

import jp.juggler.subwaytooter.ActPost
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootVisibility
import jp.juggler.subwaytooter.dialog.pickAccount
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.*
import org.jetbrains.anko.textColor
import kotlin.math.max

private val log = LogCategory("ActPostAccount")

fun ActPost.selectAccount(a: SavedAccount?) {
    this.account = a

    completionHelper.setInstance(a)

    if (a == null) {
        views.btnAccount.text = getString(R.string.not_selected)
        views.btnAccount.setTextColor(attrColor(android.R.attr.textColorPrimary))
        views.btnAccount.setBackgroundResource(R.drawable.btn_bg_transparent_round6dp)
    } else {
        launchMain {
            // 先読みしてキャッシュに保持しておく
            // 何もしない
            App1.custom_emoji_lister.getList(a)
        }

        views.spLanguage.setSelection(max(0, languages.indexOfFirst { it.first == a.lang }))

        val ac = AcctColor.load(a)
        views.btnAccount.text = ac.nickname

        if (AcctColor.hasColorBackground(ac)) {
            views.btnAccount.background =
                getAdaptiveRippleDrawableRound(this, ac.color_bg, ac.color_fg)
        } else {
            views.btnAccount.setBackgroundResource(R.drawable.btn_bg_transparent_round6dp)
        }

        views.btnAccount.textColor = ac.color_fg.notZero()
            ?: attrColor(android.R.attr.textColorPrimary)
    }
    updateTextCount()
    updateFeaturedTags()
}

fun ActPost.canSwitchAccount(): Boolean {
    val errStringId = when {
        // 予約投稿の再編集はアカウント切り替えできない
        scheduledStatus != null ->
            R.string.cant_change_account_when_editing_scheduled_status
        // 削除して再投稿はアカウント切り替えできない
        states.redraftStatusId != null ->
            R.string.cant_change_account_when_redraft
        // 投稿の編集中はアカウント切り替えできない
        states.editStatusId != null ->
            R.string.cant_change_account_when_edit
        // 添付ファイルがあったらはアカウント切り替えできない
        attachmentList.isNotEmpty() ->
            R.string.cant_change_account_when_attachment_specified
        else -> null
    } ?: return true

    showToast(true, errStringId)
    return false
}

fun ActPost.performAccountChooser() {
    if (!canSwitchAccount()) return

    if (isMultiWindowPost) {
        accountList = SavedAccount.loadAccountList(this)
        SavedAccount.sort(accountList)
    }

    launchMain {
        pickAccount(
            bAllowPseudo = false,
            bAuto = false,
            message = getString(R.string.choose_account)
        )?.let { ai ->
            // 別タンスのアカウントに変更したならならin_reply_toの変換が必要
            if (states.inReplyToId != null && ai.apiHost != account?.apiHost) {
                startReplyConversion(ai)
            } else {
                setAccountWithVisibilityConversion(ai)
            }
        }
    }
}

internal fun ActPost.setAccountWithVisibilityConversion(a: SavedAccount) {
    selectAccount(a)
    try {
        if (TootVisibility.isVisibilitySpoilRequired(states.visibility, a.visibility)) {
            showToast(true, R.string.spoil_visibility_for_account)
            states.visibility = a.visibility
        }
    } catch (ex: Throwable) {
        log.trace(ex)
    }
    showVisibility()
    showQuotedRenote()
    updateTextCount()
}
