package jp.juggler.subwaytooter.util

import android.content.SharedPreferences
import android.os.Handler
import android.os.SystemClock
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.*
import android.text.style.ForegroundColorSpan
import android.view.View
import jp.juggler.emoji.EmojiMap201709

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import java.util.ArrayList
import java.util.regex.Pattern

import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.Styler
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
import java.lang.ref.WeakReference
import java.util.HashMap

class PostHelper(
	private val activity : AppCompatActivity,
	private val pref : SharedPreferences,
	private val handler : Handler
) {
	
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
		
	}
	
	///////////////////////////////////////////////////////////////////////////////////
	// 投稿機能
	
	var content : String? = null
	var spoiler_text : String? = null
	var visibility : TootVisibility = TootVisibility.Public
	var bNSFW : Boolean = false
	var in_reply_to_id : EntityId? = null
	var attachment_list : ArrayList<PostAttachment>? = null
	var enquete_items : ArrayList<String>? = null
	var emojiMapCustom : HashMap<String, CustomEmoji>? = null
	var redraft_status_id : EntityId? = null
	
	private var last_post_tapped : Long = 0L
	
	private var last_post_task : WeakReference<TootTaskRunner>? = null
	
	fun post(
		account : SavedAccount,
		bConfirmTag : Boolean = false,
		bConfirmAccount : Boolean = false,
		bConfirmRedraft : Boolean = false,
		callback : PostCompleteCallback
	) {
		val content = this.content ?: ""
		val spoiler_text = this.spoiler_text
		val bNSFW = this.bNSFW
		val in_reply_to_id = this.in_reply_to_id
		val attachment_list = this.attachment_list
		val enquete_items = this.enquete_items
		val visibility = this.visibility
		
		val hasAttachment = attachment_list?.isNotEmpty() ?: false
		
		if(! hasAttachment && content.isEmpty()) {
			showToast(activity, true, R.string.post_error_contents_empty)
			return
		}
		
		// nullはCWチェックなしを示す
		// nullじゃなくてカラならエラー
		if(spoiler_text != null && spoiler_text.isEmpty()) {
			showToast(activity, true, R.string.post_error_contents_warning_empty)
			return
		}
		
		if(enquete_items?.isNotEmpty() == true) {
			var n = 0
			val ne = enquete_items.size
			while(n < ne) {
				val item = enquete_items[n]
				if(item.isEmpty()) {
					if(n < 2) {
						showToast(activity, true, R.string.enquete_item_is_empty, n + 1)
						return
					}
				} else {
					val code_count = item.codePointCount(0, item.length)
					if(code_count > 15) {
						val over = code_count - 15
						showToast(activity, true, R.string.enquete_item_too_long, n + 1, over)
						return
					} else if(n > 0) {
						for(i in 0 until n) {
							if(item == enquete_items[i]) {
								showToast(activity, true, R.string.enquete_item_duplicate, n + 1)
								return
							}
						}
					}
				}
				++ n
			}
		}
		
		if(! bConfirmAccount) {
			DlgConfirm.open(
				activity,
				activity.getString(R.string.confirm_post_from, AcctColor.getNickname(account.acct)),
				object : DlgConfirm.Callback {
					override var isConfirmEnabled : Boolean
						get() = account.confirm_post
						set(bv) {
							account.confirm_post = bv
							account.saveSetting()
						}
					
					override fun onOK() {
						post(account, bConfirmTag, true, bConfirmRedraft, callback)
					}
				})
			return
		}
		
		if(! bConfirmTag) {
			
			if(! account.isMisskey
				&& visibility != TootVisibility.Public
				&& reTag.matcher(content).find()
			) {
				AlertDialog.Builder(activity)
					.setCancelable(true)
					.setMessage(R.string.hashtag_and_visibility_not_match)
					.setNegativeButton(R.string.cancel, null)
					.setPositiveButton(R.string.ok) { _, _ ->
						post(
							account,
							true,
							bConfirmAccount,
							bConfirmRedraft,
							callback
						)
					}
					.show()
				return
			}
			// MisskeyのWebUIはタグ種別による警告とかはないみたいだ
		}
		
		if(! bConfirmRedraft && redraft_status_id != null) {
			AlertDialog.Builder(activity)
				.setCancelable(true)
				.setMessage(R.string.delete_base_status_before_toot)
				.setNegativeButton(R.string.cancel, null)
				.setPositiveButton(R.string.ok) { _, _ ->
					post(
						account,
						bConfirmTag,
						bConfirmAccount,
						true,
						callback
					)
				}
				.show()
			return
		}
		
		// 確認を終えたらボタン連打判定
		
		if(last_post_task?.get()?.isActive == true) {
			showToast(activity, false, R.string.post_button_tapped_repeatly)
			return
		}
		
		val now = SystemClock.elapsedRealtime()
		val delta = now - last_post_tapped
		last_post_tapped = now
		if(delta < 1000L) {
			showToast(activity, false, R.string.post_button_tapped_repeatly)
			return
		}
		
		// 全ての確認を終えたらバックグラウンドでの処理を開始する
		last_post_task = WeakReference(TootTaskRunner(activity
			, progressSetupCallback = { progressDialog ->
				progressDialog.setCanceledOnTouchOutside(false)
			}
		).run(account, object : TootTask {
			
			var status : TootStatus? = null
			
			var instance_tmp : TootInstance? = null
			
			var credential_tmp : TootAccount? = null
			
			val parser = TootParser(activity, account)
			
			fun getInstanceInformation(client : TootApiClient) : TootApiResult? {
				val result = if(account.isMisskey) {
					val params = JSONObject().apply {
						put("dummy", 1)
					}
					client.request("/api/meta", params.toPostRequestBuilder())
				} else {
					client.request("/api/v1/instance")
				}
				instance_tmp = parseItem(::TootInstance, parser, result?.jsonObject)
				return result
			}
			
			fun getCredential(client : TootApiClient) : TootApiResult? {
				val result = client.request("/api/v1/accounts/verify_credentials")
				credential_tmp = parser.account(result?.jsonObject)
				return result
			}
			
			override fun background(client : TootApiClient) : TootApiResult? {
				
				var result : TootApiResult?
				
				// 元の投稿を削除する
				if(redraft_status_id != null) {
					result = if(isMisskey) {
						val params = account.putMisskeyApiToken(JSONObject()).apply {
							put("noteId", redraft_status_id)
						}
						client.request(
							"/api/notes/delete",
							params.toPostRequestBuilder()
						)
					} else {
						client.request(
							"/api/v1/statuses/$redraft_status_id",
							Request.Builder().delete()
						)
						
					}
					log.d("delete redraft. result=$result")
					Thread.sleep(2000L)
				}
				
				var visibility_checked : TootVisibility? = visibility
				
				var instance = account.instance
				if(instance == null) {
					val r2 = getInstanceInformation(client)
					instance = instance_tmp ?: return r2
					account.instance = instance
				}
				
				if(visibility == TootVisibility.WebSetting) {
					visibility_checked =
						if(account.isMisskey || instance.versionGE(TootInstance.VERSION_1_6)) {
							null
						} else {
							val r2 = getCredential(client)
							val credential_tmp = this.credential_tmp ?: return r2
							val privacy = credential_tmp.source?.privacy
								?: return TootApiResult(activity.getString(R.string.cant_get_web_setting_visibility))
							TootVisibility.parseMastodon(privacy)
						}
				}
				
				val json = JSONObject()
				try {
					if(account.isMisskey) {
						account.putMisskeyApiToken(json)
						json.put(
							"text",
							EmojiDecoder.decodeShortCode(
								content,
								emojiMapCustom = emojiMapCustom
							)
						)
						if(visibility_checked != null) {
							
							if(visibility_checked == TootVisibility.DirectSpecified) {
								val userIds = JSONArray()
								val reMention =
									Pattern.compile("(?:\\A|\\s)@([a-zA-Z0-9_]{1,20})(?:@([\\w.:-]+))?(?:\\z|\\s)")
								val m = reMention.matcher(content)
								while(m.find()) {
									val username = m.group(1)
									val host = m.group(2)
									val queryParams = account.putMisskeyApiToken(JSONObject())
									if(username?.isNotEmpty() == true) queryParams.put(
										"username",
										username
									)
									if(host?.isNotEmpty() == true) queryParams.put("host", host)
									result = client.request(
										"/api/users/show",
										queryParams.toPostRequestBuilder()
									)
									val id = result?.jsonObject?.parseString("id")
									if(id?.isNotEmpty() == true) {
										userIds.put(id)
									}
								}
								json.put(
									"visibility", if(userIds.length() == 0) {
										"private"
									} else {
										json.put("visibleUserIds", userIds)
										"specified"
									}
								)
							} else {
								json.put("visibility", visibility_checked.strMisskey)
							}
						}
						
						if(spoiler_text?.isNotEmpty() == true) {
							json.put(
								"cw",
								EmojiDecoder.decodeShortCode(
									spoiler_text,
									emojiMapCustom = emojiMapCustom
								)
							)
						}
						
						if(in_reply_to_id != null) {
							json.put("replyId", in_reply_to_id.toString())
						}
						
						json.put("viaMobile", true)
						
						if(attachment_list != null) {
							val array = JSONArray()
							for(pa in attachment_list) {
								val a = pa.attachment ?: continue
								// Misskeyは画像の再利用に問題がないので redraftとバージョンのチェックは行わない
								array.put(a.id.toString())
								
								// Misskeyの場合、NSFWするにはアップロード済みの画像を drive/files/update で更新する
								if(bNSFW) {
									val params = account.putMisskeyApiToken(JSONObject())
										.put("fileId", a.id.toString())
										.put("isSensitive", true)
									val r = client.request(
										"/api/drive/files/update",
										params.toPostRequestBuilder()
									)
									if(r == null || r.error != null) return r
								}
							}
							if(array.length() > 0) json.put("mediaIds", array)
						}
						
						if(enquete_items?.isNotEmpty() == true) {
							val choices = JSONArray().apply {
								for(item in enquete_items) {
									val text = EmojiDecoder.decodeShortCode(
										item,
										emojiMapCustom = emojiMapCustom
									)
									if(text.isEmpty()) continue
									put(text)
								}
							}
							if(choices.length() > 0) {
								json.put("poll", JSONObject().apply {
									put("choices", choices)
								})
							}
						}
						
					} else {
						json.put(
							"status",
							EmojiDecoder.decodeShortCode(
								content,
								emojiMapCustom = emojiMapCustom
							)
						)
						if(visibility_checked != null) {
							json.put("visibility", visibility_checked.strMastodon)
						}
						json.put("sensitive", bNSFW)
						json.put(
							"spoiler_text",
							EmojiDecoder.decodeShortCode(
								spoiler_text ?: "",
								emojiMapCustom = emojiMapCustom
							)
						)
						
						if(in_reply_to_id != null) {
							json.put("in_reply_to_id", in_reply_to_id.toLong())
						}
						
						if(attachment_list != null) {
							val array = JSONArray()
							for(pa in attachment_list) {
								val a = pa.attachment ?: continue
								if(a.redraft && ! instance.versionGE(TootInstance.VERSION_2_4_1)) continue
								array.put(a.id)
							}
							json.put("media_ids", array)
						}
						
						if(enquete_items?.isNotEmpty() == true) {
							json.put("isEnquete", true)
							val array = JSONArray()
							for(item in enquete_items) {
								array.put(
									EmojiDecoder.decodeShortCode(
										item,
										emojiMapCustom = emojiMapCustom
									)
								)
							}
							json.put("enquete_items", array)
						}
						
					}
				} catch(ex : JSONException) {
					log.trace(ex)
					log.e(ex, "status encoding failed.")
				}
				
				val body_string = json.toString()
				val request_body = RequestBody.create(
					TootApiClient.MEDIA_TYPE_JSON, body_string
				)
				
				val request_builder = Request.Builder().post(request_body)
				
				if(! Pref.bpDontDuplicationCheck(pref)) {
					val digest = (body_string + account.acct).digestSHA256Hex()
					request_builder.header("Idempotency-Key", digest)
				}
				
				result = if(isMisskey) {
					client.request("/api/notes/create", request_builder)
					// TODO {"error":{}} が返ってきた時にどう扱えばいい？
				} else {
					client.request("/api/v1/statuses", request_builder)
				}
				
				val status = parser.status(
					if(isMisskey) {
						result?.jsonObject?.optJSONObject("createdNote") ?: result?.jsonObject
					} else {
						result?.jsonObject
					}
				)
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
					showToast(activity, true, result.error)
				}
				
			}
		})
		)
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
	private var isMisskey = false
	
	private val onEmojiListLoad : (list : ArrayList<CustomEmoji>) -> Unit =
		{ _ : ArrayList<CustomEmoji> ->
			val popup = this@PostHelper.popup
			if(popup?.isShowing == true) proc_text_changed.run()
		}
	
	private val proc_text_changed = object : Runnable {
		override fun run() {
			val et = this@PostHelper.et
			if(et == null || callback2?.canOpenPopup() != true) {
				closeAcctPopup()
				return
			}
			
			var start = et.selectionStart
			val end = et.selectionEnd
			if(start != end) {
				closeAcctPopup()
				return
			}
			val src = et.text.toString()
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
				openPopup()?.setList(et, start, end, acct_list, null, null)
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
				// warning.d( "checkTag: character not tag in string %s", part );
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
				log.d("checkEmoji: character not short code in string.")
				closeAcctPopup()
				return
			}
			
			// : の手前は始端か改行か空白でなければならない
			if(! EmojiDecoder.canStartShortCode(src, last_colon)) {
				log.d("checkEmoji: invalid character before shortcode.")
				closeAcctPopup()
				return
			}
			
			if(part.isEmpty()) {
				openPopup()?.setList(
					et, last_colon, end, null, picker_caption_emoji, open_picker_emoji
				)
			} else {
				
				val code_list = ArrayList<CharSequence>()
				val limit = 100
				
				// カスタム絵文字を検索
				val instance = this@PostHelper.instance
				if(instance != null && instance.isNotEmpty()) {
					val custom_list = App1.custom_emoji_lister.getListWithAliases(
						instance,
						isMisskey,
						onEmojiListLoad
					)
					if(custom_list != null) {
						val needle = src.substring(last_colon + 1, end)
						
						for(item in custom_list) {
							if(code_list.size >= limit) break
							if(! item.shortcode.contains(needle)) continue
							
							val sb = SpannableStringBuilder()
							sb.append(' ')
							sb.setSpan(
								NetworkEmojiSpan(item.url),
								0,
								sb.length,
								Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
							)
							sb.append(' ')
							if(item.alias != null) {
								val start = sb.length
								sb.append(":")
								sb.append(item.alias)
								sb.append(": → ")
								sb.setSpan(
									ForegroundColorSpan(
										Styler.getAttributeColor(
											activity,
											R.attr.colorTimeSmall
										)
									),
									start,
									sb.length,
									Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
								)
							}
							
							sb.append(':')
							sb.append(item.shortcode)
							sb.append(':')
							
							code_list.add(sb)
						}
					}
				}
				
				// 通常の絵文字を部分一致で検索
				val remain = limit - code_list.size
				if(remain > 0) {
					val s = src.substring(last_colon + 1, end).toLowerCase().replace('-', '_')
					val src = EmojiDecoder.searchShortCode(activity, s, remain)
					log.d("checkEmoji: search for %s, result=%d", s, src.size)
					code_list.addAll(src)
					
				}
				
				openPopup()?.setList(
					et,
					last_colon,
					end,
					code_list,
					picker_caption_emoji,
					open_picker_emoji
				)
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
	
	fun setInstance(_instance : String?, isMisskey : Boolean) {
		val instance = _instance?.toLowerCase()
		this.instance = instance
		this.isMisskey = isMisskey
		
		if(instance != null) {
			App1.custom_emoji_lister.getList(instance, isMisskey, onEmojiListLoad)
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
	
	fun attachEditText(
		_formRoot : View,
		et : MyEditText,
		bMainScreen : Boolean,
		_callback2 : Callback2
	) {
		this.formRoot = _formRoot
		this.et = et
		this.callback2 = _callback2
		this.bMainScreen = bMainScreen
		
		et.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(
				s : CharSequence,
				start : Int,
				count : Int,
				after : Int
			) {
			
			}
			
			override fun onTextChanged(s : CharSequence, start : Int, before : Int, count : Int) {
				handler.removeCallbacks(proc_text_changed)
				handler.postDelayed(proc_text_changed, if(popup?.isShowing == true) 100L else 500L)
			}
			
			override fun afterTextChanged(s : Editable) {
				callback2?.onTextUpdate()
			}
		})
		
		et.setOnSelectionChangeListener(object : MyEditText.OnSelectionChangeListener {
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
	
	private fun SpannableStringBuilder.appendEmoji(
		name : String,
		instance : String?,
		bInstanceHasCustomEmoji : Boolean
	) : SpannableStringBuilder {
		
		val item = EmojiMap201709.sShortNameToImageId[name]
		if(item == null || instance != null) {
			// カスタム絵文字は常にshortcode表現
			if(! EmojiDecoder.canStartShortCode(this, this.length)) this.append(' ')
			this.append(SpannableString(":$name:"))
		} else if(! bInstanceHasCustomEmoji) {
			// 古いタンスだとshortcodeを使う。見た目は絵文字に変える。
			if(! EmojiDecoder.canStartShortCode(this, this.length)) this.append(' ')
			this.append(DecodeOptions(activity).decodeEmoji(":$name:"))
		} else {
			// 十分に新しいタンスなら絵文字のunicodeを使う。見た目は絵文字に変える。
			this.append(DecodeOptions(activity).decodeEmoji(item.unified))
		}
		return this
		
	}
	
	private val open_picker_emoji : Runnable = Runnable {
		EmojiPicker(activity, instance, isMisskey) { name, instance, bInstanceHasCustomEmoji ->
			val et = this.et ?: return@EmojiPicker
			
			val src = et.text ?: ""
			val src_length = src.length
			val end = Math.min(src_length, et.selectionEnd)
			val start = src.lastIndexOf(':', end - 1)
			if(start == - 1 || end - start < 1) return@EmojiPicker
			
			val sb = SpannableStringBuilder()
				.append(src.subSequence(0, start))
				.appendEmoji(name, instance, bInstanceHasCustomEmoji)
			
			val newSelection = sb.length
			if(end < src_length) sb.append(src.subSequence(end, src_length))
			
			et.text = sb
			et.setSelection(newSelection)
			
			proc_text_changed.run()
			
			// キーボードを再度表示する
			Handler(activity.mainLooper).post { et.showKeyboard() }
			
		}.show()
	}
	
	fun openEmojiPickerFromMore() {
		EmojiPicker(activity, instance, isMisskey) { name, instance, bInstanceHasCustomEmoji ->
			val et = this.et ?: return@EmojiPicker
			
			val src = et.text ?: ""
			val src_length = src.length
			val start = Math.min(src_length, et.selectionStart)
			val end = Math.min(src_length, et.selectionEnd)
			
			val sb = SpannableStringBuilder()
				.append(src.subSequence(0, start))
				.appendEmoji(name, instance, bInstanceHasCustomEmoji)
			
			val newSelection = sb.length
			if(end < src_length) sb.append(src.subSequence(end, src_length))
			
			et.text = sb
			et.setSelection(newSelection)
			
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
