#!perl --
use strict;
use warnings;
use utf8;
use LWP::Simple;
use JSON; 
use Data::Dump qw(dump);
use Encode;

# perl convert-emoji-codes.pl < eac.json  >converted.txt

# eac.json is here 
# https://github.com/Ranks/emojione/blob/master/extras/alpha-codes/eac.json 

my $data;
{
	local $/ = undef;
	$data = <STDIN>;
}

my $eac_map = decode_json $data;
undef $data;

my @list;
while( my($k,$v)=each %$eac_map){

	{
		my $t = $v->{"alpha code"};
		my @a = ($t =~ /:([^\s:]+):/g );
		for(@a){
			push @list,[$k, $_ ];
		}
	}
	{
		my $t = $v->{"aliases"};
		my @a = ($t =~ /:([^\s:]+):/g );
		for(@a){
			push @list,[$k, $_ ];
		}
	}
}

my %unicode_map;
sub putUnicodeMap{
	my($map,$char,@remain)=@_;
	$map->{$char} or $map->{$char} = {};
	if(not @remain){
		$map->{$char}->{e}=1;
	}else{
		putUnicodeMap( $map->{$char} ,@remain );
	}
}

my $func_num = 0;
my $n = 0;
my $codepoint_max = 0;
my $length_max = 0;
sub addCode{
	my($k,$name)=@_;
	if( $n == 0 ){
		++$func_num;
		print "\tprivate static void init$func_num(){\n";
	}
	my @chars = split /-/,$k;
	for(@chars){
		my $codepoint = hex($_); 
		if( $codepoint > $codepoint_max ){
			$codepoint_max = $codepoint;
		}
	}
	my $char_count = 0+@chars;
	if( $char_count > $length_max ){
		$length_max = $char_count;
	}

	my $char_java = join(',',map{ "0x$_"} @chars );
	print qq|\t\t_addEntry("$name", new String(new int[] {$char_java}, 0, $char_count));\n|;
	if( ++$n > 100 ){
		print "\t}\n";
		$n = 0;
	}
}

for(sort {$a->[1] cmp $b->[1]} @list){
	addCode( @$_ );
}
if( $n > 0 ){
	print "\t}\n";
}
print "\tstatic{\n";
for(my $i=1;$i <= $func_num;++$i){
	print "\t\tinit$i();\n";
}
print "\t}\n";

printf "//codepoint_max=0x%x, length_max=$length_max\n",$codepoint_max;
