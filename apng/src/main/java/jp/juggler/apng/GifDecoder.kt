package jp.juggler.apng

import java.io.InputStream
import kotlin.math.min

/**
 * Class GifDecoder - Decodes a GIF file into one or more frames.
 *
 * Example:
 *
 * <pre>
 * {@code
 *    GifDecoder d = new GifDecoder();
 *    d.read("sample.gif");
 *    int n = d.getFrameCount();
 *    for (int i = 0; i < n; i++) {
 *       BufferedImage frame = d.getFrame(i);  // frame i
 *       int t = d.getDelay(i);  // display duration of frame in milliseconds
 *       // do something with frame
 *    }
 * }
 * </pre>
 * No copyright asserted on the source code of this class.  May be used for
 * any purpose, however, refer to the Unisys LZW patent for any additional
 * restrictions.  Please forward any corrections to questions at fmsware.com.
 *
 * @author Kevin Weiner, FM Software; LZW decoder adapted from John Cristy's ImageMagick.
 * @version 1.03 November 2003
 *
 */

class Rectangle(val x : Int, val y : Int, val w : Int, val h : Int)

class GifFrame(val image : ApngBitmap, val delay : Int)

private class Reader(val bis : InputStream) {
	
	var block = ByteArray(256) // current data block
	var blockSize = 0 // block size
	
	// Reads a single byte from the input stream.
	fun read() : Int = bis.read()
	
	fun readArray(ba:ByteArray,offset:Int=0,length:Int=ba.size-offset) =
		bis.read(ba,offset,length)
	
	/**
	 * Reads next 16-bit value, LSB first
	 */
	// read 16-bit value, LSB first
	fun readShort() = read() or (read() shl 8)
	
	/**
	 * Reads next variable length block from input.
	 *
	 * @return number of bytes stored in "buffer"
	 */
	fun readBlock() : ByteArray {
		val blockSize = read()
		this.blockSize = blockSize
		var n = 0
		while(n < blockSize) {
			val delta = bis .read(block, n, blockSize - n)
			if(delta == - 1) throw RuntimeException("unexpected EOS")
			n += delta
		}
		return block
	}
	
	/**
	 * Skips variable length blocks up to and including
	 * next zero length block.
	 */
	fun skip() {
		do {
			readBlock()
		} while((blockSize > 0))
	}
	
	// read n byte and compose it to ascii string
	fun string(n : Int) =
		StringBuilder()
			.apply {
				for(i in 0 until n) {
					append(read().toChar())
				}
			}
			.toString()
	
}

@Suppress("MemberVisibilityCanBePrivate", "unused")
class GifDecoder(val callback: GifDecoderCallback) {
	
	companion object {
		
		// max decoder pixel stack size
		const val MaxStackSize = 4096
		
		const val NullCode = - 1
		
		private const val b0 = 0.toByte()
		
	}
	
	private var width = 0 // full image width
	private var height = 0 // full image height
	private var gctSize = 0  // size of global color table
	private var loopCount = 1 // iterations; 0 = repeat forever
	
	private var gct : IntArray? = null // global color table
	private var lct : IntArray? = null // local color table
	private var act : IntArray? = null // active color table
	
	private var bgIndex = 0 // background color index
	private var bgColor = 0 // background color
	private var lastBgColor = 0 // previous bg color
	private var pixelAspect = 0 // pixel aspect ratio
	
	private var interlace = false // interlace flag
	
	private var lctFlag = false  // local color table flag
	private var lctSize = 0 // local color table size
	
	private var ix = 0  // current image rectangle
	private var iy = 0 // current image rectangle
	private var iw = 0  // current image rectangle
	private var ih = 0  // current image rectangle
	
	private var lastRect : Rectangle? = null // last image rect
	private var image : ApngBitmap? = null // current frame
	
	// last graphic control extension info
	private var dispose = 0
	// 0=no action; 1=leave in place; 2=restore to bg; 3=restore to prev
	private var lastDispose = 0
	private var transparency = false // use transparent color
	private var delay = 0 // delay in milliseconds
	private var transIndex = 0 // transparent color index
	
	// LZW decoder working arrays
	private var prefix : ShortArray? = null
	private var suffix : ByteArray? = null
	private var pixelStack : ByteArray? = null
	private var pixels : ByteArray? = null
	
	private var frames = ArrayList<GifFrame>()  // frames read from current file
	private var frameCount = 0
	
