package jp.juggler.subwaytooter.api

interface TootApiCallback {
    suspend fun isApiCancelled(): Boolean
    suspend fun publishApiProgress(s: String) {}
    suspend fun publishApiProgressRatio(value: Int, max: Int) {}
}
