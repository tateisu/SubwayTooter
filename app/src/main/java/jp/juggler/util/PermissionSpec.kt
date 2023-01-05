package jp.juggler.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
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
}

val permissionSpecNotification = if (Build.VERSION.SDK_INT >= 33) {
    PermissionSpec(
        permissions = listOf(
            Manifest.permission.POST_NOTIFICATIONS,
        ),
        deniedId = R.string.permission_denied_notifications,
        rationalId = R.string.permission_rational_notifications,
    )
} else {
    PermissionSpec(
        permissions = emptyList(),
        deniedId = R.string.permission_denied_notifications,
        rationalId = R.string.permission_rational_notifications,
    )
}

val permissionSpecMediaDownload = if (Build.VERSION.SDK_INT >= 33) {
    PermissionSpec(
        permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
        deniedId = R.string.permission_denied_download_manager,
        rationalId = R.string.permission_rational_download_manager,
    )
} else {
    PermissionSpec(
        permissions = listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
        deniedId = R.string.permission_denied_media_access,
        rationalId = R.string.permission_rational_media_access,
    )
}

val permissionSpecImagePicker = if (Build.VERSION.SDK_INT >= 33) {
    PermissionSpec(
        permissions = listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        ),
        deniedId = R.string.permission_denied_media_access,
        rationalId = R.string.permission_rational_media_access,
    )
} else {
    PermissionSpec(
        permissions = listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
        deniedId = R.string.permission_denied_media_access,
        rationalId = R.string.permission_rational_media_access,
    )
}
