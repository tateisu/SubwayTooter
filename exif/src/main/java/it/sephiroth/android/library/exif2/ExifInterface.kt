/*
 * Copyright (C) 2013 The Android Open Source Project
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
import android.util.Log
import android.util.SparseIntArray
import org.apache.commons.io.IOUtils
import java.io.*
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * This class provides methods and constants for reading and writing jpeg file
 * metadata. It contains a collection of ExifTags, and a collection of
 * definitions for creating valid ExifTags. The collection of ExifTags can be
 * updated by: reading new ones from a file, deleting or adding existing ones,
 * or building new ExifTags from a tag definition. These ExifTags can be written
 * to a valid jpeg image as exif metadata.
 *
 *
 * Each ExifTag has a tag ID (TID) and is stored in a specific image file
 * directory (IFD) as specified by the exif standard. A tag definition can be
 * looked up with a constant that is a combination of TID and IFD. This
 * definition has information about the type, number of components, and valid
 * IFDs for a tag.
 *
 * @see ExifTag
 */
@Suppress("unused")
class ExifInterface {
	
	private val mGPSTimeStampCalendar : Calendar by lazy {
		Calendar.getInstance(TimeZone.getTimeZone("UTC"))
	}
	
	val tagInfo : SparseIntArray by lazy {
		SparseIntArray().initTagInfo()
	}
	
	private var mData = ExifData()
	
	/**
	 * Get the exif tags in this ExifInterface object or null if none exist.
	 *
	 * @return a List of [ExifTag]s.
	 */
	val allTags : List<ExifTag>
		get() = mData.allTags
	
	/**
	 * Returns the JPEG quality used to generate the image
	 * or 0 if not found
	 *
	 * @return qualityGuess
	 */
	val qualityGuess : Int
		get() = mData.qualityGuess
	
	/**
	 * Returns the thumbnail from IFD1 as a byte array, or null if none exists.
	 * The bytes may either be an uncompressed strip as specified in the exif
	 * standard or a jpeg compressed image.
	 *
	 * @return the thumbnail as a byte array.
	 */
	val thumbnailBytes : ByteArray?
		get() = mData.thumbnailBytes
	
	/**
	 * Returns the thumbnail from IFD1 as a bitmap, or null if none exists.
	 *
	 * @return the thumbnail as a bitmap.
	 */
	val thumbnailBitmap : Bitmap?
		get() = mData.thumbnailBitmap
	
	/**
	 * this gives information about the process used to create the JPEG file.
	 * Possible values are:
	 *
	 *  * '0' Unknown
	 *  * '192' Baseline
	 *  * '193' Extended sequential
	 *  * '194' Progressive
	 *  * '195' Lossless
	 *  * '197' Differential sequential
	 *  * '198' Differential progressive
	 *  * '199' Differential lossless
	 *  * '201' Extended sequential, arithmetic coding
	 *  * '202' Progressive, arithmetic coding
	 *  * '203' Lossless, arithmetic coding
	 *  * '205' Differential sequential, arithmetic coding
	 *  * '206' Differential progressive, arithmetic codng
	 *  * '207' Differential lossless, arithmetic coding
	 *
	 */
	val jpegProcess : Short
		get() = mData.jpegProcess
	
	/**
	 * Returns the Image size as decoded from the SOF marker
	 */
	val imageSize : IntArray
		get() = mData.imageSize
	
	/**
	 * Check if thumbnail is compressed.
	 *
	 * @return true if the thumbnail is compressed.
	 */
	val isThumbnailCompressed : Boolean
		get() = mData.compressedThumbnail != null
	
	/**
	 * Decodes the user comment tag into string as specified in the EXIF
	 * standard. Returns null if decoding failed.
	 */
	val userComment : String?
		get() = mData.userComment
	
	/**
	 * Gets the GPS latitude and longitude as a pair of doubles from this
	 * ExifInterface object's tags, or null if the necessary tags do not exist.
	 *
	 * @return an array of 2 doubles containing the latitude, and longitude
	 * respectively.
	 * @see .convertLatOrLongToDouble
	 */
	val latLongAsDoubles : DoubleArray?
		get() {
			val latitude = getTagRationalValues(TAG_GPS_LATITUDE)
			val latitudeRef = getTagStringValue(TAG_GPS_LATITUDE_REF)
			val longitude = getTagRationalValues(TAG_GPS_LONGITUDE)
			val longitudeRef = getTagStringValue(TAG_GPS_LONGITUDE_REF)
			
			return when {
				
				latitude == null
					|| longitude == null
					|| latitudeRef == null
					|| longitudeRef == null
					|| latitude.size < 3
					|| longitude.size < 3 -> null
				
				else -> doubleArrayOf(
					convertLatOrLongToDouble(latitude, latitudeRef),
					convertLatOrLongToDouble(longitude, longitudeRef)
				)
			}
		}
	
	/**
	 * Returns a formatted String with the latitude representation:<br></br>
	 * 39° 8' 16.8" N
	 */
	val latitude : String?
		get() {
			val latitude = getTagRationalValues(TAG_GPS_LATITUDE)
			val latitudeRef = getTagStringValue(TAG_GPS_LATITUDE_REF)
			
			return when {
				null == latitude || null == latitudeRef -> null
				else -> convertRationalLatLonToString(latitude, latitudeRef)
			}
		}
	
	/**
	 * Returns a formatted String with the longitude representation:<br></br>
	 * 77° 37' 51.6" W
	 */
	val longitude : String?
		get() {
			val longitude = getTagRationalValues(TAG_GPS_LONGITUDE)
			val longitudeRef = getTagStringValue(TAG_GPS_LONGITUDE_REF)
			
			return when {
				null == longitude || null == longitudeRef -> null
				else -> convertRationalLatLonToString(longitude, longitudeRef)
			}
		}
	
	val altitude : Double? // may null
		get() = when(val gpsAltitude = getTagRationalValue(TAG_GPS_ALTITUDE)) {
			null -> null
			
			else -> {
				val seaLevel = when(getTagByteValue(TAG_GPS_ALTITUDE_REF)) {
					1.toByte() -> - 1
					else -> 1
				}
				gpsAltitude.toDouble() * seaLevel
			}
		}
	
	/**
	 * Return the aperture size, if present, 0 if missing
	 */
	val apertureSize : Double
		get() {
			var v = getTagRationalValue(TAG_F_NUMBER)?.toDouble()
			if(v != null && v > 0.0) return v
			
			v = getTagRationalValue(TAG_APERTURE_VALUE)?.toDouble()
			if(v != null && v > 0.0) return exp(v * ln(2.0) * 0.5)
			
			return 0.0
		}
	
	/**
	 * Returns the lens model as string if any of the tags [.TAG_LENS_MODEL]
	 * or [.TAG_LENS_SPECS] are found
	 *
	 * @return the string representation of the lens spec
	 */
	val lensModelDescription : String?
		get() {
			val lensModel = getTagStringValue(TAG_LENS_MODEL)
			if(null != lensModel) return lensModel
			
			val rat = getTagRationalValues(TAG_LENS_SPECS)
			if(null != rat) return ExifUtil.processLensSpecifications(rat)
			
			return null
		}
	
	/**
	 * Given the value from [.TAG_FOCAL_PLANE_RESOLUTION_UNIT] or [.TAG_RESOLUTION_UNIT]
	 * this method will return the corresponding value in millimeters
	 *
	 * @param resolution [.TAG_FOCAL_PLANE_RESOLUTION_UNIT] or [.TAG_RESOLUTION_UNIT]
	 * @return resolution in millimeters
	 */
	fun getResolutionUnit(resolution : Int) : Double =
		when(resolution.toShort()) {
			ResolutionUnit.INCHES -> 25.4
			ResolutionUnit.CENTIMETERS -> 10.0
			ResolutionUnit.MILLIMETERS -> 1.0
			ResolutionUnit.MICROMETERS -> .001
			else -> 25.4
		}
	
	/**
	 * Clears this ExifInterface object's existing exif tags.
	 */
	private fun clearExif() : ExifInterface {
		mData = ExifData()
		return this
	}
	
	/**
	 * Reads the exif tags from an InputStream, clearing this ExifInterface
	 * object's existing exif tags.
	 * <pre>
	 * ExifInterface exif = new ExifInterface();
	 * exif.readExif( stream, Options.OPTION_IFD_0 | Options.OPTION_IFD_1 | Options.OPTION_IFD_EXIF );
	 * ...
	 * // to request all the options use the OPTION_ALL bit mask
	 * exif.readExif( stream, Options.OPTION_ALL );
	</pre> *
	 *
	 * @param inStream an InputStream containing a jpeg compressed image.
	 * @param options  bit flag which defines which type of tags to process, see [it.sephiroth.android.library.exif2.ExifInterface.Options]
	 * @throws java.io.IOException for I/O error
	 */
	@Throws(IOException::class)
	fun readExif(inStream : InputStream, options : Int) : ExifInterface {
		mData = ExifReader(this).read(inStream, options)
		return this
	}
	
	/**
	 * Reads the exif tags from a file, clearing this ExifInterface object's
	 * existing exif tags.
	 *
	 * @param inFileName a string representing the filepath to jpeg file.
	 * @param options    bit flag which defines which type of tags to process, see [it.sephiroth.android.library.exif2.ExifInterface.Options]
	 * @throws java.io.IOException for I/O error
	 * @see .readExif
	 */
	@Throws(IOException::class)
	fun readExif(inFileName : String, options : Int) : ExifInterface =
		BufferedInputStream(FileInputStream(inFileName)).use { readExif(it, options) }
	
	/**
	 * Reads the exif tags from a byte array, clearing this ExifInterface
	 * object's existing exif tags.
	 *
	 * @param jpeg    a byte array containing a jpeg compressed image.
	 * @param options bit flag which defines which type of tags to process, see [it.sephiroth.android.library.exif2.ExifInterface.Options]
	 * @throws java.io.IOException for I/O error
	 * @see .readExif
	 */
	@Throws(IOException::class)
	fun readExif(jpeg : ByteArray, options : Int) =
		ByteArrayInputStream(jpeg).use { readExif(it, options) }
	
	/**
	 * Sets the exif tags, clearing this ExifInterface object's existing exif
	 * tags.
	 *
	 * @param tags a collection of exif tags to set.
	 */
	fun setExif(tags : Collection<ExifTag>) : ExifInterface {
		clearExif()
		setTags(tags)
		return this
	}
	
	/**
	 * Puts an ExifTag into this ExifInterface object's tags, removing a
	 * previous ExifTag with the same TID and IFD. The IFD it is put into will
	 * be the one the tag was created with in [.buildTag].
	 *
	 * @param tag an ExifTag to put into this ExifInterface's tags.
	 * @return the previous ExifTag with the same TID and IFD or null if none
	 * exists.
	 */
	private fun setTag(tag : ExifTag) : ExifTag? =
		mData.addTag(tag)
	
	/**
	 * Puts a collection of ExifTags into this ExifInterface objects's tags. Any
	 * previous ExifTags with the same TID and IFDs will be removed.
	 *
	 * @param tags a Collection of ExifTags.
	 * @see .setTag
	 */
	private fun setTags(tags : Collection<ExifTag>) : ExifInterface {
		for(t in tags) setTag(t)
		return this
	}
	
	@Throws(IOException::class)
	fun writeExif(dstFilename : String) {
		Log.i(TAG, "writeExif: $dstFilename")
		
		// create a backup file
		val dst_file = File(dstFilename)
		val bak_file = File("$dstFilename.t")
		
		// try to delete old copy of backup
		// Log.d( TAG, "delete old backup file" );
		
		bak_file.delete()
		
		// rename dst file into backup file
		// Log.d( TAG, "rename dst into bak" )
		// if( ! dst_file.renameTo( bak_file ) ) return;
		
		try {
			// Log.d( TAG, "try to write into dst" );
			// writeExif( bak_file.getAbsolutePath(), dst_file.getAbsolutePath() );
			
			// Trying to write into bak_file using dst_file as source
			writeExif(dst_file.absolutePath, bak_file.absolutePath)
			
			// Now switch bak into dst
			// Log.d( TAG, "rename the bak into dst" );
			
			bak_file.renameTo(dst_file)
		} finally {
			// deleting backup file
			
			bak_file.delete()
		}
	}
	
	@Throws(IOException::class)
	fun writeExif(srcFilename : String, dstFilename : String) {
		Log.i(TAG, "writeExif: $dstFilename")
		
		// src and dst cannot be the same
		if(srcFilename == dstFilename) return
		
		// srcFilename is used *ONLY* to read the image uncompressed data
		// exif tags are not used here
		
		// 3. rename dst file into backup file
		FileInputStream(srcFilename).use { input ->
			FileOutputStream(dstFilename).use { output ->
				val position = writeExif_internal(input, output, mData)
				
				// 7. write the rest of the image..
				val in_channel = input.channel
				val out_channel = output.channel
				in_channel.transferTo(position.toLong(), in_channel.size() - position, out_channel)
				output.flush()
			}
		}
	}
	
	@Throws(IOException::class)
	fun writeExif(input : InputStream, dstFilename : String) {
		Log.i(TAG, "writeExif: $dstFilename")
		
		// inpur is used *ONLY* to read the image uncompressed data
		// exif tags are not used here
		
		val output = FileOutputStream(dstFilename)
		writeExif_internal(input, output, mData)
		
		// 7. write the rest of the image..
		IOUtils.copy(input, output)
		
		output.flush()
		output.close()
	}
	
