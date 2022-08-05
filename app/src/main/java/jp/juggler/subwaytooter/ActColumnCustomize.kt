package jp.juggler.subwaytooter

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import com.jrummyapps.android.colorpicker.ColorPickerDialog
import com.jrummyapps.android.colorpicker.ColorPickerDialogListener
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.column.*
import jp.juggler.util.*
import org.jetbrains.anko.textColor
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import kotlin.math.max

class ActColumnCustomize : AppCompatActivity(), View.OnClickListener, ColorPickerDialogListener {

    companion object {

        internal val log = LogCategory("ActColumnCustomize")

        internal const val EXTRA_COLUMN_INDEX = "column_index"

        internal const val COLOR_DIALOG_ID_HEADER_BACKGROUND = 1
        internal const val COLOR_DIALOG_ID_HEADER_FOREGROUND = 2
        internal const val COLOR_DIALOG_ID_COLUMN_BACKGROUND = 3
        internal const val COLOR_DIALOG_ID_ACCT_TEXT = 4
        internal const val COLOR_DIALOG_ID_CONTENT_TEXT = 5

        internal const val PROGRESS_MAX = 65536

        fun createIntent(activity: ActMain, idx: Int) =
            Intent(activity, ActColumnCustomize::class.java).apply {
                putExtra(EXTRA_COLUMN_INDEX, idx)
            }
    }

    private var columnIndex: Int = 0
    internal lateinit var column: Column
    internal lateinit var appState: AppState
    internal var density: Float = 0f

    private lateinit var flColumnBackground: View
    internal lateinit var ivColumnBackground: ImageView
    internal lateinit var sbColumnBackgroundAlpha: SeekBar
    private lateinit var llColumnHeader: View
    private lateinit var ivColumnHeader: ImageView
    private lateinit var tvColumnName: TextView
    internal lateinit var etAlpha: EditText
    private lateinit var tvSampleAcct: TextView
    private lateinit var tvSampleContent: TextView

    internal var loadingBusy: Boolean = false

    private var lastImageUri: String? = null
    private var lastImageBitmap: Bitmap? = null

    private val arColumnBackgroundImage = ActivityResultHandler(log) { r ->
        if (r.isNotOk) return@ActivityResultHandler
        r.data?.handleGetContentResult(contentResolver)
            ?.firstOrNull()?.uri?.let { updateBackground(it) }
    }

    override fun onBackPressed() {
        makeResult()
        super.onBackPressed()
    }

