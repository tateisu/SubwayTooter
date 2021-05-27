package jp.juggler.subwaytooter

import android.content.Context
import jp.juggler.subwaytooter.api.ApiPath
import jp.juggler.subwaytooter.api.ApiTask
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.table.FavMute
import jp.juggler.subwaytooter.table.HighlightWord
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.util.WordTrieTree
import jp.juggler.util.launchMain
import java.util.regex.Pattern

val Column.isFilterEnabled: Boolean
    get() = (with_attachment
        || with_highlight
        || regex_text.isNotEmpty()
        || dont_show_normal_toot
        || dont_show_non_public_toot
        || quick_filter != Column.QUICK_FILTER_ALL
        || dont_show_boost
        || dont_show_favourite
        || dont_show_follow
        || dont_show_reply
        || dont_show_reaction
        || dont_show_vote
        || (language_filter?.isNotEmpty() == true)
        )

// マストドン2.4.3rcのキーワードフィルタのコンテキスト
fun Column.getFilterContext() = when (type) {

    ColumnType.HOME, ColumnType.LIST_TL, ColumnType.MISSKEY_HYBRID -> TootFilter.CONTEXT_HOME

    ColumnType.NOTIFICATIONS, ColumnType.NOTIFICATION_FROM_ACCT -> TootFilter.CONTEXT_NOTIFICATIONS

    ColumnType.CONVERSATION -> TootFilter.CONTEXT_THREAD

    ColumnType.DIRECT_MESSAGES -> TootFilter.CONTEXT_THREAD

    ColumnType.PROFILE -> TootFilter.CONTEXT_PROFILE

    else -> TootFilter.CONTEXT_PUBLIC
    // ColumnType.MISSKEY_HYBRID や ColumnType.MISSKEY_ANTENNA_TL はHOMEでもPUBLICでもある…
    // Misskeyだし関係ないが、NONEにするとアプリ内で完結するフィルタも働かなくなる
}

// カラム設定に正規表現フィルタを含めるなら真
fun Column.canStatusFilter(): Boolean {
    if (getFilterContext() != TootFilter.CONTEXT_NONE) return true

    return when (type) {
        ColumnType.SEARCH_MSP, ColumnType.SEARCH_TS, ColumnType.SEARCH_NOTESTOCK -> true
        else -> false
    }
}

// カラム設定に「すべての画像を隠す」ボタンを含めるなら真
fun Column.canNSFWDefault(): Boolean = canStatusFilter()

// カラム設定に「ブーストを表示しない」ボタンを含めるなら真
fun Column.canFilterBoost(): Boolean = when (type) {
    ColumnType.HOME, ColumnType.MISSKEY_HYBRID, ColumnType.PROFILE,
    ColumnType.NOTIFICATIONS, ColumnType.NOTIFICATION_FROM_ACCT,
    ColumnType.LIST_TL, ColumnType.MISSKEY_ANTENNA_TL -> true
    ColumnType.LOCAL, ColumnType.FEDERATE, ColumnType.HASHTAG, ColumnType.SEARCH -> isMisskey
    ColumnType.HASHTAG_FROM_ACCT -> false
    ColumnType.CONVERSATION, ColumnType.DIRECT_MESSAGES -> isMisskey
    else -> false
}

// カラム設定に「返信を表示しない」ボタンを含めるなら真
fun Column.canFilterReply(): Boolean = when (type) {
    ColumnType.HOME, ColumnType.MISSKEY_HYBRID, ColumnType.PROFILE,
    ColumnType.NOTIFICATIONS, ColumnType.NOTIFICATION_FROM_ACCT,
    ColumnType.LIST_TL, ColumnType.MISSKEY_ANTENNA_TL, ColumnType.DIRECT_MESSAGES -> true
    ColumnType.LOCAL, ColumnType.FEDERATE, ColumnType.HASHTAG, ColumnType.SEARCH -> isMisskey
    ColumnType.HASHTAG_FROM_ACCT -> true
    else -> false
}

