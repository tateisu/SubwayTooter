/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.sephiroth.android.library.exif2;

import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

class ExifParser {
	private static final String TAG = "ExifParser";

	/**
	 * When the parser reaches a new IFD area. Call {@link #getCurrentIfd()} to
	 * know which IFD we are in.
	 */
	public static final int EVENT_START_OF_IFD = 0;
	/**
	 * When the parser reaches a new tag. Call {@link #getTag()}to get the
	 * corresponding tag.
	 */
	public static final int EVENT_NEW_TAG = 1;
	/**
	 * When the parser reaches the value area of tag that is registered by
	 * {@link #registerForTagValue(ExifTag)} previously. Call {@link #getTag()}
	 * to get the corresponding tag.
	 */
	public static final int EVENT_VALUE_OF_REGISTERED_TAG = 2;
	/**
	 * When the parser reaches the compressed image area.
	 */
	public static final int EVENT_COMPRESSED_IMAGE = 3;
	/**
	 * When the parser reaches the uncompressed image strip. Call
	 * {@link #getStripIndex()} to get the index of the strip.
	 *
	 * @see #getStripIndex()
	 */
	public static final int EVENT_UNCOMPRESSED_STRIP = 4;
	/**
	 * When there is nothing more to parse.
	 */
	public static final int EVENT_END = 5;

	protected static final int EXIF_HEADER = 0x45786966; // EXIF header "Exif"
	protected static final short EXIF_HEADER_TAIL = (short) 0x0000; // EXIF header in M_EXIF
	// TIFF header
	protected static final short LITTLE_ENDIAN_TAG = (short) 0x4949; // "II"
	protected static final short BIG_ENDIAN_TAG = (short) 0x4d4d; // "MM"
	protected static final short TIFF_HEADER_TAIL = 0x002A;
	protected static final int TAG_SIZE = 12;
	protected static final int OFFSET_SIZE = 2;
	protected static final int DEFAULT_IFD0_OFFSET = 8;
	private static final Charset US_ASCII = Charset.forName( "US-ASCII" );
	private static final short TAG_EXIF_IFD = ExifInterface.getTrueTagKey( ExifInterface.TAG_EXIF_IFD );
	private static final short TAG_GPS_IFD = ExifInterface.getTrueTagKey( ExifInterface.TAG_GPS_IFD );
	private static final short TAG_INTEROPERABILITY_IFD = ExifInterface.getTrueTagKey( ExifInterface.TAG_INTEROPERABILITY_IFD );
	private static final short TAG_JPEG_INTERCHANGE_FORMAT = ExifInterface.getTrueTagKey( ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT );
	private static final short TAG_JPEG_INTERCHANGE_FORMAT_LENGTH = ExifInterface.getTrueTagKey( ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH );
	private static final short TAG_STRIP_OFFSETS = ExifInterface.getTrueTagKey( ExifInterface.TAG_STRIP_OFFSETS );
	private static final short TAG_STRIP_BYTE_COUNTS = ExifInterface.getTrueTagKey( ExifInterface.TAG_STRIP_BYTE_COUNTS );
	private final int mOptions;
	private final ExifInterface mInterface;
	private final TreeMap<Integer, Object> mCorrespondingEvent = new TreeMap<Integer, Object>();
	private final CountedDataInputStream mTiffStream;
	private int mIfdStartOffset = 0;
	private int mNumOfTagInIfd = 0;
	private int mIfdType;
	private ExifTag mTag;
	private ImageEvent mImageEvent;
	private ExifTag mStripSizeTag;
	private ExifTag mJpegSizeTag;
	private boolean mNeedToParseOffsetsInCurrentIfd;
	private byte[] mDataAboveIfd0;
	private int mIfd0Position;
	private int mQualityGuess;
	private int mImageWidth;
	private int mImageLength;
	private short mProcess = 0;
	private List<Section> mSections;
	private int mUncompressedDataPosition = 0;

	static final int std_luminance_quant_tbl[];
	static final int std_chrominance_quant_tbl[];
	static final int deftabs[][];

	static {
		std_luminance_quant_tbl = new int[]{ 16, 11, 12, 14, 12, 10, 16, 14, 13, 14, 18, 17, 16, 19, 24, 40, 26, 24, 22, 22, 24, 49, 35, 37, 29, 40, 58, 51, 61, 60, 57, 51, 56, 55, 64,
				72, 92, 78, 64, 68, 87, 69, 55, 56, 80, 109, 81, 87, 95, 98, 103, 104, 103, 62, 77, 113, 121, 112, 100, 120, 92, 101, 103, 99 };

		std_chrominance_quant_tbl = new int[]

				{ 17, 18, 18, 24, 21, 24, 47, 26, 26, 47, 99, 66, 56, 66, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99,
						99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99 };

		deftabs = new int[][]{ std_luminance_quant_tbl, std_chrominance_quant_tbl };
	}

