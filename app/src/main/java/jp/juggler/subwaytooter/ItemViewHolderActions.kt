package jp.juggler.subwaytooter

import android.view.View
import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.action.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.dialog.DlgConfirm
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.ContentWarning
import jp.juggler.subwaytooter.table.MediaShown
import jp.juggler.subwaytooter.util.openCustomTab
import jp.juggler.util.encodePercent
import jp.juggler.util.notEmpty
import jp.juggler.util.showToast

val defaultBoostedAction: ItemViewHolder.() -> Unit = {
    val pos = activity.nextPosition(column)
    val notification = (item as? TootNotification)
    boost_account?.let { whoRef ->
        if (access_info.isPseudo) {
            DlgContextMenu(activity, column, whoRef, null, notification, tvContent).show()
        } else {
            activity.userProfileLocal(pos, access_info, whoRef.get())
        }
    }
}

fun ItemViewHolder.openConversationSummary() {
    val cs = item as? TootConversationSummary ?: return

    if( activity.conversationUnreadClear(access_info, cs) ){
        list_adapter.notifyChange(
            reason = "ConversationSummary reset unread",
            reset = true
        )
    }
    activity.conversation(
        activity.nextPosition(column),
        access_info,
        cs.last_status
    )
}

fun ItemViewHolder.openFilterMenu(item: TootFilter) {
    val ad = ActionsDialog()
    ad.addAction(activity.getString(R.string.edit)) {
        ActKeywordFilter.open(activity, access_info, item.id)
    }
    ad.addAction(activity.getString(R.string.delete)) {
        activity.filterDelete( access_info, item)
    }
    ad.show(activity, activity.getString(R.string.filter_of, item.phrase))
}