	// input is used *ONLY* to read the image uncompressed data
	// exif tags are not used here
	@Throws(IOException::class)
	fun writeExif(input : Bitmap, dstFilename : String, quality : Int) {
		Log.i(TAG, "writeExif: $dstFilename")
		ByteArrayOutputStream().use { out ->
			input.compress(Bitmap.CompressFormat.JPEG, quality, out)
			ByteArrayInputStream(out.toByteArray()).use { inStream ->
				writeExif(inStream, dstFilename)
			}
		}
	}
	
	/**
	 * Returns a list of ExifTags that share a TID (which can be obtained by
	 * calling [.getTrueTagKey] on a defined tag constant) or null if none
	 * exist.
	 *
	 * @param tagId a TID as defined in the exif standard (or with
	 * [.defineTag]).
	 * @return a List of [ExifTag]s.
	 */
	fun getTagsForTagId(tagId : Short) : List<ExifTag>? {
		return mData.getAllTagsForTagId(tagId)
	}
	
	/**
	 * Returns a list of ExifTags that share an IFD (which can be obtained by
	 * calling [.getTrueIfd] on a defined tag constant) or null if none
	 * exist.
	 *
	 * @param ifdId an IFD as defined in the exif standard (or with
	 * [.defineTag]).
	 * @return a List of [ExifTag]s.
	 */
	fun getTagsForIfdId(ifdId : Int) : List<ExifTag>? {
		return mData.getAllTagsForIfd(ifdId)
	}
	
	/**
	 * Returns the ExifTag in that tag's default IFD for a defined tag constant
	 * or null if none exists.
	 *
	 * @param tagId a defined tag constant, e.g. [.TAG_IMAGE_WIDTH].
	 * @return an [ExifTag] or null if none exists.
	 */
	fun getTag(tagId : Int) : ExifTag? {
		val ifdId = getDefinedTagDefaultIfd(tagId)
		return getTag(tagId, ifdId)
	}
	
	/**
	 * Gets the default IFD for a tag.
	 *
	 * @param tagId a defined tag constant, e.g. [.TAG_IMAGE_WIDTH].
	 * @return the default IFD for a tag definition or [.IFD_NULL] if no
	 * definition exists.
	 */
	private fun getDefinedTagDefaultIfd(tagId : Int) : Int {
		val info = tagInfo.get(tagId)
		return if(info == DEFINITION_NULL) {
			IFD_NULL
		} else getTrueIfd(tagId)
	}
	
	/**
	 * Gets an ExifTag for an IFD other than the tag's default.
	 *
	 * @see .getTag
	 */
	private fun getTag(tagId : Int, ifdId : Int) : ExifTag? {
		return if(! ExifTag.isValidIfd(ifdId)) {
			null
		} else mData.getTag(getTrueTagKey(tagId), ifdId)
	}
	
	/**
	 * Returns the value of the ExifTag in that tag's default IFD for a defined
	 * tag constant or null if none exists or the value could not be cast into
	 * the return type.
	 *
	 * @param tagId a defined tag constant, e.g. [.TAG_IMAGE_WIDTH].
	 * @return the value of the ExifTag or null if none exists.
	 */
	fun getTagValue(tagId : Int) : Any? {
		val ifdId = getDefinedTagDefaultIfd(tagId)
		return getTagValue(tagId, ifdId)
	}
	
	/**
	 * Gets a tag value for an IFD other than the tag's default.
	 *
	 * @see .getTagValue
	 */
	private fun getTagValue(tagId : Int, ifdId : Int) : Any? {
		val t = getTag(tagId, ifdId)
		return t?.getValue()
	}
	
	private fun getTagStringValue(
		tagId : Int,
		ifdId : Int = getDefinedTagDefaultIfd(tagId)
	) : String? =
		getTag(tagId, ifdId)?.valueAsString
	
	// array
	
	private fun getTagLongValues(
		tagId : Int,
		ifdId : Int = getDefinedTagDefaultIfd(tagId)
	) : LongArray? =
		getTag(tagId, ifdId)?.valueAsLongs
	
	private fun getTagIntValues(
		tagId : Int,
		ifdId : Int = getDefinedTagDefaultIfd(tagId)
	) : IntArray? =
		getTag(tagId, ifdId)?.valueAsInts
	
	private fun getTagByteValues(
		tagId : Int,
		ifdId : Int = getDefinedTagDefaultIfd(tagId)
	) : ByteArray? =
		getTag(tagId, ifdId)?.valueAsBytes
	
	private fun getTagRationalValues(
		tagId : Int,
		ifdId : Int = getDefinedTagDefaultIfd(tagId)
	) : Array<Rational>? =
		getTag(tagId, ifdId)?.valueAsRationals
	
	// single value
	
	fun getTagLongValue(tagId : Int, ifdId : Int = getDefinedTagDefaultIfd(tagId)) : Long? =
		getTagLongValues(tagId, ifdId)?.firstOrNull()
	
	fun getTagIntValue(tagId : Int, ifdId : Int = getDefinedTagDefaultIfd(tagId)) : Int? =
		getTagIntValues(tagId, ifdId)?.firstOrNull()
	
	private fun getTagByteValue(tagId : Int, ifdId : Int = getDefinedTagDefaultIfd(tagId)) : Byte? =
		getTagByteValues(tagId, ifdId)?.firstOrNull()
	
	private fun getTagRationalValue(
		tagId : Int,
		ifdId : Int = getDefinedTagDefaultIfd(tagId)
	) : Rational? =
		getTagRationalValues(tagId, ifdId)?.firstOrNull()
	
	/**
	 * Checks whether a tag has a defined number of elements.
	 *
	 * @param tagId a defined tag constant, e.g. [.TAG_IMAGE_WIDTH].
	 * @return true if the tag has a defined number of elements.
	 */
	fun isTagCountDefined(tagId : Int) : Boolean {
		val info = tagInfo.get(tagId)
		// No value in info can be zero, as all tags have a non-zero type
		return info != 0 && getComponentCountFromInfo(info) != ExifTag.SIZE_UNDEFINED
	}
	
	/**
	 * Gets the defined number of elements for a tag.
	 *
	 * @param tagId a defined tag constant, e.g. [.TAG_IMAGE_WIDTH].
	 * @return the number of elements or [ExifTag.SIZE_UNDEFINED] if the
	 * tag or the number of elements is not defined.
	 */
	fun getDefinedTagCount(tagId : Int) : Int =
		when(val info = tagInfo.get(tagId)) {
			0 -> ExifTag.SIZE_UNDEFINED
			else -> getComponentCountFromInfo(info)
		}
	
	/**
	 * Gets the number of elements for an ExifTag in a given IFD.
	 *
	 * @param tagId a defined tag constant, e.g. [.TAG_IMAGE_WIDTH].
	 * @param ifdId the IFD containing the ExifTag to check.
	 * @return the number of elements in the ExifTag, if the tag's size is
	 * undefined this will return the actual number of elements that is
	 * in the ExifTag's value.
	 */
	fun getActualTagCount(tagId : Int, ifdId : Int) : Int {
		val t = getTag(tagId, ifdId) ?: return 0
		return t.componentCount
	}
	
	// Gets the defined type for a tag.
	// tagId : a defined tag constant, e.g. [.TAG_IMAGE_WIDTH].
	fun getDefinedTagType(tagId : Int) : Short {
		val info = tagInfo.get(tagId)
		return if(info == 0) - 1 else getTypeFromInfo(info)
	}
	
	fun buildUninitializedTag(tagId : Int) : ExifTag? {
		val info = tagInfo.get(tagId)
		if(info == 0) {
			return null
		}
		val type = getTypeFromInfo(info)
		val definedCount = getComponentCountFromInfo(info)
		val hasDefinedCount = definedCount != ExifTag.SIZE_UNDEFINED
		val ifdId = getTrueIfd(tagId)
		return ExifTag(getTrueTagKey(tagId), type, definedCount, ifdId, hasDefinedCount)
	}
	
	/**
	 * Sets the value of an ExifTag if it exists in the given IFD. The value
	 * must be the correct type and length for that ExifTag.
	 *
	 * @param tagId a tag constant, e.g. [.TAG_IMAGE_WIDTH].
	 * @param ifdId the IFD that the ExifTag is in.
	 * @param tagValue the value to set.
	 * @return true if success, false if the ExifTag doesn't exist or the value
	 * is the wrong type/length.
	 * @see .setTagValue
	 */
	private fun setTagValue(
		tagId : Int,
		tagValue : Any,
		ifdId : Int = getDefinedTagDefaultIfd(tagId)
	) : Boolean =
		getTag(tagId, ifdId)?.setValueAny(tagValue) ?: false
	
	/**
	 * Removes the ExifTag for a tag constant from the given IFD.
	 *
	 * @param tagId a tag constant, e.g. [.TAG_IMAGE_WIDTH].
	 * @param ifdId the IFD of the ExifTag to remove.
	 */
	private fun deleteTag(
		tagId : Int,
		ifdId : Int = getDefinedTagDefaultIfd(tagId)
	) : ExifInterface {
		mData.removeTag(getTrueTagKey(tagId), ifdId)
		return this
	}
	
	/**
	 * Creates a new tag definition in this ExifInterface object for a given TID
	 * and default IFD. Creating a definition with the same TID and default IFD
	 * as a previous definition will override it.
	 *
	 * @param tagId                 the TID for the tag.
	 * @param defaultIfd            the default IFD for the tag.
	 * @param tagType               the type of the tag
	 * @param defaultComponentCount the number of elements of this tag's type in
	 * the tags value.
	 * @param allowedIfds           the IFD's this tag is allowed to be put in.
	 * @return the defined tag constant (e.g. [.TAG_IMAGE_WIDTH]) or
	 * [.TAG_NULL] if the definition could not be made.
	 */
	fun setTagDefinition(
		tagId : Short,
		defaultIfd : Int,
		tagType : Short,
		defaultComponentCount : Short,
		allowedIfds : IntArray
	) : Int {
		if(sBannedDefines.contains(tagId)) {
			return TAG_NULL
		}
		if(ExifTag.isValidType(tagType) && ExifTag.isValidIfd(defaultIfd)) {
			val tagDef = defineTag(defaultIfd, tagId)
			if(tagDef == TAG_NULL) {
				return TAG_NULL
			}
			val otherDefs = getTagDefinitionsForTagId(tagId)
			val infos = tagInfo
			// Make sure defaultIfd is in allowedIfds
			var defaultCheck = false
			for(i in allowedIfds) {
				if(defaultIfd == i) {
					defaultCheck = true
				}
				if(! ExifTag.isValidIfd(i)) {
					return TAG_NULL
				}
			}
			if(! defaultCheck) {
				return TAG_NULL
			}
			
			val ifdFlags = getFlagsFromAllowedIfds(allowedIfds)
			// Make sure no identical tags can exist in allowedIfds
			if(otherDefs != null) {
				for(def in otherDefs) {
					val tagInfo = infos.get(def)
					val allowedFlags = getAllowedIfdFlagsFromInfo(tagInfo)
					if(ifdFlags and allowedFlags != 0) {
						return TAG_NULL
					}
				}
			}
			tagInfo.put(
				tagDef,
				ifdFlags shl 24 or (tagType shl 16) or defaultComponentCount.toInt()
			)
			return tagDef
		}
		return TAG_NULL
	}
	
	fun getTagDefinition(tagId : Short, defaultIfd : Int) : Int =
		tagInfo.get(defineTag(defaultIfd, tagId))
	
	private fun getTagDefinitionsForTagId(tagId : Short) : IntArray? {
		val ifds = IfdData.list
		val defs = IntArray(ifds.size)
		var counter = 0
		for(i in ifds) {
			val def = defineTag(i, tagId)
			if(tagInfo.get(def) != DEFINITION_NULL) {
				defs[counter ++] = def
			}
		}
		return if(counter == 0) null else defs.copyOfRange(0, counter)
		
	}
	
	fun getTagDefinitionForTag(tag : ExifTag) : Int =
		getTagDefinitionForTag(tag.tagId, tag.dataType, tag.componentCount, tag.ifd)
	
	private fun getTagDefinitionForTag(
		tagId : Short,
		type : Short,
		count : Int,
		ifd : Int
	) : Int {
		getTagDefinitionsForTagId(tagId)?.forEach { i ->
			val info = tagInfo.get(i)
			val def_type = getTypeFromInfo(info)
			val def_count = getComponentCountFromInfo(info)
			val def_ifds = getAllowedIfdsFromInfo(info)
			var valid_ifd = false
			if(def_ifds != null) {
				for(j in def_ifds) {
					if(j == ifd) {
						valid_ifd = true
						break
					}
				}
			}
			if(valid_ifd
				&& type == def_type
				&& (count == def_count || def_count == ExifTag.SIZE_UNDEFINED)
			) {
				return i
			}
		}
		return TAG_NULL
	}
	
	/**
	 * Removes a tag definition for given defined tag constant.
	 *
	 * @param tagId a defined tag constant, e.g. [.TAG_IMAGE_WIDTH].
	 */
	fun removeTagDefinition(tagId : Int) {
		tagInfo.delete(tagId)
	}
	
	//	/**
	//	 * Resets tag definitions to the default ones.
	//	 */
	//	fun resetTagDefinitions() {
	//		mTagInfo = null
	//	}
	
