
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.BaseVariantOutput

import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("io.gitlab.arturbosch.detekt")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
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
        versionCode = 539
        versionName = "5.539"
        applicationId = "jp.juggler.subwaytooter"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

    }

    buildFeatures {
        viewBinding = true
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

            lintOptions {
                disable("MissingTranslation")
            }
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

    // Generate Signed APK のファイル名を変更
    applicationVariants.all(object : Action<ApplicationVariant> {
        override fun execute(variant: ApplicationVariant) {
            println("variant: ${variant}")

            // Rename APK
            val versionCode = defaultConfig.versionCode
            val versionName = defaultConfig.versionName
            val flavor = variant.flavorName
            // val abi = output.getFilter("ABI") ?: "all"
            val date = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val branch = providers.exec {
                commandLine("git", "rev-parse", "--abbrev-ref", "HEAD")
            }.standardOutput.asText.get()?.trim() ?: "(no branch)"

            variant.outputs.all(object : Action<BaseVariantOutput> {
                override fun execute(output: BaseVariantOutput) {
                    val outputImpl = output as BaseVariantOutputImpl
                    outputImpl.outputFileName =
                        "SubwayTooter-${branch}-${flavor}-${versionCode}-${versionName}-${date}.apk"
                    println("output file name: ${outputImpl.outputFileName}")
                }
            })
        }
    })

    android.applicationVariants.all { variant ->
        if (variant.buildType.name == "release") {
            variant.outputs.all { output ->

                true
            }
        }
        true
    }

    packagingOptions {
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
    //noinspection GradleDependency
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:${Vers.desugarLibVersion}")

    implementation(project(":base"))
    implementation(project(":colorpicker"))
    implementation(project(":emoji"))
    implementation(project(":apng_android"))
    implementation(project(":anko"))
    implementation(fileTree(mapOf("dir" to "src/main/libs", "include" to arrayOf("*.aar"))))

    "fcmImplementation"("com.google.firebase:firebase-messaging:23.2.1")
    "fcmImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:${Vers.kotlinxCoroutinesVersion}")

    // implementation "org.conscrypt:conscrypt-android:$conscryptVersion"
    api("org.conscrypt:conscrypt-android:${Vers.conscryptVersion}")
    implementation("com.github.UnifiedPush:android-connector:2.1.1")
    implementation("jp.wasabeef:glide-transformations:4.3.0")

    implementation("com.github.androidmads:QRGenerator:1.0.1")

    val apng4AndroidVersion = "2.25.0"
    implementation("com.github.penfeizhou.android.animation:apng:$apng4AndroidVersion")

    ksp("com.github.bumptech.glide:ksp:${Vers.glideVersion}")

    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:${Vers.detektVersion}")

    testImplementation(project(":base"))
    androidTestImplementation(project(":base"))

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
}

repositories {
    mavenCentral()
}

fun willApplyGoogleService(): Boolean {
    val gradle = getGradle()
    val taskRequestsString = gradle.getStartParameter().getTaskRequests().toString()

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
    if (baselineFile.isFile()) {
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

//    val kotlinFiles = "**/*.kt"
//    include(kotlinFiles)

    val resourceFiles = "**/resources/**"
    val buildFiles = "**/build/**"
    exclude(resourceFiles, buildFiles)
    reports {
        html.enabled = true
        xml.enabled = false
        txt.enabled = false

        xml.required.set(true)
        xml.outputLocation.set(file("$buildDir/reports/detekt/st-${name}.xml"))
        html.required.set(true)
        html.outputLocation.set(file("$buildDir/reports/detekt/st-${name}.html"))
        txt.required.set(true)
        txt.outputLocation.set(file("$buildDir/reports/detekt/st-${name}.txt"))
        sarif.required.set(true)
        sarif.outputLocation.set(file("$buildDir/reports/detekt/st-${name}.sarif"))
    }
}