	private ExifParser( InputStream inputStream, int options, ExifInterface iRef ) throws IOException, ExifInvalidFormatException {
		if( inputStream == null ) {
			throw new IOException( "Null argument inputStream to ExifParser" );
		}

		Log.v( TAG, "Reading exif..." );
		mSections = new ArrayList<Section>(0);

		mInterface = iRef;
		mTiffStream = seekTiffData( inputStream );
		mOptions = options;

		// Log.d( TAG, "sections size: " + mSections.size() );

		if( mTiffStream == null ) {
			return;
		}

		parseTiffHeader( mTiffStream );

		long offset = mTiffStream.readUnsignedInt();
		if( offset > Integer.MAX_VALUE ) {
			throw new ExifInvalidFormatException( "Invalid offset " + offset );
		}
		mIfd0Position = (int) offset;
		mIfdType = IfdId.TYPE_IFD_0;

		if( isIfdRequested( IfdId.TYPE_IFD_0 ) || needToParseOffsetsInCurrentIfd() ) {
			registerIfd( IfdId.TYPE_IFD_0, offset );
			if( offset != DEFAULT_IFD0_OFFSET ) {
				mDataAboveIfd0 = new byte[(int) offset - DEFAULT_IFD0_OFFSET];
				read( mDataAboveIfd0 );
			}
		}
	}

	private final byte mByteArray[] = new byte[8];
	private final ByteBuffer mByteBuffer = ByteBuffer.wrap( mByteArray );

	private int readInt( byte b[], int offset ) {
		mByteBuffer.rewind();
		mByteBuffer.put( b, offset, 4 );
		mByteBuffer.rewind();
		return mByteBuffer.getInt();
	}

	private short readShort( byte b[], int offset ) {
		mByteBuffer.rewind();
		mByteBuffer.put( b, offset, 2 );
		mByteBuffer.rewind();
		return mByteBuffer.getShort();
	}

	private CountedDataInputStream seekTiffData( InputStream inputStream ) throws IOException, ExifInvalidFormatException {
		CountedDataInputStream dataStream = new CountedDataInputStream( inputStream );
		CountedDataInputStream tiffStream = null;

		int a = dataStream.readUnsignedByte();
		int b = dataStream.readUnsignedByte();

		if( a != 0xFF || b != JpegHeader.TAG_SOI ) {
			Log.e( TAG, "invalid jpeg header" );
			return null;
		}

		while( true ) {
			int itemlen;
			int prev;
			int marker;
			byte ll,lh;
			int got;
			byte data[];

			prev = 0;
			for( a = 0; ; a++ ) {
				marker = dataStream.readUnsignedByte();
				if( marker != 0xff && prev == 0xff ) break;
				prev = marker;
			}

			if (a > 10){
				Log.w( TAG, "Extraneous " + ( a - 1 ) + " padding bytes before section " + marker );
			}

			Section section = new Section();
			section.type = marker;

			// Read the length of the section.
			lh = dataStream.readByte();
			ll = dataStream.readByte();
			itemlen = ( ( lh & 0xff ) << 8 ) | ( ll & 0xff );

			if( itemlen < 2 ) {
				throw new ExifInvalidFormatException( "Invalid marker" );
			}

			section.size = itemlen;

			data = new byte[itemlen];
			data[0] = lh;
			data[1] = ll;

			// Log.i( TAG, "marker: " + String.format( "0x%2X", marker ) + ": " + itemlen + ", position: " + dataStream.getReadByteCount() + ", available: " + dataStream.available() );
			// got = dataStream.read( data, 2, itemlen-2 );

			got = readBytes( dataStream, data, 2, itemlen - 2 );

			if( got != itemlen - 2 ) {
				throw new ExifInvalidFormatException( "Premature end of file? Expecting " + (itemlen-2) + ", received " + got );
			}

			section.data = data;


			boolean ignore = false;

			switch( marker ) {
				case JpegHeader.TAG_M_SOS:
					// stop before hitting compressed data
					mSections.add( section );
					mUncompressedDataPosition = dataStream.getReadByteCount();
					return tiffStream;

				case JpegHeader.TAG_M_DQT:
					// Use for jpeg quality guessing
					process_M_DQT( data, itemlen );
					break;

				case JpegHeader.TAG_M_DHT:
					break;

				case JpegHeader.TAG_M_EOI:
					// in case it's a tables-only JPEG stream
					Log.w( TAG, "No image in jpeg!" );
					return null;

				case JpegHeader.TAG_M_COM:
					// Comment section
					ignore = true;
					break;

				case JpegHeader.TAG_M_JFIF:
					if( itemlen < 16 ) {
						ignore = true;
					}
					break;

				case JpegHeader.TAG_M_IPTC:
					break;

				case JpegHeader.TAG_M_SOF0:
				case JpegHeader.TAG_M_SOF1:
				case JpegHeader.TAG_M_SOF2:
				case JpegHeader.TAG_M_SOF3:
				case JpegHeader.TAG_M_SOF5:
				case JpegHeader.TAG_M_SOF6:
				case JpegHeader.TAG_M_SOF7:
				case JpegHeader.TAG_M_SOF9:
				case JpegHeader.TAG_M_SOF10:
				case JpegHeader.TAG_M_SOF11:
				case JpegHeader.TAG_M_SOF13:
				case JpegHeader.TAG_M_SOF14:
				case JpegHeader.TAG_M_SOF15:
					process_M_SOFn( data, marker );
					break;

				case JpegHeader.TAG_M_EXIF:
					if( itemlen >= 8 ) {
						int header = readInt( data, 2 );
						short headerTail = readShort( data, 6 );
						// header = Exif, headerTail=\0\0
						if( header == EXIF_HEADER && headerTail == EXIF_HEADER_TAIL ) {
							tiffStream = new CountedDataInputStream( new ByteArrayInputStream( data, 8, itemlen - 8 ) );
							tiffStream.setEnd( itemlen - 6 );
							ignore = false;
						} else {
							Log.v( TAG, "Image cotains XMP section" );
						}
					}
					break;

				default:
					Log.w( TAG, "Unknown marker: " + String.format( "0x%2X", marker ) + ", length: " + itemlen );
					break;
			}

			if( !ignore ) {
				// Log.d( TAG, "adding section with size: " + section.size );
				mSections.add( section );
			}
			else {
				Log.v( TAG, "ignoring marker: " + String.format( "0x%2X", marker ) + ", length: " + itemlen );
			}
		}
	}

