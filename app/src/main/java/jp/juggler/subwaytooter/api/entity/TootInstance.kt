package jp.juggler.subwaytooter.api.entity

import android.os.SystemClock
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.subwaytooter.util.VersionString
import jp.juggler.util.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import java.util.regex.Pattern
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine
import kotlin.math.max

// インスタンスの種別
enum class InstanceType {
    Mastodon,
    Misskey,
    Pixelfed,
    Pleroma
}

object InstanceCapability {
//    FavouriteHashtag(CapabilitySource.Fedibird, "favourite_hashtag"),
//    FavouriteDomain(CapabilitySource.Fedibird, "favourite_domain"),
//    StatusExpire(CapabilitySource.Fedibird, "status_expire"),
//    FollowNoDelivery(CapabilitySource.Fedibird, "follow_no_delivery"),
//    FollowHashtag(CapabilitySource.Fedibird, "follow_hashtag"),
//    SubscribeAccount(CapabilitySource.Fedibird, "subscribe_account"),
//    SubscribeDomain(CapabilitySource.Fedibird, "subscribe_domain"),
//    SubscribeKeyword(CapabilitySource.Fedibird, "subscribe_keyword"),
//    TimelineNoLocal(CapabilitySource.Fedibird, "timeline_no_local"),
//    TimelineDomain(CapabilitySource.Fedibird, "timeline_domain"),
//    TimelineGroup(CapabilitySource.Fedibird, "timeline_group"),
//    TimelineGroupDirectory(CapabilitySource.Fedibird, "timeline_group_directory"),

    fun quote(ti: TootInstance?) =
        ti?.feature_quote == true

    fun visibilityMutual(ti: TootInstance?) =
        ti?.fedibird_capabilities?.contains("visibility_mutual") == true

    fun visibilityLimited(ti: TootInstance?) =
        ti?.fedibird_capabilities?.contains("visibility_limited") == true

    fun emojiReaction(ai: SavedAccount, ti: TootInstance?) =
        when {
            ai.isPseudo -> false
            ai.isMisskey -> true
            else ->
                ti?.fedibird_capabilities?.contains("emoji_reaction") == true ||
                        ti?.pleromaFeatures?.contains("pleroma_emoji_reactions") == true
        }

    fun canMultipleReaction(ai: SavedAccount, ti: TootInstance? = TootInstance.getCached(ai)) =
        when {
            ai.isPseudo -> false
            ai.isMisskey -> false
            else -> ti?.pleromaFeatures?.contains("pleroma_emoji_reactions") == true
        }

    fun listMyReactions(ai: SavedAccount, ti: TootInstance?) =
        when {
            ai.isPseudo -> false
            ai.isMisskey ->
                // m544 extension
                ti?.misskeyEndpoints?.contains("i/reactions") == true
            else ->
                // fedibird extension
                ti?.fedibird_capabilities?.contains("emoji_reaction") == true
        }
}

class TootInstance(parser: TootParser, src: JsonObject) {

    // いつ取得したか(内部利用)
    private var time_parse: Long = SystemClock.elapsedRealtime()

    val isExpired: Boolean
        get() = SystemClock.elapsedRealtime() - time_parse >= EXPIRE

    //	URI of the current instance
    // apiHost ではなく apDomain を示す
    val uri: String?

    //	The instance's title
    val title: String?

    //	A description for the instance
    // (HTML)
    // (Mastodon: 3.0.0より後のWebUIでは全く使われなくなる見込み。 https://github.com/tootsuite/mastodon/pull/12119)
    val description: String?

    // (Mastodon 3.0.0以降)
    // (HTML)
    val short_description: String?

    // An email address which can be used to contact the instance administrator
    // misskeyの場合はURLらしい
    val email: String?

    val version: String?

    // バージョンの内部表現
    private val decoded_version: VersionString

    // インスタンスのサムネイル。推奨サイズ1200x630px。マストドン1.6.1以降。
    val thumbnail: String?

