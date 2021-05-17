package jp.juggler.subwaytooter

import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.table.SavedAccount

object ColumnSpec {

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> getParamAt(params: Array<out Any>, idx: Int): T {
        return params[idx] as T
    }

    private fun getParamEntityId(
        params: Array<out Any>,
        @Suppress("SameParameterValue") idx: Int
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

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> getParamAtNullable(params: Array<out Any>, idx: Int): T? {
        if (idx >= params.size) return null
        return params[idx] as T
    }

    fun decode(column: Column, params: Array<out Any>) {
        column.run {
            when (type) {

                ColumnType.CONVERSATION,
                ColumnType.BOOSTED_BY,
                ColumnType.FAVOURITED_BY,
                ColumnType.LOCAL_AROUND,
                ColumnType.FEDERATED_AROUND,
                ColumnType.ACCOUNT_AROUND ->
                    status_id = getParamEntityId(params, 0)

                ColumnType.PROFILE, ColumnType.LIST_TL, ColumnType.LIST_MEMBER,
                ColumnType.MISSKEY_ANTENNA_TL ->
                    profile_id = getParamEntityId(params, 0)

                ColumnType.HASHTAG ->
                    hashtag = getParamString(params, 0)

                ColumnType.HASHTAG_FROM_ACCT -> {
                    hashtag = getParamString(params, 0)
                    hashtag_acct = getParamString(params, 1)
                }

                ColumnType.NOTIFICATION_FROM_ACCT -> {
                    hashtag_acct = getParamString(params, 0)
                }

                ColumnType.SEARCH -> {
                    search_query = getParamString(params, 0)
                    search_resolve = getParamAt(params, 1)
                }

                ColumnType.SEARCH_MSP, ColumnType.SEARCH_TS, ColumnType.SEARCH_NOTESTOCK ->
                    search_query = getParamString(params, 0)

                ColumnType.INSTANCE_INFORMATION ->
                    instance_uri = getParamString(params, 0)

                ColumnType.PROFILE_DIRECTORY -> {
                    instance_uri = getParamString(params, 0)
                    search_resolve = true
                }

                ColumnType.DOMAIN_TIMELINE -> {
                    instance_uri = getParamString(params, 0)
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
        params: Array<out Any>
    ): Boolean {

        if (type != column.type || ai != column.access_info) return false

        return try {
            when (type) {

                ColumnType.PROFILE,
                ColumnType.LIST_TL,
                ColumnType.LIST_MEMBER,
                ColumnType.MISSKEY_ANTENNA_TL ->
                    column.profile_id == getParamEntityId(params, 0)

                ColumnType.CONVERSATION,
                ColumnType.BOOSTED_BY,
                ColumnType.FAVOURITED_BY,
                ColumnType.LOCAL_AROUND,
                ColumnType.FEDERATED_AROUND,
                ColumnType.ACCOUNT_AROUND ->
                    column.status_id == getParamEntityId(params, 0)

                ColumnType.HASHTAG -> {
                    (getParamString(params, 0) == column.hashtag)
                        && ((getParamAtNullable<String>(params, 1) ?: "") == column.hashtag_any)
                        && ((getParamAtNullable<String>(params, 2) ?: "") == column.hashtag_all)
                        && ((getParamAtNullable<String>(params, 3) ?: "") == column.hashtag_none)
                }

                ColumnType.HASHTAG_FROM_ACCT -> {
                    (getParamString(params, 0) == column.hashtag)
                        && ((getParamAtNullable<String>(params, 1) ?: "") == column.hashtag_acct)
                }

                ColumnType.NOTIFICATION_FROM_ACCT -> {
                    ((getParamAtNullable<String>(params, 0) ?: "") == column.hashtag_acct)
                }

                ColumnType.SEARCH ->
                    getParamString(params, 0) == column.search_query &&
                        getParamAtNullable<Boolean>(params, 1) == column.search_resolve

                ColumnType.SEARCH_MSP,
                ColumnType.SEARCH_TS,
                ColumnType.SEARCH_NOTESTOCK ->
                    getParamString(params, 0) == column.search_query

                ColumnType.INSTANCE_INFORMATION ->
                    getParamString(params, 0) == column.instance_uri

                ColumnType.PROFILE_DIRECTORY ->
                    getParamString(params, 0) == column.instance_uri &&
                        getParamAtNullable<String>(params, 1) == column.search_query &&
                        getParamAtNullable<Boolean>(params, 2) == column.search_resolve

                ColumnType.DOMAIN_TIMELINE ->
                    getParamString(params, 0) == column.instance_uri

                else -> true
            }
        } catch (ex: Throwable) {
            Column.log.trace(ex)
            false
        }
    }
}
