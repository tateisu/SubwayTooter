package jp.juggler.subwaytooter.column

import android.content.Context
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.api.ApiPath
import jp.juggler.subwaytooter.api.ApiTask
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.table.*
import jp.juggler.util.*
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.WordTrieTree
import jp.juggler.util.log.LogCategory
import jp.juggler.util.ui.AdapterChange
import jp.juggler.util.ui.AdapterChangeType
import java.util.regex.Pattern

private val log = LogCategory("ColumnFilters")

val Column.isFilterEnabled: Boolean
    get() = withAttachment ||
            withHighlight ||
            regexText.isNotEmpty() ||
            dontShowNormalToot ||
            dontShowNonPublicToot ||
            quickFilter != Column.QUICK_FILTER_ALL ||
            dontShowBoost ||
            dontShowFavourite ||
            dontShowFollow ||
            dontShowReply ||
            dontShowReaction ||
            dontShowVote ||
            (languageFilter?.isNotEmpty() == true)

// マストドン2.4.3rcのキーワードフィルタのコンテキスト
fun Column.getFilterContext() = when (type) {

    ColumnType.STATUS_HISTORY -> null

    ColumnType.HOME,
    ColumnType.LIST_TL,
    ColumnType.MISSKEY_HYBRID,
    -> TootFilterContext.Home

    ColumnType.NOTIFICATIONS,
    ColumnType.NOTIFICATION_FROM_ACCT,
    -> TootFilterContext.Notifications

    ColumnType.CONVERSATION,
    ColumnType.CONVERSATION_WITH_REFERENCE,
    ColumnType.DIRECT_MESSAGES,
    -> TootFilterContext.Thread

    ColumnType.PROFILE -> TootFilterContext.Account

    else -> TootFilterContext.Public
    // ColumnType.MISSKEY_HYBRID や ColumnType.MISSKEY_ANTENNA_TL はHOMEでもPUBLICでもある…
    // Misskeyだし関係ないが、NONEにするとアプリ内で完結するフィルタも働かなくなる
}

// カラム設定に正規表現フィルタを含めるなら真
fun Column.canStatusFilter() =
    when (type) {
        ColumnType.SEARCH_MSP,
        ColumnType.SEARCH_TS,
        ColumnType.SEARCH_NOTESTOCK,
        ColumnType.STATUS_HISTORY,
        -> true
        else -> getFilterContext() != null
    }

// カラム設定に「すべての画像を隠す」ボタンを含めるなら真
fun Column.canNSFWDefault(): Boolean = canStatusFilter()

// カラム設定に「ブーストを表示しない」ボタンを含めるなら真
fun Column.canFilterBoost(): Boolean = when (type) {
    ColumnType.HOME, ColumnType.MISSKEY_HYBRID, ColumnType.PROFILE,
    ColumnType.NOTIFICATIONS, ColumnType.NOTIFICATION_FROM_ACCT,
    ColumnType.LIST_TL, ColumnType.MISSKEY_ANTENNA_TL,
    -> true
    ColumnType.LOCAL, ColumnType.FEDERATE, ColumnType.HASHTAG, ColumnType.SEARCH -> isMisskey
    ColumnType.HASHTAG_FROM_ACCT -> false
    ColumnType.CONVERSATION,
    ColumnType.CONVERSATION_WITH_REFERENCE,
    ColumnType.DIRECT_MESSAGES,
    -> isMisskey
    else -> false
}

// カラム設定に「返信を表示しない」ボタンを含めるなら真
fun Column.canFilterReply(): Boolean = when (type) {
    ColumnType.HOME, ColumnType.MISSKEY_HYBRID, ColumnType.PROFILE,
    ColumnType.NOTIFICATIONS, ColumnType.NOTIFICATION_FROM_ACCT,
    ColumnType.LIST_TL, ColumnType.MISSKEY_ANTENNA_TL, ColumnType.DIRECT_MESSAGES,
    -> true
    ColumnType.LOCAL, ColumnType.FEDERATE, ColumnType.HASHTAG, ColumnType.SEARCH -> isMisskey
    ColumnType.HASHTAG_FROM_ACCT -> true
    else -> false
}