fun Column.canFilterNormalToot(): Boolean = when (type) {
    ColumnType.NOTIFICATIONS -> true
    ColumnType.HOME, ColumnType.MISSKEY_HYBRID,
    ColumnType.LIST_TL, ColumnType.MISSKEY_ANTENNA_TL -> true
    ColumnType.LOCAL, ColumnType.FEDERATE, ColumnType.HASHTAG, ColumnType.SEARCH -> isMisskey
    ColumnType.HASHTAG_FROM_ACCT -> true
    else -> false
}

fun Column.canFilterNonPublicToot(): Boolean = when (type) {
    ColumnType.HOME, ColumnType.MISSKEY_HYBRID,
    ColumnType.LIST_TL, ColumnType.MISSKEY_ANTENNA_TL -> true
    ColumnType.LOCAL, ColumnType.FEDERATE, ColumnType.HASHTAG, ColumnType.SEARCH -> isMisskey
    ColumnType.HASHTAG_FROM_ACCT -> true
    else -> false
}


fun Column.onFiltersChanged2(filterList: ArrayList<TootFilter>) {
    val newFilter = encodeFilterTree(filterList) ?: return
    this.keywordFilterTrees = newFilter
    checkFiltersForListData(newFilter)
}

fun Column.onFilterDeleted(filter: TootFilter, filterList: ArrayList<TootFilter>) {
    if (type == ColumnType.KEYWORD_FILTER) {
        val tmp_list = ArrayList<TimelineItem>(list_data.size)
        for (o in list_data) {
            if (o is TootFilter) {
                if (o.id == filter.id) continue
            }
            tmp_list.add(o)
        }
        if (tmp_list.size != list_data.size) {
            list_data.clear()
            list_data.addAll(tmp_list)
            fireShowContent(reason = "onFilterDeleted")
        }
    } else {
        val context = getFilterContext()
        if (context != TootFilter.CONTEXT_NONE) {
            onFiltersChanged2(filterList)
        }
    }
}

@Suppress("unused")
fun Column.onLanguageFilterChanged() {
    // TODO
}

fun Column.initFilter() {
    column_regex_filter = Column.COLUMN_REGEX_FILTER_DEFAULT
    val regex_text = this.regex_text
    if (regex_text.isNotEmpty()) {
        try {
            val re = Pattern.compile(regex_text)
            column_regex_filter =
                { text: CharSequence? ->
                    if (text?.isEmpty() != false)
                        false
                    else
                        re.matcher(text).find()
                }
        } catch (ex: Throwable) {
            Column.log.trace(ex)
        }
    }

    favMuteSet = FavMute.acctSet
    highlight_trie = HighlightWord.nameSet
}

private fun Column.isFilteredByAttachment(status: TootStatus): Boolean {
    // オプションがどれも設定されていないならフィルタしない(false)
    if (!(with_attachment || with_highlight)) return false

    val matchMedia = with_attachment && status.reblog?.hasMedia() ?: status.hasMedia()
    val matchHighlight =
        with_highlight && null != (status.reblog?.highlightAny ?: status.highlightAny)

    // どれかの条件を満たすならフィルタしない(false)、どれも満たさないならフィルタする(true)
    return !(matchMedia || matchHighlight)
}

fun Column.isFiltered(status: TootStatus): Boolean {

    val filterTrees = keywordFilterTrees
    if (filterTrees != null) {
        if (status.isKeywordFiltered(access_info, filterTrees.treeIrreversible)) {
            Column.log.d("status filtered by treeIrreversible")
            return true
        }

        // just update _filtered flag for reversible filter
        status.updateKeywordFilteredFlag(access_info, filterTrees)
    }

    if (isFilteredByAttachment(status)) return true

    val reblog = status.reblog

    if (dont_show_boost) {
        if (reblog != null) return true
    }

    if (dont_show_reply) {
        if (status.in_reply_to_id != null) return true
        if (reblog?.in_reply_to_id != null) return true
    }

    if (dont_show_normal_toot) {
        if (status.in_reply_to_id == null && reblog == null) return true
    }
    if (dont_show_non_public_toot) {
        if (!status.visibility.isPublic) return true
    }

    if (column_regex_filter(status.decoded_content)) return true
    if (column_regex_filter(reblog?.decoded_content)) return true
    if (column_regex_filter(status.decoded_spoiler_text)) return true
    if (column_regex_filter(reblog?.decoded_spoiler_text)) return true

    if (checkLanguageFilter(status)) return true

    if (access_info.isPseudo) {
        var r = UserRelation.loadPseudo(access_info.getFullAcct(status.account))
        if (r.muting || r.blocking) return true
        if (reblog != null) {
            r = UserRelation.loadPseudo(access_info.getFullAcct(reblog.account))
            if (r.muting || r.blocking) return true
        }
    }

    return status.checkMuted()
}

