import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "jp.juggler.base"

    compileSdk = Vers.stCompileSdkVersion
    buildToolsVersion = Vers.stBuildToolsVersion

    defaultConfig {
        minSdk = Vers.stMinSdkVersion

        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = Vers.javaSourceCompatibility
        targetCompatibility = Vers.javaTargetCompatibility
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        jvmToolchain(Vers.kotlinJvmToolchain)
    }
    kotlinOptions {
        jvmTarget = Vers.kotlinJvmTarget
    }
    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = Vers.kotlinJvmTarget
        }
    }
    packaging {
        jniLibs {
            excludes.addAll(listOf("META-INF/LICENSE*"))
        }
        resources {
            excludes.addAll(listOf("META-INF/LICENSE*"))
        }
    }
}

kotlin {
    jvmToolchain(Vers.kotlinJvmToolchain)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = Vers.kotlinJvmTarget
    }
}

dependencies {
    // desugar_jdk_libs 2.0.0 は AGP 7.4.0-alpha10 以降を要求する
    //noinspection GradleDependency
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:${Vers.desugarLibVersion}")

    // JugglerBaseInitializer で使う
    implementation("androidx.startup:startup-runtime:${Vers.androidxStartup}")

    // decodeP256dh で使う
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")

    // defaultSecurityProvider で使う
    implementation("org.conscrypt:conscrypt-android:${Vers.conscryptVersion}")

    // AudioTranscoderで使う
    implementation("androidx.media3:media3-common:${Vers.androidxMedia3}")
    implementation("androidx.media3:media3-transformer:${Vers.androidxMedia3}")
    implementation("androidx.media3:media3-effect:${Vers.androidxMedia3}")

    // EmptyScope.kt で使う
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Vers.kotlinxCoroutinesVersion}")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:${Vers.androidxLifecycle}")

    // Compat.kt で使う
    implementation("androidx.annotation:annotation:${Vers.androidxAnnotation}")
    implementation("androidx.appcompat:appcompat:${Vers.androidxAppcompat}")

    // JsonDelegate で使う
    implementation(kotlin("reflect"))
    // UriSerializer で使う。アカウント設定で状態の保存に kotlinx-serialization-json を使っている
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Vers.kotlinxSerializationLibVersion}")

    // BitmapUtils で使う
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // MovieUtils で使う
    implementation("com.otaliastudios:transcoder:0.10.5")

    // HttpUtils で使う
    implementation("com.squareup.okhttp3:okhttp:${Vers.okhttpVersion}")

    // ないとなぜかIDE上にエラーが出る
    implementation("androidx.activity:activity-ktx:${Vers.androidxActivity}")


    // ==========================================================================
    // 単体テスト
    testImplementation(kotlin("test"))

    // ==========================================================================
    // AndroidTest
    // 紛らわしいのでAndroidTestではkotlin.testを使わない androidTestImplementation(kotlin("test"))
    androidTestRuntimeOnly("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:core:${Vers.androidxTestCore}")
    androidTestImplementation("androidx.test.ext:junit:${Vers.androidxTestExtJunit}")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Vers.kotlinxCoroutinesVersion}")

    // DispatchersTest で使う
    androidTestImplementation("androidx.lifecycle:lifecycle-viewmodel-ktx:${Vers.androidxLifecycle}")

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