	/////////////////////////////////////////////////////////////
	// get decode result.
	
	/**
	 * Gets the image contents of frame n.
	 *
	 * @return BufferedImage representation of frame, or null if n is invalid.
	 */
	@Suppress("MemberVisibilityCanBePrivate", "unused")
	fun getFrame(n : Int) : ApngBitmap? {
		frames?.let {
			if(n in 0 until it.size) return it[n].image
		}
		return null
	}
	
	/////////////////////////////////////////////////////////////
	// private functions.
	

	
	/**
	 * Decodes LZW image data into pixel array.
	 * Adapted from John Cristy's ImageMagick.
	 */
	private fun decodeImageData(reader:Reader) {
		
		// Reallocate pizel array if need
		val npix = iw * ih
		if((pixels?.size ?: 0) < npix) pixels = ByteArray(npix)
		val pixels = this.pixels !!
		
		if(prefix == null) prefix = ShortArray(MaxStackSize)
		if(suffix == null) suffix = ByteArray(MaxStackSize)
		if(pixelStack == null) pixelStack = ByteArray(MaxStackSize + 1)
		val prefix = this.prefix !!
		val suffix = this.suffix !!
		val pixelStack = this.pixelStack !!
		
		//  Initialize GIF data stream decoder.
		val data_size = reader.read()
		val clear = 1 shl data_size
		val end_of_information = clear + 1
		for(code in 0 until clear) {
			prefix[code] = 0
			suffix[code] = code.toByte()
		}
		
		//  Decode GIF pixel stream.
		var datum = 0
		var bits = 0
		var count = 0
		var first = 0
		var top = 0
		var bi = 0
		var pi = 0
		var available = clear + 2
		var old_code = NullCode
		var code_size = data_size + 1
		var code_mask = (1 shl code_size) - 1
		
		var i = 0
		while(i < npix) {
			if(top == 0) {
				if(bits < code_size) {
					//  Load bytes until there are enough bits for a code.
					if(count == 0) {
						// Read a new data block.
						reader.readBlock()
						count = reader.blockSize
						if( count <= 0) break
						bi = 0
					}
					datum += (reader.block[bi].toInt() and 0xff) shl bits
					bits += 8
					bi ++
					count --
					continue
				}
				
				//  Get the next code.
				var code = datum and code_mask
				datum = datum shr code_size
				bits -= code_size
				
				//  Interpret the code
				if((code > available) || (code == end_of_information)) break
				
				if(code == clear) {
					//  Reset decoder.
					code_size = data_size + 1
					code_mask = (1 shl code_size) - 1
					available = clear + 2
					old_code = NullCode
					continue
				}
				if(old_code == NullCode) {
					pixelStack[top ++] = suffix[code]
					old_code = code
					first = code
					continue
				}
				val in_code = code
				
				if(code == available) {
					pixelStack[top ++] = first.toByte()
					code = old_code
				}
				while(code > clear) {
					pixelStack[top ++] = suffix[code]
					code = prefix[code].toInt()
				}
				first = suffix[code].toInt() and 0xff
				
				//  Add a new string to the string table,
				
				if(available >= MaxStackSize) {
					pixelStack[top ++] = first.toByte()
					continue
				}
				pixelStack[top ++] = first.toByte()
				prefix[available] = old_code.toShort()
				suffix[available] = first.toByte()
				available ++
				
				if((available and code_mask) == 0 && available < MaxStackSize) {
					code_size ++
					code_mask += available
				}
				
				old_code = in_code
			}
			
			//  Pop a pixel off the pixel stack.
			top --
			pixels[pi ++] = pixelStack[top]
			i ++
		}
		
		// clear missing pixels
		for(n in pi until npix) {
			pixels[n] = b0
		}
	}
	
