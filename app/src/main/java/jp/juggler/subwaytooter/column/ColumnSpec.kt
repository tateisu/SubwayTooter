package jp.juggler.subwaytooter.column

import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.data.JsonObject
import jp.juggler.util.log.LogCategory

private val log = LogCategory("ColumnSpec")

object ColumnSpec {

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> getParamAt(params: Array<out Any>, idx: Int): T {
        return params[idx] as T
    }

    private fun getParamEntityId(
        params: Array<out Any>,
        @Suppress("SameParameterValue") idx: Int,
    ): EntityId =
        when (val o = params[idx]) {
            is EntityId -> o
            is String -> EntityId(o)
            else -> error("getParamEntityId [$idx] bad type. $o")
        }

    private fun getParamString(params: Array<out Any>, idx: Int): String =
        when (val o = params[idx]) {
            is String -> o
            is EntityId -> o.toString()
            is Host -> o.ascii
            is Acct -> o.ascii
            else -> error("getParamString [$idx] bad type. $o")
        }

    private fun getParamJsonObject(params: Array<out Any>, idx: Int): JsonObject =
        when (val o = params[idx]) {
            is JsonObject -> o
            else -> error("getParamJsonObject [$idx] bad type. $o")
        }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> getParamAtNullable(params: Array<out Any>, idx: Int): T? {
        if (idx >= params.size) return null
        return params[idx] as T
    }

    fun decode(column: Column, params: Array<out Any>) {
        column.run {
            when (type) {

                ColumnType.CONVERSATION,
                ColumnType.CONVERSATION_WITH_REFERENCE,
                ColumnType.BOOSTED_BY,
                ColumnType.FAVOURITED_BY,
                ColumnType.LOCAL_AROUND,
                ColumnType.FEDERATED_AROUND,
                ColumnType.ACCOUNT_AROUND,
                -> statusId = getParamEntityId(params, 0)

                ColumnType.STATUS_HISTORY -> {
                    statusId = getParamEntityId(params, 0)
                    originalStatus = getParamJsonObject(params, 1)
                }

                ColumnType.PROFILE, ColumnType.LIST_TL, ColumnType.LIST_MEMBER,
                ColumnType.MISSKEY_ANTENNA_TL,
                -> profileId = getParamEntityId(params, 0)

                ColumnType.HASHTAG ->
                    hashtag = getParamString(params, 0)

                ColumnType.HASHTAG_FROM_ACCT -> {
                    hashtag = getParamString(params, 0)
                    hashtagAcct = getParamString(params, 1)
                }

                ColumnType.NOTIFICATION_FROM_ACCT -> {
                    hashtagAcct = getParamString(params, 0)
                }

                ColumnType.SEARCH -> {
                    searchQuery = getParamString(params, 0)
                    searchResolve = getParamAt(params, 1)
                }

                ColumnType.SEARCH_MSP, ColumnType.SEARCH_TS, ColumnType.SEARCH_NOTESTOCK ->
                    searchQuery = getParamString(params, 0)

                ColumnType.INSTANCE_INFORMATION ->
                    instanceUri = getParamString(params, 0)

                ColumnType.PROFILE_DIRECTORY -> {
                    instanceUri = getParamString(params, 0)
                    searchResolve = true
                }

                ColumnType.DOMAIN_TIMELINE -> {
                    instanceUri = getParamString(params, 0)
                }

                else -> {
                }
            }
        }
    }

    fun isSameSpec(
        column: Column,
        ai: SavedAccount,
        type: ColumnType,
        params: Array<out Any>,
    ): Boolean {

        if (type != column.type || ai != column.accessInfo) return false

        return try {
            when (type) {

                ColumnType.PROFILE,
                ColumnType.LIST_TL,
                ColumnType.LIST_MEMBER,
                ColumnType.MISSKEY_ANTENNA_TL,
                -> column.profileId == getParamEntityId(params, 0)

                ColumnType.CONVERSATION,
                ColumnType.CONVERSATION_WITH_REFERENCE,
                ColumnType.BOOSTED_BY,
                ColumnType.FAVOURITED_BY,
                ColumnType.LOCAL_AROUND,
                ColumnType.FEDERATED_AROUND,
                ColumnType.ACCOUNT_AROUND,
                ColumnType.STATUS_HISTORY,
                -> column.statusId == getParamEntityId(params, 0)

                ColumnType.HASHTAG -> {
                    (getParamString(params, 0) == column.hashtag) &&
                            ((getParamAtNullable<String>(params, 1) ?: "") == column.hashtagAny) &&
                            ((getParamAtNullable<String>(params, 2) ?: "") == column.hashtagAll) &&
                            ((getParamAtNullable<String>(params, 3) ?: "") == column.hashtagNone)
                }

                ColumnType.HASHTAG_FROM_ACCT -> {
                    (getParamString(params, 0) == column.hashtag) &&
                            ((getParamAtNullable<String>(params, 1) ?: "") == column.hashtagAcct)
                }

                ColumnType.NOTIFICATION_FROM_ACCT -> {
                    ((getParamAtNullable<String>(params, 0) ?: "") == column.hashtagAcct)
                }

                ColumnType.SEARCH ->
                    getParamString(params, 0) == column.searchQuery &&
                            getParamAtNullable<Boolean>(params, 1) == column.searchResolve

                ColumnType.SEARCH_MSP,
                ColumnType.SEARCH_TS,
                ColumnType.SEARCH_NOTESTOCK,
                ->
                    getParamString(params, 0) == column.searchQuery

                ColumnType.INSTANCE_INFORMATION ->
                    getParamString(params, 0) == column.instanceUri

                ColumnType.PROFILE_DIRECTORY ->
                    getParamString(params, 0) == column.instanceUri &&
                            getParamAtNullable<String>(params, 1) == column.searchQuery &&
                            getParamAtNullable<Boolean>(params, 2) == column.searchResolve

                ColumnType.DOMAIN_TIMELINE ->
                    getParamString(params, 0) == column.instanceUri

                else -> true
            }
        } catch (ex: Throwable) {
            log.e(ex, "isSameSpec failed.")
            false
        }
    }
}
