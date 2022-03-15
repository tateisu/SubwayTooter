package jp.juggler.subwaytooter

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.text.Editable
import android.text.SpannableString
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import jp.juggler.subwaytooter.Styler.defaultColorIcon
import jp.juggler.subwaytooter.action.accountRemove
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.databinding.ActAccountSettingBinding
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.notification.NotificationHelper
import jp.juggler.subwaytooter.notification.PollingWorker
import jp.juggler.subwaytooter.notification.PushSubscriptionHelper
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.pref.PrefS
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.*
import jp.juggler.util.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.textColor
import java.io.*
import kotlin.math.max

class ActAccountSetting : AppCompatActivity(), View.OnClickListener,
    CompoundButton.OnCheckedChangeListener, AdapterView.OnItemSelectedListener {

    companion object {

        internal val log = LogCategory("ActAccountSetting")

        internal const val KEY_ACCOUNT_DB_ID = "account_db_id"

        internal const val RESULT_INPUT_ACCESS_TOKEN = Activity.RESULT_FIRST_USER + 10
        internal const val EXTRA_DB_ID = "db_id"

        internal const val max_length_display_name = 30
        internal const val max_length_note = 160
        internal const val max_length_fields = 255

        private const val PERMISSION_REQUEST_AVATAR = 1
        private const val PERMISSION_REQUEST_HEADER = 2

        internal const val MIME_TYPE_JPEG = "image/jpeg"
        internal const val MIME_TYPE_PNG = "image/png"

        private const val ACTIVITY_STATE = "MyActivityState"

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

    private lateinit var viewBinding: ActAccountSettingBinding

    private var notificationSoundUri: String? = null

    private lateinit var nameInvalidator: NetworkEmojiInvalidator
    private lateinit var noteInvalidator: NetworkEmojiInvalidator
    private lateinit var defaultTextInvalidator: NetworkEmojiInvalidator

    private var loadingBusy = false
    private var profileBusy = false

    private lateinit var listEtFieldName: List<EditText>
    private lateinit var listEtFieldValue: List<EditText>
    private lateinit var listFieldNameInvalidator: List<NetworkEmojiInvalidator>
    private lateinit var listFieldValueInvalidator: List<NetworkEmojiInvalidator>
    private lateinit var btnFields: View

    private class ResizeItem(val config: ResizeConfig, val caption: String)

    private lateinit var imageResizeItems: List<ResizeItem>

    private class PushPolicyItem(val id: String?, val caption: String)

    private lateinit var pushPolicyItems: List<PushPolicyItem>

    internal var visibility = TootVisibility.Public

    ///////////////////////////////////////////////////////////////////

    private val arShowAcctColor = activityResultHandler { ar ->
        if (ar?.resultCode == Activity.RESULT_OK) {
            showAcctColor()
        }
    }

    private val arNotificationSound = activityResultHandler { ar ->
        if (ar?.resultCode == Activity.RESULT_OK) {
            // RINGTONE_PICKERからの選択されたデータを取得する
            val uri = ar.data?.extras?.get(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri is Uri) {
                notificationSoundUri = uri.toString()
                saveUIToData()
                //			Ringtone ringtone = RingtoneManager.getRingtone(getApplicationContext(), uri);
                //			TextView ringView = (TextView) findViewById(R.id.ringtone);
                //			ringView.setText(ringtone.getTitle(getApplicationContext()));
                //			ringtone.setStreamType(AudioManager.STREAM_ALARM);
                //			ringtone.play();
                //			SystemClock.sleep(1000);
                //			ringtone.stop();
            }
        }
    }

    private val arAddAttachment = activityResultHandler { ar ->
        if (ar?.resultCode == Activity.RESULT_OK) {
            ar.data
                ?.handleGetContentResult(contentResolver)
                ?.firstOrNull()
                ?.let {
                    uploadImage(
                        state.propName,
                        it.uri,
                        it.mimeType?.notEmpty() ?: contentResolver.getType(it.uri)
                    )
                }
        }
    }

    private val arCameraImage = activityResultHandler { ar ->
        if (ar?.resultCode == Activity.RESULT_OK) {
            // 画像のURL
            val uri = ar.data?.data ?: state.uriCameraImage
            if (uri != null) {
                val type = contentResolver.getType(uri)
                uploadImage(state.propName, uri, type)
            }
        } else {
            // 失敗したら DBからデータを削除
            state.uriCameraImage?.let {
                contentResolver.delete(it, null, null)
            }
            state.uriCameraImage = null
        }
    }

    ///////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arShowAcctColor.register(this, log)
        arNotificationSound.register(this, log)
        arAddAttachment.register(this, log)
        arCameraImage.register(this, log)

        if (savedInstanceState != null) {
            savedInstanceState.getString(ACTIVITY_STATE)
                ?.let { state = kJson.decodeFromString(it) }
        }

        App1.setActivityTheme(this)

        initUI()

        val a = SavedAccount.loadAccount(this, intent.getLongExtra(KEY_ACCOUNT_DB_ID, -1L))
        if (a == null) {
            finish()
            return
        }

        loadUIFromData(a)

        initializeProfile()

        viewBinding.btnOpenBrowser.text =
            getString(R.string.open_instance_website, account.apiHost.pretty)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        val encodedState = kJson.encodeToString(state)
        log.d("encodedState=$encodedState")
        val decodedState: State = kJson.decodeFromString(encodedState)
        log.d("encodedState.uriCameraImage=${decodedState.uriCameraImage}")
        outState.putString(ACTIVITY_STATE, encodedState)
    }

    override fun onStop() {
        PollingWorker.queueUpdateNotification(this)
        super.onStop()
    }

    var density: Float = 1f

    @Suppress("LongMethod")
    private fun initUI() {
        this.density = resources.displayMetrics.density
        this.handler = App1.getAppState(this).handler
        this.viewBinding = ActAccountSettingBinding.inflate(layoutInflater, null, false)
        setContentView(viewBinding.root)

        App1.initEdgeToEdge(this)
        Styler.fixHorizontalPadding(viewBinding.root)
        setSwitchColor(viewBinding.root)

        viewBinding.apply {
            btnPushSubscriptionNotForce.vg(BuildConfig.DEBUG)

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

            listEtFieldName = intArrayOf(
                R.id.etFieldName1,
                R.id.etFieldName2,
                R.id.etFieldName3,
                R.id.etFieldName4
            ).map { findViewById(it) }

            listEtFieldValue = intArrayOf(
                R.id.etFieldValue1,
                R.id.etFieldValue2,
                R.id.etFieldValue3,
                R.id.etFieldValue4
            ).map { findViewById(it) }

            btnNotificationStyleEditReply.vg(PrefB.bpSeparateReplyNotificationGroup())

            nameInvalidator = NetworkEmojiInvalidator(handler, etDisplayName)
            noteInvalidator = NetworkEmojiInvalidator(handler, etNote)
            defaultTextInvalidator = NetworkEmojiInvalidator(handler, etDefaultText)

            listFieldNameInvalidator = listEtFieldName.map {
                NetworkEmojiInvalidator(handler, it)
            }

            listFieldValueInvalidator = listEtFieldValue.map {
                NetworkEmojiInvalidator(handler, it)
            }

            val watcher1 = simpleTextWatcher { saveUIToData() }
            arrayOf(
                etDefaultText,
                etMediaSizeMax,
                etMovieSizeMax,
                etMovieBitrate,
                etMovieFrameRate,
                etMovieSquarePixels,
            ).forEach {
                it.addTextChangedListener(watcher1)
            }

            etMaxTootChars.addTextChangedListener(
                simpleTextWatcher {
                    val num = etMaxTootChars.parseInt()
                    if (num != null && num >= 0) {
                        saveUIToData()
                    }
                }
            )

            arrayOf(
                btnOpenBrowser,
                btnPushSubscription,
                btnPushSubscriptionNotForce,
                btnResetNotificationTracking,
                btnAccessToken,
                btnInputAccessToken,
                btnAccountRemove,
                btnLoadPreference,
                btnVisibility,
                btnUserCustom,
                btnProfileAvatar,
                btnProfileHeader,
                btnDisplayName,
                btnNote,
                btnFields,
                btnNotificationSoundEdit,
                btnNotificationSoundReset,
                btnNotificationStyleEdit,
                btnNotificationStyleEditReply,
            ).forEach { it.setOnClickListener(this@ActAccountSetting) }

            arrayOf(
                swNSFWOpen,
                swDontShowTimeout,
                swExpandCW,
                swMarkSensitive,
                cbNotificationMention,
                cbNotificationBoost,
                cbNotificationFavourite,
                cbNotificationFollow,
                cbNotificationFollowRequest,
                cbNotificationReaction,
                cbNotificationVote,
                cbNotificationPost,
                cbNotificationUpdate,
                cbLocked,
                cbConfirmFollow,
                cbConfirmFollowLockedUser,
                cbConfirmUnfollow,
                cbConfirmBoost,
                cbConfirmFavourite,
                cbConfirmUnboost,
                cbConfirmUnfavourite,
                cbConfirmToot,
                cbConfirmReaction,
            ).forEach { it.setOnCheckedChangeListener(this@ActAccountSetting) }

            arrayOf(
                spResizeImage,
                spPushPolicy,
                spMovieTranscodeMode,
            ).forEach { it.onItemSelectedListener = this@ActAccountSetting }
        }
    }

    private fun EditText.parseInt(): Int? =
        text?.toString()?.toIntOrNull()

    private fun loadUIFromData(a: SavedAccount) {
        this.account = a
        this.visibility = a.visibility
        loadingBusy = true
        try {

            viewBinding.apply {

                tvInstance.text = a.apiHost.pretty
                tvUser.text = a.acct.pretty
                swNSFWOpen.isChecked = a.dont_hide_nsfw
                swDontShowTimeout.isChecked = a.dont_show_timeout
                swExpandCW.isChecked = a.expand_cw
                swMarkSensitive.isChecked = a.default_sensitive
                cbNotificationMention.isChecked = a.notification_mention
                cbNotificationBoost.isChecked = a.notification_boost
                cbNotificationFavourite.isChecked = a.notification_favourite
                cbNotificationFollow.isChecked = a.notification_follow
                cbNotificationFollowRequest.isChecked = a.notification_follow_request
                cbNotificationReaction.isChecked = a.notification_reaction
                cbNotificationVote.isChecked = a.notification_vote
                cbNotificationPost.isChecked = a.notification_post
                cbNotificationUpdate.isChecked = a.notification_update

                cbConfirmFollow.isChecked = a.confirm_follow
                cbConfirmFollowLockedUser.isChecked = a.confirm_follow_locked
                cbConfirmUnfollow.isChecked = a.confirm_unfollow
                cbConfirmBoost.isChecked = a.confirm_boost
                cbConfirmFavourite.isChecked = a.confirm_favourite
                cbConfirmUnboost.isChecked = a.confirm_unboost
                cbConfirmUnfavourite.isChecked = a.confirm_unfavourite
                cbConfirmToot.isChecked = a.confirm_post
                cbConfirmReaction.isChecked = a.confirm_reaction
                cbConfirmUnbookmark.isChecked = a.confirm_unbookmark

                notificationSoundUri = a.sound_uri

                etDefaultText.setText(a.default_text)
                etMaxTootChars.setText(a.max_toot_chars.toString())

                val ti = TootInstance.getCached(a)
                if (ti == null) {
                    etMediaSizeMax.setText(a.image_max_megabytes ?: "")
                    etMovieSizeMax.setText(a.movie_max_megabytes ?: "")
                } else {
                    etMediaSizeMax.setText(
                        a.image_max_megabytes
                            ?: a.getImageMaxBytes(ti).div(1000000).toString()
                    )
                    etMovieSizeMax.setText(
                        a.movie_max_megabytes
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

                val currentPushPolicy = a.push_policy
                index = pushPolicyItems.indexOfFirst { it.id == currentPushPolicy }
                if (index == -1) index = 0
                spPushPolicy.setSelection(index, false)

                spMovieTranscodeMode.setSelection(max(0, a.movieTranscodeMode), false)
                etMovieFrameRate.setText(a.movieTranscodeFramerate)
                etMovieBitrate.setText(a.movieTranscodeBitrate)
                etMovieSquarePixels.setText(a.movieTranscodeSquarePixels)

                // アカウントからUIへのデータロードはここまで
                loadingBusy = false

                val enabled = !a.isPseudo

                arrayOf(
                    btnAccessToken,
                    btnInputAccessToken,
                    btnVisibility,
                    btnPushSubscription,
                    btnPushSubscriptionNotForce,
                    btnResetNotificationTracking,
                    cbNotificationMention,
                    cbNotificationBoost,
                    cbNotificationFavourite,
                    cbNotificationFollow,
                    cbNotificationFollowRequest,
                    cbNotificationReaction,
                    cbNotificationVote,
                    cbNotificationPost,
                    cbNotificationUpdate,
                    cbConfirmFollow,
                    cbConfirmFollowLockedUser,
                    cbConfirmUnfollow,
                    cbConfirmBoost,
                    cbConfirmFavourite,
                    cbConfirmUnboost,
                    cbConfirmUnfavourite,
                    cbConfirmToot,
                    cbConfirmReaction,
                ).forEach { it.isEnabledAlpha = enabled }

                val enabledOldNotification = enabled && Build.VERSION.SDK_INT < 26
                arrayOf(
                    btnNotificationSoundEdit,
                    btnNotificationSoundReset,
                ).forEach { it.isEnabledAlpha = enabledOldNotification }

                val enabledNewNotification = enabled && Build.VERSION.SDK_INT >= 26
                arrayOf(
                    btnNotificationStyleEdit,
                    btnNotificationStyleEditReply,
                ).forEach { it.isEnabledAlpha = enabledNewNotification }
            }

            showVisibility()
            showAcctColor()
        } finally {
            loadingBusy = false
        }
    }

    private fun showAcctColor() {
        val sa = this.account
        val ac = AcctColor.load(sa)
        viewBinding.tvUserCustom.apply {
            backgroundColor = ac.color_bg
            text = ac.nickname
            textColor = ac.color_fg.notZero() ?: attrColor(R.attr.colorTimeSmall)
        }
    }

    private fun saveUIToData() {
        if (!::account.isInitialized) return
        if (loadingBusy) return
        account.visibility = visibility

        viewBinding.apply {

            account.dont_hide_nsfw = swNSFWOpen.isChecked
            account.dont_show_timeout = swDontShowTimeout.isChecked
            account.expand_cw = swExpandCW.isChecked
            account.default_sensitive = swMarkSensitive.isChecked
            account.notification_mention = cbNotificationMention.isChecked
            account.notification_boost = cbNotificationBoost.isChecked
            account.notification_favourite = cbNotificationFavourite.isChecked
            account.notification_follow = cbNotificationFollow.isChecked
            account.notification_follow_request = cbNotificationFollowRequest.isChecked
            account.notification_reaction = cbNotificationReaction.isChecked
            account.notification_vote = cbNotificationVote.isChecked
            account.notification_post = cbNotificationPost.isChecked
            account.notification_update = cbNotificationUpdate.isChecked

            account.confirm_follow = cbConfirmFollow.isChecked
            account.confirm_follow_locked = cbConfirmFollowLockedUser.isChecked
            account.confirm_unfollow = cbConfirmUnfollow.isChecked
            account.confirm_boost = cbConfirmBoost.isChecked
            account.confirm_favourite = cbConfirmFavourite.isChecked
            account.confirm_unboost = cbConfirmUnboost.isChecked
            account.confirm_unfavourite = cbConfirmUnfavourite.isChecked
            account.confirm_post = cbConfirmToot.isChecked
            account.confirm_reaction = cbConfirmReaction.isChecked
            account.confirm_unbookmark = cbConfirmUnbookmark.isChecked

            account.sound_uri = notificationSoundUri ?: ""
            account.default_text = etDefaultText.text.toString()

            val num = etMaxTootChars.parseInt()
            account.max_toot_chars = if (num != null && num >= 0) {
                num
            } else {
                0
            }

            account.movie_max_megabytes = etMovieSizeMax.text.toString().trim()
            account.image_max_megabytes = etMediaSizeMax.text.toString().trim()
            account.image_resize = (
                    imageResizeItems.elementAtOrNull(spResizeImage.selectedItemPosition)?.config
                        ?: SavedAccount.defaultResizeConfig
                    ).spec

            account.push_policy =
                pushPolicyItems.elementAtOrNull(spPushPolicy.selectedItemPosition)?.id

            account.movieTranscodeMode = spMovieTranscodeMode.selectedItemPosition
            account.movieTranscodeBitrate = etMovieBitrate.text.toString()
            account.movieTranscodeFramerate = etMovieFrameRate.text.toString()
            account.movieTranscodeSquarePixels = etMovieSquarePixels.text.toString()
        }

        account.saveSetting()
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (buttonView == viewBinding.cbLocked) {
            if (!profileBusy) sendLocked(isChecked)
        } else {
            saveUIToData()
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
            R.id.btnPushSubscription -> updatePushSubscription(force = true)
            R.id.btnPushSubscriptionNotForce -> updatePushSubscription(force = false)
            R.id.btnResetNotificationTracking ->
                PollingWorker.resetNotificationTracking(this, account)

            R.id.btnUserCustom -> arShowAcctColor.launch(
                ActNickname.createIntent(this, account.acct, false),

                )

            R.id.btnNotificationSoundEdit -> openNotificationSoundPicker()

            R.id.btnNotificationSoundReset -> {
                notificationSoundUri = ""
                saveUIToData()
            }

            R.id.btnProfileAvatar -> pickAvatarImage()

            R.id.btnProfileHeader -> pickHeaderImage()

            R.id.btnDisplayName -> sendDisplayName()

            R.id.btnNote -> sendNote()

            R.id.btnFields -> sendFields()

            R.id.btnNotificationStyleEdit ->
                NotificationHelper.openNotificationChannelSetting(
                    this,
                    account,
                    NotificationHelper.TRACKING_NAME_DEFAULT
                )

            R.id.btnNotificationStyleEditReply ->
                NotificationHelper.openNotificationChannelSetting(
                    this,
                    account,
                    NotificationHelper.TRACKING_NAME_REPLY
                )
        }
    }

    private fun showVisibility() {
        viewBinding.btnVisibility.text =
            Styler.getVisibilityString(this, account.isMisskey, visibility)
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
            Styler.getVisibilityCaption(this, account.isMisskey, it)
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
                        viewBinding.swMarkSensitive.isChecked = tmpDefaultSensitive
                    }

                    val tmpExpandMedia = json.string("reading:expand:media")
                    if (tmpExpandMedia?.isNotEmpty() == true) {
                        bChanged = true
                        viewBinding.swNSFWOpen.isChecked = (tmpExpandMedia == "show_all")
                    }

                    val tmpExpandCW = json.boolean("reading:expand:spoilers")
                    if (tmpExpandCW != null) {
                        bChanged = true
                        viewBinding.swExpandCW.isChecked = tmpExpandCW
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
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm)
            .setMessage(R.string.confirm_account_remove)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                accountRemove(account)
                finish()
            }
            .show()
    }

    ///////////////////////////////////////////////////
    private fun performAccessToken() {
        launchMain {
            runApiTask(account) { client ->
                client.authentication1(
                    PrefS.spClientName(this@ActAccountSetting),
                    forceUpdateClient = true
                )
            }?.let { result ->
                val uri = result.string.mayUri()
                val error = result.error
                when {
                    uri != null -> {
                        val data = Intent()
                        data.data = uri
                        setResult(Activity.RESULT_OK, data)
                        finish()
                    }

                    error != null -> {
                        showToast(true, error)
                        log.e("can't get oauth browser URL. $error")
                    }
                }
            }
        }
    }

    private fun inputAccessToken() {

        val data = Intent()
        data.putExtra(EXTRA_DB_ID, account.db_id)
        setResult(RESULT_INPUT_ACCESS_TOKEN, data)
        finish()
    }

    private fun openNotificationSoundPicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, R.string.notification_sound)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)

        notificationSoundUri.mayUri()?.let { uri ->
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri)
        }

        val chooser = Intent.createChooser(intent, getString(R.string.notification_sound))

        arNotificationSound.launch(chooser)
    }

    //////////////////////////////////////////////////////////////////////////

    private fun initializeProfile() {
        // 初期状態
        val questionId = R.drawable.wide_question
        val loadingText = when (account.isPseudo) {
            true -> "(disabled for pseudo account)"
            else -> "(loading…)"
        }

        viewBinding.apply {

            ivProfileAvatar.setErrorImage(defaultColorIcon(this@ActAccountSetting, questionId))
            ivProfileAvatar.setDefaultImage(defaultColorIcon(this@ActAccountSetting, questionId))

            etDisplayName.setText(loadingText)
            etNote.setText(loadingText)

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

            for (et in listEtFieldName) {
                et.setText(loadingText)
                et.isEnabledAlpha = false
            }
            for (et in listEtFieldValue) {
                et.setText(loadingText)
                et.isEnabledAlpha = false
            }

            // 疑似アカウントなら編集不可のまま
            if (!account.isPseudo) loadProfile()
        }
    }

    // サーバから情報をロードする
    private fun loadProfile() {
        launchMain {
            var resultAccount: TootAccount? = null
            runApiTask(account) { client ->
                if (account.isMisskey) {
                    client.request(
                        "/api/i",
                        account.putMisskeyApiToken().toPostRequestBuilder()
                    )?.also { result ->
                        val jsonObject = result.jsonObject
                        if (jsonObject != null) {
                            resultAccount = TootParser(this, account).account(jsonObject)
                                ?: return@runApiTask TootApiResult("TootAccount parse failed.")
                        }
                    }
                } else {
                    val r0 = account.checkConfirmed(this, client)
                    if (r0 == null || r0.error != null) return@runApiTask r0

                    client.request("/api/v1/accounts/verify_credentials")
                        ?.also { result ->
                            val jsonObject = result.jsonObject
                            if (jsonObject != null) {
                                resultAccount =
                                    TootParser(this@ActAccountSetting, account).account(jsonObject)
                                        ?: return@runApiTask TootApiResult("TootAccount parse failed.")
                            }
                        }
                }
            }?.let { result ->
                when (val account = resultAccount) {
                    null -> showToast(true, result.error)
                    else -> showProfile(account)
                }
            }
        }
    }

    internal fun showProfile(src: TootAccount) {

        if (isDestroyed) return

        profileBusy = true
        try {
            viewBinding.ivProfileAvatar.setImageUrl(
                Styler.calcIconRound(viewBinding.ivProfileAvatar.layoutParams),
                src.avatar_static,
                src.avatar
            )

            viewBinding.ivProfileHeader.setImageUrl(
                0f,
                src.header_static,
                src.header
            )

            val decodeOptions = DecodeOptions(
                context = this@ActAccountSetting,
                linkHelper = account,
                emojiMapProfile = src.profile_emojis,
                emojiMapCustom = src.custom_emojis,
                mentionDefaultHostDomain = account
            )

            val displayName = src.display_name
            val name = decodeOptions.decodeEmoji(displayName)
            viewBinding.etDisplayName.setText(name)
            nameInvalidator.register(name)

            val noteString = src.source?.note ?: src.note
            val noteSpannable = when {
                account.isMisskey -> {
                    SpannableString(noteString ?: "")
                }

                else -> {
                    decodeOptions.decodeEmoji(noteString)
                }
            }

            viewBinding.etNote.setText(noteSpannable)
            noteInvalidator.register(noteSpannable)

            viewBinding.cbLocked.isChecked = src.locked

            // 編集可能にする
            viewBinding.apply {
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
                listEtFieldName.forEachIndexed { i, et ->
                    val handler = et.handler // may null
                    if (handler != null) {
                        // いつからかfields name にもカスタム絵文字が使えるようになった
                        // https://github.com/tootsuite/mastodon/pull/11350
                        // しかし
                        val text = decodeOptions.decodeEmoji(
                            when {
                                i >= fields.size -> ""
                                else -> fields[i].name
                            }
                        )
                        et.setText(text)
                        et.isEnabledAlpha = true
                        val invalidator = NetworkEmojiInvalidator(handler, et)
                        invalidator.register(text)
                    }
                }

                listEtFieldValue.forEachIndexed { i, et ->
                    val handler = et.handler // may null
                    if (handler != null) {
                        val text = decodeOptions.decodeEmoji(
                            when {
                                i >= fields.size -> ""
                                else -> fields[i].value
                            }
                        )
                        et.setText(text)
                        et.isEnabledAlpha = true
                        val invalidator = NetworkEmojiInvalidator(handler, et)
                        invalidator.register(text)
                    }
                }
            } else {
                val fields = src.fields

                listEtFieldName.forEachIndexed { i, et ->
                    val handler = et.handler // may null
                    if (handler != null) {
                        // いつからかfields name にもカスタム絵文字が使えるようになった
                        // https://github.com/tootsuite/mastodon/pull/11350
                        val text = decodeOptions.decodeEmoji(
                            when {
                                fields == null || i >= fields.size -> ""
                                else -> fields[i].name
                            }
                        )
                        et.setText(text)
                        et.isEnabledAlpha = true
                        val invalidator = NetworkEmojiInvalidator(handler, et)
                        invalidator.register(text)
                    }
                }

                listEtFieldValue.forEachIndexed { i, et ->
                    val handler = et.handler // may null
                    if (handler != null) {
                        val text = decodeOptions.decodeHTML(
                            when {
                                fields == null || i >= fields.size -> ""
                                else -> fields[i].value
                            }
                        )
                        et.text = text
                        et.isEnabledAlpha = true
                        val invalidator = NetworkEmojiInvalidator(handler, et)
                        invalidator.register(text)
                    }
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
            account.token_info?.string(TootApiClient.KEY_API_KEY_MISSKEY)
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
            val jsonObject = result.jsonObject
            if (jsonObject != null) {
                ta = parseItem(::TootAttachment, ServiceType.MISSKEY, jsonObject)
                if (ta == null) result.error = "TootAttachment.parse failed"
            }
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
                                    })
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
                            viewBinding.cbLocked.isChecked = !value
                            profileBusy = false
                        }
                    }
                }
            }
        }
    }

    private fun sendDisplayName(bConfirmed: Boolean = false) {
        val sv = viewBinding.etDisplayName.text.toString()
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
        val sv = viewBinding.etNote.text.toString()
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
        for (i in listEtFieldName.indices) {
            val k = listEtFieldName[i].text.toString().trim()
            val v = listEtFieldValue[i].text.toString().trim()
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
        openPicker(PERMISSION_REQUEST_AVATAR)
    }

    private fun pickHeaderImage() {
        openPicker(PERMISSION_REQUEST_HEADER)
    }

    private fun openPicker(requestCode: Int) {
        val permissionCheck = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            preparePermission(requestCode)
            return
        }

        val propName = when (requestCode) {
            PERMISSION_REQUEST_HEADER -> "header"
            else -> "avatar"
        }

        val a = ActionsDialog()
        a.addAction(getString(R.string.pick_image)) {
            performAttachment(propName)
        }
        a.addAction(getString(R.string.image_capture)) {
            performCamera(propName)
        }
        a.show(this, null)
    }

    private fun preparePermission(requestCode: Int) {
        if (Build.VERSION.SDK_INT >= 23) {
            // No explanation needed, we can request the permission.

            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), requestCode
            )
            return
        }
        showToast(true, R.string.missing_permission_to_access_media)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        when (requestCode) {
            PERMISSION_REQUEST_AVATAR, PERMISSION_REQUEST_HEADER ->
                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openPicker(requestCode)
                } else {
                    showToast(true, R.string.missing_permission_to_access_media)
                }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun performAttachment(propName: String) {
        try {
            state.propName = propName
            val intent = intentGetContent(false, getString(R.string.pick_image), arrayOf("image/*"))
            arAddAttachment.launch(intent)
        } catch (ex: Throwable) {
            log.trace(ex, "performAttachment failed.")
            showToast(ex, "performAttachment failed.")
        }
    }

    private fun performCamera(propName: String) {

        try {
            // カメラで撮影
            val filename = System.currentTimeMillis().toString() + ".jpg"
            val values = ContentValues()
            values.put(MediaStore.Images.Media.TITLE, filename)
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            state.uriCameraImage = uri

            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)

            state.propName = propName
            arCameraImage.launch(intent)
        } catch (ex: Throwable) {
            log.trace(ex, "opening camera app failed.")
            showToast(ex, "opening camera app failed.")
        }
    }

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
                log.trace(ex, "Resizing image failed.")
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

    private fun uploadImage(propName: String, uri: Uri, mimeType: String?) {

        if (mimeType == null) {
            showToast(false, "mime type is not provided.")
            return
        }

        if (!mimeType.startsWith("image/")) {
            showToast(false, "mime type is not image.")
            return
        }

        launchMain {
            runWithProgress(
                "preparing image",
                { createOpener(uri, mimeType) },
                { updateCredential(propName, it) }
            )
        }
    }

    private fun updatePushSubscription(force: Boolean) {
        val wps = PushSubscriptionHelper(applicationContext, account, verbose = true)
        launchMain {
            runApiTask(account) { client ->
                wps.updateSubscription(client, force = force)
            }?.let {
                val log = wps.logString
                if (log.isNotEmpty()) {
                    AlertDialog.Builder(this@ActAccountSetting)
                        .setMessage(log)
                        .setPositiveButton(R.string.close, null)
                        .show()
                }
            }
        }
    }
}
