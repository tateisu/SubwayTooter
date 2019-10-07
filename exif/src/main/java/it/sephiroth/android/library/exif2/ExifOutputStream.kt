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
import it.sephiroth.android.library.exif2.utils.OrderedDataOutputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

@Suppress("unused")
internal class ExifOutputStream(
	
	private val mInterface : ExifInterface,
	
	// the Exif header to be written into the JPEG file.
	private val exifData : ExifData
) {
	
	private val mBuffer = ByteBuffer.allocate(4)
	
	private fun requestByteToBuffer(
		requestByteCount : Int, buffer : ByteArray, offset : Int, length : Int
	) : Int {
		val byteNeeded = requestByteCount - mBuffer.position()
		val byteToRead = if(length > byteNeeded) byteNeeded else length
		mBuffer.put(buffer, offset, byteToRead)
		return byteToRead
	}
	
	@Throws(IOException::class)
	fun writeExifData(out : OutputStream) {
		Log.v(TAG, "Writing exif data...")
		
		val nullTags = stripNullValueTags(exifData)
		createRequiredIfdAndTag()
		val exifSize = calculateAllOffset()
		// Log.i(TAG, "exifSize: " + (exifSize + 8));
		if(exifSize + 8 > MAX_EXIF_SIZE) {
			throw IOException("Exif header is too large (>64Kb)")
		}
		
		val outputStream = BufferedOutputStream(out, STREAMBUFFER_SIZE)
		val dataOutputStream =
			OrderedDataOutputStream(outputStream)
		
		dataOutputStream.setByteOrder(ByteOrder.BIG_ENDIAN)
		
		dataOutputStream.write(0xFF)
		dataOutputStream.write(JpegHeader.TAG_M_EXIF)
		dataOutputStream.writeShort((exifSize + 8).toShort())
		dataOutputStream.writeInt(EXIF_HEADER)
		dataOutputStream.writeShort(0x0000.toShort())
		if(exifData.byteOrder == ByteOrder.BIG_ENDIAN) {
			dataOutputStream.writeShort(TIFF_BIG_ENDIAN)
		} else {
			dataOutputStream.writeShort(TIFF_LITTLE_ENDIAN)
		}
		dataOutputStream.setByteOrder(exifData.byteOrder)
		dataOutputStream.writeShort(TIFF_HEADER)
		dataOutputStream.writeInt(8)
		writeAllTags(dataOutputStream)
		
		writeThumbnail(dataOutputStream)
		
		for(t in nullTags) {
			exifData.addTag(t)
		}
		
		dataOutputStream.flush()
	}
	
	// strip tags that has null value
	// return list of removed tags
	private fun stripNullValueTags(data : ExifData) =
		ArrayList<ExifTag>()
			.apply {
				for(t in data.allTags) {
					if(t.getValue() == null && ! ExifInterface.isOffsetTag(t.tagId)) {
						data.removeTag(t.tagId, t.ifd)
						add(t)
					}
				}
			}
	
	@Throws(IOException::class)
	private fun writeThumbnail(dataOutputStream : OrderedDataOutputStream) {
		val compressedThumbnail = exifData.compressedThumbnail
		if(compressedThumbnail != null) {
			Log.d(TAG, "writing thumbnail..")
			dataOutputStream.write(compressedThumbnail)
		} else {
			val stripList = exifData.stripList
			if(stripList != null) {
				Log.d(TAG, "writing uncompressed strip..")
				stripList.forEach {
					dataOutputStream.write(it)
				}
			}
		}
	}
	
	@Throws(IOException::class)
	private fun writeAllTags(dataOutputStream : OrderedDataOutputStream) {
		writeIfd(exifData.getIfdData(IfdData.TYPE_IFD_0), dataOutputStream)
		writeIfd(exifData.getIfdData(IfdData.TYPE_IFD_EXIF), dataOutputStream)
		writeIfd(exifData.getIfdData(IfdData.TYPE_IFD_INTEROPERABILITY), dataOutputStream)
		writeIfd(exifData.getIfdData(IfdData.TYPE_IFD_GPS), dataOutputStream)
		writeIfd(exifData.getIfdData(IfdData.TYPE_IFD_1), dataOutputStream)
	}
	
	@Throws(IOException::class)
	private fun writeIfd(ifd : IfdData?, dataOutputStream : OrderedDataOutputStream) {
		ifd ?: return
		val tags = ifd.allTagsCollection
		dataOutputStream.writeShort(tags.size.toShort())
		for(tag in tags) {
			dataOutputStream.writeShort(tag.tagId)
			dataOutputStream.writeShort(tag.dataType)
			dataOutputStream.writeInt(tag.componentCount)
			// Log.v( TAG, "\n" + tag.toString() );
			if(tag.dataSize > 4) {
				dataOutputStream.writeInt(tag.offset)
			} else {
				writeTagValue(tag, dataOutputStream)
				var i = 0
				val n = 4 - tag.dataSize
				while(i < n) {
					dataOutputStream.write(0)
					i ++
				}
			}
		}
		dataOutputStream.writeInt(ifd.offsetToNextIfd)
		for(tag in tags) {
			if(tag.dataSize > 4) {
				writeTagValue(tag, dataOutputStream)
			}
		}
	}
	
	private fun calculateOffsetOfIfd(ifd : IfdData, offsetArg : Int) : Int {
		var offset = offsetArg
		offset += 2 + ifd.tagCount * TAG_SIZE + 4
		
		for(tag in ifd.allTagsCollection) {
			if(tag.dataSize > 4) {
				tag.offset = offset
				offset += tag.dataSize
			}
		}
		return offset
	}
	
	@Throws(IOException::class)
	private fun createRequiredIfdAndTag() {
		
		// IFD0 is required for all file
		var ifd0 = exifData.getIfdData(IfdData.TYPE_IFD_0)
		if(ifd0 == null) {
			ifd0 = IfdData(IfdData.TYPE_IFD_0)
			exifData.addIfdData(ifd0)
		}
		
		val exifOffsetTag = mInterface.buildUninitializedTag(ExifInterface.TAG_EXIF_IFD)
			?: throw IOException("No definition for crucial exif tag: " + ExifInterface.TAG_EXIF_IFD)
		ifd0.setTag(exifOffsetTag)
		
		// Exif IFD is required for all files.
		var exifIfd = exifData.getIfdData(IfdData.TYPE_IFD_EXIF)
		if(exifIfd == null) {
			exifIfd = IfdData(IfdData.TYPE_IFD_EXIF)
			exifData.addIfdData(exifIfd)
		}
		
		// GPS IFD
		val gpsIfd = exifData.getIfdData(IfdData.TYPE_IFD_GPS)
		if(gpsIfd != null) {
			val gpsOffsetTag = mInterface.buildUninitializedTag(ExifInterface.TAG_GPS_IFD)
				?: throw IOException("No definition for crucial exif tag: ${ExifInterface.TAG_GPS_IFD}")
			ifd0.setTag(gpsOffsetTag)
		}
		
		// Interoperability IFD
		val interIfd = exifData.getIfdData(IfdData.TYPE_IFD_INTEROPERABILITY)
		if(interIfd != null) {
			val interOffsetTag =
				mInterface.buildUninitializedTag(ExifInterface.TAG_INTEROPERABILITY_IFD)
					?: throw IOException("No definition for crucial exif tag: ${ExifInterface.TAG_INTEROPERABILITY_IFD}")
			exifIfd.setTag(interOffsetTag)
		}
		
		var ifd1 = exifData.getIfdData(IfdData.TYPE_IFD_1)
		
		// thumbnail
		val compressedThumbnail = exifData.compressedThumbnail
		val stripList = exifData.stripList
		when {
			
			compressedThumbnail != null -> {
				if(ifd1 == null) {
					ifd1 = IfdData(IfdData.TYPE_IFD_1)
					exifData.addIfdData(ifd1)
				}
				
				val offsetTag =
					mInterface.buildUninitializedTag(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT)
						?: throw IOException("No definition for crucial exif tag: ${ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT}")
				
				ifd1.setTag(offsetTag)
				val lengthTag =
					mInterface.buildUninitializedTag(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH)
						?: throw IOException("No definition for crucial exif tag: ${ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH}")
				
				lengthTag.setValue(compressedThumbnail.size)
				ifd1.setTag(lengthTag)
				
				// Get rid of tags for uncompressed if they exist.
				ifd1.removeTag(ExifInterface.getTrueTagKey(ExifInterface.TAG_STRIP_OFFSETS))
				ifd1.removeTag(ExifInterface.getTrueTagKey(ExifInterface.TAG_STRIP_BYTE_COUNTS))
			}
			
			stripList != null -> {
				if(ifd1 == null) {
					ifd1 = IfdData(IfdData.TYPE_IFD_1)
					exifData.addIfdData(ifd1)
				}
				val offsetTag = mInterface.buildUninitializedTag(ExifInterface.TAG_STRIP_OFFSETS)
					?: throw IOException("No definition for crucial exif tag: ${ExifInterface.TAG_STRIP_OFFSETS}")
				val lengthTag =
					mInterface.buildUninitializedTag(ExifInterface.TAG_STRIP_BYTE_COUNTS)
						?: throw IOException("No definition for crucial exif tag: ${ExifInterface.TAG_STRIP_BYTE_COUNTS}")
				
				val bytesList = LongArray(stripList.size)
				stripList.forEachIndexed { index, bytes -> bytesList[index] = bytes.size.toLong() }
				lengthTag.setValue(bytesList)
				ifd1.setTag(offsetTag)
				ifd1.setTag(lengthTag)
				// Get rid of tags for compressed if they exist.
				ifd1.removeTag(ExifInterface.getTrueTagKey(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT))
				ifd1.removeTag(ExifInterface.getTrueTagKey(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH))
			}
			
			ifd1 != null -> {
				// Get rid of offset and length tags if there is no thumbnail.
				ifd1.removeTag(ExifInterface.getTrueTagKey(ExifInterface.TAG_STRIP_OFFSETS))
				ifd1.removeTag(ExifInterface.getTrueTagKey(ExifInterface.TAG_STRIP_BYTE_COUNTS))
				ifd1.removeTag(ExifInterface.getTrueTagKey(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT))
				ifd1.removeTag(ExifInterface.getTrueTagKey(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH))
			}
		}
	}
	
	private fun calculateAllOffset() : Int {
		var offset = TIFF_HEADER_SIZE.toInt()
		
		val ifd0 = exifData.getIfdData(IfdData.TYPE_IFD_0)?.also {
			offset = calculateOffsetOfIfd(it, offset)
		}
		
		val exifIfd = exifData.getIfdData(IfdData.TYPE_IFD_EXIF)?.also { it ->
			ifd0?.getTag(ExifInterface.getTrueTagKey(ExifInterface.TAG_EXIF_IFD))
				?.setValue(offset)
			offset = calculateOffsetOfIfd(it, offset)
		}
		
		exifData.getIfdData(IfdData.TYPE_IFD_INTEROPERABILITY)?.also { it ->
			exifIfd?.getTag(ExifInterface.getTrueTagKey(ExifInterface.TAG_INTEROPERABILITY_IFD))
				?.setValue(offset)
			offset = calculateOffsetOfIfd(it, offset)
		}
		
		exifData.getIfdData(IfdData.TYPE_IFD_GPS)?.also {
			ifd0?.getTag(ExifInterface.getTrueTagKey(ExifInterface.TAG_GPS_IFD))
				?.setValue(offset)
			offset = calculateOffsetOfIfd(it, offset)
		}
		
		val ifd1 = exifData.getIfdData(IfdData.TYPE_IFD_1)?.also {
			ifd0?.offsetToNextIfd = offset
			offset = calculateOffsetOfIfd(it, offset)
		}
		
		val compressedThumbnail = exifData.compressedThumbnail
		if(compressedThumbnail != null) {
			ifd1
				?.getTag(ExifInterface.getTrueTagKey(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT))
				?.setValue(offset)
			offset += compressedThumbnail.size
		} else {
			// uncompressed thumbnail
			val stripList = exifData.stripList
			if(stripList != null) {
				val offsets = LongArray(stripList.size)
				stripList.forEachIndexed { index, bytes ->
					offsets[index] = offset.toLong()
					offset += bytes.size
				}
				ifd1
					?.getTag(ExifInterface.getTrueTagKey(ExifInterface.TAG_STRIP_OFFSETS))
					?.setValue(offsets)
			}
		}
		
		return offset
	}
	
	companion object {
		private const val TAG = "ExifOutputStream"
		private const val STREAMBUFFER_SIZE = 0x00010000 // 64Kb
		
		private const val STATE_SOI = 0
		private const val EXIF_HEADER = 0x45786966
		private const val TIFF_HEADER : Short = 0x002A
		private const val TIFF_BIG_ENDIAN : Short = 0x4d4d
		private const val TIFF_LITTLE_ENDIAN : Short = 0x4949
		private const val TAG_SIZE : Short = 12
		private const val TIFF_HEADER_SIZE : Short = 8
		private const val MAX_EXIF_SIZE = 65535
		
		@Throws(IOException::class)
		fun writeTagValue(tag : ExifTag, dataOutputStream : OrderedDataOutputStream) {
			when(tag.dataType) {
				ExifTag.TYPE_ASCII -> {
					val buf = tag.stringByte !!
					if(buf.size == tag.componentCount) {
						buf[buf.size - 1] = 0
						dataOutputStream.write(buf)
					} else {
						dataOutputStream.write(buf)
						dataOutputStream.write(0)
					}
				}
				
				ExifTag.TYPE_LONG, ExifTag.TYPE_UNSIGNED_LONG -> run {
					for(i in 0 until tag.componentCount) {
						dataOutputStream.writeInt(tag.getValueAt(i).toInt())
					}
				}
				
				ExifTag.TYPE_RATIONAL, ExifTag.TYPE_UNSIGNED_RATIONAL -> run {
					for(i in 0 until tag.componentCount) {
						dataOutputStream.writeRational(tag.getRational(i) !!)
					}
				}
				
				ExifTag.TYPE_UNDEFINED, ExifTag.TYPE_UNSIGNED_BYTE -> {
					val buf = ByteArray(tag.componentCount)
					tag.getBytes(buf)
					dataOutputStream.write(buf)
				}
				
				ExifTag.TYPE_UNSIGNED_SHORT -> {
					for(i in 0 until tag.componentCount) {
						dataOutputStream.writeShort(tag.getValueAt(i).toShort())
					}
				}
			}
		}
	}
}
