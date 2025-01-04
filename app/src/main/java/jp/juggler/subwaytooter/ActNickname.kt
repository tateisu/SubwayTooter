package jp.juggler.subwaytooter

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.jrummyapps.android.colorpicker.dialogColorPicker
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.databinding.ActNicknameBinding
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.daoAcctColor
import jp.juggler.subwaytooter.view.wrapTitleTextView
import jp.juggler.util.backPressed
import jp.juggler.util.boolean
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.data.mayUri
import jp.juggler.util.data.notEmpty
import jp.juggler.util.data.notZero
import jp.juggler.util.log.LogCategory
import jp.juggler.util.string
import jp.juggler.util.ui.ActivityResultHandler
import jp.juggler.util.ui.attrColor
import jp.juggler.util.ui.decodeRingtonePickerResult
import jp.juggler.util.ui.hideKeyboard
import jp.juggler.util.ui.isEnabledAlpha
import jp.juggler.util.ui.setContentViewAndInsets
import jp.juggler.util.ui.setNavigationBack
import jp.juggler.util.ui.vg
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.textColor

class ActNickname : AppCompatActivity(), View.OnClickListener {

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

    private val views by lazy {
        ActNicknameBinding.inflate(layoutInflater)
    }

    private val acctAscii by lazy {
        intent?.string(EXTRA_ACCT_ASCII)!!
    }
    private val acctPretty by lazy {
        intent?.string(EXTRA_ACCT_PRETTY)!!
    }
    private val showNotificationSound by lazy {
        intent?.boolean(EXTRA_SHOW_NOTIFICATION_SOUND) ?: false
    }

    private var colorFg = 0
    private var colorBg = 0
    private var notificationSoundUri: String? = null
    private var loadingBusy = false

    private val arNotificationSound = ActivityResultHandler(log) { r ->
        r.decodeRingtonePickerResult?.let { uri ->
            notificationSoundUri = uri.toString()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backPressed {
            setResult(RESULT_OK)
            finish()
        }
        arNotificationSound.register(this)
        App1.setActivityTheme(this)
        setContentViewAndInsets(views.root)
        initUI()
        load()
    }

    private fun initUI() {
        fixHorizontalMargin(views.llContent)
        setSupportActionBar(views.toolbar)
        setNavigationBack(views.toolbar)
        wrapTitleTextView(
            subtitle = getString(
                when {
                    showNotificationSound -> R.string.nickname_and_color_and_notification_sound
                    else -> R.string.nickname_and_color
                }
            )
        )

        views.btnTextColorEdit.setOnClickListener(this)
        views.btnTextColorReset.setOnClickListener(this)
        views.btnBackgroundColorEdit.setOnClickListener(this)
        views.btnBackgroundColorReset.setOnClickListener(this)
        views.btnSave.setOnClickListener(this)
        views.btnDiscard.setOnClickListener(this)

        views.btnNotificationSoundEdit.setOnClickListener(this)
        views.btnNotificationSoundReset.setOnClickListener(this)

        views.btnNotificationSoundEdit.isEnabledAlpha = false
        views.btnNotificationSoundReset.isEnabledAlpha = false

        views.etNickname.addTextChangedListener(object : TextWatcher {
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

        views.llNotificationSound.vg(showNotificationSound)

        views.tvAcct.text = acctPretty

        val ac = daoAcctColor.load(acctAscii)
        colorBg = ac.colorBg
        colorFg = ac.colorFg
        views.etNickname.setText(ac.nickname)
        notificationSoundUri = ac.notificationSound

        loadingBusy = false
        show()
    }

    private fun save() {
        if (loadingBusy) return
        launchAndShowError {
            daoAcctColor.save(
                System.currentTimeMillis(),
                AcctColor(
                    acctAscii = acctAscii,
                    nicknameSave = views.etNickname.text.toString().trim { it <= ' ' },
                    colorFg = colorFg,
                    colorBg = colorBg,
                    notificationSoundSaved = notificationSoundUri ?: "",
                )
            )
        }
    }

    private fun show() {
        val s = views.etNickname.text.toString().trim { it <= ' ' }
        views.tvPreview.text = s.notEmpty() ?: acctPretty
        views.tvPreview.textColor = colorFg.notZero() ?: attrColor(R.attr.colorTimeSmall)
        views.tvPreview.backgroundColor = colorBg
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btnTextColorEdit -> launchAndShowError {
                views.etNickname.hideKeyboard()
                colorFg = Color.BLACK or dialogColorPicker(
                    colorInitial = colorFg.notZero(),
                    alphaEnabled = false
                )
                show()
            }

            R.id.btnTextColorReset -> {
                colorFg = 0
                show()
            }

            R.id.btnBackgroundColorEdit -> launchAndShowError {
                views.etNickname.hideKeyboard()
                colorBg = Color.BLACK or dialogColorPicker(
                    colorInitial = colorBg.notZero(),
                    alphaEnabled = false,
                )
                show()
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
