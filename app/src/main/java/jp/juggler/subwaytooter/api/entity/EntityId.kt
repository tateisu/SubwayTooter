package jp.juggler.subwaytooter.api.entity

import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.getStringOrNull
import jp.juggler.util.data.notZero
import jp.juggler.util.string
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class EntityId(val x: String) : Comparable<EntityId> {

    companion object {

        // IDのパース失敗時にエラー扱いしたくない場合に使われる
        internal val DEFAULT = EntityId("<>default")

        // マストドンのアカウント作成APIで作成した直後は「IDが発行されてない」状態になる
        internal val CONFIRMING = EntityId("<>confirming")

        fun mayDefault(x: String?) = if (x == null) DEFAULT else EntityId(x)

        fun mayNull(x: String?) = if (x == null) null else EntityId(x)

        fun String.decodeEntityId(): EntityId? {
            if (this.isEmpty()) return null
            // first character is 'L' for EntityIdLong, 'S' for EntityIdString.
            // integer id is removed at https://source.joinmastodon.org/mastodon/docs/commit/e086d478afa140e7b0b9a60183655315966ad9ff
            return EntityId(this.substring(1))
        }

        fun entityId(intent: Intent?, key: String) =
            intent?.string(key)?.decodeEntityId()

        fun entityId(bundle: Bundle?, key: String) =
            bundle?.string(key)?.decodeEntityId()

        // 内部保存データのデコード用。APIレスポンスのパースに使ってはいけない
        fun entityId(data: JsonObject?, key: String): EntityId? {
            val o = data?.get(key)
            if (o is Long) return EntityId(o.toString())
            return (o as? String)?.decodeEntityId()
        }

        fun entityId(cursor: Cursor, key: String) =
            cursor.getStringOrNull(key)?.decodeEntityId()
    }

    // 昔は文字列とLong値を区別していたが、今はもうない
    fun encode(): String = "S$this"

    fun putTo(data: Intent, key: String): Intent = data.putExtra(key, encode())

    fun putTo(bundle: Bundle, key: String) = bundle.putString(key, encode())

    fun putTo(data: JsonObject, key: String) = data.apply {
        put(key, encode())
    }

    fun putTo(cv: ContentValues, key: String) {
        cv.put(key, encode())
    }

    val isDefault: Boolean
        get() = this == DEFAULT

    val notDefault: Boolean
        get() = this != DEFAULT

    val notDefaultOrConfirming: Boolean
        get() = this != DEFAULT && this != CONFIRMING

    override fun compareTo(other: EntityId): Int =
        (x.length - other.x.length).notZero() ?: x.compareTo(other.x)

    override fun equals(other: Any?): Boolean = if (other is EntityId) {
        x == other.x
    } else {
        false
    }

    override fun hashCode() = x.hashCode()

    override fun toString() = x

    fun isNewerThan(previous: EntityId?) = when (previous) {
        null -> true
        else -> this > previous
    }
}

fun EntityId?.putMayNull(cv: ContentValues, key: String) {
    if (this == null) {
        cv.putNull(key)
    } else {
        this.putTo(cv, key)
    }
}

object EntityIdSerializer : KSerializer<EntityId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("EntityId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: EntityId) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): EntityId =
        EntityId(decoder.decodeString())
}
