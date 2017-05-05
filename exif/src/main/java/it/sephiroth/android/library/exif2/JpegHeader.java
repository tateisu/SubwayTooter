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

package it.sephiroth.android.library.exif2;

class JpegHeader {
	/** Start Of Image **/
	public static final int TAG_SOI = 0xD8;

	/** JFIF (JPEG File Interchange Format) */
	public static final int TAG_M_JFIF = 0xE0;

	/** EXIF table */
	public static final int TAG_M_EXIF = 0xE1;

	/** Product Information Comment */
	public static final int TAG_M_COM = 0xFE;

	/** Quantization Table */
	public static final int TAG_M_DQT = 0xDB;

	/** Start of frame */
	public static final int TAG_M_SOF0 = 0xC0;
	public static final int TAG_M_SOF1 = 0xC1;
	public static final int TAG_M_SOF2 = 0xC2;
	public static final int TAG_M_SOF3 = 0xC3;
	public static final int TAG_M_DHT = 0xC4;
	public static final int TAG_M_SOF5 = 0xC5;
	public static final int TAG_M_SOF6 = 0xC6;
	public static final int TAG_M_SOF7 = 0xC7;
	public static final int TAG_M_SOF9 = 0xC9;
	public static final int TAG_M_SOF10 = 0xCA;
	public static final int TAG_M_SOF11 = 0xCB;
	public static final int TAG_M_SOF13 = 0xCD;
	public static final int TAG_M_SOF14 = 0xCE;
	public static final int TAG_M_SOF15 = 0xCF;

	/** Start Of Scan **/
	public static final int TAG_M_SOS = 0xDA;

	/** End of Image */
	public static final int TAG_M_EOI = 0xD9;

	public static final int TAG_M_IPTC = 0xED;

	/** default JFIF Header bytes */
	public static final byte JFIF_HEADER[] = {
			(byte) 0xff, (byte) JpegHeader.TAG_M_JFIF,
			0x00, 0x10, 'J', 'F', 'I', 'F',
			0x00, 0x01, 0x01, 0x01, 0x01, 0x2C, 0x01,
			0x2C, 0x00, 0x00
	};


	public static final short SOI = (short) 0xFFD8;
	public static final short M_EXIF = (short) 0xFFE1;
	public static final short M_JFIF = (short) 0xFFE0;
	public static final short M_EOI = (short) 0xFFD9;

	/**
	 * SOF (start of frame). All value between M_SOF0 and SOF15 is SOF marker except for M_DHT, JPG,
	 * and DAC marker.
	 */
	public static final short M_SOF0 = (short) 0xFFC0;
	public static final short M_SOF1 = (short) 0xFFC1;
	public static final short M_SOF2 = (short) 0xFFC2;
	public static final short M_SOF3 = (short) 0xFFC3;
	public static final short M_SOF5 = (short) 0xFFC5;
	public static final short M_SOF6 = (short) 0xFFC6;
	public static final short M_SOF7 = (short) 0xFFC7;
	public static final short M_SOF9 = (short) 0xFFC9;
	public static final short M_SOF10 = (short) 0xFFCA;
	public static final short M_SOF11 = (short) 0xFFCB;
	public static final short M_SOF13 = (short) 0xFFCD;
	public static final short M_SOF14 = (short) 0xFFCE;
	public static final short M_SOF15 = (short) 0xFFCF;
	public static final short M_DHT = (short) 0xFFC4;
	public static final short JPG = (short) 0xFFC8;
	public static final short DAC = (short) 0xFFCC;

	/** Define quantization table */
	public static final short M_DQT = (short) 0xFFDB;

	/** IPTC marker */
	public static final short M_IPTC = (short) 0xFFED;

	/** Start of scan (begins compressed data */
	public static final short M_SOS = (short) 0xFFDA;

	/** Comment section * */
	public static final short M_COM = (short) 0xFFFE;          // Comment section

	public static final boolean isSofMarker( short marker ) {
		return marker >= M_SOF0 && marker <= M_SOF15 && marker != M_DHT && marker != JPG && marker != DAC;
	}
}
