package jp.juggler.subwaytooter.emoji.model

class SkinToneModifier(
	val code: Int,
	val suffixList: Array<String>
)

val skinToneModifiers = HashMap<Int, SkinToneModifier>().apply {
	fun add(code: Int, suffixList: Array<String>) =
		put(code, SkinToneModifier(code, suffixList))
	add(0x1F3FB, arrayOf("_tone1", "_light_skin_tone"))
	add(0x1F3FC, arrayOf("_tone2", "_medium_light_skin_tone"))
	add(0x1F3FD, arrayOf("_tone3", "_medium_skin_tone"))
	add(0x1F3FE, arrayOf("_tone4", "_medium_dark_skin_tone"))
	add(0x1F3FF, arrayOf("_tone5", "_dark_skin_tone"))
}
