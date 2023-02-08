#!/usr/bin/perl --
use strict;
use warnings;

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

cmd "./gradlew --stop";
cmd "./gradlew clean";
cmd "./gradlew assembleNoFcmRelease";
cmd "./gradlew assembleFcmRelease";
cmd "./gradlew --stop";
cmd "mv app/build/outputs/apk/SubwayTooter*.apk app/";
cmd " ls -1t app/SubwayTooter*.apk |head -n 5";
