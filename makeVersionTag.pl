#!/usr/bin/perl --
use strict;
use warnings;

sub cmd($){
	print "+ ",$_[0],"\n";
	my $rv=system $_[0];
	if ($? == -1) {
        die "failed to execute: $!\n";
    }elsif ($? & 127) {
        die sprintf "child died with signal %d\n", ($? & 127);
    }elsif($?){
        $rv = $? >> 8;
        die "child exited with value $rv\n";
    }
}


# ワーキングツリーに変更がないことを確認
open(my $fh,"-|","git status --porcelain --branch")
	or die "can't check git status. $!";

my @untrackedFiles;
while(<$fh>){
	chomp;
	if(/^\?\?\s*(\S+)/){
		my $path =$1;
		next if $path =~ /\.idea|_Emoji|makeVersionTag.pl/;
		push @untrackedFiles,$path
	}elsif( /^##\s*(\S+?)(?:\.\.|$)/ ){
		my $branch=$1;
		print "# branch=$branch\n";
		$branch eq 'main'
			or die "current branch is not main.\n";
	}else{
		warn "working tree is not clean.\n";
		cmd "git status";
		exit 1;
	}
}

close($fh)
	or die "can't check git status. $!";

@untrackedFiles and die "forgot git add?\n",map{ "- $_\n"} @untrackedFiles;

# 現在のバージョン番号を取得
my $buildFile = 'app/build.gradle.kts';
`cat $buildFile` =~ /versionName\s*=\s*["']([\d\.]+)["']/ 
	or die "missing versionName in $buildFile\n";
my($tag)="v$1";
print "# version=$tag\n";

# すでにタグがあるなら何もしない
if( `git tag -l $tag` =~ /$tag/ ){
	print "# tag $tag is already exists.\n";
}else{
	cmd "git tag -a $tag -m $tag";
}

cmd "git push";
cmd "git push --tags";
