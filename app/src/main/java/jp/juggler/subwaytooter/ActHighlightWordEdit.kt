package jp.juggler.subwaytooter

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.jrummyapps.android.colorpicker.ColorPickerDialog
import com.jrummyapps.android.colorpicker.ColorPickerDialogListener
import jp.juggler.subwaytooter.table.HighlightWord
import jp.juggler.util.*
import org.jetbrains.anko.textColor

class ActHighlightWordEdit
	: AppCompatActivity(),
	View.OnClickListener,
	ColorPickerDialogListener,
	CompoundButton.OnCheckedChangeListener {
	
	companion object {
		internal val log = LogCategory("ActHighlightWordEdit")
		
		const val EXTRA_ITEM = "item"
		
		internal const val REQUEST_CODE_NOTIFICATION_SOUND = 2
		
		private const val COLOR_DIALOG_ID_TEXT = 1
		private const val COLOR_DIALOG_ID_BACKGROUND = 2
		
		fun open(activity : Activity, request_code : Int, item : HighlightWord) {
			try {
				val intent = Intent(activity, ActHighlightWordEdit::class.java)
				intent.putExtra(EXTRA_ITEM, item.encodeJson().toString())
				activity.startActivityForResult(intent, request_code)
			} catch(ex : JsonException) {
				throw RuntimeException(ex)
			}
		}
	}
	
	internal lateinit var item : HighlightWord
	
	private lateinit var tvName : TextView
	private lateinit var swSound : SwitchCompat
	private lateinit var swSpeech : SwitchCompat
	
	private var bBusy = false
	
	private fun makeResult() {
		try {
			val data = Intent()
			data.putExtra(EXTRA_ITEM, item.encodeJson().toString())
			setResult(Activity.RESULT_OK, data)
		} catch(ex : JsonException) {
			throw RuntimeException(ex)
		}
	}
	
	override fun onBackPressed() {
		makeResult()
		super.onBackPressed()
	}
	
	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		App1.setActivityTheme(this)
		initUI()
		
		item = HighlightWord(
			(savedInstanceState?.getString(EXTRA_ITEM) ?: intent.getStringExtra(EXTRA_ITEM))
				.decodeJsonObject()
		)
		showSampleText()
	}
	
	override fun onActivityResult(requestCode : Int, resultCode : Int, data : Intent?) {
		when(requestCode) {
			
			REQUEST_CODE_NOTIFICATION_SOUND -> {
				if(resultCode == Activity.RESULT_OK) {
					// RINGTONE_PICKERからの選択されたデータを取得する
					val uri = data?.extras?.get(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
					if(uri is Uri) {
						item.sound_uri = uri.toString()
						item.sound_type = HighlightWord.SOUND_TYPE_CUSTOM
						swSound.isChecked = true
					}
				}
			}
			
			else -> super.onActivityResult(requestCode, resultCode, data)
		}
	}
	
	private fun initUI() {
		setContentView(R.layout.act_highlight_edit)
		App1.initEdgeToEdge(this)
		
		tvName = findViewById(R.id.tvName)
		swSound = findViewById(R.id.swSound)
		swSound.setOnCheckedChangeListener(this)
		
		swSpeech = findViewById(R.id.swSpeech)
		swSpeech.setOnCheckedChangeListener(this)
		
		intArrayOf(
			R.id.btnTextColorEdit,
			R.id.btnTextColorReset,
			R.id.btnBackgroundColorEdit,
			R.id.btnBackgroundColorReset,
			R.id.btnNotificationSoundEdit,
			R.id.btnNotificationSoundReset
		).forEach {
			findViewById<View>(it)?.setOnClickListener(this)
		}
	}
	
	private fun showSampleText() {
		bBusy = true
		try {
			
			swSound.isChecked = item.sound_type != HighlightWord.SOUND_TYPE_NONE
			
			swSpeech.isChecked = item.speech != 0
			
			tvName.text = item.name
			tvName.setBackgroundColor(item.color_bg) // may 0
			tvName.textColor = item.color_fg.notZero()
				?: getAttributeColor(this, android.R.attr.textColorPrimary)
			
		} finally {
			bBusy = false
		}
	}
	
	override fun onClick(v : View) {
		
		when(v.id) {
			
			R.id.btnTextColorEdit -> openColorPicker(COLOR_DIALOG_ID_TEXT, item.color_fg)
			
			R.id.btnTextColorReset -> {
				item.color_fg = 0
				showSampleText()
			}
			
			R.id.btnBackgroundColorEdit -> openColorPicker(
				COLOR_DIALOG_ID_BACKGROUND,
				item.color_bg
			)
			
			R.id.btnBackgroundColorReset -> {
				item.color_bg = 0
				showSampleText()
			}
			
			R.id.btnNotificationSoundEdit -> openNotificationSoundPicker()
			
			R.id.btnNotificationSoundReset -> {
				item.sound_uri = null
				item.sound_type =
					if(swSound.isChecked) HighlightWord.SOUND_TYPE_DEFAULT else HighlightWord.SOUND_TYPE_NONE
			}
		}
		
	}
	
	override fun onCheckedChanged(buttonView : CompoundButton, isChecked : Boolean) {
		if(bBusy) return
		
		when(buttonView.id) {
			R.id.swSound -> {
				if(! isChecked) {
					item.sound_type = HighlightWord.SOUND_TYPE_NONE
				} else {
					item.sound_type =
						if(item.sound_uri?.isEmpty() != false) HighlightWord.SOUND_TYPE_DEFAULT else HighlightWord.SOUND_TYPE_CUSTOM
				}
			}
			
			R.id.swSpeech -> {
				item.speech = when(isChecked) {
					false -> 0
					else -> 1
				}
			}
		}
	}
	
	private fun openColorPicker(id : Int, initial_color : Int) {
		val builder = ColorPickerDialog.newBuilder()
			.setDialogType(ColorPickerDialog.TYPE_CUSTOM)
			.setAllowPresets(true)
			.setShowAlphaSlider(id == COLOR_DIALOG_ID_BACKGROUND)
			.setDialogId(id)
		
		if(initial_color != 0) builder.setColor(initial_color)
		
		builder.show(this)
		
	}
	
	override fun onDialogDismissed(dialogId : Int) {}
	
	override fun onColorSelected(dialogId : Int, color : Int) {
		when(dialogId) {
			COLOR_DIALOG_ID_TEXT -> item.color_fg = color or Color.BLACK
			COLOR_DIALOG_ID_BACKGROUND -> item.color_bg = color.notZero() ?: 0x01000000
		}
		showSampleText()
	}
	
	//////////////////////////////////////////////////////////////////
	
	private fun openNotificationSoundPicker() {
		val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
		intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
		intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, R.string.notification_sound)
		intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
		intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
		
		item.sound_uri.mayUri()?.let { uri ->
			intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri)
		}
		
		val chooser = Intent.createChooser(intent, getString(R.string.notification_sound))
		startActivityForResult(chooser, REQUEST_CODE_NOTIFICATION_SOUND)
	}
	
}
