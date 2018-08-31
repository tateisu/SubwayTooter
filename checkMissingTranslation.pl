#!/usr/bin/perl --
use XML::Parser;
use strict;
use warnings;
use File::Find;

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
	my %names;
	my $parser = XML::Parser->new(Handlers => {
			Start => sub{
				my($expat,$element)=@_;
				if( $element eq "string" ){
					my $ie=0+@_;
					for(my $i=2;$i<$ie;$i+=2){
						my $k=$_[$i];
						my $v=$_[$i+1];
						if( $k eq "name" ){
							$names{$v}=1;
						}
					}
				}
			}
			,End   => sub{}
			,Char  => sub{}
	});
	$parser->parsefile($file);
	$langs{ $lang } = \%names;
}

my $master = $langs{ $master_name };
$master or die "missing master languages.\n";

my %missing;
my %allNames;
for my $lang ( sort keys %langs ){
	my $names = $langs{$lang};
	while(my($name,$value)=each %$names){
		$allNames{$name}=1;
		if(not $master->{$name} ){
			$missing{$name} =1;
		}
	}
	my $nameCount = 0+ keys %$names;
	print "($lang)string resource count=$nameCount\n";
}

my @missing = sort keys %missing;
@missing and die "missing string resources in master language: ",join(', ',@missing),"\n";

my $nameCount = 0+ keys %allNames;
print "(total)string resource count=$nameCount\n";
exit 0;