	/**
	 * Using this instead of the default {@link java.io.InputStream#read(byte[], int, int)} because
	 * on remote input streams reading large amount of data can fail
	 *
	 * @param dataStream
	 * @param data
	 * @param offset
	 * @param length
	 * @return
	 * @throws IOException
	 */
	private int readBytes( final InputStream dataStream, final byte[] data, int offset, final int length ) throws IOException {
		int count = 0;
		int n;
		int max_length = Math.min( 1024, length );

		while( 0 < (n = dataStream.read(data, offset, max_length))) {
			count += n;
			offset += n;
			max_length = Math.min( max_length, length-count );
		}
		return count;
	}

	static int Get16m( byte[] data, int position ) {
		int b1 = ( data[position] & 0xFF ) << 8;
		int b2 = data[position + 1] & 0xFF;
		return b1 | b2;
	}

	private void process_M_SOFn( final byte[] data, final int marker ) {
		if( data.length > 7 ) {
			//int data_precision = data[2] & 0xff;
			//int num_components = data[7] & 0xff;
			mImageLength = Get16m( data, 3 );
			mImageWidth = Get16m( data, 5 );
		}
		mProcess = (short) marker;
	}

	private void process_M_DQT( final byte[] data, int length ) {
		int a = 2;
		int c;
		int tableindex, coefindex;
		double cumsf = 0.0;
		int[] reftable = null;
		int allones = 1;

		while( a < data.length ) {
			c = data[a++];
			tableindex = c & 0x0f;

			if( tableindex < 2 ) {
				reftable = deftabs[tableindex];
			}

			// Read in the table, compute statistics relative to reference table
			for( coefindex = 0; coefindex < 64; coefindex++ ) {
				int val;
				if( ( c >> 4 ) != 0 ) {
					int temp;
					temp = (int) ( data[a++] );
					temp *= 256;
					val = (int) data[a++] + temp;
				}
				else {
					val = (int) data[a++];
				}
				if( reftable != null ) {
					double x;
					// scaling factor in percent
					x = 100.0 * (double) val / (double) reftable[coefindex];
					cumsf += x;
					// separate check for all-ones table (Q 100)
					if( val != 1 ) allones = 0;
				}
			}
			// Print summary stats
			if( reftable != null ) { // terse output includes quality
				double qual;
				cumsf /= 64.0;    // mean scale factor
				if( allones != 0 ) {      // special case for all-ones table
					qual = 100.0;
				}
				else if( cumsf <= 100.0 ) {
					qual = ( 200.0 - cumsf ) / 2.0;
				}
				else {
					qual = 5000.0 / cumsf;
				}

				if( tableindex == 0 ) {
					mQualityGuess = (int) ( qual + 0.5 );
					// Log.v( TAG, "quality guess: " + mQualityGuess );
				}
			}
		}
	}

	private void parseTiffHeader( final CountedDataInputStream stream ) throws IOException, ExifInvalidFormatException {
		short byteOrder = stream.readShort();
		if( LITTLE_ENDIAN_TAG == byteOrder ) {
			stream.setByteOrder( ByteOrder.LITTLE_ENDIAN );
		}
		else if( BIG_ENDIAN_TAG == byteOrder ) {
			stream.setByteOrder( ByteOrder.BIG_ENDIAN );
		}
		else {
			throw new ExifInvalidFormatException( "Invalid TIFF header" );
		}

		if( stream.readShort() != TIFF_HEADER_TAIL ) {
			throw new ExifInvalidFormatException( "Invalid TIFF header" );
		}
	}

