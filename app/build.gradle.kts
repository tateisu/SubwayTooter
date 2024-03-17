import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
    id("io.gitlab.arturbosch.detekt")
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "jp.juggler.subwaytooter"

    compileSdk = Vers.stCompileSdkVersion
    buildToolsVersion = Vers.stBuildToolsVersion

    defaultConfig {
        targetSdk = Vers.stTargetSdkVersion
        minSdk = Vers.stMinSdkVersion
        versionCode = 545
        versionName = "5.545"
        applicationId = "jp.juggler.subwaytooter"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    // exoPlayer 2.9.0 以降は Java 8 compiler support を要求する
    compileOptions {
        sourceCompatibility = Vers.javaSourceCompatibility
        targetCompatibility = Vers.javaTargetCompatibility
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = Vers.kotlinJvmTarget
        freeCompilerArgs = listOf(
            "-opt-in=kotlin.ExperimentalStdlibApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            "-opt-in=androidx.media3.common.util.UnstableApi",
            //      "-Xopt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            //      "-Xopt-in=androidx.compose.animation.ExperimentalAnimationApi",
        )
    }

    // kotlin 1.6.0にすると This version (1.0.5) of the Compose Compiler requires Kotlin version 1.5.31 but you appear to be using Kotlin version 1.6.0 which is not known to be compatible. と怒られる
    //    buildFeatures {
    //        compose true
    //    }
    //    composeOptions {
    //        kotlinCompilerExtensionVersion compose_version
    //    }

    signingConfigs {
        create("release") {
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
        }
    }

    // Specifies comma-separated list of flavor dimensions.
    flavorDimensions += "fcmType"

    productFlavors {
        create("nofcm") {
            dimension = "fcmType"
            versionNameSuffix = "-noFcm"
            applicationIdSuffix = ".noFcm"
            manifestPlaceholders["customScheme"] = "subwaytooternofcm"
        }
        create("fcm") {
            dimension = "fcmType"
            manifestPlaceholders["customScheme"] = "subwaytooter"
        }
    }

    // https://github.com/tateisu/SubwayTooter/issues/229
    //    splits {
    //        abi {
    //            enable true
    //            reset()
    //            include "arm64-v8a", "x86", "x86_64"
    //            universalApk true
    //        }
    //    }

    packaging {
        resources {
            excludes.addAll(
                listOf(
                    "/META-INF/{AL2.0,LGPL2.1}",
                    "META-INF/DEPENDENCIES",
                )
            )
            // https://github.com/Kotlin/kotlinx.coroutines/issues/1064
            pickFirsts.addAll(
                listOf(
                    "META-INF/atomicfu.kotlin_module",
                )
            )
        }
    }

    useLibrary("android.test.base")
    useLibrary("android.test.mock")
    lint {
        warning += "DuplicatePlatformClasses"
        disable += "MissingTranslation"
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
}

dependencies {
    // desugar_jdk_libs 2.0.0 は AGP 7.4.0-alpha10 以降を要求する
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:${Vers.desugarLibVersion}")

    implementation(project(":base"))
    implementation(project(":colorpicker"))
    implementation(project(":emoji"))
    implementation(project(":apng_android"))
    implementation(project(":anko"))


    implementation("androidx.activity:activity-compose:${Vers.androidxActivity}")
    implementation("androidx.appcompat:appcompat:${Vers.androidxAppcompat}")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.runtime:runtime-livedata:${Vers.androidxComposeRuntime}")
    implementation("androidx.compose.ui:ui-tooling-preview:${Vers.androidxComposeUi}")
    implementation("androidx.compose.ui:ui:${Vers.androidxComposeUi}")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.emoji2:emoji2-bundled:${Vers.androidxEmoji2}")
    implementation("androidx.emoji2:emoji2-views-helper:${Vers.androidxEmoji2}")
    implementation("androidx.emoji2:emoji2-views:${Vers.androidxEmoji2}")
    implementation("androidx.emoji2:emoji2:${Vers.androidxEmoji2}")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:${Vers.androidxLifecycle}")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:${Vers.androidxLifecycle}")
    implementation("androidx.media3:media3-common:${Vers.androidxMedia3}")
    implementation("androidx.media3:media3-datasource:${Vers.androidxMedia3}")
    implementation("androidx.media3:media3-exoplayer:${Vers.androidxMedia3}")
    implementation("androidx.media3:media3-session:${Vers.androidxMedia3}")
    implementation("androidx.media3:media3-ui:${Vers.androidxMedia3}")
    implementation("androidx.recyclerview:recyclerview:${Vers.androidxRecyclerView}")
    implementation("androidx.work:work-runtime-ktx:${Vers.androidxWork}")
    implementation("androidx.work:work-runtime:${Vers.androidxWork}")
    implementation("com.caverock:androidsvg-aar:1.4")
    implementation("com.github.UnifiedPush:android-connector:2.1.1")
    implementation("com.github.alexzhirkevich:custom-qr-generator:1.6.2")
    implementation("com.github.bumptech.glide:glide:${Vers.glideVersion}")
    implementation("com.github.omadahealth:swipy:1.2.3@aar")
    implementation("com.github.penfeizhou.android.animation:apng:${Vers.apng4AndroidVersion}")
    implementation("com.github.woxthebox:draglistview:1.7.3")
    implementation("com.github.zjupure:webpdecoder:${Vers.webpDecoderVersion}")
    implementation("com.google.android.flexbox:flexbox:${Vers.googleFlexbox}")
    implementation("com.google.android.material:material:${Vers.googleMaterial}")
    implementation("com.squareup.okhttp3:okhttp-urlconnection:${Vers.okhttpVersion}")
    implementation("com.squareup.okhttp3:okhttp:${Vers.okhttpVersion}")
    implementation("com.squareup.okhttp3:okhttp:${Vers.okhttpVersion}")
    implementation("io.insert-koin:koin-android-compat:${Vers.koinVersion}")
    implementation("io.insert-koin:koin-android:${Vers.koinVersion}")
    implementation("io.insert-koin:koin-androidx-workmanager:${Vers.koinVersion}")
    implementation("org.conscrypt:conscrypt-android:${Vers.conscryptVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Vers.kotlinxSerializationLibVersion}")
    implementation("ru.gildor.coroutines:kotlin-coroutines-okhttp:${Vers.gildorkotlinCoroutinesOkhttp}")

    ////////////

    implementation("com.github.bumptech.glide:okhttp3-integration:${Vers.glideVersion}") {
        exclude("com.squareup.okhttp3", "okhttp")
    }

    "fcmImplementation"("com.google.firebase:firebase-messaging:23.4.1")
    "fcmImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:${Vers.kotlinxCoroutinesVersion}")

    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:${Vers.detektVersion}")

    ksp("com.github.bumptech.glide:ksp:${Vers.glideVersion}")

    debugImplementation("androidx.compose.ui:ui-test-manifest:1.6.3")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.3")

    // =================================================
    // UnitTest
    testImplementation(kotlin("test"))
    testImplementation("androidx.arch.core:core-testing:${Vers.androidxArchCoreTesting}")
    testImplementation("junit:junit:${Vers.junitVersion}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Vers.kotlinxCoroutinesVersion}")

