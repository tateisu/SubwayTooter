package jp.juggler.subwaytooter.streaming

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.table.SavedAccount
import java.util.concurrent.ConcurrentHashMap


// ストリーミング接続をacct単位でグルーピングする
class StreamGroupAcct(
    private val manager: StreamManager,
    val account: SavedAccount,
    var ti: TootInstance
) {
    val parser: TootParser = TootParser(manager.appState.context, linkHelper = account)

    val keyGroups = ConcurrentHashMap<StreamSpec, StreamGroupKey>()

    // 接続を束ねない場合に使われる
    private val connections = ConcurrentHashMap<StreamSpec, StreamConnection>()

    // 接続を束ねる場合に使われる
    private var mergedConnection: StreamConnection? = null

    // カラムIDから出力先へのマップ
    @Volatile
    private var destinations = ConcurrentHashMap<Int, StreamDestination>()

    fun addSpec(dst: StreamDestination) {
        val spec = dst.spec
        var group = keyGroups[spec]
        if (group == null) {
            group = StreamGroupKey(spec)
            keyGroups[spec] = group
        }
        group.destinations[ dst.columnInternalId] = dst
    }

    fun merge(newServer: StreamGroupAcct) {

        // 新スペックの値をコピー
        this.ti = newServer.ti

        newServer.keyGroups.entries.forEach {
            keyGroups[it.key] = it.value
        }

        // 新グループにないグループを削除
        keyGroups.entries.toList().forEach {
            if (!newServer.keyGroups.containsKey(it.key)) keyGroups.remove(it.key)
        }

        // グループにない接続を破棄
        connections.entries.toList().forEach {
            if (!keyGroups.containsKey(it.key)) {
                it.value.dispose()
                connections.remove(it.key)
            }
        }

        this.destinations = ConcurrentHashMap<Int, StreamDestination>().apply {
            keyGroups.values.forEach { group ->
                group.destinations.values.forEach { spec -> put(spec.columnInternalId, spec) }
            }
        }
    }

    // このオブジェクトはもう使われなくなる
    fun dispose() {
        connections.values.forEach { it.dispose() }
        connections.clear()

        mergedConnection?.dispose()
        mergedConnection = null

        keyGroups.clear()
    }

    private fun findConnection(streamSpec: StreamSpec?) =
        mergedConnection ?: connections.values.firstOrNull { it.spec == streamSpec }

    // ストリーミング接続インジケータ
    fun getStreamingStatus(columnInternalId: Int): StreamIndicatorState? {
        val spec = destinations[columnInternalId]?.spec
        return findConnection(spec)?.getStreamingStatus(spec)
    }

    suspend fun updateConnection() {

        val multiplex = if (account.isMastodon) {
            ti.versionGE(TootInstance.VERSION_3_3_0_rc1)
        } else {
            account.misskeyVersion >= 11
        }

        if (multiplex) {
            connections.values.forEach { it.dispose() }
            connections.clear()

            if(destinations.isEmpty()){
                mergedConnection?.dispose()
                mergedConnection = null
            }else{
                if (mergedConnection == null) {
                    mergedConnection = StreamConnection(
                        manager,
                        this,
                        spec = null,
                        name="[${account.acct.pretty}:multiplex]",
                    )
                }
                mergedConnection?.updateConnection()
            }

        } else {
            mergedConnection?.dispose()
            mergedConnection = null

            keyGroups.entries.forEach { pair ->
                val(streamKey,group)=pair
                var conn = connections[streamKey]
                if (conn == null) {
                    conn = StreamConnection(
                        manager,
                        this,
                        spec = group.spec,
                        "[${account.acct.pretty}:${group.spec.name}]",
                    )
                    connections[streamKey] = conn
                }
                conn.updateConnection()
            }
        }
    }

    fun getConnection(internalId: Int)=
        mergedConnection ?: destinations[internalId]?.spec?.let{ connections[it]}
}