fun ItemViewHolder.onClickImpl(v: View?) {
    v ?: return

    val pos = activity.nextPosition(column)
    val item = this.item
    val notification = (item as? TootNotification)
    when (v) {

        btnHideMedia, btnCardImageHide -> {
            fun hideViews() {
                llMedia.visibility = View.GONE
                btnShowMedia.visibility = View.VISIBLE
                llCardImage.visibility = View.GONE
                btnCardImageShow.visibility = View.VISIBLE
            }
            status_showing?.let { status ->
                MediaShown.save(status, false)
                hideViews()
            }
            if (item is TootScheduled) {
                MediaShown.save(item.uri, false)
                hideViews()
            }
        }

        btnShowMedia, btnCardImageShow -> {
            fun showViews() {
                llMedia.visibility = View.VISIBLE
                btnShowMedia.visibility = View.GONE
                llCardImage.visibility = View.VISIBLE
                btnCardImageShow.visibility = View.GONE
            }
            status_showing?.let { status ->
                MediaShown.save(status, true)
                showViews()
            }
            if (item is TootScheduled) {
                MediaShown.save(item.uri, true)
                showViews()
            }
        }

        ivMedia1 -> clickMedia(0)
        ivMedia2 -> clickMedia(1)
        ivMedia3 -> clickMedia(2)
        ivMedia4 -> clickMedia(3)

        btnContentWarning -> {
            status_showing?.let { status ->
                val new_shown = llContents.visibility == View.GONE
                ContentWarning.save(status, new_shown)

                // 1個だけ開閉するのではなく、例えば通知TLにある複数の要素をまとめて開閉するなどある
                list_adapter.notifyChange(reason = "ContentWarning onClick", reset = true)

            }
            if (item is TootScheduled) {
                val new_shown = llContents.visibility == View.GONE
                ContentWarning.save(item.uri, new_shown)

                // 1個だけ開閉するのではなく、例えば通知TLにある複数の要素をまとめて開閉するなどある
                list_adapter.notifyChange(reason = "ContentWarning onClick", reset = true)
            }
        }

        ivThumbnail -> status_account?.let { whoRef ->
            when {
                access_info.isNA -> DlgContextMenu(
                    activity,
                    column,
                    whoRef,
                    null,
                    notification,
                    tvContent
                ).show()

                // 2018/12/26 疑似アカウントでもプロフカラムを表示する https://github.com/tootsuite/mastodon/commit/108b2139cd87321f6c0aec63ef93db85ce30bfec

                else -> activity.userProfileLocal(

                    pos,
                    access_info,
                    whoRef.get()
                )
            }
        }

        llBoosted -> boostedAction()

        llReply -> {
            val s = status_reply

            when {
                s != null -> activity.conversation(pos, access_info, s)

                // tootsearchは返信元のIDを取得するのにひと手間必要
                column.type == ColumnType.SEARCH_TS ||
                    column.type == ColumnType.SEARCH_NOTESTOCK ->
                    activity.conversationFromTootsearch(pos, status_showing)

                else -> {
                    val id = status_showing?.in_reply_to_id
                    if (id != null) {
                        activity.conversationLocal(pos, access_info, id)
                    }
                }
            }
        }

        llFollow -> follow_account?.let { whoRef ->
            if (access_info.isPseudo) {
                DlgContextMenu(activity, column, whoRef, null, notification, tvContent).show()
            } else {
                activity.userProfileLocal(pos, access_info, whoRef.get())
            }
        }

        btnFollow -> follow_account?.let { who ->
            DlgContextMenu(activity, column, who, null, notification, tvContent).show()
        }

        btnGapHead -> when (item) {
            is TootGap -> column.startGap(item, isHead = true)
        }

        btnGapTail -> when (item) {
            is TootGap -> column.startGap(item, isHead = false)
        }

        btnSearchTag, llTrendTag -> when (item) {

            is TootConversationSummary -> openConversationSummary()

            is TootGap -> when {
                column.type.gapDirection(column, true) ->
                    column.startGap(item, isHead = true)

                column.type.gapDirection(column, false) ->
                    column.startGap(item, isHead = false)

                else ->
                    activity.showToast(true, "This column can't support gap reading.")
            }

            is TootSearchGap -> column.startGap(item, isHead = true)

            is TootDomainBlock -> {
                AlertDialog.Builder(activity)
                    .setMessage(
                        activity.getString(
                            R.string.confirm_unblock_domain,
                            item.domain.pretty
                        )
                    )
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        activity.domainBlock(
                            access_info,
                            item.domain,
                            bBlock = false
                        )
                    }
                    .show()
            }

            is TootTag -> {
                activity.tagTimeline(
                    activity.nextPosition(column),
                    access_info,
                    item.name // #を含まない
                )
            }

            is TootScheduled -> {
                ActionsDialog()
                    .addAction(activity.getString(R.string.edit)) {
                        activity.scheduledPostEdit(access_info, item)
                    }
                    .addAction(activity.getString(R.string.delete)) {
                        activity.scheduledPostDelete(access_info, item) {
                            column.onScheduleDeleted(item)
                            activity.showToast(false, R.string.scheduled_post_deleted)
                        }
                    }
                    .show(activity)
            }
        }

        btnListTL -> if (item is TootList) {
            activity.addColumn(pos, access_info, ColumnType.LIST_TL, item.id)
        } else if (item is MisskeyAntenna) {
            // TODO
            activity.addColumn(pos, access_info, ColumnType.MISSKEY_ANTENNA_TL, item.id)
        }

        btnListMore -> when (item) {
            is TootList -> {
                ActionsDialog()
                    .addAction(activity.getString(R.string.list_timeline)) {
                        activity.addColumn(pos, access_info, ColumnType.LIST_TL, item.id)
                    }
                    .addAction(activity.getString(R.string.list_member)) {
                        activity.addColumn(
                            false,
                            pos,
                            access_info,
                            ColumnType.LIST_MEMBER,
                            item.id
                        )
                    }
                    .addAction(activity.getString(R.string.rename)) {
                        activity.listRename( access_info, item)
                    }
                    .addAction(activity.getString(R.string.delete)) {
                        activity.listDelete( access_info, item)
                    }
                    .show(activity, item.title)
            }

            is MisskeyAntenna -> {
                // TODO
            }
        }

        btnFollowRequestAccept -> follow_account?.let { whoRef ->
            val who = whoRef.get()
            DlgConfirm.openSimple(
                activity,
                activity.getString(
                    R.string.follow_accept_confirm,
                    AcctColor.getNickname(access_info, who)
                )
            ) {
                activity.followRequestAuthorize(access_info, whoRef, true)
            }
        }

        btnFollowRequestDeny -> follow_account?.let { whoRef ->
            val who = whoRef.get()
            DlgConfirm.openSimple(
                activity,
                activity.getString(
                    R.string.follow_deny_confirm,
                    AcctColor.getNickname(access_info, who)
                )
            ) {
                activity.followRequestAuthorize(access_info, whoRef, false)
            }
        }

        llFilter -> if (item is TootFilter) {
            openFilterMenu(item)
        }

        ivCardImage -> status_showing?.card?.let { card ->
            val originalStatus = card.originalStatus
            if (originalStatus != null) {
                activity.conversation(
                    activity.nextPosition(column),
                    access_info,
                    originalStatus
                )
            } else {
                val url = card.url
                if (url?.isNotEmpty() == true) {
                    openCustomTab(
                        activity,
                        pos,
                        url,
                        accessInfo = access_info
                    )
                }
            }
        }

        llConversationIcons -> openConversationSummary()
    }
}

