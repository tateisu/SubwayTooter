///*
// * Copyright (C) 2012 The Android Open Source Project
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//

package it.sephiroth.android.library.exif2

//
//import java.io.InputStream
//import java.nio.ByteBuffer
//import kotlin.math.min
//
//internal class ByteBufferInputStream(private val mBuf : ByteBuffer) : InputStream() {
//
//	override fun read() : Int = when {
//		! mBuf.hasRemaining() -> - 1
//		else -> mBuf.get().toInt() and 0xFF
//	}
//
//	override fun read(bytes : ByteArray, off : Int, len : Int) : Int {
//		if(! mBuf.hasRemaining()) return - 1
//		val willRead = min(len, mBuf.remaining())
//		mBuf.get(bytes, off, willRead)
//		return willRead
//	}
//}
