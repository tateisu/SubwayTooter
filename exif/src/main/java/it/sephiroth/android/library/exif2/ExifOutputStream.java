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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

class ExifOutputStream {
	private static final String TAG = "ExifOutputStream";
	private static final int STREAMBUFFER_SIZE = 0x00010000; // 64Kb

	private static final int STATE_SOI = 0;
	private static final int EXIF_HEADER = 0x45786966;
	private static final short TIFF_HEADER = 0x002A;
	private static final short TIFF_BIG_ENDIAN = 0x4d4d;
	private static final short TIFF_LITTLE_ENDIAN = 0x4949;
	private static final short TAG_SIZE = 12;
	private static final short TIFF_HEADER_SIZE = 8;
	private static final int MAX_EXIF_SIZE = 65535;
	private final ExifInterface mInterface;
	private ExifData mExifData;
	private ByteBuffer mBuffer = ByteBuffer.allocate( 4 );

	protected ExifOutputStream( ExifInterface iRef ) {
		mInterface = iRef;
	}

	/**
	 * Gets the Exif header to be written into the JPEF file.
	 */
	protected ExifData getExifData() {
		return mExifData;
	}

	/**
	 * Sets the ExifData to be written into the JPEG file. Should be called
	 * before writing image data.
	 */
	protected void setExifData( ExifData exifData ) {
		mExifData = exifData;
	}

	private int requestByteToBuffer(
			int requestByteCount, byte[] buffer, int offset, int length ) {
		int byteNeeded = requestByteCount - mBuffer.position();
		int byteToRead = length > byteNeeded ? byteNeeded : length;
		mBuffer.put( buffer, offset, byteToRead );
		return byteToRead;
	}

	public void writeExifData( OutputStream out ) throws IOException {
		if( mExifData == null ) {
			return;
		}

		Log.v( TAG, "Writing exif data..." );

		ArrayList<ExifTag> nullTags = stripNullValueTags( mExifData );
		createRequiredIfdAndTag();
		int exifSize = calculateAllOffset();
		// Log.i(TAG, "exifSize: " + (exifSize + 8));
		if( exifSize + 8 > MAX_EXIF_SIZE ) {
			throw new IOException( "Exif header is too large (>64Kb)" );
		}

		BufferedOutputStream outputStream = new BufferedOutputStream( out, STREAMBUFFER_SIZE );
		OrderedDataOutputStream dataOutputStream = new OrderedDataOutputStream( outputStream );

		dataOutputStream.setByteOrder( ByteOrder.BIG_ENDIAN );

		dataOutputStream.write( 0xFF );
		dataOutputStream.write( JpegHeader.TAG_M_EXIF );
		dataOutputStream.writeShort( (short) ( exifSize + 8 ) );
		dataOutputStream.writeInt( EXIF_HEADER );
		dataOutputStream.writeShort( (short) 0x0000 );
		if( mExifData.getByteOrder() == ByteOrder.BIG_ENDIAN ) {
			dataOutputStream.writeShort( TIFF_BIG_ENDIAN );
		}
		else {
			dataOutputStream.writeShort( TIFF_LITTLE_ENDIAN );
		}
		dataOutputStream.setByteOrder( mExifData.getByteOrder() );
		dataOutputStream.writeShort( TIFF_HEADER );
		dataOutputStream.writeInt( 8 );
		writeAllTags( dataOutputStream );

		writeThumbnail( dataOutputStream );

		for( ExifTag t : nullTags ) {
			mExifData.addTag( t );
		}

		dataOutputStream.flush();
	}

	private ArrayList<ExifTag> stripNullValueTags( ExifData data ) {
		ArrayList<ExifTag> nullTags = new ArrayList<ExifTag>();
		for( ExifTag t : data.getAllTags() ) {
			if( t.getValue() == null && ! ExifInterface.isOffsetTag( t.getTagId() ) ) {
				data.removeTag( t.getTagId(), t.getIfd() );
				nullTags.add( t );
			}
		}
		return nullTags;
	}

