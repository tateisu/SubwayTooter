package jp.juggler.subwaytooter.api.entity

enum class TootVisibility(
	val id : Int
	, val order : Int // 公開範囲の広い方とWeb設定に合わせる方が大きい
	, val strMastodon : String
	, val strMisskey : String
) {
	
	// IDは下書き保存などで永続化するので、リリース後は変更しないこと！
	
	// WebUIの設定に合わせる。
	WebSetting(0, 100, strMastodon = "web_setting", strMisskey = "web_setting"),
	
	// 公開TLに流す
	Public(1, 90, strMastodon = "public", strMisskey = "public"),
	
	// LTL,FTLには表示されない。
	// フォロワーのホームには表示される。
	// 公開プロフから見える。
	// (Mastodon)タグTLには出ない。
	// (Misskey)タグTLには出る。
	UnlistedHome(2, 80, strMastodon = "unlisted", strMisskey = "home"),
	
	// 未フォローには見せない。
	// (Mastodon)フォロワーのHTLに出る。
	// (Mastodon)公開TLやタグTLには出ない。
	// (Mastodon)公開プロフには出ない。
	// (Misskey)ローカルのフォロワーのHTL,LTL,FTLに出る。リモートのフォロワーのHTL,FTLに出る。
	// (Misskey)非ログインの閲覧者から見たのタグTLには出るが内容は隠される。「あの人はエロゲのタグで何か話してた」とか分かっちゃう。
	// (Misskey)非ログインの閲覧者から見たのプロフには出るが内容は隠される。「あの人は寝てるはずの時間に何か投稿してた」とか分かっちゃう。
	PrivateFollowers(3, 70, strMastodon = "private", strMisskey = "followers"),
	
	// 指定したユーザにのみ送信する。
	// (Misskey)送信先ユーザのIDをリストで指定する。投稿前にユーザの存在確認を行う機会がある。
	// (Misskey)送信先ユーザが1以上ならspecified、0ならprivateを指定する。
	// (Mastodon)メンションに応じて送信先は決定される。
	// 未フォローには見せない。
	// ローカルのフォロワーのHTL,LTL,FTLに出る。リモートのフォロワーのHTL,FTLに出る。
	// (Misskey)非ログインの閲覧者から見たのタグTLには出るが内容は隠される。「あの人はエロゲのタグで何か話してた」とか分かっちゃう。
	// (Misskey)非ログインの閲覧者から見たのプロフには出るが内容は隠される。「あの人は寝てるはずの時間に何か投稿してた」とか分かっちゃう。
	DirectSpecified(4, 60, strMastodon = "direct", strMisskey = "specified"),
	DirectPrivate(5, 50, strMastodon = "direct", strMisskey = "private"),
	
	;
	
	fun canPin(isMisskey : Boolean) : Boolean {
		return when {
			isMisskey -> when(this) {
				Public, UnlistedHome -> true
				else -> false
			}
			else -> when(this) {
				Public, UnlistedHome -> true
				else -> false
			}
		}
	}
	
	companion object {
		
		fun parseMastodon(a : String?) : TootVisibility? {
			for(v in TootVisibility.values()) {
				if(v.strMastodon == a) return v
			}
			return null
		}
		
		fun parseMisskey(a : String?) : TootVisibility? {
			for(v in TootVisibility.values()) {
				if(v.strMisskey == a) return v
			}
			return null
		}
		
		fun fromId(id : Int) : TootVisibility? {
			for(v in TootVisibility.values()) {
				if(v.id == id) return v
			}
			return null
		}
		
		fun parseSavedVisibility(sv : String?) : TootVisibility? {
			sv ?: return null
			
			// 新しい方式ではenumのID
			for(v in TootVisibility.values()) {
				if(v.id.toString() == sv) return v
			}
			
			// 古い方式ではマストドンの公開範囲文字列かweb_setting
			for(v in TootVisibility.values()) {
				if(v.strMastodon == sv) return v
			}
			
			return null
		}
		
		fun isVisibilitySpoilRequired(
			current_visibility : TootVisibility?,
			max_visibility : TootVisibility?
		) : Boolean {
			return try {
				if(current_visibility == null || max_visibility == null) {
					false
				} else {
					current_visibility.order > max_visibility.order
				}
			} catch(ex : Throwable) {
				TootStatus.log.trace(ex)
				false
			}
		}
		
		// 公開範囲を比較する
		// 公開範囲が広い => 大きい
		// aの方が小さい（狭い)ならマイナス
		// aの方が大きい（広い)ならプラス
		@Suppress("unused")
		fun compareVisibility(a : TootVisibility, b : TootVisibility) : Int {
			val ia = a.order
			val ib = b.order
			return when {
				ia < ib -> - 1
				ia > ib -> 1
				else -> 0
			}
		}
		
	}
	
}