package jp.juggler.subwaytooter.api

import android.content.Context
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.api.entity.TootAccount.Companion.tootAccount
import jp.juggler.subwaytooter.api.entity.TootStatus.Companion.tootStatus
import jp.juggler.subwaytooter.span.EmojiSizeMode
import jp.juggler.subwaytooter.span.emojiSizeMode
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.util.data.JsonArray
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.WordTrieTree

class TootParser(
    val context: Context,
    val linkHelper: LinkHelper,
    var pinned: Boolean = false, // プロフィールカラムからpinned TL を読んだ時だけ真
    var highlightTrie: WordTrieTree? = null,
    var serviceType: ServiceType = when {
        linkHelper.isMisskey -> ServiceType.MISSKEY
        else -> ServiceType.MASTODON
    },
    var misskeyDecodeProfilePin: Boolean = false,
    var fromStream: Boolean = false,
    var decodeQuote: Boolean = true,
) {

    val misskeyUserRelationMap = HashMap<EntityId, UserRelation>()
    // val misskeyAccountDetailMap = HashMap<EntityId, TootAccount>()

    val apiHost: Host
        get() = linkHelper.apiHost

    val apDomain: Host
        get() = linkHelper.apDomain

    fun getFullAcct(acct: Acct?) = linkHelper.getFullAcct(acct)

    fun account(src: JsonObject?) =
        parseItem(src) { tootAccount(this, it) }

    fun accountRefList(array: JsonArray?) =
        TootAccountRef.wrapList(this, parseList(array) { tootAccount(this, it) })

    fun status(src: JsonObject?) =
        parseItem(src) { tootStatus(this, it) }
    fun statusList(array: JsonArray?) = parseList(array) { tootStatus(this, it) }

    fun notification(src: JsonObject?) = parseItem(src) { TootNotification.tootNotification(this, it) }
    fun notificationList(array: JsonArray?) =
        parseList(array) { TootNotification.tootNotification(this, it) }

    fun tag(src: JsonObject?) = src?.let { TootTag.parse(this, it) }
    fun tagList(array: JsonArray?) = TootTag.parseList(this, array)

    fun results(src: JsonObject?) =
        parseItem(src) { TootResults(this, it) }
    fun instance(src: JsonObject?) =
        parseItem(src) { TootInstance(this, it) }

    fun getMisskeyUserRelation(whoId: EntityId) = misskeyUserRelationMap[whoId]

    // ap/show の戻り値はActivityPubオブジェクトではなく、Misskeyのエンティティ
    suspend fun parseMisskeyApShow(jsonObject: JsonObject?): Any? {
        return when (jsonObject?.string("type")) {
            "Note" -> status(jsonObject.jsonObject("object"))
            "User" -> account(jsonObject.jsonObject("object"))
            else -> null
        }
    }

    val emojiSizeMode: EmojiSizeMode
        get()= (linkHelper as? SavedAccount).emojiSizeMode()
}
