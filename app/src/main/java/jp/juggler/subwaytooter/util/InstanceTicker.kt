package jp.juggler.subwaytooter.util

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import jp.juggler.subwaytooter.App1
import jp.juggler.util.LogCategory
import jp.juggler.util.ellipsize
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object InstanceTicker {
	
	private val log = LogCategory("InstanceTicker")
	
	private fun parseHex(group : String) : Int = group.toInt(16)
	
	private val reColor6 =
		Pattern.compile("""#([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})""", Pattern.CASE_INSENSITIVE)
	
	private val reColor3 =
		Pattern.compile("""#([0-9a-f])([0-9a-f])([0-9a-f])\b""", Pattern.CASE_INSENSITIVE)
	
	private fun parseColor(v : String) : Int? {
		var m = reColor6.matcher(v)
		if(m.find()) {
			return Color.rgb(
				parseHex(m.group(1)),
				parseHex(m.group(2)),
				parseHex(m.group(3))
			)
		}
		//
		m = reColor3.matcher(v)
		if(m.find()) {
			return Color.rgb(
				parseHex(m.group(1)) * 0x11,
				parseHex(m.group(2)) * 0x11,
				parseHex(m.group(3)) * 0x11
			)
		}
		if(v.isNotEmpty()) log.e("parseColor: can't parse $v")
		
		return null
	}
	
	private fun color(v : String) : Int =
		parseColor(v) ?: error("not a color: $v")
	
	enum class Type(
		val num : Int,
		val imageUrl : String,
		val imageWidth : Int,
		val colorText : Int,
		val colorBg : List<Int>
	) {
		
		GnuSocial(
			0,
			"https://cdn.weep.me/img/gnus.png",
			16,
			color("#fff"),
			listOf(color("#a23"))
		),
		
		MastodonJapan(
			1,
			"https://cdn.weep.me/img/mstdn.png",
			16,
			color("#fff"),
			listOf(color("#27c"))
		),
		
		MastodonAbroad(
			2,
			"https://cdn.weep.me/img/mstdn.png",
			16,
			color("#fff"),
			listOf(color("#49c"))
		),
		
		Pleroma(
			3,
			"https://cdn.weep.me/img/plrm.png",
			16,
			color("#da5"),
			listOf(color("#123"))
		),
		
		Misskey(
			4,
			"https://cdn.weep.me/img/msky2.png",
			36,
			color("#fff"),
			listOf(color("#29b"))
		),
		
		PeerTube(
			5,
			"https://cdn.weep.me/img/peertube2.png",
			16,
			color("#000"),
			listOf(color("#fff"), color("#fff"), color("#fff"))
		),
		
		// ロシアの大手マイクロブログ
		Juick(
			6,
			"https://cdn.weep.me/img/juick2.png",
			16,
			color("#fff"),
			listOf(color("#000"))
		)
		;
		
	}
	
	private fun findType(num : Int) : Type? = Type.values().find { it.num == num }
	
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
	
	private const val colorClosed = Color.BLACK or 0x666666
	
	class Item(type : Type, cols : List<String>) {
		// 0 type
		val type : Type
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
			this.type = type
			this.instance = cols[2]
			
			if(cols[5].isEmpty()) {
				// typeのデフォルト画像
				this.image = type.imageUrl
				this.imageWidth = type.imageWidth
			} else {
				// 独自画像
				this.image = "https://cdn.weep.me/img/${cols[5]}"
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
				this.colorBg = ColorBg(colorClosed)
			} else {
				this.name = ellipsize(
					when {
						imageWidth >= 60 -> "" // 空白を表示する
						cols[1].isNotEmpty() -> cols[1]
						else -> instance
					}, 36
				)
				
				this.colorText = parseColor(cols[3]) ?: type.colorText
				
				val ia = cols[4].split(',')
					.filter { it.isNotBlank() }
					.map { color(it) }
				
				this.colorBg = ColorBg(
					when {
						ia.isNotEmpty() -> ia
						else -> type.colorBg
					}
				)
			}
		}
	}
	
	var lastList = ConcurrentHashMap<String, Item>()
	
	private var timeNextLoad = 0L
	private val reLine = Pattern.compile("""([^\x0d\x0a]+)""")
	
	fun load() {
		synchronized(this) {
			// 頻繁に読み直さない
			val now = SystemClock.elapsedRealtime()
			if(timeNextLoad - now > 0) return
			timeNextLoad = now + 301000L
			
			val text = App1.getHttpCachedString("https://cdn.weep.me/instance/tsv/")
			if(text?.isEmpty() != false) return
			
			val list = ConcurrentHashMap<String, Item>()
			val m = reLine.matcher(text)
			while(m.find()) {
				try {
					val cols = m.group(1).split('\t')
					if(cols.size < 7 || cols[0].contains('[')) continue
					
					val type = try {
						findType(cols[0].toInt())
					} catch(ignored : Throwable) {
						null
					} ?: Type.MastodonJapan
					
					val item = Item(type, cols)
					
					if(item.instance.isNotEmpty()) list[item.instance] = item
				} catch(ex : Throwable) {
					log.trace(ex)
				}
			}
			if(list.isNotEmpty()) lastList = list
		}
	}
}