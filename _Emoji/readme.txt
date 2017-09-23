絵文字データをアプリから使いやすい形式に変換します。

* 前準備
mkdir png
git clone git@github.com:iamcal/emoji-data.git

* ビルド
perl makeJavaCode.pl 

* 出力
- png フォルダの中味をアプリの drawable-nodpi フォルダにコピーします。
- EmojiData201709.java の中味を jp.juggler.subwaytooter.util.EmojiData201709 の中に貼り付けます。
