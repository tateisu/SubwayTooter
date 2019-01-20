package jp.juggler.subwaytooter

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.jrummyapps.android.colorpicker.ColorPickerDialog
import com.jrummyapps.android.colorpicker.ColorPickerDialogListener
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.util.*
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.textColor

class ActNickname : AppCompatActivity(), View.OnClickListener, ColorPickerDialogListener {
	
	companion object {
		
		internal const val EXTRA_ACCT = "acct"
		internal const val EXTRA_SHOW_NOTIFICATION_SOUND = "show_notification_sound"
		internal const val REQUEST_CODE_NOTIFICATION_SOUND = 2
		
		fun open(
			activity : Activity,
			full_acct : String,
			bShowNotificationSound : Boolean,
			requestCode : Int
		) {
			val intent = Intent(activity, ActNickname::class.java)
			intent.putExtra(EXTRA_ACCT, full_acct)
			intent.putExtra(EXTRA_SHOW_NOTIFICATION_SOUND, bShowNotificationSound)
			activity.startActivityForResult(intent, requestCode)
		}
		
	}
	
	private var show_notification_sound : Boolean = false
	private lateinit var acct : String
	private var color_fg : Int = 0
	private var color_bg : Int = 0
	private var notification_sound_uri : String? = null
	
	private lateinit var tvPreview : TextView
	private lateinit var tvAcct : TextView
	private lateinit var etNickname : EditText
	private lateinit var btnTextColorEdit : View
	private lateinit var btnTextColorReset : View
	private lateinit var btnBackgroundColorEdit : View
	private lateinit var btnBackgroundColorReset : View
	private lateinit var btnSave : View
	private lateinit var btnDiscard : View
	private lateinit var btnNotificationSoundEdit : Button
	private lateinit var btnNotificationSoundReset : Button
	
	private var bLoading = false
	
	override fun onBackPressed() {
		setResult(RESULT_OK)
		super.onBackPressed()
	}
	
	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		App1.setActivityTheme(this, false)
		
		val intent = intent
		this.acct = intent.getStringExtra(EXTRA_ACCT)
		this.show_notification_sound = intent.getBooleanExtra(EXTRA_SHOW_NOTIFICATION_SOUND, false)
		
		initUI()
		
