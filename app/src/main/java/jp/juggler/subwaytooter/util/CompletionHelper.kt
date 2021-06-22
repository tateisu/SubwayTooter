package jp.juggler.subwaytooter.util

import android.content.SharedPreferences
import android.os.Handler
import android.text.*
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.PrefB
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.dialog.EmojiPicker
import jp.juggler.subwaytooter.dialog.EmojiPickerResult
import jp.juggler.subwaytooter.emoji.CustomEmoji
import jp.juggler.subwaytooter.emoji.EmojiBase
import jp.juggler.subwaytooter.emoji.UnicodeEmoji
import jp.juggler.subwaytooter.span.NetworkEmojiSpan
import jp.juggler.subwaytooter.table.AcctSet
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.TagSet
import jp.juggler.subwaytooter.view.MyEditText
import jp.juggler.util.*
import java.util.*
import kotlin.math.min

class CompletionHelper(
    private val activity: AppCompatActivity,
    private val pref: SharedPreferences,
    private val handler: Handler,
) {
    companion object {
        private val log = LogCategory("CompletionHelper")
        private val reCharsNotEmoji = "[^0-9A-Za-z_-]".asciiPattern()
    }

    ///////////////////////////////////////////////////////////////////////////////////
    // 投稿機能はPostImplに移動した
//    var content: String? = null
//    var spoilerText: String? = null
//    var visibility: TootVisibility = TootVisibility.Public
//    var bNSFW = false
//    var inReplyToId: EntityId? = null
//    var attachmentList: ArrayList<PostAttachment>? = null
//    var enqueteItems: ArrayList<String>? = null
//    var pollType: TootPollsType? = null
//    var pollExpireSeconds = 0
//    var pollHideTotals = false
//    var pollMultipleChoice = false
//
//    var emojiMapCustom: HashMap<String, CustomEmoji>? = null
//    var redraftStatusId: EntityId? = null
//    var useQuoteToot = false
//    var scheduledAt = 0L
//    var scheduledId: EntityId? = null

    ///////////////////////////////////////////////////////////////////////////////////
    // 入力補完機能

    private val pickerCaptionEmoji: String by lazy {
        activity.getString(R.string.open_picker_emoji)
    }
    //	private val picker_caption_tag : String by lazy {
    //		activity.getString(R.string.open_picker_tag)
    //	}
    //	private val picker_caption_mention : String by lazy {
    //		activity.getString(R.string.open_picker_mention)
    //	}

    private var callback2: Callback2? = null
    private var et: MyEditText? = null
    private var popup: PopupAutoCompleteAcct? = null
    private var formRoot: View? = null
    private var bMainScreen: Boolean = false

    private var accessInfo: SavedAccount? = null

    private val onEmojiListLoad: (list: ArrayList<CustomEmoji>) -> Unit =
        {
            val popup = this@CompletionHelper.popup
            if (popup?.isShowing == true) procTextChanged.run()
        }

    private val procTextChanged = object : Runnable {

        override fun run() {
            val et = this@CompletionHelper.et
            if (et == null || // EditTextを特定できない
                et.selectionStart != et.selectionEnd || // 範囲選択中
                callback2?.canOpenPopup() != true // 何らかの理由でポップアップが許可されていない
            ) {
                closeAcctPopup()
                return
            }

            checkMention(et, et.text.toString())
        }

        private fun checkMention(et: MyEditText, src: String) {

            fun matchUserNameOrAsciiDomain(cp: Int): Boolean {
                if (cp >= 0x7f) return false
                val c = cp.toChar()

                return '0' <= c && c <= '9' ||
                    'A' <= c && c <= 'Z' ||
                    'a' <= c && c <= 'z' ||
                    c == '_' || c == '-' || c == '.'
            }

            // Letter | Mark | Decimal_Number | Connector_Punctuation
            fun matchIdnWord(cp: Int) = when (Character.getType(cp).toByte()) {
                // Letter
                // LCはエイリアスなので文字から得られることはないはず
                Character.UPPERCASE_LETTER,
                Character.LOWERCASE_LETTER,
                Character.TITLECASE_LETTER,
                Character.MODIFIER_LETTER,
                Character.OTHER_LETTER,
                -> true
                // Mark
                Character.NON_SPACING_MARK,
                Character.COMBINING_SPACING_MARK,
                Character.ENCLOSING_MARK,
                -> true
                // Decimal_Number
                Character.DECIMAL_DIGIT_NUMBER -> true
                // Connector_Punctuation
                Character.CONNECTOR_PUNCTUATION -> true

                else -> false
            }

            var count_atMark = 0
            val end = et.selectionEnd
            var start: Int = -1
            var i = end
            while (i > 0) {
                val cp = src.codePointBefore(i)
                i -= Character.charCount(cp)

                if (cp == '@'.code) {
                    start = i
                    if (++count_atMark >= 2) break else continue
                } else if (count_atMark == 1) {
                    // @username@host の username部分はUnicodeを含まない
                    if (matchUserNameOrAsciiDomain(cp)) continue else break
                } else {
                    // @username@host のhost 部分か、 @username のusername部分
                    // ここはUnicodeを含むかもしれない
                    if (matchUserNameOrAsciiDomain(cp) || matchIdnWord(cp)) continue else break
                }
            }

            if (start == -1) {
                checkTag(et, src)
                return
            }

            // 最低でも2文字ないと補完しない
            if (end - start < 2) {
                closeAcctPopup()
                return
            }

            val limit = 100
            val s = src.substring(start, end)
            val acct_list = AcctSet.searchPrefix(s, limit)
            log.d("search for $s, result=${acct_list.size}")
            if (acct_list.isEmpty()) {
                closeAcctPopup()
            } else {
                openPopup()?.setList(et, start, end, acct_list, null, null)
            }
        }

        private fun checkTag(et: MyEditText, src: String) {

            val end = et.selectionEnd

            val last_sharp = src.lastIndexOf('#', end - 1)

            if (last_sharp == -1 || end - last_sharp < 2) {
                checkEmoji(et, src)
                return
            }

            val part = src.substring(last_sharp + 1, end)
            if (!TootTag.isValid(part, accessInfo?.isMisskey == true)) {
                checkEmoji(et, src)
                return
            }

            val limit = 100
            val s = src.substring(last_sharp + 1, end)
            val tag_list = TagSet.searchPrefix(s, limit)
            log.d("search for $s, result=${tag_list.size}")
            if (tag_list.isEmpty()) {
                closeAcctPopup()
            } else {
                openPopup()?.setList(et, last_sharp, end, tag_list, null, null)
            }
        }

        private fun checkEmoji(et: MyEditText, src: String) {

            val end = et.selectionEnd
            val last_colon = src.lastIndexOf(':', end - 1)
            if (last_colon == -1 || end - last_colon < 1) {
                closeAcctPopup()
                return
            }

            if (!EmojiDecoder.canStartShortCode(src, last_colon)) {
                // : の手前は始端か改行か空白でなければならない
                log.d("checkEmoji: invalid character before shortcode.")
                closeAcctPopup()
                return
            }

            val part = src.substring(last_colon + 1, end)

            if (part.isEmpty()) {
                // :を入力した直後は候補は0で、「閉じる」と「絵文字を選ぶ」だけが表示されたポップアップを出す
                openPopup()?.setList(
                    et, last_colon, end, null, pickerCaptionEmoji, openPickerEmoji
                )
                return
            }

            if (reCharsNotEmoji.matcher(part).find()) {
                // 範囲内に絵文字に使えない文字がある
                closeAcctPopup()
                return
            }

            val code_list = ArrayList<CharSequence>()
            val limit = 100

            // カスタム絵文字の候補を部分一致検索
            code_list.addAll(customEmojiCodeList(accessInfo, limit, part))

            // 通常の絵文字を部分一致で検索
            val remain = limit - code_list.size
            if (remain > 0) {
                val s =
                    src.substring(last_colon + 1, end).lowercase().replace('-', '_')
                val matches = EmojiDecoder.searchShortCode(activity, s, remain)
                log.d("checkEmoji: search for $s, result=${matches.size}")
                code_list.addAll(matches)
            }

            openPopup()?.setList(
                et,
                last_colon,
                end,
                code_list,
                pickerCaptionEmoji,
                openPickerEmoji
            )
        }

        // カスタム絵文字の候補を作る
        private fun customEmojiCodeList(
            accessInfo: SavedAccount?,
            @Suppress("SameParameterValue") limit: Int,
            needle: String,
        ) = ArrayList<CharSequence>().also { dst ->

            accessInfo ?: return@also

            val custom_list =
                App1.custom_emoji_lister.getListWithAliases(accessInfo, onEmojiListLoad)
                    ?: return@also

            for (item in custom_list) {
                if (dst.size >= limit) break
                if (!item.shortcode.contains(needle)) continue

                val sb = SpannableStringBuilder()
                sb.append(' ')
                sb.setSpan(
                    NetworkEmojiSpan(item.url),
                    0,
                    sb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                sb.append(' ')
                if (item.alias != null) {
                    val start = sb.length
                    sb.append(":")
                    sb.append(item.alias)
                    sb.append(": → ")
                    sb.setSpan(
                        ForegroundColorSpan(activity.attrColor(R.attr.colorTimeSmall)),
                        start,
                        sb.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                sb.append(':')
                sb.append(item.shortcode)
                sb.append(':')

                dst.add(sb)
            }
        }
    }

    private fun openPopup(): PopupAutoCompleteAcct? {
        var popup = this@CompletionHelper.popup
        if (popup?.isShowing == true) return popup
        val et = this@CompletionHelper.et ?: return null
        val formRoot = this@CompletionHelper.formRoot ?: return null
        popup = PopupAutoCompleteAcct(activity, et, formRoot, bMainScreen)
        this@CompletionHelper.popup = popup
        return popup
    }

    interface Callback2 {

        fun onTextUpdate()

        fun canOpenPopup(): Boolean
    }

    fun setInstance(accessInfo: SavedAccount?) {
        this.accessInfo = accessInfo

        if (accessInfo != null) {
            App1.custom_emoji_lister.getList(accessInfo, onEmojiListLoad)
        }

        val popup = this.popup
        if (popup?.isShowing == true) {
            procTextChanged.run()
        }
    }

    fun closeAcctPopup() {
        popup?.dismiss()
        popup = null
    }

    fun onScrollChanged() {
        if (popup?.isShowing == true) {
            popup?.updatePosition()
        }
    }

    fun onDestroy() {
        handler.removeCallbacks(procTextChanged)
        closeAcctPopup()
    }

    fun attachEditText(
        formRoot: View,
        et: MyEditText,
        bMainScreen: Boolean,
        callback2: Callback2,
    ) {
        this.formRoot = formRoot
        this.et = et
        this.callback2 = callback2
        this.bMainScreen = bMainScreen

        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence,
                start: Int,
                count: Int,
                after: Int,
            ) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                handler.removeCallbacks(procTextChanged)
                handler.postDelayed(procTextChanged, if (popup?.isShowing == true) 100L else 500L)
            }

            override fun afterTextChanged(s: Editable) {
                this@CompletionHelper.callback2?.onTextUpdate()
            }
        })

        et.setOnSelectionChangeListener(object : MyEditText.OnSelectionChangeListener {
            override fun onSelectionChanged(selStart: Int, selEnd: Int) {
                if (selStart != selEnd) {
                    // 範囲選択されてるならポップアップは閉じる
                    log.d("onSelectionChanged: range selected")
                    closeAcctPopup()
                }
            }
        })

        // 全然動いてなさそう…
        // et.setCustomSelectionActionModeCallback( action_mode_callback );
    }

    private fun SpannableStringBuilder.appendEmoji(result: EmojiPickerResult) =
        appendEmoji(result.bInstanceHasCustomEmoji, result.emoji)

    private fun SpannableStringBuilder.appendEmoji(
        bInstanceHasCustomEmoji: Boolean,
        emoji: EmojiBase,
    ): SpannableStringBuilder {

        val separator = EmojiDecoder.customEmojiSeparator(pref)
        when (emoji) {
            is CustomEmoji -> {
                // カスタム絵文字は常にshortcode表現
                if (!EmojiDecoder.canStartShortCode(this, this.length)) append(separator)
                this.append(SpannableString(":${emoji.shortcode}:"))
                // セパレータにZWSPを使う設定なら、補完した次の位置にもZWSPを追加する。連続して入力補完できるようになる。
                if (separator != ' ') append(separator)
            }
            is UnicodeEmoji -> {
                if (!bInstanceHasCustomEmoji) {
                    // 古いタンスだとshortcodeを使う。見た目は絵文字に変える。
                    if (!EmojiDecoder.canStartShortCode(this, this.length)) append(separator)
                    this.append(DecodeOptions(activity).decodeEmoji(":${emoji.unifiedName}:"))
                    // セパレータにZWSPを使う設定なら、補完した次の位置にもZWSPを追加する。連続して入力補完できるようになる。
                    if (separator != ' ') append(separator)
                } else {
                    // 十分に新しいタンスなら絵文字のunicodeを使う。見た目は絵文字に変える。
                    this.append(DecodeOptions(activity).decodeEmoji(emoji.unifiedCode))
                }
            }
        }
        return this
    }

    private val openPickerEmoji: Runnable = Runnable {
        EmojiPicker(
            activity, accessInfo,
            closeOnSelected = PrefB.bpEmojiPickerCloseOnSelected(pref)
        ) { result ->
            val et = this.et ?: return@EmojiPicker

            val src = et.text ?: ""
            val srcLength = src.length
            val end = min(srcLength, et.selectionEnd)
            val start = src.lastIndexOf(':', end - 1)
            if (start == -1 || end - start < 1) return@EmojiPicker

            val sb = SpannableStringBuilder()
                .append(src.subSequence(0, start))
                .appendEmoji(result)

            val newSelection = sb.length
            if (end < srcLength) sb.append(src.subSequence(end, srcLength))

            et.text = sb
            et.setSelection(newSelection)

            procTextChanged.run()

            // キーボードを再度表示する
            App1.getAppState(
                activity,
                "PostHelper/EmojiPicker/cb"
            ).handler.post { et.showKeyboard() }
        }.show()
    }

    fun openEmojiPickerFromMore() {
        EmojiPicker(
            activity, accessInfo,
            closeOnSelected = PrefB.bpEmojiPickerCloseOnSelected(pref)
        ) { result ->
            val et = this.et ?: return@EmojiPicker

            val src = et.text ?: ""
            val srcLength = src.length
            val start = min(srcLength, et.selectionStart)
            val end = min(srcLength, et.selectionEnd)

            val sb = SpannableStringBuilder()
                .append(src.subSequence(0, start))
                .appendEmoji(result)

            val newSelection = sb.length
            if (end < srcLength) sb.append(src.subSequence(end, srcLength))

            et.text = sb
            et.setSelection(newSelection)

            procTextChanged.run()
        }.show()
    }

    private fun SpannableStringBuilder.appendHashTag(tagWithoutSharp: String): SpannableStringBuilder {
        val separator = ' '
        if (!EmojiDecoder.canStartHashtag(this, this.length)) append(separator)
        this.append('#').append(tagWithoutSharp)
        append(separator)
        return this
    }

    fun openFeaturedTagList(list: List<TootTag>?) {
        val ad = ActionsDialog()
        list?.forEach { tag ->
            ad.addAction("#${tag.name}") {
                val et = this.et ?: return@addAction

                val src = et.text ?: ""
                val srcLength = src.length
                val start = min(srcLength, et.selectionStart)
                val end = min(srcLength, et.selectionEnd)

                val sb = SpannableStringBuilder()
                    .append(src.subSequence(0, start))
                    .appendHashTag(tag.name)
                val newSelection = sb.length
                if (end < srcLength) sb.append(src.subSequence(end, srcLength))

                et.text = sb
                et.setSelection(newSelection)

                procTextChanged.run()
            }
        }
        ad.addAction(activity.getString(R.string.input_sharp_itself)) {
            val et = this.et ?: return@addAction

            val src = et.text ?: ""
            val srcLength = src.length
            val start = min(srcLength, et.selectionStart)
            val end = min(srcLength, et.selectionEnd)

            val sb = SpannableStringBuilder()
            sb.append(src.subSequence(0, start))
            if (!EmojiDecoder.canStartHashtag(sb, sb.length)) sb.append(' ')
            sb.append('#')

            val newSelection = sb.length
            if (end < srcLength) sb.append(src.subSequence(end, srcLength))
            et.text = sb
            et.setSelection(newSelection)

            procTextChanged.run()
        }
        ad.show(activity, activity.getString(R.string.featured_hashtags))
    }

    //	final ActionMode.Callback action_mode_callback = new ActionMode.Callback() {
    //		@Override public boolean onCreateActionMode( ActionMode actionMode, Menu menu ){
    //			actionMode.getMenuInflater().inflate(R.menu.toot_long_tap, menu);
    //			return true;
    //		}
    //		@Override public void onDestroyActionMode( ActionMode actionMode ){
    //
    //		}
    //		@Override public boolean onPrepareActionMode( ActionMode actionMode, Menu menu ){
    //			return false;
    //		}
    //
    //		@Override
    //		public boolean onActionItemClicked( ActionMode actionMode, MenuItem item ){
    //			if (item.getItemId() == R.id.action_pick_emoji) {
    //				actionMode.finish();
    //				EmojiPicker.open( activity, instance, new EmojiPicker.Callback() {
    //					@Override public void onPickedEmoji( String name ){
    //						int end = et.getSelectionEnd();
    //						String src = et.getText().toString();
    //						CharSequence svInsert = ":" + name + ":";
    //						src = src.substring( 0, end ) + svInsert + " " + ( end >= src.length() ? "" : src.substring( end ) );
    //						et.setText( src );
    //						et.setSelection( end + svInsert.length() + 1 );
    //
    //						proc_text_changed.run();
    //					}
    //				} );
    //				return true;
    //			}
    //
    //			return false;
    //		}
    //	};
}
