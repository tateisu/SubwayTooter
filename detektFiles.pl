#!/usr/bin/perl --
use 5.32.0;
use strict;
use warnings;

my $inFile = "app/build/reports/detekt/st-detektAll.txt";

my $rv = system qq(./gradlew :app:detektAll);
$rv == 256 and $rv =0;
$rv and die "gradle failed.";

say "#############################################";
say "# reading $inFile ...";

my @lines;
open(my $fh, "<:encoding(UTF-8)",$inFile) or die "$! $inFile";
while(<$fh>){
    s/[\x0d\x0a]+//;
    next if not length;
    s|\\|/|g;
    
    # SpacingAroundCurly - [<anonymous>] at Z:\mastodon-related\SubwayTooter\app\src\main\java\jp\juggler\subwaytooter\itemviewholder\ItemViewHolderShowStatus.kt:359:21 - Signature=ItemViewHolderShowStatus.kt${
    if( m|\A(\S+) - \[([^]]+)\] at (\S+)|){
        my($name,$obj,$pos)=($1,$2,$3);
        $pos =~ s|\A.*?/SubwayTooter/||;
        push @lines,"$pos $name [$obj]";
        next;
    }
    die "parse error: ?? $_";
}

say "#############################################";
say "# result:";

@lines or say "no problems found.";
say $_ for sort @lines;