    private fun makeResult() {
        val data = Intent()
        data.putExtra(EXTRA_COLUMN_INDEX, columnIndex)
        setResult(RESULT_OK, data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arColumnBackgroundImage.register(this)
        App1.setActivityTheme(this)
        initUI()

        appState = App1.getAppState(this)
        density = appState.density
        columnIndex = intent.getIntExtra(EXTRA_COLUMN_INDEX, 0)
        column = appState.column(columnIndex)!!
        show()
    }

    override fun onDestroy() {
        closeBitmaps()
        super.onDestroy()
    }

    override fun onClick(v: View) {

        val builder: ColorPickerDialog.Builder

        when (v.id) {

            R.id.btnHeaderBackgroundEdit -> {
                ColorPickerDialog.newBuilder()
                    .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                    .setAllowPresets(true)
                    .setShowAlphaSlider(false)
                    .setDialogId(COLOR_DIALOG_ID_HEADER_BACKGROUND)
                    .setColor(column.getHeaderBackgroundColor())
                    .show(this)
            }

            R.id.btnHeaderBackgroundReset -> {
                column.headerBgColor = 0
                show()
            }

            R.id.btnHeaderTextEdit -> {
                ColorPickerDialog.newBuilder()
                    .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                    .setAllowPresets(true)
                    .setShowAlphaSlider(false)
                    .setDialogId(COLOR_DIALOG_ID_HEADER_FOREGROUND)
                    .setColor(column.getHeaderNameColor())
                    .show(this)
            }

            R.id.btnHeaderTextReset -> {
                column.headerFgColor = 0
                show()
            }

            R.id.btnColumnBackgroundColor -> {
                builder = ColorPickerDialog.newBuilder()
                    .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                    .setAllowPresets(true)
                    .setShowAlphaSlider(false)
                    .setDialogId(COLOR_DIALOG_ID_COLUMN_BACKGROUND)
                if (column.columnBgColor != 0) builder.setColor(column.columnBgColor)
                builder.show(this)
            }

            R.id.btnColumnBackgroundColorReset -> {
                column.columnBgColor = 0
                show()
            }

            R.id.btnAcctColor -> {
                ColorPickerDialog.newBuilder()
                    .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                    .setAllowPresets(true)
                    .setShowAlphaSlider(true)
                    .setDialogId(COLOR_DIALOG_ID_ACCT_TEXT)
                    .setColor(column.getAcctColor())
                    .show(this)
            }

            R.id.btnAcctColorReset -> {
                column.acctColor = 0
                show()
            }

            R.id.btnContentColor -> {
                ColorPickerDialog.newBuilder()
                    .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                    .setAllowPresets(true)
                    .setShowAlphaSlider(true)
                    .setDialogId(COLOR_DIALOG_ID_CONTENT_TEXT)
                    .setColor(column.getContentColor())
                    .show(this)
            }

            R.id.btnContentColorReset -> {
                column.contentColor = 0
                show()
            }

            R.id.btnColumnBackgroundImage -> {
                val intent =
                    intentGetContent(
                        false,
                        getString(R.string.pick_image),
                        arrayOf("image/*")
                    )
                arColumnBackgroundImage.launch(intent)
            }

            R.id.btnColumnBackgroundImageReset -> {
                column.columnBgImage = ""
                show()
            }
        }
    }

    override fun onColorSelected(dialogId: Int, @ColorInt newColor: Int) {
        when (dialogId) {
            COLOR_DIALOG_ID_HEADER_BACKGROUND ->
                column.headerBgColor = Color.BLACK or newColor

            COLOR_DIALOG_ID_HEADER_FOREGROUND ->
                column.headerFgColor = Color.BLACK or newColor

            COLOR_DIALOG_ID_COLUMN_BACKGROUND ->
                column.columnBgColor = Color.BLACK or newColor

            COLOR_DIALOG_ID_ACCT_TEXT ->
                column.acctColor = newColor.notZero() ?: 1

            COLOR_DIALOG_ID_CONTENT_TEXT ->
                column.contentColor = newColor.notZero() ?: 1
        }
        show()
    }

    override fun onDialogDismissed(dialogId: Int) {}

    private fun updateBackground(uriArg: Uri) {
        launchMain {
            var resultUri: String? = null
            runApiTask { client ->
                try {
                    val backgroundDir = getBackgroundImageDir(this@ActColumnCustomize)
                    val file =
                        File(backgroundDir, "${column.columnId}:${System.currentTimeMillis()}")
                    val fileUri = Uri.fromFile(file)

                    client.publishApiProgress("loading image from $uriArg")
                    contentResolver.openInputStream(uriArg)?.use { inStream ->
                        FileOutputStream(file).use { outStream ->
                            inStream.copyTo(outStream)
                        }
                    }

                    // リサイズや回転が必要ならする
                    client.publishApiProgress("check resize/rotation…")

                    val size = (max(
                        resources.displayMetrics.widthPixels,
                        resources.displayMetrics.heightPixels
                    ) * 1.5f).toInt()

                    val bitmap = createResizedBitmap(
                        this,
                        fileUri,
                        size,
                        skipIfNoNeedToResizeAndRotate = true,
                    )
                    if (bitmap != null) {
                        try {
                            client.publishApiProgress("save resized(${bitmap.width}x${bitmap.height}) image to $file")
                            FileOutputStream(file).use { os ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    }

                    resultUri = fileUri.toString()
                    TootApiResult()
                } catch (ex: Throwable) {
                    log.trace(ex)
                    TootApiResult(ex.withCaption("can't update background image."))
                }
            }?.let { result ->
                when (val bgUri = resultUri) {
                    null -> showToast(true, result.error ?: "?")
                    else -> {
                        column.columnBgImage = bgUri
                        show()
                    }
                }
            }
        }
    }

    private fun initUI() {
        setContentView(R.layout.act_column_customize)
        App1.initEdgeToEdge(this)

        Styler.fixHorizontalPadding(findViewById(R.id.svContent))

        llColumnHeader = findViewById(R.id.llColumnHeader)
        ivColumnHeader = findViewById(R.id.ivColumnHeader)
        tvColumnName = findViewById(R.id.tvColumnName)
        flColumnBackground = findViewById(R.id.flColumnBackground)
        ivColumnBackground = findViewById(R.id.ivColumnBackground)
        tvSampleAcct = findViewById(R.id.tvSampleAcct)
        tvSampleContent = findViewById(R.id.tvSampleContent)

        findViewById<View>(R.id.btnHeaderBackgroundEdit).setOnClickListener(this)
        findViewById<View>(R.id.btnHeaderBackgroundReset).setOnClickListener(this)
        findViewById<View>(R.id.btnHeaderTextEdit).setOnClickListener(this)
        findViewById<View>(R.id.btnHeaderTextReset).setOnClickListener(this)
        findViewById<View>(R.id.btnColumnBackgroundColor).setOnClickListener(this)
        findViewById<View>(R.id.btnColumnBackgroundColorReset).setOnClickListener(this)
        findViewById<View>(R.id.btnColumnBackgroundImage).setOnClickListener(this)
        findViewById<View>(R.id.btnColumnBackgroundImageReset).setOnClickListener(this)
        findViewById<View>(R.id.btnAcctColor).setOnClickListener(this)
        findViewById<View>(R.id.btnAcctColorReset).setOnClickListener(this)
        findViewById<View>(R.id.btnContentColor).setOnClickListener(this)
        findViewById<View>(R.id.btnContentColorReset).setOnClickListener(this)

        sbColumnBackgroundAlpha = findViewById(R.id.sbColumnBackgroundAlpha)
        sbColumnBackgroundAlpha.max = PROGRESS_MAX

        sbColumnBackgroundAlpha.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (loadingBusy) return
                if (!fromUser) return
                column.columnBgImageAlpha = progress / PROGRESS_MAX.toFloat()
                ivColumnBackground.alpha = column.columnBgImageAlpha
                etAlpha.setText(
                    String.format(
                        defaultLocale(this@ActColumnCustomize),
                        "%.4f",
                        column.columnBgImageAlpha
                    )
                )
            }
        })

        etAlpha = findViewById(R.id.etAlpha)
        etAlpha.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable) {
                if (loadingBusy) return
                try {

                    var f = NumberFormat.getInstance(defaultLocale(this@ActColumnCustomize))
                        .parse(etAlpha.text.toString())?.toFloat()

                    if (f != null && !f.isNaN()) {
                        if (f < 0f) f = 0f
                        if (f > 1f) f = 1f
                        column.columnBgImageAlpha = f
                        ivColumnBackground.alpha = column.columnBgImageAlpha
                        sbColumnBackgroundAlpha.progress = (0.5f + f * PROGRESS_MAX).toInt()
                    }
                } catch (ex: Throwable) {
                    log.e(ex, "alpha parse failed.")
                }
            }
        })

        etAlpha.setOnEditorActionListener { _, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_DONE -> {
                    etAlpha.hideKeyboard()
                    true
                }

                else -> false
            }
        }
    }

    private fun show() {
        try {
            loadingBusy = true

            column.setHeaderBackground(llColumnHeader)

            val c = column.getHeaderNameColor()
            tvColumnName.textColor = c
            ivColumnHeader.setImageResource(column.getIconId())
            ivColumnHeader.imageTintList = ColorStateList.valueOf(c)

            tvColumnName.text = column.getColumnName(false)

            if (column.columnBgColor != 0) {
                flColumnBackground.setBackgroundColor(column.columnBgColor)
            } else {
                ViewCompat.setBackground(flColumnBackground, null)
            }

            var alpha = column.columnBgImageAlpha
            if (alpha.isNaN()) {
                alpha = 1f
                column.columnBgImageAlpha = alpha
            }
            ivColumnBackground.alpha = alpha
            sbColumnBackgroundAlpha.progress = (0.5f + alpha * PROGRESS_MAX).toInt()

            etAlpha.setText(
                String.format(
                    defaultLocale(this@ActColumnCustomize),
                    "%.4f",
                    column.columnBgImageAlpha
                )
            )

            loadImage(ivColumnBackground, column.columnBgImage)

            tvSampleAcct.setTextColor(column.getAcctColor())
            tvSampleContent.setTextColor(column.getContentColor())
        } finally {
            loadingBusy = false
        }
    }

    private fun closeBitmaps() {
        try {
            ivColumnBackground.setImageDrawable(null)
            lastImageUri = null

            lastImageBitmap?.recycle()
            lastImageBitmap = null
        } catch (ex: Throwable) {
            log.trace(ex)
        }
    }

    private fun loadImage(ivColumnBackground: ImageView, url: String) {
        try {
            if (url.isEmpty()) {
                closeBitmaps()
                return
            } else if (url == lastImageUri) {
                // 今表示してるのと同じ
                return
            }

            // 直前のBitmapを掃除する
            closeBitmaps()

            val uri = url.mayUri() ?: return

            // 画像をロードして、成功したら表示してURLを覚える
            val resizeMax = (0.5f + 64f * density).toInt()
            lastImageBitmap = createResizedBitmap(this, uri, resizeMax)
            if (lastImageBitmap != null) {
                ivColumnBackground.setImageBitmap(lastImageBitmap)
                lastImageUri = url
            }
        } catch (ex: Throwable) {
            log.trace(ex)
        }
    }
}
