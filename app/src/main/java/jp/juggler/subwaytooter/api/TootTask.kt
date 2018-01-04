package jp.juggler.subwaytooter.api

interface TootTask {
	
	// 非同期処理をここに実装する
	fun background(client : TootApiClient) : TootApiResult?
	
	// 非同期処理が終わったらメインスレッドから実行される
	// 処理がキャンセルされた場合、 resultはnullになるかもしれない
	fun handleResult(result : TootApiResult?)
	
}
