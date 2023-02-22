package jp.juggler.subwaytooter.actpost

import android.os.Handler
import android.text.*
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootTag
import jp.juggler.subwaytooter.dialog.actionsDialog
import jp.juggler.subwaytooter.dialog.launchEmojiPicker
import jp.juggler.subwaytooter.emoji.CustomEmoji
import jp.juggler.subwaytooter.emoji.EmojiBase
import jp.juggler.subwaytooter.emoji.UnicodeEmoji
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.span.NetworkEmojiSpan
import jp.juggler.subwaytooter.util.emojiSizeMode
import jp.juggler.subwaytooter.table.*
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.EmojiDecoder
import jp.juggler.subwaytooter.util.PopupAutoCompleteAcct
import jp.juggler.subwaytooter.view.MyEditText
import jp.juggler.util.*
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.data.asciiRegex
import jp.juggler.util.log.LogCategory
import jp.juggler.util.ui.attrColor
import jp.juggler.util.ui.showKeyboard
import kotlinx.coroutines.yield
import kotlin.math.min

// 入力補完機能
class CompletionHelper(
    private val activity: AppCompatActivity,
    private val handler: Handler,
) {
    companion object {
        private val log = LogCategory("CompletionHelper")
        private val reCharsNotEmoji = "[^0-9A-Za-z_-]".asciiRegex()

        // 無視するスパン
        // ($を.に変換済)
        val ignoreSpans = setOf(
            "android.text.Selection.END",
            "android.text.Selection.START",
            "android.widget.Editor.SpanController",
            "android.widget.TextView.ChangeWatcher",
            "androidx.emoji2.text.SpannableBuilder.WatcherWrapper",
            "androidx.emoji2.viewsintegration.EmojiKeyListener",

            "android.text.DynamicLayout.ChangeWatcher",
            "android.text.method.TextKeyListener",
            "android.text.method.Touch.DragState",
            "android.text.style.SpellCheckSpan",
        )

        private val reRemoveSpan = """\Qandroid.text.style.\E.+Span""".toRegex()

        private fun matchUserNameOrAsciiDomain(cp: Int): Boolean {
            if (cp >= 0x7f) return false
            val c = cp.toChar()

            return '0' <= c && c <= '9' ||
                    'A' <= c && c <= 'Z' ||
                    'a' <= c && c <= 'z' ||
                    c == '_' || c == '-' || c == '.'
        }

        // Letter | Mark | Decimal_Number | Connector_Punctuation
        private fun matchIdnWord(cp: Int) = when (Character.getType(cp).toByte()) {
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
    }

    interface Callback2 {
        fun onTextUpdate()
        fun canOpenPopup(): Boolean
    }

    private val pickerCaptionEmoji: String by lazy {
        activity.getString(R.string.open_picker_emoji)
    }

    private var callback2: Callback2? = null
    private var et: MyEditText? = null
    private var popup: PopupAutoCompleteAcct? = null
    private var formRoot: View? = null
    private var bMainScreen: Boolean = false

    private var accessInfo: SavedAccount? = null

    private val onEmojiListLoad: (list: List<CustomEmoji>) -> Unit = {
        if (popup?.isShowing == true) procTextChanged.run()
    }

    private val procTextChanged: Runnable = Runnable {
        val et = this.et
        if (et == null || et.selectionStart != et.selectionEnd || callback2?.canOpenPopup() != true) {
            // EditTextを特定できない
            // 範囲選択中
            // 何らかの理由でポップアップが許可されていない
            closeAcctPopup()
        } else {
            checkMention(et, et.text.toString())
        }
    }

    private fun checkMention(et: MyEditText, src: String) {
        // 選択範囲末尾からスキャン
        var countAtmark = 0
        var start: Int = -1
        val end = et.selectionEnd
        var i = end
        while (i > 0) {
            val cp = src.codePointBefore(i)
            i -= Character.charCount(cp)

            if (cp == '@'.code) {
                start = i
                if (++countAtmark >= 2) break else continue
            } else if (countAtmark == 1) {
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
        val acctList = daoAcctSet.searchPrefix(s, limit)
        log.d("search for $s, result=${acctList.size}")
        if (acctList.isEmpty()) {
            closeAcctPopup()
        } else {
            openPopup()?.setList(et, start, end, acctList, null, null)
        }
    }

    private fun checkTag(et: MyEditText, src: String) {

        val end = et.selectionEnd
        val lastSharp = src.lastIndexOf('#', end - 1)
        if (lastSharp == -1 || end - lastSharp < 2) {
            checkEmoji(et, src)
            return
        }

        val part = src.substring(lastSharp + 1, end)
        if (!TootTag.isValid(part, accessInfo?.isMisskey == true)) {
            checkEmoji(et, src)
            return
        }

        val limit = 100
        val s = src.substring(lastSharp + 1, end)
        val tagList = daoTagHistory.searchPrefix(s, limit)
        log.d("search for $s, result=${tagList.size}")
        if (tagList.isEmpty()) {
            closeAcctPopup()
        } else {
            openPopup()?.setList(et, lastSharp, end, tagList, null, null)
        }
    }

    private fun checkEmoji(et: MyEditText, src: String) {
        val end = et.selectionEnd
        val lastColon = src.lastIndexOf(':', end - 1)
        if (lastColon == -1 || end - lastColon < 1) {
            closeAcctPopup()
            return
        }

        if (!EmojiDecoder.canStartShortCode(src, lastColon)) {
            // : の手前は始端か改行か空白でなければならない
            log.d("checkEmoji: invalid character before shortcode.")
            closeAcctPopup()
            return
        }

        val part = src.substring(lastColon + 1, end)

        if (part.isEmpty()) {
            // :を入力した直後は候補は0で、「閉じる」と「絵文字を選ぶ」だけが表示されたポップアップを出す
            openPopup()?.setList(
                et, lastColon, end, null, pickerCaptionEmoji, openPickerEmoji
            )
            return
        }

        if (reCharsNotEmoji.containsMatchIn(part)) {
            // 範囲内に絵文字に使えない文字がある
            closeAcctPopup()
            return
        }

        val codeList = ArrayList<CharSequence>()
        val limit = 100

        // カスタム絵文字の候補を部分一致検索
        codeList.addAll(customEmojiCodeList(accessInfo, limit, part))

        // 通常の絵文字を部分一致で検索
        val remain = limit - codeList.size
        if (remain > 0) {
            val s = src.substring(lastColon + 1, end)
                .lowercase()
                .replace('-', '_')
            val matches = EmojiDecoder.searchShortCode(activity, s, remain)
            log.d("checkEmoji: search for $s, result=${matches.size}")
            codeList.addAll(matches)
        }

        openPopup()?.setList(
            et,
            lastColon,
            end,
            codeList,
            pickerCaptionEmoji,
            openPickerEmoji
        )
    }

    // カスタム絵文字の候補を作る
    private fun customEmojiCodeList(
        accessInfo: SavedAccount?,
        @Suppress("SameParameterValue") limit: Int,
        needle: String,
    ) = buildList<CharSequence> {
        accessInfo ?: return@buildList

        val customList = App1.custom_emoji_lister.tryGetList(
            accessInfo,
            withAliases = true,
            callback = onEmojiListLoad
        )
            ?: return@buildList

        for (item in customList) {
            if (size >= limit) break
            if (!item.shortcode.contains(needle)) continue

            val sb = SpannableStringBuilder()
            sb.append(' ')
            sb.setSpan(
                NetworkEmojiSpan(item.url, sizeMode = accessInfo.emojiSizeMode()),
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
            add(sb)
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

    fun setInstance(accessInfo: SavedAccount?) {
        this.accessInfo = accessInfo
        accessInfo?.let {
            App1.custom_emoji_lister.tryGetList(
                it,
                callback = onEmojiListLoad
            )
        }
        if (popup?.isShowing == true) procTextChanged.run()
    }

    fun closeAcctPopup() {
        popup?.dismiss()
        popup = null
    }

    fun onScrollChanged() {
        popup?.takeIf { it.isShowing }?.updatePosition()
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
                // ペースト時に余計な装飾を取り除く
                val spans = s.getSpans(0, s.length, Any::class.java)

                val isImeComposing =
                    spans.any { it?.javaClass?.name == "android.view.inputmethod.ComposingText" }

                if (!isImeComposing) {
                    spans?.filter {
                        val name = (it?.javaClass?.name ?: "").replace('$', '.')
                        when {
                            ignoreSpans.contains(name) -> false

                            reRemoveSpan.matches(name) -> {
                                log.i("span remove $name")
                                true
                            }

                            else -> {
                                log.i("span keep $name")
                                false
                            }
                        }
                    }
                        ?.map { Triple(it, s.getSpanStart(it), s.getSpanEnd(it)) }
                        ?.sortedBy { -it.second }
                        ?.forEach {
                            s.removeSpan(it.first)
                        }
                }

                this@CompletionHelper.callback2?.onTextUpdate()
            }
        })

        // 範囲選択されてるならポップアップは閉じる
        et.onSelectionChange = { selStart, selEnd ->
            if (selStart != selEnd) {
                log.d("onSelectionChange: range selected")
                closeAcctPopup()
            }
        }

        // 全然動いてなさそう…
        // et.setCustomSelectionActionModeCallback( action_mode_callback );
    }

    private fun SpannableStringBuilder.appendEmoji(
        emoji: EmojiBase,
        bInstanceHasCustomEmoji: Boolean,
    ) = appendEmoji(bInstanceHasCustomEmoji, emoji)

    private fun SpannableStringBuilder.appendEmoji(
        bInstanceHasCustomEmoji: Boolean,
        emoji: EmojiBase,
    ): SpannableStringBuilder {

        val separator = EmojiDecoder.customEmojiSeparator()
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
        launchEmojiPicker(
            activity,
            accessInfo,
            closeOnSelected = PrefB.bpEmojiPickerCloseOnSelected.value
        ) { emoji, bInstanceHasCustomEmoji ->
            val et = this@CompletionHelper.et ?: return@launchEmojiPicker

            val src = et.text ?: ""
            val srcLength = src.length
            val end = min(srcLength, et.selectionEnd)
            val start = src.lastIndexOf(':', end - 1)
            if (start == -1 || end - start < 1) return@launchEmojiPicker

            val sb = SpannableStringBuilder()
                .append(src.subSequence(0, start))
                .appendEmoji(emoji, bInstanceHasCustomEmoji)

            val newSelection = sb.length
            if (end < srcLength) sb.append(src.subSequence(end, srcLength))

            et.text = sb
            et.setSelection(newSelection)

            procTextChanged.run()

            // キーボードを再度表示する
            yield()
            et.showKeyboard()
        }
    }

    fun openEmojiPickerFromMore() {
        launchEmojiPicker(
            activity,
            accessInfo,
            closeOnSelected = PrefB.bpEmojiPickerCloseOnSelected.value
        ) { emoji, bInstanceHasCustomEmoji ->
            val et = this@CompletionHelper.et ?: return@launchEmojiPicker

            val src = et.text ?: ""
            val srcLength = src.length
            val start = min(srcLength, et.selectionStart)
            val end = min(srcLength, et.selectionEnd)

            val sb = SpannableStringBuilder()
                .append(src.subSequence(0, start))
                .appendEmoji(emoji, bInstanceHasCustomEmoji)

            val newSelection = sb.length
            if (end < srcLength) sb.append(src.subSequence(end, srcLength))

            et.text = sb
            et.setSelection(newSelection)

            procTextChanged.run()
        }
    }

    private fun SpannableStringBuilder.appendHashTag(tagWithoutSharp: String): SpannableStringBuilder {
        val separator = ' '
        if (!EmojiDecoder.canStartHashtag(this, this.length)) append(separator)
        this.append('#').append(tagWithoutSharp)
        append(separator)
        return this
    }

    fun openFeaturedTagList(list: List<TootTag>?) {
        val et = this@CompletionHelper.et ?: return
        activity.run {
            launchAndShowError {
                actionsDialog(getString(R.string.featured_hashtags)) {
                    list?.forEach { tag ->
                        action("#${tag.name}") {
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
                    action(activity.getString(R.string.input_sharp_itself)) {
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
                }
            }
        }
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
