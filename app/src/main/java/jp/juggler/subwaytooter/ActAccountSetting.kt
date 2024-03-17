package jp.juggler.subwaytooter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.SpannableString
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.jrummyapps.android.colorpicker.dialogColorPicker
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.auth.AuthBase
import jp.juggler.subwaytooter.api.auth.authRepo
import jp.juggler.subwaytooter.api.entity.ServiceType
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootAttachment
import jp.juggler.subwaytooter.api.entity.TootAttachment.Companion.tootAttachment
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.api.entity.TootVisibility
import jp.juggler.subwaytooter.api.entity.parseItem
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.api.runApiTask2
import jp.juggler.subwaytooter.api.showApiError
import jp.juggler.subwaytooter.databinding.ActAccountSettingBinding
import jp.juggler.subwaytooter.dialog.DlgConfirm.confirm
import jp.juggler.subwaytooter.dialog.actionsDialog
import jp.juggler.subwaytooter.notification.checkNotificationImmediate
import jp.juggler.subwaytooter.notification.checkNotificationImmediateAll
import jp.juggler.subwaytooter.notification.resetNotificationTracking
import jp.juggler.subwaytooter.push.PushBase
import jp.juggler.subwaytooter.push.pushRepo
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.daoAcctColor
import jp.juggler.subwaytooter.table.daoSavedAccount
import jp.juggler.subwaytooter.util.*
import jp.juggler.util.backPressed
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.coroutine.launchProgress
import jp.juggler.util.data.UriAndType
import jp.juggler.util.data.UriSerializer
import jp.juggler.util.data.getDocumentName
import jp.juggler.util.data.getStreamSize
import jp.juggler.util.data.notZero
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.log.withCaption
import jp.juggler.util.long
import jp.juggler.util.media.ResizeConfig
import jp.juggler.util.media.ResizeType
import jp.juggler.util.media.createResizedBitmap
import jp.juggler.util.network.toPatch
import jp.juggler.util.network.toPost
import jp.juggler.util.network.toPostRequestBuilder
import jp.juggler.util.ui.ActivityResultHandler
import jp.juggler.util.ui.attrColor
import jp.juggler.util.ui.isEnabledAlpha
import jp.juggler.util.ui.isOk
import jp.juggler.util.ui.scan
import jp.juggler.util.ui.vg
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.textColor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max

class ActAccountSetting : AppCompatActivity(),
    View.OnClickListener,
    CompoundButton.OnCheckedChangeListener,
    AdapterView.OnItemSelectedListener {
    companion object {

        internal val log = LogCategory("ActAccountSetting")

        internal const val KEY_ACCOUNT_DB_ID = "account_db_id"

        internal const val RESULT_INPUT_ACCESS_TOKEN = Activity.RESULT_FIRST_USER + 10
        internal const val EXTRA_DB_ID = "db_id"

        internal const val max_length_display_name = 30
        internal const val max_length_note = 160
        internal const val max_length_fields = 255

        internal const val MIME_TYPE_JPEG = "image/jpeg"
        internal const val MIME_TYPE_PNG = "image/png"

        private const val ACTIVITY_STATE = "MyActivityState"

        private const val COLOR_DIALOG_NOTIFICATION_ACCENT_COLOR = 1

        fun createIntent(activity: Activity, ai: SavedAccount) =
            Intent(activity, ActAccountSetting::class.java).apply {
                putExtra(KEY_ACCOUNT_DB_ID, ai.db_id)
            }

        fun simpleTextWatcher(block: () -> Unit) = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                block()
            }

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int,
            ) {
            }

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int,
            ) {
            }
        }
    }

    @kotlinx.serialization.Serializable
    data class State(
        var propName: String = "",

        @kotlinx.serialization.Serializable(with = UriSerializer::class)
        var uriCameraImage: Uri? = null,
    )

    var state = State()

    lateinit var handler: Handler

    lateinit var account: SavedAccount

    private val views by lazy {
        ActAccountSettingBinding.inflate(layoutInflater, null, false)
    }

    private lateinit var nameInvalidator: NetworkEmojiInvalidator
    private lateinit var noteInvalidator: NetworkEmojiInvalidator
    private lateinit var defaultTextInvalidator: NetworkEmojiInvalidator

    private var loadingBusy = false
    private var profileBusy = false

    //    private lateinit var listEtFieldName: List<EditText>
