#!perl --
use strict;
use warnings;
use feature qw( say );
use File::Find;
use File::Path qw(make_path remove_tree);
use GD;

my $dry_run = grep{ $_ eq '--dry-run'} @ARGV;

my $resdir = 'app/src/main/res';
my $dstdir = 'tmp';
my $tmpfile = 'tmp/tmp.png';

sub fix($){
	my($relpath) = @_;
	
	my $dstfile = "$dstdir/$relpath";
	return if not $dstfile =~ s/(png|webp)\z/png/i;
	my $ext = lc $1;

	my $inData;
	if( $ext eq "webp" ){
		open(my $fh,"dwebp $relpath -quiet -o -|") or die "dwebp : $!";
		binmode($fh);
		local $/ = undef;
		$inData = <$fh>;
		close($fh) or die  "dwebp: $!";
	}else{
		open(my $fh,"<",$relpath) or die "$relpath : $!";
		binmode($fh);
		local $/ = undef;
		$inData = <$fh>;
		close($fh) or die "$relpath : $!";
	}

	my $image = GD::Image->newFromPngData($inData,1);

	my $width = $image->width;
	my $height = $image->height;
	my $alphaMax = 0;
	my $isColor = 0 ;
Loop: for( my $y = 0; $y < $height ;++$y){
		for( my $x = 0; $x < $width ;++$x){
			my $c = $image->getPixel($x,$y);
			my $c0 = ($c >> 24)&255; # GDのalphaは0が不透明、127が完全透明
			my $r = ($c >> 16)&255; 
			my $g = ($c >> 8)&255; 
			my $b = ($c >> 0)&255; 
			if( abs($r-$g) >= 2 or abs($r-$b) >= 2 or abs($g-$b) >= 2 ){
				$isColor = 1;
				last Loop;
			}
			my $alpha= 127-$c0;
			if( $alpha > $alphaMax){
				$alphaMax = $alpha;
			}
		}
	}
	
	return if $isColor;
	return if $alphaMax == 0;
	return if $alphaMax >= 126;

	say "$width x $height ,alphaMax=$alphaMax $relpath";

	my $scale = 127.0/$alphaMax;

	for( my $y = 0; $y < $height ;++$y){
		for( my $x = 0; $x < $width ;++$x){
			my $c = $image->getPixel($x,$y);
			my $c0 = ($c >> 24)&255; # GDのalphaは0が不透明、127が完全透明
			my $r = ($c >> 16)&255; 
			my $g = ($c >> 8)&255; 
			my $b = ($c >> 0)&255; 
			next if $c0 == 127;
			my $alpha= 127-$c0;
			$c0 = 127 - int( 0.5 + $alpha * $scale );
			my($rgb) = ($r+$g+$b >= 555 ? 0xffffff : 0x000000 );
			$c = $rgb | ($c0 << 24 );
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

	return if $dry_run;

	if(1){
		my $relpath_webp = $relpath;
		$relpath_webp =~ s/(png|webp)\z/webp/i;
		if( "png" eq lc $1 ){
			unlink $relpath;
		}
		system qq(cwebp $dstfile -quiet -o $relpath_webp);
	}
}


my @files;
find(
	{
		no_chdir => 1
		,wanted =>sub{
			my $relpath = $File::Find::name;
			return if -d $relpath;
			return if not $relpath =~ m|/drawable-|;
			return if $relpath =~ m%/(media_type_|emoji_|media_bg_dark)%;
			push @files,$relpath;
		}
	}
	,$resdir
);

for my $relpath(@files){
	fix($relpath);
}


