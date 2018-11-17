#!perl --
use strict;
use warnings;
use feature qw( say );
use File::Find;
use File::Path qw(make_path remove_tree);
use GD;

my $resdir = 'app/src/main/res';
my $dstdir = 'tmp';
my $tmpfile = 'tmp/tmp.png';

sub fix($){
	my($relpath) = @_;
	
	my $dstfile = "$dstdir/$relpath";
	return if not $dstfile =~ s/(png|webp)\z/png/i;
	my $ext = lc $1;

	my $srcfile = $relpath;
	if( $ext eq "webp" ){
		system qq(dwebp $relpath -quiet -o $tmpfile);
		$srcfile = $tmpfile;
	}

	my $image = GD::Image->newFromPng($srcfile,1);

	
	my $width = $image->width;
	my $height = $image->height;
	my $alphaMax = 0;
	for( my $y = 0; $y < $height ;++$y){
		for( my $x = 0; $x < $width ;++$x){
			my $c = $image->getPixel($x,$y);
			my $c0 = ($c >> 24)&255; # GDのalphaは0が不透明、127が完全透明
			my $alpha= 127-$c0;
			if( $alpha > $alphaMax){
				$alphaMax = $alpha;
			}
		}
	}

	return if $alphaMax == 127;

	say "$width x $height ,alphaMax=$alphaMax $relpath";


	my $scale = 127.0/$alphaMax;

	for( my $y = 0; $y < $height ;++$y){
		for( my $x = 0; $x < $width ;++$x){
			my $c = $image->getPixel($x,$y);
			my $c0 = ($c >> 24)&255; # GDのalphaは0が不透明、127が完全透明
			next if $c0 == 127;
			my $alpha= 127-$c0;
			$c0 = 127 - int( 0.5 + $alpha * $scale );
			$c = ($c & 0xffffff ) | ($c0 << 24 );
			$image->setPixel($x,$y,$c);
		}
	}
	
	my $dstdir = $dstfile;
	$dstdir =~ s|/[^/]+\z||;
	make_path($dstdir);

	$image->saveAlpha(1);
	open(my $fh,">",$dstfile) or die "$dstfile $!";
	binmode $fh;
	print $fh $image->png;
	close($fh) or die "$dstfile $!";

	my $relpath_webp = $relpath;
	$relpath_webp =~ s/(png|webp)\z/webp/i;
	if( "png" eq lc $1 ){
		unlink $relpath;
	}
	system qq(cwebp $dstfile -quiet -o $relpath_webp);
}


my @files;
find(
	{
		no_chdir => 1
		,wanted =>sub{
			my $relpath = $File::Find::name;
			return if -d $relpath;
			return if not $relpath =~ m|/drawable-|;
			return if $relpath =~ m|/media_type_|;
			push @files,$relpath;
		}
	}
	,$resdir
);

for my $relpath(@files){
	fix($relpath);
}


