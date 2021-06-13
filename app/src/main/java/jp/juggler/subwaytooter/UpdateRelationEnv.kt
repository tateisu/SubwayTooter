package jp.juggler.subwaytooter

import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.table.AcctSet
import jp.juggler.subwaytooter.table.TagSet
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.util.toJsonArray
import jp.juggler.util.toPostRequestBuilder
import java.util.HashSet

class UpdateRelationEnv(val column: Column) {

    val who_set = HashSet<EntityId>()
    val acct_set = HashSet<String>()
    val tag_set = HashSet<String>()

    fun add(whoRef: TootAccountRef?) {
        add(whoRef?.get())
    }

    fun add(who: TootAccount?) {
        who ?: return
        who_set.add(who.id)
        val fullAcct = column.access_info.getFullAcct(who)
        acct_set.add("@${fullAcct.ascii}")
        acct_set.add("@${fullAcct.pretty}")
        //
        add(who.movedRef)
    }

    fun add(s: TootStatus?) {
        if (s == null) return
        add(s.accountRef)
        add(s.reblog)
        s.tags?.forEach { tag_set.add(it.name) }
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
                val who_list =
                    parser.misskeyUserRelationMap.entries.toMutableList()
                var start = 0
                val end = who_list.size
                while (start < end) {
                    var step = end - start
                    if (step > Column.RELATIONSHIP_LOAD_STEP) step = Column.RELATIONSHIP_LOAD_STEP
                    UserRelation.saveListMisskey(now, column.access_info.db_id, who_list, start, step)
                    start += step
                }
                Column.log.d("updateRelation: update ${end} relations.")
            }

            // 2018/11/1 Misskeyにもリレーション取得APIができた
            // アカウントIDの集合からRelationshipを取得してデータベースに記録する

            size = who_set.size
            if (size > 0) {
                val who_list = ArrayList<EntityId>(size)
                who_list.addAll(who_set)

                val now = System.currentTimeMillis()

                n = 0
                while (n < who_list.size) {
                    val userIdList = ArrayList<EntityId>(Column.RELATIONSHIP_LOAD_STEP)
                    for (i in 0 until Column.RELATIONSHIP_LOAD_STEP) {
                        if (n >= size) break
                        if (!parser.misskeyUserRelationMap.containsKey(who_list[n])) {
                            userIdList.add(who_list[n])
                        }
                        ++n
                    }
                    if (userIdList.isEmpty()) continue

                    val result = client.request(
                        "/api/users/relation",
                        column.access_info.putMisskeyApiToken().apply {
                            put(
                                "userId",
                                userIdList.map { it.toString() }.toJsonArray()
                            )
                        }.toPostRequestBuilder()
                    )

                    if (result == null || result.response?.code in 400 until 500) break

                    val list = parseList(::TootRelationShip, parser, result.jsonArray)
                    if (list.size == userIdList.size) {
                        for (i in 0 until list.size) {
                            list[i].id = userIdList[i]
                        }
                        UserRelation.saveList2(now, column.access_info.db_id, list)
                    }
                }
                Column.log.d("updateRelation: update ${n} relations.")

            }

        } else {
            // アカウントIDの集合からRelationshipを取得してデータベースに記録する
            size = who_set.size
            if (size > 0) {
                val who_list = ArrayList<EntityId>(size)
                who_list.addAll(who_set)

                val now = System.currentTimeMillis()

                n = 0
                while (n < who_list.size) {
                    val sb = StringBuilder()
                    sb.append("/api/v1/accounts/relationships")
                    for (i in 0 until Column.RELATIONSHIP_LOAD_STEP) {
                        if (n >= size) break
                        sb.append(if (i == 0) '?' else '&')
                        sb.append("id[]=")
                        sb.append(who_list[n++].toString())
                    }
                    val result = client.request(sb.toString()) ?: break // cancelled.
                    val list = parseList(::TootRelationShip, parser, result.jsonArray)
                    if (list.size > 0) UserRelation.saveListMastodon(
                        now,
                        column.access_info.db_id,
                        list
                    )
                }
                Column.log.d("updateRelation: update ${n} relations.")
            }
        }

        // 出現したacctをデータベースに記録する
        size = acct_set.size
        if (size > 0) {
            val acct_list = ArrayList<String?>(size)
            acct_list.addAll(acct_set)

            val now = System.currentTimeMillis()

            n = 0
            while (n < acct_list.size) {
                var length = size - n
                if (length > Column.ACCT_DB_STEP) length = Column.ACCT_DB_STEP
                AcctSet.saveList(now, acct_list, n, length)
                n += length
            }
            Column.log.d("updateRelation: update ${n} acct.")

        }

        // 出現したタグをデータベースに記録する
        size = tag_set.size
        if (size > 0) {
            val tag_list = ArrayList<String?>(size)
            tag_list.addAll(tag_set)

            val now = System.currentTimeMillis()

            n = 0
            while (n < tag_list.size) {
                var length = size - n
                if (length > Column.ACCT_DB_STEP) length = Column.ACCT_DB_STEP
                TagSet.saveList(now, tag_list, n, length)
                n += length
            }
            Column.log.d("updateRelation: update ${n} tag.")
        }
    }
}