	private boolean isIfdRequested( int ifdType ) {
		switch( ifdType ) {
			case IfdId.TYPE_IFD_0:
				return ( mOptions & ExifInterface.Options.OPTION_IFD_0 ) != 0;
			case IfdId.TYPE_IFD_1:
				return ( mOptions & ExifInterface.Options.OPTION_IFD_1 ) != 0;
			case IfdId.TYPE_IFD_EXIF:
				return ( mOptions & ExifInterface.Options.OPTION_IFD_EXIF ) != 0;
			case IfdId.TYPE_IFD_GPS:
				return ( mOptions & ExifInterface.Options.OPTION_IFD_GPS ) != 0;
			case IfdId.TYPE_IFD_INTEROPERABILITY:
				return ( mOptions & ExifInterface.Options.OPTION_IFD_INTEROPERABILITY ) != 0;
		}
		return false;
	}

	private boolean needToParseOffsetsInCurrentIfd() {
		switch( mIfdType ) {
			case IfdId.TYPE_IFD_0:
				return isIfdRequested( IfdId.TYPE_IFD_EXIF ) || isIfdRequested( IfdId.TYPE_IFD_GPS ) || isIfdRequested( IfdId.TYPE_IFD_INTEROPERABILITY ) ||
				       isIfdRequested( IfdId.TYPE_IFD_1 );
			case IfdId.TYPE_IFD_1:
				return isThumbnailRequested();
			case IfdId.TYPE_IFD_EXIF:
				// The offset to interoperability IFD is located in Exif IFD
				return isIfdRequested( IfdId.TYPE_IFD_INTEROPERABILITY );
			default:
				return false;
		}
	}

	private void registerIfd( int ifdType, long offset ) {
		// Cast unsigned int to int since the offset is always smaller
		// than the size of M_EXIF (65536)
		mCorrespondingEvent.put( (int) offset, new IfdEvent( ifdType, isIfdRequested( ifdType ) ) );
	}

	/**
	 * Equivalent to read(buffer, 0, buffer.length).
	 */
	protected int read( byte[] buffer ) throws IOException {
		return mTiffStream.read( buffer );
	}

	private boolean isThumbnailRequested() {
		return ( mOptions & ExifInterface.Options.OPTION_THUMBNAIL ) != 0;
	}

	/**
	 * Parses the the given InputStream with the given options
	 *
	 * @throws java.io.IOException
	 * @throws ExifInvalidFormatException
	 */
	protected static ExifParser parse( InputStream inputStream, int options, ExifInterface iRef ) throws IOException, ExifInvalidFormatException {
		return new ExifParser( inputStream, options, iRef );
	}
//
//	/**
//	 * Parses the the given InputStream with default options; that is, every IFD
//	 * and thumbnaill will be parsed.
//	 *
//	 * @throws java.io.IOException
//	 * @throws ExifInvalidFormatException
//	 */
//	protected static ExifParser parse( InputStream inputStream, boolean requestThumbnail, ExifInterface iRef ) throws IOException, ExifInvalidFormatException {
//		return new ExifParser( inputStream, OPTION_IFD_0 | OPTION_IFD_1 | OPTION_IFD_EXIF | OPTION_IFD_GPS | OPTION_IFD_INTEROPERABILITY | ( requestThumbnail ? OPTION_THUMBNAIL : 0 ), iRef );
//	}

	/**
	 * Moves the parser forward and returns the next parsing event
	 *
	 * @throws java.io.IOException
	 * @throws ExifInvalidFormatException
	 * @see #EVENT_START_OF_IFD
	 * @see #EVENT_NEW_TAG
	 * @see #EVENT_VALUE_OF_REGISTERED_TAG
	 * @see #EVENT_COMPRESSED_IMAGE
	 * @see #EVENT_UNCOMPRESSED_STRIP
	 * @see #EVENT_END
	 */
	protected int next() throws IOException, ExifInvalidFormatException {
		if( null == mTiffStream ) {
			return EVENT_END;
		}

		int offset = mTiffStream.getReadByteCount();
		int endOfTags = mIfdStartOffset + OFFSET_SIZE + TAG_SIZE * mNumOfTagInIfd;
		if( offset < endOfTags ) {
			mTag = readTag();
			if( mTag == null ) {
				return next();
			}
			if( mNeedToParseOffsetsInCurrentIfd ) {
				checkOffsetOrImageTag( mTag );
			}
			return EVENT_NEW_TAG;
		}
		else if( offset == endOfTags ) {
			// There is a link to ifd1 at the end of ifd0
			if( mIfdType == IfdId.TYPE_IFD_0 ) {
				long ifdOffset = readUnsignedLong();
				if( isIfdRequested( IfdId.TYPE_IFD_1 ) || isThumbnailRequested() ) {
					if( ifdOffset != 0 ) {
						registerIfd( IfdId.TYPE_IFD_1, ifdOffset );
					}
				}
			}
			else {
				int offsetSize = 4;
				// Some camera models use invalid length of the offset
				if( mCorrespondingEvent.size() > 0 ) {
					offsetSize = mCorrespondingEvent.firstEntry().getKey() - mTiffStream.getReadByteCount();
				}
				if( offsetSize < 4 ) {
					Log.w( TAG, "Invalid size of link to next IFD: " + offsetSize );
				}
				else {
					long ifdOffset = readUnsignedLong();
					if( ifdOffset != 0 ) {
						Log.w( TAG, "Invalid link to next IFD: " + ifdOffset );
					}
				}
			}
		}
		while( mCorrespondingEvent.size() != 0 ) {
			Entry<Integer, Object> entry = mCorrespondingEvent.pollFirstEntry();
			Object event = entry.getValue();
			try {
				// Log.v(TAG, "skipTo: " + entry.getKey());
				skipTo( entry.getKey() );
			} catch( IOException e ) {
				Log.w( TAG, "Failed to skip to data at: " + entry.getKey() +
				            " for " + event.getClass().getName() + ", the file may be broken." );
				continue;
			}
			if( event instanceof IfdEvent ) {
				mIfdType = ( (IfdEvent) event ).ifd;
				mNumOfTagInIfd = mTiffStream.readUnsignedShort();
				mIfdStartOffset = entry.getKey();

				if( mNumOfTagInIfd * TAG_SIZE + mIfdStartOffset + OFFSET_SIZE > mTiffStream.getEnd() ) {
					Log.w( TAG, "Invalid size of IFD " + mIfdType );
					return EVENT_END;
				}

				mNeedToParseOffsetsInCurrentIfd = needToParseOffsetsInCurrentIfd();
				if( ( (IfdEvent) event ).isRequested ) {
					return EVENT_START_OF_IFD;
				}
				else {
					skipRemainingTagsInCurrentIfd();
				}
			}
			else if( event instanceof ImageEvent ) {
				mImageEvent = (ImageEvent) event;
				return mImageEvent.type;
			}
			else {
				ExifTagEvent tagEvent = (ExifTagEvent) event;
				mTag = tagEvent.tag;
				if( mTag.getDataType() != ExifTag.TYPE_UNDEFINED ) {
					readFullTagValue( mTag );
					checkOffsetOrImageTag( mTag );
				}
				if( tagEvent.isRequested ) {
					return EVENT_VALUE_OF_REGISTERED_TAG;
				}
			}
		}
		return EVENT_END;
	}