fun Column.canFilterNormalToot(): Boolean = when (type) {
    ColumnType.NOTIFICATIONS -> true
    ColumnType.HOME, ColumnType.MISSKEY_HYBRID,
    ColumnType.LIST_TL, ColumnType.MISSKEY_ANTENNA_TL,
    -> true
    ColumnType.LOCAL, ColumnType.FEDERATE, ColumnType.HASHTAG, ColumnType.SEARCH -> isMisskey
    ColumnType.HASHTAG_FROM_ACCT -> true
    else -> false
}

fun Column.canFilterNonPublicToot(): Boolean = when (type) {
    ColumnType.HOME, ColumnType.MISSKEY_HYBRID,
    ColumnType.LIST_TL, ColumnType.MISSKEY_ANTENNA_TL,
    -> true
    ColumnType.LOCAL, ColumnType.FEDERATE, ColumnType.HASHTAG, ColumnType.SEARCH -> isMisskey
    ColumnType.HASHTAG_FROM_ACCT -> true
    else -> false
}

fun Column.onFiltersChanged2(filterList: List<TootFilter>) {
    val newFilter = encodeFilterTree(filterList) ?: return
    this.keywordFilterTrees = newFilter
    checkFiltersForListData(newFilter)
}

fun Column.onFilterDeleted(filter: TootFilter, filterList: List<TootFilter>) {
    if (type == ColumnType.KEYWORD_FILTER) {
        val tmpList = ArrayList<TimelineItem>(listData.size)
        for (o in listData) {
            if (o is TootFilter) {
                if (o.id == filter.id) continue
            }
            tmpList.add(o)
        }
        if (tmpList.size != listData.size) {
            listData.clear()
            listData.addAll(tmpList)
            fireShowContent(reason = "onFilterDeleted")
        }
    } else {
        if (getFilterContext() != null) {
            onFiltersChanged2(filterList)
        }
    }
}

@Suppress("unused")
fun Column.onLanguageFilterChanged() {
    // TODO
}

fun Column.initFilter() {
    columnRegexFilter = Column.COLUMN_REGEX_FILTER_DEFAULT
    val regexText = this.regexText
    if (regexText.isNotEmpty()) {
        try {
            val re = Pattern.compile(regexText)
            columnRegexFilter =
                { text: CharSequence? ->
                    when {
                        text?.isEmpty() != false -> false
                        else -> re.matcher(text).find()
                    }
                }
        } catch (ex: Throwable) {
            log.e(ex, "initFilter failed.")
        }
    }

    favMuteSet = daoFavMute.acctSet()
    highlightTrie = daoHighlightWord.nameSet()
}

private fun Column.isFilteredByAttachment(status: TootStatus): Boolean {
    // オプションがどれも設定されていないならフィルタしない(false)
    if (!(withAttachment || withHighlight)) return false

    val matchMedia = withAttachment && status.reblog?.hasMedia() ?: status.hasMedia()
    val matchHighlight =
        withHighlight && null != (status.reblog?.highlightAny ?: status.highlightAny)

    // どれかの条件を満たすならフィルタしない(false)、どれも満たさないならフィルタする(true)
    return !(matchMedia || matchHighlight)
}