//    private lateinit var listEtFieldValue: List<EditText>
    private lateinit var listFieldNameInvalidator: List<NetworkEmojiInvalidator>
    private lateinit var listFieldValueInvalidator: List<NetworkEmojiInvalidator>
    private lateinit var btnFields: View

    private class ResizeItem(val config: ResizeConfig, val caption: String)

    private lateinit var imageResizeItems: List<ResizeItem>

    private class PushPolicyItem(val id: String?, val caption: String)

    private lateinit var pushPolicyItems: List<PushPolicyItem>

    internal var visibility = TootVisibility.Public

    private val languages by lazy {
        loadLanguageList()
    }

    /////////////////////////////////////////////////////////////////////

    private val cameraOpener = CameraOpener {
        uploadImage(state.propName, it)
    }

    private val visualMediaPicker = VisualMediaPickerCompat {
        uploadImage(state.propName, it?.firstOrNull())
    }

    private val arShowAcctColor = ActivityResultHandler(log) { r ->
        if (r.isOk) showAcctColor()
    }

    ///////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backPressed { handleBackPressed() }

        visualMediaPicker.register(this)
        cameraOpener.register(this)

        arShowAcctColor.register(this)

        if (savedInstanceState != null) {
            savedInstanceState.getString(ACTIVITY_STATE)
                ?.let { state = kJson.decodeFromString(it) }
        }

        App1.setActivityTheme(this)

        initUI()

        launchAndShowError {
            val a = intent.long(KEY_ACCOUNT_DB_ID)
                ?.let { daoSavedAccount.loadAccount(it) }
            if (a == null) {
                finish()
                return@launchAndShowError
            }
            supportActionBar?.subtitle = a.acct.pretty

            loadUIFromData(a)

            initializeProfile()

            views.btnOpenBrowser.text =
                getString(R.string.open_instance_website, account.apiHost.pretty)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        val encodedState = kJson.encodeToString(state)
        log.d("encodedState=$encodedState")
        val decodedState: State = kJson.decodeFromString(encodedState)
        log.d("encodedState.uriCameraImage=${decodedState.uriCameraImage}")
        outState.putString(ACTIVITY_STATE, encodedState)
    }

    var density: Float = 1f

    @Suppress("LongMethod")
    private fun initUI() {
        this.density = resources.displayMetrics.density
        this.handler = App1.getAppState(this).handler
        setContentView(views.root)
        setSupportActionBar(views.toolbar)
        fixHorizontalPadding(views.svContent)
        setSwitchColor(views.root)

        views.apply {
            btnPushSubscriptionNotForce.vg(ReleaseType.isDebug)

            imageResizeItems = SavedAccount.resizeConfigList.map {
                val caption = when (it.type) {
                    ResizeType.None -> getString(R.string.dont_resize)
                    ResizeType.LongSide -> getString(R.string.long_side_pixel, it.size)
                    ResizeType.SquarePixel -> if (it.extraStringId != 0) {
                        getString(
                            R.string.resize_square_pixels_2,
                            it.size * it.size,
                            getString(it.extraStringId)
                        )
                    } else {
                        getString(
                            R.string.resize_square_pixels,
                            it.size * it.size,
                            it.size
                        )
                    }
                }
                ResizeItem(it, caption)
            }
            spResizeImage.adapter = ArrayAdapter(
                this@ActAccountSetting,
                android.R.layout.simple_spinner_item,
                imageResizeItems.map { it.caption }.toTypedArray()
            ).apply {
                setDropDownViewResource(R.layout.lv_spinner_dropdown)
            }

            spLanguageCode.adapter = ArrayAdapter(
                this@ActAccountSetting,
                android.R.layout.simple_spinner_item,
                languages.map { it.second }.toTypedArray()
            ).apply {
                setDropDownViewResource(R.layout.lv_spinner_dropdown)
            }

            spMovieTranscodeMode.adapter = ArrayAdapter(
                this@ActAccountSetting,
                android.R.layout.simple_spinner_item,
                arrayOf(
                    getString(R.string.auto),
                    getString(R.string.no),
                    getString(R.string.always),
                )
            ).apply {
                setDropDownViewResource(R.layout.lv_spinner_dropdown)
            }

            pushPolicyItems = listOf(
                PushPolicyItem(null, getString(R.string.unspecified)),
                PushPolicyItem("all", getString(R.string.all)),
                PushPolicyItem("followed", getString(R.string.following)),
                PushPolicyItem("follower", getString(R.string.followers)),
                PushPolicyItem("none", getString(R.string.no_one)),
            )

            spPushPolicy.adapter = ArrayAdapter(
                this@ActAccountSetting,
                android.R.layout.simple_spinner_item,
                pushPolicyItems.map { it.caption }.toTypedArray()
            ).apply {
                setDropDownViewResource(R.layout.lv_spinner_dropdown)
            }

            listFieldNameInvalidator = intArrayOf(
                R.id.etFieldName1,
                R.id.etFieldName2,
                R.id.etFieldName3,
                R.id.etFieldName4
            ).map {
                NetworkEmojiInvalidator(handler, findViewById<EditText>(it))
            }

            listFieldValueInvalidator = intArrayOf(
                R.id.etFieldValue1,
                R.id.etFieldValue2,
                R.id.etFieldValue3,
                R.id.etFieldValue4
            ).map {
                NetworkEmojiInvalidator(handler, findViewById<EditText>(it))
            }

            // btnNotificationStyleEditReply.vg(PrefB.bpSeparateReplyNotificationGroup.value)

            // invalidaterがないと描画できないので
            nameInvalidator = NetworkEmojiInvalidator(handler, etDisplayName)
            noteInvalidator = NetworkEmojiInvalidator(handler, etNote)
            defaultTextInvalidator = NetworkEmojiInvalidator(handler, etDefaultText)

            val watcher1 = simpleTextWatcher {
                saveUIToData()
            }

            views.root.scan {
                when (it) {
                    etMaxTootChars -> etMaxTootChars.addTextChangedListener(
                        simpleTextWatcher {
                            val num = etMaxTootChars.parseInt()
                            if (num != null && num >= 0) {
                                saveUIToData()
                            }
                        }
                    )

                    is EditText ->
                        it.addTextChangedListener(watcher1)

                    is Spinner ->
                        it.onItemSelectedListener = this@ActAccountSetting
                    // CompoundButton はButtonでもあるので上に置く
                    is CompoundButton ->
                        it.setOnCheckedChangeListener(this@ActAccountSetting)

                    is ImageButton ->
                        it.setOnClickListener(this@ActAccountSetting)

                    is Button ->
                        it.setOnClickListener(this@ActAccountSetting)
                }
            }
        }
    }

    private fun EditText.parseInt(): Int? =
        text?.toString()?.toIntOrNull()

    private fun loadUIFromData(a: SavedAccount) {
        this.account = a
        this.visibility = a.visibility
        loadingBusy = true
        try {

            views.apply {

                tvInstance.text = a.apiHost.pretty
                tvUser.text = a.acct.pretty

                cbConfirmBoost.isChecked = a.confirmBoost
                cbConfirmFavourite.isChecked = a.confirmFavourite
                cbConfirmFollow.isChecked = a.confirmFollow
                cbConfirmFollowLockedUser.isChecked = a.confirmFollowLocked
                cbConfirmReaction.isChecked = a.confirmReaction
                cbConfirmToot.isChecked = a.confirmPost
                cbConfirmUnbookmark.isChecked = a.confirmUnbookmark
                cbConfirmUnboost.isChecked = a.confirmUnboost
                cbConfirmUnfavourite.isChecked = a.confirmUnfavourite
                cbConfirmUnfollow.isChecked = a.confirmUnfollow
                cbNotificationBoost.isChecked = a.notificationBoost
                cbNotificationFavourite.isChecked = a.notificationFavourite
                cbNotificationFollow.isChecked = a.notificationFollow
                cbNotificationFollowRequest.isChecked = a.notificationFollowRequest
                cbNotificationMention.isChecked = a.notificationMention
                cbNotificationPost.isChecked = a.notificationPost
                cbNotificationReaction.isChecked = a.notificationReaction
                cbNotificationStatusReference.isChecked = a.notificationStatusReference
                cbNotificationUpdate.isChecked = a.notificationUpdate
                cbNotificationVote.isChecked = a.notificationVote
                swDontShowTimeout.isChecked = a.dontShowTimeout
                swExpandCW.isChecked = a.expandCw
                swMarkSensitive.isChecked = a.defaultSensitive
                swNSFWOpen.isChecked = a.dontHideNsfw
                swNotificationPullEnabled.isChecked = a.notificationPullEnable
                swNotificationPushEnabled.isChecked = a.notificationPushEnable

                defaultTextInvalidator.text = a.defaultText
                etMaxTootChars.setText(a.maxTootChars.toString())

                val ti = TootInstance.getCached(a)
                if (ti == null) {
                    etMediaSizeMax.setText(a.imageMaxMegabytes ?: "")
                    etMovieSizeMax.setText(a.movieMaxMegabytes ?: "")
                } else {
                    etMediaSizeMax.setText(
                        a.imageMaxMegabytes
                            ?: a.getImageMaxBytes(ti).div(1000000).toString()
                    )
                    etMovieSizeMax.setText(
                        a.movieMaxMegabytes
                            ?: a.getMovieMaxBytes(ti).div(1000000).toString()
                    )
                }

                val currentResizeConfig = a.getResizeConfig()
                var index =
                    imageResizeItems.indexOfFirst { it.config.spec == currentResizeConfig.spec }
                log.d("ResizeItem current ${currentResizeConfig.spec} index=$index ")
                if (index == -1) index =
                    imageResizeItems.indexOfFirst { it.config.spec == SavedAccount.defaultResizeConfig.spec }
                spResizeImage.setSelection(index, false)

                val currentPushPolicy = a.pushPolicy
                index = pushPolicyItems.indexOfFirst { it.id == currentPushPolicy }
                if (index == -1) index = 0
                spPushPolicy.setSelection(index, false)

                spMovieTranscodeMode.setSelection(max(0, a.movieTranscodeMode), false)
                etMovieFrameRate.setText(a.movieTranscodeFramerate)
                etMovieBitrate.setText(a.movieTranscodeBitrate)
                etMovieSquarePixels.setText(a.movieTranscodeSquarePixels)

                spLanguageCode.setSelection(max(0, languages.indexOfFirst { it.first == a.lang }))

                // アカウントからUIへのデータロードはここまで
                loadingBusy = false

                val enabled = !a.isPseudo

                arrayOf(
                    btnAccessToken,
                    btnFields,
                    btnInputAccessToken,
                    btnLoadPreference,
                    btnPushSubscription,
                    btnPushSubscriptionNotForce,
                    btnResetNotificationTracking,
                    btnVisibility,
                    cbConfirmBoost,
                    cbConfirmFavourite,
                    cbConfirmFollow,
                    cbConfirmFollowLockedUser,
                    cbConfirmReaction,
                    cbConfirmToot,
                    cbConfirmUnbookmark,
                    cbConfirmUnboost,
                    cbConfirmUnfavourite,
                    cbConfirmUnfollow,
                    cbNotificationBoost,
                    cbNotificationFavourite,
                    cbNotificationFollow,
                    cbNotificationFollowRequest,
                    cbNotificationMention,
                    cbNotificationPost,
                    cbNotificationReaction,
                    cbNotificationStatusReference,
                    cbNotificationUpdate,
                    cbNotificationVote,
                    etDefaultText,
                    etMaxTootChars,
                    etMediaSizeMax,
                    etMovieBitrate,
                    etMovieFrameRate,
                    etMovieSizeMax,
                    etMovieSquarePixels,
                    spLanguageCode,
                    spMovieTranscodeMode,
                    spPushPolicy,
                    spResizeImage,
                    swNotificationPullEnabled,
                    swNotificationPushEnabled,
                    btnNotificationAccentColorEdit,
                    btnNotificationAccentColorReset,
                ).forEach { it.isEnabledAlpha = enabled }

//                arrayOf(
//                    btnNotificationStyleEdit,
//                    btnNotificationStyleEditReply,
//                ).forEach { it.isEnabledAlpha = enabled }
            }

            showVisibility()
            showAcctColor()
            showPushSetting()
            showNotificationColor()
        } finally {
            loadingBusy = false
        }
    }

    private fun showAcctColor() {

        val sa = this.account
        val ac = daoAcctColor.load(sa)
        views.tvUserCustom.apply {
            backgroundColor = ac.colorBg
            text = ac.nickname
            textColor = ac.colorFg.notZero()
                ?: attrColor(R.attr.colorTimeSmall)
        }
    }

    private fun showPushSetting() {
        views.run {
            run {
                val usePush = swNotificationPushEnabled.isChecked
                tvPushPolicyDesc.vg(usePush)
                spPushPolicy.vg(usePush)
                tvPushActions.vg(usePush)
                btnPushSubscription.vg(usePush)
                btnPushSubscriptionNotForce.vg(usePush)
                tvNotificationAccentColor.vg(usePush)
                llNotificationAccentColor.vg(usePush)
            }

            run {
                val usePull = swNotificationPullEnabled.isChecked
                tvDontShowTimeout.vg(usePull)
                swDontShowTimeout.vg(usePull)
                tvPullActions.vg(usePull)
                btnResetNotificationTracking.vg(usePull)
            }
        }
    }

    private fun saveUIToData() {
        if (!::account.isInitialized) return
        if (loadingBusy) return
        launchAndShowError {

            account.visibility = visibility

            views.apply {

                account.confirmBoost = cbConfirmBoost.isChecked
                account.confirmFavourite = cbConfirmFavourite.isChecked
                account.confirmFollow = cbConfirmFollow.isChecked
                account.confirmFollowLocked = cbConfirmFollowLockedUser.isChecked
                account.confirmPost = cbConfirmToot.isChecked
                account.confirmReaction = cbConfirmReaction.isChecked
                account.confirmUnbookmark = cbConfirmUnbookmark.isChecked
                account.confirmUnboost = cbConfirmUnboost.isChecked
                account.confirmUnfavourite = cbConfirmUnfavourite.isChecked
                account.confirmUnfollow = cbConfirmUnfollow.isChecked
                account.defaultSensitive = swMarkSensitive.isChecked
                account.dontHideNsfw = swNSFWOpen.isChecked
                account.dontShowTimeout = swDontShowTimeout.isChecked
                account.expandCw = swExpandCW.isChecked
                account.notificationBoost = cbNotificationBoost.isChecked
                account.notificationFavourite = cbNotificationFavourite.isChecked
                account.notificationFollow = cbNotificationFollow.isChecked
                account.notificationFollowRequest = cbNotificationFollowRequest.isChecked
                account.notificationMention = cbNotificationMention.isChecked
                account.notificationPost = cbNotificationPost.isChecked
                account.notificationPullEnable = swNotificationPullEnabled.isChecked
                account.notificationPushEnable = swNotificationPushEnabled.isChecked
                account.notificationReaction = cbNotificationReaction.isChecked
                account.notificationStatusReference = cbNotificationStatusReference.isChecked
                account.notificationUpdate = cbNotificationUpdate.isChecked
                account.notificationVote = cbNotificationVote.isChecked

//                account.soundUri = ""
                account.defaultText = etDefaultText.text.toString()

                account.maxTootChars = etMaxTootChars.parseInt()?.takeIf { it > 0 } ?: 0

                account.movieMaxMegabytes = etMovieSizeMax.text.toString().trim()
                account.imageMaxMegabytes = etMediaSizeMax.text.toString().trim()
                account.imageResize = (
                        imageResizeItems.elementAtOrNull(spResizeImage.selectedItemPosition)?.config
                            ?: SavedAccount.defaultResizeConfig
                        ).spec

                account.pushPolicy =
                    pushPolicyItems.elementAtOrNull(spPushPolicy.selectedItemPosition)?.id

                account.movieTranscodeMode = spMovieTranscodeMode.selectedItemPosition
                account.movieTranscodeBitrate = etMovieBitrate.text.toString()
                account.movieTranscodeFramerate = etMovieFrameRate.text.toString()
                account.movieTranscodeSquarePixels = etMovieSquarePixels.text.toString()
                account.lang = languages.elementAtOrNull(spLanguageCode.selectedItemPosition)?.first
                    ?: SavedAccount.LANG_WEB
            }

            daoSavedAccount.save(account)
        }
    }

    private fun handleBackPressed() {
        checkNotificationImmediateAll(this, onlyEnqueue = true)
        checkNotificationImmediate(this, account.db_id)
        finish()
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        when (buttonView) {
            views.cbLocked -> {
                if (!profileBusy) sendLocked(isChecked)
            }

            views.swNotificationPullEnabled -> {
                saveUIToData()
                showPushSetting()
            }

            views.swNotificationPushEnabled -> launchAndShowError {
                val oldChecked = account.notificationPushEnable
                try {
                    if (oldChecked == isChecked) return@launchAndShowError
                    account.notificationPushEnable = isChecked
                    if (updatePushSubscription(force = true)) {
                        saveUIToData()
                    } else {
                        account.notificationPushEnable = oldChecked
                        buttonView.isChecked = oldChecked
                    }
                } finally {
                    showPushSetting()
                }
            }

            else -> saveUIToData()
        }
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        saveUIToData()
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        saveUIToData()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btnAccessToken -> performAccessToken()
            R.id.btnInputAccessToken -> inputAccessToken()

            R.id.btnAccountRemove -> performAccountRemove()
            R.id.btnLoadPreference -> performLoadPreference()
            R.id.btnVisibility -> performVisibility()
            R.id.btnOpenBrowser -> openBrowser("https://${account.apiHost.ascii}/")
            R.id.btnPushSubscription -> launchAndShowError {
                updatePushSubscription(force = true)
            }

            R.id.btnPushSubscriptionNotForce -> launchAndShowError {
                updatePushSubscription(force = false)
            }

            R.id.btnResetNotificationTracking ->
                resetNotificationTracking(account)

            R.id.btnUserCustom -> arShowAcctColor.launch(
                ActNickname.createIntent(this, account.acct, false),
            )

            R.id.btnProfileAvatar -> pickAvatarImage()

            R.id.btnProfileHeader -> pickHeaderImage()

            R.id.btnDisplayName -> sendDisplayName()

            R.id.btnNote -> sendNote()

            R.id.btnFields -> sendFields()

//            R.id.btnNotificationStyleEdit ->
//                PullNotification.openNotificationChannelSetting(
//                    this
//                )
//
//            R.id.btnNotificationStyleEditReply ->
//                PullNotification.openNotificationChannelSetting(
//                    this
//                )

            R.id.btnNotificationAccentColorEdit -> {
                lifecycleScope.launch {
                    try {
                        account.notificationAccentColor = dialogColorPicker(
                            colorInitial = account.notificationAccentColor.notZero(),
                            alphaEnabled = false,
                        )
                        showNotificationColor()
                        saveUIToData()
                    } catch (ex: Throwable) {
                        if (ex is CancellationException) return@launch
                        log.e(ex, "openColorPicker failed.")
                    }
                }
            }

            R.id.btnNotificationAccentColorReset -> {
                account.notificationAccentColor = 0
                saveUIToData()
                showNotificationColor()
            }
        }
    }

    private fun showVisibility() {
        views.btnVisibility.text =
            visibility.getVisibilityString(account.isMisskey)
    }

    private fun performVisibility() {

        val list = if (account.isMisskey) {
            arrayOf(
                //	TootVisibility.WebSetting,
                TootVisibility.Public,
                TootVisibility.UnlistedHome,
                TootVisibility.PrivateFollowers,
                TootVisibility.LocalPublic,
                TootVisibility.LocalHome,
                TootVisibility.LocalFollowers,
                TootVisibility.DirectSpecified,
                TootVisibility.DirectPrivate
            )
        } else {
            arrayOf(
                TootVisibility.WebSetting,
                TootVisibility.Public,
                TootVisibility.UnlistedHome,
                TootVisibility.PrivateFollowers,
                TootVisibility.DirectSpecified
            )
        }

        val captionList = list.map {
            getVisibilityCaption(this, account.isMisskey, it)
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.choose_visibility)
            .setItems(captionList) { _, which ->
                if (which in list.indices) {
                    visibility = list[which]
                    showVisibility()
                    saveUIToData()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performLoadPreference() {
        launchMain {
            runApiTask(account) { client ->
                client.request("/api/v1/preferences")
            }?.let { result ->
                val json = result.jsonObject
                if (json == null) {
                    showToast(true, result.error)
                    return@let
                }

                var bChanged = false
                try {
                    loadingBusy = true

                    val tmpVisibility =
                        TootVisibility.parseMastodon(json.string("posting:default:visibility"))
                    if (tmpVisibility != null) {
                        bChanged = true
                        visibility = tmpVisibility
                        showVisibility()
                    }

                    val tmpDefaultSensitive = json.boolean("posting:default:sensitive")
                    if (tmpDefaultSensitive != null) {
                        bChanged = true
                        views.swMarkSensitive.isChecked = tmpDefaultSensitive
                    }

                    val tmpExpandMedia = json.string("reading:expand:media")
                    if (tmpExpandMedia?.isNotEmpty() == true) {
                        bChanged = true
                        views.swNSFWOpen.isChecked = (tmpExpandMedia == "show_all")
                    }

                    val tmpExpandCW = json.boolean("reading:expand:spoilers")
                    if (tmpExpandCW != null) {
                        bChanged = true
                        views.swExpandCW.isChecked = tmpExpandCW
                    }
                } finally {
                    loadingBusy = false
                    if (bChanged) saveUIToData()
                }
            }
        }
    }

///////////////////////////////////////////////////

    private fun performAccountRemove() {
        launchAndShowError {
            confirm(getString(R.string.confirm_account_remove), title = getString(R.string.confirm))
            authRepo.accountRemove(account)
            finish()
        }
    }

    ///////////////////////////////////////////////////
    private fun performAccessToken() {
        launchMain {
            try {
                runApiTask2(account) { client ->
                    val authUrl = client.authStep1(forceUpdateClient = true)
                    withContext(AppDispatchers.MainImmediate) {
                        val resultIntent = Intent().apply { data = authUrl }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    }
                }
            } catch (ex: Throwable) {
                showApiError(ex)
            }
        }
    }

    private fun inputAccessToken() {

        val data = Intent()
        data.putExtra(EXTRA_DB_ID, account.db_id)
        setResult(RESULT_INPUT_ACCESS_TOKEN, data)
        finish()
    }

//////////////////////////////////////////////////////////////////////////

    private fun initializeProfile() {
        // 初期状態
        val questionId = R.drawable.wide_question
        val loadingText = when (account.isPseudo) {
            true -> "(disabled for pseudo account)"
            else -> "(loading…)"
        }

        views.apply {

            ivProfileAvatar.setErrorImage(defaultColorIcon(this@ActAccountSetting, questionId))
            ivProfileAvatar.setDefaultImage(defaultColorIcon(this@ActAccountSetting, questionId))

            nameInvalidator.text = loadingText
            noteInvalidator.text = loadingText

            // 初期状態では編集不可能
            arrayOf(
                btnProfileAvatar,
                btnProfileHeader,
                etDisplayName,
                btnDisplayName,
                etNote,
                btnNote,
                cbLocked,
            ).forEach { it.isEnabledAlpha = false }

            for (i in listFieldNameInvalidator) {
                i.text = loadingText
                i.view.isEnabledAlpha = false
            }

            for (i in listFieldValueInvalidator) {
                i.text = loadingText
                i.view.isEnabledAlpha = false
            }

            // 疑似アカウントなら編集不可のまま
            if (!account.isPseudo) loadProfile()
        }
    }

    // サーバから情報をロードする
    private fun loadProfile() {
        launchMain {
            try {
                runApiTask2(account) { client ->
                    val json = if (account.isMisskey) {
                        val result = client.request(
                            "/api/i",
                            account.putMisskeyApiToken().toPostRequestBuilder()
                        ) ?: return@runApiTask2
                        result.error?.let { error(it) }
                        result.jsonObject
                    } else {
                        // 承認待ち状態のチェック
                        authRepo.checkConfirmed(account, client)

                        val result = client.request(
                            "/api/v1/accounts/verify_credentials"
                        ) ?: return@runApiTask2
                        result.error?.let { error(it) }
                        result.jsonObject
                    }
                    val newAccount = TootParser(this, account)
                        .account(json) ?: error("parse error.")
                    withContext(AppDispatchers.MainImmediate) {
                        showProfile(newAccount)
                    }
                }
            } catch (ex: Throwable) {
                showApiError(ex)
            }
        }
    }

    private fun showProfile(src: TootAccount) {
        if (isDestroyed) return
        profileBusy = true
        try {
            views.ivProfileAvatar.setImageUrl(
                calcIconRound(views.ivProfileAvatar.layoutParams),
                src.avatar_static,
                src.avatar
            )

            views.ivProfileHeader.setImageUrl(
                0f,
                src.header_static,
                src.header
            )

            val decodeOptions = DecodeOptions(
                context = this@ActAccountSetting,
                linkHelper = account,
                emojiMapProfile = src.profile_emojis,
                emojiMapCustom = src.custom_emojis,
                authorDomain = account,
                emojiSizeMode = account.emojiSizeMode(),
            )

            val displayName = src.display_name
            val name = decodeOptions.decodeEmoji(displayName)
            nameInvalidator.text = name

            val noteString = src.source?.note ?: src.note
            val noteSpannable = when {
                account.isMisskey -> {
                    SpannableString(noteString ?: "")
                }

                else -> {
                    decodeOptions.decodeEmoji(noteString)
                }
            }

            noteInvalidator.text = noteSpannable

            views.cbLocked.isChecked = src.locked

            // 編集可能にする
            views.apply {
                arrayOf(
                    btnProfileAvatar,
                    btnProfileHeader,
                    etDisplayName,
                    btnDisplayName,
                    etNote,
                    btnNote,
                    cbLocked,
                ).forEach { it.isEnabledAlpha = true }
            }

            if (src.source?.fields != null) {
                val fields = src.source.fields
                listFieldNameInvalidator.forEachIndexed { i, et ->
                    // いつからかfields name にもカスタム絵文字が使えるようになった
                    // https://github.com/tootsuite/mastodon/pull/11350
                    // しかし
                    val text = decodeOptions.decodeEmoji(
                        when {
                            i >= fields.size -> ""
                            else -> fields[i].name
                        }
                    )
                    et.text = text
                    et.view.isEnabledAlpha = true
                }

                listFieldValueInvalidator.forEachIndexed { i, et ->
                    val text = decodeOptions.decodeEmoji(
                        when {
                            i >= fields.size -> ""
                            else -> fields[i].value
                        }
                    )
                    et.text = text
                    et.view.isEnabledAlpha = true
                }
            } else {
                val fields = src.fields

                listFieldNameInvalidator.forEachIndexed { i, et ->
                    // いつからかfields name にもカスタム絵文字が使えるようになった
                    // https://github.com/tootsuite/mastodon/pull/11350
                    val text = decodeOptions.decodeEmoji(
                        when {
                            fields == null || i >= fields.size -> ""
                            else -> fields[i].name
                        }
                    )
                    et.text = text
                    et.view.isEnabledAlpha = true
                }

                listFieldValueInvalidator.forEachIndexed { i, et ->
                    val text = decodeOptions.decodeHTML(
                        when {
                            fields == null || i >= fields.size -> ""
                            else -> fields[i].value
                        }
                    )
                    et.text = text
                    et.view.isEnabledAlpha = true
                }
            }
        } finally {
            profileBusy = false
        }
    }

    private fun updateCredential(key: String, value: Any) {
        updateCredential(listOf(Pair(key, value)))
    }

    private suspend fun uploadImageMisskey(
        client: TootApiClient,
        opener: InputStreamOpener,
    ): Pair<TootApiResult?, TootAttachment?> {

        val size = getStreamSize(true, opener.open())

        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)

        val apiKey =
            account.tokenJson?.string(AuthBase.KEY_API_KEY_MISSKEY)
        if (apiKey?.isNotEmpty() == true) {
            multipartBuilder.addFormDataPart("i", apiKey)
        }

        multipartBuilder.addFormDataPart(
            "file",
            getDocumentName(contentResolver, opener.uri),
            object : RequestBody() {
                override fun contentType(): MediaType {
                    return opener.mimeType.toMediaType()
                }

                override fun contentLength(): Long {
                    return size
                }

                override fun writeTo(sink: BufferedSink) {
                    opener.open().use { inData ->
                        val tmp = ByteArray(4096)
                        while (true) {
                            val r = inData.read(tmp, 0, tmp.size)
                            if (r <= 0) break
                            sink.write(tmp, 0, r)
                        }
                    }
                }
            }
        )

        var ta: TootAttachment? = null
        val result = client.request(
            "/api/drive/files/create",
            multipartBuilder.build().toPost()
        )?.also { result ->
            ta = parseItem(result.jsonObject) { tootAttachment(ServiceType.MISSKEY, it) }
            if (ta == null) result.error = "TootAttachment.parse failed"
        }

        return Pair(result, ta)
    }

    private fun updateCredential(args: List<Pair<String, Any>>) {
        launchMain {
            var resultAccount: TootAccount? = null

            runApiTask(account) { client ->
                try {
                    if (account.isMisskey) {
                        val params = account.putMisskeyApiToken()

                        for (arg in args) {
                            val key = arg.first
                            val value = arg.second

                            val misskeyKey = when (key) {
                                "header" -> "bannerId"
                                "avatar" -> "avatarId"
                                "display_name" -> "name"
                                "note" -> "description"
                                "locked" -> "isLocked"
                                else -> return@runApiTask TootApiResult("Misskey does not support property '$key'")
                            }

                            when (value) {
                                is String -> params[misskeyKey] = value
                                is Boolean -> params[misskeyKey] = value

                                is InputStreamOpener -> {
                                    val (result, ta) = uploadImageMisskey(client, value)
                                    ta ?: return@runApiTask result
                                    params[misskeyKey] = ta.id
                                }
                            }
                        }

                        client.request("/api/i/update", params.toPostRequestBuilder())
                            ?.also { result ->
                                result.jsonObject?.let {
                                    resultAccount = TootParser(this, account).account(it)
                                        ?: return@runApiTask TootApiResult("TootAccount parse failed.")
                                }
                            }
                    } else {
                        val multipartBodyBuilder = MultipartBody.Builder()
                            .setType(MultipartBody.FORM)

                        for (arg in args) {
                            val key = arg.first
                            val value = arg.second

                            if (value is String) {
                                multipartBodyBuilder.addFormDataPart(key, value)
                            } else if (value is Boolean) {
                                multipartBodyBuilder.addFormDataPart(
                                    key,
                                    if (value) "true" else "false"
                                )
                            } else if (value is InputStreamOpener) {

                                val fileName = "%x".format(System.currentTimeMillis())

                                multipartBodyBuilder.addFormDataPart(
                                    key,
                                    fileName,
                                    object : RequestBody() {
                                        override fun contentType(): MediaType =
                                            value.mimeType.toMediaType()

                                        override fun writeTo(sink: BufferedSink) {
                                            value.open().use { inData ->
                                                val tmp = ByteArray(4096)
                                                while (true) {
                                                    val r = inData.read(tmp, 0, tmp.size)
                                                    if (r <= 0) break
                                                    sink.write(tmp, 0, r)
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        client.request(
                            "/api/v1/accounts/update_credentials",
                            multipartBodyBuilder.build().toPatch()
                        )?.also { result ->
                            result.jsonObject?.let {
                                resultAccount =
                                    TootParser(this@ActAccountSetting, account).account(it)
                                        ?: return@runApiTask TootApiResult("TootAccount parse failed.")
                            }
                        }
                    }
                } finally {
                    for (arg in args) {
                        val value = arg.second
                        (value as? InputStreamOpener)?.deleteTempFile()
                    }
                }
            }?.let { result ->
                val data = resultAccount
                if (data != null) {
                    showProfile(data)
                } else {
                    showToast(true, result.error)
                    for (arg in args) {
                        val key = arg.first
                        val value = arg.second
                        if (key == "locked" && value is Boolean) {
                            profileBusy = true
                            views.cbLocked.isChecked = !value
                            profileBusy = false
                        }
                    }
                }
            }
        }
    }

    private fun sendDisplayName(bConfirmed: Boolean = false) {
        val sv = views.etDisplayName.text.toString()
        if (!bConfirmed) {
            val length = sv.codePointCount(0, sv.length)
            if (length > max_length_display_name) {
                AlertDialog.Builder(this)
                    .setMessage(
                        getString(
                            R.string.length_warning,
                            getString(R.string.display_name),
                            length,
                            max_length_display_name
                        )
                    )
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok) { _, _ -> sendDisplayName(bConfirmed = true) }
                    .setCancelable(true)
                    .show()
                return
            }
        }
        updateCredential("display_name", EmojiDecoder.decodeShortCode(sv))
    }

    private fun sendNote(bConfirmed: Boolean = false) {
        val sv = views.etNote.text.toString()
        if (!bConfirmed) {

            val length = TootAccount.countText(sv)
            if (length > max_length_note) {
                AlertDialog.Builder(this)
                    .setMessage(
                        getString(
                            R.string.length_warning,
                            getString(R.string.note),
                            length,
                            max_length_note
                        )
                    )
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok) { _, _ -> sendNote(bConfirmed = true) }
                    .setCancelable(true)
                    .show()
                return
            }
        }
        updateCredential("note", EmojiDecoder.decodeShortCode(sv))
    }

    private fun sendLocked(willLocked: Boolean) {
        updateCredential("locked", willLocked)
    }

    private fun sendFields(bConfirmed: Boolean = false) {
        val args = ArrayList<Pair<String, String>>()
        var lengthLongest = -1
        for (i in listFieldNameInvalidator.indices) {
            val k = listFieldNameInvalidator[i].text.toString().trim()
            val v = listFieldValueInvalidator[i].text.toString().trim()
            args.add(Pair("fields_attributes[$i][name]", k))
            args.add(Pair("fields_attributes[$i][value]", v))

            lengthLongest = max(
                lengthLongest,
                max(
                    k.codePointCount(0, k.length),
                    v.codePointCount(0, v.length)
                )
            )
        }
        if (!bConfirmed && lengthLongest > max_length_fields) {
            AlertDialog.Builder(this)
                .setMessage(
                    getString(
                        R.string.length_warning,
                        getString(R.string.profile_metadata),
                        lengthLongest,
                        max_length_fields
                    )
                )
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ -> sendFields(bConfirmed = true) }
                .setCancelable(true)
                .show()
            return
        }

        updateCredential(args)
    }

    private fun pickAvatarImage() {
        openImagePickerOrCamera("avatar")
    }

    private fun pickHeaderImage() {
        openImagePickerOrCamera("header")
    }

    private fun openImagePickerOrCamera(propName: String) {
        state.propName = propName
        launchAndShowError {
            actionsDialog {
                action(getString(R.string.pick_image)) {
                    visualMediaPicker.open()
                }
                action(getString(R.string.image_capture)) {
                    cameraOpener.open()
                }
            }
        }
    }

    ///////////////////////////////////////////////////

    internal interface InputStreamOpener {
        val mimeType: String
        val uri: Uri
        fun open(): InputStream
        fun deleteTempFile()
    }

    private fun createOpener(uriArg: Uri, mimeType: String): InputStreamOpener {

        while (true) {
            try {

                // 画像の種別
                val isJpeg = MIME_TYPE_JPEG == mimeType
                val isPng = MIME_TYPE_PNG == mimeType
                if (!isJpeg && !isPng) {
                    log.d("createOpener: source is not jpeg or png")
                    break
                }

                // 設定からリサイズ指定を読む
                val resizeTo = 1280

                val bitmap = createResizedBitmap(this, uriArg, resizeTo)
                if (bitmap != null) {
                    try {
                        val cacheDir = externalCacheDir?.apply { mkdirs() }
                        if (cacheDir == null) {
                            showToast(false, "getExternalCacheDir returns null.")
                            break
                        }

                        val tempFile = File(
                            cacheDir,
                            "tmp." + System.currentTimeMillis() + "." + Thread.currentThread().id
                        )
                        FileOutputStream(tempFile).use { os ->
                            if (isJpeg) {
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os)
                            } else {
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                            }
                        }

                        return object : InputStreamOpener {

                            override val mimeType: String
                                get() = mimeType

                            override val uri: Uri
                                get() = uriArg

                            override fun open() = FileInputStream(tempFile)

                            override fun deleteTempFile() {
                                tempFile.delete()
                            }
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
            } catch (ex: Throwable) {
                log.e(ex, "Resizing image failed.")
                showToast(ex, "Resizing image failed.")
            }

            break
        }

        return object : InputStreamOpener {

            override val mimeType: String
                get() = mimeType

            override val uri: Uri
                get() = uriArg

            override fun open(): InputStream {
                return contentResolver.openInputStream(uri) ?: error("openInputStream returns null")
            }

            override fun deleteTempFile() {
            }
        }
    }

    private fun uploadImage(propName: String, src: UriAndType?) {
        src ?: return
        val uri = src.uri
        val mimeType = src.mimeType

        if (mimeType == null) {
            showToast(false, "mime type is not provided.")
            return
        }

        if (!mimeType.startsWith("image/")) {
            showToast(false, "mime type is not image.")
            return
        }

        launchProgress(
            "preparing image",
            doInBackground = { createOpener(uri, mimeType) },
            afterProc = { updateCredential(propName, it) }
        )
    }

    private suspend fun updatePushSubscription(force: Boolean): Boolean {
        val activity = this
        val anyNotificationWanted = account.notificationBoost ||
                account.notificationFavourite ||
                account.notificationFollow ||
                account.notificationMention ||
                account.notificationReaction ||
                account.notificationVote ||
                account.notificationFollowRequest ||
                account.notificationPost ||
                account.notificationUpdate

        val lines = ArrayList<String>()
        val subLogger = object : PushBase.SubscriptionLogger {
            override val context: Context
                get() = activity

            override fun i(msg: String) {
                log.w(msg)
                synchronized(lines) {
                    lines.add(msg)
                }
            }

            override fun e(msg: String) {
                log.e(msg)
                synchronized(lines) {
                    lines.add(msg)
                }
            }

            override fun e(ex: Throwable, msg: String) {
                log.e(ex, msg)
                synchronized(lines) {
                    lines.add(ex.withCaption(msg))
                }
            }
        }
        val rv = try {
            pushRepo.updateSubscription(
                subLogger,
                account,
                willRemoveSubscription = !anyNotificationWanted,
                forceUpdate = force,
            )
            true
        } catch (ex: Throwable) {
            subLogger.e(ex, "updateSubscription failed.")
            false
        }
        AlertDialog.Builder(activity)
            .setMessage("${account.acct}:\n${lines.joinToString("\n")}")
            .setPositiveButton(android.R.string.ok, null)
            .show()
        return rv
    }

    private fun showNotificationColor() {
        views.vNotificationAccentColorColor.backgroundColor =
            account.notificationAccentColor.notZero()
                ?: ContextCompat.getColor(this, R.color.colorOsNotificationAccent)
    }
}
