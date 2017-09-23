#!perl --
use strict;
use warnings;
use utf8;
use LWP::Simple;
use JSON; 
use Data::Dump qw(dump);
use Encode;
use File::Copy;

=tmp

	pngフォルダにある画像ファイルを参照する
	emoji-data/emoji.json を参照する
	
	以下のjavaコードを生成する
	- UTF-16文字列 => 画像リソースID のマップ。同一のIDに複数のUTF-16文字列が振られることがある。
	- shortcode => 画像リソースID のマップ。同一のIDに複数のshortcodeが振られることがある。
	- shortcode中の区切り文字はハイフンもアンダーバーもありうる。出力データではアンダーバーに寄せる
	- アプリはshortcodeの探索時にキー文字列の区切り文字をアンダーバーに正規化すること

=cut



sub loadFile{
	my($fname)=@_;
	open(my $fh,"<",$fname) or die "$fname $!";
	local $/ = undef;
	my $data = <$fh>;
	close($fh) or die "$fname $!";
	return $data;
}

sub parseCodePoint($){
	my($src)=@_;
	return () if not $src;

	my @chars = map{ lc $_ } ( $src =~ /([0-9A-Fa-f]+)/g );
	return () if not @chars;

	return \@chars;
}

sub encodeCodePoint($){
	my($chars)=@_;
	return join '-', @$chars;
}

sub parseShortName($){
	my( $name) = @_;
	$name = lc $name;
	$name =~ tr/-/_/;
	
	return $name;
}

my @emoji_variants = qw( 
img-twitter-64
img-google-64
img-apple-64
img-apple-160
img-facebook-64
img-messenger-64
);

my %emoji_variants_used;

sub findEmojiImage($){
	my($image)=@_;
	for my $variant ( @emoji_variants ){
		my $path = "emoji-data/$variant/$image";
		if( -f $path ){
			$emoji_variants_used{$variant} or $emoji_variants_used{$variant} =[];
			if( @{$emoji_variants_used{$variant}} < 5 ){
				push @{$emoji_variants_used{$variant}},$image;
			}
			return $path;
		}
	}
	return;
}

sub getEmojiResId($$){
	my($image,$name)=@_;

	# コードポイントに合う画像ファイルがあるか調べる
	my $image_path = findEmojiImage($image);
	if( not $image_path ){
		warn "$name : missing image. $image\n";
		next;
	}

	# 画像ファイルをpng フォルダにコピーする
	my $dst_name = "emj_". lc($image);
	$dst_name =~ tr/-/_/;
	my $dst_path = "png/$dst_name";
	if( not -f $dst_path ){
		copy( $image_path,$dst_path ) or die "$dst_path $!";
	}
	
	# 画像リソースの名前
	my $res_name = $dst_name;
	$res_name =~ s/\.png//;
	
	return $res_name;
}

my @skin_tone_modifier = (
	[ "1F3FB" , "_tone1" ],
	[ "1F3FC" , "_tone2" ],
	[ "1F3FD" , "_tone3" ],
	[ "1F3FE" , "_tone4" ],
	[ "1F3FF" , "_tone5" ],
);

my %res_map;




my $emoji_list = decode_json loadFile "./emoji-data/emoji.json";
for my $emoji ( @$emoji_list ){

	# short_name のリストを作る
	my @shortnames;
	push @shortnames,map{ parseShortName($_) } $emoji->{"short_name"};
	push @shortnames,map{ parseShortName($_) } @{ $emoji->{"short_names"} };

	# 絵文字のコードポイント一覧を収集する
	my @codepoints;
	push @codepoints,map{ parseCodePoint($_) } $emoji->{unified};
	push @codepoints,map{ parseCodePoint($_) } @{ $emoji->{variations} };
	for my $k (qw(docomo au softbank google) ){
		push @codepoints,map{ parseCodePoint($_) } $emoji->{$k};
	}

	my $name = $shortnames[0];
	my $res_name = getEmojiResId($emoji->{"image"},$name);
	my $res_info = $res_map{ $res_name };
	$res_info or $res_info = $res_map{ $res_name } = { codepoint_map => {} , shortname_map => {} };
	for ( @codepoints ){
		$res_info->{codepoint_map}{ encodeCodePoint($_) } = $_;
	}
	
	for ( @shortnames ){
		$res_info->{shortname_map}{ $_ } = $_;
	}

	# スキントーン
	if( $emoji->{"skin_variations"} ){
		for my $mod (@skin_tone_modifier){
			my($mod_code,$mod_suffix)=@$mod;
			my $mod_name = $name . $mod_suffix;

			my $data = $emoji->{"skin_variations"}{$mod_code};
			if( not $data ){
				warn "$name : missing skin tone $mod_code $mod_suffix\n";
				next;
			}

			$res_name = getEmojiResId($data->{"image"},$mod_name);
			my $res_info = $res_map{ $res_name };
			$res_info or $res_info = $res_map{ $res_name } = { codepoint_map => {} , shortname_map => {} };

			for ( map{ parseCodePoint($_) } $data->{unified} ){
				$res_info->{codepoint_map}{ encodeCodePoint($_) } = $_;
			}
			for ( map{ $_ . $mod_suffix } @shortnames ){
				$res_info->{shortname_map}{ $_ } = $_;
			}
		}
	}
}