	/**
	 * Creates new frame image from current data (and previous
	 * frames as specified by their disposition codes).
	 */
	private fun setPixels(destImage : ApngBitmap) {
		// expose destination image's pixels as int array
		val dest = destImage.colors
		val dest_w = destImage.width
		
		// fill in starting image contents based on last image's dispose code
		var lastImage : ApngBitmap? = null // previous frame
		if(lastDispose > 0) {
			if(lastDispose == 3) {
				// use image before last
				val n = frameCount - 2
				if(n > 0) {
					lastImage = getFrame(n - 1)
				} else {
					lastImage = null
				}
			}
			
		}
		if(lastImage != null) {
			// copy pixels
			System.arraycopy(lastImage.colors, 0, dest, 0, dest.size)
			
			val lastRect = this.lastRect
			if(lastDispose == 2 && lastRect != null) {
				// fill lastRect
				
				val fillColor = if(transparency) {
					0 // assume background is transparent
				} else {
					lastBgColor // use given background color
				}
				
				for(y in lastRect.y until lastRect.y + lastRect.h) {
					val fillStart = y * dest_w + lastRect.x
					val fillWidth = lastRect.w
					dest.fill(fillColor, fromIndex = fillStart, toIndex = fillStart + fillWidth)
				}
			}
		}
		
		// copy each source line to the appropriate place in the destination
		var pass = 1
		var inc = 8
		var iline = 0
		for(i in 0 until ih) {
			var line = i
			if(interlace) {
				if(iline >= ih) {
					pass ++
					when(++ pass) {
						2 -> {
							iline = 4
							inc = 8
						}
						
						3 -> {
							iline = 2
							inc = 4
						}
						
						4 -> {
							iline = 1
							inc = 2
						}
					}
				}
				line = iline
				iline += inc
			}
			line += iy
			if(line < height) {
				
				// start of line in source
				var sx = i * iw
				
				//
				val k = line * width
				
				// loop for dest line.
				for(dx in k + ix until min(k + width, k + ix + iw)) {
					// map color and insert in destination
					val index = pixels !![sx ++].toInt() and 0xff
					val c = act !![index]
					if(c != 0) dest[dx] = c
				}
			}
		}
	}
	
	/**
	 * Reads color table as 256 RGB integer values
	 *
	 * @param nColors int number of colors to read
	 * @return int array containing 256 colors (packed ARGB with full alpha)
	 */
	private fun readColorTable(reader:Reader,nColors : Int) : IntArray {
		val nBytes = 3 * nColors
		val c = ByteArray(nBytes)
		val n = reader.readArray(c)
		if(n < nBytes) throw RuntimeException("unexpected EOS")
		
		// max size to avoid bounds checks
		val tab = IntArray(256)
		var i = 0
		var j = 0
		val opaque = 0xff shl 24
		while(i < nColors) {
			val r = c[j].toInt() and 255
			val g = c[j + 1].toInt() and 255
			val b = c[j + 2].toInt() and 255
			j += 3
			tab[i ++] = ( opaque or (r shl 16) or (g shl 8) or b)
		}
		return tab
	}
	
	/**
	 * Reads Graphics Control Extension values
	 */
	private fun readGraphicControlExt(reader:Reader) {
		reader.read() // block size
		
		val packed = reader.read() // packed fields
		
		dispose = (packed and 0x1c) shr 2 // disposal method
		
		// elect to keep old image if discretionary
		if(dispose == 0) dispose = 1
		
		transparency = (packed and 1) != 0
		
		// delay in milliseconds
		delay = reader.readShort() * 10
		// transparent color index
		transIndex = reader.read()
		
		// block terminator
		reader.read()
	}
	
	// Reads Netscape extension to obtain iteration count
	private fun readNetscapeExt(reader:Reader) {
		do {
			val block = reader.readBlock()
			if(block[0].toInt() == 1) {
				// loop count sub-block
				val b1 = block[1].toInt() and 255
				val b2 = block[2].toInt() and 255
				loopCount = ((b2 shl 8) and b1)
			}
		} while( reader.blockSize > 0 )
	}
	
	// Reads next frame image
	private fun readImage(reader:Reader) {
		ix = reader.readShort() // (sub)image position & size
		iy = reader.readShort()
		iw = reader.readShort()
		ih = reader.readShort()
		
		val packed = reader.read()
		lctFlag = (packed and 0x80) != 0 // 1 - local color table flag
		interlace = (packed and 0x40) != 0 // 2 - interlace flag
		// 3 - sort flag
		// 4-5 - reserved
		lctSize = 2 shl (packed and 7) // 6-8 - local color table size
		
		val act = if(lctFlag) {
			lct = readColorTable(reader,lctSize) // read table
			lct !! // make local table active
		} else {
			if(bgIndex == transIndex) bgColor = 0
			gct !! // make global table active
		}
		
		this.act = act
		var save = 0
		if(transparency) {
			save = act[transIndex]
			act[transIndex] = 0 // set transparent color if specified
		}
		
		
		decodeImageData(reader) // decode pixel data
		reader.skip()
		
		++ frameCount
		
		// create new image to receive frame data
		val image = ApngBitmap(width, height).apply {
			setPixels(this) // transfer pixel data to image
		}
		this.image = image
		
		frames.add(GifFrame(image, delay)) // add image to frame list
		
		if(transparency) {
			act[transIndex] = save
		}

		/**
		 * Resets frame state for reading next image.
		 */
		lastDispose = dispose
		lastRect = Rectangle(ix, iy, iw, ih)
		lastBgColor = bgColor
		dispose = 0
		transparency = false
		delay = 0
		lct = null
	}
	
