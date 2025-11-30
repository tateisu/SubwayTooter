package jp.juggler.subwaytooter.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import jp.juggler.subwaytooter.R

class PermissionSpec(
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
     */
    @StringRes val rationalId: Int,
) {
    fun listNotGranded(context: Context) =
        permissions.filter {
            ContextCompat.checkSelfPermission(context, it) !=
                    PackageManager.PERMISSION_GRANTED
        }

    /**
     * - 権限のどれかが不足している
     * - 不足した権限のどれかが shouldShowRequestPermissionRationale == trueである
     */
    fun shouldShowRational(activity: Activity) =
        permissions.any {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }
}

val permissionSpecNotification = when {
    Build.VERSION.SDK_INT >= 33 -> PermissionSpec(
        permissions = listOf(
            Manifest.permission.POST_NOTIFICATIONS,
        ),
        deniedId = R.string.permission_denied_notifications,
        rationalId = R.string.permission_rational_notifications,
    )
    else -> PermissionSpec(
        permissions = emptyList(),
        deniedId = R.string.permission_denied_notifications,
        rationalId = R.string.permission_rational_notifications,
    )
}

val permissionSpecMediaDownload = when {
    Build.VERSION.SDK_INT >= 33 -> PermissionSpec(
        permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
        deniedId = R.string.permission_denied_download_manager,
        rationalId = R.string.permission_rational_download_manager,
    )
    else -> PermissionSpec(
        permissions = listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
        deniedId = R.string.permission_denied_media_access,
        rationalId = R.string.permission_rational_media_access,
    )
}

/**
 * 静止画のみを端末から選ぶ
 */
val permissionSpecImagePicker = when {
    Build.VERSION.SDK_INT >= 34 -> PermissionSpec(
        permissions = emptyList(),
        deniedId = R.string.permission_denied_media_access,
        rationalId = R.string.permission_rational_media_access,
    )
    Build.VERSION.SDK_INT >= 33 -> PermissionSpec(
        permissions = listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
        ),
        deniedId = R.string.permission_denied_media_access,
        rationalId = R.string.permission_rational_media_access,
    )
    else -> PermissionSpec(
        permissions = listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
        deniedId = R.string.permission_denied_media_access,
        rationalId = R.string.permission_rational_media_access,
    )
}

/**
 * オーディオのみを端末から選ぶ
 */
val permissionSpecAudioPicker = when {
    Build.VERSION.SDK_INT >= 33 -> PermissionSpec(
        permissions = listOf(
            Manifest.permission.READ_MEDIA_AUDIO,
        ),
        deniedId = R.string.permission_denied_media_access,
        rationalId = R.string.permission_rational_media_access,
    )
    else -> {
        PermissionSpec(
            permissions = listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            deniedId = R.string.permission_denied_media_access,
            rationalId = R.string.permission_rational_media_access,
        )
    }
}

/**
 * カメラ撮影用の実行時パーミッション
 * https://developer.android.com/media/camera/camera-deprecated/photobasics?hl=ja#TaskPhotoView
 */
val permissionSpecCamera = when {
    Build.VERSION.SDK_INT >= 29 -> PermissionSpec(
        permissions = emptyList(),
        deniedId = R.string.permission_denied_media_access,
        rationalId = R.string.permission_rational_media_access,
    )
    else -> PermissionSpec(
        permissions = listOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ),
        deniedId = R.string.permission_denied_media_access,
        rationalId = R.string.permission_rational_media_access,
    )
}
/**
 * 動画や音声のキャプチャ前
 */
val permissionSpecCapture = when {
    Build.VERSION.SDK_INT >= 29 -> PermissionSpec(
        permissions = emptyList(),
        deniedId = R.string.permission_denied_media_access,
        rationalId = R.string.permission_rational_media_access,
    )
    else -> PermissionSpec(
        permissions = listOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ),
        deniedId = R.string.permission_denied_media_access,
        rationalId = R.string.permission_rational_media_access,
    )
}
