package jp.juggler.subwaytooter.actmain

import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.core.view.GravityCompat
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.actpost.CompletionHelper
import jp.juggler.subwaytooter.api.entity.TootVisibility
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.dialog.pickAccount
import jp.juggler.subwaytooter.getVisibilityIconId
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.PostImpl
import jp.juggler.subwaytooter.util.PostResult
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.ui.hideKeyboard
import org.jetbrains.anko.imageResource

// 簡易投稿入力のテキスト
val ActMain.quickPostText: String
    get() = etQuickPost.text.toString()

fun ActMain.initUIQuickPost() {
    etQuickPost.typeface = ActMain.timelineFont

    if (!PrefB.bpQuickPostBar.value) {
        llQuickPostBar.visibility = View.GONE
    }

    if (PrefB.bpDontUseActionButtonWithQuickPostBar.value) {
        etQuickPost.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        etQuickPost.imeOptions = EditorInfo.IME_ACTION_NONE
        // 最後に指定する必要がある？
        etQuickPost.maxLines = 5
        etQuickPost.isVerticalScrollBarEnabled = true
        etQuickPost.isScrollbarFadingEnabled = false
    } else {
        etQuickPost.inputType = InputType.TYPE_CLASS_TEXT
        etQuickPost.imeOptions = EditorInfo.IME_ACTION_SEND
        etQuickPost.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                btnQuickToot.performClick()
                return@OnEditorActionListener true
            }
            false
        })
        // 最後に指定する必要がある？
        etQuickPost.maxLines = 1
    }

    completionHelper.attachEditText(
        llFormRoot,
        etQuickPost,
        true,
        object : CompletionHelper.Callback2 {
            override fun onTextUpdate() {}

            override fun canOpenPopup(): Boolean {
                return !drawer.isDrawerOpen(GravityCompat.START)
            }
        })

    showQuickPostVisibility()
}

fun ActMain.showQuickPostVisibility() {
    btnQuickPostMenu.imageResource =
        when (val resId = quickPostVisibility.getVisibilityIconId(false)) {
            R.drawable.ic_question -> R.drawable.ic_description
            else -> resId
        }
}

fun ActMain.toggleQuickPostMenu() {
    dlgQuickTootMenu.toggle()
}

fun ActMain.quickPostAccount(): SavedAccount? =
    when {
        // タブレットモードでオプションが無効なら
        // 簡易投稿は常にアカウント選択する
        tabletViews != null && !PrefB.bpQuickTootOmitAccountSelection.value -> {
            null
        }
        else -> currentPostTarget
    }

fun ActMain.quickPostAccountDialog(
    title: String = getString(R.string.account_picker_toot),
    block: (SavedAccount) -> Unit,
) {
    val a = quickPostAccount()
    when {
        a != null && !a.isPseudo -> block(a)
        else -> {
            launchAndShowError {
                pickAccount(
                    bAllowPseudo = false,
                    bAuto = true,
                    message = title,
                )?.let { block(it) }
            }
        }
    }
}

fun ActMain.openProfileQuickPostAccount(account: SavedAccount) {
    account.loginAccount?.id?.let {
        addColumn(defaultInsertPosition, account, ColumnType.PROFILE, params = arrayOf(it))
    }
}

fun ActMain.performQuickPost(account: SavedAccount) {

    etQuickPost.hideKeyboard()

    launchAndShowError {
        val postResult = PostImpl(
            activity = this@performQuickPost,
            account = account,
            content = etQuickPost.text.toString().trim { it <= ' ' },
            spoilerText = null,
            visibilityArg = when (quickPostVisibility) {
                TootVisibility.AccountSetting -> account.visibility
                else -> quickPostVisibility
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
            editStatusId = null,
            emojiMapCustom = App1.custom_emoji_lister.getMapNonBlocking(account),
            useQuoteToot = false,
            lang = account.lang,
        ).runSuspend()

        if (postResult is PostResult.Normal) {
            etQuickPost.setText("")
            postedAcct = postResult.targetAccount.acct
            postedStatusId = postResult.status.id
            postedReplyId = postResult.status.in_reply_to_id
            postedRedraftId = null
            refreshAfterPost()
        }
    }
}
