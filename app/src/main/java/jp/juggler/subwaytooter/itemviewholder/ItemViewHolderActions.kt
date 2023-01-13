package jp.juggler.subwaytooter.itemviewholder

import android.app.AlertDialog
import android.view.View
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.ActMediaViewer
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.action.*
import jp.juggler.subwaytooter.actmain.nextPosition
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.column.startGap
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.table.ContentWarning
import jp.juggler.subwaytooter.table.MediaShown
import jp.juggler.subwaytooter.util.copyToClipboard
import jp.juggler.subwaytooter.util.openCustomTab
import jp.juggler.util.data.cast
import jp.juggler.util.data.ellipsizeDot3
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.ui.vg

private val log = LogCategory("ItemViewHolderActions")

val defaultBoostedAction: ItemViewHolder.() -> Unit = {
    val pos = activity.nextPosition(column)
    val notification = (item as? TootNotification)
    boostAccount?.let { whoRef ->
        if (accessInfo.isPseudo) {
            DlgContextMenu(activity, column, whoRef, null, notification, tvContent).show()
        } else {
            activity.userProfileLocal(pos, accessInfo, whoRef.get())
        }
    }
}

fun ItemViewHolder.onClickImpl(v: View?) {
    v ?: return
    val pos = activity.nextPosition(column)
    val item = this.item
    with(activity) {
        when (v) {
            btnHideMedia, btnCardImageHide -> showHideMediaViews(false)
            btnShowMedia, btnCardImageShow -> showHideMediaViews(true)
            btnContentWarning -> toggleContentWarning()
            ivAvatar -> clickAvatar(pos)
            llBoosted -> boostedAction()
            llReply -> clickReplyInfo(pos, accessInfo, column.type, statusReply, statusShowing)

            llFollow -> clickFollowInfo(pos, accessInfo, followAccount) { whoRef ->
                DlgContextMenu(
                    this,
                    column,
                    whoRef,
                    null,
                    (item as? TootNotification),
                    tvContent
                ).show()
            }
            btnFollow -> clickFollowInfo(
                pos,
                accessInfo,
                followAccount,
                forceMenu = true
            ) { whoRef ->
                DlgContextMenu(
                    this,
                    column,
                    whoRef,
                    null,
                    (item as? TootNotification),
                    tvContent
                ).show()
            }

            btnGapHead -> column.startGap(item.cast(), isHead = true)
            btnGapTail -> column.startGap(item.cast(), isHead = false)
            btnSearchTag, llTrendTag -> clickTag(pos, item)
            btnListTL -> clickListTl(pos, accessInfo, item)
            btnListMore -> clickListMoreButton(pos, accessInfo, item)
            btnFollowRequestAccept -> clickFollowRequestAccept(
                accessInfo,
                followAccount,
                accept = true
            )
            btnFollowRequestDeny -> clickFollowRequestAccept(
                accessInfo,
                followAccount,
                accept = false
            )
            llFilter -> openFilterMenu(accessInfo, item.cast())
            ivCardImage -> clickCardImage(pos, accessInfo, statusShowing?.card)
            llConversationIcons -> clickConversation(
                pos,
                accessInfo,
                listAdapter,
                summary = item.cast()
            )

            else -> {
                ivMediaThumbnails.indexOfFirst { it == v }
                    .takeIf { it >= 0 }?.let {
                        clickMedia(it)
                        return
                    }
                tvMediaDescriptions.find { it == v }?.let {
                    clickMediaDescription(it)
                    return
                }
            }
        }
    }
}

fun ItemViewHolder.onLongClickImpl(v: View?): Boolean {
    v ?: return false

    with(activity) {

        val pos = activity.nextPosition(column)
        when (v) {
            ivAvatar ->
                clickAvatar(pos, longClick = true)

            llBoosted ->
                longClickBoostedInfo(boostAccount)

            llReply ->
                clickReplyInfo(
                    pos,
                    accessInfo,
                    column.type,
                    statusReply,
                    statusShowing,
                    longClick = true
                ) { status ->
                    DlgContextMenu(
                        this,
                        column,
                        status.accountRef,
                        status,
                        item.cast(),
                        tvContent
                    ).show()
                }

            llFollow ->
                followAccount?.let {
                    DlgContextMenu(activity, column, it, null, item.cast(), tvContent).show()
                }

            btnFollow ->
                followAccount?.get()?.let { followFromAnotherAccount(pos, accessInfo, it) }

            ivCardImage ->
                clickCardImage(pos, accessInfo, statusShowing?.card, longClick = true)

            btnSearchTag, llTrendTag ->
                longClickTag(pos, item)

            else ->
                return false
        }
    }

    return true
}

private fun ItemViewHolder.longClickBoostedInfo(who: TootAccountRef?) {
    who ?: return
    DlgContextMenu(activity, column, who, null, item.cast(), tvContent).show()
}

