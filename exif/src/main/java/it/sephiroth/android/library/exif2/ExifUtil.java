package it.sephiroth.android.library.exif2;

import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * Created by alessandro on 20/04/14.
 */
public class ExifUtil {

	static final NumberFormat formatter = DecimalFormat.getInstance();

	public static String processLensSpecifications( Rational[] values ) {
		Rational min_focal = values[0];
		Rational max_focal = values[1];
		Rational min_f = values[2];
		Rational max_f = values[3];

		formatter.setMaximumFractionDigits(1);

		StringBuilder sb = new StringBuilder();
		sb.append( formatter.format( min_focal.toDouble() ) );
		sb.append( "-" );
		sb.append( formatter.format( max_focal.toDouble() ) );
		sb.append( "mm f/" );
		sb.append( formatter.format( min_f.toDouble() ) );
		sb.append( "-" );
		sb.append( formatter.format( max_f.toDouble() ) );

		return sb.toString();
	}

}
