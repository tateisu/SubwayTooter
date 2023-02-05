package jp.juggler.subwaytooter.push

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
/*
// ビルド要求
// com.google.firebase:firebase-messaging.20.3.0 以降
// implementation "org.jetbrains.kotlinx:kotlinx-coroutines-play-services:$kotlinx_coroutines_version"


> Default FirebaseApp failed to initialize because no default options were found.
> This usually means that com.google.gms:google-services was not applied to your gradle project.
> FirebaseApp initialization unsuccessful
とか
> FcmHandler/loadFcmToken failed :IllegalStateException
> Default FirebaseApp is not initialized in this process ***.
> Make sure to call FirebaseApp.initializeApp(Context) first.
とか
が出る場合はビルド設定を確認すること

 */
@Suppress("unused")
class FcmTokenLoader {
    //
    suspend fun getToken(): String? =
        FirebaseMessaging.getInstance().token.await()

    suspend fun deleteToken(){
        FirebaseMessaging.getInstance().deleteToken().await()
    }
}
