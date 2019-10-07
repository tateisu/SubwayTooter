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

package it.sephiroth.android.library.exif2.utils

import java.io.EOFException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

@Suppress("unused")
internal class CountedDataInputStream constructor(`in` : InputStream) :
	FilterInputStream(`in`) {
	
	// allocate a byte buffer for a long value;
	private val mByteArray = ByteArray(8)
	private val mByteBuffer = ByteBuffer.wrap(mByteArray)
	var readByteCount = 0
		private set
	var end = 0
	
	var byteOrder : ByteOrder
		get() = mByteBuffer.order()
		set(order) {
			mByteBuffer.order(order)
		}
	
	@Throws(IOException::class)
	override fun read(b : ByteArray) : Int {
		val r = `in`.read(b)
		readByteCount += if(r >= 0) r else 0
		return r
	}
	
	@Throws(IOException::class)
	override fun read() : Int {
		val r = `in`.read()
		readByteCount += if(r >= 0) 1 else 0
		return r
	}
	
	@Throws(IOException::class)
	override fun read(b : ByteArray, off : Int, len : Int) : Int {
		val r = `in`.read(b, off, len)
		readByteCount += if(r >= 0) r else 0
		return r
	}
	
	@Throws(IOException::class)
	override fun skip(length : Long) : Long {
		val skip = `in`.skip(length)
		readByteCount += skip.toInt()
		return skip
	}
	
	@Throws(IOException::class)
	fun skipTo(target : Long) {
		val cur = readByteCount.toLong()
		val diff = target - cur
		if(diff < 0) throw IndexOutOfBoundsException("skipTo: negative move")
		skipOrThrow(diff)
	}
	
	@Throws(IOException::class)
	fun skipOrThrow(length : Long) {
		if(skip(length) != length) throw EOFException()
	}
	
	@Throws(IOException::class)
	fun readUnsignedShort() : Int = readShort().toInt() and 0xffff
	
	@Throws(IOException::class)
	fun readShort() : Short {
		readOrThrow(mByteArray, 0, 2)
		mByteBuffer.rewind()
		return mByteBuffer.short
	}
	
	@Throws(IOException::class)
	fun readByte() : Byte {
		readOrThrow(mByteArray, 0, 1)
		mByteBuffer.rewind()
		return mByteBuffer.get()
	}
	
	@Throws(IOException::class)
	fun readUnsignedByte() : Int {
		readOrThrow(mByteArray, 0, 1)
		mByteBuffer.rewind()
		return mByteBuffer.get().toInt() and 0xff
	}
	
	@Throws(IOException::class)
	@JvmOverloads
	fun readOrThrow(b : ByteArray, off : Int = 0, len : Int = b.size) {
		val r = read(b, off, len)
		if(r != len) throw EOFException()
	}
	
	@Throws(IOException::class)
	fun readUnsignedInt() : Long {
		return readInt().toLong() and 0xffffffffL
	}
	
	@Throws(IOException::class)
	fun readInt() : Int {
		readOrThrow(mByteArray, 0, 4)
		mByteBuffer.rewind()
		return mByteBuffer.int
	}
	
	@Throws(IOException::class)
	fun readLong() : Long {
		readOrThrow(mByteArray, 0, 8)
		mByteBuffer.rewind()
		return mByteBuffer.long
	}
	
	@Throws(IOException::class)
	fun readString(n : Int) : String {
		val buf = ByteArray(n)
		readOrThrow(buf)
		return String(buf, StandardCharsets.UTF_8)
	}
	
	@Throws(IOException::class)
	fun readString(n : Int, charset : Charset) : String {
		val buf = ByteArray(n)
		readOrThrow(buf)
		return String(buf, charset)
	}
}