    // ユーザ数等の数字。マストドン1.6以降。
    val stats: Stats?

    // 言語のリスト。マストドン2.3.0以降
    val languages: ArrayList<String>?

    val contact_account: TootAccount?

    // (Pleroma only) トゥートの最大文字数
    val max_toot_chars: Int?

    // (Mastodon 3.0.0)
    val approval_required: Boolean

    // (Mastodon 3.1.4)
    val invites_enabled: Boolean?

    val instanceType: InstanceType

    var feature_quote = false

    var fedibird_capabilities: Set<String>? = null

    var misskeyEndpoints: Set<String>? = null

    var pleromaFeatures: Set<String>? = null

    var configuration: JsonObject? = null

    // XXX: urls をパースしてない。使ってないから…

    init {
        if (parser.serviceType == ServiceType.MISSKEY) {

            this.misskeyEndpoints = src.jsonArray("_endpoints")?.stringList()?.toSet()

            this.uri = parser.apiHost.ascii
            this.title = parser.apiHost.pretty
            val sv = src.jsonObject("maintainer")?.string("url")
            this.email = when {
                sv?.startsWith("mailto:") == true -> sv.substring(7)
                else -> sv
            }

            this.version = src.string("version")
            this.decoded_version = VersionString(version)
            this.stats = null
            this.thumbnail = null
            this.max_toot_chars = src.int("maxNoteTextLength")
            this.instanceType = InstanceType.Misskey
            this.languages = src.jsonArray("langs")?.stringArrayList() ?: ArrayList()
            this.contact_account = null

            this.description = src.string("description")
            this.short_description = null
            this.approval_required = false

            this.feature_quote = true

            this.invites_enabled = null
        } else {
            this.uri = src.string("uri")
            this.title = src.string("title")

            val sv = src.string("email")
            this.email = when {
                sv?.startsWith("mailto:") == true -> sv.substring(7)
                else -> sv
            }

            this.version = src.string("version")
            this.decoded_version = VersionString(version)
            this.stats = parseItem(::Stats, src.jsonObject("stats"))
            this.thumbnail = src.string("thumbnail")

            this.max_toot_chars = src.int("max_toot_chars")

            this.instanceType = when {
                rePleroma.matcher(version ?: "").find() -> InstanceType.Pleroma
                rePixelfed.matcher(version ?: "").find() -> InstanceType.Pixelfed
                else -> InstanceType.Mastodon
            }

            languages = src.jsonArray("languages")?.stringArrayList()

            val parser2 = TootParser(
                parser.context,
                LinkHelper.create(Host.parse(uri ?: "?"))
            )
            contact_account =
                parseItem(::TootAccount, parser2, src.jsonObject("contact_account"))

            this.description = src.string("description")
            this.short_description = src.string("short_description")
            this.approval_required = src.boolean("approval_required") ?: false

            this.feature_quote = src.boolean("feature_quote") ?: false

            this.invites_enabled = src.boolean("invites_enabled")

            this.fedibird_capabilities =
                src.jsonArray("fedibird_capabilities")?.stringList()?.toSet()
            this.pleromaFeatures =
                src.jsonObject("pleroma")?.jsonObject("metadata")?.jsonArray("features")
                    ?.stringList()?.toSet()

            this.configuration = src.jsonObject("configuration")
        }
    }

    class Stats(src: JsonObject) {
        val user_count = src.long("user_count") ?: -1L
        val status_count = src.long("status_count") ?: -1L
        val domain_count = src.long("domain_count") ?: -1L
    }

    val misskeyVersion: Int
        get() = when {
            instanceType != InstanceType.Misskey -> 0
            else -> decoded_version.majorVersion ?: 10
        }

    val canUseReference: Boolean?
        get() = fedibird_capabilities?.contains("status_reference")

    fun versionGE(check: VersionString) = decoded_version.ge(check)

