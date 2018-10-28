#!perl --
use strict;
use warnings;
use Image::Magick;

sub resize{
	my($src_file,$dst_file,$resize_w,$resize_h,$scale) = @_;

	my $image = new Image::Magick;

	$image->read($src_file);

	my($src_w,$src_h) = $image->Get(qw( width height));
	if( not ( $src_w and $src_h ) ){
		warn "cant get image bound. $src_file\n";
		return;
	}

	if($resize_w < 0 ){
		# wが-1ならソース画像をxxxhdpi(x4)として扱い、$scaleに応じて縮小する
		$resize_w = $src_w * ( $scale /4 );
		$resize_h = $src_h * ( $scale /4 );
	}else{
		# wかhのどちらかが0なら、適当に補う
		if( not $resize_w ){
			$resize_w = $resize_h * $src_w / $src_h;
		}elsif( not $resize_h ){
			$resize_h = $resize_w * $src_h / $src_w;
		}
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
	if( $w < 0 ){
		for(@scale_list){
			my($dir_suffix,$scale)=@$_;
			my $subdir = "$res_dir/$dir_prefix-$dir_suffix";
			mkdir($subdir,0777);
			unlink "$subdir/$res_name.webp";
			resize( $src, "$subdir/$res_name.png", $w,$h,$scale);
		}
	}else{
		for(@scale_list){
			my($dir_suffix,$scale)=@$_;
			my $subdir = "$res_dir/$dir_prefix-$dir_suffix";
			mkdir($subdir,0777);
			unlink "$subdir/$res_name.webp";
			resize( $src, "$subdir/$res_name.png", $scale * $w, $scale * $h );
		}
	}
}


my $res_dir = "app/src/main/res";
#resize_scales( "_ArtWork/ic_app_logo-512.png"		,$res_dir,"drawable","ic_app_logo",0,32);

#resize_scales( "_ArtWork/ic_launcher-1024.png"		,$res_dir,"mipmap","ic_launcher",0,48);
#resize_scales( "_ArtWork/ic_notification-817.png"	,$res_dir,"drawable","ic_notification",0,24);
#
#resize_scales( "_ArtWork/ic_launcher_20170513.png"		,$res_dir,"mipmap","ic_launcher",0,48);
#resize_scales( "_ArtWork/ic_notification_20150513.png"	,$res_dir,"drawable","ic_notification",0,24);
#
#
#resize_scales( "_ArtWork/ic_hourglass.png"			,$res_dir,"drawable","ic_hourglass",0,32);
#resize_scales( "_ArtWork/ic_hourglass_dark.png"		,$res_dir,"drawable","ic_hourglass_dark",0,32);
#
#
#resize_scales( "_ArtWork/ic_follow_cross.png"		,$res_dir,"drawable","ic_follow_cross",0,32);
#resize_scales( "_ArtWork/ic_follow_cross_dark.png"	,$res_dir,"drawable","ic_follow_cross_dark",0,32);
#resize_scales( "_ArtWork/ic_follow_plus.png"		,$res_dir,"drawable","ic_follow_plus",0,32);
#resize_scales( "_ArtWork/ic_follow_plus_dark.png"	,$res_dir,"drawable","ic_follow_plus_dark",0,32);
#resize_scales( "_ArtWork/ic_followed_by.png"		,$res_dir,"drawable","ic_followed_by",0,32);
#resize_scales( "_ArtWork/ic_followed_by_dark.png"	,$res_dir,"drawable","ic_followed_by_dark",0,32);
#
#
#resize_scales( "_ArtWork/media_type_gifv.png"	,$res_dir,"drawable","media_type_gifv",-1,-1);
#resize_scales( "_ArtWork/media_type_image.png"	,$res_dir,"drawable","media_type_image",-1,-1);
#resize_scales( "_ArtWork/media_type_unknown.png"	,$res_dir,"drawable","media_type_unknown",-1,-1);
#resize_scales( "_ArtWork/media_type_video.png"	,$res_dir,"drawable","media_type_video",-1,-1);

#resize_scales( "_ArtWork/hohoemi.png"	,$res_dir,"drawable","emoji_hohoemi",0,24);
#resize_scales( "_ArtWork/nicoru.png"	,$res_dir,"drawable","emoji_nicoru",0,24);
#resize_scales( "_ArtWork/ic_nicoru.png"			,$res_dir,"drawable","ic_nicoru",0,32);
#resize_scales( "_ArtWork/ic_nicoru_dark.png"		,$res_dir,"drawable","ic_nicoru_dark",0,32);

#resize_scales( "_ArtWork/ic_pin.png"	,$res_dir,"drawable","ic_pin",0,32);
#resize_scales( "_ArtWork/ic_pin_dark.png"	,$res_dir,"drawable","ic_pin_dark",0,32);

#resize_scales( "_ArtWork/ic_follow_wait.png"	,$res_dir,"drawable","ic_follow_wait",0,32);
#resize_scales( "_ArtWork/ic_follow_wait_dark.png"	,$res_dir,"drawable","ic_follow_wait_dark",0,32);

#resize_scales( "_ArtWork/ic_list_list.png"	,$res_dir,"drawable","ic_list_list",0,32);
#resize_scales( "_ArtWork/ic_list_tl.png"	,$res_dir,"drawable","ic_list_tl",0,32);
#resize_scales( "_ArtWork/ic_list_member.png"	,$res_dir,"drawable","ic_list_member",0,32);
#resize_scales( "_ArtWork/ic_list_list_dark.png"	,$res_dir,"drawable","ic_list_list_dark",0,32);
#resize_scales( "_ArtWork/ic_list_tl_dark.png"	,$res_dir,"drawable","ic_list_tl_dark",0,32);
#resize_scales( "_ArtWork/ic_list_member_dark.png"	,$res_dir,"drawable","ic_list_member_dark",0,32);

#resize_scales( "_ArtWork/media_bg_dark.png"	,$res_dir,"drawable","media_bg_dark",0,24);

#resize_scales( "_ArtWork/v0.5.1/ic_launcher_foreground.png"		,$res_dir,"mipmap","ic_launcher_foreground",0,108);
#resize_scales( "_ArtWork/v0.5.1/ic_launcher_background.png"		,$res_dir,"mipmap","ic_launcher_background",0,108);

# resize_scales( "_ArtWork/ic_pulse.png"	,$res_dir,"drawable","ic_pulse",0,32);
#resize_scales( "_ArtWork/ic_bot.png"	,$res_dir,"drawable","ic_bot",0,24);
#resize_scales( "_ArtWork/ic_pin.png"	,$res_dir,"drawable","ic_pin",0,24);

#resize_scales( "_ArtWork/ic_cat.png"	,$res_dir,"drawable","ic_cat",0,24);
#resize_scales( "_ArtWork/ic_shield.png"	,$res_dir,"drawable","ic_shield",0,24);
#resize_scales( "_ArtWork/ic_mobile.png"	,$res_dir,"drawable","ic_mobile",0,24);


#resize_scales( "_ArtWork/misskey-reaction/btn_reaction_angry.png"			,$res_dir,"drawable","btn_reaction_angry",0,32);
#resize_scales( "_ArtWork/misskey-reaction/btn_reaction_confused.png"			,$res_dir,"drawable","btn_reaction_confused",0,32);
#resize_scales( "_ArtWork/misskey-reaction/btn_reaction_congrats.png"			,$res_dir,"drawable","btn_reaction_congrats",0,32);
#resize_scales( "_ArtWork/misskey-reaction/btn_reaction_hmm.png"			,$res_dir,"drawable","btn_reaction_hmm",0,32);
#resize_scales( "_ArtWork/misskey-reaction/btn_reaction_laugh.png"			,$res_dir,"drawable","btn_reaction_laugh",0,32);
#resize_scales( "_ArtWork/misskey-reaction/btn_reaction_like.png"			,$res_dir,"drawable","btn_reaction_like",0,32);
#resize_scales( "_ArtWork/misskey-reaction/btn_reaction_love.png"			,$res_dir,"drawable","btn_reaction_love",0,32);
#resize_scales( "_ArtWork/misskey-reaction/btn_reaction_pudding.png"			,$res_dir,"drawable","btn_reaction_pudding",0,32);
#resize_scales( "_ArtWork/misskey-reaction/btn_reaction_rip.png"			,$res_dir,"drawable","btn_reaction_rip",0,32);
#resize_scales( "_ArtWork/misskey-reaction/btn_reaction_surprise.png"			,$res_dir,"drawable","btn_reaction_surprise",0,32);
#
# resize_scales( "_ArtWork/ic_medal.png"	,$res_dir,"drawable","ic_authorized",0,24);


resize_scales( "../extra-SubwayTooter/_ArtWork/ic_unread.png"	,$res_dir,"drawable","ic_unread",0,24);

