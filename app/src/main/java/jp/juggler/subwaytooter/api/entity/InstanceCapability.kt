package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.table.SavedAccount

// インスタンスの能力
// TootInstanceで主に使われる
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
        ti?.fedibirdCapabilities?.contains("visibility_mutual") == true

    fun visibilityLimited(ti: TootInstance?) =
        ti?.fedibirdCapabilities?.contains("visibility_limited") == true

    fun statusReference(ai: SavedAccount, ti: TootInstance?) =
        when {
            ai.isPseudo -> false
            ai.isMisskey -> false
            else -> ti?.fedibirdCapabilities?.contains("status_reference") == true
        }

    fun canEmojiReaction(ai: SavedAccount, ti: TootInstance? = TootInstance.getCached(ai)) =
        when {
            ai.isPseudo -> false
            ai.isMisskey -> true
            ti?.fedibirdCapabilities?.contains("emoji_reaction") == true -> true
            ti?.pleromaFeatures?.contains("pleroma_emoji_reactions") == true -> true
            else -> false
        }

    fun canPostCustomEmojiReaction(
        ai: SavedAccount,
        ti: TootInstance? = TootInstance.getCached(ai),
    ) =
        when {
            ai.isPseudo -> false
            ai.isMisskey -> true
            ti?.fedibirdCapabilities?.contains("emoji_reaction") == true -> true
            ti?.pleromaFeatures?.contains("custom_emoji_reactions") == true -> true
            ti?.pleromaFeatures?.contains("pleroma_custom_emoji_reactions") == true -> true
            else -> false
        }

    fun maxReactionPerAccount(
        ai: SavedAccount,
        ti: TootInstance? = TootInstance.getCached(ai),
    ): Int =
        when {
            !canEmojiReaction(ai, ti) -> 0
            ai.isMisskey -> 1
            ti?.pleromaFeatures?.contains("pleroma_emoji_reactions") == true -> Int.MAX_VALUE - 10
            else ->
                ti?.configuration?.jsonObject("emoji_reactions")
                    ?.int("max_reactions_per_account")
                    ?: ti?.configuration?.int("emoji_reactions_per_account")
                    ?: 1
        }

//    fun canMultipleReaction(ai: SavedAccount, ti: TootInstance? = TootInstance.getCached(ai)) =
//        when {
//            ai.isPseudo -> false
//            ti?.pleromaFeatures?.contains("pleroma_emoji_reactions") == true -> true
//            (ti?.configuration?.int("emoji_reactions_per_account") ?: 1) > 1 -> true
//            ai.isMisskey -> false
//            else -> false
//        }

    fun listMyReactions(ai: SavedAccount, ti: TootInstance?) =
        when {
            ai.isPseudo -> false
            ai.isMisskey ->
                // m544 extension
                ti?.misskeyEndpoints?.contains("i/reactions") == true

            else ->
                // fedibird extension
                ti?.fedibirdCapabilities?.contains("emoji_reaction") == true
        }

    fun canReceiveScheduledStatus(ai: SavedAccount, ti: TootInstance?) =
        when {
            ai.isPseudo -> false
            ai.isMisskey -> false
            // 予約投稿自体はMastodonに2.7.0からある。通知はFedibird拡張
            else -> ti?.fedibirdCapabilities != null && ti.versionGE(TootInstance.VERSION_2_7_0_rc1)
        }

    fun canReceiveSeveredRelationships(ai: SavedAccount, ti: TootInstance?) =
        when {
            ai.isPseudo -> false
            ai.isMisskey -> false
            else -> ti?.versionGE(TootInstance.VERSION_4_3_0) == true
        }

    fun canReceiveEmojiReactionFedibird(ti: TootInstance?) =
        ti?.fedibirdCapabilities?.contains("emoji_reaction") == true

    fun canReceiveEmojiReactionPleroma(ti: TootInstance?) =
        ti?.pleromaFeatures?.contains("pleroma_emoji_reactions") == true

    fun canReceiveEmojiReactionAny(ti: TootInstance?) = when {
        canReceiveEmojiReactionFedibird(ti) -> true
        canReceiveEmojiReactionPleroma(ti) -> true
        else -> ti?.isMisskey == true
    }
}