	/**
	 * Sets the thumbnail to be a jpeg compressed bitmap. Clears any prior
	 * thumbnail.
	 *
	 * @param thumb a bitmap to compress to a jpeg thumbnail.
	 * @return true if the thumbnail was set.
	 */
	fun setCompressedThumbnail(thumb : Bitmap) : Boolean {
		val thumbnail = ByteArrayOutputStream()
		return if(! thumb.compress(Bitmap.CompressFormat.JPEG, 90, thumbnail)) {
			false
		} else setCompressedThumbnail(thumbnail.toByteArray())
	}
	
	/**
	 * Sets the thumbnail to be a jpeg compressed image. Clears any prior
	 * thumbnail.
	 *
	 * @param thumb a byte array containing a jpeg compressed image.
	 * @return true if the thumbnail was set.
	 */
	private fun setCompressedThumbnail(thumb : ByteArray) : Boolean {
		mData.clearThumbnailAndStrips()
		mData.compressedThumbnail = thumb
		return true
	}
	
	/**
	 * Clears the compressed thumbnail if it exists.
	 */
	fun removeCompressedThumbnail() {
		mData.compressedThumbnail = null
	}
	
	/**
	 * Return the altitude in meters. If the exif tag does not exist, return
	 * <var>defaultValue</var>.
	 *
	 * @param defaultValue the value to return if the tag is not available.
	 */
	fun getAltitude(defaultValue : Double) : Double {
		
		val ref = getTagByteValue(TAG_GPS_ALTITUDE_REF)
		val gpsAltitude = getTagRationalValue(TAG_GPS_ALTITUDE)
		
		var seaLevel = 1
		if(null != ref) {
			seaLevel = if(ref == 1.toByte()) - 1 else 1
		}
		
		return if(gpsAltitude != null) {
			gpsAltitude.toDouble() * seaLevel
		} else defaultValue
		
	}
	
	/**
	 * Creates, formats, and sets the DateTimeStamp tag for one of:
	 * [.TAG_DATE_TIME], [.TAG_DATE_TIME_DIGITIZED],
	 * [.TAG_DATE_TIME_ORIGINAL].
	 *
	 * @param tagId     one of the DateTimeStamp tags.
	 * @param timestamp a timestamp to format.
	 * @param timezone  a TimeZone object.
	 * @return true if success, false if the tag could not be set.
	 */
	fun addDateTimeStampTag(tagId : Int, timestamp : Long, timezone : TimeZone) : Boolean {
		if(tagId == TAG_DATE_TIME || tagId == TAG_DATE_TIME_DIGITIZED || tagId == TAG_DATE_TIME_ORIGINAL) {
			mDateTimeStampFormat.timeZone = timezone
			val t = buildTag(tagId, mDateTimeStampFormat.format(timestamp)) ?: return false
			setTag(t)
		} else {
			return false
		}
		return true
	}
	
	/**
	 * Creates a tag for a defined tag constant in a given IFD if that IFD is
	 * allowed for the tag.  This method will fail anytime the appropriate
	 * [ExifTag.setValue] for this tag's datatype would fail.
	 *
	 * @param tagId a tag constant, e.g. [.TAG_IMAGE_WIDTH].
	 * @param ifdId the IFD that the tag should be in.
	 * @param tagValue   the value of the tag to set.
	 * @return an ExifTag object or null if one could not be constructed.
	 * @see .buildTag
	 */
	fun buildTag(tagId : Int, tagValue : Any, ifdId : Int = getTrueIfd(tagId)) : ExifTag? {
		val info = tagInfo.get(tagId)
		if(info == 0 || ! isIfdAllowed(info, ifdId)) return null
		
		val definedCount = getComponentCountFromInfo(info)
		
		val t = ExifTag(
			tagId = getTrueTagKey(tagId),
			dataType = getTypeFromInfo(info),
			componentCount = definedCount,
			ifd = ifdId,
			mHasDefinedDefaultComponentCount = definedCount != ExifTag.SIZE_UNDEFINED
		)
		
		return when {
			t.setValueAny(tagValue) -> t
			else -> null
		}
	}
	
	/**
	 * Creates and sets all to the GPS tags for a give latitude and longitude.
	 *
	 * @param latitude  a GPS latitude coordinate.
	 * @param longitude a GPS longitude coordinate.
	 * @return true if success, false if they could not be created or set.
	 */
	fun addGpsTags(latitude : Double, longitude : Double) : Boolean {
		val latTag = buildTag(TAG_GPS_LATITUDE, toExifLatLong(latitude))
		val longTag = buildTag(TAG_GPS_LONGITUDE, toExifLatLong(longitude))
		val latRefTag = buildTag(
			TAG_GPS_LATITUDE_REF,
			if(latitude >= 0) GpsLatitudeRef.NORTH else GpsLatitudeRef.SOUTH
		)
		val longRefTag = buildTag(
			TAG_GPS_LONGITUDE_REF,
			if(longitude >= 0) GpsLongitudeRef.EAST else GpsLongitudeRef.WEST
		)
		if(latTag == null || longTag == null || latRefTag == null || longRefTag == null) {
			return false
		}
		setTag(latTag)
		setTag(longTag)
		setTag(latRefTag)
		setTag(longRefTag)
		return true
	}
	
	/**
	 * Creates and sets the GPS timestamp tag.
	 *
	 * @param timestamp a GPS timestamp.
	 * @return true if success, false if could not be created or set.
	 */
	fun addGpsDateTimeStampTag(timestamp : Long) : Boolean {
		var t = buildTag(TAG_GPS_DATE_STAMP, mGPSDateStampFormat.format(timestamp)) ?: return false
		setTag(t)
		mGPSTimeStampCalendar.timeInMillis = timestamp
		t = buildTag(
			TAG_GPS_TIME_STAMP,
			arrayOf(
				Rational(mGPSTimeStampCalendar.get(Calendar.HOUR_OF_DAY).toLong(), 1),
				Rational(mGPSTimeStampCalendar.get(Calendar.MINUTE).toLong(), 1),
				Rational(mGPSTimeStampCalendar.get(Calendar.SECOND).toLong(), 1)
			)
		) ?: return false
		setTag(t)
		return true
	}
	
	/**
	 * Constants for [.TAG_ORIENTATION]. They can be interpreted as
	 * follows:
	 *
	 *  * TOP_LEFT is the normal orientation.
	 *  * TOP_RIGHT is a left-right mirror.
	 *  * BOTTOM_LEFT is a 180 degree rotation.
	 *  * BOTTOM_RIGHT is a top-bottom mirror.
	 *  * LEFT_TOP is mirrored about the top-left<->bottom-right axis.
	 *  * RIGHT_TOP is a 90 degree clockwise rotation.
	 *  * LEFT_BOTTOM is mirrored about the top-right<->bottom-left axis.
	 *  * RIGHT_BOTTOM is a 270 degree clockwise rotation.
	 *
	 */
	object Orientation {
		
		const val TOP_LEFT : Short = 1
		const val TOP_RIGHT : Short = 2
		const val BOTTOM_RIGHT : Short = 3
		const val BOTTOM_LEFT : Short = 4
		const val LEFT_TOP : Short = 5
		const val RIGHT_TOP : Short = 6
		const val RIGHT_BOTTOM : Short = 7
		const val LEFT_BOTTOM : Short = 8
	}
	
	/**
	 * Constants for [.TAG_Y_CB_CR_POSITIONING]
	 */
	object YCbCrPositioning {
		
		const val CENTERED : Short = 1
		const val CO_SITED : Short = 2
	}
	
	/**
	 * Constants for [.TAG_COMPRESSION]
	 */
	object Compression {
		
		const val UNCOMPRESSION : Short = 1
		const val JPEG : Short = 6
	}
	
	/**
	 * Constants for [.TAG_RESOLUTION_UNIT]
	 */
	object ResolutionUnit {
		
		const val INCHES : Short = 2
		const val CENTIMETERS : Short = 3
		const val MILLIMETERS : Short = 4
		const val MICROMETERS : Short = 5
	}
	
	/**
	 * Constants for [.TAG_PHOTOMETRIC_INTERPRETATION]
	 */
	object PhotometricInterpretation {
		
		const val RGB : Short = 2
		const val YCBCR : Short = 6
	}
	
	/**
	 * Constants for [.TAG_PLANAR_CONFIGURATION]
	 */
	object PlanarConfiguration {
		
		const val CHUNKY : Short = 1
		const val PLANAR : Short = 2
	}
	
	// Convenience methods:
	
	/**
	 * Constants for [.TAG_EXPOSURE_PROGRAM]
	 */
	object ExposureProgram {
		
		const val NOT_DEFINED : Short = 0
		const val MANUAL : Short = 1
		const val NORMAL_PROGRAM : Short = 2
		const val APERTURE_PRIORITY : Short = 3
		const val SHUTTER_PRIORITY : Short = 4
		const val CREATIVE_PROGRAM : Short = 5
		const val ACTION_PROGRAM : Short = 6
		const val PROTRAIT_MODE : Short = 7
		const val LANDSCAPE_MODE : Short = 8
	}
	
	/**
	 * Constants for [.TAG_METERING_MODE]
	 */
	object MeteringMode {
		
		const val UNKNOWN : Short = 0
		const val AVERAGE : Short = 1
		const val CENTER_WEIGHTED_AVERAGE : Short = 2
		const val SPOT : Short = 3
		const val MULTISPOT : Short = 4
		const val PATTERN : Short = 5
		const val PARTAIL : Short = 6
		const val OTHER : Short = 255
	}
	
	/**
	 * Constants for [.TAG_FLASH] As the definition in Jeita EXIF 2.2
	 */
	object Flash {
		
		/**
		 * first bit
		 */
		enum class FlashFired {
			
			NO, YES
		}
		
		/**
		 * Values for bits 1 and 2 indicating the status of returned light
		 */
		enum class StrobeLightDetection {
			
			NO_DETECTION, RESERVED, LIGHT_NOT_DETECTED, LIGHT_DETECTED
		}
		
		/**
		 * Values for bits 3 and 4 indicating the camera's flash mode
		 */
		enum class CompulsoryMode {
			
			UNKNOWN,
			FIRING,
			SUPPRESSION,
			AUTO
		}
		
		/**
		 * Values for bit 5 indicating the presence of a flash function.
		 */
		enum class FlashFunction {
			
			FUNCTION_PRESENT,
			FUNCTION_NOR_PRESENT
		}
		
		/**
		 * Values for bit 6 indicating the camera's red-eye mode.
		 */
		enum class RedEyeMode {
			
			NONE,
			SUPPORTED
		}
	}
	
	/**
	 * Constants for [.TAG_COLOR_SPACE]
	 */
	object ColorSpace {
		
		const val SRGB : Short = 1
		const val UNCALIBRATED = 0xFFFF.toShort()
	}
	
	/**
	 * Constants for [.TAG_EXPOSURE_MODE]
	 */
	object ExposureMode {
		
		const val AUTO_EXPOSURE : Short = 0
		const val MANUAL_EXPOSURE : Short = 1
		const val AUTO_BRACKET : Short = 2
	}
	
	/**
	 * Constants for [.TAG_WHITE_BALANCE]
	 */
	object WhiteBalance {
		
		const val AUTO : Short = 0
		const val MANUAL : Short = 1
	}
	
	/**
	 * Constants for [.TAG_SCENE_CAPTURE_TYPE]
	 */
	object SceneCapture {
		
		const val STANDARD : Short = 0
		const val LANDSCAPE : Short = 1
		const val PROTRAIT : Short = 2
		const val NIGHT_SCENE : Short = 3
	}
	
	/**
	 * Constants for [.TAG_COMPONENTS_CONFIGURATION]
	 */
	object ComponentsConfiguration {
		
		const val NOT_EXIST : Short = 0
		const val Y : Short = 1
		const val CB : Short = 2
		const val CR : Short = 3
		const val R : Short = 4
		const val G : Short = 5
		const val B : Short = 6
	}
	
	/**
	 * Constants for [.TAG_LIGHT_SOURCE]
	 */
	object LightSource {
		
		const val UNKNOWN : Short = 0
		const val DAYLIGHT : Short = 1
		const val FLUORESCENT : Short = 2
		const val TUNGSTEN : Short = 3
		const val FLASH : Short = 4
		const val FINE_WEATHER : Short = 9
		const val CLOUDY_WEATHER : Short = 10
		const val SHADE : Short = 11
		const val DAYLIGHT_FLUORESCENT : Short = 12
		const val DAY_WHITE_FLUORESCENT : Short = 13
		const val COOL_WHITE_FLUORESCENT : Short = 14
		const val WHITE_FLUORESCENT : Short = 15
		const val STANDARD_LIGHT_A : Short = 17
		const val STANDARD_LIGHT_B : Short = 18
		const val STANDARD_LIGHT_C : Short = 19
		const val D55 : Short = 20
		const val D65 : Short = 21
		const val D75 : Short = 22
		const val D50 : Short = 23
		const val ISO_STUDIO_TUNGSTEN : Short = 24
		const val OTHER : Short = 255
	}
	
	/**
	 * Constants for [.TAG_SENSING_METHOD]
	 */
	object SensingMethod {
		
		const val NOT_DEFINED : Short = 1
		const val ONE_CHIP_COLOR : Short = 2
		const val TWO_CHIP_COLOR : Short = 3
		const val THREE_CHIP_COLOR : Short = 4
		const val COLOR_SEQUENTIAL_AREA : Short = 5
		const val TRILINEAR : Short = 7
		const val COLOR_SEQUENTIAL_LINEAR : Short = 8
	}
	
