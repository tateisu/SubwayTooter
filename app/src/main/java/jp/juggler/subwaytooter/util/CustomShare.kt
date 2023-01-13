package jp.juggler.subwaytooter.util

import android.content.*
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.pref.PrefS
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.*
import jp.juggler.util.data.*
import jp.juggler.util.log.*
import jp.juggler.util.ui.*

enum class CustomShareTarget {
    Translate,
    CustomShare1,
    CustomShare2,
    CustomShare3,
}

object CustomShare {

    val log = LogCategory("CustomShare")

    const val CN_CLIPBOARD = "<InApp>/CopyToClipboard"

    private const val translate_app_component_default =
        "com.google.android.apps.translate/com.google.android.apps.translate.TranslateActivity"

    fun getCustomShareComponentName(
        target: CustomShareTarget,
    ): ComponentName? {
        val src: String
        val defaultComponentName: String?
        when (target) {
            CustomShareTarget.Translate -> {
                src = PrefS.spTranslateAppComponent()
                defaultComponentName = translate_app_component_default
            }

            CustomShareTarget.CustomShare1 -> {
                src = PrefS.spCustomShare1()
                defaultComponentName = null
            }

            CustomShareTarget.CustomShare2 -> {
                src = PrefS.spCustomShare2()
                defaultComponentName = null
            }

            CustomShareTarget.CustomShare3 -> {
                src = PrefS.spCustomShare3()
                defaultComponentName = null
            }
        }
        return src.cn() ?: defaultComponentName?.cn()
    }

    fun getInfo(context: Context, cn: ComponentName?): Pair<CharSequence?, Drawable?> {
        var label: CharSequence? = null
        var icon: Drawable? = null
        try {
            if (cn != null) {
                val cnStr = "${cn.packageName}/${cn.className}"
                label = cnStr
                if (cnStr == CN_CLIPBOARD) {
                    label =
                        "${context.getString(R.string.copy_to_clipboard)}(${context.getString(R.string.app_name)})"
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_copy)?.mutate()?.apply {
                        setTint(context.attrColor(R.attr.colorContentText))
                        setTintMode(PorterDuff.Mode.SRC_IN)
                    }
                } else {
                    val pm = context.packageManager
                    val ri = pm.resolveActivityCompat(Intent().apply { component = cn })
                    if (ri != null) {
                        try {
                            label = ri.loadLabel(pm)
                        } catch (ex: Throwable) {
                            log.e(ex, "loadLabel failed.")
                        }
                        try {
                            icon = ri.loadIcon(pm)
                        } catch (ex: Throwable) {
                            log.e(ex, "loadIcon failed.")
                        }
                    }
                }
            }
        } catch (ex: Throwable) {
            log.e(ex, "getInfo failed.")
        }
        return Pair(label, icon)
    }

    fun invokeText(
        target: CustomShareTarget,
        context: Context,
        text: String,
    ) {
        // convert "pkgName/className" string to ComponentName object.
        val cn = getCustomShareComponentName(target)
        if (cn == null) {
            context.showToast(true, R.string.custom_share_app_not_found)
            return
        }
        val cnStr = "${cn.packageName}/${cn.className}"
        if (cnStr == CN_CLIPBOARD) {
            try {
                val cm: ClipboardManager = systemService(context)!!
                cm.setPrimaryClip(ClipData.newPlainText("", text))
                context.showToast(false, R.string.copied_to_clipboard)
            } catch (ex: Throwable) {
                context.showToast(ex, "copy to clipboard failed.")
            }
            return
        }

        // Google翻訳は共有したテキストの先頭にURLがあるとGoogle翻訳のページをブラウザに飛ばすようになって、
        // しかもFirefoxだと開けなくて全然ダメなので対策する
        val textSanitized = if (cnStr.startsWith("com.google.android.apps.translate") &&
            text.startsWith("http")
        ) {
            "■ $text"
        } else {
            text
        }

        try {
            val intent = Intent()
            intent.action = Intent.ACTION_SEND
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, textSanitized)
            intent.component = cn
            context.startActivity(intent)
        } catch (ex: ActivityNotFoundException) {
            log.e(ex, "missing custom share app. $cn")
            context.showToast(true, R.string.custom_share_app_not_found)
        } catch (ex: Throwable) {
            log.e(ex, "invokeText() failed.")
            context.showToast(ex, "invokeText() failed.")
        }
    }

    fun invokeStatusText(
        target: CustomShareTarget,
        context: Context,
        accessInfo: SavedAccount,
        status: TootStatus?,
    ) {
        status ?: return
        try {
            // convert "pkgName/className" string to ComponentName object.
            val cn = getCustomShareComponentName(target)
            if (cn == null) {
                context.showToast(true, R.string.custom_share_app_not_found)
                return
            }
            val sv = TootTextEncoder.encodeStatusForTranslate(context, accessInfo, status)
            invokeText(target, context, sv)
        } catch (ex: Throwable) {
            log.e(ex, "invokeStatusText() failed.")
            context.showToast(ex, "invokeStatusText() failed.")
        }
    }

    /**
     * 翻訳アプリが利用できるなら真
     * ただしクリップボードは偽
     */
    fun hasTranslateApp(
        target: CustomShareTarget,
        context: Context,
    ) = try {
        getCustomShareComponentName(target)?.let { cn ->
            val cnStr = "${cn.packageName}/${cn.className}"
            if (cnStr == CN_CLIPBOARD) {
                false
            } else {
                val intent = Intent()
                intent.action = Intent.ACTION_SEND
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TEXT, "this is a test")
                intent.component = cn
                val ri = context.packageManager.resolveActivityCompat(intent)
                ri != null
            }
        }
    } catch (ignores: Throwable) {
        null
    } ?: false

    private val cache = HashMap<CustomShareTarget, Pair<CharSequence?, Drawable?>>()

    fun getCache(target: CustomShareTarget) = cache[target]

    fun reloadCache(context: Context) {
        CustomShareTarget.values().forEach { target ->
            val cn = getCustomShareComponentName(target)
            val pair = getInfo(context, cn)
            cache[target] = pair
        }
    }
}

// convert "pkgName/className" string to ComponentName object.
fun String.cn(): ComponentName? {
    try {
        val idx = indexOf('/')
        if (idx >= 1) return ComponentName(substring(0 until idx), substring(idx + 1))
    } catch (ex: Throwable) {
        CustomShare.log.e(ex, "incorrect component name $this")
    }
    return null
}

fun ComponentName.exists(context: Context): Boolean {
    return try {
        @Suppress("DEPRECATION")
        context.packageManager.resolveActivityCompat(Intent().apply { component = this@exists })
            ?.activityInfo?.exported ?: false
    } catch (_: Throwable) {
        false
    }
}
