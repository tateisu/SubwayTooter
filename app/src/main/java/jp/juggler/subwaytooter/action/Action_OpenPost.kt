package jp.juggler.subwaytooter.action

import android.annotation.TargetApi
import android.content.Intent
import android.os.Build
import android.util.DisplayMetrics
import androidx.core.app.ActivityOptionsCompat
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootScheduled
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.api.syncStatus
import jp.juggler.subwaytooter.dialog.pickAccount
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.matchHost
import jp.juggler.util.LogCategory
import jp.juggler.util.isLiveActivity
import jp.juggler.util.launchMain
import jp.juggler.util.showToast

private val log = LogCategory("Action_OpenPost")

@TargetApi(24)
fun ActPost.saveWindowSize() {

    // 最大化状態で起動することはできないので、最大化状態のサイズは覚えない
    if (!isInMultiWindowMode) return

    if (Build.VERSION.SDK_INT >= 30) {
        // WindowMetrics#getBounds() the window size including all system bar areas
        windowManager?.currentWindowMetrics?.bounds?.let { bounds ->
            ActPost.log.d("API=${Build.VERSION.SDK_INT}, WindowMetrics#getBounds() $bounds")
            PrefDevice.savePostWindowBound(this, bounds.width(), bounds.height())
        }
    } else {
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay?.let { display ->
            val dm = DisplayMetrics()
            display.getMetrics(dm)
            ActPost.log.d("API=${Build.VERSION.SDK_INT}, displayMetrics=${dm.widthPixels},${dm.heightPixels}")
            PrefDevice.savePostWindowBound(this, dm.widthPixels, dm.heightPixels)
        }
    }
}

fun ActMain.openActPostImpl(
    account_db_id: Long,

    // 再編集する投稿。アカウントと同一のタンスであること
    redraft_status: TootStatus? = null,

    // 返信対象の投稿。同一タンス上に同期済みであること
    reply_status: TootStatus? = null,

    //初期テキスト
    initial_text: String? = null,

    // 外部アプリから共有されたインテント
    sent_intent: Intent? = null,

    // 返信ではなく引用トゥートを作成する
    quote: Boolean = false,

    //(Mastodon) 予約投稿の編集
    scheduledStatus: TootScheduled? = null
) {

    val useManyWindow = Pref.bpManyWindowPost(pref)
    val useMultiWindow = useManyWindow || Pref.bpMultiWindowPost(pref)

    val intent = ActPost.createIntent(
        activity = this,
        account_db_id = account_db_id,
        redraft_status = redraft_status,
        reply_status = reply_status,
        initial_text = initial_text,
        sent_intent = sent_intent,
        quote = quote,
        scheduledStatus = scheduledStatus,
        multiWindowMode = useMultiWindow
    )

    if (!useMultiWindow) {
        arActPost.launch(intent)
    } else {

        if (!useManyWindow)
            ActPost.refActPost?.get()
                ?.takeIf { it.isLiveActivity }
                ?.let {
                    it.updateText(intent)
                    return
                }
        // fall thru

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)

        var options = ActivityOptionsCompat.makeBasic()
        PrefDevice.loadPostWindowBound(this)
            ?.let {
                log.d("ActPost launchBounds ${it}")
                options = options.setLaunchBounds(it)
            }

        arActPost.launch(intent, options)
    }
}

// 投稿画面を開く。初期テキストを指定する
fun ActMain.openPost(
    initial_text: String? = quickTootText
) {
    launchMain {
        post_helper.closeAcctPopup()

        val account = currentPostTarget
            ?.takeIf { it.db_id != -1L && !it.isPseudo }
            ?: pickAccount(
                bAllowPseudo = false,
                bAuto = true,
                message = getString(R.string.account_picker_toot)
            )

        account?.db_id?.let { openActPostImpl(it, initial_text = initial_text) }
    }
}

// メンションを含むトゥートを作る
private fun ActMain.mention(
    account: SavedAccount,
    initial_text: String
) = openActPostImpl(account.db_id, initial_text = initial_text)

// メンションを含むトゥートを作る
fun ActMain.mention(
    account: SavedAccount,
    who: TootAccount
) = mention(account, "@${account.getFullAcct(who).ascii} ")

// メンションを含むトゥートを作る
fun ActMain.mentionFromAnotherAccount(
    access_info: SavedAccount,
    who: TootAccount?
) {
    launchMain {
        who ?: return@launchMain

        val initial_text = "@${access_info.getFullAcct(who).ascii} "
        pickAccount(
            bAllowPseudo = false,
            bAuto = false,
            message = getString(R.string.account_picker_toot),
            accountListArg = accountListNonPseudo(who.apDomain)
        )?.let { mention(it, initial_text) }
    }
}

fun ActMain.reply(
    access_info: SavedAccount,
    status: TootStatus,
    quote: Boolean = false
) = openActPostImpl(
    access_info.db_id,
    reply_status = status,
    quote = quote
)

private fun ActMain.replyRemote(
    access_info: SavedAccount,
    remote_status_url: String?,
    quote: Boolean = false
) {
    if (remote_status_url.isNullOrEmpty()) return
    launchMain {
        var local_status: TootStatus? = null
        runApiTask(
            access_info,
            progressPrefix = getString(R.string.progress_synchronize_toot)
        ) { client ->
            val (result, status) = client.syncStatus(access_info, remote_status_url)
            local_status = status
            result
        }?.let { result ->
            when (val ls = local_status) {
                null -> showToast(true, result.error)
                else -> reply(access_info, ls, quote = quote)
            }
        }
    }

}

fun ActMain.replyFromAnotherAccount(
    timeline_account: SavedAccount,
    status: TootStatus?
) {
    status ?: return
    launchMain {
        pickAccount(
            bAllowPseudo = false,
            bAuto = false,
            message = getString(R.string.account_picker_reply),
            accountListArg = accountListNonPseudo(timeline_account.apDomain),
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
    timeline_account: SavedAccount,
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
            accountListArg = accountListCanQuote(timeline_account.apDomain)
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
