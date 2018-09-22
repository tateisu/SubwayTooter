#!perl --
use strict;
use warnings;
use utf8;
use LWP::Simple;
use JSON; 
use Data::Dump qw(dump);
use Encode;
use File::Copy;
use Carp qw(confess);

=tmp

	pngフォルダにある画像ファイルを参照する
	emoji-data/emoji.json を参照する
	
	以下のjavaコードを生成する
	- UTF-16文字列 => 画像リソースID のマップ。同一のIDに複数のUTF-16文字列が振られることがある。
	- shortcode => 画像リソースID のマップ。同一のIDに複数のshortcodeが振られることがある。
	- shortcode中の区切り文字はハイフンもアンダーバーもありうる。出力データではアンダーバーに寄せる
	- アプリはshortcodeの探索時にキー文字列の区切り文字をアンダーバーに正規化すること

=cut

binmode \*STDOUT,":encoding(utf8)";
binmode \*STDERR,":encoding(utf8)";


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
	ref($chars) or confess "encodeCodePoint: not array ref";
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
my %shortname2unified;

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

		# using svg from mastodon folder?
		my $mastodonSvg = "mastodon/public/emoji/".lc($image);
		$mastodonSvg =~ s/\.png$/\.svg/i;
		if( -f $mastodonSvg ){
			warn "convert from mastodon SVG file: $mastodonSvg\n";
			system qq(magick.exe -density 128 -background none $mastodonSvg png32:$dst_path);

		}else{
			# override?
			my $override = "override/$dst_name";
			if( -f $override){
				copy( $override,$dst_path ) or die "$dst_path $!";
			}else{
				copy( $image_path,$dst_path ) or die "$dst_path $!";
			}
		}


	}
	
	# override?
	my $override = "override/$dst_name";
	
	# 画像リソースの名前
	my $res_name = $dst_name;
	$res_name =~ s/\.png//;
	
	return $res_name;
}

