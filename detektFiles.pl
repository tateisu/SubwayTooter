#!/usr/bin/perl --
use 5.32.0;
use strict;
use warnings;

my $inFile = "app/build/reports/detekt/st-detektAll.txt";

my $rv = system qq(./gradlew :app:detektAll);
$rv and die "gradle failed.";

my @lines;

open(my $fh, "<:encoding(UTF-8)",$inFile) or die "$! $inFile";
while(<$fh>){
    s/[\x0d\x0a]+//;
    next if not length;

    # TopLevelPropertyNaming - [wild] at Z:\mastodon-related\SubwayTooter\app\src\main\java\jp\juggler\subwaytooter\util\MimeTypeUtils.kt:118:19 - Signature=MimeTypeUtils.kt$private const val wild = '?'.code.toByte()
    if(not /\A(.+) at (\S+?:\d+:\d+) - (.+)/){
        say "?? $_";
        next;
    }
    my($name,$pos,$other)=($1,$2,$3);
    $pos =~ s|\A.+\\SubwayTooter\\||;
    $pos =~ s|\\|/|g;

    push @lines, "$pos $name $other";
}
say $_ for sort @lines;
@lines or say "no problems found.";
