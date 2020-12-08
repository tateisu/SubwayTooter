package jp.juggler.subwaytooter.api

interface TootTask {
	
	// 非同期処理をここに実装する
	suspend fun background(client : TootApiClient) : TootApiResult?
	
	// 非同期処理が終わったらメインスレッドから実行される
	// 処理がキャンセルされた場合、 resultはnullになるかもしれない
	suspend fun handleResult(result : TootApiResult?)
}
