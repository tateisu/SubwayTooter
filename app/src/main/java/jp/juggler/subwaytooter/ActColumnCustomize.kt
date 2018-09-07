package jp.juggler.subwaytooter

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.v4.view.ViewCompat
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView

import com.jrummyapps.android.colorpicker.ColorPickerDialog
import com.jrummyapps.android.colorpicker.ColorPickerDialogListener
import jp.juggler.subwaytooter.util.*
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileOutputStream

import java.text.NumberFormat
import java.util.Locale

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
	
	private var content_color_default : Int = 0
	
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
				builder = ColorPickerDialog.newBuilder()
					.setDialogType(ColorPickerDialog.TYPE_CUSTOM)
					.setAllowPresets(true)
					.setShowAlphaSlider(false)
					.setDialogId(COLOR_DIALOG_ID_HEADER_BACKGROUND)
				if(column.header_bg_color != 0) builder.setColor(column.header_bg_color)
				builder.show(this)
			}
			
			R.id.btnHeaderBackgroundReset -> {
				column.header_bg_color = 0
				show()
			}
			
			R.id.btnHeaderTextEdit -> {
				builder = ColorPickerDialog.newBuilder()
					.setDialogType(ColorPickerDialog.TYPE_CUSTOM)
					.setAllowPresets(true)
					.setShowAlphaSlider(false)
					.setDialogId(COLOR_DIALOG_ID_HEADER_FOREGROUND)
				if(column.header_fg_color != 0) builder.setColor(column.header_fg_color)
				builder.show(this)
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
				builder = ColorPickerDialog.newBuilder()
					.setDialogType(ColorPickerDialog.TYPE_CUSTOM)
					.setAllowPresets(true)
					.setShowAlphaSlider(true)
					.setDialogId(COLOR_DIALOG_ID_ACCT_TEXT)
				if(column.acct_color != 0) builder.setColor(column.acct_color)
				builder.show(this)
			}
			
			R.id.btnAcctColorReset -> {
				column.acct_color = 0
				show()
			}
			
			R.id.btnContentColor -> {
				builder = ColorPickerDialog.newBuilder()
					.setDialogType(ColorPickerDialog.TYPE_CUSTOM)
					.setAllowPresets(true)
					.setShowAlphaSlider(true)
					.setDialogId(COLOR_DIALOG_ID_CONTENT_TEXT)
				if(column.content_color != 0) builder.setColor(column.content_color)
				builder.show(this)
			}
			
			R.id.btnContentColorReset -> {
				column.content_color = 0
				show()
			}
			
			R.id.btnColumnBackgroundImage -> {
				val intent = intentOpenDocument("image/*")
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
				column.acct_color = if(colorSelected == 0) 1 else colorSelected
			}
			
			COLOR_DIALOG_ID_CONTENT_TEXT -> {
				column.content_color = if(colorSelected == 0) 1 else colorSelected
			}
		}
		show()
	}
	
	override fun onDialogDismissed(dialogId : Int) {}
	
	override fun onActivityResult(requestCode : Int, resultCode : Int, data : Intent?) {
		if(requestCode == REQUEST_CODE_PICK_BACKGROUND && data != null && resultCode == RESULT_OK) {
			data.handleGetContentResult(contentResolver).firstOrNull()?.let { pair->
				try{
					val backgroundDir = getDir(Column.DIR_BACKGROUND_IMAGE, Context.MODE_PRIVATE)
					val file = File(backgroundDir,column.column_id)
					FileOutputStream(file).use{ outStream->
						contentResolver.openInputStream(pair.first).use{ inStream->
							IOUtils.copy(inStream,outStream)
						}
					}
					column.column_bg_image = Uri.fromFile(file).toString()
					show()
				}catch(ex:Throwable){
					showToast(this@ActColumnCustomize,true,ex.withCaption("can't update background image."))
				}
			}
		}
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
		
		
		content_color_default = tvSampleContent.textColors.defaultColor
		
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
			var c = column.header_bg_color
			if(c == 0) {
				llColumnHeader.setBackgroundResource(R.drawable.btn_bg_ddd)
			} else {
				ViewCompat.setBackground(
					llColumnHeader, Styler.getAdaptiveRippleDrawable(
						c,
						if(column.header_fg_color != 0)
							column.header_fg_color
						else
							Styler.getAttributeColor(this, R.attr.colorRippleEffect)
					)
				)
			}
			
			c = column.header_fg_color
			if(c == 0) {
				tvColumnName.setTextColor(
					Styler.getAttributeColor(
						this,
						android.R.attr.textColorPrimary
					)
				)
				Styler.setIconDefaultColor(
					this,
					ivColumnHeader,
					column.getIconAttrId(column.column_type)
				)
			} else {
				tvColumnName.setTextColor(c)
				Styler.setIconCustomColor(
					this,
					ivColumnHeader,
					c,
					column.getIconAttrId(column.column_type)
				)
			}
			
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
			
			c = if(column.acct_color != 0) column.acct_color else Styler.getAttributeColor(
				this,
				R.attr.colorTimeSmall
			)
			tvSampleAcct.setTextColor(c)
			
			c = if(column.content_color != 0) column.content_color else content_color_default
			tvSampleContent.setTextColor(c)
			
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
			
			// 画像をロードして、成功したら表示してURLを覚える
			val resize_max = (0.5f + 64f * density).toInt()
			val uri = Uri.parse(url)
			last_image_bitmap = createResizedBitmap( this, uri, resize_max )
			if(last_image_bitmap != null) {
				ivColumnBackground.setImageBitmap(last_image_bitmap)
				last_image_uri = url
			}
			
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
	}
	
}
