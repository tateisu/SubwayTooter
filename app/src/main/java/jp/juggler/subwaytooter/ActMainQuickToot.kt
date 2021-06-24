package jp.juggler.subwaytooter

import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.core.view.GravityCompat
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.api.entity.TootVisibility
import jp.juggler.subwaytooter.dialog.pickAccount
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.CompletionHelper
import jp.juggler.subwaytooter.util.PostCompleteCallback
import jp.juggler.subwaytooter.util.PostImpl
import jp.juggler.util.hideKeyboard
import jp.juggler.util.launchMain
import org.jetbrains.anko.imageResource

// 簡易投稿入力のテキスト
val ActMain.quickTootText: String
    get() = etQuickToot.text.toString()

fun ActMain.initUIQuickToot() {
    etQuickToot.typeface = ActMain.timelineFont

    if (!PrefB.bpQuickTootBar(pref)) {
        llQuickTootBar.visibility = View.GONE
    }

    if (PrefB.bpDontUseActionButtonWithQuickTootBar(pref)) {
        etQuickToot.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        etQuickToot.imeOptions = EditorInfo.IME_ACTION_NONE
        // 最後に指定する必要がある？
        etQuickToot.maxLines = 5
        etQuickToot.isVerticalScrollBarEnabled = true
        etQuickToot.isScrollbarFadingEnabled = false
    } else {
        etQuickToot.inputType = InputType.TYPE_CLASS_TEXT
        etQuickToot.imeOptions = EditorInfo.IME_ACTION_SEND
        etQuickToot.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                btnQuickToot.performClick()
                return@OnEditorActionListener true
            }
            false
        })
        // 最後に指定する必要がある？
        etQuickToot.maxLines = 1
    }

    completionHelper.attachEditText(
        llFormRoot,
        etQuickToot,
        true,
        object : CompletionHelper.Callback2 {
            override fun onTextUpdate() {}

            override fun canOpenPopup(): Boolean {
                return !drawer.isDrawerOpen(GravityCompat.START)
            }
        })

    showQuickTootVisibility()
}

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
