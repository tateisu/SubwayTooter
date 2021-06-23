package jp.juggler.subwaytooter

import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.api.entity.TootVisibility
import jp.juggler.subwaytooter.dialog.pickAccount
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.PostCompleteCallback
import jp.juggler.subwaytooter.util.PostImpl
import jp.juggler.util.hideKeyboard
import jp.juggler.util.launchMain
import org.jetbrains.anko.imageResource

// 簡易投稿入力のテキスト
val ActMain.quickTootText: String
    get() = etQuickToot.text.toString()

fun ActMain.showQuickTootVisibility() {
    btnQuickTootMenu.imageResource =
        when (val resId = Styler.getVisibilityIconId(false, quickTootVisibility)) {
            R.drawable.ic_question -> R.drawable.ic_description
            else -> resId
        }
}

fun ActMain.performQuickTootMenu() {
    dlgQuickTootMenu.toggle()
}

fun ActMain.performQuickPost(account: SavedAccount?) {
    if (account == null) {
        val a = if (tabletViews != null && !PrefB.bpQuickTootOmitAccountSelection(pref)) {
            // タブレットモードでオプションが無効なら
            // 簡易投稿は常にアカウント選択する
            null
        } else {
            currentPostTarget
        }

        if (a != null && !a.isPseudo) {
            performQuickPost(a)
        } else {
            // アカウントを選択してやり直し
            launchMain {
                pickAccount(
                    bAllowPseudo = false,
                    bAuto = true,
                    message = getString(R.string.account_picker_toot)
                )?.let { performQuickPost(it) }
            }
        }
        return
    }

    etQuickToot.hideKeyboard()

    PostImpl(
        activity = this,
        account = account,
        content = etQuickToot.text.toString().trim { it <= ' ' },
        spoilerText = null,
        visibilityArg = when (quickTootVisibility) {
            TootVisibility.AccountSetting -> account.visibility
            else -> quickTootVisibility
        },
        bNSFW = false,
        inReplyToId = null,
        attachmentListArg = null,
        enqueteItemsArg = null,
        pollType = null,
        pollExpireSeconds = 0,
        pollHideTotals = false,
        pollMultipleChoice = false,
        scheduledAt = 0L,
        scheduledId = null,
        redraftStatusId = null,
        emojiMapCustom = App1.custom_emoji_lister.getMap(account),
        useQuoteToot = false,
        callback = object : PostCompleteCallback {
            override fun onScheduledPostComplete(targetAccount: SavedAccount) {}
            override fun onPostComplete(targetAccount: SavedAccount, status: TootStatus) {
                etQuickToot.setText("")
                postedAcct = targetAccount.acct
                postedStatusId = status.id
                postedReplyId = status.in_reply_to_id
                postedRedraftId = null
                refreshAfterPost()
            }
        }
    ).run()
}
