package jp.juggler.subwaytooter

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.TextView

import com.jrummyapps.android.colorpicker.ColorPickerDialog
import com.jrummyapps.android.colorpicker.ColorPickerDialogListener

import org.json.JSONException
import org.json.JSONObject

import jp.juggler.subwaytooter.table.HighlightWord
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils

class ActHighlightWordEdit : AppCompatActivity(), View.OnClickListener, ColorPickerDialogListener, CompoundButton.OnCheckedChangeListener {
	
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
			} catch(ex : JSONException) {
				throw RuntimeException(ex)
			}
		}
	}
	
	lateinit internal var item : HighlightWord
	
	lateinit private var tvName : TextView
	lateinit private var swSound : Switch
	
	private var bBusy = false
	
	private fun makeResult() {
		try {
			val data = Intent()
			data.putExtra(EXTRA_ITEM, item.encodeJson().toString())
			setResult(Activity.RESULT_OK, data)
		} catch(ex : JSONException) {
			throw RuntimeException(ex)
		}
		
	}
	
	override fun onBackPressed() {
		makeResult()
		super.onBackPressed()
	}
	
	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		App1.setActivityTheme(this, false)
		initUI()
		
		item = HighlightWord(JSONObject(
			if(savedInstanceState != null) savedInstanceState.getString(EXTRA_ITEM)
			else intent.getStringExtra(EXTRA_ITEM)
		))
		
		showSampleText()
		
	}
	
	override fun onActivityResult(requestCode : Int, resultCode : Int, data : Intent) {
		when(requestCode) {
			
			REQUEST_CODE_NOTIFICATION_SOUND -> {
				if(resultCode == Activity.RESULT_OK) {
					// RINGTONE_PICKERからの選択されたデータを取得する
					val uri = Utils.getExtraObject(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
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
		
		tvName = findViewById(R.id.tvName)
		swSound = findViewById(R.id.swSound)
		swSound.setOnCheckedChangeListener(this)
		
		findViewById<View>(R.id.btnTextColorEdit).setOnClickListener(this)
		findViewById<View>(R.id.btnTextColorReset).setOnClickListener(this)
		findViewById<View>(R.id.btnBackgroundColorEdit).setOnClickListener(this)
		findViewById<View>(R.id.btnBackgroundColorReset).setOnClickListener(this)
		findViewById<View>(R.id.btnNotificationSoundEdit).setOnClickListener(this)
		findViewById<View>(R.id.btnNotificationSoundReset).setOnClickListener(this)
	}
	
	private fun showSampleText() {
		bBusy = true
		try {
			
			swSound.isChecked = item.sound_type != HighlightWord.SOUND_TYPE_NONE
			
			tvName.text = item.name
			
			var c = item.color_bg
			if(c == 0) {
				tvName.setBackgroundColor(0)
			} else {
				tvName.setBackgroundColor(c)
			}
			
			c = item.color_fg
			if(c == 0) {
				tvName.setTextColor(Styler.getAttributeColor(this, android.R.attr.textColorPrimary))
			} else {
				tvName.setTextColor(c)
			}
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
			
			R.id.btnBackgroundColorEdit -> openColorPicker(COLOR_DIALOG_ID_BACKGROUND, item.color_bg)
			
			R.id.btnBackgroundColorReset -> {
				item.color_bg = 0
				showSampleText()
			}
			
			R.id.btnNotificationSoundEdit -> openNotificationSoundPicker()
			
			R.id.btnNotificationSoundReset -> {
				item.sound_uri = null
				item.sound_type = if(swSound.isChecked) HighlightWord.SOUND_TYPE_DEFAULT else HighlightWord.SOUND_TYPE_NONE
			}
		}
		
	}
	
	override fun onCheckedChanged(buttonView : CompoundButton, isChecked : Boolean) {
		if(bBusy) return
		
		if(! isChecked) {
			item.sound_type = HighlightWord.SOUND_TYPE_NONE
		} else {
			
			item.sound_type = if(item.sound_uri?.isEmpty() != false ) HighlightWord.SOUND_TYPE_DEFAULT else HighlightWord.SOUND_TYPE_CUSTOM
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
			COLOR_DIALOG_ID_TEXT -> item.color_fg = - 0x1000000 or color
			COLOR_DIALOG_ID_BACKGROUND -> item.color_bg = if(color == 0) 0x01000000 else color
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
		try {
			val sound_uri = item.sound_uri
			val uri = if(sound_uri?.isEmpty()!= false ) null else Uri.parse(sound_uri)
			if(uri != null) {
				intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri)
			}
		} catch(ignored : Throwable) {
		}
		
		val chooser = Intent.createChooser(intent, getString(R.string.notification_sound))
		startActivityForResult(chooser, REQUEST_CODE_NOTIFICATION_SOUND)
	}
	

}
