package jp.juggler.subwaytooter.api

object ApiPath {
     const val READ_LIMIT = 80 // API側の上限が80です。ただし指定しても40しか返ってこないことが多い
    // ステータスのリストを返すAPI
     const val PATH_DIRECT_MESSAGES = "/api/v1/timelines/direct?limit=$READ_LIMIT"
     const val PATH_DIRECT_MESSAGES2 = "/api/v1/conversations?limit=$READ_LIMIT"

     const val PATH_FAVOURITES = "/api/v1/favourites?limit=$READ_LIMIT"
     const val PATH_BOOKMARKS = "/api/v1/bookmarks?limit=$READ_LIMIT"

    // アカウントのリストを返すAPI
     const val PATH_ACCOUNT_FOLLOWING =
        "/api/v1/accounts/%s/following?limit=$READ_LIMIT" // 1:account_id
     const val PATH_ACCOUNT_FOLLOWERS =
        "/api/v1/accounts/%s/followers?limit=$READ_LIMIT" // 1:account_id
     const val PATH_MUTES = "/api/v1/mutes?limit=$READ_LIMIT"
     const val PATH_BLOCKS = "/api/v1/blocks?limit=$READ_LIMIT"
     const val PATH_FOLLOW_REQUESTS = "/api/v1/follow_requests?limit=$READ_LIMIT"
     const val PATH_FOLLOW_SUGGESTION = "/api/v1/suggestions?limit=$READ_LIMIT"
     const val PATH_FOLLOW_SUGGESTION2 = "/api/v2/suggestions?limit=$READ_LIMIT"
     const val PATH_ENDORSEMENT = "/api/v1/endorsements?limit=$READ_LIMIT"

     const val PATH_PROFILE_DIRECTORY = "/api/v1/directory?limit=$READ_LIMIT"

     const val PATH_BOOSTED_BY =
        "/api/v1/statuses/%s/reblogged_by?limit=$READ_LIMIT" // 1:status_id
     const val PATH_FAVOURITED_BY =
        "/api/v1/statuses/%s/favourited_by?limit=$READ_LIMIT" // 1:status_id
     const val PATH_LIST_MEMBER = "/api/v1/lists/%s/accounts?limit=$READ_LIMIT"

    // 他のリストを返すAPI
     const val PATH_REPORTS = "/api/v1/reports?limit=$READ_LIMIT"
     const val PATH_NOTIFICATIONS = "/api/v1/notifications?limit=$READ_LIMIT"
     const val PATH_DOMAIN_BLOCK = "/api/v1/domain_blocks?limit=$READ_LIMIT"
     const val PATH_LIST_LIST = "/api/v1/lists?limit=$READ_LIMIT"
     const val PATH_SCHEDULED_STATUSES = "/api/v1/scheduled_statuses?limit=$READ_LIMIT"

    // リストではなくオブジェクトを返すAPI
     const val PATH_STATUSES = "/api/v1/statuses/%s" // 1:status_id
     const val PATH_STATUSES_CONTEXT = "/api/v1/statuses/%s/context" // 1:status_id
    // search args 1: query(urlencoded) , also, append "&resolve=1" if resolve non-local accounts

    const val PATH_FILTERS = "/api/v1/filters"

    const val PATH_MISSKEY_PROFILE_FOLLOWING = "/api/users/following"
    const val PATH_MISSKEY_PROFILE_FOLLOWERS = "/api/users/followers"
    const val PATH_MISSKEY_PROFILE_STATUSES = "/api/users/notes"

    const val PATH_MISSKEY_MUTES = "/api/mute/list"
    const val PATH_MISSKEY_BLOCKS = "/api/blocking/list"
    const val PATH_MISSKEY_FOLLOW_REQUESTS = "/api/following/requests/list"
    const val PATH_MISSKEY_FOLLOW_SUGGESTION = "/api/users/recommendation"
    const val PATH_MISSKEY_FAVORITES = "/api/i/favorites"
}