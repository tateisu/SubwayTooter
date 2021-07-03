#!/usr/bin/perl --
use XML::Parser;
use strict;
use warnings;
use File::Find;
use XML::Simple;
use Data::Dump qw(dump);
use utf8;

binmode $_ for \*STDOUT,\*STDERR;

my $preCommit = grep{ $_ eq '--pre-commit'} @ARGV;

sub cmd($){
	print "+ ",$_[0],"\n";
	my $rv=system $_[0];
	if ($? == -1) {
        die "failed to execute: $!\n";
    }elsif ($? & 127) {
        die "child died with signal %d, %s coredump\n", ($? & 127), ($? & 128) ? 'with' : 'without';
    }else {
		my $rv = $? >> 8;
		$rv and die "child exited with value $rv\n";
    }
}


# ワーキングツリーに変更がないことを確認
open(my $fh,"-|","git status --porcelain --branch")
	or die "can't check git status. $!";

my @untrackedFiles;
while(<$fh>){
	s/[\x0d\x0a]+//;
	if(/^\?\?\s*(\S+)/){
		my $path =$1;
		next if $path =~ /\.idea|_Emoji/;
		push @untrackedFiles,$path
	}elsif( /^##\s*(\S+?)(?:\.\.|$)/ ){
		my $branch=$1;
		print "# branch=$branch\n";
		if($preCommit){
			# mainブランチに直接コミットすることはなくなった
			$branch eq 'main' and warn "!!!! current branch is main. Direct commits and pushes are prohibited. !!!!\n";
		}
#	}else{
#		warn "working tree is not clean.\n";
#		cmd "git status";
#		exit 1;
	}
}

close($fh)
	or die "can't check git status. $!";

@untrackedFiles and die "forgot git add?\n",map{ "- $_\n"} @untrackedFiles;





my $xml = XML::Simple->new;

my $default_name = "_default";

my @files;

find(sub{
	return if not -f $_;
	($_ eq "strings.xml") and push @files,$File::Find::name;
},"app/src/main/res/");

@files or die "missing string files.\n";

my %langs;
for my $file(@files){
	my $lang;
	if( $file =~ m|values-([^/]+)| ){
		$lang = $1;
	}else{
		$lang=$default_name;
	}

	my $data = $xml->XMLin($file);
	if( not $data->{string} or ($data->{string}{content} and not ref $data->{string}{content} )){
		warn "?? please make at least 2 string entries in $file\n";
		next;
	}

	my %names;
	while(my($name,$o)=each %{$data->{string}}){
		if(not $o->{content}){
			warn "$lang : $name : missing content in ",dump($o),"\n";
		}else{
			$names{$name}= $o->{content};
		}
	}
	$langs{ $lang } = \%names;
}

my $hasError = 0;

my $master = $langs{ $default_name };
$master or die "missing master languages.\n";
my %params;
while(my($name,$value)=each %$master){
	my @params = $value =~ /(%\d+\$[\d\.]*[sdxf])/g;
	$params{$name} = join ',', sort @params;
}

my %missing;
my %allNames;
for my $lang ( sort keys %langs ){
	my $names = $langs{$lang};
	while(my($name,$value)=each %$names){

		$allNames{$name}=1;
		if(not $master->{$name} ){
			$missing{$name} =1;
		}

		my @params = $value =~ /(%\d+\$[\d\.]*[sdxf])/g;
		my $params = join ',', sort @params;
		my $master_params = $params{$name} // '';
		if( $params ne $master_params){
			$hasError =1;
			print "!! ($lang)$name : parameter mismatch. master=$master_params, found=$params\n";
		}

		# 残りの部分に%が登場したらエラー
		my $sv = $value;
		$sv =~ s/(%\d+\$[\d\.]*[sdxf])//g;
		# Unit:%. を除外したい
		$sv =~ s/%[\s.。]//g;
		if( $sv =~ /%/  ){
			$hasError =1;
			print "!! ($lang)$name : broken param: $sv // $value\n";
		}

		# エスケープされていないシングルクォートがあればエラー
		if( $value =~ m/(?<!\\)['"]/ ){
			print "!! ($lang)$name : containg single or double quote without escape.\n";
		}
	}
	my $nameCount = 0+ keys %$names;
	print "($lang)string resource count=$nameCount\n";
}

my @missing = sort keys %missing;
@missing and die "missing string resources in master language: ",join(', ',@missing),"\n";

my $nameCount = 0+ keys %allNames;
print "(total)string resource count=$nameCount\n";

$hasError and die "please fix error(s).\n";

# Weblateの未マージのブランチがあるか調べる
system qq(git fetch weblate -q);
my @list = `git branch -r --no-merged`;
for(@list){
	s/[\x0d\x0a]+//;
	print "# Unmerged branch: $_\n";
}

exit 0;
