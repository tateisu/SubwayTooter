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

import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*

/**
 * This class stores information of an EXIF tag. For more information about
 * defined EXIF tags, please read the Jeita EXIF 2.2 standard. Tags should be
 * instantiated using [ExifInterface.buildTag].
 *
 * @see ExifInterface
 */
// Use builtTag in ExifInterface instead of constructor.
@Suppress("unused")
open class ExifTag internal constructor(
	
	// Exif TagId. the TID of this tag.
	val tagId : Short,
	
	// Exif Tag Type. the data type of this tag
	
	/*
	 * @see .TYPE_ASCII
	 * @see .TYPE_LONG
	 * @see .TYPE_RATIONAL
	 * @see .TYPE_UNDEFINED
	 * @see .TYPE_UNSIGNED_BYTE
	 * @see .TYPE_UNSIGNED_LONG
	 * @see .TYPE_UNSIGNED_RATIONAL
	 * @see .TYPE_UNSIGNED_SHORT
	 */
	val dataType : Short,
	
	componentCount : Int,
	
	// The ifd that this tag should be put in. the ID of the IFD this tag belongs to.
	/*
	 * @see IfdId.TYPE_IFD_0
	 * @see IfdId.TYPE_IFD_1
	 * @see IfdId.TYPE_IFD_EXIF
	 * @see IfdId.TYPE_IFD_GPS
	 * @see IfdId.TYPE_IFD_INTEROPERABILITY
	 */
	var ifd : Int,
	
	// If tag has defined count
	private var mHasDefinedDefaultComponentCount : Boolean
) {
	// Actual data count in tag (should be number of elements in value array)
	/**
	 * Gets the component count of this tag.
	 */
	
	// TODO: fix integer overflows with this
	var componentCount : Int = 0
		private set
	
	// The value (array of elements of type Tag Type)
	private var mValue : Any? = null
	
	// Value offset in exif header.  the offset of this tag.
	// This is only valid if this data size > 4 and contains an offset to the location of the actual value.
	var offset : Int = 0
	
	// the total data size in bytes of the value of this tag.
	val dataSize : Int
		get() = componentCount * getElementSize(dataType)
	

	/**
	 * Gets the value as a byte array. This method should be used for tags of
	 * type [.TYPE_UNDEFINED] or [.TYPE_UNSIGNED_BYTE].
	 *
	 * @return the value as a byte array, or null if the tag's value does not
	 * exist or cannot be converted to a byte array.
	 */
	val valueAsBytes : ByteArray?
		get() = mValue as? ByteArray
	
	/**
	 * Gets the value as an array of longs. This method should be used for tags
	 * of type [.TYPE_UNSIGNED_LONG].
	 *
	 * @return the value as as an array of longs, or null if the tag's value
	 * does not exist or cannot be converted to an array of longs.
	 */
	val valueAsLongs : LongArray?
		get() = mValue as? LongArray
	
	/**
	 * Gets the value as an array of Rationals. This method should be used for
	 * tags of type [.TYPE_RATIONAL] or [.TYPE_UNSIGNED_RATIONAL].
	 *
	 * @return the value as as an array of Rationals, or null if the tag's value
	 * does not exist or cannot be converted to an array of Rationals.
	 */
	@Suppress("UNCHECKED_CAST")
	val valueAsRationals : Array<Rational>?
		get() = mValue as? Array<Rational>
	
	/**
	 * Gets the value as an array of ints. This method should be used for tags
	 * of type [.TYPE_UNSIGNED_SHORT], [.TYPE_UNSIGNED_LONG].
	 *
	 * @return the value as as an array of ints, or null if the tag's value does
	 * not exist or cannot be converted to an array of ints.
	 */
	// Truncates
	val valueAsInts : IntArray?
		get() = when(val v = mValue){
			is LongArray-> IntArray(v.size){ v[it].toInt()}
			else ->null
		}
	
	/**
	 * Gets the value as a String. This method should be used for tags of type
	 * [.TYPE_ASCII].
	 *
	 * @return the value as a String, or null if the tag's value does not exist
	 * or cannot be converted to a String.
	 */
	val valueAsString : String?
		get() = when(val v = mValue) {
			is String -> v
			is ByteArray -> String(v, US_ASCII)
			else -> null
		}
	
	
	/**
	 * Gets the [.TYPE_ASCII] data.
	 *
	 * @throws IllegalArgumentException If the type is NOT
	 * [.TYPE_ASCII].
	 */
	protected val string : String
		get() = valueAsString !!
	
	/*
	 * Get the converted ascii byte. Used by ExifOutputStream.
	 */
	val stringByte : ByteArray?
		get() = mValue as? ByteArray
	
	init {
		this.componentCount = componentCount
		mValue = null
	}
	
	/**
	 * Sets the component count of this tag. Call this function before
	 * setValue() if the length of value does not match the component count.
	 */
	fun forceSetComponentCount(count : Int) {
		componentCount = count
	}
	
	/**
	 * Returns true if this ExifTag contains value; otherwise, this tag will
	 * contain an offset value that is determined when the tag is written.
	 */
	fun hasValue() : Boolean {
		return mValue != null
	}
	
	/**
	 * Sets integer values into this tag. This method should be used for tags of
	 * type [.TYPE_UNSIGNED_SHORT]. This method will fail if:
	 *
	 *  * The component type of this tag is not [.TYPE_UNSIGNED_SHORT],
	 * [.TYPE_UNSIGNED_LONG], or [.TYPE_LONG].
	 *  * The value overflows.
	 *  * The value.length does NOT match the component count in the definition
	 * for this tag.
	 *
	 */
	fun setValue(value : IntArray) : Boolean {
		if(checkBadComponentCount(value.size)) {
			return false
		}
		if(dataType != TYPE_UNSIGNED_SHORT && dataType != TYPE_LONG &&
			dataType != TYPE_UNSIGNED_LONG) {
			return false
		}
		if(dataType == TYPE_UNSIGNED_SHORT && checkOverflowForUnsignedShort(value)) {
			return false
		} else if(dataType == TYPE_UNSIGNED_LONG && checkOverflowForUnsignedLong(value)) {
			return false
		}
		
		val data = LongArray(value.size)
		for(i in value.indices) {
			data[i] = value[i].toLong()
		}
		mValue = data
		componentCount = value.size
		return true
	}
	
	/**
	 * Sets integer value into this tag. This method should be used for tags of
	 * type [.TYPE_UNSIGNED_SHORT], or [.TYPE_LONG]. This method
	 * will fail if:
	 *
	 *  * The component type of this tag is not [.TYPE_UNSIGNED_SHORT],
	 * [.TYPE_UNSIGNED_LONG], or [.TYPE_LONG].
	 *  * The value overflows.
	 *  * The component count in the definition of this tag is not 1.
	 *
	 */
	fun setValue(value : Int) : Boolean {
		return setValue(intArrayOf(value))
	}
	
	/**
	 * Sets long values into this tag. This method should be used for tags of
	 * type [.TYPE_UNSIGNED_LONG]. This method will fail if:
	 *
	 *  * The component type of this tag is not [.TYPE_UNSIGNED_LONG].
	 *  * The value overflows.
	 *  * The value.length does NOT match the component count in the definition
	 * for this tag.
	 *
	 */
	fun setValue(value : LongArray) : Boolean {
		if(checkBadComponentCount(value.size) || dataType != TYPE_UNSIGNED_LONG) {
			return false
		}
		if(checkOverflowForUnsignedLong(value)) {
			return false
		}
		mValue = value
		componentCount = value.size
		return true
	}
	
	/**
	 * Sets long values into this tag. This method should be used for tags of
	 * type [.TYPE_UNSIGNED_LONG]. This method will fail if:
	 *
	 *  * The component type of this tag is not [.TYPE_UNSIGNED_LONG].
	 *  * The value overflows.
	 *  * The component count in the definition for this tag is not 1.
	 *
	 */
	fun setValue(value : Long) : Boolean {
		return setValue(longArrayOf(value))
	}
	
	/**
	 * Sets Rational values into this tag. This method should be used for tags
	 * of type [.TYPE_UNSIGNED_RATIONAL], or [.TYPE_RATIONAL]. This
	 * method will fail if:
	 *
	 *  * The component type of this tag is not [.TYPE_UNSIGNED_RATIONAL]
	 * or [.TYPE_RATIONAL].
	 *  * The value overflows.
	 *  * The value.length does NOT match the component count in the definition
	 * for this tag.
	 *
	 *
	 * @see Rational
	 */
	fun setValue(value : Array<Rational>) : Boolean {
		if(checkBadComponentCount(value.size)) {
			return false
		}
		if(dataType != TYPE_UNSIGNED_RATIONAL && dataType != TYPE_RATIONAL) {
			return false
		}
		if(dataType == TYPE_UNSIGNED_RATIONAL && checkOverflowForUnsignedRational(value)) {
			return false
		} else if(dataType == TYPE_RATIONAL && checkOverflowForRational(value)) {
			return false
		}
		
		mValue = value
		componentCount = value.size
		return true
	}
	
	/**
	 * Sets a Rational value into this tag. This method should be used for tags
	 * of type [.TYPE_UNSIGNED_RATIONAL], or [.TYPE_RATIONAL]. This
	 * method will fail if:
	 *
	 *  * The component type of this tag is not [.TYPE_UNSIGNED_RATIONAL]
	 * or [.TYPE_RATIONAL].
	 *  * The value overflows.
	 *  * The component count in the definition for this tag is not 1.
	 *
	 *
	 * @see Rational
	 */
	fun setValue(value : Rational) : Boolean =
		setValue(arrayOf(value))
	
	/**
	 * Sets byte values into this tag. This method should be used for tags of
	 * type [.TYPE_UNSIGNED_BYTE] or [.TYPE_UNDEFINED]. This method
	 * will fail if:
	 *
	 *  * The component type of this tag is not [.TYPE_UNSIGNED_BYTE] or
	 * [.TYPE_UNDEFINED] .
	 *  * The length does NOT match the component count in the definition for
	 * this tag.
	 *
	 */
	@JvmOverloads
	fun setValue(value : ByteArray, offset : Int = 0, length : Int = value.size) : Boolean {
		if(checkBadComponentCount(length)) {
			return false
		}
		if(dataType != TYPE_UNSIGNED_BYTE && dataType != TYPE_UNDEFINED) {
			return false
		}
		componentCount = length
		mValue = ByteArray(length).also{
			System.arraycopy(value, offset, it, 0, length)
		}
		return true
	}
	
	/**
	 * Sets byte value into this tag. This method should be used for tags of
	 * type [.TYPE_UNSIGNED_BYTE] or [.TYPE_UNDEFINED]. This method
	 * will fail if:
	 *
	 *  * The component type of this tag is not [.TYPE_UNSIGNED_BYTE] or
	 * [.TYPE_UNDEFINED] .
	 *  * The component count in the definition for this tag is not 1.
	 *
	 */
	fun setValue(value : Byte) : Boolean =
		setValue(byteArrayOf(value))
	
	/**
	 * Sets the value for this tag using an appropriate setValue method for the
	 * given object. This method will fail if:
	 *
	 *  * The corresponding setValue method for the class of the object passed
	 * in would fail.
	 *  * There is no obvious way to cast the object passed in into an EXIF tag
	 * type.
	 *
	 */
	inline fun <reified T : Any?> setValueAny(obj : T) : Boolean {
		when(obj) {
			null -> return false
			
			is String -> return setValue(obj)
			is ByteArray -> return setValue(obj)
			is IntArray -> return setValue(obj)
			is LongArray -> return setValue(obj)
			is Rational -> return setValue(obj)
			is Byte -> return setValue(obj.toByte())
			is Short -> return setValue(obj.toInt() and 0x0ffff)
			is Int -> return setValue(obj.toInt())
			is Long -> return setValue(obj.toLong())
			
			else ->{

				@Suppress("UNCHECKED_CAST")
				val ra = obj as? Array<Rational>
				if(ra != null) return setValue( ra )
				
				// Nulls in this array are treated as zeroes.
				@Suppress("UNCHECKED_CAST")
				val sa = obj as? Array<Short?>
				if( sa != null) return setValue(IntArray(sa.size){ (sa[it]?.toInt() ?: 0) and 0xffff})
				
				// Nulls in this array are treated as zeroes.
				@Suppress("UNCHECKED_CAST")
				val ia = obj as? Array<Int?>
				if( ia != null) return setValue(IntArray(ia.size){ ia[it] ?: 0 })
				
				// Nulls in this array are treated as zeroes.
				@Suppress("UNCHECKED_CAST")
				val la = obj as? Array<Long?>
				if( la != null) return setValue(LongArray(la.size){ la[it] ?: 0L })
				
				// Nulls in this array are treated as zeroes.
				@Suppress("UNCHECKED_CAST")
				val ba = obj as? Array<Byte?>
				if( ba != null) return setValue(ByteArray(ba.size){ ba[it] ?: 0 })
				
				return false
			}
		}
	}
	
	/**
	 * Sets a timestamp to this tag. The method converts the timestamp with the
	 * format of "yyyy:MM:dd kk:mm:ss" and calls [.setValue]. This
	 * method will fail if the data type is not [.TYPE_ASCII] or the
	 * component count of this tag is not 20 or undefined.
	 *
	 * @param time the number of milliseconds since Jan. 1, 1970 GMT
	 * @return true on success
	 */
	fun setValueTime(time : Long) : Boolean {
		// synchronized on TIME_FORMAT as SimpleDateFormat is not thread safe
		synchronized(TIME_FORMAT) {
			return setValue(TIME_FORMAT.format(Date(time)))
		}
	}
	
	/**
	 * Sets a string value into this tag. This method should be used for tags of
	 * type [.TYPE_ASCII]. The string is converted to an ASCII string.
	 * Characters that cannot be converted are replaced with '?'. The length of
	 * the string must be equal to either (component count -1) or (component
	 * count). The final byte will be set to the string null terminator '\0',
	 * overwriting the last character in the string if the value.length is equal
	 * to the component count. This method will fail if:
	 *
	 *  * The data type is not [.TYPE_ASCII] or [.TYPE_UNDEFINED].
	 *  * The length of the string is not equal to (component count -1) or
	 * (component count) in the definition for this tag.
	 *
	 */
	fun setValue(value : String) : Boolean {
		if(dataType != TYPE_ASCII && dataType != TYPE_UNDEFINED) {
			return false
		}
		
		val buf = value.toByteArray(US_ASCII)
		
		val finalBuf = when {
			buf.isNotEmpty() -> when {
				buf[buf.size - 1].toInt() == 0 || dataType == TYPE_UNDEFINED -> buf
				else -> buf.copyOf(buf.size + 1)
			}
			dataType == TYPE_ASCII && componentCount == 1 -> byteArrayOf(0)
			else -> buf
		}
		val count = finalBuf.size
		if(checkBadComponentCount(count)) {
			return false
		}
		componentCount = count
		mValue = finalBuf
		return true
	}
	
	private fun checkBadComponentCount(count : Int) : Boolean {
		return mHasDefinedDefaultComponentCount && componentCount != count
	}
	
	/**
	 * Gets the value as a String. This method should be used for tags of type
	 * [.TYPE_ASCII].
	 *
	 * @param defaultValue the String to return if the tag's value does not
	 * exist or cannot be converted to a String.
	 * @return the tag's value as a String, or the defaultValue.
	 */
	fun getValueAsString(defaultValue : String) : String {
		return valueAsString ?: defaultValue
	}
	
	/**
	 * Gets the value as a byte. If there are more than 1 bytes in this value,
	 * gets the first byte. This method should be used for tags of type
	 * [.TYPE_UNDEFINED] or [.TYPE_UNSIGNED_BYTE].
	 *
	 * @param defaultValue the byte to return if tag's value does not exist or
	 * cannot be converted to a byte.
	 * @return the tag's value as a byte, or the defaultValue.
	 */
	fun getValueAsByte(defaultValue : Byte) : Byte {
		val array = valueAsBytes
		return if(array?.isNotEmpty()==true) array[0] else defaultValue
	}
	
	/**
	 * Gets the value as a Rational. If there are more than 1 Rationals in this
	 * value, gets the first one. This method should be used for tags of type
	 * [.TYPE_RATIONAL] or [.TYPE_UNSIGNED_RATIONAL].
	 *
	 * @param defaultValue the numerator of the Rational to return if tag's
	 * value does not exist or cannot be converted to a Rational (the
	 * denominator will be 1).
	 * @return the tag's value as a Rational, or the defaultValue.
	 */
	fun getValueAsRational(defaultValue : Long) : Rational =
		getValueAsRational( Rational(defaultValue, 1))
	
	/**
	 * Gets the value as a Rational. If there are more than 1 Rationals in this
	 * value, gets the first one. This method should be used for tags of type
	 * [.TYPE_RATIONAL] or [.TYPE_UNSIGNED_RATIONAL].
	 *
	 * @param defaultValue the Rational to return if tag's value does not exist
	 * or cannot be converted to a Rational.
	 * @return the tag's value as a Rational, or the defaultValue.
	 */
	private fun getValueAsRational(defaultValue : Rational) : Rational {
		val array = valueAsRationals
		return if(array?.isNotEmpty()==true) array[0] else defaultValue
	}
	
	/**
	 * Gets the value as an int. If there are more than 1 ints in this value,
	 * gets the first one. This method should be used for tags of type
	 * [.TYPE_UNSIGNED_SHORT], [.TYPE_UNSIGNED_LONG].
	 *
	 * @param defaultValue the int to return if tag's value does not exist or
	 * cannot be converted to an int.
	 * @return the tag's value as a int, or the defaultValue.
	 */
	fun getValueAsInt(defaultValue : Int) : Int {
		val array = valueAsInts
		return if(array?.isNotEmpty()==true) array[0] else defaultValue
	}
	
	/**
	 * Gets the value or null if none exists. If there are more than 1 longs in
	 * this value, gets the first one. This method should be used for tags of
	 * type [.TYPE_UNSIGNED_LONG].
	 *
	 * @param defaultValue the long to return if tag's value does not exist or
	 * cannot be converted to a long.
	 * @return the tag's value as a long, or the defaultValue.
	 */
	fun getValueAsLong(defaultValue : Long) : Long {
		val array = valueAsLongs
		return if(array?.isNotEmpty()==true) array[0] else defaultValue
	}
	
	/**
	 * Gets the tag's value or null if none exists.
	 */
	fun getValue() : Any? {
		return mValue
	}
	
	/**
	 * Gets a long representation of the value.
	 *
	 * @param defaultValue value to return if there is no value or value is a
	 * rational with a denominator of 0.
	 * @return the tag's value as a long, or defaultValue if no representation
	 * exists.
	 */
	fun forceGetValueAsLong(defaultValue : Long) : Long {
		when(val v = mValue) {
			is LongArray -> if(v.isNotEmpty()) return v[0]
			is ByteArray -> if(v.isNotEmpty()) return v[0].toLong()
			
			else -> {
				val r = valueAsRationals
				if(r?.isNotEmpty() == true && r[0].denominator != 0L) {
					return r[0].toDouble().toLong()
				}
			}
		}
		return defaultValue
	}
	
	/**
	 * Gets the value for type [.TYPE_ASCII], [.TYPE_LONG],
	 * [.TYPE_UNDEFINED], [.TYPE_UNSIGNED_BYTE],
	 * [.TYPE_UNSIGNED_LONG], or [.TYPE_UNSIGNED_SHORT]. For
	 * [.TYPE_RATIONAL] or [.TYPE_UNSIGNED_RATIONAL], call
	 * [.getRational] instead.
	 *
	 * @throws IllegalArgumentException if the data type is
	 * [.TYPE_RATIONAL] or [.TYPE_UNSIGNED_RATIONAL].
	 */
	fun getValueAt(index : Int) : Long {
		return when(val v = mValue) {
			is LongArray -> v[index]
			is ByteArray -> v[index].toLong()
			else -> error(
				"Cannot get integer value from ${convertTypeToString(dataType)}"
			)
		}
	}
	
	/**
	 * Gets the [.TYPE_RATIONAL] or [.TYPE_UNSIGNED_RATIONAL] data.
	 *
	 * @throws IllegalArgumentException If the type is NOT
	 * [.TYPE_RATIONAL] or [.TYPE_UNSIGNED_RATIONAL].
	 */
	fun getRational(index : Int) : Rational? {
		require(! (dataType != TYPE_RATIONAL && dataType != TYPE_UNSIGNED_RATIONAL)) {
			"Cannot get RATIONAL value from " + convertTypeToString(dataType)
		}
		return valueAsRationals?.get(index)
	}
	
	/**
	 * Gets the [.TYPE_UNDEFINED] or [.TYPE_UNSIGNED_BYTE] data.
	 *
	 * @param buf    the byte array in which to store the bytes read.
	 * @param offset the initial position in buffer to store the bytes.
	 * @param length the maximum number of bytes to store in buffer. If length >
	 * component count, only the valid bytes will be stored.
	 * @throws IllegalArgumentException If the type is NOT
	 * [.TYPE_UNDEFINED] or [.TYPE_UNSIGNED_BYTE].
	 */
	@JvmOverloads
	fun getBytes(buf : ByteArray, offset : Int = 0, length : Int = buf.size) {
		require(! (dataType != TYPE_UNDEFINED && dataType != TYPE_UNSIGNED_BYTE)) {
			"Cannot get BYTE value from " + convertTypeToString(
				dataType
			)
		}
		System.arraycopy(
			mValue !!,
			0,
			buf,
			offset,
			if(length > componentCount) componentCount else length
		)
	}
	
	var hasDefinedCount : Boolean
		get() = mHasDefinedDefaultComponentCount
		set(value){
			mHasDefinedDefaultComponentCount = value
		}
	
	private fun checkOverflowForUnsignedShort(value : IntArray) : Boolean {
		for(v in value) {
			if(v > UNSIGNED_SHORT_MAX || v < 0) {
				return true
			}
		}
		return false
	}
	
	private fun checkOverflowForUnsignedLong(value : LongArray) : Boolean {
		for(v in value) {
			if(v < 0 || v > UNSIGNED_LONG_MAX) {
				return true
			}
		}
		return false
	}
	
	private fun checkOverflowForUnsignedLong(value : IntArray) : Boolean {
		for(v in value) {
			if(v < 0) {
				return true
			}
		}
		return false
	}
	
	private fun checkOverflowForUnsignedRational(value : Array<Rational>) : Boolean {
		for(v in value) {
			if(v.numerator < 0 || v.denominator < 0 || v.numerator > UNSIGNED_LONG_MAX || v.denominator > UNSIGNED_LONG_MAX) {
				return true
			}
		}
		return false
	}
	
	private fun checkOverflowForRational(value : Array<Rational>) : Boolean {
		for(v in value) {
			if(v.numerator < LONG_MIN || v.denominator < LONG_MIN || v.numerator > LONG_MAX || v.denominator > LONG_MAX) {
				return true
			}
		}
		return false
	}
	
	override fun hashCode() : Int {
		var result = tagId.toInt()
		result = 31 * result + dataType.toInt()
		result = 31 * result + ifd
		result = 31 * result + componentCount
		result = 31 * result + offset
		result = 31 * result + mHasDefinedDefaultComponentCount.hashCode()
		result = 31 * result + (mValue?.hashCode() ?: 0)
		return result
	}
	
	override fun equals(other : Any?) : Boolean {
		if(other !is ExifTag) return false
		
		if(other.tagId != this.tagId
			|| other.componentCount != this.componentCount
			|| other.dataType != this.dataType
		) {
			return false
		}
		
		val va = this.mValue
		val vb = other.mValue
		
		return when {
			
			va == null -> vb == null
			
			vb == null -> false
			
			va is LongArray -> when(vb) {
				is LongArray -> Arrays.equals(va, vb)
				else -> false
			}
			
			va is ByteArray -> when(vb) {
				is ByteArray -> Arrays.equals(va, vb)
				else -> false
			}
			
			va is Array<*> && va.isArrayOf<Rational>() -> when {
				vb is Array<*> && vb.isArrayOf<Rational>() -> Arrays.equals(va, vb)
				else -> false
			}
			
			else -> va == vb
		}
	}
	
	override fun toString() : String {
		val strTagId = String.format("%04X", tagId)
		return "tag id: $strTagId\nifd id: $ifd\ntype: ${convertTypeToString(dataType)}\ncount: $componentCount\noffset: $offset\nvalue: ${forceGetValueAsString()}\n"
	}
	
	/**
	 * Gets a string representation of the value.
	 */
	private fun forceGetValueAsString() : String {
		when(val v = mValue) {
			
			null -> return ""
			
			is ByteArray -> return when(dataType) {
				TYPE_ASCII -> String(v, US_ASCII)
				else -> Arrays.toString(v)
			}
			
			is LongArray -> return when {
				v.size == 1 -> v[0].toString()
				else -> Arrays.toString(v)
			}
			
			is Array<*> -> return when {
				v.size == 1 -> v[0]?.toString() ?: ""
				else -> Arrays.toString(v)
			}
			
			else -> return v.toString()
		}
	}
	

	
	companion object {
		/**
		 * The BYTE type in the EXIF standard. An 8-bit unsigned integer.
		 */
		const val TYPE_UNSIGNED_BYTE : Short = 1
		/**
		 * The ASCII type in the EXIF standard. An 8-bit byte containing one 7-bit
		 * ASCII code. The final byte is terminated with NULL.
		 */
		const val TYPE_ASCII : Short = 2
		/**
		 * The SHORT type in the EXIF standard. A 16-bit (2-byte) unsigned integer
		 */
		const val TYPE_UNSIGNED_SHORT : Short = 3
		/**
		 * The LONG type in the EXIF standard. A 32-bit (4-byte) unsigned integer
		 */
		const val TYPE_UNSIGNED_LONG : Short = 4
		/**
		 * The RATIONAL type of EXIF standard. It consists of two LONGs. The first
		 * one is the numerator and the second one expresses the denominator.
		 */
		const val TYPE_UNSIGNED_RATIONAL : Short = 5
		/**
		 * The UNDEFINED type in the EXIF standard. An 8-bit byte that can take any
		 * value depending on the field definition.
		 */
		const val TYPE_UNDEFINED : Short = 7
		/**
		 * The SLONG type in the EXIF standard. A 32-bit (4-byte) signed integer
		 * (2's complement notation).
		 */
		const val TYPE_LONG : Short = 9
		/**
		 * The SRATIONAL type of EXIF standard. It consists of two SLONGs. The first
		 * one is the numerator and the second one is the denominator.
		 */
		const val TYPE_RATIONAL : Short = 10
		internal const val SIZE_UNDEFINED = 0
		private val TYPE_TO_SIZE_MAP = IntArray(11)
		private const val UNSIGNED_SHORT_MAX = 65535
		private const val UNSIGNED_LONG_MAX = 4294967295L
		private const val LONG_MAX = Integer.MAX_VALUE.toLong()
		private const val LONG_MIN = Integer.MIN_VALUE.toLong()
		
		init {
			TYPE_TO_SIZE_MAP[TYPE_UNSIGNED_BYTE.toInt()] = 1
			TYPE_TO_SIZE_MAP[TYPE_ASCII.toInt()] = 1
			TYPE_TO_SIZE_MAP[TYPE_UNSIGNED_SHORT.toInt()] = 2
			TYPE_TO_SIZE_MAP[TYPE_UNSIGNED_LONG.toInt()] = 4
			TYPE_TO_SIZE_MAP[TYPE_UNSIGNED_RATIONAL.toInt()] = 8
			TYPE_TO_SIZE_MAP[TYPE_UNDEFINED.toInt()] = 1
			TYPE_TO_SIZE_MAP[TYPE_LONG.toInt()] = 4
			TYPE_TO_SIZE_MAP[TYPE_RATIONAL.toInt()] = 8
		}
		
		private val TIME_FORMAT = SimpleDateFormat("yyyy:MM:dd kk:mm:ss", Locale.ENGLISH)
		private val US_ASCII = Charset.forName("US-ASCII")
		
		/**
		 * Returns true if the given IFD is a valid IFD.
		 */
		fun isValidIfd(ifdId : Int) : Boolean =
			when(ifdId) {
				IfdId.TYPE_IFD_0,
				IfdId.TYPE_IFD_1,
				IfdId.TYPE_IFD_EXIF,
				IfdId.TYPE_IFD_INTEROPERABILITY,
				IfdId.TYPE_IFD_GPS -> true
				else -> false
			}
		
		/**
		 * Returns true if a given type is a valid tag type.
		 */
		fun isValidType(type : Short) : Boolean =
			when(type) {
				TYPE_UNSIGNED_BYTE,
				TYPE_ASCII,
				TYPE_UNSIGNED_SHORT,
				TYPE_UNSIGNED_LONG,
				TYPE_UNSIGNED_RATIONAL,
				TYPE_UNDEFINED,
				TYPE_LONG,
				TYPE_RATIONAL -> true
				else -> false
			}
		
		/**
		 * Gets the element size of the given data type in bytes.
		 *
		 * @see .TYPE_ASCII
		 * @see .TYPE_LONG
		 * @see .TYPE_RATIONAL
		 * @see .TYPE_UNDEFINED
		 * @see .TYPE_UNSIGNED_BYTE
		 * @see .TYPE_UNSIGNED_LONG
		 * @see .TYPE_UNSIGNED_RATIONAL
		 * @see .TYPE_UNSIGNED_SHORT
		 */
		fun getElementSize(type : Short) : Int = TYPE_TO_SIZE_MAP[type.toInt()]
		
		private fun convertTypeToString(type : Short) : String =
			when(type) {
				TYPE_UNSIGNED_BYTE -> "UNSIGNED_BYTE"
				TYPE_ASCII -> "ASCII"
				TYPE_UNSIGNED_SHORT -> "UNSIGNED_SHORT"
				TYPE_UNSIGNED_LONG -> "UNSIGNED_LONG"
				TYPE_UNSIGNED_RATIONAL -> "UNSIGNED_RATIONAL"
				TYPE_UNDEFINED -> "UNDEFINED"
				TYPE_LONG -> "LONG"
				TYPE_RATIONAL -> "RATIONAL"
				else -> ""
			}
	}
	
}
/**
 * Equivalent to setValue(value, 0, value.length).
 */
/**
 * Equivalent to getBytes(buffer, 0, buffer.length).
 */
