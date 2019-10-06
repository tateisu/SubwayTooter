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
internal open class ExifData( val byteOrder : ByteOrder ) {
	
	var sections : List<ExifParser.Section>? = null
	private val mIfdDatas = arrayOfNulls<IfdData>(IfdId.TYPE_IFD_COUNT)
	/**
	 * Gets the compressed thumbnail. Returns null if there is no compressed
	 * thumbnail.
	 *
	 * @see .hasCompressedThumbnail
	 */
	/**
	 * Sets the compressed thumbnail.
	 */
	var compressedThumbnail : ByteArray? = null
	private val mStripBytes = ArrayList<ByteArray?>()
	var qualityGuess = 0
	private var imageLength = - 1
	private var imageWidth = - 1
	var jpegProcess : Short = 0
	var mUncompressedDataPosition = 0
	
	/**
	 * Gets the strip count.
	 */
	val stripCount : Int
		get() = mStripBytes.size
	
	/**
	 * Decodes the user comment tag into string as specified in the EXIF
	 * standard. Returns null if decoding failed.
	 */
	val userComment : String?
		get() {
			val ifdData = mIfdDatas[IfdId.TYPE_IFD_0] ?: return null
			val tag = ifdData.getTag(ExifInterface.getTrueTagKey(ExifInterface.TAG_USER_COMMENT))
				?: return null
			if(tag.componentCount < 8) {
				return null
			}
			
			val buf = ByteArray(tag.componentCount)
			tag.getBytes(buf)
			
			val code = ByteArray(8)
			System.arraycopy(buf, 0, code, 0, 8)
			
			return try {
				when {
					code.contentEquals(USER_COMMENT_ASCII) -> String(
						buf,
						8,
						buf.size - 8,
						Charsets.US_ASCII
					)
					code.contentEquals(USER_COMMENT_JIS) -> String(
						buf,
						8,
						buf.size - 8,
						Charset.forName("EUC-JP")
					)
					code.contentEquals(USER_COMMENT_UNICODE) -> String(
						buf,
						8,
						buf.size - 8,
						Charsets.UTF_16
					)
					else -> null
				}
			} catch(e : UnsupportedEncodingException) {
				Log.w(TAG, "Failed to decode the user comment")
				null
			}
		}
	
	/**
	 * Returns a list of all [ExifTag]s in the ExifData or null if there
	 * are none.
	 */
	val allTags : List<ExifTag>?
		get() {
			val ret = ArrayList<ExifTag>()
			mIfdDatas.forEach { it?.allTags?.forEach { tag -> ret.add(tag) } }
			return if(ret.isEmpty()) null else ret
		}
	
	val imageSize : IntArray
		get() = intArrayOf(imageWidth, imageLength)
	
	/**
	 * Returns true it this header contains a compressed thumbnail.
	 */
	fun hasCompressedThumbnail() : Boolean {
		return compressedThumbnail != null
	}
	
	/**
	 * Adds an uncompressed strip.
	 */
	fun setStripBytes(index : Int, strip : ByteArray) {
		if(index < mStripBytes.size) {
			mStripBytes[index] = strip
		} else {
			for(i in mStripBytes.size until index) {
				mStripBytes.add(null)
			}
			mStripBytes.add(strip)
		}
	}
	
	/**
	 * Gets the strip at the specified index.
	 *
	 * @exceptions #IndexOutOfBoundException
	 */
	fun getStrip(index : Int) : ByteArray? = mStripBytes[index]
	
	/**
	 * Returns true if this header contains uncompressed strip.
	 */
	fun hasUncompressedStrip() : Boolean = mStripBytes.isNotEmpty()
	
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
	fun getTag(tag : Short, ifd : Int) : ExifTag? {
		val ifdData = mIfdDatas[ifd]
		return ifdData?.getTag(tag)
	}
	
	/**
	 * Adds the given ExifTag to its default IFD and returns an existing ExifTag
	 * with the same TID or null if none exist.
	 */
	fun addTag(tag : ExifTag?) : ExifTag? {
		if(tag != null) {
			val ifd = tag.ifd
			return addTag(tag, ifd)
		}
		return null
	}
	
	/**
	 * Adds the given ExifTag to the given IFD and returns an existing ExifTag
	 * with the same TID or null if none exist.
	 */
	private fun addTag(tag : ExifTag?, ifdId : Int) : ExifTag? {
		if(tag != null && ExifTag.isValidIfd(ifdId)) {
			val ifdData = getOrCreateIfdData(ifdId)
			return ifdData.setTag(tag)
		}
		return null
	}
	
	/**
	 * Returns the [IfdData] object corresponding to a given IFD or
	 * generates one if none exist.
	 */
	private fun getOrCreateIfdData(ifdId : Int) : IfdData {
		var ifdData : IfdData? = mIfdDatas[ifdId]
		if(ifdData == null) {
			ifdData = IfdData(ifdId)
			mIfdDatas[ifdId] = ifdData
		}
		return ifdData
	}
	
	/**
	 * Removes the thumbnail and its related tags. IFD1 will be removed.
	 */
	protected fun removeThumbnailData() {
		clearThumbnailAndStrips()
		mIfdDatas[IfdId.TYPE_IFD_1] = null
	}
	
	fun clearThumbnailAndStrips() {
		compressedThumbnail = null
		mStripBytes.clear()
	}
	
	/**
	 * Removes the tag with a given TID and IFD.
	 */
	fun removeTag(tagId : Short, ifdId : Int) {
		val ifdData = mIfdDatas[ifdId] ?: return
		ifdData.removeTag(tagId)
	}
	
	/**
	 * Returns a list of all [ExifTag]s in a given IFD or null if there
	 * are none.
	 */
	fun getAllTagsForIfd(ifd : Int) : List<ExifTag>? {
		val d = mIfdDatas[ifd] ?: return null
		val tags = d.allTags
		val ret = ArrayList<ExifTag>(tags.size)
		for(t in tags) {
			ret.add(t)
		}
		return if(ret.size == 0) {
			null
		} else ret
	}
	
	// Returns a list of all [ExifTag]s with a given TID
	// or null if there are none.
	fun getAllTagsForTagId(tag : Short) : List<ExifTag>? {
		val ret = ArrayList<ExifTag>()
		for(d in mIfdDatas) {
			if(d != null) {
				val t = d.getTag(tag)
				if(t != null) {
					ret.add(t)
				}
			}
		}
		return if(ret.isEmpty()) null else ret
	}
	
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
			
			for(i in 0 until IfdId.TYPE_IFD_COUNT) {
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
	fun getIfdData(ifdId : Int) : IfdData? {
		return if(ExifTag.isValidIfd(ifdId)) {
			mIfdDatas[ifdId]
		} else null
	}
	
	fun setImageSize(imageWidth : Int, imageLength : Int) {
		this.imageWidth = imageWidth
		this.imageLength = imageLength
	}
	
	override fun hashCode() : Int {
		var result = byteOrder.hashCode()
		result = 31 * result + (sections?.hashCode() ?: 0)
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
	}
}
