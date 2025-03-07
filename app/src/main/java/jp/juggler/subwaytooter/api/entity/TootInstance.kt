package jp.juggler.subwaytooter.api.entity

import android.os.SystemClock
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.auth.AuthBase
import jp.juggler.subwaytooter.api.entity.TootAccount.Companion.tootAccount
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.subwaytooter.util.VersionString
import jp.juggler.util.coroutine.AppDispatchers.withTimeoutSafe
import jp.juggler.util.coroutine.launchDefault
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.asciiPattern
import jp.juggler.util.data.buildJsonObject
import jp.juggler.util.data.groupEx
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.withCaption
import jp.juggler.util.network.toPostRequestBuilder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import okhttp3.Request
import java.util.regex.Pattern
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine
import kotlin.math.max



class TootInstance(parser: TootParser, src: JsonObject) {

    // いつ取得したか(内部利用)
    private var time_parse: Long = SystemClock.elapsedRealtime()

    val isExpired: Boolean
        get() = SystemClock.elapsedRealtime() - time_parse >= EXPIRE

    // サーバのAPIホスト
    val apiHost: Host = parser.apiHost

    //	URI of the current instance
    // apiHost ではなく apDomain を示す
    val apDomain: Host

    //	The instance's title
    val title: String?

    //	A description for the instance
    // (HTML)
    // (Mastodon: 3.0.0より後のWebUIでは全く使われなくなる見込み。 https://github.com/tootsuite/mastodon/pull/12119)
    val descriptionOld: String?

    // (Mastodon 3.0.0以降)
    // (HTML)
    val description: String?

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

    var fedibirdCapabilities: Set<String>? = null

    var misskeyEndpoints: Set<String>? = null

    var pleromaFeatures: Set<String>? = null

    var configuration: JsonObject? = null

    var urls: JsonObject? = null

    init {
        if (parser.serviceType == ServiceType.MISSKEY) {

            this.misskeyEndpoints = src.jsonArray("_endpoints")?.stringList()?.toSet()

            // Misskeyは apiHost と apDomain の区別がない
            this.apDomain = parser.apiHost
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
            this.descriptionOld = null
            this.approval_required = false

            this.feature_quote = true

            this.invites_enabled = null
        } else {
            this.apDomain =
                    // mastodon /api/v2/instance
                src.string("domain")?.let { Host.parse(it) }
                        // mastodon /api/v1/instance
                    ?: src.string("uri")?.let { Host.parse(it) }
                            ?: parser.apDomain

            this.title = src.string("title")

            val sv =
                // mastodon /api/v2/instance
                src.jsonObject("contact")?.string("email")
                // mastodon /api/v1/instance
                    ?: src.string("email")
            this.email = when {
                sv?.startsWith("mailto:") == true -> sv.substring(7)
                else -> sv
            }

            this.version = src.string("version")
            this.decoded_version = VersionString(version)
            this.stats = parseItem(src.jsonObject("stats")) { Stats(it) }
            this.thumbnail =
                    // mastodon  /api/v2/instance
                src.jsonObject("thumbnail")
                    ?.jsonObject("versions")?.string("@2x")
                        // mastodon  /api/v2/instance
                    ?: src.jsonObject("thumbnail")
                        ?.string("url")
                            // mastodon  /api/v1/instance
                            ?: src.string("thumbnail")

            this.max_toot_chars = src.int("max_toot_chars")

            this.instanceType = when {
                rePleroma.matcher(version ?: "").find() -> InstanceType.Pleroma
                rePixelfed.matcher(version ?: "").find() -> InstanceType.Pixelfed
                else -> InstanceType.Mastodon
            }

            languages = src.jsonArray("languages")?.stringArrayList()

            contact_account = parseItem(
                // mastodon /api/v2/instance
                src.jsonObject("contact")?.jsonObject("account")
                // mastodon /api/v1/instance
                    ?: src.jsonObject("contact_account")
            ) {
                tootAccount(
                    TootParser(
                        parser.context,
                        LinkHelper.create(
                            apiHostArg = apiHost,
                            apDomainArg = apDomain,
                            misskeyVersion = 0,
                        )
                    ),
                    it
                )
            }

            when (val shortDesc = src.string("short_description")) {
                // /api/v2/instance
                null -> {
                    this.descriptionOld = null
                    this.description = src.string("description")
                }
                // /api/v1/instance
                else -> {
                    this.descriptionOld = src.string("description")
                    this.description = shortDesc
                }
            }

            this.approval_required =
                    // mastodon /api/v2/instance
                src.jsonObject("registrations")?.boolean("approval_required")
                        // mastodon /api/v1/instance
                    ?: src.boolean("approval_required")
                            // default
                            ?: false

            this.feature_quote = src.boolean("feature_quote") ?: false

            this.invites_enabled =
                    // removed on /api/v2/instance
                    // mastodon /api/v1/instance 3.1.4
                src.boolean("invites_enabled")

            this.fedibirdCapabilities =
                src.jsonArray("fedibird_capabilities")?.stringList()?.toSet()
            this.pleromaFeatures =
                src.jsonObject("pleroma")?.jsonObject("metadata")?.jsonArray("features")
                    ?.stringList()?.toSet()

            this.configuration = src.jsonObject("configuration")
            this.urls =
                    // mastodon /api/v2/instance
                src.jsonObject("configuration")?.jsonObject("urls")
                        //  mastodon /api/v1/instance
                    ?: src.jsonObject("urls")
        }
    }

