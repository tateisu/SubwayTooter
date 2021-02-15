package jp.juggler.subwaytooter.emoji

@Suppress("ClassName")
object log {
	fun d(msg: String) = println("D/ $msg")
	fun w(msg: String) = println("W/ $msg")
	fun e(msg: String) = println("E/ $msg")
}