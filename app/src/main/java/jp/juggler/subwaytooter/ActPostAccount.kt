package jp.juggler.subwaytooter

import jp.juggler.subwaytooter.api.entity.TootVisibility
import jp.juggler.subwaytooter.dialog.pickAccount
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.*
import org.jetbrains.anko.textColor

fun ActPost.selectAccount(a: SavedAccount?) {
    this.account = a

    completionHelper.setInstance(a)

    if (a == null) {
        btnAccount.text = getString(R.string.not_selected)
        btnAccount.setTextColor(attrColor(android.R.attr.textColorPrimary))
        btnAccount.setBackgroundResource(R.drawable.btn_bg_transparent_round6dp)
    } else {

        // 先読みしてキャッシュに保持しておく
        App1.custom_emoji_lister.getList(a) {
            // 何もしない
        }

        val ac = AcctColor.load(a)
        btnAccount.text = ac.nickname

        if (AcctColor.hasColorBackground(ac)) {
            btnAccount.background =
                getAdaptiveRippleDrawableRound(this, ac.color_bg, ac.color_fg)
        } else {
            btnAccount.setBackgroundResource(R.drawable.btn_bg_transparent_round6dp)
        }

        btnAccount.textColor = ac.color_fg.notZero()
            ?: attrColor(android.R.attr.textColorPrimary)
    }
    updateTextCount()
    updateFeaturedTags()
}

fun ActPost.canSwitchAccount(): Boolean {

    if (scheduledStatus != null) {
        // 予約投稿の再編集ではアカウントを切り替えられない
        showToast(false, R.string.cant_change_account_when_editing_scheduled_status)
        return false
    }

    if (attachmentList.isNotEmpty()) {
        // 添付ファイルがあったら確認の上添付ファイルを捨てないと切り替えられない
        showToast(false, R.string.cant_change_account_when_attachment_specified)
        return false
    }

    if (states.redraftStatusId != null) {
        // 添付ファイルがあったら確認の上添付ファイルを捨てないと切り替えられない
        showToast(false, R.string.cant_change_account_when_redraft)
        return false
    }

    return true
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
        ActPost.log.trace(ex)
    }
    showVisibility()
    showQuotedRenote()
    updateTextCount()
}