    class Stats(src: JsonObject) {
        val user_count = src.long("user_count") ?: -1L
        val status_count = src.long("status_count") ?: -1L
        val domain_count = src.long("domain_count") ?: -1L
    }

    val misskeyVersionMajor: Int
        get() = when {
            instanceType != InstanceType.Misskey -> 0
            else -> decoded_version.majorVersion ?: 10
        }

    val canUseReference: Boolean?
        get() = fedibirdCapabilities?.contains("status_reference")

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
        val VERSION_4_0_0 = VersionString("4.0.0")
        val VERSION_4_3_0 = VersionString("4.3.0")

        val MISSKEY_VERSION_11 = VersionString("11.0")
        val MISSKEY_VERSION_12 = VersionString("12.0")
        val MISSKEY_VERSION_12_75_0 = VersionString("12.75.0")
        val MISSKEY_VERSION_13 = VersionString("13.0")

        private val reDigits = """(\d+)""".asciiPattern()

        private const val EXPIRE = (1000 * 3600).toLong()

        const val DESCRIPTION_DEFAULT = "(no description)"

        // 引数はtoken_infoかTootInstanceのパース前のいずれか
        private fun parseMisskeyVersion(tokenInfo: JsonObject): Int {
            log.i("parseMisskeyVersion ${AuthBase.KEY_MISSKEY_VERSION}=${tokenInfo[AuthBase.KEY_MISSKEY_VERSION]}, (version)=${tokenInfo["version"]}")
            return when (val o = tokenInfo[AuthBase.KEY_MISSKEY_VERSION]) {
                is Int -> tokenInfo.string("version")?.let { VersionString(it).majorVersion } ?: o
                is Boolean -> if (o) 10 else 0
                else -> 0
            }
        }

        // 疑似アカウントの追加時に、インスタンスの検証を行う
        private suspend fun TootApiClient.getInstanceInformationMastodon(
            forceAccessToken: String? = null,
        ): TootApiResult? {
            var lastError: TootApiResult? = null
            for (path in arrayOf("/api/v2/instance", "/api/v1/instance")) {
                val result = TootApiResult.makeWithCaption(apiHost)
                if (result.error != null) return result
                val url = "https://${apiHost?.ascii}$path"
                if (sendRequest(result) {
                        val builder = Request.Builder().url(url)
                        (forceAccessToken ?: account?.bearerAccessToken)?.notEmpty()?.let {
                            builder.header("Authorization", "Bearer $it")
                        }
                        builder.build()
                    }) {
                    parseJson(result) ?: return null // cancelled.
                    result.jsonObject?.let { json ->
                        json.jsonObject("configuration")?.put("!instanceApiUrl", url)
                        return result
                    }
                }
                lastError = result
            }
            return lastError!!
        }

