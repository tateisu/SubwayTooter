------------------------------------------------
# 概要

絵文字データをアプリから使いやすい形式に変換します。

------------------------------------------------
# 依存データ

## emojione v2.2.7 (古いMastodonとの互換性のため)
rm -fr emojione
git clone -b v2.2.7 git@github.com:emojione/emojione.git emojione

## Gargron's fork of emoji-mart (master branch)
//2021/02 不要になった
//rm -fr emoji-mart
//git clone git@github.com:Gargron/emoji-mart.git emoji-mart

rm -fr emoji-data
git clone git@github.com:iamcal/emoji-data.git emoji-data

## マストドン公式
rm -fr mastodon
git clone git@github.com:tootsuite/mastodon.git mastodon

## twemoji
rm -fr twemoji
git clone git@github.com:twitter/twemoji.git twemoji

## noto-emoji
rm -fr noto-emoji
git clone git@github.com:googlefonts/noto-emoji.git noto-emoji

## emoji4unicode
rm -fr emoji4unicode
git clone git@github.com:google/emoji4unicode.git emoji4unicode

## override/ フォルダ

優先的に使いたいsvgやpngを入れておく

1f923.svg      傾いた笑う顔。演出的な理由でMastodonで使われている
265f-fe0f.svg  Black Chess Pawn. Emoji 11.0 で追加されたがtwemojiに入ってない。
267e-fe0f.svg  Permanent Paper Sign. Emoji 11.0 で追加されたがtwemojiに入ってない。

----------------------------------------------------
# 作業手順


## 前準備


mkdir -p assets drawable-nodpi
rm -fr assets/* drawable-nodpi/* category-pretty.json

echo '*/'

## ビルド
IntelliJ IDEA で emojiConverter のプロジェクトを開く
Gradle sync
Main.Ktを実行。CWD は _Emoji にする。

## 出力

drawable-nodpi の中身を C:\mastodon-related\SubwayTooter\emoji\src\main\res\drawable-nodpi にコピー。 (現時点ではカラ)
assets の中身を C:\mastodon-related\TestEmojiSvg\app/src/main/assets にコピー。 TestEmojiSvg をビルドしてエラーが出ないか試す
assets の中身を C:\mastodon-related\SubwayTooter\emoji\src\main\assets にコピー。
emoji_map.txt を C:\mastodon-related\SubwayTooter\emoji\src\main\assets にコピー。

---------------------------------------------------------------------
# 2018/9/23 メンテナンス

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

---------------------------------------------------------------------
# 202102 メンテナンス

## Motivation
- Gargron/emoji-mart が古すぎる。カテゴリ一覧は他の場所から持ってくるべき
- Emoji 13.1対応の画像でライセンス的にアプリで使えそうなのはnoto-emojiくらいしかない。

## Changes
- 出力データをJavaコードではなく emoji_map.txt に変更した
- 変換コードをPerlからKotlinに変えた。いままで適当だったCodepointListなどをInt配列で扱えるので精密さが増した。
- noto-emoji や emoji4unicodeを読むようになった
- Emojipediaからバージョン別絵文字一覧やカテゴリ別絵文字一覧を読むようになった。
-- カテゴリ一覧をEmoji 13.1に対応できる
-- 実際に使われているfull qualified codeを確認できる
- スキントーンの親子関係を検証,列挙するようになった

---------------------------------------------------------------------
# emoji_map.txt のフォーマット

## 基本的な構造
- 行区切りは\x0a。
- 行ごとに始端と終端をtrim{ it<= 0x20} する。
- 行ごとに//以降を読み飛ばす。
- 各行の^(\w+): 部分がヘッダ。

## ヘッダとその処理

svg または drawable
: 絵文字の画像リソースを表す。

un または u
: 直前に画像リソースが指定された絵文字に対して、Unicode表現を表す。
: 絵文字をUnicodeに変換する時はunで指定されたデータを使う(必ず提供される)。

sn または s
: 直前に画像リソースが指定された絵文字に対して、ショートコード表現を表す。
: 絵文字をショートコードに変換する時はsnで指定されたデータを使う(必ず提供される)。

cn: カテゴリ名
c: 直前に指定されたカテゴリ名に対して絵文字を追加する。パーサは登場順序を維持すること。

t: トーン指定。カンマ区切りでトーン適用前の絵文字、トーンコード、トーン適用後の絵文字を表す。

## トーンコード
絵文字中の skin tone modifiersだけを抽出したもの。
u1F3FB, u1F3FC, u1F3FD, u1F3FE, u1F3FF のコードポイントが1文字以上並ぶ。
絵文字ピッカーでは1文字のトーンコードを持つ絵文字に対してトーンを選択できる。
トーンコードが2文字以上ある場合は、絵文字ピッカーでは「複合トーン」カテゴリから選択できる。
