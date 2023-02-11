package jp.juggler.subwaytooter.columnviewholder

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.action.followFromAnotherAccount
import jp.juggler.subwaytooter.action.userProfileLocal
import jp.juggler.subwaytooter.actmain.nextPosition
import jp.juggler.subwaytooter.api.MisskeyAccountDetailMap
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootAccountRef
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.column.*
import jp.juggler.subwaytooter.databinding.LvHeaderProfileBinding
import jp.juggler.subwaytooter.dialog.showTextInputDialog
import jp.juggler.subwaytooter.emoji.EmojiMap
import jp.juggler.subwaytooter.itemviewholder.DlgContextMenu
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.pref.PrefI
import jp.juggler.subwaytooter.span.EmojiImageSpan
import jp.juggler.subwaytooter.span.LinkInfo
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.span.createSpan
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.table.daoAcctColor
import jp.juggler.subwaytooter.table.daoUserRelation
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.NetworkEmojiInvalidator
import jp.juggler.subwaytooter.util.openCustomTab
import jp.juggler.subwaytooter.util.startMargin
import jp.juggler.subwaytooter.view.MyLinkMovementMethod
import jp.juggler.subwaytooter.view.MyTextView
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.data.buildJsonObject
import jp.juggler.util.data.intoStringResource
import jp.juggler.util.data.notEmpty
import jp.juggler.util.data.notZero
import jp.juggler.util.log.showToast
import jp.juggler.util.network.toPostRequestBuilder
import jp.juggler.util.ui.attrColor
import jp.juggler.util.ui.setIconDrawableId
import jp.juggler.util.ui.vg
import org.jetbrains.anko.textColor

