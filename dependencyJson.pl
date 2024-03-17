#!/usr/bin/perl --

# - カレントディレクトリで./gradlew :app:dependencies して依存関係を列挙する
# - ユーザフォルダの.gradle/ にあるpomファイルを探索する
# - 依存関係とpomファイルを突き合わせて json を出力する

use 5.32.1;
use strict;
use warnings;
use Getopt::Long;
use File::Basename;
use File::Find;
use File::Path qw(make_path remove_tree);
use File::Copy;
use JSON5;
use JSON::XS;
use Types::Serialiser;
use constant{
    true =>Types::Serialiser::true,
    false =>Types::Serialiser::false,
};
use XML::XPath;
use XML::XPath::XMLParser;
use Data::Dump qw(dump);
use Archive::Zip qw( :ERROR_CODES :CONSTANTS );
use LWP::UserAgent;
my $ua = LWP::UserAgent->new(timeout => 10);
$ua->env_proxy;

sub loadFile($){
    my($file)=@_;
    open(my $fh,"<:raw",$file) or die "$! $file";
    local $/ = undef;
    my $data = <$fh>;
    close($fh) or die "$! $file";
    return $data;
}

# 出力フォルダがなければ作る
sub prepareDirectory($){
    my($dir)=@_;
    return if -d $dir;
    make_path($dir) or die "can't create directory. $dir";
}

#####################################################
# オプション解析、値の検証、出力フォルダの作成

# 設定ファイル
my $configFile = "config/dependencyJsonConfig.json5";
GetOptions ("configFile=s" => \$configFile) or die("bad options.\n");
my $config = decode_json5(loadFile $configFile);

# ライブラリのライセンス情報
my $initialLicenses = $config->{licenses}
    or die "config.initialLicenses is missing.";

# POM解析の検証用データ
# - POMのXML解析時に取得漏れがあればエラーとしたい
# - しかしXMLに元々情報がない場合はエラーを出したくない
# - なので情報がないライブラリを列挙しておく

# 以下のライブラリはpomにDevelopersがなくても許容する
my $libsMissingDevelopers = $config->{libsMissingDevelopers}
    or die"config.libsMissingDevelopers is missing.";

# 以下のライブラリはpomにライセンス指定がなくても許容する
my $libsMissingLicenses = $config->{libsMissingLicenses}
    or die"config.libsMissingLicenses is missing.";

# 以下のライブラリはpomにライセンス名の指定がなくても許容する
my $libsMissingLicenseName = $config->{libsMissingLicenseName}
    or die"config.libsMissingLicenseName is missing.";

# 以下のライブラリはpomにWebサイトがなくても許容する
my $libsMissingWebSite = $config->{libsMissingWebSite}
    or die"config.libsMissingWebSite is missing.";

# idがprefixesリストのいずれかに前方一致するなら真
sub matchLibs($$){
    my($id,$prefixes)=@_;
    for my $prefix(@$prefixes){
        return true if $id =~/\A$prefix/;
    }
    return false;
}

# デバッグ用。指定があればそのフォルダにpomファイルをコピーする。
my $pomDir = $config->{pomDumpDir};
$pomDir and prepareDirectory( $pomDir );

# pomのメタ情報を読む
sub readPomInfo($$){
    my($name, $xp)=@_;
    my $groupId = $xp->findvalue('/project/groupId')->value()
         || $xp->findvalue('/project/parent/groupId')->value()
         || die "missing groupId in $name";

    my $artifactId = $xp->findvalue('/project/artifactId')->value()
         || $xp->findvalue('/project/parent/artifactId')->value()
         || die "missing artifactId in $name";

    my $version = $xp->findvalue('/project/version')->value()
        || $xp->findvalue('/project/parent/version')->value()
        || die "missing version in $name";

    return {
        groupId => $groupId,
        artifactId => $artifactId,
        version => $version,
        # 
        fullName => "$groupId:$artifactId:$version",
        groupAndArtifact => "$groupId:$artifactId",
    };
}

