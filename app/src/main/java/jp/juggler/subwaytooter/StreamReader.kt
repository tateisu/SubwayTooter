package jp.juggler.subwaytooter

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.*
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.ProtocolException
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

internal class StreamReader(
	val context : Context,
	private val handler : Handler,
	val pref : SharedPreferences
) {
	
	internal interface StreamCallback {
		fun onTimelineItem(item : TimelineItem)
		fun onListeningStateChanged(bListen : Boolean)
		fun onNoteUpdated(ev : MisskeyNoteUpdate)
		
		fun channelId() : String?
	}
	
	companion object {
		val log = LogCategory("StreamReader")
		
		const val MISSKEY_ALIVE_INTERVAL = 60000L
		
		@Suppress("HasPlatformType")
		val reAuthorizeError = Pattern.compile("authorize", Pattern.CASE_INSENSITIVE)
	}
	
	private val reader_list = LinkedList<Reader>()
	
	internal inner class Reader(
		internal val access_info : SavedAccount,
		internal val end_point : String,
		highlight_trie : WordTrieTree?
	) : WebSocketListener() {
		
		private val bDisposed = AtomicBoolean()
		internal val bListening = AtomicBoolean()
		internal val socket = AtomicReference<WebSocket>(null)
		internal val callback_list = LinkedList<StreamCallback>()
		internal val parser : TootParser =
			TootParser(context, access_info, highlightTrie = highlight_trie)
		
		internal fun dispose() {
			bDisposed.set(true)
			socket.get()?.cancel()
			socket.set(null)
		}
		
		private val proc_reconnect : Runnable = Runnable {
			if(bDisposed.get()) return@Runnable
			startRead()
		}
		
		private val proc_alive : Runnable = Runnable {
			fireAlive()
		}
		
		private fun fireAlive() {
			handler.removeCallbacks(proc_alive)
			if(bDisposed.get()) return
			try {
				if(socket.get()?.send("""{"type":"alive"}""") == true) {
					handler.postDelayed(proc_alive, MISSKEY_ALIVE_INTERVAL)
				}
			} catch(ex : Throwable) {
				log.d(ex.withCaption("fireAlive failed."))
			}
		}
		
		@Synchronized
		internal fun setHighlightTrie(highlight_trie : WordTrieTree) {
			this.parser.highlightTrie = highlight_trie
		}
		
		@Synchronized
		internal fun addCallback(stream_callback : StreamCallback) {
			for(c in callback_list) {
				if(c === stream_callback) return
			}
			callback_list.add(stream_callback)
		}
		
		@Synchronized
		internal fun removeCallback(stream_callback : StreamCallback) {
			val it = callback_list.iterator()
			while(it.hasNext()) {
				val c = it.next()
				if(c === stream_callback) it.remove()
			}
		}
		
		fun containsCallback(streamCallback : StreamCallback) : Boolean {
			return callback_list.contains(streamCallback)
		}
		
		@Synchronized
		fun fireListeningChanged(bListen : Boolean) {
			for(c in callback_list) {
				try {
					c.onListeningStateChanged(bListen)
				} catch(ex : Throwable) {
					log.trace(ex)
				}
			}
		}
		
		/**
		 * Invoked when a web socket has been accepted by the remote peer and may begin transmitting
		 * messages.
		 */
		override fun onOpen(webSocket : WebSocket, response : Response) {
			log.d("WebSocket onOpen. url=%s .", webSocket.request().url)
			if(access_info.isMisskey) {
				handler.removeCallbacks(proc_alive)
				handler.postDelayed(proc_alive, MISSKEY_ALIVE_INTERVAL)
			}
		}
		
		private fun fireTimelineItem(item : TimelineItem?, channelId : String? = null) {
			item ?: return
			synchronized(this) {
				if(bDisposed.get()) return@synchronized
				for(callback in callback_list) {
					try {
						if(channelId != null && channelId != callback.channelId()) continue
						callback.onTimelineItem(item)
					} catch(ex : Throwable) {
						log.trace(ex)
					}
				}
			}
		}
		
		private fun fireDeleteId(id : EntityId) {
			val tl_host = access_info.host
			runOnMainLooper {
				synchronized(this) {
					if(bDisposed.get()) return@runOnMainLooper
					if(Pref.bpDontRemoveDeletedToot(App1.getAppState(context).pref)) return@runOnMainLooper
					for(column in App1.getAppState(context).column_list) {
						try {
							column.onStatusRemoved(tl_host, id)
						} catch(ex : Throwable) {
							log.trace(ex)
						}
					}
				}
			}
		}
		
		private fun fireNoteUpdated(ev : MisskeyNoteUpdate, channelId : String? = null) {
			runOnMainLooper {
				synchronized(this) {
					if(bDisposed.get()) return@runOnMainLooper
					for(callback in callback_list) {
						try {
							if(channelId != null && channelId != callback.channelId()) continue
							callback.onNoteUpdated(ev)
						} catch(ex : Throwable) {
							log.trace(ex)
						}
					}
				}
			}
		}
		
		private fun handleMisskeyMessage(obj : JSONObject, channelId : String? = null) {
			val type = obj.parseString("type")
			if(type?.isEmpty() != false) {
				log.d("handleMisskeyMessage: missing type parameter")
				return
			}
			when(type) {
				
				"channel" -> {
					val body = obj.optJSONObject("body")
					if(body == null) {
						log.e("handleMisskeyMessage: channel body is null")
						return
					}
					val id = body.parseString("id")
					// ストリーミングのchannelイベントにチャネルIDが含まれない場合がある
					// https://github.com/syuilo/misskey/issues/4801
					handleMisskeyMessage(body, id)
				}
				
				"readAllNotifications" -> {
					// nothing to do
				}
				
				// Misskey 11ではこれらのメッセージの形式が違う
				"followed", "renote", "mention", "meUpdated", "follow", "unfollow" -> {
					
					//					{"id":"15","type":"followed","body":{"id":"7rm8yhnvzd","name":null,"username":"tateisu_test2","host":null,"avatarUrl":"https:\/\/misskey.io\/avatar\/7rm8yhnvzd","avatarColor":null,"emojis":[]}}
					//					{"id":"15","type":"renote","body":{"id":"7s063vasr4","createdAt":"2019-04-25T12:08:32.308Z","userId":"7rm8yhnvzd","user":{"id":"7rm8yhnvzd","name":null,"username":"tateisu_test2","host":null,"avatarUrl":"https://misskey.io/avatar/7rm8yhnvzd","avatarColor":null,"emojis":[]},"text":null,"cw":null,"visibility":"home","renoteCount":0,"repliesCount":0,"reactions":{},"emojis":[],"fileIds":[],"files":[],"replyId":null,"renoteId":"7s04q1lagw","renote":{"id":"7s04q1lagw","createdAt":"2019-04-25T11:29:47.662Z","userId":"7rm6y6thc1","user":{"id":"7rm6y6thc1","name":null,"username":"tateisu","host":null,"avatarUrl":"https://pdg1.arkjp.net/misskey/drive/19c55428-7e2d-4050-86c4-39aa20bef593.jpg","avatarColor":"rgba(203,205,189,0)","emojis":[]},"text":"リストTLやタグTLのストリーミングチャネルはないんだろうか？  #MisskeyApi","cw":null,"visibility":"public","renoteCount":0,"repliesCount":0,"reactions":{},"emojis":[],"fileIds":[],"files":[],"replyId":null,"renoteId":null}}}}
					//				{"id":"15","type":"mention","body":{"id":"7s067jr5jq","createdAt":"2019-04-25T12:11:23.969Z","userId":"7rm8yhnvzd","user":{"id":"7rm8yhnvzd","name":null,"username":"tateisu_test2","host":null,"avatarUrl":"https://misskey.io/avatar/7rm8yhnvzd","avatarColor":null,"emojis":[]},"text":"test","cw":null,"visibility":"home","renoteCount":0,"repliesCount":0,"reactions":{},"emojis":[],"fileIds":[],"files":[],"replyId":"7s04q1lagw","renoteId":null,"mentions":["7rm6y6thc1"],"reply":{"id":"7s04q1lagw","createdAt":"2019-04-25T11:29:47.662Z","userId":"7rm6y6thc1","user":{"id":"7rm6y6thc1","name":null,"username":"tateisu","host":null,"avatarUrl":"https://pdg1.arkjp.net/misskey/drive/19c55428-7e2d-4050-86c4-39aa20bef593.jpg","avatarColor":"rgba(203,205,189,0)","emojis":[]},"text":"リストTLやタグTLのストリーミングチャネルはないんだろうか？  #MisskeyApi","cw":null,"visibility":"public","renoteCount":1,"repliesCount":1,"reactions":{},"emojis":[],"fileIds":[],"files":[],"replyId":null,"renoteId":null}}}}
					//
					//			{"type":"channel","body":{"id":"15","type":"mention","body":{"id":"7s067jr5jq","createdAt":"2019-04-25T12:11:23.969Z","userId":"7rm8yhnvzd","user":{"id":"7rm8yhnvzd","name":null,"username":"tateisu_test2","host":null,"avatarUrl":"https:\/\/misskey.io\/avatar\/7rm8yhnvzd","avatarColor":null,"emojis":[]},"text":"test","cw":null,"visibility":"home","renoteCount":0,"repliesCount":0,"reactions":{},"emojis":[],"fileIds":[],"files":[],"replyId":"7s04q1lagw","renoteId":null,"mentions":["7rm6y6thc1"],"reply":{"id":"7s04q1lagw","createdAt":"2019-04-25T11:29:47.662Z","userId":"7rm6y6thc1","user":{"id":"7rm6y6thc1","name":null,"username":"tateisu","host":null,"avatarUrl":"https:\/\/pdg1.arkjp.net\/misskey\/drive\/19c55428-7e2d-4050-86c4-39aa20bef593.jpg","avatarColor":"rgba(203,205,189,0)","emojis":[]},"text":"リストTLやタグTLのストリーミングチャネルはないんだろうか？  #MisskeyApi","cw":null,"visibility":"public","renoteCount":1,"repliesCount":1,"reactions":{},"emojis":[],"fileIds":[],"files":[],"replyId":null,"renoteId":null}}}}
					//			{"type":"channel","body":{"id":"15","type":"meUpdated","body":{"id":"7rm6y6thc1","name":null,"username":"tateisu","host":null,"avatarUrl":"https://pdg1.arkjp.net/misskey/drive/19c55428-7e2d-4050-86c4-39aa20bef593.jpg","avatarColor":"rgba(203,205,189,0)","isAdmin":false,"isBot":false,"isCat":false,"isVerified":false,"emojis":[],"url":null,"createdAt":"2019-04-15T17:23:20.453Z","updatedAt":"2019-04-25T12:07:41.334Z","bannerUrl":null,"bannerColor":null,"isLocked":false,"isModerator":false,"description":"がうがう","location":null,"birthday":null,"followersCount":14,"followingCount":10,"notesCount":65,"pinnedNoteIds":[],"pinnedNotes":[],"avatarId":"7rm8fefuft","bannerId":null,"autoWatch":false,"alwaysMarkNsfw":false,"carefulBot":false,"twoFactorEnabled":false,"hasUnreadMessagingMessage":false,"hasUnreadNotification":false,"pendingReceivedFollowRequestsCount":0,"clientData":{},"email":null,"emailVerified":false}}}
					//			{"type":"channel","body":{"id":"15","type":"unfollow","body":{"id":"7rluu9hv8h","name":"（しむ）‮しむしむ","username":"vandojpn","host":"friends.nico","avatarUrl":"https://pdg1.arkjp.net/misskey/drive/98d731b3-ce32-48d4-b348-befd75106224.jpeg","avatarColor":"rgba(118,88,69,0)","isAdmin":false,"isBot":true,"isCat":false,"isVerified":false,"emojis":[],"url":"https://friends.nico/@vandojpn","createdAt":"2019-04-15T11:44:21.907Z","updatedAt":"2019-04-25T12:13:59.839Z","bannerUrl":"https://pdg1.arkjp.net/misskey/drive/a091e3ef-e1b8-4460-bdd9-f47ea2e9f9f2.jpeg","bannerColor":"rgba(135,128,127,0)","isLocked":false,"isModerator":false,"description":"居場所亡くなったので\n移住先候補\n@vandojpn@mstdn.jp\n@vando@misskey.io","location":null,"birthday":null,"followersCount":2,"followingCount":0,"notesCount":353,"pinnedNoteIds":["7il4rez4b9","7qw3bj7kb0","7qw3cvtkbr","7qxtyp609n","7qyuxfswax"],"pinnedNotes":[{"id":"7il4rez4b9","createdAt":"2018-08-31T05:36:58.000Z","userId":"7rluu9hv8h","user":{"id":"7rluu9hv8h","name":"（しむ）‮しむしむ","username":"vandojpn","host":"friends.nico","avatarUrl":"https://pdg1.arkjp.net/misskey/drive/98d731b3-ce32-48d4-b348-befd75106224.jpeg","avatarColor":"rgba(118,88,69,0)","isBot":true,"emojis":[]},"text":"†┏┛:@vandojpn:┗┓†","cw":null,"visibility":"public","renoteCount":0,"repliesCount":0,"reactions":{},"emojis":[],"fileIds":[],"files":[],"replyId":null,"renoteId":null,"uri":"https://friends.nico/users/vandojpn/statuses/100643230103007331"},{"id":"7qw3bj7kb0","createdAt":"2019-03-28T10:59:44.000Z","userId":"7rluu9hv8h","user":{"id":"7rluu9hv8h","name":"（しむ）‮しむしむ","username":"vandojpn","host":"friends.nico","avatarUrl":"https://pdg1.arkjp.net/misskey/drive/98d731b3-ce32-48d4-b348-befd75106224.jpeg","avatarColor":"rgba(118,88,69,0)","isBot":true,"emojis":[]},"text":"まさらっき、ハト先生、2年間お疲れ様。","cw":null,"visibility":"public","renoteCount":0,"repliesCount":0,"reactions":{},"emojis":[],"fileIds":[],"files":[],"replyId":null,"renoteId":null,"uri":"https://friends.nico/users/vandojpn/statuses/101827922107489261"},{"id":"7qw3cvtkbr","createdAt":"2019-03-28T11:00:47.000Z","userId":"7rluu9hv8h","user":{"id":"7rluu9hv8h","name":"（しむ）‮しむしむ","username":"vandojpn","host":"friends.nico","avatarUrl":"https://pdg1.arkjp.net/misskey/drive/98d731b3-ce32-48d4-b348-befd75106224.jpeg","avatarColor":"rgba(118,88,69,0)","isBot":true,"emojis":[]},"text":"引越し先はこちらです。みんなフォロしてね！\n\nhttps://misskey.xyz/@vando","cw":null,"visibility":"public","renoteCount":0,"repliesCount":0,"reactions":{},"emojis":[],"fileIds":[],"files":[],"replyId":null,"renoteId":null,"uri":"https://friends.nico/users/vandojpn/statuses/101827926245185143"},{"id":"7qxtyp609n","createdAt":"2019-03-29T16:13:21.000Z","userId":"7rluu9hv8h","user":{"id":"7rluu9hv8h","name":"（しむ）‮しむしむ","username":"vandojpn","host":"friends.nico","avatarUrl":"https://pdg1.arkjp.net/misskey/drive/98d731b3-ce32-48d4-b348-befd75106224.jpeg","avatarColor":"rgba(118,88,69,0)","isBot":true,"emojis":[]},"text":"Thank you and Good bye friends.nico for 2 years.\nfriends.nico はみんなの心の中に。","cw":null,"visibility":"public","renoteCount":0,"repliesCount":0,"reactions":{},"emojis":[],"fileIds":[],"files":[],"replyId":null,"renoteId":null,"uri":"https://friends.nico/users/vandojpn/statuses/101834817646842776"},{"id":"7qyuxfswax","createdAt":"2019-03-30T09:28:08.000Z","userId":"7rluu9hv8h","user":{"id":"7rluu9hv8h","name":"（しむ）‮しむしむ","username":"vandojpn","host":"friends.nico","avatarUrl":"https://pdg1.arkjp.net/misskey/drive/98d731b3-ce32-48d4-b348-befd75106224.jpeg","avatarColor":"rgba(118,88,69,0)","isBot":true,"emojis":[]},"text":"二コフレ終了の移住先候補\n\nhttps://misskey.xyz/@vando\n\nhttps://mstdn.jp/@vandojpn\n\n艦これ https://kancolle.social/@vando\n\nTwitter https://twitter.com/vajpn\n\n#theboss_tech \n#クロス\n#friends_nico","cw":null,"visibility":"public","renoteCount":0,"repliesCount":0,"reactions":{},"emojis":[],"fileIds":[],"files":[],"replyId":null,"renoteId":null,"uri":"https://friends.nico/users/vandojpn/statuses/101838886557246239"}],"isFollowing":true,"isFollowed":false,"hasPendingFollowRequestFromYou":false,"hasPendingFollowRequestToYou":false,"isBlocking":false,"isBlocked":false,"isMuted":false}}}
					//			{"type":"channel","body":{"id":"15","type":"follow","body":{"id":"7rluu9hv8h","name":"（しむ）‮しむしむ","username":"vandojpn","host":"friends.nico","avatarUrl":"https://pdg1.arkjp.net/misskey/drive/98d731b3-ce32-48d4-b348-befd75106224.jpeg","avatarColor":"rgba(118,88,69,0)","isAdmin":false,"isBot":true,"isCat":false,"isVerified":false,"emojis":[],"url":"https://friends.nico/@vandojpn","createdAt":"2019-04-15T11:44:21.907Z","updatedAt":"2019-04-25T12:13:59.839Z","bannerUrl":"https://pdg1.arkjp.net/misskey/drive/a091e3ef-e1b8-4460-bdd9-f47ea2e9f9f2.jpeg","bannerColor":"rgba(135,128,127,0)","isLocked":false,"isModerator":false,"description":"居場所亡くなったので\n移住先候補\n@vandojpn@mstdn.jp\n@vando@misskey.io","location":null,"birthday":null,"followersCount":1,"followingCount":0,"notesCount":353,"pinnedNoteIds":["7il4rez4b9","7qw3bj7kb0","7qw3cvtkbr","7qxtyp609n","7qyuxfswax"],"pinnedNotes":[{"id":"7il4rez4b9","createdAt":"2018-08-31T05:36:58.000Z","userId":"7rluu9hv8h","user":{"id":"7rluu9hv8h","name":"（しむ）‮しむしむ","username":"vandojpn","host":"friends.nico","avatarUrl":"https://pdg1.arkjp.net/misskey/drive/98d731b3-ce32-48d4-b348-befd75106224.jpeg","avatarColor":"rgba(118,88,69,0)","isBot":true,"emojis":[]},"text":"†┏┛:@vandojpn:┗┓†","cw":null,"visibility":"public","renoteCount":0,"repliesCount":0,"reactions":{},"emojis":[],"fileIds":[],"files":[],"replyId":null,"renoteId":null,"uri":"https://friends.nico/users/vandojpn/statuses/100643230103007331"},{"id":"7qw3bj7kb0","createdAt":"2019-03-28T10:59:44.000Z","userId":"7rluu9hv8h","user":{"id":"7rluu9hv8h","name":"（しむ）‮しむしむ","username":"vandojpn","host":"friends.nico","avatarUrl":"https://pdg1.arkjp.net/misskey/drive/98d731b3-ce32-48d4-b348-befd75106224.jpeg","avatarColor":"rgba(118,88,69,0)","isBot":true,"emojis":[]},"text":"まさらっき、ハト先生、2年間お疲れ様。","cw":null,"visibility":"public","renoteCount":0,"repliesCount":0,"reactions":{},"emojis":[],"fileIds":[],"files":[],"replyId":null,"renoteId":null,"uri":"https://friends.nico/users/vandojpn/statuses/101827922107489261"},{"id":"7qw3cvtkbr","createdAt":"2019-03-28T11:00:47.000Z","userId":"7rluu9hv8h","user":{"id":"7rluu9hv8h","name":"（しむ）‮しむしむ","username":"vandojpn","host":"friends.nico","avatarUrl":"https://pdg1.arkjp.net/misskey/drive/98d731b3-ce32-48d4-b348-befd75106224.jpeg","avatarColor":"rgba(118,88,69,0)","isBot":true,"emojis":[]},"text":"引越し先はこちらです。みんなフォロしてね！\n\nhttps://misskey.xyz/@vando","cw":null,"visibility":"public","renoteCount":0,"repliesCount":0,"reactions":{},"emojis":[],"fileIds":[],"files":[],"replyId":null,"renoteId":null,"uri":"https://friends.nico/users/vandojpn/statuses/101827926245185143"},{"id":"7qxtyp609n","createdAt":"2019-03-29T16:13:21.000Z","userId":"7rluu9hv8h","user":{"id":"7rluu9hv8h","name":"（しむ）‮しむしむ","username":"vandojpn","host":"friends.nico","avatarUrl":"https://pdg1.arkjp.net/misskey/drive/98d731b3-ce32-48d4-b348-befd75106224.jpeg","avatarColor":"rgba(118,88,69,0)","isBot":true,"emojis":[]},"text":"Thank you and Good bye friends.nico for 2 years.\nfriends.nico はみんなの心の中に。","cw":null,"visibility":"public","renoteCount":0,"repliesCount":0,"reactions":{},"emojis":[],"fileIds":[],"files":[],"replyId":null,"renoteId":null,"uri":"https://friends.nico/users/vandojpn/statuses/101834817646842776"},{"id":"7qyuxfswax","createdAt":"2019-03-30T09:28:08.000Z","userId":"7rluu9hv8h","user":{"id":"7rluu9hv8h","name":"（しむ）‮しむしむ","username":"vandojpn","host":"friends.nico","avatarUrl":"https://pdg1.arkjp.net/misskey/drive/98d731b3-ce32-48d4-b348-befd75106224.jpeg","avatarColor":"rgba(118,88,69,0)","isBot":true,"emojis":[]},"text":"二コフレ終了の移住先候補\n\nhttps://misskey.xyz/@vando\n\nhttps://mstdn.jp/@vandojpn\n\n艦これ https://kancolle.social/@vando\n\nTwitter https://twitter.com/vajpn\n\n#theboss_tech \n#クロス\n#friends_nico","cw":null,"visibility":"public","renoteCount":0,"repliesCount":0,"reactions":{},"emojis":[],"fileIds":[],"files":[],"replyId":null,"renoteId":null,"uri":"https://friends.nico/users/vandojpn/statuses/101838886557246239"}],"isFollowing":true,"isFollowed":false,"hasPendingFollowRequestFromYou":false,"hasPendingFollowRequestToYou":false,"isBlocking":false,"isBlocked":false,"isMuted":false}}}
					//
					//			{"type":"channel","body":{"id":"15","type":"follow","body":{"id":"7rluu9hv8h","name":"（しむ）‮しむしむ","username":"vandojpn","host":"friends.nico","avatarUrl":"https://pdg1.arkjp.net/misskey/drive/98d731b3-ce32-48d4-b348-befd75106224.jpeg","avatarColor":"rgba(118,88,69,0)","isAdmin":false,"isBot":true,"isCat":false,"isVerified":false,"emojis":[],"url":"https://friends.nico/@vandojpn","createdAt":"2019-04-15T11:44:21.907Z","updatedAt":"2019-04-25T12:13:59.839Z","bannerUrl":"https://pdg1.arkjp.net/misskey/drive/a091e3ef-e1b8-4460-bdd9-f47ea2e9f9f2.jpeg","bannerColor":"rgba(135,128,127,0)","isLocked":false,"isModerator":false,"description":"居場所亡くなったので\n移住先候補\n@vandojpn@mstdn.jp\n@vando@misskey.io","location":null,"birthday":null,"followersCount":1,"followingCount":0,"notesCount":353,"pinnedNoteIds":["7il4rez4b9","7qw3bj7kb0","7qw3cvtkbr","7qxtyp609n","7qyuxfswax"],"pinnedNotes":[{"id":"7il4rez4b9","createdAt":"2018-08-31T05:36:58.000Z","userId":"7rluu9hv8h","user":{"id":"7rluu9hv8h","name":"（しむ）‮しむしむ","username":"vandojpn","host":"friends.nico","avatarUrl":"https://pdg1.arkjp.net/misskey/drive/98d731b3-ce32-48d4-b348-befd75106224.jpeg","avatarColor":"rgba(118,88,69,0)","isBot":true,"emojis":[]},"text":"†┏┛:@vandojpn:┗┓†","cw":null,"visibility":"public","renoteCount":0,"repliesCount":0,"reactions":{},"emojis":[],"fileIds":[],"files":[],"replyId":null,"renoteId":null,"uri":"https://friends.nico/users/vandojpn/statuses/100643230103007331"},{"id":"7qw3bj7kb0","createdAt":"2019-03-28T10:59:44.000Z","userId":"7rluu9hv8h","user":{"id":"7rluu9hv8h","name":"（しむ）‮しむしむ","username":"vandojpn","host":"friends.nico","avatarUrl":"https://pdg1.arkjp.net/misskey/drive/98d731b3-ce32-48d4-b348-befd75106224.jpeg","avatarColor":"rgba(118,88,69,0)","isBot":true,"emojis":[]},"text":"まさらっき、ハト先生、2年間お疲れ様。","cw":null,"visibility":"public","renoteCount":0,"repliesCount":0,"reactions":{},"emojis":[],"fileIds":[],"files":[],"replyId":null,"renoteId":null,"uri":"https://friends.nico/users/vandojpn/statuses/101827922107489261"},{"id":"7qw3cvtkbr","createdAt":"2019-03-28T11:00:47.000Z","userId":"7rluu9hv8h","user":{"id":"7rluu9hv8h","name":"（しむ）‮しむしむ","username":"vandojpn","host":"friends.nico","avatarUrl":"https://pdg1.arkjp.net/misskey/drive/98d731b3-ce32-48d4-b348-befd75106224.jpeg","avatarColor":"rgba(118,88,69,0)","isBot":true,"emojis":[]},"text":"引越し先はこちらです。みんなフォロしてね！\n\nhttps://misskey.xyz/@vando","cw":null,"visibility":"public","renoteCount":0,"repliesCount":0,"reactions":{},"emojis":[],"fileIds":[],"files":[],"replyId":null,"renoteId":null,"uri":"https://friends.nico/users/vandojpn/statuses/101827926245185143"},{"id":"7qxtyp609n","createdAt":"2019-03-29T16:13:21.000Z","userId":"7rluu9hv8h","user":{"id":"7rluu9hv8h","name":"（しむ）‮しむしむ","username":"vandojpn","host":"friends.nico","avatarUrl":"https://pdg1.arkjp.net/misskey/drive/98d731b3-ce32-48d4-b348-befd75106224.jpeg","avatarColor":"rgba(118,88,69,0)","isBot":true,"emojis":[]},"text":"Thank you and Good bye friends.nico for 2 years.\nfriends.nico はみんなの心の中に。","cw":null,"visibility":"public","renoteCount":0,"repliesCount":0,"reactions":{},"emojis":[],"fileIds":[],"files":[],"replyId":null,"renoteId":null,"uri":"https://friends.nico/users/vandojpn/statuses/101834817646842776"},{"id":"7qyuxfswax","createdAt":"2019-03-30T09:28:08.000Z","userId":"7rluu9hv8h","user":{"id":"7rluu9hv8h","name":"（しむ）‮しむしむ","username":"vandojpn","host":"friends.nico","avatarUrl":"https://pdg1.arkjp.net/misskey/drive/98d731b3-ce32-48d4-b348-befd75106224.jpeg","avatarColor":"rgba(118,88,69,0)","isBot":true,"emojis":[]},"text":"二コフレ終了の移住先候補\n\nhttps://misskey.xyz/@vando\n\nhttps://mstdn.jp/@vandojpn\n\n艦これ https://kancolle.social/@vando\n\nTwitter https://twitter.com/vajpn\n\n#theboss_tech \n#クロス\n#friends_nico","cw":null,"visibility":"public","renoteCount":0,"repliesCount":0,"reactions":{},"emojis":[],"fileIds":[],"files":[],"replyId":null,"renoteId":null,"uri":"https://friends.nico/users/vandojpn/statuses/101838886557246239"}],"isFollowing":true,"isFollowed":false,"hasPendingFollowRequestFromYou":false,"hasPendingFollowRequestToYou":false,"isBlocking":false,"isBlocked":false,"isMuted":false}}}
					//			{"type":"noteUpdated","body":{"id":"7s06kfbynh","type":"reacted","body":{"reaction":"pudding","userId":"7rm8yhnvzd"}}}
					
					// 通知IDも日時もないイベントを受け取っても通知TLに反映させられないから無視するしかない
					// https://github.com/syuilo/misskey/issues/4802
				}
				
				"note" -> {
					val body = obj.optJSONObject("body")
					fireTimelineItem(parser.status(body), channelId)
				}
				
				"noteUpdated" -> {
					val body = obj.optJSONObject("body")
					if(body == null) {
						log.e("handleMisskeyMessage: noteUpdated body is null")
						return
					}
					fireNoteUpdated(MisskeyNoteUpdate(body), channelId)
				}
				
				"notification" -> {
					val body = obj.optJSONObject("body")
					if(body == null) {
						log.e("handleMisskeyMessage: notification body is null")
						return
					}
					fireTimelineItem(parser.notification(body), channelId)
				}
				
				else -> {
					log.v("ignore streaming event $type")
				}
			}
			
		}
		
		/**
		 * Invoked when a text (type `0x1`) message has been received.
		 */
		override fun onMessage(webSocket : WebSocket, text : String) {
			// warning.d( "WebSocket onMessage. url=%s, message=%s", webSocket.request().url(), text );
			try {
				
				if(text.isEmpty() || text[0] != '{') {
					log.d("onMessage: text is not JSON: $text")
					return
				}
				
				val obj = text.toJsonObject()
				
				if(access_info.isMisskey) {
					handleMisskeyMessage(obj)
					
				} else {
					
					val event = obj.parseString("event")
					
					if(event == null || event.isEmpty()) {
						log.d("onMessage: missing event parameter")
						return
					}
					
					if(event == "filters_changed") {
						Column.onFiltersChanged(context, access_info)
						return
					}
					
					val payload = TootPayload.parsePayload(parser, event, obj, text)
					
					when(event) {
						
						"delete" -> when(payload) {
							is Long -> fireDeleteId(EntityId(payload.toString()))
							is String -> fireDeleteId(EntityId(payload.toString()))
							else -> log.d("unsupported payload type. $payload")
						}
						
						else -> when(payload) {
							is TimelineItem -> fireTimelineItem(payload)
							else -> log.d("unsupported payload type. $payload")
						}
					}
					
				}
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e("data=$text")
			}
		}
		
		/**
		 * Invoked when the peer has indicated that no more incoming messages will be transmitted.
		 */
		override fun onClosing(webSocket : WebSocket, code : Int, reason : String) {
			log.d(
				"WebSocket onClosing. code=%s,reason=%s,url=%s .",
				code,
				reason,
				webSocket.request().url
			)
			webSocket.cancel()
			bListening.set(false)
			handler.removeCallbacks(proc_alive)
			handler.removeCallbacks(proc_reconnect)
			handler.postDelayed(proc_reconnect, 10000L)
			fireListeningChanged(false)
		}
		
		/**
		 * Invoked when both peers have indicated that no more messages will be transmitted and the
		 * connection has been successfully released. No further calls to this listener will be made.
		 */
		override fun onClosed(webSocket : WebSocket, code : Int, reason : String) {
			log.d(
				"WebSocket onClosed.  code=%s,reason=%s,url=%s .",
				code,
				reason,
				webSocket.request().url
			)
			bListening.set(false)
			handler.removeCallbacks(proc_alive)
			handler.removeCallbacks(proc_reconnect)
			handler.postDelayed(proc_reconnect, 10000L)
			fireListeningChanged(false)
		}
		
		/**
		 * Invoked when a web socket has been closed due to an error reading from or writing to the
		 * network. Both outgoing and incoming messages may have been lost. No further calls to this
		 * listener will be made.
		 */
		override fun onFailure(webSocket : WebSocket, t : Throwable, response : Response?) {
			log.e(t, "WebSocket onFailure. url=%s .", webSocket.request().url)
			
			bListening.set(false)
			handler.removeCallbacks(proc_reconnect)
			handler.removeCallbacks(proc_alive)
			fireListeningChanged(false)
			
			if(t is ProtocolException) {
				val msg = t.message
				if(msg != null && reAuthorizeError.matcher(msg).find()) {
					log.e("seems old instance that does not support streaming public timeline without access token. don't retry...")
					return
				}
			}
			handler.postDelayed(proc_reconnect, 10000L)
			
		}
		
		internal fun startRead() {
			if(bDisposed.get()) {
				log.d("startRead: disposed.")
				return
			} else if(bListening.get()) {
				log.d("startRead: already listening.")
				return
			}
			
			socket.set(null)
			bListening.set(true)
			synchronized(capturedId) {
				capturedId.clear()
			}
			fireListeningChanged(false)
			
			TootTaskRunner(context).run(access_info, object : TootTask {
				override fun background(client : TootApiClient) : TootApiResult? {
					val (result, ws) = client.webSocket(end_point, this@Reader)
					
					when {
						result == null -> {
							log.d("startRead: cancelled.")
							bListening.set(false)
							fireListeningChanged(false)
						}
						
						ws == null -> {
							val error = result.error
							log.d("startRead: error. $error")
							bListening.set(false)
							fireListeningChanged(false)
							// this may network error.
							handler.removeCallbacks(proc_reconnect)
							handler.postDelayed(proc_reconnect, 5000L)
						}
						
						else -> {
							socket.set(ws)
							fireListeningChanged(true)
						}
					}
					return result
				}
				
				override fun handleResult(result : TootApiResult?) {
				}
			})
		}
		
		// Misskeyの投稿キャプチャ
		private val capturedId = HashSet<EntityId>()
		
		fun capture(list : ArrayList<EntityId>) {
			val socket = socket.get()
			when {
				bDisposed.get() -> return
				socket == null -> return
				
				else -> {
					for(id in list) {
						if(id.isDefault) continue
						synchronized(capturedId) {
							if(capturedId.contains(id)) return
							try {
								if(socket.send("""{"type":"subNote","body": {"id":"$id"}}""")) {
									capturedId.add(id)
								} else {
									log.w("capture failed.")
								}
							} catch(ex : Throwable) {
								log.d(ex.withCaption("capture failed."))
							}
						}
					}
				}
			}
		}
		
		fun registerMisskeyChannel(channelArg : JSONObject?) {
			channelArg ?: return
			try {
				if(bDisposed.get()) return
				socket.get()?.send(channelArg.toString())
			} catch(ex : Throwable) {
				log.e(ex, "registerMisskeyChannel failed.")
			}
		}
		
		fun removeChannel(channelId : String?) {
			channelId ?: return
			try {
				if(bDisposed.get()) return
				socket.get()?.send(
					JSONObject().apply {
						put("type", "disconnect")
						put("body", JSONObject().apply {
							put("id", channelId)
						})
					}.toString()
				)
			} catch(ex : Throwable) {
				log.e(ex, "registerMisskeyChannel failed.")
			}
		}
	}
	
	private fun prepareReader(
		accessInfo : SavedAccount,
		endPoint : String,
		highlightTrie : WordTrieTree?
	) : Reader {
		synchronized(reader_list) {
			// アカウントとエンドポイントが同じリーダーがあればそれを使う
			for(reader in reader_list) {
				if(reader.access_info.db_id == accessInfo.db_id && reader.end_point == endPoint) {
					if(highlightTrie != null) reader.setHighlightTrie(highlightTrie)
					return reader
				}
			}
			// リーダーを作成する
			val reader = Reader(accessInfo, endPoint, highlightTrie)
			reader_list.add(reader)
			return reader
		}
	}
	
	// onResume や ロード完了ののタイミングで登録される
	fun register(
		accessInfo : SavedAccount,
		endPoint : String,
		highlightTrie : WordTrieTree?,
		streamCallback : StreamCallback
	) : Reader {
		
		val reader = prepareReader(accessInfo, endPoint, highlightTrie)
		reader.addCallback(streamCallback)
		if(! reader.bListening.get()) {
			reader.startRead()
		} else {
			streamCallback.onListeningStateChanged(true)
		}
		return reader
	}
	
	// カラム破棄やリロードのタイミングで呼ばれる
	fun unregister(
		accessInfo : SavedAccount,
		endPoint : String,
		streamCallback : StreamCallback
	) {
		synchronized(reader_list) {
			val it = reader_list.iterator()
			while(it.hasNext()) {
				val reader = it.next()
				if(reader.access_info.db_id == accessInfo.db_id && reader.end_point == endPoint) {
					log.d("unregister: removeCallback $endPoint")
					reader.removeCallback(streamCallback)
					if(reader.callback_list.isEmpty()) {
						log.d("unregister: dispose $endPoint")
						reader.dispose()
						it.remove()
					} else {
						reader.removeChannel(streamCallback.channelId())
					}
				}
			}
		}
	}
	
	// onPauseのタイミングで全てのStreaming接続を破棄する
	fun stopAll() {
		synchronized(reader_list) {
			for(reader in reader_list) {
				reader.dispose()
			}
			reader_list.clear()
		}
	}
	
	fun getStreamingStatus(
		accessInfo : SavedAccount,
		endPoint : String,
		streamCallback : StreamCallback
	) : StreamingIndicatorState {
		synchronized(reader_list) {
			for(reader in reader_list) {
				if(reader.access_info.db_id == accessInfo.db_id
					&& reader.end_point == endPoint
					&& reader.containsCallback(streamCallback)
				) {
					return if(reader.bListening.get() && reader.socket.get() != null) {
						StreamingIndicatorState.LISTENING
					} else {
						StreamingIndicatorState.REGISTERED
					}
				}
			}
		}
		return StreamingIndicatorState.NONE
	}
}