fun ItemViewHolder.onLongClickImpl(v: View?): Boolean {
    v ?: return false

    val notification = (item as? TootNotification)

    when (v) {

        ivThumbnail -> {
            status_account?.let { who ->
                DlgContextMenu(
                    activity,
                    column,
                    who,
                    null,
                    notification,
                    tvContent
                ).show()
            }
            return true
        }

        llBoosted -> {
            boost_account?.let { who ->
                DlgContextMenu(
                    activity,
                    column,
                    who,
                    null,
                    notification,
                    tvContent
                ).show()
            }
            return true
        }

        llReply -> {
            val s = status_reply
            when {

                // 返信元のstatusがあるならコンテキストメニュー
                s != null -> DlgContextMenu(
                    activity,
                    column,
                    s.accountRef,
                    s,
                    notification,
                    tvContent
                ).show()

                // それ以外はコンテキストメニューではなく会話を開く

                // tootsearchは返信元のIDを取得するのにひと手間必要
                column.type == ColumnType.SEARCH_TS ||
                    column.type == ColumnType.SEARCH_NOTESTOCK ->
                    activity.conversationFromTootsearch(
                        activity.nextPosition(column),
                        status_showing
                    )

                else -> {
                    val id = status_showing?.in_reply_to_id
                    if (id != null) {
                        activity.conversationLocal(
                            activity.nextPosition(column),
                            access_info,
                            id
                        )
                    }
                }
            }
        }

        llFollow -> {
            follow_account?.let { whoRef ->
                DlgContextMenu(
                    activity,
                    column,
                    whoRef,
                    null,
                    notification
                ).show()
            }
            return true
        }

        btnFollow -> {
            follow_account?.let { whoRef ->
                activity.followFromAnotherAccount(
                    activity.nextPosition(column),
                    access_info,
                    whoRef.get()
                )
            }
            return true
        }

        ivCardImage -> activity.conversationOtherInstance(
            activity.nextPosition(column),
            status_showing?.card?.originalStatus
        )

        btnSearchTag, llTrendTag -> {
            when (val item = this.item) {
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

                is TootTag -> {
                    // search_tag は#を含まない
                    val tagEncoded = item.name.encodePercent()
                    val url = "https://${access_info.apiHost.ascii}/tags/$tagEncoded"
                    activity.tagTimelineFromAccount(
                        pos = activity.nextPosition(column),
                        url = url,
                        host = access_info.apiHost,
                        tag_without_sharp = item.name
                    )
                }

            }
            return true
        }
    }

    return false
}

fun ItemViewHolder.clickMedia(i: Int) {
    try {
        val media_attachments =
            status_showing?.media_attachments ?: (item as? TootScheduled)?.media_attachments
            ?: return

        when (val item = if (i < media_attachments.size) media_attachments[i] else return) {
            is TootAttachmentMSP -> {
                // マストドン検索ポータルのデータではmedia_attachmentsが簡略化されている
                // 会話の流れを表示する
                activity.conversationOtherInstance(
                    activity.nextPosition(column),
                    status_showing
                )
            }

            is TootAttachment -> when {

                // unknownが1枚だけなら内蔵ビューアを使わずにインテントを投げる
                item.type == TootAttachmentType.Unknown && media_attachments.size == 1 -> {
                    // https://github.com/tateisu/SubwayTooter/pull/119
                    // メディアタイプがunknownの場合、そのほとんどはリモートから来たURLである
                    // Pref.bpPriorLocalURL の状態に関わらずリモートURLがあればそれをブラウザで開く
                    when (val remoteUrl = item.remote_url.notEmpty()) {
                        null -> activity.openCustomTab(item)
                        else -> activity.openCustomTab(remoteUrl)
                    }
                }

                // 内蔵メディアビューアを使う
                Pref.bpUseInternalMediaViewer(App1.pref) ->
                    ActMediaViewer.open(
                        activity,
                        when (access_info.isMisskey) {
                            true -> ServiceType.MISSKEY
                            else -> ServiceType.MASTODON
                        },
                        media_attachments,
                        i
                    )

                // ブラウザで開く
                else -> activity.openCustomTab(item)
            }
        }
    } catch (ex: Throwable) {
        ItemViewHolder.log.trace(ex)
    }
}
