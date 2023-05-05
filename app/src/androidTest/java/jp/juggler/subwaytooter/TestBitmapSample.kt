package jp.juggler.subwaytooter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.juggler.util.data.*
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class TestBitmapSample {

    /**
     * BitmapFactory.Options.inSampleSize の取り扱いの確認
     */
    @Test
    fun test() {
        val srcSize = 1024

        val baSrc = run {
            val bitmap = Bitmap.createBitmap(srcSize, srcSize, Bitmap.Config.ARGB_8888)
            try {
                ByteArrayOutputStream().use { outStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outStream)
                    outStream.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
        }

        for (n in 0..32) {
            val options = BitmapFactory.Options().apply {
                inScaled = false
                outWidth = 0
                outHeight = 0
                inJustDecodeBounds = false
                inSampleSize = n
            }
            val expectedSizeA: Int
            val expectedSizeB: Int
            when (n) {
                // ドキュメントには "If set to a value > 1" とあり、1以下の値はリサイズに影響しない
                0, 1 -> {
                    expectedSizeA = srcSize
                    expectedSizeB = srcSize
                }
                // 2以上の場合、ドキュメントには
                // "Note: the decoder uses a final value based on powers of 2, any other value will be rounded down to the nearest power of 2."
                // とあるが、実際に試すと端末により width = srcSize/n となることがある。
                else -> {
                    expectedSizeA = srcSize.div(n.takeHighestOneBit())
                    expectedSizeB = srcSize.div(n)
                }
            }
            val bitmap = BitmapFactory.decodeByteArray(baSrc, 0, baSrc.size, options)!!
            try {
                when (bitmap.width) {
                    expectedSizeA -> Unit
                    expectedSizeB -> Unit
                    else -> fail("inSampleSize=$n, srcSize=$srcSize, resultWidth=${bitmap.width}")
                }
            } finally {
                bitmap.recycle()
            }
        }
    }
}
