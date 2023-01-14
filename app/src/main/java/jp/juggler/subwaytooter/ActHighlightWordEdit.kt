package jp.juggler.subwaytooter

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Bundle
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import com.jrummyapps.android.colorpicker.ColorPickerDialog
import com.jrummyapps.android.colorpicker.ColorPickerDialogListener
import jp.juggler.subwaytooter.databinding.ActHighlightEditBinding
import jp.juggler.subwaytooter.table.HighlightWord
import jp.juggler.util.backPressed
import jp.juggler.util.data.decodeJsonObject
import jp.juggler.util.data.mayUri
import jp.juggler.util.data.notEmpty
import jp.juggler.util.data.notZero
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.long
import jp.juggler.util.string
import jp.juggler.util.ui.*
import org.jetbrains.anko.textColor

class ActHighlightWordEdit
    : AppCompatActivity(),
    ColorPickerDialogListener,
    CompoundButton.OnCheckedChangeListener {

    companion object {

        internal val log = LogCategory("ActHighlightWordEdit")

        private const val COLOR_DIALOG_ID_TEXT = 1
        private const val COLOR_DIALOG_ID_BACKGROUND = 2

        private const val STATE_ITEM = "item"
        private const val EXTRA_ITEM_ID = "itemId"
        private const val EXTRA_INITIAL_TEXT = "initialText"

        fun createIntent(activity: Activity, itemId: Long) =
            Intent(activity, ActHighlightWordEdit::class.java).apply {
                putExtra(EXTRA_ITEM_ID, itemId)
            }

        fun createIntent(activity: Activity, initialText: String) =
            Intent(activity, ActHighlightWordEdit::class.java).apply {
                putExtra(EXTRA_INITIAL_TEXT, initialText)
            }
    }

    internal lateinit var item: HighlightWord

    private val views by lazy {
        ActHighlightEditBinding.inflate(layoutInflater)
    }

    private var bBusy = false

    private val arNotificationSound = ActivityResultHandler(log) { r ->
        r.decodeRingtonePickerResult?.let { uri ->
            item.sound_uri = uri.toString()
            item.sound_type = HighlightWord.SOUND_TYPE_CUSTOM
            showSound()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        backPressed {
            AlertDialog.Builder(this)
                .setCancelable(true)
                .setMessage(R.string.discard_changes)
                .setPositiveButton(R.string.no, null)
                .setNegativeButton(R.string.yes) { _, _ -> finish() }
                .show()
        }
        super.onCreate(savedInstanceState)
        arNotificationSound.register(this)
        App1.setActivityTheme(this)
        initUI()

        setResult(RESULT_CANCELED)

        fun loadData(): HighlightWord? {
            savedInstanceState?.getString(STATE_ITEM)
                ?.decodeJsonObject()
                ?.let { return HighlightWord(it) }

            intent?.string(EXTRA_INITIAL_TEXT)
                ?.let { return HighlightWord(it) }

            intent?.long(EXTRA_ITEM_ID)
                ?.let { return HighlightWord.load(it) }

            return null
        }

        val item = loadData()
        if (item == null) {
            log.d("missing source data")
            finish()
            return
        }

        this.item = item

        views.etName.setText(item.name)
        showSound()
        showColor()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        try {
            // ui may not initialized yet.
            uiToData()
        } catch (ex: Throwable) {
            log.e(ex, "uiToData failed.")
        }
        item.encodeJson().toString().let { outState.putString(STATE_ITEM, it) }
    }

    private fun initUI() {
        setContentView(views.root)
        setSupportActionBar(views.toolbar)
        setNavigationBack(views.toolbar)
        fixHorizontalMargin(views.llContent)

        views.swSound.setOnCheckedChangeListener(this)
        views.swSpeech.setOnCheckedChangeListener(this)

        setSwitchColor(views.swSound)
        setSwitchColor(views.swSpeech)

        views.btnDiscard.setOnClickListener { finish() }
        views.btnSave.setOnClickListener { save() }
        views.btnTextColorEdit.setOnClickListener {
            openColorPicker(
                COLOR_DIALOG_ID_TEXT,
                item.color_fg
            )
        }
        views.btnTextColorReset.setOnClickListener {
            item.color_fg = 0
            showColor()
        }
        views.btnBackgroundColorEdit.setOnClickListener {
            openColorPicker(
                COLOR_DIALOG_ID_BACKGROUND,
                item.color_bg
            )
        }
        views.btnBackgroundColorReset.setOnClickListener {
            item.color_bg = 0
            showColor()
        }
        views.btnNotificationSoundEdit.setOnClickListener { openNotificationSoundPicker() }
        views.btnNotificationSoundReset.setOnClickListener {
            item.sound_uri = null
            item.sound_type = when {
                views.swSound.isChecked -> HighlightWord.SOUND_TYPE_DEFAULT
                else -> HighlightWord.SOUND_TYPE_NONE
            }
            showSound()
        }
        views.btnNotificationSoundTest.setOnClickListener {
            ActHighlightWordList.sound(this, item)
        }
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (bBusy) return
        uiToData()
        showSound()
    }

    private fun openColorPicker(id: Int, initialColor: Int) {
        val builder = ColorPickerDialog.newBuilder()
            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
            .setAllowPresets(true)
            .setShowAlphaSlider(id == COLOR_DIALOG_ID_BACKGROUND)
            .setDialogId(id)

        if (initialColor != 0) builder.setColor(initialColor)

        builder.show(this)
    }

    override fun onDialogDismissed(dialogId: Int) {}

    override fun onColorSelected(dialogId: Int, newColor: Int) {
        when (dialogId) {
            COLOR_DIALOG_ID_TEXT -> item.color_fg = newColor or Color.BLACK
            COLOR_DIALOG_ID_BACKGROUND -> item.color_bg = newColor.notZero() ?: 0x01000000
        }
        showColor()
    }

    //////////////////////////////////////////////////////////////////

    private fun showSound() {
        bBusy = true
        try {
            val isSoundEnabled = item.sound_type != HighlightWord.SOUND_TYPE_NONE
            views.btnNotificationSoundTest.isEnabledAlpha = isSoundEnabled
            views.swSound.isChecked = isSoundEnabled
            views.swSpeech.isChecked = item.speech != 0
        } finally {
            bBusy = false
        }
    }

    private fun showColor() {
        bBusy = true
        try {
            views.etName.setBackgroundColor(item.color_bg) // may 0
            views.etName.textColor =
                item.color_fg.notZero() ?: attrColor(android.R.attr.textColorPrimary)
        } finally {
            bBusy = false
        }
    }

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
        arNotificationSound.launch(chooser)
    }

    private fun uiToData() {
        item.name = views.etName.text.toString().trim { it <= ' ' || it == '　' }

        item.sound_type = when {
            !views.swSound.isChecked -> HighlightWord.SOUND_TYPE_NONE
            item.sound_uri?.notEmpty() == null -> HighlightWord.SOUND_TYPE_DEFAULT
            else -> HighlightWord.SOUND_TYPE_CUSTOM
        }

        item.speech = when (views.swSpeech.isChecked) {
            false -> 0
            else -> 1
        }
    }

    private fun save() {
        uiToData()

        if (item.name.isEmpty()) {
            showToast(true, R.string.cant_leave_empty_keyword)
            return
        }

        val other = HighlightWord.load(item.name)
        if (other != null && other.id != item.id) {
            showToast(true, R.string.cant_save_duplicated_keyword)
            return
        }

        item.save(this)
        showToast(false, R.string.saved)
        setResult(RESULT_OK)
        finish()
    }
}