sub getEmojiResIdOld($$){
	my($image,$name)=@_;

	# コードポイントに合う画像ファイルがあるか調べる
	my $image_path = "emojione/assets/png/$image.png";
	if( not $image_path ){
		die "$name : missing image. $image\n";
	}

	# 画像ファイルをpng フォルダにコピーする
	my $dst_name = "emj_". lc("$image.png");
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

my %res_map;
sub addResource{
	my($name,$unified,$image,$list_code,$list_name,$has_tone,$no_tone)=@_;

	my $res_name = getEmojiResId($image,$name);

	my $res_info = $res_map{ $res_name };
	$res_info or $res_info = $res_map{ $res_name } = {
		res_name => $res_name
		, codepoint_map => {} 
		, shortname_map => {} 
		, unified => $unified
		, has_tone => $has_tone
		, no_tone => $no_tone
	};
	if( $res_info->{unified} ne $unified ){
		die "unified not match. res_name=$res_name\n";
	}
	
	for ( @$list_code ){
		$res_info->{codepoint_map}{ encodeCodePoint($_) } = $_;
	}
	for ( @$list_name ){
		$res_info->{shortname_map}{ $_ } = $_;
	}
}

sub addResourceEmojione{
	my($name,$unified,$image,$list_code,$list_name)=@_;

	my $res_name = getEmojiResIdOld($image,$name);

	my $res_info = $res_map{ $res_name };
	$res_info or $res_info = $res_map{ $res_name } = {
		res_name => $res_name
		, codepoint_map => {} 
		, shortname_map => {} 
		, unified => $unified
	};
	if( $res_info->{unified} ne $unified ){
		die "unified not match. res_name=$res_name\n";
	}

	for ( @$list_code ){
		$res_info->{codepoint_map}{ encodeCodePoint($_) } = $_;
	}

	for ( @$list_name ){
		$res_info->{shortname_map}{ $_ } = $_;
	}
}




my $fh;

################################################################################
# emoji_data のデータを読む

my @skin_tone_modifier = (
	[ "1F3FB" , ["_tone1","_light_skin_tone"] ],
	[ "1F3FC" , ["_tone2","_medium_light_skin_tone"] ],
	[ "1F3FD" , ["_tone3","_medium_skin_tone"] ],
	[ "1F3FE" , ["_tone4","_medium_dark_skin_tone"] ],
	[ "1F3FF" , ["_tone5","_dark_skin_tone"] ],
);

my $emoji_list = decode_json loadFile "./emoji-data/emoji.json";
for my $emoji ( @$emoji_list ){

	# short_name のリスト
	my @shortnames;
	push @shortnames,map{ parseShortName($_) } $emoji->{"short_name"};
	push @shortnames,map{ parseShortName($_) } @{ $emoji->{"short_names"} };

	# 絵文字のコードポイント一覧
	my @codepoints;
	push @codepoints,map{ parseCodePoint($_) } $emoji->{unified};
	push @codepoints,map{ parseCodePoint($_) } @{ $emoji->{variations} };
	for my $k (qw(docomo au softbank google) ){
		push @codepoints,map{ parseCodePoint($_) } $emoji->{$k};
	}

	my $name = $shortnames[0];
	addResource(
		$name
		,$emoji->{unified}
		, $emoji->{"image"}
		, \@codepoints
		, \@shortnames
		, $emoji->{"skin_variations"}
		, undef
	);

	# スキントーン
	if( $emoji->{"skin_variations"} ){
		for my $mod (@skin_tone_modifier){
			my($mod_code,$mod_suffix_list)=@$mod;
			for my $mod_suffix ( @$mod_suffix_list ){
				my $mod_name = $name . $mod_suffix;

				my $data = $emoji->{"skin_variations"}{$mod_code};
				if( not $data ){
					warn "$name : missing skin tone $mod_code $mod_suffix\n";
					next;
				}

				addResource(
					$mod_name
					,$data->{"unified"}
					,$data->{"image"}
					,[map{ parseCodePoint($_) } $data->{unified}]
					,[map{ $_ . $mod_suffix } @shortnames]
					,0
					,$name
				);
			}
		}
	}
}

for my $variant ( @emoji_variants ){
	next if not $emoji_variants_used{$variant};
	warn "variant: $variant ",join(',',@{$emoji_variants_used{$variant}})," ...\n";
}

##############################################################

# コード＝＞画像リソースのマップ
my %code_map;
sub updateCodeMap{
	undef %code_map;
	for my $res_info (values %res_map ){
		my $res_code_map = $res_info->{codepoint_map};
		for my $code ( keys %$res_code_map ){
			#
			my $rh = $code_map{ $code};
			$rh or $rh = $code_map{$code} = {};
			$rh->{ $res_info->{res_name} } = $res_info;
			#
			my $code2 = removeZWJ( $code );
			$rh = $code_map{ $code2};
			$rh or $rh = $code_map{$code2} = {};
			$rh->{ $res_info->{res_name} } = $res_info;
		}
	}
}

# 名前＝＞画像リソースのマップ
my %name_map;
sub updateNameMap{
	undef %name_map;
	for my $res_info (values %res_map ){
		my $res_name_map = $res_info->{shortname_map};
		for my $name ( keys %$res_name_map ){
			my $rh = $name_map{ $name};
			$rh or $rh = $name_map{$name} = {};
			$rh->{ $res_info->{res_name} } = $res_info;
		}
	}
}

##############################################################
# 古いemojioneのデータを読む

sub parseAlphaCode($){
	my($a)=@_;
	$a =~ s/^://;
	$a =~ s/:$//;
	return parseShortName($a);
}
sub removeZWJ($){
	my($a)=@_;
	$a =~ s/-(?:200d|fe0f)//g;
	return $a;
}

{
	updateCodeMap();

	my $json = JSON->new->allow_nonref->relaxed(1);
	my $data = loadFile "./old-emojione.json";
	my $old_data = $json->decode( $data);

	my %old_names;
	my %lost_codes;
	while( my($code,$item) = each %$old_data){
		
		$item->{_code} = $code;
		
		# 名前を集めておく
		my $names = $item->{names} = [];
		for( map{ parseAlphaCode($_) } $item->{"alpha code"} ){
			push @$names,$_;
		}
		if( $item->{"aliases"} ){
			for( map{ parseAlphaCode($_) } split /\|/,$item->{"aliases"} ){
				push @$names,$_;
			}
		}
		
		for my $name( @$names ){
			$old_names{ $name } = $item;
		}

		# コードを確認する
		my $code2 = removeZWJ( $code );
		my $rh = $code_map{ $code2};
		if( $rh ){
			while( my($res_name,$res_info) = each %$rh ){
				$res_info->{codepoint_map}{ $code }  = parseCodePoint($code);
				$res_info->{codepoint_map}{ $code2 } = parseCodePoint($code2);
				for ( @$names ){
					$res_info->{shortname_map}{ $_ } = $_;
				}
			}
			next;
		}else{
			# 該当するコードがないので、emojioneの画像を持ってくる
			$lost_codes{ $code } = join(',',@$names);
			addResourceEmojione(
				$names->[0]
				, $code
				, $code
				, [map{ parseCodePoint($_) } $code ]
				, $names
			);
		}
	}

	updateNameMap();
	my %lost_names;
	while( my($name,$item)=each %old_names ){
		next if $name_map{ $name };
		$lost_names{ $name } = $item->{_code};
	}

	for my $code (sort keys %lost_codes ){
		warn "old-emojione: load old emojione code $code $lost_codes{$code}\n";
	}
	for my $name (sort keys %lost_names ){
		warn "old-emojione: lost name $name $lost_names{$name}\n";
	}
}

################################################################
# 重複チェック

my @fix_code;
my @fix_name;
my @fix_category;

while(<DATA>){
	s/#.*//;
	s/^\s*//;
	s/\s*$//;
	if( s/(\w+)\s*(\w+)\s*// ){
		my($type,$key)=($1,$2);
		my @data = ( /([\w\+-]+)/g );
		next if @data != 1;
		if( $type eq 'code'){
			push @fix_code,[$key,$data[0]];
		}elsif( $type eq 'name'){
			push @fix_name,[$key,$data[0]];
		}elsif( $type eq 'category'){
			push @fix_category,[$key,$data[0]];
		}else{
			die "bad fix_data type=$type";
		}
	}
}

updateCodeMap();
updateNameMap();

for(@fix_code){
	my($code,$selected_res_name)=@$_;
	my $rh = $code_map{$code};
	my $found = 0;
	for my $res_name (sort keys %$rh ){
		my $res_info = $rh->{$res_name};
		if( $res_name eq $selected_res_name ){
			$found = 1;
		}else{
			warn "remove $code from $res_name...\n";
			delete $res_info->{codepoint_map}->{$code};
		}
	}
	$found or die "missing relation for $code and $selected_res_name\n";
}

for(@fix_name){
	my($name,$selected_res_name)=@$_;
	my $rh = $name_map{$name};
	my $found = 0;
	for my $res_name (sort keys %$rh ){
		my $res_info = $rh->{$res_name};
		if( $res_name eq $selected_res_name ){
			$found = 1;
		}else{
			warn "remove $name from $res_name...\n";
			delete $res_info->{shortname_map}->{$name};
		}
	}
	$found or die "missing relation for $name and $selected_res_name\n";
}

for(@fix_category){
	my($cname,$name)=@$_;

	my $rh = $name_map{parseShortName($name)};
	my($res_info)= values %$rh;
	if( not $res_info ){
		warn "category=$cname emoji=$name missing resource\n";
		next;
	}
	my $ra = $res_info->{category_list};
	$ra or $ra = $res_info->{category_list} =[];
	push @$ra,$cname;
}

updateCodeMap();
updateNameMap();

my %name_chars;
my $bad_name = 0;
for my $name (sort keys %name_map){
	for( split //,$name ){
		$name_chars{$_}=1;
	}
	
	my $rh = $name_map{$name};
	my @res_list = values %$rh;
	
	next if @res_list == 1;
	warn "name $name has multiple resource. ",join(',',map{ $_->{res_name} } @res_list),"\n";
	$bad_name = 1;
}
$bad_name and die "please fix name=>resource duplicate.\n";
warn "name_chars: [",join('',sort keys %name_chars),"]\n";

sub decodeUnified($){
	my($chars) = @_;
	my $str = join '',map{ chr hex $_ } @$chars;
	return $str;
}

for my $code (sort keys %code_map){
	my $rh = $code_map{$code};
	my @res_list = values %$rh;
	next if 1 == @res_list ;
	warn "code $code ",join(',',map{ $_->{res_name} } @res_list)," #  / ",join(' / ',map{ $_->{unified} ." ".decodeUnified(parseCodePoint($_->{unified})) } @res_list),"\n";
}


################################################################################
# カテゴリ情報を読む

my $category_data;
if(1){
	my $json = JSON->new->allow_nonref->relaxed(1);
	my $d1 = loadFile "./emoji-mart/data/all.json";
	my $d2="";
	while( $d1 =~/("[^"]*"|\w+|[^"\w]+)/g ){
		my $a = $1;
		if( $a =~ /^\w/){
			$d2 .= qq("$a");
		}else{
			$d2 .= $a;
		}
	}
	$category_data = $json->decode( $d2);
	# 人間に読みやすい形式で保存する
	my $category_pretty = "category-pretty.json";
	open($fh, ">:encoding(utf8)",$category_pretty) or die "$category_pretty : $!";
	print $fh  $json->pretty->encode( $category_data );
	close($fh) or die "$category_pretty : $!";
}else{
	$category_data = decode_json loadFile "category-pretty.json";
}

