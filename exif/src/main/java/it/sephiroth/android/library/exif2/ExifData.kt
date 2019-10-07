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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import it.sephiroth.android.library.exif2.utils.notEmpty

import java.io.UnsupportedEncodingException
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.ArrayList
import java.util.Arrays

/**
 * This class stores the EXIF header in IFDs according to the JPEG
 * specification. It is the result produced by [ExifReader].
 *
 * @see ExifReader
 *
 * @see IfdData
 */
@Suppress("unused")
internal class ExifData(
	val byteOrder : ByteOrder = ExifInterface.DEFAULT_BYTE_ORDER,
	val sections : List<ExifParser.Section> = ArrayList(),
	val mUncompressedDataPosition : Int = 0,
	val qualityGuess : Int = 0,
	val jpegProcess : Short = 0
) {
	
	private var imageLength = - 1
	private var imageWidth = - 1
	
	private val mIfdDatas = arrayOfNulls<IfdData>(IfdData.TYPE_IFD_COUNT)
	
	// the compressed thumbnail.
	// null if there is no compressed thumbnail.
	var compressedThumbnail : ByteArray? = null
	
	private val mStripBytes = ArrayList<ByteArray?>()
	
	val stripList : List<ByteArray>?
		get() = mStripBytes.filterNotNull().notEmpty()
	
	// Decodes the user comment tag into string as specified in the EXIF standard.
	// Returns null if decoding failed.
	val userComment : String?
		get() {
			
			val ifdData = mIfdDatas[IfdData.TYPE_IFD_0]
				?: return null
			
			val tag = ifdData.getTag(ExifInterface.getTrueTagKey(ExifInterface.TAG_USER_COMMENT))
				?: return null
			
			if(tag.componentCount < 8)
				return null
			
			return try {
				
				val buf = ByteArray(tag.componentCount)
				tag.getBytes(buf)
				
				val code = ByteArray(8)
				System.arraycopy(buf, 0, code, 0, 8)
				
				val charset = when {
					code.contentEquals(USER_COMMENT_ASCII) -> Charsets.US_ASCII
					code.contentEquals(USER_COMMENT_JIS) -> eucJp
					code.contentEquals(USER_COMMENT_UNICODE) -> Charsets.UTF_16
					else -> null
				}
				if(charset == null) null else String(buf, 8, buf.size - 8, charset)
			} catch(e : UnsupportedEncodingException) {
				Log.w(TAG, "Failed to decode the user comment")
				null
			}
		}
	
	// list of all [ExifTag]s in the ExifData
	// or null if there are none.
	val allTags : List<ExifTag>
		get() = ArrayList<ExifTag>()
			.apply { mIfdDatas.forEach { if(it != null) addAll(it.allTagsCollection) } }
	
	val imageSize : IntArray
		get() = intArrayOf(imageWidth, imageLength)
	
	val thumbnailBytes : ByteArray?
		get() = when {
			compressedThumbnail != null -> compressedThumbnail
			stripList != null -> null // TODO: implement this
			else -> null
		}
	
	val thumbnailBitmap : Bitmap?
		get() {
			val compressedThumbnail = this.compressedThumbnail
			if(compressedThumbnail != null) {
				return BitmapFactory
					.decodeByteArray(compressedThumbnail, 0, compressedThumbnail.size)
			}
			val stripList = this.stripList
			if(stripList != null) {
				// TODO: decoding uncompressed thumbnail is not implemented.
				return null
			}
			
			return null
		}
	
	/**
	 * Adds an uncompressed strip.
	 */
	fun setStripBytes(index : Int, strip : ByteArray) {
		if(index in mStripBytes.indices) {
			mStripBytes[index] = strip
		} else {
			for(i in mStripBytes.size until index) {
				mStripBytes.add(null)
			}
			mStripBytes.add(strip)
		}
	}
	
	/**
	 * Returns the [IfdData] object corresponding to a given IFD or
	 * generates one if none exist.
	 */
	private fun prepareIfdData(ifdId : Int) : IfdData {
		var ifdData = mIfdDatas[ifdId]
		if(ifdData == null) {
			ifdData = IfdData(ifdId)
			mIfdDatas[ifdId] = ifdData
		}
		return ifdData
	}
	
	/**
	 * Adds IFD data. If IFD data of the same type already exists, it will be
	 * replaced by the new data.
	 */
	fun addIfdData(data : IfdData) {
		mIfdDatas[data.id] = data
	}
	
	/**
	 * Returns the tag with a given TID in the given IFD if the tag exists.
	 * Otherwise returns null.
	 */
	fun getTag(tag : Short, ifd : Int) : ExifTag? =
		mIfdDatas[ifd]?.getTag(tag)
	
	/**
	 * Adds the given ExifTag to its default IFD and returns an existing ExifTag
	 * with the same TID or null if none exist.
	 */
	fun addTag(tag : ExifTag?) : ExifTag? =
		when(tag) {
			null -> null
			else -> addTag(tag, tag.ifd)
		}
	
	/**
	 * Adds the given ExifTag to the given IFD and returns an existing ExifTag
	 * with the same TID or null if none exist.
	 */
	private fun addTag(tag : ExifTag?, ifdId : Int) : ExifTag? =
		when {
			tag == null -> null
			! ExifTag.isValidIfd(ifdId) -> null
			else -> prepareIfdData(ifdId).setTag(tag)
		}
	
	/**
	 * Removes the tag with a given TID and IFD.
	 */
	fun removeTag(tagId : Short, ifdId : Int) {
		mIfdDatas[ifdId]?.removeTag(tagId)
	}
	
	/**
	 * Removes the thumbnail and its related tags. IFD1 will be removed.
	 */
	fun removeThumbnailData() {
		clearThumbnailAndStrips()
		mIfdDatas[IfdData.TYPE_IFD_1] = null
	}
	
	fun clearThumbnailAndStrips() {
		compressedThumbnail = null
		mStripBytes.clear()
	}
	
	/**
	 * Returns a list of all [ExifTag]s in a given IFD or null if there
	 * are none.
	 */
	fun getAllTagsForIfd(ifd : Int) : List<ExifTag>? =
		mIfdDatas[ifd]?.allTagsCollection?.notEmpty()?.toList()
	
	// Returns a list of all [ExifTag]s with a given TID
	// or null if there are none.
	fun getAllTagsForTagId(tag : Short) : List<ExifTag>? =
		ArrayList<ExifTag>()
			.apply { mIfdDatas.forEach { it?.getTag(tag)?.let { t -> add(t) } } }
			.notEmpty()
	
	override fun equals(other : Any?) : Boolean {
		if(this === other) return true
		if(other is ExifData) {
			if(other.byteOrder != byteOrder
				|| other.mStripBytes.size != mStripBytes.size
				|| ! Arrays.equals(other.compressedThumbnail, compressedThumbnail)
			) {
				return false
			}
			for(i in mStripBytes.indices) {
				val a = mStripBytes[i]
				val b = other.mStripBytes[i]
				
				if(a != null && b != null) {
					if(! a.contentEquals(b)) return false // 内容が異なる
				} else if((a == null) xor (b == null)) {
					return false // 片方だけnull
				}
			}
			
			for(i in 0 until IfdData.TYPE_IFD_COUNT) {
				val ifd1 = other.getIfdData(i)
				val ifd2 = getIfdData(i)
				if(ifd1 != ifd2) return false
			}
			return true
		}
		return false
	}
	
	/**
	 * Returns the [IfdData] object corresponding to a given IFD if it
	 * exists or null.
	 */
	fun getIfdData(ifdId : Int) = when {
		! ExifTag.isValidIfd(ifdId) -> null
		else -> mIfdDatas[ifdId]
	}
	
	fun setImageSize(imageWidth : Int, imageLength : Int) {
		this.imageWidth = imageWidth
		this.imageLength = imageLength
	}
	
	override fun hashCode() : Int {
		var result = byteOrder.hashCode()
		result = 31 * result + (sections.hashCode())
		result = 31 * result + mIfdDatas.contentHashCode()
		result = 31 * result + (compressedThumbnail?.contentHashCode() ?: 0)
		result = 31 * result + mStripBytes.hashCode()
		result = 31 * result + qualityGuess
		result = 31 * result + imageLength
		result = 31 * result + imageWidth
		result = 31 * result + jpegProcess
		result = 31 * result + mUncompressedDataPosition
		return result
	}
	
	companion object {
		private const val TAG = "ExifData"
		
		private val USER_COMMENT_ASCII =
			byteArrayOf(0x41, 0x53, 0x43, 0x49, 0x49, 0x00, 0x00, 0x00)
		
		private val USER_COMMENT_JIS =
			byteArrayOf(0x4A, 0x49, 0x53, 0x00, 0x00, 0x00, 0x00, 0x00)
		
		private val USER_COMMENT_UNICODE =
			byteArrayOf(0x55, 0x4E, 0x49, 0x43, 0x4F, 0x44, 0x45, 0x00)
		
		private val eucJp = Charset.forName("EUC-JP")
		
	}
}