        private suspend fun TootApiClient.getMisskeyEndpoints(
            forceAccessToken: String? = null,
        ): TootApiResult? {
            val result = TootApiResult.makeWithCaption(apiHost)
            if (result.error != null) return result

            if (sendRequest(result) {
                    buildJsonObject {
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
            val result = TootApiResult.makeWithCaption(apiHost)
            if (result.error != null) return result

            if (sendRequest(result) {
                    buildJsonObject {
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
                            put(AuthBase.KEY_MISSKEY_VERSION, max(1, m.groupEx(1)!!.toInt()))
                        }

                        // add endpoints
                        val r2 = getMisskeyEndpoints(forceAccessToken)
                        r2?.jsonArray?.let { result.jsonObject?.put("_endpoints", it) }
                    }
                }
            }
            return result
        }

        /*
        Misskeyは昔/api/v1/instance に応答していたことがある。
        - Oct 16, 2018 https://github.com/misskey-dev/misskey/pull/2913
        - Oct 31, 2018 https://github.com/misskey-dev/misskey/pull/3045 ここから
        - Jan 31, 2019 https://github.com/misskey-dev/misskey/pull/4061 ここまで
        */
        private val reOldMisskeyCompatible = """\A[\d.]+:compatible:misskey:""".toRegex()

        private val reBothCompatible =
            """\b(?:misskey|calckey)\b""".toRegex(RegexOption.IGNORE_CASE)

        // 疑似アカウントの追加時に、インスタンスの検証を行う
        private suspend fun TootApiClient.getInstanceInformation(
            forceAccessToken: String? = null,
        ): TootApiResult? {
            // /api/v1/instance を読む(mastodon)
            val r1 = getInstanceInformationMastodon(forceAccessToken)
                ?: return null // cancelled

            r1.jsonObject?.string("version")?.let { version ->
                // /api/v1/instance にJSONデータが含まれてバージョン情報を読めている場合
                when {
                    // 古いMisskeyがMastodon互換APIを返す事があるが、Mastodon互換だと思ってはいけない
                    reOldMisskeyCompatible.containsMatchIn(version) -> Unit

                    // 他、Mastodonではない場合は kids.0px.io が存在する
                    // https://kids.0px.io/notes/9b628dpesb
                    // Misskey有効トグルで結果を切り替えたいらしい
                    //
                    // Calckey.jp は調査中
                    reBothCompatible.containsMatchIn(version) &&
                            PrefB.bpEnableDeprecatedSomething.value -> Unit

                    // 両方のAPIに応答するサーバは他にないと思う。
                    // /api/v1/instance でJSONデータを読めてるならそれを返す
                    else -> return r1
                }
            }

            // /api/meta を読む (misskey)
            val r2 = getInstanceInformationMisskey(forceAccessToken)
                ?: return null // cancelled

            r2.jsonObject?.let { return r2 }

            // 両方読めなかった場合
            return when (r1.response?.code) {

                // /api/v1/instance が404を返したらMisskeyのエラー応答を返す
                404 -> r2

                // /api/v1/instance が200を返すがJsonObjectではない場合
                // それは Misskeyが/api/v1/instance Jsonではない応答を返している
                200 -> r2

                // /api/v1/instance 401を返した場合、それはMastodonのホワイトリストモードだろう
                // Mastoronのエラー結果を返す。
                401 -> r1

                // その他の場合
                // Mastoronのエラー結果を返す。
                else -> r1
            }
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
                            !PrefB.bpEnablePixelfed.value &&
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
                                withTimeoutSafe(30000L) {
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

        suspend fun get(client: TootApiClient): Pair<TootInstance?, TootApiResult?> =
            getEx(client)

        suspend fun getOrThrow(client: TootApiClient): TootInstance {
            val (ti, ri) = get(client)
            return ti ?: error("can't get server information. ${ri?.error}")
        }

        suspend fun getExOrThrow(
            client: TootApiClient,
            hostArg: Host? = null,
            account: SavedAccount? = null,
            allowPixelfed: Boolean = false,
            forceUpdate: Boolean = false,
            forceAccessToken: String? = null, // マストドンのwhitelist modeでアカウント追加時に必要
        ): TootInstance {
            val (ti, ri) = getEx(
                client = client,
                hostArg = hostArg,
                account = account,
                allowPixelfed = allowPixelfed,
                forceUpdate = forceUpdate,
                forceAccessToken = forceAccessToken,
            )
            return ti ?: error("can't get server information. ${ri?.error}")
        }

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

                return withTimeoutSafe(30000L) {
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

                            val item = parseItem(json) {
                                TootInstance(
                                    TootParser(
                                        client.context,
                                        linkHelper = linkHelper ?: LinkHelper.create(
                                            (hostArg ?: client.apiHost)!!,
                                            misskeyVersion = parseMisskeyVersion(json)
                                        )
                                    ),
                                    it
                                )
                            } ?: return@QueuedRequest Pair(
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
    val isMisskey get() = misskeyVersionMajor > 0
}
