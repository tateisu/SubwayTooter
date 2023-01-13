package jp.juggler.subwaytooter.action

import android.content.Intent
import android.os.Build
import android.util.DisplayMetrics
import androidx.core.app.ActivityOptionsCompat
import androidx.core.app.ShareCompat
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.actmain.currentPostTarget
import jp.juggler.subwaytooter.actmain.quickPostText
import jp.juggler.subwaytooter.actpost.updateText
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootScheduled
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.api.syncStatus
import jp.juggler.subwaytooter.dialog.pickAccount
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.pref.PrefDevice
import jp.juggler.subwaytooter.pref.PrefS
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.matchHost
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.ui.isLiveActivity
import java.util.*

private val log = LogCategory("Action_OpenPost")

fun ActPost.saveWindowSize() {

    // 最大化状態で起動することはできないので、最大化状態のサイズは覚えない
    if (!isInMultiWindowMode) return

    if (Build.VERSION.SDK_INT >= 30) {
        // WindowMetrics#getBounds() the window size including all system bar areas
        windowManager?.currentWindowMetrics?.bounds?.let { bounds ->
            log.d("API=${Build.VERSION.SDK_INT}, WindowMetrics#getBounds() $bounds")
            PrefDevice.savePostWindowBound(this, bounds.width(), bounds.height())
        }
    } else {
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay?.let { display ->
            val dm = DisplayMetrics()
            display.getMetrics(dm)
            log.d("API=${Build.VERSION.SDK_INT}, displayMetrics=${dm.widthPixels},${dm.heightPixels}")
            PrefDevice.savePostWindowBound(this, dm.widthPixels, dm.heightPixels)
        }
    }
}

fun ActMain.openActPostImpl(
    accountDbId: Long,

    // 再編集する投稿。アカウントと同一のタンスであること
    redraftStatus: TootStatus? = null,

    // 編集する投稿。アカウントと同一のタンスであること
    editStatus: TootStatus? = null,

    // 返信対象の投稿。同一タンス上に同期済みであること
    replyStatus: TootStatus? = null,

    //初期テキスト
    initialText: String? = null,

    // 外部アプリから共有されたインテント
    sharedIntent: Intent? = null,

    // 返信ではなく引用トゥートを作成する
    quote: Boolean = false,

    //(Mastodon) 予約投稿の編集
    scheduledStatus: TootScheduled? = null,
) {

    val useManyWindow = PrefB.bpManyWindowPost(pref)
    val useMultiWindow = useManyWindow || PrefB.bpMultiWindowPost(pref)

    val intent = ActPost.createIntent(
        context = this,
        accountDbId = accountDbId,
        redraftStatus = redraftStatus,
        editStatus = editStatus,
        replyStatus = replyStatus,
        initialText = initialText,
        sharedIntent = sharedIntent,
        quote = quote,
        scheduledStatus = scheduledStatus,
        multiWindowMode = useMultiWindow
    )

    if (!useMultiWindow) {
        arActPost.launch(intent)
    } else {

        if (!useManyWindow) {
            ActPost.refActPost?.get()
                ?.takeIf { it.isLiveActivity }
                ?.let {
                    it.updateText(intent)
                    return
                }
        }
        // fall thru

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)

        var options = ActivityOptionsCompat.makeBasic()
        PrefDevice.loadPostWindowBound(this)
            ?.let {
                log.d("ActPost launchBounds $it")
                options = options.setLaunchBounds(it)
            }

        arActPost.launch(intent, options)
    }
}

// 投稿画面を開く。初期テキストを指定する
fun ActMain.openPost(
    initialText: String? = quickPostText,
) {
    initialText ?: return

    launchMain {
        completionHelper.closeAcctPopup()

        val account = currentPostTarget
            ?.takeIf { it.db_id != -1L && !it.isPseudo }
            ?: pickAccount(
                bAllowPseudo = false,
                bAuto = true,
                message = getString(R.string.account_picker_toot)
            )

        account?.db_id?.let { openActPostImpl(it, initialText = initialText) }
    }
}

// メンションを含むトゥートを作る
private fun ActMain.mention(
    account: SavedAccount,
    initialText: String,
) = openActPostImpl(account.db_id, initialText = initialText)