	/**
	 * Constants for [.TAG_FILE_SOURCE]
	 */
	object FileSource {
		
		const val DSC : Short = 3
	}
	
	/**
	 * Constants for [.TAG_SCENE_TYPE]
	 */
	object SceneType {
		
		const val DIRECT_PHOTOGRAPHED : Short = 1
	}
	
	/**
	 * Constants for [.TAG_GAIN_CONTROL]
	 */
	object GainControl {
		
		const val NONE : Short = 0
		const val LOW_UP : Short = 1
		const val HIGH_UP : Short = 2
		const val LOW_DOWN : Short = 3
		const val HIGH_DOWN : Short = 4
	}
	
	/**
	 * Constants for [.TAG_CONTRAST]
	 */
	object Contrast {
		
		const val NORMAL : Short = 0
		const val SOFT : Short = 1
		const val HARD : Short = 2
	}
	
	/**
	 * Constants for [.TAG_SATURATION]
	 */
	object Saturation {
		
		const val NORMAL : Short = 0
		const val LOW : Short = 1
		const val HIGH : Short = 2
	}
	
	/**
	 * Constants for [.TAG_SHARPNESS]
	 */
	object Sharpness {
		
		const val NORMAL : Short = 0
		const val SOFT : Short = 1
		const val HARD : Short = 2
	}
	
	/**
	 * Constants for [.TAG_SUBJECT_DISTANCE]
	 */
	object SubjectDistance {
		
		const val UNKNOWN : Short = 0
		const val MACRO : Short = 1
		const val CLOSE_VIEW : Short = 2
		const val DISTANT_VIEW : Short = 3
	}
	
	/**
	 * Constants for [.TAG_GPS_LATITUDE_REF],
	 * [.TAG_GPS_DEST_LATITUDE_REF]
	 */
	object GpsLatitudeRef {
		
		const val NORTH = "N"
		const val SOUTH = "S"
	}
	
	/**
	 * Constants for [.TAG_GPS_LONGITUDE_REF],
	 * [.TAG_GPS_DEST_LONGITUDE_REF]
	 */
	object GpsLongitudeRef {
		
		const val EAST = "E"
		const val WEST = "W"
	}
	
	/**
	 * Constants for [.TAG_GPS_ALTITUDE_REF]
	 */
	object GpsAltitudeRef {
		
		const val SEA_LEVEL : Short = 0
		const val SEA_LEVEL_NEGATIVE : Short = 1
	}
	
	/**
	 * Constants for [.TAG_GPS_STATUS]
	 */
	object GpsStatus {
		
		const val IN_PROGRESS = "A"
		const val INTEROPERABILITY = "V"
	}
	
	/**
	 * Constants for [.TAG_GPS_MEASURE_MODE]
	 */
	object GpsMeasureMode {
		
		const val MODE_2_DIMENSIONAL = "2"
		const val MODE_3_DIMENSIONAL = "3"
	}
	
	/**
	 * Constants for [.TAG_GPS_SPEED_REF],
	 * [.TAG_GPS_DEST_DISTANCE_REF]
	 */
	object GpsSpeedRef {
		
		const val KILOMETERS = "K"
		const val MILES = "M"
		const val KNOTS = "N"
	}
	
	/**
	 * Constants for [.TAG_GPS_TRACK_REF],
	 * [.TAG_GPS_IMG_DIRECTION_REF], [.TAG_GPS_DEST_BEARING_REF]
	 */
	object GpsTrackRef {
		
		const val TRUE_DIRECTION = "T"
		const val MAGNETIC_DIRECTION = "M"
	}
	
	/**
	 * Constants for [.TAG_GPS_DIFFERENTIAL]
	 */
	object GpsDifferential {
		
		const val WITHOUT_DIFFERENTIAL_CORRECTION : Short = 0
		const val DIFFERENTIAL_CORRECTION_APPLIED : Short = 1
	}
	
	/**
	 * Constants for the jpeg process algorithm used.
	 *
	 * @see .getJpegProcess
	 */
	object JpegProcess {
		
		const val BASELINE = 0xFFC0.toShort()
		const val EXTENDED_SEQUENTIAL = 0xFFC1.toShort()
		const val PROGRESSIVE = 0xFFC2.toShort()
		const val LOSSLESS = 0xFFC3.toShort()
		const val DIFFERENTIAL_SEQUENTIAL = 0xFFC5.toShort()
		const val DIFFERENTIAL_PROGRESSIVE = 0xFFC6.toShort()
		const val DIFFERENTIAL_LOSSLESS = 0xFFC7.toShort()
		const val EXTENDED_SEQ_ARITHMETIC_CODING = 0xFFC9.toShort()
		const val PROGRESSIVE_AIRTHMETIC_CODING = 0xFFCA.toShort()
		const val LOSSLESS_AITHMETIC_CODING = 0xFFCB.toShort()
		const val DIFFERENTIAL_SEQ_ARITHMETIC_CODING = 0xFFCD.toShort()
		const val DIFFERENTIAL_PROGRESSIVE_ARITHMETIC_CODING = 0xFFCE.toShort()
		const val DIFFERENTIAL_LOSSLESS_ARITHMETIC_CODING = 0xFFCF.toShort()
	}
	
	/**
	 * Constants for the [.TAG_SENSITIVITY_TYPE] tag
	 */
	object SensitivityType {
		
		
		const val UNKNOWN : Short = 0
		
		/**
		 * Standard output sensitivity
		 */
		const val SOS : Short = 1
		
		/**
		 * Recommended exposure index
		 */
		const val REI : Short = 2
		
		/**
		 * ISO Speed
		 */
		const val ISO : Short = 3
		
		/**
		 * Standard output sensitivity and Recommended output index
		 */
		const val SOS_REI : Short = 4
		
		/**
		 * Standard output sensitivity and ISO speed
		 */
		const val SOS_ISO : Short = 5
		
		/**
		 * Recommended output index and ISO Speed
		 */
		const val REI_ISO : Short = 6
		
		/**
		 * Standard output sensitivity and Recommended output index and ISO Speed
		 */
		const val SOS_REI_ISO : Short = 7
	}
	
	/**
	 * Options for calling [.readExif], [.readExif],
	 * [.readExif]
	 */
	object Options {
		
		/**
		 * Option bit to request to parse IFD0.
		 */
		const val OPTION_IFD_0 = 1
		/**
		 * Option bit to request to parse IFD1.
		 */
		const val OPTION_IFD_1 = 1 shl 1
		/**
		 * Option bit to request to parse Exif-IFD.
		 */
		const val OPTION_IFD_EXIF = 1 shl 2
		/**
		 * Option bit to request to parse GPS-IFD.
		 */
		const val OPTION_IFD_GPS = 1 shl 3
		/**
		 * Option bit to request to parse Interoperability-IFD.
		 */
		const val OPTION_IFD_INTEROPERABILITY = 1 shl 4
		/**
		 * Option bit to request to parse thumbnail.
		 */
		const val OPTION_THUMBNAIL = 1 shl 5
		/**
		 * Option bit to request all the options
		 */
		const val OPTION_ALL =
			OPTION_IFD_0 xor OPTION_IFD_1 xor OPTION_IFD_EXIF xor OPTION_IFD_GPS xor OPTION_IFD_INTEROPERABILITY xor OPTION_THUMBNAIL
		
	}
	