		load()
	}
	
	private fun initUI() {
		
		title = getString(
			if(show_notification_sound)
				R.string.nickname_and_color_and_notification_sound
			else
				R.string.nickname_and_color
		)
		setContentView(R.layout.act_nickname)
		
		Styler.fixHorizontalPadding(findViewById(R.id.llContent))
		
		tvPreview = findViewById(R.id.tvPreview)
		tvAcct = findViewById(R.id.tvAcct)
		
		etNickname = findViewById(R.id.etNickname)
		btnTextColorEdit = findViewById(R.id.btnTextColorEdit)
		btnTextColorReset = findViewById(R.id.btnTextColorReset)
		btnBackgroundColorEdit = findViewById(R.id.btnBackgroundColorEdit)
		btnBackgroundColorReset = findViewById(R.id.btnBackgroundColorReset)
		btnSave = findViewById(R.id.btnSave)
		btnDiscard = findViewById(R.id.btnDiscard)
		
		etNickname = findViewById(R.id.etNickname)
		btnTextColorEdit.setOnClickListener(this)
		btnTextColorReset.setOnClickListener(this)
		btnBackgroundColorEdit.setOnClickListener(this)
		btnBackgroundColorReset.setOnClickListener(this)
		btnSave.setOnClickListener(this)
		btnDiscard.setOnClickListener(this)
		
		btnNotificationSoundEdit = findViewById(R.id.btnNotificationSoundEdit)
		btnNotificationSoundReset = findViewById(R.id.btnNotificationSoundReset)
		btnNotificationSoundEdit.setOnClickListener(this)
		btnNotificationSoundReset.setOnClickListener(this)
		
		val bBefore8 = Build.VERSION.SDK_INT < 26
		btnNotificationSoundEdit.isEnabled = bBefore8
		btnNotificationSoundReset.isEnabled = bBefore8
		
		etNickname.addTextChangedListener(object : TextWatcher {
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
				show()
			}
		})
	}
	
	private fun load() {
		bLoading = true
		
		findViewById<View>(R.id.llNotificationSound).visibility =
			if(show_notification_sound) View.VISIBLE else View.GONE
		
		tvAcct.text = acct
		
		val ac = AcctColor.load(acct)
		color_bg = ac.color_bg
		color_fg = ac.color_fg
		etNickname.setText(if(ac.nickname == null) "" else ac.nickname)
		notification_sound_uri = ac.notification_sound
		
		bLoading = false
		show()
	}
	
	private fun save() {
		if(bLoading) return
		AcctColor(
			acct,
			etNickname.text.toString().trim { it <= ' ' },
			color_fg,
			color_bg,
			notification_sound_uri
		).save(System.currentTimeMillis())
	}
	
	private fun show() {
		val s = etNickname.text.toString().trim { it <= ' ' }
		tvPreview.text = s.notEmpty() ?: acct
		tvPreview.textColor = color_fg.notZero() ?: getAttributeColor(this, R.attr.colorTimeSmall)
		tvPreview.backgroundColor = color_bg
	}
	
	override fun onClick(v : View) {
		val builder : ColorPickerDialog.Builder
		when(v.id) {
			R.id.btnTextColorEdit -> {
				etNickname.hideKeyboard()
				builder = ColorPickerDialog.newBuilder()
					.setDialogType(ColorPickerDialog.TYPE_CUSTOM)
					.setAllowPresets(true)
					.setShowAlphaSlider(false)
					.setDialogId(1)
				if(color_fg != 0) builder.setColor(color_fg)
				builder.show(this)
			}
			
			R.id.btnTextColorReset -> {
				color_fg = 0
				show()
			}
			
			R.id.btnBackgroundColorEdit -> {
				etNickname.hideKeyboard()
				builder = ColorPickerDialog.newBuilder()
					.setDialogType(ColorPickerDialog.TYPE_CUSTOM)
					.setAllowPresets(true)
					.setShowAlphaSlider(false)
					.setDialogId(2)
				if(color_bg != 0) builder.setColor(color_bg)
				builder.show(this)
			}
			
			R.id.btnBackgroundColorReset -> {
				color_bg = 0
				show()
			}
			
			R.id.btnSave -> {
				save()
				setResult(Activity.RESULT_OK)
				finish()
			}
			
			R.id.btnDiscard -> {
				setResult(Activity.RESULT_CANCELED)
				finish()
			}
			
			R.id.btnNotificationSoundEdit -> openNotificationSoundPicker()
			
			R.id.btnNotificationSoundReset -> notification_sound_uri = ""
		}
	}
	
	override fun onColorSelected(dialogId : Int, @ColorInt color : Int) {
		when(dialogId) {
			1 -> color_fg = - 0x1000000 or color
			2 -> color_bg = - 0x1000000 or color
		}
		show()
	}
	
	override fun onDialogDismissed(dialogId : Int) {}
	
	private fun openNotificationSoundPicker() {
		val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
		intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
		intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, R.string.notification_sound)
		intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
		intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
		notification_sound_uri.mayUri()?.let {
			intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it)
		}
		
		val chooser = Intent.createChooser(intent, getString(R.string.notification_sound))
		startActivityForResult(chooser, REQUEST_CODE_NOTIFICATION_SOUND)
	}
	
	override fun onActivityResult(requestCode : Int, resultCode : Int, data : Intent?) {
		if(resultCode == Activity.RESULT_OK && requestCode == REQUEST_CODE_NOTIFICATION_SOUND) {
			// RINGTONE_PICKERからの選択されたデータを取得する
			val uri = data?.extras?.get(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
			if(uri is Uri) {
				notification_sound_uri = uri.toString()
			}
		}
		super.onActivityResult(requestCode, resultCode, data)
	}
	
}
