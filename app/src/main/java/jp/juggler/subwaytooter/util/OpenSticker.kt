package jp.juggler.subwaytooter.util

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import jp.juggler.subwaytooter.App1
import jp.juggler.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object OpenSticker {
	
	private val log = LogCategory("OpenSticker")
	
	private const val alnum = """[0-9a-fA-F]"""
	
	private val reColor6 = """#($alnum{2})($alnum{2})($alnum{2})"""
		.asciiPattern(Pattern.CASE_INSENSITIVE)
	
	private val reColor3 = """#($alnum)($alnum)($alnum)\b"""
		.asciiPattern(Pattern.CASE_INSENSITIVE)
	
	private fun parseHex(group : String?) : Int = group?.toInt(16) ?: 0
	
	private fun String.parseColor() : Int? {
		reColor6.matcher(this).findOrNull()?.let {
			return Color.rgb(
				parseHex(it.groupEx(1)),
				parseHex(it.groupEx(2)),
				parseHex(it.groupEx(3))
			)
		}
		reColor3.matcher(this).findOrNull()?.let {
			return Color.rgb(
				parseHex(it.groupEx(1)) * 0x11,
				parseHex(it.groupEx(2)) * 0x11,
				parseHex(it.groupEx(3)) * 0x11
			)
		}
		if(isNotEmpty()) log.e("parseColor: can't parse $this")
		return null
	}
	
	private fun String.toColor() : Int = parseColor() ?: error("not a color: $this")
	
	class ColorBg(val array : IntArray) {
		companion object {
			
			val map = HashMap<String, GradientDrawable>()
		}
		
		constructor(src : List<Int>) : this(
			IntArray(src.size + 1) {
				if(it == 0) Color.TRANSPARENT else src[it - 1]
			}
		)
		
		constructor(src : Int) : this(
			IntArray(2) {
				if(it == 0) Color.TRANSPARENT else src
			}
		)
		
		val key = array.joinToString(",") { it.toString() }
		
		val size = array.size
		
		fun first() = array.first()
		fun last() = array.last()
		
		fun getGradation() : Drawable? {
			var v = map[key]
			return if(v != null) {
				v
			} else {
				v = GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, array)
				map[key] = v
				v
			}
		}
	}
	
	private var timeNextLoad = 0L
	
	private const val colorFgDefault = Color.WHITE
	private val colorBgDefault = ColorBg("#27c".toColor())
	
	private fun JsonArray.parseBgColor() =
		mapNotNull { it.cast<String>()?.parseColor() }
			.takeIf { it.isNotEmpty() }
			?.let { ColorBg(it) }
	
	class Default(
		val fontColor : Int,
		val bgColor : ColorBg
	)
	
	class Item(src : JsonObject, defaults : Map<String, Default>) {
		
		// domain name such as "m.aqr.af"
		// ホスト名とAPドメイン名が異なるケースは想定されていない
		val domain = src.string("domain") !!
		
		// display name such as "まくらふ丼"
		val name = src.string("name") ?: domain
		
		// 'mastodon'|'pleroma'|'misskey'|'misskeylegacy'|'pixelfed' //misskeyはv12でなければlegacy
		val type = src.string("type") !!
		
		//nullならIDefault[type].bgColorを参照
		val bgColor = src.jsonArray("bgColor")?.parseBgColor()
			?: defaults[type]?.bgColor
			?: colorBgDefault
		
		//nullならIDefault[type].fontColorを参照
		val fontColor = src.string("fontColor")?.parseColor()
			?: defaults[type]?.fontColor
			?: colorFgDefault
		
		//普通はこっちを読んでください PNG/15px, 15px
		val favicon = src.string("favicon") !!
		
		// [6]画像の横幅
		val imageWidth : Int = 15
	}
	
	var lastList = ConcurrentHashMap<String, Item>()
	
	fun load() {
		synchronized(this) {
			
			try {
				// 頻繁に読み直さない
				val now = SystemClock.elapsedRealtime()
				if(timeNextLoad - now > 0) return
				timeNextLoad = now + 301000L
				
				val text = App1.getHttpCachedString("https://s.0px.io/json")
				if(text?.isEmpty() != false) return
				
				val root = text.decodeJsonObject()
				log.d("OpenSticker: updated=${root.string("updated")}")
				
				// read defaults
				val defaults = HashMap<String, Default>()
				
				for(entry in root.jsonObject("default") ?: JsonObject()) {
					val key = entry.key
					val value = entry.value.cast<JsonObject>() ?: continue
					val fontColor = value.string("fontColor")?.parseColor()
					val bgColor = value.jsonArray("bgColor")?.parseBgColor()
					if(fontColor != null && bgColor != null)
						defaults[key] = Default(fontColor, bgColor)
				}
				
				val list = ConcurrentHashMap<String, Item>()
				for(src in root.jsonArray("data") ?: JsonArray()) {
					try {
						if(src is JsonObject) {
							val item = Item(src, defaults)
							list[item.domain] = item
						}
					} catch(ex : Throwable) {
						log.e(ex, "parse failed.")
					}
				}
				if(list.isNotEmpty()) lastList = list
			} catch(ex : Throwable) {
				log.e(ex, "load failed.")
			}
		}
	}
}