// true if the status will be hidden
private fun Column.checkLanguageFilter(status: TootStatus?): Boolean {
    status ?: return false
    val languageFilter = language_filter ?: return false

    val allow = languageFilter.boolean(
        status.language ?: status.reblog?.language ?: TootStatus.LANGUAGE_CODE_UNKNOWN
    )
        ?: languageFilter.boolean(TootStatus.LANGUAGE_CODE_DEFAULT)
        ?: true

    return !allow
}

fun Column.isFiltered(item: TootNotification): Boolean {

    if (when (quick_filter) {
            Column.QUICK_FILTER_ALL -> when (item.type) {
                TootNotification.TYPE_FAVOURITE -> dont_show_favourite

                TootNotification.TYPE_REBLOG,
                TootNotification.TYPE_RENOTE,
                TootNotification.TYPE_QUOTE -> dont_show_boost

                TootNotification.TYPE_FOLLOW,
                TootNotification.TYPE_UNFOLLOW,
                TootNotification.TYPE_FOLLOW_REQUEST,
                TootNotification.TYPE_FOLLOW_REQUEST_MISSKEY,
                TootNotification.TYPE_FOLLOW_REQUEST_ACCEPTED_MISSKEY -> dont_show_follow

                TootNotification.TYPE_MENTION,
                TootNotification.TYPE_REPLY -> dont_show_reply

                TootNotification.TYPE_EMOJI_REACTION,
                TootNotification.TYPE_REACTION -> dont_show_reaction

                TootNotification.TYPE_VOTE,
                TootNotification.TYPE_POLL,
                TootNotification.TYPE_POLL_VOTE_MISSKEY -> dont_show_vote

                TootNotification.TYPE_STATUS -> dont_show_normal_toot
                else -> false
            }

            else -> when (item.type) {
                TootNotification.TYPE_FAVOURITE -> quick_filter != Column.QUICK_FILTER_FAVOURITE
                TootNotification.TYPE_REBLOG,
                TootNotification.TYPE_RENOTE,
                TootNotification.TYPE_QUOTE -> quick_filter != Column.QUICK_FILTER_BOOST

                TootNotification.TYPE_FOLLOW,
                TootNotification.TYPE_UNFOLLOW,
                TootNotification.TYPE_FOLLOW_REQUEST,
                TootNotification.TYPE_FOLLOW_REQUEST_MISSKEY,
                TootNotification.TYPE_FOLLOW_REQUEST_ACCEPTED_MISSKEY -> quick_filter != Column.QUICK_FILTER_FOLLOW

                TootNotification.TYPE_MENTION,
                TootNotification.TYPE_REPLY -> quick_filter != Column.QUICK_FILTER_MENTION

                TootNotification.TYPE_EMOJI_REACTION,
                TootNotification.TYPE_REACTION -> quick_filter != Column.QUICK_FILTER_REACTION

                TootNotification.TYPE_VOTE,
                TootNotification.TYPE_POLL,
                TootNotification.TYPE_POLL_VOTE_MISSKEY -> quick_filter != Column.QUICK_FILTER_VOTE

                TootNotification.TYPE_STATUS -> quick_filter != Column.QUICK_FILTER_POST
                else -> true
            }
        }
    ) {
        Column.log.d("isFiltered: ${item.type} notification filtered.")
        return true
    }

    val status = item.status
    val filterTrees = keywordFilterTrees
    if (status != null && filterTrees != null) {
        if (status.isKeywordFiltered(access_info, filterTrees.treeIrreversible)) {
            Column.log.d("isFiltered: status muted by treeIrreversible.")
            return true
        }

        // just update _filtered flag for reversible filter
        status.updateKeywordFilteredFlag(access_info, filterTrees)
    }
    if (checkLanguageFilter(status)) return true

    if (status?.checkMuted() == true) {
        Column.log.d("isFiltered: status muted by in-app muted words.")
        return true
    }

    // ふぁぼ魔ミュート
    when (item.type) {
        TootNotification.TYPE_REBLOG,
        TootNotification.TYPE_RENOTE,
        TootNotification.TYPE_QUOTE,
        TootNotification.TYPE_FAVOURITE,
        TootNotification.TYPE_EMOJI_REACTION,
        TootNotification.TYPE_REACTION,
        TootNotification.TYPE_FOLLOW,
        TootNotification.TYPE_FOLLOW_REQUEST,
        TootNotification.TYPE_FOLLOW_REQUEST_MISSKEY,
        TootNotification.TYPE_FOLLOW_REQUEST_ACCEPTED_MISSKEY -> {
            val who = item.account
            if (who != null && favMuteSet?.contains(access_info.getFullAcct(who)) == true) {
                Column.log.d("%s is in favMuteSet.", access_info.getFullAcct(who))
                return true
            }
        }
    }

    return false
}