for my $category( @{ $category_data->{categories} } ){
	my $cname = $category->{name};
	my $emojis = $category->{emojis};
	for my $name( @$emojis ){
		my $rh = $name_map{parseShortName($name)};
		my($res_info)= values %$rh;
		if( not $res_info ){
			warn "category=$cname emoji=$name missing resource\n";
			next;
		}
		my $ra = $res_info->{category_list};
		$ra or $ra = $res_info->{category_list} =[];
		push @$ra,$cname;
	}
}

{
	my @missing;
	while( my($res_name,$res_info)=each %res_map ){
		next if $res_info->{no_tone};
		if( not $res_info->{category_list} ){
			my $key = join(',',sort keys %{$res_info->{shortname_map}});
			push @missing,$key;
			if( not $key ){
				warn "no key: ",dump($res_info),"\n";
			}
		}
	}
	for(sort @missing){
		warn "missing category: ",$_,"\n";
	}
}

################################################################################
# JSONコードを出力する

my $out_file = "EmojiData201709.java";
open($fh, ">:encoding(utf8)",$out_file) or die "$out_file : $!";

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



# 画像リソースIDとUnidoceシーケンスの関連付けを出力する
for my $res_name ( sort keys %res_map ){
	my $res_info = $res_map{$res_name};

	for my $codepoint_name( sort keys %{$res_info->{codepoint_map}} ){
		my $codepoint_chars = $res_info->{codepoint_map}{$codepoint_name};

		# コードポイントのリストからperl内部表現の文字列にする
		my $str = join '',map{ chr hex $_ } @$codepoint_chars;

		# perl内部表現からUTF-16に変換する
		my $str_utf16 = $utf16->encode( $str );

		# $str_utf16 をJavaのエスケープ表現に直す
		my @utf16_chars = unpack("n*",$str_utf16);
		my $char_count = 0+@utf16_chars;
		if( $char_count > $utf16_max_length ){
			$utf16_max_length = $char_count;
		}
		my $java_chars = join('',map{ sprintf qq(\\u%04x),$_} @utf16_chars );
		addCode( qq{code( R.drawable.$res_name, "$java_chars" );});
	}
}

#for my $res_name ( sort keys %res_map ){
#	my $res_info = $res_map{$res_name};
#	for my $short_name ( sort keys %{$res_info->{shortname_map}} ){
#		addCode( qq{name( R.drawable.$res_name, "$short_name" );});
#	}
#}

# 画像リソースIDとshortcodeの関連付けを出力する
# 投稿時にshortcodeをユニコードに変換するため、shortcodeとUTF-16シーケンスの関連付けを出力する
for my $name (sort keys %name_map){
	my $rh = $name_map{$name};
	my @res_list = values %$rh;
	my $res_info = $res_list[0];

	my $chars = parseCodePoint( $res_info->{unified} );
	# コードポイントのリストからperl内部表現の文字列にする
	my $str = join '',map{ chr hex $_ } @$chars;
	# perl内部表現からUTF-16に変換する
	my $str_utf16 = $utf16->encode( $str );
	my @utf16_chars = unpack("n*",$str_utf16);
	# UTF-16の文字のリストをJavaのエスケープ表現に直す
	my $java_chars = join('',map{ sprintf qq(\\u%04x),$_} @utf16_chars );
	addCode( qq{name( "$name", R.drawable.$res_info->{res_name}, "$java_chars" );});
}

my %categoryNameMapping =(
 'smileys & people'=>'CATEGORY_PEOPLE',
 'animals & nature'=>'CATEGORY_NATURE',
 'food & drink'=>'CATEGORY_FOODS',
 'activities'=>'CATEGORY_ACTIVITY',
 'travel & places'=>'CATEGORY_PLACES',
 'objects'=>'CATEGORY_OBJECTS',
 'symbols'=>'CATEGORY_SYMBOLS',
 'flags'=>'CATEGORY_FLAGS',
);

# カテゴリを書きだす
for my $category( @{ $category_data->{categories} } ){
	my $cname = lc $category->{name};
	my $emojis = $category->{emojis};
	warn "category $cname\n";
	my $catResName = $categoryNameMapping{$cname};
	$catResName or die "missing category resource name for $cname\n";
	for my $name( @$emojis ){
		$name = parseShortName($name);
		addCode( qq{category($catResName, "$name");} );
	}
}

###################################################################

# close function
if( $line_num > 0 ){
	print $fh "\t}\n";
}

# write function to call init**()

print $fh "\tpublic static final int utf16_max_length=$utf16_max_length;\n\n";
print $fh "\tstatic void initAll(){\n";
for(my $i=1;$i <= $func_num;++$i){
	print  $fh "\t\tinit$i();\n";
}
print $fh "\t}\n";

