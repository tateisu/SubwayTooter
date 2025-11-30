#!/usr/bin/perl --
use v5.32.0;
use strict;
use warnings;
use Data::Dump qw(dump);

sub parentCount{
    my($node) =@_;
    my $c = 0;
    while( $node->{parent} ){
        ++$c;
        $node = $node->{parent}
    }
    $c;
}


my @configs;
my @stack;

open(my $fh, "-|","./gradlew :app:dependencies 2>&1") or die $!;
while(<$fh>){
	s/[\s\x0d\x0a]+\z//;
	next if not length;
	if( /\A(\S*?Classpath\S*)/ ){
        my $node = {
            name => $1,
            deps => [],
        };
        push @configs,$node;
        @stack = ($node);
		next;
	}elsif( s/\A([\s\|\-\+\\]+)//){
        my $prefixLength = length($1);
        my $name = $_;
        next if not $name;
        next if $prefixLength < 5;

        splice @stack, $prefixLength/5;
        my $parent = $stack[$#stack];

        my $node = {
            name => $name,
            deps => [],
            parent => $parent,
        };
        push @stack, $node;
        push @{$parent->{deps}}, $node;

#        if( $node->{name} =~ /firebase/ ){
#            my $parentCount = parentCount($node);
#            say "$prefixLength $parentCount $node->{name} : parent=$parent->{name}";
#        }
        if( $node->{name} =~ /firebase/ and not $node->{name} =~ /firebase-annotations/ ){
            while($node){
                $node->{mark} = 1;
                $node = $node->{parent};
            }
        }
	}
}
close($fh) or die $!;

sub showMarked{
    my($node,$indent)=(@_,"");
    return if not $node->{mark};
    say "$indent$node->{name}";
    $indent .= "> ";
    for(@{$node->{deps}}){
        showMarked($_,$indent);
    }
}

for(@configs){
    if( $_->{name} =~ /RuntimeClasspath/
    and not $_->{name} =~ /Test|Debug/
    ){
        showMarked($_);
    }
}
