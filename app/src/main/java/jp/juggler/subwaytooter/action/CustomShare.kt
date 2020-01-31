package jp.juggler.subwaytooter.action

import android.content.*
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.core.content.ContextCompat
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.TootTextEncoder
import jp.juggler.util.LogCategory
import jp.juggler.util.getAttributeColor
import jp.juggler.util.showToast
import jp.juggler.util.systemService

enum class CustomShareTarget {
	Translate,
	CustomShare1,
	CustomShare2,
	CustomShare3,
}

object CustomShare {
	
	private val log = LogCategory("CustomShare")
	
	const val CN_CLIPBOARD = "<InApp>/CopyToClipboard"
	
	private const val translate_app_component_default =
		"com.google.android.apps.translate/com.google.android.apps.translate.TranslateActivity"
	
	// convert "pkgName/className" string to ComponentName object.
	private fun String.cn() : ComponentName? {
		try {
			val idx = indexOf('/')
			if(idx >= 1) return ComponentName(substring(0 until idx), substring(idx + 1))
		} catch(ex : Throwable) {
			log.e(ex, "incorrect component name $this")
		}
		return null
	}
	
	fun getCustomShareComponentName(
		pref : SharedPreferences,
		target : CustomShareTarget
	) : ComponentName? {
		val src : String
		val defaultComponentName : String?
		when(target) {
			CustomShareTarget.Translate -> {
				src = Pref.spTranslateAppComponent(pref)
				defaultComponentName = translate_app_component_default
			}
			
			CustomShareTarget.CustomShare1 -> {
				src = Pref.spCustomShare1(pref)
				defaultComponentName = null
			}
			
			CustomShareTarget.CustomShare2 -> {
				src = Pref.spCustomShare2(pref)
				defaultComponentName = null
			}
			
			CustomShareTarget.CustomShare3 -> {
				src = Pref.spCustomShare3(pref)
				defaultComponentName = null
			}
		}
		return src.cn() ?: defaultComponentName?.cn()
	}
	
	fun getInfo(context : Context, cn : ComponentName?) : Pair<CharSequence?, Drawable?> {
		var label : CharSequence? = null
		var icon : Drawable? = null
		try {
			if(cn != null) {
				val cnStr = "${cn.packageName}/${cn.className}"
				label = cnStr
				if(cnStr == CN_CLIPBOARD) {
					label = context.getString(R.string.copy_to_clipboard)
					icon = ContextCompat.getDrawable(context, R.drawable.ic_copy)?.mutate()?.apply{
						setTint(getAttributeColor(context,R.attr.colorVectorDrawable))
						setTintMode(PorterDuff.Mode.SRC_IN)
					}
				} else {
					val pm = context.packageManager
					val ri = pm.resolveActivity(Intent().apply { component = cn }, 0)
					if(ri != null) {
						try {
							label = ri.loadLabel(pm)
						} catch(ex : Throwable) {
							log.e(ex, "loadLabel failed.")
						}
						try {
							icon = ri.loadIcon(pm)
						} catch(ex : Throwable) {
							log.e(ex, "loadIcon failed.")
						}
					}
				}
			}
		} catch(ex : Throwable) {
			log.e(ex, "getInfo failed.")
		}
		return Pair(label, icon)
	}
	
	fun invoke(
		context : Context,
		text : String,
		target : CustomShareTarget
	) {
		// convert "pkgName/className" string to ComponentName object.
		val cn = getCustomShareComponentName(App1.pref, target)
		if(cn == null) {
			showToast(context, true, R.string.custom_share_app_not_found)
			return
		}
		val cnStr = "${cn.packageName}/${cn.className}"
		if(cnStr == CN_CLIPBOARD) {
			try {
				val cm : ClipboardManager = systemService(context) !!
				cm.setPrimaryClip(ClipData.newPlainText("", text))
				showToast(context, false, R.string.copied_to_clipboard)
			} catch(ex : Throwable) {
				showToast(context, ex, "copy to clipboard failed.")
			}
			return
		}
		try {
			val intent = Intent()
			intent.action = Intent.ACTION_SEND
			intent.type = "text/plain"
			intent.putExtra(Intent.EXTRA_TEXT, text)
			intent.component = cn
			context.startActivity(intent)
		} catch(ex : ActivityNotFoundException) {
			log.trace(ex)
			showToast(context, true, R.string.custom_share_app_not_found)
		} catch(ex : Throwable) {
			log.trace(ex)
			showToast(context, ex, "invoke() failed.")
		}
		
	}
	
	fun invoke(
		context : Context,
		access_info : SavedAccount,
		status : TootStatus?,
		target : CustomShareTarget
	) {
		status ?: return
		try {
			// convert "pkgName/className" string to ComponentName object.
			val cn = getCustomShareComponentName(App1.pref, target)
			if(cn == null) {
				showToast(context, true, R.string.custom_share_app_not_found)
				return
			}
			
			val sv = TootTextEncoder.encodeStatusForTranslate(context, access_info, status)
			invoke(context, sv, target)
		} catch(ex : Throwable) {
			log.trace(ex)
			showToast(context, ex, "invoke() failed.")
		}
	}
	
	private val cache = HashMap<CustomShareTarget, Pair<CharSequence?, Drawable?>>()
	
	fun getCache(target : CustomShareTarget) = cache[target]
	
	fun reloadCache(context : Context, pref : SharedPreferences) {
		CustomShareTarget.values().forEach { target ->
			val cn = getCustomShareComponentName(pref, target)
			val pair = getInfo(context, cn)
			cache[target] = pair
		}
	}
}