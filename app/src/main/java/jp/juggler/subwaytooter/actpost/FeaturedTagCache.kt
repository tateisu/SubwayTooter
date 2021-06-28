package jp.juggler.subwaytooter.actpost

import android.os.SystemClock
import jp.juggler.subwaytooter.ActPost
import jp.juggler.subwaytooter.api.ApiTask
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.TootTag
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.util.jsonObject
import jp.juggler.util.launchMain
import jp.juggler.util.toPostRequestBuilder
import jp.juggler.util.wrapWeakReference

class FeaturedTagCache(val list: List<TootTag>, val time: Long)

fun ActPost.updateFeaturedTags() {
    val account = account
    if (account == null || account.isPseudo) {
        return
    }

    val cache = featuredTagCache[account.acct.ascii]
    val now = SystemClock.elapsedRealtime()
    if (cache != null && now - cache.time <= 300000L) return

    // 同時に実行するタスクは1つまで
    if (jobFeaturedTag?.get()?.isActive != true) {
        jobFeaturedTag = launchMain {
            runApiTask(
                account,
                progressStyle = ApiTask.PROGRESS_NONE,
            ) { client ->
                if (account.isMisskey) {
                    client.request(
                        "/api/hashtags/trend",
                        jsonObject { }
                            .toPostRequestBuilder()
                    )?.also { result ->
                        val list = TootTag.parseList(
                            TootParser(this@runApiTask, account),
                            result.jsonArray
                        )
                        featuredTagCache[account.acct.ascii] =
                            FeaturedTagCache(list, SystemClock.elapsedRealtime())
                    }
                } else {
                    client.request("/api/v1/featured_tags")?.also { result ->
                        val list = TootTag.parseList(
                            TootParser(this@runApiTask, account),
                            result.jsonArray
                        )
                        featuredTagCache[account.acct.ascii] =
                            FeaturedTagCache(list, SystemClock.elapsedRealtime())
                    }
                }
            }
            if (isFinishing || isDestroyed) return@launchMain
            updateFeaturedTags()
        }.wrapWeakReference
    }
}