	@Suppress("unused")
	companion object {
		
		private const val TAG = "ExifInterface"
		
		const val TAG_NULL = - 1
		const val IFD_NULL = - 1
		const val DEFINITION_NULL = 0
		
		/**
		 * Tag constants for Jeita EXIF 2.2
		 */
		
		// IFD 0
		private val TAG_IMAGE_WIDTH = defineTag(IfdData.TYPE_IFD_0, 0x0100.toShort())
		private val TAG_IMAGE_LENGTH =
			defineTag(IfdData.TYPE_IFD_0, 0x0101.toShort()) // Image height
		private val TAG_BITS_PER_SAMPLE = defineTag(IfdData.TYPE_IFD_0, 0x0102.toShort())
		
		/**
		 * Value is unsigned int.<br></br>
		 * (Read only tag) The compression scheme used for the image data. When a primary image is JPEG compressed, this designation is
		 * not necessary and is omitted. When thumbnails use JPEG compression, this tag value is set to 6.
		 *
		 *  * 1 = uncompressed
		 *  * 6 = JPEG compression (thumbnails only)
		 *  * Other = reserved
		 */
		private val TAG_COMPRESSION = defineTag(IfdData.TYPE_IFD_0, 0x0103.toShort())
		private val TAG_PHOTOMETRIC_INTERPRETATION = defineTag(IfdData.TYPE_IFD_0, 0x0106.toShort())
		private val TAG_IMAGE_DESCRIPTION = defineTag(IfdData.TYPE_IFD_0, 0x010E.toShort())
		
		/**
		 * Value is ascii string<br></br>
		 * The manufacturer of the recording equipment. This is the manufacturer of the DSC, scanner, video digitizer or other equipment
		 * that generated the image. When the field is left blank, it is treated as unknown.
		 */
		private val TAG_MAKE = defineTag(IfdData.TYPE_IFD_0, 0x010F.toShort())
		
		/**
		 * Value is ascii string<br></br>
		 * The model name or model number of the equipment. This is the model name of number of the DSC, scanner, video digitizer or
		 * other equipment that generated the image. When the field is left blank, it is treated as unknown.
		 */
		private val TAG_MODEL = defineTag(IfdData.TYPE_IFD_0, 0x0110.toShort())
		val TAG_STRIP_OFFSETS = defineTag(IfdData.TYPE_IFD_0, 0x0111.toShort())
		
		/**
		 * Value is int<br></br>
		 * The orientation of the camera relative to the scene, when the image was captured. The start point of stored data is:
		 *
		 *  * '0' undefined
		 *  * '1' normal
		 *  * '2' flip horizontal
		 *  * '3' rotate 180
		 *  * '4' flip vertical
		 *  * '5' transpose, flipped about top-left <--> bottom-right axis
		 *  * '6' rotate 90 cw
		 *  * '7' transverse, flipped about top-right <--> bottom-left axis
		 *  * '8' rotate 270
		 *  * '9' undefined
		 *
		 */
		val TAG_ORIENTATION = defineTag(IfdData.TYPE_IFD_0, 0x0112.toShort())
		private val TAG_SAMPLES_PER_PIXEL = defineTag(IfdData.TYPE_IFD_0, 0x0115.toShort())
		private val TAG_ROWS_PER_STRIP = defineTag(IfdData.TYPE_IFD_0, 0x0116.toShort())
		val TAG_STRIP_BYTE_COUNTS = defineTag(IfdData.TYPE_IFD_0, 0x0117.toShort())
		
		private val TAG_INTEROP_VERSION =
			defineTag(IfdData.TYPE_IFD_INTEROPERABILITY, 0x0002.toShort())
		
		/**
		 * Value is unsigned double.<br></br>
		 * Display/Print resolution of image. Large number of digicam uses 1/72inch, but it has no mean because personal computer doesn't
		 * use this value to display/print out.
		 */
		private val TAG_X_RESOLUTION = defineTag(IfdData.TYPE_IFD_0, 0x011A.toShort())
		
		/**
		 * @see .TAG_X_RESOLUTION
		 */
		private val TAG_Y_RESOLUTION = defineTag(IfdData.TYPE_IFD_0, 0x011B.toShort())
		private val TAG_PLANAR_CONFIGURATION = defineTag(IfdData.TYPE_IFD_0, 0x011C.toShort())
		
		/**
		 * Value is unsigned int.<br></br>
		 * Unit of XResolution(0x011a)/YResolution(0x011b)
		 *
		 *  * '1' means no-unit ( use inch )
		 *  * '2' inch
		 *  * '3' centimeter
		 *  * '4' millimeter
		 *  * '5' micrometer
		 *
		 */
		private val TAG_RESOLUTION_UNIT = defineTag(IfdData.TYPE_IFD_0, 0x0128.toShort())
		private val TAG_TRANSFER_FUNCTION = defineTag(IfdData.TYPE_IFD_0, 0x012D.toShort())
		
		/**
		 * Value is ascii string<br></br>
		 * Shows firmware(internal software of digicam) version number.
		 */
		private val TAG_SOFTWARE = defineTag(IfdData.TYPE_IFD_0, 0x0131.toShort())
		
		/**
		 * Value is ascii string (20)<br></br>
		 * Date/Time of image was last modified. Data format is "YYYY:MM:DD HH:MM:SS"+0x00, total 20bytes. In usual, it has the same
		 * value of DateTimeOriginal(0x9003)
		 */
		val TAG_DATE_TIME = defineTag(IfdData.TYPE_IFD_0, 0x0132.toShort())
		
		/**
		 * Vallue is ascii String<br></br>
		 * This tag records the name of the camera owner, photographer or image creator. The detailed format is not specified, but it is
		 * recommended that the information be written as in the example below for ease of Interoperability. When the field is left
		 * blank, it is treated as unknown.
		 */
		private val TAG_ARTIST = defineTag(IfdData.TYPE_IFD_0, 0x013B.toShort())
		private val TAG_WHITE_POINT = defineTag(IfdData.TYPE_IFD_0, 0x013E.toShort())
		private val TAG_PRIMARY_CHROMATICITIES = defineTag(IfdData.TYPE_IFD_0, 0x013F.toShort())
		private val TAG_Y_CB_CR_COEFFICIENTS = defineTag(IfdData.TYPE_IFD_0, 0x0211.toShort())
		private val TAG_Y_CB_CR_SUB_SAMPLING = defineTag(IfdData.TYPE_IFD_0, 0x0212.toShort())
		private val TAG_Y_CB_CR_POSITIONING = defineTag(IfdData.TYPE_IFD_0, 0x0213.toShort())
		private val TAG_REFERENCE_BLACK_WHITE = defineTag(IfdData.TYPE_IFD_0, 0x0214.toShort())
		
		/**
		 * Values is ascii string<br></br>
		 * Shows copyright information
		 */
		private val TAG_COPYRIGHT = defineTag(IfdData.TYPE_IFD_0, 0x8298.toShort())
		val TAG_EXIF_IFD = defineTag(IfdData.TYPE_IFD_0, 0x8769.toShort())
		val TAG_GPS_IFD = defineTag(IfdData.TYPE_IFD_0, 0x8825.toShort())
		// IFD 1
		val TAG_JPEG_INTERCHANGE_FORMAT = defineTag(IfdData.TYPE_IFD_1, 0x0201.toShort())
		val TAG_JPEG_INTERCHANGE_FORMAT_LENGTH = defineTag(IfdData.TYPE_IFD_1, 0x0202.toShort())
		// IFD Exif Tags
		
		/**
		 * Value is unsigned double<br></br>
		 * Exposure time (reciprocal of shutter speed). Unit is second
		 */
		private val TAG_EXPOSURE_TIME = defineTag(IfdData.TYPE_IFD_EXIF, 0x829A.toShort())
		
		/**
		 * Value is unsigned double<br></br>
		 * The actual F-number(F-stop) of lens when the image was taken
		 *
		 * @see .TAG_APERTURE_VALUE
		 */
		val TAG_F_NUMBER = defineTag(IfdData.TYPE_IFD_EXIF, 0x829D.toShort())
		
		/**
		 * Value is unsigned int.<br></br>
		 * Exposure program that the camera used when image was taken.
		 *
		 *  * '1' means manual control
		 *  * '2' program normal
		 *  * '3' aperture priority
		 *  * '4' shutter priority
		 *  * '5' program creative (slow program)
		 *  * '6' program action(high-speed program)
		 *  * '7' portrait mode
		 *  * '8' landscape mode.
		 *
		 */
		private val TAG_EXPOSURE_PROGRAM = defineTag(IfdData.TYPE_IFD_EXIF, 0x8822.toShort())
		private val TAG_SPECTRAL_SENSITIVITY = defineTag(IfdData.TYPE_IFD_EXIF, 0x8824.toShort())
		
		/**
		 * Value is unsigned int.<br></br>
		 * CCD sensitivity equivalent to Ag-Hr film speedrate.<br></br>
		 * Indicates the ISO Speed and ISO Latitude of the camera or input device as specified in ISO 12232
		 */
		private val TAG_ISO_SPEED_RATINGS = defineTag(IfdData.TYPE_IFD_EXIF, 0x8827.toShort())
		private val TAG_OECF = defineTag(IfdData.TYPE_IFD_EXIF, 0x8828.toShort())
		
		/**
		 * ASCII string (4).<br></br>
		 * The version of this standard supported. Nonexistence of this field is taken to mean nonconformance to the standard (see
		 * section 4.2). Conformance to this standard is indicated by recording "0220" as 4-byte ASCII
		 */
		private val TAG_EXIF_VERSION = defineTag(IfdData.TYPE_IFD_EXIF, 0x9000.toShort())
		
		/**
		 * Value is ascii string (20)<br></br>
		 * Date/Time of original image taken. This value should not be modified by user program.
		 */
		val TAG_DATE_TIME_ORIGINAL = defineTag(IfdData.TYPE_IFD_EXIF, 0x9003.toShort())
		
		/**
		 * Value is ascii string (20)<br></br>
		 * Date/Time of image digitized. Usually, it contains the same value of DateTimeOriginal(0x9003).
		 */
		val TAG_DATE_TIME_DIGITIZED = defineTag(IfdData.TYPE_IFD_EXIF, 0x9004.toShort())
		private val TAG_COMPONENTS_CONFIGURATION =
			defineTag(IfdData.TYPE_IFD_EXIF, 0x9101.toShort())
		private val TAG_COMPRESSED_BITS_PER_PIXEL =
			defineTag(IfdData.TYPE_IFD_EXIF, 0x9102.toShort())
		
		/**
		 * Value is signed double.<br></br>
		 * Shutter speed. To convert this value to ordinary 'Shutter Speed'; calculate this value's power of 2, then reciprocal. For
		 * example, if value is '4', shutter speed is 1/(2^4)=1/16 second.
		 */
		private val TAG_SHUTTER_SPEED_VALUE = defineTag(IfdData.TYPE_IFD_EXIF, 0x9201.toShort())
		
		/**
		 * Value is unsigned double<br></br>
		 * The actual aperture value of lens when the image was taken.<br></br>
		 * To convert this value to ordinary F-number(F-stop), calculate this value's power of root 2 (=1.4142).<br></br>
		 * For example, if value is '5', F-number is 1.4142^5 = F5.6<br></br>
		 *
		 *
		 * <pre>
		 * FNumber = Math.exp( ApertureValue * Math.log( 2 ) * 0.5 );
		</pre> *
		 *
		 * @see .TAG_F_NUMBER
		 */
		val TAG_APERTURE_VALUE = defineTag(IfdData.TYPE_IFD_EXIF, 0x9202.toShort())
		
		/**
		 * Value is signed double<br></br>
		 * Brightness of taken subject, unit is EV.
		 */
		private val TAG_BRIGHTNESS_VALUE = defineTag(IfdData.TYPE_IFD_EXIF, 0x9203.toShort())
		
		/**
		 * Value is signed double.<br></br>
		 * The exposure bias. The unit is the APEX value. Ordinarily it is given in the range of -99.99 to 99.99
		 */
		private val TAG_EXPOSURE_BIAS_VALUE = defineTag(IfdData.TYPE_IFD_EXIF, 0x9204.toShort())
		
		/**
		 * Value is unsigned double.<br></br>
		 * Maximum aperture value of lens.<br></br>
		 * You can convert to F-number by calculating power of root 2 (same process of ApertureValue(0x9202).<br></br>
		 *
		 *
		 * <pre>
		 * FNumber = Math.exp( MaxApertureValue * Math.log( 2 ) * 0.5 )
		</pre> *
		 */
		private val TAG_MAX_APERTURE_VALUE = defineTag(IfdData.TYPE_IFD_EXIF, 0x9205.toShort())
		
		/**
		 * Value if signed double.<br></br>
		 * Distance to focus point, unit is meter. If value < 0 then focus point is infinite
		 */
		private val TAG_SUBJECT_DISTANCE = defineTag(IfdData.TYPE_IFD_EXIF, 0x9206.toShort())
		
		/**
		 * Value is unsigned int.<br></br>
		 * Exposure metering method:
		 *
		 *  * 0 = unknown
		 *  * 1 = Average
		 *  * 2 = CenterWeightedAverage
		 *  * 3 = Spot
		 *  * 4 = MultiSpot
		 *  * 5 = Pattern
		 *  * 6 = Partial
		 *  * Other = reserved
		 *  * 255 = other
		 *
		 */
		private val TAG_METERING_MODE = defineTag(IfdData.TYPE_IFD_EXIF, 0x9207.toShort())
		
		/**
		 * Value is unsigned int.<br></br>
		 * Light source, actually this means white balance setting.
		 *
		 *  * 0 = means auto
		 *  * 1 = Daylight
		 *  * 2 = Fluorescent
		 *  * 3 = Tungsten (incandescent light)
		 *  * 4 = Flash
		 *  * 9 = Fine weather
		 *  * 10 = Cloudy weather
		 *  * 11 = Shade
		 *  * 12 = Daylight fluorescent (D 5700 - 7100K)
		 *  * 13 = Day white fluorescent (N 4600 - 5400K)
		 *  * 14 = Cool white fluorescent (W 3900 - 4500K)
		 *  * 15 = White fluorescent (WW 3200 - 3700K)
		 *  * 17 = Standard light A
		 *  * 18 = Standard light B
		 *  * 19 = Standard light C
		 *  * 20 = D55
		 *  * 21 = D65
		 *  * 22 = D75
		 *  * 23 = D50
		 *  * 24 = ISO studio tungsten
		 *  * 255 = other light source
		 *  * Other = reserved
		 *
		 */
		private val TAG_LIGHT_SOURCE = defineTag(IfdData.TYPE_IFD_EXIF, 0x9208.toShort())
		
		/**
		 * Value is unsigned integer<br></br>
		 * The 8 bits can be extracted and evaluated in this way:<br></br>
		 *
		 *  1. Bit 0 indicates the flash firing status
		 *  1. bits 1 and 2 indicate the flash return status
		 *  1. bits 3 and 4 indicate the flash mode
		 *  1. bit 5 indicates whether the flash function is present
		 *  1. and bit 6 indicates "red eye" mode
		 *  1. bit 7 unused
		 *
		 *
		 *
		 * Resulting Flash tag values are:<br></br>
		 *
		 *  * 0000.H = Flash did not fire
		 *  * 0001.H = Flash fired
		 *  * 0005.H = Strobe return light not detected
		 *  * 0007.H = Strobe return light detected
		 *  * 0009.H = Flash fired, compulsory flash mode
		 *  * 000D.H = Flash fired, compulsory flash mode, return light not detected
		 *  * 000F.H = Flash fired, compulsory flash mode, return light detected
		 *  * 0010.H = Flash did not fire, compulsory flash mode
		 *  * 0018.H = Flash did not fire, auto mode
		 *  * 0019.H = Flash fired, auto mode
		 *  * 001D.H = Flash fired, auto mode, return light not detected
		 *  * 001F.H = Flash fired, auto mode, return light detected
		 *  * 0020.H = No flash function
		 *  * 0041.H = Flash fired, red-eye reduction mode
		 *  * 0045.H = Flash fired, red-eye reduction mode, return light not detected
		 *  * 0047.H = Flash fired, red-eye reduction mode, return light detected
		 *  * 0049.H = Flash fired, compulsory flash mode, red-eye reduction mode
		 *  * 004D.H = Flash fired, compulsory flash mode, red-eye reduction mode, return light not detected
		 *  * 004F.H = Flash fired, compulsory flash mode, red-eye reduction mode, return light detected
		 *  * 0059.H = Flash fired, auto mode, red-eye reduction mode
		 *  * 005D.H = Flash fired, auto mode, return light not detected, red-eye reduction mode
		 *  * 005F.H = Flash fired, auto mode, return light detected, red-eye reduction mode
		 *  * Other = reserved
		 *
		 *
		 * @see [http://www.exif.org/Exif2-2.PDF](http://www.exif.org/Exif2-2.PDF)
		 */
		private val TAG_FLASH = defineTag(IfdData.TYPE_IFD_EXIF, 0x9209.toShort())
		
		/**
		 * Value is unsigned double<br></br>
		 * Focal length of lens used to take image. Unit is millimeter.
		 */
		private val TAG_FOCAL_LENGTH = defineTag(IfdData.TYPE_IFD_EXIF, 0x920A.toShort())
		private val TAG_SUBJECT_AREA = defineTag(IfdData.TYPE_IFD_EXIF, 0x9214.toShort())
		private val TAG_MAKER_NOTE = defineTag(IfdData.TYPE_IFD_EXIF, 0x927C.toShort())
		val TAG_USER_COMMENT = defineTag(IfdData.TYPE_IFD_EXIF, 0x9286.toShort())
		private val TAG_SUB_SEC_TIME = defineTag(IfdData.TYPE_IFD_EXIF, 0x9290.toShort())
		private val TAG_SUB_SEC_TIME_ORIGINAL = defineTag(IfdData.TYPE_IFD_EXIF, 0x9291.toShort())
		private val TAG_SUB_SEC_TIME_DIGITIZED = defineTag(IfdData.TYPE_IFD_EXIF, 0x9292.toShort())
		private val TAG_FLASHPIX_VERSION = defineTag(IfdData.TYPE_IFD_EXIF, 0xA000.toShort())
		
		/**
		 * Value is int.<br></br>
		 * Normally sRGB (=1) is used to define the color space based on the PC monitor conditions and environment. If a color space
		 * other than sRGB is used, Uncalibrated (=FFFF.H) is set. Image data recorded as Uncalibrated can be treated as sRGB when it is
		 * converted to Flashpix. On sRGB see Annex E.
		 *
		 *  * '1' = sRGB
		 *  * 'FFFF' = Uncalibrated
		 *  * 'other' = Reserved
		 *
		 */
		private val TAG_COLOR_SPACE = defineTag(IfdData.TYPE_IFD_EXIF, 0xA001.toShort())
		
		/**
		 * Value is unsigned int.<br></br>
		 * Specific to compressed data; the valid width of the meaningful image. When a compressed file is recorded, the valid width of
		 * the meaningful image shall be recorded in this tag, whether or not there is padding data or a restart marker. This tag should
		 * not exist in an uncompressed file.
		 */
		private val TAG_PIXEL_X_DIMENSION = defineTag(IfdData.TYPE_IFD_EXIF, 0xA002.toShort())
		
		/**
		 * @see .TAG_PIXEL_X_DIMENSION
		 */
		private val TAG_PIXEL_Y_DIMENSION = defineTag(IfdData.TYPE_IFD_EXIF, 0xA003.toShort())
		private val TAG_RELATED_SOUND_FILE = defineTag(IfdData.TYPE_IFD_EXIF, 0xA004.toShort())
		val TAG_INTEROPERABILITY_IFD = defineTag(IfdData.TYPE_IFD_EXIF, 0xA005.toShort())
		private val TAG_FLASH_ENERGY = defineTag(IfdData.TYPE_IFD_EXIF, 0xA20B.toShort())
		private val TAG_SPATIAL_FREQUENCY_RESPONSE =
			defineTag(IfdData.TYPE_IFD_EXIF, 0xA20C.toShort())
		
		/**
		 * Value is unsigned double.<br></br>
		 * Indicates the number of pixels in the image width (X) direction per FocalPlaneResolutionUnit on the camera focal plane. CCD's
		 * pixel density
		 *
		 * @see .TAG_FOCAL_PLANE_RESOLUTION_UNIT
		 */
		private val TAG_FOCAL_PLANE_X_RESOLUTION =
			defineTag(IfdData.TYPE_IFD_EXIF, 0xA20E.toShort())
		
		/**
		 * @see .TAG_FOCAL_PLANE_X_RESOLUTION
		 */
		private val TAG_FOCAL_PLANE_Y_RESOLUTION =
			defineTag(IfdData.TYPE_IFD_EXIF, 0xA20F.toShort())
		
		/**
		 * Value is unsigned int.<br></br>
		 * Unit of FocalPlaneXResoluton/FocalPlaneYResolution.
		 *
		 *  * '1' means no-unit
		 *  * '2' inch
		 *  * '3' centimeter
		 *  * '4' millimeter
		 *  * '5' micrometer
		 *
		 *
		 *
		 * This tag can be used to calculate the CCD Width:
		 *
		 *
		 * <pre>
		 * CCDWidth = ( PixelXDimension * FocalPlaneResolutionUnit / FocalPlaneXResolution )
		</pre> *
		 */
		private val TAG_FOCAL_PLANE_RESOLUTION_UNIT =
			defineTag(IfdData.TYPE_IFD_EXIF, 0xA210.toShort())
		private val TAG_SUBJECT_LOCATION = defineTag(IfdData.TYPE_IFD_EXIF, 0xA214.toShort())
		private val TAG_EXPOSURE_INDEX = defineTag(IfdData.TYPE_IFD_EXIF, 0xA215.toShort())
		
		/**
		 * Value is unsigned int.<br></br>
		 * Indicates the image sensor type on the camera or input device. The values are as follows:
		 *
		 *  * 1 = Not defined
		 *  * 2 = One-chip color area sensor
		 *  * 3 = Two-chip color area sensor JEITA CP-3451 - 41
		 *  * 4 = Three-chip color area sensor
		 *  * 5 = Color sequential area sensor
		 *  * 7 = Trilinear sensor
		 *  * 8 = Color sequential linear sensor
		 *  * Other = reserved
		 *
		 */
		private val TAG_SENSING_METHOD = defineTag(IfdData.TYPE_IFD_EXIF, 0xA217.toShort())
		private val TAG_FILE_SOURCE = defineTag(IfdData.TYPE_IFD_EXIF, 0xA300.toShort())
		private val TAG_SCENE_TYPE = defineTag(IfdData.TYPE_IFD_EXIF, 0xA301.toShort())
		private val TAG_CFA_PATTERN = defineTag(IfdData.TYPE_IFD_EXIF, 0xA302.toShort())
		private val TAG_CUSTOM_RENDERED = defineTag(IfdData.TYPE_IFD_EXIF, 0xA401.toShort())
		
		/**
		 * Value is int.<br></br>
		 * This tag indicates the exposure mode set when the image was shot. In auto-bracketing mode, the camera shoots a series of
		 * frames of the same scene at different exposure settings.
		 *
		 *  * 0 = Auto exposure
		 *  * 1 = Manual exposure
		 *  * 2 = Auto bracket
		 *  * Other = reserved
		 *
		 */
		private val TAG_EXPOSURE_MODE = defineTag(IfdData.TYPE_IFD_EXIF, 0xA402.toShort())
		private val TAG_WHITE_BALANCE = defineTag(IfdData.TYPE_IFD_EXIF, 0xA403.toShort())
		
		/**
		 * Value is double.<br></br>
		 * This tag indicates the digital zoom ratio when the image was shot. If the numerator of the recorded value is 0, this indicates
		 * that digital zoom was not used
		 */
		private val TAG_DIGITAL_ZOOM_RATIO = defineTag(IfdData.TYPE_IFD_EXIF, 0xA404.toShort())
		
		/**
		 * Value is unsigned int.<br></br>
		 * This tag indicates the equivalent focal length assuming a 35mm film camera, in mm.<br></br>
		 * Exif 2.2 tag, usually not present, it can be calculated by:
		 *
		 *
		 * <pre>
		 * CCDWidth = ( PixelXDimension * FocalplaneUnits / FocalplaneXRes );
		 * FocalLengthIn35mmFilm = ( FocalLength / CCDWidth * 36 + 0.5 );
		</pre> *
		 */
		private val TAG_FOCAL_LENGTH_IN_35_MM_FILE =
			defineTag(IfdData.TYPE_IFD_EXIF, 0xA405.toShort())
		
		/**
		 * Value is int.<br></br>
		 * This tag indicates the type of scene that was shot. It can also be used to record the mode in which the image was shot. Note
		 * that this differs from the scene type (SceneType) tag.
		 *
		 *  * 0 = Standard
		 *  * 1 = Landscape
		 *  * 2 = Portrait
		 *  * 3 = Night scene
		 *  * Other = reserved
		 *
		 */
		private val TAG_SCENE_CAPTURE_TYPE = defineTag(IfdData.TYPE_IFD_EXIF, 0xA406.toShort())
		
		/**
		 * Value is int.<br></br>
		 * This tag indicates the degree of overall image gain adjustment.
		 *
		 *  * 0 = None
		 *  * 1 = Low gain up
		 *  * 2 = High gain up
		 *  * 3 = Low gain down
		 *  * 4 = High gain down
		 *  * Other = reserved
		 *
		 */
		private val TAG_GAIN_CONTROL = defineTag(IfdData.TYPE_IFD_EXIF, 0xA407.toShort())
		
		/**
		 * Value is int.<br></br>
		 * This tag indicates the direction of contrast processing applied by the camera when the image was shot.
		 *
		 *  * 0 = Normal
		 *  * 1 = Soft
		 *  * 2 = Hard
		 *  * Other = reserved
		 *
		 */
		private val TAG_CONTRAST = defineTag(IfdData.TYPE_IFD_EXIF, 0xA408.toShort())
		
		/**
		 * Value is int.<br></br>
		 * This tag indicates the direction of saturation processing applied by the camera when the image was shot.
		 *
		 *  * 0 = Normal
		 *  * 1 = Low saturation
		 *  * 2 = High saturation
		 *  * Other = reserved
		 *
		 */
		private val TAG_SATURATION = defineTag(IfdData.TYPE_IFD_EXIF, 0xA409.toShort())
		
		/**
		 * Value is int.<br></br>
		 * This tag indicates the direction of sharpness processing applied by the camera when the image was shot
		 *
		 *  * 0 = Normal
		 *  * 1 = Soft
		 *  * 2 = Hard
		 *  * Other = reserved
		 *
		 */
		private val TAG_SHARPNESS = defineTag(IfdData.TYPE_IFD_EXIF, 0xA40A.toShort())
		private val TAG_DEVICE_SETTING_DESCRIPTION =
			defineTag(IfdData.TYPE_IFD_EXIF, 0xA40B.toShort())
		
		/**
		 * Value is int.<br></br>
		 * This tag indicates the distance to the subject.
		 *
		 *  * 0 = unknown
		 *  * 1 = Macro
		 *  * 2 = Close view
		 *  * 3 = Distant view
		 *  * Other = reserved
		 *
		 */
		private val TAG_SUBJECT_DISTANCE_RANGE = defineTag(IfdData.TYPE_IFD_EXIF, 0xA40C.toShort())
		
		/**
		 * [ExifTag.TYPE_ASCII]
		 */
		private val TAG_IMAGE_UNIQUE_ID = defineTag(IfdData.TYPE_IFD_EXIF, 0xA420.toShort())
		
		/**
		 * Lens Specifications. The value it's a 4 rational containing:
		 *
		 *  1. Minimum focal length (in mm)
		 *  1. Maximum focal length (in mm)
		 *  1. Minimum F Number in the minimum focal length
		 *  1. Maximum F Number in the maximum focal length
		 *
		 *
		 *
		 * [ExifTag.TYPE_RATIONAL]
		 *
		 * @see it.sephiroth.android.library.exif2.ExifUtil.processLensSpecifications
		 * @since EXIF 2.3
		 */
		val TAG_LENS_SPECS = defineTag(IfdData.TYPE_IFD_EXIF, 0xA432.toShort())
		
		/**
		 * Lens maker
		 * [ExifTag.TYPE_ASCII]
		 *
		 * @since EXIF 2.3
		 */
		private val TAG_LENS_MAKE = defineTag(IfdData.TYPE_IFD_EXIF, 0xA433.toShort())
		/**
		 * Lens model name and number
		 * [ExifTag.TYPE_ASCII]
		 *
		 * @since EXIF 2.3
		 */
		val TAG_LENS_MODEL = defineTag(IfdData.TYPE_IFD_EXIF, 0xA434.toShort())
		
		/**
		 * The SensitivityType tag indicates which one of the parameters of ISO12232 is the
		 * PhotographicSensitivity tag. Although it is an optional tag, it should be recorded
		 * when a PhotographicSensitivity tag is recorded.
		 * Value = 4, 5, 6, or 7 may be used in case that the values of plural
		 * parameters are the same.<br></br>
		 * Values:
		 *
		 *  * 0: Unknown
		 *  * 1: Standardoutputsensitivity(SOS)
		 *  * 2: Recommended exposure index (REI)
		 *  * 3: ISOspeed
		 *  * 4: Standard output sensitivity (SOS) and recommended exposure index (REI)
		 *  * 5: Standardoutputsensitivity(SOS)andISOspeed
		 *  * 6: Recommendedexposureindex(REI)andISOspeed
		 *  * 7: Standard output sensitivity (SOS) and recommended exposure index (REI) and ISO speed
		 *  * Other: Reserved
		 *
		 *
		 *
		 * [ExifTag.TYPE_UNSIGNED_SHORT]
		 *
		 * @see it.sephiroth.android.library.exif2.ExifInterface.SensitivityType
		 *
		 * @since EXIF 2.3
		 */
		private val TAG_SENSITIVITY_TYPE = defineTag(IfdData.TYPE_IFD_EXIF, 0x8830.toShort())
		
		// IFD GPS tags
		private val TAG_GPS_VERSION_ID = defineTag(IfdData.TYPE_IFD_GPS, 0.toShort())
		
		/**
		 * Value is string(1)<br></br>
		 * Indicates whether the latitude is north or south latitude. The ASCII value 'N' indicates north latitude, and 'S' is south latitude.
		 */
		val TAG_GPS_LATITUDE_REF = defineTag(IfdData.TYPE_IFD_GPS, 1.toShort())
		
		/**
		 * Value is string.<br></br>
		 * Indicates the latitude. The latitude is expressed as three RATIONAL values giving the degrees, minutes, and
		 * seconds, respectively. If latitude is expressed as degrees, minutes and seconds, a typical format would be
		 * dd/1,mm/1,ss/1. When degrees and minutes are used and, for example, fractions of minutes are given up to two
		 * decimal places, the format would be dd/1,mmmm/100,0/1.
		 */
		val TAG_GPS_LATITUDE = defineTag(IfdData.TYPE_IFD_GPS, 2.toShort())
		
		/**
		 * Value is string(1)<br></br>
		 * Indicates whether the longitude is east or west longitude. ASCII 'E' indicates east longitude, and 'W' is west longitude.
		 */
		val TAG_GPS_LONGITUDE_REF = defineTag(IfdData.TYPE_IFD_GPS, 3.toShort())
		
		/**
		 * Value is string.<br></br>
		 * Indicates the longitude. The longitude is expressed as three RATIONAL values giving the degrees, minutes, and
		 * seconds, respectively. If longitude is expressed as degrees, minutes and seconds, a typical format would be
		 * ddd/1,mm/1,ss/1. When degrees and minutes are used and, for example, fractions of minutes are given up to two
		 * decimal places, the format would be ddd/1,mmmm/100,0/1.
		 */
		val TAG_GPS_LONGITUDE = defineTag(IfdData.TYPE_IFD_GPS, 4.toShort())
		
		/**
		 * Value is byte<br></br>
		 * Indicates the altitude used as the reference altitude. If the reference is sea level and the altitude is above sea level,
		 * 0 is given. If the altitude is below sea level, a value of 1 is given and the altitude is indicated as an absolute value in
		 * the GPSAltitude tag. The reference unit is meters. Note that this tag is BYTE type, unlike other reference tags
		 */
		val TAG_GPS_ALTITUDE_REF = defineTag(IfdData.TYPE_IFD_GPS, 5.toShort())
		
		/**
		 * Value is string.<br></br>
		 * Indicates the altitude based on the reference in GPSAltitudeRef. Altitude is expressed as one RATIONAL value. The reference unit is meters.
		 */
		val TAG_GPS_ALTITUDE = defineTag(IfdData.TYPE_IFD_GPS, 6.toShort())
		val TAG_GPS_TIME_STAMP = defineTag(IfdData.TYPE_IFD_GPS, 7.toShort())
		private val TAG_GPS_SATTELLITES = defineTag(IfdData.TYPE_IFD_GPS, 8.toShort())
		private val TAG_GPS_STATUS = defineTag(IfdData.TYPE_IFD_GPS, 9.toShort())
		private val TAG_GPS_MEASURE_MODE = defineTag(IfdData.TYPE_IFD_GPS, 10.toShort())
		private val TAG_GPS_DOP = defineTag(IfdData.TYPE_IFD_GPS, 11.toShort())
		
		/**
		 * Value is string(1).<br></br>
		 * Indicates the unit used to express the GPS receiver speed of movement. 'K' 'M' and 'N' represents kilometers per  hour, miles per hour, and knots.
		 */
		private val TAG_GPS_SPEED_REF = defineTag(IfdData.TYPE_IFD_GPS, 12.toShort())
		
		/**
		 * Value is string.<br></br>
		 * Indicates the speed of GPS receiver movement
		 */
		private val TAG_GPS_SPEED = defineTag(IfdData.TYPE_IFD_GPS, 13.toShort())
		private val TAG_GPS_TRACK_REF = defineTag(IfdData.TYPE_IFD_GPS, 14.toShort())
		private val TAG_GPS_TRACK = defineTag(IfdData.TYPE_IFD_GPS, 15.toShort())
		private val TAG_GPS_IMG_DIRECTION_REF = defineTag(IfdData.TYPE_IFD_GPS, 16.toShort())
		private val TAG_GPS_IMG_DIRECTION = defineTag(IfdData.TYPE_IFD_GPS, 17.toShort())
		private val TAG_GPS_MAP_DATUM = defineTag(IfdData.TYPE_IFD_GPS, 18.toShort())
		private val TAG_GPS_DEST_LATITUDE_REF = defineTag(IfdData.TYPE_IFD_GPS, 19.toShort())
		private val TAG_GPS_DEST_LATITUDE = defineTag(IfdData.TYPE_IFD_GPS, 20.toShort())
		val TAG_GPS_DEST_LONGITUDE_REF = defineTag(IfdData.TYPE_IFD_GPS, 21.toShort())
		val TAG_GPS_DEST_LONGITUDE = defineTag(IfdData.TYPE_IFD_GPS, 22.toShort())
		private val TAG_GPS_DEST_BEARING_REF = defineTag(IfdData.TYPE_IFD_GPS, 23.toShort())
		private val TAG_GPS_DEST_BEARING = defineTag(IfdData.TYPE_IFD_GPS, 24.toShort())
		private val TAG_GPS_DEST_DISTANCE_REF = defineTag(IfdData.TYPE_IFD_GPS, 25.toShort())
		private val TAG_GPS_DEST_DISTANCE = defineTag(IfdData.TYPE_IFD_GPS, 26.toShort())
		private val TAG_GPS_PROCESSING_METHOD = defineTag(IfdData.TYPE_IFD_GPS, 27.toShort())
		private val TAG_GPS_AREA_INFORMATION = defineTag(IfdData.TYPE_IFD_GPS, 28.toShort())
		val TAG_GPS_DATE_STAMP = defineTag(IfdData.TYPE_IFD_GPS, 29.toShort())
		private val TAG_GPS_DIFFERENTIAL = defineTag(IfdData.TYPE_IFD_GPS, 30.toShort())
		// IFD Interoperability tags
		private val TAG_INTEROPERABILITY_INDEX =
			defineTag(IfdData.TYPE_IFD_INTEROPERABILITY, 1.toShort())
		
		val DEFAULT_BYTE_ORDER : ByteOrder = ByteOrder.BIG_ENDIAN
		private const val NULL_ARGUMENT_STRING = "Argument is null"
		
		private const val GPS_DATE_FORMAT_STR = "yyyy:MM:dd"
		private val mGPSDateStampFormat = SimpleDateFormat(GPS_DATE_FORMAT_STR, Locale.ENGLISH)
			.apply { timeZone = TimeZone.getTimeZone("UTC") }
		
		private const val DATETIME_FORMAT_STR = "yyyy:MM:dd kk:mm:ss"
		private val mDateTimeStampFormat = SimpleDateFormat(DATETIME_FORMAT_STR, Locale.ENGLISH)
		
		/**
		 * Tags that contain offset markers. These are included in the banned
		 * defines.
		 */
		private val sOffsetTags = HashSet<Short>().apply {
			add(getTrueTagKey(TAG_GPS_IFD))
			add(getTrueTagKey(TAG_EXIF_IFD))
			add(getTrueTagKey(TAG_JPEG_INTERCHANGE_FORMAT))
			add(getTrueTagKey(TAG_INTEROPERABILITY_IFD))
			add(getTrueTagKey(TAG_STRIP_OFFSETS))
		}
		
		/**
		 * Tags with definitions that cannot be overridden (banned defines).
		 */
		var sBannedDefines = HashSet(sOffsetTags).apply {
			add(getTrueTagKey(TAG_NULL))
			add(getTrueTagKey(TAG_JPEG_INTERCHANGE_FORMAT_LENGTH))
			add(getTrueTagKey(TAG_STRIP_BYTE_COUNTS))
		}
		
		/**
		 * Returns true if tag TID is one of the following: [.TAG_EXIF_IFD],
		 * [.TAG_GPS_IFD], [.TAG_JPEG_INTERCHANGE_FORMAT],
		 * [.TAG_STRIP_OFFSETS], [.TAG_INTEROPERABILITY_IFD]
		 *
		 *
		 * Note: defining tags with these TID's is disallowed.
		 *
		 * @param tag a tag's TID (can be obtained from a defined tag constant with
		 * [.getTrueTagKey]).
		 * @return true if the TID is that of an offset tag.
		 */
		fun isOffsetTag(tag : Short) : Boolean =
			sOffsetTags.contains(tag)
		
		/**
		 * Returns the Orientation ExifTag value for a given number of degrees.
		 *
		 * @param degreesArg the amount an image is rotated in degrees.
		 */
		fun getOrientationValueForRotation(degreesArg : Int) : Short {
			var degrees = degreesArg
			degrees %= 360
			if(degrees < 0) {
				degrees += 360
			}
			return when {
				degrees < 90 -> Orientation.TOP_LEFT // 0 degrees
				degrees < 180 -> Orientation.RIGHT_TOP // 90 degrees cw
				degrees < 270 -> Orientation.BOTTOM_LEFT // 180 degrees
				else -> Orientation.RIGHT_BOTTOM // 270 degrees cw
			}
		}
		
		/**
		 * Returns the rotation degrees corresponding to an ExifTag Orientation
		 * value.
		 *
		 * @param orientation the ExifTag Orientation value.
		 */
		fun getRotationForOrientationValue(orientation : Short) : Int =
			when(orientation) {
				Orientation.TOP_LEFT -> 0
				Orientation.RIGHT_TOP -> 90
				Orientation.BOTTOM_LEFT -> 180
				Orientation.RIGHT_BOTTOM -> 270
				else -> 0
			}
		
		/**
		 * Gets the double representation of the GPS latitude or longitude
		 * coordinate.
		 *
		 * @param coordinate an array of 3 Rationals representing the degrees,
		 * minutes, and seconds of the GPS location as defined in the
		 * exif specification.
		 * @param reference  a GPS reference reperesented by a String containing "N",
		 * "S", "E", or "W".
		 * @return the GPS coordinate represented as degrees + minutes/60 +
		 * seconds/3600
		 */
		fun convertLatOrLongToDouble(coordinate : Array<Rational>, reference : String) : Double {
			val degrees = coordinate[0].toDouble()
			val minutes = coordinate[1].toDouble()
			val seconds = coordinate[2].toDouble()
			val result = degrees + minutes / 60.0 + seconds / 3600.0
			return when {
				reference.startsWith("S") || reference.startsWith("W") -> - result
				else -> result
			}
		}
		
		fun getAllowedIfdsFromInfo(info : Int) : IntArray? {
			val ifdFlags = getAllowedIfdFlagsFromInfo(info)
			val ifds = IfdData.list
			val l = ArrayList<Int>()
			for(i in 0 until IfdData.TYPE_IFD_COUNT) {
				val flag = ifdFlags shr i and 1
				if(flag == 1) {
					l.add(ifds[i])
				}
			}
			if(l.size <= 0) {
				return null
			}
			val ret = IntArray(l.size)
			var j = 0
			for(i in l) {
				ret[j ++] = i
			}
			return ret
		}
		
		@Throws(IOException::class)
		private fun writeExif_internal(
			input : InputStream,
			output : OutputStream,
			exifData : ExifData
		) : Int {
			// Log.i( TAG, "writeExif_internal" );
			
			// 1. read the output file first
			val src_exif = ExifInterface()
			src_exif.readExif(input, 0)
			
			// 4. Create the destination outputstream
			// 5. write headers
			output.write(0xFF)
			output.write(JpegHeader.TAG_SOI)
			
			val sections = src_exif.mData.sections
			
			// 6. write all the sections from the srcFilename
			if(sections.firstOrNull()?.type != JpegHeader.TAG_M_JFIF) {
				Log.w(TAG, "first section is not a JFIF or EXIF tag")
				output.write(JpegHeader.JFIF_HEADER)
			}
			
			// 6.1 write the *new* EXIF tag
			val eo = ExifOutputStream(src_exif, exifData)
			eo.writeExifData(output)
			
			// 6.2 write all the sections except for the SOS ( start of scan )
			sections.forEach {
				// Log.v( TAG, "writing section.. " + String.format( "0x%2X", current.type ) );
				output.write(0xFF)
				output.write(it.type)
				output.write(it.data)
			}
			
			// 6.3 write the last SOS marker
			val current = sections[sections.size - 1]
			// Log.v( TAG, "writing last section.. " + String.format( "0x%2X", current.type ) );
			output.write(0xFF)
			output.write(current.type)
			output.write(current.data)
			
			// return the position where the input stream should be copied
			return src_exif.mData.mUncompressedDataPosition
		}
		
		/**
		 * Returns the default IFD for a tag constant.
		 */
		fun getTrueIfd(tag : Int) : Int = tag.ushr(16)
		
		/**
		 * Returns the TID for a tag constant.
		 */
		fun getTrueTagKey(tag : Int) : Short = tag.toShort()
		
		private fun getFlagsFromAllowedIfds(allowedIfds : IntArray) : Int {
			if(allowedIfds.isEmpty()) return 0
			var flags = 0
			val ifds = IfdData.list
			for(i in 0 until IfdData.TYPE_IFD_COUNT) {
				for(j in allowedIfds) {
					if(ifds[i] == j) {
						flags = flags or (1 shl i)
						break
					}
				}
			}
			return flags
		}
		
		private fun getComponentCountFromInfo(info : Int) : Int = info and 0x0ffff
		
		private fun getTypeFromInfo(info : Int) : Short = (info shr 16 and 0x0ff).toShort()
		
		/**
		 * Returns the constant representing a tag with a given TID and default IFD.
		 */
		fun defineTag(ifdId : Int, tagId : Short) : Int = tagId or (ifdId shl 16)
		
		private fun convertRationalLatLonToString(
			coord : Array<Rational>,
			refArg : String
		) : String? {
			return try {
				var ref = refArg
				
				val degrees = coord[0].toDouble()
				val minutes = coord[1].toDouble()
				val seconds = coord[2].toDouble()
				ref = ref.substring(0, 1)
				
				String.format(
					Locale.ENGLISH,
					"%1$.0f° %2$.0f' %3$.0f\" %4\$s",
					degrees,
					minutes,
					seconds,
					ref.toUpperCase(Locale.getDefault())
				)
			} catch(ex : Throwable) {
				ex.printStackTrace()
				null
			}
		}
		
		/**
		 * Given an exif date time, like [.TAG_DATE_TIME] or [.TAG_DATE_TIME_DIGITIZED]
		 * returns a java Date object
		 *
		 * @param dateTimeString one of the value of [.TAG_DATE_TIME] or [.TAG_DATE_TIME_DIGITIZED]
		 * @param timeZone       the target timezone
		 * @return the parsed date
		 */
		fun getDateTime(dateTimeString : String, timeZone : TimeZone) : Date? {
			
			return try {
				val formatter = SimpleDateFormat(DATETIME_FORMAT_STR, Locale.ENGLISH)
				formatter.timeZone = timeZone
				formatter.parse(dateTimeString)
			} catch(e : Throwable) {
				e.printStackTrace()
				null
			}
		}
		
		fun isIfdAllowed(info : Int, ifd : Int) : Boolean {
			val ifdFlags = getAllowedIfdFlagsFromInfo(info)
			IfdData.list.forEachIndexed { itemIndex, itemId ->
				if(ifd == itemId && ifdFlags shr itemIndex and 1 == 1) return true
			}
			return false
		}
		
		private fun getAllowedIfdFlagsFromInfo(info : Int) : Int = info.ushr(24)
		
		private fun toExifLatLong(valueArg : Double) : Array<Rational> {
			// convert to the format dd/1 mm/1 ssss/100
			var value = abs(valueArg)
			val degrees = value
			value = (value - degrees) * 60
			val minutes = value
			value = (value - minutes) * 6000
			val seconds = value
			return arrayOf(
				Rational(degrees.toLong(), 1),
				Rational(minutes.toLong(), 1),
				Rational(seconds.toLong(), 100)
			)
		}
		
		fun toBitArray(value : Short) : ByteArray {
			val result = ByteArray(16)
			for(i in 0 .. 15) {
				result[15 - i] = (value shr i and 1).toByte()
			}
			return result
		}
		
		infix fun Short.shl(bits : Int) : Int = (this.toInt() and 0xffff) shl bits
		infix fun Short.shr(bits : Int) : Int = (this.toInt() and 0xffff) shr bits
		infix fun Short.or(bits : Int) : Int = (this.toInt() and 0xffff) or bits
		
		private fun SparseIntArray.initTagInfo() : SparseIntArray {
			
			var f : Int
			
			/*
			 * We put tag information in a 4-bytes integer. The first byte a bitmask
			 * representing the allowed IFDs of the tag, the second byte is the data
			 * type, and the last two byte are a short value indicating the default
			 * component count of this tag.
			 */
			
			// IFD0 tags
			f = getFlagsFromAllowedIfds(intArrayOf(IfdData.TYPE_IFD_0, IfdData.TYPE_IFD_1)) shl 24
			
			put(TAG_MAKE, f or (ExifTag.TYPE_ASCII shl 16))
			put(TAG_IMAGE_WIDTH, f or (ExifTag.TYPE_UNSIGNED_LONG shl 16) or 1)
			put(TAG_IMAGE_LENGTH, f or (ExifTag.TYPE_UNSIGNED_LONG shl 16) or 1)
			put(TAG_BITS_PER_SAMPLE, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 3)
			put(TAG_COMPRESSION, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 1)
			put(TAG_PHOTOMETRIC_INTERPRETATION, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 1)
			put(TAG_ORIENTATION, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 1)
			put(TAG_SAMPLES_PER_PIXEL, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 1)
			put(TAG_PLANAR_CONFIGURATION, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 1)
			put(TAG_Y_CB_CR_SUB_SAMPLING, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 2)
			put(TAG_Y_CB_CR_POSITIONING, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 1)
			put(TAG_X_RESOLUTION, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 1)
			put(TAG_Y_RESOLUTION, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 1)
			put(TAG_RESOLUTION_UNIT, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 1)
			put(TAG_STRIP_OFFSETS, f or (ExifTag.TYPE_UNSIGNED_LONG shl 16))
			put(TAG_ROWS_PER_STRIP, f or (ExifTag.TYPE_UNSIGNED_LONG shl 16) or 1)
			put(TAG_STRIP_BYTE_COUNTS, f or (ExifTag.TYPE_UNSIGNED_LONG shl 16))
			put(TAG_TRANSFER_FUNCTION, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 3 * 256)
			put(TAG_WHITE_POINT, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 2)
			put(TAG_PRIMARY_CHROMATICITIES, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 6)
			put(TAG_Y_CB_CR_COEFFICIENTS, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 3)
			put(TAG_REFERENCE_BLACK_WHITE, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 6)
			put(TAG_DATE_TIME, f or (ExifTag.TYPE_ASCII shl 16) or 20)
			put(TAG_IMAGE_DESCRIPTION, f or (ExifTag.TYPE_ASCII shl 16))
			put(TAG_MODEL, f or (ExifTag.TYPE_ASCII shl 16))
			put(TAG_SOFTWARE, f or (ExifTag.TYPE_ASCII shl 16))
			put(TAG_ARTIST, f or (ExifTag.TYPE_ASCII shl 16))
			put(TAG_COPYRIGHT, f or (ExifTag.TYPE_ASCII shl 16))
			put(TAG_EXIF_IFD, f or (ExifTag.TYPE_UNSIGNED_LONG shl 16) or 1)
			put(TAG_GPS_IFD, f or (ExifTag.TYPE_UNSIGNED_LONG shl 16) or 1)
			
			// IFD1 tags
			f = getFlagsFromAllowedIfds(intArrayOf(IfdData.TYPE_IFD_1)) shl 24
			put(TAG_JPEG_INTERCHANGE_FORMAT, f or (ExifTag.TYPE_UNSIGNED_LONG shl 16) or 1)
			put(TAG_JPEG_INTERCHANGE_FORMAT_LENGTH, f or (ExifTag.TYPE_UNSIGNED_LONG shl 16) or 1)
			
			// Exif tags
			f = getFlagsFromAllowedIfds(intArrayOf(IfdData.TYPE_IFD_EXIF)) shl 24
			put(TAG_EXIF_VERSION, f or (ExifTag.TYPE_UNDEFINED shl 16) or 4)
			put(TAG_FLASHPIX_VERSION, f or (ExifTag.TYPE_UNDEFINED shl 16) or 4)
			put(TAG_COLOR_SPACE, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 1)
			put(TAG_COMPONENTS_CONFIGURATION, f or (ExifTag.TYPE_UNDEFINED shl 16) or 4)
			put(TAG_COMPRESSED_BITS_PER_PIXEL, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 1)
			put(TAG_PIXEL_X_DIMENSION, f or (ExifTag.TYPE_UNSIGNED_LONG shl 16) or 1)
			put(TAG_PIXEL_Y_DIMENSION, f or (ExifTag.TYPE_UNSIGNED_LONG shl 16) or 1)
			put(TAG_MAKER_NOTE, f or (ExifTag.TYPE_UNDEFINED shl 16))
			put(TAG_USER_COMMENT, f or (ExifTag.TYPE_UNDEFINED shl 16))
			put(TAG_RELATED_SOUND_FILE, f or (ExifTag.TYPE_ASCII shl 16) or 13)
			put(TAG_DATE_TIME_ORIGINAL, f or (ExifTag.TYPE_ASCII shl 16) or 20)
			put(TAG_DATE_TIME_DIGITIZED, f or (ExifTag.TYPE_ASCII shl 16) or 20)
			put(TAG_SUB_SEC_TIME, f or (ExifTag.TYPE_ASCII shl 16))
			put(TAG_SUB_SEC_TIME_ORIGINAL, f or (ExifTag.TYPE_ASCII shl 16))
			put(TAG_SUB_SEC_TIME_DIGITIZED, f or (ExifTag.TYPE_ASCII shl 16))
			put(TAG_IMAGE_UNIQUE_ID, f or (ExifTag.TYPE_ASCII shl 16) or 33)
			put(TAG_LENS_SPECS, f or (ExifTag.TYPE_RATIONAL shl 16) or 4)
			put(TAG_LENS_MAKE, f or (ExifTag.TYPE_ASCII shl 16))
			put(TAG_LENS_MODEL, f or (ExifTag.TYPE_ASCII shl 16))
			put(TAG_SENSITIVITY_TYPE, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 1)
			put(TAG_EXPOSURE_TIME, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 1)
			put(TAG_F_NUMBER, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 1)
			put(TAG_EXPOSURE_PROGRAM, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 1)
			put(TAG_SPECTRAL_SENSITIVITY, f or (ExifTag.TYPE_ASCII shl 16))
			put(TAG_ISO_SPEED_RATINGS, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16))
			put(TAG_OECF, f or (ExifTag.TYPE_UNDEFINED shl 16))
			put(TAG_SHUTTER_SPEED_VALUE, f or (ExifTag.TYPE_RATIONAL shl 16) or 1)
			put(TAG_APERTURE_VALUE, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 1)
			put(TAG_BRIGHTNESS_VALUE, f or (ExifTag.TYPE_RATIONAL shl 16) or 1)
			put(TAG_EXPOSURE_BIAS_VALUE, f or (ExifTag.TYPE_RATIONAL shl 16) or 1)
			put(TAG_MAX_APERTURE_VALUE, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 1)
			put(TAG_SUBJECT_DISTANCE, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 1)
			put(TAG_METERING_MODE, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 1)
			put(TAG_LIGHT_SOURCE, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 1)
			put(TAG_FLASH, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 1)
			put(TAG_FOCAL_LENGTH, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 1)
			put(TAG_SUBJECT_AREA, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16))
			put(TAG_FLASH_ENERGY, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 1)
			put(TAG_SPATIAL_FREQUENCY_RESPONSE, f or (ExifTag.TYPE_UNDEFINED shl 16))
			put(TAG_FOCAL_PLANE_X_RESOLUTION, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 1)
			put(TAG_FOCAL_PLANE_Y_RESOLUTION, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 1)
			put(TAG_FOCAL_PLANE_RESOLUTION_UNIT, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 1)
			put(TAG_SUBJECT_LOCATION, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 2)
			put(TAG_EXPOSURE_INDEX, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 1)
			put(TAG_SENSING_METHOD, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 1)
			put(TAG_FILE_SOURCE, f or (ExifTag.TYPE_UNDEFINED shl 16) or 1)
			put(TAG_SCENE_TYPE, f or (ExifTag.TYPE_UNDEFINED shl 16) or 1)
			put(TAG_CFA_PATTERN, f or (ExifTag.TYPE_UNDEFINED shl 16))
			put(TAG_CUSTOM_RENDERED, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 1)
			put(TAG_EXPOSURE_MODE, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 1)
			put(TAG_WHITE_BALANCE, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 1)
			put(TAG_DIGITAL_ZOOM_RATIO, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 1)
			put(TAG_FOCAL_LENGTH_IN_35_MM_FILE, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 1)
			put(TAG_SCENE_CAPTURE_TYPE, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 1)
			put(TAG_GAIN_CONTROL, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 1)
			put(TAG_CONTRAST, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 1)
			put(TAG_SATURATION, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 1)
			put(TAG_SHARPNESS, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 1)
			put(TAG_DEVICE_SETTING_DESCRIPTION, f or (ExifTag.TYPE_UNDEFINED shl 16))
			put(TAG_SUBJECT_DISTANCE_RANGE, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 1)
			put(TAG_INTEROPERABILITY_IFD, f or (ExifTag.TYPE_UNSIGNED_LONG shl 16) or 1)
			
