package jp.juggler.subwaytooter.actmain

import android.content.Intent
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.ActPost
import jp.juggler.subwaytooter.PrefI
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.column.onStatusRemoved
import jp.juggler.subwaytooter.column.startLoading
import jp.juggler.subwaytooter.column.startRefreshForPost
import jp.juggler.util.isLiveActivity

// マルチウィンドウモードでは投稿画面から直接呼ばれる
// 通常モードでは activityResultHandler 経由で呼ばれる
fun ActMain.onCompleteActPost(data: Intent) {
    if (!isLiveActivity) return
    postedAcct = data.getStringExtra(ActPost.EXTRA_POSTED_ACCT)?.let { Acct.parse(it) }
    if (data.extras?.containsKey(ActPost.EXTRA_POSTED_STATUS_ID) == true) {
        postedStatusId = EntityId.from(data, ActPost.EXTRA_POSTED_STATUS_ID)
        postedReplyId = EntityId.from(data, ActPost.EXTRA_POSTED_REPLY_ID)
        postedRedraftId = EntityId.from(data, ActPost.EXTRA_POSTED_REDRAFT_ID)
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
                column.startLoading()
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

        val refreshAfterToot = PrefI.ipRefreshAfterToot(pref)
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
