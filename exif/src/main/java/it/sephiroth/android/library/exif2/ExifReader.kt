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

import java.io.IOException
import java.io.InputStream

/**
 * This class reads the EXIF header of a JPEG file and stores it in
 * [ExifData].
 */
internal class ExifReader(private val mInterface : ExifInterface) {
	
	/**
	 * Parses the inputStream and and returns the EXIF data in an
	 * [ExifData].
	 *
	 * @throws ExifInvalidFormatException
	 * @throws java.io.IOException
	 */
	@Throws(ExifInvalidFormatException::class, IOException::class)
	fun read(inputStream : InputStream, options : Int) : ExifData {
		val parser = ExifParser.parse(inputStream, options, mInterface)
		val exifData = ExifData(parser.byteOrder )
		exifData.sections = parser.sections
		exifData.mUncompressedDataPosition = parser.uncompressedDataPosition
		
		exifData.qualityGuess = parser.qualityGuess
		exifData.jpegProcess = parser.jpegProcess
		
		val w = parser.imageWidth
		val h = parser.imageLength
		
		if(w > 0 && h > 0) {
			exifData.setImageSize(w, h)
		}
		
		var tag : ExifTag?
		
		var event = parser.next()
		while(event != ExifParser.EVENT_END) {
			when(event) {
				ExifParser.EVENT_START_OF_IFD -> exifData.addIfdData(IfdData(parser.currentIfd))
				
				ExifParser.EVENT_NEW_TAG -> {
					tag = parser.tag
					
					
					
					if(! tag !!.hasValue()) {
						parser.registerForTagValue(tag)
					} else {
						// Log.v(TAG, "parsing id " + tag.getTagId() + " = " + tag);
						if(parser.isDefinedTag(tag.ifd, tag.tagId.toInt())) {
							exifData.getIfdData(tag.ifd) !!.setTag(tag)
						} else {
							Log.w(TAG, "skip tag because not registered in the tag table:$tag")
						}
					}
				}
				
				ExifParser.EVENT_VALUE_OF_REGISTERED_TAG -> {
					tag = parser.tag
					if(tag !!.dataType == ExifTag.TYPE_UNDEFINED) {
						parser.readFullTagValue(tag)
					}
					exifData.getIfdData(tag.ifd) !!.setTag(tag)
				}
				
				ExifParser.EVENT_COMPRESSED_IMAGE -> {
					val buf = ByteArray(parser.compressedImageSize)
					if(buf.size == parser.read(buf)) {
						exifData.compressedThumbnail = buf
					} else {
						Log.w(TAG, "Failed to read the compressed thumbnail")
					}
				}
				
				ExifParser.EVENT_UNCOMPRESSED_STRIP -> {
					val buf = ByteArray(parser.stripSize)
					if(buf.size == parser.read(buf)) {
						exifData.setStripBytes(parser.stripIndex, buf)
					} else {
						Log.w(TAG, "Failed to read the strip bytes")
					}
				}
			}
			event = parser.next()
		}
		return exifData
	}
	
	companion object {
		private const val TAG = "ExifReader"
	}
}
