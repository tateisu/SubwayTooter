package jp.juggler.subwaytooter

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.annotation.ColorInt
import androidx.core.view.ViewCompat
import androidx.appcompat.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import com.jrummyapps.android.colorpicker.ColorPickerDialog
import com.jrummyapps.android.colorpicker.ColorPickerDialogListener
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootTask
import jp.juggler.subwaytooter.api.TootTaskRunner
import jp.juggler.util.createResizedBitmap
import jp.juggler.util.*
import org.apache.commons.io.IOUtils
import org.jetbrains.anko.textColor
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.*
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
		
		internal const val REQUEST_CODE_PICK_BACKGROUND = 1
		
		internal const val PROGRESS_MAX = 65536
		
		fun open(activity : ActMain, idx : Int, request_code : Int) {
			val intent = Intent(activity, ActColumnCustomize::class.java)
			intent.putExtra(EXTRA_COLUMN_INDEX, idx)
			activity.startActivityForResult(intent, request_code)
			
		}
		
	}
	
	private var column_index : Int = 0
	internal lateinit var column : Column
	internal lateinit var app_state : AppState
	internal var density : Float = 0f
	
	private lateinit var flColumnBackground : View
	internal lateinit var ivColumnBackground : ImageView
	internal lateinit var sbColumnBackgroundAlpha : SeekBar
	private lateinit var llColumnHeader : View
	private lateinit var ivColumnHeader : ImageView
	private lateinit var tvColumnName : TextView
	internal lateinit var etAlpha : EditText
	private lateinit var tvSampleAcct : TextView
	private lateinit var tvSampleContent : TextView
	
	internal var loading_busy : Boolean = false
	
	private var last_image_uri : String? = null
	private var last_image_bitmap : Bitmap? = null
	
	override fun onBackPressed() {
		makeResult()
		super.onBackPressed()
	}
	
	private fun makeResult() {
		val data = Intent()
		data.putExtra(EXTRA_COLUMN_INDEX, column_index)
		setResult(RESULT_OK, data)
	}
	
	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		App1.setActivityTheme(this, false)
		initUI()
		
		app_state = App1.getAppState(this)
		density = app_state.density
		column_index = intent.getIntExtra(EXTRA_COLUMN_INDEX, 0)
		column = app_state.column_list[column_index]
		
		show()
	}
	
	override fun onDestroy() {
		closeBitmaps()
		super.onDestroy()
	}
	
	override fun onClick(v : View) {
		
		val builder : ColorPickerDialog.Builder
		
		when(v.id) {
			
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
				column.header_bg_color = 0
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
				column.header_fg_color = 0
				show()
			}
			
			R.id.btnColumnBackgroundColor -> {
				builder = ColorPickerDialog.newBuilder()
					.setDialogType(ColorPickerDialog.TYPE_CUSTOM)
					.setAllowPresets(true)
					.setShowAlphaSlider(false)
					.setDialogId(COLOR_DIALOG_ID_COLUMN_BACKGROUND)
				if(column.column_bg_color != 0) builder.setColor(column.column_bg_color)
				builder.show(this)
			}
			
			R.id.btnColumnBackgroundColorReset -> {
				column.column_bg_color = 0
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
				column.acct_color = 0
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
				column.content_color = 0
				show()
			}
			
			R.id.btnColumnBackgroundImage -> {
				val intent = intentGetContent(false, getString(R.string.pick_image), arrayOf("image/*"))
				startActivityForResult(intent, REQUEST_CODE_PICK_BACKGROUND)
			}
			
			R.id.btnColumnBackgroundImageReset -> {
				column.column_bg_image = ""
				show()
			}
		}
	}
	
	// 0xFF000000 と書きたいがkotlinではこれはlong型定数になってしまう
	private val colorFF000000 : Int = (0xff shl 24)
	
	override fun onColorSelected(dialogId : Int, @ColorInt colorSelected : Int) {
		when(dialogId) {
			COLOR_DIALOG_ID_HEADER_BACKGROUND -> column.header_bg_color = colorFF000000 or
				colorSelected
			COLOR_DIALOG_ID_HEADER_FOREGROUND -> column.header_fg_color = colorFF000000 or
				colorSelected
			COLOR_DIALOG_ID_COLUMN_BACKGROUND -> column.column_bg_color = colorFF000000 or
				colorSelected
			
			COLOR_DIALOG_ID_ACCT_TEXT -> {
				column.acct_color = colorSelected.notZero() ?: 1
			}
			
			COLOR_DIALOG_ID_CONTENT_TEXT -> {
				column.content_color = colorSelected.notZero() ?: 1
			}
		}
		show()
	}
	
	override fun onDialogDismissed(dialogId : Int) {}
	
	override fun onActivityResult(requestCode : Int, resultCode : Int, data : Intent?) {
		if(requestCode == REQUEST_CODE_PICK_BACKGROUND && data != null && resultCode == RESULT_OK) {
			data.handleGetContentResult(contentResolver).firstOrNull()
				?.uri?.let { updateBackground(it) }
		}
	}
	
	private fun updateBackground(uriArg : Uri) {
		TootTaskRunner(this).run(object : TootTask {
			var bgUri : String? = null
			
			override fun background(client : TootApiClient) : TootApiResult? {
				try {
					val backgroundDir = Column.getBackgroundImageDir(this@ActColumnCustomize)
					val file =
						File(backgroundDir, "${column.column_id}:${System.currentTimeMillis()}")
					val fileUri = Uri.fromFile(file)
					
					client.publishApiProgress("loading image from ${uriArg}")
					contentResolver.openInputStream(uriArg).use { inStream ->
						FileOutputStream(file).use { outStream ->
							IOUtils.copy(inStream, outStream)
						}
					}
					
					// リサイズや回転が必要ならする
					client.publishApiProgress("check resize/rotation…")
					
					val size = (max(
						resources.displayMetrics.widthPixels,
						resources.displayMetrics.heightPixels
					) * 1.5f).toInt()
					
					val bitmap = createResizedBitmap(
						this@ActColumnCustomize,
						fileUri,
						size,
						skipIfNoNeedToResizeAndRotate = true
					)
					if(bitmap != null) {
						try {
							client.publishApiProgress("save resized(${bitmap.width}x${bitmap.height}) image to ${file}")
							FileOutputStream(file).use { os ->
								bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
							}
						} finally {
							bitmap.recycle()
						}
					}
					
					bgUri = fileUri.toString()
					return TootApiResult()
				} catch(ex : Throwable) {
					log.trace(ex)
					return TootApiResult(ex.withCaption("can't update background image."))
				}
			}
			
			override fun handleResult(result : TootApiResult?) {
				val bgUri = this.bgUri
				when {
					result == null -> return
					
					bgUri != null -> {
						column.column_bg_image = bgUri
						show()
					}
					
					else -> showToast(this@ActColumnCustomize, true, result.error ?: "?")
				}
			}
		})
	}
	
	private fun initUI() {
		setContentView(R.layout.act_column_customize)
		
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
			override fun onStartTrackingTouch(seekBar : SeekBar) {}
			
			override fun onStopTrackingTouch(seekBar : SeekBar) {
			
			}
			
			override fun onProgressChanged(seekBar : SeekBar, progress : Int, fromUser : Boolean) {
				if(loading_busy) return
				if(! fromUser) return
				column.column_bg_image_alpha = progress / PROGRESS_MAX.toFloat()
				ivColumnBackground.alpha = column.column_bg_image_alpha
				etAlpha.setText(
					String.format(
						Locale.getDefault(),
						"%.4f",
						column.column_bg_image_alpha
					)
				)
			}
			
		})
		
		etAlpha = findViewById(R.id.etAlpha)
		etAlpha.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(
				s : CharSequence,
				start : Int,
				count : Int,
				after : Int
			) {
			
			}
			
			override fun onTextChanged(s : CharSequence, start : Int, before : Int, count : Int) {
			
			}
			
			override fun afterTextChanged(s : Editable) {
				if(loading_busy) return
				try {
					var f =
						NumberFormat.getInstance(Locale.getDefault()).parse(etAlpha.text.toString())
							.toFloat()
					if(! f.isNaN()) {
						if(f < 0f) f = 0f
						if(f > 1f) f = 1f
						column.column_bg_image_alpha = f
						ivColumnBackground.alpha = column.column_bg_image_alpha
						sbColumnBackgroundAlpha.progress = (0.5f + f * PROGRESS_MAX).toInt()
					}
				} catch(ex : Throwable) {
					log.e(ex, "alpha parse failed.")
				}
				
			}
		})
	}
	
	private fun show() {
		try {
			loading_busy = true
			
			column.setHeaderBackground(llColumnHeader)
			
			val c = column.getHeaderNameColor()
			tvColumnName.textColor = c
			ivColumnHeader.setImageResource(column.getIconId())
			ivColumnHeader.imageTintList = ColorStateList.valueOf(c)
			
			tvColumnName.text = column.getColumnName(false)
			
			if(column.column_bg_color != 0) {
				flColumnBackground.setBackgroundColor(column.column_bg_color)
			} else {
				ViewCompat.setBackground(flColumnBackground, null)
			}
			
			var alpha = column.column_bg_image_alpha
			if(alpha.isNaN()) {
				alpha = 1f
				column.column_bg_image_alpha = alpha
			}
			ivColumnBackground.alpha = alpha
			sbColumnBackgroundAlpha.progress = (0.5f + alpha * PROGRESS_MAX).toInt()
			
			etAlpha.setText(
				String.format(
					Locale.getDefault(),
					"%.4f",
					column.column_bg_image_alpha
				)
			)
			
			loadImage(ivColumnBackground, column.column_bg_image)
			
			tvSampleAcct.setTextColor(column.getAcctColor())
			tvSampleContent.setTextColor(column.getContentColor())
			
		} finally {
			loading_busy = false
		}
	}
	
	private fun closeBitmaps() {
		try {
			ivColumnBackground.setImageDrawable(null)
			last_image_uri = null
			
			last_image_bitmap?.recycle()
			last_image_bitmap = null
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
	}
	
	private fun loadImage(ivColumnBackground : ImageView, url : String) {
		try {
			if(url.isEmpty()) {
				closeBitmaps()
				return
			} else if(url == last_image_uri) {
				// 今表示してるのと同じ
				return
			}
			
			// 直前のBitmapを掃除する
			closeBitmaps()
			
			val uri = url.mayUri() ?: return
			
			// 画像をロードして、成功したら表示してURLを覚える
			val resize_max = (0.5f + 64f * density).toInt()
			last_image_bitmap = createResizedBitmap(this, uri, resize_max)
			if(last_image_bitmap != null) {
				ivColumnBackground.setImageBitmap(last_image_bitmap)
				last_image_uri = url
			}
			
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
	}
	
}
