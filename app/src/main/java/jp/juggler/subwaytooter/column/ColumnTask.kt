package jp.juggler.subwaytooter.column

import android.content.Context
import android.os.SystemClock
import jp.juggler.subwaytooter.api.ApiPath
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.TimelineItem
import jp.juggler.subwaytooter.api.entity.TootAnnouncement.Companion.tootAnnouncement
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.api.entity.parseList
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.WordTrieTree
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.withCaption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

enum class ColumnTaskType(val marker: Char) {
    LOADING('L'),
    REFRESH_TOP('T'),
    REFRESH_BOTTOM('B'),
    GAP('G')
}

abstract class ColumnTask(
    val column: Column,
    val ctType: ColumnTaskType,
) {
    companion object {
        private val log = LogCategory("ColumnTask")
    }

    val ctStarted = AtomicBoolean(false)
    val ctClosed = AtomicBoolean(false)

    private var job: Job? = null

    val isCancelled: Boolean
        get() = job?.isCancelled ?: false

    var parser = TootParser(context, accessInfo, highlightTrie = highlightTrie)

    var listTmp: ArrayList<TimelineItem>? = null

    val context: Context
        get() = column.context

    val accessInfo: SavedAccount
        get() = column.accessInfo

    val highlightTrie: WordTrieTree?
        get() = column.highlightTrie

    val isPseudo: Boolean
        get() = accessInfo.isPseudo

    private val isMastodon: Boolean
        get() = accessInfo.isMastodon

    val isMisskey: Boolean
        get() = accessInfo.isMisskey

    val misskeyVersion: Int
        get() = accessInfo.misskeyVersion

    internal fun JsonObject.addMisskeyNotificationFilter() = addMisskeyNotificationFilter(column)
    internal fun JsonObject.addRangeMisskey(bBottom: Boolean) = addRangeMisskey(column, bBottom)

    internal val profileDirectoryPath: String
        get() {
            val order = when (val q = column.searchQuery) {
                "new" -> q
                else -> "active"
            }
            val local = !column.searchResolve
            return "${ApiPath.PATH_PROFILE_DIRECTORY}&order=$order&local=$local"
        }

    internal suspend fun getAnnouncements(
        client: TootApiClient,
        force: Boolean = false,
    ): TootApiResult? {
        // announcements is shown only mastodon home timeline, not pseudo.
        if (isMastodon && !isPseudo) {
            // force (initial loading) always reload announcements
            // other (refresh,gap) may reload announcements if last load is too old
            if (force || SystemClock.elapsedRealtime() - column.announcementUpdated >= 15 * 60000L) {
                if (force) {
                    log.d("announcements reset")
                    column.announcements = null
                    column.announcementUpdated = SystemClock.elapsedRealtime()
                }
                val (instance, _) = TootInstance.get(client)
                if (instance?.versionGE(TootInstance.VERSION_3_1_0_rc1) == true) {
                    val result = client.request("/api/v1/announcements")
                        ?: return null // cancelled.
                    when (result.response?.code ?: 0) {
                        // just skip load announcements for 4xx error if server does not support announcements.
                        in 400 until 500 -> Unit

                        else -> {
                            column.announcements =
                                parseList(result.jsonArray) { tootAnnouncement(parser, it) }
                                    .notEmpty()
                            column.announcementUpdated = SystemClock.elapsedRealtime()
                            client.publishApiProgress("announcements loaded")
                            // other errors such as network or server fails will stop column loading.
                            return result
                        }
                    }
                }
            }
        }
        return TootApiResult()
    }

    abstract suspend fun background(): TootApiResult?
    abstract suspend fun handleResult(result: TootApiResult?)

    fun cancel() {
        job?.cancel()
    }

    fun start() {
        job = launchMain {
            val result = try {
                withContext(AppDispatchers.IO) { background() }
            } catch (ignored: CancellationException) {
                null // キャンセルされたらresult==nullとする
            } catch (ex: Throwable) {
                // その他のエラー
                TootApiResult(ex.withCaption("error"))
            }
            handleResult(result)
        }
    }
}