close($fh) or die "$out_file : $!";

#########################################################################
# shortname => unicode

{
	$out_file = "shortcode-emoji-data-and-old-emojione.json";
	open($fh, ">",$out_file) or die "$out_file : $!";

	my  @list;
	for my $name (sort keys %name_map){
		my $rh = $name_map{$name};
		my @res_list = values %$rh;
		my $res_info = $res_list[0];
		push @list,{shortcode=>$name,unicode=>$res_info->{unified}};
	}
	my $json = JSON->new->allow_nonref->relaxed(1)->pretty->canonical(1);
	print $fh $json->encode( \@list );

	close($fh) or die "$out_file : $!";

}

#########################################################################
__DATA__


code e006 emj_1f45a,emj_1f455 #  / 1F45A 👚 / 1F455 👕
code e007 emj_1f45f,emj_1f45e #  / 1F45F 👟 / 1F45E 👞
code e009 emj_1f4de,emj_260e #  / 1F4DE 📞 / 260E ☎
code e012 emj_1f64b,emj_270b #  / 1F64B 🙋 / 270B ✋
code e019 emj_1f41f,emj_1f3a3,emj_1f421 #  / 1F41F 🐟 / 1F3A3 🎣 / 1F421 🐡
code e02d emj_23f0,emj_1f559 #  / 23F0 ⏰ / 1F559 🕙
code e036 emj_1f3e1,emj_1f3e0 #  / 1F3E1 🏡 / 1F3E0 🏠
code e03d emj_1f3a5,emj_1f4f9 #  / 1F3A5 🎥 / 1F4F9 📹
code e044 emj_1f377,emj_1f379,emj_1f378 #  / 1F377 🍷 / 1F379 🍹 / 1F378 🍸
code e04c emj_1f31b,emj_1f313,emj_1f319,emj_1f314 #  / 1F31B 🌛 / 1F313 🌓 / 1F319 🌙 / 1F314 🌔
code e052 emj_1f436,emj_1f429 #  / 1F436 🐶 / 1F429 🐩
code e056 emj_1f60b,emj_1f60a #  / 1F60B 😋 / 1F60A 😊
code e057 emj_1f603,emj_1f63a #  / 1F603 😃 / 1F63A 😺
code e101 emj_1f4ea,emj_1f4eb #  / 1F4EA 📪 / 1F4EB 📫
code e103 emj_1f4e9,emj_2709,emj_1f4e8,emj_1f4e7 #  / 1F4E9 📩 / 2709 ✉ / 1F4E8 📨 / 1F4E7 📧
code e106 emj_1f63b,emj_1f60d #  / 1F63B 😻 / 1F60D 😍
code e10b emj_1f43d,emj_1f437 #  / 1F43D 🐽 / 1F437 🐷
code e110 emj_1f331,emj_1f340,emj_1f33f #  / 1F331 🌱 / 1F340 🍀 / 1F33F 🌿
code e112 emj_1f381,emj_1f4e6 #  / 1F381 🎁 / 1F4E6 📦
code e114 emj_1f50e,emj_1f50d #  / 1F50E 🔎 / 1F50D 🔍
code e12f emj_1f4b0,emj_1f4b2,emj_1f4b5 #  / 1F4B0 💰 / 1F4B2 💲 / 1F4B5 💵
code e137 emj_1f6a7,emj_26d4 #  / 1F6A7 🚧 / 26D4 ⛔
code e144 emj_1f510,emj_1f512,emj_1f50f #  / 1F510 🔐 / 1F512 🔒 / 1F50F 🔏
code e148 emj_1f4d9,emj_1f4d3,emj_1f4d4,emj_1f4da,emj_1f4d8,emj_1f4d2,emj_1f4d5,emj_1f4d7,emj_1f4d6,emj_1f4c7 #  / 1F4D9 📙 / 1F4D3 📓 / 1F4D4 📔 / 1F4DA 📚 / 1F4D8 📘 / 1F4D2 📒 / 1F4D5 📕 / 1F4D7 📗 / 1F4D6 📖 / 1F4C7 📇
code e14a emj_1f4ca,emj_1f4c8,emj_1f4b9 #  / 1F4CA 📊 / 1F4C8 📈 / 1F4B9 💹
code e202 emj_2693,emj_1f6a2 #  / 2693 ⚓ / 1F6A2 🚢
code e219 emj_1f534,emj_26ab,emj_26aa #  / 1F534 🔴 / 26AB ⚫ / 26AA ⚪
code e21a emj_2b1b,emj_1f532,emj_25fe,emj_1f535,emj_25fc,emj_25aa #  / 2B1B ⬛ / 1F532 🔲 / 25FE ◾ / 1F535 🔵 / 25FC ◼ / 25AA ▪
code e21b emj_1f539,emj_1f533,emj_1f538,emj_25ab,emj_2b1c,emj_1f536,emj_25fd,emj_25fb,emj_1f537 #  / 1F539 🔹 / 1F533 🔳 / 1F538 🔸 / 25AB ▫ / 2B1C ⬜ / 1F536 🔶 / 25FD ◽ / 25FB ◻ / 1F537 🔷
code e235 emj_1f519,emj_2b05 #  / 1F519 🔙 / 2B05 ⬅
code e236 emj_2197,emj_2934 #  / 2197 ↗ / 2934 ⤴
code e238 emj_2935,emj_2198 #  / 2935 ⤵ / 2198 ↘
code e23e emj_1f52f,emj_1f52e #  / 1F52F 🔯 / 1F52E 🔮
code e301 emj_1f4dd,emj_1f4c3,emj_1f4d1,emj_270f,emj_1f4cb,emj_1f4c4 #  / 1F4DD 📝 / 1F4C3 📃 / 1F4D1 📑 / 270F ✏ / 1F4CB 📋 / 1F4C4 📄
code e305 emj_1f33c,emj_1f33b #  / 1F33C 🌼 / 1F33B 🌻
code e30b emj_1f3ee,emj_1f376 #  / 1F3EE 🏮 / 1F376 🍶
code e316 emj_1f4be,emj_1f4bd #  / 1F4BE 💾 / 1F4BD 💽
code e326 emj_1f3b6,emj_1f3bc #  / 1F3B6 🎶 / 1F3BC 🎼
code e327 emj_1f496,emj_1f49e,emj_1f493,emj_1f495 #  / 1F496 💖 / 1F49E 💞 / 1F493 💓 / 1F495 💕
code e32e emj_2728,emj_2747 #  / 2728 ✨ / 2747 ❇
code e331 emj_1f4a6,emj_1f4a7 #  / 1F4A6 💦 / 1F4A7 💧
code e333 emj_2716,emj_274c,emj_274e #  / 2716 ✖ / 274C ❌ / 274E ❎
code e345 emj_1f34f,emj_1f34e #  / 1F34F 🍏 / 1F34E 🍎
code e403 emj_1f64d,emj_1f614,emj_1f629,emj_1f640 #  / 1F64D 🙍 / 1F614 😔 / 1F629 😩 / 1F640 🙀
code e404 emj_1f63c,emj_1f624,emj_1f601,emj_1f638 #  / 1F63C 😼 / 1F624 😤 / 1F601 😁 / 1F638 😸
code e406 emj_1f635,emj_1f62b,emj_1f623 #  / 1F635 😵 / 1F62B 😫 / 1F623 😣
code e407 emj_1f4ab,emj_1f616 #  / 1F4AB 💫 / 1F616 😖
code e409 emj_1f445,emj_1f61d #  / 1F445 👅 / 1F61D 😝
code e40a emj_1f606,emj_1f60c #  / 1F606 😆 / 1F60C 😌
code e412 emj_1f639,emj_1f602 #  / 1F639 😹 / 1F602 😂
code e413 emj_1f622,emj_1f63f #  / 1F622 😢 / 1F63F 😿
code e416 emj_1f64e,emj_1f621,emj_1f63e #  / 1F64E 🙎 / 1F621 😡 / 1F63E 😾
code e418 emj_1f63d,emj_1f618 #  / 1F63D 😽 / 1F618 😘
code e432 emj_1f693,emj_1f6a8 #  / 1F693 🚓 / 1F6A8 🚨
code e434 emj_1f687,emj_24c2 #  / 1F687 🚇 / 24C2 Ⓜ
code e44b emj_1f30c,emj_1f309,emj_1f303 #  / 1F30C 🌌 / 1F309 🌉 / 1F303 🌃
code e471 emj_1f603,emj_1f604 #  / 1F603 😃 / 1F604 😄
code e482 emj_2757,emj_2755 #  / 2757 ❗ / 2755 ❕
code e483 emj_2753,emj_2754 #  / 2753 ❓ / 2754 ❔
code e48b emj_2b50,emj_1f31f #  / 2B50 ⭐ / 1F31F 🌟
code e48f emj_2648,emj_1f411 #  / 2648 ♈ / 1F411 🐑
code e49a emj_2653,emj_1f41f #  / 2653 ♓ / 1F41F 🐟
code e4a5 emj_1f6be,emj_1f6bd,emj_1f6bb #  / 1F6BE 🚾 / 1F6BD 🚽 / 1F6BB 🚻
code e4b0 emj_1f685,emj_1f684 #  / 1F685 🚅 / 1F684 🚄
code e4b1 emj_1f699,emj_1f695,emj_1f697 #  / 1F699 🚙 / 1F695 🚕 / 1F697 🚗
code e4b4 emj_1f6a4,emj_26f5 #  / 1F6A4 🚤 / 26F5 ⛵
code e4d8 emj_1f434,emj_1f40e #  / 1F434 🐴 / 1F40E 🐎
code e4d9 emj_1f435,emj_1f412 #  / 1F435 🐵 / 1F412 🐒
code e4e0 emj_1f426,emj_1f424 #  / 1F426 🐦 / 1F424 🐤
code e4e1 emj_1f436,emj_1f43a #  / 1F436 🐶 / 1F43A 🐺
code e4e7 emj_1f61c,emj_1f61d #  / 1F61C 😜 / 1F61D 😝
code e4fa emj_1f469,emj_1f467 #  / 1F469 👩 / 1F467 👧
code e4fc emj_1f466,emj_1f468 #  / 1F466 👦 / 1F468 👨
code e501 emj_1f46a,emj_1f3e9 #  / 1F46A 👪 / 1F3E9 🏩
code e502 emj_1f3a8,emj_1f4fa #  / 1F3A8 🎨 / 1F4FA 📺
code e503 emj_1f3ad,emj_1f3a4,emj_1f3a9 #  / 1F3AD 🎭 / 1F3A4 🎤 / 1F3A9 🎩
code e504 emj_1f45b,emj_1f3ec #  / 1F45B 👛 / 1F3EC 🏬
code e505 emj_1f3b6,emj_1f3ef #  / 1F3B6 🎶 / 1F3EF 🏯
code e506 emj_1f3b8,emj_1f3f0 #  / 1F3B8 🎸 / 1F3F0 🏰
code e507 emj_1f3bb,emj_1f3a6 #  / 1F3BB 🎻 / 1F3A6 🎦
code e508 emj_1f3a7,emj_1f3ed #  / 1F3A7 🎧 / 1F3ED 🏭
code e509 emj_1f484,emj_1f5fc #  / 1F484 💄 / 1F5FC 🗼
code e50b emj_1f1ef_1f1f5,emj_1f486 #  / 1F1EF-1F1F5 🇯🇵 / 1F486 💆
code e50c emj_1f4c0,emj_1f1fa_1f1f8,emj_1f4bf #  / 1F4C0 📀 / 1F1FA-1F1F8 🇺🇸 / 1F4BF 💿
code e50d emj_1f45a,emj_1f1eb_1f1f7 #  / 1F45A 👚 / 1F1EB-1F1F7 🇫🇷
code e50e emj_1f1e9_1f1ea,emj_1f47d #  / 1F1E9-1F1EA 🇩🇪 / 1F47D 👽
code e50f emj_1f1ee_1f1f9,emj_1f199 #  / 1F1EE-1F1F9 🇮🇹 / 1F199 🆙
code e510 emj_1f489,emj_1f1ec_1f1e7 #  / 1F489 💉 / 1F1EC-1F1E7 🇬🇧
code e511 emj_1f4e3,emj_1f50a,emj_1f4e2,emj_1f1ea_1f1f8 #  / 1F4E3 📣 / 1F50A 🔊 / 1F4E2 📢 / 1F1EA-1F1F8 🇪🇸
code e512 emj_1f1f7_1f1fa,emj_1f514 #  / 1F1F7-1F1FA 🇷🇺 / 1F514 🔔
code e513 emj_1f1e8_1f1f3,emj_1f340 #  / 1F1E8-1F1F3 🇨🇳 / 1F340 🍀
code e514 emj_1f1f0_1f1f7,emj_1f48e,emj_1f48d #  / 1F1F0-1F1F7 🇰🇷 / 1F48E 💎 / 1F48D 💍
code e515 emj_1f471,emj_1f4f7 #  / 1F471 👱 / 1F4F7 📷
code e516 emj_1f472,emj_2702 #  / 1F472 👲 / 2702 ✂
code e517 emj_1f473,emj_1f3a5,emj_1f3a6 #  / 1F473 👳 / 1F3A5 🎥 / 1F3A6 🎦
code e518 emj_1f50d,emj_1f474 #  / 1F50D 🔍 / 1F474 👴
code e519 emj_1f475,emj_1f511 #  / 1F475 👵 / 1F511 🔑
code e51a emj_1f461,emj_1f476,emj_1f460 #  / 1F461 👡 / 1F476 👶 / 1F460 👠
code e51b emj_1f477,emj_1f4ee,emj_1f4ea #  / 1F477 👷 / 1F4EE 📮 / 1F4EA 📪
code e51c emj_1f513,emj_1f512,emj_1f478 #  / 1F513 🔓 / 1F512 🔒 / 1F478 👸
code e51d emj_1f5fd,emj_1f4db #  / 1F5FD 🗽 / 1F4DB 📛
code e51e emj_1f4de,emj_1f482 #  / 1F4DE 📞 / 1F482 💂
code e51f emj_1f4e6,emj_1f483 #  / 1F4E6 📦 / 1F483 💃
code e520 emj_1f42c,emj_1f4e0 #  / 1F42C 🐬 / 1F4E0 📠
code e521 emj_1f426,emj_2709 #  / 1F426 🐦 / 2709 ✉
code e522 emj_0031_20e3,emj_1f420 #  / 0031-20E3 1⃣ / 1F420 🐠
code e523 emj_1f425,emj_1f424,emj_0032_20e3,emj_1f423 #  / 1F425 🐥 / 1F424 🐤 / 0032-20E3 2⃣ / 1F423 🐣
code e524 emj_1f439,emj_0033_20e3 #  / 1F439 🐹 / 0033-20E3 3⃣
code e525 emj_1f41b,emj_0034_20e3 #  / 1F41B 🐛 / 0034-20E3 4⃣
code e526 emj_0035_20e3,emj_1f418 #  / 0035-20E3 5⃣ / 1F418 🐘
code e527 emj_1f428,emj_0036_20e3 #  / 1F428 🐨 / 0036-20E3 6⃣
code e528 emj_1f412,emj_0037_20e3 #  / 1F412 🐒 / 0037-20E3 7⃣
code e529 emj_1f411,emj_0038_20e3 #  / 1F411 🐑 / 0038-20E3 8⃣
code e52a emj_1f43a,emj_0039_20e3 #  / 1F43A 🐺 / 0039-20E3 9⃣
code e52b emj_1f42e,emj_1f51f #  / 1F42E 🐮 / 1F51F 🔟
code e52d emj_25c0,emj_1f40d #  / 25C0 ◀ / 1F40D 🐍
code e52e emj_25b6,emj_1f414 #  / 25B6 ▶ / 1F414 🐔
code e52f emj_1f417,emj_23ea #  / 1F417 🐗 / 23EA ⏪
code e530 emj_1f42b,emj_23e9 #  / 1F42B 🐫 / 23E9 ⏩
code e531 emj_25ab,emj_1f438 #  / 25AB ▫ / 1F438 🐸
code e532 emj_1f170,emj_25aa #  / 1F170 🅰 / 25AA ▪
code e533 emj_2139,emj_1f171 #  / 2139 ℹ / 1F171 🅱
code e534 emj_25fd,emj_1f18e #  / 25FD ◽ / 1F18E 🆎
code e535 emj_1f17e,emj_25fe #  / 1F17E 🅾 / 25FE ◾
code e536 emj_1f43e,emj_1f463,emj_1f538 #  / 1F43E 🐾 / 1F463 👣 / 1F538 🔸
code e537 emj_2122,emj_1f539 #  / 2122 ™ / 1F539 🔹
code e54b emj_1f533,emj_1f535,emj_1f532 #  / 1F533 🔳 / 1F535 🔵 / 1F532 🔲
code e594 emj_23f0,emj_1f555,emj_1f55b,emj_1f554,emj_1f557,emj_1f556,emj_1f55a,emj_1f552,emj_1f553,emj_1f550,emj_1f551,emj_1f559,emj_1f558 #  / 23F0 ⏰ / 1F555 🕕 / 1F55B 🕛 / 1F554 🕔 / 1F557 🕗 / 1F556 🕖 / 1F55A 🕚 / 1F552 🕒 / 1F553 🕓 / 1F550 🕐 / 1F551 🕑 / 1F559 🕙 / 1F558 🕘
code e595 emj_1f49f,emj_2764 #  / 1F49F 💟 / 2764 ❤
code e5bb emj_1f492,emj_26ea #  / 1F492 💒 / 26EA ⛪
code e5bc emj_24c2,emj_1f687 #  / 24C2 Ⓜ / 1F687 🚇
code e5c6 emj_1f613,emj_1f625 #  / 1F613 😓 / 1F625 😥
code e5c9 emj_1f531,emj_1f451 #  / 1F531 🔱 / 1F451 👑
code e5cd emj_1f342,emj_1f343 #  / 1F342 🍂 / 1F343 🍃
code e5da emj_1f307,emj_1f306 #  / 1F307 🌇 / 1F306 🌆
code e63e emj_1f305,emj_2600,emj_1f307,emj_1f304 #  / 1F305 🌅 / 2600 ☀ / 1F307 🌇 / 1F304 🌄
code e643 emj_1f300,emj_1f365 #  / 1F300 🌀 / 1F365 🍥
code e65c emj_1f687,emj_24c2 #  / 1F687 🚇 / 24C2 Ⓜ
code e65d emj_1f685,emj_1f684 #  / 1F685 🚅 / 1F684 🚄
code e65e emj_1f697,emj_1f695 #  / 1F697 🚗 / 1F695 🚕
code e661 emj_2693,emj_1f6a2 #  / 2693 ⚓ / 1F6A2 🚢
code e663 emj_1f3e0,emj_1f3e1 #  / 1F3E0 🏠 / 1F3E1 🏡
code e665 emj_1f3e3,emj_1f4eb,emj_1f4ee,emj_1f4ea #  / 1F3E3 🏣 / 1F4EB 📫 / 1F4EE 📮 / 1F4EA 📪
code e66e emj_1f6be,emj_1f6bd,emj_1f6bb #  / 1F6BE 🚾 / 1F6BD 🚽 / 1F6BB 🚻
code e671 emj_1f379,emj_1f378 #  / 1F379 🍹 / 1F378 🍸
code e672 emj_1f37a # ビール / 1F37B 🍻 / 1F37A 🍺
code e674 emj_1f460 # ブティック(ハイヒール) / 1F460 👠 / 1F461 👡
code e675 emj_2702_fe0f # 美容院(はさみ) / 1F487 💇 / 2702 ✂
code e677 emj_1f3a5 # 映画 / 1F3A5 🎥 / 1F3A6 🎦 / 1F4F9 📹