	private void writeThumbnail( OrderedDataOutputStream dataOutputStream ) throws IOException {
		if( mExifData.hasCompressedThumbnail() ) {
			Log.d( TAG, "writing thumbnail.." );
			dataOutputStream.write( mExifData.getCompressedThumbnail() );
		}
		else if( mExifData.hasUncompressedStrip() ) {
			Log.d( TAG, "writing uncompressed strip.." );
			for( int i = 0; i < mExifData.getStripCount(); i++ ) {
				dataOutputStream.write( mExifData.getStrip( i ) );
			}
		}
	}

	private void writeAllTags( OrderedDataOutputStream dataOutputStream ) throws IOException {
		writeIfd( mExifData.getIfdData( IfdId.TYPE_IFD_0 ), dataOutputStream );
		writeIfd( mExifData.getIfdData( IfdId.TYPE_IFD_EXIF ), dataOutputStream );
		IfdData interoperabilityIfd = mExifData.getIfdData( IfdId.TYPE_IFD_INTEROPERABILITY );
		if( interoperabilityIfd != null ) {
			writeIfd( interoperabilityIfd, dataOutputStream );
		}
		IfdData gpsIfd = mExifData.getIfdData( IfdId.TYPE_IFD_GPS );
		if( gpsIfd != null ) {
			writeIfd( gpsIfd, dataOutputStream );
		}
		IfdData ifd1 = mExifData.getIfdData( IfdId.TYPE_IFD_1 );
		if( ifd1 != null ) {
			writeIfd( mExifData.getIfdData( IfdId.TYPE_IFD_1 ), dataOutputStream );
		}
	}

	private void writeIfd( IfdData ifd, OrderedDataOutputStream dataOutputStream ) throws IOException {
		ExifTag[] tags = ifd.getAllTags();
		dataOutputStream.writeShort( (short) tags.length );
		for( ExifTag tag : tags ) {
			dataOutputStream.writeShort( tag.getTagId() );
			dataOutputStream.writeShort( tag.getDataType() );
			dataOutputStream.writeInt( tag.getComponentCount() );
			// Log.v( TAG, "\n" + tag.toString() );
			if( tag.getDataSize() > 4 ) {
				dataOutputStream.writeInt( tag.getOffset() );
			}
			else {
				ExifOutputStream.writeTagValue( tag, dataOutputStream );
				for( int i = 0, n = 4 - tag.getDataSize(); i < n; i++ ) {
					dataOutputStream.write( 0 );
				}
			}
		}
		dataOutputStream.writeInt( ifd.getOffsetToNextIfd() );
		for( ExifTag tag : tags ) {
			if( tag.getDataSize() > 4 ) {
				ExifOutputStream.writeTagValue( tag, dataOutputStream );
			}
		}
	}

	private int calculateOffsetOfIfd( IfdData ifd, int offset ) {
		offset += 2 + ifd.getTagCount() * TAG_SIZE + 4;
		ExifTag[] tags = ifd.getAllTags();
		for( ExifTag tag : tags ) {
			if( tag.getDataSize() > 4 ) {
				tag.setOffset( offset );
				offset += tag.getDataSize();
			}
		}
		return offset;
	}

