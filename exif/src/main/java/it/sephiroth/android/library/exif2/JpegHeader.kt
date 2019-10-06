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

@Suppress("unused", "MemberVisibilityCanBePrivate")
object JpegHeader {
	/** Start Of Image  */
	const val TAG_SOI = 0xD8
	
	/** JFIF (JPEG File Interchange Format)  */
	const val TAG_M_JFIF = 0xE0
	
	/** EXIF table  */
	const val TAG_M_EXIF = 0xE1
	
	/** Product Information Comment  */
	const val TAG_M_COM = 0xFE
	
	/** Quantization Table  */
	const val TAG_M_DQT = 0xDB
	
	/** Start of frame  */
	const val TAG_M_SOF0 = 0xC0
	const val TAG_M_SOF1 = 0xC1
	const val TAG_M_SOF2 = 0xC2
	const val TAG_M_SOF3 = 0xC3
	const val TAG_M_DHT = 0xC4
	const val TAG_M_SOF5 = 0xC5
	const val TAG_M_SOF6 = 0xC6
	const val TAG_M_SOF7 = 0xC7
	const val TAG_M_SOF9 = 0xC9
	const val TAG_M_SOF10 = 0xCA
	const val TAG_M_SOF11 = 0xCB
	const val TAG_M_SOF13 = 0xCD
	const val TAG_M_SOF14 = 0xCE
	const val TAG_M_SOF15 = 0xCF
	
	/** Start Of Scan  */
	const val TAG_M_SOS = 0xDA
	
	/** End of Image  */
	const val TAG_M_EOI = 0xD9
	
	const val TAG_M_IPTC = 0xED
	
	/** default JFIF Header bytes  */
	val JFIF_HEADER = byteArrayOf(
		0xff.toByte(),
		TAG_M_JFIF.toByte(),
		0x00,
		0x10,
		'J'.toByte(),
		'F'.toByte(),
		'I'.toByte(),
		'F'.toByte(),
		0x00,
		0x01,
		0x01,
		0x01,
		0x01,
		0x2C,
		0x01,
		0x2C,
		0x00,
		0x00
	)
	
	const val SOI = 0xFFD8.toShort()
	const val M_EXIF = 0xFFE1.toShort()
	const val M_JFIF = 0xFFE0.toShort()
	const val M_EOI = 0xFFD9.toShort()
	
	/**
	 * SOF (start of frame). All value between M_SOF0 and SOF15 is SOF marker except for M_DHT, JPG,
	 * and DAC marker.
	 */
	const val M_SOF0 = 0xFFC0.toShort()
	const val M_SOF1 = 0xFFC1.toShort()
	const val M_SOF2 = 0xFFC2.toShort()
	const val M_SOF3 = 0xFFC3.toShort()
	const val M_SOF5 = 0xFFC5.toShort()
	const val M_SOF6 = 0xFFC6.toShort()
	const val M_SOF7 = 0xFFC7.toShort()
	const val M_SOF9 = 0xFFC9.toShort()
	const val M_SOF10 = 0xFFCA.toShort()
	const val M_SOF11 = 0xFFCB.toShort()
	const val M_SOF13 = 0xFFCD.toShort()
	const val M_SOF14 = 0xFFCE.toShort()
	const val M_SOF15 = 0xFFCF.toShort()
	const val M_DHT = 0xFFC4.toShort()
	const val JPG = 0xFFC8.toShort()
	const val DAC = 0xFFCC.toShort()
	
	/** Define quantization table  */
	const val M_DQT = 0xFFDB.toShort()
	
	/** IPTC marker  */
	const val M_IPTC = 0xFFED.toShort()
	
	/** Start of scan (begins compressed data  */
	const val M_SOS = 0xFFDA.toShort()
	
	/** Comment section *  */
	const val M_COM = 0xFFFE.toShort()          // Comment section
	
	fun isSofMarker(marker : Short) : Boolean {
		return marker >= M_SOF0 && marker <= M_SOF15 && marker != M_DHT && marker != JPG && marker != DAC
	}
}