	private fun readContents(reader : Reader) {
		// read GIF file content blocks
		loopBlocks@ while(true) {
			when(val blockCode = reader.read()) {
				// image separator
				0x2C -> readImage(reader)
				// extension
				0x21 -> when(reader.read()) {
					// graphics control extension
					0xf9 -> readGraphicControlExt(reader)
					// application extension
					0xff -> {
						val block = reader.readBlock()
						var app = ""
						for(i in 0 until 11) {
							app += block[i].toChar()
						}
						if(app == "NETSCAPE2.0") {
							readNetscapeExt(reader)
						} else {
							reader.skip() // don't care
						}
					}
					
					else -> {
						// uninteresting extension
						reader.skip()
					}
				}
				
				// terminator
				0x3b -> break@loopBlocks
				
				// bad byte, but keep going and see what happens
				0x00 -> {
				}
				
				else -> error("unknown block code $blockCode")
			}
		}
	}
	
	var header : ApngImageHeader? = null
	var animationControl : ApngAnimationControl? = null
	
	/**
	 * Reads GIF file header information.
	 */
	private fun readHeader(reader : Reader) {
		
		/**
		 * Initializes or re-initializes reader
		 */
		frameCount = 0
		frames.clear()
		lct = null
		loopCount = ApngAnimationControl.PLAY_INDEFINITELY
		
		val id = reader.string(6)
		if(! id.startsWith("GIF"))
			error("file header not match to GIF.")
		
		/**
		 * Reads Logical Screen Descriptor
		 */

		// logical screen size
		width = reader.readShort()
		height = reader.readShort()
		if( width < 1 || height < 1) error("too small size. ${width}*${height}")
		
		// packed fields
		val packed = reader.read()
		
		// global color table used
		val gctFlag = (packed and 0x80) != 0 // 1   : global color table flag
		// 2-4 : color resolution
		// 5   : gct sort flag
		gctSize = 2 shl (packed and 7) // 6-8 : gct size
		
		bgIndex = reader.read() // background color index
		pixelAspect = reader.read() // pixel aspect ratio
		
		gct = if(gctFlag) {
			val table = readColorTable(reader,gctSize)
			bgColor = table[bgIndex]
			table
		}else{
			bgColor = 0
			null
		}
		
		val header = ApngImageHeader(
			width = this.width,
			height = this.height,
			bitDepth = 8,
			colorType = ColorType.INDEX,
			compressionMethod = CompressionMethod.Standard,
			filterMethod = FilterMethod.Standard,
			interlaceMethod = InterlaceMethod.None
		)
		this.header = header
		
		callback.onGifHeader(header)
	}
	
	
	@Suppress("MemberVisibilityCanBePrivate", "unused")
	fun read(src : InputStream) {
		val reader = Reader(src)
		readHeader(reader)
		readContents(reader)
		
		val header = this.header!!
		if(frameCount < 0) throw error("frameCount < 0")
		
		// GIFは最後まで読まないとフレーム数が分からない？
		// 最後まで読んでからフレーム数をコールバック
		val animationControl = ApngAnimationControl(numFrames = frameCount,numPlays = loopCount)
		this.animationControl = animationControl
		callback.onGifAnimationInfo( header, animationControl)

		// 各フレームを送る
		var i=0
		for(frame in frames){
			val frameControl = ApngFrameControl(
				width = header.width,
				height = header.height,
				xOffset = 0,
				yOffset = 0,
				disposeOp = DisposeOp.None,
				blendOp = BlendOp.Source,
				sequenceNumber=i++,
				delayMilliseconds = frame.delay.toLong()
			)
			callback.onGifAnimationFrame(frameControl,frame.image)
		}
		frames.clear()
	}
	
}