	private void createRequiredIfdAndTag() throws IOException {
		// IFD0 is required for all file
		IfdData ifd0 = mExifData.getIfdData( IfdId.TYPE_IFD_0 );
		if( ifd0 == null ) {
			ifd0 = new IfdData( IfdId.TYPE_IFD_0 );
			mExifData.addIfdData( ifd0 );
		}
		ExifTag exifOffsetTag = mInterface.buildUninitializedTag( ExifInterface.TAG_EXIF_IFD );
		if( exifOffsetTag == null ) {
			throw new IOException( "No definition for crucial exif tag: " + ExifInterface.TAG_EXIF_IFD );
		}
		ifd0.setTag( exifOffsetTag );

		// Exif IFD is required for all files.
		IfdData exifIfd = mExifData.getIfdData( IfdId.TYPE_IFD_EXIF );
		if( exifIfd == null ) {
			exifIfd = new IfdData( IfdId.TYPE_IFD_EXIF );
			mExifData.addIfdData( exifIfd );
		}

		// GPS IFD
		IfdData gpsIfd = mExifData.getIfdData( IfdId.TYPE_IFD_GPS );
		if( gpsIfd != null ) {
			ExifTag gpsOffsetTag = mInterface.buildUninitializedTag( ExifInterface.TAG_GPS_IFD );
			if( gpsOffsetTag == null ) {
				throw new IOException( "No definition for crucial exif tag: " + ExifInterface.TAG_GPS_IFD );
			}
			ifd0.setTag( gpsOffsetTag );
		}

		// Interoperability IFD
		IfdData interIfd = mExifData.getIfdData( IfdId.TYPE_IFD_INTEROPERABILITY );
		if( interIfd != null ) {
			ExifTag interOffsetTag = mInterface.buildUninitializedTag( ExifInterface.TAG_INTEROPERABILITY_IFD );
			if( interOffsetTag == null ) {
				throw new IOException( "No definition for crucial exif tag: " + ExifInterface.TAG_INTEROPERABILITY_IFD );
			}
			exifIfd.setTag( interOffsetTag );
		}

		IfdData ifd1 = mExifData.getIfdData( IfdId.TYPE_IFD_1 );

		// thumbnail
		if( mExifData.hasCompressedThumbnail() ) {

			if( ifd1 == null ) {
				ifd1 = new IfdData( IfdId.TYPE_IFD_1 );
				mExifData.addIfdData( ifd1 );
			}

			ExifTag offsetTag = mInterface.buildUninitializedTag( ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT );
			if( offsetTag == null ) {
				throw new IOException( "No definition for crucial exif tag: " + ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT );
			}

			ifd1.setTag( offsetTag );
			ExifTag lengthTag = mInterface.buildUninitializedTag( ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH );
			if( lengthTag == null ) {
				throw new IOException( "No definition for crucial exif tag: " + ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH );
			}

			lengthTag.setValue( mExifData.getCompressedThumbnail().length );
			ifd1.setTag( lengthTag );

			// Get rid of tags for uncompressed if they exist.
			ifd1.removeTag( ExifInterface.getTrueTagKey( ExifInterface.TAG_STRIP_OFFSETS ) );
			ifd1.removeTag( ExifInterface.getTrueTagKey( ExifInterface.TAG_STRIP_BYTE_COUNTS ) );
		}
		else if( mExifData.hasUncompressedStrip() ) {
			if( ifd1 == null ) {
				ifd1 = new IfdData( IfdId.TYPE_IFD_1 );
				mExifData.addIfdData( ifd1 );
			}
			int stripCount = mExifData.getStripCount();
			ExifTag offsetTag = mInterface.buildUninitializedTag( ExifInterface.TAG_STRIP_OFFSETS );
			if( offsetTag == null ) {
				throw new IOException( "No definition for crucial exif tag: " + ExifInterface.TAG_STRIP_OFFSETS );
			}
			ExifTag lengthTag = mInterface.buildUninitializedTag( ExifInterface.TAG_STRIP_BYTE_COUNTS );
			if( lengthTag == null ) {
				throw new IOException( "No definition for crucial exif tag: " + ExifInterface.TAG_STRIP_BYTE_COUNTS );
			}
			long[] lengths = new long[stripCount];
			for( int i = 0; i < mExifData.getStripCount(); i++ ) {
				lengths[i] = mExifData.getStrip( i ).length;
			}
			lengthTag.setValue( lengths );
			ifd1.setTag( offsetTag );
			ifd1.setTag( lengthTag );
			// Get rid of tags for compressed if they exist.
			ifd1.removeTag( ExifInterface.getTrueTagKey( ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT ) );
			ifd1.removeTag( ExifInterface.getTrueTagKey( ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH ) );
		}
		else if( ifd1 != null ) {
			// Get rid of offset and length tags if there is no thumbnail.
			ifd1.removeTag( ExifInterface.getTrueTagKey( ExifInterface.TAG_STRIP_OFFSETS ) );
			ifd1.removeTag( ExifInterface.getTrueTagKey( ExifInterface.TAG_STRIP_BYTE_COUNTS ) );
			ifd1.removeTag( ExifInterface.getTrueTagKey( ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT ) );
			ifd1.removeTag( ExifInterface.getTrueTagKey( ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH ) );
		}
	}

