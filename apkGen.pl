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


sub getBranch{
    my $text = `git rev-parse --abbrev-ref HEAD`;
    $text =~ s/\A\s+//;
    $text =~ s/\s+\z//;
    return $text;
}

sub getDate{
    my @lt= localtime;
    $lt[4]+=1;$lt[5]+=1900;
    return sprintf("%d%02d%02d_%02d%02d%02d",reverse @lt[0..5]);
}

sub getVersion{
    my $appBuildGradle = "app/build.gradle.kts";
    my($code,$name);
    open(my $fh,"<",$appBuildGradle) or die "$! $appBuildGradle";
    while(<$fh>){
        s/[\x0d\x0a]+//g;
        s|//.*| |;
        if(/versionCode\s*=\s*(\S+)/){
            my $a = $1;
            $a =~ s/[\s"]+//g;
            $code = $a;
        }elsif( /versionName\s*=\s*(\S+)/ ){
            my $a = $1;
            $a =~ s/[\s"]+//g;
            $name = $a;
        }
    }
    close($fh) or die "$! $appBuildGradle";
    $code or die "missing versionCode in $appBuildGradle";
    $name or die "missing versionCode in $appBuildGradle";
    return ($code,$name);
}

my ($versionCode,$versionName) = getVersion();
my $branch = getBranch() or die "missing git branch";
my $date = getDate() or die "missing date";

cmd "mkdir -p _apk";

for(
    ["fcm","app/build/outputs/apk/fcm/release/app-fcm-release.apk"],
    ["noFcm","app/build/outputs/apk/nofcm/release/app-nofcm-release.apk"],
){
    my($flavor,$srcPath)=@$_;
    (-f $srcPath) or die "not found: $srcPath";

    my $dstName= "SubwayTooter-$branch-$flavor-$versionCode-$versionName-$date.apk";

    cmd "mv $srcPath _apk/$dstName";
}

cmd "ls -lt _apk/SubwayTooter*.apk |head -n 2";
