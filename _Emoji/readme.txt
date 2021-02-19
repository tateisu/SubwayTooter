絵文字データをアプリから使いやすい形式に変換します。

#################################
*依存データ

# emojione v2.2.7 (古いMastodonとの互換性のため)
rm -fr emojione
git clone -b v2.2.7 git@github.com:emojione/emojione.git emojione

# 2021/02 不要になった
## Gargron's fork of emoji-mart (master branch)
#rm -fr emoji-mart
#git clone git@github.com:Gargron/emoji-mart.git emoji-mart

rm -fr emoji-data
git clone git@github.com:iamcal/emoji-data.git emoji-data

# マストドン公式
rm -fr mastodon
git clone git@github.com:tootsuite/mastodon.git mastodon

# twemoji
rm -fr twemoji
git clone git@github.com:twitter/twemoji.git twemoji


# noto-emoji
rm -fr noto-emoji
git clone git@github.com:googlefonts/noto-emoji.git noto-emoji

# emoji4unicode
rm -fr emoji4unicode
git clone git@github.com:google/emoji4unicode.git emoji4unicode

# override/ フォルダに優先的に使いたいsvgやpngを入れておく

1f923.svg      傾いた笑う顔。演出的な理由でMastodonで使われている
265f-fe0f.svg  Black Chess Pawn. Emoji 11.0 で追加されたがtwemojiに入ってない。
267e-fe0f.svg  Permanent Paper Sign. Emoji 11.0 で追加されたがtwemojiに入ってない。


########################################

* 前準備
mkdir assets drawable-nodpi
rm -f assets/* drawable-nodpi/* category-pretty.json

* ビルド
####perl makeJavaCode.pl 2>error.log
2021/02 からkotlinのコードに変えた

* 出力

assets の中身を C:\mastodon-related\TestEmojiSvg\app/src/main/assets にコピー。 TestEmojiSvg をビルドしてエラーが出ないか試す
assets の中身を C:\mastodon-related\SubwayTooter\emoji\src\main\assets にコピー。
drawable-nodpi の中身を C:\mastodon-related\SubwayTooter\emoji\src\main\res\drawable-nodpi にコピー。
EmojiData201709.java の中味を emoji/src/main/java/.../EmojiMap.java の所定の場所にペースト。


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