# pomを読んで出力用データに変換する
sub parsePom($$){
    my($errors,$found) = @_;
    my $pomInfo = $found->{pomInfo};
    my $id = $found->{dep};

    # デバッグ用：pomファイルをコピーする
    # スクリプトから使う訳ではない
    if($pomDir){
        # idの:を_に変更する
        my $idSafe = $id;
        $idSafe =~ s/:/_/g;
        # ファイルがまだなければコピーする
        my $outPomFile = "$pomDir/$idSafe.pom";
        -e $outPomFile or copy($pomInfo->{pomFile}, $outPomFile);
    }

    my $info = {
        id => $id,
        artifactVersion => $pomInfo->{version},
    };

    # xpathを使ってXMLからデータを読む
    my $xp = XML::XPath->new(filename => $pomInfo->{pomFile});

    my $developers = $info->{developers} = [];
    for my $node( $xp->findnodes("/project/developers/developer") ){
        my $name = $node->findvalue("name")->value()
            || $node->findvalue("id")->value();
        if(not $name){
            push @$errors,"[$id]missing developer.name";
            next;
        }
        push @$developers,{ name => $name, };
    }

    if( not @$developers
    and not matchLibs($id,$libsMissingDevelopers) 
    ){
        push @$errors,"[$id]missing developers.";
    }

    my $licenses = $info->{licenses} = [];
    for my $node( $xp->findnodes("/project/licenses/license") ){
        my $url = $node->findvalue('url')->value();
        if(not $url){
            push @$errors,"[$id]missing license.url";
            next;
        }

        my $name = $node->findvalue('name')->value();
        if( not $name){
            if( matchLibs($id,$libsMissingLicenseName) ){
                $name = "Unknown license";
            }else{
                push @$errors,"[$id]missing license.name";
                next;
            }
        }

        push @$licenses, { name => $name, url => $url, };
    }

    if( not @$licenses
    and not matchLibs($id,$libsMissingLicenses)
    ){
        push @$errors,"[$id]missing licenses.";
    }

    my $name = $xp->findvalue('/project/name')->value();
    $name and $info->{name} = $name;

    my $description = $xp->findvalue('/project/description')->value();
    if($description){
        $description =~ s/\A\s+//;
        $description =~ s/\s+\z//;
        $description and $info->{description} = $description;
    }

    my $webSite = $info->{website} = $xp->findvalue('/project/url')->value()
        || $xp->findvalue('/project/scm/url')->value();

    if($webSite){
         $info->{website} = $webSite;
    }elsif( not matchLibs($id,$libsMissingWebSite) ){
        push @$errors,"[$id]missing website.";
    }

    return $info;
}


# aarファイルにはpom.xmlが含まれないのでmvnコマンドで取得する。
sub downloadPom($){
    my($dep)=@_;
    # ダウンロードしたjarの保存フォルダ
    my $dirDlSave = ".depCheck/download";
    prepareDirectory( $dirDlSave );
    # ダウンロードしたファイル
    my $file = "$dirDlSave/$dep.pom";
    $file =~ s/:/_/g;
    if( not -f $file){
        say "downloading pom for $dep";
        $dep =~ m|^([^:]+):([^:]+):([^:]+)$|;
        my($groupId,$artifactId,$version)=($1,$2,$3);
        my $groupIdSlashed = $groupId;
        $groupIdSlashed =~ s|\.|/|g;

        my $successResponse;
        my @errorResponses;
        for my $repo(@{$config->{repos}}){
            my $url = "$repo/$groupIdSlashed/$artifactId/$version/$artifactId-$version.pom";
            my $response = $ua->get($url);
            if( $response->is_success) {
                $successResponse = $response;
                last;
            }else{
                push @errorResponses,$response;
            }
        }
        if(!$successResponse){
            for(@errorResponses){
                say $_->status_line ," ", $_->request->uri;
            }
            die "can't download $dep.";
        }
        open(my $fh,">:raw",$file) or die "$! $file";
        print $fh $successResponse->content;
        close($fh) or die "$! $file";
    }
    my $xp = XML::XPath->new(filename => $file);
    my $pomInfo = readPomInfo($file,$xp);
    $pomInfo->{pomFile} = $file;
    return $pomInfo;
}

# gradleで依存関係を列挙する
sub listingDependencies($){
    my($configuration)=@_;

    my $cmd = "./gradlew -q --no-configuration-cache :app:dependencies --configuration $configuration";
    say $cmd;
    open(my $fh,"-|",$cmd) or die "failed to get dependencies: $!";

    my %deps;
    while(<$fh>){
        s/[\x0d\x0a]+//;
        s/\s+\z//;

        # 依存関係は5文字単位でインデントされる
        next if not s/\A[ \\|+-]{5,}//;

        # 子プロジェクトは対象外
        next if /^project :/;

        # 末尾の注釈を除去
        s/\s*\([c*]\)$//;

        # "->" の対応：バージョンのみが変わる場合
        s/([^ :]+?) -> ([^ :]+?)$/$2/;
        # "->" の対応：パッケージごと変わる場合
        s/(\S+?) -> (\S+?)$/$2/;

        $_ and $deps{$_} = 1;
    }
    close($fh) or die "failed to get dependencies: $!";
    
    my $depsCount = 0+(keys %deps);
    $depsCount or die "ERROR: dependencies not found!";
    say "$depsCount dependencies found.";
    
    return \%deps;
}

