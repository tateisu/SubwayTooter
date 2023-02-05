package jp.juggler.subwaytooter

import jp.juggler.subwaytooter.table.SavedAccount
import org.junit.Assert.assertEquals
import org.junit.Test

class TestColumnMeta {

    @Test
    fun test1() {
        val columnList = SavedAccount.columnList
        val actual = columnList.createTableSql()
            .joinToString(";")
        val expect =
            "create table if not exists access_info (_id INTEGER PRIMARY KEY,a text not null,confirm_boost integer default 1,confirm_favourite integer default 1,confirm_follow integer default 1,confirm_follow_locked integer default 1,confirm_post integer default 1,confirm_reaction integer default 1,confirm_unbookmark integer default 1,confirm_unboost integer default 1,confirm_unfavourite integer default 1,confirm_unfollow integer default 1,d text,default_sensitive integer default 0,default_text text default '',dont_hide_nsfw integer default 0,dont_show_timeout integer default 0,expand_cw integer default 0,extra_json text default null,h text not null,image_max_megabytes text default null,image_resize text default null,is_misskey integer default 0,last_notification_error text,last_push_endpoint text,last_subscription_error text,max_toot_chars integer default 0,movie_max_megabytes text default null,notification_boost integer default 1,notification_favourite integer default 1,notification_follow integer default 1,notification_follow_request integer default 1,notification_mention integer default 1,notification_post integer default 1,notification_reaction integer default 1,notification_server text default '',notification_update integer default 1,notification_vote integer default 1,push_policy text default null,register_key text default '',register_time integer default 0,sound_uri text default '',t text not null,u text not null,visibility text);create index if not exists access_info_user on access_info(u);create index if not exists access_info_host on access_info(h,u)"
        assertEquals("SavedAccount createParams()", expect, actual)
    }

    @Test
    fun test2() {
        val columnList = SavedAccount.columnList
        val expectMap = mapOf(
            2 to "alter table access_info add column notification_boost integer default 1;alter table access_info add column notification_favourite integer default 1;alter table access_info add column notification_follow integer default 1;alter table access_info add column notification_mention integer default 1",
            10 to "alter table access_info add column confirm_follow integer default 1;alter table access_info add column confirm_follow_locked integer default 1;alter table access_info add column confirm_post integer default 1;alter table access_info add column confirm_unfollow integer default 1",
            13 to "alter table access_info add column notification_server text default ''",
            14 to "alter table access_info add column register_key text default '';alter table access_info add column register_time integer default 0",
            16 to "alter table access_info add column sound_uri text default ''",
            18 to "alter table access_info add column dont_show_timeout integer default 0",
            23 to "alter table access_info add column confirm_favourite integer default 1",
            24 to "alter table access_info add column confirm_unboost integer default 1;alter table access_info add column confirm_unfavourite integer default 1",
            27 to "alter table access_info add column default_text text default ''",
            28 to "alter table access_info add column is_misskey integer default 0",
            33 to "alter table access_info add column notification_reaction integer default 1;alter table access_info add column notification_vote integer default 1",
            38 to "alter table access_info add column default_sensitive integer default 0;alter table access_info add column expand_cw integer default 0",
            39 to "alter table access_info add column max_toot_chars integer default 0",
            42 to "alter table access_info add column last_notification_error text",
            44 to "alter table access_info add column notification_follow_request integer default 1",
            45 to "alter table access_info add column last_subscription_error text",
            46 to "alter table access_info add column last_push_endpoint text",
            56 to "alter table access_info add column d text",
            57 to "alter table access_info add column notification_post integer default 1",
            59 to "alter table access_info add column image_max_megabytes text default null;alter table access_info add column image_resize text default null;alter table access_info add column movie_max_megabytes text default null",
            60 to "alter table access_info add column push_policy text default null",
            61 to "alter table access_info add column confirm_reaction integer default 1",
        )
        for (newVersion in 1..expectMap.maxOf { it.key }) {
            val actualSql = columnList.upgradeSql(db = null, newVersion - 1, newVersion)
                .joinToString(";")
            val expectSql = expectMap[newVersion] ?: ""
            assertEquals("SavedAccount v$newVersion", expectSql, actualSql)
        }
    }
}