internal class ViewHolderHeaderProfile(
    override val activity: ActMain,
    parent: ViewGroup,
    val views: LvHeaderProfileBinding =
        LvHeaderProfileBinding.inflate(activity.layoutInflater, parent, false),
) : ViewHolderHeaderBase(views.root), View.OnClickListener, View.OnLongClickListener {

    companion object {
        private fun SpannableStringBuilder.appendSpan(text: String, span: Any) {
            val start = length
            append(text)
            setSpan(
                span,
                start,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private var whoRef: TootAccountRef? = null

    private var movedRef: TootAccountRef? = null

    private val nameInvalidator1 =
        NetworkEmojiInvalidator(activity.handler, views.tvDisplayName)

    private val noteInvalidator =
        NetworkEmojiInvalidator(activity.handler, views.tvNote)

    private val movedCaptionInvalidator =
        NetworkEmojiInvalidator(activity.handler, views.tvMoved)

    private val movedNameInvalidator =
        NetworkEmojiInvalidator(activity.handler, views.tvMovedName)

    private val density: Float

    private var colorTextContent = 0

    private var relation: UserRelation? = null

    init {
        views.root.tag = this
        val holder = this
        views.run {

            density = tvDisplayName.resources.displayMetrics.density

            for (v in arrayOf(
                ivBackground,
                btnFollowing,
                btnFollowers,
                btnStatusCount,
                btnMore,
                btnFollow,
                tvRemoteProfileWarning,
                btnPersonalNotesEdit,

                btnMoved,
                llMoved,
                btnPersonalNotesEdit
            )) {
                v.setOnClickListener(holder)
            }

            btnMoved.setOnLongClickListener(holder)
            btnFollow.setOnLongClickListener(holder)

            tvNote.movementMethod = MyLinkMovementMethod


            ivBackground.measureProfileBg = true
        }
    }

    override fun getAccount(): TootAccountRef? = whoRef

    override fun onViewRecycled() {
    }

    //	fun updateRelativeTime() {
    //		val who = whoRef?.get()
    //		if(who != null) {
    //			tvCreated.text = TootStatus.formatTime(tvCreated.context, who.time_created_at, true)
    //		}
    //	}

    override fun bindData(column: Column) {
        super.bindData(column)
        bindFonts()
        bindColors()
        views.run {
            llMoved.visibility = View.GONE
            tvMoved.visibility = View.GONE
            llFields.visibility = View.GONE
            llFields.removeAllViews()
        }
        val whoRef = column.whoAccount
        this.whoRef = whoRef
        when (val who = whoRef?.get()) {
            null -> bindAccountNull()
            else -> bindAccount(who, whoRef)
        }
    }

    // カラム設定から戻った際に呼ばれる
    override fun showColor() {
        views.llProfile.setBackgroundColor(
            when (val c = column.columnBgColor) {
                0 -> activity.attrColor(R.attr.colorProfileBackgroundMask)
                else -> -0x40000000 or (0x00ffffff and c)
            }
        )
    }

    // bind時に呼ばれる
    private fun bindColors() {
        val contentColor = column.getContentColor()
        this.colorTextContent = contentColor

        views.run {

            tvPersonalNotes.textColor = contentColor
            tvMoved.textColor = contentColor
            tvMovedName.textColor = contentColor
            tvDisplayName.textColor = contentColor
            tvNote.textColor = contentColor
            tvRemoteProfileWarning.textColor = contentColor
            btnStatusCount.textColor = contentColor
            btnFollowing.textColor = contentColor
            btnFollowers.textColor = contentColor
            tvFeaturedTags.textColor = contentColor

            setIconDrawableId(
                activity,
                btnMore,
                R.drawable.ic_more,
                color = contentColor,
                alphaMultiplier = stylerBoostAlpha
            )

            setIconDrawableId(
                activity,
                btnPersonalNotesEdit,
                R.drawable.ic_edit,
                color = contentColor,
                alphaMultiplier = stylerBoostAlpha
            )

            val acctColor = column.getAcctColor()
            tvCreated.textColor = acctColor
            tvMovedAcct.textColor = acctColor
            tvLastStatusAt.textColor = acctColor

            showColor()
        }
    }

    private fun bindFonts() {
        views.run {
            var f: Float

            f = activity.timelineFontSizeSp
            if (!f.isNaN()) {
                tvMovedName.textSize = f
                tvMoved.textSize = f
                tvPersonalNotes.textSize = f
                tvFeaturedTags.textSize = f
            }

            f = activity.acctFontSizeSp
            if (!f.isNaN()) {
                tvMovedAcct.textSize = f
                tvCreated.textSize = f
                tvLastStatusAt.textSize = f
            }

            val spacing = activity.timelineSpacing
            if (spacing != null) {
                tvMovedName.setLineSpacing(0f, spacing)
                tvMoved.setLineSpacing(0f, spacing)
            }
        }
    }

    private fun bindAccountNull() {
        relation = null
        nameInvalidator1.register(null)
        noteInvalidator.register(null)
        views.run {
            tvCreated.text = ""
            tvLastStatusAt.vg(false)
            tvFeaturedTags.vg(false)
            ivBackground.setImageDrawable(null)
            ivAvatar.setImageDrawable(null)

            tvAcct.text = "@"

            tvDisplayName.text = ""

            tvNote.text = ""
            tvMisskeyExtra.text = ""

            btnStatusCount.text = activity.getString(R.string.statuses) + "\n" + "?"
            btnFollowing.text = activity.getString(R.string.following) + "\n" + "?"
            btnFollowers.text = activity.getString(R.string.followers) + "\n" + "?"

            btnFollow.setImageDrawable(null)
            tvRemoteProfileWarning.visibility = View.GONE
        }
    }

    private fun bindAccount(who: TootAccount, whoRef: TootAccountRef) {
        // Misskeyの場合はNote中のUserエンティティと /api/users/show の情報量がかなり異なる
        val whoDetail = MisskeyAccountDetailMap.get(accessInfo, who.id)
        val relation = daoUserRelation.load(accessInfo.db_id, who.id)
        this.relation = relation
        views.run {
            tvCreated.text =
                TootStatus.formatTime(tvCreated.context, (whoDetail ?: who).time_created_at, true)

            who.setAccountExtra(
                accessInfo,
                tvLastStatusAt,
                invalidator = null,
                fromProfileHeader = true
            )

            val featuredTagsText = formatFeaturedTags()
            tvFeaturedTags.vg(featuredTagsText != null)?.let {
                it.text = featuredTagsText!!
                it.movementMethod = MyLinkMovementMethod
            }

            ivBackground.setImageUrl(0f, accessInfo.supplyBaseUrl(who.header_static))

            ivAvatar.setImageUrl(
                calcIconRound(ivAvatar.layoutParams),
                accessInfo.supplyBaseUrl(who.avatar_static),
                accessInfo.supplyBaseUrl(who.avatar)
            )

            val name = whoDetail?.decodeDisplayName(activity) ?: whoRef.decoded_display_name
            tvDisplayName.text = name
            nameInvalidator1.register(name)

            tvRemoteProfileWarning.vg(column.accessInfo.isRemoteUser(who))

            tvAcct.text = encodeAcctText(who, whoDetail)

            val note = whoRef.decoded_note
            tvNote.text = note
            noteInvalidator.register(note)

            tvMisskeyExtra.text = encodeMisskeyExtra(whoDetail)
            tvMisskeyExtra.vg(tvMisskeyExtra.text.isNotEmpty())

            btnStatusCount.text =
                "${activity.getString(R.string.statuses)}\n${
                    whoDetail?.statuses_count ?: who.statuses_count
                }"

            val hideFollowCount = PrefB.bpHideFollowCount.value

            var caption = activity.getString(R.string.following)
            btnFollowing.text = when {
                hideFollowCount -> caption
                else -> "${caption}\n${whoDetail?.following_count ?: who.following_count}"
            }

            caption = activity.getString(R.string.followers)
            btnFollowers.text = when {
                hideFollowCount -> caption
                else -> "${caption}\n${whoDetail?.followers_count ?: who.followers_count}"
            }


            setFollowIcon(
                activity,
                btnFollow,
                ivFollowedBy,
                relation,
                who,
                colorTextContent,
                alphaMultiplier = stylerBoostAlpha
            )

            tvPersonalNotes.text = relation.note ?: ""

            showMoved(who, who.movedRef)

            (whoDetail?.fields ?: who.fields)?.notEmpty()?.let { showFields(who, it) }
        }
    }

    private fun showMoved(who: TootAccount, movedRef: TootAccountRef?) {
        if (movedRef == null) return
        this.movedRef = movedRef
        val moved = movedRef.get()

        views.run {

            llMoved.visibility = View.VISIBLE
            tvMoved.visibility = View.VISIBLE

            val caption = who.decodeDisplayName(activity)
                .intoStringResource(activity, R.string.account_moved_to)

            tvMoved.text = caption
            movedCaptionInvalidator.register(caption)

            ivMoved.layoutParams.width = activity.avatarIconSize
            ivMoved.setImageUrl(
                calcIconRound(ivMoved.layoutParams),
                accessInfo.supplyBaseUrl(moved.avatar_static)
            )

            tvMovedName.text = movedRef.decoded_display_name
            movedNameInvalidator.register(movedRef.decoded_display_name)

            setAcct(tvMovedAcct, accessInfo, moved)

            val relation = daoUserRelation.load(accessInfo.db_id, moved.id)
            setFollowIcon(
                activity,
                btnMoved,
                ivMovedBy,
                relation,
                moved,
                colorTextContent,
                alphaMultiplier = stylerBoostAlpha
            )
        }
    }

    override fun onClick(v: View) {

        when (v.id) {

            R.id.ivBackground, R.id.tvRemoteProfileWarning ->
                activity.openCustomTab(whoRef?.get()?.url)

            R.id.btnFollowing -> {
                column.profileTab = ProfileTab.Following
                activity.appState.saveColumnList()
                column.startLoading()
            }

            R.id.btnFollowers -> {
                column.profileTab = ProfileTab.Followers
                activity.appState.saveColumnList()
                column.startLoading()
            }

            R.id.btnStatusCount -> {
                column.profileTab = ProfileTab.Status
                activity.appState.saveColumnList()
                column.startLoading()
            }

            R.id.btnMore -> whoRef?.let { whoRef ->
                DlgContextMenu(activity, column, whoRef, null, null, null).show()
            }

            R.id.btnFollow -> whoRef?.let { whoRef ->
                DlgContextMenu(activity, column, whoRef, null, null, null).show()
            }

            R.id.btnMoved -> movedRef?.let { movedRef ->
                DlgContextMenu(activity, column, movedRef, null, null, null).show()
            }

            R.id.llMoved -> movedRef?.let { movedRef ->
                if (accessInfo.isPseudo) {
                    DlgContextMenu(activity, column, movedRef, null, null, null).show()
                } else {
                    activity.userProfileLocal(

                        activity.nextPosition(column),
                        accessInfo,
                        movedRef.get()
                    )
                }
            }

            R.id.btnPersonalNotesEdit -> whoRef?.let { whoRef ->
                val who = whoRef.get()
                val relation = this.relation
                val lastColumn = column
                activity.launchAndShowError {
                    activity.showTextInputDialog(
                        title = daoAcctColor.getStringWithNickname(
                            activity,
                            R.string.personal_notes_of,
                            who.acct
                        ),
                        initialText = relation?.note ?: "",
                        allowEmpty = true,
                        onEmptyText = {},
                    ) { text ->
                        val result = activity.runApiTask(column.accessInfo) { client ->
                            when {
                                accessInfo.isPseudo ->
                                    TootApiResult("Personal notes is not supported on pseudo account.")
                                accessInfo.isMisskey ->
                                    TootApiResult("Personal notes is not supported on Misskey account.")
                                else ->
                                    client.request(
                                        "/api/v1/accounts/${who.id}/note",
                                        buildJsonObject {
                                            put("comment", text)
                                        }.toPostRequestBuilder()
                                    )
                            }
                        }
                        result ?: return@showTextInputDialog true
                        when (val error = result.error) {
                            null -> {
                                relation?.note = text
                                if (lastColumn == column) bindData(column)
                                true
                            }
                            else -> {
                                activity.showToast(true, error)
                                false
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onLongClick(v: View): Boolean {
        when (v.id) {

            R.id.btnFollow -> {
                activity.followFromAnotherAccount(
                    activity.nextPosition(column),
                    accessInfo,
                    whoRef?.get()
                )
                return true
            }

            R.id.btnMoved -> {
                activity.followFromAnotherAccount(
                    activity.nextPosition(column),
                    accessInfo,
                    movedRef?.get()
                )
                return true
            }
        }

        return false
    }

    private fun setAcct(tv: TextView, accessInfo: SavedAccount, who: TootAccount) {
        val ac = daoAcctColor.load(accessInfo, who)
        tv.text = when {
            daoAcctColor.hasNickname(ac) -> ac.nickname
            PrefB.bpShortAcctLocalUser.value -> "@${who.acct.pretty}"
            else -> "@${ac.nickname}"
        }

        tv.textColor = ac.colorFg.notZero() ?: column.getAcctColor()

        tv.setBackgroundColor(ac.colorBg) // may 0
        tv.setPaddingRelative(activity.acctPadLr, 0, activity.acctPadLr, 0)
    }

    private fun formatFeaturedTags() = column.whoFeaturedTags?.notEmpty()?.let { tagList ->
        SpannableStringBuilder().apply {
            append(activity.getString(R.string.featured_hashtags))
            append(":")
            tagList.forEach { tag ->
                append(" ")
                val tagWithSharp = "#" + tag.name
                val start = length
                append(tagWithSharp)
                val end = length
                tag.url?.notEmpty()?.let { url ->
                    val span = MyClickableSpan(
                        LinkInfo(url = url, tag = tag.name, caption = tagWithSharp)
                    )
                    setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
    }

    private fun encodeAcctText(who: TootAccount, whoDetail: TootAccount?) =
        SpannableStringBuilder().apply {
            append("@")
            append(accessInfo.getFullAcct(who).pretty)
            if (whoDetail?.locked ?: who.locked) {
                append(" ")
                val emoji = EmojiMap.shortNameMap["lock"]
                when {
                    emoji == null ->
                        append("locked")
                    PrefB.bpUseTwemoji.value ->
                        appendSpan("locked", emoji.createSpan(activity))
                    else ->
                        append(emoji.unifiedCode)
                }
            }

            if (who.bot) {
                append(" ")
                val emoji = EmojiMap.shortNameMap["robot_face"]
                when {
                    emoji == null ->
                        append("bot")
                    PrefB.bpUseTwemoji.value ->
                        appendSpan("bot", emoji.createSpan(activity))
                    else ->
                        append(emoji.unifiedCode)
                }
            }

            if (who.suspended) {
                append(" ")
                val emoji = EmojiMap.shortNameMap["cross_mark"]
                when {
                    emoji == null ->
                        append("suspended")
                    PrefB.bpUseTwemoji.value ->
                        appendSpan("suspended", emoji.createSpan(activity))
                    else ->
                        append(emoji.unifiedCode)
                }
            }
        }

    private fun encodeMisskeyExtra(whoDetail: TootAccount?) = SpannableStringBuilder().apply {
        var s = whoDetail?.location
        if (s?.isNotEmpty() == true) {
            if (isNotEmpty()) append('\n')
            appendSpan(
                activity.getString(R.string.location),
                EmojiImageSpan(
                    activity,
                    R.drawable.ic_location,
                    useColorShader = true
                )
            )
            append(' ')
            append(s)
        }
        s = whoDetail?.birthday
        if (s?.isNotEmpty() == true) {
            if (isNotEmpty()) append('\n')
            appendSpan(
                activity.getString(R.string.birthday),
                EmojiImageSpan(
                    activity,
                    R.drawable.ic_cake,
                    useColorShader = true
                )
            )
            append(' ')
            append(s)
        }
    }

    private fun showFields(who: TootAccount, fields: List<TootAccount.Field>) {
        views.llFields.visibility = View.VISIBLE

        // fieldsのnameにはカスタム絵文字が適用されるようになった
        // https://github.com/tootsuite/mastodon/pull/11350
        // fieldsのvalueはMisskeyならMFM、MastodonならHTML
        val fieldDecodeOptions = DecodeOptions(
            context = activity,
            decodeEmoji = true,
            linkHelper = accessInfo,
            short = true,
            emojiMapCustom = who.custom_emojis,
            emojiMapProfile = who.profile_emojis,
            authorDomain = who
        )

        val nameTypeface = ActMain.timelineFontBold
        val valueTypeface = ActMain.timelineFont

        for (item in fields) {

            //
            val nameView = MyTextView(activity)
            val nameLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val nameText = fieldDecodeOptions.decodeEmoji(item.name)
            val nameInvalidator = NetworkEmojiInvalidator(activity.handler, nameView)
            nameInvalidator.register(nameText)

            nameLp.topMargin = (density * 6f).toInt()
            nameView.layoutParams = nameLp
            nameView.text = nameText
            nameView.setTextColor(colorTextContent)
            nameView.typeface = nameTypeface
            nameView.movementMethod = MyLinkMovementMethod
            views.llFields.addView(nameView)

            // 値の方はHTMLエンコードされている
            val valueView = MyTextView(activity)
            val valueLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            val valueText = fieldDecodeOptions.decodeHTML(item.value)
            if (item.verified_at > 0L) {
                valueText.append('\n')

                val start = valueText.length
                valueText.append(activity.getString(R.string.verified_at))
                valueText.append(": ")
                valueText.append(TootStatus.formatTime(activity, item.verified_at, false))
                val end = valueText.length

                val linkFgColor = PrefI.ipVerifiedLinkFgColor.value.notZero()
                    ?: (Color.BLACK or 0x7fbc99)

                valueText.setSpan(
                    ForegroundColorSpan(linkFgColor),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            val valueInvalidator = NetworkEmojiInvalidator(activity.handler, valueView)
            valueInvalidator.register(valueText)

            valueLp.startMargin = (density * 32f).toInt()
            valueView.layoutParams = valueLp
            valueView.text = valueText
            valueView.setTextColor(colorTextContent)
            valueView.typeface = valueTypeface
            valueView.movementMethod = MyLinkMovementMethod

            if (item.verified_at > 0L) {
                val linkBgColor = PrefI.ipVerifiedLinkBgColor.value.notZero()
                    ?: (0x337fbc99)

                valueView.setBackgroundColor(linkBgColor)
            }

            views.llFields.addView(valueView)
        }
    }
}