			// GPS tag
			f = getFlagsFromAllowedIfds(intArrayOf(IfdData.TYPE_IFD_GPS)) shl 24
			put(TAG_GPS_VERSION_ID, f or (ExifTag.TYPE_UNSIGNED_BYTE shl 16) or 4)
			put(TAG_GPS_LATITUDE_REF, f or (ExifTag.TYPE_ASCII shl 16) or 2)
			put(TAG_GPS_LONGITUDE_REF, f or (ExifTag.TYPE_ASCII shl 16) or 2)
			put(TAG_GPS_LATITUDE, f or (ExifTag.TYPE_RATIONAL shl 16) or 3)
			put(TAG_GPS_LONGITUDE, f or (ExifTag.TYPE_RATIONAL shl 16) or 3)
			put(TAG_GPS_ALTITUDE_REF, f or (ExifTag.TYPE_UNSIGNED_BYTE shl 16) or 1)
			put(TAG_GPS_ALTITUDE, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 1)
			put(TAG_GPS_TIME_STAMP, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 3)
			put(TAG_GPS_SATTELLITES, f or (ExifTag.TYPE_ASCII shl 16))
			put(TAG_GPS_STATUS, f or (ExifTag.TYPE_ASCII shl 16) or 2)
			put(TAG_GPS_MEASURE_MODE, f or (ExifTag.TYPE_ASCII shl 16) or 2)
			put(TAG_GPS_DOP, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 1)
			put(TAG_GPS_SPEED_REF, f or (ExifTag.TYPE_ASCII shl 16) or 2)
			put(TAG_GPS_SPEED, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 1)
			put(TAG_GPS_TRACK_REF, f or (ExifTag.TYPE_ASCII shl 16) or 2)
			put(TAG_GPS_TRACK, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 1)
			put(TAG_GPS_IMG_DIRECTION_REF, f or (ExifTag.TYPE_ASCII shl 16) or 2)
			put(TAG_GPS_IMG_DIRECTION, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 1)
			put(TAG_GPS_MAP_DATUM, f or (ExifTag.TYPE_ASCII shl 16))
			put(TAG_GPS_DEST_LATITUDE_REF, f or (ExifTag.TYPE_ASCII shl 16) or 2)
			put(TAG_GPS_DEST_LATITUDE, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 1)
			put(TAG_GPS_DEST_BEARING_REF, f or (ExifTag.TYPE_ASCII shl 16) or 2)
			put(TAG_GPS_DEST_BEARING, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 1)
			put(TAG_GPS_DEST_DISTANCE_REF, f or (ExifTag.TYPE_ASCII shl 16) or 2)
			put(TAG_GPS_DEST_DISTANCE, f or (ExifTag.TYPE_UNSIGNED_RATIONAL shl 16) or 1)
			put(TAG_GPS_PROCESSING_METHOD, f or (ExifTag.TYPE_UNDEFINED shl 16))
			put(TAG_GPS_AREA_INFORMATION, f or (ExifTag.TYPE_UNDEFINED shl 16))
			put(TAG_GPS_DATE_STAMP, f or (ExifTag.TYPE_ASCII shl 16) or 11)
			put(TAG_GPS_DIFFERENTIAL, f or (ExifTag.TYPE_UNSIGNED_SHORT shl 16) or 11)
			
			// Interoperability tag
			f = getFlagsFromAllowedIfds(intArrayOf(IfdData.TYPE_IFD_INTEROPERABILITY)) shl 24
			put(TAG_INTEROPERABILITY_INDEX, f or (ExifTag.TYPE_ASCII shl 16))
			put(TAG_INTEROP_VERSION, f or (ExifTag.TYPE_UNDEFINED shl 16) or 4)
			
			return this
		}
		
	}
}