	/**
	 * Skips the tags area of current IFD, if the parser is not in the tag area,
	 * nothing will happen.
	 *
	 * @throws java.io.IOException
	 * @throws ExifInvalidFormatException
	 */
	protected void skipRemainingTagsInCurrentIfd() throws IOException, ExifInvalidFormatException {
		int endOfTags = mIfdStartOffset + OFFSET_SIZE + TAG_SIZE * mNumOfTagInIfd;
		int offset = mTiffStream.getReadByteCount();
		if( offset > endOfTags ) {
			return;
		}
		if( mNeedToParseOffsetsInCurrentIfd ) {
			while( offset < endOfTags ) {
				mTag = readTag();
				offset += TAG_SIZE;
				if( mTag == null ) {
					continue;
				}
				checkOffsetOrImageTag( mTag );
			}
		}
		else {
			skipTo( endOfTags );
		}
		long ifdOffset = readUnsignedLong();
		// For ifd0, there is a link to ifd1 in the end of all tags
		if( mIfdType == IfdId.TYPE_IFD_0 && ( isIfdRequested( IfdId.TYPE_IFD_1 ) || isThumbnailRequested() ) ) {
			if( ifdOffset > 0 ) {
				registerIfd( IfdId.TYPE_IFD_1, ifdOffset );
			}
		}
	}

	/**
	 * If {@link #next()} return {@link #EVENT_NEW_TAG} or
	 * {@link #EVENT_VALUE_OF_REGISTERED_TAG}, call this function to get the
	 * corresponding tag.
	 * <p/>
	 * For {@link #EVENT_NEW_TAG}, the tag may not contain the value if the size
	 * of the value is greater than 4 bytes. One should call
	 * {@link ExifTag#hasValue()} to check if the tag contains value. If there
	 * is no value,call {@link #registerForTagValue(ExifTag)} to have the parser
	 * emit {@link #EVENT_VALUE_OF_REGISTERED_TAG} when it reaches the area
	 * pointed by the offset.
	 * <p/>
	 * When {@link #EVENT_VALUE_OF_REGISTERED_TAG} is emitted, the value of the
	 * tag will have already been read except for tags of undefined type. For
	 * tags of undefined type, call one of the read methods to get the value.
	 *
	 * @see #registerForTagValue(ExifTag)
	 * @see #read(byte[])
	 * @see #read(byte[], int, int)
	 * @see #readLong()
	 * @see #readRational()
	 * @see #readString(int)
	 * @see #readString(int, java.nio.charset.Charset)
	 */
	protected ExifTag getTag() {
		return mTag;
	}

	/**
	 * Gets number of tags in the current IFD area.
	 */
	public int getTagCountInCurrentIfd() {
		return mNumOfTagInIfd;
	}

	/**
	 * Gets the ID of current IFD.
	 *
	 * @see IfdId#TYPE_IFD_0
	 * @see IfdId#TYPE_IFD_1
	 * @see IfdId#TYPE_IFD_GPS
	 * @see IfdId#TYPE_IFD_INTEROPERABILITY
	 * @see IfdId#TYPE_IFD_EXIF
	 */
	protected int getCurrentIfd() {
		return mIfdType;
	}

