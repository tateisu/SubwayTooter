package jp.juggler.subwaytooter.api

interface TootApiCallback {
	val isApiCancelled : Boolean
	fun publishApiProgress(s : String) {}
	fun publishApiProgressRatio(value : Int, max : Int) {}
}