private fun ItemViewHolder.longClickTag(pos: Int, item: TimelineItem?): Boolean {

    when (item) {
        //					is TootGap -> column.startGap(item)
        //
        //					is TootDomainBlock -> {
        //						val domain = item.domain
        //						AlertDialog.Builder(activity)
        //							.setMessage(activity.getString(R.string.confirm_unblock_domain, domain))
        //							.setNegativeButton(R.string.cancel, null)
        //							.setPositiveButton(R.string.ok) { _, _ -> Action_Instance.blockDomain(activity, access_info, domain, false) }
        //							.show()
        //					}
        is TootTag -> activity.longClickTootTag(pos, accessInfo, item)
    }
    return true
}

private fun ItemViewHolder.clickMedia(i: Int) {
    try {
        val mediaAttachments =
            statusShowing?.media_attachments ?: (item as? TootScheduled)?.mediaAttachments
            ?: return

        when (val item = if (i < mediaAttachments.size) mediaAttachments[i] else return) {
            is TootAttachmentMSP -> {
                // マストドン検索ポータルのデータではmedia_attachmentsが簡略化されている
                // 会話の流れを表示する
                activity.conversationOtherInstance(
                    activity.nextPosition(column),
                    statusShowing
                )
            }

            is TootAttachment -> when {

                // unknownが1枚だけなら内蔵ビューアを使わずにインテントを投げる
                item.type == TootAttachmentType.Unknown && mediaAttachments.size == 1 -> {
                    // https://github.com/tateisu/SubwayTooter/pull/119
                    // メディアタイプがunknownの場合、そのほとんどはリモートから来たURLである
                    // PrefB.bpPriorLocalURL の状態に関わらずリモートURLがあればそれをブラウザで開く
                    when (val remoteUrl = item.remote_url.notEmpty()) {
                        null -> activity.openCustomTab(item)
                        else -> activity.openCustomTab(remoteUrl)
                    }
                }

                // 内蔵メディアビューアを使う
                PrefB.bpUseInternalMediaViewer() ->
                    ActMediaViewer.open(
                        activity,
                        column.showMediaDescription,
                        when (accessInfo.isMisskey) {
                            true -> ServiceType.MISSKEY
                            else -> ServiceType.MASTODON
                        },
                        mediaAttachments,
                        i
                    )

                // ブラウザで開く
                else -> activity.openCustomTab(item)
            }
        }
    } catch (ex: Throwable) {
        log.e(ex, "clickMedia failed.")
    }
}

private fun ItemViewHolder.clickMediaDescription(tv: View) {
    val desc = tv.getTag(R.id.text)
        ?.cast<CharSequence>()
        ?: return
    AlertDialog.Builder(activity)
        .setMessage(desc.toString().ellipsizeDot3(2000))
        .setPositiveButton(R.string.ok, null)
        .setNeutralButton(android.R.string.copy) { _, _ ->
            desc.copyToClipboard(activity)
        }
        .show()
}

private fun ItemViewHolder.showHideMediaViews(show: Boolean) {
    llMedia.vg(show)
    llCardImage.vg(show)
    btnShowMedia.vg(!show)
    btnCardImageShow.vg(!show)
    statusShowing?.let { MediaShown.save(it, show) }
    item.cast<TootScheduled>()?.let { MediaShown.save(it.uri, show) }
}

private fun ItemViewHolder.toggleContentWarning() {
    // トグル動作
    val show = llContents.visibility == View.GONE

    statusShowing?.let { ContentWarning.save(it, show) }
    item.cast<TootScheduled>()?.let { ContentWarning.save(it.uri, show) }

    // 1個だけ開閉するのではなく、例えば通知TLにある複数の要素をまとめて開閉するなどある
    listAdapter.notifyChange(reason = "ContentWarning onClick", reset = true)
}

private fun ItemViewHolder.clickAvatar(pos: Int, longClick: Boolean = false) {

    statusAccount?.let { whoRef ->
        when {
            longClick || accessInfo.isNA ->
                DlgContextMenu(activity, column, whoRef, null, item.cast(), tvContent).show()

            // 2018/12/26 疑似アカウントでもプロフカラムを表示する https://github.com/tootsuite/mastodon/commit/108b2139cd87321f6c0aec63ef93db85ce30bfec
            else -> activity.userProfileLocal(pos, accessInfo, whoRef.get())
        }
    }
}

private fun ItemViewHolder.clickTag(pos: Int, item: TimelineItem?) {
    with(activity) {
        when (item) {
            is TootTag -> when (item.type) {
                TootTag.TagType.Tag ->
                    tagDialog(
                        accessInfo,
                        pos,
                        item.url!!,
                        accessInfo.apiHost,
                        item.name,
                        tagInfo = item,
                    )
                TootTag.TagType.Link ->
                    openCustomTab(item.url)
            }
            is TootSearchGap -> column.startGap(item, isHead = true)
            is TootConversationSummary -> clickConversation(
                pos,
                accessInfo,
                listAdapter,
                summary = item
            )
            is TootGap -> clickTootGap(column, item)
            is TootDomainBlock -> clickDomainBlock(accessInfo, item)
            is TootScheduled -> clickScheduledToot(accessInfo, item, column)
        }
    }
}

fun ActMain.clickTootGap(column: Column, item: TootGap) {
    when {
        column.type.gapDirection(column, true) -> column.startGap(item, isHead = true)
        column.type.gapDirection(column, false) -> column.startGap(item, isHead = false)
        else -> showToast(true, "This column can't support gap reading.")
    }
}
