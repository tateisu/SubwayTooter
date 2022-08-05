package jp.juggler.util

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import jp.juggler.subwaytooter.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException

/**
 * ActivityResultLauncherを使ってパーミッション要求とその結果の処理を行う
 */
class PermissionRequester(
    /**
     * 必要なパーミッションのリスト
     */
    val permissions: List<String>,

    /**
     * 要求が拒否された場合に表示するメッセージのID
     */
    @StringRes val deniedId: Int,

    /**
     * なぜ権限が必要なのか説明するメッセージのID。
     * デフォルトは0で、この場合はメッセージを出さない。
     */
    @StringRes val rationalId: Int = 0,

    /**
     * 権限が与えられた際に処理を再開するラムダ
     * - ラムダの引数にこのPermissionRequester自身が渡される
     */
    val onGrant: (PermissionRequester) -> Unit,
) : ActivityResultCallback<Map<String, Boolean>> {
    companion object {
        private val log = LogCategory("PermissionRequester")
    }

    private var launcher: ActivityResultLauncher<Array<String>>? = null

    private var getContext: (() -> Context?)? = null

    private val activity
        get() = getContext?.invoke() as? FragmentActivity

    // ActivityのonCreate()から呼び出す
    fun register(activity: FragmentActivity) {
        getContext = { activity }
        launcher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
            this,
        )
    }

    // FragmentのonCreate()から呼び出す
    fun register(fragment: Fragment) {
        getContext = { fragment.activity ?: fragment.context }
        launcher = fragment.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
            this,
        )
    }

    /**
     * 実行時権限が全て揃っているならtrueを返す
     * そうでなければ権限の要求を行い、falseを返す
     */
    fun checkOrLaunch(): Boolean {
        val activity = activity ?: error("missing activity.")
        val listNotGranted = permissions.filter {
            PackageManager.PERMISSION_GRANTED !=
                    ContextCompat.checkSelfPermission(activity, it)
        }
        if (listNotGranted.isEmpty()) return true

        launchMain {
            try {
                if (Build.VERSION.SDK_INT < 23) {
                    activity.showToast(true, deniedId)
                    return@launchMain
                }

                val shouldShowRational = listNotGranted.any {
                    shouldShowRequestPermissionRationale(activity, it)
                }
                if (shouldShowRational && rationalId != 0) {
                    suspendCancellableCoroutine { cont ->
                        AlertDialog.Builder(activity)
                            .setMessage(rationalId)
                            .setNegativeButton(R.string.cancel, null)
                            .setPositiveButton(R.string.ok) { _, _ ->
                                if (cont.isActive) cont.resumeWith(Result.success(Unit))
                            }
                            .setOnDismissListener {
                                if (cont.isActive) cont.resumeWithException(CancellationException())
                            }
                            .create()
                            .also { dialog -> cont.invokeOnCancellation { dialog.dismissSafe() } }
                            .show()
                    }
                }
                launcher!!.launch(listNotGranted.toTypedArray())
            } catch (ex: Throwable) {
                if (ex !is CancellationException) {
                    activity.showToast(ex, "can't request permissions.")
                }
            }
        }
        return false
    }

    /**
     * 権限要求の結果を処理する
     * @param result 「パーミッション名」と「それが許可されているなら真」のマップ
     */
    override fun onActivityResult(result: Map<String, Boolean>?) {
        try {
            result ?: error("missing result.")
            val listNotGranted = result.entries.filter { !it.value }.map { it.key }
            if (listNotGranted.isEmpty()) {
                // すべて許可されている
                onGrant(this)
                return
            }
            // 許可されなかった。
            val activity = activity ?: error("missing activity.")
            AlertDialog.Builder(activity)
                .setMessage(deniedId)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.setting) { _, _ ->
                    openAppSetting(activity)
                }
                .show()
        } catch (ex: Throwable) {
            log.trace(ex, "can't handle result.")
        }
    }

    private fun openAppSetting(activity: FragmentActivity) {
        try {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${activity.packageName}".toUri()
            ).let { activity.startActivity(it) }
        } catch (ex: Throwable) {
            activity.showToast(ex, "openAppSetting failed.")
        }
    }
}
