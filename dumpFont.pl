#!perl --
use strict;
use warnings;
use Font::FreeType;

my $face = Font::FreeType->new->face('emojione_android.ttf');

my $f =0;
my $l =0;
my $n =0;
$face->foreach_char(sub{
	my $codepoint = $_->char_code;

	if( $codepoint < 80 ){
		return;
	}
	

	if($n ==0 ){
		if( $l == 0 ){
			print "\tprivate static void initForFont",(++$f),"(){\n";
		}
		print "\t\taddFontCode(new int[]{";
	}

	printf "0x%x,",$codepoint;

	if( ++$n >= 5 ){
		$n =0;
		print "});\n";

		if( ++$l >= 100 ){
			$l = 0;
			print "\t}\n";
		}
	}
});

if( $n > 0 ){
	print "});\n";
	print "\t}\n";
}
print "\tstatic{\n";
for(my $i=1;$i<=$f;++$i){
	print"\t\tinitForFont$i();\n";
}
print "\t}\n";