//    testImplementation("com.squareup.okhttp3:mockwebserver:${Vers.okhttpVersion}") {
//        exclude("com.squareup.okio", "okio")
//        exclude("com.squareup.okhttp3", "okhttp")
//        exclude("org.jetbrains.kotlin", "kotlin-stdlib-common")
//        exclude("org.jetbrains.kotlin", "kotlin-stdlib")
//        exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
//    }

    // ==============================================
    // androidTest
    androidTestRuntimeOnly("androidx.test:runner:1.5.2")
    androidTestUtil("androidx.test:orchestrator:1.4.2")

    androidTestImplementation("androidx.test.espresso:espresso-core:${Vers.androidxTestEspressoCore}")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.1.5")
    androidTestImplementation("androidx.test.ext:junit:${Vers.androidxTestExtJunit}")
    androidTestImplementation("androidx.test.ext:truth:1.5.0")
    androidTestImplementation("androidx.test:core-ktx:${Vers.androidxTestCoreKtx}")
    androidTestImplementation("androidx.test:core:${Vers.androidxTestCore}")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.3")

    androidTestImplementation("com.squareup.okhttp3:mockwebserver:${Vers.okhttpVersion}") {
        exclude("com.squareup.okio", "okio")
        exclude("com.squareup.okhttp3", "okhttp")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib-common")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
    }
}

repositories {
    mavenCentral()
}

fun willApplyGoogleService(): Boolean {
    val taskRequestsString = gradle.startParameter.taskRequests.toString()

    val isMatch = """(assemble|generate|connected)Fcm""".toRegex()
        .find(taskRequestsString) != null

    println("willApplyGoogleService=$isMatch. $taskRequestsString")
    return isMatch
}

if (willApplyGoogleService()) apply(plugin = "com.google.gms.google-services")


tasks.register<Detekt>("detektAll") {
    description = "Custom DETEKT build for all modules"

    // activate all available (even unstable) rules.
    allRules = false

    // a way of suppressing issues before introducing detekt
    // baseline = file("$rootDir/config/detekt/baseline.xml")

    parallel = true
    ignoreFailures = false
    autoCorrect = false

    // preconfigure defaults
    buildUponDefaultConfig = true

    val configFile = files("$rootDir/config/detekt/config.yml")
    config.setFrom(configFile)

    val baselineFile = file("$rootDir/config/detekt/baseline.xml")
    if (baselineFile.isFile) {
        baseline.set(baselineFile)
    }

    setSource(
        files(
            "$rootDir/anko/src",
            "$rootDir/apng/src",
            "$rootDir/apng_android/src",
            "$rootDir/app/src",
            "$rootDir/base/src",
            "$rootDir/colorpicker/src",
            "$rootDir/emoji/src",
            "$rootDir/icon_material_symbols/src",
            "$rootDir/sample_apng/src",
        )
    )

    val resourceFiles = "**/resources/**"
    val buildFiles = "**/build/**"
    exclude(resourceFiles, buildFiles)
    reports {
        fun reportLocationByExt(ext: String) =
            layout.buildDirectory
                .file("reports/detekt/st-${name}.$ext")
                .get()
                .asFile

        txt.required.set(true)
        txt.outputLocation.set(reportLocationByExt("txt"))

        html.required.set(true)
        html.outputLocation.set(reportLocationByExt("html"))

        xml.required.set(false)
        xml.outputLocation.set(reportLocationByExt("xml"))

        sarif.required.set(false)
        sarif.outputLocation.set(reportLocationByExt("sarif"))
    }
}