fun Column.isFiltered(status: TootStatus): Boolean {

    val isMe = accessInfo.isMe(status.account) ||
            accessInfo.isMe(status.reblog?.account)

    val filterTrees = keywordFilterTrees
    if (filterTrees != null && !isMe) {
        val ti = TootInstance.getCached(accessInfo)
        if (ti?.versionGE(TootInstance.VERSION_4_0_0) == true) {
            // v4 はサーバ側でフィルタしてる
            // XXX: フィルタが後から更新されたら再チェックが必要か？
            val filteredV4 = status.filteredV4 ?: status.reblog?.filteredV4
            if (filteredV4.isNullOrEmpty()) {
                // フィルタされていない
            } else if (filteredV4.any { it.isHide }) {
                // 隠すフィルタ
                log.d("isFiltered: status muted by filteredV4 hide.")
                return true
            } else {
                // 警告フィルタ
                status.updateKeywordFilteredFlag(
                    accessInfo,
                    filterTrees,
                    matchedFiltersV4 = filteredV4
                )
            }
        } else {
            if (status.matchKeywordFilterWithReblog(accessInfo, filterTrees.treeHide) != null) {
                log.d("status filtered by treeIrreversible")
                return true
            } else {
                // 警告フィルタ
                status.updateKeywordFilteredFlag(accessInfo, filterTrees)
            }
        }
    }

    if (isFilteredByAttachment(status)) return true

    val reblog = status.reblog

    if (dontShowBoost) {
        if (reblog != null) return true
    }

    if (dontShowReply) {
        if (status.in_reply_to_id != null) return true
        if (reblog?.in_reply_to_id != null) return true
    }

    if (dontShowNormalToot) {
        if (status.in_reply_to_id == null && reblog == null) return true
    }
    if (dontShowNonPublicToot) {
        if (!status.visibility.isPublic) return true
    }

    if (columnRegexFilter(status.decoded_content)) return true
    if (columnRegexFilter(reblog?.decoded_content)) return true
    if (columnRegexFilter(status.decoded_spoiler_text)) return true
    if (columnRegexFilter(reblog?.decoded_spoiler_text)) return true

    if (checkLanguageFilter(status)) return true

    if (accessInfo.isPseudo) {
        var r = daoUserRelation.loadPseudo(accessInfo.getFullAcct(status.account))
        if (r.muting || r.blocking) return true
        if (reblog != null) {
            r = daoUserRelation.loadPseudo(accessInfo.getFullAcct(reblog.account))
            if (r.muting || r.blocking) return true
        }
    }

    return status.checkMuted()
}

// true if the status will be hidden
private fun Column.checkLanguageFilter(status: TootStatus?): Boolean {
    status ?: return false
    val languageFilter = languageFilter ?: return false

    val allow = languageFilter.boolean(
        status.language ?: status.reblog?.language ?: TootStatus.LANGUAGE_CODE_UNKNOWN
    )
        ?: languageFilter.boolean(TootStatus.LANGUAGE_CODE_DEFAULT)
        ?: true

    return !allow
}

