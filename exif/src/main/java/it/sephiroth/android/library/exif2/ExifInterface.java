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

package it.sephiroth.android.library.exif2;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseIntArray;

import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * This class provides methods and constants for reading and writing jpeg file
 * metadata. It contains a collection of ExifTags, and a collection of
 * definitions for creating valid ExifTags. The collection of ExifTags can be
 * updated by: reading new ones from a file, deleting or adding existing ones,
 * or building new ExifTags from a tag definition. These ExifTags can be written
 * to a valid jpeg image as exif metadata.
 * <p/>
 * Each ExifTag has a tag ID (TID) and is stored in a specific image file
 * directory (IFD) as specified by the exif standard. A tag definition can be
 * looked up with a constant that is a combination of TID and IFD. This
 * definition has information about the type, number of components, and valid
 * IFDs for a tag.
 *
 * @see ExifTag
 */
public class ExifInterface {
	private static final String TAG = "ExifInterface";

	public static final int TAG_NULL = - 1;
	public static final int IFD_NULL = - 1;
	public static final int DEFINITION_NULL = 0;

	/**
	 * Tag constants for Jeita EXIF 2.2
	 */

	// IFD 0
	public static final int TAG_IMAGE_WIDTH = defineTag( IfdId.TYPE_IFD_0, (short) 0x0100 );
	public static final int TAG_IMAGE_LENGTH = defineTag( IfdId.TYPE_IFD_0, (short) 0x0101 ); // Image height
	public static final int TAG_BITS_PER_SAMPLE = defineTag( IfdId.TYPE_IFD_0, (short) 0x0102 );

	/**
	 * Value is unsigned int.<br />
	 * (Read only tag) The compression scheme used for the image data. When a primary image is JPEG compressed, this designation is
	 * not necessary and is omitted. When thumbnails use JPEG compression, this tag value is set to 6.
	 * <ul>
	 * <li>1 = uncompressed</li>
	 * <li>6 = JPEG compression (thumbnails only)</li>
	 * <li>Other = reserved</li>
	 */
	public static final int TAG_COMPRESSION = defineTag( IfdId.TYPE_IFD_0, (short) 0x0103 );
	public static final int TAG_PHOTOMETRIC_INTERPRETATION = defineTag( IfdId.TYPE_IFD_0, (short) 0x0106 );
	public static final int TAG_IMAGE_DESCRIPTION = defineTag( IfdId.TYPE_IFD_0, (short) 0x010E );

	/**
	 * Value is ascii string<br />
	 * The manufacturer of the recording equipment. This is the manufacturer of the DSC, scanner, video digitizer or other equipment
	 * that generated the image. When the field is left blank, it is treated as unknown.
	 */
	public static final int TAG_MAKE = defineTag( IfdId.TYPE_IFD_0, (short) 0x010F );

	/**
	 * Value is ascii string<br />
	 * The model name or model number of the equipment. This is the model name of number of the DSC, scanner, video digitizer or
	 * other equipment that generated the image. When the field is left blank, it is treated as unknown.
	 */
	public static final int TAG_MODEL = defineTag( IfdId.TYPE_IFD_0, (short) 0x0110 );
	public static final int TAG_STRIP_OFFSETS = defineTag( IfdId.TYPE_IFD_0, (short) 0x0111 );

	/**
	 * Value is int<br />
	 * The orientation of the camera relative to the scene, when the image was captured. The start point of stored data is:
	 * <ul>
	 * <li>'0' undefined</li>
	 * <li>'1' normal</li>
	 * <li>'2' flip horizontal</li>
	 * <li>'3' rotate 180</li>
	 * <li>'4' flip vertical</li>
	 * <li>'5' transpose, flipped about top-left <--> bottom-right axis</li>
	 * <li>'6' rotate 90 cw</li>
	 * <li>'7' transverse, flipped about top-right <--> bottom-left axis</li>
	 * <li>'8' rotate 270</li>
	 * <li>'9' undefined</li>
	 * </ul>
	 */
	public static final int TAG_ORIENTATION = defineTag( IfdId.TYPE_IFD_0, (short) 0x0112 );
	public static final int TAG_SAMPLES_PER_PIXEL = defineTag( IfdId.TYPE_IFD_0, (short) 0x0115 );
	public static final int TAG_ROWS_PER_STRIP = defineTag( IfdId.TYPE_IFD_0, (short) 0x0116 );
	public static final int TAG_STRIP_BYTE_COUNTS = defineTag( IfdId.TYPE_IFD_0, (short) 0x0117 );

	public static final int TAG_INTEROP_VERSION = defineTag(IfdId.TYPE_IFD_INTEROPERABILITY, (short)0x0002);

	/**
	 * Value is unsigned double.<br />
	 * Display/Print resolution of image. Large number of digicam uses 1/72inch, but it has no mean because personal computer doesn't
	 * use this value to display/print out.
	 */
	public static final int TAG_X_RESOLUTION = defineTag( IfdId.TYPE_IFD_0, (short) 0x011A );

	/**
	 * @see #TAG_X_RESOLUTION
	 */
	public static final int TAG_Y_RESOLUTION = defineTag( IfdId.TYPE_IFD_0, (short) 0x011B );
	public static final int TAG_PLANAR_CONFIGURATION = defineTag( IfdId.TYPE_IFD_0, (short) 0x011C );

	/**
	 * Value is unsigned int.<br />
	 * Unit of XResolution(0x011a)/YResolution(0x011b)
	 * <ul>
	 * <li>'1' means no-unit ( use inch )</li>
	 * <li>'2' inch</li>
	 * <li>'3' centimeter</li>
	 * <li>'4' millimeter</li>
	 * <li>'5' micrometer</li>
	 * </ul>
	 */
	public static final int TAG_RESOLUTION_UNIT = defineTag( IfdId.TYPE_IFD_0, (short) 0x0128 );
	public static final int TAG_TRANSFER_FUNCTION = defineTag( IfdId.TYPE_IFD_0, (short) 0x012D );

	/**
	 * Value is ascii string<br />
	 * Shows firmware(internal software of digicam) version number.
	 */
	public static final int TAG_SOFTWARE = defineTag( IfdId.TYPE_IFD_0, (short) 0x0131 );

	/**
	 * Value is ascii string (20)<br />
	 * Date/Time of image was last modified. Data format is "YYYY:MM:DD HH:MM:SS"+0x00, total 20bytes. In usual, it has the same
	 * value of DateTimeOriginal(0x9003)
	 */
	public static final int TAG_DATE_TIME = defineTag( IfdId.TYPE_IFD_0, (short) 0x0132 );

	/**
	 * Vallue is ascii String<br />
	 * This tag records the name of the camera owner, photographer or image creator. The detailed format is not specified, but it is
	 * recommended that the information be written as in the example below for ease of Interoperability. When the field is left
	 * blank, it is treated as unknown.
	 */
	public static final int TAG_ARTIST = defineTag( IfdId.TYPE_IFD_0, (short) 0x013B );
	public static final int TAG_WHITE_POINT = defineTag( IfdId.TYPE_IFD_0, (short) 0x013E );
	public static final int TAG_PRIMARY_CHROMATICITIES = defineTag( IfdId.TYPE_IFD_0, (short) 0x013F );
	public static final int TAG_Y_CB_CR_COEFFICIENTS = defineTag( IfdId.TYPE_IFD_0, (short) 0x0211 );
	public static final int TAG_Y_CB_CR_SUB_SAMPLING = defineTag( IfdId.TYPE_IFD_0, (short) 0x0212 );
	public static final int TAG_Y_CB_CR_POSITIONING = defineTag( IfdId.TYPE_IFD_0, (short) 0x0213 );
	public static final int TAG_REFERENCE_BLACK_WHITE = defineTag( IfdId.TYPE_IFD_0, (short) 0x0214 );

	/**
	 * Values is ascii string<br />
	 * Shows copyright information
	 */
	public static final int TAG_COPYRIGHT = defineTag( IfdId.TYPE_IFD_0, (short) 0x8298 );
	public static final int TAG_EXIF_IFD = defineTag( IfdId.TYPE_IFD_0, (short) 0x8769 );
	public static final int TAG_GPS_IFD = defineTag( IfdId.TYPE_IFD_0, (short) 0x8825 );
	// IFD 1
	public static final int TAG_JPEG_INTERCHANGE_FORMAT = defineTag( IfdId.TYPE_IFD_1, (short) 0x0201 );
	public static final int TAG_JPEG_INTERCHANGE_FORMAT_LENGTH = defineTag( IfdId.TYPE_IFD_1, (short) 0x0202 );
	// IFD Exif Tags

