#!/usr/bin/perl --
use 5.32.1;
use strict;
use warnings;
use utf8;
use Getopt::Long;
use Data::Dump qw(dump);
use File::Basename;
use File::Find;
use File::Path qw(make_path remove_tree);
use File::Copy;
use JSON5;
use JSON::XS;
use List::Util qw(any);
use Types::Serialiser;
use XML::XPath;
use XML::XPath::XMLParser;
use Archive::Zip qw( :ERROR_CODES :CONSTANTS );
use LWP::UserAgent;

use constant{
    true => Types::Serialiser::true,
    false => Types::Serialiser::false,
};
   
binmode $_,":encoding(utf8)" for \*STDOUT,\*STDERR;

######################################################
# コマンドラインオプション

my $buildFile = "app/build.gradle.kts";
my $configFile = "config/dependencyJsonConfig.json5";

my $usage = <<"END";
usage: $0 [options] {action}

  action is one of:
    tag
      リリースビルド向け。依存関係JSON更新とAPK生成を行ってからタグを打つ。
    apk
      試験ビルド向け。APKを生成するが、依存関係JSONの更新やタグ打ちは行わない。
    depJson
      依存関係JSONを生成する。git操作は全く行わない。

  options:
    --configFile=(file path)
                    path of config file. 
                    default: $configFile
    --buildFile=(file path)
                    path of app build file.
                    default: $buildFile
END

GetOptions (
    "configFile=s" => \$configFile,
    "buildFile=s" => \$buildFile,
) or die("!! bad options. !!\n$usage");

######################################################
# ユーティリティ

my $jsonXs = new JSON::XS->utf8->pretty(1)->indent(1)->canonical(1);

# ディープコピー
sub deepCopy($){
    return $jsonXs->decode( $jsonXs->encode($_[0]));
}

sub cmd($){
	print "+ ",$_[0],"\n";
	my $rv = system $_[0];
	if ($? == -1) {
        die "failed to execute: $!\n";
    }elsif ($? & 127) {
        die sprintf "child died with signal %d\n", ($? & 127);
    }elsif($?){
        $rv = $? >> 8;
        die "child exited with value $rv\n";
    }
}

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

# idがprefixesリストのいずれかに前方一致するなら真
sub matchLibs($$){
    my($id,$prefixes)=@_;
    for my $prefix(@$prefixes){
        return true if $id =~/\A$prefix/;
    }
    return false;
}

my $reInvalidChars = qr/[:<>"\?\*\/\|\\]/;
sub sanitizeFileName($;$){
    my($str,$replacement)=(@_,'-');
    if( $replacement =~ $reInvalidChars ){
    	die "sanitizeFileName: $replacement is one of invalid character.";
    }
    $str =~ s/$reInvalidChars/$replacement/g;
    $str =~ s/\Q$replacement\E{2,}/$replacement/g;
	return $str;
}

######################################################
# 設定ファイルを読む

my $config = decode_json5(loadFile $configFile);

######################################################
# 依存関係JSONの出力

my $ua = LWP::UserAgent->new(timeout => 10);
$ua->env_proxy;

# ダウンロードしたPOMデータの保存フォルダ
my $dirDlSave = ".depCheck/download";
prepareDirectory( $dirDlSave );

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

# gradleで依存関係を列挙する
sub listDependencies($){
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
        
        my $before = $_;
        
        # "->" の対応：変更前のバージョンがカラの場合がある
        s/^([^ :]+:[^ :]+) -> ([^ :]+)$/$1:$2/;

        # "->" の対応：バージョンのみが変わる場合
        s/([^ :]+?) -> ([^ :]+)$/$2/;

        # "->" の対応：パッケージごと変わる場合
        s/(\S+?) -> (\S+?)$/$2/;

        # ($_ eq $before) or say "changed: $before $_";

        $_ and $deps{$_} = 1;
    }
    close($fh) or die "failed to get dependencies: $!";
    
    my $depsCount = 0+(keys %deps);
    $depsCount or die "ERROR: dependencies not found!";
    say "$depsCount dependencies found.";
    return sort keys %deps;
}

# POMをダウンロードしてを指定ファイルに保存する
# ファイルが既に存在するなら何もしない
sub pomDownload($$){
    my($dep,$outFile)=@_;
    return if -f $outFile;
    say "downloading pom for $dep";
    my $urlSuffix;
    if( $dep =~ m|^([^:]+):([^:]+):([^:]+)$| ){
        my($groupId,$artifactId,$version)=($1,$2,$3);
        my $groupIdSlashed = $groupId;
        $groupIdSlashed =~ s|\.|/|g;
        $urlSuffix = "$groupIdSlashed/$artifactId/$version/$artifactId-$version.pom";
    }else{
        die "can't parse urlSuffix from string '$dep'";
    }

    # 複数のリポジトリを確認する
    my $successResponse;
    my @errorResponses;
    for my $repo(@{$config->{repos}}){
        my $url = "$repo/$urlSuffix";
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
        die "download failed. $dep";
    }
    open(my $fh, ">:raw", $outFile) or die "$! $outFile";
    print $fh $successResponse->content;
    close($fh) or die "$! $outFile";
}