fun Column.isFiltered(item: TootNotification): Boolean {

    if (when (quickFilter) {
            Column.QUICK_FILTER_ALL -> when (item.type) {
                TootNotification.TYPE_FAVOURITE -> dontShowFavourite

                TootNotification.TYPE_REBLOG,
                TootNotification.TYPE_RENOTE,
                TootNotification.TYPE_QUOTE,
                -> dontShowBoost

                TootNotification.TYPE_FOLLOW,
                TootNotification.TYPE_UNFOLLOW,
                TootNotification.TYPE_FOLLOW_REQUEST,
                TootNotification.TYPE_FOLLOW_REQUEST_MISSKEY,
                TootNotification.TYPE_FOLLOW_REQUEST_ACCEPTED_MISSKEY,
                TootNotification.TYPE_ADMIN_SIGNUP,
                TootNotification.TYPE_ADMIN_REPORT,
                -> dontShowFollow

                TootNotification.TYPE_MENTION,
                TootNotification.TYPE_REPLY,
                -> dontShowReply

                TootNotification.TYPE_EMOJI_REACTION_PLEROMA,
                TootNotification.TYPE_EMOJI_REACTION,
                TootNotification.TYPE_REACTION,
                -> dontShowReaction

                TootNotification.TYPE_VOTE,
                TootNotification.TYPE_POLL,
                TootNotification.TYPE_POLL_VOTE_MISSKEY,
                -> dontShowVote

                TootNotification.TYPE_STATUS,
                TootNotification.TYPE_UPDATE,
                TootNotification.TYPE_STATUS_REFERENCE,
                -> dontShowNormalToot

                else -> false
            }

            else -> when (item.type) {
                TootNotification.TYPE_FAVOURITE -> quickFilter != Column.QUICK_FILTER_FAVOURITE
                TootNotification.TYPE_REBLOG,
                TootNotification.TYPE_RENOTE,
                TootNotification.TYPE_QUOTE,
                -> quickFilter != Column.QUICK_FILTER_BOOST

                TootNotification.TYPE_FOLLOW,
                TootNotification.TYPE_UNFOLLOW,
                TootNotification.TYPE_FOLLOW_REQUEST,
                TootNotification.TYPE_FOLLOW_REQUEST_MISSKEY,
                TootNotification.TYPE_FOLLOW_REQUEST_ACCEPTED_MISSKEY,
                TootNotification.TYPE_ADMIN_SIGNUP,
                TootNotification.TYPE_ADMIN_REPORT,
                -> quickFilter != Column.QUICK_FILTER_FOLLOW

                TootNotification.TYPE_MENTION,
                TootNotification.TYPE_REPLY,
                -> quickFilter != Column.QUICK_FILTER_MENTION

                TootNotification.TYPE_EMOJI_REACTION_PLEROMA,
                TootNotification.TYPE_EMOJI_REACTION,
                TootNotification.TYPE_REACTION,
                -> quickFilter != Column.QUICK_FILTER_REACTION

                TootNotification.TYPE_VOTE,
                TootNotification.TYPE_POLL,
                TootNotification.TYPE_POLL_VOTE_MISSKEY,
                -> quickFilter != Column.QUICK_FILTER_VOTE

                TootNotification.TYPE_STATUS,
                TootNotification.TYPE_UPDATE,
                TootNotification.TYPE_STATUS_REFERENCE,
                -> quickFilter != Column.QUICK_FILTER_POST

                else -> true
            }
        }
    ) {
        log.d("isFiltered: ${item.type} notification filtered.")
        return true
    }

    val status = item.status
    val filterTrees = keywordFilterTrees
    if (status != null && filterTrees != null) {
        val ti = TootInstance.getCached(accessInfo)
        if (ti?.versionGE(TootInstance.VERSION_4_0_0) == true) {
            // v4 はサーバ側でフィルタしてる
            // XXX: フィルタが後から更新されたら再チェックが必要か？
            val filterResults = status.filteredV4 ?: status.reblog?.filteredV4
            if (filterResults.isNullOrEmpty()) {
                // フィルタされていない
            } else if (filterResults.any { it.isHide }) {
                // 隠すフィルタ
                log.d("isFiltered: status muted by filteredV4 hide.")
                return true
            } else {
                // 警告フィルタ
                status.updateKeywordFilteredFlag(
                    accessInfo,
                    filterTrees,
                    matchedFiltersV4 = filterResults
                )
            }
        } else {
            // v4未満は端末側でのチェック
            if (status.matchKeywordFilterWithReblog(accessInfo, filterTrees.treeHide) != null) {
                // 隠すフィルタ
                log.d("isFiltered: status muted by treeIrreversible.")
                return true
            } else {
                // 警告フィルタ
                // just update _filtered flag for reversible filter
                status.updateKeywordFilteredFlag(accessInfo, filterTrees)
            }
        }
    }

    if (checkLanguageFilter(status)) return true

    if (status?.checkMuted() == true) {
        log.d("isFiltered: status muted by in-app muted words.")
        return true
    }

    // ふぁぼ魔ミュート
    when (item.type) {
        TootNotification.TYPE_REBLOG,
        TootNotification.TYPE_RENOTE,
        TootNotification.TYPE_QUOTE,
        TootNotification.TYPE_FAVOURITE,
        TootNotification.TYPE_EMOJI_REACTION_PLEROMA,
        TootNotification.TYPE_EMOJI_REACTION,
        TootNotification.TYPE_REACTION,
        TootNotification.TYPE_FOLLOW,
        TootNotification.TYPE_FOLLOW_REQUEST,
        TootNotification.TYPE_FOLLOW_REQUEST_MISSKEY,
        TootNotification.TYPE_FOLLOW_REQUEST_ACCEPTED_MISSKEY,
        -> {
            val who = item.account
            if (who != null && favMuteSet?.contains(accessInfo.getFullAcct(who)) == true) {
                log.d("${accessInfo.getFullAcct(who)} is in favMuteSet.")
                return true
            }
        }
    }

    return false
}

