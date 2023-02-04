#!/usr/bin/perl --
use strict;
use warnings;
use feature qw(say);
use Data::Dump qw(dump);

my $text ;
my $start;
my $end;


my $expected;
my $found;
my $target;
while(<DATA>){
	s/\A\s+//;
	s/\s+\z//;
	if( not length){
		next;
	if($_ eq "Expected:"){
		$target = \$expected;
	}elsif($_ eq "Found:"){
		$target = \$found;
	}else{
		$$target = $_;
		last if $expected and $found;
	}
}
$found or die "missing 'Found' schema.";
$expected or die "missing 'Found' schema.";

sub parse{
    my($line)=@_;
    my @result;
    while( $line =~ s/([\w_]+=Column{[^}]+}),? ?//){
        push @result,$1;
    }
    while( $line =~ s/(Index{[^}]+}),? ?//){
        push @result,$1;
    }
    push @result,$line;
    return [sort @result];
}

my $f = parse($found);
my $e = parse($expected);

my 




TableInfo{name='acct_color', columns={
nick=Column{name='nick', type='TEXT', affinity='2', notNull=false, primaryKeyPosition=0, defaultValue='undefined'}, 
time_save=Column{name='time_save', type='INTEGER', affinity='3', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}, 
ac=Column{name='ac', type='TEXT', affinity='2', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}, 
cf=Column{name='cf', type='INTEGER', affinity='3', notNull=false, primaryKeyPosition=0, defaultValue='undefined'}, 
notification_sound=Column{name='notification_sound', type='TEXT', affinity='2', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}, 
_id=Column{name='_id', type='INTEGER', affinity='3', notNull=true, primaryKeyPosition=1, defaultValue='undefined'}, 
cb=Column{name='cb', type='INTEGER', affinity='3', notNull=false, primaryKeyPosition=0, defaultValue='undefined'}
}, foreignKeys=[], indices=[
Index{name='acct_color_time', unique=false, columns=[time_save], orders=[ASC]'}, 
Index{name='acct_color_acct', unique=true, columns=[ac], orders=[ASC]'}
]}
Found:
TableInfo{name='acct_color', columns={_id=Column{name='_id', type='INTEGER', affinity='3', notNull=false, primaryKeyPosition=1, defaultValue='undefined'}, ac=Column{name='ac', type='text', affinity='2', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}, cb=Column{name='cb', type='integer', affinity='3', notNull=false, primaryKeyPosition=0, defaultValue='undefined'}, cf=Column{name='cf', type='integer', affinity='3', notNull=false, primaryKeyPosition=0, defaultValue='undefined'}, nick=Column{name='nick', type='text', affinity='2', notNull=false, primaryKeyPosition=0, defaultValue='undefined'}, notification_sound=Column{name='notification_sound', type='text', affinity='2', notNull=false, primaryKeyPosition=0, defaultValue=''''}, time_save=Column{name='time_save', type='integer', affinity='3', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}}, foreignKeys=[], indices=[Index{name='acct_color_time', unique=false, columns=[time_save], orders=[ASC]'}, Index{name='acct_color_acct', unique=true, columns=[ac], orders=[ASC]'}]}




__END__
Expected:
TableInfo{name='acct_color', columns={nick=Column{name='nick', type='TEXT', affinity='2', notNull=false, primaryKeyPosition=0, defaultValue='undefined'}, time_save=Column{name='time_save', type='INTEGER', affinity='3', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}, ac=Column{name='ac', type='TEXT', affinity='2', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}, cf=Column{name='cf', type='INTEGER', affinity='3', notNull=false, primaryKeyPosition=0, defaultValue='undefined'}, notification_sound=Column{name='notification_sound', type='TEXT', affinity='2', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}, _id=Column{name='_id', type='INTEGER', affinity='3', notNull=true, primaryKeyPosition=1, defaultValue='undefined'}, cb=Column{name='cb', type='INTEGER', affinity='3', notNull=false, primaryKeyPosition=0, defaultValue='undefined'}}, foreignKeys=[], indices=[Index{name='acct_color_time', unique=false, columns=[time_save], orders=[ASC]'}, Index{name='acct_color_acct', unique=true, columns=[ac], orders=[ASC]'}]}
Found:
TableInfo{name='acct_color', columns={_id=Column{name='_id', type='INTEGER', affinity='3', notNull=false, primaryKeyPosition=1, defaultValue='undefined'}, ac=Column{name='ac', type='text', affinity='2', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}, cb=Column{name='cb', type='integer', affinity='3', notNull=false, primaryKeyPosition=0, defaultValue='undefined'}, cf=Column{name='cf', type='integer', affinity='3', notNull=false, primaryKeyPosition=0, defaultValue='undefined'}, nick=Column{name='nick', type='text', affinity='2', notNull=false, primaryKeyPosition=0, defaultValue='undefined'}, notification_sound=Column{name='notification_sound', type='text', affinity='2', notNull=false, primaryKeyPosition=0, defaultValue=''''}, time_save=Column{name='time_save', type='integer', affinity='3', notNull=true, primaryKeyPosition=0, defaultValue='undefined'}}, foreignKeys=[], indices=[Index{name='acct_color_time', unique=false, columns=[time_save], orders=[ASC]'}, Index{name='acct_color_acct', unique=true, columns=[ac], orders=[ASC]'}]}
