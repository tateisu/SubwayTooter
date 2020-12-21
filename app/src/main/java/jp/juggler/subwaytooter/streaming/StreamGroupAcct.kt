package jp.juggler.subwaytooter.streaming

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.table.SavedAccount
import java.util.concurrent.ConcurrentHashMap


// ストリーミング接続をacct単位でグルーピングする
class StreamGroupAcct(
    private val manager: StreamManager,
    val accessInfo: SavedAccount,
    var ti: TootInstance
) {
    val parser: TootParser = TootParser(manager.appState.context, linkHelper = accessInfo)

    // map key is Column.internalId
    val groups = ConcurrentHashMap<String, StreamGroupKey>()

    private var specs = ConcurrentHashMap<Int, StreamSpec>()

    //val specs = ConcurrentHashMap<Int, StreamSpec>()
    private val connections = ConcurrentHashMap<String, StreamConnection>()

    private var mergedConnection: StreamConnection? = null

    fun addSpec(spec: StreamSpec) {
        val key = spec.streamKey
        var group = groups[key]
        if (group == null) {
            group = StreamGroupKey(streamKey = key)
            groups[key] = group
        }
        group[spec.columnInternalId] = spec
    }

    fun merge(newServer: StreamGroupAcct) {

        // 新スペックの値をコピー
        this.ti = newServer.ti

        newServer.groups.entries.forEach {
            groups[it.key] = it.value
        }

        // 新グループにないグループを削除
        groups.entries.toList().forEach {
            if (!newServer.groups.containsKey(it.key)) groups.remove(it.key)
        }

        // グループにない接続を破棄
        connections.entries.toList().forEach {
            if (!groups.containsKey(it.key)) {
                it.value.dispose()
                connections.remove(it.key)
            }
        }

        this.specs = ConcurrentHashMap<Int, StreamSpec>().apply {
            groups.values.forEach { group ->
                group.values.forEach { spec -> put(spec.columnInternalId, spec) }
            }
        }
    }

    // このオブジェクトはもう使われなくなる
    fun dispose() {
        connections.values.forEach { it.dispose() }
        connections.clear()

        mergedConnection?.dispose()
        mergedConnection = null

        groups.clear()
    }

    private fun findConnection(streamKey: String?) =
        mergedConnection ?: connections.values.firstOrNull { it.streamKey == streamKey }

    // ストリーミング接続インジケータ
    fun getStreamingStatus(columnInternalId: Int): StreamIndicatorState? {
        val streamKey = specs[columnInternalId]?.streamKey
        return findConnection(streamKey)?.getStreamingStatus(streamKey)
    }

    suspend fun updateConnection() {

        val multiplex = if (accessInfo.isMastodon) {
            ti.versionGE(TootInstance.VERSION_3_3_0_rc1)
        } else {
            accessInfo.misskeyVersion >= 11
        }

        if (multiplex) {
            connections.values.forEach { it.dispose() }
            connections.clear()

            if(specs.isEmpty()){
                mergedConnection?.dispose()
                mergedConnection = null
            }else{
                if (mergedConnection == null) {
                    mergedConnection = StreamConnection(
                        manager,
                        this,
                        "[${accessInfo.acct.pretty}:multiplex",
                        streamKey = null
                    )
                }
                mergedConnection?.updateConnection()
            }

        } else {
            mergedConnection?.dispose()
            mergedConnection = null

            groups.entries.forEach { pair ->
                val(streamKey,group)=pair
                var conn = connections[streamKey]
                if (conn == null) {
                    conn = StreamConnection(
                        manager,
                        this,
                        "[${accessInfo.acct.pretty}:${group.streamKey}]",
                        streamKey = group.streamKey
                    )
                    connections[streamKey] = conn
                }
                conn.updateConnection()
            }
        }
    }

    fun getConnection(internalId: Int)=
        mergedConnection ?: connections[ specs[internalId]?.streamKey]
}

