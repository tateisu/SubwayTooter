package jp.juggler.subwaytooter.api.finder

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.api.entity.TootAccountRef.Companion.tootAccountRefOrNull
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.data.JsonArray
import jp.juggler.util.data.JsonObject

val nullArrayFinder: (JsonObject) -> JsonArray? =
    { null }

val misskeyArrayFinderUsers = { it: JsonObject ->
    it.jsonArray("users")
}

////////////////////////////////////////////////////////////////////////////////
// account list parser

val defaultAccountListParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootAccountRef> =
    { parser, jsonArray -> parser.accountRefList(jsonArray) }

private fun misskeyUnwrapRelationAccount(parser: TootParser, srcList: JsonArray, key: String) =
    srcList.objectList().mapNotNull {
        when (val relationId = EntityId.mayNull(it.string("id"))) {
            null -> null
            else -> tootAccountRefOrNull(parser, parser.account(it.jsonObject(key)))
                ?.apply { _orderId = relationId }
        }
    }

val misskey11FollowingParser: (TootParser, JsonArray) -> List<TootAccountRef> =
    { parser, jsonArray -> misskeyUnwrapRelationAccount(parser, jsonArray, "followee") }

val misskey11FollowersParser: (TootParser, JsonArray) -> List<TootAccountRef> =
    { parser, jsonArray -> misskeyUnwrapRelationAccount(parser, jsonArray, "follower") }

val misskeyCustomParserFollowRequest: (TootParser, JsonArray) -> List<TootAccountRef> =
    { parser, jsonArray -> misskeyUnwrapRelationAccount(parser, jsonArray, "follower") }

val misskeyCustomParserMutes: (TootParser, JsonArray) -> List<TootAccountRef> =
    { parser, jsonArray -> misskeyUnwrapRelationAccount(parser, jsonArray, "mutee") }

val misskeyCustomParserBlocks: (TootParser, JsonArray) -> List<TootAccountRef> =
    { parser, jsonArray -> misskeyUnwrapRelationAccount(parser, jsonArray, "blockee") }

////////////////////////////////////////////////////////////////////////////////
// status list parser

val defaultStatusListParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootStatus> =
    { parser, jsonArray -> parser.statusList(jsonArray) }

val misskeyCustomParserFavorites: (TootParser, JsonArray) -> List<TootStatus> =
    { parser, jsonArray ->
        jsonArray.objectList().mapNotNull {
            when (val relationId = EntityId.mayNull(it.string("id"))) {
                null -> null
                else -> parser.status(it.jsonObject("note"))?.apply {
                    favourited = true
                    _orderId = relationId
                }
            }
        }
    }

////////////////////////////////////////////////////////////////////////////////
// notification list parser

val defaultNotificationListParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootNotification> =
    { parser, jsonArray -> parser.notificationList(jsonArray) }

val defaultDomainBlockListParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootDomainBlock> =
    { _, jsonArray -> TootDomainBlock.parseList(jsonArray) }

val defaultReportListParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootReport> =
    { _, jsonArray -> parseList(jsonArray) { TootReport(it) } }

val defaultConversationSummaryListParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootConversationSummary> =
    { parser, jsonArray ->
        parseList(jsonArray) { TootConversationSummary(parser, it) }
    }

///////////////////////////////////////////////////////////////////////

val mastodonFollowSuggestion2ListParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootAccountRef> =
    { parser, jsonArray ->
        TootAccountRef.wrapList(parser,
            jsonArray.objectList().mapNotNull {
                parser.account(it.jsonObject("account"))?.also { a ->
                    SuggestionSource.set(
                        (parser.linkHelper as? SavedAccount)?.db_id,
                        a.acct,
                        it.string("source")
                    )
                }
            }
        )
    }
