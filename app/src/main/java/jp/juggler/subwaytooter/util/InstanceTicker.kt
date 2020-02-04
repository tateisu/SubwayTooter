package jp.juggler.subwaytooter.util

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import jp.juggler.subwaytooter.App1
import jp.juggler.util.LogCategory
import jp.juggler.util.asciiPattern
import jp.juggler.util.ellipsize
import jp.juggler.util.groupEx
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object InstanceTicker {
	
	private val log = LogCategory("InstanceTicker")
	
	private fun parseHex(group : String?) : Int = group?.toInt(16) ?: 0
	
	private const val alnum = """[0-9a-fA-F]"""
	
	private val reColor6 ="""#($alnum{2})($alnum{2})($alnum{2})"""
		.asciiPattern( Pattern.CASE_INSENSITIVE)
	
	private val reColor3 ="""#($alnum)($alnum)($alnum)\b"""
		.asciiPattern( Pattern.CASE_INSENSITIVE)
	
	private fun parseColor(v : String) : Int? {
		var m = reColor6.matcher(v)
		if(m.find()) {
			return Color.rgb(
				parseHex(m.groupEx(1)),
				parseHex(m.groupEx(2)),
				parseHex(m.groupEx(3))
			)
		}
		//
		m = reColor3.matcher(v)
		if(m.find()) {
			return Color.rgb(
				parseHex(m.groupEx(1)) * 0x11,
				parseHex(m.groupEx(2)) * 0x11,
				parseHex(m.groupEx(3)) * 0x11
			)
		}
		if(v.isNotEmpty()) log.e("parseColor: can't parse $v")
		
		return null
	}
	
	private fun color(v : String) : Int =
		parseColor(v) ?: error("not a color: $v")
	
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
		
		fun isEmpty() = size == 0
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
	
	private val colorBgClosed = ColorBg(Color.BLACK or 0x666666)
	private val colorBgDefault = ColorBg(color("#27c"))
	
	class Item(cols : List<String>) {
		// 0 type
		//val type : Type
		//[1]インスタンス名（ドメイン） (空文字列かもしれない)
		val name : String
		// [2]ドメイン
		val instance : String
		// [3]独自文字色
		val colorText : Int
		// [4]独自背景色
		val colorBg : ColorBg
		// [5]独自画像
		val image : String
		// [6]画像の横幅
		val imageWidth : Int
		
		init {
			this.instance = cols[2]
			
			if(cols[5].isEmpty()) {
				// typeのデフォルト画像
				this.image = "https://wee.jp/i/mstdn.png"
				this.imageWidth = 16
			} else {
				// 独自画像
				this.image = cols[5]
				// 画像の横幅は省略されてるかもしれない
				this.imageWidth = try {
					cols[6].toInt()
				} catch(ex : Throwable) {
					18 // デフォルト値
				}
			}
			
			if(cols[1] == "閉鎖") {
				this.name = "閉鎖済み"
				this.colorText = Color.WHITE
				this.colorBg = colorBgClosed
			} else {
				this.name = ellipsize(
					when {
						imageWidth >= 60 -> "" // 空白を表示する
						cols[1].isNotEmpty() -> cols[1]
						else -> instance
					}, 36
				)
				
				this.colorText = parseColor(cols[3]) ?: Color.WHITE
				
				val ia = cols[4].split(',')
					.filter { it.isNotBlank() }
					.map { color(it) }
					.reversed()
				
				this.colorBg = when {
					ia.isNotEmpty() -> ColorBg(ia)
					else -> colorBgDefault
				}
			}
		}
	}
	
	var lastList = ConcurrentHashMap<String, Item>()
	
	private var timeNextLoad = 0L
	private val reLine = """([^\x0d\x0a]+)""".asciiPattern()
	
	fun load() {
		synchronized(this) {
			// 頻繁に読み直さない
			val now = SystemClock.elapsedRealtime()
			if(timeNextLoad - now > 0) return
			timeNextLoad = now + 301000L
			
			val text = App1.getHttpCachedString("https://wee.jp/tsv/1")
			if(text?.isEmpty() != false) return
			
			val list = ConcurrentHashMap<String, Item>()
			val m = reLine.matcher(text)
			while(m.find()) {
				try {
					val cols = m.groupEx(1)?.split('\t')
					if(cols == null || cols.size < 7 || cols[0].contains('[')) continue
					
					val item = Item(cols)
					if(item.instance.isNotEmpty()) list[item.instance] = item
				} catch(ex : Throwable) {
					log.trace(ex)
				}
			}
			if(list.isNotEmpty()) lastList = list
		}
	}
}