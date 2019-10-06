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

import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class OrderedDataOutputStream(out : OutputStream) : FilterOutputStream(out) {
	private val mByteBuffer = ByteBuffer.allocate(4)
	
	fun setByteOrder(order : ByteOrder) : OrderedDataOutputStream {
		mByteBuffer.order(order)
		return this
	}
	
	@Throws(IOException::class)
	fun writeShort(value : Short) : OrderedDataOutputStream {
		mByteBuffer.rewind()
		mByteBuffer.putShort(value)
		out.write(mByteBuffer.array(), 0, 2)
		return this
	}
	
	@Throws(IOException::class)
	fun writeRational(rational : Rational) : OrderedDataOutputStream {
		writeInt(rational.numerator.toInt())
		writeInt(rational.denominator.toInt())
		return this
	}
	
	@Throws(IOException::class)
	fun writeInt(value : Int) : OrderedDataOutputStream {
		mByteBuffer.rewind()
		mByteBuffer.putInt(value)
		out.write(mByteBuffer.array())
		return this
	}
}
