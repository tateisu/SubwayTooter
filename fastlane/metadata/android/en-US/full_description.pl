#!/usr/bin/perl --
use strict;
use warnings;
use utf8;
use feature qw(say);

# read from __DATA__ section in this file.
local $/ = undef;
my $text = <DATA>;

############################################
# change spaces and line feeds to single space,
# it's required for F-droid android client.
# also remove head/tail spaces in whole text.

$text =~ s/[\x00-\x20]+/ /g;
$text =~ s/\A //;
$text =~ s/ \z//;

############################################
# trim spaces before/after open/close block tags. also <br>,<br/>,</br>

# HTML block elements and "br". joined with '|'
my $blockElements = join "|", qw(
    address article aside blockquote canvas dd div dl dt 
    fieldset figcaption figure footer form 
    h1 h2 h3 h4 h5 h6 header hr li 
    main nav noscript ol p pre section table tfoot ul video 
    br
);

# RegEx for block tag that may have attributes, and spaces before/after tag.
my $trimElementRe = qr!\s*(</?(?:$blockElements)\b(?:[^>/"]+|"[^"]*")*/?>)\s*!i;

## verbose debugging.
#say $trimElementsRe;
#while( $text =~ /$trimElementRe/g){
#    next if $& eq $1;
#    say "[$&] => [$1]";
#}

$text =~ s/$trimElementRe/$1/g;

############################################

# write to .txt file. $0 means path of the this script file.
my $file = $0;
$file =~ s/\.pl$/\.txt/ or die "can't make output filename. $0";
open(my $fh,">:utf8",$file) or die "$file $!";
say $fh $text;
close($fh) or die "$file $!";

# apt-cyg install tidy libtidy5
system qq(tidy -q -e $file);

__DATA__

<p>Mastodon client for Android 8.0 or later.</p>

<p>Also this app has partially support for Misskey. But it does not include function to use message, drive, reversi, widget.</p>

<p><b>Multiple accounts, Multiple columns</b></p>
<ul>
<li>You can swipe horizontally to switch columns and accounts.</li>
<li>You can add, remove, rearrange columns.</li>
<li>Column types: home, notification, local-TL, federate-TL, search, hash tags, conversation, profile, muted, blocked, follow requests, etc.</li>
</ul>

<p><b>Cross account action</b></p>
<ul>
<li>You can favorite/follow operation as a user different from bind to column.</li>
</ul>

<p><b>Other information</b></p>
<ul>
<li>source code is here. <a href="https://github.com/tateisu/SubwayTooter">https://github.com/tateisu/SubwayTooter</a></li>
<li>Some of the icons used in this app is based on the Icons8. <a href="https://icons8.com/license/">https://icons8.com/license/</a></li>
</ul>