# 依存関係を指定して、POMファイルからざっくりした情報を取得する
# POMファイルがなければダウンロードする

sub pomOutline($){
    my($dep)=@_;

    my $file = "$dirDlSave/" . sanitizeFileName("$dep.pom");

    pomDownload($dep,$file);

    # POMのXMLファイルからgroupId, artifactId, version を読む
    my $xp = XML::XPath->new(filename => $file);

    my $groupId = $xp->findvalue('/project/groupId')->value()
         || $xp->findvalue('/project/parent/groupId')->value()
         || die "missing groupId in $file";

    my $artifactId = $xp->findvalue('/project/artifactId')->value()
         || $xp->findvalue('/project/parent/artifactId')->value()
         || die "missing artifactId in $file";

    my $version = $xp->findvalue('/project/version')->value()
        || $xp->findvalue('/project/parent/version')->value()
        || die "missing version in $file";

    return {
        dep => $dep,
        pomFile => $file,
        # 
        fullName => "$groupId:$artifactId:$version",
        groupAndArtifact => "$groupId:$artifactId",
        # 
        groupId => $groupId,
        artifactId => $artifactId,
        version => $version,
    };
}

# POM概要を出力用データに変換する
sub pomToInfo($$){
    my($errors, $pomInfo) = @_;

    my $id = $pomInfo->{dep};
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
    my $licenses = deepCopy $initialLicenseList;

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

sub dependencyJson{
    # 出力先ごとに処理を行う
    my $outputs = $config->{outputs} or die "config.outputs is missing.";
    @$outputs or die "config.outputs is empty.";
    my $outIndex = 0;
    for my $out (@$outputs){
        # - 出力ファイルごとの処理
        # - ただしGradleキャッシュのスキャンは1回だけ
        my $name = $out->{name} or die "config.outputs[$outIndex].name is missing.";
        $out->{outFile} or die "config.outputs[$name].outFile is missing.";
        $out->{configuration} or die "config.outputs[$name].configuration is missing.";
        prepareDirectory( dirname($out->{outFile}) );

        # - カレントディレクトリで./gradlew :app:dependencies して依存関係を列挙する
        # gradleで依存関係を列挙する
        say "# [$name] list dependencies ...";
        my @deps = listDependencies $out->{configuration};
        $out->{deps} = \@deps;

        # POMをダウンロードして概要情報に変換
        say "# [$name] download pom and read outline...";
        my @pomOutlines = map{ pomOutline($_) } @deps;

        # POMをINFOに変換
        say "# [$name] convert pom to info...";

        my @errors;
        my @libs = map{ pomToInfo(\@errors, $_) } @pomOutlines;
        if(@errors){
            say $_ for @errors;
            exit 1;
        }

        my $size = 0 + @libs;
        say "$size library information parsed.";

        # 追加の依存関係
        my $addItems = deepCopy $config->{additionalLibs};
        @$addItems and push @libs, @$addItems;

        # initialLicenses をディープコピーして見つかったライセンス情報を追加してコンパクト化する
        say "# [$name] compacting licenses ...";
        my $licenses = compactLisences($initialLicenses, \@libs);

        # 情報をJSONファイルに出力
        say "# [$name] save to json $out->{outFile}";
        my $outFile = $out->{outFile};
        open(my $fh,">:raw",$outFile) or die "$outFile $!";
        print $fh $jsonXs->encode(
            {
                libs => \@libs,
                licenses => $licenses,
            }
        );
        close($fh) or die "$outFile $!";

        ++$outIndex;
    }
}

######################################################
# git 関連の処理

# ワーキングツリー中のuntrackまたは変更のあるファイルのリストを返す
sub checkWorkingTree(){
    open(my $fh,"-|","git status --porcelain --branch")
    	or die "can't check git status. $!";
    my @dirtyFiles;
    while(<$fh>){
        chomp;
        if(/^\?\?\s*(\S+)/){
            # ?? {path}
            my($path)=($1);
            next if $path =~ /\.idea|_Emoji|makeVersionTag.pl/;
            push @dirtyFiles, $_;
        }elsif( /^##\s*(\S+?)(?:\.\.|$)/ ){
            # ## {branch}...origin/{branch} [ahead 3]
        }else{
            # M {path} など
            push @dirtyFiles, $_;
        }
    }
    close($fh) or die "can't check git status. $!";
    return @dirtyFiles;
}
sub checkWorkingTreeOrDie(){
    # ワーキングツリーがクリーンではないならビルドしない
    my @changes = checkWorkingTree();
    if( @changes ){
        say "!! Working tree is not clean. please check files:";
        for(@changes){
            say "  $_";
        }
        exit 1;
    }
}
sub checkWorkingTreeOrCommit(){
    my @changes = checkWorkingTree();
    if( @changes ){
        say "# dependency JSON updated. adding commit for it...";
        cmd qq(git commit -a -m 'dependency JSON updated.');
    }
}

# Weblateの未マージのブランチがあるか調べる
sub checkWeblateMerged(){
    system qq(git fetch weblate -q);
    my @list = `git branch -r --no-merged`;
    for(@list){
    	s/[\x0d\x0a]+//;
    	print "# Unmerged branch: $_\n";
    }
    my @weblateBranches = grep{ /weblate/ } @list;
    if( @weblateBranches ){
        die "weblate branches not merged. ",join(' ',@weblateBranches),"\n";
    }
}

sub getAppVersion{
    `cat $buildFile` =~ /versionName\s*=\s*["']([\d\.]+)["']/ 
        or die "missing versionName in $buildFile\n";
    retun $1;
}

sub isExistTag($){
    my($tag)=@_;
    my $result = `git rev-parse -q --verify 'refs/tags/$tag'`;
    return scalar $result =~ /[0-9A-Fa-f]{40}/;
}

sub getBranch{
    my $text = `git rev-parse --abbrev-ref HEAD`;
    $text =~ s/\A\s+//;
    $text =~ s/\s+\z//;
    return $text;
}

sub getNowString{
    my @lt= localtime;
    $lt[4]+=1; $lt[5]+=1900;
    return sprintf("%d%02d%02d_%02d%02d%02d",reverse @lt[0..5]);
}

sub getVersion{
    open(my $fh,"<",$buildFile) or die "$! $buildFile";
    my($code,$name);
    while(<$fh>){
        s/[\x0d\x0a]+//g;
        s|//.*| |;
        if(/versionCode\s*=\s*(\S+)/){
            my $a = $1;
            $a =~ s/[\s"]+//g;
            $code = $a;
        }elsif( /versionName\s*=\s*(\S+)/ ){
            my $a = $1;
            $a =~ s/[\s"]+//g;
            $name = $a;
        }
    }
    close($fh) or die "$! $buildFile";
    $code or die "missing versionCode in $buildFile";
    $name or die "missing versionName in $buildFile";
    return ($code,$name);
}

#####################################################

sub apkGen($){
    my($info)=@_;
    cmd "./gradlew --stop";
    cmd "rm -rf .gradle/caches/build-cache-*";
    cmd "./gradlew clean";
    cmd "./gradlew assembleNoFcmRelease";
    cmd "./gradlew assembleFcmRelease";
    cmd "./gradlew --stop";
    cmd "mkdir -p _apk";
    
    my $branchSanitided = sanitizeFileName($info->{branch});

    for(
        ["fcm", "app/build/outputs/apk/fcm/release/app-fcm-release.apk"],
        ["noFcm", "app/build/outputs/apk/nofcm/release/app-nofcm-release.apk"],
    ){
        my($flavor,$srcPath)=@$_;
        (-f $srcPath) or die "not found: $srcPath";
        my $dstName = "SubwayTooter-$branchSanitided-$flavor-$info->{versionCode}-$info->{versionName}-$info->{date}.apk";
        $dstName =~ s/-{2,}/-/g;
        cmd "mv $srcPath _apk/$dstName";
    }
    cmd "ls -lt _apk/SubwayTooter*.apk |head -n 2";
}

########################################################
# メインフロー

# ビルド設定からアプリのバージョンを読む
my($versionCode,$versionName) = getVersion();
my $branch = getBranch() or die "missing git branch";
my $tag = "v$versionName";
say "# branch=$branch, versionCode=$versionCode, versionName=$versionName";

my $info = {
    branch => $branch,
    versionCode => $versionCode,
    versionName => $versionName,
    date => getNowString(),
};

my $action = shift(@ARGV) // "";
if($action eq "apk"){
    # APKを生成するが、タグ打ちや依存関係の更新は行わない
    checkWorkingTreeOrDie();
    apkGen($info);
}elsif($action eq "depJson"){
    dependencyJson();
}elsif($action eq "tag"){
    $branch eq 'main' or die "branch is not main. [$branch]";
    checkWorkingTreeOrDie();
    checkWeblateMerged();
    dependencyJson();
    checkWorkingTreeOrCommit();
    apkGen($info);
    if( isExistTag($tag) ){
        say "# already tag is exists. [$tag]";
    }else{
        say "# tag $tag is not yet exist. create & push the tag...";
        cmd "git tag -a $tag -m $tag";
        cmd "git push";
        cmd "git push --tags";
    }
}elsif(not $action){
    die "!! missing action. !!\n$usage";
}else{
    die "!! unknown action [$action]. !!\n$usage";
}
