package jp.juggler.subwaytooter.util

import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.view.View

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import java.util.ArrayList
import java.util.regex.Pattern

import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootTask
import jp.juggler.subwaytooter.api.TootTaskRunner
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.DlgConfirm
import jp.juggler.subwaytooter.dialog.EmojiPicker
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.span.NetworkEmojiSpan
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.AcctSet
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.TagSet
import jp.juggler.subwaytooter.view.MyEditText
import okhttp3.Request
import okhttp3.RequestBody

class PostHelper(
	private val activity : AppCompatActivity,
	private val pref : SharedPreferences,
	private val handler : Handler
){
	
	companion object {
		private val log = LogCategory("PostHelper")
		
		// [:word:] 単語構成文字 (Letter | Mark | Decimal_Number | Connector_Punctuation)
		// [:alpha:] 英字 (Letter | Mark)
		
		private const val word = "[_\\p{L}\\p{M}\\p{Nd}\\p{Pc}]"
		private const val alpha = "[_\\p{L}\\p{M}]"
		
		private val reTag = Pattern.compile(
			"(?:^|[^/)\\w])#($word*$alpha$word*)", Pattern.CASE_INSENSITIVE
		)
		
		private val reCharsNotTag = Pattern.compile("[・\\s\\-+.,:;/]")
		private val reCharsNotEmoji = Pattern.compile("[^0-9A-Za-z_-]")
		
		private val version_1_6 = VersionString("1.6")
	}
	
	///////////////////////////////////////////////////////////////////////////////////
	// 投稿機能
	
	var content : String? = null
	var spoiler_text : String? = null
	var visibility : String? = null
	var bNSFW : Boolean = false
	var in_reply_to_id : Long = 0
	var attachment_list : ArrayList<PostAttachment>? = null
	var enquete_items : ArrayList<String>? = null
	
	fun post(account : SavedAccount, bConfirmTag : Boolean, bConfirmAccount : Boolean, callback : PostCompleteCallback) {
		val content = this.content ?:""
		val spoiler_text = this.spoiler_text
		val bNSFW = this.bNSFW
		val in_reply_to_id = this.in_reply_to_id
		val attachment_list = this.attachment_list
		val enquete_items = this.enquete_items
		var visibility = this.visibility ?:""
		
		if(content.isEmpty() ) {
			Utils.showToast(activity, true, R.string.post_error_contents_empty)
			return
		}
		
		// nullはCWチェックなしを示す
		// nullじゃなくてカラならエラー
		if(spoiler_text != null && spoiler_text.isEmpty() ) {
			Utils.showToast(activity, true, R.string.post_error_contents_warning_empty)
			return
		}
		
		if(visibility.isEmpty() ) {
			visibility = TootStatus.VISIBILITY_PUBLIC
		}
		
		if(enquete_items?.isNotEmpty() == true) {
			var n = 0
			val ne = enquete_items.size
			while(n < ne) {
				val item = enquete_items[n]
				if(item.isEmpty()) {
					if(n < 2) {
						Utils.showToast(activity, true, R.string.enquete_item_is_empty, n + 1)
						return
					}
				} else {
					val code_count = item.codePointCount(0, item.length)
					if(code_count > 15) {
						val over = code_count - 15
						Utils.showToast(activity, true, R.string.enquete_item_too_long, n + 1, over)
						return
					} else if(n > 0) {
						for(i in 0 until n) {
							if(item == enquete_items[i]) {
								Utils.showToast(activity, true, R.string.enquete_item_duplicate, n + 1)
								return
							}
						}
					}
				}
				++ n
			}
		}
		
		if(! bConfirmAccount) {
			DlgConfirm.open(activity, activity.getString(R.string.confirm_post_from, AcctColor.getNickname(account.acct)), object : DlgConfirm.Callback {
				override var isConfirmEnabled : Boolean
					get() = account.confirm_post
					set(bv) {
						account.confirm_post = bv
						account.saveSetting()
					}
				
				override fun onOK() {
					post(account, bConfirmTag, true, callback)
				}
			})
			return
		}
		
		if(! bConfirmTag) {
			val m = reTag.matcher(content)
			if(m.find() && TootStatus.VISIBILITY_PUBLIC != visibility) {
				AlertDialog.Builder(activity)
					.setCancelable(true)
					.setMessage(R.string.hashtag_and_visibility_not_match)
					.setNegativeButton(R.string.cancel, null)
					.setPositiveButton(R.string.ok) { _, _ -> post(account, true, bConfirmAccount, callback) }
					.show()
				return
			}
		}
		
		TootTaskRunner(activity).run(account, object : TootTask {
			
			internal var status : TootStatus? = null
			
			internal var instance_tmp : TootInstance? = null
			
			internal var credential_tmp : TootAccount? = null
			
			internal fun getInstanceInformation(client : TootApiClient) : TootApiResult? {
				val result = client.request("/api/v1/instance")
				instance_tmp = parseItem(::TootInstance,result?.jsonObject)
				return result
			}
			
			internal fun getCredential(client : TootApiClient) : TootApiResult? {
				val result = client.request("/api/v1/accounts/verify_credentials")
				credential_tmp = TootAccount.parse(activity, account, result?.jsonObject)
				return result
			}
			
			override fun background(client : TootApiClient) : TootApiResult? {
				
				var visibility_checked : String? = visibility
				
				if(TootStatus.VISIBILITY_WEB_SETTING == visibility) {
					var instance = account.instance
					if(instance == null) {
						val r2 = getInstanceInformation(client)
						instance = instance_tmp ?: return r2
						account.instance = instance
					}
					visibility_checked = if(instance.isEnoughVersion(version_1_6)) {
						null
					} else {
						val r2 = getCredential(client)
						val credential_tmp = this.credential_tmp
							?: return r2
						credential_tmp.source?.privacy
							?: return TootApiResult(activity.getString(R.string.cant_get_web_setting_visibility))
					}
				}
				
				val request_body : RequestBody
				val body_string : String
				
				if(enquete_items?.isNotEmpty() == true) {
					
					val json = JSONObject()
					try {
						json.put("status", EmojiDecoder.decodeShortCode(content ))
						if(visibility_checked != null) {
							json.put("visibility", visibility_checked)
						}
						json.put("sensitive", bNSFW)
						json.put("spoiler_text", EmojiDecoder.decodeShortCode(spoiler_text?:""))
						json.put("in_reply_to_id", if(in_reply_to_id == - 1L) null else in_reply_to_id)
						var array = JSONArray()
						if(attachment_list != null) {
							for(pa in attachment_list) {
								val a = pa.attachment
								if(a != null) array.put(a.id)
							}
						}
						json.put("media_ids", array)
						json.put("isEnquete", true)
						array = JSONArray()
						for(item in enquete_items) {
							array.put(EmojiDecoder.decodeShortCode(item))
						}
						json.put("enquete_items", array)
					} catch(ex : JSONException) {
						log.trace(ex)
						log.e(ex, "status encoding failed.")
					}
					
					body_string = json.toString()
					request_body = RequestBody.create(
						TootApiClient.MEDIA_TYPE_JSON, body_string
					)
				} else {
					val sb = StringBuilder()
					
					sb.append("status=")
					sb.append(Uri.encode(EmojiDecoder.decodeShortCode(content )))
					
					if(visibility_checked != null) {
						sb.append("&visibility=")
						sb.append(Uri.encode(visibility_checked))
					}
					
					if(bNSFW) {
						sb.append("&sensitive=1")
					}
					
					if(spoiler_text?.isNotEmpty()==true) {
						sb.append("&spoiler_text=")
						sb.append(Uri.encode(EmojiDecoder.decodeShortCode(spoiler_text)))
					}
					
					if(in_reply_to_id != - 1L) {
						sb.append("&in_reply_to_id=")
						sb.append(in_reply_to_id.toString())
					}
					
					if(attachment_list != null) {
						for(pa in attachment_list) {
							val a = pa.attachment
							if(a != null) sb.append("&media_ids[]=").append(a.id)
						}
					}
					
					body_string = sb.toString()
					request_body = RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED, body_string
					)
				}
				
				val request_builder = Request.Builder()
					.post(request_body)
				val digest = Utils.digestSHA256(body_string + account.acct)
				
				if(digest != null && ! pref.getBoolean(Pref.KEY_DONT_DUPLICATION_CHECK, false)) {
					request_builder.header("Idempotency-Key", digest)
				}
				
				val result = client.request("/api/v1/statuses", request_builder)
				val status = TootParser(activity, account).status(result?.jsonObject)
				this.status = status
				if(status != null) {
					// タグを覚えておく
					val s = status.decoded_content
					val span_list = s.getSpans(0, s.length, MyClickableSpan::class.java)
					if(span_list != null) {
						val tag_list = ArrayList<String?>(span_list.size)
						for(span in span_list) {
							val start = s.getSpanStart(span)
							val end = s.getSpanEnd(span)
							val text = s.subSequence(start, end).toString()
							if(text.startsWith("#")) {
								tag_list.add(text.substring(1))
							}
						}
						val count = tag_list.size
						if(count > 0) {
							TagSet.saveList(System.currentTimeMillis(), tag_list, 0, count)
						}
						
					}
				}
				return result
			}
			
			override fun handleResult(result : TootApiResult?) {
				result ?: return // cancelled.
				
				val status = this.status
				if(status != null) {
					// 連投してIdempotency が同じだった場合もエラーにはならず、ここを通る
					callback(account, status)
				} else {
					Utils.showToast(activity, true, result.error)
				}
				
			}
		})
	}
	
	///////////////////////////////////////////////////////////////////////////////////
	// 入力補完機能
	
	private val picker_caption_emoji : String by lazy {
		activity.getString(R.string.open_picker_emoji)
	}
	//	private val picker_caption_tag : String by lazy {
	//		activity.getString(R.string.open_picker_tag)
	//	}
	//	private val picker_caption_mention : String by lazy {
	//		activity.getString(R.string.open_picker_mention)
	//	}
	
	private var callback2 : Callback2? = null
	private var et : MyEditText? = null
	private var popup : PopupAutoCompleteAcct? = null
	private var formRoot : View? = null
	private var bMainScreen : Boolean = false
	
	private var instance : String? = null
	
	private val onEmojiListLoad : (list : ArrayList<CustomEmoji> ) -> Unit
		= { _ : ArrayList<CustomEmoji> ->
			val popup = this@PostHelper.popup
			if(popup?.isShowing == true) proc_text_changed.run()
		}
	
	private val proc_text_changed = object : Runnable {
		override fun run() {
			val et = this@PostHelper.et
			if( et==null || callback2?.canOpenPopup() != true) {
				closeAcctPopup()
				return
			}
			
			var start = et .selectionStart
			val end = et .selectionEnd
			if(start != end) {
				closeAcctPopup()
				return
			}
			val src = et .text.toString()
			var count_atMark = 0
			val pos_atMark = IntArray(2)
			while(true) {
				if(count_atMark >= 2) break
				
				if(start == 0) break
				val c = src[start - 1]
				
				if(c == '@') {
					-- start
					pos_atMark[count_atMark ++] = start
					continue
				} else if('0' <= c && c <= '9'
					|| 'A' <= c && c <= 'Z'
					|| 'a' <= c && c <= 'z'
					|| c == '_' || c == '-' || c == '.') {
					-- start
					continue
				}
				// その他の文字種が出たら探索打ち切り
				break
			}
			// 登場した@の数
			start = when(count_atMark) {
				1 -> pos_atMark[0]
				2 -> pos_atMark[1]
				
				else -> {
					// 次はAcctじゃなくてHashtagの補完を試みる
					checkTag()
					return
				}
			}
			// 最低でも2文字ないと補完しない
			// 最低でも2文字ないと補完しない
			if(end - start < 2) {
				closeAcctPopup()
				return
			}
			val limit = 100
			val s = src.substring(start, end)
			val acct_list = AcctSet.searchPrefix(s, limit)
			log.d("search for %s, result=%d", s, acct_list.size)
			if(acct_list.isEmpty()) {
				closeAcctPopup()
			} else {
				openPopup()?.setList(et , start, end, acct_list, null, null)
			}
		}
		
		private fun checkTag() {
			val et = this@PostHelper.et ?: return
			
			val end = et.selectionEnd
			
			val src = et.text.toString()
			val last_sharp = src.lastIndexOf('#', end - 1)
			
			if(last_sharp == - 1 || end - last_sharp < 2) {
				checkEmoji()
				return
			}
			
			val part = src.substring(last_sharp + 1, end)
			if(reCharsNotTag.matcher(part).find()) {
				// log.d( "checkTag: character not tag in string %s", part );
				checkEmoji()
				return
			}
			
			val limit = 100
			val s = src.substring(last_sharp + 1, end)
			val tag_list = TagSet.searchPrefix(s, limit)
			log.d("search for %s, result=%d", s, tag_list.size)
			if(tag_list.isEmpty()) {
				closeAcctPopup()
			} else {
				openPopup()?.setList(et, last_sharp, end, tag_list, null, null)
			}
		}
		
		private fun checkEmoji() {
			val et = this@PostHelper.et ?: return
			
			val end = et.selectionEnd
			val src = et.text.toString()
			val last_colon = src.lastIndexOf(':', end - 1)
			
			if(last_colon == - 1 || end - last_colon < 1) {
				closeAcctPopup()
				return
			}
			val part = src.substring(last_colon + 1, end)
			
			if(reCharsNotEmoji.matcher(part).find()) {
				log.d("checkEmoji: character not short code in string %s", part)
				closeAcctPopup()
				return
			}
			
			// : の手前は始端か改行か空白でなければならない
			if(last_colon > 0 && ! CharacterGroup.isWhitespace(src.codePointBefore(last_colon))) {
				log.d("checkEmoji: invalid character before shortcode.")
				closeAcctPopup()
				return
			}
			
			if(part.isEmpty()) {
				openPopup()?.setList(
					et, last_colon, end, null, picker_caption_emoji, open_picker_emoji
				)
			} else {
				// 絵文字を部分一致で検索
				val limit = 100
				val s = src.substring(last_colon + 1, end).toLowerCase().replace('-', '_')
				val code_list = EmojiDecoder.searchShortCode(activity, s, limit)
				log.d("checkEmoji: search for %s, result=%d", s, code_list.size)
				
				// カスタム絵文字を検索
				val instance = this@PostHelper.instance
				if(instance != null && instance.isNotEmpty()) {
					val custom_list = App1.custom_emoji_lister.getList(instance, onEmojiListLoad)
					if(custom_list != null) {
						val needle = src.substring(last_colon + 1, end)
						for(item in custom_list) {
							if(code_list.size >= limit) break
							if(! item.shortcode.contains(needle)) continue
							
							val sb = SpannableStringBuilder()
							sb.append(' ')
							sb.setSpan(NetworkEmojiSpan(item.url), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
							sb.append(' ')
							sb.append(':')
							sb.append(item.shortcode)
							sb.append(':')
							code_list.add(sb)
						}
					}
				}
				
				openPopup()?.setList(et, last_colon, end, code_list, picker_caption_emoji, open_picker_emoji)
			}
			
		}
	}
	
	private fun openPopup() : PopupAutoCompleteAcct? {
		var popup = this@PostHelper.popup
		if(popup?.isShowing == true) return popup
		val et = this@PostHelper.et ?: return null
		val formRoot = this@PostHelper.formRoot ?: return null
		popup = PopupAutoCompleteAcct(activity, et, formRoot, bMainScreen)
		this@PostHelper.popup = popup
		return popup
	}

	
	interface Callback2 {
		fun onTextUpdate()
		
		fun canOpenPopup() : Boolean
	}
	
	fun setInstance(_instance : String?) {
		val instance = _instance?.toLowerCase()
		this.instance = instance
		
		if(instance != null) {
			App1.custom_emoji_lister.getList(instance, onEmojiListLoad)
		}
		
		val popup = this.popup
		if(popup?.isShowing == true) {
			proc_text_changed.run()
		}
	}
	
	fun closeAcctPopup() {
		popup?.dismiss()
		popup = null
	}
	
	fun onScrollChanged() {
		if(popup?.isShowing == true) {
			popup?.updatePosition()
		}
	}
	
	fun onDestroy() {
		handler.removeCallbacks(proc_text_changed)
		closeAcctPopup()
	}
	
	fun attachEditText(_formRoot : View, et : MyEditText, bMainScreen : Boolean, _callback2 : Callback2) {
		this.formRoot = _formRoot
		this.et = et
		this.callback2 = _callback2
		this.bMainScreen = bMainScreen
		
		et .addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s : CharSequence, start : Int, count : Int, after : Int) {
			
			}
			
			override fun onTextChanged(s : CharSequence, start : Int, before : Int, count : Int) {
				handler.removeCallbacks(proc_text_changed)
				handler.postDelayed(proc_text_changed, if(popup?.isShowing ==true ) 100L else 500L)
			}
			
			override fun afterTextChanged(s : Editable) {
				callback2?.onTextUpdate()
			}
		})
		
		et .setOnSelectionChangeListener(object : MyEditText.OnSelectionChangeListener {
			override fun onSelectionChanged(selStart : Int, selEnd : Int) {
				if(selStart != selEnd) {
					// 範囲選択されてるならポップアップは閉じる
					log.d("onSelectionChanged: range selected")
					closeAcctPopup()
				}
			}
		})
		
		// 全然動いてなさそう…
		// et.setCustomSelectionActionModeCallback( action_mode_callback );
		
	}
	
	private val open_picker_emoji :Runnable = Runnable {
		EmojiPicker(activity, instance){ name ->
			val et = this.et ?: return@EmojiPicker

			val src = et.text.toString()
			val end = et.selectionEnd

			val last_colon = src.lastIndexOf(':', end - 1)
			if(last_colon == - 1 || end - last_colon < 1) return@EmojiPicker

			val svInsert = ":$name: "
			val newText = StringBuilder()
				.append(src.substring(0, last_colon))
				.append(svInsert)
				.append( if(end >= src.length) "" else src.substring(end) )
				.toString()
			
			et.setText(newText)
			et.setSelection(last_colon + svInsert.length )
			
			proc_text_changed.run()
			
			// キーボードを再度表示する
			Handler(activity.mainLooper).post { Utils.showKeyboard(activity, et) }
			
		}.show()
	}
	
	fun openEmojiPickerFromMore(){
		EmojiPicker(activity, instance){ name->
			val et = this.et?:return@EmojiPicker
			val src = et.text.toString()
			val end = et.selectionEnd
			val insert_start = if( end >= src.length ) src.length else end
			
			val svInsert = ":$name: "
			val newText = StringBuilder()
				.append(src.substring(0,insert_start))
				.append(svInsert)
				.append( src.substring(insert_start) )
				.toString()
			
			et.setText(newText)
			et.setSelection(insert_start + svInsert.length )
			
			proc_text_changed.run()
		}.show()
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
