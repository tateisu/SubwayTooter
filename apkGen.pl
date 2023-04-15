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
cmd "rm -rf .gradle/caches/build-cache-*";
cmd "./gradlew clean";
cmd "./gradlew assembleNoFcmRelease";
cmd "./gradlew assembleFcmRelease";
cmd "./gradlew --stop";

cmd "mkdir -p _apk";
cmd "mv `find app/build/outputs/apk/ -path '*.apk'` _apk/";
cmd "ls -1t _apk/SubwayTooter*.apk |head -n 5";
