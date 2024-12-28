package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.entity.RelationshipSeveranceEventType.Companion.toRelationshipSeveranceEventType
import jp.juggler.util.data.JsonObject

// https://docs.joinmastodon.org/entities/RelationshipSeveranceEvent/
// フォロー関係が切断される原因となったモデレーションまたはブロック イベントの概要。
class RelationshipSeveranceEvent(
    // データベース内の関係切断イベントの ID
    val id: EntityId,
    // イベントが発生した日時。
    val timeCreatedAt: Long,
    // イベントのタイプ
    val type: RelationshipSeveranceEventType,
    // 根本的な問題が削除されたために切断された関係のリストが利用できないなら真
    val purged: Boolean? = null,
    // モデレーション/ブロック イベントのターゲットの名前。
    // イベントの種類に応じて、ドメイン名またはユーザー ハンドルのいずれかになります。
    val targetName: String? = null,
    // イベントの結果として削除されたフォロワーの数。
    val followersCount: Int? = null,
    // イベントの結果としてユーザーがフォローを停止したアカウントの数。
    val followingCount: Int? = null,
) {
    companion object {
        fun JsonObject.parseTootNotififcationEvent(): RelationshipSeveranceEvent? {
            val type = string("type")?.toRelationshipSeveranceEventType()
            return when (type) {
                null -> null
                else -> RelationshipSeveranceEvent(
                    id = EntityId.mayDefault(string("id")),
                    timeCreatedAt = TootStatus.parseTime(string("created_at")),
                    type = type,
                    purged = boolean("purged"),
                    targetName = string("target_name"),
                    followersCount = int("followers_count"),
                    followingCount = int("following_count"),
                )
            }
        }
    }
}