code e682 emj_1f45c # カバン / 1F4BC 💼 / 1F45C 👜
code e683 emj_1f4d9 # 本 / 1F4D8 📘 / 1F4D2 📒 / 1F4DA 📚 / 1F4C7 📇 / 1F4D7 📗 / 1F4D6 📖 / 1F4D5 📕 / 1F4D3 📓 / 1F4D4 📔 / 1F4D9 📙
code e685 emj_1f381 # プレゼント / 1F4E6 📦 / 1F381 🎁
code e687 emj_260e_fe0f # 電話 / 1F4DE 📞 / 260E ☎
code e689 emj_1f4c4 # メモ / 1F4CB 📋 / 1F4C4 📄 / 1F4DD 📝 / 1F4C3 📃 / 1F4D1 📑
code e68c emj_1f4bf # ＣＤ / 1F4BF 💿 / 1F4C0 📀
code e695 emj_270b # 手（パー） / 1F44B 👋 / 270B ✋ / 1F450 👐
code e698 emj_1f463 # 足あと / 1F43E 🐾 / 1F463 👣
code e699 emj_1f45f # くつ / 1F45F 👟 / 1F45E 👞
code e69c emj_1f311 # 新月 / 1F532 🔲 / 26AA ⚪ / 1F533 🔳 / 1F535 🔵 / 1F534 🔴 / 26AB ⚫ / 1F311 🌑
code e69e emj_1f313 # 半月 / 1F313 🌓 / 1F31B 🌛
code e6a0 emj_1f315 # 満月  / 2B55 ⭕ / 1F315 🌕
code e6a1 emj_1f436 # 犬 / 1F436 🐶 / 1F43A 🐺 / 1F429 🐩
code e6a3 emj_26f5 # リゾート(ヨット)  / 26F5 ⛵ / 1F6A4 🚤
code e6b3 emj_1f303 # 夜 / 1F30C 🌌 / 1F309 🌉 / 1F303 🌃
code e6ba emj_23f0 # 時計10:10ごろ / 1F553 🕓 / 1F552 🕒 / 1F558 🕘 / 1F559 🕙 / 1F551 🕑 / 1F550 🕐 / 1F554 🕔 / 1F555 🕕 / 1F55B 🕛 / 23F0 ⏰ / 1F55A 🕚 / 1F556 🕖 / 1F557 🕗
code e6cf emj_1f4e9 # mail to / 1F4E8 📨 / 1F4E9 📩
code e6d3 emj_1f4e7 # メール / 1F4E7 📧 / 2709 ✉
code e6d9 emj_1f511 # パスワード / 1F513 🔓 / 1F510 🔐 / 1F512 🔒 / 1F50F 🔏 / 1F511 🔑
code e6dc emj_1f50d # サーチ（調べる） / 1F50D 🔍 / 1F50E 🔎
code e6ec emj_1f496 # ハート  / 1F496 💖 / 1F49D 💝 / 2764 ❤ / 1F49B 💛 / 1F499 💙 / 1F49C 💜 / 1F498 💘 / 1F49A 💚
code e6ed emj_1f49e # 揺れるハート / 1F491 💑 / 1F497 💗 / 1F49E 💞 / 1F493 💓
code e6f0 emj_1f604 # わーい（嬉しい顔） / 1F63A 😺 / 263A ☺ / 1F467 👧 / 1F468 👨 / 1F469 👩 / 1F604 😄 / 1F603 😃 / 1F466 👦 / 1F60A 😊
code e6f1 emj_1f620 # ちっ（怒った顔） / 1F620 😠 / 1F64E 🙎
code e6f3 emj_1f616 # もうやだ～（悲しい顔） / 1F640 🙀 / 1F616 😖 / 1F629 😩 / 1F64D 🙍
code e6f4 emj_1f635 # ふらふら / 1F635 😵 / 1F632 😲
code e6f7 emj_2668_fe0f # いい気分（温泉） / 1F6C0 🛀 / 2668 ♨
code e6f8 emj_2734_fe0f # かわいい / 1F49F 💟 / 2733 ✳ / 1F4A0 💠 / 2734 ✴
code e6f9 emj_1f48b #  キスマーク / 1F48F 💏 / 1F444 👄 / 1F48B 💋
code e6fa emj_2728 # ぴかぴか（新しい） / 2728 ✨ / 2747 ❇
code e6fb emj_1f4a1 # ひらめき / 1F526 🔦 / 1F4A1 💡
code e6ff emj_1f3b6 # ムード  / 1F3BC 🎼 / 1F3B6 🎶
code e700 emj_2935_fe0f # バッド（下向き矢印） / 1F44E 👎 / 2935 ⤵
code e701 emj_1f4a4 # 眠い(睡眠) / 1F62A 😪 / 1F4A4 💤
code e702 emj_2757 # exclamation / 2757 ❗ / 2755 ❕
code e70a emj_27b0 # ー（長音記号２） / 27B0 ➰ / 1F4DC 📜
code e70b emj_1f197 # docomo 決定 / 1F44C 👌 / 1F197 🆗 / 1F646 🙆
code e70e emj_1f455 # docomo Tシャツ（ボーダー） / 1F45A 👚 / 1F455 👕
code e712 emj_1f3c2 # docomo スノボ / 1F3C4 🏄 / 1F3C2 🏂
code e715 emj_1f4b0 #docomo ドル袋  / 1F4B0 💰 / 1F4B2 💲 / 1F4B5 💵
code e71a emj_1f451 # docomo 王冠 / 1F451 👑 / 1F531 🔱
code e71b emj_1f48d # docomo 指輪 / 1F48E 💎 / 1F48D 💍
code e71c emj_231b # docomo 砂時計 / 231B ⌛ / 23F3 ⏳
code e723 emj_1f613 # docomo 冷や汗と無表情 / 1F613 😓 / 1F630 😰 / 1F625 😥
code e724 emj_1f621 # docomo ぷっくっくな顔 / 1F63E 😾 / 1F621 😡
code e726 emj_1f60d # docomo 目がハート / 1F63D 😽 / 1F63B 😻 / 1F61A 😚 / 1F60D 😍 / 1F618 😘
code e728 emj_1f61c # docomo あっかんべー / 1F61D 😝 / 1F445 👅 / 1F61C 😜
code e72a emj_1f606 # docomo うれしい顔 / 1F602 😂 / 1F633 😳 / 1F639 😹 / 1F606 😆
code e72b emj_1f623 # docomo がまん顔 / 1F62B 😫 / 1F623 😣
code e72e emj_1f622 # docomo 涙 / 1F63F 😿 / 1F622 😢
code e72f emj_1f196 # docomo NG / 26D4 ⛔ / 1F196 🆖 / 1F645 🙅
code e733 emj_1f3c3 # docomo 走る人(右向き) / 1F3C3 🏃 / 1F6B6 🚶
code e735 emj_267b_fe0f  # docomo リサイクル(緑) / 267B ♻ / 1F503 🔃
code e738 emj_1f232 # docomo 禁止 / 1F6AB 🚫 / 1F232 🈲
code e741 emj_1f340 # docomo 四葉のクローバー / 1F340 🍀 / 1F33F 🌿
code e745 emj_1f34e # docomo りんご(赤い) / 1F34E 🍎 / 1F34F 🍏
code e747 emj_1f341 # docomo もみじ/ 1F341 🍁 / 1F342 🍂
code e74b emj_1f376 # docomo とっくりとおちょこ / 1F3EE 🏮 / 1F376 🍶
code e74c emj_1f35c # docomo どんぶり(湯気。中身は見えない) / 1F35A 🍚 / 1F35C 🍜
code e74f emj_1f424 # docomo ひよこ / 1F423 🐣 / 1F426 🐦 / 1F424 🐤 / 1F425 🐥
code e751 emj_1f41f # docomo 魚 / 1F420 🐠 / 1F41F 🐟 / 1F421 🐡 / 1F3A3 🎣
code e753 emj_1f601 # docomo ウッシッシ / 1F624 😤 / 1F638 😸 / 1F601 😁 / 1F63C 😼
code e754 emj_1f434 # docomo 馬の首 / 1f40e 🐎 Horse / 1f434 🐴 Horse Face
code e755 emj_1f437 # docomo ブタ / 1f437 Pig Face / 1f43d Pig Nose
code e757 emj_1f631 # docomo げっそり/ 1f628 Fearful Face / 1F631 Face Screaming in Fear 
code ea8f emj_1f52e # ezweb 占い(水晶玉) / 1f52e Crystal Ball / 1f52f Six Pointed Star With Middle Dot
code eac0 emj_1f61e # ezweb しょんぼり/ 1f61e Disappointed Face / 1f614 Pensive Face
code eac5 emj_1f60c # ezweb てれてれ/ 1f60c Relieved Face / 1f606 Smiling Face with Open Mouth and Closed Eyes
code eacd emj_1f60a # ezweb にこにこ/ 1f60a Smiling Face with Smiling Eyes / 1f60b Face Savouring Delicious Food
code ead6 emj_1f44b # ezweb bye (手のひらを振る) / 1F44B: WAVING HAND SIGN / 1F450: OPEN HANDS SIGN
code eaf4 emj_1f305 # ezweb 日の出と海 / 1f304 Sunrise Over Mountains / 1f305 Sunrise
code eb18 emj_1f476 # ezweb 赤ちゃん(の顔) / 1f6bc Baby Symbol Emoji / 1f476 Baby Emoji
code eb75 emj_1f493 # ezweb ドキドキしているハート / 1f493=Beating Heart / 1F497 Growing Heart
code feb64 emj_1f535 # feb64=LARGE BLUE CIRCLE / 1F535=LARGE BLUE CIRCLE / 1F532=BLACK SQUARE BUTTON

name city_sunset   emj_1f307
name email         emj_1f4e7
name family        emj_1f46a
name man_woman_boy emj_1f468_200d_1f469_200d_1f466
name medal         emj_1f3c5
name satellite     emj_1f6f0_fe0f
name snowman       emj_26c4
name umbrella      emj_2602_fe0f
name cricket      emj_1f997
name cricket_bat_and_ball emj_1f3cf


category activities military_medal