// フィルタを読み直してリストを返す。またはnull
suspend fun Column.loadFilter2(client: TootApiClient): List<TootFilter>? {
    if (accessInfo.isPseudo || accessInfo.isMisskey) return null
    if (getFilterContext() == null) return null
    var result = client.request(ApiPath.PATH_FILTERS_V2)
    if (result?.response?.code == 404) {
        result = client.request(ApiPath.PATH_FILTERS)
    }

    val jsonArray = result?.jsonArray ?: return null
    return TootFilter.parseList(jsonArray)
}

fun Column.encodeFilterTree(filterList: List<TootFilter>?): FilterTrees? {
    val columnContext = getFilterContext()
    if (columnContext == null || filterList == null) return null
    val result = FilterTrees()
    val now = System.currentTimeMillis()
    for (filter in filterList) {
        if (filter.time_expires_at > 0L && now >= filter.time_expires_at) continue
        if (!filter.hasContext(columnContext)) continue

        for (kw in filter.keywords) {
            val validator = when (kw.whole_word) {
                true -> WordTrieTree.WORD_VALIDATOR
                else -> WordTrieTree.EMPTY_VALIDATOR
            }
            when (filter.hide) {
                true -> result.treeHide
                else -> result.treeWarn
            }.add(
                s = kw.keyword,
                tag = filter,
                validator = validator
            )
            result.treeAll.add(
                s = kw.keyword,
                tag = filter,
                validator = validator
            )
        }
    }
    return result
}

// フィルタ更新時に全部チェックし直す
fun Column.checkFiltersForListData(trees: FilterTrees?) {
    trees ?: return
    val changeList = ArrayList<AdapterChange>()
    listData.forEachIndexed { idx, item ->
        when (item) {
            is TootStatus -> {
                val oldFiltered = item.filtered
                item.updateKeywordFilteredFlag(accessInfo, trees, checkAll = true)
                if (oldFiltered != item.filtered) {
                    changeList.add(AdapterChange(AdapterChangeType.RangeChange, idx))
                }
            }

            is TootNotification -> {
                val s = item.status
                if (s != null) {
                    val oldFiltered = s.filtered
                    s.updateKeywordFilteredFlag(accessInfo, trees, checkAll = true)
                    if (oldFiltered != s.filtered) {
                        changeList.add(AdapterChange(AdapterChangeType.RangeChange, idx))
                    }
                }
            }
        }
    }
    fireShowContent(reason = "filter updated", changeList = changeList)
}

fun reloadFilter(context: Context, accessInfo: SavedAccount) {
    launchMain {
        var resultList: List<TootFilter>? = null

        context.runApiTask(
            accessInfo,
            progressStyle = ApiTask.PROGRESS_NONE
        ) { client ->
            var result = client.request(ApiPath.PATH_FILTERS_V2)
            if (result?.response?.code == 404) {
                result = client.request(ApiPath.PATH_FILTERS)
            }
            result?.jsonArray?.let {
                resultList = TootFilter.parseList(it)
            }
            result
        }

        resultList?.let {
            log.d("update filters for ${accessInfo.acct.pretty}")
            for (column in App1.getAppState(context).columnList) {
                if (column.accessInfo == accessInfo) {
                    column.onFiltersChanged2(it)
                }
            }
        }
    }
}
