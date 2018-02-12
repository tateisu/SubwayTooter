package jp.juggler.subwaytooter.util

import android.content.Context
import android.graphics.*
import android.net.Uri
import it.sephiroth.android.library.exif2.ExifInterface

object BitmapUtils {
	internal val log = LogCategory("BitmapUtils")
}

fun createResizedBitmap(
	context : Context,
	uri : Uri,
	resizeToArg : Int,
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
		
		// 長辺
		val size = Math.max(src_width, src_height)
		
		// リサイズも回転も必要がない場合
		if(skipIfNoNeedToResizeAndRotate
			&& (orientation == null || orientation == 1)
			&& (resizeToArg <= 0 || size <= resizeToArg)) {
			BitmapUtils.log.d("createOpener: no need to resize & rotate")
			return null
		}
		
		val resize_to = Math.min(size, resizeToArg)
		
		// inSampleSizeを計算
		var bits = 0
		var x = size
		while(x > resize_to * 2) {
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
			src_width = options.outWidth
			src_height = options.outHeight
			val scale : Float
			var dst_width : Int
			var dst_height : Int
			if(src_width >= src_height) {
				scale = resize_to / src_width.toFloat()
				dst_width = resize_to
				dst_height = (0.5f + src_height / src_width.toFloat() * resize_to).toInt()
				if(dst_height < 1) dst_height = 1
			} else {
				scale = resize_to / src_height.toFloat()
				dst_height = resize_to
				dst_width = (0.5f + src_width / src_height.toFloat() * resize_to).toInt()
				if(dst_width < 1) dst_width = 1
			}
			
			val matrix = Matrix()
			matrix.reset()
			
			// 画像の中心が原点に来るようにして
			matrix.postTranslate(src_width * - 0.5f, src_height * - 0.5f)
			// スケーリング
			matrix.postScale(scale, scale)
			// 回転情報があれば回転
			if(orientation != null) {
				val tmp : Int
				when(orientation) {
					
					2 -> matrix.postScale(1f, - 1f)  // 上下反転
					3 -> matrix.postRotate(180f) // 180度回転
					4 -> matrix.postScale(- 1f, 1f) // 左右反転
					
					5 -> {
						tmp = dst_width
						
						dst_width = dst_height
						dst_height = tmp
						matrix.postScale(1f, - 1f)
						matrix.postRotate(- 90f)
					}
					
					6 -> {
						tmp = dst_width
						
						dst_width = dst_height
						dst_height = tmp
						matrix.postRotate(90f)
					}
					
					7 -> {
						tmp = dst_width
						
						dst_width = dst_height
						dst_height = tmp
						matrix.postScale(1f, - 1f)
						matrix.postRotate(90f)
					}
					
					8 -> {
						tmp = dst_width
						
						dst_width = dst_height
						dst_height = tmp
						matrix.postRotate(- 90f)
					}
					
					else -> {
					}
				}
			}
			// 表示領域に埋まるように平行移動
			matrix.postTranslate(dst_width * 0.5f, dst_height * 0.5f)
			
			// 出力用Bitmap作成
			var dst : Bitmap? =
				Bitmap.createBitmap(dst_width, dst_height, Bitmap.Config.ARGB_8888)
			try {
				return if(dst == null) {
					showToast(context, false, "bitmap creation failed.")
					null
				} else {
					val canvas = Canvas(dst)
					val paint = Paint()
					paint.isFilterBitmap = true
					canvas.drawBitmap(sourceBitmap, matrix, paint)
					BitmapUtils.log.d(
						"createResizedBitmap: resized to %sx%s",
						dst_width,
						dst_height
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
	} catch(ex : SecurityException) {
		BitmapUtils.log.e(ex, "maybe we need pick up image again.")
	} catch(ex : Throwable) {
		BitmapUtils.log.trace(ex)
	}
	return null
}