	/**
	 * When receiving {@link #EVENT_UNCOMPRESSED_STRIP}, call this function to
	 * get the index of this strip.
	 */
	protected int getStripIndex() {
		return mImageEvent.stripIndex;
	}

	/**
	 * When receiving {@link #EVENT_UNCOMPRESSED_STRIP}, call this function to
	 * get the strip size.
	 */
	protected int getStripSize() {
		if( mStripSizeTag == null ) return 0;
		return (int) mStripSizeTag.getValueAt( 0 );
	}

	/**
	 * When receiving {@link #EVENT_COMPRESSED_IMAGE}, call this function to get
	 * the image data size.
	 */
	protected int getCompressedImageSize() {
		if( mJpegSizeTag == null ) {
			return 0;
		}
		return (int) mJpegSizeTag.getValueAt( 0 );
	}

	private void skipTo( int offset ) throws IOException {
		mTiffStream.skipTo( offset );
		// Log.v(TAG, "available: " + mTiffStream.available() );
		while( ! mCorrespondingEvent.isEmpty() && mCorrespondingEvent.firstKey() < offset ) {
			mCorrespondingEvent.pollFirstEntry();
		}
	}

	/**
	 * When getting {@link #EVENT_NEW_TAG} in the tag area of IFD, the tag may
	 * not contain the value if the size of the value is greater than 4 bytes.
	 * When the value is not available here, call this method so that the parser
	 * will emit {@link #EVENT_VALUE_OF_REGISTERED_TAG} when it reaches the area
	 * where the value is located.
	 *
	 * @see #EVENT_VALUE_OF_REGISTERED_TAG
	 */
	protected void registerForTagValue( ExifTag tag ) {
		if( tag.getOffset() >= mTiffStream.getReadByteCount() ) {
			mCorrespondingEvent.put( tag.getOffset(), new ExifTagEvent( tag, true ) );
		}
	}

	private void registerCompressedImage( long offset ) {
		mCorrespondingEvent.put( (int) offset, new ImageEvent( EVENT_COMPRESSED_IMAGE ) );
	}

	private void registerUncompressedStrip( int stripIndex, long offset ) {
		mCorrespondingEvent.put( (int) offset, new ImageEvent( EVENT_UNCOMPRESSED_STRIP, stripIndex ) );
	}

	private ExifTag readTag() throws IOException, ExifInvalidFormatException {
		short tagId = mTiffStream.readShort();
		short dataFormat = mTiffStream.readShort();
		long numOfComp = mTiffStream.readUnsignedInt();
		if( numOfComp > Integer.MAX_VALUE ) {
			throw new ExifInvalidFormatException( "Number of component is larger then Integer.MAX_VALUE" );
		}
		// Some invalid image file contains invalid data type. Ignore those tags
		if( ! ExifTag.isValidType( dataFormat ) ) {
			Log.w( TAG, String.format( "Tag %04x: Invalid data type %d", tagId, dataFormat ) );
			mTiffStream.skip( 4 );
			return null;
		}
		// TODO: handle numOfComp overflow
		ExifTag tag = new ExifTag( tagId, dataFormat, (int) numOfComp, mIfdType, ( (int) numOfComp ) != ExifTag.SIZE_UNDEFINED );
		int dataSize = tag.getDataSize();
		if( dataSize > 4 ) {
			long offset = mTiffStream.readUnsignedInt();
			if( offset > Integer.MAX_VALUE ) {
				throw new ExifInvalidFormatException( "offset is larger then Integer.MAX_VALUE" );
			}
			// Some invalid images put some undefined data before IFD0.
			// Read the data here.
			if( ( offset < mIfd0Position ) && ( dataFormat == ExifTag.TYPE_UNDEFINED ) ) {
				byte[] buf = new byte[(int) numOfComp];
				System.arraycopy( mDataAboveIfd0, (int) offset - DEFAULT_IFD0_OFFSET, buf, 0, (int) numOfComp );
				tag.setValue( buf );
			}
			else {
				tag.setOffset( (int) offset );
			}
		}
		else {
			boolean defCount = tag.hasDefinedCount();
			// Set defined count to 0 so we can add \0 to non-terminated strings
			tag.setHasDefinedCount( false );
			// Read value
			readFullTagValue( tag );
			tag.setHasDefinedCount( defCount );
			mTiffStream.skip( 4 - dataSize );
			// Set the offset to the position of value.
			tag.setOffset( mTiffStream.getReadByteCount() - 4 );
		}
		return tag;
	}

