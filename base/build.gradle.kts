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
        targetSdk = Vers.stTargetSdkVersion

        consumerProguardFiles( "consumer-rules.pro")
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
    packagingOptions {
        jniLibs {
            excludes .addAll(listOf("META-INF/LICENSE*"))
        }
        resources {
            excludes .addAll(listOf("META-INF/LICENSE*"))
        }
    }
}

kotlin {
    jvmToolchain( Vers.kotlinJvmToolchain)
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

    api("androidx.appcompat:appcompat:${Vers.appcompatVersion}")
    api("androidx.browser:browser:1.5.0")
    api("androidx.core:core-ktx:${Vers.coreKtxVersion}")
    api("androidx.drawerlayout:drawerlayout:1.2.0")
    api("androidx.emoji2:emoji2-bundled:${Vers.emoji2Version}")
    api("androidx.emoji2:emoji2-views-helper:${Vers.emoji2Version}")
    api("androidx.emoji2:emoji2-views:${Vers.emoji2Version}")
    api("androidx.emoji2:emoji2:${Vers.emoji2Version}")
    api("androidx.exifinterface:exifinterface:1.3.6")
    api("androidx.lifecycle:lifecycle-common-java8:${Vers.lifecycleVersion}")
    api("androidx.lifecycle:lifecycle-livedata-ktx:${Vers.lifecycleVersion}")
    api("androidx.lifecycle:lifecycle-process:${Vers.lifecycleVersion}")
    api("androidx.lifecycle:lifecycle-reactivestreams-ktx:${Vers.lifecycleVersion}")
    api("androidx.lifecycle:lifecycle-runtime-ktx:${Vers.lifecycleVersion}")
    api("androidx.lifecycle:lifecycle-service:${Vers.lifecycleVersion}")
    api("androidx.lifecycle:lifecycle-viewmodel-ktx:${Vers.lifecycleVersion}")
    api("androidx.lifecycle:lifecycle-viewmodel-savedstate:${Vers.lifecycleVersion}")
    api("androidx.recyclerview:recyclerview:1.3.0")
    api("androidx.startup:startup-runtime:${Vers.startupVersion}")
    api("androidx.work:work-runtime-ktx:${Vers.workVersion}")
    api("androidx.work:work-runtime:${Vers.workVersion}")
    api("com.astuetz:pagerslidingtabstrip:1.0.1")
    api("com.caverock:androidsvg-aar:1.4")
    api("com.github.hadilq:live-event:1.3.0")
    api("com.github.omadahealth:swipy:1.2.3@aar")
    api("com.github.woxthebox:draglistview:1.7.3")
    api("com.google.android.flexbox:flexbox:3.0.0")
    api("com.google.android.material:material:${Vers.materialVersion}")
    api("com.otaliastudios:transcoder:0.10.5")
    api("com.squareup.okhttp3:okhttp-urlconnection:${Vers.okhttpVersion}")
    api("com.squareup.okhttp3:okhttp:${Vers.okhttpVersion}")
    // api( "io.github.inflationx:calligraphy3:3.1.1")
    // api( "io.github.inflationx:viewpump:2.1.1")
    api("org.bouncycastle:bcprov-jdk15on:1.70")
    api("org.jetbrains.kotlin:kotlin-reflect:${Vers.kotlinVersion}")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Vers.kotlinxCoroutinesVersion}")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Vers.kotlinxCoroutinesVersion}")
    api("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    api("ru.gildor.coroutines:kotlin-coroutines-okhttp:1.0")

    //non-OSS dependency api "androidx.media3:media3-cast:$media3Version"
    api("androidx.media3:media3-common:${Vers.media3Version}")
    api("androidx.media3:media3-datasource:${Vers.media3Version}")
    api("androidx.media3:media3-effect:${Vers.media3Version}")
    api("androidx.media3:media3-exoplayer:${Vers.media3Version}")
    api("androidx.media3:media3-session:${Vers.media3Version}")
    api("androidx.media3:media3-transformer:${Vers.media3Version}")
    api("androidx.media3:media3-ui:${Vers.media3Version}")

    // commons-codecをapiにすると、端末上の古いjarが使われてしまう
    // declaration of "org.apache.commons.codec.binary.Base64" appears in /system/framework/org.apache.http.legacy.jar)
    androidTestImplementation("commons-codec:commons-codec:${Vers.commonsCodecVersion}")

    // Koin main features for Android
    api("io.insert-koin:koin-android:${Vers.koinVersion}")
    api("io.insert-koin:koin-android-compat:${Vers.koinVersion}")
    api("io.insert-koin:koin-androidx-workmanager:${Vers.koinVersion}")
    // api( "io.insert-koin:koin-androidx-navigation:$koinVersion")
    // api( "io.insert-koin:koin-androidx-compose:$koinVersion")

    // for com.bumptech.glide.integration.webp.*
    api("com.github.zjupure:webpdecoder:${Vers.webpDecoderVersion}")
    api("com.github.bumptech.glide:glide:${Vers.glideVersion}")
    api("com.github.bumptech.glide:annotations:${Vers.glideVersion}")
    api("com.github.bumptech.glide:okhttp3-integration:${Vers.glideVersion}") {
        exclude("com.squareup.okhttp3", "okhttp")
    }

    androidTestApi("androidx.test.espresso:espresso-core:${Vers.androidxTestEspressoCoreVersion}")
    androidTestApi("androidx.test.ext:junit-ktx:1.1.5")
    androidTestApi("androidx.test.ext:junit:${Vers.androidxTestExtJunitVersion}")
    androidTestApi("androidx.test.ext:truth:1.5.0")
    androidTestApi("androidx.test:core-ktx:${Vers.testKtxVersion}")
    androidTestApi("androidx.test:core:${Vers.androidxTestVersion}")
    androidTestApi("androidx.test:runner:1.5.2")
    androidTestApi("org.jetbrains.kotlin:kotlin-test:${Vers.kotlinTestVersion}")
    androidTestApi("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Vers.kotlinxCoroutinesVersion}")
    testApi("androidx.arch.core:core-testing:${Vers.archVersion}")
    testApi("junit:junit:${Vers.junitVersion}")
    testApi("org.jetbrains.kotlin:kotlin-test:${Vers.kotlinTestVersion}")
    testApi("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Vers.kotlinxCoroutinesVersion}")

    // To use android test orchestrator
    androidTestUtil("androidx.test:orchestrator:1.4.2")

    testApi("com.squareup.okhttp3:mockwebserver:${Vers.okhttpVersion}") {
        exclude("com.squareup.okio", "okio")
        exclude("com.squareup.okhttp3", "okhttp")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib-common")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
    }
    androidTestApi("com.squareup.okhttp3:mockwebserver:${Vers.okhttpVersion}") {
        exclude("com.squareup.okio", "okio")
        exclude("com.squareup.okhttp3", "okhttp")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib-common")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
    }

    // conscrypt をUnitテストするための指定
    // https://github.com/google/conscrypt/issues/649

    api("org.conscrypt:conscrypt-android:${Vers.conscryptVersion}")
}
