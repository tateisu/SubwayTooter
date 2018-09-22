絵文字データをアプリから使いやすい形式に変換します。

#################################
*依存データ

# emojione v2.2.7 (古いMastodonとの互換性のため)
git clone -b v2.2.7 git@github.com:emojione/emojione.git

# Gargron's fork of emoji-mart (master branch)
git clone git@github.com:Gargron/emoji-mart.git

# emoji-data 4.0.4
# (上のemoji-martのpackages.jsonで指定されたバージョンに合わせる
git clone -b v4.0.4 git@github.com:iamcal/emoji-data.git

# マストドン公式
git clone git@github.com:tootsuite/mastodon.git

#オーバライド用
override フォルダにPNG画像を用意する
マストドンのタンスにある絵文字を以下のようにPNGに変換します
magick.exe -density 128 -background none 1f923.svg png32:emj_1f923.png


########################################

* 前準備
mkdir png

* ビルド
perl makeJavaCode.pl 

* 出力
- png フォルダの中味をアプリの drawable-nodpi フォルダにコピーします。
- EmojiData201709.java の中味を jp.juggler.subwaytooter.util.EmojiData201709 の中に貼り付けます。


#################################
* 2018/9/23 メンテナンス。

今のマストドンが利用している絵文字データの再確認。

MastodonのJavaScript依存パッケージ
https://github.com/tootsuite/mastodon/blob/master/package.json
では、フォークされたemoji-martが使われている
https://github.com/Gargron/emoji-mart

そのemoji-mart は "emoji-datasource": "4.0.4" に依存している。
npmのemoji-datasource は iamcal/emoji-data のことだ
https://www.npmjs.com/package/emoji-datasource
https://github.com/iamcal/emoji-data/tree/v4.0.4

カテゴリ情報はコレ
https://github.com/Gargron/emoji-mart/blob/master/data/all.json

絵文字データはコレ
https://github.com/iamcal/emoji-data/blob/v4.0.4/emoji.json

が、上記は絵文字ピッカーの話であり投稿後のデータの絵文字表示には使われていない。

たとえば rolling_on_the_floor_laughing は絵文字ピッカー内部では泣いていないが、投稿後は泣いている。

投稿後のデータのUnicode絵文字に使われているのはMastodon公式リポジトリにあるsvgファイルだ。
https://github.com/tootsuite/mastodon/tree/master/public/emoji