	/**
	 * Check the tag, if the tag is one of the offset tag that points to the IFD
	 * or image the caller is interested in, register the IFD or image.
	 */
	private void checkOffsetOrImageTag( ExifTag tag ) {
		// Some invalid formattd image contains tag with 0 size.
		if( tag.getComponentCount() == 0 ) {
			return;
		}
		short tid = tag.getTagId();
		int ifd = tag.getIfd();
		if( tid == TAG_EXIF_IFD && checkAllowed( ifd, ExifInterface.TAG_EXIF_IFD ) ) {
			if( isIfdRequested( IfdId.TYPE_IFD_EXIF ) || isIfdRequested( IfdId.TYPE_IFD_INTEROPERABILITY ) ) {
				registerIfd( IfdId.TYPE_IFD_EXIF, tag.getValueAt( 0 ) );
			}
		}
		else if( tid == TAG_GPS_IFD && checkAllowed( ifd, ExifInterface.TAG_GPS_IFD ) ) {
			if( isIfdRequested( IfdId.TYPE_IFD_GPS ) ) {
				registerIfd( IfdId.TYPE_IFD_GPS, tag.getValueAt( 0 ) );
			}
		}
		else if( tid == TAG_INTEROPERABILITY_IFD && checkAllowed( ifd, ExifInterface.TAG_INTEROPERABILITY_IFD ) ) {
			if( isIfdRequested( IfdId.TYPE_IFD_INTEROPERABILITY ) ) {
				registerIfd( IfdId.TYPE_IFD_INTEROPERABILITY, tag.getValueAt( 0 ) );
			}
		}
		else if( tid == TAG_JPEG_INTERCHANGE_FORMAT && checkAllowed( ifd, ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT ) ) {
			if( isThumbnailRequested() ) {
				registerCompressedImage( tag.getValueAt( 0 ) );
			}
		}
		else if( tid == TAG_JPEG_INTERCHANGE_FORMAT_LENGTH && checkAllowed( ifd, ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH ) ) {
			if( isThumbnailRequested() ) {
				mJpegSizeTag = tag;
			}
		}
		else if( tid == TAG_STRIP_OFFSETS && checkAllowed( ifd, ExifInterface.TAG_STRIP_OFFSETS ) ) {
			if( isThumbnailRequested() ) {
				if( tag.hasValue() ) {
					for( int i = 0; i < tag.getComponentCount(); i++ ) {
						if( tag.getDataType() == ExifTag.TYPE_UNSIGNED_SHORT ) {
							registerUncompressedStrip( i, tag.getValueAt( i ) );
						}
						else {
							registerUncompressedStrip( i, tag.getValueAt( i ) );
						}
					}
				}
				else {
					mCorrespondingEvent.put( tag.getOffset(), new ExifTagEvent( tag, false ) );
				}
			}
		}
		else if( tid == TAG_STRIP_BYTE_COUNTS && checkAllowed( ifd, ExifInterface.TAG_STRIP_BYTE_COUNTS ) && isThumbnailRequested() && tag.hasValue() ) {
			mStripSizeTag = tag;
		}
	}

	public boolean isDefinedTag(int ifdId, int tagId ) {
		return mInterface.getTagInfo().get(ExifInterface.defineTag(ifdId, (short)tagId)) != ExifInterface.DEFINITION_NULL;
	}

	public boolean checkAllowed( int ifd, int tagId ) {
		int info = mInterface.getTagInfo().get( tagId );
		if( info == ExifInterface.DEFINITION_NULL ) {
			return false;
		}
		return ExifInterface.isIfdAllowed( info, ifd );
	}

	protected void readFullTagValue( final ExifTag tag ) throws IOException {
		// Some invalid images contains tags with wrong size, check it here
		short type = tag.getDataType();
		final int componentCount = tag.getComponentCount();

		// sanity check
		if (componentCount >= 0x66000000) throw new IOException("size out of bounds");

		if( type == ExifTag.TYPE_ASCII || type == ExifTag.TYPE_UNDEFINED ||
		    type == ExifTag.TYPE_UNSIGNED_BYTE ) {
			int size = tag.getComponentCount();
			if( mCorrespondingEvent.size() > 0 ) {
				if( mCorrespondingEvent.firstEntry().getKey() < mTiffStream.getReadByteCount() + size ) {
					Object event = mCorrespondingEvent.firstEntry().getValue();
					if( event instanceof ImageEvent ) {
						// Tag value overlaps thumbnail, ignore thumbnail.
						Log.w( TAG, "Thumbnail overlaps value for tag: \n" + tag.toString() );
						Entry<Integer, Object> entry = mCorrespondingEvent.pollFirstEntry();
						Log.w( TAG, "Invalid thumbnail offset: " + entry.getKey() );
					}
					else {
						// Tag value overlaps another tag, shorten count
						if( event instanceof IfdEvent ) {
							Log.w( TAG, "Ifd " + ( (IfdEvent) event ).ifd + " overlaps value for tag: \n" + tag.toString() );
						}
						else if( event instanceof ExifTagEvent ) {
							Log.w( TAG, "Tag value for tag: \n" + ( (ExifTagEvent) event ).tag.toString() + " overlaps value for tag: \n" + tag.toString() );
						}
						size = mCorrespondingEvent.firstEntry().getKey() - mTiffStream.getReadByteCount();
						Log.w( TAG, "Invalid size of tag: \n" + tag.toString() + " setting count to: " + size );
						tag.forceSetComponentCount( size );
					}
				}
			}
		}
		switch( tag.getDataType() ) {
			case ExifTag.TYPE_UNSIGNED_BYTE:
			case ExifTag.TYPE_UNDEFINED: {
				byte buf[] = new byte[componentCount];
				read( buf );
				tag.setValue( buf );
			}
			break;
			case ExifTag.TYPE_ASCII:
				tag.setValue( readString(componentCount) );
				break;
			case ExifTag.TYPE_UNSIGNED_LONG: {
				long value[] = new long[componentCount];
				for( int i = 0, n = value.length; i < n; i++ ) {
					value[i] = readUnsignedLong();
				}
				tag.setValue( value );
			}
			break;
			case ExifTag.TYPE_UNSIGNED_RATIONAL: {
				Rational value[] = new Rational[componentCount];
				for( int i = 0, n = value.length; i < n; i++ ) {
					value[i] = readUnsignedRational();
				}
				tag.setValue( value );
			}
			break;
			case ExifTag.TYPE_UNSIGNED_SHORT: {
				int value[] = new int[componentCount];
				for( int i = 0, n = value.length; i < n; i++ ) {
					value[i] = readUnsignedShort();
				}
				tag.setValue( value );
			}
			break;
			case ExifTag.TYPE_LONG: {
				int value[] = new int[componentCount];
				for( int i = 0, n = value.length; i < n; i++ ) {
					value[i] = readLong();
				}
				tag.setValue( value );
			}
			break;
			case ExifTag.TYPE_RATIONAL: {
				Rational value[] = new Rational[componentCount];
				for( int i = 0, n = value.length; i < n; i++ ) {
					value[i] = readRational();
				}
				tag.setValue( value );
			}
			break;
		}

		// Log.v( TAG, "\n" + tag.toString() );
	}

