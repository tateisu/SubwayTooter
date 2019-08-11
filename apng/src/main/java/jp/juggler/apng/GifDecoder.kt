package jp.juggler.apng

import java.io.InputStream
import kotlin.math.min

class GifDecoder {
	
	private class Rectangle(var x : Int = 0, var y : Int = 0, var w : Int = 0, var h : Int = 0) {
		fun set(x : Int, y : Int, w : Int, h : Int) {
			this.x = x
			this.y = y
			this.w = w
			this.h = h
		}
	}
	
	private class Reader(val bis : InputStream) {
		
		var block = ByteArray(256) // current data block
		var blockSize = 0 // block size
		
		// Reads a single byte from the input stream.
		fun byte() : Int = bis.read()
		
		// Reads next 16-bit value, LSB first
		fun UInt16() = byte() or (byte() shl 8)
		
		fun array(ba : ByteArray, offset : Int = 0, length : Int = ba.size - offset) {
			var nRead = 0
			while(nRead < length) {
				val delta = bis.read(ba, offset + nRead, length - nRead)
				if(delta == - 1) throw RuntimeException("unexpected End of Stream")
				nRead += delta
			}
		}
		
		// Reads specified bytes and compose it to ascii string
		fun string(n : Int) : String {
			val ba = ByteArray(n)
			array(ba)
			return ba.map { it.toChar() }.joinToString(separator = "")
		}
		
		// Reads next variable length block
		fun block() : ByteArray {
			blockSize = byte()
			array(block, 0, blockSize)
			return block
		}
		
		// Skips variable length blocks up to and including next zero length block.
		fun skipBlock() {
			do {
				block()
			} while(blockSize > 0)
		}
	}
	
	companion object {
		
		// max decoder pixel stack size
		private const val MaxStackSize = 4096
		
		private const val NullCode = - 1
		
		private const val b0 = 0.toByte()
		
		private const val OPAQUE = 0xff shl 24
		
	}
	
	private var width = 0 // full image width
	private var height = 0 // full image height
	private var gctSize = 0  // size of global color table
	private var loopCount = 1 // iterations; 0 = repeat forever
	
	private var gct : IntArray? = null // global color table
	
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
	
	private val lastRect : Rectangle = Rectangle() // last image rect
	
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
	
	private val frames = ArrayList<Pair<ApngFrameControl, ApngBitmap>>()
	
	private var lastImage : ApngBitmap? = null
	
