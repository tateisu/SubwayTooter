package jp.juggler.subwaytooter.action

import android.content.*
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.TootTextEncoder
import jp.juggler.util.LogCategory
import jp.juggler.util.showToast

enum class CustomShareTarget {
	Translate,
	CustomShare1,
	CustomShare2,
	CustomShare3,
}

object CustomShare {
	
	private val log = LogCategory("CustomShare")
	
	private const val translate_app_component_default = "com.google.android.apps.translate/com.google.android.apps.translate.TranslateActivity"
	
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
	
	fun getInfo(pm : PackageManager, cn : ComponentName?) : Pair<CharSequence?, Drawable?> {
		var label : CharSequence? = null
		var icon : Drawable? = null
		try {
			if(cn != null) {
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
		} catch(ex : Throwable) {
			log.e(ex, "getInfo failed.")
		}
		return Pair(label, icon)
	}
	
	fun invoke(
		activity : ActMain,
		access_info : SavedAccount,
		status : TootStatus?,
		target : CustomShareTarget
	) {
		status ?: return
		
		try {
			// convert "pkgName/className" string to ComponentName object.
			val cn = getCustomShareComponentName(activity.pref, target)
			if(cn == null) {
				showToast(activity,true,R.string.custom_share_app_not_found)
				return
			}
			
			val sv = TootTextEncoder.encodeStatusForTranslate(activity, access_info, status)
			
			val intent = Intent()
			intent.action = Intent.ACTION_SEND
			intent.type = "text/plain"
			intent.putExtra(Intent.EXTRA_TEXT, sv)
			intent.component = cn
			activity.startActivity(intent)
		} catch( ex: ActivityNotFoundException ){
			log.trace(ex)
			showToast(activity, true, R.string.custom_share_app_not_found)
		} catch(ex : Throwable) {
			log.trace(ex)
			showToast(activity, ex, "invoke() failed.")
		}
	}
	
	private val cache = HashMap<CustomShareTarget, Pair<CharSequence?, Drawable?>>()
	
	fun getCache(target:CustomShareTarget) = cache[target]

	fun reloadCache(context : Context, pref : SharedPreferences) {
		val pm = context.packageManager
		CustomShareTarget.values().forEach { target ->
			val cn = getCustomShareComponentName(pref, target)
			val pair = getInfo(pm, cn)
			cache[target] = pair
		}
	}
	
}