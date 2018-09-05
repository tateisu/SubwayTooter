#!/usr/bin/perl --
use XML::Parser;
use strict;
use warnings;
use File::Find;
use XML::Simple;
use Data::Dump qw(dump);

my $xml = XML::Simple->new;

my $master_name = "_master";

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
		$lang=$master_name;
	}
	my $data = $xml->XMLin($file);
	#print dump($data);
	#exit;
	
	my %names;
	while(my($name,$o)=each %{$data->{string}}){
		$names{$name}=$o->{content};
	}
	$langs{ $lang } = \%names;
}

my $hasError = 0;

my $master = $langs{ $master_name };
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
		if( $sv =~ /%/ && not $sv=~/:%/ ){
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
my @list = `git branch -a --no-merged`;
for(@list){
	print "# Unmerged branch: $_\n";
}

exit 0;
