package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.buildJsonObject

/**
 * 単語フィルタのキーワード一つ。
 * 編集画面でも使うのでmutable
 */
class TootFilterKeyword(
    // v1 has no id
    var id: EntityId? = null,
    var keyword: String,
    var whole_word: Boolean = true,
) {
    companion object {
        private const val JSON_ID = "id"
        private const val JSON_KEYWORD = "keyword"
        private const val JSON_WHOLE_WORD = "whole_word"
    }

    // from Mastodon api/v2/filter
    constructor(src: JsonObject) : this(
        id = EntityId.mayNull(src.string(JSON_ID)),
        keyword = src.string(JSON_KEYWORD) ?: "",
        whole_word = src.boolean(JSON_WHOLE_WORD) ?: true,
    )

    fun encodeNewParam(
        newKeyword: String,
        newWholeWord: Boolean,
    ) = buildJsonObject {
        put(JSON_ID, id.toString())
        put(JSON_KEYWORD, newKeyword)
        put(JSON_WHOLE_WORD, newWholeWord)
    }
}
