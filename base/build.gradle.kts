plugins {
    id("buildLogic.StAndroidLib")
}
android {
    namespace = "jp.juggler.base"
}

dependencies {
    // JugglerBaseInitializer で使う
    implementation(libs.androidx.startup.runtime)

    // decodeP256dh で使う
    implementation(libs.bcprov.jdk15on)

    // defaultSecurityProvider で使う
    implementation(libs.conscrypt.android)

    // AudioTranscoderで使う
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)

    // EmptyScope.kt で使う
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Compat.kt で使う
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)

    // JsonDelegate で使う
    implementation(kotlin("reflect"))
    // UriSerializer で使う。アカウント設定で状態の保存に kotlinx-serialization-json を使っている
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.kotlinx.coroutines.core)

    // BitmapUtils で使う
    implementation(libs.androidx.exifinterface)

    // MovieUtils で使う
    implementation(libs.otaliastudios.transcoder)

    // HttpUtils で使う
    implementation(libs.okhttp)

    // ないとなぜかIDE上にエラーが出る
    implementation(libs.androidx.activity.ktx)



    // ==========================================================================
    // 単体テスト
    testImplementation(kotlin("test"))

    // ==========================================================================
    // AndroidTest
    // 紛らわしいのでAndroidTestではkotlin.testを使わない androidTestImplementation(kotlin("test"))
    androidTestRuntimeOnly(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    // DispatchersTest で使う
    androidTestImplementation(libs.androidx.lifecycle.viewmodel.ktx)

//    implementation("androidx.core:core-ktx:${Vers.androidxCoreVersion}")
//
//    implementation("androidx.emoji2:emoji2-bundled:${Vers.androidxEmoji2}")
//    implementation("androidx.emoji2:emoji2-views-helper:${Vers.androidxEmoji2}")
//    implementation("androidx.emoji2:emoji2-views:${Vers.androidxEmoji2}")
//    implementation("androidx.emoji2:emoji2:${Vers.androidxEmoji2}")
//    implementation("androidx.lifecycle:lifecycle-common-java8:${Vers.lifecycleVersion}")
//    implementation("androidx.lifecycle:lifecycle-livedata-ktx:${Vers.lifecycleVersion}")
//    implementation("androidx.lifecycle:lifecycle-process:${Vers.lifecycleVersion}")
//    implementation("androidx.lifecycle:lifecycle-reactivestreams-ktx:${Vers.lifecycleVersion}")
//    implementation("androidx.lifecycle:lifecycle-service:${Vers.lifecycleVersion}")
//    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:${Vers.lifecycleVersion}")
//    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:${Vers.lifecycleVersion}")
//
//
//    implementation("com.astuetz:pagerslidingtabstrip:1.0.1")
//    implementation("com.caverock:androidsvg-aar:1.4")
//    implementation("com.github.UnifiedPush:android-connector:2.1.1")
//
//    implementation("com.github.bumptech.glide:annotations:${Vers.glideVersion}")
//    implementation("com.github.bumptech.glide:glide:${Vers.glideVersion}")
//    implementation("com.github.hadilq:live-event:1.3.0")
//
//    implementation("com.github.penfeizhou.android.animation:apng:${Vers.apng4AndroidVersion}")
//
//    implementation("com.github.zjupure:webpdecoder:${Vers.webpDecoderVersion}")
//
//    implementation("com.google.android.material:material:${Vers.googleMaterialVersion}")
//
//    implementation("jp.wasabeef:glide-transformations:4.3.0")
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Vers.kotlinxCoroutinesVersion}")
//    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
//    implementation("ru.gildor.coroutines:kotlin-coroutines-okhttp:${Vers.gildorkotlinCoroutinesOkhttp}")

//    androidTestImplementation("androidx.test.espresso:espresso-core:${Vers.androidxTestEspressoCoreVersion}")
//    androidTestImplementation("androidx.test.ext:junit-ktx:1.1.5")
//    androidTestImplementation("androidx.test.ext:truth:1.5.0")
//    androidTestImplementation("androidx.test:core-ktx:${Vers.testKtxVersion}")
//
//    testImplementation("androidx.arch.core:core-testing:${Vers.archVersion}")
//    testImplementation("junit:junit:${Vers.junitVersion}")
//    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Vers.kotlinxCoroutinesVersion}")
//
//    // Compose compilerによりkotlinのバージョンを上げられない
//    //noinspection GradleDependency
//    testImplementation("org.jetbrains.kotlin:kotlin-test:${Vers.kotlinTestVersion}")
//    //noinspection GradleDependency
//    androidTestImplementation("org.jetbrains.kotlin:kotlin-test:${Vers.kotlinTestVersion}")
//
//    // To use android test orchestrator
//    // androidTestUtil("androidx.test:orchestrator:1.4.2")
//
//    testImplementation("com.squareup.okhttp3:mockwebserver:${Vers.okhttpVersion}") {
//        exclude("com.squareup.okio", "okio")
//        exclude("com.squareup.okhttp3", "okhttp")
//        exclude("org.jetbrains.kotlin", "kotlin-stdlib-common")
//        exclude("org.jetbrains.kotlin", "kotlin-stdlib")
//        exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
//    }
//    androidTestImplementation("com.squareup.okhttp3:mockwebserver:${Vers.okhttpVersion}") {
//        exclude("com.squareup.okio", "okio")
//        exclude("com.squareup.okhttp3", "okhttp")
//        exclude("org.jetbrains.kotlin", "kotlin-stdlib-common")
//        exclude("org.jetbrains.kotlin", "kotlin-stdlib")
//        exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
//    }
}
