package jp.juggler.subwaytooter

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import com.jrummyapps.android.colorpicker.ColorPickerDialog
import com.jrummyapps.android.colorpicker.ColorPickerDialogListener
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.util.*
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.textColor

class ActNickname : AppCompatActivity(), View.OnClickListener, ColorPickerDialogListener {

    companion object {
        private val log = LogCategory("ActNickname")

        internal const val EXTRA_ACCT_ASCII = "acctAscii"
        internal const val EXTRA_ACCT_PRETTY = "acctPretty"
        internal const val EXTRA_SHOW_NOTIFICATION_SOUND = "show_notification_sound"

        fun createIntent(
            activity: Activity,
            fullAcct: Acct,
            bShowNotificationSound: Boolean,
        ) = Intent(activity, ActNickname::class.java).apply {
            putExtra(EXTRA_ACCT_ASCII, fullAcct.ascii)
            putExtra(EXTRA_ACCT_PRETTY, fullAcct.pretty)
            putExtra(EXTRA_SHOW_NOTIFICATION_SOUND, bShowNotificationSound)
        }
    }

    private lateinit var tvPreview: TextView
    private lateinit var tvAcct: TextView
    private lateinit var etNickname: EditText
    private lateinit var btnTextColorEdit: View
    private lateinit var btnTextColorReset: View
    private lateinit var btnBackgroundColorEdit: View
    private lateinit var btnBackgroundColorReset: View
    private lateinit var btnSave: View
    private lateinit var btnDiscard: View
    private lateinit var btnNotificationSoundEdit: Button
    private lateinit var btnNotificationSoundReset: Button

    private var showNotificationSound = false
    private lateinit var acctAscii: String
    private lateinit var acctPretty: String
    private var colorFg = 0
    private var colorBg = 0
    private var notificationSoundUri: String? = null
    private var loadingBusy = false

    private val arNotificationSound = activityResultHandler { ar ->
        if (ar?.resultCode == RESULT_OK) {
            // RINGTONE_PICKERからの選択されたデータを取得する
            val uri = ar.data?.extras?.get(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri is Uri) {
                notificationSoundUri = uri.toString()
            }
        }
    }

    override fun onBackPressed() {
        setResult(RESULT_OK)
        super.onBackPressed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arNotificationSound.register(this, log)
        App1.setActivityTheme(this)

        val intent = intent
        this.acctAscii = intent.getStringExtra(EXTRA_ACCT_ASCII)!!
        this.acctPretty = intent.getStringExtra(EXTRA_ACCT_PRETTY)!!
        this.showNotificationSound = intent.getBooleanExtra(EXTRA_SHOW_NOTIFICATION_SOUND, false)

        initUI()

        load()
    }

    private fun initUI() {

        title = getString(
            when {
                showNotificationSound -> R.string.nickname_and_color_and_notification_sound
                else -> R.string.nickname_and_color
            }
        )
        setContentView(R.layout.act_nickname)
        App1.initEdgeToEdge(this)

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
        btnNotificationSoundEdit.isEnabledAlpha = bBefore8
        btnNotificationSoundReset.isEnabledAlpha = bBefore8

        etNickname.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence,
                start: Int,
                count: Int,
                after: Int,
            ) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable) {
                show()
            }
        })
    }

    private fun load() {
        loadingBusy = true

        findViewById<View>(R.id.llNotificationSound).visibility =
            if (showNotificationSound) View.VISIBLE else View.GONE

        tvAcct.text = acctPretty

        val ac = AcctColor.load(acctAscii, acctPretty)
        colorBg = ac.color_bg
        colorFg = ac.color_fg
        etNickname.setText(ac.nickname)
        notificationSoundUri = ac.notification_sound

        loadingBusy = false
        show()
    }

    private fun save() {
        if (loadingBusy) return
        AcctColor(
            acctAscii,
            acctPretty,
            etNickname.text.toString().trim { it <= ' ' },
            colorFg,
            colorBg,
            notificationSoundUri
        ).save(System.currentTimeMillis())
    }

    private fun show() {
        val s = etNickname.text.toString().trim { it <= ' ' }
        tvPreview.text = s.notEmpty() ?: acctPretty
        tvPreview.textColor = colorFg.notZero() ?: attrColor(R.attr.colorTimeSmall)
        tvPreview.backgroundColor = colorBg
    }

    override fun onClick(v: View) {
        val builder: ColorPickerDialog.Builder
        when (v.id) {
            R.id.btnTextColorEdit -> {
                etNickname.hideKeyboard()
                builder = ColorPickerDialog.newBuilder()
                    .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                    .setAllowPresets(true)
                    .setShowAlphaSlider(false)
                    .setDialogId(1)
                if (colorFg != 0) builder.setColor(colorFg)
                builder.show(this)
            }

            R.id.btnTextColorReset -> {
                colorFg = 0
                show()
            }

            R.id.btnBackgroundColorEdit -> {
                etNickname.hideKeyboard()
                builder = ColorPickerDialog.newBuilder()
                    .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                    .setAllowPresets(true)
                    .setShowAlphaSlider(false)
                    .setDialogId(2)
                if (colorBg != 0) builder.setColor(colorBg)
                builder.show(this)
            }

            R.id.btnBackgroundColorReset -> {
                colorBg = 0
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

            R.id.btnNotificationSoundReset -> notificationSoundUri = ""
        }
    }

    override fun onColorSelected(dialogId: Int, @ColorInt newColor: Int) {
        when (dialogId) {
            1 -> colorFg = -0x1000000 or newColor
            2 -> colorBg = -0x1000000 or newColor
        }
        show()
    }

    override fun onDialogDismissed(dialogId: Int) {}

    private fun openNotificationSoundPicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, R.string.notification_sound)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
        notificationSoundUri.mayUri()?.let {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it)
        }
        val chooser = Intent.createChooser(intent, getString(R.string.notification_sound))
        arNotificationSound.launch(chooser)
    }
}