    companion object {
        private val log = LogCategory("TootInstance")

        private val rePleroma = """\bpleroma\b""".asciiPattern(Pattern.CASE_INSENSITIVE)
        private val rePixelfed = """\bpixelfed\b""".asciiPattern(Pattern.CASE_INSENSITIVE)

        val VERSION_1_6 = VersionString("1.6")
        val VERSION_2_4_0_rc1 = VersionString("2.4.0rc1")
        val VERSION_2_4_0_rc2 = VersionString("2.4.0rc2")

        //		val VERSION_2_4_0 = VersionString("2.4.0")
        //		val VERSION_2_4_1_rc1 = VersionString("2.4.1rc1")
        val VERSION_2_4_1 = VersionString("2.4.1")
        val VERSION_2_6_0 = VersionString("2.6.0")
        val VERSION_2_7_0_rc1 = VersionString("2.7.0rc1")
        val VERSION_2_8_0_rc1 = VersionString("2.8.0rc1")
        val VERSION_3_0_0_rc1 = VersionString("3.0.0rc1")
        val VERSION_3_1_0_rc1 = VersionString("3.1.0rc1")
        val VERSION_3_1_3 = VersionString("3.1.3")
        val VERSION_3_3_0_rc1 = VersionString("3.3.0rc1")
        val VERSION_3_4_0_rc1 = VersionString("3.4.0rc1")
        val VERSION_3_5_0_rc1 = VersionString("3.5.0rc1")

        val MISSKEY_VERSION_11 = VersionString("11.0")
        val MISSKEY_VERSION_12 = VersionString("12.0")
        val MISSKEY_VERSION_12_75_0 = VersionString("12.75.0")

        private val reDigits = """(\d+)""".asciiPattern()

        private const val EXPIRE = (1000 * 3600).toLong()

        const val DESCRIPTION_DEFAULT = "(no description)"

        // 引数はtoken_infoかTootInstanceのパース前のいずれか
        fun parseMisskeyVersion(tokenInfo: JsonObject): Int {
            return when (val o = tokenInfo[TootApiClient.KEY_MISSKEY_VERSION]) {
                is Int -> o
                is Boolean -> if (o) 10 else 0
                else -> 0
            }
        }

        // 疑似アカウントの追加時に、インスタンスの検証を行う
        private suspend fun TootApiClient.getInstanceInformationMastodon(
            forceAccessToken: String? = null,
        ): TootApiResult? {
            val result = TootApiResult.makeWithCaption(apiHost?.pretty)
            if (result.error != null) return result

            if (sendRequest(result) {
                    val builder = Request.Builder().url("https://${apiHost?.ascii}/api/v1/instance")

                    (forceAccessToken ?: account?.getAccessToken())
                        ?.notEmpty()?.let { builder.header("Authorization", "Bearer $it") }
                    builder.build()
                }
            ) {
                parseJson(result) ?: return null
            }

            return result
        }

        private suspend fun TootApiClient.getMisskeyEndpoints(
            forceAccessToken: String? = null,
        ): TootApiResult? {
            val result = TootApiResult.makeWithCaption(apiHost?.pretty)
            if (result.error != null) return result

            if (sendRequest(result) {
                    jsonObject {
                        (forceAccessToken ?: account?.misskeyApiToken)
                            ?.notEmpty()?.let { put("i", it) }
                    }.toPostRequestBuilder()
                        .url("https://${apiHost?.ascii}/api/endpoints")
                        .build()
                }
            ) {
                parseJson(result) ?: return null
            }
            return result
        }

        // 疑似アカウントの追加時に、インスタンスの検証を行う
        private suspend fun TootApiClient.getInstanceInformationMisskey(
            forceAccessToken: String? = null,
        ): TootApiResult? {
            val result = TootApiResult.makeWithCaption(apiHost?.pretty)
            if (result.error != null) return result

            if (sendRequest(result) {
                    jsonObject {
                        put("dummy", 1)
                        (forceAccessToken ?: account?.misskeyApiToken)
                            ?.notEmpty()?.let { put("i", it) }
                    }.toPostRequestBuilder()
                        .url("https://${apiHost?.ascii}/api/meta")
                        .build()
                }
            ) {
                parseJson(result) ?: return null
                if (result.response?.isSuccessful == true) {
                    result.jsonObject?.apply {
                        val m = reDigits.matcher(string("version") ?: "")
                        if (m.find()) {
                            put(TootApiClient.KEY_MISSKEY_VERSION, max(1, m.groupEx(1)!!.toInt()))
                        }

                        // add endpoints
                        val r2 = getMisskeyEndpoints(forceAccessToken)
                        r2?.jsonArray?.let { result.jsonObject?.put("_endpoints", it) }
                    }
                }
            }
            return result
        }

        // 疑似アカウントの追加時に、インスタンスの検証を行う
        private suspend fun TootApiClient.getInstanceInformation(
            forceAccessToken: String? = null,
        ): TootApiResult? {

            // misskeyのインスタンス情報を読めたら、それはmisskeyのインスタンス
            val r2 = getInstanceInformationMisskey(forceAccessToken) ?: return null
            if (r2.jsonObject != null) return r2
            when (r2.response?.code) {
                null, 404 -> Unit // fall
                else -> return r2
            }

            // マストドンのインスタンス情報を読めたら、それはマストドンのインスタンス
            // インスタンス情報を読めない場合もホワイトリストモードの問題があるので
            // マストドン側のエラーを返す
            return getInstanceInformationMastodon(forceAccessToken)
        }

        /**
         * TootInstance.get() のエラー戻り値を作る
         */
        private fun tiError(errMsg: String) =
            Pair<TootInstance?, TootApiResult?>(null, TootApiResult(errMsg))

        /**
         * サーバ情報リクエスト
         * - ホスト別のキューで実行する
         */
        class QueuedRequest(
            var cont: Continuation<Pair<TootInstance?, TootApiResult?>>,
            val allowPixelfed: Boolean,
            val get: suspend (cached: TootInstance?) -> Pair<TootInstance?, TootApiResult?>,
        )

        /**
         * ホスト別のインスタンス情報キャッシュと処理キュー
         */
        class CacheEntry(
            val hostLower: String,
        ) {
            // インスタンス情報のキャッシュ
            var cacheData: TootInstance? = null

            // ホストごとに同時に1つしか実行しない、インスタンス情報更新キュー
            val requestQueue = Channel<QueuedRequest>(capacity = Channel.UNLIMITED)

            private suspend fun handleRequest(req: QueuedRequest) = try {
                val qrr = req.get(cacheData)
                qrr.first?.let { cacheData = it }

                when {
                    qrr.first?.instanceType == InstanceType.Pixelfed &&
                            !PrefB.bpEnablePixelfed() &&
                            !req.allowPixelfed ->
                        tiError("currently Pixelfed instance is not supported.")
                    else -> qrr
                }
            } catch (ex: Throwable) {
                log.e(ex, "handleRequest failed.")
                tiError(ex.withCaption("can't get server information."))
            }

            init {
                launchDefault {
                    while (true) {
                        try {
                            val req = requestQueue.receive()
                            val r = try {
                                withTimeout(30000L) {
                                    handleRequest(req)
                                }
                            } catch (ex: Throwable) {
                                log.e(ex, "handleRequest failed.")
                                tiError(ex.withCaption("handleRequest failed."))
                            }
                            runCatching { req.cont.resumeWith(Result.success(r)) }
                        } catch (ex: Throwable) {
                            log.e(ex, "requestQueue.take failed.")
                            delay(3000L)
                        }
                    }
                }
            }
        }

        private val _hostCache = HashMap<String, CacheEntry>()

        private fun Host.getCacheEntry(): CacheEntry =
            synchronized(_hostCache) {
                val hostLower = ascii.lowercase()
                var item = _hostCache[hostLower]
                if (item == null) {
                    item = CacheEntry(hostLower)
                    _hostCache[hostLower] = item
                }
                item
            }

        // get from cache
        // no request, no expiration check
        fun getCached(apiHost: String) = Host.parse(apiHost).getCacheEntry().cacheData
        fun getCached(apiHost: Host) = apiHost.getCacheEntry().cacheData
        fun getCached(a: SavedAccount?) = a?.apiHost?.getCacheEntry()?.cacheData

        suspend fun get(client: TootApiClient): Pair<TootInstance?, TootApiResult?> = getEx(client)

        suspend fun getEx(
            client: TootApiClient,
            hostArg: Host? = null,
            account: SavedAccount? = null,
            allowPixelfed: Boolean = false,
            forceUpdate: Boolean = false,
            forceAccessToken: String? = null, // マストドンのwhitelist modeでアカウント追加時に必要
        ): Pair<TootInstance?, TootApiResult?> {
            try {
                val cacheEntry = (hostArg ?: account?.apiHost ?: client.apiHost)?.getCacheEntry()
                    ?: return tiError("missing host.")

                return withTimeout(30000L) {
                    suspendCoroutine { cont ->
                        QueuedRequest(cont, allowPixelfed) { cached ->

                            // may use cached item.
                            if (!forceUpdate && forceAccessToken == null && cached != null) {
                                val now = SystemClock.elapsedRealtime()
                                if (now - cached.time_parse <= EXPIRE) {
                                    return@QueuedRequest Pair(cached, TootApiResult())
                                }
                            }

                            val tmpInstance = client.apiHost
                            val tmpAccount = client.account

                            val linkHelper: LinkHelper?

                            // get new information
                            val result = when {

                                // ストリームマネジャから呼ばれる
                                account != null -> try {
                                    linkHelper = account
                                    client.account = account // this may change client.apiHost
                                    if (account.isMisskey) {
                                        client.getInstanceInformationMisskey()
                                    } else {
                                        client.getInstanceInformationMastodon()
                                    }
                                } finally {
                                    client.account = tmpAccount
                                    client.apiHost = tmpInstance // must be last.
                                }

                                // サーバ情報カラムやProfileDirectoryを開く場合
                                hostArg != null && hostArg != tmpInstance -> try {
                                    linkHelper = null
                                    client.account = null // don't use access token.
                                    client.apiHost = hostArg
                                    client.getInstanceInformation()
                                } finally {
                                    client.account = tmpAccount
                                    client.apiHost = tmpInstance // must be last.
                                }

                                // client にすでにあるアクセス情報でサーバ情報を取得する
                                // マストドンのホワイトリストモード用にアクセストークンを指定できる
                                else -> {
                                    linkHelper = client.account // may null
                                    client.getInstanceInformation(
                                        forceAccessToken = forceAccessToken
                                    )
                                }
                            }

                            val json = result?.jsonObject
                                ?: return@QueuedRequest Pair(null, result)

                            val item = parseItem(
                                ::TootInstance,
                                TootParser(
                                    client.context,
                                    linkHelper = linkHelper ?: LinkHelper.create(
                                        (hostArg ?: client.apiHost)!!,
                                        misskeyVersion = parseMisskeyVersion(json)
                                    )
                                ),
                                json
                            ) ?: return@QueuedRequest Pair(
                                null,
                                result.setError("instance information parse error.")
                            )

                            Pair(item, result)
                        }.let {
                            val result = cacheEntry.requestQueue.trySend(it)
                            when {
                                // 誰も閉じないので発生しない
                                result.isClosed -> error("cacheEntry.requestQueue closed")
                                // capacity=UNLIMITEDなので発生しない
                                result.isFailure -> error("cacheEntry.requestQueue failed")
                            }
                        }
                    }
                }
            } catch (ex: Throwable) {
                log.w(ex, "getEx failed.")
                return tiError(ex.withCaption("can't get instance information"))
            }
        }
    }

    val isMastodon get() = instanceType == InstanceType.Mastodon
}
