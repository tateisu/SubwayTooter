package jp.juggler.util

import android.content.Context
import android.graphics.*
import android.net.Uri
import it.sephiroth.android.library.exif2.ExifInterface
import java.io.FileNotFoundException
import kotlin.math.max
import kotlin.math.sqrt

private val log = LogCategory("BitmapUtils")

// EXIFのorientationが特定の値ならwとhを入れ替える
private fun rotateSize(orientation:Int? ,w:Float,h:Float):PointF =
	when(orientation) {
		5, 6, 7, 8 -> PointF(h, w)
		else -> PointF(w, h)
	}

enum class ResizeType{
	None,
	LongSide,
	SquarePixel,
}

class ResizeConfig(
	val type: ResizeType,
	val size: Int
)

fun createResizedBitmap(
	context : Context,
	uri : Uri,
	sizeLongSide : Int,
	skipIfNoNeedToResizeAndRotate : Boolean = false
) = createResizedBitmap(
	context,
	uri,
	if(sizeLongSide<=0) ResizeConfig(ResizeType.None,0) else ResizeConfig(ResizeType.LongSide,sizeLongSide),
	skipIfNoNeedToResizeAndRotate = skipIfNoNeedToResizeAndRotate
)

fun createResizedBitmap(
	context : Context,
	uri : Uri,
	resizeConfig : ResizeConfig,
	skipIfNoNeedToResizeAndRotate : Boolean = false
) : Bitmap? {
	
	try {
		
		// EXIF回転情報の取得
		val orientation : Int? = context.contentResolver.openInputStream(uri)?.use { inStream ->
			val exif = ExifInterface()
			exif.readExif(
				inStream,
				ExifInterface.Options.OPTION_IFD_0 or ExifInterface.Options.OPTION_IFD_1 or ExifInterface.Options.OPTION_IFD_EXIF
			)
			exif.getTagIntValue(ExifInterface.TAG_ORIENTATION)
		}
		
		// 画像のサイズを調べる
		val options = BitmapFactory.Options()
		options.inJustDecodeBounds = true
		options.inScaled = false
		options.outWidth = 0
		options.outHeight = 0
		context.contentResolver.openInputStream(uri)?.use { inStream ->
			BitmapFactory.decodeStream(inStream, null, options)
		}
		var src_width = options.outWidth
		var src_height = options.outHeight
		if(src_width <= 0 || src_height <= 0) {
			showToast(context, false, "could not get image bounds.")
			return null
		}

		// 回転後のサイズ
		val srcSize = rotateSize(orientation,src_width.toFloat(),src_height.toFloat())
		val aspect = srcSize.x / srcSize.y

		
		/// 出力サイズの計算
		val sizeSpec = resizeConfig.size.toFloat()
		val dstSize:PointF = when(resizeConfig.type){
			ResizeType.None ->
				srcSize
			ResizeType.LongSide->
				if(max(srcSize.x, srcSize.y) <= resizeConfig.size) {
					srcSize
				} else {
					if(aspect >= 1f) {
						PointF(
							resizeConfig.size.toFloat(),
							sizeSpec / aspect
						)
					} else {
						PointF(
							sizeSpec * aspect,
							resizeConfig.size.toFloat()
						)
					}
				}
			ResizeType.SquarePixel->{
				val maxPixels = sizeSpec * sizeSpec
				val currentPixels = srcSize.x * srcSize.y
				if( currentPixels <= maxPixels ) {
					srcSize
				}else {
					val y = sqrt( maxPixels / aspect)
					val x = aspect * y
					PointF( x,y)
				}
			}
		}
		
		val dstSizeInt = Point(
			max(1,(dstSize.x+0.5f).toInt()),
			max(1,(dstSize.y+0.5f).toInt())
		)

		val reSizeRequired = dstSizeInt.x != srcSize.x.toInt() || dstSizeInt.y != srcSize.y.toInt()
		
		// リサイズも回転も必要がない場合
		if(skipIfNoNeedToResizeAndRotate
			&& (orientation == null || orientation == 1)
			&& !reSizeRequired
		) {
			log.d("createOpener: no need to resize & rotate")
			return null
		}
		
		// 長辺
		val dstMax = max( dstSize.x, dstSize.y).toInt()

		// inSampleSizeを計算
		var bits = 0
		var x = max( srcSize.x,srcSize.y).toInt()
		while( x > 512 && x > dstMax * 2 ){
			++ bits
			x = x shr 1
		}
		options.inJustDecodeBounds = false
		options.inSampleSize = 1 shl bits
		
		val sourceBitmap : Bitmap? =
			context.contentResolver.openInputStream(uri)?.use { inStream ->
				BitmapFactory.decodeStream(inStream, null, options)
			}
		
		if(sourceBitmap == null) {
			showToast(context, false, "could not decode image.")
			return null
		}
		try {
			// サンプル数が変化している
			src_width = options.outWidth
			src_height = options.outHeight
			val scale = dstMax.toFloat() / max(src_width,src_height)
			
			val matrix = Matrix()
			matrix.reset()
			
			// 画像の中心が原点に来るようにして
			matrix.postTranslate(src_width * - 0.5f, src_height * - 0.5f)
			// スケーリング
			matrix.postScale(scale, scale)

			// 回転情報があれば回転
				when(orientation) {
					2 -> matrix.postScale(1f, - 1f)  // 上下反転
					3 -> matrix.postRotate(180f) // 180度回転
					4 -> matrix.postScale(- 1f, 1f) // 左右反転
					
					5 ->{
						matrix.postScale(1f, - 1f)
						matrix.postRotate(- 90f)
					}
					
					6 -> matrix.postRotate(90f)
					
					7 -> {
						matrix.postScale(1f, - 1f)
						matrix.postRotate(90f)
					}
					
					8 -> matrix.postRotate(- 90f)
				}
			
			

			// 表示領域に埋まるように平行移動
			matrix.postTranslate(dstSizeInt.x.toFloat() * 0.5f, dstSizeInt.y.toFloat() * 0.5f)
			
			// 出力用Bitmap作成
			var dst : Bitmap? = Bitmap.createBitmap(dstSizeInt.x,dstSizeInt.y, Bitmap.Config.ARGB_8888)
			try {
				return if(dst == null) {
					showToast(context, false, "bitmap creation failed.")
					null
				} else {
					val canvas = Canvas(dst)
					val paint = Paint()
					paint.isFilterBitmap = true
					canvas.drawBitmap(sourceBitmap, matrix, paint)
					log.d(
						"createResizedBitmap: resized to %sx%s",
						dstSizeInt.x,
						dstSizeInt.y
					)
					val tmp = dst
					dst = null
					tmp
				}
			} finally {
				dst?.recycle()
			}
		} finally {
			sourceBitmap.recycle()
		}
	} catch(ex : FileNotFoundException) {
		log.w(ex, "not found. $uri")
	} catch(ex : SecurityException) {
		log.w(ex, "maybe we need pick up image again.")
	} catch(ex : Throwable) {
		log.trace(ex, "createResizedBitmap")
	}
	return null
}