// メンションを含むトゥートを作る
fun ActMain.mention(
    account: SavedAccount,
    who: TootAccount,
) = mention(account, "@${account.getFullAcct(who).ascii} ")

// メンションを含むトゥートを作る
fun ActMain.mentionFromAnotherAccount(
    accessInfo: SavedAccount,
    who: TootAccount?,
) {
    launchMain {
        who ?: return@launchMain

        val initialText = "@${accessInfo.getFullAcct(who).ascii} "
        pickAccount(
            bAllowPseudo = false,
            bAuto = false,
            message = getString(R.string.account_picker_toot),
            accountListArg = accountListNonPseudo(who.apDomain)
        )?.let { mention(it, initialText) }
    }
}

fun ActMain.reply(
    accessInfo: SavedAccount,
    status: TootStatus,
    quote: Boolean = false,
) = openActPostImpl(
    accessInfo.db_id,
    replyStatus = status,
    quote = quote
)

private fun ActMain.replyRemote(
    accessInfo: SavedAccount,
    remoteStatusUrl: String?,
    quote: Boolean = false,
) {
    if (remoteStatusUrl.isNullOrEmpty()) return
    launchMain {
        var localStatus: TootStatus? = null
        runApiTask(
            accessInfo,
            progressPrefix = getString(R.string.progress_synchronize_toot)
        ) { client ->
            val (result, status) = client.syncStatus(accessInfo, remoteStatusUrl)
            localStatus = status
            result
        }?.let { result ->
            when (val ls = localStatus) {
                null -> showToast(true, result.error)
                else -> reply(accessInfo, ls, quote = quote)
            }
        }
    }
}

fun ActMain.replyFromAnotherAccount(
    timelineAccount: SavedAccount,
    status: TootStatus?,
) {
    status ?: return
    launchMain {
        pickAccount(
            bAllowPseudo = false,
            bAuto = false,
            message = getString(R.string.account_picker_reply),
            accountListArg = accountListNonPseudo(timelineAccount.apDomain),
        )?.let { ai ->
            if (ai.matchHost(status.readerApDomain)) {
                // アクセス元ホストが同じならステータスIDを使って返信できる
                reply(ai, status, quote = false)
            } else {
                // それ以外の場合、ステータスのURLを検索APIに投げることで返信できる
                replyRemote(ai, status.url, quote = false)
            }
        }
    }
}

fun ActMain.quoteFromAnotherAccount(
    timelineAccount: SavedAccount,
    status: TootStatus?,
) {
    status ?: return
    launchMain {
        pickAccount(
            bAllowPseudo = false,
            bAllowMisskey = true,
            bAllowMastodon = true,
            bAuto = true,
            message = getString(R.string.account_picker_quote_toot),
            accountListArg = accountListCanQuote(timelineAccount.apDomain)
        )?.let { ai ->
            if (ai.matchHost(status.readerApDomain)) {
                // アクセス元ホストが同じならステータスIDを使って返信できる
                reply(ai, status, quote = true)
            } else {
                // それ以外の場合、ステータスのURLを検索APIに投げることで返信できる
                replyRemote(ai, status.url, quote = true)
            }
        }
    }
}

fun ActMain.quoteName(who: TootAccount) {
    var sv = who.display_name
    try {
        val fmt = PrefS.spQuoteNameFormat(pref)
        if (fmt.contains("%1\$s")) {
            sv = String.format(Locale.getDefault(), fmt, sv)
        }
    } catch (ex: Throwable) {
        log.e(ex, "quoteName failed.")
    }
    openPost(sv)
}

fun ActMain.shareText(text: String?) {
    text ?: return
    ShareCompat.IntentBuilder(this)
        .setText(text)
        .setType("text/plain")
        .startChooser()
}

fun ActMain.clickReply(accessInfo: SavedAccount, status: TootStatus) {
    when {
        accessInfo.isPseudo -> replyFromAnotherAccount(accessInfo, status)
        else -> reply(accessInfo, status)
    }
}

fun ActMain.clickQuote(accessInfo: SavedAccount, status: TootStatus) {
    when {
        accessInfo.isPseudo -> quoteFromAnotherAccount(accessInfo, status)
        else -> reply(accessInfo, status, quote = true)
    }
}
