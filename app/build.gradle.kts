import io.gitlab.arturbosch.detekt.Detekt
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("buildLogic.StAndroidApp")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)

    // 以下は試験用、保守用
    alias(libs.plugins.detekt)
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "jp.juggler.subwaytooter"

    defaultConfig {
        applicationId = namespace
        versionCode = 550
        versionName = "5.550"
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

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

}

dependencies {
    implementation(project(":base"))
    implementation(project(":colorpicker"))
    implementation(project(":emoji"))
    implementation(project(":apng_android"))
    implementation(project(":anko"))

    // 各種ライブラリのBoM
    implementation(platform(libs.koin.bom))
    implementation(platform(libs.androidx.compose.bom))
    testImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidsvg.aar)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.constraintLayout)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.drawerlayout)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.emoji2.bundled)
    implementation(libs.androidx.emoji2.views)
    implementation(libs.androidx.emoji2.views.helper)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.apng4Android)
    implementation(libs.conscrypt.android)
    implementation(libs.custom.qr.generator)
    implementation(libs.draglistview)
    implementation(libs.glide)
    implementation(libs.google.flexbox)
    implementation(libs.google.material)
    implementation(libs.koin.android)
    implementation(libs.koin.android.compat)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.kotlin.coroutines.okhttp)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.guava) // ListenableFuture.await()
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.urlconnection)
    implementation(libs.unifiedpush.android.connector)
    implementation(libs.webpDecoder)




    // AAR指定はバージョンカタログにはない…
    //noinspection UseTomlInstead
    implementation("com.github.omadahealth:swipy:1.2.3@aar")

    val glideVersion = libs.versions.glide.get()
    implementation("com.github.bumptech.glide:okhttp3-integration:$glideVersion") {
        exclude("com.squareup.okhttp3", "okhttp")
    }

    "fcmImplementation"(libs.firebase.messaging)
    "fcmImplementation"(libs.kotlinx.coroutines.play.services)

    detektPlugins(libs.detekt.formatting)

    ksp(libs.glide.ksp)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // =================================================
    // UnitTest
    // https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-test
    testImplementation(libs.kotlin.test)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

//    testImplementation("com.squareup.okhttp3:mockwebserver:${Vers.okhttpVersion}") {
//        exclude("com.squareup.okio", "okio")
//        exclude("com.squareup.okhttp3", "okhttp")
//        exclude("org.jetbrains.kotlin", "kotlin-stdlib-common")
//        exclude("org.jetbrains.kotlin", "kotlin-stdlib")
//        exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
//    }

    // ==============================================
    // androidTest
    androidTestRuntimeOnly(libs.androidx.test.runner)
    androidTestUtil(libs.androidx.test.orchestrator)

    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit.ktx)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.ext.truth)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    val okHttpVersion = libs.versions.okhttp.get()
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:$okHttpVersion") {
        exclude("com.squareup.okio", "okio")
        exclude("com.squareup.okhttp3", "okhttp")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib-common")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
    }
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

composeCompiler {
//    reportsDestination = layout.buildDirectory.dir("compose_compiler")
//    stabilityConfigurationFile = rootProject.layout.projectDirectory.file("stability_config.conf")
}