# 依存関係とpomを照合してライブラリ毎の出力データを読み取る
sub mergeDepsAndPoms($){
    my($depMap)=@_;

    # 依存関係とpomを照合して @founds と @missings に分類する
    my @founds;
    for my $dep (sort keys %$depMap){
        my $pomInfo = downloadPom($dep);
        push @founds, {
            dep => $dep,
            pomInfo=>$pomInfo,
        }
    }

    # pomのパース
    my @errors;
    my @info = map{ parsePom(\@errors, $_) } @founds;
    if(@errors){
        say $_ for @errors;
        exit 1;
    }
    my $size = 0 + @info;
    say "$size library information parsed.";

    return \@info;
}

# @$licenses の要素でURLがマッチするものを返す
sub findLisenceByUrl($$){
    my($licenses,$url) = @_;
    for( @$licenses){
        return $_ if grep{ $_ eq $url } @{$_->{urls}};
    }
    return;
}

# ライセンスのshortNameを返す
# @$licensesにデータがなければ追加する
sub licenseShortName($$){
    my($licenses,$json)=@_;

    my($item) = findLisenceByUrl($licenses,$json->{url});
    if(not $item){
        $item = {
            shortName => $json->{name},
            name => $json->{name},
            urls =>[ $json->{url} ],
        };
        push @$licenses,$item;
    }
    return $item->{shortName};
}

# ライセンス情報をまとめる
sub compactLisences($$){
    my($initialLicenseList,$libs)=@_;

    # 変更するライセンスリスト
    # ディープコピーする
    my $licenses = decode_json encode_json $initialLicenseList;

    # ライブラリごとにライセンスのリストがあるので、それをshortNameのリストに変換する
    for my $lib (@$libs){
        @{$lib->{licenses}} = map{ licenseShortName($licenses,$_) } @{$lib->{licenses}};
    }

    # 出力結果の並び順を安定させるため、ライセンス一覧をshortNameでソートする
    @$licenses = sort {$a->{shortName} cmp $b->{shortName} } @$licenses;

    say "licenses:";
    for(@$licenses){
        my $url = $_->{urls}[0];
        say "  [$_->{shortName}] name='$_->{name}' url=$url";
    }
    
    return $licenses;
}

# 情報をJSONファイルに出力
sub outputDepJson($$$){
    my($outFile,$libs,$licences)=@_;
    open(my $fh,">:raw",$outFile) or die "$outFile $!";
    print $fh encode_json {
        libs => $libs,
        licenses => $licences,
    };
    close($fh) or die "$outFile $!";
}

##################################################
# - 出力ファイルごとの処理
# - ただしGradleキャッシュのスキャンは1回だけ

my $outputs = $config->{outputs} or die "contif.outputs is missing.";
@$outputs or die "contif.outputs is empty.";

# validation
my $outIndex = 0;
for my $out (@$outputs){
    my $name = $out->{name} or die "config.outputs[$outIndex].name is missing.";
    $out->{outFile} or die "config.outputs[$name].outFile is missing.";
    $out->{configuration} or die "config.outputs[$name].configuration is missing.";
    prepareDirectory( dirname($out->{outFile}) );

    # gradleで依存関係を列挙する
    say "# [$name] listing dependencies ...";
    $out->{deps} = listingDependencies $out->{configuration};

    # 依存関係とpomを照合してライブラリ毎の出力データを読み取る
    say "# [$name] read lib data from dependencies and pom data.";
    my $libs = mergeDepsAndPoms($out->{deps});

    # 追加の依存関係
    my $addItems = decode_json encode_json $config->{additionalLibs};
    @$libs = ( @$addItems , @$libs );

    # ライセンス情報をまとめる
    say "# [$name] compacting licenses ...";
    my $licenses = compactLisences($initialLicenses,$libs);

    # 情報をJSONファイルに出力
    say "# [$name] save to json $out->{outFile}";
    outputDepJson($out->{outFile},$libs,$licenses);

    ++$outIndex;
}

say "complete!!";
