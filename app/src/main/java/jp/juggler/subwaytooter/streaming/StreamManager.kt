package jp.juggler.subwaytooter.streaming

import jp.juggler.subwaytooter.AppState
import jp.juggler.subwaytooter.Column
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.api.TootApiCallback
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.table.HighlightWord
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.LogCategory
import jp.juggler.util.launchDefault
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.set


class StreamManager(val appState: AppState) {
    companion object {
        private val log = LogCategory("StreamManager")

        val traceDelivery = "false".toBoolean()

        // 画面ONの間は定期的に状況を更新する
        const val updateInterval = 5000L

    }

    val context = appState.context
    private val queue = Channel<suspend () -> Unit>(capacity = Channel.UNLIMITED)
    private val handler = appState.handler

    private val isScreenOn = AtomicBoolean(false)
    private val acctGroups = ConcurrentHashMap<Acct, StreamGroupAcct>()

    val client = TootApiClient(
        appState.context,
        callback = object : TootApiCallback {
            override val isApiCancelled = false
        }
    )

    private val updateConnection: suspend () -> Unit = {

        val isScreenOn = isScreenOn.get()

        val newMap = HashMap<Acct, StreamGroupAcct>()
        val errorAcct = HashSet<Acct>()

        suspend fun prepareAcctGroup(accessInfo: SavedAccount): StreamGroupAcct? {
            val acct = accessInfo.acct
            if (errorAcct.contains(acct)) return null
            var acctGroup = newMap[acct]
            if (acctGroup == null) {
                var (ti, ri) = TootInstance.getEx(client, account = accessInfo)
                if (ti == null) {
                    log.d("can't get server info. ${ri?.error}")
                    val tiOld = acctGroups[acct]?.ti
                    if (tiOld == null) {
                        errorAcct.add(acct)
                        return null
                    }
                    ti = tiOld
                }
                acctGroup = StreamGroupAcct(this, accessInfo, ti)
                newMap[acct] = acctGroup
            }
            return acctGroup
        }

        if (isScreenOn && !Pref.bpDontUseStreaming(appState.pref)) {
            for (column in appState.columnList) {
                val accessInfo = column.access_info
                if (column.is_dispose.get() || column.dont_streaming || accessInfo.isNA) continue

                val acctGroup = prepareAcctGroup(accessInfo) ?: continue

                val streamSpec = column.getStreamDestination()
                if (streamSpec != null)
                    acctGroup.addSpec(streamSpec)
            }
        }

        if (newMap.size != acctGroups.size) {
            log.d("updateConnection: acctGroups.size changed. ${acctGroups.size} => ${newMap.size}")
        }


        // 新構成にないサーバは破棄する
        acctGroups.entries.toList().forEach {
            if (!newMap.containsKey(it.key)) {
                it.value.dispose()
                acctGroups.remove(it.key)
            }
        }

        // 追加.変更されたサーバをマージする
        newMap.entries.forEach {
            when (val current = acctGroups[it.key]) {
                null -> acctGroups[it.key] = it.value.apply { initialize() }
                else -> current.merge(it.value)
            }
        }

        // ハイライトツリーを読み直す
        val highlight_trie = HighlightWord.nameSet

        acctGroups.values.forEach {
            // パーサーを更新する
            it.parser.highlightTrie = highlight_trie

            // 接続を更新する
            it.updateConnection()
        }
    }

    private val procInterval = object : Runnable {
        override fun run() {
            enqueue(updateConnection)
            handler.removeCallbacks(this)
            if (isScreenOn.get()) handler.postDelayed(this, updateInterval)
        }
    }

    //////////////////////////////////////////////////
    // methods

    fun enqueue(block: suspend () -> Unit) = launchDefault { queue.send(block) }

    // UIスレッドから呼ばれる
    fun updateStreamingColumns() {
        handler.post(procInterval)
    }

    // 画面表示開始時に呼ばれる
    fun onScreenStart() {
        isScreenOn.set(true)
        handler.post(procInterval)
    }

    // 画面表示終了時に呼ばれる
    fun onScreenStop() {
        isScreenOn.set(false)
        handler.post(procInterval)
    }

    // カラムヘッダの表示更新から、インジケータを取得するために呼ばれる
    // UIスレッドから呼ばれる
    // returns StreamStatus.Missing if account is NA or all columns are non-streaming.
    fun getStreamStatus(column: Column): StreamStatus =
        acctGroups[column.access_info.acct]?.getStreamStatus(column.internalId)
            ?: StreamStatus.Missing

    // returns null if account is NA or all columns are non-streaming.
    fun getConnection(column: Column): StreamConnection? =
        acctGroups[column.access_info.acct]?.getConnection(column.internalId)

    ////////////////////////////////////////////////////////////////

    init {
        launchDefault {
            while (true) {
                try {
                    queue.receive().invoke()
                } catch (_: ClosedReceiveChannelException) {
                    // 発生しない
                } catch (ex: Throwable) {
                    log.trace(ex, "error.")
                }
            }
        }
    }
}