	private int calculateAllOffset() {
		int offset = TIFF_HEADER_SIZE;
		IfdData ifd0 = mExifData.getIfdData( IfdId.TYPE_IFD_0 );
		offset = calculateOffsetOfIfd( ifd0, offset );
		ifd0.getTag( ExifInterface.getTrueTagKey( ExifInterface.TAG_EXIF_IFD ) ).setValue( offset );

		IfdData exifIfd = mExifData.getIfdData( IfdId.TYPE_IFD_EXIF );
		offset = calculateOffsetOfIfd( exifIfd, offset );

		IfdData interIfd = mExifData.getIfdData( IfdId.TYPE_IFD_INTEROPERABILITY );
		if( interIfd != null ) {
			exifIfd.getTag( ExifInterface.getTrueTagKey( ExifInterface.TAG_INTEROPERABILITY_IFD ) ).setValue( offset );
			offset = calculateOffsetOfIfd( interIfd, offset );
		}

		IfdData gpsIfd = mExifData.getIfdData( IfdId.TYPE_IFD_GPS );
		if( gpsIfd != null ) {
			ifd0.getTag( ExifInterface.getTrueTagKey( ExifInterface.TAG_GPS_IFD ) ).setValue( offset );
			offset = calculateOffsetOfIfd( gpsIfd, offset );
		}

		IfdData ifd1 = mExifData.getIfdData( IfdId.TYPE_IFD_1 );
		if( ifd1 != null ) {
			ifd0.setOffsetToNextIfd( offset );
			offset = calculateOffsetOfIfd( ifd1, offset );
		}

		// thumbnail
		if( mExifData.hasCompressedThumbnail() ) {
			ifd1.getTag( ExifInterface.getTrueTagKey( ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT ) ).setValue( offset );
			offset += mExifData.getCompressedThumbnail().length;
		}
		else if( mExifData.hasUncompressedStrip() ) {
			int stripCount = mExifData.getStripCount();
			long[] offsets = new long[stripCount];
			for( int i = 0; i < mExifData.getStripCount(); i++ ) {
				offsets[i] = offset;
				offset += mExifData.getStrip( i ).length;
			}
			ifd1.getTag( ExifInterface.getTrueTagKey( ExifInterface.TAG_STRIP_OFFSETS ) ).setValue( offsets );
		}
		return offset;
	}

	static void writeTagValue( ExifTag tag, OrderedDataOutputStream dataOutputStream ) throws IOException {
		switch( tag.getDataType() ) {
			case ExifTag.TYPE_ASCII:
				byte buf[] = tag.getStringByte();
				if( buf.length == tag.getComponentCount() ) {
					buf[buf.length - 1] = 0;
					dataOutputStream.write( buf );
				}
				else {
					dataOutputStream.write( buf );
					dataOutputStream.write( 0 );
				}
				break;
			case ExifTag.TYPE_LONG:
			case ExifTag.TYPE_UNSIGNED_LONG:
				for( int i = 0, n = tag.getComponentCount(); i < n; i++ ) {
					dataOutputStream.writeInt( (int) tag.getValueAt( i ) );
				}
				break;
			case ExifTag.TYPE_RATIONAL:
			case ExifTag.TYPE_UNSIGNED_RATIONAL:
				for( int i = 0, n = tag.getComponentCount(); i < n; i++ ) {
					dataOutputStream.writeRational( tag.getRational( i ) );
				}
				break;
			case ExifTag.TYPE_UNDEFINED:
			case ExifTag.TYPE_UNSIGNED_BYTE:
				buf = new byte[tag.getComponentCount()];
				tag.getBytes( buf );
				dataOutputStream.write( buf );
				break;
			case ExifTag.TYPE_UNSIGNED_SHORT:
				for( int i = 0, n = tag.getComponentCount(); i < n; i++ ) {
					dataOutputStream.writeShort( (short) tag.getValueAt( i ) );
				}
				break;
		}
	}
}
