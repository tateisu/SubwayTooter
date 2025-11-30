package jp.juggler.subwaytooter.column

import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.table.daoAcctSet
import jp.juggler.subwaytooter.table.daoTagHistory
import jp.juggler.subwaytooter.table.daoUserRelation
import jp.juggler.util.data.toJsonArray
import jp.juggler.util.log.LogCategory
import jp.juggler.util.network.toPostRequestBuilder
import kotlin.math.min

class UserRelationLoader(val column: Column) {
    companion object {
        private val log = LogCategory("UserRelationLoader")
    }

    val whoSet = HashSet<EntityId>()
    val acctSet = HashSet<String>()
    private val tagSet = HashSet<String>()

    fun add(whoRef: TootAccountRef?) {
        add(whoRef?.get())
    }

    fun add(who: TootAccount?) {
        who ?: return
        whoSet.add(who.id)
        val fullAcct = column.accessInfo.getFullAcct(who)
        acctSet.add("@${fullAcct.ascii}")
        acctSet.add("@${fullAcct.pretty}")
        //
        add(who.movedRef)
    }

    fun add(s: TootStatus?) {
        if (s == null) return
        add(s.accountRef)
        add(s.reblog)
        s.tags?.forEach { tagSet.add(it.name) }
    }

    fun add(n: TootNotification?) {
        if (n == null) return
        add(n.accountRef)
        add(n.status)
    }

    suspend fun update(client: TootApiClient, parser: TootParser) {

        var n: Int
        var size: Int

        if (column.isMisskey) {

            // parser内部にアカウントIDとRelationのマップが生成されるので、それをデータベースに記録する
            run {
                val now = System.currentTimeMillis()
                val whoList =
                    parser.misskeyUserRelationMap.entries.toMutableList()
                var start = 0
                val end = whoList.size
                while (start < end) {
                    var step = end - start
                    if (step > Column.RELATIONSHIP_LOAD_STEP) step = Column.RELATIONSHIP_LOAD_STEP
                    daoUserRelation.saveListMisskey(
                        now,
                        column.accessInfo.db_id,
                        whoList,
                        start,
                        step
                    )
                    start += step
                }
                log.d("updateRelation: update $end relations.")
            }

            // 2018/11/1 Misskeyにもリレーション取得APIができた
            // アカウントIDの集合からRelationshipを取得してデータベースに記録する

            size = whoSet.size
            if (size > 0) {
                val whoList = ArrayList<EntityId>(size)
                whoList.addAll(whoSet)

                val now = System.currentTimeMillis()

                n = 0
                while (n < whoList.size) {
                    val userIdList = ArrayList<EntityId>(Column.RELATIONSHIP_LOAD_STEP)
                    for (i in 0 until Column.RELATIONSHIP_LOAD_STEP) {
                        if (n >= size) break
                        if (!parser.misskeyUserRelationMap.containsKey(whoList[n])) {
                            userIdList.add(whoList[n])
                        }
                        ++n
                    }
                    if (userIdList.isEmpty()) continue

                    val result = client.request(
                        "/api/users/relation",
                        column.accessInfo.putMisskeyApiToken().apply {
                            put(
                                "userId",
                                userIdList.map { it.toString() }.toJsonArray()
                            )
                        }.toPostRequestBuilder()
                    )

                    if (result == null || result.response?.code in 400 until 500) break

                    val list = parseList(result.jsonArray) { TootRelationShip(parser, it) }
                    if (list.size == userIdList.size) {
                        for (i in 0 until list.size) {
                            list[i].id = userIdList[i]
                        }
                        daoUserRelation.saveListMisskeyRelationApi(
                            now,
                            column.accessInfo.db_id,
                            list
                        )
                    }
                }
                log.d("updateRelation: update $n relations.")
            }
        } else {
            // アカウントIDの集合からRelationshipを取得してデータベースに記録する
            size = whoSet.size
            if (size > 0) {
                val whoList = ArrayList<EntityId>(size)
                whoList.addAll(whoSet)

                val now = System.currentTimeMillis()

                n = 0
                while (n < whoList.size) {
                    val sb = StringBuilder()
                    sb.append("/api/v1/accounts/relationships")
                    for (i in 0 until Column.RELATIONSHIP_LOAD_STEP) {
                        if (n >= size) break
                        sb.append(if (i == 0) '?' else '&')
                        sb.append("id[]=")
                        sb.append(whoList[n++].toString())
                    }
                    val result = client.request(sb.toString()) ?: break // cancelled.
                    val list = parseList(result.jsonArray) { TootRelationShip(parser, it) }
                    if (list.size > 0) daoUserRelation.saveListMastodon(
                        now,
                        column.accessInfo.db_id,
                        list
                    )
                }
                log.d("updateRelation: update $n relations.")
            }
        }

        // 出現したacctをデータベースに記録する
        size = acctSet.size
        if (size > 0) {
            val acctList = ArrayList<String?>(size)
            acctList.addAll(acctSet)

            val now = System.currentTimeMillis()

            n = 0
            while (n < acctList.size) {
                var length = size - n
                if (length > Column.ACCT_DB_STEP) length = Column.ACCT_DB_STEP
                daoAcctSet.saveList(now, acctList, n, length)
                n += length
            }
            log.d("updateRelation: update $n acct.")
        }

        // 出現したタグをデータベースに記録する
        size = tagSet.size
        if (size > 0) {
            val tagList = ArrayList<String?>(size)
            tagList.addAll(tagSet)

            val now = System.currentTimeMillis()

            n = 0
            while (n < size) {
                val step = min(Column.ACCT_DB_STEP, size - n)
                daoTagHistory.saveList(now, tagList, n, step)
                n += step
            }
            log.d("updateRelation: update $n tag.")
        }
    }
}
