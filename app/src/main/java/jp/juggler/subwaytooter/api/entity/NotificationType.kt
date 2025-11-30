package jp.juggler.subwaytooter.api.entity

sealed class NotificationType(
    val code: String,
    val hideByFavMute: Boolean = false,
) {

    // 未知の通知
    class Unknown(c: String) : NotificationType(c)

    // 言及と返信
    data object Mention : NotificationType("mention")// Mastodon, Misskey
    data object Reply : NotificationType("reply")  // Misskey (メンションとReplyは別の物らしい

    // ブーストとリノート
    data object Reblog : NotificationType(
        "reblog",
        hideByFavMute = true,
    ) // Mastodon

    // Misskeyの（引用ではない)リノート
    data object Renote : NotificationType(
        "renote",
        hideByFavMute = true,
    )

    // Misskeyの引用Renote fedibirdのquote
    data object Quote : NotificationType(
        "quote",
        hideByFavMute = true,
    )

    // フォロー Mastodon, Misskey
    data object Follow : NotificationType(
        "follow",
        hideByFavMute = true,
    )

    // アンフォロー Mastodon, Misskey (いやMastodonにはなかった気が…)
    data object Unfollow : NotificationType(
        "unfollow",
        hideByFavMute = true,
    )

    data object Favourite : NotificationType(
        "favourite",
        hideByFavMute = true,
    )

    data object FollowRequest : NotificationType(
        "follow_request",
        hideByFavMute = true,
    )

    data object FollowRequestMisskey : NotificationType(
        "receiveFollowRequest",
        hideByFavMute = true,
    )

    data object FollowRequestAcceptedMisskey : NotificationType(
        "followRequestAccepted",
        hideByFavMute = true,
    )

    data object PollVoteMisskey : NotificationType(
        "pollVote",
        hideByFavMute = true,
    )

    // 投票
    data object Vote : NotificationType(
        "poll_vote",
        hideByFavMute = true,
    )

    // (Mastodon 2.8)投票完了
    data object Poll : NotificationType(
        "poll",
        hideByFavMute = true,
    )

    // Mastodon 3.3 指定ユーザからの投稿
    data object Status : NotificationType("status")

    // (Mastodon 3.5.0rc1)
    data object Update : NotificationType("update")
    data object AdminSignup : NotificationType("admin.sign_up")

    // (Mastodon 4.0)
    data object AdminReport : NotificationType("admin.report")

    // (Mastodon 4.3) 断絶した関係
    data object SeveredRelationships : NotificationType("severed_relationships")

    // -------------------------------------------------
    // 絵文字リアクション

    // misskey
    data object Reaction : NotificationType(
        "reaction",
        hideByFavMute = true,
    )

    // Fedibird
    data object EmojiReactionFedibird : NotificationType(
        "emoji_reaction",
        hideByFavMute = true,
    )

    // pleroma
    data object EmojiReactionPleroma : NotificationType(
        "pleroma:emoji_reaction",
        hideByFavMute = true,
    )

    // ---------------------------------------------------
    // 他のFedibird拡張
    // https://github.com/fedibird/mastodon/blob/fedibird/app/controllers/api/v1/push/subscriptions_controller.rb#L55
    // https://github.com/fedibird/mastodon/blob/fedibird/app/models/notification.rb
    data object StatusReference : NotificationType("status_reference")
    data object ScheduledStatus : NotificationType("scheduled_status")

    companion object {
        val allKnown by lazy {
            NotificationType::class.sealedSubclasses.mapNotNull { it.objectInstance }
        }
        val map by lazy {
            allKnown.associateBy { it.code }
        }

        fun String.toNotificationType() = map[this] ?: Unknown(this)
    }
}
