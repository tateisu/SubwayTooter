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

import java.util.HashMap

// This class stores all the tags in an IFD.
// an IfdData with given IFD ID.
internal class IfdData(
	val id : Int // the ID of this IFD.
) {
	
	companion object {
		/**
		 * The constants of the IFD ID defined in EXIF spec.
		 */
		const val TYPE_IFD_0 = 0
		const val TYPE_IFD_1 = 1
		const val TYPE_IFD_EXIF = 2
		const val TYPE_IFD_INTEROPERABILITY = 3
		const val TYPE_IFD_GPS = 4
		/* This is used in ExifData to allocate enough IfdData */
		const val TYPE_IFD_COUNT = 5
		
		val list = intArrayOf(
			TYPE_IFD_0,
			TYPE_IFD_1,
			TYPE_IFD_EXIF,
			TYPE_IFD_INTEROPERABILITY,
			TYPE_IFD_GPS
		)
	}
	
	private val mExifTags = HashMap<Short, ExifTag>()
	
	// the offset of next IFD.
	var offsetToNextIfd = 0
	
	// the tags count in the IFD.
	val tagCount : Int
		get() = mExifTags.size
	
	// Collection the contains all [ExifTag] in this IFD.
	val allTagsCollection : Collection<ExifTag>
		get() = mExifTags.values
	
	// checkCollision
	fun contains(tagId : Short) : Boolean {
		return mExifTags[tagId] != null
	}
	
	// the [ExifTag] with given tag id.
	// null if there is no such tag.
	fun getTag(tagId : Short) : ExifTag? {
		return mExifTags[tagId]
	}
	
	// Adds or replaces a [ExifTag].
	fun setTag(tag : ExifTag) : ExifTag? {
		tag.ifd = id
		return mExifTags.put(tag.tagId, tag)
	}
	
	// Removes the tag of the given ID
	fun removeTag(tagId : Short) {
		mExifTags.remove(tagId)
	}
	
	/**
	 * Returns true if all tags in this two IFDs are equal. Note that tags of
	 * IFDs offset or thumbnail offset will be ignored.
	 */
	override fun equals(other : Any?) : Boolean {
		if(other is IfdData) {
			if(other === this) return true
			if(other.id == id && other.tagCount == tagCount) {
				for(tag in other.allTagsCollection) {
					if(ExifInterface.isOffsetTag(tag.tagId)) continue
					if(tag != mExifTags[tag.tagId]) return false
				}
				return true
			}
		}
		return false
	}
	
	override fun hashCode() : Int {
		var result = id
		result = 31 * result + mExifTags.hashCode()
		result = 31 * result + offsetToNextIfd
		return result
	}
}