for my $variant ( @emoji_variants ){
	next if not $emoji_variants_used{$variant};
	warn "$variant ",join(',',@{$emoji_variants_used{$variant}})," ...\n";
}

my $out_file = "EmojiData201709.java";
open(my $fh, ">:encoding(utf8)",$out_file) or die "$out_file : $!";

my $line_num = 0;
my $func_num = 0;
sub addCode{
	my($code)=@_;
	# open new function
	if( $line_num == 0 ){
		++$func_num;
		print $fh "\tprivate static void init$func_num(){\n";
	}
	# write code
	print $fh "\t\t",$code,"\n";

	# close function
	if( ++ $line_num > 100 ){
		print $fh "\t}\n";
		$line_num = 0;
	}
}

my $utf8 = Encode::find_encoding("utf8");
my $utf16 = Encode::find_encoding("UTF-16BE");
my $utf16_max_length = 0;

my %codepoint_duplicate;
my %shortname_duplicate;

for my $res_name ( sort keys %res_map ){
	my $res_info = $res_map{$res_name};
	
	my $java = "";
	for my $codepoint_name( sort keys %{$res_info->{codepoint_map}} ){
		my $codepoint_chars = $res_info->{codepoint_map}{$codepoint_name};

		my $dup = $codepoint_duplicate{ $codepoint_name };
		$dup or $dup = $codepoint_duplicate{ $codepoint_name } = [];
		push @$dup,$res_name;
		next if @$dup > 1;
		# 一つのコードポイントに複数の絵文字グリフが割り当てられているケース
		# - 過去のガラケー絵文字の「時計」が複数の時刻の時計にマッチする
		# - 過去のガラケー絵文字の「占い」が水晶玉や魔法陣にマッチする
		# など、現代では別に気にしなくてもよさそうなケースだった

		# コードポイントのリストからperl内部表現の文字列にする
		my $str = join '',map{ chr hex $_ } @$codepoint_chars;
		# perl内部表現からUTF-16に変換する
		my $str_utf16 = $utf16->encode( $str );
		# $str_utf16 をJavaのエスケープ表現に直す
		my @utf16_chars = unpack("n*",$str_utf16);
		my $java_chars = join('',map{ sprintf qq(\\u%04x),$_} @utf16_chars );
		$java .= qq(, "$java_chars");

		my $char_count = 0+@utf16_chars;
		if( $char_count > $utf16_max_length ){
			$utf16_max_length = $char_count;
		}
		if( $char_count == 11 ){
			warn "max length: $res_name\n";
		}
	}
	addCode( qq{_addCodePoints( R.drawable.$res_name$java );});
}
for my $res_name ( sort keys %res_map ){
	my $res_info = $res_map{$res_name};
	
	my $java = "";
	for my $short_name ( sort keys %{$res_info->{shortname_map}} ){
		
		my $dup = $shortname_duplicate{ $short_name };
		$dup or $dup = $shortname_duplicate{ $short_name } = [];
		push @$dup,$res_name;
		next if @$dup > 1;
		# 一つのshort_name に複数の絵文字グリフが割り当てられているケース
		# family, man_woman_boy だけだった
		# emj_1f468_200d_1f469_200d_1f466, emj_1f46a の２パターンがある
		# 図柄は全く同じに見えるので気にしなくてもよさそうだった

		
		$java .= qq(, "$short_name");
	}
	addCode( qq{_addShortNames( R.drawable.$res_name$java );});
}

while(my($k,$ra)=each %codepoint_duplicate){
	next if @$ra <= 1;
	warn "codepoint duplicate $k : ",join(',',@$ra),"\n";
}

while(my($k,$ra)=each %shortname_duplicate){
	next if @$ra <= 1;
	warn "shortname duplicate $k : ",join(',',@$ra),"\n";
}

# close function
if( $line_num > 0 ){
	print $fh "\t}\n";
}

# write function to call init**()

print $fh "\tstatic final int utf16_max_length=$utf16_max_length;\n\n";
print $fh "\tstatic void initAll(){\n";
for(my $i=1;$i <= $func_num;++$i){
	print  $fh "\t\tinit$i();\n";
}
print $fh "\t}\n";

close($fh) or die "$out_file : $!";
