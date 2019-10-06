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

package it.sephiroth.android.library.exif2

import android.util.Log
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.*
import kotlin.math.min

internal open class ExifParser
@Throws(IOException::class, ExifInvalidFormatException::class)
private constructor(
	inputStream : InputStream,
	private val mOptions : Int,
	private val mInterface : ExifInterface
) {
	
	// number of tags in the current IFD area.
	private var tagCountInCurrentIfd = 0
	
	/**
	 * the ID of current IFD.
	 *
	 * @see IfdId.TYPE_IFD_0
	 * @see IfdId.TYPE_IFD_1
	 * @see IfdId.TYPE_IFD_GPS
	 * @see IfdId.TYPE_IFD_INTEROPERABILITY
	 * @see IfdId.TYPE_IFD_EXIF
	 */
	var currentIfd : Int = 0
		private set

	/**
	 * If [.next] return [.EVENT_NEW_TAG] or
	 * [.EVENT_VALUE_OF_REGISTERED_TAG], call this function to get the
	 * corresponding tag.
	 *
	 *
	 * For [.EVENT_NEW_TAG], the tag may not contain the value if the size
	 * of the value is greater than 4 bytes. One should call
	 * [ExifTag.hasValue] to check if the tag contains value. If there
	 * is no value,call [.registerForTagValue] to have the parser
	 * emit [.EVENT_VALUE_OF_REGISTERED_TAG] when it reaches the area
	 * pointed by the offset.
	 *
	 *
	 * When [.EVENT_VALUE_OF_REGISTERED_TAG] is emitted, the value of the
	 * tag will have already been read except for tags of undefined type. For
	 * tags of undefined type, call one of the read methods to get the value.
	 *
	 * @see .registerForTagValue
	 * @see .read
	 * @see .read
	 * @see .readLong
	 * @see .readRational
	 * @see .readString
	 * @see .readString
	 */
	var tag : ExifTag? = null
		private set
	
	private var mImageEvent : ImageEvent? = null
	private var mStripSizeTag : ExifTag? = null
	private var mJpegSizeTag : ExifTag? = null
	private var mNeedToParseOffsetsInCurrentIfd : Boolean = false
	private var mDataAboveIfd0 : ByteArray? = null
	private var mIfd0Position : Int = 0
	
	var qualityGuess : Int = 0
		private set
	var imageWidth : Int = 0
		private set
	var imageLength : Int = 0
		private set
	var jpegProcess : Short = 0
		private set
	
	var uncompressedDataPosition = 0
		private set
	
	private val mByteArray = ByteArray(8)
	private val mByteBuffer = ByteBuffer.wrap(mByteArray)
	
	private val isThumbnailRequested : Boolean
		get() = mOptions and ExifInterface.Options.OPTION_THUMBNAIL != 0
	
	/**
	 * When receiving [.EVENT_UNCOMPRESSED_STRIP], call this function to
	 * get the index of this strip.
	 */
	val stripIndex : Int
		get() = mImageEvent?.stripIndex ?: 0
	
	/**
	 * When receiving [.EVENT_UNCOMPRESSED_STRIP], call this function to
	 * get the strip size.
	 */
	val stripSize : Int
		get() = mStripSizeTag?.getValueAt(0)?.toInt() ?: 0
	
	/**
	 * When receiving [.EVENT_COMPRESSED_IMAGE], call this function to get
	 * the image data size.
	 */
	val compressedImageSize : Int
		get() = mJpegSizeTag?.getValueAt(0)?.toInt() ?: 0
	
	/**
	 * Gets the byte order of the current InputStream.
	 */
	val byteOrder : ByteOrder
		get() = mTiffStream.byteOrder
	
	val sections : List<Section>
		get() = mSections
	
	private val mCorrespondingEvent = TreeMap<Int, Any>()
	
	private val mSections = ArrayList<Section>(0)
	
	private var mIfdStartOffset = 0
	
	private val mTiffStream : CountedDataInputStream = seekTiffData(inputStream)
	
	init {
		
		// Log.d( TAG, "sections size: " + mSections.size() );
		
		val tiffStream = mTiffStream
		
		parseTiffHeader(tiffStream)
		
		val offset = tiffStream.readUnsignedInt()
		if(offset > Integer.MAX_VALUE) {
			throw ExifInvalidFormatException("Invalid offset $offset")
		}
		mIfd0Position = offset.toInt()
		currentIfd = IfdId.TYPE_IFD_0
		
		if(isIfdRequested(IfdId.TYPE_IFD_0) || needToParseOffsetsInCurrentIfd()) {
			registerIfd(IfdId.TYPE_IFD_0, offset)
			if(offset != DEFAULT_IFD0_OFFSET.toLong()) {
				val ba = ByteArray(offset.toInt() - DEFAULT_IFD0_OFFSET)
				mDataAboveIfd0 = ba
				read(ba)
			}
		}
	}
	
	private fun readInt(b : ByteArray, @Suppress("SameParameterValue") offset : Int) : Int {
		mByteBuffer.rewind()
		mByteBuffer.put(b, offset, 4)
		mByteBuffer.rewind()
		return mByteBuffer.int
	}
	
	private fun readShort(b : ByteArray, @Suppress("SameParameterValue") offset : Int) : Short {
		mByteBuffer.rewind()
		mByteBuffer.put(b, offset, 2)
		mByteBuffer.rewind()
		return mByteBuffer.short
	}
	
	@Throws(IOException::class, ExifInvalidFormatException::class)
	private fun seekTiffData(inputStream : InputStream) : CountedDataInputStream {
		val dataStream = CountedDataInputStream(inputStream)
		var tiffStream : CountedDataInputStream? = null
		
		var a = dataStream.readUnsignedByte()
		val b = dataStream.readUnsignedByte()
		

		if(a == 137 && b == 80) error("maybe PNG image")

		if(a != 0xFF || b != JpegHeader.TAG_SOI) error("invalid jpeg header")
		
		while(true) {
			val itemlen : Int
			var marker : Int
			
			val got : Int
			val data : ByteArray
			
			var prev = 0
			a = 0
			while(true) {
				marker = dataStream.readUnsignedByte()
				if(marker != 0xff && prev == 0xff) break
				prev = marker
				a ++
			}
			
			if(a > 10) {
				Log.w(TAG, "Extraneous ${a - 1} padding bytes before section $marker")
			}
			
			val section = Section()
			section.type = marker
			
			// Read the length of the section.
			val lh = dataStream.readByte().toInt()
			val ll = dataStream.readByte().toInt()
			itemlen = lh and 0xff shl 8 or (ll and 0xff)
			
			if(itemlen < 2) {
				throw ExifInvalidFormatException("Invalid marker")
			}
			
			section.size = itemlen
			
			data = ByteArray(itemlen)
			data[0] = lh.toByte()
			data[1] = ll.toByte()
			
			// Log.i( TAG, "marker: " + String.format( "0x%2X", marker ) + ": " + itemlen + ", position: " + dataStream.getReadByteCount() + ", available: " + dataStream.available() );
			// got = dataStream.read( data, 2, itemlen-2 );
			
			got = readBytes(dataStream, data, 2, itemlen - 2)
			
			if(got != itemlen - 2) {
				throw ExifInvalidFormatException("Premature end of file? Expecting " + (itemlen - 2) + ", received " + got)
			}
			
			section.data = data
			
			var ignore = false
			
			when(marker) {
				JpegHeader.TAG_M_SOS -> {
					// stop before hitting compressed data
					mSections.add(section)
					uncompressedDataPosition = dataStream.readByteCount
					return tiffStream !!
				}
				
				JpegHeader.TAG_M_DQT ->
					// Use for jpeg quality guessing
					process_M_DQT(data)
				
				JpegHeader.TAG_M_DHT -> {
				}
				
				// in case it's a tables-only JPEG stream
				JpegHeader.TAG_M_EOI -> {
					error("\"No image in jpeg!\"")
				}
				
				JpegHeader.TAG_M_COM ->
					// Comment section
					ignore = true
				
				JpegHeader.TAG_M_JFIF -> if(itemlen < 16) {
					ignore = true
				}
				
				JpegHeader.TAG_M_IPTC -> {
				}
				
				JpegHeader.TAG_M_SOF0, JpegHeader.TAG_M_SOF1, JpegHeader.TAG_M_SOF2, JpegHeader.TAG_M_SOF3, JpegHeader.TAG_M_SOF5, JpegHeader.TAG_M_SOF6, JpegHeader.TAG_M_SOF7, JpegHeader.TAG_M_SOF9, JpegHeader.TAG_M_SOF10, JpegHeader.TAG_M_SOF11, JpegHeader.TAG_M_SOF13, JpegHeader.TAG_M_SOF14, JpegHeader.TAG_M_SOF15 -> process_M_SOFn(
					data,
					marker
				)
				
				JpegHeader.TAG_M_EXIF -> if(itemlen >= 8) {
					val header = readInt(data, 2)
					val headerTail = readShort(data, 6)
					// header = Exif, headerTail=\0\0
					if(header == EXIF_HEADER && headerTail == EXIF_HEADER_TAIL) {
						tiffStream =
							CountedDataInputStream(ByteArrayInputStream(data, 8, itemlen - 8))
						tiffStream.end = itemlen - 6
						ignore = false
					} else {
						Log.v(TAG, "Image cotains XMP section")
					}
				}
				
				else -> Log.w(
					TAG,
					"Unknown marker: " + String.format("0x%2X", marker) + ", length: " + itemlen
				)
			}
			
			if(! ignore) {
				// Log.d( TAG, "adding section with size: " + section.size );
				mSections.add(section)
			} else {
				Log.v(
					TAG,
					"ignoring marker: " + String.format("0x%2X", marker) + ", length: " + itemlen
				)
			}
		}
	}
	
	/**
	 * Using this instead of the default [java.io.InputStream.read] because
	 * on remote input streams reading large amount of data can fail
	 *
	 * @param dataStream
	 * @param data
	 * @param offsetArg
	 * @param length
	 * @return
	 * @throws IOException
	 */
	@Throws(IOException::class)
	private fun readBytes(
		dataStream : InputStream,
		data : ByteArray,
		@Suppress("SameParameterValue") offsetArg : Int,
		length : Int
	) : Int {
		var offset = offsetArg
		var count = 0
		var n : Int
		var max_length = min(1024, length)
		while(true) {
			n = dataStream.read(data, offset, max_length)
			if(n <= 0) break
			count += n
			offset += n
			max_length = min(max_length, length - count)
		}
		return count
	}
	
	private fun process_M_SOFn(data : ByteArray, marker : Int) {
		if(data.size > 7) {
			//int data_precision = data[2] & 0xff;
			//int num_components = data[7] & 0xff;
			imageLength = Get16m(data, 3)
			imageWidth = Get16m(data, 5)
		}
		jpegProcess = marker.toShort()
	}
	
	private fun process_M_DQT(data : ByteArray) {
		var a = 2
		var c : Int
		var tableindex : Int
		var coefindex : Int
		var cumsf = 0.0
		var reftable : IntArray? = null
		var allones = 1
		
		while(a < data.size) {
			c = data[a ++].toInt()
			tableindex = c and 0x0f
			
			if(tableindex < 2) {
				reftable = deftabs[tableindex]
			}
			
			// Read in the table, compute statistics relative to reference table
			coefindex = 0
			while(coefindex < 64) {
				val `val` : Int
				if(c shr 4 != 0) {
					var temp : Int
					temp = data[a ++].toInt()
					temp *= 256
					`val` = data[a ++].toInt() + temp
				} else {
					`val` = data[a ++].toInt()
				}
				if(reftable != null) {
					val x : Double
					// scaling factor in percent
					x = 100.0 * `val`.toDouble() / reftable[coefindex].toDouble()
					cumsf += x
					// separate check for all-ones table (Q 100)
					if(`val` != 1) allones = 0
				}
				coefindex ++
			}
			// Print summary stats
			if(reftable != null) { // terse output includes quality
				val qual : Double
				cumsf /= 64.0    // mean scale factor
				
				qual = when {
					allones != 0 -> 100.0 // special case for all-ones table
					cumsf <= 100.0 -> (200.0 - cumsf) / 2.0
					else -> 5000.0 / cumsf
				}
				
				if(tableindex == 0) {
					qualityGuess = (qual + 0.5).toInt()
					// Log.v( TAG, "quality guess: " + mQualityGuess );
				}
			}
		}
	}
	
	@Throws(IOException::class, ExifInvalidFormatException::class)
	private fun parseTiffHeader(stream : CountedDataInputStream) {
		
		stream.byteOrder = when(stream.readShort()) {
			LITTLE_ENDIAN_TAG -> ByteOrder.LITTLE_ENDIAN
			BIG_ENDIAN_TAG -> ByteOrder.BIG_ENDIAN
			else -> throw ExifInvalidFormatException("Invalid TIFF header")
		}
		
		if(stream.readShort() != TIFF_HEADER_TAIL) {
			throw ExifInvalidFormatException("Invalid TIFF header")
		}
	}
	
	private fun isIfdRequested(ifdType : Int) : Boolean {
		when(ifdType) {
			IfdId.TYPE_IFD_0 -> return mOptions and ExifInterface.Options.OPTION_IFD_0 != 0
			IfdId.TYPE_IFD_1 -> return mOptions and ExifInterface.Options.OPTION_IFD_1 != 0
			IfdId.TYPE_IFD_EXIF -> return mOptions and ExifInterface.Options.OPTION_IFD_EXIF != 0
			IfdId.TYPE_IFD_GPS -> return mOptions and ExifInterface.Options.OPTION_IFD_GPS != 0
			IfdId.TYPE_IFD_INTEROPERABILITY -> return mOptions and ExifInterface.Options.OPTION_IFD_INTEROPERABILITY != 0
		}
		return false
	}
	
	private fun needToParseOffsetsInCurrentIfd() : Boolean {
		return when(currentIfd) {
			
			IfdId.TYPE_IFD_0 ->
				isIfdRequested(IfdId.TYPE_IFD_EXIF) ||
					isIfdRequested(IfdId.TYPE_IFD_GPS) ||
					isIfdRequested(IfdId.TYPE_IFD_INTEROPERABILITY) ||
					isIfdRequested(IfdId.TYPE_IFD_1)
			
			IfdId.TYPE_IFD_1 -> isThumbnailRequested
			
			IfdId.TYPE_IFD_EXIF ->
				// The offset to interoperability IFD is located in Exif IFD
				isIfdRequested(IfdId.TYPE_IFD_INTEROPERABILITY)
			
			else -> false
		}
	}
	
	private fun registerIfd(ifdType : Int, offset : Long) {
		// Cast unsigned int to int since the offset is always smaller
		// than the size of M_EXIF (65536)
		mCorrespondingEvent[offset.toInt()] = IfdEvent(ifdType, isIfdRequested(ifdType))
	}
	
	/**
	 * Equivalent to read(buffer, 0, buffer.length).
	 */
	@Throws(IOException::class)
	fun read(buffer : ByteArray) : Int = mTiffStream.read(buffer)
	
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
	 * @see .EVENT_START_OF_IFD
	 * @see .EVENT_NEW_TAG
	 * @see .EVENT_VALUE_OF_REGISTERED_TAG
	 * @see .EVENT_COMPRESSED_IMAGE
	 * @see .EVENT_UNCOMPRESSED_STRIP
	 * @see .EVENT_END
	 */
	@Throws(IOException::class, ExifInvalidFormatException::class)
	operator fun next() : Int {
		
		val offset = mTiffStream.readByteCount
		val endOfTags = mIfdStartOffset + OFFSET_SIZE + TAG_SIZE * tagCountInCurrentIfd
		if(offset < endOfTags) {
			val tag = readTag()
			this.tag = tag
			if(tag == null) {
				return next()
			} else if(mNeedToParseOffsetsInCurrentIfd) {
				checkOffsetOrImageTag(tag)
			}
			return EVENT_NEW_TAG
		} else if(offset == endOfTags) {
			// There is a link to ifd1 at the end of ifd0
			if(currentIfd == IfdId.TYPE_IFD_0) {
				val ifdOffset = readUnsignedLong()
				if(isIfdRequested(IfdId.TYPE_IFD_1) || isThumbnailRequested) {
					if(ifdOffset != 0L) {
						registerIfd(IfdId.TYPE_IFD_1, ifdOffset)
					}
				}
			} else {
				var offsetSize = 4
				// Some camera models use invalid length of the offset
				if(mCorrespondingEvent.size > 0) {
					val firstEntry = mCorrespondingEvent.firstEntry() !!
					offsetSize = firstEntry.key - mTiffStream.readByteCount
				}
				if(offsetSize < 4) {
					Log.w(TAG, "Invalid size of link to next IFD: $offsetSize")
				} else {
					val ifdOffset = readUnsignedLong()
					if(ifdOffset != 0L) {
						Log.w(TAG, "Invalid link to next IFD: $ifdOffset")
					}
				}
			}
		}
		while(mCorrespondingEvent.size != 0) {
			val entry = mCorrespondingEvent.pollFirstEntry() !!
			val event = entry.value
			try {
				// Log.v(TAG, "skipTo: " + entry.getKey());
				skipTo(entry.key)
			} catch(e : IOException) {
				Log.w(
					TAG,
					"Failed to skip to data at: ${entry.key} for ${event.javaClass.name}, the file may be broken."
				)
				continue
			}
			
			if(event is IfdEvent) {
				currentIfd = event.ifd
				tagCountInCurrentIfd = mTiffStream.readUnsignedShort()
				mIfdStartOffset = entry.key
				
				if(tagCountInCurrentIfd * TAG_SIZE + mIfdStartOffset + OFFSET_SIZE > mTiffStream.end) {
					Log.w(TAG, "Invalid size of IFD $currentIfd")
					return EVENT_END
				}
				
				mNeedToParseOffsetsInCurrentIfd = needToParseOffsetsInCurrentIfd()
				if(event.isRequested) {
					return EVENT_START_OF_IFD
				} else {
					skipRemainingTagsInCurrentIfd()
				}
			} else if(event is ImageEvent) {
				mImageEvent = event
				return event.type
			} else {
				val tagEvent = event as ExifTagEvent
				val tag = tagEvent.tag
				this.tag = tag
				if(tag.dataType != ExifTag.TYPE_UNDEFINED) {
					readFullTagValue(tag)
					checkOffsetOrImageTag(tag)
				}
				if(tagEvent.isRequested) {
					return EVENT_VALUE_OF_REGISTERED_TAG
				}
			}
		}
		return EVENT_END
	}
	
	/**
	 * Skips the tags area of current IFD, if the parser is not in the tag area,
	 * nothing will happen.
	 *
	 * @throws java.io.IOException
	 * @throws ExifInvalidFormatException
	 */
	@Throws(IOException::class, ExifInvalidFormatException::class)
	protected fun skipRemainingTagsInCurrentIfd() {
		
		val endOfTags = mIfdStartOffset + OFFSET_SIZE + TAG_SIZE * tagCountInCurrentIfd
		var offset = mTiffStream.readByteCount
		if(offset > endOfTags) return
		
		if(mNeedToParseOffsetsInCurrentIfd) {
			while(offset < endOfTags) {
				val tag = readTag()
				this.tag = tag
				offset += TAG_SIZE
				if(tag == null) {
					continue
				}
				checkOffsetOrImageTag(tag)
			}
		} else {
			skipTo(endOfTags)
		}
		val ifdOffset = readUnsignedLong()
		// For ifd0, there is a link to ifd1 in the end of all tags
		if(currentIfd == IfdId.TYPE_IFD_0 && (isIfdRequested(IfdId.TYPE_IFD_1) || isThumbnailRequested)) {
			if(ifdOffset > 0) {
				registerIfd(IfdId.TYPE_IFD_1, ifdOffset)
			}
		}
	}
	
	@Throws(IOException::class)
	private fun skipTo(offset : Int) {
		mTiffStream.skipTo(offset.toLong())
		
		// Log.v(TAG, "available: " + mTiffStream.available() );
		while(! mCorrespondingEvent.isEmpty() && mCorrespondingEvent.firstKey() < offset) {
			mCorrespondingEvent.pollFirstEntry()
		}
	}
	
	/**
	 * When getting [.EVENT_NEW_TAG] in the tag area of IFD, the tag may
	 * not contain the value if the size of the value is greater than 4 bytes.
	 * When the value is not available here, call this method so that the parser
	 * will emit [.EVENT_VALUE_OF_REGISTERED_TAG] when it reaches the area
	 * where the value is located.
	 *
	 * @see .EVENT_VALUE_OF_REGISTERED_TAG
	 */
	fun registerForTagValue(tag : ExifTag) {
		if(tag.offset >= mTiffStream.readByteCount) {
			mCorrespondingEvent[tag.offset] = ExifTagEvent(tag, true)
		}
	}
	
	private fun registerCompressedImage(offset : Long) {
		mCorrespondingEvent[offset.toInt()] = ImageEvent(EVENT_COMPRESSED_IMAGE)
	}
	
	private fun registerUncompressedStrip(stripIndex : Int, offset : Long) {
		mCorrespondingEvent[offset.toInt()] = ImageEvent(EVENT_UNCOMPRESSED_STRIP, stripIndex)
	}
	
	@Throws(IOException::class, ExifInvalidFormatException::class)
	private fun readTag() : ExifTag? {
		
		val tagId = mTiffStream.readShort()
		val dataFormat = mTiffStream.readShort()
		val numOfComp = mTiffStream.readUnsignedInt()
		if(numOfComp > Integer.MAX_VALUE) {
			throw ExifInvalidFormatException("Number of component is larger then Integer.MAX_VALUE")
		}
		// Some invalid image file contains invalid data type. Ignore those tags
		if(! ExifTag.isValidType(dataFormat)) {
			Log.w(TAG, String.format("Tag %04x: Invalid data type %d", tagId, dataFormat))
			mTiffStream.skip(4)
			return null
		}
		// TODO: handle numOfComp overflow
		val tag = ExifTag(
			tagId,
			dataFormat,
			numOfComp.toInt(),
			currentIfd,
			numOfComp.toInt() != ExifTag.SIZE_UNDEFINED
		)
		val dataSize = tag.dataSize
		if(dataSize > 4) {
			val offset = mTiffStream.readUnsignedInt()
			if(offset > Integer.MAX_VALUE) {
				throw ExifInvalidFormatException("offset is larger then Integer.MAX_VALUE")
			}
			// Some invalid images put some undefined data before IFD0.
			// Read the data here.
			if(offset < mIfd0Position && dataFormat == ExifTag.TYPE_UNDEFINED) {
				val buf = ByteArray(numOfComp.toInt())
				System.arraycopy(
					mDataAboveIfd0 !!,
					offset.toInt() - DEFAULT_IFD0_OFFSET,
					buf,
					0,
					numOfComp.toInt()
				)
				tag.setValue(buf)
			} else {
				tag.offset = offset.toInt()
			}
		} else {
			val defCount = tag.hasDefinedCount
			// Set defined count to 0 so we can add \0 to non-terminated strings
			tag.hasDefinedCount = false
			// Read value
			readFullTagValue(tag)
			tag.hasDefinedCount = defCount
			mTiffStream.skip((4 - dataSize).toLong())
			// Set the offset to the position of value.
			tag.offset = mTiffStream.readByteCount - 4
		}
		return tag
	}
	
	/**
	 * Check the tag, if the tag is one of the offset tag that points to the IFD
	 * or image the caller is interested in, register the IFD or image.
	 */
	private fun checkOffsetOrImageTag(tag : ExifTag) {
		// Some invalid formattd image contains tag with 0 size.
		if(tag.componentCount == 0) {
			return
		}
		val tid = tag.tagId
		val ifd = tag.ifd
		if(tid == TAG_EXIF_IFD && checkAllowed(ifd, ExifInterface.TAG_EXIF_IFD)) {
			if(isIfdRequested(IfdId.TYPE_IFD_EXIF) || isIfdRequested(IfdId.TYPE_IFD_INTEROPERABILITY)) {
				registerIfd(IfdId.TYPE_IFD_EXIF, tag.getValueAt(0))
			}
		} else if(tid == TAG_GPS_IFD && checkAllowed(ifd, ExifInterface.TAG_GPS_IFD)) {
			if(isIfdRequested(IfdId.TYPE_IFD_GPS)) {
				registerIfd(IfdId.TYPE_IFD_GPS, tag.getValueAt(0))
			}
		} else if(tid == TAG_INTEROPERABILITY_IFD && checkAllowed(
				ifd,
				ExifInterface.TAG_INTEROPERABILITY_IFD
			)) {
			if(isIfdRequested(IfdId.TYPE_IFD_INTEROPERABILITY)) {
				registerIfd(IfdId.TYPE_IFD_INTEROPERABILITY, tag.getValueAt(0))
			}
		} else if(tid == TAG_JPEG_INTERCHANGE_FORMAT && checkAllowed(
				ifd,
				ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT
			)) {
			if(isThumbnailRequested) {
				registerCompressedImage(tag.getValueAt(0))
			}
		} else if(tid == TAG_JPEG_INTERCHANGE_FORMAT_LENGTH && checkAllowed(
				ifd,
				ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH
			)) {
			if(isThumbnailRequested) {
				mJpegSizeTag = tag
			}
		} else if(tid == TAG_STRIP_OFFSETS && checkAllowed(ifd, ExifInterface.TAG_STRIP_OFFSETS)) {
			if(isThumbnailRequested) {
				if(tag.hasValue()) {
					for(i in 0 until tag.componentCount) {
						if(tag.dataType == ExifTag.TYPE_UNSIGNED_SHORT) {
							registerUncompressedStrip(i, tag.getValueAt(i))
						} else {
							registerUncompressedStrip(i, tag.getValueAt(i))
						}
					}
				} else {
					mCorrespondingEvent[tag.offset] = ExifTagEvent(tag, false)
				}
			}
		} else if(tid == TAG_STRIP_BYTE_COUNTS && checkAllowed(
				ifd,
				ExifInterface.TAG_STRIP_BYTE_COUNTS
			) && isThumbnailRequested && tag.hasValue()) {
			mStripSizeTag = tag
		}
	}
	
	fun isDefinedTag(ifdId : Int, tagId : Int) : Boolean {
		return mInterface.tagInfo.get(
			ExifInterface.defineTag(
				ifdId,
				tagId.toShort()
			)
		) != ExifInterface.DEFINITION_NULL
	}
	
	private fun checkAllowed(ifd : Int, tagId : Int) : Boolean {
		val info = mInterface.tagInfo.get(tagId)
		return if(info == ExifInterface.DEFINITION_NULL) {
			false
		} else ExifInterface.isIfdAllowed(info, ifd)
	}
	
	@Throws(IOException::class)
	fun readFullTagValue(tag : ExifTag) {
		
		// Some invalid images contains tags with wrong size, check it here
		val type = tag.dataType
		val componentCount = tag.componentCount
		
		// sanity check
		if(componentCount >= 0x66000000) throw IOException("size out of bounds")
		
		if(type == ExifTag.TYPE_ASCII || type == ExifTag.TYPE_UNDEFINED ||
			type == ExifTag.TYPE_UNSIGNED_BYTE) {
			var size = tag.componentCount
			if(mCorrespondingEvent.size > 0) {
				val firstEntry = mCorrespondingEvent.firstEntry() !!
				if(firstEntry.key < mTiffStream.readByteCount + size) {
					val event = firstEntry.value
					if(event is ImageEvent) {
						// Tag value overlaps thumbnail, ignore thumbnail.
						Log.w(TAG, "Thumbnail overlaps value for tag: \n$tag")
						val entry = mCorrespondingEvent.pollFirstEntry() !!
						Log.w(TAG, "Invalid thumbnail offset: " + entry.key)
					} else {
						// Tag value overlaps another tag, shorten count
						if(event is IfdEvent) {
							Log.w(TAG, "Ifd ${event.ifd} overlaps value for tag: \n$tag")
						} else if(event is ExifTagEvent) {
							Log.w(
								TAG,
								"Tag value for tag: \n${event.tag} overlaps value for tag: \n$tag"
							)
						}
						size = firstEntry.key - mTiffStream.readByteCount
						Log.w(TAG, "Invalid size of tag: \n$tag setting count to: $size")
						tag.forceSetComponentCount(size)
					}
				}
			}
		}
		when(tag.dataType) {
			ExifTag.TYPE_UNSIGNED_BYTE, ExifTag.TYPE_UNDEFINED ->
				tag.setValue(ByteArray(componentCount).also { read(it) })
			
			ExifTag.TYPE_ASCII ->
				tag.setValue(readString(componentCount))
			
			ExifTag.TYPE_UNSIGNED_SHORT ->
				tag.setValue(IntArray(componentCount) { readUnsignedShort() })
			
			ExifTag.TYPE_LONG ->
				tag.setValue(IntArray(componentCount) { readLong().toInt() })
			ExifTag.TYPE_UNSIGNED_LONG ->
				tag.setValue(LongArray(componentCount) { readUnsignedLong() })
			
			ExifTag.TYPE_RATIONAL ->
				tag.setValue(Array(componentCount) { readRational() })
			ExifTag.TYPE_UNSIGNED_RATIONAL ->
				tag.setValue(Array(componentCount) { readUnsignedRational() })
			
		}
		
		// Log.v( TAG, "\n" + tag.toString() );
	}
	
	/**
	 * Reads bytes from the InputStream.
	 */
	@Throws(IOException::class)
	protected fun read(buffer : ByteArray, offset : Int, length : Int) : Int =
		mTiffStream.read(buffer, offset, length)
	
	/**
	 * Reads a String from the InputStream with US-ASCII charset. The parser
	 * will read n bytes and convert it to ascii string. This is used for
	 * reading values of type [ExifTag.TYPE_ASCII].
	 */
	
	/**
	 * Reads a String from the InputStream with the given charset. The parser
	 * will read n bytes and convert it to string. This is used for reading
	 * values of type [ExifTag.TYPE_ASCII].
	 */
	@Throws(IOException::class)
	@JvmOverloads
	protected fun readString(n : Int, charset : Charset = US_ASCII) : String =
		when {
			n <= 0 -> ""
			else -> mTiffStream.readString(n, charset)
		}
	
	/**
	 * Reads value of type [ExifTag.TYPE_UNSIGNED_SHORT] from the
	 * InputStream.
	 */
	@Throws(IOException::class)
	protected fun readUnsignedShort() : Int =
		mTiffStream.readShort().toInt() and 0xffff
	
	/**
	 * Reads value of type [ExifTag.TYPE_UNSIGNED_LONG] from the
	 * InputStream.
	 */
	@Throws(IOException::class)
	protected fun readUnsignedLong() : Long {
		return readLong() and 0xffffffffL
	}
	
	/**
	 * Reads value of type [ExifTag.TYPE_UNSIGNED_RATIONAL] from the
	 * InputStream.
	 */
	@Throws(IOException::class)
	protected fun readUnsignedRational() : Rational {
		val nomi = readUnsignedLong()
		val denomi = readUnsignedLong()
		return Rational(nomi, denomi)
	}
	
	/**
	 * Reads value of type [ExifTag.TYPE_LONG] from the InputStream.
	 */
	@Throws(IOException::class)
	protected fun readLong() : Long =
		mTiffStream.readInt().toLong()
	
	/**
	 * Reads value of type [ExifTag.TYPE_RATIONAL] from the InputStream.
	 */
	@Throws(IOException::class)
	protected fun readRational() : Rational {
		val nomi = readLong()
		val denomi = readLong()
		return Rational(nomi, denomi)
	}
	
	private class ImageEvent {
		internal var stripIndex : Int = 0
		internal var type : Int = 0
		
		internal constructor(type : Int) {
			this.stripIndex = 0
			this.type = type
		}
		
		internal constructor(type : Int, stripIndex : Int) {
			this.type = type
			this.stripIndex = stripIndex
		}
	}
	
	private class IfdEvent internal constructor(
		internal var ifd : Int,
		internal var isRequested : Boolean
	)
	
	private class ExifTagEvent internal constructor(
		internal var tag : ExifTag,
		internal var isRequested : Boolean
	)
	
	class Section {
		internal var size : Int = 0
		internal var type : Int = 0
		internal var data : ByteArray? = null
	}
	
	companion object {
		private const val TAG = "ExifParser"
		
		/**
		 * When the parser reaches a new IFD area. Call [.getCurrentIfd] to
		 * know which IFD we are in.
		 */
		const val EVENT_START_OF_IFD = 0
		/**
		 * When the parser reaches a new tag. Call [.getTag]to get the
		 * corresponding tag.
		 */
		const val EVENT_NEW_TAG = 1
		/**
		 * When the parser reaches the value area of tag that is registered by
		 * [.registerForTagValue] previously. Call [.getTag]
		 * to get the corresponding tag.
		 */
		const val EVENT_VALUE_OF_REGISTERED_TAG = 2
		/**
		 * When the parser reaches the compressed image area.
		 */
		const val EVENT_COMPRESSED_IMAGE = 3
		/**
		 * When the parser reaches the uncompressed image strip. Call
		 * [.getStripIndex] to get the index of the strip.
		 *
		 * @see .getStripIndex
		 */
		const val EVENT_UNCOMPRESSED_STRIP = 4
		/**
		 * When there is nothing more to parse.
		 */
		const val EVENT_END = 5
		
		protected const val EXIF_HEADER = 0x45786966 // EXIF header "Exif"
		protected const val EXIF_HEADER_TAIL = 0x0000.toShort() // EXIF header in M_EXIF
		// TIFF header
		protected const val LITTLE_ENDIAN_TAG = 0x4949.toShort() // "II"
		protected const val BIG_ENDIAN_TAG = 0x4d4d.toShort() // "MM"
		protected const val TIFF_HEADER_TAIL : Short = 0x002A
		protected const val TAG_SIZE = 12
		protected const val OFFSET_SIZE = 2
		protected const val DEFAULT_IFD0_OFFSET = 8
		private val US_ASCII = Charset.forName("US-ASCII")
		private val TAG_EXIF_IFD = ExifInterface.getTrueTagKey(ExifInterface.TAG_EXIF_IFD)
		private val TAG_GPS_IFD = ExifInterface.getTrueTagKey(ExifInterface.TAG_GPS_IFD)
		private val TAG_INTEROPERABILITY_IFD =
			ExifInterface.getTrueTagKey(ExifInterface.TAG_INTEROPERABILITY_IFD)
		private val TAG_JPEG_INTERCHANGE_FORMAT =
			ExifInterface.getTrueTagKey(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT)
		private val TAG_JPEG_INTERCHANGE_FORMAT_LENGTH =
			ExifInterface.getTrueTagKey(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH)
		private val TAG_STRIP_OFFSETS = ExifInterface.getTrueTagKey(ExifInterface.TAG_STRIP_OFFSETS)
		private val TAG_STRIP_BYTE_COUNTS =
			ExifInterface.getTrueTagKey(ExifInterface.TAG_STRIP_BYTE_COUNTS)
		
		private val std_luminance_quant_tbl : IntArray
		private val std_chrominance_quant_tbl : IntArray
		val deftabs : Array<IntArray>
		
		init {
			std_luminance_quant_tbl = intArrayOf(
				16,
				11,
				12,
				14,
				12,
				10,
				16,
				14,
				13,
				14,
				18,
				17,
				16,
				19,
				24,
				40,
				26,
				24,
				22,
				22,
				24,
				49,
				35,
				37,
				29,
				40,
				58,
				51,
				61,
				60,
				57,
				51,
				56,
				55,
				64,
				72,
				92,
				78,
				64,
				68,
				87,
				69,
				55,
				56,
				80,
				109,
				81,
				87,
				95,
				98,
				103,
				104,
				103,
				62,
				77,
				113,
				121,
				112,
				100,
				120,
				92,
				101,
				103,
				99
			)
			
			std_chrominance_quant_tbl = intArrayOf(
				17,
				18,
				18,
				24,
				21,
				24,
				47,
				26,
				26,
				47,
				99,
				66,
				56,
				66,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99,
				99
			)
			
			deftabs = arrayOf(std_luminance_quant_tbl, std_chrominance_quant_tbl)
		}
		
		fun Get16m(data : ByteArray, position : Int) : Int {
			val b1 = data[position].toInt() and 0xFF shl 8
			val b2 = data[position + 1].toInt() and 0xFF
			return b1 or b2
		}
		
		/**
		 * Parses the the given InputStream with the given options
		 *
		 * @throws java.io.IOException
		 * @throws ExifInvalidFormatException
		 */
		@Throws(IOException::class, ExifInvalidFormatException::class)
		fun parse(inputStream : InputStream, options : Int, iRef : ExifInterface) : ExifParser =
			ExifParser(inputStream, options, iRef)
	}
}