	// render to ApngBitmap
	// may use some previous frame.
	private fun render(destImage : ApngBitmap,act:IntArray) {
		// expose destination image's pixels as int array
		val dest = destImage.colors
		val dest_w = destImage.width
		
		// fill in starting image contents based on last image's dispose code
		if(lastDispose > 0) {

			if(lastDispose == 3) {
				// use image before last
				val idx = frames.size - 2
				lastImage = if(idx > 0) {
					frames[idx-1].second
				} else {
					null
				}
			} else {
				// lastDisposeが3以外でもlastImageはnullではない場合がある
			}
			
			lastImage?.let{ lastImage ->

				// copy pixels
				System.arraycopy(lastImage.colors, 0, dest, 0, dest.size)
				
				if(lastDispose == 2) {
					val fillColor = if(transparency) {
						0 // assume background is transparent
					} else {
						lastBgColor // use given background color
					}
					
					// fill lastRect
					for(y in lastRect.y until lastRect.y + lastRect.h) {
						val fillStart = y * dest_w + lastRect.x
						val fillWidth = lastRect.w
						dest.fill(fillColor, fromIndex = fillStart, toIndex = fillStart + fillWidth)
					}
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
					when(++ pass) {
						2 -> {
							iline = 4
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
					val c = act[index]
					if(c != 0) dest[dx] = c
				}
			}
		}
	}
	
	/**
	 * Decodes LZW image data into pixel array.
	 * Adapted from John Cristy's ImageMagick.
	 */
	private fun decodeImageData(reader : Reader) {
		
		// allocate pixel array if need
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
		val data_size = reader.byte()
		val clear = 1 shl data_size
		val end_of_information = clear + 1
		
		var available = clear + 2
		var old_code = NullCode
		var code_size = data_size + 1
		var code_mask = (1 shl code_size) - 1

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
		
		var i = 0
		while(i < npix) {
			if(top == 0) {
				if(bits < code_size) {
					//  Load bytes until there are enough bits for a code.
					if(count == 0) {
						// Read a new data block.
						reader.block()
						count = reader.blockSize
						if(count <= 0) break
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
	 * Reads color table as 256 RGB integer values
	 *
	 * @param nColors int number of colors to read
	 * @return int array containing 256 colors (packed ARGB with full alpha)
	 */
	private fun parseColorTable(reader : Reader, nColors : Int) : IntArray {
		val nBytes = 3 * nColors
		val c = ByteArray(nBytes)
		reader.array(c)
		
		// max size to avoid bounds checks
		val tab = IntArray(256)
		var i = 0
		var j = 0
		while(i < nColors) {
			val r = c[j].toInt() and 255
			val g = c[j + 1].toInt() and 255
			val b = c[j + 2].toInt() and 255
			j += 3
			tab[i ++] = (OPAQUE or (r shl 16) or (g shl 8) or b)
		}
		return tab
	}
	
	/**
	 * Reads Graphics Control Extension values
	 */
	private fun parseGraphicControlExt(reader : Reader) {
		reader.byte() // block size
		val packed = reader.byte() // packed fields
		dispose = (packed and 0x1c) shr 2 // disposal method
		// elect to keep old image if discretionary
		if(dispose == 0) dispose = 1
		
		transparency = (packed and 1) != 0
		// delay in milliseconds
		delay = reader.UInt16() * 10
		// transparent color index
		transIndex = reader.byte()
		// block terminator
		reader.byte()
	}
	
	// Reads Netscape extension to obtain iteration count
	private fun readNetscapeExt(reader : Reader) {
		do {
			val block = reader.block()
			if(block[0].toInt() == 1) {
				// loop count sub-block
				val b1 = block[1].toInt() and 255
				val b2 = block[2].toInt() and 255
				loopCount = ((b2 shl 8) and b1)
			}
		} while(reader.blockSize > 0)
	}
	
	// Reads next frame image
	private fun parseFrame(reader : Reader) {
		ix = reader.UInt16() // (sub)image position & size
		iy = reader.UInt16()
		iw = reader.UInt16()
		ih = reader.UInt16()
		
		val packed = reader.byte()
		lctFlag = (packed and 0x80) != 0 // 1 - local color table flag
		interlace = (packed and 0x40) != 0 // 2 - interlace flag
		// 3 - sort flag
		// 4-5 - reserved
		lctSize = 2 shl (packed and 7) // 6-8 - local color table size
		
		val act = if(lctFlag) {
			// make local table active
			parseColorTable(reader, lctSize)
		} else {
			if(bgIndex == transIndex) bgColor = 0
			gct !! // make global table active
		}
		
		var save = 0
		if(transparency) {
			save = act[transIndex]
			act[transIndex] = 0 // set transparent color if specified
		}
		
		decodeImageData(reader) // decode pixel data
		reader.skipBlock()
		
		val image = ApngBitmap(width, height).also {
			render(it,act) // transfer pixel data to image
		}
		
		// add image to frame list
		frames.add(
			Pair(
				ApngFrameControl(
					width = width,
					height = height,
					xOffset = 0,
					yOffset = 0,
					disposeOp = DisposeOp.None,
					blendOp = BlendOp.Source,
					sequenceNumber = frames.size,
					delayMilliseconds = delay.toLong()
				),
				image
			)
		)
		
		if(transparency) {
			act[transIndex] = save
		}
		
		/**
		 * Resets frame state for reading next image.
		 */
		lastDispose = dispose
		lastRect.set(ix, iy, iw, ih)
		lastBgColor = bgColor
		dispose = 0
		transparency = false
		delay = 0
		lastImage = image
	}
	
	// read GIF content blocks
	private fun readContents(reader : Reader) : ApngAnimationControl {
		loopBlocks@ while(true) {
			when(val blockCode = reader.byte()) {
				// image separator
				0x2C -> parseFrame(reader)
				// extension
				0x21 -> when(reader.byte()) {
					// graphics control extension
					0xf9 -> parseGraphicControlExt(reader)
					// application extension
					0xff -> {
						val block = reader.block()
						var app = ""
						for(i in 0 until 11) {
							app += block[i].toChar()
						}
						if(app == "NETSCAPE2.0") {
							readNetscapeExt(reader)
						} else {
							reader.skipBlock() // don't care
						}
					}
					
					else -> {
						// uninteresting extension
						reader.skipBlock()
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
		
		return ApngAnimationControl(numFrames = frames.size, numPlays = loopCount)
	}
	
	/**
	 * Initializes or re-initializes reader
	 */
	private fun reset(){
		frames.clear()
		lastImage = null
		loopCount = ApngAnimationControl.PLAY_INDEFINITELY
	}
	
	/**
	 * Reads GIF file header information.
	 */
	private fun parseImageHeader(reader : Reader) : ApngImageHeader {
		
		reset()
		
		val id = reader.string(6)
		if(! id.startsWith("GIF"))
			error("file header not match to GIF.")
		
		/**
		 * Reads Logical Screen Descriptor
		 */
		
		// logical screen size
		width = reader.UInt16()
		height = reader.UInt16()
		if(width < 1 || height < 1) error("too small size. ${width}*${height}")
		
		// packed fields
		val packed = reader.byte()
		
		// global color table used
		val gctFlag = (packed and 0x80) != 0 // 1   : global color table flag
		// 2-4 : color resolution
		// 5   : gct sort flag
		gctSize = 2 shl (packed and 7) // 6-8 : gct size
		
		bgIndex = reader.byte() // background color index
		pixelAspect = reader.byte() // pixel aspect ratio
		
		gct = if(gctFlag) {
			val table = parseColorTable(reader, gctSize)
			bgColor = table[bgIndex]
			table
		} else {
			bgColor = 0
			null
		}
		
		return ApngImageHeader(
			width = this.width,
			height = this.height,
			bitDepth = 8,
			colorType = ColorType.INDEX,
			compressionMethod = CompressionMethod.Standard,
			filterMethod = FilterMethod.Standard,
			interlaceMethod = InterlaceMethod.None
		)
	}
	
	fun parse(src : InputStream, callback : GifDecoderCallback) {
		val reader = Reader(src)
		val header = parseImageHeader(reader)
		val animationControl = readContents(reader)
		
		// GIFは最後まで読まないとフレーム数が分からない
		if(frames.isEmpty()) throw error("there is no frame.")
		
		callback.onGifHeader(header)
		callback.onGifAnimationInfo(header, animationControl)
		for(frame in frames) {
			callback.onGifAnimationFrame(frame.first, frame.second)
		}
		
		reset()
	}
}
