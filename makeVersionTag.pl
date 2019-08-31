#!/usr/bin/perl --
use strict;
use warnings;

my $buildFile = 'app/build.gradle';

# ワーキングツリーに変更がないことを確認
open(my $fh,"-|","git status --porcelain --branch")
	or die "can't check git status. $!";

my @untrackedFiles;
while(<$fh>){
	chomp;
	if(/^\?\?\s*(\S+)/){
		my $path =$1;
		next if $path =~ /\.idea|_Emoji/;
		push @untrackedFiles,$path
	}elsif( /^##\s*(\S+?)(?:\.\.|$)/ ){
		my $branch=$1;
		print "branch=$branch\n";
		$branch eq 'master'
			or die "current branch is not master.\n";
	}else{
		die "working tree is not clean.\n";
	}
}

close($fh)
	or die "can't check git status. $!";

@untrackedFiles and die "forgot git add?\n",map{ "- $_\n"} @untrackedFiles;

# 現在のバージョン番号を取得
my $output = `cat $buildFile`;

$output =~ /versionName\s+["']([\d\.]+)["']/
	or die "missing versionName in $buildFile\n";
my($version)=($1);
print "version=$version\n";

# 
$output = `git log --oneline --no-merges --grep v$version`;
print $output,"\n";