// フィルタを読み直してリストを返す。またはnull
suspend fun Column.loadFilter2(client: TootApiClient): ArrayList<TootFilter>? {
    if (access_info.isPseudo || access_info.isMisskey) return null
    val column_context = getFilterContext()
    if (column_context == 0) return null
    val result = client.request(ApiPath.PATH_FILTERS)

    val jsonArray = result?.jsonArray ?: return null
    return TootFilter.parseList(jsonArray)
}

fun Column.encodeFilterTree(filterList: ArrayList<TootFilter>?): FilterTrees? {
    val column_context = getFilterContext()
    if (column_context == 0 || filterList == null) return null
    val result = FilterTrees()
    val now = System.currentTimeMillis()
    for (filter in filterList) {
        if (filter.time_expires_at > 0L && now >= filter.time_expires_at) continue
        if ((filter.context and column_context) != 0) {

            val validator = when (filter.whole_word) {
                true -> WordTrieTree.WORD_VALIDATOR
                else -> WordTrieTree.EMPTY_VALIDATOR
            }

            if (filter.irreversible) {
                result.treeIrreversible
            } else {
                result.treeReversible
            }.add(filter.phrase, validator = validator)

            result.treeAll.add(filter.phrase, validator = validator)
        }
    }
    return result
}

fun Column.checkFiltersForListData(trees: FilterTrees?) {
    trees ?: return
    val changeList = ArrayList<AdapterChange>()
    list_data.forEachIndexed { idx, item ->
        when (item) {
            is TootStatus -> {
                val old_filtered = item.filtered
                item.updateKeywordFilteredFlag(access_info, trees, checkIrreversible = true)
                if (old_filtered != item.filtered) {
                    changeList.add(AdapterChange(AdapterChangeType.RangeChange, idx))
                }
            }

            is TootNotification -> {
                val s = item.status
                if (s != null) {
                    val old_filtered = s.filtered
                    s.updateKeywordFilteredFlag(access_info, trees, checkIrreversible = true)
                    if (old_filtered != s.filtered) {
                        changeList.add(AdapterChange(AdapterChangeType.RangeChange, idx))
                    }
                }
            }
        }
    }
    fireShowContent(reason = "filter updated", changeList = changeList)
}

fun reloadFilter(context: Context, access_info: SavedAccount) {
    launchMain{
        var resultList: ArrayList<TootFilter>? = null

        context.runApiTask(
            access_info,
            progressStyle = ApiTask.PROGRESS_NONE
        ){client->
            client.request(ApiPath.PATH_FILTERS)?.also{ result->
                result.jsonArray?.let{
                    resultList = TootFilter.parseList(it)
                }
            }
        }

        resultList?.let{
            Column.log.d("update filters for ${access_info.acct.pretty}")
            for (column in App1.getAppState(context).columnList) {
                if (column.access_info == access_info) {
                    column.onFiltersChanged2(it)
                }
            }
        }
    }
}
