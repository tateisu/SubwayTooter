package jp.juggler.subwaytooter.actmain

import android.content.Intent
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.ActPost
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.column.*
import jp.juggler.subwaytooter.pref.PrefI
import jp.juggler.util.data.decodeJsonObject
import jp.juggler.util.string
import jp.juggler.util.ui.isLiveActivity

// マルチウィンドウモードでは投稿画面から直接呼ばれる
// 通常モードでは activityResultHandler 経由で呼ばれる
fun ActMain.onCompleteActPost(data: Intent) {
    if (!isLiveActivity) return

    this.postedAcct = data.string(ActPost.EXTRA_POSTED_ACCT)?.let { Acct.parse(it) }
    if (data.extras?.containsKey(ActPost.EXTRA_POSTED_STATUS_ID) == true) {
        postedStatusId = EntityId.entityId(data, ActPost.EXTRA_POSTED_STATUS_ID)
        postedReplyId = EntityId.entityId(data, ActPost.EXTRA_POSTED_REPLY_ID)
        postedRedraftId = EntityId.entityId(data, ActPost.EXTRA_POSTED_REDRAFT_ID)

        val postedStatusId = postedStatusId
        val statusJson = data.string(ActPost.KEY_EDIT_STATUS)
            ?.decodeJsonObject()
        if (statusJson != null && postedStatusId != null) {
            appState.columnList
                .filter { it.accessInfo.acct == postedAcct }
                .forEach {
                    it.replaceStatus(postedStatusId, statusJson)
                }
        }
    } else {
        postedStatusId = null
    }
    if (isStartedEx) refreshAfterPost()
}

// 簡易投稿なら直接呼ばれる
// ActPost経由なら画面復帰タイミングや onCompleteActPost から呼ばれる
fun ActMain.refreshAfterPost() {
    val postedAcct = this.postedAcct
    val postedStatusId = this.postedStatusId

    if (postedAcct != null && postedStatusId == null) {
        // 予約投稿なら予約投稿リストをリロードする
        appState.columnList.forEach { column ->
            if (column.type == ColumnType.SCHEDULED_STATUS &&
                column.accessInfo.acct == postedAcct
            ) {
                column.startLoading(ColumnLoadReason.RefreshAfterPost)
            }
        }
    } else if (postedAcct != null && postedStatusId != null) {
        val postedRedraftId = this.postedRedraftId
        if (postedRedraftId != null) {
            val host = postedAcct.host
            if (host != null) {
                appState.columnList.forEach {
                    it.onStatusRemoved(host, postedRedraftId)
                }
            }
            this.postedRedraftId = null
        }

        val refreshAfterToot = PrefI.ipRefreshAfterToot.value
        if (refreshAfterToot != PrefI.RAT_DONT_REFRESH) {
            appState.columnList
                .filter { it.accessInfo.acct == postedAcct }
                .forEach {
                    it.startRefreshForPost(
                        refreshAfterToot,
                        postedStatusId,
                        postedReplyId
                    )
                }
        }
    }
    this.postedAcct = null
    this.postedStatusId = null
}
