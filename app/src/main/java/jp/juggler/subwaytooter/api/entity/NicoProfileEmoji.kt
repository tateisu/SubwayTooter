package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.data.JsonObject

class NicoProfileEmoji(
    val url: String,
    private val shortcode: String,
    @Suppress("unused") private val account_url: String?,
    @Suppress("unused") private val account_id: EntityId,
) : Mappable<String> {

    constructor(src: JsonObject, shortcode: String? = null) : this(
        url = src.stringOrThrow("url"),
        shortcode = shortcode ?: src.stringOrThrow("shortcode"),
        account_url = src.string("account_url"),
        account_id = EntityId.mayDefault(src.string("account_id"))
    )

    constructor(src: JsonObject) : this(src, null)

    override val mapKey: String
        get() = shortcode
}