	/**
	 * Value is unsigned double<br />
	 * Exposure time (reciprocal of shutter speed). Unit is second
	 */
	public static final int TAG_EXPOSURE_TIME = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x829A );

	/**
	 * Value is unsigned double<br />
	 * The actual F-number(F-stop) of lens when the image was taken
	 *
	 * @see #TAG_APERTURE_VALUE
	 */
	public static final int TAG_F_NUMBER = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x829D );

	/**
	 * Value is unsigned int.<br />
	 * Exposure program that the camera used when image was taken.
	 * <ul>
	 * <li>'1' means manual control</li>
	 * <li>'2' program normal</li>
	 * <li>'3' aperture priority</li>
	 * <li>'4' shutter priority</li>
	 * <li>'5' program creative (slow program)</li>
	 * <li>'6' program action(high-speed program)</li>
	 * <li>'7' portrait mode</li>
	 * <li>'8' landscape mode.</li>
	 * </ul>
	 */
	public static final int TAG_EXPOSURE_PROGRAM = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x8822 );
	public static final int TAG_SPECTRAL_SENSITIVITY = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x8824 );

	/**
	 * Value is unsigned int.<br />
	 * CCD sensitivity equivalent to Ag-Hr film speedrate.<br />
	 * Indicates the ISO Speed and ISO Latitude of the camera or input device as specified in ISO 12232
	 */
	public static final int TAG_ISO_SPEED_RATINGS = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x8827 );
	public static final int TAG_OECF = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x8828 );

	/**
	 * ASCII string (4).<br />
	 * The version of this standard supported. Nonexistence of this field is taken to mean nonconformance to the standard (see
	 * section 4.2). Conformance to this standard is indicated by recording "0220" as 4-byte ASCII
	 */
	public static final int TAG_EXIF_VERSION = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x9000 );

	/**
	 * Value is ascii string (20)<br />
	 * Date/Time of original image taken. This value should not be modified by user program.
	 */
	public static final int TAG_DATE_TIME_ORIGINAL = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x9003 );

	/**
	 * Value is ascii string (20)<br />
	 * Date/Time of image digitized. Usually, it contains the same value of DateTimeOriginal(0x9003).
	 */
	public static final int TAG_DATE_TIME_DIGITIZED = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x9004 );
	public static final int TAG_COMPONENTS_CONFIGURATION = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x9101 );
	public static final int TAG_COMPRESSED_BITS_PER_PIXEL = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x9102 );

	/**
	 * Value is signed double.<br />
	 * Shutter speed. To convert this value to ordinary 'Shutter Speed'; calculate this value's power of 2, then reciprocal. For
	 * example, if value is '4', shutter speed is 1/(2^4)=1/16 second.
	 */
	public static final int TAG_SHUTTER_SPEED_VALUE = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x9201 );


	/**
	 * Value is unsigned double<br />
	 * The actual aperture value of lens when the image was taken.<br />
	 * To convert this value to ordinary F-number(F-stop), calculate this value's power of root 2 (=1.4142).<br />
	 * For example, if value is '5', F-number is 1.4142^5 = F5.6<br />
	 * <p/>
	 * <pre>
	 * FNumber = Math.exp( ApertureValue * Math.log( 2 ) * 0.5 );
	 * </pre>
	 *
	 * @see #TAG_F_NUMBER
	 */
	public static final int TAG_APERTURE_VALUE = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x9202 );

	/**
	 * Value is signed double<br />
	 * Brightness of taken subject, unit is EV.
	 */
	public static final int TAG_BRIGHTNESS_VALUE = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x9203 );

	/**
	 * Value is signed double.<br />
	 * The exposure bias. The unit is the APEX value. Ordinarily it is given in the range of -99.99 to 99.99
	 */
	public static final int TAG_EXPOSURE_BIAS_VALUE = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x9204 );

	/**
	 * Value is unsigned double.<br />
	 * Maximum aperture value of lens.<br />
	 * You can convert to F-number by calculating power of root 2 (same process of ApertureValue(0x9202).<br />
	 * <p/>
	 * <pre>
	 * FNumber = Math.exp( MaxApertureValue * Math.log( 2 ) * 0.5 )
	 * </pre>
	 */
	public static final int TAG_MAX_APERTURE_VALUE = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x9205 );

	/**
	 * Value if signed double.<br />
	 * Distance to focus point, unit is meter. If value < 0 then focus point is infinite
	 */
	public static final int TAG_SUBJECT_DISTANCE = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x9206 );

	/**
	 * Value is unsigned int.<br />
	 * Exposure metering method:
	 * <ul>
	 * <li>0 = unknown</li>
	 * <li>1 = Average</li>
	 * <li>2 = CenterWeightedAverage</li>
	 * <li>3 = Spot</li>
	 * <li>4 = MultiSpot</li>
	 * <li>5 = Pattern</li>
	 * <li>6 = Partial</li>
	 * <li>Other = reserved</li>
	 * <li>255 = other</li>
	 * </ul>
	 */
	public static final int TAG_METERING_MODE = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x9207 );

	/**
	 * Value is unsigned int.<br />
	 * Light source, actually this means white balance setting.
	 * <ul>
	 * <li>0 = means auto</li>
	 * <li>1 = Daylight</li>
	 * <li>2 = Fluorescent</li>
	 * <li>3 = Tungsten (incandescent light)</li>
	 * <li>4 = Flash</li>
	 * <li>9 = Fine weather</li>
	 * <li>10 = Cloudy weather</li>
	 * <li>11 = Shade</li>
	 * <li>12 = Daylight fluorescent (D 5700 - 7100K)</li>
	 * <li>13 = Day white fluorescent (N 4600 - 5400K)</li>
	 * <li>14 = Cool white fluorescent (W 3900 - 4500K)</li>
	 * <li>15 = White fluorescent (WW 3200 - 3700K)</li>
	 * <li>17 = Standard light A</li>
	 * <li>18 = Standard light B</li>
	 * <li>19 = Standard light C</li>
	 * <li>20 = D55</li>
	 * <li>21 = D65</li>
	 * <li>22 = D75</li>
	 * <li>23 = D50</li>
	 * <li>24 = ISO studio tungsten</li>
	 * <li>255 = other light source</li>
	 * <li>Other = reserved</li>
	 * </ul>
	 */
	public static final int TAG_LIGHT_SOURCE = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x9208 );

	/**
	 * Value is unsigned integer<br />
	 * The 8 bits can be extracted and evaluated in this way:<br />
	 * <ol>
	 * <li>Bit 0 indicates the flash firing status</li>
	 * <li>bits 1 and 2 indicate the flash return status</li>
	 * <li>bits 3 and 4 indicate the flash mode</li>
	 * <li>bit 5 indicates whether the flash function is present</li>
	 * <li>and bit 6 indicates "red eye" mode</li>
	 * <li>bit 7 unused</li>
	 * </ol>
	 * <p/>
	 * Resulting Flash tag values are:<br />
	 * <ul>
	 * <li>0000.H = Flash did not fire</li>
	 * <li>0001.H = Flash fired</li>
	 * <li>0005.H = Strobe return light not detected</li>
	 * <li>0007.H = Strobe return light detected</li>
	 * <li>0009.H = Flash fired, compulsory flash mode</li>
	 * <li>000D.H = Flash fired, compulsory flash mode, return light not detected</li>
	 * <li>000F.H = Flash fired, compulsory flash mode, return light detected</li>
	 * <li>0010.H = Flash did not fire, compulsory flash mode</li>
	 * <li>0018.H = Flash did not fire, auto mode</li>
	 * <li>0019.H = Flash fired, auto mode</li>
	 * <li>001D.H = Flash fired, auto mode, return light not detected</li>
	 * <li>001F.H = Flash fired, auto mode, return light detected</li>
	 * <li>0020.H = No flash function</li>
	 * <li>0041.H = Flash fired, red-eye reduction mode</li>
	 * <li>0045.H = Flash fired, red-eye reduction mode, return light not detected</li>
	 * <li>0047.H = Flash fired, red-eye reduction mode, return light detected</li>
	 * <li>0049.H = Flash fired, compulsory flash mode, red-eye reduction mode</li>
	 * <li>004D.H = Flash fired, compulsory flash mode, red-eye reduction mode, return light not detected</li>
	 * <li>004F.H = Flash fired, compulsory flash mode, red-eye reduction mode, return light detected</li>
	 * <li>0059.H = Flash fired, auto mode, red-eye reduction mode</li>
	 * <li>005D.H = Flash fired, auto mode, return light not detected, red-eye reduction mode</li>
	 * <li>005F.H = Flash fired, auto mode, return light detected, red-eye reduction mode</li>
	 * <li>Other = reserved</li>
	 * </ul>
	 *
	 * @see <a href="http://www.exif.org/Exif2-2.PDF">http://www.exif.org/Exif2-2.PDF</a>
	 */
	public static final int TAG_FLASH = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x9209 );

	/**
	 * Value is unsigned double<br />
	 * Focal length of lens used to take image. Unit is millimeter.
	 */
	public static final int TAG_FOCAL_LENGTH = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x920A );
	public static final int TAG_SUBJECT_AREA = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x9214 );
	public static final int TAG_MAKER_NOTE = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x927C );
	public static final int TAG_USER_COMMENT = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x9286 );
	public static final int TAG_SUB_SEC_TIME = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x9290 );
	public static final int TAG_SUB_SEC_TIME_ORIGINAL = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x9291 );
	public static final int TAG_SUB_SEC_TIME_DIGITIZED = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x9292 );
	public static final int TAG_FLASHPIX_VERSION = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA000 );

	/**
	 * Value is int.<br />
	 * Normally sRGB (=1) is used to define the color space based on the PC monitor conditions and environment. If a color space
	 * other than sRGB is used, Uncalibrated (=FFFF.H) is set. Image data recorded as Uncalibrated can be treated as sRGB when it is
	 * converted to Flashpix. On sRGB see Annex E.
	 * <ul>
	 * <li>'1' = sRGB</li>
	 * <li>'FFFF' = Uncalibrated</li>
	 * <li>'other' = Reserved</li>
	 * </ul>
	 */
	public static final int TAG_COLOR_SPACE = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA001 );

	/**
	 * Value is unsigned int.<br />
	 * Specific to compressed data; the valid width of the meaningful image. When a compressed file is recorded, the valid width of
	 * the meaningful image shall be recorded in this tag, whether or not there is padding data or a restart marker. This tag should
	 * not exist in an uncompressed file.
	 */
	public static final int TAG_PIXEL_X_DIMENSION = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA002 );

	/**
	 * @see #TAG_PIXEL_X_DIMENSION
	 */
	public static final int TAG_PIXEL_Y_DIMENSION = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA003 );
	public static final int TAG_RELATED_SOUND_FILE = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA004 );
	public static final int TAG_INTEROPERABILITY_IFD = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA005 );
	public static final int TAG_FLASH_ENERGY = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA20B );
	public static final int TAG_SPATIAL_FREQUENCY_RESPONSE = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA20C );

	/**
	 * Value is unsigned double.<br />
	 * Indicates the number of pixels in the image width (X) direction per FocalPlaneResolutionUnit on the camera focal plane. CCD's
	 * pixel density
	 *
	 * @see #TAG_FOCAL_PLANE_RESOLUTION_UNIT
	 */
	public static final int TAG_FOCAL_PLANE_X_RESOLUTION = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA20E );

	/**
	 * @see #TAG_FOCAL_PLANE_X_RESOLUTION
	 */
	public static final int TAG_FOCAL_PLANE_Y_RESOLUTION = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA20F );

	/**
	 * Value is unsigned int.<br />
	 * Unit of FocalPlaneXResoluton/FocalPlaneYResolution.
	 * <ul>
	 * <li>'1' means no-unit</li>
	 * <li>'2' inch</li>
	 * <li>'3' centimeter</li>
	 * <li>'4' millimeter</li>
	 * <li>'5' micrometer</li>
	 * </ul>
	 * <p/>
	 * This tag can be used to calculate the CCD Width:
	 * <p/>
	 * <pre>
	 * CCDWidth = ( PixelXDimension * FocalPlaneResolutionUnit / FocalPlaneXResolution )
	 * </pre>
	 */
	public static final int TAG_FOCAL_PLANE_RESOLUTION_UNIT = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA210 );
	public static final int TAG_SUBJECT_LOCATION = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA214 );
	public static final int TAG_EXPOSURE_INDEX = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA215 );

	/**
	 * Value is unsigned int.<br />
	 * Indicates the image sensor type on the camera or input device. The values are as follows:
	 * <ul>
	 * <li>1 = Not defined</li>
	 * <li>2 = One-chip color area sensor</li>
	 * <li>3 = Two-chip color area sensor JEITA CP-3451 - 41</li>
	 * <li>4 = Three-chip color area sensor</li>
	 * <li>5 = Color sequential area sensor</li>
	 * <li>7 = Trilinear sensor</li>
	 * <li>8 = Color sequential linear sensor</li>
	 * <li>Other = reserved</li>
	 * </ul>
	 */
	public static final int TAG_SENSING_METHOD = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA217 );
	public static final int TAG_FILE_SOURCE = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA300 );
	public static final int TAG_SCENE_TYPE = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA301 );
	public static final int TAG_CFA_PATTERN = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA302 );
	public static final int TAG_CUSTOM_RENDERED = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA401 );

	/**
	 * Value is int.<br />
	 * This tag indicates the exposure mode set when the image was shot. In auto-bracketing mode, the camera shoots a series of
	 * frames of the same scene at different exposure settings.
	 * <ul>
	 * <li>0 = Auto exposure</li>
	 * <li>1 = Manual exposure</li>
	 * <li>2 = Auto bracket</li>
	 * <li>Other = reserved</li>
	 * </ul>
	 */
	public static final int TAG_EXPOSURE_MODE = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA402 );
	public static final int TAG_WHITE_BALANCE = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA403 );

	/**
	 * Value is double.<br />
	 * This tag indicates the digital zoom ratio when the image was shot. If the numerator of the recorded value is 0, this indicates
	 * that digital zoom was not used
	 */
	public static final int TAG_DIGITAL_ZOOM_RATIO = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA404 );

	/**
	 * Value is unsigned int.<br />
	 * This tag indicates the equivalent focal length assuming a 35mm film camera, in mm.<br />
	 * Exif 2.2 tag, usually not present, it can be calculated by:
	 * <p/>
	 * <pre>
	 * CCDWidth = ( PixelXDimension * FocalplaneUnits / FocalplaneXRes );
	 * FocalLengthIn35mmFilm = ( FocalLength / CCDWidth * 36 + 0.5 );
	 * </pre>
	 */
	public static final int TAG_FOCAL_LENGTH_IN_35_MM_FILE = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA405 );

	/**
	 * Value is int.<br />
	 * This tag indicates the type of scene that was shot. It can also be used to record the mode in which the image was shot. Note
	 * that this differs from the scene type (SceneType) tag.
	 * <ul>
	 * <li>0 = Standard</li>
	 * <li>1 = Landscape</li>
	 * <li>2 = Portrait</li>
	 * <li>3 = Night scene</li>
	 * <li>Other = reserved</li>
	 * </ul>
	 */
	public static final int TAG_SCENE_CAPTURE_TYPE = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA406 );

	/**
	 * Value is int.<br />
	 * This tag indicates the degree of overall image gain adjustment.
	 * <ul>
	 * <li>0 = None</li>
	 * <li>1 = Low gain up</li>
	 * <li>2 = High gain up</li>
	 * <li>3 = Low gain down</li>
	 * <li>4 = High gain down</li>
	 * <li>Other = reserved</li>
	 * </ul>
	 */
	public static final int TAG_GAIN_CONTROL = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA407 );

	/**
	 * Value is int.<br />
	 * This tag indicates the direction of contrast processing applied by the camera when the image was shot.
	 * <ul>
	 * <li>0 = Normal</li>
	 * <li>1 = Soft</li>
	 * <li>2 = Hard</li>
	 * <li>Other = reserved</li>
	 * </ul>
	 */
	public static final int TAG_CONTRAST = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA408 );

	/**
	 * Value is int.<br />
	 * This tag indicates the direction of saturation processing applied by the camera when the image was shot.
	 * <ul>
	 * <li>0 = Normal</li>
	 * <li>1 = Low saturation</li>
	 * <li>2 = High saturation</li>
	 * <li>Other = reserved</li>
	 * </ul>
	 */
	public static final int TAG_SATURATION = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA409 );

	/**
	 * Value is int.<br />
	 * This tag indicates the direction of sharpness processing applied by the camera when the image was shot
	 * <ul>
	 * <li>0 = Normal</li>
	 * <li>1 = Soft</li>
	 * <li>2 = Hard</li>
	 * <li>Other = reserved</li>
	 * </ul>
	 */
	public static final int TAG_SHARPNESS = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA40A );
	public static final int TAG_DEVICE_SETTING_DESCRIPTION = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA40B );

	/**
	 * Value is int.<br />
	 * This tag indicates the distance to the subject.
	 * <ul>
	 * <li>0 = unknown</li>
	 * <li>1 = Macro</li>
	 * <li>2 = Close view</li>
	 * <li>3 = Distant view</li>
	 * <li>Other = reserved</li>
	 * </ul>
	 */
	public static final int TAG_SUBJECT_DISTANCE_RANGE = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA40C );

	/**
	 * {@link ExifTag#TYPE_ASCII}
	 */
	public static final int TAG_IMAGE_UNIQUE_ID = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA420 );

	/**
	 * Lens Specifications. The value it's a 4 rational containing:
	 * <ol>
	 * <li>Minimum focal length (in mm)</li>
	 * <li>Maximum focal length (in mm)</li>
	 * <li>Minimum F Number in the minimum focal length</li>
	 * <li>Maximum F Number in the maximum focal length</li>
	 * </ol>
	 *
	 * {@link ExifTag#TYPE_RATIONAL}
	 * @since EXIF 2.3
	 * @see it.sephiroth.android.library.exif2.ExifUtil#processLensSpecifications(Rational[])
	 */
	public static final int TAG_LENS_SPECS = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA432 );

	/**
	 * Lens maker
	 * {@link ExifTag#TYPE_ASCII}
	 * @since EXIF 2.3
	 */
	public static final int TAG_LENS_MAKE = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA433 );
	/**
	 * Lens model name and number
	 * {@link ExifTag#TYPE_ASCII}
	 * @since EXIF 2.3
	 */
	public static final int TAG_LENS_MODEL = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0xA434 );

	/**
	 * The SensitivityType tag indicates which one of the parameters of ISO12232 is the
	 * PhotographicSensitivity tag. Although it is an optional tag, it should be recorded
	 * when a PhotographicSensitivity tag is recorded.
	 * Value = 4, 5, 6, or 7 may be used in case that the values of plural
	 * parameters are the same.<br/>
	 * Values:
	 * <ul>
	 * <li>0: Unknown</li>
	 * <li>1: Standardoutputsensitivity(SOS)</li>
	 * <li>2: Recommended exposure index (REI)</li>
	 * <li>3: ISOspeed</li>
	 * <li>4: Standard output sensitivity (SOS) and recommended exposure index (REI)</li>
	 * <li>5: Standardoutputsensitivity(SOS)andISOspeed</li>
	 * <li>6: Recommendedexposureindex(REI)andISOspeed</li>
	 * <li>7: Standard output sensitivity (SOS) and recommended exposure index (REI) and ISO speed</li>
	 * <li>Other: Reserved</li>
	 * </ul>
	 *
	 * {@link ExifTag#TYPE_UNSIGNED_SHORT}
	 * @see it.sephiroth.android.library.exif2.ExifInterface.SensitivityType
	 * @since EXIF 2.3
	 */
	public static final int TAG_SENSITIVITY_TYPE = defineTag( IfdId.TYPE_IFD_EXIF, (short) 0x8830 );


	// IFD GPS tags
	public static final int TAG_GPS_VERSION_ID = defineTag( IfdId.TYPE_IFD_GPS, (short) 0 );

	/**
	 * Value is string(1)<br />
	 * Indicates whether the latitude is north or south latitude. The ASCII value 'N' indicates north latitude, and 'S' is south latitude.
	 */
	public static final int TAG_GPS_LATITUDE_REF = defineTag( IfdId.TYPE_IFD_GPS, (short) 1 );

	/**
	 * Value is string.<br />
	 * Indicates the latitude. The latitude is expressed as three RATIONAL values giving the degrees, minutes, and
	 * seconds, respectively. If latitude is expressed as degrees, minutes and seconds, a typical format would be
	 * dd/1,mm/1,ss/1. When degrees and minutes are used and, for example, fractions of minutes are given up to two
	 * decimal places, the format would be dd/1,mmmm/100,0/1.
	 */
	public static final int TAG_GPS_LATITUDE = defineTag( IfdId.TYPE_IFD_GPS, (short) 2 );

	/**
	 * Value is string(1)<br />
	 * Indicates whether the longitude is east or west longitude. ASCII 'E' indicates east longitude, and 'W' is west longitude.
	 */
	public static final int TAG_GPS_LONGITUDE_REF = defineTag( IfdId.TYPE_IFD_GPS, (short) 3 );

	/**
	 * Value is string.<br />
	 * Indicates the longitude. The longitude is expressed as three RATIONAL values giving the degrees, minutes, and
	 * seconds, respectively. If longitude is expressed as degrees, minutes and seconds, a typical format would be
	 * ddd/1,mm/1,ss/1. When degrees and minutes are used and, for example, fractions of minutes are given up to two
	 * decimal places, the format would be ddd/1,mmmm/100,0/1.
	 */
	public static final int TAG_GPS_LONGITUDE = defineTag( IfdId.TYPE_IFD_GPS, (short) 4 );

	/**
	 * Value is byte<br />
	 * Indicates the altitude used as the reference altitude. If the reference is sea level and the altitude is above sea level,
	 * 0 is given. If the altitude is below sea level, a value of 1 is given and the altitude is indicated as an absolute value in
	 * the GPSAltitude tag. The reference unit is meters. Note that this tag is BYTE type, unlike other reference tags
	 */
	public static final int TAG_GPS_ALTITUDE_REF = defineTag( IfdId.TYPE_IFD_GPS, (short) 5 );

	/**
	 * Value is string.<br />
	 * Indicates the altitude based on the reference in GPSAltitudeRef. Altitude is expressed as one RATIONAL value. The reference unit is meters.
	 */
	public static final int TAG_GPS_ALTITUDE = defineTag( IfdId.TYPE_IFD_GPS, (short) 6 );
	public static final int TAG_GPS_TIME_STAMP = defineTag( IfdId.TYPE_IFD_GPS, (short) 7 );
	public static final int TAG_GPS_SATTELLITES = defineTag( IfdId.TYPE_IFD_GPS, (short) 8 );
	public static final int TAG_GPS_STATUS = defineTag( IfdId.TYPE_IFD_GPS, (short) 9 );
	public static final int TAG_GPS_MEASURE_MODE = defineTag( IfdId.TYPE_IFD_GPS, (short) 10 );
	public static final int TAG_GPS_DOP = defineTag( IfdId.TYPE_IFD_GPS, (short) 11 );

	/**
	 * Value is string(1).<br />
	 * Indicates the unit used to express the GPS receiver speed of movement. 'K' 'M' and 'N' represents kilometers per  hour, miles per hour, and knots.
	 */
	public static final int TAG_GPS_SPEED_REF = defineTag( IfdId.TYPE_IFD_GPS, (short) 12 );

	/**
	 * Value is string.<br />
	 * Indicates the speed of GPS receiver movement
	 */
	public static final int TAG_GPS_SPEED = defineTag( IfdId.TYPE_IFD_GPS, (short) 13 );
	public static final int TAG_GPS_TRACK_REF = defineTag( IfdId.TYPE_IFD_GPS, (short) 14 );
	public static final int TAG_GPS_TRACK = defineTag( IfdId.TYPE_IFD_GPS, (short) 15 );
	public static final int TAG_GPS_IMG_DIRECTION_REF = defineTag( IfdId.TYPE_IFD_GPS, (short) 16 );
	public static final int TAG_GPS_IMG_DIRECTION = defineTag( IfdId.TYPE_IFD_GPS, (short) 17 );
	public static final int TAG_GPS_MAP_DATUM = defineTag( IfdId.TYPE_IFD_GPS, (short) 18 );
	public static final int TAG_GPS_DEST_LATITUDE_REF = defineTag( IfdId.TYPE_IFD_GPS, (short) 19 );
	public static final int TAG_GPS_DEST_LATITUDE = defineTag( IfdId.TYPE_IFD_GPS, (short) 20 );
	public static final int TAG_GPS_DEST_LONGITUDE_REF = defineTag( IfdId.TYPE_IFD_GPS, (short) 21 );
	public static final int TAG_GPS_DEST_LONGITUDE = defineTag( IfdId.TYPE_IFD_GPS, (short) 22 );
	public static final int TAG_GPS_DEST_BEARING_REF = defineTag( IfdId.TYPE_IFD_GPS, (short) 23 );
	public static final int TAG_GPS_DEST_BEARING = defineTag( IfdId.TYPE_IFD_GPS, (short) 24 );
	public static final int TAG_GPS_DEST_DISTANCE_REF = defineTag( IfdId.TYPE_IFD_GPS, (short) 25 );
	public static final int TAG_GPS_DEST_DISTANCE = defineTag( IfdId.TYPE_IFD_GPS, (short) 26 );
	public static final int TAG_GPS_PROCESSING_METHOD = defineTag( IfdId.TYPE_IFD_GPS, (short) 27 );
	public static final int TAG_GPS_AREA_INFORMATION = defineTag( IfdId.TYPE_IFD_GPS, (short) 28 );
	public static final int TAG_GPS_DATE_STAMP = defineTag( IfdId.TYPE_IFD_GPS, (short) 29 );
	public static final int TAG_GPS_DIFFERENTIAL = defineTag( IfdId.TYPE_IFD_GPS, (short) 30 );
	// IFD Interoperability tags
	public static final int TAG_INTEROPERABILITY_INDEX = defineTag( IfdId.TYPE_IFD_INTEROPERABILITY, (short) 1 );




	public static final ByteOrder DEFAULT_BYTE_ORDER = ByteOrder.BIG_ENDIAN;
	private ExifData mData = new ExifData( DEFAULT_BYTE_ORDER );
	private static final String NULL_ARGUMENT_STRING = "Argument is null";

	private static final String GPS_DATE_FORMAT_STR = "yyyy:MM:dd";
	private static final DateFormat mGPSDateStampFormat = new SimpleDateFormat( GPS_DATE_FORMAT_STR );
	private static final String DATETIME_FORMAT_STR = "yyyy:MM:dd kk:mm:ss";
	private static final DateFormat mDateTimeStampFormat = new SimpleDateFormat( DATETIME_FORMAT_STR );

	/**
	 * Tags that contain offset markers. These are included in the banned
	 * defines.
	 */
	private static HashSet<Short> sOffsetTags = new HashSet<Short>();

	static {
		sOffsetTags.add( getTrueTagKey( TAG_GPS_IFD ) );
		sOffsetTags.add( getTrueTagKey( TAG_EXIF_IFD ) );
		sOffsetTags.add( getTrueTagKey( TAG_JPEG_INTERCHANGE_FORMAT ) );
		sOffsetTags.add( getTrueTagKey( TAG_INTEROPERABILITY_IFD ) );
		sOffsetTags.add( getTrueTagKey( TAG_STRIP_OFFSETS ) );
	}

	/**
	 * Tags with definitions that cannot be overridden (banned defines).
	 */
	protected static HashSet<Short> sBannedDefines = new HashSet<Short>( sOffsetTags );

	static {
		sBannedDefines.add( getTrueTagKey( TAG_NULL ) );
		sBannedDefines.add( getTrueTagKey( TAG_JPEG_INTERCHANGE_FORMAT_LENGTH ) );
		sBannedDefines.add( getTrueTagKey( TAG_STRIP_BYTE_COUNTS ) );
	}

	private final Calendar mGPSTimeStampCalendar = Calendar.getInstance( TimeZone.getTimeZone( "UTC" ) );
	private SparseIntArray mTagInfo = null;

	public ExifInterface() {
		mGPSDateStampFormat.setTimeZone( TimeZone.getTimeZone( "UTC" ) );
	}

	/**
	 * Returns true if tag TID is one of the following: {@link #TAG_EXIF_IFD},
	 * {@link #TAG_GPS_IFD}, {@link #TAG_JPEG_INTERCHANGE_FORMAT},
	 * {@link #TAG_STRIP_OFFSETS}, {@link #TAG_INTEROPERABILITY_IFD}
	 * <p/>
	 * Note: defining tags with these TID's is disallowed.
	 *
	 * @param tag a tag's TID (can be obtained from a defined tag constant with
	 *            {@link #getTrueTagKey}).
	 * @return true if the TID is that of an offset tag.
	 */
	protected static boolean isOffsetTag( short tag ) {
		return sOffsetTags.contains( tag );
	}

	/**
	 * Returns the Orientation ExifTag value for a given number of degrees.
	 *
	 * @param degrees the amount an image is rotated in degrees.
	 */
	public static short getOrientationValueForRotation( int degrees ) {
		degrees %= 360;
		if( degrees < 0 ) {
			degrees += 360;
		}
		if( degrees < 90 ) {
			return Orientation.TOP_LEFT; // 0 degrees
		}
		else if( degrees < 180 ) {
			return Orientation.RIGHT_TOP; // 90 degrees cw
		}
		else if( degrees < 270 ) {
			return Orientation.BOTTOM_LEFT; // 180 degrees
		}
		else {
			return Orientation.RIGHT_BOTTOM; // 270 degrees cw
		}
	}

	/**
	 * Returns the rotation degrees corresponding to an ExifTag Orientation
	 * value.
	 *
	 * @param orientation the ExifTag Orientation value.
	 */
	@SuppressWarnings( "unused" )
	public static int getRotationForOrientationValue( short orientation ) {
		switch( orientation ) {
			case Orientation.TOP_LEFT:
				return 0;
			case Orientation.RIGHT_TOP:
				return 90;
			case Orientation.BOTTOM_LEFT:
				return 180;
			case Orientation.RIGHT_BOTTOM:
				return 270;
			default:
				return 0;
		}
	}

	/**
	 * Given the value from {@link #TAG_FOCAL_PLANE_RESOLUTION_UNIT} or {@link #TAG_RESOLUTION_UNIT}
	 * this method will return the corresponding value in millimeters
	 *
	 * @param resolution {@link #TAG_FOCAL_PLANE_RESOLUTION_UNIT} or {@link #TAG_RESOLUTION_UNIT}
	 * @return resolution in millimeters
	 */
	@SuppressWarnings( "unused" )
	public double getResolutionUnit( int resolution ) {
		switch( resolution ) {
			case 1:
			case ResolutionUnit.INCHES:
				return 25.4;

			case ResolutionUnit.CENTIMETERS:
				return 10;

			case ResolutionUnit.MILLIMETERS:
				return 1;

			case ResolutionUnit.MICROMETERS:
				return .001;

			default:
				return 25.4;
		}
	}

	/**
	 * Gets the double representation of the GPS latitude or longitude
	 * coordinate.
	 *
	 * @param coordinate an array of 3 Rationals representing the degrees,
	 *                   minutes, and seconds of the GPS location as defined in the
	 *                   exif specification.
	 * @param reference  a GPS reference reperesented by a String containing "N",
	 *                   "S", "E", or "W".
	 * @return the GPS coordinate represented as degrees + minutes/60 +
	 * seconds/3600
	 */
	public static double convertLatOrLongToDouble( Rational[] coordinate, String reference ) {
		try {
			double degrees = coordinate[0].toDouble();
			double minutes = coordinate[1].toDouble();
			double seconds = coordinate[2].toDouble();
			double result = degrees + minutes / 60.0 + seconds / 3600.0;
			if( ( reference.startsWith( "S" ) || reference.startsWith( "W" ) ) ) {
				return - result;
			}
			return result;
		} catch( ArrayIndexOutOfBoundsException e ) {
			throw new IllegalArgumentException();
		}
	}

	protected static int[] getAllowedIfdsFromInfo( int info ) {
		int ifdFlags = getAllowedIfdFlagsFromInfo( info );
		int[] ifds = IfdData.getIfds();
		ArrayList<Integer> l = new ArrayList<Integer>();
		for( int i = 0; i < IfdId.TYPE_IFD_COUNT; i++ ) {
			int flag = ( ifdFlags >> i ) & 1;
			if( flag == 1 ) {
				l.add( ifds[i] );
			}
		}
		if( l.size() <= 0 ) {
			return null;
		}
		int[] ret = new int[l.size()];
		int j = 0;
		for( int i : l ) {
			ret[j++] = i;
		}
		return ret;
	}

	/**
	 * Reads the exif tags from a file, clearing this ExifInterface object's
	 * existing exif tags.
	 *
	 * @param inFileName a string representing the filepath to jpeg file.
	 * @param options bit flag which defines which type of tags to process, see {@link it.sephiroth.android.library.exif2.ExifInterface.Options}
	 * @see #readExif(java.io.InputStream, int)
	 * @throws java.io.IOException
	 */
	@SuppressWarnings( "unused" )
	public void readExif( String inFileName, int options ) throws IOException {
		if( inFileName == null ) {
			throw new IllegalArgumentException( NULL_ARGUMENT_STRING );
		}
		InputStream is = null;
		try {
			is = new BufferedInputStream( new FileInputStream( inFileName ) );
			readExif( is, options );
		} catch( IOException e ) {
			closeSilently( is );
			throw e;
		}
		is.close();
	}

	/**
	 * Reads the exif tags from an InputStream, clearing this ExifInterface
	 * object's existing exif tags.
	 * <pre>
	 *     ExifInterface exif = new ExifInterface();
	 *     exif.readExif( stream, Options.OPTION_IFD_0 | Options.OPTION_IFD_1 | Options.OPTION_IFD_EXIF );
	 *     ...
	 *     // to request all the options use the OPTION_ALL bit mask
	 *     exif.readExif( stream, Options.OPTION_ALL );
	 * </pre>
	 *
	 * @param inStream an InputStream containing a jpeg compressed image.
	 * @param options bit flag which defines which type of tags to process, see {@link it.sephiroth.android.library.exif2.ExifInterface.Options}
	 * @throws java.io.IOException
	 */
	@SuppressWarnings( "unused" )
	public void readExif( InputStream inStream, int options ) throws IOException {
		if( inStream == null ) {
			throw new IllegalArgumentException( NULL_ARGUMENT_STRING );
		}
		ExifData d;
		try {
			d = new ExifReader( this ).read( inStream, options );
		} catch( ExifInvalidFormatException e ) {
			throw new IOException( "Invalid exif format : " + e );
		}
		mData = d;
	}

	protected static void closeSilently( Closeable c ) {
		if( c != null ) {
			try {
				c.close();
			} catch( Throwable e ) {
				// ignored
			}
		}
	}

	/**
	 * Sets the exif tags, clearing this ExifInterface object's existing exif
	 * tags.
	 *
	 * @param tags a collection of exif tags to set.
	 */
	public void setExif( Collection<ExifTag> tags ) {
		clearExif();
		setTags( tags );
	}

	/**
	 * Clears this ExifInterface object's existing exif tags.
	 */
	public void clearExif() {
		mData = new ExifData( DEFAULT_BYTE_ORDER );
	}

	/**
	 * Puts a collection of ExifTags into this ExifInterface objects's tags. Any
	 * previous ExifTags with the same TID and IFDs will be removed.
	 *
	 * @param tags a Collection of ExifTags.
	 * @see #setTag
	 */
	public void setTags( Collection<ExifTag> tags ) {
		if( null == tags ) return;
		for( ExifTag t : tags ) {
			setTag( t );
		}
	}

	/**
	 * Puts an ExifTag into this ExifInterface object's tags, removing a
	 * previous ExifTag with the same TID and IFD. The IFD it is put into will
	 * be the one the tag was created with in {@link #buildTag}.
	 *
	 * @param tag an ExifTag to put into this ExifInterface's tags.
	 * @return the previous ExifTag with the same TID and IFD or null if none
	 * exists.
	 */
	public ExifTag setTag( ExifTag tag ) {
		return mData.addTag( tag );
	}

	@SuppressWarnings( "unused" )
	public void writeExif( final String dstFilename ) throws IOException {
		Log.i( TAG, "writeExif: " + dstFilename );

		// create a backup file
		File dst_file = new File( dstFilename );
		File bak_file = new File( dstFilename + ".t" );

		// try to delete old copy of backup
		// Log.d( TAG, "delete old backup file" );
		bak_file.delete();

		// rename dst file into backup file
		// Log.d( TAG, "rename dst into bak" )
		// if( ! dst_file.renameTo( bak_file ) ) return;

		try {
			// Log.d( TAG, "try to write into dst" );
			// writeExif( bak_file.getAbsolutePath(), dst_file.getAbsolutePath() );

			// Trying to write into bak_file using dst_file as source
			writeExif( dst_file.getAbsolutePath(), bak_file.getAbsolutePath() );

			// Now switch bak into dst
			// Log.d( TAG, "rename the bak into dst" );
			bak_file.renameTo( dst_file );
		} catch( IOException e ) {
			throw e;
		} finally {
			// deleting backup file
			bak_file.delete();
		}
	}

	@SuppressWarnings( "unused" )
	public void writeExif( final String srcFilename, final String dstFilename ) throws IOException {
		Log.i( TAG, "writeExif: " + dstFilename );

		// src and dst cannot be the same
		if( srcFilename.equals( dstFilename ) ) return;

		// srcFilename is used *ONLY* to read the image uncompressed data
		// exif tags are not used here

		// 3. rename dst file into backup file
		FileInputStream input = new FileInputStream( srcFilename );
		FileOutputStream output = new FileOutputStream( dstFilename );

		int position = writeExif_internal( input, output, mData );

		// 7. write the rest of the image..
		FileChannel in_channel = input.getChannel();
		FileChannel out_channel = output.getChannel();
		in_channel.transferTo( position, in_channel.size() - position, out_channel );
		output.flush();

		IOUtils.closeQuietly( input );
		IOUtils.closeQuietly( output );
	}


	public void writeExif( final InputStream input, final String dstFilename ) throws IOException {
		Log.i( TAG, "writeExif: " + dstFilename );

		// inpur is used *ONLY* to read the image uncompressed data
		// exif tags are not used here

		FileOutputStream output = new FileOutputStream( dstFilename );
		writeExif_internal( input, output, mData );

		// 7. write the rest of the image..
		IOUtils.copy( input, output );

		output.flush();
		output.close();
	}

	@SuppressWarnings( "unused" )
	public void writeExif( final Bitmap input, final String dstFilename, int quality ) throws IOException {
		Log.i( TAG, "writeExif: " + dstFilename );

		// inpur is used *ONLY* to read the image uncompressed data
		// exif tags are not used here

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		input.compress( Bitmap.CompressFormat.JPEG, quality, out );

		ByteArrayInputStream in = new ByteArrayInputStream( out.toByteArray() );
		out.close();

		writeExif( in, dstFilename );
	}

	private static int writeExif_internal( final InputStream input, final OutputStream output, ExifData exifData ) throws IOException {
		// Log.i( TAG, "writeExif_internal" );

		// 1. read the output file first
		ExifInterface src_exif = new ExifInterface();
		src_exif.readExif( input, 0 );

		// 4. Create the destination outputstream
		// 5. write headers
		output.write( 0xFF );
		output.write( JpegHeader.TAG_SOI );

		final List<ExifParser.Section> sections = src_exif.mData.getSections();

		// 6. write all the sections from the srcFilename
		if( sections.get( 0 ).type != JpegHeader.TAG_M_JFIF ) {
			Log.w( TAG, "first section is not a JFIF or EXIF tag" );
			output.write( JpegHeader.JFIF_HEADER );
		}

		// 6.1 write the *new* EXIF tag
		ExifOutputStream eo = new ExifOutputStream( src_exif );
		eo.setExifData( exifData );
		eo.writeExifData( output );

		// 6.2 write all the sections except for the SOS ( start of scan )
		for( int a = 0; a < sections.size() - 1; a++ ) {
			ExifParser.Section current = sections.get( a );
			// Log.v( TAG, "writing section.. " + String.format( "0x%2X", current.type ) );
			output.write( 0xFF );
			output.write( current.type );
			output.write( current.data );
		}

		// 6.3 write the last SOS marker
		ExifParser.Section current = sections.get( sections.size() - 1 );
		// Log.v( TAG, "writing last section.. " + String.format( "0x%2X", current.type ) );
		output.write( 0xFF );
		output.write( current.type );
		output.write( current.data );

		// return the position where the input stream should be copied
		return src_exif.mData.mUncompressedDataPosition;
	}


	/**
	 * Get the exif tags in this ExifInterface object or null if none exist.
	 *
	 * @return a List of {@link ExifTag}s.
	 */
	public List<ExifTag> getAllTags() {
		return mData.getAllTags();
	}

	/**
	 * Reads the exif tags from a byte array, clearing this ExifInterface
	 * object's existing exif tags.
	 *
	 * @param jpeg a byte array containing a jpeg compressed image.
	 * @param options bit flag which defines which type of tags to process, see {@link it.sephiroth.android.library.exif2.ExifInterface.Options}
	 * @throws java.io.IOException
	 * @see #readExif(java.io.InputStream, int)
	 */
	@SuppressWarnings( "unused" )
	public void readExif( byte[] jpeg, int options ) throws IOException {
		readExif( new ByteArrayInputStream( jpeg ), options );
	}

	/**
	 * Returns a list of ExifTags that share a TID (which can be obtained by
	 * calling {@link #getTrueTagKey} on a defined tag constant) or null if none
	 * exist.
	 *
	 * @param tagId a TID as defined in the exif standard (or with
	 *              {@link #defineTag}).
	 * @return a List of {@link ExifTag}s.
	 */
	@SuppressWarnings( "unused" )
	public List<ExifTag> getTagsForTagId( short tagId ) {
		return mData.getAllTagsForTagId( tagId );
	}

	/**
	 * Returns a list of ExifTags that share an IFD (which can be obtained by
	 * calling {@link #getTrueIfd(int)} on a defined tag constant) or null if none
	 * exist.
	 *
	 * @param ifdId an IFD as defined in the exif standard (or with
	 *              {@link #defineTag}).
	 * @return a List of {@link ExifTag}s.
	 */
	@SuppressWarnings( "unused" )
	public List<ExifTag> getTagsForIfdId( int ifdId ) {
		return mData.getAllTagsForIfd( ifdId );
	}

	/**
	 * Returns the ExifTag in that tag's default IFD for a defined tag constant
	 * or null if none exists.
	 *
	 * @param tagId a defined tag constant, e.g. {@link #TAG_IMAGE_WIDTH}.
	 * @return an {@link ExifTag} or null if none exists.
	 */
	public ExifTag getTag( int tagId ) {
		int ifdId = getDefinedTagDefaultIfd( tagId );
		return getTag( tagId, ifdId );
	}

	/**
	 * Gets the default IFD for a tag.
	 *
	 * @param tagId a defined tag constant, e.g. {@link #TAG_IMAGE_WIDTH}.
	 * @return the default IFD for a tag definition or {@link #IFD_NULL} if no
	 * definition exists.
	 */
	public int getDefinedTagDefaultIfd( int tagId ) {
		int info = getTagInfo().get( tagId );
		if( info == DEFINITION_NULL ) {
			return IFD_NULL;
		}
		return getTrueIfd( tagId );
	}

	/**
	 * Gets an ExifTag for an IFD other than the tag's default.
	 *
	 * @see #getTag
	 */
	public ExifTag getTag( int tagId, int ifdId ) {
		if( ! ExifTag.isValidIfd( ifdId ) ) {
			return null;
		}
		return mData.getTag( getTrueTagKey( tagId ), ifdId );
	}

	protected SparseIntArray getTagInfo() {
		if( mTagInfo == null ) {
			mTagInfo = new SparseIntArray();
			initTagInfo();
		}
		return mTagInfo;
	}

	/**
	 * Returns the default IFD for a tag constant.
	 */
	public static int getTrueIfd( int tag ) {
		return tag >>> 16;
	}

	/**
	 * Returns the TID for a tag constant.
	 */
	public static short getTrueTagKey( int tag ) {
		// Truncate
		return (short) tag;
	}

	private void initTagInfo() {
		/**
		 * We put tag information in a 4-bytes integer. The first byte a bitmask
		 * representing the allowed IFDs of the tag, the second byte is the data
		 * type, and the last two byte are a short value indicating the default
		 * component count of this tag.
		 */
		// IFD0 tags
		int[] ifdAllowedIfds = { IfdId.TYPE_IFD_0, IfdId.TYPE_IFD_1 };
		int ifdFlags = getFlagsFromAllowedIfds( ifdAllowedIfds ) << 24;
		mTagInfo.put( ExifInterface.TAG_MAKE, ifdFlags | ExifTag.TYPE_ASCII << 16 );
		mTagInfo.put( ExifInterface.TAG_IMAGE_WIDTH, ifdFlags | ExifTag.TYPE_UNSIGNED_LONG << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_IMAGE_LENGTH, ifdFlags | ExifTag.TYPE_UNSIGNED_LONG << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_BITS_PER_SAMPLE, ifdFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 3 );
		mTagInfo.put( ExifInterface.TAG_COMPRESSION, ifdFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION, ifdFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_ORIENTATION, ifdFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_SAMPLES_PER_PIXEL, ifdFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_PLANAR_CONFIGURATION, ifdFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_Y_CB_CR_SUB_SAMPLING, ifdFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 2 );
		mTagInfo.put( ExifInterface.TAG_Y_CB_CR_POSITIONING, ifdFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_X_RESOLUTION, ifdFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_Y_RESOLUTION, ifdFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_RESOLUTION_UNIT, ifdFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_STRIP_OFFSETS, ifdFlags | ExifTag.TYPE_UNSIGNED_LONG << 16 );
		mTagInfo.put( ExifInterface.TAG_ROWS_PER_STRIP, ifdFlags | ExifTag.TYPE_UNSIGNED_LONG << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_STRIP_BYTE_COUNTS, ifdFlags | ExifTag.TYPE_UNSIGNED_LONG << 16 );
		mTagInfo.put( ExifInterface.TAG_TRANSFER_FUNCTION, ifdFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 3 * 256 );
		mTagInfo.put( ExifInterface.TAG_WHITE_POINT, ifdFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 2 );
		mTagInfo.put( ExifInterface.TAG_PRIMARY_CHROMATICITIES, ifdFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 6 );
		mTagInfo.put( ExifInterface.TAG_Y_CB_CR_COEFFICIENTS, ifdFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 3 );
		mTagInfo.put( ExifInterface.TAG_REFERENCE_BLACK_WHITE, ifdFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 6 );
		mTagInfo.put( ExifInterface.TAG_DATE_TIME, ifdFlags | ExifTag.TYPE_ASCII << 16 | 20 );
		mTagInfo.put( ExifInterface.TAG_IMAGE_DESCRIPTION, ifdFlags | ExifTag.TYPE_ASCII << 16 );
		mTagInfo.put( ExifInterface.TAG_MODEL, ifdFlags | ExifTag.TYPE_ASCII << 16 );
		mTagInfo.put( ExifInterface.TAG_SOFTWARE, ifdFlags | ExifTag.TYPE_ASCII << 16 );
		mTagInfo.put( ExifInterface.TAG_ARTIST, ifdFlags | ExifTag.TYPE_ASCII << 16 );
		mTagInfo.put( ExifInterface.TAG_COPYRIGHT, ifdFlags | ExifTag.TYPE_ASCII << 16 );
		mTagInfo.put( ExifInterface.TAG_EXIF_IFD, ifdFlags | ExifTag.TYPE_UNSIGNED_LONG << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_GPS_IFD, ifdFlags | ExifTag.TYPE_UNSIGNED_LONG << 16 | 1 );
		// IFD1 tags
		int[] ifd1AllowedIfds = { IfdId.TYPE_IFD_1 };
		int ifdFlags1 = getFlagsFromAllowedIfds( ifd1AllowedIfds ) << 24;
		mTagInfo.put( ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT, ifdFlags1 | ExifTag.TYPE_UNSIGNED_LONG << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH, ifdFlags1 | ExifTag.TYPE_UNSIGNED_LONG << 16 | 1 );
		// Exif tags
		int[] exifAllowedIfds = { IfdId.TYPE_IFD_EXIF };
		int exifFlags = getFlagsFromAllowedIfds( exifAllowedIfds ) << 24;
		mTagInfo.put( ExifInterface.TAG_EXIF_VERSION, exifFlags | ExifTag.TYPE_UNDEFINED << 16 | 4 );
		mTagInfo.put( ExifInterface.TAG_FLASHPIX_VERSION, exifFlags | ExifTag.TYPE_UNDEFINED << 16 | 4 );
		mTagInfo.put( ExifInterface.TAG_COLOR_SPACE, exifFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_COMPONENTS_CONFIGURATION, exifFlags | ExifTag.TYPE_UNDEFINED << 16 | 4 );
		mTagInfo.put( ExifInterface.TAG_COMPRESSED_BITS_PER_PIXEL, exifFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_PIXEL_X_DIMENSION, exifFlags | ExifTag.TYPE_UNSIGNED_LONG << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_PIXEL_Y_DIMENSION, exifFlags | ExifTag.TYPE_UNSIGNED_LONG << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_MAKER_NOTE, exifFlags | ExifTag.TYPE_UNDEFINED << 16 );
		mTagInfo.put( ExifInterface.TAG_USER_COMMENT, exifFlags | ExifTag.TYPE_UNDEFINED << 16 );
		mTagInfo.put( ExifInterface.TAG_RELATED_SOUND_FILE, exifFlags | ExifTag.TYPE_ASCII << 16 | 13 );
		mTagInfo.put( ExifInterface.TAG_DATE_TIME_ORIGINAL, exifFlags | ExifTag.TYPE_ASCII << 16 | 20 );
		mTagInfo.put( ExifInterface.TAG_DATE_TIME_DIGITIZED, exifFlags | ExifTag.TYPE_ASCII << 16 | 20 );
		mTagInfo.put( ExifInterface.TAG_SUB_SEC_TIME, exifFlags | ExifTag.TYPE_ASCII << 16 );
		mTagInfo.put( ExifInterface.TAG_SUB_SEC_TIME_ORIGINAL, exifFlags | ExifTag.TYPE_ASCII << 16 );
		mTagInfo.put( ExifInterface.TAG_SUB_SEC_TIME_DIGITIZED, exifFlags | ExifTag.TYPE_ASCII << 16 );
		mTagInfo.put( ExifInterface.TAG_IMAGE_UNIQUE_ID, exifFlags | ExifTag.TYPE_ASCII << 16 | 33 );
		mTagInfo.put( ExifInterface.TAG_LENS_SPECS, exifFlags | ExifTag.TYPE_RATIONAL << 16 | 4 );
		mTagInfo.put( ExifInterface.TAG_LENS_MAKE, exifFlags | ExifTag.TYPE_ASCII << 16 );
		mTagInfo.put( ExifInterface.TAG_LENS_MODEL, exifFlags | ExifTag.TYPE_ASCII << 16 );
		mTagInfo.put( ExifInterface.TAG_SENSITIVITY_TYPE, exifFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_EXPOSURE_TIME, exifFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_F_NUMBER, exifFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_EXPOSURE_PROGRAM, exifFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_SPECTRAL_SENSITIVITY, exifFlags | ExifTag.TYPE_ASCII << 16 );
		mTagInfo.put( ExifInterface.TAG_ISO_SPEED_RATINGS, exifFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 );
		mTagInfo.put( ExifInterface.TAG_OECF, exifFlags | ExifTag.TYPE_UNDEFINED << 16 );
		mTagInfo.put( ExifInterface.TAG_SHUTTER_SPEED_VALUE, exifFlags | ExifTag.TYPE_RATIONAL << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_APERTURE_VALUE, exifFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_BRIGHTNESS_VALUE, exifFlags | ExifTag.TYPE_RATIONAL << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_EXPOSURE_BIAS_VALUE, exifFlags | ExifTag.TYPE_RATIONAL << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_MAX_APERTURE_VALUE, exifFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_SUBJECT_DISTANCE, exifFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_METERING_MODE, exifFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_LIGHT_SOURCE, exifFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_FLASH, exifFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_FOCAL_LENGTH, exifFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_SUBJECT_AREA, exifFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 );
		mTagInfo.put( ExifInterface.TAG_FLASH_ENERGY, exifFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_SPATIAL_FREQUENCY_RESPONSE, exifFlags | ExifTag.TYPE_UNDEFINED << 16 );
		mTagInfo.put( ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION, exifFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_FOCAL_PLANE_Y_RESOLUTION, exifFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_FOCAL_PLANE_RESOLUTION_UNIT, exifFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_SUBJECT_LOCATION, exifFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 2 );
		mTagInfo.put( ExifInterface.TAG_EXPOSURE_INDEX, exifFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_SENSING_METHOD, exifFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_FILE_SOURCE, exifFlags | ExifTag.TYPE_UNDEFINED << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_SCENE_TYPE, exifFlags | ExifTag.TYPE_UNDEFINED << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_CFA_PATTERN, exifFlags | ExifTag.TYPE_UNDEFINED << 16 );
		mTagInfo.put( ExifInterface.TAG_CUSTOM_RENDERED, exifFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_EXPOSURE_MODE, exifFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_WHITE_BALANCE, exifFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_DIGITAL_ZOOM_RATIO, exifFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_FOCAL_LENGTH_IN_35_MM_FILE, exifFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_SCENE_CAPTURE_TYPE, exifFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_GAIN_CONTROL, exifFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_CONTRAST, exifFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_SATURATION, exifFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_SHARPNESS, exifFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION, exifFlags | ExifTag.TYPE_UNDEFINED << 16 );
		mTagInfo.put( ExifInterface.TAG_SUBJECT_DISTANCE_RANGE, exifFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_INTEROPERABILITY_IFD, exifFlags | ExifTag.TYPE_UNSIGNED_LONG << 16 | 1 );
		// GPS tag
		int[] gpsAllowedIfds = { IfdId.TYPE_IFD_GPS };
		int gpsFlags = getFlagsFromAllowedIfds( gpsAllowedIfds ) << 24;
		mTagInfo.put( ExifInterface.TAG_GPS_VERSION_ID, gpsFlags | ExifTag.TYPE_UNSIGNED_BYTE << 16 | 4 );
		mTagInfo.put( ExifInterface.TAG_GPS_LATITUDE_REF, gpsFlags | ExifTag.TYPE_ASCII << 16 | 2 );
		mTagInfo.put( ExifInterface.TAG_GPS_LONGITUDE_REF, gpsFlags | ExifTag.TYPE_ASCII << 16 | 2 );
		mTagInfo.put( ExifInterface.TAG_GPS_LATITUDE, gpsFlags | ExifTag.TYPE_RATIONAL << 16 | 3 );
		mTagInfo.put( ExifInterface.TAG_GPS_LONGITUDE, gpsFlags | ExifTag.TYPE_RATIONAL << 16 | 3 );
		mTagInfo.put( ExifInterface.TAG_GPS_ALTITUDE_REF, gpsFlags | ExifTag.TYPE_UNSIGNED_BYTE << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_GPS_ALTITUDE, gpsFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_GPS_TIME_STAMP, gpsFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 3 );
		mTagInfo.put( ExifInterface.TAG_GPS_SATTELLITES, gpsFlags | ExifTag.TYPE_ASCII << 16 );
		mTagInfo.put( ExifInterface.TAG_GPS_STATUS, gpsFlags | ExifTag.TYPE_ASCII << 16 | 2 );
		mTagInfo.put( ExifInterface.TAG_GPS_MEASURE_MODE, gpsFlags | ExifTag.TYPE_ASCII << 16 | 2 );
		mTagInfo.put( ExifInterface.TAG_GPS_DOP, gpsFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_GPS_SPEED_REF, gpsFlags | ExifTag.TYPE_ASCII << 16 | 2 );
		mTagInfo.put( ExifInterface.TAG_GPS_SPEED, gpsFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_GPS_TRACK_REF, gpsFlags | ExifTag.TYPE_ASCII << 16 | 2 );
		mTagInfo.put( ExifInterface.TAG_GPS_TRACK, gpsFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_GPS_IMG_DIRECTION_REF, gpsFlags | ExifTag.TYPE_ASCII << 16 | 2 );
		mTagInfo.put( ExifInterface.TAG_GPS_IMG_DIRECTION, gpsFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_GPS_MAP_DATUM, gpsFlags | ExifTag.TYPE_ASCII << 16 );
		mTagInfo.put( ExifInterface.TAG_GPS_DEST_LATITUDE_REF, gpsFlags | ExifTag.TYPE_ASCII << 16 | 2 );
		mTagInfo.put( ExifInterface.TAG_GPS_DEST_LATITUDE, gpsFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_GPS_DEST_BEARING_REF, gpsFlags | ExifTag.TYPE_ASCII << 16 | 2 );
		mTagInfo.put( ExifInterface.TAG_GPS_DEST_BEARING, gpsFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_GPS_DEST_DISTANCE_REF, gpsFlags | ExifTag.TYPE_ASCII << 16 | 2 );
		mTagInfo.put( ExifInterface.TAG_GPS_DEST_DISTANCE, gpsFlags | ExifTag.TYPE_UNSIGNED_RATIONAL << 16 | 1 );
		mTagInfo.put( ExifInterface.TAG_GPS_PROCESSING_METHOD, gpsFlags | ExifTag.TYPE_UNDEFINED << 16 );
		mTagInfo.put( ExifInterface.TAG_GPS_AREA_INFORMATION, gpsFlags | ExifTag.TYPE_UNDEFINED << 16 );
		mTagInfo.put( ExifInterface.TAG_GPS_DATE_STAMP, gpsFlags | ExifTag.TYPE_ASCII << 16 | 11 );
		mTagInfo.put( ExifInterface.TAG_GPS_DIFFERENTIAL, gpsFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 11 );
		// Interoperability tag
		int[] interopAllowedIfds = { IfdId.TYPE_IFD_INTEROPERABILITY };
		int interopFlags = getFlagsFromAllowedIfds( interopAllowedIfds ) << 24;
		mTagInfo.put( TAG_INTEROPERABILITY_INDEX, interopFlags | ExifTag.TYPE_ASCII << 16 );
		mTagInfo.put( TAG_INTEROP_VERSION, interopFlags | ExifTag.TYPE_UNDEFINED << 16 | 4 );
	}

	protected static int getFlagsFromAllowedIfds( int[] allowedIfds ) {
		if( allowedIfds == null || allowedIfds.length == 0 ) {
			return 0;
		}
		int flags = 0;
		int[] ifds = IfdData.getIfds();
		for( int i = 0; i < IfdId.TYPE_IFD_COUNT; i++ ) {
			for( int j : allowedIfds ) {
				if( ifds[i] == j ) {
					flags |= 1 << i;
					break;
				}
			}
		}
		return flags;
	}

	/**
	 * Returns the value of the ExifTag in that tag's default IFD for a defined
	 * tag constant or null if none exists or the value could not be cast into
	 * the return type.
	 *
	 * @param tagId a defined tag constant, e.g. {@link #TAG_IMAGE_WIDTH}.
	 * @return the value of the ExifTag or null if none exists.
	 */
	@SuppressWarnings( "unused" )
	public Object getTagValue( int tagId ) {
		int ifdId = getDefinedTagDefaultIfd( tagId );
		return getTagValue( tagId, ifdId );
	}

	/**
	 * Gets a tag value for an IFD other than the tag's default.
	 *
	 * @see #getTagValue
	 */
	public Object getTagValue( int tagId, int ifdId ) {
		ExifTag t = getTag( tagId, ifdId );
		return ( t == null ) ? null : t.getValue();
	}

	/**
	 * @see #getTagValue
	 */
	public String getTagStringValue( int tagId, int ifdId ) {
		ExifTag t = getTag( tagId, ifdId );
		if( t == null ) {
			return null;
		}
		return t.getValueAsString();
	}

	/**
	 * @see #getTagValue
	 */
	public String getTagStringValue( int tagId ) {
		int ifdId = getDefinedTagDefaultIfd( tagId );
		return getTagStringValue( tagId, ifdId );
	}

	/**
	 * @see #getTagValue
	 */
	@SuppressWarnings( "unused" )
	public Long getTagLongValue( int tagId ) {
		int ifdId = getDefinedTagDefaultIfd( tagId );
		return getTagLongValue( tagId, ifdId );
	}

	/**
	 * @see #getTagValue
	 */
	public Long getTagLongValue( int tagId, int ifdId ) {
		long[] l = getTagLongValues( tagId, ifdId );
		if( l == null || l.length <= 0 ) {
			return null;
		}
		return new Long( l[0] );
	}

	/**
	 * @see #getTagValue
	 */
	public long[] getTagLongValues( int tagId, int ifdId ) {
		ExifTag t = getTag( tagId, ifdId );
		if( t == null ) {
			return null;
		}
		return t.getValueAsLongs();
	}

	/**
	 * @see #getTagValue
	 */
	public Integer getTagIntValue( int tagId ) {
		int ifdId = getDefinedTagDefaultIfd( tagId );
		return getTagIntValue( tagId, ifdId );
	}

	/**
	 * @see #getTagValue
	 */
	public Integer getTagIntValue( int tagId, int ifdId ) {
		int[] l = getTagIntValues( tagId, ifdId );
		if( l == null || l.length <= 0 ) {
			return null;
		}
		return new Integer( l[0] );
	}

	/**
	 * @see #getTagValue
	 */
	public int[] getTagIntValues( int tagId, int ifdId ) {
		ExifTag t = getTag( tagId, ifdId );
		if( t == null ) {
			return null;
		}
		return t.getValueAsInts();
	}

	/**
	 * @see #getTagValue
	 */
	public Byte getTagByteValue( int tagId ) {
		int ifdId = getDefinedTagDefaultIfd( tagId );
		return getTagByteValue( tagId, ifdId );
	}

	/**
	 * @see #getTagValue
	 */
	public Byte getTagByteValue( int tagId, int ifdId ) {
		byte[] l = getTagByteValues( tagId, ifdId );
		if( l == null || l.length <= 0 ) {
			return null;
		}
		return new Byte( l[0] );
	}

	/**
	 * @see #getTagValue
	 */
	public byte[] getTagByteValues( int tagId, int ifdId ) {
		ExifTag t = getTag( tagId, ifdId );
		if( t == null ) {
			return null;
		}
		return t.getValueAsBytes();
	}

	/**
	 * @see #getTagValue
	 */
	public Rational getTagRationalValue( int tagId ) {
		int ifdId = getDefinedTagDefaultIfd( tagId );
		return getTagRationalValue( tagId, ifdId );
	}

	/**
	 * @see #getTagValue
	 */
	public Rational getTagRationalValue( int tagId, int ifdId ) {
		Rational[] l = getTagRationalValues( tagId, ifdId );
		if( l == null || l.length == 0 ) {
			return null;
		}
		return new Rational( l[0] );
	}

    /*
     * Getter methods that are similar to getTagValue. Null is returned if the
     * tag value cannot be cast into the return type.
     */

	/**
	 * @see #getTagValue
	 */
	public Rational[] getTagRationalValues( int tagId, int ifdId ) {
		ExifTag t = getTag( tagId, ifdId );
		if( t == null ) {
			return null;
		}
		return t.getValueAsRationals();
	}

	/**
	 * @see #getTagValue
	 */
	@SuppressWarnings( "unused" )
	public long[] getTagLongValues( int tagId ) {
		int ifdId = getDefinedTagDefaultIfd( tagId );
		return getTagLongValues( tagId, ifdId );
	}

	/**
	 * @see #getTagValue
	 */
	@SuppressWarnings( "unused" )
	public int[] getTagIntValues( int tagId ) {
		int ifdId = getDefinedTagDefaultIfd( tagId );
		return getTagIntValues( tagId, ifdId );
	}

	/**
	 * @see #getTagValue
	 */
	@SuppressWarnings( "unused" )
	public byte[] getTagByteValues( int tagId ) {
		int ifdId = getDefinedTagDefaultIfd( tagId );
		return getTagByteValues( tagId, ifdId );
	}

	/**
	 * @see #getTagValue
	 */
	public Rational[] getTagRationalValues( int tagId ) {
		int ifdId = getDefinedTagDefaultIfd( tagId );
		return getTagRationalValues( tagId, ifdId );
	}

	/**
	 * Checks whether a tag has a defined number of elements.
	 *
	 * @param tagId a defined tag constant, e.g. {@link #TAG_IMAGE_WIDTH}.
	 * @return true if the tag has a defined number of elements.
	 */
	@SuppressWarnings( "unused" )
	public boolean isTagCountDefined( int tagId ) {
		int info = getTagInfo().get( tagId );
		// No value in info can be zero, as all tags have a non-zero type
		return info != 0 && getComponentCountFromInfo( info ) != ExifTag.SIZE_UNDEFINED;
	}

	protected static int getComponentCountFromInfo( int info ) {
		return info & 0x0ffff;
	}

	/**
	 * Gets the defined number of elements for a tag.
	 *
	 * @param tagId a defined tag constant, e.g. {@link #TAG_IMAGE_WIDTH}.
	 * @return the number of elements or {@link ExifTag#SIZE_UNDEFINED} if the
	 * tag or the number of elements is not defined.
	 */
	@SuppressWarnings( "unused" )
	public int getDefinedTagCount( int tagId ) {
		int info = getTagInfo().get( tagId );
		if( info == 0 ) {
			return ExifTag.SIZE_UNDEFINED;
		}
		return getComponentCountFromInfo( info );
	}

	/**
	 * Gets the number of elements for an ExifTag in a given IFD.
	 *
	 * @param tagId a defined tag constant, e.g. {@link #TAG_IMAGE_WIDTH}.
	 * @param ifdId the IFD containing the ExifTag to check.
	 * @return the number of elements in the ExifTag, if the tag's size is
	 * undefined this will return the actual number of elements that is
	 * in the ExifTag's value.
	 */
	@SuppressWarnings( "unused" )
	public int getActualTagCount( int tagId, int ifdId ) {
		ExifTag t = getTag( tagId, ifdId );
		if( t == null ) {
			return 0;
		}
		return t.getComponentCount();
	}

	/**
	 * Gets the defined type for a tag.
	 *
	 * @param tagId a defined tag constant, e.g. {@link #TAG_IMAGE_WIDTH}.
	 * @return the type.
	 * @see ExifTag#getDataType()
	 */
	@SuppressWarnings( "unused" )
	public short getDefinedTagType( int tagId ) {
		int info = getTagInfo().get( tagId );
		if( info == 0 ) {
			return - 1;
		}
		return getTypeFromInfo( info );
	}

	protected static short getTypeFromInfo( int info ) {
		return (short) ( ( info >> 16 ) & 0x0ff );
	}

	protected ExifTag buildUninitializedTag( int tagId ) {
		int info = getTagInfo().get( tagId );
		if( info == 0 ) {
			return null;
		}
		short type = getTypeFromInfo( info );
		int definedCount = getComponentCountFromInfo( info );
		boolean hasDefinedCount = ( definedCount != ExifTag.SIZE_UNDEFINED );
		int ifdId = getTrueIfd( tagId );
		return new ExifTag( getTrueTagKey( tagId ), type, definedCount, ifdId, hasDefinedCount );
	}

	/**
	 * Sets the value of an ExifTag if it exists it's default IFD. The value
	 * must be the correct type and length for that ExifTag.
	 *
	 * @param tagId a tag constant, e.g. {@link #TAG_IMAGE_WIDTH}.
	 * @param val   the value to set.
	 * @return true if success, false if the ExifTag doesn't exist or the value
	 * is the wrong type/length.
	 */
	@SuppressWarnings( "unused" )
	public boolean setTagValue( int tagId, Object val ) {
		int ifdId = getDefinedTagDefaultIfd( tagId );
		return setTagValue( tagId, ifdId, val );
	}

	/**
	 * Sets the value of an ExifTag if it exists in the given IFD. The value
	 * must be the correct type and length for that ExifTag.
	 *
	 * @param tagId a tag constant, e.g. {@link #TAG_IMAGE_WIDTH}.
	 * @param ifdId the IFD that the ExifTag is in.
	 * @param val   the value to set.
	 * @return true if success, false if the ExifTag doesn't exist or the value
	 * is the wrong type/length.
	 * @see #setTagValue
	 */
	public boolean setTagValue( int tagId, int ifdId, Object val ) {
		ExifTag t = getTag( tagId, ifdId );
		return t != null && t.setValue( val );
	}

	/**
	 * Removes the ExifTag for a tag constant from that tag's default IFD.
	 *
	 * @param tagId a tag constant, e.g. {@link #TAG_IMAGE_WIDTH}.
	 */
	public void deleteTag( int tagId ) {
		int ifdId = getDefinedTagDefaultIfd( tagId );
		deleteTag( tagId, ifdId );
	}

	/**
	 * Removes the ExifTag for a tag constant from the given IFD.
	 *
	 * @param tagId a tag constant, e.g. {@link #TAG_IMAGE_WIDTH}.
	 * @param ifdId the IFD of the ExifTag to remove.
	 */
	public void deleteTag( int tagId, int ifdId ) {
		mData.removeTag( getTrueTagKey( tagId ), ifdId );
	}

	/**
	 * Creates a new tag definition in this ExifInterface object for a given TID
	 * and default IFD. Creating a definition with the same TID and default IFD
	 * as a previous definition will override it.
	 *
	 * @param tagId                 the TID for the tag.
	 * @param defaultIfd            the default IFD for the tag.
	 * @param tagType               the type of the tag (see {@link ExifTag#getDataType()}).
	 * @param defaultComponentCount the number of elements of this tag's type in
	 *                              the tags value.
	 * @param allowedIfds           the IFD's this tag is allowed to be put in.
	 * @return the defined tag constant (e.g. {@link #TAG_IMAGE_WIDTH}) or
	 * {@link #TAG_NULL} if the definition could not be made.
	 */
	@SuppressWarnings( "unused" )
	public int setTagDefinition(
			short tagId, int defaultIfd, short tagType, short defaultComponentCount, int[] allowedIfds ) {
		if( sBannedDefines.contains( tagId ) ) {
			return TAG_NULL;
		}
		if( ExifTag.isValidType( tagType ) && ExifTag.isValidIfd( defaultIfd ) ) {
			int tagDef = defineTag( defaultIfd, tagId );
			if( tagDef == TAG_NULL ) {
				return TAG_NULL;
			}
			int[] otherDefs = getTagDefinitionsForTagId( tagId );
			SparseIntArray infos = getTagInfo();
			// Make sure defaultIfd is in allowedIfds
			boolean defaultCheck = false;
			for( int i : allowedIfds ) {
				if( defaultIfd == i ) {
					defaultCheck = true;
				}
				if( ! ExifTag.isValidIfd( i ) ) {
					return TAG_NULL;
				}
			}
			if( ! defaultCheck ) {
				return TAG_NULL;
			}

			int ifdFlags = getFlagsFromAllowedIfds( allowedIfds );
			// Make sure no identical tags can exist in allowedIfds
			if( otherDefs != null ) {
				for( int def : otherDefs ) {
					int tagInfo = infos.get( def );
					int allowedFlags = getAllowedIfdFlagsFromInfo( tagInfo );
					if( ( ifdFlags & allowedFlags ) != 0 ) {
						return TAG_NULL;
					}
				}
			}
			getTagInfo().put( tagDef, ifdFlags << 24 | ( tagType << 16 ) | defaultComponentCount );
			return tagDef;
		}
		return TAG_NULL;
	}

	@SuppressWarnings( "unused" )
	protected int getTagDefinition( short tagId, int defaultIfd ) {
		return getTagInfo().get( defineTag( defaultIfd, tagId ) );
	}

	/**
	 * Returns the constant representing a tag with a given TID and default IFD.
	 */
	public static int defineTag( int ifdId, short tagId ) {
		return ( tagId & 0x0000ffff ) | ( ifdId << 16 );
	}

	protected int[] getTagDefinitionsForTagId( short tagId ) {
		int[] ifds = IfdData.getIfds();
		int[] defs = new int[ifds.length];
		int counter = 0;
		SparseIntArray infos = getTagInfo();
		for( int i : ifds ) {
			int def = defineTag( i, tagId );
			if( infos.get( def ) != DEFINITION_NULL ) {
				defs[counter++] = def;
			}
		}
		if( counter == 0 ) {
			return null;
		}

		return Arrays.copyOfRange( defs, 0, counter );
	}

	@SuppressWarnings( "unused" )
	protected int getTagDefinitionForTag( ExifTag tag ) {
		short type = tag.getDataType();
		int count = tag.getComponentCount();
		int ifd = tag.getIfd();
		return getTagDefinitionForTag( tag.getTagId(), type, count, ifd );
	}

	protected int getTagDefinitionForTag( short tagId, short type, int count, int ifd ) {
		int[] defs = getTagDefinitionsForTagId( tagId );
		if( defs == null ) {
			return TAG_NULL;
		}
		SparseIntArray infos = getTagInfo();
		int ret = TAG_NULL;
		for( int i : defs ) {
			int info = infos.get( i );
			short def_type = getTypeFromInfo( info );
			int def_count = getComponentCountFromInfo( info );
			int[] def_ifds = getAllowedIfdsFromInfo( info );
			boolean valid_ifd = false;
			for( int j : def_ifds ) {
				if( j == ifd ) {
					valid_ifd = true;
					break;
				}
			}
			if( valid_ifd && type == def_type && ( count == def_count || def_count == ExifTag.SIZE_UNDEFINED ) ) {
				ret = i;
				break;
			}
		}
		return ret;
	}

	/**
	 * Removes a tag definition for given defined tag constant.
	 *
	 * @param tagId a defined tag constant, e.g. {@link #TAG_IMAGE_WIDTH}.
	 */
	@SuppressWarnings( "unused" )
	public void removeTagDefinition( int tagId ) {
		getTagInfo().delete( tagId );
	}

	/**
	 * Resets tag definitions to the default ones.
	 */
	@SuppressWarnings( "unused" )
	public void resetTagDefinitions() {
		mTagInfo = null;
	}

	/**
	 * Returns the thumbnail from IFD1 as a bitmap, or null if none exists.
	 *
	 * @return the thumbnail as a bitmap.
	 */
	public Bitmap getThumbnailBitmap() {
		if( mData.hasCompressedThumbnail() ) {
			byte[] thumb = mData.getCompressedThumbnail();
			return BitmapFactory.decodeByteArray( thumb, 0, thumb.length );
		}
		else if( mData.hasUncompressedStrip() ) {
			// TODO: implement uncompressed
		}
		return null;
	}

	/**
	 * Returns the thumbnail from IFD1 as a byte array, or null if none exists.
	 * The bytes may either be an uncompressed strip as specified in the exif
	 * standard or a jpeg compressed image.
	 *
	 * @return the thumbnail as a byte array.
	 */
	@SuppressWarnings( "unused" )
	public byte[] getThumbnailBytes() {
		if( mData.hasCompressedThumbnail() ) {
			return mData.getCompressedThumbnail();
		}
		else if( mData.hasUncompressedStrip() ) {
			// TODO: implement this
		}
		return null;
	}

	/**
	 * Returns the thumbnail if it is jpeg compressed, or null if none exists.
	 *
	 * @return the thumbnail as a byte array.
	 */
	public byte[] getThumbnail() {
		return mData.getCompressedThumbnail();
	}

	/**
	 * Returns the JPEG quality used to generate the image
	 * or 0 if not found
	 *
	 * @return
	 */
	public int getQualityGuess() {
		return mData.getQualityGuess();
	}

	/**
	 * this gives information about the process used to create the JPEG file.
	 * Possible values are:
	 * <ul>
	 * <li>'0' Unknown</li>
	 * <li>'192' Baseline</li>
	 * <li>'193' Extended sequential</li>
	 * <li>'194' Progressive</li>
	 * <li>'195' Lossless</li>
	 * <li>'197' Differential sequential</li>
	 * <li>'198' Differential progressive</li>
	 * <li>'199' Differential lossless</li>
	 * <li>'201' Extended sequential, arithmetic coding</li>
	 * <li>'202' Progressive, arithmetic coding</li>
	 * <li>'203' Lossless, arithmetic coding</li>
	 * <li>'205' Differential sequential, arithmetic coding</li>
	 * <li>'206' Differential progressive, arithmetic codng</li>
	 * <li>'207' Differential lossless, arithmetic coding</li>
	 * </ul>
	 */
	public short getJpegProcess() {
		return mData.getJpegProcess();
	}

	/**
	 * Returns the Image size as decoded from the SOF marker
	 */
	public int[] getImageSize() {
		return mData.getImageSize();
	}

	/**
	 * Check if thumbnail is compressed.
	 *
	 * @return true if the thumbnail is compressed.
	 */
	@SuppressWarnings( "unused" )
	public boolean isThumbnailCompressed() {
		return mData.hasCompressedThumbnail();
	}

	/**
	 * Check if thumbnail exists.
	 *
	 * @return true if a compressed thumbnail exists.
	 */
	public boolean hasThumbnail() {
		// TODO: add back in uncompressed strip
		return mData.hasCompressedThumbnail();
	}

	/**
	 * Sets the thumbnail to be a jpeg compressed bitmap. Clears any prior
	 * thumbnail.
	 *
	 * @param thumb a bitmap to compress to a jpeg thumbnail.
	 * @return true if the thumbnail was set.
	 */
	@SuppressWarnings( "unused" )
	public boolean setCompressedThumbnail( Bitmap thumb ) {
		ByteArrayOutputStream thumbnail = new ByteArrayOutputStream();
		if( ! thumb.compress( Bitmap.CompressFormat.JPEG, 90, thumbnail ) ) {
			return false;
		}
		return setCompressedThumbnail( thumbnail.toByteArray() );
	}

	/**
	 * Sets the thumbnail to be a jpeg compressed image. Clears any prior
	 * thumbnail.
	 *
	 * @param thumb a byte array containing a jpeg compressed image.
	 * @return true if the thumbnail was set.
	 */
	public boolean setCompressedThumbnail( byte[] thumb ) {
		mData.clearThumbnailAndStrips();
		mData.setCompressedThumbnail( thumb );
		return true;
	}

	/**
	 * Clears the compressed thumbnail if it exists.
	 */
	@SuppressWarnings( "unused" )
	public void removeCompressedThumbnail() {
		mData.setCompressedThumbnail( null );
	}

	/**
	 * Decodes the user comment tag into string as specified in the EXIF
	 * standard. Returns null if decoding failed.
	 */
	@SuppressWarnings( "unused" )
	public String getUserComment() {
		return mData.getUserComment();
	}

	/**
	 * Return the altitude in meters. If the exif tag does not exist, return
	 * <var>defaultValue</var>.
	 *
	 * @param defaultValue the value to return if the tag is not available.
	 */
	@SuppressWarnings( "unused" )
	public double getAltitude( double defaultValue ) {

		Byte ref = getTagByteValue( TAG_GPS_ALTITUDE_REF );
		Rational gpsAltitude = getTagRationalValue( TAG_GPS_ALTITUDE );

		int seaLevel = 1;
		if( null != ref ) {
			seaLevel = ref.intValue() == 1 ? - 1 : 1;
		}

		if( gpsAltitude != null ) {
			return gpsAltitude.toDouble() * seaLevel;
		}

		return defaultValue;
	}

	/**
	 * Gets the GPS latitude and longitude as a pair of doubles from this
	 * ExifInterface object's tags, or null if the necessary tags do not exist.
	 *
	 * @return an array of 2 doubles containing the latitude, and longitude
	 * respectively.
	 * @see #convertLatOrLongToDouble
	 */
	public double[] getLatLongAsDoubles() {
		Rational[] latitude = getTagRationalValues( TAG_GPS_LATITUDE );
		String latitudeRef = getTagStringValue( TAG_GPS_LATITUDE_REF );
		Rational[] longitude = getTagRationalValues( TAG_GPS_LONGITUDE );
		String longitudeRef = getTagStringValue( TAG_GPS_LONGITUDE_REF );
		if( latitude == null || longitude == null || latitudeRef == null || longitudeRef == null || latitude.length < 3 || longitude.length < 3 ) {
			return null;
		}
		double[] latLon = new double[2];
		latLon[0] = convertLatOrLongToDouble( latitude, latitudeRef );
		latLon[1] = convertLatOrLongToDouble( longitude, longitudeRef );
		return latLon;
	}

	/**
	 * Returns a formatted String with the latitude representation:<br />
	 * 39° 8' 16.8" N
	 */
	public String getLatitude() {
		Rational[] latitude = getTagRationalValues( TAG_GPS_LATITUDE );
		String latitudeRef = getTagStringValue( TAG_GPS_LATITUDE_REF );

		if( null == latitude || null == latitudeRef ) return null;
		return convertRationalLatLonToString( latitude, latitudeRef );
	}

	/**
	 * Returns a formatted String with the longitude representation:<br />
	 * 77° 37' 51.6" W
	 */
	public String getLongitude() {
		Rational[] longitude = getTagRationalValues( TAG_GPS_LONGITUDE );
		String longitudeRef = getTagStringValue( TAG_GPS_LONGITUDE_REF );

		if( null == longitude || null == longitudeRef ) return null;
		return convertRationalLatLonToString( longitude, longitudeRef );
	}

	private static String convertRationalLatLonToString( Rational[] coord, String ref ) {
		try {

			double degrees = coord[0].toDouble();
			double minutes = coord[1].toDouble();
			double seconds = coord[2].toDouble();
			ref = ref.substring( 0, 1 );

			return String.format( "%1$.0f° %2$.0f' %3$.0f\" %4$s", degrees, minutes, seconds, ref.toUpperCase( Locale.getDefault() ) );
		} catch( NumberFormatException e ) {
			e.printStackTrace();
		} catch( ArrayIndexOutOfBoundsException e ) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Given an exif date time, like {@link #TAG_DATE_TIME} or {@link #TAG_DATE_TIME_DIGITIZED}
	 * returns a java Date object
	 *
	 * @param dateTimeString one of the value of {@link #TAG_DATE_TIME} or {@link #TAG_DATE_TIME_DIGITIZED}
	 * @param timeZone the target timezone
	 * @return the parsed date
	 */
	public static Date getDateTime( String dateTimeString, TimeZone timeZone ) {
		if( dateTimeString == null ) return null;

		DateFormat formatter = new SimpleDateFormat( DATETIME_FORMAT_STR );
		formatter.setTimeZone( timeZone );

		try {
			return formatter.parse( dateTimeString );
		} catch( IllegalArgumentException e ) {
			e.printStackTrace();
		} catch( ParseException e ) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Creates, formats, and sets the DateTimeStamp tag for one of:
	 * {@link #TAG_DATE_TIME}, {@link #TAG_DATE_TIME_DIGITIZED},
	 * {@link #TAG_DATE_TIME_ORIGINAL}.
	 *
	 * @param tagId     one of the DateTimeStamp tags.
	 * @param timestamp a timestamp to format.
	 * @param timezone  a TimeZone object.
	 * @return true if success, false if the tag could not be set.
	 */
	public boolean addDateTimeStampTag( int tagId, long timestamp, TimeZone timezone ) {
		if( tagId == TAG_DATE_TIME || tagId == TAG_DATE_TIME_DIGITIZED || tagId == TAG_DATE_TIME_ORIGINAL ) {
			mDateTimeStampFormat.setTimeZone( timezone );
			ExifTag t = buildTag( tagId, mDateTimeStampFormat.format( timestamp ) );
			if( t == null ) {
				return false;
			}
			setTag( t );
		}
		else {
			return false;
		}
		return true;
	}

	/**
	 * Creates a tag for a defined tag constant in the tag's default IFD.
	 *
	 * @param tagId a tag constant, e.g. {@link #TAG_IMAGE_WIDTH}.
	 * @param val   the tag's value.
	 * @return an ExifTag object.
	 */
	public ExifTag buildTag( int tagId, Object val ) {
		int ifdId = getTrueIfd( tagId );
		return buildTag( tagId, ifdId, val );
	}

	/**
	 * Creates a tag for a defined tag constant in a given IFD if that IFD is
	 * allowed for the tag.  This method will fail anytime the appropriate
	 * {@link ExifTag#setValue} for this tag's datatype would fail.
	 *
	 * @param tagId a tag constant, e.g. {@link #TAG_IMAGE_WIDTH}.
	 * @param ifdId the IFD that the tag should be in.
	 * @param val   the value of the tag to set.
	 * @return an ExifTag object or null if one could not be constructed.
	 * @see #buildTag
	 */
	public ExifTag buildTag( int tagId, int ifdId, Object val ) {
		int info = getTagInfo().get( tagId );
		if( info == 0 || val == null ) {
			return null;
		}
		short type = getTypeFromInfo( info );
		int definedCount = getComponentCountFromInfo( info );
		boolean hasDefinedCount = ( definedCount != ExifTag.SIZE_UNDEFINED );
		if( ! ExifInterface.isIfdAllowed( info, ifdId ) ) {
			return null;
		}
		ExifTag t = new ExifTag( getTrueTagKey( tagId ), type, definedCount, ifdId, hasDefinedCount );
		if( ! t.setValue( val ) ) {
			return null;
		}
		return t;
	}

	protected static boolean isIfdAllowed( int info, int ifd ) {
		int[] ifds = IfdData.getIfds();
		int ifdFlags = getAllowedIfdFlagsFromInfo( info );
		for( int i = 0; i < ifds.length; i++ ) {
			if( ifd == ifds[i] && ( ( ifdFlags >> i ) & 1 ) == 1 ) {
				return true;
			}
		}
		return false;
	}

	protected static int getAllowedIfdFlagsFromInfo( int info ) {
		return info >>> 24;
	}

	/**
	 * Creates and sets all to the GPS tags for a give latitude and longitude.
	 *
	 * @param latitude  a GPS latitude coordinate.
	 * @param longitude a GPS longitude coordinate.
	 * @return true if success, false if they could not be created or set.
	 */
	@SuppressWarnings( "unused" )
	public boolean addGpsTags( double latitude, double longitude ) {
		ExifTag latTag = buildTag( TAG_GPS_LATITUDE, toExifLatLong( latitude ) );
		ExifTag longTag = buildTag( TAG_GPS_LONGITUDE, toExifLatLong( longitude ) );
		ExifTag latRefTag = buildTag( TAG_GPS_LATITUDE_REF, latitude >= 0 ? GpsLatitudeRef.NORTH : GpsLatitudeRef.SOUTH );
		ExifTag longRefTag = buildTag( TAG_GPS_LONGITUDE_REF, longitude >= 0 ? GpsLongitudeRef.EAST : GpsLongitudeRef.WEST );
		if( latTag == null || longTag == null || latRefTag == null || longRefTag == null ) {
			return false;
		}
		setTag( latTag );
		setTag( longTag );
		setTag( latRefTag );
		setTag( longRefTag );
		return true;
	}

	private static Rational[] toExifLatLong( double value ) {
		// convert to the format dd/1 mm/1 ssss/100
		value = Math.abs( value );
		int degrees = (int) value;
		value = ( value - degrees ) * 60;
		int minutes = (int) value;
		value = ( value - minutes ) * 6000;
		int seconds = (int) value;
		return new Rational[]{ new Rational( degrees, 1 ), new Rational( minutes, 1 ), new Rational( seconds, 100 ) };
	}

	/**
	 * Creates and sets the GPS timestamp tag.
	 *
	 * @param timestamp a GPS timestamp.
	 * @return true if success, false if could not be created or set.
	 */
	@SuppressWarnings( "unused" )
	public boolean addGpsDateTimeStampTag( long timestamp ) {
		ExifTag t = buildTag( TAG_GPS_DATE_STAMP, mGPSDateStampFormat.format( timestamp ) );
		if( t == null ) {
			return false;
		}
		setTag( t );
		mGPSTimeStampCalendar.setTimeInMillis( timestamp );
		t = buildTag( TAG_GPS_TIME_STAMP,
		              new Rational[]{ new Rational( mGPSTimeStampCalendar.get( Calendar.HOUR_OF_DAY ), 1 ), new Rational( mGPSTimeStampCalendar.get( Calendar.MINUTE ), 1 ),
				              new Rational( mGPSTimeStampCalendar.get( Calendar.SECOND ), 1 ) }
		);
		if( t == null ) {
			return false;
		}
		setTag( t );
		return true;
	}

	/**
	 * Return the aperture size, if present, 0 if missing
	 */
	public double getApertureSize() {
		Rational rational = getTagRationalValue( TAG_F_NUMBER );
		if( null != rational && rational.toDouble() > 0 ) {
			return rational.toDouble();
		}

		rational = getTagRationalValue( TAG_APERTURE_VALUE );
		if( null != rational && rational.toDouble() > 0 ) {
			return Math.exp( rational.toDouble() * Math.log( 2 ) * 0.5 );
		}
		return 0;
	}

	/**
	 * Returns the lens model as string if any of the tags {@link #TAG_LENS_MODEL}
	 * or {@link #TAG_LENS_SPECS} are found
	 *
	 * @return the string representation of the lens spec
	 */
	public String getLensModelDescription() {
		String lensModel = getTagStringValue( TAG_LENS_MODEL );
		if( null != lensModel ) return lensModel;

		Rational[] rat = getTagRationalValues( TAG_LENS_SPECS );
		if( null != rat ) return ExifUtil.processLensSpecifications( rat );

		return null;
	}

	/**
	 * Constants for {@link #TAG_ORIENTATION}. They can be interpreted as
	 * follows:
	 * <ul>
	 * <li>TOP_LEFT is the normal orientation.</li>
	 * <li>TOP_RIGHT is a left-right mirror.</li>
	 * <li>BOTTOM_LEFT is a 180 degree rotation.</li>
	 * <li>BOTTOM_RIGHT is a top-bottom mirror.</li>
	 * <li>LEFT_TOP is mirrored about the top-left<->bottom-right axis.</li>
	 * <li>RIGHT_TOP is a 90 degree clockwise rotation.</li>
	 * <li>LEFT_BOTTOM is mirrored about the top-right<->bottom-left axis.</li>
	 * <li>RIGHT_BOTTOM is a 270 degree clockwise rotation.</li>
	 * </ul>
	 */
	@SuppressWarnings( "unused" )
	public static interface Orientation {
		public static final short TOP_LEFT = 1;
		public static final short TOP_RIGHT = 2;
		public static final short BOTTOM_RIGHT = 3;
		public static final short BOTTOM_LEFT = 4;
		public static final short LEFT_TOP = 5;
		public static final short RIGHT_TOP = 6;
		public static final short RIGHT_BOTTOM = 7;
		public static final short LEFT_BOTTOM = 8;
	}

	/**
	 * Constants for {@link #TAG_Y_CB_CR_POSITIONING}
	 */
	@SuppressWarnings( "unused" )
	public static interface YCbCrPositioning {
		public static final short CENTERED = 1;
		public static final short CO_SITED = 2;
	}

	/**
	 * Constants for {@link #TAG_COMPRESSION}
	 */
	@SuppressWarnings( "unused" )
	public static interface Compression {
		public static final short UNCOMPRESSION = 1;
		public static final short JPEG = 6;
	}

	// TODO: uncompressed thumbnail setters

	/**
	 * Constants for {@link #TAG_RESOLUTION_UNIT}
	 */
	@SuppressWarnings( "unused" )
	public static interface ResolutionUnit {
		public static final short INCHES = 2;
		public static final short CENTIMETERS = 3;
		public static final short MILLIMETERS = 4;
		public static final short MICROMETERS = 5;
	}

	/**
	 * Constants for {@link #TAG_PHOTOMETRIC_INTERPRETATION}
	 */
	@SuppressWarnings( "unused" )
	public static interface PhotometricInterpretation {
		public static final short RGB = 2;
		public static final short YCBCR = 6;
	}

	/**
	 * Constants for {@link #TAG_PLANAR_CONFIGURATION}
	 */
	@SuppressWarnings( "unused" )
	public static interface PlanarConfiguration {
		public static final short CHUNKY = 1;
		public static final short PLANAR = 2;
	}

	// Convenience methods:

	/**
	 * Constants for {@link #TAG_EXPOSURE_PROGRAM}
	 */
	@SuppressWarnings( "unused" )
	public static interface ExposureProgram {
		public static final short NOT_DEFINED = 0;
		public static final short MANUAL = 1;
		public static final short NORMAL_PROGRAM = 2;
		public static final short APERTURE_PRIORITY = 3;
		public static final short SHUTTER_PRIORITY = 4;
		public static final short CREATIVE_PROGRAM = 5;
		public static final short ACTION_PROGRAM = 6;
		public static final short PROTRAIT_MODE = 7;
		public static final short LANDSCAPE_MODE = 8;
	}

	/**
	 * Constants for {@link #TAG_METERING_MODE}
	 */
	@SuppressWarnings( "unused" )
	public static interface MeteringMode {
		public static final short UNKNOWN = 0;
		public static final short AVERAGE = 1;
		public static final short CENTER_WEIGHTED_AVERAGE = 2;
		public static final short SPOT = 3;
		public static final short MULTISPOT = 4;
		public static final short PATTERN = 5;
		public static final short PARTAIL = 6;
		public static final short OTHER = 255;
	}

	@SuppressWarnings( "unused" )
	public static byte[] toBitArray( short value ) {
		byte[] result = new byte[16];
		for( int i = 0; i < 16; i++ ) {
			result[15 - i] = (byte) ( ( value >> i ) & 1 );
		}
		return result;
	}

	/**
	 * Constants for {@link #TAG_FLASH} As the definition in Jeita EXIF 2.2
	 */
	@SuppressWarnings( "unused" )
	public static interface Flash {

		/** first bit */
		public static enum FlashFired {
			NO, YES
		}

		/** Values for bits 1 and 2 indicating the status of returned light */
		public static enum StrobeLightDetection {
			NO_DETECTION, RESERVED, LIGHT_NOT_DETECTED, LIGHT_DETECTED
		}

		/** Values for bits 3 and 4 indicating the camera's flash mode */
		public static enum CompulsoryMode {
			UNKNOWN,
			FIRING,
			SUPPRESSION,
			AUTO
		}

		/** Values for bit 5 indicating the presence of a flash function. */
		public static enum FlashFunction {
			FUNCTION_PRESENT,
			FUNCTION_NOR_PRESENT
		}

		/** Values for bit 6 indicating the camera's red-eye mode. */
		public static enum RedEyeMode {
			NONE,
			SUPPORTED
		}
	}

	/**
	 * Constants for {@link #TAG_COLOR_SPACE}
	 */
	@SuppressWarnings( "unused" )
	public static interface ColorSpace {
		public static final short SRGB = 1;
		public static final short UNCALIBRATED = (short) 0xFFFF;
	}

	/**
	 * Constants for {@link #TAG_EXPOSURE_MODE}
	 */
	@SuppressWarnings( "unused" )
	public static interface ExposureMode {
		public static final short AUTO_EXPOSURE = 0;
		public static final short MANUAL_EXPOSURE = 1;
		public static final short AUTO_BRACKET = 2;
	}

	/**
	 * Constants for {@link #TAG_WHITE_BALANCE}
	 */
	@SuppressWarnings( "unused" )
	public static interface WhiteBalance {
		public static final short AUTO = 0;
		public static final short MANUAL = 1;
	}

	/**
	 * Constants for {@link #TAG_SCENE_CAPTURE_TYPE}
	 */
	@SuppressWarnings( "unused" )
	public static interface SceneCapture {
		public static final short STANDARD = 0;
		public static final short LANDSCAPE = 1;
		public static final short PROTRAIT = 2;
		public static final short NIGHT_SCENE = 3;
	}

	/**
	 * Constants for {@link #TAG_COMPONENTS_CONFIGURATION}
	 */
	@SuppressWarnings( "unused" )
	public static interface ComponentsConfiguration {
		public static final short NOT_EXIST = 0;
		public static final short Y = 1;
		public static final short CB = 2;
		public static final short CR = 3;
		public static final short R = 4;
		public static final short G = 5;
		public static final short B = 6;
	}

	/**
	 * Constants for {@link #TAG_LIGHT_SOURCE}
	 */
	@SuppressWarnings( "unused" )
	public static interface LightSource {
		public static final short UNKNOWN = 0;
		public static final short DAYLIGHT = 1;
		public static final short FLUORESCENT = 2;
		public static final short TUNGSTEN = 3;
		public static final short FLASH = 4;
		public static final short FINE_WEATHER = 9;
		public static final short CLOUDY_WEATHER = 10;
		public static final short SHADE = 11;
		public static final short DAYLIGHT_FLUORESCENT = 12;
		public static final short DAY_WHITE_FLUORESCENT = 13;
		public static final short COOL_WHITE_FLUORESCENT = 14;
		public static final short WHITE_FLUORESCENT = 15;
		public static final short STANDARD_LIGHT_A = 17;
		public static final short STANDARD_LIGHT_B = 18;
		public static final short STANDARD_LIGHT_C = 19;
		public static final short D55 = 20;
		public static final short D65 = 21;
		public static final short D75 = 22;
		public static final short D50 = 23;
		public static final short ISO_STUDIO_TUNGSTEN = 24;
		public static final short OTHER = 255;
	}

	/**
	 * Constants for {@link #TAG_SENSING_METHOD}
	 */
	@SuppressWarnings( "unused" )
	public static interface SensingMethod {
		public static final short NOT_DEFINED = 1;
		public static final short ONE_CHIP_COLOR = 2;
		public static final short TWO_CHIP_COLOR = 3;
		public static final short THREE_CHIP_COLOR = 4;
		public static final short COLOR_SEQUENTIAL_AREA = 5;
		public static final short TRILINEAR = 7;
		public static final short COLOR_SEQUENTIAL_LINEAR = 8;
	}

	/**
	 * Constants for {@link #TAG_FILE_SOURCE}
	 */
	@SuppressWarnings( "unused" )
	public static interface FileSource {
		public static final short DSC = 3;
	}

	/**
	 * Constants for {@link #TAG_SCENE_TYPE}
	 */
	@SuppressWarnings( "unused" )
	public static interface SceneType {
		public static final short DIRECT_PHOTOGRAPHED = 1;
	}

	/**
	 * Constants for {@link #TAG_GAIN_CONTROL}
	 */
	@SuppressWarnings( "unused" )
	public static interface GainControl {
		public static final short NONE = 0;
		public static final short LOW_UP = 1;
		public static final short HIGH_UP = 2;
		public static final short LOW_DOWN = 3;
		public static final short HIGH_DOWN = 4;
	}

	/**
	 * Constants for {@link #TAG_CONTRAST}
	 */
	@SuppressWarnings( "unused" )
	public static interface Contrast {
		public static final short NORMAL = 0;
		public static final short SOFT = 1;
		public static final short HARD = 2;
	}

	/**
	 * Constants for {@link #TAG_SATURATION}
	 */
	@SuppressWarnings( "unused" )
	public static interface Saturation {
		public static final short NORMAL = 0;
		public static final short LOW = 1;
		public static final short HIGH = 2;
	}

	/**
	 * Constants for {@link #TAG_SHARPNESS}
	 */
	@SuppressWarnings( "unused" )
	public static interface Sharpness {
		public static final short NORMAL = 0;
		public static final short SOFT = 1;
		public static final short HARD = 2;
	}

	/**
	 * Constants for {@link #TAG_SUBJECT_DISTANCE}
	 */
	@SuppressWarnings( "unused" )
	public static interface SubjectDistance {
		public static final short UNKNOWN = 0;
		public static final short MACRO = 1;
		public static final short CLOSE_VIEW = 2;
		public static final short DISTANT_VIEW = 3;
	}

	/**
	 * Constants for {@link #TAG_GPS_LATITUDE_REF},
	 * {@link #TAG_GPS_DEST_LATITUDE_REF}
	 */
	@SuppressWarnings( "unused" )
	public static interface GpsLatitudeRef {
		public static final String NORTH = "N";
		public static final String SOUTH = "S";
	}

	/**
	 * Constants for {@link #TAG_GPS_LONGITUDE_REF},
	 * {@link #TAG_GPS_DEST_LONGITUDE_REF}
	 */
	@SuppressWarnings( "unused" )
	public static interface GpsLongitudeRef {
		public static final String EAST = "E";
		public static final String WEST = "W";
	}

	/**
	 * Constants for {@link #TAG_GPS_ALTITUDE_REF}
	 */
	@SuppressWarnings( "unused" )
	public static interface GpsAltitudeRef {
		public static final short SEA_LEVEL = 0;
		public static final short SEA_LEVEL_NEGATIVE = 1;
	}

	/**
	 * Constants for {@link #TAG_GPS_STATUS}
	 */
	@SuppressWarnings( "unused" )
	public static interface GpsStatus {
		public static final String IN_PROGRESS = "A";
		public static final String INTEROPERABILITY = "V";
	}

	/**
	 * Constants for {@link #TAG_GPS_MEASURE_MODE}
	 */
	@SuppressWarnings( "unused" )
	public static interface GpsMeasureMode {
		public static final String MODE_2_DIMENSIONAL = "2";
		public static final String MODE_3_DIMENSIONAL = "3";
	}

	/**
	 * Constants for {@link #TAG_GPS_SPEED_REF},
	 * {@link #TAG_GPS_DEST_DISTANCE_REF}
	 */
	@SuppressWarnings( "unused" )
	public static interface GpsSpeedRef {
		public static final String KILOMETERS = "K";
		public static final String MILES = "M";
		public static final String KNOTS = "N";
	}

	/**
	 * Constants for {@link #TAG_GPS_TRACK_REF},
	 * {@link #TAG_GPS_IMG_DIRECTION_REF}, {@link #TAG_GPS_DEST_BEARING_REF}
	 */
	@SuppressWarnings( "unused" )
	public static interface GpsTrackRef {
		public static final String TRUE_DIRECTION = "T";
		public static final String MAGNETIC_DIRECTION = "M";
	}

	/**
	 * Constants for {@link #TAG_GPS_DIFFERENTIAL}
	 */
	@SuppressWarnings( "unused" )
	public static interface GpsDifferential {
		public static final short WITHOUT_DIFFERENTIAL_CORRECTION = 0;
		public static final short DIFFERENTIAL_CORRECTION_APPLIED = 1;
	}

	/**
	 * Constants for the jpeg process algorithm used.
	 *
	 * @see #getJpegProcess()
	 */
	@SuppressWarnings( "unused" )
	public static interface JpegProcess {
		public static final short BASELINE = (short) 0xFFC0;
		public static final short EXTENDED_SEQUENTIAL = (short) 0xFFC1;
		public static final short PROGRESSIVE = (short) 0xFFC2;
		public static final short LOSSLESS = (short) 0xFFC3;
		public static final short DIFFERENTIAL_SEQUENTIAL = (short) 0xFFC5;
		public static final short DIFFERENTIAL_PROGRESSIVE = (short) 0xFFC6;
		public static final short DIFFERENTIAL_LOSSLESS = (short) 0xFFC7;
		public static final short EXTENDED_SEQ_ARITHMETIC_CODING = (short) 0xFFC9;
		public static final short PROGRESSIVE_AIRTHMETIC_CODING = (short) 0xFFCA;
		public static final short LOSSLESS_AITHMETIC_CODING = (short) 0xFFCB;
		public static final short DIFFERENTIAL_SEQ_ARITHMETIC_CODING = (short) 0xFFCD;
		public static final short DIFFERENTIAL_PROGRESSIVE_ARITHMETIC_CODING = (short) 0xFFCE;
		public static final short DIFFERENTIAL_LOSSLESS_ARITHMETIC_CODING = (short) 0xFFCF;
	}

	/**
	 * Constants for the {@link #TAG_SENSITIVITY_TYPE} tag
	 */
	@SuppressWarnings( "unused" )
	public static interface SensitivityType {

		public static final short UNKNOWN = 0;

		/** Standard output sensitivity */
		public static final short SOS = 1;

		/** Recommended exposure index */
		public static final short REI = 2;

		/** ISO Speed */
		public static final short ISO = 3;

		/** Standard output sensitivity and Recommended output index */
		public static final short SOS_REI = 4;

		/** Standard output sensitivity and ISO speed */
		public static final short SOS_ISO = 5;

		/** Recommended output index and ISO Speed */
		public static final short REI_ISO = 6;

		/** Standard output sensitivity and Recommended output index and ISO Speed */
		public static final short SOS_REI_ISO = 7;
	}

	/**
	 * Options for calling {@link #readExif(java.io.InputStream, int)}, {@link #readExif(byte[], int)},
	 * {@link #readExif(String, int)}
	 */
	public static interface Options {
		/**
		 * Option bit to request to parse IFD0.
		 */
		int OPTION_IFD_0 = 1;
		/**
		 * Option bit to request to parse IFD1.
		 */
		int OPTION_IFD_1 = 1 << 1;
		/**
		 * Option bit to request to parse Exif-IFD.
		 */
		int OPTION_IFD_EXIF = 1 << 2;
		/**
		 * Option bit to request to parse GPS-IFD.
		 */
		int OPTION_IFD_GPS = 1 << 3;
		/**
		 * Option bit to request to parse Interoperability-IFD.
		 */
		int OPTION_IFD_INTEROPERABILITY = 1 << 4;
		/**
		 * Option bit to request to parse thumbnail.
		 */
		int OPTION_THUMBNAIL = 1 << 5;
		/**
		 * Option bit to request all the options
		 */
		int OPTION_ALL = OPTION_IFD_0 ^ OPTION_IFD_1 ^ OPTION_IFD_EXIF ^ OPTION_IFD_GPS ^ OPTION_IFD_INTEROPERABILITY ^ OPTION_THUMBNAIL;
	}
}