	/**
	 * Reads bytes from the InputStream.
	 */
	protected int read( byte[] buffer, int offset, int length ) throws IOException {
		return mTiffStream.read( buffer, offset, length );
	}

	/**
	 * Reads a String from the InputStream with US-ASCII charset. The parser
	 * will read n bytes and convert it to ascii string. This is used for
	 * reading values of type {@link ExifTag#TYPE_ASCII}.
	 */
	protected String readString( int n ) throws IOException {
		return readString( n, US_ASCII );
	}

	/**
	 * Reads a String from the InputStream with the given charset. The parser
	 * will read n bytes and convert it to string. This is used for reading
	 * values of type {@link ExifTag#TYPE_ASCII}.
	 */
	protected String readString( int n, Charset charset ) throws IOException {
		if( n > 0 ) {
			return mTiffStream.readString( n, charset );
		}
		else {
			return "";
		}
	}

	/**
	 * Reads value of type {@link ExifTag#TYPE_UNSIGNED_SHORT} from the
	 * InputStream.
	 */
	protected int readUnsignedShort() throws IOException {
		return mTiffStream.readShort() & 0xffff;
	}

	/**
	 * Reads value of type {@link ExifTag#TYPE_UNSIGNED_LONG} from the
	 * InputStream.
	 */
	protected long readUnsignedLong() throws IOException {
		return readLong() & 0xffffffffL;
	}

	/**
	 * Reads value of type {@link ExifTag#TYPE_UNSIGNED_RATIONAL} from the
	 * InputStream.
	 */
	protected Rational readUnsignedRational() throws IOException {
		long nomi = readUnsignedLong();
		long denomi = readUnsignedLong();
		return new Rational( nomi, denomi );
	}

	/**
	 * Reads value of type {@link ExifTag#TYPE_LONG} from the InputStream.
	 */
	protected int readLong() throws IOException {
		return mTiffStream.readInt();
	}

	/**
	 * Reads value of type {@link ExifTag#TYPE_RATIONAL} from the InputStream.
	 */
	protected Rational readRational() throws IOException {
		int nomi = readLong();
		int denomi = readLong();
		return new Rational( nomi, denomi );
	}

	/**
	 * Gets the byte order of the current InputStream.
	 */
	protected ByteOrder getByteOrder() {
		if( null != mTiffStream ) return mTiffStream.getByteOrder();
		return null;
	}

	public int getQualityGuess() {
		return mQualityGuess;
	}

	public int getImageWidth() {
		return mImageWidth;
	}

	public short getJpegProcess() {
		return mProcess;
	}

	public int getImageLength() {
		return mImageLength;
	}

	public List<Section> getSections() {
		return mSections;
	}

	public int getUncompressedDataPosition() {
		return mUncompressedDataPosition;
	}

	private static class ImageEvent {
		int stripIndex;
		int type;

		ImageEvent( int type ) {
			this.stripIndex = 0;
			this.type = type;
		}

		ImageEvent( int type, int stripIndex ) {
			this.type = type;
			this.stripIndex = stripIndex;
		}
	}

	private static class IfdEvent {
		int ifd;
		boolean isRequested;

		IfdEvent( int ifd, boolean isInterestedIfd ) {
			this.ifd = ifd;
			this.isRequested = isInterestedIfd;
		}
	}

	private static class ExifTagEvent {
		ExifTag tag;
		boolean isRequested;

		ExifTagEvent( ExifTag tag, boolean isRequireByUser ) {
			this.tag = tag;
			this.isRequested = isRequireByUser;
		}
	}

	public static class Section {
		int size;
		int type;
		byte[] data;
	}
}
