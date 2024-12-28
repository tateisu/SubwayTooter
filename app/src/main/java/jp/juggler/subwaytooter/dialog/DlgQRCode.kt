package jp.juggler.subwaytooter.dialog

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatActivity
import com.github.alexzhirkevich.customqrgenerator.QrData
import com.github.alexzhirkevich.customqrgenerator.vector.QrCodeDrawable
import com.github.alexzhirkevich.customqrgenerator.vector.createQrVectorOptions
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorBallShape
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorColor
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorFrameShape
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorLogoPadding
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorLogoShape
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorPixelShape
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.databinding.DlgQrCodeBinding
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.coroutine.withProgress
import jp.juggler.util.log.LogCategory
import jp.juggler.util.ui.resDrawable
import kotlinx.coroutines.withContext

private val log = LogCategory("DlgQRCode")

val UInt.int get() = toInt()

fun AppCompatActivity.dialogQrCode(
    message: CharSequence,
    url: String,
) = launchAndShowError("dialogQrCode failed.") {
    val drawable = withProgress(
        caption = getString(R.string.generating_qr_code),
    ) {
        withContext(AppDispatchers.DEFAULT) {
            QrCodeDrawable(data = QrData.Url(url), options = qrCodeOptions())
        }
    }
    val dialog = Dialog(this@dialogQrCode)

    val views = DlgQrCodeBinding.inflate(layoutInflater).apply {
        btnCancel.setOnClickListener { dialog.cancel() }
        ivQrCode.setImageDrawable(drawable)
        tvMessage.text = message
        tvUrl.text = "[ $url ]" // なぜか素のURLだと@以降が表示されない
    }

    dialog.apply {
        setContentView(views.root)
        setCancelable(true)
        setCanceledOnTouchOutside(true)
        show()
    }
}

private fun AppCompatActivity.qrCodeOptions() = createQrVectorOptions {
    background {
        drawable = ColorDrawable(Color.WHITE)
    }

    padding = .125f

    logo {
        drawable = resDrawable(R.drawable.qr_code_center)
        size = .25f
        shape = QrVectorLogoShape.Default
        padding = QrVectorLogoPadding.Natural(.1f)
    }
    shapes {
        // 市松模様のドット
        darkPixel = QrVectorPixelShape.RoundCorners(.5f)
        // 3隅の真ん中の大きめドット
        ball = QrVectorBallShape.RoundCorners(.25f)
        // 3隅の枠
        frame = QrVectorFrameShape.RoundCorners(.25f)
    }
    colors {
        val cobalt = 0xFF0088FFU.int
        val cobaltDark = 0xFF004488U.int
        // 市松模様のドット
        dark = QrVectorColor.Solid(cobaltDark)
        // 3隅の真ん中の大きめドット
        ball = QrVectorColor.RadialGradient(
            colors = listOf(
                0f to cobaltDark,
                1f to cobalt,
            ),
            radius = 2f,
        )
        // 3隅の枠
        frame = QrVectorColor.LinearGradient(
            colors = listOf(
                0f to cobaltDark,
                1f to cobalt,
            ),
            orientation = QrVectorColor.LinearGradient
                .Orientation.Vertical
        )
    }
}
