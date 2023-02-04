package jp.juggler.subwaytooter.push

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

@Suppress("unused")
class FcmTokenLoader {
    // com.google.firebase:firebase-messaging.20.3.0 以降
    // implementation "org.jetbrains.kotlinx:kotlinx-coroutines-play-services:$kotlinx_coroutines_version"
    suspend fun getToken(): String? =
        FirebaseMessaging.getInstance().token.await()

    suspend fun deleteToken(){
        FirebaseMessaging.getInstance().deleteToken().await()
    }
}
