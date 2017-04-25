#!perl --
use strict;
use warnings;
use Image::Magick;

sub resize{
	my($src_file,$dst_file,$resize_w,$resize_h) = @_;

	my $image = new Image::Magick;

	$image->read($src_file);

	my($src_w,$src_h) = $image->Get(qw( width height));

	if( not $resize_w ){
		$resize_w = $resize_h * $src_w / $src_h;
	}elsif( not $resize_h ){
		$resize_h = $resize_w * $src_h / $src_w;
	}

	$image -> Resize(
		width  => $resize_w,
		height => $resize_h,
		blur   => 0.8,
	);
	$image -> Write( "png:$dst_file" );
	print "$dst_file\n";
}

my @scale_list = (
	[qw( mdpi 1 )],
	[qw( hdpi 1.5 )],
	[qw( xhdpi 2 )],
	[qw( xxhdpi 3 )],
	[qw( xxxhdpi 4 )],
);

sub resize_scales{
	my($src,$res_dir,$dir_prefix,$res_name,$w,$h)=@_;
	for(@scale_list){
		my($dir_suffix,$scale)=@$_;
		my $subdir = "$res_dir/$dir_prefix-$dir_suffix";
		mkdir($subdir,0777);
		resize( $src, "$subdir/$res_name.png", $scale * $w, $scale * $h );
	}
}


my $res_dir = "app/src/main/res";
resize_scales( "ic_launcher-1024.png",$res_dir,"mipmap","ic_launcher",0,48);
#resize_scales( "ic_app_logo-512.png",$res_dir,"drawable","ic_app_logo",0,32);
resize_scales( "ic_notification-817.png",$res_dir,"drawable","ic_notification",0,24);
